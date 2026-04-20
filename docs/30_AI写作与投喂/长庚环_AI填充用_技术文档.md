# 长庚环技术文档（AI 填充基线）

## 1. 文档目的与使用方式

本文档用于为后续 AI、技术写作工具、自动文档生成程序提供一份尽量真实、细节充分、面向工程事实的技术基线。本文档的定位不是教学文档，也不是外宣白皮书，而是项目技术结构与运行方式的综合说明。

使用方式建议如下：

1. 如果要生成项目开发文档、技术研究报告、测试文档或技术答辩稿，先读取本文档。
2. 如果要理解项目模块职责，再结合 `长庚环_AI填充用_模块介绍文档.md`。
3. 如果要写偏产品定位和价值表达的材料，再结合 `长庚环_AI填充用_项目概览.md`。

本文档重点回答以下技术问题：

- Android 当前如何构建、运行、导航与分层。
- 云端由哪些框架、接口、认证逻辑组成。
- 本地数据库有哪些核心表与数据对象。
- BLE、网络、AI、OCR、语音、虚拟机器人这些能力如何落在代码中。
- 当前项目有哪些已经实现的技术事实。
- 当前还处于演进或持续收敛阶段的部分有哪些。

## 2. 技术栈总览

### 2.1 Android 技术栈

根据当前 Gradle 配置，Android 运行时模块为 `:app-shell`，主要技术栈包括：

- Android Gradle Plugin：8.7.3
- Kotlin：1.9.24
- Java / Kotlin JVM Target：11
- compileSdk：35
- targetSdk：35
- minSdk：24

主要依赖包括：

- AndroidX 核心组件
- AppCompat / Material
- Navigation
- Lifecycle / LiveData / ViewModel
- Room
- Retrofit / OkHttp / Gson
- Kotlin Coroutines
- DataStore Preferences
- MPAndroidChart
- Nordic BLE
- TensorFlow Lite 2.14
- Google Filament
- Lottie
- Shimmer
- ML Kit 中文文本识别
- Hilt（已在依赖中存在）

从技术路线看，Android 端并不是一个简单的页面壳层，而是一个负责承接真实数据、设备、AI、语音、多页面流程和本地存储的完整客户端。

### 2.2 云端技术栈

云端位于 `cloud-next/`，当前主技术栈包括：

- Next.js 16.1.6
- React 18.3.1
- Supabase SSR
- Supabase JS 2.50.0
- Zod
- Node.js / npm 构建流程

云端主要承担：

- 公共 API
- 认证与用户资料
- AI 路由与 provider 编排
- 睡眠分析任务入口
- 问诊与医检理解增强
- 多模态服务网关

### 2.3 契约与模型层

共享契约与模型相关资产分别位于：

- `contracts/`
- `ml/`

`contracts/` 用于跨 Android / Cloud / ML 的统一类型与 schema。

`ml/` 用于模型训练、导出和推理脚本验证，不是移动端直接运行入口，但会影响算法与模型的研发基础。

## 3. 仓库结构与运行边界

### 3.1 顶层目录职责

当前仓库的核心目录可以概括如下：

#### Android

- `app-shell/`：当前唯一 Android 运行入口与宿主层
- `core-common/`：公共通用能力
- `core-model/`：共享数据模型
- `core-data/`：仓库层与服务层抽象
- `core-ble/`：BLE 基础能力
- `core-network/`：网络、认证、讯飞配置与 API 访问
- `core-db/`：Room 数据库与 DAO / Entity
- `core-ml/`：本地模型、OCR/解析、AI 本地能力
- `feature-home/`：首页与晨报特性
- `feature-device/`：设备特性
- `feature-doctor/`：医生问诊特性
- `feature-relax/`：症状自查、放松、医检、干预相关特性
- `feature-trend/`：趋势特性
- `feature-profile/`：我的页与账户特性

#### 非 Android

