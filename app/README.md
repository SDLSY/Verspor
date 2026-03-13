# Legacy Android Archive

`app/` 目录保留为 Android 旧业务实现归档。

当前仓库的唯一 Android 运行入口是 `:app-shell`，APK 构建不再通过 `sourceSets` 引入本目录下的源码、资源或 assets。

保留本目录的目的：

- 作为历史实现参考，方便继续按主题拆分和清理
- 为迁移文档、比赛材料和阶段性比对提供来源

约束：

- 不再向 `app/` 新增运行时代码
- 不再把 `app/` 作为调试或发布构建输入
- 需要改 Android 行为时，优先修改 `app-shell/`、`core-*`、`feature-*`
