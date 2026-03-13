# core-data

`core-data` 是当前“统一画像 -> 处方 -> 执行”主线里数据访问层的抽离起点。

它已经不是空壳，但也还没有完成全面迁移。

## 当前定位

- Gradle 模块：`:core-data`
- 类型：Android Library
- 当前状态：部分落地

## 当前已存在内容

按源码看，目前主要包含干预执行链路的最小仓库抽象：

- `InterventionRepository.kt`
- `InMemoryInterventionRepository.kt`

这说明当前模块已经开始承载“执行记录 / 任务访问”的统一接口，但范围还很小。

## 当前价值

- 给上层 ViewModel 或 use case 提供稳定的数据访问边界
- 让执行链路先脱离具体页面实现
- 为后续把旧 `app/repository/*` 逐步迁入 core 层打基础

## 还没有完成的部分

- 旧 `app/` 中大量 repository 仍未迁入
- 统一画像与统一处方相关仓库还没有完全下沉到本模块
- Room / network / BLE 的跨层组装关系仍在过渡期

## 适合继续迁入的内容

- 统一画像聚合仓库
- 处方生成/持久化仓库
- 执行记录与效果回写仓库
- 面向 feature 的稳定数据接口

## 维护约定

- `core-data` 优先承载接口、聚合逻辑和稳定实现边界。
- 不要把页面状态或 Fragment 级逻辑迁进来。
- 每迁入一块真实仓库，都应同步更新 `docs/MODULE_MAP.md` 和相关技术基线文档。

相关文档：

- `docs/MODULE_MAP.md`
- `docs/TECH_REFACTOR_STATUS.md`
