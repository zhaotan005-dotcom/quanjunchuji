package com.zframe.inject;

import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;

/**
 * v2.96：娱乐模式开房的根修。
 * v2.92~2.95 用 ?game=/Script/NZBattleRoyaleGame.NZBRLegionGameMode（C++ 底层类）开房 → 加载第 5 秒闪退（两次复现，日志都停在
 * 「SetViewTarget NewViewTarget = NZPlayerController_0」）。原因（libUE4 反汇编）：ANZGameMode 构造 0xc786c8 只把
 * HUD/GameState/PlayerState/PlayerController 设成 NZGame 的基础类（ANZHUD/ANZGameState/ANZPlayerState/ANZPlayerController），
 * ANZBattleRoyaleGameMode(0x1007b7c)、ANZBRLegionGameMode(0x1062cd4)、ANZBRBFAGameMode(0x105253c) 的构造都不改这几个类，
 * 真正能玩的配置（BP 控制器/人物/HUD/对局状态）在各模式的蓝图子类里（正常海岛用的是 BP_NZBRIslandGameMode_C）。
 * 直接实例化 C++ 类 = 吃鸡逻辑拿到的是基础 GameState/控制器 → 闪退。
 * 所以开房必须用该模式的蓝图类。蓝图路径不在 libUE4 里，也不解密 pak：运行时从游戏自己的名字表（GNames）里找
 * 含模式关键字且像 GameMode 蓝图的名字（/Game/... 包名或对象路径），拼成 ?game=/Game/.../X.X_C；找不到就不开（底层类必闪退），
 * 把找到的相关名字写进联机日志并自动上传 mode-*。
 */
public class ModeProbe {
    public static volatile String state = "娱乐模式：没查过";
    /** v3.01：当前对局是战术模式（BP_NZBattleRoyalePropsGameMode，游戏自己有道具栏）→ 不放自制道具键 */
    static volatile boolean propsMode;
    static void gameClass(String l) {
        int i = l.indexOf("Game class is '"); if (i < 0) return;
        String c = l.substring(i + 15); int e = c.indexOf('\''); if (e > 0) c = c.substring(0, e);
        propsMode = c.contains("Props");
        LogCap.note("对局模式类：" + c + (propsMode ? "（战术模式，用游戏原生道具栏）" : ""));
    }
    static volatile boolean busy;

