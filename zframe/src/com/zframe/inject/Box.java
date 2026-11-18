package com.zframe.inject;

import java.io.RandomAccessFile;

/** v3.05 用户：出生岛放「空投」——每类东西一堆，堆在一起游戏自己会显示成箱子（那个灰色方块就是一堆拾取物合成的箱子，模型 P40300000001_DeathDropBox）。
 *  服务端（房主）每局一次，用房主自己那个隐藏的人物当「搬运工」：
 *   1. K2_TeleportTo 把房主人物挪到第 i 堆的位置（以房主出生的位置为起点，一排，每堆隔 4 米）；
 *   2. 能叠的东西（战术道具/药品/投掷物/子弹/配件/油桶）：刷物品补丁 0x17169c0 塞进房主背包（每次最多 999），
 *      再调游戏自己的丢弃 ServerDiscardItem 实现 0xcd6728(背包, 物品, 数量, 不查容量=1, 不校验=1)，丢在房主脚下；
 *      背包物品列表 背包+0x4a8/+0x4ac，道具槽 +0x490..+0x498，物品数量 +0x2b4，编号 = +0x2cc*10000 + +0x2d0（反汇编 0xcd6798 读法）；
 *   3. 不能叠的（枪、头甲包）：SpawnOnePickUp（cave 'G'，Veh.dropAt）每件刷一个在那一堆的位置。
 *      不往房主身上塞头甲包和枪：身上已有再塞会换装、走崩溃路（v2.02 记录）。
 *  丢弃调用要 5 个参数，ucave 改过（v3.05：buf+36/64/68 → r2/r3/栈）。 */
final class Box {
    static final long DISCARD = 0xcd6728L;
    static volatile String state = "空投：还没开局";
    static volatile boolean done, busy;
    static volatile boolean on = true;
    static volatile int tries;
    static void newMatch() { done = false; tries = 0; }

    static final class Pile { String name; java.util.ArrayList<Long> stack = new java.util.ArrayList<>(); int n; java.util.ArrayList<Long> single = new java.util.ArrayList<>(); }

    static java.util.ArrayList<Long> ids(java.util.function.LongPredicate f) {
        java.util.ArrayList<Long> l = new java.util.ArrayList<>();
        java.util.TreeMap<Long, java.util.ArrayList<Long>> m = GameOverlay.items; if (m == null) return l;
        for (java.util.Map.Entry<Long, java.util.ArrayList<Long>> e : m.entrySet()) for (long v : e.getValue()) if (!GameOverlay.itemBad.contains(v) && f.test(v)) l.add(v);
        return l;
    }
    static long grp(long id) { return id / 1000000L; }   // 20101000001 → 20101
    static java.util.ArrayList<Long> att(String[]... gs) { java.util.ArrayList<Long> l = new java.util.ArrayList<>(); for (String[] g : gs) for (String x : g) { long v = Long.parseLong(x); if (!l.contains(v)) l.add(v); } return l; }
    static String nm(long id) { String s = GameOverlay.itemName.get(id); return s == null ? "" : s; }

    static java.util.ArrayList<Pile> plan() {
        java.util.ArrayList<Pile> ps = new java.util.ArrayList<>();
        Pile p;
        p = new Pile(); p.name = "枪械"; p.single = ids(v -> { long g = grp(v); return g / 100 == 201 || g / 100 == 202; }); ps.add(p);
        p = new Pile(); p.name = "特殊道具"; p.stack = ids(v -> grp(v) / 100 == 402); p.n = 999; ps.add(p);
        p = new Pile(); p.name = "子弹"; p.stack = ids(v -> grp(v) == 20600); p.n = 9999; ps.add(p);
        p = new Pile(); p.name = "投掷物"; p.stack = ids(v -> grp(v) == 20401); p.n = 999; ps.add(p);
        p = new Pile(); p.name = "油桶"; p.stack = ids(v -> { String s = nm(v); return grp(v) / 100 == 402 && (s.contains("油桶") || s.contains("Barrel") || s.contains("Fuel") || s.contains("Oil")); }); p.n = 999; ps.add(p);
        String[] SC = {"20503000001", "20503000002", "20503000003", "20503000004", "20503000005", "20503000006", "20503000007", "20503000008", "20503000009"};
        String[] GRIP = {"20502000001", "20502000002", "20502000003", "20502000004", "20502000005"};
        p = new Pile(); p.name = "步枪配件"; p.stack = att(new String[]{"20501000001", "20501000002", "20501000003"}, GRIP, SC, new String[]{"20504000001", "20504000002", "20504000003", "20505000001", "20505000003"}); p.n = 999; ps.add(p);
        p = new Pile(); p.name = "冲锋枪配件"; p.stack = att(new String[]{"20501000004", "20501000005", "20501000006"}, GRIP, SC, new String[]{"20504000004", "20504000005", "20504000006", "20505000001", "20505000002"}); p.n = 999; ps.add(p);
        p = new Pile(); p.name = "药品"; p.stack = ids(v -> grp(v) / 100 == 401); p.n = 999; ps.add(p);
        p = new Pile(); p.name = "头甲包"; p.single = ids(v -> { long g = grp(v); String s = nm(v); return g == 30304 || g == 30305 || g == 30307 || s.contains("喷气") || s.contains("Jetpack"); }); ps.add(p);
        return ps;
    }

