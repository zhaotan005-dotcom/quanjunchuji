package com.zframe.inject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

/** v2.54 用户：「自动返回日志」。导出的诊断/闪退日志 gzip 后传到一个私有收件箱（ntfy），我那边直接取，不用用户再发文件。
 *  只传 ZFrame 自己导出的那份文本（游戏日志里筛过的行 + 框架状态），不传别的。失败就算了，本地那份照样在 下载/ZFrame。 */
final class LogUp {
    static final String TOPIC = "https://ntfy.sh/zf-qj-k7r2p9m4x1";
    static volatile String state = "日志上传：还没传过";
    static void send(final String name, final String text) {
        new Thread(() -> {
            try {
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                try (java.util.zip.GZIPOutputStream g = new java.util.zip.GZIPOutputStream(bo)) { g.write(text.getBytes("UTF-8")); }
                byte[] d = bo.toByteArray();
                HttpURLConnection c = (HttpURLConnection) new URL(TOPIC).openConnection();
                c.setConnectTimeout(8000); c.setReadTimeout(20000);
                c.setRequestMethod("PUT"); c.setDoOutput(true);
                c.setRequestProperty("Filename", name + ".gz");
                c.setRequestProperty("Title", GameOverlay.VER + " " + Ed.name());
                c.setFixedLengthStreamingMode(d.length);
                try (OutputStream o = c.getOutputStream()) { o.write(d); }
                int code = c.getResponseCode(); c.disconnect();
                state = code == 200 ? "日志上传：已传 " + name : "日志上传：失败 HTTP " + code;
            } catch (Throwable t) { state = "日志上传：失败 " + t.getMessage(); }
            LogCap.note(state);
        }, "zf-logup").start();
    }
}
