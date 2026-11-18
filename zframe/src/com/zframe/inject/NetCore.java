package com.zframe.inject;

import android.content.Context;
import java.io.*;

/**
 * v2.52 在线内核：免安装更新。
 *  - 启动时：游戏私有目录里有从 GitHub 下好的、同一包型(BUILD)、版本更高的内核 → 直接换成它（链式加载）。
 *  - 进大厅后：后台从 GitHub 查最新版本号，有新的就下载，下次启动生效（菜单里提示「重开游戏」）。
 *  - 新内核连续 2 次没进到大厅（闪退）→ 自动不用它，退回 ZFrame 里的内核。
 *  只在游戏进程里联网（ZFrame App 没有联网权限）；下载只在大厅后台进行，启动阶段不联网。
 */
final class NetCore {
    // v2.72/v2.86：多个镜像一起查版本号，取最大的（jsdelivr @main 会缓存旧号；云手机直连 GitHub 常失败）
    static final String RAW = "https://raw.githubusercontent.com/zhaotan005-dotcom/quanjunchuji/main/core/";
    static final String[] BASES = {
        "https://ghfast.top/" + RAW,
        "https://gh-proxy.com/" + RAW,
        "https://testingcf.jsdelivr.net/gh/zhaotan005-dotcom/quanjunchuji@main/core/",
        "https://cdn.jsdelivr.net/gh/zhaotan005-dotcom/quanjunchuji@main/core/",
        "https://gcore.jsdelivr.net/gh/zhaotan005-dotcom/quanjunchuji@main/core/",
        RAW,
    };
    /** 并发查各镜像的版本号，最多等 12 秒；返回 各镜像版本（-1 = 失败） */
    static int[] vers(final String name, final StringBuilder err) {
        final int[] v = new int[BASES.length]; java.util.Arrays.fill(v, -1);
        Thread[] ts = new Thread[BASES.length];
        for (int k = 0; k < BASES.length; k++) {
            final int i = k;
            ts[i] = new Thread(() -> { try { v[i] = Integer.parseInt(new String(get(BASES[i] + name + ".ver?t=" + System.currentTimeMillis() / 60000, 64)).trim()); } catch (Throwable t) { synchronized (err) { err.append(BASES[i].substring(8, Math.min(28, BASES[i].length()))).append("：").append(t.getMessage()).append("；"); } } }, "zf-ver" + i);
            ts[i].setDaemon(true); ts[i].start();
        }
        long end = System.currentTimeMillis() + 12000;
        for (Thread t : ts) { long w = end - System.currentTimeMillis(); if (w > 0) try { t.join(w); } catch (InterruptedException ignored) {} }
        return v.clone();
    }
    static volatile String status = "";
    static volatile int netVer;
    static volatile int pct = -1;   // v2.56 下载进度 0~100，没在下 = -1

    static File f(Context c, String n) { return new File(c.getFilesDir(), n); }
    static String read(File f) { try { return new String(java.nio.file.Files.readAllBytes(f.toPath())).trim(); } catch (Throwable e) { return ""; } }
    static void write(File f, String s) { try { java.nio.file.Files.write(f.toPath(), s.getBytes()); } catch (Throwable ignored) {} }

    /** 在 ZInit.boot 最前面调。返回 true = 已交给更新的内核，本内核什么都不做 */
    static boolean chain(Context ctx, boolean main) {
        try {
            File dex = f(ctx, "zcore_net.dex"), ver = f(ctx, "zcore_net.ver"), tries = f(ctx, "zcore_net.try");
            String[] v = read(ver).split(" ");
            if (!dex.exists() || v.length < 2) return false;
            int nv = Integer.parseInt(v[0]), nb = Integer.parseInt(v[1]);
            if (nb != Ed.BUILD || nv <= ZInit.CORE_VER) return false;
            int t = 0; try { t = Integer.parseInt(read(tries)); } catch (Throwable ignored) {}
            // v2.84 用户：不要自动退回老版本 → 不再因为连续没进大厅就不用（只记次数，诊断里看）
            if (main) write(tries, String.valueOf(t + 1));
            // v3.12 用户：装完闪退 —— 以前每版都拷成同一个名字 zcore_netrun.dex，ART 可能复用上一版留下的 oat/vdex → 新旧代码混着跑。
            //  根修：每个版本用自己的文件名，旧的全删
            File run = new File(ctx.getCodeCacheDir(), "zcore_netrun" + nv + ".dex");
            File[] olds = ctx.getCodeCacheDir().listFiles();
            if (olds != null) for (File o : olds) if (o.getName().startsWith("zcore_netrun") && !o.getName().startsWith("zcore_netrun" + nv + ".")) { o.setWritable(true); o.delete(); }
            File oat = new File(ctx.getCodeCacheDir(), "oat"); deleteOld(oat, nv);
            // 几个进程同时启动会一起走这里：已有同长度的就直接用；否则写到本进程自己的临时文件再原子改名，不删别人正在用的
            if (!run.exists() || run.length() != dex.length()) {
                File tmp = new File(ctx.getCodeCacheDir(), "znrtmp" + android.os.Process.myPid() + ".dex");
                tmp.setWritable(true); tmp.delete();
                try (InputStream in = new FileInputStream(dex); OutputStream o = new FileOutputStream(tmp)) { copy(in, o); }
                tmp.setReadOnly();
                if (!tmp.renameTo(run)) { tmp.setWritable(true); tmp.delete(); if (!run.exists()) return false; }
            }
            ClassLoader cl = new dalvik.system.DexClassLoader(run.getAbsolutePath(), ctx.getCodeCacheDir().getAbsolutePath(), null, Context.class.getClassLoader());
            cl.loadClass(ZInit.class.getName()).getMethod("boot", Context.class).invoke(null, ctx);
            android.util.Log.i(ZInit.TAG, "已换成在线内核 " + nv);
            write(f(ctx, "zcore_net.just"), String.valueOf(nv));   // v2.54：新内核进大厅时提示「已更新」
            return true;
        } catch (Throwable t) {
            android.util.Log.w(ZInit.TAG, "在线内核加载失败", t);
            return false;
        }
    }

