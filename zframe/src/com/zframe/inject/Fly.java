package com.zframe.inject;

import java.io.RandomAccessFile;

/**
 * v2.77 用户：v2.76 飞行模式（5）在空中是走路动作；开着跳伞掉不下去，关了也不行。
 * 改成下落模式（3，跳起/弹射那种腾空动作）+ 重力 GravityScale(+0x120)=0：
 *  - 开：前 h/20m·s⁻¹ 时间竖直速度 2000 往上，之后竖直速度 0 悬停；AirControl(+0x19c)=1、FallingLateralFriction(+0x1a8)=3，
 *    MaxWalkSpeed(+0x164)×加速倍数 → 摇杆在空中前后左右飞。落到地面（模式 1）会再弹回下落模式，一直飞到关。
 *  - 跳伞/上飞机/上车/游泳（模式不是 1 或 3）或换了人物：自动关掉并还原，绝不去改跳伞。
 *  - 关：还原重力和这几个值，落下时竖直速度最多 -7 米/秒，不摔伤。
 * 偏移来自 libUE4 反射表（名字指针后第 7 个字）。只在单机/房主（服务器）上有效。
 */
final class Fly {
    static final int C_GRAV = 0x120, C_WALK = 0x164, C_AIR = 0x19c, C_LATF = 0x1a8;
    static final float RISE = 2000f, MAX_FALL = -700f;
    static volatile float height = 80f;   // 米
    static volatile String state = "飞天：还没用";
    static volatile boolean want, on, falling;
    static long t0, riseMs, lastLog, cmcOn;
    static int phaseOn;
    static float sGrav, sWalk, sAir, sLat;

    /** 按钮：开/关切换 */
    static String go() {
        if (!want) {
            if (!Con.inMatch) return state = "飞天：进对局后才能用";
            if (Ed.ED == 2 || Lan.role == 2) return state = "飞天：加入的玩家用不了（服务器说了算）";
            float h = Math.max(5f, Math.min(1000f, height)) * 100f;
            riseMs = (long) (h / RISE * 1000f);
            want = true; falling = false; phaseOn = Bots.phase;
            Mods.startMover();
            return state = "飞天：开，升到 " + Math.round(h / 100) + " 米后一直飞，再点一下关";
        }
        want = false;
        return state = "飞天：关，慢慢落下";
    }

    static boolean busy() { return want || on || falling; }

    static void restore(RandomAccessFile m, long c) throws java.io.IOException {
        if (!on) return;
        on = false;
        if (c == cmcOn) {
            Mods.wf(m, c + C_GRAV, sGrav); Mods.wf(m, c + C_WALK, sWalk);
            Mods.wf(m, c + C_AIR, sAir); Mods.wf(m, c + C_LATF, sLat);
        }
    }

    /** 移动线程每 40ms 调 */
    static void tick(RandomAccessFile m, long c) throws java.io.IOException {
        int mw = Mods.rd(m, c + Mods.C_MODE);
        int mode = mw & 0xff;
        long now = System.currentTimeMillis();
        boolean ok = mode == 1 || mode == Mods.MOVE_FALLING;
        if (!Con.inMatch || (on && c != cmcOn) || (busy() && Bots.phase != phaseOn)) {   // 换人物/阶段变了（上飞机、跳伞）：关掉不管
            if (busy() && Bots.phase != phaseOn) { state = "飞天：对局阶段变了，已自动关"; }
            want = false; falling = false; restore(m, c); return; }
        if (want && !ok) {   // 跳伞/飞机/车/水里：自动关
            want = false; falling = false; restore(m, c);
            state = "飞天：模式 " + mode + "（跳伞/上车/水里），已自动关";
            LogCap.note(state);
            return;
        }
        if (want) {
            if (!on) {
                cmcOn = c; on = true; t0 = now;
                sGrav = Mods.f(m, c + C_GRAV); sWalk = Mods.f(m, c + C_WALK);
                sAir = Mods.f(m, c + C_AIR); sLat = Mods.f(m, c + C_LATF);
                LogCap.note(String.format("飞天：开 模式 %d 重力 %.2f 走 %.0f 空中控制 %.2f", mode, sGrav, sWalk, sAir));
            }
            if (mode != Mods.MOVE_FALLING) Mods.wi(m, c + Mods.C_MODE, (mw & ~0xff) | Mods.MOVE_FALLING);
            Mods.wf(m, c + C_GRAV, 0f);
            Mods.wf(m, c + C_AIR, 1f);
            Mods.wf(m, c + C_LATF, 3f);
            float ws = sWalk * Math.max(1f, Mods.speed);
            if (Math.abs(Mods.f(m, c + C_WALK) - ws) > 1f) Mods.wf(m, c + C_WALK, ws);
            Mods.wf(m, c + Mods.C_VEL + 8, now - t0 < riseMs ? RISE : 0f);
            if (now - lastLog > 2000) { lastLog = now; float vx = Mods.f(m, c + Mods.C_VEL), vy = Mods.f(m, c + Mods.C_VEL + 4);
                state = String.format("飞天：飞行中 模式 %d 水平速度 %.0f 上限 %.0f", mode, Math.hypot(vx, vy), ws); }
            return;
        }
        if (on) {
            restore(m, c);
            falling = true; t0 = now;
            Mods.wf(m, c + Mods.C_VEL + 8, 0f);
            return;
        }
        if (falling) {
            if (mode == Mods.MOVE_FALLING) {
                if (Mods.f(m, c + Mods.C_VEL + 8) < MAX_FALL) Mods.wf(m, c + Mods.C_VEL + 8, MAX_FALL);
            } else if (now - t0 > 500) { falling = false; state = "飞天：已落地"; }
            if (now - t0 > riseMs * 3 + (long) (height * 1000f / 7f) + 5000) falling = false;   // 最多管这么久，不会卡住别的下落
        }
    }
}
