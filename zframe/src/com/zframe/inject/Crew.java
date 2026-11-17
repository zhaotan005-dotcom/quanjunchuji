package com.zframe.inject;

import java.io.RandomAccessFile;

/**
 * v2.26 房主版：把房主的 加速 / 高跳 / 无限子弹 也加到加入的玩家身上（服务器这边的人物）。
 * 原因：联机下移动和子弹都是房主（服务器）说了算。客户端只改自己 → 被服务器拉回 / 子弹对不上一直换弹。
 * 找人：借 Alive 的计数钩子（0x101a814）短暂装 1 秒，记下被数到的人物，控制器类名带 PlayerController 的才算玩家（人机是 AIController）。
 * 每 15 秒重找一次（只在开着这几项时），中间每 100 ms 写一遍：JumpZ（CMC+0x128）= 原值×倍数，CustomTimeDilation（+0x58）= 加速倍数（空中 1），
 * 当前枪 +0x890 → 子弹 0x608/0x604 = 999。
 */
final class Crew {
    static volatile String state = "全员：没开";
    static final java.util.HashMap<Long, Float> baseJump = new java.util.HashMap<>();
    static volatile java.util.ArrayList<Long> players = new java.util.ArrayList<>();
    static Thread th;
    static long lastFind;

    static boolean want() { return Lan.role == 1 && Con.inMatch && (Mods.speed != 1f || Mods.jump != 1f || Mods.infAmmo); }
    /** v2.31：新加入的玩家拉到房主身边（每人每局一次）；按钮 pull() 再拉一次 */
    static final java.util.HashSet<Long> pulled = new java.util.HashSet<>();
    static volatile String pullState = "拉人：还没有人加入";
    static volatile boolean pullAll;
    static void pull() { pullAll = true; lastFind = 0; }
    /** 日志里出现 Join succeeded：等 6 秒（人物生成）后找人并拉一次，12 秒后再补一次 */
    static volatile long joinAt;
    static volatile int joinStage;
    static volatile boolean anyJoin;
    static void joined() { anyJoin = true; joinStage = 0; joinAt = System.currentTimeMillis(); start(); }
    static void doPull(java.util.ArrayList<Long> ps) {
        boolean all = pullAll; pullAll = false;
        double[] me;
        try { me = Veh.info(); } catch (Throwable t) { me = null; }
        if (me == null) { pullState = "拉人：读不到房主位置"; return; }
        int i = 0, ok = 0, n = 0;
        for (long p : ps) {
            i++;
            if (!all && pulled.contains(p)) continue;
            n++;
            double a = Math.toRadians(me[3] + 90 * i);
            boolean r;
            try { r = Veh.teleport(p, me[0] + 200 * Math.cos(a), me[1] + 200 * Math.sin(a), me[2] + 30, me[3]); } catch (Throwable t) { r = false; }
            if (r) { ok++; pulled.add(p); }
        }
        if (n > 0) { pullState = "拉人：拉到房主身边 " + ok + "/" + n; LogCap.note(pullState); GameOverlay.refreshAny(); }
    }

    static synchronized void start() {
        if (th != null) return;
        th = new Thread(() -> {
            while (true) {
                try { tick(); } catch (Throwable t) { state = "全员：出错 " + t; }
                try { Thread.sleep(100); } catch (InterruptedException e) { return; }
            }
        }, "zf-crew");
        th.setDaemon(true); th.start();
    }

