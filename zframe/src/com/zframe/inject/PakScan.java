package com.zframe.inject;

import android.content.Context;

import java.io.*;
import java.util.*;
import java.util.regex.*;

/**
 * v1.30：只读游戏包（obb / pak）的文件名目录，不解密、不改任何文件。
 * 用途：
 *  1. 找载具蓝图（场上一辆车都没生成，getall 永远找不到车型 → 以前一直提示“过两秒再点”）。
 *  2. 找头盔/护甲/风衣等外观编号（Avatar/.../<11位编号>/<文件名>，文件名里带 Helmet03 这类等级信息）。
 * 目录是加密的 pak 直接跳过（只计数）。
 */
public class PakScan {
    public static volatile int state = 0; // 0 没扫 1 扫描中 2 完成
    public static volatile String summary = "";
    public static final List<String> cars = Collections.synchronizedList(new ArrayList<String>());   // 完整类路径 /Game/...X.X_C
    public static final Map<String, TreeMap<String, String>> avatar = new TreeMap<>();            // 分类 -> 编号 -> 示例文件名
    public static volatile String helmet3, armor3, coat;
    public static final List<String> coatList = new ArrayList<>();
    static int paks, encPaks, files, badPaks, zipPaks, zipDeflated;

    static final int MAGIC = 0x5A6F12E1;

    public static synchronized void startAsync(final Context ctx, final Runnable done) {
        if (state == 2) { if (done != null) done.run(); return; }
        if (state == 1) { pending = done; return; }
        state = 1; pending = done;
        new Thread(() -> {
            try { run(ctx); } catch (Throwable e) { LogCap.note("包扫描出错 " + e); }
            state = 2;
            Runnable r = pending; pending = null;
            if (r != null) r.run();
        }, "zframe-pakscan").start();
    }
    static volatile Runnable pending;

    static void run(Context ctx) {
        long t0 = System.currentTimeMillis();
        List<File> found = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        String pkg = ctx.getPackageName();
        File[] roots = {
                ctx.getObbDir(), new File("/storage/emulated/0/Android/obb/" + pkg),
                ctx.getExternalFilesDir(null), new File("/storage/emulated/0/Android/data/" + pkg + "/files"),
                new File("/storage/emulated/0/UE4Game"), ctx.getFilesDir()};
        for (File r : roots) collect(r, found, seen, 0);
        // v1.31：数据包其实在游戏安装包 base.apk 里（assets/…/Paks），上一版没扫这里所以是 0
        android.content.pm.ApplicationInfo ai = ctx.getApplicationInfo();
        if (ai.sourceDir != null) found.add(0, new File(ai.sourceDir));
        if (ai.splitSourceDirs != null) for (String sd : ai.splitSourceDirs) found.add(new File(sd));
        LogCap.note("包扫描: 找到 " + found.size() + " 个包文件");
        for (File f : found) {
            try {
                LogCap.note("包扫描: " + f.getPath() + " " + (f.length() >> 20) + "MB");
                if (f.getName().endsWith(".pak")) scanPak(f, 0, f.length());
                else scanZip(f);
            } catch (Throwable e) { LogCap.note("包扫描 " + f.getName() + " 出错 " + e); }
        }
        pick();
        summary = "安装包里 pak " + zipPaks + "（压缩的 " + zipDeflated + "），读了 " + paks + " 个（目录加密 " + encPaks + "，读不了 " + badPaks + "），文件 " + files + "，车型 " + cars.size();
        LogCap.note("包扫描完成 " + (System.currentTimeMillis() - t0) + "ms: " + summary);
        synchronized (cars) { for (String c : cars) LogCap.note("车型候选: " + c); }
        synchronized (avatar) {
            for (Map.Entry<String, TreeMap<String, String>> e : avatar.entrySet())
                for (Map.Entry<String, String> x : e.getValue().entrySet())
                    LogCap.note("外观 " + e.getKey() + " " + x.getKey() + " " + x.getValue());
        }
        LogCap.note("3级头=" + helmet3 + " 3级甲=" + armor3 + " 风衣=" + coat + " 外套候选=" + coatList);
    }

    static void collect(File d, List<File> out, Set<String> seen, int depth) {
        if (d == null || depth > 7) return;
        File[] ls = d.listFiles(); if (ls == null) return;
        for (File f : ls) {
            if (f.isDirectory()) collect(f, out, seen, depth + 1);
            else {
                String n = f.getName().toLowerCase();
                if ((n.endsWith(".pak") || n.endsWith(".obb")) && f.length() > 1024) {
                    try { if (seen.add(f.getCanonicalPath())) out.add(f); } catch (IOException e) { out.add(f); }
                }
            }
        }
    }

