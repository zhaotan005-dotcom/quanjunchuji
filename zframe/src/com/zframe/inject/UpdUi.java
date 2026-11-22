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
                // v4.06：不再全量扫名字表（64×16384 次读 /proc/self/mem；页面早就找到了，结果没人用；同类扫描 v3.97 已从服务端拿掉）
                state = "游戏更新页名字表：v4.06 起不扫";
                Lan.autoUp("upd");
            } catch (Throwable t) { state = "游戏更新页：查名字出错 " + t; LogCap.note(state); }
        }, "zf-updui").start();
    }
}
