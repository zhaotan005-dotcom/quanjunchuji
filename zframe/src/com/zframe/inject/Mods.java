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
    public static final String COAT = "30318000000";
    static long base;

    /* 初始衣服 = 绝地风衣（30318000000）。
     * InitAvatarProxy(0x10d7380) 离线时人物列表只有角色编号，最后在 0x10d882c 调 0xc62d48(表, &列表, 性别)：
     * 按 DefaultRoleClotheDataTable 把默认衣服补进列表，这个列表就是出生时发的衣服（OnAvatarAssembler In Items）。
     * 把这一处 bl 改成跳到 GM 面板里没用的函数空位(0x171e4d0)：先照常补默认衣服，再把列表里的
     * 上衣 30301000001 换成风衣 30318000000、去掉裤子 30302000000（风衣是套装）。
     * （v1.51 改的是 0x10d7960 那份拷贝，那份只用来比对，不发衣服，所以没效果。） */
    static final long COAT_HOOK = 0x10d882cL, COAT_CAVE = 0x171e4d0L;
    static final String COAT_HOOK_O = "4529eeeb", COAT_HOOK_N = "271719eb";
    static final String COAT_CAVE_O = "00009fe7000090e520000be5040095e5007095e5000050e302008fe001708f00046080e25310a0e3040097e4010050e10500000a3f3ac6eb0040a0e1040016e53c3ac6eb000054e10b00001a040097e4041096e4002091e1f3ffff1aec029fe520101be500009fe7000090e5010050e018d04b02f08bbd08d439c6ebc40198e5000050e3f4ffff0a700090e5000050e31840901500005413";
    static final String COAT_CAVE_N = "f0412de90140a0e11a12d5eb005094e5046094e50070a0e3060057e11c0000aa878185e0000098e5041098e5412109e3142e40e3020050e1070051030600001a80270fe3172f40e3002088e50720a0e3042088e5017087e2eeffffea80230de3232e40e3020050e10700510301708712e8ffff1a016046e2863185e0000093e5041093e5000088e5041088e5046084e5e0ffffeaf081bde8";
    public static volatile boolean coatOn;
    /** v1.65 启动早装：v1.64 自检证明运行中改已经跑过的代码不生效（钩子字节在，但从没跑到；假仓库取角色 0 次也是这个原因）。
     *  所以在 libUE4 刚加载、大厅代码还没跑之前，把风衣、大厅人物、假仓库、服务端钩子一次装好，之后不再重进大厅。 */
    public static volatile boolean early = true, earlyDone;
    public static volatile String earlyState = "启动早装：没做";
    static void preinstall() {
        long t0 = System.currentTimeMillis();
        String c = "不装（v1.73 改成邮件领取）";   // 用户要求：去掉初始风衣
        coatOn = false;
        String r = setHall(male);
        if ("ok".equals(r)) { String st = setStore(male); if (!"ok".equals(st)) r = "仓库数据 " + st; }
        hallState = "ok".equals(r) ? "大厅人物：已改好（" + (male ? "男" : "女") + "，启动时装）" : "大厅人物：没改成，" + r;
        Server.preinstall();
        earlyDone = "ok".equals(r);
        earlyState = "启动早装（等库 " + Patches.foundMs + " ms）：风衣 " + c + "，人物 " + r + "，服务端 " + Server.hookWhen + "，" + (System.currentTimeMillis() - t0) + " ms";
        LogCap.note(earlyState);
    }
    static void builtInCoat() {
        if (earlyDone || !early) { GameOverlay.refreshAny(); return; }   // v1.73：风衣改邮件领取，这里不再装
        String r = write(COAT_CAVE, COAT_CAVE_O, COAT_CAVE_N, true);
        if ("ok".equals(r)) r = write(COAT_HOOK, COAT_HOOK_O, COAT_HOOK_N, true);
        coatOn = "ok".equals(r);
        LogCap.note("初始衣服 绝地风衣 → " + r);
        hallAndReload();
        GameOverlay.refreshAny();
    }

    /* v1.55 灰色人物（大厅 / 仓库 / 试衣间都算）：单机没有服务器，角色编号是 0。
     * FNZStoreAvatarHelper::InitAvatarClotheTry(0x1503208) 查不到角色，衣服列表只有 [0]，人物就是灰模。
     * 这个函数一共有 8 个地方调用（大厅、仓库、商店预览等），v1.54 只改了大厅那一处。
     * v1.55 改函数入口：0x1503230 跳到 0x171e610 小 cave。角色编号是 0 时换成 女 10200000000 / 男 10100000000，
     * 然后走「默认衣服」分支，和局内一样。默认分支 0x1503680 再接风衣 cave(0x171e600 → 0x171e4dc)。 */
    static final long HALL_HOOK = 0x1503230L, HALL_RCAVE = 0x171e610L, HALL_GEN = 0x171e620L, HALL_COAT = 0x1503680L, HALL_CAVE = 0x171e600L;
    static final String HALL_HOOK_O = "0c909be5", HALL_HOOK_N = "f66c08ea";
    static final String HALL_RCAVE_O = "000050e30000000af0ad09eb040095e5000050e37400000a005095e50011a0e1";
    static final String HALL_RCAVE_N = "0c909be508a09be509309ae10593f71a00a60ae3f7af45e30290a0e30193f7ea"; // 女
    static final String HALL_RCAVE_M = "0c909be508a09be509309ae10593f71a00a50ce301aa45e30290a0e30193f7ea";
    static final String HALL_GEN_F = "00a60ae3f7af45e3", HALL_GEN_M = "00a50ce301aa45e3";
    static final String HALL_COAT_O = "737dddeb", HALL_COAT_N = "de6b08eb";
    static final String HALL_CAVE_O = "0700a0e10810a0e1391d37eb2c001be5", HALL_CAVE_N = "f0412de90040a0e19111d5ebb2ffffea";
    public static volatile boolean hallOn;
    public static volatile String hallState = "大厅人物：等大厅加载好";

    static String setHall(boolean m) {
        String r = write(HALL_RCAVE, HALL_RCAVE_O, HALL_RCAVE_N, true);
        if (r.startsWith("原字节不符") && "ok".equals(write(HALL_RCAVE, HALL_RCAVE_O, HALL_RCAVE_M, true))) r = "ok"; // 之前写成了男
        if (!"ok".equals(r)) return r;
        r = write(HALL_GEN, m ? HALL_GEN_F : HALL_GEN_M, m ? HALL_GEN_M : HALL_GEN_F, true);
        if (!"ok".equals(r)) return r;
        if (coatOn) {
            String c = write(HALL_CAVE, HALL_CAVE_O, HALL_CAVE_N, true);
            if ("ok".equals(c)) c = write(HALL_COAT, HALL_COAT_O, HALL_COAT_N, true);
            LogCap.note("大厅风衣 → " + c);
        }
        r = write(HALL_HOOK, HALL_HOOK_O, HALL_HOOK_N, true);
        if (!"ok".equals(r)) return r;
        hallOn = true;
        return "ok";
    }
    /* v1.58 假仓库数据（v1.57 挂在 InitAvatar 上，手机上证明那里根本没跑，仓库仍空）。
     * 大厅/仓库的人物都从 UNZStoreDataServer 的物品表(+0x5c TArray，每条 0x38)里找「当前角色」(类型 1 且 +0x10 非 0)
     * 和这个角色身上的衣服(类型 3，+0x10 == 角色 +0x08)。单机没有服务器，这张表是空的。
     * 改挂在「取当前角色」0x1877bfc(out, ds) 入口：所有大厅/仓库/角色页都会调它。cave 0x171e630：
     *   计数+1，记下 ds；若开关开且 ds 物品表为空 → 调游戏自己的 CacheStoreProp(0x1872b04, ds, 我们的物品)，记下入库后条数；
     *   再执行原指令 push {r4,r5,fp,lr}，回到 0x1877c00。
     * 缓冲区：+0 TArray{data,num,max}  +0xc 调用次数  +0x10 ds  +0x14 入库后条数  +0x40 起物品，每条 0x68：
     *   +0x10 物品编号  +0x18 gid  +0x20 归属(穿在哪个角色上 = 角色 gid；角色自己填 1)  +0x34 状态 1(不是限时)
     * 服务器分支补默认衣服 0x1503494 的 bl c62d48 也接风衣 cave，去掉裤子。 */
    static final long STORE_HOOK = 0x1877bfcL, STORE_CAVE = 0x171e630L, HALL_FILL = 0x1503494L;
    static final String STORE_HOOK_O = "30482de9", STORE_HOOK_N = "8b9afaea";
    static final String STORE_CAVE_O = "0020a0e30060a0e30530a0e10270b3e75f0057e30300000a042082e2020051e1f8ffff1a680000ea051043e00060a0e30020e0e3410152e16300000a016040e24121a0e1020056e10030a0e30260a0c1000051e3006000b3018086e2000056e3";
    static final String STORE_CAVE_T = "0f502de9%s%s0c0092e5010080e20c0082e5101082e5040092e5000050e30a00000a600091e5000050e30700001a0100a0e10210a0e1245105eb%s%s04009de5600090e5140082e50f50bde830482de95b6505ea";
    static final String HALL_FILL_O = "2b7eddeb", HALL_FILL_N = "0d6c08eb";
    static final long[] STORE_ITEMS = { 30301000001L, 30302000000L, 30303000006L, 30306000000L,
            30213000003L, 30307000006L, 30304000003L, 30305000002L };
    static final int STORE_HDR = 0x40, STORE_REC = 0x68;
    static java.nio.ByteBuffer storeBuf; static long storeAddr; static boolean storeHooked;

    static String armMov(boolean top, int rd, int imm) {
        int w = (top ? 0xe3400000 : 0xe3000000) | ((imm >> 12) & 0xf) << 16 | rd << 12 | (imm & 0xfff);
        return String.format("%02x%02x%02x%02x", w & 0xff, (w >> 8) & 0xff, (w >> 16) & 0xff, (w >>> 24) & 0xff);
    }

    /** 填「服务器物品」：当前性别的角色排第一，风衣穿在角色身上，其余放仓库。计数和 ds 不动 */
    static void fillStore(boolean m) {
        int n = 2 + STORE_ITEMS.length;
        if (storeBuf == null) {
            storeBuf = java.nio.ByteBuffer.allocateDirect(STORE_HDR + STORE_REC * n + 64).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            storeAddr = GameOverlay.bufAddr(storeBuf);
        }
        java.nio.ByteBuffer b = storeBuf;
        b.putInt(4, 0);                                   // 先关，填好再开
        for (int i = STORE_HDR; i < b.capacity(); i++) b.put(i, (byte) 0);
        long role = m ? 10100000000L : 10200000000L, other = m ? 10200000000L : 10100000000L;
        long roleGid = 0x5a460001L;
        int i = 0;
        i = putItem(b, i, role, roleGid, 1);
        i = putItem(b, i, other, roleGid + 1, 0);
        for (int k = 0; k < STORE_ITEMS.length; k++)
            i = putItem(b, i, STORE_ITEMS[k], roleGid + 0x100 + k, 0);   // v1.73：不再把风衣穿在角色身上
        b.putInt(0, (int) (storeAddr + STORE_HDR)); b.putInt(8, i); b.putInt(4, i);
    }
    static int putItem(java.nio.ByteBuffer b, int i, long id, long gid, long owner) {
        int o = STORE_HDR + i * STORE_REC;
        b.putLong(o + 0x10, id); b.putLong(o + 0x18, gid); b.putLong(o + 0x20, owner); b.putInt(o + 0x34, 1);
        return i + 1;
    }

    static String setStore(boolean m) {
        fillStore(m);
        if (storeAddr == 0) return "拿不到内存地址";
        long b = base(); if (b == 0) return "找不到游戏库";
        // 换性别：游戏表里已经有我们的数据 → 把条数清 0，下次取角色时按新性别重新入库
        int ds = storeBuf.getInt(0x10);
        if (storeHooked && ds != 0) LogCap.note("清空旧物品 → " + pokeInt((ds & 0xffffffffL) + 0x60, 0));
        if (!storeHooked) {
            int a = (int) storeAddr;
            String mw = armMov(false, 2, a & 0xffff), mt = armMov(true, 2, (a >>> 16) & 0xffff);
            String r = write(STORE_CAVE, STORE_CAVE_O, String.format(STORE_CAVE_T, mw, mt, mw, mt), true);
            if (!"ok".equals(r)) return "cave " + r;
            if (coatOn) LogCap.note("服务端分支风衣 → " + write(HALL_FILL, HALL_FILL_O, HALL_FILL_N, true));
            r = write(STORE_HOOK, STORE_HOOK_O, STORE_HOOK_N, true);
            if (!"ok".equals(r)) return "入口 " + r;
            storeHooked = true;
        }
        return "ok";
    }
    static String pokeInt(long addr, int v) {
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "rw")) {
            byte[] w = { (byte) v, (byte) (v >> 8), (byte) (v >> 16), (byte) (v >> 24) };
            m.seek(addr); m.write(w); return "ok";
        } catch (Throwable t) { return "出错 " + t.getMessage(); }
    }
    /** 诊断用：取角色被调用几次、ds、入库后条数 */
    public static String storeInfo() {
        if (storeBuf == null) return "假仓库：没启用";
        java.nio.ByteBuffer b = storeBuf;
        return "假仓库：取角色 " + b.getInt(0xc) + " 次，ds=" + Integer.toHexString(b.getInt(0x10))
                + "，入库后 " + b.getInt(0x14) + " 条（准备了 " + b.getInt(4) + " 条），缓冲 " + Long.toHexString(storeAddr);
    }

    /** 改好后重开大厅，让人物重新生成 */
    static void hallAndReload() {
        if (Con.inMatch) return;
        String r = setHall(male);
        if ("ok".equals(r)) { String st = setStore(male); if (!"ok".equals(st)) r = "仓库数据 " + st; }
        hallState = "ok".equals(r) ? "大厅人物：已改好（" + (male ? "男" : "女") + "）" : "大厅人物：没改成，" + r;
        GameOverlay.refreshAny();
        if ("ok".equals(r)) Con.exec("open " + Con.LOBBY);
    }

    static void resetMatch() {
        speed = 1f; jump = 1f; noRecoil = false; noShake = false; infAmmo = false;
        cmc = 0; lastJump = Float.NaN; tableAi = 0; tableAt = 0;
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
        if ("ok".equals(r)) { noPoison = on; }
        return r;
    }

    public static String setPlane(boolean on) {
        String r = "ok";
        if (on) r = write(CAVE, CAVE_O, CAVE_N, true);
        if ("ok".equals(r)) r = write(HOOK, HOOK_O, HOOK_N, on);
        LogCap.note("不上飞机 " + on + " → " + r);
        if ("ok".equals(r)) { noPlane = on; }
        return r;
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
        if ("ok".equals(r)) { male = on; }
        return r;
    }

    // ---------- 加速 / 高跳 ----------
    // 偏移（UE4 反射表）：ACharacter Mesh +0x2f4 / CharacterMovement +0x2f8；AActor CustomTimeDilation +0x58；
    // CMC GravityScale +0x120、JumpZVelocity +0x128、MovementMode +0x138（3 = 空中）；
    // USkeletalMeshComponent AnimScriptInstance +0x750、GlobalAnimRateScale +0x7c4
    static final int P_DIL = 0x58, P_MESH = 0x2f4, P_CMC = 0x2f8, C_JUMP = 0x128, C_MODE = 0x138, C_VEL = 0xb4, M_ANIM = 0x750, M_RATE = 0x7c4;
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
        noRecoil = r; noShake = s; infAmmo = ia;
        LogCap.note("无后座=" + r + " 聚点=" + s + " 无限子弹=" + ia);
        startMover();
    }
    /** 滑条 0~10：小于 1 就是关（×1） */
    public static void setMove(float s, float j) {
        speed = Math.max(1f, s); jump = Math.max(1f, j);
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

    /** 加速：人物自己的时间倍率 = 倍数（只快在位移上），动画按 slowMo 放慢；人在空中时恢复 1，跳跃才正常。
     * v1.52：在空中时间恢复正常，起跳带出去的水平速度也就只有正常跑步那么快，看着像原地跳。
     * 所以起跳那一刻把水平速度 Velocity(+0xb4 X,+0xb8 Y) 也乘上倍数，落地后游戏自己会重算速度。 */
    static boolean wasAir;
    static void dilation(RandomAccessFile m, long b, long pawn, long c) throws java.io.IOException {
        int mode = rd(m, c + C_MODE) & 0xff;
        boolean air = mode == MOVE_FALLING;
        if (air && !wasAir && speed > 1f) {
            float vx = f(m, c + C_VEL), vy = f(m, c + C_VEL + 4);
            if (Math.abs(vx) < 3000f && Math.abs(vy) < 3000f && vx * vx + vy * vy > 100f) {
                wf(m, c + C_VEL, vx * speed); wf(m, c + C_VEL + 4, vy * speed);
            }
        }
        wasAir = air;
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
