package com.zframe.inject;

import java.io.RandomAccessFile;
import java.util.Random;

/**
 * v1.85 100 人单机局（自动刷人机）。
 * 进对局、人物出生后：
 *   1. 出生岛：围着玩家随机刷 10~15 个（6~25 米）。
 *   2. 还在出生岛等待（阶段 0/1）：分批（每批 3 个，隔 2 秒）在出生岛更远处（30~150 米）补，凑够 99 个人机 + 玩家 = 100。
 *      真实对局里出生岛上的人机会被游戏放上飞机、跳伞到全图（日志 "DropOneAI ... PlaneLoc"），我们不改这个流程。
 *   3. 开局后还没补够（比如「不上飞机」模式直接阶段 4）：等玩家落地站稳，在玩家周围 50~300 米补，高度 = 玩家脚下 +1.5 米。
 * 阶段 = GameState(GM+0x2e0) +0x71c 字节（游戏自己的判断：>3 用战斗行为树）。
 * 不在启动画面动内存：只在 Con 看到对局地图加载完以后才开始，而且先等 8 秒。
 * 什么都不记忆：开关和难度每次启动回到默认（开、普通）。
 */
public class Bots {
    public static volatile boolean auto = true;
    public static volatile int diff = 2;          // 1 简单 2 普通 3 困难（EBotDifficulty 1~3）
    public static volatile String state = "100 人局：等进对局";
    public static volatile int spawned, phase = -1;
    static final int TOTAL = 99, BATCH = 3;
    static volatile int gen;
    static final Random rnd = new Random();

    /** Con：开始进新地图 */
    static void newMatch() { gen++; spawned = 0; phase = -1; state = auto ? "100 人局：等进对局" : "100 人局：已关"; }

    /** Con：对局地图加载完 */
    static void matchLoaded() {
        final int g = ++gen; spawned = 0; phase = -1;
        if (!auto) { state = "100 人局：已关"; return; }
        new Thread(() -> {
            try { run(g); }
            catch (Throwable e) { state = "100 人局：出错 " + e; Veh.note(state); }
            GameOverlay.refreshAny();
        }, "zf-bots").start();
    }

    static boolean alive(int g) { return g == gen && auto && Con.inMatch; }

    static void run(int g) throws Exception {
        Thread.sleep(8000);
        double[] p = null;
        for (int i = 0; i < 60 && alive(g); i++) {   // 最多等 2 分钟人物出生
            p = Veh.info();
            if (p != null) break;
            state = "100 人局：等人物出生"; Thread.sleep(2000);
        }
        if (p == null || !alive(g)) { if (p == null && g == gen) state = "100 人局：一直没等到人物（" + Veh.state + "）"; return; }
        final double ix = p[0], iy = p[1], iz = p[2];
        phase = readPhase((long) p[4]);
        Veh.note("100 人局：开始，难度 " + Veh.DIFF[diff] + "，阶段 " + phase + "，出生点 " + (int) ix + " " + (int) iy + " " + (int) iz);
        int fails = 0;
        // 1. 出生岛围着玩家
        int first = 10 + rnd.nextInt(6);
        while (spawned < first && alive(g)) {
            for (int k = 0; k < BATCH && spawned < first; k++) fails = one(ix, iy, iz + 100, 600, 2500, fails);
            state = "100 人局：出生岛 " + spawned + "/" + first;
            GameOverlay.refreshAny();
            if (fails >= 5) { stop(); return; }
            Thread.sleep(1200);
        }
        // 2/3. 补满
        double lz = Double.NaN; int still = 0;
        while (spawned < TOTAL && alive(g)) {
            double[] q = Veh.info();
            if (q == null) { Thread.sleep(2000); continue; }
            phase = readPhase((long) q[4]);
            if (phase <= 1) {
                for (int k = 0; k < BATCH && spawned < TOTAL; k++) fails = one(ix, iy, iz + 100, 3000, 15000, fails);
                state = "100 人局：出生岛补人 " + spawned + "/" + TOTAL;
            } else {
                // 开局后：等玩家落地（高度 3 秒内变化 < 0.5 米）再在附近补
                if (!Double.isNaN(lz) && Math.abs(q[2] - lz) < 50) still++; else still = 0;
                lz = q[2];
                if (still >= 2) {
                    for (int k = 0; k < BATCH && spawned < TOTAL; k++) fails = one(q[0], q[1], q[2] + 150, 5000, 30000, fails);
                    state = "100 人局：开局后补人 " + spawned + "/" + TOTAL;
                } else state = "100 人局：等落地后补人 " + spawned + "/" + TOTAL + "（阶段 " + phase + "）";
            }
            GameOverlay.refreshAny();
            if (fails >= 5) { stop(); return; }
            Thread.sleep(still >= 2 || phase <= 1 ? 2000 : 1500);
        }
        if (spawned >= TOTAL) { state = "100 人局：已满 " + (spawned + 1) + " 人"; Veh.note(state + "；最后一个 " + Veh.botInfo); }
    }

    /** 在 (cx,cy) 周围 rMin~rMax 厘米的环上刷一个，面朝圆心 */
    static int one(double cx, double cy, double z, double rMin, double rMax, int fails) throws InterruptedException {
        double a = rnd.nextDouble() * Math.PI * 2, r = rMin + rnd.nextDouble() * (rMax - rMin);
        int c = Veh.addBot(diff, cx + r * Math.cos(a), cy + r * Math.sin(a), z, Math.toDegrees(a) + 180, 1 | 2);
        if (c == 0x5b08 || c == 0x5b07) {
            spawned++;
            if (spawned == 1) Veh.note("100 人局：第一个人机 " + Veh.codeText(c) + "；" + Veh.botInfo);
            return 0;
        }
        Veh.botState = "刷人机：" + Veh.codeText(c);
        Veh.note("100 人局：刷人机失败 " + Veh.codeText(c) + "；" + Veh.botInfo);
        return fails + 1;
    }

    static void stop() { state = "100 人局：连续失败停了（" + Veh.botState + "）"; Veh.note(state); GameOverlay.refreshAny(); }

    /** 对局阶段：GameState(GM+0x2e0)+0x71c，读不到返回 -1 */
    static int readPhase(long gm) {
        if (gm < 0x10000) return -1;
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
            long gs = Server.rd(m, gm + 0x2e0);
            if (gs < 0x10000) return -1;
            return (int) (Server.rd(m, gs + 0x71c) & 0xff);
        } catch (Throwable e) { return -1; }
    }
}
