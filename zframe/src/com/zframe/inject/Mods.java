package com.zframe.inject;

import java.io.RandomAccessFile;

/**
 * v1.37：游戏内开关，直接改游戏内存（/proc/self/mem，先核对原字节，对不上就不写）。
 *  - 不刷毒：毒圈 Tick(0xd97df0) 开头改成 bx lr
 *  - 不上飞机：SetGamePhase(0xffc1e4) 跳到 GM 面板里没用的函数(0x171e4b0)，阶段 1/2 改成 4
 *  - 男角色：InitAvatarProxy 没有角色时默认塞 10200000000(女)，改成 10100000000(男)，下一局生效
 *  - 加速/高跳：人物 CharacterMovement(+0x34c) 的 MaxWalkSpeed(+0x164)/蹲走(+0x168)/JumpZVelocity(+0x128) 乘倍数
 */
public class Mods {
    static final long POISON = 0xd97df0L, HOOK = 0xffc1e4L, CAVE = 0x171e4b0L, MALE = 0x10d76d8L;
    static final String POISON_O = "f04f2de9", POISON_N = "1eff2fe1";
    static final String HOOK_O = "0040a0e1", HOOK_N = "b1881cea";
    static final String CAVE_O = "f04b2de918b08de220d04de20080a0e144039fe50150a0e1";
    static final String CAVE_N = "71c0efe601005ce302005c130410a0030040a0e14777e3ea"; // v1.40：阶段1(倒计时)/2(上飞机) 直接改成 4(落地开打)，不显示读秒、不刷航线
    // 非正常演习：开局等人时间(表里读 60 秒)改成 1 秒 —— NZBRGameState 初始化 0x1019774 ldr r0,[r6,#0x38] → mov r0,#1.0f
    static final long WAIT = 0x1019774L; static final String WAIT_O = "380096e5", WAIT_N = "fe05a0e3";
    // v1.41~1.43 的开火不扣子弹代码补丁(0xefd104)会让游戏启动几秒就闪退（扣子弹代码有校验），已删除，别再改这里
    static final long AMMO = 0xefd104L; static final String AMMO_O = "065045e0", AMMO_N = "0550a0e1";
    static final String MALE_O = "00560ae30210a0e3f75f45e3", MALE_N = "00550ce30210a0e3015a45e3";

    public static volatile boolean noPoison, noPlane, male, noRecoil, noShake, infAmmo;
    public static volatile float speed = 1f, jump = 1f;
    static long base;

    static android.content.SharedPreferences sp; static boolean restored;
    static void save() { if (sp != null) sp.edit().putBoolean("np", noPlane).putBoolean("npo", noPoison).putBoolean("male", male).putBoolean("nr", noRecoil).putBoolean("ns", noShake).putBoolean("ia", infAmmo).putFloat("spd", speed).putFloat("jmp", jump).apply(); }
    /** 记住的开关：游戏界面出来时重新打上（内存改动每次启动都要重做） */
    static void restore(final android.content.Context c) {
        if (restored) return; restored = true;
        sp = c.getSharedPreferences("zmods", 0);
        // v1.46：启动不再自动恢复任何开关（v1.41~1.45 启动闪退），每次进游戏在菜单里手动打开
    }

    static synchronized long base() {
        if (base == 0) base = Patches.findBase("libUE4.so");
        return base;
    }

