package com.zframe.inject;

import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** v3.77 用户：进大厅太快，衣服/人物还没加载完就看到了 → 进大厅时盖一层加载页。
 *  v3.80 用户：「这个等待加载页面不是游戏的，调用游戏的加载页面」→ 不再用我们自己画的那层，改用游戏开机那页 UpdateUI（带进度条）：
 *  RE（UNZStartupFlowUpdate::Begin 0x121244c）：游戏建这页 = CreateWidget 0x25448ac(UpdateUI_C 类, GameInstance, 0, 0)
 *    → 存 +0x34 → vt+0x23c(页, 流程)（只记流程 +0x4e0，我们没有流程，不调）→ AddToViewport 0x253dbe0(页, ZOrder)
 *    → vt+0x278 RegisterDelegates（流程空直接跳过，不调）→ vt+0x244 StartCheck（查磁盘/开始腾讯更新，不调）。
 *  UNZUpdateUIView 虚表 0x4d449c8：+0x240 OnUpdateProcess(页, float 0~1, FString*)，+0x25c ShowUpdateError；UWidget::RemoveFromParent = vt+0x128。
 *  做法：日志「Browse: …Erangle_RoleHall」→ 游戏线程（Server.gcall）建 UpdateUI、AddToViewport(ZOrder 10000)、把 UpdatePanel/进度条设成可见（同 UpdPage.showPanel），
 *  每 0.2 秒 OnUpdateProcess 写「正在加载大厅 N%」；日志看到人物装好（HideLoadingProgress / OnChangeHairColor）后 2.5 秒走到 100%，RemoveFromParent 关掉。最长 15 秒。
 *  UpdateUI_C 类：开机那页（UpdPage.view）的类先记下；拿不到就在对象表里按名字 UpdateUI_C 找。GameInstance 在对象表里按类名找实例。未实测。 */
final class LobbyLoad {
    static volatile long page, startAt, readyAt, cls;
    static volatile boolean busy, lobbyBrowse;
    static volatile String state = "大厅加载页：没出现过";
    static final long CREATE = 0x25448acL, ADD_VP = 0x253dbe0L;

