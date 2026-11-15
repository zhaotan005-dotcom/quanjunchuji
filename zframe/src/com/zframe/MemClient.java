package com.zframe;

import java.io.*;

/** App 端：启动 root 修改器内核并收发命令。所有方法需在后台线程调用。 */
public class MemClient {
    private static MemClient inst;
    private Process proc;
    private BufferedWriter w;
    private BufferedReader r;

    public static synchronized MemClient get(String apkPath) throws IOException {
        if (inst == null || !inst.alive()) {
            inst = new MemClient();
            inst.start(apkPath);
        }
        return inst;
    }

    private boolean alive() {
        try { proc.exitValue(); return false; } catch (IllegalThreadStateException e) { return true; }
    }

    private void start(String apk) throws IOException {
        proc = Runtime.getRuntime().exec("su");
        w = new BufferedWriter(new OutputStreamWriter(proc.getOutputStream(), "UTF-8"));
        r = new BufferedReader(new InputStreamReader(proc.getInputStream(), "UTF-8"));
        w.write("export CLASSPATH=" + Root.q(apk) + "\n");
        w.write("exec app_process /system/bin com.zframe.MemServer 2>/dev/null\n");
        w.flush();
        String hello = readReply();
        if (!hello.contains("READY")) throw new IOException("修改器内核启动失败（没有 root？）" + hello);
    }

    private String readReply() throws IOException {
        StringBuilder sb = new StringBuilder();
        String l;
        while ((l = r.readLine()) != null) {
            if (l.equals("<<END>>")) return sb.toString();
            sb.append(l).append('\n');
        }
        throw new IOException("修改器内核已退出");
    }

    public synchronized String cmd(String c) throws IOException {
        w.write(c + "\n");
        w.flush();
        return readReply();
    }
}
