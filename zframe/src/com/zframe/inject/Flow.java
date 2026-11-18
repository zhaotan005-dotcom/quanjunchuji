package com.zframe.inject;

import android.app.Activity;
import java.io.RandomAccessFile;
import java.util.Random;

/**
 * 毒圈、航线改回全军出击正式服节奏（用户选：以全军出击为准；全军出击攻略的 10 分钟 / 5 分 40 秒 = 端游 2018 表）。
 *
 * v1.87 实测没用，诊断看出两件事：
 *   1. 这个单机版的毒圈只有 2 圈：InitArea[0] 半径 1500 米、等 900 秒、缩 180 秒；InitArea[1] 半径 0.1（直接缩没）。
 *   2. InitArea 在地图加载后约 1.3 秒就跑完了（OnRestrictedAreaStart），比 v1.87 动手早，所以改 Default 数组没用。
 * v1.88 改法：InitArea 跑完以后、飞机起飞（阶段 4）之前，直接换掉本局数组 RestrictedAreaSystem+0x354（TArray，每项 0x54）：
 *   用游戏自己的 FMemory::Malloc（cave 'M'）要 9 项的内存，第 1 圈用游戏已经定好的中心和半径（航线也是按它算的），
 *   后面 7 圈半径按端游比例缩，中心在上一圈里随机（保证下一圈在上一圈里面），第 9 项半径 0.1 = 最后缩没；
 *   等待/缩圈时间按端游表（第一圈等待加上端游上飞机到出圈的 2 分钟 / 雨林 1.5 分钟）。旧的那块小内存不释放（几百字节）。
 *   每项：+0 CenterPointOffsetMax，+4 中心 xyz，+0x10 半径 cm，+0x14 等待 s，+0x18 缩圈 s，+0x1c 伤害（int），+0x20 伤害间隔；其余字节照抄游戏的第 1 项。
 * 航线：飞机对象（AirTransporterSystem+0x304 列表）的 StartLoc 0x300 / EndLoc 0x30c / Dir 0x318 / TotalAirlineDist 0x324 /
 *   StartJumpLoc 0x328 / EndJumpLoc 0x334 / TotalJumpDist 0x340，加载时就算好了（这局 4x4 只有约 1.9 公里）。
 *   起飞前把航线以原中点为中心、沿原方向拉长到整张图的边长（8x8：8 公里，4x4：4 公里），能跳的范围同样拉长。
 * 系统对象怎么找：0xd96028 = GetWorld()+0x98 → +0x348 表（16 字节/项：+4 对象数组、+8 个数），表里是本局的各个「系统」，
 *   按字段长相认：毒圈系统 +0x354 数组像毒圈；航线系统 +0x304 列表里的对象起终点、总长对得上。
 */
public class Flow {
    public static volatile boolean on = true;
    public static volatile String state = "毒圈：等进对局", air = "";
    static volatile int gen;
    // 半径(cm)、等待(s)、缩圈(s)、伤害
    static final float[][] BIG = { {228235, 420, 300, 1}, {148355, 200, 140, 1}, {74175, 150, 90, 1}, {37090, 120, 60, 1},
        {18545, 120, 40, 3}, {9270, 90, 30, 5}, {4635, 90, 30, 7}, {2320, 60, 30, 9} };
    static final float[][] SMALL = { {142755, 210, 240, 1}, {99930, 120, 120, 1}, {59955, 90, 120, 1}, {29980, 45, 135, 1},
        {14990, 90, 15, 3}, {7495, 45, 60, 5}, {3745, 45, 40, 7}, {1875, 30, 55, 9} };
    static final int ES = 0x54;
    /** v2.00：飞机高度（cm）。这个单机版航线 Z=20000（200 米），跳伞组件按「离地距离 / 1500 米」算比例，低于 0.23 才强制开伞，
     *  200 米跳下去离地太近、开伞动画没放完就落地 → 摔死。游戏组件默认 SkydivingStartHeight=150000（构造函数 0x1121ed8），
     *  所以把航线（和跳伞段）抬到 1000 米：离地约 345 米时自动开伞。 */
    static final float PLANE_Z = 100000f;
    /** v2.00：开局整图圈的半径 = 边长 × WHOLE（以前 0.75，用户：坐飞机时地图上还是有圈、人在圈外） */
    static final float WHOLE = 1.5f;

