package com.zframe;

import java.io.*;
import java.util.*;

/**
 * 修改器内核（GG 风格）。两种运行方式：
 *  - root：su + app_process 运行 main()，stdin/stdout 通信，读写 /proc/<pid>/mem
 *  - 免 root：注入游戏进程后直接调用 handle()，读写 /proc/self/mem
 *
 * 数值语法：
 *   100            精确
 *   100~200        范围
 *   100;200;300    联合搜索（默认 512 字节内），可写成 100;200;300::256
 *   ?              未知值（模糊搜索，先拍快照，再用 变大/变小/没变/变了 筛选）
 *
 * 命令：
 *   attach <包名|pid>
 *   regions <0常用|1常用+Java堆|2全部>
 *   search <type> <value>         type: dword float qword double byte
 *   refine <value|changed|unchanged|increased|decreased>
 *   list <n>                      OK 总数 / 地址 当前值 [F]
 *   write <hexaddr> <value> [type]
 *   writeall <value>  freezeall <value>
 *   freeze <hexaddr> <value> [type] / unfreeze <hexaddr> / unfreezeall
 *   view <hexaddr> <n> [type]     从地址开始连续 n 个值
 *   save <hexaddr> [type] [备注]   saved   unsave <hexaddr>
 *   reset
 */
public class MemServer {
    static int pid = -1;
    static RandomAccessFile mem;
    static int type = 0; // 0 dword,1 float,2 qword,3 double,4 byte
    static int regionMode = 0;
    static long[] addrs = new long[0];
    static long[] lastVals = new long[0];
    static int count = 0;
    static boolean fuzzy = false;           // 有快照、尚未筛选
    static List<long[]> snapRegions = new ArrayList<>();
    static File snapFile;
    static final int MAX_RESULTS = 3_000_000;
    static final Map<Long, byte[]> frozen = Collections.synchronizedMap(new LinkedHashMap<Long, byte[]>());
    static final Map<Long, String[]> saved = new LinkedHashMap<>(); // addr -> {type, 备注}
    static final String[] TYPES = {"dword", "float", "qword", "double", "byte"};

    public static void main(String[] args) throws Exception {
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        PrintStream out = new PrintStream(new FileOutputStream(FileDescriptor.out), false, "UTF-8");
        initInProcess();
        out.println("READY uid=" + android.os.Process.myUid());
        out.println("<<END>>");
        out.flush();
        String line;
        while ((line = in.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) continue;
            if (line.equals("quit")) break;
            try {
                out.print(handle(line));
            } catch (Throwable t) {
                out.println("ERR " + t);
            }
            out.println("<<END>>");
            out.flush();
        }
        System.exit(0);
    }

    private static boolean freezeStarted;

    /** 游戏进程内（免 root）直接调用 */
    public static synchronized void initInProcess() {
        if (freezeStarted) return;
        freezeStarted = true;
        Thread fz = new Thread(MemServer::freezeLoop, "zf-freeze");
        fz.setDaemon(true);
        fz.start();
    }

    public static synchronized String handle(String line) throws Exception {
        String[] a = line.split("\\s+", 4);
        switch (a[0]) {
            case "attach": return attach(a[1]);
            case "regions": regionMode = Integer.parseInt(a[1]); return "OK\n";
            case "search": return search(a[1], a[2]);
            case "refine": return refine(a[1]);
            case "list": return list(a.length > 1 ? Integer.parseInt(a[1]) : 100);
            case "write": write(hex(a[1]), a[2], tp(a, 3)); return "OK\n";
            case "writeall": {
                int n = Math.min(count, 5000);
                for (int i = 0; i < n; i++) try { write(addrs[i], a[1], type); } catch (IOException ignored) {}
                return "OK 已修改 " + n + "\n";
            }
            case "freezeall": {
                int n = Math.min(count, 500);
                for (int i = 0; i < n; i++) frozen.put(addrs[i], encode(a[1], type));
                return "OK 已冻结 " + n + "\n";
            }
            case "freeze": {
                long ad = hex(a[1]);
                int t = tp(a, 3);
                frozen.put(ad, encode(a[2], t));
                write(ad, a[2], t);
                return "OK\n";
            }
            case "unfreeze": frozen.remove(hex(a[1])); return "OK\n";
            case "unfreezeall": frozen.clear(); return "OK\n";
            case "view": return view(hex(a[1]), Integer.parseInt(a[2]), tp(a, 3));
            case "save": {
                String[] p = line.split("\\s+", 4);
                int t = p.length > 2 ? parseType(p[2]) : type;
                saved.put(hex(p[1]), new String[]{TYPES[t], p.length > 3 ? p[3] : ""});
                return "OK\n";
            }
            case "unsave": saved.remove(hex(a[1])); return "OK\n";
            case "saved": return savedList();
            case "reset": clearResults(); return "OK\n";
            default: return "ERR unknown\n";
        }
    }

