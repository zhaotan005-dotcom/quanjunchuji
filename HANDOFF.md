# 交接给下一个智能体（最新在本文件末尾「v2.97」）

先读：README.md → notes/history.md（末尾最新，含所有 RE 偏移）→ zframe/build3.sh。

- v2.37–v2.42：衣服页只剩风衣+吉利服1-3；车可炸诊断；开车断线闪退修复（find 只认 Character）；去掉拉人；换弹诊断（背包子弹 +0x60c）。详见 notes/history.md
- v2.44：客户端包加载时选单机/联机（选单机=完整单机版）；加入地址改成可输入可删的输入框（粘贴/清空/加入）。
- v2.43：诊断导出加「资源清单」（全部 pak 挂载目录、关键词、游戏目录大文件），日志关键词加空投/信号枪/道具/军团。
- v2.36：上车补丁 0xdb00f0、菜单独立窗口、拉回/换弹/无敌/人机带枪、改名服务端（未测）
- v2.31: host auto-teleports joined players next to itself (vcave T = K2_TeleportTo 0x273d654), button 把加入的人拉到我身边; speed/jump/gun toggles no longer reset on map load.
- v2.30: client domain join resolves to IPv4 in background again (direct domain open failed).

## 当前版本
- **v2.26（vc127）**：标题区分服务端/客户端；客户端只粘贴+加入（域名直连）；房主刷人机回来；皮卡（试 31/32）；吉利服按钮；联机页回大厅按钮；房主加速/高跳/无限子弹同步给加入的玩家（Crew）。待查：客户端上车「座位有人」、配件不生效、客户端退出后重进失败。
- **v2.25（vc126）**：加入房间支持穿透域名（粘贴 + 后台解析）。
- **v2.24（vc125）**：客户端加入房间改用自带数字键盘输 IP（系统键盘删除键被游戏吃掉）。
- **v2.23（vc124/core124）**：对局页日志区加「查剩余人数」只读探针，查只有房主一人时剩余为什么是 2（不许直接改成 1）。等用户：进图后点它，再导出诊断。
- **v2.22（vc123/core123）**：房主开房页加正常/不刷毒对局。v2.21 实测联机能进同一局、不闪退；房主不上飞机（不算玩家），客户端在飞机上。见 history v2.22。
- **v2.21（vc122/core122）**：房主版地图物资/门改成大厅开关，默认关（v2.20 房主在云手机上进图后闪退，用来定位是不是 Loot）。等用户：关着进图还闪不闪、闪退后点「导出诊断」。
- **v2.20（vc121/core121）联机版**：releases/ZFrame-v2.20-房主/客户端/单机.apk，**未实测**。只改房主版：地图物资/门（ServerOnly 流式层进 CurrentLayers，cave 0x171e720；物资点 NetMode 门槛放开房主，0x10c0ba4）+ 空投空箱兜底（日志触发，在箱子位置用 cave 'G' 放一套）。客户端版、单机版行为不变（Loot 只在 Ed.ED==1 装，cave 里还判断 NetMode==2）。详见 history.md v2.20。
  - 要用户测：房主开房（新海岛/老海岛/雨林）→ 能否进图不闪退、房主身边房子里有没有物资和门、客户端加入后看不看得到同样的物资/门、空投落地后箱子旁边有没有 AWM 一套；测完点「导出诊断」看「地图物资/门」那行（服务器层关卡行数、物资生成日志行数、门日志行数、空箱次数）。
  - 如果「服务器层关卡 0 行」：客户端包可能根本没有 ServerOnly 关卡，下一步就只能按位置撒物资（cave 'G'）。
- v2.19（vc120/core120，commit 94c08e8），releases/ZFrame-v2.19-单机/房主/客户端.apk，**未实测**，只发了单机版给用户。
- v2.18：丢枪闪退修复（Recast queryPolygons 空 m_nav，0x25cb8e8 → cave 0x171e700 返回失败）。未实测。
- 一体包（游戏+框架一个 APK）在沙盒 /workspace/work/full/，太大不进库；装了 ZFrame 单机 APK 就会覆盖一体包内置核心 → **以后只发小的 ZFrame-v*-单机.apk**（用户明确要求）。
- 构建：`cd zframe && ./build3.sh <旧> <新> <旧vc> <新vc>`（三个版本一起出，Ed.ED 0 单机/1 房主/2 客户端）。改完写 notes/history.md、本文件，push。

## 规矩
简体中文、直接给结果；每版小改动，用户在手机上测（荣耀 X70，安卓16，无 root）；不推荐第三方工具/虚拟机；不解密 pak、不绕保护、不做联网外挂；不删枪和门；沐岑(libMczi)已彻底删除。
- **不要用补血/无敌之类治标方案，找游戏原生机制；载具可被打爆是正常的**（用户 2026-11-22 21:02「治标不治本」「车炸是因为我拿枪打爆的」）。训练营死亡由游戏自己的复活系统处理（v4.11 ModeMap）。崩溃看 下载/ZFrame/crash-*.txt（Bugly tomb，只看 pc/lr/fault addr）。

## v2.19 做了（待用户实测确认）
1. 菜单「常用」下面新页「功能」(页号 8)：加速、高跳；「动作放慢」默认折叠。
2. 大厅菜单高度自适应不留白；腾讯加载页不显示菜单，进大厅才出现（GameOverlay.lobbyReady）；默认位置中上。
3. 人机页去掉难度，改「固定人机 / 自由人机」。手动刷的也配枪（Bots.manual→loadout）；固定人机每秒把移动组件 MaxWalkSpeed 0x164/蹲 0x168/MaxAcceleration 0x178/JumpZ 0x128 写 0。
4. 所有人机随机衣服（Bots.dress，301/303 款按部位随机，用 'I' 发到身上）和动画角色名字（Bots.NAMES；写 PlayerState 的 FString，候选偏移 0x210/0x260/0x2f0，哪个是显示名**未确认**，看诊断「人机名字」）。
5. 单机版看到开始加载大厅就开自建服务端（仓库衣服不用等）。

## 待做（用户 11:07 提的，按顺序）
1. **空投落地是空的、地图不刷物资、门不显示**：v2.20 先做了联机房主版（待实测）；单机版还没做（单机 NetMode 0，同样的 SL cave 把条件改成 0/2 就能试）。原记录：（单机）。老问题：ServerOnly 流式关卡层（Door/Pickups/车点）单机不加载，见 history「StreamingServerLayer」「ANZSceneDoorManager」。地上刷物品可用 cave 'G'（SpawnOnePickUp 0x119d884）；可考虑自己按建筑位置撒物资。空投箱内容要找空投 actor 的生成物品逻辑。
2. **双排/四排可选 + 人机队友填充**：选图界面单机下双排四排不可选；队友名字从 Bots.NAMES 随机（用户给的名单，不要「幼儿园小朋友」这类）。需要：解锁组队模式选择、刷出的人机设成玩家同队（PlayerState 队伍字段 +0x340 附近、IsTeammate 判定在 NZBRTeamInfo 0x102281c 附近）并让队友 AI 不打玩家。
3. **军需页面抽奖**：大厅协议走自建服务端（Server.java，CSPkg protobuf，协议定义在 libUE4 里，导出在 game/proto）。要找军需/抽奖的请求号并回包。
4. **电竞页面按钮**：用户要大厅的电竞入口（两个，左上角附近）点了有反应但不跳转。已向用户要大厅截图标位置，还没收到。
5. 用户还要测：v2.17 两个模式（正常/不刷毒）、v2.18 丢枪不闪退、v2.19 以上全部。
6. 老的未完成：掉虚空、结算页（人机全死不结算）、吉利服编号确认、房主一体包仍是 v2.16c。
- v2.45 (vc146): 毒圈底层：InitArea 读我们的表、圈心由游戏自己算（cave SLACK+0x990..0xaa8），不再事后改本局数组；未实测。
- v2.46 (vc147): 单机开局读秒 45→20 秒（START 补丁 0x101977c movt r0,#0x41a0，大厅时打）；含 v2.45 毒圈底层；未实测。
- v2.47 (vc148): 启动删旧 AkDiag 明文 Engine.ini；含 v2.46；审计记录见 history 23:31。
- v2.48 (vc149): 军需自选抽奖（51001-51008）离线回包，未实测；大厅灯光 RE 起点在 history。
- v2.49 (vc150): 装备页「全部道具」（内存扫物品编号，按分类刷）。只发服务端+客户端。
- v2.50 (vc151): 地上模型替身 cave（0xce6068/0xce62a0 → SLACK+0x300），全部道具页列游戏缺的资源路径。未实测。

- v2.52 (vc153): 在线内核——以后改完 build3 + push（core/ 目录）用户重开游戏就更新，不用装包；「游戏里的框架」不再提示更新补丁。未实测。

## 下一步（2026-11-18 01:34，v2.50 之后）
1. 等用户 v2.50 截图：「全部道具」页底部的缺资源路径（丢道具、刷喷气背包后）。
   - 有「地上模型」行 → 替身 cave 应生效；没有 → 行指针 +0x2f8 为空（0xce6c38 直接跳过），要在发起方补。
   - 喷气背包特效路径加载失败 → 文件不在手机上；可考虑换成已有粒子路径（需先确认路径存在）。
