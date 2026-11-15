package com.zframe.inject;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.RandomAccessFile;
import java.util.*;

/**
 * v1.47 找绝地风衣编号（只读，不改游戏内存）。
 * 游戏进大厅后会把物品表（名字是 UTF-32 的 FString）载进内存，v1.2x 的 ItemFind 已经看到过「绝地风衣」这几个字。
 * 做法：
 *  1. 扫 /proc/self/maps 里可读写的匿名内存，找 UTF-32 的「绝地风衣」；
 *  2. 再找指向这段文字开头的指针（FString.Data），也就是物品表那一行；
 *  3. 把这一行前后 0xC0 字节里像物品编号的数都收起来：
 *     11 位的 3xxxxxxxxxx（int64），或者 int32 的 3030000~3039999 / 303xx（分类）+ 序号，
 *     还有 UTF-32 的 11 位数字字符串；
 *  4. 候选编号按出现次数排序，交给 GameOverlay 逐个去刷，刷得出来的那个就是。
 * 所有原始内容都写进日志（下载/ZFrame），没找到时把日志发回来再分析。
 */
public class CoatFind {
    public static volatile String state = "";
    public static volatile boolean running;

    static final String NAME = "绝地风衣";

    static byte[] u32(String s) {
        int[] cp = s.codePoints().toArray();
        byte[] b = new byte[cp.length * 4];
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
                if (name.startsWith("/") && !name.contains("dalvik")) continue;      // 跳过库文件的数据段
                if (name.contains("stack") || name.contains("dalvik-main space") || name.contains("jit")) continue;
                long s = Long.parseLong(p[0].substring(0, p[0].indexOf('-')), 16), e = Long.parseLong(p[0].substring(p[0].indexOf('-') + 1), 16);
                if (e - s > 512L << 20) continue;
                r.add(new long[]{s, e});
            }
        } catch (Throwable t) { LogCap.note("找风衣: 读 maps 失败 " + t); }
        return r;
    }

    interface Hit { void on(long addr, byte[] buf, int off); }

    /** 在所有区域里找 needle（4 字节对齐），每找到一个回调一次 */
    static long scan(RandomAccessFile m, List<long[]> rs, byte[][] needles, Hit h) {
        long total = 0; byte[] buf = new byte[1 << 20];
        int maxN = 0; for (byte[] n : needles) maxN = Math.max(maxN, n.length);
        for (long[] r : rs) {
            for (long a = r[0]; a < r[1]; ) {
                int len = (int) Math.min(buf.length, r[1] - a);
                try { m.seek(a); m.readFully(buf, 0, len); } catch (Throwable e) { a += len; continue; }
                total += len;
                for (int i = 0; i + 4 <= len; i += 4) {
                    for (byte[] n : needles) {
                        if (buf[i] != n[0] || buf[i + 1] != n[1] || buf[i + 2] != n[2] || buf[i + 3] != n[3]) continue;
                        if (i + n.length > len) continue;
                        boolean ok = true;
                        for (int k = 4; k < n.length; k++) if (buf[i + k] != n[k]) { ok = false; break; }
                        if (ok) h.on(a + i, buf, i);
                    }
                }
                a += len - (len == buf.length ? maxN : 0);
                if (len < buf.length) break;
            }
        }
        return total;
    }

    static long u32le(byte[] b, int o) { return (b[o] & 255) | (b[o + 1] & 255) << 8 | (b[o + 2] & 255) << 16 | (long) (b[o + 3] & 255) << 24; }

    /** 结果：候选编号（按可能性排好） */
    public static List<String> run() {
        running = true;
        long t0 = System.currentTimeMillis();
        final Map<String, Integer> score = new LinkedHashMap<>();
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
            List<long[]> rs = regions();
            state = "第 1 步：找「绝地风衣」文字（" + rs.size() + " 块内存）";
            final List<Long> strs = new ArrayList<>();
            final Map<Long, String> full = new HashMap<>();
            long tot = scan(m, rs, new byte[][]{u32(NAME)}, (addr, buf, off) -> {
                if (strs.size() > 200) return;
                // 往前找到字符串开头（前一个字符是 0），往后读完整名字
                long start = addr;
                try {
                    for (int k = 0; k < 16; k++) { int p = off - 4 * (k + 1); if (p < 0) break; long c = u32le(buf, p); if (c == 0 || c > 0x10ffff) break; start -= 4; }
                    StringBuilder sb = new StringBuilder();
                    for (int p = off - (int) (addr - start); p + 4 <= buf.length && sb.length() < 40; p += 4) { long c = u32le(buf, p); if (c == 0 || c > 0x10ffff) break; sb.appendCodePoint((int) c); }
                    strs.add(start); full.put(start, sb.toString());
                } catch (Throwable ignored) {}
            });
            LogCap.note(String.format("找风衣: 扫了 %d MB，找到文字 %d 处 %s", tot >> 20, strs.size(), full.values()));
            if (strs.isEmpty()) { state = "内存里没有「绝地风衣」这几个字，进大厅打开一次仓库/商城再试"; return new ArrayList<>(); }
            state = "第 2 步：找物品表里指向这些文字的行（" + strs.size() + " 处）";
            byte[][] ptrs = new byte[strs.size()][];
            for (int i = 0; i < ptrs.length; i++) { long v = strs.get(i); ptrs[i] = new byte[]{(byte) v, (byte) (v >> 8), (byte) (v >> 16), (byte) (v >> 24)}; }
            final int[] rows = {0};
            scan(m, rs, ptrs, (addr, buf, off) -> {
                if (rows[0] > 60) return;
                rows[0]++;
                String nm = full.get(u32le(buf, off));
                // 精确的名字（不带「脱帽」之类）权重大一些
                int w = NAME.equals(nm) ? 3 : 1;
                int lo = Math.max(0, off - 0xc0), hi = Math.min(buf.length, off + 0xc0);
                StringBuilder hex = new StringBuilder();
                for (int p = lo; p < hi; p += 4) hex.append(String.format("%08x ", u32le(buf, p)));
                LogCap.note(String.format("找风衣: 行 @%x 名字=%s 前后: %s", addr, nm, hex));
                for (int p = lo; p + 8 <= hi; p += 4) {
                    long v = u32le(buf, p) | u32le(buf, p + 4) << 32;
                    if (v >= 30100000000L && v < 30400000000L) add(score, String.valueOf(v), w * (10 - Math.min(9, Math.abs(p - off) / 0x20)));
                }
                for (int p = lo; p + 4 <= hi; p += 4) {
                    long v = u32le(buf, p);
                    // 7 位：3031600 + 序号 这类缩写（分类 5 位 + 2 位序号）
                    if (v >= 3010000 && v < 3040000) add(score, String.format(Locale.US, "%d00%03d", v / 100, v % 100), w);
                    // 分类和序号分开存：303xx 后面紧跟一个小序号
                    if (v >= 30100 && v < 30400 && p + 8 <= hi) { long n = u32le(buf, p + 4); if (n > 0 && n < 1000) add(score, String.format(Locale.US, "%d%06d", v, n), w); }
                }
            });
            LogCap.note("找风衣: 物品表行 " + rows[0] + " 个，候选 " + score);
        } catch (Throwable t) {
            LogCap.note("找风衣出错 " + t); state = "出错：" + t.getMessage();
        } finally { running = false; }
        List<Map.Entry<String, Integer>> es = new ArrayList<>(score.entrySet());
        Collections.sort(es, (a, b) -> b.getValue() - a.getValue());
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Integer> e : es) out.add(e.getKey());
        state = out.isEmpty() ? "找到了名字，但旁边没有像编号的数，日志已记下，发给我" : "找到 " + out.size() + " 个候选编号，正在逐个试穿…";
        LogCap.note("找风衣: 用时 " + (System.currentTimeMillis() - t0) + "ms 候选顺序 " + out);
        return out;
    }

    static void add(Map<String, Integer> m, String k, int w) { Integer o = m.get(k); m.put(k, (o == null ? 0 : o) + w); }
}