- `cloud-next/`：云端 API 与管理面
- `contracts/`：共享契约
- `ml/`：训练、导出、推理脚本
- `docs/`：文档与说明材料

#### 历史归档

- `app/`：旧 Android 业务归档，不再参与 APK 构建

### 3.2 Android 运行边界

当前 Android 已完成单入口收敛：

- 运行时唯一入口：`:app-shell`
- 当前 APK 不再通过 `sourceSets` 编译 `app/`
- `app/` 仅作为历史归档保留

这说明：

1. 用户最终安装使用的是单一 App。
2. 代码层虽然仍在持续模块化收敛，但产品层已经统一。
3. 后续所有技术文档都应默认以 `app-shell` 为 Android 入口。

## 4. Android 构建与运行方式

### 4.1 构建入口

当前标准构建方式为：

```powershell
gradlew.bat :app-shell:assembleDebug
gradlew.bat :app-shell:installDebug
gradlew.bat :app-shell:assembleRelease
```

单元测试与静态检查：

```powershell
gradlew.bat :app-shell:testDebugUnitTest
gradlew.bat :app-shell:lint
```

仪器测试：

```powershell
gradlew.bat :app-shell:connectedDebugAndroidTest
```

### 4.2 Application 与主 Activity

Android 应用启动时的关键入口包括：

- `com.example.newstart.SleepHealthApp`
- `com.example.newstart.MainActivity`

#### SleepHealthApp

应用初始化时会做的关键动作包括：

- 初始化 `DataManager`
- 初始化 `ApiClient`
- 初始化本地模型入口 `EdgeLlmOnDeviceModel`
- 初始化讯飞凭据 `XfyunCredentialBootstrap`
- 进行一定程度的初始数据准备

这说明项目并非等到页面打开后才按需初始化一切，而是在应用层就建立了一部分系统状态和外部能力接入。

#### MainActivity

`MainActivity` 当前承担的职责包括：

- 作为 AppCompatActivity 宿主承接导航
- 绑定底部导航
- 管理桌面机器人 / 全局 Avatar 覆盖层
- 管理页面进入时的机器人说明文案
- 管理机器人播报与本地/云端语音联动

从设计上看，`MainActivity` 不只是空壳 Activity，而是一个综合型宿主层。

### 4.3 导航结构

当前主导航定义于：

- `core-common/src/main/res/navigation/mobile_navigation.xml`

主一级导航包括：

- `navigation_home`
- `navigation_doctor`
- `navigation_trend`
- `navigation_device`
- `navigation_profile`

当前系统二级页面还包括：

- `navigation_cloud_auth`
- `navigation_profile_personal_info`
- `navigation_profile_notifications`
- `navigation_profile_privacy`
- `navigation_profile_about`
- `navigation_relax_hub`
- `navigation_intervention_center`
- `navigation_relax_center_legacy`
- `navigation_breathing_coach`
- `navigation_relax_review`
- `navigation_medical_report_analyze`
- `navigation_assessment_baseline`
- `navigation_intervention_profile`
- `navigation_intervention_session`

从导航结构可见，当前产品虽然底部是五大入口，但内部已经形成较丰富的二级业务链路。

## 5. Android 模块分层说明

### 5.1 app-shell

`app-shell` 当前是宿主模块，也是当前最核心的 Android 构建模块。它承担：

- Application
- MainActivity
- Manifest
- 顶层主题与宿主资源
- 当前运行时装配

虽然项目目标是持续把业务逻辑下沉到 `feature-*` 和 `core-*`，但当前 `app-shell` 仍然是最实际的 Android 承载层。

### 5.2 core-common

`core-common` 负责公共能力和可跨模块复用的基础内容，当前包括：

- 通用 UI 组件
- 日志或公用 helper
- 导航资源
- 一些共享字符串、公共视图与基础设施

### 5.3 core-model

`core-model` 负责共享数据对象与领域模型。它的目标是避免各模块重复定义业务数据结构，使 Android 端的领域对象能在多个模块之间保持一致。

