package com.zframe.inject;

import java.io.RandomAccessFile;

/** v3.32 用户：单机「剩余」多一个占位（v1.84 起，v2.23 RE：GameState 数活人时世界里多一个活着的 ANZCharacter）。
 *  不改数字（用户 v2.23 规定不许硬改 2→1），而是找到那个人物本身删掉（AActor::K2_DestroyActor 虚表 +0x164，游戏线程）。
 *  认定：被数进剩余的人物里，不是我、不是我们刷的人机（Bots.live）、控制器不是玩家控制器，隔 4 秒两次都在。只在真单机做。未实测。 */
final class Phantom {
    static volatile String state = "占位人物：还没查";
    static volatile int gen;

    static void start() {
        if (Ed.ED != 0 || Lan.role != 0) return;
        final int g = ++gen;
        new Thread(() -> {
            for (long wait : new long[]{20000, 40000, 60000}) {
                try { Thread.sleep(wait); } catch (InterruptedException e) { return; }
                if (g != gen || !Con.inMatch) return;
                try { once(); } catch (Throwable t) { state = "占位人物：出错 " + t; }
            }
        }, "zf-phantom").start();
    }

    static java.util.LinkedHashSet<Long> cands() throws Exception {
        java.util.LinkedHashSet<Long> seen = Alive.collect(1500), out = new java.util.LinkedHashSet<>();
        if (seen == null) { state = "占位人物：" + Alive.err; return null; }
        long me = Mods.myPawn();
        if (me < 0x10000) { state = "占位人物：还没拿到自己的人物"; return null; }
        java.util.HashSet<Long> bots = new java.util.HashSet<>();
        synchronized (Bots.live) { for (long[] x : Bots.live) { bots.add(x[0]); bots.add(x[1]); } }
        Veh.Names nm = null; try { nm = Veh.Names.get(); } catch (Throwable ignored) {}
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
            for (long p : seen) {
                if (p == me || bots.contains(p)) continue;
                if (Server.rd(m, p + 0x5a0) < 1) continue;
                long ctl = Server.rd(m, p + 0x2d0);
                if (ctl >= 0x10000) {
                    if (bots.contains(ctl)) continue;
                    String cn = Alive.clsName(m, nm, ctl);
                    if (cn != null && cn.contains("PlayerController")) continue;
                }
                out.add(p);
            }
        }
        return out;
    }

    static void once() throws Exception {
        java.util.LinkedHashSet<Long> a = cands();
        if (a == null) return;
        if (a.isEmpty()) { state = "占位人物：没有（剩余里只有你和人机）"; return; }
        Thread.sleep(4000);
        java.util.LinkedHashSet<Long> b = cands();
        if (b == null) return;
        a.retainAll(b);
        if (a.isEmpty()) { state = "占位人物：两次结果对不上，先不删"; return; }
        long base = Mods.base();
        int ok = 0; StringBuilder sb = new StringBuilder();
        Veh.Names nm = null; try { nm = Veh.Names.get(); } catch (Throwable ignored) {}
        for (long p : a) {
            try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
                if (!Prop.alive(m, base, p)) continue;
                sb.append(' ').append(Long.toHexString(p)).append(' ').append(Alive.clsName(m, nm, p));
            }
            long[] r = Veh.ucall(p, 0, 0x164, 0, null);
            if (r[0] == 0x5b0e) ok++;
        }
        state = "占位人物：删了 " + ok + "/" + a.size() + sb;
        LogCap.note(state);
        if (ok == 0) GameOverlay.toastAny("剩余人数里的占位删除失败");
    }
}
