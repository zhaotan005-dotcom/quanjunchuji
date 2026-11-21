package com.zframe.inject;

import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** v3.25 用户：「把它改成游戏内的那个更新的进度条」（v3.24 只是我们自己的弹窗盖在上面）。
 *  v3.24 去掉了启动流程关更新步骤那句，UNZStartupFlowUpdate::Begin 会 CreateWidget(UpdateUI_C) 并 AddToViewport。
 *  这里找到这页的实例后，在游戏线程（Server.gcall，FEngineLoop::Tick 调用槽）里：
 *   - 公告：把我们的公告临时放进 UNZUpdateInfo(0xfb1be8 返回)+0x3a8 公告数组（每条 0x34：+0 是否图片、+0x10 正文、+0x1c 标题），
 *     调页面自己的显示函数 0x15de914(页面, 0)，再把数组还原（游戏不会释放我们的内存）；
 *   - 进度条：每 0.3 秒调页面虚表 +0x240 OnUpdateProcess(页面, float 进度 0~1, const FString& 文字)，进度 = 我们从仓库下内核的进度。
 *  找不到这页（这包没有/没建出来）→ 照旧用我们自己的那层（GameOverlay.srvUpdatePage）。未实测。 */
final class UpdPage {
    static volatile long view;
    static volatile boolean finished, started, panelOk;
    static volatile String state = "游戏更新页：没开始";
    static final long INFO_GET = 0xfb1be8L, SHOW_NOTICE = 0x15de914L;

