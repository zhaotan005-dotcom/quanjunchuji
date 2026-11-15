# libUE4.so (armeabi-v7a) static RE: offline-build gating report

Scope: `x/lib/armeabi-v7a/libUE4.so` only. The .pak/.obb files were not touched. Tools and scripts are in `/workspace/work/qjcj/re/`:
- `ue.py`: loader, vaddr↔file mapping, disassembly, exidx function table, xref helpers
- `xref.py` → `xr.pkl`: PC-relative literal xrefs
- `bl.py` → `calls.pkl`: B/BL call graph
- `natives.py` → `natives.json`: 7,870 `{name, exec thunk}` native-function pairs
- `w32.pkl`: every UTF-32 TCHAR string, about 36.8k of them

## 0. Ground facts (these change how offsets must be used)

- **The code is ARM (A32), not Thumb.** Native function pointers are even, and every function inspected decodes as ARM. Patch in ARM mode with 4-byte instructions.
- **File offset ≠ vaddr for data.** From the program headers:
  - LOAD0: file 0x0 → vaddr 0x0, size 0x4a2d528 (text and rodata). Here offset == vaddr.
  - LOAD1: file 0x4a2d528 → vaddr 0x4a2e528, filesz 0x6fcbf8, memsz 0x9a56ec (.data, .data.rel.ro, .got, .bss). Here **vaddr = file + 0x1000**.
  - Smaller segments: init_array is file 0x512a324 → vaddr 0x53d4324; .dynamic is file 0x512b474 → vaddr 0x53d5474.
  - **A runtime patch address is base + vaddr.** The "0x4c2e0d4" in the brief is a file offset; the runtime address is base + 0x4c2f0d4.
- **Native pointers inside data are already final vaddrs.** Relocations are REL / R_ARM_RELATIVE with the addend stored in place.
- **TCHAR is 4 bytes (UTF-32, wchar_t).** Search for wide strings as UTF-32LE, not UTF-16.
- **Reflection uses the 4.19-style UE4CodeGen_Private param structs.** FBoolPropertyParams = `{Type(0xc), NameUTF8, ObjectFlags(0x45), pad, PropertyFlags(u64), ArrayDim, ?, ElementSize, NativeBool, SizeOfOuter, SetBitFunc}`. Native tables are `{const char* name, exec thunk, 0}`, 12-byte entries (a member-function pointer). Thunks end with `ldr r2,[r1,#0x10]; ... b <impl>` or call the implementation through the vtable.
- **Net mode function.** `UWorld::GetNetMode` (InternalGetNetMode) is at 0x2e65f00; veneers to it sit at 0x1097658 and 0xca2c08. It returns NM_Client (3) whenever a NetDriver exists (client-only target behaviour), otherwise derives the mode, which is Standalone (0) offline.

## 1. Doors

- **Owner of the bool.** `StandaloneUnloadAllDoor` belongs to `ANZSceneDoorManager`. Class params are at vaddr 0x4c2f370, StaticClass at 0xe85398, the config section string is "NZSystemSetting", and sizeof is 0x380.
  - Property params are at vaddr 0x4c2f0e8 (file 0x4c2e0e8). PropertyFlags low dword = 0x00004005 (CPF_Edit | CPF_BlueprintVisible | **CPF_Config**).
  - **The real field offset is 0x378.** SetBitFunc 0xe854cc does `strb #1,[r0,#0x378]`; the ctor at 0xce8db0 stores 0 at 0x378. The 0x380 in the struct is SizeOfOuter (sizeof), not the offset.
- **No native code reads this field.**
  - DoorManager functions are BeginPlay 0xce8ea4, Tick 0xce9184, TickDoorOps 0xce91b0, Register/Create/UnRegister/OnToggle at 0xce97cc, 0xce9f48, 0xcea190 and 0xcea2fc, and SetDoorState 0xcea488. None of them touch +0x378.
  - None of the 10 `ldrb #0x378` sites, and none of the `mov #0x378` sites, are door-related.
  - Conclusion: the flag is consumed in Blueprint (BP door manager), which lives in the paks.
- **DoorManager creation/lookup (0xce8a0c).** With NetMode == Client it is fetched from GameState+0x740. Otherwise it is spawned or cached in a global, so it does exist in Standalone.
  - BeginPlay only logs "Should never spawn ANZSceneDoorManager On Server!" when NetMode == 1 (dedicated).
  - Tick returns early only when NetMode == 1.
  - None of this blocks Standalone.
