# ZFrame + 全军出击单机版2.0 修复项目（交接文档）

更新时间：2026-11-16 07:10（北京时间）
接手的智能体请先读完本文件，再读 `notes/history.md`（每一版的详细记录和逆向发现，按时间顺序排列）。

## 目标
在 **荣耀 X70（MTN-AN00，Android 16，未 root）** 上，用自制的免 Root 注入框架 **ZFrame**，让「全军出击单机版2.0」（包名 com.tencent.tmgp.pubgm，UE4 armv7，带 libfucktp / libMczi）能单机正常玩。

## 用户要求（必须遵守）
- 用简体中文回复，直接给结果。每版只做小改动，交给用户在手机上实测。
- 交付前先验证：javac 编译通过，离线能测的部分用 unicorn 模拟或 Java 单测跑通。没在手机上测过的功能，要明确说「未实测」。
- 不推荐第三方工具或虚拟机类 App。游戏里只保留 Z 悬浮菜单。
- 不删枪、不删门。原作者的 libMczi 窗口，用户确认前不要关。
- **启动画面期间不改内存、不发控制台命令**。v1.41–1.45 的闪退就是这个原因。
- 内存签名扫描必须严格限定范围。v1.47 因为扫描太宽，清零了 896 处，导致崩溃。
- 什么都不记忆、不自动应用：每次启动、每局都从默认状态开始。唯一例外是保存找到的风衣编号，但也不自动穿。
- 日志、诊断、探针只在用户按按钮后才运行。
- 界面要简约、高级：近黑底，白/灰字，一个强调色。不用渐变、emoji、彩色标签或复古风格。不要屎山代码。
- 已拒绝的请求：解密腾讯 pak（用户同意后仍然拒绝，改用只读内存扫描）、绕过保护、联网外挂。不要分发解密出来的游戏资源。

## 目录
- `zframe/src/com/zframe/inject/`：注入内核（ContentProvider 热加载，更新内核不用重装游戏）。
  - `ZInit.java`：入口，`CORE_VER`。
  - `GameOverlay.java`：Z 菜单。大厅里只显示 模式、男/女、画质，加上服务端按钮；对局里有 常用/武器/装备/衣服/画面 几页。注意：refreshAny 一定要 post 到主线程 Handler，不能在触摸回调里同步重建界面。
  - `Mods.java`：运行时改内存（加速、高跳、无后座、聚点、无限子弹、模式、风衣 cave 等）。
  - `Con.java`：控制台命令和游戏日志监听。
  - `Patches.java`：通过 /proc/self/mem 写内存，写之前校验原字节。
  - `AvatarLog.java`：「导出诊断」按钮，写到 下载/ZFrame。
  - `Server.java` + `PB.java`：v1.61 自建服务端 + 邮件（见下文）。
- `zframe/test/ServerTest.java`：服务端离线单测，输出给 `game/re/emu_server.py` 校验。
- `game/re/`：逆向脚本。
  - `ue.py`（disf / func_of / XR / callers）、`asm.py`（keystone 汇编，支持 `.word`）。
  - `pn.py`：用 UPROPERTY 名查偏移。
  - `emu_*.py`：各个 code cave 的 unicorn 模拟测试。
  - `srv_cave.py`：服务端 SEND/TICK 两个 cave 的汇编。
  - `protoext.py` + `protodump.py`：从 libUE4 里提取内嵌的 protobuf schema，生成到 `game/proto/`（已在 .gitignore 里，需要时自己跑脚本生成）。
- `game/x/`：游戏 APK 解包（lib/libUE4.so 等）。
- `notes/history.md`：完整历史，最重要。
- `releases/`：各版 APK（v1.46–v1.60）。游戏原包和 obb 太大，放在 GitHub Releases 里。

## 构建发版
- 环境：JDK21，build-tools r36，android-36 的 android.jar。
- 步骤：
  1. javac
  2. d8，生成 inject dex 和 app dex
  3. `python3 zframe/rel.py ZFrame-v<旧>.apk build/unsigned.apk <旧版本名> <新版本名> <旧vc> <新vc>`。build.sh 里的 aapt2 路径有问题，所以改用 rel.py。版本名新旧长度必须一样，比如 1.61，不能写 1.61.1。
  4. 用 apksigner 签名：zframe.jks，密码 zframe123，别名 zf。
- 每版都要：versionCode +1；ZInit.CORE_VER +1；在 history.md 追加记录；APK 复制到 releases/；提交 git。

## 当前状态（2026-11-16 07:10）
- 最新构建版是 **v1.64**（vc65/core65，诊断版：自检钩子是否装上）。v1.63（vc64/core64）：收包改挂 FEngineLoop::Tick 0xb665a8（每帧必跑），Java 扫内存找 ConnectorService 对象。**未实测**。
- v1.62 实测：UpdateService::Tick 在大厅也不跑（帧 0）。
- v1.61 实测：请求 0、游戏已收 0（ConnectorService 的 Tick 离线不跑，大厅重进也不发请求）。
- 用法：大厅 Z 菜单按「开启服务端」→ 大厅重进并自动推一封「补给」邮件 → 游戏邮箱里领取 → 进仓库看。「发邮件」按钮可再发一封。
- v1.60 是测量版（40 个探针），已在手机上测过诊断输出。
- 最后一个功能实测 OK 的版本是 v1.50。实测有效的功能：无敌、传送、刷枪/子弹、3级头 30304000005、3级甲 30305000003、999 子弹、找风衣（绝地风衣 = 30318000000）、开始游戏选图。
- v1.51–v1.60 的功能**都没有实测成功**：开局自带风衣、跳跃加速、大厅灰人修复等。

