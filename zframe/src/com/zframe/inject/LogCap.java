package com.zframe.inject;

/** v1.51：不再抓日志、不再往 下载/ZFrame 导出文件。只留一行系统日志，方便以后需要时用 adb 看。
 *  v4.06：另留最近 120 条在内存里（开机卡住时 BootLog 上传用，不碰游戏日志文件）。 */
public class LogCap {
    static volatile String lastNote;
    static final java.util.ArrayDeque<String> ring = new java.util.ArrayDeque<>();
    static void note(String s) {
        lastNote = s; android.util.Log.i("ZFrame", s);
        String t = new java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US).format(new java.util.Date());
        synchronized (ring) { ring.addLast(t + " " + (s.length() > 300 ? s.substring(0, 300) : s)); while (ring.size() > 120) ring.removeFirst(); }
    }
    static String recent() { StringBuilder sb = new StringBuilder(); synchronized (ring) { for (String s : ring) sb.append("  ").append(s).append('\n'); } return sb.toString(); }
}
