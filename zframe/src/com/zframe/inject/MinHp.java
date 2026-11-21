package com.zframe.inject;

import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** v3.68 用户 11-21：训练场会掉血、有受伤反馈，但剩一丝血就不再掉（死不了）。v3.67 训练场开车撞死后闪退。
 *  伤害链（history v3.19 RE）：GameMode vt+0x484 → 0xcc0ad0 → 0xcc0e0c bl 0xc8b450(GM, float* 伤害, ?, 受伤者, ...)，
 *  返回后 r6 = 伤害指针、r8 = 受伤者，0xcc0e10 mov r0,#0 → 改 b 0x171e1c8（GM GiveItem 死函数头 0x1c8..0x1ff + PickAnim 后面 0x28c..0x2af）：
 *    受伤者 == 缓冲区+0 的人物时，伤害 > 血(+0x5a0) − 1 就改成 max(血 − 1, 0)（+4 = 1.0f，+8 计次），然后 mov r0,#0; b 0xcc0e14。
 *  只有单机/服务端算伤害；缓冲区+0 = 0 时 cave 什么都不做。 */
final class MinHp {
    static final long HOOK = 0xcc0e10L, A = 0x171e1c8L, B = 0x171e28cL;
    static final String HOOK_O = "0000a0e3", HOOK_N = "ec7429ea";
    static final String CA = "2c109fe5000091e5080050e13300001aa00598e5010040e2100a01eec11ab8ee000a96edc10ab4ee10faf1ee2b00009a230000ea";
    static final String CA_O = "f04b2de918b08de218d04de20140a0e10080a0e1041094e5bc029fe5007094e5000051e3b4229fe54710a0e300708f0002008fe0046080e2";
    static final String CB = "100a11ee000050e30000a0b3000086e5080091e5010080e2080081e50000a0e3d88ad6ea";
    static final String CB_O = "0c1095e55c2090e55c3091e5030052e1e4ffffca581091e5580080e2021191e7000051e1";
    static volatile String state = "训练场锁血：没装";
    static ByteBuffer buf; static long addr; static volatile boolean running;

    static synchronized String install() {
        long b = Mods.base(); if (b == 0) return "找不到游戏库";
        if (buf == null) { buf = ByteBuffer.allocateDirect(32).order(ByteOrder.LITTLE_ENDIAN); addr = GameOverlay.bufAddr(buf); buf.putFloat(4, 1.0f); }
        if (addr <= 0 || addr > 0xffffffffL) return "缓冲区地址不对";
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "rw")) {
            String r = put(m, b + A, CA + Server.w32((int) addr), CA_O + "", 56); if (r != null) return "cave A " + r;
            r = put(m, b + B, CB, CB_O, 36); if (r != null) return "cave B " + r;
        } catch (Throwable e) { return "出错 " + e.getMessage(); }
        String r = Mods.write(HOOK, HOOK_O, HOOK_N, true);
        return "ok".equals(r) ? "ok" : "入口 " + r;
    }
    /** null = 写好；原字节要么是我们的，要么是文件原样（A 区比前 52 字节，最后 4 字节是缓冲区地址） */
    static String put(RandomAccessFile m, long at, String code, String orig, int len) throws java.io.IOException {
        byte[] want = Patches.hex(code), cur = new byte[len]; m.seek(at); m.readFully(cur);
        byte[] o = Patches.hex(orig);
        int cmp = Math.min(o.length, len);
        boolean isOrig = java.util.Arrays.equals(java.util.Arrays.copyOf(cur, cmp), java.util.Arrays.copyOf(o, cmp));
        boolean ours = java.util.Arrays.equals(java.util.Arrays.copyOf(cur, want.length - (len == 56 ? 4 : 0)), java.util.Arrays.copyOf(want, want.length - (len == 56 ? 4 : 0)));
        if (!isOrig && !ours) return "空位字节不符 " + Patches.hexs(java.util.Arrays.copyOf(cur, 8));
        m.seek(at); m.write(want);
        return null;
    }

    /** 训练岛进图后（单机/服务端）开：装 cave，之后每秒把自己的人物写进缓冲区；离开训练岛清 0 */
    static void start() {
        if (running) return;
        if (GameOverlay.remote()) { state = "训练场锁血：客户端不用（伤害只在服务端算）"; return; }
        running = true;
        new Thread(() -> {
            try {
                String r = install();
                if (!"ok".equals(r)) { state = "训练场锁血：没装上，" + r; LogCap.note(state); return; }
                int n = 0, last = 0;
                while (Con.training && Con.inMatch) {
                    long me = Mods.myPawn();
                    buf.putInt(0, (int) me);
                    state = "训练场锁血：已装（血最低留 1，人物 " + Long.toHexString(me) + "，挡住致命伤害 " + buf.getInt(8) + " 次）";
                    int c = buf.getInt(8);
                    if (++n == 5 || c != last) { LogCap.note(state); last = c; }
                    Thread.sleep(1000);
                }
                state = state + "；已离开训练岛，停";
            } catch (Throwable t) { state = "训练场锁血：出错 " + t; LogCap.note(state); }
            finally { if (buf != null) buf.putInt(0, 0); running = false; }
        }, "zf-minhp").start();
    }
}
