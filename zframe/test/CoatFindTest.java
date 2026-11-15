import com.zframe.inject.CoatFind;
import java.nio.*;
import java.util.*;

/** 用一块假的内存（照 v1.48 日志里看到的结构摆）验证 CoatFind 的 4 遍扫描能不能找出编号 */
public class CoatFindTest {
    static final long BASE = 0x40000000L;
    static ByteBuffer mem = ByteBuffer.allocate(3 << 20).order(ByteOrder.LITTLE_ENDIAN);
    static void w(long a, long v) { mem.putInt((int) (a - BASE), (int) v); }
    static void u32s(long a, String s) { int i = 0; for (int c : s.codePoints().toArray()) w(a + 4 * i++, c); w(a + 4 * i, 0); }
    static void ascii(long a, String s) { for (int i = 0; i < s.length(); i++) mem.put((int) (a - BASE) + i, (byte) s.charAt(i)); mem.put((int) (a - BASE) + s.length(), (byte) 0); }

    public static void main(String[] x) throws Exception {
        // 文字
        u32s(BASE + 0x1000, "绝地风衣");
        u32s(BASE + 0x1100, "绝地风衣脱帽版");
        u32s(BASE + 0x1200, "限时绝地风衣宝箱");             // 干扰：不完整匹配
        // 文字对象 A（源文字直接指向「绝地风衣」）
        long A = BASE + 0x2000;
        w(A, 0xb04e2c30L); w(A + 4, 2); w(A + 8, 1);
        long O = A + 0xc;
        w(O, 0xb04e27c0L); w(O + 0xc, 0xb04e3050L); w(O + 0x10, 1); w(O + 0x14, BASE + 0x1000); w(O + 0x18, 5); w(O + 0x1c, 5);
        // 文字对象 B（本地化串那条路：Lptr → FString{data,num,max}）
        long FS = BASE + 0x3000; w(FS, BASE + 0x1100); w(FS + 4, 8); w(FS + 8, 8);
        long B = BASE + 0x3100; w(B, 0xb04e2c30L); w(B + 4, 1); w(B + 8, 1);
        long O2 = B + 0xc; w(O2, 0xb04e27c0L); w(O2 + 4, FS); w(O2 + 8, BASE + 0x3200);
        // 物品表行 1：FText 在 +0x10，+0x30 是图标路径
        long R1 = BASE + 0x5000; w(R1 + 0x10, O); w(R1 + 0x14, O - 0xc);
        long PATH = BASE + 0x7000; u32s(PATH, "/Game/Arts/Avatar/Common/CoatWithHat/30316000099/T_x");
        w(R1 + 0x30, PATH);
        // 物品表行 2（脱帽版）
        long R2 = BASE + 0x6000; w(R2 + 0x10, O2); w(R2 + 0x14, O2 - 0xc);
        // RowMap 元素 {FName, 0, 行指针}
        w(BASE + 0x8000, 0x1234); w(BASE + 0x8004, 0); w(BASE + 0x8008, R1);
        w(BASE + 0x8020, 0x2222); w(BASE + 0x8024, 0); w(BASE + 0x8028, R2);
        // FNameEntry [Index<<1][HashNext] "编号"
        w(BASE + 0x9000, 0x1234 << 1); w(BASE + 0x9004, 0); ascii(BASE + 0x9008, "30316000088");
        w(BASE + 0x9100, 0x2222 << 1); w(BASE + 0x9104, 0); ascii(BASE + 0x9108, "30316000077");
        // 干扰：放在第二块（跨 1MB 边界）再放一份一样的 FText，确认块重叠不出问题
        long R3 = BASE + (1 << 20) - 8; w(R3, O); w(R3 + 4, O - 0xc);

        final byte[] arr = mem.array();
        CoatFind.Mem m = (addr, buf, len) -> {
            long o = addr - BASE;
            if (o < 0 || o + len > arr.length) throw new java.io.IOException("越界 " + Long.toHexString(addr));
            System.arraycopy(arr, (int) o, buf, 0, len);
        };
        List<long[]> rs = Collections.singletonList(new long[]{BASE, BASE + arr.length});
        CoatFind.Result r = CoatFind.run(m, rs);
        System.out.println(r);
        if (r.hood.isEmpty() || !r.hood.get(0).equals("30316000088")) throw new AssertionError("绝地风衣应排第一个是 30316000088");
        if (!r.hood.contains("30316000099")) throw new AssertionError("路径里的编号没找到");
        if (!r.noHood.contains("30316000077")) throw new AssertionError("脱帽版没找到");
        System.out.println("CoatFind 测试通过");
    }
}
