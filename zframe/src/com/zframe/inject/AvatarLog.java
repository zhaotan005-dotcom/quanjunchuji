package com.zframe.inject;

import android.app.Activity;
import android.content.ContentValues;
import android.net.Uri;
import android.provider.MediaStore;
import java.io.*;

/** v1.56 灰色人物诊断：按一下，把游戏日志里和人物/衣服有关的行存到 下载/ZFrame/avatar-*.txt。平时什么都不做。 */
public class AvatarLog {
    static final String[] KEYS = { "Avatar", "Ava", "jayzhong", "cidefeng", "ClotheTry", "HallRole", "RoleID", "roleID",
            "LoadMap", "Assembler", "SkeletalMesh", "Skin", "KeyLobbyMsg", "SendRawBuffer", "Wardrobe", "Depot", "Bag", "Store", "Error", "LuaErr", "Mail", "VehicleInterface", "UseVehicle", "EnterVehicle", "Ammo", "Reload", "Discrepancy", "Correction" };

    static String export(Activity a) { return export(a, "avatar-"); }
    static String export(Activity a, String prefix) {
        try {
            File f = Con.findUeLog(a);
            if (f == null) return "找不到游戏日志";
            StringBuilder sb = new StringBuilder();
            sb.append("ZFrame v2.38 " + Ed.name() + " 诊断\n").append(Mods.hallState).append('\n').append(Mods.earlyState).append('\n');
            sb.append(Veh.state).append("\n  ").append(Veh.botState).append("\n  ").append(Veh.tableInfo).append("\n");
            sb.append("人机：").append(Bots.state).append("，难度 ").append(Veh.DIFF[Bots.diff]).append("，已刷 ").append(Bots.spawned).append("，阶段 ").append(Bots.phase)
              .append("\n  最后一个人机：").append(Veh.botInfo).append("\n  装备：").append(Bots.equipState).append("\n  导航网格：").append(Con.navInfo).append("\n  服务器关卡：").append(Con.layerStatus())
              .append("\n  人机日志：\n  ").append(Con.botLog()).append("\n  ").append(Flow.state).append("\n  ").append(Flow.air).append("\n  上一局：").append(Flow.last).append("\n  ").append(Bots.countText()).append("\n  ").append(Bots.outfitState).append("\n  航线/毒圈日志：\n  ").append(Con.flowLog()).append("\n  画质：").append(Gfx.state).append("\n  联机：").append(Lan.roleText()).append("，").append(Lan.pre).append("，").append(Lan.state).append("，本机 ").append(Lan.ip()).append("\n  地图物资/门：").append(Loot.text()).append("\n  ").append(Alive.state).append("\n  ").append(Crew.state).append("\n  ").append(Crew.pullState).append("\n  ").append(Crew.ammoState).append("\n  跟服务端：").append(Mods.syncState).append("\n  联机日志：\n  ").append(Lan.text()).append("\n");
            long b = Mods.base();
            sb.append("libUE4=").append(Long.toHexString(b)).append('\n');
            sb.append("入口 1503230=").append(peek(b, 0x1503230L, 4)).append("  仓库 1877bfc=").append(peek(b, 0x1877bfcL, 4)).append(" 171e630=").append(peek(b, 0x171e630L, 12)).append("\n").append(Mods.storeInfo()).append("\n").append(Mods.wearState).append("\n").append("  cave 171e610=").append(peek(b, 0x171e610L, 32))
              .append("\n风衣 1503680=").append(peek(b, 0x1503680L, 4)).append("  171e600=").append(peek(b, 0x171e600L, 16)).append("\n").append(Server.report()).append("\n");
            sb.append(crashInfo(f)).append('\n');
            long len = f.length();
            int n = 0;
            {
                String l;
                BufferedReader br = new BufferedReader(new StringReader(readText(f, 6_000_000)));
                while ((l = br.readLine()) != null) {
                    if (l.contains("LogTexture") || l.contains("LogNavigation")) continue;
                    for (String k : KEYS) if (l.contains(k)) { sb.append(l.length() > 400 ? l.substring(0, 400) : l).append('\n'); n++; break; }
                    if (sb.length() > 3_000_000) break;
                }
            }
            sb.append("\n共 ").append(n).append(" 行，日志 ").append(f.getName()).append(' ').append(len).append(" 字节\n");
            return "已存到 下载/ZFrame/" + save(a, sb.toString(), prefix);
        } catch (Throwable t) { return "导出失败：" + t.getMessage(); }
    }

