package com.zframe.inject;

/** v2.13：分三个版本打包（build 时改 BUILD）。0 = 单机版（大厅自建服务端、仓库衣服、人机）；
 *  1 = 联机服务端版（只有开房，不开自建服务端、不发衣服）；2 = 联机客户端版（只有加入，同样不开服务端）。
 *  v2.44 用户：客户端版在加载页让人选「单机 / 联机」。选单机就把 ED 改成 0，后面完全按单机版走。
 *  ED 不能再是常量（javac 会把常量内联），改成运行时变量；启动早装要等选完再装。 */
final class Ed {
    static final int BUILD = 1;
    // v3.13 用户：去掉单机/联机选择，客户端包也默认开自建服务端（ED 0：大厅自建服务端 + 仓库衣服），大厅里照样能加入别人
    static volatile int ED = BUILD == 2 ? 0 : BUILD;
    static volatile boolean chosen = true;
    static boolean single() { return ED == 0; }
    /** v4.08 用户规矩：单机/客户端不上传任何日志、不收集只为上传用的数据（心跳/步骤文件/闪退自动存），只有服务端（ED 1）上传。 */
    static boolean uploads() { return BUILD == 1 && ED == 1; }
    static boolean canChoose() { return false; }
    static String name() { return ED == 1 ? "服务端" : ED == 2 ? "客户端" : BUILD == 2 ? "客户端" : "单机版"; }
    static java.io.File file;
    static synchronized void init(android.content.Context c) { if (file == null && c != null) file = new java.io.File(c.getFilesDir(), "zf_mode.txt"); }
    static int saved() {
        if (BUILD == 2) return 0;   // v3.13 自建服务端；v3.10 用户：删了单机/联机选择，客户端包只走联机（以前存过单机的也改回联机）
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
