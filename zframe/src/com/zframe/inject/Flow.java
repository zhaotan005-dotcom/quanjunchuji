package com.zframe.inject;

import android.app.Activity;
import java.io.RandomAccessFile;

/**
 * v1.87 毒圈改回全军出击正式服节奏（用户选：以全军出击为准）。
 * 全军出击当年的攻略：第一圈到第二圈 10 分钟搜、第二圈到第三圈 5 分 40 秒，和端游 2018 年海岛/沙漠数据一致，所以用端游那套：
 *   8x8 图（海岛等）每圈 半径/等待/缩圈：2282m 300+120s 300s，1484m 200 140，742m 150 90，371m 120 60，185m 120 40，93m 90 30，46m 90 30，23m 60 30
 *   4x4 图（雨林 Savage）用端游雨林：1428m 120+90 240，999m 120 120，600m 90 120，300m 45 135，150m 90 15，75m 45 60，37m 45 40，19m 30 55
 *   第一圈等待多加的 120/90 秒 = 端游「上飞机到出第一个圈」的延迟（这个游戏里一开始就画圈，所以把延迟并进第一圈等待）。
 * 数据在哪：0xd96028 = GetWorld()+0x98 → +0x348 表（16 字节一项：+4 对象数组、+8 个数）→ 毒圈系统对象；
 *   对象 +0x33c/+0x340 DefaultRestrictedAreaInfos（每项 0x54：+4 中心 xyz，+0x10 半径 cm，+0x14 等待 s，+0x18 缩圈 s，+0x1c 伤害），
 *   +0x354/+0x358 是 OnRestrictedAreaStart→InitArea 拷出来的本局数组（NZ 日志 "InitArea[%d], WaitTime..." 读的就是它）。
 * 做法：进对局后（人物出生，不在启动画面）读出来、检查数值像毒圈数据才改 Default 数组；开打（阶段 4）前每 5 秒再检查一遍。
 *   本局数组（+0x354）不改：InitArea 已经按旧半径算好了每圈中心，换半径可能让下一圈跑出上一圈。
 * 航线：这版只把当前航线参数打进诊断（getall AirTransporterSystem …），看清楚再改，免得改坏了不起飞。
 */
public class Flow {
    public static volatile boolean on = true;
    public static volatile String state = "毒圈：等进对局";
    static volatile int gen;
    // 半径(cm)、等待(s)、缩圈(s)
    static final float[][] BIG = { {228235, 420, 300}, {148355, 200, 140}, {74175, 150, 90}, {37090, 120, 60},
        {18545, 120, 40}, {9270, 90, 30}, {4635, 90, 30}, {2320, 60, 30} };
    static final float[][] SMALL = { {142755, 210, 240}, {99930, 120, 120}, {59955, 90, 120}, {29980, 45, 135},
        {14990, 90, 15}, {7495, 45, 60}, {3745, 45, 40}, {1875, 30, 55} };

    static void newMatch() { gen++; state = on ? "毒圈：等进对局" : "毒圈：不改（用游戏自带）"; }

    static void matchLoaded(final String map) {
        final int g = ++gen;
        if (!on) { state = "毒圈：不改（用游戏自带）"; return; }
        new Thread(() -> {
            try { run(g, map); } catch (Throwable e) { state = "毒圈：出错 " + e; Veh.note(state); }
            GameOverlay.refreshAny();
        }, "zf-flow").start();
    }

    static void run(int g, String map) throws Exception {
        Thread.sleep(5000);
        boolean small = map != null && (map.contains("Savage") || map.contains("4x4"));
        float[][] tb = small ? SMALL : BIG;
        boolean dumped = false; int done = 0;
        for (int i = 0; i < 60 && g == gen && Con.inMatch; i++) {
            double[] p = Veh.info();
            if (p == null) { state = "毒圈：等人物出生"; Thread.sleep(3000); continue; }
            if (!dumped) { dumped = true; dumpAirline(); }
            int ph = Bots.readPhase((long) p[4]);
            String r = patch((long) p[5], tb, ph < 4);
            if (r.startsWith("改")) done++;
            state = "毒圈（" + (small ? "4x4 雨林" : "8x8") + "）：" + r + "，阶段 " + ph;
            if (i == 0 || r.startsWith("改")) Veh.note(state);
            GameOverlay.refreshAny();
            if (ph >= 4) break;
            Thread.sleep(5000);
        }
        if (done == 0) Veh.note("毒圈：一直没改成 " + state);
    }

