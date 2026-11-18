package com.zframe.inject;

import java.io.RandomAccessFile;
import java.util.HashMap;

/** v2.69 探测：用户想用游戏自己的更新页显示我们的更新。启动加载页起每 2 秒扫一遍 GUObjectArray，
 *  记下类名带 Update / Logo 的对象（更新页控件 NZUpdateUIView 进度条在 +0x208、文字 +0x20c；
 *  持有者 +0x34 UpdateUI、+0x38 UpdateUIClass、+0x3c OnlineUpdate），以及启动阶段 ucall 能不能用。
 *  90 秒或进大厅后把结果自动传日志。只读内存，ucall 只调一条 bx lr。 */
final class UpdProbe {
    static volatile boolean started;
    static final StringBuilder out = new StringBuilder();
    static void say(String s) { synchronized (out) { out.append(s).append('\n'); } LogCap.note("更新页探测：" + s); }

    static void start() {
        if (started) return;
        started = true;
        new Thread(UpdProbe::run, "zf-updprobe").start();
    }
    static void run() {
        long t0 = System.currentTimeMillis();
        try {
            long b = 0;
            for (int i = 0; i < 40 && (b = Mods.base()) == 0; i++) Thread.sleep(250);
            if (b == 0) { say("找不到游戏库"); return; }
            Veh.Names nm = Veh.Names.get();
            say("名字表 " + (nm == null ? "没找到" : "ok") + " " + (System.currentTimeMillis() - t0) + "ms");
            try {
                long[] r = Veh.ucall(b, 0, 0, 0, null, b + 0x9ab224L);
                say("启动阶段 ucall(bx lr)：" + Long.toHexString(r[0]) + " installed=" + Veh.installed + " uOn=" + Veh.uOn);
            } catch (Throwable e) { say("ucall 出错 " + e); }
            HashMap<Long, String> cn = new HashMap<>();
            String last = "";
            for (int round = 0; round < 45; round++) {
                StringBuilder sb = new StringBuilder();
                try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
                    long g = Server.rd(m, b + 0x51250acL);   // 同 Prop.alive
                    long objs = Server.rd(m, g + 0x10), num = Server.rd(m, g + 0x18);
                    if (objs < 0x10000 || num <= 0 || num > 0x200000) { say("对象表不对 " + Long.toHexString(objs) + " " + num); Thread.sleep(2000); continue; }
                    byte[] raw = new byte[(int) num * 16];
                    m.seek(objs); m.readFully(raw);
                    java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(raw).order(java.nio.ByteOrder.LITTLE_ENDIAN);
                    java.util.ArrayList<Long> hits = new java.util.ArrayList<>();
                    for (int i = 0; i < num; i++) {
                        long o = bb.getInt(i * 16) & 0xffffffffL;
                        if (o < 0x10000) continue;
                        long c = Server.rd(m, o + 0xc);
                        if (c < 0x10000) continue;
                        String n = cn.get(c);
                        if (n == null) { n = Alive.objName(m, nm, c); cn.put(c, n); }
                        if (n.startsWith("Default__")) continue;
                        if ((n.contains("Update") || n.contains("Logo")) && !n.contains("Property") && !n.equals("Function") && !n.contains("Class")) hits.add(o);
                    }
                    sb.append("对象 ").append(num).append(" 个，命中 ").append(hits.size());
                    for (long o : hits) {
                        String c = cn.get(Server.rd(m, o + 0xc));
                        sb.append("\n  ").append(c).append(' ').append(Alive.objName(m, nm, o)).append(" @").append(Long.toHexString(o))
                          .append(" vt+").append(Long.toHexString(Server.rd(m, o) - b));
                        if (c.contains("UpdateUI") || c.contains("Update_UI") || c.contains("UpdateView")) {
                            for (int off = 0x208; off <= 0x214; off += 4) {
                                long q = Server.rd(m, o + off);
                                sb.append(" [").append(Integer.toHexString(off)).append("]=").append(q < 0x10000 ? "0" : Alive.clsName(m, nm, q));
                            }
                        }
                        for (int off = 0x24; off <= 0x3c; off += 4) {   // 持有者？
                            long q = Server.rd(m, o + off);
                            if (q < 0x10000 || q >= 0xffff0000L) continue;
                            String qc = Alive.clsName(m, nm, q);
                            if (qc != null && (qc.contains("Update") || qc.contains("Logo"))) sb.append(" +").append(Integer.toHexString(off)).append("→").append(qc);
                        }
                    }
                }
                String s = sb.toString();
                String key = s.replaceAll("对象 \\d+ 个", "");
                if (!key.equals(last)) { say("t=" + (System.currentTimeMillis() - t0) / 1000 + "s " + Ed.name() + " 选择框" + (GameOverlay.chooser != null ? "在" : "关") + " 更新=" + NetCore.status + "\n" + s); last = key; }
                if (NetCore.started && round > 10 && GameOverlay.chooser == null) break;
                Thread.sleep(2000);
            }
        } catch (Throwable e) { say("出错 " + e); }
        finally {
            String s; synchronized (out) { s = out.toString(); }
            LogUp.send("updprobe-" + System.currentTimeMillis(), "更新页探测 " + GameOverlay.VER + "\n" + s);
        }
    }
}
