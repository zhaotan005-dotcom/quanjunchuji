package com.zframe.inject;

import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;

/** 训练岛（训练场 = BP_NZBRPracticeGameMode_C，游戏自带「训练」入口）。
 *
 *  v3.42 根因（RE + v3.41 block-1119-195412 日志）：
 *  - 训练场是「一个 DS 开 N 份岛」：GameMode BeginPlay 0x1081d70 建 N 个位置（GM+0xa40 数组，每个 0x34 字节，
 *    [0]=块号，+4 本位置的 PlayerStart 数组 (data,num,max)，+0x10 玩家数组；个数 +0xa44，块数 +0xa4c，BP 默认 SlotCount=9）。
 *  - 单机里玩家 Login 比 GameMode BeginPlay 早 → 位置数组空 → 不进位置（v3.37 空队伍补丁让它跳过）。
 *
 *  v3.43 根因补全（RE + train-1119-204331 日志）：
 *  - SetupPlayerStartActor 0x1082868（Tick 里第一次拿到地块管理器 GM+0xa3c 时跑一次）先调 0x1164d90(管理器, GM+0xa4c)：
 *    专用服（NetMode==1）在这里写 管理器+0x43c = 块数 9；单机/listen 直接退出 → 管理器块数还是默认 16。
 *    出生点 = 块偏移 0x1163e2c(管理器+0x43c 算边长) + BP 模板 SpawnPointArray[0]=(53920,-48810,-15900)：
 *    用 16（4×4）算 → 位置 0 出生点 (-156080,-258810)（日志 20:42:29 正是 4×4 排法：0..3 同 X）。
 *    客户端 LoadBlock(块号, 9) 又把 +0x43c 改成 9（3×3）→ 岛副本放在 (-86080,-188810) 一带 → 出生点在岛西南约 1 公里海上。
 *    这就是「人在岛西南很远的海上」。修：0x10828b0 的 bl 0x1164d90 换成 str r1,[r0,#0x43c]（只做专用服那一步写块数；
 *    那个函数其余部分单机本来就不跑）。补丁没赶上时：写 +0x43c、清各位置 PlayerStart 数、GM+0xa3c 置 0 → Tick 按 9 重建。
 *  - 出生点不是位置里的 3 个 float（v3.42 读成 0,0,0）：+4 是 PlayerStart 数组。
 *  - 原生摆人：AddPlayerToSlot 0x1083a98 只要 PC+0x558 有 PlayerState、PC+0xbdc<0、PC 在某位置玩家数组里，就自己
 *    写 PC+0xbdc 块号/+0xbe0 块数 → FindPlayerStart 0x2eca4ac（→ ChoosePlayerStart_Implementation 0x10847xx 从位置 PlayerStart 里挑）
 *    → PC SetInitialLocation(vt+0x30c) → StartSpot → 人物 SetActorLocation 0x274ca58 / SetActorRotation 0x274cd14。
 *    它不依赖 +a70（Login 在 +a70 已置时也是直接调它）。v3.42 排进 +a24 等 OnAllDuplicateLevelLoaded，但那要 Landscape 层先加载完，
 *    人不在岛上就等不到（本次日志没有 OnLevelStreamingLayerLoadCompleted）→ 死等。v3.43 改为位置建好就直接调 AddPlayerToSlot。
 *  - 射击训练区：LogicLevel（game/pak，只读解析）shootreturn=(47835,-48630,-15850)、BP_ShootPracticeBase 盒子 (47800,-48610)，
 *    出生模板离它约 61 米 → 原生出生点就在射击区边上，不用再传。只做诊断。全部未实测。
 *
 *  v3.44（RE + 21:05 用户 v3.43 诊断 t3）：v3.43 LoadBlock(块 0, 块数 9) 之后整整 50 秒地形块一直 1。
 *  - 三版对比：v3.41 只调 LoadBlock(0,1) → 块 0 正常流式加载（地形块 1→17、OnLevelStreamingLayerLoadCompleted）；
 *    v3.42、v3.43 都在 LoadBlock 后多调了一次 FillLayersWithCacheData(管理器, +0x37c 服务器层, 块号) → 两版都一点都没加载。
 *    冻结（时间缩放）只有 v3.43 有，v3.42 没冻也没加载 → 冻结不是主因。
 *  - 服务器层不是客户端该填的：客户端原生只有 Client Load Block → LoadBlock → Fill(+0x370)。pak 只读解析 Main.umap：
 *    ServerLayersSettings = Landscape, AlwaysLoad, Ocean, ServerOnly, ServerOnlyMajority, ServerOnlyGroundObjects ——
 *    跟客户端层同名的 Landscape / AlwaysLoad 也在里面。FillLayers 0x11649bc 按「管理器当前层 +0x2f4 的名字」去缓存里找，
 *    找到就 TileLayer 0x116543c → 同一个块号第二次给 Landscape/AlwaysLoad 层生成「%s_LevelInstance_%d」：实例名和第一次完全一样，
 *    0x1165bf4（非 world+0x6d8==3 时不查旧的）再 NewObject 一个同名关卡流对象（UE 同名同 Outer 会在原地重建那个对象）、
 *    层 vt+0x14 再 AddLevel 一次、+0x358 再追加一次（推断，静态读码）。→ 去掉服务器层填充，完全按客户端原生步骤。
 *  - 冻结：管理器 Tick 0x115e7dc 用自己的 DeltaSeconds，Update 0x115eb7c 取 PC+0x2c0 人物根组件坐标 → 只改人物
 *    CustomTimeDilation 本来不该卡流式，但用户要求不影响流式的冻结 → 改成只停重力：移动组件 GravityScale(+0x120)=0、速度清零，
 *    地形到了再还原（不改时间缩放）。
 *  - 诊断 stream：管理器 +0x400 首次加载标志 / +0x43c / +0x440 / +0x2c8 / 两份缓存层名、每层关卡数、包住人物的关卡数、
 *    层更新计数（+0x10，看 Default 策略 Update 有没有在跑）、+0x358 关卡流对象数（重复指针数）、加载/可见数、world 流关卡数。
 *
 *  v3.45 根因（RE + 23:15 用户 v3.44 诊断 slot-1119-231553，证据见 notes/history.md v3.45）：
 *  - v3.44 的「服务器层重复填充」推断被推翻（+37c 0 个、+358 重复指针 0）。
 *  - 真因：管理器 +0x400 是 bFirstUpdate（构造 0x115b320 置 1）。客户端 Tick 0x115e7dc：+0x400!=0 就不调 vt+0x304 Update，
 *    只有 FirstLoad（vt+0x2f8；TileMode 0x1165e34 → 0x115f130，末尾 0x115f8f0 清 0）会清它。原生 Client Load Block 0x1086960 =
 *    LoadBlock(PC+bdc, PC+be0) 之后紧接着 PC vt+0x8bc 非空就调 管理器 FirstLoad。v3.42~v3.44 只抄了 LoadBlock 这一半 →
 *    +0x400 一直 1（v3.44 诊断 93 秒都是 1）→ 一帧都不 Update → 109 个关卡流对象从没提交加载、world StreamingLevels 0。
 *    游戏日志也没有「ANZStreamingLevelsManagerActor::FirstLoad called」（同类别同级别的 LoadBlock 行有打出来）。
 *  - 修：AddPlayerToSlot 之后直接调原生 Client Load Block 0x1086960(PC)；之后 +0x400 仍 1 才读 PC vt+0x8bc 并直接调同一个 FirstLoad。
 *  - 诊断偏移改正：层 +0x10 是管理器弱指针（116169 = 管理器 ObjectIndex），不是更新计数；关卡在层的集合对象里（+0x1c，Landscape 类层 +0x34/+0x2c/+0x24），
 *    集合 +0x14/+0x18 关卡项数组。 */
