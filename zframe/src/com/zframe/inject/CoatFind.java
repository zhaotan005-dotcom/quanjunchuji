package com.zframe.inject;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.*;

/**
 * v1.49 找绝地风衣编号（只读，不改游戏内存）。
 *
 * v1.48 实测：内存里「绝地风衣」是 UE4 的 FText。文字对象长这样（32 位）：
 *   [引用计数器 vt][强引用][弱引用] [ITextData vt][本地化串指针][本地化串计数器] [History vt][版本][源文字 data][num][max]
 * 物品表那一行里存的 FText = {对象指针 O, 计数器指针 O-0xC, flags}，物品编号是这一行在表里的行名（FName，如 "30316000007"），
 * 或者是行里的 int64 / 路径文字。所以分 4 遍扫：
 *  1. 找 UTF-32 的「绝地风衣」「绝地风衣脱帽版」（完整、前后都是 0），同时收集内存里所有 11 位编号形式的 FName 名字（ASCII）；
 *  2. 找指向这些文字的指针 P（FString.Data）；
 *  3. 找文字对象 O=P-0x14 被谁引用：相邻两个字是 (O, O-0xC) 的地方就是物品表行里的 FText；
 *     P 本身被引用的地方（本地化串）→ 它前面 4 字节是另一个文字对象 O2，第 4 遍再找 (O2, O2-0xC)；
 *  4. 同一遍里找表的 RowMap 元素 {FName 序号, 0, 行指针}，行指针落在 FText 前 0x300 字节内 → 行名就是编号。
 * 另外在行附近找 int64 编号、指针指向的含 11 位编号的文字（图标/模型路径），都当候选。
 */
public class CoatFind {
    public static volatile String state = "";
    public static volatile boolean running;

    static final String NAME = "绝地风衣", NAME_OFF = "绝地风衣脱帽版";

    /** 读内存的接口：真机用 /proc/self/mem，测试用文件 */
    public interface Mem { void read(long addr, byte[] buf, int len) throws IOException; }

    public static final class Result {
        public final List<String> hood = new ArrayList<>();     // 绝地风衣（按可能性排好）
        public final List<String> noHood = new ArrayList<>();   // 绝地风衣脱帽版
        @Override public String toString() { return "绝地风衣=" + hood + " 脱帽版=" + noHood; }
    }

    // ---------------- 公用 ----------------
    static long u32(byte[] b, int o) { return (b[o] & 255) | (b[o + 1] & 255) << 8 | (b[o + 2] & 255) << 16 | (long) (b[o + 3] & 255) << 24; }

    static byte[] utf32(String s) {
        int[] cp = s.codePoints().toArray();
        byte[] b = new byte[cp.length * 4 + 4];               // 带结尾的 0
        for (int i = 0; i < cp.length; i++) { int c = cp[i]; b[4 * i] = (byte) c; b[4 * i + 1] = (byte) (c >> 8); b[4 * i + 2] = (byte) (c >> 16); b[4 * i + 3] = (byte) (c >> 24); }
        return b;
    }

