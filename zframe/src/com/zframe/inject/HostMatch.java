package com.zframe.inject;

import java.io.RandomAccessFile;

/** v3.03 服务端（房主，Lan.role==1）开局做两件事：
 *  1. 用户：战术道具开局让服务端给每个人刷每样 999 个。
 *     做法：游戏线程里（Veh.ucall 的 'U' cave）直接调刷物品补丁 0x17169c0(控制器, FString*)，FString = "P<编号>x999"。
 *     补丁从控制器 +0x2c0 取人物，所以传加入玩家的控制器 = 服务器直接放进他的背包（服务器权威，游戏自己同步给他），不是刷在地上。
 *     道具编号 = 道具表扫到的 402 类（去掉坏编号）。每局每个控制器只给一次。
 *  2. 用户：删掉服务端显示的那个人物 → 房主自己的人物 SetActorHiddenInGame(true)（AActor vt+0x160，thunk 0x2e70d40 里读到），
 *     bHidden 会复制，加入的玩家也看不到。不关碰撞（关了会掉出地图死掉，影响结算）。可在联机页关掉。 */
final class HostMatch {
    static final long GIVE_CAVE = 0x17169c0L;
    static volatile String state = "开局发道具：还没开局";
    static volatile boolean hideHost = true;
    static final java.util.HashSet<Long> given = new java.util.HashSet<>();
    static volatile long hidden;
    static java.nio.ByteBuffer sb; static long sbAddr;
    static volatile boolean running;
    static void newMatch() { synchronized (given) { given.clear(); } hidden = 0; ghost = 0; Box.newMatch(); Training.done = false; Duel.reset(); synchronized (Training.pawns) { Training.pawns.clear(); } }
    /** v3.05 用户：要单排，新加入的玩家各自一队，不要和房主是队友。
     *  RE：SetupTestTeam 0x100b2b0（没有专用服分组信息时给玩家分队）：GM+0x998 = 当前队已进人数，为 0 就新建一个 NZBRTeamInfo；
     *  进人后 +0x998 加 1，>= GM+0x8ed（MaxPlayerCountPerTeam，字节）就清 0。所以把 MaxPlayerCountPerTeam 改 1、+0x998 清 0，
     *  之后每个加入的人都新建一队（游戏自己的分队，不是事后改 TeamIndex）。对已经进来的人不改。 */
    static volatile String soloState = "单排：还没开局";
    static void solo() {
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "rw")) {
            double[] q = Veh.info(); long gm = q != null ? (long) q[4] : 0; if (gm < 0x10000) return;
            int teams = (int) Server.rd(m, gm + 0x8cc); if (teams < 0 || teams > 200) { soloState = "单排：GameMode 队伍数不对（" + teams + "），没改"; return; }
            byte[] one = new byte[1]; m.seek(gm + 0x8ed); m.readFully(one);
            long cur = Server.rd(m, gm + 0x998);
            if (one[0] != 1) { m.seek(gm + 0x8ed); m.write(new byte[]{1}); }
            if (cur != 0 && cur < 100) { m.seek(gm + 0x998); m.write(new byte[]{0, 0, 0, 0}); }
            soloState = "单排：每队 1 人（原来每队 " + (one[0] & 0xff) + "），现有队伍 " + teams + "；" + noEnd(m, hideHost || Duel.on);   // v3.20：单挑开着时一定不让游戏自己结算（打完一把接着再开）
        } catch (Throwable t) { soloState = "单排：出错 " + t; }
    }
    /** v3.05 用户：服务端的人物实际还在（只是隐藏）→ 要删掉。直接销毁会让「活着的队」只剩加入的人，一个人加入时会立刻结算；
     *  所以做成「不存在」：隐藏 + 关碰撞（游戏自己的 SetActorEnableCollision 0x2740af0，打不到、挡不住人）+ 时间冻结（CustomTimeDilation +0x58 = 0，不会掉下去）+ 血一直满。 */
    /** v3.05.1 用户：单排后死了不能重进。日志：OnPlayerKilled AliveTeamCount[1] → start to kick all players → 踢掉。
     *  原因：房主人物（隐藏的）自己算一队，加入的人死了只剩这一队 → 查结束 0x100fbd4 里 vt+0x634（活着的队伍数）<= 1 就结算踢人。
     *  去掉房主人物时：0x100fc60 cmp r0,#1 → cmp r0,#0（只在一个队都不剩时才结算），死了还能重进；关开关就改回。 */
    static final long END_CMP = 0x100fc60L; static final int CMP1 = 0xe3500001, CMP0 = 0xe3500000;
    static String noEnd(RandomAccessFile m, boolean on) throws java.io.IOException {
        long b = Mods.base(); if (b == 0) return "不结算：没基址";
        m.seek(b + END_CMP); int cur = Integer.reverseBytes(m.readInt());
        if (cur != CMP1 && cur != CMP0) return "不结算：代码和预期不一样 " + Integer.toHexString(cur);
        int want = on ? CMP0 : CMP1;
        if (cur != want) { m.seek(b + END_CMP); m.writeInt(Integer.reverseBytes(want)); }
        return on ? "只剩房主那队不结算（死了能重进）" : "结算照原样";
    }
    static final long COLLISION = 0x2740af0L;
    static volatile long ghost;
    static void ghost() throws Exception {
        if (!hideHost) return;
        long p = Mods.myPawn(); if (p < 0x10000) return;
        long b = Mods.base(); if (b == 0) return;
        if (Box.busy) { /* 空投放完再冻住（放空投要挪这个人物） */ return; }
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "rw")) {
            if (!Mods.isObj(m, b, p)) return;
            Mods.wf(m, p + 0x58, 0f);
            float hp = Mods.f(m, p + 0x5a0), mx = Mods.f(m, p + 0x5a4);
            if (mx > 1f && mx < 1000f && hp > 0f && hp < mx) Mods.wf(m, p + 0x5a0, mx);
        }
        if (ghost != p) {
            long[] r = Veh.ucall(p, 0, 0, 0, null, b + COLLISION);
            if (r[0] == 0x5b0e) { ghost = p; LogCap.note("房主人物：隐藏 + 关碰撞 + 冻结 " + Long.toHexString(p)); }
        }
    }
    static void start() {
        if (running) return; running = true;
        new Thread(() -> {
            while (true) {
                try { Thread.sleep(1500); } catch (InterruptedException e) { return; }
                if (Lan.role != 1 || !Con.inMatch) continue;
                solo();
                try { hide(); } catch (Throwable t) { LogCap.note("藏房主人物出错 " + t); }
                // v3.21 用户：删掉出生岛空投（Box 开局放物资堆、排车不再跑）
                // v3.47 用户：海岛服务端自动刷东西，用真空投箱（Box.isle：老/新海岛、不在单挑/训练营；不再放地上堆）
                if (Box.isle()) Box.tick();
                try { ghost(); } catch (Throwable t) { LogCap.note("房主人物冻结出错 " + t); }
                Training.tick();
                // v3.10 用户：道具只刷在玩家脚下（物资箱里每种 67），不再直接塞进背包（塞满后游戏会把多的丢出来）
                state = "开局发道具：改成放进玩家身边的物资箱（Box）";
            }
        }, "zf-hostmatch").start();
    }
    static void hide() throws Exception {
        if (!hideHost) return;
        long p = Mods.myPawn(); if (p == 0 || p == hidden) return;
        long b = Mods.base(); if (b == 0) return;
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) { if (!Mods.isObj(m, b, p)) return; }
        long[] r = Veh.ucall(p, 1, 0x160, 0, null);
        if (r[0] == 0x5b0e) { hidden = p; LogCap.note("房主人物已隐藏 " + Long.toHexString(p)); }
    }
    static java.util.ArrayList<Long> propIds() {
        java.util.TreeMap<Long, java.util.ArrayList<Long>> m = GameOverlay.items;
        if (m == null) { GameOverlay.loadItems(false); return null; }
        java.util.ArrayList<Long> l = new java.util.ArrayList<>();
        for (java.util.Map.Entry<Long, java.util.ArrayList<Long>> e : m.entrySet()) if (e.getKey() / 100 == 402) for (long v : e.getValue()) if (!GameOverlay.itemBad.contains(v)) l.add(v);
        return l;
    }
    static void giveAll() throws Exception {
        long b = Mods.base(); if (b == 0) return;
        java.util.ArrayList<Long> ids = propIds();
        if (ids == null) { state = "开局发道具：在读道具表"; return; }
        if (ids.isEmpty()) { state = "开局发道具：道具表里没有战术道具（402）"; return; }
        Veh.Names nm = Veh.Names.get();
        java.util.ArrayList<Long> ctls = new java.util.ArrayList<>();
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
            double[] q = Veh.info(); long gm = q != null ? (long) q[4] : 0;
            long gs = gm >= 0x10000 ? Server.rd(m, gm + 0x2e0) : 0;
            long data = gs >= 0x10000 ? Server.rd(m, gs + 0x2bc) : 0;
            int n = gs >= 0x10000 ? (int) Server.rd(m, gs + 0x2c0) : 0;
            if (data < 0x10000 || n <= 0 || n > 200) return;
            for (int k = 0; k < n; k++) {
                long ps = Server.rd(m, data + 4L * k); if (ps < 0x10000) continue;
                long ctl = Server.rd(m, ps + 0x60); if (ctl < 0x10000) continue;
                if (!Alive.clsName(m, nm, ctl).contains("PlayerController")) continue;
                if (!Alive.clsName(m, nm, Server.rd(m, ctl + 0x2fc)).contains("NetConnection")) continue;   // 只给加入的玩家
                long p = Server.rd(m, ctl + 0x2c0);
                if (p < 0x10000 || !Alive.clsName(m, nm, p).contains("Character")) continue;
                synchronized (given) { if (given.contains(ctl)) continue; }
                ctls.add(ctl);
            }
        }
        for (long ctl : ctls) {
            int ok = 0;
            for (long id : ids) if (giveTo(b, ctl, id, 999)) ok++;
            synchronized (given) { given.add(ctl); }
            state = "开局发道具：给加入玩家 " + Long.toHexString(ctl) + " 发了 " + ok + "/" + ids.size() + " 种，每种 999";
            LogCap.note(state + "：" + ids);
        }
    }
    /** 游戏线程里调刷物品补丁，人物 = 控制器 +0x2c0 */
    static synchronized boolean giveTo(long b, long ctl, long id, int n) throws InterruptedException {
        String s = "P" + id + "x" + n;
        if (sb == null) { sb = java.nio.ByteBuffer.allocateDirect(256).order(java.nio.ByteOrder.LITTLE_ENDIAN); sbAddr = GameOverlay.bufAddr(sb); }
        if (sbAddr <= 0 || sbAddr > 0xffffffffL) return false;
        // FString {TCHAR* Data, int Num, int Max}（这个包 TCHAR 4 字节），字符从 +16 开始
        for (int i = 0; i < s.length(); i++) sb.putInt(16 + 4 * i, s.charAt(i));
        sb.putInt(16 + 4 * s.length(), 0);
        sb.putInt(0, (int) (sbAddr + 16)); sb.putInt(4, s.length() + 1); sb.putInt(8, s.length() + 1);
        long[] r = Veh.ucall(ctl, sbAddr, 0, 0, null, b + GIVE_CAVE);
        return r[0] == 0x5b0e;
    }
}
