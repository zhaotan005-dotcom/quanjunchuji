package com.zframe.inject;

/** v1.51：不再抓日志、不再往 下载/ZFrame 导出文件。只留一行系统日志，方便以后需要时用 adb 看。 */
public class LogCap {
    static volatile String lastNote;
    static void note(String s) { lastNote = s; android.util.Log.i("ZFrame", s); }
}