    /** HostMatch 每 3 秒调：服务端在对局里、房主人物在、这局还没放过 */
    static void tick() {
        if (!on || done || busy || Lan.role != 1 || !Con.inMatch) return;
        if (GameOverlay.items == null) { GameOverlay.loadItems(false); state = "空投：在读物品表"; return; }
        if (++tries > 30) { done = true; state = state + "（试了 30 次没放成，这局不放了）"; LogCap.note(state); return; }
        busy = true;
        new Thread(() -> { try { run(); } catch (Throwable t) { state = "空投：出错 " + t; LogCap.note(state); } finally { busy = false; } }, "zf-box").start();
    }

    static void run() throws Exception {
        long b = Mods.base(); if (b == 0) return;
        long pawn = Mods.myPawn(); if (pawn < 0x10000) { state = "空投：房主人物还没出来"; return; }
        long ctl = 0, inv; float x0, y0, z0;
        Veh.Names nm = Veh.Names.get();
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
            if (!Mods.isObj(m, b, pawn)) return;
            long root = Server.rd(m, pawn + 0x128); if (root < 0x10000) return;
            x0 = Mods.f(m, root + 0x140); y0 = Mods.f(m, root + 0x144); z0 = Mods.f(m, root + 0x148);
            inv = Server.rd(m, pawn + 0x838); if (inv < 0x10000) { state = "空投：房主人物没有背包组件"; return; }
            double[] q = Veh.info(); long gm = q != null ? (long) q[4] : 0;
            long gs = gm >= 0x10000 ? Server.rd(m, gm + 0x2e0) : 0;
            long data = gs >= 0x10000 ? Server.rd(m, gs + 0x2bc) : 0;
            int n = gs >= 0x10000 ? (int) Server.rd(m, gs + 0x2c0) : 0;
            for (int k = 0; k < n && k < 200 && data >= 0x10000; k++) {
                long ps = Server.rd(m, data + 4L * k); if (ps < 0x10000) continue;
                long c = Server.rd(m, ps + 0x60); if (c < 0x10000) continue;
                if (Server.rd(m, c + 0x2c0) == pawn) { ctl = c; break; }
            }
        }
        if (ctl == 0) { state = "空投：没找到房主自己的控制器"; return; }
        if (Math.abs(x0) < 1 && Math.abs(y0) < 1) { state = "空投：房主位置读不到"; return; }
        done = true;
        java.util.ArrayList<Pile> ps = plan();
        StringBuilder sb = new StringBuilder("空投（出生岛 " + Math.round(x0 / 100) + "," + Math.round(y0 / 100) + "）：");
        for (int i = 0; i < ps.size(); i++) {
            Pile p = ps.get(i);
            // 两排：前 5 堆一排，后面的往后挪 5 米；堆和堆之间 4 米
            float px = x0 + 400f * (i % 5) + 300f, py = y0 + (i < 5 ? 300f : 800f), pz = z0;
            if (p.stack.isEmpty() && p.single.isEmpty()) { sb.append("\n· ").append(p.name).append("：物品表里没有"); continue; }
            int got = 0, drop = 0, one = 0;
            if (!p.stack.isEmpty()) {
                Veh.teleport(pawn, px, py, pz, 0);
                Thread.sleep(150);
                for (long id : p.stack) {
                    int left = p.n;
                    while (left > 0) { int c = Math.min(999, left); if (HostMatch.giveTo(b, ctl, id, c)) got++; left -= c; }
                }
                drop = dropAll(b, inv);
            }
            for (long id : p.single) if (Veh.dropAt(id, px + (one % 6) * 40f - 100f, py + (one / 6) * 40f - 100f, pz) == null) one++;
            sb.append("\n· ").append(p.name).append("：").append(p.stack.size() + p.single.size()).append(" 种")
              .append(p.stack.isEmpty() ? "" : "，每种 " + p.n + "，塞进 " + got + " 次、丢出 " + drop + " 堆")
              .append(p.single.isEmpty() ? "" : "，地上刷 " + one + " 件");
            state = sb.toString();
        }
        Veh.teleport(pawn, x0, y0, z0 + 50f, 0);
        state = sb.toString(); LogCap.note(state);
        Lan.autoUp("box");
        GameOverlay.toastAny("出生岛空投已放好（" + ps.size() + " 堆）");
    }

    /** 把房主背包（物品列表 + 3 个道具槽）里的东西全部按数量丢出来 */
    static int dropAll(long b, long inv) throws Exception {
        int n = 0;
        for (int round = 0; round < 6; round++) {
            java.util.ArrayList<long[]> its = new java.util.ArrayList<>();
            try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
                java.util.HashSet<Long> seen = new java.util.HashSet<>();
                for (int k = 0; k < 3; k++) { long p = Server.rd(m, inv + 0x490 + 4 * k); if (p >= 0x10000 && seen.add(p)) its.add(new long[]{p, Server.rd(m, p + 0x2b4)}); }
                long data = Server.rd(m, inv + 0x4a8), num = Server.rd(m, inv + 0x4ac);
                for (int k = 0; k < Math.min(num, 300) && data >= 0x10000; k++) { long p = Server.rd(m, data + 4L * k); if (p >= 0x10000 && seen.add(p)) its.add(new long[]{p, Server.rd(m, p + 0x2b4)}); }
            }
            if (its.isEmpty()) break;
            for (long[] it : its) {
                long cnt = it[1]; if (cnt <= 0 || cnt > 100000) cnt = 1;
                long[] r = Veh.ucall5(inv, it[0], 0, 0, null, b + DISCARD, cnt, 1, 0);
                if (r[0] == 0x5b0e) n++;
            }
        }
        return n;
    }
}
