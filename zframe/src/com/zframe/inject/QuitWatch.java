package com.zframe.inject;

import java.io.RandomAccessFile;

/** v4.30 用户：训练场里游戏设置的「退出」点了确认没反应（v4.07 起不读游戏日志，以前靠日志里的 QuitMatch）。
 *  RE：PC::QueryQuitMatch 实现 0xd777f8，单机(NetMode 0)/客户端(3)先 strb #1,[PC,#0x55d]（bHasRequestQuitMatchOnClient，
 *  反射 SetBit 0xe7b5ac 证实）再调 ServerQueryQuitMatch。训练场里每 0.3 秒看这一位，0→1 就清掉并回大厅（走菜单同一个 Lan.leave）。 */
final class QuitWatch {
    static volatile boolean running;
    static volatile String state = "训练场设置退出：没开";

    /** v4.36 用户：还是退不出。改：拿到控制器就开；0x55c/0x55d 两位都看；控制器换了就跟着换（Veh.lastPc） */
    static void start(final long pc0) {
        if (running || pc0 == 0) return;
        running = true;
        new Thread(() -> {
            try {
                long b = Mods.base(), pc = 0;
                try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "rw")) {
                    long cand = pc0;
                    while (Con.inMatch) {
                        long lp = Veh.lastPc(); if (lp >= 0x10000 && lp != pc && Mods.isObj(m, b, lp)) cand = lp;
                        if (cand != pc && Mods.isObj(m, b, cand)) {   // 新控制器：先清零当基准
                            pc = cand; m.seek(pc + 0x55c); m.write(new byte[] { 0, 0 });
                            state = "训练场设置退出：在看（控制器 " + Long.toHexString(pc) + "）";
                        }
                        Thread.sleep(300);
                        if (pc == 0 || !Con.training) continue;
                        if (!Mods.isObj(m, b, pc)) { pc = 0; cand = 0; state = "训练场设置退出：控制器没了，再找"; continue; }
                        byte[] f = new byte[2]; m.seek(pc + 0x55c); m.readFully(f);
                        if (f[0] != 0 || f[1] != 0) {
                            m.seek(pc + 0x55c); m.write(new byte[] { 0, 0 });
                            state = "训练场设置退出：收到，回大厅";
                            LogCap.note(state);
                            GameOverlay.toastAny(Lan.leave());
                            break;
                        }
                    }
                }
            } catch (Throwable t) { state = "训练场设置退出：出错 " + t; }
            finally { running = false; }
        }, "zf-quitwatch").start();
    }
}
