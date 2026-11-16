package com.zframe.inject;

import android.app.Activity;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;

/**
 * v1.80 刷车。
 * 单机对局里地图上一辆车都没有：车辆管理器的车表（VehicleManager+0x2c0）是有的，但刷车点 VehicleSpawnComponentList 是 0
 * （刷车点在服务器才加载的关卡里）。游戏自带的 PC::SpawnVehicle 实现 0x1102ca4(PC, 车编号, &位置, &朝向) 是好的，只是控制台调不到。
 * 做法：刷物品补丁的 cave（AddAvaItem P…）判断第一个字不是 'P' 时，转到新 cave（代码段空隙 SLACK+0x400）：
 *   "V0x<buf>"      → 只填信息：+4 PC，+8 人物，+12..20 人物位置，+24 World，+28 GameMode，+32 车辆管理器，+36 车表，+68 根组件，+72..84 朝向四元数
 *   "V<编号>x<buf>" → 再调 0x1102ca4，位置取 buf+40，朝向取 buf+52。完成标记 +0：0x5b02 只读信息 / 0x5b03 已调刷车
 * 车表的行名是车编号的十进制字符串（FindRow("%d")），所以要读 FName 名字表把行名翻出来。
 * 这些钩子和早装一样，在 libUE4 刚加载时装（运行中改已跑过的代码不生效）。
 */
public class Veh {
    static final long VCAVE = Server.SLACK + 0x400, TRAMP = 0x1716c10L, BNE = 0x17169ecL, SPAWN = 0x1102ca4L;
    static final String TRAMP_O = "00009de50810a0e1", BNE_O = "8400001a", BNE_N = "8700001a";
    static final String CAVE = "560050e35300001a0190a0e30070a0e3060059e1070000aa090195e7300040e2090050e30300008a077187e0877080e0019089e2f5ffffea019089e20080a0e3060059e1070000aa090195e7300040e2090050e30300008a088188e0888080e0019089e2f5ffffea000058e33900000a010b05e3000088e5044088e5c0a294e508a088e500005ae33000000a28019ae5440088e5000050e32c00000a401190e50c1088e5441190e5101088e5481190e5141088e5301190e5481088e5341190e54c1088e5381190e5501088e53c1190e5541088e5000094e5a01090e50400a0e131ff2fe1180088e5000050e31700000a940090e51c0088e5000050e31300000a140990e5200088e5000050e30f00000ac01290e5241088e5000057e30b00000a000051e30900000a407088e50400a0e10710a0e1282088e2343088e21cc09fe53cff2fe1030b05e3000088e5010000ea020b05e3000088e544d08de2f08fbde8";

    public static volatile String state = "刷车：没装";
    static volatile boolean installed;

