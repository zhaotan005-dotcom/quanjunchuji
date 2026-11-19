package com.zframe.inject;

import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;

/** v3.21 用户：不要让用户记录任何地点，程序自己选单挑场地；服务端只开房什么都不做。
 *  选法（服务端，单挑线程外的后台线程）：
 *   1. 候选点 = 这张图已加载的地形块（类名含 LandscapeHeightfieldCollisionComponent，没有就用 LandscapeComponent）的位置：一定在地图的地面上。
 *      地图范围 = 这些地形块的外框，四边各去掉 8%；离玩家现在的位置（出生岛）800 米内的不要。海面高度 = 名字以 sea_ 或 Ocean 开头 的 actor 的最低 Z。
 *   2. 每个候选（随机打乱、在地形块里再随机偏一点）：先在中心往下打一条射线（Trace.down，游戏自己的 LineTraceSingle），要打到地形（不是房子/石头/树），
 *      且比海面高 1.5 米以上（不在水里）。
 *   3. 场地 = 两个出生点相距 50 米，方向随机；围墙矩形 u∈[-12, 62] 米、v∈[-17.5, 17.5] 米。在矩形里 5×3 个点往下打：全部要打到地形，
 *      高度差（最高-最低）≤ 1.5 米；外面一圈 8 个点（矩形外扩 20 米）：最多 1 个不是地形（房子/树/石头），地形高度和场内平均差 ≤ 4 米（不在坡/崖边）。
 *   4. 第一个全部过的就用；同一个进程里同一张图记住（再开一把 / 下一局还用它，围墙刷失败了才换）。最多试 60 个候选、90 秒。
 *   5. 一个都没有（射线不能用、没找到地形块、全被拒）→ 用第一个加入玩家现在站的地方（已经落地站稳），沿随机方向 ±25 米摆两个点，日志写原因。
 *  出生点 Z = 地面 + 90 厘米（人物中心，和以前「落地记点」读到的人物 Z 一样，Walls 用 Z-90 当地面）。 */
final class DuelArena {
    static final float D = 5000f, M = 1200f, H = 1750f, SPREAD = 150f;
    static volatile String state = "场地：还没选";
    static final java.util.HashMap<String, float[][]> session = new java.util.HashMap<>();

    static final class Tile { float x, y, z; }

