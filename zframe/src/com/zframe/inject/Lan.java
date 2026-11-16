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

    static String host(String map) {
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
        boolean ok = Con.exec("open " + ip);
        state = ok ? "加入：已发 open " + ip : "加入：命令没发出去";
        note(state);
        return ok ? "正在连 " + ip : "命令没发出去";
    }

    static void note(String s) {
        LogCap.note("联机 " + s);
        add(new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(new java.util.Date()) + " " + s);
    }
    static void add(String s) { synchronized (log) { if (log.size() >= 60) log.remove(0); log.add(s.length() > 260 ? s.substring(0, 260) : s); } }

    /** 日志行过滤：网络相关的都收下 */
    static void line(String l) {
        if (l.contains("LogNet") || l.contains("NetDriver") || l.contains("Listen") || l.contains("NMT_") || l.contains("PendingNet")
                || l.contains("NetConnection") || l.contains("Welcomed") || l.contains("TravelFailure") || l.contains("NetworkFailure")
                || l.contains("PreLogin") || l.contains("Login") && l.contains("NZ") || l.contains("Beacon") || l.contains("NetMode")) {
            if (l.contains("UNZMobilePlayerinput")) return;
            add(l.trim());
        }
    }
    static String text() { synchronized (log) { return log.isEmpty() ? "（空）" : String.join("\n  ", log); } }
}
