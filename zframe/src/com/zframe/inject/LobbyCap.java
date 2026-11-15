package com.zframe.inject;

import android.app.Activity;
import android.content.ContentValues;
import android.net.Uri;
import android.provider.MediaStore;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

/**
 * v1.53 服务端调试：记下大厅发给服务器的请求，用来做本地服务端。默认关，只在大厅里手动打开。
 *  - 钩 1：KeyLobby SendMsg 0xf94a00(this, 消息号 r1, TArray* 内容 r2)
 *  - 钩 2：ConnectorService::SendRawBuffer 0xf950e0(this, buf r1, len r2)，额外记调用位置
 * 两处开头改成跳到 cave(0x171e600，game/re/cap_cave.py 生成，emu_cap_cave.py 模拟验证)，
 * cave 把内容复制进一块 mmap 的文件（缓存目录 zcap.bin），满了就不再记，游戏照常往下走。
 * 关掉时先还原两处钩子，再把记录转成文字存到 下载/ZFrame。
 */
public class LobbyCap {
    static final long HOOK_A = 0xf94a00L, HOOK_B = 0xf950e0L, CAVE = 0x171e600L;
    static final String A_O = "f04f2de9", A_N = "fe261eea", B_O = "f0482de9", B_N = "4f251eea";
    static final String CAVE_O = "0700a0e10810a0e1391d37eb2c001be5000050e30000000af0ad09eb040095e5000050e37400000a005095e50011a0e10020a0e30060a0e30530a0e10270b3e75f0057e30300000a042082e2020051e1f8ffff1a680000ea051043e00060a0e30020e0e3410152e16300000a016040e24121a0e1020056e10030a0e30260a0c1000051e3006000b3018086e2000056e30670a0e10870a01138300be530300be5010057e334700be51b0000ba38004be20010a0e3";
    static final String CAVE_T = "ff402de90160a0e1001092e5042092e50100a0e3090000ebff40bde8f04f2de9f7d8e1eaff402de90e60a0e10200a0e3020000ebff40bde8f0482de9a8dae1ea004000e3004040e3005094e5047094e5000051e30ef0a001070052e10ef0a081023085e00c3083e2070053e10ef0a081053084e0000083e5046083e5082083e50c3083e20000a0e3020050e10300002a0070d1e70070c3e7010080e2f9ffffea025085e00f5085e20350c5e3005084e50ef0a0e1";
    static final int MOVW_AT = 16 * 4, SIZE = 1 << 20;

    public static volatile boolean on;
    static MappedByteBuffer buf;   // 一直留着，不解除映射（钩子还原前 cave 可能还在写）
    static long bufAddr;
    static String caveHex;          // 本次进程里 cave 的内容（地址固定后不再变）

    /** 缓冲区地址填进 movw/movt r4 */
    static String caveFor(long addr) {
        byte[] c = Patches.hex(CAVE_T);
        int lo = (int) (addr & 0xffff), hi = (int) ((addr >>> 16) & 0xffff);
        putLE(c, MOVW_AT, 0xe3004000 | ((lo >> 12) << 16) | (lo & 0xfff));
        putLE(c, MOVW_AT + 4, 0xe3404000 | ((hi >> 12) << 16) | (hi & 0xfff));
        return Patches.hexs(c);
    }
    static void putLE(byte[] b, int i, int v) { b[i] = (byte) v; b[i + 1] = (byte) (v >> 8); b[i + 2] = (byte) (v >> 16); b[i + 3] = (byte) (v >>> 24); }

