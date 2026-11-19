全军出击 — game/pak 累计原始资源清单（四坐标补丁后）
更新：2026-11-19 20:34:12 Asia/Taipei (UTC+8)

本地准备补丁，未访问/clone/push仓库，未上传平台。父任务应用到既有game/pak。
基线为已推送提交9a2d5ed92d31ccde4c8afbb6326a4b2e1e85f53b。补丁ZIP不是全量仓库包：
只带本次四块新增资源与需更新的累计元数据；不重传首批136个资源。
务必先覆盖应用补丁，再在完整仓库树核对SHA256SUMS.tsv；它同时列出原基线与新增文件。

累计已选资源 148 文件/4,548,464 字节；其中首批136文件/1,314,244字节完整保留，
本次新增 12 文件/3,234,220 字节。实际存在的umap/uexp/ubulk均检查并补全，按原路径/文件名保存。
同路径无内容覆盖冲突，本次新资源目标在基线均不存在。
单文件阈值50,000,000字节，超过即跳过并在全清单标skipped_over_50MB；本次超限0。

本次明确授权四坐标：
- Heightmap_x2_y0: .umap 8803字节 selected, .uexp 740450字节 selected, .ubulk 65536字节 selected
- Heightmap_x2_y1: .umap 8803字节 selected, .uexp 727437字节 selected, .ubulk 65536字节 selected
- Heightmap_x0_y2: .umap 8782字节 selected, .uexp 740327字节 selected, .ubulk 65536字节 selected
- Heightmap_x1_y2: .umap 8803字节 selected, .uexp 728671字节 selected, .ubulk 65536字节 selected

范围解释：
此前“第9块”编号的唯一性仍未被证明，但用户现在明确要求四个候选全部传，所以不再因编号歧义阻塞。
不把四坐标重命名为9，不声称它们都是同一个第9块，也不修改原始资源内容。
保留Heightmap_x2_y0、x2_y1、x0_y2、x1_y2原坐标文件名与原PUBGM/Content/...目录。
这是用户明确指定的Heightmap例外；不扩展其余12块Heightmap或模型/贴图/其他依赖。
_audit/tile9-candidates.tsv保留原16格坐标及Main名字表引用的历史依据，新增user_authorized与当前状态。

首批范围与状态：
PracticeGameMode、同目录Practice PlayerController、Main/AlwaysLoad七关卡、训练岛有真实引用链的两个刷新蓝图、
27对Grenade/Trajectory相关逻辑蓝图及60个Config ini均保留原selected-files.tsv记录和原SHA256。
Practice相关GameState蓝图仍未在同目录找到，未用PlayerState替代；该说明和未命中审计继续保留。
VehicleSpawnPoint对象实际类为Actor，不虚构独立车辆刷新蓝图；原刷新引用及类型筛选审计未删改。
完整pak-files.tsv仍恰好83,436条，保留所有来源字段；原136 selected不丢，本次12成员由歧义候选转为selected。
清单中not_selected仍只是未导出条目；不是声称全83436资源都已解压/提交。
原本地完整索引文件/workspace/so-inspection/pak-files.tsv没有修改。

元数据与验证：
selected-files.tsv为累计映射；SHA256SUMS.tsv为覆盖应用补丁后的完整逻辑game/pak树校验，校验表自身除外。
来源PAK索引SHA1重新验证；新增每文件PAK条目/内容SHA1、解压长度、导出SHA256全部核对；
首批151项已有文件基线校验也已重验。ZIP生成后全量CRC与每个补丁文件逐项读取SHA256核对。
本次失败0，超限0，缺成员0；剩余未命中事项只有PracticeGameState。
不含密钥、解析器、APK或.so；不执行游戏。各资源字节原样，cooked资源不等于源码或完整编辑器工程。
