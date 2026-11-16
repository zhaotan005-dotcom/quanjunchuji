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

    static void newMatch() { gen++; spawned = 0; phase = -1; equipState = ""; state = auto ? "人机：等进对局" : "人机：自动刷新已关"; }

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
        double lz = Double.NaN; long stillSince = 0, next = 0; int fails = 0, lastPhase = -2;
        while (spawned < TOTAL && alive(g)) {
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
            if (!Double.isNaN(lz) && Math.abs(q[2] - lz) < 50) { if (stillSince == 0) stillSince = now; } else stillSince = 0;
            lz = q[2];
            if (stillSince == 0 || now - stillSince < 15000) {
                if (spawned == 0) state = "人机：落地站稳后开始刷";
                Thread.sleep(2000); continue;
            }
            if (next == 0) next = now;
            if (now >= next) {
                int n = 1 + rnd.nextInt(3);
                for (int k = 0; k < n && spawned < TOTAL && alive(g); k++) fails = one(g, q, fails);
                if (fails >= 5) { state = "人机：连续失败停了（" + Veh.botState + "）"; Veh.note(state); GameOverlay.refreshAny(); return; }
                next = now + 25000 + rnd.nextInt(35000);
                state = "人机：已刷 " + spawned + " 个（" + Veh.DIFF[diff] + "），下一波约 " + (next - now) / 1000 + " 秒后";
                GameOverlay.refreshAny();
            }
            Thread.sleep(2000);
        }
        if (spawned >= TOTAL) state = "人机：本局 " + TOTAL + " 个已刷完";
    }

    /** 玩家周围 40~110 米刷一个，面朝玩家；之后补两次装备 */
    static int one(final int g, double[] q, int fails) throws InterruptedException {
        double a = rnd.nextDouble() * Math.PI * 2, r = 4000 + rnd.nextDouble() * 7000;
        int c = Veh.addBot(diff, q[0] + r * Math.cos(a), q[1] + r * Math.sin(a), q[2] + 300, Math.toDegrees(a) + 180, FLAGS);
        if (c == 0x5b08 || c == 0x5b07) {
            spawned++;
            final long ctrl = Veh.lastCtrl();
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
