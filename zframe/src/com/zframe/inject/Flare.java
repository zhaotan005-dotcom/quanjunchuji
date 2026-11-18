package com.zframe.inject;

import java.io.RandomAccessFile;

/** v3.00 信号枪叫飞机诊断（只记录，不改任何东西）。
 *  静态 RE（v2.99）：ANZProjectile_FlareBullet 虚表 0x4c50f5c。Init 0xee6b9c：+0x331 = 发射仰角 > 枪+0x1b64（默认 35°），+0x334 = 枪+0x1b60（默认 10000）。
 *  Tick 0xee6dfc：Role(+0xe8)≥3 且 +0x331 → 记最高 Z(+0x32c)；下落时最高 Z > +0x334 → +0x330=1 → 人物 vt+0x95c 0x10e8d20 → GameMode 事件管理广播事件 1。
 *  监听者 AAirSupplySystem 虚表 0x4caf198：BeginPlay(vt+0x178 0x1186134) 把 OnQuickStartSingleAirSupply 绑到事件管理 +0x23c；
 *  0x11867bc 事件类型==1 → 0x118a60c：GetSystem<AirSupplySystem>(GM+0x380) 非空、this+0x2bc(圈对象) 非空、
 *  CurFlareUsedNum(+0x354) < LimitsNumAdd(+0x340) × (圈序号(圈对象+0x2b8)+1) 才 +1 并叫飞机(0x118908c/0x1188140)。LimitsNumAdd==0 → 永远叫不来。
 *  游戏日志出现 FlareGun 开火行 → 25 秒内每 0.25 秒扫 GUObjectArray 找信号弹对象，记 Role/+0x331/+0x330/+0x32c/+0x334/位置，同时记空投系统 +0x354，
 *  结束后自动传 flare-*。 */
