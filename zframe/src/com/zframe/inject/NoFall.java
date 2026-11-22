package com.zframe.inject;

import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.HashSet;

/** v3.62 训练岛防摔（用户：训练营会被摔死）。只在训练岛、只在有权威的一端（单机 / 服务端房主）跑。
 *
 *  RE（capstone，libUE4 armv7，notes/history.md v3.62）：
 *  - ANZCharacter 虚表 0x4bddb48：+0x3e4 Landed = 0xd14030：GetVelocity(vt+0x15c) 取 |Vz| → vt+0x7e8 算并扣摔伤；
 *    BR 人物虚表（0x4c9c4dc / 0x4c9e068 / 0x4ca2828 / 0x4ca49d0 / 0x4ce23ac）+0x3e4 = 0x10d3d04（先调 0xd14030），
 *    +0x7e8 = 0x10e8f00（车上等情况返回 0，否则尾跳 0xd149a4）。
 *  - 0xd149a4（摔伤）：速度 s = |Vz|，人物属性（反射表核对）
 *      +0xa78 FallVelocityThreshold_High、+0xa7c FallVelocityThreshold_VeryHigh、+0xa80 FallVelocityThreshold_Kill、
 *      +0xa84 FallDamagePercent_High、+0xa88 FallDamagePercent_VeryHigh、+0x5a4 HealthMax：
 *      High ≤ s < VeryHigh → HealthMax×High%；VeryHigh ≤ s < Kill → HealthMax×VeryHigh%；s ≥ Kill → HealthMax（直接死）；否则 0（常量 0xd14b30 = 0.0）。
 *      伤害 > 0 且 Role(+0xe8)==3（权威）才走 vt+0x2a0 TakeDamage。摔伤日志「FallDamage=%f」只在 verbose 级别打（0xd1409c cmp #5），所以诊断日志里看不到。
 *  - 改法：不改代码，只把训练岛上每个人物的三个阈值写成 1e9 → 永远落到「否则 0」分支，不扣血。
 *    落地动作档位（0xd1445c 用 +0xa74 Low / +0xa78）跟着变轻，不影响别的。人物换了（复活、新加入）10 秒内重新找到再写。 */
final class NoFall {
    static final int T_HIGH = 0xa78, T_VHIGH = 0xa7c, T_KILL = 0xa80, VT_FALL = 0x7e8;
    static final long FALL_BASE = 0xd149a4L, FALL_BR = 0x10e8f00L;
    static final float BIG = 1e9f;
    static volatile String state = "训练岛防摔：没跑";
    static volatile boolean running;
    static final HashSet<Long> pawns = new HashSet<>();
    static String orig = "";
    // v3.94 用户：训练场开高跳摔死（v3.93 日志：阈值法只在第一次进训练场找到人物，复活/再进后「还没找到人物」）→
    //   直接改摔伤函数 0xd149a4 开头：mov r0,#0; vmov s0,r0; bx lr（摔伤一律 0，不扣血）；只在训练岛里改，离开改回原样。
    static final String FALL_O = "f04d2de918b08de2028b2ded", FALL_N = "0000a0e3100a00ee1eff2fe1";
    static volatile String patchState = "";

