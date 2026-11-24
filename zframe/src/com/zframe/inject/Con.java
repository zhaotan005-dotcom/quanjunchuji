package com.zframe.inject;

import android.app.Activity;

import java.io.*;
import java.lang.reflect.Method;
import java.util.*;

/**
 * 通过 UE4 自带的 GameActivity.nativeConsoleCommand 给引擎发控制台命令（纯 Java，不需要 root）。
 * v4.07/4.09：不读任何游戏日志（也不读 logcat）；游戏状态全从内存（WorldWatch）和游戏发给自建服务端的请求包（15001 等）来。
 */
public class Con {
    static volatile int p92n;
    static volatile long fastUntil;   // v3.93 刷车时游戏日志每 100ms 读一次（以前 1 秒，刷车确认最多晚 1 秒）   // v3.92 P92 诊断条数
    static volatile Activity act;
    static volatile String last = "";
    static final List<String> history = Collections.synchronizedList(new ArrayList<String>());

    static void setActivity(Activity a) { act = a; try { if (a != null) { if (Ed.uploads()) Beat.start(a.getApplicationContext()); else Beat.wipe(a.getApplicationContext()); } } catch (Throwable ignored) {} }

    static Method find(Class<?> c) {
        for (; c != null; c = c.getSuperclass()) {
            try { Method m = c.getDeclaredMethod("nativeConsoleCommand", String.class); m.setAccessible(true); return m; }
            catch (NoSuchMethodException ignored) {}
        }
        return null;
    }

    /** 发一条命令，返回是否送达引擎 */
    public static boolean exec(String cmd) {
        Activity a = act;
        if (a == null) { last = "没找到游戏界面"; return false; }
        try {
            Method m = find(a.getClass());
            if (m == null) { last = "这个游戏没有 UE4 控制台入口"; return false; }
            if (cmd.startsWith("open ")) {   // v4.07：记下开图命令（战术模式判断）；对局图先装毒圈底层表（以前在日志 Browse 行装，时机相同：切图开始前）
                String t = cmd.substring(5).trim(); lastOpen = t; lastOpenAt = System.currentTimeMillis();
                if (isLobby(t)) lobbyOpenAt = lastOpenAt;
                int q = t.indexOf('?'); String mp = q > 0 ? t.substring(0, q) : t;
                if (mp.startsWith("/Game/Maps/") && !isLobby(mp)) { natFor = mp; try { Flow.natPrepare(mp); } catch (Throwable ignored) {} }
                if (mp.startsWith("/Game/Maps/") && !isLobby(mp)) { try { Air.prepare(mp); } catch (Throwable ignored) {} }   // v4.21：航线/飞机高度从源头改
                try { ModeMap.beforeOpen(mp); } catch (Throwable ignored) {}   // v4.11：训练营模式编号 600（游戏自己的「死了 4 秒复活」规则），别的图改回
            }
            m.invoke(a, cmd);
            last = cmd;
            history.add(cmd);
            if (history.size() > 50) history.remove(0);
            LogCap.note("命令: " + cmd);
            return true;
        } catch (Throwable e) {
            last = "发送失败 " + e;
            LogCap.note("命令失败: " + cmd + " " + e);
            return false;
        }
    }

    // ===== 自动加载 ServerOnly 关卡 =====
    static final LinkedHashSet<String> serverLevels = new LinkedHashSet<>();
    static volatile String layerStatus = "等待进地图";
    static volatile long lastAddAt, sentAt; static volatile int passes;
    static volatile boolean autoOn = true;

    public static String layerStatus() { return layerStatus; }
    static String layerShort() {
        synchronized (serverLevels) { if (serverLevels.isEmpty()) return "这张图的门/物资/车是游戏自己加载的，不用重载"; }
        return "已重载门/物资/车";
    }

    static void loadServerLevels(String why) {
        List<String> lv;
        synchronized (serverLevels) { lv = new ArrayList<>(serverLevels); }
        if (lv.isEmpty()) { layerStatus = "还没在日志里看到服务器关卡（先进一局）"; return; }
        exec("EnableCheats");
        int n = 0;
        for (String p : lv) {
            // v1.85：导航网格要加载——单机持久关卡里的 RecastNavMesh 只有 4 字节（日志 RecastNavMeshSizeBytes=4），没有导航网格人机走不了
            if (p.endsWith("/WorldMeshesLevel")) continue; // 只给服务器用的大网格，跳过
            if (exec("StreamLevelIn " + p)) n++;
        }
        sentAt = System.currentTimeMillis();
        layerStatus = why + "：已请求加载 " + n + " 个关卡（门/物资/载具点）";
    }