    public static volatile String last = "";
    /** v1.94：游戏日志里的本局航线 {StartLoc, EndLoc, StartJumpLoc, EndJumpLoc}（xyz），按值在内存里找 */
    static volatile float[][] logAir;
    static final java.util.regex.Pattern LOC = java.util.regex.Pattern.compile("(StartLoc|EndLoc|StartJumpLoc|EndJumpLoc): X=([-0-9.]+), Y=([-0-9.]+), Z=([-0-9.]+)");
    static void airLog(String l) {
        float[][] r = new float[4][];
        java.util.regex.Matcher mt = LOC.matcher(l);
        while (mt.find()) {
            int k = java.util.Arrays.asList("StartLoc", "EndLoc", "StartJumpLoc", "EndJumpLoc").indexOf(mt.group(1));
            if (k >= 0 && r[k] == null) r[k] = new float[] { Float.parseFloat(mt.group(2)), Float.parseFloat(mt.group(3)), Float.parseFloat(mt.group(4)) };
        }
        if (r[0] != null && r[1] != null) logAir = r;
    }
    static void newMatch() { Mods.write(ZF, ZF_O, ZF_N, false); logAir = null; if (!state.startsWith("毒圈：等进对局")) last = state + "。" + air; gen++; state = on ? "毒圈：等进对局" : "毒圈：不改（用游戏自带）"; air = ""; }

    static void matchLoaded(final String map) {
        final int g = ++gen;
        if (!on) { state = "毒圈：不改（用游戏自带）"; return; }
        new Thread(() -> {
            try { run(g, map); } catch (Throwable e) { state = "毒圈：出错 " + e; Veh.note(state); }
            GameOverlay.refreshAny();
        }, "zf-flow").start();
    }