    static void copy(InputStream in, OutputStream o) throws IOException { byte[] b = new byte[1 << 16]; int r; while ((r = in.read(b)) > 0) o.write(b, 0, r); }

    static byte[] get(String url, int max) throws IOException {
        java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url + "?t=" + System.currentTimeMillis() / 60000).openConnection();
        c.setConnectTimeout(8000); c.setReadTimeout(15000); c.setUseCaches(false);
        try {
            if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            try (InputStream in = c.getInputStream()) { byte[] b = new byte[1 << 16]; int r; while ((r = in.read(b)) > 0) { bo.write(b, 0, r); if (bo.size() > max) throw new IOException("太大"); } }
            return bo.toByteArray();
        } finally { c.disconnect(); }
    }

    /** 带进度的下载（每 5% 报一次） */
    static byte[] getP(String url, int max) throws IOException {
        java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url + "?t=" + System.currentTimeMillis() / 60000).openConnection();
        c.setConnectTimeout(8000); c.setReadTimeout(15000); c.setUseCaches(false);
        try {
            if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
            int len = c.getContentLength(); int lastP = -1;
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            try (InputStream in = c.getInputStream()) { byte[] b = new byte[1 << 15]; int r; while ((r = in.read(b)) > 0) { bo.write(b, 0, r); if (bo.size() > max) throw new IOException("太大");
                if (len > 0) { int p = (int) (bo.size() * 100L / len); if (p / 5 != lastP / 5) { lastP = p; pct = p; say("正在下载 " + p + "%", false); } } } }
            return bo.toByteArray();
        } finally { c.disconnect(); }
    }

    static volatile boolean started;
    static volatile int readyVer;   // 已下好、等重启的版本
    interface L { void on(String msg, boolean ready); }
    static volatile L listener;
    /** v2.68：这次检查已经有结果（最新 / 下好了 / 失败）。加载页的选择框等它 */
    static volatile boolean done;
    static void say(String m, boolean ready) { status = m; if (ready || m.startsWith("已是最新") || m.startsWith("检查更新失败")) done = true; GameOverlay.refreshAny(); LogCap.note("在线更新：" + m); L l = listener; if (l != null) try { l.on(m, ready); } catch (Throwable ignored) {} }

    /** 进大厅后调：清掉闪退计数；本内核是刚换上的就提示「已更新」；再查一次新版本（选单机/联机时查过就不重复） */
    static void lobby(final Context ctx) {
        if (ctx == null) return;
        f(ctx, "zcore_net.try").delete();
        File j = f(ctx, "zcore_net.just");
        if (j.exists()) { j.delete(); GameOverlay.toastAny("已更新到 " + GameOverlay.VER + "（内核 " + ZInit.CORE_VER + "）"); }
        if (readyVer > ZInit.CORE_VER) GameDlg.updatePrompt();
        check(ctx);
    }

    /** 后台查 GitHub 上的版本，比本地新就下载（下次启动生效） */
    static void check(final Context ctx) {
        if (ctx == null || started) return; started = true;
        say("正在检查更新…", false);
        new Thread(() -> {
            String err = "";
            final String name0 = "b" + Ed.BUILD;
            StringBuilder eb = new StringBuilder();
            int[] vs = vers(name0, eb); err = eb.toString();
            int best = -1; for (int x : vs) best = Math.max(best, x);
            LogCap.note("在线更新：各镜像版本 " + java.util.Arrays.toString(vs));
            for (int bi = 0; bi < BASES.length; bi++) {
                if (vs[bi] < 0 || vs[bi] != best) continue;
                String base = BASES[bi];
                try {
                    String name = name0;
                    int v = vs[bi];
                    netVer = v;
                    int have = ZInit.CORE_VER;
                    String[] hv = read(f(ctx, "zcore_net.ver")).split(" ");
                    try { if (hv.length >= 2 && Integer.parseInt(hv[1]) == Ed.BUILD) have = Math.max(have, Integer.parseInt(hv[0])); } catch (Throwable ignored) {}
                    if (v <= ZInit.CORE_VER) { say("已是最新（" + GameOverlay.VER + "）", false); return; }
                    if (v <= have) { readyVer = have; say("新版本已下好，重启游戏生效", true); GameDlg.updatePrompt(); return; }
                    pct = 0; say("发现新版本，正在下载…", false);
                    byte[] d = getP(base + name + ".dex", 8 << 20);
                    if (d.length < 1000 || d[0] != 'd' || d[1] != 'e' || d[2] != 'x') throw new IOException("不是 dex");
                    File tmp = f(ctx, "zcore_net.tmp"), dex = f(ctx, "zcore_net.dex");
                    try (OutputStream o = new FileOutputStream(tmp)) { o.write(d); }
                    // v3.12 用户：装完闪退/没检测到更新 —— 镜像缓存可能 .ver 是新的、.dex 还是旧的（或别的版本）→ 下错包，重启后跑的是旧代码甚至闪退。
                    //  根修：下完先读 dex 里自己的 CORE_VER 和 BUILD，对不上就不装，换下一个镜像
                    String bad = check(ctx, tmp, v);
                    if (bad != null) { tmp.delete(); throw new IOException("包不对（" + bad + "）"); }
                    dex.setWritable(true); dex.delete();
                    if (!tmp.renameTo(dex)) throw new IOException("改名失败");
                    write(f(ctx, "zcore_net.ver"), v + " " + Ed.BUILD);
                    f(ctx, "zcore_net.try").delete();
                    readyVer = v;
                    pct = 100; say("新版本已下好，重启游戏生效", true);
                    GameDlg.updatePrompt();
                    return;
                } catch (Throwable t) { err += base.substring(8, 20) + "：" + t.getMessage() + "；"; }
            }
            pct = -1; say("检查更新失败（网络）", false);
            LogCap.note("在线更新失败：" + err);
        }, "zframe-netcore").start();
    }

    static void deleteOld(File d, int nv) {
        if (d == null || !d.exists()) return;
        File[] fs = d.listFiles(); if (fs == null) return;
        for (File f : fs) { if (f.isDirectory()) deleteOld(f, nv); else if (f.getName().startsWith("zcore_netrun") && !f.getName().startsWith("zcore_netrun" + nv + ".")) { f.setWritable(true); f.delete(); } }
    }
    /** 下好的 dex 里的 CORE_VER 必须等于 .ver 写的版本、Ed.BUILD 必须是本版本；对得上返回 null */
    static String check(Context ctx, File dex, int want) {
        File dir = new File(ctx.getCodeCacheDir(), "zchk"); dir.mkdirs();
        File c = new File(dir, "c" + System.nanoTime() + ".dex");
        try {
            try (InputStream in = new FileInputStream(dex); OutputStream o = new FileOutputStream(c)) { copy(in, o); }
            c.setReadOnly();
            ClassLoader cl = new dalvik.system.DexClassLoader(c.getAbsolutePath(), dir.getAbsolutePath(), null, Context.class.getClassLoader());
            int cv = cl.loadClass(ZInit.class.getName()).getField("CORE_VER").getInt(null);
            java.lang.reflect.Field bf = cl.loadClass(Ed.class.getName()).getDeclaredField("BUILD"); bf.setAccessible(true);
            int b = bf.getInt(null);
            if (cv != want) return "内核 " + cv + " 不是 " + want;
            if (b != Ed.BUILD) return "版本 " + b + " 不是 " + Ed.BUILD;
            return null;
        } catch (Throwable t) { return "读不出 " + t; }
        finally { File[] fs = dir.listFiles(); if (fs != null) for (File f : fs) { f.setWritable(true); f.delete(); } }
    }

    /** 重启游戏：3.5 秒后用闹钟重新打开，自己退出 */
    static void restart(android.app.Activity a) {
        try {
            android.content.Intent i = a.getPackageManager().getLaunchIntentForPackage(a.getPackageName());
            i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
            android.app.PendingIntent pi = android.app.PendingIntent.getActivity(a, 7301, i, android.app.PendingIntent.FLAG_IMMUTABLE | android.app.PendingIntent.FLAG_CANCEL_CURRENT);
            android.app.AlarmManager am = (android.app.AlarmManager) a.getSystemService(Context.ALARM_SERVICE);
            am.set(android.app.AlarmManager.RTC, System.currentTimeMillis() + 3500, pi);   // v3.03：1.2 秒太快，旧进程还没退干净新进程就读配置，两次启动闪退都在读 ini 时（0x1a322f8，推断）
        } catch (Throwable t) { LogCap.note("重启：定时打开失败 " + t); }
        a.finishAffinity();
        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(0);
    }
}
