package com.zframe.inject;
import java.nio.*;
/** 内存扫描单测：在直接内存里放一个假的 CDO（flags 0x10）和一个假对象，scan 应该跳过 CDO、找到对象。 */
public class ScanTest {
    public static void main(String[] a) throws Exception {
        long vt = 0x5a5b5c5dL;
        ByteBuffer b = ByteBuffer.allocateDirect(1 << 16).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(0x100, (int) vt).putInt(0x104, 0x10); b.putInt(0x800, (int) vt).putInt(0x804, 0x1).putInt(0x808, 1234).putInt(0x80c, 0x7f001230).putInt(0x818, 0x7f004560);
        b.putInt(0x400, (int) vt).putInt(0x404, 0x1);   // 只有 vtable 值、不像对象
        long addr = ((sun.nio.ch.DirectBuffer) b).address();
        try (java.io.RandomAccessFile m = new java.io.RandomAccessFile("/proc/self/mem", "r")) {
            long t0 = System.currentTimeMillis(), f = Server.scan(m, vt);
            System.out.println("found " + Long.toHexString(f) + " expect " + Long.toHexString(addr + 0x800) + " in " + (System.currentTimeMillis() - t0) + "ms, read " + Server.rd(m, f + 4));
        }
    }
}
