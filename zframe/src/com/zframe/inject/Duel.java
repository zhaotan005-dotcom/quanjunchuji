package com.zframe.inject;

import java.io.RandomAccessFile;

/** v3.16 用户：两个人单挑，20 个头定胜负；雨林、海岛各一个；开局前自由选枪；被打死 5 秒复活、复活后 3 秒无敌；
 *  两把一样的枪 + 300 发子弹，配件放背包，3 级头 3 级甲，背包用喷气背包。
 *  做法（服务端，Lan.role==1）：
 *   - 开房：不上飞机不刷毒（Mods.setNoZone），关物资箱（Box.on=false），在出生岛打。海岛 = 战术模式蓝图，雨林 = 默认模式。
 *   - 选枪：进图后在每个加入玩家面前摆一排全部枪（Veh.dropAt），捡起一把就算选好（读背包 +0x4a8 列表里 201/202 开头的编号；
 *     读不到 45 秒后默认 M416）。选好后记下他现在的位置当出生点，开始发装备。
 *   - 发装备：HostMatch.giveTo（刷物品补丁，进背包）：同款枪补到 2 把、子弹 300、这把枪能装的全部配件、3 级头、3 级甲、喷气背包。
 *   - 死亡：控制器 +0x2c0 人物没了或血 +0x5a0 <= 0 → 对面 +1 头；5 秒后在游戏线程调 GameMode 虚表 +0x39c（RestartPlayer，
 *     NZ 版本 0x10127a4 / 0x1079fa8，游戏自己复活用的那条）→ 新人物传回他的出生点、重新发装备、3 秒内血掉了就补满。
 *   - 谁先 20 个头谁赢，之后不再复活。 */
public class Duel {
    static volatile boolean on;
    static volatile String state = "单挑：没开";
    static final int WIN = 20;
    static final String RENEW = "/Game/Maps/ErangeMobileRenew_Runtime/BR-ErangelRenew_Mobile_Main";
    static final String SAVAGE = "/Game/Maps/SavageMobile_4x4_Runtime/BR-Savage_Mobile_Main";

    static class P {
        long ctl, pawn; int kills; long gun; String gunName = "", ammo = "1";
        float[] home; long rowAt, deadAt, protectUntil; boolean dead, kitted, picked;
    }
    static final java.util.LinkedHashMap<Long, P> ps = new java.util.LinkedHashMap<>();
    static volatile boolean over; static volatile String winner = "";
    static Thread th;

    static String open(boolean savage) {
        Training.off();
        on = true; reset();
        Mods.setNoZone();
        Box.on = false;
        state = "单挑：开" + (savage ? "雨林" : "海岛");
        start();
        if (savage) return Lan.host(SAVAGE);
        return ModeProbe.host("Props", "单挑", RENEW);
    }
    static void off() { on = false; }
    static void reset() { synchronized (ps) { ps.clear(); } over = false; winner = ""; }

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

    /** 找加入的玩家（控制器 +0x2fc 是 NetConnection），最多 2 个 */
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
    static String[] gunInfo(long id) {
        String s = Long.toString(id);
        for (String[] c : GameOverlay.CATS) for (int j = 1; j < c.length; j++) { String[] t = c[j].split(":"); if (t.length >= 3 && t[1].equals(s)) return t; }
        return new String[] { s, s, "1" };
    }

