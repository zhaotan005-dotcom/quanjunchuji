package com.zframe.inject;

import java.io.RandomAccessFile;

/** v3.03 服务端（房主，Lan.role==1）开局做两件事：
 *  1. 用户：战术道具开局让服务端给每个人刷每样 999 个。
 *     做法：游戏线程里（Veh.ucall 的 'U' cave）直接调刷物品补丁 0x17169c0(控制器, FString*)，FString = "P<编号>x999"。
 *     补丁从控制器 +0x2c0 取人物，所以传加入玩家的控制器 = 服务器直接放进他的背包（服务器权威，游戏自己同步给他），不是刷在地上。
 *     道具编号 = 道具表扫到的 402 类（去掉坏编号）。每局每个控制器只给一次。
 *  2. 用户：删掉服务端显示的那个人物 → 房主自己的人物 SetActorHiddenInGame(true)（AActor vt+0x160，thunk 0x2e70d40 里读到），
 *     bHidden 会复制，加入的玩家也看不到。不关碰撞（关了会掉出地图死掉，影响结算）。可在联机页关掉。 */
final class HostMatch {
    static final long GIVE_CAVE = 0x17169c0L;
    static volatile String state = "开局发道具：还没开局";
    static volatile boolean hideHost = true;
    static final java.util.HashSet<Long> given = new java.util.HashSet<>();
    static volatile long hidden;
    static java.nio.ByteBuffer sb; static long sbAddr;
    static volatile boolean running;
    static void newMatch() { synchronized (given) { given.clear(); } hidden = 0; }
    static void start() {
        if (running) return; running = true;
        new Thread(() -> {
            while (true) {
                try { Thread.sleep(3000); } catch (InterruptedException e) { return; }
                if (Lan.role != 1 || !Con.inMatch) continue;
                try { hide(); } catch (Throwable t) { LogCap.note("藏房主人物出错 " + t); }
                try { giveAll(); } catch (Throwable t) { state = "开局发道具：出错 " + t; }
            }
        }, "zf-hostmatch").start();
    }
    static void hide() throws Exception {
        if (!hideHost) return;
        long p = Mods.myPawn(); if (p == 0 || p == hidden) return;
        long b = Mods.base(); if (b == 0) return;
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) { if (!Mods.isObj(m, b, p)) return; }
        long[] r = Veh.ucall(p, 1, 0x160, 0, null);
        if (r[0] == 0x5b0e) { hidden = p; LogCap.note("房主人物已隐藏 " + Long.toHexString(p)); }
    }
    static java.util.ArrayList<Long> propIds() {
        java.util.TreeMap<Long, java.util.ArrayList<Long>> m = GameOverlay.items;
        if (m == null) { GameOverlay.loadItems(false); return null; }
        java.util.ArrayList<Long> l = new java.util.ArrayList<>();
        for (java.util.Map.Entry<Long, java.util.ArrayList<Long>> e : m.entrySet()) if (e.getKey() / 100 == 402) for (long v : e.getValue()) if (!GameOverlay.itemBad.contains(v)) l.add(v);
        return l;
    }
    static void giveAll() throws Exception {
        long b = Mods.base(); if (b == 0) return;
        java.util.ArrayList<Long> ids = propIds();
        if (ids == null) { state = "开局发道具：在读道具表"; return; }
        if (ids.isEmpty()) { state = "开局发道具：道具表里没有战术道具（402）"; return; }
        Veh.Names nm = Veh.Names.get();
        java.util.ArrayList<Long> ctls = new java.util.ArrayList<>();
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
            double[] q = Veh.info(); long gm = q != null ? (long) q[4] : 0;
            long gs = gm >= 0x10000 ? Server.rd(m, gm + 0x2e0) : 0;
            long data = gs >= 0x10000 ? Server.rd(m, gs + 0x2bc) : 0;
            int n = gs >= 0x10000 ? (int) Server.rd(m, gs + 0x2c0) : 0;
            if (data < 0x10000 || n <= 0 || n > 200) return;
            for (int k = 0; k < n; k++) {
                long ps = Server.rd(m, data + 4L * k); if (ps < 0x10000) continue;
                long ctl = Server.rd(m, ps + 0x60); if (ctl < 0x10000) continue;
                if (!Alive.clsName(m, nm, ctl).contains("PlayerController")) continue;
                if (!Alive.clsName(m, nm, Server.rd(m, ctl + 0x2fc)).contains("NetConnection")) continue;   // 只给加入的玩家
                long p = Server.rd(m, ctl + 0x2c0);
                if (p < 0x10000 || !Alive.clsName(m, nm, p).contains("Character")) continue;
                synchronized (given) { if (given.contains(ctl)) continue; }
                ctls.add(ctl);
            }
        }
        for (long ctl : ctls) {
            int ok = 0;
            for (long id : ids) if (giveTo(b, ctl, id, 999)) ok++;
            synchronized (given) { given.add(ctl); }
            state = "开局发道具：给加入玩家 " + Long.toHexString(ctl) + " 发了 " + ok + "/" + ids.size() + " 种，每种 999";
            LogCap.note(state + "：" + ids);
        }
    }
    /** 游戏线程里调刷物品补丁，人物 = 控制器 +0x2c0 */
    static synchronized boolean giveTo(long b, long ctl, long id, int n) throws InterruptedException {
        String s = "P" + id + "x" + n;
        if (sb == null) { sb = java.nio.ByteBuffer.allocateDirect(256).order(java.nio.ByteOrder.LITTLE_ENDIAN); sbAddr = GameOverlay.bufAddr(sb); }
        if (sbAddr <= 0 || sbAddr > 0xffffffffL) return false;
        // FString {TCHAR* Data, int Num, int Max}（这个包 TCHAR 4 字节），字符从 +16 开始
        for (int i = 0; i < s.length(); i++) sb.putInt(16 + 4 * i, s.charAt(i));
        sb.putInt(16 + 4 * s.length(), 0);
        sb.putInt(0, (int) (sbAddr + 16)); sb.putInt(4, s.length() + 1); sb.putInt(8, s.length() + 1);
        long[] r = Veh.ucall(ctl, sbAddr, 0, 0, null, b + GIVE_CAVE);
        return r[0] == 0x5b0e;
    }
}
