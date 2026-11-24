package com.zframe.inject;

import android.app.Activity;
import android.content.ContentValues;
import android.net.Uri;
import android.provider.MediaStore;
import java.io.*;

/** v1.56 灰色人物诊断：按一下，把游戏日志里和人物/衣服有关的行存到 下载/ZFrame/avatar-*.txt。平时什么都不做。 */
public class AvatarLog {

    static String export(Activity a) { manual = true; try { return export(a, "avatar-"); } finally { manual = false; } }
    /** v3.36 用户：单机没有导出诊断 → 手动点的照样导出（存手机 下载/ZFrame），自动的仍关 */
    static volatile boolean manual;
    /** v3.38：训练岛进图 50 秒后自动传一份诊断（用户截图：地形全黑、退出没反应；不存手机） */
    static void autoTrain(Activity a) {
        if (!Ed.uploads()) return;
        new Thread(() -> { try { Thread.sleep(50000); if (Con.training && Con.inMatch) { manual = true; try { export(a, "train-"); } finally { manual = false; } } } catch (Throwable ignored) {} }, "zf-traindiag").start();
    }
    /** v3.22 用户：单机不需要记录日志，也别往手机里放东西 → 单机（ED 0 且没加入别人）不导出、不上传、不留队列 */
    static boolean off() { return Ed.ED == 0 && Lan.role != 2; }
    static String export(Activity a, String prefix) {
        if (!Ed.uploads()) return "单机/客户端不上传日志";   // v4.08 用户：只有服务端上传（手动/自动都一样）
        if (off() && !manual) return "";
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("ZFrame " + GameOverlay.VER + " " + Ed.name() + " 诊断\n").append(Prop.useState).append("\n用道具日志：").append(Prop.lastUseLog).append("\n").append(Prop.diagText()).append("\n").append(Mods.hallState).append('\n').append(Mods.earlyState).append('\n').append(PickAnim.report()).append('\n').append(Flow.natState).append('\n');
            sb.append(Veh.state).append("\n  ").append(Veh.botState).append("\n  ").append(Veh.tableInfo).append("\n");
            sb.append("人机：").append(Bots.state).append("，难度 ").append(Veh.DIFF[Bots.diff]).append("，已刷 ").append(Bots.spawned).append("，阶段 ").append(Bots.phase)
              .append("\n  最后一个人机：").append(Veh.botInfo).append("\n  装备：").append(Bots.equipState).append("\n  导航网格：").append(Con.navInfo).append("\n  服务器关卡：").append(Con.layerStatus())
              .append("\n  人机日志：\n  ").append(Con.botLog()).append("\n  ").append(Flow.state).append("\n  ").append(Flow.air).append("\n  上一局：").append(Flow.last).append("\n  ").append(Bots.countText()).append("\n  ").append(Bots.outfitState).append("\n  航线/毒圈日志：\n  ").append(Con.flowLog()).append("\n  画质：").append(Gfx.state).append("\n  联机：").append(Lan.roleText()).append("，").append(Lan.pre).append("，").append(Lan.state).append("，本机 ").append(Lan.ip()).append("\n  ").append(ModeProbe.state).append("\n  地图物资/门：").append(Loot.text()).append("\n  ").append(Alive.state).append("\n  ").append(Phantom.state).append("\n  ").append(Crew.state).append("\n  ").append(Crew.pullState).append("\n  ").append(Lan.ammoPatch).append("\n  ").append(Lan.adpPatch).append("\n  ").append(Crew.ammoState).append("\n  ").append(NetRate.state).append("\n  ").append(Crew.teamState).append("\n  ").append(Loot.flareState).append("\n  ").append(Flare.text()).append("\n  ").append(Jetpack.state).append("\n  ").append(Mods.zoneNow()).append("\n  ").append(Trace.text()).append("\n  ").append(DuelArena.state).append("\n  ").append(HostMatch.soloState).append("\n  ").append(Box.state).append("\n  ").append(HostMatch.state).append("\n  ").append(GameOverlay.attText()).append("\n  ").append(Give.state).append("\n  跟服务端：").append(Mods.syncState).append("\n  联机日志：\n  ").append(Lan.text()).append("\n  丢东西日志：\n  ").append(Con.dropLog()).append("\n  ").append(Fly.state).append("\n  ").append(MapMark.state).append("，").append(MapMark.inst).append("\n  标点日志：").append(MapMark.log).append("\n");
            long b = Mods.base();
            sb.append(UpdFlow.state).append("\n").append(UpdPage.state).append("\n").append(UpdPage.creep).append("\n").append(Server.preState).append("\n").append(Server.connState).append("\n").append(LobbyLoad.state).append("\n").append(UpdFlow.timi).append("\n").append(UpdPage.flowTypes).append("\n").append(UpdPage.flows).append("\n").append(Island.state).append("\n").append(Island.setup).append("\n").append(Island.block).append("\n").append(Island.track).append("\n").append(Island.stream).append("\n").append(Island.range).append("\n").append(Island.logic).append("\n").append(Island.pickup).append("\n").append(Island.guns).append("\n").append(Island.gunTables).append("\n").append(Island.restock).append("\n").append(NoFall.state).append("\n").append(MinHp.state).append("\n").append(Island.spawn).append("\n").append(Island.vehicles).append("\n").append(Island.fix).append("\n").append(Island.caveText()).append("\n").append(Bots.outfitState).append("\n训练岛关键日志：\n  ").append(Island.seenLog).append("\n").append(Parabola.state).append("\n").append(Parabola.aim).append("\n").append(Parabola.crouch).append("\n").append(ThrowHud.state).append("\n").append(Room.text()).append("\n").append("训练岛模式 ").append(Con.training).append("\n");
            sb.append("libUE4=").append(Long.toHexString(b)).append('\n');
            sb.append("入口 1503230=").append(peek(b, 0x1503230L, 4)).append("  仓库 1877bfc=").append(peek(b, 0x1877bfcL, 4)).append(" 171e630=").append(peek(b, 0x171e630L, 12)).append("\n").append(Mods.storeInfo()).append("\n").append(Mods.wearState).append("\n").append("  cave 171e610=").append(peek(b, 0x171e610L, 32))
              .append("\n风衣 1503680=").append(peek(b, 0x1503680L, 4)).append("  171e600=").append(peek(b, 0x171e600L, 16)).append("\n").append(Server.report()).append("\n");
            sb.append(WorldWatch.text()).append('\n').append(Gfx.state).append('\n');
            sb.append(crashInfo()).append('\n');
            // v4.07：不读游戏日志（普通手机没有）。换成框架自己最近 120 条记录
            sb.append("== 框架最近记录 ==\n").append(LogCap.recent());
            sb.append(resources(a));
            return "已上传 " + save(a, sb.toString(), prefix);
        } catch (Throwable t) { return "导出失败：" + t.getMessage(); }
    }

    /** v4.09：不读任何日志（不读 logcat、不读游戏日志）。只留系统 API 的退出原因、游戏私有目录的崩溃文件和心跳。只服务端用。 */
    static String crashInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append(crashFiles());
        sb.append(exitInfo());
        try { if (Con.act != null) sb.append(Beat.prev(Con.act)); } catch (Throwable ignored) {}
        return sb.toString();
    }

    /** v3.99：系统记的最近几次进程退出原因（Android 11+ ApplicationExitInfo）：被系统杀（内存）/ 原生崩溃 / 信号，原生崩溃带系统 tombstone（gzip+base64） */
    static String exitInfo() {
        StringBuilder sb = new StringBuilder("== 系统记的退出原因 ==\n");
        try {
            android.content.Context c = Con.act;
            if (c == null || android.os.Build.VERSION.SDK_INT < 30) { sb.append("拿不到\n"); return sb.toString(); }
            android.app.ActivityManager am = (android.app.ActivityManager) c.getSystemService(android.content.Context.ACTIVITY_SERVICE);
            java.util.List<android.app.ApplicationExitInfo> l = am.getHistoricalProcessExitReasons(c.getPackageName(), 0, 6);
            java.text.SimpleDateFormat df = new java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.US);
            int traced = 0;
            for (android.app.ApplicationExitInfo e : l) {
                sb.append(df.format(new java.util.Date(e.getTimestamp()))).append(" pid ").append(e.getPid()).append(' ').append(e.getProcessName())
                  .append(" 原因 ").append(e.getReason()).append(" 状态 ").append(e.getStatus()).append(" 重要性 ").append(e.getImportance())
                  .append(" pss ").append(e.getPss() / 1024).append("MB rss ").append(e.getRss() / 1024).append("MB：").append(e.getDescription()).append('\n');
                if (traced < 1 && (e.getReason() == android.app.ApplicationExitInfo.REASON_CRASH_NATIVE || e.getReason() == android.app.ApplicationExitInfo.REASON_ANR)) {
                    try (InputStream in = e.getTraceInputStream()) {
                        if (in != null) {
                            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                            try (java.util.zip.GZIPOutputStream gz = new java.util.zip.GZIPOutputStream(bo)) { byte[] b = new byte[1 << 15]; int r, tot = 0; while ((r = in.read(b)) > 0 && tot < (2 << 20)) { gz.write(b, 0, r); tot += r; } }
                            sb.append("TRACE_B64 ").append(android.util.Base64.encodeToString(bo.toByteArray(), android.util.Base64.NO_WRAP)).append('\n');
                            traced++;
                        }
                    } catch (Throwable t) { sb.append("trace 读不到 ").append(t).append('\n'); }
                }
            }
            if (l.isEmpty()) sb.append("没有记录\n");
        } catch (Throwable t) { sb.append("读不到：").append(t).append('\n'); }
        return sb.toString();
    }


    /** v2.09：游戏自带的崩溃上报（Bugly 等）会吞掉系统的闪退记录，去游戏私有目录里找最近 3 小时的崩溃文件 */
    static String crashFiles() {
        StringBuilder sb = new StringBuilder("== 游戏私有目录里的崩溃文件 ==\n");
        try {
            File root = new File("/data/data/com.tencent.tmgp.pubgm");
            java.util.List<File> hit = new java.util.ArrayList<>();
            scan(root, 0, hit, System.currentTimeMillis() - 3 * 3600_000L);
            File ext = new File("/storage/emulated/0/Android/data/com.tencent.tmgp.pubgm/files");
            scan(ext, 0, hit, System.currentTimeMillis() - 3 * 3600_000L);
            int shown = 0;
            // v2.11：最新的在前；Bugly 的 tomb_*.txt（闪退现场）整份打出来，其他文件只列名字
            hit.sort((x, y) -> Long.compare(y.lastModified(), x.lastModified()));
            int tombs = 0;
            for (File f : hit) if (f.getName().startsWith("tomb") && tombs < 2) {
                tombs++;
                sb.append("--- 闪退现场 ").append(f.getName()).append(' ').append(new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date(f.lastModified()))).append(" ---\n");
                byte[] b = java.nio.file.Files.readAllBytes(f.toPath());
                StringBuilder cur = new StringBuilder();
                for (byte x : b) { if (x >= 32 && x < 127 || x == 10 || x == 9) cur.append((char) x); else cur.append(' '); }
                sb.append(cur.length() > 14000 ? cur.substring(0, 14000) : cur).append("\n");
            }
            shown = 4;
            for (File f : hit) {
                if (f.getPath().contains("/lib/")) continue;
                sb.append(new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date(f.lastModified()))).append(' ').append(f.getPath()).append(' ').append(f.length()).append("\n");
                if (shown < 4 && f.length() < 200_000) {
                    shown++;
                    byte[] b = java.nio.file.Files.readAllBytes(f.toPath());
                    StringBuilder t = new StringBuilder(); int run = 0; StringBuilder cur = new StringBuilder();
                    for (byte x : b) { if (x >= 32 && x < 127 || x == 10) { cur.append((char) x); } else { if (cur.length() >= 6) t.append(cur).append('\n'); cur.setLength(0); } if (t.length() > 6000) break; }
                    if (cur.length() >= 6) t.append(cur);
                    sb.append(t.length() > 6000 ? t.substring(0, 6000) : t).append("\n---\n");
                }
            }
            if (hit.isEmpty()) sb.append("没找到\n");
        } catch (Throwable e) { sb.append("读不到：").append(e).append('\n'); }
        return sb.toString();
    }
    static void scan(File d, int depth, java.util.List<File> out, long since) {
        File[] fs = d.listFiles(); if (fs == null || depth > 5 || out.size() > 30) return;
        for (File f : fs) {
            String n = f.getName().toLowerCase();
            if (f.isDirectory()) { if (!n.equals("paks") && !n.equals("cache") && !n.startsWith("obb")) scan(f, depth + 1, out, since); }
            else if (f.lastModified() > since && (n.contains("crash") || n.contains("tomb") || n.contains("native") || n.contains("eup") || n.contains("dump") || n.contains("dmp") || n.contains("bugly") || d.getName().toLowerCase().contains("bugly") || d.getName().toLowerCase().contains("crash")))
                out.add(f);
        }
    }


    static String peek(long b, long off, int n) {
        if (b == 0) return "?";
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
            byte[] x = new byte[n]; m.seek(b + off); m.readFully(x); return Patches.hexs(x);
        } catch (Throwable t) { return "读不到"; }
    }

    static String save(Activity a, String text, String prefix) throws Exception {
        String name = prefix + new java.text.SimpleDateFormat("MMdd-HHmmss", java.util.Locale.US).format(new java.util.Date()) + ".txt";
        // v2.61 用户：能自动上传就别存到手机 → 只上传；传不出去先放游戏私有目录，下次传日志时补传
        LogUp.dir = new File(a.getFilesDir(), "zf_logq");
        LogUp.send(name, text);
        return name + "（已自动上传）";
    }

    /** v2.43 资源清单：游戏日志里全部 pak 的挂载目录（看包里到底有没有沙漠、五军、战术大师道具等资源），加上游戏目录里的大文件。只读。 */
    static final String[] WANT = { "Desert", "Miramar", "Legion", "FiveArmy", "ThreeArmy", "Prop", "Mine", "Trap", "Doll", "Shield", "Lift", "Bush", "Tree", "Rock", "Jet", "Flare", "AirSupply", "AirBox", "Training", "Tactic", "Zombie", "Monster", "Maps", "Snow", "Sanhok", "Savage" };
    static String resources(Activity a) {
        StringBuilder sb = new StringBuilder("\n== 资源清单 ==\n（v4.07 不读游戏日志，pak 挂载目录不再列）\n");
        sb.append("-- 游戏目录大文件 --\n");
        try {
            String pkg = "com.tencent.tmgp.pubgm";
            java.util.List<File> big = new java.util.ArrayList<>();
            for (File r : new File[] { new File("/storage/emulated/0/Android/data/" + pkg + "/files"), new File("/storage/emulated/0/Android/obb/" + pkg), new File("/data/data/" + pkg + "/files") })
                listBig(r, 0, big);
            long tot = 0;
            for (File x : big) { tot += x.length(); if (big.indexOf(x) < 80) sb.append(x.getPath()).append(' ').append(x.length() >> 20).append("MB\n"); }
            sb.append("合计 ").append(big.size()).append(" 个，").append(tot >> 20).append("MB\n");
        } catch (Throwable t) { sb.append("读不到：").append(t).append('\n'); }
        try { sb.append("安装包 ").append(new File(a.getApplicationInfo().sourceDir).length() >> 20).append("MB\n"); } catch (Throwable ignored) {}
        return sb.toString();
    }
    static void listBig(File d, int depth, java.util.List<File> out) {
        File[] fs = d.listFiles(); if (fs == null || depth > 8 || out.size() > 400) return;
        for (File f : fs) {
            if (f.isDirectory()) listBig(f, depth + 1, out);
            else if (f.length() > (1 << 20)) out.add(f);
        }
    }
}
