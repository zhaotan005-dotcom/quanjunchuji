package com.zframe.inject;

import java.io.RandomAccessFile;
import java.util.HashMap;
import java.util.LinkedHashSet;

/** v4.10 训练岛保命（用户 11-22 20:39：训练岛里被刷出来的摩托炸死「你因载具爆炸而被淘汰」，还会淹死）。只在有权威的一端（单机 / 服务端房主）跑，客户端不跑（伤害在服务端算）。
 *
 *  以前为什么挡不住（静态 RE，libUE4 armv7）：
 *  - MinHp（训练场锁血）只钩 GameMode 伤害链 vt+0x484（0xcc0e10）。人物扣血在 0xbe0618（人物 TakeDamage 0xd2230c 在 0xd2270c/0xd22b00 调它）：
 *    0xbe0a84 那次 GM vt+0x484 只在 GameMode 类检查通过时才调；0xbe0e48/0xbe0ed0 两处 ldr/sub/str [人物+0x5a0] 都能不经过 MinHp 的钩子扣血。
 *  - MinHp 指向的人物是 Mods.livePawn() = 控制器 +0x2c0 的 Pawn —— 人在车上时那是车，不是人 → 开车被炸时锁血指着车（v3.96 记录的 InVehicleExplosion 就是这种）。
 *  - MinHp/NoFall 由 Island.run 在 GameMode/地块管理器/出生点都找到之后才启动，前面任何一步 return 都不启动。
 *  - 淹死：人物 Tick 里的 0xd028a0（憋气）：在水下 CurrentBreathHoldTime(+0xc30，float) += DeduceBreathTimeScale(+0xc40)×dt，夹到 MaxBreathHoldTime(+0xc34)；
 *    到上限后置 +0xc44=1，+0xc48 每秒累计到 1.0 就在权威端（Role +0xe8 == 3）调人物 TakeDamage(vt+0x2a0)，伤害 = HealthMax(+0x5a4，int) × +0xc38。出水 +0xc30 按 +0xc3c 往回减到 0。
 *
 *  现在（只写内存，不改代码字节、不扫大内存）：
 *  - 每 1 秒问一次自己的人物（控制器 Pawn，且虚表 +0x7e8 是人物摔伤函数才算人物；在车上时保留上车前的人物），房主再加上 NoFall 扫到的、有 PlayerState(+0x2c4) 的人物（别的玩家；靶子假人没有 PlayerState 不保护）。
 *  - 每 0.1 秒对这些人物：
 *    · 血(+0x5a0 int) < 上限×10 就写成 上限×20（上限 +0x5a4，1..10000 才动；血 ≤ 0 已死不动）→ 单次伤害（车炸、摔、淹、枪）都打不到 0，受伤动作/声音照旧有；
 *    · 关 bCanBeDamaged（AActor +0x110 半字 bit1，和 Mods.godTick 一样）；
 *    · 憋气清零：+0xc30 = 0、+0xc44 = 0、+0xc48 = 0（+0xc34 上限读出来是 0..10000 的 float 才写）→ 永远到不了上限，不掉血不淹死。
 *  - MinHp 改为指向这里认出的人物（不是车）；NoFall/MinHp 进训练岛立刻启动（不等 Island.run）。
 *  - 刷出来的车（Veh.place 用射线确认到的那辆）记下来，每 0.1 秒血补满（NZVehicle Health +0x308 = MaxHealth +0x2f8），离开训练岛清掉。 */
final class TrainSafe {
    static final int HP = 0x5a0, HPMAX = 0x5a4, CANDMG = 0x110, PS = 0x2c4;
    static final int BREATH = 0xc30, BREATH_MAX = 0xc34, BREATH_FLAG = 0xc44, BREATH_T = 0xc48;
    static volatile String state = "训练岛保命：没开";
    static volatile boolean running;
    static volatile long me;
    static final LinkedHashSet<Long> chars = new LinkedHashSet<>();
    static final HashMap<Long, Long> vehs = new HashMap<>();   // 车 → 虚表
    static volatile int refills, breathResets, vehRepairs;