    static long hex(String s) { return Long.parseUnsignedLong(s.startsWith("0x") ? s.substring(2) : s, 16); }

    static int tp(String[] a, int i) { return a.length > i ? parseType(a[i].split("\\s+")[0]) : type; }

    static void clearResults() {
        count = 0; addrs = new long[0]; lastVals = new long[0];
        fuzzy = false; snapRegions.clear(); snapBits.clear();
        if (snapFile != null) snapFile.delete();
    }

    // ---------- attach ----------
    static String attach(String target) throws IOException {
        int p;
        if (target.matches("\\d+")) p = Integer.parseInt(target);
        else p = findPid(target);
        if (p <= 0) return "ERR 找不到进程，请先启动游戏: " + target + "\n";
        if (p == pid && mem != null) return "OK " + p + "\n";
        if (mem != null) try { mem.close(); } catch (IOException ignored) {}
        mem = new RandomAccessFile("/proc/" + p + "/mem", "rw");
        pid = p;
        clearResults();
        frozen.clear();
        saved.clear();
        return "OK " + p + "\n";
    }

    static int findPid(String pkg) {
        File[] procs = new File("/proc").listFiles();
        if (procs == null) return -1;
        int best = -1;
        for (File f : procs) {
            String n = f.getName();
            if (!n.matches("\\d+")) continue;
            try {
                byte[] b = readAll(new File(f, "cmdline"));
                int e = 0;
                while (e < b.length && b[e] != 0) e++;
                String cmd = new String(b, 0, e);
                if (cmd.equals(pkg)) return Integer.parseInt(n);
                if (best < 0 && cmd.startsWith(pkg + ":")) best = Integer.parseInt(n);
            } catch (Exception ignored) {}
        }
        return best;
    }

    static byte[] readAll(File f) throws IOException {
        try (FileInputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int r;
            while ((r = in.read(buf)) > 0) bo.write(buf, 0, r);
            return bo.toByteArray();
        }
    }

    // ---------- 内存区域（对应 GG 的 Ca/Ch/Cd/Cb/A/Jh/J/As/O） ----------
    static List<long[]> regions() throws IOException {
        List<long[]> list = new ArrayList<>();
        BufferedReader r = new BufferedReader(new FileReader("/proc/" + pid + "/maps"));
        String l;
        while ((l = r.readLine()) != null) {
            String[] p = l.split("\\s+", 6);
            if (p.length < 5 || !p[1].startsWith("rw")) continue;
            String path = p.length > 5 ? p[5].trim() : "";
            if (path.contains("[anon:dalvik-card") || path.contains("[anon:dalvik-mark") || path.contains("[anon:dalvik-live")
                    || path.contains("stack_and_tls") || path.startsWith("/dev/kgsl") || path.startsWith("/dev/mali")
                    || path.contains("[vvar]") || path.contains("zframe")) continue;
            boolean common = path.isEmpty()                                   // A
                    || path.startsWith("[heap]")                              // Ch
                    || path.startsWith("[anon:libc_malloc") || path.startsWith("[anon:scudo") // Ca
                    || path.contains(".bss")                                  // Cb
                    || path.endsWith(".so");                                  // Cd
            boolean java = path.startsWith("[anon:dalvik-main") || path.startsWith("[anon:dalvik-large")
                    || path.startsWith("[anon:dalvik-");                      // Jh / J
            boolean ok = regionMode == 2 || common || (regionMode == 1 && java);
            if (!ok) continue;
            if (regionMode < 2 && path.startsWith("/system")) continue;
            String[] se = p[0].split("-");
            list.add(new long[]{Long.parseUnsignedLong(se[0], 16), Long.parseUnsignedLong(se[1], 16)});
        }
        r.close();
        return list;
    }

