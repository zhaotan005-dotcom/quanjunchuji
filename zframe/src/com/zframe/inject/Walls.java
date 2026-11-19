package com.zframe.inject;

import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;

/** v3.19 单挑掩体 = 地图自己已加载的墙/集装箱/箱子模型（UStaticMesh），用游戏原生 AStaticMeshActor 刷出来，房主和加入的人都能看到、都挡得住。
 *  RE（libUE4 armv7，偏移都经 capstone 反汇编核对，见 notes/history.md v3.19）：
 *   - AStaticMeshActor::StaticClass 缓存 *(base+0x5389048)，类大小 0x2b8；构造 0x2d98764：StaticMeshComponent = actor+0x2b0（也是根组件 +0x128），
 *     组件 Mobility = comp+0x16e（构造里写 0 = Static），碰撞预设在构造里设好（vt+0x2f0）。
 *   - UStaticMesh 类缓存 *(base+0x538902c)，ExtendedBounds(FBoxSphereBounds) = mesh+0xbc：Origin xyz、BoxExtent xyz、SphereRadius（反射记录 0x503bedc）。
 *   - 生成：UWorld::SpawnActor(world, class, &FTransform, &FActorSpawnParameters) = 0x2aa756c（0x2aa8ad4 的 loc/rot 版本内部就是拼一个 FTransform 调它）。
 *     FTransform（这个包非向量化）：+0 FQuat xyzw、+0x10 位置、+0x1c 缩放，共 0x28；FActorSpawnParameters 0x20（构造 0x2e523bc：全 0、+0x1c=8），
 *     +0x18 碰撞处理 = 1（AlwaysSpawn），+0x19 = 2（bNoFail）。
 *   - 设模型：UStaticMeshComponent::SetStaticMesh = 组件虚表 +0x43c（0x2957578）；StaticMesh 字段 comp+0x628（反射 Net + OnRep_StaticMesh）。
 *     它在「已注册 + Mobility==Static」时拒绝（日志 "Calling SetStaticMesh on '{0}' but Mobility is Static."，0x29575fc 起三条指令）→ 服务端先把 comp+0x16e 写 2（Movable）。
 *   - 复制：AActor::SetReplicates 0x2747784（Role==3 且原来不复制时 AddNetworkActor 0x2e5c524）；UActorComponent::SetIsReplicated 0x28a40d8（comp+0x74 bit3）。
 *     USceneComponent 的 RelativeLocation/Rotation/Scale3D（+0x100/+0x10c/+0x118）带 Net 标记 + OnRep_Transform，StaticMesh 带 Net + OnRep_StaticMesh，
 *     所以组件复制后客户端拿到位置、缩放和模型。NetCullDistanceSquared = actor+0xf4（写 1e12，远处也同步）。
 *   - 客户端问题：客户端按类默认值生成的组件是 Static，OnRep_StaticMesh（0x295596c）→ SetStaticMesh 会被同一个 Static 检查拒掉 → 看不到模型。
 *     修：客户端（也装了本模组）把 0x29575fc..0x2957610 的 `ldrb r0,[r4,#0x16e]; cmp r0,#0; bne 0x29579e8; ldr r0,[pc,#0x490]; add r6,sp,#0x38`
 *     改成 `ldrb r0,[r4,#0x16e]; cmp r0,#0; moveq r0,#2; strbeq r0,[r4,#0x16e]; b 0x29579e8`：只把 Static 改成 Movable 再照常设模型，Stationary/Movable 不变。
 *     被盖掉的两条是警告分支的开头，查过 0x2940000..0x2970000 没有别的跳转进 0x2957600..0x2957614；0x29579e8 不用 r0。
 *     game/re/emu_meshpatch.py 用 unicorn 跑过（0/1/2 三种 Mobility）。一进图就打（房主也打，无害）。 */
public class Walls {
    static final long SPAWN_T = 0x2aa756cL, SET_REPL = 0x2747784L, SET_COMP_REPL = 0x28a40d8L;
    static final long SMA_CLS = 0x5389048L, SM_CLS = 0x538902cL;
    static final long PATCH = 0x29575fcL; static final String PATCH_O = "6e01d4e5000050e3f700001a90049fe538608de2", PATCH_N = "6e01d4e5000050e30200a0036e01c405f50000ea";
    static final float TOP = 330f, SINK = 120f;   // 地面以上至少 3.3 米（人 1.8 米、跳起来也打不过去）；往地下埋 1.2 米防坡地悬空

