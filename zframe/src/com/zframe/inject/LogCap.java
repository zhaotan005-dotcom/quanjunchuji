package com.zframe.inject;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.provider.MediaStore;

import java.io.*;
import java.util.*;

/**
 * 抓游戏日志：logcat(本进程) 先写到私有文件，每 10 秒整份同步到 下载/ZFrame/（每次写完都关闭，文件大小才会刷新）。
 * 同时把游戏自己写的 UE4 日志（*.log）一起同步过去。出错信息也写进日志开头。
 */
public class LogCap {
    static final StringBuffer head = new StringBuffer();
    static final Map<String, Uri> outs = new HashMap<>();

    static void note(String s) { head.append(s).append('\n'); }

    static String stamp; static File lcFile; static long lastSync;

    public static String status() {
        long n = lcFile != null && lcFile.exists() ? lcFile.length() : 0;
        return "已抓取 " + (n / 1024) + " KB · 自动每10秒保存" + (lastSync > 0 ? " · 上次 " + (System.currentTimeMillis() - lastSync) / 1000 + " 秒前" : "");
    }

    public static synchronized void syncNow(Context ctx) {
        if (stamp == null) return;
        try {
            syncFile(ctx, stamp + "-logcat.log", lcFile, true);
            String[] tags = {"app", "int", "sd"};
            File[] roots = {ctx.getExternalFilesDir(null), ctx.getFilesDir(), new File("/storage/emulated/0/UE4Game")};
            for (int k = 0; k < roots.length; k++) {
                List<File> ue = new ArrayList<>();
                findLogs(roots[k], ue, 0);
                for (File f : ue) {
                    // 只要最近2小时写过的，避免旧日志冒充新日志
                    if (System.currentTimeMillis() - f.lastModified() > 2L * 3600 * 1000) continue;
                    syncFile(ctx, stamp + "-" + tags[k] + "-" + f.getName(), f, false);
                }
            }
            lastSync = System.currentTimeMillis();
        } catch (Throwable e) {
            note("同步失败: " + e);
        }
    }

    static long startAt;

    static void listRecent(File d, int depth, StringBuilder sb, long since) {
        if (d == null || depth > 6) return;
        File[] fs = d.listFiles(); if (fs == null) return;
        for (File f : fs) {
            if (f.isDirectory()) { if (!f.getName().equals("ProgramBinaryCache")) listRecent(f, depth + 1, sb, since); }
            else if (f.lastModified() > since) sb.append("  ").append(f.getPath()).append(' ').append(f.length()).append(' ')
                    .append(new java.text.SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date(f.lastModified()))).append('\n');
        }
    }

    public static void start(final Context ctx) {
        startAt = System.currentTimeMillis();
        stamp = new java.text.SimpleDateFormat("MMdd-HHmmss", Locale.US).format(new Date());
        final File dir = new File(ctx.getCacheDir(), "zlog");
        dir.mkdirs();
        final File lc = new File(dir, "logcat.txt"); lcFile = lc;
        note("ZFrame 日志 内核" + ZInit.CORE_VER + " pkg=" + ctx.getPackageName() + " pid=" + android.os.Process.myPid()
                + " sdk=" + android.os.Build.VERSION.SDK_INT + " " + android.os.Build.MODEL);
        try {
            StringBuilder sb = new StringBuilder("最近2小时改过的游戏文件:\n");
            long since = System.currentTimeMillis() - 2L * 3600 * 1000;
            listRecent(ctx.getFilesDir().getParentFile(), 0, sb, since);
            listRecent(ctx.getExternalFilesDir(null), 0, sb, since);
            note(sb.toString());
        } catch (Throwable ignored) {}
        Thread cap = new Thread(new Runnable() { public void run() {
            Process p = null;
            try (OutputStream o = new FileOutputStream(lc)) {
                p = Runtime.getRuntime().exec(new String[]{"logcat", "-v", "threadtime", "--pid=" + android.os.Process.myPid()});
                final Process fp = p;
                new Thread(new Runnable() { public void run() {
                    try { byte[] e = readSome(fp.getErrorStream()); if (e.length > 0) note("logcat stderr: " + new String(e)); } catch (Throwable ignored) {}
                }}).start();
                InputStream in = p.getInputStream();
                byte[] b = new byte[1 << 15]; int r; long total = 0;
                while ((r = in.read(b)) > 0) {
                    o.write(b, 0, r); total += r;
                    if (total > 200L * 1024 * 1024) break;
                }
                note("logcat 结束 共" + total + "字节 exit=" + safeExit(p));
            } catch (Throwable e) {
                note("logcat 失败: " + e);
            } finally { if (p != null) p.destroy(); }
        }}, "zframe-logcat");
        cap.setDaemon(true);
        cap.start();
        Thread sync = new Thread(new Runnable() { public void run() {
            while (true) {
                syncNow(ctx);
                long up = System.currentTimeMillis() - startAt;
                try { Thread.sleep(up < 40000 ? 1000 : 10000); } catch (InterruptedException e) { return; }
            }
        }}, "zframe-logsync");
        sync.setDaemon(true);
        sync.start();
    }

    static String safeExit(Process p) { try { return String.valueOf(p.exitValue()); } catch (Throwable t) { return "?"; } }

    static byte[] readSome(InputStream in) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] b = new byte[4096]; int r;
        while ((r = in.read(b)) > 0 && bo.size() < 65536) bo.write(b, 0, r);
        return bo.toByteArray();
    }

    static void findLogs(File d, List<File> out, int depth) {
        if (d == null || depth > 8) return;
        File[] fs = d.listFiles();
        if (fs == null) return;
        for (File f : fs) {
            if (f.isDirectory()) findLogs(f, out, depth + 1);
            else if (f.getName().endsWith(".log") && f.length() > 0) out.add(f);
        }
    }

    static void syncFile(Context ctx, String name, File src, boolean withHead) throws IOException {
        Uri u = outs.get(name);
        if (u == null) {
            ContentValues v = new ContentValues();
            v.put(MediaStore.MediaColumns.DISPLAY_NAME, ctx.getPackageName() + "-" + name);
            v.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
            v.put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/ZFrame");
            u = ctx.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (u == null) throw new IOException("无法在 下载/ZFrame 建文件");
            outs.put(name, u);
        }
        try (OutputStream o = ctx.getContentResolver().openOutputStream(u, "wt")) {
            if (withHead) o.write(head.toString().getBytes("UTF-8"));
            if (src.exists()) try (FileInputStream in = new FileInputStream(src)) {
                long skip = src.length() - 30L * 1024 * 1024;
                if (skip > 0) in.skip(skip);
                byte[] b = new byte[1 << 16]; int r;
                while ((r = in.read(b)) > 0) o.write(b, 0, r);
            }
        }
    }
}