final class Flare {
    static final long VT_BULLET = 0x4c50f5cL, VT_AIRSYS = 0x4caf198L;
    static volatile String state = "信号弹诊断：还没开过信号枪";
    static volatile long until, lastStart;
    static final java.util.ArrayDeque<String> log = new java.util.ArrayDeque<>();
    static void d(String s) {
        String t = new java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US).format(new java.util.Date()) + " " + s;
        synchronized (log) { log.addLast(t); while (log.size() > 200) log.removeFirst(); }
    }
    static String text() {
        StringBuilder sb = new StringBuilder(state);
        synchronized (log) { for (String x : log) sb.append("\n    ").append(x); }
        return sb.toString();
    }
    static final String[] KEYS = { "Flare", "AirSupply", "AirBox", "AirDrop", "AirPlane", "Airplane", "SupplyBox", "AirTransporter" };
    /** Con.line 每行调 */
    static void line(String l) {
        boolean hit = false;
        for (String k : KEYS) if (l.contains(k)) { hit = true; break; }
        if (!hit) return;
        long now = System.currentTimeMillis();
        if (now < until) { int i = l.indexOf("]Log"); d("游戏: " + (i > 0 ? l.substring(i + 1) : l)); return; }
        if (l.contains("Flare") && now - lastStart > 30000) start(l);   // 服务端不一定有开火动画行，任何 Flare 行都开始记
    }
    static void start(String why) {
        lastStart = System.currentTimeMillis(); until = lastStart + 25000;
        synchronized (log) { log.clear(); }
        d("开火：" + why);
        state = "信号弹诊断：记录中";
        new Thread(Flare::watch, "zf-flare").start();
    }
    static float fl(RandomAccessFile m, long a) { return Float.intBitsToFloat((int) Server.rd(m, a)); }
    static int b8(RandomAccessFile m, long a) { try { byte[] x = new byte[1]; m.seek(a); m.readFully(x); return x[0] & 0xff; } catch (Throwable e) { return -1; } }
    /** GUObjectArray 里 虚表 == vt 的活对象 */
    static java.util.ArrayList<Long> byVt(RandomAccessFile m, long b, long vt) throws java.io.IOException {
        java.util.ArrayList<Long> out = new java.util.ArrayList<>();
        long g = Server.rd(m, b + 0x51250acL); if (g < 0x10000) return out;
        long objs = Server.rd(m, g + 0x10), n = Server.rd(m, g + 0x18);
        if (objs < 0x10000 || n <= 0 || n > 0x200000) return out;
        byte[] blk = new byte[16 * 4096];
        for (long i = 0; i < n; i += 4096) {
            int k = (int) Math.min(4096, n - i);
            m.seek(objs + i * 16); m.readFully(blk, 0, k * 16);
            java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(blk).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            for (int j = 0; j < k; j++) {
                long o = bb.getInt(j * 16) & 0xffffffffL; int f = bb.getInt(j * 16 + 4);
                if (o < 0x10000 || (f & 0x20000000) != 0) continue;
                if (Server.rd(m, o) == vt) out.add(o);
            }
        }
        return out;
    }
    static String loc(RandomAccessFile m, long actor) {
        long rc = Server.rd(m, actor + 0x128); if (rc < 0x10000) return "位置?";
        return Math.round(fl(m, rc + 0x140)) + "," + Math.round(fl(m, rc + 0x144)) + "," + Math.round(fl(m, rc + 0x148));
    }
    static void watch() {
        long b = Mods.base(); if (b == 0) { state = "信号弹诊断：找不到游戏库"; return; }
        java.util.HashMap<Long, String> last = new java.util.HashMap<>();
        java.util.HashSet<Long> seen = new java.util.HashSet<>();
        String airLast = "";
        int scans = 0; boolean fired = false;
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
            java.util.ArrayList<Long> air = byVt(m, b, b + VT_AIRSYS);
            d("空投系统对象 " + air.size() + " 个，NetMode " + Prop.netMode());
            java.util.ArrayList<Long> cur = new java.util.ArrayList<>();
            while (System.currentTimeMillis() < until) {
                if (scans++ % 2 == 0 || cur.isEmpty()) cur = byVt(m, b, b + VT_BULLET);
                for (long o : cur) {
                    if (seen.add(o)) d(String.format("信号弹 %08x 出现", o));
                    String s = "Role " + b8(m, o + 0xe8) + " 仰角够 " + b8(m, o + 0x331) + " 已触发 " + b8(m, o + 0x330)
                        + " 最高Z " + Math.round(fl(m, o + 0x32c)) + " 门槛 " + Math.round(fl(m, o + 0x334)) + " 位置 " + loc(m, o);
                    if (!s.equals(last.get(o))) { last.put(o, s); d(String.format("%08x ", o) + s); }
                    if (b8(m, o + 0x330) == 1) fired = true;
                }
                StringBuilder as = new StringBuilder();
                for (long a : air) {
                    long circ = Server.rd(m, a + 0x2bc);
                    as.append(String.format("%08x", a)).append(" Role ").append(b8(m, a + 0xe8)).append(" 已用信号枪 ").append(Server.rd(m, a + 0x354))
                      .append(" 每圈上限LimitsNumAdd ").append(Server.rd(m, a + 0x340)).append(" 圈对象+2bc ").append(String.format("%08x", circ))
                      .append(" 圈序号 ").append(circ < 0x10000 ? "?" : String.valueOf(Server.rd(m, circ + 0x2b8))).append(" 范围 ").append(Math.round(fl(m, a + 0x33c))).append("；");
                }
                if (!as.toString().equals(airLast)) { airLast = as.toString(); d("空投系统：" + airLast); }
                Thread.sleep(250);
            }
        } catch (Throwable e) { d("出错 " + e); }
        state = "信号弹诊断：信号弹 " + seen.size() + " 个，" + (fired ? "触发了叫飞机（+0x330=1）" : "没触发叫飞机") + "，记录 " + scans + " 次";
        d(state);
        android.app.Activity a = Con.act; if (a != null) AvatarLog.export(a, "flare-");
    }
}
