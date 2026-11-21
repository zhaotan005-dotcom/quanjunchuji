package com.zframe.inject;

import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** v3.21 往下打射线找地面（单挑自动选场地、围墙贴地）。
 *  走游戏自己的 UKismetSystemLibrary::LineTraceSingle，用 UObject::ProcessEvent（虚表 +0x100）在游戏线程里调（Veh.ucall5 'U' 路径，r2 = 参数块），
 *  不加新 cave、不改代码字节。RE（capstone，libUE4 armv7，notes/history.md v3.21）：
 *   - ProcessEvent = 虚表 +0x100：RPC 包装 0xe66908 就是 `ldr r5,[vt,#0x100]; bl 0x1bd3414 (FindFunctionChecked); blx r5(this, func, &parms)`。
 *   - LineTraceSingle 的 FFunctionParams 0x4fd05c4：参数块 0xe8 字节、14 个属性（数组 0x4fd0588）：
 *     0 WorldContextObject、4 Start(FVector)、0x10 End、0x1c TraceChannel(字节)、0x1d bTraceComplex（bool setter 0xe4a2bc 写 +0x1d）、
 *     0x20 ActorsToIgnore(TArray)、0x2c DrawDebugType、0x30 OutHit(FHitResult)、0xb8 bIgnoreSelf（setter 0xf1e44c）、0xbc TraceColor、
 *     0xcc TraceHitColor、0xdc DrawTime、0xe0 ReturnValue（setter 0x24319ac）。
 *   - FHitResult（属性表 0x4f8d0xx~0x4f8d3xx）：+4 Time、+8 Distance、+0xc Location、+0x18 ImpactPoint、+0x24 Normal、+0x30 ImpactNormal、
 *     +0x3c TraceStart、+0x48 TraceEnd、+0x54 PenetrationDepth、+0x58 Item、+0x5c PhysMaterial、+0x64 Actor（TWeakObjectPtr：对象序号、序列号）、
 *     +0x6c Component、+0x80 FaceIndex。
 *   - UFunction 对象 / 类默认对象运行时从 GUObjectArray 按名字找（LineTraceSingle 的外层 = KismetSystemLibrary；Default__KismetSystemLibrary）。
 *  TraceChannel 0 = TraceTypeQuery1（Visibility），地形挡 Visibility。打到的 actor 类名含 Landscape = 地面，其他（房子、石头、树、我们刷的墙）= 不是地面。 */
final class Trace {
    static final int PE = 0x100, PSIZE = 0xe8, HIT = 0x30;
    static volatile long fn, cdo; static volatile String state = "地面射线：还没试";
    static volatile int calls, hits, fails;
    static ByteBuffer pb; static long pa; static long worldC, worldAt;

    static final class Hit { float x, y, z, nz = 1f; String actor = "", cls = "", comp = ""; boolean land; long obj; }

