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
    static volatile String pre = "没装";
    static void preinstall() {
        String a = Mods.write(SETF, SETF_O, SETF_N, true), b = Mods.write(CHEAT, CHEAT_O, CHEAT_N, true), c = Mods.write(JOINC, JOINC_O, JOINC_N, true);
        pre = "置标志 " + a + "，开房检查 " + b + "，加入检查 " + c;
        LogCap.note("联机补丁（启动时）：" + pre);
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
        role = 1; Bots.auto = false; Server.quiet(); beat("开房加载");
        boolean ok = Con.exec("open " + map + "?listen");
        state = ok ? "开房：已发 open " + map.substring(map.lastIndexOf('/') + 1) + "?listen，等加载" : "开房：命令没发出去";
        note(state);
        return ok ? "正在开房，加载完把本机 IP 告诉另一台" : "命令没发出去";
    }

    static String join(String ip) {
        ip = ip == null ? "" : ip.trim();
        int k = ip.indexOf('（'); if (k > 0) ip = ip.substring(0, k);
        if (!ip.matches("\\d{1,3}(\\.\\d{1,3}){3}(:\\d{1,5})?")) return "IP 格式不对";
        lastIp = ip;
        if (ip.indexOf(':') < 0) ip += ":7777";
        unlock();
        role = 2; Bots.auto = false; Server.quiet(); beat("加入加载");
        boolean ok = Con.exec("open " + ip);
        state = ok ? "加入：已发 open " + ip : "加入：命令没发出去";
        note(state);
        return ok ? "正在连 " + ip : "命令没发出去";
    }

    static String roleText() { return role == 1 ? "房主（服务器）" : role == 2 ? "已加入别人的房间" : "单机"; }
    static void note(String s) {
        LogCap.note("联机 " + s); Server.mark("联机 " + s);
        add(new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(new java.util.Date()) + " " + s);
    }
    static void add(String s) { synchronized (log) { if (log.size() >= 60) log.remove(0); log.add(s.length() > 260 ? s.substring(0, 260) : s); } }

    /** 日志行过滤：网络相关的都收下 */
    static void line(String l) {
        if (l.contains("LogNet") || l.contains("NetDriver") || l.contains("Listen") || l.contains("NMT_") || l.contains("PendingNet")
                || l.contains("NetConnection") || l.contains("Welcomed") || l.contains("TravelFailure") || l.contains("NetworkFailure")
                || l.contains("PreLogin") || l.contains("Login") && l.contains("NZ") || l.contains("Beacon") || l.contains("NetMode")) {
            if (l.contains("UNZMobilePlayerinput") || l.contains("AvaListener") || l.contains("EventListener") || l.contains("KeyLobbyMsg")) return;
            add(l.trim());
        }
    }
    static String text() { synchronized (log) { return log.isEmpty() ? "（空）" : String.join("\n  ", log); } }
}