    /** v4.07：不再读游戏日志。这个线程只做定时的事（发设置、开机进大厅、声音修复跳转）；游戏状态由 WorldWatch 从内存读 */
    static void startWatcher(final android.content.Context ctx) {
        WorldWatch.start();
        Thread t = new Thread(() -> {
            while (true) {
                try { tickTweaks(System.currentTimeMillis()); } catch (Throwable e) { LogCap.note("定时任务出错 " + e); }
                try { Thread.sleep(500); } catch (InterruptedException e) { return; }
            }
        }, "zframe-tick");
        t.setDaemon(true);
        t.start();
    }

    // ===== 用引擎 SET 命令改类默认值（新对象和已存在的对象都会改） =====
    static final String[] TWEAKS = {
        // v1.85：去掉等待/开局/跳伞倒计时 5 秒、1 秒的改动，恢复游戏表里的正常节奏（用户：开局太快、第一个圈直接出现）
        "set NZSceneDoorManager StandaloneUnloadAllDoor 0",
        "set NZGameMode bEnableAIDefaultWeap 1",
        "set NZGameMode bEnableGiveBotEquip 1",
        "set NZBattleRoyaleGameMode MaxBotLimit 100",   // v1.85：100 人局
        // 最高帧率档；关掉引擎的帧率平滑上限（游戏表里最高只给 60）
        "set NZSystemSettingData FrameRateOption 4",
        "set GameEngine bSmoothFrameRate 0",
        "r.AutoAdjustRenderQuality 0",
    };
    // ===== 画质：流畅（最低画质、帧率最高）/ 最高画质。游戏自己的画质档离线不生效，直接改引擎画质项 =====
    static final String[] GFX_LOW = {"sg.ShadowQuality 0", "sg.PostProcessQuality 0", "sg.EffectsQuality 0", "sg.FoliageQuality 0",
        "sg.TextureQuality 1", "sg.AntiAliasingQuality 0", "r.ShadowQuality 0", "r.BloomQuality 0", "r.MobileMSAA 1", "r.DetailMode 0"};
    static final String[] GFX_HIGH = {"sg.ShadowQuality 3", "sg.PostProcessQuality 3", "sg.EffectsQuality 3", "sg.FoliageQuality 3",
        "sg.TextureQuality 3", "sg.AntiAliasingQuality 3", "sg.ViewDistanceQuality 3", "r.ShadowQuality 5", "r.BloomQuality 5",
        "r.MobileMSAA 4", "r.DetailMode 2", "r.MaxAnisotropy 8"};
    /** 画质选择会记住（这是偏好，不是功能开关） */
    public static volatile boolean hiGfx;
    static void loadGfx() { Activity a = act; if (a != null) hiGfx = a.getSharedPreferences("zframe", 0).getBoolean("gfx_hi", false); }
    public static void setGfx(boolean hi) {
        hiGfx = hi;
        Activity a = act;
        if (a != null) a.getSharedPreferences("zframe", 0).edit().putBoolean("gfx_hi", hi).apply();
        if (engineSeenAt > 0) applyGfx();
    }
    static void applyGfx() { for (String c : hiGfx ? GFX_HIGH : GFX_LOW) exec(c); }

    static volatile long engineSeenAt, tweakAt, browseAt, coatAt;
    static volatile int tweakDone; static volatile long waitAt, brAt;
    /** v1.49：当前是不是在对局地图里（大厅 = false）。加速/高跳/刷物品只在对局里用 */
    public static volatile boolean inMatch;
    static volatile long lobbyAt, mallTipAt;
    static volatile long quitAt;
    public static volatile String navInfo = "";
    static final java.util.ArrayList<String> botLog = new java.util.ArrayList<>();
    static String botLog() { synchronized (botLog) { return String.join("\n  ", botLog); } }
    /** v2.73 用户：丢枪/配件到地上直接消失 → 记下游戏日志里丢东西、生成地上物的行 */
    static final java.util.ArrayList<String> dropLog = new java.util.ArrayList<>();
    static String dropLog() { synchronized (dropLog) { return dropLog.isEmpty() ? "（没有）" : String.join("\n  ", dropLog); } }
    static final java.util.ArrayList<String> flowLog = new java.util.ArrayList<>();
    static String flowLog() { synchronized (flowLog) { return String.join("\n  ", flowLog); } }
    public static volatile String tweakStatus = "等待游戏启动";

    // ===== 帧率 =====
    public static volatile int fps = 0; // 0 = 不改
    static volatile long fpsAt;

    static void loadFps() {
        Activity a = act; if (a == null) return;
        fps = a.getSharedPreferences("zframe", 0).getInt("fps", 0);
    }