    static void start() {
        if (running) return;
        if (GameOverlay.remote()) { state = "训练岛防摔：客户端不用（摔伤只在服务端算）"; return; }
        running = true;
        new Thread(() -> {
            long lastScan = 0; int passes = 0;
            try {
                synchronized (pawns) { pawns.clear(); } orig = "";
                patchState = "摔伤函数 " + Mods.write(FALL_BASE, FALL_O, FALL_N, true);
                LogCap.note("训练岛防摔：" + patchState);
                while (Con.training && Con.inMatch) {
                    long b = Mods.base(); if (b == 0) { Thread.sleep(1000); continue; }
                    long now = System.currentTimeMillis();
                    if (now - lastScan > 10000) { lastScan = now; scan(b); }
                    long me = Mods.myPawn(); if (me != 0) synchronized (pawns) { pawns.add(me); }
                    int ok = 0, gone = 0;
                    try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "rw")) {
                        java.util.ArrayList<Long> list; synchronized (pawns) { list = new java.util.ArrayList<>(pawns); }
                        for (long p : list) {
                            String r = apply(m, b, p);
                            if (r == null) ok++; else { gone++; synchronized (pawns) { pawns.remove(p); } }
                        }
                    }
                    passes++;
                    state = (patchState.isEmpty() ? "" : patchState + "；") + (ok > 0 ? "训练岛防摔：ok（人物 " + ok + " 个，摔伤阈值 High/VeryHigh/Kill 原值 " + orig + " → 1e9；第 " + passes + " 遍）"
                                   : "训练岛防摔：还没找到人物（第 " + passes + " 遍" + (gone > 0 ? "，" + gone + " 个不是人物/已没了" : "") + "）");
                    if (passes == 3 || passes % 150 == 0) LogCap.note(state);
                    Thread.sleep(2000);
                }
                state = state + "；已离开训练岛，停";
            } catch (Throwable t) { state = "训练岛防摔：出错 " + t; LogCap.note(state); }
            finally { running = false; patchState = "摔伤函数已改回 " + Mods.write(FALL_BASE, FALL_O, FALL_N, false); }
        }, "zf-nofall").start();
    }

    /** null = 已写好；否则原因（不是人物 / 值不像阈值） */
    static String apply(RandomAccessFile m, long b, long p) throws java.io.IOException {
        if (!isChar(m, b, p)) return "不是人物";
        float[] v = { Mods.f(m, p + T_HIGH), Mods.f(m, p + T_VHIGH), Mods.f(m, p + T_KILL) };
        for (float x : v) if (!(x == BIG || (x > 0f && x < 1e8f))) return "阈值不像（" + v[0] + "/" + v[1] + "/" + v[2] + "）";
        if (orig.isEmpty() && v[0] != BIG) orig = (int) v[0] + "/" + (int) v[1] + "/" + (int) v[2];
        if (v[0] != BIG) Mods.wf(m, p + T_HIGH, BIG);
        if (v[1] != BIG) Mods.wf(m, p + T_VHIGH, BIG);
        if (v[2] != BIG) Mods.wf(m, p + T_KILL, BIG);
        return null;
    }
    /** 虚表 +0x7e8 是 ANZCharacter 摔伤 0xd149a4 或 BR 人物的 0x10e8f00 → 是会摔伤的人物 */
    static boolean isChar(RandomAccessFile m, long b, long p) {
        if (!Mods.isObj(m, b, p)) return false;
        try {
            long vt = Mods.rp(m, p), f = Mods.rp(m, vt + VT_FALL);
            return f == ((b + FALL_BASE) & 0xffffffffL) || f == ((b + FALL_BR) & 0xffffffffL);
        } catch (Throwable t) { return false; }
    }
    /** 扫全部对象：按类缓存，类第一次见到时看实例虚表 +0x7e8 */
    static void scan(long b) {
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
            long g = Server.rd(m, b + 0x51250acL);
            long objs = Server.rd(m, g + 0x10), num = Server.rd(m, g + 0x18);
            if (objs < 0x10000 || num <= 0 || num > 0x200000) return;
            byte[] raw = new byte[(int) num * 16];
            m.seek(objs); m.readFully(raw);
            ByteBuffer bb = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
            HashMap<Long, Boolean> kind = new HashMap<>();
            for (int i = 0; i < num; i++) {
                long o = bb.getInt(i * 16) & 0xffffffffL; int fl = bb.getInt(i * 16 + 4);
                if (o < 0x10000 || (fl & 0x20000000) != 0) continue;
                long c = Server.rd(m, o + 0xc); if (c < 0x10000) continue;
                Boolean k = kind.get(c);
                if (k == null) { k = isChar(m, b, o); kind.put(c, k); }
                if (!k) continue;
                if ((Server.rd(m, o + 4) & 0x10) != 0) continue;   // 类默认对象不写
                synchronized (pawns) { pawns.add(o); }
            }
        } catch (Throwable ignored) {}
    }
}
