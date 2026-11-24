package com.zframe.inject;

import java.io.RandomAccessFile;

/** v4.19（core321，未实测）投掷物 HUD：手拿烟雾/震爆等非手雷时，武器栏显示手雷图标和手雷捏雷倒计时。用户批准三个嫌疑都做防御修复（没上机诊断）。
 *  静态 RE（game/re/throwhud_cave.py、emu_throwhud.py；workspace work/re/throw_hud_findings.md）：
 *  1. 管理器+0x558：武器栏 OnDrawThrowWeapon 0x17f12c8 → GetLastThrowWeapon 0xfe90e8（管理器 vt+0x388 = +0x55c 当前武器，是投掷物就缓存 +0x558，不是就返回旧缓存）。
 *     +0x55c 只在 WeaponManager 换枪时顺带 SetCurrentWeapon（0xe2b7dc / 0xe2c22c，人物+0x838 取管理器失败就不更新）→ 可能停在旧手雷。
 *     修：0xfe90e8 入口 b 0x171e88c（死掉的 GM CloseGMPanel）：管理器+0x390 = 人物，且 人物+0x838 == 管理器 才信；
 *     人物 WeaponManager(+0x888)+0x2d8（原生每帧抛物线用的「手上那把」）有效（没 PendingKill）且 IsThrowableWeapon(0xcc93d0) → 缓存 +0x558 并返回；否则原函数照旧。
 *  2. 图标名：画图标 0x17ea248 读 武器+0x2c0 InventoryIdName → FName::ToString（0x17ea2a4 bl 0x1a8e880）→ 拼图标路径。
 *     修：这条 bl 改 bl 0x171eaf4（死掉的 GM SwitchToAimSet）：武器自己的 InventoryId（+0x2cc×10000 + +0x2d0，和 IsThrowableWeapon/0xce0280 同一算法）
 *     在 204xxxxxxxx（投掷物）就用 FString::Printf("%llu", 编号)（0x1936cfc，EmptyHand 同样用法）当图标名；别的武器照原样 ToString。
 *     SetInventoryIdName 第三个调用 0xed0120（0xecfee8）= 武器给自己的适配/皮肤挂件抄自己的名字，不会把手雷名字写到别的雷上。
 *  3. 倒计时：0xef9ac8（开火模式 vt+0x128，开始捏雷）原生只给 ThrowableType(+0x22) 1..3（手雷/震爆/烟雾）写 人物+0xdd0 = 自己的 AutoFireDelay（+0xa0）；
 *     pak：手雷 8.0、震爆 5.5、烟雾 7.0，三个 bAutoCountdownFireTime 都是 1 → 原生本意是震爆/烟雾也有自己的倒计时，不是只有手雷。
 *     倒计时控件 NativeTick 0x17029e0 读 +0xdd0 压一条再清 0。修：0xef9ab8 b 0x171eb78：照原判断 1..3，再加「这个开火模式的武器(+0x1c) == 人物手上那把」才写；
 *     WeaponManager 读不到就照原生写。别的投掷物（不是手上那把）不再启动倒计时。
 *  全部 cave 字节是静态的（同库内相对跳转），写前核对 cave 空位原字节（或已是我们的）和钩子原字节。对局开始装（Parabola.matchStart），各版本都装，不改玩法。 */
