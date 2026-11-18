package com.zframe.inject;

import java.io.RandomAccessFile;

/**
 * v2.74 用户：飞天不要弹射板，要同款效果（往上弹到设定高度再落下来），v2.73 那版用不了。
 * 做法：移动线程里把移动组件 MovementMode(+0x138) 改成 3（下落），竖直速度 Velocity.Z(+0xbc) = √(2·g·h)，
 *  游戏自己按下落物理往上冲、到顶再落；落下时竖直速度最多 -7 米/秒（慢慢飘下来，不摔伤）。只在单机/房主（服务器）上有效。
 */
final class Fly {
    static final float G = 980f, MAX_FALL = -700f;
    static volatile float height = 80f;   // 米
    static volatile String state = "飞天：还没用";
    static volatile float pending;        // 等移动线程起飞的竖直速度
    static volatile boolean flying;
    static long t0;

    static String go() {
        if (!Con.inMatch) return state = "飞天：进对局后才能用";
        if (Ed.ED == 2 || Lan.role == 2) return state = "飞天：加入的玩家用不了（服务器说了算）";
        float h = Math.max(5f, Math.min(1000f, height)) * 100f;
        pending = (float) Math.sqrt(2.0 * G * h);
        Mods.startMover();
        return state = "飞天：起飞 " + Math.round(h / 100) + " 米";
    }

    static boolean busy() { return pending > 0 || flying; }

    /** 移动线程每 40ms 调 */
    static void tick(RandomAccessFile m, long c) throws java.io.IOException {
        int mw = Mods.rd(m, c + Mods.C_MODE);
        int mode = mw & 0xff;
        long now = System.currentTimeMillis();
        if (pending > 0) {
            float vz = pending; pending = 0;
            if (mode == 1 || mode == Mods.MOVE_FALLING || mode == 4) {
                Mods.wi(m, c + Mods.C_MODE, (mw & ~0xff) | Mods.MOVE_FALLING);
                Mods.wf(m, c + Mods.C_VEL + 8, vz);
                flying = true; t0 = now;
                LogCap.note(String.format("飞天：模式 %d → 3，竖直速度 %.0f", mode, vz));
            } else { state = "飞天：现在的状态（模式 " + mode + "）起不了飞"; }
            return;
        }
        if (!flying) return;
        if (mode == Mods.MOVE_FALLING) {
            float vz = Mods.f(m, c + Mods.C_VEL + 8);
            if (vz < MAX_FALL && now - t0 > 300) Mods.wf(m, c + Mods.C_VEL + 8, MAX_FALL);
        } else if (now - t0 > 500) { flying = false; state = "飞天：已落地"; }
    }
}