    static volatile String state = "围墙：没刷", meshInfo = "", patchState = "";
    static final ArrayList<Long> spawned = new ArrayList<>();
    static int ok, fail; static volatile boolean busy;

    static String patch() { return patchState = "客户端模型补丁 0x29575fc：" + Mods.write(PATCH, PATCH_O, PATCH_N, true); }

    static final class Mesh { long p; String name; float ox, oy, oz, ex, ey, ez; boolean crate;
        float h() { return 2 * ez; } float len() { return 2 * Math.max(ex, ey); } float thick() { return 2 * Math.min(ex, ey); }
        String desc() { return name + String.format(java.util.Locale.US, "（长%.0f 厚%.0f 高%.0f 厘米，中心偏移 %.0f,%.0f,%.0f）", len(), thick(), h(), ox, oy, oz); } }
    static Mesh[] walls = new Mesh[0], crates = new Mesh[0];

    static final String[] BAD = { "glass", "window", "door", "fx", "decal", "broken", "debris", "grass", "tree", "rope", "wire", "sign", "roof",
        "floor", "stair", "light", "lamp", "pipe", "cable", "frame", "rail", "ladder", "plant", "bush", "rock", "cloth", "flag", "car", "vehicle", "weapon", "lod1", "lod2" };
    static final String[] WALLK = { "wall", "fence", "concrete", "brick", "barrier", "container", "partition", "block" };
    static final String[] CRATEK = { "crate", "box", "container", "cargo" };
    static boolean has(String s, String[] k) { for (String x : k) if (s.contains(x)) return true; return false; }
    static boolean sane(float v) { return !Float.isNaN(v) && !Float.isInfinite(v) && Math.abs(v) < 100000f; }

