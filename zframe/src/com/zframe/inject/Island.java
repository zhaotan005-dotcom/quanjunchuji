package com.zframe.inject;

import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;

/** 训练岛（训练场 = BP_NZBRPracticeGameMode_C，游戏自带「训练」入口）。
 *
 *  v3.42 根因（RE + v3.41 block-1119-195412 日志）：
 *  - 训练场是「一个 DS 开 N 份岛」：GameMode BeginPlay 0x1081d70 建 N 个位置（GM+0xa40 数组，每个 0x34 字节，
 *    [0]=块号，+4 本位置的 PlayerStart 数组 (data,num,max)，+0x10 玩家数组；个数 +0xa44，块数 +0xa4c，BP 默认 SlotCount=9）。
 *  - 单机里玩家 Login 比 GameMode BeginPlay 早 → 位置数组空 → 不进位置（v3.37 空队伍补丁让它跳过）。
 *
 *  v3.43 根因补全（RE + train-1119-204331 日志）：
 *  - SetupPlayerStartActor 0x1082868（Tick 里第一次拿到地块管理器 GM+0xa3c 时跑一次）先调 0x1164d90(管理器, GM+0xa4c)：
 *    专用服（NetMode==1）在这里写 管理器+0x43c = 块数 9；单机/listen 直接退出 → 管理器块数还是默认 16。
 *    出生点 = 块偏移 0x1163e2c(管理器+0x43c 算边长) + BP 模板 SpawnPointArray[0]=(53920,-48810,-15900)：
 *    用 16（4×4）算 → 位置 0 出生点 (-156080,-258810)（日志 20:42:29 正是 4×4 排法：0..3 同 X）。
 *    客户端 LoadBlock(块号, 9) 又把 +0x43c 改成 9（3×3）→ 岛副本放在 (-86080,-188810) 一带 → 出生点在岛西南约 1 公里海上。
 *    这就是「人在岛西南很远的海上」。修：0x10828b0 的 bl 0x1164d90 换成 str r1,[r0,#0x43c]（只做专用服那一步写块数；
 *    那个函数其余部分单机本来就不跑）。补丁没赶上时：写 +0x43c、清各位置 PlayerStart 数、GM+0xa3c 置 0 → Tick 按 9 重建。
 *  - 出生点不是位置里的 3 个 float（v3.42 读成 0,0,0）：+4 是 PlayerStart 数组。
 *  - 原生摆人：AddPlayerToSlot 0x1083a98 只要 PC+0x558 有 PlayerState、PC+0xbdc<0、PC 在某位置玩家数组里，就自己
 *    写 PC+0xbdc 块号/+0xbe0 块数 → FindPlayerStart 0x2eca4ac（→ ChoosePlayerStart_Implementation 0x10847xx 从位置 PlayerStart 里挑）
 *    → PC SetInitialLocation(vt+0x30c) → StartSpot → 人物 SetActorLocation 0x274ca58 / SetActorRotation 0x274cd14。
 *    它不依赖 +a70（Login 在 +a70 已置时也是直接调它）。v3.42 排进 +a24 等 OnAllDuplicateLevelLoaded，但那要 Landscape 层先加载完，
 *    人不在岛上就等不到（本次日志没有 OnLevelStreamingLayerLoadCompleted）→ 死等。v3.43 改为位置建好就直接调 AddPlayerToSlot。
 *  - 射击训练区：LogicLevel（game/pak，只读解析）shootreturn=(47835,-48630,-15850)、BP_ShootPracticeBase 盒子 (47800,-48610)，
 *    出生模板离它约 61 米 → 原生出生点就在射击区边上，不用再传。只做诊断。全部未实测。
 *
 *  v3.44（RE + 21:05 用户 v3.43 诊断 t3）：v3.43 LoadBlock(块 0, 块数 9) 之后整整 50 秒地形块一直 1。
 *  - 三版对比：v3.41 只调 LoadBlock(0,1) → 块 0 正常流式加载（地形块 1→17、OnLevelStreamingLayerLoadCompleted）；
 *    v3.42、v3.43 都在 LoadBlock 后多调了一次 FillLayersWithCacheData(管理器, +0x37c 服务器层, 块号) → 两版都一点都没加载。
 *    冻结（时间缩放）只有 v3.43 有，v3.42 没冻也没加载 → 冻结不是主因。
 *  - 服务器层不是客户端该填的：客户端原生只有 Client Load Block → LoadBlock → Fill(+0x370)。pak 只读解析 Main.umap：
 *    ServerLayersSettings = Landscape, AlwaysLoad, Ocean, ServerOnly, ServerOnlyMajority, ServerOnlyGroundObjects ——
 *    跟客户端层同名的 Landscape / AlwaysLoad 也在里面。FillLayers 0x11649bc 按「管理器当前层 +0x2f4 的名字」去缓存里找，
 *    找到就 TileLayer 0x116543c → 同一个块号第二次给 Landscape/AlwaysLoad 层生成「%s_LevelInstance_%d」：实例名和第一次完全一样，
 *    0x1165bf4（非 world+0x6d8==3 时不查旧的）再 NewObject 一个同名关卡流对象（UE 同名同 Outer 会在原地重建那个对象）、
 *    层 vt+0x14 再 AddLevel 一次、+0x358 再追加一次（推断，静态读码）。→ 去掉服务器层填充，完全按客户端原生步骤。
 *  - 冻结：管理器 Tick 0x115e7dc 用自己的 DeltaSeconds，Update 0x115eb7c 取 PC+0x2c0 人物根组件坐标 → 只改人物
 *    CustomTimeDilation 本来不该卡流式，但用户要求不影响流式的冻结 → 改成只停重力：移动组件 GravityScale(+0x120)=0、速度清零，
 *    地形到了再还原（不改时间缩放）。
 *  - 诊断 stream：管理器 +0x400 首次加载标志 / +0x43c / +0x440 / +0x2c8 / 两份缓存层名、每层关卡数、包住人物的关卡数、
 *    层更新计数（+0x10，看 Default 策略 Update 有没有在跑）、+0x358 关卡流对象数（重复指针数）、加载/可见数、world 流关卡数。 */
