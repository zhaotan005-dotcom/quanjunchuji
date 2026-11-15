package com.zframe;

import java.io.*;

/** 一次性 root 命令执行。兼容 Magisk / KernelSU / APatch / 其他 su。 */
public class Root {
    public static String run(String cmd) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "(" + cmd + ") 2>&1"});
            String out = read(p.getInputStream());
            p.waitFor();
            return out;
        } catch (Exception e) {
            return "ERR: " + e.getMessage();
        }
    }

    static String read(InputStream in) throws IOException {
        BufferedReader r = new BufferedReader(new InputStreamReader(in));
        StringBuilder sb = new StringBuilder();
        String l;
        while ((l = r.readLine()) != null) sb.append(l).append('\n');
        return sb.toString();
    }

    public static boolean hasRoot() {
        return run("id").contains("uid=0");
    }

    /** 返回 magisk / ksu / apatch / su */
    public static String backend() {
        String r = run("if command -v magisk >/dev/null; then echo magisk; "
                + "elif command -v ksud >/dev/null || [ -d /data/adb/ksu ]; then echo ksu; "
                + "elif command -v apd >/dev/null || [ -d /data/adb/ap ]; then echo apatch; "
                + "else echo su; fi").trim();
        return r.isEmpty() ? "none" : r;
    }

    public static String backendVersion(String b) {
        switch (b) {
            case "magisk": return run("magisk -v; magisk -V").trim().replace('\n', ' ');
            case "ksu": return run("ksud -V 2>/dev/null || /data/adb/ksud -V").trim();
            case "apatch": return run("apd -V 2>/dev/null || /data/adb/apd -V").trim();
            default: return run("su -v").trim();
        }
    }

    public static String q(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }
}