    // ===== obb 是 zip（pak 以“存储”方式放在里面）=====
    static void scanZip(File f) throws IOException {
        try (RandomAccessFile r = new RandomAccessFile(f, "r")) {
            long len = r.length();
            int tail = (int) Math.min(len, 70000);
            byte[] t = new byte[tail]; r.seek(len - tail); r.readFully(t);
            int eocd = -1;
            for (int i = tail - 22; i >= 0; i--) if (le32(t, i) == 0x06054b50) { eocd = i; break; }
            if (eocd < 0) return;
            long cdOff = le32(t, eocd + 16) & 0xffffffffL, cdSize = le32(t, eocd + 12) & 0xffffffffL;
            long entries = le16(t, eocd + 10);
            // zip64
            int loc = eocd - 20;
            if (loc >= 0 && le32(t, loc) == 0x07064b50) {
                long z64 = le64(t, loc + 8);
                byte[] z = new byte[56]; r.seek(z64); r.readFully(z);
                if (le32(z, 0) == 0x06064b50) { entries = le64(z, 32); cdSize = le64(z, 40); cdOff = le64(z, 48); }
            }
            if (cdSize > 64 << 20) return;
            byte[] cd = new byte[(int) cdSize]; r.seek(cdOff); r.readFully(cd);
            int p = 0;
            for (long k = 0; k < entries && p + 46 <= cd.length; k++) {
                if (le32(cd, p) != 0x02014b50) break;
                int method = le16(cd, p + 10);
                long csize = le32(cd, p + 20) & 0xffffffffL, usize = le32(cd, p + 24) & 0xffffffffL;
                int nl = le16(cd, p + 28), el = le16(cd, p + 30), cl = le16(cd, p + 32);
                long lho = le32(cd, p + 42) & 0xffffffffL;
                String name = new String(cd, p + 46, nl, "UTF-8");
                // zip64 extra
                int e = p + 46 + nl, ee = e + el;
                while (e + 4 <= ee) {
                    int id = le16(cd, e), sz = le16(cd, e + 2), q = e + 4;
                    if (id == 1) {
                        if (usize == 0xffffffffL) { usize = le64(cd, q); q += 8; }
                        if (csize == 0xffffffffL) { csize = le64(cd, q); q += 8; }
                        if (lho == 0xffffffffL) { lho = le64(cd, q); }
                    }
                    e += 4 + sz;
                }
                p += 46 + nl + el + cl;
                if (!name.toLowerCase().endsWith(".pak")) continue;
                zipPaks++;
                if (method != 0) { zipDeflated++; continue; }
                byte[] lh = new byte[30]; r.seek(lho); r.readFully(lh);
                if (le32(lh, 0) != 0x04034b50) continue;
                long data = lho + 30 + le16(lh, 26) + le16(lh, 28);
                try { scanPak(r, data, csize, name); } catch (Throwable ex) { badPaks++; }
            }
        }
    }

    static void scanPak(File f, long base, long size) throws IOException {
        try (RandomAccessFile r = new RandomAccessFile(f, "r")) { scanPak(r, base, size, f.getName()); }
        catch (Throwable e) { badPaks++; }
    }

    static void scanPak(RandomAccessFile r, long base, long size, String name) throws IOException {
        paks++;
        int tl = (int) Math.min(size, 512);
        byte[] t = new byte[tl]; r.seek(base + size - tl); r.readFully(t);
        int mp = -1, ver = 0; long io = 0, is = 0;
        for (int i = tl - 44; i >= 0; i--) {
            if (le32(t, i) != MAGIC) continue;
            int v = le32(t, i + 4); long o = le64(t, i + 8), s = le64(t, i + 16);
            if (v < 1 || v > 9 || o < 0 || s <= 0 || o + s > size) continue;
            mp = i; ver = v; io = o; is = s; break;
        }
        if (mp < 0) { badPaks++; return; }
        boolean enc = false;
        if (ver >= 7 && mp >= 17) enc = t[mp - 17] != 0;
        else if (ver >= 4 && mp >= 1) enc = t[mp - 1] != 0;
        if (enc || is > 96 << 20) { encPaks++; return; }
        byte[] idx = new byte[(int) is]; r.seek(base + io); r.readFully(idx);
        int[] pos = {0};
        String mount = fstr(idx, pos);
        if (mount == null) { encPaks++; return; } // 读出来是乱码 = 加密
        int n = le32(idx, pos[0]); pos[0] += 4;
        if (n < 0 || n > 2000000) { encPaks++; return; }
        for (int k = 0; k < n; k++) {
            String fn = fstr(idx, pos);
            if (fn == null) return;
            pos[0] += 24; // offset size usize
            int method = le32(idx, pos[0]); pos[0] += 4;
            if (ver <= 1) pos[0] += 8;
            pos[0] += 20;
            if (ver >= 3) {
                if (method != 0) { int nb = le32(idx, pos[0]); pos[0] += 4 + 16 * Math.max(0, nb); }
                pos[0] += 1 + 4;
            }
            files++;
            add(norm(mount + fn));
        }
    }

    static String norm(String p) {
        p = p.replace('\\', '/');
        int i = p.indexOf("/Content/");
        if (i >= 0) p = "/Game/" + p.substring(i + 9);
        return p;
    }

