package com.zframe.inject;

import java.io.ByteArrayOutputStream;

/** v1.61 最小的 protobuf 编解码（只用到 varint 和 bytes 两种），给自建服务端拼包、拆包用。 */
public final class PB {
    final ByteArrayOutputStream o = new ByteArrayOutputStream();

    PB varint(long v) { while ((v & ~0x7fL) != 0) { o.write((int) ((v & 0x7f) | 0x80)); v >>>= 7; } o.write((int) v); return this; }
    PB u(int field, long v) { return varint((long) field << 3).varint(v); }
    PB b(int field, byte[] v) { varint(((long) field << 3) | 2).varint(v.length); o.write(v, 0, v.length); return this; }
    PB m(int field, PB v) { return b(field, v.bytes()); }
    byte[] bytes() { return o.toByteArray(); }

    /** 读：按顺序取字段。wire 0 → val；wire 2 → 子串 [off, off+len) */
    static final class R {
        final byte[] d; int p; final int end;
        int field, wire, off, len; long val;
        R(byte[] d) { this(d, 0, d.length); }
        R(byte[] d, int off, int len) { this.d = d; p = off; end = off + len; }
        long vint() { long r = 0; int s = 0; while (true) { int c = d[p++] & 0xff; r |= (long) (c & 0x7f) << s; if (c < 0x80) return r; s += 7; } }
        boolean next() {
            if (p >= end) return false;
            long t = vint(); field = (int) (t >>> 3); wire = (int) (t & 7);
            switch (wire) {
                case 0: val = vint(); break;
                case 2: len = (int) vint(); off = p; p += len; break;
                case 5: off = p; p += 4; break;
                case 1: off = p; p += 8; break;
                default: return false;
            }
            return p <= end;
        }
        R sub() { return new R(d, off, len); }
        byte[] raw() { return java.util.Arrays.copyOfRange(d, off, off + len); }
    }
}
