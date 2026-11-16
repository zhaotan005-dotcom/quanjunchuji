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

    public static volatile String last = "";
    static void newMatch() { if (!state.startsWith("毒圈：等进对局")) last = state + "。" + air; gen++; state = on ? "毒圈：等进对局" : "毒圈：不改（用游戏自带）"; air = ""; }

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
        boolean circleDone = false, airDone = false; int ph = -1;
        Thread.sleep(3000);
        for (int i = 0; i < 120 && g == gen && Con.inMatch && !(circleDone && airDone); i++) {
            double[] p = Veh.info();
            if (p == null) { state = "毒圈：等人物出生"; Thread.sleep(1000); continue; }
            ph = Bots.readPhase((long) p[4]);
            if (ph >= 4) break;                         // 飞机已经起飞 / 毒圈开始了，不再动
            long[] sys = systems((long) p[5]);
            if (sys == null) { state = "毒圈：找不到系统表"; Thread.sleep(1000); continue; }
            try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "rw")) {
                if (!circleDone) {
                    String r = circle(m, sys, tb, side, 60);   // 起飞 1 分钟后才出第一个圈
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
            if (n >= tb.length + 2 && Math.abs(r0 - side * 0.75f) < 1f) return "已经是正式服的圈";
            String before = rows(m, d, n);
            byte[] first = new byte[ES], last = new byte[ES];
            m.seek(d); m.readFully(first);
            m.seek(d + ES * (n - 1)); m.readFully(last);
            int cnt = tb.length + 2;
            byte[] nb = build(first, last, tb, side, firstWait, cz, new Random());
            long mem = Veh.malloc(cnt * ES);
            if (mem < 0x10000) return "要内存失败，没改（原来 " + before + "）";
            m.seek(mem); m.write(nb);
            // 先写元素，再换指针，最后改个数
            wi(m, ta + 8, cnt); wi(m, ta, (int) mem); wi(m, ta + 4, cnt);
            return "改好 " + cnt + " 圈：" + rows(m, mem, cnt) + "（原来 " + before + "）";
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
        int cnt = tb.length + 2;
        java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(cnt * ES).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        float half = side / 2, pr = side * 0.75f, px = 0, py = 0;
        for (int i = 0; i < cnt; i++) {
            int o = i * ES;
            b.position(o); b.put(i < cnt - 1 ? first : last);
            float r, w, s, x, y; int dmg;
            if (i == 0) { r = side * 0.75f; w = firstWait; s = 1; dmg = 1; x = 0; y = 0; }
            else {
                if (i <= tb.length) { float[] t = tb[i - 1]; r = t[0]; w = t[1]; s = t[2]; dmg = (int) t[3]; }
                else { r = 0.1f; w = 180; s = 15; dmg = 11; }
                double room = i == 1 ? Math.max(0, (half - r) * 0.7) : Math.max(0, pr - r);
                double ang = rnd.nextDouble() * Math.PI * 2, dist = Math.sqrt(rnd.nextDouble()) * room;
                x = (float) ((i == 1 ? 0 : px) + Math.cos(ang) * dist); y = (float) ((i == 1 ? 0 : py) + Math.sin(ang) * dist);
            }
            b.putFloat(o + 4, x); b.putFloat(o + 8, y); b.putFloat(o + 12, cz);
            b.putFloat(o + 0x10, r); b.putFloat(o + 0x14, w); b.putFloat(o + 0x18, s); b.putInt(o + 0x1c, dmg);
            px = x; py = y; pr = r;
        }
        return b.array();
    }

    /** 航线拉长；没找到飞机返回 null */
    static String airline(RandomAccessFile m, long[] sys, float side) throws Exception {
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
                wf(m, t + 0x300, nsx); wf(m, t + 0x304, nsy); wf(m, t + 0x30c, nex); wf(m, t + 0x310, ney);
                wf(m, t + 0x318, (float) dx); wf(m, t + 0x31c, (float) dy);   // Dir
                wf(m, t + 0x324, (float) (2 * h));
                // 能跳的范围也跟着拉长（原来是不是和航线同样两端都记下来）
                float jsx = f(m, t + 0x328), jsy = f(m, t + 0x32c), jex = f(m, t + 0x334), jey = f(m, t + 0x338);
                wf(m, t + 0x328, nsx); wf(m, t + 0x32c, nsy); wf(m, t + 0x334, nex); wf(m, t + 0x338, ney);
                wf(m, t + 0x340, (float) (2 * h));
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
}
