# 交接给下一个智能体（2026-11-16，v2.00）

先读：README.md（总交接）→ notes/history.md（全部历史 + RE 偏移，末尾最新）→ notes/device-tests/v1.98/（最新实测诊断/崩溃日志/截图）。

## 当前版本
- v2.00（versionCode 101，CORE_VER=101），releases/ZFrame-v2.00.apk，**未实测**：飞机/跳伞段高度 200 米 → 1000 米（修摔死，原因见 history 末尾）；开局整图圈半径 ×1.5。
- v1.99（versionCode 100，ZInit.CORE_VER=100），releases/ZFrame-v1.99.apk，**未实测**。
- 构建：见 README「构建」；`python3 rel.py ../releases/ZFrame-v1.98.apk build/unsigned.apk 1.98 1.99 99 100`，签名 zframe.jks / zframe123 / zf。
- 规矩：简体中文；每版小改动；cave 用 unicorn 模拟（game/re/emu_*.py）；启动画面期间不改内存；不解密 pak、不绕保护、不联网外挂；每版 versionCode/CORE_VER/菜单版本号/history/README 同步并 push。

## v1.99 改了什么
1. 人机只在玩家高度 <120 米、高度站稳 15 秒后才开始刷，第一批再随机等 30~120 秒（Bots.run）；每局人机总数随机 15~30 个，每批 1~2 个，身边少于 3 个才补（用户：不用真刷 100 个，一次别太多）。原因：v1.98 在飞机上（高度不变）就刷了，人机刷在空中摔死。
2. 恢复 nop 游戏数人（ST_A/ST_B），但不写人数。原因：v1.98 用真实人数，人机摔死后只剩玩家 → 阶段 4→10，无结算直接弹回大厅。

## 待改（按用户优先级）
1. **门、地上物资、车都没有**。线索：海岛2(Renew) 日志从没出现 `StreamingServerLayer Add`（0x117bb7c，verbosity≥5 才打），服务器专用关卡层（物资点/门/车点）单机从未加载。门：ANZSceneDoorManager（BeginPlay 0xce8ea4 只打日志；StandaloneUnloadAllDoor 在门管理器+0x380；服务器门由 `[DS Load]` 0xc7b7b8 加载，客户端靠 ClientSyncBatchDoorData）。地上刷物资可用 cave 'G'（SpawnOnePickUp 0x119d884）。
2. **人机没枪**：cave 'I'（Veh.giveTo，ADDCHECK 0xccecbc + GIVE 0xcd3bdc）把枪放进人机背包成功（诊断 5b0b，能放 1 返回 1），但人机不拿在手上 → 要找装备/切换武器的函数（AI 或背包组件里的 Equip/SwitchWeapon）。
3. **人机在地图上显示成队友**：不是 Uin（诊断：人机 Uin=3、玩家=1）。要查 TeamID（PlayerState 里的队伍号，诊断「队伍 b07b006c」是人机队伍字段读数，需和玩家比）及 IsTeammate 判定（NZBRTeamInfo.cpp 0x102281c 附近）。v1.98 写 Uin 900000+序号那段可删。
4. **开局仍等约 60 秒**：日志 WaitingPlayersCountDownTime=1，但阶段 0→1 仍 50~60 秒；真正计时来源未找到（还有 bEnableQuickStartMatch ResetStartMatchCountDownTime[5.00]，阶段 1 仍 45 秒）。用户没回是否缩短 45 秒倒计时。
5. **丢枪闪退**（v1.98 又复现）：crash-1116-191836 / 192302，最后一行是丢枪后 `Load NewDataTable[CommonItemDataTable]`，无 tombstone。
6. **没有结算页面**：人机全死/玩家赢的结算没做（v1.94 的 END 补丁 0x100fc58/5c 已移除）；数人已 nop，需要我们自己在人机全死时触发胜利结算。
7. 降落伞经常没有；画质设置即时生效（做到后删我们的画质按钮）；局域网联机。

## 用户未回答的问题
- 45 秒倒计时要不要缩短；「柜子里怎么可以捡」是什么意思；要不要「出生只穿初始衣服」开关。
