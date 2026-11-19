package com.zframe.inject;

import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** v3.16 用户：两个人单挑，20 个头定胜负；雨林、海岛各一个；开局前自由选枪；被打死 5 秒复活、复活后 3 秒无敌；
 *  两把一样的枪 + 300 发子弹，配件放背包，3 级头 3 级甲，背包用喷气背包。
 *  v3.17：自定单挑点（v3.21 删掉，改自动选）、看不见的固定圈。
 *  v3.18：车当掩体（v3.19 删掉，用户说太敷衍）、认手上的枪、开局被挪走拉回。
 *  v3.19 用户：
 *   - 掩体要真的墙：Walls.java 用地图里已加载的墙/集装箱/箱子模型刷 AStaticMeshActor（复制给客户端），外圈一圈无缝围墙 + 场内长短墙、L 形、交错墙、箱子，
 *     墙顶至少高出地面 3.3 米（跳起来也打不过去），埋地 1.2 米防坡地悬空，离两个出生点 7 米内不放。看不见的圈留着当后备（改成围墙外 3 米才掉血）。
 *   - 无敌要真的：游戏自己的出生保护（NZGameMode 伤害 0xc8b450：人物+0x7e0 bSpawnProtectionEligible 且 世界时间(world+0x740) - 人物+0x114 < GameState+0x3c8
 *     SpawnProtectionTime 就把伤害改 0，日志 "don't take damage in SpawnProtection"）。复活发完装备后：GameState 时长写 3 秒、人物 Eligible=1、
 *     +0x114 改成现在的世界时间（从这一刻起 3 秒）。三个字段都是复制的（Net），客户端 IsSpawnProtected 也能算出来。
 *     看得见的效果：这 3 秒里人物每 0.25 秒隐藏/显示一次（AActor::SetActorHiddenInGame 虚表 +0x160，bHidden 复制）= 闪烁，结束一定恢复显示。补满血留着当后备。
 *   - 两个人不一定同时进：两个加入的玩家（控制器 +0x2fc 是 NetConnection，房主自己的幽灵人物不算）都在、都活着才开始；等人时先到的人站在自己的点上
 *     （离开 5 米拉回、补满血、不掉圈血、死了不算分）。开打后有人掉线就暂停（不算分、不掉血），回来（新控制器）接着用原来的分数和枪。
 *  v3.20 用户：打完 20 个头不用退房间，同一张图接着打下一把（newRound）：
 *   - 结束：房主 toast + 日志；10 秒后两人都在就自动重开，有人不在就清场回到「等待对手加入」，两人到齐自动开始。
 *   - 对局里服务端常用页「再开一把」：随时（选枪中、打到一半、结束后）马上重开。按钮只置标志，清场在单挑线程里做（和摆枪/发装备/复活串行，不会两个人同时动一个东西）。
 *   - 清场：等上一把的围墙线程结束 → 丢掉背包里的子弹/投掷物（游戏自己的 ServerDiscardItem 0xcd6728，Box 已用过）→ 销毁上一把的墙（Walls.spawned）
 *     → 销毁场地里所有地上拾取物/死亡盒（上一把摆的枪、丢出来的东西、死亡掉落），用 AActor::K2_DestroyActor = 虚表 +0x164
 *     （exec thunk 0x2e6f950 就是 bx [vt+0x164]；AStaticMeshActor 虚表 0x4f42690 的 +0x164 = 0x1140af4 → AActor::Destroy 0x273e8d4(this,0,1) → UWorld::DestroyActor 0x2aab2e8，
 *     函数里的字符串 "UWorld::DestroyActor: World has no context!" 核对过；服务端权威，复制的 actor 销毁会同步给客户端）
 *     → 比分清零、每人的计时/标志清掉 → 活着的传回自己的点、补满血、原生出生保护；死着的立刻 RestartPlayer → 重新摆枪选枪（45 秒）→ 重刷围墙。
 *   - 身上的枪/头甲不删：v1.98 实测丢枪闪退过，没有安全的删枪路径 → 留着，新一把照常发新装备（日志写明）；选枪时只认这一把新捡的枪（记下重开时背包里已有的枪对象）。
 *   - 不调用游戏任何结束/重开对局的函数，不碰游戏流程（不上飞机、不刷圈照旧）；单挑开着时强制「只剩房主那队不结算」补丁（HostMatch.solo）。
 *  v3.21 用户（12:08，生气）：不要让用户记录任何地点，程序自己选场地；服务端只开房什么都不做（房主人物隐藏冻结，不上飞机）。
 *   - 删掉 记录单挑点/清除单挑点、prefs duel_e/duel_s、「第一局上飞机跳、落地记点」。单挑一律 setNoZone（不上飞机、不刷圈）。
 *   - 玩家进来后在游戏放他的地方（出生岛）等；有人站稳就后台自动选场地（DuelArena.choose：地形块 + 射线检测平地、不在房子/水里）；
 *     两个人都在、都活着、都站稳 → 一起传到两个点（相距 50 米），然后照旧：围墙（每块射线贴地）、选枪、发装备、出生保护、计分、再开一把。
 *   - 选不到 → 用玩家 1 现在站的地方；同一进程同一张图沿用（围墙一块没刷成才换）。
 */
public class Duel {
    static volatile boolean on;
    static volatile String state = "单挑：没开";
    static final int WIN = 20;
    static final String RENEW = "/Game/Maps/ErangeMobileRenew_Runtime/BR-ErangelRenew_Mobile_Main";
    static final String SAVAGE = "/Game/Maps/SavageMobile_4x4_Runtime/BR-Savage_Mobile_Main";

    static class P {
        long ctl, pawn; int kills, idx = -1; long gun; String gunName = "", ammo = "1";
        float[] home, last; long rowAt, deadAt, protectUntil, movedAt, hurtAt, tryAt, leashAt, respawnSince;
        boolean dead, kitted, picked, placed, tele, present, reKit, ground; float[] gpos;
        final java.util.HashSet<Long> oldGuns = new java.util.HashSet<>(); long oldGunsPawn;   // v3.20：重开时背包里已有的枪（选枪不算）
    }
    static final java.util.LinkedHashMap<Long, P> ps = new java.util.LinkedHashMap<>();
    static volatile boolean over, started, paused; static volatile String winner = "";
    static Thread th;
    // v3.20 再开一把
    static volatile boolean rematchReq, resetting; static volatile String rematchWhy = ""; static volatile long endAt; static volatile int round = 1, gen;
    static volatile Thread wallsTh; static volatile String lastReset = "";

