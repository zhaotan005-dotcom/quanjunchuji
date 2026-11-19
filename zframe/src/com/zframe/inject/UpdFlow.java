package com.zframe.inject;

/** v3.24 用户：要游戏自己的更新进度条（「大厅自建服务端都可以，这个肯定也可以」）。
 *  RE：启动流程 UNZStartupFlowCheck 先发 HTTP 问腾讯服务器（已停，约 12 秒后失败），回调 0x1210ef8 里
 *  配置标记为 0 时走 FlowManager：0x12110d4 `strb r1(=0),[r4,#0x348]` = 「FlowManager Disable Update」把更新步骤关掉，
 *  所以 UNZStartupFlowUpdate::Begin（0x121244c，CreateWidget '/Game/Startup/UI/UpdateUI.UpdateUI_C'，存 +0x34）从来不跑，更新页建不出来。
 *  试验：启动早装把这条 strb 改成 nop，不让它关更新步骤。
 *  保险：装之前写 zf_updflow.try，进大厅删掉；上次装了却没进大厅（卡在更新页），这次自动不装。未实测。 */
final class UpdFlow {
    static final long GATE = 0x12110d4L;
    static final String GATE_O = "4813c4e5", GATE_N = "00f020e3";
    static volatile boolean on;
    static volatile String state = "游戏更新页：没装";
    static java.io.File tryFile;

    static void install() {
        try {
            android.content.Context c = Con.act != null ? Con.act : null;
            java.io.File dir = c != null ? c.getFilesDir() : (Ed.file != null ? Ed.file.getParentFile() : null);
            if (dir == null) { state = "游戏更新页：没目录，不装"; return; }
            tryFile = new java.io.File(dir, "zf_updflow.try");
            // v3.37 用户：更新页还是自制的 → 去掉「上次没进大厅这次不开」保险（中途退出就会触发），每次都走游戏自己的更新页
            if (tryFile.exists()) tryFile.delete();
            String r = Mods.write(GATE, GATE_O, GATE_N, true);
            if ("ok".equals(r)) { on = true; try { tryFile.createNewFile(); } catch (Throwable ignored) {} state = "游戏更新页：已开（不让启动流程关更新步骤）"; }
            else state = "游戏更新页：没装上 " + r;
            LogCap.note(state);
        } catch (Throwable t) { state = "游戏更新页：出错 " + t; }
    }
    /** 进大厅：说明没卡住，删掉保险文件 */
    static void lobby() { java.io.File f = tryFile; if (f != null) f.delete(); }

    /** 日志里看到更新流程开始 / 页面类没找到 */
    static volatile boolean pageSeen;
    static void line(String l) {
        if (l.contains("Begin Flow") && l.contains("Update")) { pageSeen = true; LogCap.note("游戏更新页：更新流程开始了"); GameOverlay.gamePage(); }
        else if (l.contains("UpdateUIClass is null")) { state = "游戏更新页：这个包里没有更新页蓝图"; LogCap.note(state); }
    }
}
