package com.zframe.inject;

import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;

/** 训练岛（训练场 = BP_NZBRPracticeGameMode_C，游戏自带「训练」入口）。
 *
 *  v3.42 根因（RE + v3.41 block-1119-195412 日志）：
 *  - 训练场是「一个 DS 开 16 份岛」的设计：GameMode BeginPlay 0x1081d70 建 16 个位置（GM+0xa40 数组，每个 0x34 字节，
 *    [0]=块号，+4..+0xc 出生点，+0x10 玩家数组；个数 +0xa44，块数 +0xa4c 默认 16）。玩家 Login 0x1084c5c 时
 *    0x10845b0 给他挑位置、塞进位置的玩家数组；所有副本关卡加载完（GM+0xa70）后 AddPlayerToSlot 0x1083a98
 *    写 PC+0xbdc 块号 / +0xbe0 块数、传到这个位置的出生点、发 Client Load Block（客户端 0x1086960 → LoadBlock(块号, 块数)），
 *    还没加载完就先排进 GM+0xa24，等 Tick 0x1082cb0 里 OnAllDuplicateLevelLoaded 0x108357c 统一处理。
 *  - 单机里玩家 Login（19:51:24）比 GameMode BeginPlay（19:51:29）早 → 那时位置数组是空的 → 找不到位置（v3.37 空队伍补丁让它跳过）
 *    → 玩家永远不进位置：不发 Client Load Block、不传出生点、练习组件（PracticeSlotIndex/刷车/靶子）都没绑到玩家。
 *  - 「所有副本关卡加载完」这道检查不是卡点：v3.41 日志 19:52:44 已经出现 OnLevelStreamingLayerLoadCompleted Landscape
 *    → OnAllDuplicateLevelLoaded（GM+0xa70=1、+0xa21=1），只是队列里没有玩家。
 *  - v3.41「第 9 块地形最全」是假象：LoadBlock 第三个参数传的是 1（块数），块号 ≥1 全部 Out Of Index（日志 15 行），
 *    只有块 0 真加载了，地形块数是随时间涨上来的（1→4→14→17）。
 *  做法：BeginPlay 建好位置后，按 Login 原来的步骤把自己补进位置（0x10845b0 挑位置 → 塞进位置玩家数组 → 排进 +0xa24 或直接 AddPlayerToSlot），
 *  再按 Client Load Block 的做法 LoadBlock(管理器, 位置块号, 块数)；之后传出生点、刷东西都走游戏原生。全部未实测。 */
final class Island {
    static final long LOAD_BLOCK = 0x1163f3cL, FIND_SLOT = 0x10845b0L, ADD_TO_SLOT = 0x1083a98L, REALLOC = 0x1989d38L, FILL_LAYERS = 0x11649bcL;
    /** 训练场（Practice 模式）登录时 0x10845b0 找队伍返回空，0x1084f6c ldr [r8,#0x14] 崩（fault 0x14，19:07 实测诊断）。
     *  改成 movs r8,r0 / ldrne / cmpne / beq 0x10850a8（没队伍就走原来的「跳过」分支）。v3.42 起空位置由 Island.join 事后补上。 */
    static final long PR = 0x1084f2cL;
    static final String PR_O = "0080a0e1580596e5000050e35a00000a", PR_N = "0080b0e158059615000050135a00000a";
    static volatile String fix = "训练场空队伍补丁：没装";
    static void install() { String r = Mods.write(PR, PR_O, PR_N, true); fix = "训练场空队伍补丁：" + r; LogCap.note(fix); }
    static volatile String block = "分配位置：没试";
    static volatile String state = "训练岛：没进";
    static volatile String range = "射击训练区：没找";
    static volatile String spawn = "原生刷车/刷物资：没查";
    static volatile boolean running;