### 5.4 core-data

`core-data` 当前已经不是空壳，而是项目的数据访问与业务服务层核心之一。当前实际包含：

- `SleepRepository`
- `MedicalReportRepository`
- `DoctorConversationRepository`
- `DeviceRepository`
- `AssessmentRepository`
- `InterventionRepository`
- `PrescriptionRepository`
- `CloudAccountRepository`
- `RelaxRepository`
- `NetworkRepository`
- 部分 AI 服务接口与实现

这表明 Android 端的数据层已经开始从“页面直接干很多事”向“Repository 与 Service 收口”演进。

### 5.5 core-network

`core-network` 当前承担：

- `ApiClient`
- `ApiService`
- 认证会话 `CloudSessionStore`
- 隐私守卫 `PrivacyGuard`
- 讯飞配置、凭据 bootstrap 与相关客户端

这是 Android 与云端连接的关键层，也是多个 AI 功能和认证功能的基础层。

### 5.6 core-db

`core-db` 当前承接 Room 数据库体系，包括：

- `AppDatabase`
- DAO
- Entity
- Migration
- Converters

数据库版本当前为 `version = 10`，数据库名为：

`sleep_health_database`

### 5.7 core-ble

`core-ble` 当前已经承接 BLE 真实能力，包括：

- `BleManager`
- `BleConnectionManager`
- `CleveringCommandSender`
- `Hi90BCommandBuilder`
- `Hi90BFrameParser`
- `DataParser`
- `Hi90BWorkParams`

这说明戒指或设备通信并不是随便写在页面层，而是已进入专门基础模块。

### 5.8 core-ml

`core-ml` 当前承接本地算法与端侧 AI 能力，包括：

- `SleepAnomalyDetector`
- `MedicalReportParser`
- `MedicalReportDraftFormatter`
- `EdgeLlmOnDeviceModel`
- `EdgeLlmRagAdvisor`
- `HealthAnalyzers`
- `PpgPeakDetector`
- `RelaxationScorer`
- `HrvFallbackEstimator`
- `TemperatureMapper`

这说明项目中大量“看起来像 AI 的功能”并非都依赖云端，端侧本地算法是实实在在存在的。

### 5.9 feature-home

当前真实承接的内容包括：

- `MorningReportFragment`
- `MorningReportViewModel`
- `HealthDataFragment`

### 5.10 feature-device

当前真实承接的内容包括：

- `DeviceFragment`
- `DeviceListAdapter`
- `DeviceViewModel`

### 5.11 feature-doctor

当前真实承接的内容包括：

- `DoctorChatFragment`
- `DoctorChatViewModel`
- `DoctorChatAdapter`
- `DoctorDecisionEngine`
- `DoctorInferenceEngine`
- `DoctorInquiryModels`
- `DoctorLiveAvatarActivity`
- `DoctorAiService`
- `LocalDoctorRetrievalService`
- `XfyunVirtualHumanController`

### 5.12 feature-relax

当前真实承接的内容最多，覆盖：

- `SymptomGuideFragment`
- `SymptomGuideViewModel`
- `SymptomDetailBottomSheet`
- `RelaxHubFragment`
- `MedicalReportAnalyzeFragment`
- `BreathingCoachFragment`
- `RelaxReviewFragment`
- `InterventionCenterFragment`
- `InterventionSessionFragment`
- `InterventionProfileFragment`
- `AssessmentBaselineFragment`

### 5.13 feature-trend

当前真实承接：

- `SleepTrendFragment`
- `SleepTrendViewModel`
- `PeriodReportUiModels`

### 5.14 feature-profile

当前真实承接：

- `ProfileFragment`
- `CloudAuthFragment`
- `PersonalInfoFragment`
- `NotificationSettingsFragment`
- `DataPrivacyFragment`
- `AboutProjectFragment`
- `ProfileSettingsStore`
- `ProfileAvatarHelper`

