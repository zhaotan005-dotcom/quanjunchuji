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
 * 邮件：开启时推一封补给邮件（25007），25001 列表、25003 领取（附件进仓库再推 13002），Z 菜单可再发。
 * 其它需要回应的关键请求（key_pkg）回一个空的成功包，免得大厅一直等。
 */
public class Server {
    static final long SLACK = 0x4a2d540L, SLACK_END = 0x4a2e000L;
    static final long SEND_AT = 0xf950e0L, TICK_AT = 0xb665a8L;   // v1.63：收包改挂 FEngineLoop::Tick（每帧必跑；v1.62 的 UpdateService::Tick 实测大厅不跑）
    static final String SEND_ORIG = "f0482de910b08de2", TICK_ORIG = "f04f2de91cb08de2";
    static final int REQ_CAP = 1 << 20, RPL_CAP = 1 << 20, RH = 32;   // 回包区头 32 字节：写/读/已收/连接器/UpdateService/帧数
    static final long CONN_VT = 0x4c7deb4L, CONN_INIT = 0xf925d0L;       // ConnectorService 的 vtable（+0x11c 是 Init）

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
    /** TICK 段（v1.63）：挂在 FEngineLoop::Tick 开头。连接器对象（+12）找到前只记帧数，找到后把回包逐个交给 vt+0x164 */
    static String tickCave(int buf, int ret) {
        return "f3412de9" + Mods.armMov(false, 4, buf & 0xffff) + Mods.armMov(true, 4, (buf >>> 16) & 0xffff)
            + "100084e5146094e5016086e2146084e50c8094e5000058e31a00000a045094e5006094e55bf07ff5060055e1150000aa086094e51c2094e5020056e1110000aa207084e2057087e0002097e5041087e2045085e2025085e0035085e20350c5e3045084e5086094e5016086e2086084e50800a0e10030a0e300c098e564c19ce53cff2fe1e4ffffeaf341bde8f04f2de91cb08de204f01fe5"
            + w32(ret);
    }
    static String hook(int target) { return "04f01fe5" + w32(target); }

    /** v1.65 SEND 段前面加一道门：回包区 +24（开关）是 0 时照原样执行 SendRawBuffer（不影响游戏），是 1 才进我们的服务端 */
    static String sendGate(int rplBuf, int ret) {
        return "04402de5" + Mods.armMov(false, 4, rplBuf & 0xffff) + Mods.armMov(true, 4, (rplBuf >>> 16) & 0xffff)
            + "184094e5" + "000054e3" + "04409de4" + "0300001a"      // ldr r4,[r4,#24]; cmp; ldr r4,[sp],#4; bne 服务端
            + SEND_ORIG + "04f01fe5" + w32(ret);
    }
    static volatile boolean hooked; static volatile String hookWhen = "没装";

