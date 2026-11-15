# ZFrame + 全军出击单机版2.0 修复项目（交接文档）

更新时间：2026-11-15 20:10（北京时间）

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
  - `Patches.java`：通过 /proc/self/mem 改内存（图层解锁、缩短等待、声音探测）。**目前用 MEM=false 关闭了。**
  - `LogCap.java`：把日志同步到手机的 下载/ZFrame 目录。启动后 40 秒内每 1 秒同步一次，之后每 10 秒一次。日志头会列出最近改过的游戏文件。
  - `GameOverlay.java`：游戏内的 Z 悬浮球和菜单。
- `apk/`：各版本 ZFrame APK（v1.0 到 v1.18）。
- `notes/history.md`：每一轮的详细记录和逆向发现（最重要，先读这个）。
- `game/x/`：游戏 APK 解包（dex、lib、assets）。`game/re/`：逆向脚本，ue.py 提供 disf / func_of / XR / callers 等（需要 numpy 和 capstone）；report.md 是逆向报告；pkl 是交叉引用缓存。`game/logs/`：历次游戏日志。
- 游戏原包、注入后的包和 obb（每个约 1.6–1.7 GB）超过了 git 的大小限制，放在 Releases 里。

## 当前状态
- **最新版是 v1.18（versionCode 19，CORE_VER 19），还没实测。**
- v1.13 到 v1.17 每次启动，在腾讯启动画面后约 6–10 秒闪退，没有任何报错，属于静默退出。v1.11 基本可用，偶尔「闪退两次」。
  - v1.17 关掉了全部内存读写，还是闪退，所以原因**不是**内存读写。
  - 现在怀疑的原因：Con 的日志监视读到了上一局留下的旧 PUBGM.log，误以为引擎已经启动，于是开机约 8 秒就发送 SET 命令、`t.MaxFPS` 并改屏幕刷新率模式（用户在 v1.13 里设置过帧率，所以每次启动都会执行）。那时引擎还没准备好，导致闪退。
  - v1.18 的改法：跳过比进程启动时间更早的日志；只有新日志里出现 `LogLoad: Took` 之后再等 10 秒，才发送设置。
  - 如果 v1.18 还是闪退：继续逐个关掉功能来排查（GameOverlay、Con 的监视线程、LogCap 的 logcat 子进程）。对照组是内核 5，它不会闪退。
- 能用的功能：控制台命令、无敌（God）、传送（BugItGo 到圈中心）、跳伞。
- 用不了的功能：NZ 的作弊函数 GiveItem / TestSpawnVehicle / AddBot / SpawnPickupBox / EnterTraining 都是只有 `bx lr` 的空函数（地址见 history）。
- 门、物资、载具缺失的根本原因：它们在 ServerOnly 流关卡图层里，只有专用服务器才会加载。v1.8 解锁了图层标志，v1.9 以后会自动对每个图层执行 StreamLevelIn，效果还没确认。
- 声音：Wwise 没有初始化，原因不明。不要再改 LogAkAudio 的日志级别字节（0x53b5d58），v1.13 改了以后每次启动都闪退。
- 训练营：地图 BR-Training_Mobile_Main 不存在，pak 里只有碎片。
- 开局等待：WaitingPlayers 60 秒 + StartMatch 45 秒。用 SET 和堆内存修改都试过，还没生效。
- 帧率：游戏选项最高 40。Z 菜单里有 60/90/120 按钮（`t.MaxFPS` 加窗口 preferredDisplayModeId），还没测过。

## 下一步
1. 让用户实测 v1.18，确认是否还闪退。不闪退的话，再逐个把 MEM 相关功能打开，确认图层解锁和缩短等待是否安全。
2. 声音：找一种不碰内存的方法。
3. 开局装备、开局就有人机、门的按钮：需要新的实现方法。
