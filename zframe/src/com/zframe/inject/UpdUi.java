package com.zframe.inject;

/** v3.14 用户：在线更新用游戏自己的更新页（进度条）来显示。
 *  libUE4 里这页的成员：UpdatePanel / UpdateProgressBar / UpdateProcessValue / UpdateProcessText / UpdateTip / UpdateBtn，
 *  函数 OnUpdateProcess / OnUpdateBtnClick / OnAbortUpdateConfirm（字符串 0x4811cf6 一带）；类名和蓝图路径不在 libUE4 字符串里。
 *  第一步：大厅时扫名字表，把带 Update 的类/蓝图名字记进日志（upd-*），找到这页的蓝图路径后再接进度。 */
final class UpdUi {
    static volatile boolean probed;
    static volatile String state = "";
    static void probe() {
        if (probed) return; probed = true;
        new Thread(() -> {
            try {
                Thread.sleep(6000);
                java.util.ArrayList<String> hits = new java.util.ArrayList<>();
                ModeProbe.find("updatepanel|updateprogress|updateprocess|update_ui|ui_update|updateui|updatewidget|updateview|dolphin|hotupdate|versionupdate|update_bp|bp_update|wbp_update|updatetip", hits);
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < hits.size() && i < 120; i++) sb.append(i == 0 ? "" : " | ").append(hits.get(i));
                state = "游戏更新页名字表命中 " + hits.size() + "：" + (sb.length() == 0 ? "无" : sb);
                LogCap.note(state);
                Lan.autoUp("upd");
            } catch (Throwable t) { state = "游戏更新页：查名字出错 " + t; LogCap.note(state); }
        }, "zf-updui").start();
    }
}
