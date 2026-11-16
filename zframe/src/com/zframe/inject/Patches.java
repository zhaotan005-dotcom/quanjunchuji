package com.zframe.inject;

import android.content.Context;

import java.io.*;

/**
 * 游戏修复补丁：等游戏加载 libUE4.so 后、引擎初始化前，直接改内存（/proc/self/mem，纯 Java，不需要 root）。
 * 每个补丁先核对原始字节，对不上就跳过，不会乱写。
 */
public class Patches {
    static final String PUBGM = "com.tencent.tmgp.pubgm";

    static class P {
        final String name; final long off; final byte[] orig, now;
        P(String n, long o, String a, String b) { name = n; off = o; orig = hex(a); now = hex(b); }
    }

    // 全军出击单机版 2.0（libUE4 85133868 字节）
    static final P[] PUBGM_PATCHES = {
        // （v1.7 去掉了旧的“门”补丁：日志证明门不是被删，而是根本没加载）
    };

    static volatile long foundMs = -1;
    public static void start(final Context ctx) {
        if (!PUBGM.equals(ctx.getPackageName())) return;
        Thread t = new Thread(new Runnable() { public void run() {
            long base = 0, t0 = System.currentTimeMillis();
            while (base == 0 && System.currentTimeMillis() - t0 < 180000) {
                base = findBase("libUE4.so");
                if (base == 0) try { Thread.sleep(200); } catch (InterruptedException e) { return; }
            }
            if (base == 0) { LogCap.note("补丁: 3 分钟内没等到 libUE4.so"); return; }
            LogCap.note("补丁: libUE4 基址 0x" + Long.toHexString(base) + " 用时" + (System.currentTimeMillis() - t0) + "ms");
            ueBase = base; foundMs = System.currentTimeMillis() - t0;
            Mods.early = ctx.getSharedPreferences("zframe", 0).getBoolean("early_hooks", true);
            if (Mods.early) Mods.preinstall();               // v1.65：代码还没跑之前装钩子
            if (!MEM) {
                LogCap.note("安全模式: 不写游戏内存（v1.19 只读一次 Wwise 标志）");
                // v1.17 已证明闪退不是内存读写导致；这里只读 1 个字节，不写
                audioProbe(base);
                return;
            }
            audioProbe(base);
            for (int round = 0; round < 5 && PUBGM_PATCHES.length > 0; round++) {
                for (P p : PUBGM_PATCHES) apply(base, p, round == 0);
                try { Thread.sleep(400); } catch (InterruptedException e) { return; }
            }
            layerFix(base);
        }}, "zframe-patch");
        t.setDaemon(true);
        t.start();
    }

