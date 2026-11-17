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
        if (!want()) { if (!Con.inMatch) { players = new java.util.ArrayList<>(); baseJump.clear(); lastFind = 0; } return; }
        long now = System.currentTimeMillis();
        if (now - lastFind > 15000) { lastFind = now; find(); }
        java.util.ArrayList<Long> ps = players;
        if (ps.isEmpty()) return;
        long b = Mods.base(); if (b == 0) return;
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "rw")) {
            for (long p : ps) {
                if (!Mods.isObj(m, b, p)) continue;
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

    static void find() {
        java.util.LinkedHashSet<Long> seen = Alive.collect(1000);
        if (seen == null) { state = "全员：找人失败 " + Alive.err; return; }
        long me = GameOverlay.pawnAddr();
        java.util.ArrayList<Long> out = new java.util.ArrayList<>();
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
            Veh.Names nm = null; try { nm = Veh.Names.get(); } catch (Throwable t) {}
            for (long p : seen) {
                if (p == me) continue;   // 房主自己由 Mods 管
                long ctl = Server.rd(m, p + 0x2d0);
                if (ctl < 0x10000) continue;
                String cn = Alive.clsName(m, nm, ctl);
                if (cn.contains("PlayerController")) out.add(p);
            }
        } catch (Throwable t) { state = "全员：读出错 " + t.getMessage(); return; }
        players = out;
        state = "全员：找到加入的玩家 " + out.size() + " 个（人物共 " + seen.size() + "）";
        LogCap.note(state);
    }
}
