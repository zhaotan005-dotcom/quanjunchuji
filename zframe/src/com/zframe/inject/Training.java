package com.zframe.inject;

import java.io.File;
import java.io.RandomAccessFile;

/** v3.11 用户：要训练营。
 *  1) 原图：手机上挂了 Content/Maps/TrainingIsland_Runtime/ 这个 pak（日志 Success mount pak … pakchunk_c2afa52b…），
 *     但主关卡 BR-Training_Mobile_Main 不在（v1.12 open 报 does not exist）。不解密：只读 pak 末尾的明文索引（如果索引没加密），
 *     列出里面所有 .umap，存下来；大厅每个 umap 一个「训练营原图」按钮，用战术模式开。索引加密/格式不认 → 日志记末尾原始字节。
 *  2) 自制训练营：新海岛 + 战术模式 + 不上飞机不刷毒 + 物资箱 + 排车（Box）+ 玩家前方 20 米一排固定靶子人机（不带枪），靶子打完自动补。 */
final class Training {
    static volatile boolean on;            // 这局是自制训练营
    static volatile String state = "训练营：还没开";
    static volatile String pakState = "训练营原图：还没查";
    static final java.util.ArrayList<String> umaps = new java.util.ArrayList<>();
    static volatile boolean scanned;

    // ---------------- 1) 读 pak 明文索引 ----------------
    static void scan(android.content.Context c) {
        if (scanned || c == null) return; scanned = true;
        new Thread(() -> {
            StringBuilder lg = new StringBuilder("训练营 pak：");
            try {
                java.util.ArrayList<File> roots = new java.util.ArrayList<>();
                File ext = c.getExternalFilesDir(null); if (ext != null) roots.add(ext);
                File obb = c.getObbDir(); if (obb != null) roots.add(obb);
                roots.add(c.getFilesDir());
                File pak = null;
                for (File r : roots) { pak = find(r, 0); if (pak != null) break; }
                if (pak == null) { pakState = "训练营原图：手机上没找到 pakchunk_c2afa52b…pak（查了 " + roots + "）"; LogCap.note(pakState); return; }
                lg.append(pak.getAbsolutePath()).append("，").append(pak.length() / 1024).append(" KB；");
                read(pak, lg);
            } catch (Throwable t) { lg.append("出错 ").append(t); }
            pakState = umaps.isEmpty() ? "训练营原图：没读到关卡（看日志 pak 行）" : "训练营原图：" + umaps.size() + " 个关卡";
            LogCap.note(lg.toString());
            Lan.autoUp("pak");
            GameOverlay.refreshAny();
        }, "zf-train-pak").start();
    }
    static File find(File d, int depth) {
        if (d == null || depth > 7) return null;
        File[] fs = d.listFiles(); if (fs == null) return null;
        for (File f : fs) if (f.isFile() && f.getName().contains("c2afa52b") && f.getName().endsWith(".pak")) return f;
        for (File f : fs) if (f.isDirectory()) { File r = find(f, depth + 1); if (r != null) return r; }
        return null;
    }
    static void read(File pak, StringBuilder lg) throws Exception {
        try (RandomAccessFile f = new RandomAccessFile(pak, "r")) {
            long len = f.length();
            byte[] tail = new byte[64]; f.seek(len - 64); f.readFully(tail);
            java.nio.ByteBuffer t = java.nio.ByteBuffer.wrap(tail).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            int at = -1;
            for (int i = 0; i <= 60; i++) if (t.getInt(i) == 0x5A6F12E1) { at = i; }
            if (at < 0) { lg.append("末尾没有标准 pak 标记，末 64 字节 ").append(hex(tail)); return; }
            int ver = t.getInt(at + 4); long io = t.getLong(at + 8), is = t.getLong(at + 16);
            int enc = at > 0 ? tail[at - 1] : 0;
            lg.append("版本 ").append(ver).append(" 索引 @").append(io).append(" 长 ").append(is).append(" 加密标记 ").append(enc).append("；");
            if (io <= 0 || is <= 0 || is > 64L << 20 || io + is > len) { lg.append("索引位置不对，末 64 字节 ").append(hex(tail)); return; }
            byte[] idx = new byte[(int) is]; f.seek(io); f.readFully(idx);
            java.nio.ByteBuffer b = java.nio.ByteBuffer.wrap(idx).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            String mount = fstr(b);
            if (mount == null) { lg.append("索引读不出（可能加密），开头 ").append(hex(java.util.Arrays.copyOf(idx, 48))); return; }
            int n = b.getInt();
            lg.append("挂载 ").append(mount).append("，").append(n).append(" 个文件；");
            if (n < 0 || n > 200000) { lg.append("文件数不对"); return; }
            java.util.ArrayList<String> all = new java.util.ArrayList<>();
            for (int i = 0; i < n; i++) {
                String name = fstr(b); if (name == null) { lg.append("第 ").append(i).append(" 个名字读不出，停"); break; }
                all.add(name);
                b.getLong(); b.getLong(); b.getLong();     // Offset Size UncompressedSize
                int cm = b.getInt(); b.position(b.position() + 20);   // CompressionMethod + Hash
                if (ver >= 3) { if (cm != 0) { int nb = b.getInt(); b.position(b.position() + nb * 16); } b.get(); b.getInt(); }
            }
            String mp = mount.replace("../../../PUBGM/Content/", "/Game/");
            synchronized (umaps) {
                umaps.clear();
                for (String s : all) if (s.endsWith(".umap")) umaps.add(mp + s.substring(0, s.length() - 5));
            }
            lg.append("关卡 ").append(umaps).append("；全部文件（前 120）：");
            for (int i = 0; i < all.size() && i < 120; i++) lg.append(i == 0 ? "" : " | ").append(all.get(i));
        }
    }
    static String fstr(java.nio.ByteBuffer b) {
        if (b.remaining() < 4) return null;
        int l = b.getInt();
        if (l > 0 && l < 1024 && b.remaining() >= l) { byte[] x = new byte[l]; b.get(x); return new String(x, 0, Math.max(0, l - 1), java.nio.charset.StandardCharsets.UTF_8); }
        if (l < 0 && l > -1024 && b.remaining() >= -2 * l) { byte[] x = new byte[-2 * l]; b.get(x); return new String(x, 0, Math.max(0, -2 * l - 2), java.nio.charset.StandardCharsets.UTF_16LE); }
        return null;
    }
    static String hex(byte[] x) { StringBuilder s = new StringBuilder(); for (byte v : x) s.append(String.format("%02x", v & 0xff)); return s.toString(); }

