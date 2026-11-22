package com.zframe.inject;

import java.io.*;

/** v4.06 服务端（云手机）进大厅/开房几秒被 SIGKILL：6 次样本全在 RSS ~1.0GB（996~1040MB）、MemAvailable 还有 2.2GB、
 *  1GB swap 一直一点没用、系统没记低内存、也不是我们 killProcess（那种 logcat 有「Sending signal」，大厅那几次没有）→
 *  推断是云手机给单个应用的内存上限（memcg 或厂商看门狗，未证实）。
 *  办法：内存超过 850MB 时，在游戏线程调 libc madvise(范围, 长度, MADV_PAGEOUT=21)，把私有匿名内存里的冷页主动换到 swap
 *  （内核不会自己换：swap 一直是满的空闲），常驻内存降下来。热页会马上换回，冷页留在 swap。
 *  只在服务端包（Ed.ED==1）跑；每 15 秒最多一轮，一轮最多 64 次调用；第一轮降不到 20MB 就停（这台机子不吃这招，免得白白卡顿）。
 *  不改游戏代码字节，只调系统函数；内核不支持 PAGEOUT 时 madvise 返回 -1，什么都不发生。未实测。 */
final class MemTrim {
    static volatile String state = "内存换出：没跑";
    static volatile boolean started, off;
    static volatile long fn;
    static final long LIMIT_KB = 850 * 1024;

    static void start() {
        if (started || Ed.ED != 1) return; started = true;
        new Thread(() -> {
            long last = 0; int rounds = 0;
            while (!off) {
                try {
                    Thread.sleep(1000);
                    if (!Server.callReady()) continue;
                    long rss = kb("VmRSS");
                    if (rss < LIMIT_KB || System.currentTimeMillis() - last < 15000) continue;
                    last = System.currentTimeMillis();
                    rounds++;
                    long gain = round(rss, rounds);
                    if (rounds == 1 && gain < 20 * 1024) { off = true; state += "；第一轮只降 " + gain / 1024 + "MB，停用"; LogCap.note(state); }
                } catch (InterruptedException e) { return; } catch (Throwable t) { state = "内存换出：出错 " + t; LogCap.note(state); off = true; }
            }
        }, "zf-memtrim").start();
    }

    static long round(long rss0, int n) throws Exception {
        if (fn == 0) { fn = madvise(); if (fn == 0) { off = true; state = "内存换出：找不到 32 位 libc 的 madvise"; LogCap.note(state); return 0; } }
        java.util.List<long[]> rs = ranges();
        rs.sort((a, b) -> Long.compare(b[1] - b[0], a[1] - a[0]));
        int calls = 0, ok = 0; long bytes = 0; long ret = 0;
        for (long[] r : rs) {
            if (calls >= 64) break;
            long[] q = Server.gcall(fn, r[0], r[1] - r[0], 21, 0, 1500);
            calls++;
            if (q[0] == 1) { ret = q[1]; if (q[1] == 0) { ok++; bytes += r[1] - r[0]; } }
        }
        Thread.sleep(800);
        long rss1 = kb("VmRSS"), sw = kb("VmSwap");
        long gain = rss0 - rss1;
        state = "内存换出第 " + n + " 轮：RSS " + rss0 / 1024 + "→" + rss1 / 1024 + "MB，进程 swap " + sw / 1024 + "MB，范围 " + rs.size() + " 个，调用 " + calls + " 次，成功 " + ok + "（" + bytes / 1048576 + "MB），最后返回 " + (int) ret;
        LogCap.note(state);
        return gain;
    }