2. 大厅灯光：ANZFrontEndSceneMgr InitLight 0x12056b4，标签 HallDefaultSkyLight/HallDefaultDirectLight（utf32 0x43c9118/0x43c9064）。
3. 军需（51001-51007 已接，用户说点不开，暂停）。
4. 未回的问题见 notes/history.md 末尾和项目文档：客户端换弹循环、配件不生效、出生在飞机上、服务端设置退出卡死。
- SLACK 空位：0x224–0x2b7 是 v2.51 'U' cave（ucave.py）；0x2b8–0x2ff 还空；0x300–0x3c0 是 Prop cave；0x400–0x98f vcave；0x990–0xaa7 毒圈 cave。
- v2.51 (vc152, 2026-11-18 02:40, 未实测)：装备页「用道具（草丛/盾/雷）」——经典界面没有道具栏，功能道具选不中所以用不了；照抄人机放盾：背包 vt+0x4d0 拿出、vt+0x484 放下（'U' cave SLACK+0x224，vcave 0x5c 处 bne done→bne ucave）。页上会显示每个道具的场景模型类（TargetProp +0x3f0），空 = 蓝图不在手机上、放不出来。服务端地图：核对挂载表后只有现有三张完整（训练场主关卡不存在、沙漠是按需下载的包），没加新图。三个包 releases/ZFrame-v2.51-*.apk（基于 v2.49-单机.apk 重打，v2.50 单机包没进库）。
  - 要用户测：刷草丛 → 装备页「查背包里的道具」→ 截图那几行（槽/数量/放出来是什么）→「拿出」→ 看游戏界面有没有放置预览、开火键能不能放 →「放下 / 使用」→ 地上有没有草丛；不行就截图页底「最近游戏日志」。

## v2.66（vc167）2026-11-18 10:2x
- 收回（非伪装）：不走游戏回收读条 0x110d6a0（实测收不回），改 Prop.destroyBack：核对场景道具虚表+0x164 == 人物虚表+0x164（AActor K2_DestroyActor），ucall 删掉；背包里没同类就按类名 11 位编号 give 回 1 个。每次点收回都上传 prop- 日志。伪装岩仍用 0x110dc50。
- 喷气背包卡键位：新 Jet.java。UNZBRPlayerPropAnimComponent(+0xa0=人物) +0x1d0 开关；0x110c28c(组件,0/1) 进/出人物状态 3（0xc904f4/0xc9065c）。道具键轮询里 Jet.tick：开着但背包里没有类名含 jetpack 的物品（连续 2 次）→ 调 0x110c28c(组件,0)。常用页「恢复键位」手动关并上传 jet- 日志。未实测：键位是否真由状态 3 控制。
- 下一步（没做）：联机「邀请好友」。游戏自带邀请走腾讯组队服务器，做不了。替代方案：服务端(role 1)进图后每 45 秒往 ntfy 主题（如 zf-qj-room-k7r2p9m4x1）发 {房间id, 地图, 内网IP}；客户端在大厅每 10 秒轮询，新房间弹 GameDlg「好友开房了 · 加入」，地址用 Lan.lastIp（没有就用内网 IP）→ Lan.join。

## v2.87（2026-11-18 14:30）起服务端重新用 main 源码打（build_all.sh 一次打三个包），server-2.55 分支停用

## v2.67 服务端（vc168，2026-11-18 10:45）— 服务端和客户端分开了
- 用户：服务端用 v2.55，客户端用 v2.66。服务端源码在分支 **server-2.55**（基于 v2.55 commit fa4d7de），改服务端去那个分支改，用 zframe/build_srv.sh 只打服务端，然后把 core/b1.dex/.ver 和 releases/ZFrame-v*-服务端.apk 拷回 main（在线内核从 main 拉）。main 上的源码是客户端 v2.66，不要再用 build3.sh 覆盖 core/b1.*。
- v2.67：服务端自己不能上车（之前能上、下不来）。CanUse 0xdafe1c 里 0xdb00f0 → cave 0x171e7fc，NetMode==2 且 ClosedVehicle 不空（=服务端本机人物）就返回 0；加入的人照旧能上。详见 server-2.55 分支 notes/history.md。未实测。

## v2.68 客户端/单机（vc169，2026-11-18 11:00，main）— 加载页查完更新才放行
- 10:46 用户：检测更新时停在那个页面，别没查完就进大厅。
- 改（GameOverlay 选模式框 + NetCore.done）：框底改成近乎不透明（0xF0000000）盖住后面；以前 20 秒到点就关，现在要等查更新有结果（最新/失败）才关（另外还要满 20 秒没点，或者点过当前模式）；点当前模式但还没查完 → 显示「查完更新自动进入」；查到新版本下载完 → 2 秒后自动重启用新版。腾讯页时已查完的，框一出来就按结果处理。
- 限制：游戏本身的加载没法暂停，后面照样在加载，只是被框盖住。服务端（server-2.55 分支）没有这个框，更新在大厅查。未实测。
- 构建：zframe/build_cli.sh（build3.sh 只打 e=2、0），不动 core/b1。

## v2.69（客户端+单机，探测版）
- 加 UpdProbe：启动时扫游戏自己的更新页对象并自动传日志，看能不能把我们的下载进度喂给游戏更新页。结果看 ntfy 的 updprobe-*.gz。未实测。

## v2.70（客户端+单机，vc171，2026-11-18 11:40）
- 开局没有可见毒圈（开局蓝圈 = 第一项整图半径）；跳伞段 = 航线 18%~92%；拾取动作 1.5 秒内去重（PickAnim，0xd21bfc → 0x171e200）；刷枪配件放脚下让游戏自动装。v2.69 的更新页探测结论：这个包不建更新页实例，不做。详见 history。未实测。
- 0x171e200..0x171e28c 现在被 PickAnim 用了（GM GiveItem 死函数里）。

## 交接（2026-11-18 14:55，v2.91 / vc192 / core 192）
- 三个包都从 main 打：`cd zframe && bash build_all.sh <旧> <新> <旧vc> <新vc>`（编译失败也会打印 built，要看有没有 error）。推 core 后对 b0/b1/b2 的 .ver/.dex 调 purge.jsdelivr.net。
- v2.88：断线/加入失败自动传 net-*，换弹循环自动传 ammo-*（ntfy 主题 zf-qj-k7r2p9m4x1）。不要让用户手点导出诊断。
- v2.89：换弹循环真因 = 加入别人房间时客户端本地 AddAvaItem，服务器没有（ServerAmmo=0）。加入后不在本地刷。
- v2.90/2.91：客户端代刷。加入别人房间（Ed.ED==2 && Lan.role==2）时武器/特殊/子弹/装备/道具页都显示，点刷 → Give.ask 把「编号 数量 x y z」POST 到 ntfy zf-qj-give-k7r2p9m4x1；服务端开房（Lan.host，role=1）后 Give.serve 每 2 秒拉一次，用 Veh.dropAt 刷在客户端脚下。诊断里有「代刷：」行。未实测。
- 待做：断线后重进进不去（等 net-* 日志）；服务端搬云主机（ARM Linux + Redroid 容器跑游戏+服务端 APK，一条命令装、开机自动开房，开 UDP 7777）；地图标点用户叫停；v2.74~2.77 毒圈/飞天等未实测。
- 用户要求：在线内核不自动退回老版本；回复简体中文，没实测写「未实测」。

## v2.92（vc193/core193，2026-11-18 15:2x，三包，未实测）
- 改了：客户端刷车/衣服页、客户端加速高跳（服务端按人改）、全套配件=物品表全部 205 类、油桶键（编号未知，靠认名字）、服务端替加入玩家装弹（扣 +0x60c 备弹）、队友 TeamIndex 修正（PS+0x35c → +0x2b0）、开房「五军对决/BFA（试）」。详见 notes/history.md 末尾。
- 构建环境（沙箱重置后）：JDK21 aarch64 → /workspace/work/rkenv/jdk-21.0.13+9（adoptium api），build-tools r36 → /workspace/work/tools/android-16（dl.google.com build-tools_r36_linux.zip 解出来的目录就叫 android-16），platform-36 → /workspace/work/tools/android-36。pip: capstone unicorn pyelftools numpy。
- 日志：ntfy.sh/zf-qj-k7r2p9m4x1/json?poll=1&since=3h（附件 .gz）。
- 待确认：用户说「取消不刷毒服务端情况下上限人数」，意思没确认（已问）。待做：油桶真实编号；队友若是同一个 TeamInfo 要拆；五军/BFA 若模块名不对日志里会找不到类。

## v2.96（vc197/core197，2026-11-18 16:xx，三包，未实测）— 娱乐模式开房闪退根因
- 根因：v2.92~2.95 用 C++ 底层类 `?game=/Script/NZBattleRoyaleGame.NZBRLegionGameMode` 开房。类名/模块其实对（日志 Game class is 'NZBRLegionGameMode'），但 ANZGameMode 构造(0xc786c8) 只给基础类 ANZGameState/ANZPlayerController/ANZPlayerState/ANZHUD，吃鸡和 Legion/BFA 构造都不改 → 真配置在模式蓝图里，直接开 5 秒闪退（日志停在 SetViewTarget NZPlayerController_0）。
- 修：ModeProbe 扫 GNames 找 Legion/BFA 的 GameMode 蓝图路径再开；找不到不开，传 mode-* 日志。下一步看 mode-* 里「模式蓝图…名字表命中」：命中 /Game/…GameMode 路径 → 看能不能进图；0 命中 → 名字表里没有（资产注册表没载入或包里没蓝图），要换办法查（不解密 pak 的前提下基本只能确认缺资源）。
- 中文名：Legion = 三军对垒（MatchModeName_13，GroupCountMax 默认 3），BFA = 决胜五军（MatchModeName_10，推断）。全表见 history。
- crash 里那份 15:49:47 tomb 是启动读 ini 的另一个崩溃（AppInit → LoadGlobalIniFile），不是开房的；对局内闪退没有 tomb（Bugly native 5 秒后被关）。