    static void matchLoaded() {
        if (running) return; running = true;
        block = "分配位置：没试"; range = "射击训练区：没找"; spawn = "原生刷车/刷物资：没查";
        { android.app.Activity x = Con.act; if (x != null) AvatarLog.autoTrain(x); }
        new Thread(() -> {
            try { run(); } catch (Throwable t) { state = "训练岛：出错 " + t; LogCap.note(state); GameOverlay.toastAny(state); }
            finally { running = false; }
        }, "zf-island").start();
    }

    static void run() throws Exception {
        long t0 = System.currentTimeMillis();
        long b = 0, gm = 0, pc = 0; String gmName = "?";
        Veh.Names nm = null;
        // 1) 等 GameMode BeginPlay 建好位置
        while (true) {
            if (System.currentTimeMillis() - t0 > 90000) { state = "训练岛：90 秒没等到 GameMode 建位置（GM " + Long.toHexString(gm) + " " + gmName + "）"; note(state); return; }
            Thread.sleep(1000);
            if (!Con.training || !Con.inMatch) return;
            b = Mods.base(); if (b == 0) continue;
            double[] q = Veh.info(); if (q == null) { state = "训练岛：等人物"; continue; }
            gm = (long) q[4]; pc = Veh.lastPc(); nm = Veh.Names.get();
            if (gm < 0x10000 || pc < 0x10000) { state = "训练岛：等 GameMode/控制器"; continue; }
            try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
                gmName = Alive.clsName(m, nm, gm);
                if (!gmName.contains("PracticeGameMode")) { state = "训练岛：模式是 " + gmName + "，不是训练场，不分配位置"; note(state); return; }
                int n = (int) Server.rd(m, gm + 0xa44);
                if (n <= 0) { state = "训练岛：等 BeginPlay 建位置（现在 0 个）"; continue; }
            }
            break;
        }
        // 2) 地块管理器
        long mgr = 0;
        for (int i = 0; i < 20 && mgr == 0; i++) {
            Scan s = scan(b, nm); mgr = s.mgr;
            if (mgr == 0) { state = "训练岛：没找到地块管理器（NZStreamingLevelsManagerActor_TileMode），再找"; Thread.sleep(1000); }
        }
        if (mgr == 0) { state = "训练岛：20 秒没找到地块管理器"; note(state); return; }
        // 3) 补进位置 + 加载这个位置的块
        join(b, gm, pc, mgr);
        // 4) 等原生 AddPlayerToSlot（OnAllDuplicateLevelLoaded 处理队列）
        long w0 = System.currentTimeMillis(); boolean ok = false;
        while (System.currentTimeMillis() - w0 < 150000) {
            Thread.sleep(2000);
            if (!Con.training || !Con.inMatch) return;
            int slotBlk, allLoaded, handled; int land;
            try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
                slotBlk = (int) Server.rd(m, pc + 0xbdc);
                allLoaded = rb(m, gm + 0xa70); handled = rb(m, gm + 0xa21);
            }
            land = scan(b, nm).land;
            state = "训练岛：等原生分配 " + (System.currentTimeMillis() - w0) / 1000 + " 秒；副本加载完 +a70=" + allLoaded + " 已处理 +a21=" + handled + " PC 块号 +bdc=" + slotBlk + " 地形块 " + land;
            if (slotBlk >= 0 && handled != 0) { ok = true; break; }
        }
        double[] me = Veh.info();
        String pos = me == null ? "?" : (int) me[0] + "," + (int) me[1] + "," + (int) me[2];
        if (!ok) { state = state + " → 150 秒没等到 AddPlayerToSlot；人物在 " + pos; note(state); GameOverlay.toastAny("训练岛：分配位置失败，看诊断"); }
        else { state = "训练岛：原生分配完成，人物在 " + pos + "，地形块 " + scan(b, nm).land; note(state); }
        // 5) 射击训练区
        Thread.sleep(3000);
        toRange(b, nm, gm, pc);
        // 6) 原生刷车/刷物资情况
        Thread.sleep(20000);
        if (!Con.training || !Con.inMatch) return;
        spawnDiag(b, nm, gm);
        android.app.Activity x = Con.act;
        if (x != null) { AvatarLog.manual = true; try { AvatarLog.export(x, "slot-"); } finally { AvatarLog.manual = false; } }
    }

    static void note(String s) { LogCap.note(s); }
    /** 读一个字节（Server.rd 只读 4 字节对齐） */
    static int rb(RandomAccessFile m, long a) { return (int) ((Server.rd(m, a & ~3L) >>> ((a & 3) * 8)) & 0xff); }

    /** 按 Login 0x1084f20..0x10851cc 的步骤把自己补进位置，再按 Client Load Block 0x1086a64 的做法加载这个位置的块。 */
    static void join(long b, long gm, long pc, long mgr) throws Exception {
        StringBuilder sb = new StringBuilder("分配位置：");
        long slots, slot = 0; int n, cnt, blk, cur;
        boolean in = false, queued = false; int allLoaded, handled;
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
            slots = Server.rd(m, gm + 0xa40); n = (int) Server.rd(m, gm + 0xa44); cnt = (int) Server.rd(m, gm + 0xa4c);
            allLoaded = rb(m, gm + 0xa70); handled = rb(m, gm + 0xa21);
            for (int i = 0; i < n && !in; i++) {
                long s = slots + 0x34L * i, d = Server.rd(m, s + 0x10); int k = (int) Server.rd(m, s + 0x14);
                for (int j = 0; j < k && j < 64; j++) if (Server.rd(m, d + 4L * j) == pc) { in = true; slot = s; }
            }
            long q = Server.rd(m, gm + 0xa24); int qn = (int) Server.rd(m, gm + 0xa28);
            for (int j = 0; j < qn && j < 64; j++) if (Server.rd(m, q + 4L * j) == pc) queued = true;
        }
        sb.append("位置 ").append(n).append(" 个，块数 ").append(cnt).append("，+a70=").append(allLoaded).append(" +a21=").append(handled).append("；");
        if (!in) {
            long[] r = Server.gcall(b + FIND_SLOT, gm, pc, 0, 0, 3000);
            slot = r[1];
            if (r[0] == 0) { block = sb.append("挑位置 0x10845b0 没跑（游戏线程没接）").toString(); note(block); return; }
            if (slot < slots || slot >= slots + 0x34L * n || (slot - slots) % 0x34 != 0) { block = sb.append("挑位置返回 ").append(Long.toHexString(slot)).append(" 不在位置数组里").toString(); note(block); return; }
            String a = append(b, slot + 0x10, pc);
            sb.append("塞进位置 ").append((slot - slots) / 0x34).append(" 玩家数组 ").append(a).append("；");
            if (!"ok".equals(a)) { block = sb.toString(); note(block); return; }
        } else sb.append("已在位置 ").append((slot - slots) / 0x34).append("；");
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
            blk = (int) Server.rd(m, slot); cur = (int) Server.rd(m, mgr + 0x440);
            sb.append("块号 ").append(blk).append(" 出生点 ").append((int) Mods.f(m, slot + 4)).append(',').append((int) Mods.f(m, slot + 8)).append(',').append((int) Mods.f(m, slot + 0xc)).append("；");
        }
        if (handled != 0) {
            long[] r = Server.gcall(b + ADD_TO_SLOT, gm, pc, 0, 0, 3000);
            sb.append("副本已处理过 → 直接 AddPlayerToSlot ").append(r[0] != 0 ? "已跑" : "没跑").append("；");
        } else if (!queued) {
            sb.append("排进 +a24 等 OnAllDuplicateLevelLoaded ").append(append(b, gm + 0xa24, pc)).append("；");
        } else sb.append("已在 +a24 队列；");
        if (cur != blk) {
            long[] r = Server.gcall(b + LOAD_BLOCK, mgr, blk, cnt, 0, 3000);
            sb.append("LoadBlock(管理器 ").append(Long.toHexString(mgr)).append(", ").append(blk).append(", ").append(cnt).append(") 原当前块 ").append(cur).append(r[0] != 0 ? " 已跑" : " 没跑");
        } else sb.append("管理器当前块已是 ").append(blk).append("，不重复加载");
        // ServerOnly 层（Main.umap ServerLayersSettings：ServerOnly/Pickups、WorldMeshTiles）：专用服在 0x1164d90（NetMode==1 才跑）里
        // 对每块调 FillLayersWithCacheData(管理器, +0x37c 服务器层, 块号)；客户端 LoadBlock 只填 +0x370。单机两边都不跑 → 地上没物资。
        // 这里按专用服的做法只给自己这块填一次（管理器 +0x2c8 开着才填，和 0x1164eac 一样）。
        int srv; try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) { srv = rb(m, mgr + 0x2c8); }
        if (srv != 0) {
            long[] r = Server.gcall(b + FILL_LAYERS, mgr, mgr + 0x37c, blk, 0, 3000);
            sb.append("；服务器层 FillLayersWithCacheData(+0x37c, ").append(blk).append(") ").append(r[0] != 0 ? "已跑" : "没跑");
        } else sb.append("；管理器 +0x2c8=0（不带服务器层），没填");
        block = sb.toString(); note(block);
        if (block.contains("没跑")) GameOverlay.toastAny("训练岛" + block);
    }

    /** 往 TArray<指针>（data,num,max）尾部加一个。空间不够用游戏的 FMemory::Realloc 0x1989d38（引擎以后用 FMemory 释放）。
     *  先写数据和 max，最后写 num，游戏线程同时读也不会读到空指针。 */
    static String append(long b, long arr, long v) throws Exception {
        long data; int num, max;
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) { data = Server.rd(m, arr); num = (int) Server.rd(m, arr + 4); max = (int) Server.rd(m, arr + 8); }
        if (num < 0 || num > 4096) return "数组数目不对 " + num;
        if (data != 0 && num < max) {
            Mods.pokeInt(data + 4L * num, (int) v);
            return Mods.pokeInt(arr + 4, num + 1);
        }
        int nmax = Math.max(4, num * 2);
        long[] r = Server.gcall(b + REALLOC, data, nmax * 4, 0, 0, 3000);
        if (r[0] == 0 || r[1] < 0x10000) return "分配内存失败（" + (r[0] == 0 ? "游戏线程没接" : "返回 " + Long.toHexString(r[1])) + "）";
        Mods.pokeInt(r[1] + 4L * num, (int) v);
        Mods.pokeInt(arr, (int) r[1]); Mods.pokeInt(arr + 8, nmax);
        return Mods.pokeInt(arr + 4, num + 1);
    }

    /** 射击训练区：游戏里的靶子是 NZSceneItem_RangeTarget（日志 NZSceneItem_RangeTarget.cpp）。
     *  只认人物 1.2 公里内的靶子，按 50 米连成群，取最大的一群；靶子沿射击方向排开，近处的靶子挨得更密，
     *  站在密的那一头往外 10 米、高 2 米落下。找不到不传，列出所有候选（出生点名字和坐标、靶子群）。 */
    static void toRange(long b, Veh.Names nm, long gm, long pc) throws Exception {
        double[] me = Veh.info();
        if (me == null) { range = "射击训练区：没拿到人物位置"; note(range); return; }
        Scan s = scan(b, nm);
        StringBuilder cand = new StringBuilder();
        cand.append("出生点 ").append(s.psName.size()).append(" 个：");
        for (int i = 0; i < s.psName.size(); i++) { float[] p = s.psPos.get(i); cand.append(s.psName.get(i)).append('(').append((int) p[0]).append(',').append((int) p[1]).append(',').append((int) p[2]).append(") "); }
        cand.append("；靶子共 ").append(s.target.size()).append(" 个");
        ArrayList<float[]> near = new ArrayList<>();
        for (float[] p : s.target) if (d2(p, me) < 120000.0 * 120000.0) near.add(p);
        if (near.isEmpty()) {
            range = "射击训练区：人物 " + (int) me[0] + "," + (int) me[1] + "," + (int) me[2] + " 1.2 公里内没有靶子，没传。" + cand + clusters(s.target);
            note(range); GameOverlay.toastAny("训练岛：附近没找到射击训练区靶子，看诊断"); return;
        }
        // 50 米单链聚类
        int n = near.size(); int[] grp = new int[n]; for (int i = 0; i < n; i++) grp[i] = i;
        for (int i = 0; i < n; i++) for (int j = i + 1; j < n; j++) if (d2(near.get(i), near.get(j)) < 5000.0 * 5000.0) { int a = root(grp, i), c = root(grp, j); if (a != c) grp[a] = c; }
        HashMap<Integer, ArrayList<float[]>> gs = new HashMap<>();
        for (int i = 0; i < n; i++) gs.computeIfAbsent(root(grp, i), k -> new ArrayList<>()).add(near.get(i));
        ArrayList<float[]> best = null; for (ArrayList<float[]> g : gs.values()) if (best == null || g.size() > best.size()) best = g;
        double cx = 0, cy = 0; for (float[] p : best) { cx += p[0]; cy += p[1]; } cx /= best.size(); cy /= best.size();
        float[] end;
        if (best.size() == 1) end = best.get(0);
        else {
            // 主轴（xy 协方差）两头，取最近邻距离小的那头（近靶密）
            double sxx = 0, sxy = 0, syy = 0;
            for (float[] p : best) { double dx = p[0] - cx, dy = p[1] - cy; sxx += dx * dx; sxy += dx * dy; syy += dy * dy; }
            double ang = 0.5 * Math.atan2(2 * sxy, sxx - syy), ux = Math.cos(ang), uy = Math.sin(ang);
            float[] lo = null, hi = null; double pl = 1e18, ph = -1e18;
            for (float[] p : best) { double t = (p[0] - cx) * ux + (p[1] - cy) * uy; if (t < pl) { pl = t; lo = p; } if (t > ph) { ph = t; hi = p; } }
            end = nn(best, lo) <= nn(best, hi) ? lo : hi;
        }
        double ex = end[0] - cx, ey = end[1] - cy, el = Math.sqrt(ex * ex + ey * ey);
        double tx = end[0] + (el > 1 ? ex / el * 1000 : 0), ty = end[1] + (el > 1 ? ey / el * 1000 : 0), tz = end[2] + 200;
        long pawn = Mods.myPawn();
        boolean ok = pawn >= 0x10000 && Veh.teleport(pawn, tx, ty, tz, Math.toDegrees(Math.atan2(cy - ty, cx - tx)));
        range = "射击训练区：附近靶子 " + n + " 个分 " + gs.size() + " 群，最大一群 " + best.size() + " 个（中心 " + (int) cx + "," + (int) cy + "）→ 传到 "
            + (int) tx + "," + (int) ty + "," + (int) tz + (ok ? "" : "（传送失败）") + "。" + cand + clusters(s.target);
        note(range);
        if (!ok) GameOverlay.toastAny("训练岛：传射击训练区失败");
    }
    static int root(int[] g, int i) { while (g[i] != i) i = g[i] = g[g[i]]; return i; }
    static double d2(float[] a, double[] b) { double x = a[0] - b[0], y = a[1] - b[1]; return x * x + y * y; }
    static double d2(float[] a, float[] b) { double x = a[0] - b[0], y = a[1] - b[1]; return x * x + y * y; }
    static double nn(ArrayList<float[]> g, float[] p) { double m = 1e18; for (float[] q : g) if (q != p) m = Math.min(m, d2(p, q)); return m; }
    /** 全图靶子按 50 米连群，列每群中心和个数（最多 12 群） */
    static String clusters(ArrayList<float[]> t) {
        int n = Math.min(t.size(), 400); if (n == 0) return "";
        int[] g = new int[n]; for (int i = 0; i < n; i++) g[i] = i;
        for (int i = 0; i < n; i++) for (int j = i + 1; j < n; j++) if (d2(t.get(i), t.get(j)) < 5000.0 * 5000.0) { int a = root(g, i), c = root(g, j); if (a != c) g[a] = c; }
        HashMap<Integer, double[]> c = new HashMap<>();
        for (int i = 0; i < n; i++) { double[] s = c.computeIfAbsent(root(g, i), k -> new double[4]); s[0] += t.get(i)[0]; s[1] += t.get(i)[1]; s[2] += t.get(i)[2]; s[3]++; }
        StringBuilder sb = new StringBuilder("；靶子群："); int cntG = 0;
        for (double[] s : c.values()) { if (cntG++ >= 12) { sb.append("…"); break; } sb.append((int) (s[0] / s[3])).append(',').append((int) (s[1] / s[3])).append(',').append((int) (s[2] / s[3])).append('×').append((int) s[3]).append(' '); }
        return sb.toString();
    }

    /** 分配完 20 秒后：原生有没有刷车/刷物资（只读） */
    static void spawnDiag(long b, Veh.Names nm, long gm) throws Exception {
        double[] me = Veh.info();
        Scan s = scan(b, nm);
        int vNear = 0, pNear = 0; double vMin = 1e18;
        if (me != null) {
            for (float[] p : s.veh) { double d = d2(p, me); if (d < 150000.0 * 150000.0) vNear++; vMin = Math.min(vMin, d); }
            for (float[] p : s.pick) if (d2(p, me) < 150000.0 * 150000.0) pNear++;
        }
        int a70, a21, bdc = -9;
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) { a70 = rb(m, gm + 0xa70); a21 = rb(m, gm + 0xa21); long pc = Veh.lastPc(); if (pc > 0x10000) bdc = (int) Server.rd(m, pc + 0xbdc); }
        spawn = "原生刷车/刷物资（分配后 20 秒）：车 " + s.veh.size() + " 辆（1.5 公里内 " + vNear + "，最近 " + (vMin > 1e17 ? "-" : (int) (Math.sqrt(vMin) / 100) + " 米") + "）；地上物资 " + s.pick.size() + "（1.5 公里内 " + pNear + "）；"
            + "刷车点 " + s.vsp + "，刷物资区 " + s.vol + "；系统：" + (s.sys.length() == 0 ? "无" : s.sys) + "；阶段 " + Bots.readPhase(gm) + "，+a70=" + a70 + " +a21=" + a21 + " PC+bdc=" + bdc;
        note(spawn);
    }

    static final class Scan {
        long mgr; int land, vsp, vol; String sys = "";
        ArrayList<String> psName = new ArrayList<>(); ArrayList<float[]> psPos = new ArrayList<>();
        ArrayList<float[]> target = new ArrayList<>(), veh = new ArrayList<>(), pick = new ArrayList<>();
    }
    /** 类型：0 不管，1 PlayerStart，2 地形块，3 地块管理器，4 靶子，5 车，6 地上物资，7 刷车点，8 刷物资区，9 刷车/物资系统 */
    static Scan scan(long b, Veh.Names nm) {
        Scan s = new Scan();
        StringBuilder sys = new StringBuilder();
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
            long g = Server.rd(m, b + 0x51250acL);
            long objs = Server.rd(m, g + 0x10), num = Server.rd(m, g + 0x18);
            if (objs < 0x10000 || num <= 0 || num > 0x200000) return s;
            byte[] raw = new byte[(int) num * 16];
            m.seek(objs); m.readFully(raw);
            ByteBuffer bb = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
            HashMap<Long, Integer> kind = new HashMap<>();
            for (int i = 0; i < num; i++) {
                long o = bb.getInt(i * 16) & 0xffffffffL; int fl = bb.getInt(i * 16 + 4);
                if (o < 0x10000 || (fl & 0x20000000) != 0) continue;
                long c = Server.rd(m, o + 0xc);
                if (c < 0x10000) continue;
                Integer k = kind.get(c);
                if (k == null) { k = classify(m, nm, c); kind.put(c, k); }
                if (k == 0) continue;
                if ((Server.rd(m, o + 4) & 0x10) != 0) continue;   // 类默认对象
                switch (k) {
                    case 1: { float[] p = pos(m, o); if (p != null) { s.psName.add(Alive.objName(m, nm, o)); s.psPos.add(p); } break; }
                    case 2: s.land++; break;
                    case 3: if (s.mgr == 0) s.mgr = o; break;
                    case 4: { float[] p = pos(m, o); if (p != null) s.target.add(p); break; }
                    case 5: { float[] p = pos(m, o); if (p != null) s.veh.add(p); break; }
                    case 6: { float[] p = pos(m, o); if (p != null) s.pick.add(p); break; }
                    case 7: s.vsp++; break;
                    case 8: s.vol++; break;
                    case 9: sys.append(Alive.clsName(m, nm, o)).append(' '); break;
                }
            }
        } catch (Throwable ignored) {}
        s.sys = sys.toString().trim();
        return s;
    }
    static float[] pos(RandomAccessFile m, long o) throws java.io.IOException {
        long root = Server.rd(m, o + 0x128); if (root < 0x10000) return null;
        return new float[] { Mods.f(m, root + 0x140), Mods.f(m, root + 0x144), Mods.f(m, root + 0x148) };
    }
    /** 按类和父类名（UStruct+0x58 父类链，+0x5c 深度；IsA 0x1082ce4 那种写法）分类 */
    static int classify(RandomAccessFile m, Veh.Names nm, long c) {
        String n = Alive.objName(m, nm, c);
        if (n.equals("PlayerStart")) return 1;
        if (n.equals("NZStreamingLevelsManagerActor_TileMode")) return 3;
        if (n.startsWith("LandscapeStreamingProxy") || n.equals("LandscapeProxy") || n.equals("Landscape")) return 2;
        if (n.contains("Comp") || n.contains("Statistic") || n.contains("Manager")) return 0;
        if (n.equals("SpawnVehicleSystemSystem") || n.equals("PickUpRefreshSystem")) return 9;
        String chain = chain(m, nm, c);
        if (chain.contains("|NZSceneItem_RangeTarget|")) return 4;
        if (chain.contains("|NZVehicleSpawnPoint|")) return 7;
        if (chain.contains("|NZBRPickUpRefreshTriggerVolume|") || n.contains("RefreshVolum")) return 8;
        if (chain.contains("|SpawnVehicleSystemSystem|") || chain.contains("|PickUpRefreshSystem|")) return 9;
        if (n.contains("SpawnPoint") || n.contains("System") || n.contains("Volum") || n.contains("Trigger")) return 0;
        if (chain.matches(".*\\|[A-Za-z_]*Vehicle\\|.*")) return 5;
        if (chain.contains("PickUp") || chain.contains("Pickup")) return 6;
        return 0;
    }
    static String chain(RandomAccessFile m, Veh.Names nm, long c) {
        StringBuilder sb = new StringBuilder("|");
        try {
            long arr = Server.rd(m, c + 0x58); int d = (int) Server.rd(m, c + 0x5c);
            if (arr < 0x10000 || d < 0 || d > 32) return "|" + Alive.objName(m, nm, c) + "|";
            for (int i = 0; i <= d; i++) { long e = Server.rd(m, arr + 4L * i); if (e < 0x10000) continue; sb.append(Alive.objName(m, nm, e - 0x58)).append('|'); }
        } catch (Throwable t) { return "|" + Alive.objName(m, nm, c) + "|"; }
        return sb.toString();
    }
}
