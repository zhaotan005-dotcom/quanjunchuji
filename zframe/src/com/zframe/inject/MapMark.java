package com.zframe.inject;

import java.io.RandomAccessFile;

/**
 * v2.78 地图标点（用户：标点用不了；还要「点地图标点自动传送过去」）。
 * RE：标点 = 控制器（NZBRPlayerController）的 ServerAddNewMapSign(FVector2D InNewLoc)（exec 0x11e528c，校验 vt+0x3f0、实现 vt+0x3f4），
 *  控制器 +0xa04 是 MapSignInfo（NZBRMapSignInfo，0xc 字节：位置 X、Y + 开关），队友那边是 TeamMemberInfo 的 MapSignLocation(+0x30)/bIsMapSignEnable。
 * 这版：
 *  1. 每 0.5 秒看一眼 控制器+0xa04 那 12 字节，变了就记进诊断（看点地图到底有没有走到服务器那一步）。
 *  2. 「点标点自动传送」开着时：标点位置一变，就用游戏自己的 K2_TeleportTo 把人传到 (X, Y, 高处)，再用飞天的慢落把人放下来（不摔伤）。
 */
final class MapMark {
    static final int MI = 0xa04;
    static volatile boolean autoTp;
    static volatile String state = "标点：还没看到", log = "";
    static Thread th;
    static long lastCtl, lastUp; static String lastHex = "";

    static synchronized void start() {
        if (th != null) return;
        th = new Thread(() -> {
            while (true) {
                try { tick(); } catch (Throwable t) { state = "标点：出错 " + t; }
                try { Thread.sleep(500); } catch (InterruptedException e) { return; }
            }
        }, "zf-mapmark");
        th.setDaemon(true); th.start();
    }

    static void note(String s) {
        LogCap.note(s);
        String l = log + "\n  " + new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date()) + " " + s;
        log = l.length() > 1500 ? l.substring(l.length() - 1500) : l;
    }

    static void tick() throws Exception {
        if (!Con.inMatch) {
            if (lastCtl != 0) {   // v2.79 一局结束（回大厅）也自动传一份，标点没反应也能看到
                new Thread(() -> { try { Thread.sleep(8000); android.app.Activity a = Con.act; if (a != null) AvatarLog.export(a, "matchend-"); } catch (Throwable ignored) {} }, "zf-end-log").start();
            }
            lastCtl = 0; lastHex = ""; return; }
        long b = Mods.base(); if (b == 0) return;
        long pawn = Mods.myPawn(); if (pawn < 0x10000) return;
        float x, y; String hex; long ctl;
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
            long ps = Server.rd(m, pawn + 0x2c4); if (ps < 0x10000) return;
            ctl = Server.rd(m, ps + 0x60); if (ctl < 0x10000) return;
            if (ctl != lastCtl) {
                Veh.Names nm = Veh.Names.get();
                String cn = Alive.clsName(m, nm, ctl);
                lastCtl = ctl; lastHex = "";
                state = "标点：控制器 " + Long.toHexString(ctl) + " " + cn;
                // v2.80：记下控制器虚表里 删标点(0x3e8/0x3ec) 加标点(0x3f0 校验/0x3f4 实现) 的函数（减库基址），好离线看实现
                long vt = Server.rd(m, ctl);
                StringBuilder sb = new StringBuilder("虚表 ").append(Long.toHexString(vt - b));
                for (int off = 0x3e8; off <= 0x3f4; off += 4) sb.append(" +").append(Integer.toHexString(off)).append("=").append(Long.toHexString(Server.rd(m, vt + off) - b));
                note(sb.toString());
                if (cn == null || !cn.contains("PlayerController")) { state += "（不是玩家控制器，不看）"; lastCtl = -1; }
            }
            if (lastCtl == -1) return;
            hex = Server.bytesAt(m, ctl + MI, 12);
            x = Mods.f(m, ctl + MI); y = Mods.f(m, ctl + MI + 4);
        }
        if (lastHex.isEmpty()) { lastHex = hex; note("标点信息起始 +a04=" + hex); return; }
        if (hex.equals(lastHex)) return;
        lastHex = hex;
        note(String.format("标点变了 +a04=%s → (%.0f, %.0f)", hex, x, y));
        if (System.currentTimeMillis() - lastUp > 30000) {   // v2.79 标点有变化就自动传一份诊断（30 秒最多一次）
            lastUp = System.currentTimeMillis();
            new Thread(() -> { try { Thread.sleep(5000); android.app.Activity a = Con.act; if (a != null) AvatarLog.export(a, "mark-"); } catch (Throwable ignored) {} }, "zf-mark-log").start();
        }
        state = String.format("标点：最近一次 (%.0f, %.0f)", x, y);
        if (!autoTp || Ed.ED == 2 || Lan.role == 2) return;
        if (Float.isNaN(x) || Float.isNaN(y) || (x == 0 && y == 0) || Math.abs(x) > 2e6 || Math.abs(y) > 2e6) return;
        double[] me = Veh.info();
        double z = Math.max((me == null ? 0 : me[2]) + 3000, 20000);   // 200 米高处落下，限速 10 米/秒（不摔伤）
        boolean ok = Veh.teleport(pawn, x, y, z, me == null ? 0 : me[3]);
        note("自动传送到标点 " + (ok ? "成功" : "失败"));
        if (ok) Fly.softLand();
    }
}
