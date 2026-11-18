package com.zframe.inject;

import java.io.RandomAccessFile;

/**
 * v2.75 用户：飞天要一直飞，关掉才往下落。
 * 开：移动组件 MovementMode(+0x138) 改成 5（飞行，没有重力），先以 20 米/秒往上升到设定高度（按时间算），之后竖直速度保持 0 悬停，
 *  摇杆照常前后左右飞（飞行速度用游戏默认的 MaxFlySpeed）。游戏把模式改回别的就再改成 5。
 * 关：改成 3（下落），落下时竖直速度最多 -7 米/秒，不摔伤。只在单机/房主（服务器）上有效。
 */
final class Fly {
    static final int MOVE_FLYING = 5;
    static final float RISE = 2000f, MAX_FALL = -700f;
    static volatile float height = 80f;   // 米
    static volatile String state = "飞天：还没用";
    static volatile boolean want, on, falling;
    static long t0, riseMs;

    /** 按钮：开/关切换 */
    static String go() {
        if (!want) {
            if (!Con.inMatch) return state = "飞天：进对局后才能用";
            if (Ed.ED == 2 || Lan.role == 2) return state = "飞天：加入的玩家用不了（服务器说了算）";
            float h = Math.max(5f, Math.min(1000f, height)) * 100f;
            riseMs = (long) (h / RISE * 1000f);
            want = true; on = false; falling = false;
            Mods.startMover();
            return state = "飞天：开，升到 " + Math.round(h / 100) + " 米后悬停，再点一下落地";
        }
        want = false;
        return state = "飞天：关，慢慢落下";
    }

    static boolean busy() { return want || on || falling; }

    /** 移动线程每 40ms 调 */
    static void tick(RandomAccessFile m, long c) throws java.io.IOException {
        int mw = Mods.rd(m, c + Mods.C_MODE);
        int mode = mw & 0xff;
        long now = System.currentTimeMillis();
        if (!Con.inMatch) { want = on = falling = false; return; }
        if (want) {
            if (!on) {
                if (mode == 0) return;   // 没在动（上车/死亡等）
                on = true; falling = false; t0 = now;
                LogCap.note("飞天：模式 " + mode + " → 5，上升 " + riseMs + "ms");
            }
            if (mode != MOVE_FLYING) Mods.wi(m, c + Mods.C_MODE, (mw & ~0xff) | MOVE_FLYING);
            Mods.wf(m, c + Mods.C_VEL + 8, now - t0 < riseMs ? RISE : 0f);
            return;
        }
        if (on) {
            on = false; falling = true; t0 = now;
            if (mode == MOVE_FLYING) Mods.wi(m, c + Mods.C_MODE, (mw & ~0xff) | Mods.MOVE_FALLING);
            Mods.wf(m, c + Mods.C_VEL + 8, 0f);
            return;
        }
        if (falling) {
            if (mode == Mods.MOVE_FALLING) {
                if (Mods.f(m, c + Mods.C_VEL + 8) < MAX_FALL) Mods.wf(m, c + Mods.C_VEL + 8, MAX_FALL);
            } else if (now - t0 > 500) { falling = false; state = "飞天：已落地"; }
        }
    }
}