    public static void setFps(int f) {
        fps = f;
        Activity a = act;
        if (a != null) a.getSharedPreferences("zframe", 0).edit().putInt("fps", f).apply();
        applyFps();
    }

    static void applyFps() {
        // v1.27：默认最高帧率：屏幕切到最高刷新率，引擎上限跟着屏幕走
        final Activity a = act;
        if (a == null) return;
        int f = 60;
        try { for (android.view.Display.Mode m : a.getWindowManager().getDefaultDisplay().getSupportedModes()) f = Math.max(f, Math.round(m.getRefreshRate())); } catch (Throwable ignored) {}
        fps = f;
        exec("t.MaxFPS " + f);
        final int ff = f;
        if (a != null) a.runOnUiThread(() -> { try {
            android.view.Window w = a.getWindow();
            android.view.WindowManager.LayoutParams lp = w.getAttributes();
            android.view.Display d = a.getWindowManager().getDefaultDisplay();
            android.view.Display.Mode best = null; android.view.Display.Mode cur = d.getMode();
            for (android.view.Display.Mode m : d.getSupportedModes()) {
                if (m.getPhysicalWidth() != cur.getPhysicalWidth() || m.getPhysicalHeight() != cur.getPhysicalHeight()) continue;
                if (m.getRefreshRate() + 0.5f < ff) continue;
                if (best == null || m.getRefreshRate() < best.getRefreshRate()) best = m;
            }
            if (best == null) for (android.view.Display.Mode m : d.getSupportedModes()) if (best == null || m.getRefreshRate() > best.getRefreshRate()) best = m;
            if (best != null) { lp.preferredDisplayModeId = best.getModeId(); lp.preferredRefreshRate = best.getRefreshRate(); w.setAttributes(lp); }
        } catch (Throwable ignored) {} });
    }

    /** v4.05：服务端进程每次都在 ~1.0GB 内存被系统杀（4 次样本，推断是单应用内存上限）→ 服务端包压内存：画质最低、贴图池 160MB。
     *  开机发一次、每次进图后再发、开房前也发（游戏 PreLoadMap 会按设置重设 sg.*，所以也把设置里的画质改成流畅） */
    static final String[] SRV_MEM = {"set NZSystemSettingData RenderQualityOption Low", "sg.TextureQuality 0", "sg.ViewDistanceQuality 0", "sg.FoliageQuality 0",
        "sg.EffectsQuality 0", "sg.ShadowQuality 0", "sg.PostProcessQuality 0", "r.ShadowQuality 0", "r.MobileMSAA 1", "r.DetailMode 0",
        "r.Streaming.PoolSize 80", "r.Streaming.MipBias 2", "r.SkeletalMeshLODBias 1", "r.ViewDistanceScale 0.6", "foliage.DensityScale 0", "grass.DensityScale 0"};   // v4.06：池 160→128、少一级 mip；v4.16：池 128→80、少两级 mip、骨骼模型降一级 LOD、视距 0.6（云手机显存算进进程内存，推断）
    static void srvMem(String why) {
        if (Ed.ED != 1) return;
        int n = 0; for (String c : SRV_MEM) if (exec(c)) n++;
        LogCap.note("服务端省内存（" + why + "）：发了 " + n + " 条");
    }
    static void runTweaks(String why) {
        int n = 0;
        if (fps == 0) loadFps();
        applyFps();
        for (String c : TWEAKS) if (exec(c)) n++;
        srvMem("开机");
        // v2.04：画质改由游戏设置决定（Gfx.java 选了立即生效），不再发我们自己的画质
        tweakAt = System.currentTimeMillis();
        tweakStatus = why + "：已发送 " + n + " 条（门/人机/帧率）";
    }

    // ===== v1.35 游戏流程：不上飞机 / 不刷毒 =====
    // 游戏状态在进图后才从表里读出 60/45 秒（NZBRGameState.cpp:304），所以要等这行之后再改实例。
    // 不上飞机：等待时间改成 99999 秒，一直停在出生岛；否则改成 5 秒（缩短等待）。
    // 不刷毒：RestrictedAreaSystem.CurrRoundRestrictedAreaRepInfo.bStart（+0x2f9）为 0 时，毒圈 Tick 整个跳过（不缩圈、不扣血），每 5 秒重设一次。
    public static volatile boolean noPlane, noPoison;
    static volatile long poisonAt;
    static final String STOP_POISON = "set RestrictedAreaSystem CurrRoundRestrictedAreaRepInfo (bStart=False)";

    static void loadFlow() {
        Activity a = act; if (a == null) return;
        android.content.SharedPreferences p = a.getSharedPreferences("zframe", 0);
        noPlane = p.getBoolean("no_plane", false); noPoison = p.getBoolean("no_poison", false);
    }

