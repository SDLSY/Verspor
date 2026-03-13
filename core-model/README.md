# core-model

`core-model` 承载跨模块复用的数据模型，是 Android 侧当前最稳定的核心模块之一。

## 当前定位

- Gradle 模块：`:core-model`
- 类型：Android Library
- 当前状态：已落地，已有真实源码

## 当前内容

按源码目录看，当前主要分成两类：

### `com.example.newstart.core.model`

承载更偏主线和跨层传递的模型：

- `ApiEnvelope.kt`
- `InterventionModels.kt`

这部分更接近统一画像、处方、执行闭环里的共享对象。

### `com.example.newstart.data`

承载端侧已有业务数据对象：

- `ActionAdvice.kt`
- `DeviceInfo.kt`
- `HealthData.kt`
- `HealthMetrics.kt`
- `RecoveryScore.kt`
- `SleepData.kt`
- `SleepStage.kt`

## 适合放什么

- 会被多个模块复用的数据类
- 稳定的 DTO / 领域模型
- 与执行闭环直接相关的共享对象

## 不适合放什么

- 页面状态
- 仓库实现
- Android UI 逻辑
- 只在单页面内部使用的临时结构

## 维护约定

- 新增模型时，先判断是否应该直接进入 `contracts/` 作为跨端契约。
- `core-model` 内的数据结构应保持尽量稳定，避免频繁被 UI 需求牵着改。
- 若一个结构只在单一 feature 内使用，优先放到对应 feature，而不是塞进 `core-model`。

相关文档：

- `contracts/README.md`
- `docs/MODULE_MAP.md`
- `docs/06_技术文档基线_2026-03-05.md`
