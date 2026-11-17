package com.zframe.inject;

import java.io.RandomAccessFile;
import java.util.Random;

/**
 * v1.86 人机随机刷新（像正式服的人机：附近零散冒出来几个，有装备、会打人）。
 * v1.85 一进图就在出生岛刷满 99 个，人机在等待区乱跑乱跳（出生岛行为树）、没枪，手机还卡 → 用户否决。
 * 现在：
 *   - 等待 / 飞机 / 跳伞阶段（GameState+0x71c < 4）一个都不刷；开打（阶段 4 RestrictedArea 起，「不上飞机」模式直接是 4）后，
 *     玩家落地站稳 15 秒再开始。
 *   - 每隔 25~60 秒随机刷 1~3 个，在玩家周围 40~110 米（高度 = 玩家 +3 米，落一下），面朝玩家；
 *     一局最多 99 个（加玩家 100）。
 *   - 每个都强制战斗行为树、给装备；3 秒、10 秒后再补一次装备（cave 'E'），诊断里记装备组件情况。
 * 阶段枚举：0 等人 1 开局倒计时 2 等跳伞 3 跳伞 4 毒圈 5 混战 6 结束。
 * 不记忆：开关、难度每次启动回到默认（开、普通）。
 */
public class Bots {
    public static volatile boolean auto = true;
    public static volatile int diff = 2;          // 1 简单 2 普通 3 困难（EBotDifficulty 1~3）
    public static volatile String state = "人机：等进对局";
    public static volatile String equipState = "";
    public static volatile int spawned, phase = -1;
    static final int TOTAL = 99;
    static final int FLAGS = 1 | 4;               // 给装备 + 强制战斗行为树
    static volatile int gen;
    static final Random rnd = new Random();

    static void newMatch() { usedNames.clear(); gen++; spawned = 0; phase = -1; equipState = ""; synchronized (live) { live.clear(); } killed = 0; state = auto ? "人机：等进对局" : "人机：自动刷新已关"; }

