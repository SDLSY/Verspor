# 长庚环模块介绍文档（AI 填充基线）

## 1. 文档目的

本文档用于对长庚环仓库中的关键模块进行逐一说明，帮助后续 AI 在填充项目计划书、开发文档、技术报告、测试报告时，能够：

1. 知道每个模块的职责范围。
2. 知道当前模块是否为真实业务承载层。
3. 知道每个模块中有哪些关键页面、类和服务。
4. 避免把占位壳层写成完全成熟模块，或把基础模块写成页面模块。

本文档不会逐行解释代码，而是从模块职责、当前内容、关键文件、模块间依赖、可如何写入正式材料五个维度给出说明。

## 2. 模块分类方法

当前仓库中的模块大致可分为五类：

1. Android 宿主模块
2. Android 基础能力模块
3. Android 特性模块
4. 云端与契约模块
5. 模型脚本与文档模块

本次重点介绍前三类，并补充 cloud/contracts/ml 的角色。

## 3. Android 宿主模块

### 3.1 `:app-shell`

#### 3.1.1 模块定位

`app-shell` 是当前 Android 唯一运行入口，也是 APK 的实际宿主模块。

它的职责包括：

- 提供 Application
- 提供 MainActivity
- 提供 AndroidManifest
- 负责打包与依赖装配
- 承接顶层导航与宿主级资源
- 承接当前运行时所需的主页面链路

#### 3.1.2 当前状态

- 状态：活跃
- 重要性：最高
- 是否参与构建：是
- 是否为用户实际运行入口：是

#### 3.1.3 关键文件

- `app-shell/src/main/java/com/example/newstart/SleepHealthApp.kt`
- `app-shell/src/main/java/com/example/newstart/MainActivity.kt`
- `app-shell/src/main/AndroidManifest.xml`
- `app-shell/build.gradle.kts`

#### 3.1.4 应如何描述

可写为：

> `app-shell` 是长庚环 Android 端的唯一宿主模块，负责应用启动、顶层导航、全局资源装配与整体运行时承载。

不应写为：

> `app-shell` 只是一个空壳模块。

#### 3.1.5 当前边界提醒

虽然目标架构希望把更多页面逻辑下沉到 `feature-*` 模块，但当前 `app-shell` 仍然是最真实的宿主与运行时承载层，因此技术材料应承认这一点，而不是假装其已完全轻量化。

## 4. Android 基础能力模块

### 4.1 `:core-common`

#### 4.1.1 职责

`core-common` 用于承载跨模块通用能力，主要包括：

- 公共字符串和资源
- 通用 UI 组件
- 导航资源
- 公共 helper 和基础性工具
- 无强业务语义的通用类

#### 4.1.2 当前现实意义

该模块是支撑 `app-shell` 与 feature 模块共享公共视图和资源的重要层，尤其在导航资源和公共字符串方面具有实际作用。

#### 4.1.3 如何写入文档

可写为“公共能力层”或“基础 UI / 通用资源层”。

### 4.2 `:core-model`

#### 4.2.1 职责

`core-model` 用于承载 Android 侧共享数据结构、领域对象和可跨业务复用的模型。

#### 4.2.2 为什么它重要

如果没有该层，页面、仓库、服务之间会反复定义相近的对象结构。`core-model` 的存在说明项目已经有“共享模型统一化”的意识。

#### 4.2.3 如何写入文档

建议写为“核心模型层”或“领域模型层”。

### 4.3 `:core-data`

#### 4.3.1 职责

`core-data` 是当前 Android 数据访问和业务服务抽象的关键模块。它包含多个 Repository 和 AI 相关服务，是业务数据收口的重要层。

#### 4.3.2 当前实际包含的仓库

当前已存在的仓库与服务包括：

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
- `service/ai/*`

#### 4.3.3 可表达的工程事实

可以明确写：

> 项目已经开始通过 Repository 与 Service 层收口业务逻辑，而不是让页面直接承担所有网络、数据库和 AI 调用。

#### 4.3.4 不宜夸大的点

不应写成：

> 所有业务逻辑都已完全收敛至 `core-data`。

因为当前宿主层与特性层中仍存在大量业务逻辑。

### 4.4 `:core-network`

#### 4.4.1 职责

`core-network` 负责 Android 与云端通信的关键基础设施，同时也承接讯飞配置和部分外部服务接入。

#### 4.4.2 当前实际内容

包括：

- `ApiClient`
- `ApiService`
- `CloudSessionStore`
- `PrivacyGuard`
- 讯飞配置与凭据 bootstrap
- 讯飞相关客户端/配置对象

#### 4.4.3 工程价值

该模块说明项目已经将“HTTP API 访问”和“云端连接能力”从页面层抽离到基础层。

### 4.5 `:core-db`

#### 4.5.1 职责

`core-db` 负责本地数据库能力，是 Android 本地数据持久化的核心模块。

#### 4.5.2 当前关键内容

包括：

- `AppDatabase`
- 多组 DAO
- 各业务域 Entity
- 数据库迁移
- TypeConverters

#### 4.5.3 当前业务覆盖面

数据库已经覆盖：

