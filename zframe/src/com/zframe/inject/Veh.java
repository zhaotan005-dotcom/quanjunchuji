package com.zframe.inject;

import android.app.Activity;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;

/**
 * v1.80 刷车。
 * 单机对局里地图上一辆车都没有：车辆管理器的车表（VehicleManager+0x2c0）是有的，但刷车点 VehicleSpawnComponentList 是 0
 * （刷车点在服务器才加载的关卡里）。游戏自带的 PC::SpawnVehicle 实现 0x1102ca4(PC, 车编号, &位置, &朝向) 是好的，只是控制台调不到。
 * 做法：刷物品补丁的 cave（AddAvaItem P…）判断第一个字不是 'P' 时，转到新 cave（代码段空隙 SLACK+0x400）：
 *   "V0x<buf>"      → 只填信息：+4 PC，+8 人物，+12..20 人物位置，+24 World，+28 GameMode，+32 车辆管理器，+36 车表，+68 根组件，+72..84 朝向四元数
 *   "V<编号>x<buf>" → 再调 0x1102ca4，位置取 buf+40，朝向取 buf+52。完成标记 +0：0x5b02 只读信息 / 0x5b03 已调刷车
 * 车表的行名是车编号的十进制字符串（FindRow("%d")），所以要读 FName 名字表把行名翻出来。
 * 这些钩子和早装一样，在 libUE4 刚加载时装（运行中改已跑过的代码不生效）。
 */
public class Veh {
    static final long VCAVE = Server.SLACK + 0x400, TRAMP = 0x1716c10L, BNE = 0x17169ecL, SPAWN = 0x1102ca4L, BOT = 0x1115620L;
    /** v1.85 cave 末尾的字面量（顺序见 game/re/vcave.py LITS）：GM AddBotAt 实现、难度→人机类、AddOneBot、PVP 人机 OnPossess、给装备、切战斗行为树 */
    static final long[] LITS = { SPAWN, BOT, 0x1012318L, 0x1014898L, 0x10112d0L, 0xc1bbd8L, 0xc1a534L, 0xc17fe0L, 0x1989c98L, 0x119d884L, 0xccecbcL, 0xcd3bdcL, 0x273d654L };   // v2.31 加 K2_TeleportTo 实现（'T' 把人物传到位置）   // v1.98 加 ADDCHECK/GIVE（'I' 给人机发物品）   // v1.88 加 FMemory::Malloc，v1.91 加 SpawnOnePickUp 实现（刷在地上）
    static final String TRAMP_O = "00009de50810a0e1", BNE_O = "8400001a", BNE_N = "8700001a";
    static final String CAVE = "00b0a0e3560050e31400000a01b0a0e3420050e31100000a02b0a0e3410050e30e00000a03b0a0e3450050e30b00000a04b0a0e34d0050e30800000a06b0a0e3490050e30500000a07b0a0e3540050e30200000a05b0a0e3470050e377ffff1a0190a0e30070a0e3060059e1070000aa090195e7300040e2090050e30300008a077187e0877080e0019089e2f5ffffea019089e20080a0e3060059e1070000aa090195e7300040e2090050e30300008a088188e0888080e0019089e2f5ffffea000058e32201000a04005be3bd00000a05005be3c300000a06005be3d400000a07005be30601000a010b05e3000088e5044088e5c0a294e508a088e500005ae31101000a28019ae5440088e5000050e30d01000a401190e50c1088e5441190e5101088e5481190e5141088e5301190e5481088e5341190e54c1088e5381190e5501088e53c1190e5541088e5000094e5a01090e50400a0e131ff2fe1180088e5000050e3f800000a940090e51c0088e5000050e3f400000a00005be31300001a140990e5200088e5000050e3ee00000ac01290e5241088e5000057e3ea00000a000051e3e800000a407088e50400a0e10710a0e1282088e2343088e298c39fe53cff2fe1030b05e3000088e5e00000ea01005be31300001a000057e3da00000a407088e5300098e500008de5340098e504008de5380098e508008de53c0098e50c008de50400a0e10710a0e1282098e52c3098e544c39fe53cff2fe1040b05e3000088e5ca0000ea03005be35a00000a0060a0e1001090e5c41591e520239fe5020051e1050b05e35100001a407088e50600a0e10710a0e108c39fe53cff2fe1580088e5000050e3060b05e34800000a581098e50600a0e10720a0e1e8c29fe53cff2fe10050a0e15c5088e5000055e3060b05e33e00000a000096e5f82590e50600a0e10510a0e132ff2fe1000096e59c2390e50600a0e10510a0e132ff2fe1b40295e5600088e5000050e30c00000a001090e550c291e50010a0e300108de5281088e2342088e20030a0e33cff2fe1000095e5082390e50500a0e1341088e232ff2fe1000095e5440390e55c129fe5010050e1070b05e31d00001a540395e5640088e5580395e5680088e5240395e56c0088e5ec0395e5700088e5749098e5010019e30200000a0500a0e120c29fe53cff2fe1040019e30400001a020019e30800000a240395e5000050e30500001a580395e5000050e30200000a0500a0e1f0c19fe53cff2fe1240395e5780088e5080b05e3000088e56d0000ea0750a0e1000055e3060b05e3f9ffff0a000095e5440390e5b4119fe5010050e1070b05e3f3ffff1ad4ffffea0700a0e11010a0e3a4c19fe53cff2fe1040088e5090b05e3000088e55a0000ea2c0098e500008de5300098e504008de5340098e508008de5380098e50c008de53c0098e510008de50400a0e10710a0e17c2098e5283098e558c19fe53cff2fe10a0b05e3000088e5470000ea0750a0e1000055e3060b05e3d3ffff0a38a895e504a088e500005ae30c0b05e3ceffff0a806098e5847098e5889098e50000a0e300008de50c008de50000e0e304008de508008de50a00a0e10610a0e10720a0e10930a0e1f0c09fe53cff2fe18c0088e5010050e30900a0b10090a0e10000a0e340008de508008de50c008de500908de50010e0e304108de510908de540108de214108de518008de51c008de520008de524008de528008de50500a0e10610a0e10720a0e10030a0e390c09fe53cff2fe1900088e50b0b05e3a3ffffea000057e3060b05e3a0ffff0a340098e500008de5380098e504008de53c0098e508008de50700a0e1281098e52c2098e5303098e54cc09fe53cff2fe1040088e50d0b05e391ffffea020b05e3000088e544d08de2f08fbde8";
    /** v2.51 'U' cave（SLACK+0x224，game/re/ucave.py）：vcave 认不出的字母跳过来；末尾拼缓冲区地址。装不上时 vcave 那一处改回原来的 bne done */
    static final long UCAVE = Server.SLACK + 0x240; static final int UBNE_AT = 0x5c; static final String UBNE_OLD = "3c01001a";   // v2.54：0x224 和服务端门(SLACK+0x200，0x34 字节)重叠 → 挪到 0x240
    static final String UCODE = "550050e32800001aa4809fe51c0098e5000050e30d00000a281098e5381380e52c1098e53c1380e5301098e5401380e5341098e5441380e5381098e5481380e53c1098e54c1380e50110a0e33613c0e50c0098e5000050e31100000a441098e500108de5481098e504108de5242098e5403098e5101098e520c098e500005ce30200001a00e090e514c098e50cc09ee73cff2fe1180088e50e0b05e3000088e5010000ea0f0b05e3000088e544d08de2f08fbde8";

    public static volatile String state = "刷车：没装";
    static volatile boolean installed;

