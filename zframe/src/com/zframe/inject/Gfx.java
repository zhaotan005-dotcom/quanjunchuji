package com.zframe.inject;

/** v2.04 游戏设置里的画质选了立刻生效。
 *  游戏自己只在进地图时按设置应用画质（UNZSystemSettingData::ApplyRenderQuality 0xca14e0，PreLoadMap 里调），设置界面只提示「下局生效」。
 *  v4.07 起每 3 秒直接读设置对象内存（见 start），选项变了就按档位发 sg./r. 命令。第一次读到的值不发（进图时游戏已经应用了）。 */
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

    /** v4.07：不读游戏日志。以前每 3 秒 getall 让游戏把值打进日志再读；现在直接读设置对象内存：
     *  UNZSystemSettingData 实例（对象表按类名找一次，缓存，类指针变了再找）+0x7c RenderQualityOption（字节，反射表 0x4c07a5c 偏移 0x7c；
     *  枚举 ERenderQualityOption 0 Low 1 Middle 2 High 3 VeryHigh 4 Ultra 5 Unknown，注册表 0x4c04120）、+0x84 FrameRateOption（反射 0x4c07914）。
     *  帧率选项变了 = 游戏重设了帧率（以前日志 ApplyFrameRateOption）→ 0.8 秒后重发我们的最高帧率。服务端不跟设置（v4.05），不找。 */
    static volatile long inst, instCls, findAt; static volatile int lastFr = -1;
    static void start() {
        if (started) return; started = true;
        Thread t = new Thread(() -> {
            while (true) {
                try { Thread.sleep(3000); } catch (InterruptedException e) { return; }
                try { poll(); } catch (Throwable ignored) {}
            }
        }, "zf-gfx");
        t.setDaemon(true); t.start();
        state = "在看设置里的画质（读内存）";
    }
    static void poll() throws Exception {
        if (Ed.ED == 1) { state = "服务端压内存，不跟设置切画质"; return; }
        if (!GameOverlay.lobbySeen) return;
        long b = Mods.base(); if (b == 0) return;
        long o = inst; int v, fr;
        try (java.io.RandomAccessFile m = new java.io.RandomAccessFile("/proc/self/mem", "r")) {
            if (o != 0 && (!Mods.isObj(m, b, o) || Server.rd(m, o + 0xc) != instCls)) { o = 0; inst = 0; opt = null; lastFr = -1; }
            if (o == 0) {
                long now = System.currentTimeMillis();
                if (now - findAt < 60000) return;
                findAt = now;
                o = WorldWatch.firstInstance("NZSystemSettingData");
                if (o == 0) { state = "画质：没找到设置对象（60 秒后再找）"; return; }
                inst = o; instCls = Server.rd(m, o + 0xc);
                LogCap.note("画质：设置对象 " + Long.toHexString(o));
            }
            byte[] x = new byte[12]; m.seek(o + 0x7c); m.readFully(x);
            v = x[0] & 0xff; fr = x[8] & 0xff;
        }
        if (lastFr >= 0 && fr != lastFr) Con.fpsAt = System.currentTimeMillis();
        lastFr = fr;
        if (v < NAME.length) onOpt(NAME[v]);
    }
    static void onOpt(String v) {
        String prev = opt; opt = v;
        if (prev == null) { state = "设置里是 " + cn(v) + "（进图时游戏已应用）"; return; }
        if (prev.equals(v)) return;
        if (Ed.ED == 1) { state = "服务端压内存，不跟设置切画质"; return; }   // v4.05
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