    static synchronized String start(Activity a) {
        if (on) return "ok";
        if (Con.inMatch) return "请在大厅里打开";
        try {
            if (buf == null) {
                File f = new File(a.getCacheDir(), "zcap.bin");
                try (RandomAccessFile r = new RandomAccessFile(f, "rw")) {
                    r.setLength(SIZE);
                    buf = r.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, SIZE);
                }
                buf.order(ByteOrder.LITTLE_ENDIAN);
                bufAddr = mapAddr(f.getCanonicalPath());
                if (bufAddr == 0 || bufAddr > 0xffffffffL) { buf = null; return "找不到记录区地址"; }
                caveHex = caveFor(bufAddr);
            }
            buf.putInt(0, 16); buf.putInt(4, SIZE); buf.force();
            String r = Mods.write(CAVE, CAVE_O, caveHex, true);
            if ("ok".equals(r)) r = Mods.write(HOOK_A, A_O, A_N, true);
            if ("ok".equals(r)) r = Mods.write(HOOK_B, B_O, B_N, true);
            if (!"ok".equals(r)) { Mods.write(HOOK_A, A_O, A_N, false); return r; }
            on = true;
            return "ok";
        } catch (Throwable t) { return "出错 " + t.getMessage(); }
    }

    /** 还原钩子并保存，返回提示文字 */
    static synchronized String stop(Activity a) {
        if (!on) return "没在记录";
        String r1 = Mods.write(HOOK_A, A_O, A_N, false), r2 = Mods.write(HOOK_B, B_O, B_N, false);
        on = false;
        if (!"ok".equals(r1) || !"ok".equals(r2)) return "还原失败：" + r1 + " / " + r2;
        try { Thread.sleep(50); } catch (InterruptedException ignored) { }
        try {
            String name = save(a, dump());
            return "已保存：下载/ZFrame/" + name;
        } catch (Throwable t) { return "保存失败 " + t.getMessage(); }
    }

    static int count() {
        if (buf == null) return 0;
        int used = buf.getInt(0), n = 0;
        for (int p = 16; p + 12 <= used && p + 12 <= SIZE; n++) p += (12 + buf.getInt(p + 8) + 3) & ~3;
        return n;
    }

    static String dump() {
        long b = Mods.base();
        int used = Math.min(buf.getInt(0), SIZE);
        StringBuilder sb = new StringBuilder("ZFrame 大厅请求记录 v1.53  libUE4=0x" + Long.toHexString(b) + "  记录区=0x" + Long.toHexString(bufAddr) + "\n");
        int p = 16, i = 0;
        while (p + 12 <= used) {
            int tag = buf.getInt(p), extra = buf.getInt(p + 4), len = buf.getInt(p + 8);
            if (len < 0 || p + 12 + len > used) break;
            if (tag == 1) sb.append("\n#").append(i).append(" 消息 编号=").append(extra & 0xffffffffL);
            else sb.append("\n#").append(i).append(" 原始发送 调用处=0x").append(Long.toHexString((extra & 0xffffffffL) - b));
            sb.append(" 长度=").append(len).append('\n');
            StringBuilder txt = new StringBuilder();
            for (int k = 0; k < len; k++) {
                int v = buf.get(p + 12 + k) & 0xff;
                sb.append(String.format("%02x", v));
                txt.append(v >= 0x20 && v < 0x7f ? (char) v : '.');
                if (k % 32 == 31 || k == len - 1) { sb.append("  ").append(txt).append('\n'); txt.setLength(0); }
                else if (k % 4 == 3) sb.append(' ');
            }
            p += (12 + len + 3) & ~3; i++;
        }
        sb.append("\n共 ").append(i).append(" 条\n");
        return sb.toString();
    }

    static String save(Activity a, String text) throws Exception {
        String name = "lobby-" + new java.text.SimpleDateFormat("MMdd-HHmmss", java.util.Locale.US).format(new java.util.Date()) + ".txt";
        ContentValues v = new ContentValues();
        v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        v.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
        v.put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/ZFrame");
        Uri u = a.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
        if (u == null) throw new Exception("无法在 下载/ZFrame 建文件");
        try (OutputStream o = a.getContentResolver().openOutputStream(u, "wt")) { o.write(text.getBytes("UTF-8")); }
        return name;
    }

    /** /proc/self/maps 里这个文件被映射的起始地址 */
    static long mapAddr(String path) {
        try (BufferedReader r = new BufferedReader(new FileReader("/proc/self/maps"))) {
            String l;
            while ((l = r.readLine()) != null) {
                if (!l.endsWith(path) || !l.contains(" rw")) continue;
                String[] f = l.split("\\s+");
                if (Long.parseLong(f[2], 16) != 0) continue;
                long s = Long.parseLong(f[0].split("-")[0], 16), e = Long.parseLong(f[0].split("-")[1], 16);
                if (e - s >= SIZE) return s;
            }
        } catch (Throwable ignored) { }
        return 0;
    }
}