    // ---------------- v1.93/v1.94 100 人局 ----------------
    // 用户：上面「剩余」从 100 开始，只在身边刷几个人机，打死一个少一个；玩家和服务端算一个人；减到 1 就结束游戏、放结算动画。
    // HUD 的「剩余」= GameState+0x774 PlayerLeftCount。游戏自己每隔一会儿在 0x101a6e8（GameState 数活人）里写
    //   +0x774 = 活着的人物数（玩家 + 服务端那个 = 2）、+0x778 = 人机数 → v1.93 我们写 1、游戏写 2，来回跳。
    // v1.94：进对局把 0x101a880 / 0x101a884 两条 str 换成 nop，只由我们写：+0x774 = 100 - 已死人机，+0x778 = 剩下要打的人机数。
    // 结束：GameMode 0x100fbd4（BR 查结束）原来是 vt+0x634（活着的队伍数）<= 1 才结算（服务端那队一直活着，所以永远不结束）。
    //   剩余到 1 时把 0x100fc58 / 0x100fc5c 换成 ldr r0,[r4,#0x908]（GameState）/ ldr r0,[r0,#0x774]，
    //   后面 cmp r0,#1 / bgt 不动 → 游戏自己走胜利结算（vt+0x510）。下一局进图前改回原样。
    // 人机死没死：控制器 +0x2c0 人物指针变了，或者人物 +0x5a0（血量，游戏数活人时也是看它 >= 1）< 1。
    static final java.util.ArrayList<long[]> live = new java.util.ArrayList<>();   // {控制器, 人物}
    public static volatile int killed;
    public static volatile String countInfo = "";
    static final long ST_A = 0x101a880L, ST_B = 0x101a884L, END_A = 0x100fc58L, END_B = 0x100fc5cL;
    static final String ST_A_O = "747789e5", ST_B_O = "788789e5", NOP = "0000a0e1", END_A_O = "0400a0e1", END_B_O = "31ff2fe1", END_A_N = "080994e5", END_B_N = "740790e5";
    static void track(long ctrl) {
        if (ctrl < 0x10000) return;
        long pawn = 0;
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) { pawn = Server.rd(m, ctrl + 0x2c0); } catch (Throwable ignored) {}
        if (pawn >= 0x10000) synchronized (live) { live.add(new long[] { ctrl, pawn }); }
    }
    static int aliveBots(RandomAccessFile m) {
        int n = 0;
        synchronized (live) {
            for (java.util.Iterator<long[]> it = live.iterator(); it.hasNext(); ) {
                long[] b = it.next();
                long p = Server.rd(m, b[0] + 0x2c0);
                int hp = (int) Server.rd(m, b[1] + 0x5a0);
                if (p == b[1] && hp >= 1) n++; else { it.remove(); killed++; }
            }
        }
        return n;
    }
    /** 改一处代码：现在是 from 才改成 to；返回现在的字节 */
    static String patch(RandomAccessFile m, long off, String from, String to) throws java.io.IOException {
        long b = Mods.base(); if (b == 0) return "?";
        String cur = Server.bytesAt(m, b + off, 4);
        if (cur.equals(from)) { m.seek(b + off); m.write(Patches.hex(to)); cur = Server.bytesAt(m, b + off, 4); }
        return cur;
    }
    /** 新的一局进图：结束补丁改回原样（数人补丁留着，进对局后计数线程马上写 100） */
    static void unpatchEnd() {
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "rw")) {
            patch(m, END_A, END_A_N, END_A_O); patch(m, END_B, END_B_N, END_B_O);
        } catch (Throwable ignored) {}
    }
    /** v1.95：用户说 v1.94 人数还在 2 和 100 之间闪。每 50 毫秒写一次，并记下内存被别处改掉的次数和值（诊断看是谁在改） */
    public static volatile int foreign; public static volatile long foreignVal = -1, foreignAiVal = -1;
    static void counter(final int g, final long gm) {
        new Thread(() -> {
            boolean first = true, ended = false; int lastL = -1; long tick = 0;
            try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "rw")) {
                while (g == gen && Con.inMatch) {
                    try {
                        long gs = Server.rd(m, gm + 0x2e0);
                        if (gs >= 0x10000) {
                            if (first) {
                                // v1.99：v1.98 用真实人数后，人机摔死 → 只剩 1 人 → 游戏直接结束弹回大厅。数人代码还是 nop 掉，但不再写人数（不闪）
                                String a = patch(m, ST_A, ST_A_O, NOP), b = patch(m, ST_B, ST_B_O, NOP);
                                countInfo = "剩余人数（v1.99 游戏数人已停、不写）开局 " + Server.rd(m, gs + 0x774) + "（人机 " + Server.rd(m, gs + 0x778) + "），数人补丁 " + (a.equals(NOP) && b.equals(NOP) ? "已打" : "没打上 " + a + " " + b);
                                if ((Server.rd(m, gs + 0x71c) & 0xff) == 0 && Server.rd(m, gs + 0x72c) == Float.floatToIntBits(60f)) {
                                    Flow.wi(m, gs + 0x72c, Float.floatToIntBits(1f)); Veh.note("开局等人 60 秒改成 1 秒（本局）");
                                }
                                first = false;
                            }
                            if (tick++ % 6 == 0) aliveBots(m);
                            lastL = (int) Server.rd(m, gs + 0x774); foreignVal = lastL; foreignAiVal = Server.rd(m, gs + 0x778);
                        }
                    } catch (Throwable ignored) {}
                    try { Thread.sleep(300); } catch (InterruptedException e) { return; }
                }
            } catch (Throwable ignored) {}
        }, "zf-count").start();
    }
    static String countText() {
        return countInfo + "，现在 " + foreignVal + "（人机 " + foreignAiVal + "），已死人机 " + killed + "\n  " + gunState + "\n  " + dressState + "\n  " + nameState;
    }

    /** v1.97：仓库里穿着的衣服（不含默认衣服、发型），进对局后当普通物品发到身上，能脱能捡 */
    public static volatile java.util.List<Long> outfit = new java.util.ArrayList<>();
    public static volatile String outfitState = "仓库衣服：还没发";
    static void giveOutfit(final int g) {
        final java.util.List<Long> l = outfit;
        if (l == null || l.isEmpty()) { outfitState = "仓库衣服：没有要发的"; return; }
        new Thread(() -> {
            try {
                // v1.98：用户要一进去就穿着。不再等 3 秒：每 0.2 秒看一次，人物一出生就用 cave 'I' 直接发到玩家身上
                long pawn = 0;
                for (int t = 0; t < 300 && g == gen && Con.inMatch; t++) {
                    double[] q = Veh.info();
                    if (q != null && q.length > 6 && (long) q[6] >= 0x10000) { pawn = (long) q[6]; break; }
                    Thread.sleep(200);
                }
                if (pawn == 0) { outfitState = "仓库衣服：等不到人物"; return; }
                StringBuilder b = new StringBuilder();
                for (long id : l) {
                    if (g != gen || !Con.inMatch) return;
                    int c = Veh.giveTo(pawn, id, 1);
                    b.append(b.length() == 0 ? "" : ",").append(id).append('=').append(Integer.toHexString(c));
                }
                outfitState = "仓库衣服：进对局发了 " + l.size() + " 件 " + b;
                Veh.note(outfitState);
            } catch (Throwable e) { outfitState = "仓库衣服：出错 " + e; }
        }, "zf-outfit").start();
    }

    static void matchLoaded() {
        usedNames.clear(); final int g = ++gen; spawned = 0; phase = -1; synchronized (live) { live.clear(); } killed = 0;
        unpatchEnd();
        new Thread(() -> {   // 剩余人数：不管自动刷人机开没开都数
            try {
                for (int i = 0; i < 400 && g == gen && Con.inMatch; i++) {
                    double[] q = Veh.info();
                    if (q != null && (long) q[4] >= 0x10000) { counter(g, (long) q[4]); giveOutfit(g); return; }
                    Thread.sleep(300);
                }
            } catch (Throwable ignored) {}
        }, "zf-count0").start();
        if (!auto) { state = "人机：自动刷新已关"; return; }
        new Thread(() -> {
            try { run(g); }
            catch (Throwable e) { state = "人机：出错 " + e; Veh.note(state); }
            GameOverlay.refreshAny();
        }, "zf-bots").start();
    }

    static boolean alive(int g) { return g == gen && auto && Con.inMatch; }

    static void run(int g) throws Exception {
        Thread.sleep(8000);
        double lz = Double.NaN; long stillSince = 0, next = 0; int fails = 0, lastPhase = -2;
        int cap = 15 + rnd.nextInt(16);   // v1.99：不真刷 99 个，本局随机 15~30 个
        Veh.note("人机：本局最多 " + cap + " 个");
        while (killed < cap && alive(g)) {
            double[] q = Veh.info();
            if (q == null) { state = "人机：等人物出生"; Thread.sleep(3000); continue; }
            phase = readPhase((long) q[4]);
            if (phase != lastPhase) { Veh.note("人机：对局阶段 " + phase); lastPhase = phase; }
            long now = System.currentTimeMillis();
            if (phase < 4) {
                state = "人机：开打后再刷（现在阶段 " + phase + "）";
                stillSince = 0; GameOverlay.refreshAny(); Thread.sleep(3000); continue;
            }
            // 落地站稳：高度 2 秒内变化 < 0.5 米，持续 15 秒
            // v1.99：飞机上高度也不变（约 200 米），以前人机会被刷在空中摔死 → 高度要低于 120 米才算落地
            if (q[2] < 12000 && !Double.isNaN(lz) && Math.abs(q[2] - lz) < 50) { if (stillSince == 0) stillSince = now; } else stillSince = 0;
            lz = q[2];
            if (stillSince == 0 || now - stillSince < 15000) {
                if (spawned == 0) state = "人机：落地站稳后开始刷";
                Thread.sleep(2000); continue;
            }
            if (next == 0) { next = now + 30000 + rnd.nextInt(90001); Veh.note("人机：落地站稳，第一批 " + (next - now) / 1000 + " 秒后刷"); }   // v1.99：第一批随机 30~120 秒后
            int al; synchronized (live) { al = live.size(); }
            if (now >= next && al < 3) {
                int n = Math.min(cap - killed - al, Math.min(2, 1 + rnd.nextInt(2)));   // v1.99：一次 1~2 个
                for (int k = 0; k < n && alive(g); k++) fails = one(g, q, fails);
                if (fails >= 5) { state = "人机：连续失败停了（" + Veh.botState + "）"; Veh.note(state); GameOverlay.refreshAny(); return; }
                next = now + 20000 + rnd.nextInt(25000);
                state = "人机：剩余 " + (100 - killed) + "，身边 " + Math.min(4, al + n) + " 个（" + Veh.DIFF[diff] + "）";
                GameOverlay.refreshAny();
            }
            Thread.sleep(2000);
        }
        if (killed >= cap) state = "人机：本局 " + cap + " 个都打完了";
    }

    /** 玩家周围 40~110 米刷一个，面朝玩家；之后补两次装备 */
    static int one(final int g, double[] q, int fails) throws InterruptedException {
        double a = rnd.nextDouble() * Math.PI * 2, r = 4000 + rnd.nextDouble() * 7000;
        int c = Veh.addBot(diff, q[0] + r * Math.cos(a), q[1] + r * Math.sin(a), q[2] + 300, Math.toDegrees(a) + 180, FLAGS);
        if (c == 0x5b08 || c == 0x5b07) {
            spawned++;
            final long ctrl = Veh.lastCtrl();
            track(ctrl);
            if (spawned <= 3) Veh.note("人机：第 " + spawned + " 个 " + Veh.codeText(c) + "；" + Veh.botInfo + "；" + Veh.equipInfo(ctrl));
            if (c == 0x5b08) new Thread(() -> regive(g, ctrl), "zf-bot-eq").start();
            final long pawn = Veh.buf.getInt(96) & 0xffffffffL; final int idx = spawned;
            new Thread(() -> loadout(g, ctrl, pawn, idx), "zf-bot-gun").start();
            return 0;
        }
        Veh.botState = "刷人机：" + Veh.codeText(c);
        Veh.note("人机：刷失败 " + Veh.codeText(c) + "；" + Veh.botInfo);
        return fails + 1;
    }

    /** v1.98 人机随机装备：游戏自己的「给人机装备」只给了衣服之类，用户说人机没枪。
     *  用 cave 'I' 直接往人机背包里放：随机一把枪 + 对应子弹，一半概率头盔/甲，一半概率药。 */
    static final String[][] GUN = {
        {"20101000001", "20600000001"}, {"20101000002", "20600000002"}, {"20101000003", "20600000001"}, {"20101000004", "20600000001"},
        {"20105000001", "20600000003"}, {"20105000002", "20600000003"}, {"20105000003", "20600000003"}, {"20105000004", "20600000004"},
        {"20103000001", "20600000005"}, {"20103000002", "20600000005"}, {"20104000001", "20600000002"}, {"20104000003", "20600000002"},
        {"20104000006", "20600000001"}, {"20102000001", "20600000001"}, {"20201000001", "20600000003"}, {"20201000002", "20600000004"} };
    static final String[] HELM = {"30304000001", "30304000002", "30304000003"}, VEST = {"30305000001", "30305000002", "30305000003"};
    public static volatile String gunState = "人机配枪：还没试";
    static void loadout(int g, long ctrl, long pawn, int idx) { loadout(g, ctrl, pawn, idx, true); }
    static void loadout(int g, long ctrl, long pawn, int idx, boolean arm) {
        try {
            Thread.sleep(1500);
            if (g != gen || !Con.inMatch) return;
            if (pawn < 0x10000) { try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) { pawn = Server.rd(m, ctrl + 0x2c0); } }
            fixUin(ctrl, pawn, idx);
            if (!arm) { dress(pawn); name(ctrl, pawn); gunState = "人机配枪：第 " + idx + " 个 不带枪；" + uinState; return; }
            String[] gun = GUN[rnd.nextInt(GUN.length)];
            int c1 = Veh.giveTo(pawn, Long.parseLong(gun[0]), 1);
            int cap = Veh.buf.getInt(140), ret = Veh.buf.getInt(144);
            int c2 = Veh.giveTo(pawn, Long.parseLong(gun[1]), 30 + rnd.nextInt(60));
            if (rnd.nextBoolean()) Veh.giveTo(pawn, Long.parseLong(HELM[rnd.nextInt(HELM.length)]), 1);
            if (rnd.nextBoolean()) Veh.giveTo(pawn, Long.parseLong(VEST[rnd.nextInt(VEST.length)]), 1);
            if (rnd.nextInt(3) == 0) Veh.giveTo(pawn, 40100000001L + rnd.nextInt(2), 1 + rnd.nextInt(3));
            dress(pawn);
            name(ctrl, pawn);
            gunState = "人机配枪：第 " + idx + " 个 枪 " + gun[0] + " → " + Integer.toHexString(c1) + "（能放 " + cap + "，返回 " + ret + "），子弹 → " + Integer.toHexString(c2) + "；" + uinState;
            if (idx <= 3) Veh.note(gunState);
        } catch (Throwable e) { gunState = "人机配枪：出错 " + e; }
    }
    // ---------------- v2.19 人机衣服随机 + 名字 + 固定人机 ----------------
    /** 衣服池：仓库存档里的衣服 + 衣服页存过的列表；都没有就扫一次内存（后台，只扫一次）。按部位分：01 裤 02 上衣 03 鞋 12 帽 14/15 外套 */
    static volatile java.util.Map<Integer, java.util.ArrayList<Long>> pool;
    static volatile boolean poolBusy;
    public static volatile String dressState = "人机衣服：还没试";
    static java.util.Map<Integer, java.util.ArrayList<Long>> pool() {
        java.util.Map<Integer, java.util.ArrayList<Long>> pl = pool;
        if (pl != null) return pl;
        java.util.TreeSet<Long> ids = new java.util.TreeSet<>();
        try { if (Server.props != null) for (long[] v : Server.props.values()) ids.add(v[0]); } catch (Throwable ignored) {}
        try {
            java.io.File f = GameOverlay.clothFile();
            if (f != null && f.exists()) for (String x : new String(java.nio.file.Files.readAllBytes(f.toPath())).trim().split("[,\\s]+")) if (!x.isEmpty()) ids.add(Long.parseLong(x));
        } catch (Throwable ignored) {}
        java.util.Map<Integer, java.util.ArrayList<Long>> m = new java.util.HashMap<>();
        for (long id : ids) {
            int big = (int) (id / 100000000L) % 1000, yy = (int) (id / 1000000 % 100);
            if (big != 301 && big != 303) continue;              // 只用男款和通用（人机是男模型）
            if (id == 30304000000L || yy == 4 || yy == 5 || yy == 13) continue;
            int slot = yy == 15 ? 14 : yy;
            if (slot != 1 && slot != 2 && slot != 3 && slot != 12 && slot != 14) continue;
            m.computeIfAbsent(slot, k -> new java.util.ArrayList<>()).add(id);
        }
        int n = 0; for (java.util.ArrayList<Long> l : m.values()) n += l.size();
        if (n >= 6) { pool = m; return m; }
        if (!poolBusy) {
            poolBusy = true;
            new Thread(() -> {
                try {
                    java.util.TreeSet<Long> all = new java.util.TreeSet<>(Server.scanSkins().keySet());
                    java.io.File f = GameOverlay.clothFile();
                    StringBuilder b = new StringBuilder(); for (long v : all) b.append(v).append(' ');
                    if (f != null && !all.isEmpty()) java.nio.file.Files.write(f.toPath(), b.toString().trim().getBytes());
                    dressState = "人机衣服：扫到 " + all.size() + " 件";
                } catch (Throwable e) { dressState = "人机衣服：扫衣服出错 " + e; }
                poolBusy = false;
            }, "zf-botcloth").start();
        }
        return m;
    }
    static void dress(long pawn) {
        try {
            java.util.Map<Integer, java.util.ArrayList<Long>> m = pool();
            StringBuilder b = new StringBuilder();
            for (int slot : new int[] { 2, 1, 3, 12, 14 }) {
                java.util.ArrayList<Long> l = m.get(slot);
                if (l == null || l.isEmpty()) continue;
                if ((slot == 12 || slot == 14) && rnd.nextBoolean()) continue;   // 帽子、外套一半概率
                long id = l.get(rnd.nextInt(l.size()));
                int c = Veh.giveTo(pawn, id, 1);
                b.append(id).append('=').append(Integer.toHexString(c)).append(' ');
            }
            dressState = b.length() == 0 ? "人机衣服：衣服池是空的（" + dressState + "）" : "人机衣服：" + b;
        } catch (Throwable e) { dressState = "人机衣服：出错 " + e; }
    }

    /** 人机名字：动画角色名里随机，一局不重复 */
    static final String[] NAMES = (
        "喜羊羊 美羊羊 懒羊羊 沸羊羊 暖羊羊 慢羊羊 快羊羊 软绵绵 刀羊 羚羊公主 羚羊王子 忽悠老羊 羊大力 荷花 古羊族族长 威哥 吉祥 如意 小可爱 外母鸡 恶龙 " +
        "灰太狼 红太狼 小灰灰 蕉太狼 黑太狼 武大狼 小白狼 香太狼 夜太狼 白眼狼 拳击狼 灰二太太狼 巫师狼 双刀狼 双叉狼 狼将军 芯太狼 鱼淘淘 " +
        "咚咚锵 嘭恰恰 白牛国王 黑牛国王 虎威太岁 阿豹 壁虎军师 精灵兔 小蜜蜂 包包大人 泰哥 小强 乌鸦 飞哥 河马 犀牛 狮子 大象 " +
        "熊大 熊二 光头强 赵琳 纳雅 萨尔塔 团子 鹿希 毛毛 吉吉 蹦蹦 涂涂 萝卜头 肥波 小狸 老鳄 翠花 铁掌大师 拖拖 胖头 肥肥 球球 柯基 嘟嘟 贝丝 南海怪兽 天才威 洞洞幺 大马猴 二狗 李老板 王老板 强爸 强妈 光头强奶奶 虎妞 崽崽 小月亮 虎子 猫头鹰 黑风 象奶奶 AGE Coco 金鹿角守护者 大鹿 猴头菇精灵 乐天 小可 团子妈妈 麋鹿 老酋长 驯鹿 雪人 雪熊 猫头鹰老师 熊妈妈 熊爸爸 小树 大树 跳跳 豆豆 毛毛表弟 野猪 山羊 刺猬 松鼠 啄木鸟 青蛙 兔子 小鸟 " +
        "洛洛 王嘉洛 霹雳火 急先锋 龙卷风 超音速 力霸天 冲击波 风火轮 闪电 风雪 急先锋师父 月神殿城主 时光之城老城主 能源之城老城主 " +
        "晶晶 猛虎王 狂野猩 暴龙神 金铁兽 银铁兽 绿豹兽 蓝豹兽 紫龙兽 蓝毒兽 紫毒兽 银毒兽 黑毒兽 黑豹兽 黑铁兽 冰狼兽 地藏虎 " +
        "南宫问天 南宫问雅 东方铁心 北冥雪 西门孝 北冥雷 阿娇 阿莎 南宫逸 玉燕 西门豪 北冥正 东方雄 天地盟主 玄天圣帝 玄天帝姬 燕侠 武勇 玉龙国女王 宽容王子 希望王子 纯真公主 白燕 白月蟾蜍 天马 白羊 美人鱼 " +
        "天晶兽 玉灵龙 凤凰 神农兽 小太虚 太虚兽 十方明亮 惊邪 炎兽 虎魄 巨灵龙 魔兵天晶 神兽坐骑 " +
        "胡图图 图图 张小丽 胡英俊 张帅子 刷子 壮壮 小美 牛爷爷 健康哥哥 芳芳老师 小怪 麦大星 麦小星 图图爷爷 图图奶奶 王子 快快 大龙 二龙 帅子妈 壮壮妈 小美妈"
        ).split(" ");
    static final java.util.Set<String> usedNames = java.util.Collections.synchronizedSet(new java.util.HashSet<>());
    public static volatile String nameState = "人机名字：还没试";
    static String pickName() {
        for (int t = 0; t < 50; t++) { String n = NAMES[rnd.nextInt(NAMES.length)]; if (usedNames.add(n)) return n; }
        return NAMES[rnd.nextInt(NAMES.length)];
    }
    /** PlayerState 里的名字是 FString{data,num,max}，TCHAR 4 字节。三个候选偏移（反射表里叫 PlayerName 的属性：0x210/0x260/0x2f0），
     *  看着像字符串、并且原来的空间放得下新名字才写（不另外分配内存，免得游戏释放时崩）。 */
    static final int[] NAME_OFF = { 0x210, 0x260, 0x2f0 };
    static void name(long ctrl, long pawn) {
        String nm = pickName();
        StringBuilder b = new StringBuilder("人机名字 " + nm + "：");
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "rw")) {
            long ps = Server.rd(m, ctrl + 0x2c4); if (ps < 0x10000) ps = Server.rd(m, pawn + 0x2c4);
            if (ps < 0x10000) { nameState = b + "找不到 PlayerState"; return; }
            int[] cps = nm.codePoints().toArray();
            for (int off : NAME_OFF) {
                long data = Server.rd(m, ps + off); int num = (int) Server.rd(m, ps + off + 4), max = (int) Server.rd(m, ps + off + 8);
                b.append(Integer.toHexString(off)).append('[');
                if (data < 0x10000 || num < 1 || num > 64 || max < num || max > 256) { b.append("不像 ").append(num).append('/').append(max).append("] "); continue; }
                StringBuilder old = new StringBuilder();
                for (int i = 0; i < num - 1; i++) { int c = (int) Server.rd(m, data + 4L * i); if (c <= 0 || c > 0x10ffff) { old.setLength(0); old.append('?'); break; } old.appendCodePoint(c); }
                b.append(old);
                if (max >= cps.length + 1) {
                    for (int i = 0; i < cps.length; i++) Flow.wi(m, data + 4L * i, cps[i]);
                    Flow.wi(m, data + 4L * cps.length, 0);
                    Flow.wi(m, ps + off + 4, cps.length + 1);
                    b.append("→写了");
                } else b.append(" 放不下 ").append(max);
                b.append("] ");
            }
        } catch (Throwable e) { b.append("出错 ").append(e); }
        nameState = b.toString();
    }

    /** 固定人机：人物移动组件的 MaxWalkSpeed(0x164)/蹲走(0x168)/MaxAcceleration(0x178)/JumpZ(0x128) 写 0，每秒补一次（会转身开枪，不会走） */
    static void hold(final int g, final long pawn) {
        new Thread(() -> {
            try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "rw")) {
                while (g == gen && Con.inMatch) {
                    long c = Server.rd(m, pawn + Mods.P_CMC);
                    int hp = (int) Server.rd(m, pawn + 0x5a0);
                    if (c < 0x10000 || hp < 1) return;
                    for (int o : new int[] { 0x164, 0x168, 0x178, 0x128 }) Mods.wf(m, c + o, 0f);
                    Thread.sleep(1000);
                }
            } catch (Throwable ignored) {}
        }, "zf-bot-hold").start();
    }
    /** v2.19 手动刷：和自动刷的人机一样配枪/衣服/名字；fixed = 固定人机 */
    /** v2.36 用户：固定人机、自由人机各一个「带枪」开关（默认带）。不带枪：不给全套装备（标志去掉 1）、不补装备、不发枪和子弹 */
    static volatile boolean gunFixed = true, gunFree = true;
    static boolean armed(boolean fixed) { return fixed ? gunFixed : gunFree; }
    static void manual(long ctrl, boolean fixed) {
        final int g = gen; spawned++; track(ctrl);
        final boolean arm = armed(fixed);
        long pawn = 0;
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) { pawn = Server.rd(m, ctrl + 0x2c0); } catch (Throwable ignored) {}
        if (pawn < 0x10000) pawn = Veh.buf.getInt(96) & 0xffffffffL;
        final long pw = pawn; final int idx = spawned;
        if (arm) new Thread(() -> regive(g, ctrl), "zf-bot-eq").start();
        new Thread(() -> loadout(g, ctrl, pw, idx, arm), "zf-bot-gun").start();
        if (fixed && pw >= 0x10000) hold(g, pw);
    }

    /** v1.98 人机在地图上显示成队友（日志 IsTeammateByUin! Found!）：队伍按 PlayerState+0x3b0 的 Uin 认人，
     *  单机下人机和玩家的 Uin 都是 0 → 被当成队友。给每个人机一个不同的 Uin。 */
    public static volatile String uinState = "";
    static void fixUin(long ctrl, long pawn, int idx) {
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "rw")) {
            long ps = Server.rd(m, ctrl + 0x2c4); if (ps < 0x10000) ps = Server.rd(m, pawn + 0x2c4);
            if (ps < 0x10000) { uinState = "Uin：找不到 PlayerState"; return; }
            long lo = Server.rd(m, ps + 0x3b0), hi = Server.rd(m, ps + 0x3b4);
            long me = GameOverlay.pawnAddr(), mps = me >= 0x10000 ? Server.rd(m, me + 0x2c4) : 0;
            String mine = mps >= 0x10000 ? Long.toHexString(Server.rd(m, mps + 0x3b4)) + ":" + Long.toHexString(Server.rd(m, mps + 0x3b0)) : "?";
            if (lo == 0 && hi == 0) { Flow.wi(m, ps + 0x3b0, 900000 + idx); }
            uinState = "Uin 人机 " + Long.toHexString(hi) + ":" + Long.toHexString(lo) + "→" + Long.toHexString(Server.rd(m, ps + 0x3b0)) + "，玩家 " + mine + "，队伍 " + Long.toHexString(Server.rd(m, ps + 0x340));
        } catch (Throwable e) { uinState = "Uin：出错 " + e; }
    }

    static void regive(int g, long ctrl) {
        try {
            for (int t : new int[] { 3000, 7000 }) {
                Thread.sleep(t);
                if (g != gen || !Con.inMatch) return;
                int c = Veh.equip(ctrl, 1);
                equipState = "补装备 " + Integer.toHexString(c) + "：" + Veh.equipInfo(ctrl);
            }
            if (spawned <= 3) Veh.note("人机：10 秒后 " + equipState);
        } catch (Throwable ignored) {}
    }

    /** 对局阶段：GameState(GM+0x2e0)+0x71c（CurrentGamePhase），读不到返回 -1 */
    static int readPhase(long gm) {
        if (gm < 0x10000) return -1;
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
            long gs = Server.rd(m, gm + 0x2e0);
            if (gs < 0x10000) return -1;
            return (int) (Server.rd(m, gs + 0x71c) & 0xff);
        } catch (Throwable e) { return -1; }
    }
}