    static void apply(long base, P p, boolean report) {
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "rw")) {
            long a = base + p.off;
            byte[] cur = new byte[p.orig.length];
            m.seek(a); m.readFully(cur);
            if (java.util.Arrays.equals(cur, p.now)) { if (report) LogCap.note("补丁[" + p.name + "] 已生效"); return; }
            if (!java.util.Arrays.equals(cur, p.orig)) { if (report) LogCap.note("补丁[" + p.name + "] 原字节不符 " + hexs(cur) + "，跳过"); return; }
            m.seek(a); m.write(p.now);
            m.seek(a); m.readFully(cur);
            LogCap.note("补丁[" + p.name + "] " + (java.util.Arrays.equals(cur, p.now) ? "写入成功" : "写入失败 " + hexs(cur)));
            Status.ok(p.name, java.util.Arrays.equals(cur, p.now));
        } catch (Throwable e) {
            if (report) LogCap.note("补丁[" + p.name + "] 出错 " + e);
            Status.ok(p.name, false);
        }
    }

    // ===== 地图图层修复 =====
    // 门 / 物资刷新点 / 载具刷新点 放在 ServerOnly 图层里，引擎只在专用服务器上加载它（客户端标志=0）。
    // 图层描述对象在堆上（0x90 字节）：+0 虚表，+0x21 客户端加载，+0x22 服务器加载。
    // 找到 “客户端0 / 服务器1” 的图层，把客户端标志改成 1，单机就会把这些图层也加载进来。
    static final long LAYER_VT = 0x4cade2cL, LAYER_K40 = 0x116ad54L;

    static void layerFix(long base) {
        final int vt = (int) (base + LAYER_VT), k40 = (int) (base + LAYER_K40);
        long t0 = System.currentTimeMillis();
        java.util.HashSet<Long> done = new java.util.HashSet<>();
        int scans = 0, total = 0;
        while (System.currentTimeMillis() - t0 < 120000) {
            scans++;
            total = 0;
            wide = scans > 2 && lastSeen == 0;
            int fixed = scanLayers(vt, k40, done);
            total = lastSeen;
            if (fixed > 0) LogCap.note("图层: 第" + scans + "次扫描改了 " + fixed + " 个 ServerOnly 图层（共找到图层 " + total + " 个）");
            if (done.size() >= 3) break;
            try { Thread.sleep(done.isEmpty() ? 1500 : 4000); } catch (InterruptedException e) { return; }
        }
        boolean ok = done.size() >= 1;
        LogCap.note("图层: 结束，已解锁 " + done.size() + " 个 ServerOnly 图层，扫描 " + scans + " 次，看到图层 " + total + " 个");
        Status.ok("图层" + done.size(), ok);
    }

    // ===== 声音诊断 =====
    // Wwise 初始化函数：静态区 0x53b5d50 = 已初始化标志，0x53b5d58 = LogAkAudio 日志级别。
    // 引擎把这个分类的日志关了，所以初始化失败也不吭声；这里把级别拉到 Log(5)，再读标志。
    static final long AK_FLAG = 0x53b5d50L, AK_VERB = 0x53b5d58L;
    public static volatile String audioStatus = "检测中";

    static void audioProbe(final long base) {
        Thread t = new Thread(new Runnable() { public void run() {
            // v1.14：不再改日志级别（v1.13 改了之后引擎启动闪退），只读标志
            try { Thread.sleep(25000); } catch (InterruptedException e) { return; }
            try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
                m.seek(base + AK_FLAG); int f = m.read();
                m.seek(base + AK_VERB); int v = m.read();
                audioStatus = f == 1 ? "Wwise 音效引擎已启动" : "Wwise 音效引擎没启动(" + f + ")";
                LogCap.note("声音: Wwise 标志=" + f + " 日志级别=" + v);
            } catch (Throwable e) { LogCap.note("声音: 探测出错 " + e); audioStatus = "探测出错"; }
        }}, "zframe-audio");
        t.setDaemon(true); t.start();
    }

    // ===== 缩短开局等待 =====
    // ANZBRGameState: +0x73c = ResetStartMatchCountDownTime(5.0)，旁边是 WaitingPlayers(60) / StartMatch(45)。
    // 找到这样的对象，把 60 和 45 改成 WAIT 秒（类默认对象也会被改到，下一局直接生效）。
    static final float WAIT = 3f;
    static volatile long ueBase;
    public static volatile String waitStatus = "等待进图";
    static volatile long lastWaitScan;

    // v1.17 安全模式：v1.12~1.16 启动闪退，怀疑腾讯保护检测到 /proc/self/mem 读写后杀进程
    static final boolean MEM = false; // v1.18 仍关闭，确认不闪退后再逐个打开

    public static void shortenWait(final String why) {
        if (!MEM) { waitStatus = "安全模式已关闭"; return; }
        if (System.currentTimeMillis() - lastWaitScan < 3000) return;
        lastWaitScan = System.currentTimeMillis();
        new Thread(new Runnable() { public void run() {
            int total = 0;
            for (int round = 0; round < 12; round++) {
                int n = scanWait();
                total += n;
                if (n > 0) { waitStatus = why + "：已改 " + total + " 处，开局等待≈" + (int) WAIT + "秒"; LogCap.note("等待时间: " + waitStatus); }
                if (total >= 2 && round >= 2) break;
                try { Thread.sleep(2500); } catch (InterruptedException e) { return; }
            }
            if (total == 0) { waitStatus = why + "：没找到计时对象"; LogCap.note("等待时间: 没找到"); }
        }}, "zframe-wait").start();
    }

    static int scanWait() {
        int fixed = 0;
        final int F120 = Float.floatToIntBits(120f), F1 = Float.floatToIntBits(1f);
        final int F5 = Float.floatToIntBits(5f), F60 = Float.floatToIntBits(60f), F45 = Float.floatToIntBits(45f), FW = Float.floatToIntBits(WAIT);
        java.util.ArrayList<long[]> regs = rwRegions(false);
        byte[] buf = new byte[1 << 20];
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "rw")) {
            for (long[] rg : regs) {
                for (long a = rg[0]; a < rg[1]; a += buf.length - 0x800) {
                    int n = (int) Math.min(buf.length, rg[1] - a);
                    try { m.seek(a); m.readFully(buf, 0, n); } catch (Throwable e) { continue; }
                    java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(buf, 0, n).order(java.nio.ByteOrder.LITTLE_ENDIAN);
                    for (int i = 0; i + 0x780 <= n; i += 4) {
                        if (bb.getInt(i + 0x73c) != F5) continue;
                        int o60 = -1, o45 = -1;
                        for (int k = 0x71c; k <= 0x760; k += 4) {
                            int v = bb.getInt(i + k);
                            if (v == F60) o60 = k; else if (v == F45) o45 = k;
                        }
                        if (o60 < 0 || o45 < 0) continue;
                        // 必须像一个真正的游戏对象：开头是 libUE4 里的虚表，窗口里还要有 120 和 1
                        long vt = bb.getInt(i) & 0xffffffffL;
                        if (ueBase == 0 || vt < ueBase || vt > ueBase + 0x5500000L) continue;
                        boolean has120 = false, has1 = false;
                        for (int k = 0x71c; k <= 0x760; k += 4) { int v = bb.getInt(i + k); if (v == F120) has120 = true; if (v == F1) has1 = true; }
                        if (!has120 || !has1) continue;
                        byte[] w = new byte[4];
                        java.nio.ByteBuffer.wrap(w).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(FW);
                        m.seek(a + i + o60); m.write(w);
                        m.seek(a + i + o45); m.write(w);
                        bb.putInt(i + o60, FW); bb.putInt(i + o45, FW);
                        LogCap.note(String.format("等待时间: 对象 %x 偏移 60@%x 45@%x", a + i, o60, o45));
                        fixed++;
                    }
                }
            }
        } catch (Throwable e) { LogCap.note("等待时间: 扫描出错 " + e); }
        return fixed;
    }

    static java.util.ArrayList<long[]> rwRegions(boolean wide) {
        java.util.ArrayList<long[]> regs = new java.util.ArrayList<>();
        try (BufferedReader r = new BufferedReader(new FileReader("/proc/self/maps"), 1 << 16)) {
            String l;
            while ((l = r.readLine()) != null) {
                String[] f = l.split("\\s+", 6);
                if (f.length < 5 || !f[1].startsWith("rw")) continue;
                String name = f.length > 5 ? f[5].trim() : "";
                if (wide) { if (name.startsWith("[anon:dalvik") || name.startsWith("/dev/") || name.startsWith("[stack")) continue; }
                else if (!(name.isEmpty() || name.startsWith("[anon:libc_malloc") || name.startsWith("[anon:scudo")
                        || name.startsWith("[anon:jemalloc") || name.equals("[heap]") || name.startsWith("[anon:.bss"))) continue;
                int dash = f[0].indexOf('-');
                long s = Long.parseLong(f[0].substring(0, dash), 16), e = Long.parseLong(f[0].substring(dash + 1), 16);
                if (e - s > (1024L << 20)) continue;
                regs.add(new long[]{s, e});
            }
        } catch (Throwable ignored) {}
        return regs;
    }

    static int lastSeen; static boolean wide; static long lastMB; static int lastRegs;
    static String lastFlags = "";

    static int scanLayers(int vt, int k40, java.util.Set<Long> done) {
        int fixed = 0, seen = 0;
        StringBuilder flags = new StringBuilder();
        java.util.ArrayList<long[]> regs = new java.util.ArrayList<>();
        try (BufferedReader r = new BufferedReader(new FileReader("/proc/self/maps"), 1 << 16)) {
            String l;
            while ((l = r.readLine()) != null) {
                String[] f = l.split("\\s+", 6);
                if (f.length < 5 || !f[1].startsWith("rw")) continue;
                String name = f.length > 5 ? f[5].trim() : "";
                if (wide) { if (name.startsWith("[anon:dalvik") || name.startsWith("/dev/") || name.startsWith("[stack")) continue; }
                else if (!(name.isEmpty() || name.startsWith("[anon:libc_malloc") || name.startsWith("[anon:scudo")
                        || name.startsWith("[anon:jemalloc") || name.equals("[heap]") || name.startsWith("[anon:.bss"))) continue;
                int dash = f[0].indexOf('-');
                long s = Long.parseLong(f[0].substring(0, dash), 16), e = Long.parseLong(f[0].substring(dash + 1), 16);
                if (e - s > (1024L << 20)) continue;
                regs.add(new long[]{s, e});
            }
        } catch (Throwable ignored) {}
        byte[] buf = new byte[1 << 20];
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "rw")) {
            for (long[] rg : regs) {
                for (long a = rg[0]; a < rg[1]; a += buf.length - 0x100) {
                    int n = (int) Math.min(buf.length, rg[1] - a);
                    try { m.seek(a); m.readFully(buf, 0, n); } catch (Throwable e) { continue; }
                    java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(buf, 0, n).order(java.nio.ByteOrder.LITTLE_ENDIAN);
                    for (int i = 0; i + 0x44 <= n; i += 8) {
                        if (bb.getInt(i) != vt) continue;
                        if ((buf[i + 0x21] & 0xfe) != 0 || (buf[i + 0x22] & 0xfe) != 0 || (buf[i + 0x23] & 0xfe) != 0) continue;
                        long obj = a + i;
                        seen++;
                        byte cli = buf[i + 0x21], srv = buf[i + 0x22];
                        flags.append(cli).append(srv).append(' ');
                        if (cli == 0 && srv == 1 && !done.contains(obj)) {
                            m.seek(obj + 0x21); m.write(1);
                            m.seek(obj + 0x21);
                            if (m.read() == 1) { done.add(obj); fixed++; }
                        }
                    }
                }
            }
        } catch (Throwable e) { LogCap.note("图层: 扫描出错 " + e); }
        lastSeen = seen;
        lastFlags = flags.toString();
        long mb = 0; for (long[] rg : regs) mb += (rg[1] - rg[0]) >> 20; lastMB = mb; lastRegs = regs.size();
        return fixed;
    }

    static long findBase(String lib) {
        long best = 0;
        try (BufferedReader r = new BufferedReader(new FileReader("/proc/self/maps"), 1 << 16)) {
            String l;
            while ((l = r.readLine()) != null) {
                if (!l.endsWith(lib) && !l.endsWith("/" + lib)) continue;
                long s = Long.parseLong(l.substring(0, l.indexOf('-')), 16);
                if (best == 0 || s < best) best = s;
            }
        } catch (Throwable ignored) {}
        return best;
    }

    static byte[] hex(String s) {
        byte[] b = new byte[s.length() / 2];
        for (int i = 0; i < b.length; i++) b[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        return b;
    }
    static String hexs(byte[] b) { StringBuilder sb = new StringBuilder(); for (byte x : b) sb.append(String.format("%02x", x)); return sb.toString(); }

    /** 给菜单显示用 */
    public static class Status {
        static final java.util.LinkedHashMap<String, Boolean> m = new java.util.LinkedHashMap<>();
        static synchronized void ok(String n, boolean v) { m.put(n, v); }
        public static synchronized String text() {
            if (m.isEmpty()) return "补丁：还没应用（等游戏加载引擎）";
            StringBuilder sb = new StringBuilder("补丁：");
            for (java.util.Map.Entry<String, Boolean> e : m.entrySet()) sb.append(e.getKey()).append(e.getValue() ? " ✓  " : " ✗  ");
            return sb.toString().trim();
        }
    }
}