    static String write(long off, String o, String n, boolean on) {
        long b = base();
        if (b == 0) return "找不到游戏库";
        byte[] want = Patches.hex(on ? n : o), other = Patches.hex(on ? o : n), cur = new byte[want.length];
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "rw")) {
            m.seek(b + off); m.readFully(cur);
            if (java.util.Arrays.equals(cur, want)) return "ok";
            if (!java.util.Arrays.equals(cur, other)) return "原字节不符 " + Patches.hexs(cur);
            m.seek(b + off); m.write(want);
            m.seek(b + off); m.readFully(cur);
            return java.util.Arrays.equals(cur, want) ? "ok" : "写入没生效";
        } catch (Throwable t) { return "出错 " + t.getMessage(); }
    }

    public static String setPoison(boolean on) {
        String r = write(POISON, POISON_O, POISON_N, on);
        LogCap.note("不刷毒 " + on + " → " + r);
        if ("ok".equals(r)) { noPoison = on; save(); }
        return r;
    }

    public static String setPlane(boolean on) {
        String r = "ok";
        if (on) r = write(CAVE, CAVE_O, CAVE_N, true);
        if ("ok".equals(r)) r = write(HOOK, HOOK_O, HOOK_N, on);
        LogCap.note("不上飞机 " + on + " → " + r);
        if ("ok".equals(r)) { noPlane = on; save(); }
        return r;
    }

    /** 正常演习 / 非正常演习：不等人、不倒计时、不上飞机、不刷毒，进图直接开打 */
    public static String setFree(boolean on) {
        String r = setPlane(on);
        if ("ok".equals(r)) r = setPoison(on);
        // v1.45：v1.41~1.44 启动闪退，开局等待补丁 0x1019774 在启动时打上会崩，已停用；只在关时还原
        if (!on) write(WAIT, WAIT_O, WAIT_N, false);
        return r;
    }
    public static String setMale(boolean on) {
        String r = write(MALE, MALE_O, MALE_N, on);
        LogCap.note("男角色 " + on + " → " + r);
        if ("ok".equals(r)) { male = on; save(); }
        return r;
    }

    // ---------- 加速 / 高跳 ----------
    static long cmc; static float baseWalk, baseCrouch, baseJump, lastWalk, lastCrouch, lastJump;
    static Thread mover;

    static int rd(RandomAccessFile m, long a) throws java.io.IOException {
        byte[] b = new byte[4]; m.seek(a); m.readFully(b);
        return (b[0] & 255) | (b[1] & 255) << 8 | (b[2] & 255) << 16 | (b[3] & 255) << 24;
    }
    static void wf(RandomAccessFile m, long a, float f) throws java.io.IOException {
        int v = Float.floatToIntBits(f);
        m.seek(a); m.write(new byte[]{(byte) v, (byte) (v >> 8), (byte) (v >> 16), (byte) (v >> 24)});
    }

    /** pawn 地址来自刷物品补丁的诊断缓冲区（AddAvaItem P0 只填人物地址，不刷东西） */
    public static void setGun(boolean r, boolean s, boolean ia) {
        noRecoil = r; noShake = s; infAmmo = ia; save();
        LogCap.note("无后座=" + r + " 防抖=" + s + " 无限子弹=" + ia);
        startMover();
    }
    public static void setMove(float s, float j) {
        speed = s; jump = j; save();
        startMover();
    }
    static synchronized void startMover() {
        if (mover != null) return;
        mover = new Thread(() -> {
            while (true) {
                try { tickMove(); } catch (Throwable t) { LogCap.note("加速出错 " + t); }
                try { Thread.sleep(150); } catch (InterruptedException e) { return; }
            }
        }, "zframe-move");
        mover.setDaemon(true);
        mover.start();
    }

    static long lastPawnAsk; static String moveState = "";
    static void tickMove() throws Exception {
        if (speed == 1f && jump == 1f && cmc == 0 && !noRecoil && !noShake && !infAmmo) return;
        long b = base(); if (b == 0) return;
        long now = System.currentTimeMillis();
        long pawn = GameOverlay.pawnAddr();
        if (pawn == 0 && now - lastPawnAsk > 4000) { lastPawnAsk = now; GameOverlay.askPawn(); }
        if (pawn == 0) { moveState = "等人物（刷一下任意物品就能生效）"; return; }
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "rw")) {
            gun(m, b, pawn);
            // ACharacter：Mesh +0x2f4，CharacterMovement +0x2f8，Capsule +0x2fc（v1.37 用的 0x34c 是另一个类的）
            long c = rd(m, pawn + 0x2f8) & 0xffffffffL;
            if (c == 0) { moveState = "没有移动组件"; return; }
            long vt = rd(m, c) & 0xffffffffL;
            if (vt < b || vt > b + 0x5500000L) { moveState = "等人物"; cmc = 0; GameOverlay.forgetPawn(); return; }
            float w = Float.intBitsToFloat(rd(m, c + 0x164)), cr = Float.intBitsToFloat(rd(m, c + 0x168)), jz = Float.intBitsToFloat(rd(m, c + 0x128));
            if (c != cmc) { cmc = c; baseWalk = w; baseCrouch = cr; baseJump = jz; lastWalk = lastCrouch = lastJump = Float.NaN;
                LogCap.note(String.format("移动组件 %x 走=%.0f 蹲=%.0f 跳=%.0f", c, w, cr, jz)); }
            // 游戏自己改过（换姿势/跑步）就当新的基准
            if (w != lastWalk) baseWalk = w;
            if (cr != lastCrouch) baseCrouch = cr;
            if (jz != lastJump) baseJump = jz;
            // 走路速度游戏每帧自己算（MaxWalkSpeed 改了没用），改人物自己的时间倍率 CustomTimeDilation(+0x58)
            float td = Float.intBitsToFloat(rd(m, pawn + 0x58));
            if (td > 0.05f && td < 20f && td != speed) wf(m, pawn + 0x58, speed);
            if (baseJump > 0 && baseJump < 5000) { lastJump = baseJump * jump; wf(m, c + 0x128, lastJump); }
            moveState = "已生效";
        }
    }

    // ---------- 无后座 / 防抖 / 无限子弹：人物 CurrWeapon(+0x890) 上的武器字段 ----------
    // RecoilScaleBase_Vert 0x584 / Hori 0x588 / ExtraHitRate 0x58c；扩散：Normal 0x578 / Zooming 0x57c / ZoomingWalk 0x580 / ShootAiming 0x5ac
    // 子弹：ClipAmmoCount 0x52c（弹匣容量），ServerWeaponAmmoCount 0x604，WeaponAmmoCount 0x608
    static long lastGunLog;
    static void gun(RandomAccessFile m, long b, long pawn) throws java.io.IOException {
        if (!noRecoil && !noShake && !infAmmo) return;
        long w = rd(m, pawn + 0x890) & 0xffffffffL;
        if (w == 0) return;
        long vt = rd(m, w) & 0xffffffffL;
        if (vt < b || vt > b + 0x5500000L) return;
        int clip = rd(m, w + 0x52c);
        long now = System.currentTimeMillis();
        if (now - lastGunLog > 10000) { lastGunLog = now; LogCap.note(String.format("当前武器 %x 弹匣=%d 子弹=%d", w, clip, rd(m, w + 0x608))); }
        // RecoilScaleBase(0x584..) 是游戏随机的加密倍数，≤0.5 会被重新随机成 1~10 倍 → v1.40 写 0 反而飘上天，不能碰
        if (noRecoil || noShake) recoilTables(m, b, w);
        if (noShake) { wf(m, w + 0x578, 0f); wf(m, w + 0x57c, 0f); wf(m, w + 0x580, 0f); wf(m, w + 0x5ac, 0f); }
        // 无限子弹：游戏自带控制台命令 InfiniteAmmo（界面显示 ∞），另加 0xefd104 补丁开枪不扣兜底
        if (infAmmo && clip > 0 && clip <= 200) {
            if (rd(m, w + 0x608) < clip) { wi(m, w + 0x608, clip); wi(m, w + 0x604, clip); }
        }
    }
    /** 后座参数表在武器的某个组件里：RecoilParamTable +0x118、CameraRecoilParamTable +0x17c、SpreadParamTable +0xb4。
     *  候选 = 武器 +0x3f0..+0x418 的组件指针 + WeaponComponentList(+0x43c)，按数值是否像后座表来认。 */
    static final java.util.Map<Long, Long> recoilObj = new java.util.HashMap<>();
    static void recoilTables(RandomAccessFile m, long b, long w) throws java.io.IOException {
        Long o = recoilObj.get(w);
        if (o == null) {
            o = 0L;
            java.util.List<Long> c = new java.util.ArrayList<>();
            for (int off = 0x3f0; off <= 0x418; off += 4) c.add(rd(m, w + off) & 0xffffffffL);
            long arr = rd(m, w + 0x43c) & 0xffffffffL; int n = rd(m, w + 0x440);
            if (arr != 0 && n > 0 && n < 32) for (int i = 0; i < n; i++) c.add(rd(m, arr + 4L * i) & 0xffffffffL);
            for (long x : c) {
                if (x < 0x10000) continue;
                try {
                    long vt = rd(m, x) & 0xffffffffL; if (vt < b || vt > b + 0x5500000L) continue;
                    float t = f(m, x + 0x118 + 0x3c), upMin = f(m, x + 0x118 + 0x44), upMax = f(m, x + 0x118 + 0x4c), sMin = f(m, x + 0x118 + 0x54), sMax = f(m, x + 0x118 + 0x5c);
                    if (t > 0 && t < 5 && upMin >= 0 && upMin <= upMax && upMax < 60 && sMin <= sMax && Math.abs(sMax) < 60 && upMax > 0) { o = x; break; }
                } catch (Throwable ignored) {}
            }
            recoilObj.put(w, o);
            LogCap.note(String.format("后座表 武器%x → %s", w, o == 0 ? "没找到" : Long.toHexString(o)));
        }
        if (o == 0) return;
        long r = o + 0x118;
        if (noRecoil) {
            for (int k = 0x44; k <= 0x60; k += 4) wf(m, r + k, 0f);       // RecoilUpMin..RecoilSideRange
            long cr = o + 0x17c; wf(m, cr + 8, 0f); wf(m, cr + 0xc, 0f); wf(m, cr + 0x10, 0f); // 镜头后座
        }
        if (noShake) { long s = o + 0xb4; for (int k = 0x3c; k <= 0x4c; k += 4) wf(m, s + k, 0f); } // SpreadMin..SpreadModifierMax
    }
    static float f(RandomAccessFile m, long a) throws java.io.IOException { return Float.intBitsToFloat(rd(m, a)); }
    static void wi(RandomAccessFile m, long a, int v) throws java.io.IOException {
        m.seek(a); m.write(new byte[]{(byte) v, (byte) (v >> 8), (byte) (v >> 16), (byte) (v >> 24)});
    }
}