    static volatile boolean savage;
    /** v3.21 用户：不要让用户记录任何地点，服务端只开房什么都不做 → 场地由 DuelArena.choose 自动选（见 DuelArena.java），没有记点按钮、没有 prefs */
    static volatile float[][] spots; static volatile Thread arenaTh; static volatile long arenaRetryAt; static volatile int mgen;
    static String mapKey() { String mp = Con.brMap == null ? "" : Con.brMap; return mp.isEmpty() ? (savage ? "S" : "E") : mp; }
    static boolean falling(RandomAccessFile m, long pawn) {
        try { long cmc = Server.rd(m, pawn + Mods.P_CMC) & 0xffffffffL; if (cmc < 0x10000) return false; byte[] x = new byte[1]; m.seek(cmc + Mods.C_MODE); m.readFully(x); return x[0] == Mods.MOVE_FALLING; }
        catch (Throwable t) { return false; }
    }
    static volatile float[] center; static volatile float radius;
    static void arena(float[] a, float[] c) {
        float d = (float) Math.hypot(a[0] - c[0], a[1] - c[1]);
        center = new float[] { (a[0] + c[0]) / 2, (a[1] + c[1]) / 2 };
        radius = Math.max(10000f, d / 2 + 6000f);
    }
    /** 圈外：有围墙时按围墙矩形外 3 米算，没围墙按圆 */
    static boolean outside(float[] q) {
        Walls.Arena A = Walls.arena;
        if (A != null && Walls.ok > 0) return !A.inside(q[0], q[1], 300f);
        float[] c = center; return c != null && Math.hypot(q[0] - c[0], q[1] - c[1]) > radius;
    }
    static String open(boolean sv) {
        Training.off();
        on = true; reset(); savage = sv;
        // v3.21：单挑一律不上飞机、不刷圈（v3.17~3.20 没记点时 setPlane(false) 让所有人上飞机 → 房主隐藏人物被飞机带走、视角进灰色虚空）
        String z = Mods.setNoZone();
        state = "单挑：开" + (savage ? "雨林" : "海岛") + "（不上飞机 " + z + "），等两个人进来，场地自动选";
        start();
        if (sv) return Lan.host(SAVAGE);
        return ModeProbe.host("Props", "单挑", RENEW);
    }
    static void off() { on = false; }
    /** 每局开始（HostMatch.newMatch）和每次开房都调：清掉上一局的人、分数、围墙记录（上一张图的墙随世界一起销毁） */
    static void reset() {
        synchronized (ps) { ps.clear(); }
        over = false; started = false; paused = false; winner = ""; center = null; wallsDone = false; protGs = 0; announced = false;
        spots = null; arenaTh = null; arenaRetryAt = 0; mgen++; arenaInfo = "场地：等有人进来站稳后自动选";
        rematchReq = false; endAt = 0; round = 1; gen++; lastReset = "";
        Walls.reset();
    }
    /** v3.20 对局里服务端「再开一把」：只置标志，单挑线程下一次循环清场重开 */
    static String rematch() {
        if (!on) return "现在没在单挑（开房页开「单挑·海岛/雨林」）";
        if (Lan.role != 1 || !Con.inMatch) return "要在单挑对局里（房主）按";
        if (resetting) return "正在重开，稍等几秒";
        rematchWhy = "房主按了「再开一把」"; rematchReq = true;
        LogCap.note("单挑：房主按了「再开一把」（第 " + round + " 把" + (over ? "已结束" : started ? "打到一半" : "还没开始") + "）");
        return "马上重开：清墙、清地上的枪、比分归零，两人回到自己的点";
    }
    static volatile boolean wallsDone, announced; static volatile long protGs; static volatile String arenaInfo = "场地：还没选";

    static void start() {
        if (th != null && th.isAlive()) return;
        th = new Thread(() -> {
            while (true) {
                try { Thread.sleep(250); } catch (InterruptedException e) { return; }
                if (!on || Lan.role != 1 || !Con.inMatch) continue;
                try { tick(); } catch (Throwable t) { state = "单挑：出错 " + t; LogCap.note(state); try { Thread.sleep(2000); } catch (InterruptedException e) { return; } }
            }
        }, "zf-duel");
        th.start();
    }

