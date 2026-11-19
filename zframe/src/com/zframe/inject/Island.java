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
    /** v3.37 RE：训练岛的子关卡由 NZStreamingLevelsManagerActor_TileMode::LoadBlock(this, 块号, 块数) 0x1163f3c 加载，
     *  正式服是服务端给玩家控制器发「Client Load Block」RPC（0x1086960 读 PC+0xbdc 块号 / +0xbe0 块数后调它）；单机没人发 → 只有海。
     *  这里在游戏线程直接调 LoadBlock(管理器, 0, 1)。当前块 +0x440，块数 +0x43c。 */
    static final long LOAD_BLOCK = 0x1163f3cL;
    /** 训练场（Practice 模式）登录时 0x10845b0 找队伍返回空，0x1084f6c ldr [r8,#0x14] 崩（fault 0x14，19:07 实测诊断）。
     *  改成 movs r8,r0 / ldrne / cmpne / beq 0x10850a8（没队伍就走原来的「跳过」分支）。 */
    static final long PR = 0x1084f2cL;
    static final String PR_O = "0080a0e1580596e5000050e35a00000a", PR_N = "0080b0e158059615000050135a00000a";
    static volatile String fix = "训练场空队伍补丁：没装";
    static void install() { String r = Mods.write(PR, PR_O, PR_N, true); fix = "训练场空队伍补丁：" + r; LogCap.note(fix); }
    static volatile String block = "加载块：没试";
    static volatile String state = "训练岛：没进";
    static volatile boolean running;

    static void matchLoaded() {
        if (running) return; running = true; blockTried = false;
        new Thread(() -> {
            try { run(); } catch (Throwable t) { state = "训练岛：出错 " + t; GameOverlay.toastAny(state); }
            finally { running = false; }
        }, "zf-island").start();
    }
    static void run() throws Exception {
        long t0 = System.currentTimeMillis();
        int land = 0;
        while (System.currentTimeMillis() - t0 < 45000) {
            Thread.sleep(2000);
            if (!Con.training || !Con.inMatch) return;
            long b = Mods.base(); if (b == 0) continue;
            Veh.Names nm = Veh.Names.get();
            long[] ps = new long[1]; int[] cnt = new int[2]; long[] mgr = new long[1];
            scan(b, nm, ps, cnt, mgr);
            if (ps[0] == 0 && mgr[0] != 0 && !blockTried) {
                blockTried = true;
                try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
                    int cur = (int) Server.rd(m, mgr[0] + 0x440), num = (int) Server.rd(m, mgr[0] + 0x43c);
                    if (cur == 0) Mods.pokeInt(mgr[0] + 0x440, -1);   // 当前块是 0 会被当成「已加载」
                    long[] r = Server.gcall(b + LOAD_BLOCK, mgr[0], 0, 1, 0, 3000);
                    block = "加载块：管理器 " + Long.toHexString(mgr[0]) + " 原当前块 " + cur + " 块数 " + num + " → 调用" + (r[0] != 0 ? "已跑" : "没跑（游戏线程没接）");
                }
                LogCap.note(block);
                if (block.contains("没跑")) GameOverlay.toastAny("训练岛" + block);
                continue;
            }
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
        state = "训练岛：45 秒没找到出生点（子关卡没加载），地形块 " + land + "；" + block;
        LogCap.note(state); GameOverlay.toastAny(state);
    }
    /** ps[0] = 第一个 PlayerStart；cnt[0] PlayerStart 数，cnt[1] 地形块数 */
    static volatile boolean blockTried;
    static void scan(long b, Veh.Names nm, long[] ps, int[] cnt, long[] mgr) {
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
                    k = n == null ? 0 : n.equals("PlayerStart") ? 1 : n.equals("NZStreamingLevelsManagerActor_TileMode") ? 3 : n.startsWith("LandscapeStreamingProxy") || n.equals("LandscapeProxy") || n.equals("Landscape") ? 2 : 0;
                    kind.put(c, k);
                }
                if (k == 0) continue;
                if ((Server.rd(m, o + 4) & 0x10) != 0) continue;   // 类默认对象
                if (k == 1) { cnt[0]++; if (ps[0] == 0) ps[0] = o; } else if (k == 3) { if (mgr[0] == 0) mgr[0] = o; } else cnt[1]++;
            }
        } catch (Throwable ignored) {}
    }
}
