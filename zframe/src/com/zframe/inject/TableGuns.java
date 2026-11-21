package com.zframe.inject;

import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * v3.63 训练岛射击区枪桌（用户：像原版训练营一样——桌上整齐摆枪，枪带配件（倍镜/枪口/握把/弹匣/枪托）和子弹，捡走的会补）。
 *
 * 物品编号全部按 pak 文件表（私有 pak 仓库 pak-files.tsv）里游戏自己的拾取物蓝图名核对过：
 *   Pickups/Weapon/<编号>_<枪名>、Pickups/WeaponAssist/<编号>_BP_<配件名>、Pickups/Ammo/<编号>_Ammo_<口径>。
 *   配件存在的：枪口 20501000001..11（AR/SMG/SR 各 消音/消焰/补偿，10 手枪消音，11 收束器）；握把 20502000001 垂直、2 直角、3 箭袋；
 *   瞄具 20503000001 红点、2 全息、3 二倍、4 四倍 ACOG、5 八倍 CQBSS、7 弩瞄；弹匣 20504000001..12（AR/SMG/SR/手枪 各 扩容/快速/快扩）；
 *   枪托 20505000001 战术、2 UZI、3 托腮板、4 霰弹子弹袋、5 98K 子弹袋。（没有 3/6 倍镜、拇指/半截/轻型握把这些编号。）
 *
 * 找桌子（先快后慢）：
 *   1. 读对象表：StaticMeshComponent 的模型（+0x628）名字带 Table（pak 里射击场模型是 Environment/Assets/ShootingRange/Range_Table），
 *      在射击区 60 米内 → 用组件世界变换（+0x130 四元数、+0x140 位置、+0x150/+0x14c 缩放）和模型 ExtendedBounds（+0xbc 中心、+0xc8 半边长）
 *      算桌面矩形，桌面中心打一条射线核对高度。不用打几千条射线，几乎立刻就有。
 *   2. 找不到（或是实例化模型读不到每个实例）→ 射线：1.5 米粗格（25×25）找高出地面 55–115 厘米的非地形面，连成桌子，
 *      再沿桌子长/短方向 20 厘米一步往外打，找桌子边。v3.62 是 0.9 米格 45×45 = 2025 条射线（约 1 分钟），这里约 650 + 每桌 30~80 条。
 * 摆法：每张桌子沿长边均分成 1~4 段（每段约 1.5 米），每段一把枪：
 *   靠里一行 = 枪（顺着桌子方向）+ 两头各一份子弹；靠外一行 = 这把枪的配件，均匀排开（同一段内间隔 ≥ 20 厘米，免得游戏把挨着的合成一个箱子）。
 * 补货：每 5 秒扫一次地上拾取物，每件东西按自己的位置（10 厘米内）认；连续两次不在 → 只补那一件。
 *   第一次就认不出一半以上自己摆的东西 → 不补（免得越摆越多）；同一件补了 3 次还认不出 → 那一件不再补。
 * 刷法：cave 'G'（Veh.dropAt → SpawnOnePickUp 0x119d884，朝向 buf+52..60）。全部未实测。
 */