final class Island {
    /** v3.45：原生 Client Load Block 实现（Practice PC 的 RPC _Implementation）；TileMode FirstLoad（vt+0x2f8）；PC vt+0x8bc（Client Load Block 里决定调不调 FirstLoad） */
    static final long CLIENT_LOAD_BLOCK = 0x1086960L, FIRST_LOAD_TILE = 0x1165e34L, PC_GET_8BC = 0xd64740L;
    static final long LOAD_BLOCK = 0x1163f3cL, FIND_SLOT = 0x10845b0L, ADD_TO_SLOT = 0x1083a98L, REALLOC = 0x1989d38L;
    /** 训练场（Practice 模式）登录时 0x10845b0 找队伍返回空，0x1084f6c ldr [r8,#0x14] 崩（fault 0x14，19:07 实测诊断）。
     *  改成 movs r8,r0 / ldrne / cmpne / beq 0x10850a8（没队伍就走原来的「跳过」分支）。空位置由 Island.join 事后补上。 */
    static final long PR = 0x1084f2cL;
    static final String PR_O = "0080a0e1580596e5000050e35a00000a", PR_N = "0080b0e158059615000050135a00000a";
    /** v3.43：SetupPlayerStartActor 里 0x10828b0 bl 0x1164d90 → str r1,[r0,#0x43c]（r0=管理器 GM+0xa3c，r1=GM+0xa4c 块数）。 */
    static final long PB = 0x10828b0L;
    static final String PB_O = "368903eb", PB_N = "3c1480e5";
    /** BP_NZBRPracticeGameMode 默认 SpawnPointArray[0]；LogicLevel shootreturn（pak 解析，原始坐标，块偏移前） */
    static final double TPL_X = 53920, TPL_Y = -48810, TPL_Z = -15900, SHOOT_X = 47835, SHOOT_Y = -48630, SHOOT_Z = -15850;
    static volatile String fix = "训练场空队伍补丁：没装";
    /** v3.46 同帧原生摆人（game/re/islecave.py 生成，emu_islecave.py unicorn 过）：v3.45 实测进岛先在虚空停约 1.3 秒——
     *  SetupPlayerStartActor 在 23:30:34.477 建好出生点，我们的线程轮询 + 三次 gcall 到 36.106 才 AddPlayerToSlot。
     *  改成游戏线程自己做：Login 没找到位置时（PR 补丁最后的 beq）把 PC 记进缓冲区；Tick 0x1082e84 调 SetupPlayerStartActor
     *  之后同一帧按 Login 原步骤 FindSlot → 追加进位置玩家数组 → AddPlayerToSlot → Client Load Block(PC) →（+400 仍 1）FirstLoad。
     *  cave 放在 GM 面板不用的 GiveItem 函数里 0x171e2c0..0x171e3f3（PickAnim 用 0x171e200..0x171e28b）。
     *  缓冲区：+0 PC（Login 写，S 看过就清）+4 S 调用次数 +8 阶段（1 找位置 2 已追加 3 已摆人 4 Client Load Block 已跑 5 FirstLoad 已补）+12 位置 +16 Login 记录次数 */
    static final long ICAVE = 0x171e2c0L, ICAVE_S = 0x171e2d8L, TICK_SETUP = 0x1082e84L;
    static final String ICODE = "28019fe5006080e5101090e5011081e2101080e5739be5eaf0412de90040a0e16091e5eb04719fe5040097e5010080e2040087e5005097e5000055e33a00000a440a94e5000050e33500000adc0b95e5000050e3320000aa580595e5000050e32f00000a0100a0e3080087e50400a0e10510a0e19d98e5eb0060b0e12800000a0c6087e5148096e5010088e2140086e5181096e5010058e1020000ba100086e20810a0e18559d1eb100096e5085180e70200a0e3080087e50400a0e10510a0e1c495e5ebdc0b95e5000050e3140000bae00b95e5010050e3110000ba0300a0e3080087e50500a0e16ca1e5eb0400a0e3080087e53c0a94e5000050e30800000a0014d0e5000051e30500000a401490e5000051e3020000ba951ee9eb0500a0e3080087e50000a0e3000087e5f081bde8";
    static final String IORIG = "58a0eaeb0c1095e55c2090e55c3091e5030052e1d6ffffca581091e5580080e2021191e7000051e1d1ffff1a0000a0e30c708de2f46298e50010a0e30c008de514008de50900a0e310008de50700a0e19a59d1eb90119fe52420a0e30c009de501108fe0c14509eb0600a0e10710a0e1ef1d37eb0c009de5000050e30000000aa6ae09eb040094e5000050e35000000a006094e50011a0e10020a0e30630a0e10270b3e75f0057e30300000a042082e2020051e1f8ffff1a450000ea061043e00020e0e3410152e14100000a017040e24121a0e1020057e10030a0e30270a0c1000051e3007000b3018087e2000057e30700a0e10800a01100308de508308de5010050e304008de51b0000ba0d00a0e10010a0e36959d1eb04009de5010050e3150000ba000056e300909d15000059131100000a";
    static final String TICK_O = "77feffeb", TICK_N = "136d1aeb", PR_N2 = "0080b0e15805961500005013e0641a0a";
    static ByteBuffer ib; static long ibAddr; static volatile boolean caveOn;
    static String caveInstall() {
        long b = Mods.base(); if (b == 0) return "找不到游戏库";
        if (ib == null) { ib = ByteBuffer.allocateDirect(32).order(ByteOrder.LITTLE_ENDIAN); ibAddr = GameOverlay.bufAddr(ib); }
        if (ibAddr <= 0 || ibAddr > 0xffffffffL) return "缓冲区地址不对";
        byte[] want = Patches.hex(ICODE + Server.w32((int) ibAddr));
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "rw")) {
            byte[] cur = new byte[want.length]; m.seek(b + ICAVE); m.readFully(cur);
            boolean ours = java.util.Arrays.equals(java.util.Arrays.copyOf(cur, want.length - 4), java.util.Arrays.copyOf(want, want.length - 4));
            if (!ours && !Patches.hexs(cur).equalsIgnoreCase(IORIG)) return "空位字节不符 " + Patches.hexs(java.util.Arrays.copyOf(cur, 8));
            m.seek(b + ICAVE); m.write(want);
            m.seek(b + ICAVE); m.readFully(cur);
            if (!java.util.Arrays.equals(cur, want)) return "写入没生效";
        } catch (Throwable e) { return "出错 " + e.getMessage(); }
        return "ok";
    }
    /** 回大厅时清掉缓冲区（不留上一局的 PC） */
    static void caveReset() { ByteBuffer x = ib; if (x != null) for (int o = 0; o < 32; o += 4) x.putInt(o, 0); }
    static String caveText() {
        ByteBuffer x = ib; if (!caveOn || x == null) return "同帧摆人：没装\n" + tlText();
        String[] st = { "没开始", "找位置", "已追加进位置", "已原生摆人", "Client Load Block 已跑", "FirstLoad 已补" };
        int s = x.getInt(8);
        return "同帧摆人：Login 记录 " + x.getInt(16) + " 次，建出生点后调用 " + x.getInt(4) + " 次，阶段 " + s + "（" + (s >= 0 && s < st.length ? st[s] : "?") + "）\n" + tlText();
    }
    /** v3.47 训练岛不刷枪：TileMode 流式管理器单机走客户端路径，服务器层（ServerOnly/ServerOnlyMajority/ServerOnlyGroundObjects）
     *  不进 CurrentLayers → FillLayersWithCacheData 不给它们建关卡 → 刷枪点/地上物资不存在（v3.45：地上物资 0，客户端层缓存里这三层各 5 个关卡）。
     *  钩子 BeginPlay 汇合点 0x115c744 → cave（game/re/tlcave.py，emu_tlcave.py 四种情况过）：只对 TileMode（vptr==0x4cada54）
     *  照专用服规则把服务器层列表 +0x2e8 里缺的层追加进 CurrentLayers。A 0x171e3f4..0x171e4af，B 0x171e568..0x171e5fb。
     *  字面量（Java 填绝对地址）：TileMode 虚表、静态层名 0x5173280、缓冲区、原指令的 r0=0x51734e0。 */
    static final long TL_A = 0x171e3f4L, TL_B = 0x171e568L, TL_HOOK = 0x115c744L;
    static final String TL_HOOK_O = "d4089fe5", TL_HOOK_N = "2a0717ea";
    static final String TA = "ff4f2de958409de5000094e594109fe5010050e12000001a90a09fe588909fe5e87294e5ec8294e508808ae50060e0e3080056e1180000aa010076e3b50f840286018710005090e504b090e5000055e36a00000a082095e50c3095e5010076e34300000a000099e5041099e5020050e1030051016100000a180099e51c1099e5020050e1030051013900001ac902d4e5000050e35900000a350000eaff4fbde80c009fe5abf8e8ea";
    static final String TAO = "0010a0e3000058e30600000a010196e7000050e30300000a010189e7011081e2080051e1f8ffff3a010058e10300009a010189e0011048e00111a0e1473ac6eb0000a0e3070189e70d00a0e1040050e10e00000a000094e5000050e30000000a61ae09eb00009de5000084e50000a0e300008de504109de5041084e508109de5081084e504008de508008de5030000ea00009de5000050e30000000a52ae09eb0500a0e10410a0e16315d1eb63ffffea74c0b7027420d302";
    static final String TB = "f4e294e5f8c294e50010a0e30c0051e10b0000aa81019ee7011081e2000050e3f9ffff0a050050e11700000a0ca090e5080090e5020050e103005a011200000af1ffffea01008ce2f80284e5fc0294e500005ce10ca0a0e1020000babd0f84e20a10a0e1eb9bd2ebf41294e58a1181e0005081e504b081e500005be30200000a04009be5010080e204008be5016086e289ffffea";
    static final String TBO = "efffff0abe52ddeb0c1094e55c2090e55c3091e5030052e1e9ffffca581091e5580080e2021191e7000051e1e4ffff1abf0debeb0c1094e55c2090e55c3091e5030052e1deffffca581091e5580080e2021191e7000051e1d9ffff1a347298e52c804be20060a0e30d00a0e32c600be50010a0e324600be528000be50800a0e1e458d1eb24129fe53420a0e32c001be501108fe0";
    /** v3.47 小地图不对：训练岛 9 个位置是同一个岛复制成 3x3（块间距 1400 米），地图贴图只画中间那个原岛（块 4，偏移 0）。
     *  FindSlot 0x10845b0 挑第一个空位置 → 单机总进块 0（-1400 米,-1400 米 偏移）→ 小地图/大地图看到的是原岛西南的海（推断）。
     *  改空位置搜索起点：0x108464c mov r1,#0 / add r2,r0,#0x14 → mov r1,#4 / add r2,r0,#0xe8（从位置 4 开始找；训练场默认 9 个位置）。 */
    static final long PS4 = 0x108464cL; static final String PS4_O = "0010a0e3142080e2", PS4_N = "0410a0e3e82080e2";
    /** v3.51 用户：服务端开训练岛，加入的人被分到别的训练场副本（一局 9 个副本，FindSlot 找第一个空位置）→ 上不了岸。
     *  空位置循环第一句 0x1084654 ldr r3,[r2] → b 0x10846c0（返回 r0+r1*0x34，r1=4）：所有人都进位置 4（中间原岛），同一个训练场。 */
    static final long SAME4 = 0x1084654L; static final String SAME4_O = "003092e5", SAME4_N = "190000ea";
    static ByteBuffer tb; static long tbAddr; static volatile String tlState = "服务器层补丁：没装";
    static String tlWrite(RandomAccessFile m, long at, byte[] want, String orig, int cmpLen) throws Exception {
        byte[] cur = new byte[want.length]; m.seek(at); m.readFully(cur);
        boolean ours = java.util.Arrays.equals(java.util.Arrays.copyOf(cur, cmpLen), java.util.Arrays.copyOf(want, cmpLen));
        if (!ours && !Patches.hexs(cur).equalsIgnoreCase(orig)) return "空位字节不符 " + Long.toHexString(at - Mods.base()) + " " + Patches.hexs(java.util.Arrays.copyOf(cur, 8));
        return null;
    }
    static String tlInstall() {
        long b = Mods.base(); if (b == 0) return "找不到游戏库";
        if (tb == null) { tb = ByteBuffer.allocateDirect(16).order(ByteOrder.LITTLE_ENDIAN); tbAddr = GameOverlay.bufAddr(tb); }
        if (tbAddr <= 0 || tbAddr > 0xffffffffL) return "缓冲区地址不对";
        byte[] a = Patches.hex(TA + Server.w32((int) (b + 0x4cada54L)) + Server.w32((int) (b + 0x5173280L)) + Server.w32((int) tbAddr) + Server.w32((int) (b + 0x51734e0L)));
        byte[] bb = Patches.hex(TB);
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "rw")) {
            String e = tlWrite(m, b + TL_A, a, TAO, TA.length() / 2); if (e != null) return e;
            e = tlWrite(m, b + TL_B, bb, TBO, bb.length); if (e != null) return e;
            m.seek(b + TL_A); m.write(a); m.seek(b + TL_B); m.write(bb);
            byte[] c1 = new byte[a.length], c2 = new byte[bb.length];
            m.seek(b + TL_A); m.readFully(c1); m.seek(b + TL_B); m.readFully(c2);
            if (!java.util.Arrays.equals(c1, a) || !java.util.Arrays.equals(c2, bb)) return "写入没生效";
        } catch (Throwable t) { return "出错 " + t.getMessage(); }
        return Mods.write(TL_HOOK, TL_HOOK_O, TL_HOOK_N, true);
    }
    static String tlText() {
        ByteBuffer x = tb; if (x == null) return tlState;
        return tlState + "；TileMode BeginPlay " + (x.getInt(8) > 0 ? "跑过" : "没跑") + "，服务器层列表 " + x.getInt(8) + " 个（另加 ServerOnly 本层；加了几层看流式状态的当前层）";
    }
    /** v3.59 用户：单机训练场移动靶不动。ANZSceneItem_RangeTarget::Tick 0xcf544c 开头 ldrb r0,[r4,#0xe8](Role); cmp r0,#2; pophi
     *  → Role=Authority(3) 直接返回，移动（状态 2 → 0xcf5654 → 0xcf66c8 按 MovingHorizontalVelocity +0x334 来回走）只在客户端本地算。
     *  单机/开房的人都是 Authority → 靶子永远不动。把 0xcf5464 pophi 改 nop：Authority 也跑本地移动（蓝图里 Mobile/Moveable 靶的速度、范围不改）。 */
    static final long RT_TICK = 0xcf5464L; static final String RT_O = "108cbd88", RT_N = "00f020e3";
    static volatile String rtState = "移动靶补丁：没装";
    static void install() {
        rtState = "移动靶补丁（Authority 也走本地移动）：" + Mods.write(RT_TICK, RT_O, RT_N, true); LogCap.note(rtState);
        caveReset();
        String c = caveInstall(), t = "没装", r;
        if ("ok".equals(c)) {
            r = Mods.write(PR, PR_O, PR_N2, true);
            if ("ok".equals(r)) { t = Mods.write(TICK_SETUP, TICK_O, TICK_N, true); caveOn = "ok".equals(t); }
            if (!caveOn) { Mods.write(PR, PR_O, PR_N2, false); r = Mods.write(PR, PR_O, PR_N, true); }
        } else r = Mods.write(PR, PR_O, PR_N, true);
        String r2 = Mods.write(PB, PB_O, PB_N, true);
        tlState = "服务器层补丁（训练岛刷枪点）：" + tlInstall() + "；先进中间位置 4（小地图）：" + Mods.write(PS4, PS4_O, PS4_N, true) + "；所有人同一个位置 4：" + Mods.write(SAME4, SAME4_O, SAME4_N, true);
        // v3.53 物资点组件 UNZBRPickupConfigComponent::BeginPlay 0x10c0b5c 只认 NetMode 0/1，服务端（listen，NetMode 2）训练岛的物资点不交给 PickupManager → 放开到「只排除客户端 3」（Loot 的 PK 那三条）
        String pk = Mods.write(Loot.PK, Loot.PK_O, Loot.PK_N, true);
        fix = "训练场空队伍补丁：" + r + "；块数补丁 0x10828b0：" + r2 + "；同帧摆人 cave " + c + "，Tick 入口 " + t + "；物资点放开（服务端也交给 PickupManager）：" + pk
            + "；ServerOnly 层：v3.56 不再改虚表（v3.55 无效）；" + rtState; LogCap.note(fix);
    }
    /** v3.55 ServerOnly 层类（vt 0x4caee2c：ServerOnly、/Door、/Glass、/Pickups、/GrassMap）的 vt+0x18「按视野更新」是空函数 bx lr 0xb63a30，
     *  客户端策略 Update 0x11671bc 轮到它什么也不加载 → ServerOnly/Pickups 关卡一直「已载 0」→ 物资点组件不存在、[Pickup] SPAWN Total=0。
     *  换成普通层的 0x11555f0（转给集合 vt+0x14 0x1175304，ServerOnly 集合 0x4cb1bf0 同一个函数）→ 跟普通层一样按人物视野加载。
     *  物资点组件后来才 BeginPlay 也行：PickupManager 0xff1e88 见 +0x30c 已开刷 → 「Direct spawn」直接刷（游戏自己的刷点和物品表）。
     *  大地图客户端路径不把 ServerOnly 层放进当前层，这个改动只在训练岛（tlcave 加进来的）起作用。 */
    static volatile String soState = "没试";
    static final long SO_VT18 = 0x4caee2cL + 0x18, SO_NOP = 0xb63a30L, SO_UPD = 0x11555f0L;
    static String soLoad() {
        long b = Mods.base(); if (b == 0) return "找不到游戏库";
        long a = b + SO_VT18, cur = rdq(a) & 0xffffffffL;
        if (cur == ((b + SO_UPD) & 0xffffffffL)) return "ok";
        if (cur != ((b + SO_NOP) & 0xffffffffL)) return "原值不符 " + Long.toHexString(cur - b);
        String w = Mods.pokeInt(a, (int) (b + SO_UPD));
        return (rdq(a) & 0xffffffffL) == ((b + SO_UPD) & 0xffffffffL) ? "ok" : "写入没生效 " + w;
    }
    /** v3.56：v3.55 改虚表后 Pickups 还是「已载 0」（集合 0x1175304 拿不到距离表就直接返回）。
     *  改走流对象本身：ULevelStreaming +0xa0 半字 bit5 = bShouldBeLoaded、bit6 = bShouldBeVisible（NZCustomLevelStreamingHelper 0x11529dc 读写的就是这两位）。
     *  对 ServerOnly/Pickups 层的每个关卡项（[0] 流对象弱指针 → GUObjectArray）置这两位，关卡和里面的物资点组件是游戏自己的，
     *  组件 BeginPlay 后 PickupManager 见已开刷 → 「Direct spawn」按游戏自己的物品表刷。只置位，不摆东西。返回「置位前→后，已载」。 */
    /** v3.59：v3.56 直接置 +0xa0 位没用（那几位只是「当前请求状态」，实际加载靠 RequestsManager；位已相等时 0x11529dc 反而不再发请求）。
     *  改成在游戏线程调游戏自己的「整组加载」0x1174180(集合, LOD 上限, 可见)：它对集合里每个关卡调 0x11742c4(集合, i, LOD, 加载=1, 可见, 不阻塞)
     *  → 0x11529dc → RequestsManager 发加载请求（和别的层按视野加载走同一条路）。只读找集合，调用只在游戏线程。未实测。 */
    static final long LOAD_ALL = 0x1174180L;
    static final java.util.HashSet<Long> soCalled = new java.util.HashSet<>();
    static String soForce(long b, Veh.Names nm) throws Exception {
        double[] q = Veh.info(); long gm = q == null ? 0 : (long) q[4];
        if (gm < 0x10000) return "没有 GameMode";
        StringBuilder sb = new StringBuilder(); int called = 0, loaded = 0, tot = 0, colls = 0;
        java.util.ArrayList<Long> todo = new java.util.ArrayList<>();
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
            long g = Server.rd(m, b + 0x51250acL), objs = Server.rd(m, g + 0x10); int num = (int) Server.rd(m, g + 0x18);
            long mgr = Server.rd(m, gm + 0xa3c);
            if (mgr < 0x10000 || !Mods.isObj(m, b, mgr) || objs < 0x10000) return "没有管理器";
            long ld = Server.rd(m, mgr + 0x2f4); int ln = (int) Server.rd(m, mgr + 0x2f8);
            for (int i = 0; i < ln && i < 40 && ld >= 0x10000; i++) {
                long L = Server.rd(m, ld + 8L * i); if (L < 0x10000) continue;
                if (Server.rd(m, L) - b != 0x4caee2cL) continue;   // 只看 ServerOnly 类层
                long coll = Server.rd(m, L + 0x1c); if (coll < 0x10000) continue;
                String lname = nm == null ? "?" : Box.nameOf(m, nm, Server.rd(m, L + 8));
                if (!lname.contains("Pickups")) continue;
                colls++;
                long ed = Server.rd(m, coll + 0x14); int en = (int) Server.rd(m, coll + 0x18);
                for (int j = 0; j < en && j < 64 && ed >= 0x10000; j++) {
                    long E = Server.rd(m, ed + 8L * j); if (E < 0x10000) continue;
                    int idx = (int) Server.rd(m, E); if (idx < 0 || idx >= num) continue;
                    long S = Server.rd(m, objs + 16L * idx); if (S < 0x10000) continue;
                    tot++;
                    if (Server.rd(m, S + 0x110) >= 0x10000) loaded++;
                    if (sb.length() < 200) sb.append(" [").append(Integer.toHexString((int) Server.rd(m, S + 0xa0) & 0xffff)).append("]");
                }
                synchronized (soCalled) { if (soCalled.add(coll)) todo.add(coll); }
            }
        }
        for (long coll : todo) {
            long[] r = Server.gcall(b + LOAD_ALL, coll, 1, 1, 0, 2000);
            if (r[0] == 1) { called++; sb.append(" 整组加载→").append(r[1]); }
            else { synchronized (soCalled) { soCalled.remove(coll); } sb.append(" 游戏线程没跑到"); }
        }
        return "Pickups 集合 " + colls + " 个，关卡 " + tot + " 个，这次调整组加载 " + called + "，已载 " + loaded + sb;
    }
    static volatile String block = "分配位置：没试";
    static volatile String state = "训练岛：没进";
    static volatile String range = "射击训练区：没查";
    static volatile String spawn = "原生刷车/刷物资：没查";
    static volatile String setup = "出生点建立：没查";
    static volatile String track = "坐标/地形块随时间：-";
    static volatile String stream = "流式状态：没查";
    static volatile String seenLog = "";
    static volatile boolean running;
    static final StringBuilder seen = new StringBuilder();

    /** Con 每行游戏日志都给这里（只在训练岛）：记关键行 */
    static void seen(String l) {
        String[] keys = { "OnAllDuplicateLevelLoaded", "OnLevelStreamingLayerLoadCompleted", "AddPlayerToSlot", "Spawn Vehicle", "SpawnVehicle", "Load Block", "Client Load Block", "SetupPlayerStartActor Add Player Start 0 ", "LevelMgr is null", "level not prepared",
            "FillLayersWithCacheData", "TileLayer", "Already Load Block", "Out Of Index", "Streaming in level", "DirectSubmit", "NZStreamingRequestsManager", "Not Need", "FirstLoad", "StreamingLevelsManagerActor", "[Pickup]", "PickUpRefresh", "CachedPickupSPList", "CachedSpawnedPickupList", "GridMap" };
        for (String k : keys) if (l.contains(k)) {
            synchronized (seen) { if (seen.length() < 6000) seen.append(l.trim().length() > 200 ? l.trim().substring(0, 200) : l.trim()).append("\n  "); seenLog = seen.toString(); }
            return;
        }
    }

    static void clearSeen() { synchronized (seen) { seen.setLength(0); seenLog = ""; } }

    static void matchLoaded() {
        if (running) return; running = true;
        soState = "进岛 15 秒后直接让游戏加载 Pickups 关卡";
        block = "分配位置：没试"; range = "射击训练区：没查"; guns = "训练桌放枪：没跑"; logic = "常驻层 AlwaysLoad：没跑"; pickup = "物资链：没查"; spawn = "原生刷车/刷物资：没查"; setup = "出生点建立：没查"; track = "坐标/地形块随时间：-"; stream = "流式状态：没查"; state = "训练岛：进了，开始等 GameMode";
        { android.app.Activity x = Con.act; if (x != null) AvatarLog.autoTrain(x); }
        new Thread(() -> {
            try { run(); } catch (Throwable t) { state = "训练岛：出错 " + t; LogCap.note(state); GameOverlay.toastAny(state); }
            finally { running = false; }
        }, "zf-island").start();
    }

    static String p3(double[] p) { return p == null ? "?" : (int) p[0] + "," + (int) p[1] + "," + (int) p[2]; }
    static double[] me() throws InterruptedException { double[] q = Veh.info(); return q == null ? null : new double[] { q[0], q[1], q[2] }; }

    static void run() throws Exception {
        long t0 = System.currentTimeMillis();
        long b = 0, gm = 0, pc = 0; String gmName = "?";
        Veh.Names nm = null;
        // 1) 等 GameMode BeginPlay 建好位置
        while (true) {
            if (System.currentTimeMillis() - t0 > 90000) { state = "训练岛：90 秒没等到 GameMode 建位置（GM " + Long.toHexString(gm) + " " + gmName + "）"; note(state); outfit(); return; }
            Thread.sleep(500);
            if (!Con.training || !Con.inMatch) return;
            b = Mods.base(); if (b == 0) continue;
            double[] q = Veh.info(); if (q == null) { state = "训练岛：等人物"; continue; }
            gm = (long) q[4]; pc = Veh.lastPc(); nm = Veh.Names.get();
            if (gm < 0x10000 || pc < 0x10000) { state = "训练岛：等 GameMode/控制器"; continue; }
            try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
                gmName = Alive.clsName(m, nm, gm);
                if (!gmName.contains("PracticeGameMode")) { state = "训练岛：模式是 " + gmName + "，不是训练场，不分配位置"; note(state); outfit(); return; }
                int n = (int) Server.rd(m, gm + 0xa44);
                if (n <= 0) { state = "训练岛：等 BeginPlay 建位置（现在 0 个）"; continue; }
            }
            break;
        }
        state = "训练岛：GameMode 位置已建好，找地块管理器";
        // 2) 地块管理器
        long mgr = 0;
        for (int i = 0; i < 20 && mgr == 0; i++) {
            try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) { long a = Server.rd(m, gm + 0xa3c); if (a >= 0x10000 && Mods.isObj(m, b, a)) mgr = a; }
            if (mgr == 0) mgr = scan(b, nm).mgr;
            if (mgr == 0) { state = "训练岛：没找到地块管理器（NZStreamingLevelsManagerActor_TileMode），再找"; Thread.sleep(1000); }
        }
        if (mgr == 0) { state = "训练岛：20 秒没找到地块管理器"; note(state); outfit(); return; }
        // 3) 出生点按正确块数建好（块数补丁没赶上就让 Tick 重建）
        state = "训练岛：管理器 " + Long.toHexString(mgr) + "，等出生点建好";
        if (!ensureSetup(b, gm, mgr)) { outfit(); return; }
        state = "训练岛：出生点好了，原生摆人 + Client Load Block";
        // 4) 补进位置 → 原生 AddPlayerToSlot 摆人 → 原生 Client Load Block（LoadBlock + FirstLoad）
        double[] ps = join(b, gm, pc, mgr);
        if (!GameOverlay.remote()) { try { logic = alwaysLoad(b, nm, mgr, pc); } catch (Throwable e) { logic = "常驻层 AlwaysLoad：出错 " + e; } note(logic); }
        // v3.50 用户：v3.49 枪要等 2 分多钟（跟踪 120 秒之后才摆）→ 进岛 15 秒就摆。
        //   v3.49 诊断：ServerOnly 本层进来后展开出 ServerOnly/Pickups 等子层（关卡 1，包住人物），但地上还是没枪 → 先看原生 150 米内有没有物资，不够 5 个才自己摆
        //   （服务端开房时游戏自己有权威，原生刷出来就不重复摆）。
        if (!GameOverlay.remote()) { final long fb = b; final Veh.Names fnm = nm; new Thread(() -> {
            try {
                guns = "训练桌（只看不摆）：线程开始，尽早加载刷枪点关卡";
                synchronized (soCalled) { soCalled.clear(); }
                // v3.59：进岛后每 0.5 秒找 Pickups 层，一出现就让游戏整组加载（越早越好，赶在 PickupManager 刷物资之前）
                for (int k = 0; k < 30 && Con.training && Con.inMatch; k++) {
                    try { soState = soForce(fb, fnm); } catch (Throwable e) { soState = "出错 " + e; }
                    if (soState.contains("整组加载→")) { note("Pickups 关卡（早）：" + soState); break; }
                    Thread.sleep(500);
                }
                Thread.sleep(15000);
                if (!Con.training || !Con.inMatch) { guns = "训练桌放枪：15 秒后不在训练岛了（training " + Con.training + " inMatch " + Con.inMatch + "）"; return; }
                try { soState = soForce(fb, fnm); } catch (Throwable e) { soState = "出错 " + e; } note("Pickups 关卡：" + soState);
                Thread.sleep(6000);
                try { soState += "；6 秒后 " + soForce(fb, fnm); } catch (Throwable e) { soState += "；出错 " + e; }
                try { pickup = pickDiag(fb, fnm); } catch (Throwable e) { pickup = "物资链：出错 " + e; } note(pickup);
                guns = "训练桌（只看不摆）：扫物资";
                double[] me = me(); Scan sc = scan(fb, fnm); int near = 0;
                if (me != null) for (float[] q : sc.pick) if (d2(q, me) < 15000.0 * 15000.0) near++;
                // v3.54 用户：枪必须游戏自己刷，不许我们摆 → 只统计原生物资，不再调 tableGuns()
                guns = "训练桌（只看不摆）：原生 150 米内物资 " + near + " 个"; note(guns);
            } catch (Throwable e) { guns = "训练桌放枪：出错 " + e; note(guns); }
        }, "zf-tablegun").start(); }
        state = "训练岛：已摆人和 Client Load Block，跟踪地形加载";
        // 5) 衣服（训练岛不走 Bots.matchLoaded，以前从来没发过）
        outfit();
        // 6) 跟踪：坐标、地形块、+a70/+a21，最多 120 秒；人物等地形到了才放开
        follow(b, nm, gm, pc, ps, mgr);
        // 7) 射击训练区（只诊断）
        rangeDiag(b, nm, mgr, pc);
        // 7b) v3.49 训练桌上的枪：这版客户端 pak 里训练岛根本没有 ServerOnly 关卡文件（pak 文件表 TrainingIsland_Runtime 只有
        //     AlwaysLoad/Grass/Heightmap/OcHmp/OcPolygons/Runtime/Tree，全包没有任何 ServerOnly 文件），v3.47/48 把层加进去也是空的 → 枪桌原来的刷枪点不存在。
        //     改成自己摆：射线找桌面，每张桌子放枪 + 子弹；找不到桌子就在人物前方地上摆一排。
        // 8) 原生刷车/刷物资情况
        Thread.sleep(20000);
        if (!Con.training || !Con.inMatch) return;
        spawnDiag(b, nm, gm);
        android.app.Activity x = Con.act;
        stream = streamDiag(b, nm, mgr, true); note(stream);
        if (x != null) { AvatarLog.manual = true; try { AvatarLog.export(x, "slot-"); } finally { AvatarLog.manual = false; } }
    }

    static void outfit() { Bots.trainingOutfit(); }

    /** v3.52 训练岛刷枪/靶子：刷物资区 BP_PickUpRefreshVolumn、五轮靶子、射击/驾驶训练台都在 AlwaysLoad/LogicLevel.umap。
     *  v3.51 诊断：客户端层缓存 +370 里有 AlwaysLoad×6（Light/LogicLevel/Ocean/ProceduralFoliageVolumes/block/cars），
     *  可当前层 18 个里没有 AlwaysLoad —— 显示层列表 +0x2dc 和服务器层列表 +0x2e8 都没建它 → LogicLevel 从来没加载（刷物资区 0、全图靶子 0）。
     *  做法（只服务端/单机）：层工厂 0x1156e20(&FName AlwaysLoad 0x5173208) → 工厂 vt+0x10(out, 工厂) 建层（同构造里建 +0x2cc/+0x2d4 的写法）；
     *  CurrentLayers(+0x2f4) 临时换成只有这一层 → FillLayersWithCacheData 0x11649bc(管理器, +0x370, 块号) 只给它建关卡流对象（别的层不重复 Tile）→
     *  换回原数组，再把它追加到 CurrentLayers 尾部，之后 Tick 的 Update 正常流式加载。 */
    static final long LAYER_FACTORY = 0x1156e20L, FILL_LAYERS = 0x11649bcL, NAME_ALWAYSLOAD = 0x5173208L;
    static volatile String logic = "常驻层 AlwaysLoad：没跑";
    static String alwaysLoad(long b, Veh.Names nm, long mgr, long pc) throws Exception {
        StringBuilder sb = new StringBuilder("常驻层 AlwaysLoad（LogicLevel 刷枪/靶子）：");
        long cur; int num, max, blk;
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
            cur = Server.rd(m, mgr + 0x2f4); num = (int) Server.rd(m, mgr + 0x2f8); max = (int) Server.rd(m, mgr + 0x2fc); blk = (int) Server.rd(m, pc + 0xbdc);
            if (cur < 0x10000 || num <= 0 || num > 64) return sb.append("当前层数组不对 ").append(num).toString();
            for (int i = 0; i < num; i++) {
                long L = Server.rd(m, cur + 8L * i); if (L < 0x10000) continue;
                if ("AlwaysLoad".equals(Box.nameOf(m, nm, Server.rd(m, L + 8)))) return sb.append("当前层里已有，不加").toString();
            }
        }
        if (blk < 0) return sb.append("PC 没块号，不加").toString();
        long[] r = Server.gcall(b + LAYER_FACTORY, b + NAME_ALWAYSLOAD, 0, 0, 0, 3000);
        long fac = r[1];
        if (r[0] == 0 || fac < 0x10000) return sb.append("层工厂没拿到（").append(r[0] == 0 ? "游戏线程没接" : Long.toHexString(fac)).append("）").toString();
        long[] mem = Server.gcall(b + REALLOC, 0, 16, 0, 0, 3000);
        if (mem[0] == 0 || mem[1] < 0x10000) return sb.append("分配内存失败").toString();
        long out = mem[1], one = mem[1] + 8;
        Mods.pokeInt(out, 0); Mods.pokeInt(out + 4, 0);
        long vt10;
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) { vt10 = Server.rd(m, Server.rd(m, fac) + 0x10); }
        if (vt10 < b || vt10 > b + 0x4000000L) return sb.append("工厂虚表不对 ").append(Long.toHexString(vt10)).toString();
        r = Server.gcall(vt10, out, fac, 0, 0, 3000);
        long layer, ref;
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) { layer = Server.rd(m, out); ref = Server.rd(m, out + 4); }
        if (r[0] == 0 || layer < 0x10000) return sb.append("建层失败（").append(r[0] == 0 ? "游戏线程没接" : "空").append("）").toString();
        String lname; try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) { lname = Box.nameOf(m, nm, Server.rd(m, layer + 8)); }
        sb.append("建层 ").append(lname).append(" vt+").append(Long.toHexString(rdq(layer) - b)).append("；");
        // 临时只留这一层 → Fill
        Mods.pokeInt(one, (int) layer); Mods.pokeInt(one + 4, (int) ref);
        Mods.pokeInt(mgr + 0x2f4, (int) one); Mods.pokeInt(mgr + 0x2fc, 1); Mods.pokeInt(mgr + 0x2f8, 1);
        long[] f = Server.gcall(b + FILL_LAYERS, mgr, mgr + 0x370, blk, 0, 3000);
        Mods.pokeInt(mgr + 0x2f8, num); Mods.pokeInt(mgr + 0x2f4, (int) cur); Mods.pokeInt(mgr + 0x2fc, max);
        sb.append("Fill(块 ").append(blk).append(") ").append(f[0] != 0 ? "已跑" : "没跑").append("；");
        // 追加到 CurrentLayers（8 字节元素：层指针 + 引用计数块）
        long data = cur; int nmax = max;
        if (num >= max) {
            nmax = Math.max(4, num * 2);
            long[] a = Server.gcall(b + REALLOC, cur, nmax * 8, 0, 0, 3000);
            if (a[0] == 0 || a[1] < 0x10000) return sb.append("追加时分配失败").toString();
            data = a[1];
        }
        Mods.pokeInt(data + 8L * num, (int) layer); Mods.pokeInt(data + 8L * num + 4, (int) ref);
        Mods.pokeInt(mgr + 0x2f4, (int) data); Mods.pokeInt(mgr + 0x2fc, nmax); Mods.pokeInt(mgr + 0x2f8, num + 1);
        int lv = -1;
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
            long coll = Server.rd(m, layer + 0x1c); if (coll >= 0x10000) lv = (int) Server.rd(m, coll + 0x18);
        }
        return sb.append("已加进当前层（第 ").append(num).append(" 层），关卡 ").append(lv).append(" 个").toString();
    }
    static long rdq(long a) { try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) { return Server.rd(m, a); } catch (Throwable t) { return 0; } }

    static volatile String guns = "训练桌放枪：没跑";
    /** 人物周围 36×36 米、1.2 米一格，从人物中心上方 1 米往下打射线（低于棚顶）。
     *  一格比周围 3 米内最低点高 55~115 厘米 = 桌面。桌面格按 1.5 米连成桌子，每张桌子一把枪（按枪表轮流）+ 子弹 3 份。 */
    static void tableGuns() throws Exception {
        double[] me = me(); if (me == null) { guns = "训练桌放枪：拿不到人物位置"; note(guns); return; }
        final float step = 120f; final int R = 15;
        int n = 2 * R + 1;
        float[][] z = new float[n][n]; String[][] who = new String[n][n];
        int ok = 0;
        for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) {
            if (!Con.training || !Con.inMatch) return;
            float x = (float) (me[0] + (i - R) * step), y = (float) (me[1] + (j - R) * step);
            Trace.Hit h = Trace.down(x, y, (float) me[2] + 100f, (float) me[2] - 1500f);
            z[i][j] = h == null || Float.isNaN(h.z) ? Float.NaN : h.z;
            who[i][j] = h == null ? "" : h.actor + "/" + h.cls;
            if (!Float.isNaN(z[i][j])) ok++;
        }
        ArrayList<float[]> top = new ArrayList<>(); java.util.TreeMap<String, Integer> names = new java.util.TreeMap<>();
        for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) {
            if (Float.isNaN(z[i][j])) continue;
            float lo = z[i][j];
            for (int a = Math.max(0, i - 2); a <= Math.min(n - 1, i + 2); a++) for (int c = Math.max(0, j - 2); c <= Math.min(n - 1, j + 2); c++) if (!Float.isNaN(z[a][c])) lo = Math.min(lo, z[a][c]);
            float d = z[i][j] - lo;
            if (d >= 55 && d <= 115) { top.add(new float[] { (float) (me[0] + (i - R) * step), (float) (me[1] + (j - R) * step), z[i][j] }); names.merge(who[i][j], 1, Integer::sum); }
        }
        // 连成桌子
        int[] g = new int[top.size()]; for (int i = 0; i < g.length; i++) g[i] = i;
        for (int i = 0; i < g.length; i++) for (int j = i + 1; j < g.length; j++)
            if (d2(top.get(i), top.get(j)) < 150.0 * 150.0 && Math.abs(top.get(i)[2] - top.get(j)[2]) < 30) g[root(g, i)] = root(g, j);
        java.util.LinkedHashMap<Integer, ArrayList<float[]>> tb = new java.util.LinkedHashMap<>();
        for (int i = 0; i < g.length; i++) tb.computeIfAbsent(root(g, i), k -> new ArrayList<>()).add(top.get(i));
        ArrayList<float[]> spots = new ArrayList<>();
        for (ArrayList<float[]> t : tb.values()) {
            float sx = 0, sy = 0, sz = -1e9f; for (float[] p : t) { sx += p[0]; sy += p[1]; sz = Math.max(sz, p[2]); }
            spots.add(new float[] { sx / t.size(), sy / t.size(), sz + 10f });
        }
        spots.sort((p, q) -> Double.compare(Math.hypot(p[0] - me[0], p[1] - me[1]), Math.hypot(q[0] - me[0], q[1] - me[1])));
        boolean onTable = spots.size() >= 3;
        if (!onTable) {   // 找不到桌子：人物前方 4 米起地上一排，间隔 1.5 米
            spots.clear();
            for (int k = 0; k < Bots.GUN.length; k++) {
                float x = (float) (me[0] + 400 + (k / 8) * 150), y = (float) (me[1] + ((k % 8) - 3.5) * 150);
                Trace.Hit h = Trace.down(x, y, (float) me[2] + 300f, (float) me[2] - 1500f);
                float gz = h == null || Float.isNaN(h.z) ? (float) me[2] - 80f : h.z + 10f;
                spots.add(new float[] { x, y, gz });
            }
        }
        int put = 0, fail = 0; String err = null;
        int cnt = Math.min(spots.size(), onTable ? Math.max(Bots.GUN.length, Math.min(spots.size(), 40)) : Bots.GUN.length);
        for (int k = 0; k < cnt; k++) {
            if (!Con.training || !Con.inMatch) return;
            float[] p = spots.get(k); String[] gun = Bots.GUN[k % Bots.GUN.length];
            String e = Veh.dropAt(Long.parseLong(gun[0]), p[0], p[1], p[2]);
            if (e == null) { put++; for (int a = 0; a < 3; a++) Veh.dropAt(Long.parseLong(gun[1]), p[0] + 30f, p[1] + (a - 1) * 25f, p[2]); }
            else { fail++; err = e; }
            Thread.sleep(40);
        }
        StringBuilder nb = new StringBuilder(); int c = 0;
        for (java.util.Map.Entry<String, Integer> e : names.entrySet()) { if (c++ >= 6) break; nb.append(e.getKey()).append("×").append(e.getValue()).append(' '); }
        guns = "训练桌放枪：射线 " + ok + "/" + (n * n) + "，桌面格 " + top.size() + "，桌子 " + tb.size() + "（" + (onTable ? "放桌上" : "不够 3 张，放人物前方地上") + "）；放了枪 " + put + " 把，失败 " + fail + (err == null ? "" : "（" + err + "）") + "；桌面命中：" + nb;
        note(guns);
        if (put > 0) GameOverlay.toastAny("训练枪已摆好 " + put + " 把");
    }

    static void note(String s) { LogCap.note(s); }
    /** 读一个字节（Server.rd 只读 4 字节对齐） */
    static int rb(RandomAccessFile m, long a) { return (int) ((Server.rd(m, a & ~3L) >>> ((a & 3) * 8)) & 0xff); }

    /** 位置 i 的第一个 PlayerStart 的坐标（+4 是 PlayerStart 数组）；没有 → null */
    static double[] slotStart(RandomAccessFile m, long slot) throws java.io.IOException {
        long d = Server.rd(m, slot + 4); int k = (int) Server.rd(m, slot + 8);
        if (d < 0x10000 || k <= 0 || k > 64) return null;
        long a = Server.rd(m, d); if (a < 0x10000) return null;
        float[] p = pos(m, a); if (p == null) return null;
        return new double[] { p[0], p[1], p[2], a };
    }
    /** 块偏移，和 0x1163e2c / LoadBlock 一样：边长 = floor(sqrt(N)+0.01)，行=块/边长，列=块%边长，
     *  X = (行数-1)*-0.5*W + 行*W（W=管理器+0x41c），Y = (边长-1)*-0.5*H + 列*H（H=+0x420） */
    static double[] offset(int n, int blk, int w, int h) {
        if (n <= 0) return new double[] { 0, 0 };
        int side = (int) Math.floor(Math.sqrt(n) + 0.01); if (side < 1) side = 1;
        int rows = n / side, row = blk / side, col = blk % side;
        return new double[] { (rows - 1) * -0.5 * w + (double) row * w, (side - 1) * -0.5 * h + (double) col * h };
    }

    /** 等 SetupPlayerStartActor 按 GM 块数建好每个位置的 PlayerStart。管理器块数不对（补丁没赶上）→ 改块数、清 PlayerStart 数、GM+0xa3c=0 让 Tick 重建。 */
    static boolean ensureSetup(long b, long gm, long mgr) throws Exception {
        StringBuilder sb = new StringBuilder("出生点建立：");
        boolean redone = false; long w0 = System.currentTimeMillis();
        while (System.currentTimeMillis() - w0 < 30000) {
            if (!Con.training || !Con.inMatch) return false;
            int n, cnt, mcnt, num0, w, h; long slots, a3c; double[] p0;
            try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
                slots = Server.rd(m, gm + 0xa40); n = (int) Server.rd(m, gm + 0xa44); cnt = (int) Server.rd(m, gm + 0xa4c);
                mcnt = (int) Server.rd(m, mgr + 0x43c); a3c = Server.rd(m, gm + 0xa3c); w = (int) Server.rd(m, mgr + 0x41c); h = (int) Server.rd(m, mgr + 0x420);
                num0 = (int) Server.rd(m, slots + 8); p0 = slotStart(m, slots);
            }
            if (num0 <= 0 || p0 == null) { setup = sb + "等 Tick 建 PlayerStart（GM+a3c=" + Long.toHexString(a3c) + "，管理器块数 " + mcnt + "，GM 块数 " + cnt + "）"; Thread.sleep(300); continue; }
            double[] off = offset(cnt, 0, w, h);
            double ex = TPL_X + off[0], ey = TPL_Y + off[1];
            boolean good = mcnt == cnt && Math.abs(p0[0] - ex) < 100 && Math.abs(p0[1] - ey) < 100;
            sb.append("位置 ").append(n).append(" 个，GM 块数 ").append(cnt).append("，管理器块数 +43c=").append(mcnt).append("，块宽 ").append(w).append('×').append(h)
              .append("；位置 0 的 PlayerStart ").append(p3(p0)).append("（按块数 ").append(cnt).append(" 应在 ").append((int) ex).append(',').append((int) ey).append("）");
            if (good) { setup = sb.append(redone ? " → 重建后对了" : " → 对（块数补丁生效）").toString(); note(setup); return true; }
            if (redone) { setup = sb.append(" → 重建后还不对，照原生走").toString(); note(setup); GameOverlay.toastAny("训练岛：出生点和地块对不上，看诊断"); return true; }
            // 补丁没赶上：照专用服写块数，清 PlayerStart 数，GM+0xa3c=0 → 下一帧 Tick 重新拿管理器、重跑 SetupPlayerStartActor
            sb.append(" → 不对，重建：");
            sb.append("写块数 ").append(Mods.pokeInt(mgr + 0x43c, cnt));
            try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) { slots = Server.rd(m, gm + 0xa40); n = (int) Server.rd(m, gm + 0xa44); }
            for (int i = 0; i < n && i < 64; i++) Mods.pokeInt(slots + 0x34L * i + 8, 0);
            sb.append("，清 ").append(n).append(" 个位置的 PlayerStart 数，GM+a3c=0 ").append(Mods.pokeInt(gm + 0xa3c, 0)).append("；");
            redone = true; Thread.sleep(500);
        }
        setup = sb.append("30 秒没等到 PlayerStart 建好").toString(); note(setup); GameOverlay.toastAny("训练岛：出生点没建好，看诊断");
        return false;
    }

    /** 按 Login 0x1084f20.. 把自己补进位置，然后原生 AddPlayerToSlot 摆人，再调原生 Client Load Block 0x1086960(PC)
     *  （LoadBlock(管理器, PC+0xbdc, PC+0xbe0) + FirstLoad）。返回位置 PlayerStart 坐标（x,y,z）。 */
    static double[] join(long b, long gm, long pc, long mgr) throws Exception {
        StringBuilder sb = new StringBuilder("分配位置：");
        long slots, slot = 0; int n, cnt, blk, bdc0;
        boolean in = false; int allLoaded, handled; double[] ps = null;
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
            slots = Server.rd(m, gm + 0xa40); n = (int) Server.rd(m, gm + 0xa44); cnt = (int) Server.rd(m, gm + 0xa4c);
            allLoaded = rb(m, gm + 0xa70); handled = rb(m, gm + 0xa21); bdc0 = (int) Server.rd(m, pc + 0xbdc);
            for (int i = 0; i < n && !in; i++) {
                long s = slots + 0x34L * i, d = Server.rd(m, s + 0x10); int k = (int) Server.rd(m, s + 0x14);
                for (int j = 0; j < k && j < 64; j++) if (Server.rd(m, d + 4L * j) == pc) { in = true; slot = s; }
            }
        }
        sb.append("位置 ").append(n).append(" 个，块数 ").append(cnt).append("，+a70=").append(allLoaded).append(" +a21=").append(handled).append("，PC+bdc=").append(bdc0).append("；");
        // v3.46：游戏线程在建出生点的同一帧已经按原生步骤做完（cave 阶段 ≥3）→ 这里只记诊断，不再调任何东西
        if (caveOn && bdc0 >= 0 && ib != null && ib.getInt(8) >= 3) {
            int cur, curN, f1; long be4;
            try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
                if (in) ps = slotStart(m, slot);
                cur = (int) Server.rd(m, mgr + 0x440); curN = (int) Server.rd(m, mgr + 0x43c); f1 = rb(m, mgr + 0x400); be4 = Server.rd(m, pc + 0xbe4);
            }
            double[] now = me();
            sb.append(caveText()).append("；位置 ").append(in ? String.valueOf((slot - slots) / 0x34) : "?").append(" PlayerStart ").append(ps == null ? "没有" : p3(ps))
              .append("；人物 ").append(p3(now)).append(ps != null && now != null && Math.hypot(now[0] - ps[0], now[1] - ps[1]) < 5000 ? "（在出生点）" : "（不在出生点）")
              .append("；管理器 +440=").append(cur).append(" +43c=").append(curN).append(" +400=").append(f1).append(f1 == 0 ? "（FirstLoad 已跑）" : "（FirstLoad 没跑）")
              .append(" PC+be4=").append(Long.toHexString(be4)).append(be4 == mgr ? "（=管理器）" : "（≠管理器）");
            block = sb.toString(); note(block);
            return ps;
        }
        sb.append(caveText()).append(" → 线程补做（原生步骤，晚几帧）；");
        if (!in) {
            long[] r = Server.gcall(b + FIND_SLOT, gm, pc, 0, 0, 3000);
            slot = r[1];
            if (r[0] == 0) { block = sb.append("挑位置 0x10845b0 没跑（游戏线程没接）").toString(); note(block); return null; }
            if (slot < slots || slot >= slots + 0x34L * n || (slot - slots) % 0x34 != 0) { block = sb.append("挑位置返回 ").append(Long.toHexString(slot)).append(" 不在位置数组里").toString(); note(block); return null; }
            String a = append(b, slot + 0x10, pc);
            sb.append("塞进位置 ").append((slot - slots) / 0x34).append(" 玩家数组 ").append(a).append("；");
            if (!"ok".equals(a)) { block = sb.toString(); note(block); return null; }
        } else sb.append("已在位置 ").append((slot - slots) / 0x34).append("；");
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
            blk = (int) Server.rd(m, slot);
            ps = slotStart(m, slot);
            sb.append("块号 ").append(blk).append("（位置号 ").append((slot - slots) / 0x34).append("）PlayerStart ").append(ps == null ? "没有" : p3(ps) + "（" + Long.toHexString((long) ps[3]) + "）").append("；");
        }
        double[] before = me();
        sb.append("摆人前人物 ").append(p3(before)).append("；");
        // 原生摆人：AddPlayerToSlot 自己写 PC+bdc/+be0、FindPlayerStart、SetActorLocation/Rotation
        if (bdc0 >= 0) sb.append("PC 已有块号 ").append(bdc0).append("，AddPlayerToSlot 会直接退（already has a index），不调；");
        else {
            long[] r = Server.gcall(b + ADD_TO_SLOT, gm, pc, 0, 0, 3000);
            sb.append("原生 AddPlayerToSlot ").append(r[0] != 0 ? "已跑" : "没跑（游戏线程没接）").append("；");
        }
        int bdc, be0;
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) { bdc = (int) Server.rd(m, pc + 0xbdc); be0 = (int) Server.rd(m, pc + 0xbe0); }
        double[] after = me();
        sb.append("之后 PC+bdc=").append(bdc).append(" +be0=").append(be0).append("，人物 ").append(p3(after));
        boolean placed = ps != null && after != null && Math.hypot(after[0] - ps[0], after[1] - ps[1]) < 5000;
        if (placed) sb.append("（原生摆到出生点了）；");
        else   // v3.46 用户：不再退回传送，只记原因
            sb.append("（原生没摆到出生点：").append(ps == null ? "位置没有 PlayerStart" : bdc < 0 ? "AddPlayerToSlot 没写块号（PlayerState 空或没找到位置）" : "FindPlayerStart 没挑到这个位置的 PlayerStart").append("）；");
        // v3.45：不再自己调 LoadBlock，直接调原生 Client Load Block 实现 0x1086960(PC)：
        //   TActorIterator 找地块管理器 → PC+0xbe4 = 管理器 → LoadBlock(PC+0xbdc, PC+0xbe0) → PC vt+0x8bc 非空就 管理器 vt+0x2f8 FirstLoad。
        //   FirstLoad（TileMode 0x1165e34 → 0x115f130）按人物位置做第一次 Update、RequestsManager 提交、最后把 +0x400 bFirstUpdate 清 0；
        //   客户端 Tick 0x115e7dc 只在 +0x400==0 时才调 Update（vt+0x304）。v3.42~v3.44 只调 LoadBlock、没调 FirstLoad → +0x400 永远 1 → 一帧都不 Update。
        int f0, cur, curN, aft, aftN, f1; long be4;
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) { cur = (int) Server.rd(m, mgr + 0x440); curN = (int) Server.rd(m, mgr + 0x43c); f0 = rb(m, mgr + 0x400); }
        sb.append("加载块前 管理器 +440 当前块=").append(cur).append(" +43c 块数=").append(curN).append(" +400 bFirstUpdate=").append(f0).append("；");
        if (bdc >= 0 && be0 >= 1) {
            long[] r = Server.gcall(b + CLIENT_LOAD_BLOCK, pc, 0, 0, 0, 3000);
            sb.append("原生 Client Load Block 0x1086960(PC) ").append(r[0] != 0 ? "已跑" : "没跑（游戏线程没接）");
        } else sb.append("AddPlayerToSlot 没写 PC 块号（bdc=").append(bdc).append(" be0=").append(be0).append("），原生 Client Load Block 会直接退，不调；不自己 LoadBlock");
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) { aft = (int) Server.rd(m, mgr + 0x440); aftN = (int) Server.rd(m, mgr + 0x43c); f1 = rb(m, mgr + 0x400); be4 = Server.rd(m, pc + 0xbe4); }
        sb.append("；之后 +440=").append(aft).append(" +43c=").append(aftN).append(" +400=").append(f1).append(" PC+be4=").append(Long.toHexString(be4)).append(be4 == mgr ? "（=管理器）" : "（≠管理器 " + Long.toHexString(mgr) + "）");
        if (f1 != 0 && aft >= 0) {
            // 原生里 FirstLoad 只在 PC vt+0x8bc 返回非空时调。读一下它返回什么，再直接调同一个原生 FirstLoad（不是兜底：就是 Client Load Block 下一步）。
            long[] v = Server.gcall(b + PC_GET_8BC, pc, 0, 0, 0, 3000);
            long[] r = Server.gcall(b + FIRST_LOAD_TILE, mgr, 0, 0, 0, 3000);
            int f2; try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) { f2 = rb(m, mgr + 0x400); }
            sb.append("；+400 还是 1 → PC vt+0x8bc 返回 ").append(v[0] != 0 ? Long.toHexString(v[1]) : "没跑").append(v[0] != 0 && v[1] == 0 ? "（空，原生就不调 FirstLoad）" : "")
              .append("，直接调管理器 FirstLoad 0x1165e34 ").append(r[0] != 0 ? "已跑" : "没跑").append("，之后 +400=").append(f2);
        } else if (f1 == 0) sb.append("（FirstLoad 已跑：+400 清 0，Tick 开始 Update）");
        sb.append("；不填服务器层 +0x37c");
        block = sb.toString(); note(block);
        if (block.contains("没跑") || block.contains("没摆到")) GameOverlay.toastAny("训练岛：原生摆人没成功，已记诊断");
        return ps;
    }

    /** 摆人后跟 120 秒：每 5 秒记坐标/地形块/+a70/+a21 和流式简况，脚下射线看地形出来没有。
     *  v3.46 用户：不冻重力、不抬人、不传送，只记录（地形出来 40 秒后停）。 */
    static void follow(long b, Veh.Names nm, long gm, long pc, double[] ps, long mgr) throws Exception {
        StringBuilder tr = new StringBuilder("坐标/地形块随时间：");
        long w0 = System.currentTimeMillis(); boolean ground = false, done = false; long lastRec = -10;
        while (System.currentTimeMillis() - w0 < 120000) {
            if (!Con.training || !Con.inMatch) return;
            long s = (System.currentTimeMillis() - w0) / 1000;
            if (s - lastRec < 5) { Thread.sleep(1000); continue; }
            lastRec = s;
            int a70, a21, bdc; try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) { a70 = rb(m, gm + 0xa70); a21 = rb(m, gm + 0xa21); bdc = (int) Server.rd(m, pc + 0xbdc); }
            int land = scan(b, nm).land; double[] p = me();
            float g = Float.NaN;
            if (!ground && p != null) { Trace.Hit h = Trace.down((float) p[0], (float) p[1], (float) p[2] + 5000, (float) p[2] - 20000); if (h != null && h.land) g = h.z; }
            tr.append(s).append("s ").append(p3(p)).append(" 地形块 ").append(land).append(" a70=").append(a70).append(" a21=").append(a21).append(" bdc=").append(bdc).append(Float.isNaN(g) ? "" : " 地面 " + (int) g)
              .append(" [").append(streamDiag(b, nm, mgr, false)).append("]；");
            track = tr.toString();
            if (s <= 6 || s % 20 < 5) { stream = streamDiag(b, nm, mgr, true); }
            if (!ground && !Float.isNaN(g)) { ground = true; tr.append("【脚下地形出来了，第 ").append(s).append(" 秒】"); }
            if (a21 != 0 && !done) { done = true; tr.append("【OnAllDuplicateLevelLoaded 已处理 +a21=1】"); }
            if (ground && s >= 40) break;
            Thread.sleep(1000);
        }
        track = tr.toString(); note(track);
        stream = streamDiag(b, nm, mgr, true); note(stream);
        double[] me = me();
        state = "训练岛：摆人后人物在 " + p3(me) + (ps == null ? "" : "，离位置出生点 " + (me == null ? "?" : (int) (Math.hypot(me[0] - ps[0], me[1] - ps[1]) / 100) + " 米"));
        note(state);
    }

    /** 缓存数组（TArray，每项 0x20：[0] 层名 FName，+8 关卡数组，+0xc 关卡数，+0x18 瓦片信息数）→ 「名字×关卡数」 */
    static String cacheNames(RandomAccessFile m, Veh.Names nm, long arr) {
        long d = Server.rd(m, arr); int n = (int) Server.rd(m, arr + 4);
        if (d < 0x10000 || n <= 0 || n > 64) return n + " 个";
        StringBuilder sb = new StringBuilder().append(n).append(" 个（");
        for (int i = 0; i < n; i++) { long e = d + 0x20L * i; sb.append(Box.nameOf(m, nm, Server.rd(m, e))).append('×').append((int) Server.rd(m, e + 0xc)).append(i + 1 < n ? " " : ""); }
        return sb.append("）").toString();
    }

    /** 流式状态（只读）。full=false 时只出一小段：层更新计数和、包住人物的关卡数、关卡流对象 已加载/可见/总数。
     *  偏移（libUE4 静态读码，未实测）：管理器 +0x2f4/+0x2f8 当前层 TSharedPtr 数组（每项 8 字节）；层对象 +8 层名，集合对象 +0x1c
     *  （Landscape 类层 +0x34/+0x2c/+0x24）；集合 +0x14/+0x18 关卡项 TSharedPtr 数组，关卡项 +8 minX、+0xc minY、+0x14 maxX、+0x18 maxY（AddLevel 0x1178e00）；
     *  管理器 +0x358/+0x35c TileLayer 建的关卡流对象；ULevelStreaming +0x110 LoadedLevel，ULevel +0x174 bit3 可见（IsLevelLoaded/IsLevelVisible 原生）；
     *  world = 管理器 Outer(关卡) 的 Outer，+0x54/+0x58 StreamingLevels，+0x768 OriginLocation。 */
    static String streamDiag(long b, Veh.Names nm, long mgr, boolean full) {
        StringBuilder sb = new StringBuilder(full ? "流式状态：" : "");
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
            if (mgr < 0x10000 || !Mods.isObj(m, b, mgr)) return sb.append("管理器没了").toString();
            double[] me = me(); float px = me == null ? Float.NaN : (float) me[0], py = me == null ? Float.NaN : (float) me[1];
            long ld = Server.rd(m, mgr + 0x2f4); int ln = (int) Server.rd(m, mgr + 0x2f8);
            int lvTot = 0, lvIn = 0; StringBuilder per = new StringBuilder();
            for (int i = 0; i < ln && i < 40 && ld >= 0x10000; i++) {
                long L = Server.rd(m, ld + 8L * i); if (L < 0x10000) { per.append(" 空"); continue; }
                long vt = Server.rd(m, L); String lname = Box.nameOf(m, nm, Server.rd(m, L + 8));
                // v3.45 改正：层 +0x10 是管理器弱指针（v3.44 读成「更新计数」，所有层都是 116169 = 管理器 ObjectIndex）。
                // 关卡在层的集合对象里：普通层 vt+0x10(0x1168b40) 返回 +0x1c；Landscape 类层（vt 0x4caa624，vt+0x10=0x1155e00）先 +0x34，否则 +0x2c / +0x24。
                // 集合 AddLevel 0x1178e00：+0x14/+0x18 关卡项 TSharedPtr 数组；关卡项 [0] 关卡流对象弱指针，+8 minX、+0xc minY、+0x14 maxX、+0x18 maxY。
                long coll; String cw;
                if (vt - b == 0x4caa624L) { coll = Server.rd(m, L + 0x34); cw = "+34"; if (coll < 0x10000) { coll = Server.rd(m, L + 0x2c); cw = "+2c"; } if (coll < 0x10000) { coll = Server.rd(m, L + 0x24); cw = "+24"; } }
                else { coll = Server.rd(m, L + 0x1c); cw = "+1c"; }
                long ed = coll >= 0x10000 ? Server.rd(m, coll + 0x14) : 0; int en = coll >= 0x10000 ? (int) Server.rd(m, coll + 0x18) : 0;
                if (en < 0 || en > 5000) en = -1;
                int in = 0; float x0 = Float.MAX_VALUE, y0 = Float.MAX_VALUE, x1 = -Float.MAX_VALUE, y1 = -Float.MAX_VALUE;
                for (int j = 0; j < en && j < 400 && ed >= 0x10000; j++) {
                    long E = Server.rd(m, ed + 8L * j); if (E < 0x10000) continue;
                    float a = Mods.f(m, E + 8), c = Mods.f(m, E + 0xc), d = Mods.f(m, E + 0x14), e = Mods.f(m, E + 0x18);
                    x0 = Math.min(x0, a); y0 = Math.min(y0, c); x1 = Math.max(x1, d); y1 = Math.max(y1, e);
                    if (px >= a && px <= d && py >= c && py <= e) in++;
                }
                lvTot += Math.max(en, 0); lvIn += in;
                if (full) per.append("\n    层").append(i).append(' ').append(lname).append(" vt+").append(Long.toHexString(vt - b)).append(" 集合").append(cw).append(" 关卡 ").append(en).append(" 包住人物 ").append(in)
                    .append(en > 0 && x0 < Float.MAX_VALUE ? " 范围 " + (int) x0 + ".." + (int) x1 + "," + (int) y0 + ".." + (int) y1 : "");
            }
            long sd = Server.rd(m, mgr + 0x358); int sn = (int) Server.rd(m, mgr + 0x35c);
            int loaded = 0, vis = 0, dup = 0; java.util.HashSet<Long> uniq = new java.util.HashSet<>(); StringBuilder names = new StringBuilder();
            for (int i = 0; i < sn && i < 600 && sd >= 0x10000; i++) {
                long S = Server.rd(m, sd + 4L * i); if (S < 0x10000) continue;
                if (!uniq.add(S)) { dup++; continue; }
                long lvl = Server.rd(m, S + 0x110); boolean v = lvl >= 0x10000 && (rb(m, lvl + 0x174) & 8) != 0;
                if (lvl >= 0x10000) loaded++; if (v) vis++;
                if (full && names.length() < 1500 && (i < 12 || Alive.objName(m, nm, S).contains("Heightmap")))
                    names.append(Alive.objName(m, nm, S)).append(lvl >= 0x10000 ? (v ? "(载+显)" : "(载)") : "(未载)").append(' ');
            }
            if (!full) return sb.append("+400=").append(rb(m, mgr + 0x400)).append(" 包住人物关卡 ").append(lvIn).append("/").append(lvTot).append(" 流对象 载").append(loaded).append(" 显").append(vis).append("/").append(uniq.size()).append(dup > 0 ? " 重复" + dup : "").toString();
            long lvl0 = Server.rd(m, mgr + 0x18), world = lvl0 >= 0x10000 ? Server.rd(m, lvl0 + 0x18) : 0;
            int wn = -1, wl = 0; String wName = "?"; int ox = 0, oy = 0;
            if (world >= 0x10000) {
                wName = Alive.clsName(m, nm, world);
                long wd = Server.rd(m, world + 0x54); wn = (int) Server.rd(m, world + 0x58);
                for (int i = 0; i < wn && i < 1000 && wd >= 0x10000; i++) { long S = Server.rd(m, wd + 4L * i); if (S >= 0x10000 && Server.rd(m, S + 0x110) >= 0x10000) wl++; }
                ox = (int) Server.rd(m, world + 0x768); oy = (int) Server.rd(m, world + 0x76c);
            }
            sb.append("管理器 ").append(Long.toHexString(mgr)).append(" +400 bFirstUpdate=").append(rb(m, mgr + 0x400)).append(rb(m, mgr + 0x400) != 0 ? "（≠0：客户端 Tick 不调 Update，FirstLoad 没跑）" : "（0：FirstLoad 跑过，Tick 在 Update）").append(" +409=").append(rb(m, mgr + 0x409))
              .append(" +43c 块数=").append((int) Server.rd(m, mgr + 0x43c)).append(" +440 当前块=").append((int) Server.rd(m, mgr + 0x440)).append(" +2c8 服务器层=").append(rb(m, mgr + 0x2c8))
              .append(" 块偏移 +468/+46c=").append((int) Mods.f(m, mgr + 0x468)).append(',').append((int) Mods.f(m, mgr + 0x46c))
              .append(" 策略 +30c=").append(Long.toHexString(Server.rd(m, mgr + 0x30c))).append(" +314=").append(Long.toHexString(Server.rd(m, mgr + 0x314)))
              .append("；人物 ").append(p3(me)).append("；world ").append(wName).append(" 原点 ").append(ox).append(',').append(oy)
              .append("；客户端层缓存 +370 ").append(cacheNames(m, nm, mgr + 0x370)).append("；服务器层缓存 +37c ").append(cacheNames(m, nm, mgr + 0x37c))
              .append("；当前层 ").append(ln).append(" 个，关卡项 ").append(lvTot).append("，包住人物 ").append(lvIn).append(per)
              .append("\n  TileLayer 关卡流对象 +358：").append(sn).append(" 个（不同 ").append(uniq.size()).append("，重复指针 ").append(dup).append("），已加载 ").append(loaded).append("，可见 ").append(vis)
              .append("；world StreamingLevels ").append(wn).append(" 个，已加载 ").append(wl)
              .append("\n  关卡流对象：").append(names);
        } catch (Throwable t) { sb.append("读出错 ").append(t); }
        return sb.toString();
    }

    /** 射击训练区：按 pak 里 LogicLevel 的 shootreturn + 本块偏移算出来，再数它 150 米内的靶子。只诊断，不传送（原生出生点离它约 61 米）。 */
    static void rangeDiag(long b, Veh.Names nm, long mgr, long pc) throws Exception {
        int bdc, cnt, w, h;
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) { bdc = (int) Server.rd(m, pc + 0xbdc); cnt = (int) Server.rd(m, mgr + 0x43c); w = (int) Server.rd(m, mgr + 0x41c); h = (int) Server.rd(m, mgr + 0x420); }
        double[] off = offset(cnt, Math.max(bdc, 0), w, h);
        double rx = SHOOT_X + off[0], ry = SHOOT_Y + off[1];
        double[] me = me();
        Scan s = scan(b, nm);
        int nearR = 0; for (float[] p : s.target) if ((p[0] - rx) * (p[0] - rx) + (p[1] - ry) * (p[1] - ry) < 15000.0 * 15000.0) nearR++;
        range = "射击训练区：按 pak shootreturn + 块 " + bdc + " 偏移（块数 " + cnt + "）算在 " + (int) rx + "," + (int) ry + "," + (int) SHOOT_Z + "；人物 " + p3(me)
            + (me == null ? "" : "，离它 " + (int) (Math.hypot(me[0] - rx, me[1] - ry) / 100) + " 米") + "；它 150 米内靶子 " + nearR + " 个（全图靶子 " + s.target.size() + "）" + clusters(s.target);
        note(range);
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

    static volatile String pickup = "物资链：没查";
    /** v3.53 训练岛枪从哪来：物资点组件个数、GameMode 是不是 BR GameMode、PickupManager(GM+0x90c)、刷物资信息、常驻层/Pickups 层的关卡加载了没有 */
    static String pickDiag(long b, Veh.Names nm) throws Exception {
        StringBuilder sb = new StringBuilder("物资链（进岛 15 秒）：ServerOnly 层按视野加载 " + soState + "；");
        double[] q = Veh.info(); long gm = q == null ? 0 : (long) q[4];
        try (RandomAccessFile m = new RandomAccessFile("/proc/self/mem", "r")) {
            long g = Server.rd(m, b + 0x51250acL);
            long objs = Server.rd(m, g + 0x10), num = Server.rd(m, g + 0x18);
            if (objs < 0x10000 || num <= 0 || num > 0x200000) return sb.append("对象表不对").toString();
            byte[] raw = new byte[(int) num * 16]; m.seek(objs); m.readFully(raw);
            ByteBuffer bb = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
            HashMap<Long, Integer> kind = new HashMap<>();
            int comp = 0, compOwn = 0, info = 0, vol = 0, pick = 0; StringBuilder cn = new StringBuilder();
            for (int i = 0; i < num; i++) {
                long o = bb.getInt(i * 16) & 0xffffffffL; int fl = bb.getInt(i * 16 + 4);
                if (o < 0x10000 || (fl & 0x20000000) != 0) continue;
                long c = Server.rd(m, o + 0xc); if (c < 0x10000) continue;
                Integer k = kind.get(c);
                if (k == null) {
                    String n = Alive.objName(m, nm, c), ch = (n.contains("ick") || n.contains("Refresh") || n.contains("Comp")) ? chain(m, nm, c) : "|" + n + "|";
                    k = ch.contains("|NZBRPickupConfigComponent|") ? 1 : ch.contains("|PickUpRefreshInfoState|") ? 2 : ch.contains("|NZBRPickUpRefreshTriggerVolume|") ? 3
                        : (ch.contains("|NZPickUpWrapperActor|") || ch.contains("|PickUpWrapperActor|") || ch.contains("|NZPickUp|") || ch.contains("|PickUp|")) ? 4 : 0;
                    kind.put(c, k);
                    if (k == 1 && cn.length() < 200) cn.append(n).append(' ');
                }
                if (k == 0 || (Server.rd(m, o + 4) & 0x10) != 0) continue;
                if (k == 1) { comp++; long own = Server.rd(m, o + 0xa4); if (own >= 0x10000) compOwn++; }
                else if (k == 2) info++; else if (k == 3) vol++; else pick++;
            }
            sb.append("物资点组件 ").append(comp).append(" 个（").append(cn.toString().trim()).append("，有 Owner ").append(compOwn).append("）；刷物资信息 ").append(info).append("；刷物资区 ").append(vol).append("；地上物资对象 ").append(pick);
            if (gm >= 0x10000) {
                String gc = chain(m, nm, Server.rd(m, gm + 0xc));
                long pm = Server.rd(m, gm + 0x90c);
                sb.append("；GameMode ").append(gc.contains("|NZBattleRoyaleGameMode|") ? "是" : "不是").append(" BR GameMode").append("，PickupManager(+90c) ")
                  .append(pm >= 0x10000 && Mods.isObj(m, b, pm) ? Alive.clsName(m, nm, pm) : Long.toHexString(pm));
            }
            // 常驻层 / ServerOnly/Pickups 层的关卡流对象加载情况（关卡项 [0] = 流对象弱指针 index/serial）
            long mgr = gm >= 0x10000 ? Server.rd(m, gm + 0xa3c) : 0;
            if (mgr >= 0x10000 && Mods.isObj(m, b, mgr)) {
                long ld = Server.rd(m, mgr + 0x2f4); int ln = (int) Server.rd(m, mgr + 0x2f8);
                for (int i = 0; i < ln && i < 40 && ld >= 0x10000; i++) {
                    long L = Server.rd(m, ld + 8L * i); if (L < 0x10000) continue;
                    String lname = Box.nameOf(m, nm, Server.rd(m, L + 8));
                    if (!lname.startsWith("AlwaysLoad") && !lname.contains("Pickups") && !lname.equals("ServerOnly")) continue;
                    long coll = Server.rd(m, L + 0x1c); if (coll < 0x10000) continue;
                    long ed = Server.rd(m, coll + 0x14); int en = (int) Server.rd(m, coll + 0x18);
                    int ok = 0, vis = 0, bad = 0;
                    for (int j = 0; j < en && j < 64 && ed >= 0x10000; j++) {
                        long E = Server.rd(m, ed + 8L * j); if (E < 0x10000) continue;
                        int idx = (int) Server.rd(m, E);
                        if (idx < 0 || idx >= num) { bad++; continue; }
                        long S = bb.getInt(idx * 16) & 0xffffffffL; if (S < 0x10000) { bad++; continue; }
                        long lvl = Server.rd(m, S + 0x110); if (lvl >= 0x10000) { ok++; if ((rb(m, lvl + 0x174) & 8) != 0) vis++; }
                    }
                    sb.append("；层 ").append(lname).append(" 关卡 ").append(en).append(" 已载 ").append(ok).append(" 可见 ").append(vis).append(bad > 0 ? " 找不到 " + bad : "");
                }
            }
        }
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
