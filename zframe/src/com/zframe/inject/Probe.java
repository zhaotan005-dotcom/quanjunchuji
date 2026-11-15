package com.zframe.inject;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.RandomAccessFile;

/**
 * v1.59 人物探针：只在玩家按「开始人物探针」后才装。给大厅/仓库/角色相关的游戏函数各加一个计数器，
 * 记下被调用几次、最后一次是从哪里调来的（返回地址），导出诊断时一起写出来。用来确定大厅人物到底走哪条路。
 *
 * 做法：函数开头 8 字节换成 ldr pc,[pc,#-4] + 跳板地址。跳板放在 libUE4 代码段末尾到页尾的空隙
 * （文件里代码段长 0x4a2d528，映射到 0x4a2e000，中间这段可执行、游戏不用）。每个跳板 0x40：
 *   push {r0,r1}; movw/movt r0,计数地址; ldr r1,[r0]; add r1,#1; str r1,[r0]; str lr,[r0,#4]; pop {r0,r1};
 *   原来的两条指令; ldr pc,[pc,#-4]; .word 函数+8
 * 选的函数开头两条都是 push / add fp / mov 这类和位置无关的指令，装之前逐个核对原字节。
 */
public class Probe {
    static final long SLACK = 0x4a2d540L, SLACK_END = 0x4a2e000L;
    static final int STRIDE = 0x40;
    static final Object[][] FN = {
        { "InitAvatar 大厅人物初始化", 0x135afd0L, "f04b2de918b08de2" },
        { "InitAvatarClotheTry 试穿", 0x1503208L, "f04f2de91cb08de2" },
        { "CacheStoreProp 物品入库", 0x1872b04L, "f04f2de91cb08de2" },
        { "UpdateStoreCache 仓库缓存", 0x1872dc8L, "f04f2de91cb08de2" },
        { "c62d48 补默认衣服", 0xc62d48L, "f04f2de91cb08de2" },
        { "EquipItemsForHallRole 大厅穿衣", 0x1207d40L, "f04f2de91cb08de2" },
        { "1205d60 代理管理器", 0x1205d60L, "30482de908b08de2" },
        { "InitAvatarProxy 局内穿衣", 0x10d7380L, "f04f2de91cb08de2" },
        { "exec EquipItemsForHallRole", 0x16a60a4L, "f0482de910b08de2" },
        { "exec GetDefaultCharacterClothes", 0x16a63f0L, "f0482de910b08de2" },
        { "exec ShowLobbyDisplayType", 0x16a6b34L, "30482de908b08de2" },
        { "exec RotateHallRole", 0x16a69a4L, "704c2de910b08de2" },
        { "1877a1c 是否拥有", 0x1877a1cL, "f04b2de918b08de2" },
        { "18739b8 按编号找物品", 0x18739b8L, "30482de960e090e5" },
        { "1877ec8 角色身上衣服", 0x1877ec8L, "f04f2de91cb08de2" },
        { "cc69cc 物品检查", 0xcc69ccL, "00482de90db0a0e1" },
    };
    static java.nio.ByteBuffer cnt; static long cntAddr;
    public static volatile String state = "人物探针：没开";

    static String w32(int w) { return String.format("%02x%02x%02x%02x", w & 0xff, (w >> 8) & 0xff, (w >> 16) & 0xff, (w >>> 24) & 0xff); }

    /** 一个跳板的机器码（纯函数，测试用） */
    static String tramp(int counter, String orig8, int ret) {
        return "03002de9"                                   // push {r0, r1}
            + Mods.armMov(false, 0, counter & 0xffff) + Mods.armMov(true, 0, (counter >>> 16) & 0xffff)
            + "001090e5" + "011081e2" + "001080e5"          // ldr r1,[r0]; add r1,r1,#1; str r1,[r0]
            + "04e080e5"                                    // str lr,[r0,#4]
            + "0300bde8"                                    // pop {r0, r1}
            + orig8
            + "04f01fe5" + w32(ret);                        // ldr pc,[pc,#-4]; .word ret
    }
    static String hook(int target) { return "04f01fe5" + w32(target); }

    static boolean slackIsCode(long b) {
        long lo = b + SLACK, hi = b + SLACK_END;
        try (BufferedReader r = new BufferedReader(new FileReader("/proc/self/maps"))) {
            String l;
            while ((l = r.readLine()) != null) {
                if (!l.contains("libUE4")) continue;
                String[] p = l.split("\\s+");
                String[] ab = p[0].split("-");
                long s = Long.parseLong(ab[0], 16), e = Long.parseLong(ab[1], 16);
                if (s <= lo && hi <= e) return p[1].startsWith("r-x");
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public static synchronized String start() {
        if (cnt != null) return state;
        long b = Mods.base();
        if (b == 0) return state = "人物探针：找不到游戏库";
        if (!slackIsCode(b)) return state = "人物探针：代码段空隙不可用，没装";
        if (FN.length * STRIDE > SLACK_END - SLACK) return state = "人物探针：空间不够";
        cnt = java.nio.ByteBuffer.allocateDirect(FN.length * 8).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        cntAddr = GameOverlay.bufAddr(cnt);
        if (cntAddr == 0) { cnt = null; return state = "人物探针：拿不到内存地址"; }
        int ok = 0; StringBuilder skip = new StringBuilder();
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "rw")) {
            for (int i = 0; i < FN.length; i++) {
                long off = (Long) FN[i][1]; String orig = (String) FN[i][2];
                byte[] cur = new byte[8]; m.seek(b + off); m.readFully(cur);
                if (!Patches.hexs(cur).replace(" ", "").equalsIgnoreCase(orig)) { skip.append(' ').append(i); continue; }
                long t = b + SLACK + (long) i * STRIDE;
                m.seek(t); m.write(Patches.hex(tramp((int) (cntAddr + i * 8), orig, (int) (b + off + 8))));
                m.seek(b + off); m.write(Patches.hex(hook((int) t)));
                ok++;
            }
        } catch (Throwable e) { return state = "人物探针：出错 " + e.getMessage(); }
        return state = "人物探针：已装 " + ok + "/" + FN.length + (skip.length() > 0 ? "，原字节不符跳过" + skip : "");
    }

    /** 诊断文本：每个函数被调几次、最后一次的调用方（相对 libUE4 的偏移） */
    public static String report() {
        if (cnt == null) return state + "\n";
        long b = Mods.base();
        StringBuilder sb = new StringBuilder(state).append('\n');
        for (int i = 0; i < FN.length; i++) {
            int n = cnt.getInt(i * 8); long lr = cnt.getInt(i * 8 + 4) & 0xffffffffL;
            sb.append(String.format("%-34s %6d 次", FN[i][0], n));
            if (n > 0) sb.append("  来自 ").append(lr >= b && lr < b + 0x6000000L ? "+" + Long.toHexString(lr - b) : Long.toHexString(lr));
            sb.append('\n');
        }
        return sb.toString();
    }
}