    static List<long[]> regions() {
        List<long[]> r = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/self/maps"), 1 << 16)) {
            String l;
            while ((l = br.readLine()) != null) {
                String[] p = l.split("\\s+");
                if (p.length < 5 || !p[1].startsWith("rw")) continue;
                String name = p.length >= 6 ? p[5] : "";
                if (name.startsWith("/") || name.contains("stack") || name.contains("dalvik") || name.contains("jit")) continue;
                int d = p[0].indexOf('-');
                long s = Long.parseLong(p[0].substring(0, d), 16), e = Long.parseLong(p[0].substring(d + 1), 16);
                if (e - s <= 512L << 20) r.add(new long[]{s, e});
            }
        } catch (Throwable t) { LogCap.note("找风衣: 读 maps 失败 " + t); }
        return r;
    }

    interface Visit { void at(long addr, byte[] buf, int i, int len); }

    /** 逐块读所有区域，每个 4 字节对齐的位置回调一次（块之间重叠 64 字节，避免漏掉跨块的内容） */
    static long walk(Mem m, List<long[]> rs, Visit v) {
        final int CH = 1 << 20, OV = 64;
        byte[] buf = new byte[CH];
        long total = 0;
        for (long[] r : rs) {
            for (long a = r[0]; a < r[1]; ) {
                int len = (int) Math.min(CH, r[1] - a);
                try { m.read(a, buf, len); } catch (Throwable e) { a += len; continue; }
                total += len;
                int end = len == CH && a + len < r[1] ? len - OV : len;   // 重叠部分留给下一块
                for (int i = 0; i + 4 <= end; i += 4) v.at(a + i, buf, i, len);
                a += end;
            }
        }
        return total;
    }

    static boolean match(byte[] buf, int i, int len, byte[] n) {
        if (i + n.length > len) return false;
        for (int k = 0; k < n.length; k++) if (buf[i + k] != n[k]) return false;
        return true;
    }

    /** 11 位、30 开头的物品编号 */
    static boolean isId(long v) { return v >= 30100000000L && v < 30500000000L; }

    // ---------------- 主流程 ----------------
    public static Result run() {
        try (RandomAccessFile f = new RandomAccessFile("/proc/self/mem", "r")) {
            return run((addr, buf, len) -> { f.seek(addr); f.readFully(buf, 0, len); }, regions());
        } catch (Throwable t) {
            LogCap.note("找风衣出错 " + t); state = "出错：" + t.getMessage(); running = false;
            return new Result();
        }
    }

    public static Result run(final Mem m, final List<long[]> rs) {
        running = true;
        long t0 = System.currentTimeMillis();
        final Map<String, Integer> hood = new HashMap<>(), off = new HashMap<>();
        try {
            // ---- 第 1 遍：文字 + FName 编号名 ----
            state = "第 1/4 步：找「绝地风衣」文字";
            final byte[] n1 = utf32(NAME), n2 = utf32(NAME_OFF);
            final Map<Long, Boolean> strs = new HashMap<>();          // 文字地址 → 是不是脱帽版
            final Map<Long, String> fnames = new HashMap<>();         // FName 序号 → 编号（UE4.18：条目里存的是 序号<<1）
            final Map<Long, String> fnamesRaw = new HashMap<>();      // 备用：条目里的原值
            long tot = walk(m, rs, (a, b, i, len) -> {
                // UTF-32 文字：前一个字是 0（不是别的字的后半截）
                if (u32(b, i) == 0x7edd /* 绝 */ && (i < 4 || u32(b, i - 4) == 0) && strs.size() < 400) {
                    if (match(b, i, len, n2)) strs.put(a, true);
                    else if (match(b, i, len, n1)) strs.put(a, false);
                }
                // FNameEntry：[Index][HashNext] "30xxxxxxxxx\0"（ASCII，11 位）
                if (b[i] == '3' && b[i + 1] == '0' && i >= 8 && i + 12 <= len && b[i + 11] == 0) {
                    boolean digits = true;
                    for (int k = 2; k < 11; k++) if (b[i + k] < '0' || b[i + k] > '9') { digits = false; break; }
                    if (digits) {
                        String s = new String(b, i, 11, java.nio.charset.StandardCharsets.US_ASCII);
                        long idx = u32(b, i - 8);
                        if (isId(Long.parseLong(s)) && idx > 0 && idx < (1L << 24) && fnamesRaw.size() < 200000) {
                            if ((idx & 1) == 0) fnames.put(idx >> 1, s);
                            fnamesRaw.put(idx, s);
                        }
                    }
                }
            });
            LogCap.note(String.format(Locale.US, "找风衣: 第1遍 %d MB，文字 %d 处，编号名字 %d 个", tot >> 20, strs.size(), fnamesRaw.size()));
            if (strs.isEmpty()) { state = "内存里没有「绝地风衣」，先进大厅再进一局后再点"; return new Result(); }

            // ---- 第 2 遍：指向文字的指针（FString.Data）----
            state = "第 2/4 步：找文字对象";
            final Map<Long, Boolean> dataP = new HashMap<>();          // FString.Data 所在地址 → 脱帽版?
            walk(m, rs, (a, b, i, len) -> {
                Boolean h = strs.get(u32(b, i));
                if (h != null && dataP.size() < 2000) dataP.put(a, h);
            });
            LogCap.note("找风衣: 第2遍 文字指针 " + dataP.size() + " 个");

            // ---- 第 3 遍：FText 引用 (O, O-0xC)，以及本地化串被谁引用 ----
            state = "第 3/4 步：找物品表里的行";
            final Map<Long, Boolean> objs = new HashMap<>();
            for (Map.Entry<Long, Boolean> e : dataP.entrySet()) objs.put(e.getKey() - 0x14, e.getValue());
            final Map<Long, Boolean> rows = new LinkedHashMap<>();     // 行里 FText 的地址 → 脱帽版?
            final Map<Long, Boolean> obj2 = new HashMap<>();
            walk(m, rs, (a, b, i, len) -> {
                long w = u32(b, i);
                Boolean h = objs.get(w);
                if (h != null && i + 8 <= len && u32(b, i + 4) == w - 0xc && rows.size() < 200) rows.put(a, h);
                Boolean h2 = dataP.get(w);
                if (h2 != null && i >= 4 && obj2.size() < 2000) obj2.put(a - 4, h2);
            });

            // ---- 第 4 遍：本地化串那条路的 FText，和 RowMap 元素 ----
            state = "第 4/4 步：找物品编号";
            final Map<Long, Boolean> rows2 = new LinkedHashMap<>();
            walk(m, rs, (a, b, i, len) -> {
                long w = u32(b, i);
                Boolean h = obj2.get(w);
                if (h != null && i + 8 <= len && u32(b, i + 4) == w - 0xc && rows2.size() < 200) rows2.put(a, h);
            });
            rows.putAll(rows2);
            LogCap.note("找风衣: 物品表里的 FText " + rows.size() + " 处（本地化串那条路 " + rows2.size() + "）");

            // RowMap：{FName 序号, Number=0, 行指针}，行指针在 FText 前 0x300 字节内
            if (!rows.isEmpty() && !fnamesRaw.isEmpty()) {
                final long[] rk = new long[rows.size()]; int q = 0;
                for (long r : rows.keySet()) rk[q++] = r;
                Arrays.sort(rk);
                final Map<Long, Boolean> rowsF = rows;
                walk(m, rs, (a, b, i, len) -> {
                    if (i < 8) return;
                    long p = u32(b, i);
                    int k = Arrays.binarySearch(rk, p);
                    int at = k >= 0 ? k : -k - 1;                       // 第一个 >= p 的 FText
                    if (at >= rk.length || rk[at] - p > 0x300) return;
                    if (u32(b, i - 4) != 0) return;                     // FName.Number
                    long key = u32(b, i - 8);
                    String id = fnames.containsKey(key) ? fnames.get(key) : fnamesRaw.get(key);
                    if (id == null) return;
                    add(rowsF.get(rk[at]) ? off : hood, id, 100);
                    LogCap.note(String.format(Locale.US, "找风衣: 行名 %s 行@%x FText@%x", id, p, rk[at]));
                });
            }

            // 行附近：int64 编号、指针指向的文字里的 11 位编号
            byte[] w = new byte[0x400], s = new byte[0x200];
            for (Map.Entry<Long, Boolean> e : rows.entrySet()) {
                long r = e.getKey(); Map<String, Integer> to = e.getValue() ? off : hood;
                try { m.read(r - 0x200, w, w.length); } catch (Throwable t) { continue; }
                StringBuilder hex = new StringBuilder();
                for (int p = 0x180; p < 0x280; p += 4) hex.append(String.format("%08x ", u32(w, p)));
                LogCap.note(String.format(Locale.US, "找风衣: FText@%x %s 前后 %s", r, e.getValue() ? "脱帽版" : "绝地风衣", hex));
                for (int p = 0; p + 8 <= w.length; p += 4) {
                    long v = u32(w, p) | u32(w, p + 4) << 32;
                    if (isId(v)) add(to, String.valueOf(v), 30 - Math.min(29, Math.abs(p - 0x200) / 0x20));
                    long ptr = u32(w, p);
                    if (ptr < 0x10000 || (ptr & 3) != 0) continue;
                    try { m.read(ptr, s, s.length); } catch (Throwable t) { continue; }
                    String txt = text(s);
                    java.util.regex.Matcher mm = java.util.regex.Pattern.compile("30[1-4]\\d{8}").matcher(txt);
                    while (mm.find()) { add(to, mm.group(), 20); LogCap.note("找风衣: 行里的文字 " + txt); }
                }
            }
        } catch (Throwable t) {
            LogCap.note("找风衣出错 " + t); state = "出错：" + t.getMessage();
        } finally { running = false; }
        Result res = new Result();
        res.hood.addAll(sorted(hood)); res.noHood.addAll(sorted(off));
        res.noHood.removeAll(res.hood);
        state = res.hood.isEmpty() ? "没找到编号，日志已记下（下载/ZFrame）" : "找到 " + res.hood.size() + " 个候选，正在试穿…";
        LogCap.note("找风衣: 用时 " + (System.currentTimeMillis() - t0) + "ms " + res);
        return res;
    }

    /** 读一段文字：UTF-32 或 ASCII，读到 0 为止 */
    static String text(byte[] s) {
        StringBuilder a = new StringBuilder(), u = new StringBuilder();
        for (int p = 0; p < s.length && s[p] != 0; p++) a.append((char) (s[p] & 255));
        for (int p = 0; p + 4 <= s.length; p += 4) { long c = u32(s, p); if (c == 0 || c > 0x10ffff) break; u.appendCodePoint((int) c); }
        return a + " " + u;
    }

    static void add(Map<String, Integer> m, String k, int w) { Integer o = m.get(k); m.put(k, (o == null ? 0 : o) + w); }

    static List<String> sorted(Map<String, Integer> m) {
        List<Map.Entry<String, Integer>> es = new ArrayList<>(m.entrySet());
        Collections.sort(es, (a, b) -> b.getValue() - a.getValue());
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Integer> e : es) out.add(e.getKey());
        return out;
    }
}