final class TableGuns {
    // {名字, 枪, 子弹, 配件...}；顺序 = 优先级（桌位不够时后面的不摆）
    static final String[][] GUNS = {
        {"M416", "20101000001", "20600000001", "20501000003", "20502000001", "20503000004", "20504000003", "20505000001"},
        {"AKM", "20101000002", "20600000002", "20501000003", "20503000001", "20504000003"},
        {"UMP9", "20105000001", "20600000003", "20501000006", "20502000001", "20503000001", "20504000006"},
        {"98K", "20104000001", "20600000002", "20501000007", "20503000005", "20505000005"},
        {"SKS", "20104000003", "20600000002", "20501000009", "20502000001", "20503000004", "20504000009", "20505000003"},
        {"S1897", "20103000001", "20600000005", "20501000011", "20505000004"},
        {"M249", "20102000001", "20600000001", "20503000004"},
        {"SCAR-L", "20101000004", "20600000001", "20501000001", "20502000002", "20503000003", "20504000003"},
        {"Vector", "20105000003", "20600000003", "20501000004", "20503000002", "20504000006", "20505000001"},
        {"AWM", "20104000002", "20600000006", "20501000007", "20503000005", "20504000009", "20505000003"},
        {"Mini14", "20104000006", "20600000001", "20501000007", "20503000004", "20504000009"},
        {"S686", "20103000002", "20600000005", "20501000011", "20505000004"},
        {"M16A4", "20101000003", "20600000001", "20501000002", "20503000004", "20504000003", "20505000001"},
        {"UZI", "20105000002", "20600000003", "20501000004", "20504000006", "20505000002"},
        {"M24", "20104000004", "20600000002", "20501000009", "20503000005", "20504000009", "20505000003"},
        {"MK14", "20104000005", "20600000002", "20501000008", "20503000004", "20504000009"},
        {"S12K", "20103000003", "20600000005", "20501000011", "20503000001", "20504000003"},
        {"DP-28", "20102000002", "20600000002", "20503000003"},
        {"GROZA", "20101000005", "20600000002", "20501000001", "20503000002", "20504000003"},
        {"汤姆逊", "20105000004", "20600000004", "20501000004", "20502000001", "20504000006"},
        {"QBU88", "20104000008", "20600000001", "20501000009", "20503000004", "20504000009"},
        {"VSS", "20104000007", "20600000003", "20504000009"},
        {"P92", "20201000001", "20600000003", "20501000010", "20504000012"},
        {"P1911", "20201000002", "20600000004", "20501000010", "20504000012"},
        {"十字弩", "20106000001", "20600000007", "20502000003", "20503000007"},
    };
    /** 枪种排序（同一张桌子尽量放同类）：步枪 机枪 冲锋 喷子 狙 连狙 手枪 弩 */
    static int cat(String id) {
        String c = id.substring(0, 5);
        switch (c) { case "20101": return 0; case "20102": return 1; case "20105": return 2; case "20103": return 3; case "20202": case "20201": return 6; case "20106": return 7; }
        if (c.equals("20104")) { int k = Integer.parseInt(id.substring(8)); return k == 1 || k == 2 || k == 4 ? 4 : 5; }
        return 8;
    }

    static final class Table { double cx, cy, z, ux, uy, L, W; String src; boolean ground; }
    static final class Item { final long id; final float x, y, z, yaw; final String what; int miss, unseen, refills; boolean blind, seen;
        Item(long id, float x, float y, float z, float yaw, String what) { this.id = id; this.x = x; this.y = y; this.z = z; this.yaw = yaw; this.what = what; } }
    static final ArrayList<Item> items = new ArrayList<>();
    static void reset() { synchronized (items) { items.clear(); } }
    static void note(String s) { LogCap.note(s); }

