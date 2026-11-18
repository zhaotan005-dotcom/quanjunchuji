package com.zframe.inject;

import java.io.RandomAccessFile;

/**
 * v2.97 联机带宽（开车延迟、船上岸后一下车被拉回的根因之一）。
 * 证据（v2.95 16:08 日志）：服务端 "Client netspeed is 10000"（客户端按引擎默认 ConfiguredInternetSpeed=10000 字节/秒报上来），
 * 服务端 "Network saturated. NZIpNetConnection_0 skip replicated"（这一帧不给这台客户端复制任何 Actor），
 * 客户端每帧 "UNZIpNetConnection [...] Saturated"（上行约 120 字节/帧 × 80 帧 > 10000）。
 * 引擎在连接饱和时直接丢掉不可靠 RPC（UNetDriver::InternalProcessRemoteFunction）：ServerMove（标志 0x80220c40）、
 * 车的 ServerUpdateState（0x80280c40，带车的刚体状态）都是不可靠的 → 服务端收不到车/人的新位置，客户端也收不到服务端的纠正 →
 * 开车延迟、服务端那边车停在岸边、下车按服务端位置算。换弹 RPC ServerProceedWeapRepInfo 是可靠的（0x80220cc0），不受影响。
 * 另外听服（NetMode 2）还会被 AGameNetworkManager::UpdateNetSpeeds（0x29f6d70，非 LAN 才调）按 TotalNetBandwidth/人数 夹在
 * Min/MaxDynamicBandwidth 之间（GNM +0x2b8/+0x2bc/+0x2c0），专用服不走这条。
 * 改法（只改游戏自己的带宽配置字段，不改逻辑）：
 *  - 两边：NetDriver（World+0x24）MaxClientRate +0x3c、MaxInternetClientRate +0x38 提到 RATE（SetNetSpeed 0x2c595b0 按 +0x3c 夹）。
 *  - 服务端：GameNetworkManager（World+0x310，核对类名）Total/Min/MaxDynamicBandwidth 提高；每个加入玩家的连接
 *    （控制器+0x2fc Player = UNetConnection）CurrentNetSpeed +0x24 = RATE。
 *  - 客户端：ServerConnection（NetDriver+0x60）和 LocalPlayer（控制器+0x2fc）CurrentNetSpeed +0x24 = RATE，Configured +0x28/+0x2c 也改。
 * 偏移来源：反射表 UNetDriver MaxInternetClientRate 0x38 / MaxClientRate 0x3c / NetServerMaxTickRate 0x34；UPlayer ConfiguredInternetSpeed 0x28 /
 * ConfiguredLanSpeed 0x2c；AGameNetworkManager TotalNetBandwidth 0x2b8 / MinDynamicBandwidth 0x2bc / MaxDynamicBandwidth 0x2c0。
 */
final class NetRate {
    static final int RATE = 100000;   // 字节/秒（约 0.8 Mbps），够车和人物同步，又不会把 FRP 撑爆
    static volatile String state = "联机带宽：还没调";
    static volatile int satLines;      // 联机日志里的 Saturated 行数（Lan.line 计数，不再全塞进联机日志）
    static Thread th;
    static long pc;
    static String lastNote = "";

    static synchronized void start() {
        if (th != null) return;
        th = new Thread(() -> {
            while (true) {
                try { tick(); } catch (Throwable t) { state = "联机带宽：出错 " + t; }
                try { Thread.sleep(2000); } catch (InterruptedException e) { return; }
            }
        }, "zf-netrate");
        th.setDaemon(true); th.start();
    }

    static String cls(RandomAccessFile m, Veh.Names nm, long o) { try { return Alive.clsName(m, nm, o); } catch (Throwable t) { return "?"; } }

    static int raise(RandomAccessFile m, long a, int want) throws java.io.IOException {
        int v = Mods.rd(m, a);
        if (v < want) { Mods.wi(m, a, want); return v; }
        return -1;
    }