    static int size(int t) {
        switch (t) { case 2: case 3: return 8; case 4: return 1; default: return 4; }
    }

    static int parseType(String t) {
        switch (t) {
            case "float": return 1; case "qword": return 2; case "double": return 3; case "byte": return 4;
            default: return 0;
        }
    }

    static long readRaw(byte[] b, int i, int sz) {
        if (sz == 1) return b[i] & 0xffL;
        long v = (b[i] & 0xffL) | (b[i + 1] & 0xffL) << 8 | (b[i + 2] & 0xffL) << 16 | (b[i + 3] & 0xffL) << 24;
        if (sz == 8) v |= (b[i + 4] & 0xffL) << 32 | (b[i + 5] & 0xffL) << 40 | (b[i + 6] & 0xffL) << 48 | (b[i + 7] & 0xffL) << 56;
        return v;
    }

    static double num(long raw, int t) {
        switch (t) {
            case 1: return Float.intBitsToFloat((int) raw);
            case 3: return Double.longBitsToDouble(raw);
            case 0: return (int) raw;
            case 4: return (byte) raw;
            default: return raw;
        }
    }

    static long rawOf(String v, int t) {
        switch (t) {
            case 1: return Float.floatToRawIntBits(Float.parseFloat(v)) & 0xffffffffL;
            case 3: return Double.doubleToRawLongBits(Double.parseDouble(v));
            case 2: return Long.parseLong(v);
            case 4: return Long.parseLong(v) & 0xff;
            default: return Long.parseLong(v) & 0xffffffffL;
        }
    }

    /** 单个数值条件：精确或范围 */
    static class Cond {
        boolean range; double lo, hi; long raw; boolean exactInt;
        Cond(String v, int t) {
            int i = v.indexOf('~');
            if (i > 0) {
                range = true;
                double a = Double.parseDouble(v.substring(0, i)), b = Double.parseDouble(v.substring(i + 1));
                lo = Math.min(a, b); hi = Math.max(a, b);
            } else if (t == 1 || t == 3) {
                double w = Double.parseDouble(v);
                double eps = t == 1 ? Math.max(0.0005, Math.abs(w) * 1e-5) : Math.max(1e-6, Math.abs(w) * 1e-9);
                range = true; lo = w - eps; hi = w + eps;
            } else {
                exactInt = true; raw = rawOf(v, t);
            }
        }
        boolean ok(long r, int t) {
            if (exactInt) return r == raw;
            double d = num(r, t);
            return d >= lo && d <= hi;
        }
    }

    // ---------- 搜索 ----------
    static String search(String t, String v) throws IOException {
        if (mem == null) return "ERR 未附加进程\n";
        clearResults();
        type = parseType(t);
        if (v.equals("?")) return snapshot();
        if (v.contains(";")) return groupSearch(v);
        Cond c = new Cond(v, type);
        int sz = size(type);
        long[] res = new long[1 << 16], vals = new long[1 << 16];
        int n = 0;
        byte[] buf = new byte[1 << 20];
        boolean full = false;
        for (long[] rg : regions()) {
            for (long base = rg[0]; base < rg[1] && !full; base += buf.length) {
                int len = (int) Math.min(buf.length, rg[1] - base);
                try { mem.seek(base); mem.readFully(buf, 0, len); } catch (IOException e) { break; }
                for (int i = 0; i + sz <= len; i += sz) {
                    long raw = readRaw(buf, i, sz);
                    if (c.exactInt ? raw != c.raw : !c.ok(raw, type)) continue;
                    if (n == res.length) { res = Arrays.copyOf(res, n * 2); vals = Arrays.copyOf(vals, n * 2); }
                    res[n] = base + i; vals[n++] = raw;
                    if (n >= MAX_RESULTS) { full = true; break; }
                }
            }
            if (full) break;
        }
        addrs = res; lastVals = vals; count = n;
        return "OK " + n + (full ? " (结果过多已截断，请继续筛选)" : "") + "\n";
    }