    static void line(String l) {
        // v3.83：以前在「Browse 大厅」时就建，那时旧关卡还没卸，页面跟着 StartupMap 一起被清掉（看不到），
        //       15 秒后再对已经被回收的页面调 RemoveFromParent → 服务端进大厅一会儿就闪退（推断）。改成大厅关卡载好（Game class 是 FrontEnd）再建
        if (l.contains("Browse: /Game/Maps/Erangle_RoleHall")) lobbyBrowse = true;
        else if (lobbyBrowse && l.contains("Game class is '") && l.contains("FrontEnd")) { lobbyBrowse = false; show(); }
        else if (busy && readyAt == 0 && (l.contains("HideLoadingProgress") || l.contains("OnChangeHairColor"))) readyAt = System.currentTimeMillis();
    }
    /** 开机更新页找到时记下它的类（UpdateUI_C） */
    static void rememberClass(long v) {
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) { long c = Server.rd(m, v + 0xc); if (c > 0x10000) cls = c; } catch (Throwable ignored) {}
    }

    static volatile boolean once;
    static void show() {
        if (busy || once) return;   // v3.81：只在开机第一次进大厅（接着开机更新页），对局回大厅不盖
        once = true;
        busy = true; startAt = System.currentTimeMillis(); readyAt = 0;
        new Thread(LobbyLoad::run, "zf-lobbyload").start();
    }

    static void run() {
        long w = 0;
        try {
            long b = Mods.base();
            if (b == 0) { state = "大厅加载页：找不到游戏库"; return; }
            Veh.Names nm = Veh.Names.get();
            long[] f = find(b, nm);   // {类, GameInstance}
            if (f[0] == 0 || f[1] == 0) { state = "大厅加载页：没找到 " + (f[0] == 0 ? "UpdateUI_C 类 " : "") + (f[1] == 0 ? "GameInstance" : ""); return; }
            long[] r = Server.gcall(b + CREATE, f[0], f[1], 0, 0, 3000);
            w = r[1];
            try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
                if (r[0] == 0 || w < 0x10000 || !Mods.isObj(m, b, w)) { state = "大厅加载页：建页面失败（游戏线程 " + r[0] + "，返回 " + Long.toHexString(w) + "）"; w = 0; return; }
            }
            page = w;
            r = Server.gcall(b + ADD_VP, w, 10000, 0, 0, 3000);
            if (r[0] == 0) { state = "大厅加载页：AddToViewport 没跑到"; return; }
            String panel = showPanel(b, w, nm);
            long fn;
            try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) { fn = Server.rd(m, Server.rd(m, w) + 0x240); }
            if (fn < b || fn > b + 0x5000000L) { state = "大厅加载页：OnUpdateProcess 地址不对，" + panel; return; }
            state = "大厅加载页：游戏 UpdateUI 显示中（" + panel + "）";
            LogCap.note(state);
            // v3.80b 用户：不要「正在加载大厅」假进度，就是开机那个更新页本身一直留着，大厅在它后面加载 →
            // 页面和开机时一样：我们的公告 + 更新进度条停在开机最后的状态（满格「已是最新版本」），人物装好就关
            ByteBuffer bb = ByteBuffer.allocateDirect(64 * 1024).order(ByteOrder.LITTLE_ENDIAN);
            long ba = GameOverlay.bufAddr(bb);
            // v3.83 用户：不要我们的公告
            String txt = "已是最新版本（v" + GameOverlay.VER.replace("v", "") + "）";
            bb.clear();
            UpdPage.fstr(bb, ba, 0x8000, txt);
            Server.gcall(fn, w, Float.floatToIntBits(1f), ba + 0x8000, 0, 1500);
            while (true) {
                long now = System.currentTimeMillis(), el = now - startAt;
                if ((readyAt != 0 && now - readyAt > 2500) || el > 15000) { state = "大厅加载页：开机更新页，" + (readyAt == 0 ? "15 秒超时关" : "人物装好后关") + "（" + el + " ms，" + panel + "）"; break; }
                Thread.sleep(200);
            }
            Thread.sleep(300);
        } catch (Throwable t) { state = "大厅加载页：出错 " + t; }
        finally {
            if (w != 0) close(w);
            page = 0; busy = false;
            LogCap.note(state);
        }
    }

    static void close(long w) {
        try {
            long b = Mods.base(), fn;
            try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
                if (!Mods.isObj(m, b, w) || Server.rd(m, w + 0xc) != cls) { state += "；关：页面已不在"; return; }
                fn = Server.rd(m, Server.rd(m, w) + 0x128);
            }
            if (fn < b || fn > b + 0x5000000L) { state += "；关：RemoveFromParent 地址不对"; return; }
            long[] r = Server.gcall(fn, w, 0, 0, 0, 3000);
            if (r[0] == 0) state += "；关：没跑到";
        } catch (Throwable t) { state += "；关出错 " + t; }
    }

    /** 和 UpdPage.showPanel 一样：UpdatePanel 蓝图里默认 Collapsed，调 SetVisibility（vt+0x124）设成 Visible */
    static String showPanel(long b, long v, Veh.Names nm) {
        StringBuilder got = new StringBuilder();
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
            for (int off = 0x200; off <= 0x260; off += 4) {
                long x = Server.rd(m, v + off);
                if (x < 0x10000 || !Mods.isObj(m, b, x)) continue;
                String n = Alive.objName(m, nm, x);
                if (n == null || !(n.equals("UpdatePanel") || n.equals("UpdateProgressBar") || n.equals("UpdateProcessText") || n.equals("UpdateProcessValue"))) continue;
                long fn = Server.rd(m, Server.rd(m, x) + 0x124);
                if (fn < b || fn > b + 0x5000000L) continue;
                long[] r = Server.gcall(fn, x, 0, 0, 0, 1500);
                if (r[0] == 1) got.append(n).append(' ');
            }
        } catch (Throwable t) { got.append("出错 ").append(t); }
        return got.length() == 0 ? "进度条没找到" : "显示了 " + got.toString().trim();
    }

    /** {UpdateUI_C 类, GameInstance 实例} */
    static long[] find(long b, Veh.Names nm) {
        long c = 0, gi = 0;
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
            long k = cls;
            if (k > 0x10000 && Mods.isObj(m, b, k)) { String n = Alive.objName(m, nm, k); if (n != null && n.startsWith("UpdateUI")) c = k; }
            long g = Server.rd(m, b + 0x51250acL), objs = Server.rd(m, g + 0x10); int num = (int) Server.rd(m, g + 0x18);
            if (objs < 0x10000 || num <= 0 || num > 0x200000) return new long[] { c, 0 };
            byte[] raw = new byte[num * 16]; m.seek(objs); m.readFully(raw);
            ByteBuffer bb = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
            java.util.HashMap<Long, String> cn = new java.util.HashMap<>();
            for (int i = 0; i < num && (c == 0 || gi == 0); i++) {
                long o = bb.getInt(i * 16) & 0xffffffffL; int fl = bb.getInt(i * 16 + 4);
                if (o < 0x10000 || (fl & 0x20000000) != 0) continue;
                long oc = Server.rd(m, o + 0xc); if (oc < 0x10000) continue;
                String n = cn.get(oc); if (n == null) { n = Alive.objName(m, nm, oc); if (n == null) n = ""; cn.put(oc, n); }
                if (c == 0 && n.contains("GeneratedClass")) { String on = Alive.objName(m, nm, o); if ("UpdateUI_C".equals(on)) c = o; }
                else if (gi == 0 && (n.endsWith("GameInstance") || n.endsWith("GameInstance_C")) && (Server.rd(m, o + 4) & 0x10) == 0) {
                    String on = Alive.objName(m, nm, o);
                    if (on != null && !on.startsWith("Default__")) gi = o;
                }
            }
        } catch (Throwable t) { state = "大厅加载页：找对象出错 " + t; }
        if (c != 0) cls = c;
        return new long[] { c, gi };
    }
}