    /** 扫 GUObjectArray 里已加载的 UStaticMesh，按名字关键字 + 包围盒挑墙和箱子（只读） */
    static String scan() {
        long b = Mods.base(); if (b == 0) return meshInfo = "找不到游戏库";
        ArrayList<Mesh> w = new ArrayList<>(), c = new ArrayList<>(); int total = 0, named = 0;
        StringBuilder all = new StringBuilder();
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
            long cls = Server.rd(m, b + SM_CLS); if (cls < 0x10000) return meshInfo = "UStaticMesh 类还没注册";
            Veh.Names nm = Veh.Names.get();
            long g = Server.rd(m, b + 0x51250acL); long objs = Server.rd(m, g + 0x10), n = Server.rd(m, g + 0x18);
            if (objs < 0x10000 || n <= 0 || n > 0x200000) return meshInfo = "对象表不对 " + n;
            byte[] blk = new byte[16 * 4096]; byte[] c4 = new byte[4]; byte[] bb7 = new byte[28];
            for (long i = 0; i < n; i += 4096) {
                int k = (int) Math.min(4096, n - i);
                m.seek(objs + i * 16); m.readFully(blk, 0, k * 16);
                ByteBuffer bb = ByteBuffer.wrap(blk).order(ByteOrder.LITTLE_ENDIAN);
                for (int j = 0; j < k; j++) {
                    long o = bb.getInt(j * 16) & 0xffffffffL; int fl = bb.getInt(j * 16 + 4);
                    if (o < 0x10000 || (fl & 0x20000000) != 0) continue;
                    try { m.seek(o + 0xc); m.readFully(c4); } catch (Throwable t) { continue; }
                    if ((ByteBuffer.wrap(c4).order(ByteOrder.LITTLE_ENDIAN).getInt() & 0xffffffffL) != cls) continue;
                    total++;
                    String name = Alive.objName(m, nm, o); String s = name.toLowerCase();
                    boolean isW = has(s, WALLK), isC = has(s, CRATEK);
                    if ((!isW && !isC) || has(s, BAD)) continue;
                    named++;
                    m.seek(o + 0xbc); m.readFully(bb7);
                    ByteBuffer f = ByteBuffer.wrap(bb7).order(ByteOrder.LITTLE_ENDIAN);
                    Mesh x = new Mesh(); x.p = o; x.name = name;
                    x.ox = f.getFloat(0); x.oy = f.getFloat(4); x.oz = f.getFloat(8); x.ex = f.getFloat(12); x.ey = f.getFloat(16); x.ez = f.getFloat(20);
                    if (!sane(x.ox) || !sane(x.oy) || !sane(x.oz) || !(x.ex > 1 && x.ey > 1 && x.ez > 1) || x.ex > 5000 || x.ey > 5000 || x.ez > 5000) continue;
                    if (all.length() < 2500) all.append("\n· ").append(x.desc());
                    float L = x.len(), T = x.thick(), H = x.h();
                    // 墙：长 2.5~25 米、厚 0.1~3 米、长是厚的 1.8 倍以上、高 1.5~8 米（高度不够的后面按 Z 放大，最多 3 倍）
                    if (isW && L >= 250 && L <= 2500 && T >= 10 && T <= 300 && L >= 1.8f * T && H >= 150 && H <= 800) w.add(x);
                    // 箱子：三边都 1~13 米、高 ≥1.5 米
                    else if (isC && L >= 100 && L <= 1300 && T >= 100 && H >= 150 && H <= 1300) { x.crate = true; c.add(x); }
                }
            }
        } catch (Throwable e) { return meshInfo = "扫模型出错 " + e; }
        // 偏好：不用放大就够高、长度 4~12 米
        java.util.Comparator<Mesh> score = (a1, a2) -> Float.compare(cost(a1), cost(a2));
        w.sort(score); c.sort(score);
        walls = w.subList(0, Math.min(4, w.size())).toArray(new Mesh[0]);
        crates = c.subList(0, Math.min(3, c.size())).toArray(new Mesh[0]);
        StringBuilder sb = new StringBuilder("模型：已加载 UStaticMesh " + total + " 个，名字像墙/箱 " + named + " 个，合格墙 " + w.size() + "、箱 " + c.size() + "。选用：");
        for (Mesh x : walls) sb.append("\n 墙 ").append(x.desc());
        for (Mesh x : crates) sb.append("\n 箱 ").append(x.desc());
        sb.append("\n候选（前若干）：").append(all);
        meshInfo = sb.toString(); LogCap.note("单挑围墙：" + meshInfo);
        return "ok";
    }
    static float cost(Mesh x) {
        float need = Math.max(1f, (TOP + SINK) / x.h());
        float lenPen = x.crate ? 0 : Math.abs(x.len() - 800) / 800f;
        return (need - 1) * 3 + lenPen + (x.h() > 600 ? 1 : 0);
    }

    static ByteBuffer wb; static long wa;
    /** 在 (cx,cy) 放一块：长轴朝 dirYaw（度），目标长度 targetLen（0 = 原长），底部埋到 groundZ-SINK、顶部至少到 groundZ+TOP。返回 actor 或 0 */
    static long place(long b, long world, Mesh ms, float cx, float cy, float groundZ, double dirYaw, float targetLen) throws Exception {
        boolean alongX = ms.ex >= ms.ey;
        float L0 = ms.len(), T0 = ms.thick();
        float sLen = targetLen <= 0 ? 1f : Math.max(0.6f, Math.min(3f, targetLen / L0));
        float sThick = T0 < 20 ? 20f / T0 : 1f;
        float sz = Math.max(1f, Math.min(3f, (TOP + SINK) / ms.h()));
        float Sx = alongX ? sLen : sThick, Sy = alongX ? sThick : sLen;
        double yaw = alongX ? dirYaw : dirYaw - 90;
        double r = Math.toRadians(yaw), cs = Math.cos(r), sn = Math.sin(r);
        float lx = ms.ox * Sx, ly = ms.oy * Sy;
        float ax = (float) (cx - (lx * cs - ly * sn)), ay = (float) (cy - (lx * sn + ly * cs));
        float az = groundZ - SINK - (ms.oz - ms.ez) * sz;
        if (wb == null) { wb = ByteBuffer.allocateDirect(256).order(ByteOrder.LITTLE_ENDIAN); wa = GameOverlay.bufAddr(wb); }
        if (wa <= 0 || wa > 0xffffffffL) throw new IllegalStateException("缓冲区地址不对");
        for (int o = 0; o < 0x60; o += 4) wb.putInt(o, 0);
        wb.putFloat(0x8, (float) Math.sin(r / 2)); wb.putFloat(0xc, (float) Math.cos(r / 2));   // FQuat(0,0,sin,cos) = 只转 yaw
        wb.putFloat(0x10, ax); wb.putFloat(0x14, ay); wb.putFloat(0x18, az);
        wb.putFloat(0x1c, Sx); wb.putFloat(0x20, Sy); wb.putFloat(0x24, sz);
        wb.put(0x40 + 0x18, (byte) 1); wb.put(0x40 + 0x19, (byte) 2); wb.putInt(0x40 + 0x1c, 8);
        long cls;
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) { cls = Server.rd(m, b + SMA_CLS); }
        if (cls < 0x10000) throw new IllegalStateException("AStaticMeshActor 类没注册");
        long[] rr = Veh.ucall5(world, cls, 0, 0, null, b + SPAWN_T, wa, wa + 0x40, 0);
        long actor = rr[0] == 0x5b0e ? rr[1] : 0;
        if (actor < 0x10000) return 0;
        synchronized (spawned) { spawned.add(actor); }   // v3.20：生成了就记下（后面哪一步没成也要能删掉，重开不漏）
        long comp;
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "rw")) {
            if (Server.rd(m, actor + 0xc) != cls) return 0;
            comp = Server.rd(m, actor + 0x2b0);
            if (comp < 0x10000 || !Alive.clsName(m, Veh.Names.get(), comp).contains("StaticMeshComponent")) return 0;
            m.seek(comp + 0x16e); m.write(new byte[] { 2 });             // Movable，否则 SetStaticMesh 拒绝
            Mods.wf(m, actor + 0xf4, 1e12f);                             // NetCullDistanceSquared
        }
        Veh.ucall(comp, ms.p, 0x43c, 0, null);                          // SetStaticMesh
        Veh.ucall(comp, 1, 0, 0, null, b + SET_COMP_REPL);              // 组件复制
        Veh.ucall(actor, 1, 0, 0, null, b + SET_REPL);                  // actor 复制
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
            boolean meshOk = Server.rd(m, comp + 0x628) == ms.p;
            byte[] x = new byte[1]; m.seek(actor + 0x5e); m.readFully(x); boolean rep = (x[0] & 8) != 0;
            boolean crep = (Server.rd(m, comp + 0x74) & 8) != 0;
            if (!meshOk || !rep || !crep) { LogCap.note("单挑围墙：一块没成 模型" + meshOk + " 复制" + rep + " 组件复制" + crep + " " + ms.name); return meshOk ? actor : 0; }
        }
        return actor;
    }

    static final class Arena { float ax, ay, az, cx, cy, cz, ux, uy, nx, ny, d, M, H; float gz(float u) { float t = d < 1 ? 0 : Math.max(0, Math.min(1, u / d)); return az + (cz - az) * t - 90f; }
        float wx(float u, float v) { return ax + ux * u + nx * v; } float wy(float u, float v) { return ay + uy * u + ny * v; }
        double yawU() { return Math.toDegrees(Math.atan2(uy, ux)); }
        /** 点在围墙里面（留 tol 厘米） */
        boolean inside(float x, float y, float tol) { float dx = x - ax, dy = y - ay; float u = dx * ux + dy * uy, v = dx * nx + dy * ny; return u >= -M - tol && u <= d + M + tol && Math.abs(v) <= H + tol; } }
    static volatile Arena arena;
    static Arena arena(float[] a, float[] c) {
        Arena r = new Arena(); r.ax = a[0]; r.ay = a[1]; r.az = a[2]; r.cx = c[0]; r.cy = c[1]; r.cz = c[2];
        float dx = c[0] - a[0], dy = c[1] - a[1]; r.d = (float) Math.hypot(dx, dy);
        if (r.d < 1) { r.ux = 1; r.uy = 0; } else { r.ux = dx / r.d; r.uy = dy / r.d; }
        r.nx = -r.uy; r.ny = r.ux; r.M = 1200f; r.H = Math.max(1200f, 0.35f * r.d);
        return r;
    }

    /** 刷整个场地：外圈围墙（矩形、无缝、四角压角） + 场内掩体（长短墙、L 形、交错、箱子）。在后台线程调。 */
    static void build(float[] a, float[] c, java.util.List<float[]> avoid) {
        if (busy) return; busy = true; ok = 0; fail = 0; snapped = 0; snapTried = 0;
        long t0 = System.currentTimeMillis();
        try {
            long b = Mods.base(); if (b == 0) { state = "围墙：找不到游戏库"; return; }
            patch();
            Arena A = arena(a, c); arena = A;
            if (A.d < 1500) { state = "围墙：两个单挑点太近（" + Math.round(A.d / 100) + " 米 < 15 米），不刷，靠看不见的圈"; LogCap.note(state); return; }
            String s = scan();
            if (!"ok".equals(s) || walls.length == 0) { state = "围墙：地图里没找到合格的墙模型（" + meshInfo + "），不刷，靠看不见的圈"; LogCap.note(state); return; }
            double[] q = Veh.info(); long world = q == null ? 0 : (long) q[5];
            if (world < 0x10000) { state = "围墙：拿不到 World"; LogCap.note(state); return; }
            Mesh W0 = walls[0];
            // ---- 外圈：u∈[-M, d+M]，v∈[-H, H]，每边分段，段长 ≤ 原长×3 且 ≤ 10 米，相邻段重叠 8%，四角各伸出一个墙厚 ----
            float segMax = Math.min(W0.len() * 3f, 1000f), th = Math.max(20f, W0.thick());
            float u0 = -A.M, u1 = A.d + A.M, v0 = -A.H, v1 = A.H;
            int perim = 0;
            perim += side(b, world, A, W0, u0 - th, v0, u1 + th, v0, segMax);
            perim += side(b, world, A, W0, u0 - th, v1, u1 + th, v1, segMax);
            perim += side(b, world, A, W0, u0, v0 - th, u0, v1 + th, segMax);
            perim += side(b, world, A, W0, u1, v0 - th, u1, v1 + th, segMax);
            // ---- 场内：离两个出生点 7 米内不放，不压到站着的人 ----
            float d = A.d, H = A.H;
            Mesh W1 = walls[Math.min(1, walls.length - 1)], W2 = walls[Math.min(2, walls.length - 1)];
            Mesh C0 = crates.length > 0 ? crates[0] : null, C1 = crates.length > 1 ? crates[1] : C0;
            Object[][] lay = {
                { C0, 0.5f * d, 0f, 0, 0f },                 // 中间箱子
                { W1, 0.5f * d, 600f, 1, 500f },             // 中线两侧短墙（竖）
                { W2, 0.5f * d, -650f, 1, 400f },
                { W0, 0.3f * d, 250f, 1, 500f },             // L 形（竖 + 横）
                { W1, 0.3f * d + 250f, 520f, 0, 450f },
                { W0, 0.7f * d, -250f, 1, 500f },            // 对面镜像 L 形
                { W1, 0.7f * d - 250f, -520f, 0, 450f },
                { W2, 0.18f * d, -420f, 0, 400f },           // 交错短墙
                { W2, 0.82f * d, 420f, 0, 400f },
                { C1, 0.4f * d, -0.6f * H, 0, 0f },          // 两侧箱子
                { C1, 0.6f * d, 0.6f * H, 0, 0f },
                { W1, 900f, 300f, 1, 350f },                 // 出生点前的掩体（各一块，偏一侧，能躲）
                { W1, d - 900f, -300f, 1, 350f },
                { W0, 0.5f * d, 0.72f * H, 0, 600f },        // 两边长墙
                { W0, 0.5f * d, -0.72f * H, 0, 600f } };
            int inner = 0, skipped = 0;
            for (Object[] e : lay) {
                Mesh ms = (Mesh) e[0]; if (ms == null) { skipped++; continue; }
                float u = (Float) e[1], v = (Float) e[2]; boolean perp = (Integer) e[3] == 1; float len = (Float) e[4];
                float half = (len > 0 ? len : ms.len()) / 2 + 100;
                float x = A.wx(u, v), y = A.wy(u, v);
                if (Math.hypot(x - a[0], y - a[1]) - half < 700 || Math.hypot(x - c[0], y - c[1]) - half < 700) { skipped++; continue; }
                boolean onPlayer = false;
                if (avoid != null) for (float[] p : avoid) if (p != null && Math.hypot(x - p[0], y - p[1]) - half < 150) onPlayer = true;
                if (onPlayer || !A.inside(x, y, -300)) { skipped++; continue; }
                double yaw = A.yawU() + (perp ? 90 : 0);
                if (place(b, world, ms, x, y, snap(A, u, x, y), yaw, len) != 0) { ok++; inner++; } else fail++;
            }
            state = "围墙：外圈 " + perim + " 块、场内 " + inner + " 块（跳过 " + skipped + "，失败 " + fail + "），场地 " + Math.round((A.d + 2 * A.M) / 100) + "×" + Math.round(2 * A.H / 100)
                + " 米，墙顶 ≥ 地面+" + Math.round(TOP / 100f * 10) / 10f + " 米，射线贴地 " + snapped + "/" + snapTried + " 块，用时 " + (System.currentTimeMillis() - t0) / 1000 + " 秒；" + patchState;
            LogCap.note("单挑" + state);
        } catch (Throwable e) { state = "围墙：出错 " + e; LogCap.note("单挑" + state); }
        finally { busy = false; }
    }
    /** 一条边：从 (ua,va) 到 (ub,vb) 均分 n 段，每段长度多 8% 重叠，不留缝 */
    static int side(long b, long world, Arena A, Mesh ms, float ua, float va, float ub, float vb, float segMax) throws Exception {
        float len = (float) Math.hypot(ub - ua, vb - va);
        int n = Math.max(1, (int) Math.ceil(len / (segMax * 0.92f)));
        float step = len / n, pieceLen = step * 1.08f;
        double yaw = A.yawU() + Math.toDegrees(Math.atan2(vb - va, ub - ua));
        int c = 0;
        for (int i = 0; i < n; i++) {
            float t = (i + 0.5f) / n, u = ua + (ub - ua) * t, v = va + (vb - va) * t;
            float x = A.wx(u, v), y = A.wy(u, v);
            if (place(b, world, ms, x, y, snap(A, u, x, y), yaw, pieceLen) != 0) { ok++; c++; } else fail++;
        }
        return c;
    }
    /** v3.21：每块墙中心往下打射线贴地（只认地形，和两点插值差 4 米以上就不信），射线不能用就照旧按两个出生点插值 */
    static volatile int snapped, snapTried;
    static float snap(Arena A, float u, float x, float y) {
        float f = A.gz(u); snapTried++;
        if (Trace.fn == 0 && !Trace.init()) return f;
        float g = Trace.ground(x, y, f + 3000, f - 3000);
        if (Float.isNaN(g) || Math.abs(g - f) > 400) return f;
        snapped++; return g;
    }
    static void reset() { synchronized (spawned) { spawned.clear(); } arena = null; ok = 0; state = "围墙：没刷"; }
    /** v3.20 再开一把：删掉这局刷过的墙（AActor::K2_DestroyActor 虚表 +0x164，游戏线程；服务端销毁复制的 actor，客户端跟着删），然后清状态 */
    static String destroyAll(long b) {
        ArrayList<Long> l; synchronized (spawned) { l = new ArrayList<>(spawned); spawned.clear(); }
        int done = 0, gone = 0;
        long cls = 0;
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) { cls = Server.rd(m, b + SMA_CLS); } catch (Throwable ignored) {}
        for (long a : l) {
            try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
                if (!Prop.alive(m, b, a) || Server.rd(m, a + 0xc) != cls) { gone++; continue; }
            } catch (Throwable t) { gone++; continue; }
            try { long[] r = Veh.ucall(a, 0, 0x164, 0, null); if (r[0] == 0x5b0e) done++; } catch (Throwable ignored) {}
        }
        reset();
        return "删墙 " + done + "/" + l.size() + "（已经没了 " + gone + "）";
    }

    // ---------------- 客户端：打补丁 + 看能不能看到房主刷的墙 ----------------
    static volatile Thread cw;
    static void clientWatch() {
        if (cw != null && cw.isAlive()) return;
        cw = new Thread(() -> {
            // 补丁要在房主的墙复制过来之前打上：一进图就打（房主自己也无害）
            LogCap.note("单挑围墙：进图 " + patch());
            long until = System.currentTimeMillis() + 15 * 60_000L; int last = -1;
            while (System.currentTimeMillis() < until && Con.inMatch) {
                try { Thread.sleep(15000); } catch (InterruptedException e) { return; }
                if (Lan.role != 2) continue;
                long b = Mods.base(); if (b == 0) continue;
                int n = 0, withMesh = 0;
                try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
                    long cls = Server.rd(m, b + SMA_CLS);
                    for (long o : Prop.scene(cls)) {
                        byte[] x = new byte[1]; m.seek(o + 0xe8); m.readFully(x);
                        if (x[0] != 2) continue;                       // 只认从房主复制来的（SimulatedProxy）
                        n++; long comp = Server.rd(m, o + 0x2b0); if (comp >= 0x10000 && Server.rd(m, comp + 0x628) >= 0x10000) withMesh++;
                    }
                } catch (Throwable t) { continue; }
                if (n != last) { last = n; LogCap.note("单挑围墙（客户端）：收到房主复制的墙 " + n + " 块，带模型 " + withMesh + " 块"); }
            }
        }, "zf-walls-client");
        cw.start();
    }
}
