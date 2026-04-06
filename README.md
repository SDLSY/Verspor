# 长庚环 / VesperO

长庚环是一个以智能戒指为感知入口、以 Android 应用为主交互端、以 `cloud-next` 为云侧编排与管理后台的端云协同健康辅助系统。当前仓库的重点不是单点页面演示，而是把设备采集、睡眠与恢复分析、医生问诊、医检报告理解、药物与饮食图片分析、干预执行与回写、趋势复盘，以及桌面机器人讲解串成可运行的业务闭环。

## 当前事实

- 当前唯一 Android 运行入口是 `:app-shell`，它负责 `Application`、`MainActivity`、Manifest、打包配置，以及全局桌面机器人叠层。
- Android 已按 `core-* + feature-*` 拆分为多模块；`app/` 仍保留大量历史源码，但当前不再参与 `:app-shell` 的构建。
- 云端主线位于 `cloud-next/`，使用 Next.js App Router + Route Handlers，配合 Supabase 提供认证、数据写入、AI 编排、演示账号 bootstrap 和管理后台。
- `contracts/` 是跨端契约层，维护 schema、TypeScript 类型和 Kotlin DTO；接口响应遵循 `{ code, message, data, traceId }` 包装。
- `ml/` 保存训练、导出和推理脚本；`tools/` 保存测试取证、图表生成、模型资产处理和演示辅助脚本。
- 根目录 `README.pdf` 是 `README.md` 的分发快照，内容应与本文件保持一致。

## 当前可运行能力

### Android 主流程

- 底部导航当前包含五个一级页面：今日、医生、趋势、设备、我的。
- 今日页由 `MorningReportFragment` 承载，展示恢复分、睡眠摘要、关键生理指标和当日建议。
- 设备页由 `DeviceFragment` 承载，负责 BLE 扫描、连接、断开、工作参数读取和前台采集服务控制。
- 医生页由 `DoctorChatFragment` 承载，支持文本问诊、语音输入、问诊摘要和结果落库。
- 趋势页由 `SleepTrendFragment` 承载，展示睡眠、恢复与周期报告相关视图。
- 我的页由 `ProfileFragment` 及其子页面承载，覆盖云端登录、个人资料、通知、隐私和关于页面。

### 放松 / 报告 / 干预链路

- 症状自查入口是 `SymptomGuideFragment`，配套 2.5D 身体示意、症状选择与风险提示。
- 医检报告理解入口是 `MedicalReportAnalyzeFragment`，支持 OCR 后的可读化整理。
- 药物分析与饮食分析页面分别是 `MedicationAnalyzeFragment` 和 `FoodAnalyzeFragment`。
- 干预链路包含 `InterventionCenterFragment`、`AssessmentBaselineFragment`、`InterventionProfileFragment`、`InterventionSessionFragment`、`BreathingCoachFragment`、`ZenInteractionFragment` 和 `RelaxReviewFragment`。

### 桌面机器人与播报

- `MainActivity` 挂载了全局桌面机器人叠层，支持页面进入讲解、点击讲解和音频播报控制。
- Android 端已接入 3D 角色、桌面讲解文案生成、语音播放控制和本地预热逻辑。
- 云端提供 `/api/avatar/narration`、`/api/ai/speech/transcribe`、`/api/ai/speech/synthesize` 等接口，为讲解和语音能力提供支持。

### 云侧能力

- `cloud-next` 提供用户认证、睡眠上传与分析、医生问诊、报告理解、干预建议、执行回写、多模态接口和内部 worker 路由。
- 云端还包含演示账号 bootstrap、管理后台聚合视图，以及模型激活与任务监控相关接口。
- 代码默认的文本 provider 顺序是 `openrouter -> vector_engine -> deepseek`，可通过环境变量覆盖。
- `Vector Engine` 相关配置已经覆盖文本、结构化视觉、ASR、TTS、图片生成和视频生成等接口。

## 架构概览

### Android 侧

- `:app-shell`
  - 运行宿主，负责打包、Application、MainActivity、资产挂载、AIUI/讯飞原生库引入。
- `:core-common`
  - 公共资源、导航、字符串、日志和通用工具。
- `:core-model`
  - 核心领域模型与共享业务对象。
- `:core-data`
  - Repository、业务编排、云端账户与演示 bootstrap 协调。
- `:core-ble`
  - BLE 连接、协议处理和设备通信。
- `:core-network`
  - Retrofit、API 模型和网络访问层。
- `:core-db`
  - Room 数据库、DAO、实体与迁移。
- `:core-ml`
  - 端侧 AI / ML 能力，包括本地模型加载、异常检测和部分推理辅助。
- `:feature-home`
  - 今日页与晨报主流程。
- `:feature-device`
  - 设备扫描、连接与采集视图。
- `:feature-doctor`
  - 医生问诊、摘要与语音交互。
- `:feature-relax`
  - 症状自查、报告理解、药物/饮食分析、干预执行、呼吸、Zen 和复盘。
- `:feature-trend`
  - 趋势与周期报告。
- `:feature-profile`
  - 我的页、账户、通知、隐私和关于页面。

### Cloud 侧

- Web 框架：Next.js `16.1.6`
- 运行形态：App Router + Route Handlers
- 数据与认证：Supabase JS `2.50.0`
- 类型校验：Zod
- 常见接口组：
  - `/api/auth/*`
  - `/api/sleep/*`
  - `/api/report/*`
  - `/api/doctor/*`
  - `/api/intervention/*`
  - `/api/ai/*`
  - `/api/internal/*`
  - `/api/demo/bootstrap`

### 契约与模型资产