    /** 入口（单机/房主，进岛后的线程里调）：等射击区地面能打到 → 找桌子 → 摆 → 补货循环 */
    static void run(long b, Veh.Names nm, long mgr, long pc) throws Exception {
        long t0 = System.currentTimeMillis();
        // v3.64 用户：v3.63 进训练场几秒就闪退。枪桌是进岛几秒内开始、25ms 一件连刷上百件 → 先等 12 秒、每件隔 150ms、最多 12 把枪
        for (int i = 0; i < 24 && Con.training && Con.inMatch; i++) Thread.sleep(500);
        if (!Con.training || !Con.inMatch) return;
        Server.mark("训练岛枪桌：开始找桌子");
        double[] c = null; float gz = Float.NaN;
        for (int i = 0; i < 40 && Con.training && Con.inMatch; i++) {   // 最多 20 秒等射击区地形碰撞
            c = Island.shootCenter(b, mgr, pc);
            if (c != null) { gz = Trace.ground((float) c[0], (float) c[1], (float) c[2] + 5000f, (float) c[2] - 5000f); if (!Float.isNaN(gz)) break; }
            Thread.sleep(500);
        }
        String where;
        if (c == null) { double[] me = Island.me(); if (me == null) { Island.gunTables = "训练岛枪桌：拿不到射击区和人物位置"; note(Island.gunTables); return; } c = me; where = "人物位置"; }
        else where = "射击区 " + (int) c[0] + "," + (int) c[1] + (Float.isNaN(gz) ? "（地面没打到）" : "");
        double cz = Float.isNaN(gz) ? c[2] - 90 : gz;   // 射击区地面
        StringBuilder diag = new StringBuilder();
        ArrayList<Table> ts = new ArrayList<>();
        for (int i = 0; i < 8 && ts.isEmpty() && Con.training && Con.inMatch; i++) {   // 桌子所在关卡可能晚一点才加载：最多 8 秒每秒找一次
            diag.setLength(0);
            ts = meshTables(b, nm, c[0], c[1], cz, diag);
            if (ts.isEmpty()) Thread.sleep(1000);
        }
        String how = "模型";
        if (ts.isEmpty()) { how = "射线"; ts = traceTables(c[0], c[1], cz, diag); }
        long tFind = System.currentTimeMillis() - t0;
        ArrayList<Item> placed = new ArrayList<>();
        int guns = 0, okN = 0, failN = 0;
        if (ts.isEmpty()) {   // 没找到桌子：射击区中心前方地上 4 排「虚拟桌子」（6 米长、隔 2 米），每件东西单独贴地
            how += "（没找到桌子，摆在射击区地上）";
            for (int r = 0; r < 4; r++) { Table t = new Table(); t.ux = 0; t.uy = 1; t.L = 300; t.W = 50; t.cx = c[0] + 400 + r * 200; t.cy = c[1]; t.z = cz; t.src = "地上"; t.ground = true; ts.add(t); }
        }
        // 桌位：每张桌子按约 1.5 米一段均分，最多 4 段
        ArrayList<double[]> slots = new ArrayList<>();   // {桌子序号, 段中心 u, 段长}
        sortTables(ts, c[0], c[1]);
        for (int ti = 0; ti < ts.size(); ti++) {
            Table t = ts.get(ti);
            int k = (int) Math.max(1, Math.min(4, Math.round(2 * t.L / 150.0)));
            double seg = 2 * t.L / k;
            for (int j = 0; j < k; j++) slots.add(new double[] { ti, -t.L + seg * (j + 0.5), seg });
        }
        int n = Math.min(Math.min(slots.size(), GUNS.length), 12);
        Server.mark("训练岛枪桌：开始摆 " + n + " 把");
        ArrayList<String[]> pick = new ArrayList<>();
        for (int i = 0; i < n; i++) pick.add(GUNS[i]);
        pick.sort((p, q) -> cat(p[1]) - cat(q[1]));
        for (int i = 0; i < n; i++) {
            if (!Con.training || !Con.inMatch) return;
            double[] s = slots.get(i); Table t = ts.get((int) s[0]); String[] g = pick.get(i);
            ArrayList<Item> grp = layout(t, s[1], s[2], g);
            int ok = 0;
            for (Item it : grp) { if (drop(it)) { ok++; okN++; placed.add(it); } else failN++; Thread.sleep(150); }
            if (ok > 0) guns++;
        }
        synchronized (items) { items.clear(); items.addAll(placed); }
        Server.mark("训练岛枪桌：摆完 " + okN + " 件");
        StringBuilder tb = new StringBuilder();
        for (int i = 0; i < ts.size() && i < 8; i++) { Table t = ts.get(i); tb.append(String.format(" [%.0f,%.0f z%.0f 长%.0f 宽%.0f %s]", t.cx, t.cy, t.z, 2 * t.L, 2 * t.W, t.src)); }
        Island.gunTables = "训练岛枪桌：" + ts.size() + " 桌 " + slots.size() + " 个桌位，摆了 " + guns + " 把枪（共 " + okN + " 件，失败 " + failN + "）；找桌子用" + how
            + "，" + tFind / 1000.0 + " 秒，总用时 " + (System.currentTimeMillis() - t0) / 1000.0 + " 秒；中心 " + where + "；桌子：" + tb + "；" + diag;
        note(Island.gunTables);
        if (guns > 0) GameOverlay.toastAny("训练岛枪桌已摆好 " + guns + " 把（带配件和子弹）");
        restock(b, nm);
    }