    /** 写 cave 和两个钩子。v1.64 自检证明：游戏已经跑过的代码，运行中再改不生效（字节在、但一帧都没跑到），
     *  所以 v1.65 在 libUE4 刚加载、代码还没跑之前就装上（开关是 0，等于原样执行）。 */
    static synchronized String install(String when) {
        if (hooked) return "ok";
        long b = Mods.base();
        if (b == 0) return "找不到游戏库";
        if (!slackIsCode(b)) return "代码段空隙不可用";
        ByteBuffer q = ByteBuffer.allocateDirect(16 + REQ_CAP).order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer r = ByteBuffer.allocateDirect(RH + RPL_CAP).order(ByteOrder.LITTLE_ENDIAN);
        long qa = GameOverlay.bufAddr(q), ra = GameOverlay.bufAddr(r);
        if (qa <= 0 || ra <= 0 || qa > 0xffffffffL || ra > 0xffffffffL) return "拿不到内存地址";
        q.putInt(8, REQ_CAP);
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "rw")) {
            if (!bytesAt(m, b + SEND_AT, 8).equals(SEND_ORIG) || !bytesAt(m, b + TICK_AT, 8).equals(TICK_ORIG))
                return "游戏代码和预期不一样";
            long sc = b + SLACK, tc = b + SLACK + 0x100, gc = b + SLACK + 0x200;
            m.seek(sc); m.write(Patches.hex(sendCave((int) qa)));
            m.seek(tc); m.write(Patches.hex(tickCave((int) ra, (int) (b + TICK_AT + 8))));
            m.seek(gc); m.write(Patches.hex(sendGate((int) ra, (int) (b + SEND_AT + 8))));
            req = q; rpl = r; reqAddr = qa; rplAddr = ra;
            m.seek(b + TICK_AT); m.write(Patches.hex(hook((int) tc)));
            m.seek(b + SEND_AT); m.write(Patches.hex(hook((int) gc)));
        } catch (Throwable e) { return "出错 " + e.getMessage(); }
        hooked = true; hookWhen = when;
        return "ok";
    }
    /** 启动时调用：只装钩子，不开服务端 */
    public static void preinstall() {
        String r = install("启动时");
        LogCap.note("服务端钩子预装 → " + r);
        if (!"ok".equals(r)) hookWhen = "启动时没装上：" + r;
    }

    static volatile boolean on;
    public static synchronized String start() {
        if (on) return state;
        if (Con.inMatch) return state = "自建服务端：请在大厅里开";
        mark("开服务端，钩子：" + hookWhen);
        String r = install("开服务端时（运行中装，可能不生效）");
        if (!"ok".equals(r)) return state = "自建服务端：" + r + "，没开";
        on = true;
        rpl.putInt(28, 0);                             // v1.66：先不投递，按「投递 1 条」一条一条来
        // v1.67 排队顺序（逐条测哪条会闪退）：1 邮件推送 25007，2 仓库 13002 不带角色，3 仓库 13002 带角色
        mark("准备邮件");
        newMail();
        mark("准备 13002（不带角色）");
        push(13002, 0, false, depositProps(false), "仓库-不带角色");
        mark("准备 13002（带角色）");
        push(13002, 0, false, depositProps(true), "仓库-带角色");
        mark("开 SEND 门");
        rpl.putInt(24, 1);                             // 打开 SEND 的门
        Thread t = new Thread(Server::loop, "zf-server"); t.setDaemon(true); t.start();
        Thread f = new Thread(Server::findConn, "zf-conn"); f.setDaemon(true); f.start();
        final int f0 = rpl.getInt(20);
        new Thread(() -> { try { Thread.sleep(1000); } catch (Throwable ignored) {} frames1s = rpl.getInt(20) - f0; }).start();
        mark("服务端已开（没重进大厅）");
        return state = "自建服务端：已开";
    }

    // ---------------- 找 ConnectorService 对象 ----------------
    // 回包要交给 ConnectorService 自己的收包函数，所以要它的对象地址。它是 UObject：+0 vtable，+4 ObjectFlags（0x10 = 默认对象 CDO，跳过）。
    // 先看 UpdateService 的 Outer（+0x18）里的字段，找不到再扫一遍可写的匿名内存。
    static volatile String connState = "连接器：还没找";
    static void findConn() {
        try {
            long b = Mods.base(), vt = b + CONN_VT;
            try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
                if (rd(m, vt + 0x11c) != b + CONN_INIT) { connState = "连接器：vtable 对不上，没找"; return; }
                connState = "连接器：正在扫内存"; mark("开始扫连接器");
                long t0 = System.currentTimeMillis(), c = scan(m, vt);
                long ms = System.currentTimeMillis() - t0;
                if (c == 0) { connState = "连接器：没找到（扫了 " + (scanned >> 20) + " MB，" + ms + " ms）"; return; }
                connInfo = "  连接器 +fa038=" + Long.toHexString(rd(m, c + 0xfa038)) + " +fa054=" + Long.toHexString(rd(m, c + 0xfa054))
                    + " 队列 " + rd(m, c + 0xfa05c);
                mark("找到连接器 " + Long.toHexString(c) + connInfo);
                rpl.putInt(12, (int) c);
                connState = "连接器：" + Long.toHexString(c) + "（扫了 " + (scanned >> 20) + " MB，" + ms + " ms）";
            }
        } catch (Throwable e) { connState = "连接器：出错 " + e; }
    }
    static volatile long scanned; static volatile String connInfo = "";
    static long rd(RandomAccessFile m, long a) {
        if (a < 0x1000 || a > 0xfffffff0L || (a & 3) != 0) return 0;
        try { byte[] x = new byte[4]; m.seek(a); m.readFully(x); return (x[0] & 0xff) | (x[1] & 0xff) << 8 | (x[2] & 0xff) << 16 | (x[3] & 0xffL) << 24; }
        catch (Throwable e) { return 0; }
    }
    static boolean isConn(RandomAccessFile m, long p, long vt) {
        if (p == 0 || rd(m, p) != vt) return false;
        long fl = rd(m, p + 4), idx = rd(m, p + 8), cls = rd(m, p + 0xc), out = rd(m, p + 0x18);
        return looksObj(fl, idx, cls, out);
    }
    /** UObject 头：不是 CDO，序号合理，类和 Outer 都是对齐的指针 */
    static boolean looksObj(long fl, long idx, long cls, long out) {
        return (fl & 0x10) == 0 && idx > 0 && idx < 0x400000 && cls > 0x10000 && (cls & 3) == 0 && out > 0x10000 && (out & 3) == 0;
    }
    static long scan(RandomAccessFile m, long vt) throws java.io.IOException {
        java.util.ArrayList<long[]> rs = new java.util.ArrayList<>();
        try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader("/proc/self/maps"))) {
            String l;
            while ((l = r.readLine()) != null) {
                String[] p = l.trim().split("\\s+");
                if (!p[1].startsWith("rw")) continue;
                String name = p.length > 5 ? p[5] : "";
                if (!(name.isEmpty() || name.startsWith("[anon:") || name.equals("[heap]")) || name.contains("stack")) continue;
                String[] ab = p[0].split("-");
                rs.add(new long[] { Long.parseLong(ab[0], 16), Long.parseLong(ab[1], 16) });
            }
        }
        byte[] buf = new byte[1 << 20];
        int[] w = new int[buf.length / 4];
        int v0 = (int) vt;
        scanned = 0;
        for (long[] g : rs) {
            for (long a = g[0]; a < g[1]; a += buf.length) {
                int n = (int) Math.min(buf.length, g[1] - a);
                try { m.seek(a); m.readFully(buf, 0, n); } catch (Throwable e) { break; }
                scanned += n;
                int k = n / 4;
                ByteBuffer.wrap(buf, 0, n).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().get(w, 0, k);
                for (int i = 0; i + 7 <= k; i++)
                    if (w[i] == v0 && looksObj(w[i + 1] & 0xffffffffL, w[i + 2] & 0xffffffffL, w[i + 3] & 0xffffffffL, w[i + 6] & 0xffffffffL))
                        return a + 4L * i;
            }
        }
        return 0;
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
            case 25001: push(25002, seq, key, mailList()); what = "邮件列表 " + mails.size() + " 封"; break;
            case 25003: what = "领取邮件"; push(25004, seq, key, readMails(body)); push(13002, 0, false, depositProps()); break;
            case 25005: push(25006, seq, key, new PB().u(1, 0).bytes()); what = "发邮件（回成功）"; break;
            default:
                if (key) { push(cmd + 1, seq, true, new PB().u(1, 0).bytes()); what = "空的成功包"; }
                else what = "不回";
        }
        if (log.size() < 300) log.add(cmd + " seq" + seq + (key ? " key" : "") + " 长" + body.length + " → " + what + "  " + hexHead(body, 48));
    }

    /** 往回包区放一个 CSPkg，下一帧游戏收 */
    static void push(int cmd, int seq, boolean key, byte[] body) { push(cmd, seq, key, body, null); }
    static synchronized void push(int cmd, int seq, boolean key, byte[] body, String tag) {
        PB head = new PB().u(1, cmd).u(4, seq).u(6, 0);
        if (key) head.u(7, 1);
        byte[] p = new PB().m(1, head).b(2, body).bytes();
        if (rplWp + 4 + p.length + 4 > RPL_CAP) return;
        rpl.putInt(RH + rplWp, p.length);
        for (int i = 0; i < p.length; i++) rpl.put(RH + rplWp + 4 + i, p[i]);
        rplWp = (rplWp + 4 + p.length + 3) & ~3;
        VarHandle.releaseFence();
        rpl.putInt(0, rplWp);
        replied++;
        cmds.add(cmd + (tag != null ? " " + tag : cmd == 25007 ? " 邮件推送" : "") + "（" + p.length + " 字节）");
    }
    static final ArrayList<String> cmds = new ArrayList<>();

    // ---------------- v1.66 闪退定位：每一步先同步写进文件，下次启动能看到上次停在哪 ----------------
    static boolean rotated;
    static synchronized java.io.File stepFile() {
        android.app.Activity a = Con.act; if (a == null) return null;
        java.io.File f = new java.io.File(a.getFilesDir(), "zf_server_steps.txt");
        if (!rotated) {   // 每次启动第一次用到时，把上次的记录改名保存
            rotated = true;
            java.io.File old = new java.io.File(f.getParentFile(), "zf_server_steps_prev.txt");
            if (f.exists()) { old.delete(); f.renameTo(old); }
        }
        return f;
    }
    static String lastRun;
    static synchronized void mark(String s) {
        java.io.File f = stepFile(); if (f == null) return;
        try (java.io.FileOutputStream o = new java.io.FileOutputStream(f, true)) {
            o.write((new java.text.SimpleDateFormat("HH:mm:ss.SSS").format(new java.util.Date()) + " " + s + "\n").getBytes("UTF-8"));
            o.getFD().sync();
        } catch (Throwable ignored) {}
    }
    /** 上一次运行最后几步（开服务端时把旧文件改名保存） */
    static String readSteps(java.io.File f, int n) {
        if (f == null || !f.exists()) return "（没有）";
        try { java.util.List<String> l = java.nio.file.Files.readAllLines(f.toPath());
            return String.join("\n  ", l.subList(Math.max(0, l.size() - n), l.size())); }
        catch (Throwable e) { return "读不到"; }
    }
    static void rotateSteps() {
        java.io.File f = stepFile(); if (f == null) return;
        java.io.File old = new java.io.File(f.getParentFile(), "zf_server_steps_prev.txt");
        if (f.exists()) { old.delete(); f.renameTo(old); }
    }
    /** 一条一条投递：先记下要投哪条，再把上限 +1，游戏线程下一帧投它 */
    public static String deliverOne() {
        if (!on) return "先按「开启服务端」";
        if (rpl.getInt(12) == 0) return "还没找到连接器，等几秒";
        int n = rpl.getInt(28), got = rpl.getInt(8);
        if (got < n) return "上一条还没被游戏收走（已收 " + got + "/" + n + "）";
        if (n >= cmds.size()) return "没有新回包了（已投 " + n + " 条）";
        mark("投递第 " + (n + 1) + " 条：" + cmds.get(n));
        rpl.putInt(28, n + 1);
        try { Thread.sleep(300); } catch (Throwable ignored) {}
        String r = "第 " + (n + 1) + " 条 " + cmds.get(n) + (rpl.getInt(8) > n ? " 游戏已收" : " 游戏还没收");
        mark("  → " + r);
        for (int i = 1; i <= 5; i++) { try { Thread.sleep(1000); } catch (Throwable ignored) {} mark("  收后 " + i + " 秒还活着"); }
        return r + "，5 秒没闪退" + (n + 1 < cmds.size() ? "，下一条 " + cmds.get(n + 1) : "");
    }

    /** 跳过 1 条：不交给游戏，直接把读指针挪过去 */
    public static synchronized String skipOne() {
        if (!on) return "先按「开启服务端」";
        int n = rpl.getInt(28), got = rpl.getInt(8);
        if (got < n) return "上一条还没被游戏收走";
        if (n >= cmds.size()) return "没有可跳过的了";
        int rp = rpl.getInt(4), len = rpl.getInt(RH + rp);
        rpl.putInt(4, (rp + 4 + len + 3) & ~3);
        rpl.putInt(8, got + 1);
        rpl.putInt(28, n + 1);
        mark("跳过第 " + (n + 1) + " 条：" + cmds.get(n));
        return "跳过了第 " + (n + 1) + " 条 " + cmds.get(n) + (n + 1 < cmds.size() ? "，下一条 " + cmds.get(n + 1) : "");
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
    static byte[] depositProps() { return depositProps(true); }
    static synchronized byte[] depositProps(boolean roles) {
        initProps();
        PB res = new PB().u(1, 0);
        for (java.util.Map.Entry<Long, long[]> e : props.entrySet())
            if (roles || e.getValue()[2] == 0) res.m(2, prop(e.getKey(), e.getValue()));
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

    // ---------------- 邮件 ----------------
    // 25001/25002 取列表，25003/25004 读+领取（附件进仓库），25007 新邮件推送。邮件类型 1 = 系统邮件。
    static final int MAIL_SYS = 1;
    /** 附件：{编号, 性别(角色才有，1 男 2 女), 脸, 发型}。脸/发型编号和多数衣服编号没在手机上验证过，游戏不认的会被忽略。 */
    static final long[][] GIFT = {
        { ROLE_M, 1, 1, 0 }, { ROLE_F, 2, 1, 0 },
        { 30213000000L }, { 30213000001L }, { 30213000002L }, { 30213000003L }, { 30213000004L }, { 30213000005L },
        { 30301000000L }, { 30301000071L }, { 30303000005L }, { 30307000007L }, { 30232000006L },
        { 30315000050L }, { 30315000061L }, { 30323000002L },
        { 30325000000L }, { 30325000001L }, { 30325000002L }, { 30325000003L }, { 30325000004L }, { 30325000005L },
        { 30318000000L },
    };
    static final ArrayList<long[]> mails = new ArrayList<>();   // {index, 已读}
    static int mailSeq;

    static PB mail(long[] m) {
        PB att = new PB();
        for (long[] g : GIFT) {
            PB p = new PB().u(1, g[0]).u(4, 1);
            if (g.length > 1) p.m(13, new PB().u(1, g[1]).u(2, g[2]));
            att.m(1, p);
        }
        return new PB().u(1, m[2]).s(3, "补给").s(4, "服务端已开启，点领取把角色、发型和衣服放进仓库。")
            .m(6, att).u(7, m[1]).u(8, m[0]).u(10, 0);
    }
    static PB mailListOf(java.util.List<long[]> ms) {
        PB l = new PB().u(1, MAIL_SYS);
        for (long[] m : ms) l.m(2, mail(m));
        return l;
    }
    static synchronized byte[] mailList() { return new PB().u(1, 0).m(2, mailListOf(mails)).bytes(); }

    /** 新建一封邮件并推给游戏（25007） */
    static synchronized void newMail() {
        long[] m = { ++mailSeq, 0, System.currentTimeMillis() / 1000 };
        mails.add(m);
        push(25007, 0, false, new PB().m(1, mailListOf(java.util.Collections.singletonList(m))).bytes());
    }
    /** Z 菜单「发邮件」：服务端开着才行 */
    public static String sendMail() {
        if (!on) return state = "自建服务端：先按「开启服务端」";
        newMail();
        return "已发一封补给邮件，去邮箱领取";
    }

    /** 读邮件 = 领取附件：没有的物品加进仓库，回 PlayerDataChange */
    static synchronized byte[] readMails(byte[] body) {
        initProps();
        ArrayList<Integer> idx = new ArrayList<>(); int type = MAIL_SYS; boolean del = false;
        PB.R r = new PB.R(body);
        while (r.next()) {
            if (r.field == 1 && r.wire == 0) type = (int) r.val;
            else if (r.field == 2 && r.wire == 0) idx.add((int) r.val);
            else if (r.field == 2 && r.wire == 2) { PB.R q = r.sub(); while (q.next()) idx.add((int) q.val); }   // packed
            else if (r.field == 3) del = r.val != 0;
        }
        PB change = new PB(); PB res = new PB().u(1, 0);
        for (long[] m : new ArrayList<>(mails)) {
            if (!idx.contains((int) m[0])) continue;
            if (m[1] == 0) {
                m[1] = 1;
                for (long[] g : GIFT) {
                    long gid = addProp(g);
                    if (gid != 0) change.m(2, prop(gid, props.get(gid)));
                }
            }
            if (del) mails.remove(m);
        }
        res.m(2, change).u(3, type);
        for (int i : idx) res.u(4, i);
        if (del) res.u(5, 1);
        return res.bytes();
    }
    /** 仓库里没有就加，返回 gid；已有返回 0 */
    static long addProp(long[] g) {
        for (long[] p : props.values()) if (p[0] == g[0]) return 0;
        if (g.length > 1) { props.put(g[0], new long[] { g[0], 0, g[1] }); return g[0]; }
        long gid = 1001; while (props.containsKey(gid)) gid++;
        props.put(gid, new long[] { g[0], 0, 0 });
        return gid;
    }

    static String hexHead(byte[] d, int n) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < Math.min(n, d.length); i++) s.append(String.format("%02x", d[i] & 0xff));
        return d.length > n ? s + "…" : s.toString();
    }

    static volatile int frames1s = -1;
    /** v1.64：把钩子、cave、缓冲区从游戏内存里直接读回来，看到底装上没有 */
    static String selfCheck() {
        long b = Mods.base();
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
            return "自检 TICK@" + Long.toHexString(TICK_AT) + "=" + bytesAt(m, b + TICK_AT, 8)
                + " SEND@" + Long.toHexString(SEND_AT) + "=" + bytesAt(m, b + SEND_AT, 8)
                + "\n  cave=" + bytesAt(m, b + SLACK + 0x100, 16) + " 缓冲=" + Long.toHexString(rplAddr)
                + " 直读帧=" + rd(m, rplAddr + 20) + " 直读连接器=" + Long.toHexString(rd(m, rplAddr + 12))
                + "\n  空隙段=" + mapLine(b + SLACK) + "\n  Tick段=" + mapLine(b + TICK_AT);
        } catch (Throwable e) { return "自检出错 " + e; }
    }
    static String mapLine(long a) {
        try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader("/proc/self/maps"))) {
            String l;
            while ((l = r.readLine()) != null) {
                String[] p = l.trim().split("\\s+"), ab = p[0].split("-");
                long s = Long.parseLong(ab[0], 16), e = Long.parseLong(ab[1], 16);
                if (s <= a && a < e) return p[0] + " " + p[1] + " " + p[2] + (p.length > 5 ? " " + p[5].replaceAll(".*/", "") : "");
            }
        } catch (Throwable ignored) {}
        return "?";
    }

    public static synchronized String report() {
        StringBuilder sb = new StringBuilder(state).append('\n');
        sb.append("钩子：").append(hookWhen).append('\n');
        java.io.File sf = stepFile();
        if (sf != null) sb.append("上次运行最后几步：\n  ").append(readSteps(new java.io.File(sf.getParentFile(), "zf_server_steps_prev.txt"), 8))
            .append("\n这次：\n  ").append(readSteps(sf, 8)).append('\n');
        if (req == null) return sb.toString();
        sb.append("请求 ").append(req.getInt(4)).append(" 个（丢 ").append(req.getInt(12)).append("），回包 ").append(replied)
          .append(" 个，游戏已收 ").append(rpl.getInt(8)).append(" 个\n")
          .append(connState).append("，帧 ").append(rpl.getInt(20)).append("（开后 1 秒多跑 ").append(frames1s).append("）\n");
        sb.append(connInfo).append("\n已投 ").append(rpl.getInt(28)).append(" 条，排队 ").append(cmds).append('\n');
        sb.append(selfCheck()).append('\n');
        sb.append("邮件 ").append(mails.size()).append(" 封\n");
        for (String l : log) sb.append(l).append('\n');
        return sb.toString();
    }
}