- **Door visibility goes through the GridMap.** Doors are registered into the server GridMap ("RegisterDoor to GridMap", 0xc7c7b4 / 0xc7b7b8) and synced to clients with `ClientSyncBatchDoorData` / `ClientSyncSingleDoorData`. The same pipeline as loot (see §2).
- **Opening doors.** `ServerToggleDoor` thunk 0xe76ce4 calls vtable+0x754 (Validate), then vtable+0x758 (Implementation); offsets are on the owning controller/character. In Standalone the RPC runs locally. I did not locate the _Implementation body, so I can't say whether it requires GridMap/door-manager state.
- `ToggleDoor` (cheat), `SpawnPickupBox` and `TransportBot` are empty natives: the thunk tail-calls `bx lr` at 0xb63a30.
- **Patch.** See patches.json: clear CPF_Config at vaddr **0x4c2f0f8** (`05400000` → `05000000`). Confidence low-medium, because the reader is in BP.

## 2. Loot / weapons (the common pipeline also explains vehicles and doors)

- **What the brief's leads actually are.**
  - `StartSpawnPickupsTimer` (thunk 0x11c0d60 → impl 0xfd67a8) belongs to the **air-drop box** class, not world loot.
  - `DoItemGeneration` is a **BlueprintImplementableEvent** (FunctionFlags 0x48420800) on an EQS-style generator, so its logic is in BP.
- **World-loot pipeline found in native code.**
  1. **Spawning the PickupManager.** The BR GameMode ctor (0x1007b7c) stores manager classes: PickupManager class at +0x938, VehicleManager class at +0x91c.
     - A BR GameMode virtual, 0x100888c (also logs "ANZSceneEnvironmentActor Spawn Succeed!"), spawns them **unconditionally**: PickupManager → GameMode+0x90c, VehicleManager → +0x914.
     - No net-mode gate here.
  2. **Registering spawn points.** `UNZBRPickupConfigComponent::BeginPlay` (0x10c0b5c) proceeds only if:
     - World+0x6d8 (WorldType) ∈ {Game=1, PIE=3}
     - and NetMode ∈ {Standalone=0, Dedicated=1}
     - and AuthGameMode (World+0x94) IsA `ANZBattleRoyaleGameMode` (StaticClass 0x11bd4ec)
     - and that GameMode's PickupManager (+0x90c) is non-null

     It then calls PickupManager::AddSpawnComp (0xff1e88). **Standalone passes the net-mode test**, so nothing to flip here.
  3. **Spawning.** 0xff1e88 arms a timer → 0xff2498 → 0xff30ec ("[Pickup] SPAWN Started, Stage=…"). Items are written into the **GridMap**. If the GridMap does not exist they are cached ("SPAWN GridMap is not exist, Cache Spawn Point…", 0x10c0fbc).
  4. **Making them visible.** Pickup actors on screen are created by **UNZBRClientPickupMgr** from `ClientSyncBatchItems` RPCs ("[ClientPickupMgr] add a new normal pickup").
     - The per-player sync runs from `ANZPlayerController::Tick` (0xd6e71c → 0xd6eb18 → 0xc9b0d8 op dispatcher).
     - Players are put into grid cells by 0xc97530.
     - Registration of the player in the grid also appears on the **PreLogin/Login path** (0x1009890, "[johnnyrao][PreLogin]…", and 0x10fdc54 "Player[%llu] Relogin[%d], Sync:%d").
     - In UE Standalone, `PreLogin` is **never called**: the local player goes through `SpawnPlayActor` → `Login` only.
- **GridMap creation is a separate native.** It is `DelayCreateGripMap` (thunk 0xe5c754 → 0xc7993c), called from BP.
  - It checks `this+0x508`, a GameMode bool (likely bEnableGridMap / IsGridMapEnabled), and `this+0x524`.
  - Otherwise it logs "MapName=%s is not BattleRoyaleMap, and not run in Editor, don't create GridMap!".
  - Related: "SceneItemManager UpdateItem Server NZGameMode IsGridMapEnabled is false" (0xcf920c).
- **Most likely root cause for loot, vehicles and doors together.** Either no GridMap was created (map-name/bool gate in 0xc7993c), or the local player is never registered as a grid "player" because PreLogin doesn't run in Standalone. In both cases nothing is synced to UNZBRClientPickupMgr / the door/vehicle client managers.
  - This is an inference from the code structure and has **not** been confirmed at runtime.
  - **Next step (cheap):** run the game with logcat. The lib logs every one of these branches with distinct strings ("[Pickup] SPAWN GridMap is not exist…", "don't create GridMap!", "Grid%u player[…] RpcSync…", "[ClientPickupMgr] …", "[VehicleSpawn] …"). One log tells us which gate fails.
- **No byte patch proposed yet.** The candidate (the `+0x508` test at 0xc799a0, `ldrb r0,[r4,#0x508]; cmp; beq`) needs the runtime log to confirm it is the failing gate. Forcing GridMap creation without player registration would not help.

## 3. AI bots