    /** v1.68：上次闪退的原因。系统日志里本应用自己的 Fatal signal / backtrace 行，加上上一次的游戏日志最后几十行。 */
    static String crashInfo(File cur) {
        StringBuilder sb = new StringBuilder("== 系统日志（闪退）==\n");
        int n = 0;
        try {
            Process p = Runtime.getRuntime().exec(new String[] { "logcat", "-d", "-v", "time", "-b", "crash", "-b", "main", "-b", "system", "-t", "60000" });
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), "UTF-8"));
            java.util.ArrayDeque<String> keep = new java.util.ArrayDeque<>();
            String l;
            while ((l = br.readLine()) != null) {
                if (l.contains("Fatal signal") || l.contains("SIGSEGV") || l.contains("SIGABRT") || l.contains("backtrace") || l.contains(" #")
                        || l.contains("Abort message") || l.contains("fault addr") || l.contains("libUE4") || l.contains("Cmdline")
                        || l.contains("KeyLobbyMsg") || l.contains("FATAL") || l.contains("Assertion")
                        || l.contains("CrashReport") || l.contains("Bugly") || l.contains("eup") || l.contains("signal") || l.contains("pubgm") && (l.contains("died") || l.contains("kill") || l.contains("exit"))) {
                    keep.add(l.length() > 300 ? l.substring(0, 300) : l);
                    if (keep.size() > 150) keep.removeFirst();
                }
            }
            p.destroy();
            for (String k : keep) { sb.append(k).append('\n'); n++; }
        } catch (Throwable t) { sb.append("读不到：").append(t.getMessage()).append('\n'); }
        sb.append("（").append(n).append(" 行）\n");
        sb.append(crashFiles());
        sb.append("== 上一次游戏日志最后 80 行 ==\n");
        try {
            File prev = null;
            File[] fs = cur.getParentFile().listFiles();
            if (fs != null) for (File x : fs)
                if (!x.equals(cur) && x.getName().startsWith("PUBGM") && x.getName().endsWith(".log") && (prev == null || x.lastModified() > prev.lastModified())) prev = x;
            if (prev == null) sb.append("没有\n");
            else {
                sb.append(prev.getName()).append(' ').append(prev.length()).append(" 字节\n");
                java.util.ArrayDeque<String> tail = new java.util.ArrayDeque<>();
                for (String l : readText(prev, 200_000).split("\n")) { tail.add(l.length() > 300 ? l.substring(0, 300) : l); if (tail.size() > 80) tail.removeFirst(); }
                for (String t : tail) sb.append(t).append('\n');
            }
        } catch (Throwable t) { sb.append("读不到：").append(t.getMessage()).append('\n'); }
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

    /** 读文件最后 max 字节；游戏日志有时是异或 0x73 存的（开头没有 "Log" 就解码），和 Con 里的判断一样 */
    static String readText(File f, int max) throws IOException {
        try (RandomAccessFile r = new RandomAccessFile(f, "r")) {
            byte[] head = new byte[(int) Math.min(4000, r.length())]; r.readFully(head);
            boolean xor = !new String(head, "ISO-8859-1").contains("Log");
            long from = Math.max(0, r.length() - max);
            byte[] b = new byte[(int) (r.length() - from)]; r.seek(from); r.readFully(b);
            if (xor) for (int i = 0; i < b.length; i++) b[i] ^= 0x73;
            return new String(b, "UTF-8");
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
        ContentValues v = new ContentValues();
        v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        v.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
        v.put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/ZFrame");
        Uri u = a.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
        if (u == null) throw new Exception("无法在 下载/ZFrame 建文件");
        try (OutputStream o = a.getContentResolver().openOutputStream(u, "wt")) { o.write(text.getBytes("UTF-8")); }
        return name;
    }
}
