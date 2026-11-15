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
    /* x = UFUNCTION 的 exec 入口（蓝图/Lua 调 C++ 时走），ev = C++ 调蓝图事件的包装（开头是 ldr r0,[pc,#n]; ldr r0,[pc,r0]，
     * 跳板里换成 ldr r0,=绝对地址; ldr r0,[r0]，绝对地址装的时候按游戏里的实际字节算） */
    static final Object[][] FN = {
        { "InitAvatar", 0x135afd0L, "f04b2de918b08de2" },
        { "EquipItemsForHallRole impl", 0x1207d40L, "f04f2de91cb08de2" },
        { "cc69cc 物品检查", 0xcc69ccL, "00482de90db0a0e1" },
        { "x HandleDisplayAvatar", 0x16a6630L, "f0482de910b08de2" },
        { "x ShowHallRole", 0x16a6aacL, "30482de908b08de2" },
        { "x GetDefaultCharacterID", 0x16a64f0L, "704c2de910b08de2" },
        { "x GetDefaultCharacterClothes", 0x16a63f0L, "f0482de910b08de2" },
        { "x EquipItemsForHallRole", 0x16a60a4L, "f0482de910b08de2" },
        { "x ShowLobbyDisplayType", 0x16a6b34L, "30482de908b08de2" },
        { "x RotateHallRole", 0x16a69a4L, "704c2de910b08de2" },
        { "x ShowAvatar", 0x161ba1cL, "f04d2de918b08de2" },
        { "x TryDressItem", 0x161bb98L, "f04d2de918b08de2" },
        { "x OnSexComboBoxChanged", 0x1638198L, "704c2de910b08de2" },
        { "x RoleSettingBtnClick", 0x1687e34L, "102091e5000052e3" },
        { "x FittingRoomBtnClick", 0x1687a20L, "102091e5000052e3" },
        { "x OnRoleStateChanged", 0x16886b4L, "30482de908b08de2" },
        { "x OnFaceCBStateChanged", 0x1687bb4L, "30482de908b08de2" },
        { "x OnEquipBtnClick", 0x1688e5cL, "102091e5000052e3" },
        { "x ShowWarehouseUI", 0x16980e0L, "102091e5000052e3" },
        { "x OnClickTabRole", 0x1815420L, "102091e5000052e3" },
        { "x OnChangeSkinButton", 0x1815330L, "104c2de908b08de2" },
        { "x OnHairMoreBtnClick", 0x16751a4L, "102091e5000052e3" },
        { "x OnFaceMoreBtnClick", 0x167517cL, "102091e5000052e3" },
        { "x OnRoleSelectionConfirm", 0x1673804L, "102091e5000052e3" },
        { "x OnAvatarClick", 0x166de90L, "102091e5000052e3" },
        { "x InitAvatarIcons", 0x1653230L, "104c2de908b08de2" },
        { "x SetCharacter", 0xe7f064L, "30482de908b08de2" },
        { "x OnBtnFaceClick", 0x1625e30L, "102091e5000052e3" },
        { "x OnFaceBtnClick", 0x1683a34L, "102091e5000052e3" },
        { "LuaGet_FaceIndex", 0x3d9301cL, "104c2de908b08de2" },
        { "LuaSet_FaceIndex", 0x3d93048L, "30482de908b08de2" },
        { "ev HandleDisplayAvatar", 0x16a7354L, "34009fe500009fe7" },
        { "ev ShowHallRole", 0x16a7624L, "34009fe500009fe7" },
        { "ev InitDefaultRole", 0x16a7474L, "34009fe500009fe7" },
        { "ev EquipItemsForHallRole", 0x16a7084L, "34009fe500009fe7" },
        { "ev GetDefaultCharacterClothes", 0x16a71ecL, "34009fe500009fe7" },
        { "ev ShowLobbyDisplayType", 0x16a766cL, "34009fe500009fe7" },
        { "ev TryDressItem", 0x161ae0cL, "b8009fe500009fe7" },
        { "ev ShowAvatar", 0x161c08cL, "34009fe500009fe7" },
        { "ev ShowWarehouseUI", 0x16988f0L, "34009fe500009fe7" },
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
    /** ev 型：原两条是 ldr r0,[pc,#imm]; ldr r0,[pc,r0]，等价于 r0 = *(abs) */
    static String trampPcrel(int counter, int abs, int ret) {
        return "03002de9"
            + Mods.armMov(false, 0, counter & 0xffff) + Mods.armMov(true, 0, (counter >>> 16) & 0xffff)
            + "001090e5" + "011081e2" + "001080e5" + "04e080e5" + "0300bde8"
            + "08009fe5" + "000090e5"                       // ldr r0,[pc,#8] (= abs); ldr r0,[r0]
            + "04f01fe5" + w32(ret) + w32(abs);
    }
    static boolean isPcrel(String orig) { return orig.startsWith("9fe5", 4) && orig.endsWith("00009fe7") && orig.startsWith("0", 2); }
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
                String tr;
                if (isPcrel(orig)) {
                    int imm = Integer.parseInt(orig.substring(2, 4) + orig.substring(0, 2), 16) & 0xfff;  // 低 12 位
                    byte[] lw = new byte[4]; m.seek(b + off + 8 + imm); m.readFully(lw);
                    int lit = (lw[0] & 0xff) | (lw[1] & 0xff) << 8 | (lw[2] & 0xff) << 16 | (lw[3] & 0xff) << 24;
                    tr = trampPcrel((int) (cntAddr + i * 8), (int) (b + off + 12 + lit), (int) (b + off + 8));
                } else tr = tramp((int) (cntAddr + i * 8), orig, (int) (b + off + 8));
                m.seek(t); m.write(Patches.hex(tr));
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
