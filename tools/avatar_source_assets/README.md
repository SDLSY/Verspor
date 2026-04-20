# 3D 源资产目录

本目录保存 3D 角色相关的源资产文件，供 `tools/build_vroid_avatar.ps1` 及其他转换、重定向、优化脚本使用。

## 当前资产

- `5422721126842864302.vrm`
- `8590256991748008892.vrm`
- `merged-model.glb`

## 使用规则

- 新的 VRM、GLB 源资产应直接放到这里，不再放到仓库根目录。
- `app-shell/src/main/assets/3d_avatar/` 中的是导出后的运行时资产，不是源文件层。
- 若替换源资产，应同步检查 `tools/build_vroid_avatar.ps1` 与相关元数据文件。
