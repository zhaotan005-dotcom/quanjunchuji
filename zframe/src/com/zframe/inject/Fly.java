package com.zframe.inject;

import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * v2.73 用户：参考弹射板的跳跃和动作做一个「飞天」，高度能自己定。
 * RE（libUE4）：弹射板 LaunchPlayer 0x11417fc(板, 人物) → 人物+0x114c = LaunchPadComponent，
 *  服务器上调 0x1105428(组件, &板+0x400)：把 0xd8 字节的 LaunchPadRepInfo 抄进 组件+0xc8，置 +0x1a0=1，开 Tick。
 *  Tick 0x1104c0c 状态机（组件+0xac）：1 Init → 过 LaunchDelayTime(+0x10) → 2 Launch → 过 LaunchTime(+0x14) → 3 Rise → 4 Fall → 5 Land …
 *  0x1104720：起跳速度 = 方向（按 LaunchAngleByDegree +0x8 转）归一化 × LaunchVelocity(+0xc) → 组件+0x1a8；
 *  下落 z += -0.5 × GravityWhenFall(+0x4) × t²；上升用 GravityWhenRise(+0x0)。
 * 做法：自己拼一份 info（特效路径都空），用游戏线程调 0x1105428(组件, info)。高度 h：竖直速度 = √(2·g·h)。
 */
final class Fly {
    static final long START = 0x1105428L;
    static final float G = 1960f, ANGLE = 75f;
    static volatile float height = 80f;   // 米
    static volatile String state = "飞天：还没用";
    static ByteBuffer info; static long infoAddr;
    static volatile long comp, compPawn;

    static long find(RandomAccessFile m, long pawn) {
        long c = Server.rd(m, pawn + 0x114c);
        if (c >= 0x10000 && Server.rd(m, c + 0xa0) == pawn) return c;
        if (comp != 0 && compPawn == pawn && Server.rd(m, comp + 0xa0) == pawn) return comp;
        comp = 0;
        Veh.Names nm = null; try { nm = Veh.Names.get(); } catch (Throwable t) {}
        for (int off = 0x200; off < 0x1800; off += 4) {
            long q = Server.rd(m, pawn + off);
            if (q < 0x10000 || q >= 0xffff0000L) continue;
            String cn = Alive.clsName(m, nm, q);
            if (cn != null && cn.contains("LaunchPad") && Server.rd(m, q + 0xa0) == pawn) { comp = q; compPawn = pawn; return q; }
        }
        return 0;
    }

    static String go() {
        if (!Con.inMatch) return state = "飞天：进对局后才能用";
        if (Lan.role == 2) return state = "飞天：加入的玩家用不了（要服务器那边飞）";
        long b = Mods.base(); if (b == 0) return state = "飞天：找不到游戏库";
        long pawn = Mods.myPawn(); if (pawn < 0x10000) return state = "飞天：还没找到人物";
        long c;
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "rw")) {
            c = find(m, pawn);
            if (c == 0) return state = "飞天：人物身上没有弹射组件";
            Mods.wi(m, pawn + 0x114c, (int) c);
        } catch (Throwable e) { return state = "飞天：读内存出错 " + e.getMessage(); }
        if (info == null) { info = ByteBuffer.allocateDirect(0x100).order(ByteOrder.LITTLE_ENDIAN); infoAddr = GameOverlay.bufAddr(info); }
        if (infoAddr <= 0 || infoAddr > 0xffffffffL) return state = "飞天：缓冲区地址不对";
        for (int o = 0; o < 0x100; o += 4) info.putInt(o, 0);
        float h = Math.max(5f, Math.min(1000f, height)) * 100f;
        float vz = (float) Math.sqrt(2.0 * G * h), v = (float) (vz / Math.sin(Math.toRadians(ANGLE)));
        info.putFloat(0x0, G); info.putFloat(0x4, G); info.putFloat(0x8, ANGLE); info.putFloat(0xc, v);
        info.putFloat(0x10, 0.05f); info.putFloat(0x14, 0.2f); info.putFloat(0x18, 0.8f); info.putFloat(0x1c, 0.4f);
        try {
            long[] r = Veh.ucall(c, infoAddr, 0, 0, null, b + START);
            if (r[0] == 0x5b0e) return state = "飞天：起飞（" + Math.round(h / 100) + " 米，速度 " + Math.round(v) + "）";
            if (r[0] == 0x5b0f) return state = "飞天：组件是空的";
            return state = "飞天：游戏没反应（用道具 cave 没装？）";
        } catch (Throwable e) { return state = "飞天：出错 " + e; }
    }
}