    /** 私有可写匿名内存（32 位地址内，≥256KB，相邻的合并）；不碰栈、Java 堆、显存/驱动映射 */
    static java.util.List<long[]> ranges() throws IOException {
        java.util.List<long[]> out = new java.util.ArrayList<>();
        try (BufferedReader r = new BufferedReader(new FileReader("/proc/self/maps"), 1 << 16)) {
            String l; long[] cur = null;
            while ((l = r.readLine()) != null) {
                String[] p = l.trim().split("\\s+", 6);
                if (p.length < 5) continue;
                String name = p.length > 5 ? p[5] : "";
                int dash = p[0].indexOf('-');
                long s = Long.parseLong(p[0].substring(0, dash), 16), e = Long.parseLong(p[0].substring(dash + 1), 16);
                boolean take = p[1].equals("rw-p") && p[4].equals("0") && e <= 0xffffffffL
                        && (name.isEmpty() || (name.startsWith("[anon:") && !name.contains("stack") && !name.contains("dalvik") && !name.contains("bss")
                            && !name.contains("signal") && !name.contains("gralloc") && !name.contains("dmabuf") && !name.contains("kgsl") && !name.contains("GL")));
                if (!take) { if (cur != null) { if (cur[1] - cur[0] >= 256 * 1024) out.add(cur); cur = null; } continue; }
                if (cur != null && cur[1] == s) cur[1] = e;
                else { if (cur != null && cur[1] - cur[0] >= 256 * 1024) out.add(cur); cur = new long[] { s, e }; }
            }
            if (cur != null && cur[1] - cur[0] >= 256 * 1024) out.add(cur);
        }
        return out;
    }

    /** 32 位 ARM libc.so 里 madvise 的运行地址（读 ELF 动态符号表） */
    static long madvise() {
        try (BufferedReader r = new BufferedReader(new FileReader("/proc/self/maps"), 1 << 16)) {
            String l; java.util.LinkedHashMap<String, Long> libs = new java.util.LinkedHashMap<>();
            while ((l = r.readLine()) != null) {
                String[] p = l.trim().split("\\s+", 6);
                if (p.length < 6 || !p[5].endsWith("/libc.so")) continue;
                long s = Long.parseLong(p[0].substring(0, p[0].indexOf('-')), 16);
                if (s > 0xffffffffL) continue;
                Long o = libs.get(p[5]); if (o == null || s < o) libs.put(p[5], s);
            }
            for (java.util.Map.Entry<String, Long> en : libs.entrySet()) {
                long a = sym(en.getKey(), en.getValue(), "madvise");
                if (a != 0) { state = "内存换出：madvise 在 " + en.getKey() + " +" + Long.toHexString(a - en.getValue()); LogCap.note(state); return a; }
            }
        } catch (Throwable t) { state = "内存换出：找 libc 出错 " + t; }
        return 0;
    }
    static long sym(String path, long base, String want) {
        try {
            byte[] f = java.nio.file.Files.readAllBytes(new File(path).toPath());
            java.nio.ByteBuffer b = java.nio.ByteBuffer.wrap(f).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            if (f[0] != 0x7f || f[1] != 'E' || f[4] != 1 || (b.getShort(18) & 0xffff) != 40) return 0;   // ELF32 ARM
            long phoff = b.getInt(28) & 0xffffffffL, shoff = b.getInt(32) & 0xffffffffL;
            int phent = b.getShort(42) & 0xffff, phnum = b.getShort(44) & 0xffff, shent = b.getShort(46) & 0xffff, shnum = b.getShort(48) & 0xffff;
            long lo = -1;
            for (int i = 0; i < phnum; i++) { int o = (int) (phoff + (long) i * phent); if (b.getInt(o) == 1) { long v = b.getInt(o + 8) & 0xffffffffL; if (lo < 0 || v < lo) lo = v; } }
            if (lo < 0) lo = 0;
            long bias = base - (lo & ~0xfffL);
            for (int i = 0; i < shnum; i++) {
                int o = (int) (shoff + (long) i * shent);
                if (b.getInt(o + 4) != 11) continue;   // SHT_DYNSYM
                int symOff = b.getInt(o + 16), symSize = b.getInt(o + 20), link = b.getInt(o + 24);
                int so = (int) (shoff + (long) link * shent), strOff = b.getInt(so + 16);
                for (int k = 0; k + 16 <= symSize; k += 16) {
                    int nameOff = b.getInt(symOff + k); long val = b.getInt(symOff + k + 4) & 0xffffffffL;
                    if (val == 0) continue;
                    int q = strOff + nameOff, n = 0; while (q + n < f.length && f[q + n] != 0) n++;
                    if (n == want.length() && new String(f, q, n, "ISO-8859-1").equals(want)) return bias + val;
                }
            }
        } catch (Throwable ignored) {}
        return 0;
    }
    static long kb(String key) {
        String s = Beat.grab("/proc/self/status", key);
        try { return Long.parseLong(s.substring(s.indexOf('=') + 1).replace("k", "").trim()); } catch (Throwable t) { return 0; }
    }
}
