package com.zframe.inject;

import java.io.RandomAccessFile;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.LinkedHashMap;

/**
 * v1.61 自建服务端（在游戏进程里，不联网）。只在玩家按「开启服务端」后才装。
 *
 * 游戏大厅和服务器之间收发的是 CSPkg（protobuf：head{cmd, client_sequence_id, result, key_pkg} + body），
 * 协议定义就嵌在 libUE4 里（game/proto/*.proto 是从里面导出来的结构，只有字段定义）。
 *  - 发：ConnectorService::SendRawBuffer 0xf950e0 整个换成 SEND 段：把 len+数据 追加到请求区，假装发出去了。
 *  - 收：ConnectorService 每帧的 Tick 在 0xf92fb0 跳到 TICK 段：把回包区里我们放好的包，逐个交给
 *    游戏自己的收包函数 vt+0x164 (0xf95298)，和真服务器回来的包走同一条路。
 * Java 线程读请求、按协议回包：13001 取仓库 → 13002 物品列表（角色带性别、衣服、风衣），13003/13005 穿/脱，
 * 其它需要回应的关键请求（key_pkg）回一个空的成功包，免得大厅一直等。
 */
public class Server {
    static final long SLACK = 0x4a2d540L, SLACK_END = 0x4a2e000L;
    static final long SEND_AT = 0xf950e0L, TICK_AT = 0xf92fb0L;
    static final String SEND_ORIG = "f0482de910b08de2", TICK_ORIG = "54000ae30f0040e3";
    static final int REQ_CAP = 1 << 20, RPL_CAP = 1 << 20;

    static ByteBuffer req, rpl; static long reqAddr, rplAddr;
    static int reqRp, rplWp;
    public static volatile String state = "自建服务端：没开";
    static final ArrayList<String> log = new ArrayList<>();
    static int replied;

