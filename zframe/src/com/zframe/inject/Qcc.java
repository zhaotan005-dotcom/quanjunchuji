package com.zframe.inject;

import android.content.Context;
import android.os.Build;

import java.io.*;
import java.util.regex.*;

/**
 * v1.24：默认还原成原始档位（提到高档后大厅人物变灰、超高帧率也没解开），设置里可以手动打开。
 * v1.23 画质/帧率解锁：游戏用腾讯 APM 的机型配置（assets/QCC_APM_PUBGM，明文 JSON）给手机定档次，
 * 新机型不在表里会被判成 2 档，设置里就没有高帧率和高画质。
 * 这里把本机型号加进最高档（6）的 filter-model，改游戏私有目录里缓存的那份（apm_qcc*）。
 * 游戏包里的 assets 那份由 ZFrame App 的「修补游戏」改。
 */
public class Qcc {
    public static volatile String status = "未处理";

    /** 在 JSON 文本里把 model 加进 filter-model 的 "6"，返回新文本；已经有了或格式不对返回 null */
    public static String addModel(String js, String model) {
        int fm = js.indexOf("\"filter-model\"");
        if (fm < 0) return null;
        Matcher m = Pattern.compile("\"6\"\\s*:\\s*\\[").matcher(js);
        if (!m.find(fm)) return null;
        int end = js.indexOf(']', m.end());
        if (end < 0) return null;
        if (js.substring(m.end(), end).contains("\"" + model + "\"")) return null;
        boolean empty = js.substring(m.end(), end).trim().isEmpty();
        return js.substring(0, m.end()) + "\"" + model + "\"" + (empty ? "" : ", ") + js.substring(m.end());
    }

    /** 把 model 从 filter-model 的 "6" 里拿掉，返回新文本；本来就没有返回 null */
    public static String removeModel(String js, String model) {
        String q = "\"" + model + "\"";
        int fm = js.indexOf("\"filter-model\"");
        if (fm < 0) return null;
        Matcher m = Pattern.compile("\"6\"\\s*:\\s*\\[").matcher(js);
        if (!m.find(fm)) return null;
        int end = js.indexOf(']', m.end());
        String in = js.substring(m.end(), end);
        if (!in.contains(q)) return null;
        String out = in.replace(q + ", ", "").replace(", " + q, "").replace(q, "");
        return js.substring(0, m.end()) + out + js.substring(end);
    }

    public static volatile boolean up;

    public static void fix(Context ctx) {
        try {
            String model = Build.MODEL;
            up = true; // v1.27：机型解锁固定开
            int n = 0, seen = 0;
            File data = ctx.getFilesDir().getParentFile();
            for (File root : new File[]{data, ctx.getExternalFilesDir(null)}) {
                if (root == null) continue;
                int[] r = walk(root, model, 0);
                n += r[0]; seen += r[1];
            }
            status = (up ? "已提高到高档机型" : "原始机型档位") + "（配置 " + seen + " 份，本次改 " + n + " 份，重启游戏生效）";
            LogCap.note("画质解锁: " + status);
        } catch (Throwable t) { status = "失败 " + t; }
    }

    static int[] walk(File dir, String model, int depth) {
        int[] r = {0, 0};
        File[] fs = dir.listFiles();
        if (fs == null || depth > 4) return r;
        for (File f : fs) {
            String nm = f.getName();
            if (f.isDirectory()) {
                if (nm.equals("lib") || nm.equals("code_cache") || nm.equals("cache") || nm.startsWith("Paks") || nm.equals("UE4Game")) continue;
                int[] s = walk(f, model, depth + 1); r[0] += s[0]; r[1] += s[1];
            } else if (nm.startsWith("apm_qcc") && f.length() < 512 * 1024) {
                try {
                    String js = read(f);
                    if (!js.contains("classLevelNum")) continue;
                    r[1]++;
                    String out = up ? addModel(js, model) : removeModel(js, model);
                    if (out != null) {
                        try (OutputStream o = new FileOutputStream(f)) { o.write(out.getBytes("UTF-8")); }
                        r[0]++;
                        LogCap.note("画质解锁: 已改 " + f.getAbsolutePath());
                    }
                } catch (Throwable ignored) {}
            }
        }
        return r;
    }

    static String read(File f) throws IOException {
        try (InputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] b = new byte[8192]; int n;
            while ((n = in.read(b)) > 0) bo.write(b, 0, n);
            return bo.toString("UTF-8");
        }
    }
}
