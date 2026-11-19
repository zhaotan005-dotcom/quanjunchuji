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
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "rw")) {
            long ps = Server.rd(m, pawn + 0x2c4); if (ps < 0x10000) return;
            ctl = Server.rd(m, ps + 0x60); if (ctl < 0x10000) return;
            if (ctl != lastCtl) {
                Veh.Names nm = Veh.Names.get();
                String cn = Alive.clsName(m, nm, ctl);
                lastCtl = ctl; lastHex = ""; fixTry = 0;
                state = "标点：控制器 " + Long.toHexString(ctl) + " " + cn;
                // v2.80：记下控制器虚表里 删标点(0x3e8/0x3ec) 加标点(0x3f0 校验/0x3f4 实现) 的函数（减库基址），好离线看实现
                long vt = Server.rd(m, ctl);
                StringBuilder sb = new StringBuilder("虚表 ").append(Long.toHexString(vt - b));
                for (int off = 0x3e8; off <= 0x3f8; off += 4) sb.append(" +").append(Integer.toHexString(off)).append("=").append(Long.toHexString(Server.rd(m, vt + off) - b));
                note(sb.toString());
                note(install(m, b, vt));
                findTeam(m, b, ps, ctl);
                if (cn == null || !cn.contains("PlayerController")) { state += "（不是玩家控制器，不看）"; lastCtl = -1; }
            }
            if (lastCtl == -1) return;
            fixTeam(m, b);
            int cnt = (int) Server.rd(m, b + MCAVE + DATA);
            if (inst.contains("已补") && cnt != lastCnt) {
                if (lastCnt >= 0 || cnt != 0) {
                    long th = Server.rd(m, b + MCAVE + DATA + 12);
                    note(String.format("加标点被调用 %d 次，最后一次 (%.0f, %.0f) 对象 %s%s", cnt, Mods.f(m, b + MCAVE + DATA + 4), Mods.f(m, b + MCAVE + DATA + 8),
                        Long.toHexString(th), th == ctl ? "（本机控制器）" : "（不是本机控制器：" + Alive.clsName(m, Veh.Names.get(), th) + "）"));
                    autoLog();
                }
                lastCnt = cnt;
            }
            hex = Server.bytesAt(m, ctl + MI, 12);
            x = Mods.f(m, ctl + MI); y = Mods.f(m, ctl + MI + 4);
        }
        if (lastHex.isEmpty()) { lastHex = hex; note("标点信息起始 +a04=" + hex); return; }
        if (hex.equals(lastHex)) return;
        lastHex = hex;
        note(String.format("标点变了 +a04=%s → (%.0f, %.0f)", hex, x, y));
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "rw")) { syncTeam(m, ctl); } catch (Throwable t) { note("队伍信息写入出错 " + t); }
        autoLog();
        state = String.format("标点：最近一次 (%.0f, %.0f)", x, y);
        if (!autoTp || Ed.ED == 2 || Lan.role == 2) return;
        if (Float.isNaN(x) || Float.isNaN(y) || (x == 0 && y == 0) || Math.abs(x) > 2e6 || Math.abs(y) > 2e6) return;
        double[] me = Veh.info();
        double z = Math.max((me == null ? 0 : me[2]) + 3000, 20000);   // 200 米高处落下，限速 10 米/秒（不摔伤）
        boolean ok = Veh.teleport(pawn, x, y, z, me == null ? 0 : me[3]);
        note("自动传送到标点 " + (ok ? "成功" : "失败"));
        if (ok) Fly.softLand();
    }

    /** v2.81：游戏里 ServerAddNewMapSign/ServerRemoveMapSign 的实现是空的（vt+0x3f4/+0x3ec = 0xb63518「return 1」），标点永远存不下来、落在地图左上角。
     *  补两段：加标点 把 InNewLoc(r1,r2) 写到 控制器+0xa04/+0xa08、+0xa0c(bEnable)=1；删标点 +0xa0c=0。放 SLACK+0x3d0（v2.81 用的 +0xa90 那里其实是数据，毒圈 +0x990 装不上也是这个原因），改这个控制器类的虚表两格。 */
    // v2.98：挪到 SLACK+0x380（Prop 地上模型 cave 代码+字面量实际到 0x380，0x370 会把它末尾 4 个字面量盖掉）；0x3d0 起是放下修复 cave
    // v2.85：挪到 SLACK+0x370（Prop 用到 0x368，Veh 从 0x400），加标点顺便记 次数/x/y/this 到 +0x30（看 RPC 到底有没有被调）
    static final long MCAVE = Server.SLACK + 0x380, STUB = 0xb63518L, DATA = 0x30, DELO = 0x40;
    static final String ADD = "041a80e5082a80e50130a0e30c3ac0e518c08fe200309ce5013083e200308ce504108ce508208ce50c008ce51eff2fe1", DEL = "0030a0e30c3ac0e51eff2fe1";
    static int lastCnt = -1;
    static void autoLog() {
            if (System.currentTimeMillis() - lastUp > 30000) {   // v2.79 标点有变化就自动传一份诊断（30 秒最多一次）
            lastUp = System.currentTimeMillis();
            new Thread(() -> { try { Thread.sleep(5000); android.app.Activity a = Con.act; if (a != null) AvatarLog.export(a, "mark-"); } catch (Throwable ignored) {} }, "zf-mark-log").start();
        }
    }
    static volatile String inst = "";
    static boolean put(RandomAccessFile m, long a, byte[] w) throws java.io.IOException {
        byte[] cur = new byte[w.length]; m.seek(a); m.readFully(cur);
        if (java.util.Arrays.equals(cur, w)) return true;
        m.seek(a); m.write(w);
        if (a == Mods.base() + MCAVE) { m.seek(a + DATA); m.write(new byte[16]); }
        m.seek(a); m.readFully(cur); return java.util.Arrays.equals(cur, w);
    }
    static String install(RandomAccessFile m, long b, long vt) throws java.io.IOException {
        // v2.83：SLACK 这段是代码段最后一页里文件自带的数据段副本（数据段另在 0x4a2e528 映射），不是 0 但可以随便写
        if (!put(m, b + MCAVE, Patches.hex(ADD)) || !put(m, b + MCAVE + DELO, Patches.hex(DEL))) return inst = "补标点：写不进去";
        long add = b + MCAVE, del = b + MCAVE + DELO;
        long a4 = Server.rd(m, vt + 0x3f4), ac = Server.rd(m, vt + 0x3ec);
        String r = "补标点：";
        if (a4 == b + STUB) { Mods.wi(m, vt + 0x3f4, (int) add); r += "加标点已补 "; } else if (a4 == add) r += "加标点已补过 "; else r += "加标点原来不是空的(" + Long.toHexString(a4 - b) + ") ";
        if (ac == b + STUB) { Mods.wi(m, vt + 0x3ec, (int) del); r += "删标点已补"; } else if (ac == del) r += "删标点已补过"; else r += "删标点原来不是空的(" + Long.toHexString(ac - b) + ")";
        return inst = r;
    }

    /** 队伍对象（类名带 Team 的，+0x2f4 是 TeamMemberInfoList，每项 0x50：Location +0x14、bIsMapSignEnable +0x2c?、MapSignLocation +0x30） */
    static long team;
    static void findTeam(RandomAccessFile m, long b, long ps, long ctl) throws Exception {
        team = 0;
        Veh.Names nm = Veh.Names.get();
        StringBuilder sb = new StringBuilder("找队伍：");
        for (long o : new long[]{ps, ctl}) {
            for (int off = 0x200; off < 0xc00 && team == 0; off += 4) {
                long q = Server.rd(m, o + off);
                if (q < 0x10000 || q >= 0xffff0000L) continue;
                String cn = Alive.clsName(m, nm, q);
                if (cn != null && cn.contains("Team") && !cn.contains("Component") && !cn.contains("Widget")) {
                    team = q; sb.append(o == ps ? "PS+" : "控制器+").append(Integer.toHexString(off)).append(" ").append(cn);
                }
            }
        }
        if (team == 0) { note(sb.append("没找到").toString()); return; }
        long arr = Server.rd(m, team + 0x2f4); int n = (int) Server.rd(m, team + 0x2f8);
        sb.append(" 成员 ").append(n);
        if (arr > 0x10000 && n > 0 && n <= 4) for (int i = 0; i < n; i++) sb.append("\n    [").append(i).append("] ").append(Server.bytesAt(m, arr + 0x50L * i, 0x50));
        note(sb.toString());
    }
    /** v3.22 用户：标点联机能用、单机不行。原因：单机（独立模式）里队伍的 TeamMemberInfoList 一直是 0 个成员（诊断「找队伍：PS+35c NZBRTeamInfo 成员 0」），
     *  这个列表只在客户端收到同步时由 OnTeamCharacterRep → 0x1020614（按队伍成员 +0x300/+0x304 重建列表）建；每帧 0x102085c 再把控制器 MapSignInfo(+0xa04/+0xa0c) 抄进列表。
     *  单机没人触发 OnRep → 列表空 → 地图上没有自己这一项，标点显示不出来。修：单机时列表是 0 而成员数 >0，就在游戏线程调一次 0x1020614(队伍)。 */
    static final long REBUILD = 0x1020614L;
    static long fixAt; static int fixTry;
    static void fixTeam(RandomAccessFile m, long b) throws Exception {
        if (team == 0 || Lan.role != 0 || Ed.ED == 2) return;
        long now = System.currentTimeMillis();
        if (now - fixAt < 3000 || fixTry >= 40) return;
        int list = (int) Server.rd(m, team + 0x2f8), mem = (int) Server.rd(m, team + 0x304);
        if (list != 0 || mem <= 0 || mem > 4) return;
        fixAt = now; fixTry++;
        long[] r = Veh.ucall(team, 0, 0, 0, null, b + REBUILD);
        note("单机补队伍成员列表：成员 " + mem + "，调用 " + (r[0] == 0x5b0e ? "ok" : "没反应") + "，现在列表 " + Server.rd(m, team + 0x2f8) + " 个");
    }
    static void syncTeam(RandomAccessFile m, long ctl) throws java.io.IOException {
        if (team == 0) return;
        long arr = Server.rd(m, team + 0x2f4); int n = (int) Server.rd(m, team + 0x2f8);
        if (arr < 0x10000 || n != 1) { note("队伍成员 " + n + " 个，不改"); return; }   // 单机只有自己一个
        int x = Mods.rd(m, ctl + MI), y = Mods.rd(m, ctl + MI + 4), en = Mods.rd(m, ctl + MI + 8) & 0xff;
        Mods.wi(m, arr + 0x30, x); Mods.wi(m, arr + 0x34, y);
        int w = Mods.rd(m, arr + 0x2c); Mods.wi(m, arr + 0x2c, (w & ~0xff) | en);
        note("队伍信息已同步：" + Server.bytesAt(m, arr, 0x50));
    }
}