    /** 启动早装（Mods.preinstall 里调）。刷物品补丁没打就不装 */
    static synchronized String install() {
        if (installed) return "ok";
        long b = Mods.base();
        if (b == 0) return state = "刷车：找不到游戏库";
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "rw")) {
            if (!Server.bytesAt(m, b + 0x17169dcL, 4).equals("020056e3")) return state = "刷车：刷物品补丁没打（先在补丁更新里点「更新补丁」）";
            String bne = Server.bytesAt(m, b + BNE, 4);
            if (!bne.equals(BNE_O) && !bne.equals(BNE_N)) return state = "刷车：补丁代码和预期不一样 " + bne;
            String tr = Server.bytesAt(m, b + TRAMP, 8);
            String trN = "04f01fe5" + Server.w32((int) (b + VCAVE));
            if (!tr.equals(TRAMP_O) && !tr.equals(trN)) return state = "刷车：空位字节不符 " + tr;
            m.seek(b + VCAVE); m.write(Patches.hex(CAVE + Server.w32((int) (b + SPAWN))));
            m.seek(b + TRAMP); m.write(Patches.hex(trN));
            m.seek(b + BNE); m.write(Patches.hex(BNE_N));
            if (!Server.bytesAt(m, b + BNE, 4).equals(BNE_N)) return state = "刷车：写入没生效";
        } catch (Throwable e) { return state = "刷车：出错 " + e.getMessage(); }
        installed = true;
        state = "刷车：已装（启动时）";
        return "ok";
    }

    static ByteBuffer buf; static long addr;
    static boolean call(long id) throws InterruptedException {
        if (buf == null) { buf = ByteBuffer.allocateDirect(128).order(ByteOrder.LITTLE_ENDIAN); addr = GameOverlay.bufAddr(buf); }
        if (addr <= 0 || addr > 0xffffffffL) return false;
        buf.putInt(0, 0);
        final String c = "AddAvaItem V" + id + "x" + addr;
        Activity a = Con.act; if (a == null) return false;
        a.runOnUiThread(() -> Con.exec(c));
        for (int i = 0; i < 40; i++) {
            Thread.sleep(50);
            int mg = buf.getInt(0);
            if (mg == 0x5b02 || mg == 0x5b03) return true;
        }
        return false;
    }

    // ---------------- 车表：行名（车编号）+ 车的蓝图名 ----------------
    static final class Row { long id; String name; Row(long i, String n) { id = i; name = n; } }
    static ArrayList<Row> rows; static long rowsDt;
    static int next;
    static volatile String tableInfo = "";

    static ArrayList<Row> readRows(long dt) throws Exception {
        ArrayList<Row> out = new ArrayList<>();
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
            long data = Server.rd(m, dt + 0x20), num = Server.rd(m, dt + 0x24);
            if (data == 0 || num <= 0 || num > 500) { tableInfo = "车表行数不对 " + num; return out; }
            Names n = Names.get();
            int dig = 0;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < num; i++) {
                long e = data + 20L * i;
                long idx = Server.rd(m, e), no = Server.rd(m, e + 4), row = Server.rd(m, e + 8);
                String nm = n == null ? null : n.name(m, (int) idx);
                if (nm != null && no > 0) nm = nm + "_" + (no - 1);
                String bp = path(m, row);
                if (sb.length() < 600) sb.append(nm).append('=').append(bp).append(' ');
                if (nm != null && nm.matches("\\d{1,9}")) { dig++; out.add(new Row(Long.parseLong(nm), bp)); }
            }
            tableInfo = "车表 " + num + " 行，编号 " + dig + " 个，名字表 " + (n == null ? "没找到" : "ok") + "：" + sb;
        }
        return out;
    }
    /** 行里找 "/Game/..." 的 FString（UTF-32），取蓝图名 */
    static String path(RandomAccessFile m, long row) {
        if (row == 0) return "?";
        for (int o = 0; o < 0x100; o += 4) {
            long p = Server.rd(m, row + o), len = Server.rd(m, row + o + 4);
            if (p < 0x10000 || len < 8 || len > 300) continue;
            String s = u32(m, p, (int) Math.min(len, 120));
            if (s != null && s.startsWith("/Game")) {
                String t = s.substring(s.lastIndexOf('/') + 1);
                int d = t.indexOf('.'); if (d > 0) t = t.substring(0, d);
                return t;
            }
        }
        return "?";
    }
    static String u32(RandomAccessFile m, long p, int n) {
        try {
            byte[] x = new byte[n * 4]; m.seek(p); m.readFully(x);
            StringBuilder s = new StringBuilder();
            for (int i = 0; i < n; i++) {
                int c = (x[4 * i] & 0xff) | (x[4 * i + 1] & 0xff) << 8 | (x[4 * i + 2] & 0xff) << 16 | (x[4 * i + 3] & 0xff) << 24;
                if (c == 0) break;
                if (c < 0x20 || c > 0x7e) return null;
                s.append((char) c);
            }
            return s.toString();
        } catch (Throwable e) { return null; }
    }

    static String nice(String bp) {
        String l = bp.toLowerCase();
        if (l.contains("uaz")) return "吉普";
        if (l.contains("dacia")) return "轿车";
        if (l.contains("buggy")) return "蹦蹦";
        if (l.contains("moto") && l.contains("sidecar")) return "三轮摩托";
        if (l.contains("moto")) return "摩托";
        if (l.contains("bus")) return "面包车";
        if (l.contains("pickup")) return "皮卡";
        if (l.contains("boat") || l.contains("aqua")) return "船";
        return bp;
    }

    /** 菜单按钮：在面前刷一辆，每按一次换一种 */
    static volatile boolean busy;
    public static void spawnNext() {
        if (busy) { GameOverlay.toastAny(state); return; }
        busy = true;
        new Thread(() -> {
            try {
                if (!installed) { GameOverlay.toastAny(state); return; }
                if (!call(0)) { GameOverlay.toastAny(state = "刷车：补丁代码没反应（进对局、落地后再按）"); return; }
                long dt = buf.getInt(36) & 0xffffffffL, ch = buf.getInt(8) & 0xffffffffL;
                if (ch == 0) { GameOverlay.toastAny(state = "刷车：还没有人物"); return; }
                if (dt == 0) { GameOverlay.toastAny(state = "刷车：这局没有车表（GameMode " + Integer.toHexString(buf.getInt(28)) + "）"); return; }
                if (rows == null || rowsDt != dt) {
                    GameOverlay.toastAny("第一次刷车要读车表，约半分钟，读完会提示");
                    state = "刷车：正在读车表…"; GameOverlay.refreshAny();
                    rows = readRows(dt); rowsDt = dt; next = 0;
                    LogCap.note("刷车 " + tableInfo); GameOverlay.toastAny(tableInfo.length() > 60 ? tableInfo.substring(0, 60) : tableInfo);
                }
                ArrayList<Row> use = new ArrayList<>();
                for (Row r : rows) if (!nice(r.name).equals("船")) use.add(r);
                if (use.isEmpty()) use = rows;
                if (use.isEmpty()) { GameOverlay.toastAny(state = "刷车：车表里没读到车编号（" + tableInfo.split("：")[0] + "）"); return; }
                Row r = use.get(next++ % use.size());
                float x = Float.intBitsToFloat(buf.getInt(12)), y = Float.intBitsToFloat(buf.getInt(16)), z = Float.intBitsToFloat(buf.getInt(20));
                float qx = Float.intBitsToFloat(buf.getInt(72)), qy = Float.intBitsToFloat(buf.getInt(76)), qz = Float.intBitsToFloat(buf.getInt(80)), qw = Float.intBitsToFloat(buf.getInt(84));
                double yaw = Math.atan2(2 * (qw * qz + qx * qy), 1 - 2 * (qy * qy + qz * qz));
                if (Double.isNaN(yaw)) yaw = 0;
                buf.putFloat(40, (float) (x + 600 * Math.cos(yaw)));
                buf.putFloat(44, (float) (y + 600 * Math.sin(yaw)));
                buf.putFloat(48, z + 150);
                buf.putFloat(52, 0f); buf.putFloat(56, (float) Math.toDegrees(yaw)); buf.putFloat(60, 0f);
                boolean ok = call(r.id) && buf.getInt(0) == 0x5b03;
                String s = "刷车：" + (ok ? "已刷 " : "没反应 ") + nice(r.name) + "（" + r.id + "，" + ((next - 1) % use.size() + 1) + "/" + use.size() + "）";
                LogCap.note(s + " 位置 " + x + "," + y + "," + z);
                GameOverlay.toastAny(state = s + (ok ? "，在你面前，没看到就再按一次换一种" : ""));
            } catch (Throwable e) { GameOverlay.toastAny(state = "刷车：出错 " + e); LogCap.note(state); }
            finally { busy = false; GameOverlay.refreshAny(); }
        }, "zf-veh").start();
    }

    /** FName 名字表（GNames）：找 "None"(0号) 和 "ByteProperty"(1号) 两个条目，反推块表 */
    static final class Names {
        long chunks; boolean shift = true;
        static Names cached;
        /** 小的 int 集合（开放寻址），避免 HashSet<Long> 装箱太慢 */
        static final class ISet {
            int[] t = new int[1 << 14]; int n;
            void add(int v) { if (v == 0 || n > t.length / 2) return; int h = (v * 0x9E3779B1) >>> 18; while (t[h] != 0 && t[h] != v) h = (h + 1) & (t.length - 1); if (t[h] == 0) { t[h] = v; n++; } }
            boolean has(int v) { if (v == 0) return false; int h = (v * 0x9E3779B1) >>> 18; while (t[h] != 0) { if (t[h] == v) return true; h = (h + 1) & (t.length - 1); } return false; }
        }
        static synchronized Names get() throws Exception {
            if (cached != null) return cached;
            ArrayList<long[]> rs = regions();
            long total = 0; for (long[] g : rs) total += g[1] - g[0];
            ISet none = new ISet(), byteP = new ISet();
            byte[] bb = new byte[1 << 20];
            int[] w = new int[bb.length / 4];
            long t0 = System.currentTimeMillis();
            try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
                // 第 1 遍：找 "None"(序号字 0) 和 "ByteProperty"(序号字 2 或 1) 两种条目
                long done = 0;
                for (long[] g : rs) for (long a = g[0]; a < g[1]; a += bb.length) {
                    int n = (int) Math.min(bb.length, g[1] - a);
                    try { m.seek(a); m.readFully(bb, 0, n); } catch (Throwable e) { break; }
                    done += n;
                    for (int i = 8; i + 13 <= n; i += 4) {
                        byte c = bb[i];
                        if (c == 'N') {
                            if (bb[i + 1] == 'o' && bb[i + 2] == 'n' && bb[i + 3] == 'e' && bb[i + 4] == 0
                                && bb[i - 8] == 0 && bb[i - 7] == 0 && bb[i - 6] == 0 && bb[i - 5] == 0) none.add((int) (a + i - 8));
                        } else if (c == 'B') {
                            if (bb[i + 12] == 0 && bb[i + 1] == 'y' && bb[i + 4] == 'P' && bb[i + 11] == 'y' && (bb[i - 8] == 2 || bb[i - 8] == 1) && bb[i - 7] == 0
                                && new String(bb, i, 12, "ISO-8859-1").equals("ByteProperty")) byteP.add((int) (a + i - 8));
                        }
                    }
                    if ((done & 0x3ffffff) == 0) state = "刷车：读名字表 1/3 " + done * 100 / Math.max(1, total) + "%";
                }
                LogCap.note("刷车 名字表候选 None " + none.n + " ByteProperty " + byteP.n + "（" + (System.currentTimeMillis() - t0) + " ms，" + total / 1048576 + " MB）");
                if (none.n == 0 || byteP.n == 0) return null;
                // 第 2 遍：块 0 = 相邻两个字分别指向 None 和 ByteProperty
                long chunk0 = 0; done = 0;
                outer:
                for (long[] g : rs) for (long a = g[0]; a < g[1]; a += bb.length) {
                    int n = (int) Math.min(bb.length, g[1] - a);
                    try { m.seek(a); m.readFully(bb, 0, n); } catch (Throwable e) { break; }
                    done += n;
                    ByteBuffer.wrap(bb, 0, n).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().get(w, 0, n / 4);
                    for (int i = 0; i + 1 < n / 4; i++) if (byteP.has(w[i + 1]) && none.has(w[i])) { chunk0 = a + 4L * i; break outer; }
                    if ((done & 0x3ffffff) == 0) state = "刷车：读名字表 2/3 " + done * 100 / Math.max(1, total) + "%";
                }
                LogCap.note("刷车 名字块0=" + Long.toHexString(chunk0) + "（" + (System.currentTimeMillis() - t0) + " ms）");
                if (chunk0 == 0) return null;
                Names r = new Names();
                r.shift = Server.rd(m, Server.rd(m, chunk0 + 4)) == 2;
                // 第 3 遍：块表 = 存着块 0 地址的那个字
                int c0 = (int) chunk0; done = 0;
                outer3:
                for (long[] g : rs) for (long a = g[0]; a < g[1]; a += bb.length) {
                    int n = (int) Math.min(bb.length, g[1] - a);
                    try { m.seek(a); m.readFully(bb, 0, n); } catch (Throwable e) { break; }
                    done += n;
                    ByteBuffer.wrap(bb, 0, n).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().get(w, 0, n / 4);
                    for (int i = 0; i < n / 4; i++) if (w[i] == c0) { r.chunks = a + 4L * i; break outer3; }
                    if ((done & 0x3ffffff) == 0) state = "刷车：读名字表 3/3 " + done * 100 / Math.max(1, total) + "%";
                }
                if (r.chunks == 0) r.chunks = -chunk0;   // 没找到块表：只能查前 16384 个
                LogCap.note("刷车 名字块表=" + Long.toHexString(r.chunks) + " 移位=" + r.shift + "（" + (System.currentTimeMillis() - t0) + " ms）");
                return cached = r;
            }
        }
        String name(RandomAccessFile m, int idx) {
            long c0 = chunks < 0 ? -chunks : 0;
            long chunk = chunks < 0 ? (idx < 16384 ? c0 : 0) : Server.rd(m, chunks + 4L * (idx / 16384));
            if (chunk == 0) return null;
            long e = Server.rd(m, chunk + 4L * (idx % 16384));
            if (e == 0) return null;
            long f = Server.rd(m, e);
            if ((shift ? f >> 1 : f) != idx) return null;
            return ascii(m, e + 8, 64);
        }
    }
    static String ascii(RandomAccessFile m, long p, int n) {
        if (p < 0x10000) return null;
        try {
            byte[] x = new byte[n]; m.seek(p); m.readFully(x);
            int e = 0; while (e < n && x[e] != 0) e++;
            return new String(x, 0, e, "ISO-8859-1");
        } catch (Throwable t) { return null; }
    }
    static ArrayList<long[]> regions() throws java.io.IOException {
        ArrayList<long[]> rs = new ArrayList<>();
        try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader("/proc/self/maps"))) {
            String l;
            while ((l = r.readLine()) != null) {
                String[] p = l.trim().split("\\s+");
                if (!p[1].startsWith("rw")) continue;
                String name = p.length > 5 ? p[5] : "";
                if (!(name.isEmpty() || name.startsWith("[anon:") || name.equals("[heap]")) || name.contains("stack")) continue;
                String[] ab = p[0].split("-");
                long s = Long.parseLong(ab[0], 16), e = Long.parseLong(ab[1], 16);
                if (e > 0xffffffffL) continue;
                rs.add(new long[] { s, e });
            }
        }
        return rs;
    }
}
