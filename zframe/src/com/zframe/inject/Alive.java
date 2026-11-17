package com.zframe.inject;

import java.io.RandomAccessFile;

/**
 * v2.23：查「剩余」为什么是 2（只有房主一个人时）。只读探针，只在按钮按下时装，读完马上拆。
 * 游戏数活人：0x101a6e8 遍历全部 ANZCharacter（排除 ANZDollCharacter），+0x5a0 >= 1 算活 → GameState+0x774。
 * 钩子：计数那一步 0x101a814（mov r0,r6，r6 = 被算进去的人物）→ b cave 0x171e7d0（GM 面板废函数里的空地，SL cave 后面）。
 * cave 把 r6 记进 8 格环形缓冲（Java 直接缓冲区，地址写在 cave 的字面量里），然后 mov r0,r6，跳回 0x101a818。
 * 不改剩余人数，只看是谁被算进去。
 */
public class Alive {
    static final long HOOK = 0x101a814L, CAVE = 0x171e7d0L;
    static final String HOOK_O = "0600a0e1", HOOK_N = "ed0f1cea";
    static final String CAVE_O = "000057e3f8ffff1a0630c0e70150a0e1873ac6eb0060a0e105d0a0e138001be5000050e30000000a78ad09eb";
    static final String CAVE_CODE = "20c09fe500009ce51f0000e200018ce0046080e500009ce5010080e200008ce50600a0e107f0e3ea";
    static java.nio.ByteBuffer buf; static long bufAddr;
    public static volatile String state = "剩余人数：还没查";

    /** v2.26：同一个钩子也拿来找「房间里全部人物」（房主给所有玩家加速/高跳/无限子弹用）。返回 null = 装不上，原因在 err */
    static volatile String err = "";
    static synchronized java.util.LinkedHashSet<Long> collect(long ms) {
        long b = Mods.base();
        if (b == 0) { err = "找不到游戏库"; return null; }
        if (!Con.inMatch) { err = "进对局后再点"; return null; }
        if (buf == null) { buf = java.nio.ByteBuffer.allocateDirect(160).order(java.nio.ByteOrder.LITTLE_ENDIAN); bufAddr = GameOverlay.bufAddr(buf); }
        if (bufAddr == 0) { err = "缓冲区地址拿不到"; return null; }
        for (int i = 0; i < 160; i += 4) buf.putInt(i, 0);
        String caveN = CAVE_CODE + Server.w32((int) bufAddr);
        String caveO = CAVE_O.substring(0, caveN.length());
        String c = Mods.write(CAVE, caveO, caveN, true);
        if (!"ok".equals(c)) { err = "cave " + c; return null; }
        String h = Mods.write(HOOK, HOOK_O, HOOK_N, true);
        if (!"ok".equals(h)) { Mods.write(CAVE, caveO, caveN, false); err = "钩子 " + h; return null; }
        try { Thread.sleep(ms); } catch (InterruptedException e) {}
        Mods.write(HOOK, HOOK_O, HOOK_N, false);   // 先拆钩子
        try { Thread.sleep(200); } catch (InterruptedException e) {}
        Mods.write(CAVE, caveO, caveN, false);
        int n = buf.getInt(0); lastN = n;
        java.util.LinkedHashSet<Long> seen = new java.util.LinkedHashSet<>();
        for (int j = 0; j < Math.min(n, 32); j++) { long p = buf.getInt(4 + 4 * ((n - 1 - j) & 31)) & 0xffffffffL; if (p >= 0x10000) seen.add(p); }
        return seen;
    }
    static int lastN;
    static String probe() {
        java.util.LinkedHashSet<Long> seen = collect(2500);
        if (seen == null) return state = "剩余人数：" + err;
        int n = lastN;
        StringBuilder sb = new StringBuilder("剩余人数：2.5 秒里记到 " + n + " 次，算进去的人物 " + seen.size() + " 个");
        long me = GameOverlay.pawnAddr();
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
            Veh.Names nm = null; try { nm = Veh.Names.get(); } catch (Throwable t) {}
            for (long p : seen) {
                sb.append("\n  ").append(Long.toHexString(p)).append(p == me ? "（我）" : "");
                sb.append(" 类 ").append(clsName(m, nm, p)).append(" 名 ").append(objName(m, nm, p));
                sb.append(" +5a0=").append(Server.rd(m, p + 0x5a0));
                long ctl = Server.rd(m, p + 0x2d0);
                sb.append(" 控制器 ").append(ctl < 0x10000 ? "无" : Long.toHexString(ctl) + " " + clsName(m, nm, ctl));
                long lv = Server.rd(m, p + 0x18), pkg = lv < 0x10000 ? 0 : Server.rd(m, lv + 0x18);
                sb.append(" 所在 ").append(objName(m, nm, pkg)).append('/').append(objName(m, nm, lv));
            }
        } catch (Throwable t) { sb.append("\n  读出错 ").append(t.getMessage()); }
        if (me == 0) sb.append("\n  （没拿到我自己的人物地址：先刷一次东西再查，就能标出哪个是我）");
        state = sb.toString();
        LogCap.note(state);
        return seen.size() + " 个人物被算进剩余，详情在导出诊断";
    }
    static String objName(RandomAccessFile m, Veh.Names nm, long o) {
        if (nm == null || o < 0x10000) return "?";
        long idx = Server.rd(m, o + 0x10), no = Server.rd(m, o + 0x14);
        String s = idx > 0 && idx < 0x400000 ? nm.name(m, (int) idx) : null;
        return s == null ? "?" : no > 0 ? s + "_" + (no - 1) : s;
    }
    static String clsName(RandomAccessFile m, Veh.Names nm, long o) { return o < 0x10000 ? "?" : objName(m, nm, Server.rd(m, o + 0xc)); }
}