    static void run(int g, String map) throws Exception {
        boolean small = map != null && (map.contains("Savage") || map.contains("4x4"));
        float[][] tb = small ? SMALL : BIG;
        float side = small ? 408000f : 816000f;   // 地图边长（原点在地图正中，「传送到地图中间」BugItGo 0 0 也是这么用的）
        boolean circleDone = false, airDone = false; int ph = -1; sideNow = side;
        Thread.sleep(3000);
        if (on) { if (nat) Veh.note("毒圈换圈用游戏自己的逻辑（还原 0xd97d68）：" + Mods.write(Mods.POOL, Mods.POOL_O, Mods.POOL_N, false)); else Veh.note("毒圈不按玩家位置定圈心：" + Mods.write(Mods.POOL, Mods.POOL_O, Mods.POOL_N, true)); }
        for (int i = 0; i < 300 && g == gen && Con.inMatch; i++) {
            double[] p = Veh.info();
            if (p == null) { state = "毒圈：等人物出生"; Thread.sleep(1000); continue; }
            ph = Bots.readPhase((long) p[4]);
            if (ph >= 4) { airAfter(g, p); break; }   // 毒圈开始了不再动；飞机可能这时才生出来，航线再找 25 秒
            long[] sys = systems((long) p[5]);
            if (circleDone && airDone) { Thread.sleep(500); continue; }   // 都改好了，等起飞再找一次飞机
            if (sys == null) { state = "毒圈：找不到系统表"; Thread.sleep(1000); continue; }
            try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "rw")) {
                if (!circleDone) {
                    String r = circle(m, sys, tb, side, 120);   // 起飞 1 分钟后才出第一个圈
                    if (r != null) { circleDone = true; state = "毒圈（" + (small ? "4x4" : "8x8") + "）：" + r; Veh.note(state); }
                    else state = "毒圈：等游戏算好本局的圈";
                }
                if (!airDone) {
                    String r = airline(m, sys, side);
                    if (r != null) { airDone = true; air = "航线：" + r; Veh.note(air); }
                }
            }
            GameOverlay.refreshAny();
            Thread.sleep(1000);
        }
        if (!circleDone) { state = state + "（没改成，阶段 " + ph + "）"; Veh.note(state); }
        if (!airDone && air.isEmpty()) { air = "航线：没找到飞机（阶段 " + ph + "）"; Veh.note(air); }
    }

    /** World+0x98 → +0x348 表里的全部对象 */
    static long[] systems(long world) {
        if (world < 0x10000) return null;
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
            long a = Server.rd(m, world + 0x98);
            if (a < 0x10000) return null;
            long arr = Server.rd(m, a + 0x348), n = Server.rd(m, a + 0x34c);
            if (arr < 0x10000 || n <= 0 || n > 64) return null;
            java.util.ArrayList<Long> out = new java.util.ArrayList<>();
            for (int i = 0; i < n; i++) {
                long data = Server.rd(m, arr + 16L * i + 4), cnt = Server.rd(m, arr + 16L * i + 8);
                if (data < 0x10000 || cnt <= 0 || cnt > 8) continue;
                for (int k = 0; k < cnt; k++) { long o = Server.rd(m, data + 4L * k); if (o >= 0x10000 && !out.contains(o)) out.add(o); }
            }
            long[] r = new long[out.size()]; for (int i = 0; i < r.length; i++) r[i] = out.get(i);
            return r.length == 0 ? null : r;
        } catch (Throwable e) { return null; }
    }

    /** 换本局毒圈数组；没找到 / 还没算好返回 null */
    static String circle(RandomAccessFile m, long[] sys, float[][] tb, float side, float firstWait) throws Exception {
        for (long o : sys) {
            long ta = o + 0x354;
            long d = Server.rd(m, ta), n = Server.rd(m, ta + 4), mx = Server.rd(m, ta + 8);
            if (d < 0x10000 || n < 1 || n > 16 || mx < n || mx > 64) continue;
            float r0 = f(m, d + 0x10), w0 = f(m, d + 0x14), s0 = f(m, d + 0x18);
            float cx = f(m, d + 4), cy = f(m, d + 8), cz = f(m, d + 12);
            if (!(r0 > 10000 && r0 < 2e6f && w0 >= 0 && w0 < 7200 && s0 >= 0 && s0 < 7200 && Math.abs(cx) < 2e6f && Math.abs(cy) < 2e6f)) continue;
            if (n >= tb.length + 3 && Math.abs(r0 - side * WHOLE) < 1f) return "游戏自己按正式服表生成（" + rows(m, d, n) + "）";
            String before = rows(m, d, n);
            byte[] first = new byte[ES], last = new byte[ES];
            m.seek(d); m.readFully(first);
            m.seek(d + ES * (n - 1)); m.readFully(last);
            int cnt = tb.length + 3;
            byte[] nb = build(first, last, tb, side, firstWait, cz, new Random());
            long mem = Veh.malloc(cnt * ES);
            if (mem < 0x10000) return "要内存失败，没改（原来 " + before + "）";
            m.seek(mem); m.write(nb);
            // 先写元素，再换指针，最后改个数
            wi(m, ta + 8, cnt); wi(m, ta, (int) mem); wi(m, ta + 4, cnt);
            // v2.71：开局蓝圈（+0x3b0）和换圈函数第一次用的「上一圈」半径（+0x3c0，配合 ZF 补丁）都改成第一项（整图），地图上就看不到圈
            float whole = f(m, mem + 0x10);
            wf(m, o + 0x3c0, whole); wf(m, o + 0x3b0, whole);
            String rf = Mods.write(ZF, ZF_O, ZF_N, true);
            return "改好 " + cnt + " 圈：" + rows(m, mem, cnt) + "（原来 " + before + "），开局蓝圈 " + Math.round(whole / 100) + " 米" + ("ok".equals(rf) ? "" : "（补丁 " + rf + "）");
        }
        return null;
    }

    /**
     * v1.93 生成新数组（用户：进去就已经刷过一圈了）：
     *   第 0 项盖住整张图（中心在地图正中，半径 = 边长 × 0.75，地图角落也在里面），开局先等 firstWait 秒，不显示圈；
     *   第 1 项起是正式服的圈：第一个圈中心在地图中间一带随机（整个圈都在图里），后面每圈在上一圈里随机；最后一项半径 0.1 = 缩没。
     *   不再用游戏自己定的第一圈（这个单机版的第一圈经常在地图边上甚至海上）。
     */
    static byte[] build(byte[] first, byte[] last, float[][] tb, float side, float firstWait, float cz, Random rnd) {
        // v1.95：v1.94 实测：坐飞机时整张图就已经缩成第一圈（人在圈外）。原因：游戏的一项 = 「当前圈，等 W 秒，再用 S 秒缩到下一项」，
        //   以前把第 k 圈自己的等/缩时间放在第 k 项上 → 第 0 项（整图，等 60 缩 1）1 秒就缩成了第一圈。现在时间往前挪一项：
        //   第 0 项 整图 等 firstWait 缩 1（白圈 = 第 1 项 = 整图，看不见）
        //   第 1 项 整图 等/缩 = 第一圈的时间（这时才显示第一圈白圈）
        //   第 i+2 项 = 第 i 圈，等/缩 = 第 i+1 圈的时间；最后一项半径 0.1。
        //   第一圈中心只在地图正中 ±(边长/2-半径)×0.3 内，不出岛。
        int cnt = tb.length + 3;
        java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(cnt * ES).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        float half = side / 2, pr = side * WHOLE, px = 0, py = 0;
        for (int i = 0; i < cnt; i++) {
            int o = i * ES;
            b.position(o); b.put(i < cnt - 1 ? first : last);
            float r, w, s, x, y; int dmg;
            if (i == 0) { r = side * WHOLE; w = firstWait; s = 1; dmg = 1; x = 0; y = 0; }
            else if (i == 1) { r = side * WHOLE; w = tb[0][1]; s = tb[0][2]; dmg = 1; x = 0; y = 0; }
            else {
                int k = i - 2;   // 第 k 圈（k == tb.length 是最后 0.1 米）
                if (k < tb.length) { r = tb[k][0]; dmg = (int) tb[k][3]; } else { r = 0.1f; dmg = 11; }
                if (k + 1 < tb.length) { w = tb[k + 1][1]; s = tb[k + 1][2]; } else if (k < tb.length) { w = 180; s = 15; } else { w = 9999; s = 1; }
                double room = i == 2 ? Math.max(0, (half - r) * 0.3) : Math.max(0, pr - r);
                double ang = rnd.nextDouble() * Math.PI * 2, dist = Math.sqrt(rnd.nextDouble()) * room;
                x = (float) ((i == 2 ? 0 : px) + Math.cos(ang) * dist); y = (float) ((i == 2 ? 0 : py) + Math.sin(ang) * dist);
            }
            b.putFloat(o, 0f);   // v2.01 CenterPointOffsetMax = 0：游戏换圈时不再随机挪圈心
            b.putFloat(o + 4, x); b.putFloat(o + 8, y); b.putFloat(o + 12, cz);
            b.putFloat(o + 0x10, r); b.putFloat(o + 0x14, w); b.putFloat(o + 0x18, s); b.putInt(o + 0x1c, dmg);
            px = x; py = y; pr = r;
        }
        return b.array();
    }

    /** v1.94：起飞后接着找航线（飞机对象起飞时才生），每 0.2 秒一次，找 25 秒 */
    static void airAfter(int g, double[] p) throws Exception {
        for (int i = 0; i < 125 && g == gen && Con.inMatch; i++) {
            long[] sys = systems((long) p[5]);
            if (sys != null) try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "rw")) {
                String r = airline(m, sys, sideNow);
                if (r != null) { air = (air.isEmpty() ? "" : air + "；") + "起飞后又找到一次：" + r; Veh.note(air); return; }
            }
            Thread.sleep(200);
        }
    }
    static volatile float sideNow = 408000f;
    /** v2.70 用户：要在航线内才能跳伞，跳伞键被提前打开了。以前跳伞段 = 整条航线（一起飞就能跳，海上也能跳）。
     *  现在跳伞段取航线的 JUMP_A ~ JUMP_B（起点后约 18%，已经进图了；到 92% 强制跳）。 */
    static final double JUMP_A = 0.18, JUMP_B = 0.92;
    static float lerp(float a, float b, double t) { return (float) (a + (b - a) * t); }

    /** v1.94：按日志里的起点/终点坐标值，在各系统对象和它们指向的对象里找（不靠猜偏移），找到就改；没找到返回 null */
    static String airByValue(RandomAccessFile m, long[] sys, float side) throws Exception {
        float[][] la = logAir; if (la == null) return null;
        int s0 = Float.floatToRawIntBits(la[0][0]), s1 = Float.floatToRawIntBits(la[0][1]), e0 = Float.floatToRawIntBits(la[1][0]), e1 = Float.floatToRawIntBits(la[1][1]);
        java.util.ArrayList<Long> objs = new java.util.ArrayList<>();
        for (long o : sys) { objs.add(o); byte[] b = read(m, o, 0x800); if (b == null) continue;
            java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(b).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            for (int k = 0x40; k + 8 <= b.length; k += 4) {   // TArray 里的对象（飞机列表）和直接指针
                long d = bb.getInt(k) & 0xffffffffL, n = bb.getInt(k + 4) & 0xffffffffL;
                if (d >= 0x10000 && n >= 1 && n <= 4) for (int x = 0; x < n; x++) { long t = Server.rd(m, d + 4L * x); if (t >= 0x10000 && !objs.contains(t)) objs.add(t); }
            }
        }
        StringBuilder sb = new StringBuilder(); int hits = 0; boolean jumpSet = false;
        Random rnd = new Random();
        double ang = rnd.nextDouble() * Math.PI * 2, dx = Math.cos(ang), dy = Math.sin(ang), off = (rnd.nextDouble() * 2 - 1) * side * 0.1;
        double mx = -dy * off, my = dx * off, h = side * 0.55;
        float nsx = (float) (mx - dx * h), nsy = (float) (my - dy * h), nex = (float) (mx + dx * h), ney = (float) (my + dy * h);
        for (long o : objs) {
            byte[] b = read(m, o, 0x800); if (b == null) continue;
            java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(b).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            for (int k = 0; k + 24 <= b.length; k += 4) {
                if (bb.getInt(k) != s0 || bb.getInt(k + 4) != s1 || bb.getInt(k + 12) != e0 || bb.getInt(k + 16) != e1) continue;
                long a = o + k;
                wf(m, a, nsx); wf(m, a + 4, nsy); wf(m, a + 12, nex); wf(m, a + 16, ney); wf(m, a + 8, PLANE_Z); wf(m, a + 20, PLANE_Z);
                if (k + 0x2c <= b.length && Math.abs(Math.hypot(bb.getFloat(k + 0x18), bb.getFloat(k + 0x1c)) - 1) < 0.01) { wf(m, a + 0x18, (float) dx); wf(m, a + 0x1c, (float) dy); }
                double len0 = Math.hypot(la[1][0] - la[0][0], la[1][1] - la[0][1]);
                if (k + 0x28 <= b.length && Math.abs(bb.getFloat(k + 0x24) - len0) < Math.max(100, len0 * 0.02)) wf(m, a + 0x24, (float) (2 * h));
                // 跳伞段：起点后 0x80 字节内找 StartJumpLoc / EndJumpLoc 的值
                if (la[2] != null && la[3] != null) for (int j = k + 12; j + 24 <= Math.min(b.length, k + 0x80); j += 4) {
                    if (bb.getInt(j) == Float.floatToRawIntBits(la[2][0]) && bb.getInt(j + 4) == Float.floatToRawIntBits(la[2][1]) && bb.getInt(j + 12) == Float.floatToRawIntBits(la[3][0]) && bb.getInt(j + 16) == Float.floatToRawIntBits(la[3][1]) && j != k) {
                        wf(m, o + j, lerp(nsx, nex, JUMP_A)); wf(m, o + j + 4, lerp(nsy, ney, JUMP_A)); wf(m, o + j + 12, lerp(nsx, nex, JUMP_B)); wf(m, o + j + 16, lerp(nsy, ney, JUMP_B)); wf(m, o + j + 8, PLANE_Z); wf(m, o + j + 20, PLANE_Z);
                        double jl = Math.hypot(la[3][0] - la[2][0], la[3][1] - la[2][1]);
                        if (j + 0x1c <= b.length && Math.abs(bb.getFloat(j + 0x18) - jl) < Math.max(100, jl * 0.02)) wf(m, o + j + 0x18, (float) (2 * h * (JUMP_B - JUMP_A)));
                        jumpSet = true;
                        break;
                    }
                }
                hits++; sb.append(Long.toHexString(o)).append('+').append(Integer.toHexString(k)).append(' ');
            }
        }
        if (hits == 0) return null;
        return Math.round(Math.hypot(la[1][0] - la[0][0], la[1][1] - la[0][1]) / 100) + " 米 → " + Math.round(2 * h / 100) + " 米，高 " + Math.round(PLANE_Z / 100)  + " 米，穿过地图中间，跳伞段 " + (jumpSet ? Math.round(JUMP_A * 100) + "%~" + Math.round(JUMP_B * 100) + "%" : "没找到") + "（改了 " + hits + " 处：" + sb.toString().trim() + "）";
    }
    static byte[] read(RandomAccessFile m, long a, int n) { try { byte[] b = new byte[n]; m.seek(a); m.readFully(b); return b; } catch (Throwable e) { return null; } }

    /** 航线拉长；没找到飞机返回 null */
    static String airline(RandomAccessFile m, long[] sys, float side) throws Exception {
        sideNow = side;
        String bv = airByValue(m, sys, side); if (bv != null) return bv;
        for (long o : sys) {
            long d = Server.rd(m, o + 0x304), n = Server.rd(m, o + 0x308);
            if (d < 0x10000 || n < 1 || n > 4) continue;
            StringBuilder sb = new StringBuilder(); int done = 0, seen = 0;
            for (int i = 0; i < n; i++) {
                long t = Server.rd(m, d + 4L * i);
                if (t < 0x10000) continue;
                float sx = f(m, t + 0x300), sy = f(m, t + 0x304), sz = f(m, t + 0x308), ex = f(m, t + 0x30c), ey = f(m, t + 0x310), ez = f(m, t + 0x314);
                float tot = f(m, t + 0x324);
                double len = Math.hypot(ex - sx, ey - sy);
                if (!(len > 1000 && Math.abs(len - tot) < Math.max(100, tot * 0.02) && Math.abs(sz - ez) < 1 && Math.abs(sx) < 2e6f && Math.abs(ey) < 2e6f)) continue;
                seen++;
                float speed = f(m, t + 0x2b8);
                if (Math.abs(len - side * 1.1) < 100 && Math.hypot((sx + ex) / 2, (sy + ey) / 2) < side * 0.15) { sb.append("已经改过 "); done++; continue; }
                // v1.93：航线穿过地图中间（离正中不超过边长 10%），方向随机，从这边图边飞到那边图边（用户：每局航线是地图中间一个很大的跨度）
                Random rnd = new Random();
                double ang = rnd.nextDouble() * Math.PI * 2, dx = Math.cos(ang), dy = Math.sin(ang), off = (rnd.nextDouble() * 2 - 1) * side * 0.1;
                double mx = -dy * off, my = dx * off, h = side * 0.55;
                float nsx = (float) (mx - dx * h), nsy = (float) (my - dy * h), nex = (float) (mx + dx * h), ney = (float) (my + dy * h);
                wf(m, t + 0x300, nsx); wf(m, t + 0x304, nsy); wf(m, t + 0x30c, nex); wf(m, t + 0x310, ney); wf(m, t + 0x308, PLANE_Z); wf(m, t + 0x314, PLANE_Z);
                wf(m, t + 0x318, (float) dx); wf(m, t + 0x31c, (float) dy);   // Dir
                wf(m, t + 0x324, (float) (2 * h));
                // 能跳的范围也跟着拉长（原来是不是和航线同样两端都记下来）
                float jsx = f(m, t + 0x328), jsy = f(m, t + 0x32c), jex = f(m, t + 0x334), jey = f(m, t + 0x338);
                wf(m, t + 0x328, lerp(nsx, nex, JUMP_A)); wf(m, t + 0x32c, lerp(nsy, ney, JUMP_A)); wf(m, t + 0x334, lerp(nsx, nex, JUMP_B)); wf(m, t + 0x338, lerp(nsy, ney, JUMP_B)); wf(m, t + 0x330, PLANE_Z); wf(m, t + 0x33c, PLANE_Z);
                wf(m, t + 0x340, (float) (2 * h * (JUMP_B - JUMP_A)));
                sb.append(Math.round(len / 100)).append(" 米 → ").append(Math.round(side / 100)).append(" 米（起 ").append(Math.round(sx)).append(',').append(Math.round(sy))
                  .append(" 终 ").append(Math.round(ex)).append(',').append(Math.round(ey)).append(" 原跳伞段 ").append(Math.round(jsx)).append(',').append(Math.round(jsy))
                  .append('~').append(Math.round(jex)).append(',').append(Math.round(jey)).append(" 速度 ").append(Math.round(speed)).append("）");
                done++;
            }
            if (seen > 0) return sb.toString();
        }
        return null;
    }

    static String rows(RandomAccessFile m, long d, long n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n && i < 10; i++)
            sb.append(Math.round(f(m, d + ES * i + 0x10) / 100)).append("m ").append(Math.round(f(m, d + ES * i + 0x14))).append('/').append(Math.round(f(m, d + ES * i + 0x18))).append(i + 1 < n ? ", " : "");
        return sb.toString();
    }

    static float f(RandomAccessFile m, long a) { return Float.intBitsToFloat((int) Server.rd(m, a)); }
    static void wf(RandomAccessFile m, long a, float v) throws java.io.IOException { wi(m, a, Float.floatToRawIntBits(v)); }
    static void wi(RandomAccessFile m, long a, int b) throws java.io.IOException {
        m.seek(a); m.write(new byte[] { (byte) b, (byte) (b >> 8), (byte) (b >> 16), (byte) (b >> 24) });
    }

    // ---------------- v2.45 毒圈从底层改：游戏自己的 InitArea 直接读正式服的表 ----------------
    // 用户：别在游戏生成以后再改一遍，改底层。InitArea(0xd9777c) 把 DefaultRestrictedAreaInfos(+0x33c/+0x340) 逐项拷到本局数组，
    //   圈心由游戏自己算（第一圈：预设点/FirstCenterPoint；之后 RandomCenter 0xd96364 在上一圈里随机并检查落点）。
    //   这个单机版的表只有 2 项（1500 米、等 900 秒），所以开局就有圈、位置乱。
    // 做法：0xd977ec 跳到 cave A：把 +0x33c/+0x340/+0x344 换成我们的表（每项 +0x20 以后的字节照抄游戏原表），
    //   函数出口 0xd97b6c 跳到 cave B：换回原来的数组（游戏的内存归游戏管，我们的表不会被它释放）。
    //   表在进图（Browse）时按地图写好；InitArea 之后游戏自己的换圈逻辑照常跑（前一半圈按玩家位置定圈心）。
    //   zone_cave.py 生成并用 unicorn 测过。
    static final long ZA = 0xd977ecL, ZB = 0xd97b6cL, ZS = Server.SLACK + 0x990;
    static final String ZA_O = "4c5394e560008de5", ZB_O = "1cd04be2f08fbde8";
    static final String ZCA = "60008de52cc04fe200109ce5000051e32700000a08209ce5000052e32400001a3c2394e5400394e5000052e32000000a010050e31e0000ba08408ce50c208ce510008ce5443394e514308ce5010040e25430a0e3902320e004309ce5c0002de90050a0e3030055e10d0000aa016043e2060055e100e0a00102e0a0115460a0e395162ce02070a0e307609ee707608ce7047087e2540057e3faffffba015085e2efffffeac000bde83c1384e5403384e5443384e54c5394e504f01fe5", ZCB = "e8c04fe208109ce5000051e30c00000a0c209ce53c2381e510209ce5402381e514209ce5442381e50020a0e308208ce5582391e5000052e3010000dac02391e5b02381e51cd04be2f08fbde8";
    // v2.70 用户：开局不应该有毒圈。开局的蓝圈 = 地图对角线一半(+0x330/+0x334 求 hypot，约 4327 米，圈心是第一项的圈心) → 地图上看得到。
    //   cave B 出口把 +0x3b0 写成第一项半径 +0x3c0；换圈函数 0xd974d4 第一次（当前 -1）也改成读 +0x3c0（vldr s0,[r4,#0x3c0]; b 0xd975a8）。
    static final long ZF = 0xd97578L; static final String ZF_O = "cc0a94edcd1a94ed", ZF_N = "f00a94ed090000ea";
    static volatile boolean nat; public static volatile String natState = "";
    static java.nio.ByteBuffer tbuf; static long taddr;
    static synchronized String natInstall() {
        if (nat) return "ok";
        long b = Mods.base(); if (b == 0) return "找不到游戏库";
        String caveA = ZCA + Server.w32((int) (b + 0xd977f4L));
        byte[] body = Patches.hex("0000000000000000000000000000000000000000000000000000000000000000" + caveA + ZCB);
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "rw")) {
            byte[] cur = new byte[body.length]; m.seek(b + ZS); m.readFully(cur);
            boolean zero = true; for (byte x : cur) if (x != 0) { zero = false; break; }
            byte[] curCode = java.util.Arrays.copyOfRange(cur, 32, cur.length), want = java.util.Arrays.copyOfRange(body, 32, body.length);
            if (!zero && !java.util.Arrays.equals(curCode, want)) return "空位被占 " + Patches.hexs(java.util.Arrays.copyOf(cur, 16));
            if (zero) { m.seek(b + ZS); m.write(body); }
        } catch (Throwable e) { return "出错 " + e.getMessage(); }
        String ra = Mods.write(ZA, ZA_O, "04f01fe5" + Server.w32((int) (b + ZS + 0x20)), true);
        if (!"ok".equals(ra)) return "入口 " + ra;
        String rb = Mods.write(ZB, ZB_O, "04f01fe5" + Server.w32((int) (b + ZS + 0xe0)), true);
        if (!"ok".equals(rb)) { Mods.write(ZA, ZA_O, "04f01fe5" + Server.w32((int) (b + ZS + 0x20)), false); return "出口 " + rb; }

        nat = true; return "ok";
    }
    /** 进对局地图（Browse）时调：按地图写好本局的表（游戏 InitArea 会用） */
    static void natPrepare(String l) {
        try {
            if (l.contains("FrontEnd") || l.contains("RoleHall") || !l.contains("/Game/Maps/") || Lan.role == 2) return;
            String r = natInstall();
            if (!"ok".equals(r)) { natState = "毒圈底层：没装上（" + r + "）"; Veh.note(natState); return; }
            boolean small = l.contains("Savage") || l.contains("4x4");
            float[][] tb = small ? SMALL : BIG; float side = small ? 408000f : 816000f;
            int cnt = tb.length + 3;
            if (tbuf == null) { tbuf = java.nio.ByteBuffer.allocateDirect(16 * ES).order(java.nio.ByteOrder.LITTLE_ENDIAN); taddr = GameOverlay.bufAddr(tbuf); }
            if (taddr <= 0 || taddr > 0xffffffffL) { natState = "毒圈底层：表内存地址不对"; return; }
            byte[] t = table(tb, side, small ? 180 : 240);
            tbuf.position(0); tbuf.put(t);
            long b = Mods.base();
            try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "rw")) {
                wi(m, b + ZS + 8, 0); wi(m, b + ZS + 4, cnt); wi(m, b + ZS, (int) taddr);
            }
            natState = "毒圈底层：已装，本局 " + (small ? "4x4" : "8x8") + " " + cnt + " 项（游戏自己定圈心）";
            Veh.note(natState);
        } catch (Throwable e) { natState = "毒圈底层：出错 " + e; }
    }
    /** 正式服节奏的表（圈心留给游戏算；+0 CenterPointOffsetMax = 能离上一圈中心多远） */
    static byte[] table(float[][] tb, float side, float firstWait) {
        int cnt = tb.length + 3;
        java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(cnt * ES).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        float whole = side * WHOLE, pr = whole;
        for (int i = 0; i < cnt; i++) {
            int o = i * ES; float r, w, s, off; int dmg;
            if (i == 0) { r = whole; w = firstWait; s = 1; dmg = 1; off = 0; }
            else if (i == 1) { r = whole; w = tb[0][1]; s = tb[0][2]; dmg = 1; off = 0; }
            else {
                int k = i - 2;
                if (k < tb.length) { r = tb[k][0]; dmg = (int) tb[k][3]; } else { r = 0.1f; dmg = 11; }
                if (k + 1 < tb.length) { w = tb[k + 1][1]; s = tb[k + 1][2]; } else if (k < tb.length) { w = 180; s = 15; } else { w = 9999; s = 1; }
                off = i == 2 ? Math.max(0, side / 2 - r) : Math.max(0, pr - r);
            }
            b.putFloat(o, off); b.putFloat(o + 0x10, r); b.putFloat(o + 0x14, w); b.putFloat(o + 0x18, s); b.putInt(o + 0x1c, dmg);
            pr = r;
        }
        return b.array();
    }
}
