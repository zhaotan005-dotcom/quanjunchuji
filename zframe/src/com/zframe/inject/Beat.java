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
            java.util.List<String> cgs = cgDirs();
            q.addLast("cgroup " + grabAll("/proc/self/cgroup") + " 目录" + cgs);
            java.text.SimpleDateFormat df = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US);
            while (true) {
                try {
                    Thread.sleep(1000);
                    StringBuilder sb = new StringBuilder(df.format(new java.util.Date()));
                    sb.append(' ').append(grab("/proc/self/status", "VmPeak", "VmSize", "VmRSS", "VmSwap", "Threads")).append(" oom=").append(Beat.one("/proc/self/oom_score")).append("/").append(Beat.one("/proc/self/oom_score_adj"));
                    sb.append(' ').append(grab("/proc/meminfo", "MemAvailable", "SwapFree"));
                    sb.append(' ').append(cg(cgs));
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
    /** v4.04：查是不是 memcg（应用内存组）超上限被内核 OOM 杀——那种也是 SIGKILL、系统不记低内存 */
    static java.util.List<String> cgDirs() {
        java.util.List<String> out = new java.util.ArrayList<>();
        try (BufferedReader r = new BufferedReader(new FileReader("/proc/self/cgroup"))) {
            String l;
            while ((l = r.readLine()) != null) {
                String[] a = l.split(":", 3); if (a.length < 3) continue;
                String path = a[2];
                String[] roots = a[1].isEmpty() ? new String[]{"/sys/fs/cgroup"} : a[1].contains("memory") ? new String[]{"/dev/memcg", "/sys/fs/cgroup/memory", "/sys/fs/cgroup"} : new String[0];
                for (String root : roots) {
                    String d = root + path;
                    while (d.length() >= root.length()) { if (new File(d).isDirectory() && !out.contains(d)) out.add(d); int i = d.lastIndexOf('/'); if (i <= 0 || d.length() == root.length()) break; d = d.substring(0, i); }
                }
            }
        } catch (Throwable ignored) {}
        return out;
    }
    static String cg(java.util.List<String> dirs) {
        StringBuilder sb = new StringBuilder();
        for (String d : dirs) {
            StringBuilder e = new StringBuilder();
            for (String f : new String[]{"memory.current", "memory.max", "memory.high", "memory.usage_in_bytes", "memory.limit_in_bytes", "memory.max_usage_in_bytes"}) {
                File x = new File(d, f); if (!x.canRead()) continue;
                String v = one(x.getPath()); if (v == null || v.equals("max") || v.startsWith("9223372")) continue;
                e.append(f.substring(7)).append('=').append(mb(v)).append(' ');
            }
            File ev = new File(d, "memory.events"); if (ev.canRead()) e.append(grabSp(ev.getPath(), "oom", "oom_kill", "max", "high")).append(' ');
            File oc = new File(d, "memory.oom_control"); if (oc.canRead()) e.append(grabSp(oc.getPath(), "under_oom", "oom_kill")).append(' ');
            if (e.length() > 0) sb.append(d.replaceAll("^/(sys/fs/cgroup|dev/memcg)", "")).append('{').append(e.toString().trim()).append("} ");
        }
        return sb.length() == 0 ? "cg=无" : "cg " + sb.toString().trim();
    }
    static String mb(String v) { try { return (Long.parseLong(v.trim()) >> 20) + "M"; } catch (Throwable t) { return v; } }
    static String grabSp(String path, String... keys) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new FileReader(path))) { String l; while ((l = r.readLine()) != null) { String[] a = l.trim().split("\\s+"); if (a.length == 2) for (String k : keys) if (a[0].equals(k)) sb.append(k).append('=').append(a[1]).append(' '); } } catch (Throwable ignored) {}
        return sb.toString().trim();
    }
    static String grabAll(String p) { StringBuilder sb = new StringBuilder(); try (BufferedReader r = new BufferedReader(new FileReader(p))) { String l; while ((l = r.readLine()) != null) sb.append(l).append(" | "); } catch (Throwable ignored) {} return sb.toString(); }
    static String one(String p) { try (BufferedReader r = new BufferedReader(new FileReader(p))) { return r.readLine(); } catch (Throwable t) { return "?"; } }
    static String prev(android.content.Context c) {
        File f = new File(c.getFilesDir(), "beat-prev.txt");
        StringBuilder sb = new StringBuilder("== 上次运行最后的心跳（内存/线程）==\n");
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"))) { String l; while ((l = r.readLine()) != null) sb.append(l).append('\n'); }
        catch (Throwable t) { sb.append("没有\n"); }
        return sb.toString();
    }
}
