全军出击 — game/pak 原始资源提交准备包
准备时间：2026-11-19 20:24:21 Asia/Taipei (UTC+8)

本包仅为本地提取和传输准备；本执行器没有访问/clone/push Git仓库，没有上传外站。
父任务可将ZIP内game/pak/...按原逻辑目录写入目标仓库。资源字节不修改；不是源码或完整工程。
选定来源条目136，唯一目标资源136，完整头+uexp对38，资源字节1,314,244。
所有同stem的uasset/umap、uexp及实际存在的ubulk均检查并补全；缺省不存在的ubulk不伪造。
路径为game/pak/PUBGM/...或game/pak/Engine/...，不以来源PAK作默认顶层；完整来源映射见selected-files.tsv。
单文件超过50,000,000字节保守跳过，配套其他小文件仍可保留并单列缺成员；此批超限0。

组统计（类别可能重叠）：
- PracticeGameMode: 2文件/10,968字节，1资源族，ubulk 0。
- PracticeControllerGameState: 2文件/7,559字节，1资源族，ubulk 0。
- TrainingMaps: 14文件/363,812字节，7资源族，ubulk 0。
- TrainingTile9: 0文件/0字节，0资源族，ubulk 0。
- TrainingSpawnBlueprints: 4文件/5,907字节，2资源族，ubulk 0。
- GrenadeTrajectoryBlueprints: 54文件/124,853字节，27资源族，ubulk 0。
- ConfigINI: 60文件/801,145字节，0资源族，ubulk 0。

训练岛第9块：尚不能唯一确定，未擅自选取。
实际Heightmap以x0..x3/y0..y3构成4x4坐标网格，没有名为block_9或Heightmap_9的关卡，也不把19/90混作9。
Main名字表确实列出16个Heightmap引用，顺序为x优先；其第9个引用为Heightmap_x2_y0，
但“名字表第9项”不证明游戏定义的“第9块”，且0/1起始与行/列优先未获唯一编号依据。
因此仅列四个常见解释候选，不提交任何Heightmap：x2_y0（1起始x优先）、x2_y1（0起始x优先）、
x0_y2（1起始y优先）、x1_y2（0起始y优先）。tile9-candidates.tsv包含全部16格原路径与Main名字表序号。
AlwaysLoad/block.umap是单个无编号关卡，按AlwaysLoad明确范围已经提交，不能冒称它就是第9块。
需要用户指明坐标或明确编号规则后才能补该项。

刷新蓝图：只据7个训练关卡真实导入及其直接蓝图依赖再走一层，不按全游戏PickUp名字抓取。
spawn-blueprint-references.tsv记录源地图、深度、真实导入类/Outer完整路径、索引匹配和选择结果。
名称匹配但落在/Script下的是原生类，不捏造同名uasset。地图Actor名VehicleSpawnPoint仅是名字证据，
map-spawn-named-objects.tsv保留真实类；不据此虚构车辆刷新蓝图或运行时行为。
Grenade/Trajectory基于全索引basename候选及真实导出类过滤；排除模型、纹理、音频、动画和无关HUD/预测。
具体每个候选的类和选择理由见grenade-trajectory-candidates.tsv。

完整清单语义：
game/pak/pak-files.tsv恰好83,436行，保留来源PAK、原内部名、完整逻辑路径、大小和压缩标记，
增加selection/status/skip_reason/destination/SHA256。未入选行不表示已导出；没有全量解压全部资源。
原本地/workspace/so-inspection/pak-files.tsv未改动，本ZIP也不夹带旧批次多余文件。

异常：路径内容冲突0；超50MB0；缺配对/跳过必要成员0；失败0；未命中/歧义2。
同路径相同内容允许去重并保留所有来源；不同内容不静默覆盖，候选存_audit/conflict-candidates供父决定（本次无冲突）。
Main/AlwaysLoad关卡按明确请求保留其本来嵌入对象/引用元数据；未额外展开素材依赖。
配置文件原样保留；禁止把解密密钥、解析器、APK、.so或登录资料混入。

验证：全部交付资源重验来源PAK索引SHA1、磁盘内容SHA1、解压长度、交付SHA256；包内SHA256SUMS.tsv、
独立ZIP全量CRC及逐文件SHA256复核。单个ZIP不超过24MB；若需多包，每包独立可解压，不用字节流split。

本轮刷新蓝图实证：
- LogicLevel导入直接引用 /Game/Blueprints/PracticeGame/BP_PickUpRefreshVolumn.BP_PickUpRefreshVolumn_C，已原样包含其uasset/uexp。
- Main导入PracticeGameMode，再经该蓝图一层导入引用 /Game/Blueprints/BattleRoyaleGame/InfoState/BP_PickUpRefreshInfoState.BP_PickUpRefreshInfoState_C，已包含。
- VehicleSpawnPoint1等12个LogicLevel对象实际类是Actor；没有据此虚构独立VehicleSpawn蓝图。
- 同目录有BP_NZBRPracticePlayerController，已包含；没有Practice相关GameState蓝图头，未用PlayerState代替。
- 第9块4个候选每个均存在umap/uexp/ubulk；候选实际各成员及总大小见tile9-candidates.tsv，均未入选。
