# 自定义模型 → 游戏套装模组（不用 UE 编辑器，未实测）
模板法：拿游戏 cooked 原文件（30325000000 男连体装，Grok 导出，未加密，unversioned 4.18）当模板，
把 FStaticLODModel（section/索引/顶点 24B[TangentX,TangentZ,Pos,UV half]/蒙皮 8B）换成我们的，其余字节原样；
uasset 只改 export SerialSize 和 BulkDataStartOffset。贴图按原 mip 布局用 ETC1 同尺寸替换。
流程：repose.py（Tripo GLB 41 骨 → 摆到游戏 46 骨 ref pose，按骨段拉伸）→ decim.py（pymeshlab 带 UV 减面，需 libOpenGL）
→ build_mesh.py → tex.py → pak_v4_writer.py 打 *_P.pak（mount ../../../PUBGM/Content/Avatar/Common/WholeBody/30325000000/）。
注意：腾讯 4.18 的 section 仍有 TriangleSorting 字节、cloth 物理数组；LOD 去掉了 adjacency。