- `bUseAIStartFireInStandalone` sits at offset 0x98 of a small object (SizeOfOuter 0x98). Two param copies, at vaddr 0x4c7a6a0 and 0x4c7a8e0. Flags 0x1 = Edit only, not config. It controls whether AI may *fire* in Standalone, not whether bots spawn.
- Spawning natives are `ANZAISpawnFactory`: SpawnAI thunk 0xf54b68 → 0xf3a784, SpawnAIInArea → 0xf3a434, GetAINumberToSpawn → 0xf3a764. They have no callers in native code, so they are driven from BP or the GameMode.
- **Important limitation found.** In `ARecastNavMesh` serialize (0x2797928) the lib logs "[NavMesh] Cooking Non-Server, don't Serialize NavMesh Data!", with a special case for "BR-Training_Mobile_Main".
  - So the **client-cooked paks contain no navmesh for normal BR maps** (except the training map).
  - Even if bots spawn, they cannot path-find on BR maps. That cannot be fixed with a libUE4 byte patch; it would need runtime navmesh generation, which is a large job.
- No patch proposed.

## 4. Vehicles

- `ASpawnVehicleSystemSystem` and the VehicleManager (GameMode+0x914) spawn vehicles and **add them to the GridMap** ("[VehicleSpawn]RandomSP=… add to GridMap", "bEnableGridMapManageVehicle=%d, but GridMap is not valid" at 0x10025f0 / 0x10044ec).
- Same pipeline and suspected root cause as loot (§2). No separate gate found. No patch yet.

## 5. Sound (Wwise)

Not analysed in the time box. Starting points:
- "UAkGameplayStatics::LoadBanks: Bank Manager unused…" (0x47a6f20)
- "Audiokinetic Audio Device setting bank directory to %s."
- `UNZFrontEndAudioManager::LoadAllBank`, "[NZInventoryManager zhitanglu] LoadBank", Open/CloseMapAudioBank, Global_Bank

Next step: logcat for `LogAkAudio`. A missing bank directory or an AK init failure will show there.

## 6. Training camp → falling into the void

- `OnTrainingModeEntranceBtnClick` (thunk 0x163d164 → 0x1375b68) only handles UI: the "Click Start Game Btn Two Fast" debounce, `%lld_TrainTipsShown`, then the FrontEnd UI controller. **It does not open a map itself.** Training is normally started by the lobby/match server, which sends a travel to a DS.
- Map name present in the lib: **`BR-Training_Mobile_Main`** (navmesh exception). Classes `ANZBRTrainingGameMode`, `ANZBRTrainingGameState`, `ANZBRTrainingPlayerController`, `ANZBRTrainingCharacter` and `ANZBRTrainingResAreaManager` exist. The only full `/Game/Maps/` path string in the lib is the showcase map.
- Offline "open level" paths exist in `UNZOnlineLobby::OnConfirmOffline` (0x123abdc, "open level:%s"), `UNZOnlineLogin::OnConfirmOffline` (0x1245228) and `UNZMatchUIController::OnConfirmOffline` (0x13c0a3c).
- Verdict, inferred and unconfirmed:
  - Falling into the void usually means the persistent level loaded but its landscape/tile **sub-levels did not stream in**.
  - The lib has Tencent grid-level streaming (`ANZGridLevelsBuildConfig`, `bServerUseNewStreamingLoading`, `bServerUseTileCollision` in 0x115bb60 with a net-mode check).
  - A client-cooked pak may also simply lack server-side collision tiles.
  - I can't tell which from the lib alone. The logcat check is for streaming ("SeamlessTravel FlushLevelStreaming", level-load errors) and "Failed to load package".
  - If the packages exist and are not requested, it is patchable. If they are missing, it is not.

## 7. Runtime-plugin anchors

| What | vaddr (base-relative) | Confidence | Notes |
|---|---|---|---|
| **GUObjectArray** | **0x529b660** (.bss) | medium | From the inlined AllocateObjectPool next to "Max UObject count is invalid": `strd Objects,MaxElements → [G+0x10]`, NumElements at G+0x18, MaxObjectsNotConsideredByGC at G+0x8. FUObjectItem is 16 bytes. |
| UWorld::GetNetMode | 0x2e65f00 | high | |
| FFrame::Step (used by all thunks) | 0x1bd0dc4 | likely | |
| FNamePool / ProcessEvent | not located | — | Next step: take a BlueprintImplementableEvent wrapper (e.g. the one using the DoItemGeneration FName static initialised at 0xa78454); its final `blx` through the vtable gives the ProcessEvent slot. |

## 8. Recommended next actions

1. Capture logcat from one offline match. Grep for `Pickup] SPAWN`, `GridMap`, `RpcSync`, `ClientPickupMgr`, `VehicleSpawn`, `DoorManager`, `NavMesh`, `LogAkAudio`, `LogStreaming`. That decides which gate to patch for bugs 1/2/4 and 5/6.
2. If the GridMap is never created: patch the `+0x508` test in 0xc7993c, or set the GameMode bool at runtime.
3. If the GridMap exists but the player isn't registered: call the PreLogin/relogin grid-registration routine (around 0x10fdc54) from the plugin after the local PC spawns.
4. Bots on BR maps likely stay broken because there is no navmesh in the client paks.
