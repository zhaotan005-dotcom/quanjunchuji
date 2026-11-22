package com.zframe.inject;

import android.content.Context;
import java.io.File;

/** v4.06：开机卡住也要能看到日志。以前只在进大厅后才上传诊断 → 朋友手机卡在加载图时一条日志都没有。
 *  - 启动 6 秒：发一条很短的「启动」消息（机型/安卓/内核版本/网络是否走 VPN）。
 *  - 启动 40 秒、120 秒还没进大厅：传 boot-*（在线更新各镜像结果、更新页/启动步骤状态、游戏日志找没找到、最近 120 条框架记录、上次运行心跳）。
 *  不读游戏日志全文，不扫内存，只用已有状态；单机包（Ed.BUILD 0）照规矩不传。 */
final class BootLog {
    static volatile boolean started;
    static void start(final Context ctx) {
        if (started) return; started = true;
        if (Ed.BUILD == 0) return;   // 单机包不传；客户端包运行时 ED 也是 0，所以按 BUILD 判断
        new Thread(() -> {
            try {
                Thread.sleep(6000);
                ping(ctx);
                Thread.sleep(34000);
                if (!GameOverlay.lobbySeen) send(ctx, "40 秒没进大厅");
                Thread.sleep(80000);
                if (!GameOverlay.lobbySeen) send(ctx, "120 秒没进大厅");
            } catch (Throwable ignored) {}
        }, "zf-bootlog").start();
    }
    static String dev() {
        return android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL + " 安卓 " + android.os.Build.VERSION.RELEASE + "（SDK " + android.os.Build.VERSION.SDK_INT + "）ABI " + String.join(",", android.os.Build.SUPPORTED_ABIS);
    }
    static String net(Context ctx) {
        StringBuilder sb = new StringBuilder();
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            android.net.Network n = cm.getActiveNetwork();
            android.net.NetworkCapabilities nc = n == null ? null : cm.getNetworkCapabilities(n);
            if (nc == null) sb.append("没有网络");
            else {
                sb.append(nc.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) ? "VPN " : "")
                  .append(nc.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ? "WiFi " : "")
                  .append(nc.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) ? "流量 " : "")
                  .append(nc.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED) ? "已验证" : "未验证");
            }
            android.net.ProxyInfo pi = cm.getDefaultProxy();
            if (pi != null) sb.append(" 代理 ").append(pi.getHost()).append(':').append(pi.getPort());
            String hp = System.getProperty("https.proxyHost"); if (hp != null) sb.append(" https.proxyHost=").append(hp);
        } catch (Throwable t) { sb.append("读不到 ").append(t.getMessage()); }
        return sb.toString();
    }
    static void ping(Context ctx) {
        try {
            String msg = "启动 " + GameOverlay.VER + " " + Ed.name() + " 内核 " + ZInit.CORE_VER + (ZInit.class.getClassLoader() instanceof dalvik.system.DexClassLoader ? "（在线内核）" : "（内置内核）")
                    + "\n" + dev() + "\n网络：" + net(ctx);
            java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(LogUp.TOPIC).openConnection();
            c.setConnectTimeout(8000); c.setReadTimeout(10000); c.setRequestMethod("POST"); c.setDoOutput(true);
            c.setRequestProperty("Title", GameOverlay.VER + " " + Ed.name() + " 启动 " + android.os.Build.MODEL);
            byte[] d = msg.getBytes("UTF-8"); c.setFixedLengthStreamingMode(d.length);
            try (java.io.OutputStream o = c.getOutputStream()) { o.write(d); }
            LogCap.note("启动消息：HTTP " + c.getResponseCode()); c.disconnect();
        } catch (Throwable t) { LogCap.note("启动消息：发不出去 " + t.getMessage()); }
    }
    static void send(Context ctx, String why) {
        StringBuilder sb = new StringBuilder();
        sb.append("ZFrame ").append(GameOverlay.VER).append(' ').append(Ed.name()).append(" 开机诊断（").append(why).append("）\n");
        sb.append("内核 ").append(ZInit.CORE_VER).append(ZInit.class.getClassLoader() instanceof dalvik.system.DexClassLoader ? "（在线内核）" : "（内置内核）")
          .append("，启动后 ").append((System.currentTimeMillis() - Con.bootAt) / 1000).append(" 秒\n");
        sb.append(dev()).append('\n');
        try { sb.append("游戏 ").append(ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionName).append('\n'); } catch (Throwable ignored) {}
        sb.append("网络：").append(net(ctx)).append('\n');
        sb.append("在线更新：").append(NetCore.status).append("；done=").append(NetCore.done).append(" 超时放行=").append(NetCore.released)
          .append(" 下载=").append(NetCore.pct).append(" 已下好=").append(NetCore.readyVer).append("\n  各镜像：").append(NetCore.lastVers).append("\n  最后错误：").append(NetCore.lastErr).append('\n');
        sb.append(UpdFlow.state).append('\n').append(UpdFlow.timi).append('\n').append(UpdPage.state).append('\n').append(UpdPage.flows).append('\n');
        sb.append("引擎就绪 ").append(Con.engineSeenAt > 0 ? ((Con.engineSeenAt - Con.bootAt) / 1000) + " 秒" : "没看到").append("，开机进大厅 ").append(Con.startupLobby).append('\n');
        try { File f = Con.findUeLog(ctx); sb.append("游戏日志：").append(f == null ? "没找到" : f.getPath() + " " + f.length() + " 字节").append('\n'); } catch (Throwable t) { sb.append("游戏日志：出错 ").append(t).append('\n'); }
        sb.append("最近框架记录：\n").append(LogCap.recent());
        try { sb.append(Beat.prev(ctx)); } catch (Throwable ignored) {}
        String name = "boot-" + new java.text.SimpleDateFormat("MMdd-HHmmss", java.util.Locale.US).format(new java.util.Date()) + ".txt";
        LogUp.dir = new File(ctx.getFilesDir(), "zf_logq");
        LogUp.send(name, sb.toString());
    }
}