    public static void setFlow(boolean plane, boolean poison) {
        noPlane = plane; noPoison = poison;
        Activity a = act;
        if (a != null) a.getSharedPreferences("zframe", 0).edit().putBoolean("no_plane", plane).putBoolean("no_poison", poison).apply();
        applyFlow("手动");
    }

    public static void applyFlow(String why) {
        String t = noPlane ? "99999" : "5";
        exec("set NZBRGameState WaitingPlayersCountDownTime " + t);
        exec("set NZBRGameState StartMatchCountDownTime " + t);
        exec("set NZBRGameState ResetStartMatchCountDownTime " + t);
        if (noPoison) exec(STOP_POISON);
        LogCap.note("游戏流程(" + why + "): 不上飞机=" + noPlane + " 不刷毒=" + noPoison);
    }

    static void tickTweaks(long now) {
        if (engineSeenAt > 0 && tweakDone == 0 && now - engineSeenAt > 10000) { tweakDone = 1; runTweaks("启动时"); }
        if (browseAt > tweakAt && now - browseAt > 1500) { if (Lan.role == 0) runTweaks("进图时"); else { tweakAt = browseAt; LogCap.note("联机：进图时不发调整命令"); } }
        if (fpsAt > 0 && now - fpsAt > 800) { fpsAt = 0; applyFps(); }
        if (brAt > 0 && now - brAt > 4000) { brAt = 0; Mods.lastPawnAsk = 0; }
        // 大厅加载完 6 秒后打上「初始衣服 = 绝地风衣」（启动画面阶段不动内存）
        if (coatAt > 0 && now - coatAt > 6000) { coatAt = -1; Mods.builtInCoat(); Mods.restoreZone(); LogCap.note("开局等人 60 秒→1 秒：" + Mods.write(Mods.WAIT, Mods.WAIT_O, Mods.WAIT_N, true)); if (Lan.role == 0) { LogCap.note("单机开局读秒 45→20 秒：" + Mods.write(Mods.START, Mods.START_O, Mods.START_N, true)); LogCap.note("单机等人默认 60→1 秒（构造）：" + Mods.waitCtor()); }   // v1.95b 用户：一进去不显示读秒，过好一会才出 43 倒计时 → 去掉不显示读秒的那 60 秒
            /* v2.06 用户：不要每次都存；v2.09b 只在上次联机加载闪退时自动存 */ if (Ed.uploads()) new Thread(Server::autoCrashSave).start(); }
        tickAudio(now);
    }

    // ===== v1.19 声音修复：经大厅初始化 Wwise 音频管理器 =====
    // 日志显示：单机版开机直接从 StartupMap 跳进海岛，绕过了大厅；
    // UNZFrontEndAudioManager / UNZPlatformAudioHelper（加载 UI/BGM 音频库、管理 Wwise 挂起/唤醒）只在大厅地图里初始化。
    // 做法：第一次进海岛后，如果本次启动还没见过大厅音频初始化，就先 open 大厅，等音频初始化完成后再 open 回同一张地图。
    static final String LOBBY = "/Game/Maps/Erangle_RoleHall/NEW/FrontEndMap_NEW";
    public static volatile boolean audioAuto = true;
    static volatile boolean earlySrv;
    static volatile boolean feAudioSeen; static volatile int bounce; // 0 未做 1 已去大厅 2 已回地图
    static volatile long brLoadedAt, feSeenAt; static volatile String brMap;
    public static volatile String audioFix = "等待进图";

    static void loadAudioPref() {
        Activity a = act; if (a == null) return;
        audioAuto = true; // v1.27：固定开，不在菜单里显示
    }

    public static void setAudioAuto(boolean on) {
        audioAuto = on;
        Activity a = act;
        if (a != null) a.getSharedPreferences("zframe", 0).edit().putBoolean("audio_auto3", on).apply();
    }