## v2.97（vc198/core198，2026-11-18 16:4x，三包，未实测）— 换弹根因 + 联机带宽
- 换弹根因：游戏所有设弹匣处 `GetNetMode()==1(专用服)` 才写同步弹匣 枪+0x604，我们是听服(2) → 永远 0 → 客户端 0xefd460 强制改 0 → 无限换弹。Lan.ammoInstall 启动时 9 处改成「非客户端都写」。换弹 RPC（ServerProceedWeapRepInfo，可靠）一直是通的。去掉 v2.95 手调 0xefd13c。Crew.find 改按 控制器+0x2fc 类名区分加入玩家（NetConnection）和房主（LocalPlayer）。
- 载具：客户端 netspeed 10000 + 听服动态带宽 → 连接饱和，不可靠 RPC（ServerMove、车 ServerUpdateState）被丢、服务端跳过复制。NetRate.java 把两边速率提到 100000。车代码里还有很多 GetNetMode()==1 分支没查（列表在 history v2.97），如果带宽修好后还拉回，下一步查这些。FRP 抖动（Not Recived Data 0.2~7 秒）配置修不了。
- 等用户实测：客户端 "Force set" 是否消失；诊断「听服弹匣同步」「联机带宽」「全员…跳过：」三行。

## v2.98（vc199/core199，2026-11-18 17:xx，三包，未实测）— 道具放下/收回原生化（native-prop 接手）
- 放下：PropBtn/装备页「放下」= 背包 vt+0x484（游戏原生：读条 → FinishUsingItemCastTime → UseThisItem 生成 + 0xcdf058 扣数量）。根因：FinishUsingItemCastTime(0xcde9f4) 对 bIsBot==0 的人物先调 ClientStopPreUsingItem，单机/房主本机在本地执行 → 背包+0x2c0 清 0 → 校验失败。修：0xcdea88 → cave SLACK+0x3d0 调完写回（fin_cave.py / emu_fin.py）。去掉了 force()（直接 SpawnScenePropItem 不扣数量）和「面前 1.5 米」兜底；预览红直接提示不放。
- 收回：单机 0x110d6a0、房主 0x110f334（Lan.role==1），组件=人物+0x1160；去掉 destroyBack。放下成功后扫 GUObjectArray 找 TargetProp 类的新对象记进 placed。
- 诊断：诊断头「道具诊断：」（Prop.diagText，AvatarLog 加了一句）；放下/收回每 0.5 秒记状态，失败/完成自动传 prop-*；进图扫一次 GNames 的 PropsBar 资源。
- SLACK 现在：0x240–0x2df U cave；0x300–0x37f Prop 地上模型 cave；0x380–0x3cb MapMark（从 0x370 挪过来，原来盖了 Prop 的字面量）；0x3d0–0x3fb 放下修复；0x400–0x98f vcave；0x990– 毒圈。没空位了。
- 等用户测：拿出 → 放下（看有没有读条、放下后数量减 1、地上有东西）→ 收回键（读条后回背包）。不行就看 ntfy prop-* 的「道具诊断」段。

## v2.99（vc200/core200，2026-11-18 17:1x，三包，未实测）— 本轮交接（用户 17:07 额度不够，要求收尾）
细节和偏移见 notes/history.md 末尾「v2.99」。日志：服务端 v2.97 game/logs/ntfy/net-1118-165423.txt（16:46~16:54 一局，客户端 127.0.0.1 经 FRP 加入）。
### 用户 16:53 四项
1. **配件不生效 — 已根修（未实测）**。根因：武器 AttachAdapter/DetachAdapter 只有 NetMode==1 才发可靠 RPC ClientAttachAdapter/ClientDetachAdapter，客户端实现要 NetMode==3 才在本地装 → 听服下客户端枪本地从没装配件。改 Lan.adpInstall（8 处，启动时装）。要看：诊断「听服配件同步 8/8 ok」；客户端装配件后后座/扩容/枪上模型是否生效。若仍有一把显示 30：查服务端那把枪 +0x52c。
2. **加速不生效 — 找到根因并修（未实测）**。服务端 Veh.Names 用了假块表（「方式0」，块 1 是乱的），序号 ≥16384 的类名都读成 ?；Crew.find 靠类名认 PlayerController/NetConnection → 一直「找到 0 个（共 2）跳过： ?」→ 客户端的「S15.0_1.0」请求（ntfy 上 16:48:01 确实发出、服务端拉到了）setFor 找不到人 → 不写 PS.Score → 客户端「还没收到服务端的数」。改 Names.check 要求块 1 首条序号=16384。同一原因也让 NetRate「找不到 NetDriver」（v2.97 带宽修复其实没生效）、MapMark 服务端控制器名 ?。速度按人改仍是旧逻辑（CustomTimeDilation + CMC，按 控制器+0x2fc NetConnection 区分），没再改。要看：服务端诊断「全员（PlayerArray）：找到加入的玩家 1 个」「代刷：加速请求：玩家 …」「联机带宽」三行；客户端「跟服务端」行。代刷 ntfy 偶发 FileNotFoundException（since=消息 id），下一步可在出错时把 since 退回时间戳。
3. **快扩刷不出 — 没做完**。没拿到设备上的 zf_items.txt / zf_items_bad.txt；怀疑是 v2.89 前客户端本地刷失败或 Prop.missAt 把它写进了 zf_items_bad（加入房间时客户端本地 give 失败会被记坏）。下一步：诊断里打印 205 类全部编号和 bad 里的 205 编号；加入房间(remote)时不要往 itemBad 写。
4. **信号枪叫飞机 — 只追了一半，没改**。链路：FlareBullet Init(0xee6b9c) 仰角 > 枪+0x1b64(35°) 才置 +0x331；Tick(0xee6dfc) 权威端、下落时最高绝对 Z > 枪+0x1b60(10000) → 人物 vt+0x95c(0x10e8d20) → GameMode vt+0x5d0 的事件管理 +0x23c 广播事件 1。没发现「只有专用服」的分支（开火 0xee597c 只在 NetMode==1 时不生成本地弹）；监听者 AAirSupplySystem（配置 CSV 读失败后的默认值）没追完。下一步：加 flare 诊断（扫虚表 base+0x4c50f5c 的对象，记 Role/+0x331/+0x330/+0x32c/+0x334/位置，自动传 flare-*），实测时朝上 >35° 打；再追 AAirSupplySystem 对事件 1 的处理。
### 用户 17:01 道具三项 — 都没做（时间不够）
- A 捡起功能道具后没有道具键：没查。起点 PropBtn.java 显示条件 + Prop.scanUse（v2.98 改了 place/recycle）。
- B 地上道具模型错：没改。v2.50 Prop 地上模型替身 cave（0xce6068/0xce62a0 → SLACK+0x300）就是显示替身箱子的来源；正确的地上模型资源若只在 pak 里则手机上没有，见下一条。
- C 道具页去掉用不了的：没改（现有逻辑：刷失败/缺资源/402 无场景模型才删，见 GameOverlay.giveItem）。
- D 加速：见上第 2 项（不是只凭 Score=0.0 下结论，证据是服务端「跳过： ?」和名字表方式0）。
### 结论记录
- 三军对垒/决胜五军：用户结论「手机上没有这两个模式的蓝图，不再试」。注意：v2.97 那次服务端名字表是假块表，只扫到块 0，证据偏弱；按用户要求不再试。
- **用户 17:07 要求下一个智能体处理 pak**：三军对垒/决胜五军模式蓝图、训练营主关卡、道具地上模型，卡点都是资源只在 pak 里（手机现有名字表/挂载里找不到）。这里只记录要求和卡点。
### 需要用户的日志
- 加入房间后：装配件、开加速（拉条停 1 秒）、朝天 >35° 打信号枪各一次，然后让服务端和客户端都自动传 net-/matchend-（不用手点）。

## v3.00（vc201/core201，2026-11-18 17:2x，三包，未实测）
细节/偏移见 notes/history.md 末尾「v3.00」。接手时 ntfy 最新只到 17:02（v2.98 服务端），v2.99 实测日志还没来 → 配件同步、名字表（加速/带宽）仍未实测。
- 快扩：根因是误删（「补丁没反应」和「1.2 秒内任何缺资源」都会永久写进 zf_items_bad.txt）。改成只在游戏明确失败时删、缺资源只对 402；第一次运行清掉非 402 的旧记录。看诊断「配件编号（205）」「坏编号」两行。
- 信号枪：Flare.java 自动记信号弹和空投系统，传 flare-*。RE 结论：空投系统只在 CurFlareUsedNum < LimitsNumAdd×(圈序号+1) 时叫飞机，最可疑是 LimitsNumAdd=0。下一步看 flare-* 再决定改哪。
- 道具键：客户端也有了（原生入口），收回客户端走 0x110d6a0。地上模型替身（拿第一个加载成功的模型顶）是模型不对的原因，还没改。
- 模式蓝图：服务端进大厅 20 秒后自动全量扫名字表（多关键字含 Camp），传 mode-*，不开房。
- 要用户：服务端+客户端都装 v3.00（或重开等在线内核 201），加入后：装配件、开加速、朝天 >35° 打信号枪、捡道具看有没有键；全部自动上传，不用手点。

## v3.01（vc202/core202，未实测）
- v3.00 日志结论：三军对垒手机上没蓝图（按钮已去掉）；决胜五军蓝图在、能进图，但 listen 开房没人进五军分组 → 没流程（下一步 RE NZBRBFADsLogic/分组入口）；信号弹诊断上一版误触发（贴图日志），已修，这轮用户没开枪。
- 新：「战术模式（试）」开房，用游戏自己的战术模式蓝图和原生道具栏；客户端自制道具键撤回，战术模式里房主也不显示自制键。
- 等：战术模式开房结果（mode-*/avatar-*）、真开信号枪的 flare-*、快扩反馈。

## v3.02（vc203/core203，未实测）
- 刷枪配件按枪给，弹匣只给快扩（GameOverlay.attFor；205 编号表见 history v3.02，按枚举顺序推断）。等用户确认捡到的是不是快扩、各枪配件对不对。