    static void addVehicle(long v) {
        long b = Mods.base(); if (b == 0 || v < 0x10000) return;
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
            if (!Mods.isObj(m, b, v)) return;
            synchronized (vehs) { vehs.put(v, Mods.rp(m, v)); if (vehs.size() > 64) vehs.remove(vehs.keySet().iterator().next()); }
        } catch (Throwable ignored) {}
    }

    /** MinHp 用：认出的自己人物（0 = 还没有） */
    static long me() { return me; }

    static void start() {
        if (running) return;
        if (GameOverlay.remote()) { state = "训练岛保命：客户端不用（伤害在服务端算）"; return; }
        running = true;
        refills = 0; breathResets = 0; vehRepairs = 0;
        try { NoFall.start(); } catch (Throwable ignored) {}
        try { MinHp.start(); } catch (Throwable ignored) {}
        new Thread(() -> {
            long lastAsk = 0; int n = 0;
            try {
                while (Con.training && Con.inMatch) {
                    long b = Mods.base(); if (b == 0) { Thread.sleep(500); continue; }
                    long now = System.currentTimeMillis();
                    if (now - lastAsk > 1000) {
                        lastAsk = now;
                        long p = 0; try { p = Mods.livePawn(); } catch (Throwable ignored) {}
                        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
                            if (p != 0 && NoFall.isChar(m, b, p)) { me = p; synchronized (chars) { chars.add(p); } }
                            java.util.ArrayList<Long> ns; synchronized (NoFall.pawns) { ns = new java.util.ArrayList<>(NoFall.pawns); }
                            for (long q : ns) if (Mods.isObj(m, b, Mods.rp(m, q + PS)) && NoFall.isChar(m, b, q)) synchronized (chars) { chars.add(q); }
                        }
                        n++;
                        int nc; synchronized (chars) { nc = chars.size(); }
                        int nv; synchronized (vehs) { nv = vehs.size(); }
                        state = "训练岛保命：人物 " + nc + " 个（我 " + Long.toHexString(me) + "），补血 " + refills + " 次，憋气清零 " + breathResets + " 次，车 " + nv + " 辆补血 " + vehRepairs + " 次";
                        if (n == 3) LogCap.note(state);
                    }
                    tick(b);
                    Thread.sleep(100);
                }
                state = state + "；已离开训练岛，停";
            } catch (Throwable t) { state = "训练岛保命：出错 " + t; LogCap.note(state); }
            finally { running = false; me = 0; synchronized (chars) { chars.clear(); } synchronized (vehs) { vehs.clear(); } }
        }, "zf-trainsafe").start();
    }

    static void tick(long b) {
        java.util.ArrayList<Long> cs; synchronized (chars) { cs = new java.util.ArrayList<>(chars); }
        java.util.ArrayList<java.util.Map.Entry<Long, Long>> vs; synchronized (vehs) { vs = new java.util.ArrayList<>(vehs.entrySet()); }
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "rw")) {
            for (long p : cs) {
                if (!NoFall.isChar(m, b, p)) { synchronized (chars) { chars.remove(p); } if (p == me) me = 0; continue; }
                int mx = Mods.rd(m, p + HPMAX), hp = Mods.rd(m, p + HP);
                if (mx >= 1 && mx <= 10000 && hp > 0 && hp < mx * 10) { Mods.wi(m, p + HP, mx * 20); refills++; }
                int cb = Mods.rd(m, p + CANDMG) & 0xffff;
                if ((cb & 2) != 0) { m.seek(p + CANDMG); m.write(new byte[] { (byte) (cb & ~2), (byte) (cb >> 8) }); }
                float bm = Mods.f(m, p + BREATH_MAX), bc = Mods.f(m, p + BREATH);
                if (bm > 0f && bm < 10000f && !Float.isNaN(bc) && bc > 0f && bc <= bm + 1f) {
                    Mods.wf(m, p + BREATH, 0f); breathResets++;
                    if ((Mods.rd(m, p + BREATH_FLAG) & 0xff) != 0) { int w = Mods.rd(m, p + BREATH_FLAG); Mods.wi(m, p + BREATH_FLAG, w & ~0xff); }
                    float t = Mods.f(m, p + BREATH_T); if (t > 0f && t < 2f) Mods.wf(m, p + BREATH_T, 0f);
                }
            }
            for (java.util.Map.Entry<Long, Long> e : vs) {
                long v = e.getKey();
                if (!Mods.isObj(m, b, v) || Mods.rp(m, v) != e.getValue()) { synchronized (vehs) { vehs.remove(v); } continue; }
                int mx = Mods.rd(m, v + Veh.V_MAXHP), hp = Mods.rd(m, v + Veh.V_HP);
                if (mx > 0 && mx <= 100000 && hp > 0 && hp < mx) { Mods.wi(m, v + Veh.V_HP, mx); vehRepairs++; }
            }
        } catch (Throwable ignored) {}
    }
}
