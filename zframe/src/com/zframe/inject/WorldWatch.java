package com.zframe.inject;

import java.io.RandomAccessFile;

/**
 * v4.07：唯一的「游戏状态来源」。游戏日志只有手机设置里开了日志才有（普通手机没有）→ 不再读游戏日志，
 * 进图/回大厅/开机页全部从引擎内存里读。每 0.4 秒：
 *   GWorld（libUE4 .bss 0x53757b0，UWorldProxy 首字 = UWorld*）
 *     RE：UEngine::LoadMap 0x2e0c0f8 在 0x2e0d6c4 `str 新世界,[GWorld]`；0x2519fd8 是 `return GWorld`；
 *         GetWorldFromContextObject 0x2e05764 兜底返回它；UGameEngine::Tick 0x29d5c04 也写它。
 *   世界 +0x744 UnpausedTimeSeconds（UGameplayStatics::GetUnpausedTimeSeconds 0x29fda50 读 +0x744；+0x740 TimeSeconds）
 *     → 世界开始 Tick 才会涨 = LoadMap 已经走完（等同以前日志里的 "LogLoad: Took … LoadMap(…)"）。
 *   世界 +0x98 GameState、+0x94 AuthorityGameMode、+0x24 NetDriver（反射表核对：0x50453b4/0x50453dc/0x50456d4），
 *   GameStateBase +0x2bc/+0x2c0 PlayerArray（反射 0x4f9d384）。
 *   地图名：UWorld 里的 FURL.Map（FString，UTF-32），运行时在世界对象前 0xC00 字节里找内容以 /Game/ 开头的 FString，偏移找到后缓存；
 *   找不到再用名字表读世界 Outer（包）的名字。
 * 事件（只从这里发，去重：每个世界最多一次 browse、一次 loaded；切图时 leave 一次）：
 *   Con.onLeave()        GWorld 变成空 / 换了世界（以前日志 "LogNet: Browse" 的收起菜单部分）
 *   Con.onBrowse(map)    新世界出现、地图名读到（以前 Browse 行的重置部分）
 *   Con.onLoaded(map)    新世界开始走时间（以前 LogLoad: Took 行）
 * 同一地址被复用（旧世界回收后新世界分到同一块内存）：时间倒退或地图名变了也算换世界。
 */
final class WorldWatch {
    static final long GWORLD = 0x53757b0L;
    static final int W_NETDRV = 0x24, W_GM = 0x94, W_GS = 0x98, W_TIME = 0x744, GS_PLAYERS = 0x2bc;

    static volatile long world, seenAt, loadedAt;
    static volatile String map = "";
    static volatile boolean browsed, loaded;
    static volatile int urlOff = -1, players = -1, worlds;
    static volatile float lastT = -1f;
    static volatile long usedOpenAt;
    static volatile String state = "世界状态：没开始", how = "";
    static volatile boolean leaveSent = true, mapFromUrl;
    /** 调试：模拟「没有游戏日志」已经是常态（v4.07 起不读日志），这里只记录本次运行发过的事件，诊断里看 */
    static final java.util.ArrayDeque<String> events = new java.util.ArrayDeque<>();
    static Thread th;

    static synchronized void start() {
        if (th != null) return;
        th = new Thread(() -> {
            long errAt = 0;
            while (true) {
                try { tick(); }
                catch (Throwable t) { long now = System.currentTimeMillis(); if (now - errAt > 30000) { errAt = now; LogCap.note("世界状态：出错 " + t); } }
                try { Thread.sleep(400); } catch (InterruptedException e) { return; }
            }
        }, "zf-world");
        th.setDaemon(true);
        th.start();
    }

    static void ev(String s) {
        String t = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(new java.util.Date());
        synchronized (events) { events.addLast(t + " " + s); while (events.size() > 40) events.removeFirst(); }
        LogCap.note("世界状态：" + s);
    }
    static String events() { StringBuilder sb = new StringBuilder(); synchronized (events) { for (String s : events) sb.append("\n  ").append(s); } return sb.toString(); }

