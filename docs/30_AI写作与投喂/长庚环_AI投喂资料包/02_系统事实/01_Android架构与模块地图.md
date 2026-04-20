# Android架构与模块地图

- 适用任务：技术架构说明、Android 端源码导览、模块边界说明、开发协作上下文补全。
- 推荐阅读优先级：最高。
- 是否允许对外直接引用：可以转写后引用。
- 最后核对日期：2026-03-15。

## Android 端的一句话事实

当前 Android 端不是旧单体 `app/`，而是以 `:app-shell` 为唯一运行入口、以 `core-* + feature-*` 为主体的模块化应用。

## 现行运行入口

- `:app-shell` 是唯一 `application` 壳模块。
- 应用级入口是 `SleepHealthApp`。
- 主界面入口是 `MainActivity`。
- 主导航由 `core-common` 中的导航图和底部导航承接。
- 当前运行时真正展示的业务页面，主要仍位于各个 `feature-*` 模块中的 `com.example.newstart.ui.*` 包下。

## 当前 Gradle 模块

- `:app-shell`
- `:core-common`
- `:core-model`
- `:core-data`
- `:core-ble`
- `:core-network`
- `:core-db`
- `:core-ml`
- `:feature-home`
- `:feature-device`
- `:feature-doctor`
- `:feature-relax`
- `:feature-trend`
- `:feature-profile`

## 模块职责地图

### `:app-shell`

- Android 运行时壳层。
- 负责 `Application` 初始化、主 Activity、底部导航、全局桌面机器人、页面讲解入口、后台服务接入。
- 是 BLE 连接生命周期、桌面机器人悬浮层、全局页面切换协调的上层容器。

### `:core-common`

- 共享导航图、主题、颜色、通用卡片、图表样式、自定义控件、页面资源。
- 共享字符串、图标、原始音频资源也主要集中在这里。
- 这是 Android 端的视觉与 UI 共享层，不只是“资源包”。

### `:core-model`

- 领域模型层。
- 承载睡眠、恢复分、干预协议、药物/饮食记录、干预体验 metadata 等核心数据类与目录定义。
- 是多个 feature 与 repository 之间共享的数据语义来源。

### `:core-db`

- Room 数据库层。
- 当前 `AppDatabase` 版本为 `12`。
- 管理 `sleep_records`、`health_metrics`、`ppg_samples`、`doctor_*`、`medical_*`、`intervention_*`、`prescription_*`、`medication_analysis_records`、`food_analysis_records`、`relax_sessions`、`recovery_scores` 等实体与 DAO。

### `:core-data`

- Repository 与业务编排层。
- 连接本地 DAO、云端接口、本地模型服务。
- 画像、处方、问诊、报告、恢复分、药物分析、饮食分析、音景/禅定/呼吸会话记录都通过这一层汇合。

### `:core-ble`

- 戒指/设备 BLE 协议、扫描、连接、帧解析、命令构造、demo/mock 支撑。
- 这是硬件数据进入 Android 端的底层入口。

### `:core-network`

- 网络层与云会话层。
- 负责 Retrofit/OkHttp API、云登录态、token 刷新、第三方网络客户端接入。
- 当前云端登录修复后的 `refreshToken` 会话续期也在这一层承接。

### `:core-ml`

- 本地模型与本地推理能力。
- 包含 TFLite 异常检测、本地 LLM bridge、部分本地解析与格式化逻辑。
- 这说明项目不是“全云端推理”，而是明确保留本地兜底。

### `:feature-home`

- 今日页与晨报主链路。
- 负责恢复分、睡眠摘要、生理指标、建议卡片和晨报解释。

### `:feature-device`

- 设备扫描、连接、状态、前台采集控制。
- 与 `core-ble` 协同承接戒指数据接入。

### `:feature-doctor`

- 医生问诊页面、结构化问诊、问诊单、问诊说明与部分自动播报入口。

### `:feature-relax`

- 干预中心与放松能力主模块。
- 覆盖症状自查、医检报告、干预执行、呼吸训练、禅定轻交互、音景干预、药物分析、饮食分析、复盘等。

### `:feature-trend`

- 趋势/周报/月报、恢复分与干预效果的周期视图。

### `:feature-profile`

- “我的”与账户设置、偏好、通知、云账户等入口。

## 当前页面与模块的真实接线

很多现行页面沿用了旧包名，而不是使用 `feature.home.*` 这类新包名。这是当前代码的实际形态，不应误写为“所有页面都已完全按模块新命名重构”。

典型现行页面包括：

- `ui.home.MorningReportFragment`
- `ui.doctor.DoctorChatFragment`
- `ui.trend.SleepTrendFragment`
- `ui.device.DeviceFragment`
- `ui.profile.ProfileFragment`
- `ui.relax.SymptomGuideFragment`
- `ui.relax.MedicalReportAnalyzeFragment`
- `ui.relax.MedicationAnalyzeFragment`
- `ui.relax.FoodAnalyzeFragment`
- `ui.relax.BreathingCoachFragment`
- `ui.relax.ZenInteractionFragment`
- `ui.intervention.InterventionCenterFragment`
- `ui.intervention.InterventionSessionFragment`

## 代码组织上的一个重要事实

当前仓库是“物理模块化，包名历史延续”的状态。

这意味着：

- 不能只看包名前缀判断模块归属。
- 不能把 `com.example.newstart.ui.*` 误判为旧单体还在运行。
- 需要以“文件真实所在模块路径”为准，而不是以“包名长得像不像新模块”为准。

## `app/` 旧单体的地位

- `app/` 仍保留历史源码和资源，可作为演进参考。
- `settings.gradle.kts` 不再把 `:app` 作为当前运行入口。
- `:app-shell` 不再通过 `sourceSets` 编译 `app/`。
- 因此，`app/` 应标记为“历史归档”，不是当前 Android 事实入口。

## 当前 Android 端的主要能力闭环

### 感知层

- BLE 戒指连接。
- 采集健康指标与 PPG 数据。
- 写入 Room。

### 分析层

- 今日页恢复分与摘要。
- 医检报告 OCR 与可读报告。
- AI 问诊与问诊单。
- 药物与饮食图像分析。
- 干预画像与处方生成。

### 执行层

- 呼吸训练。
- 音景会话。
- 禅定轻交互。
- 干预执行记录。

### 反馈层

- 今日页恢复分。
- 趋势页周报/月报。
- 复盘页与执行效果。
- 云端后台患者时间线与建议轨迹。

## 对其他 AI 最重要的四条事实

1. Android 唯一运行入口是 `:app-shell`，不是 `app/`。
2. `feature-relax` 不是小模块，而是整个干预与报告理解主场。
3. 当前项目是端云协同架构，本地模型与云端模型并存。
4. 页面包名保留历史命名，不代表仍在运行旧单体。