    /** v3.57 用户：开机一直没有 TiMi 那页（LogoUI，UNZStartupFlowTimi）。先只看：游戏日志里跑了哪些启动步骤、
     *  启动 GameMode +0x33c StartupFlowTypes（反射表 0x4d8b0f4）里有哪几步。只读。 */
    static final StringBuilder flowSb = new StringBuilder();
    static volatile String flows = "启动步骤：没看到";
    static volatile boolean logoDone;
    static void flowLine(String l) {
        if (l.contains("End Flow NZStartupFlowLogo")) logoDone = true;
        synchronized (flowSb) { if (flowSb.length() < 2500) flowSb.append("\n  ").append(l.trim().length() > 180 ? l.trim().substring(0, 180) : l.trim()); flows = "启动步骤日志：" + flowSb; }
    }
    static volatile String flowTypes = "StartupFlowTypes：没查";
    static void flowTypes(long b, Veh.Names nm) {
        StringBuilder sb = new StringBuilder("StartupFlowTypes（启动 GameMode +0x33c）：");
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
            long g = Server.rd(m, b + 0x51250acL), objs = Server.rd(m, g + 0x10); int num = (int) Server.rd(m, g + 0x18);
            if (objs < 0x10000 || num <= 0 || num > 0x200000) { flowTypes = sb.append("对象表不对").toString(); return; }
            byte[] raw = new byte[num * 16]; m.seek(objs); m.readFully(raw);
            ByteBuffer bb = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
            java.util.HashMap<Long, String> cn = new java.util.HashMap<>(); int found = 0; StringBuilder flowObjs = new StringBuilder();
            for (int i = 0; i < num; i++) {
                long o = bb.getInt(i * 16) & 0xffffffffL; if (o < 0x10000) continue;
                long c = Server.rd(m, o + 0xc); if (c < 0x10000) continue;
                String n = cn.get(c); if (n == null) { n = Alive.objName(m, nm, c); cn.put(c, n == null ? "" : n); }
                if (n == null) continue;
                boolean cdo = (Server.rd(m, o + 4) & 0x10) != 0;
                if (n.contains("StartupGameMode") && !cdo && found < 3) {
                    found++;
                    long d = Server.rd(m, o + 0x33c); int k = (int) Server.rd(m, o + 0x340);
                    sb.append(n).append(" 共 ").append(k).append(" 步：");
                    for (int j = 0; j < k && j < 16 && d >= 0x10000; j++) { long t = Server.rd(m, d + 4L * j); sb.append(t < 0x10000 ? "空" : Alive.objName(m, nm, t)).append(' '); }
                    sb.append("；");
                }
                if ((n.startsWith("NZStartupFlow") || n.startsWith("LogoUI")) && flowObjs.length() < 400) flowObjs.append(n).append(cdo ? "(默认)" : "(实例)").append(' ');
            }
            if (found == 0) sb.append("没找到启动 GameMode 实例；");
            sb.append("启动步骤对象：").append(flowObjs.length() == 0 ? "无" : flowObjs.toString());
        } catch (Throwable t) { sb.append("出错 ").append(t); }
        flowTypes = sb.toString();
    }

    static void start() {
        if (started) return;
        started = true;
        new Thread(UpdPage::run, "zf-updpage").start();
    }
    /** FString（UTF-32，含结尾 0）写到 bb 的 off 处：头 12 字节 {data,num,max}，字符接在 off+12 */
    static int fstr(ByteBuffer bb, long base, int off, String s) {
        int[] cps = s.codePoints().toArray();
        int n = cps.length + 1, data = off + 12;
        bb.putInt(off, (int) (base + data)); bb.putInt(off + 4, n); bb.putInt(off + 8, n);
        for (int i = 0; i < cps.length; i++) bb.putInt(data + i * 4, cps[i]);
        bb.putInt(data + cps.length * 4, 0);
        return data + n * 4;
    }
    static void run() {
        long t0 = System.currentTimeMillis();
        try {
            long b = 0;
            for (int i = 0; i < 80 && (b = Mods.base()) == 0; i++) Thread.sleep(250);
            if (b == 0 || !UpdFlow.on) { state = "游戏更新页：没开（" + UpdFlow.state + "）"; finished = true; return; }
            Veh.Names nm = Veh.Names.get();
            // v3.82：去掉开机扫整张对象表的启动步骤诊断（慢，拖慢找更新页）
            long v = 0;
            // 启动流程先问腾讯（约 12 秒失败），之后才进更新步骤建页面 → 最多等 25 秒
            state = "游戏更新页：开始找页面";
            while (v == 0 && System.currentTimeMillis() - t0 < 25000) {
                v = find(b, nm);
                // v3.38 用户截图一直「没开始」→ 找的过程写出来：用了几秒、对象数、带 Update 的实例类名
                state = "游戏更新页：找页面 " + (System.currentTimeMillis() - t0) / 1000 + " 秒，对象 " + lastNum + "，Update 类：" + lastCls;
                if (v == 0) Thread.sleep(400);
            }
            if (v == 0) { state = "游戏更新页：25 秒内没找到（对象 " + lastNum + "，Update 类：" + lastCls + "），用我们自己的那层"; LogCap.note(state); finished = true; GameOverlay.srvFallback(); return; }
            view = v; LobbyLoad.rememberClass(v);
            UpdFlow.lobby();   // v3.30：页面建出来了 = 没卡住，删保险文件
            state = "游戏更新页：找到 " + Long.toHexString(v);
            LogCap.note(state);
            ByteBuffer bb = ByteBuffer.allocateDirect(64 * 1024).order(ByteOrder.LITTLE_ENDIAN);
            long ba = GameOverlay.bufAddr(bb);
            // v3.82 用户：不要我们的公告，游戏更新页不再塞我们的公告
            showPanel(b, v, nm);   // v3.34：UpdatePanel 蓝图里默认 Collapsed，游戏只在腾讯 Dolphin 开始时才显示 → 我们自己设成可见
            // 进度条
            long fn;
            try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) { fn = Server.rd(m, Server.rd(m, v) + 0x240); }
            if (fn < b || fn > b + 0x5000000L) { state += "；OnUpdateProcess 地址不对 " + Long.toHexString(fn); finished = true; return; }
            long doneAt = 0, end = Long.MAX_VALUE;   // v3.29：等下载走完，不设上限（失败 NetCore 自动重试）
            while (System.currentTimeMillis() < end) {
                boolean ready = NetCore.readyVer > ZInit.CORE_VER;
                int p = NetCore.pct;
                float f = ready ? 1f : NetCore.done ? 1f : p >= 0 ? p / 100f : 0.05f;
                String s = ready ? "更新已下好，马上重启" : NetCore.done ? "已是最新版本（v" + GameOverlay.VER.replace("v", "") + "）"
                        : p >= 0 ? "正在下载更新 " + p + "%" : (NetCore.status.isEmpty() ? "正在检查更新…" : NetCore.status);
                int so = 0x8000;
                bb.clear();
                fstr(bb, ba, so, s);
                long[] r = Server.gcall(fn, v, Float.floatToIntBits(f), ba + so, 0, 1500);
                if (r[0] == 0) { state += "；游戏线程没跑到调用"; GameOverlay.toastAny("更新页进度条失败：游戏线程没跑到调用"); break; }
                if ((ready || NetCore.done) && doneAt == 0) doneAt = System.currentTimeMillis();
                if (doneAt != 0 && System.currentTimeMillis() - doneAt > (ready ? 1500 : 3000)) break;
                Thread.sleep(300);
            }
            if (NetCore.readyVer > ZInit.CORE_VER && Con.act != null) NetCore.restart(Con.act);
            state += "；进度条已接上";
        } catch (Throwable t) { state = "游戏更新页：出错 " + t; LogCap.note(state); GameOverlay.srvFallback(); }
        finally { finished = true; }
    }
    /** 找类名带 UpdateUI 的实例（不是默认对象） */
    static volatile long lastNum; static volatile String lastCls = "";
    static long find(long b, Veh.Names nm) {
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
            long g = Server.rd(m, b + 0x51250acL);
            long objs = Server.rd(m, g + 0x10), num = Server.rd(m, g + 0x18);
            lastNum = num;
            if (objs < 0x10000 || num <= 0 || num > 0x200000) { lastCls = "对象表不对"; return 0; }
            java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
            byte[] raw = new byte[(int) num * 16];
            m.seek(objs); m.readFully(raw);
            ByteBuffer bb = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
            java.util.HashMap<Long, Boolean> hit = new java.util.HashMap<>();
            for (int i = 0; i < num; i++) {
                long o = bb.getInt(i * 16) & 0xffffffffL; int fl = bb.getInt(i * 16 + 4);
                if (o < 0x10000 || (fl & 0x20000000) != 0) continue;
                long c = Server.rd(m, o + 0xc);
                if (c < 0x10000) continue;
                Boolean h = hit.get(c);
                if (h == null) { String n = Alive.objName(m, nm, c); h = n != null && n.startsWith("UpdateUI"); hit.put(c, h);
                    if (n != null && n.contains("Update") && seen.size() < 8) seen.add(n); }
                if (!h) continue;
                if ((Server.rd(m, o + 4) & 0x30) != 0) continue;   // 类默认对象 / v3.83：原型对象（RF_ArchetypeObject 0x20）
                String on = Alive.objName(m, nm, o);
                // v3.83：以前找到的是蓝图包里的 WidgetArchetype（模板，不在屏幕上）→ 进度条全写在模板上，用户看不到。只认真正建出来的 UpdateUI_C_N
                if (on == null || on.startsWith("Default__") || on.equals("WidgetArchetype") || !on.startsWith("UpdateUI_C")) continue;
                lastCls = String.join(",", seen);
                return o;
            }
            lastCls = seen.isEmpty() ? "无（类名 " + hit.size() + " 种）" : String.join(",", seen);
        } catch (Throwable t) { lastCls = "出错 " + t; }
        return 0;
    }
    static void notice(long b, long v, ByteBuffer bb, long ba) {
        try {
            long[] r = Server.gcall(b + INFO_GET, 0, 0, 0, 0, 1500);
            long info = r[1];
            try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "rw")) {
                if (r[0] == 0 || info < 0x10000 || !Mods.isObj(m, b, info)) { state += "；公告：拿不到公告对象"; return; }
                // 显示函数要用到的几个控件都在才调（空的会崩）
                for (int off : new int[] { 0x224, 0x228, 0x238, 0x240 }) if (Server.rd(m, v + off) < 0x10000) { state += "；公告：控件 +" + Integer.toHexString(off) + " 是空的，不显示"; return; }
                if ((b8(m, v + 0x4e8)) != 0) { state += "；公告：页面忙"; return; }
                String txt = GameOverlay.remoteNotice != null ? GameOverlay.remoteNotice : GameOverlay.NOTICE;
                bb.clear();
                for (int i = 0; i < 0x40; i++) bb.put(i, (byte) 0);   // 一条 0x34：+0 是否图片=0，+4 空串，+0x28 链接空
                int c0 = 0x40, e1 = fstr(bb, ba, c0, txt);
                for (int k = 0; k < 12; k += 4) bb.putInt(0x10 + k, bb.getInt(c0 + k));   // +0x10 正文
                int t0 = (e1 + 3) & ~3; fstr(bb, ba, t0, "公告");
                for (int k = 0; k < 12; k += 4) bb.putInt(0x1c + k, bb.getInt(t0 + k));   // +0x1c 标题
                long arr = info + 0x3a8;
                long od = Server.rd(m, arr), on = Server.rd(m, arr + 4), om = Server.rd(m, arr + 8);
                Mods.wi(m, arr, (int) ba); Mods.wi(m, arr + 8, 1); Mods.wi(m, arr + 4, 1);
                long[] q;
                try { q = Server.gcall(b + SHOW_NOTICE, v, 0, 0, 0, 1500); }
                finally { Mods.wi(m, arr + 4, (int) on); Mods.wi(m, arr, (int) od); Mods.wi(m, arr + 8, (int) om); }
                state += q[0] == 1 ? "；公告已换成我们的" : "；公告：游戏线程没跑到";
            }
        } catch (Throwable t) { state += "；公告出错 " + t; }
    }
    /** v3.34 用户截图（v3.33）：只有更新页背景，没有进度条 → UpdateUI 蓝图里 UpdatePanel 是 Collapsed。
     *  控件属性在页面 +0x200~+0x260（UpdateProgressBar +0x208 …），按对象名找 UpdatePanel / UpdateProgressBar / UpdateProcessText / UpdateProcessValue，
     *  调 UWidget::SetVisibility（原生 thunk 0x25a6764 → 虚表 +0x124(控件, ESlateVisibility)），0 = Visible。 */
    static void showPanel(long b, long v, Veh.Names nm) {
        StringBuilder got = new StringBuilder();
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
            for (int off = 0x200; off <= 0x260; off += 4) {
                long w = Server.rd(m, v + off);
                if (w < 0x10000 || !Mods.isObj(m, b, w)) continue;
                String n = Alive.objName(m, nm, w);
                if (n == null || !(n.equals("UpdatePanel") || n.equals("UpdateProgressBar") || n.equals("UpdateProcessText") || n.equals("UpdateProcessValue"))) continue;
                long fn = Server.rd(m, Server.rd(m, w) + 0x124);
                if (fn < b || fn > b + 0x5000000L) { got.append(n).append(" 函数不对 "); continue; }
                long[] r = Server.gcall(fn, w, 0, 0, 0, 1500);
                got.append(n).append(r[0] == 1 ? " 显示 " : " 没跑到 ");
            }
        } catch (Throwable t) { got.append("出错 ").append(t); }
        String g = got.length() == 0 ? "没找到 UpdatePanel" : got.toString().trim();
        state += "；面板：" + g;
        LogCap.note(state);
        if (!g.contains("UpdatePanel 显示")) GameOverlay.toastAny("更新页进度条失败：" + g);
        else { panelOk = true; GameOverlay.closeUpdAny(); }
    }
    static int b8(RandomAccessFile m, long a) { try { byte[] x = new byte[1]; m.seek(a); m.readFully(x); return x[0] & 0xff; } catch (Throwable e) { return -1; } }
}