final class ThrowHud {
    static final long CAVE_A = 0x171eaf4L, CAVE_B = 0x171e88cL;
    static final String CAVE_A_ORIG = "704c2de910b08de20040a0e10150a0e1c40194e5000050e33d00000a700090e5000050e318609015000056133800000a5051ddeb0c1096e55c2090e55c3091e5030052e1320000ca581091e5580080e2021191e7000051e12d00001a510cebeb0c1096e55c2090e55c3091e5030052e1270000ca581091e5580080e2021191e7000051e12200001a146396e5000056e31f00000adef1eaeb0c1096e55c2090e55c3091e5030052e1190000ca581091e5580080e2021191e7000051e1";
    static final String CAVE_A_NEW = "d0402de9ac4195e5000054e31500000acc2294e5d03294e510c702e3926c87e0036096e00070a7e240209fe540309fe5022056e00330d7e00a00003a34209fe534309fe5022056e00330d7e00500002a0620a0e10730a0e120109fe501108fe0686008ebd080bde8d040bde846bf0dea004cefbf04000000002de5c50400000040931c032200d4e5010040e2ff0000e2020050e30700008a880895e5000050e30300000ad80290e51c1094e5010050e10000001ad06d85e5c66bdfea";
    static final String CAVE_B_ORIG = "104c2de908b08de2c40190e5000050e31a00000a700090e5000050e318409015000054131500000aec51ddeb0c1094e55c2090e55c3091e5030052e10f0000ca581091e5580080e2021191e7000051e10a00001aed0cebeb0c1094e55c2090e55c3091e5030052e1108cbdc8581091e5580080e2021191e7000051e10000000a108cbde8";
    static final String CAVE_B_NEW = "30482de908b08de20040a0e1900394e5000050e31700000a381890e5040051e11400001a880890e5000050e31100000ad85290e5000055e30e00000a3c009fe500009fe7100090e5081095e5010280e00700d0e5200010e30600001a0500a0e1b7aad6eb010050e30200001a585584e50500a0e13088bde80400a0e1f729e3ead867a003";
    static final long H_ICON = 0x17ea2a4L, H_LAST = 0xfe90e8L, H_CD = 0xef9ab8L;
    static final String ICON_O = "75910aeb", ICON_N = "12d2fceb";
    static final String LAST_O = "30482de9", LAST_N = "e7d51cea";
    static final String CD_O = "2200d4e5", CD_N = "2e9420ea";
    static volatile String state = "投掷物图标/倒计时：没装";
    static volatile boolean caveA, caveB;

    /** cave 空位：原字节 → 写我们的；已经是我们的 → ok；别的 → 不写 */
    static String cave(long off, String orig, String mine) {
        long b = Mods.base(); if (b == 0) return "找不到游戏库";
        byte[] want = Patches.hex(mine), o = Patches.hex(orig), cur = new byte[want.length];
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "rw")) {
            m.seek(b + off); m.readFully(cur);
            if (java.util.Arrays.equals(cur, want)) return "ok";
            if (!java.util.Arrays.equals(cur, o)) return "空位字节不符 " + Patches.hexs(java.util.Arrays.copyOf(cur, 8));
            m.seek(b + off); m.write(want);
            m.seek(b + off); m.readFully(cur);
            return java.util.Arrays.equals(cur, want) ? "ok" : "写入没生效";
        } catch (Throwable t) { return "出错 " + t.getMessage(); }
    }

    static synchronized void install() {
        StringBuilder sb = new StringBuilder("投掷物HUD：");
        if (!caveA) { String r = cave(CAVE_A, CAVE_A_ORIG, CAVE_A_NEW); caveA = "ok".equals(r); if (!caveA) sb.append("图标/倒计时 cave ").append(r).append("；"); }
        if (!caveB) { String r = cave(CAVE_B, CAVE_B_ORIG, CAVE_B_NEW); caveB = "ok".equals(r); if (!caveB) sb.append("手上投掷物 cave ").append(r).append("；"); }
        // 钩子只在对应 cave 写好后才改
        sb.append("图标=").append(caveA ? Mods.write(H_ICON, ICON_O, ICON_N, true) : "没装");
        sb.append(" 倒计时=").append(caveA ? Mods.write(H_CD, CD_O, CD_N, true) : "没装");
        sb.append(" 管理器+558=").append(caveB ? Mods.write(H_LAST, LAST_O, LAST_N, true) : "没装");
        state = sb.toString();
        if (Ed.uploads()) LogCap.note(state);
    }
}