## 6. 本地数据库设计概览

### 6.1 数据库基本信息

根据 `AppDatabase.kt`，当前本地数据库：

- 数据库名：`sleep_health_database`
- 版本号：`10`
- 使用 Room
- `exportSchema = false`

### 6.2 当前核心表

数据库当前实体覆盖以下主要数据域：

#### 睡眠与恢复

- `sleep_records`
- `health_metrics`
- `recovery_scores`
- `ppg_samples`

#### 设备

- `devices`

#### 医生问诊

- `doctor_messages`
- `doctor_sessions`
- `doctor_assessments`

#### 评估

- `assessment_sessions`
- `assessment_answers`

#### 放松与干预

- `relax_sessions`
- `intervention_tasks`
- `intervention_executions`
- `intervention_profile_snapshots`

#### 医检报告

- `medical_reports`
- `medical_metrics`

#### 处方

- `prescription_bundles`
- `prescription_items`

### 6.3 数据库结构反映出的业务事实

从实体和迁移可以看到项目当前业务已经形成以下结构化事实：

1. 睡眠与恢复并非单一分数展示，而是有独立记录和样本。
2. 医生问诊具备消息、会话、评估三个层次，不是简单聊天列表。
3. 医检报告分析具备“报告”和“指标”双层结构。
4. 干预系统具备任务、执行和画像快照，而不是只有静态建议。
5. 处方系统已经有 bundle/item 结构，说明项目正在向“结构化推荐输出”收敛。

## 7. 网络与云端接口

### 7.1 Android 默认 API 地址

当前 Android 配置默认指向：

`https://cloud.changgengring.cyou/`

这是当前生产云端统一域名，也是 Android 与云端对接的默认入口。

### 7.2 ApiService 当前覆盖的接口族

根据 `ApiService.kt`，当前 Android 端实际对接的接口包括：

#### 认证与账户

- `POST /api/auth/login`
- `POST /api/auth/register`
- `POST /api/auth/resend-confirmation`
- `POST /api/auth/password-reset`
- `GET /api/user/profile`
- `PUT /api/user/profile`

#### 睡眠与恢复

- `POST /api/sleep/upload`
- `POST /api/sleep/analyze`
- `GET /api/sleep/history`
- `GET /api/recovery/trend`

#### 同步与建议

- `POST /api/sync`
- `POST /api/advice/generate`
- `POST /api/data/upload`

#### 干预与评估

- `POST /api/intervention/task/upsert`
- `POST /api/intervention/execution/upsert`
- `POST /api/assessment/baseline-summary/upsert`
- `GET /api/intervention/effect/trend`

#### 医生与报告

- `POST /api/doctor/inquiry-summary/upsert`
- `POST /api/doctor/turn`
- `POST /api/report/understand`
- `GET /api/report/latest`
- `POST /api/report/metrics/upsert`
- `GET /api/report/period-summary`

#### 多模态 AI

- `POST /api/ai/speech/transcribe`
- `POST /api/ai/speech/synthesize`
- `POST /api/ai/image/generate`
- `POST /api/ai/video/generate`
- `GET /api/ai/video/jobs/{jobId}`

#### 机器人叙事

- `POST /api/avatar/narration`

#### 推荐解释

- `GET /api/recommendation/explanations`
- `GET /api/recommendation/effects`

### 7.3 认证逻辑

当前 Android 认证逻辑不再只是登录/注册两态，而是已经支持：

- 登录
- 注册
- 待邮箱确认状态
- 重发确认邮件
- 忘记密码 / 重置密码

Android 端由 `CloudAuthFragment` 配合 `CloudAccountRepository` 执行认证流程。

当前云端认证背后依赖 Supabase Auth，项目中对邮箱确认和重置密码已经做了流程补全。

### 7.4 忘记密码与确认邮件回跳

当前云端已具备：

- `/auth/confirm`
- `/reset-password`