    // ===== v1.22 艾挽菜单：进地图（本次启动第一次进图先经过大厅，让声音正常） =====
    public static final String MAP_ERANGEL = "/Game/Maps/ErangelMobile_Runtime/BR-Erangel_Mobile_Main";
    public static final String MAP_ERANGEL2 = "/Game/Maps/ErangeMobileRenew_Runtime/BR-ErangelRenew_Mobile_Main";
    public static final String MAP_SAVAGE = "/Game/Maps/SavageMobile_4x4_Runtime/BR-Savage_Mobile_Main";
    static volatile String pendingMap;
    /** v3.33：用户发来的 pak 清单里训练营地图真名 BR-TrainingIsland_Mobile_Main（libUE4 里的 BR-Training_Mobile_Main 是旧名，所以 v1.12 报不存在）。
     *  地图默认模式 = 训练场 BP_NZBRPracticeGameMode；训练营模式 = BP_NZBRTrainingGameMode（训练人机 TrainingBot、训练 HUD）。 */
    public static final String MAP_TRAINING = "/Game/Maps/TrainingIsland_Runtime/BR-TrainingIsland_Mobile_Main";
    public static final String GM_TRAINING = "/Game/Blueprints/TrainingGame/BP_NZBRTrainingGameMode.BP_NZBRTrainingGameMode_C";
    static volatile boolean training;   // 当前对局在训练岛：不跑我们的刷人机/改模式/删占位
    static String goTraining(boolean camp) {
        String r = goMap(MAP_TRAINING + (camp ? "?game=" + GM_TRAINING : ""));
        LogCap.note((camp ? "训练营" : "训练场") + " → " + r);
        return r;
    }

    /** 返回给用户看的提示 */
    public static String goMap(String m) {
        if (audioAuto && !feAudioSeen && startupLobby && brMap == null) {
            // 开机进大厅还没完成：等大厅声音好了再自动进这张图，不重复开大厅
            pendingMap = m; bounce = 1; brLoadedAt = 0;
            return "大厅还在加载声音，好了自动进图";
        }
        if (audioAuto && !feAudioSeen) {
            pendingMap = m; bounce = 1; brLoadedAt = 0;
            audioFix = "先进大厅加载声音，然后自动进图…";
            LogCap.note("进图: 先进大厅 " + LOBBY + "，然后进 " + m);
            exec("open " + LOBBY);
            return "先进大厅加载声音，几秒后自动进图";
        }
        pendingMap = null;
        LogCap.note("进图: " + m);
        exec("open " + m);
        return "正在进图";
    }