    /** 联合搜索：所有值都出现在 dist 字节以内才算命中，结果包含每个成员 */
    static String groupSearch(String v) throws IOException {
        int dist = 512;
        int di = v.indexOf("::");
        if (di > 0) { dist = Integer.parseInt(v.substring(di + 2).trim()); v = v.substring(0, di); }
        String[] parts = v.split(";");
        Cond[] cs = new Cond[parts.length];
        for (int i = 0; i < parts.length; i++) cs[i] = new Cond(parts[i].trim(), type);
        int sz = size(type);
        int chunk = 1 << 20;
        byte[] buf = new byte[chunk + dist];
        long[] res = new long[1 << 12], vals = new long[1 << 12];
        int n = 0;
        TreeSet<Long> added = new TreeSet<>();
        for (long[] rg : regions()) {
            for (long base = rg[0]; base < rg[1] && n < MAX_RESULTS; base += chunk) {
                int len = (int) Math.min(buf.length, rg[1] - base);
                try { mem.seek(base); mem.readFully(buf, 0, len); } catch (IOException e) { break; }
                int scanEnd = (int) Math.min(chunk, len);
                for (int i = 0; i + sz <= scanEnd; i += sz) {
                    if (!cs[0].ok(readRaw(buf, i, sz), type)) continue;
                    int lo = Math.max(0, i - dist), hi = Math.min(len - sz, i + dist);
                    int[] pos = new int[cs.length];
                    pos[0] = i;
                    boolean all = true;
                    for (int k = 1; k < cs.length && all; k++) {
                        pos[k] = -1;
                        for (int j = lo; j <= hi; j += sz) {
                            if (j == i) continue;
                            if (cs[k].ok(readRaw(buf, j, sz), type)) { pos[k] = j; break; }
                        }
                        all = pos[k] >= 0;
                    }
                    if (!all) continue;
                    for (int p : pos) {
                        long ad = base + p;
                        if (!added.add(ad)) continue;
                        if (n == res.length) { res = Arrays.copyOf(res, n * 2); vals = Arrays.copyOf(vals, n * 2); }
                        res[n] = ad; vals[n++] = readRaw(buf, p, sz);
                    }
                }
            }
        }
        // 按地址排序
        Integer[] idx = new Integer[n];
        for (int i = 0; i < n; i++) idx[i] = i;
        final long[] r0 = res;
        Arrays.sort(idx, (x, y) -> Long.compareUnsigned(r0[x], r0[y]));
        addrs = new long[n]; lastVals = new long[n];
        for (int i = 0; i < n; i++) { addrs[i] = res[idx[i]]; lastVals[i] = vals[idx[i]]; }
        count = n;
        return "OK " + n + "\n";
    }

    static List<BitSet> snapBits = new ArrayList<>();
    static long fuzzyCount = 0;

    static File snapPath(String suffix) {
        File dir = new File(System.getProperty("java.io.tmpdir", "/data/local/tmp"));
        if (!dir.canWrite()) dir = new File("/data/local/tmp");
        return new File(dir, "zframe_snap_" + pid + suffix + ".bin");
    }