final class Island {
    static final long LOAD_BLOCK = 0x1163f3cL, FIND_SLOT = 0x10845b0L, ADD_TO_SLOT = 0x1083a98L, REALLOC = 0x1989d38L;
    /** 训练场（Practice 模式）登录时 0x10845b0 找队伍返回空，0x1084f6c ldr [r8,#0x14] 崩（fault 0x14，19:07 实测诊断）。
     *  改成 movs r8,r0 / ldrne / cmpne / beq 0x10850a8（没队伍就走原来的「跳过」分支）。空位置由 Island.join 事后补上。 */
    static final long PR = 0x1084f2cL;
    static final String PR_O = "0080a0e1580596e5000050e35a00000a", PR_N = "0080b0e158059615000050135a00000a";
    /** v3.43：SetupPlayerStartActor 里 0x10828b0 bl 0x1164d90 → str r1,[r0,#0x43c]（r0=管理器 GM+0xa3c，r1=GM+0xa4c 块数）。 */
    static final long PB = 0x10828b0L;
    static final String PB_O = "368903eb", PB_N = "3c1480e5";
    /** BP_NZBRPracticeGameMode 默认 SpawnPointArray[0]；LogicLevel shootreturn（pak 解析，原始坐标，块偏移前） */
    static final double TPL_X = 53920, TPL_Y = -48810, TPL_Z = -15900, SHOOT_X = 47835, SHOOT_Y = -48630, SHOOT_Z = -15850;
    static volatile String fix = "训练场空队伍补丁：没装";
    static void install() {
        String r = Mods.write(PR, PR_O, PR_N, true), r2 = Mods.write(PB, PB_O, PB_N, true);
        fix = "训练场空队伍补丁：" + r + "；块数补丁 0x10828b0：" + r2; LogCap.note(fix);
    }
    static volatile String block = "分配位置：没试";
    static volatile String state = "训练岛：没进";
    static volatile String range = "射击训练区：没查";
    static volatile String spawn = "原生刷车/刷物资：没查";
    static volatile String setup = "出生点建立：没查";
    static volatile String track = "坐标/地形块随时间：-";
    static volatile String stream = "流式状态：没查";
    static volatile String seenLog = "";
    static volatile boolean running;
    static final StringBuilder seen = new StringBuilder();

    /** Con 每行游戏日志都给这里（只在训练岛）：记关键行 */
    static void seen(String l) {
        String[] keys = { "OnAllDuplicateLevelLoaded", "OnLevelStreamingLayerLoadCompleted", "AddPlayerToSlot", "Spawn Vehicle", "SpawnVehicle", "Load Block", "Client Load Block", "SetupPlayerStartActor Add Player Start 0 ", "LevelMgr is null", "level not prepared",
            "FillLayersWithCacheData", "TileLayer", "Already Load Block", "Out Of Index", "Streaming in level", "DirectSubmit", "NZStreamingRequestsManager", "Not Need", "FirstLoad", "StreamingLevelsManagerActor" };
        for (String k : keys) if (l.contains(k)) {
            synchronized (seen) { if (seen.length() < 6000) seen.append(l.trim().length() > 200 ? l.trim().substring(0, 200) : l.trim()).append("\n  "); seenLog = seen.toString(); }
            return;
        }
    }

    static void clearSeen() { synchronized (seen) { seen.setLength(0); seenLog = ""; } }

    static void matchLoaded() {
        if (running) return; running = true;
        block = "分配位置：没试"; range = "射击训练区：没查"; spawn = "原生刷车/刷物资：没查"; setup = "出生点建立：没查"; track = "坐标/地形块随时间：-"; stream = "流式状态：没查"; state = "训练岛：进了，开始等 GameMode";
        { android.app.Activity x = Con.act; if (x != null) AvatarLog.autoTrain(x); }
        new Thread(() -> {
            try { run(); } catch (Throwable t) { state = "训练岛：出错 " + t; LogCap.note(state); GameOverlay.toastAny(state); }
            finally { running = false; }
        }, "zf-island").start();
    }

    static String p3(double[] p) { return p == null ? "?" : (int) p[0] + "," + (int) p[1] + "," + (int) p[2]; }
    static double[] me() throws InterruptedException { double[] q = Veh.info(); return q == null ? null : new double[] { q[0], q[1], q[2] }; }