    // ===== v1.31 无敌状态（自己记：每局开局都是关的）+ 传送到地图中心 =====
    static volatile boolean godOn = false;
    static boolean godToggled() { godOn = !godOn; if (godOn) Mods.startMover(); return godOn; }
    static volatile long tpGen = 0;
    /** 地图中心 = 世界原点(0,0)（出生点 387615,-388622 在海岛角上，海岛 ±4080 米）。
     *  地面高度没法直接读，所以落点放在离地不高的地方（120 米，地面一般 5~50 米），并自动开无敌，落地后再关回去。 */
    public static String tpCenter() {
        exec("EnableCheats");
        final boolean autoGod = !godOn;
        if (autoGod) { exec("God"); godOn = true; }
        exec("BugItGo 0 0 12000 0 0 0");
        LogCap.note("传送到地图中心，自动无敌=" + autoGod);
        final long g = ++tpGen;
        if (autoGod) new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (g == tpGen && godOn) { exec("God"); godOn = false; GameOverlay.toastAny("已落地，无敌已自动关掉"); }
        }, 15000);
        return autoGod ? "已传送到地图中心，落地前自动无敌，15 秒后自动关" : "已传送到地图中心（无敌本来就开着）";
    }

    public static String goLobby() {
        pendingMap = null; if (bounce == 1) bounce = 0;
        LogCap.note("进大厅: " + LOBBY);
        exec("open " + LOBBY);
        return "正在进大厅";
    }

    /** 手动：现在就经大厅重进当前地图 */
    public static boolean audioBounceNow() {
        if (brMap == null) { audioFix = "还没进过地图，先进一局再点"; return false; }
        manualBounce = true; bounce = 0; feAudioSeen = false; feSeenAt = 0; brLoadedAt = System.currentTimeMillis() - 5000;
        return true;
    }

    // v1.23：开机在 StartupMap 上就直接进大厅（只一次），声音在大厅里初始化好，之后从艾挽选图直接进，不再来回跳
    static volatile boolean startupLobby;
    static void tickAudio(long now) {
        // v3.24：开了游戏更新页时，多停一会儿让进度条和公告显示完（查完更新且满 8 秒，最多 40 秒）
        // v3.28 用户：要走完更新进度条才进大厅 → 查更新没结果（没「已是最新/失败/下好」）就一直停在更新页，最多 10 分钟兜底
        boolean upd = !NetCore.done && NetCore.readyVer <= ZInit.CORE_VER;
        // v4.06：游戏日志读不到时 engineSeenAt 永远是 0 → 永远不进大厅。更新页已建出来 = 引擎肯定在跑，按它补上
        if (engineSeenAt == 0 && UpdPage.view != 0) { engineSeenAt = now - 3000; LogCap.note("引擎就绪：世界还没开始走时间，按更新页已建出来算"); }
        // v4.09：最后兜底——开机 90 秒内存里什么都没认出（更新页也没找到）也算引擎就绪，后面开大厅照常走，不让开机卡死
        if (engineSeenAt == 0 && now - bootAt > 90000 && act != null) { engineSeenAt = now - 3000; LogCap.note("引擎就绪：开机 90 秒兜底"); }
        // v4.06：开机最多停 60 秒（NetCore 自己 20~40 秒就放行；这里兜底，任何原因都不再无限卡在加载页）
        boolean hold = engineSeenAt > 0 && now - engineSeenAt < 60000 && (upd || (UpdFlow.on && !UpdPage.finished));   // v3.29：不设时间上限，失败自动重试   // v3.25：等游戏更新页走完（没建出来 25 秒放行）
        if (!startupLobby && !hold && audioAuto && !feAudioSeen && bounce == 0 && brMap == null && engineSeenAt > 0 && now - engineSeenAt > 3000 && act != null) {
            startupLobby = true;
            audioFix = "开机先进大厅加载声音";
            LogCap.note("声音: 开机直接进大厅 " + LOBBY);
            exec("open " + LOBBY);
        }
        if (bounce == 0 && brLoadedAt > 0 && !feAudioSeen && now - brLoadedAt > 2500) {
            if (!audioAuto && !manualBounce) { brLoadedAt = 0; audioFix = "自动修复已关闭（大厅音频未初始化）"; return; }
            manualBounce = false;
            bounce = 1; brLoadedAt = 0;
            audioFix = "正在进大厅初始化声音…";
            LogCap.note("声音修复: 本次启动没见过大厅音频初始化，先进大厅 " + LOBBY);
            exec("open " + LOBBY);
        }
        // v4.09：兜底——开了大厅之后 40 秒还没认出大厅加载完（WorldWatch 读不到地图名等），按已加载处理，等着进的图照样进，不卡人
        if (!feAudioSeen && lobbyOpenAt > 0 && now - lobbyOpenAt > 40000) { feSeenAt = now - 5000; feAudioSeen = true; LogCap.note("声音: 开大厅 40 秒没认出大厅加载完，按已加载算（兜底）"); }
        // v4.09：大厅里服务端没开（认不出大厅时 onBrowse/onLoaded 不会开）→ 开大厅 12 秒后兜底开，游戏的 15001/仓库请求才有人回
        if (!Server.on && !srvFallback && lobbyOpenAt > 0 && now - lobbyOpenAt > 12000 && Lan.role == 0 && !Lan.busy && !inMatch) {
            srvFallback = true;
            new Thread(() -> { if (Server.on || Lan.role != 0 || Lan.busy) return; LogCap.note("兜底开服务端（大厅 12 秒没认出）→ " + Server.start()); GameOverlay.refreshAny(); }, "zf-srv2").start();
        }
        if (bounce == 1 && feAudioSeen && feSeenAt > 0 && now - feSeenAt > 4000) {
            bounce = 2;
            String m = pendingMap != null ? pendingMap : brMap != null ? brMap : MAP_SAVAGE;
            pendingMap = null;
            audioFix = "大厅音频已初始化，正在回到地图";
            LogCap.note("声音修复: 大厅音频已初始化，回到 " + m);
            exec("open " + m);
        }
    }
    static volatile boolean manualBounce, srvFallback;
    static volatile long lobbyOpenAt;

    static volatile String circle = null; // "x y"
    static volatile long startAt;
    /** v4.09：菜单状态行用（只在内存，不落盘不上传）：最近一次 15001 解析结果、最近一次进图触发 */
    static volatile String lastStart = "没收到", lastTrig = "没有";
    /** 地图编号来自日志：1 老海岛，3 新海岛（GameMapId=3），4 雨林（GameMapId=4），5 训练场 */
    static void startGame(final int id) {
        // v3.66 用户：雨林也用战术模式（和海岛一样能用战术道具）
        // v3.22 用户：单机新海岛也用战术模式（和联机服务端开房一样：?game=战术模式蓝图）
        String m0 = id == 1 ? MAP_ERANGEL : id == 3 ? MAP_ERANGEL2 + "?game=" + ModeProbe.cache.get("Props") : id == 4 ? MAP_SAVAGE + "?game=" + ModeProbe.cache.get("Props") : id == 5 ? MAP_TRAINING + (Ed.BUILD == 2 ? "?listen" : "") : null;   // v3.33：5 训练场 = 训练岛默认模式；v4.18 用户：客户端训练场跟服务端一样（服务端「联机训练营」= open 训练岛 ?listen，菜单刷车、训练台刷车都正常、不卡）→ 客户端包进训练场也带 ?listen；单机包不变
        // v3.42 用户：删掉伪装联机（v3.25 加的单机 ?listen 进图；v3.31 起默认关），单机一律真单机进图
        final String m = m0;
        LogCap.note("游戏开始按钮: 地图编号 " + id + " → " + m);
        // v4.09：玩家是在大厅界面点的开始 → 大厅肯定已经加载完了。WorldWatch 没认出大厅（读不到地图名）时 feAudioSeen 一直是 false，
        //   goMap 就会把这张图挂在 pendingMap 等「大厅声音」，永远不进 → 这里直接认定大厅已好，绝不让开始按钮卡住
        pendingMap = null; if (bounce == 1) bounce = 2;   // 先撤掉等着的图（防 tickAudio 抢先开旧图）
        if (!feAudioSeen) { feSeenAt = System.currentTimeMillis(); feAudioSeen = true; LogCap.note("开始按钮：大厅界面已在 → 算大厅音频已初始化"); }
        lastTrig = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(new java.util.Date()) + " 开始游戏 编号 " + id + " → " + (m == null ? "无此图" : m.substring(m.lastIndexOf('/') + 1));
        final Activity a = act;
        if (a == null) return;
        a.runOnUiThread(() -> {
            if (m == null) { GameOverlay.toast(a, "这张图（编号 " + id + "）单机版里没有，换海岛或雨林"); return; }
            String r = Con.goMap(m); if (!"正在进图".equals(r)) GameOverlay.toast(a, r);
        });
    }

    static final long bootAt = System.currentTimeMillis();

    static volatile String lastWeap = ""; static volatile long lastWeapAt;
    static final java.util.regex.Pattern WEAP = java.util.regex.Pattern.compile("BP_NZWeap_(\\w+?)_C|WeaponBase/W\\d+_(\\w+)/");
    // ===== v4.07：游戏状态全部来自内存（WorldWatch），不再读游戏日志 =====
    // 普通手机没有游戏日志（要在手机设置里开），以前靠日志行触发的进图/回大厅/开机页/引擎就绪全改成 WorldWatch 的三个事件。
    static volatile String lastOpen = "", natFor = ""; static volatile long lastOpenAt;
    static boolean isLobby(String m) { return m.contains("RoleHall") || m.contains("FrontEnd"); }

    /** GWorld 变空 / 换世界（以前 Browse 行的「收起菜单」部分） */
    static void onLeave() {
        inMatch = false;
        GameOverlay.mapLeaving();
        GameOverlay.refreshAny();
    }

    /** 新世界出现、读到地图名（以前「LogNet: Browse: 地图」那行） */
    static void onBrowse(String m) {
        boolean lobby = isLobby(m);
        if (m.startsWith("/Game/Startup/StartupMap")) { GameOverlay.loadingPage(); GameOverlay.startupUpdate(); }
        // 大厅开始加载就开自建服务端（v2.19，以前看日志里 LoadMap 大厅）
        if (lobby && !Server.on && !earlySrv) {
            earlySrv = true;
            new Thread(() -> { if (Lan.role != 0 || Lan.busy || inMatch) { earlySrv = false; return; } LogCap.note("大厅开始加载，提前开服务端 → " + Server.start()); }, "zf-srv0").start();
        }
        Island.clearSeen();
        // 毒圈底层表要在对局开始前装：我们自己发 open 时已经装了（exec 里），游戏自己切的图这里补
        if (m.startsWith("/Game/Maps/") && !lobby && !m.equals(natFor)) { natFor = m; Flow.natPrepare(m); try { Air.prepare(m); } catch (Throwable ignored) {} }
        if (m.startsWith("/Game/Maps/") && !lobby) Prop.matchLoad();
        Bots.newMatch(); Flow.newMatch(); Loot.newMatch();
        synchronized (serverLevels) { serverLevels.clear(); }
        circle = null; godOn = false; tpGen++; browseAt = System.currentTimeMillis(); layerStatus = "正在进图…"; inMatch = false;
        GameOverlay.mapLeaving(); GameOverlay.forgetPawn(); GameOverlay.newMatch(); Mods.resetMatch(); GameOverlay.refreshAny();
    }

    /** 新世界开始走时间 = 加载完（以前「LogLoad: Took … LoadMap(地图)」那行） */
    static void onLoaded(String m) {
        long now = System.currentTimeMillis();
        if (engineSeenAt == 0 && (m.startsWith("/Game/") || "?".equals(m))) { engineSeenAt = now; LogCap.note("引擎就绪（" + m + "），10秒后发送设置"); loadAudioPref(); }
        Gfx.start();
        GfxOpt.afterLoad(); srvMem("进图后");
        fpsAt = now;   // 以前看日志 ApplyFrameRateOption：游戏进图会按设置重设帧率 → 每次进图后重发我们的
        natFor = "";
        if (!m.startsWith("/Game/Maps/")) return;
        boolean lobby = isLobby(m);
        // 战术模式（以前看日志 Game class is '…'）：名字表有就读 GameMode 类名，没有就看我们发的 open 带的 ?game=
        if (!lobby) {
            String gm = WorldWatch.gameModeClass();
            boolean props = gm != null ? gm.contains("Props") : (lastOpen.contains(m) && lastOpen.contains("Props"));
            ModeProbe.propsMode = props;
            LogCap.note("对局模式类：" + (gm != null ? gm : "名字表没有，按开图命令") + (props ? "（战术模式，用游戏原生道具栏）" : ""));
        } else ModeProbe.propsMode = false;
        training = m.contains("TrainingIsland");
        if (training && !lobby) { inMatch = true; MapMark.start(); Jetpack.start(); GameOverlay.refreshAny(); GameOverlay.lobbyReady(); if (coatAt == 0) coatAt = now; LogCap.note("进训练岛：不刷人机、不改模式、不删占位"); TrainMode.start(); Island.matchLoaded(); }
        else { inMatch = !lobby; if (inMatch) { MapMark.start(); Phantom.start(); Jetpack.start(); Walls.clientWatch(); if (Ed.ED == 1) HostMatch.start(); Mods.matchZone(); } GameOverlay.refreshAny(); GameOverlay.lobbyReady(); if (coatAt == 0) coatAt = now; }
        if (lobby) {
            lobbyAt = now;
            // 声音：大厅加载完 = 大厅音频已初始化（以前看日志 UNZFrontEndAudioManager: init）
            if (!feAudioSeen) { feAudioSeen = true; feSeenAt = now; LogCap.note("声音修复: 大厅已加载，算大厅音频已初始化"); }
            if (bounce != 1) audioFix = "大厅音频已初始化";
            Gfx.unlite();
            Island.caveReset();
            // 以前看日志 NetworkFailure/ConnectionLost 自动传 net-：现在客户端不是我们发的「回大厅」却回到了大厅 → 多半断线，传一份
            if (Lan.role == 2 && !(lastOpen.startsWith(LOBBY) && now - lastOpenAt < 120000)) { Lan.note("回到大厅（不是框架发的回大厅，可能断线/被踢）"); Lan.autoUp("net"); }
            Lan.role = 0; Bots.auto = Bots.want;
            if (!Server.on) new Thread(() -> { try { Thread.sleep(300); } catch (InterruptedException ignored) {} if (Lan.role != 0 || Lan.busy) return; LogCap.note("自动开服务端 → " + Server.start()); GameOverlay.refreshAny(); }, "zf-srv").start();
            return;
        }
        brMap = m; brAt = now;
        Parabola.matchStart();
        if (Lan.role == 0 && !training) { Bots.matchLoaded(); Flow.matchLoaded(m); }
        else if (Lan.role == 1) { Flow.matchLoaded(m); Gfx.lite(); Lan.note("进图完成（服务端：航线/毒圈照单机改；减负）"); }
        else if (Lan.role == 2) Lan.note("进图完成（客户端：航线/毒圈跟服务端）");
        if (bounce == 2) audioFix = feAudioSeen ? "已经过大厅初始化，本局应有声音" : "已回到地图";
        else if (!feAudioSeen && bounce == 0) brLoadedAt = now;
    }

    /** v2.16 全员无敌（房主）：伤害在房主这边算，God 只是把自己人物的 bCanBeDamaged 关掉。
     *  这里用 set Character bCanBeDamaged 0 把所有人物（含后加入、重生的）都关掉，每 3 秒补一次。 */
    public static volatile boolean allGod;
    static Thread godT;
    public static String setAllGod(boolean on) {
        allGod = on;
        if (!on) {
            exec("set Character bCanBeDamaged 1");
            if (godOn) exec("God");   // 关全员会把房主自己的也打开，自己的无敌重新开回去
            godOn = false;
            return "全员无敌已关";
        }
        exec("EnableCheats");
        if (godT == null || !godT.isAlive()) {
            godT = new Thread(() -> {
                while (allGod) {
                    try {
                        exec("set Character bCanBeDamaged 0");
                        Thread.sleep(3000);
                    } catch (Throwable e) { break; }
                }
            }, "zf-allgod");
            godT.start();
        }
        LogCap.note("全员无敌：开");
        return "ok";
    }
}
