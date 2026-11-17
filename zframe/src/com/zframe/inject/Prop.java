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
    }
    static boolean fallbackUsed() { return slot != null && slot.getInt(0) != 0; }

    // ---------------- 缺资源记录 ----------------
    static final java.util.LinkedHashMap<String, Integer> miss = new java.util.LinkedHashMap<>();
    static volatile long missAt; static volatile String lastMiss = "", lastUseLog = "";
    static final java.util.regex.Pattern PATH = java.util.regex.Pattern.compile("(/Game/[^\\s'\"\\]\\)\\[,]+)");
    static void line(String l) {
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
    /** 放下/用：背包 vt+0x484；放置点无效就用面前 1.5 米 */
    static String use() {
        long b = Mods.base(); if (b == 0) return "找不到游戏库";
        long inv, sel; boolean valid; float[] pos = null;
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
            inv = inv(m, b); if (inv <= 0) return "背包不对";
            sel = Server.rd(m, inv + 0x568);
            if (!isProp(m, b, sel)) return "还没拿出道具：先点道具名「拿出」";
            byte[] x = new byte[1]; m.seek(sel + 0x336); m.readFully(x); valid = x[0] != 0;
            if (!valid) {
                double[] q = Veh.info();
                if (q == null) return "读不到人物位置";
                double yaw = Math.toRadians(q[3]);
                pos = new float[] { (float) (q[0] + Math.cos(yaw) * 150), (float) (q[1] + Math.sin(yaw) * 150), (float) (q[2] - 88), 0f, (float) q[3], 0f };
            }
        } catch (Throwable e) { return "出错 " + e; }
        try {
            long[] r = Veh.ucall(inv, 0, 0x484, valid ? 0 : sel, pos);
            if (r[0] != 0x5b0e) return Veh.uOn ? "补丁没反应" : "用道具补丁没装（" + (Veh.installed ? "空位被占 " + Veh.uBusy : "刷物品补丁还没装好，进对局再试") + "）";
            LogCap.note("用道具：放下 " + Long.toHexString(sel) + (valid ? "（游戏算的放置点）" : "（面前 1.5 米）"));
            return valid ? "ok" : "ok2";
        } catch (InterruptedException e) { return "中断"; }
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
