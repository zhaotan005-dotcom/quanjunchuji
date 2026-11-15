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
    public static String flowText() {
        if (!noPlane && !noPoison) return "正常模式（正常上飞机、正常刷圈）";
        if (noPlane && noPoison) return "不上飞机 + 不刷圈";
        return noPlane ? "不上飞机（毒圈照常刷）" : "不刷圈（照常上飞机）";
    }
    public static String setMale(boolean on) {
        String r = write(MALE, MALE_O, MALE_N, on);
        LogCap.note("男角色 " + on + " → " + r);
        if ("ok".equals(r)) { male = on; wantMale = on; save(); }
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
    static long animMesh; static float animSet = Float.NaN;
    static void tickMove() throws Exception {
        if (speed == 1f && jump == 1f && cmc == 0 && !noRecoil && !noShake && !infAmmo) return;
        long b = base(); if (b == 0) return;
        long now = System.currentTimeMillis();
        long pawn = GameOverlay.pawnAddr();
        if (pawn == 0 && now - lastPawnAsk > 4000) { lastPawnAsk = now; GameOverlay.askPawn(); }
        if (pawn == 0) { moveState = "等进图（进图后自动生效）"; return; }
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "rw")) {
            gun(m, b, pawn);
            // ACharacter：Mesh +0x2f4，CharacterMovement +0x2f8，Capsule +0x2fc
            long c = rd(m, pawn + 0x2f8) & 0xffffffffL;
            if (c == 0) { moveState = "没有移动组件"; return; }
            long vt = rd(m, c) & 0xffffffffL;
            if (vt < b || vt > b + 0x5500000L) { moveState = "等人物"; cmc = 0; GameOverlay.forgetPawn(); return; }
            float jz = Float.intBitsToFloat(rd(m, c + 0x128));
            if (c != cmc) { cmc = c; baseJump = jz; lastJump = Float.NaN; animMesh = 0; animSet = Float.NaN;
                LogCap.note(String.format("移动组件 %x 跳=%.0f", c, jz)); }
            if (jz != lastJump) baseJump = jz;
            // v1.47 加速（科技那种）：人物自己的时间倍率 CustomTimeDilation(+0x58)=倍数 → 走/跑位移变快；
            // 同时把人物骨骼网格 GlobalAnimRateScale(USkeletalMeshComponent +0x7c4) 设成 1/倍数，动作放慢回正常速度，只快在移动上
            float td = Float.intBitsToFloat(rd(m, pawn + 0x58));
            if (td > 0.05f && td < 20f && td != speed) wf(m, pawn + 0x58, speed);
            long mesh = rd(m, pawn + 0x2f4) & 0xffffffffL;
            if (mesh != 0) {
                long mvt = rd(m, mesh) & 0xffffffffL;
                if (mvt >= b && mvt <= b + 0x5500000L) {
                    float ar = Float.intBitsToFloat(rd(m, mesh + 0x7c4));
                    float want = speed == 1f ? 1f : Math.max(0.1f, 1f / speed * slowMo);
                    if (mesh != animMesh) { animMesh = mesh; LogCap.note(String.format("骨骼网格 %x 动作倍率=%.2f", mesh, ar)); }
                    if (ar > 0.01f && ar < 20f && Math.abs(ar - want) > 0.001f) wf(m, mesh + 0x7c4, want);
                }
            }
            if (baseJump > 0 && baseJump < 5000) { lastJump = baseJump * jump; wf(m, c + 0x128, lastJump); }
            moveState = "已生效";
        }
    }
    /** 动作放慢系数：1 = 动作看起来和平时一样快；0.6 = 比平时还慢一点（像科技那种滑步） */
    public static volatile float slowMo = 0.75f;

    // ---------- 枪械：人物 CurrWeapon(+0x890) ----------
    // 子弹：ClipAmmoCount 0x52c（弹匣容量），ServerWeaponAmmoCount 0x604，WeaponAmmoCount 0x608
    // 扩散：CrosshairExpandRatio 0x574 / Normal 0x578 / Zooming 0x57c / ZoomingWalk 0x580 / ShootAiming 0x5ac
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
        if (noRecoil || noShake) paramTables(m, b, pawn, w);
        if (noShake) { wf(m, w + 0x574, 0f); wf(m, w + 0x578, 0f); wf(m, w + 0x57c, 0f); wf(m, w + 0x580, 0f); wf(m, w + 0x5ac, 0f); }
        // v1.47 无限子弹：枪里一直是 999 发（只给枪，近战/投掷弹匣是 0 不动）
        if (infAmmo && clip > 0 && clip <= 200) {
            if (rd(m, w + 0x608) != 999) { wi(m, w + 0x608, 999); wi(m, w + 0x604, 999); }
        }
    }

    /* v1.47 无后座修复：
     * 后座/扩散参数不在武器组件里，而是一整块 NZWeapParamsTable（0x254 字节）：
     *   +0x004 SpreadAccuracyRatios、+0x05c CrosshairAccuracyRatios（AccuracyRatio_Jump 0x3c / Prone 0x40 / ProneWalk 0x44 / Stand 0x48 / StandWalk 0x4c / Crouch 0x50 / CrouchWalk 0x54）
     *   +0x0b4 SpreadParamTable（SpreadMin 0x3c, Max 0x40, ModifierBase 0x44, Inc 0x48, ModMax 0x4c, IncreaseSpeed 0x50, IncreaseSpeed_Firing 0x54）
     *   +0x118 RecoilParamTable（RecoilTime 0x3c, UpMin 0x44, UpModifier 0x48, UpMax 0x4c, UpTotalMax 0x50, SideMin 0x54, SideModifier 0x58, SideMax 0x5c, SideRange 0x60）
     *   +0x17c CameraRecoilParamTable（Base 0x8, Modifier 0xc, Max 0x10）
     *   +0x1ac bUseCFMFireMode（为 1 时走穿越火线式后座，用下面这块，v1.46 没改这块所以还有后座）
     *   +0x1b0 CFMParams（m_MaxInaccuracy 0x8, m_MinInaccuracy 0xc, m_DisperseBase 0x10, Disperse 站/跳/走 0x14-0x1c,
     *          m_RecoilUpBase 0x28, UpModifier 0x2c, UpMax 0x30, LateralBase 0x34, LateralModifier 0x38, LateralMax 0x3c,
     *          RecoilScale 站/走/跳 0x48-0x50, 准星 Size/Length 普通 0x5c-0x68 / 走 0x6c-0x78 / 跳 0x7c-0x88, InaccuracyIncrement 0x8c）
     * 游戏把这块从表 WeapParamsTable_AllWeapons 复制到动画/武器管理对象（例如 +0x18c4），复制件不止一份 →
     * 在人物、武器以及它们指向的对象里按特征找出所有副本，全部改掉。 */
    static final java.util.Map<Long, long[]> tables = new java.util.HashMap<>();
    static long tableScanAt;
    static float f(RandomAccessFile m, long a) throws java.io.IOException { return Float.intBitsToFloat(rd(m, a)); }
    static boolean looksTable(byte[] buf, int t) {
        if (t + 0x254 > buf.length) return false;
        java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(buf).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        int r = t + 0x118;
        float rt = bb.getFloat(r + 0x3c), upMin = bb.getFloat(r + 0x44), upMax = bb.getFloat(r + 0x4c), sMin = bb.getFloat(r + 0x54), sMax = bb.getFloat(r + 0x5c);
        if (!(rt >= 0 && rt < 5 && upMin >= 0 && upMin <= upMax && upMax < 60 && sMin <= sMax && Math.abs(sMax) < 60 && Math.abs(sMin) < 60)) return false;
        int c = t + 0x17c; float cb = bb.getFloat(c + 8), cmx = bb.getFloat(c + 0x10);
        if (!(cb >= 0 && cb < 60 && cmx >= 0 && cmx < 200)) return false;
        int s = t + 0xb4; float spMin = bb.getFloat(s + 0x3c), spMax = bb.getFloat(s + 0x40);
        if (!(spMin >= 0 && spMin <= spMax && spMax < 100)) return false;
        int u = buf[t + 0x1ac] & 0xff; if (u > 1) return false;
        int a = t + 0x4 + 0x48; float st = bb.getFloat(a);
        if (!(st >= 0 && st < 20)) return false;
        // 至少要有一个非零的后座值，排除全 0 的空内存
        return upMax > 0 || sMax > 0 || cb > 0 || bb.getFloat(t + 0x1b0 + 0x28) > 0;
    }
    static long[] findTables(RandomAccessFile m, long b, long pawn, long w) throws java.io.IOException {
        java.util.LinkedHashSet<Long> objs = new java.util.LinkedHashSet<>();
        objs.add(w); objs.add(pawn);
        for (long base : new long[]{w, pawn}) {
            byte[] bb = new byte[0x1000];
            try { m.seek(base); m.readFully(bb); } catch (Throwable e) { continue; }
            for (int o = 0; o + 4 <= bb.length; o += 4) {
                long p = (bb[o] & 255) | (bb[o + 1] & 255) << 8 | (bb[o + 2] & 255) << 16 | (long) (bb[o + 3] & 255) << 24;
                if (p < 0x10000 || (p & 3) != 0) continue;
                try { long vt = rd(m, p) & 0xffffffffL; if (vt >= b && vt <= b + 0x5500000L) objs.add(p); } catch (Throwable ignored) {}
            }
        }
        java.util.List<Long> hits = new java.util.ArrayList<>();
        byte[] buf = new byte[0x2400];
        for (long o : objs) {
            int n = 0;
            for (int len : new int[]{0x2400, 0x1000, 0x600}) {
                try { m.seek(o); m.readFully(buf, 0, len); n = len; break; } catch (Throwable e) { n = 0; }
            }
            if (n < 0x300) continue;
            for (int t = 0; t + 0x254 <= n; t += 4) if (looksTable(buf, t)) { hits.add(o + t); t += 0x250; }
        }
        long[] r = new long[hits.size()]; for (int i = 0; i < r.length; i++) r[i] = hits.get(i);
        StringBuilder sb = new StringBuilder();
        for (long h : r) sb.append(Long.toHexString(h)).append(m == null ? "" : String.format("(CFM=%d) ", readByte(m, h + 0x1ac)));
        LogCap.note(String.format("后座参数表 武器%x 查了%d个对象 → %d份 %s", w, objs.size(), r.length, sb));
        return r;
    }
    static int readByte(RandomAccessFile m, long a) { try { m.seek(a); return m.read(); } catch (Throwable e) { return -1; } }
    static void paramTables(RandomAccessFile m, long b, long pawn, long w) throws java.io.IOException {
        long[] ts = tables.get(w);
        long now = System.currentTimeMillis();
        // 换枪后动画对象里的副本会被重新复制，所以每 3 秒重新找一次
        if (ts == null || now - tableScanAt > 5000) { tableScanAt = now; ts = findTables(m, b, pawn, w); tables.put(w, ts); }
        for (long t : ts) {
            if (noRecoil) {
                long r = t + 0x118;
                for (int k = 0x44; k <= 0x60; k += 4) wf(m, r + k, 0f);          // 后座 上/左右
                long c = t + 0x17c; wf(m, c + 8, 0f); wf(m, c + 0xc, 0f); wf(m, c + 0x10, 0f); // 镜头后座
                long cf = t + 0x1b0;
                for (int k = 0x28; k <= 0x3c; k += 4) wf(m, cf + k, 0f);         // CFM 后座 上/横向
                wf(m, cf + 0x48, 0f); wf(m, cf + 0x4c, 0f); wf(m, cf + 0x50, 0f); // CFM 站/走/跳 后座倍数
            }
            if (noShake) {
                // 聚点：准星缩到最小、不随跳跃/走动/开枪变大
                long s = t + 0xb4; for (int k = 0x3c; k <= 0x54; k += 4) wf(m, s + k, 0f);
                for (long a : new long[]{t + 0x4, t + 0x5c}) {
                    float st = f(m, a + 0x48);
                    for (int k = 0x3c; k <= 0x54; k += 4) wf(m, a + k, Math.min(st, f(m, a + 0x50)) > 0 ? Math.min(st, f(m, a + 0x50)) : st);
                }
                long cf = t + 0x1b0;
                wf(m, cf + 0x8, 0f); wf(m, cf + 0xc, 0f);                      // 最大/最小不准度
                for (int k = 0x10; k <= 0x1c; k += 4) wf(m, cf + k, 0f);       // 散布（站/跳/走）
                float sz = f(m, cf + 0x5c), ln = f(m, cf + 0x64);
                wf(m, cf + 0x60, 0f); wf(m, cf + 0x68, 0f);
                wf(m, cf + 0x6c, sz); wf(m, cf + 0x70, 0f); wf(m, cf + 0x74, ln); wf(m, cf + 0x78, 0f); // 走路准星 = 站立
                wf(m, cf + 0x7c, sz); wf(m, cf + 0x80, 0f); wf(m, cf + 0x84, ln); wf(m, cf + 0x88, 0f); // 跳跃准星 = 站立
                wf(m, cf + 0x8c, 0f);                                          // 开枪准星不再变大
            }
        }
    }
    static void wi(RandomAccessFile m, long a, int v) throws java.io.IOException {
        m.seek(a); m.write(new byte[]{(byte) v, (byte) (v >> 8), (byte) (v >> 16), (byte) (v >> 24)});
    }
}
