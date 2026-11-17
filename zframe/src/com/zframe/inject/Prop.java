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
            if (!zero && !java.util.Arrays.equals(cur, want)) return state = "空位被占";
            if (zero) { m.seek(b + CAVE); m.write(want); }
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
    static volatile long missAt; static volatile String lastMiss = "";
    static final java.util.regex.Pattern PATH = java.util.regex.Pattern.compile("(/Game/[^\\s'\"\\]\\)\\[,]+)");
    static void line(String l) {
        String kind = null;
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
}