    /** 未知值：把内存区域拍成快照存到磁盘，每个位置一个候选位 */
    static String snapshot() throws IOException {
        snapFile = snapPath("");
        snapBits.clear();
        long total = 0;
        int sz = size(type);
        byte[] buf = new byte[1 << 20];
        try (OutputStream o = new BufferedOutputStream(new FileOutputStream(snapFile), 1 << 20)) {
            for (long[] rg : regions()) {
                long start = rg[0], got = 0;
                for (long base = rg[0]; base < rg[1]; base += buf.length) {
                    int len = (int) Math.min(buf.length, rg[1] - base);
                    try { mem.seek(base); mem.readFully(buf, 0, len); } catch (IOException e) { break; }
                    o.write(buf, 0, len);
                    got += len;
                }
                if (got > 0) {
                    snapRegions.add(new long[]{start, start + got});
                    int slots = (int) (got / sz);
                    BitSet b = new BitSet(slots);
                    b.set(0, slots);
                    snapBits.add(b);
                    total += got;
                }
            }
        }
        fuzzy = true;
        fuzzyCount = total / sz;
        return "OK 已记录 " + (total >> 20) + " MB 内存，回游戏让数值变化后点 变大/变小/没变/变了\n";
    }

    static boolean cmp(String mode, Cond c, long now, long before, int t) {
        switch (mode) {
            case "changed": return now != before;
            case "unchanged": return now == before;
            case "increased": return num(now, t) > num(before, t);
            case "decreased": return num(now, t) < num(before, t);
            default: return c.ok(now, t);
        }
    }

    static boolean isMode(String v) {
        return v.equals("changed") || v.equals("unchanged") || v.equals("increased") || v.equals("decreased");
    }

    static String refine(String v) throws IOException {
        if (mem == null) return "ERR 未附加进程\n";
        Cond c = isMode(v) ? null : new Cond(v, type);
        int sz = size(type);
        if (fuzzy) return refineSnapshot(v, c, sz);
        int k = 0;
        byte[] b = new byte[sz];
        for (int i = 0; i < count; i++) {
            long raw;
            try { mem.seek(addrs[i]); mem.readFully(b); raw = readRaw(b, 0, sz); } catch (IOException e) { continue; }
            if (cmp(v, c, raw, lastVals[i], type)) { addrs[k] = addrs[i]; lastVals[k++] = raw; }
        }
        count = k;
        return "OK " + k + "\n";
    }

    /** 模糊筛选：对照快照逐个候选位比较，结果多时继续留在快照模式（不会截断丢结果） */
    static String refineSnapshot(String mode, Cond c, int sz) throws IOException {
        File next = snapPath("_n");
        byte[] cur = new byte[1 << 20], old = new byte[1 << 20];
        long remain = 0;
        try (InputStream in = new BufferedInputStream(new FileInputStream(snapFile), 1 << 20);
             OutputStream out = new BufferedOutputStream(new FileOutputStream(next), 1 << 20)) {
            for (int r = 0; r < snapRegions.size(); r++) {
                long[] rg = snapRegions.get(r);
                BitSet bits = snapBits.get(r);
                for (long base = rg[0]; base < rg[1]; base += cur.length) {
                    int len = (int) Math.min(cur.length, rg[1] - base);
                    readFully(in, old, len);
                    int slot0 = (int) ((base - rg[0]) / sz);
                    boolean ok = true;
                    try { mem.seek(base); mem.readFully(cur, 0, len); } catch (IOException e) { ok = false; }
                    if (!ok) { bits.clear(slot0, slot0 + len / sz); out.write(old, 0, len); continue; }
                    for (int s = bits.nextSetBit(slot0); s >= 0 && s < slot0 + len / sz; s = bits.nextSetBit(s + 1)) {
                        int i = (s - slot0) * sz;
                        if (i + sz > len) { bits.clear(s); continue; }
                        if (!cmp(mode, c, readRaw(cur, i, sz), readRaw(old, i, sz), type)) bits.clear(s);
                    }
                    out.write(cur, 0, len);
                }
                remain += bits.cardinality();
            }
        }
        snapFile.delete();
        next.renameTo(snapFile);
        fuzzyCount = remain;
        if (remain > MAX_RESULTS) return "OK " + remain + " (继续筛选，剩到 300 万以内会转成结果列表)\n";
        // 转成普通结果
        long[] res = new long[(int) remain], vals = new long[(int) remain];
        int n = 0;
        byte[] b = new byte[sz];
        for (int r = 0; r < snapRegions.size(); r++) {
            long[] rg = snapRegions.get(r);
            BitSet bits = snapBits.get(r);
            for (int s = bits.nextSetBit(0); s >= 0 && n < res.length; s = bits.nextSetBit(s + 1)) {
                long ad = rg[0] + (long) s * sz;
                res[n] = ad;
                try { mem.seek(ad); mem.readFully(b); vals[n] = readRaw(b, 0, sz); } catch (IOException e) { vals[n] = 0; }
                n++;
            }
        }
        fuzzy = false;
        snapFile.delete();
        snapRegions.clear();
        snapBits.clear();
        addrs = res; lastVals = vals; count = n;
        return "OK " + n + "\n";
    }