## v3.03（vc204/core204，未实测）
- 新：新海岛=战术模式；扫到的其他大地图按钮；Jetpack.java；HostMatch.java（开局发 402×999、藏房主人物）；Give 合并请求+长连接订阅；重启延时 3.5 秒；去飞天、油桶键。
- 待办：换弹切枪自动装弹（RE/日志）；方块（等用户说明）；pak 资源检查；修训练营；地上模型替身。

## v3.04（vc205/core205）
- 不刷毒对局的选择存 prefs（zone_off），大厅加载完自动重打补丁；avatar 日志加「游戏模式」行。原因：v3.03 用户选完不刷毒后重开更新内核，状态丢了，战术模式开局全员上飞机。未实测。

## v3.05（vc206/core206）未实测
- 单排（GM+0x8ed MaxPlayerCountPerTeam=1、+0x998 清 0）；房主人物 隐藏+关碰撞+冻结+满血；出生岛 9 堆空投（Box.java）；ucave 5 参数（Veh.ucall5）；heal-* 自动日志；客户端只留 功能/刷车/联机/画面；菜单分组。详见 history v3.05。
- 下一步：看 box-* / avatar 日志里「空投」「单排」两行；打药看 heal-*；喷气背包看「喷气背包 原值」。

## v3.06（vc207）未实测
- 修 U cave 空位检查；0x100fc60 不结算补丁（HostMatch.noEnd）。看 avatar「单排」「空投」两行。

## v3.07（vc208）未实测
- 真空投箱：Box.airdrop + boxcave（0xfd3424 钩子，cave 在 0x11de78c）+ ucall6。看 avatar「空投箱」行。

## v3.08（vc209）未实测
- Box 堆放在加入玩家身边（joinedPos）；airdrop() 不再调用。刷车：客户端请求没到 give 主题，待客户端提示。

## v3.09（vc210）未实测
- 新海岛战术模式蓝图路径写死在 ModeProbe.cache，直接开。

## v3.10（vc211）未实测
- 见 history v3.10。待：换弹打断 RE、训练营/单挑（问用户能否接受现有地图上的模式）、打药 heal 日志。

## v3.11（vc212）未实测
- 训练营：看 pak-* 日志里「训练营 pak」行（文件位置、索引是否明文、umap 列表）；看 avatar「训练营：靶子」。

## v3.12（vc213）未实测
- 在线更新：看「在线更新失败：…包不对」行。chain 修复需重装 APK。

## v3.13（vc214）未实测
- 客户端包 ED=0。看大厅是否有衣服、加入是否正常。

## v3.14（vc215）未实测
- 看 upd-* 日志「游戏更新页名字表命中」。

## v3.15（vc216/core216，2026-11-19 01:3x，三包，未实测）
- 删了训练营、决胜五军开房按钮；修断线后重进被拒（PreLogin 0x1009ab0 补丁 Lan.RELOG）。详见 history。
- 等用户测：客户端退回大厅后再「加入艾挽服务器」能不能进；服务端开房页确认没有训练营/五军。
- 用户只说「修一些 bug」没具体说哪些；ntfy 日志里 v3.14 能看出的只有断线重进。其它未确认的老问题见上面各版本「待做」。

## v3.16（vc217/core217，未实测）— 两人单挑（Duel.java），详见 history
- 等用户测：开「单挑·海岛/雨林」→ 两个客户端加入 → 面前有没有一排枪、捡了以后有没有发装备 → 打死一个 5 秒后能不能复活。看服务端日志「单挑：」和开房页状态行（复活：调 RestartPlayer 标记…）。
- 复活不行的下一步：查 NZ RestartPlayer 0x10127a4 的拒绝分支，或改用 BTT 模式蓝图（有原生复活）。

## v3.17（vc218，未实测）：单挑改成自定点（prefs duel_e/duel_s）+ 看不见的固定圈（圈外掉血）。见 history。

## v3.18（vc219，未实测）：单挑加车辆掩体、认手上的枪、开局拉回。

## v3.19（vc220/core220，三包，未实测）：单挑真墙围场 + 原生出生保护 + 等两人到齐。见 history v3.19（含自查清单）。
- 删车掩体；Walls.java 用地图已加载的墙/集装箱模型刷 AStaticMeshActor（复制），外圈无缝围墙 + 场内 15 块变化布局，墙顶 ≥ 地面 3.3 米、埋地 1.2 米；客户端 SetStaticMesh 补丁 0x29575fc（emu 过）一进图就打。
- 无敌：原生 SpawnProtection（GS+0x3c8=3、人物+0x7e0=1、+0x114=世界时间）+ 闪烁（SetActorHiddenInGame）+ 补血后备。
- 两人都到才开始，掉线暂停、重连接手。
- 要看的日志：duel-*（墙/模型/保护）、客户端「收到房主复制的墙 N 块，带模型 M 块」。墙看不到/没模型 → 先看客户端补丁行和 M。

## v3.20（vc221/core221，三包，未实测）：单挑同一张图再开一把。见 history v3.20。
- 20 头结束 → 房主 toast + 日志（不调游戏结束）→ 10 秒后两人都在就自动重开，不全在就清场等人、到齐自动开始。服务端对局常用页「单挑」新按钮「再开一把」随时重开。
- 重开 = 等围墙线程 → 丢子弹/投掷物 → 删墙（K2_DestroyActor vt+0x164）→ 删场地里地上拾取物/死亡盒 → 比分/计时清零 → 活的传回+补血+出生保护、死的立刻复活 → 重新摆枪选枪 45 秒、重刷墙。枪/头甲不删（丢枪闪退史），选枪只认新捡的枪。
- 单挑开着时强制 0x100fc60 不结算补丁。
- 要看的日志：「单挑：再开一把 第 n 把…」整行（删墙数、清地上东西类名表、虚表+0x164 目标、用时），客户端「收到房主复制的墙 N 块」是否先减少再恢复。

## v3.21（vc222/core222，三包，未实测）：单挑自动选场地 + 不刷毒修飞机 + 单机版菜单单机/联机 + 删物资/门/出生岛空投。见 history v3.21。
- 用户规则：不要让用户记录任何地点；服务端只开房什么都不做。
- 单挑：没有记点按钮了；DuelArena.choose（地形块候选 + Trace 射线 LineTraceSingle/ProcessEvent vt+0x100 找平地、不在非地形/水里）→ 两人都站稳后一起传过去 → 原流程。
- 要看的日志：「单挑场地：自动选 …」整行（候选数、拒绝原因、射线次数）、「地面射线：函数 …」、「单挑围墙：…射线贴地 a/b」、「单挑：玩家 n 传到自动选的单挑点」。射线不行 → 看「地面射线：ProcessEvent 没反应 / 没找到…」。场地落在房子里 → 房主没加载房子碰撞（见 history 限制），下一步临时加载 ServerOnly WorldMeshes。
- 不刷毒对局：看「游戏模式：进图按存的模式…」「阶段守护…」两行。

## 交接（2026-11-19 12:53，v3.21 / core222）
- 最新：v3.21 已推 main，jsDelivr 已刷新（b0-b2.ver=222），手机重启游戏自动更新；三包在 releases/ZFrame-v3.21-{单机,客户端,服务端}.apk。全部未实测。
- 用户规则：回简体中文；未实测要标明；不要让用户记录任何地点，服务端只开房什么都不做；单挑掩体只要真实建筑墙（不要车、不要防弹墙），比人明显高、不能跳枪、外圈围墙；两人都进来才开始；做事考虑周全、打磨好再交付；单机/联机选择放大厅菜单里，不要弹窗；不解密 pak、不做线上外挂。
- 待测：v3.21 单挑自动选场地（DuelArena/Trace 射线）、围墙（Walls）、原生出生保护+闪烁、等两人、再开一把（v3.20）、不刷毒不上飞机、大厅 单机|联机 切换和标题。
- 已知风险：房主不加载房子碰撞层，选场地可能挨着镇子；上一把的枪和头甲收不回（丢枪会崩）；客户端看不到等待提示和结算。
- 刚问用户：要不要加只给测试用的开关，让一个人也能进场地看围墙（用户只有一个客户端）。未回复。
- 以前没做完的：空投空、地图物资刷新（物资点在不加载的图层，目前没有能用的刷法）、单机门、双排/四排带人机队友、军需抽奖、电竞按钮、45 秒倒计时缩短、柜子里能捡、出生衣服开关、v3.15 重新登录修复未测。
- 日志：ntfy.sh/zf-qj-k7r2p9m4x1；构建：cd zframe && bash build_all.sh <旧> <新> <旧vc> <新vc>（JDK 路径不对时临时 sed 成 /workspace/work/rkenv/jdk-21.0.12.1+1 再改回）。

## v3.42（vc243/core243，2026-11-19 20:3x，只推内核，未实测）
- 详见 history v3.42。训练场：Island 按 Login 原步骤补位置 → 原生 AddPlayerToSlot；LoadBlock(位置块号, 16)；服务器层 FillLayersWithCacheData(+0x37c)；传射击训练区；slot-* 自动诊断。
- v3.41「第 9 块」是假象（块 1..15 全 Out Of Index）；「第 9 块对应哪个 Heightmap」这个问题不成立：块号是整座岛的第几份副本，不是 Heightmap 瓦片。
- 待用户测：slot-* 四行；para-*（拿雷点「手雷抛物线检查」）。下一步按诊断：若 +a21 不来 → 查 Landscape 层完成条件；若车/物资仍 0 → 查 BP_VehiclePracticeBase / PickUpRefresh 触发（BP 在 game/pak）。
- 日志：ntfy zf-qj-k7r2p9m4x1。构建：build_all.sh 一次跑三版可能超 120 秒，可按 history 拆成单版跑（/tmp/b1.sh 同逻辑）。

