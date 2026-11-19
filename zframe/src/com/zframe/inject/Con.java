package com.zframe.inject;

import android.app.Activity;

import java.io.*;
import java.lang.reflect.Method;
import java.util.*;

/**
 * 通过 UE4 自带的 GameActivity.nativeConsoleCommand 给引擎发控制台命令（纯 Java，不需要 root）。
 * 另外盯着引擎日志：地图里一出现 “StreamingServerLayer Add 某关卡”，就自动用 StreamLevelIn 把这些关卡
 * （门、物资刷新点、载具刷新点……）真正加载出来。
 */
public class Con {
    static volatile Activity act;
    static volatile String last = "";
    static final List<String> history = Collections.synchronizedList(new ArrayList<String>());

    static void setActivity(Activity a) { act = a; }

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

    // ===== v1.32 刷车 =====
    // 游戏包是腾讯 IFS 格式，扫不到车的蓝图名；改用游戏自己的 SpawnVehicle(车型编号, 位置, 朝向)（控制器上的 GM 命令，
    // 里面走 GameMode 的载具管理器按编号刷车）。位置用 getall 读自己人物胶囊体的坐标，车放在身边 5 米。
    // 车型编号（ENZVehicleType）：1 吉普(封顶) 2 轿车 4 吉普(铁顶) 5 吉普(敞篷) 6 蹦蹦 8 摩托 9 三轮摩托 17 防弹吉普
    static final java.util.LinkedHashSet<String> carClasses = new java.util.LinkedHashSet<>();
    static final int[] CAR_TYPES = {2, 5, 1, 4, 6, 8, 9, 17};
    static final String[] CAR_NAMES = {"", "吉普", "轿车", "船", "吉普", "敞篷吉普", "蹦蹦", "", "摩托", "三轮摩托", "", "", "", "", "", "", "", "防弹吉普"};
    static volatile int carIdx = 0, carTry;
    static volatile String myLoc; static volatile long myLocAt;
    static volatile int carCount = -1, carCountNew;
    static volatile String carWait; static volatile boolean carFail, carOk;
    static final java.util.regex.Pattern CAR = java.util.regex.Pattern.compile("\\d+\\) (\\w+_C) \\S+\\.bHidden = ");
    static final java.util.regex.Pattern LOC = java.util.regex.Pattern.compile("BP_NZBRCharacter\\w*\\.\\w+\\.RelativeLocation = \\(X=(-?[0-9.]+),Y=(-?[0-9.]+),Z=(-?[0-9.]+)\\)");
    static void spawnCar(final Activity a) {
        if (brMap == null) { GameOverlay.toast(a, "进地图后再刷车"); return; }
        carTry = 0;
        tryCar(a);
    }
    static void tryCar(final Activity a) {
        if (carTry >= 4) { GameOverlay.toast(a, "试了 " + carTry + " 种车都没刷出来，把日志发我"); return; }
        carTry++;
        final int t = CAR_TYPES[carIdx++ % CAR_TYPES.length];
        myLoc = null; carCountNew = 0;
        exec("EnableCheats");
        exec("getall CapsuleComponent RelativeLocation");
        exec("getall WheeledVehicle bHidden");
        final int before = carClasses.size();
        final android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
        h.postDelayed(() -> {
            String loc = myLoc;
            if (loc == null) { LogCap.note("刷车: 没读到人物坐标"); GameOverlay.toast(a, "没读到你的位置，把日志发我"); return; }
            String[] p = loc.split(" ");
            double x = Double.parseDouble(p[0]) + 500, y = Double.parseDouble(p[1]), z = Double.parseDouble(p[2]) + 150;
            String v = String.format(java.util.Locale.US, "(X=%.1f,Y=%.1f,Z=%.1f)", x, y, z);
            String c = "SpawnVehicle " + t + " " + v + " (Pitch=0.000000,Yaw=0.000000,Roll=0.000000)";
            boolean ok = exec(c);
            LogCap.note("刷车: " + c + " → " + ok);
            h.postDelayed(() -> {
                carCountNew = 0;
                exec("getall WheeledVehicle bHidden");
                exec("getall NZVehicleMotoCycle bHidden");
                h.postDelayed(() -> {
                    LogCap.note("刷车后场上车辆行数 " + carCountNew);
                    if (carCountNew > 0) GameOverlay.toast(a, "已刷车：" + CAR_NAMES[t] + "（在你东边 5 米）");
                    else { LogCap.note("刷车没出来，换下一种 " + t); tryCar(a); }
                }, 1200);
            }, 1200);
        }, 1200);
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

    static void startWatcher(final android.content.Context ctx) {
        Thread t = new Thread(new Runnable() { public void run() {
            File cur = null; long pos = 0; StringBuilder part = new StringBuilder();
            while (true) {
                try {
                    if (cur == null || !cur.exists() || cur.length() < pos) {
                        cur = findUeLog(ctx); pos = 0; part.setLength(0);
                        // v1.18：上一局留下的旧日志不读（以前会把旧日志当成“引擎已启动”，引擎没起来就发命令→闪退）
                        if (cur != null && cur.lastModified() < bootAt) pos = cur.length();
                    }
                    if (cur != null && cur.length() > pos) {
                        try (RandomAccessFile f = new RandomAccessFile(cur, "r")) {
                            f.seek(pos);
                            byte[] b = new byte[(int) Math.min(1 << 20, cur.length() - pos)];
                            f.readFully(b);
                            pos += b.length;
                            boolean xor = !plain;
                            if (pos == b.length) { plain = new String(b, 0, Math.min(b.length, 4000), "ISO-8859-1").contains("Log"); xor = !plain; }
                            if (xor) for (int i = 0; i < b.length; i++) b[i] ^= 0x73;
                            part.append(new String(b, "UTF-8"));
                            int nl;
                            while ((nl = part.indexOf("\n")) >= 0) { line(part.substring(0, nl)); part.delete(0, nl + 1); }
                        }
                    }
                    long now = System.currentTimeMillis();
                    // 只有新日志里出现 “LogLoad: Took” 才算引擎真正就绪
                    tickTweaks(now);
                    // v2.20：联机时不用控制台 StreamLevelIn（房主的 ServerOnly 层由游戏自己加载，见 Loot）
                    if (Lan.role != 0) { /* 不发命令 */ }
                    else if (autoOn && lastAddAt > 0 && now - lastAddAt > 3000 && sentAt < lastAddAt) { passes = 1; loadServerLevels("自动"); }
                    else if (autoOn && passes == 1 && sentAt > lastAddAt && now - sentAt > 15000) { passes = 2; loadServerLevels("自动(第2遍)"); }
                } catch (Throwable e) { LogCap.note("日志监视出错 " + e); }
                try { Thread.sleep(1000); } catch (InterruptedException e) { return; }
            }
        }}, "zframe-uelog");
        t.setDaemon(true);
        t.start();
    }

    static boolean plain;

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

    static void runTweaks(String why) {
        int n = 0;
        if (fps == 0) loadFps();
        applyFps();
        for (String c : TWEAKS) if (exec(c)) n++;
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
            /* v2.06 用户：不要每次都存；v2.09b 只在上次联机加载闪退时自动存 */ new Thread(Server::autoCrashSave).start(); }
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
        if (!startupLobby && audioAuto && !feAudioSeen && bounce == 0 && brMap == null && engineSeenAt > 0 && now - engineSeenAt > 3000 && act != null) {
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
        if (bounce == 1 && feAudioSeen && feSeenAt > 0 && now - feSeenAt > 4000) {
            bounce = 2;
            String m = pendingMap != null ? pendingMap : brMap != null ? brMap : MAP_SAVAGE;
            pendingMap = null;
            audioFix = "大厅音频已初始化，正在回到地图";
            LogCap.note("声音修复: 大厅音频已初始化，回到 " + m);
            exec("open " + m);
        }
    }
    static volatile boolean manualBounce;

    static volatile String circle = null; // "x y"
    static volatile long startAt;
    /** 地图编号来自日志：1 老海岛，3 新海岛（GameMapId=3），4 雨林（GameMapId=4），5 训练场 */
    static void startGame(final int id) {
        // v3.22 用户：单机新海岛也用战术模式（和联机服务端开房一样：?game=战术模式蓝图）
        final String m = id == 1 ? MAP_ERANGEL : id == 3 ? MAP_ERANGEL2 + "?game=" + ModeProbe.cache.get("Props") : id == 4 ? MAP_SAVAGE : null;
        LogCap.note("游戏开始按钮: 地图编号 " + id + " → " + m);
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
    static void line(String l) {
        Prop.line(l);
        Flare.line(l);
        if (l.contains("Game class is '")) ModeProbe.gameClass(l);
        if (l.contains("BP_NZWeap_") || l.contains("WeaponBase/W")) {
            java.util.regex.Matcher m = WEAP.matcher(l);
            if (m.find()) { String w = m.group(1) != null ? m.group(1) : m.group(2); if (!"EmptyHand".equals(w)) { lastWeap = w; lastWeapAt = System.currentTimeMillis(); } }
        }
        if (l.contains(".bHidden = ")) {
            java.util.regex.Matcher m = CAR.matcher(l);
            if (m.find()) { carCountNew++; synchronized (carClasses) { if (carClasses.add(m.group(1))) LogCap.note("车型: " + m.group(1)); } }
        }
        if (l.contains(".RelativeLocation = ")) {
            java.util.regex.Matcher m = LOC.matcher(l);
            if (m.find()) { myLoc = m.group(1) + " " + m.group(2) + " " + m.group(3); myLocAt = System.currentTimeMillis(); }
        }
        if (l.contains("SpawnVehicle") || l.contains("VehicleSpawn") || l.contains("NZBRVehicleManager")) LogCap.note("刷车日志: " + l.trim());
        // v1.32：游戏自带「开始游戏」→ 按选的地图直接进图
        int sm = l.indexOf("RequestStartSingleMatch add_maps_chose:");
        if (sm >= 0) {
            try {
                int id = Integer.parseInt(l.substring(sm + 39).trim().split("\\D")[0]);
                long now = System.currentTimeMillis();
                if (now - startAt > 4000) { startAt = now; startGame(id); }
            } catch (Throwable ignored) {}
        }
        Lan.line(l);
        Gfx.line(l);
        int av = l.indexOf("OnAvatarAssembler In Items");
        if (av >= 0) LogCap.note("身上装备编号: " + l.substring(av).trim());
        if (engineSeenAt == 0 && l.contains("LogLoad: Took")) { engineSeenAt = System.currentTimeMillis(); LogCap.note("引擎就绪，10秒后发送设置"); loadAudioPref(); Gfx.start(); }
        if (l.contains("UNZFrontEndAudioManager: init") || l.contains("UNZPlatformAudioHelper::OnInitialize")) {
            if (!feAudioSeen) { feAudioSeen = true; feSeenAt = System.currentTimeMillis(); LogCap.note("声音修复: 看到大厅音频初始化"); }
            if (bounce != 1) audioFix = "大厅音频已初始化";
        }
        // v2.19 用户：仓库衣服刚进大厅没有、要等服务端开 → 单机版一看到开始加载大厅就开服务端（不等加载完 + 2.5 秒）
        if (Ed.single() && !Server.on && !earlySrv && l.contains("LoadMap") && (l.contains("RoleHall") || l.contains("FrontEnd")) && !l.contains("LogLoad: Took")) {
            earlySrv = true;
            new Thread(() -> { if (Lan.role != 0 || Lan.busy || inMatch) { earlySrv = false; return; } LogCap.note("大厅开始加载，提前开服务端 → " + Server.start()); }, "zf-srv0").start();
        }
        int tk = l.indexOf("LogLoad: Took");
        if (tk >= 0) {
            int lm = l.indexOf("LoadMap(", tk);
            if (lm >= 0) {
                String m = l.substring(lm + 8); int e = m.indexOf(')'); if (e > 0) m = m.substring(0, e);
                int q = m.indexOf('?'); if (q > 0) m = m.substring(0, q);
                boolean lobby = m.contains("RoleHall") || m.contains("FrontEnd");
                if (m.startsWith("/Game/Maps/")) { inMatch = !lobby; if (inMatch) { MapMark.start(); Jetpack.start(); Walls.clientWatch(); if (Ed.ED == 1) HostMatch.start(); Mods.matchZone(); /* v3.21：每次进图按存的模式重打不上飞机/不刷圈 + 阶段守护 */ } GameOverlay.refreshAny(); if (lobby || inMatch) GameOverlay.lobbyReady(); if (coatAt == 0) coatAt = System.currentTimeMillis(); }
                if (lobby) {   // v2.05：回大厅 = 不在联机里；单机默认刷人机；服务端自动开（不再显示开关）
                    Gfx.unlite();
                    Lan.role = 0; Bots.auto = Bots.want;
                    if (!Server.on && Ed.single()) new Thread(() -> { try { Thread.sleep(Server.on ? 0 : 300); } catch (InterruptedException ignored) {} if (Lan.role != 0 || Lan.busy) return; try { Thread.sleep(0); } catch (InterruptedException ignored) {} LogCap.note("自动开服务端 → " + Server.start()); GameOverlay.refreshAny(); }, "zf-srv").start();
                }
                if (m.startsWith("/Game/Maps/") && !lobby) {
                    brMap = m;
                    if (Lan.role == 0) { Bots.matchLoaded(); Flow.matchLoaded(m); }
                    else if (Lan.role == 1) { Flow.matchLoaded(m); Gfx.lite(); Lan.note("进图完成（服务端：航线/毒圈照单机改；减负）"); }
                    else Lan.note("进图完成（客户端：航线/毒圈跟服务端）");   // v1.93 不再自动读车表（车编号写死）
                    if (bounce == 2) audioFix = feAudioSeen ? "已经过大厅初始化，本局应有声音" : "已回到地图";
                    else if (!feAudioSeen && bounce == 0) brLoadedAt = System.currentTimeMillis();
                }
            }
        }
        if (l.contains("AkAudio") || l.contains("Audiokinetic") || l.contains("Wwise(R)")) LogCap.note("声音日志: " + l.trim());
        int c = l.indexOf("BlueCircle: ");
        if (c >= 0) { try {
            String[] a = l.substring(c + 12).split(",");
            circle = Math.round(Double.parseDouble(a[2].trim())) + " " + Math.round(Double.parseDouble(a[3].trim()));
        } catch (Throwable ignored) {} }
        if (l.contains("ApplyFrameRateOption")) fpsAt = System.currentTimeMillis();
        // v1.85 诊断：导航网格大小、人机相关日志
        int nv = l.indexOf("RecastNavMeshSizeBytes=");
        if (nv >= 0) navInfo = (navInfo.length() > 300 ? "" : navInfo + " | ") + l.substring(nv, Math.min(l.length(), nv + 120)).trim();
        // v1.86 诊断：航线、毒圈数据（每圈等待/缩圈时间、半径）、开局倒计时
        if (!l.contains("GVoice") && (l.contains("InitArea[") || l.contains("AirLine") || l.contains("RestrictedArea") || l.contains("Round") || l.contains("AirlineRadius") || l.contains("RadiusForAirLine") || l.contains("JumpRadiusScale") || l.contains("FlightNumber") || l.contains("RestrictedAreaInfos[") || l.contains("CountDownTime") || l.contains("SetGamePhase") || l.contains("GamePhase"))) {
            synchronized (flowLog) { if (flowLog.size() >= 40) flowLog.remove(0); flowLog.add(l.trim().length() > 260 ? l.trim().substring(0, 260) : l.trim()); }
        }
        if (l.contains("Drop") || l.contains("PickUpWrapper") || l.contains("PickUp") && l.contains("Spawn") || l.contains("Discard")) {
            synchronized (dropLog) { if (dropLog.size() >= 40) dropLog.remove(0); dropLog.add(l.trim().length() > 260 ? l.trim().substring(0, 260) : l.trim()); }
        }
        if (l.contains("BehaviorTree") || l.contains("Add a Bot") || l.contains("AddBot") || l.contains("DropOneAI") || l.contains("NumBots") || l.contains("NavigationSubLevel")) {
            synchronized (botLog) { if (botLog.size() >= 30) botLog.remove(0); botLog.add(l.trim().length() > 220 ? l.trim().substring(0, 220) : l.trim()); }
        }
        Loot.line(l);   // v2.20 房主：服务器物资层/门/空投
        if (l.contains("CalcAirLineByRestrictedAreaInfo StartLoc:")) Flow.airLog(l);   // v1.94：航线原始坐标，按值去内存里找
        if (l.contains("NZBRGameState.cpp:304")) brAt = System.currentTimeMillis();
        int i = l.indexOf("LogNet: Browse: ");
        if (i >= 0 && l.contains("/Game/Startup/StartupMap")) { GameOverlay.loadingPage(); GameOverlay.srvUpdatePage(); }   // v3.22 三个包都显示更新进度条+公告   // v2.70 探测已完成（更新页只有默认对象，没实例），不再跑 UpdProbe   // v2.59 游戏自己的加载页
        if (i >= 0) { Flow.natPrepare(l); if (l.contains("/Game/Maps/") && !l.contains("FrontEnd") && !l.contains("RoleHall")) Prop.matchLoad(); Bots.newMatch(); Flow.newMatch(); Loot.newMatch(); synchronized (serverLevels) { serverLevels.clear(); } circle = null; godOn = false; tpGen++; browseAt = System.currentTimeMillis(); layerStatus = "正在进图…"; inMatch = false; GameOverlay.mapLeaving(); GameOverlay.forgetPawn(); GameOverlay.newMatch(); Mods.resetMatch(); GameOverlay.refreshAny(); return; }
        i = l.indexOf("StreamingServerLayer Add ");
        if (i >= 0) {
            String p = l.substring(i + "StreamingServerLayer Add ".length()).trim();
            if (p.startsWith("other ")) p = p.substring(6).trim();
            if (p.startsWith("/Game/")) {
                synchronized (serverLevels) { serverLevels.add(p); }
                lastAddAt = System.currentTimeMillis();
                layerStatus = "发现服务器关卡 " + serverLevels.size() + " 个，准备加载";
            }
        }
    }

    static File findUeLog(android.content.Context ctx) {
        List<File> all = new ArrayList<>();
        findLogs(ctx.getExternalFilesDir(null), all, 0);
        findLogs(ctx.getFilesDir(), all, 0);
        findLogs(new File("/storage/emulated/0/UE4Game"), all, 0);
        File best = null;
        for (File f : all) if (f.getName().equals("PUBGM.log") || (f.getName().endsWith(".log") && !f.getName().contains("backup") && f.getPath().contains("Logs"))) {
            if (best == null || f.lastModified() > best.lastModified()) best = f;
        }
        return best;
    }
    static void findLogs(File d, java.util.List<File> out, int depth) {
        if (d == null || depth > 8) return;
        File[] fs = d.listFiles();
        if (fs == null) return;
        for (File f : fs) {
            if (f.isDirectory()) findLogs(f, out, depth + 1);
            else if (f.getName().endsWith(".log") && f.length() > 0) out.add(f);
        }
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
                boolean first = true;
                while (allGod) {
                    try {
                        exec("set Character bCanBeDamaged 0");
                        if (first) { exec("getall Character bCanBeDamaged"); first = false; }
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