    static void readFully(InputStream in, byte[] b, int len) throws IOException {
        int off = 0;
        while (off < len) {
            int r = in.read(b, off, len - off);
            if (r < 0) throw new EOFException();
            off += r;
        }
    }

    // ---------- 读写 ----------
    static String fmt(long raw, int t) {
        switch (t) {
            case 1: return Float.toString(Float.intBitsToFloat((int) raw));
            case 3: return Double.toString(Double.longBitsToDouble(raw));
            case 0: return Integer.toString((int) raw);
            case 4: return Integer.toString((byte) raw);
            default: return Long.toString(raw);
        }
    }

    static String readFmt(long addr, int t) {
        try {
            byte[] b = new byte[size(t)];
            mem.seek(addr);
            mem.readFully(b);
            return fmt(readRaw(b, 0, b.length), t);
        } catch (Exception e) { return "?"; }
    }

    static String list(int n) {
        if (fuzzy) return "OK " + fuzzyCount + " 模糊候选（继续筛选）\n";
        StringBuilder sb = new StringBuilder("OK " + count + "\n");
        for (int i = 0; i < Math.min(n, count); i++)
            sb.append(Long.toHexString(addrs[i])).append(' ').append(readFmt(addrs[i], type))
              .append(frozen.containsKey(addrs[i]) ? " F" : "").append('\n');
        return sb.toString();
    }

    static String view(long addr, int n, int t) {
        int sz = size(t);
        StringBuilder sb = new StringBuilder("OK " + n + "\n");
        for (int i = 0; i < n; i++) {
            long ad = addr + (long) i * sz;
            sb.append(Long.toHexString(ad)).append(' ').append(readFmt(ad, t))
              .append(frozen.containsKey(ad) ? " F" : "").append('\n');
        }
        return sb.toString();
    }

    static String savedList() {
        StringBuilder sb = new StringBuilder("OK " + saved.size() + "\n");
        for (Map.Entry<Long, String[]> e : saved.entrySet()) {
            int t = parseType(e.getValue()[0]);
            sb.append(Long.toHexString(e.getKey())).append(' ').append(readFmt(e.getKey(), t))
              .append(frozen.containsKey(e.getKey()) ? " F" : " -").append(' ').append(e.getValue()[0])
              .append(' ').append(e.getValue()[1]).append('\n');
        }
        return sb.toString();
    }

    static byte[] encode(String v, int t) {
        long raw = rawOf(v, t);
        int sz = size(t);
        byte[] b = new byte[sz];
        for (int i = 0; i < sz; i++) b[i] = (byte) (raw >>> (8 * i));
        return b;
    }

    static synchronized void writeBytes(long addr, byte[] b) throws IOException {
        mem.seek(addr);
        mem.write(b);
    }

    static void write(long addr, String v, int t) throws IOException {
        if (mem == null) throw new IOException("未附加进程");
        writeBytes(addr, encode(v, t));
    }

    static void freezeLoop() {
        while (true) {
            try {
                Thread.sleep(80);
                if (mem == null) continue;
                Map.Entry<Long, byte[]>[] es;
                synchronized (frozen) { es = frozen.entrySet().toArray(new Map.Entry[0]); }
                for (Map.Entry<Long, byte[]> e : es) {
                    try { writeBytes(e.getKey(), e.getValue()); } catch (IOException ignored) {}
                }
            } catch (InterruptedException ignored) {}
        }
    }
}