- `contracts/schemas/`：JSON Schema 源。
- `contracts/typescript/`：云侧共享类型。
- `contracts/kotlin/`：Android / core 模块共享 DTO。
- Android 资产目录中已包含 TFLite、GGUF、3D 人体模型、3D 角色模型与讯飞 AIUI 运行时资产。

## 仓库结构

| 路径 | 作用 | 当前状态 |
| --- | --- | --- |
| `app-shell/` | Android 运行入口与宿主层 | 当前主入口 |
| `core-common/` | 公共资源、导航、工具 | 活跃 |
| `core-model/` | 核心模型 | 活跃 |
| `core-data/` | Repository 与编排 | 活跃 |
| `core-ble/` | BLE 协议与连接 | 活跃 |
| `core-network/` | API 与网络层 | 活跃 |
| `core-db/` | Room 数据层 | 活跃 |
| `core-ml/` | 端侧 AI / ML | 活跃 |
| `feature-home/` | 今日页 | 活跃 |
| `feature-device/` | 设备页 | 活跃 |
| `feature-doctor/` | 医生页 | 活跃 |
| `feature-relax/` | 放松 / 报告 / 干预 | 活跃 |
| `feature-trend/` | 趋势页 | 活跃 |
| `feature-profile/` | 我的页 | 活跃 |
| `cloud-next/` | 云端 API、AI 编排、后台 | 活跃 |
| `contracts/` | 跨端契约 | 活跃 |
| `ml/` | 训练、导出、推理脚本 | 辅助 |
| `tools/` | 自动化脚本与取证工具 | 辅助 |
| `app/` | 历史 Android 业务归档 | legacy，不参与当前宿主构建 |
| `docs/` | 技术文档、源码映射与材料 | 文档入口 |
| `test-evidence/` | 测试证据与导出材料 | 交付/取证，不是运行时源码事实层 |

## 快速开始

### Android

```powershell
.\gradlew.bat :app-shell:assembleDebug
.\gradlew.bat :app-shell:installDebug
.\gradlew.bat :app-shell:testDebugUnitTest
.\gradlew.bat :app-shell:connectedDebugAndroidTest
.\gradlew.bat :app-shell:lint
```

发布包构建：

```powershell
.\gradlew.bat :app-shell:assembleRelease
```

说明：

- Release 构建依赖根目录 `keystore.properties`。
- API 基地址通过 `DEBUG_API_BASE_URL` 和 `RELEASE_API_BASE_URL` 注入 `BuildConfig`。
- `app-shell` 会挂载 `Android_aiui_soft_6.7.0001.0007/` 下的讯飞 AIUI 资产与原生库。

### Cloud

```powershell
cd cloud-next
npm install
npm run dev
npm run build
npm run lint
```

### 核心模块单测

```powershell
.\gradlew.bat :core-ble:testDebugUnitTest
.\gradlew.bat :core-network:testDebugUnitTest
.\gradlew.bat :core-db:testDebugUnitTest
.\gradlew.bat :core-data:testDebugUnitTest
.\gradlew.bat :core-ml:testDebugUnitTest
```

## 配置说明

### Android 侧

- `local.properties` 与 Gradle properties 会为 `app-shell` 注入 API 地址、OpenRouter 模型配置以及讯飞相关密钥。
- 代码里保留了大量 `XFYUN_*` 构建字段，用于 IAT、RTASR、RAASR、TTS、OCR、Spark、AIUI 和虚拟人相关能力。
- Debug 与 Release 的 OpenRouter 模型默认值是 `google/gemini-2.5-flash`，但可以通过属性覆盖。

### Cloud 侧

请基于 `cloud-next/.env.example` 配置环境变量。常见分组如下：

- Supabase:
  - `NEXT_PUBLIC_SUPABASE_URL`
  - `NEXT_PUBLIC_SUPABASE_ANON_KEY`
  - `SUPABASE_SERVICE_ROLE_KEY`
- 内部任务与安全：
  - `INTERNAL_WORKER_TOKEN`
  - `CRON_SECRET`
  - `CLOUDFLARE_ORIGIN_SECRET`
- AI provider 顺序：
  - `PRESCRIPTION_PROVIDER_PRIMARY`
  - `PRESCRIPTION_PROVIDER_SECONDARY`
  - `PRESCRIPTION_PROVIDER_TERTIARY`
- Provider 关键项：
  - `VECTOR_ENGINE_*`
  - `OPENROUTER_*`
  - `DEEPSEEK_*`
  - `DOUBAO_TTS_*`

## 推荐阅读入口

按这个顺序理解项目，通常比直接翻历史材料更有效：

1. `README.md`
2. `app-shell/README.md`
3. `cloud-next/README.md`
4. `contracts/README.md`
5. `docs/06_技术文档基线_2026-03-05.md`
6. `docs/00_项目总览与使用指南.md`

## 能力边界

- 当前能证明的是“端云协同睡眠与恢复分析链路可运行”，不是“Android 端已经稳定部署完整五阶段睡眠分期主模型”。
- 当前的 SRM_V2 和干预建议链路是“多源证据整合 + 安全门控 + 表达层生成”的混合建议系统，不应表述为“AI 自动诊断”或“自动处方”。
- `app/`、`test-evidence/`、比赛材料目录和导出快照不是当前运行时真相来源；运行事实应以 `:app-shell`、`cloud-next`、`contracts` 和代码实现为准。
- `README.pdf` 只是本文件的可分发快照。若 README 发生实质更新，PDF 应同步重新生成，而不是继续保留旧快照。

## 维护约定

- 根 README 只承担仓库入口职责：说明项目是什么、如何运行、当前边界是什么、应该先看哪些目录。
- 详细技术说明进入 `app-shell/README.md`、`cloud-next/README.md` 和 `docs/`。
- 演示材料、测试证据、截图和取证导出不应继续混入根 README。
