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
            if (traj >= 0x10000) sb.append(trajDiag(b, nm, traj));
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
    /** v3.46：画线前检查都过了（已画+21=1、有抛物线物体），卡在光束显示。画线 0xee9fe4 在抛物线物体上：
     *  段数 = round(+0x2cc / +0x2d0)，<1 一段都不画；每段 0xeea4f0 用模板 +0x2d4 建组件存进 +0x2d8/+0x2dc，
     *  SetVisibility(1) 0x292041c、SetHiddenInGame(0) 0x2b6f01c、vt+0x420 SetStartAndEnd；终点组件 +0x2e4。
     *  这里把这些全部读出来，并列出每段组件里指向 StaticMesh / 材质 / 粒子的指针，对比单机和联机哪里不一样。只读。 */
    static String trajDiag(long b, Veh.Names nm, long t) {
        StringBuilder sb = new StringBuilder("光束：");
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
            float len = Mods.f(m, t + 0x2cc), step = Mods.f(m, t + 0x2d0);
            sb.append("长度+2cc=").append(len).append(" 每段+2d0=").append(step).append(" → 段数 ").append(step == 0 ? "除 0" : String.valueOf(Math.round(len / step)));
            sb.append("；输入+2b0..2c8=");
            for (int o = 0x2b0; o <= 0x2c8; o += 4) sb.append(String.format("%.0f ", Mods.f(m, t + o)));
            sb.append("；物体标志+58..5f=").append(Server.bytesAt(m, t + 0x58, 8));
            long tpl = Server.rd(m, t + 0x2d4);
            sb.append("；段模板+2d4 ").append(tpl < 0x10000 ? "空" : Alive.clsName(m, nm, tpl) + " " + Alive.objName(m, nm, tpl));
            long arr = Server.rd(m, t + 0x2d8); int n = (int) Server.rd(m, t + 0x2dc), mx = (int) Server.rd(m, t + 0x2e0);
            sb.append("；段组件 ").append(n).append('/').append(mx);
            for (int i = 0; i < n && i < 3 && arr >= 0x10000; i++) {
                long c = Server.rd(m, arr + 4L * i);
                sb.append("；段").append(i).append(' ').append(Long.toHexString(c)).append(' ').append(Alive.clsName(m, nm, c)).append(ptrs(m, b, nm, c));
            }
            long end = Server.rd(m, t + 0x2e4);
            sb.append("；终点+2e4 ").append(end < 0x10000 ? "空" : Long.toHexString(end) + " " + Alive.clsName(m, nm, end) + ptrs(m, b, nm, end));
        } catch (Throwable e) { sb.append("出错 ").append(e); }
        return sb.append("；").toString();
    }
    /** 组件里 +0x80..+0x700 指向 StaticMesh/材质/粒子/纹理的指针 → 「+偏移=类:名字」，再带 +0x80..+0x100 的原始字节（看可见性位） */
    static String ptrs(RandomAccessFile m, long b, Veh.Names nm, long c) throws java.io.IOException {
        if (c < 0x10000 || !Mods.isObj(m, b, c)) return "（不是对象）";
        StringBuilder sb = new StringBuilder("【");
        int k = 0;
        for (int o = 0x80; o < 0x700 && k < 8; o += 4) {
            long p = Server.rd(m, c + o); if (p < 0x10000 || p == c) continue;
            if (!Mods.isObj(m, b, p)) continue;
            String cn = Alive.clsName(m, nm, p);
            if (cn.contains("StaticMesh") && !cn.contains("Component") || cn.contains("Material") || cn.contains("Particle") && !cn.contains("Component") || cn.contains("Texture")) {
                sb.append('+').append(Integer.toHexString(o)).append('=').append(cn).append(':').append(Alive.objName(m, nm, p)).append(' '); k++;
            }
        }
        if (k == 0) sb.append("没有网格/材质指针 ");
        return sb.append("字节+80..ff ").append(Server.bytesAt(m, c + 0x80, 0x80)).append("】").toString();
    }

    /** v3.46：自动跑（单机/服务端/客户端都跑，联机正常、单机不正常要对比）：对局里手上切到投掷物 1.5 秒后检查一次并上传 para-，每局一次 */
    static volatile boolean watching;
    /** v3.65 抛物线不跟视角（用户多次提，联机客户端正常、单机/房主不正常）。根因（静态读码）：每帧更新 0xefa98c 取瞄准方向用 0xefae5c：
     *  武器 Role(+0xe8) ≤ 2（联机客户端）→ 取自己控制器的视角（0x2c551a4）；Role = 3（单机/房主，自己就是主机）→ 读武器 +0x1b48 那份
     *  「客户端上报给服务器的方向」，单机没人上报，一直是拿雷那一刻的值 → 抛物线定住。
     *  补丁：0xefaea8 的 bhi（Role>2 跳去读 +0x1b48）改成 nop，主机也走控制器视角。只改抛物线取方向这一处（原字节核对）。 */
    static volatile String aim = "抛物线跟视角：没装";
    static void aimFix() {
        if (Lan.role == 2) { aim = "抛物线跟视角：联机客户端本来就跟视角，不用改"; return; }
        String r = Mods.write(0xefaea8L, "3900008a", "00f020e3", true);
        aim = "抛物线跟视角（0xefaea8 主机也用控制器视角）：" + r;
        LogCap.note(aim);
    }
    static void matchStart() {
        aimFix();
        if (watching) return; watching = true;
        new Thread(() -> {
            try {
                long t0 = System.currentTimeMillis();
                while (System.currentTimeMillis() - t0 < 1800000) {
                    Thread.sleep(2000);
                    if (!Con.inMatch) { if (System.currentTimeMillis() - t0 > 60000) break; continue; }
                    long pawn = Mods.myPawn(); if (pawn < 0x10000) continue;
                    String cn;
                    try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
                        long wm = Server.rd(m, pawn + 0x888), cur = wm < 0x10000 ? 0 : Server.rd(m, wm + 0x2d8);
                        cn = cur < 0x10000 ? "" : Alive.clsName(m, Veh.Names.get(), cur);
                    }
                    if (!cn.contains("Grenade") && !cn.contains("Throw") && !cn.contains("Molotov") && !cn.contains("Smoke") && !cn.contains("Flash")) continue;
                    Thread.sleep(1500);
                    check();
                    android.app.Activity a = Con.act;
                    if (a != null) { AvatarLog.manual = true; try { AvatarLog.export(a, "para-"); } finally { AvatarLog.manual = false; } }
                    break;
                }
            } catch (Throwable ignored) {}
            finally { watching = false; }
        }, "zf-para").start();
    }

    /** 在游戏线程调对象虚表 off 处的函数（只读取值函数） */
    static long[] vcall(long o, int off) throws Exception {
        long fn;
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) { long vt = Server.rd(m, o); fn = vt < 0x10000 ? 0 : Server.rd(m, vt + off); }
        if (fn < 0x10000) return new long[] { 0, 0 };
        return Server.gcall(fn, o, 0, 0, 0, 3000);
    }
}
