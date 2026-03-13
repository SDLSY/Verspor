# VesperO / new start

VesperO 是一个面向睡眠、恢复与日内干预场景的端云协同项目。当前仓库的核心主线是：

`统一画像 -> 处方 -> 执行 -> 反馈`

项目目标不是做“更多监测图表”，而是把睡眠、恢复、压力与执行历史收敛成一条可验证的干预闭环。

## 当前仓库状态

- Android 已从单模块演进为多模块结构，当前运行入口是 `:app-shell`。
- Android 运行时代码现已统一收敛到 `app-shell` 及各 `core-*` 模块，不再通过 `sourceSets` 编译 `app/`。
- `core-*` / `feature-*` 模块已经建立边界，但业务迁移尚未完全完成。
- 云端主线位于 `cloud-next/`，采用 Next.js Route Handlers + Supabase。
- 契约层位于 `contracts/`，用于统一 Android / Cloud / ML 之间的数据结构。

## 当前 AI 主线

- 端侧实时层：
  - `SleepAnomalyDetector` 负责本地异常检测
  - 本地规则回退持续保留
  - 本地 OCR 与本地干预草案继续作为离线兜底
- 云侧生成层：
  - `cloud-next` 现已支持 `Vector Engine -> OpenRouter -> DeepSeek` 的 provider 优先级
  - 当前已落地在处方生成链路，后续会扩展到医生问诊、医检增强、语音与多模态任务
- 策略编排层：
  - Android UI 已开始通过服务层访问 AI 能力，不再直接依赖具体模型客户端
  - `contracts/` 已增加 AI provider / capability / logical model 类型，用于后续端云对齐

## 仓库结构

| 路径 | 作用 | 当前状态 |
| --- | --- | --- |
| `app-shell/` | Android 运行入口与构建壳层 | 当前唯一运行入口 |
| `app/` | 已归档的旧 Android 业务源码 | 不再参与 APK 构建 |
| `core-model/` | 跨模块核心模型 | 已有真实内容 |
| `core-common/` | 公共日志与基础能力 | 已有真实内容 |
| `core-data/` | 核心仓库接口与实现雏形 | 已开始抽离 |
| `core-ble/` `core-db/` | BLE 与数据库基础能力 | 已接入当前构建 |
| `core-network/` `core-ml/` | 网络与端侧 AI 基础能力 | 迁移中 |
| `feature-*/` | 业务特性模块 | 多数仍为占位骨架 |
| `cloud-next/` | 云端 API、推理编排、管理接口 | 持续演进中 |
| `contracts/` | v2 契约与 schema | 已建立 |
| `ml/` | 训练、导出、推理脚本 | 可独立维护 |
| `docs/` | 项目文档与历史材料 | 已整理为索引化维护 |

## 快速开始

### Android

```powershell
gradlew.bat :app-shell:assembleDebug
gradlew.bat :app-shell:testDebugUnitTest
gradlew.bat :app-shell:lint
```

### Cloud

```powershell
cd cloud-next
npm run dev
npm run build
npm run lint
```

### ML

```powershell
python ml\pipeline\smoke.py
```

## 文档入口

请优先阅读以下文档，而不是直接依赖历史比赛材料：

1. `docs/README.md`：文档索引与阅读顺序
2. `docs/00_项目总览与使用指南.md`：项目总览、主线与运行入口
3. `docs/MODULE_MAP.md`：模块职责与迁移状态
4. `docs/06_技术文档基线_2026-03-05.md`：代码对齐技术基线
5. `docs/TECH_REFACTOR_STATUS.md`：多模块重构进度
6. `docs/AI_RUNTIME_ARCHITECTURE.md`：AI 运行时架构与 provider 策略
7. `docs/DOC_MAINTENANCE.md`：文档维护规则

## 当前主线说明

### 1. 统一画像
由端侧睡眠、健康指标、问诊结果、医检结果、干预历史共同构成当前画像。

### 2. 处方
根据画像生成日内干预 bundle，优先输出可执行协议，而不是纯文本建议。

### 3. 执行
通过干预任务、执行记录与效果回写形成闭环。

### 4. 反馈
次日恢复、执行效果与趋势统计继续回流到画像层。

## 维护原则

- 根 README 只保留“项目是什么、怎么跑、去哪看细节”。
- 详细设计进入 `docs/`，避免 README 继续膨胀。
- 以代码事实为准；比赛材料、阶段总结、模板文档默认视为“历史/展示材料”。
- 所有新增或重构模块，至少补齐模块职责说明或在 `docs/MODULE_MAP.md` 登记。

## 相关说明

- 云端说明：`cloud-next/README.md`
- 云端文档索引：`cloud-next/docs/README.md`
- 契约说明：`contracts/README.md`
- Android 壳层说明：`app-shell/README.md`
- 核心模型说明：`core-model/README.md`
- 数据层说明：`core-data/README.md`
- 代理协作规范：`AGENTS.md`
