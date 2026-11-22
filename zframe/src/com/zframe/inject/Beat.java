package com.zframe.inject;

import java.io.*;

/** v4.00：每秒记一行内存/线程到私有目录 beat.txt（只留最近 90 行），下次启动挪成 beat-prev.txt，诊断里带上——查「被 SIGKILL 无声退出」是不是内存顶满 */
final class Beat {
    static volatile boolean started;
    static void start(android.content.Context c) {
        if (started) return; started = true;
        final File dir = c.getFilesDir(), cur = new File(dir, "beat.txt"), prev = new File(dir, "beat-prev.txt");
        try { if (cur.exists()) { prev.delete(); cur.renameTo(prev); } } catch (Throwable ignored) {}
        try { File sm = new File(dir, "smaps.txt"), sp = new File(dir, "smaps-prev.txt"); if (sm.exists()) { sp.delete(); sm.renameTo(sp); } } catch (Throwable ignored) {}
        new Thread(() -> {
            java.util.ArrayDeque<String> q = new java.util.ArrayDeque<>();
            java.util.List<String> cgs = cgDirs();
            q.addLast("cgroup " + grabAll("/proc/self/cgroup") + " 目录" + cgs);
            java.text.SimpleDateFormat df = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US);
            while (true) {
                try {
                    Thread.sleep(1000);
                    StringBuilder sb = new StringBuilder(df.format(new java.util.Date()));
                    sb.append(' ').append(grab("/proc/self/status", "VmRSS", "RssAnon", "RssFile", "RssShmem", "VmSwap", "Threads")).append(" oom=").append(Beat.one("/proc/self/oom_score")).append("/").append(Beat.one("/proc/self/oom_score_adj"));
                    sb.append(' ').append(grab("/proc/meminfo", "MemAvailable", "SwapFree"));
                    sb.append(' ').append(cg(cgs));
                    StringBuilder zf = new StringBuilder();
                    for (Thread t : Thread.getAllStackTraces().keySet()) if (t.getName().startsWith("zf")) zf.append(t.getName()).append(',');
                    sb.append(" 线程[").append(zf).append(']');
                    if (!smapsDone && Ed.ED == 1) { long rss = MemTrim.kb("VmRSS"); if (rss > 800 * 1024) { smapsDone = true; new Thread(() -> smaps(dir), "zf-smaps").start(); } }
                    if (MemTrim.started) sb.append(" [").append(MemTrim.state.length() > 50 ? MemTrim.state.substring(0, 50) : MemTrim.state).append(']');
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
    static volatile boolean smapsDone;
    /** v4.06：服务端内存第一次过 800MB 时，按映射名汇总 /proc/self/smaps 的 Rss/Swap（前 15 名）写 smaps.txt，下次启动进诊断——看 1GB 是谁占的 */
    static void smaps(File dir) {
        java.util.HashMap<String, long[]> m = new java.util.HashMap<>();
        try (BufferedReader r = new BufferedReader(new FileReader("/proc/self/smaps"), 1 << 16)) {
            String l, name = "?";
            while ((l = r.readLine()) != null) {
                if (l.length() > 0 && Character.isLetterOrDigit(l.charAt(0)) && l.indexOf('-') > 0 && l.indexOf(' ') > l.indexOf('-')) {
                    String[] p = l.trim().split("\\s+", 6);
                    name = p.length > 5 ? p[5] : "（无名匿名）";
                    if (name.startsWith("/")) { int i = name.lastIndexOf('/'); name = name.substring(i + 1); }
                    if (name.startsWith("[anon:dalvik")) name = "[anon:dalvik*]";
                    continue;
                }
                int k = l.startsWith("Rss:") ? 0 : l.startsWith("Swap:") ? 1 : l.startsWith("Anonymous:") ? 2 : -1;
                if (k < 0) continue;
                long v = Long.parseLong(l.replaceAll("[^0-9]", ""));
                long[] a = m.get(name); if (a == null) m.put(name, a = new long[3]); a[k] += v;
            }
        } catch (Throwable t) { m.put("出错 " + t, new long[3]); }
        java.util.List<java.util.Map.Entry<String, long[]>> es = new java.util.ArrayList<>(m.entrySet());
        es.sort((x, y) -> Long.compare(y.getValue()[0], x.getValue()[0]));
        StringBuilder sb = new StringBuilder(new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(new java.util.Date()) + " smaps 前 15（Rss/Anon/Swap MB）：\n");
        long tot = 0; for (java.util.Map.Entry<String, long[]> e : es) tot += e.getValue()[0];
        sb.append("  合计 Rss ").append(tot / 1024).append("MB，").append(es.size()).append(" 种\n");
        for (int i = 0; i < es.size() && i < 15; i++) { long[] a = es.get(i).getValue(); sb.append("  ").append(a[0] / 1024).append('/').append(a[2] / 1024).append('/').append(a[1] / 1024).append("  ").append(es.get(i).getKey()).append('\n'); }
        try (Writer w = new OutputStreamWriter(new FileOutputStream(new File(dir, "smaps.txt")), "UTF-8")) { w.write(sb.toString()); } catch (Throwable ignored) {}
        LogCap.note("smaps 已记（合计 " + tot / 1024 + "MB）");
    }
    static String prev(android.content.Context c) {
        File f = new File(c.getFilesDir(), "beat-prev.txt");
        StringBuilder sb = new StringBuilder("== 上次运行最后的心跳（内存/线程）==\n");
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"))) { String l; while ((l = r.readLine()) != null) sb.append(l).append('\n'); }
        catch (Throwable t) { sb.append("没有\n"); }
        File sp = new File(c.getFilesDir(), "smaps-prev.txt");
        if (sp.exists()) try { sb.append("== 上次运行 smaps 汇总 ==\n").append(new String(java.nio.file.Files.readAllBytes(sp.toPath()), "UTF-8")); } catch (Throwable ignored) {}
        return sb.toString();
    }
}
