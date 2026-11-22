package com.zframe.inject;

import android.app.Activity;
import android.content.SharedPreferences;

/** v3.58 用户：菜单里加改画质的细项（关阴影等），可以选，立刻生效。
 *  每项一组控制台命令（Con.exec），选了马上发；选择存 prefs，每次进图（LogLoad: Took 后 2.5 秒）再发一遍，
 *  因为游戏 PreLoadMap 会按自己的画质档重设。-1 = 跟游戏设置（不发）。未实测。 */
final class GfxOpt {
    static final String[] KEY = {"shadow", "aa", "post", "fx", "grass", "view", "tex", "res"};
    static final String[] NAME = {"阴影", "抗锯齿", "泛光/后期", "特效", "草和植被", "视距", "贴图", "清晰度"};
    static final String[][] LBL = {
        {"关", "低", "中", "高"}, {"关", "开"}, {"关", "开"}, {"低", "高"}, {"关", "少", "满"}, {"近", "中", "远"}, {"低", "高"}, {"70%", "85%", "100%"}};
    static final String[][][] CMD = {
        {{"sg.ShadowQuality 0", "r.ShadowQuality 0"}, {"sg.ShadowQuality 1", "r.ShadowQuality 1"}, {"sg.ShadowQuality 2", "r.ShadowQuality 3"}, {"sg.ShadowQuality 3", "r.ShadowQuality 5"}},
        {{"sg.AntiAliasingQuality 0", "r.MobileMSAA 1"}, {"sg.AntiAliasingQuality 3", "r.MobileMSAA 4"}},
        {{"sg.PostProcessQuality 0", "r.BloomQuality 0"}, {"sg.PostProcessQuality 3", "r.BloomQuality 4"}},
        {{"sg.EffectsQuality 0"}, {"sg.EffectsQuality 3"}},
        {{"foliage.DensityScale 0", "grass.DensityScale 0"}, {"foliage.DensityScale 0.5", "grass.DensityScale 0.5"}, {"foliage.DensityScale 1", "grass.DensityScale 1"}},
        {{"sg.ViewDistanceQuality 0"}, {"sg.ViewDistanceQuality 1"}, {"sg.ViewDistanceQuality 3"}},
        {{"sg.TextureQuality 0"}, {"sg.TextureQuality 3"}},
        {{"r.ScreenPercentage 70"}, {"r.ScreenPercentage 85"}, {"r.ScreenPercentage 100"}}};
    static final int[] val = new int[KEY.length];
    static volatile boolean loaded;
    static volatile String state = "";

    static SharedPreferences sp() { Activity a = Con.act; return a == null ? null : a.getSharedPreferences("zframe", 0); }
    static void load() {
        if (loaded) return;
        SharedPreferences p = sp(); if (p == null) { for (int i = 0; i < KEY.length; i++) val[i] = -1; return; }
        for (int i = 0; i < KEY.length; i++) val[i] = p.getInt("gfx_" + KEY[i], -1);
        loaded = true;
    }
    static int get(int i) { load(); return val[i]; }
    /** 选一项：-1 = 跟游戏设置 */
    static void set(int i, int v) {
        load();
        val[i] = v;
        SharedPreferences p = sp(); if (p != null) p.edit().putInt("gfx_" + KEY[i], v).apply();
        if (v >= 0) {
            int n = 0; for (String c : CMD[i][v]) if (Con.exec(c)) n++;
            state = NAME[i] + "：" + LBL[i][v] + "（发了 " + n + " 条）";
        } else state = NAME[i] + "：跟游戏设置（下次进图恢复）";
        LogCap.note("画面细项 " + state);
    }
    static void applyAll() {
        load();
        int n = 0;
        for (int i = 0; i < KEY.length; i++) if (val[i] >= 0) for (String c : CMD[i][val[i]]) if (Con.exec(c)) n++;
        if (n > 0) { state = "进图后已重发 " + n + " 条画面设置"; LogCap.note(state); }
    }
    static void afterLoad() {
        if (Ed.ED == 1) return;   // v4.05 服务端压内存，不发画面细项
        if (Lan.role == 1 && Gfx.liteOn) return;   // 服务端减负时不动
        new Thread(() -> { try { Thread.sleep(2500); } catch (InterruptedException e) { return; } applyAll(); }, "zf-gfxopt").start();
    }
}