    /** 返回 {A, C}（人物中心坐标）；fallback = 第一个玩家现在的位置（人物中心），players = 所有在场玩家位置（排除出生岛） */
    static float[][] choose(String key, float[] fallback, java.util.List<float[]> players) {
        long t0 = System.currentTimeMillis();
        float[][] keep; synchronized (session) { keep = session.get(key); }
        if (keep != null) {
            float g = Trace.ground(keep[0][0], keep[0][1], keep[0][2] + 3000, keep[0][2] - 3000);
            if (!Float.isNaN(g) || !Trace.init()) { state = "场地：自动选（沿用这次开服选好的场地）" + where(keep); LogCap.note("单挑" + state); return keep; }
            synchronized (session) { session.remove(key); }
        }
        StringBuilder why = new StringBuilder();
        java.util.TreeMap<String, Integer> rej = new java.util.TreeMap<>();
        ArrayList<Tile> tiles = new ArrayList<>(); float[] sea = { Float.NaN };
        String ts = tiles(tiles, sea);
        why.append(ts);
        float[][] got = null;
        if (!tiles.isEmpty() && Trace.init()) {
            float x0 = Float.MAX_VALUE, x1 = -Float.MAX_VALUE, y0 = Float.MAX_VALUE, y1 = -Float.MAX_VALUE;
            for (Tile t : tiles) { x0 = Math.min(x0, t.x); x1 = Math.max(x1, t.x); y0 = Math.min(y0, t.y); y1 = Math.max(y1, t.y); }
            float mx = (x1 - x0) * 0.08f, my = (y1 - y0) * 0.08f;
            ArrayList<Tile> ok = new ArrayList<>();
            for (Tile t : tiles) {
                if (t.x < x0 + mx || t.x > x1 - mx || t.y < y0 + my || t.y > y1 - my) continue;
                boolean near = false; for (float[] p : players) if (p != null && Math.hypot(t.x - p[0], t.y - p[1]) < 80000) near = true;
                if (!near) ok.add(t);
            }
            why.append("，地图范围 ").append(Math.round((x1 - x0) / 100)).append("×").append(Math.round((y1 - y0) / 100)).append(" 米，可用候选 ").append(ok.size());
            java.util.Collections.shuffle(ok);
            java.util.Random rnd = new java.util.Random();
            float seaZ = Float.isNaN(sea[0]) ? -1e9f : sea[0];
            int tried = 0;
            for (Tile t : ok) {
                if (tried >= 60 || System.currentTimeMillis() - t0 > 90000) break;
                tried++;
                float cx = t.x + (rnd.nextFloat() - 0.5f) * 8000f, cy = t.y + (rnd.nextFloat() - 0.5f) * 8000f;
                float top = Math.max(t.z, 0) + 150000f, bot = t.z - 150000f;
                Trace.Hit h0 = Trace.down(cx, cy, top, bot);
                if (h0 == null) { rej.merge("射线调用失败", 1, Integer::sum); if (Trace.fails > 3 && Trace.hits == 0) break; continue; }
                if (Float.isNaN(h0.z)) { rej.merge("没打到东西", 1, Integer::sum); continue; }
                if (!h0.land) { rej.merge("中心打到 " + shortCls(h0.cls), 1, Integer::sum); continue; }
                if (h0.z < seaZ + 150) { rej.merge("水里/海边", 1, Integer::sum); continue; }
                double yaw = rnd.nextDouble() * Math.PI * 2; float ux = (float) Math.cos(yaw), uy = (float) Math.sin(yaw), nx = -uy, ny = ux;
                // 场地中心在 (cx,cy)，A 在 u=0（中心往回 25 米）
                float ax = cx - ux * D / 2, ay = cy - uy * D / 2;
                float zTop = h0.z + 6000, zBot = h0.z - 6000;
                float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE, sum = 0; int n = 0; String bad = null; float za = Float.NaN, zc = Float.NaN;
                float[] us = { -M, 0, D / 2, D, D + M }, vs = { -H, 0, H };
                outer:
                for (float uu : us) for (float vv : vs) {
                    float px = ax + ux * uu + nx * vv, py = ay + uy * uu + ny * vv;
                    Trace.Hit h = Trace.down(px, py, zTop, zBot);
                    if (h == null) { bad = "射线调用失败"; break outer; }
                    if (Float.isNaN(h.z)) { bad = "场内没打到"; break outer; }
                    if (!h.land) { bad = "场内有 " + shortCls(h.cls); break outer; }
                    if (h.z < seaZ + 150) { bad = "场内有水"; break outer; }
                    lo = Math.min(lo, h.z); hi = Math.max(hi, h.z); sum += h.z; n++;
                    if (hi - lo > SPREAD) { bad = "不平（高差 > " + Math.round(SPREAD) + " 厘米）"; break outer; }
                    if (vv == 0 && uu == 0) za = h.z; if (vv == 0 && uu == D) zc = h.z;
                }
                if (bad != null) { rej.merge(bad.startsWith("场内有 ") ? "场内有房子/石头/树等" : bad, 1, Integer::sum); if (bad.startsWith("场内有 ")) rej.merge("  " + bad, 1, Integer::sum); continue; }
                float mean = sum / n; int notLand = 0; String ringBad = null;
                for (int k = 0; k < 8 && ringBad == null; k++) {
                    double a = Math.PI * 2 * k / 8; float ru = (float) (D / 2 + Math.cos(a) * (D / 2 + M + 2000)), rv = (float) (Math.sin(a) * (H + 2000));
                    float px = ax + ux * ru + nx * rv, py = ay + uy * ru + ny * rv;
                    Trace.Hit h = Trace.down(px, py, zTop + 3000, zBot - 3000);
                    if (h == null) { ringBad = "射线调用失败"; break; }
                    if (Float.isNaN(h.z)) continue;
                    if (!h.land) { if (++notLand > 1) ringBad = "外圈有房子/石头/树等"; continue; }
                    if (Math.abs(h.z - mean) > 400) ringBad = "外圈是坡/崖（高差 > 4 米）";
                    if (h.z < seaZ + 150) ringBad = "外圈有水";
                }
                if (ringBad != null) { rej.merge(ringBad, 1, Integer::sum); continue; }
                got = new float[][] { { ax, ay, za + 90f }, { ax + ux * D, ay + uy * D, zc + 90f } };
                why.append("，第 ").append(tried).append(" 个候选通过：中心 ").append(Math.round(cx)).append(",").append(Math.round(cy)).append(",").append(Math.round(h0.z))
                   .append("，场内 15 点高差 ").append(Math.round(hi - lo)).append(" 厘米，外圈非地形 ").append(notLand).append(" 点");
                break;
            }
            why.append("，试了 ").append(tried).append(" 个");
        } else if (tiles.isEmpty()) why.append("，没有地形块可选");
        else why.append("，").append(Trace.state);
        if (!rej.isEmpty()) why.append("，拒绝原因 ").append(rej);
        why.append("，射线 ").append(Trace.calls).append(" 次，用时 ").append((System.currentTimeMillis() - t0) / 1000).append(" 秒");
        if (got != null) {
            synchronized (session) { session.put(key, got); }
            state = "场地：自动选 " + where(got);
        } else if (fallback != null) {
            double yaw = new java.util.Random().nextDouble() * Math.PI * 2; float ux = (float) Math.cos(yaw), uy = (float) Math.sin(yaw);
            float[] a = { fallback[0] - ux * D / 2, fallback[1] - uy * D / 2, fallback[2] }, c = { fallback[0] + ux * D / 2, fallback[1] + uy * D / 2, fallback[2] };
            for (float[] p : new float[][] { a, c }) { float g = Trace.ground(p[0], p[1], fallback[2] + 3000, fallback[2] - 3000); if (!Float.isNaN(g)) p[2] = g + 90f; }
            got = new float[][] { a, c };
            state = "场地：没找到合格的地方，用玩家 1 现在站的地方 " + where(got);
        } else state = "场地：没找到合格的地方，也没有玩家位置可用";
        LogCap.note("单挑" + state + "。" + why);
        return got;
    }
    static String shortCls(String c) { return c == null || c.isEmpty() ? "?" : c.length() > 40 ? c.substring(0, 40) : c; }
    static String where(float[][] s) { return Math.round((s[0][0] + s[1][0]) / 2) + "," + Math.round((s[0][1] + s[1][1]) / 2) + "（两点相距 " + Math.round(Math.hypot(s[0][0] - s[1][0], s[0][1] - s[1][1]) / 100) + " 米，高 " + Math.round(s[0][2] - 90) + "/" + Math.round(s[1][2] - 90) + "）"; }
    static void forget(String key) { synchronized (session) { session.remove(key); } }