    static void tick() throws Exception {
        if (Lan.role != 1 || !Con.inMatch) { if (!Con.inMatch) { players = new java.util.ArrayList<>(); states = new java.util.ArrayList<>(); baseJump.clear(); pulled.clear(); lastFind = 0; anyJoin = false; } return; }
        long now = System.currentTimeMillis();
        // v2.31：有人加入（日志）或点了按钮才找人、拉到房主身边；开着加速/高跳/子弹时照旧每 15 秒找一次
        long ja = joinAt;
        boolean jt = false;
        if (ja != 0 && joinStage == 0 && now - ja > 6000) { joinStage = 1; jt = true; }
        else if (ja != 0 && joinStage == 1 && now - ja > 12000) { joinStage = 0; joinAt = 0; jt = true; }
        if (pullAll || jt) { lastFind = now; find(); doPull(players); return; }
        boolean wn = want();
        if (!wn && !anyJoin) return;   // v2.32：有人加入后即使没开也继续同步（把 1 倍也告诉客户端）
        if (now - lastFind > 15000) { lastFind = now; find(); }
        java.util.ArrayList<Long> ps = players;
        float code = Mods.encode(Mods.speed, Mods.jump);
        long b = Mods.base(); if (b == 0) return;
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "rw")) {
            for (long st : states) if (Mods.isObj(m, b, st) && Mods.f(m, st + Mods.PS_SCORE) != code) Mods.wf(m, st + Mods.PS_SCORE, code);
            for (long p : ps) {
                if (!Mods.isObj(m, b, p)) continue;
                // v2.32：把房主的 加速/高跳 写进这个玩家的 PlayerState.Score，客户端读到自动跟
                if (!wn) continue;
                long c = Mods.rp(m, p + Mods.P_CMC);
                if (!Mods.isObj(m, b, c)) continue;
                // 高跳
                float jz = Mods.f(m, c + Mods.C_JUMP);
                Float bj = baseJump.get(c);
                if (bj == null) { if (!(jz >= 100f && jz <= 2000f)) continue; bj = jz; baseJump.put(c, bj); }
                float wj = bj * Mods.jump;
                if (Math.abs(jz - wj) > 0.01f) Mods.wf(m, c + Mods.C_JUMP, wj);
                // 加速（空中恢复 1）
                boolean air = (Mods.rd(m, c + Mods.C_MODE) & 0xff) == Mods.MOVE_FALLING;
                float wd = Mods.speed == 1f || air ? 1f : Mods.speed;
                float td = Mods.f(m, p + Mods.P_DIL);
                if (td > 0.05f && td < 20f && Math.abs(td - wd) > 0.001f) Mods.wf(m, p + Mods.P_DIL, wd);
                // 无限子弹（服务器这边改，会同步给客户端）
                if (Mods.infAmmo) {
                    long w = Mods.rp(m, p + 0x890);
                    if (Mods.isObj(m, b, w)) {
                        int clip = Mods.rd(m, w + 0x52c);
                        if (clip > 0 && clip <= 200 && Mods.rd(m, w + 0x608) != 999) { Mods.wi(m, w + 0x608, 999); Mods.wi(m, w + 0x604, 999); }
                    }
                }
            }
        }
    }

    /** v2.32b：v2.32 实测 Alive 计数钩子 1 秒里一次都没触发（找到 0 个）。改成走 GameState.PlayerArray（GS+0x2bc，TArray<PlayerState*>）：
     *  PS 的 Owner（AActor+0x60）是控制器，类名带 PlayerController 才算玩家；控制器+0x2c0 是人物。读不到再退回 Alive。 */
    static volatile java.util.ArrayList<Long> states = new java.util.ArrayList<>();
    static void find() {
        long me = GameOverlay.pawnAddr();
        java.util.ArrayList<Long> out = new java.util.ArrayList<>(), sts = new java.util.ArrayList<>();
        int total = 0; String how = "PlayerArray";
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
            Veh.Names nm = null; try { nm = Veh.Names.get(); } catch (Throwable t) {}
            long mps = me >= 0x10000 ? Server.rd(m, me + 0x2c4) : 0;
            double[] q = null; try { q = Veh.info(); } catch (Throwable t) {}
            long gm = q != null ? (long) q[4] : 0;
            long gs = gm >= 0x10000 ? Server.rd(m, gm + 0x2e0) : 0;
            long data = gs >= 0x10000 ? Server.rd(m, gs + 0x2bc) : 0;
            int n = gs >= 0x10000 ? (int) Server.rd(m, gs + 0x2c0) : 0;
            if (data >= 0x10000 && n > 0 && n <= 200) {
                total = n;
                for (int k = 0; k < n; k++) {
                    long ps = Server.rd(m, data + 4L * k);
                    if (ps < 0x10000 || ps == mps) continue;
                    long ctl = Server.rd(m, ps + 0x60);
                    if (ctl < 0x10000) continue;
                    if (!Alive.clsName(m, nm, ctl).contains("PlayerController")) continue;
                    long p = Server.rd(m, ctl + 0x2c0); if (p < 0x10000) p = Server.rd(m, ctl + 0x2b4);
                    if (p == me) continue;
                    sts.add(ps);
                    if (p >= 0x10000) out.add(p);
                }
            } else {
                how = "计数钩子";
                java.util.LinkedHashSet<Long> seen = Alive.collect(1000);
                if (seen == null) { state = "全员：找人失败（PlayerArray " + Long.toHexString(gs) + "/" + n + "）" + Alive.err; return; }
                total = seen.size();
                for (long p : seen) {
                    if (p == me) continue;
                    long ctl = Server.rd(m, p + 0x2d0);
                    if (ctl < 0x10000) continue;
                    if (Alive.clsName(m, nm, ctl).contains("PlayerController")) { out.add(p); long ps = Server.rd(m, p + 0x2c4); if (ps >= 0x10000) sts.add(ps); }
                }
            }
        } catch (Throwable t) { state = "全员：读出错 " + t.getMessage(); return; }
        players = out; states = sts;
        String ns = "全员（" + how + "）：找到加入的玩家 " + out.size() + " 个（共 " + total + "），加速 " + Mods.speed + " 高跳 " + Mods.jump + " 子弹 " + Mods.infAmmo;
        if (!ns.equals(state)) LogCap.note(ns);
        state = ns;
    }
}
