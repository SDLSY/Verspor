# 标题
附录 F：legacy `app/` 与现行模块映射

- 适用任务：历史代码追溯、避免误写运行入口、迁移说明
- 阅读优先级：中高
- 是否允许对外直接引用：允许，但必须明确 `app/` 为历史归档

## 1. 这份映射为什么重要

当前仓库最大的理解陷阱之一，是很多现行模块仍保留了旧单体时期的包名和类名，因此如果只看源码路径或只看包名，很容易误把 `app/` 当成现行运行入口。

必须先锁定这个事实：

- `app/`：历史单体归档
- `app-shell + core-* + feature-*`：现行实现

## 2. 一对一映射主线

### 2.1 壳层与入口

旧：

- `app/MainActivity.kt`
- `app/SleepHealthApp.kt`
- `app/service/DataCollectionService.kt`

现：

- `app-shell/MainActivity.kt`
- `app-shell/SleepHealthApp.kt`
- `app-shell/service/DataCollectionService.kt`

### 2.2 数据与领域模型

旧：

- `app/data/*`
- `app/intervention/*`

现：

- `core-model`
- `core-data`

### 2.3 数据库

旧：

- `app/database/*`

现：

- `core-db`

### 2.4 BLE

旧：

- `app/bluetooth/*`
- `app/demo/*`

现：

- `core-ble`

### 2.5 网络

旧：

- `app/network/*`
- `app/xfyun/*`

现：

- `core-network`

### 2.6 本地模型与工具

旧：

- `app/ml/*`
- `app/util/*`
- `app/ai/*`

现：

- `core-ml`
- 部分逻辑分散至 `core-data`、`feature-*`

### 2.7 页面层

旧页面目录大体按原路径迁移到：

- `ui/home` -> `feature-home`
- `ui/device` -> `feature-device`
- `ui/doctor` -> `feature-doctor`
- `ui/relax` / `ui/intervention` -> `feature-relax`
- `ui/trend` -> `feature-trend`
- `ui/profile` -> `feature-profile`

## 3. legacy 仍然有价值的地方

`app/` 当前仍可用于：

- 看旧单体完整业务结构
- 做 current / legacy 对照
- 找迁移前的实现细节
- 解释为什么现行模块中保留了很多旧包名

## 4. legacy 不能被拿来做什么

不能把 `app/` 用于：

- 证明当前 APK 编译链
- 证明当前主导航入口
- 证明当前云端登录和演示链
- 证明当前后台能力

## 5. 写开发文档时的正确写法

推荐：

- “项目由历史单体逐步演进为以 `:app-shell` 为入口的模块化 Android 架构，legacy `app/` 保留为归档与映射参考。”

不推荐：

- “项目当前由 `app/` 模块直接运行。”
- “Android 运行时代码仍主要位于 `app/`。”