    /** 找毒圈系统对象并改数据；返回说明 */
    static String patch(long world, float[][] tb, boolean runtimeToo) {
        if (world < 0x10000) return "没拿到 World";
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "rw")) {
            long a = Server.rd(m, world + 0x98);
            if (a < 0x10000) return "World+0x98 是空的";
            long arr = Server.rd(m, a + 0x348), n = Server.rd(m, a + 0x34c);
            if (arr < 0x10000 || n <= 0 || n > 64) return "找不到毒圈表（" + n + "）";
            StringBuilder sb = new StringBuilder(); int changed = 0, seen = 0;
            for (int i = 0; i < n; i++) {
                long data = Server.rd(m, arr + 16L * i + 4), cnt = Server.rd(m, arr + 16L * i + 8);
                if (data < 0x10000 || cnt <= 0 || cnt > 8) continue;
                for (int k = 0; k < cnt; k++) {
                    long obj = Server.rd(m, data + 4L * k);
                    if (obj < 0x10000) continue;
                    seen++;
                    String before = rows(m, obj + 0x33c);
                    if (before == null) continue;
                    if (sb.length() < 600) sb.append(" 原来[").append(before).append("]");
                    changed += write(m, obj + 0x33c, tb);
                    String rt = rows(m, obj + 0x354);   // 本局数组已经拷出来了（InitArea 跑过）就只记下来，不改（中心是按旧半径算的）
                    if (rt != null && sb.length() < 900) sb.append(" 本局已开始[").append(rt).append("]");
                }
            }
            if (changed == 0) return "没有可改的（对象 " + seen + " 个）" + sb;
            return "改好 " + changed + " 处" + sb;
        } catch (Throwable e) { return "出错 " + e; }
    }

    /** 读一个 TArray<FRestrictedAreaInfo>，数值不像毒圈就返回 null */
    static String rows(RandomAccessFile m, long ta) {
        long d = Server.rd(m, ta), n = Server.rd(m, ta + 4);
        if (d < 0x10000 || n < 3 || n > 16) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            float r = f(m, d + 0x54L * i + 0x10), w = f(m, d + 0x54L * i + 0x14), s = f(m, d + 0x54L * i + 0x18);
            if (!(r >= 0 && r < 2e6f && w >= 0 && w < 7200 && s >= 0 && s < 7200)) return null;
            if (i < 9) sb.append(Math.round(r / 100)).append("m ").append(Math.round(w)).append('/').append(Math.round(s)).append(i + 1 < n ? ", " : "");
        }
        if (f(m, d + 0x10) < 50000) return null;   // 第一圈半径不到 500 米：小图/特殊模式，不动
        return sb.toString();
    }

    static int write(RandomAccessFile m, long ta, float[][] tb) throws java.io.IOException {
        if (rows(m, ta) == null) return 0;
        long d = Server.rd(m, ta), n = Server.rd(m, ta + 4);
        int c = 0;
        for (int i = 0; i < n && i < tb.length; i++) {
            wf(m, d + 0x54L * i + 0x10, tb[i][0]); wf(m, d + 0x54L * i + 0x14, tb[i][1]); wf(m, d + 0x54L * i + 0x18, tb[i][2]); c++;
        }
        // 第 9 圈以后（缩到没有）：端游是第 8 圈合拢后等 180 秒，再 15 秒缩完
        for (long i = tb.length; i < n; i++) { wf(m, d + 0x54L * i + 0x14, 180); wf(m, d + 0x54L * i + 0x18, 15); c++; }
        return c;
    }

    static float f(RandomAccessFile m, long a) { return Float.intBitsToFloat((int) Server.rd(m, a)); }
    static void wf(RandomAccessFile m, long a, float v) throws java.io.IOException {
        int b = Float.floatToRawIntBits(v);
        m.seek(a); m.write(new byte[] { (byte) b, (byte) (b >> 8), (byte) (b >> 16), (byte) (b >> 24) });
    }

    /** 航线参数打进日志（Con 的航线/毒圈日志会收） */
    static void dumpAirline() {
        final Activity a = Con.act; if (a == null) return;
        a.runOnUiThread(() -> {
            for (String p : new String[] { "MaxAirlineRadius", "RandomRadiusForAirLine", "AirLineOffset", "JumpRadiusScale", "FlightNumber" })
                Con.exec("getall AirTransporterSystem " + p);
            for (String p : new String[] { "WaitingPlayersCountDownTime", "StartMatchCountDownTime", "WaitingSkydivingCountDownTime" })
                Con.exec("getall NZBRGameState " + p);
        });
    }
}
