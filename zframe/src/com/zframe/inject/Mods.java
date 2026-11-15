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

    public static volatile boolean noPoison, noPlane, male, noRecoil, noShake, infAmmo, autoCoat = true;
    public static volatile float speed = 1f, jump = 1f;
    /** v1.47：开局衣服编号（绝地风衣，找到后存 prefs "coat"） */
    public static volatile String coatId = "30316000007";
    static long base;

    static android.content.SharedPreferences sp; static boolean restored;
    static void save() { if (sp != null) sp.edit().putBoolean("np", wantPlane).putBoolean("npo", wantPoison).putBoolean("male", wantMale).putBoolean("nr", noRecoil).putBoolean("ns", noShake).putBoolean("ia", infAmmo).putFloat("spd", speed).putFloat("jmp", jump).putBoolean("ac", autoCoat).putString("coat", coatId).putFloat("slow", slowMo).apply(); }
    /** 记住的开关：游戏界面出来时重新打上（内存改动每次启动都要重做） */
    static void restore(final android.content.Context c) {
        if (restored) return; restored = true;
        sp = c.getSharedPreferences("zmods", 0);
        // v1.46：启动画面阶段不能改内存（v1.41~1.45 闪退）。v1.47：只读回记住的值，等大厅地图加载完 6 秒后（Con.lobbyReady）再打上
        autoCoat = sp.getBoolean("ac", true); coatId = sp.getString("coat", "30316000007"); slowMo = sp.getFloat("slow", 0.75f);
        speed = sp.getFloat("spd", 1f); jump = sp.getFloat("jmp", 1f);
        noRecoil = sp.getBoolean("nr", false); noShake = sp.getBoolean("ns", false); infAmmo = sp.getBoolean("ia", false);
        wantMale = sp.getBoolean("male", false); wantPlane = sp.getBoolean("np", false); wantPoison = sp.getBoolean("npo", false);
    }
    static boolean wantMale, wantPlane, wantPoison, lateDone;
    /** v1.47：进大厅（引擎和大厅地图都加载完）以后再恢复上次的开关，不在启动画面阶段动内存 */
    static void lateRestore() {
        if (lateDone) return; lateDone = true;
        StringBuilder b = new StringBuilder("延后恢复开关:");
        if (wantMale) b.append(" 男角色=").append(setMale(true));
        if (wantPlane) b.append(" 不上飞机=").append(setPlane(true));
        if (wantPoison) b.append(" 不刷圈=").append(setPoison(true));
        if (speed != 1f || jump != 1f || noRecoil || noShake || infAmmo) { b.append(" 枪/移动线程"); startMover(); }
        LogCap.note(b.toString());
        GameOverlay.refreshAny();
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
        LogCap.note("不刷圈 " + on + " → " + r);
        if ("ok".equals(r)) { noPoison = on; wantPoison = on; save(); }
        return r;
    }

    public static String setPlane(boolean on) {
        String r = "ok";
        if (on) r = write(CAVE, CAVE_O, CAVE_N, true);
        if ("ok".equals(r)) r = write(HOOK, HOOK_O, HOOK_N, on);
        LogCap.note("不上飞机 " + on + " → " + r);
        if ("ok".equals(r)) { noPlane = on; wantPlane = on; save(); }
        return r;
    }

    /** v1.47：进图后自动穿开局衣服（默认绝地风衣，编号在衣服页「找绝地风衣」找到后记住） */
    static void onMatch() {
        lastPawnAsk = 0;
        final String id = coatId;
        if (!autoCoat || id == null || !id.matches("\\d{11}")) return;
        final android.app.Activity a = Con.act; if (a == null) return;
        a.runOnUiThread(() -> { GameOverlay.give(a, id, 1); LogCap.note("开局自动穿衣服 " + id); });
    }
    /** v1.47：正常模式 = 不上飞机、不刷圈都关掉 */
    public static String setNormal() {
        String r = setPlane(false);
        String r2 = setPoison(false);
        write(WAIT, WAIT_O, WAIT_N, false);
        return "ok".equals(r) ? r2 : r;
    }
    public static String setMale(boolean on) {
        String r = write(MALE, MALE_O, MALE_N, on);
        LogCap.note("男角色 " + on + " → " + r);
        if ("ok".equals(r)) { male = on; wantMale = on; save(); }
        return r;
    }

    // ---------- 加速 / 高跳 ----------
    // 偏移（UE4 反射表）：ACharacter Mesh +0x2f4 / CharacterMovement +0x2f8；AActor CustomTimeDilation +0x58；
    // CMC GravityScale +0x120、JumpZVelocity +0x128、MovementMode +0x138（3 = 空中）；
    // USkeletalMeshComponent AnimScriptInstance +0x750、GlobalAnimRateScale +0x7c4
    static final int P_DIL = 0x58, P_MESH = 0x2f4, P_CMC = 0x2f8, C_JUMP = 0x128, C_MODE = 0x138, M_ANIM = 0x750, M_RATE = 0x7c4;
    static final int MOVE_FALLING = 3;
    static volatile long cmc; static float baseJump, lastJump = Float.NaN;
    static Thread mover;
    /** 动作放慢系数：1 = 动作和平时一样快；越小越像「科技」那种滑步 */
    public static volatile float slowMo = 0.75f;
    static volatile String moveState = "";

    static int rd(RandomAccessFile m, long a) throws java.io.IOException {
        byte[] b = new byte[4]; m.seek(a); m.readFully(b);
        return (b[0] & 255) | (b[1] & 255) << 8 | (b[2] & 255) << 16 | (b[3] & 255) << 24;
    }
    static long rp(RandomAccessFile m, long a) throws java.io.IOException { return rd(m, a) & 0xffffffffL; }
    static float f(RandomAccessFile m, long a) throws java.io.IOException { return Float.intBitsToFloat(rd(m, a)); }
    static void wf(RandomAccessFile m, long a, float f) throws java.io.IOException { wi(m, a, Float.floatToIntBits(f)); }
    static void wi(RandomAccessFile m, long a, int v) throws java.io.IOException {
        m.seek(a); m.write(new byte[]{(byte) v, (byte) (v >> 8), (byte) (v >> 16), (byte) (v >> 24)});
    }
    /** 是不是游戏里的 UObject：第一个字（虚表）落在 libUE4 里 */
    static boolean isObj(RandomAccessFile m, long b, long o) {
        if (o < 0x10000 || (o & 3) != 0) return false;
        try { long vt = rp(m, o); return vt >= b && vt <= b + 0x5500000L; } catch (Throwable e) { return false; }
    }

    public static void setGun(boolean r, boolean s, boolean ia) {
        noRecoil = r; noShake = s; infAmmo = ia; save();
        LogCap.note("无后座=" + r + " 聚点=" + s + " 无限子弹=" + ia);
        startMover();
    }
    /** 滑条 0~10：小于 1 就是关（×1） */
    public static void setMove(float s, float j) {
        speed = Math.max(1f, s); jump = Math.max(1f, j); save();
        startMover();
    }
    static boolean active() { return speed != 1f || jump != 1f || noRecoil || noShake || infAmmo || cmc != 0; }

    static synchronized void startMover() {
        if (mover != null) return;
        mover = new Thread(() -> {
            while (true) {
                try { tickMove(); } catch (Throwable t) { LogCap.note("移动线程出错 " + t); }
                // 开加速时要及时发现「起跳」，40ms 查一次；其他时候 200ms
                try { Thread.sleep(speed != 1f ? 40 : 200); } catch (InterruptedException e) { return; }
            }
        }, "zframe-move");
        mover.setDaemon(true);
        mover.start();
    }

    static long lastPawnAsk, lastMesh;
    /** v1.49：只在对局里动人物（大厅里的人物模型不是对局里的角色，写了会闪退） */
    static void tickMove() throws Exception {
        if (!Con.inMatch) { if (cmc != 0) { cmc = 0; GameOverlay.forgetPawn(); } moveState = "进图后生效"; return; }
        if (!active()) return;
        long b = base(); if (b == 0) return;
        long pawn = GameOverlay.pawnAddr();
        long now = System.currentTimeMillis();
        if (pawn == 0) {
            if (now - lastPawnAsk > 4000) { lastPawnAsk = now; GameOverlay.askPawn(); }
            moveState = "等人物"; return;
        }
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "rw")) {
            if (!isObj(m, b, pawn)) { GameOverlay.forgetPawn(); cmc = 0; moveState = "等人物"; return; }
            long c = rp(m, pawn + P_CMC);
            if (!isObj(m, b, c)) { cmc = 0; moveState = "等人物"; return; }
            if (c != cmc) { cmc = c; baseJump = f(m, c + C_JUMP); lastJump = Float.NaN; LogCap.note(String.format("移动组件 %x 跳=%.0f", c, baseJump)); }
            gun(m, b, pawn);
            jump(m, c);
            dilation(m, b, pawn, c);
            moveState = "已生效";
        }
    }

    /** 高跳：JumpZVelocity = 原值 × 倍数。只在读到的是原值或我们上次写的值时才写（对象换了就不写） */
    static void jump(RandomAccessFile m, long c) throws java.io.IOException {
        float jz = f(m, c + C_JUMP);
        boolean ours = !Float.isNaN(lastJump) && Math.abs(jz - lastJump) < 0.01f;
        if (!ours) {
            if (!(jz >= 100f && jz <= 2000f)) return;     // 不像跳跃速度，不碰
            baseJump = jz;                                // 游戏自己改过（换人物/状态）→ 重新记原值
        }
        float want = baseJump * jump;
        if (Math.abs(jz - want) > 0.01f) wf(m, c + C_JUMP, want);
        lastJump = want;
    }

    /** 加速：人物自己的时间倍率 = 倍数（只快在位移上），动画按 slowMo 放慢；人在空中时恢复 1，跳跃才正常 */
    static void dilation(RandomAccessFile m, long b, long pawn, long c) throws java.io.IOException {
        int mode = rd(m, c + C_MODE) & 0xff;
        boolean air = mode == MOVE_FALLING;
        float wantDil = speed == 1f || air ? 1f : speed;
        float td = f(m, pawn + P_DIL);
        if (td > 0.05f && td < 20f && Math.abs(td - wantDil) > 0.001f) wf(m, pawn + P_DIL, wantDil);
        long mesh = rp(m, pawn + P_MESH);
        if (!isObj(m, b, mesh)) return;
        if (mesh != lastMesh) { lastMesh = mesh; LogCap.note(String.format("骨骼网格 %x", mesh)); }
        float ar = f(m, mesh + M_RATE);
        float want = wantDil == 1f ? 1f : Math.max(0.1f, slowMo / wantDil);
        if (ar > 0.01f && ar < 20f && Math.abs(ar - want) > 0.001f) wf(m, mesh + M_RATE, want);
    }

    // ---------- 枪械：人物 CurrWeapon(+0x890) ----------
    // 子弹：ClipAmmoCount 0x52c（弹匣容量），ServerWeaponAmmoCount 0x604，WeaponAmmoCount 0x608
    // 扩散：CrosshairExpandRatio 0x574 / Normal 0x578 / Zooming 0x57c / ZoomingWalk 0x580 / ShootAiming 0x5ac
    static long lastGunLog;
    static void gun(RandomAccessFile m, long b, long pawn) throws java.io.IOException {
        if (!noRecoil && !noShake && !infAmmo) return;
        long w = rp(m, pawn + 0x890);
        if (!isObj(m, b, w)) return;
        int clip = rd(m, w + 0x52c);
        long now = System.currentTimeMillis();
        if (now - lastGunLog > 10000) { lastGunLog = now; LogCap.note(String.format("当前武器 %x 弹匣=%d 子弹=%d", w, clip, rd(m, w + 0x608))); }
        if (noRecoil || noShake) paramTable(m, b, pawn);
        if (noShake) { wf(m, w + 0x574, 0f); wf(m, w + 0x578, 0f); wf(m, w + 0x57c, 0f); wf(m, w + 0x580, 0f); wf(m, w + 0x5ac, 0f); }
        // 无限子弹：枪里一直是 999 发（只给枪，近战/投掷弹匣是 0 不动）
        if (infAmmo && clip > 0 && clip <= 200 && rd(m, w + 0x608) != 999) { wi(m, w + 0x608, 999); wi(m, w + 0x604, 999); }
    }

    /* 后座/扩散参数表 NZWeapParamsTable（0x254 字节）：
     *   +0x004 SpreadAccuracyRatios、+0x05c CrosshairAccuracyRatios（Jump 0x3c / Prone 0x40 / ProneWalk 0x44 / Stand 0x48 / StandWalk 0x4c / Crouch 0x50 / CrouchWalk 0x54）
     *   +0x0b4 SpreadParamTable（Min 0x3c .. IncreaseSpeed_Firing 0x54）
     *   +0x118 RecoilParamTable（Time 0x3c, UpMin 0x44 .. SideRange 0x60）
     *   +0x17c CameraRecoilParamTable（Base 0x8, Modifier 0xc, Max 0x10）
     *   +0x1ac bUseCFMFireMode，+0x1b0 CFMParams（不准度 0x8/0xc，散布 0x10-0x1c，后座 0x28-0x3c，后座倍数 0x48-0x50，准星 0x5c-0x88，0x8c）
     * v1.49：游戏只把它复制到人物动画实例的 WeaponParamsTable（AnimInstance +0x18c4）。
     * 路径固定：人物 → Mesh(+0x2f4) → AnimScriptInstance(+0x750) → +0x18c4，不再按特征到处找（v1.47 误写 896 处闪退）。 */
    static final int WPT = 0x18c4;
    static long tableAi, tableAt;
    static void paramTable(RandomAccessFile m, long b, long pawn) throws java.io.IOException {
        long mesh = rp(m, pawn + P_MESH);
        if (!isObj(m, b, mesh)) return;
        long ai = rp(m, mesh + M_ANIM);
        if (!isObj(m, b, ai)) return;
        if (ai != tableAi) {
            byte[] buf = new byte[0x254];
            m.seek(ai + WPT); m.readFully(buf);
            boolean ok = looksTable(buf);
            LogCap.note(String.format("后座参数表 动画实例 %x → %s CFM=%d", ai, ok ? "认出" : "不像，不改", buf[0x1ac]));
            tableAi = ai; tableAt = ok ? ai + WPT : 0;
        }
        if (tableAt == 0) return;
        long t = tableAt;
        if (noRecoil) {
            for (int k = 0x44; k <= 0x60; k += 4) wf(m, t + 0x118 + k, 0f);                          // 后座 上/左右
            wf(m, t + 0x17c + 8, 0f); wf(m, t + 0x17c + 0xc, 0f); wf(m, t + 0x17c + 0x10, 0f);       // 镜头后座
            for (int k = 0x28; k <= 0x3c; k += 4) wf(m, t + 0x1b0 + k, 0f);                          // CFM 后座
            for (int k = 0x48; k <= 0x50; k += 4) wf(m, t + 0x1b0 + k, 0f);                          // CFM 后座倍数
        }
        if (noShake) {
            for (int k = 0x3c; k <= 0x54; k += 4) wf(m, t + 0xb4 + k, 0f);                           // 扩散
            for (long a : new long[]{t + 0x4, t + 0x5c}) {                                           // 跳/走/趴 的准度 = 站立
                float st = f(m, a + 0x48);
                if (st > 0 && st < 20) for (int k = 0x3c; k <= 0x54; k += 4) wf(m, a + k, st);
            }
            long cf = t + 0x1b0;
            wf(m, cf + 0x8, 0f); wf(m, cf + 0xc, 0f);
            for (int k = 0x10; k <= 0x1c; k += 4) wf(m, cf + k, 0f);
            float sz = f(m, cf + 0x5c), ln = f(m, cf + 0x64);
            wf(m, cf + 0x6c, sz); wf(m, cf + 0x74, ln); wf(m, cf + 0x7c, sz); wf(m, cf + 0x84, ln);
            for (int k : new int[]{0x60, 0x68, 0x70, 0x78, 0x80, 0x88, 0x8c}) wf(m, cf + k, 0f);
        }
    }
    /** 表的特征（固定路径上再核对一次）：数值都在合理范围，没有 NaN */
    static boolean looksTable(byte[] buf) {
        java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(buf).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        if ((buf[0x1ac] & 0xff) > 1 || buf[0x1ad] != 0 || buf[0x1ae] != 0 || buf[0x1af] != 0) return false;
        int[][] rg = {{0x118 + 0x3c, 0x118 + 0x60}, {0x17c + 8, 0x17c + 0x10}, {0xb4 + 0x3c, 0xb4 + 0x54}, {0x1b0 + 0x8, 0x1b0 + 0x8c}, {0x4 + 0x3c, 0x4 + 0x54}, {0x5c + 0x3c, 0x5c + 0x54}};
        for (int[] q : rg) for (int k = q[0]; k <= q[1]; k += 4) {
            float v = bb.getFloat(k);
            if (Float.isNaN(v) || Float.isInfinite(v) || Math.abs(v) > 10000f) return false;
            if (v != 0f && Math.abs(v) < 1e-6f) return false;     // 指针/整数当浮点看是极小数
        }
        float upMin = bb.getFloat(0x118 + 0x44), upMax = bb.getFloat(0x118 + 0x4c);
        return upMin >= 0 && upMax >= 0 && upMax < 100;
    }
}
