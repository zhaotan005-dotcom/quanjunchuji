package com.zframe.inject;

import java.io.RandomAccessFile;

/**
 * v2.50 道具资源（用户：战术模式道具都能刷进背包，丢地上看不见；喷气背包能用但没特效）。
 *
 * RE（libUE4）：
 *  - 地上物品 ANZPickupInventory 的模型是异步加载的，回调 0xce5fc0：路径解析出 UStaticMesh 才 SetStaticMesh(+0x2c4 组件)，
 *    否则只打一行 "asynchronous load mesh failed [路径]" → 地上什么都没有（模型文件不在手机上 / 表里没配）。
 *  - 喷气背包 UNZBRPlayerPropAnimComponent::InitJetpackBag 0x110c424：特效路径(+0x170)用 TryLoad(0x1be0c88) 同步加载，
 *    加载出来是 UParticleSystem 才挂 JetpackEffectPSC(+0x1d8)。游戏自己在加载，没特效 = 特效文件在手机上没有。
 *
 * 做法：
 *  1) 地上模型替身：0xce6068（成功分支）→ cave1：记下本局第一个成功加载的地上模型并 AddToRoot（不会被回收）；
 *     0xce62a0（失败分支）→ cave2：有替身就当成功继续（SetStaticMesh 替身），没有就照原样走。只影响「本来就加载失败」的东西。
 *     pick_cave.py 生成、emu_pick.py（unicorn）测过。
 *  2) 缺资源记录：游戏日志里加载失败的路径记下来，「全部道具」页直接显示（单机没有导出，看屏幕就行）。
 */
public class Prop {
    static final long H1 = 0xce6068L, H2 = 0xce62a0L, CAVE = Server.SLACK + 0x300, C2 = CAVE + 0x44;
    static final String H1_O = "c47295e5000057e3", H2_O = "18039fe500008fe0";
    static final String CODE = "07002de95c009fe5001090e5000051e30700001a006080e54c109fe5101091e5082096e5021281e0042091e5012182e3042081e50700bde8c47295e5000057e328f09fe501002de918009fe5000090e5000050e30060a0110100bde810f09f1510009fe510f09fe5";
    static java.nio.ByteBuffer slot; static long slotAddr;
    static volatile boolean on; static volatile String state = "";