- 睡眠与恢复
- 设备
- 医生问诊
- 症状与评估
- 放松与干预
- 医检报告
- 处方结果

这意味着本地数据层并非临时缓存，而是已形成较完整的业务存储体系。

### 4.6 `:core-ble`

#### 4.6.1 职责

`core-ble` 是设备连接和戒指通信相关能力的基础模块。

#### 4.6.2 当前实际内容

包括：

- `BleManager`
- `BleConnectionManager`
- `CleveringCommandSender`
- `Hi90BCommandBuilder`
- `Hi90BFrameParser`
- `Hi90BWorkParams`
- `DataParser`

#### 4.6.3 应如何描述

可写为：

> `core-ble` 提供智能戒指和外设接入所需的 BLE 扫描、连接、协议编解码与命令发送能力，是设备链路的底层支撑模块。

### 4.7 `:core-ml`

#### 4.7.1 职责

`core-ml` 承担端侧算法、本地模型和解析逻辑，是项目区别于“纯云端应用”的重要技术基础。

#### 4.7.2 当前实际内容

包括：

- `SleepAnomalyDetector`
- `MedicalReportParser`
- `MedicalReportDraftFormatter`
- `EdgeLlmOnDeviceModel`
- `EdgeLlmRagAdvisor`
- `HealthAnalyzers`
- `HrvFallbackEstimator`
- `PpgPeakDetector`
- `RelaxationScorer`
- `TemperatureMapper`

#### 4.7.3 文档写法建议

适合写成：

> `core-ml` 负责端侧健康数据分析、异常检测、报告文本解析与本地 AI 兜底能力，使长庚环在网络不稳定或云端不可用时仍能保留部分核心分析功能。

## 5. Android 特性模块

### 5.1 `:feature-home`

#### 5.1.1 模块职责

首页与晨报特性模块，主要承接用户日常进入 App 后最先看到的状态概览、晨间摘要与快捷入口。

#### 5.1.2 当前关键页面与类

- `MorningReportFragment`
- `MorningReportViewModel`
- `HealthDataFragment`

#### 5.1.3 可如何写入文档

该模块可描述为“首页与状态概览模块”，主要负责今日页、晨报摘要和关键业务链路入口组织。

### 5.2 `:feature-device`

#### 5.2.1 模块职责

设备页模块，负责智能戒指等设备的扫描、连接、状态展示和部分交互逻辑。

#### 5.2.2 当前关键类

- `DeviceFragment`
- `DeviceViewModel`
- `DeviceListAdapter`

#### 5.2.3 模块作用

该模块是系统感知入口的页面层承接模块。它与 `core-ble` 配合，形成“底层 BLE + 页面交互”的完整设备链路。

### 5.3 `:feature-doctor`

#### 5.3.1 模块职责

医生问诊模块，负责 AI 问诊、问诊消息组织、问诊状态推理、语音与数字人相关交互。

#### 5.3.2 当前关键类

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

#### 5.3.3 模块价值

该模块说明项目并非只是聊天问答，而是具有结构化问诊与辅助判断能力，同时与语音和数字人交互结合。

### 5.4 `:feature-relax`

#### 5.4.1 模块职责

这是当前最复杂的业务模块之一，涵盖症状自查、旧放松中心、医检报告分析、呼吸训练、干预中心、执行页与复盘页等能力。

#### 5.4.2 当前关键页面与类

- `SymptomGuideFragment`
- `SymptomGuideViewModel`
- `SymptomDetailBottomSheet`
- `RelaxHubFragment`
- `MedicalReportAnalyzeFragment`
- `BreathingCoachFragment`
- `RelaxReviewFragment`
- `InterventionCenterFragment`
- `InterventionProfileFragment`
- `InterventionSessionFragment`
- `AssessmentBaselineFragment`

#### 5.4.3 模块意义

该模块承接了长庚环从“症状—判断—建议—干预—执行—反馈”链路中的大部分中后段能力，是当前最能体现项目业务闭环特征的特性模块。

#### 5.4.4 文档写法建议

可以将其描述为：

> 以症状自查、康复辅助、医检理解和干预执行为核心的综合特性模块。

### 5.5 `:feature-trend`

#### 5.5.1 模块职责

趋势模块负责展示睡眠、恢复与周期变化，帮助用户从时间维度理解自身状态。

#### 5.5.2 当前关键内容

- `SleepTrendFragment`
- `SleepTrendViewModel`
- `PeriodReportUiModels`

#### 5.5.3 业务定位

趋势页的作用不是提供新数据，而是整合既有数据并从周期和变化角度进行展示。

### 5.6 `:feature-profile`

#### 5.6.1 模块职责

个人中心模块负责账户、资料、设置与项目信息展示。

#### 5.6.2 当前关键页面与类

- `ProfileFragment`
- `CloudAuthFragment`
- `PersonalInfoFragment`
- `NotificationSettingsFragment`
- `DataPrivacyFragment`
- `AboutProjectFragment`
- `ProfileSettingsStore`
- `ProfileAvatarHelper`

#### 5.6.3 当前意义