## 主线问题：大厅灰色人物、换角色、换脸/换头发、仓库换装
- 结论：大厅、仓库、角色页的人物都由 BP/Lua 驱动。数据来自服务器下发的「仓库物品」，离线时没有服务器，所以是灰人、仓库是空的。
  - v1.59/v1.60 用探针测过：原生 C++ 的 avatar 函数调用次数全是 0，只有 RoleSettingBtnClick 和 OnRoleStateChanged 触发了。
  - 用户找到的 B 站私服截图也印证了这一点：角色、脸都是作为物品，通过邮件「领取」下发的。
- 改本地数据（v1.54–v1.58 的各种 cave）都失败了，已放弃。
- **用户已批准的方案：在游戏进程里自建一个服务端**。登录后自动下发角色、脸、发型、衣服物品，不需要用户手动发邮件。计划在 Z 菜单里加一个「重新下发」按钮。

### 协议（从 libUE4 内嵌的 proto 得到）
- 包结构：CSPkg { head(1): cmd(1), plat_id(2), client_ver(3), client_sequence_id(4), mod_route_id(5), result(6), key_pkg(7), ntf_seq(8); body(2): bytes }。回包的 cmd = 请求 cmd + 1，同一个 client_sequence_id。
- 仓库：
  - 13001/13002 CSDepositGetProps（Res: prop_list, role_id）
  - 13003/13004 装备、13005/13006 卸下
- 其他：
  - 36001/36002 relogin
  - 25001–25007 邮件（25007 是新邮件推送）
  - 15001/15002 匹配
- 大厅加载时会发出的请求：36001、22005、30014、37010。之后点按钮不会发任何请求。
- Prop：id, gid, owner_id, num, stack_type, equip_position, bag_id, prop_role_attr{sex, faceid, face_color, hair_id, hair_color, color}。
  - EEquipPosition：1 角色、2 头、3 脸、4 头发、5 上衣、6 裤子、9 手套、11 鞋、14 皮肤…
- 角色 ID：10200000000 是女，10100000000 是男。衣服：上衣 30302000000、裤 30301000001、手套 30306000000、鞋 30303000006、头发 30213000003、风衣 30318000000。

### v1.61 的实现（Server.java）
- 「开启服务端」按钮（只在大厅里，只在用户按下时生效）会装两个 cave，放在 libUE4 代码段末尾空隙 0x4a2d540（SEND）和 +0x100（TICK），都先校验原字节：
  - **SEND**：ConnectorService::SendRawBuffer 0xf950e0 改为跳到 cave。cave 把请求追加到 Java 的请求环形缓冲区，返回 len。原来的逻辑是 connector(this+0xfa054) 为空或 IsConnected(vt+0x38)=0 时就报 "connector is invalidate"。
  - **TICK**（v1.63 起）：FEngineLoop::Tick 0xb665a8 开头（原 `f04f2de9 1cb08de2`，跳回 0xb665b0）跳到 cave（v1.62 用的 UpdateService::Tick 0xfb0218 大厅不跑）。回包区头 32 字节（+12 连接器对象由 Java 找到后填，+16 FEngineLoop this，+20 帧数）。连接器找到后，cave 把每条回包交给 conn->vt+0x164（0xf95298），再执行原指令，v1.61 挂的 ConnectorService Tick 0xf92fb0 离线不跑。
- Java 线程轮询请求：13001 返回假仓库物品，13003/13005 处理装备/卸下，其他带 key_pkg 的请求回一个空的成功包。开启后先主动推一个 13002，然后重开一次大厅。
- 已验证：`game/re/emu_server.py` 通过（两个 cave 的模拟；Java 生成的包用游戏自己的 schema 解码，13002 里有 8 个物品，role_id 正确）；Java poll 单测通过（36001→空成功包，22005 不回，13001→物品列表）。

## 接下来要做（按顺序）
1. **确认 v1.61 能跑通**：用户在大厅按「开启服务端」，看邮箱有没有补给邮件、能不能领取，再按「导出诊断」把文件发回来。Server.report() 会写出请求数、回包数和「游戏已收」的数量。
   - 风险 1：离线时 ConnectorService 的 Tick 可能根本不跑。如果「游戏已收」为 0，需要换一个每帧都会调用的点来投递回包。
   - 风险 2：UNZOnlineLobby 的 bIsOffline / connector 为空，可能让请求在 SendMsg 0xf94a00 之前就被拦下，大厅也不处理回包。可能还要造一个假 connector 对象，放到 ConnectorService+0xfa054，它的 vtable 里 IsConnected 返回 1、Write/Read 接到环形缓冲区；或者把 bIsOffline 清零。
   - 风险 3：启动时的登录包是在启动画面阶段发的（不能碰），大厅可能处于「未登录」状态，忽略仓库回包。如果这样，就主动推 36002/登录类回包，或者在大厅里重新触发 relogin（36001）。
2. 仓库物品被接受后：加上脸、发型物品（equip_position 3/4，prop_role_attr），实现换角色、换脸、换头发；在 Z 菜单加「重新下发」按钮。需要的话用 25007 邮件推送，模仿私服的「领取」。
3. 仓库换装：13003/13005 回包要正确更新 equip_position，同时让大厅预览模型刷新。
4. 刷车：离线时 VehicleManager 没有载具数据表，SpawnVehicle 只能蓝图调用。可以看看服务端或训练营那条路。
5. 训练营入口和资源：OnTrainingModeEntranceBtnClick 0x1375b68 走 BP/Lua。
6. 信号枪不出空投：还没查，可能和 GM 权限或服务端有关。
7. 其他遗留问题：无后座、聚点没生效（AnimInstance+0x18c4 的表对不上）；画质切换效果没确认；开局风衣 cave（v1.52）没实测；声音（Wwise 没初始化，不要再改 0x53b5d58）。
