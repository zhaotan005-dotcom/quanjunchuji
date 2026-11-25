package com.zframe.inject;

import java.io.RandomAccessFile;

/** v4.30 用户：训练场里游戏设置的「退出」点了确认没反应（v4.07 起不读游戏日志，以前靠日志里的 QuitMatch）。
 *  RE：PC::QueryQuitMatch 实现 0xd777f8，单机(NetMode 0)/客户端(3)先 strb #1,[PC,#0x55d]（bHasRequestQuitMatchOnClient，
 *  反射 SetBit 0xe7b5ac 证实）再调 ServerQueryQuitMatch。训练场里每 0.3 秒看这一位，0→1 就清掉并回大厅（走菜单同一个 Lan.leave）。 */
final class QuitWatch {
    static volatile boolean running;
    static volatile String state = "训练场设置退出：没开";

    static void start(final long pc) {
        if (running || pc == 0) return;
        running = true;
        new Thread(() -> {
            try {
                long b = Mods.base();
                try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "rw")) {
                    if (!Mods.isObj(m, b, pc)) { state = "训练场设置退出：控制器不对 " + Long.toHexString(pc); return; }
                    m.seek(pc + 0x55d); if (m.read() != 0) { m.seek(pc + 0x55d); m.write(0); }
                    state = "训练场设置退出：在看（控制器 " + Long.toHexString(pc) + "）";
                    while (Con.training && Con.inMatch) {
                        Thread.sleep(300);
                        if (!Mods.isObj(m, b, pc)) { state = "训练场设置退出：控制器没了"; break; }
                        m.seek(pc + 0x55d);
                        if (m.read() != 0) {
                            m.seek(pc + 0x55d); m.write(0);
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