    static boolean drop(Item it) throws InterruptedException { return Veh.dropAt(it.id, it.x, it.y, it.z, it.yaw) == null; }

    /** 一段桌面上的一组：靠里一行 枪 + 两头子弹，靠外一行 配件 */
    static ArrayList<Item> layout(Table t, double u0, double seg, String[] g) {
        ArrayList<Item> l = new ArrayList<>();
        double half = Math.max(12, Math.min(25, t.W * 0.45));   // 两行离桌子中线
        float yaw = (float) Math.toDegrees(Math.atan2(t.uy, t.ux));
        float z = (float) t.z + 2f;
        // 枪：段中心、靠里
        l.add(at(t, u0, +half, z, yaw, Long.parseLong(g[1]), g[0]));
        double ae = Math.min(Math.min(Math.max(45, seg / 2 - 14), 70), seg / 2 - 8);   // 子弹放在枪两头（枪模型约 80~100 厘米长），不出这一段
        ArrayList<long[]> row = new ArrayList<>();   // 靠外一行：配件（段太短时子弹也放这一行）
        for (int j = 3; j < g.length; j++) row.add(new long[] { Long.parseLong(g[j]), 0 });
        if (ae >= 40) {
            l.add(at(t, u0 - ae, +half, z, 0, Long.parseLong(g[2]), g[0] + " 子弹"));
            l.add(at(t, u0 + ae, +half, z, 0, Long.parseLong(g[2]), g[0] + " 子弹"));
        } else { row.add(new long[] { Long.parseLong(g[2]), 1 }); row.add(new long[] { Long.parseLong(g[2]), 1 }); }
        int m = row.size();
        if (m > 0) {
            double d = Math.max(20, Math.min(30, (seg - 30) / m));
            if (d * (m - 1) > seg - 16) d = Math.max(12, (seg - 16) / Math.max(1, m - 1));
            for (int j = 0; j < m; j++) l.add(at(t, u0 + (j - (m - 1) / 2.0) * d, -half, z, 0, row.get(j)[0], g[0] + (row.get(j)[1] == 1 ? " 子弹" : " 配件")));
        }
        return l;
    }
    static Item at(Table t, double u, double v, float z, float yaw, long id, String what) {
        double nx = -t.uy, ny = t.ux;
        float x = (float) (t.cx + t.ux * u + nx * v), y = (float) (t.cy + t.uy * u + ny * v);
        if (t.ground) { float gz = Trace.ground(x, y, z + 400f, z - 1500f); if (!Float.isNaN(gz)) z = gz + 2f; }
        return new Item(id, x, y, z, yaw, what);
    }
    /** 按离射击区中心的距离排（近的先放热门枪），同类枪相邻 */
    static void sortTables(ArrayList<Table> ts, double cx, double cy) {
        ts.sort((a, b) -> Double.compare((a.cx - cx) * (a.cx - cx) + (a.cy - cy) * (a.cy - cy), (b.cx - cx) * (b.cx - cx) + (b.cy - cy) * (b.cy - cy)));
    }

