package com.zframe.inject;

import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** v3.42 手雷抛物线：只诊断，不改（哪一项挡住还没确定）。
 *  RE（全部来自 libUE4 静态反汇编，未实测）：
 *  - 画线本体 0xee9fe4（DrawProjectileTrajectory 的实现；蓝图入口 thunk 0xf16e4c），里面没有联机判断。
 *  - 调它的是投掷武器的组件（虚表 0x4c524d0，属性 ProjectileTrajectory +0xa8，武器 +0x1c）：
 *    · 每帧 0xefa98c（虚表 +0x148）：武器有效 → 拥有者人物 = 武器 vt+0x430 → 人物 vt+0x34c 为真 → 人物 WeaponManager(+0x888)+0x2d8 == 这把武器
 *      → 还没画（组件+0x21==0）且数量（武器+0x2b4）≥1 → 调 0xef9454 开始画；之后人物 vt+0x918 / vt+0x4c4 / vt+0x4ac 都为 0 才继续更新，否则收起。
 *    · 0xef9454 开始画：数量 ≥1、人物 vt+0x34c==1、vt+0x350==1、当前武器一致；设置对象（0xc9e8b8）的 ThrowWeaponTrace(+0xd5，配置项)==0 时
 *      还要求 [武器+0x3e8]+0x24 的 +0x20 是 5 或 6；最后要有 World。
 *    · 虚表 +0x120 0xef9354：ThrowWeaponTrace==1 时调 0xef9454；+0x128 0xef9994：ThrowWeaponTrace==0 时调。
 *  这里把这些检查逐项读出来（虚函数在游戏线程调一次，都是只读的取值函数），写进诊断。 */
final class Parabola {
    static final long VT_COMP = 0x4c524d0L, SLOT120 = 0xef9354L, SETTINGS = 0xc9e8b8L;
    static volatile String state = "抛物线检查：没跑（对局里手上拿着手雷，再点「诊断 → 手雷抛物线检查」）";