    static String w32(int w) { return String.format("%02x%02x%02x%02x", w & 0xff, (w >> 8) & 0xff, (w >> 16) & 0xff, (w >>> 24) & 0xff); }
    static String bytesAt(RandomAccessFile m, long a, int n) throws java.io.IOException {
        byte[] x = new byte[n]; m.seek(a); m.readFully(x); return Patches.hexs(x).replace(" ", "").toLowerCase();
    }
    /** 代码段末尾到页尾的空隙（文件里代码段长 0x4a2d528，映射到 0x4a2e000）是否可执行 */
    static boolean slackIsCode(long b) {
        long lo = b + SLACK, hi = b + SLACK_END;
        try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader("/proc/self/maps"))) {
            String l;
            while ((l = r.readLine()) != null) {
                if (!l.contains("libUE4")) continue;
                String[] p = l.split("\\s+"), ab = p[0].split("-");
                long s = Long.parseLong(ab[0], 16), e = Long.parseLong(ab[1], 16);
                if (s <= lo && hi <= e) return p[1].startsWith("r-x");
            }
        } catch (Throwable ignored) {}
        return false;
    }

    /** SEND 段（地址填好） */
    static String sendCave(int buf) {
        return "f0412de9" + Mods.armMov(false, 4, buf & 0xffff) + Mods.armMov(true, 4, (buf >>> 16) & 0xffff)
            + "005094e5026085e0086086e2087094e5070056e11500008a107084e2057087e0002087e5047087e20060a0e3020056e1030000aa06c0d1e706c0c7e7016086e2f9ffffea045085e2025085e0035085e20350c5e35bf07ff5005084e5046094e5016086e2046084e50200a0e1f081bde80c6094e5016086e20c6084e50200a0e1f081bde8";
    }
    /** TICK 段，ret = 0xf92fb8 的实际地址 */
    static String tickCave(int buf, int ret) {
        return "f0422de9" + Mods.armMov(false, 4, buf & 0xffff) + Mods.armMov(true, 4, (buf >>> 16) & 0xffff)
            + "045094e5006094e55bf07ff5060055e1110000aa107084e2057087e0002097e5041087e2045085e2025085e0035085e20350c5e3045084e5086094e5016086e2086084e50800a0e10030a0e300c098e564c19ce53cff2fe1e8ffffeaf042bde854000ae30f0040e304f01fe5"
            + w32(ret);
    }
    static String hook(int target) { return "04f01fe5" + w32(target); }

    public static synchronized String start() {
        if (req != null) return state;
        if (Con.inMatch) return state = "自建服务端：请在大厅里开";
        long b = Mods.base();
        if (b == 0) return state = "自建服务端：找不到游戏库";
        if (!slackIsCode(b)) return state = "自建服务端：代码段空隙不可用，没装";
        ByteBuffer q = ByteBuffer.allocateDirect(16 + REQ_CAP).order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer r = ByteBuffer.allocateDirect(16 + RPL_CAP).order(ByteOrder.LITTLE_ENDIAN);
        long qa = GameOverlay.bufAddr(q), ra = GameOverlay.bufAddr(r);
        if (qa <= 0 || ra <= 0 || qa > 0xffffffffL || ra > 0xffffffffL) return state = "自建服务端：拿不到内存地址";
        q.putInt(8, REQ_CAP);
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "rw")) {
            if (!bytesAt(m, b + SEND_AT, 8).equals(SEND_ORIG) || !bytesAt(m, b + TICK_AT, 8).equals(TICK_ORIG))
                return state = "自建服务端：游戏代码和预期不一样，没装";
            long sc = b + SLACK, tc = b + SLACK + 0x100;
            m.seek(sc); m.write(Patches.hex(sendCave((int) qa)));
            m.seek(tc); m.write(Patches.hex(tickCave((int) ra, (int) (b + TICK_AT + 8))));
            req = q; rpl = r; reqAddr = qa; rplAddr = ra;
            push(13002, 0, false, depositProps());         // 一开就先把仓库物品推过去
            m.seek(b + TICK_AT); m.write(Patches.hex(hook((int) tc)));
            m.seek(b + SEND_AT); m.write(Patches.hex(hook((int) sc)));
        } catch (Throwable e) { req = null; rpl = null; return state = "自建服务端：出错 " + e.getMessage(); }
        Thread t = new Thread(Server::loop, "zf-server"); t.setDaemon(true); t.start();
        Con.exec("open " + Con.LOBBY);                     // 大厅重进一次，让它把开场请求发给我们
        return state = "自建服务端：已开";
    }

    static void loop() {
        while (true) {
            try { Thread.sleep(30); poll(); } catch (Throwable ignored) {}
        }
    }

    /** 读新请求并回包 */
    static synchronized void poll() {
        int wp = req.getInt(0);
        VarHandle.acquireFence();
        while (reqRp < wp) {
            int len = req.getInt(16 + reqRp);
            byte[] d = new byte[len];
            for (int i = 0; i < len; i++) d[i] = req.get(16 + reqRp + 4 + i);
            reqRp = (reqRp + 4 + len + 3) & ~3;
            handle(d);
        }
    }

    static void handle(byte[] pkg) {
        int cmd = 0, seq = 0; boolean key = false; byte[] body = new byte[0];
        PB.R r = new PB.R(pkg);
        while (r.next()) {
            if (r.field == 1 && r.wire == 2) {
                PB.R h = r.sub();
                while (h.next()) {
                    if (h.field == 1) cmd = (int) h.val;
                    else if (h.field == 4) seq = (int) h.val;
                    else if (h.field == 7) key = h.val != 0;
                }
            } else if (r.field == 2 && r.wire == 2) body = r.raw();
        }
        String what;
        switch (cmd) {
            case 13001: push(13002, seq, key, depositProps()); what = "仓库物品"; break;
            case 13003: push(13004, seq, key, equip(body)); what = "穿上"; break;
            case 13005: push(13006, seq, key, unequip(body)); what = "脱下"; break;
            default:
                if (key) { push(cmd + 1, seq, true, new PB().u(1, 0).bytes()); what = "空的成功包"; }
                else what = "不回";
        }
        if (log.size() < 300) log.add(cmd + " seq" + seq + (key ? " key" : "") + " 长" + body.length + " → " + what + "  " + hexHead(body, 48));
    }

    /** 往回包区放一个 CSPkg，下一帧游戏收 */
    static synchronized void push(int cmd, int seq, boolean key, byte[] body) {
        PB head = new PB().u(1, cmd).u(4, seq).u(6, 0);
        if (key) head.u(7, 1);
        byte[] p = new PB().m(1, head).b(2, body).bytes();
        if (rplWp + 4 + p.length + 4 > RPL_CAP) return;
        rpl.putInt(16 + rplWp, p.length);
        for (int i = 0; i < p.length; i++) rpl.put(16 + rplWp + 4 + i, p[i]);
        rplWp = (rplWp + 4 + p.length + 3) & ~3;
        VarHandle.releaseFence();
        rpl.putInt(0, rplWp);
        replied++;
    }

    // ---------------- 仓库 ----------------
    static final long ROLE_F = 10200000000L, ROLE_M = 10100000000L;
    /** gid → {id, 部位, 性别(角色才有)}；角色的 gid 就用它的编号 */
    static LinkedHashMap<Long, long[]> props;

    static void initProps() {
        if (props != null) return;
        props = new LinkedHashMap<>();
        boolean male = Mods.male;
        props.put(ROLE_F, new long[] { ROLE_F, male ? 0 : 1, 2 });
        props.put(ROLE_M, new long[] { ROLE_M, male ? 1 : 0, 1 });
        long[][] wear = { { 30302000000L, 5 }, { 30301000001L, 6 }, { 30306000000L, 9 }, { 30303000006L, 11 },
                          { 30213000003L, male ? 0 : 4 }, { 30318000000L, 0 } };
        long gid = 1001;
        for (long[] w : wear) props.put(gid++, new long[] { w[0], w[1], 0 });
    }
    static long role() { for (long[] p : props.values()) if (p[2] != 0 && p[1] == 1) return p[0]; return ROLE_F; }

    static PB prop(long gid, long[] p) {
        PB x = new PB().u(1, p[0]).u(2, gid).u(3, p[2] != 0 ? 0 : role()).u(4, 1).u(10, p[1]);
        if (p[2] != 0) x.m(13, new PB().u(1, p[2]));
        return x;
    }
    static synchronized byte[] depositProps() {
        initProps();
        PB res = new PB().u(1, 0);
        for (java.util.Map.Entry<Long, long[]> e : props.entrySet()) res.m(2, prop(e.getKey(), e.getValue()));
        return res.u(5, role()).bytes();
    }
    static long propGid(byte[] body, int field) {
        PB.R r = new PB.R(body); long id = 0, gid = 0;
        while (r.next()) if (r.field == field && r.wire == 2) { PB.R p = r.sub(); while (p.next()) { if (p.field == 1) id = p.val; else if (p.field == 2) gid = p.val; } }
        if (props.containsKey(gid)) return gid;
        for (java.util.Map.Entry<Long, long[]> e : props.entrySet()) if (e.getValue()[0] == id) return e.getKey();
        return -1;
    }
    static synchronized byte[] equip(byte[] body) {
        initProps();
        long gid = propGid(body, 1); int pos = 0;
        PB.R r = new PB.R(body); while (r.next()) if (r.field == 2 && r.wire == 0) pos = (int) r.val;
        PB res = new PB().u(1, gid < 0 ? 1 : 0);
        if (gid >= 0) {
            if (pos == 0) pos = (int) props.get(gid)[1];
            for (java.util.Map.Entry<Long, long[]> e : props.entrySet())
                if (e.getValue()[1] == pos && e.getKey() != gid) { e.getValue()[1] = 0; res.m(2, prop(e.getKey(), e.getValue())); }
            props.get(gid)[1] = pos;
            res.m(2, prop(gid, props.get(gid)));
        }
        return res.u(3, role()).bytes();
    }
    static synchronized byte[] unequip(byte[] body) {
        initProps();
        long gid = propGid(body, 1);
        PB res = new PB().u(1, gid < 0 ? 1 : 0);
        if (gid >= 0) { props.get(gid)[1] = 0; res.m(2, prop(gid, props.get(gid))); }
        return res.bytes();
    }

    static String hexHead(byte[] d, int n) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < Math.min(n, d.length); i++) s.append(String.format("%02x", d[i] & 0xff));
        return d.length > n ? s + "…" : s.toString();
    }

    public static synchronized String report() {
        StringBuilder sb = new StringBuilder(state).append('\n');
        if (req == null) return sb.toString();
        sb.append("请求 ").append(req.getInt(4)).append(" 个（丢 ").append(req.getInt(12)).append("），回包 ").append(replied)
          .append(" 个，游戏已收 ").append(rpl.getInt(8)).append(" 个\n");
        for (String l : log) sb.append(l).append('\n');
        return sb.toString();
    }
}
