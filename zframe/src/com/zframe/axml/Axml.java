package com.zframe.axml;

import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** 极简二进制 AndroidManifest.xml 读写器：解析成节点列表，可插入元素，再序列化。 */
public class Axml {
    public static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    static final int T_NS_START = 0x0100, T_NS_END = 0x0101, T_START = 0x0102, T_END = 0x0103, T_CDATA = 0x0104;
    public static final int TYPE_STRING = 0x03, TYPE_BOOL = 0x12, TYPE_INT = 0x10;

    public static class Attr {
        public String ns, name, raw; public int resId; public int type; public int data; public String strData;
        public Attr(String ns, String name, int resId, int type, int data, String strData, String raw) {
            this.ns = ns; this.name = name; this.resId = resId; this.type = type; this.data = data; this.strData = strData; this.raw = raw;
        }
    }

    public static class Node {
        public int type, line; public String comment;
        public String ns, name;           // element / ns: prefix in ns, uri in name
        public List<Attr> attrs = new ArrayList<>();
        public int idIndex, classIndex, styleIndex;
        public String cdata; public int cType, cData;
    }

    public List<Node> nodes = new ArrayList<>();

    // ---------------- 解析 ----------------
    public static Axml parse(byte[] b) throws IOException {
        ByteBuffer bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN);
        if (bb.getShort(0) != 0x0003) throw new IOException("不是二进制 XML");
        Axml x = new Axml();
        String[] strings = new String[0];
        int[] resMap = new int[0];
        int pos = bb.getShort(2) & 0xffff;
        while (pos < b.length) {
            int type = bb.getShort(pos) & 0xffff, hsz = bb.getShort(pos + 2) & 0xffff, size = bb.getInt(pos + 4);
            if (size <= 0) break;
            if (type == 0x0001) strings = readPool(bb, pos);
            else if (type == 0x0180) {
                int n = (size - hsz) / 4;
                resMap = new int[n];
                for (int i = 0; i < n; i++) resMap[i] = bb.getInt(pos + hsz + i * 4);
            } else if (type >= T_NS_START && type <= T_CDATA) {
                Node nd = new Node();
                nd.type = type;
                nd.line = bb.getInt(pos + 8);
                nd.comment = s(strings, bb.getInt(pos + 12));
                int e = pos + hsz;
                if (type == T_NS_START || type == T_NS_END) {
                    nd.ns = s(strings, bb.getInt(e)); nd.name = s(strings, bb.getInt(e + 4));
                } else if (type == T_END) {
                    nd.ns = s(strings, bb.getInt(e)); nd.name = s(strings, bb.getInt(e + 4));
                } else if (type == T_CDATA) {
                    nd.cdata = s(strings, bb.getInt(e)); nd.cType = bb.get(e + 7) & 0xff; nd.cData = bb.getInt(e + 8);
                } else {
                    nd.ns = s(strings, bb.getInt(e)); nd.name = s(strings, bb.getInt(e + 4));
                    int aStart = bb.getShort(e + 8) & 0xffff, aSize = bb.getShort(e + 10) & 0xffff, aCount = bb.getShort(e + 12) & 0xffff;
                    nd.idIndex = bb.getShort(e + 14) & 0xffff; nd.classIndex = bb.getShort(e + 16) & 0xffff; nd.styleIndex = bb.getShort(e + 18) & 0xffff;
                    for (int i = 0; i < aCount; i++) {
                        int a = e + aStart + i * aSize;
                        int nameIdx = bb.getInt(a + 4);
                        int t = bb.get(a + 15) & 0xff, d = bb.getInt(a + 16);
                        nd.attrs.add(new Attr(s(strings, bb.getInt(a)), s(strings, nameIdx),
                                nameIdx >= 0 && nameIdx < resMap.length ? resMap[nameIdx] : 0,
                                t, d, t == TYPE_STRING ? s(strings, d) : null, s(strings, bb.getInt(a + 8))));
                    }
                }
                x.nodes.add(nd);
            }
            pos += size;
        }
        return x;
    }

    static String s(String[] a, int i) { return i < 0 || i >= a.length ? null : a[i]; }

    static String[] readPool(ByteBuffer bb, int p) {
        int count = bb.getInt(p + 8), flags = bb.getInt(p + 16), strStart = bb.getInt(p + 20);
        boolean utf8 = (flags & 0x100) != 0;
        String[] out = new String[count];
        for (int i = 0; i < count; i++) {
            int off = p + strStart + bb.getInt(p + 28 + i * 4);
            if (utf8) {
                int o = off;
                int c = bb.get(o++) & 0xff; if ((c & 0x80) != 0) o++;
                int len = bb.get(o++) & 0xff; if ((len & 0x80) != 0) len = ((len & 0x7f) << 8) | (bb.get(o++) & 0xff);
                byte[] d = new byte[len];
                for (int k = 0; k < len; k++) d[k] = bb.get(o + k);
                out[i] = new String(d, StandardCharsets.UTF_8);
            } else {
                int o = off;
                int len = bb.getShort(o) & 0xffff; o += 2;
                if ((len & 0x8000) != 0) { len = ((len & 0x7fff) << 16) | (bb.getShort(o) & 0xffff); o += 2; }
                char[] c = new char[len];
                for (int k = 0; k < len; k++) c[k] = bb.getChar(o + k * 2);
                out[i] = new String(c);
            }
        }
        return out;
    }

    // ---------------- 序列化 ----------------
    public byte[] write() {
        // 1) 带资源 ID 的属性名放在字符串池最前面
        LinkedHashMap<String, Integer> resNames = new LinkedHashMap<>();
        for (Node n : nodes) for (Attr a : n.attrs) if (a.resId != 0 && !resNames.containsKey(a.name)) resNames.put(a.name, a.resId);
        List<String> pool = new ArrayList<>(resNames.keySet());
        Map<String, Integer> idx = new HashMap<>();
        for (int i = 0; i < pool.size(); i++) idx.put(pool.get(i), i);
        int[] resIds = new int[pool.size()];
        int k = 0; for (int v : resNames.values()) resIds[k++] = v;
        for (Node n : nodes) {
            add(pool, idx, n.comment, n.ns, n.name, n.cdata);
            for (Attr a : n.attrs) add(pool, idx, a.ns, a.name, a.raw, a.strData);
        }
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        writePool(body, pool);
        // res map
        ByteBuffer rm = le(8 + resIds.length * 4);
        rm.putShort((short) 0x0180).putShort((short) 8).putInt(8 + resIds.length * 4);
        for (int r : resIds) rm.putInt(r);
        body.write(rm.array(), 0, rm.capacity());
        for (Node n : nodes) {
            ByteBuffer c;
            switch (n.type) {
                case T_NS_START: case T_NS_END: case T_END:
                    c = le(24); hdr(c, n, 24, idx); c.putInt(i(idx, n.ns)).putInt(i(idx, n.name)); break;
                case T_CDATA:
                    c = le(28); hdr(c, n, 28, idx); c.putInt(i(idx, n.cdata)).putShort((short) 8).put((byte) 0).put((byte) n.cType)
                            .putInt(n.cType == TYPE_STRING ? i(idx, n.cdata) : n.cData); break;
                default: {
                    int sz = 36 + n.attrs.size() * 20;
                    c = le(sz); hdr(c, n, sz, idx);
                    c.putInt(i(idx, n.ns)).putInt(i(idx, n.name)).putShort((short) 20).putShort((short) 20)
                            .putShort((short) n.attrs.size()).putShort((short) n.idIndex).putShort((short) n.classIndex).putShort((short) n.styleIndex);
                    for (Attr a : n.attrs) {
                        c.putInt(i(idx, a.ns)).putInt(i(idx, a.name)).putInt(i(idx, a.raw)).putShort((short) 8).put((byte) 0).put((byte) a.type)
                                .putInt(a.type == TYPE_STRING ? i(idx, a.strData) : a.data);
                    }
                }
            }
            body.write(c.array(), 0, c.capacity());
        }
        byte[] bd = body.toByteArray();
        ByteBuffer out = le(8 + bd.length);
        out.putShort((short) 0x0003).putShort((short) 8).putInt(8 + bd.length).put(bd);
        return out.array();
    }

    static void add(List<String> pool, Map<String, Integer> idx, String... ss) {
        for (String s : ss) if (s != null && !idx.containsKey(s)) { idx.put(s, pool.size()); pool.add(s); }
    }

    static int i(Map<String, Integer> idx, String s) { return s == null ? -1 : idx.get(s); }

    static ByteBuffer le(int n) { return ByteBuffer.allocate(n).order(ByteOrder.LITTLE_ENDIAN); }

    static void hdr(ByteBuffer c, Node n, int size, Map<String, Integer> idx) {
        c.putShort((short) n.type).putShort((short) 16).putInt(size).putInt(n.line).putInt(i(idx, n.comment));
    }

    static void writePool(ByteArrayOutputStream out, List<String> pool) {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        int[] offs = new int[pool.size()];
        for (int k = 0; k < pool.size(); k++) {
            offs[k] = data.size();
            String s = pool.get(k);
            int len = s.length();
            if (len > 0x7fff) { w16(data, 0x8000 | (len >> 16)); w16(data, len & 0xffff); } else w16(data, len);
            for (int j = 0; j < len; j++) w16(data, s.charAt(j));
            w16(data, 0);
        }
        while (data.size() % 4 != 0) data.write(0);
        int strStart = 28 + pool.size() * 4;
        int size = strStart + data.size();
        ByteBuffer h = le(strStart);
        h.putShort((short) 0x0001).putShort((short) 28).putInt(size).putInt(pool.size()).putInt(0).putInt(0).putInt(strStart).putInt(0);
        for (int o : offs) h.putInt(o);
        out.write(h.array(), 0, h.capacity());
        byte[] d = data.toByteArray();
        out.write(d, 0, d.length);
    }

    static void w16(ByteArrayOutputStream o, int v) { o.write(v & 0xff); o.write((v >> 8) & 0xff); }

    // ---------------- 编辑工具 ----------------
    public int findStart(String name) {
        for (int k = 0; k < nodes.size(); k++) if (nodes.get(k).type == T_START && name.equals(nodes.get(k).name)) return k;
        return -1;
    }

    /** 找到 start 对应的 end 节点下标 */
    public int findEnd(int start) {
        int depth = 0;
        for (int k = start; k < nodes.size(); k++) {
            int t = nodes.get(k).type;
            if (t == T_START) depth++;
            else if (t == T_END && --depth == 0) return k;
        }
        return -1;
    }

    public String attr(int nodeIdx, String name) {
        for (Attr a : nodes.get(nodeIdx).attrs) if (name.equals(a.name)) return a.type == TYPE_STRING ? a.strData : a.raw;
        return null;
    }

    public static Node start(String name, int line, Attr... attrs) {
        Node n = new Node();
        n.type = T_START; n.name = name; n.line = line;
        n.attrs.addAll(Arrays.asList(attrs));
        n.attrs.sort((a, b) -> Integer.compareUnsigned(a.resId, b.resId));
        return n;
    }

    public static Node end(String name, int line) {
        Node n = new Node();
        n.type = T_END; n.name = name; n.line = line;
        return n;
    }

    public static Attr str(String name, int resId, String v) { return new Attr(ANDROID_NS, name, resId, TYPE_STRING, 0, v, v); }

    public static Attr bool(String name, int resId, boolean v) { return new Attr(ANDROID_NS, name, resId, TYPE_BOOL, v ? -1 : 0, null, null); }
}
