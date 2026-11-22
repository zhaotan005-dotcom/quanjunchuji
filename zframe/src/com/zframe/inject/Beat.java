package com.zframe.inject;

import java.io.*;

/** v4.00：每秒记一行内存/线程到私有目录 beat.txt（只留最近 90 行），下次启动挪成 beat-prev.txt，诊断里带上——查「被 SIGKILL 无声退出」是不是内存顶满 */
final class Beat {
    static volatile boolean started;
    static void start(android.content.Context c) {
        if (started) return; started = true;
        final File dir = c.getFilesDir(), cur = new File(dir, "beat.txt"), prev = new File(dir, "beat-prev.txt");
        try { if (cur.exists()) { prev.delete(); cur.renameTo(prev); } } catch (Throwable ignored) {}
        new Thread(() -> {
            java.util.ArrayDeque<String> q = new java.util.ArrayDeque<>();
            java.text.SimpleDateFormat df = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US);
            while (true) {
                try {
                    Thread.sleep(1000);
                    StringBuilder sb = new StringBuilder(df.format(new java.util.Date()));
                    sb.append(' ').append(grab("/proc/self/status", "VmRSS", "VmSwap", "Threads"));
                    sb.append(' ').append(grab("/proc/meminfo", "MemAvailable", "SwapFree"));
                    StringBuilder zf = new StringBuilder();
                    for (Thread t : Thread.getAllStackTraces().keySet()) if (t.getName().startsWith("zf")) zf.append(t.getName()).append(',');
                    sb.append(" 线程[").append(zf).append(']');
                    String n = LogCap.lastNote; if (n != null) sb.append(" 最后:").append(n.length() > 60 ? n.substring(0, 60) : n);
                    q.addLast(sb.toString()); while (q.size() > 90) q.removeFirst();
                    File tmp = new File(dir, "beat.tmp");
                    try (Writer w = new OutputStreamWriter(new FileOutputStream(tmp), "UTF-8")) { for (String s : q) { w.write(s); w.write('\n'); } }
                    tmp.renameTo(cur);
                } catch (InterruptedException e) { return; } catch (Throwable ignored) {}
            }
        }, "zbeat").start();
    }
    static String grab(String path, String... keys) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new FileReader(path))) {
            String l;
            while ((l = r.readLine()) != null) for (String k : keys) if (l.startsWith(k + ":")) sb.append(k).append('=').append(l.substring(k.length() + 1).trim().replace(" kB", "k")).append(' ');
        } catch (Throwable ignored) {}
        return sb.toString().trim();
    }
    static String prev(android.content.Context c) {
        File f = new File(c.getFilesDir(), "beat-prev.txt");
        StringBuilder sb = new StringBuilder("== 上次运行最后的心跳（内存/线程）==\n");
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"))) { String l; while ((l = r.readLine()) != null) sb.append(l).append('\n'); }
        catch (Throwable t) { sb.append("没有\n"); }
        return sb.toString();
    }
}