    /** 找加入的玩家（控制器 +0x2fc 是 NetConnection，房主自己的不算），最多 2 个 */
    static java.util.ArrayList<long[]> players(RandomAccessFile m, Veh.Names nm) throws Exception {
        java.util.ArrayList<long[]> out = new java.util.ArrayList<>();
        double[] q = Veh.info(); long gm = q != null ? (long) q[4] : 0;
        long gs = gm >= 0x10000 ? Server.rd(m, gm + 0x2e0) : 0;
        long data = gs >= 0x10000 ? Server.rd(m, gs + 0x2bc) : 0;
        int n = gs >= 0x10000 ? (int) Server.rd(m, gs + 0x2c0) : 0;
        for (int k = 0; k < n && k < 200 && data >= 0x10000; k++) {
            long s = Server.rd(m, data + 4L * k); if (s < 0x10000) continue;
            long c = Server.rd(m, s + 0x60); if (c < 0x10000) continue;
            if (!Alive.clsName(m, nm, Server.rd(m, c + 0x2fc)).contains("NetConnection")) continue;
            out.add(new long[] { c, gm });
            if (out.size() >= 2) break;
        }
        return out;
    }
    static float[] pos(RandomAccessFile m, long p) throws Exception {
        long root = Server.rd(m, p + 0x128); if (root < 0x10000) return null;
        float x = Mods.f(m, root + 0x140), y = Mods.f(m, root + 0x144), z = Mods.f(m, root + 0x148);
        if (Math.abs(x) < 1 && Math.abs(y) < 1) return null;
        return new float[] { x, y, z };
    }
    static boolean live(RandomAccessFile m, Veh.Names nm, long p) throws Exception {
        if (p < 0x10000 || !Alive.clsName(m, nm, p).contains("Character")) return false;
        return Mods.f(m, p + 0x5a0) > 0f;
    }
    /** 背包物品列表里的第一把枪（201/202 开头） */
    static long gunIn(RandomAccessFile m, long p) throws Exception { return gunIn(m, p, null); }
    static long gunIn(RandomAccessFile m, long p, java.util.Set<Long> skip) throws Exception {
        long inv = Server.rd(m, p + 0x838); if (inv < 0x10000) return 0;
        long data = Server.rd(m, inv + 0x4a8), num = Server.rd(m, inv + 0x4ac);
        if (data < 0x10000 || num <= 0 || num > 300) return 0;
        for (int i = 0; i < num; i++) {
            long it = Server.rd(m, data + 4L * i); if (it < 0x10000) continue;
            if (skip != null && skip.contains(it)) continue;
            long id = Server.rd(m, it + 0x2cc) * 10000L + Server.rd(m, it + 0x2d0);
            long g = id / 1000000L / 100;
            if (g == 201 || g == 202) return id;
        }
        return 0;
    }
    /** 手上的枪：人物 +0x890，类名按关键字认编号 */
    static final String[][] KW = {
        {"m416", "20101000001"}, {"akm", "20101000002"}, {"m16", "20101000003"}, {"scar", "20101000004"}, {"groza", "20101000005"},
        {"m249", "20102000001"}, {"dp28", "20102000002"}, {"ump", "20105000001"}, {"uzi", "20105000002"}, {"vector", "20105000003"},
        {"tommy", "20105000004"}, {"thompson", "20105000004"}, {"s1897", "20103000001"}, {"s686", "20103000002"}, {"s12k", "20103000003"},
        {"kar98", "20104000001"}, {"98k", "20104000001"}, {"awm", "20104000002"}, {"m24", "20104000004"}, {"sks", "20104000003"},
        {"mk14", "20104000005"}, {"mini14", "20104000006"}, {"vss", "20104000007"}, {"qbu", "20104000008"},
        {"p92", "20201000001"}, {"p1911", "20201000002"}, {"r1895", "20201000003"}, {"p18c", "20201000004"}, {"r45", "20201000006"} };
    static volatile String lastWeapCls = "";
    static long gunInHand(RandomAccessFile m, long b, Veh.Names nm, long p) throws Exception {
        long w = Mods.rp(m, p + 0x890);
        if (!Mods.isObj(m, b, w)) return 0;
        String cls = Alive.clsName(m, nm, w); lastWeapCls = cls;
        String k = cls.toLowerCase().replace("_", "").replace("-", "");
        for (String[] e : KW) if (k.contains(e[0])) return Long.parseLong(e[1]);
        return -1;
    }
    static String[] gunInfo(long id) {
        String s = Long.toString(id);
        for (String[] c : GameOverlay.CATS) for (int j = 1; j < c.length; j++) { String[] t = c[j].split(":"); if (t.length >= 3 && t[1].equals(s)) return t; }
        return new String[] { s, s, "1" };
    }
    static void heal(RandomAccessFile m, long pawn) throws Exception {
        float hp = Mods.f(m, pawn + 0x5a0), mx = Mods.f(m, pawn + 0x5a4);
        if (mx > 1f && mx < 1000f && hp > 0f && hp < mx) Mods.wf(m, pawn + 0x5a0, mx);
    }
    static void tele(java.util.ArrayList<Runnable> todo, long pawn, float[] h) {
        todo.add(() -> { try { Veh.teleport(pawn, h[0], h[1], h[2] + 50, 0); } catch (Throwable ignored) {} });
    }

