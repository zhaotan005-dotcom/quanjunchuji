package com.zframe.inject;

import android.app.Activity;
import android.content.ContentValues;
import android.net.Uri;
import android.provider.MediaStore;
import java.io.*;

/** v1.56 灰色人物诊断：按一下，把游戏日志里和人物/衣服有关的行存到 下载/ZFrame/avatar-*.txt。平时什么都不做。 */
public class AvatarLog {
    static final String[] KEYS = { "Avatar", "Ava", "jayzhong", "cidefeng", "ClotheTry", "HallRole", "RoleID", "roleID",
            "LoadMap", "Assembler", "SkeletalMesh", "Skin", "KeyLobbyMsg", "SendRawBuffer", "Wardrobe", "Depot", "Bag", "Store", "Error", "LuaErr", "Mail" };

    static String export(Activity a) { return export(a, "avatar-"); }
    static String export(Activity a, String prefix) {
        try {
            File f = Con.findUeLog(a);
            if (f == null) return "找不到游戏日志";
            StringBuilder sb = new StringBuilder();
            sb.append("ZFrame v2.00 诊断\n").append(Mods.hallState).append('\n').append(Mods.earlyState).append('\n');
            sb.append(Veh.state).append("\n  ").append(Veh.botState).append("\n  ").append(Veh.tableInfo).append("\n");
            sb.append("人机：").append(Bots.state).append("，难度 ").append(Veh.DIFF[Bots.diff]).append("，已刷 ").append(Bots.spawned).append("，阶段 ").append(Bots.phase)
              .append("\n  最后一个人机：").append(Veh.botInfo).append("\n  装备：").append(Bots.equipState).append("\n  导航网格：").append(Con.navInfo).append("\n  服务器关卡：").append(Con.layerStatus())
              .append("\n  人机日志：\n  ").append(Con.botLog()).append("\n  ").append(Flow.state).append("\n  ").append(Flow.air).append("\n  上一局：").append(Flow.last).append("\n  ").append(Bots.countText()).append("\n  ").append(Bots.outfitState).append("\n  航线/毒圈日志：\n  ").append(Con.flowLog()).append("\n");
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
            Process p = Runtime.getRuntime().exec(new String[] { "logcat", "-d", "-v", "time", "-b", "crash", "-b", "main", "-t", "6000" });
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), "UTF-8"));
            java.util.ArrayDeque<String> keep = new java.util.ArrayDeque<>();
            String l;
            while ((l = br.readLine()) != null) {
                if (l.contains("Fatal signal") || l.contains("SIGSEGV") || l.contains("SIGABRT") || l.contains("backtrace") || l.contains(" #")
                        || l.contains("Abort message") || l.contains("fault addr") || l.contains("libUE4") || l.contains("Cmdline")
                        || l.contains("KeyLobbyMsg") || l.contains("FATAL") || l.contains("Assertion")) {
                    keep.add(l.length() > 300 ? l.substring(0, 300) : l);
                    if (keep.size() > 150) keep.removeFirst();
                }
            }
            p.destroy();
            for (String k : keep) { sb.append(k).append('\n'); n++; }
        } catch (Throwable t) { sb.append("读不到：").append(t.getMessage()).append('\n'); }
        sb.append("（").append(n).append(" 行）\n== 上一次游戏日志最后 80 行 ==\n");
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
