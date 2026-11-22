package com.zframe.inject;

import java.io.RandomAccessFile;

/** v4.11 训练营的「模式编号」—— 让游戏自己的训练营规则（死了 4 秒原地复活）生效。根因修复，取代 v4.10 的补血/无敌。
 *
 *  原版训练营怎么处理死亡（全部静态读出，未改 pak）：
 *  - 训练岛地图默认模式 BP_NZBRPracticeGameMode（pakchunk_111896…，明文 uasset 标签属性）：
 *      SystemNameList = PracticeSystem、ResurrectSystem、PickUpRefreshSystem；
 *      StateInfoClassList = BP_PracticeInfoState、BP_PracticeResurrectInfoState、BP_PickUpRefreshInfoState。
 *    → 训练营里死亡交给游戏自己的复活系统 AResurrectSystem，不是「不会死」。
 *  - 复活规则表 /Game/DataTables/GameFeatureConfig/ResurrectSystemConfig（pakchunk_7c73d0…，行结构 ResurrectInfoStateRow）4 行：
 *      NewRow   ModeId 0（通配）：Sequence，SecondsArray 60,120…1320（整点批量复活，带运输机参数）
 *      BTT      ModeId 503；GameModeTest ModeId 2001
 *      Practice ModeId 600：Personal（个人），PersonalDelaySeconds 4.0，PersonalResurrectEndTimes -1（不限次数），bSpawnPawnImediately，SpectateProtectTime 3
 *    GameModeType 表：SubGameModeType 600 = 「训练营地图」（MainGameModeType 2，ServerGameModeType 4）；GameMapID 表：5 = 训练营地图。
 *  - 选哪一行（libUE4）：AResurrectInfoState 初始化 0x103c9d0 → 0x104dd7c → NZCommonTable 0x104e058「[ModeMapping]」：
 *      取 GameState→GetWorld()→OwningGameInstance（UWorld +0xc4，反射 0x5045270），按 GI+0xe4（模式）/+0xdc（地图）/+0xe8/+0xec 和每行 ModeId/MapId/MatchId/TeamId 打分，
 *      行 ModeId == GI+0xe4 得 0x80，行 ModeId 0 只得 8（通配）。把行 +0x38 的 ResurrectParam 拷到 InfoState+0x2b8。
 *  - GI 这几个字是 GameInstance 虚函数 0xc74234（虚表 +0x174）从 URL 里解析的：MainModeId=→+0xe0、SubModeId=→+0xe4、MapId=→+0xdc、RoomId=→+0xe8、TeamId=→+0xec。
 *    正式服由服务器给这些值；我们单机/自建服务端「open 训练岛地图」什么都没给 → GI+0xe4 = 0 → 复活系统拿到通配行 NewRow（Sequence、60 秒一批），
 *    不是 Practice 行 → 训练营「死了 4 秒原地复活」这条游戏规则从来没生效。这就是根因。
 *  - 复活本身全是游戏代码：ResurrectSystem Tick 0x119feb0 → 个人模式 0x11a0458（InfoState+0x394 每人倒计时）→ 0x11a11d8 → 0x11a1438（重生人物）；
 *    InfoState+0x350 由 OnMatchBegin 0x119f560 置 1。
 *
 *  做法：Con.exec 发「open 训练岛」之前，把 GI+0xe0/+0xe4/+0xdc 写成 2/600/5（和服务器会给的一样），开别的图之前改回原值。
 *  只写 GameInstance 的这三个 int，不改代码字节；GI 用 GWorld→+0xc4 拿，虚表 +0x174 必须是 0xc74234（确认是那个类）才写。 */
final class ModeMap {
    static final int W_GI = 0xc4, GI_MAP = 0xdc, GI_MAIN = 0xe0, GI_SUB = 0xe4;
    static final long GI_PARSE = 0xc74234L;   // 虚表 +0x174：从 URL 解析模式编号的那个函数
    static final int TRAIN_MAIN = 2, TRAIN_SUB = 600, TRAIN_MAP = 5;
    static volatile int[] saved;            // 改之前的 {map, main, sub}；null = 没改
    static volatile String state = "模式编号：没动";
    static volatile long gi;

    /** Con.exec 的 open 前调用（mp = 地图路径，不带 ?参数） */
    static void beforeOpen(String mp) {
        if (mp == null || !mp.startsWith("/Game/Maps/")) return;   // 连 IP（客户端）不管：规则在服务端算
        boolean train = mp.contains("TrainingIsland");
        if (!train && saved == null) return;
        long b = Mods.base(); if (b == 0) { state = "模式编号：找不到游戏库"; return; }
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "rw")) {
            long g = findGi(m, b);
            if (g == 0) { state = "模式编号：没找到 GameInstance（" + (train ? "训练营复活规则没设上" : "没改回") + "）"; LogCap.note(state); return; }
            gi = g;
            int map = Mods.rd(m, g + GI_MAP), main = Mods.rd(m, g + GI_MAIN), sub = Mods.rd(m, g + GI_SUB);
            if (train) {
                if (saved == null) saved = new int[] { map, main, sub };
                Mods.wi(m, g + GI_MAP, TRAIN_MAP); Mods.wi(m, g + GI_MAIN, TRAIN_MAIN); Mods.wi(m, g + GI_SUB, TRAIN_SUB);
                state = "模式编号：训练营 " + TRAIN_MAIN + "/" + TRAIN_SUB + " 地图 " + TRAIN_MAP + "（原来 " + main + "/" + sub + " 地图 " + map + "）";
            } else {
                int[] s = saved; saved = null;
                // 只在还是我们写的值时改回（游戏自己改过就不动）
                if (sub == TRAIN_SUB && main == TRAIN_MAIN && map == TRAIN_MAP) {
                    Mods.wi(m, g + GI_MAP, s[0]); Mods.wi(m, g + GI_MAIN, s[1]); Mods.wi(m, g + GI_SUB, s[2]);
                }
                state = "模式编号：离开训练营，改回 " + s[1] + "/" + s[2] + " 地图 " + s[0];
            }
            LogCap.note(state);
        } catch (Throwable t) { state = "模式编号：出错 " + t; LogCap.note(state); }
    }

    static long findGi(RandomAccessFile m, long b) {
        try {
            long w = Server.rd(m, b + WorldWatch.GWORLD);
            if (w == 0 || !Mods.isObj(m, b, w)) return 0;
            long g = Mods.rp(m, w + W_GI);
            if (g == 0 || !Mods.isObj(m, b, g)) return 0;
            long vt = Mods.rp(m, g);
            if (Mods.rp(m, vt + 0x174) != b + GI_PARSE) return 0;
            return g;
        } catch (Throwable t) { return 0; }
    }

    /** 状态行用：现在 GI 里的模式（读内存，600 = 训练营规则会生效） */
    static String shortText() {
        long b = Mods.base(), g = gi;
        if (b == 0) return "模式编号 ?";
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
            if (g == 0 || !Mods.isObj(m, b, g)) g = findGi(m, b);
            if (g == 0) return "模式编号 没找到GI";
            int sub = Mods.rd(m, g + GI_SUB), map = Mods.rd(m, g + GI_MAP);
            return "模式编号 " + sub + " 地图 " + map + (sub == TRAIN_SUB ? "（训练营规则：死了4秒复活）" : "（不是600：复活规则不对）");
        } catch (Throwable t) { return "模式编号 出错"; }
    }
}