    static void tick() throws Exception {
        if (Lan.role == 0 || !Con.inMatch) { pc = 0; return; }
        long b = Mods.base(); if (b == 0) return;
        if (pc == 0) {
            if (!Veh.installed || !Veh.call(0)) { state = "联机带宽：拿不到本机控制器（刷车 cave 没装）"; return; }
            pc = Veh.buf.getInt(4) & 0xffffffffL;
        }
        Veh.Names nm = null; try { nm = Veh.Names.get(); } catch (Throwable t) {}
        StringBuilder sb = new StringBuilder("联机带宽（" + (Lan.role == 1 ? "服务端" : "客户端") + "，目标 " + RATE + " 字节/秒）：");
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "rw")) {
            if (!Mods.isObj(m, b, pc)) { pc = 0; return; }
            long lvl = Mods.rp(m, pc + 0x18), world = Mods.isObj(m, b, lvl) ? Mods.rp(m, lvl + 0x18) : 0;
            if (!Mods.isObj(m, b, world) || !cls(m, nm, world).contains("World")) { state = sb + "找不到 World（" + cls(m, nm, world) + "）"; pc = 0; return; }
            long drv = Mods.rp(m, world + 0x24);
            if (!Mods.isObj(m, b, drv) || !cls(m, nm, drv).contains("NetDriver")) { state = sb + "找不到 NetDriver（" + cls(m, nm, drv) + "）"; return; }
            int o1 = raise(m, drv + 0x3c, RATE), o2 = raise(m, drv + 0x38, RATE);
            sb.append("MaxClientRate ").append(o1 >= 0 ? o1 + "→" + RATE : "ok").append("，MaxInternetClientRate ").append(o2 >= 0 ? o2 + "→" + RATE : "ok");
            if (Lan.role == 1) {
                long gnm = Mods.rp(m, world + 0x310);
                if (Mods.isObj(m, b, gnm) && cls(m, nm, gnm).contains("NetworkManager")) {
                    int t = raise(m, gnm + 0x2b8, RATE * 32), mn = raise(m, gnm + 0x2bc, RATE), mx = raise(m, gnm + 0x2c0, RATE);
                    sb.append("，动态带宽 总 ").append(t >= 0 ? t + "→" + RATE * 32 : "ok").append(" 下限 ").append(mn >= 0 ? mn + "→" + RATE : "ok").append(" 上限 ").append(mx >= 0 ? mx + "→" + RATE : "ok");
                } else sb.append("，GameNetworkManager 没认出（" + cls(m, nm, gnm) + "）");
                long gm = Mods.rp(m, world + 0x94), gs = Mods.isObj(m, b, gm) ? Mods.rp(m, gm + 0x2e0) : 0;
                long data = Mods.isObj(m, b, gs) ? Mods.rp(m, gs + 0x2bc) : 0; int n = Mods.isObj(m, b, gs) ? Mods.rd(m, gs + 0x2c0) : 0;
                int conns = 0, fixed = 0;
                if (data >= 0x10000 && n > 0 && n <= 200) for (int k = 0; k < n; k++) {
                    long ps = Mods.rp(m, data + 4L * k), ctl = ps >= 0x10000 ? Mods.rp(m, ps + 0x60) : 0;
                    if (!Mods.isObj(m, b, ctl)) continue;
                    long con = Mods.rp(m, ctl + 0x2fc);
                    if (!Mods.isObj(m, b, con) || !cls(m, nm, con).contains("NetConnection")) continue;
                    conns++;
                    int was = raise(m, con + 0x24, RATE);
                    if (was >= 0) { fixed++; sb.append("，连接 ").append(Long.toHexString(con)).append(" ").append(was).append("→").append(RATE); }
                }
                sb.append("，加入玩家连接 ").append(conns).append(" 个").append(fixed == 0 && conns > 0 ? "（都已是 " + RATE + "）" : "");
            } else {
                long sc = Mods.rp(m, drv + 0x60);
                if (Mods.isObj(m, b, sc) && cls(m, nm, sc).contains("NetConnection")) {
                    int was = raise(m, sc + 0x24, RATE);
                    sb.append("，到服务端的连接 ").append(was >= 0 ? was + "→" + RATE : "ok");
                } else sb.append("，ServerConnection 没认出（" + cls(m, nm, sc) + "）");
                long lp = Mods.rp(m, pc + 0x2fc);
                if (Mods.isObj(m, b, lp) && cls(m, nm, lp).contains("LocalPlayer")) {
                    int a = raise(m, lp + 0x24, RATE); raise(m, lp + 0x28, RATE); raise(m, lp + 0x2c, RATE);
                    sb.append("，本机玩家 ").append(a >= 0 ? a + "→" + RATE : "ok");
                } else sb.append("，LocalPlayer 没认出（" + cls(m, nm, lp) + "）");
            }
        }
        sb.append("，Saturated 日志 ").append(satLines).append(" 行");
        state = sb.toString();
        String key = state.replaceAll("Saturated 日志 \\d+ 行", "");
        if (state.contains("→") && !key.equals(lastNote)) { lastNote = key; LogCap.note(state); }
    }
}
