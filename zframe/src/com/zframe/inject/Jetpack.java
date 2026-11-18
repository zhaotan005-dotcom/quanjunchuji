package com.zframe.inject;

import java.io.RandomAccessFile;

/** v3.03 用户：喷气背包燃料上限 999999、每次消耗 1、不过热一直飞。
 *  RE（libUE4 反射表，ANZItem_JetpackBagBase，/Script/NZBattleRoyaleGame，属性数组 0x4ce8a68，全是 float）：
 *   CurrentEnergy +0x388  MaxEnergy +0x38c  LaunchCostEnergyPer +0x390  LaunchCostEnergyPerFromCfg +0x394  HoldCostEnergyPerSec +0x398
 *   CurrentDurable +0x39c MaxDurable +0x3a0 LaunchCostDurablePer +0x3a4  LaunchCostDurablePerFromCfg +0x3a8 HoldCostDurablePerSec +0x3ac
 *   RecoverDurablePerSec +0x3b0  RecoverDurableCDTime +0x3b8  CurrentState +0x3f4  LastLaunchTime +0x3fc
 *  哪一条是「燃料」、哪一条是「过热」没实测，两条都改：上限 999999、起飞/按住每次扣 1、当前值第一次见到时填满。
 *  找对象：GUObjectArray 里类名（对象+0xc 的类的名字）含 JetpackBag 的活对象（蓝图子类也算），服务端和客户端都改（服务端说了算，客户端改了界面不显示过热）。 */
final class Jetpack {
    static volatile String state = "喷气背包：还没见到";
    static final float BIG = 999999f;
    static final int CUR_E = 0x388, MAX_E = 0x38c, LC_E = 0x390, LC_E_CFG = 0x394, HC_E = 0x398,
                     CUR_D = 0x39c, MAX_D = 0x3a0, LC_D = 0x3a4, LC_D_CFG = 0x3a8, HC_D = 0x3ac;
    static volatile boolean running;
    static final java.util.HashMap<Long, Boolean> clsOk = new java.util.HashMap<>();
    static void start() {
        if (running) return; running = true;
        new Thread(() -> {
            while (true) {
                try { Thread.sleep(2000); } catch (InterruptedException e) { return; }
                if (!Con.inMatch) continue;
                try { tick(); } catch (Throwable t) { state = "喷气背包：出错 " + t.getMessage(); }
            }
        }, "zf-jetpack").start();
    }
    static boolean sane(float v) { return !Float.isNaN(v) && v >= 0f && v <= 2e6f; }
    static void tick() throws Exception {
        long b = Mods.base(); if (b == 0) return;
        Veh.Names nm = Veh.Names.get(); if (nm == null) return;
        int n = 0, fixed = 0;
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "rw")) {
            long g = Server.rd(m, b + 0x51250acL); if (g < 0x10000) return;
            long objs = Server.rd(m, g + 0x10), num = Server.rd(m, g + 0x18);
            if (objs < 0x10000 || num <= 0 || num > 0x200000) return;
            byte[] blk = new byte[16 * 4096];
            for (long i = 0; i < num; i += 4096) {
                int k = (int) Math.min(4096, num - i);
                m.seek(objs + i * 16); m.readFully(blk, 0, k * 16);
                java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(blk).order(java.nio.ByteOrder.LITTLE_ENDIAN);
                for (int j = 0; j < k; j++) {
                    long o = bb.getInt(j * 16) & 0xffffffffL; int f = bb.getInt(j * 16 + 4);
                    if (o < 0x10000 || (f & 0x20000000) != 0) continue;
                    long c = Server.rd(m, o + 0xc); if (c < 0x10000) continue;
                    Boolean ok = clsOk.get(c);
                    if (ok == null) { String cn = Alive.objName(m, nm, c); ok = cn.contains("JetpackBag") || cn.contains("JetPackBag"); clsOk.put(c, ok); }
                    if (!ok || (Server.rd(m, o + 4) & 0x10) != 0) continue;   // 跳过类默认对象
                    if (!sane(Mods.f(m, o + MAX_E)) || !sane(Mods.f(m, o + MAX_D))) continue;
                    n++;
                    if (Mods.f(m, o + MAX_E) != BIG || Mods.f(m, o + MAX_D) != BIG) {
                        LogCap.note(String.format("喷气背包 %x 原值：能量 %.1f/%.1f 起飞扣 %.2f 按住每秒扣 %.2f；耐久 %.1f/%.1f 起飞扣 %.2f 按住每秒扣 %.2f",
                            o, Mods.f(m, o + CUR_E), Mods.f(m, o + MAX_E), Mods.f(m, o + LC_E), Mods.f(m, o + HC_E),
                            Mods.f(m, o + CUR_D), Mods.f(m, o + MAX_D), Mods.f(m, o + LC_D), Mods.f(m, o + HC_D)));
                        Mods.wf(m, o + MAX_E, BIG); Mods.wf(m, o + CUR_E, BIG);
                        Mods.wf(m, o + MAX_D, BIG); Mods.wf(m, o + CUR_D, BIG);
                        fixed++;
                    }
                    for (int off : new int[] { LC_E, LC_E_CFG, HC_E, LC_D, LC_D_CFG, HC_D }) if (Mods.f(m, o + off) != 1f) Mods.wf(m, o + off, 1f);
                }
            }
        }
        String s = "喷气背包：" + (n == 0 ? "这局还没见到" : n + " 个，上限 999999，每次扣 1" + (fixed > 0 ? "（刚改 " + fixed + " 个）" : ""));
        if (!s.equals(state)) { state = s; LogCap.note(s); }
    }
}
