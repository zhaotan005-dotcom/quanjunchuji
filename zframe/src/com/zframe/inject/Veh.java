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
    static final long VCAVE = Server.SLACK + 0x400, TRAMP = 0x1716c10L, BNE = 0x17169ecL, SPAWN = 0x1102ca4L, BOT = 0x1115620L;
    /** v1.85 cave 末尾的字面量（顺序见 game/re/vcave.py LITS）：GM AddBotAt 实现、难度→人机类、AddOneBot、PVP 人机 OnPossess、给装备、切战斗行为树 */
    static final long[] LITS = { SPAWN, BOT, 0x1012318L, 0x1014898L, 0x10112d0L, 0xc1bbd8L, 0xc1a534L, 0xc17fe0L, 0x1989c98L, 0x119d884L };   // v1.88 加 FMemory::Malloc，v1.91 加 SpawnOnePickUp 实现（刷在地上）
    static final String TRAMP_O = "00009de50810a0e1", BNE_O = "8400001a", BNE_N = "8700001a";
    static final String CAVE = "00b0a0e3560050e30e00000a01b0a0e3420050e30b00000a02b0a0e3410050e30800000a03b0a0e3450050e30500000a04b0a0e34d0050e30200000a05b0a0e3470050e3f200001a0190a0e30070a0e3060059e1070000aa090195e7300040e2090050e30300008a077187e0877080e0019089e2f5ffffea019089e20080a0e3060059e1070000aa090195e7300040e2090050e30300008a088188e0888080e0019089e2f5ffffea000058e3d800000a04005be3b900000a05005be3bf00000a010b05e3000088e5044088e5c0a294e508a088e500005ae3cb00000a28019ae5440088e5000050e3c700000a401190e50c1088e5441190e5101088e5481190e5141088e5301190e5481088e5341190e54c1088e5381190e5501088e53c1190e5541088e5000094e5a01090e50400a0e131ff2fe1180088e5000050e3b200000a940090e51c0088e5000050e3ae00000a00005be31300001a140990e5200088e5000050e3a800000ac01290e5241088e5000057e3a400000a000051e3a200000a407088e50400a0e10710a0e1282088e2343088e280c29fe53cff2fe1030b05e3000088e59a0000ea01005be31300001a000057e39400000a407088e5300098e500008de5340098e504008de5380098e508008de53c0098e50c008de50400a0e10710a0e1282098e52c3098e52cc29fe53cff2fe1040b05e3000088e5840000ea03005be35a00000a0060a0e1001090e5c41591e508229fe5020051e1050b05e35100001a407088e50600a0e10710a0e1f0c19fe53cff2fe1580088e5000050e3060b05e34800000a581098e50600a0e10720a0e1d0c19fe53cff2fe10050a0e15c5088e5000055e3060b05e33e00000a000096e5f82590e50600a0e10510a0e132ff2fe1000096e59c2390e50600a0e10510a0e132ff2fe1b40295e5600088e5000050e30c00000a001090e550c291e50010a0e300108de5281088e2342088e20030a0e33cff2fe1000095e5082390e50500a0e1341088e232ff2fe1000095e5440390e544119fe5010050e1070b05e31d00001a540395e5640088e5580395e5680088e5240395e56c0088e5ec0395e5700088e5749098e5010019e30200000a0500a0e108c19fe53cff2fe1040019e30400001a020019e30800000a240395e5000050e30500001a580395e5000050e30200000a0500a0e1d8c09fe53cff2fe1240395e5780088e5080b05e3000088e5270000ea0750a0e1000055e3060b05e3f9ffff0a000095e5440390e59c109fe5010050e1070b05e3f3ffff1ad4ffffea0700a0e11010a0e38cc09fe53cff2fe1040088e5090b05e3000088e5140000ea2c0098e500008de5300098e504008de5340098e508008de5380098e50c008de53c0098e510008de50400a0e10710a0e17c2098e5283098e540c09fe53cff2fe10a0b05e3000088e5010000ea020b05e3000088e544d08de2f08fbde8";

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
            StringBuilder lit = new StringBuilder(CAVE);
            for (long l : LITS) lit.append(Server.w32((int) (b + l)));
            m.seek(b + VCAVE); m.write(Patches.hex(lit.toString()));
            m.seek(b + TRAMP); m.write(Patches.hex(trN));
            m.seek(b + BNE); m.write(Patches.hex(BNE_N));
            if (!Server.bytesAt(m, b + BNE, 4).equals(BNE_N)) return state = "刷车：写入没生效";
        } catch (Throwable e) { return state = "刷车：出错 " + e.getMessage(); }
        installed = true;
        state = "刷车：已装（启动时）";
        return "ok";
    }

    static ByteBuffer buf; static long addr;
    static boolean call(long id) throws InterruptedException { return call('V', id); }
    static synchronized boolean call(char k, long id) throws InterruptedException {
        if (buf == null) { buf = ByteBuffer.allocateDirect(128).order(ByteOrder.LITTLE_ENDIAN); addr = GameOverlay.bufAddr(buf); }
        if (addr <= 0 || addr > 0xffffffffL) return false;
        buf.putInt(0, 0);
        final String c = "AddAvaItem " + k + id + "x" + addr;
        Activity a = Con.act; if (a == null) return false;
        a.runOnUiThread(() -> Con.exec(c));
        for (int i = 0; i < 40; i++) {
            Thread.sleep(50);
            int mg = buf.getInt(0);
            if (mg >= 0x5b02 && mg <= 0x5b0a) return true;
        }
        return false;
    }

    /** v1.91 刷在地上：游戏自己的 SpawnOnePickUp（cave G），v1.92 起放在脚下，piles 堆；返回 null = 成功 */
    static synchronized String ground(long id, int piles) throws InterruptedException {
        if (!installed) return state;
        long type = id / 10000, tsid = id % 10000;
        java.util.Random rnd = new java.util.Random();
        for (int i = 0; i < piles; i++) {
            String e = ahead(20, -80);   // v1.92 直接脚下（人物中心离地约 90 厘米），几堆靠随机散开
            if (e != null) return e;
            buf.putFloat(40, buf.getFloat(40) + (rnd.nextFloat() - 0.5f) * (i == 0 ? 10 : 60));
            buf.putFloat(44, buf.getFloat(44) + (rnd.nextFloat() - 0.5f) * (i == 0 ? 10 : 60));
            buf.putFloat(52, 0f); buf.putFloat(60, 0f);
            buf.putInt(124, (int) tsid);
            if (!call('G', type) || buf.getInt(0) != 0x5b0a) return "补丁代码没反应";
        }
        return null;
    }
    /** v1.88 用游戏自己的分配器要一块内存（cave 'M'），失败返回 0 */
    static synchronized long malloc(int size) throws InterruptedException {
        if (!installed || size <= 0 || size > 0x10000) return 0;
        if (!call('M', size)) return 0;
        if (buf.getInt(0) != 0x5b09) return 0;
        return buf.getInt(4) & 0xffffffffL;
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
            // v1.83：实测元素间距不是 20（20 字节读出来的大半是乱的，但 5/11/16/21/26 号车都刷出来了）
            // → 在 num×128 字节范围里每 4 字节试一次 FName，名字是纯数字、Number=0 的就是车编号；行指针试 +8/+12
            java.util.TreeMap<Long, String> ids = new java.util.TreeMap<>();
            long end = data + Math.min(num * 128L, 0x10000);
            for (long e = data; e + 16 <= end && n != null; e += 4) {
                long idx = Server.rd(m, e), no = Server.rd(m, e + 4);
                if (idx <= 0 || idx > 0x400000 || no != 0) continue;
                String nm = n.name(m, (int) idx);
                if (nm == null || !nm.matches("\\d{1,9}")) continue;
                long id = Long.parseLong(nm);
                if (id == 0 || ids.containsKey(id)) continue;
                String bp = path(m, Server.rd(m, e + 8), n);
                if ("?".equals(bp)) bp = path(m, Server.rd(m, e + 12), n);
                ids.put(id, bp);
            }
            StringBuilder sb = new StringBuilder();
            for (java.util.Map.Entry<Long, String> en : ids.entrySet()) { out.add(new Row(en.getKey(), en.getValue())); if (sb.length() < 800) sb.append(en.getKey()).append('=').append(en.getValue()).append(' '); }
            if (ids.size() < 3) for (long k = 1; k <= 40; k++) if (!ids.containsKey(k)) { ids.put(k, "?"); out.add(new Row(k, "?")); }
            tableInfo = "车表 " + num + " 行，编号 " + ids.size() + " 个，名字表 " + (n == null ? "没找到" : "ok") + "：" + sb;
        }
        return out;
    }
    /** 行里找 "/Game/..." 的 FString（UTF-32），取蓝图名 */
    static String path(RandomAccessFile m, long row, Names nm) {
        if (row < 0x10000) return "?";
        // v1.84：路径多半是 FSoftObjectPath（FName），先按 FName 试
        if (nm != null) for (int o = 0; o < 0x100; o += 4) {
            long idx = Server.rd(m, row + o);
            if (idx <= 0 || idx > 0x400000 || Server.rd(m, row + o + 4) > 64) continue;
            String s = nm.name(m, (int) idx);
            if (s == null) continue;
            int k = s.lastIndexOf('/');
            String t = k >= 0 ? s.substring(k + 1) : s;
            int d = t.indexOf('.'); if (d > 0) t = t.substring(0, d);
            if (s.startsWith("/Game") || t.toLowerCase(java.util.Locale.ROOT).contains("vehicle")) return t;
        }
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
        if (l.contains("boat") || l.contains("aqua") || l.contains("pg117")) return "船";
        if (l.contains("mirado") || l.contains("coupe")) return "跑车";
        if (l.contains("rony")) return "皮卡";
        if (l.contains("scooter")) return "小绵羊";
        if (l.contains("snow")) return "雪地车";
        if (l.contains("tuk")) return "嘟嘟车";
        if (l.contains("brdm") || l.contains("armor")) return "装甲车";
        if (l.contains("glider")) return "滑翔机";
        return "?".equals(bp) ? "车" : bp;
    }

    /** v1.84：按车型分组（同一车型不同颜色合成一组，按一次换一个颜色）。名字读不到时一个编号一组 */
    static volatile boolean busy;
    static final class Group { String label; ArrayList<Long> ids = new ArrayList<>(); int at; }
    /** v1.93 车编号写死（用户：不要再读车表）。编号 = 游戏车表的行号，三张图同一张表（v1.89 读出来的）：
     *  图标名 Uaz469=吉普，dacia=轿车，boat_pg117=大船，car_002/003=吉普另外两款（敞篷/篷布，按 PUBG 吉普三款推测），buggy=蹦蹦，
     *  bike=摩托，bike_sidecart=三轮摩托，Chuan_001=快艇，car_005=面包车（推测）、car_006=跑车（v1.93 实测：刷出来是跑车）。car_007/008 编号没读到，先不放。 */
    static volatile ArrayList<Group> groups = fixed();
    static ArrayList<Group> fixed() {
        Object[][] t = { {"吉普", new long[]{1, 17, 20, 28}}, {"敞篷吉普", new long[]{4, 11, 21}}, {"篷布吉普", new long[]{5, 12, 22}},
            {"轿车", new long[]{2, 23}}, {"蹦蹦", new long[]{6, 14, 24}}, {"面包车", new long[]{10}}, {"跑车", new long[]{29, 30}},
            {"摩托", new long[]{9, 15, 25}}, {"三轮摩托", new long[]{8, 16, 26}}, {"快艇", new long[]{13, 18, 19}}, {"大船", new long[]{3, 27}} };
        ArrayList<Group> out = new ArrayList<>();
        for (Object[] r : t) { Group g = new Group(); g.label = (String) r[0]; for (long id : (long[]) r[1]) g.ids.add(id); out.add(g); }
        return out;
    }
    static String base(String bp) {
        // BP_Vehicle_UAZ_01_C → uaz；去掉颜色/皮肤后缀数字
        String t = bp.replaceAll("(?i)^BP_(Vehicle_)?", "").replaceAll("(?i)_C$", "").replaceAll("(_?\\d+)+$", "");
        return t.toLowerCase(java.util.Locale.ROOT);
    }
    static void makeGroups() {
        java.util.LinkedHashMap<String, Group> g = new java.util.LinkedHashMap<>();
        for (Row r : rows) {
            String key = "?".equals(r.name) ? "#" + r.id : base(r.name);
            Group x = g.get(key);
            if (x == null) { x = new Group(); x.label = "?".equals(r.name) ? r.id + " 号" : nice(r.name); g.put(key, x); }
            x.ids.add(r.id);
        }
        ArrayList<Group> out = new ArrayList<>(g.values());
        for (Group x : out) if (x.ids.size() > 1) x.label += "（" + x.ids.size() + " 色）";
        groups = out;
    }
    /** 读人物位置/朝向（V0），算面前 dist 厘米的点写进 buf+40..60 */
    static String ahead(float dist, float up) throws InterruptedException {
        if (!installed) return state;
        if (!call(0)) return "补丁代码没反应（进对局、落地后再按）";
        if ((buf.getInt(8)) == 0) return "还没有人物";
        float x = Float.intBitsToFloat(buf.getInt(12)), y = Float.intBitsToFloat(buf.getInt(16)), z = Float.intBitsToFloat(buf.getInt(20));
        float qx = Float.intBitsToFloat(buf.getInt(72)), qy = Float.intBitsToFloat(buf.getInt(76)), qz = Float.intBitsToFloat(buf.getInt(80)), qw = Float.intBitsToFloat(buf.getInt(84));
        double yaw = Math.atan2(2 * (qw * qz + qx * qy), 1 - 2 * (qy * qy + qz * qz));
        if (Double.isNaN(yaw)) yaw = 0;
        buf.putFloat(40, (float) (x + dist * Math.cos(yaw)));
        buf.putFloat(44, (float) (y + dist * Math.sin(yaw)));
        buf.putFloat(48, z + up);
        // 人机面朝玩家（转 180°），车和人朝向一致
        buf.putFloat(52, 0f); buf.putFloat(56, (float) Math.toDegrees(yaw) + (up < 100 ? 180f : 0f)); buf.putFloat(60, 0f);
        return null;
    }
    /** 第一次：读车表、分组，刷新菜单出按钮 */
    static String loadTable() throws Exception {
        String e = ahead(600, 150); if (e != null) return e;
        long dt = buf.getInt(36) & 0xffffffffL;
        if (dt == 0) return "这局没有车表（GameMode " + Integer.toHexString(buf.getInt(28)) + "）";
        if (rows == null || rowsDt != dt) {
            state = "刷车：正在读车表…"; GameOverlay.refreshAny();
            rows = readRows(dt); rowsDt = dt; next = 0;
            makeGroups();
            note("刷车 " + tableInfo);
        }
        return null;
    }
    /** v1.88 进对局自动读车表（用户不想每次手动点「读车表」）：等人物出生后读，读到就出车按钮 */
    static volatile int loadGen;
    static void matchLoaded() {
        final int g = ++loadGen;
        new Thread(() -> {
            try {
                Thread.sleep(4000);
                for (int i = 0; i < 40 && g == loadGen && Con.inMatch; i++) {
                    if (!busy) {
                        busy = true;
                        String e;
                        try { e = loadTable(); } finally { busy = false; }
                        if (e == null && groups != null && !groups.isEmpty()) {
                            if (state.startsWith("刷车：正在读") || state.startsWith("刷车：已装")) state = "刷车：车表已自动读好（" + groups.size() + " 种车）";
                            GameOverlay.refreshAny(); return;
                        }
                    }
                    Thread.sleep(2000);
                }
            } catch (Throwable e) { note("刷车：自动读车表出错 " + e); }
        }, "zf-vehload").start();
    }
    /** 菜单按钮：读车表（读完菜单里出现每种车的按钮） */
    public static void spawnNext() { spawnGroup(-1); }
    public static void spawnGroup(final int gi) {
        if (busy) { GameOverlay.toastAny(state); return; }
        busy = true;
        new Thread(() -> {
            try {
                String e = ahead(600, 150);
                if (e != null) { GameOverlay.toastAny(state = "刷车：" + e); return; }
                if (buf.getInt(36) == 0) { GameOverlay.toastAny(state = "刷车：这局没有车表"); return; }
                ArrayList<Group> gs = groups;
                if (gs == null || gs.isEmpty()) { GameOverlay.toastAny(state = "刷车：车表里没读到车编号（" + tableInfo.split("：")[0] + "）"); return; }
                if (gi < 0) { GameOverlay.toastAny(state = "刷车：读到 " + gs.size() + " 种车，菜单里点车名刷"); return; }
                Group g = gs.get(gi % gs.size());
                long id = g.ids.get(g.at++ % g.ids.size());
                boolean ok = call(id) && buf.getInt(0) == 0x5b03;
                String s = "刷车：" + (ok ? "已刷 " : "没反应 ") + g.label + "（编号 " + id + "）";
                note(s); state = s;
                if (!ok) GameOverlay.toastAny(s);
            } catch (Throwable e) { GameOverlay.toastAny(state = "刷车：出错 " + e); note(state); }
            finally { busy = false; GameOverlay.refreshAny(); }
        }, "zf-veh").start();
    }

    // ---------------- v1.84/v1.85 刷人机 ----------------
    // v1.84 用教学关的 ServerAddBotAt（参数 1 = GameMode 人机类数组第 1 个，难度写死 1），刷出来站着不动。
    // v1.85 改走对局自己的加人机：cave 'A<难度>' → 0x1014898(GM, 难度) 取人机类（BotDifficultMapping，没有就用 PVPBotTypeIndex）
    //   → 0x10112d0 AddOneBot(GM, 类, 难度)（难度写进 AIController+0x3ec）→ 和 AddBotAt 一样 GM vt+0x5f8 / +0x39c 生出人物、传送到位置。
    //   是 PVP 人机控制器（vt+0x344 == OnPossess 0xc1bbd8）时：标志 1 = ForceGiveAllEquipToAI 给装备；
    //   标志 2 = 行为树没跑（BrainComponent +0x324 为空）就 SwitchToBattleBehaviorTree；标志 4 = 强制切战斗树。
    //   buf：+88 类，+92 控制器，+96 人物，+100 出生岛树，+104 战斗树，+108 Brain（前），+112 难度，+120 Brain（后）。
    //   结果码 +0：5b05 GameMode 不对，5b06 没拿到人机类/没刷出来，5b07 不是 PVP 人机控制器，5b08 成功。
    public static volatile String botState = "刷人机：还没试";
    public static volatile String botInfo = "";
    static final String[] DIFF = { "", "简单", "普通", "困难" };

    /** 读一次人物位置：{x, y, z, yaw(度), GameMode, World}；没人物返回 null */
    static synchronized double[] info() throws InterruptedException {
        if (!installed || !call(0) || buf.getInt(0) != 0x5b02 || buf.getInt(8) == 0) return null;
        float x = Float.intBitsToFloat(buf.getInt(12)), y = Float.intBitsToFloat(buf.getInt(16)), z = Float.intBitsToFloat(buf.getInt(20));
        float qx = Float.intBitsToFloat(buf.getInt(72)), qy = Float.intBitsToFloat(buf.getInt(76)), qz = Float.intBitsToFloat(buf.getInt(80)), qw = Float.intBitsToFloat(buf.getInt(84));
        double yaw = Math.atan2(2 * (qw * qz + qx * qy), 1 - 2 * (qy * qy + qz * qz));
        if (Double.isNaN(yaw)) yaw = 0;
        return new double[] { x, y, z, Math.toDegrees(yaw), buf.getInt(28) & 0xffffffffL, buf.getInt(24) & 0xffffffffL };
    }
    /** 在 (x,y,z) 加一个人机，面朝 yaw。返回结果码（0 = cave 没反应） */
    static synchronized int addBot(int diff, double x, double y, double z, double yaw, int flags) throws InterruptedException {
        if (!installed) return 0;
        if (buf == null && !call(0)) return 0;
        for (int o = 88; o < 128; o += 4) buf.putInt(o, 0);
        buf.putFloat(40, (float) x); buf.putFloat(44, (float) y); buf.putFloat(48, (float) z);
        buf.putFloat(52, 0f); buf.putFloat(56, (float) yaw); buf.putFloat(60, 0f);
        buf.putInt(116, flags);
        if (!call('A', diff)) return 0;
        int code = buf.getInt(0);
        botInfo = "结果 " + Integer.toHexString(code) + " 类 " + h(88) + " 控制器 " + h(92) + " 人物 " + h(96)
            + " 出生岛树 " + h(100) + " 战斗树 " + h(104) + " 行为树前 " + h(108) + " 后 " + h(120) + " 难度 " + buf.getInt(112);
        return code;
    }
    static String h(int o) { return Integer.toHexString(buf.getInt(o)); }
    /** v1.86 对已刷出的人机控制器再给一次装备 / 切战斗树（cave 'E'） */
    static synchronized int equip(long ctrl, int flags) throws InterruptedException {
        if (!installed || ctrl < 0x10000) return 0;
        if (buf == null && !call(0)) return 0;
        for (int o = 88; o < 128; o += 4) buf.putInt(o, 0);
        buf.putInt(116, flags);
        if (!call('E', ctrl)) return 0;
        return buf.getInt(0);
    }
    /** 最后一次 A/E 的控制器地址 */
    static synchronized long lastCtrl() { return buf == null ? 0 : buf.getInt(92) & 0xffffffffL; }
    /** 装备组件情况：AutoGiveEquipComponent(+0x410) 的开关字节 +0xd5、装备条数 +0xb0；AIEquipComponent(+0x3f0)；人物 +0x2c0 */
    static String equipInfo(long ctrl) {
        if (ctrl < 0x10000) return "无控制器";
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
            long ag = Server.rd(m, ctrl + 0x410), ae = Server.rd(m, ctrl + 0x3f0), pawn = Server.rd(m, ctrl + 0x2c0);
            String s = "自动给装备组件 " + Long.toHexString(ag);
            if (ag > 0x10000) s += "（开关 " + (Server.rd(m, ag + 0xd4) >> 8 & 0xff) + "，条数 " + Server.rd(m, ag + 0xb0) + "）";
            return s + " 装备组件 " + Long.toHexString(ae) + " 人物 " + Long.toHexString(pawn) + " 行为树 " + Long.toHexString(Server.rd(m, ctrl + 0x324));
        } catch (Throwable e) { return "读不到 " + e; }
    }
    static String codeText(int c) {
        switch (c) {
            case 0x5b08: return "成功";
            case 0x5b07: return "刷出来了，但不是对局人机（不会给装备）";
            case 0x5b06: return "游戏没给人机类或没刷出来";
            case 0x5b05: return "这局的 GameMode 不支持加人机";
            case 0: return "补丁代码没反应（进对局后再按）";
            default: return "未知结果 " + Integer.toHexString(c);
        }
    }

    /** 菜单按钮：面前 8 米刷一个（当前难度） */
    public static void spawnBot() {
        if (busy) { GameOverlay.toastAny(state); return; }
        busy = true;
        new Thread(() -> {
            try {
                double[] p = info();
                if (p == null) { GameOverlay.toastAny(botState = "刷人机：还没有人物（进对局、出生后再按）"); return; }
                double r = Math.toRadians(p[3]);
                int c = addBot(Bots.diff, p[0] + 800 * Math.cos(r), p[1] + 800 * Math.sin(r), p[2] + 50, p[3] + 180, 1 | 4);
                if (c == 0x5b08 || c == 0x5b07) { Bots.spawned++; Bots.track(lastCtrl()); }
                botState = "刷人机（" + DIFF[Bots.diff] + "）：" + codeText(c);
                note(botState + "；" + botInfo);
                GameOverlay.toastAny(botState);
            } catch (Throwable e) { GameOverlay.toastAny(botState = "刷人机：出错 " + e); note(botState); }
            finally { busy = false; GameOverlay.refreshAny(); }
        }, "zf-bot").start();
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
        /** v1.82：先在 libUE4 自己的数据段里找 GNames 全局（几 MB，几秒），找不到才全内存扫 */
        static Names fast(RandomAccessFile m) throws Exception {
            ArrayList<long[]> lib = new ArrayList<>(), anon = regions();
            try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader("/proc/self/maps"))) {
                String l; boolean after = false;
                while ((l = r.readLine()) != null) {
                    String[] p = l.trim().split("\\s+");
                    String name = p.length > 5 ? p[5] : "";
                    boolean isLib = name.endsWith("libUE4.so");
                    String[] ab = p[0].split("-");
                    long s0 = Long.parseLong(ab[0], 16), e0 = Long.parseLong(ab[1], 16);
                    if (p[1].startsWith("rw") && (isLib || after && (name.isEmpty() || name.startsWith("[anon:.bss")))) lib.add(new long[] { s0, e0 });
                    after = isLib || after && name.startsWith("[anon:.bss");
                }
            }
            long[] lo = new long[anon.size()], hi = new long[anon.size()];
            for (int i = 0; i < lo.length; i++) { lo[i] = anon.get(i)[0]; hi[i] = anon.get(i)[1]; }
            long t0 = System.currentTimeMillis(), size = 0; int cand = 0;
            for (int pass = 0; pass < 2; pass++) for (long[] g : lib) {
                int n = (int) (g[1] - g[0]); size += pass == 0 ? n : 0;
                byte[] bb = new byte[n];
                try { m.seek(g[0]); m.readFully(bb); } catch (Throwable e) { continue; }
                int[] w = new int[n / 4];
                ByteBuffer.wrap(bb).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().get(w);
                for (int i = 0; i < w.length; i++) {
                    long v = w[i] & 0xffffffffL;
                    if (v < 0x10000 || (v & 3) != 0 || !in(lo, hi, v)) continue;
                    cand++;
                    // pass 0：这个字本身就是块 0 指针（块表就在 libUE4 数据段里）；pass 1：这个字指向块表
                    long chunks = pass == 0 ? g[0] + 4L * i : v;
                    long c0 = pass == 0 ? v : Server.rd(m, v);
                    if (pass == 1 && (c0 < 0x10000 || !in(lo, hi, c0))) continue;
                    Names r = check(m, c0, chunks);
                    if (r != null) { note("刷车 名字表在 libUE4 数据段找到 块表=" + Long.toHexString(chunks) + " 方式" + pass + "（" + (System.currentTimeMillis() - t0) + " ms）"); return r; }
                }
            }
            note("刷车 libUE4 数据段没找到名字表（" + size / 1024 + " KB，候选 " + cand + "，" + (System.currentTimeMillis() - t0) + " ms）");
            return null;
        }
        static boolean in(long[] lo, long[] hi, long v) {
            int a = 0, b = lo.length - 1;
            while (a <= b) { int c = (a + b) >>> 1; if (v < lo[c]) b = c - 1; else if (v >= hi[c]) a = c + 1; else return true; }
            return false;
        }
        static Names check(RandomAccessFile m, long c0, long chunks) {
            long e0 = Server.rd(m, c0);
            if (e0 < 0x10000 || (e0 & 3) != 0) return null;
            if (!"None".equals(ascii(m, e0 + 8, 8)) || Server.rd(m, e0) != 0) return null;
            long e1 = Server.rd(m, c0 + 4);
            if (e1 < 0x10000 || !"ByteProperty".equals(ascii(m, e1 + 8, 16))) return null;
            long f1 = Server.rd(m, e1);
            if (f1 != 1 && f1 != 2) return null;
            Names r = new Names(); r.chunks = chunks; r.shift = f1 == 2;
            return r;
        }
        static synchronized Names get() throws Exception {
            if (cached != null) return cached;
            try (RandomAccessFile mm = new RandomAccessFile("/proc/self/mem", "r")) {
                Names f = fast(mm);
                if (f != null) return cached = f;
            }
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
                    if (((done >> 20) & 63) == 0) { state = "刷车：读名字表 1/3 " + done * 100 / Math.max(1, total) + "%"; GameOverlay.refreshAny(); }
                }
                note("刷车 名字表候选 None " + none.n + " ByteProperty " + byteP.n + "（" + (System.currentTimeMillis() - t0) + " ms，" + total / 1048576 + " MB）");
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
                    if (((done >> 20) & 63) == 0) { state = "刷车：读名字表 2/3 " + done * 100 / Math.max(1, total) + "%"; GameOverlay.refreshAny(); }
                }
                note("刷车 名字块0=" + Long.toHexString(chunk0) + "（" + (System.currentTimeMillis() - t0) + " ms）");
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
                    if (((done >> 20) & 63) == 0) { state = "刷车：读名字表 3/3 " + done * 100 / Math.max(1, total) + "%"; GameOverlay.refreshAny(); }
                }
                if (r.chunks == 0) r.chunks = -chunk0;   // 没找到块表：只能查前 16384 个
                note("刷车 名字块表=" + Long.toHexString(r.chunks) + " 移位=" + r.shift + "（" + (System.currentTimeMillis() - t0) + " ms）");
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
            return ascii(m, e + 8, 200);
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
    static void note(String s) { LogCap.note(s); Server.mark(s); }
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