    /** 找 UFunction 和类默认对象（进程里只要找一次，都是常驻对象） */
    static volatile long failAt;
    static synchronized boolean init() {
        if (fn != 0 && cdo != 0) return true;
        if (failAt != 0 && System.currentTimeMillis() - failAt < 30000) return false;   // 找不到时 30 秒内不重扫对象表
        failAt = System.currentTimeMillis();
        long b = Mods.base(); if (b == 0) { state = "地面射线：找不到游戏库"; return false; }
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
            Veh.Names nm = Veh.Names.get();
            long g = Server.rd(m, b + 0x51250acL); long objs = Server.rd(m, g + 0x10), n = Server.rd(m, g + 0x18);
            if (objs < 0x10000 || n <= 0 || n > 0x200000) { state = "地面射线：对象表不对 " + n; return false; }
            byte[] blk = new byte[16 * 4096]; long f = 0, c = 0;
            for (long i = 0; i < n && (f == 0 || c == 0); i += 4096) {
                int k = (int) Math.min(4096, n - i);
                m.seek(objs + i * 16); m.readFully(blk, 0, k * 16);
                ByteBuffer bb = ByteBuffer.wrap(blk).order(ByteOrder.LITTLE_ENDIAN);
                for (int j = 0; j < k; j++) {
                    long o = bb.getInt(j * 16) & 0xffffffffL; if (o < 0x10000) continue;
                    String s; try { s = Alive.objName(m, nm, o); } catch (Throwable t) { continue; }
                    if (f == 0 && "LineTraceSingle".equals(s)) {
                        long outer = Server.rd(m, o + 0x18) & 0xffffffffL;
                        if ("KismetSystemLibrary".equals(Alive.objName(m, nm, outer)) && Alive.clsName(m, nm, o).equals("Function")) f = o;
                    } else if (c == 0 && "Default__KismetSystemLibrary".equals(s)) c = o;
                }
            }
            if (f == 0 || c == 0) { state = "地面射线：没找到 " + (f == 0 ? "LineTraceSingle 函数对象 " : "") + (c == 0 ? "KismetSystemLibrary 默认对象" : ""); return false; }
            fn = f; cdo = c; failAt = 0;
            state = "地面射线：函数 " + Long.toHexString(f) + "，默认对象 " + Long.toHexString(c);
            LogCap.note(state);
            return true;
        } catch (Throwable e) { state = "地面射线：找函数出错 " + e; return false; }
    }

    /** 从 (x,y,zTop) 往下打到 (x,y,zBot)。返回 null = 调用失败；hit.obj==0 && !land 且 z NaN = 没打到东西 */
    static synchronized Hit down(float x, float y, float zTop, float zBot) {
        if (!init()) return null;
        long world = worldC;
        if (world == 0 || System.currentTimeMillis() - worldAt > 10000) {   // World 每 10 秒问一次（Veh.info 要走一次游戏线程）
            double[] q; try { q = Veh.info(); } catch (InterruptedException e) { return null; }
            world = q == null ? 0 : (long) q[5]; worldC = world; worldAt = System.currentTimeMillis();
        }
        if (world < 0x10000) { state = "地面射线：拿不到 World"; return null; }
        if (pb == null) { pb = ByteBuffer.allocateDirect(0x100).order(ByteOrder.LITTLE_ENDIAN); pa = GameOverlay.bufAddr(pb); }
        if (pa <= 0 || pa > 0xffffffffL) { state = "地面射线：缓冲区地址不对"; return null; }
        for (int o = 0; o < 0x100; o += 4) pb.putInt(o, 0);
        pb.putInt(0, (int) world);
        pb.putFloat(4, x); pb.putFloat(8, y); pb.putFloat(12, zTop);
        pb.putFloat(0x10, x); pb.putFloat(0x14, y); pb.putFloat(0x18, zBot);
        // TraceChannel 0、bTraceComplex 0、ActorsToIgnore 空、DrawDebugType 0、bIgnoreSelf 0、颜色 0、DrawTime 0
        long[] r;
        try { r = Veh.ucall5(cdo, fn, PE, 0, null, 0, pa, 0, 0); } catch (InterruptedException e) { return null; }
        calls++;
        if (r[0] != 0x5b0e) { fails++; state = "地面射线：ProcessEvent 没反应（标记 " + Long.toHexString(r[0]) + "）"; return null; }
        Hit h = new Hit();
        boolean ret = (pb.get(0xe0) & 1) != 0;
        if (!ret) { h.z = Float.NaN; return h; }
        hits++;
        h.x = pb.getFloat(HIT + 0x18); h.y = pb.getFloat(HIT + 0x1c); h.z = pb.getFloat(HIT + 0x20);
        h.nz = pb.getFloat(HIT + 0x38);   // ImpactNormal.Z
        if (Float.isNaN(h.nz) || h.nz == 0f) h.nz = pb.getFloat(HIT + 0x2c);   // Normal.Z
        int idx = pb.getInt(HIT + 0x64), cidx = pb.getInt(HIT + 0x6c);
        long b = Mods.base();
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
            Veh.Names nm = Veh.Names.get();
            long g = Server.rd(m, b + 0x51250acL); long objs = Server.rd(m, g + 0x10), n = Server.rd(m, g + 0x18);
            if (idx > 0 && idx < n && objs >= 0x10000) {
                long o = Server.rd(m, objs + 16L * idx) & 0xffffffffL;
                if (o >= 0x10000) { h.obj = o; h.actor = Alive.objName(m, nm, o); h.cls = Alive.clsName(m, nm, o); }
            }
            if (cidx > 0 && cidx < n && objs >= 0x10000) {
                long o = Server.rd(m, objs + 16L * cidx) & 0xffffffffL;
                if (o >= 0x10000) h.comp = Alive.objName(m, nm, o);
            }
        } catch (Throwable ignored) {}
        h.land = h.cls.contains("Landscape") || h.actor.startsWith("Landscape");
        if (Math.abs(h.x - x) > 50 || Math.abs(h.y - y) > 50) { h.land = false; h.cls = h.cls + "(位置对不上 " + h.x + "," + h.y + ")"; }
        return h;
    }
    /** 地面高度（只认地形）；不是地形 / 没打到 / 调用失败 → NaN */
    static float ground(float x, float y, float zTop, float zBot) {
        Hit h = down(x, y, zTop, zBot);
        return h != null && h.land ? h.z : Float.NaN;
    }
    /** v3.63 标点传送用：人能站的面。从高处往下打，碰到树/草/叶子/体积/很陡的面（法线 Z < 0.6）就从它下面 3 厘米接着往下打，
     *  最多 6 层；第一层能站的（地形、房顶、石头、地板）就是落点。返回 null = 调用失败；z 是 NaN = 这一列什么都没打到（碰撞还没加载）。
     *  why 记每一层打到什么（诊断）。 */
    static Hit stand(float x, float y, StringBuilder why) {
        float top = 500000f; Hit last = null;
        for (int i = 0; i < 6; i++) {
            Hit h = down(x, y, top, -500000f);
            if (h == null) return null;
            if (Float.isNaN(h.z)) { if (why != null) why.append(i == 0 ? "什么都没打到" : " → 下面没有了"); break; }
            boolean bad = h.cls.contains("位置对不上");
            boolean soft = !h.land && (bad || softName(h.actor + "/" + h.cls + "/" + h.comp) || h.nz < 0.6f);
            if (why != null) why.append(i == 0 ? "" : " → ").append(String.format("%.0f %s/%s/%s 法线%.2f%s", h.z, h.cls, h.actor, h.comp, h.nz, soft ? "（跳过）" : ""));
            if (!soft) return h;
            last = h; top = h.z - 3f;
        }
        Hit n = new Hit(); n.z = Float.NaN; if (last != null) { n.actor = last.actor; n.cls = "只打到跳过的东西"; } return n;
    }
    static boolean softName(String s) {
        String w = s.toLowerCase().replace("street", "").replace("spine", "");
        for (String k : new String[] { "foliage", "tree", "bush", "leaf", "leaves", "plant", "grass", "branch", "pine", "birch", "fern", "shrub",
                "volume", "trigger", "blocking", "water", "ocean", "sky", "cloud", "camera" }) if (w.contains(k)) return true;
        return false;
    }
    static String text() { return state + "（调用 " + calls + " 次，打到 " + hits + "，失败 " + fails + "）"; }
}