## v3.43（vc244/core244，2026-11-19 20:5x，只推内核，未实测）
- 详见 history v3.43。训练场根因：单机不跑专用服那句「管理器块数=9」→ 出生点按 4×4、岛副本按 3×3 → 人在岛西南海上。补丁 0x10828b0 + 原生 AddPlayerToSlot 摆人（不排队等 +a70），地形到前冻住；原生不行才退回传送。训练岛补发仓库衣服（以前 Bots.matchLoaded 在训练岛不调）。
- pak 只读解析的训练岛坐标（原始、块偏移前）：出生模板 (53920,-48810,-15900)，shootreturn (47835,-48630,-15850)，drivereturn (57545,-39960)，AIreturn (49835,-59570)；解析脚本思路：uasset 名字/导入/导出表 + 标签属性（FV 当 508，未版本化包）。
- 待用户测：进训练岛是不是直接在射击区旁边、地形正常；看 slot-* 的「出生点建立」「分配位置」「坐标/地形块随时间」「训练岛关键日志」「仓库衣服」。若原生摆人失败 → 看「退回传送」后的原因，查 vt+0x64c；若 Landscape 仍不完成 → 查 0x1082670 层名判断/流式条件。

- 20:58 发布核对：raw.githubusercontent / ghfast / gh-proxy 三个镜像 b0-b2.ver 都是 244（客户端取各镜像最大值，能更新）。jsDelivr @main purge 已发，但 cdn 对 b0/b2 回「Package size exceeded 50 MB」（仓库加了 game/pak 和历次 APK 后超限），b1 还是旧缓存 243——jsDelivr 这条路以后不可靠，不影响更新。

