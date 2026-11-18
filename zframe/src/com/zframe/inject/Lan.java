package com.zframe.inject;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;

/** v2.03 局域网联机（实验）：只用引擎自带的 listen server / open IP，不连任何外部服务器。 */
final class Lan {
    static volatile String state = "还没试";
    static volatile String lastIp;
    static final java.util.List<String> log = new java.util.ArrayList<>();

    static String ip() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                String n = ni.getName();
                if (!(n.startsWith("wlan") || n.startsWith("ap") || n.startsWith("swlan") || n.startsWith("eth"))) continue;
                for (InetAddress ad : Collections.list(ni.getInetAddresses()))
                    if (ad instanceof Inet4Address) return ad.getHostAddress() + "（" + n + "）";
            }
        } catch (Throwable ignored) {}
        return "没连 Wi-Fi";
    }

    /** 引擎在 Browse 里：用过控制台命令（作弊）+ URL 带 Listen → TravelFailure CheatCommands。0x2e0a084 beq→b 跳过这个检查。 */
    static final long CHEAT = 0x2e0a084L; static final String CHEAT_O = "2000000a", CHEAT_N = "200000ea";
    /** v2.05：v2.04 只改了上面这一处，实测还是 CheatCommands。改成启动时（还没执行过任何控制台命令、代码还没进指令缓存）就装三处：
     *  1. 0x2a110b8 控制台 Exec 里把「用过作弊」标志（bss 0x5359ad8）置 1 的 mov r2,#1 → mov r2,#0，标志永远不会变成 1；
     *  2. 0x2e0a084 Browse 里的检查 beq→b；3. 0x2bffb78 PendingNetGame::InitNetDriver（加入时）的检查 beq→b。 */
    static final long SETF = 0x2a110b8L; static final String SETF_O = "0120a0e3", SETF_N = "0020a0e3";
    static final long JOINC = 0x2bffb78L; static final String JOINC_O = "1a00000a", JOINC_N = "1a0000ea";
    static final long FLAG = 0x5359ad8L;
    /** v2.12：开房闪退现场（Bugly tomb）= 0x276f058 ldr r1,[r0,#4]，r0=0。所在函数是 UNavigationSystem::GetDefaultNavDataInstance：
     *  找不到导航数据就 CreateNavigationDataInstance(默认 "Default" 35/144) → 返回空 → 给空指针加 RF_Transient 标志 → 崩。
     *  把这三条（读标志/或 0x40/写回）换成 nop：返回空就空着，不加标志。 */
    /** v2.13：房主进图约 2 分钟弹「网络异常无法收到结算数据」：日志 NZGameMode.cpp:2452 All players have quit game! NumPlayers[1]。
     *  0xc898e0 每 2 秒查一次：对局中且 vtbl+0x3f0（有连接的玩家数）==0 → 过 AllQuitEndGameProtectTime(120s) 就结束。
     *  房主自己的角色没有网络连接，算成 0 人。把 0xc89960 的 bne 改成 b，永远不走这条。 */
    static final long QUIT = 0xc89960L; static final String QUIT_O = "0d00001a", QUIT_N = "0d0000ea";
    static final long NAV = 0x276f058L; static final String NAV_O = "041090e5401081e3041080e5", NAV_N = "00f020e300f020e300f020e3";
    /** v2.17：刷到身上的枪丢出去闪退（tomb：0x25cb8e8 vldr s22,[r0,#0x1c]，r0=0）。函数 0x25cb880 = Recast dtNavMeshQuery::queryPolygons，
     *  [this] = m_nav 是空的（单机没导航网格），被 0x25cb4a0 findNearestPoly 调。改成：m_nav 为空就返回 DT_FAILURE。
     *  钩子 0x25cb8e8 → b 0x171e700（废掉的 GM 面板函数里的空地，在商店 cave 0x171e630..68f 后面）。 */
    static final long NQ = 0x25cb8e8L, NQ_CAVE = 0x171e700L;
    static final String NQ_O = "07ba90ed", NQ_N = "844bc5ea";
    static final String NQ_CAVE_O = "0300009a010189e0011048e00111a0e18f39c6eb0000a0e3", NQ_CAVE_N = "000050e30100000a07ba90ed76b43aea0201a0e32ab53aea";
    /** v2.36：客户端上不了车（ClientUseVehicleFailed / 提示座位有人）。服务器判能不能上车（VehicleInterface CanUse 0xdafe1c）：
     *  联机下要求人物+0xb84 ClosedVehicle == 这辆车，但 ClosedVehicle 只在「本机第一个玩家」的每帧检查（0xdd7c2c → 0xd1a06c）里写，
     *  房主机器上加入玩家的人物永远是空 → 永远失败。把「ClosedVehicle 为空就失败」的 beq 0xdb0430 改成 beq 0xdb0400（跳过距离/视线检查，
     *  只查车还在、有空座），和 ClosedVehicle 是别的车时游戏自己走的路一样。所有车（四轮/摩托/船）共用这个函数。 */
    static final long CAR = 0xdb00f0L; static final String CAR_O = "ce00000a", CAR_N = "c200000a";
    /** v2.67（服务端分支移植）：服务端自己不能上车（能上下不来）。beq 改成 b 0x171e7fc cave：ClosedVehicle 空 → 0xdb0400；不空且 NetMode==2 → 0xdb0430 返回 0；其他 → 原版检查。只在服务端装。 */
    static final long CARC = 0x171e7fcL;
    static final String CARC_O = "0400a0e10610a0e1eef7eaeb47ffffea906aa003", CARC_N = "000057e3fe46da0a020050e30847da0a3846daea", CAR_H = "c1b925ea";
    static volatile String pre = "没装";
    static void preinstall() {
        String a = Mods.write(SETF, SETF_O, SETF_N, true), b = Mods.write(CHEAT, CHEAT_O, CHEAT_N, true), c = Mods.write(JOINC, JOINC_O, JOINC_N, true);
        String d = Mods.write(NAV, NAV_O, NAV_N, true), e = Mods.write(QUIT, QUIT_O, QUIT_N, true);
        String g = Mods.write(NQ_CAVE, NQ_CAVE_O, NQ_CAVE_N, true);
        String car;
        if (Ed.BUILD == 1) {
            car = Mods.write(CARC, CARC_O, CARC_N, true);
            if ("ok".equals(car)) { car = Mods.write(CAR, CAR_O, CAR_H, true); if ("ok".equals(car)) car = "ok（服务端不能上车）"; }
            else car = "cave " + car + "，退回：" + Mods.write(CAR, CAR_O, CAR_N, true);
        } else car = Mods.write(CAR, CAR_O, CAR_N, true);
        if ("ok".equals(g)) g = Mods.write(NQ, NQ_O, NQ_N, true);
        pre = "导航查询空指针 " + g + "，置标志 " + a + "，开房检查 " + b + "，加入检查 " + c + "，导航空指针 " + d + "，全员退出结束 " + e + "，加入的人上车 " + car;
        LogCap.note("联机补丁（启动时）：" + pre);
        Loot.preinstall();   // v2.20 房主版：地图物资/门（ServerOnly 层）
    }
    static String unlock() {
        String r = Mods.write(CHEAT, CHEAT_O, CHEAT_N, true);
        Mods.write(SETF, SETF_O, SETF_N, true); Mods.write(JOINC, JOINC_O, JOINC_N, true);
        String f = Mods.pokeByte(Mods.base() + FLAG, 0);
        note("联机补丁：" + pre + "；清标志 " + f);
        return r;
    }
    /** 0 = 单机，1 = 房主（服务器），2 = 加入的客户端。回大厅清零。 */
    static volatile int role;
    static volatile boolean busy;
    /** v2.06：开房后每秒把内存和进度写进持久的步骤文件（闪退后下次导出能看到死在哪、是不是内存爆了） */
    static void beat(String what) {
        busy = true;
        final long t0 = System.currentTimeMillis();
        Thread t = new Thread(() -> {
            for (int i = 0; i < 120 && busy; i++) {
                String m = "";
                try { for (String l : java.nio.file.Files.readAllLines(new java.io.File("/proc/self/status").toPath()))
                    if (l.startsWith("VmSize") || l.startsWith("VmRSS") || l.startsWith("VmSwap")) m += l.replaceAll("\\s+", " ") + " "; } catch (Throwable ignored) {}
                Server.mark(what + " " + (System.currentTimeMillis() - t0) / 1000 + "s " + m + (Con.inMatch ? "已进图" : ""));
                if (Con.inMatch && i > 3) { busy = false; break; }
                try { Thread.sleep(1000); } catch (InterruptedException e) { break; }
            }
            busy = false;
        }, "zf-lanbeat");
        t.setDaemon(true); t.start();
    }
    static String host(String map) {
        String u = unlock(); if (!"ok".equals(u)) return "开房失败：" + u;
        role = 1; Give.serve(); Bots.auto = false; Server.quiet(); beat("开房加载");
        // v2.93 用户：服务端一直收人。引擎 GameSession.MaxPlayers（+0x2b4）默认很小，满了 ApproveLogin 回「Server full.」→ 开房带 ?MaxPlayers=100（InitOptions 0x2a011c4 读这个选项）
        boolean ok = Con.exec("open " + map + "?listen?MaxPlayers=100");
        state = ok ? "开房：已发 open " + map.substring(map.lastIndexOf('/') + 1) + "?listen，等加载" : "开房：命令没发出去";
        note(state);
        return ok ? "正在开房，加载完把本机 IP 告诉另一台" : "命令没发出去";
    }

    static String join(String ip) {
        ip = ip == null ? "" : ip.trim();
        int k = ip.indexOf('（'); if (k > 0) ip = ip.substring(0, k);
        // v2.26 用户：域名不自己解析，原样交给游戏（open 域名:端口）
        if (!ip.matches("[A-Za-z0-9.-]+(:\\d{1,5})?")) return "地址格式不对，要像 frp-put.com:47937 或 192.168.1.5";
        lastIp = ip;
        if (ip.indexOf(':') < 0) ip += ":7777";
        // v2.30：v2.26 把域名原样交给 open，实测连不上（游戏自己不解析域名）。
        // 改回后台把域名解析成数字 IP 再 open，界面上照样只显示域名。
        if (ip.matches(".*[A-Za-z].*")) {
            final String dom = ip; final int c = dom.lastIndexOf(':');
            final String host = dom.substring(0, c), port = dom.substring(c + 1);
            note("解析域名 " + dom);
            new Thread(() -> {
                String num = null;
                try { for (java.net.InetAddress x : java.net.InetAddress.getAllByName(host)) if (x instanceof java.net.Inet4Address) { num = x.getHostAddress(); break; } }
                catch (Throwable t) { note("域名解析失败 " + t); }
                final String r = num;
                GameOverlay.MAIN.post(() -> {
                    String msg;
                    if (r == null) msg = "域名解析不出来，检查网络";
                    else { note(dom + " → " + r); msg = openJoin(r + ":" + port, dom); }
                    android.app.Activity a = Con.act; if (a != null) GameOverlay.toast(a, msg);
                });
            }, "zf-dns").start();
            state = "加入：正在连 " + dom;
            return "正在连 " + dom;
        }
        return openJoin(ip, ip);
    }

    private static String openJoin(String ip, String shown) {
        unlock();
        role = 2; Mods.startMover(); Bots.auto = false; Server.quiet(); beat("加入加载");
        boolean ok = Con.exec("open " + ip);
        state = ok ? "加入：已发 open " + shown : "加入：命令没发出去";
        note(ok ? "加入：open " + ip : state);
        return ok ? "正在连 " + shown : "命令没发出去";
    }

    /** v2.26：回大厅（房主 = 关房间；客户端 = 退出房间）。游戏设置里退出在联机版卡住，这里直接 open 大厅图 */
    static String leave() {
        if (!Con.inMatch) return "已经在大厅";
        boolean ok = Con.exec("open " + Con.LOBBY);
        note(ok ? "回大厅：已发 open 大厅" : "回大厅：命令没发出去");
        return ok ? "正在回大厅" : "命令没发出去";
    }
    static String roleText() { return role == 1 ? "服务端（服务器）" : role == 2 ? "已加入别人的房间" : "单机"; }
    static void note(String s) {
        LogCap.note("联机 " + s); Server.mark("联机 " + s);
        add(new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(new java.util.Date()) + " " + s);
    }
    static void add(String s) { synchronized (log) { if (log.size() >= 150) log.remove(0); log.add(s.length() > 260 ? s.substring(0, 260) : s); } }

    /** 日志行过滤：网络相关的都收下 */
    static void line(String l) {
        if (role == 1 && l.contains("Join succeeded")) Crew.joined();
        try { trig(l); } catch (Throwable ignored) {}   // v2.31：有人进来 → 房主找人、拉到身边
        if (l.contains("LogNet") || l.contains("NetDriver") || l.contains("Listen") || l.contains("NMT_") || l.contains("PendingNet")
                || l.contains("NetConnection") || l.contains("Welcomed") || l.contains("TravelFailure") || l.contains("NetworkFailure")
                || l.contains("PreLogin") || l.contains("Login") && l.contains("NZ") || l.contains("Beacon") || l.contains("NetMode") || l.contains("Reconnect") || l.contains("ReLogin") || l.contains("Kick") || l.contains("Reject") || l.contains("Duplicate") || l.contains("GameMode") && l.contains("Login")) {
            if (l.contains("UNZMobilePlayerinput") || l.contains("AvaListener") || l.contains("EventListener") || l.contains("KeyLobbyMsg") || l.contains("per Net Tick, too much") || l.contains("CheckNetRecvState")) return;   // v2.87：刷屏行挤掉了加入/重连的关键行
            add(l.trim());
        }
    }
    /** v2.88 用户：不要让我点导出诊断 → 断线/加入失败/换弹循环时自动导出上传（同类 90 秒内只传一次，等 8 秒让后面的日志进来） */
    static final java.util.Map<String, Long> upAt = new java.util.HashMap<>();
    static void autoUp(String kind) {
        long now = System.currentTimeMillis();
        synchronized (upAt) { Long t = upAt.get(kind); if (t != null && now - t < 90000) return; upAt.put(kind, now); }
        new Thread(() -> { try { Thread.sleep(8000); android.app.Activity a = Con.act; if (a != null) AvatarLog.export(a, kind + "-"); } catch (Throwable ignored) {} }, "zf-auto-" + kind).start();
    }
    static void trig(String l) {
        if (l.contains("NetworkFailure") || l.contains("TravelFailure") || l.contains("ConnectionLost") || l.contains("Connection Closed") || l.contains("PreLogin failure") || l.contains("Closing connection") && Lan.role == 1) autoUp("net");
        else if (l.contains("Force set local front ammo count") || l.contains("ServerAmmo=0")) autoUp("ammo");
    }
    static String text() { synchronized (log) { return log.isEmpty() ? "（空）" : String.join("\n  ", log); } }
}
