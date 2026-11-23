package com.zframe.inject;

import java.io.RandomAccessFile;

/** v4.12 训练岛淹死/炸死。用户 11-23：服务端/单机训练场还会淹死、被车炸死。
 *  原因（history v4.10 RE）：人物扣血写在 0xbe0618 里两处（0xbe0e48 ldr/sub/str [+0x5a0]、0xbe0ed0 同样），
 *  MinHp 的钩子在 GM 伤害链（vt+0x484）上，憋气伤害（0xd028a0 → TakeDamage）和车炸伤害不走那条链 → 锁血挡不住，人就死了。
 *  修：两处扣血都跳到 SLACK+0x990 的 cave：照原样算新血，训练岛标志（MinHp 缓冲区 +12）开着时新血 < 1 就留 1（和 MinHp「剩一丝血不死」同一条规矩）。
 *  标志只在训练岛（单机/服务端）打开，离开清 0 → 别的图、人机一点不受影响。SLACK+0x990 原来给 Flow 毒圈底层表，那段一直装不上（放不下、空位被占），v4.12 起让出来。 */
final class HpFloor {
    static final long A = 0xbe0e48L, B = 0xbe0ed0L, CAVE = Server.SLACK + 0x990;
    static final String A_O = "a00595e5010040e0", B_O = "a01595e5000041e0";
    static final String TAIL = "1cc09fe50cc09ce500005ce30100000a010050e30100a0b3a00585e504f01fe5";
    static volatile String state = "训练岛防淹/炸死：没装";
    static volatile boolean ok;

    static synchronized String install() {
        if (ok) return "ok";
        long b = Mods.base(); if (b == 0) return "找不到游戏库";
        long buf = MinHp.addr; if (buf <= 0 || buf > 0xffffffffL) return "缓冲区没准备好";
        String ca = A_O + TAIL + Server.w32((int) (b + A + 0xc)) + Server.w32((int) buf);
        String cb = B_O + TAIL + Server.w32((int) (b + B + 0xc)) + Server.w32((int) buf);
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "rw")) {
            m.seek(b + CAVE); m.write(Patches.hex(ca + cb));
        } catch (Throwable e) { return "出错 " + e.getMessage(); }
        String r = Mods.write(A, A_O, "04f01fe5" + Server.w32((int) (b + CAVE)), true);
        if (!"ok".equals(r)) return "入口 A " + r;
        r = Mods.write(B, B_O, "04f01fe5" + Server.w32((int) (b + CAVE + 0x30)), true);
        if (!"ok".equals(r)) { Mods.write(A, A_O, "04f01fe5" + Server.w32((int) (b + CAVE)), false); return "入口 B " + r; }
        ok = true; return "ok";
    }

    /** MinHp 装好缓冲区后调；on = 在训练岛 */
    static void set(boolean on) {
        if (MinHp.buf == null) return;
        if (on) { String r = install(); state = "训练岛防淹/炸死：" + ("ok".equals(r) ? "开（血最低留 1）" : "没装上，" + r); if (!"ok".equals(r)) { LogCap.note(state); return; } }
        MinHp.buf.putInt(12, on ? 1 : 0);
        if (!on && ok) state = "训练岛防淹/炸死：关（不在训练岛）";
    }
}