以及登录页对恢复参数的兜底跳转逻辑。技术上这意味着：

1. 邮件回跳不应再直接卡死到普通登录页。
2. 忘记密码的链路不再只是“发邮件”，而包含了页面承接。

## 8. BLE 与设备通信

### 8.1 当前 BLE 能力位置

BLE 能力主要位于 `core-ble`。

这层当前提供：

- 扫描与连接管理
- 命令构造
- 帧解析
- 参数对象
- 数据解析

### 8.2 设备侧在产品中的角色

项目中的设备页不是孤立存在。戒指侧数据会继续进入：

- 睡眠分析
- 恢复趋势
- 今日页摘要
- 干预建议
- 长期画像

因此在技术文档中，应将 BLE 理解为整条系统链路的前端输入层。

### 8.3 Demo / Mock 能力

`core-ble` 中仍存在：

- `DemoConfig`
- `DemoDataGenerator`
- `MockBleManager`

这说明项目保留了一定测试/演示能力，但不能简单推断“当前正式运行就全部使用 mock 数据”。应更准确地说，项目保留了演示和兜底能力，同时也具备真实 BLE 基础设施。

## 9. AI 与本地算法能力

### 9.1 当前 AI 架构特点

项目 AI 不是单一来源，而是本地算法、端侧模型、本地兜底、讯飞能力与云端 AI 的混合体系。

这是项目的重要技术特征之一。

### 9.2 端侧能力

当前端侧已能确认存在的能力包括：

- 睡眠异常检测
- PPG 峰值检测
- HRV 兜底估计
- 医检报告本地解析
- 可读报告本地草案生成
- 本地干预计划与建议草案
- 本地 LLM 接口与 RAG 辅助器

这意味着：

1. 并非所有 AI 任务都必须依赖云端。
2. 项目在网络不可用或云端失败时具备一定降级能力。

### 9.3 讯飞能力

当前项目中讯飞相关配置和客户端位于 `core-network` 与 `feature-doctor` 中，涉及：

- 语音听写
- 文本转语音
- OCR/智能解析相关配置
- 数字人控制器

项目对讯飞配置的加载方式依赖 Gradle `BuildConfig` 与本地 `local.properties`。这意味着在不同开发环境中，配置是否正确写入构建产物会直接影响功能是否可用。

### 9.4 云端 AI 路由

云端当前统一了以下 AI 路由：

- 医生问诊
- 医检理解
- 语音转写
- 语音合成
- 图像生成
- 视频生成
- 机器人讲解文案

根据 `cloud-next/README.md`，处方生成 provider 当前采用优先级策略，支持：

- `vector_engine`
- `openrouter`
- `deepseek`

这说明项目云端 AI 不是单 provider 强绑定，而是具备一定路由与备援思路。

### 9.5 桌面机器人 / Avatar

当前项目的桌面机器人是一个重要交互层，而不是简单装饰：

- 它在页面切换时可展示提示内容
- 可进行 TTS 播报
- 当前采用“本地预热文案 + 云端生成回复”的组合策略
- `MainActivity` 会在进入页面时驱动机器人说明

这一能力使项目在答辩或展示时具有更强的引导性与产品完成度。

## 10. 关键业务链路技术说明

### 10.1 今日页链路

今日页本质上是一个整合视图。它依赖：

- 本地数据库中的睡眠/恢复数据
- 云端同步结果
- 推荐或建议逻辑
- 干预相关入口

技术上它是多条数据链路汇合的页面，而不是纯本地静态页。

### 10.2 医生问诊链路

医生问诊链路典型包括：

1. 用户输入文本或语音。
2. Android 端进行本地组织或预处理。
3. 调用云端 `/api/doctor/turn`。
4. 云端生成结构化问诊回合结果。
5. Android 展示消息、建议、动作区与相关说明。

### 10.3 医检报告链路

报告链路通常包括：