该模块表明项目已具备较完整的账户与设置体系，而不是只有业务演示页。

## 6. 云端与跨端模块

### 6.1 `cloud-next`

#### 6.1.1 模块职责

云端模块，负责：

- 公开 API
- 认证
- 用户资料
- 睡眠分析
- 问诊 AI
- 医检理解
- 多模态 AI 路由
- 推荐与干预相关云侧能力
- 内部 worker 与模型管理接口

#### 6.1.2 技术栈

- Next.js
- React
- Supabase
- Zod
- Vercel 部署

#### 6.1.3 应如何描述

可写为：

> `cloud-next` 是长庚环的云端 API 与 AI 编排中心，负责认证、数据同步和多模态 AI 能力接入。

### 6.2 `contracts`

#### 6.2.1 模块职责

共享契约模块，负责 Android、Cloud 和 ML 之间的数据结构共识。

#### 6.2.2 当前关键内容

- `schemas/`
- `typescript/`
- `kotlin/`
- AI provider / capability / logical model 相关共享类型

#### 6.2.3 为什么它重要

这个模块是工程规范化的重要标志，说明项目已经不只依赖口头或文档描述，而是在代码层建立跨端契约。

### 6.3 `ml`

#### 6.3.1 模块职责

模型训练、导出与推理脚本模块。

#### 6.3.2 当前价值

它说明项目并不仅仅消费第三方接口，而是有自己的模型研发与验证资产。

## 7. 模块之间的典型依赖关系

### 7.1 宿主与特性模块

- `app-shell` 依赖多个 `feature-*`
- `app-shell` 承担导航与全局交互装配
- 各 feature 承担具体页面与 ViewModel

### 7.2 特性模块与基础模块

典型模式是：

- `feature-device` 依赖 `core-ble`、`core-db`、`core-data`
- `feature-doctor` 依赖 `core-data`、`core-network`、`core-ml`
- `feature-relax` 依赖 `core-data`、`core-ml`、`core-network`、`core-db`
- `feature-profile` 依赖 `core-data`、`core-network`

### 7.3 Android 与云端

Android 通过 `ApiClient + ApiService` 访问 `cloud-next` 提供的 API。

### 7.4 Android 与本地数据库

页面与仓库通过 DAO/Repository 访问 `core-db` 中的 Room 数据。

### 7.5 Android 与本地算法

业务功能通过 `core-ml` 调用本地算法、端侧解析和本地兜底逻辑。

## 8. 当前模块成熟度说明

### 8.1 可视为真实承载层的模块

以下模块可明确写成“已承载真实能力”：

- `app-shell`
- `core-common`
- `core-model`
- `core-data`
- `core-ble`
- `core-network`
- `core-db`
- `core-ml`
- `feature-home`
- `feature-device`
- `feature-doctor`
- `feature-relax`
- `feature-trend`
- `feature-profile`
- `cloud-next`
- `contracts`
- `ml`

### 8.2 对成熟度的更准确说法

但需要注意，更准确的表述应是：

> 上述模块均已有真实内容和实际职责，但整体架构仍处于持续收敛与优化阶段。

这比简单说“全部模块都已完全成熟”更真实。

## 9. 各模块适合在正式材料中的描述模板

### 9.1 宿主模块描述模板

`app-shell` 是长庚环 Android 端的唯一宿主模块，负责应用启动、顶层导航、全局资源装配和整体运行时承载，是当前单入口架构的核心。

### 9.2 基础模块描述模板

`core-*` 模块用于承接 BLE、数据库、网络、共享模型、本地算法等跨页面、跨业务的基础能力，为 Android 端的多业务链路提供统一底座。

### 9.3 特性模块描述模板

`feature-*` 模块围绕首页、设备、医生、症状与干预、趋势、个人中心等业务领域进行页面与逻辑划分，使系统在功能层面具备清晰的模块边界。

### 9.4 云端模块描述模板

`cloud-next` 是项目的云端 API 与 AI 编排中心，负责认证、数据同步、问诊增强、报告理解与多模态任务路由。

### 9.5 契约与模型模块描述模板

`contracts` 模块用于维护 Android、Cloud 和 ML 之间的共享数据结构，`ml` 模块用于模型训练、导出和脚本级验证，是项目跨端一致性和算法研发的重要支撑。

## 10. AI 在使用本文档时应遵守的约束

1. 不要把占位或规划中的内容写成已全部完成。
2. 不要把“辅助诊断”“辅助理解”写成医学确诊。
3. 不要把所有 feature 模块夸大成完全独立于宿主的成熟插件化模块。
4. 不要省略 `cloud-next`、`contracts`、`ml` 这三部分，否则会低估项目复杂度。
5. 不要忽略 `core-ble` 与 `core-ml`，它们是项目区别于普通信息展示 App 的关键能力。

## 11. 推荐配套阅读

为了获得更完整的上下文，建议与本文档一起使用：

- `长庚环_AI填充用_项目概览.md`
- `长庚环_AI填充用_技术文档.md`
- `README.md`
- `docs/10_项目事实与架构/MODULE_MAP.md`
- `cloud-next/README.md`
- `contracts/README.md`


