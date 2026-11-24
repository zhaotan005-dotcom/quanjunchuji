# ZFrame / 全军出击 history (moved from project skill 2026-11-15 19:20)
See project skill v27 text (archived below in summary). Key RE facts live in /workspace/work/qjcj/re/report.md.

## 19:13 round
- User: only 无敌 works; GM/给枪/刷车/人机/训练营 no effect. Wants shorter plane wait, start kit (三级套+M4+配件), bots at start, doors.
- RE: exec thunks GiveItem 0xe40c58, TestSpawnVehicle 0xe3df3c, AddBot 0xe43e04, SpawnPickupBox/EnterTraining 0xe43f54 all call 0xb63a30 (bx lr) = stripped. ShowGMPanel vtable +0xb54, QuickStartMatch +0xe14 unverified.
- Engine CheatManager alive (God works). SET/GETALL/OPEN/TRAVEL strings present (utf-32 TCHAR).
- Props: NZBRGameState {WaitingPlayersCountDownTime, StartMatchCountDownTime, WaitingSkydivingCountDownTime, NumPlayersBeforeCountDown}; NZGameMode {bEnableGiveBotEquip, bEnableAIDefaultWeap, bEnableGridMap, DoorManager}; NZBattleRoyaleGame {MaxBotLimit, BotArray, BotSpawnPointActorArray}; NZSceneDoorManager {StandaloneUnloadAllDoor}.
- v1.11 delivered (versionCode 12, CORE_VER 12).
- Plane route bug (19:03): CalcAirLineByRestrictedAreaInfo random center unclamped; v1.10 added teleport-to-circle (now BugItGo).

## 19:28 round → v1.12 (versionCode 13, CORE_VER 13)
- User: teleport + God work; Fly no; wants Chinese toasts; wait still 43s, no countdown first tens of seconds; no sound.
- Log (19:04 session): phase0 WaitingPlayers 60s (no UI), phase1 StartMatch 45s. ResetStartMatchCountDownTime at GameState+0x73c (GameMode code ~0x100cb04, GameState = GameMode+0x908).
- Training: engine "map ... BR-Training_Mobile_Main does not exist"; TrainingIsland pak 11 MB = pieces only → main map missing. Button removed.
- Sound: Wwise plugin mounted, "Wwise(R) SDK Version" init line never printed → AkAudio device not initialised. Open.
- LogCap bug: same-named PUBGM.log from several dirs overwrote each other; now tagged app/int/sd and only files <2h old.
- v1.12: Patches.shortenWait heap scan (+0x73c==5.0, 60/45 within 0x71c–0x760 → 3.0), auto after NZBRGameState.cpp:304 line; button 缩短本局等待; Chinese toasts; removed 飞行/落地/进训练营/重发开局设置.

## 19:40 round → v1.13 (versionCode 14, CORE_VER 14)
- Sound RE: FAkAudioDevice::Init = 0x3c32e24 (StartupModule 0x3c3a730 calls wrapper 0x3c32c00; on fail deletes device). Gates: -nosound (not on cmdline), two GOT bools, static bInitialized byte at 0x53b5d50 (written 1 at 0x3c3309c on success). LogAkAudio category verbosity byte at 0x53b5d58; the "Wwise(R) SDK Version" log needs >=5, so missing line ≠ failed init (category likely muted).
- v1.13: Patches.audioProbe forces 0x53b5d58=5 for 30 s from lib load, then logs flag (ZFrame log line "声音: Wwise 标志=…"); menu shows 声音 status. TWEAKS add "log LogAkAudio Log"/"log LogAudio Log".
- FPS: game ApplyFrameRateOption max option 3 = 40 fps. v1.13 menu 60/90/120/默认 → t.MaxFPS N + window preferredDisplayModeId; saved in prefs "zframe" fps; re-applied after each ApplyFrameRateOption line and at tweaks.

## 19:42 → v1.14 (versionCode 15, CORE_VER 15)
- v1.13 crashed every launch right after Tencent splash. Likely cause: forcing LogAkAudio verbosity byte (0x53b5d58=5) → previously-muted Wwise log path crashes, or byte isn't verbosity. v1.14 removes the write and the "log LogAkAudio/LogAudio" commands; audioProbe only reads flag after 25 s. Lesson: don't unmute LogAkAudio.

## 19:45 → v1.15 (versionCode 16, CORE_VER 16)
- User: v1.14 still crashes after Tencent splash. Suspect v1.12 shortenWait: triggered by any LogNet Browse (lobby load too), loose pattern (+0x73c==5.0 with 60/45 nearby) could hit random heap and write 3.0 → crash. v1.15: trigger only on NZBRGameState.cpp:304 line; require vtable at obj+0 inside libUE4 range and 120.0 + 1.0 in window. If still crashing, ask for newest Download/ZFrame logs; v1.11 is last known-good.

## 19:50 crash analysis → v1.16 (versionCode 17, CORE_VER 17)
- ZFrame.zip (120 files): runs 194138–194212 (core 15) and 194443–194501 (core 16) died <10 s (logcat only 26 lines, UE log never opened). 194428 (core 16) reached UE: layers fixed on 1st scan, then UE log ends 19:44:41 at APP_EVENT_STATE_ON_PAUSE (identical to good 19:22:53 run up to there); died ~4 s after UE start.
- Earlier "闪退两次" sessions had the same ~104 KB truncated UE logs → same crash, now persistent. No signal/backtrace captured (sync every 10 s). TerSafe active (TERSAFE:HandleAntiData) → hypothesis: TP kill, possibly persisted after v1.13's rapid /proc/self/mem writes. Unconfirmed.
- v1.16: LogCap syncs every 1 s for first 40 s; header lists game files modified in last 2 h. Asked user to try v1.11 first (A/B: ours vs persistent state).
- 19:56: user's "v1.16 不闪退" run actually used built-in core 5 (provider com.zframe.mods not found; ZFrame app missing after uninstall). Old core does not crash → crash is in newer core features (v1.12+). Asked the user to install and open v1.16, then send the 1 s-sync logcat if it crashes.
- 19:59 v1.16 logs (core 17): 3 runs die ~10 s after start with no fatal line (silent exit → likely TerSafe kill). No "补丁" note synced, so death happens right when libUE4 shows up (late load) = the moment layerFix starts reading /proc/self/mem. In 194428, libUE4 was found at 0.9 s and that run lived longer. Hypothesis: TP watches /proc/self/mem access.
- v1.17 (versionCode 18, CORE_VER 18): safe mode Patches.MEM=false (no audioProbe, layerFix or scanWait). findBase polls every 200 ms. Console features (teleport, God, set tweaks, FPS) kept.
- 20:02 v1.17 safe mode STILL crashed (~6 s) → memory access was not the cause. New suspect: Con watcher read the previous session's PUBGM.log, so engineSeenAt was set at once and runTweaks (set commands plus t.MaxFPS and a display-mode change from the saved fps pref) fired about 8 s in, before the engine was up. This also explains the old occasional "闪退两次".
- v1.18 (versionCode 19, CORE_VER 19): the watcher skips logs older than process start. Tweaks fire 10 s after "LogLoad: Took" in the new log. MEM still false.
- GitHub connected (zhaotan005-dotcom), but the integration can't create repos (403 "Resource not accessible by integration"). Asked the user to create an empty private repo zframe-pubgm.

## 20:55 round → v1.19 (versionCode 20, CORE_VER 20)
- v1.18 (core 19) logs: no crash, matches play. Sound still missing.
- Finding: 单机版 flow goes StartupMap → (after update err 107) Browse BR-Savage_Mobile_Main directly, never the lobby. UNZFrontEndAudioManager init/LoadAllBank (BGMLobbyBank, C01_UI_New_Bank) and UNZPlatformAudioHelper::OnInitialize only logged when FrontEndMap_NEW loads (20:34:55, after quitting a match). Hypothesis: lobby BP also does UAkGameplayStatics::LoadInitBank etc. → match silent when lobby skipped.
- `set NZBattleRoyaleGame MaxBotLimit` → "Unrecognized class"; class is NZBattleRoyaleGameMode (utf32 0x43b6228). Fixed in TWEAKS.
- v1.19: Con.tickAudio: after first BR LoadMap with no lobby audio init seen this process → `open /Game/Maps/Erangle_RoleHall/NEW/FrontEndMap_NEW`, wait for FrontEndAudioManager init + 4 s → `open <same BR map>`. Menu: 经大厅重进本局 button, 自动修声音 toggle (prefs zframe/audio_auto).
- AkDiag: writes [Core.Log] LogAkAudio=Verbose, LogAudio=Log into …/UE4Game/PUBGM/PUBGM/Saved/Config/Android/Engine.ini before engine start (file only). Crash guard via prefs akdiag_boot/akdiag_off. Lines containing AkAudio/Audiokinetic/Wwise(R) copied into ZFrame log head as "声音日志:".
- Patches: MEM still false, but audioProbe re-enabled read-only (1 byte at base+0x53b5d50 after 25 s) → "声音: Wwise 标志=".
- Build env here is aarch64: x86 aapt2 unusable, so v1.19 APK was assembled from v1.18 APK (binary manifest patched versionCode 19→20, versionName 1.18→1.19), new classes.dex + assets/inject.dex, signed with zframe.jks. build.sh paths updated but aapt2 step needs x86_64.
- 21:05 v1.19 test: still no sound. Game not listed in Android per-app volume panel → no audio stream opened at all. Probe: Wwise 标志=0, LogAkAudio verbosity=5. Lobby bounce ran (FrontEnd banks "loaded") but no effect.
- Root cause (RE): wrapper 0x3c32c00 logs "Audiokinetic Audio Device initialized."/"...initialization failed." via LogInit (GOT 0x5124fd0 → 0x51b8678, same category as "LogInit: Using libcurl") at Log level. Neither line ever appears → FAkAudioModule::StartupModule (0x3c3a730, vtable ref 0x50a81c8) never runs → AkAudio module is never loaded (no ModuleManager "not found" warning either, so plugin manager never asks for it). FAkAudioDevice::Get (0x3c318a0) only returns a device if module already loaded → all Wwise calls no-op.
- Init 0x3c32e24 gates: nosound (utf32 0x4730770), GOT bools 0x5125008/0x5124fe4, static bInitialized 0x53b5d50 (merged globals; LogAkAudio verbosity at +8 = 0x53b5d58).
- AkDiag useless: game reads Saved/Config/Android/Engine.ini via LoadCryptFileToString → "invalid config file" (expects Tencent-encrypted ini). Remove AkDiag + strip our lines next version.
- Proposed fix (needs user OK, breaks MEM=false): renderer fn 0x22e7f58 does once-only FModuleManager::LoadModule(FName(L"SoftwareOcclusionCulling")) (utf32 at 0x45e7104, ~2.8 s after log open, warning "not found"); result only stored at 0x4a35634, never used. Overwrite that utf32 string with L"AkAudio\0" before then → engine loads Wwise module itself. 0x101bfb8 is an existing IsModuleLoaded/LoadModule("AkAudio") helper with no callers.
- v1.20 (versionCode 21, CORE_VER 21): user allowed modifying the game package ("可以改游戏安装包，只要确保不闪退"). ZFrame app game card gets a 「声音补丁」 button: reads the installed injected base.apk, Patcher.patchSound rewrites lib/armeabi-v7a/libUE4.so at file offset 0x45e7104 (L"SoftwareOcclusionCulling" → L"AkAudio\0" + rest unchanged, 32 bytes differ). It works for both STORED and DEFLATED entries and CRC is recomputed. It re-signs with gkey and update-installs with no uninstall (aborts if the game isn't signed by gkey; on install failure the uninstall prompt is never shown). The same button restores. soundState checks the exact bytes, so an unknown libUE4 version gets no patch. Tested on the real libUE4 in the sandbox: patch → state 2, restore → md5 identical.
- Call site 0x22e7f58 = FRendererModule vtable slot 9 (BeginRenderingViewFamily, game thread), once-only, result only stored → safe target. Module missing → only a "not found" warning.
- Core: AkDiag now only cleans its old lines out of Engine.ini (deletes the file if empty); the auto lobby bounce defaults off (pref audio_auto2); the manual button stays.
- v1.21 (vc 22, core 22): the user's ZFrame app had an empty games list and they started re-injecting the already-injected game, which would add classes3 and prompt an uninstall. Fixes: patchBase refuses a manifest that already contains com.zframe.inject.ZInit; pickInstalled on a gkey-signed game just re-adds it to the list; the 声音补丁 button shows for any installed non-tool game; install() skips the uninstall prompt when the game is gkey-signed.
- 21:27 sound confirmed working: "Audiokinetic Audio Device initialized." appears in the logs.
- Map paths (from the libMczi window run, 21:30): 海岛 /Game/Maps/ErangelMobile_Runtime/BR-Erangel_Mobile_Main; 海岛2 /Game/Maps/ErangeMobileRenew_Runtime/BR-ErangelRenew_Mobile_Main (BP_NZBRIslandGameMode_C, starts on the spawn island, no guns; CachedPickupSPList=0); 雨林 Savage; lobby FrontEndMap_NEW.
- Old in-game "ZFrame 修改器" = built-in fallback core 5 (memory editor). It appears when the game can't reach the ZFrame provider (Honor 关联启动 block) → that's why "only launching from ZFrame gives the new version".
- v1.22 (vc 23, core 23): Z menu renamed 艾挽 (ball text 艾), with tabs 地图/功能/设置; map buttons 海岛/海岛2/雨林/进入大厅. A map button goes via the lobby first when lobby audio hasn't been seen yet this launch (pref audio_auto3 default on). ZInit caches the last provider core in filesDir (zcore_last.dex/.ver) and uses it when the provider fails, if it's newer than the built-in core. The app's 修补游戏 also replaces the injected ZFrame dex in the game APK (detected by the Lcom/zframe/inject/ZInit; string), so the built-in core becomes current.
- Declined: renaming/re-skinning the libMczi window and cracking its network verification.
- TODO: guns on the 海岛2 spawn island.
- v1.23 (vc 24, core 24), changes requested 21:43–21:45:
  - In-game UI is now a resident panel like the libMczi window instead of a ball + dialog: a draggable title bar ("艾挽 · 全军出击单机版"; tap it to fold ▼/▶), a left tab column (地图/功能/设置), and content on the right. View tag zframe_panel.
  - Lobby logic: on boot, ~3 s after StartupMap loads, `open FrontEndMap_NEW` runs once (audio inits in the lobby). After that, map buttons go straight to the map. The old BR→lobby→BR bounce stays only as a fallback, used if something enters a BR map before lobby audio has been seen. Pref audio_auto3.
  - FPS/quality: device level comes from Tencent APM QCC config (plain asset assets/QCC_APM_PUBGM; cached copies apm_qcc* in app data). Adreno 810 isn't in the table → level 2. Qcc.fix() at boot adds Build.MODEL to level6 filter-model "6" in the cached copies; 修补游戏 also patches the asset. The menu's FPS buttons were removed, and applyFps is disabled (the game's own setting decides).
- v1.23 test, 21:53: QCC took effect: MobileLevel went 2→5 and PipelineQuality 2→5. But the 超高 framerate option still says 「您的手机暂不支持」, and the lobby character renders as a gray mannequin (default material look). Settings log shows CalcRenderQuality InDeviceLevel:0. UE has MobileLevel2DeviceLevelMap / GetDeviceLevelByQcc / DeviceLevelForTest; the FPS gate is not found yet (probably UMG/blueprint).
- QCC algorithm, from the dex: com.tencent.hawk.bridge.QccConfig.judgeDcls tries filter-model, then gpu, soc and manu, then "and opts" (res/ram/cpufreq/cores/gpu tables).
- libMczi is loaded by System.loadLibrary("Mczi") in com.tencent.tpshell.TPShellApplication.preInitial. It has no JNI_OnLoad (init_array only) and imports EGL/GLES (the ImGui window).
- v1.24 (vc 25, core 25):
  - The QCC model bump is now off by default. Qcc.fix removes the model unless the pref qcc_up is set, and 修补游戏 also reverts the asset. The panel's 设置 tab has a 高档机型 toggle.
  - 修补游戏 is now a list: 更新内核 / 更新内核 + 关掉原作者窗口 (replaces libMczi.so in the APK with a copy of libzlib.so and backs the original up to ZFrame filesDir/libMczi.orig.so) / 恢复原作者窗口 / 去掉声音补丁.
  - User confirmed 21:53 that "他那个" (the libMczi window) should go.
- Open requests: lobby character fix, and putting all wardrobe clothes into the 仓库 (server inventory, offline). Not done; needs RE.
- 22:00 user: the gray lobby avatar was always there; it comes from the package, not from QCC. Simple ask: spawn 风衣 in-match, into the backpack or on the ground. Found that the exec functions GiveItem/GiveItemCount/SpawnPickupBox are stubbed to bx lr. ServerGiveItem (thunk 0xe67c20) calls vtable+0x378, implementation not checked yet. Asked the user whether to attempt a libUE4 patch that routes GiveItem to the real pickup/add-to-backpack path. Awaiting answer.
- 22:08: the libMczi window still showed. The 22:04 file listing has libMczi.so at its original 3866712 bytes and the lib dir is still from 21:19, so the v1.24 修补 never installed. Asked the user what happened. TPShell preInitial loads fucktp, then Mczi, then tprt from nativeLibraryDir.
- In-match outfit: ANZBRCharacter::InitAvatarProxy (0x10d7380) has no DS playerinfo offline, so it falls back to DefaultRoleClotheDataTable and gets the list 10200000000, 30302000000, 30301000001, 30306000000, 30213000003, 30303000006, then runs Give Inventory. Plan: a libUE4 patch that adds the coat ID. Asked the user for the 黑色连帽风衣 ID; known coats are 30315000050 and 30315000061. Starting guns come later.
- Gray avatar: the 'Lower Device for Original Mesh' check (0xea5b4c) is gated on quality >= 2, but the avatar stays gray even at level 5, so the cause is something else. Deprioritised.
- v1.25 (vc 26, core 26):
  - 功能 tab adds 查物品编号（只读）. It scans the heap through /proc/self/mem (read-only) for UTF-32 names containing 风衣 or 连帽, finds pointers to those names, and collects nearby int64 values in 1e10–1e11. ItemFind.java.
  - 设置 tab shows the 原作者窗口 status by comparing the libMczi.so and libzlib.so sizes in nativeLibraryDir.
  - Next: once the user sends the coat ID, patch the InitAvatarProxy default list in libUE4.
- 22:20 v1.25 test:
  - libMczi.so in the lib dir is now 67088 bytes (the stub), so the 原作者窗口 is closed.
  - ItemFind found the 风衣 names, including 黑色风衣 and 绝地风衣 (the hooded one; 脱帽版 is separate). But the "IDs" next to them are FString {ptr,Num} pairs (hi = length), not item IDs, so the tool is useless as written.
  - The user asked to remove it.
- Coat plan: only two Coat models are mounted (30315000050 F_Jacket, 30315000061), so try 30315000061 as the 绝地风衣.
- User's next ask: find the gun-spawn code and ammo. GiveItem and its siblings are stubs (0xb63a30, bx lr), so look at the pickup spawn path instead (SpawnPickup / ItemGenerator / PickupManager).
- 22:25 RE for gun/ammo spawn:
  - The GM panel (class with CloseGMPanel/GiveEquip/OnBtnGiveM416…, funcs ~0x1716xxx–0x171exxx) is dead. GiveEquip only issues the console command "GiveItem <id>" (IDs 30307000006, 40100000001/2, 20101000002, 20503000004, 20105000002, 30304000003, 30305000002, 20504000001, 20501000001, 20502000001/2, 20501000003). OnBtnGiveM416 builds FString "2101001..." and calls the 0xb63a30 stub.
  - The exec GiveItem thunk 0xe40c58 parses one FString and calls 0xb63a30 (stub) at 0xe40ccc: `mov r0,r4(this); mov r1,sp(&FString); bl`. The stub call can be redirected into a code cave (e.g. the dead GM function bodies).
  - Real give path, used by bots: ForceGiveAllEquipToAI 0xc1a534 iterates [ctrl+0x410] entries (0x18 bytes; id64 at +8, count at +0x10) and calls 0xc11f04(entry.., ctrl, pawn).
    - Inside it, Type = id/10000 (0xce0248) and TSID = id%10000 (0xce0260).
    - Then 0xc12370(ctrl, pawn, &{Type,TSID}, count) does: backpack = [pawn+0x838] (0xcc8274); n = 0xccecbc(bp, Type, TSID, count, 0, -1, -1, 0); then 0xcd3bdc(pawn, Type, TSID, 0, [sp]=n, -1, uidlo, uidhi, n, &zero, 0…). uid comes from 0xc1194c(ctrl) = [[ctrl+0x420]+0x3b0] (0 if null).
  - Plan: a cave that parses "GiveItem <id> [count]" and calls ccecbc + cd3bdc with uid 0. Open problem: the ID format for guns/ammo (split /10000) is still unconfirmed.
- 22:35 v1.26 (vc 27, core 27):
  - Removed ItemFind.
  - 修补游戏 now also applies the 刷物品补丁 (applied together with the sound patch, restored together), 3 patches:
    - bl at 0xe40ccc changed from 0xb63a30 to 0x17169c0.
    - 0x173c91c (grenade thunk tail-b) becomes bx lr.
    - 0x17169c0 holds the cave (404 bytes, source game/re/cave_giveitem.s, assembler game/re/asm.py).
  - Cave logic: it parses the FString (UTF-32) as "<id>[x<count>]" with count capped at 999, then splits Type=id/10000 and TSID=id%10000 via __aeabi_ldivmod 0x8ad168. bp=[char+0x838]. It calls ccecbc(bp,T,S,cnt,0,-1,-1,0) and uses n (or cnt if n<1), then cd3bdc(char,T,S,0,n,-1,uid0,0,n,&zero,0...).
  - IDs decoded from the GM panel:
    - Guns: M416 20101000001, AK47 20101000002, UMP9 20105000001.
    - 手雷 20401000001.
    - Ammo 20600000001/2/3 (given after each gun by 0x171e920).
    - GM list (guesses): 30304000003 头盔?, 30305000002 甲?, 30307000006 背包?
    - Other IDs in the 0x8b4368 table: 201xx guns, 205xx, 401xx.
  - InitAvatarProxy also gives avatar items via cd3bdc directly (0x10d8c14), so the coat should work through the same path.
  - 功能 tab: 一键开局装备, gun/ammo/gear/coat buttons, and custom ID + count. Patch status is read from libUE4 at 0xe40ccc.
  - Build: zframe/rel.py (swap dex and bump manifest from the previous APK).
- 22:48 v1.26 test:
  - The patch was installed (刷物品补丁状态 2) and there was no crash, but nothing was given.
  - The game log shows no StaticGiveToImpl or "Can't Find Item" lines. GridMap shows Players:1.
  - The sd log is XOR 0x73 over the WHOLE file (decode fully; the per-line decode was wrong).
  - GiveItem is on ANZCharacter with flags 0x20601 (Exec|Native|Public|Final).
- v1.27 (vc 28, core 28):
  - The cave now takes a 3rd number: the address of a Java direct ByteBuffer (Buffer.address via reflection). It writes diagnostics there:
    - magic 0x5a01 on entry, 0x5a02 when done
    - +4 this, +8 bp, +12 Type, +16 TSID, +20 cnt, +24 ccecbc ret, +28 cd3bdc ret
  - New cave is 0x22c bytes. Patcher recognizes the v1.26 cave (giveState 3) and upgrades it.
  - Panel toasts and logs "刷物品诊断".
  - Removed the sound and QCC toggles from the panel; audioAuto and Qcc.up are forced on.
  - Tweaks added: set NZSystemSettingData FrameRateOption 4, RenderQualityOption 0; set GameEngine bSmoothFrameRate 0; t.MaxFPS = the screen's max refresh rate, plus a window preferredDisplayMode at max refresh.
  - ApplyFrameRateOption (0xca24ac) takes its FPS table from [this+0x88][DeviceLevel]; at level 5, option 4 = 60.
- 23:03 v1.27 test: every diagnostic said "补丁代码没被调用". The GiveItem exec on the pawn is never reached (God/BugItGo on CheatManager do work).
- RE findings:
  - Thunk 0xe40c58 is identical-code-folded. It is shared by ANZCharacter::GiveItem, ANZBRPlayerController::AddAvaItem / RemoveAvaItem, and ANZPlayerController::ServerCmd, so all of them now reach the cave.
  - AController offsets: Pawn +0x2b4, Character +0x2c0, PlayerState +0x2c4.
  - Exec function list: /tmp/execs2.txt. Regenerate with the scan of FFunctionParams (name, 0x45, 0, flags & 0x200).
- v1.28 (vc 29, core 29):
  - The cave acts only when the string starts with 'P'. It then takes pawn = [PC+0x2c0].
  - Java sends "AddAvaItem P<id>x<n>x<dbgaddr>".
  - Patcher recognizes the v1.26 and v1.27 caves as old (state 3). In-game status checks the word at 0x17169dc (e3560002).
  - Removed: the layer/wait status lines, the custom ID input, the console, the 原作者窗口 section, and the 恢复原作者窗口 option.
- 23:22 v1.28 test: GIVE WORKS through AddAvaItem P... on the PC. Every diagnostic came back as 走完 with result 1.
  - User feedback: the helmet given was level 2 (30304000003 = Avatar/Common/Helmet/30304000003/Helmet02_*). The armor 30305000002 was level 1. No attachments were given. The coat was wrong: both 30315000061 and 050 are not the 风衣.
- ID sources: UTF-32 strings in libUE4.
  - Auto-pickup DF list at 0x42c9c64: 401000000x1-3, 401010000x1-3, 2040100000x1-4, attachments 20503000001/3/8/4/9/5, 20504000001, 20501000001, 20502000001, 20505000001.
  - GM list at 0x44502cc.
  - Gun folders W2010x000y map to id 2010x00000y.
- getall works and prints into the sd log. Server DoCmd ("spawn", "avehicle") is stripped from the client. CheatManager Summon exists ("Fabricate %s").
- v1.29 (vc 30, core 30):
  - App label changed to 艾挽补丁更新 (same UTF-16 length; rel.py patches it). Single page: 更新 + 启动游戏 (import only when not injected). The nav and modules are hidden.
  - 更新 now runs runSound(toAk=true) directly. Shorter progress and toast text. Done toast: "更新完成，打开游戏就行".
  - Panel: more guns, a 全套配件 button (ATT list), and toasts shortened to "已刷 X" / "X 没刷出来".
  - 刷车: getall WheeledVehicle/NZVehicleMotoCycle bHidden, Con.line parses the *_C class names, then EnableCheats + Summon <cls>, cycling through the classes.
  - Logs "身上装备编号:" from OnAvatarAssembler, to discover level-3 helmet/armor IDs and the coat ID.
- v1.30 (vc 31, core 31):
  - 刷车 root cause: no vehicle ever spawns offline (NZBRVehicleManager VehicleSpawnComponentList.Num()=0, GridMap Vehicle:0/0), so `getall WheeledVehicle bHidden` returns nothing → v1.29 always said "过两秒再点".
  - RE: GM panel OnSpawnVehicleSelectChange 0x171e4b0 → 0x11dc7c4(BRPC,id) = ProcessEvent ServerSpawnVehicleById; its _Implementation (PC vtables +0xd54) is 0xb63a30 (bx lr) = stripped. Summon (0x28898dc) only uses StaticFindObject, so the class must already be loaded.
  - New PakScan.java: read-only scan of obb (zip, stored paks) / loose .pak file-name indexes (no decryption; encrypted indexes are skipped and counted). Collects vehicle BP class paths (/Vehicles/, BP_/VH_) and Avatar/<cat>/<11-digit id>/<file> names. Logs "车型候选", "外观 ...", "3级头= 3级甲= 风衣= 外套候选=".
  - 刷车: scan on first tap and auto-spawn when done; load BP via `set GameModeBase SpectatorClass <path>` + `set PlayerController CheatClass <path>` (FindImportedObject load fallback; metaclass mismatch means nothing is changed), then `Summon <path>`; on "Failed to summon/find class" tries next candidate (max 6).
  - 头盔/护甲 → 3级头/3级甲: scan result (Helmet03/Armor03/lv3 in file names), fallback 30304000004 / 30305000004 (pattern: 30304000003=Helmet02, 30305000002=1级甲).
  - 风衣 button: scan match (trench/windcoat/…); otherwise cycles Coat/LongShirt ids and toasts each id so the user can report which is the 风衣.
  - Build env now: tools from build-tools r36 (android-16) + platform-34 android.jar + JDK21 aarch64.
- v1.31 (vc 32, core 32):
  - v1.30 PakScan found 0 paks: data paks are not in obb / Android/data (only files/update, IFS first_source.ifs = base.apk?assets/nzres.png). Now also scans the game's own base.apk + splits (zip, stored .pak entries) and logs pak counts.
  - Device test: 30304000004 (Helmet02_2) gives OK; 30305000004 gives 可放=-6 结果=0. 3级甲 now tries 30305000003,05,06,01,07,08 in order (auto-next on 结果=0), remembers the one that works, toast shows id.
  - 传送到安全区 → 传送到地图中心: BugItGo 0 0 12000 (world origin = map centre; ground samples 5–50 m), auto God on if not on (tracked Con.godOn, reset each map), auto God off after 15 s.
  - Launcher: 「更新」→「更新补丁」 only shown when checkUpdate finds sound/give patch missing or game-embedded ZFrame dex != assets/inject.dex; otherwise chip 补丁已是最新. After sign shows dialog 「补丁已做好 / 即将跳转安装…」 2 s, then installer.
  - Not done: map select on the game's own 开始游戏 (needs lobby UI RE), costumes via mail (lobby/mail data is server-side).
- v1.32 (vc 33, core 33):
  - Device test v1.31: 30305000003 = 3级甲 (耐久 250, first try). 30304000004 = 2级头 (耐久 150). Base.apk has 0 .pak entries: paks come from Tencent IFS (assets/nzres.png = first_source.ifs) → PakScan useless, no longer auto-started.
  - 开始游戏选图: lobby 开始 → MatchUI; pressing its 开始 logs "UNZOnlineMatch::RequestStartSingleMatch add_maps_chose:N" then server msg 15001 is discarded (connector invalid, offline). Con.line watches that line → goMap: 1 老海岛, 3 新海岛(GameMapId 3), 4 雨林(GameMapId 4), 5 训练场 (not mapped), 4 s debounce.
  - 刷车: libUE4 has real exec SpawnVehicle(int type, FVector, FRotator) thunk 0x11df118 → impl 0x1102ca4 (World→AuthGameMode, GameMode+0x914 VehicleManager → 0x100600c). ENZVehicleType order: 0 None,1 Uaz_ClosedTop,2 Dacia1300,3 Boat,4 Uaz_MetalTop,5 Uaz_OpenTop,6 Buggy,7 Reserver,8 MotoCycle,9 MotoBike,10 MiniBus,...,17 Uaz_BulletProof. Player pos via "getall CapsuleComponent RelativeLocation" (line with BP_NZBRCharacter...). Spawn at +500 X, +150 Z; success = getall WheeledVehicle/NZVehicleMotoCycle bHidden lines > 0; tries 4 types.
  - 3级头 tries 30304000005..09; 一键开局装备 uses 30305000003.
  - 风衣: cycles Coat 30315000001..080, skips failed, toasts id.
  - Warehouse/mail: lobby has no server (ConnectorService connector invalidate), inventory never arrives — not done.
- v1.33 (vc 34, core 34):
  - Device test v1.32: 3级头 = 30304000005 (Helmet03, 耐久 230). Start-button map select works. 刷车 failed.
  - 刷车 verdict: PC SpawnVehicle(int ResId, FVector, FRotator) is FUNC 0x00c20401 (BlueprintCallable, NOT Exec) → console never reaches it. Even natively, it needs the vehicle data table at VehicleManager+0x2c0 (row name = "%d" of ResId); no vehicle table is loaded offline (no NewDataTable for vehicles, VehicleSpawnComponentList 0). Server GM RPCs (ServerSpawnVehicleById/AddBot/AddBotEx/AddSpecialBot/TransBotToPlayer/TestSpawnAirSupplyBox/QuickAirStrick/DirectStartMatchSkipPlane/TestModifyAvaItem) all have _Implementation = 0xb63a30 (bx lr) in PC vtable 0x4c92d64. → 刷车 and 放机器人 button removed.
  - Also v1.32 position bug: LOC regex matched ProneCollisionComponent (0,0,0) and the 2nd character; would need CollisionCylinder of BP_NZBRCharacter_..._C_0.
  - Maps: only ErangelMobile, ErangeMobileRenew, SavageMobile_4x4 have main maps; TrainingIsland_Runtime has pieces only.
  - Avatar categories (from texture paths): Coat 30315, CoatWithHat 30316, Overall 30323, WholeBody 30325, Shirt 30302, Trousers 30301, Shoes 30303, Helmet 30304, Armor 30305, Gloves 30306, Bagpack 30307, Facing 30308, Hair F 30213, BareMidriff 30232, Character F 10200000000. 找风衣 now cycles WholeBody→CoatWithHat→Overall 000..019.
  - UI: removed 地图 tab (map select via game start button), 进入大厅, 重载门, 刷车. Title "全军出击单机版", launcher header "补丁更新".
  - Lobby 选角色/仓库: lobby has no server (connector invalidate) — not feasible.
- 01:04 device test v1.33: WholeBody 30325 always 可放=-5 (backpack vfunc +0x6ec CanAdd false, before item-table lookup); CoatWithHat only 30316000007 ok; Overall only 30323000002 ok. Coat 30315 ok: 1-6,8-12,14-18,20-22,26,33,36,37,39,41-44,48,49,52-55,57-61. 30315000001 texture F_Jacket_Lon_01 (long jacket, likely 风衣). Offered: button cycles only known-good ids starting 30315000001.
- v1.34 (vc35/core35): user reports many coats say 与当前角色不符 (gender). 找风衣 cycles only known-good ids (39 coats from 30315000001 + 30316000007 + 30323000002). New 切换男/女(试验): set NZCharacter CharacterGender CharacterGender_Male/Female + set NZPlayerState PlayerGender PlayerGender_Male/Female (props at char+0x70c, PS+0x460), getall before/after logged.
- v1.35 (vc36/core36): user asked (01:10) for 游戏流程 toggles: 不上飞机 (stay on 出生岛) and 不刷毒. Also wants later to "改服务端" so warehouse has items / cars / bots (deferred; offline has no server).
  - Found: `set` works (FrameRateOption), but NZBRGameState reads 60/45 from table at init (NZBRGameState.cpp:304) after our 进图 tweaks → old TWEAKS never took effect (logs: phase 0→1 after 60 s, 1→2 after 45 s). Now applyFlow runs 3 s after the cpp:304 line: set WaitingPlayers/StartMatch/ResetStartMatch CountDownTime = 99999 (不上飞机) or 5.
  - Poison: ARestrictedAreaSystem.CurrRoundRestrictedAreaRepInfo at +0x2b8, struct field bStart at +0x41 (=0x2f9). Circle Tick 0xd97df0 skips everything when bStart==0. 不刷毒 runs `set RestrictedAreaSystem CurrRoundRestrictedAreaRepInfo (bStart=False)` every 5 s. Unverified on device.
  - Exec StopRestrictedArea/TestRestrictedArea on PC are stripped (b 0xb63a30); Debug_PoisonFog_* are on a non-console class. StartRestrictedArea = NZBattleRoyaleGameMode vtable(0x4c85d44)+0x604 → 0x100f0dc.
- 01:34 user (v1.35 test): gender switch, 不上飞机, 不刷毒 all had no effect. Logs: `set` on in-world actors is ignored (getall still 60/45, CharacterGender_Female, PlayerGender_None); `set` only took effect on NZSystemSettingData (config props, transient) → shipping build likely limits SET to config properties. A red long coat did get worn (screenshot).
- v1.36 (vc37/core37):
  - Removed 缩短等待, 游戏流程 toggles, 切换男/女. Panel: 无敌, 传送, 平板视角 (FOV exec: 默认/90/100/110), guns M416/AKM/AWM + search box (GUNS table), ammo 5.56/7.62/子弹3, 信号枪 (giveTry candidates) + 信号弹 20600000009, 特别的东西 (平底锅, 弩+箭, 苹果, 毒气弹, 烟雾弹, 燃烧瓶, 全套药品, 绝地风衣(男款) 30315000048).
  - Weapon ID rule: 201 + mid(01 Rifle,02 Machine,03 Shotgun,04 Snipe,05 SMG,06 Special) + 6-digit subtype index from ENZ*SubType enums (Rifle: M416,AKM,M16A4,SCAR_L,GROZA; Snipe: Kar98k,AWM,SKS,M24,MK14,MINI14,VSS; SMG: UMP9,UZI,Vector,Tommy; Shotgun: S1897,S686,S12K; Machine: M249; Special: CrossBow). Pistols 2020100000N (P92,P1911,R1895,P18C) and melee 2030100000N (EmptyHand,Pan,Machete,Sickle,CrowBar) and throw 2040100000N (Frag,Stun,Smoke,Molotov,Apple,PoisonGas) are inferred. Ammo EItemAmmo: 1 5.56, 2 7.62, 3 9mm, 4 .45, 5 12G, 6 .300, 7 Bolt, 8 5.8, 9 FLARE. Flare gun class ANZWeap_FlareGun exists, ID unknown.
  - Avatar coat 30315000048 textures M_JDYS_D (male; likely 绝地风衣). Male-only items give 与当前角色不符; body comes from DefaultRoleClotheDataTable fallback in InitAvatarProxy 0x10d7380 (female row). Male switch would need a libUE4 patch (not done).
  - App installer sets SessionParams.abiOverride="armeabi-v7a" via reflection to suppress Android 16 deprecated-ABI dialog (此应用与最新版Android不兼容). Unverified.
  - Exec catalog: TestSetMoveSpeed → stub (mov r0,#1); InfiniteAmmo/TestSwitchRole/StopRestrictedArea/QuickStartNextFlight stripped. Speed/jump need a libUE4 patch.
  - Open: which "更新弹窗" the user wants removed (libMczi window? needs confirmation).
- v1.37 (vc38/core38): user asked to remove the yellow startup popup 更新中(100) 模块其他错误 Err:107 (game's GCloud first_extract fails offline), make default role male (绝地风衣 30315000048 is male), and add 加速/高跳/不上飞机/不刷毒.
  - File patch in 更新 (Patcher UPD_OFF 0x15de030 = UNZUpdateUIView::ShowUpdateError → bx lr). Startup flow does not wait on it (core opens lobby itself).
  - New Mods.java: runtime /proc/self/mem writes (verify orig bytes): 不刷毒 0xd97df0 → bx lr; 不上飞机 hook 0xffc1e4 (SetGamePhase 0xffc1d8, phase byte at +0x71c) → cave in GM OnSpawnVehicleSelectChange 0x171e4b0 (uxtb ip,r1; cmp ip,#2; subeq sp,fp,#0x10; popeq {r4-r7,fp,pc}; mov r4,r0; b 0xffc1e8); 男角色 0x10d76d8 movw/movt 10200000000 → 10100000000 (male literal also used at 0x10d7d1c). Toggles saved in prefs zmods, re-applied when overlay attaches.
  - 加速/高跳: pawn from give cave (AddAvaItem P0x1x<buf> → [buf+4]=pawn), CMC=[pawn+0x34c], MaxWalkSpeed +0x164, Crouched +0x168, JumpZVelocity +0x128 (from FPropertyParams). Thread rewrites every 250 ms, rebasing when the game changes the value.
  - All unverified on device. Male body assets may be missing (pak index only showed Avatar/Male/Character/ dir).
- v1.37 test: 男角色 works (body 10100000000). 30315000048 not the 绝地风衣 the user means. 信号枪 candidate 20201000005 = 92式手枪 (pistol); flare gun is not in any ENZ*SubType enum → no item ID, dropped. 毒气弹 rejected (-6). Speed/jump never applied: AddAvaItem P0x1x<pbuf> never filled the pawn (cause unknown).
- Weapon enum order (UEnum tables): Rifle M416,AKM,M16A4,SCAR_L,GROZA; Machine M249; Shotgun S1897,S686,S12K; Snipe Kar98k,AWM,SKS,M24,MK14,MINI14,VSS; SMG UMP9,MicroUZI,Vector,TommyGun; Special CrossBow; Assi P92,P1911,R1895,P18C (+20201000005=92式); Melee EmptyHand,Pan,Machete,Sickle,CrowBar; Throw Frag,Stun,Smoke,Molotov,Apple,PoisonGas.
- v1.38 (vc39/core39): branding AW / 艾挽 全军出击单机版; volume +/- show/hide panel with animation (Window.Callback proxy); gun search box → category picker (CATS, each gun gives matching ammo); speed/jump SeekBars 1.0–5.0; pawn also taken from the give diag buffer; coat browser 上一件/下一件 over 30315 1-70, 30316 1-20, 30323 1-10; male toggle labelled 大厅设置; 正在进图 toast hidden unless abnormal. 不兼容 dialog = Android's 32-bit (deprecated ABI) warning, cannot be removed from the app side. Throwable arc not following pitch: not investigated yet.
- v1.39 (vc40/core40): 不兼容弹窗 — Android 16 AppWarnings.showDeprecatedAbiDialogIfNeeded has no "don't show again" flag, shows on every activity start, skipped only when ApplicationInfo has PRIVATE_FLAG_EXT_CPU_OVERRIDE (install with abiOverride). v1.36 set the hidden field by reflection (blocked). Now MainActivity.withAbi rebuilds SessionParams through a Parcel with abiOverride="armeabi-v7a" (A16 parcel order). Needs 更新 reinstall. Flare gun: "找信号枪" in 特殊武器 tries FLARE candidates, reads weapon name from game log (Con.lastWeap). Added R45 20201000006, 短管喷子 20201000007, QBU88 20104000008 (from pak mount points W<9-digit>_<Name>). Speed/jump slider up to 10x. Throw arc not following pitch also without FOV: not fixed yet.
- v1.40 (vc41/core41): left tabs = 功能 + gun categories (CATS) + 衣服 + 设置 (left column scrolls), right side shows the guns; 信号枪 button (probe found it on device, id persisted in prefs zmods "flare"); success toasts removed. Volume keys removed → five-finger tap opens panel (Window.Callback proxy on dispatchTouchEvent), ✕ in title bar closes it. 不上飞机 cave now maps SetGamePhase 1/2 → 4 (phases: 0 waiting 60s no UI, 1 StartMatch countdown, 2 plane, 3 ?, 4 landed/fighting, 10 end), so no countdown and no flight route. Speed fix: ACharacter CMC is +0x2f8 (Mesh 0x2f4, Capsule 0x2fc); 0x34c was wrong. New gun mods via pawn+0x890 CurrWeapon: RecoilScaleBase 0x584/0x588/0x58c=0, spread 0x578/0x57c/0x580/0x5ac=0, ammo 0x608/0x604 refilled to ClipAmmoCount 0x52c. Coat browser auto-skips rejected IDs and also tries 30115/30116/30123/30215. Throw arc still open.
- v1.41 (vc42/core42): 正常演习/非正常演习 = setFree: plane cave + poison + WAIT patch 0x1019774 (ldr r0,[r6,#0x38] WaitingPlayers 60s → mov r0,#1.0). 子弹 tab added. Coat page has 11-digit input (giveTry single id; -5 = 不能放/性别不符, -6 = 没有). Speed: MaxWalkSpeed is recomputed by game; now writes pawn CustomTimeDilation +0x58. Jump (CMC+0x2f8 → JumpZ +0x128) confirmed working by user. RecoilScaleBase 0x584/588/58c are randomized obfuscation multipliers (func 0xf07970 re-rolls to 1..10 if ≤0.5) → zeroing made recoil fly up; now heuristic-find component with RecoilParamTable +0x118 / CameraRecoil +0x17c / Spread +0xb4 and zero those. 真无限子弹: patch 0xefd104 sub r5,r5,r6 → mov r5,r5. Flare gun class = BP_NZWeap_FlareGun. 不上飞机 confirmed working.
- v1.42 (vc43/core43): 无限子弹 now also sets ClipAmmoCount 0x52c and ammo 0x608/0x604 to 999 (user wants count shown as unlimited).
- v1.43 (vc44/core44): 999 dropped. Game has Exec console cmd InfiniteAmmo on PlayerController (flags 0x20601, near DirectStartMatchSkipPlane/KillBot/BotNum) → button sends it (HUD ∞ expected, toggle). Patch 0xefd104 kept as fallback. Also noted Exec DirectStartMatchSkipPlane exists.
- v1.44 (vc45/core45): v1.43 crashed a few seconds into the Tencent splash. Suspected integrity check on ammo code → removed the 0xefd104 code patch entirely. Infinite ammo = InfiniteAmmo cmd + data refill only. If it still crashes, next suspect is the WAIT patch 0x1019774 (applied at startup when 非正常演习 is on).
- v1.45 (vc46/core46): v1.44 still crashed at splash (SIGSEGV fault 0x644 MainThread-UE4, before UE log opens). WAIT patch 0x1019774 no longer applied (only restored when turning off). 非正常演习 = plane + poison only.
- v1.46 (vc47/core47): v1.45 still crashed. Mods.restore no longer auto-applies anything at startup (no patches, no mover/askPawn); user re-enables toggles per launch.
- v1.46 confirmed: no startup crash. Root cause = auto-restore of toggles during splash (patches or mover's AddAvaItem exec). Never write memory / exec console during splash.
- v1.47 (vc48/core48), built 2026-11-16 by Hark (not yet tested on device):
  - 无限子弹: WeaponAmmoCount 0x608 / ServerWeaponAmmoCount 0x604 held at 999 (guns only, clip 1..200). The InfiniteAmmo console cmd is no longer sent.
  - 加速 (科技 style): pawn CustomTimeDilation +0x58 = 倍数, and Mesh(pawn+0x2f4) USkeletalMeshComponent.GlobalAnimRateScale +0x7c4 = slowMo/倍数, so only movement is faster and animations look slowed down (动作快慢 slider, default 0.75). 1.0 restores both.
  - 游戏模式: 非正常演习 split into 正常模式 / 不上飞机 / 不刷圈 (the last two can both be on). 正常 = both off + restore WAIT. Status chips at the top of the menu show the current state.
  - 无后座 fix (RE): the weapon params are struct NZWeapParamsTable (0x254 bytes; getter 0xf269c8), copied from DataTable WeapParamsTable_AllWeapons (row FWeaponParamsInfo, ParamTable at +8) into an anim/weapon manager (+0x18c4, GetWeaponParamsTable 0xec9b70). Layout: +0x4 SpreadAccuracyRatios, +0x5c CrosshairAccuracyRatios (Jump 0x3c, Prone 0x40, ProneWalk 0x44, Stand 0x48, StandWalk 0x4c, Crouch 0x50, CrouchWalk 0x54), +0xb4 Spread (Min 0x3c..IncreaseSpeed_Firing 0x54), +0x118 Recoil (Time 0x3c, UpMin 0x44 .. SideRange 0x60), +0x17c CameraRecoil (Base 8, Mod 0xc, Max 0x10), +0x1ac bUseCFMFireMode, +0x1b0 CFMParams (NZCFMWeaponParams: m_Max/MinInaccuracy 8/0xc, Disperse 0x10-0x1c, RecoilUp 0x28-0x30, Lateral 0x34-0x3c, RecoilScale stand/walk/jump 0x48-0x50, crosshair size/length base+mod: stand 0x5c-0x68, walk 0x6c-0x78, jump 0x7c-0x88, InaccuracyIncrement 0x8c). v1.46 never touched CFMParams, and its heuristic required a vtable at the table start. v1.47 scans the weapon, the pawn and every object they point to (0x1000 bytes each) for table copies by signature, every 5 s, and patches all copies. Log line: "后座参数表 … → N份".
  - 聚点: spread params set to 0, every accuracy ratio set to min(stand, crouch), CFM inaccuracy/disperse set to 0, walk/jump crosshair = stand, weapon 0x574-0x580/0x5ac set to 0.
  - 近战 merged into 特殊武器. Gun tabs replaced by one 武器 page with a category chip row.
  - New UI: dark card panel, 5 tabs (常用/武器/装备/衣服/设置), switches, gradient sliders with preset chips, and a 收起 button.
  - 绝地风衣: new CoatFind.java (read-only). Scans rw anon memory for UTF-32 "绝地风衣", then for pointers to it (item table rows), and collects ID-like values within ±0xC0 (int64 3010…3040e7, int32 short forms). It tries the candidates in order (fallback 30316000007, the only givable CoatWithHat) and stores the first that wears as prefs zmods "coat". Raw hex windows are logged.
  - 开局自动穿: Con brAt (NZBRGameState.cpp:304) + 4 s → Mods.onMatch gives coatId (default 30316000007).
  - 男/女 + other toggles: remembered again, but applied only 6 s after the first /Game/Maps LoadMap (lobby), never during the splash (Mods.lateRestore). Male still only changes the in-match body; the lobby preview model is not native (no other 10200000000 literal in libUE4).
  - askPawn now gives 1×5.56 (P20600000001x1) because id 0 never filled the pawn.
  - Build: build-tools r36 (apksigner API) + platform-34 android.jar + Temurin JDK21 aarch64; rel.py from v1.46.
- v1.48 (vc49/core49), 2026-11-16, fixes v1.47 (not yet tested on device):
  - v1.47 crashed after entering a match. Log: "后座参数表 … 查了133个对象 → 896份". The signature scan over 0x2400 bytes per object matched hundreds of non-table spots and zeroed them. Now only the copy at obj+0x18c4 (GetWeaponParamsTable) is used, for objects the pawn or weapon point to directly. Each match must pass a strict check: RecoilTime > 0, padding after bUseCFM = 0, and the known float ranges contain no NaN or pointer-like tiny values. At most 2 copies.
  - UI redone as minimal: near-black panel, top text tabs with an underline, a single grey status line, outline buttons, white selected state, thin white slider, small blue switch. No gradients, emoji or coloured chips.
  - User allowed pak decryption. Still declined (Tencent's encryption); the runtime CoatFind memory search stays.
- v1.48 device test (2026-11-16 04:20): no crash on entering a match. 999 ammo works. Recoil table found 0 copies: the anim copy is not pointed to by pawn/weapon directly; it is AnimInstance+0x18c4 (WeaponParamsTable belongs to the character AnimInstance class with BaseAnimTable/InitializeMovement). Jump ×4 crashed; two launches then died in the lobby a few seconds after load (mover/askPawn ran in the lobby on non-match objects). Speed + jump together: the jump looked dragged down (time dilation makes the arc 1/d as long). CoatFind found the FText objects for 绝地风衣 / 绝地风衣脱帽版 but no IDs nearby; 30316000007 (the current default) is a suit-type item (it replaces both shirt 30302 and trousers 30113), but the user says it is not the 绝地风衣. RenderQualityOption=Low is set but ignored (log: ApplyRenderQuality bCanApplyRenderQuality:0).
- v1.49 (vc50/core50), 2026-11-16:
  - Con.inMatch is set from LoadMap: lobby (RoleHall/FrontEnd) = false, BR maps = true, and Browse = false. The mover and askPawn only run in a match. Every write is validated: pawn/CMC/mesh/AnimInstance must have a vtable inside libUE4, and JumpZ is only written when it reads as the game's value (100–2000) or our last write.
  - Speed: CustomTimeDilation = ×N, but set to 1 while MovementMode (CMC+0x138) == 3 (falling), so jumps are normal. The mover polls every 40 ms while speed is on.
  - Sliders: 加速/高跳 run 0–10 in steps of 0.5; <1 shows 关; tick labels 0 and 10; no preset chips.
  - No-recoil/聚点: exact path pawn → Mesh +0x2f4 → AnimScriptInstance +0x750 → +0x18c4 (NZWeapParamsTable), checked once per AnimInstance. No heuristic scanning.
  - UI: width min(34% screen, 300dp). In the lobby only 游戏模式 (正常/不上飞机/不刷圈) and 男/女 are shown, with no tabs. In a match the tabs are 常用/武器/装备/衣服/设置, and there is no mode/role section. 一键三件套 = 3级头+3级甲+3级包, with no M416. Every gun gives ammo plus the full ATT set, skipping attachments already given this match (givenAtt is cleared on Browse). Dead code removed (GUNS search table, suitIds, needGive, mcziText, flowText).
  - Graphics: added cvars r.AutoAdjustRenderQuality 0, sg.Shadow/PostProcess/Effects/FoliageQuality 0, r.ShadowQuality 0, r.BloomQuality 0, r.MobileMSAA 1, r.DetailMode 0 (all names verified as UTF-32 strings in libUE4). FPS settings unchanged.
  - CoatFind v2: 4 passes. (1) UTF-32 exact 绝地风衣 / 绝地风衣脱帽版 plus ASCII FNameEntry "30xxxxxxxxx"; (2) pointers to the strings (FString.Data); (3) FText refs (O, O-0xC) where O = Data-0x14 (layout: ctrl{vt,shared,weak} + ITextData{vt, Lptr, Lctrl} + History{vt, rev, FString}), plus localized-string owners; (4) FText refs of those, and RowMap elements {FName idx, 0, rowPtr} with rowPtr within 0x300 before the FText, which gives the row name = ID. Also collects int64 IDs and 11-digit numbers in strings pointed to from the row. Unit test: zframe/test/CoatFindTest.java (synthetic memory) passes. Unverified on device.
  - Pak decryption is still declined.
- v1.49 device test (2026-11-16 04:51): entered a match without crashing. Set 高跳 to ×1 → Java crash `NPE View.mViewFlags in ViewGroup.dispatchCollectViewAttributes`. Cause: on slider ACTION_UP, refreshAny() → Activity.runOnUiThread runs immediately on the UI thread → fill() → removeAllViews removed the slider while its own touch was still being dispatched. The memory writes were fine (log: 移动组件 跳=450). The user also wants every launch and match to start at defaults, with nothing applied automatically.
- v1.50 (vc51/core51), 2026-11-16:
  - refreshAny posts to the main Handler (never synchronous). The slider no longer triggers a rebuild on release.
  - Nothing is remembered or auto-applied. Modes, gender and in-match toggles all start off on every launch (lateRestore removed). Every map change runs Mods.resetMatch() (speed/jump/recoil/聚点/ammo off). Auto-wear coat removed. Only the found coat ID is saved (the old placeholder 30316000007 is cleared). The coat page has 找绝地风衣 / 穿上.
  - CoatFindTest passes. Not tested on device.
- v1.50 device test (2026-11-16 05:00): no crash. CoatFind v2 found row names. Try-on 30318000000 → "穿上了", and the user confirmed it is 绝地风衣 (other candidates were 30317/30318/30319 and 30312000001/2; 脱帽版 candidates were 30319000004/5 and 30324000050/1). Recoil table at AnimInstance+0x18c4 was still "不像" (no-recoil not effective).
- v1.51 (vc52/core52), 2026-11-16:
  - Initial clothes = 绝地风衣 30318000000, via a libUE4 code cave. InitAvatarProxy calls 0xc62c54 at 0x10d7960 to copy the default-clothes TArray<int64> (TMap<uint8 gender,...> at obj+0x20, element 0x28, array at +0x10). That bl now goes to a cave at 0x171e4d0 (inside the unused GM OnSpawnVehicleSelectChange, right after the plane cave at 0x171e4b0..c7, 152 bytes). The cave calls the original, replaces 30301000001 (shirt) with 30318000000, and swap-removes 30302000000 (trousers). Applied once, 6 s after the first /Game/Maps LoadMap (Con.coatAt → Mods.builtInCoat). Emulated with unicorn on 6 lists, all correct, registers and stack preserved. Not tested on device. The lobby preview model is unchanged.
  - Logs removed: LogCap is now just android.util.Log; no logcat capture and no 下载/ZFrame export. CoatFind, AkDiag and CoatFindTest deleted. Diagnostic getall commands for the flow were removed.
  - Graphics: GFX_LOW / GFX_HIGH cvar lists (sg.* 0 vs 3, r.ShadowQuality, Bloom, MSAA, DetailMode, MaxAnisotropy, ViewDistance, Texture; names verified in libUE4). Persisted pref zframe/gfx_hi. Choosable in the lobby and in the 画面 tab; applied immediately and on every map load.
  - UI: 常用 page order is 保命 (无敌, 传送到地图中间), then 枪, then 跑跳 (sliders). Plain-language descriptions. Section headers have an accent bar. Tabs: 常用/武器/装备/衣服/画面. The lobby shows 模式, 角色, 画质 and the coat status.
- v1.51 device test (05:32): no coat at spawn. Speed + jump together jumps "like in place".
  - Coat RE: the 0x10d7960 copy (fp-0x108) is only a filter ("is this item a default?"). The real fill is at 0x10d882c, bl 0xc62d48(table r7, &fp-0x80 list, gender), which reads the table entry directly and appends defaults to fp-0x80. That list is given and logged as In Items.
  - Jump: speed is pawn time dilation, and in the air dilation goes back to 1, so the horizontal velocity is only the normal run speed.
- v1.52 (vc53/core53), 2026-11-16:
  - Coat hook moved to 0x10d882c (orig 4529eeeb → bl cave 271719eb). The cave at 0x171e4d0 calls c62d48 and then edits [r1] (shirt → coat, trousers removed). Unicorn emulation passes on 6 cases (game/re/emu_coat_cave.py).
  - Speed: on the ground→air transition, the CMC Velocity XY (+0xb4/+0xb8, UMovementComponent::Velocity from pn.py) is multiplied by speed.
  - Menu title is "艾挽 · 全军出击单机".
- User's roadmap (05:34): after v1.52, do the 服务端 work: fix the lobby front end, then vehicle spawning (刷车) and entering 训练营 (training ground resources). Also asked why the flare gun gives no airdrop (suspects GM permission). Not investigated yet; the guess is that the server-side airdrop logic is missing offline.
- 2026-11-16 05:51 服务端 recon (static):
  - The lobby talks over the GCloud Connector ("KeyLobbyMsg").
    - ConnectorService::SendRawBuffer(this, buf, len, x) is at 0xf950e0. The connector ptr is at this+0xfa054. If it is null, or its vtable+0x38 (IsConnected?) returns 0, it logs "connector is invalidate" and returns 0. Otherwise it calls vtable+0x1c(conn, buf, len, x, 0) (Write).
    - OnDataRecvedProc(this, conn, err) is at 0xf95b1c. It gets an object via this->vtable+0x128, then calls 0xfa9f40(obj, conn, err), which reads from the connector.
  - Plan: a fake in-process connector (our vtable: IsConnected=1, Write = copy to a ring buffer, Read = our reply buffer) stored at this+0xfa054. A Java thread decodes requests and builds replies. Recv is triggered from the game thread.
  - Step 1 is to capture real request bytes so the message format can be learned.
  - The 训练营 map pak is mounted (Content/Maps/TrainingIsland_Runtime/), but its map name is not in libUE4, so it lives in pak data and Lua. OnTrainingModeEntranceBtnClick is at 0x1375b68 (it calls a BP/Lua function by name via 0x3173a40).
- v1.53 (vc54/core54), 2026-11-16: 服务端 step 1 is capturing lobby requests.
  - The user's priorities: (1) the gray lobby character, 换角色 and 换脸/换头发 (捏脸); then (2) 仓库换装. The user OK'd a debug capture switch.
  - New LobbyCap.java: the lobby shows a 服务端调试 switch, 记录大厅请求, off by default and only allowed in the lobby.
    - Hooks KeyLobby SendMsg 0xf94a00 (records the msgId and TArray payload) and SendRawBuffer 0xf950e0 (records buf/len and the caller lr), both into a cave at 0x171e600 (180 bytes; generated by game/re/cap_cave.py, verified by emu_cap_cave.py).
    - The cave writes into a mmapped cache file zcap.bin (1 MB). Its address comes from /proc/self/maps and is patched into the cave's movw/movt.
    - Turning it off restores both hooks and saves a hex+ASCII text to Download/ZFrame/lobby-MMdd-HHmmss.txt.
  - The startup login send happens during the splash, so it is NOT captured (splash rule).
  - Not tested on device.
- 06:04 v1.53 capture test: 0 records, no crash (libUE4 0xabe5a000, buffer 0x91e78000). Lobby UI requests never reach KeyLobby SendMsg or SendRawBuffer, so they are blocked earlier by the offline state (UNZOnlineLobby bIsOffline / connector null). Next: drop LobbyCap and fill lobby role/avatar data locally, starting with the gray character. The user was told the switch will be removed next version.
- v1.54 (vc55/core55), 2026-11-16: fixes the gray lobby character (local hall-role data). LobbyCap and its capture switch are removed.
  - Finding from the 17:42 lobby log: lobby requests DO go through SendMsg (36001 ReLoginInfo, 22005, 30014, 37010), but only during lobby load. Clicks send nothing, which is why v1.53 captured 0.
  - UNZFrontEndUIController::InitAvatar (0x135afd0) gets roleID=0 from the data server (0x1877bfc). It calls FNZStoreAvatarHelper::InitAvatarClotheTry 0x1503208(this, &out, &flag, roleID on the stack). The list ends up [0], then AvatarProxyManager vtable+0xc = EquipItemsForHallRole. Logs: "roleID = 0", "Default ClotheID = 0", so the body is gray.
  - Patch:
    - 0x135b140: 3 instructions → movw/movt r0 + mov r1,#2, so the role is 10200000000 (F) or 10100000000 (M).
    - 0x135b158 and 0x135b170: mov r1,r7 → add r1,sp,#0x6c.
    - Coat in the lobby: the default-branch bl c62c54 at 0x1503680 → cave2 0x171e600 (push; mov r4,r0; bl c62c54; b 0x171e4dc, i.e. into the coat cave's replace loop).
  - Mods.setHall / hallAndReload run after builtInCoat (6 s after the first lobby load) and on the lobby 男/女 pick. They then `open` the lobby once so the avatar rebuilds.
  - Verified by emulation (game/re/emu_hall.py, plus the arg emulation inline). Not tested on device.
  - Next if it works: 换脸/换头发 (probably face/hair items in the same list), then 仓库换装.
- v1.55 (vc56/core56), 2026-11-16: user reported v1.54 showed "no change". Their screenshot was the 仓库 screen: gray male mannequin while the gender setting was 女.
  - InitAvatarClotheTry 0x1503208 has 8 callers (0x135b15c lobby, 0x13a91dc, 0x13aa6d0, 0x13d6ff8, 0x148fed4, 0x1493380, 0x1512984, 0x1523854), each paired with a get-role call 0x1877bfc. v1.54 patched only the lobby caller.
  - v1.55 patches the function entry instead, covering all 8: 0x1503230 (ldr sb,[fp,#0xc]) → b 0x171e610.
    - Cave: load the args; if roleID==0, use 10200000000 (F) or 10100000000 (M); then b 0x1503238. Gender lives in the movw/movt at 0x171e620.
    - Lobby coat cave2 (0x171e600 → 0x171e4dc) is unchanged.
    - The v1.54 0x135b140 patch has been removed.
  - Lobby menu now shows 大厅人物 status (ok / reason) so the user can report it.
  - Emulated (emu_hall2.py, 8 cases). Not tested on device.
  - If the character is still gray: the cause is in EquipItemsForHallRole 0x1207d40 (it checks each item through cc69cc and may reject items that aren't owned), or in 0x1877a1c.
- v1.56 (vc57/core57), 2026-11-16: v1.55 still shows a gray male mannequin in 仓库 and on the 角色 page. The face/hair list is empty except one 涂装 icon. User asked whether to try a different approach.
  - Decision: stop guessing and measure first. Added a lobby button 导出人物诊断 (AvatarLog.java). It filters avatar-related lines from the game's PUBGM.log (last 6 MB) and dumps the patch bytes at 0x1503230, 0x171e610, 0x1503680 and 0x171e600 plus hallState into Download/ZFrame/avatar-*.txt. It runs only when pressed.
  - Hypotheses to check with the file:
    - (a) Patches not applied, or the lobby didn't reload.
    - (b) InitAvatarClotheTry now sees role 10200000000, but EquipItemsForHallRole 0x1207d40 rejects the items (cc69cc ownership check / "Num Error ... bHasCharacter").
    - (c) The UI avatar assembler (IsUI:1) never runs, or fails loading skin assets.
- v1.57 (vc58/core58), 2026-11-16, new approach: fake store (inventory) data.
  - v1.56 device result:
    - Patches applied (bytes OK), hallState ok.
    - PUBGM.log on device has 0 avatar lines (the log is only 343 KB; verbose logging is off).
    - The 角色 page shows the right female model (default shirt) after toggling the game's own 男/女, but the lobby and 仓库 stay gray. Face/hair lists are empty.
  - RE:
    - UNZStoreDataServer (StaticClass 0x1890800) holds item records at +0x5c/+0x60 (0x38 bytes each). Filled by CacheStoreProp 0x1872b04(ds, TArray<proto 0x68>), which calls vtable+0x124 first (clear).
    - Proto → record mapping: id = p+0x10 → r+0; p+0x18 → r+8 (gid); p+0x20 → r+0x10 (owner); p+0x28 → r+0x18; p+0x34 → r+0x20 and r+0x34 (state; 2 = time-limited, checked against expire r+0x24 = p+0x38); p+0xc = submsg ptr (byte +0x1c → r+0x2c; null = default). r+0x2e..0x31 come from item-table lookups c45310/c45330/c4536c/c453a8 (r+0x2e = type).
    - GetCurrentRole 0x1877bfc: first record with type 1 and r+0x10 != 0.
    - Owned check 0x1877a1c: id match, and state != 2 or not expired.
    - Equipped clothes 0x1877ec8: type-3 records with r+0x10 == role r+8.
  - Patch:
    - InitAvatar 0x135b104 (mov r0,#1; r5 = store ds) → b 0x171e630 cave: push; r1 = Java direct buffer (movw/movt patched at runtime); if num != 0, CacheStoreProp(r5, r1); pop; mov r0,#1; b 0x135b108.
    - Buffer: current-gender role (gid 0x5a460001, owner 1), other role (owner 0), coat 30318000000 owned by the role (equipped), plus 8 more clothes unequipped.
    - Server-branch default fill bl c62d48 at 0x1503494 → coat cave 0x171e4d0 (drops trousers).
  - Emulated: emu_store.py (3 cases), Java armMov matches the assembler. Not tested on device.
  - Unknowns: whether role ids are type 1 in the item table; whether EquipItemsForHallRole accepts them.
- v1.58 (vc59/core59), 2026-11-16:
  - v1.57 device result:
    - Patch bytes OK, but the lobby and 仓库 are still gray and 仓库 is empty. The 角色 page is now also empty (male, no model).
    - Conclusion: InitAvatar 0x135b104 most likely never runs in the lobby, which also explains why the v1.54 patch had no effect.
  - Changes:
    - The fake-store hook moved to the GetCurrentRole entry 0x1877bfc (push {r4,r5,fp,lr} → b 0x171e630). All 9 avatar callers use it.
    - New cave (0x60 bytes):
      - counts calls at buf+0xc and saves ds (r1) at buf+0x10;
      - if enabled (num) and ds+0x60 == 0, calls CacheStoreProp(ds, buf) and saves the new ds+0x60 at buf+0x14;
      - then runs the original push and continues at 0x1877c00.
    - Items now start at buf+0x40.
    - On a gender change, Java pokes ds+0x60 = 0 so the next call re-caches.
    - The diagnostic prints Mods.storeInfo().
  - Emulated: emu_store.py (4 cases: empty/non-empty/disabled, regs + stack OK).
  - Next step depends on the counters: if calls are 0, the lobby uses another path; if calls > 0 but the count after caching is 0, the role type check fails in CacheStoreProp's lookups.
- v1.59 (vc60/core60), 2026-11-16, a pure measurement build:
  - v1.58 device result:
    - Hook bytes are in, but GetCurrentRole was called 0 times, even after opening 仓库 and 角色 and the 角色 page rendering the female model.
    - So none of the 9 InitAvatar-family callers run in the offline lobby. The lobby, 仓库 and 角色 avatars come from a different path; UIAvatarProxyManager is the candidate.
    - UIAvatarProxyManager natives: execEquipItemsForHallRole 0x16a60a4 → vt+0x314 = 0x1207d40; execGetDefaultCharacterClothes 0x16a63f0 → vt+0x33c; execShowLobbyDisplayType 0x16a6b34 → vt+0x304. FNameNativePtrPair table at 0x4da5a48.
  - Added Probe.java, a 人物探针 started from a new button. It puts call counters plus the last caller (LR) on 16 functions:
    - InitAvatar, InitAvatarClotheTry, CacheStoreProp, UpdateStoreCache, c62d48, EquipItemsForHallRole, 1205d60, InitAvatarProxy;
    - 4 UIAvatarProxyManager execs;
    - 1877a1c, 18739b8, 1877ec8, cc69cc.
  - How it works:
    - Each function's first 8 bytes become `ldr pc,[pc,#-4]` plus the trampoline address.
    - Trampolines live in the libUE4 text-segment slack 0x4a2d540..0x4a2e000 (filesz 0x4a2d528, page end 0x4a2e000; r-x is checked via /proc/self/maps). Spacing is 0x40.
    - Original bytes are verified before patching.
  - Emulated: emu_probe.py, all 16 pass (regs, stack, counter, LR). The trampoline hex comes from the real Java code.
  - The report goes into the avatar diagnostic.
- v1.60 (vc61/core61), 2026-11-16:
  - v1.59 probe result:
    - Probes installed 16/16 and worked: cc69cc was hit 4 times, from +eb6278.
    - Every avatar function was hit 0 times: InitAvatar, ClotheTry, CacheStoreProp, UpdateStoreCache, c62d48, EquipItemsForHallRole impl and exec, the UIAvatarProxyManager execs, 1877a1c/18739b8/1877ec8, InitAvatarProxy.
    - So the native C++ avatar paths are not used in the offline lobby/仓库/角色 pages. Likely Lua (slua LuaGet_*) or BP.
  - The probe list grew to 40, auto-picked from the FNameNativePtrPair tables:
    - x = exec thunks: HandleDisplayAvatar, ShowHallRole, GetDefaultCharacterID, ShowAvatar, TryDressItem, OnSexComboBoxChanged, RoleSettingBtnClick, FittingRoomBtnClick, OnRoleStateChanged, ShowWarehouseUI, OnClickTabRole, Hair/Face buttons, SetCharacter, LuaGet/Set_FaceIndex, and others;
    - ev = BP event wrappers starting `ldr r0,[pc,#n]; ldr r0,[pc,r0]`. These get a pcrel trampoline variant: `ldr r0,=abs; ldr r0,[r0]`, with abs computed at runtime.
  - Emulated: 40/40, including ev (compared against the original instructions).
  - User found a bilibili screenshot of someone's private server sending role/face items (默认男角色, 白人男2号, faces…) by mail with 领取. So faces/roles are inventory items delivered by the server, which confirms the fake-store direction.
- 06:52 v1.60 probe result: 40/40 installed. Only x RoleSettingBtnClick 1 and x OnRoleStateChanged 1 fired, both from +1b125e8 (generic UFunction invoke). cc69cc 4 from +eb6278. Everything else 0, including all ev wrappers.
  - So the role/face/仓库 UI is driven from BP/Lua and reads data we can't see natively.
  - User decided: build the self-hosted server (自建服务端).
  - Answered that the user does not need to send mail by hand: the server sends it automatically on login, or a Z-menu button triggers it.
  - Blocker: v1.53 showed lobby requests never reach SendRawBuffer (bIsOffline / connector null). Step 1 is to make the lobby think it is online, using the fake connector at ConnectorService+0xfa054.
- v1.61 WIP (not built; the user said stop and push at 07:04), 自建服务端:
  - Protocol: CSPkg{head{cmd, client_sequence_id, result, key_pkg}, body}.
    - The full .proto schemas are embedded in libUE4. game/re/protoext.py + protodump.py regenerate them into game/proto/ (gitignored).
  - Deposit: 13001/13002 GetProps (Prop: id, gid, owner_id, num, equip_position (EEquipPosition), prop_role_attr{sex, faceid, hair_id}).
    - 13003/13004 equip, 13005/13006 unequip. Mail 25001+.
  - Send: SendRawBuffer 0xf950e0 is replaced by the SEND cave (append to the Java request ring, return len).
  - Recv: ConnectorService Tick hook at 0xf92fb0 (reached every tick, even when not connected) → TICK cave feeds our reply records to vt+0x164 (0xf95298, recv: parse CSPkg + dispatch).
  - Caves live in the libUE4 slack at 0x4a2d540 / +0x100. Probe.java was removed.
  - Server.java answers 13001/13003/13005; other key_pkg requests get an empty success reply. It pushes 13002 on start, then reopens the lobby.
  - Emulated: game/re/emu_server.py (caves + protobuf decode with the game's own schemas) passes, plus a Java poll test.
- v1.61 (vc62/core62), 2026-11-16 07:20, built (not tested on device), 自建服务端 + 邮件:
  - Mail protocol (cs_mail.proto): 25001/25002 GetMailList (CSMailList{type, mails}), 25003/25004 ReadMails (type, indexs, del_flg → PlayerDataChange.prop_changes), 25005/25006 SendMails, 25007 CSMailPushNewMailNtf{mail_list}. Mail{recv_time, title, content, attachment{props}, read_flag, index}.
  - Server.java: on 「开启服务端」 pushes 13002 then a 补给 system mail (type 1) via 25007. 25001 → list; 25003 → marks read, adds the attachment props the deposit lacks (new gid, owner = current role), replies 25004 with the change, then pushes a fresh 13002. 25005 → success. Claiming twice gives nothing.
  - Attachments (GIFT): both roles with prop_role_attr {sex, faceid 1}, hair 30213000000–05, clothes seen in logs (30301000000/071, 30303000005, 30307000007, 30232000006, 30315000050/061, 30323000002, 30325000000–05), coat 30318000000. Face/hair/most cloth IDs are unverified.
  - Z menu (lobby): new 「发邮件」 button sends another 补给 mail (server must be on). Diagnostic shows the mail count.
  - Tests: zframe/test/ServerTest.java writes /tmp/pt/o.txt; game/re/emu_server.py decodes 25007/25002/25004/13002 with the game's schemas and runs both caves — all pass.
  - Build env (x86_64 sandbox): apt openjdk-21, build-tools r36 + platform-36 zips from dl.google.com unpacked to /workspace/work/sdk; release via rel.py from v1.60.
- 07:22 v1.61 device result: 服务端已开, but 请求 0, 回包 3, 游戏已收 0, 邮件 2. Neither cave ran: ConnectorService::Tick (vt+0x120, 0xf92c80) is never called offline, and the lobby sent nothing after the hook (lobby requests only go out on FrontEnd load after a match; `open` lobby did not resend).
- v1.62 (vc63/core63), 2026-11-16, built (not tested on device):
  - Recv moved to UNZOnlineUpdateService::Tick 0xfb0218 (same vt slot 0x120; logs "TickAppState" when delta>1 s, seen in the lobby at frames 937/938/10551 → runs every frame). Orig 8 bytes 104c2de9 08b08de2, return to 0xfb0220.
  - TICK2 cave (srv_cave.py TICK2, 152 bytes): reply header is now 32 bytes: +0 wp, +4 rp, +8 recv count, +12 ConnectorService object (Java fills), +16 UpdateService this, +20 frame count. Until +12 is set it only counts frames; then feeds each record to conn->vt+0x164 (0xf95298), restores r0/r1, runs the original push/add and jumps back.
  - Java finds the ConnectorService object: vptr = base+0x4c7deb4 (checked: vt+0x11c = Init 0xf925d0, vt+0x164 = 0xf95298). First looks in UpdateService's Outer (+0x18) fields, else scans rw anon memory. Object check: not CDO (flags&0x10), InternalIndex 1..0x400000, class and outer aligned pointers.
  - Diagnostic now prints 连接器 state and 大厅帧. If 大厅帧 is 0 the UpdateService tick does not run either; if the connector is found but 游戏已收 stays 0, check the cave; if 已收 > 0 but nothing changes, the lobby drops replies (offline/login state).
  - Tests: emu_server.py (TICK2: no delivery before conn, 2 records after, regs/stack match original prologue) + ServerTest + ScanTest (skips CDO and bare vptr values) all pass.
- 07:30 v1.62 device result: 连接器：UpdateService 没跑，大厅帧 0 → UNZOnlineUpdateService::Tick is not called in the lobby either (TickAppState only shows during app-state changes). Scan never finished/started because it waited for UpdateService.
- v1.63 (vc64/core64), built (not tested on device):
  - Recv hook moved to FEngineLoop::Tick 0xb665a8 (identified by strings t.IdleWhenNotForeground / r.OneFrameThreadLag / Media, size 0x1c44, vtable ref 0x4bbf0ac). Orig f04f2de9 1cb08de2 (push {r4-sl,fp,lr}; add fp,sp,#0x1c), return 0xb665b0. Cave TICK3 = TICK2 with that prologue.
  - findConn no longer waits for UpdateService: scans rw anon memory right away (int[] bulk read), diagnostic shows MB scanned and ms. 「帧」 = FEngineLoop ticks since install.
  - Tests: emu_server.py (stack after cave = original 9-reg push, fp = SP-8), ServerTest, ScanTest pass.
- 07:37 v1.63 device result: 连接器 found at 9c855000 (scanned 869 MB in 5.9 s), but 帧 0 and 游戏已收 0. Static check: AndroidMain main loop 0xb64870–0xb648e0 calls bl 0xb665a8 (FEngineLoop::Tick) every frame (callers() misses it; calls.pkl is incomplete). So the TICK hook itself is not taking effect, or the cave/buffer readback is wrong.
- v1.64 (vc65/core65), diagnostic build (not tested): report adds 自检 line read straight from game memory: bytes at TICK_AT/SEND_AT, first 16 bytes of the TICK cave, buffer address, frame counter and connector read via /proc/self/mem, and the /proc/self/maps lines for the slack and FEngineLoop::Tick pages; plus 帧 1 s after install.
- 11:50 v1.64 device self-check: hook bytes present at TICK_AT/SEND_AT (ldr pc → slack), cave bytes correct, buffer 9538b010, slack in r-x libUE4 vma, yet 帧 0. Conclusion: code patched at runtime after it has already executed never takes effect on this device (also explains 假仓库 取角色 0 次 and most v1.59/v1.60 probes = 0; only cold code like cc69cc fired). Hooks written before code runs (in-match code patched from lobby) work.
- v1.65 (vc66/core66, not tested): 启动早装 (pref early_hooks, default on, toggle in 大厅 page, takes effect after restart). Patches.start → right after libUE4 base found: Mods.preinstall() writes coat cave/hook, hall role hook, fake store hook, and Server.install() (TICK cave counting frames; SEND hook now goes through a gate at slack+0x200: rpl+24==0 → run original prologue, ==1 → server). builtInCoat no longer reloads the lobby when early install succeeded. Server.start() just enables (rpl+24=1), pushes 13002 + mail, reloads lobby. Diag shows 启动早装 line (等库 ms) and 钩子 line. emu_server.py tests the gate both ways.
- 12:00 v1.65 device: 启动早装 WORKS — lobby shows the female character wearing the 绝地风衣 (screenshot). Pressing 开启服务端 crashes immediately, no log.
  - RE: received packets normally go GCloud read thread (0xf95cd4: conn->vt+0x18 Read into a 0x24-byte buffer, queued at ConnectorService+0xfa058 TArray under lock +0xfa064) → ConnectorService::Tick 0xf92fb0 drains it (only if +0xfa054 connector != 0) calling vt+0x164(this, data, len, 0) per entry. So our call convention is the game's own. f95298 first calls 0x1097fa0([this+0xfa038], data, len) (decoder). v1.61's "Tick never runs" was a runtime hook → invalid evidence.
  - Likely crash: delivery into f95298 (decoder/state null offline) or a handler of our 13002/25007 payload.
- v1.66 (vc67/core67, not tested): delivery is manual. TICK cave adds a limit at rpl+28 (delivers only while recv count < limit); 「投递 1 条」 writes a synced step line to filesDir/zf_server_steps.txt then raises the limit by 1. Each launch renames the old file to _prev; diag shows 上次运行最后几步 + 这次. 开启服务端 no longer reopens the lobby. Diag also shows connector +fa038/+fa054/queue count and the queued cmd list.
- 12:08 v1.66 device: 开启服务端 OK, 投递 1 条 (13002 deposit with roles) → toast "游戏已收，没闪退", then the screen froze ~2 s and the game crashed. Nothing received in game. So f95298 accepts our packet; the crash comes after (likely the lobby rebuilding the avatar/deposit from our 13002, e.g. role prop_role_attr faceid 0).
- v1.67 (vc68/core68, not tested): queue order 1) 25007 mail push, 2) 13002 without role props, 3) 13002 with roles. New 「跳过 1 条」 (moves rp, recv count and limit without delivering). After each delivery, writes "收后 N 秒还活着" lines for 5 s to the step file.
- 12:16 v1.67 device diag: early install all ok (等库 1127 ms), 假仓库 取角色 1 次 (store cave now runs), frames run (4107). Connector found; ConnectorService +fa054 = real GCloud connector (non-null!). Delivered #1 = 25007 mail push → 游戏已收, alive 1 s, crash ~2-3 s later. Previous run: 13002 also crashed ~2 s later. So any delivered packet crashes ~2 s later, not a payload issue. UE log lines = 0 because the log is XOR 0x73.
- v1.68 (vc69/core69, not tested): diag adds logcat (crash+main, own uid: Fatal signal/backtrace/libUE4 lines) and the tail (80 lines) of the previous PUBGM*.log; log reading now XOR-decodes like Con. KEYS add Error/LuaErr/Mail.
- Next idea: deliver from ConnectorService::Tick (0xf92fb0, r8=this, real drain context) instead of FEngineLoop::Tick prologue.
- 12:24 v1.68 device: 开启服务端, then tapped into 仓库 (角色设定/试衣间 page) → instant crash, nothing delivered. Lobby (screenshot) shows female + 风衣 + shoes.
  - ROOT CAUSE FOUND: the v1.65 SEND gate's "open" branch (bne +3) jumped to gate+0x2c, where nothing was written (the send cave lives at slack+0, not after the gate). So once 开启服务端 set rpl+24=1, any game send (opening 仓库, or "SendPendingMsg after recv" after a delivered packet) ran into empty slack → crash. The emu test only checked that execution reached gate+44.
- v1.69 (vc70/core70, not tested): sendGate appends `ldr pc,[pc,#-4]; <send cave addr>`. emu_server.py now runs gate→send cave end to end (request written, r0=len, sp/r4 restored). New 「自动投递」 button (limit = max).
- 12:28 user asked for automatic recording (instead of pressing 导出诊断 after a crash). Overrides "diagnostics only on button press" for this case.
- v1.70 (vc71/core71, not tested): 6 s after the first lobby map load (same tick as builtInCoat), Server.autoCrashSave: if zf_server_steps_prev.txt exists (last run used the server) and was not saved yet (key lastModified/length in zf_crash_saved), export the full diag (with logcat crash lines + previous UE log tail) to Download/ZFrame/crash-MMdd-HHmmss.txt and toast. Steps are already logged automatically.
- 12:54 v1.70 device: NO CRASH (gate fix works). Mail 25007 delivered; game log "Recv msgid=25007 … NtfNewMail" with all attachment IDs; mail UI shows the items with icons/names (默认男/女角色, 女式马尾, 波浪头, 银色时尚短发, 白色牛仔裤, 蓝黄运动裤, 深蓝帆布鞋, 降落伞包, 武器商人上衣, 蓝白运动外套, 暗夜机能外套, 击友飞行员连体服; 30213000001/02 and 30325000000/01 = 未知道具). 领取 sent 25003 ×22 (type1 idx1 del1) — requests reach Java, but replies stayed queued because v1.67+ delivery was manual (limit 1). Also 37010 (pending reliable msg) got an empty success reply. autoCrashSave and logcat capture work (F/DEBUG backtrace lines visible).
- v1.71 (vc72/core72, not tested): auto delivery by default again (limit max; Server.manual=false). Start queues 25007 + one 13002 (with roles). 25003 pushes 13002 only if props were actually added. Removed the 4 未知道具 IDs from GIFT (now 19 attachments).
- 13:01 v1.71 device: auto delivery works, no crash. 25007+13002 received (CacheProp Num=8). Every 领取 got 25004 but our server logged "已领过": BUG — 25003 body 08 01 12 01 01 18 01 has read_indexs PACKED (12 01 01); readMails parsed packed bytes as a sub-message so idx never matched → empty change, mail not removed. 30325000002..05 = GetItemDataByID Failed (未知道具).
- v1.72 (vc73/core73, not tested): packed varint fix (Server.packed); tests now use the real device bytes. Removed 30325000002-05. Role equip in warehouse (13003 on a role prop) switches role flags and calls Mods.setMale. 启动早装 always on (toggle removed); Z-menu 女/男 row removed (use in-game warehouse).
- 13:09 v1.72 device: 领取 WORKS (25004 255 bytes + 13002 425 bytes), items in 仓库, hall try-on works. But equip/unequip blocked: "Cann't send reliable msg 13003/13005 before response" — 83001 (IsKeyMsg=0, waits 83002) was never answered.
- v1.73 (vc74/core74, not tested): reply empty success to ALL requests (cmd+1); warehouse persisted to filesDir/zf_props.txt ("gid id pos sex" lines), saved on claim/equip/unequip/allSkins; 「清空仓库」 deletes it. Initial coat removed (early coat hook not installed, STORE_ITEMS no coat, initProps no coat) → coat comes via mail. Backpack 30306000000 unequipped by default (user found it odd). Status line no longer shows 男女/大厅; coat/hall notes removed from menu. New 「全部皮肤入库」: scans rw anon memory for int64 301xx/302xx/303xx IDs (excl 30304/30305 and known-bad), threshold 2 occurrences if ≥30 such, adds up to 800, pushes 13002. Diag shows category counts.
- 13:22 v1.73 device: match shows default tee/ponytail (no warehouse outfit); backpack still on; clothes don't switch / overlap; hall gender switch fails; mail not all skins. Log: only 37010/25003/83001 reached server. 13003/13005 still "add to pending list" then discarded — the stuck reliable msg is 36001 (CS_INFO_RELOGIN_INFO_REQ, key, seq1) sent before server opened; pending is flushed only after a recv. Role switch = 13003 with prop=role + hair_prop(6). Hall equip list (EquipItemsForHallRole) included every owned item: game treats owner_id=role as "worn".
- v1.74 (vc75/core75, not tested): at start push 36002 seq1 key + 22006 seq2 + 30015 seq5 (empty success). Prop owner: clothes owner=role only when equipped, roles owner=1 if in use else 0. Role equip: unequip other-gender (301/302) items, equip hair_prop at pos 4. Backpack removed from fake store. All-skins: after connector found (+3 s) scan memory → 2nd mail "全部皮肤" (≤400 items not already owned); button removed.
- 13:32 v1.74 device: equip/unequip now reach server and get 13004 result 0; all-skins mail (400) claimed. But worn item disappears on returning to hall, match still default, gender change shows in match (male) but not hall, hair not changed. Cause: game's 13003 has no equip_position (only prop + owner_id field 5) → pos=0 → item recorded unequipped. Hall gender: early hook writes role id via movw/movt in cave (code), runtime rewrite not effective. 30306000000 is a game default backpack added when backpack slot empty (in "All ClotheID" but not "storeClotheTry").
- v1.75 (vc76/core76, not tested): slot = category (id/1e6%1000, e.g. 318, 213), same-category replace; old saved positions migrated. Hall role: cave HALL_GEN now "ldr r10,[pc,#-0x28]; nop" reading literal at 0x171e600 (data) → setMale pokes it at runtime; preinstall uses saved in-use role. Removed 投递1条/跳过1条/自动投递/发邮件 buttons (user request).
- 13:43 v1.75 device: hall male at startup (literal works), 13002 from server updates hall role/hair (storeClotheTry from 13002). But 13004 prop_list does NOT update the game's store cache → equipped item vanishes on return to hall. In match: offline InitAvatarProxy uses role + DefaultRoleClotheDataTable defaults only (store clothes ignored). Mask missing: skin scan threshold 2 skipped it.
- v1.76 (vc77/core77, not tested): after 13003/13005, read UE log tail for the EquipItemsForHallRole preview line before "ReqStore(Un)EquipProp propID = X" and set worn = that set (game already resolved conflicts), save, push full 13002. Role/hair change → push 13002. New spawn-outfit cave at 0x171e4d0 (hook 0x10d882c, early install): after default fill, overwrite TArray<int64> with wearBuf (role + worn, own items first, then hair, then defaults), truncated to TArray max (unicorn-tested). Skin scan threshold 1, cap 800.
- 14:07 v1.76 device: hall now keeps worn clothes (preview-line sync + 13002 push works). Spawn-outfit hook NOT installed: new cave 104 bytes vs original 152 → write() "原字节不符". Face paint = facial_tattoos_prop(7) in role 13003 (paintId logged in ReqStoreEquipProp roleId line), unhandled. User asks: rename 开启服务端→邮件全皮, send every sendable item (parachutes missing, PUBG mask missing), DP-28 (大盘鸡) missing, M249+DP-28 as 机枪 group, option to spawn items on body or on the ground under the player.
- v1.77 (vc78/core78, not tested): wear cave padded to 152 bytes with original tail; paint field 7 equip/unequip (slot 328); button renamed 邮件全皮; skin scan widened to 301xx–309xx (minus 30304 helmet/30305 armor) + diag histogram of all 11-digit ID categories (≥3 hits) to locate parachute/mask; 机枪 group M249 + DP-28 20102000002 (guessed; weapon enum only lists M249). Ground spawn NOT done (GiveItem/SpawnPickupBox are stripped; AddAvaItem only goes to backpack).
- 14:17 v1.77 device: spawn-outfit hook installed and works in match ("进游戏有效果了"); 邮件全皮 filled warehouse. Problems: equip/paint changes delayed ~15 s and conflict; after one match, back in hall it auto-starts matchmaking and can't cancel.
  - Cause: KeyLobbyMsg reliable queue only flushes pending msgs "after recv". 83001 seq8 was sent before the server started and never answered, so later 13003/13005/13020/15001 sat in the pending list until some other packet arrived. 15001 (START_ALLOC) is sent when starting a match but the offline game loads the map at once; its success reply arrived after returning to hall → game believes it is matching; 15003 (cancel) then got stuck in the same queue.
- v1.78 (vc79/core79, not tested): kick = every 300 ms push an empty msg 5017 (client→server TLOG ntf, no client handler) so pending msgs flush; 15001 → 15002 result=1 (fail); RPL_CAP 1MB→8MB (each equip pushes a 12KB 13002; buffer is linear, full = replies dropped); diag shows kick count and buffer use.
- 14:31 v1.78 device: equip/unequip now responsive; face paint visible in match. User: delete 衣服 tab; find red PUBG mask automatically; don't resend mails once claimed. Next: settings (画面) not applying immediately, then vehicle spawn/driving.
- v1.79 (vc80/core80, not tested): 衣服 tab removed; supply mail only if GIFT items missing (skin mail already only sends missing); memory scan also searches item names 口罩/面罩 (UTF-16LE + UTF-8), takes nearest skin ID within ±512 B (int64) or decimal text, saves to filesDir/zf_mask.txt, adds directly to warehouse every server start; diag lists each name hit with context.
- 14:43 v1.79 device: no mask. Name scan hits were a category label table ("口罩 面罩" UI tabs), nearest IDs were WholeBody 30325 (BAD) → wrong items added. User: fix 刷车/开车 now; correct intro text (first time 邮件全皮 to claim; afterwards just open server; outfits in warehouse).
- RE 刷车 (v1.80):
  - Offline match: VehicleManager [GM+0x914] has VehicleDataTable at +0x2c0 (non-null, log "SpawnVehicleByAllRandom, VehicleSpawnFactor" proves it), but TActorIterator over spawn points finds 0 (spawn points live in server-only levels) → no vehicles.
  - 0x1102ca4(PC, int ResId, FVector*, FRotator*) = SpawnVehicle impl: GetWorld (vt+0xa0) → [world+0x94] GM → [GM+0x914] VM → 0x100600c(VM, id, loc, rot, …). 0x1005400 = FindRow by FString::Printf("%d", id) → class at row+0x50 (loaded by 0x1005208). Missing row logs "Find Vehicle by Id … not exist" and returns null.
  - Actor root component at +0x128; component ComponentToWorld quat +0x130, translation +0x140.
- v1.80 (vc81/core81, not tested): 刷车 via the GiveItem cave: early hook changes cave `bne out` at 0x17169ec (8400001a→8700001a) to a trampoline at 0x1716c10 (ldr pc → SLACK+0x400). New cave (game/re/vcave.py, emu_vcave.py unicorn test passes) handles "AddAvaItem V<id>x<buf>". Java Veh.java: info call, reads DT RowMap (elements [DT+0x20], num [DT+0x24], stride 20: FName idx, number, row ptr), resolves FName via GNames found by memory scan ("None" idx0 + "ByteProperty" idx1 → chunk0 → chunk table), row names = ResIds; label from "/Game/..." FString in row. Spawns 6 m ahead (yaw from quat), +1.5 m Z; skips boats; cycles. Diag shows Veh.state + table info. Mask auto-add removed; BAD ids purged from saved warehouse; zf_mask.txt deleted. Lobby intro text updated.
- 15:02 v1.80 device: 刷车 stuck on "第一次刷车要读车表" (no result toast, menu status never refreshed; likely GNames scan too slow with HashSet<Long> boxing over 3 passes). 信号枪 spawned but toast said "刷出的不是信号枪" (weapon name not in game log). DP-28 20102000002 confirmed correct.
- v1.81 (vc82/core82, not tested): GNames scan pass 1 finds both "None" and "ByteProperty" entries; int open-addressing sets; progress in Veh.state + LogCap notes (candidate counts, chunk0, chunk table, timings); busy guard; refresh menu; toast table info. 信号枪: empty weapon name now counts as success.
- 15:22 v1.81 device: still stuck at "正在读车表". Diag: LogCap.note only goes to logcat (not diag!); skin scan took 228 s this session (findNames U16/U8 scan slow), so full-memory GNames scan is hopeless on device.
- v1.82 (vc83/core83, not tested): Names.fast() searches only libUE4 rw segments + following [anon:.bss] for the GNames global (word = chunk0 inline, or word → chunk table), verifying entry0 "None"(idx 0) and entry1 "ByteProperty"; full scan only as fallback. Veh notes now also Server.mark → appear in diag "这次". Removed findNames from skin scan (speed). Progress refresh in menu.
- 15:50 v1.83 device: all 31 vehicle ids (1-32 minus 7) spawn; some are only color variants. User next: 刷人机 → test gun damage → 刷物资 and 门.
- v1.84 (vc85/core85, not tested):
  - 载具: after 读车表, one button per vehicle type (groups by BP base name; color variants merged, press again cycles color). BP path now tried as FName (FSoftObjectPath) in row; names up to 200 chars. If names unreadable, one button per id.
  - 刷人机: vcave extended: 'B<n>x<buf>' calls ServerAddBotAt_Implementation 0x1115620(PC, int n=1, FVector loc r2/r3/[sp], FRotator [sp+4..]) — from tutorial PC class (vt 0x4ca3408, AddBotAt thunk vt+0xe40). It checks GameMode IsA NZBattleRoyaleGame (StaticClass 0x11bd4ec) then calls GM vt+0x5c4. Marker 0x5b04. Bot 8 m ahead facing player. emu_vcave.py covers B path.
  - PC ServerAddBot impl (vt+0xcd4) is bx lr (stripped) in all PC vtables.
  - Doors: strings show ANZSceneDoorManager (client) "Should never spawn On Server", NZDoorManager_C BP, StandaloneUnloadAllDoor prop. Not yet investigated.
- 16:05 v1.84 device: 刷人机 works (many bots spawned, 剩余 9), gun damage kills bots. When bots all dead (剩余 2 = player + phantom slot) match settles immediately (normal win). Ask user if they want 不结算.
- 16:19 user added: bots need equipment; auto spawn in match (no manual button); restore normal match start/circle timing (v1.84 starts too fast, first circle immediately); later ground loot + doors.
- RE (v1.85, bots standing still):
  - GM vt+0x5c4 (BR GameMode vtables e.g. 0x4c85d44) = AddBotAt 0x1012318(GM, PC, idx, FVector, FRotator): idx = 1-based index into GM+0x490 class array (0x28/entry), then AddOneBot 0x10112d0(GM, class, difficulty=1 hard-coded) → GM vt+0x5f8, vt+0x39c (spawn pawn), [ctrl+0x2b4]->vt+0x250 TeleportTo, ctrl vt+0x308 SetControlRotation. So v1.84's "int=1" is a class index, not difficulty.
  - TickAddBot 0x1013cc8 (GM vt+0x63c, DS bot list) uses 0x1014898(GM, difficulty) → class via BotDifficultMapping (GM+0x49c TMap; fallback PVPBotTypeIndex GM+0x930 → 0x1013b18) then AddOneBot(GM, class, difficulty). AddOneBot writes difficulty to AIController+0x3ec (EBotDifficulty 1..3), NumBots GM+0x314; when GS(+0x908) phase==0 and players+bots >= GS+0x728 it calls 0x100c4a8 (start).
  - NZPVPAIController (vtable 0x4bce5c0) OnPossess 0xc1bbd8 (slot 0x344): tree = PVPBehaviorTree +0x358 if bInitalUseBattleBehaviorTree(+0x3bb) or GM vt+0x588 (0x100fad0: GameState [GM+0x2e0] +0x71c > 3) else BornIslandBehaviorTree +0x354; NULL tree → only a log, RunBehaviorTree (vt+0x3c8) skipped. BrainComponent AController+0x324. SwitchToBattleBehaviorTree 0xc17fe0(this), ForceGiveAllEquipToAI 0xc1a534(this) (pawn +0x2c0, AIParamComponent +0x410 equip list).
  - Strong lead: device logs show "RecastNavMeshSizeBytes=4" for the persistent level navmesh → real navmesh lives in the server-only NavigationSubLevel, which Con.loadServerLevels skipped since v1.46. No navmesh = MoveTo fails = bots stand.
  - Match timing: TWEAKS forced WaitingPlayers/StartMatch countdown 5 s and WaitingSkydivingCountDownTime 1 s → fast start (user complaint).
- v1.85 (vc86/core86, not tested on device):
  - vcave 'A<diff>x<buf>' (game/re/vcave.py, 840 B + 8 literals, ends 0x4a2dca8): checks GM vt+0x5c4 == AddBotAt (else 5b05) → cls=0x1014898(GM,diff) → AddOneBot(GM,cls,diff) (null → 5b06) → GM vt+0x5f8/0x39c → teleport to buf+40, rotation buf+52 → if ctrl vt+0x344 == PVP OnPossess (else 5b07): record trees/brain/difficulty, flag1 ForceGiveAllEquipToAI, flag2 SwitchToBattleBehaviorTree when Brain null, flag4 force switch → 5b08. emu_vcave.py covers all branches (stack/regs restored).
  - Bots.java: 100 人局 (default on, not remembered): after match map loaded + 8 s, wait pawn, spawn 10–15 bots 6–25 m around player (batches of 3 / 1.2 s), then fill to 99 bots: phase <= 1 on island 30–150 m (game should take island bots onto the plane); phase > 1 wait until player Z stable then 50–300 m around player at Z+1.5 m. Stops after 5 consecutive failures. Phase read from [GM+0x2e0]+0x71c.
  - Menu: 人机 section (lobby + match): 100 人局 toggle, 简单/普通/困难 (EBotDifficulty 1/2/3), manual 再刷一个 uses 'A' too.
  - Con: removed countdown/skydiving tweaks (normal timing from game tables); MaxBotLimit 30 → 100; server level loader now also streams NavigationSubLevel (WorldMeshesLevel still skipped); diag: navmesh size lines, bot-related UE log lines, last bot info (class/controller/pawn/trees/brain), phase.
  - Not done yet (next): hide phantom slot (剩余 1 / win only when all bots dead), ground loot, doors, LAN feasibility.
- 16:47 v1.85 实测（用户）：一进图出生岛就刷满（剩余 100/101），太卡；人机白衣服没枪，原地跑来跑去跳（= 出生岛行为树在跑，行为树本身是好的）；航线还是很短（海上→P港）、毒圈节奏不是原版数据（用户：这是之前单机版的流程，缩圈时间和航线距离不是原始数据）。用户要：像正式服人机那样附近零散刷几个、有装备、会打人、不跳伞；不要一次全刷。用户打算找云服务器当服务端（手机当服务端太卡）。crash-1116-163650 是 v1.84 那局的自动记录，v1.85 那局没有诊断。
- RE：CurrentGamePhase = NZBRGameState+0x71c（pn 确认）；阶段 0 等人 1 开局倒计时 2 等跳伞 3 跳伞 4 毒圈 5 混战 6 结束。航线由 AAirTransporterSystem::CalcAirLineByRestrictedAreaInfo 按第一个安全区算（属性 MaxAirlineRadius / RandomRadiusForAirLine / AirLineOffset 在 0x4cc39xx、0x4cca7xx 两个类），毒圈来自 GameFeatureConfig/RestrictedAreaConfig（每圈 WaitTime/ShrinkTime/Radius/Damage）。日志行 "InitArea[%d], WaitTime..."、"AirLine: ..."、"CalcAirLineByRestrictedAreaInfo StartLoc..." 可读出当前数据。
- v1.86（vc87/core87，未实测）：
  - Bots.java 重写：阶段 < 4 不刷；开打后玩家落地站稳 15 秒开始，每 25~60 秒随机刷 1~3 个，玩家周围 40~110 米、高度 +3 米，一局最多 99。标志 1|4（给装备 + 强制战斗行为树）；3 秒、10 秒后用新 cave 'E<控制器>' 再给装备；诊断记 AutoGiveEquipComponent(+0x410) 开关(+0xd5)/条数(+0xb0)、AIEquipComponent(+0x3f0)、人物(+0x2c0)、Brain(+0x324)。
  - cave 'E'（vcave.py，904 B + 8 字面量，到 0x4a2dce8），emu_vcave 全过。
  - 诊断新增「航线/毒圈日志」（InitArea / AirLine / RestrictedAreaInfos / GamePhase 行），下一版按它改航线和毒圈。
  - 云服务器：游戏里只有客户端 libUE4.so，没有能在服务器上跑的服务端程序；人机/毒圈逻辑都在手机游戏进程里，云服务器暂时替代不了（已告知用户）。
- 16:55 用户：毒圈/航线按「全军出击」正式服；三张能用的图现在都是快速版。全军出击攻略（游民星空/17173 2018）：第一圈到第二圈 10 分钟、第二到第三 5 分 40 秒，和端游 2018 海岛数据一致（fandom The_Playzone 表）→ 用端游表；4x4 雨林用端游 Sanhok 表。
- RE（毒圈）：OnRestrictedAreaStart(0xe9bae0, UFunction) → InitArea 0xd9777c；系统对象 = 0xd96028：GetWorld()+0x98 → +0x348 表（16 字节/项：+4 对象数组，+8 个数，取最后一个非空）。对象 +0x33c/+0x340 DefaultRestrictedAreaInfos（0x54/项：+4 中心，+0x10 半径 cm，+0x14 WaitTime s，+0x18 ShrinkTime s，+0x1c Damage int），InitArea 拷到 +0x354/+0x358 并随机中心（0xd96364）。日志 "InitArea[%d], WaitTime..." 读 +0x354。
- RE（航线）：AirTransporterSystem：MaxAirlineRadius +0x328、RandomRadiusForAirLine +0x324、JumpRadiusScale +0x32c、AirLineOffset +0x330、FlightNumber +0x33c；CalcAirLine 参数结构 Speed 0/Height 4/RegionFly 0xc/RegionJump 0x20/CenterRadius 0x34/MaxAirlineRadius 0x38/JumpRadiusScale 0x3c/MaxDistToCenter 0x40/AirlineType 0x44。航线按第一个安全区算（CalcAirLineByRestrictedAreaInfo 0x118dc6c）。
- v1.87（vc88/core88，未实测）：Flow.java：对局地图加载后 5 秒起、有人物后，找毒圈系统对象，数值像毒圈（3~16 圈、第一圈 ≥500 米）才改 Default 数组的半径/等待/缩圈（8x8：2282m 420/300 …；4x4：1428m 210/240 …；第 9 圈起等 180 缩 15）；开打前每 5 秒检查；本局数组（InitArea 已跑）只记录不改。诊断：改前数据、本局数据、getall AirTransporterSystem 航线参数和 NZBRGameState 倒计时。FlowTest（临时文件模拟内存）通过。航线这版只诊断不改。
- 17:24 v1.87 实测：「好像没有用」。诊断（notes/device-tests/v1.87/）：雨林 4x4 本局毒圈只有 2 项（InitArea[0] 1500m 等 900 缩 180；[1] 半径 0.1），InitArea 在 GameState BeginPlay 后约 1.3 秒就跑完，v1.87 改 Default 太晚（而且只有 2 圈，过不了 ≥3 圈的检查）。航线 StartLoc (0,-325555) → EndLoc (-91622,-155389)，约 1.9 公里。倒计时 WaitingPlayers 60 / StartMatch 45 / WaitingSkydiving 1 / SkydivingTime 120；阶段 2→3→4 在同一秒（起飞）。用户另要：刷车、刷人机不放常用，放上面（→ 单独一页）。
- RE：RestrictedAreaSystem 字段：CurrRoundRestrictedAreaRepInfo 0x2b8、WorldExtent 0x330、DefaultRestrictedAreaInfos 0x33c、PresetCenterPointList 0x348、RestrictedAreaInfos 0x354、FirstCenterPoint 0x3b4、FirstRadius 0x3c0。飞机对象：Param 0x2b8（Speed +0、Height +4、RegionFly +0xc、RegionJump +0x20、CenterRadius +0x34、MaxAirlineRadius +0x38、JumpRadiusScale +0x3c、MaxDistToCenter +0x40、AirlineType +0x44）、StartLoc 0x300、EndLoc 0x30c、Dir 0x318、TotalAirlineDist 0x324、StartJumpLoc 0x328、EndJumpLoc 0x334、TotalJumpDist 0x340。FMemory::Malloc = 0x1989c98(size, align)（GMalloc vt+0xc），Free 0x1989de0。
- v1.88（vc89/core89，未实测）：cave 加 'M'（Malloc，lit9，emu 通过，cave 到 0x4a2dd20）；Veh.call 加 synchronized、Veh.malloc。Flow 重写：InitArea 后、阶段 4 前，用 Malloc 的 9 项数组换掉本局 RestrictedAreaInfos（第 1 圈中心/半径用游戏的，之后按端游比例缩、中心在上一圈内随机，时间按端游表，第 9 项 0.1 米缩没），飞机 Start/End/跳伞段沿原方向以中点拉长到 8 公里（4x4：4 公里）。菜单：新页「刷车人机」（载具、人机、毒圈状态），常用页去掉这两块。FlowTest（2000 次随机，圈都在上一圈内）通过。
- 17:31 用户追问：刷车、刷人机放好了没有；不要每次都手动点「读车表」。v1.89（vc90/core90，未实测）：进对局后 Veh.matchLoaded 自动读车表（等人物出生，每 2 秒试，最多 40 次），按钮改成「车表自动读取中…（读不出来再点这里）」。刷车人机页 v1.88 已做（顶部新页「刷车人机」）。
- 17:33 用户：删掉没用的介绍；「邮件全皮」没用（实际是邮件要领），改成「开启服务端」直接把补给和全部皮肤放进仓库，不发邮件。v1.90（vc91/core91，未实测）：Server.start 用 giveGift() 直接入库（不再 newMail），allSkins 扫到的直接 addProp + saveProps + 推 13002（v1.76 起 13002 推送能更新仓库缓存）；按钮改名「开启服务端」；菜单删掉大部分说明（模式说明、早装说明、人机说明、每局重置、画质说明、视野说明、车说明、毒圈说明），只留状态行。
- 17:37 用户：常用里加「刷在身上 / 刷在地上」。17:38 问游戏设置里改画质能否立即生效 → 答还不能，流畅/最高画质按钮先留，等设置里的画质能即时生效并实测后再删。
- RE 刷在地上：UFunction SpawnOnePickUp（exec 0x11fba30）→ 实现 0x119d884(this, ItemID{Type,TSID}, Pos x/y/z, Rot p/y/r)，this 只用来 GetWorld；要求 GameMode 是 ANZBattleRoyaleGameMode（0x11bd4ec 类检查），GM+0x509 为真走拾取管理器 [GM+0x524]（0xc7bea8，空就进待生成数组 +0x534），否则 GM vt+0x554 按物品名生成。ItemID 合成 = Type*10000+TSID（0xce36e8）。另：ServerDiscardItem exec 0xe67820（Inv, Count, DisableCapacityCheck, DisableValidationCheck，vt+0x348/0x34c）没用上。
- v1.91（vc92/core92，未实测）：cave 加 'G<Type>x<buf>'（TSID 在 buf+124，位置/朝向 buf+40..60，标记 5b0a，lit10 = 0x119d884，emu 通过，cave 到 0x4a2dd84）；Veh.ground（面前 1.3 米起，脚下高度 -80，最多 3 堆）；常用页「刷物资放在：身上（背包）/ 地上（面前）」，开地上时武器/装备页刷的东西都放地上。
- 17:42 用户：刷在地上直接放脚下。v1.92（vc93/core93，未实测）：Veh.ground 改 ahead(20,-80)，第一堆几乎正脚下，多堆随机散开 ±30 厘米；按钮改「地上（脚下）」。
- 17:52 v1.92 实测反馈（notes/device-tests/v1.92/）：刷车、人机要分两页；车按钮直接显示中文名，不要读车表；「剩余」固定从 100 开始，只在身边刷几个人机，打死一个少一个，玩家和服务端算一个，减到 1 结束游戏并放结算动画；航线还是一小段、进去毒圈像已经刷过一圈 → 航线要穿过地图中间、跨度大。
  诊断：v1.88 换毒圈数组生效了（开打时 CurrIndex 0 waittime 210 shinktime 240 = SMALL 第一行），但第一圈用的是游戏自己定的中心（这局 190448,19298 在地图东边缘），飞机期间就画白圈。航线原始数据很怪（4x4 原线在 x≈31 万的东边海上，v1.87 那局起点 X=0）。地图原点在正中：雨林 ±204000（截图量出来），海岛按 ±408000。诊断里 Flow 状态被回大厅（Browse→newMatch）清掉了，所以看不到航线改没改。
  GameState 属性（UProperty 表）：+0x774 PlayerLeftCount，+0x778 AIPlayerLeftCount，+0x71c CurrentGamePhase，+0x720 UpdateAlivePlayerTimerHandle，+0x728 NumPlayersBeforeCountDown。人机控制器 +0x2c0 = 人物。
- v1.93（vc94/core94，未实测）：
  - 菜单页签：常用 / 刷车 / 人机 / 武器 / 装备 / 画面。刷车页车按钮写死编号（吉普 1,17,20,28；敞篷吉普 4,11,21；篷布吉普 5,12,22；轿车 2,23；蹦蹦 6,14,24；面包车 10；皮卡 29,30；摩托 9,15,25；三轮摩托 8,16,26；快艇 13,18,19；大船 3,27；car_002/003/005/006 的中文名是推测），不再读车表（Con 不再调 Veh.matchLoaded，spawnGroup 只 ahead+call）。
  - 毒圈：数组第 0 项盖整张图（中心 0,0，半径 边长×0.75，等 60 秒，缩 1 秒），第 1 项起是表里的圈，第一圈中心在地图中间一带随机（整圈在图里），不用游戏自己的第一圈。FlowTest 2000×2 通过。
  - 航线：方向随机、穿过离正中 ±10% 边长以内、长度 边长×1.1（图边到图边），Dir 0x318 一起写，跳伞段同长。
  - 100 人局：Bots 记刷出来的 {控制器, 人物}，人物指针变了 = 死了；每 0.5 秒写 GameState+0x774=1、+0x778=99-已死；身边活着的人机少于 3 个就补到 2~4 个；死够 99 个时剩余写成 1 / 0，看游戏会不会自己结算（没做专门的结束调用，未实测）。诊断里记原来的两个值和已死人数，Flow 上一局结果不再被回大厅清掉。
- 18:27 v1.93 实测：「皮卡」刷出来是跑车（car_006 = 跑车）；上面人数在 1 和 2 之间来回跳；要求人数初始 100、修好结算动画。诊断：原来 PlayerLeftCount 2、AIPlayerLeftCount 0；毒圈 10 项改好；航线「没找到飞机（阶段 4）」（按偏移找的飞机对象在起飞前不存在 / 偏移不对）。
  RE：0x101a6e8 = GameState 数活人（遍历人物，+0x5a0 >= 1 算活，0xcd4054 判人机）→ 0x101a880 str r7,[sb,#0x774]（全部活人）、0x101a884 str r8,[sb,#0x778]（人机）。HUD「剩余」= +0x774。
  0x100fbd4 = BR GameMode 查结束：阶段 >= 3，CheckLeftPlayerCount(GM+0x3f8)，然后 vt+0x634（活着的队伍数）<= 1 → 0xe5c010 取赢家 → vt+0x510(GM, 赢家, 名字) 结算。服务端那个人物一直算一队，所以永远不结算。
- v1.94（vc95/core95，未实测）：
  - 剩余人数：进对局把 0x101a880/0x101a884 改成 nop（e1a00000），每 0.3 秒写 +0x774 = 100 - 已死人机、+0x778 = 剩余 - 1；不管自动刷人机开没开都数，手动「再刷一个」的人机也算。人机死 = 控制器 +0x2c0 换了或人物 +0x5a0 < 1。
  - 结算：剩余到 1 时把 0x100fc58（mov r0,r4）/0x100fc5c（blx r1）改成 ldr r0,[r4,#0x908] / ldr r0,[r0,#0x774]，让查结束用我们的剩余人数 → 游戏自己走胜利结算；新的一局进图时改回。
  - 航线：Con 解析日志「CalcAirLineByRestrictedAreaInfo StartLoc: X=…」的 4 个坐标，Flow.airByValue 在系统对象和它们 TArray 里的对象中按值找（起点+终点 4 个 float 完全相等），找到就改成穿过地图中间；跳伞段在后 0x80 字节内按值找；起飞后再每 0.2 秒找 25 秒（飞机起飞时才生）。
  - 车名：29、30 改叫「跑车」。
- 18:45 v1.94 实测：航线对了（穿过地图中间）。问题：坐飞机时已经有圈（整图已缩成第一圈，人在圈外）；人机没枪；人机在地图上显示成队友（2 号标记）；丢枪到地上闪退（崩溃日志里没有这次的记录）；大厅衣服在对局里脱不下来（官方也是大厅衣服对局里不能脱，待用户确认）；结算没试。18:46 菜单太复古、没逻辑。18:48 人数在 2 和 100 之间闪。
  原因（毒圈）：游戏的一项 = 当前圈，等 W 再用 S 缩到下一项；以前第 k 圈的时间放在第 k 项 → 第 0 项（整图 等 60 缩 1）1 秒就缩成第一圈。
  RE：其余写 +0x774 的地方都是构造函数/别的类；0x101a6e8 是唯一的数活人函数。HUD 可能另有来源（结构体 PlayerRemainsNum +0x58/+0x68），待诊断。队伍：没定位（GenericTeamId / PlayerTeamId 字符串有，偏移未知）。
- v1.95（vc96/core96，未实测）：
  - Flow.build：时间往前挪一项。第 0 项整图 等 120 缩 1；第 1 项整图，等/缩 = 第一圈的；第 i+2 项 = 第 i 圈，等/缩 = 下一圈的；最后 0.1 米等 9999。第一圈中心 ±(边长/2-半径)×0.3。FlowTest 2000×2 通过。
  - 人数：每 50 毫秒写一次（文件一直开着），诊断记录「被别处改掉 N 次（最后改成 X）」和 GameState / GameMode+0x908 地址。
  - 菜单：左边竖排分页（常用/武器/装备/刷车/人机/画面），选中白色圆角块；面板宽 400dp。
- 18:54 用户：大厅衣服对局里应该能脱（不认同「正式服也脱不下」）；「那个衣服在柜子里面怎么可以捡」意思待问。开局先有一段不显示读秒（阶段 0 等人 60 秒），之后才出 43 倒计时（阶段 1 45 秒）→ 要去掉前一段。
- v1.96（vc97/core97，未实测）：大厅加载 6 秒后打 WAIT 补丁 0x1019774（ldr r0,[r6,#0x38] → mov r0,#1.0，GameState+0x72c 等人时间 60→1）；setNormal 不再还原它。备用：对局里阶段 0 且 GS+0x72c==60.0 时直接写 1.0。v1.44 怀疑它在启动画面闪退，所以只在大厅加载后打。
- 18:57 用户：以前（正式服能玩时）对局里仓库衣服能脱、能捡。
- v1.97（vc98/core98，未实测）：syncWear 出生列表（0x10d882c 钩子 = 默认衣服列表，焊在身上）只留 角色 + 发型(113/213) + 默认衣服；自己穿的仓库衣服存 Bots.outfit，进对局拿到 GameMode 后 3 秒、giveSt==2 时逐件 AddAvaItem P<id>x1（和刷物资同一路径），诊断「仓库衣服：…」。若 AddAvaItem 发的也脱不下，下一步改成刷在脚下让玩家捡。
- 19:08 v1.97 实测（notes/device-tests/v1.97/）：开局 60 秒等人已去掉（日志 WaitingPlayersCountDownTime=1）。仓库衣服过一会才出现（要一进去就有）。飞机上经常没降落伞（摔死）。读秒一出现人数就 2/100 闪：诊断「被别处改掉 143 次，最后改成 1」→ 用户：改不了就用真实人数。摔死没结算画面。人机在地图上是队友：日志 "IsTeammateByUin! Found!"（NZBRTeamInfo 0x102281c：TeamInfo+0x300 成员数组（0xc/项，PlayerState 指针），比对 PS+0x3b0 的 int64 Uin）、"Cannot Rescue Teammate InWater[BaseMan_C_2]"。丢枪闪退：最后一行是丢 AKM 后 "Load NewDataTable[CommonItemDataTable]"，没有对应 tombstone（原因仍未知）。
  用户下一步要：局内自动刷物资、门看不见、车、随机人机带随机装备和枪。
  RE 门：ANZSceneDoorManager 客户端门管理器（BeginPlay 0xce8ea4 只打日志），StandaloneUnloadAllDoor bool 在门管理器 +0x380；服务器门由 [DS Load] 0xc7b7b8 加载，客户端门靠 ClientSyncBatchDoorData。服务器关卡：这张图（海岛2 Renew）日志里一直没有 "StreamingServerLayer Add"（0x117bb7c，按 8 个层名加关卡，日志要 verbosity≥5），所以 loadServerLevels 从没跑过 → 物资/门很可能都在没加载的服务器层里。待查。
- v1.98（vc99/core99，未实测）：
  - 人数不改了：不 nop 数人代码、不写 +0x774/+0x778、不改结算（用游戏自己的），诊断只读。
  - vcave 加 'I<人物>x<buf>'（game/re/vcave.py，emu_igive.py 通过）：buf+128 Type、+132 TSID、+136 数量 → ADDCHECK 0xccecbc + GIVE 0xcd3bdc，标记 5b0b/5b0c/5b06；Veh buf 128→256 字节；Veh.giveTo。
  - 人机配枪：每个人机刷出 1.5 秒后随机枪+子弹 30~90，一半概率头/甲（30304/30305 00000 1~3），1/3 概率药；诊断「人机配枪」。
  - 人机队友：PlayerState（ctrl+0x2c4，否则 pawn+0x2c4）+0x3b0 Uin 为 0 时写 900000+序号；诊断记 Uin/玩家 Uin/TeamInfo(+0x340)。
  - 仓库衣服：每 0.3 秒查人物，一出生就用 'I' 直接发到玩家身上（不再等 3 秒）。
- 19:30 v1.98 实测：丢枪又闪退；人机摔死（落地检测没排除飞机：飞机上高度不变 → 人机刷在 ~200 米空中）；人机摔死后阶段 4→10，直接弹大厅无结算（真实人数降到 1 → 游戏判结束）；开局仍等约 60 秒（日志 WaitingPlayersCountDownTime=1 但阶段 0→1 还是 50~60 秒，真正的等待来源未知；另有 bEnableQuickStartMatch ResetStartMatchCountDownTime[5.00] 但阶段 1 仍 45 秒）；门/物资/车没有（还没做）。
  人机配枪：'I' 成功（5b0b，能放 1，返回 1），但人机手里没枪 → 进背包不等于拿在手上，要找 AI 切武器/装备武器。Uin：人机本来就是 3、玩家 1 → 队友标记不是 Uin 的问题，要查 TeamID（人机队伍 PS+0x340 = b07b006c，和玩家比）。仓库衣服 'I' 发成功。
- v1.99（vc100/core100，未实测）：人机只在玩家高度 <120 米且站稳 15 秒后刷；恢复 nop 游戏数人（不写人数，避免弹大厅和闪）。
- v1.99 补：第一批人机落地站稳后随机 30~120 秒（版本号不变，用户要求）；写 HANDOFF.md 交接。
- v1.99 补：每局人机随机 15~30 个、每批 1~2 个（版本号不变）。
- 19:52 v1.99 实测（用户截图）：坐飞机时地图上已经有圈、人在圈外（「毒圈还是会刷」）；还是经常没开伞摔死。
  RE（跳伞）：UNZBRCharacterSkydivingComponent 构造 0x1121ed8：+0x25c ForceParachuteOpenHeightRate=0.23，+0x2c0 SkydivingStartHeight=150000（1500 米）。
  0x1127de0（vt+0x228）= |离地距离 +0x280 / StartHeight| 夹到 1；0x1128498 每帧向下射线（20 km）把离地距离写 +0x280，比例 <0.23 且阶段 2（自由落体）→ GotoStage(3) 强制开伞。
  这个单机版航线 Z=20000（200 米），离地太近，强制开伞时动画没放完就落地。
- v2.00（vc101/core101，未实测）：Flow 把航线起终点和跳伞段的 Z 写成 100000（1000 米，PLANE_Z）；开局整图圈半径 边长×0.75 → ×1.5（WHOLE），防止坐飞机时地图上出圈。
- 20:00 用户：开局本来应该没圈；安全区老刷到地图外（不是地图中间）。
- RE（换圈 0xd97d00）：当前圈序号 r7 < 总数/2 时调 MakeLowPoolPlayerPoisonFogCenter 0xd99cf4（按玩家位置定圈心），否则调 RandomCenter 0xd96364（上一圈中心 ± 本项 +0 CenterPointOffsetMax 随机，+0x10 半径）。单机只有一个玩家且还在飞机上（常在图边/海上）→ 前几圈圈心跟着跑到地图外；我们算好的中心被覆盖。
- v2.01（vc102/core102，未实测）：Mods.POOL 补丁 0xd97d68 bge→b（040000aa→040000ea），所有圈都走 RandomCenter；Flow.build 每项 +0 CenterPointOffsetMax 写 0 → 圈心 = 我们算的（第一圈在地图正中一带，之后在上一圈里）。Flow.run 开头打补丁并记日志「毒圈不按玩家位置定圈心：ok」。
- 20:22 用户：出生岛刷 3 级套 → 跳伞后背包里不显示（身上显示有）；再刷一次 3 级套直接闪退。要修刷门/刷物资，问能否局域网联机。
  崩溃（notes/device-tests/v2.01/crash-1116-200813.txt）：GameThread SIGSEGV SEGV_ACCERR，fault addr …fe78，栈顶 libc realpath+0 ← java.io.File.getCanonicalFile（GameThread 上的 Java，调用者未知）= GameThread 栈溢出；连续 4 次同样。和 v1.94「丢枪到地上闪退」可能同源：身上已有头/甲时再塞一件 → 换装、旧的扔地上（生成拾取物）。闪退那局的游戏日志被轮转掉了，没录到。
- v2.02（vc103/core103，未实测）：头/甲/包刷到身上每局每个部位只允许一次（GameOverlay.gearOnce，换局清空；刷在地上不限），第二次提示「身上已有再刷会闪退」。
  待查：生成地上拾取物是否本身就会崩（让用户用「地上（脚下）」刷一把枪试）；刷物资计划依赖这个。局域网联机：客户端版 GetNetMode 有 NetDriver 就返回 Client、DS 代码不在包里，暂不可行。
- 20:27 用户：「地上（脚下）」刷不闪退（之前那次可能是没背包时刷枪+配件再丢）。要做局域网联机。
  RE：libUE4 的 TCHAR 是 UTF-32。有服务端网络代码：UWorld::Listen（"LoadMap: failed to Listen(%s)"）、"%s IpNetDriver listening on port %i"、UNetDriver::InitListen、服务端 "NMT_Login: Invalid URL"、UNZIpNetDriver（D:/QJCJ/Game/Source/NZNetHandle）、"Listening for LAN beacon requests"。之前说「做不了」是错的，没核实。单机开局是 LogNet: Browse 地图（不带参数）。
- v2.03（vc104/core104，未实测）：大厅页加「局域网联机（实验）」：显示本机 Wi-Fi IP；开房（海岛/雨林）= Con.exec("open <地图>?listen")；加入 = open IP:7777。新 Lan.java 收网络相关日志行，诊断里「联机日志」。
- 20:52 用户（v2.03 实测）：开房没反应。日志：Browse ...?listen → TravelFailure CheatCommands「Console commands were used which are disallowed in netplay」。只有一台设备，想把房主放云电脑/虚拟机 + 内网穿透。要求：UI 改名「全军出击联机版」；菜单做成可收起的悬浮球；大厅不放刷人机开关（单机默认刷）；三张图；游戏设置里选画质立即生效。
  RE：检查在 0x2e098bc（UEngine::Browse）0x2e0a074：ldrb 作弊标志 → beq 0x2e0a10c；不为 0 且 URL HasOption("Listen") → 失败。补丁 0x2e0a084 2000000a→200000ea（Lan.unlock，开房/加入前写）。
  画质 RE：UNZSystemSettingData::ApplyRenderQuality 0xca14e0（this+0x7c RenderQualityOption，+0x930 bCanApply，+0x58 每档命令表，CalcRenderQuality 0xca21e0），只在 PreLoadMap 0xca120c 调；设置界面提示 SystemSetting_NextMatchWork。ERenderQualityOption: Low/Middle/High/VeryHigh/Ultra。
- v2.04（vc105/core105，未实测）：Lan.unlock 去作弊检查；开房三张图（老海岛 ErangelMobile_Runtime/BR-Erangel_Mobile_Main、新海岛、雨林）。Gfx.java：引擎就绪后每 3 秒 getall NZSystemSettingData RenderQualityOption，变了就发对应档位 sg./r. 命令（第一次不发）；删掉我们自己的画质按钮，runTweaks 不再发画质。大厅：去掉「自动刷人机」开关，只留难度。标题「艾挽 · 全军出击联机版」。「–」收成可拖动的悬浮球「艾」，点球展开。联机日志过滤掉 AvaListener 刷屏。
- 21:08 用户（v2.04 实测）：开房还是没反应（日志：补丁 ok，仍 TravelFailure CheatCommands）。要求：大厅去掉难度和刷人机（联机版不刷人机）；不要「艾」悬浮球，要整齐的苹果风格浅色菜单；服务端自动开、不显示开关；日志放最下面；对局里不要开房；保留单机版（单独一个入口）；房主和客户端进局的菜单要不一样。
  RE：作弊标志 bss 0x5359ad8（GOT 0x512753c），只有 3 处引用：0x2a110c8 strb 置 1（控制台 Exec 0x2a11068 里，mov r2,#1 在 0x2a110b8）、Browse 0x2e0a078、PendingNetGame::InitNetDriver 0x2bffb64（检查 beq 0x2bffb78）。v2.04 的运行时补丁写了却没生效，怀疑 Browse 代码已在指令缓存里。
- v2.05（vc106/core106，未实测）：Lan.preinstall 启动时（早装）写三处：0x2a110b8 0120a0e3→0020a0e3（不再置标志）、0x2e0a084 beq→b、0x2bffb78 1a00000a→1a0000ea；开房/加入前再清标志字节。Lan.role 0 单机/1 房主/2 客户端，回大厅清零；房主/客户端 Bots.auto=false，回大厅 true。服务端回大厅 2.5 秒后自动开。菜单：浅色苹果风（F2F2F7 底、白卡片、系统蓝、绿色开关），标题「全军出击联机版」，「–」收成标题小条（去掉悬浮球）。大厅：分段「联机版/单机版」；联机版 = 开房三张图 + 加入（IP 输入）；单机版 = 游戏模式 + 清空仓库；最下面「日志」导出诊断。对局分页：单机 常用/武器/装备/刷车/人机/画面；房主 人机换成联机；客户端只有 联机/画面。
- 21:17 v2.05 实测：开房不再被 CheatCommands 拦，开始加载但闪退（用户说）。发来的 crash-1116-211627 是启动时自动存的、记的是 v2.04 那次，不含这次闪退；已让用户重开游戏发更新的 crash 文件。三处启动补丁 ok。
- 21:19 v2.05：日志确认「GameNetDriver NZIpNetDriver_0 IpNetDriver listening on port 7777」，开房监听成功；之后在同步 LoadMap 里闪退（21:19:28 后），导出拿不到。已让用户重开在大厅导出（带上一次 backup 日志）。
- 21:22 v2.05 开房：listen 7777 成功，游戏日志停在 21:19:24.714「Bringing up level for play」（NZStreamingLevelsPolicyServer 已建），之后进程没了，没有 tombstone（怀疑内存爆/被杀，未证实）。同时自动开服务端在点开房后 1 秒（21:19:20）启动，加载中扫连接器。
- v2.06（vc107/core107，未实测）：联机时（Lan.role≠0）进图不发调整命令、不跑 Flow/Bots；开房后或已在联机里不自动开服务端；开房/加入后每秒把 VmSize/VmRSS 写进持久步骤文件（下次导出「上次运行最后几步」可见）；联机日志也写步骤文件；去掉每次启动自动存 crash 文件；菜单默认收起。
- v2.07（vc108/core108）：标题栏改为左边三角方块（▶ 收起 / ▼ 展开）、标题居中、版本号移到状态行和画面页；收起宽 210dp。含 v2.06 全部改动，未实测。
- v2.08（vc109/core109）：用户给了 沐岑国体注入（root+64位 ImGui，igce 国际服体验服，不用），要类似菜单；用户没 root 拿不到截图 → 做 ImGui 浅色风：灰色标题栏条、小圆角 8dp + 细边框、三角方块 26dp。未实测。
- 21:40 v2.08 开房（Savage）：心跳显示加载后约 6–7 秒进程消失，VmRSS 约 760 MB、Swap 8 MB → 不是内存/手机配置问题。游戏日志停在「Bringing up level for play took」；系统日志没有新的 Fatal signal（怀疑 Bugly 自己吞了信号）。
- v2.09（vc110/core110，未实测）：开房/加入时 Server.quiet() 关 SEND 门 + 停投递（排除服务端钩子）；诊断加 logcat system 缓冲、6 万行、CrashReport/Bugly 关键词；扫游戏私有目录和 Android/data 下 3 小时内的崩溃文件（crash/tomb/native/dump/bugly）并附可读字符串。
- v2.10（vc111/core111，未实测）：用户嫌每次手动导出麻烦 → 恢复自动存 crash-*.txt，但只在上次联机加载中途闪退时（步骤文件最后一行是开房/加入加载心跳）存一次，平时不存。含 v2.09 全部改动。
- 21:47 v2.10：自动存 crash 生效。两次开房（Savage）同样在「Bringing up level for play」后死；v2.09 静默服务端钩子后照样死 → 不是服务端钩子。Bugly 现场在 /data/data/com.tencent.tmgp.pubgm/app_bugly/tomb_<ms>.txt（约 9.5 KB，每次闪退一份），CrashReport-Native 21:46:34 有记录。
- v2.11（vc112/core112）：诊断把最新 2 份 app_bugly/tomb_*.txt 整份打出来（闪退调用栈）。
- 21:52 v2.11：Bugly tomb 拿到。所有开房闪退同一处：GameThread SIGSEGV fault 0x4，pc libUE4+0x276f058（ldr r1,[r0,#4]，r0=0），函数 0x276edfc（vtable 槽 @0x4ed9af4）= UNavigationSystem::GetDefaultNavDataInstance：FNavDataConfig "Default"（半径 35、高 144、步高 -1、0.5、范围 50/50/250）→ vtable+0x124 CreateNavigationDataInstance 返回空 → 设 RF_Transient 崩。客户端包里导航网格是空的（RecastNavMeshSizeBytes=4）。
- v2.12（vc113/core113，未实测）：启动时把 0x276f058..060 三条换 nop（Lan.NAV），返回空不再崩。之后可能在别处用空导航崩，看下一份 tomb。
- 22:10 v2.12 实测：房主进图成功（新海岛，出生岛→飞机→落地→捡到头盔背包），没闪退，导航修复有效。约 2 分钟后弹「网络异常无法收到结算数据」：NZGameMode.cpp:2452 All players have quit game! NumPlayers[1]（AllQuitEndGameProtectTime 120）。函数 0xc898e0：对局中且 vtbl+0x3f0()==0 → 结束；房主本地角色无连接算 0。飞机仍 200 米（联机跳过 Flow）。
- v2.13（vc114/core114，未实测）：分三个包 ZFrame-v2.13-单机/房主/客户端.apk（Ed.ED 0/1/2，build 时 sed 改）。房主/客户端版不装 setStore/出生衣服/自建服务端，大厅只显示开房或加入；0xc89960 bne→b（Lan.QUIT，全员退出不结束）；房主进图跑 Flow（1000 米航线+毒圈修正），不刷人机。客户端刷东西不生效（服务器权威），要房主刷。
- v2.14（vc115/core115，未实测）：房主版刷东西固定刷在脚下（不显示身上/地上选项）；装备页加「吉利服」：槽位推断 Ghillie=30309（EAvaItemSlot 顺序对上 30304 头/30305 甲/30306 手套/30307 包），编号按 30309000001..06、…000 试；刷身上自动判断，刷地上每点一次换下一个。新增 zframe/build3.sh 一次打三版。
- v2.15（vc116/core116，未实测）：客户端版「常用」只留无后座/防抖聚点/无限子弹/加速/高跳（clientMain），客户端分页 = 常用/联机/画面。新增「衣服」页（页号 7，房主/单机）：顶上「刷绝地风衣」(30318000000)，下面 Server.scanSkins 只读扫内存得全部服装编号，按分类（30Xyy：01 裤子 02 上衣 03 鞋 06 手套 07 背包外观 08 面部 09 吉利服 12 帽子 …，301 男/302 女）分组，名字 = 分类+序号（无中文物品名表），每页 30 个，列表存 filesDir/zf_clothes.txt。
- v2.16（vc117/core117，未实测）：房主「常用」加「全员无敌」（Con.setAllGod：每 3 秒 set Character bCanBeDamaged 0，第一次 getall 验证；关时 set 1 并恢复房主自己的 God）。
- 一体包：/workspace/work/full/全军出击单机版-ZFrame-v2.16.apk = qjcj-zframe.apk 换 classes2.dex 为单机 inject.dex，zipalign（rkenv/aapt2tools/build-tools/zipalign）+ apksigner --key assets/gkey.pk8 --cert assets/gcert.der。不用装 ZFrame。
- 安卓15 一体包：游戏 manifest targetSdk 19→24（mf/AndroidManifest.xml，替换后 zipalign+签名），文件 全军出击{单机,房主}版-安卓15-v2.16.apk。
- 用户要求回退：不要安卓15（targetSdk 24）包，已删；一体包保持原 targetSdk 19。

- 2026-11-17 00:19 一体包 v2.16c：libMczi.so 整个删掉 + classes.dex TPShellApplication.preInitial 里 loadLibrary("Mczi") 改 nop（dex 偏移 0x550298，修 sha1/adler）；libUE4 带 UPD/SND/GI 补丁。用户实测：能进大厅、自建服务端已开。
- 00:43 用户：人机在旁边时丢枪到地上闪退（有概率），怀疑人机自动拾取（同 v1.94/v1.98 丢枪闪退，至今没有现场）。
- v2.17（vc118/core118）：大厅只两个模式 正常对局 / 不刷毒对局（= 不上飞机 + 不刷圈，Mods.setNoZone）；大厅去掉清空仓库、日志/导出诊断；单机版去掉衣服页；Server.autoCrashSave 发现新的 Bugly tomb_*.txt（24 小时内、没存过）就进大厅自动存 crash-*.txt。单机一体包 全军出击单机版-v2.17.apk（v2.16c 换 classes2）。
- 00:58 用户：刷到身上（背包）的枪再丢会闪退；刷在地上捡起来的再丢没事。两份 crash（tomb）一样：GameThread SIGSEGV fault 0x1c，pc libUE4+0x25cb8e8 vldr s22,[r0,#0x1c]，r0=0；lr 0x25cb4d4。0x25cb880 = Recast dtNavMeshQuery::queryPolygons（this+0 = m_nav 空，单机没导航网格），被 0x25cb4a0 findNearestPoly 调。丢枪日志最后：Discard success … LogNavigation Error build nav collision for P20101000001_M416。
- v2.18（vc119/core119）：Lan.preinstall 加 NQ：0x25cb8e8 → b 0x171e700 cave（cmp r0,#0; beq→mov r0,#0x80000000; b 0x25cbbc4 epilogue；否则原指令 vldr + b 0x25cb8ec）。诊断「导航查询空指针 ok」。
- 11:07 用户大单：加速/高跳/动作放慢移到「常用」下新页并折叠动作；空投没东西；去掉人机难度，刷人机分固定/自由；手动刷的人机没枪包；人机衣服随机；仓库衣服要等服务端；大厅菜单留白太多；加载页不显示菜单、到大厅才显示、位置中上；电竞页点了不跳转（两个按钮，左上大厅？待截图）；军需抽奖；双排四排可选+人机队友（给了动画角色名单）；地图不刷物资、门不显示（单机）。
- RE：UCharacterMovementComponent MaxWalkSpeed 0x164、MaxWalkSpeedCrouched 0x168、MaxSwimSpeed 0x16c、MaxAcceleration 0x178（反射表：ansi 名字指针后第 7 个字是偏移）。PlayerName 属性候选偏移 0x210/0x260/0x2f0（未确认哪个是 APlayerState）。
- v2.19（vc120/core120）：新页「功能」(id 8，常用下面，客户端也有)：加速/高跳 + 「动作放慢」默认折叠；常用/客户端常用去掉跑跳。大厅里内容区高度自适应（不留白）；菜单在第一次进大厅/进图前隐藏（GameOverlay.lobbyReady）；默认位置中上，展开/收起保持居中。人机页：去难度，按钮 固定人机/自由人机（Veh.spawnBot(fixed) → Bots.manual：配枪 loadout、补装备、固定的每秒把移动组件速度/加速度/跳写 0）。所有人机：dress() 从仓库存档/zf_clothes.txt（没有就后台 scanSkins 一次）按部位随机穿 301/303 款；name() 动画名随机，写 PS 候选 FString（原空间放得下才写）。单机：看到大厅开始 LoadMap 就开服务端（不等加载完+2.5s），兜底 300ms。诊断：人机衣服/名字状态。
- 未做：空投/地图物资/门（ServerOnly 层，老问题）、双排四排+人机队友、军需抽奖、电竞页拦截（要截图）。
- 12:10 用户改单：v2.20 做联机版（不是单机）：房主版在服务器那边生成物资/门/空投，同步给客户端；客户端版不自己刷。
- RE（v2.20，ServerOnly 层）：
  - 层工厂注册 0x1156fc0（按路径前缀：Heightmap/、Runtime/Buildings/…、ServerOnly/、ServerOnly/MeshTiles/）。ServerOnly 层类 vtable 0x4caee2c：+0x14 0x117bb14 收关卡，+0xc 0x117bb7c「StreamingServerLayer Add %s」（按 WorldMeshes/VehicleSpawn/Door/Glass/Fence/Navigtion/Navigation/Pickups 分进 [this+0x1c] 的 TilesCollection，其余「Add other」）。
  - 流式管理器 ANZStreamingLevelsManagerActor：BeginPlay 0x115beb4。0x115c004 GetNetMode(world)==1（专用服）或全局字节 0x5212a62 → 服务器路径（CurrentLayers +0x2f4/+0x2f8/+0x2fc 里放 Landscape/AlwaysLoad/Ocean + +0x2cc + ServerOnly 层 +0x2d4/+0x2d8）；否则客户端路径 0x115c3fc 只拷 +0x2dc 的显示层（+0x2e0 个），末尾 0x115c498 b 0x115c744。+0x2cc/+0x2d4 在 0x115a78c 里按层名 0x5173288/0x5173280 从工厂表取。
  - FillLayersWithCacheData 0x1161068 只遍历 CurrentLayers：建 ULevelStreaming、layer vt+0x14 收关卡、再调 vt+0xc。Default 策略 Update 0x11671bc 每帧轮一个 CurrentLayer 按视野进出。所以单机/房主（NetMode 0/2）ServerOnly 关卡从来不建 = 没物资点、没门、没车点。
  - UWorld::GetNetMode 0x2e65f00：[w+0x24] DemoNetDriver → 3；[w+0x74] NetDriver → 0x2b49740（服务器：GIsClient? 2 listen : 1 dedicated；否则 3）。
  - UNZBRPickupConfigComponent::BeginPlay 0x10c0b5c 门槛：世界类型 Game/PIE 且 (NetMode|1)==1 → 房主(2)被挡。
  - 空投箱 ANZBRAirSupplyBox：初始化 0xfd3274（DropAirSupplyBox 0x118a820 调）从 AirSupplyItem 表填 +0x3d8 组 → 0xfd3ac0 填 PickupItemIDList(+0x3f8/+0x3fc)；OnBeginOverlap 0xfd6294 权威端先打「OnBeginOverlap!!!!LockServer--Pos: %f, %f, %f」（Log 5，箱子 +0x370..378），列表空时打「PickupItemIDList.Num() = 0」（Warning 3）；OnHit 0xfd67a8 同样的 Warning。BeginPlay 0xfd3f3c 的 NetMode==1 只管特效组件，不管物品。
- v2.20（vc121/core121，未实测）：
  - 新 Loot.java（只在房主版 Ed.ED==1 装，Lan.preinstall 启动早装里调）：
    1. SL：0x115c498 a90000ea → b 0x171e720（a00817ea）；cave 172 字节到 0x171e7cc（废 GM 函数 0x171e4b0 里，NQ cave 后面）：world=[sp+0xc]，GetNetMode==2 时把 ServerOnly 层（+0x2d4，引用计数 +1）追加到 CurrentLayers（已在就不加，满了用游戏扩容 0xbc5580），然后 b 0x115c744。NetMode 0/1/3 不变。源码 game/re/lootcave.py，原字节从 libUE4 取、写前校验。
    2. PK：0x10c0ba4 01008003 01005003 1200001a → 1400001a 030050e3 1200000a（世界类型不对就退；NetMode==3 才退）。
    3. 空投兜底：日志「PickupItemIDList.Num() = 0」且 5 秒内有「LockServer--Pos」→ 在箱子位置用 cave 'G'（Veh.dropAt，服务器上生成）放 AWM + .300×2 + 3 级头 + 3 级甲，同一箱子（±5 米）只放一次。
  - Con：联机时不发 StreamLevelIn（以前收到「StreamingServerLayer Add」会自动发）；每行日志给 Loot.line 计数（服务器层关卡 / [Pickup] SPAWN / 门 / 空箱），只在「导出诊断」里显示；房主联机页多一行状态。
  - 测试：javac 过；game/re/emu_loot.py 在真 libUE4 代码上跑 unicorn：SL 7 种情况（房主加入、满了扩容、已存在不重复、无控制块、单机/专用服/客户端不变、world 空、层空，寄存器/栈恢复），PK 5 种世界类型 × 4 种网络模式（只多放开 NetMode 2）。
  - 没验证：客户端包里到底有没有 ServerOnly 关卡文件（缓存里没有就什么都不会加载）；房主加载 WorldMeshes 的内存；只按房主视野加载（离房主远的客户端身边可能没物资/门）；门/物资同步到客户端（GridMap 在 listen server 上是否建）；「LockServer--Pos」那行在设备日志里是否打印。

## v2.21（vc122/core122，2026-11-17 16:10）
- 用户：v2.20 房主在云手机（SM-G9810 安卓14，172.29.10.8）上联机进图一会儿闪退；下载里没有崩溃文件（没点导出诊断）。
- 改动：Loot（ServerOnly 物资层 cave + 物资点放开 + 空投兜底）不再启动时装，改成大厅开房页「地图物资/门 关/开」开关，默认关，开=写补丁，关=还原（先还原钩子再还原 cave）。Loot.line 在关的时候不处理。
- 目的：A/B 定位闪退是不是 Loot 导致。关着不闪 → 是 Loot；关着也闪 → 云手机本身（内存/32 位）或别的。

## v2.22（vc123/core123，2026-11-17 16:35）
- 用户 v2.21 实测（房主=云手机 SM-G9810，客户端=X70 MTN-AN00，穿透/局域网连上，剩余 2，1ms）：进图不闪退（物资/门关着）。房主停在出生岛地面，飞机从头上过；客户端在飞机上（屏幕中下两个灰框，疑似跳伞按钮贴图缺失），用户说"卡飞机里面"。
- 房主日志：CalcuAIRatioLevel failed, NumPlayers=1（房主自己没网络连接，不算玩家 → 不上飞机），阶段 1→2→3→4 正常。
- 改动：房主版大厅开房页加「正常对局 / 不刷毒对局」（Mods.setNormal/setNoZone，跟单机一样），不刷毒 = 都不上飞机在出生岛开打。
- 待确认：客户端点灰框能不能跳；不上飞机模式在联机下客户端是否也落在出生岛。
- 16:29 更正：用户当时只有房主一个人，没人加入。三张图都是房主视角（SM-G9810 是游戏水印），灰框是空武器栏。真实问题：房主正常对局卡在飞机里、没有跳伞按钮。v2.22 的「不刷毒对局」（不上飞机）可以先绕开；正常对局的跳伞待查（等倒计时结束会不会自动弹出）。

## v2.23（vc124/core124，2026-11-17 16:50）— 查「剩余 2」
- 用户：只有房主一个人，HUD 剩余却是 2；要找原因，**不许强行把 2 改成 1**。（单机 v1.84 起就有这个"多一个"，之前叫 phantom slot，没查过。）
- RE：0x101a6e8 = GameState 数活人：TActorIterator<ANZCharacter>（StaticClass 0xe3a608），跳过 PendingKill、跳过 ANZDollCharacter（0x11f3cd0），[+0x5a0]>=1 算活，0xcd4054（看 +0x2d0 控制器）判人机 → +0x774/+0x778。所以世界里还有第二个活着的 ANZCharacter（不是假人 Doll 类）。
- 探针（只在按「查剩余人数」时装，2.5 秒后拆）：0x101a814 mov r0,r6 → b 0x171e7d0；cave 把 r6 记进 8 格环（Java 直接缓冲区），mov r0,r6，b 0x101a818。0x171e7d0..7fb 是 GM 面板废函数里 SL cave 后面的空地。
- 输出（导出诊断里「剩余人数：」）：每个被算进去的人物 地址/（我）/类名/对象名/+5a0/控制器类/所在关卡包。

## v2.24（vc125/core125，2026-11-17 16:40）— 客户端 IP 输入能填不能删
- 用户：加入房间的 IP 框能打字但删不掉。悬浮窗里的 EditText 删除键（KEYCODE_DEL 按键事件）被游戏吃掉，提交文字走 InputConnection 所以能打。
- 改：去掉 EditText，换成自带键盘（0-9 . : 退格 清空 加入），上面显示 ___.___.___.___ 格式和是否合法（每段 ≤255，端口可省，默认 7777）。不弹系统键盘。
- 衣服「按编号穿」的数字框没改，可能同样删不了。

## v2.25（vc126/core126，2026-11-17 17:10）— 穿透域名
- 用户的 Sakura FRP 隧道（UDP，本地 7777）给的是域名 frp-put.com:47937（官方不建议节点 IP 直连）。
- 加入房间：键盘加「粘贴」（读剪贴板，去掉 xxx:// 和尾部斜杠）；Lan.join 遇到域名就开后台线程 InetAddress 解析成 IPv4，再在主线程 open 数字IP:端口。lastIp 记域名。

## v2.26（vc127/core127，2026-11-17 17:40）— 联机实测反馈一批
用户 v2.25 实测（雨林、不刷毒，穿透 frp-put.com:47937 连上，40ms，剩余 3）：
- 客户端解析域名得到 198.18.0.39（客户端手机有加速器 fake-IP），也连上了。用户要：只要粘贴+加入，不解析，域名原样连。
- 刷的车客户端上不去（日志 NZPlayerController.cpp:4503 ClientUseVehicleFailed，提示座位有人）；捡枪后（不拿枪也）一直换弹；配件装上倍镜还是机瞄；客户端加速/高跳失效；客户端从设置退出后加入不进去；房主进图后退不出去。
改：
- 标题：联机版 服务端 / 联机版 客户端；客户端大厅状态行不显示对局模式。
- 客户端加入：去掉键盘，只有地址显示 + 粘贴 + 加入；Lan.join 接受域名，直接 open 域名:端口（UIpConnection 自己解析）。
- 房主对局分页显示「人机」（刷人机加回来）。刷车加「皮卡（试）」= 车表 31（car_007）/32（car_008），按一次换一款，等用户说哪个是皮卡。衣服页顶上加「刷吉利服」（同装备页 ghillie）。
- 联机页加「关房间，回大厅」/「退出房间，回大厅」= open FrontEndMap_NEW（Lan.leave）。推测设置里退出走的是大厅服务器流程，联机版没开自建服务端所以卡住；未验证。
- 客户端去掉「无限子弹」（客户端写 0x608/0x604 和服务器对不上 → 推测是一直换弹的原因）。
- 新 Crew.java（房主）：开着加速/高跳/无限子弹时，每 15 秒用 Alive 的计数钩子（环形缓冲 8→32 格，cave and #0x1f）装 1 秒找人物，控制器类名带 PlayerController 的（除房主自己）= 加入的玩家；每 100ms 给他们服务器侧人物写 JumpZ×倍数、CustomTimeDilation=加速倍数（空中 1）、当前枪子弹 999。客户端要拉同一个数。加速走 dilation 在服务器侧是否有效未验证（ServerMove 用客户端时间戳）。
- 没动：车座位有人、配件不生效（都要房主日志：刷车/刷配件是谁刷的、房主那边是否也上不去）。

## v2.27（vc128/core128，2026-11-17 17:45）— 房主减负
- 用户：大家跟着房主开，房主（云手机）不用显示那么多，减负。Gfx.lite()：房主进图 4 秒后发 LITE 列表（sg.* 0、r.ScreenPercentage 30、foliage/grass.DensityScale 0、t.MaxFPS 30）；回大厅 unlite 恢复 ScreenPercentage 100 和 t.MaxFPS。诊断「画质」行显示减负状态。
- 用户纠正：要修游戏自己的退出，不是加回大厅按钮（按钮暂留，修好后去掉）。客户端没开无限子弹也一直换弹 → 不是 infAmmo。
- 换弹视频（1000080547.mp4）：客户端 M416 弹匣显示在 30/204 和 0/204 之间跳，换弹图标一直闪；推测客户端本地弹匣数和房主下发的对不上（0x604 ServerWeaponAmmoCount / 0x608），待房主诊断确认。视频里也看到「座位已有人」。
- 等：房主设置退出卡住时的诊断；换弹时房主+客户端诊断（v2.26 起房主有诊断）。

## v2.28（vc129，2026-11-17 17:55）— 房主分页看不全
- 用户截图：房主左列只显示到「刷车」，人机/联机/画面被挤出去 → 看不到刷人机和日志（导出诊断在联机页）。左列分页包进 ScrollView（可上下滑），分页高 36→32dp。

## v2.29（vc130，2026-11-17 18:00）— 菜单上滑动不再带动游戏
- 用户：在菜单上滑，游戏也跟着操作。游戏在 Activity.dispatchTouchEvent 里就把触摸给引擎。Window.Callback 代理里：ACTION_DOWN 落在 zframe_panel 可见区域内 → 这一整串触摸走 w.superDispatchTouchEvent（只给界面），不调原 Activity.dispatchTouchEvent。

## v2.30（vc131/core131，2026-11-17 18:12）— 域名加入改回后台解析
- v2.29 客户端：粘贴 frp-put.com:47937 点加入，状态「已发 open frp-put.com:47937」但进不去（房主已在图里）。v2.26 把域名原样交给 open，游戏不自己解析域名。
- 改回：Lan.join 遇到字母地址 → 后台线程 InetAddress 取 IPv4 → 主线程 openJoin(数字IP:端口)。界面和状态只显示域名，日志记「域名 → IP」。
- 房主设置里「退出比赛」卡住：还在等房主 导出诊断（静态：OnConfirmExit 0x162f38c → 0x130475c → 0x12486a8）。

## v2.31（vc132/core132，2026-11-17 18:50）— 加入的人拉到房主身边；加速/高跳跨局保留
- 18:27 双端诊断：客户端经穿透连上了（房主日志 Join succeeded: Guest4，127.0.0.1 = frp 本地转发）。客户端出生在随机点（快速开局，没飞机），常在水里，和房主不在一处。
- 新 vcave 指令 'T<人物>x<buf>'：0x273d654 = K2_TeleportTo 的实现（内部走 vt+0x250 TeleportTo(loc, rot, false, false)），位置 buf+40..48，朝向 +52..60，返回 +4，标记 5b0d。emu_vcave.py 加 T 测试，通过。cave 1424 字节，到 0x4a2ded0（< 0x4a2e000）。
- Crew：日志出现 Join succeeded → 6 秒、12 秒各找一次人（Alive.collect），把加入的玩家传到房主身边 2 米（每人每局一次）。联机页加「把加入的人拉到我身边」。诊断导出加 Crew.state / pullState。
- 加速/高跳没生效：Con 进图 Browse 时 Mods.resetMatch() 把 speed/jump/枪开关清回 1 —— 大厅里拉好的数进图就没了。v2.31 不再清这些，只清内部状态；setMove 记日志。
- v2.32 (vc133/core133): 客户端加速/高跳跟房主自动同步。房主 Crew 把 7700000+round(加速×2)×100+round(高跳×2) 写进每个加入玩家 PlayerState(pawn+0x2c4)+0x2b0 Score（反射表 APlayerState: Score 0x2b0 CPF_Net OnRep_Score，PlayerName 0x2b8，PlayerId 0x2d0）。有人加入后就一直写（没开也写 1 倍）。客户端 Mods.syncFromHost 读自己 PS 的 Score 解码设 speed/jump；客户端功能页滑条换成「跟房主」说明；诊断加「跟房主：」行。未实测：Score 可能被游戏别处显示/改写。
- 19:31 v2.32 实测：房主「全员：找到加入的玩家 0 个（人物共 0）」——Alive 计数钩子 1 秒内没触发，所以既没拉人也没同步（客户端「还没收到房主的数」）。截图：客户端在飞机上（跟随跳伞），房主在地上，剩余 3。
- v2.33 (vc134/core134): Crew.find 改走 GameState(GM+0x2e0).PlayerArray（+0x2bc data/+0x2c0 num；反射表 GameState PlayerArray 0x2bc），PS.Owner(AActor+0x60, OnRep_Owner) = 控制器，类名含 PlayerController 才算，人物 = ctl+0x2c0/0x2b4。Score 按 PS 列表写（不依赖人物）。有人加入后每 15 秒重找。读不到 PlayerArray 才退回 Alive 钩子。
- 19:47 v2.33 实测：拉人修好（PlayerArray 找到 2/2，但房主自己也被算进去了：GameOverlay.pawnAddr() 在房主上是 0）；客户端加速仍不生效（「跟房主：还没收到房主的数」）；客户端可以设置→退出了；上车仍 ClientUseVehicleFailed；服务端菜单拖出屏幕（标题栏出界）拉不回来。
- v2.34 (vc135/core135): 根因——客户端没有刷物品补丁，askPawn 永远拿不到人物地址 → Mods 的加速/高跳/跟房主从来没跑。新增 Mods.myPawn()：pawnAddr 为 0 时用刷车 cave Veh.info()[6]（buf+8 本机人物），2 秒一问，出图清零。tickMove、syncFromHost、Crew.find 都用它（房主不再把自己当加入的人）。跟房主诊断写明卡在哪（找不到人物/没有 PS/Score=…）。菜单 clamp：拖动、松手、宽度变化、decor 尺寸变化都把面板夹回屏幕（标题栏完整在内）。
- 上车失败未修：只有客户端日志（ClientUseVehicleFailed→CancelPreEnterVehicle），要房主同时刻诊断。游戏有自己的 NZStreamingLevelsTilesCollection（日志 UpdateActorPosTriggerLoadLevels ... PlayerNum），猜测与服务端位置不同步/房主那边地形没加载有关，未证实。
- v2.35 (vc136/core136): 用户要求进大厅/进对局菜单出现时用五指打开同款动画。GameOverlay.mapLeaving()（Con Browse 时淡出收起，40 秒没进图兜底弹回）+ mapShown()/enterAnim()（每次地图加载完 lobbyReady 时弹出，0.85→1 缩放+下滑+Overshoot 260ms）。用户手动 ✕ 关掉的（hidden）不弹。

## v2.36 (vc137/core137, 2026-11-17 21:05) — 上车、滑动穿透、拉回、换弹、无敌、人机带枪、改名
- 上车：服务端判定 VehicleInterface CanUse(0xdafe1c，所有车共用，接口副表 vt+0xc；TryUse vt+0x254=0xda7200，失败时 PC vt+0xb9c 弹提示 9=座位有人)。联机走 0xdb00e8 分支：要求 pawn+0xb84 ClosedVehicle == 车，但 ClosedVehicle 只由 0xdd7c2c（每辆车 Tick，只看本机第一个 PC）→ 0xd1a06c 写，服务端上加入玩家永远为空 → 必失败。补丁 0xdb00f0 beq 0xdb0430 → beq 0xdb0400（ce00000a→c200000a），跳过距离/视线，只查车在、有空座。Lan.preinstall 装。ServerUseVehicle_Impl = 0xd75f2c（PC vt+0x898）。
- 菜单滑动穿透：游戏 NativeActivity，触摸在 ViewRootImpl NativePostImeInputStage 先给引擎再给界面，Window.Callback 拦不住。菜单改放独立 TYPE_APPLICATION 窗口（WRAP_CONTENT，NOT_FOCUSABLE|NOT_TOUCH_MODAL|LAYOUT_IN_SCREEN|NO_LIMITS，刘海 SHORT_EDGES），位置=窗口 x/y（getX/getY/setXY），失败退回 decor。按编号穿输入框点时临时可聚焦。
- 高跳/加速拉回：服务端给加入玩家 CMC 置 bIgnoreClientMovementErrorChecksAndCorrection（+0x354 bit0，反射 SetBit 0x2ea9874）；加入玩家空中不再切回 1 倍（服务端 Crew 和客户端 Mods 都是），客户端不再起跳加水平速度。Crew 50ms 一轮。
- 换弹循环：OnReplicateAmmoCount(0xedbc68) 服务端弹匣(+0x604)与本地差≥2就覆盖。服务端那边加入玩家的枪 +0x604 停在 0 超 2.5 秒 → 按 ClipAmmoCount(+0x52c) 补满 +0x604/+0x608（Crew.guard，诊断「换弹：」）。WeaponSkinTable 报错只是皮肤，不相关。
- 无敌：God 只关 bCanBeDamaged，人机仍能打死。服务端/单机开无敌时 Mods.godTick 每 40ms 把 Health(+0x5a0) 补到 HealthMax(+0x5a4)；全员无敌对加入玩家同样补。
- 人机：固定/自由各一个「带枪」开关（Bots.gunFixed/gunFree）；不带枪 = addBot 标志去掉 1（不给全套装备）、不 regive、不发枪弹。
- 改名：界面「房主」全改「服务端」；APK 文件名 ZFrame-v2.36-服务端/客户端/单机；rel.py 重建 manifest 字符串池：label「全军出击服务端 v2.36」、versionName「2.36-服务端」（androguard 验证）。build3.sh 传第 7 参数。
- 诊断导出关键字加 VehicleInterface/UseVehicle/EnterVehicle/Ammo/Reload/Discrepancy/Correction。
- 未测。用户 21:06 说「编号和吉利服的编号弄好了」、衣服只刷风衣和吉利服 —— 待问编号后做。
- v2.37 (vc138/core138): 用户：衣服页只要风衣和吉利服，排整齐。clothesPage 改成：风衣卡片（显示编号）、吉利服 7 个按钮（30309000001..06、30309000000，toast 显示编号）、按编号刷（输入框）。去掉全部衣服列表。
- v2.38 (vc139): 服务端日志 avatar-1117-205110 20:45:00–02 依次试 30309000001..06/00，04/05/06/00 报 NZPickupInventory Can't Find Item，01/02/03 无报错 → 吉利服只有 3 款。GHILLIE 改成 01-03，衣服页三个按钮 吉利服 1/2/3。以后判断编号真假看日志 Can't Find Item。
- v2.39 (vc140): 用户不接受 v2.36 换弹补子弹（不扣备弹）。去掉直接补 +0x604/+0x608。新推测真因：听服模式下加入玩家人物不在房主画面里，USkinnedMeshComponent.MeshComponentUpdateFlag（+0x6c1，反射表 0x502e534）是 OnlyTickPoseWhenRendered → 换弹动画结束通知不触发 → 服务端弹匣停 0。Crew.guard 对加入玩家 mesh(+0x2f4) 写 +0x6c1=0（AlwaysTickPoseAndRefreshBones），清 bEnableUpdateRateOptimizations（+0x740 bit0，反射表 0x502e33c）。诊断「换弹：」记录弹匣 0 次数、动画修正次数、原值。未实测。
- v2.40 (vc141): 用户：枪打刷出来的车不爆。NZVehicle 反射：MaxHealth +0x2f8、DamageScale +0x2fc、Health +0x308（NZWheeledVehicle 另有 Health +0x4b4 / DamageScale +0x49c / MaxHealth +0x498，Hover +0x3fc/+0x3f0/+0x3ec）。刷车成功 2 秒后（服务端/单机）：EnableCheats、set NZVehicle bCanBeDamaged 1、getall NZVehicle bCanBeDamaged/Health/MaxHealth 写进日志；诊断关键字加 NZVehicle/Explo/Burn。真因未确认，等日志。
- v2.41 (vc142): 用户 21:43 日志（服务端 avatar-1117-213949、客户端 avatar-1117-213849）：
  - 上车成功了（服务端 NZWheeledVehicle.cpp:4224 EnterVehicle、客户端 Attach to Vehicle）→ 0xdb00f0 补丁有效。
  - 服务端开车闪退：客户端网络抖动重连（OnReconnected 21:38:27）→ Crew.joined → find()+doPull；玩家已上车，控制器+0x2c0 是 BP_Vehicle_Mirado_A_C_0 → 当人物 teleport + 写 CMC/血/骨骼网格（v2.39 +0x6c0/+0x740）进了车的内存 → 崩（最后一行 21:38:28 AddAvaItem V0x… 就是 find 的 Veh.info）。修：find 只收类名含 Character 的，车上的计 drivers；骨骼网格写之前核对类名含 SkeletalMesh。
  - 换弹仍循环：客户端 "Force set local front ammo count to server value. ClientAmmo=16, ServerAmmo=0" 每 ~4 秒。服务端崩了没留下 Crew 状态 → 改成 LogCap.note（崩后「上次运行最后几步」能看到）：动画原值、弹匣 0 事件。
  - 用户：服务端删刷人机 → 服务端分页去掉「人机」。
  - 吉利服 3 款确认正常。客户端穿 30309000003 时每帧 GetItemDataByID Failed（客户端物品表没有吉利服），只是刷日志。
- v2.42 (vc143): 用户：拉人去掉（开加速自己过去；海岛都在出生岛）。用户以为拉人是手动的 —— v2.31 起有人加入/重连会自动拉（Lan "Join succeeded" → Crew.joined），这就是 v2.41 那次闪退的触发点。现在加入只 find()，不 teleport；联机页拉人按钮删了。
  - 换弹：反射表 NZWeapon WeaponCarriedAmmoCount +0x60c（ServerWeaponAmmoCount +0x604 有 RepNotify OnReplicateAmmoCount，WeaponAmmoCount +0x608，ClipAmmoCount +0x52c）。没有单独的换弹 RPC，换弹状态走 ServerProceedWeapRepInfo；结束事件 EStateEvent_EndChangeClip，时间 WeaponChangeClipTime。新推测：服务端那边加入玩家背包没子弹（20:47:57 服务端 "GiveItem failed, Pick->GetInventoryType() is NULL"）→ 服务端换弹拿不到备弹 → 弹匣 0。诊断写 LogCap.note：加入玩家拿枪时 容量/服务端弹匣/备弹，弹匣 0 超 2.5 秒时备弹；AvatarLog 加 GiveItem/PickupInventory/ChangeClip/Carried。
  - 地图：只有 海岛、海岛2、雨林 三张完整主图（训练场只有碎片）。libUE4 里有 ZombieSpawnManager/ZombieClass 代码，但没看到僵尸/圣诞地图。
- v2.43 (vc144): The user's base is already the final 1.0.17.1.0. The 五军/战术大师 (landmine, trap, prop items) and desert resources are probably on-demand downloads.
  - The diagnostic export gets a 「资源清单」 section: every "mount pak" MountPoint in the game log (Avatar folded per category), keyword hits (Desert/Legion/Prop/Mine/Trap/Flare/AirSupply/Training…), files >1MB under Android/data, obb and data/files, and the APK size.
  - Log KEYS add AirSupply/AirBox/Flare/FunctionalProp/Legion/FiveArmy.
  - RE: the flare path is ANZProjectile_FlareBullet / UNZFireMode_FlareGun / ANZBRAirSupplyPlaneForFlareGun.
    - AirSupplyManager props: BoxClass +0x32c, SP +0x330, FlareGunNormal +0x334, FlareGunVehicle +0x338, CurFlareUsedNum +0x354.
    - PC TestSpawnAirSupplyBox impl is stripped.
  - Functional props are ItemSubType_FunctionalProp (enum Bush, Doll, LandMine, LaunchPad, LiftTable, Shiled, Trap, Tree, ScanGrenade). Item IDs are unknown.
- v2.44 (vc145): The user asked for a mode chooser in the client APK.
  - Ed.ED is now a runtime volatile; build3.sh seds `static final int BUILD`.
  - In the client APK (BUILD=2), GameOverlay.attach shows a full-screen TYPE_APPLICATION chooser 「单机 / 联机（加入房间）」 on the first resume.
  - Patches waits for the choice (Ed.await, at most 15 s after libUE4 is found, then falls back to the saved choice in filesDir/zf_mode.txt) before Mods.preinstall. 单机 sets ED=0, so everything follows the single build: self-hosted lobby server, wardrobe, bots, single lobby page.
  - Client join box: EditText (input, delete, focusable toggle) with 粘贴 / 清空 / 加入, replacing the paste-only label.
- 23:16–23:20 用户：单机毒圈还是乱的（开局就有圈、位置随机、常在图边，人在圈外），要求「改底层」，别在游戏生成以后再改一遍；单机没有导出诊断。
- RE：InitArea 0xd9777c 逐项拷 DefaultRestrictedAreaInfos(+0x33c/+0x340, 0x54/项) 到本局数组 +0x354；第 0 项圈心 = PresetCenterPointList(+0x348/+0x34c) 随机一个，否则 RandomCenter(FirstCenterPoint +0x3b4, 本项 +0 偏移)；之后每项 RandomCenter(上一项中心, 本项 +0 偏移, +0x10 半径)，0xd96700/0xd96a14 校验落点、最多试 +0x320(10) 次。出口算 +0x3a4/+0x3b0/+0x2dc/+0x3c0(FirstRadius)。换圈 0xd97d00：前一半圈 MakeLowPoolPlayerPoisonFogCenter（按玩家位置），后一半 RandomCenter。
- v2.45 (vc146)：毒圈改在底层。0xd977ec（ldr r5,[r4,#0x34c]; str r0,[sp,#0x60]）→ cave A（SLACK+0x9b0）：把 +0x33c/+0x340/+0x344 换成我们的表（每项 +0x20..+0x54 照抄游戏原表第 0 项/最后一项），存原值到 SLACK+0x998..；0xd97b6c（函数出口）→ cave B（SLACK+0xa70）换回原数组，表永远不归游戏释放。表在 LogNet Browse 进对局图时写（Flow.natPrepare，客户端不写），direct ByteBuffer；表 = 整图(等 240/4x4 180 秒) + 整图(第一圈时间) + 正式服 8 圈 + 0.1 米；圈心全交给游戏算，+0 偏移 = 第一圈 边长/2-半径，之后 上一圈半径-本圈半径。底层装上时不再打 POOL 补丁（换圈恢复游戏自己的按玩家定圈心），Flow.circle 看到已是正式服表就只记录。zone_cave.py 生成、unicorn 测过（拷尾巴、不重复换、出口还原）。未实测。
- 23:28 用户：单机版不用等人，进游戏 20 秒就上飞机。
- v2.46 (vc147)：单机（Lan.role==0）大厅加载 6 秒后和 WAIT 补丁一起打 START 补丁 0x101977c：ldr r0,[r6,#0x3c]（StartMatchCountDownTime 表值 45 → GS+0x740）→ movt r0,#0x41a0（r0 低 16 位来自上一句 60.0/1.0，都是 0 → 20.0）。等人 1 秒（WAIT）+ 读秒 20 秒 → 上飞机。联机服务端不改（给客户端留进房时间）。表加载顺序见 0x1019774..0x10197d8（+0x38→0x72c 等人，+0x3c→0x740 开局读秒，+0x40→0x73c Reset，+0x44→0x734，+0x48→0x738，…）。未实测。
- 23:31 用户：检查别人改坏的东西。审计结果：
  - libUE4 末尾追加段 + DT_INIT 桩（字符串 libmono.so / "[%s] time:%ld ms"）= 腾讯加固壳的初始化，不是玩法改动。整段代码启发式扫描（bx lr 后残留函数体、手工 nop）：26 处都是正常编译结果（在 physx/编解码库里），没发现被挖空的游戏函数。
  - 游戏会读 Saved/Config/*.csv 覆盖数据表（ModeItemTable/ItemTable/GameFeatureConfig/AirTransporterSystemConfig/AirSupplySystemConfig/GameStateCommonConfig 等），日志全是 Read File Failed → 没有人放覆盖表。
  - Saved/Config/Android/Engine.ini：我们 v1.19 AkDiag 留下的明文文件，游戏报 invalid config file 直接不用；v1.19 之前的日志没有这行。v2.47 启动时删掉（只删带 [Core.Log] 或空的）。
  - 已知被改的数据（都在 pak/蓝图里，不碰 pak）：雨林毒圈只有 2 项（→ v2.45 底层表）、航线短（→ v1.94 Flow 改）、飞机高度 200 米（→ v2.00）、人数 2（→ v1.99）、开局读秒（→ v2.46）。
  - 原作者 libMczi（Dobby 钩子 + CodePatch）早已换成空壳；libfucktp 是去 TP 的（Substrate 钩子），按规则不碰。
  - 大厅：ANZFrontEndSceneMgr InitLight 找不到 HallDefaultSkyLight/HallDefaultDirectLight 标签 → 大厅灯光缺（可能和灰色大厅人物有关），没改。
- v2.47 (vc148)：只加 Engine.ini 清理。含 v2.46。
- 23:40 用户：剩余 2 可能是单机一个人开不了局所以放了个假人；大厅灯光可以修；现在可以做大厅军需抽奖。
- v2.48 (vc149)：Server 处理军需自选抽奖 CustomLottery（cmd.proto：51001/02 列表，51003/04 提交，51005/06 抽，51007/08 重置；cs_lottery.proto 字段见 game/proto/fds.pkl，protoext.py 导出）。奖池 8 件 = skinGift（扫到的全部皮肤）或 GIFT，去掉 BAD；pool_type 1 必中/2 高/3 中/4 低；抽中入仓库并推 13002。没有 consume_prop（不扣券）。未实测；以前日志里从没出现 51xxx 请求，若军需页走的是 31001/31003（宝箱）或 38003（卡片抽奖），要看服务端日志里的请求号再接。
- 大厅灯光 RE 起点：HallDefaultSkyLight/DirectLight utf32 0x43c9118/0x43c9064，引用函数 0x12056b4（ANZFrontEndSceneMgr InitLight）、0x121733c、0x1218xxx。还没看完怎么找带标签的 actor。
- 00:29 用户：军需还是点不开（单机没日志），先不管；把能刷的全部加到单机里。
- v2.49 (vc150)：装备页（单机/服务端）加「全部道具」：Server.scanItems 只读扫内存里所有 11 位物品编号（不含 301-309 服装，保留 30304/30305/30307 头甲包），按分类（ITEM_CAT）分页，每页 12 个，点一下刷（子弹 30、投掷/药 3）。结果缓存 filesDir/zf_items.txt，「重新读」重扫。刷不出的标「（没有）」，刷出的枪从日志取武器名。娱乐道具（ItemSubType_FunctionalProp）编号不知道，靠这个扫出来的「其他 xxxxx」分类找。未实测。
- 01:23 用户（v2.49 实测）：战术模式道具全部能刷进背包，但丢到地上看不见；喷气背包能飞，少特效和别的；魔爪饮料也能刷。单机没日志，只有截图/视频。
- RE：
  - 地上物品 ANZPickupInventory 模型异步加载回调 0xce5fc0（NZPickupInventory.cpp）：ResolveObject(0x1be0d50) 得到 UStaticMesh 才 SetStaticMesh(组件 +0x2c4, vt+0x43c)，失败只打 Warning "asynchronous load mesh failed [%s]"。发起方 0xce6c38：+0x2b5 bShouldDisplayPickupMesh、+0x2f8 行指针（空就不加载），路径 FString 在行 +0x20。
  - 手持模型 ANZInventory_ItemBase::ForceCreateItemMesh 0xcca500：+0x2c0 路径，失败 "Unable to load static mesh [%s]"。
  - 喷气背包：UNZBRPlayerPropAnimComponent。0x110c004 从物品 +0x338/+0x350 拷 JetpackBagInfo(+0x170 特效 SoftObjectPath，+0x188 耐久模型路径)，非专用服务器直接 InitJetpackBag 0x110c424：特效用 TryLoad 0x1be0c88（同步加载+跟重定向）→ 是 UParticleSystem 才 SpawnEmitterAttached(0x29fa5b4) 存 +0x1d8 JetpackEffectPSC。所以没特效 = 特效文件加载不到（不在手机上或路径空），不是联网/OnRep 问题。
  - GUObjectArray = base+0x529b660（Objects +0x10，16 字节一项，Flags +4，RootSet 0x40000000）。
- v2.50 (vc151)：
  - Prop.java：地上模型替身。0xce6068 → cave1(SLACK+0x300)：记本局第一个成功加载的地上模型（直接缓冲区存指针）并 AddToRoot；0xce62a0 → cave2(SLACK+0x344)：有替身就走成功分支，没有照原样。进对局图（Browse）时装，所有模式。pick_cave.py / emu_pick.py（unicorn）测过。+0x2f8 行为空的情况不会进回调，替身管不到。
  - 缺资源记录：Con.line → Prop.line，抓 asynchronous load mesh failed / Unable to load static mesh / Failed to find object / Couldn't find file 等带路径的行，「全部道具」页底部列最近 6 条；刷完 1.2 秒内有新的就 toast。
  - 喷气背包特效没改（文件不在就没法补），等页底部列出的路径。未实测。

## v2.51（vc152/core152，2026-11-18 02:40）— 道具能用（草丛等）+ 服务端地图核对
用户 02:18：「还要能使用啊，查查为什么使用不了比如那个草丛，然后再给服务端加上能开的所有的地图」。
### 1. 功能道具用不了（草丛/盾/雷/陷阱/扫描雷…）
- RE：功能道具都是 ANZItem_PropBase 子类（StaticClass 0xf34f54，虚表 0x4c6c800；草丛 ANZBRItem_Bush 构造 0x11d7384 直接继承 PropBase，虚表 0x4ccf8ac；伪装 ANZItem_Camouflage 虚表 0x4ca84b4）。识别：道具虚表 +0x394 == 0xf2e750（OnSelected）。
  - 道具属性：bSelected +0x334、放置点有效 +0x336、放置点 +0x338(FVector)/+0x344(FRotator)、CheckPlaceBoxComponent +0x360、AdditionOverlapBoxComponent +0x364、PropType +0x3ec、TargetProp(TSubclassOf 场景道具) +0x3f0、PreviewProp +0x3f4、ItemUseType +0x310（2=Passive → NotInitiativeUsable）。
  - 背包组件 = 人物+0x838（0xcc8274）。3 个道具槽 +0x490/+0x494/+0x498（拾取 PropPickUp 0xfe2074 → 0xfdef7c 放槽），SelectedPropInventory +0x568、LastSelected +0x56c。背包虚表 0x4c830a4：+0x410 0xfe915c（投掷物/道具栏选择：不是投掷物就转 +0x4d0），+0x480 UseItem 0xcdcdfc，+0x484 用选中道具 0xfe98b0，+0x48c 取选中，+0x4d0 DoSelectedFunctionalProp 0xfe9570→0xfe9574，+0x4d4 取消选中 0xfe96e4，+0x4fc(i) 取道具槽。
  - 流程：选中（0xfe9574：人物有效、0xd15070/0xcdb0ac 为 0、是 PropBase → 取消旧的、道具 vt+0x394 置 bSelected、写 +0x568；Role==2 才发 RPC）→ 道具 Tick 0xf2e124（vt+0x204）每帧用 vt+0x3b4 0xf2fb30 算放置点并写 +0x336（检测盒 +0x360 无效时直接返回 false、不写位置）→ 用 0xfe98b0：选中且 +0x336≠0（或专用服 / vt+0x324）才 UseItem，否则给玩家弹「调整放置位置」提示 → UseThisItem 0xf2e3c4（vt+0x348，CanUseThisItem 0xf2e1f8）→ SpawnScenePropItem 0xf300dc（vt+0x3a4，TargetProp 空就什么都不做，生成失败打 "[evanlu]ANZItem_PropBase::SpawnScenePropItem failed!"）。
  - 游戏自己的人机放盾 0xfc8f5c 就是：背包 vt+0x4d0(道具) 再 vt+0x484()。
  - 结论（静态，未实测）：选中只由游戏界面（Lua 的道具栏 / 投掷物栏 → vt+0x410/0x4d0）触发，经典模式界面没有道具栏 → 选不中 → 怎么点都用不了。喷气背包是背包类、魔爪饮料是普通消耗品，不走这条，所以能用。
- 改：新 'U' cave（SLACK+0x224..0x2b7，game/re/ucave.py，emu_ucave.py unicorn 过：选中、拷位置+置有效、obj 空、其他字母原样收尾、r4-r11/sp 恢复）。vcave 'G' 判断后的 `bne done` 改成 `bne ucave`（CAVE 偏移 0x5c：3c01001a → 70ffff1a，长度不变；emu_vcave 仍全过）。Veh.install 启动时先放 ucave（空位要全 0），放不上就把那个字改回原样。
  - 'U'：buf+12 obj、+16 arg、+20 虚表偏移 → 游戏线程调 obj->vt[off](obj,arg)，返回 +24，标记 5b0e/5b0f；buf+28 非 0 时先把 buf+40..63 拷到 道具+0x338..0x34c 并置 +0x336=1。Java 只调背包 vt+0x4d0/vt+0x484，调前核对虚表项 == 0xfe9570/0xfe98b0。
  - 装备页（单机/服务端）新「用道具（草丛/盾/雷）」：查背包里的道具（槽 + 物品列表里 vt+0x394==0xf2e750 的，显示类名、槽、数量、是否拿出、放置点是否有效、TargetProp 类名；TargetProp 空 = 场景蓝图没加载 → 放不出来）、每个道具「拿出」（vt+0x4d0）、「放下 / 使用」（vt+0x484；游戏算的放置点无效就用面前 1.5 米、脚下高度）。日志里 SpawnScenePropItem/CanUseThisItem 行记进 LogCap 并显示在页底。
  - 如果「放出来是…」那一项显示「场景模型类没加载」→ 草丛的场景蓝图（ANZBRSceneItem_Bush 的 BP）不在手机的 pak 里，那就没法修（Items/Prop 两个 pak 已挂载，但内容在 IFS 里不能读）。
### 2. 服务端能开的地图
- 依据：游戏日志的 pak 挂载表（game/logs/PUBGM.log-e92460374c78.txt.log）+ libUE4 字符串。Content/Maps 下挂载的：ErangelMobile_Runtime（老海岛 BR-Erangel_Mobile_Main）、ErangeMobileRenew_Runtime（新海岛 BR-ErangelRenew_Mobile_Main）、SavageMobile_4x4_Runtime（雨林 BR-Savage_Mobile_Main）、TrainingIsland_Runtime、ErangeMobileRenew（新海岛共用资源，无主关卡）、Maps/ 根 pak（大厅 Erangle_RoleHall/FrontEndMap_NEW）。
- 训练场：libUE4 里主关卡名 BR-Training_Mobile_Main（0x464aac4，只在 NavMesh 特判 0x2797928 用）；v1.12 设备上 open 报 "does not exist"（UEngine::HandleOpenCommand → MakeSureMapNameIsValid，会按短名搜所有已挂载包）→ 手机上只有碎片，不加。
- 沙漠：大厅有 OnDesertBtnClick，但没有任何 Desert/Miramar pak 挂载；地图包是按需下载的（MapPakDownloadConfig / "MountMap no pak exist for map %s"），离线下载不了 → 不加。ShowcaseMap/OpeningAnimation 只是开场动画相机，FrontEndMap 是大厅 → 不是对战图。
- 结论：能开的就是现有三张，服务端开房页不变（单机选图也不变）。
- v2.52 (vc153)：在线内核（免安装更新）。NetCore.java：进大厅后后台从 GitHub 拉 core/b<BUILD>.ver（raw.githubusercontent，失败再试 jsdelivr），比本地新就下 core/b<BUILD>.dex 存到游戏 files/zcore_net.dex(+.ver「版本 BUILD」)，toast「重开游戏生效」；下次启动 ZInit.boot 最前面 NetCore.chain 用 DexClassLoader 链式加载它（同 BUILD 且版本更高才用；主进程计数 zcore_net.try，连续 2 次没进大厅就不用）。启动阶段不联网（ZFrame App 没 INTERNET 权限，只在游戏进程下）。MainActivity.checkUpdate 不再因为「游戏里的框架」旧了提示更新补丁（内核本来就每次热加载），只有声音/刷物品文件补丁才要重新打包。build3.sh 每个包型同时输出 ../core/b<e>.dex/.ver，push 后用户重开游戏两次即更新（第一次下载，第二次生效）。未实测。
- 03:13 用户：v2.52 显示生效；草丛/伪装岩/人偶标靶在背包里但用不了（没找到装备页的用道具）；「以前游戏里有使用道具的专用键，加上去」；全部道具页刷不出的没删、没名字；捡东西有两次拾取动作（待查是不是新问题）。
- v2.53 (vc154)：PropBtn.java 对局画面上的圆形「道具」键（独立小窗口，可拖动、位置记住）：背包里有功能道具才出现（每 2 秒只读 scanUse(quiet)）；点一下 拿出(vt+0x4d0)/放下(vt+0x484)，长按换下一个。游戏自带道具键在 Lua/UMG 里改不了。全部道具：刷失败的编号自动删掉（zf_items_bad.txt），空分类不显示。名字表还没找（在 Lua 配置里）。未实测。
- 03:24 用户（v2.53 实测）：道具键出现但显示类名「40201000005_Shrub」「…hara」，点了提示「用道具补丁没装（空位被占）」，地上替身也「空位被占」；全部道具还是很多、每次要重新读、没名字；不知道更新了没；要把更新提示放进游戏、放到选单机/联机那一页；捡东西两次动作还在。
- v2.54 (vc155)：
  - 根因：'U' cave 在 SLACK+0x224，服务端门 sendGate 在 SLACK+0x200 长 0x34（到 0x234）→ 重叠，ucave 从来没装上。挪到 SLACK+0x240（位置无关，ucave.py ORG 改了），vcave 的 bne 改 77ffff1a（vcave.py 重新生成一致，emu_vcave/emu_ucave 过）。U/Prop cave：代码相同只是末尾缓冲区地址不同也算自己的、重写；被占时把前 8 字节写进提示。Prop 0x300 为什么被占还不知道（看新提示里的字节）。
  - 道具键/全部道具名字：类名关键词转中文（Shrub 草丛、Camou/Rock 伪装岩、Doll/Chara 人偶标靶、Shield 盾…）；刷成功后从背包物品类名（含编号）取名，存 zf_item_names.txt；40201 分类叫「战术道具」；有缓存文件就自动加载，不用再点读。
  - 在线更新提示：选单机/联机框里查更新，下好了显示「重启更新」（AlarmManager 1.2 秒后重开 + 自杀）；大厅菜单顶上显示更新状态/重启按钮；换上新内核后第一次进大厅 toast「已更新到 vX」。
  - 捡东西两次动作：未修。RE 起点：BeginPlayingPickupAnim 原生实现 0xd21bfc，C++ 调用方 0xf041e8（NZWeaponAppearanceComponent，0xf036b0 函数里，有 "Pickup Anim = %s" 日志），另一个是 UFunction thunk 0xe3ff20（Lua/蓝图）。猜测两边都调了。
  - 注意 build3.sh javac 出错不会停，会把旧 dex 写进 core/ —— 构建后一定看有没有 error。
- 03:37 用户（v2.54 实测）：道具键能拿出（升降台/伪装岩/人偶标靶蓝色预览出来了），但放不下；要自动回传日志；选模式时能选不刷人机。
- v2.55 (vc156)：LogUp.java —— AvatarLog.save 导出的诊断自动 gzip 传到 ntfy.sh/zf-qj-k7r2p9m4x1（附件 3 小时有效，取：curl "https://ntfy.sh/zf-qj-k7r2p9m4x1/json?poll=1&since=12h" 拿 attachment.url）；道具键「放下」后 1.5 秒还拿在手上就提示游戏给的原因（CanUseThisItem/SpawnScenePropItem 日志）并自动导出+上传；KEYS 加 PropBase/evanlu/SpawnScenePropItem/CanUseThisItem/UseThisItem；诊断头加 Prop.useState。Bots.want（prefs zframe/bots_auto）：选单机/联机框里「单机自动刷人机：开/关」，人机页也有开关，回大厅 auto=want。
- 03:39 用户问能不能卸载补丁更新 App：libMczi 已删、没有插件；卸了以后 ZInit 走「上次保存的内核 / 内置内核」，内置 v2.52 有 NetCore 会链式加载下载的在线内核 → 可以卸。以后只有要改游戏包本体时才需要它重新打包。游戏自带更新页连腾讯服务器（已停）+ 弹窗在 Lua/UMG 里，没法复用；我们的更新检查放在选模式框里。
- 03:44 用户：游戏自带更新页早就去掉了，确认框也没有（点退出直接返回）→ 下一版把选模式/更新弹窗改成游戏风格（深色半透明底、黄色主按钮、白字），跟放下修复一起推。
- 03:50 用户（v2.55 实测）：对局里道具键没出现；卸了补丁更新 App 后仓库衣服不显示（原因待查，日志未收到）；自动刷人机开关要在大厅；选模式位置是对的；更新弹窗参考游戏「提示」框（金色标题条、灰取消/金确认），并要游戏那种更新进度条。
- v2.56 (vc157, 在线内核)：GameDlg.java 游戏风框（深色底、金标题条、取消/确认）；选模式框改成这个样式、去掉人机开关、下载时显示进度条（NetCore.getP 报 pct）；大厅单机版加「自动刷人机」开关；大厅里新版本下好弹「更新」提示框（取消/确认=重启）；每次启动进大厅 40 秒后自动导出+上传一份诊断（lobby-，查仓库空的原因）。
- 03:56 用户：道具能捡起来但没有道具键；地上建模是错的（替身箱子）；全部道具里用不了的还在。
- v2.57 (vc158, 在线)：道具键对局里一直显示（没认出道具时半透明，点一下 Prop.rawDump 把 3 个道具槽+背包列表的类名和 vt+0x394 记进诊断并自动上传）；全部道具刷出来但缺资源（Prop.missAt）或 402 道具没场景模型（target==null）也自动删掉。
- 03:59 用户：v2.57 选模式框标题条撑满全屏（FrameLayout 高度 -2 + 竖条 MATCH_PARENT）；而且弹在腾讯 logo 页上，应该在游戏自己的加载页。
- v2.58 (vc159, 在线)：GameDlg 标题条固定 42dp；Activity 类名含 splash/logo 时不弹选模式框（日志记「页面：类名」），Ed.await 15→25 秒。
- 2026-11-18 v2.69 (vc170，客户端+单机，b0/b2；b1 不动)：探测版。用户想让游戏自己的更新页显示我们的在线更新（像大厅那样在游戏里拦）。RE：更新页控件类（NZUpdateUIView）属性 UpdateProgressBar +0x208、UpdateProcessText +0x20c、UpdateProcessValue +0x210、NoticeImage +0x214 … 函数 OnUpdateProcess(InProcessValue float, InStageStr FString)、SetRichText、OnUpdateBtnClick；持有者 +0x24 UpdateFlowManager、+0x28 UpdateFlowTypes、+0x34 UpdateUI、+0x38 UpdateUIClass、+0x3c OnlineUpdate。腾讯 Dolphin 协议不模拟。UpdProbe.java：Con 看到 Browse StartupMap 时启动，每 2 秒扫 GUObjectArray 记类名带 Update/Logo 的对象（控件的 +0x208..0x214 类名、+0x24..0x3c 指向的 Update 类），启动阶段试一次 ucall(bx lr 0x9ab224)，90 秒或选择框关掉后自动传 ntfy（updprobe-*.gz）。未实测。
- 2026-11-18 11:40 v2.69 探测结果（updprobe ntfy）：启动阶段 ucall 不通（返回 0，Con.exec 还不能用）；更新相关对象只有默认对象（Default__NZUpdateUIView、NZStartupFlowUpdate* 等 CDO），实例只有 NZOnlineUpdateService_0、NZGCloudUpdate_0、NZUpdateInfo_0 → 这个包的启动流程根本不建更新页，借游戏更新页显示要自己 CreateWidget，暂不做。v2.70 起不再跑 UpdProbe。
- 11:12 用户（单机）：拾取有两次动作；开局不应该有毒圈；要在航线内才能跳伞（跳伞键提前开了）；刷的枪没有配件。
- v2.70 (vc171，客户端+单机 b0/b2；b1 不动)，未实测：
  - 开局毒圈：诊断 BlueCircle 日志开局就是 2, 432749, 140677,-192407 = 蓝圈半径 4327 米（系统 +0x3b0；+0x3a4..0x3ac 圈心）。来源：InitArea 0xd97b04 和换圈函数 0xd974d4（当前 -1 时）都把「上一圈」设成 hypot(+0x330,+0x334) 半径、第一项圈心 → 地图上能看到。改：zone cave B 出口在 +0x358>0 时 +0x3b0 = +0x3c0（第一项半径，整图 12240 米）；0xd97578 cc0a94edcd1a94ed → f00a94ed090000ea（vldr s0,[r4,#0x3c0]; b 0xd975a8）。zone_cave.py / emu_zone.py 测过。Tick 0xd97df0：+0x2c8 当前圈心 +0x2d4 当前半径，+0x2dc 目标圈心 +0x2e8 目标半径，+0x384 在缩，蓝圈 = 两者插值写 +0x3a4..+0x3b0。
  - 跳伞段：以前 = 整条航线（一起飞就能跳）。Flow 改成航线的 18%~92%（JUMP_A/JUMP_B），两处（airByValue / airline）都改，TotalJumpDist 按比例。
  - 拾取两次动作：BeginPlayingPickupAnim 0xd21bfc（清旧计时器 +0xbf8、设 1 秒计时器、+0xc00=1）被 C++ 0xf041e8 和 Lua thunk 0xe3ff20 各调一次。PickAnim.java：入口 b 0x171e200（GM GiveItem 死函数 0x171e1c8 中间），同一人物 1.5 秒内（GetWorld 0x273d080 +0x740 TimeSeconds）第二次直接返回；诊断显示调用/挡掉次数。pickanim_cave.py / emu_pickanim.py 测过。
  - 刷枪配件：背包里塞配件不会装上枪（日志没有 Arielchou Attaching adapter）。改成配件放脚下（cave G），走游戏拾取自动装；每把枪都给。ServerAttachAdapter 是背包组件 vt+0x330/0x334（0xfecca0，参数 物品, bool），没直接调。
- 11:27 用户：上把跳伞时闪退。lobby-1118-112527（v2.70）：系统日志只有 #370 以后的帧，全是 0x19bb448（0x19bb434：[r0+4]->vt+0x60 递归）= 栈溢出，顶部帧被截掉；Bugly 没留 tomb（栈溢出写不出），游戏日志备份最后停在 11:09:36，崩溃那段没写出来。原因不明。
  同一份诊断发现 v2.70 两处没装上：「拾取动作：空位字节不符」（比较少比 4 字节的 bug）；「毒圈底层：没装上（空位被占）」——SLACK+0x990 起在内存里本来就是文件里下一段的字节（不是 0），natInstall 要求全 0，所以 v2.45 起底层毒圈从来没装上过，一直是 Flow.circle 事后换数组在起作用。
- v2.71 (vc172，客户端+单机)，未实测：PickAnim 比较修好；开局蓝圈改在 Flow.circle 换完数组后写 +0x3c0/+0x3b0 = 第一项半径并打 ZF 补丁（0xd97578），newMatch 时撤掉 ZF（防止没换成数组时用游戏第一项 1500 米当开局蓝圈）。底层毒圈 natInstall 保持不动（继续装不上，不贸然启用）。
- 11:31 用户：刚进地图没有倒计时，还是有等人时间；毒圈刚上飞机就刷了。
  RE：v2.69 日志进图 10:57:46，阶段 0→1 在 10:58:46（正好 60 秒），虽然 WaitingPlayersCountDownTime=1。PostLogin 0x100bb88：第一个玩家进来（GS+0x730 没置）就用 GS+0x72c 设定时器（0x2deb32c）到点调 StartCountDown 0x100c4a8；人数够 GS+0x728 也直接调。读到的是 GameState 构造里的默认 60.0（读表那句还没跑）。构造三处：0x1017d10/0x101afb4/0x101fcb0 movt r0,#0x4270。
  毒圈「刚上飞机就刷」= 阶段 4 一开始就出的 4327 米蓝圈（BlueCircle 日志正好在 10:59:06 阶段 4 开始），v2.71 已处理。
- v2.72 (vc173，客户端+单机)，未实测：单机大厅加载 6 秒后把三处构造默认等人时间改成 1.0（Mods.WAIT_CTOR，诊断「单机等人默认 60→1 秒（构造）」）。
- 11:54 用户（v2.72 实测，截图雨林）：开局还不是直接读秒；地图显示在缩毒却没圈；要「飞天」（参考弹射板的跳跃和动作，高度自定）；加速改到 67 倍、不要加快动作；翻墙秒翻；水里开加速很快没氧气；丢枪/配件到地上直接消失。
  分析：缩毒看不到圈 = 第 1 项半径是边长×1.5（雨林 6120 米），缩 240 秒里前一大半蓝圈都在地图外。
  RE 飞天：弹射板 LaunchPlayer 0x11417fc → 人物+0x114c LaunchPadComponent，0x1105428(组件, &info) 抄 0xd8 字节 LaunchPadRepInfo 到 组件+0xc8 并开 Tick；Tick 0x1104c0c 状态机 +0xac（1 Init→2 Launch→3 Rise→4 Fall→5 Land…）；0x1104720 起跳速度 = 方向(LaunchAngleByDegree +8) × LaunchVelocity(+0xc) → +0x1a8；下落用 GravityWhenFall(+4)。CMC MaxSimulationTimeStep 0x274 / MaxSimulationIterations 0x278。
- v2.73 (vc174，客户端+单机)，未实测：
  - 单机读秒：再改 PostLogin 定时器那句 0x100bcc8 vldr s16,[r0] → vmov.f32 s16,#1.0（Mods.WAIT_PL，和构造补丁一起打）。
  - 毒圈第 1 项半径 边长×1.5 → ×0.72（刚盖住地图四角，一开始缩就看得见）。
  - 新 Fly.java「飞天」：功能页高度滑条 10~500 米 + 按钮；自拼 info（G=1960，角度 75，竖直速度 √(2gh)，延迟 0.05/起跳 0.2/落地 0.8/0.4，特效路径空）→ ucall(组件, info, 0x1105428)。角度含义（离水平还是离竖直）没核实。
  - 加速：滑条最大 67；只在走路模式（1）加速，游泳/翻越/其他特殊模式恢复 1 倍（翻墙秒翻、水里氧气）；倍数 >2 时 MaxSimulationIterations 加到 ⌈倍数×0.8⌉+2（最多 100），关了还原。
  - 丢东西：Con.dropLog 记含 Drop/PickUpWrapper/Discard 的游戏日志，诊断里「丢东西日志」。原因还没查。
- 12:21 用户（v2.73 实测）：还是不刷毒；67 倍动作也变快（忘了放慢）；飞天不要弹射板、要同款效果，v2.73 那版用不了；水里别取消加速，只要别加快掉氧气；翻墙要慢放；修地图标点；加「点地图标点自动传送」。
  日志 lobby-1118-120656：v2.73 读秒已好（进图 1.6 秒就 阶段 0→1）。毒圈表第 0 项等 120 秒 + 第 1 项等 420 秒，第一次看得见的缩圈要 9 分钟后 → 用户以为不刷毒。
- v2.74 (vc175，客户端+单机)，未实测：
  - 毒圈：去掉中间项，第 0 项整图等 min(firstWait,60) 后用第一圈的缩圈时间直接缩到第一圈（白圈一开始就有）。服务端 v2.70 同改。
  - 动作：M_RATE 下限 0.1 → 0.005（67 倍时以前被卡在 0.1，动作快 6.7 倍）。
  - 游泳：时间倍率保持 1（氧气正常），MaxSwimSpeed(+0x16c) = 原值×倍数。翻越等特殊模式仍 1 倍。
  - 飞天重写：不用弹射板组件，移动线程把 MovementMode 改 3、Velocity.Z = √(2·980·h)，下落最快 -7 米/秒。
  - 地图标点：RE 到 ServerAddNewMapSign（NZBRPlayerState，exec 0x11e528c，Validate vt+0x3f0 / 实现 vt+0x3f4，参数 8 字节 InNewLoc），标点存 NZBRTeamMemberInfo.MapSignLocation / bIsMapSignEnable；实现函数和失败原因还没找到。点标点传送未做。
- v2.75 (vc176，客户端+单机)，未实测：用户要飞天一直飞、关了才落。Fly 改开关：MovementMode=5（Flying，无重力），前 h/20m·s⁻¹ 时间 Velocity.Z=2000，之后 0 悬停，每 40ms 重写；关时改 3，下落限速 -700。
- v2.76 (vc177)，未实测：用户要空中能移动。飞行时 MaxFlySpeed(+0x170) = max(600, MaxWalkSpeed)×加速倍数，关时还原；诊断「飞天：飞行中 模式/水平速度/飞行上限」。
- 12:40 用户（v2.76）：飞天开着跳伞掉不下去，关了也不行（截图跳伞 26 km/h = 关后的 -7 m/s 限速一直在管跳伞，跳伞也是模式 3）；空中是走路动作（模式 5）。诊断：飞行中 水平速度 450 → 空中能动。地图标点还是不行。
- v2.77 (vc178)，未实测：飞天改下落模式 3 + GravityScale(0x120)=0，AirControl(0x19c)=1，FallingLateralFriction(0x1a8)=3，MaxWalkSpeed×倍数（偏移已用反射表核对）；模式不是 1/3、换人物、Bots.phase 变了就自动关并还原；关后限速最多管 (h/7m·s⁻¹ + 上升时间×3 + 5 秒)。
- v2.78 (vc179)，未实测：地图标点。RE：ServerAddNewMapSign(FVector2D InNewLoc) 在 NZBRPlayerController（同表有 ServerSetTacticsInfo/ClientSetTacticsInfo），exec 0x11e528c；控制器 +0xa04 = MapSignInfo（NZBRMapSignInfo 0xc 字节）；TeamMemberInfo.MapSignLocation +0x30。控制器 = 人物+0x2c4(PS)→+0x60。
  MapMark.java：进对局起每 0.5 秒看 控制器+0xa04，变了写「标点日志」；开「点地图标点自动传送」→ Veh.teleport 到 (X,Y,max(当前z+30m,200m))，Fly.softLand 限速 -10 m/s 落 40 秒。标点本身显示不出来的原因还没定位，等这版日志看 +a04 有没有变。
- v2.79 (vc180)：标点变化 5 秒后自动传诊断（mark-，30 秒一次）；一局结束回大厅 8 秒后自动传（matchend-）。
- 12:55 诊断（v2.79）：标点 控制器 BP_NZBRPlayerController_C，+a04 一直全 0 → 点大地图标点根本没写到 MapSignInfo（不是 UI 显示问题，或者 RPC 没调到/实现写别处）。12:50:20 启动 2 秒闪退一次，又是 0x19bb434 递归栈溢出（和 11:24 同一个），重开正常。离线找 NZBRPlayerController 虚表没成（vtable 在重定位里）。
- v2.80 (vc181)：诊断加控制器虚表 0x3e8~0x3f4 函数地址（减基址），下次离线反汇编加标点实现。
- 13:02 v2.80 日志：BP_NZBRPlayerController_C 虚表 +3e8=2c5bbfc +3ec=b63518 +3f0=2c6151c +3f4=b63518。0xb63518 = 「mov r0,#1; bx lr」→ ServerAddNewMapSign/ServerRemoveMapSign 的实现在这个包里是空的。用户截图：标点总落在地图左上角（显示 888m）。
  RE：NZBRMapSignInfo(0xc) = Location FVector2D +0、bEnable +8；控制器 MapSignInfo +0xa04。NZBRTeamMemberInfo(0x50)：Location +0x14、Rotation +0x20、bIsMapSignEnable 推测 +0x2c、MapSignLocation +0x30、MemberIndex +0x38、Health +0x3c…；某个 Team 类 +0x2f4 = TeamMemberInfoList。
- v2.81 (vc182)，未实测：SLACK+0xa90 放两段（加：str r1/r2 → +a04/+a08、+a0c=1；删：+a0c=0），改控制器虚表 +0x3f4/+0x3ec 指过去（只在原值是 0xb63518 时改）。MapMark 找 PS/控制器里类名带 Team 的对象，日志打印 TeamMemberInfoList；标点变了且成员只有 1 个时把位置/开关同步进去。
  注意：build_cli.sh 编译失败也会打印 built 并且已改版本号 → 失败后要 git checkout GameOverlay/ZInit/core/assets/build 再重跑。
- 13:10 v2.81 日志：SLACK+0xa90 起「空位被占」（9c718a01d3488204…，像数据），SLACK+0x990 之后都不是空的 → 毒圈 natInstall 一直装不上也是这个原因。队伍：PS+0x35c NZBRTeamInfo，成员 0（单机不填）。
- v2.82 (vc183) 未实测：标点补丁挪到 SLACK+0x3d0（Prop 用到 0x368，Veh 从 0x400 起）。
- 13:16 v2.82 日志：SLACK+0x3d0 也「被占」。查 ELF：代码段 filesz 到 0x4a2d528，数据段 vaddr 0x4a2e528 另外映射 → SLACK(0x4a2d540~0x4a2e000) 里是文件里数据段开头的副本，非 0 但可以随便写（Veh 一直就是直接写）。毒圈 natInstall「空位被占」也是误判，+0x990 那段（0x12c 字节到 0xabc）其实能装 —— 还没改，改了会第一次启用底层毒圈，要单独一版。
- v2.83 (vc184) 未实测：MapMark 去掉「必须是 0」的检查，直接写 SLACK+0x3d0。
- v2.84 (vc185)：用户要求不要自动退回老版本 → NetCore.chain 去掉「连续 2 次没进大厅就不用在线内核」。注意：以后若新内核启动必闪退，只能清游戏数据/重装 ZFrame 才能回内置版本。
- 13:28 v2.84 日志：补丁装上（加/删标点已补）但用户标点后 控制器+a04 仍全 0 → 要么 RPC 没走到实现，要么走到的是服务端那个控制器。v2.85 (vc186)：cave 挪到 SLACK+0x370，加标点时记 次数/x/y/this（+0x30），日志里打印并自动上传；虚表多记 +0x3f8（校验实际函数）。未实测。
- 客户端 v2.86 (vc187) + 服务端 v2.72 (vc173)：在线更新改成并发查 6 个镜像取最大版本。
- 14:21 用户：服务端菜单/功能比单机差太多（刷道具不行）；还是一直换子弹；重新加入加入不进去。客户端 v2.86 日志 matchend-1118-141454：14:13 加入 frp-put.com:47937 成功，14:14:25 ConnectionLost（FRP/网络断），之后重进没日志；联机日志 60 行被「per Net Tick, too much」刷满。
- v2.87 (vc188/core188，三个包都从 main 打，build_all.sh)：服务端不再用 server-2.55 分支，改用 main 源码（菜单/刷道具/功能同单机最新）。移植服务端分支：v2.67 服务端不能上车 cave（Lan，只 BUILD==1 装）、v2.68/69 加载页查更新框（srvUpdatePage，白底）、大厅页精简、v2.72 多镜像更新。联机日志去掉刷屏行、加 Reconnect/ReLogin/Kick/Reject 关键字、150 行。换弹循环、重进失败：等服务端+客户端诊断。未实测。
- v2.88 (vc189，三包)：用户不想点导出诊断 → Lan.autoUp：断线/加入失败(NetworkFailure/TravelFailure/ConnectionLost/Connection Closed/PreLogin failure/服务端 Closing connection)传 net-*，换弹循环(客户端 Force set ammo / 服务端弹匣 0 超 2.5 秒)传 ammo-*，同类 90 秒一次。未实测。
- 14:39 用户：换弹了。ammo-1118-143620（客户端）：信号枪 ClientAmmo=1 ServerAmmo=0 每 6 秒强制同步+换弹动画；之前客户端本地刷配件 Can't Find Item → 客户端菜单 AddAvaItem 只在本地加，服务器没有 → 一直换弹。v2.89 (vc190)：Ed.ED==2 且已加入(Lan.role==2) 时 give() 不在本地刷，提示让服务端刷在脚下。服务端诊断 lobby-1118-143444 已能自动上传。未实测。
- v2.90 (vc191)：Give.java 客户端代刷：加入后客户端点刷 → ntfy 主题 zf-qj-give-k7r2p9m4x1 发「编号 数量 x y z」（Veh.ahead 拿自己位置），服务端开房后每 2 秒拉，Veh.dropAt 在该坐标生成。诊断里有「代刷：」行。未实测。用户下一步要把服务器做成能在云服务器跑（计划：ARM Linux + Redroid 跑安卓版服务端）。
- 15:11 用户（v2.91 实测）：客户端还是一直换弹；客户端少刷车、风衣；配件只有步枪的、扩容没有快速扩容；要刷油桶；加入玩家会和房主成队友；客户端没加速；试五军对决等娱乐模式能否开房；「取消不刷毒服务端情况下上限人数」（意思没确认，已问）。ammo-1118-150815（服务端）：加入玩家枪服务端弹匣 0 超 2.5 秒 172 次，动画修正生效但没用；客户端 Force set ... ClientAmmo=30 ServerAmmo=0 每 4 秒。
- v2.92 (vc193/core193，三包，未实测)：
  - 客户端分页加回 刷车(1)、衣服(7)；刷车走 ntfy「V<车组> 1 x y z」→ 服务端 Veh.spawnGroupAt（前方 6 米）。
  - 客户端功能页加速/高跳拉条 → ntfy「S<加速>_<高跳> 1 x y z」（停 0.7 秒才发）→ 服务端 Crew.setFor 找离坐标最近的加入玩家（RootComponent +0x128，平移 +0x140），Crew.over 按人存；tick 里该玩家 PS.Score 写他自己的编码、时间膨胀/跳跃用他自己的数。
  - 全套配件改成读游戏物品表扫到的全部 205 类（没扫过先扫）；新「油桶」键（从认出名字里找 油桶/Barrel/Gas，没认出就提示，编号未知）。
  - 换弹：服务端弹匣 0 超 2.5 秒 → 从 +0x60c 备弹扣、装进 +0x604/+0x608（不白给；若备弹是从背包实时算的，可能扣不掉 → 看日志「换弹：服务端替加入玩家装弹」）。
  - 队友：Crew.team：PS+0x35c TeamInfo，TeamInfo+0x2b0 TeamIndex（反射表）。不同对象同 TeamIndex → 改加入玩家的；同一对象只记日志。诊断「队伍：」。
  - 服务端开房加「五军对决（试）」「BFA 模式（试）」：新海岛 + ?game=/Script/NZBattleRoyaleGame.NZBRLegionGameMode / NZBRBFAGameMode（模块名是猜的；日志关键词加 GameMode/Legion/BFA）。
- 15:23 用户解释「取消不刷毒服务端上限人数」= 服务端一直开着，任何时候都不要停止接收别人加入。RE：AGameSession::ApproveLogin 0x2a01600 满员回 "Server full."；InitOptions 0x2a011c4 读 ?MaxPlayers= 存 GameSession+0x2b4。PreLogin 0x1009890 只拒绝「重连找不到控制器」，没有按开局拒新人。
- v2.93 (vc194/core194，三包，未实测)：Lan.host 开房命令加 ?MaxPlayers=100。
- 15:24 用户：信号枪召唤空投修复。日志：客户端开了信号枪（FlareGun_Stand_Fire_Gun role 2），服务端没有任何 AirSupply/飞机日志 → 游戏的信号枪空投不起作用。
- v2.94 (vc195/core195，三包，未实测)：Loot.flareLine 兜底——客户端自己开信号枪（role 2）→ ntfy「A 1 x y z」→ 服务端在前方 8 米放 AWM+.300×2+3 级头/甲/包；服务端/单机自己开（role 3）直接放。15 秒内一次。诊断「信号枪空投：」。
- 15:26 用户：信号枪别在面前放物资，从根上修；换弹也从根上修。
  RE（NZWeaponAmmoComponent.cpp）：0xefce1c = 状态事件处理（事件 9/11 开火扣弹，17 BeginChangeClip 打日志，18~20 置 +0x29，21 → 0xefd13c）。0xefd13c = 换弹结束：备弹 = 背包(组件+0x2c，= 人物 vt+0x430 → +0x888) vt+0x2f8(弹种 +0x28)；弹匣 = min(容量 +0x34, 现弹+备弹)（+0x29 非 0 时一发一发）；权威端(枪 +0xe8==3) 背包 vt+0x2fc 扣；写 枪+0x604(权威)/+0x608。0xefd460 = 客户端每 2 秒对不上就把本地弹匣强制成服务端值（日志 Force set ...）。组件 +0x20 = 枪，+0x24 = 容量。
  结论：服务端加入玩家的枪事件 21 没跑到 → 服务端弹匣永远 0。信号枪同根：服务端弹匣 0 → 服务端不认那一枪 → 不叫飞机。
- v2.95 (vc196/core196，三包，未实测)：撤掉 v2.94 信号枪放物资兜底；撤掉 v2.92 自己写弹匣。服务端加入玩家枪弹匣 0 超 2.5 秒 → 找枪身上弹药组件（[q+0x20]==枪、[q+0x24]==容量），ucall 游戏自己的换弹结束 0xefd13c(组件)，日志「换弹：服务端调游戏换弹结束 … 弹匣 0 → N，备弹 A → B」。
- 15:51 用户（v2.95 服务端）：开「五军对决」（?game=/Script/NZBattleRoyaleGame.NZBRLegionGameMode）加载 5 秒闪退；15:52 又复现一次（crash-1118-155417）；BFA 那次结果未知。
  RE / 证据：
  - 类名、模块名是对的：两次游戏日志都有 `Game class is 'NZBRLegionGameMode'`、NZBRGameMode created/inited，然后都停在 `SetViewTarget---NewViewTarget = NZPlayerController_0, Role = 3`。正常开房（BP_NZBRIslandGameMode_C）生成的是 BP_NZBRPlayerController_C_0。
  - ANZGameMode 构造 0xc786c8：HUDClass(+0x2cc)=ANZHUD、GameStateClass(+0x2c0)=ANZGameState、PlayerStateClass(+0x2c8)=ANZPlayerState、PlayerControllerClass(+0x2c4)=ANZPlayerController。ANZBattleRoyaleGameMode 构造 0x1007b7c、ANZBRLegionGameMode 0x1062cd4、ANZBRBFAGameMode 0x105253c 都不改这几个类（只设 DsLogic +0x4fc 等）。→ C++ 底层类不能直接当模式用，能玩的配置在各模式的蓝图子类里；直接开 = 吃鸡/三军逻辑拿到基础 GameState/控制器 → 闪退。libUE4 里没有任何 Legion/BFA 蓝图路径。
  - 那份 bugly tomb（15:49:47，fault 0x6f）不是这次的：栈是 android_main → FEngineLoop::PreInit(0xb63a90) → AppInit(0xb6f5c4) → FConfigCacheIni::InitializeConfigSystem(0x1a2de90) → LoadGlobalIniFile(0x1a2ea98) → 0x1a31f44 遍历 ini 层级 TMap（+0x1c 位数组指针被写成 0x6f），是启动读 ini 时的崩溃，另一回事（原因未查）。开房那两次没有 tomb：Bugly 启动 5 秒后被服务器策略关掉 native 捕获（日志 "server native changed to false"），所以对局里的闪退都不会留 tomb。
  - 游戏内中文名（libUE4 0x16632ac 本地化默认文本 NSCommon/MatchModeName_N）：0~2 匹配赛、3 速战速决、4 PVE模式、5 狙击精英、6 冷兵器战、7 神装训练、8 绝地训练、9 专家模式、10 决胜五军、11 专家模式、12 迷雾求生、13 三军对垒。Legion 的 GroupCountMax（反射偏移 +0xa78）构造里默认 3 → NZBRLegionGameMode = 三军对垒；NZBRBFAGameMode = 决胜五军（推断：BFA≈Battle of Five Armies，有 MaxGroupCount/组淘汰）。其它 C++ 模式类：NZBRBTTGameMode、NZBRCompactGameMode、NZBRCustomItemGameMode、NZBRPracticeGameMode、NZBRTrainingGameMode、NZBRTslGameMode，模块都是 /Script/NZBattleRoyaleGame。
- v2.96 (vc197/core197，三包，未实测)：新 ModeProbe.java。开房按钮改名「三军对垒（试）」「决胜五军（试）」，点了先扫游戏名字表（Veh.Names，GNames）找含 Legion/BFA 且像 GameMode 蓝图的 /Game/ 路径 → ?game=/Game/…/X.X_C 开房；找不到就不开房（底层类必闪退），提示「没找到模式蓝图」。命中的相关名字写联机日志并自动上传 mode-*。诊断加 ModeProbe.state；联机日志收 "Game class is" 行。

## v2.97（vc198/core198，2026-11-18 16:4x，三包，未实测）— 换弹/载具根因
用户 16:21 实测 v2.95：换弹没修好；开车有延迟，船开上岸还能开、一下车又回到上岸的边上。日志 /tmp/L（服务端 9qA34gSRdoME = FxKRgIq0cLMU 同一份；客户端 gUqhTgVIj4Ak / 9gkOSlIFmOjc / AH9XAhdoaF7B）。
### 换弹根因（静态 RE + 日志对得上）
- 客户端比较的是 枪 vt+0x528 = 0xf0e410 `ldr r0,[r0,#0x604]`（ServerWeaponAmmoCount，复制字段）对 vt+0x514 = +0x608。0xefd460（弹药组件 tick，role≤2 才查）不一致 2 秒就打 "Force set local front ammo count to server value" 并把本地改成服务端值 → 0 → 自动换弹。
- 游戏所有「设弹匣」的地方都是 `bl 0x274dbb0(AActor::GetNetMode); cmp r0,#1; streq rX,[枪,#0x604]; str rX,[枪,#0x608]` —— 只有专用服（NM_DedicatedServer=1）才写 +0x604。我们的房主是 listen server（NetMode 2），+0x604 永远是初始 0。共 9 处：0xeda308（补满）、0xedbc94、0xefcc94（清零）、0xefce0c、0xefd110（开火扣弹）、0xefd43c（换弹结束 0xefd13c 末尾）、0xefd618（客户端强制同步路径）、0xf0e334、0xf0e4a4（清零）。
- 换弹 RPC 本身是通的：ServerProceedWeapRepInfo(uint8) 标志 0x80220cc0（可靠），实现 = 武器 vt+0x384 = 0xed7d38 → vt+0x4bc HandleStateEvent 0xed7124（遍历 武器+0x43c 组件数组调 vt+0x120）→ 弹药组件 0xefce1c 事件 21 → 0xefd13c 换弹结束（读背包 +0x2c vt+0x2f8、权威端 vt+0x2fc 扣）。客户端发 RPC 的地方：0xed7124 里 role≤2 时 0xf1ff10(武器, 事件)（ProcessEvent）。所以服务端换弹逻辑在跑，只是结果没写进同步字段。v2.39 的动画假说、v2.92 写弹匣、v2.95 手调 0xefd13c 都是绕路。
- 修：Lan.ammoInstall（Lan.preinstall 里、启动时）把 `cmp r0,#1` → `cmp r0,#3`，`streq/moveq` → `strne/movne`：非客户端（单机 0/专用 1/听服 2）都写 +0x604，客户端（3）不变。诊断行「听服弹匣同步…9/9 ok」。
- Crew.guard 去掉 v2.95 的 ucall 0xefd13c（会和游戏自己的换弹叠加重复扣弹），只留检测（备弹>0 且服务端弹匣 0 超 2.5 秒才记+自动传 ammo-）。
- 「全员：找到加入的玩家 0 个（共 2）」：find 用「人物 == 自己就跳过」，房主没人物（不上飞机）时 me=0、加入玩家人物读到 0 也被跳过。改成看 控制器+0x2fc Player 类名：NetConnection = 加入玩家，LocalPlayer = 房主；诊断里打印跳过原因。
- 信号枪叫飞机：同一根因的推测（服务端弹匣 0 不认那一枪），未验证。
### 载具根因（日志证据 + 引擎规则；部分推断）
- 服务端 15:55:39 "Client netspeed is 10000"（客户端按引擎默认 ConfiguredInternetSpeed 报 10KB/s），加入时 "Network saturated. NZIpNetConnection_0 skip replicated"（ServerReplicateActors 这帧不给这台客户端复制），客户端 16:03~16:04 每帧 "UNZIpNetConnection [198.18.0.24:47937] Saturated"（上行每帧 ~120 字节 × 80 帧 ≈ 10KB/s 撞上限）。
- 引擎规则：连接饱和时不可靠 RPC 直接不发（UNetDriver::InternalProcessRemoteFunction）。反射标志：ServerMove 0x80220c40、车 ServerUpdateState 0x80280c40（带刚体状态，0x3df4d28 → 移动组件 vt+0x2bc）都是不可靠的；ClientAdjustPhysicState 0x1080c40 也不可靠。→ 服务端收不到车的新状态、客户端收不到纠正：开车延迟；船在客户端开上岸，服务端那边停在岸边（16:04:54/58/16:05:04 三次下车 VehicleLoc 都是 385884,-380189 附近），下车按服务端车位置算出口 → 被拉回岸边。
- 听服额外：AGameNetworkManager::UpdateNetSpeeds 0x29f6d70（NetMode 0/1 不跑；listen 跑）按 Total(+0x2b8)/人数 夹在 Min(+0x2bc)/Max(+0x2c0) → 每个连接再被限一次；APlayerController::SetNetSpeed 0x2c595b0 按 NetDriver MaxClientRate(+0x3c) 夹，写 Player(+0x2fc)+0x24 和 ServerConnection(NetDriver+0x60)+0x24。
- 服务端日志里没有 "Call ClientAdjustPhysicState"（Warning 级，0xdd0468）也没有 "Server adjust rigidBodyState"，说明不是服务端在主动纠正车，而是两边状态没传过去。车的同步开关在 GameState +0x64c/+0x64d/+0x64e（bEnableOptimizeSyncVehiclePhysicState / bEnableClientAdjustByDSPhysicState / bEnableVehicle1PAuthority，0xc90ff4 打日志）。车代码里还有不少 GetNetMode()==1 的分支（0xdafe88、0xdd4b94、0xdf15a8、0xdfa9cc、0xdbd6cc、0xe0ab78 等），可能也有「只给专用服」的问题，没逐个确认、没改。
- FRP 本身也有抖动：服务端多次 "Not Recived Data for a Long Time"（0.2~7 秒），16:05:30 起客户端完全没数据，150 秒后超时断开（客户端 16:04:07 切后台过一次）。这部分不是配置能修的。
- 修：新 NetRate.java（Lan.host/openJoin 启动，进对局后每 2 秒查一次）：World = 本机控制器(+0x18 Level)+0x18；NetDriver = World+0x24（类名含 NetDriver）MaxClientRate +0x3c、MaxInternetClientRate +0x38 → 100000；服务端：GameNetworkManager = World+0x310（类名核对）Total→3200000、Min/Max→100000，每个加入玩家连接（PS+0x60 控制器 +0x2fc，类名含 NetConnection）CurrentNetSpeed +0x24 → 100000；客户端：ServerConnection（NetDriver+0x60）和 LocalPlayer（控制器+0x2fc）+0x24/+0x28/+0x2c → 100000。只改不足的（比 100000 小才写）。诊断行「联机带宽（…）」。联机日志里 Saturated 行改成计数（每 100 条留 1 条）。
- 偏移来源：反射表 UNetDriver NetServerMaxTickRate 0x34 / MaxInternetClientRate 0x38 / MaxClientRate 0x3c；UPlayer ConfiguredInternetSpeed 0x28 / ConfiguredLanSpeed 0x2c；AGameNetworkManager TotalNetBandwidth 0x2b8 / Min 0x2bc / Max 0x2c0 / ClientNetSendMoveThrottleAtNetSpeed 0x304。World+0x310 = NetworkManager 是从 0x2992888 推断的，代码里核对类名。
- 新工具：game/re/fstart.py（找函数头）、vtslot.py（函数在哪些虚表第几格）、vcall.py（找 ldr [rX,#off]+blx 的虚调用）。
- 未实测。要看：客户端还有没有 "Force set local front ammo count"；诊断「听服弹匣同步 9/9」「联机带宽」两行；开车/船上岸下车还被不被拉回；若还拉回，看服务端有没有 Saturated / skip replicated、看车 GetNetMode()==1 分支。

## native-prop 上一轮结论（2026-11-18，补记，静态 RE）
- 道具组件 = 人物+0x1160（UNZBRPlayerPropComponent），组件+0xbc = 人物，组件+0xb8 = 读条动作。v2.61 用 控制器+0x1160 是错的（闪退）。
- 原生回收：客户端 StartRecycleItem 0x110d6a0（要求 场景道具+0x2ec 回收时长>0；NetMode≠0 时先在 组件+0xb8 上本地读条 0xcde1f0(动作, 时长, 6, 1) 并绑 OnCancel/OnFinishRecycleSceneItem，再 0x11e2f10 RPC ServerStartRecycleSceneItem；NetMode 0 不起本地读条直接 RPC）。服务端实现 0x110f334（组件虚表 +0x210，校验 +0x20c）：人物 vt+0x680、场景道具 vt+0x358 OnTriggerRecycle 0x1143234（记表 +0x3e4）、绑 OnCancelRecycleSceneItemServer、在同一个 组件+0xb8 上 StartAction(6)、人物+0xd28=0。场景道具 Tick vt+0x204 0x1134df0（要 +0x2cc>0）倒计时 → vt+0x360 OnRecycle 0x1143560（距离−300 > CanRecycleDist +0x420 失败，日志 "Error Recycle Distance"）→ ClientFinishRecycle → RecycleDestroy 0x1143d38（置 +0x439，按 +0x428 定时）→ DoRecycleDestroy 0x116d09c：0xd11a48(人物, 场景道具+0x320 编号, 1, 0, 6=Recycle) 还背包，0x273e8d4 删除。
- 拿出/收起：背包 vt+0x4d0 / +0x4d4 就是原生道具格按钮做的事，本来就是原生。
- 原生 UseItem 单机停在 StopPreUsingItem（根因见下面 v2.98）。PreUseItem 背包 vt+0x494 0xcdd510；FinishUsingItemCastTime vt+0x4b8 0xcde9f4（要求 0xcdb0ac(背包)==背包+0x2c0 CachedPreUsingItem，然后道具 UseThisItem vt+0x348 → 0xcdf058 扣数量）。
- 原生道具栏 UMG（UNZBRPHUDWidget_PropsBar*）只由 pak 蓝图创建，经典界面没有，改标志出不来。

## v2.98（vc199/core199，2026-11-18 17:xx，三包，未实测）— 道具放下/收回原生化
### 放下断点根因（静态 RE，NZInventoryManager.cpp）
- 链：背包 vt+0x484 0xfe98b0（选中且 +0x336 放置点有效）→ vt+0x480 UseItem 0xcdcdfc（道具 vt+0x33c CanUseThisItem==1）→ vt+0x494 PreUseItem 0xcdd510（权威端：准备时长>0 时定时器回调 0xcdddb8，否则直接）→ 0xcddf54 PreUseItemPrepareFinish：StartAction(人物+0xce4 ActionWithCastTimeInstance, 道具+0x318, 类型1)、背包+0x2c0 = 道具、道具 vt+0x340 StartPreUsing（+0x311 状态→2）。定时器回调还会调 ClientPreUseItemPrepareFinish（RPC，单机本地执行 0xcde05c → 再来一遍 0xcddf54 → 第二次 StartAction「already active, force to abort」→ 动作 OnAbort(+0x28) → AbortPreUsingItem 0xcdcba0 → 收尾后又被第二次重新置好，最终状态一致）。
- 读条完：动作 OnFinish(+0x1c) 绑的是 UFunction FinishUsingItemCastTime（exec 0xe67348 → vt+0x4b8 0xcde9f4，绑定在 0xcdc280）。它先算 r5 = 0xcdb0ac(背包)（vt+0x400 列表里状态 +0x311==2 的道具），然后**权威端 + 背包+0x2c0≠0 + 人物+0xb34 bIsBot==0 时调 ClientStopPreUsingItem(道具)（0xcdea88，vt+0x4a8 = 0xe66908 ProcessEvent）**。专用服务器上这是发给客户端的 RPC；单机 NetMode 0（以及房主自己的人物）上 Client RPC 本地执行 → ClientStopPreUsingItem_Implementation 0xcde77c → vt+0x4a0 0xcde5e0：清定时器、动作 Abort、道具 vt+0x37c StopPreUsingItem（日志 "[marvinsong]ANZItem_PropBase::StopPreUsingItem"，状态 2→1）、**背包+0x2c0 = 0** → 回来 `r5 == 背包+0x2c0` 不成立 → 日志 "FinishUsingItemCastTime failed …" → 不调 UseThisItem（生成）也不调 0xcdf058（扣数量）。人机 bIsBot=1 跳过这个 RPC，所以游戏自己的人机放盾能成。
- 反射/字符串证据：+0x2c0 = CachedPreUsingItem（反射表）；+0xb34 = bIsBot（布尔属性 setter 0xe47838）；FName 表 0x51486a0：+0x40 ClientPreUseItem、+0x48 ClientPreUseItemPrepareFinish、+0x50 ClientStopPreUsingItem（初始化在 0x8e07a4）。
- 修：0xcdea88 两条（ldr r0,[r4]; ldr r2,[r0,#0x4a8]）→ `ldr pc,[pc,#-4]` 跳 cave SLACK+0x3d0（game/re/fin_cave.py，unicorn game/re/emu_fin.py 三种情况：本地清 0 → 写回；远端不变 → 不动；被改成别的 → 不动）：push{r1,r2}；照原样调 ClientStopPreUsingItem（客户端那半边的收尾照做）；pop；`背包+0x2c0==0` 就写回原道具；回 0xcdea98 走原成功分支（UseThisItem → SpawnScenePropItem + 0xcdf058 扣数量）。专用服务器/远端客户端行为不变。Prop.finInstall，进对局图时装（Prop.matchLoad），诊断「放下修复：已装」。
- UseThisItem 之后：CanUseThisItem（状态 1 不忙）→ 状态 3 → vt+0x3a4 生成。CanUseThisItem 失败码 5 = 拥有者(+0x2b0)/其背包(+0x838)空，或 拥有者 vt+0x918（ANZBRCharacter 0x10e6690：PS(+0x2c4)+0xa14 MapPlayerState==5；5 = 人物+0xfe4 置位，0x8c8 那格里置，像倒地）。预览红 = 道具 Tick 0xf2e124 里放置点 vt+0x3b4 无效，或人物 vt+0x4ac（+0xae8，载具）非空。
### 收回
- 去掉 v2.66 Prop.destroyBack（删掉再刷回）。NetMode 0：调 0x110d6a0（它在 NetMode 0 不起本地读条，直接本地执行 0x110f334，单读条）；房主 NetMode 2：直接调服务端实现 0x110f334（避开 0x110d6a0 本地读条 + 服务端读条在同一个 组件+0xb8 上 StartAction 两次互相 Abort → OnCancelRecycleSceneItem → StopRecycleItem）。调前核对 组件=人物+0x1160、组件+0xbc==人物、组件 vt+0x210==0x110f334、场景道具+0x2ec>0。NetMode 用 Lan.role 推（1→2、2→3、其它 0）。伪装岩仍走 0x110dc50。
- 放下后找场景道具：游戏不返回指针 → 扫 GUObjectArray（*0x51250ac +0x10 Objects / +0x18 个数）找 类==道具 TargetProp(+0x3f0) 的新对象，记进 placed 给收回键用。
### 诊断（Prop.d → 诊断头「道具诊断」，自动上传 prop-*）
- 放下：拥有者/拥有者背包/PS+0xa14/人物+0xfe4/+0xae8/NetMode/修复状态；之后每 0.5 秒记 数量、+0x311、背包+0x2c0/+0x2c4、读条（人物+0xce4：+0x48 进行中、+0x5c 类型、+0x34 时长），10 秒；失败或没找到场景道具自动上传。
- 收回：时长 +0x2ec、可回收距离 +0x420、距离；每 0.5 秒记 +0x3e4/+0x3e8/+0x2cc/+0x438/+0x439/存活/读条，20 秒；完成/失败都上传。
- 期间游戏日志含 PreUseItem/FinishUsingItemCastTime/StopPreUsingItem/AbortPreUsingItem/Recycle/StartAction… 的行也记进去（AvatarLog KEYS 也加了）。
- 进图 8 秒后扫一次 GNames 含 PropsBar 的名字（ModeProbe.find），诊断里列 /Game/ 路径个数和前几条，只看资源在不在。
### 顺手修的冲突
- MapMark 补标点 cave 在 SLACK+0x370，但 Prop 地上模型 cave（代码 0x68 + 6 个字面量）实际占到 0x380 → MapMark 装上后把 Prop cave 末尾 4 个字面量盖掉，之后地上模型加载回调走到 cave 会跳飞。MapMark 挪到 0x380（到 0x3cc），0x3d0~0x3fc 给放下修复 cave。
- 删掉临时 game/re/_pp.py。新工具 game/re/ldrscan.py（找 ldr [rX,#imm]+blx）、pcref.py/pcref2.py（pc 相对引用）、offname.py（偏移→反射属性名，结构对齐不准，仅参考）、dref.py（数据段指针引用）。
- 未实测：单机/房主放下是否读条后生成并扣数量；预览会不会一直红（v2.63 报过 CanUseThisItem 5）；收回读条能否走完（距离、+0x2cc）；房主收回加入玩家放的道具不支持（只收自己 placed 里的）。

## v2.99（vc200/core200，2026-11-18 17:1x，三包，未实测）— 配件根修 + 名字表假块表根修（用户 17:07 要求收尾）
- 配件（静态 RE）：武器 AttachAdapter 0xed4540（接口 thunk 0xed5434）/ DetachAdapter 0xed5628（thunk 0xed5a34）：本地装/卸 0xed4a80/0xed56bc 后，`GetNetMode()==1` 才发 ClientAttachAdapter/ClientDetachAdapter（0xf1f8c4/0xf1f988，FName 表 0x5154610 +0x20/+0x30，init 0x90386c；反射标志 0x01020cc0 = 可靠 Client RPC）。客户端实现 0xed54d8 要 NetMode==3 才在本地装（vt+0x4e8）。听服(2) 从不发 → 加入玩家客户端武器只有背包图标、本地没装配件 → 后座/散布等客户端算的属性不生效。修：Lan.adpInstall 启动时 8 处：cmp #1/bne → cmp #3/beq（非客户端都发），后面统计 bl 0xed4f94 → nop（它和前面专用服才调的 0xed45e0 成对）。单机/房主自己：RPC 本地执行，实现里 NetMode≠3 直接返回，无副作用。诊断行「听服配件同步 8/8」。
- 名字表：服务端两次都是「方式0」块表（af32f6a0/af34b6a0，libUE4 rw 段里一个刚好指向块 0 的字），块 1 是乱的 → 序号 ≥16384 的名字全读成 ?。证据：服务端 net-1118-165423 标点行「控制器 aa267fc0 ?」，同一虚表 4c9f334 在客户端读出 BP_NZBRPlayerController_C；服务端「全员找到 0 个（共 2）跳过： ?」；NetRate「找不到 NetDriver」。客户端是方式1（正确）。修：Veh.Names.check 要求块 1 第一条序号 == 16384。
  - 影响：加速（Crew.find 找不到加入玩家 → Give 收到 S 请求也 setFor 失败 → 不写 PS.Score → 客户端「还没收到服务端的数」）、NetRate 带宽、ModeProbe 只扫到块 0（三军/五军「没有 /Game 蓝图」的结论证据因此偏弱）。
- 信号枪（静态 RE，未修）：ANZProjectile_FlareBullet 虚表 0x4c50f5c；Init vt+0x304=0xee6b9c：+0x331 = 发射仰角 > 枪+0x1b64（默认 35°），+0x334 = 枪+0x1b60（默认 10000）；Tick vt+0x204=0xee6dfc：Role≥3 且 +0x331 时记最高 Z(+0x32c)，下落时最高 Z > +0x334 → +0x330=1 → 枪(+0x2d0)+0x2b0 人物 vt+0x95c=0x10e8d20（Role≥3 → world 是游戏世界 → AuthorityGameMode(+0x94) 是 ANZGameMode → GM vt+0x5d0 拿事件管理 → +0x23c 广播事件类型 1 (x,y)）。监听者（AAirSupplySystem，虚表 0x4caf198，ctor 0x11860d4；CurFlareUsedNum +0x354、AirSupplyBoxClassFlareGunNormal/Vehicle 等属性在 0x4cb49cc 附近）没追完。枪 ANZWeap_FlareGun 虚表 0x4c4d92c：ServerSetFireInfo 实现 0xec09ec 只存 +0x1b3c..0x1b50；客户端开火 0xec0b28 非专用服且 Role≤2 才发。FireMode_FlareGun 开火 = 0xee597c（PhysicSimulate）：投射物 CDO+0x2cc 非 0 时只有权威端生成，否则 NetMode==1 不生成 → 听服房主会生成。没找到「只有专用服」的断点。
- 快扩：没查完（设备上 zf_items.txt / zf_items_bad.txt 看不到）。
- 新工具：game/re/nm1.py（全库 GetNetMode()==N 位置+源文件，结果在 /tmp 重跑即可）、u32s.py（UTF-32 字符串）、callers.py、fstr.py（函数引用的字符串）、pcref3.py、vtof.py。

## v3.00（vc201/core201，2026-11-18 17:2x，三包，未实测）— 快扩误删根修 + 信号弹诊断 + 空投系统 RE + 客户端道具键 + 模式蓝图全量扫
接手时 ntfy 最新是 17:02 的 v2.98 服务端 net-1118-170233，没有 v2.99 实测日志；配件/加速/带宽仍未实测。
### 快扩刷不出（静态，未拿到设备文件）
- giveItem 以前两条路会把编号永久写进 zf_items_bad.txt：①1.2 秒后诊断缓冲区不是「走完 0x5a02 且结果≠0」——包括「补丁代码没被调用 / 走到一半」这种没反应；②这 1.2 秒内游戏日志出现**任何**缺资源（Prop.missAt），不管是不是这件东西的。配件常缺图标/特效 → 快扩这类被误删，allAtt 也读 itemBad，所以全套配件里也没有。
- 改：只有补丁走完、游戏明确返回失败才删（记删的原因）；缺资源只对功能道具 402 算。loadBad 第一次运行把非 402 的删除记录全部清掉一次（标记文件 zf_items_bad.v300 记下清掉了哪些）。诊断新两行：「配件编号（205）：…（共 N）」和「坏编号：… 其中配件 …；v3.00 清掉过的误删：[…]」。
### 信号枪 → 空投（静态 RE，新增诊断，没改游戏逻辑）
- AAirSupplySystem BeginPlay = vt+0x178 0x1186134，把委托绑到事件管理（GameMode vt+0x5d0 返回的对象）各偏移：+0x10c OnMatchBegin、+0x230 OnAirSupplyMessage、**+0x23c OnQuickStartSingleAirSupply（信号弹 0x10e8d20 广播的就是这一格）**、+0x248 OnAirSupportJump、+0x2b4 OnTestSpawn、+0x254 OnDropItem、+0x38c OnRequestDropSupply。
- OnQuickStartSingleAirSupply exec thunk 0x11b699c → 0x11867bc：事件 +0x1c 类型==1 才继续 → 0x118a60c(this, +0xc,+0x10,+0x14,+0x18)：
  1. 0x1186a74：World(+0x94 AuthorityGameMode) 是 NZ 的 GameMode → GM+0x380 系统表里按 AAirSupplySystem::StaticClass(0x11b6238) 找系统对象，空 → 不叫；
  2. this+0x2bc（圈对象，+0x2b8 = 当前圈序号）空 → 不叫；
  3. **CurFlareUsedNum(+0x354) < LimitsNumAdd(+0x340, EditDefaultsOnly int) × (圈序号+1)** 才 +1 并叫飞机（0x118908c 生成 + 0x1188140）。LimitsNumAdd 为 0（蓝图默认值没来/配置没读到）就永远叫不来——这是目前最可疑的断点，等日志确认。
  反射：+0x33c RandomRadius(float)、+0x340 LimitsNumAdd、+0x344 AirSupplyTimeConfigList、+0x350 AirSupplyDropItemPoolIndex、+0x354 CurFlareUsedNum；+0x334/+0x338 AirSupplyBoxClassFlareGunNormal/Vehicle。
- 新 Flare.java：游戏日志出现含 Flare 的行（30 秒一次）→ 25 秒内每 0.25 秒扫 GUObjectArray 找虚表 0x4c50f5c 的信号弹，记 Role(+0xe8)/仰角够(+0x331)/已触发(+0x330)/最高Z(+0x32c)/门槛(+0x334)/位置；同时记空投系统对象（虚表 0x4caf198）的 Role、CurFlareUsedNum、LimitsNumAdd、圈对象/圈序号、RandomRadius；期间含 Flare/AirSupply/AirBox/AirDrop/AirPlane/SupplyBox/AirTransporter 的游戏日志也记；结束自动传 flare-*。诊断里「信号弹诊断」段。
  看法：信号弹 已触发=1 而 CurFlareUsedNum 不变 → 卡在空投系统三个条件（看 LimitsNumAdd、圈对象）；已触发一直 0 → 看 仰角够/最高Z/门槛/Role（服务端那份 Role 要 3）。
### 道具
- A 捡起后没有道具键：客户端包（Ed.ED==2）PropBtn.attach 直接 return、refresh 也藏 → 加入别人房间时永远没有道具键。RE：拿出 DoSelectedFunctionalProp 0xfe9574 末尾人物 Role==2 时自己调 0xd11a88 通知服务器；放下(vt+0x484)、收回(StartRecycleItem 0x110d6a0，本地读条 + ServerStartRecycleSceneItem RPC) 都是游戏道具栏用的原生入口。改：客户端也显示道具键，收回在 NetMode 3 走 0x110d6a0（以前直接提示「由房主收回」）。单机/房主没改。若单机包里也没有键，要 prop- 日志（点一下没认出时会自动传背包原始信息）。
- B 地上模型不对：没改。原因已知：v2.50 地上模型替身 cave 用「本局第一个加载成功的地上模型」顶替所有加载失败的 → 看到的是别的东西的模型。下一步想法（未做）：替身改成该道具自己 TargetProp 场景类里已加载的网格（场景道具类在手机上时）。
- C 去掉用不了的：规则不变（功能道具刷出后没有场景模型类 / 缺资源 → 删），只是不再误删别的类。
### 三军对垒/决胜五军
- 新 ModeProbe.survey：服务端第一次进大厅 20 秒后自动全量扫一次名字表（v2.99 修好的多块表），关键字 三军 Legion|ThreeArmy|3Army|Camp（空投系统里有 CampVioletGetBoxTips/CampBlue/CampGreen，阵营模式可能叫 Camp）、五军 BFA|FiveArmy|5Army，再列所有含 GameMode 的名字和其中的 /Game/ 路径，写联机日志并传 mode-*，不开房。开房按钮也改用同样的多关键字。

## v3.01（vc202/core202，2026-11-18 17:5x，三包，未实测）— 看 v3.00 日志后：战术模式按钮、撤回客户端自制道具键、信号弹诊断误触发修正
### v3.00 实测日志（17:28–17:40，服务端+客户端）
- 名字表全量扫（mode-1118-173107）：**三军对垒没有蓝图**（Legion/Camp 206 个名字全是 C++ 类和 HUD 类）；**决胜五军有** /Game/Blueprints/BattleRoyaleBFAGame/BP_NZBRBFAGameMode.BP_NZBRBFAGameMode_C；/Game/ 模式蓝图共 12 个：BattleRoyale、Compact、Death、CustomItem、Desert、BFA、CompactDesert、Savage、Island、BTT、**BattleRoyalePropsGame/BP_NZBattleRoyalePropsGameMode（战术/道具模式）**、FrontEnd。
- 用户开了决胜五军（新海岛，?game=BP_NZBRBFAGameMode_C）：能进图，Game class 对、BFA 控制器/GameState 都起来了、MatchState 直接 InProgress、有蓝圈；但「Uin not found in options」、找队伍 NZBRBFATeamInfo 成员 0、大量 ANZBRBFAGameState::OnSameTeam..error!、上飞机 1 秒就被移下来、掉到地图原点地下被拉起 → 用户说「没有流程」。推断：五军的分组（NZBRBFAGroup）靠专用服务器开局时的分组信息，listen 开房没人进组 → 没有分组航线/五军流程。未追，下一步 RE ANZBRBFAGameMode 的分组入口（NZBRBFADsLogic）。
- 信号弹诊断（flare-*）全部是误触发：触发词 Flare 匹配到 LensFlare/T_EFX_Flare 贴图日志，记到的「信号弹」「空投系统」都是类默认对象（Role 3、位置 0、LimitsNumAdd 0、圈对象空）→ 不能当结论。这轮用户没开信号枪。
- 快扩：服务端诊断已列出 205 类 44 个编号（20501…20505），没有坏编号文件；用户还没反馈快扩能不能刷。
- 客户端点了自制道具键（背包空，自动传了 prop-）；用户明确要游戏原生道具栏，不要自制键。
### 改
- 撤回 v3.00 的客户端自制道具键；当前对局模式类含 Props（战术模式）时单机/房主也不显示自制键（ModeProbe.gameClass 读「Game class is」）。
- 联机页娱乐模式：去掉「三军对垒」（手机上没蓝图），加「战术模式（试）」= ?game=/Game/Blueprints/BattleRoyalePropsGame/BP_NZBattleRoyalePropsGameMode..._C（新海岛）；道具栏 HUD 类 UNZBRPHUDWidget_PropsBar 属于这个模式。未实测，是否闪退/有没有原生道具栏看 mode-* 和 avatar-*。
- Flare：只认含 FlareGun/FlareBullet 的行；扫对象跳过类默认对象（+4 & 0x10）。

## v3.02（vc203/core203，2026-11-18 18:0x，三包，未实测）— 刷枪配件按枪给、弹匣给快扩
- 用户：刷枪自动放到地上的配件里没有快扩，而且别的枪给的也是一样的步枪配件。
- 根因：giveGun 对所有枪都放同一套固定 ATT（12 件，步枪消音/补偿 + 20504000001）。
- 编号对应（推断，未实测）：libUE4 里 ENZWeapAdapterItemType 枚举按声明顺序（0x47ddfc1 起）分组，组内个数与道具表 205 类完全一致：枪口 12、握把 6（含箭袋）、瞄具 9、弹匣 12、枪托 5。所以 NNN = 组内顺序：
  - 20501 枪口：1 步枪消音 2 步枪消焰 3 步枪补偿 4–6 冲锋（消音/消焰/补偿）7–9 狙击 10 手枪消音 11 收束器 12 鸭嘴
  - 20502：1 垂直 2 直角 3 拇指 4 半截 5 轻型 6 箭袋
  - 20503：1 红点 2 全息 3 二倍 4 三倍 5 四倍 6 六倍 7 八倍 8 VSS 四倍 9 Dot
  - 20504 弹匣：每类 扩容/快速/快扩，步枪 1–3、冲锋 4–6、狙击 7–9、手枪 10–12 → 快扩：步枪 20504000003、冲锋 20504000006、狙击 20504000009
  - 20505：1 战术枪托 2 UZI 枪托 3 托腮板 4 子弹袋 5 98K 子弹袋
- 改：GameOverlay.attFor(枪编号) 按枪给能装的配件，弹匣只给快扩；十字弩给箭袋；日志记「刷枪配件 名字：[…]」。若用户反馈名字对不上，按这行日志和捡起时的提示修正表。

## v3.03（vc204/core204，2026-11-18 18:4x，三包，未实测）— 新海岛默认战术模式、找地图、喷气背包、去飞天、开局发道具 999、藏房主人物、ntfy 限流
用户 18:13 实测 v3.02：「这次很不错，都能用了」（战术模式开房、原生道具栏、人偶可能也能用；信号枪服务端这次触发了叫飞机 +0x330=1，CalcAirSupplyPlaneMoveInfo 出现）。客户端截图「请求失败 HTTP 429」。
- 新海岛按钮直接开战术模式（ModeProbe.host("Props")，模式类缓存 cache）；去掉单独的「战术模式（试）」。
- 找地图：服务端大厅自动扫名字表 /Game/Maps/…_Main（ModeProbe.maps，排除 FrontEnd/RoleHall/Startup），除老海岛/新海岛/雨林外扫到的都加按钮（战术模式），联机日志「名字表全量扫 地图」。
- 喷气背包（Jetpack.java）：ANZItem_JetpackBagBase 反射属性（全 float）：CurrentEnergy 0x388 / MaxEnergy 0x38c / LaunchCostEnergyPer 0x390 / …FromCfg 0x394 / HoldCostEnergyPerSec 0x398；CurrentDurable 0x39c / MaxDurable 0x3a0 / LaunchCostDurablePer 0x3a4 / …FromCfg 0x3a8 / HoldCostDurablePerSec 0x3ac / RecoverDurablePerSec 0x3b0 / RecoverDurableCDTime 0x3b8 / CurrentState 0x3f4。两条（能量、耐久）都改上限 999999、当前填满一次、起飞/按住扣 1。服务端客户端都每 2 秒扫（类名含 JetpackBag）。日志记原值，哪条是燃料哪条是过热看日志。
- 飞天：菜单去掉（客户端本来没有），传送开关留。
- 开局发战术道具（HostMatch.java，服务端）：ucall 刷物品补丁 0x17169c0(加入玩家控制器, FString "P<id>x999")，补丁取控制器+0x2c0 人物 → 服务器直接放进他背包。道具 = 道具表 402 类去坏编号。每局每个控制器一次。
- 藏房主人物：房主自己人物 AActor::SetActorHiddenInGame vt+0x160（thunk 0x2e70d40），不关碰撞（SetActorEnableCollision = 0x2740af0，没用，关了会掉出地图）。联机页开关「藏起房主人物」默认开。
- 油桶键去掉（认不出编号）。
- 稳定性：ntfy 429 —— 客户端代刷 0.5 秒内合并成一条多行消息，429 时 6 秒后重试 2 次；服务端从每 2 秒轮询改为长连接订阅 /json?since=（断了 5 秒重连）。
- 重启：在线更新后的重开从 1.2 秒延到 3.5 秒（服务端 15:49、17:52 两次启动闪退都在 0x1a322f8 ← 0x1a2ea98（读 ini/命令行），推断是旧进程没退干净）。
- 没做：换弹时切枪还会自动装弹（要那一刻的日志/RE）；「方块」（截图里灰色棋盘格方块，人偶站在上面）—— 是什么、要做成什么已问用户；pak 资源、训练营下一步。
- 18:24 用户澄清「方块」：不是能站的方块，是装物资的箱子（像死亡盒），刷的装备放进箱子里。RE 线索：ANZPickupBox（NZPickupBox.cpp），背包组件 ServerSpawnPickupBox（thunk 0xe68298，校验 vt+0x368，实现 vt+0x36c），SpawnPickupBox exec 被桩成 bx lr；PickupBoxClass 属性 +0xf64（所属类未定）、AllPickupBoxList +0x674、bAutoDestroyPickupBox +0x8c8。背包组件虚表静态没定位，下次运行时从 人物+0x838 读。计划 v3.04 做「刷进箱子」+ 换弹切枪。
- v3.04（vc205/core205）18:4x 用户（v3.03 实测）：新海岛战术模式选了「不刷毒对局」，进图后服务端和客户端都上了飞机，应该在出生岛。日志 avatar-1118-183906（服务端）：战术模式开房成功（BP_NZBattleRoyalePropsGameMode_C），阶段 0→1→2→3→4 走正常流程 + [SkyDiving] Add a passenger to plane；对比 15:54 海岛不刷毒那局是 0→4 直接跳。说明那局不上飞机补丁没打上。原因（推断）：Mods.noPlane/noPoison 只在本次进程里有效，没存；用户选完后为了更新内核重开了游戏 → 变回正常对局。修：setNoZone/setNormal 存 prefs zone_off，大厅加载完（coatAt 分支）Mods.restoreZone() 自动恢复；avatar 日志加「游戏模式」行。未实测。
- 同局：开局发道具 13/13 种每种 999 成功（客户端截图「附近的物品」×999）；随后 18:38:01 UID=3 把 9 种各 999 Discard 到地上（谁触发未确认，可能是战术模式道具槽只有 3 个）。门/SceneItems 日志一直是 Add 0（正常对局也一样，不是判断依据）。

## v3.05（vc206/core206，2026-11-18 19:1x，三包，未实测）— 单排、去掉房主人物、出生岛空投、客户端只留刷车、菜单分组
- 19:01 用户（v3.04 实测有用）：加入玩家要单独一队（单排，不要双排四排），现在一进去就和服务端是队友；服务端人物只是隐藏、实际还在 → 删掉；出生岛放空投：枪械、特殊道具 999、各种子弹 9999、投掷物 999、油桶 999、步枪配件、冲锋枪配件（含倍镜）、药品 999、头甲包（含喷气背包）；客户端只留刷车；修打药没效果；喷气背包按之前改动做好。19:10：菜单分组排列更美观。
- 日志 avatar-1118-185503（服务端 v3.04）：游戏模式恢复不刷毒 ok，阶段 0→4（出生岛）；队伍：加入玩家和房主同一个 TeamInfo（TeamIndex 0）；开局发道具 13/13；喷气背包这局没用；没有药品使用记录。
- 单排 RE：SetupTestTeam 0x100b2b0（日志 "Uin not found in options" 后走这里）：GM+0x998 当前队人数（0 → 新建队，存下标 +0x99c），进人后 +1，>= GM+0x8ed MaxPlayerCountPerTeam（字节，反射名）就清 0；人机同理 +0x9a0/+0x8ee MaxBotCountPerTeam；Teams TArray GM+0x8c8/+0x8cc；NumTeams +0x8ec。修：HostMatch.solo() 每 1.5 秒 GM+0x8ed=1、+0x998 清 0 → 每个加入的人新建一队。已进来的人不改。
- 去掉房主人物：直接销毁会让活着的队只剩加入的人（一个人加入就立刻结算，见 history 0x100fbd4）→ 隐藏 + 关碰撞（AActor::SetActorEnableCollision 原生 0x2740af0，由 exec thunk 0x2e70cc0 读出）+ CustomTimeDilation(+0x58)=0 冻结 + 血补满。联机页开关改名「去掉房主人物」。空投放完才冻结。
- 出生岛空投（Box.java，服务端每局一次，联机页开关「出生岛空投」）：堆在一起游戏会显示成箱子（灰色方块 = P40300000001_DeathDropBox）。房主人物当搬运工：K2_TeleportTo 到第 i 堆（房主出生点起两排，隔 4 米），能叠的用刷物品补丁塞进房主背包（每次 999，子弹 10 次 = 9999），再调 ServerDiscardItem 实现 0xcd6728(背包, 物品, 数量, 不查容量 1, 校验 0) 丢在脚下（背包列表 +0x4a8/+0x4ac、道具槽 +0x490..、数量 +0x2b4、编号 +0x2cc/+0x2d0）；枪和头甲包用 SpawnOnePickUp（cave 'G'，Veh.dropAt）每件刷 1 个（不往身上塞，身上已有会换装走崩溃路）。枪「每把 999」没做：枪不能叠，999 把会刷出几万个物体。油桶按已认出名字（油桶/Barrel/Fuel/Oil）的 402 道具找，没认出就那堆为空，特殊道具那堆里有全部 402。
- ucave 改（game/re/ucave.py，emu_ucave 过）：fn 路径多传 r2=buf+36、r3=buf+64、栈 [sp]=buf+68；虚表路径改用 lr/ip 取函数，r2/r3 也照传。Veh.ucall5。cave 长 0xb0（SLACK+0x240..0x2f0，不碰 0x300 道具 cave）。
- 打药没效果：这次日志没有用药记录，根因未查到。加自动上传 heal-*（游戏日志出现 PreUseItem/FinishUsing/CancelUsing/CastTime 且是药品时）。
- 客户端菜单只留 功能/刷车/联机/画面；服务端/单机分页顺序改成 常用 功能 | 武器 特殊 子弹 装备 道具 | 刷车 衣服 (人机) | 联机 画面，组间加分隔线。
- 喷气背包：v3.03 改动未变，日志「喷气背包：这局还没见到」，等用一次看「喷气背包 原值」。

## v3.06（vc207/core207，2026-11-18 19:3x，未实测）— 修 v3.05：U cave 装不上、单排后死了不能重进
- 19:32 用户：死了之后不能重进；喷气背包能飞了；好多刷不了，空投只有枪和头甲包。日志 box-1118-192454：特殊道具/子弹/投掷物/配件/药品「塞进 0 次」，开局发道具 0/13，刷车状态「用道具 cave 没装（空位被占 0000000000000000）」。
- 原因 1：v3.05 ucave 变长 0xb0，尾巴伸进 SLACK 页自带数据段副本（非 0）→ 空位检查失败 → U cave 没装 → 所有 ucall（刷物品补丁、丢弃、关碰撞）返回 0。修：只查开头 0x98（旧长度）是不是 0（v2.83：这段数据没人用，可以写）。
- 原因 2：net-1118-193226：OnPlayerKilled AliveTeamMembersCount[0] AliveTeamCount[1] → NZGameMode.cpp:1677 start to kick all players。单排后房主隐藏人物自己算一队，加入的人一死只剩它 → 结算踢人。修：去掉房主人物开着时 0x100fc60 cmp r0,#1 → cmp r0,#0（只有 0 队活着才结算），关掉改回；状态写在 avatar「单排」行。
- 19:40 用户：没看到空投。v3.05/3.06 的「空投」只是地上堆（房主出生点旁，枪那堆在集装箱旁），不是箱子。已答应下一版（v3.07）改成游戏自己的空投箱，每类一个落在出生岛。RE 起点：DropAirSupplyBox 0x118a820(this, x, y, z, [fp+8] 索引/类, [fp+0xc] 字节) → 0x1186a74 取配置 → 0x1023490 → SpawnActor 0x2aa8ad4 → 0xfd4c64(箱, 字节) → 0xfd3274(箱, 表) 填物品；箱子 PickupItemIDList +0x3f8/+0x3fc（元素格式看 0xfd3ac0）。要 6 参数，ucave 只支持 1 个栈参数，需再扩。

## v3.07（vc208/core208，2026-11-18 20:xx，未实测）— 真空投箱
- 19:42 用户：「所以你修啊」（没看到空投）。v3.05/3.06 的空投只是地上堆。
- RE：DropAirSupplyBox 0x118a820(this=空投系统, x, y, z 浮点按位, [sp] 箱型序号 0..3, [sp+4] 字节)：0x1186a74(this) 取配置对象（GM+0x380 那套），0x1023490(配置, 序号) → 类：0→+0x32c，1→+0x334，2→+0x338，3→+0x330；校验是 ANZBRAirSupplyBox 子类（StaticClass 0x11c0918）→ SpawnActor 0x2aa8ad4 → 0xfd4c64(箱, 字节) → 0xfd3274(箱, 0xde1fa4(配置)) 初始化。
- 0xfd3274 内 0xfd3424 bl 0xfd3ac0 按物品池（箱+0x3d8 组）填 PickupItemIDList 箱+0x3f8（元素 8 字节 = FName ItemDesc，反射 NZBRAirSupplyItemPercent{ItemDesc, Percent, MaxAmmoPickupCount, MinAmmoPickupCount}，NZBRAirSupplyItemCountPercent{PickupCount, Percent}）；之后 0x1112cb0 登记（所以要在填表这一步换，不能事后改）。列表没有数量字段。
- 背包组件 ServerSpawnPickupBox（vt+0x36c）= 0xb63a30 桩，不能用。BR 背包虚表 0x4c830a4（ServerDiscardItem vt+0x34c=0xcd6728，vt+0x354=0xcd40d0 有「[Pickup] BOX … Box spawn at」日志，参数没认）。
- 做法：boxcave（game/re/boxcave.py + emu_boxcave.py 过）放在被剥掉的作弊 RPC thunk 0x11de78c..0x11de844（ServerTestSpawnAirSupplyBox 那两个，没人调），0xfd3424 bl → cave；缓冲区 [0]=n [4]=用过次数 [8..] FName；n>0 时把我们的 FName 追加进 +0x3f8（扩容 0xbc5580），不调原函数。
- ucave 再加第 6 参数（buf+72 → [sp+4]），Veh.ucall6；ucave 0xb8 字节（到 SLACK+0x2f8）。
- Box.airdrop：先投一个探路箱（游戏随机物资）读 FName 行名格式（找 11 位编号），再全量扫名字表找我们每件东西的行名序号，按 9 类每类投一个箱（出生岛，z+30 米落下），箱里每样一份；999 的地上堆保留在旁边。日志写在 avatar「空投」那段（空投系统、箱子类、探路箱物资、行名格式、找到几个、每箱是否换成我们的）。

## v3.08（vc209/core209，2026-11-18 20:1x，未实测）
- 19:59 用户：刷不了车；空投没有；v3.07 连枪和头甲包都看不到。
- v3.07 box 日志：堆都放了（枪 36、头甲包 19、各类丢出若干堆），位置是「房主隐藏人物」出生点；真空投箱：空投系统 4 个箱子类全空 → 没投（这个模式不能用 DropAirSupplyBox）。
- 根因（推断）：堆放在房主隐藏人物身边，加入玩家出生点不同（分散出生），所以看不到。改：Box.joinedPos 找第一个加入玩家（控制器+0x2fc NetConnection）人物位置，堆放在他身边（前 5 堆一排 y+4 米，后面 y+7.5 米，x 间隔 3 米）；没加入玩家就等。
- 去掉 v3.07 的 airdrop() 调用（不装 0xfd3424 钩子），代码留着。
- 刷车：give 主题 18:05 之后没收到客户端任何请求；客户端日志 v3.02 之后也没上传过 → 客户端那边请求没发出去（疑 429 或别的），要看客户端点刷车时的提示。

## v3.09（vc210/core210，2026-11-18 20:1x，未实测）
- 20:08 用户：新海岛怎么还在查蓝图，早就找到了直接开。改：ModeProbe.cache 写死 Props → /Game/Blueprints/BattleRoyalePropsGame/BP_NZBattleRoyalePropsGameMode.BP_NZBattleRoyalePropsGameMode_C，点了直接开房，不扫名字表。含 v3.08 改动。

## v3.10（vc211/core211，2026-11-18 21:xx，未实测）— 物资方块、菜单精简、开局排车、喷气背包识别、加入艾挽服务器
- 20:38 用户（v3.09 实测，截图：出生岛已有车、喷气背包、一个灰色方块）：客户端刷车「请求失败 429」；换弹时切枪/收枪打断不了换弹；喷气背包日志发了没改；所有物资用方块装（服务端刷 3 次 M4 就出现方块，非实体）；删单机/联机选择；联机默认 frp-put.com:47937 显示「加入艾挽服务器」，右边留可改地址；客户端删刷东西、画质、加速高跳（留给服务端）；服务端只留地图上没有的东西能刷；枪每把 10，配件通用每个 67，药品/道具 67，道具只刷在玩家脚下不刷服务端脚下；开局刷所有车在出生地排好；新增自制训练营和单挑地图；喷气背包和头甲包每种 6 份。
- 日志 avatar-1118-202831（服务端 v3.09）：物资放在加入玩家身边 3895,-3899（房主人物 3857,-3871）；喷气背包「这局还没见到」（客户端 20:20–20:23 InitJetpackBag 多次）。
- 做了：
  - Box.plan：枪 10 把/种、战术道具 67、子弹 9999、投掷物 67、配件合成一堆（步枪+冲锋枪全部）67、药品 67、头甲包含喷气背包 6 份/种；每类所有东西放同一个点（不能叠的 dropAt 同坐标 z-80）→ 游戏合成一个方块。一排隔 3 米、玩家前 4 米。
  - Box.cars：开局刷所有车组（不含快艇/大船），玩家身后 8 米一排隔 5 米（spawnGroupAt）。
  - HostMatch：不再 giveAll（开局塞背包），道具在物资箱。
  - Jetpack.isJet：沿 FStructBaseChain（类+0x58 链、+0x5c 层数-1）查父类名含 JetpackBag。
  - 菜单：客户端只剩「联机」页；服务端 常用 功能 | 道具 | 刷车 衣服 | 联机 画面；服务端道具页只列 402 类；服务端点刷的东西放在加入玩家脚下（Box.joinedPos，没有加入玩家才放自己脚下）。
  - 客户端包：去掉加载页单机/联机按钮，Ed.saved() 客户端包恒为 2；查完更新自动进。
  - 加入：大按钮「加入艾挽服务器」= frp-put.com:47937，右边输入框 +「加入这个地址」。
  - 服务端不再订阅 ntfy 代刷主题（客户端不再请求，少占 429 限流）。
- 没做：换弹打断（ENZInnerWeaponEvent：16 BeginChangeClip … 21 EndChangeClip、22 SwitchToInactive；武器代码 0xec0000–0xf30000 里 GetNetMode 比较位点已列出，未定位打断路径）；训练营/单挑新地图（不能做新地图，pak 不解密、无编辑器）；打药（heal-1118-202310 未分析）。

## v3.11（vc212/core212，2026-11-18 21:0x，未实测）— 训练营
- 20:51 用户：「怎么可能做不了？给我做」「我只要训练营的」（单挑不要了）。
- Training.java：
  - 原图：大厅（服务端）找 pakchunk_c2afa52b…pak（外部 files / obb / files 递归），只读末尾明文索引（标记 0x5A6F12E1、版本、索引位置；FString 名字 + FPakEntry），列出 .umap → /Game/Maps/TrainingIsland_Runtime/… 每个一个「训练营原图 xxx（试）」按钮（战术模式开）。索引加密/不认 → 日志记末尾字节。日志自动上传 pak-*。不解密。
  - 自制「训练营」按钮：新海岛 + 战术模式 + setNoZone（不上飞机不刷毒）+ 物资方块 + 排车 + 加入玩家前方 20 米一排 8 个固定靶子人机（不带枪，addBot 标志 4，Bots.manual 固定），每 15 秒看一次（Prop.alive + 血 +0x5a0>0），少了就补。其他开房按钮 Training.off()。

## v3.12（vc213/core213，2026-11-18 21:1x，未实测）— 在线更新根修
- 21:08 用户：更新要么装完闪退，要么没检测到更新。
- NetCore：下完 dex 先用 DexClassLoader 读包里的 CORE_VER/BUILD，和 .ver 对不上就不装、换下个镜像（镜像缓存 .ver 新 .dex 旧 → 以前会装错包）；.ver 加 ?t= 分钟级防缓存。
- chain（APK 里的启动代码，要重装 APK 才生效）：运行文件按版本命名 zcore_netrun<ver>.dex，删旧的及 oat；多进程同时启动不再互删，写本进程临时文件再原子改名。

## v3.13（vc214/core214，2026-11-18 21:2x，未实测）
- 21:11 用户：去掉单机/联机选择，不管单机联机都默认开服务端，大厅有装扮。
- Ed：客户端包 ED 初值 0（自建大厅服务端+仓库衣服），chosen=true、canChoose=false；大厅游戏模式页下加「加入艾挽服务器」+ 上次地址；对局菜单：加入别人后只剩联机，没加入时按服务端页。

## v3.14（vc215/core215，2026-11-18 21:3x，未实测）— 游戏自带更新页接管（第一步）
- 21:16 用户：现在就做（用游戏自己的更新页显示我们的更新）。
- RE：Dolphin（com/tencent/gcloud/dolphin UpdateCallBack/UpdateInterface，libUE4 strDolphinUpdateUrl、NZGCloudPuffer.cpp）；更新页成员 UpdatePanel/UpdateProgressBar/UpdateProcessValue/UpdateProcessText/UpdateTip/UpdateBtn，函数 OnUpdateProcess/OnUpdateBtnClick/OnAbortUpdateConfirm（libUE4 字符串 0x4811cf6 一带）。类名/蓝图路径不在 libUE4 字符串里。
- UpdUi.java：大厅扫名字表带 Update 的名字，日志 upd-*。下一步：拿到蓝图路径 → 运行时创建/显示这页，下载时写进度条和文字。

## v3.15（vc216/core216，2026-11-19 01:3x，三包，未实测）
- 01:20 用户：删掉训练营和五军对决地图，修一些 bug。
- 服务端开房页去掉「训练营」「训练营原图…」「决胜五军（试）」按钮（Training.java/ModeProbe.BFA 代码保留，无入口；不再调 Training.scan 读 pak）；ModeProbe.maps 额外地图列表排除含 Training 的关卡。
- 断线重进修复：客户端 01:08 回大厅后再加入被拒，服务端回 `Refuse player to relogin, because playercontroller not find!`（UTF-32 字符串 0x436d964，PreLogin 0x1009890，xref 0x1009e48）。0x1009a40 vt+0x494 按 Uin 找到旧玩家信息(r5)，vt+0x498 找旧控制器(r7)；r7==0 → 0x1009ab0 beq 0x1009d7c 写错误拒绝。Lan.RELOG 改成 beq 0x1009d68（和「没有旧信息」同一条正常放行路径）：b100000a → ac00000a。诊断联机补丁行「断线重进 ok」。

## v3.16（vc217/core217，2026-11-19 02:xx，三包，未实测）— 两人单挑
- 01:36 用户：两人单挑 20 个头定胜负；雨林、海岛各一个，有掩体；开局前自由选枪；死了 5 秒复活、3 秒无敌；两把一样的枪 + 300 发，配件在背包，3 头 3 甲，背包用喷气背包。
- Duel.java（服务端）：开房页「单挑·海岛」（战术模式蓝图 + 新海岛）「单挑·雨林」（默认模式）；setNoZone，关物资箱，在出生岛打（不能做新地图）。
  - 选枪：进图后在每个加入玩家面前摆一排全部枪（CATS 前 6 类，Veh.dropAt），背包 +0x4a8 列表出现 201/202 编号 = 选好（读不到 45 秒默认 M416）；选好时位置 = 他的出生点。
  - 发装备 HostMatch.giveTo：同款枪补到 2 把、子弹 300、attFor 全部配件、helmet3/armor3、喷气背包（itemName 含 喷气/Jetpack）。
  - 死亡：控制器+0x2c0 不是活人物/血 +0x5a0<=0 → 对面 +1；5 秒后每 2.5 秒调一次 GameMode 虚表 +0x39c(RestartPlayer，NZ 版 0x10127a4，BTT 0x1079fa8)；新人物出现 → 传回出生点、发装备、3 秒内补满血。20 头结束，不再复活。
  - RE：GameMode 虚表 0x3a0 RestartPlayerAtPlayerStart(0x29f6170)、0x3a4 RestartPlayerAtTransform(0x29f65e0)；NZ RestartPlayer 检查 PS(+0x2c4)+0x2bc>=2、vt+0x3ec==1、PS+0x2d4 bit2。BTT 模式蓝图存在（BTT 有原生复活 AIRespawn），没用。
  - 未知/待测：死后 RestartPlayer 是否被拒（PS 状态）；比赛是否因只剩 1 人结束（房主幽灵人物算一队，应不会）；选枪读背包能否读到枪（枪可能不在物品列表里）。看服务端日志「单挑：」行。

## v3.17（vc218/core218，2026-11-19 02:xx，三包，未实测）— 单挑：不在出生岛、自定点、毒圈
- 01:43 用户：不要设到出生岛；要有掩体、毒圈、位置选好；「你自己定，自己做」。
- 每张图记两个单挑点（prefs duel_e 海岛 / duel_s 雨林，x,y,z×2）。没记过：这局上飞机（setPlane(false)+不刷毒），两人自己跳，落地静止 2.5 秒（阶段≥4）记成两个点；记过：不上飞机，进图传到两个点（Veh.teleport），再在点前摆枪。
- 开房页「记录单挑点」「清除单挑点」；对局里服务端常用页「单挑」→「记录单挑点」（两个加入玩家现在的位置）。
- 毒圈：圆心 = 两点中点，半径 = 两点距离/2 + 60 米（≥100 米），不缩；游戏毒圈关着，圈外每秒 -10 血（最低 1），看不见。
- 无敌特效没做（没找到可靠的特效接口），只是 3 秒内补满血。

## v3.18（vc219/core219，2026-11-19 01:5x，三包，未实测）— 单挑细节
- 用户：慢慢做，掩体和细节做好。
- 掩体：两个单挑点连线 25%~75% 处放 6 辆车（面包车/吉普/轿车，左右交错 4 米，Veh.spawnGroupAt），每局一次。车会被打爆。
- 选枪：背包列表读不到时认手上的枪（人物 +0x890 类名关键字 → 编号表 Duel.KW），认不出就记日志类名。
- 传送模式：开局 60 秒内被游戏挪走 >50 米就拉回单挑点。
- 用户问怎么联机：同一个服务端（虚拟机/云电脑），两人装客户端加入；房主幽灵人物是听服必需的，底层没变成纯服务器（做不到）。

## v3.19（vc220/core220，2026-11-19 02:1x，三包，未实测）— 单挑：真墙围场 + 原生出生保护 + 等两人到齐
用户 01:5x–02:09：车当掩体太敷衍（删）；也不要护盾墙，要地图里那种真墙/集装箱/箱子；布局要有变化（长短墙、L 形、交错、箱子），墙必须比人高、跳枪打不过去，贴地不悬空，不压出生点；两个人外面要一圈封闭围墙；无敌要真的有效果；两个人不同时进，要等齐了才开始，中途掉线暂停；「要全面考虑周全」。
### 中途放弃的方案（记录）
- 护盾墙（战术道具盾）：ANZItem_Shield 虚表 0x4ce8ed0，vt+0x3a4 = SpawnScenePropItem 0xf300dc（用道具 +0x3f0 TargetProp 在 +0x338/+0x344 生成）；ANZBRSceneItem_Shield 构造 0x1147360（虚表 0x4ca79b8，大小 0x480），HP = 场景道具 Health +0x2cc / HealthMax +0x2c8（反射 0x4c2faf4），寿命 LifeCycle +0x308（Tick 0xd95624：LifeCycle + 生成时间 +0x30c < 世界时间 → vt+0x344 过期），CanRecycleDist +0x420。用户 02:04 改主意，没用。
### 墙（Walls.java）
- 类：AStaticMeshActor StaticClass 缓存 *(base+0x5389048)（GetPrivateStaticClass 0x2fb9760 区，大小 0x2b8），构造 0x2d98764：StaticMeshComponent = actor+0x2b0 = 根组件，Mobility comp+0x16e=0(Static)，碰撞预设构造里设（vt+0x2f0）；BeginPlay 0x2d9888c。UStaticMesh 类缓存 *(base+0x538902c)（大小 0x118），ExtendedBounds = mesh+0xbc（Origin、BoxExtent、Radius；反射 0x503bedc）。
- 生成：UWorld::SpawnActor(world, class, FTransform*, FActorSpawnParameters*) = 0x2aa756c（0x2aa8ad4 是 loc/rot 包装，内部拼 FTransform）。FTransform 非向量化：+0 FQuat、+0x10 位置、+0x1c 缩放（0x28 字节）。FActorSpawnParameters 0x20（构造 0x2e523bc），+0x18=1 AlwaysSpawn、+0x19=2 bNoFail、+0x1c=8。经 ucall5（ucave 已有 5 参数路径）。FRotator→FQuat 约定用 unicorn 跑 0x19fac90 核对（game/re/emu_quat.py，5 个 yaw 全对）。
- 设模型：SetStaticMesh = 组件虚表 +0x43c（0x2957578，StaticMeshComponent 虚表 0x4ef5d58），StaticMesh 字段 comp+0x628。0x29575fc：已注册 + Mobility==Static 时拒绝（"Calling SetStaticMesh on '{0}' but Mobility is Static."）→ 服务端生成后先写 comp+0x16e=2。
- 复制：AActor::SetReplicates 0x2747784（Role 3 且原来不复制 → AddNetworkActor 0x2e5c524，bReplicates = 字节 +0x5e bit3）；UActorComponent::SetIsReplicated 0x28a40d8（comp+0x74 bit3）；NetCullDistanceSquared actor+0xf4 写 1e12。反射：USceneComponent RelativeLocation/Rotation/Scale3D(+0x100/+0x10c/+0x118) 带 Net + OnRep_Transform；StaticMesh(+0x628) 带 Net + OnRep_StaticMesh(0x295596c：先清字段再调 vt+0x43c)。
- 客户端会被同一个 Static 检查拒掉（客户端组件按类默认值 = Static）→ 补丁 0x29575fc..0x2957610（20 字节）：原 `ldrb r0,[r4,#0x16e]; cmp r0,#0; bne 0x29579e8; ldr r0,[pc,#0x490]; add r6,sp,#0x38` → `ldrb; cmp r0,#0; moveq r0,#2; strbeq r0,[r4,#0x16e]; b 0x29579e8`（只把 Static 改 Movable 再照常设模型）。盖掉的后两条是警告分支开头，0x2940000–0x2970000 内没有跳转进 0x2957600..0x2957614；0x29579e8 起不用 r0。unicorn：game/re/emu_meshpatch.py（Mobility 0/1/2 三种都对）。每次进图就打（房主、客户端都打；Walls.clientWatch 在 Con 进图处启动），客户端每 15 秒记「收到房主复制的墙 N 块，带模型 M 块」（Role==2 的 StaticMeshActor）。副作用：游戏运行时对 Static 组件调 SetStaticMesh 不再被拒（以前是报警告不生效），会变 Movable。
- 选模型：扫 GUObjectArray 里类 == UStaticMesh 的对象，名字含 wall/fence/concrete/brick/barrier/container/partition/block（墙）或 crate/box/container/cargo（箱），排除 glass/window/door/roof/floor/stair/rock/tree 等；包围盒过滤：墙 长 2.5–25 米、厚 0.1–3 米、长≥1.8×厚、高 1.5–8 米；箱 三边 1–13 米、高≥1.5 米。按「不用放大就够高、长度接近 8 米」排序，墙取 4 种、箱取 3 种，全部写日志（名字 + 长厚高 + 中心偏移，以及前若干个候选）。
- 摆放：场地坐标 u = A→C、v = 垂直；围墙矩形 u∈[-12 米, d+12 米]、v∈[±max(12 米, 0.35d)]，每边均分、段长 ≤ min(原长×3, 10 米)、相邻段多 8% 重叠、四角各伸出一个墙厚 → 无缝。场内 15 块：中间箱子、中线两侧竖墙、两组镜像 L 形（竖 5 米 + 横 4.5 米）、两块交错短墙、两侧箱子、两个出生点前 9 米偏一侧的掩体、两侧长墙；离两个出生点 7 米内不放、不压在站着的人身上、不出围墙。高度：Z 缩放到「地面以上 ≥3.3 米」（1–3 倍），底部埋地 1.2 米；地面 = 两个点人物中心 Z 沿 u 插值 − 90 厘米（没有做射线检测，坡度超过 1.2 米处会露底）。长度缩放 0.6–3 倍，太薄（<20 厘米）的加厚到 20 厘米。
- 圈：有围墙时，看不见的掉血圈改成「围墙矩形外 3 米」，没刷成墙时还是原来的圆。两点距离 < 15 米或找不到合格墙模型 → 不刷，日志写原因，比赛照常。
### 无敌（原生出生保护）
- NZGameMode 伤害链：BR GameMode 虚表 +0x484 = 0x101112c → 0xcc0ad0 → 所有路径都到 0xcc0e0c bl 0xc8b450：受伤人物是 NZCharacter、有施害者、人物+0x7e0 bSpawnProtectionEligible≠0、人物+0x2d0≠施害者、(world+0x740 世界时间 − 人物+0x114 CreationTime) < [GM+0x380]+0x3c8 → 伤害写 0，日志 "[%s:%d] InjuredChar=%s don't take damage in SpawnProtection"。IsSpawnProtected（exec 0xe413ec → 0xd11268）同样条件，用 World+0x98 GameState。
- 反射：bSpawnProtectionEligible 人物+0x7e0（Net，构造 0xcfee40 默认 1；虚表 +0x67c = 0xd1be90 清 0，拾取 0xce5f90 会调）；SpawnProtectionTime GameState+0x3c8（Net，float）。
- 做法：每局一次把 GM+0x2e0 和 GM+0x380（类名含 GameState 才写）的 +0x3c8 写 3.0（日志原值）；复活 → 传送 → 发装备之后写人物 +0x7e0=1、+0x114=当前世界时间 → 从这一刻起 3 秒原生免伤（发装备的拾取可能清 Eligible，所以放在发装备之后）。效果：同时 12 次 × 0.25 秒 SetActorHiddenInGame（虚表 +0x160，bHidden 复制）闪烁，finally 恢复显示。补满血保留当后备。客户端有没有游戏自带的保护特效（蓝图/Lua 里用 IsSpawnProtected）无法在 libUE4 里确认（只有 exec thunk 调它）。
### 等人 / 暂停 / 边界（自查清单，状态）
- [做了] 两个 NetConnection 玩家都在且都活着、都到位才开始（摆枪、发装备、拉回、算分、掉血都在开始后）；等人时先到的人站在自己的点（离开 5 米拉回、补满血、不掉圈血），开房页/房主提示「等待对手加入（n/2）」，日志「单挑：等待对手加入 (1/2)」。客户端屏幕提示：没有可用的 HUD 通道，只有房主 toast + 日志。
- [做了] 开打后有人掉线 → 暂停（不算分、不掉血），日志 + 房主 toast；新控制器进来时接手掉线玩家的记录（分数、枪、出生点），回到点上继续。
- [做了] 选枪阶段死了：不算分，照常 5 秒后复活回出生点（还没选枪就不发装备）。只有两人都选好枪后的死亡才算分。
- [做了] 同一刻两人都死：各给对方 +1；同时到 20 记「平局」。
- [做了] 复活：每 2.5 秒调 RestartPlayer，30 秒还没复活写日志（含 RestartPlayer 标记）继续试；新人物出现后传回、发装备、原生保护。
- [做了] 墙失败（找不到模型/拿不到 World/某块没成）：日志写原因，比赛照常，掉血圈回退成圆。墙刷完自动传 duel-* 日志。
- [没做/限制] 地面只按两个出生点高度插值，没有射线检测：坡地超过 1.2 米会露底；点记在水里/楼里不做检查（录点时自己选平地）。
- [做了] 墙不困人：外圈离出生点 ≥12 米，场内离出生点 7 米内不放、不压站着的人；枪摆成 8 列方阵在出生点朝对手 2.5–5.5 米处（不被墙压住）。
- [做了] 连续开新的单挑：每局 HostMatch.newMatch → Duel.reset → Walls.reset 清状态；上一张图的墙随世界销毁（每次单挑都是新开房换图）。
- [做了] 房主幽灵人物不算（只认 NetConnection 控制器），第三个人进来不算。
- [做了] 20 头结束：不再复活、停掉圈、拉回、算分；房主 toast + 日志 + 自动传 duel-*。客户端没有结果界面（同上，无 HUD 通道）。
- [注意] 没记过单挑点的那张图：第一局要上飞机跳（游戏自己的流程），第二个人晚进可能上不了飞机 → 建议两人都在时用「记录单挑点」。
### 验证情况
- 反汇编核对：上面所有偏移；unicorn：客户端 SetStaticMesh 补丁（emu_meshpatch.py）、FQuat 约定（emu_quat.py）；ucall5 5 参数路径沿用 v3.05 emu_ucave。
- 未实测：手机上墙能否生成/复制/看见/挡子弹、选到的模型长什么样、出生保护是否生效、闪烁、等人流程。看日志：「单挑围墙：模型：…」「单挑围墙：外圈 n 块、场内 m 块…」「单挑围墙（客户端）：收到房主复制的墙 N 块，带模型 M 块」「单挑：出生保护：GM+2e0 … SpawnProtectionTime x→3.0」「原生出生保护：Eligible=1，CreationTime …」「保护闪烁 n/12」。

- 2026-11-19 02:19 用户规则：掩体不要车、不要防弹墙，要真实建筑墙；要外圈围墙；掩体要明显比人高（不能跳枪）；两人都进来才开始；要全面考虑周全、打磨好再交付。v3.19 已按此做，未实测。

## v3.20（vc221/core221，2026-11-19 02:4x，三包，未实测）— 单挑：同一张图再开一把
- 02:21 用户：打完 20 个头还要能接着打，不用退房间重开。
- 结束（20 头）：只是我们自己的结束（over=true，记 endAt），房主 toast「10 秒后自动再开一把」+ 日志，**不调游戏任何结束/结算函数**。10 秒后：两个加入玩家（NetConnection 控制器）都在 → 自动 newRound；不全在 → 也清场，回到「等待对手加入」，两人到齐（都活着、都在点上）自动开始。
- 对局里服务端常用页「单挑」加按钮「再开一把」：任何时候（等人、选枪中、打到一半、结束后）马上重开。按钮只置标志（rematchReq），清场在单挑线程下一次循环里做，和摆枪/发装备/复活串行；清场中再按提示「正在重开」。
- newRound 顺序：
  1. 上一把围墙线程（wallsTh）还在跑就 join（最多 90 秒），不然刷完的墙没人删。
  2. 活着在场的人背包里 204 投掷物、206 子弹按数量 ServerDiscardItem 0xcd6728 丢掉（Box.dropAll 用过的同一路径/参数）。**枪、头甲包不丢**：v1.98 实测丢枪闪退过，没有安全删枪路径 → 留着，照常发新装备，日志「身上还有上一把的枪 n 把」。
  3. 删墙 Walls.destroyAll：Walls.spawned 里每个（Prop.alive + 类仍是 AStaticMeshActor）调 K2_DestroyActor。place() 改成生成成功就记进 spawned（以前半成功的那块不记 → 会漏）。
  4. 清地上：扫 GUObjectArray，类名含 pickup/deathdrop/dropbox 且不含 component/manager/config/spawner/generator/point/airsupply/weap/character/controller/vehicle/widget/data，非类默认对象（+4 & 0x10），根组件是 Component，位置在围墙矩形外扩 15 米内（没墙就两个点 60 米内）→ K2_DestroyActor，最多 400 个；日志列类名计数和这个类虚表 +0x164 的目标（看是不是 1140af4）。
  5. 比分 0、所有每人计时/标志（rowAt/deadAt/protectUntil/movedAt/hurtAt/tryAt/leashAt/respawnSince/picked/kitted/reKit/last）清掉，over/started/paused/winner/endAt/announced/wallsDone 清掉，round+1；单挑点（home/placed）、圈中心、GameState 出生保护时长保留。
  6. 活着的：记下背包里已有的枪对象（oldGuns）→ 传回自己的点、补满血、原生出生保护（protect，3 秒 + 闪烁）。死着的：deadAt = now-5s → 下一次循环立刻 RestartPlayer（原来的复活路径），新人物传回、保护。
  7. 下一次循环：两人到齐 → started → 重新摆枪（rowAt=0）、45 秒选枪；围墙重新刷（wallsDone=false）。
- 选枪：重开时身上有旧枪的人，只认背包里**新出现**的枪对象（gunIn 跳过 oldGuns），不用手上枪类名判断（手上可能是旧枪）；旧枪集合按人物记，复活换了人物就作废。
- 重复按/线程：gen 计数（reset/newRound 各 +1）；闪烁线程看 gen 变了就停（finally 照样显示）；围墙线程启动时 gen 不对就不刷；中途换图（HostMatch.newMatch → Duel.reset）newRound 在每一步检查 gen，不对就停。
- 重连：接手掉线记录时 reKit = 已选枪；开打后他活着 → 背包里没枪就补发装备（有就不补）、传回点、出生保护。
- 游戏自己的结束：0x100fbd4 查结束（核对：0x100fc60 仍是 cmp r0,#1 / 补丁 #0）—— HostMatch.solo 现在 `noEnd(hideHost || Duel.on)`，单挑开着时不管「去掉房主人物」开没开都只在 0 队活着才结算；全员退出 Lan.QUIT 0xc89960 照旧；不上飞机/不刷圈照旧（没记点那张图第一局仍是上飞机，后面的把都在点上）。
- RE（capstone）：K2_DestroyActor exec thunk 0x2e6f950 = `ldr r1,[r0]; ldr r1,[r1,#0x164]; bx r1`（和 SetActorHiddenInGame vt+0x160 相邻）；AStaticMeshActor 虚表 0x4f42690（构造 0x2d98764 写的）+0x164 = 0x1140af4 = `mov r1,#0; mov r2,#1; b 0x273e8d4`；0x273e8d4 = AActor::Destroy（查 +0x110 bit2、对象表 PendingKill，GetWorld vt+0xa0，bl 0x2aab2e8）；0x2aab2e8 = UWorld::DestroyActor（函数里 "UWorld::DestroyActor: World has no context!"、LevelActor.cpp）。没有新 cave、没有新补丁字节（走已有 ucall 'U' 路径，所以没做 unicorn）。
- 日志（中文）：「单挑：房主按了「再开一把」」「单挑：第 n 把结束 … 10 秒后…」「单挑：结束 10 秒，在场的只有 n/2 人 → 清场回到等人」「单挑：再开一把（第 n 把，原因）：开始清场」「单挑：再开一把 第 n 把，…：丢掉子弹/投掷物 x 件；删墙 a/b；清地上东西：找到…删了…{类名=数}；活着传回 x 人、待复活 y 人，用时 s 秒」「单挑：玩家 n 重连回来…」。开房页/常用页状态行带「第 n 把」和「上次重开：…」。
- 未实测：K2_DestroyActor 在手机上删墙/拾取物是否同步到客户端（看客户端「收到房主复制的墙 N 块」数字是否先变小再变回来）；拾取物类名过滤是否抓得到（看「清地上东西」的类名表，抓不到就按表改 PICK_IN）；丢子弹/投掷物是否正常；旧枪留在身上对选枪/发装备的影响。

## v3.21（vc222/core222，2026-11-19 12:2x，三包，未实测）— 单挑自动选场地、不刷毒对局修飞机、单机版菜单单机/联机、删物资/门/出生岛空投
- **用户规则（12:08，生气）：不要让用户记录任何地点，程序自己选场地；服务端只开房，什么都不做（房主人物隐藏冻结，不上飞机、不跳伞）。**
- 用户截图：开房页还显示「记录单挑点」「清除单挑点」和「单挑点：海岛没记，雨林没记（第一局上飞机自己跳，落地就记）」；测试时房主视角进了灰色虚空，客户端落在海岛房子旁。
### 单挑（Duel.java / 新 DuelArena.java / 新 Trace.java / Walls.java）
- 删：记录单挑点/清除单挑点（开房页、对局常用页）、prefs duel_e/duel_s、saved/save/clear/pts/recordNow、「没记点第一局上飞机跳、落地静止记点」。
- 单挑一律 Mods.setNoZone()（v3.17~3.20 没记点时调 setPlane(false) → 所有人上飞机，房主隐藏冻结的人物被飞机带走 → 房主视角灰色虚空）。房主人物照旧 HostMatch 隐藏+关碰撞+冻结，不跳伞。
- 流程：加入玩家在游戏放他的地方（出生岛）等 → 有人「站稳」（CMC(人物+0x2f8)+0x138 MovementMode≠3 下落、1.5 秒内移动 <60 厘米）就后台选场地 → 两个人都在、都活着、都站稳（或已在点上）且场地选好 → 一起 K2_TeleportTo 到两个点 → 照旧：围墙、选枪、发装备、出生保护、计分、再开一把（重开沿用同一场地）。状态行：「单挑：第 n 把，等待对手加入（k/2）/两人都在，等场地选好/等站稳传送…；场地：自动选 x,y（两点相距 50 米，高 …）；围墙…」。
- 选场地（DuelArena.choose）：
  1. 候选 = GUObjectArray 里已加载的 LandscapeHeightfieldCollisionComponent（没有就 LandscapeComponent）位置（组件 +0x140 位置）。外框四边各去 8%；离在场玩家（出生岛）800 米内不要。海面 = 名字以 sea_/ocean 开头的 actor 根组件 Z 的最小值（游戏日志 CheckWaterBelow Actor = sea_1__11）。
  2. 候选随机打乱，地形块位置再随机偏 ±40 米；中心往下打射线：要打到地形、比海面高 1.5 米以上。
  3. 场地：A、C 相距 50 米、方向随机；围墙矩形 u∈[-12,62] 米、v∈±17.5 米里 5×3 个点：全部地形、高差 ≤1.5 米；外扩 20 米一圈 8 点：非地形（房子/树/石头）最多 1 个、地形高度和场内平均差 ≤4 米、不低于海面+1.5 米。
  4. 第一个通过的就用；同进程同一张图（Con.brMap）记住，下一局先打一条射线确认还在就沿用；围墙一块都没刷成就忘掉、下局重选。最多 60 个候选 / 90 秒。
  5. 没候选/射线不能用/全被拒 → 用玩家 1 站稳的位置沿随机方向 ±25 米（射线能用就贴地）。日志一行写：地形块数、海面、地图范围、可用候选数、第几个通过（中心、15 点高差、外圈非地形数）或拒绝原因计数（含打到的类名）、射线次数、用时。
  - 出生点 Z = 地面 + 90（人物中心，和以前读人物 Z 一样；Walls 用 Z−90 当地面，传送再 +50）。
  - 限制（没法解决、要实测看）：房主是 listen server，ServerOnly/WorldMeshes（房子碰撞）层默认不加载（见 v2.20），所以射线在房主这边多半只看得到地形，**房子不一定能被射线看到**；只能靠「打到非地形就拒」+ 平地 + 外圈检查。若用户反馈场地在房子里，下一步：选场地前临时把 ServerOnly WorldMeshes 层加进 CurrentLayers（Loot 的 SL cave 那套），或按地形块再加更严的筛选。
- 射线（Trace.java，无新 cave、无补丁字节）：UKismetSystemLibrary::LineTraceSingle 通过 UObject::ProcessEvent（虚表 +0x100）调，走已有 ucall 'U' 虚表路径（r0=默认对象 Default__KismetSystemLibrary，r1=UFunction LineTraceSingle（外层 KismetSystemLibrary、类 Function，运行时扫 GUObjectArray 找），r2=参数块）。
  - capstone 核对（game/re/trace_re.py，全部 assert 过）：0xe66908 RPC 包装 `ldr r5,[vt,#0x100]; bl 0x1bd3414(FindFunctionChecked); blx r5(this,func,&parms)` → ProcessEvent=vt+0x100；FFunctionParams 0x4fd05c4（名字 LineTraceSingle、参数块 0xe8、属性数组 0x4fd0588 共 14 个、函数标志 0x04c22401 = Native|Static|Final）：0 WorldContextObject、4 Start、0x10 End、0x1c TraceChannel、0x1d bTraceComplex（setter 0xe4a2bc）、0x20 ActorsToIgnore、0x2c DrawDebugType、0x30 OutHit、0xb8 bIgnoreSelf（setter 0xf1e44c）、0xbc/0xcc 颜色、0xdc DrawTime、0xe0 ReturnValue（setter 0x24319ac）。FHitResult（属性表 0x4f8d0xx~0x4f8d3xx）：+0x18 ImpactPoint、+0x3c TraceStart、+0x64 Actor 弱指针（对象序号）、+0x80 FaceIndex。
  - 参数：World 当上下文、TraceChannel 0（Visibility）、不复杂、不忽略。打到的 actor 用对象序号查 GUObjectArray，类名/名字含 Landscape = 地面。World 10 秒问一次。找不到函数 30 秒内不重扫。诊断里「游戏模式」行下面两行：「地面射线：…（调用 n 次，打到 m，失败 k）」和「场地：…」。
- 围墙每块中心射线贴地（Walls.snap：只认地形、和两点插值差 >4 米不信），状态行加「射线贴地 a/b 块」。
### 「不刷毒对局」还上飞机（12:11 用户，单机版 v3.20）
- 日志（ntfy，客户端包=用户说的单机版）：upd-1119-120323「启动时恢复「正常对局」」；12:03:22 SetGamePhase current[0] new[1]（钩子在 0xffc1e4 = 函数入口+0xc、日志前，钩子生效时日志会是 new[4]）→ 那局进图时模式还是正常，「不刷毒对局」是之后在菜单里选的（matchend-1119-121032「手动选「不刷毒对局」→ ok / ok」），读秒已经开始；补丁只拦以后的阶段切换。服务端日志：恢复不刷毒 ok 后「现在 不上飞机=false」= v3.17 起 Duel.open 没记点时 setPlane(false)，按钮高亮用 noPlane||noPoison 仍显示不刷毒。
- 修：Mods.matchZone()——每次进对局图（Con 进图处；加入别人 role==2 不管）按存的模式（prefs zone_off）重打不上飞机/不刷圈；不刷毒时开 60 秒「阶段守护」：阶段已是 1（读秒）或 2（刚上飞机）就在游戏线程调 SetGamePhase(GameState=GM+0x2e0, 4)（0xffc1d8，NZBRGameState.cpp:73，r1=阶段，capstone 核对；钩子本来就是把 1/2 改 4），最多 6 次，日志「阶段守护（…）：阶段 1 → 调 SetGamePhase(4) …」；对局里点「不刷毒对局」也会立刻守护。菜单高亮改成按用户选的模式（Mods.zoneOff）。单挑不再 setPlane(false)。诊断「游戏模式」行后面带守护状态。
### 单机版菜单（12:11/12:13 用户）
- 大厅菜单（Ed.single()，单机包和客户端包都是）最上面加分段「单机 | 联机」（prefs lobby_net，记住，不弹窗）：单机 = 游戏模式 + 自动刷人机 +「选好后点匹配赛」；联机 = 「加入艾挽服务器」+ 自己填地址（GameOverlay.joinCard，和客户端包同一张卡片）。
- 标题 titleText：加入了别人房间（Lan.role==2）=「全军出击联机版」；本机对局 =「全军出击单机版」；大厅按分段。refresher 每次刷新都更新标题。
- 检查过：单机/联机弹窗早就没了（Ed.canChoose()=false，GameOverlay 加载页选择框不会出），没东西可删。加入后对局菜单 cliT（Lan.role==2）只剩「联机」页；Lan.join 设 Bots.auto=false；HostMatch/Duel/Box 只在 Ed.ED==1/role 1；Flow 进图 role 2 直接 return；matchZone role 2 不动 → 加入的单机版不会刷人机/改流程。
### 删掉（12:14 用户）
- 「地图物资/门 关/开」开关和开房页 Loot.shortText 行删掉（Loot 默认关，不再有入口；代码留着，Loot.line 关着时什么都不做）。
- 「出生岛空投」开关删掉；Box.on 默认 false；HostMatch 不再调 Box.tick（开局物资堆、排车不跑）。Box.joinedPos/dropAll/DISCARD 留着（刷东西放加入玩家脚下、单挑清背包要用）。
- 「正常对局」现在 = 游戏自己的流程（读秒、飞机、毒圈按 Flow），出生岛不再放任何东西。地图物资：现有代码里没有一个能用的「自动刷地图物资」——物资点在 ServerOnly 流式层，单机(NetMode 0)/房主(2)都不加载（v2.20 RE），唯一的办法就是刚删掉的 Loot（SL cave，从没实测成功过）。所以正常对局地图上目前没有物资，没新做系统（用户要的话下一步再议）。

## v3.22（vc223/core223，2026-11-19 16:0x，三包，未实测）
- 15:42/15:44 用户：单机修成联机那样；新海岛用战术模式；删自制道具键；水里加速不生效；标点单机不行；游戏内更新改成从仓库拿资源；黑框提示直接删（不要精简，特定情况才要）；单机不记日志、不往手机放文件。
- 黑框：GameOverlay.toast 只弹含 失败/出错/错误/闪退/没反应/找不到/没找到 的，其余只进 logcat；exec 成功不再弹。
- 单机不记录：AvatarLog.off()（ED 0 且没加入别人）→ export 直接返回，不上传、不进 zf_logq；Server.autoCrashSave 单机直接返回。
- 删自制「道具」键：GameOverlay 不再 PropBtn.attach（代码留着）。
- 单机新海岛：Con.startGame 编号 3 → MAP_ERANGEL2 + "?game=" + 战术模式蓝图（ModeProbe.cache Props）。
- 水里加速：RE 确认 NZ CMC GetMaxSpeed 0xd370f8 游泳时用实例 +0x16c，但 v2.74 写 MaxSwimSpeed 实测无效（原因未查清）→ 游泳模式(4)改用时间倍率（和走路一样，动画按 slowMo 放慢），MaxSwimSpeed 只还原原值。注意潜水时氧气会按倍率掉得快（用户 v2.73 提过）。
- 标点：单机诊断「找队伍：PS+35c NZBRTeamInfo 成员 0」。RE：0x1020614 = 按队伍成员(+0x300/+0x304，步长 0xc)重建 TeamMemberInfoList(+0x2f4/+0x2f8，每项 0x50)，只被 OnTeamCharacterRep(exec 0x11eddf8，RepNotify，单机不触发) 调；0x102085c 每帧把控制器 +0xa04/+0xa0c 抄进列表(+0x30/+0x2c)。MapMark.fixTeam：单机列表 0 且成员>0 时用 Veh.ucall 在游戏线程调 0x1020614(队伍)，3 秒一次最多 40 次。
- 游戏内更新/资源包：没做。游戏自带资源下载走腾讯 Dolphin/Puffer（服务器已停、要真 pak 资源），这个包启动不建更新页实例（v2.69 探测）；我们的内核已经是发到仓库 core/ 后游戏启动自动拿。

## v3.23（vc224/core224，2026-11-19 16:2x，三包，未实测）
- 16:11 用户：加载游戏那样的更新进度条，公告改成我们的（好知道更新了什么）。
- RE：游戏更新页 UNZUpdateUIView（0x15d9d60 InitNoticeInfo 公告取 https://hy.pubgm.qq.com/UpdateInfo/UpdateInfo1.0.3.1.2.json，固定拷 0x3c 字符），真正的更新走 GCloud Dolphin(version_mgr, update_type 20)+Puffer，服务器停了，日志超时跳过；这个包不会建出更新页实例。
- 做法：三个包都在 StartupMap 加载页盖一层（GameOverlay.srvUpdatePage）：上面「公告」= 仓库 core/notice.txt（各镜像，拿不到用包里 NOTICE），下面黄色进度条 = NetCore 下内核进度；最新/失败满 6 秒关，下好 2 秒后自动重启，点一下关，最多 40 秒。
- 以后每次发版：改 core/notice.txt 和 GameOverlay.NOTICE（写这版更新了什么）。

## v3.24（vc225/core225，2026-11-19 16:4x，三包，未实测，试验）
- 16:14 用户：为什么建不出来？大厅自建服务端都可以，这个肯定也可以，再试试。
- RE：UNZStartupFlowCheck 回调 0x1210ef8：配置标记([0x18576e8()] byte0)为 0 → FlowManager(+0x1c)->+0x18 对象 +0x348 = 0（0x12110d4 strb，日志 "FlowManager Disable Update"）→ 更新步骤被关；UNZStartupFlowUpdate::Begin 0x121244c 才 CreateWidget('/Game/Startup/UI/UpdateUI.UpdateUI_C') 存 +0x34。更新页 OnUpdateProcess = vt+0x240(this, float 进度(r1), const FString& 文字)，原生 thunk 0x16aa9f0。v2.69 实测启动阶段 ucall/Con.exec 不通。
- 做法（UpdFlow.java）：启动早装把 0x12110d4 改 nop（4813c4e5→00f020e3）。保险 filesDir/zf_updflow.try：装前建、进大厅删；上次装了没进大厅 → 这次不装。日志见 "Begin Flow…Update" 时我们的加载页层去掉黑底。Con.tickAudio 开机进大厅在开了时等「查完更新且满 8 秒」（最多 40 秒）。
- 下一步（看实测）：页出来了 → 找办法在游戏线程调 vt+0x240 喂我们的进度（启动阶段 ucall 不通，要挂每帧函数 cave）；公告 InitNoticeInfo 的 URL 59 字符可换成 GitHub Pages 地址（要知道 json 格式）。没出来/提示没蓝图 → 这包里更新页资源被删了（用户 v2.69 时说过「游戏自带更新页早就去掉了」）。

## v3.25（vc226/core226，2026-11-19 17:3x，三包，未实测）
- 17:07 用户（v3.24 实测，截图 notes 未存）：更新页还是我们的弹窗（游戏后面在加载）；探测器（飞的道具）视角还跟着自己；标点传送没修好；少了人偶；道具名没改中文（配件 04 2 / Extended / CrossBow）；删「恢复键位」「传送到地图中间」；服务端标点传送 → 所有人跟着传；高跳 67 倍；抛物线联机正常 → 单机伪装成联机。
- v3.24 截图里我们那层是透明底 → 日志里看到了 Begin Flow…Update（gamePage 跑了），且没闪退 → CreateWidget 成功（Begin 0x12126f4 之后不判空直接用）、AddToViewport(0)、vt+0x23c/0x278/0x244。页面空白多半是没人喂进度（Dolphin 失败）。
- 游戏线程调用槽：Server.tickCave 加 cb 段（FEngineLoop::Tick 开头每帧看 cb+0：非 0 就 blx(r0~r3=cb+4..+16)，返回值 +20，次数 +24），unicorn 测过（/tmp 脚本）。单机随 Server.install 装；联机两个包 Server.installTick 只装 TICK（连接器 0，不投包）。Server.gcall(fn,r0..r3,waitMs)。
- UpdPage：GUObjectArray 找类名 UpdateUI* 实例（最多 25 秒，找不到 → GameOverlay.srvFallback 用我们那层）。公告：0xfb1be8() = UNZUpdateInfo，+0x3a8 公告数组（每条 0x34：+0 是否图片、+0x10 正文 FString、+0x1c 标题、+0x28 图片链接），临时换成我们一条 → 调 0x15de914(页面,0)（先查 +0x224/+0x228/+0x238/+0x240 控件非空、+0x4e8 为 0）→ 还原数组。进度：每 0.3 秒 vt+0x240(页面, float, FString*)（UTF-32）。Con hold = UpdFlow.on && !UpdPage.finished（最多 60 秒）。
- 菜单：删两个按钮；高跳 0~67 步长 1；同步编码改 7700000 + 加速×2×1000 + 高跳×2。
- 道具中文：SUB_CAT（20501 枪口/20502 握把/20503 瞄准镜/20504 弹匣/20505 枪托/20600 子弹/40100 回血药品/40101 能量药品/40200 道具，按 v3.21 日志名字推断）+ CN 关键词表；配件里的枪名 → 「XX 专用弹匣」；没名字 → 「分类 序号」。
- 人偶：不再因缺资源/没模型删 402；zf_items_bad.v325 一次性把删掉的 402 放回。
- 服务端标点传送：房主传送+慢落后（Fly.busy 结束），Crew.find + doPull 把加入的人拉到身边 2 米一圈。
- 单机伪装联机：Con.startGame 单机（role 0）且 Lan.fakeOnline 时先 Lan.unlock()，地图加 ?listen（同开房，没人进）。功能页开关「单机按联机方式进图」。探测器视角没单独查，靠这个。

## v3.26（vc227/core227，2026-11-19 17:5x，三包，未实测）
- 17:40 用户（v3.25 实测，截图）：单机开无敌还是摔死；标点传送要下局才生效；更新页还是我们的（客户端包加载页显示我们的公告+「已是最新」）。用户猜是以前关更新失败弹窗改掉的（Patcher UPD_OFF 0x15de030 ShowUpdateError→bx lr，只关报错，不影响建页）。
- 真正原因：UpdFlow.install 在启动早装时 Con.act 还是 null，Ed.file 只有 canChoose() 才 init → dir 为空 →「没目录，不装」，UpdFlow.on 一直 false（v3.24/3.25 根本没试成）。修：Patches 里总是 Ed.init(ctx)。我们那层底部显示 UpdPage/UpdFlow 状态（单机不导日志，看截图）。
- 无敌：单机伪装联机(listen)后控制台 God 要作弊权限 → 直接写 AActor::bCanBeDamaged = pawn+0x110 半字 bit1（RE：FBoolPropertyParams 0x4f5014c，SetBit 0x2e73790 `orr #2` at +0x110，SizeOfOuter 0x2b0），关时还原；仍补血。
- 标点：打开自动传送开关时 tpNow，已有标点（+a0c 开）就马上传。

## v3.27（vc228/core228，2026-11-19 17:5x，未实测）
- 17:48 用户：更新就走游戏更新页，别在腾讯 logo 页弹更新。GameDlg.updatePrompt 只在 lobbySeen 后弹；启动阶段下好由 UpdPage（或我们那层）自动重启。

## v3.28（vc229/core229，2026-11-19 18:0x，未实测）
- 17:52 用户：不是要走完更新进度条才进大厅吗。Con hold 改成：查更新没结果（NetCore.done 假且没下好）或游戏更新页没走完就不进大厅，兜底 10 分钟；UpdPage 进度循环上限 60 秒 → 10 分钟。下好 → 页里自动重启。

## v3.29（vc230/core230，2026-11-19，未实测）
- 17:55 用户：已经联网了，应该能修。NetCore.check 失败不再 done（不放行），3/6/9…最多 30 秒间隔自动重试；Con hold 和 UpdPage 去掉 10 分钟上限，只有「已是最新」或下好重启才结束。

## v3.30（vc231/core231，2026-11-19 18:1x，未实测）
- 18:01 用户截图（客户端包单机模式 v3.29）：加载页底部「游戏更新页：没开始」+ 我们自己的公告层；单机标点 3237m 不传送。
- 更新页根因：UpdFlow 的保险文件 zf_updflow.try 只在进大厅删；v3.28 在更新页下好直接 NetCore.restart（没进大厅）→ 下次启动当成卡住不装 GATE。修：restart 前和 UpdPage 找到页面时删保险文件；状态行没启动 UpdPage 时显示 UpdFlow.state。
- 标点：autoTp 不存档、默认关，更新后重启就变关（推断是没传的原因）。改成 prefs mark_tp 默认开；控制器 +a04 开关为 0 时从队伍列表 [0]+0x2c/+0x30 读标点；菜单副标题显示 MapMark.state；传送失败 toast。

## v3.31（vc232/core232，2026-11-19 18:1x，未实测）
- 18:06 用户：开车下车后人物消失，是把自己角色做成服务端（?listen）的后果；剩余人数的占位还在。Lan.fakeOnline 默认改 false（真单机）。占位那个不是真玩家（没控制器/没手机），不能当房主；隐藏空位仍待做。

## v3.32（vc233/core233，2026-11-19 18:2x，未实测）
- 18:09 用户：去掉剩余人数里的占位（要作用到那个人身上，不是改数字）。新 Phantom.java：真单机进图 20/60/120 秒，用 Alive.collect 拿被数进剩余的人物，排除我、Bots.live、玩家控制器、+5a0<1，隔 4 秒两次都在 → K2_DestroyActor（ucall vt+0x164）。状态进诊断（单机不导出）。
- 用户同时问：真单机抛物线/标点能不能用 → 回：标点能（自己的代码）；抛物线不能，下一步查单机下抛物线被哪里关掉。
- 18:19 用户要一劳永逸（从根上不生成占位）。字符串扫描：没有 Phantom/FillBot；有 SpawnDollCharacter（Doll 本来就不算剩余）、MinPlayers、bIgnorePlayerControllerInStandalone、bUseAIStartFireInStandalone。占位是谁刷的还没定位。
- 18:20 用户：Grok 解开了 pak（assets/main.obb.png 里 PUBGM/Content/Paks 321 个，83436 条），会发 pak-files.tsv；我们只读用户给的资源，不自己解密。下一步：从清单挑 GameMode/出生/投掷物蓝图让 Grok 单独提取。

## 2026-11-19 18:35 用户发来 Grok 解出的部分 pak 资源（本地 /workspace/work/pakx，不进 git）
- 训练营地图真名：/Game/Maps/TrainingIsland_Runtime/BR-TrainingIsland_Mobile_Main（.umap 在 pakchunk_c2afa52b…，不是 libUE4 里的 BR-Training_Mobile_Main，所以 v1.12 open 报不存在）。地图默认 GameMode = BP_NZBRPracticeGameMode（训练场）。
- 训练营模式蓝图：/Game/Blueprints/TrainingGame/BP_NZBRTrainingGameMode.BP_NZBRTrainingGameMode_C（HUD BP_NZBRHUD_Training，人机 TrainingBot，HideXxx 开关、MaxBotCountPerTeam）。可试 open 地图?game=该类。
- LuaSource 是明文 Lua（uexp 里直接是源码）：cs_lottery_pb（CustomLottery List/Commit/Rand/Reset）、cs_treasure_pb（TreasureInfo / TreasureExchange）= 军需协议字段。缺 cmd_pb（命令号）、common_pb（Prop/PlayerDataChange）、cs_pkg_pb、s_pkg_pb。LuaSource 全部 642 条约 2.1 MB，已向用户要全部。
- 自建服务端仍需要：协议回包和更新文件都不在 pak 里。

## v3.33（vc234/core234，2026-11-19 18:45，未实测）— 训练营/训练场
- 第二批 pak（LuaSource 全部 321 个 lua，/workspace/work/pakx2/lua 已抽成明文）：cmd_pb 证实军需相关命令号 31001-31004（宝箱）、38003-38005（卡片抽奖）、51001-51008（自选抽奖），与 v2.48 一致；Lua 里没有军需 UI（Lua 只有成就/活动/邮件/房间 UI），军需页是蓝图 StoreUI。
- 喷气背包：30307000099_Jetpack 蓝图只有 NZItem_JetpackBag + 特效 P_EFX_Jetpack_01，不引用任何声音；声音属性 JetLaunchSoundEvent 在 C++ 类上（推测角色蓝图 BP_NZBRCharacter_BaseMan 填值）；Wwise 资源里没有名字带 jet/fly 的事件。
- 沙漠：pak 里只有 4 张主图（老海岛/新海岛/雨林/训练岛），沙漠只有模式蓝图和贴图碎片。
- 改动：Con.MAP_TRAINING/GM_TRAINING/goTraining；startGame 编号 5 → 训练岛（默认训练场模式，不加 ?listen）；Con.training 标记：训练岛里不跑 Phantom/Mods.matchZone/HostMatch/Bots.matchLoaded/Flow.matchLoaded，只跑标点和喷气背包；单机大厅「训练」两个按钮（训练营 = ?game=BP_NZBRTrainingGameMode，训练场 = 默认）；服务端开房页也加两个按钮（Lan.host）。
- 18:49 第三批（/workspace/work/pakx3）：BP_NZBRCharacter_BaseMan 导入 235（Wwise 50）、BP_NZBRPlayerController 导入 73（Wwise 38），两者名字表/导入表都没有 Jet；libUE4 没有任何喷气背包声音的事件名或路径；DataTable/Lua/ini 也没有。结论：这个版本根本没有喷气背包音效资源，JetLaunchSoundEvent 没人填 → 不是「没加载」，是没有。可选替代：起飞时播跳板音效 /Game/WwiseAssets/Events/Item/Item_FangZhi_Mod/LaunchPad/Play_Lauchpad_Lauch（BaseMan 已导入，必定已加载）。已问用户。
- 18:50 用户：喷气背包音效算了，不加替代声音。
- 18:52 用户（v3.33 实测）：训练营（BP_NZBRTrainingGameMode）进去掉水里、周围什么都没有；训练场（地图默认模式）加载时闪退。
  - pak 索引：训练岛是分块地图（AlwaysLoad/Light、LogicLevel、Ocean、block、cars、ProceduralFoliageVolumes + Heightmap x0..3_y0..3 + Grass 瓦片），推测子关卡没流式加载（只出海面），出生点在 LogicLevel 里没加载 → 落在原点海里。待：Main.umap/LogicLevel.umap 导入和 LevelStreaming 设置 + 闪退诊断。

## v3.34（vc235/core235，2026-11-19 18:5x，未实测）— 更新页显示进度条
- 18:53 用户截图（v3.33）：开机只有游戏更新页背景（试炼季图），没有进度条/文字，没走我们的更新。
- RE：UpdateUI 蓝图里有 ESlateVisibility::Collapsed，UpdatePanel 默认隐藏，游戏只在腾讯 Dolphin 更新开始时显示 → 我们调 OnUpdateProcess 也看不见。UWidget::SetVisibility 原生 thunk 0x25a6764 → 虚表 +0x124(控件, 字节)，0=Visible。
- 改动：UpdPage.showPanel：页面 +0x200~0x260 按对象名找 UpdatePanel/UpdateProgressBar/UpdateProcessText/UpdateProcessValue，Server.gcall 调 vt+0x124(w,0)；失败（没找到/没跑到）弹黑框「更新页进度条失败…」，游戏线程没跑到也弹。
- 训练岛问题（掉海里/训练场闪退）等用户发 Grok 第四批（Main.umap、AlwaysLoad 子关卡）和导出诊断。

## v3.35（vc236/core236，2026-11-19 19:0x，未实测）— 更新层常显、训练岛出生点
- 18:57 用户录屏（v3.34）：腾讯 logo → 游戏更新页背景约 8 秒，什么都没显示（用户退出）。原因：UpdFlow 开时我们那层不出，只等 UpdPage 25 秒找页面。改：开机总是显示我们的进度/公告层（游戏页出来后透明底），UpdPage.showPanel 成功（panelOk）才收起；去掉 40 秒自动关；状态行每 0.3 秒刷新 UpdPage.state。
- 18:58 Grok 第四批（/workspace/work/pakx4）：Main.umap 导出 NZStreamingLevelsManagerActor_TileMode_2、NZWorldSettings、WorldComposition_0；导入 BP_NZBRPracticeGameMode（地图默认模式）；LogicLevel 有 10 个 PlayerStart（PlayerStart2_2…11）、12 个 VehicleSpawnPoint（Actor）、BP_PickUpRefreshVolumn×2。
- 改：Island.java（类名 Training 已被单挑/训练功能占用）：进训练岛 30 秒内每 2 秒扫 PlayerStart 实例和地形块（Landscape*），找到就 Veh.teleport 到第一个出生点上方 150；找不到弹黑框「30 秒没找到出生点（子关卡没加载）」。
- 18:58 用户：不用发安装包（用不到）→ 以后只推 core 在线内核，不再 display_file APK。
- 训练场（默认 Practice 模式）加载闪退：还没日志。

## v3.36（vc237/core237，2026-11-19 19:0x，未实测）— 单机手动导出诊断
- 用户 19:03：单机版没有导出诊断（logRow 只在联机页；AvatarLog.off 单机全关）。改：单机大厅「训练」下加「导出诊断」按钮；AvatarLog.export(a) 手动标 manual 绕过 off()，自动导出仍关；结果提示含「已自动上传」进 keepToast 白名单；诊断加 Island.state、训练岛模式。上传到 ntfy 主题（LogUp.TOPIC），从那里取。

## v3.37（vc238/core238，2026-11-19 19:2x，未实测）— 游戏更新页每次都开；训练岛加载地块；训练场不崩
- 19:09 用户 v3.36 导出诊断（ntfy：avatar-1119-190745/190934、matchend-190938，客户端版）：
  - 训练场 19:07:02 闪退：Game class BP_NZBRPracticeGameMode_C，「ChoosePlayerStart_Implementation level not prepared!!!」，GameThread SIGSEGV fault 0x14，pc 0x1084f6c（ANZBRPracticeGameMode::Login 0x1084c5c）：0x10845b0 找队伍（GM+0xa40 数组/+0xa44 个数，空）返回 0 → ldr r7,[r8,#0x14]。
  - 训练营：天空/海里，有新手引导 UI；Island 45 秒没找到 PlayerStart，地形块 1。
  - 截图：更新页是我们的（状态「游戏更新页：上次开了没进大厅，这次不开」——UpdFlow 保险因用户中途退出触发）。
- RE：NZStreamingLevelsManagerActor_TileMode::LoadBlock(this, 块号, 块数) 0x1163f3c（+0x440 当前块，+0x43c 块数；NetMode==1 专用服直接退）。唯一调用者 0x1086960「Client Load Block」（Practice PC 的 RPC 实现，读 PC+0xbdc/+0xbe0，TActorIterator 找管理器）。FillLayersWithCacheData 0x11649bc，PostInitializeComponents 0x1164d90，TileLayer 0x116543c（%s_LevelInstance_%d）。正式服由服务端发块号，单机没人发 → 子关卡（LogicLevel 出生点、地形）不加载。
- 改：
  - UpdFlow 去掉 zf_updflow.try 保险（每次都开游戏更新页）。
  - Island：扫到管理器（类名 NZStreamingLevelsManagerActor_TileMode）且没出生点 → 当前块为 0 时先写 -1，再 Server.gcall(LoadBlock, mgr, 0, 1)；之后继续找 PlayerStart 传送。诊断加 Island.block/fix。
  - 训练场补丁 PR 0x1084f2c：0080a0e1580596e5000050e35a00000a → 0080b0e158059615000050135a00000a（movs r8,r0/ldrne/cmpne/beq 0x10850a8，没队伍走原跳过分支），启动早装 Island.install。
  - 游戏线程调用槽 installTick 改为只要 !Server.hooked 就装（不再依赖 UpdFlow.on）。
- 未知：块 0 的实例是否在原点附近；LoadBlock 的块数参数是否要 >1；Practice 没队伍后续是否还会崩。

## v3.38（vc239/core239，2026-11-19 19:3x，未实测）— 菜单行为；更新页查找过程可见
- 19:22 用户（v3.37）：开机仍是我们的层，底部「游戏更新页：没开始」（= UpdPage 已开始但 25 秒查找期间不改状态）；训练场能从游戏自己的「训练」入口进 → 菜单里不用做；进训练场掉到训练场旁边很远的海里然后闪退（等导出诊断，19:19 那份是进图前导出的）。
- 用户菜单要求：大厅固定中上（收起那条，截图里「全军出击单机版」），不能拖；取消关闭按钮；进对局自动关；对局里不自动开，五指按屏幕才开；关闭只做收起；往上拖自动隐藏，再开要五指。
- 改：GameOverlay：× 永远隐藏；lobbyPos（收起 + 中上 + 不能拖，标题只点按收起/展开）；placeFor：Con.inMatch → hidden + GONE，否则回中上；mapLeaving 40 秒兜底也走 placeFor；对局里拖标题松手时 y < -24dp → showPanel(false)。单机大厅「训练」组改名「诊断」，去掉训练营/训练场按钮。
- UpdPage：查找时状态写「找页面 N 秒，对象数，带 Update 的实例类名（最多 8 个）」；诊断加 UpdFlow.state / UpdPage.state。

## v3.39（vc240/core240，2026-11-19 19:4x，未实测）— 训练岛退出按钮 + 自动诊断
- 19:32 用户截图（训练岛，游戏自带入口）：天和水正常，山全是黑色剪影、没有建筑；小地图/大地图正常（训练区都在）；人在岛东侧海湾外。点游戏「退出」没反应。用户问是不是没有地图资源。
- 判断：pak 里训练岛资源齐（pak-files.tsv：TrainingIsland_Runtime 330 个文件，Heightmap_x0..3_y0..3、AlwaysLoad/Light/LogicLevel/block、地形材质都在）。黑剪影像是远景代理（OcHmp/hp_*），说明 Heightmap/AlwaysLoad 子关卡没流式加载进来（TileMode 要服务端发块号）——不是缺资源。等 train- 诊断看 Island.block 结果。
- 改：常用页（Con.training）最上面「退出训练，回大厅」= Lan.leave()（open 大厅）；Island.matchLoaded → AvatarLog.autoTrain 50 秒后上传 train-*（绕过单机不导出）。

## v3.40（vc241/core241，2026-11-19 19:4x，未实测）— 训练岛退出走游戏设置
- 19:38 用户：「退出用在设置那个退出上，别乱做」→ 删 v3.39 常用页「退出训练，回大厅」按钮。
- 改：Server 收 15003/15005（CS_ROOM_MATCH_QUIT_ALLOC/QUIT_MATCH_REQ）回成功，训练岛里就 Lan.leave()；Con 日志在训练岛出现 QuitMatch（3 秒一次）也 Lan.leave()。哪条真生效看 train- 诊断。
- 19:38 用户等 50 秒没收到 train- 诊断：大概率还没更新到 v3.39。

## v3.41（vc242/core242，2026-11-19 19:5x，未实测）— 训练岛逐块加载
- 19:45 train- 诊断（v3.40）：游戏更新页「找到；公告换成我们的；UpdatePanel/进度条/文字都显示；进度条已接上」（更新页成功！）。训练场（BP_NZBRPracticeGameMode_C）没闪退（空队伍补丁 ok，Login NZPlayerState error 1 只是报错）；Island 找到出生点 9 个、地形块 1，传到 -156080,-258810,-15900；加载块没试（先找到出生点）。
- 改：传送后 Island.blocks：块 0..min(num,24) 逐个 LoadBlock（+0x440 先 -1），每次后数地形块，最后停在最多那块；完成后自动上传 block-* 诊断。

## v3.42（vc243/core243，2026-11-19 20:3x，未实测）— 训练场按原生流程分位置；收起键；删伪装联机；抛物线诊断
只更新在线内核（构建脚本仍会出三个 APK，照放 releases/，用户不用装）。
- v3.41 block-1119-195412 诊断复核（ntfy）：
  - 「第 9 块地形最全」是假象：v3.41 调的是 LoadBlock(管理器, i, 1)，第三个参数是块数，LoadBlock 0x1163f3c 先写 +0x43c=块数，再查 0≤块号<块数 → 块 1..15 全部「Load Block Out Of Index」（日志 15 行）。只有块 0 真加载了；地形块数 1→4→14→17 是块 0 随时间流式加载出来的。
  - 「所有副本关卡加载完」这道检查 v3.41 已经通过：19:52:44 OnLevelStreamingLayerLoadCompleted Landscape → Tick OnAllDuplicateLevelLoaded（GM+0xa70 由 0x108233c 设，Tick 0x1082cb0 看 +0xa70 && !+0xa21 → 0x108357c）。之后照样没刷车/物资，所以卡点不在这里。
- RE（libUE4）：
  - 训练场 = 一个专用服开 16 份岛。BeginPlay 0x1081d70 建 GM+0xa40 位置数组（0x34 字节/个：[0] 块号，+4..+0xc 出生点，+0x10 玩家 TArray；+0xa44 个数，+0xa4c 块数默认 16）。SetupPlayerStartActor 0x1082868：出生点 = 块偏移 0x1163e2c + 模板出生点（GM+0xa64）。块偏移：边长 int(sqrt(N)+0.5)，(行-1.5)×W、(列-1.5)×H；日志出生点 X 间隔 140000 → 推断 W=H=140000，岛本身的出生点约 (53920,-48810)（推断）。
  - Login 0x1084c5c：0x10845b0 挑位置 → 塞进位置玩家数组 → +0xa70 已置就 AddPlayerToSlot 0x1083a98，否则排进 GM+0xa24，由 0x108357c 处理。AddPlayerToSlot 写 PC+0xbdc 块号/+0xbe0 块数、传出生点（0x2eca4ac/0x274ca58）、GameState+0x284 登记。客户端 Client Load Block 实现 0x1086960 → LoadBlock(PC+0xbdc, PC+0xbe0)。
  - 单机根因：玩家 Login（19:51:24）早于 GameMode BeginPlay（19:51:29）→ 位置数组空 → 挑不到位置（v3.37 补丁让它跳过）→ 玩家永远不进位置。
  - 专用服 0x1164d90（NetMode==1 才跑）给每块 FillLayersWithCacheData 0x11649bc(管理器, +0x370 普通层, i)，+0x2c8 开着再填 +0x37c 服务器层；客户端 LoadBlock 只填 +0x370。Main.umap 的 ServerLayersSettings 引用 ServerOnly/Pickups、ServerOnly/MeshTiles → 单机地上物资在没加载的服务器层（推断，同 v2.20 Loot 对大地图的结论）。
  - pak（game/pak，只读）：LogicLevel 有 BP_ShootPracticeBase、BP_VehiclePracticeBase、BP_Practice_ActualCombat、VehicleSpawnPoint1..12、PlayerStart2..11、BP_PickUpRefreshVolumn×2、靶子（BP_FlyTarget/MobileTarget/MoveableTarget、ShootRangeTarget Bp_First..FifthRound）。刷车相关属性 VehicleSpawnInfoArray/PracticeSlotIndex → 刷车按位置绑定。
  - 手雷抛物线：画线 0xee9fe4 无联机判断。投掷组件（虚表 0x4c524d0，+0x1c 武器，+0xa8 ProjectileTrajectory）：每帧 0xefa98c（+0x148）、开始画 0xef9454、+0x120 0xef9354（ThrowWeaponTrace==1 调）、+0x128 0xef9994（==0 调）。检查项：武器+0x2b4≥1、人物 vt+0x34c==1、vt+0x350==1、WeaponManager(+0x888)+0x2d8==这把、设置对象 0xc9e8b8 的 ThrowWeaponTrace(+0xd5，配置属性)==0 时要 [武器+0x3e8]+0x24+0x20 ∈{5,6}；持续更新要人物 vt+0x918/0x4c4/0x4ac 都为 0。哪项挡住未定 → 只加诊断。
- 改：
  - Island 重写：等 GM+0xa44>0 → 0x10845b0 挑位置、把 PC 塞进位置玩家数组（数组不够用 FMemory::Realloc 0x1989d38 分配）→ +0xa21 已处理就直接 AddPlayerToSlot，否则排进 +0xa24 → LoadBlock(管理器, 位置块号, GM+0xa4c) → 管理器 +0x2c8 开着就 FillLayersWithCacheData(管理器, +0x37c, 块号) → 等 PC+0xbdc≥0 且 +0xa21（最多 150 秒）→ 射击训练区：人物 1.2 km 内靶子（父类链含 NZSceneItem_RangeTarget）按 50 m 连群，取最大群主轴较密的一端外 10 m、高 2 m → 20 秒后数车/地上物资/刷车点/刷物资区/系统 → 自动上传 slot-* 诊断。删 v3.41 逐块加载。不是 PracticeGameMode 就不做。
  - GameOverlay：标题栏「收起」键只在对局里显示（= 隐藏，五指再开）；删「单机按联机方式进图」开关，换成「诊断 → 手雷抛物线检查」（Parabola.check + 上传 para-*）。
  - 删伪装联机：Con.startGame 的 ?listen 分支、Lan.fakeOnline。单机进图地图串不变（这开关 v3.31 起默认关），不影响单机进图。
- 要看的日志行：slot-* 里「分配位置：…」「训练岛：…」「射击训练区：…」「原生刷车/刷物资（分配后 20 秒）：…」；para-* 里「抛物线检查（手里拿雷时）：…」；游戏日志 AddPlayerToSlot / Client Load Block / Load Block Out Of Index / OnAllDuplicateLevelLoaded。

## v3.43（vc244/core244，2026-11-19 20:5x，只推内核，未实测）— 训练场出生点根修（原生摆人）；训练岛仓库衣服
- 20:44 用户 v3.42 实测：进训练岛人在岛西南很远的海上/空中，1 分钟没传送，山是黑剪影；以前用标点传过去后训练场会加载 → 地形按人位置流式加载。衣服没穿（只有默认内衣）。用户要求：正式流程一进去就在位置上，不要用我们自己的传送当主方案。
- train-1119-204331 诊断：v3.42 把自己排进 +a24 等 OnAllDuplicateLevelLoaded，但整份日志没有 OnLevelStreamingLayerLoadCompleted（人不在岛上，Landscape 层等不到）→ +a70 一直 0 → 死等；出生点读成 0,0,0（读错：位置 +4 是 PlayerStart 数组，不是坐标）；SetupPlayerStartActor 20:42:29 加了 9 个出生点但排法是 4×4（0..3 同 X）。「仓库衣服：还没发」。
- RE（libUE4，只读）：
  - SetupPlayerStartActor 0x1082868：Tick 0x1082cb0 第一次拿到地块管理器（全局弱指针 → GM+0xa3c）时调一次。开头 bl 0x1164d90(管理器, GM+0xa4c)：只在 NetMode==1（专用服）时 str 块数到 管理器+0x43c（0x1164df4），单机/listen 直接退 → 管理器块数保持默认 16。出生点 = 0x1163e2c 块偏移（边长 floor(sqrt(管理器+0x43c)+0.01)，X=(行数-1)·-0.5·W+行·W，Y=(边长-1)·-0.5·H+列·H，W/H=管理器+0x41c/+0x420=140000）+ BP 模板。每个出生点 SpawnActor 成 PlayerStart，加进 位置+4 数组（位置结构：[0] 块号，+4/+8/+0xc PlayerStart TArray，+0x10/+0x14 玩家 TArray）。
  - pak（game/pak 只读解析 uasset/umap 标签属性）：BP_NZBRPracticeGameMode 默认 SlotCount=9、SpawnPointArray[0]=(53920,-48810,-15900) 朝向约 165°；LogicLevel：shootreturn (47835,-48630,-15850)、BP_ShootPracticeBase 盒子 (47800,-48610)、drivereturn (57545,-39960)、AIreturn (49835,-59570)、刷车点 12 个在 (54450..72310,-42490..-32750)、刷物资区 2 个、PlayerStart2..11 在 (37955..59775,-79840..-62539)、靶子 Round 系列 (42668..45997,-48286..-46591)。→ 原生出生点离射击区约 61 米。
  - 根因：块数 16 算出生点（位置 0 = 模板-1.5·140000 = (-156080,-258810)），LoadBlock(块,9) 把管理器块数改 9 → 岛副本按 3×3 放（块 0 偏移 -140000，岛在 (-86080,-188810) 一带）→ 出生点在岛西南约 1 公里海上。正式服专用服先写了 9，所以对得上。
  - ChoosePlayerStart_Implementation（0x10847xx，日志 :343）：控制器是 PC 且 GM+0xa44≥1 → 0x10845b0 找到所在位置（先找玩家数组里有它的，否则第一个空位置，否则随机）→ 位置 PlayerStart 数≥1 → vt+0x64c 从中挑；否则 Super + 「level not prepared」。
  - AddPlayerToSlot 0x1083a98：PC+0x558 有 PlayerState、PC+0xbdc<0、PC 在某位置玩家数组里 → 写 PC+0xbdc=位置[0] 块号、+0xbe0=GM+0xa4c → FindPlayerStart 0x2eca4ac → PC vt+0x30c 设初始位置 → StartSpot(+0x2dc) → 人物 SetActorLocation 0x274ca58 + SetActorRotation 0x274cd14。不依赖 +a70；Login 在 +a70 已置时就直接调它（"call client load directly"）。OnAllDuplicateLevelLoaded 0x108357c 只是把 +a24 队列逐个 AddPlayerToSlot。
  - RestartPlayer 不选：人物已存在时 RestartPlayerAtPlayerStart 不挪人物（UE4 原逻辑）；AddPlayerToSlot 才是训练场自己「把已有人物摆到位置出生点」的函数。
  - 衣服：Bots.giveOutfit 只在 Bots.matchLoaded 里调，Con 里训练岛是 `!training` 才调 matchLoaded → 训练岛从来不发仓库衣服（和出生时机无关）。
- 改：
  - Island.install 加补丁 0x10828b0：`bl 0x1164d90`(368903eb) → `str r1,[r0,#0x43c]`(3c1480e5)，即只做专用服那一步写块数（0x1164d90 其余部分单机本来就不跑）。启动早装。
  - Island.run 重写：等位置 → 管理器（先读 GM+0xa3c）→ ensureSetup：位置 0 的 PlayerStart 要等于 模板+块偏移(GM 块数)，且 管理器+0x43c==GM+0xa4c；不对（补丁没赶上）→ 写 +0x43c、清各位置 PlayerStart 数、GM+0xa3c=0 让 Tick 重跑 Setup → join：补进位置玩家数组（不再排 +a24）→ 冻住人物（+0x58 时间缩放=0）→ 原生 AddPlayerToSlot → 读 PC+0xbdc/+0xbe0 → LoadBlock(管理器, bdc, be0) + 服务器层 Fill → 原生没摆到出生点（差 >50 米）才退回 Veh.teleport 到同一个 PlayerStart，并写原因 → follow：120 秒每 5 秒记坐标/地形块/+a70/+a21/bdc，脚下射线打到地形或 +a70 置或 60 秒就放开（低于地面就抬到地面上 1.2 米）→ 射击区只诊断（shootreturn+块偏移、150 米内靶子数），不传送 → 20 秒后刷车/物资诊断 → 上传 slot-*。
  - Bots.trainingOutfit()：训练岛摆完人发仓库衣服（同普通对局 cave 'I'）。
  - 诊断：slot-* 加「出生点建立：」「坐标/地形块随时间：」「仓库衣服：」「训练岛关键日志：」（Con 每行日志在训练岛时抓 OnAllDuplicateLevelLoaded / OnLevelStreamingLayerLoadCompleted / AddPlayerToSlot / Spawn Vehicle / Load Block / SetupPlayerStartActor 位置 0 / level not prepared）。
- 要看的日志行：「训练场空队伍补丁：ok；块数补丁 0x10828b0：ok」；游戏日志 SetupPlayerStartActor Add Player Start 0 应为 (-86080,-188810,-15900)、1 应为 (-86080,-48810)（3×3）；「出生点建立：… → 对（块数补丁生效）」；「分配位置：…原生 AddPlayerToSlot 已跑；之后 PC+bdc=0 +be0=9，人物 -86080,-188810,…（原生摆到出生点了）」；「坐标/地形块随时间」地形块是否涨、何时放开；关键日志里有没有 OnLevelStreamingLayerLoadCompleted Landscape / Tick OnAllDuplicateLevelLoaded；「射击训练区：…算在 -92165,-188630…离它 N 米…150 米内靶子 N 个」；「原生刷车/刷物资（分配后 20 秒）」；「仓库衣服：进对局发了 N 件」。
- 未知：AddPlayerToSlot 后 FindPlayerStart 是否真挑到位置的 PlayerStart（vt+0x64c 实现未看）；PlayerStart z=-15900 是否在地面上（射击区地面约 -15785..-15885，可能略低 → follow 里抬）；Landscape 层完成是否真跟人位置有关；刷车/物资是否会因 OnAllDuplicateLevelLoaded 自己出来。

## v3.44（vc245/core245，2026-11-19 21:3x，只推内核，未实测）— 训练岛块 0 不流式加载：去掉服务器层填充；冻结改停重力；流式诊断；仓库瘦身
- 21:07 用户 v3.43 实测（t3 / ntfy kvg6EJ9Yrmkz）：块数补丁、原生 AddPlayerToSlot、出生点 (-86080,-188810,-15900)、仓库衣服 3 件都好；但脚下岛一直不加载，悬空 60 秒后掉海里。诊断 13s/38s 地形块 1，只有 21:05:14.428「LoadBlock Load Block 0」，没有 OnLevelStreamingLayerLoadCompleted / OnAllDuplicateLevelLoaded。「训练岛：没进」是 state 只在 follow 结束才写、50 秒自动诊断早于它（显示 bug）。
- 三版对比（证据）：v3.41 只调 LoadBlock(0,1) → 地形块 1→4→14→17、19:52:44 Landscape 完成；v3.42 LoadBlock(0,9)+Fill(+0x37c) 没加载；v3.43 LoadBlock(0,9)+Fill(+0x37c)+冻结 没加载。v3.43 LoadBlock 前 +0x440=-1（不是 Already 跳过）。冻结只有 v3.43 有 → 不是主因。共同差异 = 我们自己加的服务器层 FillLayersWithCacheData(管理器,+0x37c,块)（v3.42 起）和块数 ≥2。
- RE（libUE4 只读）：
  - LoadBlock 0x1163f3c：+0x440==块 → 「Already Load Block」退；专用服退；写 +0x43c=块数、+0x440=块；越界退；world+0x6d8==3 且 +0x409==0 走另一条（WorldComposition 式，日志里没走）；否则算块偏移存 +0x468/+0x46c，块数≥2 时把常驻关卡里 ANZLandscapeProxyMeshActor（远景黑剪影）挪块偏移；Fill(+0x370 客户端层缓存, 块)；+0x32c 有就把块偏移写全局（0x5173200 标志 + 0x51732ac 向量，只给远景代理 OcHmp/OcPolygons 用，0x11547ac / 0x115ef34）。
  - FillLayers 0x11649bc：按管理器当前层 +0x2f4/+0x2f8（TSharedPtr）的名字在给的缓存（每项 0x20，[0] 层名）里找 → TileLayer 0x116543c：每个关卡生成「%s_LevelInstance_%d」（块号做后缀），+0x388 映射登记偏移后的包围盒，0x1165bf4 NewObject 关卡流对象（world+0x6d8!=3 时不查已有的），层 vt+0x14 AddLevel，追加到 +0x358。
  - 管理器 Tick 0x115e7dc：非专用服 +0x400==0 时 vt+0x304 Update 0x115eb7c（取 PC+0x2c0 人物根组件坐标，+world 原点 +0x768）→ 策略（+0x30c 当前/+0x314 默认）vt+0x10；之后 NZStreamingRequestsManager(0x117b4bc) vt+0x38 Tick(管理器自己的 DeltaSeconds)。Default 策略 Update 0x11671bc 每帧轮一层 + 一个常驻层；TilesCollection::Update 0x117627c 用人物坐标对关卡项包围盒（+8/+0xc/+0x14/+0x18）和 LOD 半径决定加载。→ 人物 CustomTimeDilation 不在这条链上（推断）。
  - pak（/workspace/work/quanjunchuji-pak，只读解析 Main.umap/uexp 标签属性）：LayersFilesCache 12 层（AlwaysLoad×6、Grass×33、Landscape×16、Interior×9、MainBuilding×12、OutdoorBig×5、OutdoorSmall×21、Road×2、ServerOnly×5、ServerOnlyMajority×5、ServerOnlyGroundObjects×5、Tree×5）；LayersSetting 15 个；ServerLayersSettings = Landscape, AlwaysLoad, Ocean, ServerOnly, ServerOnlyMajority, ServerOnlyGroundObjects。
  - 根因判断（推断，待诊断确认）：+0x37c 服务器层缓存含 Landscape/AlwaysLoad → 第二次 Fill 让客户端 Landscape/AlwaysLoad 层同一块再 Tile 一遍：实例名完全相同，再 NewObject 同名关卡流对象（UE 同名同 Outer 原地重建已登记的那个对象）、AddLevel 再加一遍 → 块 0 的 Landscape 关卡流被搅坏，地形一个都没进来。客户端原生（Client Load Block 0x1086960）从来不填 +0x37c。
- 改：
  - Island.join：删 FillLayersWithCacheData(+0x37c)；顺序完全按原生：补进位置 → AddPlayerToSlot → LoadBlock(PC+bdc, PC+be0)。LoadBlock 前后记 +0x440/+0x43c。
  - 冻结：不再写人物时间缩放 +0x58；改 hold()：移动组件（人物+0x2f8）GravityScale(+0x120)=0、Velocity(+0xb4)=0，每秒补一次；脚下射线打到地形 / +a70 / 90 秒放开（还原重力，低于地面抬到地面上 1.2 米）。
  - 诊断 Island.stream（slot-*/train-* 都带）：管理器 +400/+409/+43c/+440/+2c8/块偏移/策略指针、world 原点、两份层缓存的名字×关卡数（+370 客户端、+37c 服务器——确认服务器层里是不是有 Landscape/AlwaysLoad）、每层：更新计数（+0x10，看策略 Update 在不在跑）/关卡数/包住人物的关卡数/包围盒范围、+0x358 关卡流对象数（重复指针数）/已加载/可见、Heightmap 关卡流对象逐个（载/显）、world StreamingLevels 数和已加载数。「坐标/地形块随时间」每 5 秒附一段简况。
  - 关键日志抓取加：FillLayersWithCacheData、TileLayer、Already Load Block、Out Of Index、Streaming in level、DirectSubmit、NZStreamingRequestsManager、Not Need、FirstLoad、StreamingLevelsManagerActor。
  - state 每步都写（修「训练岛：没进」）。
  - 仓库：game/pak 删（pak 资源在私有仓库 zhaotan005-dotcom/quanjunchuji-pak，本地 /workspace/work/quanjunchuji-pak，以后不往主仓库放）；releases 只留 v3.44 三个 APK；game/x（解包游戏 libUE4.so 等 118MB）、game/re/*.pkl、apk/、zframe/ZFrame-v1.*.apk、zframe/build/ 停止跟踪（本地文件保留，.gitignore）→ main 当前文件 < 50MB，jsDelivr 恢复。
- 要看的日志行：「分配位置：…LoadBlock 前 管理器 +440 当前块=-1 …之后 +440=0 +43c=9；不填服务器层」；「流式状态：…服务器层缓存 +37c …（有没有 Landscape/AlwaysLoad）」「层i … 更新计数 N 关卡 16 包住人物 k 范围 …」「TileLayer 关卡流对象 +358：… 重复指针 0，已加载 n」「关卡流对象：Heightmap_x?_y?_LevelInstance_0(载+显)…」；「坐标/地形块随时间」地形块涨不涨、【放开：脚下射线打到地形】；关键日志 OnLevelStreamingLayerLoadCompleted Landscape / Tick OnAllDuplicateLevelLoaded。
- 如果还不加载：看 ①层更新计数是否在涨（不涨 = 策略 Update 没跑，查 +0x400 首次加载标志/策略指针）；②包住人物=0（包围盒没套上人 → 查 TileLayer 偏移）；③包住>0 但流对象全未载（查 NZStreamingRequestsManager / DirectSubmit 日志）。

## v3.45（vc246/core246，2026-11-19 23:5x，只推内核，未实测）— 训练岛不加载的真根因：没调 FirstLoad（+0x400 bFirstUpdate 一直 1，Tick 不 Update）
- 23:15 用户 v3.44 实测：还是掉进虚空。诊断 notes/device-tests/v3.44/slot-1119-231553.txt、train-1119-231430.txt；对照 v3.43 的 notes/device-tests/v3.43/（ntfy 下载；v3.41 的 train-/block- 附件 ntfy 已过期，取不到）。
- v3.44 推断被推翻：+37c 服务器层缓存 0 个、+358 重复指针 0 → 不是重复填充。
- (1) 资源（证实，不是缺资源）：pak-files.tsv 里 TrainingIsland_Runtime 330 个文件全在同一个 pakchunk_c2afa52b…（Heightmap 48 个 = 16 块 × umap/uexp/ubulk，还有 AlwaysLoad/Grass/OcHmp/Runtime/Tree），挂载点 ../../../PUBGM/Content/Maps/TrainingIsland_Runtime/。手机诊断「资源清单」是按 pak 挂载点统计的：Maps/TrainingIsland_Runtime/ ×1 = 这个 pak 挂上了（Main.umap 也在这个 pak，能进图就说明它在）；别的大图分在多个 pak 里所以有 Heightmap 子目录挂载点。v3.41 块 0 地形能出来也说明资源在。
- (2) 层「更新计数」不变（证实为读错）：层对象 +0x10 是管理器弱指针（vt+0x8 0x1153a70 返回 +0x10），116169 = 管理器 ObjectIndex，所有层一样。层名在 +8；关卡在层的集合对象里：普通层 vt+0x10（0x1168b40）返回 +0x1c，vt+0x14 AddLevel（0x1155254）转给集合 vt+0x30 = 0x1178e00；Landscape 类层（vt 0x4caa624，vt+0x10=0x1155e00）集合在 +0x34/+0x2c/+0x24。集合 +0x14/+0x18 = 关卡项 TSharedPtr 数组（0x28 字节：[0] 关卡流对象弱指针，+8..+0x1c 包围盒，+0x20 字节）。v3.44 诊断把层当集合读 → 关卡数是垃圾。
- (3) 真根因（静态读码证实，设备未实测）：
  - 管理器 +0x400 = bFirstUpdate（构造 0x115b320 写 1；FirstLoad 日志串「ANZStreamingLevelsManagerActor::FirstLoad called, bFirstUpdate = %d」读的就是它）。
  - 管理器 Tick 0x115e7dc 非专用服分支：`ldrb [+0x400]; cmp 0; bne 跳过` → +0x400!=0 就不调 vt+0x304 Update（TileMode 0x1165e7c → 0x115eb7c），只跑 RequestsManager Tick。所以 Update/TilesCollection::Update 一次都没调 → 关卡流对象从没提交加载 → world StreamingLevels 0、+358 已加载 0。
  - 清 +0x400 的只有：FirstLoad 0x115f130 末尾 0x115f8f0 `strb 0,[+0x400]`（客户端；里面先按人物/视点做第一次 UpdateInternal、0x115f974、RequestsManager vt+0x18、可能 FlushLevelStreaming 0x2e55f3c）；0x11600a4（vt+0x2fc）只在专用服清。TileMode 的 FirstLoad 包装 0x1165e34：非专用服且 +0x440<0（还没块）直接返回，不清。
  - 原生 Client Load Block 实现 0x1086960(PC)：TActorIterator 找管理器 → PC+0xbe4=管理器 → bdc≥0 且 be0≥1 时 LoadBlock 0x1163f3c(管理器, PC+0xbdc, PC+0xbe0) → 日志「Client Load Block」→ PC vt+0x8bc（0xd64740，读 PC+0x54c / +0x2b4 链的一个对象）非空 → 管理器 vt+0x2f8 = FirstLoad → 再处理 world+0xc4 的某对象 +0x3bc（0x10bf700(x,0)）。
  - v3.42~v3.44 的 Island.join 只抄了 LoadBlock，没走 FirstLoad → +0x400 一直 1（v3.44 诊断 93 秒全是 1）。游戏日志里 LogNZLevelStreaming 同类别同级别（5）的 LoadBlock / FillLayers / TileLayer 行都打出来了，唯独没有「FirstLoad called」→ FirstLoad 从没进去过（v3.43、v3.44 四份诊断都 0 行）。
  - NetMode 不挡：0x2e65f00 是 GetNetMode（world+0x24 有 NetDriver→3…）；LoadBlock 同样判断 ==1(专用服) 退出，而 LoadBlock 实际跑了 → 单机不是 1。
  - 另一个调 FirstLoad 的是某 UUserWidget 子类（vtable 0x4ca9f60 的 0x115254c，Construct 时 +0x400 非 0 就调 FirstLoad 并 RequestsManager vt+0x1c(1)；0x11525cc 对应 (0)）。类名没查到。推断：v3.41 能加载是因为那时 Island 一进图就 LoadBlock（+0x440 早于这个界面变成 ≥0），这个界面触发的 FirstLoad 没被 TileMode 包装挡掉；v3.42 起要等 GameMode 建位置再加载，块号设得晚 → 那次 FirstLoad 在 +0x440<0 时被跳过，之后没人再调。v3.41 原始日志已过期，无法核对。
  - (3') 109 个流对象没进 World StreamingLevels：TileLayer 0x116543c 只 NewObject（0x1165bf4/0x1165d0c）+ 层 AddLevel（进集合）+ 追加 +0x358，不碰 world StreamingLevels；提交给 world 是 Update → 策略 → NZStreamingRequestsManager 的事，Update 没跑就一个都不会进。不需要另修。
  - TileLayer 额外发现：+0x424/+0x428 名单里的关卡不加 _LevelInstance 后缀、全块只建一次（+0x444 记已建）；+0x430 名单另有预载调用 0x11dca94。
- 改（Island.join）：AddPlayerToSlot 之后不再自己调 LoadBlock，改调原生 Client Load Block 0x1086960(PC)。之后读 +0x400：仍非 0 且 +0x440≥0（= PC vt+0x8bc 返回空、原生没调 FirstLoad）→ 记 vt+0x8bc 返回值，再直接调同一个原生 FirstLoad 0x1165e34(管理器)。不做假地面、不传送。
- 诊断：「分配位置」写 加载块前 +440/+43c/+400、Client Load Block 已跑、之后 +440/+43c/+400、PC+be4 是否=管理器、（必要时）vt+0x8bc 返回值和直接 FirstLoad 后 +400；「坐标/地形块随时间」每段 [+400=… 包住人物关卡 a/b 流对象 载/显/总]；「流式状态」每层 层名 + 集合偏移 + 关卡数 + 包住人物 + 范围，+400 带说明。
- 要看的日志行：分配位置「…原生 Client Load Block 0x1086960(PC) 已跑；之后 +440=0 +43c=9 +400=0 PC+be4=…（=管理器）（FirstLoad 已跑：+400 清 0，Tick 开始 Update）」；游戏日志「ANZStreamingLevelsManagerActor::FirstLoad called, bFirstUpdate = 1」「Client Load Block」「FirstLoad--ViewLocation」「Streaming in level」「OnLevelStreamingLayerLoadCompleted Landscape」「Tick OnAllDuplicateLevelLoaded」；「坐标/地形块随时间」地形块是否从 1 涨上去、流对象 载 是否 >0、【放开：脚下射线打到地形】；「流式状态」world StreamingLevels 是否 >0。
- 若 +400 已 0 但仍不加载：看各层「包住人物」是否 >0（包围盒偏移不对）→ 查 TilesCollection::Update 0x117627c 和策略 +0x30c/+0x314；若包住>0 但流对象 载 0 → 查 RequestsManager 0x117b4bc。

## v3.46（vc247/core247，2026-11-20 00:xx，只推内核，未实测）— 同帧原生摆人；标点按地面高度；训练营菜单精简；删动作放慢；删传送残留；抛物线光束诊断
- 23:55 用户交接（上一个智能体已查到原因、没改）。v3.45 实测（notes/device-tests/v3.45 六份：avatar/matchend/slot×2/train/para）：地形能出来（+400=0，FirstLoad 跑过），但进岛先在虚空停一下。
- (1) 虚空：SetupPlayerStartActor 23:30:34.477（帧 5830）建好出生点，AddPlayerToSlot 到 36.106（帧 5985）才跑（Java 轮询 500ms + 3 次 gcall）。改：cave 0x171e2c0..0x171e3f3（GM 面板 GiveItem 0x171e1c8 死函数里，PickAnim 用到 0x171e28b；game/re/islecave.py 生成，emu_islecave.py unicorn 过：调用顺序、阶段 5、寄存器/栈恢复）。
  - 入口 L：PR 补丁最后的 beq 0x10850a8（0x1084f38）→ beq L：Login 没找到位置时把 PC(r6) 记进缓冲区 +0、+16 计数，再回 0x10850a8。PR_N2=0080b0e15805961500005013e0641a0a。
  - 入口 S：Tick 0x1082e84 bl 0x1082868 → bl S（136d1aeb）：原函数后同一帧 FindSlot 0x10845b0 → 追加进位置玩家数组（照 Login，满了 0xb74980）→ AddPlayerToSlot 0x1083a98 → bdc≥0&be0≥1 → Client Load Block 0x1086960(PC) → 管理器 +400 仍 1 且 +440≥0 → FirstLoad 0x1165e34。看过就清 +0（不留旧 PC）；回大厅 Island.caveReset。
  - 缓冲区：+0 PC +4 S 调用次数 +8 阶段（1 找位置 2 追加 3 已摆人 4 CLB 已跑 5 FirstLoad 已补）+12 位置 +16 Login 记录次数。诊断行「同帧摆人：…」。
  - Island.join：cave 阶段≥3 且 PC+bdc≥0 → 只记诊断；否则原 Java 线程补做（原生步骤）。
- (2) 标点传送：MapMark 以前 Z=max(自己+3000, 20000)（绝对 200 米，训练岛地面 ≈-15800 → 离地约 358 米）。改 Trace.down(x,y,±5000 米) 打到 → 地面+2 米；没打到（目标没加载）→ 自己高度+30 米再 softLand。
- (3) 训练营菜单：Con.training 时只显示「功能」页（id 8），只有加速/高跳/标点传送（诊断按钮不在训练营显示）。
- (4) 删「动作放慢」：Mods.slowMo、骨骼网格 +0x7c4 播放速率写入、菜单卡片全删，用游戏默认。
- (5) 传送残留：Island 删退回传送、抬到地面、hold()（停重力）；follow 只记录（地形出来 40 秒后停）。
- (6) 手雷抛物线：v3.45 para 诊断所有画线前检查都过（+21=1、BP_ProjectileTrajectory_C、34c/350=1），卡在光束。画线 0xee9fe4 在抛物线物体上：段数 round(+2cc/+2d0)<1 不画；每段 0xeea4f0 用模板 +2d4 建组件存 +2d8/+2dc，SetVisibility 0x292041c、SetHiddenInGame(0) 0x2b6f01c、vt+0x420 SetStartAndEnd、vt+0x42c；终点 +2e4。Parabola.trajDiag 读这些 + 每段组件里 StaticMesh/材质/粒子指针 + 原始字节；Parabola.matchStart 每局拿雷 1.5 秒后自动跑一次传 para-（单机/服务端/客户端都跑，联机对比）。
- 还没查：训练岛不刷枪（游戏日志 UPracticePlayerComponent::InitializeRecordInfo ds not prepared!，utf32 串 0x43769e8，xr.pkl 没扫到引用；地上物资 0）；实战区不自动进流程、没有人机。

## v3.47（vc248/core248，未实测，2026-11-20 00:45）
- 训练营菜单：训练营里隐藏左侧分页列和分隔线，内容区高度改为按内容（不再固定高），去掉 3 项菜单下面的大片空白（GameOverlay）。
- 训练岛不刷枪：TileMode 流式管理器单机走客户端路径，服务器层（ServerOnly/ServerOnlyMajority/ServerOnlyGroundObjects）不进 CurrentLayers(+0x2f4)，FillLayersWithCacheData 0x11649bc 不建这些层 → 刷枪点/物资不存在（v3.45：地上物资 0）。钩子 BeginPlay 汇合点 0x115c744（d4089fe5 → 2a0717ea）→ cave A 0x171e3f4..4af / B 0x171e568..5fb（game/re/tlcave.py，emu_tlcave.py 四种情况过）：仅 vptr==TileMode 虚表 0x4cada54 时，按专用服规则（跳过静态名 0x5173280 的 N0、N18 且 +0x2c9==0、已有同指针/同名）把 +0x2e8 服务器层追加进 CurrentLayers，引用 +1，满了 0xbc5580 扩容。诊断行「服务器层补丁（训练岛刷枪点）」。
- 训练岛小地图（推断）：岛复制成 3x3，贴图只对中间原岛；FindSlot 0x10845b0 从位置 0 找空位 → 单机进西南块。0x108464c 0010a0e3142080e2 → 0410a0e3e82080e2，从位置 4（中间）开始找。
- 单机刷车：游戏碰撞处理 3 被挡就不生成（只打 spawn vehicle failed），以前总提示「已刷」；落点不贴地且和人机/传送共用缓冲。改：射线贴地 +2 米、写落点+调用一次加锁、扫油箱组件确认多了一辆，否则换位置（最多 4 个），都不行提示「没刷出来」。
- 皮卡拆成 皮卡(31)/大脚车(32)；第一次刷读车表路径（MonsterTruck/PickUpTruck），反了自动对调。
- 刷出的车满油：UVehicleFuelComponent（vt 0x4beb188，Max +0xa8 int，Current +0xb4 float），游戏线程调 vt+0x1e8 AddFuel 0xe1e6b8 加满。
- 海岛服务端：加入玩家落地后投真空投箱；配置 0x1186a74 的箱子类 +0x32c/+0x330/+0x334/+0x338 为空时用 StaticLoadClass 0x1bf2c5c 加载 /Game/Blueprints/AirSupply/BP_NZBRAirSupplyBox… 填上；去掉地上假箱子堆；车照排、满油。HostMatch：Box.isle() 时 Box.tick()。
- 雷：按用户说先不动。

## 2026-11-20 00:53 用户（v3.47 实测）：训练岛还是没有枪；大厅换发色不生效
- v3.47 诊断（notes/device-tests/v3.47/slot-1120-005137）：服务器层补丁 ok、位置 4 ok（人物在原岛出生点 53922,-48811）；当前层 13 = 原 11 + ServerOnlyMajority(关卡 0) + ServerOnlyGroundObjects(关卡 5)；地上物资 1；ServerOnly 本层没进（它在 +0x2d4，名字等于静态名 N0，专用服是 0x115c708 单独加的，+0x2e8 循环里被跳过）。
- 发色：游戏 13003 日志 ReqStoreEquipProp ... hairColor = 4，回 13004 后 InitAvatarData HairColor 0 —— 我们回的角色属性 PropRoleAttr 只有 sex（hair_color 是字段 5）。

## v3.48（vc249/core249，未实测）
- tlcave：先追加 ServerOnly 本层（+0x2d4/+0x2d8，r6=-1 不过名字过滤），再按原规则加 +0x2e8 里的；去掉 +0 计数（+8>0 即跑过），A 缩到 0x171e3f4..0x171e4ab。emu_tlcave.py 五种情况过。
- Server：角色按性别记发色（存档第 5 列），回包 PropRoleAttr 带 hair_color(5)；发色先从 13003 请求 Prop.role_attr(13).hair_color(5) 取，取不到读日志「hairColor = N」后推 13002。

## 2026-11-20 01:43 用户（v3.48 实测）：训练岛还是没枪（截图：枪桌全空，小地图已正常显示训练场）；发色还是变不了
- 根因（训练岛枪）：pak 文件表里 TrainingIsland_Runtime 只有 AlwaysLoad/Grass/Heightmap/OcHmp/OcPolygons/Runtime/Tree，全包没有任何 ServerOnly 文件 → 客户端包剥掉了服务器关卡，层补丁加进去也是空的。v3.47/48 的 tlcave 保留（无害）。
- 根因（发色）：mark 日志 13003 原包 0a0c0880ccdeff251080ccdeff25 320d08d396d7c67010c1086a0230043a00 → 发色在 hair_prop(6).role_attr(13).color(6)=4，不在角色 hair_color(5)。

## v3.49（vc250/core250，未实测）
- Island.tableGuns()：rangeDiag 后，人物周围 36×36 m、1.2 m 格向下射线（起点人物中心+1 m，低于棚顶）；比 3 m 内最低点高 55–115 cm = 桌面；1.5 m 连桌；每桌一把枪（Bots.GUN 轮流）+ 3 份子弹（Veh.dropAt）；不足 3 张桌子则人物前方 4 m 起地上 2 排。诊断「训练桌放枪」行（含桌面命中的 actor/cls）。
- Server：发型道具记颜色（props 第 5 列），prop() 对非角色带 role_attr{color(6)}；请求里 field5/6 都认；日志路径也写到穿着的发型（slot 213）。

## 2026-11-20 01:53 用户（v3.49 实测）：还是没枪；发色好了；要服务端也能进训练营
- v3.49 train-1120-015133（50 秒导出）：ServerOnly 本层进来后展开 ServerOnly/Door、Glass、Pickups（关卡 1，包住人物）、GrassMap —— 更正 v3.49 的说法：服务器关卡是有的（pak-files.tsv 只是没列全）。但地上仍没枪；训练桌放枪那时「没跑」（排在跟踪 120 秒之后）。

## v3.50（vc251/core251，未实测）
- Island：join 之后另起线程 15 秒检查原生 150 m 内物资，<5 才 tableGuns()（不再等跟踪 120 秒）。
- GameOverlay：服务端大厅卡片第一行「进训练营」= Lan.host(MAP_TRAINING)（listen，训练岛默认模式，和单机同）；去掉旧「训练营/训练场」两个按钮。

## 2026-11-20 02:05 用户（v3.50）：服务端开训练岛，加入的人上不了岸（怀疑不在同一个训练场副本）；加速时动作变快，不放慢了
- FindSlot 0x10845b0：先找已在的位置，再从第一个空位置（v3.47 起从 4）找，满了随机 → 第二个人进位置 5（别的副本）。

## v3.51（vc252/core252，未实测）
- Island SAME4：0x1084654 ldr r3,[r2] (003092e5) → b 0x10846c0 (190000ea)：空位置循环直接返回 r1=4，所有人进位置 4（中间原岛）。和 PS4 一起装。
- Mods.dilation：加回骨骼网格 +0x7c4 播放速率 = 0.75/倍数（v2.74 行为，固定 0.75，不加菜单）。

## 2026-11-20 02:21 用户（v3.51 实测）：还是不刷装备；人物掉血。建议对照 grok 解密的 pak。
- v3.51 服务端 slot/train（notes/device-tests/v3.51）：所有人同一个位置 4 ok；ServerOnly/Pickups 层包住人物；原生 150 米内物资 0；训练桌放枪 4 把，但 74 个「桌面」命中全是 LandscapeStreamingProxy（地形）→ 枪在地上；射击训练区离人 60 米，全图靶子 0 → 训练设施（桌子/靶子/刷枪点）整套没生成。
- 掉血：日志没有伤害来源（只有 02:14:05 车炸 InVehicleExplosion）。
- 已问用户：发 grok 解开的 TrainingIsland 地图文件夹（ServerOnly、LogicLevel umap/uasset）；掉血是哪端、岸上还是水里。game/pak 已在 v3.44 删掉。

## 2026-11-20 02:55 pak 对照（不用再找 grok：仓库历史 9a2d5ed/1db4a46 里就有 game/pak，v3.44 从工作区删了，用 `git archive 1db4a46 game/pak | tar -x -C /tmp/pak` 取）
- 训练岛刷枪/靶子都在 AlwaysLoad/LogicLevel.umap（主图 BR-TrainingIsland_Mobile_Main 引用 AlwaysLoad/Light、LogicLevel、Ocean、ProceduralFoliageVolumes、block、cars）：
  - 刷枪 = 2 个 BP_PickUpRefreshVolumn（导出 138/139，父类 C++ ANZBRPickUpRefreshTriggerVolume，靠 OnActorBeginOverlap 人走进区域才刷；配 GameState 里的 BP_PickUpRefreshInfoState：PickUpRefreshTime/PickUpDestroyTime；系统 APickUpRefreshSystem，SpawnOnePickUp/DestroyOnePickUp）。
  - 靶子 Bp_First..FifthRound×、BP_FlyTarget/MobileTarget/MoveableTarget、BP_ShootPracticeBase、BP_VehiclePracticeBase、BP_Practice_ActualCombat、shootreturn/drivereturn/AIreturn、VehicleSpawnPoint1..12。
- 设备诊断「刷物资区 0、全图靶子 0」→ LogicLevel 的 actor 在游戏里不存在或没被我们的扫描认出；TileMode 层列表 0..17 里没有 AlwaysLoad 层。下一步：诊断查 LogicLevel 有没有加载（world StreamingLevels 0 个），查 ANZBRPickUpRefreshTriggerVolume 实例数。
- libUE4 字符串：PickUpRefreshVolumnDisable (0x47d2d54，某类的属性名，待查归属)；APickUpRefreshSystem::FetchPickUpRefreshInfoState 报错串 utf32 0x43b2170（设备日志没出现）。

## v3.52（core253，未实测，2026-11-20 03:10）训练岛常驻层 AlwaysLoad（LogicLevel 刷枪/靶子）
- 根因（v3.51 服务端 slot 诊断）：客户端层缓存 +370 有 AlwaysLoad×6（Light/LogicLevel/Ocean/ProceduralFoliageVolumes/block/cars），但当前层 18 个没有 AlwaysLoad；
  显示层 +0x2dc（工厂 vt+0x18 过滤）和服务器层 +0x2e8（工厂 vt+0x1c 过滤，0x11577a4）都没建它 → LogicLevel 里的 BP_PickUpRefreshVolumn×2、五轮靶子、训练台从没加载（刷物资区 0、全图靶子 0）。
- 层工厂注册表 0x5173800（0x1156e20(&FName) 查/建，注册在 0x1157990..0x1158c00，AlwaysLoad 前缀 'AlwaysLoad/'，FName 静态 0x5173208）。
- Island.alwaysLoad（只服务端/单机，join 后）：工厂 vt+0x10(out, 工厂) 建层 → CurrentLayers(+0x2f4) 临时只放这一层 → FillLayersWithCacheData 0x11649bc(管理器, +0x370, PC+bdc) → 换回原数组并把新层追加到尾部。诊断行「常驻层 AlwaysLoad（LogicLevel 刷枪/靶子）：…」。
- 训练桌自摆枪保留（15 秒后原生 150 米内物资 <5 才摆）。

## v3.53（core254，未实测，2026-11-20 04:5x）训练岛物资链：服务端物资点放开 + 诊断
- v3.52 实测（notes/device-tests/v3.52，单机 03:17 + 服务端 03:49）：常驻层生效——层18 AlwaysLoad 关卡 5，流对象 124→129、已载 65→69；
  NZSceneItem_RangeTarget 日志 128 行（v3.51 没有）= 靶子出来了；原生刷车 BP_PickupTruck_B_01 在 57625,-38410（LogicLevel 刷车点）。枪还是没有。
- RE：BP_PickUpRefreshVolumn（ANZBRPickUpRefreshTriggerVolume，vt 0x4c90614，ctor 0x10818a8）OnOverlapBegin 0x1081a90 只把 PC+0x5fd=1，OnOverlapEnd 0x1081b80 清 0 → 刷物资区只是「人在区里」标记，不生成枪。
  枪应来自 ServerOnly/Pickups 的物资点组件 UNZBRPickupConfigComponent::BeginPlay 0x10c0b5c：要 (world+0x6d8)|2==3 且 NetMode∈{0,1}，GM 是 ANZBattleRoyaleGameMode → GM+0x90c PickupManager → 0xff1e88（[Pickup] SPAWN 日志）。
  服务端 NetMode 2 被挡 → Island.install 装 Loot.PK（0x10c0ba4 三条，只排除 3）。单机 NetMode 0 本来就过，单机也没枪 → 另有原因，看诊断。
- APickUpRefreshSystem ctor 0x119cd4c；系统附近 0x119d884 有「GridMap is not exist, add pickup gid=%d to GameMode->CachedS…」。
- 诊断「物资链（进岛 15 秒）」：物资点组件个数/有 Owner、刷物资信息、刷物资区、地上物资对象、GM 是否 BR GameMode、PickupManager(+90c)、AlwaysLoad/ServerOnly/Pickups 层每层关卡已载/可见；
  关键日志加 [Pickup]/PickUpRefresh/CachedPickupSPList/GridMap；训练桌放枪线程开头写状态（v3.52 两次都显示「没跑」，原因待看）。
- 注：单机包其实也会把诊断发 ntfy（v3.52 03:17 客户端 train 就是单机）。

## 2026-11-20 10:1x 补发 v3.53 内核（core254）
- 发现：4b23614 v3.53 提交没更新 core/（b*.ver 仍 253、notice.txt 停在 v3.46）→ 用户手机实际一直是 v3.52（ntfy 09:15 诊断标题 v3.52，没有「物资链」行）。
- 用户 09:55：更新到最新、训练场还是不刷枪，日志已传（train-1120-091545 服务端 / 091614 客户端 / slot-091835）。
- 09:15 服务端诊断：训练岛 GridMap「CachedPickupSPList.Num()=0」「Add 229 SceneItems」、无 [Pickup] SPAWN → 物资点组件没把刷点交给 GM（服务端 NetMode 2 被 0x10c0ba4 挡，v3.53 的 Loot.PK 正是放开这个）；「训练桌放枪：没跑」原因待 v3.53 诊断看。
- 本次：bash build_all.sh 3.53 3.53 254 254，core/b0-2 = 254，notice.txt 换成 v3.53 内容。

## v3.54（core255，未实测，2026-11-20 10:2x）
- 用户规则（09:59）：训练场的枪必须游戏自己刷，位置完全由游戏决定，不许我们摆（tableGuns 删调用，只统计原生 150 米内物资写诊断）。服务端物资点 NetMode 门放开（Loot.PK 0x10c0ba4）保留——那是让游戏自己的刷点逻辑跑。
- 用户 09:59：联机服务端开、客户端开车开着开着被拉回。线索（v3.52 服务端日志）：NZVehicleSyncPhysicStateComponent.cpp:90「Vehicle=%s GetRigidBodyState failed」刷屏（Van/Uaz/Mirado/Buggy 各上百行）。打印点在 0xdf43cc，函数 0xdf3e08（开头 GetNetMode 0x28a4250 ≤2 才跑 = 服务端侧；[this+0xf4] 车，vt+0x2c 取根组件 → GetRigidBodyState 0x2c74c1c 失败）；同族串：ServerAdjustRigidBodyStateByClient 0xdbe500、OnRep_SyncToDSLocation 0xe01c38、TickCorrectVehicleLocation 0xe1c814（客户端纠正位置）。还没定根因，下一步查 0x2c74c1c 返回 0 的条件（物理体没建/没模拟？）和客户端纠正阈值。
- 环境：libUE4.so 从 git show 585c115^:game/x/lib/armeabi-v7a/libUE4.so 恢复；ue.py 的 xr.pkl/calls.pkl 改成可缺省。

## v3.55（core256，未实测，2026-11-20 12:4x）训练岛刷枪点关卡 ServerOnly/Pickups 按视野加载
- 用户 12:33：单机还是不刷枪。诊断 train-1120-122904（v3.54 单机，notes/device-tests/v3.54）：
  「[Pickup] SPAWN Started, Stage=2, Total=0」→ PickupManager 自己的刷物资流程跑了，但没有物资点；物资链：物资点组件 0、层 ServerOnly/Pickups 关卡 1 已载 0（包住人物）、AlwaysLoad 5/5 已载。
- RE：客户端策略 Update 0x11671bc 对每个当前层调 layer vt+0x18（按视野加载）。普通层 vt+0x18 = 0x11555f0（转 [this+0x1c] 集合 vt+0x14 = 0x1175304）；
  ServerOnly 层类 vt 0x4caee2c（ServerOnly、/Door、/Glass、/Pickups、/GrassMap，ctor 0x117b8ec，集合 0x4cb1bf0 派生自 TilesCollection 0x4caebac，vt+0x14 同为 0x1175304）的 vt+0x18 = 0xb63a30（bx lr）→ tlcave 把层加进来也永远不加载。
  ServerOnlyMajority 0x4cb1c58 / GroundObjects 0x4cb1ca4 同样是空，这版没动。
- PickupManager 注册 0xff1e88：+0x30c（已开刷）为真时打「[Pickup] SPAWN Spawn has start, Direct spawn, Comp=%s」并直接 0x10c0fbc 刷 → 关卡后加载也会刷。
- 改动：Island.soLoad 把 vt 0x4caee2c+0x18 从 base+0xb63a30 改成 base+0x11555f0（install 早装 + matchLoaded 再补一次，防重定位前写）；诊断「物资链」行开头写结果。大地图客户端路径没有 ServerOnly 层在当前层，不受影响（推断）。
- 要看：物资链「ServerOnly/Pickups 已载 1」、关键日志里「Direct spawn」、「训练桌（只看不摆）原生 150 米内物资」>0。附带可能出门/玻璃（ServerOnly/Door、Glass 同类）。
- 另：12:27:25 有一次闪退（libUE4 0x19bb448 无限递归栈），是 12:27:32 这次启动之前的那次运行，原因没查。
- v3.56 (core257, 未实测)：v3.55 实测（train-1120-124346）：虚表补丁 ok，但 ServerOnly/Pickups 仍「已载 0」，SPAWN Total=0；玩一会闪退（崩溃栈还没传上来）。原因推测：集合 0x1175304 拿不到距离表（[r2+4]==0）直接返回。v3.56 撤掉虚表补丁；进岛 15 秒后对 ServerOnly/Pickups 层各关卡项的流对象置 +0xa0 bit5(bShouldBeLoaded)|bit6(bShouldBeVisible)（0x11529dc 读写同两位，RequestsManager 0x117b4bc vt+8 发请求），6 秒后再置一次，诊断「Pickups 关卡：」+ 物资链。用户另说加载页少了 TiMi 那页，未查。
- v3.57 (core258, 未实测)：用户说开机一直没有 TiMi 页（LogoUI，含 timistart 帧和 Timi_Logo_SoundCue，资源在 pakchunk_base）。UNZStartupFlowTimi（NZStartupFlowTimi.cpp，0x1211a24 一段是 Get Device Level）。已存日志里从没出现过 "Get Device Level" → Timi 步骤可能没进流程。StartupFlowTypes 反射在 0x4d8b0f4，偏移 +0x33c（推测在启动 GameMode 上）。v3.57 只加诊断：UpdPage.flowTypes 列出启动 GameMode +0x33c 的步骤类和 NZStartupFlow*/LogoUI 对象；Con 收 [FlowManager]/NZStartupFlow/LogoUI 日志行到 UpdPage.flows。
- v3.57 实测（train-1120-130015）：StartupFlowTypes = Timi/Update/LoadLogin（C++ 构造 0x121af90 推三个 StaticClass 0x16807b8/0x16808f8/0x1680518）。Timi 步骤 Begin 0x1211940 只做设备档位，0.15 秒结束。放 LogoUI 的是另一个类（vt 0x4cf1600，Begin 0x121166c CreateWidget LogoUI_C→AddToViewport，静态已显示标志 0x517a5a1，构造 0x1211628 载 LogoUI_C 到 +0x28），不在列表里 → TiMi 页是这包代码里拿掉的。Pickups：v3.56 置位后 6 秒内游戏清掉，仍 0 加载。
- v3.58 (core259, 未实测)：GfxOpt.java 画面细项（阴影/抗锯齿/泛光后期/特效/草植被/视距/贴图/清晰度，控制台命令即时发，prefs gfx_*，每次 LogLoad: Took 后 2.5 秒重发；Gfx 档位切换后也重发）。入口：大厅单机页、对局功能页末尾、画面页。

## v3.59（core260，未实测）
- TiMi 开机页：启动 GameMode 构造 0x121b0c0 原来推 Timi 流程（只做设备分级，0.15 秒结束），改推 Logo 流程（LogoUI_C，timistart 帧 + Timi_Logo_SoundCue，3.6 秒自动结束）；LogoUI 结束 0x1211914 改跳 0x1211a24 让 Timi 的 End（设备分级）照跑。我们的更新黑底层等日志 "End Flow NZStartupFlowLogo"（最多 7 秒）再盖，免得挡住 TiMi。
- 训练岛刷枪：v3.56 置 +0xa0 位无效原因——这几位是请求状态，实际加载靠 RequestsManager；0x11529dc 见位已相等就不发请求。改成 Server.gcall 游戏线程调 0x1174180(集合, 1, 可见1)（集合内逐关卡 0x11742c4 加载=1、不阻塞），进岛后每 0.5 秒找 ServerOnly/Pickups 层，一出现就调（PickupManager 约在摆人后 2.4 秒开始 SPAWN，可能赶不上——看诊断「Pickups 关卡（早）」和 SPAWN Total）。
- 仓库：allSkins 加 PARA（pak 里 Items/Parachute/<编号> 有资源的 56 把伞）。
- 移动靶：原生 NZSceneItem_RangeTarget 里没有移动逻辑，在 BP_MobileTarget/BP_MoveableTarget/BP_FlyTarget（蓝图）里，pak 解包里没有这几个文件，待用户发。

## v3.60（core261，未实测）
- 移动靶：用户发来 RangeTarget 蓝图（/workspace/work/pakx5），蓝图里只有参数（Moveable：MovingHorizontalVelocity +0x334、HorizontalOffset +0x32c），移动在原生 ANZSceneItem_RangeTarget::Tick 0xcf544c：Role(+0xe8)>2 即 Authority 直接 pophi 返回，移动只在客户端本地算。单机/房主是 Authority → 不动。补丁 0xcf5464 pophi(108cbd88)→nop，Island.install 早装（Island.rtState）。站起/倒下动画 0xcf549c 里另有三处 Role 判断没改。
- v3.60 实测（13:28，notes/device-tests/v3.60）：移动靶会动了 ✅（用户确认）。TiMi：Logo 流程 4.8 秒（13:26:54.6→59.4），用户觉得一闪而过——是 Logo 流程自身时长，没加速。更新页：StepApp 开始后马上收到 lb#OnResUpdateResult 107「更新中(100)」，没走我们的内核下载进度。训练岛：Pickups 集合关卡 1 个，流对象 +0xa0=0x6d（加载/可见位已置），+0x110 关卡仍空，SPAWN Total=0，也没车。
- 根因更正（训练岛枪/车/门）：pakx/pak-files.tsv 全 321 个 pak、83436 个文件，**没有任何 ServerOnly 文件**（TrainingIsland_Runtime 只有 AlwaysLoad/Grass/Heightmap/OcHmp/OcPolygons/Runtime/Tree + Main）。v3.49 那条「更正」是错的：层元数据里有 ServerOnly/Pickups，但关卡包本身被客户端包剥掉了，请求加载也加载不出来。所以「游戏自己刷」在这个客户端里没有数据可刷（刷枪点、车点都在 ServerOnly）。v3.55/56/59 的加载办法都不可能成功。等用户决定：有没有服务端文件，或放宽「不许自己摆」。
- 14:00 用户问能否用自制服务端跑：不行（同一套客户端文件，没有 ServerOnly 关卡）。客户端仍有：AlwaysLoad/LogicLevel 里 BP_PickUpRefreshVolumn×2（=诊断「刷物资区 2」，有 BoxExtent）、BP_ShootPracticeBase、BP_VehiclePracticeBase、靶子；AlwaysLoad/cars.umap 只有一个 BoxComponent（推测车区）。已问用户是否允许用游戏物品表/刷车函数在这些区域内刷（位置与原版略有偏差）。TiMi：用户说可见不到 3 秒，下版让 LogoUI 播完整（Logo 流程 vt 段 0x4cf1720：Begin 0x121166c、End 0x12118c8、ctor 0x1211628）。

## v3.61（core262，全部未实测，2026-11-20 14:xx）
- 工具：game/re/uasset.py（UE4.18 无版本号 cooked 包只读解析：名字/导入/导出表 + tagged 属性），game/re/logo_cave.py + emu_logo_cave.py（unicorn 三条路径过）。xr.pkl/calls.pkl 用 xref.py/bl.py 重新生成（不进 git）。
- 训练岛车（根因 + 改）：
  - pak 只读解析 LogicLevel：BP_VehiclePracticeBase_2（C++ AVehiclePracticeBase）VehicleSpawnInfoArray 12 项 {VehicleResId 32/19/12/29/24/23/10/25/26/20/27/27, SpawnPointActor VehicleSpawnPoint1..12}，ReturnPoint=drivereturn。
  - 偏移：+0x2f4 PracticeSlotIndex、+0x2f8/+0x2fc 起止区域、+0x300 VehicleSpawnInfoArray（元素 8 字节）、+0x30c AllRangeTargetArray、+0x324 RankArray、+0x33c/+0x340「有人的位置」int 数组、+0x348 已刷标志。
  - Tick 0x10387ec（虚表 0x4c8a538+0x204）：+0x348==0 且数组含 +0x2f4、GM IsA 训练场（0x11e68d4）、GM+0x524(GridMap)+0x48≠0、GM+0xa70≠0 → 0x1038a34 SpawnAllVehicle（日志「bHasLandscapeLevelLoadDone = true, Start to SpawnAllVehicle!」verbose 不打），用 GM+0x914 车表 0x1005400 按 ResId 取车类、刷在刷车点 Actor 上。没有 NetMode/Role 判断。
  - 数组只由 OnPlayerAddToSlot（原生表 0x4cf0654，thunk 0x1201b10 → 实现 0x1037604(this, 参数*)，读 [参数+0x10]，-1 跳过，不重复追加）填；BeginPlay 0x1036fdc 把它绑到 0xc73834(this)+0x284，PracticeSlotIndex 在 BeginPlay 末尾 = GM 0x1086180(坐标)。
  - 广播只在 AddPlayerToSlot 0x1083a98 末尾（0x10843f4..：参数 {0,0,0, PC, PC+0xbdc} → 0xbe7680）。Login 0x1084c5c：Uin(PS+0x3b0/0x3b4)=0 → 「NZPlayerState error」不进位置（单机就是这条，靠 Island.join 自己调 AddPlayerToSlot）。
  - 证据：v3.51 服务端日志——房主自己摆进位置时没刷，客户端 Login「Add player to slot 2 4」0.3 秒后游戏刷出 12 辆（Loc 57625,-38410 等）= 这 12 个刷车点；v3.60 单机 slot 诊断 PC+bdc=4、+a70=1、AlwaysLoad 关卡 5/5 已载，车 0。
  - 根因：单机 join（AddPlayerToSlot）比 LogicLevel 加载（Island.alwaysLoad）早，广播时驾驶训练台还不存在，之后再也没广播。
  - 改：Island.vehPractice（join+alwaysLoad 之后另起线程，60 秒内每秒）：找 VehiclePracticeBase 实例，位置号 == PC+0xbdc 且数组里没有时，Server.gcall 调游戏自己的 0x1037604(训练台, PC+0xbcc)（[+0x10]=PC+0xbdc）。不挑位置、不用 Veh.dropAt。诊断行「训练岛刷车（游戏自己的驾驶训练台）」：位置号、刷车点数、有人位置、已刷/未刷、GM+a70、GridMap+48、车表+914。服务端（房主）同样跑（幂等），客户端不跑。
- 训练岛枪（结论：做不到，不造假）：
  - 刷枪点 = ServerOnly/Pickups 关卡里的 UNZBRPickupConfigComponent；pak 文件表（pakx4/pak-files.tsv，321 pak/83436 文件）训练岛没有任何 ServerOnly 文件。
  - LogicLevel 只有 2 个 BP_PickUpRefreshVolumn（只有盒子，无点表）；BP_ShootPracticeBase 只有 AllRangeTargetMap/TotalLimitTime/ReturnPoint；BP_PickUpRefreshVolumn 蓝图没有图表；BP_NZBRPracticeGameMode 只引用 BP_PickUpRefreshInfoState（PickUpRefreshTime/PickUpDestroyTime，本地没有这个文件，按属性名只有时间）。
  - APickUpRefreshSystem（ctor 0x119cd4c，虚表 0x4cb06c8；BeginPlay 0x119cd84 绑 OnPlayerPickUp/OnPlayerDiscard）：OnPlayerPickUp 0x119cf9c 只在 PC+0x5fd（人在刷物资区）时把 {时间, Uin, 物品, 位置, 朝向} 记进 +0x2c8，Tick 0x119d478 过了 +0x2c0 秒用 0x119d884(SpawnOnePickUp 实现) 原地重刷；+0x2d4 是丢弃物定时删。SpawnOnePickUp(物品, 位置, 朝向) 位置由调用者给。→ 没有初始刷点数据，游戏自己刷不出第一批枪。
  - 改：去掉 v3.55~v3.60 那些不可能成功的「加载 Pickups 关卡」请求（soForce 不再调），只留物资链诊断。
- TiMi 页：
  - UNZStartupFlowLogo 虚表 0x4cf2600：+0x120 Begin 0x121166c、+0x124 End 0x12118c8（RemoveFromViewport → v3.59 改跳 0x1211a24）、+0x130 超时 0x1211368 = 3.6f（Timi 类 0x4cf24b8+0x130 也指它）、+0x134 Tick = 基类 0x120f460（+0x20 累加 dt > 超时 → End）、+0x13c 0x1211628 = 载 LogoUI_C 类到 +0x28（不是构造）。结束条件只有超时。
  - LogoUI.uasset（pakx/pakchunk_base）：LogoAnim PlaybackRange 0~4.0015 秒，音效段 1.1~4.0 秒 → 3.6 秒本来就截掉尾巴；开机 Begin 在第 0 帧、窗口要到 GEngineLoop.Init 之后才起来，第一帧 Slate dt 大，动画开头被跳过（推断）。
  - 改：cave 0x171e690..0x171e6df（GM 面板废函数空地，商店 cave 0x630..0x68f 和 Lan NQ 0x700 之间，装前比对原字节）：虚表 +0x120 → bx lr；+0x134 → 控件(+0x24)没建时只累计 dt，满 0.5 秒清 +0x20 再尾调原 Begin，建好后跳基类 Tick；+0x130 → 4.5f。UpdFlow.logoFull（installTimi 里装，虚表写之前核对原值，写失败还原）。
  - GameOverlay.startupUpdate：等「End Flow NZStartupFlowLogo」最多 15 秒（原 7 秒）；游戏更新页面板已显示（UpdPage.panelOk）就不再盖黑底层。
- 更新页：
  - v3.60 日志：StepApp 开始 → GCloud 开 apk 里 nzres.png IFS 失败 → [lb#OnResUpdateResult] 107「更新中(100)」。
  - UNZStartupFlowUpdateStep::OnResUpdateResult 0x1212a9c：错误码+0x1b39 ∈0..5（-6969..-6964 成功/跳过/取消/SkipStep）走跳转表；其余进默认 0x1212b6c：GI(world+0xc4)+0x3b8 弹错误框 0x10b5d9c、父流程(+0x1c→+0x18 Update 流程→+0x34 UpdateUI) vt+0x25c(信息) 切出错显示，步骤不 End（之后进大厅是我们 Con.tickAudio 在 NetCore 完成+UpdPage 结束后 open 大厅）。
  - 改：0x1212b6c 74079fe5 → b10100ea（b 0x1213238 函数出口），错误结果忽略；UpdFlow.install 里和 GATE 一起装。进度仍是 UpdPage 每 0.3 秒 vt+0x240 OnUpdateProcess。
- 其它（v3.60 日志）：PracticePlayerComponent「InitializeRecordInfo ds not prepared!」每帧一行（0x10313e8，GM+0x4f8 空）→ 0x103152c 24059fe5 → 680000ea（b 0x10316d4 同函数出口，只去掉日志）。Island.install 里装。
- 没动的：毒圈底层 cave（SLACK+0x990）一直「空位被占」（那段是数据段副本，v2.85 就知道），用户没提，这版不动；LogRHI EPixelFormat 28 引擎层不动。
- 构建：bash build_all.sh 3.60 3.61 261 262，core/b0-2.ver = 262，notice.txt 加 v3.61 段。APK 在 releases/（不进 git）。
- v3.61 实测（14:59，notes/device-tests/v3.61）：训练岛刷车 ✅ 游戏自己的驾驶训练台刷出 12 辆（含 2 艘快艇、1 个摩托艇）。TiMi「放完整动画 ok」，Logo 流程 4.6 秒。更新页仍收到 107（已忽略报错分支）。枪：仍没有（客户端无原版刷点数据，用户确认）。15:07 用户要求把最新版推到仓库并交付：releases/ 加入 v3.61 三个 APK（单机/客户端/服务端）。

## v3.62（core263，全部未实测，2026-11-20 23:3x）训练岛防摔 + 枪桌摆枪
- 用户：训练营会被摔死；另外（23:24 明确放开 09:59「不许自己摆」）射击区枪桌要像原版训练营一样有枪和子弹、捡走会补。
- 日志证据：v3.60/v3.61 三份诊断（train-1120-132833、slot-1120-132934、train-1120-145938）没有任何死亡/伤害行，人物 Z 一直 -15734..-15768（地面 -15860），这几局没摔。游戏的摔伤日志「this=%s, GetVelocity()=%s, FallDamage=%f」（0x42e44ec）只在 verbose 级别打（0xd1409c `cmp #5`），所以就算摔死诊断里也看不到。根因靠静态读码定的。
- 摔伤路径（capstone）：ANZCharacter 虚表 0x4bddb48 +0x3e4 Landed = 0xd14030：|Vz| → vt+0x7e8；BR 人物虚表（0x4c9c4dc/0x4c9e068/0x4ca2828/0x4ca49d0/0x4ce23ac）+0x3e4 = 0x10d3d04（先调 0xd14030），+0x7e8 = 0x10e8f00（车上等返回 0，否则尾跳 0xd149a4）。
  0xd149a4：+0xa78 FallVelocityThreshold_High ≤ s < +0xa7c VeryHigh → HealthMax(+0x5a4)×FallDamagePercent_High(+0xa84)；VeryHigh ≤ s < +0xa80 Kill → ×VeryHigh(+0xa88)；s ≥ Kill → HealthMax（直接死）；否则 0（常量 0xd14b30=0.0）。伤害>0 且 Role(+0xe8)==3 才 vt+0x2a0 TakeDamage → 只有单机/房主算。偏移用反射表核对（offname）。
  vt+0x7ec = 0xd1445c 只是落地动作档位（+0xa74 Low/+0xa78，写 +0xa68），不扣血。
  另：bEnableWorldBoundsChecks=False（Main.umap NZWorldSettings，uasset.py 只读）→ KillZ 不生效，不是掉出世界判死。
- 推断的触发：训练营菜单「高跳」（JumpZ 最高 67 倍）落地速度远超 Kill 阈值；「标点传送」目标点地面没加载时落在自己高度 +30 米再慢落。阈值默认值没读（本局诊断会写「原值」）。
- 改（NoFall.java，不改代码字节，不需要新 cave/emu）：训练岛 join 后，单机/服务端（客户端不跑）每 2 秒把所有人物的 +0xa78/+0xa7c/+0xa80 写成 1e9（只在原值像阈值 0~1e8 或已是 1e9 时写）；人物 = 实例虚表 +0x7e8 是 base+0xd149a4 或 base+0x10e8f00；每 10 秒扫 GUObjectArray（按类缓存）找新人物（复活/新加入）。诊断「训练岛防摔：ok（人物 N 个，原值 a/b/c → 1e9）」或原因。
- 标点传送（训练岛）：目标点地面没打到时每 0.5 秒再打 3 次射线，再兜底自己高度 +30 米（原逻辑）。
- 枪桌（Island.tableGuns 重写 + restockLoop）：中心 = pak shootreturn + 本块偏移（shootCenter），±19.8 米 0.9 米格往下射线（起点射击区地面 +1.9 米）；只认非地形命中（类/对象名含 Landscape/Character/Vehicle/Pick/Target 不算，修 v3.51 的 74 个地形假桌面）；比 2.7 米内最低点高 55–115 cm；1.5 m 连桌、至少 2 格、最长 6 m；每桌每 1.2 m 一个点最多 4 个；每点 1 把枪 + 3 份对应子弹（TRAIN_GUNS 18 种，编号/子弹来自 GameOverlay.CATS 已实测表）；没桌子就射击区前地上一排。刷法用现有 cave 'G'（Veh.dropAt，服务器生成、客户端同步）。SpawnOnePickUp 0x119d884 要 APickUpRefreshSystem 对象 + 物品结构体，没在模拟器验证，没用。倍镜编号没核实，没摆。
  补货：每 15 秒 scan 地上物资（类型 6），点位 70 cm 内少于摆下件数、连续两次 → 补枪 + 缺的子弹；第一次扫不到自己摆的东西就停（防越摆越多）。诊断「训练岛枪桌：N 桌 M 把…」「训练岛补枪：…」。
- 构建：bash build_all.sh 3.61 3.62 262 263（JDK 临时 sed 成 jdk-21.0.12.1+1 后改回），core/b0-2.ver=263。releases/ 只留 v3.62 三个（git rm v3.53/v3.54/v3.61）。

## v3.63（core264，全部未实测，2026-11-21 13:xx）标点传送立刻落地 + 枪桌带配件
- 用户反馈（v3.62 未测）：①标点传送要立刻到、站在地上，不要等也不要在空中（山顶标到山谷 → 直接在谷底地面）；去掉 v3.62 3×0.5 秒重试和 +30 米兜底。②枪桌像原版训练营：桌上整齐、枪带配件（倍镜/枪口/握把/弹匣/枪托）+ 子弹，捡走要补；扫描别要 1 分钟；补货更快、只补拿走的。
- 以前的标点日志（device-tests 里 20 多次）：射线基本都打到了（地形或「Actor」类），所以「在空中」主要来自 落点 +2 米 + Fly.softLand 慢落、打到不是地面的东西（Actor 类，可能是树冠/体积，日志里没记名字）和 +30 米兜底。
- 标点传送（MapMark + Trace.stand + Fly.hold/release）：
  - Trace.stand：从 Z=500000 往下打，命中是 树/草/叶/植物/体积/触发/水/相机 名字（actor/类/组件，「street」「spine」先去掉）或 ImpactNormal.Z（FHitResult +0x38，0 时用 Normal +0x2c）< 0.6 或「位置对不上」→ 从命中下 3 厘米接着打，最多 6 层；第一层能站的 = 落点。Hit 加了 nz 和 comp（FHitResult +0x6c 组件弱指针的对象序号）。
  - 打到 → Veh.teleport 到 落点 +95 厘米（胶囊半高约 88~90，没读真实值），Fly.release 清 0.3 秒水平速度和向下速度；不再 softLand。
  - 没打到（碰撞没加载）→ 先 Fly.hold（移动线程每 40ms：下落模式 + GravityScale 0 + 速度 0，同飞天悬停那套）再传到目标 XY、自己当前高度，每 0.1 秒 stand 一次，打到就传到 落点 +95 再放开；20 秒还没有才放开 + softLand。
  - 标点检查间隔 0.5 → 0.2 秒。所有地图都走这条（原来就是），只在单机/房主（Ed.ED==2 / Lan.role==2 不传）。
  - 诊断「自动传送到标点 … 用时 N 毫秒：落在 z（类）+95 厘米；射线：每层 z 类/actor/组件 法线（跳过）」。
- 物品编号核对（证实）：私有 pak 仓库 pak-files.tsv 里的拾取物蓝图名 Pickups/Weapon|WeaponAssist|Ammo/<编号>_<名字>：
  - 20501：1/2/3 AR 消音/消焰/补偿，4/5/6 SMG，7/8/9 SR，10 手枪消音，11 霰弹收束器（无 12）
  - 20502：1 垂直 2 直角 3 弩箭袋（无 4/5/6 —— v3.01 推断的拇指/半截/轻型握把不存在）
  - 20503：1 红点 2 全息 3 2X AimPoint 4 4X ACOG 5 8X CQBSS 7 弩瞄（无 6/8/9）
  - 20504：1..12（AR/SMG/SR/手枪 各 扩容/快速/快扩）；20505：1 战术枪托 2 UZI 枪托 3 托腮板 4 霰弹子弹袋 5 98K 子弹袋
  - 枪：20101000001..7（M416/AKM/M16A4/SCARL/Groza/QBZ95/AUG）、20102 1 M249 2 DP-28、20103 1..3、20104 1..9（…9 Win94）、20105 1..4、20106 1 弩、20201 1..7；子弹 20600000001..8。
  - 改：GameOverlay.attFor（握把/瞄具表、弩、手枪加消音+快扩）、ATT、Box 配件堆去掉不存在的编号。
- 训练岛枪桌（TableGuns.java，替换 Island.tableGuns/restockLoop）：
  - 不再固定等 15 秒：射击区（shootCenter）地面射线一打到就开始（最多等 20 秒）。物资链诊断挪到另一个线程 15 秒后做。
  - 找桌子 1（模型）：对象表里 StaticMeshComponent（+0x628 StaticMesh）模型名含 table/desk（排除 lift/datatable；pak 里射击场模型是 Environment/Assets/ShootingRange/Range_Table），60 米内；世界变换：+0x130 四元数、+0x140 位置、缩放 +0x150（不合理再试 +0x14c、RelativeScale3D +0x118）；模型 ExtendedBounds +0xbc 中心、+0xc8 半边长 → 桌面矩形（要正放、高 40–170、长 80–1000、宽 30–300 厘米），桌面中心打一条射线核对高度。实例化组件（ISM/HISM）没读实例数组，只计数，交给射线。偏移 +0x130/+0x150 是按 FTransform 16 字节对齐推断的，未证实（诊断有「读不对」原因）。没找到时每秒重找，最多 8 次。
  - 找桌子 2（射线兜底）：1.5 米粗格 25×25（625 条，v3.62 是 2025 条）找比 2 格内最低点高 55–115 的非地形面 → 1.6 米内连块 → PCA 方向（单格就试 4 个方向）→ 沿长边 20 厘米一步、短边 15 厘米一步找边（每桌约 30–80 条）。
  - 都没有：射击区前方地上 4 排 6 米的「虚拟桌子」，每件东西单独射线贴地。
  - 摆法：每桌按约 1.5 米均分 1–4 段；枪按优先级取（M416、AKM、UMP9、98K、SKS、S1897、M249、SCAR-L、Vector、AWM、Mini14、S686、M16A4、UZI、M24、MK14、S12K、DP-28、GROZA、汤姆逊、QBU88、VSS、P92、P1911、十字弩），再按枪种排序让同类相邻；一段：靠里一行 枪（朝向沿桌子，dropAt 新增 yaw → buf+56）+ 两头各一份子弹（距枪中心 45–70 厘米），靠外一行 配件均匀排（20–30 厘米间隔；段太短时子弹也进这一行）。两行离中线 12–25 厘米。离线算过：任意两件 ≥ 19 厘米、都在桌长内。
  - 补货：每 5 秒 scan 地上拾取物（类型 6），每件按自己位置 10 厘米内（Z 差 < 80）认；连续两次不在（约 10 秒）→ 只补那一件；第一次检查认出不到一半 → 整个不补并记平均最近距离；同一件补 3 次仍认不出 → 不再补。
  - 风险：挨着的拾取物游戏会不会合成箱子（以前同一坐标会），没测间隔阈值；dropAt 的 z 是桌面 +2 厘米，拾取物是否会再贴地没测。
  - 诊断「训练岛枪桌：N 桌 M 个桌位，摆了 K 把枪（共 x 件，失败 y）；找桌子用模型/射线，用时；桌子：[中心 z 长 宽 模型名]…；模型找桌子：…」「训练岛补枪：第 n 次检查（每 5 秒）…」。
- 构建：bash build_all.sh 3.62 3.63 263 264，core/b0-2.ver=264。releases/ 只留 v3.63 三个（git rm v3.62）。

## v3.80（core281，2026-11-21 20:4x，只推内核，未实测）进大厅加载页改用游戏自己的 UpdateUI
- 用户 20:27：等待加载页面不是游戏的，要调游戏自己的加载页（开机那页带进度条的 UpdateUI），进大厅时建出来盖上，人物装好后关；上一个智能体没改完的那版没推。
- RE（UNZStartupFlowUpdate::Begin 0x121244c）：类 = StaticLoadClass 0x1bf2c5c(UUserWidget, 0, "Blueprint'/Game/Startup/UI/UpdateUI.UpdateUI_C'", 0, 栈 0,0)；页 = CreateWidget 0x25448ac(类, GameInstance(world+0xc4), 0, 0)，存流程 +0x34；vt+0x23c 0x15d99a0 记流程到 +0x4e0；AddToViewport = 0x253dbe0(页, ZOrder) → vt+0x164；vt+0x278 0x15da2bc RegisterDelegates（+0x4e0 空就跳过）；vt+0x244 0x15dbb00 StartCheck（磁盘/腾讯更新）。
- UNZUpdateUIView 虚表 0x4d449c8（+0x25c ShowUpdateError 0x15de030 → 定位）：+0x240 OnUpdateProcess 0x15de6c4（只用 +0x210 等控件，不碰流程）。UWidget::RemoveFromParent = vt+0x128（thunk 0xf22f84）。
- LobbyLoad.java 重写：Browse 大厅 → 线程里找类（开机 UpdPage 找到页时 rememberClass 记 +0xc，失败按对象名 UpdateUI_C 扫 *GeneratedClass）和 GameInstance（类名以 GameInstance/GameInstance_C 结尾的实例）→ gcall CreateWidget → AddToViewport(10000) → SetVisibility 显示 UpdatePanel/进度条 → 每 0.2 秒 OnUpdateProcess(「正在加载大厅…」, 进度) → HideLoadingProgress/OnChangeHairColor 后 2.5 秒到 100% 或 15 秒 → RemoveFromParent。不调 vt+0x23c/0x278/0x244。我们自己的黑底层删掉，失败也不回退（只写 LobbyLoad.state 到日志）。
- 风险：在 LoadMap 之后的 Tick 里建（gcall 在 FEngineLoop::Tick），大厅之后若再 RemoveAllViewportWidgets 会被清掉；ZOrder 10000 是否压住大厅 UI 没测。
- 构建：bash build_all.sh 3.77 3.80 278 281（没有 3.79 APK 做底包，APK 没提交），core/b0-2.ver=281。
- v3.81（core282，20:4x，未实测）：用户 20:35：不要「正在加载大厅」进度条，要的就是开机更新的那个加载页本身，大厅在那页里加载。改 LobbyLoad：只开机第一次进大厅（once），建出的 UpdateUI 调 UpdPage.notice 放我们的公告，OnUpdateProcess 一次写满格「已是最新版本（vX）」，不再走假进度；人物装好 2.5 秒后或 15 秒关。LoadMap 期间（旧页被清、新页还没建）仍有空档。
- 用户 20:35 要重发服务端游戏包：本沙盒没有 /workspace/w（原版 game.apk、hostpatch/Host.java 都不在），重打不了。

## v3.82（core283）未实测
- 用户：开机不要我们自己画的公告弹层（GameOverlay.srvUpdatePage，黑底+公告+黄条），要游戏自己的加载页/进度。改：startupUpdate 不再弹，srvFallback 只记日志，新内核兜底重启线程（UpdPage 没找到页时）；UpdPage 不再塞我们公告（notice 调用去掉）、去掉 flowTypes 全表扫描。
- 客户端游戏包：用户 Drive 原版 game.apk（/workspace/w/game_apk）+ /workspace/w/hostpatch/Host.java（patchBase+patchSound mczi=1+gkey 签名）打了 v3.81 客户端包发给用户。用户说服务端包他有。

## v3.83（core284，2026-11-21 22:0x，只推内核，未实测）
- 用户 21:59（v3.82 实测，截图）：开机加载页还是没有进度条；进大厅等好一会才出衣服（大厅加载页没盖上）；服务端进大厅一会就闪退。
- 查 UpdateUI.uasset：进度条/文字在 CanvasPanel_1（CanvasPanel_0 槽 10，底部），蓝图默认可见；包里有 UpdateUI_C 的实例 WidgetArchetype（原型，不在屏幕）。UpdPage.find 只跳 CDO(0x10) → 找到的是原型，进度条全写在原型上 → 看不到。改：跳 flag 0x30，只认名字 UpdateUI_C*、不认 WidgetArchetype。
- LobbyLoad：以前 Browse 大厅时建（旧关卡还在，LoadMap 时被清），15 秒后对可能已回收的页调 RemoveFromParent → 推断是服务端大厅闪退原因（server 日志：进大厅后约 10~60 秒进程重启，无 tombstone）。改：Browse 后等 "Game class is '…FrontEnd…'" 再建；关前核对 +0xc 仍是 UpdateUI 类；去掉 UpdPage.notice（不放我们公告）。诊断加 LobbyLoad.state。

## v3.84（core285，2026-11-22 01:2x，只推内核，未实测）
- 用户 01:16（v3.83 实测）：开机页只显示「更新」、像有 bug、进度条不走；进大厅还是没穿衣服。日志 upd-1122-011558：UpdPage 找到真实页 e68bc540（v3.83 修对了）；LobbyLoad「没找到 UpdateUI_C 类」= 切关卡时蓝图类被 GC。
- RE：OnUpdateProcess 0x15de6c4：+0x210 值标签（文字非空时显示 Printf("%d%%")），+0x20c 文字标签=传入串，+0x208 进度条 SetPercent，f>0 才 Visible；FString 是 UTF-32（"%d%%" 字面量 4 字节/字）。
- 改：LobbyLoad.rememberClass 把 UpdateUI_C 类的 FUObjectItem flags 置 RootSet(1<<30) 防回收；UpdPage 进度显示值每 0.3 秒涨 0.04，开机页到 60%（文字「正在检查更新…/正在加载资源…」），到了 0.6 秒后停；LobbyLoad 从 60% 每 0.2 秒涨到 95%，HideLoadingProgress/OnChangeHairColor 每次刷新 readyAt，最后一次后 2.5 秒走满 100% 关，最长 15 秒。诊断带 rooted 状态。「显示有 bug」具体是什么没截图，已问用户。

## v3.85（core286，2026-11-22 01:30，只推内核，未实测）
- 用户 01:26（v3.84 实测，4 张截图）：进度条会走了，但开机页还有空白公告框 +「放弃更新/立即更新/帮助」+ 更新提示 → 都去掉只留进度条；进大厅先露一下大厅再出加载页（要等加载完直接穿好衣服进）；进去后键位变默认（原因未查，待问是否卸载重装过）；训练场 1 分钟内闪退（单机无日志）、没刷枪；单机不刷任何东西，服务端再刷且要游戏自己刷、不要一把一把摆；再查联机能否游戏自己刷；接着修军需。
- RE UpdateUI.uasset 父子关系：公告（ContentSwitcher/Text/Image/NoticeImage/PreBtn/NextBtn）、ExitBtn/UpdateBtn/Help/CloseBtn、IosOrAndroidSwitch(AndroidTip) 全在 UpdatePanel 下；UpdateProgressBar/UpdateProcessText/UpdateProcessValue/UpdateTip 在 CanvasPanel_1（CanvasPanel_0 下，独立）。
- 改：UpdPage.showPanel / LobbyLoad 显示面板时 UpdatePanel 设 Collapsed(1)，其余 Visible；LobbyLoad 开机 rememberClass 后后台 find 预取 GameInstance，进大厅关卡时类和 GI 都有效就跳过全表扫直接建；Island：单机（Ed.single）不调 vehPractice，TableGuns 单机/服务端都关（if false）；服务端仍让游戏自己的驾驶训练台刷车。
- 枪：客户端/服务端同一套 pak，训练岛无 ServerOnly/Pickups 关卡文件（v3.61 结论不变），游戏自己刷不出枪。军需 = Pandora 网页商城（要腾讯服务器下 Lua 页），离线无法恢复原页。

## v3.86（core287，2026-11-22 01:4x，只推内核，未实测）
- 用户 01:35：为什么进大厅又跳出来再加载，加载到 60% 就不走了。（日志：开机 StartupMap 01:21:59 → Browse 大厅 01:22:15，开机页放行后停在 60% 直到切关卡。）
- 改：UpdPage 放行游戏流程后，若开机页 Outer 是 GameInstance → 设 RootSet，继续每 0.3 秒 +0.8% 走到最多 90%，直到 LobbyLoad.lobbyBrowse/busy（40 秒上限），8 秒后清 RootSet；Outer 不是 GameInstance 就不走（防旧世界回收不掉）。UpdPage.last → LobbyLoad 起点。LobbyLoad.root(obj,on) 通用。诊断 UpdPage.creep。
- 切关卡那一下（旧页被游戏清掉、大厅页建出来之间）还会闪一下大厅，无法完全消掉（UE 换关卡会清掉旧世界的界面）。

## v3.87（core288，2026-11-22 01:5x，只推内核，未实测）
- 用户 01:39：不要大厅加载页，开机页加载完进大厅直接穿好衣服。
- 查 v3.84 日志 upd-1122-012228：大厅 LoadMap 2.5 秒（15.1→17.7），17.45~17.5 游戏发 36001/22005/30014（仓库），回包全在 23.77 才到。原因：Server.findConn 整块扫匿名内存找 ConnectorService 从 16.2 扫到 ~23.7（7 秒多），连接器没找到前 TICK 段不投包 → 衣服 6 秒后才换上。不是资源加载慢。
- 改：Server.byTable（GUObjectArray 里找 vt==CONN_VT 且非 CDO 的对象）+ Server.preFind（开机页放行时先找，存 preConn）；findConn 顺序 预找 → 对象表 → 整块扫；诊断 Server.preState / connState。LobbyLoad.show 直接 return（不再建大厅加载页）。开机页继续走最多 95% 直到 Browse。

## v3.88（core289，2026-11-22 01:5x，只推内核，未实测）
- 用户 01:47（v3.87 实测）：加载到 61% 就进了，要走到 100%；衣服穿上后还卡一下，要根治。
- 日志 lobbyop-1122-014613（v3.87）：连接器开机预找 761 ms ✅，进大厅 0 ms；LoadMap 1.9 秒结束 05.385，30015/13002 在 05.835 到，05.869 第二次 InitAvatar 穿上仓库衣服（比 v3.84 早 6 秒）。之后 08.278「开始扫全部皮肤」（整块读匿名内存）→ 帧 1488(08.7)→1503(09.98) 变慢，1503 停到 10.976 = 卡一下。
- 改：UpdPage 下载/检查完目标直接 100%（每 0.3 秒 +8%），走满 0.3 秒后才放行游戏流程；不再放行后继续走/RootSet。Server：全部皮肤扫描每个安装只做一次（filesDir/zf_skins_scanned；zf_props.txt > 8000 字节视为扫过），线程最低优先级、每读 1 MB 睡 3 ms。

## v3.89（core290，2026-11-22 02:2x，只推内核，未实测）
- 用户 02:14（截图道具页）：道具页只留战术道具和油桶，喷气背包放到背包栏；配件页按枪种分类，点一下把这类枪全部配件刷在地上，下面紧跟倍镜；刷车皮卡去掉「试」字；全部对齐；刷车不是秒出了要修；还有军需。
- 改：allItems 用 propCats：背包(30307 + 402 类里名字是喷气背包的)、战术道具(40201 去掉喷气)、油桶(40200，原「道具(1)」，推测是油桶)。attPage：枪种一排键 → giveGround 整组刷脚下（Veh.ground，服务端有加入玩家时放他脚下，加入别人房间时 Give.ask）；下面倍镜单个刷。pad() 补空位让最后一行不拉宽（道具分类/道具/翻页行/载具）；载具每行 4 个，标签去掉（试）。当前源码里没有「皮卡（试）」字样（v1.9x 老标签），按显示去字兜底。
- 刷车慢：place() 每次先 fuelComps() 整表扫 GUObjectArray 才刷。改成用缓存快照 Veh.snap（打开刷车页 preSnap 后台扫），刷完 findNew 扫一次更新；没快照时只认 9 米内的新车。
- 军需：仍是 Pandora 网页（腾讯服务器下发），离线没有原页面文件；已向用户说明并问是否接受改成游戏自己的界面替代（未做）。
- 2026-11-22 11:22 用户问游戏包里知不知道军需页内容。查：pak 只有协议（cs_lottery/cs_treasure/res_lottery/res_treasure/res_treasure_drop 的 proto 定义，无数据）和命令号；无 Pandora 页面、无奖品表。原生 UI 有 CommodityUI/TreasureOpenAnimation、TreasureAwardItemMovie、TreasureObtainItem、PropObtainUI/TreasureOpenMedia、StoreUI/StorePackageComposite*。已提议：点军需走游戏原生开箱动画+获得物品界面，从扫到的皮肤里抽一件入仓库；等用户答复。

## v3.90（core291，2026-11-22 11:4x，只推内核，未实测）
- 用户 11:39（v3.89 实测，9 张截图）：单机刷车大概率点了没效果；菜单出现「检查补丁中…」（应内置）；训练场玩一会闪退；P92 手里空气、开镜灰屏；喷气背包没模型；对局里脱衣服后身上还有（装备栏已空）。
- 日志 matchend-1122-113518：游戏 11:28:02 已 Spawn Vehicle，我们 11:28:21 才「已刷」（findNew 整表扫 GUObjectArray 十几秒，busy 期间再点只弹旧提示；确认不到就换位置再刷一辆 → Uaz_2_C_0/C_1 两辆）。另见 NZGameMode.cpp:2602「Success DisableVehicle」刷出几秒后出现（作用未查）。
- 改：Con.line 数游戏日志「Spawn Vehicle 」/「spawn vehicle failed」→ Veh.spawnOk/spawnFail；place() 调完最多等 3 秒看日志，成功马上提示「已刷」，满油+可受伤放后台线程；失败换下一个落点；没日志才走旧 findNew。成功也弹提示。giveText 检查中不再显示。
- 训练场闪退：本次日志无新崩溃（崩溃记录 11:23:18，旧 tombstone 01:23 GameThread SIGSEGV fault 4 pc 0x2dfdfb4，函数 0x2dfdec8 遍历 r4+0x640 TArray（12 字节元素）时 data 为空，原因未查）。P92/喷气背包/脱衣服：日志里没有相关记录，未改。仓库衣服进对局用 cave 'I' 发到身上（30318000000/30308000017/30310000006），焊死列表 10200000000,30213000003,30306000000,30303000006。

## v3.91（core292，2026-11-22 11:5x，只推内核，未实测）
- 用户 11:49 纠正：喷气背包、脱衣服两个问题是真的（截图就是证据），我上一条说「日志没记录」不对——11:30 那局日志里有。
- 日志：11:30:02 Discard 30318000000（绝地风衣）成功后紧跟 NZBRCharacter.cpp:6836「[Pickup] DiscardInventory Give Default Apparel Fault.」→ 身上模型不换。11:30:07/08 丢 30310000006、30308000017 同样（截图：地上有深绿迷彩短裤、吃鸡MVP口罩，身上还戴着）。原因：syncWear 只把「穿着的」默认件放进出生默认列表（10200000000,30213000003,30306000000,30303000006），默认上衣 30301000001/裤子 30302000000 被风衣顶掉后不在列表，游戏脱下时找不到默认件。改：4 件默认衣服（30301000001,30302000000,30303000006,30306000000）始终放进出生列表（原版 DefaultRoleClotheDataTable 也全在）。
- 喷气背包：11:29:37 穿上 30307000099 → InitJetpackBag ×2、OnRep_AvaRepInfo、SkeletalMeshComponent_0 注册、紧跟「LogRHI: Error: Unsupported EPixelFormat 28」×2；客户端 pak 里 Avatar/Common/Bagpack/30307000099/ Mesh/Huan/材质都在。原因未定，未改（可能和焊死的默认背包 30306000000 抢背包位，或贴图格式 28 不支持）。
- P92 / 训练场闪退：那段（训练场）没有上传日志，ntfy 里搜不到 P92，仍待查。

## v3.92（core293，2026-11-22 12:2x，只推内核，未实测）
- 用户 12:09（v3.91 实测，7 图+视频）：训练场不闪退了 ✅；喷气背包截图里有模型了；抛物线正常（「注册了」=正常）。要求：战术道具标名字、删「重新读」、删「手雷抛物线检查」、视频里菜单展开/收起要丝滑过渡、倍镜刷进背包、霰弹枪配件只有 2 件且 S12K 没配件、继续修 P92、训练场菜单加刷车。12:13 补：扔烟雾弹显示的还是手雷那个标（未处理）。
- 改：GameOverlay toggle：点标题展开/收起用 ValueAnimator 260ms 宽高一起插值、中心不动、body 淡入淡出（WindowManager 窗口逐帧 updateViewLayout，结束恢复 WRAP_CONTENT）；TAC_CN 名字表（按 pak 蓝图名：1 Shield 护盾/2 Lift 升降台/3 LandMine 地雷/4 Trap 捕兽夹/5 Shrub 伪装灌木/6 Chara 替身人偶/7 FakeRock 伪装石头/8 Tree 伪装树/9 Dummy 诱饵假人/10 Box 伪装箱子/12 ScoutBallon 侦察气球/14 LaunchPad 弹射板/15 ScanGrenade 侦测手雷，40200000001 汽油桶）；删重新读、删抛物线检查；倍镜 giveBag（单机强制进背包，服务端仍脚下）；霰弹枪组 8 件（加步枪枪口 3 + 步枪弹匣 3 给 S12K，pak 里没有 S12K 专用弹匣蓝图）、attFor S12K 加 AR_MZ；TRAIN_TABS 加 1（刷车）。
- P92：客户端 pak cf1f20f5… 里 W202010001_P92 的 BP/1P_FPP/3P 模型/材质/贴图都在，设备上这个 pak 有挂载（11-15 日志）。静态看不出原因。加诊断：Con 收到含 P92 / SK_WP_ / Failed to load 的游戏日志记 60 条「P92诊断」。

## v3.93（core294，2026-11-22 12:3x，只推内核，未实测）
- 用户 12:27（v3.92 实测，客户端包，5 图+视频）：过渡有点卡；喷子枪口「红色那个」（推断=鸭嘴 20501000012，pak 有 Items/WeaponAssist/20501000012_BP_Duckbill，没有 Pickups 蓝图，刷地上可能失败）；训练场「摔死」——日志 12:23:45 Player Died DamageType=NZDamageType_VehicleExplosion（是刷出来的车爆炸，MinHp 挡住 0 次：爆炸伤害不走 GameMode vt+0x484 那条）；训练场刷车不是立马出（日志：游戏 12:22:44.108 Spawn Vehicle，我们 45.463 才提示——Con 日志线程每 1 秒读一次）；训练场菜单刷车上面空一格（隐藏的「道具」分隔线没跟着隐藏）。
- 改：toggle 改成一次性改窗口 + scale/alpha 硬件层动画（不再逐帧 updateViewLayout）；鸭嘴加进霰弹枪组和 S1897/S686/S12K；carCheck 训练场不跑（车不打开 bCanBeDamaged）；Con.fastUntil：刷车后 4 秒日志 100ms 读一次；分隔线存 tabSep，分页隐藏/训练场时一起隐藏。
- P92：这几局日志只有 20201000005（92式）出现过，没有 20201000001 P92；用户截图里的手枪可能是 92式。P92 诊断没抓到东西。

## v3.94（core295，2026-11-22 12:4x，只推内核，未实测）
- 用户 12:33：训练场是开高跳跳起来摔死的（不是车炸——12:23:45 那条是另一次）；刷车不该靠读游戏日志（日志关了就刷不了）。
- 查：三局诊断 MinHp/防摔的人物都是 94653b00（myPawn 复活/再进后可能是旧的）；防摔阈值法只在第一次进训练场 ok，之后「还没找到人物（第 13 遍）」→ 摔伤阈值没写上。
- 改：NoFall 进训练岛时直接改摔伤函数 0xd149a4 开头 f04d2de918b08de2028b2ded → mov r0,#0; vmov s0,r0; bx lr（0000a0e3100a00ee1eff2fe1），离开训练岛改回；阈值法保留。Veh.place：cave 回 0x5b03（游戏线程同步调完 0x1102ca4）就马上提示「已刷」，日志/对象表只做后台核对，挡住才再提示。
- 风险：项目经验说运行中改已跑过的代码可能不生效；如果还摔死，要改成启动早装 + 标志位（SLACK 已满，要另找空位）。

## v3.95（core296，2026-11-22 12:4x，只推内核，未实测）
- 用户 12:38：要「能掉血，剩一丝血怎么也死不掉」；12:39：刷东西都不要靠日志，下个版本他会删掉上传日志。
- 改：NoFall 不再把摔伤函数整个返回 0，改 0xd149a4 里三处 HealthMax(+0x5a4) → 当前血(+0x5a0)−1：0xd149ec / 0xd14a24（High/VeryHigh 档 ×百分比）、0xd14b1c（Kill 档 = 血−1）。训练岛里改、离开改回；不再写阈值。注意：高、很高两档伤害现在按当前血算（比原来按满血算小）。
- 刷车：v3.94 起 cave 返回就提示，不依赖日志；日志只做后台核对。

## v3.96（core297，2026-11-22 12:5x，只推内核，未实测）
- 用户 12:50（图+视频，v3.95）：①动画还是闪 ②训练场脱衣服身上还在 ③要剩 1% 血、再受伤照样有叫声和掉血特效但不死 ④每次回大厅重新进游戏所有参数回默认 ⑤单机进图默认上飞机，只有服务端能开关。
- ①逐帧（60fps）看：收起时内容缩小淡出后出现一块空白白框、几帧后才出小条；展开时小条位置先出空白框。= 改悬浮窗大小那一下内容没画。改：整块淡出到 0 → apply 改窗口 → OnPreDraw 等 3 帧 → 缩放淡入；wl.windowAnimations=0。
- ③日志 12:47:05 Player Died DamageType=NZDamageType_InVehicleExplosion（CheckReallyDead: InVehicleExplosion, VehicleHitWall），MinHp 状态「人物 0」→ 锁血根本没指向人物。改：MinHp 每秒用 Mods.livePawn()（Veh.info buf+8 = PC 当前人物）。注意 Health(+0x5a0)/HealthMax(+0x5a4) 是 int（godTick 按 float 读是错的，没生效）。
  - RE：人物 TakeDamage = 虚表 vt+0x2a0 = 0xd2230c（虚表起点文件 0x4bdcb48，摔伤函数在 +0x7e8）；它内部不直接写血，写血在 GM 伤害链（MinHp 钩点）。爆炸是否走 GM 链未确认。死函数空位 0x171e1c8..0x171e7cc 全被占。libUE4 里日志字符串查不到（加密/混淆）。
  - 血=1 时伤害改成 0，可能没有受伤反馈（未确认）。
- ②12:46:43 丢 30318000000 / 30310000006 后仍「DiscardInventory Give Default Apparel Fault」两次；出生列表已含 6 件（含 30301000001、30302000000）→ 不是出生列表的问题，原因未查到，没改。
- ④resetMatch：speed/jump=1、无后座/聚点/无限子弹关、Fly.want=false（以前 v2.31 跨局保留）。
- ⑤zoneOff()/restoreZone：Ed.ED != 1 一律 false（单机上飞机），服务端照旧。

## 2026-11-22 13:40 v3.97（core298）
- 用户：服务端大厅站一会就闪退（有时点了联机训练营、有时没点都会），客户端正常。
- 诊断：13:33 那次（upd-133414 的上次运行）进大厅 13:32:56，找到连接器 13:33:07，游戏日志停在 13:33:13，没点任何东西，无 tombstone（Bugly 关了原生上报）；13:28、13:32 两次是点了联机训练营后加载 3~4 秒没了。和 v3.82 记的「服务端进大厅 10~60 秒进程重启、无 tombstone」同一现象。
- 服务端独有、进大厅 20 秒后跑的：ModeProbe.survey（名字表全量扫 4 遍，每遍 64×16384 次读 /proc/self/mem），时间对得上 → 不再自动跑（结果没人用）。未实测是否就是闪退原因；若还闪，下一个怀疑 Server 的 TICK/SEND 钩子或 Lan.host 开训练岛。
- 14:05 在线更新收不到：jsdelivr 对新提交报「Package size exceeded 50 MB」（仓库 game/ 123M、notes 40M），@main 一直给缓存 297；raw/ghfast 是 298，但用户网络疑似只通 jsdelivr。1e535fb 误把 66 个 releases APK 提交了，ed9b216 已取消跟踪（.git/info/exclude 加 /releases/*.apk）。已发 ZFrame-v3.97-服务端.apk。待办：在线更新换一个不受仓库大小限制的源（如单独小仓库只放 core/，或 GitHub Release），需要新底包才能生效。
- v3.98（core299）：NetCore 加两条更新路——① ntfy 话题 zf-qj-core-k7r2p9m4x1 读最新「core <版本> <sha>」，按 jsdelivr @sha 取；② 探 jsdelivr 标签 @core<CORE_VER+1..+10>。注意：标签在打之前被探过会被 jsdelivr 404 缓存（实测 core900 先探后打一直 404；core901 先打后探立即 200），所以 ① 是主路。发布流程：push 后 `git tag core<N> && git push origin core<N>`，再 `curl -d "core <N> <sha>" ntfy.sh/zf-qj-core-k7r2p9m4x1`（ntfy 只留 12 小时，过期要重发）。另打服务端游戏包（原版 game.apk + Host + core/b1.dex）发给用户。
- 15:13 用户：服务端大厅闪退原因可能是他把安装包压缩再解压过（重新打包），直接放进去就正常。v3.97 去掉 ModeProbe 自动扫描不一定是原因（未实测）。传包一律别压缩。
- v3.99（core300）：16:00 v3.98 服务端进大厅约 12 秒无声退出（最后只有 5017 包，无 tombstone）。诊断加 AvatarLog.exitInfo：ApplicationExitInfo 最近 6 次退出原因/pss/rss，原生崩溃或 ANR 附 TRACE_B64（gzip 的 tombstone proto）。首次用新更新路（ntfy 指针 + 标签）推送。
- 16:06 exitInfo 结果：服务端大厅 3 次退出（15:55:51、16:00:13、16:04:57）全是 原因 2（SIGNALED）状态 9（SIGKILL）前台，pss ~380MB rss ~520MB；不是 LOW_MEMORY(3)、不是原生崩溃。进大厅后 12~16 秒。我们自己的 NetCore.restart（killProcess）也会记成同样的 2/9（16:03:54 那次是更新重启）。怀疑：云手机 cgroup/内核 OOM 或其它进程 kill。v4.00（core301）加 Beat 心跳：每秒 VmRSS/VmSwap/Threads/MemAvailable/SwapFree/zf 线程/最后 note 写 files/beat.txt，下次启动挪 beat-prev.txt 进诊断。
- 16:13 v4.00 心跳：被杀前 MemAvailable 1.7GB、RSS ~1.04GB、线程稳定 → 不像内存不足。被杀前活着的 zf 线程：zf-server/zf-skins/zf-gfx/zframe-uelog/zf-logup/zf-diag。zf-skins（Server.allSkins 整块读 /proc/self/mem 匿名内存）在连接器找到后 3 秒开始，约 9 秒后进程被 SIGKILL；被杀就建不了 zf_skins_scanned 标记，于是每次进大厅都重扫。v4.01（core302）禁用 allSkins 自动扫（推断为原因，未实测）。
- 16:16 用户：全部皮肤扫描本来只要客户端做，服务端不该加；服务端别再加这类整块扫内存的加载。
- 16:20 v4.01 实测：不扫皮肤照样在进大厅约 16 秒被 SIGKILL（16:18:59），被杀前 zf 线程只剩 zf-gfx/zframe-uelog/zf-diag/zf-server，内存充足 → 皮肤扫描不是原因（v4.01 那条推断作废，但服务端仍不扫皮肤，用户规则）。logcat 过滤的 150 行被 5017 刷屏挤掉。v4.02（core303）：诊断加被杀前 10 秒全部 logcat（-b all，去刷屏行），看谁发的信号。
- 16:28 v4.02 被杀前 logcat（pid 35502，被杀 16:26:07）：16:25:57 W/xclient [WAIT TO ACTIVE]→[ACTIVE]、TApm 发 hawk_data；16:25:58.608 W/xclient「Tencent Qemu blocked」；之后只有正常日志，约 9 秒后 SIGKILL。推断：游戏自带安全组件（xclient/TP）认出云手机是模拟器（Qemu）后杀进程，不是我们的代码。按项目规则不做绕过保护，不去改/关 xclient。只看到一次窗口，待更多样本确认；客户端为何不被杀未知（可能 xclient 在服务端登录后才激活，推断）。
- 16:34 用户：游戏本来已经去了 TP（包里有 libfucktp），问是不是我们又加上了。核对：服务端包除 classes2.dex（=我们的 b1.dex）外与原包一致，libfucktp/libtersafe 都没动；我们代码里没有任何加载/调用 TP 的地方。时序：16:25:52 开大厅、16:25:57 xclient ACTIVE（大厅登录后 5 秒）、16:25:58 「Tencent Qemu blocked」、16:26:07 被杀。「Qemu blocked」也可能是 fucktp 拦下了检测，凶手未定。已问用户服务端大厅以前哪个版本稳定过，好对版本。
- 16:36 用户：应该不是 TP。核对：xclient 是腾讯 WeTest Cube APM（libcubehawk、hawk_data、qcc），不是 TP，「Qemu blocked」是 APM 日志，撤回 TP 推断。更新重启那次 logcat 有 I/Process「Sending signal. PID SIG: 9」（我们的 killProcess），而大厅被杀那次没有 → 不是本进程 Java killProcess；是原生 kill 或外部进程（如厂商内存管理）。三次被杀时 VmRSS 都在 ~1.0GB（996/1004/1037 MB），MemAvailable 仍 1.7GB。v4.03（core304）心跳加 VmPeak/VmSize/oom_score/adj，准备拿客户端大厅心跳对比。
- 16:56 v4.03 样本（pid 2974）：用户在服务端大厅点「联机训练营」开房（16:55:30 open TrainingIsland?listen），加载 5 秒后 16:55:35 被杀，原因2/状态9，前台 oom_adj 0，MemAvailable 2.2GB，VmRSS 1037MB（第 4 次被杀都在 ~1.0–1.04GB）。另一次（pid 2302，16:54:37）是在线更新重启。xclient「Qemu blocked」照常出现（APM，非原因）。推断：应用 memcg 上限 ~1GB → 内核 OOM 杀（不走 lmkd，系统记 SIGNALED），未证实。v4.04（core305）心跳读 /proc/self/cgroup + memcg 的 current/max/limit/events(oom_kill)。
- 17:32 用户：不陪测了，要解决办法，问为什么以前不闪。查：服务端大厅闪退最早 v3.82 实测时报（11-21 21:59），那几版（v3.80–3.84）加了大厅保留游戏更新页/预取等，之前版本服务端大厅是否稳定没有记录。服务端 RenderQualityOption 读出来是 Unknown（大厅没减负，只有对局 Gfx.lite）。
- v4.05（core306，未实测）：服务端包（Ed.ED==1）压内存 Con.SRV_MEM：set RenderQualityOption Low + sg.* 0 + r.Streaming.PoolSize 160 + 草/植被 0；开机 runTweaks、每次 LogLoad: Took、Lan.host 开房前各发一次；服务端不发 GfxOpt 细项、Gfx 不跟设置切画质。依据：4 次被杀都在 RSS ~1.0GB（推断上限）。若还闪，下一步：服务端去掉大厅保留更新页/LobbyLoad。
- 18:24 用户朋友装包后卡开机页（无进度条、无文字，开着美国节点梯子），没收到他的日志。推断：开梯子后腾讯开机检查请求走代理，没按离线流程失败 → 游戏没进更新步骤；或 ZFrame 在他手机没加载。已让关梯子重开，还卡就要机型和包名。未实测。

## v4.06（core307，2026-11-22 19:0x，全部未实测）服务端大厅被杀 + 朋友开机卡加载图
- 证据（ntfy 18:00 后全部）：v4.05 服务端那次（pid 6475，18:30:43 启动、18:31:25 进大厅、18:31:34 传完 lobbyop）之后没有新的上传 → v4.05 被杀那一刻的心跳/logcat 还没有（下次启动才会传 beat-prev）。18:30:34 那次（pid 5560，v4.03→306 下载完）是我们的更新重启（logcat 有 I/Process Sending signal 9）。v4.04 在服务端一次都没跑过（4.03 直接更新到 4.05），所以 memcg 数据一直没有。
  - 已有 6 次大厅/开房被杀：原因 2 状态 9、前台 adj 0、被杀前 RSS 996~1040MB、MemAvailable 2.2GB、SwapFree 一直 1048576k（1GB swap 从没用过）、VmSwap 0、没有「Sending signal」（不是我们的 killProcess；全代码只有 NetCore.restart 会自杀，且只在下好新版时）。客户端在荣耀上训练场 RSS 975MB + swap 20MB 正常。→ 推断：云手机单应用内存上限 ~1GB（memcg 不换 swap 或厂商看门狗），未证实。
  - 开机 StartupMap 阶段服务端已 ~810MB，进大厅 +230MB（游戏大厅本身）。我们开机 50 个 zf-ver 线程（NetCore 探镜像）时 RSS 435→531MB。
- 朋友开机卡住（视频：腾讯 logo → TiMi → 加载图，无进度条无字）：TiMi 能出来 = ZFrame 已注入（TiMi 是我们 v3.59 接回的）。没有任何上传 = 我们只在进大厅后才传日志。
  - 代码原因（确定）：v3.29 起「查更新没结果就一直重试、不放行」：Con.tickAudio 的 hold = !NetCore.done || !UpdPage.finished，NetCore.done 只在 已是最新/下好 时置位，失败分支（「更新失败（网络）…」）不置位 → 所有镜像都不通/包不对时游戏永远停在更新页（GATE 补丁让游戏自己的更新步骤不结束，只能靠我们 open 大厅）。另外 startupUpdate/UpdPage 只靠游戏日志里的 StartupMap 行触发、engineSeenAt 只靠 LogLoad 行 → 日志读不到也会永远卡。
  - jsDelivr @main 现状（19:0x 实测）：cdn 报 Package size exceeded；testingcf/gcore 的 b2.ver=297、fastly=298，但 b2.dex 是 306 的（sha1 084a0f9…=core306）→ 旧内核（<299，没有 ntfy 指针）只通 jsDelivr 时：读到 297/298 → 下 dex → 内核号对不上「包不对」→ 无限重试 → 永远卡。purge @main 的 .ver 返回 finished 但不变。钉提交 @sha 正常。
  - 没进度条：≤283 的内核把进度写在 WidgetArchetype（v3.83 修的）上，看不见；最后一个客户端游戏包是 v3.81（core282，11-21 打）→ 推断朋友装的是它（未确认）。
- 改：
  1. NetCore：check 开始后 20 秒没结果（下载中 40 秒）强制 done（released），第一次失败也立刻 done，后台继续重试（5/10/…/60 秒，最多 6 次），下好在大厅弹「重启更新」；指针最多等 9 秒（DNS 卡住）；下载整体 90 秒上限；标签只探 +1..+3 × 2 节点（线程 50→16）。
  2. Con.tickAudio：hold 最多 60 秒；日志里没 LogLoad 但更新页已建出来 → 算引擎就绪。GameOverlay.attach 20 秒计时兜底 startupUpdate（不靠 StartupMap 日志）；UpdPage 找页 25→45 秒、进度循环 70 秒上限，放行后文字「正在加载资源…（更新在后台）」。
  3. BootLog（新）：客户端/服务端包（BUILD≠0）启动 6 秒发一条「启动」消息（标题带机型；内核号、内置/在线、机型/安卓/ABI、VPN/代理），40 秒和 120 秒还没进大厅传 boot-*（在线更新各镜像/错误、更新页/启动步骤、日志找没找到、最近 120 条 LogCap、上次心跳）。LogCap 加 120 条内存环。
  4. 服务端内存：UpdPage 不再 LobbyLoad.rememberClass（v3.84 加的 UpdateUI_C 类 RootSet + 开机全表扫 GameInstance，v3.87 起大厅加载页不建，纯占内存）；UpdUi.probe 不再全量扫名字表（v3.14 诊断，大厅/开机都跑）；SRV_MEM 贴图池 160→128 + r.Streaming.MipBias 1。
  5. MemTrim（新，只服务端）：RSS>850MB 时游戏线程调 32 位 libc madvise(范围, 长度, 21=MADV_PAGEOUT) 把私有匿名冷页换到 swap（libc 用 ELF dynsym 找 madvise；范围 rw-p 无名/[anon:*]，排除栈/dalvik/bss/显存，相邻合并，≥256KB，一轮最多 64 次，15 秒一轮；第一轮降不到 20MB 就停用）。依据：swap 1GB 一直空着，内核不会自己换。若上限是 memory+swap 合计（memsw）则无效。
  6. Beat：每秒加 RssAnon/RssFile/RssShmem 和换出状态；服务端第一次过 800MB 记 smaps 前 15 名到 smaps.txt，下次启动随 beat-prev 进诊断。
- 未做：客户端游戏包重打（本沙盒没有原版 game.apk / Host.java；Drive API 看不到文件）。
- 待看（下一次服务端启动自动上传的诊断）：「内存换出第 1 轮：RSS a→b」「上次运行 smaps 汇总」「cg …」；还被杀就按 smaps 前几名再砍。朋友：boot-/启动消息（标题带机型）。

## 2026-11-22 19:5x 朋友小米开机卡住 → v4.06 已能进（只记笔记，没出新版）
- 设备：Xiaomi 2602BRT18C 安卓 16（SDK 36），v4.06 客户端 内核 307（在线），WiFi 无 VPN。19:46:23 启动消息；boot-1122-194657（40 秒）、boot-1122-194817（120 秒）。用户 19:48 说朋友已进游戏。
- boot 日志要点：
  - 在线更新 14 秒内「已是最新（v4.06）」，done=true、超时放行=false → 这次没走更新超时那条路（镜像：jsDelivr 部分 297、其余 307；gh-proxy 403）。
  - **游戏日志：没找到**（findUeLog 在 外部 files / files / /storage/emulated/0/UE4Game 都没找到 .log）。所以 StartupMap / LogLoad / [FlowManager] 一行都没读到，「启动步骤：没看到」。
  - 19:46:37 「日志里没看到 StartupMap，20 秒计时兜底开始找更新页」→ 19:46:54 找到更新页 e84b8fc0（UpdatePanel 收起）→「引擎就绪：日志里没看到 LogLoad，按更新页已建出来算」→ 19:46:59 开机直接 open FrontEndMap_NEW（启动后约 36 秒）。之后朋友进了游戏。
- 原因（由日志证实的部分）：这台手机上我们读不到游戏日志。v4.05 及以前开机流程（startupUpdate、engineSeenAt、开机进大厅）全靠日志里的 StartupMap/LogLoad 行触发 → 永远不触发，而 GATE 又让游戏自己的更新步骤不结束 → 永远停在加载图。v4.06 的「不靠日志」兜底（GameOverlay 20 秒计时 + 更新页建出来算引擎就绪）正好救了它。和梯子/在线更新无关（这次没开梯子，更新也正常）。
- 推断（未证实）：读不到日志是因为游戏日志写在 /sdcard/UE4Game/… 而新装的游戏在小米安卓 16 没有存储权限（或游戏这台机器没开文件日志）；用户自己的荣耀能找到 PUBGM.log。
- 遗留（未改）：这台机器上所有靠游戏日志的功能都不灵——lobbySeen 不会置位（120 秒 boot 仍说「没进大厅」，其实 19:46:59 已开大厅）、lobbyop/诊断自动上传、刷车日志核对、进图/对局开始识别（Con.line 里的 Browse/LoadMap）等。若朋友报功能不灵，下一步：boot 诊断加 UE4Game 各候选目录是否存在/可读 + 存储权限状态；大厅/进图识别改成不靠日志（如读 GWorld 当前地图名）。

## v4.07（core308，2026-11-22 20:0x，全部未实测）不读游戏日志，状态全从内存读
- 用户 19:52：游戏日志只有手机设置里开了日志才有，普通手机没有 → 三个包（单机/客户端/服务端）都不再读游戏日志，不保留日志这条路；诊断里原来的日志摘录直接去掉；不去想办法让游戏写日志。
- 以前靠日志的（盘点）：Con.line 全部 —— Browse（切图重置、StartupMap 开机更新页/选择框）、LogLoad: Took（引擎就绪、进大厅/进图/训练岛、lobbyReady 菜单显示、服务端自动开、Flow/Bots/HostMatch/Island/Parabola 进图、GfxOpt/srvMem 进图重发）、UNZFrontEndAudioManager（声音跳转 feAudioSeen）、LoadMap 大厅（提前开服务端）、Game class is（战术模式 propsMode、LobbyLoad）、RequestStartSingleMatch（游戏自带开始游戏选图）、QueryQuitMatch（训练岛退出）、Mall/Pandora（军需提示、lobbyop 自动传）、RenderQualityOption（getall 画质跟设置）、ApplyFrameRateOption（重发帧率）、Join succeeded（服务端有人加入 → Crew 同步）、NetworkFailure/ammo/heal（自动传 net-/ammo-/heal-）、StreamingServerLayer Add（单机 StreamLevelIn 补服务器层）、Spawn Vehicle（刷车后台核对）、LockServer/PickupItemIDList（服务端空投空箱补货）、BlueCircle、武器名 lastWeap、getall 刷车（旧 spawnCar）、UpdFlow「Begin Flow Update」、UpdPage 启动步骤、Prop 缺资源/用道具、Flare、Island.seen、P92、导航/人机/航线/丢东西日志、Flow.airLog、Server 试穿行 EquipItemsForHallRole/发色兜底、AvatarLog/BootLog 读日志文件。
- 新 WorldWatch.java（唯一状态来源，zf-world 线程每 0.4 秒）：
  - GWorld = libUE4 .bss 0x53757b0（RE：LoadMap 0x2e0c0f8 在 0x2e0d6c4 str 新世界,[GWorld]；0x2519fd8 return GWorld；GetWorldFromContextObject 0x2e05764 兜底读它；GameEngine::Tick 0x29d5c04 写它）。
  - 世界 +0x744 UnpausedTimeSeconds（GetUnpausedTimeSeconds 0x29fda50；+0x740 TimeSeconds 0x29fda08）开始涨 = LoadMap 走完；45 秒不涨也放行。
  - 反射表核对：World +0x24 NetDriver、+0x54 StreamingLevels、+0x94 AuthorityGameMode、+0x98 GameState；GameStateBase +0x2bc PlayerArray。
  - 地图名：世界前 0xC00 字节里找内容 /Game/ 开头的 FString（FURL.Map，UTF-32），偏移缓存；6 秒找不到用名字表读 Outer 包名（服务端只用已缓存的名字表）。
  - 事件：Con.onLeave（GWorld 空/换世界，收菜单）、Con.onBrowse(地图)（原 Browse 重置）、Con.onLoaded(地图)（原 LogLoad: Took）。每个世界最多各一次；地址复用靠时间倒退/URL 地图名变了识别。
- 替代：
  - 大厅加载完 = 大厅音频已初始化（feAudioSeen）。
  - 战术模式：GameMode 类名（名字表已缓存时）含 Props，否则看我们发的 open 是否带 Props。
  - 游戏自带开始游戏：Server 收到 15001 CSRoomMatchStartAllocReq（cs_room_match.proto：map_id=4、maps_chose=7）→ Con.startGame（认 1/3/4/5，也认位掩码；mark 记原值「开始游戏 15001：maps_chose=… map_id=…」，没实测字段含义）。
  - 训练岛退出：只走 Server 15003/15005（本来就有）。
  - 毒圈底层表（natPrepare）：Con.exec 发「open /Game/Maps/…」时装（原来 Browse 行，同一时机）；游戏自己切的图在 onBrowse 补。
  - 画质跟设置：对象表找 NZSystemSettingData 实例（WorldWatch.firstInstance，60 秒最多找一次，服务端不找），每 3 秒读 +0x7c RenderQualityOption（反射 0x4c07a5c；枚举 0 Low 1 Middle 2 High 3 VeryHigh 4 Ultra 5 Unknown，注册表 0x4c04120）；+0x84 FrameRateOption（反射 0x4c07914）变了 → 重发帧率；每次进图后也重发帧率。注：ApplyRenderQuality 0xca14e0 里 +0x930 是 bCanApplyRenderQuality，不是画质。
  - 服务端有人加入：GameState.PlayerArray 人数变多 → Crew.joined()。
  - 客户端断线：不是我们发的 open 大厅却回到大厅 → Lan.autoUp("net")。
  - UpdFlow.pageSeen/gamePage：UpdPage 找到更新页即置位。
  - 发色：只认 13003 请求包里的；试穿：只按类别处理 + 推仓库（不再等日志 3 秒）。
- 去掉（没有内存替代，按用户说法诊断直接省）：单机按日志 StreamLevelIn 补服务器层（history 505：日志 verbosity 低时这行本来就没有）、服务端空投空箱补货（Loot 开关默认关）、刷车日志核对、ammo/heal 自动上传、军需提示、Mall 触发 lobbyop、各类日志摘录诊断、旧 getall 刷车 spawnCar/tryCar（已不用）。
- 诊断：AvatarLog.export 不再要求找到游戏日志（以前找不到日志直接「找不到游戏日志」不导出 → 普通手机所有自动诊断都没传过）；日志行换成 WorldWatch 状态/事件 + LogCap 最近 120 条；资源清单只列大文件。BootLog 加「状态来源：内存」+ WorldWatch.text()。
- 用户 20:02：联机卡片去掉「上传日志」「设为我的开房地址」；「加入这个地址」占满一行（边距和地址框对齐）；Room.myAddr 固定 AIWAN（frp-put.com:47937），首次建卡片时删旧 pref room_addr。后台自动上传照旧。
- 没做：「调试开关模拟没日志」——日志整条路已删，所有手机现在都是没日志的路径，不需要。没做存储权限/日志路径诊断（用户说不用管日志）。
- 未实测风险：GWorld 偏移（静态 RE 4 处一致，未上机）；URL FString 启发式找错字段（会退到包名）；开机 Dummy 世界的 URL 也可能是 StartupMap（startupUpdate/loadingPage 有一次性保护）；NZSystemSettingData 找到的实例是不是设置界面改的那个；15001 字段含义。
- 构建：bash build_all.sh 4.06 4.07 307 308（JDK sed 成 jdk-21.0.12.1+1 后改回），core/b0-2.ver=308；releases 换成 v4.07 三个（git rm v4.06）。

## v4.08（core309，2026-11-22 20:1x，全部未实测）单机/客户端不上传任何日志，只有服务端上传
- 用户 20:07：把所有自动后台诊断上传（boot-、lobbyop-、net-、train-、crash-、upd- 等、启动 ntfy 消息、zf-logup 上传线程）从单机和客户端彻底去掉，只留服务端（Ed.ED==1）；只为上传存在的数据收集（心跳/步骤文件/autoCrashSave）单机/客户端也不做；在线更新读 ntfy 指针照旧。
- 实现：Ed.uploads()（BUILD==1 && ED==1）。LogUp.send/put 硬闸；BootLog、AvatarLog.export（手动也不传，返回「单机/客户端不上传日志」）、autoTrain、Lan.autoUp、Server.opLog/autoCrashSave、大厅 40 秒 lobby-、MapMark 两个线程都先判；联机页「导出诊断」按钮只服务端显示。Beat.start 只服务端，其它调 Beat.wipe 清旧文件（beat*、smaps*、zf_server_steps*、zf_tomb_saved、zf_crash_saved、zf_logq）；Server.stepFile 非服务端返回 null（mark 不写）。
- 没动：NetCore（ntfy 指针只读 + 下载）、Room/Give 的 ntfy（房间表/代刷是功能）。
- 构建：build_all.sh 4.07 4.08 308 309（JDK 换 jdk-21.0.12.1+1 的临时副本 /tmp/b.sh 分两次跑，仓库脚本没改），core/b0-2.ver=309；releases 换成 v4.08 三个。

## v4.09（core310，2026-11-22 20:3x，全部未实测）开始游戏/训练场没日志进不去 —— 根因 + 去掉全部日志残留
- 用户 20:19：不开日志进不了训练场、点游戏内「开始游戏」不开局。20:22：把全代码所有靠日志的地方都去掉。
- 根因（确定）：v4.07 把「RequestStartSingleMatch add_maps_chose:N」日志行换成读 15001 CSRoomMatchStartAllocReq 包，Server.startPicked 只认 field 7 wire 0。
  libUE4 0x494a035 内嵌描述符：cs_room_match.proto 是 **proto3**（同文件 +2027 处 syntax "proto3"），maps_chose = `7 repeated int32` → proto3 默认 **packed（wire 2）**；
  map_id(4) 单机下多半是 0（proto3 省略）→ 解析永远 -1「认不出地图」→ Con.startGame 不调用。训练场入口走的也是同一条（编号 5）→ 两个症状同一根因。
  v4.07 之前能用是因为读的日志行；v4.07/4.08 跟开不开日志无关，所有手机都坏（用户开日志时多半是旧版或误判）。
- 修：startPicked 认 packed 和非 packed，取 maps_chose 第一个 1/3/4/5，再看 map_id，再按位掩码；结果存 Con.lastStart（菜单显示）。离线单测 packed [3]/[5,3] 解析正确。
- 次要卡点（防 WorldWatch 认不出大厅时卡死）：
  - Con.startGame：玩家在大厅点的开始 → 直接置 feAudioSeen、清 pendingMap/bounce，goMap 不再挂起等「大厅声音」。
  - Con.tickAudio：开大厅（exec 记 lobbyOpenAt）40 秒仍没认出加载完 → 按已加载；12 秒 Server 没开 → 兜底 Server.start（否则 15001/仓库请求没人回）；开机 90 秒 engineSeenAt 还是 0 → 兜底就绪。
  - WorldWatch：URL 读不到地图名时，用我们刚发的 open 命令里的地图名（每条命令只给它之后出现的第一个世界用一次），再退包名、再 "?"。
- 去掉日志残留（都是 v4.07 后的死代码或只读日志的诊断）：LobbyLoad.line、Flare.line+KEYS、Island.seen、Lan.line+trig（net/ammo/heal 自动传）、Loot.line（空投空箱补货触发）、Prop.line/gameLine/GAME_KEYS、UpdFlow.line、AvatarLog.readText（异或 0x73 解码）/KEYS/around（被杀前后 logcat）/crashInfo 的 logcat 读取（只留 ApplicationExitInfo、私有目录崩溃文件、心跳，仍只服务端）；LogCap.note 不再 android.util.Log.i（不写 logcat，只留内存 120 条）。
- 菜单状态行（GameOverlay，标题下 9sp 灰字最多 3 行，每秒刷新，只内存）：WorldWatch.shortText() = 世界地址 地图短名 状态（第 N 个、地图名来源）；模式；15001 解析原值 → 编号；最近触发；服务端开/关。
- 未实测/不确定：maps_chose 实际值（按旧日志应是 1/3/4/5）；Room.mine 残留（以前建过游戏房间没退）时 15001 会走 Room 房主开房；WorldWatch GWorld 偏移仍未上机验证（状态行能直接看出来）。
- 构建：/tmp/b.sh（build_all.sh 的 JDK 换 jdk-21.0.12.1+1、分两次跑，仓库脚本没改）4.08 4.09 309 310，core/b0-2.ver=310；releases 加 v4.09 三个（v4.06 已不在仓库，v4.08 留着）。

## v4.10（core311，2026-11-22 20:5x，全部未实测）训练岛不死 + 刷车安全落点
- 用户 20:39（v4.09 实测，荣耀 X70 单机，5 图）：训练岛入口好了（状态行 15001 maps_chose=[5] → 5，BR-TrainingIsland_Mobile_Main，服务端 开）✅。问题：①刷车按「三轮摩托」提示「刷车：没反应 三轮摩托（编号 16，补丁代码没反应）」，旁边却出来一辆摩托；②训练岛「你因载具爆炸而被淘汰」（截图人旁边一辆烧着的摩托），还会淹死。
- 「补丁代码没反应」根因（Veh.place → spawnAt → call）：call() 只等 2 秒（40×50ms）cave 的 0x5b03。cave 进入时写 0x5b01，在游戏线程同步调 PC::SpawnVehicle 0x1102ca4 → 0x100600c（FindRow → SpawnActor 0x2aa8ad4），第一次刷某种车要同步加载蓝图/模型，超过 2 秒就误报，车稍后才出来；晚到的 0x5b03 还会被下一次调用当成自己的结果。编号 16 本身没问题的证据只有「旁边出来一辆车」（是不是三轮没看清，未确认）。
- 叠车/炸车（推断，未上机证实）：v4.07 删日志后 place() 仍在等日志「Spawn Vehicle」（spawnOk/spawnFail 不再变）→ 每次白等 3 秒 + 整表扫 GUObjectArray 找新车（十几秒），找不到就在 7 米外左右 4.5 米再刷，一次按键最多 4 辆，车叠在一起被物理弹开受伤爆炸；落点只看一个点高度，不看车身/坡/别的车/人，船按人物高度刷。
- 训练岛还会死的根因（静态 RE）：
  - 人物扣血在 0xbe0618（人物 TakeDamage 0xd2230c 在 0xd2270c/0xd22b00 调），两处 ldr/sub/str [+0x5a0]（0xbe0e48、0xbe0ed0）；GM vt+0x484（0xbe0a84，MinHp 钩子所在链）只在 GameMode 类检查通过时才调 → MinHp 不是所有伤害都挡。
  - MinHp 指向 Mods.livePawn() = PC+0x2c0 Pawn，人在车上时可能是车（vcave 'V0' 的 buf+8 就是 PC+0x2c0）。
  - MinHp/NoFall 只在 Island.run 找到 GameMode/地块管理器/出生点后才启动，前面 return 就不启动。
  - 淹死：0xd028a0（人物憋气）：水下 +0xc30 CurrentBreathHoldTime += +0xc40 DeduceBreathTimeScale×dt，夹到 +0xc34 MaxBreathHoldTime；到上限置 +0xc44，+0xc48 每满 1 秒在权威端调 TakeDamage(vt+0x2a0)，伤害 = HealthMax(+0x5a4 int) × +0xc38。出水 +0xc30 按 +0xc3c 减回 0。（反射表：MaxBreathHoldTime 0x4bfcb04、CurrentBreathHoldTime 0x4bfcb2c）
- 改：
  1. TrainSafe（新，单机/服务端，客户端不跑）：Con.onLoaded 进训练岛立即启动，同时启动 NoFall/MinHp（不再等 Island.run）。每 1 秒认自己的人物（PC Pawn 且 NoFall.isChar；在车上保留上车前的人物），房主加 NoFall.pawns 里有 PlayerState(+0x2c4) 的人物。每 0.1 秒：血(int) < 上限×10 → 写 上限×20；关 bCanBeDamaged（+0x110 bit1）；憋气 +0xc30/+0xc44/+0xc48 清零（+0xc34 是 0..10000 才写）；记下的刷出来的车血补满（NZVehicle MaxHealth +0x2f8 / Health +0x308，int）。状态行加「保命 开 补血N 憋气N 车N」。
  2. MinHp 改用 TrainSafe.me()（人物，不是车）。
  3. Veh：call(k,id,timeout)，刷车等 10 秒；超时看标记（0 = 游戏没执行；0x5b01 = 还在加载 →「刷车中…稍等几秒」），并在下次调用前等上次那条出来（staleUntil 15 秒）。删 spawnOk/spawnFail/spawnLog。
  4. Veh.place 重写：按车型车身尺寸（摩托 半长120/半宽50，三轮 140/100，蹦蹦 180/100，吉普/轿车/跑车 260/115，面包 290/120，皮卡/大脚 310/140，快艇 320/130，大船 520/220）；落点离人 2.5 米（在车上 4.5 米）+ 半车长；每个候选点打 5 条射线（中心/头/尾/左/右），要能停车（不是水/树草体积/车/人，法线 Z≥0.85，和脚下差 ≤2 米，5 点高差 ≤1.2 米），最多 7 个候选；菜单刷车 2 分钟内刷过的点附近不再刷（防连按叠车，服务端代刷/排车不用）；刷在 最高点+车型抬高（120~160 厘米）。船：人游泳时刷人前方水面；否则人周围 8 方向 6~25 米找射线打到水（名字含 water/ocean/sea/lake/river）且船头船尾也是水的点；找不到就不刷并提示。人在游泳时不刷车。人在车上（PC Pawn 不是人物）放车两侧/后方。射线用不了时退回旧办法（人物高度+1.5 米，更远）。刷完在落点打射线确认（打到 *vehicle*），确认到就补满车血，训练岛记进 TrainSafe；确认不到也不再自动刷第二辆。满油后台照旧（findNew）。
  5. carCheck：训练岛改成 set NZVehicle bCanBeDamaged 0；去掉 getall（只会让游戏写日志）；Con.setAllGod 去掉 getall。
- 未实测/风险：血写成上限×20 后血条显示可能超出（若 HUD 不夹 0..1）；bCanBeDamaged 位 +0x110 bit1 沿用 v3.26 的 RE；射线是否打得到水面/车身（Visibility 通道）未上机确认——打不到水 → 船刷不出（会提示），打不到车 → 只是提示「射线没确认到车」；人在车上时 PC Pawn 是车还是人未确认（两种都处理了）；`set NZVehicle bCanBeDamaged 0` 控制台在这版是否生效未确认（有 TrainSafe 补血兜底）；房主别的玩家只能靠 NoFall 10 秒一次的扫描找到（沿用旧的扫描，没新增大内存扫描）。
- 注意：本次查车辆类名时误 grep 了仓库里旧的已存日志 game/logs（只取了 BP_Vehicle_ 前缀），以后别碰 game/logs。
- 构建：/tmp/b.sh（build_all.sh 的 JDK 换 jdk-21.0.12.1+1，仓库脚本没改）4.09 4.10 310 311，core/b0-2.ver=311；releases 加 v4.10 三个（留 v4.08/4.09/4.10，没有 v4.07 残留）。

## v4.11（core312，2026-11-22 21:xx，全部未实测）训练营：撤掉 v4.10 补血/无敌 → 让游戏自己的训练营复活规则生效
- 用户 21:02（看了 v4.10 说明）：「666，你这治标不治本的毛病得改」「车炸是因为我拿枪打爆的啊」。= 车是他故意打爆的，车能炸是正常的；补血/无敌是治标。
- 撤掉：TrainSafe.java 整个删（0.1 秒血写上限×20、清 bCanBeDamaged、憋气 +0xc30/+0xc44/+0xc48 清零、刷出来的车补血）；Veh.carCheck 训练岛 `set NZVehicle bCanBeDamaged 0` → 和别的图一样 `bCanBeDamaged 1`；Veh.place 刷完补满车血（repair）删掉。
- 原版训练营怎么处理死亡（只读 pak 明文数据 + libUE4 静态）：
  - BP_NZBRPracticeGameMode CDO（pakchunk_111896…）：SystemNameList = /Script/NZBattleRoyaleGame.PracticeSystem、ResurrectSystem、PickUpRefreshSystem；StateInfoClassList = BP_PracticeInfoState、BP_PracticeResurrectInfoState（CDO 无覆盖属性）、BP_PickUpRefreshInfoState；DefaultPawnClass BP_NZBRCharacter_Practice；PC ReturnToDeathTime 5.0。
  - /Game/DataTables/GameFeatureConfig/ResurrectSystemConfig（pakchunk_7c73d0…，RowStruct ResurrectInfoStateRow）：NewRow ModeId0 Sequence（SecondsArray 60..1320、ResurrectInterval 10、带运输机参数）；BTT 503 Interval 60；GameModeTest 2001；**Practice 600：TriggerType Personal、PersonalDelaySeconds 4.0、PersonalResurrectEndTimes -1、bSpawnPawnImediately True、SpectateProtectTime 3、DelaySeconds 0**。
  - GameModeType 表：SubGameModeType 600 =「训练营地图」，MainGameModeType 2，ServerGameModeType 4。GameMapID：5 = 训练营地图。（其它：100 经典、201 快速、403 绝地训练、505 道具、506 道具快速、503 三军、502 五军…）
  - 结论：原版训练营会死，死后 4 秒个人复活、不限次数、3 秒保护 —— 不是「不会死」。
- libUE4 RE：
  - AResurrectInfoState 初始化 0x103c9d0（虚表 0x4c8a9f0 一项）：World+0x98 GameState → vt+0xa0(GetWorld) → 0x104dd7c(世界, "GameFeatureConfig/ResurrectSystemConfig") → [世界+0xc4] = OwningGameInstance（反射 0x5045270 偏移 0xc4）→ NZCommonTable 选行 0x104e058（日志 "[ModeMapping]: Mode=%d, Map=%d, Match=%d, Team=%d"，NZCommonTable.h）：行 +4 ModeId 对 GI+0xe4（相等 0x80，行为 0 得 8），+8 MapId 对 GI+0xdc（0x40/4），+0xc MatchId 对 +0xe8，+0x10 TeamId 对 +0xec；取最高分。行 +0x38 ResurrectParam（0x50 字节）拷到 InfoState+0x2b8（+0x2b8 TriggerType 0 Sequence/1 Interval/2 Personal，+0x2bc Interval，+0x308/+0x30c SecondsArray，+0x340 PersonalResurrectEndTimes）。
  - GI 这几个字的来源：GameInstance 虚函数 0xc74234（虚表 0x4bd536c/0x4c871f8 的 +0x174）：GIsClient(0x5212a65) && URL.IsInternal(0x2e1ed18) 时，从 FURL+0x28（反射：RedirectURL 0x28、Portal 0x40、Protocol 0）用 FParse::Value 0x1a53880 解析 MainModeId=→+0xe0、SubModeId=→+0xe4、MapId=→+0xdc、RoomId=→+0xe8、TeamId=→+0xec（日志 "Sync Client: GameMapId…"）。字符串 0x42ca278..0x42ca310。
  - 复活流程：ResurrectSystem::Tick 0x119feb0（要 InfoState+0x350 = 1，由 OnMatchBegin 0x119f560 置；CanResurrect 0x103c974：Personal 且 EndTimes ≤ 0 → 一直能）→ Personal 0x11a0458（InfoState+0x394 每个 Uin 的倒计时，到 0 → 0xc896fc 按 Uin 找控制器 → 0x11a11d8 → 0x11a1438 重生）。Sequence 走 0x11a07c4 ResurrectAllPlayer（整批）。
  - 根因：我们 open 训练岛不带模式 → GI+0xe4 = 0 → 选到 NewRow（Sequence）→ Practice 行（4 秒个人复活）从没生效。
- 改：
  1. ModeMap.java（新）：Con.exec 发 open /Game/Maps/… 前调 beforeOpen：训练岛 → 记下原值，GI+0xdc=5、+0xe0=2、+0xe4=600；别的图 → 还是我们写的值就改回原值。GI = [GWorld]+0xc4，校验虚表 +0x174 == base+0xc74234。只写这三个 int，不改代码。状态行「模式编号 600 地图 5（训练营规则：死了4秒复活）」。
  2. TrainMode.java（取代 TrainSafe）：进训练岛启动 NoFall/MinHp（这两个是用户 v3.95/3.96 要的「剩一丝血不死」，保留），每秒认人物给 MinHp；不写血/憋气/车。
  3. Veh：删 repair/TrainSafe.addVehicle；carCheck 训练岛不再特殊。v4.10 刷车修复（10 秒、一次一辆、5 点射线落点、船刷水面）不动。
- 未实测/风险：OnMatchBegin 事件在训练营模式会不会来（不来 → +0x350=0 → 不复活）；重生走 Practice ChoosePlayerStart（v3.33 时「level not prepared」崩过，后来 Island 修过空队伍）；MinHp 仍挡大部分伤害 → 实际只有车炸（InVehicleExplosion/VehicleExplosion）和淹水会真死。设 SubModeId 600 后其它按模式选行的表（GameStateCommonConfig 无 600 行 → Default，不变）。其它模式（经典 100、战术 505…）同样没给模式编号，这版没动。
- pak 读取方式：pak 索引是加密的（footer 前一字节 bEncryptedIndex=1），但文件数据 encrypted=0（只是 zlib）。game/re/pakscan.py 直接扫 zlib 块解压、按 uasset 魔数分段，**不解密任何东西**；dtrows.py 读 DataTable 行。解出来的文件放沙盒项目 work/（不进仓库）。
- 构建：/tmp/b.sh 4.10 4.11 311 312，core/b0-2.ver=312；releases 留 v4.09/4.10/4.11（git rm v4.08）。

## v4.12（core313，2026-11-23 14:5x，全部未实测）训练岛防淹死/炸死 + 大厅闪退诊断
- 用户 14:39：v4.11 服务端在大厅闪退；训练场会淹死和炸死，要修。
- 训练岛：HpFloor.java（新）。人物扣血 0xbe0618 两处：A 0xbe0e48 `ldr r0,[r5,#0x5a0]; sub r0,r0,r1`（a00595e5010040e0）、B 0xbe0ed0 `ldr r1,[r5,#0x5a0]; sub r0,r1,r0`（a01595e5000041e0）→ 各改 `ldr pc,[pc,#-4]; .word cave`。cave 在 SLACK+0x990（A）/+0x9c0（B），各 0x30：原样算新血；[buf]+12 != 0 时 cmp r0,#1; movlt r0,#1；str [r5,#0x5a0]；跳回 A+0xc / B+0xc。buf = MinHp.addr，+12 = 训练岛标志（MinHp 线程进训练岛置 1，离开清 0）。所有人物都挡（训练岛没人机；服务端加入的人也不淹死/炸死）。ip 在两处都是空闲（A 后到 blx r6 不用，B 后到 blx r1 不用）。
- Flow.natInstall 停用：ZS=SLACK+0x990，32+264+76 字节超出 SLACK_END（0xac0），一直「空位被占」，没生效过。
- 大厅闪退：v4.11 上传 upd-1123-143655（启动）、lobbyop-1123-143718（进大厅，RSS 872→837MB 内存换出第 1 轮成功 62 次 650MB 只降 35MB）。之后没有新上传 → 闪退记录等下次启动。14:36:31 那次 kill（v4.05 → 更新到 312 重启）是更新重启。Beat 加 low4g()：低 4GB 已映射总量 + 最大空洞（每 5 拍算一次）。
- 未实测/风险：车炸如果是直接判死（不走扣血）挡不住；血从 0 以下被抬成 1 的瞬间 HUD 是否正常。
- 构建 build_all.sh 4.11 4.12 312 313；releases 留 v4.10/4.11/4.12（git rm v4.09）。

## v4.15（客户端/单机 core316，2026-11-23 15:0x，未实测）训练场刷车、退出、菜单字
- 用户：不要老回退；客户端训练场刷不了车、退不出来、菜单上面字太多。服务端仍 v4.14（=v3.83 代码，b1=315，没动）。
- 刷车：Veh.place 5 点车位检查全不过时不再直接「没刷」，照 v4.10 以前办法刷在人前方（净空+1 米，落点射线地面 +1.5 米），只有前面是水才不刷。
- 退出：常用页（funcPage）训练场里顶上加「退出训练场，回大厅」（Lan.leave）；Server 默认分支：训练场里任何 15xxx 请求都当退出（模式 600 下游戏退出可能发别的号，推断）。
- 菜单：v4.09 世界状态那行（ww）隐藏；WorldWatch.shortText 去掉 ModeMap；TrainMode 不再每秒读模式编号。

## v4.16（服务端 core317，2026-11-23 16:3x，未实测）
- 用户 16:27「怎么服务端还是闪退」（v4.14 = v3.83 代码）。用户规则：不看日志、不回退。服务端 b1 改为仓库 src（v4.15 代码）编，Con.SRV_MEM 再压：r.Streaming.PoolSize 80、MipBias 2、SkeletalMeshLODBias 1、ViewDistanceScale 0.6；MemTrim.start 直接停用。推断根因：云手机单进程 ~1.0GB 上限（v4.00–4.06 心跳样本），未证实。若还闪：下一步考虑降渲染分辨率（r.MobileContentScaleFactor，需开机前设）。

## v4.17（客户端 core319，2026-11-24 00:2x，未实测）
- 用户 00:19：X70 跑服务端内核（core318 MASK）不闪退 → 云手机问题。要把服务端的刷车（训练台原生刷 12 辆车）给客户端：Island vp 条件加 Ed.BUILD == 2。b2 恢复为客户端内核。

## v4.19（全部三槽 core321，2026-11-24 15:4x，未实测）投掷物 HUD 三处防御修复
- 用户：拿烟雾/震爆等非手雷投掷物时，HUD 显示手雷图标和手雷的捏雷倒计时。用户批准三个嫌疑都做防御修复，不做真机诊断，服务端不能用来测。
- 先查 SetInventoryIdName 第三个调用 0xed0120（函数 0xecfee8）：r4 是 ANZWeaponBase（+0x44c 人物、+0x454 缓存的适配/皮肤挂件），按武器自己的对象名建挂件后把**武器自己的** InventoryIdName 抄给挂件 → 不会把手雷的名字写到别的雷上，排除。
- 倒计时写入者（+0xdd0）：0xef9444/0xefa424/0xefb38c 写 -1.0（停），0xef9ac8 写 开火模式+0xa0（AutoFireDelay）；r4 = 投掷开火模式（反射 0x4c64460 ThrowableType +0x22 ENZThrowSubType、AutoFireDelay +0xa0、OwnerWeapon +0x1c），r5 = 武器 vt+0x430 的人物；条件 ThrowableType-1 ≤ 2。
  pak BP_FireMode_*：手雷 ThrowableType=Frag AutoFireDelay 8.0、震爆 Stun 5.5、烟雾 Smoke 7.0，bAutoCountdownFireTime 三个都是 1。
  → **原生本意：手雷/震爆/烟雾都显示倒计时，各用自己的时长**（不是「只有手雷」）。所以这版不屏蔽非手雷，只保证倒计时来自手上那颗。若用户要非手雷不显示：cave 里 `cmp r0,#2` 改 `cmp r0,#0`。
- 管理器：HUD+0x120 = 人物+0x838（0xcc8274）。GetLastThrowWeapon 用 vt+0x388 = +0x55c；+0x55c 只由 WeaponManager 换枪（0xe2b798/0xe2c1e4 存 +0x2d8 后）调 SetCurrentWeapon（vt+0x414 = 0xfe91f4）顺带写，管理器取不到就不写。管理器+0x390 = 人物（自校验 人物+0x838 == 管理器）。
- IsThrowableWeapon 0xcc93d0：编号 = +0x2cc×10000 + +0x2d0（0xce0280 同算法），unicorn 测 204xxxxxxxx 全为真、其它类为假。InventoryId 由 0xcc82a4 从 InventoryIdName 字符串解析。
- 修（ThrowHud.java，Parabola.matchStart 里装，各版本都装；cave 在死掉的 GM 面板函数里，静态字节，同库相对跳转；写前核对 cave 空位原字节和钩子原字节）：
  1. 管理器+0x558：0xfe90e8 入口 `push {r4,r5,fp,lr}`(30482de9) → `b 0x171e88c`（GM CloseGMPanel，0x84 字节）：人物 WeaponManager(+0x888)+0x2d8 有效、无 PendingKill、IsThrowableWeapon → 存 +0x558 并返回；否则跳回原函数 0xfe90ec。
  2. 图标名：0x17ea2a4 `bl 0x1a8e880`(75910aeb) → `bl 0x171eaf4`：武器 InventoryId 在 [20400000000, 20500000000) 就 FString::Printf(0x1936cfc, L"%llu" 0x48e7e98, 编号) 当图标名，否则照原样 FName::ToString。
  3. 倒计时：0xef9ab8 `ldrb r0,[r4,#0x22]`(2200d4e5) → `b 0x171eb78`：ThrowableType 1..3 且 OwnerWeapon == 人物 WeaponManager+0x2d8 才写 +0xdd0（WeaponManager 为空照原生写），回 0xef9acc。
- 验证：game/re/throwhud_cave.py 生成 + capstone 反汇编；game/re/emu_throwhud.py unicorn 25 个用例全过（图标 7 种编号、倒计时 9 种、GetLastThrowWeapon 7 种 + 原函数接续）。
- 残留风险：图标函数只在武器指针变化时刷新——旧手雷销毁后新雷正好分到同一地址时图标不刷新，这版没处理；cave 字节按 workspace 里的 libUE4 核对，若手机上的库不同会「空位字节不符」不装（不会乱写）。
- 构建：build_all.sh 底包用 v4.18-客户端.apk 临时复制成 v4.18-单机.apk（构建后删，不提交）；4.18 4.19 320 321，三槽 b0/b1/b2 = 321（dex 里 CORE_VER 321、BUILD 0/1/2、MASK=false）。releases 留 v4.17/4.18/4.19（git rm v4.16），v4.19 只发客户端、服务端。

- 11-24 15:4x v4.20（core322）：用户要求回退 v4.19 → 源码回到 v4.18（db52414），删 ThrowHud。三槽 322。投掷物图标/倒计时未修。