    static void tick() throws Exception {
        long b = Mods.base(); if (b == 0) return;
        Veh.Names nm = Veh.Names.get();
        long now = System.currentTimeMillis();
        // ---- v3.20 再开一把：按钮 / 结束 10 秒后自动 ----
        if (rematchReq) { rematchReq = false; newRound(b, rematchWhy); return; }
        if (over && endAt > 0 && now - endAt >= 10000) {
            endAt = 0; int n;
            try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) { n = players(m, nm).size(); }
            if (n >= 2) newRound(b, "结束 10 秒，两人都在，自动开下一把");
            else { LogCap.note("单挑：结束 10 秒，在场的只有 " + n + "/2 人 → 清场回到等人，两人到齐自动开始"); newRound(b, "结束 10 秒有人不在，清场等人"); }
            return;
        }
        java.util.ArrayList<long[]> pl;
        java.util.ArrayList<Runnable> todo = new java.util.ArrayList<>();
        int liveN = 0;
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "rw")) {
            pl = players(m, nm);
            long gm = pl.isEmpty() ? 0 : pl.get(0)[1];
            if (gm >= 0x10000 && protGs != gm) { protGs = gm; nativeProtection(m, gm); }
            // ---- 认人：在场的控制器；掉线的人留着记录，新控制器（重连）在暂停时接手 ----
            java.util.HashSet<Long> here = new java.util.HashSet<>(); for (long[] e : pl) here.add(e[0]);
            synchronized (ps) {
                for (P p : ps.values()) { boolean was = p.present; p.present = here.contains(p.ctl); if (was && !p.present) LogCap.note("单挑：" + Long.toHexString(p.ctl) + " 离开了（" + p.gunName + " " + p.kills + " 头）"); }
                for (long[] e : pl) {
                    if (ps.containsKey(e[0])) continue;
                    P orphan = null;
                    for (P p : ps.values()) if (!p.present) { orphan = p; break; }
                    if (orphan != null && ps.size() >= 2) {   // 重连：接手原来的记录
                        ps.remove(orphan.ctl); LogCap.note("单挑：" + Long.toHexString(e[0]) + " 接手掉线玩家 " + Long.toHexString(orphan.ctl) + " 的记录（" + orphan.kills + " 头）");
                        orphan.ctl = e[0]; orphan.present = true; orphan.pawn = 0; orphan.dead = false; orphan.kitted = false; orphan.tryAt = 0; orphan.movedAt = now; orphan.reKit = orphan.picked; orphan.oldGuns.clear(); orphan.oldGunsPawn = 0;
                        ps.put(e[0], orphan);
                    } else if (ps.size() < 2) { P x = new P(); x.ctl = e[0]; x.present = true; x.idx = ps.size(); ps.put(e[0], x); LogCap.note("单挑：玩家 " + (x.idx + 1) + " 进来了 " + Long.toHexString(x.ctl)); }
                }
            }
            java.util.ArrayList<P> all = snapshot();
            for (P p : all) if (p.present && live(m, nm, Server.rd(m, p.ctl + 0x2c0))) liveN++;
            boolean both = liveN >= 2 && all.size() >= 2 && all.get(0).present && all.get(1).present;
            // ---- 阶段切换 ----
            if (!started && both && all.get(0).placed && all.get(1).placed) { started = true; paused = false; LogCap.note("单挑：两个人都到了，开始（先选枪）"); GameOverlay.toastAny("单挑开始：两人都到了"); }
            else if (started && !over && !paused && !both) { paused = true; LogCap.note("单挑：有人掉线/不在，暂停（" + liveN + "/2 活着在场）"); GameOverlay.toastAny("单挑暂停：等对手回来"); }
            else if (started && paused && both) { paused = false; LogCap.note("单挑：两个人都回来了，继续"); GameOverlay.toastAny("单挑继续"); }
            boolean run = started && !paused && !over;

            for (P p : all) {
                if (!p.present) continue;
                long pawn = Server.rd(m, p.ctl + 0x2c0);
                boolean alive = live(m, nm, pawn);
                if (alive && now < p.protectUntil) heal(m, pawn);   // 后备：保护期内掉血就补满
                // ---- v3.21 出生点：先等他落地站稳（不在下落、1.5 秒没怎么动），两个人都站稳且场地选好后一起传到自动选的两个点 ----
                if (!p.placed) {
                    if (!alive) { p.ground = false; continue; }
                    p.pawn = pawn;
                    float[] q = pos(m, pawn); if (q == null) continue;
                    if (falling(m, pawn) || p.last == null || Math.abs(q[0] - p.last[0]) + Math.abs(q[1] - p.last[1]) + Math.abs(q[2] - p.last[2]) > 60f) { p.last = q; p.movedAt = now; p.ground = false; continue; }
                    if (now - p.movedAt < 1500) continue;
                    if (!p.ground) LogCap.note("单挑：玩家 " + (p.idx + 1) + " 站稳了 " + java.util.Arrays.toString(q));
                    p.ground = true; p.gpos = q;
                    continue;
                }
                // ---- 等人 / 暂停：站在自己的点上，离开 5 米拉回，满血，不掉圈血，死了不算分 ----
                if (!run && !over && alive && p.home != null && !p.dead) {
                    heal(m, pawn);
                    float[] q = pos(m, pawn);
                    if (q != null && Math.hypot(q[0] - p.home[0], q[1] - p.home[1]) > 500 && now - p.leashAt > 3000) { p.leashAt = now; tele(todo, pawn, p.home); }
                }
                // ---- 选枪（开打后才摆枪） ----
                if (run && !p.picked && alive) {
                    p.pawn = pawn;
                    if (p.rowAt == 0) {
                        float[] to = null; for (P o : all) if (o != p && o.home != null) to = o.home;
                        final float[] at = p.home, tf = to; p.rowAt = now; todo.add(() -> gunRow(at, tf)); continue;
                    }
                    boolean hadOld = p.oldGunsPawn == pawn && !p.oldGuns.isEmpty();
                    long g = gunIn(m, pawn, hadOld ? p.oldGuns : null);
                    // 重开时身上已经有枪的人只认背包里新出现的枪（他背包读得到）；手上的枪可能是上一把的，不用来判断
                    if (g == 0 && !hadOld) { long h = gunInHand(m, b, nm, pawn); if (h > 0) g = h; else if (h < 0 && now - p.rowAt > 8000) LogCap.note("单挑：手上的枪类名认不出 " + lastWeapCls); }
                    if (g == 0 && now - p.rowAt < 45000) continue;
                    p.gun = g != 0 ? g : 20101000001L;
                    String[] gi = gunInfo(p.gun); p.gunName = gi[0]; p.ammo = gi[2];
                    p.picked = true;
                    final boolean had = g != 0;
                    LogCap.note("单挑：玩家 " + (p.idx + 1) + " 选了 " + p.gunName + (had ? "" : "（45 秒没捡，默认）"));
                    todo.add(() -> kit(b, p, had ? 1 : 2));
                    continue;
                }
                // ---- v3.20 重连接手：已经选过枪的人回来，背包里没有枪就补发装备，传回自己的点 + 出生保护 ----
                if (run && alive && p.reKit && !p.dead) {
                    p.reKit = false; p.pawn = pawn;
                    final boolean has = gunIn(m, pawn) != 0; final long np = pawn; final float[] h = p.home;
                    LogCap.note("单挑：玩家 " + (p.idx + 1) + " 重连回来" + (has ? "，背包里还有枪，不补发" : "，补发装备") + "，传回自己的点");
                    todo.add(() -> {
                        try { if (h != null) Veh.teleport(np, h[0], h[1], h[2] + 50, 0); } catch (Throwable ignored) {}
                        if (!has) kit(b, p, 2);
                        protect(b, p, np);
                    });
                    continue;
                }
                // ---- 开打后 60 秒内被游戏挪走就拉回 ----
                if (run && alive && p.tele && !p.dead && p.home != null && now - p.movedAt < 60000 && now - p.tryAt > 3000) {
                    float[] q = pos(m, pawn);
                    if (q != null && Math.hypot(q[0] - p.home[0], q[1] - p.home[1]) > 5000) { p.tryAt = now; tele(todo, pawn, p.home); }
                }
                // ---- 圈（后备，看不见）：围墙外 3 米 / 圆外每秒 -10，最低 1 ----
                if (run && alive && now >= p.protectUntil && now - p.hurtAt >= 1000) {
                    float[] q = pos(m, pawn);
                    if (q != null && outside(q)) { p.hurtAt = now; float hp = Mods.f(m, pawn + 0x5a0); if (hp > 1f) Mods.wf(m, pawn + 0x5a0, Math.max(1f, hp - 10f)); }
                }
                if (alive && pawn != p.pawn && !p.dead) p.pawn = pawn;
                // ---- 死亡：只在开打且两人都选好枪时算分；同一刻两人都死各给对方 +1 ----
                if (!alive && !p.dead) {
                    p.dead = true; p.deadAt = now; p.respawnSince = now;
                    boolean count = run;
                    for (P o : all) if (o != p && !(o.picked && p.picked)) count = false;
                    if (count) {
                        for (P o : all) if (o != p) o.kills++;
                        LogCap.note("单挑：玩家 " + (p.idx + 1) + " 死了，比分 " + score());
                        int top = 0, topN = 0; P best = null;
                        for (P o : all) { if (o.kills > top) { top = o.kills; best = o; topN = 1; } else if (o.kills == top) topN++; }
                        if (top >= WIN && !over) {
                            over = true; winner = topN > 1 ? "平局" : "玩家 " + (best.idx + 1) + "（" + best.gunName + "）";
                            endAt = now;
                            LogCap.note("单挑：第 " + round + " 把结束，" + winner + "，比分 " + score() + "（只是我们自己的结束，不调游戏结算；10 秒后两人都在就自动再开一把）");
                            GameOverlay.toastAny("单挑结束：" + winner + " " + score() + "，10 秒后自动再开一把");
                            android.app.Activity a = Con.act; if (a != null) AvatarLog.export(a, "duel-");
                        }
                    } else LogCap.note("单挑：玩家 " + (p.idx + 1) + " 死了（" + (run ? "还在选枪" : paused ? "暂停中" : "还没开打") + "，不算分）");
                }
                // ---- 复活（任何阶段都复活，结束后不复活） ----
                if (p.dead && !over && now - p.deadAt >= 5000) {
                    if (alive && pawn != p.pawn) {
                        p.dead = false; p.pawn = pawn; p.protectUntil = now + 3000;
                        final float[] h = p.home; final long np = pawn; final boolean kit = p.picked;
                        todo.add(() -> {
                            try { if (h != null) Veh.teleport(np, h[0], h[1], h[2] + 50, 0); } catch (Throwable ignored) {}
                            if (kit) kit(b, p, 2);
                            protect(b, p, np);
                        });
                        LogCap.note("单挑：玩家 " + (p.idx + 1) + " 复活 新人物 " + Long.toHexString(pawn) + "（等了 " + (now - p.deadAt) / 1000 + " 秒）");
                    } else if (now - p.tryAt > 2500) {
                        p.tryAt = now; final long gmF = gm;
                        todo.add(() -> respawn(gmF, p));
                        if (now - p.respawnSince > 30000) { p.respawnSince = now; LogCap.note("单挑：玩家 " + (p.idx + 1) + " 30 秒还没复活（" + respawnState + "），继续每 2.5 秒试"); }
                    }
                }
            }
            // ---- v3.21 自动选场地：有人站稳了就开始选（后台线程，DuelArena.choose），选好前谁都不传 ----
            if (spots == null && arenaTh == null && now >= arenaRetryAt) {
                float[] fb = null; java.util.ArrayList<float[]> pp = new java.util.ArrayList<>();
                for (P p : all) { if (p.gpos != null) pp.add(p.gpos); if (fb == null && p.present && p.ground && p.gpos != null) fb = p.gpos; }
                if (fb != null) {
                    final float[] f0 = fb; final int g0 = mgen; final String key = mapKey();
                    arenaInfo = "场地：正在自动选…";
                    Thread t = new Thread(() -> {
                        float[][] s = null;
                        try { s = DuelArena.choose(key, f0, pp); } catch (Throwable e) { LogCap.note("单挑：选场地出错 " + e); }
                        if (g0 != mgen) return;
                        arenaInfo = DuelArena.state;
                        if (s != null) { spots = s; arena(s[0], s[1]); } else { arenaRetryAt = System.currentTimeMillis() + 10000; arenaTh = null; }
                    }, "zf-arena");
                    arenaTh = t; t.start();
                }
            }
            // ---- 两个人都在、都活着、都站稳（或已经在点上），场地也选好了 → 一起传过去 ----
            float[][] sp = spots;
            if (sp != null && all.size() >= 2) {
                boolean ready = true;
                for (P p : all) if (!p.present || !(p.placed || p.ground) || !live(m, nm, Server.rd(m, p.ctl + 0x2c0))) ready = false;
                if (ready) for (P p : all) {
                    if (p.placed || p.idx < 0 || p.idx > 1) continue;
                    long pawn = Server.rd(m, p.ctl + 0x2c0);
                    p.home = sp[p.idx]; p.placed = true; p.tele = true; p.movedAt = now; p.leashAt = now;
                    tele(todo, pawn, p.home); LogCap.note("单挑：玩家 " + (p.idx + 1) + " 传到自动选的单挑点 " + java.util.Arrays.toString(p.home));
                }
            }
        }
        // ---- 围墙：两个点都知道就刷（等人的时候就可以刷，后台线程） ----
        if (!wallsDone && spots != null && !pl.isEmpty()) {
            java.util.ArrayList<P> all = snapshot();
            wallsDone = true; final float[] fa = spots[0], fc = spots[1]; final String key = mapKey();
            final java.util.ArrayList<float[]> avoid = new java.util.ArrayList<>();
            try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) { for (P p : all) { long pw = Server.rd(m, p.ctl + 0x2c0); if (pw >= 0x10000) avoid.add(pos(m, pw)); } } catch (Throwable ignored) {}
            final int g0 = gen;
            Thread wt = new Thread(() -> {
                if (g0 != gen) return;
                Walls.build(fa, fc, avoid);
                if (Walls.ok == 0) { DuelArena.forget(key); LogCap.note("单挑：这个场地一块墙都没刷成，下一局换场地"); }
                android.app.Activity ac = Con.act; if (ac != null) AvatarLog.export(ac, "duel-");
            }, "zf-walls");
            wallsTh = wt; wt.start();
        }
        for (Runnable r : todo) r.run();
        String wait = !started ? (liveN >= 2 && spots == null ? "两人都在，等场地选好" : liveN >= 2 ? "两人都在，等站稳/传送" : "等待对手加入（" + liveN + "/2）") : paused ? "暂停：等对手回来（" + liveN + "/2）" : "";
        if (!started && !announced && liveN == 1) { announced = true; LogCap.note("单挑：等待对手加入 (1/2)"); GameOverlay.toastAny("单挑：等待对手加入 (1/2)"); }
        String rn = "第 " + round + " 把，";
        state = over ? "单挑：" + rn + "结束，" + winner + "，比分 " + score() + (endAt > 0 ? "，" + Math.max(0, (10000 - (now - endAt)) / 1000) + " 秒后自动再开" : "")
                     : "单挑：" + rn + (wait.isEmpty() ? "进行中" : wait) + "，比分 " + score() + "（" + WIN + " 个头赢）；" + arenaInfo + "；" + Walls.state + (lastReset.isEmpty() ? "" : "；上次重开：" + lastReset);
    }
    static java.util.ArrayList<P> snapshot() { synchronized (ps) { return new java.util.ArrayList<>(ps.values()); } }
    static String score() {
        StringBuilder s = new StringBuilder();
        for (P p : snapshot()) s.append(s.length() == 0 ? "" : " : ").append(p.picked ? p.gunName : "选枪中").append(" ").append(p.kills);
        return s.length() == 0 ? "等人加入" : s.toString();
    }
    static volatile String respawnState = "", protState = "";
    static void respawn(long gm, P p) {
        try {
            if (gm < 0x10000) { respawnState = "复活：没有 GameMode"; return; }
            long[] r = Veh.ucall(gm, p.ctl, 0x39c, 0, null);   // GameMode->RestartPlayer(控制器)
            respawnState = "复活：调 RestartPlayer 标记 " + Long.toHexString(r[0]);
        } catch (Throwable t) { respawnState = "复活出错 " + t; LogCap.note(respawnState); }
    }

    // ---------------- 出生保护（原生）+ 闪烁 ----------------
    static final float PROT = 3.0f;
    /** 每局一次：GameState(+0x3c8 SpawnProtectionTime) 写 3 秒。GM+0x2e0 和 GM+0x380 两个指针（伤害函数用 GM+0x380，IsSpawnProtected 用 World+0x98）都写，类名要含 GameState */
    static void nativeProtection(RandomAccessFile m, long gm) {
        StringBuilder sb = new StringBuilder("出生保护：");
        try {
            Veh.Names nm = Veh.Names.get();
            for (int off : new int[] { 0x2e0, 0x380 }) {
                long gs = Server.rd(m, gm + off);
                String cn = gs < 0x10000 ? "?" : Alive.clsName(m, nm, gs);
                if (gs < 0x10000 || !cn.contains("GameState")) { sb.append("GM+").append(Integer.toHexString(off)).append(" 不是 GameState（").append(cn).append("）；"); continue; }
                float old = Mods.f(m, gs + 0x3c8);
                Mods.wf(m, gs + 0x3c8, PROT);
                sb.append("GM+").append(Integer.toHexString(off)).append(" ").append(cn).append(" SpawnProtectionTime ").append(old).append("→").append(PROT).append("；");
            }
        } catch (Throwable t) { sb.append("出错 ").append(t); }
        protState = sb.toString(); LogCap.note("单挑：" + protState);
    }
    /** 复活发完装备后：Eligible=1、CreationTime=现在 → 从这一刻起 3 秒原生不掉血；同时每 0.25 秒隐藏/显示闪烁，最后一定显示 */
    static void protect(long b, P p, long pawn) {
        String how;
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "rw")) {
            double[] q = Veh.info(); long world = q == null ? 0 : (long) q[5];
            float t = world >= 0x10000 ? Mods.f(m, world + 0x740) : Float.NaN;
            float ct = Mods.f(m, pawn + 0x114);
            m.seek(pawn + 0x7e0); m.write(new byte[] { 1 });
            if (!Float.isNaN(t) && t > 0 && t < 1e7f) Mods.wf(m, pawn + 0x114, t);
            how = "原生出生保护：Eligible=1，CreationTime " + ct + "→" + t + "（世界时间），时长 " + PROT + " 秒";
        } catch (Throwable e) { how = "原生出生保护写入出错 " + e + "（只剩补满血）"; }
        p.protectUntil = System.currentTimeMillis() + 3000;
        LogCap.note("单挑：玩家 " + (p.idx + 1) + " " + how);
        final long pw = pawn; final int g0 = gen;
        new Thread(() -> {
            int ok = 0;
            try {
                for (int i = 0; i < 12 && g0 == gen; i++) { long[] r = Veh.ucall(pw, (i % 2 == 0) ? 1 : 0, 0x160, 0, null); if (r[0] == 0x5b0e) ok++; Thread.sleep(250); }
            } catch (Throwable ignored) {}
            finally { try { Veh.ucall(pw, 0, 0x160, 0, null); } catch (Throwable ignored) {} }
            LogCap.note("单挑：保护闪烁 " + ok + "/12 次（SetActorHiddenInGame），结束已显示");
        }, "zf-duel-blink").start();
    }

    // ---------------- v3.20 再开一把（同一张图，不调游戏任何结束/开局函数） ----------------
    static final int DESTROY = 0x164;   // AActor::K2_DestroyActor 虚表偏移（见类注释）
    /** 在单挑线程里调（和摆枪、发装备、复活串行）。清场 → 清分数/计时 → 传回 + 补血 + 出生保护 / 死的立刻复活 → 下一次循环重新摆枪、刷墙 */
    static void newRound(long b, String why) {
        resetting = true; final int myGen = ++gen; long t0 = System.currentTimeMillis();
        int r = round + 1;
        StringBuilder sb = new StringBuilder();
        LogCap.note("单挑：再开一把（第 " + r + " 把，" + why + "）：开始清场");
        GameOverlay.toastAny("单挑：正在重开第 " + r + " 把…");
        try {
            // 1. 上一把的围墙线程还在刷就等它刷完（不然刷完的墙没人删）
            Thread wt = wallsTh;
            if (wt != null && wt.isAlive()) { LogCap.note("单挑：等上一把的围墙刷完再清（最多 90 秒）"); wt.join(90000); if (wt.isAlive()) sb.append("围墙线程 90 秒没结束；"); }
            Walls.Arena A = Walls.arena;
            java.util.ArrayList<P> all = snapshot();
            Veh.Names nm = Veh.Names.get();
            // 2. 背包里的子弹(206)、投掷物(204)丢掉（游戏自己的 ServerDiscardItem；枪和头甲不丢：v1.98 丢枪闪退过）
            int disc = 0;
            for (P p : all) {
                if (!p.present || myGen != gen) continue;
                long pawn; try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) { pawn = Server.rd(m, p.ctl + 0x2c0); if (!live(m, nm, pawn)) continue; }
                disc += discardStack(b, pawn);
            }
            sb.append("丢掉子弹/投掷物 ").append(disc).append(" 件；");
            if (disc > 0) Thread.sleep(800);   // 让丢出来的东西先生成，下面一起删
            // 3. 上一把的墙
            if (myGen != gen) { sb.append("中途换图，停"); return; }
            sb.append(Walls.destroyAll(b)).append("；");
            // 4. 场地里地上的拾取物 / 死亡盒（摆的枪、丢出来的、死亡掉落）
            java.util.ArrayList<float[]> homes = new java.util.ArrayList<>();
            for (P p : all) if (p.home != null) homes.add(p.home);
            sb.append(sweep(b, A, homes, myGen)).append("；");
            if (myGen != gen) { sb.append("中途换图，停"); return; }
            // 5. 状态清零
            long now = System.currentTimeMillis();
            over = false; winner = ""; started = false; paused = false; endAt = 0; announced = false; wallsDone = false; round = r;
            int alive = 0, dead = 0;
            for (P p : all) {
                p.kills = 0; p.gun = 0; p.gunName = ""; p.ammo = "1"; p.picked = false; p.kitted = false; p.reKit = false;
                p.rowAt = 0; p.protectUntil = 0; p.hurtAt = 0; p.leashAt = 0; p.tryAt = 0; p.movedAt = now; p.respawnSince = now; p.last = null;
                p.oldGuns.clear(); p.oldGunsPawn = 0;
                if (!p.present) { p.dead = false; continue; }
                long pawn; boolean lv;
                try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "rw")) {
                    pawn = Server.rd(m, p.ctl + 0x2c0); lv = live(m, nm, pawn);
                    if (lv) {
                        p.dead = false; p.pawn = pawn;
                        long inv = Server.rd(m, pawn + 0x838);
                        long data = inv >= 0x10000 ? Server.rd(m, inv + 0x4a8) : 0, num = inv >= 0x10000 ? Server.rd(m, inv + 0x4ac) : 0;
                        for (int i = 0; i < num && i < 300 && data >= 0x10000; i++) {
                            long it = Server.rd(m, data + 4L * i); if (it < 0x10000) continue;
                            long g = (Server.rd(m, it + 0x2cc) * 10000L + Server.rd(m, it + 0x2d0)) / 100000000L;
                            if (g == 201 || g == 202) p.oldGuns.add(it);
                        }
                        p.oldGunsPawn = pawn;
                        float hp = Mods.f(m, pawn + 0x5a0), mx = Mods.f(m, pawn + 0x5a4);
                        if (mx > 1f && mx < 1000f) Mods.wf(m, pawn + 0x5a0, mx);
                        if (hp <= 0) lv = false;
                    }
                }
                if (lv) {
                    alive++;
                    if (p.home != null) try { Veh.teleport(pawn, p.home[0], p.home[1], p.home[2] + 50, 0); } catch (Throwable ignored) {}
                    protect(b, p, pawn);
                    if (!p.oldGuns.isEmpty()) LogCap.note("单挑：玩家 " + (p.idx + 1) + " 身上还有上一把的枪 " + p.oldGuns.size() + " 把（没有安全的删枪方法，留着；这把选枪只认新捡的）");
                } else { dead++; p.dead = true; p.deadAt = now - 5000; }   // 死着的：下一次循环立刻 RestartPlayer
            }
            sb.append("活着传回 ").append(alive).append(" 人、待复活 ").append(dead).append(" 人");
        } catch (Throwable t) { sb.append("出错 ").append(t); }
        finally {
            lastReset = "第 " + r + " 把，" + why + "：" + sb + "，用时 " + (System.currentTimeMillis() - t0) / 1000 + " 秒";
            LogCap.note("单挑：再开一把 " + lastReset);
            GameOverlay.toastAny("单挑：第 " + r + " 把，比分清零，两人到齐重新选枪");
            resetting = false;
        }
    }
    /** 背包列表里 204（投掷物）/206（子弹）全部按数量丢掉 */
    static int discardStack(long b, long pawn) throws Exception {
        java.util.ArrayList<long[]> its = new java.util.ArrayList<>(); long inv;
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
            inv = Server.rd(m, pawn + 0x838); if (inv < 0x10000) return 0;
            long data = Server.rd(m, inv + 0x4a8), num = Server.rd(m, inv + 0x4ac);
            for (int k = 0; k < Math.min(num, 300) && data >= 0x10000; k++) {
                long it = Server.rd(m, data + 4L * k); if (it < 0x10000) continue;
                long g = (Server.rd(m, it + 0x2cc) * 10000L + Server.rd(m, it + 0x2d0)) / 100000000L;
                if (g == 204 || g == 206) its.add(new long[] { it, Server.rd(m, it + 0x2b4) });
            }
        }
        int n = 0;
        for (long[] it : its) {
            long cnt = it[1]; if (cnt <= 0 || cnt > 100000) cnt = 1;
            long[] r = Veh.ucall5(inv, it[0], 0, 0, null, b + Box.DISCARD, cnt, 1, 0);
            if (r[0] == 0x5b0e) n++;
        }
        return n;
    }
    static final String[] PICK_IN = { "pickup", "deathdrop", "dropbox" };
    static final String[] PICK_OUT = { "component", "manager", "config", "spawner", "generator", "point", "airsupply", "weap", "character", "controller", "vehicle", "widget", "data" };
    /** 场地里（围墙矩形外扩 15 米；没墙就两个点 60 米内）所有地上拾取物/死亡盒 → K2_DestroyActor（游戏线程） */
    static String sweep(long b, Walls.Arena A, java.util.List<float[]> homes, int myGen) {
        java.util.ArrayList<Long> kill = new java.util.ArrayList<>();
        java.util.TreeMap<String, Integer> tally = new java.util.TreeMap<>();
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
            Veh.Names nm = Veh.Names.get();
            long g = Server.rd(m, b + 0x51250acL); long objs = Server.rd(m, g + 0x10), n = Server.rd(m, g + 0x18);
            if (objs < 0x10000 || n <= 0 || n > 0x200000) return "清地上东西：对象表不对 " + n;
            java.util.HashMap<Long, Boolean> clsOk = new java.util.HashMap<>(); java.util.HashMap<Long, String> clsNm = new java.util.HashMap<>();
            byte[] blk = new byte[16 * 4096];
            for (long i = 0; i < n; i += 4096) {
                int k = (int) Math.min(4096, n - i);
                m.seek(objs + i * 16); m.readFully(blk, 0, k * 16);
                ByteBuffer bb = ByteBuffer.wrap(blk).order(ByteOrder.LITTLE_ENDIAN);
                for (int j = 0; j < k; j++) {
                    long o = bb.getInt(j * 16) & 0xffffffffL; int fl = bb.getInt(j * 16 + 4);
                    if (o < 0x10000 || (fl & 0x20000000) != 0) continue;
                    long cls; try { cls = Server.rd(m, o + 0xc); } catch (Throwable t) { continue; }
                    if (cls < 0x10000) continue;
                    Boolean okc = clsOk.get(cls);
                    if (okc == null) {
                        String cn = Alive.objName(m, nm, cls); String l = cn.toLowerCase();
                        okc = Walls.has(l, PICK_IN) && !Walls.has(l, PICK_OUT);
                        clsOk.put(cls, okc); clsNm.put(cls, cn);
                    }
                    if (!okc) continue;
                    try { if ((Server.rd(m, o + 4) & 0x10) != 0) continue; } catch (Throwable t) { continue; }   // 类默认对象
                    if (!Mods.isObj(m, b, o)) continue;
                    long root = Server.rd(m, o + 0x128);
                    if (root < 0x10000 || !Alive.clsName(m, nm, root).contains("Component")) continue;
                    float[] q = pos(m, o); if (q == null) continue;
                    boolean in = false;
                    if (A != null) in = A.inside(q[0], q[1], 1500f);
                    else for (float[] h : homes) if (Math.hypot(q[0] - h[0], q[1] - h[1]) < 6000) in = true;
                    if (!in) continue;
                    kill.add(o); tally.merge(clsNm.get(cls), 1, Integer::sum);
                    if (kill.size() >= 400) break;
                }
                if (kill.size() >= 400) break;
            }
        } catch (Throwable e) { return "清地上东西：扫描出错 " + e; }
        int done = 0, gone = 0; String vt = "";
        for (long o : kill) {
            if (myGen != gen) break;
            try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
                if (!Prop.alive(m, b, o)) { gone++; continue; }
                if (vt.isEmpty()) vt = Long.toHexString(Server.rd(m, Server.rd(m, o) + DESTROY) - b);
            } catch (Throwable t) { gone++; continue; }
            try { long[] r = Veh.ucall(o, 0, DESTROY, 0, null); if (r[0] == 0x5b0e) done++; } catch (Throwable ignored) {}
        }
        return "清地上东西：找到 " + kill.size() + (kill.size() >= 400 ? "（到上限 400）" : "") + " 个，删了 " + done + "，已经没了 " + gone + "（虚表+0x164=" + vt + "）" + tally;
    }

    /** v3.19：枪摆成 8 列的小方阵，在出生点朝对手方向 2.5~5.5 米处（围墙场内离出生点 7 米内不放墙，枪不会被墙压住） */
    static void gunRow(float[] at, float[] toward) {
        try {
            double ux = 1, uy = 0;
            if (toward != null) { double dx = toward[0] - at[0], dy = toward[1] - at[1], d = Math.hypot(dx, dy); if (d > 1) { ux = dx / d; uy = dy / d; } }
            double nx = -uy, ny = ux;
            int k = 0;
            for (int c = 0; c < 6; c++) for (int j = 1; j < GameOverlay.CATS[c].length; j++) {
                String[] t = GameOverlay.CATS[c][j].split(":");
                int row = k / 8, col = k % 8;
                double u = 250 + 100 * row, v = (col - 3.5) * 100;
                Veh.dropAt(Long.parseLong(t[1]), (float) (at[0] + ux * u + nx * v), (float) (at[1] + uy * u + ny * v), at[2] - 80f);
                k++; Thread.sleep(80);
            }
            LogCap.note("单挑：摆了 " + k + " 把枪给选（出生点前 2.5 米起，8 列）");
        } catch (Throwable t) { LogCap.note("单挑：摆枪出错 " + t); }
    }
    static long jetId() {
        for (java.util.Map.Entry<Long, String> e : GameOverlay.itemName.entrySet()) { String s = e.getValue(); if (s != null && (s.contains("喷气") || s.contains("Jetpack"))) return e.getKey(); }
        return 0;
    }
    /** 发装备进背包：同款枪 guns 把、300 发、配件、3 级头甲、喷气背包 */
    static void kit(long b, P p, int guns) {
        try {
            int ok = 0, all = 0;
            for (int i = 0; i < guns; i++) { all++; if (HostMatch.giveTo(b, p.ctl, p.gun, 1)) ok++; }
            if (!"0".equals(p.ammo)) { all++; if (HostMatch.giveTo(b, p.ctl, Long.parseLong("2060000000" + p.ammo), 300)) ok++; }
            for (String a : GameOverlay.attFor(Long.toString(p.gun))) { all++; if (HostMatch.giveTo(b, p.ctl, Long.parseLong(a), 1)) ok++; }
            all++; if (HostMatch.giveTo(b, p.ctl, Long.parseLong(GameOverlay.helmet3()), 1)) ok++;
            all++; if (HostMatch.giveTo(b, p.ctl, Long.parseLong(GameOverlay.armor3()), 1)) ok++;
            long jet = jetId(); if (jet != 0) { all++; if (HostMatch.giveTo(b, p.ctl, jet, 1)) ok++; }
            p.kitted = true;
            LogCap.note("单挑：给玩家 " + (p.idx + 1) + " 发装备 " + ok + "/" + all + "（" + p.gunName + "×" + guns + "，喷气背包 " + (jet == 0 ? "没找到编号" : jet) + "）");
        } catch (Throwable t) { LogCap.note("单挑：发装备出错 " + t); }
    }
}