    /** key：Legion / BFA；label：游戏里的中文名 */
    static String host(final String key, final String label, final String map) {
        if (busy) return "正在查" + label + "的模式蓝图，稍等";
        busy = true;
        final boolean known = cache.containsKey(key);
        state = known ? "娱乐模式：开「" + label + "」…" : "娱乐模式：正在查「" + label + "」的模式蓝图…";
        new Thread(() -> {
            String msg;
            try {
                ArrayList<String> hits = new ArrayList<>();
                String cls = cache.get(key);
                if (cls == null) { cls = find(key, hits); if (cls != null) cache.put(key, cls); }
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < hits.size() && i < 40; i++) sb.append(i == 0 ? "" : " | ").append(hits.get(i));
                if (!known) Lan.note("模式蓝图「" + label + "」(" + key + ") 名字表命中 " + hits.size() + "：" + (sb.length() == 0 ? "无" : sb));
                if (cls == null) {
                    msg = "这个安装包里没找到「" + label + "」的模式蓝图（只有 C++ 底层类，直接开必闪退），没开房。已传日志";
                    state = "娱乐模式：「" + label + "」没找到蓝图（命中 " + hits.size() + " 个相关名字），没开房";
                } else {
                    state = "娱乐模式：「" + label + "」用 " + cls;
                    final String url = map + "?game=" + cls;
                    final String[] r = new String[1];
                    final Object lock = new Object();
                    GameOverlay.MAIN.post(() -> { synchronized (lock) { r[0] = Lan.host(url); lock.notifyAll(); } });
                    synchronized (lock) { if (r[0] == null) lock.wait(5000); }
                    msg = "「" + label + "」" + (r[0] == null ? "开房命令没回应" : r[0]);
                }
            } catch (Throwable e) {
                msg = "查模式蓝图出错 " + e; state = "娱乐模式：" + msg;
            } finally { busy = false; }
            Lan.note(state);
            GameOverlay.toastAny(msg);
            Lan.autoUp("mode");
            GameOverlay.refreshAny();
        }, "zf-mode").start();
        return known ? "正在开「" + label + "」…" : "正在查「" + label + "」的模式蓝图…";
    }

    /** 扫名字表：收集含 key 的名字（hits），返回能当 ?game= 用的蓝图类路径，没有返回 null */
    static String find(String key, ArrayList<String> hits) throws Exception {
        Veh.Names n = Veh.Names.get();
        if (n == null) { hits.add("（名字表没找到）"); return null; }
        String[] ks = key.toLowerCase(java.util.Locale.US).split("\\|");   // v3.00：多个关键字（三军对垒的蓝图可能叫 Camp/ThreeArmy，不一定带 Legion）
        String best = null; int bestScore = -1;
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
            int nChunks = n.chunks < 0 ? 1 : 64;
            byte[] pb = new byte[16384 * 4]; int[] ptr = new int[16384];
            byte[] eb = new byte[8 + 160];
            for (int c = 0; c < nChunks; c++) {
                long chunk = n.chunks < 0 ? -n.chunks : Server.rd(m, n.chunks + 4L * c);
                if (chunk == 0) break;
                try { m.seek(chunk); m.readFully(pb); } catch (Throwable t) { break; }
                ByteBuffer.wrap(pb).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().get(ptr);
                for (int i = 0; i < 16384; i++) {
                    long e = ptr[i] & 0xffffffffL;
                    if (e < 0x10000) continue;
                    int got;
                    try { m.seek(e); got = m.read(eb); } catch (Throwable t) { continue; }
                    if (got < 12) continue;
                    int f = ByteBuffer.wrap(eb, 0, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
                    int idx = c * 16384 + i;
                    if ((n.shift ? f >>> 1 : f) != idx) continue;
                    if (n.shift && (f & 1) != 0) continue;   // 宽字符名字，跳过
                    int end = 8; while (end < got && eb[end] != 0) end++;
                    String s = new String(eb, 8, end - 8, "ISO-8859-1");
                    String low = s.toLowerCase(java.util.Locale.US);
                    String k = null; for (String kk : ks) if (low.contains(kk)) { k = kk; break; }
                    if (k == null) continue;
                    if (hits.size() < 400) hits.add(s);
                    String cls = toClass(s);
                    if (cls == null) continue;
                    String base = cls.substring(cls.lastIndexOf('.') + 1).toLowerCase(java.util.Locale.US);
                    if (!base.contains("gamemode")) continue;
                    int score = (s.startsWith("/Game/") ? 2 : 0) + (base.startsWith("bp_") ? 1 : 0);
                    if (score > bestScore) { bestScore = score; best = cls; }
                }
            }
        }
        return best;
    }

    /** v3.00：服务端进大厅后自动扫一次名字表（v2.99 名字表修好后第一次全量扫），只读，不开房：
     *  收集含模式关键字的名字和所有像 GameMode 蓝图的 /Game/ 路径，写联机日志并自动传 mode-*。 */
    static final java.util.concurrent.ConcurrentHashMap<String, String> cache = new java.util.concurrent.ConcurrentHashMap<>();
    // v3.08 用户：海岛（战术模式）早就找到了，别每次开房再查蓝图 → 已确认的路径直接写死（v3.01 起实测能开）
    static { cache.put("Props", "/Game/Blueprints/BattleRoyalePropsGame/BP_NZBattleRoyalePropsGameMode.BP_NZBattleRoyalePropsGameMode_C"); }
    /** v3.03：名字表里扫到的大地图（/Game/Maps/…_Main，排除大厅/启动/训练营之外的都算） */
    static volatile java.util.List<String> maps = new java.util.ArrayList<>();
    static volatile boolean surveyed;
    static void survey() {
        // v3.97 用户：服务端大厅站一会就闪退（13:33 那次进大厅约 15~20 秒进程没了，无 tombstone，时间对得上这里进大厅 20 秒后的名字表全量扫：4 遍 × 100 万次读内存）。
        //   这个扫描只是 v3.00 找模式蓝图的诊断，结果没人用 → 不再自动跑。
        if (surveyed || Ed.ED != 1) return; surveyed = true;
        if (true) { state = "娱乐模式：v3.97 不再自动扫名字表（疑似服务端大厅闪退原因）"; return; }
        new Thread(() -> {
            try {
                Thread.sleep(20000);
                Veh.Names n = Veh.Names.get();
                state = "娱乐模式：自动扫名字表…（名字表 " + (n == null ? "没找到" : n.chunks < 0 ? "单块" : "多块") + "）";
                ArrayList<String> h1 = new ArrayList<>(), h2 = new ArrayList<>(), h3 = new ArrayList<>();
                String c1 = find(LEGION, h1), c2 = find(BFA, h2), c3 = find("gamemode", h3);
                ArrayList<String> gm = new ArrayList<>(); for (String x : h3) if (x.startsWith("/Game/")) gm.add(x);
                Lan.note("名字表全量扫 三军对垒(" + LEGION + ") 命中 " + h1.size() + "，蓝图 " + c1 + "：" + join(h1, 60));
                Lan.note("名字表全量扫 决胜五军(" + BFA + ") 命中 " + h2.size() + "，蓝图 " + c2 + "：" + join(h2, 60));
                Lan.note("名字表全量扫 GameMode 命中 " + h3.size() + "，其中 /Game/ 路径 " + gm.size() + "：" + join(gm, 80));
                Lan.note("GameMode 短名：" + join(h3, 120));
                ArrayList<String> h4 = new ArrayList<>(); find("/game/maps/", h4);
                java.util.TreeSet<String> mm = new java.util.TreeSet<>();
                for (String x : h4) { String y = x; int d = y.indexOf('.'); if (d > 0) y = y.substring(0, d); if (y.endsWith("_Main") && !y.contains("FrontEnd") && !y.contains("RoleHall") && !y.contains("Startup") && !y.contains("Training")) mm.add(y); }
                maps = new ArrayList<>(mm);
                Lan.note("名字表全量扫 地图 /Game/Maps/ 命中 " + h4.size() + "，大地图 " + mm.size() + "：" + mm + "；全部：" + join(h4, 150));
                state = "娱乐模式：自动扫完 三军对垒蓝图 " + (c1 == null ? "没有" : c1) + "，决胜五军蓝图 " + (c2 == null ? "没有" : c2) + "，/Game/ 模式蓝图 " + gm.size() + " 个";
                Lan.note(state);
                Lan.autoUp("mode");
            } catch (Throwable e) { state = "娱乐模式：自动扫出错 " + e; }
            GameOverlay.refreshAny();
        }, "zf-mode-survey").start();
    }
    static final String LEGION = "Legion|ThreeArmy|3Army|Camp", BFA = "BFA|FiveArmy|5Army";
    static String join(ArrayList<String> l, int max) { StringBuilder b = new StringBuilder(); for (int i = 0; i < l.size() && i < max; i++) b.append(i == 0 ? "" : " | ").append(l.get(i)); return b.length() == 0 ? "无" : b.toString(); }

    /** /Game/a/B → /Game/a/B.B_C；/Game/a/B.B → /Game/a/B.B_C；/Game/a/B.B_C 原样；其它（短名）null */
    static String toClass(String s) {
        if (!s.startsWith("/Game/") || s.indexOf(' ') >= 0) return null;
        int dot = s.lastIndexOf('.'), sl = s.lastIndexOf('/');
        if (dot < sl) return s + "." + s.substring(sl + 1) + "_C";
        return s.endsWith("_C") ? s : s + "_C";
    }
}
