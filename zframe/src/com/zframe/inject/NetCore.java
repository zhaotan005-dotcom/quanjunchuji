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
            ts[i] = new Thread(() -> { try { v[i] = Integer.parseInt(new String(get(BASES[i] + name + ".ver", 64)).trim()); } catch (Throwable t) { synchronized (err) { err.append(BASES[i].substring(8, Math.min(28, BASES[i].length()))).append("：").append(t.getMessage()).append("；"); } } }, "zf-ver" + i);
            ts[i].setDaemon(true); ts[i].start();
        }
        long end = System.currentTimeMillis() + 12000;
        for (Thread t : ts) { long w = end - System.currentTimeMillis(); if (w > 0) try { t.join(w); } catch (InterruptedException ignored) {} }
        return v.clone();
    }
    static volatile String status = "";
    static volatile int netVer;

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
            // v2.71 用户：不要自动退回老版本
            if (main) write(tries, String.valueOf(t + 1));
            File run = new File(ctx.getCodeCacheDir(), "zcore_netrun.dex");
            run.setWritable(true); run.delete();
            try (InputStream in = new FileInputStream(dex); OutputStream o = new FileOutputStream(run)) { copy(in, o); }
            run.setReadOnly();
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

    static volatile boolean started;
    static volatile int readyVer;   // 已下好、等重启的版本
    interface L { void on(String msg, boolean ready); }
    static volatile L listener;
    static volatile boolean done;
    static void say(String m, boolean ready) { status = m; if (ready || m.startsWith("已是最新") || m.startsWith("检查更新失败")) done = true; GameOverlay.refreshAny(); LogCap.note("在线更新：" + m); L l = listener; if (l != null) try { l.on(m, ready); } catch (Throwable ignored) {} }

    /** 进大厅后调：清掉闪退计数；本内核是刚换上的就提示「已更新」；再查一次新版本（选单机/联机时查过就不重复） */
    static void lobby(final Context ctx) {
        if (ctx == null) return;
        f(ctx, "zcore_net.try").delete();
        File j = f(ctx, "zcore_net.just");
        if (j.exists()) { j.delete(); GameOverlay.toastAny("已更新到 " + GameOverlay.VER + "（内核 " + ZInit.CORE_VER + "）"); }
        check(ctx);
    }

    /** 后台查 GitHub 上的版本，比本地新就下载（下次启动生效） */
    static void check(final Context ctx) {
        if (ctx == null || started) return; started = true;
        say("正在检查更新…", false);
        new Thread(() -> {   // v2.68：总共 45 秒没结果就报失败（域名解析卡住时 HttpURLConnection 的超时不起作用）
            try { Thread.sleep(45000); } catch (InterruptedException ignored) {}
            if (!done && readyVer <= ZInit.CORE_VER && !status.startsWith("发现新版本")) say("检查更新失败（网络超时）", false);
        }, "zframe-netcore-wd").start();
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
                    if (v <= have) { readyVer = have; say("新版本已下好，重启游戏生效", true); return; }
                    say("发现新版本，正在下载…", false);
                    byte[] d = get(base + name + ".dex", 8 << 20);
                    if (d.length < 1000 || d[0] != 'd' || d[1] != 'e' || d[2] != 'x') throw new IOException("不是 dex");
                    File tmp = f(ctx, "zcore_net.tmp"), dex = f(ctx, "zcore_net.dex");
                    try (OutputStream o = new FileOutputStream(tmp)) { o.write(d); }
                    dex.setWritable(true); dex.delete();
                    if (!tmp.renameTo(dex)) throw new IOException("改名失败");
                    write(f(ctx, "zcore_net.ver"), v + " " + Ed.BUILD);
                    f(ctx, "zcore_net.try").delete();
                    readyVer = v;
                    say("新版本已下好，重启游戏生效", true);
                    GameOverlay.toastAny("新版本已下好，重启游戏生效（菜单里有「重启更新」）");
                    return;
                } catch (Throwable t) { err += base.substring(8, 20) + "：" + t.getMessage() + "；"; }
            }
            say("检查更新失败（网络）", false);
            LogCap.note("在线更新失败：" + err);
        }, "zframe-netcore").start();
    }

    /** 重启游戏：1 秒后用闹钟重新打开，自己退出 */
    static void restart(android.app.Activity a) {
        try {
            android.content.Intent i = a.getPackageManager().getLaunchIntentForPackage(a.getPackageName());
            i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
            android.app.PendingIntent pi = android.app.PendingIntent.getActivity(a, 7301, i, android.app.PendingIntent.FLAG_IMMUTABLE | android.app.PendingIntent.FLAG_CANCEL_CURRENT);
            android.app.AlarmManager am = (android.app.AlarmManager) a.getSystemService(Context.ALARM_SERVICE);
            am.set(android.app.AlarmManager.RTC, System.currentTimeMillis() + 1200, pi);
        } catch (Throwable t) { LogCap.note("重启：定时打开失败 " + t); }
        a.finishAffinity();
        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(0);
    }
}
