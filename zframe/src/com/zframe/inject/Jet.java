package com.zframe.inject;

import java.io.RandomAccessFile;

/**
 * v2.66 用户：刷喷气背包后键位没了，丢掉也不恢复。
 * RE（libUE4）：UNZBRPlayerPropAnimComponent（人物上的组件，+0xa0 = 人物）+0x1d0 = 喷气背包开着；
 *  设置函数 0x110c28c(组件, 开/关)：开 → 人物进状态 3（0xc904f4），关 → 离开状态 3（0xc9065c）。
 *  键位跟着人物状态 3 走；丢背包时单机里没人调「关」→ 一直卡在状态 3。
 * 做法：对局里每 2 秒看一次，组件 +0x1d0 还开着、背包里却已经没有喷气背包 → 调 0x110c28c(组件, 0)。
 *  菜单「常用」里也有「恢复键位」手动按钮（不管有没有背包都关）。
 */
final class Jet {
    static final long SET = 0x110c28cL;
    static volatile String state = "";
    static volatile long comp; static volatile long compPawn;
    static volatile boolean sawJet; static int gone;

    static long find(RandomAccessFile m, long pawn) {
        if (comp != 0 && compPawn == pawn && Server.rd(m, comp + 0xa0) == pawn) return comp;
        comp = 0;
        Veh.Names nm = null; try { nm = Veh.Names.get(); } catch (Throwable t) {}
        for (int off = 0x200; off < 0x1800; off += 4) {
            long q = Server.rd(m, pawn + off);
            if (q < 0x10000 || q >= 0xffff0000L) continue;
            String cn = Alive.clsName(m, nm, q);
            if (cn != null && cn.contains("PropAnimComponent") && Server.rd(m, q + 0xa0) == pawn) { comp = q; compPawn = pawn; return q; }
        }
        return 0;
    }
    static boolean jetInBag(RandomAccessFile m, long pawn) {
        long inv = Server.rd(m, pawn + 0x838); if (inv < 0x10000) return true;   // 读不到就当还在，不动
        Veh.Names nm = null; try { nm = Veh.Names.get(); } catch (Throwable t) {}
        long data = Server.rd(m, inv + 0x4a8), num = Server.rd(m, inv + 0x4ac);
        for (int k = 0; k < Math.min(num, 300); k++) {
            long p = Server.rd(m, data + 4 * k); if (p < 0x10000) continue;
            String c = Alive.clsName(m, nm, p);
            if (c != null && c.toLowerCase().contains("jetpack")) return true;
        }
        return false;
    }
    /** 对局里每 2 秒（道具键的轮询线程里调，只读；要关时才调游戏函数） */
    static void tick() {
        if (!Con.inMatch || Lan.role == 2) return;
        long b = Mods.base(); if (b == 0) return;
        long pawn = Mods.myPawn(); if (pawn < 0x10000) return;
        long c; boolean on, bag;
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
            c = find(m, pawn); if (c == 0) return;
            byte[] x = new byte[1]; m.seek(c + 0x1d0); m.readFully(x); on = x[0] != 0;
            bag = jetInBag(m, pawn);
        } catch (Throwable e) { return; }
        if (bag) { sawJet = true; gone = 0; return; }
        if (!on) { gone = 0; return; }
        if (++gone < 2) return;   // 连续两次（约 4 秒）没背包还开着才关，免得丢/捡的瞬间误判
        gone = 0;
        String r = off(false);
        LogCap.note("喷气背包：背包没了但还开着 → 关掉，恢复键位 " + r);
    }
    /** 关掉喷气背包状态；manual = 菜单按钮 */
    static String off(boolean manual) {
        long b = Mods.base(); if (b == 0) return "找不到游戏库";
        long pawn = Mods.myPawn(); if (pawn < 0x10000) return "找不到人物（进对局再点）";
        long c; int before;
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
            c = find(m, pawn); if (c == 0) { LogCap.note("喷气背包：没找到组件"); return "没找到喷气背包组件（已记日志）"; }
            byte[] x = new byte[1]; m.seek(c + 0x1d0); m.readFully(x); before = x[0];
        } catch (Throwable e) { return "出错 " + e; }
        try {
            long[] r = Veh.ucall(c, 0, 0, 0, null, b + SET);
            int after = -1;
            try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) { byte[] x = new byte[1]; m.seek(c + 0x1d0); m.readFully(x); after = x[0]; } catch (Throwable ignored) {}
            state = "组件 " + Long.toHexString(c) + " 开关 " + before + "→" + after + " 返回 " + Long.toHexString(r[0]);
            LogCap.note("喷气背包：" + (manual ? "手动" : "自动") + "恢复键位 " + state);
            if (r[0] != 0x5b0e) return Veh.uOn ? "补丁没反应" : "补丁还没装好，先刷一次东西再点";
            return "ok";
        } catch (InterruptedException e) { return "中断"; }
    }
}
