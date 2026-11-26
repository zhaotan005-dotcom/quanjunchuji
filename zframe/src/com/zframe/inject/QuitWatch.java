package com.zframe.inject;

import java.io.RandomAccessFile;

/** v4.30 用户：训练场里游戏设置的「退出」点了确认没反应（v4.07 起不读游戏日志，以前靠日志里的 QuitMatch）。
 *  RE：PC::QueryQuitMatch 实现 0xd777f8，单机(NetMode 0)/客户端(3)先 strb #1,[PC,#0x55d]（bHasRequestQuitMatchOnClient，
 *  反射 SetBit 0xe7b5ac 证实）再调 ServerQueryQuitMatch。训练场里每 0.3 秒看这一位，0→1 就清掉并回大厅（走菜单同一个 Lan.leave）。 */
final class QuitWatch {
    static volatile boolean running;
    static volatile String state = "训练场设置退出：没开";

    /** v4.38 用户：菜单的回大厅能退，设置里的退出还是不行 → 回大厅本身没问题，是没认出「确认退出」。
     *  控制器不再只信 Veh.lastPc：同时看自己人物身上的控制器（人物+0x2d0），两个都盯 0x55c/0x55d。 */
    /** v4.39 根因（读码）：设置「退出比赛→确认」调 PC 的 ManualQuitMatch（vtable+0xb08）。普通 PC 的实现 0xd77c2c 转去 QueryQuitMatch(+0xb00)
     *  会置 +0x55d；但训练场那种 PC 子类把它重写成 0x1086910（只调蓝图事件 + 地块逻辑），从不走 QueryQuitMatch → 0x55d 永远是 0，所以一直没反应。
     *  改：训练场里把 0x1086910 开头换成和 0xd77c2c 一样的三条指令（转 QueryQuitMatch），再由下面盯 0x55d 回大厅。 */
    static final long MQM = 0x1086910L;
    static final String MQM_O = "104c2de908b08de210d04de2", MQM_N = "002090e5002b92e512ff2fe1";
    static volatile String patch = "没打";
    static void start(final long pc0) {
        if (Con.training) patch = Mods.write(MQM, MQM_O, MQM_N, true);
        if (running) return;
        running = true;
        new Thread(() -> {
            try {
                long b = Mods.base();
                java.util.LinkedHashSet<Long> pcs = new java.util.LinkedHashSet<>();
                try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "rw")) {
                    int t = 0;
                    while (Con.inMatch) {
                        if (t++ % 7 == 0) {   // 约 2 秒找一次控制器
                            java.util.ArrayList<Long> c = new java.util.ArrayList<>();
                            c.add(pc0); c.add(Veh.lastPc());
                            try { double[] q = Veh.info(); if (q != null && q.length > 6 && (long) q[6] >= 0x10000) c.add(Server.rd(m, (long) q[6] + 0x2d0)); } catch (Throwable ignored) {}
                            long pa = GameOverlay.pawnAddr(); if (pa >= 0x10000) c.add(Server.rd(m, pa + 0x2d0));
                            for (long x : c) if (x >= 0x10000 && !pcs.contains(x) && Mods.isObj(m, b, x)) {
                                pcs.add(x); m.seek(x + 0x55c); m.write(new byte[] { 0, 0 });
                            }
                            pcs.removeIf(x -> { try { return !Mods.isObj(m, b, x); } catch (Throwable e) { return true; } });
                            state = "训练场设置退出：在看 " + pcs.size() + " 个控制器，补丁 " + patch;
                        }
                        Thread.sleep(300);
                        if (!Con.training) continue;
                        for (long x : pcs) {
                            byte[] f = new byte[2]; m.seek(x + 0x55c); m.readFully(f);
                            if (f[0] != 0 || f[1] != 0) {
                                m.seek(x + 0x55c); m.write(new byte[] { 0, 0 });
                                state = "训练场设置退出：收到，回大厅";
                                LogCap.note(state);
                                GameOverlay.toastAny(Lan.leave());
                                return;
                            }
                        }
                    }
                }
            } catch (Throwable t) { state = "训练场设置退出：出错 " + t; }
            finally { running = false; }
        }, "zf-quitwatch").start();
    }
}
