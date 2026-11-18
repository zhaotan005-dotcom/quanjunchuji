package com.zframe.inject;

import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** v2.70 用户：捡东西有两次拾取动作。BeginPlayingPickupAnim 0xd21bfc（设 +0xbf8 计时器 + 标志 +0xc00）被 C++（0xf041e8，选动作时）
 *  和 Lua（UFunction 0xe3ff20）各调一次。入口改成 b 0x171e200（GM 面板没用的 GiveItem 函数中间）：同一个人物 1.5 秒内第二次调用直接返回。
 *  game/re/pickanim_cave.py 生成，emu_pickanim.py（unicorn）测过。数据缓冲区：+0 人物 +4 时间 +8 1.5f +12 跳过次数 +16 调用次数 */
final class PickAnim {
    static final long HOOK = 0xd21bfcL, CAVE = 0x171e200L;
    static final String HOOK_O = "f04b2de9", HOOK_N = "7ff127ea";
    static final String CODE = "1f402de90040a0e178c09fe510109ce5011081e210108ce5987b40eb000050e31400000a1d0d80e2000a90ed54c09fe500109ce5040051e10c00001a010adced601a30eec01ab5ee10faf1ee070000ba021adcede11ab4ee10faf1ee030000aa0c109ce5011081e20c108ce51f80bde800408ce5010a8ced1f40bde8f04b2de918b08de25e0ed8ea";
    static final String ORIG = "040097e4010050e10500000afd3ac6eb0050a0e1040016e5fa3ac6eb000055e10500001a040097e4041096e4002091e1f3ffff1a18d04be2f08bbde8c40198e5000050e3faffff0a700090e5000050e31850901500005513f5ffff0a8253ddeb0c1095e55c2090e55c3091e5030052e1efffffca581091e5580080e2021191e7000051e1eaffff1a830eebeb";
    static volatile String state = "拾取动作：没装";
    static ByteBuffer buf; static long addr;
    static synchronized String install() {
        long b = Mods.base(); if (b == 0) return state = "拾取动作：找不到游戏库";
        if (buf == null) { buf = ByteBuffer.allocateDirect(64).order(ByteOrder.LITTLE_ENDIAN); addr = GameOverlay.bufAddr(buf); buf.putFloat(8, 1.5f); }
        if (addr <= 0 || addr > 0xffffffffL) return state = "拾取动作：缓冲区地址不对";
        byte[] want = Patches.hex(CODE + Server.w32((int) addr));
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "rw")) {
            byte[] cur = new byte[want.length]; m.seek(b + CAVE); m.readFully(cur);
            boolean ours = java.util.Arrays.equals(java.util.Arrays.copyOf(cur, want.length - 4), java.util.Arrays.copyOf(want, want.length - 4));
            boolean orig = Patches.hexs(java.util.Arrays.copyOf(cur, want.length - 4)).replace(" ", "").equalsIgnoreCase(ORIG);
            if (!ours && !orig) return state = "拾取动作：空位字节不符 " + Patches.hexs(java.util.Arrays.copyOf(cur, 8));
            m.seek(b + CAVE); m.write(want);
        } catch (Throwable e) { return state = "拾取动作：出错 " + e.getMessage(); }
        String r = Mods.write(HOOK, HOOK_O, HOOK_N, true);
        return state = "ok".equals(r) ? "拾取动作：已装（1.5 秒内重复的不放）" : "拾取动作：入口 " + r;
    }
    static String report() {
        if (buf == null) return state;
        return state + "，调用 " + buf.getInt(16) + " 次，挡掉重复 " + buf.getInt(12) + " 次";
    }
}