## v3.44（vc245/core245，2026-11-19 21:3x，只推内核，未实测）
- 详见 history v3.44。训练岛块 0 不流式加载：删掉 v3.42 起多加的服务器层 Fill(+0x37c)（客户端原生不做；Main.umap ServerLayersSettings 里有 Landscape/AlwaysLoad，推断会把块 0 的地形实例重复 Tile 一遍）；原生顺序 补位置→AddPlayerToSlot→LoadBlock(PC+bdc,PC+be0)；等地形时只停重力（GravityScale=0+速度清零），不改时间缩放；新诊断「流式状态：」。修「训练岛：没进」显示。
- 待用户测：进训练岛脚下地形是否出来、人是否站在射击区旁；看 slot-*/train-* 的「分配位置」（LoadBlock 前后 +440/+43c、不填服务器层）、「坐标/地形块随时间」（每段 [层更新计数和 包住人物关卡 流对象 载/显]）、「流式状态」（两份层缓存名、每层更新计数/包住人物、+358 重复指针/已加载、Heightmap 实例 载/显、world StreamingLevels）、关键日志 OnLevelStreamingLayerLoadCompleted / OnAllDuplicateLevelLoaded / FillLayersWithCacheData / TileLayer。
- 仍不加载时按 history v3.44 末尾三步分支查。
- 仓库瘦身（用户要求）：pak 资源在私有仓库 zhaotan005-dotcom/quanjunchuji-pak（本地 /workspace/work/quanjunchuji-pak），以后不往主仓库放。game/pak 已 git rm；releases/ 只留 v3.44 三个（rel.py 下一版用 releases/ZFrame-v3.44-单机.apk 作基底）；game/x、game/re/*.pkl、apk/、zframe/ZFrame-v1.*.apk、zframe/build/ 停止跟踪（本地文件还在，.gitignore）。main 当前文件约 22MB（<50MB），jsDelivr 应恢复。以后每版发完要 git rm 上一版三个 APK。
- README 里那行「已接受解密 pak/绕保护/联网外挂」已按用户确认删除（2026-11-19）。规矩不变：不解密 pak、不绕保护、不做联网外挂。
- 21:3x 发布核对：raw.githubusercontent / ghfast / gh-proxy 三个 b0-b2.ver 都是 245。jsDelivr 包大小问题已解决：钉提交 `@585c115` 的 b0-b2.ver=245、.dex 和本地 sha1 一致；无分支 `/gh/…/core/*`（默认分支）purge 成功，返回 245。但 `@main` 的 purge 一直 throttled（20:58 刚 purge 过），各节点缓存不一（cdn 245/243/243，testingcf/gcore/fastly 还有 221/243）——要等节点缓存过期或晚点再 purge @main；客户端取各镜像最大值，raw/ghfast/gh-proxy 已是 245，不影响更新。

## v3.45（vc246/core246，2026-11-19 23:5x，只推内核，未实测）
- 详见 history v3.45。根因（静态读码证实，设备未实测）：管理器 +0x400 bFirstUpdate=1 时客户端 Tick 0x115e7dc 不调 Update；只有 FirstLoad（vt+0x2f8，TileMode 0x1165e34）会清它；原生 Client Load Block 0x1086960 = LoadBlock + FirstLoad，v3.42~v3.44 只调了 LoadBlock。v3.44「服务器层重复填充」推断作废。资源不缺（训练岛 330 个文件同在 pakchunk_c2afa52b，资源清单 ×1 是 pak 挂载点计数）。
- 改：Island.join 调原生 Client Load Block(PC)；+0x400 仍 1 才读 PC vt+0x8bc 并直接调同一个 FirstLoad。诊断：层关卡数偏移改正（层 +0x10 是管理器弱指针，不是更新计数），+400 进时间线。
- 待用户测：进训练岛脚下地形是否出来。看「分配位置」里 +400 是否变 0、游戏日志「FirstLoad called」、「坐标/地形块随时间」地形块/流对象载是否涨、「流式状态」world StreamingLevels。
- device-tests：v3.43 两份（train-1119-210620、slot-1119-210730）从 ntfy 补下载进仓库；v3.41 附件 ntfy 已过期。
- 23:2x 发布核对：jsDelivr @main 与 @e9cda24 的 b0-b2.ver=246，b0.dex sha1 与本地一致；raw.githubusercontent b0/b2.ver=246。purge @main 六个文件 finished。

## v3.46（vc247/core247，2026-11-20 00:xx，只推内核，未实测）
- 详见 history v3.46。同帧原生摆人 cave（0x171e2c0，入口 Login beq + Tick 0x1082e84）；标点按目标地面高度；训练营只留加速/高跳/标点传送；删动作放慢；Island 删退回传送/抬到地面/停重力；拿雷自动 para- 光束诊断。
- 要看：avatar/slot 的「同帧摆人：… 阶段 4/5」和「分配位置」是否「在出生点」；进岛不再停在虚空。para- 里「光束：段数、段模板、段组件…」单机和联机各一份对比。
- 下一步（还没查）：训练岛不刷枪（ds not prepared、地上物资 0）；实战区不自动进流程、没人机。
- RE 环境：game/re/*.pkl 从 /workspace/work/zf/game/re 拷；libUE4 在 /workspace/work/qjcj/x；dd.py 要 /workspace/work/qjcj-gh → qj2 软链。

- v3.47（core248，15e8778 后，未实测）：训练营菜单去空白；训练岛服务器层进 CurrentLayers（刷枪点，0x115c744 cave）；FindSlot 从位置 4 开始（小地图）；单机刷车贴地+确认+「没刷出来」；皮卡/大脚车拆开；刷车满油（AddFuel）；海岛服务端真空投箱（StaticLoadClass）。雷未动。详见 notes/history.md。
- v3.48（core249，未实测）：训练岛再加 ServerOnly 本层（v3.47 只进了 Majority/GroundObjects）；大厅发色回包带 hair_color。
- v3.49（core250，未实测）：训练桌自己摆枪（pak 没有 ServerOnly 关卡）；发色走 hair_prop.role_attr.color(6)。
- v3.50（core251，未实测）：服务端「进训练营」；训练桌 15 秒检查原生物资，不够才自己摆。
- v3.51（core252，未实测）：训练岛所有人进位置 4（同一个训练场）；加速动作放慢加回（0.75/倍数）。
- v3.52 (core253): 训练岛补建 AlwaysLoad 常驻层（LogicLevel 刷枪/靶子），未实测。详见 history v3.52。
- v3.53 (core254, 未实测): 服务端物资点放开 PK 0x10c0ba4 + 训练岛「物资链」诊断。v3.52 常驻层生效（靶子/刷车出来），枪仍无。

## v3.62（core263，2026-11-20 23:3x，全部未实测）训练岛防摔 + 枪桌摆枪。详见 history v3.62。
- 防摔：NoFall.java，单机/服务端每 2 秒把训练岛人物 FallVelocityThreshold_High/VeryHigh/Kill（+0xa78/+0xa7c/+0xa80）写 1e9（摔伤 0xd149a4 走到「0 伤害」分支）。看诊断「训练岛防摔：」。
- 枪桌：用户 23:24 放开「不许自己摆」。射击区射线找非地形桌面，每桌最多 4 把枪 + 子弹，15 秒检查补货。看「训练岛枪桌：N 桌 M 把」「训练岛补枪：」。没找到桌子会摆地上一排；补货认不出自己摆的东西会停。
- 下一步若用户仍摔死：看「防摔」行人物数/原值；可能是车上摔（0x10e8f00 车上返回 0，另有 CalcuFallFromVehicleDamage 没查）。

## v3.63（core264，2026-11-21 13:xx，全部未实测）标点传送立刻落地 + 枪桌带配件。详见 history v3.63。
- 标点：Trace.stand 找能站的面（树/草/体积/陡面跳过，最多 6 层）→ 传到 落点 +95 厘米、清速度，不慢落；碰撞没加载 → Fly.hold 原地定住（重力 0 速度 0），每 0.1 秒再打，地面出来再放（最多 20 秒）。所有地图，单机/房主。看「自动传送到标点 … 用时 … 射线：…」。
- 枪桌：TableGuns.java。先按模型名含 Table 的 StaticMeshComponent（Range_Table）+ 世界变换 + 模型边界找桌子，射线兜底（625 条粗格 + 找边）；每把枪一组：枪 + 2 份子弹 + 配件；每 5 秒按件认，拿走的约 10 秒内补那一件。配件/枪/子弹编号按 pak-files.tsv 拾取物蓝图名证实（attFor/ATT/Box 已改正）。
- 待用户测：标点（尤其山顶→山谷、标在树下/房子上）；枪桌摆在哪、是否整齐、配件能否捡起装上、拿走后是否补。若「模型找桌子」读不对 → 看诊断里四元数长/缩放/边界，修 +0x130/+0x150 偏移；若补货「认不出」→ 看平均最近距离（拾取物落点和给的点不同）。
- pak 文件表：私有仓库 zhaotan005-dotcom/quanjunchuji-pak 的 pak-files.tsv（blobless 克隆后 git checkout HEAD -- pak-files.tsv）。

## v3.64（core265，2026-11-21 13:4x，只推内核，全部未实测）
- 用户 13:11：v3.63 进训练场几秒闪退（单机不传日志，没有闪退记录）；大厅菜单标题只要「全军出击」、去掉单机（游戏自己的开始游戏就是单机），只留联机；开小窗再回全屏菜单位置歪；大厅手持枪展示点了没效果；装降落伞的「装备成功」字样和预览里冒出人物；军需点不进去。
- 改：①TableGuns 进岛先等 12 秒、每件隔 150ms、最多 12 把（v3.63 是几秒内 25ms 一件连刷上百件，疑似闪退原因，未证实），步骤写 Server.mark（「训练岛枪桌：开始找桌子/开始摆/摆完」，下次 crash- 里「上次运行最后几步」能看到）。②单机也自动上传闪退诊断 crash-（只上传）。③大厅标题「全军出击」，大厅菜单只剩「联机」加入卡片（netTab 不再用；正常/不刷毒、自动刷人机沿用上次存的）。④菜单位置：屏幕大小变了调 repos：大厅固定中上；对局回到用户拖的位置（ux/uy），小窗里只临时夹进屏幕。⑤降落伞（405xxxxxxxx）穿/脱不再 followPreview/pushStore（推测预览里的人物来自重推仓库+重穿）。「装备成功」字样是游戏自己的提示，没找到来源，没改。⑥大厅手持枪展示、军需：没有日志看不出游戏发了什么 → Server 收到没见过的请求、31xxx/38xxx/51xxx、或 13003/13005 非衣服类编号时，4 秒后自动传 lobbyop- 日志（45 秒一次，单机也传）。下一步按 lobbyop- 里的请求号和包体补回包。
- 构建：只出内核（/tmp/core.sh 3.64 265：javac+d8 三个 BUILD 写 core/b0-2.dex/.ver），没出 APK；releases 仍是 v3.63。Ed.java BUILD 在仓库里是 1，脚本结束会还原。

## v3.65（core266，2026-11-21 15:xx，只推内核，全部未实测）
- 用户 14:35：大厅标题「全军出击 艾挽」；训练场退出要先弹游戏自己的「确定要退出演习？」再退；训练场枪位置不对、没有手雷和配件专区；「刷枪就是刷枪」；配件按枪种分区、倍镜单开；手雷抛物线；训练场把刷枪加回来。
- 改：①标题 titleText 大厅 =「全军出击 艾挽」。②训练岛退出：Con 只认确认后的 QueryQuitMatch（v3.64 认的是点按钮那行 Btn_System_QuitMatch Btn Click，确认框刚弹就回大厅了）；Server 15003/15005 不变。
  ③抛物线：根因 0xefae5c（每帧取瞄准方向）武器 Role(+0xe8)=3（单机/房主）时读武器 +0x1b48（客户端上报方向，单机没人写）→ 定在拿雷那一刻；补丁 0xefaea8 bhi(3900008a)→nop(00f020e3)，主机也走控制器视角 0x2c551a4。Parabola.aimFix 每次进图打（原字节核对），诊断行「抛物线跟视角…」。
  ④大厅手持枪展示：13022 = CSDepositReportShowInfoReq{DepositShowInfo show_info=1}，回 13023 {result=1, show_info=2}；存 filesDir/zf_show.bin，仓库 13002 回包带 show_info(8)。协议来自 libUE4 内嵌 cs_deposit.proto（/tmp/fdx.py 思路：找 "\n\x10cs_deposit.proto" 按 wire 解 FileDescriptorProto）。
  ⑤菜单：「武器」改「刷枪」（只给枪+子弹，不再带配件）；新「配件」页（页号 12，ATT_CATS：步枪/冲锋枪/狙击枪/连狙/霰弹枪/手枪/十字弩/倍镜，点哪件刷哪件）；客户端包（srvT）也加刷枪/配件/特殊/子弹；训练岛分页 TRAIN_TABS = 功能/刷枪/配件/特殊/子弹/装备。
  ⑥枪桌分区：TableGuns.ZONES（步枪/冲锋/狙击/连狙/投掷武器/倍镜/步枪配件/冲锋配件/狙击配件/霰弹/手枪/机枪/十字弩），每区一张桌（不够占最近空桌），最多 160 件、120ms 一件。找地上分类标牌（模型名含 indicator，pak Props_Trainingisland/indicator_01..28），标牌 3 米内的桌子优先，同编号同分区；编号→分区对照 SIGN_ZONE 还是空的（标牌字在贴图 indicator_01_D 里，没有文件），先按编号顺序排。诊断「训练岛标牌：[编号 坐标 离桌 → 分区]」。下一步：拿到 indicator 贴图/模型 UV（或用户截图）填 SIGN_ZONE。等地面从 20 秒减到 6 秒。
  ⑦军需：点的时候游戏没发任何请求（v3.64 lobbyop 里没有 51xxx/31xxx/38xxx）→ 大厅里建界面（CreateUI 非 StoreUI/失败）或 Lua 报错时自动传 lobbyop-（进大厅 20 秒后）。
- 降落伞「装备成功」：libUE4 里没有这串字（Lua/蓝图里），没改。
- 构建：/tmp/core.sh <ver> <corever>（本沙盒 JDK/SDK 在 /workspace/w/tools），会改 Ed.BUILD，结束还原（失败中断时手动改回 1）。

## v3.66（core267，2026-11-21 15:0x，只推内核，未实测）
- 用户 14:52：v3.65 大厅手持枪展示能点了。雨林也要战术模式（能用战术道具），单机和服务端都要。
- 改：Con.startGame 编号 4 → MAP_SAVAGE + ?game=BP_NZBattleRoyalePropsGameMode；服务端开房「雨林」走 ModeProbe.host("Props")。
- 用户说点「军需」进的是游戏自带「房间」页（单人模式/经典模式下拉、开始游戏卡在「正在进入战局…」）；用户：开房以后再修，修好拿它组队（待办，没动）。
- 更正：上条「军需进的是房间页」是我理解错。14:56 用户问军需：lobbyop-1121-145058 只到 14:50:47，没有点军需的记录，也没有 51xxx 请求。已问用户点军需后的现象并要截图和导出诊断。候选蓝图 CommodityUI / StoreUI。
- v3.67（core268）：用户说点军需显示「加载商城」然后就没了。大厅菜单最下面加「上传日志」（lobbyop-），UE 日志出现 Mall/Midas/Pandora/Commodity/Lottery/Treasure 也会自动传。

## v3.68（core269，2026-11-21 17:4x，未实测）
- 训练桌分区按用户 17:2x 截图核对标牌：10 射手步枪→连狙，11 冲锋枪，12 推断突击步枪→步枪，13 投掷武器，14 弩→十字弩，15 轻机枪→机枪，16 霰弹枪，17 近战武器→放倍镜（没有近战物品），18 狙击步枪，19 手枪（TableGuns.SIGN_ZONE）。三个配件区（步枪/冲锋/狙击配件）放在对应枪区旁最近的空桌（TableGuns.NEAR）。12 没截图，待确认。
- 训练场锁血 MinHp.java：0xcc0e10 mov r0,#0 → b 0x171e1c8；cave A 0x171e1c8..0x1ff（GM GiveItem 死函数头）+ B 0x171e28c..0x2af；受伤者==自己人物时伤害夹到 血−1（最低 0），计次在缓冲区+8。unicorn 测过。诊断行「训练场锁血：」。原因：用户训练场开车撞死后闪退（剩 20 分钟时）。
- 军需：39001 = CS_GET_ACHIEVEMENT_REQ（成就，不是军需）；27005 = 房间列表。军需是 Pandora 网页商城（libUE4 NZPandoraSupport "type":"MallReady"），要从腾讯服务器下 Lua 页面 → 「加载商城」后关闭。修法：把军需按钮改开游戏自带商城 NZShoppingMall（cs_mall CS_MALL_GET_MALL_REQ 18001/18002 CSMallGetRes），还没做。
- 协议：game/re/protoext.py+protodump.py 重新导出 58 个 .proto 到 game/proto/（cmd.proto 是消息号表，cs_roomsvr/cs_roomcenter 是房间）。
- 用户要：游戏自带「创建房间」能用，所有人能加入，云手机挂服务端。计划：服务端包（云手机）开房 → 用 cs_roomsvr 回 26001/26005/26017，27006 房间列表填云手机房间，开始后 26031 CSRoomGetDsInfoNtf 给 frp-put.com:47937 让游戏原生连。下一版做。
- 用户说：扔闪光弹/烟雾弹显示手雷的图标；捏雷时不能蹲起。没查。

## v3.69（core270，2026-11-21 18:5x，只推内核，未实测）
- 训练场不掉血的原因：MinHp cave 把 Health(+0x5a0) 当 float 读，实际是 int（TakeDamage 0xbe0e48 ldr/sub/str）→ 每次伤害都被夹成 0。改成 ldr r0,[r8,#0x5a0]; sub r0,#1; vmov s2; vcvt.f32.s32 → 伤害 > 血−1 才夹。unicorn 测：100 血 30 伤 → 30；150 伤 → 99；1 血 → 0。防摔（NoFall）没动。锁血计次变化就写日志。
- 捏雷不能蹲起：姿态检查 0xd7adcc（返回 EMovementFailureReason）在 0xd7af08 bl 0xd1c060 读人物 +0x810 bIsInKeepPreFireState → 10 IsInKeepPreFireState。补丁 0xd7af08 5484feeb → 0000a0e3（Parabola.crouchFix，每局 matchStart 装，所有端）。诊断「捏雷蹲起」。
- 军需：MallMainView = UMallUIView : UBasePandoraMall，内容是 Pandora 网页（等 MallReady），pak 里没有原生 ShoppingMall_C（上次说「改开本地商城」是错的）。现在 UE 日志出现 WaitingMallReady / Pandora is Ready Mall 时弹提示说明。
- 闪光/烟雾图标：武器是对的（BP_NZWeap_StunGrenade_C），图标是 HUD 蓝图 UpdateThrowWeaponTexture 选的，没改。另：震爆弹投掷模式 [武器+3e8]+24+20 = 2，ThrowWeaponTrace=0 时要 5/6 → 震爆弹不画抛物线。
- 房间 Room.java：Server.handle 先给 Room（26xxx/27xxx、房主在房间里的 15001）。建房 26001→26002、改房 26025、信息 26017、加入 26005/26033、退出 26007、开始 26011/15001、列表 27005→27006、搜房号 27001。房间表走 ntfy zf-qj-room-k7r2p9m4x1（R/J/Q 行，见类注释），房主每 30 秒发一次。开始：房主本机 Lan.host / ModeProbe.host（地图 1 老海岛、3 新海岛战术、4 雨林战术），进图后发 P，加入的人 Lan.join(地址)。地址：服务端包（云手机 Ed.ED==1）= frp-put.com:47937，其它 = 局域网 IP。

- v3.70（core271）：联机页地址框旁加「设为我的开房地址」（prefs room_addr），Room.myAddr 优先用它（用户自己的内网穿透，需转发 UDP 7777）。

- v3.71（core272）：开房地址没设过默认 frp-put.com:47937（所有包）；输入框加 IME_FLAG_NO_EXTRACT_UI/NO_FULLSCREEN，横屏不进全屏输入页。

- v3.72（core273）：服务端/客户端包进大厅也自动开自建服务端（之前只有单机），否则游戏「创建房间」的 26001 没人回，点了没反应（日志：服务端包 自建服务端：没开，ConnectorService::SendRawBuffer connector is invalidate）。

- v3.73（core274）：右边输入框提示「填穿透地址」，不预填（只显示已设的开房地址）；开房地址没设时仍默认 frp-put.com:47937。

- v3.74（core275）：服务端大厅菜单改成客户端样式（标题「全军出击 艾挽 服务端」、更新行、「开房」卡片）；删单挑两个按钮、去掉房主人物开关（hideHost 默认 false）、本机 IP、调试说明、扫到的额外地图；进训练营改名「联机训练营」。

- v3.75（core276）：TiMi 页超时 4.5→3.95 秒（cave 0x171e6d4 movw/movt），去掉结尾发白帧和 0.5 秒停顿。

- v3.76（core277）：服务端/客户端包启动也预装完整服务端钩子（之前只装 TICK，大厅再装时 TICK 字节已变 →「游戏代码和预期不一样」没开，创建房间 26001 无回包）；联机包开服务端不发仓库/礼物。删跨进程：内核里不再从 com.zframe.mods 读插件；ZInit.onCreate 源码去掉读 ZFrame App 内核（要新 APK 才生效）。

- v3.77（core278/vc278）：LobbyLoad 进大厅加载页（Browse 大厅→盖层进度条，人物装好 HideLoadingProgress/OnChangeHairColor 后 2.5 秒到 100%，最长 15 秒）。打了三包 releases/ZFrame-v3.77-*.apk（ZInit.onCreate 不再读 ZFrame App 内核，需用新包重打补丁才进游戏包）。

- 2026-11-21 用户不要框架 App：原版游戏 APK 在 /workspace/w/game_apk/game.apk；沙盒打游戏包用 /workspace/w/hostpatch/Host.java（patchBase+patchSound+gkey 签名，参数：game.apk core/b<ED>.dex 输出）。v3.77 客户端(b2)/服务端(b1)游戏包已发。

- v3.78（core279，2026-11-21 19:4x，只推内核，未实测）：服务端监听端口跟着开房地址的端口走（Lan.setListenPort，开房前改 UrlConfig.DefaultPort = libUE4+0x5374904；RE：FUrlConfig::Init 0x1a34ad0，UrlConfig 0x53748c8，+0x3c Port）。开房地址 frp-put.com:47937 → 本机听 UDP 47937；没写端口的地址仍 7777。用户 frpc 的 local_port 要从 7777 改成 47937。诊断行「服务端监听端口」。
- v3.79（core280）：撤回 v3.78。用户 Sakura FRP 隧道 aiwan 本地 127.0.0.1:7777、远程 frp-put.com:47937 固定不能改 → 服务端一直听默认 7777（setListenPort 留着没调用）。UE4 不要求穿透两端端口一致。

## v4.06（core307，2026-11-22 19:0x，全部未实测）详见 history v4.06
- 开机不再被在线更新卡住：NetCore 20 秒（下载中 40 秒）/第一次失败就放行，后台更新；Con hold 最多 60 秒；不靠游戏日志也能起更新页/进大厅。
- BootLog：启动 6 秒「启动」消息（标题带机型），40/120 秒没进大厅传 boot-*。
- 服务端：去掉 UpdateUI 类 RootSet + GameInstance 预扫、UpdUi 名字表扫描；贴图池 128 + MipBias 1；MemTrim 超 850MB 用 madvise PAGEOUT 换出冷页；Beat 记 RssAnon/File 和 smaps 前 15。
- jsDelivr @main 的 .ver 卡 297/298、.dex 是新的 → 旧内核（<299）只通 jsDelivr 时永远「包不对」重试卡在加载页；钉 @sha / 标签正常。旧内核没法远程救，只能装新包或让 raw/ghfast/gh-proxy 能通（梯子全局模式）。

- 19:5x 朋友小米 2602BRT18C（安卓 16）开机卡加载图：v4.06 已能进（用户 19:48 确认）。boot 日志：更新 14 秒已是最新（没走超时），**游戏日志没找到** → 旧版靠日志的开机触发全不触发；v4.06 的 20 秒计时兜底找到更新页并开大厅。遗留：那台机器所有靠游戏日志的功能（lobbySeen、自动上传、进图识别等）都不灵，未改。详见 history。

## v4.07（core308，2026-11-22 20:0x，全部未实测）不读游戏日志 + 联机卡片精简。详见 history v4.07
- 游戏日志普通手机没有 → 删掉日志监视（Con.line/findUeLog 全删），游戏状态只从 WorldWatch（GWorld 0x53757b0、世界 +0x744 时间、URL 地图名）来：Con.onLeave/onBrowse/onLoaded。以后**不要再加任何靠游戏日志的触发**。
- 开始游戏选图改看 15001 包（maps_chose/map_id）；画质读 NZSystemSettingData +0x7c；有人加入看 PlayerArray；断线 = 非我们发的回大厅。
- 联机卡片去掉「上传日志」「设为我的开房地址」，开房地址固定 frp-put.com:47937。
- 待看（下次诊断）：「世界状态：」事件行（新世界/加载完的地图名、URL 偏移、ms）；「开始游戏 15001：maps_chose=… map_id=…」；「画质：设置对象 …」。若世界状态读不到地图名/不加载完 → 先查 GWorld 偏移。

## v4.08（core309，2026-11-22 20:1x，全部未实测）单机/客户端不上传任何日志
- 用户 20:07 规矩：**单机/客户端不上传任何日志，只有服务端上传**（Ed.uploads() = BUILD==1 && ED==1）。以后新加的诊断/上传一律先判 Ed.uploads()。
- 闸口：LogUp.send/put 直接返回（硬闸，含 zf-logup 线程和 zf_logq 补传）；BootLog.start/ping（启动消息、boot-*）；AvatarLog.export（所有前缀 lobby-/lobbyop-/net-/train-/crash-/upd-/mode-/pak-/box-/ammo-/matchend-/mark-/duel-/flare-/prop-/para-/slot-，手动导出也不传）；Lan.autoUp；Server.opLog、autoCrashSave（Con 调用处也判）；AvatarLog.autoTrain；GameOverlay 进大厅 40 秒 lobby-；MapMark matchend-/mark- 线程；联机页「导出诊断」按钮单机/客户端不显示。
- 不再收集：Beat 心跳（beat.txt、smaps）只服务端开；Server.stepFile 单机/客户端返回 null → Server.mark 步骤文件不写；单机/客户端启动时 Beat.wipe 删旧的 beat*/smaps*/zf_server_steps*/zf_tomb_saved/zf_crash_saved/zf_logq。
- 保留：NetCore 读 ntfy 内核指针（只读）和镜像下载；Room 房间表（ntfy R/J/Q）、Give 代刷 —— 是功能不是日志；LogCap 内存记录（不落盘不上传）。