    static final Pattern AVA = Pattern.compile("/Game/Avatar/(\\w+)/(\\w+)/(\\d{11})/(?:.*/)?([^/]+)\\.uasset$");
    static final Pattern BAD = Pattern.compile("(?i)(anim|abp|wheel|tire|tyre|^m_|^mi_|^mat|^t_|^sm_|^sk_|phys|^pa_|fx|sound|^ak|widget|^ui|^dt_|curve|camera|damage|effect|seat|c130|plane|parachute|airdrop|carepackage|skeleton|mesh|texture|light|decal|broken|rusty|props)");

    static void add(String p) {
        if (!p.endsWith(".uasset")) return;
        Matcher m = AVA.matcher(p);
        if (m.find()) {
            String cat = m.group(1) + "/" + m.group(2);
            synchronized (avatar) {
                TreeMap<String, String> mm = avatar.get(cat);
                if (mm == null) avatar.put(cat, mm = new TreeMap<>());
                String old = mm.get(m.group(3));
                if (old == null) mm.put(m.group(3), m.group(4));
                else if (old.length() < 60 && !old.contains(m.group(4))) mm.put(m.group(3), old + "," + m.group(4));
            }
            return;
        }
        String low = p.toLowerCase();
        if (!(low.contains("/vehicles/") || low.contains("/vehicle/"))) return;
        if (low.contains("/environment/")) return;
        String file = p.substring(p.lastIndexOf('/') + 1, p.length() - 7);
        String fl = file.toLowerCase();
        if (BAD.matcher(file).find()) return;
        String dir = p.substring(0, p.length() - 7 - file.length()).toLowerCase();
        if (dir.contains("/anim") || dir.contains("/mesh") || dir.contains("/texture") || dir.contains("/material") || dir.contains("/fx") || dir.contains("/sound")) return;
        if (!(fl.startsWith("bp_") || fl.startsWith("vh_") || fl.contains("vehicle"))) return;
        String cls = p.substring(0, p.length() - 7) + "." + file + "_C";
        if (!cars.contains(cls)) cars.add(cls);
    }

    static void pick() {
        // 车：带 VH / Vehicle 的优先
        synchronized (cars) {
            Collections.sort(cars, (x, y) -> score(y) - score(x));
        }
        synchronized (avatar) {
            for (Map.Entry<String, TreeMap<String, String>> e : avatar.entrySet()) {
                String cat = e.getKey().toLowerCase();
                for (Map.Entry<String, String> x : e.getValue().entrySet()) {
                    String id = x.getKey(), fn = x.getValue().toLowerCase();
                    if (helmet3 == null && (cat.contains("helmet") || id.startsWith("30304")) && Pattern.compile("helmet_?0?3(?!\\d)|lv_?3|level_?3").matcher(fn).find()) helmet3 = id;
                    if (armor3 == null && (cat.contains("armor") || cat.contains("vest") || id.startsWith("30305")) && Pattern.compile("(armor|vest)_?0?3(?!\\d)|lv_?3|level_?3").matcher(fn).find()) armor3 = id;
                    if (coat == null && Pattern.compile("trench|windcoat|wind_coat|windbreaker|fengyi|dustcoat|duster|longcoat|long_coat|overcoat").matcher(fn).find()) coat = id;
                    if (cat.contains("coat") || cat.contains("longshirt") || id.startsWith("30315")) coatList.add(id);
                }
            }
        }
        if (coat != null) { coatList.remove(coat); coatList.add(0, coat); }
    }

    static int score(String c) {
        String l = c.toLowerCase(); int s = 0;
        if (l.contains("bp_vh") || l.contains("bp_vehicle")) s += 10;
        if (l.contains("/wheeled/")) s += 5;
        if (l.contains("uaz") || l.contains("buggy") || l.contains("dacia") || l.contains("motor")) s += 3;
        if (l.contains("base") || l.contains("parent") || l.contains("template")) s -= 8;
        return s;
    }

    static String fstr(byte[] b, int[] pos) {
        int p = pos[0];
        if (p + 4 > b.length) return null;
        int n = le32(b, p); p += 4;
        try {
            String s;
            if (n == 0) s = "";
            else if (n > 0) {
                if (n > 1024 || p + n > b.length) return null;
                s = new String(b, p, n - 1, "ISO-8859-1"); p += n;
            } else {
                n = -n; if (n > 1024 || p + 2 * n > b.length) return null;
                s = new String(b, p, 2 * (n - 1), "UTF-16LE"); p += 2 * n;
            }
            for (int i = 0; i < s.length(); i++) if (s.charAt(i) < 32) return null;
            pos[0] = p; return s;
        } catch (UnsupportedEncodingException e) { return null; }
    }

    static int le16(byte[] b, int i) { return (b[i] & 0xff) | (b[i + 1] & 0xff) << 8; }
    static int le32(byte[] b, int i) { return (b[i] & 0xff) | (b[i + 1] & 0xff) << 8 | (b[i + 2] & 0xff) << 16 | (b[i + 3] & 0xff) << 24; }
    static long le64(byte[] b, int i) { return (le32(b, i) & 0xffffffffL) | ((long) le32(b, i + 4) << 32); }
}
