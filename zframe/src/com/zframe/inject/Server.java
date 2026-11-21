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
    static final int REQ_CAP = 1 << 20, RPL_CAP = 8 << 20, RH = 32;   // v1.78：1MB → 8MB（每次穿脱推 12KB 仓库，1MB 玩久了会满，满了回包被丢）
    static int kicks;   // 回包区头 32 字节：写/读/已收/连接器/UpdateService/帧数
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
    static String tickCave(int buf, int ret) { return tickCave(buf, ret, 0); }
    /** v3.25：每帧先看「游戏线程调用槽」cb：+0 函数（非 0 才调，调前清 0）、+4..+16 r0~r3、+20 返回值、+24 调用次数。
     *  启动阶段 Con.exec 还不能用（v2.69 实测），开机更新页要在游戏线程里调函数，就走这里。cb=0 时不加这段 */
    static String tickCave(int buf, int ret, int cb) {
        String call = cb == 0 ? "" : Mods.armMov(false, 5, cb & 0xffff) + Mods.armMov(true, 5, (cb >>> 16) & 0xffff)
            + "00c095e5" + "00005ce3" + "0a00000a"                       // ldr ip,[r5]; cmp ip,#0; beq skip
            + "040095e5" + "081095e5" + "0c2095e5" + "103095e5"          // r0..r3
            + "0060a0e3" + "006085e5" + "3cff2fe1"                       // mov r6,#0; str r6,[r5]; blx ip
            + "140085e5" + "186095e5" + "016086e2" + "186085e5";         // 返回值、次数+1
        return "f3412de9" + Mods.armMov(false, 4, buf & 0xffff) + Mods.armMov(true, 4, (buf >>> 16) & 0xffff)
            + "100084e5146094e5016086e2146084e5" + call + "0c8094e5000058e31a00000a045094e5006094e55bf07ff5060055e1150000aa086094e51c2094e5020056e1110000aa207084e2057087e0002097e5041087e2045085e2025085e0035085e20350c5e3045084e5086094e5016086e2086084e50800a0e10030a0e300c098e564c19ce53cff2fe1e4ffffeaf341bde8f04f2de91cb08de204f01fe5"
            + w32(ret);
    }
    static String hook(int target) { return "04f01fe5" + w32(target); }

    /** v1.65 SEND 段前面加一道门：回包区 +24（开关）是 0 时照原样执行 SendRawBuffer（不影响游戏），是 1 才进我们的服务端 */
    static String sendGate(int rplBuf, int ret, int server) {
        return "04402de5" + Mods.armMov(false, 4, rplBuf & 0xffff) + Mods.armMov(true, 4, (rplBuf >>> 16) & 0xffff)
            + "184094e5" + "000054e3" + "04409de4" + "0300001a"      // ldr r4,[r4,#24]; cmp; ldr r4,[sp],#4; bne 服务端
            + SEND_ORIG + "04f01fe5" + w32(ret)
            + "04f01fe5" + w32(server);                              // v1.69 修：门开时跳到 SEND 段（v1.65~1.68 这里是空的 → 一发请求就闪退）
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
            m.seek(tc); m.write(Patches.hex(tickCave((int) ra, (int) (b + TICK_AT + 8), (int) callBuf())));
            m.seek(gc); m.write(Patches.hex(sendGate((int) ra, (int) (b + SEND_AT + 8), (int) sc)));
            req = q; rpl = r; reqAddr = qa; rplAddr = ra;
            m.seek(b + TICK_AT); m.write(Patches.hex(hook((int) tc)));
            m.seek(b + SEND_AT); m.write(Patches.hex(hook((int) gc)));
        } catch (Throwable e) { return "出错 " + e.getMessage(); }
        hooked = true; hookWhen = when;
        return "ok";
    }
    /** v3.25 游戏线程调用槽（tick cave 每帧看一次） */
    static ByteBuffer cb; static long cbAddr;
    static synchronized long callBuf() {
        if (cb == null) { cb = ByteBuffer.allocateDirect(64).order(ByteOrder.LITTLE_ENDIAN); cbAddr = GameOverlay.bufAddr(cb); }
        return cbAddr;
    }
    static volatile boolean tickOnly;
    /** v3.25：联机两个包不装服务端，但开机更新页要「游戏线程调用槽」→ 只装 TICK 段（连接器一直是 0，不投递任何包） */
    static synchronized String installTick() {
        if (hooked || tickOnly) return "ok";
        long b = Mods.base();
        if (b == 0) return "找不到游戏库";
        if (!slackIsCode(b)) return "代码段空隙不可用";
        ByteBuffer r = ByteBuffer.allocateDirect(RH + 64).order(ByteOrder.LITTLE_ENDIAN);
        long ra = GameOverlay.bufAddr(r), ca = callBuf();
        if (ra <= 0 || ca <= 0 || ra > 0xffffffffL || ca > 0xffffffffL) return "拿不到内存地址";
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "rw")) {
            if (!bytesAt(m, b + TICK_AT, 8).equals(TICK_ORIG)) return "游戏代码和预期不一样";
            long tc = b + SLACK + 0x100;
            m.seek(tc); m.write(Patches.hex(tickCave((int) ra, (int) (b + TICK_AT + 8), (int) ca)));
            tickRpl = r;
            m.seek(b + TICK_AT); m.write(Patches.hex(hook((int) tc)));
        } catch (Throwable e) { return "出错 " + e.getMessage(); }
        tickOnly = true;
        return "ok";
    }
    static ByteBuffer tickRpl;
    static boolean callReady() { return (hooked || tickOnly) && cb != null; }
    /** 在游戏线程调 fn(r0,r1,r2,r3)，等下一帧跑完（最多 waitMs）；返回 {调了没有(1/0), 返回值} */
    static synchronized long[] gcall(long fn, long r0, long r1, long r2, long r3, int waitMs) throws InterruptedException {
        if (!callReady()) return new long[] { 0, 0 };
        int n0 = cb.getInt(24);
        cb.putInt(4, (int) r0); cb.putInt(8, (int) r1); cb.putInt(12, (int) r2); cb.putInt(16, (int) r3);
        cb.putInt(0, (int) fn);
        for (int t = 0; t < waitMs; t += 10) {
            Thread.sleep(10);
            if (cb.getInt(24) != n0) return new long[] { 1, cb.getInt(20) & 0xffffffffL };
        }
        if (cb.getInt(0) != 0) cb.putInt(0, 0);   // 一直没跑到（游戏线程卡住）→ 撤销，免得以后突然调
        return new long[] { cb.getInt(24) != n0 ? 1 : 0, cb.getInt(20) & 0xffffffffL };
    }
    /** 启动时调用：只装钩子，不开服务端 */
    public static void preinstall() {
        String r = install("启动时");
        LogCap.note("服务端钩子预装 → " + r);
        if (!"ok".equals(r)) hookWhen = "启动时没装上：" + r;
    }

    static volatile boolean on;
    /** v2.09：开房/加入前把服务端钩子变成直通（关 SEND 门、停投递），排查联机进图闪退是不是它 */
    static void quiet() {
        try { if (rpl != null) { rpl.putInt(24, 0); rpl.putInt(28, 0); } mark("联机：服务端钩子已静默"); } catch (Throwable e) { mark("静默失败 " + e); }
    }
    public static synchronized String start() {
        if (on) return state;
        if (Con.inMatch) return state = "自建服务端：请在大厅里开";
        mark("开服务端，钩子：" + hookWhen);
        String r = install("开服务端时（运行中装，可能不生效）");
        if (!"ok".equals(r)) return state = "自建服务端：" + r + "，没开";
        on = true;
        rpl.putInt(28, manual ? 0 : 0x7fffffff);   // 默认自动投递；调试时可改成逐条
        // v1.71：v1.65~1.68 的闪退是 SEND 门的 bug（已修），恢复自动投递：邮件推送 + 仓库
        // v1.74：开服务端前游戏发的 36001（关键消息，seq1）一直没回，游戏就一直认为「有可靠消息在等回复」，
        // 后面的穿/脱(13003/13005)只能排队，再收一个包才发出去 → 大部分被丢。先把这几个补回掉。
        push(36002, 1, true, new PB().u(1, 0).bytes(), "补回 36001");
        push(22006, 2, false, new PB().u(1, 0).bytes(), "补回 22005");
        push(30015, 5, false, new PB().u(1, 0).bytes(), "补回 30014");
        mark("补给直接入库");
        giveGift();   // v1.90：不再发邮件，角色/发型/衣服直接放进仓库（用户要求）
        mark("准备 13002");
        push(13002, 0, false, depositProps(true), "仓库");
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
                new Thread(() -> { try { Thread.sleep(3000); } catch (Throwable ignored) {} allSkins(); }, "zf-skins").start();
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
            try { Thread.sleep(30); poll(); kick(); } catch (Throwable ignored) {}
        }
    }

    /** v1.78：游戏的可靠消息「排队」只在收到任何包时才往外发（SendPendingMsg after recv）。
     *  服务端开之前游戏发的 83001 seq8 没人回，之后的穿脱/匹配请求都排队，等下一个随便什么包进来才发 → 延迟十几秒、冲突。
     *  每 300ms 塞一个没人处理的通知（5017 是客户端→服务器的日志通知，客户端没有处理函数），让排队的消息马上发出来。 */
    static long lastKick;
    static void kick() {
        long now = System.currentTimeMillis();
        if (!on || rpl == null || now - lastKick < 300) return;
        lastKick = now;
        if (rpl.getInt(12) == 0) return;   // 连接器还没找到
        pushQuiet(5017);
        kicks++;
    }
    static synchronized void pushQuiet(int cmd) {
        byte[] p = new PB().m(1, new PB().u(1, cmd).u(4, 0).u(6, 0)).b(2, new byte[0]).bytes();
        if (rplWp + 4 + p.length + 4 > RPL_CAP) return;
        rpl.putInt(RH + rplWp, p.length);
        for (int i = 0; i < p.length; i++) rpl.put(RH + rplWp + 4 + i, p[i]);
        rplWp = (rplWp + 4 + p.length + 3) & ~3;
        VarHandle.releaseFence();
        rpl.putInt(0, rplWp);
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
            case 13003: {
                push(13004, seq, key, equip(body)); saveProps(); what = "穿上";
                long id = reqId(body);
                if (id / 100000000L == 101 || id / 100000000L == 102) { syncWear(); pushStore(); what = "换角色/发型"; followHairColor(id); }
                else if (id / 100000000L == 405) what = "穿上降落伞（不再推仓库/重穿人物）";   // v3.64 用户：装降落伞时预览里冒出人物
                else followPreview("ReqStoreEquipProp", id);
                break;
            }
            case 13005: push(13006, seq, key, unequip(body)); saveProps(); what = "脱下"; { long uid = reqId(body); if (uid / 100000000L != 405) followPreview("ReqStoreUnEquipProp", uid); } break;
            case 25001: push(25002, seq, key, mailList()); what = "邮件列表 " + mails.size() + " 封"; break;
            case 25003: {
                int before = props == null ? 0 : props.size();
                push(25004, seq, key, readMails(body));
                boolean added = props != null && props.size() > before;
                if (added) { push(13002, 0, false, depositProps()); saveProps(); }   // v1.71：只有真加了东西才推仓库，连点不会刷屏
                what = added ? "领取邮件，入库" : "领取邮件（已领过）";
                break;
            }
            // v1.78：15001 = 开始匹配。单机游戏发完就直接进图，回成功的话回到大厅会以为还在匹配（自动匹配、取消不了）→ 回失败
            case 15001: push(15002, seq, key, new PB().u(1, 1).bytes()); what = "开始匹配（回失败，单机直接进图）"; break;
            // v3.39 用户：训练岛里设置的「退出」没反应 → 收到退出对局请求（15003/15005）就回成功并直接回大厅
            case 15003: case 15005: push(cmd + 1, seq, key, new PB().u(1, 0).bytes()); what = "退出对局（回成功）"; if (Con.training && Con.inMatch) new Thread(() -> GameOverlay.toastAny(Lan.leave()), "zf-quit").start(); break;
            case 25005: push(25006, seq, key, new PB().u(1, 0).bytes()); what = "发邮件（回成功）"; break;
            // v2.48：军需（自选抽奖 CustomLottery）51001 列表 / 51003 提交 / 51005 抽 / 51007 重置
            case 51001: push(51002, seq, key, lotList()); what = "军需列表"; break;
            case 51003: push(51004, seq, key, lotCommit(body)); what = "军需提交"; break;
            case 51005: { byte[] r5 = lotRand(); push(51006, seq, key, r5); what = "军需抽奖 " + lotLast;
                if (lotLastNew) { push(13002, 0, false, depositProps()); saveProps(); } break; }
            case 51007: push(51008, seq, key, lotReset()); what = "军需重置"; break;
            default:
                // v1.73：非关键消息也回空的成功包。之前 83001 不回，游戏一直等 83002，后面的穿/脱(13003/13005)全被卡住丢掉
                push(cmd + 1, seq, key, new PB().u(1, 0).bytes()); what = "空的成功包";
        }
        if (log.size() < 300) log.add(cmd + " seq" + seq + (key ? " key" : "") + " 长" + body.length + " → " + what + "  " + hexHead(body, 48));
        // v3.64 用户：大厅手持枪展示、军需点了没反应 → 点这些（或没见过的请求）4 秒后自动传一份大厅日志（单机也传，只上传），看游戏到底发了什么
        long rc = (cmd == 13003 || cmd == 13005) ? reqId(body) / 100000000L : 0;
        if (!SEEN.contains(cmd) || (rc != 0 && (rc < 300 || rc >= 400) && rc != 101 && rc != 102 && rc != 405) || (cmd >= 31000 && cmd < 32000) || (cmd >= 38000 && cmd < 39000) || (cmd >= 51000 && cmd < 52000)) opLog(cmd);
    }
    static final java.util.Set<Integer> SEEN = new java.util.HashSet<>(java.util.Arrays.asList(10001, 15016, 44033, 37010, 36001, 30014, 22005, 22003, 73140, 83001, 15001, 15003, 15005, 13001, 13003, 13005, 25001, 25003, 25005));
    static volatile long opAt;
    static void opLog(int cmd) {
        long now = System.currentTimeMillis();
        if (now - opAt < 45000) return;
        opAt = now;
        new Thread(() -> {
            try { Thread.sleep(4000); } catch (Throwable ignored) {}
            if (Con.act == null || Con.inMatch) return;
            mark("大厅操作日志：请求 " + cmd);
            AvatarLog.manual = true; try { AvatarLog.export(Con.act, "lobbyop-"); } catch (Throwable ignored) {} finally { AvatarLog.manual = false; }
        }, "zf-oplog").start();
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
    static volatile boolean manual;   // 调试用：true = 回包排队，按「投递 1 条」逐条交

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

    /** v1.70 自动记录：上次运行用过服务端（留下了步骤文件），这次进大厅 6 秒后自动导出一份诊断到 下载/ZFrame/crash-*.txt，每份记录只存一次 */
    public static void autoCrashSave() {
        try {   // v3.64：单机也上传闪退诊断（只上传，不存手机），训练场闪退要看
            // v2.17：任何闪退（Bugly 留了新的 tomb_*.txt）下次进大厅自动存一份 crash-*.txt，每个 tomb 只存一次（大厅已去掉「导出诊断」）
            try {
                java.io.File[] ts = new java.io.File("/data/data/com.tencent.tmgp.pubgm/app_bugly").listFiles((d, n) -> n.startsWith("tomb"));
                java.io.File nt = null;
                if (ts != null) for (java.io.File t : ts) if (nt == null || t.lastModified() > nt.lastModified()) nt = t;
                java.io.File st = Con.act == null ? null : new java.io.File(Con.act.getFilesDir(), "zf_tomb_saved");
                if (nt != null && st != null && System.currentTimeMillis() - nt.lastModified() < 24 * 3600_000L) {
                    String k = nt.getName();
                    if (!(st.exists() && k.equals(new String(java.nio.file.Files.readAllBytes(st.toPath()), "UTF-8")))) {
                        java.nio.file.Files.write(st.toPath(), k.getBytes("UTF-8"));
                        AvatarLog.manual = true; try { AvatarLog.export(Con.act, "crash-"); } finally { AvatarLog.manual = false; }
                        return;
                    }
                }
            } catch (Throwable ignored) {}
            if (AvatarLog.off()) return;
            java.io.File f = stepFile(); if (f == null) return;
            java.io.File prev = new java.io.File(f.getParentFile(), "zf_server_steps_prev.txt");
            if (!prev.exists()) return;
            // v2.09b：只在上次是联机加载中途死掉时自动存（最后一行是开房/加入加载的心跳），平时不存
            String last = "";
            for (String l : new String(java.nio.file.Files.readAllBytes(prev.toPath()), "UTF-8").split("\n")) if (!l.trim().isEmpty()) last = l;
            if (!(last.contains("开房加载") || last.contains("加入加载")) || last.contains("已进图")) return;
            java.io.File done = new java.io.File(f.getParentFile(), "zf_crash_saved");
            String key = prev.lastModified() + "/" + prev.length();
            if (done.exists() && key.equals(new String(java.nio.file.Files.readAllBytes(done.toPath()), "UTF-8"))) return;
            java.nio.file.Files.write(done.toPath(), key.getBytes("UTF-8"));
            String r = AvatarLog.export(Con.act, "crash-");
            GameOverlay.toastAny("上次联机闪退，诊断" + r);
        } catch (Throwable ignored) {}
    }

    /** 自动投递：上限放到最大，回包一来就交给游戏 */
    public static String deliverAll() {
        if (!on) return "先按「开启服务端」";
        mark("自动投递开（排队 " + cmds.size() + " 条，已投 " + rpl.getInt(28) + "）");
        rpl.putInt(28, 0x7fffffff);
        return "自动投递已开，回包会直接交给游戏";
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
        if (loadProps()) {
            // v1.80：v1.79 按名字找口罩找错了（名字是分类标签），放进去的 30325 整套等游戏不认的编号清掉
            for (long b : BAD) props.values().removeIf(p -> p[0] == b && p[1] == 0);
            java.io.File mf = maskFile(); if (mf != null) mf.delete();
            return;
        }
        boolean male = Mods.male;
        props.put(ROLE_F, new long[] { ROLE_F, male ? 0 : 1, 2, 0 });
        props.put(ROLE_M, new long[] { ROLE_M, male ? 1 : 0, 1, 0 });
        // v1.73：初始不再带风衣（改成邮件领取），背包 30306000000 默认不穿
        long[][] wear = { { 30302000000L, 302 }, { 30301000001L, 301 }, { 30306000000L, 0 }, { 30303000006L, 303 },
                          { 30213000003L, male ? 0 : 213 } };
        long gid = 1001;
        for (long[] w : wear) props.put(gid++, new long[] { w[0], w[1], 0 });
    }
    // v1.73：仓库存到 filesDir/zf_props.txt，重启游戏还在。一行：gid 编号 位置 性别
    static java.io.File propsFile() {
        android.content.Context a = Con.act;
        if (a == null) try { a = (android.content.Context) Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null); } catch (Throwable ignored) {}
        return a == null ? null : new java.io.File(a.getFilesDir(), "zf_props.txt");
    }
    /** v1.76：把「角色 + 穿着的衣服」交给出生衣服钩子（进对局时用）。没有存档时不改 */
    static synchronized void syncWear() {
        try {
            if (props == null) { java.io.File f = propsFile(); if (f == null || !f.exists()) return; initProps(); }
            ArrayList<Long> l = new ArrayList<>();
            l.add(role());
            // 列表容量有限（默认一般 6 格），按优先级排：自己换上的衣服 → 发型 → 默认衣服
            java.util.List<Long> def = java.util.Arrays.asList(30301000001L, 30302000000L, 30303000006L, 30306000000L);
            for (int pass = 0; pass < 3; pass++)
                for (long[] p : props.values()) {
                    if (p[2] != 0 || p[1] == 0 || l.contains(p[0])) continue;
                    int k = def.contains(p[0]) ? 2 : (p[0] / 1000000 % 1000 == 113 || p[0] / 1000000 % 1000 == 213) ? 1 : 0;
                    if (k == pass) l.add(p[0]);
                }
            // v1.97：用户说以前对局里仓库衣服能脱、能捡。塞进「默认衣服」列表的东西是焊在身上的，
            //   所以出生列表只留 角色 + 发型 + 默认衣服，自己换上的衣服改成进对局后像普通物品一样发到身上（Bots.giveOutfit）。
            ArrayList<Long> base = new ArrayList<>(), own = new ArrayList<>();
            for (int i = 0; i < l.size(); i++) {
                long id = l.get(i); int cat = (int) (id / 1000000 % 1000);
                if (i == 0 || cat == 113 || cat == 213 || def.contains(id)) base.add(id); else own.add(id);
            }
            long[] a = new long[base.size()]; for (int i = 0; i < a.length; i++) a[i] = base.get(i);
            Mods.setWear(a);
            Bots.outfit = own;
        } catch (Throwable e) { LogCap.note("syncWear " + e); }
    }
    /** v1.76：游戏点「穿上/脱下」前会在日志里写一行 EquipItemsForHallRole（试穿后的整套，冲突已经由游戏处理好）。
     *  回包后读日志尾，按这一行重设穿着，再推一次 13002 让游戏的仓库缓存更新（13004 的列表游戏不拿来更新缓存）。 */
    /** v3.48 大厅换发色不生效：游戏 13003 发 hairColor（日志 ReqStoreEquipProp ... hairColor = 4），我们回的角色属性
     *  PropRoleAttr 只有 sex → 游戏 InitAvatarData HairColor 0。现在按角色记发色（存档第 5 列），回包带 hair_color(5)。
     *  发色先从请求 protobuf 里找（Prop.role_attr(13).hair_color(5)），找不到就读日志这一行。 */
    static void setHairColor(long role, long c, String from) {
        synchronized (Server.class) {
            initProps(); long[] v = props.get(role); if (v == null || v[2] == 0 || c < 0 || c > 64) return;
            if (v.length < 4) { v = java.util.Arrays.copyOf(v, 4); props.put(role, v); }
            boolean ch = v[3] != c; v[3] = c;
            for (java.util.Map.Entry<Long, long[]> e : props.entrySet()) {   // v3.49 穿着的发型道具也记上
                long[] h = e.getValue(); if (h[2] != 0 || h[1] != 213) continue;
                if (h.length < 4) { h = java.util.Arrays.copyOf(h, 4); e.setValue(h); }
                if (h[3] != c) { h[3] = c; ch = true; }
            }
            if (!ch) return;
            saveProps();
        }
        mark("发色 " + c + "（" + from + "）"); pushStore();
    }
    static long reqHairColor(byte[] body) {
        try {
            PB.R r = new PB.R(body);
            while (r.next()) if ((r.field == 1 || r.field == 6) && r.wire == 2) {
                PB.R p = r.sub();
                while (p.next()) if (p.field == 13 && p.wire == 2) { PB.R a = p.sub(); while (a.next()) if ((a.field == 5 || a.field == 6) && a.wire == 0) return a.val; }
            }
        } catch (Throwable ignored) {}
        return -1;
    }
    static void followHairColor(final long role) {
        new Thread(() -> {
            for (int t = 0; t < 8; t++) {
                try { Thread.sleep(250); } catch (Throwable ignored) {}
                try {
                    java.io.File f = Con.findUeLog(Con.act); if (f == null) continue;
                    String txt = AvatarLog.readText(f, 128 * 1024);
                    int k = txt.lastIndexOf("ReqStoreEquipProp   roleId = " + role);
                    if (k < 0) continue;
                    int nl = txt.indexOf('\n', k); String l = txt.substring(k, nl < 0 ? txt.length() : nl);
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("hairColor = (\\d+)").matcher(l);
                    if (m.find()) { setHairColor(role, Long.parseLong(m.group(1)), "日志"); return; }
                } catch (Throwable ex) { return; }
            }
        }, "zf-hair").start();
    }
    static void followPreview(final String req, final long id) {
        new Thread(() -> {
            String set = null;
            for (int t = 0; t < 12 && set == null; t++) {
                try { Thread.sleep(250); } catch (Throwable ignored) {}
                try {
                    java.io.File f = Con.findUeLog(Con.act); if (f == null) continue;
                    String txt = AvatarLog.readText(f, 256 * 1024);
                    int k = txt.lastIndexOf(req + "   propID = " + id);
                    if (k < 0) continue;
                    int e = txt.lastIndexOf("EquipItemsForHallRole  ", k);
                    if (e < 0) continue;
                    int nl = txt.indexOf('\n', e);
                    set = txt.substring(e + "EquipItemsForHallRole  ".length(), nl < 0 ? txt.length() : nl);
                } catch (Throwable ex) { mark("读试穿行出错 " + ex); return; }
            }
            if (set == null) { mark(req + " " + id + "：日志里没找到试穿行，只按类别处理"); pushStore(); return; }
            applyPreview(set);
        }, "zf-preview").start();
    }
    static void applyPreview(String set) {
        synchronized (Server.class) {
            java.util.HashSet<Long> want = new java.util.HashSet<>();
            for (String x : set.split(",")) { x = x.trim(); if (x.matches("\\d{11}")) want.add(Long.parseLong(x)); }
            int on = 0;
            java.util.HashSet<Long> done = new java.util.HashSet<>();
            for (long[] p : props.values()) {
                if (p[2] != 0) continue;
                boolean w = want.contains(p[0]) && done.add(p[0]);
                p[1] = w ? slotOf(p[0]) : 0;
                if (w) on++;
            }
            mark("按试穿行设穿着 " + on + " 件：" + set);
        }
        saveProps();
        pushStore();
    }
    static void pushStore() {
        push(13002, 0, false, depositProps(true), "仓库（穿脱后）");
        syncWear();
    }
    /** 存档里在用的角色：1 男，2 女，0 没有存档 */
    static int savedSex() {
        try {
            java.io.File f = propsFile(); if (f == null || !f.exists()) return 0;
            for (String l : new String(java.nio.file.Files.readAllBytes(f.toPath()), "UTF-8").split("\n")) {
                String[] x = l.trim().split(" "); if (x.length < 4) continue;
                if (!"0".equals(x[3]) && "1".equals(x[2])) return Integer.parseInt(x[3]);
            }
        } catch (Throwable ignored) {}
        return 0;
    }
    /** v1.75：游戏的穿戴请求不带位置，按道具类别定位置（例 30318 → 318，发型 302 13 → 213），同类互相替换 */
    static int slotOf(long id) { int c = (int) (id / 1000000 % 1000); return c == 0 ? 1 : c; }
    static boolean loadProps() {
        try {
            java.io.File f = propsFile(); if (f == null || !f.exists()) return false;
            for (String l : new String(java.nio.file.Files.readAllBytes(f.toPath()), "UTF-8").split("\n")) {
                String[] x = l.trim().split(" "); if (x.length < 4) continue;
                long id = Long.parseLong(x[1]), pos = Long.parseLong(x[2]), sex = Long.parseLong(x[3]);
                if (sex == 0 && pos != 0) pos = slotOf(id);   // v1.75：旧存档的位置换成按类别
                props.put(Long.parseLong(x[0]), x.length > 4 ? new long[] { id, pos, sex, Long.parseLong(x[4]) } : new long[] { id, pos, sex });
            }
            if (!props.containsKey(ROLE_F) || !props.containsKey(ROLE_M)) { props.clear(); return false; }
            return true;
        } catch (Throwable e) { props.clear(); return false; }
    }
    static synchronized void saveProps() {
        try {
            java.io.File f = propsFile(); if (f == null || props == null) return;
            StringBuilder b = new StringBuilder();
            for (java.util.Map.Entry<Long, long[]> e : props.entrySet()) { long[] v = e.getValue(); b.append(e.getKey()).append(' ').append(v[0]).append(' ').append(v[1]).append(' ').append(v[2]); if (v.length > 3) b.append(' ').append(v[3]); b.append('\n'); }
            java.io.File t = new java.io.File(f.getPath() + ".tmp");
            java.nio.file.Files.write(t.toPath(), b.toString().getBytes("UTF-8"));
            t.renameTo(f);
        } catch (Throwable e) { }
    }
    /** Z 菜单「清空仓库」：删存档，回到初始 */
    public static synchronized String resetProps() {
        java.io.File f = propsFile(); if (f != null) f.delete();
        props = null; mails.clear();
        return "仓库存档已清空，重启游戏后回到初始";
    }

    static long role() { for (long[] p : props.values()) if (p[2] != 0 && p[1] == 1) return p[0]; return ROLE_F; }

    static PB prop(long gid, long[] p) {
        // v1.74：游戏按 owner_id 判断「穿在身上」。之前所有衣服 owner 都是角色 → 全部算穿着（背包去不掉、衣服重叠）。
        // 现在：衣服穿着才 owner=角色；角色本身在用的 owner=1（和启动时假仓库一致），其余 0
        long owner = p[2] != 0 ? (p[1] != 0 ? 1 : 0) : (p[1] != 0 ? role() : 0);
        PB x = new PB().u(1, p[0]).u(2, gid).u(3, owner).u(4, 1).u(10, p[1]);
        if (p[2] != 0) { PB at = new PB().u(1, p[2]); if (p.length > 3 && p[3] > 0) at.u(5, p[3]); x.m(13, at); }   // v3.48 发色 hair_color(5)
        else if (p.length > 3 && p[3] > 0) x.m(13, new PB().u(6, p[3]));   // v3.49 发型道具自己的颜色：游戏 13003 里是 hair_prop(6).role_attr(13).color(6)
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
    static long reqId(byte[] body) {
        PB.R r = new PB.R(body); long id = 0;
        while (r.next()) if (r.field == 1 && r.wire == 2) { PB.R p = r.sub(); while (p.next()) if (p.field == 1) id = p.val; }
        return id;
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
        if (gid >= 0 && props.get(gid)[2] != 0) {   // v1.72：仓库里点角色 = 换男/女，用游戏自己的切换
            long hc = reqHairColor(body);
            if (hc >= 0 && hc <= 64) { long[] rv = props.get(gid); if (rv.length < 4) { rv = java.util.Arrays.copyOf(rv, 4); props.put(gid, rv); } rv[3] = hc; }
            for (java.util.Map.Entry<Long, long[]> e : props.entrySet())
                if (e.getValue()[2] != 0) { e.getValue()[1] = e.getKey() == gid ? 1 : 0; res.m(2, prop(e.getKey(), e.getValue())); }
            final boolean m = props.get(gid)[2] == 1;
            // v1.74：换角色时脱掉另一性别专属的衣服/发型（301 男款，302 女款），再按请求里的 hair_prop(6) 穿发型
            long other = m ? 302 : 301;
            for (java.util.Map.Entry<Long, long[]> e : props.entrySet()) {
                long[] v = e.getValue();
                if (v[2] == 0 && v[1] != 0 && v[0] / 100000000L == other) { v[1] = 0; res.m(2, prop(e.getKey(), v)); }
            }
            long hg = propGid(body, 6);
            if (hg >= 0 && props.get(hg)[2] == 0) {
                for (java.util.Map.Entry<Long, long[]> e : props.entrySet())
                    if (e.getValue()[2] == 0 && e.getValue()[1] == slotOf(props.get(hg)[0]) && e.getKey() != hg) { e.getValue()[1] = 0; res.m(2, prop(e.getKey(), e.getValue())); }
                props.get(hg)[1] = slotOf(props.get(hg)[0]);
                // v3.49：00:49 日志 13003 = 0a0c..(角色) 320d 08<30213000019> 10<gid> 6a02 3004 → 发色在 hair_prop 的 role_attr.color(6)=4。记在发型道具上，回包原样带回
                if (hc >= 0 && hc <= 64) { long[] hv = props.get(hg); if (hv.length < 4) { hv = java.util.Arrays.copyOf(hv, 4); props.put(hg, hv); } hv[3] = hc; }
                res.m(2, prop(hg, props.get(hg)));
            }
            // v1.77：脸上涂装 facial_tattoos_prop(7)：有就穿上（同类替换），没有就脱掉所有涂装
            long tg = propGid(body, 7);
            for (java.util.Map.Entry<Long, long[]> e : props.entrySet()) {
                long[] v = e.getValue();
                if (v[2] == 0 && v[1] != 0 && v[0] / 1000000 % 1000 == 328 && e.getKey() != tg) { v[1] = 0; res.m(2, prop(e.getKey(), v)); }
            }
            if (tg >= 0 && props.get(tg)[2] == 0) { props.get(tg)[1] = slotOf(props.get(tg)[0]); res.m(2, prop(tg, props.get(tg))); }
            if (m != Mods.male) new Thread(() -> mark("换角色 " + (m ? "男" : "女") + " → " + Mods.setMale(m))).start();
        } else if (gid >= 0) {
            pos = slotOf(props.get(gid)[0]);   // v1.75：之前请求里没位置 → pos=0 → 穿上的衣服被记成没穿，回大厅就没了
            for (java.util.Map.Entry<Long, long[]> e : props.entrySet())
                if (e.getValue()[2] == 0 && e.getValue()[1] == pos && e.getKey() != gid) { e.getValue()[1] = 0; res.m(2, prop(e.getKey(), e.getValue())); }
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
        { 30213000000L }, { 30213000003L }, { 30213000004L }, { 30213000005L },   // v1.71：30213000001/02 游戏显示「未知道具」，去掉
        { 30301000000L }, { 30301000071L }, { 30303000005L }, { 30307000007L }, { 30232000006L },
        { 30315000050L }, { 30315000061L }, { 30323000002L },
        { 30318000000L },
    };
    static final ArrayList<long[]> mails = new ArrayList<>();   // {index, 已读}
    static int mailSeq;

    static PB mail(long[] m) {
        PB att = new PB();
        for (long[] g : gifts(m)) {
            PB p = new PB().u(1, g[0]).u(4, 1);
            if (g.length > 1) p.m(13, new PB().u(1, g[1]).u(2, g[2]));
            att.m(1, p);
        }
        boolean all = m.length > 3 && m[3] == 1;
        return new PB().u(1, m[2]).s(3, all ? "全部皮肤" : "补给").s(4, all ? "游戏里能找到的服装都在这里，点领取放进仓库。" : "服务端已开启，点领取把角色、发型和衣服放进仓库。")
            .m(6, att).u(7, m[1]).u(8, m[0]).u(10, 0);
    }
    static long[][] skinGift = new long[0][];
    static long[][] gifts(long[] m) { return m.length > 3 && m[3] == 1 ? skinGift : GIFT; }
    static PB mailListOf(java.util.List<long[]> ms) {
        PB l = new PB().u(1, MAIL_SYS);
        for (long[] m : ms) l.m(2, mail(m));
        return l;
    }
    static synchronized byte[] mailList() { return new PB().u(1, 0).m(2, mailListOf(mails)).bytes(); }

    /** 新建一封邮件并推给游戏（25007） */
    static synchronized void newMail() { newMail(0); }
    static synchronized void newMail(int kind) {
        long[] m = { ++mailSeq, 0, System.currentTimeMillis() / 1000, kind };
        mails.add(m);
        push(25007, 0, false, new PB().m(1, mailListOf(java.util.Collections.singletonList(m))).bytes());
    }
    /** v1.90：补给直接放进仓库（不发邮件），返回新加几件 */
    static synchronized int giveGift() {
        initProps(); int n = 0;
        for (long[] g : GIFT) if (addProp(g) != 0) n++;
        if (n > 0) saveProps();
        return n;
    }
    static synchronized boolean missingGift() {
        initProps();
        for (long[] g : GIFT) { boolean have = false; for (long[] p : props.values()) if (p[0] == g[0]) { have = true; break; } if (!have) return true; }
        return false;
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
            else if (r.field == 2 && r.wire == 2) for (long v : packed(r.raw())) idx.add((int) v);   // v1.72：packed 是连续 varint，不是子消息（v1.71 以前解析错，领取认不出邮件）
            else if (r.field == 3) del = r.val != 0;
        }
        PB change = new PB(); PB res = new PB().u(1, 0);
        for (long[] m : new ArrayList<>(mails)) {
            if (!idx.contains((int) m[0])) continue;
            if (m[1] == 0) {
                m[1] = 1;
                for (long[] g : gifts(m)) {
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
    // ---------------- v1.73 全部皮肤入库 ----------------
    // 游戏在大厅已把物品表读进内存。扫可写内存里 8 字节对齐的 64 位数，取 301xx/302xx/303xx 开头的服装编号（301 男款、302 女款、303 通用），
    // 去掉头盔 30304、护甲 30305（对局装备）和已知游戏不认的编号。物品表里一个编号通常出现多次，要求至少出现 2 次，减少误报。
    static final long[] BAD = { 30213000001L, 30213000002L, 30325000000L, 30325000001L, 30325000002L, 30325000003L, 30325000004L, 30325000005L };
    static java.util.TreeMap<Long, Integer> scanSkins() throws java.io.IOException {
        java.util.TreeMap<Long, Integer> hit = new java.util.TreeMap<>();
        other.clear(); nameHits.clear(); maskCand.clear();
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
        byte[] buf = new byte[1 << 20]; long[] w = new long[buf.length / 8];
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
            for (long[] g : rs) for (long a = g[0]; a < g[1]; a += buf.length) {
                int n = (int) Math.min(buf.length, g[1] - a);
                try { m.seek(a); m.readFully(buf, 0, n); } catch (Throwable e) { break; }
                // v1.82：不再按名字找口罩（太慢，扫一遍要几分钟）
                int k = n / 8;
                ByteBuffer.wrap(buf, 0, n).order(ByteOrder.LITTLE_ENDIAN).asLongBuffer().get(w, 0, k);
                for (int i = 0; i < k; i++) {
                    long v = w[i];
                    if (v < 10000000000L || v >= 100000000000L) continue;
                    long cat = v / 1000000, sub = v % 1000000;
                    if (sub >= 1000 || sub == 0 && cat % 100 == 0) continue;
                    other.merge(cat, 1, Integer::sum);
                    // v1.77：衣服扩到 301xx~309xx（30304 头盔 / 30305 防弹衣是对局装备，不发）
                    int c3 = (int) (cat / 100);
                    if (c3 < 301 || c3 > 309 || cat == 30304 || cat == 30305) continue;
                    hit.merge(v, 1, Integer::sum);
                }
            }
        }
        for (long b : BAD) hit.remove(b);
        return hit;
    }
    /** v2.49：游戏内存里所有不是服装的 11 位物品编号（枪、配件、投掷、子弹、药、头甲包、娱乐道具等），只读，用于「全部道具」页 */
    static java.util.TreeSet<Long> scanItems() throws java.io.IOException {
        java.util.TreeSet<Long> hit = new java.util.TreeSet<>();
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
        byte[] buf = new byte[1 << 20]; long[] w = new long[buf.length / 8];
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
            for (long[] g : rs) for (long a = g[0]; a < g[1]; a += buf.length) {
                int n = (int) Math.min(buf.length, g[1] - a);
                try { m.seek(a); m.readFully(buf, 0, n); } catch (Throwable e) { break; }
                int k = n / 8;
                ByteBuffer.wrap(buf, 0, n).order(ByteOrder.LITTLE_ENDIAN).asLongBuffer().get(w, 0, k);
                for (int i = 0; i < k; i++) {
                    long v = w[i];
                    if (v < 20000000000L || v >= 100000000000L) continue;
                    long cat = v / 1000000, sub = v % 1000000;
                    if (sub == 0 || sub >= 1000 || cat % 100 > 40) continue;
                    int c3 = (int) (cat / 100);
                    if (c3 % 100 == 0 || c3 % 100 > 40) continue;
                    if (c3 >= 301 && c3 <= 309 && !(cat == 30304 || cat == 30305 || cat == 30307)) continue;   // 服装另有页
                    if (c3 >= 310 && c3 < 400) continue;
                    hit.add(v);
                    if (hit.size() > 3000) return hit;
                }
            }
        }
        for (long b : BAD) hit.remove(b);
        return hit;
    }
    // ---------------- v1.79 按名字找口罩 ----------------
    // 物品名字在内存里（UTF-16 或 UTF-8）。找「口罩」「面罩」，看名字前后 512 字节里的服装编号（8 字节整数或十进制文字），
    // 离名字最近的当作这个物品的编号。诊断里列出每处名字和附近编号，找到的编号存 filesDir/zf_mask.txt，以后每次开服务端直接放进仓库。
    static final byte[][] NAMES = {
        { (byte) 0xe3, 0x53, 0x69, 0x7f },                                   // 口罩 UTF-16LE
        { 0x62, (byte) 0x97, 0x69, 0x7f },                                   // 面罩 UTF-16LE
        { (byte) 0xe5, (byte) 0x8f, (byte) 0xa3, (byte) 0xe7, (byte) 0xbd, (byte) 0xa9 },   // 口罩 UTF-8
        { (byte) 0xe9, (byte) 0x9d, (byte) 0xa2, (byte) 0xe7, (byte) 0xbd, (byte) 0xa9 },   // 面罩 UTF-8
    };
    static final ArrayList<String> nameHits = new ArrayList<>();
    static final java.util.LinkedHashMap<Long, Integer> maskCand = new java.util.LinkedHashMap<>();
    static void findNames(byte[] b, int n, long base) {
        for (int k = 0; k < NAMES.length; k++) {
            byte[] p = NAMES[k];
            outer:
            for (int i = 0; i + p.length <= n; i++) {
                if (b[i] != p[0]) continue;
                for (int j = 1; j < p.length; j++) if (b[i + j] != p[j]) continue outer;
                boolean u16 = k < 2;
                if (u16 && (i & 1) != 0) continue;
                String text = around(b, n, i, u16);
                long best = 0; int bestD = 1 << 30;
                int lo = Math.max(0, i - 512) & ~7, hi = Math.min(n - 8, i + 512);
                for (int q = lo; q <= hi; q += 8) {   // 8 字节整数
                    long v = ByteBuffer.wrap(b, q, 8).order(ByteOrder.LITTLE_ENDIAN).getLong();
                    if (isSkin(v) && Math.abs(q - i) < bestD) { bestD = Math.abs(q - i); best = v; }
                }
                java.util.regex.Matcher mt = java.util.regex.Pattern.compile("30[1-9]\\d{8}").matcher(text);   // 十进制文字
                while (mt.find()) { long v = Long.parseLong(mt.group()); if (isSkin(v) && best == 0) best = v; }
                synchronized (nameHits) {
                    if (best != 0) maskCand.merge(best, 1, Integer::sum);
                    if (nameHits.size() < 40) nameHits.add(Long.toHexString(base + i) + (u16 ? " U16 " : " U8 ") + "「" + text + "」 → " + (best == 0 ? "附近没编号" : best + "（距 " + bestD + "）"));
                }
                i += p.length - 1;
            }
        }
    }
    static boolean isSkin(long v) {
        if (v < 30100000000L || v >= 31000000000L) return false;
        long cat = v / 1000000, sub = v % 1000000;
        return sub < 1000 && cat != 30304 && cat != 30305;
    }
    static String around(byte[] b, int n, int i, boolean u16) {
        int s = Math.max(0, i - (u16 ? 40 : 60)), e = Math.min(n, i + (u16 ? 60 : 90));
        if (u16) { s &= ~1; e &= ~1; }
        String t = new String(b, s, e - s, u16 ? java.nio.charset.StandardCharsets.UTF_16LE : java.nio.charset.StandardCharsets.UTF_8);
        StringBuilder o = new StringBuilder();
        for (char c : t.toCharArray()) o.append(c < 0x20 || c == 0xfffd || Character.isSurrogate(c) ? '·' : c);
        return o.toString().replaceAll("·{3,}", "··");
    }
    static java.io.File maskFile() { android.app.Activity a = Con.act; return a == null ? null : new java.io.File(a.getFilesDir(), "zf_mask.txt"); }
    static ArrayList<Long> savedMasks() {
        ArrayList<Long> l = new ArrayList<>(); java.io.File f = maskFile();
        if (f == null || !f.exists()) return l;
        try { for (String x : new String(java.nio.file.Files.readAllBytes(f.toPath())).trim().split("[,\\s]+")) if (!x.isEmpty()) l.add(Long.parseLong(x)); } catch (Throwable ignored) {}
        return l;
    }
    static void saveMasks(java.util.Collection<Long> ids) {
        java.io.File f = maskFile(); if (f == null) return;
        java.util.LinkedHashSet<Long> all = new java.util.LinkedHashSet<>(savedMasks()); all.addAll(ids);
        StringBuilder b = new StringBuilder(); for (long v : all) b.append(v).append(' ');
        try { java.nio.file.Files.write(f.toPath(), b.toString().trim().getBytes()); } catch (Throwable ignored) {}
    }
    /** 口罩：存过的 + 这次按名字找到的，仓库没有就直接放进去（不用领邮件），穿不穿你在仓库里选 */
    static synchronized String giveMasks() {
        java.util.LinkedHashSet<Long> ids = new java.util.LinkedHashSet<>(savedMasks());
        synchronized (nameHits) { ids.addAll(maskCand.keySet()); }
        if (!maskCand.isEmpty()) saveMasks(maskCand.keySet());
        if (ids.isEmpty()) return "口罩：内存里没找到名字（" + nameHits.size() + " 处）";
        initProps(); int added = 0;
        for (long v : ids) if (addProp(new long[] { v }) != 0) added++;
        if (added > 0) { push(13002, 0, false, depositProps(), "口罩入库"); saveProps(); }
        return "口罩：编号 " + ids + "，新放进仓库 " + added + " 件";
    }
    static volatile String maskState = "";
    /** v1.74 扫出全部服装；v1.90 改成直接放进仓库再推 13002（不发邮件）。连接器找到后自动跑一次 */
    public static String allSkins() {
        if (!on) return state = "自建服务端：先按「开启服务端」";
        try {
            mark("开始扫全部皮肤");
            long t0 = System.currentTimeMillis();
            java.util.TreeMap<Long, Integer> hit = scanSkins();
            // v1.80：按名字找口罩不准（找到的是分类标签「口罩/面罩」），只留诊断，不再自动入库
            int two = 0;
            for (int c : hit.values()) if (c >= 2) two++;
            final int min = 1;   // v1.76：只出现 1 次的也算（之前门槛 2 漏了口罩等）
            java.util.TreeMap<Long, Integer> cats = new java.util.TreeMap<>();
            ArrayList<long[]> l = new ArrayList<>();
            // v3.59 用户：大厅降落伞对照 pak 改 → 物品表里有、pak 里有资源文件夹的 56 把伞直接入库（没资源的不放）
            for (long pid : PARA) hit.merge(pid, 1, Integer::sum);
            synchronized (Server.class) {
                initProps();
                for (java.util.Map.Entry<Long, Integer> e : hit.entrySet()) {
                    if (e.getValue() < min) continue;
                    cats.merge(e.getKey() / 1000000, 1, Integer::sum);
                    boolean have = false;
                    for (long[] p : props.values()) if (p[0] == e.getKey()) { have = true; break; }
                    if (!have && l.size() < 800) l.add(new long[] { e.getKey() });
                }
                skinCats = "门槛 " + min + " 次，分类 " + cats;
                if (l.isEmpty()) return skinState = "全部皮肤：仓库里都有了（找到 " + cats.values().stream().mapToInt(Integer::intValue).sum() + " 件）";
                for (long[] g : l) addProp(g);
                saveProps();
                push(13002, 0, false, depositProps(), "全部皮肤入库");
            }
            String r = "全部皮肤：直接放进仓库 " + l.size() + " 件（" + (System.currentTimeMillis() - t0) + " ms）";
            mark(r);
            return skinState = r;
        } catch (Throwable e) { return skinState = "全部皮肤：出错 " + e; }
    }
    static volatile String skinState = "", skinCats = "";
    /** v3.59：pak 里有资源（Items/Parachute/<编号>/）的降落伞编号，名字由游戏物品表 CommonItemDataTable 自己显示 */
    static final long[] PARA = { 40500000001L, 40500000002L, 40500000003L, 40500000004L, 40500000005L, 40500000006L, 40500000010L, 40500000011L, 40500000012L, 40500000013L, 40500000014L, 40500000015L, 40500000016L, 40500000017L, 40500000018L, 40500000019L, 40500000020L, 40500000021L, 40500000029L, 40500000030L, 40500000031L, 40500000032L, 40500000033L, 40500000034L, 40500000035L, 40500000036L, 40500000037L, 40500000038L, 40500000039L, 40500000040L, 40500000041L, 40500000042L, 40500000043L, 40500000044L, 40500000045L, 40500000046L, 40500000047L, 40500000049L, 40500000050L, 40500000051L, 40500000052L, 40500000053L, 40500000054L, 40500000055L, 40500000056L, 40500000057L, 40500000058L, 40500000059L, 40500000060L, 40500000061L, 40500000062L, 40500000063L, 40500000064L, 40500000065L, 40500000066L, 40500000068L };
    /** v1.77：内存里所有 11 位编号按分类计数（找降落伞/口罩等在哪个分类，诊断用） */
    static final java.util.TreeMap<Long, Integer> other = new java.util.TreeMap<>();
    static String otherCats() {
        StringBuilder b = new StringBuilder("全部编号分类（≥3 次）：");
        synchronized (other) { for (java.util.Map.Entry<Long, Integer> e : other.entrySet()) if (e.getValue() >= 3) b.append(e.getKey()).append('=').append(e.getValue()).append(' '); }
        return b.toString();
    }

    static ArrayList<Long> packed(byte[] b) {
        ArrayList<Long> out = new ArrayList<>(); int i = 0;
        while (i < b.length) { long v = 0; int sh = 0; while (i < b.length) { int x = b[i++] & 0xff; v |= (long) (x & 0x7f) << sh; sh += 7; if (x < 0x80) break; } out.add(v); }
        return out;
    }
    // ---------------- v2.48 军需抽奖（离线自建，奖品 = 游戏认得的服装）----------------
    static long[] lotIds; static boolean[] lotGot; static int lotRound, lotState;   // lotState 0 选奖 1 抽奖
    static String lotLast = ""; static boolean lotLastNew;
    static void lotFill() {
        java.util.ArrayList<Long> pool = new java.util.ArrayList<>();
        for (long[] g : skinGift.length > 0 ? skinGift : GIFT) if (g.length == 1 || g[1] == 0) { boolean bad = false; for (long b : BAD) if (b == g[0]) bad = true; if (!bad && !pool.contains(g[0])) pool.add(g[0]); }
        java.util.Collections.shuffle(pool);
        int n = Math.min(8, pool.size());
        lotIds = new long[n]; lotGot = new boolean[n];
        for (int i = 0; i < n; i++) lotIds[i] = pool.get(i);
    }
    static PB lotProp(int i) {
        int pool = i == 0 ? 1 : i < 3 ? 2 : i < 5 ? 3 : 4;   // 必中/高/中/低
        return new PB().u(1, i).u(2, pool).m(3, new PB().u(1, lotIds[i]).u(4, 1)).u(4, lotGot[i] ? 1 : 0);
    }
    static synchronized byte[] lotList() {
        if (lotIds == null) lotFill();
        PB r = new PB().u(1, 0).u(2, lotState);
        for (int i = 0; i < lotIds.length; i++) r.m(3, lotProp(i));
        return r.u(4, lotRound).bytes();
    }
    static synchronized byte[] lotCommit(byte[] body) {
        if (lotIds == null) lotFill();
        lotState = 1;
        PB r = new PB().u(1, 0);
        for (int i = 0; i < lotIds.length; i++) r.m(2, lotProp(i));
        return r.bytes();
    }
    static synchronized byte[] lotRand() {
        if (lotIds == null) lotFill();
        initProps(); lotLastNew = false;
        java.util.ArrayList<Integer> left = new java.util.ArrayList<>();
        for (int i = 0; i < lotIds.length; i++) if (!lotGot[i]) left.add(i);
        if (left.isEmpty()) { lotLast = "全抽完"; return new PB().u(1, 51009).bytes(); }
        int i = left.get(new java.util.Random().nextInt(left.size()));
        lotGot[i] = true; lotRound++;
        lotLastNew = addProp(new long[] { lotIds[i] }) != 0;
        lotLast = lotIds[i] + (lotLastNew ? " 入库" : " 已有");
        return new PB().u(1, 0).m(2, lotProp(i)).u(3, lotRound).bytes();
    }
    static synchronized byte[] lotReset() {
        lotFill(); lotRound = 0; lotState = 0;
        PB r = new PB().u(1, 0);
        for (int i = 0; i < lotIds.length; i++) r.m(3, lotProp(i));
        return r.bytes();
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
        sb.append("请求 ").append(req.getInt(4)).append(" 个（丢 ").append(req.getInt(12)).append("），回包 ").append(replied).append("，催发包 ").append(kicks).append("，回包区用了 ").append(rplWp / 1024).append(" KB")
          .append(" 个，游戏已收 ").append(rpl.getInt(8)).append(" 个\n")
          .append(connState).append("，帧 ").append(rpl.getInt(20)).append("（开后 1 秒多跑 ").append(frames1s).append("）\n");
        sb.append(connInfo).append("\n已投 ").append(rpl.getInt(28)).append(" 条，排队 ").append(cmds).append('\n');
        sb.append(selfCheck()).append('\n');
        if (!nameHits.isEmpty()) { sb.append("名字命中 ").append(nameHits.size()).append('\n'); synchronized (nameHits) { for (String h : nameHits) sb.append("  名字 ").append(h).append('\n'); } }
        if (!skinState.isEmpty()) sb.append(skinState).append("\n  ").append(skinCats).append("\n  ").append(otherCats()).append("\n");
        sb.append("仓库 ").append(props == null ? 0 : props.size()).append(" 件\n");
        sb.append("邮件 ").append(mails.size()).append(" 封\n");
        for (String l : log) sb.append(l).append('\n');
        return sb.toString();
    }
}
