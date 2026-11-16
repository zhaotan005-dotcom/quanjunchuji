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

    static void newMatch() { gen++; spawned = 0; phase = -1; equipState = ""; synchronized (live) { live.clear(); } killed = 0; state = auto ? "人机：等进对局" : "人机：自动刷新已关"; }

    // ---------------- v1.93 100 人局 ----------------
    // 用户：上面「剩余」固定从 100 开始，只在身边刷几个人机，打死一个少一个；玩家和服务端算一个人；减到 1 就结束游戏（吃鸡）。
    // GameState(GM+0x2e0) +0x774 PlayerLeftCount、+0x778 AIPlayerLeftCount（UProperty 表里读的偏移）。
    // 每 0.5 秒写：玩家 = 1，人机 = 99 - 已死人机。人机死没死：控制器 +0x2c0 的人物指针还是不是刷出来时那个。
    // 身边同时活着的人机保持 2~4 个，死了再补，直到一共死够 99 个。
    static final java.util.ArrayList<long[]> live = new java.util.ArrayList<>();   // {控制器, 人物}
    public static volatile int killed;
    public static volatile String countInfo = "";
    static int aliveBots(RandomAccessFile m) {
        int n = 0;
        synchronized (live) {
            for (java.util.Iterator<long[]> it = live.iterator(); it.hasNext(); ) {
                long[] b = it.next();
                long p = Server.rd(m, b[0] + 0x2c0);
                if (b[1] != 0 && p == b[1]) n++; else { it.remove(); killed++; }
            }
        }
        return n;
    }
    static void counter(final int g, final long gm) {
        new Thread(() -> {
            boolean first = true, ended = false;
            while (g == gen && Con.inMatch && auto) {
                try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "rw")) {
                    long gs = Server.rd(m, gm + 0x2e0);
                    if (gs >= 0x10000) {
                        if (first) { countInfo = "剩余人数 原来 玩家 " + Server.rd(m, gs + 0x774) + " 人机 " + Server.rd(m, gs + 0x778); Veh.note(countInfo); first = false; }
                        aliveBots(m);
                        int left = Math.max(0, TOTAL - killed);
                        Flow.wi(m, gs + 0x774, 1); Flow.wi(m, gs + 0x778, left);
                        if (left == 0 && !ended) { ended = true; Veh.note("人机：99 个都死了，剩余 1"); }
                    }
                } catch (Throwable ignored) {}
                try { Thread.sleep(500); } catch (InterruptedException e) { return; }
            }
        }, "zf-count").start();
    }

    static void matchLoaded() {
        final int g = ++gen; spawned = 0; phase = -1;
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
        double lz = Double.NaN; long stillSince = 0, next = 0; int fails = 0, lastPhase = -2; boolean counting = false;
        while (spawned < TOTAL && alive(g)) {
            double[] q = Veh.info();
            if (q == null) { state = "人机：等人物出生"; Thread.sleep(3000); continue; }
            if (!counting && (long) q[4] >= 0x10000) { counting = true; counter(g, (long) q[4]); }
            phase = readPhase((long) q[4]);
            if (phase != lastPhase) { Veh.note("人机：对局阶段 " + phase); lastPhase = phase; }
            long now = System.currentTimeMillis();
            if (phase < 4) {
                state = "人机：开打后再刷（现在阶段 " + phase + "）";
                stillSince = 0; GameOverlay.refreshAny(); Thread.sleep(3000); continue;
            }
            // 落地站稳：高度 2 秒内变化 < 0.5 米，持续 15 秒
            if (!Double.isNaN(lz) && Math.abs(q[2] - lz) < 50) { if (stillSince == 0) stillSince = now; } else stillSince = 0;
            lz = q[2];
            if (stillSince == 0 || now - stillSince < 15000) {
                if (spawned == 0) state = "人机：落地站稳后开始刷";
                Thread.sleep(2000); continue;
            }
            if (next == 0) next = now;
            int al; synchronized (live) { al = live.size(); }
            if (now >= next && al < 3) {
                int n = Math.min(TOTAL - spawned, 2 + rnd.nextInt(3) - al);
                for (int k = 0; k < n && spawned < TOTAL && alive(g); k++) fails = one(g, q, fails);
                if (fails >= 5) { state = "人机：连续失败停了（" + Veh.botState + "）"; Veh.note(state); GameOverlay.refreshAny(); return; }
                next = now + 20000 + rnd.nextInt(25000);
                state = "人机：剩余 " + (100 - killed) + "，身边 " + Math.min(4, al + n) + " 个（" + Veh.DIFF[diff] + "）";
                GameOverlay.refreshAny();
            }
            Thread.sleep(2000);
        }
        if (spawned >= TOTAL) state = "人机：本局 " + TOTAL + " 个已刷完，剩余 " + (100 - killed);
    }

    /** 玩家周围 40~110 米刷一个，面朝玩家；之后补两次装备 */
    static int one(final int g, double[] q, int fails) throws InterruptedException {
        double a = rnd.nextDouble() * Math.PI * 2, r = 4000 + rnd.nextDouble() * 7000;
        int c = Veh.addBot(diff, q[0] + r * Math.cos(a), q[1] + r * Math.sin(a), q[2] + 300, Math.toDegrees(a) + 180, FLAGS);
        if (c == 0x5b08 || c == 0x5b07) {
            spawned++;
            final long ctrl = Veh.lastCtrl();
            long pawn = 0;
            try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) { pawn = Server.rd(m, ctrl + 0x2c0); } catch (Throwable ignored) {}
            if (ctrl >= 0x10000 && pawn >= 0x10000) synchronized (live) { live.add(new long[] { ctrl, pawn }); }
            if (spawned <= 3) Veh.note("人机：第 " + spawned + " 个 " + Veh.codeText(c) + "；" + Veh.botInfo + "；" + Veh.equipInfo(ctrl));
            if (c == 0x5b08) new Thread(() -> regive(g, ctrl), "zf-bot-eq").start();
            return 0;
        }
        Veh.botState = "刷人机：" + Veh.codeText(c);
        Veh.note("人机：刷失败 " + Veh.codeText(c) + "；" + Veh.botInfo);
        return fails + 1;
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