    /** 扫 GUObjectArray：地形块位置、海面高度 */
    static String tiles(ArrayList<Tile> out, float[] sea) {
        long b = Mods.base(); if (b == 0) return "找不到游戏库";
        int coll = 0, comp = 0, seas = 0; ArrayList<Tile> alt = new ArrayList<>();
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
            Veh.Names nm = Veh.Names.get();
            long g = Server.rd(m, b + 0x51250acL); long objs = Server.rd(m, g + 0x10), n = Server.rd(m, g + 0x18);
            if (objs < 0x10000 || n <= 0 || n > 0x200000) return "对象表不对 " + n;
            java.util.HashMap<Long, Integer> kind = new java.util.HashMap<>();
            byte[] blk = new byte[16 * 4096];
            for (long i = 0; i < n; i += 4096) {
                int k = (int) Math.min(4096, n - i);
                m.seek(objs + i * 16); m.readFully(blk, 0, k * 16);
                ByteBuffer bb = ByteBuffer.wrap(blk).order(ByteOrder.LITTLE_ENDIAN);
                for (int j = 0; j < k; j++) {
                    long o = bb.getInt(j * 16) & 0xffffffffL; int fl = bb.getInt(j * 16 + 4);
                    if (o < 0x10000 || (fl & 0x20000000) != 0) continue;
                    long cls; try { cls = Server.rd(m, o + 0xc) & 0xffffffffL; } catch (Throwable t) { continue; }
                    if (cls < 0x10000) continue;
                    Integer kd = kind.get(cls);
                    if (kd == null) {
                        String cn = Alive.objName(m, nm, cls);
                        String lc = cn.toLowerCase();
                        kd = cn.contains("LandscapeHeightfieldCollisionComponent") ? 1 : cn.equals("LandscapeComponent") ? 2
                           : (lc.contains("staticmeshactor") || lc.contains("water") || lc.contains("ocean") || lc.contains("sea")) && !lc.contains("component") ? 3 : 0;
                        kind.put(cls, kd);
                    }
                    try {
                        if ((Server.rd(m, o + 4) & 0x10) != 0) continue;   // 类默认对象
                        if (kd == 1 || kd == 2) {
                            float x = Mods.f(m, o + 0x140), y = Mods.f(m, o + 0x144), z = Mods.f(m, o + 0x148);
                            if (Float.isNaN(x) || Float.isNaN(y) || Float.isNaN(z) || Math.abs(x) > 2e6 || Math.abs(y) > 2e6 || Math.abs(z) > 1e6) continue;
                            Tile t = new Tile(); t.x = x; t.y = y; t.z = z;
                            if (kd == 1) { coll++; if (out.size() < 20000) out.add(t); } else { comp++; if (alt.size() < 20000) alt.add(t); }
                        } else if (kd == 3) {
                            String on = Alive.objName(m, nm, o); String l = on.toLowerCase();
                            if (l.startsWith("sea_") || l.startsWith("ocean")) {
                                long root = Server.rd(m, o + 0x128) & 0xffffffffL; if (root < 0x10000) continue;
                                float z = Mods.f(m, root + 0x148);
                                if (!Float.isNaN(z) && Math.abs(z) < 1e6) { seas++; sea[0] = Float.isNaN(sea[0]) ? z : Math.min(sea[0], z); }
                            }
                        }
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable e) { return "扫地形出错 " + e; }
        if (out.isEmpty()) out.addAll(alt);
        return "地形碰撞块 " + coll + "、地形块 " + comp + "，海面 actor " + seas + (Float.isNaN(sea[0]) ? "" : "（Z " + Math.round(sea[0]) + "）");
    }
}
