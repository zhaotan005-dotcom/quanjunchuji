package com.zframe.inject;

/** v2.04 游戏设置里的画质选了立刻生效。
 *  游戏自己只在进地图时按设置应用画质（UNZSystemSettingData::ApplyRenderQuality 0xca14e0，PreLoadMap 里调），设置界面只提示「下局生效」。
 *  这里每 3 秒 getall NZSystemSettingData RenderQualityOption，选项变了就按档位发 sg./r. 命令。第一次读到的值不发（进图时游戏已经应用了）。 */
final class Gfx {
    static volatile String opt, state = "等游戏启动";
    static volatile boolean started;
    static final String[][] LV = {
        // Low
        {"sg.ShadowQuality 0", "sg.PostProcessQuality 0", "sg.EffectsQuality 0", "sg.FoliageQuality 0", "sg.TextureQuality 1",
         "sg.AntiAliasingQuality 0", "sg.ViewDistanceQuality 1", "r.ShadowQuality 0", "r.BloomQuality 0", "r.MobileMSAA 1", "r.DetailMode 0"},
        // Middle
        {"sg.ShadowQuality 1", "sg.PostProcessQuality 1", "sg.EffectsQuality 1", "sg.FoliageQuality 1", "sg.TextureQuality 2",
         "sg.AntiAliasingQuality 1", "sg.ViewDistanceQuality 2", "r.ShadowQuality 2", "r.BloomQuality 2", "r.MobileMSAA 1", "r.DetailMode 1"},
        // High
        {"sg.ShadowQuality 2", "sg.PostProcessQuality 2", "sg.EffectsQuality 2", "sg.FoliageQuality 2", "sg.TextureQuality 2",
         "sg.AntiAliasingQuality 2", "sg.ViewDistanceQuality 2", "r.ShadowQuality 3", "r.BloomQuality 3", "r.MobileMSAA 2", "r.DetailMode 1"},
        // VeryHigh
        {"sg.ShadowQuality 3", "sg.PostProcessQuality 3", "sg.EffectsQuality 3", "sg.FoliageQuality 3", "sg.TextureQuality 3",
         "sg.AntiAliasingQuality 2", "sg.ViewDistanceQuality 3", "r.ShadowQuality 4", "r.BloomQuality 4", "r.MobileMSAA 2", "r.DetailMode 2"},
        // Ultra
        {"sg.ShadowQuality 3", "sg.PostProcessQuality 3", "sg.EffectsQuality 3", "sg.FoliageQuality 3", "sg.TextureQuality 3",
         "sg.AntiAliasingQuality 3", "sg.ViewDistanceQuality 3", "r.ShadowQuality 5", "r.BloomQuality 5", "r.MobileMSAA 4", "r.DetailMode 2", "r.MaxAnisotropy 8"},
    };
    static final String[] NAME = {"Low", "Middle", "High", "VeryHigh", "Ultra"};
    static final String[] CN = {"流畅", "均衡", "高清", "超高清", "极致"};

    static void start() {
        if (started) return; started = true;
        Thread t = new Thread(() -> {
            while (true) {
                try { Thread.sleep(3000); } catch (InterruptedException e) { return; }
                try { Con.exec("getall NZSystemSettingData RenderQualityOption"); } catch (Throwable ignored) {}
            }
        }, "zf-gfx");
        t.setDaemon(true); t.start();
        state = "在看设置里的画质";
    }

    /** 日志行：... .RenderQualityOption = ERenderQualityOption::High（或 High） */
    static void line(String l) {
        int i = l.indexOf("RenderQualityOption = ");
        if (i < 0) return;
        String v = l.substring(i + 22).trim();
        int c = v.lastIndexOf("::"); if (c >= 0) v = v.substring(c + 2);
        int sp = v.indexOf(' '); if (sp > 0) v = v.substring(0, sp);
        if (v.isEmpty()) return;
        String prev = opt; opt = v;
        if (prev == null) { state = "设置里是 " + cn(v) + "（进图时游戏已应用）"; return; }
        if (prev.equals(v)) return;
        int k = idx(v);
        if (k < 0) { state = "不认识的画质 " + v; LogCap.note("画质：" + state); return; }
        int n = 0;
        for (String cmd : LV[k]) if (Con.exec(cmd)) n++;
        state = "已切到 " + CN[k] + "（发了 " + n + " 条）";
        LogCap.note("画质：" + state);
        GameOverlay.toastAny("画质已切到 " + CN[k]);
        GfxOpt.applyAll();   // v3.58 细项盖在画质档上面
    }
    /** v2.26b 房主减负：联机里大家跟着房主，房主（云手机）只当服务器用，画面压到最低、帧率锁 30（服务器每秒 30 次更新，够用） */
    static final String[] LITE = {"sg.ShadowQuality 0", "sg.PostProcessQuality 0", "sg.EffectsQuality 0", "sg.FoliageQuality 0", "sg.TextureQuality 0",
        "sg.AntiAliasingQuality 0", "sg.ViewDistanceQuality 0", "r.ShadowQuality 0", "r.BloomQuality 0", "r.MobileMSAA 1", "r.DetailMode 0",
        "r.ScreenPercentage 30", "foliage.DensityScale 0", "grass.DensityScale 0", "t.MaxFPS 30"};
    static volatile boolean liteOn;
    static void lite() {
        new Thread(() -> {
            try { Thread.sleep(4000); } catch (InterruptedException e) { return; }
            if (Lan.role != 1 || !Con.inMatch) return;
            int n = 0; for (String c : LITE) if (Con.exec(c)) n++;
            liteOn = true;
            state = "服务端减负：画面最低、30 帧（发了 " + n + " 条）";
            LogCap.note(state);
        }, "zf-lite").start();
    }
    static void unlite() {
        if (!liteOn) return; liteOn = false;
        Con.exec("r.ScreenPercentage 100"); Con.exec("t.MaxFPS " + Con.fps);
        state = "服务端减负：回大厅已恢复分辨率和帧率";
    }
    static int idx(String v) { for (int k = 0; k < NAME.length; k++) if (NAME[k].equalsIgnoreCase(v)) return k; return -1; }
    static String cn(String v) { int k = idx(v); return k < 0 ? v : CN[k]; }
}