    static synchronized String install() {
        if (on) return "ok";
        long b = Mods.base(); if (b == 0) return state = "找不到游戏库";
        if (slot == null) { slot = java.nio.ByteBuffer.allocateDirect(16).order(java.nio.ByteOrder.LITTLE_ENDIAN); slotAddr = GameOverlay.bufAddr(slot); }
        if (slotAddr <= 0 || slotAddr > 0xffffffffL) return state = "内存地址不对";
        String body = CODE + Server.w32((int) slotAddr) + Server.w32((int) (b + 0x529b660L)) + Server.w32((int) (b + 0xce6070L))
            + Server.w32((int) (b + H1)) + Server.w32((int) (b + 0x4a2eab0L)) + Server.w32((int) (b + 0xce62a8L));
        byte[] want = Patches.hex(body);
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "rw")) {
            byte[] cur = new byte[want.length]; m.seek(b + CAVE); m.readFully(cur);
            boolean zero = true; for (byte x : cur) if (x != 0) { zero = false; break; }
            int cl = CODE.length() / 2;
            boolean ours = java.util.Arrays.equals(java.util.Arrays.copyOf(cur, cl), java.util.Arrays.copyOf(want, cl));
            if (!zero && !ours) return state = "空位被占 " + Server.bytesAt(m, b + CAVE, 8);   // v2.54 带上字节好查
            if (!java.util.Arrays.equals(cur, want)) { m.seek(b + CAVE); m.write(want); }
        } catch (Throwable e) { return state = "出错 " + e.getMessage(); }
        String r1 = Mods.write(H1, H1_O, "04f01fe5" + Server.w32((int) (b + CAVE)), true);
        if (!"ok".equals(r1)) return state = "钩子1 " + r1;
        String r2 = Mods.write(H2, H2_O, "04f01fe5" + Server.w32((int) (b + C2)), true);
        if (!"ok".equals(r2)) return state = "钩子2 " + r2;
        on = true; state = "已装";
        return "ok";
    }
    /** 进对局图时调（不在启动画面里写内存） */
    static void matchLoad() {
        String r = install();
        LogCap.note("地上模型替身 → " + r);
        LogCap.note(finInstall());   // v2.98 放下原生路径修复（FinishUsingItemCastTime）
        scanPropsBar();
    }
    static boolean fallbackUsed() { return slot != null && slot.getInt(0) != 0; }

    // ---------------- 缺资源记录 ----------------
    static final java.util.LinkedHashMap<String, Integer> miss = new java.util.LinkedHashMap<>();
    static volatile long missAt; static volatile String lastMiss = "", lastUseLog = "";
    static final java.util.regex.Pattern PATH = java.util.regex.Pattern.compile("(/Game/[^\\s'\"\\]\\)\\[,]+)");
    static void line(String l) {
        gameLine(l);
        String kind = null;
        if (l.contains("SpawnScenePropItem") || l.contains("CanUseThisItem")) { LogCap.note("用道具日志: " + l); lastUseLog = l; }
        if (l.contains("asynchronous load mesh failed")) kind = "地上模型";
        else if (l.contains("Unable to load static mesh")) kind = "手持模型";
        else if (l.contains("failed to load mesh resource") || l.contains("load fail")) kind = "模型";
        else if (l.contains("Failed to find object") || l.contains("Couldn't find file") || l.contains("Can't find file")
            || l.contains("Failed to load") || l.contains("Failed to read file")) kind = "资源";
        if (kind == null) return;
        java.util.regex.Matcher m = PATH.matcher(l);
        String p;
        if (m.find()) p = m.group(1);
        else if (kind.equals("资源")) return;     // 没有 /Game/ 路径的通用失败行不记（太杂）
        else { int a = l.lastIndexOf('['), e = l.lastIndexOf(']'); p = a >= 0 && e > a ? l.substring(a + 1, e) : "（路径空）"; if (p.isEmpty()) p = "（路径空）"; }
        String key = kind + " " + p;
        boolean neu;
        synchronized (miss) {
            Integer c = miss.remove(key); neu = c == null;
            miss.put(key, c == null ? 1 : c + 1);
            while (miss.size() > 40) miss.remove(miss.keySet().iterator().next());
        }
        missAt = System.currentTimeMillis(); lastMiss = kind + " " + shortPath(p);
        if (neu) LogCap.note("缺资源: " + key);
    }
    static String shortPath(String p) {
        if (!p.startsWith("/Game/")) return p;
        String s = p.substring(6); int d = s.lastIndexOf('.'); if (d > 0) s = s.substring(0, d);
        return s;
    }
    /** 最近 n 条，新的在前 */
    static String recent(int n) {
        java.util.ArrayList<String> l;
        synchronized (miss) { l = new java.util.ArrayList<>(miss.keySet()); }
        StringBuilder b = new StringBuilder();
        for (int i = l.size() - 1, k = 0; i >= 0 && k < n; i--, k++) {
            String x = l.get(i); int sp = x.indexOf(' ');
            b.append("\n· ").append(x.substring(0, sp)).append("：").append(shortPath(x.substring(sp + 1)));
        }
        return b.toString();
    }
    static int missCount() { synchronized (miss) { return miss.size(); } }

    // ---------------- v2.51 用道具（草丛/盾/地雷/陷阱/扫描雷…） ----------------
    // 用户：战术道具能刷进背包，但草丛这类用不了。RE（libUE4）：
    //  - 功能道具 = ANZItem_PropBase 子类（草丛 ANZBRItem_Bush 直接继承它，虚表 0x4ccf8ac）。背包组件（人物+0x838）有 3 个道具槽
    //    +0x490/+0x494/+0x498（拾取时 0xfdef7c 放进去），当前选中的道具 SelectedPropInventory +0x568。
    //  - 用法是两步：选中 DoSelectedFunctionalProp（背包 vt+0x4d0 = 0xfe9570→0xfe9574：置道具 bSelected(+0x334)，道具 Tick 0xf2e124
    //    每帧算放置点 +0x338/+0x344、放置有效 +0x336），再「用」（背包 vt+0x484 = 0xfe98b0：+0x336 有效才走 UseItem 0xcdcdfc →
    //    UseThisItem 0xf2e3c4 → SpawnScenePropItem 0xf300dc 用 TargetProp(+0x3f0) 类在 +0x338 生成场景道具）。
    //  - 选中只有游戏界面（Lua：投掷物/道具栏 vt+0x410 0xfe915c）会调；经典模式界面没有道具栏 → 选不中 → 用不了。
    //    游戏自己的人机放盾 0xfc8f5c 就是这两步（vt+0x4d0 再 vt+0x484），这里照抄，经 'U' cave 在游戏线程里调。
    //  - 放置点算不出来（放置检测盒 +0x360/+0x364 不在）时 +0x336 一直是 0：「放下」把面前 1.5 米的位置写进去并置有效再用。
    static final long INV_SEL = 0xfe9570L, INV_USE = 0xfe98b0L, PROP_ONSEL = 0xf2e750L;
    static final class It { long p; String cls; String target; int slot; long cnt; boolean sel; boolean valid; }
    static volatile String useState = "";
    static volatile java.util.ArrayList<It> useList = new java.util.ArrayList<>();
    static long inv(RandomAccessFile m, long b) {
        long pawn = Mods.myPawn(); if (pawn < 0x10000) return 0;
        long inv = Server.rd(m, pawn + 0x838); if (inv < 0x10000) return 0;
        long vt = Server.rd(m, inv);
        if (Server.rd(m, vt + 0x4d0) != b + INV_SEL || Server.rd(m, vt + 0x484) != b + INV_USE) return -1;
        return inv;
    }
    static boolean isProp(RandomAccessFile m, long b, long o) { return o >= 0x10000 && Server.rd(m, Server.rd(m, o) + 0x394) == b + PROP_ONSEL; }
    /** 读背包里的功能道具（3 个道具槽 + 物品列表里的），只读 */
    static String scanUse() { return scanUse(false); }
    static String scanUse(boolean quiet) {
        long b = Mods.base(); if (b == 0) return useState = "找不到游戏库";
        java.util.ArrayList<It> out = new java.util.ArrayList<>();
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
            long inv = inv(m, b);
            if (inv == 0) return useState = "没找到人物/背包（进对局、先刷一次东西再点）";
            if (inv < 0) return useState = "背包组件和预期不一样，不敢调";
            Veh.Names nm = null; try { nm = Veh.Names.get(); } catch (Throwable t) {}
            long sel = Server.rd(m, inv + 0x568);
            java.util.HashSet<Long> seen = new java.util.HashSet<>();
            java.util.ArrayList<long[]> cand = new java.util.ArrayList<>();
            for (int k = 0; k < 3; k++) cand.add(new long[] { Server.rd(m, inv + 0x490 + 4 * k), k + 1 });
            long data = Server.rd(m, inv + 0x4a8), num = Server.rd(m, inv + 0x4ac);
            for (int k = 0; k < Math.min(num, 200); k++) cand.add(new long[] { Server.rd(m, data + 4 * k), 0 });
            for (long[] c : cand) {
                long p = c[0];
                if (!isProp(m, b, p) || !seen.add(p)) continue;
                It it = new It(); it.p = p; it.slot = (int) c[1];
                it.cls = Alive.clsName(m, nm, p);
                long tc = Server.rd(m, p + 0x3f0);
                it.target = tc < 0x10000 ? null : Alive.objName(m, nm, tc);
                it.cnt = Server.rd(m, p + 0x2b4); it.sel = p == sel;
                byte[] x = new byte[1]; m.seek(p + 0x336); m.readFully(x); it.valid = x[0] != 0;
                out.add(it);
            }
        } catch (Throwable e) { return useState = "读背包出错 " + e; }
        useList = out;
        StringBuilder sb = new StringBuilder(out.isEmpty() ? "背包里没有功能道具（草丛/盾/雷等）" : "功能道具 " + out.size() + " 个：");
        for (It it : out) sb.append("\n· ").append(nice(it.cls)).append(it.slot > 0 ? "（槽" + it.slot + "）" : "（不在道具槽）")
            .append(" 数量 ").append(it.cnt).append(it.sel ? " 已拿出" + (it.valid ? "，放置点有效" : "，放置点无效") : "")
            .append(it.target == null ? "，场景模型类没加载（蓝图不在手机上，放不出来）" : "，放出来是 " + nice(it.target));
        useState = sb.toString(); if (!quiet) LogCap.note("用道具：" + useState);
        return "ok";
    }
    /** v2.56：道具键认不出道具时，把道具槽和背包里每个东西的类名都记下来（只读），随日志上传 */
    static String rawDump() {
        long b = Mods.base(); if (b == 0) return "找不到游戏库";
        StringBuilder sb = new StringBuilder("背包原始：");
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
            long pawn = Mods.myPawn(); sb.append("pawn=").append(Long.toHexString(pawn));
            if (pawn < 0x10000) return sb.toString();
            long inv = Server.rd(m, pawn + 0x838); sb.append(" inv=").append(Long.toHexString(inv));
            if (inv < 0x10000) return sb.toString();
            long vt = Server.rd(m, inv);
            sb.append(" vt4d0=").append(Long.toHexString(Server.rd(m, vt + 0x4d0) - b)).append(" vt484=").append(Long.toHexString(Server.rd(m, vt + 0x484) - b));
            Veh.Names nm = null; try { nm = Veh.Names.get(); } catch (Throwable t) {}
            for (int k = 0; k < 3; k++) { long p = Server.rd(m, inv + 0x490 + 4 * k); sb.append("\n槽").append(k + 1).append(' ').append(Long.toHexString(p));
                if (p >= 0x10000) sb.append(' ').append(Alive.clsName(m, nm, p)).append(" +394=").append(Long.toHexString(Server.rd(m, Server.rd(m, p) + 0x394) - b)); }
            long data = Server.rd(m, inv + 0x4a8), num = Server.rd(m, inv + 0x4ac);
            sb.append("\n列表 ").append(num).append(" 个");
            for (int k = 0; k < Math.min(num, 60); k++) { long p = Server.rd(m, data + 4 * k); if (p < 0x10000) continue;
                sb.append("\n· ").append(Alive.clsName(m, nm, p)).append(" +394=").append(Long.toHexString(Server.rd(m, Server.rd(m, p) + 0x394) - b)); }
        } catch (Throwable e) { sb.append(" 出错 ").append(e); }
        return sb.toString();
    }
    static String nice(String c) { if (c == null) return "?"; String s = c.replace("BP_", "").replace("_C", ""); return s.length() > 26 ? s.substring(0, 26) : s; }
    /** 拿出（选中）：背包 vt+0x4d0(道具)，和人机放盾同一个函数 */
    static String select(It it) {
        long b = Mods.base(); if (b == 0) return "找不到游戏库";
        long inv;
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
            inv = inv(m, b); if (inv <= 0) return "背包不对";
            if (!isProp(m, b, it.p)) return "道具已经不在了，点「查背包」";
        } catch (Throwable e) { return "出错 " + e; }
        try {
            long[] r = Veh.ucall(inv, it.p, 0x4d0, 0, null);
            if (r[0] != 0x5b0e) return Veh.uOn ? "补丁没反应" : "用道具补丁没装（" + (Veh.installed ? "空位被占 " + Veh.uBusy : "刷物品补丁还没装好，进对局再试") + "）";
            LogCap.note("用道具：拿出 " + it.cls + " 返回 " + (r[1] & 0xff));
            return (r[1] & 0xff) != 0 ? "ok" : "游戏不让拿出（在车上/正在用别的/已经拿着）";
        } catch (InterruptedException e) { return "中断"; }
    }
    // ---------------- v2.98 放下 / 收回 走游戏原生路径 ----------------
    // 放下断点根因（RE，NZInventoryManager）：
    //  背包 vt+0x484 → UseItem 0xcdcdfc → PreUseItem vt+0x494 0xcdd510 → 0xcddf54 起读条（人物+0xce4 ActionWithCastTime，
    //  inv+0x2c0 CachedPreUsingItem = 道具，道具状态 +0x311=2）→ 读条完 → 委托 FinishUsingItemCastTime vt+0x4b8 0xcde9f4。
    //  它在权威端、人物不是人机（人物+0xb34 bIsBot==0）时先调 ClientStopPreUsingItem(道具)（0xcdea88，vt+0x4a8）——
    //  专用服务器上这是发给客户端的 RPC；单机(NetMode 0)和房主自己的人物上 RPC 直接在本地执行 →
    //  ClientStopPreUsingItem_Implementation 0xcde77c → vt+0x4a0 0xcde5e0：道具 StopPreUsingItem（日志 [marvinsong]…StopPreUsingItem）、
    //  inv+0x2c0 = 0 → 回到 0xcdeaa0 比较 0xcdb0ac(inv)（状态 2 的道具）== inv+0x2c0 失败 → 「FinishUsingItemCastTime failed」，
    //  UseThisItem（生成场景道具）和 0xcdf058（扣数量）都不走。人机 bIsBot=1 跳过这个 RPC，所以人机能放盾。
    // 修：0xcdea88 → cave（SLACK+0x3d0，game/re/fin_cave.py，emu_fin.py 测过）：照样调 ClientStopPreUsingItem（客户端那一半的收尾照做），
    //  调完 inv+0x2c0 变 0 就写回原来的道具（专用服务器/远端客户端 RPC 不在本地跑，值不变，不受影响），再回 0xcdea98 走原来的成功分支。
    static final long FIN_HOOK = 0xcdea88L, FIN_RET = 0xcdea98L, FIN_CAVE = Server.SLACK + 0x3d0;
    static final String FIN_O = "000094e5a82490e5";
    static final String FIN_CODE = "06002de9000094e5a82490e50400a0e132ff2fe10600bde8c00294e5000050e3c012840504f01fe5";
    static volatile String finState = "放下修复：没装";
    static synchronized String finInstall() {
        long b = Mods.base(); if (b == 0) return finState = "放下修复：找不到游戏库";
        byte[] want = Patches.hex(FIN_CODE + Server.w32((int) (b + FIN_RET)));
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "rw")) {
            byte[] cur = new byte[want.length]; m.seek(b + FIN_CAVE); m.readFully(cur);
            if (!java.util.Arrays.equals(cur, want)) { m.seek(b + FIN_CAVE); m.write(want); m.seek(b + FIN_CAVE); m.readFully(cur); }
            if (!java.util.Arrays.equals(cur, want)) return finState = "放下修复：cave 写不进去";
        } catch (Throwable e) { return finState = "放下修复：出错 " + e.getMessage(); }
        String r = Mods.write(FIN_HOOK, FIN_O, "04f01fe5" + Server.w32((int) (b + FIN_CAVE)), true);
        return finState = "ok".equals(r) ? "放下修复：已装" : "放下修复：钩子 " + r;
    }

    // ---------------- 诊断 ----------------
    static final java.util.ArrayDeque<String> diag = new java.util.ArrayDeque<>();
    static void d(String s) {
        String t = new java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US).format(new java.util.Date()) + " " + s;
        synchronized (diag) { diag.addLast(t); while (diag.size() > 160) diag.removeFirst(); }
        LogCap.note("道具：" + s);
    }
    static String diagText() {
        StringBuilder sb = new StringBuilder("道具诊断：").append(finState).append("；").append(propsBar);
        synchronized (diag) { for (String x : diag) sb.append('\n').append(x); }
        return sb.toString();
    }
    static final String[] GAME_KEYS = { "PreUseItem", "FinishUsingItemCastTime", "StopPreUsingItem", "AbortPreUsingItem", "CanUseThisItem",
        "UseThisItem", "SpawnScenePropItem", "Recycle", "recycle", "StartAction", "PlayerPropComponent", "ClientPreUseItemPrepareFinish" };
    static volatile long watchUntil;
    static void gameLine(String l) {
        if (System.currentTimeMillis() > watchUntil) return;
        for (String k : GAME_KEYS) if (l.contains(k)) { int i = l.indexOf("]Log"); d("游戏: " + (i > 0 ? l.substring(i + 1) : l)); return; }
    }
    static void upload(String why) {
        d("上传日志：" + why);
        android.app.Activity a = Con.act; if (a != null) AvatarLog.export(a, "prop-");
    }
    static int b8(RandomAccessFile m, long a) throws java.io.IOException { byte[] x = new byte[1]; m.seek(a); m.readFully(x); return x[0] & 0xff; }
    static float fl(RandomAccessFile m, long a) { return Float.intBitsToFloat((int) Server.rd(m, a)); }
    /** 人物+0xce4 读条动作：+0x48 进行中、+0x5c 类型（1 用道具 / 6 回收）、+0x34 时长 */
    static String act(RandomAccessFile m, long pawn) throws java.io.IOException {
        long ac = Server.rd(m, pawn + 0xce4);
        if (ac < 0x10000) return "读条无";
        return "读条 " + (b8(m, ac + 0x48) != 0 ? "进行中" : "停") + " 类型" + Server.rd(m, ac + 0x5c) + " " + fl(m, ac + 0x34) + "s";
    }
    static int netMode() { return Lan.role == 1 ? 2 : Lan.role == 2 ? 3 : 0; }

    /** 放下：游戏自己的「用选中的道具」背包 vt+0x484（读条、扣数量、生成都由游戏做）。之后 0.5 秒记一次状态，最多 10 秒 */
    static String place() {
        long b = Mods.base(); if (b == 0) return "找不到游戏库";
        if (!finState.endsWith("已装")) finInstall();
        long inv, sel, pawn = Mods.myPawn(); int valid; long cnt0; String cls = null; long target;
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
            inv = inv(m, b); if (inv <= 0) return "背包不对";
            sel = Server.rd(m, inv + 0x568);
            if (!isProp(m, b, sel)) return "手上没有道具";
            valid = b8(m, sel + 0x336); cnt0 = Server.rd(m, sel + 0x2b4); target = Server.rd(m, sel + 0x3f0);
            for (It it : useList) if (it.p == sel) cls = it.cls;
            long own = Server.rd(m, sel + 0x2b0), ps = pawn < 0x10000 ? 0 : Server.rd(m, pawn + 0x2c4);
            d("放下 " + cls + " 数量 " + cnt0 + " 放置点" + (valid != 0 ? "有效" : "无效") + " 状态+311=" + b8(m, sel + 0x311)
                + " 拥有者=" + (own == pawn ? "我" : Long.toHexString(own)) + " 拥有者背包=" + (own < 0x10000 ? "-" : Long.toHexString(Server.rd(m, own + 0x838)))
                + " 地图状态PS+a14=" + (ps < 0x10000 ? "-" : String.valueOf(Server.rd(m, ps + 0xa14))) + " 倒地+fe4=" + (pawn < 0x10000 ? "-" : String.valueOf(b8(m, pawn + 0xfe4)))
                + " 载具+ae8=" + (pawn < 0x10000 ? "-" : Long.toHexString(Server.rd(m, pawn + 0xae8))) + " NetMode≈" + netMode() + " " + finState);
        } catch (Throwable e) { return "出错 " + e; }
        if (valid == 0) { d("放置点无效（预览红），游戏不会放"); return "放置位置不行（预览是红的），换个平地再放"; }
        watchUntil = System.currentTimeMillis() + 12000;
        long[] r;
        try { r = Veh.ucall(inv, 0, 0x484, 0, null); } catch (InterruptedException e) { return "中断"; }
        if (r[0] != 0x5b0e) return Veh.uOn ? "补丁没反应" : "用道具补丁没装（" + (Veh.installed ? "空位被占 " + Veh.uBusy : "刷物品补丁还没装好，进对局再试") + "）";
        final long fsel = sel, finv = inv, fpawn = pawn, ftarget = target, fcnt0 = cnt0; final String fcls = cls;
        new Thread(() -> watchPlace(b, finv, fsel, fpawn, ftarget, fcnt0, fcls), "zf-prop-watch").start();
        return "ok";
    }
    static void watchPlace(long b, long inv, long sel, long pawn, long target, long cnt0, String cls) {
        boolean done = false; String res = "10 秒内没放下";
        java.util.HashSet<Long> before = new java.util.HashSet<>(scene(target));
        for (int i = 0; i < 20 && !done; i++) {
            try { Thread.sleep(500); } catch (InterruptedException e) { return; }
            try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
                boolean al = alive(m, b, sel);
                long cnt = al ? Server.rd(m, sel + 0x2b4) : 0;
                d(String.format(java.util.Locale.US, "放下中 %.1fs 道具%s 数量%d 状态+311=%s inv+2c0=%s +2c4=%s %s", (i + 1) * 0.5, al ? "在" : "没了", cnt,
                    al ? String.valueOf(b8(m, sel + 0x311)) : "-", Long.toHexString(Server.rd(m, inv + 0x2c0)), Long.toHexString(Server.rd(m, inv + 0x2c4)), act(m, pawn)));
                if (!al || cnt < cnt0) { done = true; res = "放下成功（数量 " + cnt0 + " → " + cnt + "）"; }
            } catch (Throwable e) { d("放下监视出错 " + e); }
        }
        int added = 0;
        if (done) {
            try { Thread.sleep(300); } catch (InterruptedException ignored) {}
            long now = System.currentTimeMillis();
            for (long o : scene(target)) if (!before.contains(o)) { Placed p = new Placed(); p.actor = o; p.cls = cls == null ? "" : cls; p.t = now; synchronized (placed) { placed.add(p); } added++; }
            res += "，地上新场景道具 " + added + " 个";
        }
        d(res);
        if (!done || added == 0) upload("放下" + (done ? "后没找到场景道具" : "失败"));
        final String msg = done ? null : "没放下（日志已自动上传）";
        android.app.Activity a = Con.act; if (msg != null && a != null) a.runOnUiThread(() -> GameOverlay.toast(a, msg));
        PropBtn.refresh();
    }

    /** 放下的场景道具（放下成功后按 TargetProp 类扫出来的新对象），给「收回 / 取消伪装」用 */
    static final class Placed { long actor; String cls; long t; }
    static final java.util.ArrayList<Placed> placed = new java.util.ArrayList<>();
    /** UObject 还活着：GUObjectArray（*0x51250ac +0x10，每项 16 字节）[idx].Object == 自己，且没标 PendingKill（+4 的 1<<29） */
    static boolean alive(RandomAccessFile m, long b, long o) {
        if (o < 0x10000) return false;
        long g = Server.rd(m, b + 0x51250acL); if (g < 0x10000) return false;
        long objs = Server.rd(m, g + 0x10); long idx = Server.rd(m, o + 8);
        if (objs < 0x10000 || idx > 0x200000) return false;
        long e = objs + idx * 16;
        return Server.rd(m, e) == o && (Server.rd(m, e + 4) & 0x20000000L) == 0;
    }
    static boolean camo(String c) { return c != null && (c.contains("Camou") || c.contains("Rock") || c.contains("Stone")); }
    /** 最近放下、还在的场景道具（死掉的顺手删掉）；null = 没有 */
    static Placed lastPlaced() {
        long b = Mods.base(); if (b == 0) return null;
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
            synchronized (placed) {
                for (int i = placed.size() - 1; i >= 0; i--) { if (alive(m, b, placed.get(i).actor)) return placed.get(i); placed.remove(i); }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /** 全部活着的、类 == cls 的对象（GUObjectArray：*0x51250ac +0x10 Objects，+0x18 个数，每项 16 字节） */
    static java.util.ArrayList<Long> scene(long cls) {
        java.util.ArrayList<Long> out = new java.util.ArrayList<>();
        long b = Mods.base(); if (b == 0 || cls < 0x10000) return out;
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
            long g = Server.rd(m, b + 0x51250acL); if (g < 0x10000) return out;
            long objs = Server.rd(m, g + 0x10), n = Server.rd(m, g + 0x18);
            if (objs < 0x10000 || n <= 0 || n > 0x200000) return out;
            byte[] blk = new byte[16 * 4096]; byte[] c4 = new byte[4];
            for (long i = 0; i < n; i += 4096) {
                int k = (int) Math.min(4096, n - i);
                m.seek(objs + i * 16); m.readFully(blk, 0, k * 16);
                java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(blk).order(java.nio.ByteOrder.LITTLE_ENDIAN);
                for (int j = 0; j < k; j++) {
                    long o = bb.getInt(j * 16) & 0xffffffffL; int fl = bb.getInt(j * 16 + 4);
                    if (o < 0x10000 || (fl & 0x20000000) != 0) continue;
                    try { m.seek(o + 0xc); m.readFully(c4); } catch (Throwable t) { continue; }
                    if ((java.nio.ByteBuffer.wrap(c4).order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt() & 0xffffffffL) == cls) out.add(o);
                }
            }
        } catch (Throwable e) { d("扫场景道具出错 " + e); }
        return out;
    }

    /** 收回：道具组件 = 人物+0x1160（UNZBRPlayerPropComponent，组件+0xbc = 人物，虚表+0x210 = ServerStartRecycleSceneItem 实现 0x110f334）。
     *  单机(NetMode 0)：调客户端入口 StartRecycleItem 0x110d6a0 —— NetMode 0 时它不起本地读条，直接 RPC（本地执行）进 0x110f334，只有一个读条。
     *  房主(NetMode 2)：0x110d6a0 会先在同一个读条动作(组件+0xb8)上起本地读条，再本地执行 0x110f334 起服务端读条 → 同一个动作 StartAction 两次，
     *   第二次「already active, force to abort」把第一次 Abort → 广播 OnCancelRecycleSceneItem → StopRecycleItem，两边互相取消。
     *   所以房主直接调服务端实现 0x110f334（专用服务器收到 RPC 后做的就是这个）：只起服务端读条，场景道具 Tick 倒计时 → OnRecycle → DoRecycleDestroy 还背包、删除。
     *  伪装石头：取消伪装 0x110dc50(组件, 伪装物)。 */
    static final long REC_CLIENT = 0x110d6a0L, REC_SERVER = 0x110f334L, CANCEL_CAMO = 0x110dc50L;
    static String recycle(Placed p) {
        long b = Mods.base(); if (b == 0) return "找不到游戏库";
        long pawn = Mods.myPawn(); if (pawn < 0x10000) return "找不到人物";
        long comp; float dur; int nm = netMode();
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
            if (!alive(m, b, p.actor)) { synchronized (placed) { placed.remove(p); } return "已经没了"; }
            comp = Server.rd(m, pawn + 0x1160);
            long vt = comp < 0x10000 ? 0 : Server.rd(m, comp);
            if (comp < 0x10000 || Server.rd(m, comp + 0xbc) != pawn || Server.rd(m, vt + 0x210) != b + REC_SERVER) {
                d("道具组件对不上：人物+1160=" + Long.toHexString(comp) + (comp < 0x10000 ? "" : " +bc=" + Long.toHexString(Server.rd(m, comp + 0xbc)) + " vt+210=" + Long.toHexString(Server.rd(m, vt + 0x210) - b)));
                upload("收回：道具组件对不上"); return "道具组件对不上（日志已上传）";
            }
            dur = fl(m, p.actor + 0x2ec);
            d("收回 " + p.cls + " 场景道具 " + Long.toHexString(p.actor) + " 回收时长+2ec=" + dur + " 可回收距离+420=" + fl(m, p.actor + 0x420)
                + " 距离=" + dist(m, pawn, p.actor) + " NetMode≈" + nm + " " + act(m, pawn));
        } catch (Throwable e) { return "出错 " + e; }
        boolean cm = camo(p.cls);
        if (!cm && dur <= 0) { upload("收回：回收时长为 0"); return "这个道具游戏里不能回收（回收时长 0）"; }
        if (nm == 3) return "加入别人的房间时由房主收回";
        long fn = cm ? CANCEL_CAMO : nm == 2 ? REC_SERVER : REC_CLIENT;
        watchUntil = System.currentTimeMillis() + 20000;
        long[] r;
        try { r = Veh.ucall(comp, p.actor, 0, 0, null, b + fn); } catch (InterruptedException e) { return "中断"; }
        d((cm ? "取消伪装" : nm == 2 ? "收回：调服务端实现 0x110f334" : "收回：调 StartRecycleItem 0x110d6a0") + " 返回 " + Long.toHexString(r[0]));
        if (r[0] != 0x5b0e) return "补丁没反应";
        if (cm) { synchronized (placed) { placed.remove(p); } return "ok"; }
        final long fp = pawn;
        new Thread(() -> watchRecycle(b, p, fp), "zf-prop-rec").start();
        return "ok";
    }
    static String dist(RandomAccessFile m, long a1, long a2) {
        try {
            long r1 = Server.rd(m, a1 + 0x128), r2 = Server.rd(m, a2 + 0x128);
            if (r1 < 0x10000 || r2 < 0x10000) return "?";
            float dx = fl(m, r1 + 0x140) - fl(m, r2 + 0x140), dy = fl(m, r1 + 0x144) - fl(m, r2 + 0x144), dz = fl(m, r1 + 0x148) - fl(m, r2 + 0x148);
            return String.valueOf(Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz)));
        } catch (Throwable e) { return "?"; }
    }
    /** 每 0.5 秒记 场景道具 +0x3e4/+0x3e8/+0x2cc/+0x438/+0x439/存活 和读条，最多 20 秒；没收回就自动上传 */
    static void watchRecycle(long b, Placed p, long pawn) {
        boolean gone = false;
        for (int i = 0; i < 40 && !gone; i++) {
            try { Thread.sleep(500); } catch (InterruptedException e) { return; }
            try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
                boolean al = alive(m, b, p.actor);
                if (!al) { gone = true; d(String.format(java.util.Locale.US, "收回中 %.1fs 场景道具已删除", (i + 1) * 0.5)); break; }
                long a = p.actor;
                d(String.format(java.util.Locale.US, "收回中 %.1fs +3e4=%s +3e8=%s +2cc=%s(%.2f) +438=%d +439=%d 距离=%s %s", (i + 1) * 0.5,
                    Long.toHexString(Server.rd(m, a + 0x3e4)), Long.toHexString(Server.rd(m, a + 0x3e8)), Long.toHexString(Server.rd(m, a + 0x2cc)), fl(m, a + 0x2cc),
                    b8(m, a + 0x438), b8(m, a + 0x439), dist(m, pawn, a), act(m, pawn)));
            } catch (Throwable e) { d("收回监视出错 " + e); }
        }
        if (gone) synchronized (placed) { placed.remove(p); }
        try { Thread.sleep(500); } catch (InterruptedException ignored) {}
        scanUse(true);
        StringBuilder bag = new StringBuilder();
        for (It it : useList) if (it.cls != null && it.cls.equals(p.cls)) bag.append(" 背包里 ").append(nice(it.cls)).append(" 数量 ").append(it.cnt);
        d((gone ? "收回完成" : "20 秒没收回") + (bag.length() == 0 ? "，背包里没看到同类" : "，" + bag));
        upload(gone ? "收回完成" : "收回失败");
        android.app.Activity a = Con.act;
        final String msg = gone ? "已收回" + (bag.length() == 0 ? "（背包里没看到，日志已上传）" : "") : "没收回（日志已上传）";
        if (a != null) a.runOnUiThread(() -> GameOverlay.toast(a, msg));
        PropBtn.refresh();
    }

    /** 运行时扫 GNames 里含 PropsBar 的名字，只确认手机上有没有原生道具栏 UMG 资源（不做别的） */
    static volatile String propsBar = "道具栏资源：没查";
    static volatile boolean pbBusy;
    static void scanPropsBar() {
        if (pbBusy || !propsBar.equals("道具栏资源：没查")) return;
        pbBusy = true;
        new Thread(() -> {
            try {
                Thread.sleep(8000);
                java.util.ArrayList<String> hits = new java.util.ArrayList<>();
                ModeProbe.find("propsbar", hits);
                StringBuilder g = new StringBuilder(); int nGame = 0;
                for (String h : hits) if (h.startsWith("/Game/")) { nGame++; if (g.length() < 1200) g.append("\n  ").append(h); }
                propsBar = "道具栏资源：名字表含 PropsBar " + hits.size() + " 个，/Game/ 路径 " + nGame + " 个" + g
                    + (hits.size() > nGame ? "\n  其它：" + String.join(" ", hits.subList(0, Math.min(hits.size(), 30))) : "");
                d(propsBar);
            } catch (Throwable e) { propsBar = "道具栏资源：扫名字表出错 " + e; }
            finally { pbBusy = false; }
        }, "zf-propsbar").start();
    }

    /** 收起手上的道具：背包 vt+0x4d4 0xfe96e4 */
    static void unselect() {
        try {
            long b = Mods.base(); if (b == 0) return; long inv;
            try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) { inv = inv(m, b); if (inv <= 0) return; if (Server.rd(m, Server.rd(m, inv) + 0x4d4) != b + 0xfe96e4L) return; }
            Veh.ucall(inv, 0, 0x4d4, 0, null);
        } catch (Throwable ignored) {}
    }
    /** v2.54：刚刷进背包的东西，从背包物品列表里找类名带这个编号的，取中文名（找不到就用类名尾巴） */
    static String nameInBag(long id) {
        long b = Mods.base(); if (b == 0) return null;
        String key = String.valueOf(id);
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
            long pawn = Mods.myPawn(); if (pawn < 0x10000) return null;
            long inv = Server.rd(m, pawn + 0x838); if (inv < 0x10000) return null;
            Veh.Names nm = Veh.Names.get();
            java.util.ArrayList<Long> c = new java.util.ArrayList<>();
            for (int k = 0; k < 3; k++) c.add(Server.rd(m, inv + 0x490 + 4 * k));
            long data = Server.rd(m, inv + 0x4a8), num = Server.rd(m, inv + 0x4ac);
            for (int k = 0; k < Math.min(num, 300); k++) c.add(Server.rd(m, data + 4 * k));
            for (long o : c) {
                if (o < 0x10000) continue;
                String cls = Alive.clsName(m, nm, o);
                if (cls == null || !cls.contains(key)) continue;
                return PropBtn.cn(cls);
            }
        } catch (Throwable ignored) {}
        return null;
    }
}