    static String check() {
        StringBuilder sb = new StringBuilder("抛物线检查（手里拿雷时）：");
        try {
            long b = Mods.base(); if (b == 0) return state = sb.append("找不到游戏库").toString();
            long pawn = Mods.myPawn(); if (pawn < 0x10000) return state = sb.append("找不到自己的人物").toString();
            Veh.Names nm = Veh.Names.get();
            long wm, cur, cnt, ctl; String curName;
            try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
                wm = Server.rd(m, pawn + 0x888); cur = wm < 0x10000 ? 0 : Server.rd(m, wm + 0x2d8);
                curName = Alive.clsName(m, nm, cur); cnt = cur < 0x10000 ? -1 : (int) Server.rd(m, cur + 0x2b4); ctl = Server.rd(m, pawn + 0x2d0);
            }
            sb.append("人物 ").append(Long.toHexString(pawn)).append(" 控制器+2d0 ").append(Long.toHexString(ctl)).append("；WeaponManager+888 ").append(Long.toHexString(wm))
              .append(" 当前武器+2d8 ").append(Long.toHexString(cur)).append(' ').append(curName).append(" 数量+2b4 ").append(cnt).append("；");
            if (cur < 0x10000) return state = sb.append("手上没武器 → 抛物线组件不会画").toString();
            // 找这把武器的抛物线组件：虚表 0x4c524d0 或 +0x120 槽是 0xef9354（子类），且 +0x1c == 当前武器
            long comp = 0; int all = 0;
            try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
                long g = Server.rd(m, b + 0x51250acL);
                long objs = Server.rd(m, g + 0x10), num = Server.rd(m, g + 0x18);
                if (objs >= 0x10000 && num > 0 && num <= 0x200000) {
                    byte[] raw = new byte[(int) num * 16]; m.seek(objs); m.readFully(raw);
                    ByteBuffer bb = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
                    java.util.HashMap<Long, Boolean> vtOk = new java.util.HashMap<>();
                    for (int i = 0; i < num; i++) {
                        long o = bb.getInt(i * 16) & 0xffffffffL;
                        if (o < 0x10000) continue;
                        long vt = Server.rd(m, o); if (vt < 0x10000) continue;
                        Boolean ok = vtOk.get(vt);
                        if (ok == null) { ok = vt == b + VT_COMP || Server.rd(m, vt + 0x120) == b + SLOT120; vtOk.put(vt, ok); }
                        if (!ok || (Server.rd(m, o + 4) & 0x10) != 0) continue;
                        all++;
                        if (Server.rd(m, o + 0x1c) == cur) comp = o;
                    }
                }
            }
            sb.append("抛物线组件共 ").append(all).append(" 个，");
            if (comp == 0) return state = sb.append("这把武器没有抛物线组件（不是投掷武器，或组件没建）").toString();
            long traj, set; int drawn, b22, bcc, bcd, bcf, mode = -1;
            long[] st = Server.gcall(b + SETTINGS, comp, 0, 0, 0, 3000);
            set = st[1];
            try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
                sb.append("组件 ").append(Long.toHexString(comp)).append(' ').append(Alive.clsName(m, nm, comp)).append("；");
                drawn = Island.rb(m, comp + 0x21); b22 = Island.rb(m, comp + 0x22); bcc = Island.rb(m, comp + 0xcc); bcd = Island.rb(m, comp + 0xcd); bcf = Island.rb(m, comp + 0xcf);
                traj = Server.rd(m, comp + 0xa8);
                long fm = Server.rd(m, cur + 0x3e8), fs = fm < 0x10000 ? 0 : Server.rd(m, fm + 0x24);
                if (fs >= 0x10000) mode = Island.rb(m, fs + 0x20);
                sb.append("已画+21=").append(drawn).append(" +22=").append(b22).append(" +cc=").append(bcc).append(" +cd=").append(bcd).append(" +cf=").append(bcf)
                  .append(" 抛物线物体+a8 ").append(traj < 0x10000 ? "无" : Long.toHexString(traj) + " " + Alive.clsName(m, nm, traj)).append("；");
                sb.append("设置对象 0xc9e8b8 ").append(st[0] == 0 ? "没跑" : set < 0x10000 ? "空" : Long.toHexString(set) + " " + Alive.clsName(m, nm, set) + " ThrowWeaponTrace+d5=" + Island.rb(m, set + 0xd5)).append("；");
                sb.append("投掷模式 [武器+3e8]+24+20 = ").append(mode).append("（ThrowWeaponTrace=0 时要 5 或 6）；");
            }
            long[] op = vcall(cur, 0x430);
            sb.append("武器 vt+430 拥有者 ").append(op[0] == 0 ? "没跑" : Long.toHexString(op[1])).append(op[1] == pawn ? "(=我)" : "(≠我)").append("；");
            long who = op[1] >= 0x10000 ? op[1] : pawn;
            for (int off : new int[] { 0x34c, 0x350, 0x918, 0x4c4, 0x4ac }) {
                long[] r = vcall(who, off);
                sb.append("人物 vt+").append(Integer.toHexString(off)).append('=').append(r[0] == 0 ? "没跑" : String.valueOf(r[1] & 0xff)).append(' ');
            }
            sb.append("（要求 34c=1、350=1；918/4c4/4ac 要都是 0 才持续更新）");
        } catch (Throwable t) { sb.append("出错 ").append(t); }
        state = sb.toString(); LogCap.note(state);
        return state;
    }
    /** 在游戏线程调对象虚表 off 处的函数（只读取值函数） */
    static long[] vcall(long o, int off) throws Exception {
        long fn;
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) { long vt = Server.rd(m, o); fn = vt < 0x10000 ? 0 : Server.rd(m, vt + off); }
        if (fn < 0x10000) return new long[] { 0, 0 };
        return Server.gcall(fn, o, 0, 0, 0, 3000);
    }
}
