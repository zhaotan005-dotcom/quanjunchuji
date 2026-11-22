package com.zframe.inject;

import java.io.RandomAccessFile;

/** v4.11 训练岛（取代 v4.10 TrainSafe）。只在有权威的一端（单机 / 服务端房主）跑。
 *
 *  v4.10 TrainSafe 每 0.1 秒把人物血写成上限×20、关 bCanBeDamaged、憋气清零、给刷出来的车补血 —— 用户 11-22 21:02：「治标不治本」，
 *  车是他拿枪打爆的（车能被打爆是正常的）。全部撤掉。原版训练营本来就能死：死了 4 秒后原地规则复活（见 ModeMap：游戏自己的 ResurrectSystem
 *  「Practice」规则），我们这边缺的是告诉游戏「这是训练营模式 600」，那个修在 ModeMap。
 *
 *  这里只剩：
 *  - 进训练岛启动 NoFall（摔伤留 1 血，v3.95 用户要的）和 MinHp（剩一丝血不死，v3.95/3.96 用户要的）—— 不再等 Island.run 找到出生点才启动；
 *  - 每 1 秒认一次自己的人物（控制器 Pawn 且虚表 +0x7e8 是人物摔伤函数；人在车上时 Pawn 是车 → 保留上车前的人物），给 MinHp 用。
 *  不写血、不写 bCanBeDamaged、不写憋气、不碰车。 */
final class TrainMode {
    static volatile String state = "训练岛：没开";
    static volatile boolean running;
    static volatile long me;

    /** MinHp 用：认出的自己人物（0 = 还没有） */
    static long me() { return me; }

    static void start() {
        if (running) return;
        if (GameOverlay.remote()) { state = "训练岛：客户端不用（伤害/复活在服务端算）"; return; }
        running = true;
        try { NoFall.start(); } catch (Throwable ignored) {}
        try { MinHp.start(); } catch (Throwable ignored) {}
        new Thread(() -> {
            try {
                while (Con.training && Con.inMatch) {
                    long b = Mods.base();
                    if (b != 0) {
                        long p = 0; try { p = Mods.livePawn(); } catch (Throwable ignored) {}
                        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
                            if (p != 0 && NoFall.isChar(m, b, p)) me = p;
                            else if (me != 0 && !NoFall.isChar(m, b, me)) me = 0;   // 死了/换人物：等复活后的新人物
                        } catch (Throwable ignored) {}
                    }
                    state = "训练岛：人物 " + Long.toHexString(me) + "；" + ModeMap.shortText();
                    Thread.sleep(1000);
                }
            } catch (Throwable t) { state = "训练岛：出错 " + t; LogCap.note(state); }
            finally { running = false; me = 0; }
        }, "zf-trainmode").start();
    }
}