    static void run() throws Exception {
        long t0 = System.currentTimeMillis();
        long b = 0, gm = 0, pc = 0; String gmName = "?";
        Veh.Names nm = null;
        // 1) 等 GameMode BeginPlay 建好位置
        while (true) {
            if (System.currentTimeMillis() - t0 > 90000) { state = "训练岛：90 秒没等到 GameMode 建位置（GM " + Long.toHexString(gm) + " " + gmName + "）"; note(state); outfit(); return; }
            Thread.sleep(500);
            if (!Con.training || !Con.inMatch) return;
            b = Mods.base(); if (b == 0) continue;
            double[] q = Veh.info(); if (q == null) { state = "训练岛：等人物"; continue; }
            gm = (long) q[4]; pc = Veh.lastPc(); nm = Veh.Names.get();
            if (gm < 0x10000 || pc < 0x10000) { state = "训练岛：等 GameMode/控制器"; continue; }
            try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
                gmName = Alive.clsName(m, nm, gm);
                if (!gmName.contains("PracticeGameMode")) { state = "训练岛：模式是 " + gmName + "，不是训练场，不分配位置"; note(state); outfit(); return; }
                int n = (int) Server.rd(m, gm + 0xa44);
                if (n <= 0) { state = "训练岛：等 BeginPlay 建位置（现在 0 个）"; continue; }
            }
            break;
        }
        state = "训练岛：GameMode 位置已建好，找地块管理器";
        // 2) 地块管理器
        long mgr = 0;
        for (int i = 0; i < 20 && mgr == 0; i++) {
            try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) { long a = Server.rd(m, gm + 0xa3c); if (a >= 0x10000 && Mods.isObj(m, b, a)) mgr = a; }
            if (mgr == 0) mgr = scan(b, nm).mgr;
            if (mgr == 0) { state = "训练岛：没找到地块管理器（NZStreamingLevelsManagerActor_TileMode），再找"; Thread.sleep(1000); }
        }
        if (mgr == 0) { state = "训练岛：20 秒没找到地块管理器"; note(state); outfit(); return; }
        // 3) 出生点按正确块数建好（块数补丁没赶上就让 Tick 重建）
        state = "训练岛：管理器 " + Long.toHexString(mgr) + "，等出生点建好";
        if (!ensureSetup(b, gm, mgr)) { outfit(); return; }
        state = "训练岛：出生点好了，原生摆人 + LoadBlock";
        // 4) 补进位置 → 原生 AddPlayerToSlot 摆人 → 按 PC+0xbdc/+0xbe0 LoadBlock
        double[] ps = join(b, gm, pc, mgr);
        state = "训练岛：已摆人和 LoadBlock，跟踪地形加载";
        // 5) 衣服（训练岛不走 Bots.matchLoaded，以前从来没发过）
        outfit();
        // 6) 跟踪：坐标、地形块、+a70/+a21，最多 120 秒；人物等地形到了才放开
        follow(b, nm, gm, pc, ps, mgr);
        // 7) 射击训练区（只诊断）
        rangeDiag(b, nm, mgr, pc);
        // 8) 原生刷车/刷物资情况
        Thread.sleep(20000);
        if (!Con.training || !Con.inMatch) return;
        spawnDiag(b, nm, gm);
        android.app.Activity x = Con.act;
        stream = streamDiag(b, nm, mgr, true); note(stream);
        if (x != null) { AvatarLog.manual = true; try { AvatarLog.export(x, "slot-"); } finally { AvatarLog.manual = false; } }
    }

    static void outfit() { Bots.trainingOutfit(); }

    static void note(String s) { LogCap.note(s); }
    /** 读一个字节（Server.rd 只读 4 字节对齐） */
    static int rb(RandomAccessFile m, long a) { return (int) ((Server.rd(m, a & ~3L) >>> ((a & 3) * 8)) & 0xff); }

    /** 位置 i 的第一个 PlayerStart 的坐标（+4 是 PlayerStart 数组）；没有 → null */
    static double[] slotStart(RandomAccessFile m, long slot) throws java.io.IOException {
        long d = Server.rd(m, slot + 4); int k = (int) Server.rd(m, slot + 8);
        if (d < 0x10000 || k <= 0 || k > 64) return null;
        long a = Server.rd(m, d); if (a < 0x10000) return null;
        float[] p = pos(m, a); if (p == null) return null;
        return new double[] { p[0], p[1], p[2], a };
    }
    /** 块偏移，和 0x1163e2c / LoadBlock 一样：边长 = floor(sqrt(N)+0.01)，行=块/边长，列=块%边长，
     *  X = (行数-1)*-0.5*W + 行*W（W=管理器+0x41c），Y = (边长-1)*-0.5*H + 列*H（H=+0x420） */
    static double[] offset(int n, int blk, int w, int h) {
        if (n <= 0) return new double[] { 0, 0 };
        int side = (int) Math.floor(Math.sqrt(n) + 0.01); if (side < 1) side = 1;
        int rows = n / side, row = blk / side, col = blk % side;
        return new double[] { (rows - 1) * -0.5 * w + (double) row * w, (side - 1) * -0.5 * h + (double) col * h };
    }

    /** 等 SetupPlayerStartActor 按 GM 块数建好每个位置的 PlayerStart。管理器块数不对（补丁没赶上）→ 改块数、清 PlayerStart 数、GM+0xa3c=0 让 Tick 重建。 */
    static boolean ensureSetup(long b, long gm, long mgr) throws Exception {
        StringBuilder sb = new StringBuilder("出生点建立：");
        boolean redone = false; long w0 = System.currentTimeMillis();
        while (System.currentTimeMillis() - w0 < 30000) {
            if (!Con.training || !Con.inMatch) return false;
            int n, cnt, mcnt, num0, w, h; long slots, a3c; double[] p0;
            try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
                slots = Server.rd(m, gm + 0xa40); n = (int) Server.rd(m, gm + 0xa44); cnt = (int) Server.rd(m, gm + 0xa4c);
                mcnt = (int) Server.rd(m, mgr + 0x43c); a3c = Server.rd(m, gm + 0xa3c); w = (int) Server.rd(m, mgr + 0x41c); h = (int) Server.rd(m, mgr + 0x420);
                num0 = (int) Server.rd(m, slots + 8); p0 = slotStart(m, slots);
            }
            if (num0 <= 0 || p0 == null) { setup = sb + "等 Tick 建 PlayerStart（GM+a3c=" + Long.toHexString(a3c) + "，管理器块数 " + mcnt + "，GM 块数 " + cnt + "）"; Thread.sleep(300); continue; }
            double[] off = offset(cnt, 0, w, h);
            double ex = TPL_X + off[0], ey = TPL_Y + off[1];
            boolean good = mcnt == cnt && Math.abs(p0[0] - ex) < 100 && Math.abs(p0[1] - ey) < 100;
            sb.append("位置 ").append(n).append(" 个，GM 块数 ").append(cnt).append("，管理器块数 +43c=").append(mcnt).append("，块宽 ").append(w).append('×').append(h)
              .append("；位置 0 的 PlayerStart ").append(p3(p0)).append("（按块数 ").append(cnt).append(" 应在 ").append((int) ex).append(',').append((int) ey).append("）");
            if (good) { setup = sb.append(redone ? " → 重建后对了" : " → 对（块数补丁生效）").toString(); note(setup); return true; }
            if (redone) { setup = sb.append(" → 重建后还不对，照原生走").toString(); note(setup); GameOverlay.toastAny("训练岛：出生点和地块对不上，看诊断"); return true; }
            // 补丁没赶上：照专用服写块数，清 PlayerStart 数，GM+0xa3c=0 → 下一帧 Tick 重新拿管理器、重跑 SetupPlayerStartActor
            sb.append(" → 不对，重建：");
            sb.append("写块数 ").append(Mods.pokeInt(mgr + 0x43c, cnt));
            try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) { slots = Server.rd(m, gm + 0xa40); n = (int) Server.rd(m, gm + 0xa44); }
            for (int i = 0; i < n && i < 64; i++) Mods.pokeInt(slots + 0x34L * i + 8, 0);
            sb.append("，清 ").append(n).append(" 个位置的 PlayerStart 数，GM+a3c=0 ").append(Mods.pokeInt(gm + 0xa3c, 0)).append("；");
            redone = true; Thread.sleep(500);
        }
        setup = sb.append("30 秒没等到 PlayerStart 建好").toString(); note(setup); GameOverlay.toastAny("训练岛：出生点没建好，看诊断");
        return false;
    }

    /** 按 Login 0x1084f20.. 把自己补进位置，然后原生 AddPlayerToSlot 摆人，再按 Client Load Block 0x1086960 的做法
     *  LoadBlock(管理器, PC+0xbdc, PC+0xbe0)。返回位置 PlayerStart 坐标（x,y,z）。 */
    static double[] join(long b, long gm, long pc, long mgr) throws Exception {
        StringBuilder sb = new StringBuilder("分配位置：");
        long slots, slot = 0; int n, cnt, blk, bdc0;
        boolean in = false; int allLoaded, handled; double[] ps = null;
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
            slots = Server.rd(m, gm + 0xa40); n = (int) Server.rd(m, gm + 0xa44); cnt = (int) Server.rd(m, gm + 0xa4c);
            allLoaded = rb(m, gm + 0xa70); handled = rb(m, gm + 0xa21); bdc0 = (int) Server.rd(m, pc + 0xbdc);
            for (int i = 0; i < n && !in; i++) {
                long s = slots + 0x34L * i, d = Server.rd(m, s + 0x10); int k = (int) Server.rd(m, s + 0x14);
                for (int j = 0; j < k && j < 64; j++) if (Server.rd(m, d + 4L * j) == pc) { in = true; slot = s; }
            }
        }
        sb.append("位置 ").append(n).append(" 个，块数 ").append(cnt).append("，+a70=").append(allLoaded).append(" +a21=").append(handled).append("，PC+bdc=").append(bdc0).append("；");
        if (!in) {
            long[] r = Server.gcall(b + FIND_SLOT, gm, pc, 0, 0, 3000);
            slot = r[1];
            if (r[0] == 0) { block = sb.append("挑位置 0x10845b0 没跑（游戏线程没接）").toString(); note(block); return null; }
            if (slot < slots || slot >= slots + 0x34L * n || (slot - slots) % 0x34 != 0) { block = sb.append("挑位置返回 ").append(Long.toHexString(slot)).append(" 不在位置数组里").toString(); note(block); return null; }
            String a = append(b, slot + 0x10, pc);
            sb.append("塞进位置 ").append((slot - slots) / 0x34).append(" 玩家数组 ").append(a).append("；");
            if (!"ok".equals(a)) { block = sb.toString(); note(block); return null; }
        } else sb.append("已在位置 ").append((slot - slots) / 0x34).append("；");
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
            blk = (int) Server.rd(m, slot);
            ps = slotStart(m, slot);
            sb.append("块号 ").append(blk).append("（位置号 ").append((slot - slots) / 0x34).append("）PlayerStart ").append(ps == null ? "没有" : p3(ps) + "（" + Long.toHexString((long) ps[3]) + "）").append("；");
        }
        double[] before = me();
        sb.append("摆人前人物 ").append(p3(before)).append("；");
        // 原生摆人：AddPlayerToSlot 自己写 PC+bdc/+be0、FindPlayerStart、SetActorLocation/Rotation
        long pawn = Mods.myPawn();
        if (pawn >= 0x10000) hold(pawn, true);   // v3.44：摆到岛上那一刻地形还没流进来 → 只停重力、速度清零（不改时间缩放）
        if (bdc0 >= 0) sb.append("PC 已有块号 ").append(bdc0).append("，AddPlayerToSlot 会直接退（already has a index），不调；");
        else {
            long[] r = Server.gcall(b + ADD_TO_SLOT, gm, pc, 0, 0, 3000);
            sb.append("原生 AddPlayerToSlot ").append(r[0] != 0 ? "已跑" : "没跑（游戏线程没接）").append("；");
        }
        int bdc, be0;
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) { bdc = (int) Server.rd(m, pc + 0xbdc); be0 = (int) Server.rd(m, pc + 0xbe0); }
        double[] after = me();
        sb.append("之后 PC+bdc=").append(bdc).append(" +be0=").append(be0).append("，人物 ").append(p3(after));
        boolean placed = ps != null && after != null && Math.hypot(after[0] - ps[0], after[1] - ps[1]) < 5000;
        if (placed) sb.append("（原生摆到出生点了）；");
        else {
            // 原生没摆上（FindPlayerStart 没挑到 / 没跑）→ 退回传送到同一个 PlayerStart
            sb.append("（原生没摆到出生点：").append(ps == null ? "位置没有 PlayerStart" : bdc < 0 ? "AddPlayerToSlot 没写块号（PlayerState 空或没找到位置）" : "FindPlayerStart 没挑到这个位置的 PlayerStart")
              .append("）→ 退回传送 ");
            if (ps != null && pawn >= 0x10000) sb.append(Veh.teleport(pawn, ps[0], ps[1], ps[2] + 100, 165) ? "ok" : "失败");
            else sb.append("没法传（没出生点/人物）");
            sb.append("；");
        }
        // Client Load Block：块号/块数都用 AddPlayerToSlot 原生写进 PC 的值；没写就用位置块号 + GM 块数
        int lb = bdc >= 0 ? bdc : blk, lc = be0 > 0 ? be0 : cnt, cur, curN, aft, aftN;
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) { cur = (int) Server.rd(m, mgr + 0x440); curN = (int) Server.rd(m, mgr + 0x43c); }
        sb.append("LoadBlock 前 管理器 +440 当前块=").append(cur).append(" +43c 块数=").append(curN).append("；");
        if (cur != lb) {
            // 和 Client Load Block 0x1086960 一样只调 LoadBlock（它里面 Fill 的是 +0x370 客户端层）。v3.42/v3.43 额外 Fill 的 +0x37c 服务器层已删（见类注释）
            long[] r = Server.gcall(b + LOAD_BLOCK, mgr, lb, lc, 0, 3000);
            sb.append("LoadBlock(管理器 ").append(Long.toHexString(mgr)).append(", ").append(lb).append(", ").append(lc).append(")").append(r[0] != 0 ? " 已跑" : " 没跑");
        } else sb.append("管理器当前块已是 ").append(lb).append("（LoadBlock 会直接说 Already Load Block 退出），不重复调");
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) { aft = (int) Server.rd(m, mgr + 0x440); aftN = (int) Server.rd(m, mgr + 0x43c); }
        sb.append("；之后 +440=").append(aft).append(" +43c=").append(aftN).append("；不填服务器层 +0x37c（v3.44 删）");
        block = sb.toString(); note(block);
        if (block.contains("没跑") || block.contains("退回传送")) GameOverlay.toastAny("训练岛" + block);
        return ps;
    }

    static long holdCmc; static float holdGrav = Float.NaN;
    /** 停住人物但不碰时间缩放：移动组件（人物+0x2f8）GravityScale(+0x120)=0、Velocity(+0xb4..)=0；放开时还原重力 */
    static void hold(long pawn, boolean on) {
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "rw")) {
            long b = Mods.base();
            if (!Mods.isObj(m, b, pawn)) return;
            long c = Server.rd(m, pawn + Mods.P_CMC); if (c < 0x10000 || !Mods.isObj(m, b, c)) return;
            if (on) {
                if (holdCmc != c || Float.isNaN(holdGrav)) { holdCmc = c; float g = Mods.f(m, c + Fly.C_GRAV); holdGrav = g == 0f ? 1f : g; }
                Mods.wf(m, c + Fly.C_GRAV, 0f);
                Mods.wf(m, c + Mods.C_VEL, 0f); Mods.wf(m, c + Mods.C_VEL + 4, 0f); Mods.wf(m, c + Mods.C_VEL + 8, 0f);
            } else if (c == holdCmc && !Float.isNaN(holdGrav)) { Mods.wf(m, c + Fly.C_GRAV, holdGrav); holdGrav = Float.NaN; }
        } catch (Throwable ignored) {}
    }

    /** 摆人后跟 120 秒：每 5 秒记坐标/地形块/+a70/+a21 和流式简况；人物停重力，每秒补一次速度清零；
     *  脚下射线打到地形（或 +a70 置了、或 90 秒）就还原重力 */
    static void follow(long b, Veh.Names nm, long gm, long pc, double[] ps, long mgr) throws Exception {
        StringBuilder tr = new StringBuilder("坐标/地形块随时间：");
        long w0 = System.currentTimeMillis(), pawn = Mods.myPawn(); boolean held = pawn >= 0x10000, done = false; String why = ""; long lastRec = -10;
        while (System.currentTimeMillis() - w0 < 120000) {
            if (!Con.training || !Con.inMatch) { if (held) hold(pawn, false); return; }
            long s = (System.currentTimeMillis() - w0) / 1000;
            if (held) hold(pawn, true);
            if (s - lastRec < 5) { Thread.sleep(1000); continue; }
            lastRec = s;
            int a70, a21, bdc; try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) { a70 = rb(m, gm + 0xa70); a21 = rb(m, gm + 0xa21); bdc = (int) Server.rd(m, pc + 0xbdc); }
            int land = scan(b, nm).land; double[] p = me();
            float g = Float.NaN;
            if (held && p != null) { Trace.Hit h = Trace.down((float) p[0], (float) p[1], (float) p[2] + 5000, (float) p[2] - 20000); if (h != null && h.land) g = h.z; }
            tr.append(s).append("s ").append(p3(p)).append(" 地形块 ").append(land).append(" a70=").append(a70).append(" a21=").append(a21).append(" bdc=").append(bdc).append(Float.isNaN(g) ? "" : " 地面 " + (int) g)
              .append(" [").append(streamDiag(b, nm, mgr, false)).append("]；");
            track = tr.toString();
            if (s <= 6 || s % 20 < 5) { stream = streamDiag(b, nm, mgr, true); }
            if (held) {
                if (!Float.isNaN(g)) why = "脚下射线打到地形";
                else if (a70 != 0) why = "+a70 已置（Landscape 层加载完）";
                else if (s >= 90) why = "90 秒到了，地形还没到也放开";
                if (why.length() > 0) {
                    if (!Float.isNaN(g) && p != null && p[2] < g + 50) { Veh.teleport(pawn, p[0], p[1], g + 120, 165); why += "，人物低于地面，抬到地面上"; }
                    hold(pawn, false); held = false; tr.append("【放开：").append(why).append("】");
                    note("训练岛：放开人物（" + why + "）");
                }
            }
            if (a21 != 0 && !done) { done = true; tr.append("【OnAllDuplicateLevelLoaded 已处理 +a21=1】"); }
            if (!held && s >= 40) break;
            Thread.sleep(1000);
        }
        if (held) hold(pawn, false);
        track = tr.toString(); note(track);
        stream = streamDiag(b, nm, mgr, true); note(stream);
        double[] me = me();
        state = "训练岛：摆人后人物在 " + p3(me) + (ps == null ? "" : "，离位置出生点 " + (me == null ? "?" : (int) (Math.hypot(me[0] - ps[0], me[1] - ps[1]) / 100) + " 米"));
        note(state);
    }

    /** 缓存数组（TArray，每项 0x20：[0] 层名 FName，+8 关卡数组，+0xc 关卡数，+0x18 瓦片信息数）→ 「名字×关卡数」 */
    static String cacheNames(RandomAccessFile m, Veh.Names nm, long arr) {
        long d = Server.rd(m, arr); int n = (int) Server.rd(m, arr + 4);
        if (d < 0x10000 || n <= 0 || n > 64) return n + " 个";
        StringBuilder sb = new StringBuilder().append(n).append(" 个（");
        for (int i = 0; i < n; i++) { long e = d + 0x20L * i; sb.append(Box.nameOf(m, nm, Server.rd(m, e))).append('×').append((int) Server.rd(m, e + 0xc)).append(i + 1 < n ? " " : ""); }
        return sb.append("）").toString();
    }

    /** 流式状态（只读）。full=false 时只出一小段：层更新计数和、包住人物的关卡数、关卡流对象 已加载/可见/总数。
     *  偏移（libUE4 静态读码，未实测）：管理器 +0x2f4/+0x2f8 当前层 TSharedPtr 数组（每项 8 字节）；层对象 +0x10 Update 计数、
     *  +0x14/+0x18 关卡项 TSharedPtr 数组，关卡项 +8 minX、+0xc minY、+0x14 maxX、+0x18 maxY（TilesCollection::Update 0x117627c）；
     *  管理器 +0x358/+0x35c TileLayer 建的关卡流对象；ULevelStreaming +0x110 LoadedLevel，ULevel +0x174 bit3 可见（IsLevelLoaded/IsLevelVisible 原生）；
     *  world = 管理器 Outer(关卡) 的 Outer，+0x54/+0x58 StreamingLevels，+0x768 OriginLocation。 */
    static String streamDiag(long b, Veh.Names nm, long mgr, boolean full) {
        StringBuilder sb = new StringBuilder(full ? "流式状态：" : "");
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
            if (mgr < 0x10000 || !Mods.isObj(m, b, mgr)) return sb.append("管理器没了").toString();
            double[] me = me(); float px = me == null ? Float.NaN : (float) me[0], py = me == null ? Float.NaN : (float) me[1];
            long ld = Server.rd(m, mgr + 0x2f4); int ln = (int) Server.rd(m, mgr + 0x2f8);
            long upd = 0; int lvTot = 0, lvIn = 0; StringBuilder per = new StringBuilder();
            for (int i = 0; i < ln && i < 40 && ld >= 0x10000; i++) {
                long L = Server.rd(m, ld + 8L * i); if (L < 0x10000) { per.append(" 空"); continue; }
                long vt = Server.rd(m, L); int cntU = (int) Server.rd(m, L + 0x10); long ed = Server.rd(m, L + 0x14); int en = (int) Server.rd(m, L + 0x18);
                upd += cntU; int in = 0; float x0 = Float.MAX_VALUE, y0 = Float.MAX_VALUE, x1 = -Float.MAX_VALUE, y1 = -Float.MAX_VALUE;
                for (int j = 0; j < en && j < 400 && ed >= 0x10000; j++) {
                    long E = Server.rd(m, ed + 8L * j); if (E < 0x10000) continue;
                    float a = Mods.f(m, E + 8), c = Mods.f(m, E + 0xc), d = Mods.f(m, E + 0x14), e = Mods.f(m, E + 0x18);
                    x0 = Math.min(x0, a); y0 = Math.min(y0, c); x1 = Math.max(x1, d); y1 = Math.max(y1, e);
                    if (px >= a && px <= d && py >= c && py <= e) in++;
                }
                lvTot += Math.max(en, 0); lvIn += in;
                if (full) per.append("\n    层").append(i).append(" vt+").append(Long.toHexString(vt - b)).append(" 更新计数 ").append(cntU).append(" 关卡 ").append(en).append(" 包住人物 ").append(in)
                    .append(en > 0 && x0 < Float.MAX_VALUE ? " 范围 " + (int) x0 + ".." + (int) x1 + "," + (int) y0 + ".." + (int) y1 : "");
            }
            long sd = Server.rd(m, mgr + 0x358); int sn = (int) Server.rd(m, mgr + 0x35c);
            int loaded = 0, vis = 0, dup = 0; java.util.HashSet<Long> uniq = new java.util.HashSet<>(); StringBuilder names = new StringBuilder();
            for (int i = 0; i < sn && i < 600 && sd >= 0x10000; i++) {
                long S = Server.rd(m, sd + 4L * i); if (S < 0x10000) continue;
                if (!uniq.add(S)) { dup++; continue; }
                long lvl = Server.rd(m, S + 0x110); boolean v = lvl >= 0x10000 && (rb(m, lvl + 0x174) & 8) != 0;
                if (lvl >= 0x10000) loaded++; if (v) vis++;
                if (full && names.length() < 1500 && (i < 12 || Alive.objName(m, nm, S).contains("Heightmap")))
                    names.append(Alive.objName(m, nm, S)).append(lvl >= 0x10000 ? (v ? "(载+显)" : "(载)") : "(未载)").append(' ');
            }
            if (!full) return sb.append("层更新计数和 ").append(upd).append(" 包住人物关卡 ").append(lvIn).append("/").append(lvTot).append(" 流对象 载").append(loaded).append(" 显").append(vis).append("/").append(uniq.size()).append(dup > 0 ? " 重复" + dup : "").toString();
            long lvl0 = Server.rd(m, mgr + 0x18), world = lvl0 >= 0x10000 ? Server.rd(m, lvl0 + 0x18) : 0;
            int wn = -1, wl = 0; String wName = "?"; int ox = 0, oy = 0;
            if (world >= 0x10000) {
                wName = Alive.clsName(m, nm, world);
                long wd = Server.rd(m, world + 0x54); wn = (int) Server.rd(m, world + 0x58);
                for (int i = 0; i < wn && i < 1000 && wd >= 0x10000; i++) { long S = Server.rd(m, wd + 4L * i); if (S >= 0x10000 && Server.rd(m, S + 0x110) >= 0x10000) wl++; }
                ox = (int) Server.rd(m, world + 0x768); oy = (int) Server.rd(m, world + 0x76c);
            }
            sb.append("管理器 ").append(Long.toHexString(mgr)).append(" +400 首次加载标志=").append(rb(m, mgr + 0x400)).append(" +409=").append(rb(m, mgr + 0x409))
              .append(" +43c 块数=").append((int) Server.rd(m, mgr + 0x43c)).append(" +440 当前块=").append((int) Server.rd(m, mgr + 0x440)).append(" +2c8 服务器层=").append(rb(m, mgr + 0x2c8))
              .append(" 块偏移 +468/+46c=").append((int) Mods.f(m, mgr + 0x468)).append(',').append((int) Mods.f(m, mgr + 0x46c))
              .append(" 策略 +30c=").append(Long.toHexString(Server.rd(m, mgr + 0x30c))).append(" +314=").append(Long.toHexString(Server.rd(m, mgr + 0x314)))
              .append("；人物 ").append(p3(me)).append("；world ").append(wName).append(" 原点 ").append(ox).append(',').append(oy)
              .append("；客户端层缓存 +370 ").append(cacheNames(m, nm, mgr + 0x370)).append("；服务器层缓存 +37c ").append(cacheNames(m, nm, mgr + 0x37c))
              .append("；当前层 ").append(ln).append(" 个，层更新计数和 ").append(upd).append("，关卡项 ").append(lvTot).append("，包住人物 ").append(lvIn).append(per)
              .append("\n  TileLayer 关卡流对象 +358：").append(sn).append(" 个（不同 ").append(uniq.size()).append("，重复指针 ").append(dup).append("），已加载 ").append(loaded).append("，可见 ").append(vis)
              .append("；world StreamingLevels ").append(wn).append(" 个，已加载 ").append(wl)
              .append("\n  关卡流对象：").append(names);
        } catch (Throwable t) { sb.append("读出错 ").append(t); }
        return sb.toString();
    }

    /** 射击训练区：按 pak 里 LogicLevel 的 shootreturn + 本块偏移算出来，再数它 150 米内的靶子。只诊断，不传送（原生出生点离它约 61 米）。 */
    static void rangeDiag(long b, Veh.Names nm, long mgr, long pc) throws Exception {
        int bdc, cnt, w, h;
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) { bdc = (int) Server.rd(m, pc + 0xbdc); cnt = (int) Server.rd(m, mgr + 0x43c); w = (int) Server.rd(m, mgr + 0x41c); h = (int) Server.rd(m, mgr + 0x420); }
        double[] off = offset(cnt, Math.max(bdc, 0), w, h);
        double rx = SHOOT_X + off[0], ry = SHOOT_Y + off[1];
        double[] me = me();
        Scan s = scan(b, nm);
        int nearR = 0; for (float[] p : s.target) if ((p[0] - rx) * (p[0] - rx) + (p[1] - ry) * (p[1] - ry) < 15000.0 * 15000.0) nearR++;
        range = "射击训练区：按 pak shootreturn + 块 " + bdc + " 偏移（块数 " + cnt + "）算在 " + (int) rx + "," + (int) ry + "," + (int) SHOOT_Z + "；人物 " + p3(me)
            + (me == null ? "" : "，离它 " + (int) (Math.hypot(me[0] - rx, me[1] - ry) / 100) + " 米") + "；它 150 米内靶子 " + nearR + " 个（全图靶子 " + s.target.size() + "）" + clusters(s.target);
        note(range);
    }

    /** 往 TArray<指针>（data,num,max）尾部加一个。空间不够用游戏的 FMemory::Realloc 0x1989d38（引擎以后用 FMemory 释放）。
     *  先写数据和 max，最后写 num，游戏线程同时读也不会读到空指针。 */
    static String append(long b, long arr, long v) throws Exception {
        long data; int num, max;
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) { data = Server.rd(m, arr); num = (int) Server.rd(m, arr + 4); max = (int) Server.rd(m, arr + 8); }
        if (num < 0 || num > 4096) return "数组数目不对 " + num;
        if (data != 0 && num < max) {
            Mods.pokeInt(data + 4L * num, (int) v);
            return Mods.pokeInt(arr + 4, num + 1);
        }
        int nmax = Math.max(4, num * 2);
        long[] r = Server.gcall(b + REALLOC, data, nmax * 4, 0, 0, 3000);
        if (r[0] == 0 || r[1] < 0x10000) return "分配内存失败（" + (r[0] == 0 ? "游戏线程没接" : "返回 " + Long.toHexString(r[1])) + "）";
        Mods.pokeInt(r[1] + 4L * num, (int) v);
        Mods.pokeInt(arr, (int) r[1]); Mods.pokeInt(arr + 8, nmax);
        return Mods.pokeInt(arr + 4, num + 1);
    }

    static int root(int[] g, int i) { while (g[i] != i) i = g[i] = g[g[i]]; return i; }
    static double d2(float[] a, double[] b) { double x = a[0] - b[0], y = a[1] - b[1]; return x * x + y * y; }
    static double d2(float[] a, float[] b) { double x = a[0] - b[0], y = a[1] - b[1]; return x * x + y * y; }
    static double nn(ArrayList<float[]> g, float[] p) { double m = 1e18; for (float[] q : g) if (q != p) m = Math.min(m, d2(p, q)); return m; }
    /** 全图靶子按 50 米连群，列每群中心和个数（最多 12 群） */
    static String clusters(ArrayList<float[]> t) {
        int n = Math.min(t.size(), 400); if (n == 0) return "";
        int[] g = new int[n]; for (int i = 0; i < n; i++) g[i] = i;
        for (int i = 0; i < n; i++) for (int j = i + 1; j < n; j++) if (d2(t.get(i), t.get(j)) < 5000.0 * 5000.0) { int a = root(g, i), c = root(g, j); if (a != c) g[a] = c; }
        HashMap<Integer, double[]> c = new HashMap<>();
        for (int i = 0; i < n; i++) { double[] s = c.computeIfAbsent(root(g, i), k -> new double[4]); s[0] += t.get(i)[0]; s[1] += t.get(i)[1]; s[2] += t.get(i)[2]; s[3]++; }
        StringBuilder sb = new StringBuilder("；靶子群："); int cntG = 0;
        for (double[] s : c.values()) { if (cntG++ >= 12) { sb.append("…"); break; } sb.append((int) (s[0] / s[3])).append(',').append((int) (s[1] / s[3])).append(',').append((int) (s[2] / s[3])).append('×').append((int) s[3]).append(' '); }
        return sb.toString();
    }

    /** 分配完 20 秒后：原生有没有刷车/刷物资（只读） */
    static void spawnDiag(long b, Veh.Names nm, long gm) throws Exception {
        double[] me = Veh.info();
        Scan s = scan(b, nm);
        int vNear = 0, pNear = 0; double vMin = 1e18;
        if (me != null) {
            for (float[] p : s.veh) { double d = d2(p, me); if (d < 150000.0 * 150000.0) vNear++; vMin = Math.min(vMin, d); }
            for (float[] p : s.pick) if (d2(p, me) < 150000.0 * 150000.0) pNear++;
        }
        int a70, a21, bdc = -9;
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) { a70 = rb(m, gm + 0xa70); a21 = rb(m, gm + 0xa21); long pc = Veh.lastPc(); if (pc > 0x10000) bdc = (int) Server.rd(m, pc + 0xbdc); }
        spawn = "原生刷车/刷物资（分配后 20 秒）：车 " + s.veh.size() + " 辆（1.5 公里内 " + vNear + "，最近 " + (vMin > 1e17 ? "-" : (int) (Math.sqrt(vMin) / 100) + " 米") + "）；地上物资 " + s.pick.size() + "（1.5 公里内 " + pNear + "）；"
            + "刷车点 " + s.vsp + "，刷物资区 " + s.vol + "；系统：" + (s.sys.length() == 0 ? "无" : s.sys) + "；阶段 " + Bots.readPhase(gm) + "，+a70=" + a70 + " +a21=" + a21 + " PC+bdc=" + bdc;
        note(spawn);
    }

    static final class Scan {
        long mgr; int land, vsp, vol; String sys = "";
        ArrayList<String> psName = new ArrayList<>(); ArrayList<float[]> psPos = new ArrayList<>();
        ArrayList<float[]> target = new ArrayList<>(), veh = new ArrayList<>(), pick = new ArrayList<>();
    }
    /** 类型：0 不管，1 PlayerStart，2 地形块，3 地块管理器，4 靶子，5 车，6 地上物资，7 刷车点，8 刷物资区，9 刷车/物资系统 */
    static Scan scan(long b, Veh.Names nm) {
        Scan s = new Scan();
        StringBuilder sys = new StringBuilder();
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
            long g = Server.rd(m, b + 0x51250acL);
            long objs = Server.rd(m, g + 0x10), num = Server.rd(m, g + 0x18);
            if (objs < 0x10000 || num <= 0 || num > 0x200000) return s;
            byte[] raw = new byte[(int) num * 16];
            m.seek(objs); m.readFully(raw);
            ByteBuffer bb = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
            HashMap<Long, Integer> kind = new HashMap<>();
            for (int i = 0; i < num; i++) {
                long o = bb.getInt(i * 16) & 0xffffffffL; int fl = bb.getInt(i * 16 + 4);
                if (o < 0x10000 || (fl & 0x20000000) != 0) continue;
                long c = Server.rd(m, o + 0xc);
                if (c < 0x10000) continue;
                Integer k = kind.get(c);
                if (k == null) { k = classify(m, nm, c); kind.put(c, k); }
                if (k == 0) continue;
                if ((Server.rd(m, o + 4) & 0x10) != 0) continue;   // 类默认对象
                switch (k) {
                    case 1: { float[] p = pos(m, o); if (p != null) { s.psName.add(Alive.objName(m, nm, o)); s.psPos.add(p); } break; }
                    case 2: s.land++; break;
                    case 3: if (s.mgr == 0) s.mgr = o; break;
                    case 4: { float[] p = pos(m, o); if (p != null) s.target.add(p); break; }
                    case 5: { float[] p = pos(m, o); if (p != null) s.veh.add(p); break; }
                    case 6: { float[] p = pos(m, o); if (p != null) s.pick.add(p); break; }
                    case 7: s.vsp++; break;
                    case 8: s.vol++; break;
                    case 9: sys.append(Alive.clsName(m, nm, o)).append(' '); break;
                }
            }
        } catch (Throwable ignored) {}
        s.sys = sys.toString().trim();
        return s;
    }
    static float[] pos(RandomAccessFile m, long o) throws java.io.IOException {
        long root = Server.rd(m, o + 0x128); if (root < 0x10000) return null;
        return new float[] { Mods.f(m, root + 0x140), Mods.f(m, root + 0x144), Mods.f(m, root + 0x148) };
    }
    /** 按类和父类名（UStruct+0x58 父类链，+0x5c 深度；IsA 0x1082ce4 那种写法）分类 */
    static int classify(RandomAccessFile m, Veh.Names nm, long c) {
        String n = Alive.objName(m, nm, c);
        if (n.equals("PlayerStart")) return 1;
        if (n.equals("NZStreamingLevelsManagerActor_TileMode")) return 3;
        if (n.startsWith("LandscapeStreamingProxy") || n.equals("LandscapeProxy") || n.equals("Landscape")) return 2;
        if (n.contains("Comp") || n.contains("Statistic") || n.contains("Manager")) return 0;
        if (n.equals("SpawnVehicleSystemSystem") || n.equals("PickUpRefreshSystem")) return 9;
        String chain = chain(m, nm, c);
        if (chain.contains("|NZSceneItem_RangeTarget|")) return 4;
        if (chain.contains("|NZVehicleSpawnPoint|")) return 7;
        if (chain.contains("|NZBRPickUpRefreshTriggerVolume|") || n.contains("RefreshVolum")) return 8;
        if (chain.contains("|SpawnVehicleSystemSystem|") || chain.contains("|PickUpRefreshSystem|")) return 9;
        if (n.contains("SpawnPoint") || n.contains("System") || n.contains("Volum") || n.contains("Trigger")) return 0;
        if (chain.matches(".*\\|[A-Za-z_]*Vehicle\\|.*")) return 5;
        if (chain.contains("PickUp") || chain.contains("Pickup")) return 6;
        return 0;
    }
    static String chain(RandomAccessFile m, Veh.Names nm, long c) {
        StringBuilder sb = new StringBuilder("|");
        try {
            long arr = Server.rd(m, c + 0x58); int d = (int) Server.rd(m, c + 0x5c);
            if (arr < 0x10000 || d < 0 || d > 32) return "|" + Alive.objName(m, nm, c) + "|";
            for (int i = 0; i <= d; i++) { long e = Server.rd(m, arr + 4L * i); if (e < 0x10000) continue; sb.append(Alive.objName(m, nm, e - 0x58)).append('|'); }
        } catch (Throwable t) { return "|" + Alive.objName(m, nm, c) + "|"; }
        return sb.toString();
    }
}
