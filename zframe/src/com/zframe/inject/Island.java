package com.zframe.inject;

import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** v3.35 用户（v3.33 实测）：训练营进去掉海里、周围什么都没有。
 *  pak（Grok 第四批）：训练岛 Main.umap 是 WorldComposition + NZStreamingLevelsManagerActor_TileMode 分块地图，
 *  10 个 PlayerStart 都在子关卡 AlwaysLoad/LogicLevel 里，地形在 Heightmap_x0..3_y0..3。
 *  推测：出生时 LogicLevel 还没载入 → 没出生点 → 人放在原点海里，分块按人的位置加载 → 原点附近只有海。
 *  做法：进训练岛后 30 秒内每 2 秒找 PlayerStart 实例，找到就把自己传到第一个出生点上方（只传一次）；
 *  一直找不到 → 黑框报「没找到出生点」，并带地形块数量（LandscapeStreamingProxy/LandscapeProxy）。未实测。 */
final class Island {
    static volatile String state = "训练岛：没进";
    static volatile boolean running;

    static void matchLoaded() {
        if (running) return; running = true;
        new Thread(() -> {
            try { run(); } catch (Throwable t) { state = "训练岛：出错 " + t; GameOverlay.toastAny(state); }
            finally { running = false; }
        }, "zf-island").start();
    }
    static void run() throws Exception {
        long t0 = System.currentTimeMillis();
        int land = 0;
        while (System.currentTimeMillis() - t0 < 30000) {
            Thread.sleep(2000);
            if (!Con.training || !Con.inMatch) return;
            long b = Mods.base(); if (b == 0) continue;
            Veh.Names nm = Veh.Names.get();
            long[] ps = new long[1]; int[] cnt = new int[2];
            scan(b, nm, ps, cnt);
            land = cnt[1];
            if (ps[0] == 0) { state = "训练岛：还没出生点，地形块 " + land; continue; }
            long pawn = Mods.myPawn();
            if (pawn < 0x10000) { state = "训练岛：出生点 " + cnt[0] + " 个，等人物"; continue; }
            float x, y, z;
            try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
                long root = Server.rd(m, ps[0] + 0x128); if (root < 0x10000) continue;
                x = Mods.f(m, root + 0x140); y = Mods.f(m, root + 0x144); z = Mods.f(m, root + 0x148);
            }
            boolean ok = Veh.teleport(pawn, x, y, z + 150, 0);
            state = "训练岛：出生点 " + cnt[0] + " 个，地形块 " + land + "，传到 " + (int) x + "," + (int) y + "," + (int) z + (ok ? "" : "（传送失败）");
            LogCap.note(state);
            if (!ok) GameOverlay.toastAny(state);
            return;
        }
        state = "训练岛：30 秒没找到出生点（子关卡没加载），地形块 " + land;
        LogCap.note(state); GameOverlay.toastAny(state);
    }
    /** ps[0] = 第一个 PlayerStart；cnt[0] PlayerStart 数，cnt[1] 地形块数 */
    static void scan(long b, Veh.Names nm, long[] ps, int[] cnt) {
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
            long g = Server.rd(m, b + 0x51250acL);
            long objs = Server.rd(m, g + 0x10), num = Server.rd(m, g + 0x18);
            if (objs < 0x10000 || num <= 0 || num > 0x200000) return;
            byte[] raw = new byte[(int) num * 16];
            m.seek(objs); m.readFully(raw);
            ByteBuffer bb = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
            java.util.HashMap<Long, Integer> kind = new java.util.HashMap<>();
            for (int i = 0; i < num; i++) {
                long o = bb.getInt(i * 16) & 0xffffffffL; int fl = bb.getInt(i * 16 + 4);
                if (o < 0x10000 || (fl & 0x20000000) != 0) continue;
                long c = Server.rd(m, o + 0xc);
                if (c < 0x10000) continue;
                Integer k = kind.get(c);
                if (k == null) {
                    String n = Alive.objName(m, nm, c);
                    k = n == null ? 0 : n.equals("PlayerStart") ? 1 : n.startsWith("LandscapeStreamingProxy") || n.equals("LandscapeProxy") || n.equals("Landscape") ? 2 : 0;
                    kind.put(c, k);
                }
                if (k == 0) continue;
                if ((Server.rd(m, o + 4) & 0x10) != 0) continue;   // 类默认对象
                if (k == 1) { cnt[0]++; if (ps[0] == 0) ps[0] = o; } else cnt[1]++;
            }
        } catch (Throwable ignored) {}
    }
}