## v4.09（core310，2026-11-22 20:3x，全部未实测）开始游戏/训练场修好 + 全部日志残留去掉。详见 history v4.09
- 根因：15001 的 maps_chose 是 proto3 repeated → packed（wire 2），v4.07/4.08 只认 wire 0 → 永远「认不出地图」，开始游戏和训练场入口（编号 5）都不进图。已改成两种都认。
- 兜底：开始按钮直接认定大厅已好（不再挂 pendingMap 等声音）；开大厅 12 秒没开服务端就兜底开、40 秒认不出大厅按已加载；开机 90 秒引擎就绪兜底；WorldWatch 读不到地图名用刚发的 open 命令里的地图名。
- 删光日志残留：各类 line()/seen()/trig() 死代码、AvatarLog 读 logcat/异或解码、LogCap 不再写 logcat。**以后不要加任何读日志（游戏日志、logcat）的代码。**
- 菜单标题下新增一行小字（只内存）：世界/地图/状态/模式/15001 原值/最近触发/服务端。用户出问题让他截图这一行：
  - 「15001 没收到」= 游戏请求没到我们的服务端（看「服务端 关」）；「15001 maps_chose=[…] → -1」= 编号不认识，按原值补映射；
  - 「世界 0 无世界」或地图一直「读地图名中」= GWorld 偏移/地图名读取有问题。