    static void tick() throws Exception {
        long b = Mods.base();
        if (b == 0) { state = "世界状态：找不到 libUE4"; return; }
        long now = System.currentTimeMillis();
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
            long w = Server.rd(m, b + GWORLD);
            if (w != 0 && !Mods.isObj(m, b, w)) w = 0;   // 不像 UObject（虚表不在 libUE4）就当没有
            float t = w == 0 ? -1f : Mods.f(m, w + W_TIME);
            if (Float.isNaN(t) || t < 0 || t > 1e8f) t = 0f;
            String mp = w == 0 ? null : mapName(m, b, w);
            boolean changed = w != world;
            // 地址复用：时间倒退明显、或已读到的地图名变了
            if (!changed && w != 0 && loaded && t + 1f < lastT) { changed = true; how = "同地址，时间倒退 " + lastT + "→" + t; }
            if (!changed && w != 0 && browsed && mapFromUrl && mp != null && !mp.equals(map)) { changed = true; how = "同地址，地图名变了"; }
            if (changed) {
                if (!leaveSent && (browsed || world != 0)) { leaveSent = true; ev("离开 " + map); Con.onLeave(); }
                world = w; seenAt = now; browsed = false; loaded = false; lastT = t; map = ""; players = -1;
                if (w == 0) { state = "世界状态：切图中（GWorld 空）"; return; }
                worlds++;
            }
            if (w == 0) return;
            if (!browsed) {
                mapFromUrl = mp != null;
                // v4.09：URL 里读不到 → 这个世界是我们 open 之后出现的第一个世界，就用开图命令里的地图名（每条命令只用一次，防游戏后面自己切图被认错）
                long oa = Con.lastOpenAt;
                if (mp == null && now - seenAt > 1500 && oa > 0 && oa != usedOpenAt && seenAt >= oa - 500 && seenAt - oa < 120000) {
                    usedOpenAt = oa; mp = strip(Con.lastOpen); how = "地图名来自开图命令";
                }
                if (mp == null && now - seenAt > 6000) mp = outerName(m, b, w);
                if (mp == null) { state = "世界状态：新世界 " + Long.toHexString(w) + "，还没读到地图名"; if (now - seenAt < 30000) { lastT = t; return; } mp = "?"; }
                map = mp; browsed = true; leaveSent = false;
                ev("新世界 " + mp + "（" + Long.toHexString(w) + "，URL 偏移 " + (urlOff < 0 ? "无" : "0x" + Integer.toHexString(urlOff)) + "）");
                Con.onBrowse(mp);
            }
            if (!loaded) {
                boolean ticking = t > 0.05f && t > lastT && lastT >= 0;
                boolean late = now - seenAt > 45000;   // 45 秒时间都没走：偏移不对也照样放行，不卡流程
                if (ticking || late) {
                    loaded = true; loadedAt = now;
                    ev("加载完 " + map + (late && !ticking ? "（时间没走，45 秒兜底）" : "（" + (now - seenAt) + " ms，世界时间 " + t + "）"));
                    Con.onLoaded(map);
                }
            }
            lastT = t;
            if (loaded) watchPlayers(m, b, w);
            state = "世界状态：" + map + (loaded ? " 已加载" : " 加载中") + "，世界 " + Long.toHexString(w) + "，时间 " + Math.round(t) + " 秒，玩家 " + players;
        }
    }

    /** 服务端：GameState.PlayerArray 人数变多 = 有人加入（以前日志 "Join succeeded"） */
    static void watchPlayers(RandomAccessFile m, long b, long w) {
        try {
            long gs = Server.rd(m, w + W_GS);
            if (!Mods.isObj(m, b, gs)) return;
            int n = (int) Server.rd(m, gs + GS_PLAYERS + 4);
            if (n < 0 || n > 200) return;
            int old = players; players = n;
            if (old >= 0 && n > old && Lan.role == 1 && Con.inMatch) { ev("玩家 " + old + "→" + n + "（有人加入）"); Crew.joined(); }
        } catch (Throwable ignored) {}
    }

    /** FURL.Map：在世界对象里找 /Game/ 开头的 FString（UTF-32，含结尾 0） */
    static String mapName(RandomAccessFile m, long b, long w) {
        int o = urlOff;
        if (o >= 0) { String s = fstr(m, w + o); if (s != null && s.startsWith("/")) return strip(s); }
        try {
            byte[] raw = new byte[0xC00]; m.seek(w); m.readFully(raw);
            java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(raw).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            int found = -1; String best = null;
            for (int k = 0x20; k + 12 <= raw.length; k += 4) {
                long d = bb.getInt(k) & 0xffffffffL; int n = bb.getInt(k + 4), mx = bb.getInt(k + 8);
                if (d < 0x10000 || n < 8 || n > 400 || mx < n || mx > 2048) continue;
                String s = fstr(m, w + k);
                if (s == null || !s.startsWith("/Game/")) continue;
                if (best == null || (s.startsWith("/Game/Maps/") || s.startsWith("/Game/Startup/")) && !(best.startsWith("/Game/Maps/") || best.startsWith("/Game/Startup/"))) { best = s; found = k; }
                if (best.startsWith("/Game/Maps/") || best.startsWith("/Game/Startup/")) break;
            }
            if (found >= 0) { urlOff = found; return strip(best); }
        } catch (Throwable ignored) {}
        return null;
    }
    static String strip(String s) { int q = s.indexOf('?'); return q > 0 ? s.substring(0, q) : s; }
    static String fstr(RandomAccessFile m, long a) {
        try {
            long d = Server.rd(m, a); int n = (int) Server.rd(m, a + 4), mx = (int) Server.rd(m, a + 8);
            if (d < 0x10000 || n < 2 || n > 400 || mx < n || mx > 2048) return null;
            byte[] x = new byte[n * 4]; m.seek(d); m.readFully(x);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < n - 1; i++) {
                int c = (x[i * 4] & 255) | (x[i * 4 + 1] & 255) << 8 | (x[i * 4 + 2] & 255) << 16 | (x[i * 4 + 3] & 255) << 24;
                if (c < 32 || c > 126) return null;
                sb.append((char) c);
            }
            if ((x[(n - 1) * 4] | x[(n - 1) * 4 + 1] | x[(n - 1) * 4 + 2] | x[(n - 1) * 4 + 3]) != 0) return null;
            return sb.toString();
        } catch (Throwable t) { return null; }
    }
    /** 兜底：世界的 Outer 是地图包，包名就是 /Game/Maps/… 路径（要名字表；服务端只用已经找好的，不为这个去扫） */
    static String outerName(RandomAccessFile m, long b, long w) {
        try {
            Veh.Names nm = Veh.Names.cached;
            if (nm == null && Ed.ED != 1) nm = Veh.Names.get();
            if (nm == null) return null;
            long pkg = Server.rd(m, w + 0x18);
            String s = Alive.objName(m, nm, pkg);
            if (s != null && s.startsWith("/")) { how = "地图名来自包名"; return s; }
        } catch (Throwable ignored) {}
        return null;
    }

    /** 当前世界的 GameMode 类名（只在名字表已经有了时；没有返回 null） */
    static String gameModeClass() {
        try {
            Veh.Names nm = Veh.Names.cached; long w = world, b = Mods.base();
            if (nm == null || w == 0 || b == 0) return null;
            try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
                long gm = Server.rd(m, w + W_GM);
                if (!Mods.isObj(m, b, gm)) return null;
                return Alive.clsName(m, nm, gm);
            }
        } catch (Throwable t) { return null; }
    }

    /** 对象表里第一个类名正好是 cls 的实例（跳过类默认对象）。整张表读一次，只在需要时调（不在服务端循环里用） */
    static long firstInstance(String cls) {
        try {
            long b = Mods.base(); if (b == 0) return 0;
            Veh.Names nm = Veh.Names.get(); if (nm == null) return 0;
            try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
                long g = Server.rd(m, b + 0x51250acL), objs = Server.rd(m, g + 0x10); int num = (int) Server.rd(m, g + 0x18);
                if (objs < 0x10000 || num <= 0 || num > 0x200000) return 0;
                byte[] raw = new byte[num * 16]; m.seek(objs); m.readFully(raw);
                java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(raw).order(java.nio.ByteOrder.LITTLE_ENDIAN);
                java.util.HashMap<Long, Boolean> hit = new java.util.HashMap<>();
                for (int i = 0; i < num; i++) {
                    long o = bb.getInt(i * 16) & 0xffffffffL; int fl = bb.getInt(i * 16 + 4);
                    if (o < 0x10000 || (fl & 0x20000000) != 0) continue;
                    long c = Server.rd(m, o + 0xc); if (c < 0x10000) continue;
                    Boolean h = hit.get(c);
                    if (h == null) { String n = Alive.objName(m, nm, c); h = cls.equals(n); hit.put(c, h); }
                    if (!h) continue;
                    if ((Server.rd(m, o + 4) & 0x10) != 0) continue;   // RF_ClassDefaultObject
                    return o;
                }
            }
        } catch (Throwable ignored) {}
        return 0;
    }

    /** v4.09：菜单里一行（只在内存）：世界/地图/是否加载完/模式/最近一次开始游戏 */
    static String shortText() {
        String mp = map.isEmpty() ? "-" : map.substring(map.lastIndexOf('/') + 1);
        long w = world;
        String st = w == 0 ? "无世界" : !browsed ? "读地图名中" : loaded ? "已加载" : "加载中";
        return "世界 " + (w == 0 ? "0" : Long.toHexString(w)) + " " + mp + " " + st + "（第" + worlds + "个" + (how.isEmpty() ? "" : "，" + how) + "）"
            + "；模式 " + (Con.training ? "训练岛" : ModeProbe.propsMode ? "战术" : Con.inMatch ? "对局" : "大厅/无")
            + "；15001 " + Con.lastStart + "；触发 " + Con.lastTrig + "；服务端 " + (Server.on ? "开" : "关")
            + (Con.training ? "；保命 " + (TrainSafe.running ? "开 补血" + TrainSafe.refills + " 憋气" + TrainSafe.breathResets + " 车" + TrainSafe.vehRepairs : "关") : "");
    }

    static String text() {
        return state + (how.isEmpty() ? "" : "（" + how + "）") + "；GWorld=libUE4+0x" + Long.toHexString(GWORLD) + "；本次运行世界 " + worlds + " 个；事件：" + events();
    }
}
