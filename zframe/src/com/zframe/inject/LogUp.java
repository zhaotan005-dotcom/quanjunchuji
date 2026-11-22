package com.zframe.inject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

/** v2.54 用户：「自动返回日志」。导出的诊断/闪退日志 gzip 后传到一个私有收件箱（ntfy），我那边直接取，不用用户再发文件。
 *  只传 ZFrame 自己导出的那份文本（游戏日志里筛过的行 + 框架状态），不传别的。v2.61 不再存 下载/ZFrame；传失败的放 游戏私有目录/zf_logq，下次补传。 */
final class LogUp {
    static final String TOPIC = "https://ntfy.sh/zf-qj-k7r2p9m4x1";
    static volatile String state = "日志上传：还没传过";
    static volatile File dir;
    static void send(final String name, final String text) {
        new Thread(() -> {
            if (put(name, text)) {   // 成功了顺手补传以前没传出去的
                File d = dir; File[] q = d == null ? null : d.listFiles();
                if (q != null) for (File f : q) try { if (put(f.getName(), new String(java.nio.file.Files.readAllBytes(f.toPath()), "UTF-8"))) f.delete(); } catch (Throwable ignored) {}
            } else {
                File d = dir; if (d != null) try { d.mkdirs(); java.nio.file.Files.write(new File(d, name).toPath(), text.getBytes("UTF-8")); } catch (Throwable ignored) {}
            }
        }, "zf-logup").start();
    }
    static boolean put(final String name, final String text) {
            boolean ok = false;
            try {
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                try (java.util.zip.GZIPOutputStream g = new java.util.zip.GZIPOutputStream(bo)) { g.write(text.getBytes("UTF-8")); }
                byte[] d = bo.toByteArray();
                HttpURLConnection c = (HttpURLConnection) new URL(TOPIC).openConnection();
                c.setConnectTimeout(8000); c.setReadTimeout(20000);
                c.setRequestMethod("PUT"); c.setDoOutput(true);
                c.setRequestProperty("Filename", name + ".gz");
                c.setRequestProperty("Title", GameOverlay.VER + " " + Ed.name() + (name.startsWith("boot-") ? " " + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL : ""));
                c.setFixedLengthStreamingMode(d.length);
                try (OutputStream o = c.getOutputStream()) { o.write(d); }
                int code = c.getResponseCode(); c.disconnect();
                ok = code == 200;
                state = ok ? "日志上传：已传 " + name : "日志上传：失败 HTTP " + code;
            } catch (Throwable t) { state = "日志上传：失败 " + t.getMessage(); }
            LogCap.note(state);
            return ok;
    }
}
