# ZFrame + 全军出击单机版2.0 修复项目（交接文档）

更新时间：2026-11-16 03:50（北京时间）

## 目标
在 **荣耀 X70（MTN-AN00，Android 16，没有 root）** 上，用自制的免 Root 注入框架 **ZFrame** 让「全军出击单机版2.0」（包名 com.tencent.tmgp.pubgm，UE4 armv7，2025 年的改版，带 libfucktp / libMczi）能单机正常玩。要做的事：门、物资、载具、人机、声音、训练营、开局装备（三级套+M4+配件）、缩短飞机等待、开局就有人机、跳伞、帧率高于 40。

## 用户要求（必须遵守）
- 用简体中文回复，直接给结果。每次只做小改动，用户在手机上实测。
- 不推荐第三方工具或虚拟机类 App。游戏内只保留 Z 悬浮菜单，不要内存修改器界面。
- 不删枪、不删门。原作者的 libMczi 窗口在用户确认前不要关。
- 已拒绝的请求：解密腾讯 pak、绕过王者荣耀保护、联网外挂。

## 目录
- `zframe/`：ZFrame 源码。`build.sh` 用于构建（JDK21，签名 zframe.jks / 密码 zframe123 / 别名 zf）。每次发版都要把 AndroidManifest 的 versionCode 和 `src/com/zframe/inject/ZInit.java` 里的 CORE_VER 加 1。
  - `inject/ZInit.java`：注入入口。游戏里装的是 v1.4 的桩，会通过 ContentProvider `content://com.zframe.mods/core` 热加载 ZFrame App 里最新的内核 dex，所以更新内核不用重装游戏。如果连不上 ZFrame App，就用游戏里自带的老内核 5（界面是内存修改器）。
  - `Con.java`：通过 GameActivity.nativeConsoleCommand（反射调用）向 UE4 控制台发命令；监视游戏日志 PUBGM.log（加密时是 XOR 0x73）；自动发送 SET 设置；自动 StreamLevelIn。
  - `Patches.java`：通过 /proc/self/mem 改内存；「更新补丁」时打文件补丁去掉开机黄框 Err:107（ShowUpdateError→bx lr）。
  - `Mods.java`：游戏内开关（非正常演习=不上飞机+不刷毒、男角色、加速/高跳、无后座/防抖/无限子弹），运行时改内存。
  - `LogCap.java`：把日志同步到手机的 下载/ZFrame 目录。启动后 40 秒内每 1 秒同步一次，之后每 10 秒一次。日志头会列出最近改过的游戏文件。
  - `GameOverlay.java`：游戏内的 Z 悬浮球和菜单。
- `apk/`：早期 ZFrame APK（v1.0 到 v1.25）；`zframe/ZFrame-v1.19.apk` 到 `ZFrame-v1.46.apk` 是之后各版；`releases/` 放最新可用版（v1.46）。
- `notes/history.md`：每一轮的详细记录和逆向发现（最重要，先读这个）。
- `game/x/`：游戏 APK 解包（dex、lib、assets）。`game/re/`：逆向脚本，ue.py 提供 disf / func_of / XR / callers 等（需要 numpy 和 capstone）；report.md 是逆向报告；pkl 是交叉引用缓存。`game/logs/`：历次游戏日志。
- 游戏原包、注入后的包和 obb（每个约 1.6–1.7 GB）超过了 git 的大小限制，放在 Releases 里。

## 当前状态
- **最新版是 v1.47（versionCode 48，CORE_VER 48），待实测；上一个实测稳定版 v1.46。**
- 界面：标题「艾挽 全军出击单机版」，logo「AW」。五指点屏打开菜单，✕ 关闭。左边分页：功能、各枪类、子弹、衣服、设置。
- v1.41～v1.45 启动几秒就闪退（SIGSEGV，fault addr 0x644，MainThread-UE4）。原因：启动画面阶段自动恢复上次的开关（打代码补丁、或 mover 线程发 AddAvaItem 命令取人物地址）。v1.46 起启动时不恢复任何开关，进游戏后在菜单里手动打开。**以后不要在启动阶段改内存或发控制台命令。**
- 已删掉/停用：开火不扣子弹代码补丁 0xefd104；开局等人补丁 0x1019774。
- 实测有效：刷枪/刷物品/子弹、信号枪（编号存 prefs "flare"）、3级头 30304000005、3级甲 30305000003、男角色、高跳（CMC +0x2f8 → JumpZ）、不上飞机（SetGamePhase 阶段1/2→4）、游戏自带开始游戏选图（1老海岛 3新海岛 4雨林）、无敌、传送。
- 待实测：非正常演习（不上飞机+不刷毒）、加速（CustomTimeDilation +0x58）、无后座（改后座参数表）、无限子弹 ∞（控制台命令 InfiniteAmmo）。
- 不可行：刷车、放机器人/空投（GM 函数是空函数）、大厅仓库/邮件（没有服务器）、其他地图（只有 3 张完整）。RecoilScaleBase 是加密随机倍数，别写 0。
- 声音：Wwise 没有初始化，原因不明。不要再改 LogAkAudio 的日志级别字节（0x53b5d58）。

## 下一步
1. 用户进大厅后逐个手动打开各开关测试，哪个崩或没用就停哪个。
2. 投掷抛物线固定在初始视角：加日志记录出手角度/初速，让用户镜头朝上、朝下各扔一次。
3. 绝地风衣（连体衣 30323…）编号，用户在衣服页自己输入试。