## v4.10（core311，2026-11-22 20:5x，全部未实测）训练岛不死 + 刷车安全落点。详见 history v4.10
- 刷车「补丁代码没反应」= 等 cave 完成标记只等 2 秒，第一次刷某种车游戏同步加载超时误报（车稍后出来）。现在等 10 秒，超时分「游戏没执行」/「还在加载（刷车中）」，下次调用先等上次出来。
- v4.07 后 place() 还在等日志行 → 白等 3 秒 + 慢扫描，找不到就再刷一辆（一次最多 4 辆，叠车炸车）。现在：一次只刷一辆；车身 5 点射线找平地（避开坡/树/房边/车/人/水），离人 2.5 米 + 半车长（在车上 4.5 米，放车侧/后）；船只刷水面（找不到水就不刷并提示）；人在水里不刷车；连按 2 分钟内同一处不再刷；刷完射线确认后补满车血。
- 训练岛保命 TrainSafe（单机/服务端）：进训练岛立刻开（连带 NoFall/MinHp）。每 0.1 秒：人物血 < 上限×10 就写成上限×20（单次伤害打不到 0）、关 bCanBeDamaged、憋气 +0xc30/+0xc44/+0xc48 清零（不淹死）、刷出来的车血补满；训练岛的车 set NZVehicle bCanBeDamaged 0。MinHp 改指向人物（以前 livePawn 在车上可能是车）。
- 菜单状态行训练岛多「保命 开 补血N 憋气N 车N」：用户再报死亡就看这几个数是不是在涨（保命 关 = 没启动；补血 0 = 没认到人物）。
- 风险：血条可能显示超出（血 > 上限）；射线打不打得到水面/车身没上机确认。
- 规矩：**别 grep/读 game/logs 里的旧日志**（这次误 grep 过一次车辆类名）。

## v4.11（core312，2026-11-22 21:xx，全部未实测）训练营：撤掉补血/无敌，改成让游戏自己的「死了 4 秒复活」规则生效。详见 history v4.11
- 用户 21:02：「666，你这治标不治本的毛病得改」「车炸是因为我拿枪打爆的啊」→ v4.10 TrainSafe（每 0.1 秒血写成上限×20、关 bCanBeDamaged、憋气清零、给车补血）和训练岛 `set NZVehicle bCanBeDamaged 0`、刷车后补满车血 **全部删掉**。车能被打爆、人会被炸死/淹死 = 正常。
- 原版训练营（BP_NZBRPracticeGameMode）本来就能死：模式里挂着游戏自己的复活系统 ResurrectSystem；复活规则表 ResurrectSystemConfig 的「Practice」行（ModeId 600）= 个人复活、死后 4 秒、不限次数、复活后 3 秒保护。
- 根因：规则表按 GameInstance 里的模式编号选行（GI+0xe4 SubModeId，+0xe0 MainModeId，+0xdc MapId；正式服由服务器给）。我们 open 训练岛没给 → 模式 0 → 拿到通配行 NewRow（60 秒一批整点复活）→ 训练营规则从没生效。
- 修：ModeMap.beforeOpen（Con.exec 发 open 前）：开训练岛把 GI 写成 MainModeId 2 / SubModeId 600 / MapId 5（GameModeType 表「训练营地图」、GameMapID 表 5），开别的图改回原值。GI = GWorld→+0xc4，且虚表 +0x174 = 0xc74234 才写。
- 保留：MinHp（剩 1 血不死，v3.95/3.96 用户要的）、NoFall（摔伤留 1 血，v3.95 用户要的）—— 进训练岛由 TrainMode 立刻启动；TrainMode 只认人物给 MinHp，不写血/憋气/车。车炸、淹水这些不走 MinHp 的伤害照样会死 → 4 秒后游戏自己复活。
- v4.10 刷车修复全保留（一次一辆、落点射线、等 10 秒）。训练岛车辆和别的图一样 `set NZVehicle bCanBeDamaged 1`。
- 状态行训练岛显示「模式编号 600 地图 5（训练营规则：死了4秒复活）」；不是 600 就是没写上（看「模式编号：…」那条 LogCap）。
- 未实测/风险：OnMatchBegin（InfoState+0x350）在训练营里会不会触发没上机看；复活后出生点（Practice ChoosePlayerStart）会不会再遇到「level not prepared」；MinHp 挡掉大部分伤害，所以只有车炸/淹水会真死。用户若说死了不复活：先看状态行模式编号，再查 OnMatchBegin。
- 其它模式（经典 100、战术 505/506 等）同样没给模式编号，各种按模式选行的表都拿通配行；这版没动，待用户决定。
- 工具：game/re/pakscan.py（pak 的文件数据没加密、只是 zlib 压缩，直接扫压缩块解压，不碰加密的索引，**不是解密**），game/re/dtrows.py（DataTable 行）。
- 构建：/tmp/b.sh（build_all.sh 的 JDK 换 jdk-21.0.12.1+1）4.10 4.11 311 312，core/b0-2.ver=312；releases 留 v4.09/4.10/4.11（git rm v4.08）。

## v4.12（core313，2026-11-23 14:5x，全部未实测）训练岛防淹死/炸死 + 服务端大厅闪退诊断。详见 history v4.12
- 用户 14:39：「最新版本服务端会在大厅闪退」「修复训练场会淹死和炸死」。
- 训练岛：HpFloor 把人物两处扣血（0xbe0e48、0xbe0ed0，history v4.10 RE）跳到 SLACK+0x990 cave，训练岛标志（MinHp 缓冲区 +12）开时新血 < 1 留 1。MinHp 原来只挡 GM 伤害链，憋气和车炸不走那条 → 现在也挡。只在训练岛（单机/服务端）开，别的图/人机不受影响。v4.11 的模式编号 600（死了 4 秒复活）保留。Flow 毒圈底层（SLACK+0x990，一直装不上）停用让出空位。
- 服务端大厅闪退：v4.11 第一次启动上传只到 14:37:18 进大厅（RSS 872MB，内存换出降到 837MB），之后被杀，闪退那次的记录要下一次启动才上传。老问题：v4.02–4.06 样本都是 RSS ~1.0GB 被 SIGKILL（原因 2/状态 9，不是 memcg）。新推断（未证实）：游戏是 32 位库在 64 位云手机上跑，低 4GB 地址用光。Beat 心跳加「低4G=已映射 最大空洞=」，下次闪退看这两个数。
- 状态行训练岛加「训练岛防淹/炸死：开（血最低留 1）」。

## v4.13（服务端 core314，2026-11-23 14:5x）服务端回退到 v4.05 代码
- 用户 14:52：「不要再看日志了，直接把服务端退回到之前的版本」；14:53：服务端本来没问题，是更新搞坏的；不读日志的版本能刷枪但很卡。
- core/b1.dex = commit 29e1c81（v4.05）源码，只改版本号 VER v4.13 / CORE_VER 314 重新编；releases 只有 ZFrame-v4.13-服务端.apk。仓库 zframe/src 仍是 v4.12 代码（单机 b0 / 客户端 b2 仍 313）。以后再改服务端要从 29e1c81 的源码出发，或先问用户。

## v4.14（服务端 core315，2026-11-23 14:5x）服务端回退到 v3.83 代码（用户指定）
- 用户：v4.13（=v4.05 代码）服务端照样闪退；v3.79 太早；指定 3.83。core/b1.dex = commit 2e1a617（v3.83）源码，只改 VER v4.14 / CORE_VER 315。注意 v3.83 内核是旧的更新逻辑（@main 镜像 + raw）、会读游戏日志（服务端云手机）。
- 客户端/单机不动（core313，v4.12 代码，不读日志）——用户：手机日志关了，客户端要保留不读日志的功能。
- 仓库 zframe/src 仍是 v4.12 代码。

## v4.15（客户端/单机 core316，未实测）训练场：刷车兜底刷在人前方、常用页「退出训练场，回大厅」+ 训练场 15xxx 当退出、菜单世界状态行隐藏。服务端 b1 仍 315（v3.83 代码），build_all 会覆盖 b1 → 只打 2 0 并还原 b1。

## v4.16（服务端 core317，2026-11-23 16:3x，未实测）服务端改回当前代码 + 再压内存 + 停 MemTrim
- 用户 16:27：服务端（v4.14 = v3.83 代码）还是闪退 → 不再回退，服务端改用仓库当前代码（v4.15，不读游戏日志）。
- 依据（以前的样本，没新看日志）：服务端大厅每次都是 RSS ~1.0GB 被 SIGKILL，可用内存还有 1.7GB → 推断云手机按单进程内存上限杀；v3.83/v4.05/v4.12 都闪 → 不是某一版加的功能。
- 改：Con.SRV_MEM 池 128→80、MipBias 2、r.SkeletalMeshLODBias 1、r.ViewDistanceScale 0.6；MemTrim 停用（换出 650MB 只降 35MB，还卡）。只影响服务端包（Ed.ED==1）。
- 构建：build_all.sh 只跑 `for e in 1`（b0/b2 仍 316），底包 v4.15-单机。releases 保留 v4.16 服务端、v4.15、v4.14 服务端，git rm v4.12/v4.13。
