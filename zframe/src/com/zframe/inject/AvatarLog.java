package com.zframe.inject;

import android.app.Activity;
import android.content.ContentValues;
import android.net.Uri;
import android.provider.MediaStore;
import java.io.*;

/** v1.56 灰色人物诊断：按一下，把游戏日志里和人物/衣服有关的行存到 下载/ZFrame/avatar-*.txt。平时什么都不做。 */
public class AvatarLog {
    static final String[] KEYS = { "Avatar", "Ava", "jayzhong", "cidefeng", "ClotheTry", "HallRole", "RoleID", "roleID",
            "LoadMap", "Assembler", "SkeletalMesh", "Skin", "KeyLobbyMsg", "SendRawBuffer", "Wardrobe", "Depot", "Bag", "Store" };

    static String export(Activity a) {
        try {
            File f = Con.findUeLog(a);
            if (f == null) return "找不到游戏日志";
            StringBuilder sb = new StringBuilder();
            sb.append("ZFrame v1.62 诊断\n").append(Mods.hallState).append('\n');
            long b = Mods.base();
            sb.append("libUE4=").append(Long.toHexString(b)).append('\n');
            sb.append("入口 1503230=").append(peek(b, 0x1503230L, 4)).append("  仓库 1877bfc=").append(peek(b, 0x1877bfcL, 4)).append(" 171e630=").append(peek(b, 0x171e630L, 12)).append("\n").append(Mods.storeInfo()).append("  cave 171e610=").append(peek(b, 0x171e610L, 32))
              .append("\n风衣 1503680=").append(peek(b, 0x1503680L, 4)).append("  171e600=").append(peek(b, 0x171e600L, 16)).append("\n").append(Server.report()).append("\n");
            long len = f.length(), from = Math.max(0, len - 6_000_000L);
            int n = 0;
            try (RandomAccessFile r = new RandomAccessFile(f, "r")) {
                r.seek(from);
                BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(r.getFD()), "UTF-8"));
                String l;
                while ((l = br.readLine()) != null) {
                    if (l.contains("LogTexture") || l.contains("LogNavigation")) continue;
                    for (String k : KEYS) if (l.contains(k)) { sb.append(l.length() > 400 ? l.substring(0, 400) : l).append('\n'); n++; break; }
                    if (sb.length() > 3_000_000) break;
                }
            }
            sb.append("\n共 ").append(n).append(" 行，日志 ").append(f.getName()).append(' ').append(len).append(" 字节\n");
            return "已存到 下载/ZFrame/" + save(a, sb.toString());
        } catch (Throwable t) { return "导出失败：" + t.getMessage(); }
    }

    static String peek(long b, long off, int n) {
        if (b == 0) return "?";
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
            byte[] x = new byte[n]; m.seek(b + off); m.readFully(x); return Patches.hexs(x);
        } catch (Throwable t) { return "读不到"; }
    }

    static String save(Activity a, String text) throws Exception {
        String name = "avatar-" + new java.text.SimpleDateFormat("MMdd-HHmmss", java.util.Locale.US).format(new java.util.Date()) + ".txt";
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