    // ---------------- 找桌子 1：模型 ----------------
    static ArrayList<Table> meshTables(long b, Veh.Names nm, double cx, double cy, double cz, StringBuilder diag) {
        ArrayList<Table> out = new ArrayList<>();
        HashMap<Long, Integer> kind = new HashMap<>(); HashMap<Long, String> meshName = new HashMap<>();
        java.util.TreeMap<String, Integer> names = new java.util.TreeMap<>();
        int comps = 0, inst = 0, far = 0, bad = 0, dup = 0; String why = "";
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
            long g = Server.rd(m, b + 0x51250acL);
            long objs = Server.rd(m, g + 0x10), num = Server.rd(m, g + 0x18);
            if (objs < 0x10000 || num <= 0 || num > 0x200000) { diag.append("模型找桌子：对象表不对"); return out; }
            byte[] raw = new byte[(int) num * 16];
            m.seek(objs); m.readFully(raw);
            ByteBuffer bb = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < num; i++) {
                long o = bb.getInt(i * 16) & 0xffffffffL; int fl = bb.getInt(i * 16 + 4);
                if (o < 0x10000 || (fl & 0x20000000) != 0) continue;
                long c = Server.rd(m, o + 0xc); if (c < 0x10000) continue;
                Integer k = kind.get(c);
                if (k == null) { String cn = Alive.objName(m, nm, c); k = cn.endsWith("StaticMeshComponent") ? (cn.contains("Instanced") ? 2 : 1) : 0; kind.put(c, k); }
                if (k == 0) continue;
                if ((Server.rd(m, o + 4) & 0x10) != 0) continue;   // 类默认对象
                long mesh = Server.rd(m, o + 0x628); if (mesh < 0x10000) continue;
                String mn = meshName.get(mesh);
                if (mn == null) { mn = Alive.objName(m, nm, mesh); meshName.put(mesh, mn); }
                String lw = mn.toLowerCase();
                if (!(lw.contains("table") || lw.contains("desk")) || lw.contains("lift") || lw.contains("datatable")) continue;
                comps++;
                float tx = Mods.f(m, o + 0x140), ty = Mods.f(m, o + 0x144), tz = Mods.f(m, o + 0x148);
                double dx = tx - cx, dy = ty - cy;
                if (dx * dx + dy * dy > 6000.0 * 6000.0 || Math.abs(tz - cz) > 1500) { far++; continue; }
                names.merge(mn + (k == 2 ? "(实例)" : ""), 1, Integer::sum);
                if (k == 2) { inst++; continue; }   // 实例化模型：每个实例的变换数组偏移没查，交给射线
                float qx = Mods.f(m, o + 0x130), qy = Mods.f(m, o + 0x134), qz = Mods.f(m, o + 0x138), qw = Mods.f(m, o + 0x13c);
                double qn = Math.sqrt(qx * qx + qy * qy + qz * qz + qw * qw);
                float[] sc = null;
                for (int off : new int[] { 0x150, 0x14c, 0x118 }) {
                    float a = Mods.f(m, o + off), bq = Mods.f(m, o + off + 4), cq = Mods.f(m, o + off + 8);
                    if (ok(a) && ok(bq) && ok(cq)) { sc = new float[] { a, bq, cq }; break; }
                }
                float ox = Mods.f(m, mesh + 0xbc), oy = Mods.f(m, mesh + 0xc0), oz = Mods.f(m, mesh + 0xc4);
                float ex = Mods.f(m, mesh + 0xc8), ey = Mods.f(m, mesh + 0xcc), ez = Mods.f(m, mesh + 0xd0);
                if (qn < 0.9 || qn > 1.1 || sc == null || !(ex > 1 && ey > 1 && ez > 1 && ex < 5000 && ey < 5000 && ez < 5000)) {
                    bad++; if (why.length() < 200) why += String.format(" [%s 四元数长 %.2f 缩放 %s 边界 %.0f/%.0f/%.0f]", mn, qn, sc == null ? "?" : sc[0] + "/" + sc[1] + "/" + sc[2], ex, ey, ez); continue; }
                qx /= qn; qy /= qn; qz /= qn; qw /= qn;
                double[] X = rot(qx, qy, qz, qw, 1, 0, 0), Y = rot(qx, qy, qz, qw, 0, 1, 0), Z = rot(qx, qy, qz, qw, 0, 0, 1);
                if (Z[2] < 0.95) { bad++; if (why.length() < 200) why += " [" + mn + " 不是正放 " + String.format("%.2f", Z[2]) + "]"; continue; }
                double[] co = rot(qx, qy, qz, qw, sc[0] * ox, sc[1] * oy, sc[2] * oz);
                double ccx = tx + co[0], ccy = ty + co[1], ccz = tz + co[2];
                double hx = Math.abs(sc[0]) * ex, hy = Math.abs(sc[1]) * ey, hz = Math.abs(sc[2]) * ez;
                Table t = new Table(); t.cx = ccx; t.cy = ccy; t.z = ccz + hz;
                double[] U = hx >= hy ? X : Y; double ul = Math.hypot(U[0], U[1]); t.ux = U[0] / ul; t.uy = U[1] / ul;
                t.L = Math.max(hx, hy); t.W = Math.min(hx, hy);
                if (2 * hz < 40 || 2 * hz > 170 || t.L < 40 || t.L > 500 || t.W < 15 || t.W > 150) {
                    bad++; if (why.length() < 200) why += String.format(" [%s 尺寸 长%.0f 宽%.0f 高%.0f 不像桌子]", mn, 2 * t.L, 2 * t.W, 2 * hz); continue; }
                boolean same = false;
                for (Table q : out) if (Math.abs(q.cx - t.cx) < 30 && Math.abs(q.cy - t.cy) < 30) same = true;
                if (same) { dup++; continue; }
                t.src = mn;
                out.add(t);
            }
        } catch (Throwable e) { diag.append("模型找桌子：出错 ").append(e); return out; }
        // 桌面高度用射线核对（模型边界可能比桌面高/低一点）
        int fixed = 0;
        for (Table t : out) {
            Trace.Hit h = Trace.down((float) t.cx, (float) t.cy, (float) t.z + 60f, (float) t.z - 80f);
            if (h != null && !Float.isNaN(h.z) && !h.land && Math.abs(h.z - t.z) < 40) { t.z = h.z; fixed++; }
        }
        StringBuilder nb = new StringBuilder(); int c = 0;
        for (java.util.Map.Entry<String, Integer> e : names.entrySet()) { if (c++ >= 6) break; nb.append(e.getKey()).append("×").append(e.getValue()).append(' '); }
        diag.append("模型找桌子：带 Table 的模型组件 ").append(comps).append("（60 米外 ").append(far).append("，实例化 ").append(inst).append("，读不对 ").append(bad)
            .append("，重复 ").append(dup).append("）→ 桌子 ").append(out.size()).append("，射线核对桌面 ").append(fixed).append("；模型：").append(nb).append(why);
        return out;
    }
    static boolean ok(float v) { return !Float.isNaN(v) && Math.abs(v) >= 0.05f && Math.abs(v) <= 50f; }
    /** 四元数转向量 */
    static double[] rot(double qx, double qy, double qz, double qw, double x, double y, double z) {
        double tx = 2 * (qy * z - qz * y), ty = 2 * (qz * x - qx * z), tz = 2 * (qx * y - qy * x);
        return new double[] { x + qw * tx + (qy * tz - qz * ty), y + qw * ty + (qz * tx - qx * tz), z + qw * tz + (qx * ty - qy * tx) };
    }

    // ---------------- 找桌子 2：射线（粗格 + 找边） ----------------
    static boolean bad(Trace.Hit h) {
        if (h == null || Float.isNaN(h.z) || h.land) return true;
        String w = (h.actor + "/" + h.cls).toLowerCase();
        return w.contains("landscape") || w.contains("character") || w.contains("vehicle") || w.contains("pick") || w.contains("target") || w.contains("位置对不上");
    }
    static ArrayList<Table> traceTables(double cx, double cy, double cz, StringBuilder diag) throws Exception {
        long t0 = System.currentTimeMillis();
        ArrayList<Table> out = new ArrayList<>();
        final float step = 150f; final int R = 12; int n = 2 * R + 1, rays = 0;
        float[][] z = new float[n][n]; boolean[][] obj = new boolean[n][n];
        for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) {
            if (!Con.training || !Con.inMatch) return out;
            Trace.Hit h = Trace.down((float) (cx + (i - R) * step), (float) (cy + (j - R) * step), (float) cz + 190f, (float) cz - 1500f); rays++;
            z[i][j] = h == null || Float.isNaN(h.z) ? Float.NaN : h.z;
            obj[i][j] = !bad(h);
        }
        ArrayList<float[]> top = new ArrayList<>();
        for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) {
            if (!obj[i][j]) continue;
            float lo = z[i][j];
            for (int a = Math.max(0, i - 2); a <= Math.min(n - 1, i + 2); a++) for (int c = Math.max(0, j - 2); c <= Math.min(n - 1, j + 2); c++) if (!Float.isNaN(z[a][c])) lo = Math.min(lo, z[a][c]);
            float d = z[i][j] - lo;
            if (d >= 55 && d <= 115) top.add(new float[] { (float) (cx + (i - R) * step), (float) (cy + (j - R) * step), z[i][j] });
        }
        int[] g = new int[top.size()]; for (int i = 0; i < g.length; i++) g[i] = i;
        for (int i = 0; i < g.length; i++) for (int j = i + 1; j < g.length; j++) {
            float[] p = top.get(i), q = top.get(j);
            if (Math.hypot(p[0] - q[0], p[1] - q[1]) < 160 && Math.abs(p[2] - q[2]) < 30) g[Island.root(g, i)] = Island.root(g, j);
        }
        java.util.LinkedHashMap<Integer, ArrayList<float[]>> cl = new java.util.LinkedHashMap<>();
        for (int i = 0; i < g.length; i++) cl.computeIfAbsent(Island.root(g, i), k -> new ArrayList<>()).add(top.get(i));
        int tooLong = 0;
        for (ArrayList<float[]> c : cl.values()) {
            if (!Con.training || !Con.inMatch) return out;
            double mx = 0, my = 0, mz = 0; for (float[] p : c) { mx += p[0]; my += p[1]; mz += p[2]; } mx /= c.size(); my /= c.size(); mz /= c.size();
            double[] dirs;
            if (c.size() >= 2) {   // 主方向（PCA）
                double sxx = 0, syy = 0, sxy = 0; for (float[] p : c) { double dx = p[0] - mx, dy = p[1] - my; sxx += dx * dx; syy += dy * dy; sxy += dx * dy; }
                dirs = new double[] { 0.5 * Math.atan2(2 * sxy, sxx - syy) };
            } else dirs = new double[] { 0, Math.PI / 4, Math.PI / 2, 3 * Math.PI / 4 };
            Table best = null; double[] r = new double[1];
            for (double a : dirs) {
                double ux = Math.cos(a), uy = Math.sin(a);
                double lp = edge(mx, my, mz, ux, uy, 20, 400, r), ln = edge(mx, my, mz, -ux, -uy, 20, 400, r);
                rays += (int) r[0];
                if (best == null || lp + ln > 2 * best.L) { best = new Table(); best.ux = ux; best.uy = uy; best.L = (lp + ln) / 2; best.cx = mx + ux * (lp - ln) / 2; best.cy = my + uy * (lp - ln) / 2; best.z = mz; }
            }
            double wp = edge(best.cx, best.cy, best.z, -best.uy, best.ux, 15, 150, r); rays += (int) r[0];
            double wn = edge(best.cx, best.cy, best.z, best.uy, -best.ux, 15, 150, r); rays += (int) r[0];
            best.W = (wp + wn) / 2; best.cx += -best.uy * (wp - wn) / 2; best.cy += best.ux * (wp - wn) / 2;
            if (best.L > 400) { tooLong++; continue; }
            if (best.L < 40 || best.W < 15) continue;
            best.src = "射线";
            out.add(best);
        }
        diag.append("射线找桌子：粗格 1.5 米 ").append(n).append("×").append(n).append("，桌面格 ").append(top.size()).append("，连成 ").append(cl.size())
            .append(" 块（太长 ").append(tooLong).append("）→ 桌子 ").append(out.size()).append("，射线 ").append(rays).append(" 条，")
            .append((System.currentTimeMillis() - t0) / 1000).append(" 秒");
        return out;
    }
    /** 从 (x,y) 沿 (ux,uy) 每 st 厘米打一条，桌面（非地形、高度差 < 10 厘米）断了就停；返回到边的距离（最后一个在桌面上的点 + 半步） */
    static double edge(double x, double y, double z0, double ux, double uy, double st, double max, double[] rays) throws InterruptedException {
        rays[0] = 0; double d = 0;
        for (double s = st; s <= max; s += st) {
            Trace.Hit h = Trace.down((float) (x + ux * s), (float) (y + uy * s), (float) z0 + 40f, (float) z0 - 40f); rays[0]++;
            if (bad(h) || Math.abs(h.z - z0) > 10) break;
            d = s;
        }
        return d + st / 2;
    }

    // ---------------- 补货 ----------------
    static void restock(long b, Veh.Names nm) throws Exception {
        int rounds = 0, refills = 0; boolean checked = false;
        while (Con.training && Con.inMatch) {
            Thread.sleep(5000);
            if (!Con.training || !Con.inMatch) break;
            ArrayList<Item> its; synchronized (items) { its = new ArrayList<>(items); }
            if (its.isEmpty()) { Island.restock = "训练岛补枪：没有摆放的东西"; return; }
            Island.Scan sc = Island.scan(b, nm); rounds++;
            int seen = 0; double sumNear = 0; int nearN = 0;
            for (Item it : its) {
                double best = 1e18;
                for (float[] q : sc.pick) { double dx = q[0] - it.x, dy = q[1] - it.y; if (Math.abs(q[2] - it.z) > 80) continue; best = Math.min(best, dx * dx + dy * dy); }
                boolean here = best < 10.0 * 10.0;
                if (!checked && best < 1e17) { sumNear += Math.sqrt(best); nearN++; }
                if (here) { seen++; it.seen = true; it.miss = 0; it.unseen = 0; } else it.miss++;
            }
            if (!checked) {
                checked = true;
                if (seen * 2 < its.size()) {
                    Island.restock = String.format("训练岛补枪：第一次检查只认出 %d/%d 件（地上拾取物 %d 个，平均最近 %.0f 厘米），认不准，不补（免得越摆越多）", seen, its.size(), sc.pick.size(), nearN == 0 ? -1 : sumNear / nearN);
                    note(Island.restock); return;
                }
            }
            int now = 0, blind = 0;
            for (Item it : its) {
                if (it.blind) { blind++; continue; }
                if (it.miss < 2) continue;
                if (it.unseen >= 3) { it.blind = true; blind++; note("训练岛补枪：" + it.what + " " + it.id + " 补了 3 次还认不出，不再补"); continue; }
                if (drop(it)) { now++; it.refills++; }
                it.miss = 0; it.unseen++;
                Thread.sleep(25);
            }
            refills += now;
            Island.restock = "训练岛补枪：第 " + rounds + " 次检查（每 5 秒），" + its.size() + " 件里在桌上 " + seen + "，这次补 " + now + " 件，累计 " + refills + " 件" + (blind > 0 ? "，不再补 " + blind + " 件" : "");
            if (now > 0) note(Island.restock);
        }
    }
}
