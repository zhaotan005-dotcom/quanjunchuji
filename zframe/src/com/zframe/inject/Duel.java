package com.zframe.inject;

import java.io.RandomAccessFile;

/** v3.16 用户：两个人单挑，20 个头定胜负；雨林、海岛各一个；开局前自由选枪；被打死 5 秒复活、复活后 3 秒无敌；
 *  两把一样的枪 + 300 发子弹，配件放背包，3 级头 3 级甲，背包用喷气背包。
 *  v3.17：自定单挑点（prefs duel_e 海岛 / duel_s 雨林，没记过就第一局上飞机跳、落地 2.5 秒记下）、看不见的固定圈。
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
        boolean dead, kitted, picked, placed, tele, present;
    }
    static final java.util.LinkedHashMap<Long, P> ps = new java.util.LinkedHashMap<>();
    static volatile boolean over, started, paused; static volatile String winner = "";
    static Thread th;

    static volatile boolean savage;
    static String key(boolean sv) { return sv ? "duel_s" : "duel_e"; }
    static float[][] saved(boolean sv) {
        try {
            String v = Con.act.getSharedPreferences("zframe", 0).getString(key(sv), null); if (v == null) return null;
            String[] t = v.split(","); if (t.length != 6) return null;
            float[][] r = new float[2][3]; for (int i = 0; i < 6; i++) r[i / 3][i % 3] = Float.parseFloat(t[i]); return r;
        } catch (Throwable e) { return null; }
    }
    static void save(boolean sv, float[] a, float[] c) {
        try { Con.act.getSharedPreferences("zframe", 0).edit().putString(key(sv), a[0] + "," + a[1] + "," + a[2] + "," + c[0] + "," + c[1] + "," + c[2]).apply(); } catch (Throwable ignored) {}
        LogCap.note("单挑：记下" + (sv ? "雨林" : "海岛") + "单挑点 " + java.util.Arrays.toString(a) + " / " + java.util.Arrays.toString(c));
    }
    static void clear(boolean sv) { try { Con.act.getSharedPreferences("zframe", 0).edit().remove(key(sv)).apply(); } catch (Throwable ignored) {} }
    static String pts() {
        return "单挑点：海岛" + (saved(false) == null ? "没记" : "已记") + "，雨林" + (saved(true) == null ? "没记（第一局上飞机自己跳，落地就记）" : "已记");
    }
    /** 开房页按钮：把现在两个加入玩家站的位置记成当前地图的单挑点 */
    static String recordNow() {
        if (!Con.inMatch) return "进对局后再按";
        String mp = Con.brMap == null ? "" : Con.brMap; boolean sv = mp.contains("Savage") || mp.contains("4x4");
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
            Veh.Names nm = Veh.Names.get(); java.util.ArrayList<float[]> l = new java.util.ArrayList<>();
            for (long[] e : players(m, nm)) { long p = Server.rd(m, e[0] + 0x2c0); if (live(m, nm, p)) { float[] q = pos(m, p); if (q != null) l.add(q); } }
            if (l.size() < 2) return "要两个加入的玩家都活着站好（现在 " + l.size() + " 个）";
            save(sv, l.get(0), l.get(1)); arena(l.get(0), l.get(1));
            return "记好了" + (sv ? "雨林" : "海岛") + "的两个单挑点（下一局开始按这两个点刷围墙）";
        } catch (Throwable t) { return "出错 " + t; }
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
        float[][] pt = saved(sv);
        if (pt != null) { Mods.setNoZone(); arena(pt[0], pt[1]); }
        else { Mods.setPlane(false); Mods.setPoison(true); }
        Box.on = false;
        state = "单挑：开" + (savage ? "雨林" : "海岛") + "，等两个人进来";
        start();
        if (sv) return Lan.host(SAVAGE);
        return ModeProbe.host("Props", "单挑", RENEW);
    }
    static void off() { on = false; }
    /** 每局开始（HostMatch.newMatch）和每次开房都调：清掉上一局的人、分数、围墙记录（上一张图的墙随世界一起销毁） */
    static void reset() {
        synchronized (ps) { ps.clear(); }
        over = false; started = false; paused = false; winner = ""; center = null; wallsDone = false; protGs = 0; announced = false;
        Walls.reset();
    }
    static volatile boolean wallsDone, announced; static volatile long protGs;

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
    static long gunIn(RandomAccessFile m, long p) throws Exception {
        long inv = Server.rd(m, p + 0x838); if (inv < 0x10000) return 0;
        long data = Server.rd(m, inv + 0x4a8), num = Server.rd(m, inv + 0x4ac);
        if (data < 0x10000 || num <= 0 || num > 300) return 0;
        for (int i = 0; i < num; i++) {
            long it = Server.rd(m, data + 4L * i); if (it < 0x10000) continue;
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
                        orphan.ctl = e[0]; orphan.present = true; orphan.pawn = 0; orphan.dead = false; orphan.kitted = false; orphan.tryAt = 0; orphan.movedAt = now;
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
                // ---- 出生点：存过的点直接传过去；没存过等落地站稳 ----
                if (!p.placed) {
                    if (!alive) continue;
                    p.pawn = pawn;
                    float[][] pt = saved(savage);
                    float[] q = pos(m, pawn); if (q == null) continue;
                    if (pt != null && p.idx >= 0 && p.idx < 2) {
                        if (now - p.tryAt < 2000) continue; p.tryAt = now;
                        p.home = pt[p.idx]; p.placed = true; p.tele = true; p.movedAt = now;
                        tele(todo, pawn, p.home); LogCap.note("单挑：玩家 " + (p.idx + 1) + " 传到单挑点"); continue;
                    }
                    if (p.last == null || Math.abs(q[0] - p.last[0]) + Math.abs(q[1] - p.last[1]) + Math.abs(q[2] - p.last[2]) > 60f) { p.last = q; p.movedAt = now; continue; }
                    if (Bots.readPhase(gm) < 4 || now - p.movedAt < 2500) continue;
                    p.home = q; p.placed = true;
                    LogCap.note("单挑：玩家 " + (p.idx + 1) + " 落地 " + java.util.Arrays.toString(q));
                    if (all.size() >= 2 && all.get(0).placed && all.get(1).placed && saved(savage) == null) { save(savage, all.get(0).home, all.get(1).home); arena(all.get(0).home, all.get(1).home); }
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
                    long g = gunIn(m, pawn);
                    if (g == 0) { long h = gunInHand(m, b, nm, pawn); if (h > 0) g = h; else if (h < 0 && now - p.rowAt > 8000) LogCap.note("单挑：手上的枪类名认不出 " + lastWeapCls); }
                    if (g == 0 && now - p.rowAt < 45000) continue;
                    p.gun = g != 0 ? g : 20101000001L;
                    String[] gi = gunInfo(p.gun); p.gunName = gi[0]; p.ammo = gi[2];
                    p.picked = true;
                    final boolean had = g != 0;
                    LogCap.note("单挑：玩家 " + (p.idx + 1) + " 选了 " + p.gunName + (had ? "" : "（45 秒没捡，默认）"));
                    todo.add(() -> kit(b, p, had ? 1 : 2));
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
                            LogCap.note("单挑：结束，" + winner + "，比分 " + score()); GameOverlay.toastAny("单挑结束：" + winner + " " + score());
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
        }
        // ---- 围墙：两个点都知道就刷（等人的时候就可以刷，后台线程） ----
        if (!wallsDone) {
            float[][] pt = saved(savage);
            java.util.ArrayList<P> all = snapshot();
            float[] a = null, c = null;
            if (pt != null) { a = pt[0]; c = pt[1]; }
            else if (all.size() >= 2 && all.get(0).placed && all.get(1).placed) { a = all.get(0).home; c = all.get(1).home; }
            if (a != null && !pl.isEmpty()) {
                wallsDone = true; final float[] fa = a, fc = c;
                final java.util.ArrayList<float[]> avoid = new java.util.ArrayList<>();
                try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) { for (P p : all) { long pw = Server.rd(m, p.ctl + 0x2c0); if (pw >= 0x10000) avoid.add(pos(m, pw)); } } catch (Throwable ignored) {}
                new Thread(() -> { Walls.build(fa, fc, avoid); android.app.Activity ac = Con.act; if (ac != null) AvatarLog.export(ac, "duel-"); }, "zf-walls").start();
            }
        }
        for (Runnable r : todo) r.run();
        String wait = !started ? "等待对手加入（" + liveN + "/2）" : paused ? "暂停：等对手回来（" + liveN + "/2）" : "";
        if (!started && !announced && liveN == 1) { announced = true; LogCap.note("单挑：等待对手加入 (1/2)"); GameOverlay.toastAny("单挑：等待对手加入 (1/2)"); }
        state = over ? "单挑：结束，" + winner + "，比分 " + score()
                     : "单挑：" + (wait.isEmpty() ? "进行中" : wait) + "，比分 " + score() + "（" + WIN + " 个头赢）；" + Walls.state;
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
        final long pw = pawn;
        new Thread(() -> {
            int ok = 0;
            try {
                for (int i = 0; i < 12; i++) { long[] r = Veh.ucall(pw, (i % 2 == 0) ? 1 : 0, 0x160, 0, null); if (r[0] == 0x5b0e) ok++; Thread.sleep(250); }
            } catch (Throwable ignored) {}
            finally { try { Veh.ucall(pw, 0, 0x160, 0, null); } catch (Throwable ignored) {} }
            LogCap.note("单挑：保护闪烁 " + ok + "/12 次（SetActorHiddenInGame），结束已显示");
        }, "zf-duel-blink").start();
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
