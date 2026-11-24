package com.zframe.inject;

import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * v4.21 航线和飞机高度从源头改（不再起飞后找飞机改坐标）。
 *
 * 为什么以前的修法失效：v1.94~v4.06 的 Flow.airByValue 靠游戏日志「CalcAirLineByRestrictedAreaInfo StartLoc: …」的坐标在内存里按值找飞机，
 *   v4.07 起不读日志 → logAir 永远空 → 从没改过；剩下按偏移找（AirTransporterSystem+0x304）那条在 v1.93 就证实找不到 → 飞机一直是原版：
 *   200 米高、2 公里左右、经常在地图边上/海上。
 *
 * 原生算法（RE，game/re/air_cave.py 注释；history v4.21）：CalcAirLineByRestrictedAreaInfo 0x118dc6c(系统, 飞机, &圈心, &半径, [sp]=Param)
 *   在 min(半径, MaxDistToCenter +0x40) 圆内随机点 P → 方向 ⟂ (P-圈心) → 过 P 的弦 → RegionFly(+0xc FBox2D) 框裁 → 两头加 MaxAirlineRadius(+0x38)；
 *   Z = Height(+4)；跳伞段 = 弦 × JumpRadiusScale(+0x3c) 再用 RegionJump(+0x20) 框裁。
 *   这个包：Height 20000（200 米）、RegionFly/RegionJump 全 0（无效框，把线裁成一小段）、圈 = 第一个安全区（1500 米）→ 就是现在的问题。
 *   跳伞组件按 SkydivingStartHeight 150000（1500 米）设计（构造 0x1121ed8），离地比例 < 0.23（约 345 米）强制开伞 → 200 米跳下来来不及开伞。
 *
 * 改法：入口 0x118dc6c push → b cave（GM SwitchToAimSet 死函数 0x171eaf4）。cave 只改游戏的「输入」再走原函数：
 *   圈心 = 地图正中、半径 = 半边长×1.1、MaxDistToCenter = 边长×0.1、MaxAirlineRadius = 0、RegionFly = ±半边长×1.1、
 *   RegionJump = ±半边长×0.9、Height = 1500 米（= 跳伞组件的起跳高度）、JumpRadiusScale 0.8；飞机自己的 Param 副本（+0x2b8）同样改。
 *   结果（unicorn 跑真函数 60 局）：8x8 航线 8.8~9 公里、4x4 4.4 公里，都穿过地图中间（离正中 ≤ 边长 10%），方向随机，
 *   高 1500 米，跳伞段只在地图里（约航线 10%~90%）。数据区 R==0 时 cave 不改任何东西，完全走游戏原样。
 * 只有单机/服务端算航线（客户端是复制下来的），装在哪个包都无害。
 */
final class Air {
    static final long HOOK = 0x118dc6cL, CAVE = 0x171eaf4L, DATA = CAVE + 0xa0;
    static final String HOOK_O = "f04f2de9", HOOK_N = "a04316ea";
    static final String CODE = "70402de994408fe2005094e5000055e30700000a00c0a0e300c082e504c082e5005083e510609de5040000ebae6f81e2020000eb7040bde8f04f2de94ebce9ea045094e5045086e5085094e5405086e50050a0e3385086e50c5094e5145086e5185086e5025125e20c5086e5105086e5105094e5285086e52c5086e5025125e2205086e5245086e50150a0e31c50c6e53050c6e5145094e53c5086e50ef0a0e1";
    /** 空位原字节（0xa0 代码 + 0x18 数据） */
    static final String ORIG = "704c2de910b08de20040a0e10150a0e1c40194e5000050e33d00000a700090e5000050e318609015000056133800000a5051ddeb0c1096e55c2090e55c3091e5030052e1320000ca581091e5580080e2021191e7000051e12d00001a510cebeb0c1096e55c2090e55c3091e5030052e1270000ca581091e5580080e2021191e7000051e12200001a146396e5000056e31f00000adef1eaeb0c1096e55c2090e55c3091e5030052e1190000ca581091e5580080e2021191e7";
    /** 飞机高度 cm：跳伞组件 SkydivingStartHeight 默认 150000 */
    static final float HEIGHT = 150000f;
    static final float JUMP_SCALE = 0.8f;
    static volatile boolean on;
    static volatile String state = "航线：没装";

    static synchronized String install() {
        if (on) return "ok";
        long b = Mods.base(); if (b == 0) return "找不到游戏库";
        byte[] code = Patches.hex(CODE), orig = Patches.hex(ORIG);
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "rw")) {
            byte[] cur = new byte[orig.length]; m.seek(b + CAVE); m.readFully(cur);
            boolean isOrig = java.util.Arrays.equals(cur, orig);
            boolean ours = java.util.Arrays.equals(java.util.Arrays.copyOf(cur, code.length), code);
            if (!isOrig && !ours) return "空位字节不符 " + Patches.hexs(java.util.Arrays.copyOf(cur, 8));
            if (isOrig) {
                m.seek(b + DATA); m.write(new byte[24]);   // 先把数据区清 0（R==0 = 不改），再写代码
                m.seek(b + CAVE); m.write(code);
            }
        } catch (Throwable e) { return "出错 " + e.getMessage(); }
        String r = Mods.write(HOOK, HOOK_O, HOOK_N, true);
        if (!"ok".equals(r)) return "入口 " + r;
        on = true; return "ok";
    }

    /** 进对局图之前（我们发 open 时 / 游戏自己切图的 Browse）按地图写数据区；训练岛等没有飞机的图写 0（原样） */
    static void prepare(String map) {
        try {
            if (map == null || !map.startsWith("/Game/Maps/") || Con.isLobby(map) || Lan.role == 2) return;
            String r = install();
            if (!"ok".equals(r)) { state = "航线：没装上（" + r + "）"; LogCap.note(state); return; }
            boolean training = map.contains("TrainingIsland");
            boolean small = map.contains("Savage") || map.contains("4x4");
            float side = small ? 408000f : 816000f, h = side / 2;
            ByteBuffer d = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
            if (!training) d.putFloat(h * 1.1f).putFloat(HEIGHT).putFloat(side * 0.1f).putFloat(h * 1.1f).putFloat(h * 0.9f).putFloat(JUMP_SCALE);
            long b = Mods.base();
            try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "rw")) { m.seek(b + DATA); m.write(d.array()); }
            state = training ? "航线：训练岛没有飞机，原样" : "航线：已从源头改（" + (small ? "4x4" : "8x8") + "，游戏自己算：穿过地图中间约 " + Math.round(side * 1.1f / 100000f * 10) / 10.0 + " 公里，高 " + Math.round(HEIGHT / 100) + " 米，跳伞段只在地图里）";
            LogCap.note(state);
        } catch (Throwable e) { state = "航线：出错 " + e; }
    }
}
