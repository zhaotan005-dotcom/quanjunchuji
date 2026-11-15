package com.zframe.inject;
import java.io.*; import java.nio.*;
/** 离线单测：写出 cave 机器码和各种回包，给 game/re/emu_server.py 用游戏自己的 schema 解码校验。 */
public class ServerTest {
    public static void main(String[] a) throws Exception {
        PrintWriter w = new PrintWriter(a[0]);
        w.println(Server.sendCave(0x7a000000)); w.println(Server.tickCave(0x7b000000, 0x40000008));
        w.println(hex(Server.depositProps()));
        byte[] eq = new PB().m(1, new PB().u(1, 30302000000L).u(2, 1001)).u(2, 8).bytes();
        w.println(hex(Server.equip(eq)));
        w.println(hex(new PB().m(1, new PB().u(1, 36001).u(4, 7).u(7, 1)).bytes()));
        // 邮件：用 push 进回包区，再按记录取出来
        Server.rpl = ByteBuffer.allocate(16 + Server.RPL_CAP).order(ByteOrder.LITTLE_ENDIAN);
        Server.newMail();
        w.println(hex(rec(0)));                                      // 25007 整包
        w.println(hex(Server.mailList()));                           // 25002
        w.println(hex(Server.readMails(new PB().u(1, 1).u(2, 1).bytes())));   // 25004（领取 index 1）
        w.println(hex(Server.depositProps()));                       // 领取后的 13002
        w.println(hex(Server.readMails(new PB().u(1, 1).u(2, 1).bytes())));   // 再领一次：不重复给
        w.close();
    }
    static byte[] rec(int off) { int n = Server.rpl.getInt(16 + off); byte[] d = new byte[n]; for (int i = 0; i < n; i++) d[i] = Server.rpl.get(16 + off + 4 + i); return d; }
    static String hex(byte[] d) { StringBuilder s = new StringBuilder(); for (byte b : d) s.append(String.format("%02x", b & 0xff)); return s.toString(); }
}
