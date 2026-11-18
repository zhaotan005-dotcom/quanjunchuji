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
        state = sb.toString();
        try { sb.append("\n").append(airdrop(b, x0, y0, z0, ps)); } catch (Throwable t) { sb.append("\n空投箱：出错 ").append(t); }
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
    /* ---------------- v3.07 真空投箱 ----------------
     * 用户：没看到空投（v3.05/3.06 只是地上堆）。改成游戏自己的空投箱 ANZBRAirSupplyBox：
     *  DropAirSupplyBox 0x118a820(空投系统, x, y, z, 箱型序号, 字节)：空投系统 AAirSupplySystem（虚表 0x4caf198，每局一个）；
     *   箱型序号 0..3 → 配置 +0x32c / +0x334 / +0x338 / +0x330 的箱子类；
     *   生成后 0xfd3274 初始化 → 0xfd3424 bl 0xfd3ac0 按空投物品池填 PickupItemIDList(箱+0x3f8，元素 8 字节 = FName 物品行名) → 0x1112cb0 登记。
     *  内容：0xfd3424 改 bl cave（game/re/boxcave.py，放在被剥掉的作弊 RPC thunk 0x11de78c..0x11de844 上，emu_boxcave 过）：
     *   我们的缓冲区 n>0 时用我们的 FName 列表代替随机物资，n=0 照原样。
     *  FName 格式不知道 → 先投一个「探路箱」（游戏自己的随机物资），读它的列表认出行名格式（含 11 位编号的那种），再在名字表里找我们每件东西的序号。
     *  箱子里每样东西是一份（数量是游戏默认的，列表里没有数量字段），999 的那些还在旁边地上那堆里。 */
    static final long DROP = 0x118a820L, SYS_VT = 0x4caf198L, CAVE_AT = 0x11de78cL, HOOK_AT = 0xfd3424L;
    static final String CAVE = "f0412de90040a0e10180a0e194509fe5006095e5000056e31e00000ac80056e3c860a0c30070a0e3060057e1130000aafc1394e5010081e2fc0384e5000494e5000051e1030000bafe0f84e206002de9679be7eb0600bde8f80394e5810180e0082085e2872182e0003092e5003080e5043092e5043080e5017087e2e9ffffea0000a0e3000085e5040095e5010080e2040085e5f081bde80400a0e10810a0e1f041bde8a2d4f7ea";
    static final String HOOK_O = "a50100eb", HOOK_N = "d82c08eb";
    static final String CAVE_O64 = "104c2de908b08de20040a0e1100091e5000050e301008012100081e5000094e5f81c90e50400a0e131ff2fe1000050e30400000a000094e5fc1c90e50400a0e1";
    static java.nio.ByteBuffer cbuf; static long caddr;
    static String installCave(long b) throws Exception {
        if (cbuf == null) { cbuf = java.nio.ByteBuffer.allocateDirect(8 + 8 * 200).order(java.nio.ByteOrder.LITTLE_ENDIAN); caddr = GameOverlay.bufAddr(cbuf); }
        if (caddr <= 0 || caddr > 0xffffffffL) return "缓冲区地址不对";
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "rw")) {
            String h = Server.bytesAt(m, b + HOOK_AT, 4);
            if (!h.equals(HOOK_O) && !h.equals(HOOK_N)) return "钩子处字节不对 " + h;
            String c = Server.bytesAt(m, b + CAVE_AT, 64);
            if (!c.equals(CAVE_O64) && !c.equals(CAVE.substring(0, 128))) return "cave 处字节不对 " + c.substring(0, 16);
            cbuf.putInt(0, 0);
            m.seek(b + CAVE_AT); m.write(Patches.hex(CAVE + Server.w32((int) caddr)));
            m.seek(b + HOOK_AT); m.write(Patches.hex(HOOK_N));
            if (!Server.bytesAt(m, b + HOOK_AT, 4).equals(HOOK_N)) return "钩子没写进去";
        }
        return "ok";
    }
    /** 活着的、类名含 key 的对象（跳过类默认对象） */
    static java.util.ArrayList<Long> byCls(RandomAccessFile m, long b, Veh.Names nm, String key) throws java.io.IOException {
        java.util.ArrayList<Long> out = new java.util.ArrayList<>();
        java.util.HashMap<Long, Boolean> ok = new java.util.HashMap<>();
        long g = Server.rd(m, b + 0x51250acL); if (g < 0x10000) return out;
        long objs = Server.rd(m, g + 0x10), num = Server.rd(m, g + 0x18);
        if (objs < 0x10000 || num <= 0 || num > 0x200000) return out;
        byte[] blk = new byte[16 * 4096];
        for (long i = 0; i < num; i += 4096) {
            int k = (int) Math.min(4096, num - i);
            m.seek(objs + i * 16); m.readFully(blk, 0, k * 16);
            java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(blk).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            for (int j = 0; j < k; j++) {
                long o = bb.getInt(j * 16) & 0xffffffffL; int f = bb.getInt(j * 16 + 4);
                if (o < 0x10000 || (f & 0x20000000) != 0) continue;
                long c = Server.rd(m, o + 0xc); if (c < 0x10000) continue;
                Boolean y = ok.get(c);
                if (y == null) { y = Alive.objName(m, nm, c).contains(key); ok.put(c, y); }
                if (y && (Server.rd(m, o + 4) & 0x10) == 0) out.add(o);
            }
        }
        return out;
    }
    static String nameOf(RandomAccessFile m, Veh.Names nm, long idx) { String s = idx >= 0 && idx < 0x400000 ? nm.name(m, (int) idx) : null; return s == null ? "#" + idx : s; }
    static long drop(long b, long sys, float x, float y, float z, int type) throws InterruptedException {
        long[] r = Veh.ucall6(sys, Float.floatToRawIntBits(x) & 0xffffffffL, 0, 0, null, b + DROP,
            Float.floatToRawIntBits(y) & 0xffffffffL, Float.floatToRawIntBits(z) & 0xffffffffL, type, 0);
        return r[0];
    }
    static String airdrop(long b, float x0, float y0, float z0, java.util.ArrayList<Pile> ps) throws Exception {
        StringBuilder sb = new StringBuilder("空投箱：");
        String ic = installCave(b);
        sb.append("内容补丁 ").append(ic);
        Veh.Names nm = Veh.Names.get(); if (nm == null) return sb.append("；名字表没找到").toString();
        long sys = 0; int type = -1;
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
            java.util.ArrayList<Long> ss = Flare.byVt(m, b, b + SYS_VT);
            if (ss.isEmpty()) return sb.append("；这局没有空投系统").toString();
            sys = ss.get(0);
            int[] offs = { 0x32c, 0x334, 0x338, 0x330 };
            sb.append("；空投系统 ").append(Long.toHexString(sys)).append(" 箱子类");
            for (int t : new int[] { 0, 3, 1, 2 }) {
                long c = Server.rd(m, sys + offs[t]);
                sb.append(" ").append(t).append("=").append(c < 0x10000 ? "空" : Alive.objName(m, nm, c));
                if (type < 0 && c >= 0x10000) type = t;
            }
        }
        if (type < 0) return sb.append("；没有可用的箱子类").toString();
        // 探路箱：游戏自己的随机物资，用来认 FName 格式
        java.util.HashSet<Long> before = new java.util.HashSet<>();
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) { before.addAll(byCls(m, b, nm, "AirSupplyBox")); }
        if (cbuf != null) cbuf.putInt(0, 0);
        long r0 = drop(b, sys, x0 - 600f, y0 + 300f, z0 + 3000f, type);
        Thread.sleep(800);
        String tplA = null, tplB = null; java.util.ArrayList<String> probe = new java.util.ArrayList<>();
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
            java.util.ArrayList<Long> now = byCls(m, b, nm, "AirSupplyBox");
            long box = 0; for (long o : now) if (!before.contains(o)) { box = o; break; }
            sb.append("；探路箱 ").append(r0 == 0x5b0e ? "已调" : "没调成(" + Long.toHexString(r0) + ")").append(box == 0 ? "，没找到新箱子" : "，箱子 " + Long.toHexString(box) + " " + Alive.clsName(m, nm, box));
            if (box != 0) {
                long data = Server.rd(m, box + 0x3f8), n = Server.rd(m, box + 0x3fc);
                for (int i = 0; i < Math.min(n, 40) && data >= 0x10000; i++) probe.add(nameOf(m, nm, Server.rd(m, data + 8L * i)) + (Server.rd(m, data + 8L * i + 4) != 0 ? "_" + Server.rd(m, data + 8L * i + 4) : ""));
                sb.append("，随机物资 ").append(n).append(" 件：").append(String.join(" ", probe));
            }
        }
        java.util.regex.Pattern d11 = java.util.regex.Pattern.compile("(\\d{11})");
        for (String s : probe) { java.util.regex.Matcher mt = d11.matcher(s); if (mt.find()) { tplA = s.substring(0, mt.start()); tplB = s.substring(mt.end()); break; } }
        if (tplA == null) return sb.append("；行名里没有 11 位编号，认不出格式，箱子内容没换").toString();
        sb.append("；行名格式 ").append(tplA).append("<编号>").append(tplB);
        java.util.HashSet<String> want = new java.util.HashSet<>();
        for (Pile p : ps) { for (long id : p.stack) want.add(tplA + id + tplB); for (long id : p.single) want.add(tplA + id + tplB); }
        java.util.HashMap<String, Integer> idx = new java.util.HashMap<>();
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
            int miss = 0;
            for (int i = 1; i < 0x300000 && miss < 40000 && idx.size() < want.size(); i++) {
                String s = nm.name(m, i);
                if (s == null) { miss++; continue; } miss = 0;
                if (want.contains(s)) idx.put(s, i);
            }
        }
        sb.append("；名字表找到 ").append(idx.size()).append("/").append(want.size());
        for (int i = 0; i < ps.size(); i++) {
            Pile p = ps.get(i);
            java.util.ArrayList<Integer> ent = new java.util.ArrayList<>();
            for (long id : p.single) { Integer k = idx.get(tplA + id + tplB); if (k != null) ent.add(k); }
            for (long id : p.stack) { Integer k = idx.get(tplA + id + tplB); if (k != null) ent.add(k); }
            if (ent.isEmpty()) { sb.append("\n· ").append(p.name).append(" 箱：一件都没在名字表找到，没投"); continue; }
            int n = Math.min(ent.size(), 200);
            for (int k = 0; k < n; k++) { cbuf.putInt(8 + 8 * k, ent.get(k)); cbuf.putInt(12 + 8 * k, 0); }
            int used0 = cbuf.getInt(4);
            cbuf.putInt(0, n);
            float px = x0 + 400f * (i % 5) + 300f, py = y0 + (i < 5 ? 300f : 800f) + 220f;
            long r = drop(b, sys, px, py, z0 + 3000f, type);
            Thread.sleep(200);
            boolean used = cbuf.getInt(4) != used0;
            cbuf.putInt(0, 0);
            sb.append("\n· ").append(p.name).append(" 箱：").append(n).append(" 样，").append(r == 0x5b0e ? (used ? "已投，内容换成我们的" : "已投，但内容补丁没走到") : "没调成");
        }
        return sb.toString();
    }
}