    // ---------------- 2) 自制训练营 ----------------
    static final String RENEW = "/Game/Maps/ErangeMobileRenew_Runtime/BR-ErangelRenew_Mobile_Main";
    static String open(String map, String label) {
        on = true; done = false; synchronized (pawns) { pawns.clear(); }
        Mods.setNoZone();          // 不上飞机、不刷毒（存 prefs）
        Box.on = true;
        state = "训练营：开「" + label + "」";
        return ModeProbe.host("Props", label, map);
    }
    static void off() { on = false; }
    static volatile boolean done; static volatile long lastFill;
    static final int TARGETS = 8;
    static final java.util.ArrayList<Long> pawns = new java.util.ArrayList<>();
    static long pawnOf(long ctl) {
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) { long p = Server.rd(m, ctl + 0x2c0); if (p >= 0x10000) return p; } catch (Throwable ignored) {}
        return Veh.buf.getInt(96) & 0xffffffffL;
    }
    /** 还活着的靶子：对象还在（Prop.alive）且血 Health(+0x5a0，见 Crew) > 0 */
    static int aliveTargets() {
        int n = 0; long b = Mods.base();
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
            synchronized (pawns) {
                java.util.Iterator<Long> it = pawns.iterator();
                while (it.hasNext()) { long p = it.next(); if (p < 0x10000 || !Prop.alive(m, b, p) || !(Mods.f(m, p + 0x5a0) > 0f)) it.remove(); else n++; }
            }
        } catch (Throwable ignored) {}
        return n;
    }
    /** HostMatch 每 1.5 秒调：物资箱放完后在加入玩家前方 20 米放一排靶子；每 15 秒看一次，靶子不够就补 */
    static void tick() {
        if (!on || !Box.done || Box.busy) return;
        long now = System.currentTimeMillis();
        if (done && now - lastFill < 15000) return;
        lastFill = now;
        try {
            float[] pl = Box.joinedPos(Mods.base(), Veh.Names.get()); if (pl == null) return;
            int alive = aliveTargets();
            int need = TARGETS - Math.max(0, alive);
            if (need <= 0) { done = true; return; }
            int ok = 0;
            for (int i = 0; i < need; i++) {
                int k = (alive + i) % TARGETS;
                int c = Veh.addBot(1, pl[0] - 1050f + 300f * k, pl[1] + 2000f, pl[2] + 50f, 270f, 4);   // 不带枪、面朝玩家
                if (c == 0x5b08 || c == 0x5b07) { long ctl = Veh.lastCtrl(); Bots.manual(ctl, true); synchronized (pawns) { pawns.add(pawnOf(ctl)); } ok++; }
                Thread.sleep(300);
            }
            done = true;
            state = "训练营：靶子 " + (Math.max(0, alive) + ok) + "/" + TARGETS + "（这次补 " + ok + "）";
            LogCap.note(state);
        } catch (Throwable t) { state = "训练营：放靶子出错 " + t; LogCap.note(state); }
    }
}