1. 图片/文档输入。
2. OCR 提取。
3. 本地解析或结构化草案生成。
4. 云端 `/api/report/understand` 增强理解。
5. 将结果转换为用户可读摘要与重点异常说明。

### 10.4 症状自查链路

症状自查链路包括：

1. 用户选择身体视图与症状区域。
2. 填写补充信息。
3. 本地 ViewModel 组织状态。
4. 生成风险与建议结果。
5. 可继续跳入 AI 医生或其他辅助页。

### 10.5 干预与执行链路

干预链路包括：

1. 基于画像与状态生成任务。
2. 任务进入执行页。
3. 记录执行过程与前后状态。
4. 将结果写入本地数据库和可能的云端接口。
5. 趋势页和复盘页使用这些结果回看效果。

## 11. 当前部署与运行环境

### 11.1 Android

Android 当前为单 APK 交付形态，运行入口固定为 `:app-shell`。

### 11.2 云端

云端当前采用：

- Next.js Route Handlers
- Supabase 作为认证与数据底座
- Vercel 部署

当前生产域名已统一到：

`https://cloud.changgengring.cyou/`

### 11.3 邮件链路

当前项目已完成：

- 注册邮件确认
- 重发确认邮件
- 忘记密码邮件
- 对应页面承接

邮件发送基础设施依赖 Supabase Auth + SMTP 配置，当前已接入 Resend。

## 12. 当前工程成熟度与边界

### 12.1 可以明确写为成熟事实的部分

1. Android 已经单入口运行。
2. Cloud / Contracts / ML 三层边界明确存在。
3. Room、BLE、网络、端侧算法、AI 路由都不是空壳。
4. 认证、资料、设备、医生、趋势、症状自查、医检分析、干预执行等主功能已存在于同一软件。

### 12.2 应谨慎表述的部分

1. 多模块架构仍在持续收敛，而非彻底结束。
2. 部分 AI 路由和数字人能力成熟度不应被夸大为完全产品化。
3. 项目是健康辅助与康复辅助系统，而不是临床诊断系统。
4. 项目已具备真实端云链路，但并不等于所有能力都已达到商业化上线成熟度。

## 13. 生成其他技术文档时可直接复用的描述块

### 13.1 技术架构简述

长庚环采用“智能戒指感知 + Android 单入口应用 + 云端 API 与 AI 编排 + 跨端契约层 + 模型脚本层”的端云协同架构。Android 端负责设备连接、页面交互、本地数据库、本地算法与云端接入；云端负责认证、数据同步、AI 路由和结果增强；contracts 提供跨栈统一数据结构；ml 目录承担训练、导出与验证脚本功能。

### 13.2 Android 技术简述

Android 当前唯一运行入口为 `:app-shell`，已不再编译 legacy `app/`。工程采用宿主层 + core 模块 + feature 模块的分层结构，结合 Room、Retrofit、BLE、端侧算法和多页面导航承载完整业务流程。

### 13.3 云端技术简述

云端基于 Next.js Route Handlers 与 Supabase 构建，负责认证、用户资料、睡眠分析、问诊、医检理解、干预数据上报与多模态 AI 路由。生产环境域名已统一到 `cloud.changgengring.cyou`。

## 14. 不建议 AI 默认补写的内容

1. 不要假设存在 iOS 端。
2. 不要假设当前已经完成全部医学验证。
3. 不要假设所有 feature 模块都已经完全独立并彻底清空宿主依赖。
4. 不要假设所有 AI 能力都完全离线可用或完全云端可用。
5. 不要假设所有设备协议都已标准化对外公开。

## 15. 配套文档建议

配合本文档使用的推荐顺序：

1. `长庚环_AI填充用_项目概览.md`
2. `长庚环_AI填充用_技术文档.md`
3. `长庚环_AI填充用_模块介绍文档.md`
4. `README.md`
5. `docs/10_项目事实与架构/MODULE_MAP.md`
6. `cloud-next/README.md`
7. `contracts/README.md`


