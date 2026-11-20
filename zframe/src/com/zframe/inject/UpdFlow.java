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
    /** v3.61 用户：v3.60 日志里更新步骤 NZStartupFlowUpdateStepApp 一开始就收到 [lb#OnResUpdateResult] 107「更新中(100)」
     *  （GCloud 开 apk 里的 nzres.png IFS 失败 —— 腾讯资源更新，离线包里本来就没有），游戏自己的更新页就进了出错状态，看不到我们的内核下载进度。
     *  RE：UNZStartupFlowUpdateStep::OnResUpdateResult 0x1212a9c(步骤, 错误码, FString 信息)：错误码 -6969..-6964（成功/跳过/取消等）走跳转表，
     *  其余（107 这类错误）进默认分支 0x1212b6c：GameInstance+0x3b8 弹错误框 0x10b5d9c，再调更新页 vt+0x25c(信息) 把页面切成出错显示。
     *  改：默认分支第一句 ldr → b 0x1213238（函数出口，栈帧已建、还没分配 FString），错误结果直接忽略；成功/跳过等不受影响。
     *  更新页留在游戏自己的页面上，进度条照旧由 UpdPage 每 0.3 秒调页面 vt+0x240 OnUpdateProcess 写我们的下载进度，走完（已是最新/下好重启）才进大厅。未实测。 */
    static final long RES_ERR = 0x1212b6cL;
    static final String RES_ERR_O = "74079fe5", RES_ERR_N = "b10100ea";
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
            if ("ok".equals(r)) { on = true; try { tryFile.createNewFile(); } catch (Throwable ignored) {} state = "游戏更新页：已开（不让启动流程关更新步骤）；腾讯资源检查报错不弹框：" + Mods.write(RES_ERR, RES_ERR_O, RES_ERR_N, true); }
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

    /** v3.59 用户：开机没有 TiMi 页（一直没有）。RE：启动 GameMode 构造 0x121af90 只推 Timi/Update/LoadLogin 三步，
     *  放 LogoUI（timistart 帧 + Timi_Logo_SoundCue）的 UNZStartupFlowLogo（StaticClass 0x1680678，Begin 0x121166c，3.6 秒自动结束）没在列表里；
     *  UNZStartupFlowTimi 只做设备档位/分辨率（End 0x1211a24），不放动画。
     *  改：0x121b0c0 bl Timi::StaticClass → bl Logo::StaticClass（第一步换成 Logo）；
     *      Logo 的 End 0x12118c8 末尾 0x1211914 `b 0x120ca70`（基类 End）→ `b 0x1211a24`（Timi 的 End：设备档位，再进基类 End）。两处 4 字节，不用 cave。未实测。 */
    static final long LOGO_CTOR = 0x121b0c0L, LOGO_END = 0x1211914L;
    static volatile String timi = "TiMi 页：没装";
    static void installTimi() {
        String a = Mods.write(LOGO_CTOR, "bc9511eb", "6c9511eb", true);
        String b = "ok".equals(a) ? Mods.write(LOGO_END, "55ecffea", "420000ea", true) : "跳过";
        if ("ok".equals(a) && !"ok".equals(b)) Mods.write(LOGO_CTOR, "bc9511eb", "6c9511eb", false);
        String c = "ok".equals(a) && "ok".equals(b) ? logoFull() : "跳过";
        timi = "TiMi 页：" + ("ok".equals(a) && "ok".equals(b) ? "已接回开机流程；放完整动画 " + c : "没装上 " + a + "/" + b);
        LogCap.note(timi);
    }

    /** v3.61 用户：TiMi 页看得到的不到 3 秒（Logo 流程日志 4.8 秒）。RE + pak 只读解析：
     *  - UNZStartupFlowLogo 虚表 0x4cf2600：+0x120 Begin 0x121166c（CreateWidget LogoUI_C → AddToViewport，蓝图 Construct 播 LogoAnim），
     *    +0x130 超时 0x1211368 = 3.6f，+0x134 Tick = 基类 0x120f460（+0x20 累加 dt，超过超时就 End）。没有别的结束条件。
     *  - LogoUI.uasset：LogoAnim PlaybackRange 0~4.0015 秒，TiMi 音效段 1.1~4.0 秒 → 3.6 秒超时本来就切掉动画最后 0.4 秒。
     *  - 开机日志：Begin 在第 0 帧（13:26:54.616），窗口/渲染要到 GEngineLoop.Init 之后才起来（上一轮 LoadMap 后约 1.3 秒才 ExecWindowCreated），
     *    控件在第一帧前就建好，第一帧 Slate 的 dt 很大 → 动画一开始就跳过一大截（推断），看到的只剩 2~3 秒。
     *  改（cave 0x171e690..0x171e6df，game/re/logo_cave.py，emu_logo_cave.py unicorn 过）：
     *    虚表 +0x120 → 空函数（先不建控件）；+0x134 → 控件没建时只累计 dt，满 0.5 秒（引擎已正常出帧）再调原 Begin；建好后照常走基类 Tick；
     *    +0x130 → 4.5 秒（动画 4.0 秒放完再停 0.5 秒）。只改 Logo 这个类的虚表，Timi 类（同用 0x1211368）不动。未实测。 */
    static final long LOGO_CAVE = 0x171e690L, LOGO_VT = 0x4cf2600L;
    static final String LOGO_CAVE_O = "0670a0e10870a01138300be530300be5010057e334700be51b0000ba38004be20010a0e3b158d1eb34701be5010057e3150000ba000055e338901b15000059131300000a0010a0e3000058e30600000a";
    static final String LOGO_CAVE_N = "1eff2fe1242090e5000052e30b00001a101a00ee081a90ed001a31ee081a80ed000ab6eec01ab4ee10faf1ee1eff2fd10020a0e3202080e5e7cbebea00f020e362c3ebea000000e3900044e31eff2fe1";
    static final long[][] LOGO_SLOTS = { { 0x120, 0x121166cL, 0x171e690L }, { 0x134, 0x120f460L, 0x171e694L }, { 0x130, 0x1211368L, 0x171e6d4L } };
    static String logoFull() {
        long b = Mods.base(); if (b == 0) return "找不到游戏库";
        String r = Mods.write(LOGO_CAVE, LOGO_CAVE_O, LOGO_CAVE_N, true);
        if (!"ok".equals(r)) return "cave " + r;
        try (java.io.RandomAccessFile m = new java.io.RandomAccessFile("/proc/self/mem", "r")) {
            for (long[] e : LOGO_SLOTS) {
                long cur = Server.rd(m, b + LOGO_VT + e[0]);
                if (cur != ((b + e[1]) & 0xffffffffL) && cur != ((b + e[2]) & 0xffffffffL)) return "虚表 +" + Long.toHexString(e[0]) + " 原值不符 " + Long.toHexString(cur - b);
            }
        } catch (Throwable t) { return "出错 " + t; }
        for (long[] e : LOGO_SLOTS) Mods.pokeInt(b + LOGO_VT + e[0], (int) (b + e[2]));
        try (java.io.RandomAccessFile m = new java.io.RandomAccessFile("/proc/self/mem", "r")) {
            for (long[] e : LOGO_SLOTS) if (Server.rd(m, b + LOGO_VT + e[0]) != ((b + e[2]) & 0xffffffffL)) {
                for (long[] f : LOGO_SLOTS) Mods.pokeInt(b + LOGO_VT + f[0], (int) (b + f[1]));
                return "虚表写入没生效，已还原";
            }
        } catch (Throwable t) { return "出错 " + t; }
        return "ok";
    }
}