    /** 启动早装（Mods.preinstall 里调）。刷物品补丁没打就不装 */
    static synchronized String install() {
        if (installed) return "ok";
        long b = Mods.base();
        if (b == 0) return state = "刷车：找不到游戏库";
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "rw")) {
            if (!Server.bytesAt(m, b + 0x17169dcL, 4).equals("020056e3")) return state = "刷车：刷物品补丁没打（先在补丁更新里点「更新补丁」）";
            String bne = Server.bytesAt(m, b + BNE, 4);
            if (!bne.equals(BNE_O) && !bne.equals(BNE_N)) return state = "刷车：补丁代码和预期不一样 " + bne;
            String tr = Server.bytesAt(m, b + TRAMP, 8);
            String trN = "04f01fe5" + Server.w32((int) (b + VCAVE));
            if (!tr.equals(TRAMP_O) && !tr.equals(trN)) return state = "刷车：空位字节不符 " + tr;
            // v2.51：先放 ucave（空位必须全 0 或已经是我们的），放不上就让 vcave 照旧（不认识的字母直接收尾）
            String cave = CAVE;
            if (ubuf == null) { ubuf = ByteBuffer.allocateDirect(256).order(ByteOrder.LITTLE_ENDIAN); uaddr = GameOverlay.bufAddr(ubuf); }
            boolean uok = false;
            if (uaddr > 0 && uaddr <= 0xffffffffL) {
                byte[] want = Patches.hex(UCODE + Server.w32((int) uaddr)), cur = new byte[want.length];
                m.seek(b + UCAVE); m.readFully(cur);
                // v3.05.1：cave 变长（0xb0）后尾巴伸进这页自带的数据段副本（不是 0，但没人用，v2.83 记录）→ v3.05 装不上，「U」调用全失败（空投塞不进、开局发道具 0/13、关碰撞没生效）。
                // 只要开头 0x98（旧 cave 长度）是 0 就算空位
                boolean zero = true; for (int z = 0; z < Math.min(0x98, cur.length); z++) if (cur[z] != 0) { zero = false; break; }
                // v2.54：代码一样只是末尾缓冲区地址不同（同一进程装过）也算我们的，重写
                boolean ours = java.util.Arrays.equals(java.util.Arrays.copyOf(cur, want.length - 4), java.util.Arrays.copyOf(want, want.length - 4));
                if (zero || ours) { m.seek(b + UCAVE); m.write(want); uok = true; }
                else uBusy = Server.bytesAt(m, b + UCAVE, 8);
            }
            if (!uok) cave = CAVE.substring(0, UBNE_AT * 2) + UBNE_OLD + CAVE.substring(UBNE_AT * 2 + 8);
            uOn = uok;
            StringBuilder lit = new StringBuilder(cave);
            for (long l : LITS) lit.append(Server.w32((int) (b + l)));
            m.seek(b + VCAVE); m.write(Patches.hex(lit.toString()));
            m.seek(b + TRAMP); m.write(Patches.hex(trN));
            m.seek(b + BNE); m.write(Patches.hex(BNE_N));
            if (!Server.bytesAt(m, b + BNE, 4).equals(BNE_N)) return state = "刷车：写入没生效";
        } catch (Throwable e) { return state = "刷车：出错 " + e.getMessage(); }
        installed = true;
        state = "刷车：已装（启动时）" + (uOn ? "" : "；用道具 cave 没装（空位被占 " + uBusy + "）");
        return "ok";
    }

    static ByteBuffer buf; static long addr;
    static ByteBuffer ubuf; static long uaddr; static volatile boolean uOn; static volatile String uBusy = "";
    /** v2.51 'U'：游戏线程里调 obj->vt[off](obj, arg)。item 非 0 时先把 pos（x,y,z,pitch,yaw,roll）写到 道具+0x338.. 并置 道具+0x336=1。
     *  返回 {标记, 返回值}；标记 0x5b0e 调了、0x5b0f obj 空、0 没反应 */
    static long[] ucall(long obj, long arg, int off, long item, float[] pos) throws InterruptedException { return ucall(obj, arg, off, item, pos, 0); }
    /** v2.60：fn 非 0 = 直接调绝对地址 fn(obj, arg) */
    static synchronized long[] ucall(long obj, long arg, int off, long item, float[] pos, long fn) throws InterruptedException { return ucall5(obj, arg, off, item, pos, fn, 0, 0, 0); }
    /** v3.05：fn(obj, arg, a2, a3, a4)，a4 在栈上（ucave 改了：buf+36 → r2，buf+64 → r3，buf+68 → [sp]） */
    static long[] ucall5(long obj, long arg, int off, long item, float[] pos, long fn, long a2, long a3, long a4) throws InterruptedException { return ucall6(obj, arg, off, item, pos, fn, a2, a3, a4, 0); }
    /** v3.07：第 6 个参数 a5 在 [sp+4]（buf+72） */
    static synchronized long[] ucall6(long obj, long arg, int off, long item, float[] pos, long fn, long a2, long a3, long a4, long a5) throws InterruptedException {
        if (!installed || !uOn || ubuf == null) return new long[] { 0, 0 };
        for (int o = 0; o < 76; o += 4) ubuf.putInt(o, 0);
        ubuf.putInt(36, (int) a2); ubuf.putInt(64, (int) a3); ubuf.putInt(68, (int) a4); ubuf.putInt(72, (int) a5);
        ubuf.putInt(32, (int) fn);
        ubuf.putInt(12, (int) obj); ubuf.putInt(16, (int) arg); ubuf.putInt(20, off); ubuf.putInt(28, (int) item);
        if (item != 0 && pos != null) for (int k = 0; k < 6; k++) ubuf.putFloat(40 + 4 * k, pos[k]);
        Activity a = Con.act; if (a == null) return new long[] { 0, 0 };
        a.runOnUiThread(() -> Con.exec("AddAvaItem U"));
        for (int i = 0; i < 40; i++) {
            Thread.sleep(50);
            int mg = ubuf.getInt(0);
            if (mg == 0x5b0e || mg == 0x5b0f) return new long[] { mg, ubuf.getInt(24) & 0xffffffffL };
        }
        return new long[] { 0, 0 };
    }
    static boolean call(long id) throws InterruptedException { return call('V', id); }
    static boolean call(char k, long id) throws InterruptedException { return call(k, id, 2000); }
    /** v4.10：上一次调用超时时 cave 的标记（0 = 游戏线程根本没跑到 cave；0x5b01 = 进了 cave 还没出来，多半在同步加载车的资源） */
    static volatile int lastTimeoutMark = -1;
    /** v4.10：超时的那条命令可能还在游戏线程里跑（刷车第一次加载某种车要好几秒）。下一次调用前先等它出来，免得它晚到的 0x5b03 被当成新命令的结果 */
    static volatile long staleUntil;
    static synchronized boolean call(char k, long id, int timeoutMs) throws InterruptedException {
        if (buf == null) { buf = ByteBuffer.allocateDirect(256).order(ByteOrder.LITTLE_ENDIAN); addr = GameOverlay.bufAddr(buf); }
        if (addr <= 0 || addr > 0xffffffffL) return false;
        while (staleUntil > System.currentTimeMillis() && buf.getInt(0) == 0x5b01) Thread.sleep(50);
        staleUntil = 0;
        buf.putInt(0, 0);
        final String c = "AddAvaItem " + k + id + "x" + addr;
        Activity a = Con.act; if (a == null) return false;
        a.runOnUiThread(() -> Con.exec(c));
        for (int i = 0, n = Math.max(1, timeoutMs / 50); i < n; i++) {
            Thread.sleep(50);
            int mg = buf.getInt(0);
            if (mg >= 0x5b02 && mg <= 0x5b0d) { lastTimeoutMark = -1; return true; }
        }
        lastTimeoutMark = buf.getInt(0);
        if (lastTimeoutMark == 0x5b01) staleUntil = System.currentTimeMillis() + 15000;
        return false;
    }

    /** v1.91 刷在地上：游戏自己的 SpawnOnePickUp（cave G），v1.92 起放在脚下，piles 堆；返回 null = 成功 */
    static synchronized String ground(long id, int piles) throws InterruptedException {
        if (!installed) return state;
        long type = id / 10000, tsid = id % 10000;
        java.util.Random rnd = new java.util.Random();
        for (int i = 0; i < piles; i++) {
            String e = ahead(20, -80);   // v1.92 直接脚下（人物中心离地约 90 厘米），几堆靠随机散开
            if (e != null) return e;
            buf.putFloat(40, buf.getFloat(40) + (rnd.nextFloat() - 0.5f) * (i == 0 ? 10 : 60));
            buf.putFloat(44, buf.getFloat(44) + (rnd.nextFloat() - 0.5f) * (i == 0 ? 10 : 60));
            buf.putFloat(52, 0f); buf.putFloat(60, 0f);
            buf.putInt(124, (int) tsid);
            if (!call('G', type) || buf.getInt(0) != 0x5b0a) return "补丁代码没反应";
        }
        return null;
    }
    /** v2.20 房主：在指定坐标（游戏世界坐标，厘米）放一件拾取物（cave 'G'，服务器上生成，客户端由游戏同步）；返回 null = 成功 */
    static String dropAt(long id, float x, float y, float z) throws InterruptedException { return dropAt(id, x, y, z, 0f); }
    /** v3.63 带朝向（yaw 度，SpawnOnePickUp 的 Rot = buf+52 pitch / +56 yaw / +60 roll） */
    static synchronized String dropAt(long id, float x, float y, float z, float yaw) throws InterruptedException {
        if (!installed) return state;
        if (buf == null) { buf = ByteBuffer.allocateDirect(256).order(ByteOrder.LITTLE_ENDIAN); addr = GameOverlay.bufAddr(buf); }
        buf.putFloat(40, x); buf.putFloat(44, y); buf.putFloat(48, z);
        buf.putFloat(52, 0f); buf.putFloat(56, yaw); buf.putFloat(60, 0f);
        buf.putInt(124, (int) (id % 10000));
        if (!call('G', id / 10000) || buf.getInt(0) != 0x5b0a) return "补丁代码没反应";
        return null;
    }
    /** v2.92 服务端替客户端刷车：在 (x,y,z) 前方（+x）6 米刷第 gi 组车。
     *  v3.47：和菜单刷车同一条路 place()（贴地、挡住换位置、确认真刷出来、加满油）；不再持锁等结果（place 里自己分段加锁） */
    static String spawnGroupAt(int gi, float x, float y, float z) throws InterruptedException {
        if (!installed) return state;
        if (!call(0)) return "补丁代码没反应";
        long dt = buf.getInt(36) & 0xffffffffL;
        if (dt == 0) return "这局没有车表";
        ArrayList<Group> gs = groups;
        if (gs == null || gs.isEmpty() || gi < 0) return "车表里没读到车编号";
        trucks(dt);
        Group g = gs.get(gi % gs.size());
        long id = g.ids.get(g.at++ % g.ids.size());
        return place(g.label, id, x, y, z, 0);
    }

    // ---------------- v3.47 刷车：落点、确认、满油 ----------------
    /* 单机刷车「点了没车」的原因（静态 RE + v1.92/v1.93 设备日志）：
     *  游戏 SpawnVehicle 0x1102ca4 → 0x100600c：FindRow → SpawnActor 0x2aa8ad4，生成参数碰撞处理写死 3（0x1006064 mov r0,#3 = AdjustIfPossibleButDontSpawnIfColliding），
     *  被挡住就返回空，只打一行 Error「[VehicleSpawn] spawn vehicle failed … SpawnPoint=None」（v1.92/v1.93 日志 18:24:51~18:25:06 连续 6 次，全在同一个落点）。
     *  我们以前的落点是「人物中心往前 6 米、再高 1.5 米」，不看地面：前面是上坡/墙/树/房子就生成失败；
     *  而 vcave 调完 0x1102ca4 不管成没成都写 0x5b03，Java 就提示「已刷」——失败完全看不出来。
     *  另外落点写在共用缓冲 buf+40..60，ahead() 和 call(id) 是两次加锁，中间人机自动刷（addBot）/传送（teleport）/刷地上东西（dropAt）会改这块，车就刷到别处去了（单机有自动人机，最容易撞上）。
     *  修：落点 = 射线打到的地面 + 2 米；一次加锁写落点+调用；调完扫车（油箱组件）确认真的多了一辆，没有就换下一个落点（最多 5 个）。 */
    /** UVehicleFuelComponent（/Script/NZGame，StaticClass 0xe9e7b0，构造 0xe1e61c，虚表 0x4beb188）：+0xa0 Owner（车），+0xa8 FuelAmountMax（int，默认 100），
     *  +0xb4 FuelAmountCurrent（float，复制，OnRep_Fuel = vt+0x1ec 0xe1ef10）；BeginPlay vt+0x17c = 0xe1e6f0：权威端 Current = Max × 随机(RandMin +0xb8=0.1 .. RandMax +0xbc=1.0) × GM+0x340 VehicleFuelFactor；
     *  vt+0x1e8 = 0xe1e6b8 AddFuel(this, float)：Current += min(数量, Max-Current)。 */
    static final long FUEL_VT = 0x4beb188L, ADD_FUEL = 0xe1e6b8L;
    static final int F_OWNER = 0xa0, F_MAX = 0xa8, F_CUR = 0xb4, F_ADD = 0x1e8;
    public static volatile String fuelState = "满油：还没刷车";
    static volatile boolean quickToast;

    /** 一次加锁：写落点和朝向，再调刷车。true = cave 调过了 0x1102ca4（不代表车一定生成了） */
    static boolean spawnAt(long id, float x, float y, float z, float yaw) throws InterruptedException { return spawnCode(id, x, y, z, yaw) == 1; }
    /** v4.10：1 = cave 调完游戏刷车函数（0x5b03）；0 = cave 跑了但半路收尾（没人物/没 GameMode/没车辆管理器/没车表，0x5b02）；
     *  -1 = 10 秒内游戏线程没跑到 cave（命令没执行）；-2 = 进了 cave、10 秒还没出来（游戏在同步加载这辆车，车多半稍后会出来） */
    static synchronized int spawnCode(long id, float x, float y, float z, float yaw) throws InterruptedException {
        if (!installed) return -1;
        if (buf == null && !call(0)) return -1;
        buf.putFloat(40, x); buf.putFloat(44, y); buf.putFloat(48, z);
        buf.putFloat(52, 0f); buf.putFloat(56, yaw); buf.putFloat(60, 0f);
        if (call('V', id, 10000)) return buf.getInt(0) == 0x5b03 ? 1 : 0;
        return lastTimeoutMark == 0x5b01 ? -2 : -1;
    }
    /** 场上所有车的油箱组件 → {组件, 车}；扫一遍 GUObjectArray（只比虚表，跳过类默认对象）。出错返回 null */
    static java.util.HashMap<Long, Long> fuelComps() {
        long b = Mods.base(); if (b == 0) return null;
        java.util.HashMap<Long, Long> out = new java.util.HashMap<>();
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
            for (long c : Flare.byVt(m, b, b + FUEL_VT)) { long o = Server.rd(m, c + F_OWNER); if (o >= 0x10000) out.put(c, o); }
        } catch (Throwable e) { return null; }
        return out;
    }
    /** 刷之后：找 before 里没有的、车在 (x,y) 30 米内的那个油箱组件 → {组件, 车}；没有返回 null */
    static volatile java.util.HashMap<Long, Long> snap;
    /** v3.89：打开刷车页时后台扫一次现有车，刷车时不用再等 */
    static void preSnap() { new Thread(() -> { java.util.HashMap<Long, Long> h = fuelComps(); if (h != null) snap = h; }, "zf-vsnap").start(); }
    static long[] findNew(java.util.HashMap<Long, Long> before, float x, float y, double rad) {
        java.util.HashMap<Long, Long> now = fuelComps(); if (now == null) return null;
        snap = now;
        long[] best = null; double bd = rad * rad;
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
            for (java.util.Map.Entry<Long, Long> e : now.entrySet()) {
                if (before.containsKey(e.getKey())) continue;
                long root = Server.rd(m, e.getValue() + 0x128); if (root < 0x10000) continue;
                double dx = Mods.f(m, root + 0x140) - x, dy = Mods.f(m, root + 0x144) - y, d = dx * dx + dy * dy;
                if (d < bd) { bd = d; best = new long[] { e.getKey(), e.getValue() }; }
            }
        } catch (Throwable ignored) {}
        return best;
    }
    /** v3.47 用户：刷出来的车默认满油。游戏线程调这辆车油箱组件自己的 AddFuel(上限)（vt+0x1e8，调之前核对虚表项 == 0xe1e6b8） */
    static String fillFuel(long comp) throws InterruptedException {
        long b = Mods.base(); if (b == 0) return "满油：找不到游戏库";
        int max; float cur;
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
            if (Server.rd(m, comp) != b + FUEL_VT) return "满油：油箱组件不对";
            if (Server.rd(m, Server.rd(m, comp) + F_ADD) != b + ADD_FUEL) return "满油：加油函数对不上，没加";
            max = (int) Server.rd(m, comp + F_MAX); cur = Mods.f(m, comp + F_CUR);
        } catch (Throwable e) { return "满油：读不到 " + e; }
        if (max <= 0 || max > 100000) return "满油：上限不对 " + max;
        if (cur >= max - 0.01f) return "油 " + max + "/" + max;
        long[] r = ucall(comp, Float.floatToRawIntBits((float) max) & 0xffffffffL, F_ADD, 0, null);
        float now = cur;
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) { now = Mods.f(m, comp + F_CUR); } catch (Throwable ignored) {}
        if (r[0] != 0x5b0e) return "满油：加油没调成（" + Long.toHexString(r[0]) + "），油 " + Math.round(cur) + "/" + max;
        return "油 " + Math.round(cur) + "→" + Math.round(now) + "/" + max;
    }
    // ---------------- v4.10 刷车落点（安全版） ----------------
    /* v4.09 实测（训练岛，11-22 20:39 截图）：按「三轮摩托」提示「没反应（编号 16，补丁代码没反应）」，但旁边出来了一辆摩托；之后人物「因载具爆炸而被淘汰」。原因：
     *  1) 「补丁代码没反应」= call() 2 秒内没等到 cave 的完成标记 0x5b03。cave 在游戏线程里同步调游戏刷车函数，第一次刷某种车要同步加载它的蓝图/模型，
     *     会超过 2 秒 → 误报没反应，车其实过一会儿才出来（标记停在 0x5b01 = 进了 cave 没出来）。超时那条命令还会把晚到的 0x5b03 写进下一次调用的缓冲区。
     *  2) v4.07 删掉读游戏日志后，place() 还在等日志里的「Spawn Vehicle」（spawnOk/spawnFail 永远不变）→ 每次白等 3 秒，再整表扫 GUObjectArray（十几秒）找新车；
     *     找不到（扫描慢/车被物理弹开超过 9 米/没预扫）就当「没生成」在 700 米外左右 4.5 米再刷一辆 → 一次按键最多刷 4 辆，车叠在一起/砸在一起被物理弹飞、受伤爆炸。
     *  3) 落点只看前方一个点的高度：不看车身占地（坡、房子边、树、别的车、人），船按人物高度刷（可能刷在岸上），车可能刷进水里。
     * 现在：
     *  - 按车型给车身尺寸；落点在人前方「人 2.5 米净空 + 半个车长」以外；每个候选点打 5 条向下射线（中心、车头、车尾、左右），
     *    都要打到能停车的面（不是树/草/水/体积/车/人，法线 Z ≥ 0.85 ≈ 坡度 ≤ 32°），和人脚下高差 ≤ 2 米，5 点高差 ≤ 1.2 米；再和最近 2 分钟刷过的点保持距离。
     *  - 船只能刷在水面：在人周围 6~25 米找射线打到水（名字含 water/ocean/sea/lake/river）的点；人自己在游泳时刷在人前方水面。其他车不刷进水里。
     *  - 人在车上（控制器的 Pawn 不是人物）：落点放到车的左右两侧/后方更远处。
     *  - 刷车等 10 秒（不再 2 秒误报）；cave 返回 0x5b03 后在落点往下打射线确认车在那（打到 BP_Vehicle…）。确认不到也不会自动再刷第二辆（避免叠车）。满油照旧后台做。
     *  v4.11：去掉 v4.10 的「刷完补满车血 / 训练岛一直给车补血 / 训练岛车不受伤」—— 用户 21:02：车是他拿枪打爆的，车能被打爆是正常的；那套是治标。 */
    static final class Size { float halfLen, halfW, lift; boolean boat; Size(float l, float w, float z, boolean b) { halfLen = l; halfW = w; lift = z; boat = b; } }
    static Size size(String label) {
        if (label.contains("大船")) return new Size(520, 220, 60, true);
        if (label.contains("艇") || label.contains("船")) return new Size(320, 130, 60, true);
        if (label.contains("三轮")) return new Size(140, 100, 120, false);
        if (label.contains("摩托")) return new Size(120, 50, 120, false);
        if (label.contains("蹦蹦")) return new Size(180, 100, 140, false);
        if (label.contains("面包")) return new Size(290, 120, 150, false);
        if (label.contains("皮卡") || label.contains("大脚")) return new Size(310, 140, 160, false);
        return new Size(260, 115, 150, false);   // 吉普、轿车、跑车
    }
    /** 最近刷过的点 {x, y, 时间, 世界}（同一个世界 2 分钟内） */
    static final ArrayList<float[]> recent = new ArrayList<>();
    static final long T0 = System.currentTimeMillis();   // 时间存成离 T0 的秒数（float 存绝对秒数会丢精度）
    static boolean nearRecent(float x, float y, float r) {
        long now = System.currentTimeMillis();
        synchronized (recent) {
            for (int i = recent.size() - 1; i >= 0; i--) {
                float[] q = recent.get(i);
                if (now - T0 - (long) (q[2] * 1000f) > 120000) { recent.remove(i); continue; }
                float dx = q[0] - x, dy = q[1] - y;
                if (dx * dx + dy * dy < r * r) return true;
            }
        }
        return false;
    }
    static void remember(float x, float y) { synchronized (recent) { recent.add(new float[] { x, y, (System.currentTimeMillis() - T0) / 1000f }); if (recent.size() > 40) recent.remove(0); } }
    static boolean isWater(Trace.Hit h) {
        String w = (h.actor + "/" + h.cls + "/" + h.comp).toLowerCase(java.util.Locale.ROOT);
        return w.contains("water") || w.contains("ocean") || w.contains("sea_") || w.contains("_sea") || w.contains("lake") || w.contains("river");
    }
    static boolean isVehOrChar(Trace.Hit h) {
        String w = (h.actor + "/" + h.cls).toLowerCase(java.util.Locale.ROOT);
        return (w.contains("vehicle") && !w.contains("props_") && !w.contains("broken")) || w.contains("character") || w.startsWith("bp_vh");
    }
    /** 一个点能不能停车：返回落地高度，不行返回 NaN，why 记原因 */
    static float okPoint(float x, float y, float feet, StringBuilder why) {
        Trace.Hit h = Trace.down(x, y, feet + 600f, feet - 1500f);
        if (h == null) { why.append("射线没调成"); return Float.NaN; }
        if (Float.isNaN(h.z)) { why.append("下面什么都没打到"); return Float.NaN; }
        if (isWater(h)) { why.append("是水面"); return Float.NaN; }
        if (isVehOrChar(h)) { why.append("有车/人（").append(h.cls).append("）"); return Float.NaN; }
        if (!h.land && (h.cls.contains("位置对不上") || Trace.softName(h.actor + "/" + h.cls + "/" + h.comp))) { why.append("是树/草/体积（").append(h.cls).append("）"); return Float.NaN; }
        if (h.nz < 0.85f) { why.append("太陡（法线 ").append(String.format("%.2f", h.nz)).append("）"); return Float.NaN; }
        if (Math.abs(h.z - feet) > 200f) { why.append("和脚下差 ").append(Math.round((h.z - feet) / 100f)).append(" 米"); return Float.NaN; }
        return h.z;
    }
    /** 车身 5 个点（中心、车头、车尾、左、右，车朝 yaw）都能停：返回最高点高度，否则 NaN */
    static float okFootprint(float cx, float cy, double fx, double fy, Size sz, float feet, StringBuilder why) {
        float[][] pts = { {0, 0}, {sz.halfLen, 0}, {-sz.halfLen, 0}, {0, sz.halfW}, {0, -sz.halfW} };
        float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
        for (float[] q : pts) {
            float x = (float) (cx + q[0] * fx - q[1] * fy), y = (float) (cy + q[0] * fy + q[1] * fx);
            float z = okPoint(x, y, feet, why);
            if (Float.isNaN(z)) return Float.NaN;
            lo = Math.min(lo, z); hi = Math.max(hi, z);
        }
        if (hi - lo > 120f) { why.append("地面不平（高差 ").append(Math.round(hi - lo)).append(" 厘米）"); return Float.NaN; }
        return hi;
    }
    /** 船：水面高度，不是水返回 NaN */
    static float waterAt(float x, float y, float feet) {
        Trace.Hit h = Trace.down(x, y, feet + 800f, feet - 3000f);
        if (h == null || Float.isNaN(h.z) || !isWater(h)) return Float.NaN;
        return h.z;
    }
    /** 人在不在水里（移动模式 4 = 游泳） */
    static boolean swimming(long pawn) {
        long b = Mods.base(); if (b == 0 || pawn < 0x10000) return false;
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
            if (!NoFall.isChar(m, b, pawn)) return false;
            long c = Mods.rp(m, pawn + Mods.P_CMC);
            return Mods.isObj(m, b, c) && (Mods.rd(m, c + Mods.C_MODE) & 0xff) == 4;
        } catch (Throwable e) { return false; }
    }
    /** 在落点往下打射线确认车出来了：打到 BP_Vehicle… 就返回车（actor），否则 0 */
    static long confirm(float x, float y, float z) {
        for (int k = 0; k < 3; k++) {
            Trace.Hit h = Trace.down(x, y, z + 400f, z - 600f);
            if (h != null && !Float.isNaN(h.z) && h.obj != 0) {
                String w = (h.actor + "/" + h.cls).toLowerCase(java.util.Locale.ROOT);
                if (w.contains("vehicle") && !w.contains("props_") && !w.contains("broken")) return h.obj;
            }
            try { Thread.sleep(250); } catch (InterruptedException e) { return 0; }
        }
        return 0;
    }
    /** NZVehicle 反射：MaxHealth +0x2f8（int）、Health +0x308（int） */
    static final int V_MAXHP = 0x2f8, V_HP = 0x308;
    /** 在 (px,py,pz)（人物中心）朝 yaw 方向找安全落点刷 id。返回给菜单看的一句话 */
    static String place(String label, long id, double px, double py, double pz, double yawDeg) throws InterruptedException { return place(label, id, px, py, pz, yawDeg, false, false, false); }
    static String place(String label, long id, double px, double py, double pz, double yawDeg, boolean inVeh, boolean swim) throws InterruptedException { return place(label, id, px, py, pz, yawDeg, inVeh, swim, true); }
    /** useRecent = 菜单刷车（防连按叠车：2 分钟内刷过的点附近不再刷）；服务端代刷/排车（spawnGroupAt）不用，只靠射线看车位 */
    static String place(String label, long id, double px, double py, double pz, double yawDeg, boolean inVeh, boolean swim, boolean useRecent) throws InterruptedException {
        double r = Math.toRadians(yawDeg), fx = Math.cos(r), fy = Math.sin(r);
        Size sz = size(label);
        float feet = (float) pz - 90f;   // 人物中心离脚约 90 厘米（车上时是车的位置，差不多）
        float clear = (inVeh ? 450f : 250f) + sz.halfLen + 50f;   // 离人（或人坐的车）的净空
        float fx0 = 0, fy0 = 0, fz = Float.NaN; String how = "";
        StringBuilder tried = new StringBuilder();
        boolean traceOk = Trace.init();
        if (sz.boat) {
            // 船：先看人前方，再绕一圈找水面
            if (swim) { fx0 = (float) (px + clear * fx); fy0 = (float) (py + clear * fy); float w = waterAt(fx0, fy0, feet); fz = (Float.isNaN(w) ? (float) pz : w) + sz.lift; how = "人前方水面"; }
            else if (traceOk) {
                outer:
                for (float d : new float[] { clear, clear + 400f, clear + 900f, 2500f }) for (int k = 0; k < 8; k++) {
                    double a = r + (k == 0 ? 0 : ((k + 1) / 2) * Math.PI / 4 * ((k & 1) == 1 ? 1 : -1));
                    float x = (float) (px + d * Math.cos(a)), y = (float) (py + d * Math.sin(a));
                    float w = waterAt(x, y, feet);
                    if (Float.isNaN(w) || useRecent && nearRecent(x, y, sz.halfLen * 2)) continue;
                    // 船身两头也要是水
                    float w1 = waterAt((float) (x + sz.halfLen * Math.cos(a)), (float) (y + sz.halfLen * Math.sin(a)), feet), w2 = waterAt((float) (x - sz.halfLen * Math.cos(a)), (float) (y - sz.halfLen * Math.sin(a)), feet);
                    if (Float.isNaN(w1) || Float.isNaN(w2)) continue;
                    fx0 = x; fy0 = y; fz = w + sz.lift; how = "水面（" + Math.round(d / 100) + " 米）"; yawDeg = Math.toDegrees(a); break outer;
                }
            }
            if (Float.isNaN(fz)) return "没刷 " + label + "：船只能刷在水上，附近 25 米没找到水面（走到水边或下水再刷）";
        } else {
            if (swim) return "没刷 " + label + "：人在水里，先上岸再刷（车不刷进水里）";
            if (!traceOk) {
                // 射线用不了：退回旧办法（人前方、人物高度 +1.5 米），但离人更远
                fx0 = (float) (px + (clear + 100f) * fx); fy0 = (float) (py + (clear + 100f) * fy); fz = (float) pz + 150f; how = "人物高度（地面射线用不了：" + Trace.state + "）";
                if (useRecent && nearRecent(fx0, fy0, sz.halfLen * 2 + 150f)) return "没刷 " + label + "：前面刚刷过车，换个方向再刷（地面射线用不了，没法看落点）";
            } else {
                // 候选：{前方距离, 左右}；人在车上时放车两侧/后方更远
                float[][] cand = inVeh
                    ? new float[][] { {0, clear + 150f}, {0, -(clear + 150f)}, {-(clear + 300f), 0}, {clear + 600f, 0}, {-(clear + 300f), clear + 150f}, {-(clear + 300f), -(clear + 150f)} }
                    : new float[][] { {clear, 0}, {clear + 350f, 0}, {clear + 100f, 2 * sz.halfW + 300f}, {clear + 100f, -(2 * sz.halfW + 300f)}, {clear + 700f, 0}, {0, clear + 150f}, {0, -(clear + 150f)} };
                for (int i = 0; i < cand.length; i++) {
                    float x = (float) (px + cand[i][0] * fx - cand[i][1] * fy), y = (float) (py + cand[i][0] * fy + cand[i][1] * fx);
                    if (useRecent && nearRecent(x, y, sz.halfLen * 2 + 150f)) { tried.append(tried.length() == 0 ? "" : "；").append("位置").append(i + 1).append(" 刚刷过车"); continue; }
                    StringBuilder why = new StringBuilder();
                    float z = okFootprint(x, y, fx, fy, sz, feet, why);
                    if (Float.isNaN(z)) { tried.append(tried.length() == 0 ? "" : "；").append("位置").append(i + 1).append(" ").append(why); continue; }
                    fx0 = x; fy0 = y; fz = z + sz.lift; how = i == 0 ? "前方地面" : "第 " + (i + 1) + " 个位置";
                    break;
                }
                if (Float.isNaN(fz)) {
                    // v4.15 用户：训练场刷不了车。5 点车位检查太严（训练场台子/斜坡/草都会被拒）→ 找不到「完美车位」时照 v4.10 以前的办法：
                    //  人前方 (净空+1 米)，高度取那一点射线打到的地面（打不到就人物高度），再抬 1.5 米落下。只有前面是水才不刷
                    fx0 = (float) (px + (clear + 100f) * fx); fy0 = (float) (py + (clear + 100f) * fy);
                    Trace.Hit h = Trace.down(fx0, fy0, feet + 600f, feet - 1500f);
                    if (h != null && !Float.isNaN(h.z) && isWater(h)) return "没刷 " + label + "：前面是水，转个方向再刷";
                    fz = (h != null && !Float.isNaN(h.z) && Math.abs(h.z - feet) < 600f ? h.z : feet) + 150f + sz.lift;
                    how = "人前方（没找到平整车位）";
                }
            }
        }
        java.util.HashMap<Long, Long> before = snap;
        final boolean fresh = before != null;
        if (before == null) before = new java.util.HashMap<>();
        int code = spawnCode(id, fx0, fy0, fz, (float) yawDeg);
        if (code == -1) return "没反应 " + label + "（编号 " + id + "，游戏 10 秒没执行刷车命令；进对局落地后再按）";
        if (code == 0) return "没刷 " + label + "（编号 " + id + "，这局拿不到人物/车辆管理器/车表）";
        remember(fx0, fy0);
        if (code == -2) return "刷车中 " + label + "（编号 " + id + "，游戏还在加载这辆车，稍等几秒会出来；别连按）";
        long veh = confirm(fx0, fy0, fz);
        final java.util.HashMap<Long, Long> bf = before; final float cx = fx0, cy = fy0; final String lb = label;
        new Thread(() -> {
            try {
                long[] v = findNew(bf, cx, cy, fresh ? 3000 : 900);
                if (v != null) { String f = fillFuel(v[0]); fuelState = "满油：" + lb + " " + f; note("刷车：" + lb + " " + f); }
                carCheck();
            } catch (Throwable ignored) {}
        }, "zf-vfuel").start();
        if (veh == 0) return "已刷 " + label + "（编号 " + id + "，" + how + "；射线没确认到车，前面没看到车就换个空地再刷，别连按）";
        return "已刷 " + label + "（编号 " + id + "，" + how + "）";
    }

    /** v3.47：皮卡/大脚车核对。车表行里有车的蓝图路径（FSoftObjectPath，FName）：/Game/Vehicles/Wheeled/MonsterTruck/… 或 /Game/Vehicles/Wheeled/PickUpTruck/…（pak 清单里的目录名） */
    public static volatile String truckInfo = "皮卡/大脚车：还没核对（先按 31 皮卡、32 大脚车）";
    static volatile long trucksDt;
    static void trucks(long dt) {
        if (dt == 0 || dt == trucksDt) return;
        trucksDt = dt;
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
            Names n = Names.get();
            if (n == null) { truckInfo = "皮卡/大脚车：名字表没找到，按 31 皮卡、32 大脚车"; return; }
            String k31 = kind(m, dt, 31, n), k32 = kind(m, dt, 32, n);
            boolean swap = "大脚车".equals(k31) || "皮卡".equals(k32);
            if (swap) for (Group g : groups) { if ("皮卡".equals(g.label)) { g.ids.clear(); g.ids.add(32L); g.at = 0; } else if ("大脚车".equals(g.label)) { g.ids.clear(); g.ids.add(31L); g.at = 0; } }
            truckInfo = "皮卡/大脚车：车表 31=" + (k31 == null ? "没认出" : k31) + "，32=" + (k32 == null ? "没认出" : k32) + (swap ? "，已对调" : "");
        } catch (Throwable e) { truckInfo = "皮卡/大脚车：核对出错 " + e; }
        note(truckInfo);
    }
    /** 车表里编号 id 那一行，行内 0x200 字节里找 FName / FString 含 MonsterTruck / PickUpTruck */
    static String kind(RandomAccessFile m, long dt, long id, Names n) {
        long data = Server.rd(m, dt + 0x20), num = Server.rd(m, dt + 0x24);
        if (data == 0 || num <= 0 || num > 500) return null;
        String want = Long.toString(id);
        long end = data + Math.min(num * 128L, 0x10000);
        for (long e = data; e + 16 <= end; e += 4) {
            long idx = Server.rd(m, e), no = Server.rd(m, e + 4);
            if (idx <= 0 || idx > 0x400000 || no != 0 || !want.equals(n.name(m, (int) idx))) continue;
            for (int k = 8; k <= 12; k += 4) {
                long row = Server.rd(m, e + k); if (row < 0x10000) continue;
                for (int o = 0; o < 0x200; o += 4) {
                    String s = null;
                    long w = Server.rd(m, row + o), w2 = Server.rd(m, row + o + 4);
                    if (w > 0 && w <= 0x400000 && w2 < 64) s = n.name(m, (int) w);
                    if (s == null && w >= 0x10000 && w2 >= 8 && w2 <= 300) s = u32(m, w, (int) Math.min(w2, 160));
                    if (s == null) continue;
                    String l = s.toLowerCase(java.util.Locale.ROOT);
                    if (l.contains("monstertruck")) return "大脚车";
                    if (l.contains("pickuptruck")) return "皮卡";
                }
            }
            return null;
        }
        return null;
    }
    /** v1.88 用游戏自己的分配器要一块内存（cave 'M'），失败返回 0 */
    static synchronized long malloc(int size) throws InterruptedException {
        if (!installed || size <= 0 || size > 0x10000) return 0;
        if (!call('M', size)) return 0;
        if (buf.getInt(0) != 0x5b09) return 0;
        return buf.getInt(4) & 0xffffffffL;
    }

    // ---------------- 车表：行名（车编号）+ 车的蓝图名 ----------------
    static final class Row { long id; String name; Row(long i, String n) { id = i; name = n; } }
    static ArrayList<Row> rows; static long rowsDt;
    static int next;
    static volatile String tableInfo = "";

    static ArrayList<Row> readRows(long dt) throws Exception {
        ArrayList<Row> out = new ArrayList<>();
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
            long data = Server.rd(m, dt + 0x20), num = Server.rd(m, dt + 0x24);
            if (data == 0 || num <= 0 || num > 500) { tableInfo = "车表行数不对 " + num; return out; }
            Names n = Names.get();
            // v1.83：实测元素间距不是 20（20 字节读出来的大半是乱的，但 5/11/16/21/26 号车都刷出来了）
            // → 在 num×128 字节范围里每 4 字节试一次 FName，名字是纯数字、Number=0 的就是车编号；行指针试 +8/+12
            java.util.TreeMap<Long, String> ids = new java.util.TreeMap<>();
            long end = data + Math.min(num * 128L, 0x10000);
            for (long e = data; e + 16 <= end && n != null; e += 4) {
                long idx = Server.rd(m, e), no = Server.rd(m, e + 4);
                if (idx <= 0 || idx > 0x400000 || no != 0) continue;
                String nm = n.name(m, (int) idx);
                if (nm == null || !nm.matches("\\d{1,9}")) continue;
                long id = Long.parseLong(nm);
                if (id == 0 || ids.containsKey(id)) continue;
                String bp = path(m, Server.rd(m, e + 8), n);
                if ("?".equals(bp)) bp = path(m, Server.rd(m, e + 12), n);
                ids.put(id, bp);
            }
            StringBuilder sb = new StringBuilder();
            for (java.util.Map.Entry<Long, String> en : ids.entrySet()) { out.add(new Row(en.getKey(), en.getValue())); if (sb.length() < 800) sb.append(en.getKey()).append('=').append(en.getValue()).append(' '); }
            if (ids.size() < 3) for (long k = 1; k <= 40; k++) if (!ids.containsKey(k)) { ids.put(k, "?"); out.add(new Row(k, "?")); }
            tableInfo = "车表 " + num + " 行，编号 " + ids.size() + " 个，名字表 " + (n == null ? "没找到" : "ok") + "：" + sb;
        }
        return out;
    }
    /** 行里找 "/Game/..." 的 FString（UTF-32），取蓝图名 */
    static String path(RandomAccessFile m, long row, Names nm) {
        if (row < 0x10000) return "?";
        // v1.84：路径多半是 FSoftObjectPath（FName），先按 FName 试
        if (nm != null) for (int o = 0; o < 0x100; o += 4) {
            long idx = Server.rd(m, row + o);
            if (idx <= 0 || idx > 0x400000 || Server.rd(m, row + o + 4) > 64) continue;
            String s = nm.name(m, (int) idx);
            if (s == null) continue;
            int k = s.lastIndexOf('/');
            String t = k >= 0 ? s.substring(k + 1) : s;
            int d = t.indexOf('.'); if (d > 0) t = t.substring(0, d);
            if (s.startsWith("/Game") || t.toLowerCase(java.util.Locale.ROOT).contains("vehicle")) return t;
        }
        for (int o = 0; o < 0x100; o += 4) {
            long p = Server.rd(m, row + o), len = Server.rd(m, row + o + 4);
            if (p < 0x10000 || len < 8 || len > 300) continue;
            String s = u32(m, p, (int) Math.min(len, 120));
            if (s != null && s.startsWith("/Game")) {
                String t = s.substring(s.lastIndexOf('/') + 1);
                int d = t.indexOf('.'); if (d > 0) t = t.substring(0, d);
                return t;
            }
        }
        return "?";
    }
    static String u32(RandomAccessFile m, long p, int n) {
        try {
            byte[] x = new byte[n * 4]; m.seek(p); m.readFully(x);
            StringBuilder s = new StringBuilder();
            for (int i = 0; i < n; i++) {
                int c = (x[4 * i] & 0xff) | (x[4 * i + 1] & 0xff) << 8 | (x[4 * i + 2] & 0xff) << 16 | (x[4 * i + 3] & 0xff) << 24;
                if (c == 0) break;
                if (c < 0x20 || c > 0x7e) return null;
                s.append((char) c);
            }
            return s.toString();
        } catch (Throwable e) { return null; }
    }

    static String nice(String bp) {
        String l = bp.toLowerCase();
        if (l.contains("uaz")) return "吉普";
        if (l.contains("dacia")) return "轿车";
        if (l.contains("buggy")) return "蹦蹦";
        if (l.contains("moto") && l.contains("sidecar")) return "三轮摩托";
        if (l.contains("moto")) return "摩托";
        if (l.contains("bus")) return "面包车";
        if (l.contains("pickup")) return "皮卡";
        if (l.contains("boat") || l.contains("aqua") || l.contains("pg117")) return "船";
        if (l.contains("mirado") || l.contains("coupe")) return "跑车";
        if (l.contains("rony")) return "皮卡";
        if (l.contains("scooter")) return "小绵羊";
        if (l.contains("snow")) return "雪地车";
        if (l.contains("tuk")) return "嘟嘟车";
        if (l.contains("brdm") || l.contains("armor")) return "装甲车";
        if (l.contains("glider")) return "滑翔机";
        return "?".equals(bp) ? "车" : bp;
    }

    /** v1.84：按车型分组（同一车型不同颜色合成一组，按一次换一个颜色）。名字读不到时一个编号一组 */
    static volatile boolean busy;
    static final class Group { String label; ArrayList<Long> ids = new ArrayList<>(); int at; }
    /** v1.93 车编号写死（用户：不要再读车表）。编号 = 游戏车表的行号，三张图同一张表（v1.89 读出来的）：
     *  图标名 Uaz469=吉普，dacia=轿车，boat_pg117=大船，car_002/003=吉普另外两款（敞篷/篷布，按 PUBG 吉普三款推测），buggy=蹦蹦，
     *  bike=摩托，bike_sidecart=三轮摩托，Chuan_001=快艇，car_005=面包车（推测）、car_006=跑车（v1.93 实测：刷出来是跑车）。car_007/008 编号没读到，先不放。 */
    static volatile ArrayList<Group> groups = fixed();
    static ArrayList<Group> fixed() {
        Object[][] t = { {"吉普", new long[]{1, 17, 20, 28}}, {"敞篷吉普", new long[]{4, 11, 21}}, {"篷布吉普", new long[]{5, 12, 22}},
            {"轿车", new long[]{2, 23}}, {"蹦蹦", new long[]{6, 14, 24}}, {"面包车", new long[]{10}}, {"跑车", new long[]{29, 30}},
            {"摩托", new long[]{9, 15, 25}}, {"三轮摩托", new long[]{8, 16, 26}}, {"快艇", new long[]{13, 18, 19}}, {"大船", new long[]{3, 27}},
            {"皮卡", new long[]{31}}, {"大脚车", new long[]{32}} };   // v3.47 用户：「皮卡」按钮里有两辆车，其中一辆是大脚车 → 拆成两个按钮。
            // 车表 31=car_007、32=car_008（图标名），哪个是大脚车先按 31 皮卡 / 32 大脚车，第一次刷时 trucks() 读车表行里的蓝图路径（MonsterTruck / PickUpTruck）核对，反了就对调
        ArrayList<Group> out = new ArrayList<>();
        for (Object[] r : t) { Group g = new Group(); g.label = (String) r[0]; for (long id : (long[]) r[1]) g.ids.add(id); out.add(g); }
        return out;
    }
    static String base(String bp) {
        // BP_Vehicle_UAZ_01_C → uaz；去掉颜色/皮肤后缀数字
        String t = bp.replaceAll("(?i)^BP_(Vehicle_)?", "").replaceAll("(?i)_C$", "").replaceAll("(_?\\d+)+$", "");
        return t.toLowerCase(java.util.Locale.ROOT);
    }
    static void makeGroups() {
        java.util.LinkedHashMap<String, Group> g = new java.util.LinkedHashMap<>();
        for (Row r : rows) {
            String key = "?".equals(r.name) ? "#" + r.id : base(r.name);
            Group x = g.get(key);
            if (x == null) { x = new Group(); x.label = "?".equals(r.name) ? r.id + " 号" : nice(r.name); g.put(key, x); }
            x.ids.add(r.id);
        }
        ArrayList<Group> out = new ArrayList<>(g.values());
        for (Group x : out) if (x.ids.size() > 1) x.label += "（" + x.ids.size() + " 色）";
        groups = out;
    }
    /** 读人物位置/朝向（V0），算面前 dist 厘米的点写进 buf+40..60 */
    static String ahead(float dist, float up) throws InterruptedException {
        if (!installed) return state;
        if (!call(0)) return "补丁代码没反应（进对局、落地后再按）";
        if ((buf.getInt(8)) == 0) return "还没有人物";
        float x = Float.intBitsToFloat(buf.getInt(12)), y = Float.intBitsToFloat(buf.getInt(16)), z = Float.intBitsToFloat(buf.getInt(20));
        float qx = Float.intBitsToFloat(buf.getInt(72)), qy = Float.intBitsToFloat(buf.getInt(76)), qz = Float.intBitsToFloat(buf.getInt(80)), qw = Float.intBitsToFloat(buf.getInt(84));
        double yaw = Math.atan2(2 * (qw * qz + qx * qy), 1 - 2 * (qy * qy + qz * qz));
        if (Double.isNaN(yaw)) yaw = 0;
        buf.putFloat(40, (float) (x + dist * Math.cos(yaw)));
        buf.putFloat(44, (float) (y + dist * Math.sin(yaw)));
        buf.putFloat(48, z + up);
        // 人机面朝玩家（转 180°），车和人朝向一致
        buf.putFloat(52, 0f); buf.putFloat(56, (float) Math.toDegrees(yaw) + (up < 100 ? 180f : 0f)); buf.putFloat(60, 0f);
        return null;
    }
    /** 第一次：读车表、分组，刷新菜单出按钮 */
    static String loadTable() throws Exception {
        String e = ahead(600, 150); if (e != null) return e;
        long dt = buf.getInt(36) & 0xffffffffL;
        if (dt == 0) return "这局没有车表（GameMode " + Integer.toHexString(buf.getInt(28)) + "）";
        if (rows == null || rowsDt != dt) {
            state = "刷车：正在读车表…"; GameOverlay.refreshAny();
            rows = readRows(dt); rowsDt = dt; next = 0;
            makeGroups();
            note("刷车 " + tableInfo);
        }
        return null;
    }
    /** v1.88 进对局自动读车表（用户不想每次手动点「读车表」）：等人物出生后读，读到就出车按钮 */
    static volatile int loadGen;
    static void matchLoaded() {
        final int g = ++loadGen;
        new Thread(() -> {
            try {
                Thread.sleep(4000);
                for (int i = 0; i < 40 && g == loadGen && Con.inMatch; i++) {
                    if (!busy) {
                        busy = true;
                        String e;
                        try { e = loadTable(); } finally { busy = false; }
                        if (e == null && groups != null && !groups.isEmpty()) {
                            if (state.startsWith("刷车：正在读") || state.startsWith("刷车：已装")) state = "刷车：车表已自动读好（" + groups.size() + " 种车）";
                            GameOverlay.refreshAny(); return;
                        }
                    }
                    Thread.sleep(2000);
                }
            } catch (Throwable e) { note("刷车：自动读车表出错 " + e); }
        }, "zf-vehload").start();
    }
    /** 菜单按钮：读车表（读完菜单里出现每种车的按钮） */
    public static void spawnNext() { spawnGroup(-1); }
    public static void spawnGroup(final int gi) {
        if (busy) { GameOverlay.toastAny(state); return; }
        busy = true;
        new Thread(() -> {
            try {
                // v3.47：不再 ahead() 写落点后另一次加锁再调（中间会被人机/传送改掉落点）；info() 读人物，place() 一次加锁写落点+调用，并确认真生成
                double[] p = info();
                if (p == null) { GameOverlay.toastAny(state = "刷车：" + (installed ? "还没有人物（进对局、落地后再按）" : state)); return; }
                long dt = buf.getInt(36) & 0xffffffffL;
                if (dt == 0) { GameOverlay.toastAny(state = "刷车：这局没有车表"); return; }
                ArrayList<Group> gs = groups;
                if (gs == null || gs.isEmpty()) { GameOverlay.toastAny(state = "刷车：车表里没读到车编号（" + tableInfo.split("：")[0] + "）"); return; }
                if (gi < 0) { GameOverlay.toastAny(state = "刷车：读到 " + gs.size() + " 种车，菜单里点车名刷"); return; }
                trucks(dt);
                Group g = gs.get(gi % gs.size());
                long id = g.ids.get(g.at++ % g.ids.size());
                // v4.10：人在车上（控制器的 Pawn 不是人物）→ 落点离车更远；人在游泳 → 只能刷船
                long pawn = (long) p[6]; boolean inVeh = false;
                long bb = Mods.base();
                if (bb != 0 && pawn != 0) try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) { inVeh = !NoFall.isChar(m, bb, pawn); } catch (Throwable ignored) {}
                String r = place(g.label, id, p[0], p[1], p[2], p[3], inVeh, !inVeh && swimming(pawn));
                String s = "刷车：" + r;
                note(s); state = s;
                GameOverlay.toastAny(s);
                quickToast = false;
            } catch (Throwable e) { GameOverlay.toastAny(state = "刷车：出错 " + e); note(state); }
            finally { busy = false; GameOverlay.refreshAny(); }
        }, "zf-veh").start();
    }

    /** v2.40 用户：枪打刷出来的车不会爆。刷出 2 秒后：把车的 bCanBeDamaged 打开，并把车的血量/上限/伤害倍数/能否受伤写进游戏日志（诊断导出能看到）。
     *  NZVehicle 反射：MaxHealth +0x2f8、DamageScale +0x2fc、Health +0x308。 */
    static void carCheck() {
        if (Ed.ED == 2) return;
        try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
        Con.exec("EnableCheats");
        // v4.10：去掉 getall（只是让游戏往日志里写，现在不读日志也不让游戏写日志）
        // v4.11：撤掉 v4.10 训练岛「set NZVehicle bCanBeDamaged 0」（车不受伤是治标，用户：车是他打爆的，能打爆是正常的）。训练岛和别的图一样：车能受伤、能被打爆。
        Con.exec("set NZVehicle bCanBeDamaged 1");
        note("刷车：已打开车辆可受伤");
    }

    // ---------------- v1.84/v1.85 刷人机 ----------------
    // v1.84 用教学关的 ServerAddBotAt（参数 1 = GameMode 人机类数组第 1 个，难度写死 1），刷出来站着不动。
    // v1.85 改走对局自己的加人机：cave 'A<难度>' → 0x1014898(GM, 难度) 取人机类（BotDifficultMapping，没有就用 PVPBotTypeIndex）
    //   → 0x10112d0 AddOneBot(GM, 类, 难度)（难度写进 AIController+0x3ec）→ 和 AddBotAt 一样 GM vt+0x5f8 / +0x39c 生出人物、传送到位置。
    //   是 PVP 人机控制器（vt+0x344 == OnPossess 0xc1bbd8）时：标志 1 = ForceGiveAllEquipToAI 给装备；
    //   标志 2 = 行为树没跑（BrainComponent +0x324 为空）就 SwitchToBattleBehaviorTree；标志 4 = 强制切战斗树。
    //   buf：+88 类，+92 控制器，+96 人物，+100 出生岛树，+104 战斗树，+108 Brain（前），+112 难度，+120 Brain（后）。
    //   结果码 +0：5b05 GameMode 不对，5b06 没拿到人机类/没刷出来，5b07 不是 PVP 人机控制器，5b08 成功。
    public static volatile String botState = "刷人机：还没试";
    public static volatile String botInfo = "";
    static final String[] DIFF = { "", "简单", "普通", "困难" };

    /** v3.42：上一次 info() 填的本机玩家控制器（buf+4） */
    static long lastPc() { ByteBuffer x = buf; return x == null ? 0 : x.getInt(4) & 0xffffffffL; }
    /** 读一次人物位置：{x, y, z, yaw(度), GameMode, World}；没人物返回 null */
    static synchronized double[] info() throws InterruptedException {
        if (!installed || !call(0) || buf.getInt(0) != 0x5b02 || buf.getInt(8) == 0) return null;
        float x = Float.intBitsToFloat(buf.getInt(12)), y = Float.intBitsToFloat(buf.getInt(16)), z = Float.intBitsToFloat(buf.getInt(20));
        float qx = Float.intBitsToFloat(buf.getInt(72)), qy = Float.intBitsToFloat(buf.getInt(76)), qz = Float.intBitsToFloat(buf.getInt(80)), qw = Float.intBitsToFloat(buf.getInt(84));
        double yaw = Math.atan2(2 * (qw * qz + qx * qy), 1 - 2 * (qy * qy + qz * qz));
        if (Double.isNaN(yaw)) yaw = 0;
        return new double[] { x, y, z, Math.toDegrees(yaw), buf.getInt(28) & 0xffffffffL, buf.getInt(24) & 0xffffffffL, buf.getInt(8) & 0xffffffffL };
    }
    /** v2.31 房主：把某个人物传到 (x,y,z)，朝向 yaw（度）。游戏自己的 K2_TeleportTo，服务器这边改位置会同步给那台客户端 */
    static synchronized boolean teleport(long pawn, double x, double y, double z, double yaw) throws InterruptedException {
        if (!installed) return false;
        if (buf == null && !call(0)) return false;
        buf.putFloat(40, (float) x); buf.putFloat(44, (float) y); buf.putFloat(48, (float) z);
        buf.putFloat(52, 0f); buf.putFloat(56, (float) yaw); buf.putFloat(60, 0f);
        buf.putInt(4, 0);
        return call('T', pawn) && buf.getInt(0) == 0x5b0d && (buf.getInt(4) & 0xff) != 0;
    }
    /** 在 (x,y,z) 加一个人机，面朝 yaw。返回结果码（0 = cave 没反应） */
    static synchronized int addBot(int diff, double x, double y, double z, double yaw, int flags) throws InterruptedException {
        if (!installed) return 0;
        if (buf == null && !call(0)) return 0;
        for (int o = 88; o < 128; o += 4) buf.putInt(o, 0);
        buf.putFloat(40, (float) x); buf.putFloat(44, (float) y); buf.putFloat(48, (float) z);
        buf.putFloat(52, 0f); buf.putFloat(56, (float) yaw); buf.putFloat(60, 0f);
        buf.putInt(116, flags);
        if (!call('A', diff)) return 0;
        int code = buf.getInt(0);
        botInfo = "结果 " + Integer.toHexString(code) + " 类 " + h(88) + " 控制器 " + h(92) + " 人物 " + h(96)
            + " 出生岛树 " + h(100) + " 战斗树 " + h(104) + " 行为树前 " + h(108) + " 后 " + h(120) + " 难度 " + buf.getInt(112);
        return code;
    }
    static String h(int o) { return Integer.toHexString(buf.getInt(o)); }
    /** v1.86 对已刷出的人机控制器再给一次装备 / 切战斗树（cave 'E'） */
    static synchronized int equip(long ctrl, int flags) throws InterruptedException {
        if (!installed || ctrl < 0x10000) return 0;
        if (buf == null && !call(0)) return 0;
        for (int o = 88; o < 128; o += 4) buf.putInt(o, 0);
        buf.putInt(116, flags);
        if (!call('E', ctrl)) return 0;
        return buf.getInt(0);
    }
    /** v1.98 给指定人物（人机）发物品（cave 'I'）。返回标记：5b0b 成功、5b0c 没背包组件、5b06 人物空；+140 能放几个、+144 GIVE 返回 */
    static synchronized int giveTo(long pawn, long id, int n) throws InterruptedException {
        if (!installed || pawn < 0x10000) return 0;
        if (buf == null && !call(0)) return 0;
        buf.putInt(128, (int) (id / 10000)); buf.putInt(132, (int) (id % 10000)); buf.putInt(136, Math.max(1, n));
        buf.putInt(140, 0); buf.putInt(144, 0);
        if (!call('I', pawn)) return 0;
        return buf.getInt(0);
    }
    /** 最后一次 A/E 的控制器地址 */
    static synchronized long lastCtrl() { return buf == null ? 0 : buf.getInt(92) & 0xffffffffL; }
    /** 装备组件情况：AutoGiveEquipComponent(+0x410) 的开关字节 +0xd5、装备条数 +0xb0；AIEquipComponent(+0x3f0)；人物 +0x2c0 */
    static String equipInfo(long ctrl) {
        if (ctrl < 0x10000) return "无控制器";
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
            long ag = Server.rd(m, ctrl + 0x410), ae = Server.rd(m, ctrl + 0x3f0), pawn = Server.rd(m, ctrl + 0x2c0);
            String s = "自动给装备组件 " + Long.toHexString(ag);
            if (ag > 0x10000) s += "（开关 " + (Server.rd(m, ag + 0xd4) >> 8 & 0xff) + "，条数 " + Server.rd(m, ag + 0xb0) + "）";
            return s + " 装备组件 " + Long.toHexString(ae) + " 人物 " + Long.toHexString(pawn) + " 行为树 " + Long.toHexString(Server.rd(m, ctrl + 0x324));
        } catch (Throwable e) { return "读不到 " + e; }
    }
    static String codeText(int c) {
        switch (c) {
            case 0x5b08: return "成功";
            case 0x5b07: return "刷出来了，但不是对局人机（不会给装备）";
            case 0x5b06: return "游戏没给人机类或没刷出来";
            case 0x5b05: return "这局的 GameMode 不支持加人机";
            case 0: return "补丁代码没反应（进对局后再按）";
            default: return "未知结果 " + Integer.toHexString(c);
        }
    }

    /** 菜单按钮：面前 8 米刷一个（当前难度） */
    public static void spawnBot() { spawnBot(false); }
    public static void spawnBot(final boolean fixed) {
        if (busy) { GameOverlay.toastAny(state); return; }
        busy = true;
        new Thread(() -> {
            try {
                double[] p = info();
                if (p == null) { GameOverlay.toastAny(botState = "刷人机：还没有人物（进对局、出生后再按）"); return; }
                double r = Math.toRadians(p[3]);
                int c = addBot(Bots.diff, p[0] + 800 * Math.cos(r), p[1] + 800 * Math.sin(r), p[2] + 50, p[3] + 180, Bots.armed(fixed) ? 1 | 4 : 4);
                if (c == 0x5b08 || c == 0x5b07) Bots.manual(lastCtrl(), fixed);
                botState = "刷" + (fixed ? "固定" : "自由") + "人机（" + (Bots.armed(fixed) ? "带枪" : "不带枪") + "）：" + codeText(c);
                note(botState + "；" + botInfo);
                GameOverlay.toastAny(botState);
            } catch (Throwable e) { GameOverlay.toastAny(botState = "刷人机：出错 " + e); note(botState); }
            finally { busy = false; GameOverlay.refreshAny(); }
        }, "zf-bot").start();
    }

    /** FName 名字表（GNames）：找 "None"(0号) 和 "ByteProperty"(1号) 两个条目，反推块表 */
    static final class Names {
        long chunks; boolean shift = true;
        static Names cached;
        /** 小的 int 集合（开放寻址），避免 HashSet<Long> 装箱太慢 */
        static final class ISet {
            int[] t = new int[1 << 14]; int n;
            void add(int v) { if (v == 0 || n > t.length / 2) return; int h = (v * 0x9E3779B1) >>> 18; while (t[h] != 0 && t[h] != v) h = (h + 1) & (t.length - 1); if (t[h] == 0) { t[h] = v; n++; } }
            boolean has(int v) { if (v == 0) return false; int h = (v * 0x9E3779B1) >>> 18; while (t[h] != 0) { if (t[h] == v) return true; h = (h + 1) & (t.length - 1); } return false; }
        }
        /** v1.82：先在 libUE4 自己的数据段里找 GNames 全局（几 MB，几秒），找不到才全内存扫 */
        static Names fast(RandomAccessFile m) throws Exception {
            ArrayList<long[]> lib = new ArrayList<>(), anon = regions();
            try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader("/proc/self/maps"))) {
                String l; boolean after = false;
                while ((l = r.readLine()) != null) {
                    String[] p = l.trim().split("\\s+");
                    String name = p.length > 5 ? p[5] : "";
                    boolean isLib = name.endsWith("libUE4.so");
                    String[] ab = p[0].split("-");
                    long s0 = Long.parseLong(ab[0], 16), e0 = Long.parseLong(ab[1], 16);
                    if (p[1].startsWith("rw") && (isLib || after && (name.isEmpty() || name.startsWith("[anon:.bss")))) lib.add(new long[] { s0, e0 });
                    after = isLib || after && name.startsWith("[anon:.bss");
                }
            }
            long[] lo = new long[anon.size()], hi = new long[anon.size()];
            for (int i = 0; i < lo.length; i++) { lo[i] = anon.get(i)[0]; hi[i] = anon.get(i)[1]; }
            long t0 = System.currentTimeMillis(), size = 0; int cand = 0;
            for (int pass = 0; pass < 2; pass++) for (long[] g : lib) {
                int n = (int) (g[1] - g[0]); size += pass == 0 ? n : 0;
                byte[] bb = new byte[n];
                try { m.seek(g[0]); m.readFully(bb); } catch (Throwable e) { continue; }
                int[] w = new int[n / 4];
                ByteBuffer.wrap(bb).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().get(w);
                for (int i = 0; i < w.length; i++) {
                    long v = w[i] & 0xffffffffL;
                    if (v < 0x10000 || (v & 3) != 0 || !in(lo, hi, v)) continue;
                    cand++;
                    // pass 0：这个字本身就是块 0 指针（块表就在 libUE4 数据段里）；pass 1：这个字指向块表
                    long chunks = pass == 0 ? g[0] + 4L * i : v;
                    long c0 = pass == 0 ? v : Server.rd(m, v);
                    if (pass == 1 && (c0 < 0x10000 || !in(lo, hi, c0))) continue;
                    Names r = check(m, c0, chunks);
                    if (r != null) { note("刷车 名字表在 libUE4 数据段找到 块表=" + Long.toHexString(chunks) + " 方式" + pass + "（" + (System.currentTimeMillis() - t0) + " ms）"); return r; }
                }
            }
            note("刷车 libUE4 数据段没找到名字表（" + size / 1024 + " KB，候选 " + cand + "，" + (System.currentTimeMillis() - t0) + " ms）");
            return null;
        }
        static boolean in(long[] lo, long[] hi, long v) {
            int a = 0, b = lo.length - 1;
            while (a <= b) { int c = (a + b) >>> 1; if (v < lo[c]) b = c - 1; else if (v >= hi[c]) a = c + 1; else return true; }
            return false;
        }
        static Names check(RandomAccessFile m, long c0, long chunks) {
            long e0 = Server.rd(m, c0);
            if (e0 < 0x10000 || (e0 & 3) != 0) return null;
            if (!"None".equals(ascii(m, e0 + 8, 8)) || Server.rd(m, e0) != 0) return null;
            long e1 = Server.rd(m, c0 + 4);
            if (e1 < 0x10000 || !"ByteProperty".equals(ascii(m, e1 + 8, 16))) return null;
            long f1 = Server.rd(m, e1);
            if (f1 != 1 && f1 != 2) return null;
            // v2.99：服务端进程里「方式0」命中过假块表（只有块 0 对，块 1 是乱的）→ 序号 ≥16384 的名字（BP_…_C 类名、/Game 路径）全读成 ?
            // → 全员找人 0 个、加速请求没人、NetRate 找不到 NetDriver、模式蓝图查不到。现在要求块 1 第一个条目序号正好是 16384。
            long c1 = Server.rd(m, chunks + 4);
            if (c1 < 0x10000 || (c1 & 3) != 0) return null;
            long e16 = Server.rd(m, c1);
            if (e16 < 0x10000) return null;
            long f16 = Server.rd(m, e16);
            if ((f1 == 2 ? f16 >> 1 : f16) != 16384) return null;
            Names r = new Names(); r.chunks = chunks; r.shift = f1 == 2;
            return r;
        }
        static synchronized Names get() throws Exception {
            if (cached != null) return cached;
            try (RandomAccessFile mm = new RandomAccessFile("/proc/self/mem", "r")) {
                Names f = fast(mm);
                if (f != null) return cached = f;
            }
            ArrayList<long[]> rs = regions();
            long total = 0; for (long[] g : rs) total += g[1] - g[0];
            ISet none = new ISet(), byteP = new ISet();
            byte[] bb = new byte[1 << 20];
            int[] w = new int[bb.length / 4];
            long t0 = System.currentTimeMillis();
            try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
                // 第 1 遍：找 "None"(序号字 0) 和 "ByteProperty"(序号字 2 或 1) 两种条目
                long done = 0;
                for (long[] g : rs) for (long a = g[0]; a < g[1]; a += bb.length) {
                    int n = (int) Math.min(bb.length, g[1] - a);
                    try { m.seek(a); m.readFully(bb, 0, n); } catch (Throwable e) { break; }
                    done += n;
                    for (int i = 8; i + 13 <= n; i += 4) {
                        byte c = bb[i];
                        if (c == 'N') {
                            if (bb[i + 1] == 'o' && bb[i + 2] == 'n' && bb[i + 3] == 'e' && bb[i + 4] == 0
                                && bb[i - 8] == 0 && bb[i - 7] == 0 && bb[i - 6] == 0 && bb[i - 5] == 0) none.add((int) (a + i - 8));
                        } else if (c == 'B') {
                            if (bb[i + 12] == 0 && bb[i + 1] == 'y' && bb[i + 4] == 'P' && bb[i + 11] == 'y' && (bb[i - 8] == 2 || bb[i - 8] == 1) && bb[i - 7] == 0
                                && new String(bb, i, 12, "ISO-8859-1").equals("ByteProperty")) byteP.add((int) (a + i - 8));
                        }
                    }
                    if (((done >> 20) & 63) == 0) { state = "刷车：读名字表 1/3 " + done * 100 / Math.max(1, total) + "%"; GameOverlay.refreshAny(); }
                }
                note("刷车 名字表候选 None " + none.n + " ByteProperty " + byteP.n + "（" + (System.currentTimeMillis() - t0) + " ms，" + total / 1048576 + " MB）");
                if (none.n == 0 || byteP.n == 0) return null;
                // 第 2 遍：块 0 = 相邻两个字分别指向 None 和 ByteProperty
                long chunk0 = 0; done = 0;
                outer:
                for (long[] g : rs) for (long a = g[0]; a < g[1]; a += bb.length) {
                    int n = (int) Math.min(bb.length, g[1] - a);
                    try { m.seek(a); m.readFully(bb, 0, n); } catch (Throwable e) { break; }
                    done += n;
                    ByteBuffer.wrap(bb, 0, n).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().get(w, 0, n / 4);
                    for (int i = 0; i + 1 < n / 4; i++) if (byteP.has(w[i + 1]) && none.has(w[i])) { chunk0 = a + 4L * i; break outer; }
                    if (((done >> 20) & 63) == 0) { state = "刷车：读名字表 2/3 " + done * 100 / Math.max(1, total) + "%"; GameOverlay.refreshAny(); }
                }
                note("刷车 名字块0=" + Long.toHexString(chunk0) + "（" + (System.currentTimeMillis() - t0) + " ms）");
                if (chunk0 == 0) return null;
                Names r = new Names();
                r.shift = Server.rd(m, Server.rd(m, chunk0 + 4)) == 2;
                // 第 3 遍：块表 = 存着块 0 地址的那个字
                int c0 = (int) chunk0; done = 0;
                outer3:
                for (long[] g : rs) for (long a = g[0]; a < g[1]; a += bb.length) {
                    int n = (int) Math.min(bb.length, g[1] - a);
                    try { m.seek(a); m.readFully(bb, 0, n); } catch (Throwable e) { break; }
                    done += n;
                    ByteBuffer.wrap(bb, 0, n).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().get(w, 0, n / 4);
                    for (int i = 0; i < n / 4; i++) if (w[i] == c0) { r.chunks = a + 4L * i; break outer3; }
                    if (((done >> 20) & 63) == 0) { state = "刷车：读名字表 3/3 " + done * 100 / Math.max(1, total) + "%"; GameOverlay.refreshAny(); }
                }
                if (r.chunks == 0) r.chunks = -chunk0;   // 没找到块表：只能查前 16384 个
                note("刷车 名字块表=" + Long.toHexString(r.chunks) + " 移位=" + r.shift + "（" + (System.currentTimeMillis() - t0) + " ms）");
                return cached = r;
            }
        }
        String name(RandomAccessFile m, int idx) {
            long c0 = chunks < 0 ? -chunks : 0;
            long chunk = chunks < 0 ? (idx < 16384 ? c0 : 0) : Server.rd(m, chunks + 4L * (idx / 16384));
            if (chunk == 0) return null;
            long e = Server.rd(m, chunk + 4L * (idx % 16384));
            if (e == 0) return null;
            long f = Server.rd(m, e);
            if ((shift ? f >> 1 : f) != idx) return null;
            return ascii(m, e + 8, 200);
        }
    }
    static String ascii(RandomAccessFile m, long p, int n) {
        if (p < 0x10000) return null;
        try {
            byte[] x = new byte[n]; m.seek(p); m.readFully(x);
            int e = 0; while (e < n && x[e] != 0) e++;
            return new String(x, 0, e, "ISO-8859-1");
        } catch (Throwable t) { return null; }
    }
    static void note(String s) { LogCap.note(s); Server.mark(s); }
    static ArrayList<long[]> regions() throws java.io.IOException {
        ArrayList<long[]> rs = new ArrayList<>();
        try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader("/proc/self/maps"))) {
            String l;
            while ((l = r.readLine()) != null) {
                String[] p = l.trim().split("\\s+");
                if (!p[1].startsWith("rw")) continue;
                String name = p.length > 5 ? p[5] : "";
                if (!(name.isEmpty() || name.startsWith("[anon:") || name.equals("[heap]")) || name.contains("stack")) continue;
                String[] ab = p[0].split("-");
                long s = Long.parseLong(ab[0], 16), e = Long.parseLong(ab[1], 16);
                if (e > 0xffffffffL) continue;
                rs.add(new long[] { s, e });
            }
        }
        return rs;
    }
}
