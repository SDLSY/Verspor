# 模块地图

本文档记录当前仓库的模块职责、真实状态和迁移情况。它的目标不是替代代码，而是告诉维护者：

- 哪个模块已经承载真实能力
- 哪个模块还是迁移壳层
- 新功能应该优先落在哪一层

## 运行入口

当前 Android 运行入口是 `:app-shell`。

当前 APK 已不再通过 `sourceSets` 引入 `app/` 下的旧业务源码，因此：

- 运行时真实行为来自 `app-shell` 与其依赖模块
- `app/` 仅作为 legacy archive 保留
- `core-*` / `feature-*` 仍是持续收敛中的目标结构，不代表已全部承接业务

## Android 模块

| 模块 | 职责 | 当前状态 | 备注 |
| --- | --- | --- | --- |
| `:app-shell` | Android 构建与运行壳层 | 活跃 | 当前唯一运行入口 |
| `app/` | 旧业务实现与资源归档 | 已归档 | 不再参与构建 |
| `:core-common` | 公共日志与基础通用能力 | 已落地 | 已有真实源码 |
| `:core-model` | 核心模型、DTO、数据类 | 已落地 | 已有真实源码 |
| `:core-data` | 核心仓库接口与实现雏形 | 部分落地 | 已有 Intervention 仓库接口 |
| `:core-ble` | BLE 基础能力边界 | 已接入构建 | 旧 BLE 代码已迁入模块 |
| `:core-db` | 数据库基础能力边界 | 已接入构建 | 旧数据库代码已迁入模块 |
| `:core-network` | 网络与云侧 provider 边界 | 迁移中 | 已开始承接 AI provider env 与逻辑模型路由 |
| `:core-ml` | AI / 模型基础能力边界 | 迁移中 | 端侧实时能力仍主要在旧业务层，通过服务层收口 |
| `:feature-home` | 今日 / 首页特性 | 占位 | 新 feature 壳层已建 |
| `:feature-device` | 设备特性 | 占位 | 新 feature 壳层已建 |
| `:feature-doctor` | 医生 / 干预解释特性 | 占位 | 新 feature 壳层已建 |
| `:feature-relax` | 放松 / 执行特性 | 占位 | 新 feature 壳层已建 |
| `:feature-trend` | 趋势特性 | 占位 | 新 feature 壳层已建 |
| `:feature-profile` | 画像/个人中心特性 | 占位 | 新 feature 壳层已建 |

## Cloud / Contracts / ML

| 路径 | 职责 | 当前状态 | 备注 |
| --- | --- | --- | --- |
| `cloud-next/` | 云端 API、推理编排、管理面 | 活跃 | Next.js + Supabase |
| `contracts/` | v2 契约与 schema | 活跃 | Android / Cloud 共用对象 |
| `ml/` | 训练、导出、推理脚本 | 活跃 | 已有 pipeline smoke |

## 当前推荐放置位置

### 新业务逻辑
- 优先放入目标模块结构。
- 如果必须依赖当前运行链路，可先放在 `app/`，但要在迁移计划里登记。

### 新模型/新数据结构
- 优先放 `:core-model` 或 `contracts/`。
- 避免继续在页面层重复定义结构。

### 新 AI 能力
- provider id、capability、logical model id 优先放 `contracts/` 与云端 `lib/ai/*`
- 端侧 UI 不允许直接访问具体模型客户端
- 本地模型、云侧生成、规则回退优先通过服务层或编排层暴露

### 新接口/新链路说明
- Android / Cloud / ML 共识结构优先写到 `contracts/`。
- 技术事实同步更新 `06_技术文档基线_2026-03-05.md`。

## 当前维护风险

1. `feature-*` 已存在，但很多仍是占位，不能默认认为业务已迁移。
2. 大量历史业务虽已摆脱 `app/` 构建链路，但仍集中在 `app-shell`，后续还需继续按模块收敛。
3. 比赛材料和技术文档混在 `docs/` 下，容易把历史叙事当成代码事实。

## 更新规则

以下情况发生时，必须更新本文件：

- 新增或删除 Gradle module
- 某个模块开始承接真实业务
- `app-shell` 与 `app/` 的构建边界再次变化
- 云端或契约层新增独立子域
