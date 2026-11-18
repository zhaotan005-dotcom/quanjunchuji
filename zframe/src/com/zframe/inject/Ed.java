package com.zframe.inject;

/** v2.13：分三个版本打包（build 时改 BUILD）。0 = 单机版（大厅自建服务端、仓库衣服、人机）；
 *  1 = 联机服务端版（只有开房，不开自建服务端、不发衣服）；2 = 联机客户端版（只有加入，同样不开服务端）。
 *  v2.44 用户：客户端版在加载页让人选「单机 / 联机」。选单机就把 ED 改成 0，后面完全按单机版走。
 *  ED 不能再是常量（javac 会把常量内联），改成运行时变量；启动早装要等选完再装。 */
final class Ed {
    static final int BUILD = 1;
    static volatile int ED = BUILD;
    static volatile boolean chosen = BUILD != 2;
    static boolean single() { return ED == 0; }
    static boolean canChoose() { return BUILD == 2; }
    static String name() { return ED == 1 ? "服务端" : ED == 2 ? "客户端" : BUILD == 2 ? "单机（客户端包）" : "单机版"; }
    static java.io.File file;
    static synchronized void init(android.content.Context c) { if (file == null && c != null) file = new java.io.File(c.getFilesDir(), "zf_mode.txt"); }
    static int saved() {
        try { if (file != null && file.exists()) return "0".equals(new String(java.nio.file.Files.readAllBytes(file.toPath())).trim()) ? 0 : 2; } catch (Throwable ignored) {}
        return 2;
    }
    static synchronized void choose(int e, String how) {
        if (chosen) return;
        ED = e; chosen = true;
        try { if (file != null) java.nio.file.Files.write(file.toPath(), String.valueOf(e).getBytes()); } catch (Throwable ignored) {}
        LogCap.note("启动选择：" + (e == 0 ? "单机" : "联机客户端") + "（" + how + "）");
        Ed.class.notifyAll();
        GameOverlay.chosen();
    }
    static void save(int e) { try { if (file != null) java.nio.file.Files.write(file.toPath(), String.valueOf(e).getBytes()); } catch (Throwable ignored) {} }
    /** 启动早装前调用：客户端包等人选，最多等 limitMs，超时就用上次的选择 */
    static void await(long limitMs) {
        if (chosen) return;
        long end = System.currentTimeMillis() + limitMs;
        synchronized (Ed.class) {
            while (!chosen) {
                long left = end - System.currentTimeMillis();
                if (left <= 0) break;
                try { Ed.class.wait(left); } catch (InterruptedException ex) { break; }
            }
        }
        if (!chosen) choose(saved(), "超时，用上次的");
    }
}