    static void tick() throws Exception {
        long b = Mods.base(); if (b == 0) return;
        Veh.Names nm = Veh.Names.get();
        long now = System.currentTimeMillis();
        java.util.ArrayList<long[]> pl;
        java.util.ArrayList<Runnable> todo = new java.util.ArrayList<>();
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "rw")) {
            pl = players(m, nm);
            for (long[] e : pl) {
                final P p; synchronized (ps) { P x = ps.get(e[0]); if (x == null) { x = new P(); x.ctl = e[0]; ps.put(e[0], x); } p = x; }
                final long gm = e[1];
                long pawn = Server.rd(m, p.ctl + 0x2c0);
                boolean alive = live(m, nm, pawn);
                // 3 秒无敌：血掉了就补满
                if (alive && now < p.protectUntil) {
                    float hp = Mods.f(m, pawn + 0x5a0), mx = Mods.f(m, pawn + 0x5a4);
                    if (mx > 1f && mx < 1000f && hp < mx) Mods.wf(m, pawn + 0x5a0, mx);
                }
                if (!p.picked) {
                    if (!alive) continue;
                    p.pawn = pawn;
                    if (p.rowAt == 0) { final float[] at = pos(m, pawn); if (at == null) continue; p.rowAt = now; p.home = at; todo.add(() -> gunRow(at)); continue; }
                    long g = gunIn(m, pawn);
                    if (g == 0 && now - p.rowAt < 45000) continue;
                    p.gun = g != 0 ? g : 20101000001L;
                    String[] gi = gunInfo(p.gun); p.gunName = gi[0]; p.ammo = gi[2];
                    p.picked = true; float[] h = pos(m, pawn); if (h != null) p.home = h;
                    final boolean had = g != 0;
                    LogCap.note("单挑：" + Long.toHexString(p.ctl) + " 选了 " + p.gunName + (had ? "" : "（45 秒没捡，默认）"));
                    todo.add(() -> kit(b, p, had ? 1 : 2));
                    continue;
                }
                if (alive && pawn != p.pawn && !p.dead) { p.pawn = pawn; }
                if (!alive && !p.dead) {
                    p.dead = true; p.deadAt = now;
                    for (P o : snapshot()) if (o != p) { o.kills++; if (o.kills >= WIN && !over) { over = true; winner = Long.toHexString(o.ctl) + "（" + o.gunName + "）"; } }
                    LogCap.note("单挑：" + Long.toHexString(p.ctl) + " 死了，比分 " + score());
                }
                if (p.dead && !over && now - p.deadAt >= 5000) {
                    if (alive && pawn != p.pawn) {   // 复活了（游戏自己的或我们调的）
                        p.dead = false; p.pawn = pawn; p.protectUntil = now + 3000;
                        final float[] h = p.home;
                        todo.add(() -> { try { if (h != null) Veh.teleport(p.pawn, h[0], h[1], h[2] + 50, 0); } catch (Throwable ignored) {} kit(b, p, 2); p.protectUntil = System.currentTimeMillis() + 3000; });
                        LogCap.note("单挑：" + Long.toHexString(p.ctl) + " 复活 新人物 " + Long.toHexString(pawn));
                    } else if (now - p.deadAt >= 5000 && (p.deadAt % 1 == 0) && now - p.rowAt > 2500) {
                        p.rowAt = now;   // 2.5 秒试一次
                        todo.add(() -> respawn(gm, p));
                    }
                }
            }
        }
        for (Runnable r : todo) r.run();
        state = over ? "单挑：结束，赢家 " + winner + "，比分 " + score()
                     : "单挑：" + pl.size() + " 人，比分 " + score() + "（" + WIN + " 个头赢）";
    }
    static java.util.ArrayList<P> snapshot() { synchronized (ps) { return new java.util.ArrayList<>(ps.values()); } }
    static String score() {
        StringBuilder s = new StringBuilder();
        for (P p : snapshot()) s.append(s.length() == 0 ? "" : " : ").append(p.picked ? p.gunName : "选枪中").append(" ").append(p.kills);
        return s.length() == 0 ? "等人加入" : s.toString();
    }
    static volatile String respawnState = "";
    static void respawn(long gm, P p) {
        try {
            if (gm < 0x10000) { respawnState = "复活：没有 GameMode"; return; }
            long[] r = Veh.ucall(gm, p.ctl, 0x39c, 0, null);   // GameMode->RestartPlayer(控制器)
            respawnState = "复活：调 RestartPlayer 标记 " + Long.toHexString(r[0]);
            LogCap.note("单挑：" + Long.toHexString(p.ctl) + " " + respawnState);
        } catch (Throwable t) { respawnState = "复活出错 " + t; LogCap.note(respawnState); }
    }
    /** 面前 3 米起摆一排全部枪（步枪/机枪/冲锋/喷子/狙/连狙），隔 1.2 米 */
    static void gunRow(float[] at) {
        try {
            int k = 0;
            for (int c = 0; c < 6; c++) for (int j = 1; j < GameOverlay.CATS[c].length; j++) {
                String[] t = GameOverlay.CATS[c][j].split(":");
                Veh.dropAt(Long.parseLong(t[1]), at[0] + 300f, at[1] - 1200f + 120f * k, at[2] - 80f);
                k++; Thread.sleep(80);
            }
            LogCap.note("单挑：摆了 " + k + " 把枪给选");
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
            LogCap.note("单挑：给 " + Long.toHexString(p.ctl) + " 发装备 " + ok + "/" + all + "（" + p.gunName + "×" + guns + "，喷气背包 " + (jet == 0 ? "没找到编号" : jet) + "）");
        } catch (Throwable t) { LogCap.note("单挑：发装备出错 " + t); }
    }
}
