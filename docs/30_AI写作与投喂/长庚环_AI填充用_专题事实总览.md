# 长庚环 AI 填充用专题事实总览

项目名称：长庚环  
英文名称：VesperO  
团队名称：夜巡者

## 1. 文档定位

本文档是长庚环项目面向 AI 的专题事实总览包，用来补充以下三份 AI 基线文档：

- [长庚环_AI填充用_项目概览.md](D:/newstart/docs/30_AI写作与投喂/长庚环_AI填充用_项目概览.md)
- [长庚环_AI填充用_技术文档.md](D:/newstart/docs/30_AI写作与投喂/长庚环_AI填充用_技术文档.md)
- [长庚环_AI填充用_模块介绍文档.md](D:/newstart/docs/30_AI写作与投喂/长庚环_AI填充用_模块介绍文档.md)

它不替代全局文档，而是专门解决四类最容易被 AI 写偏的专题：

1. 讯飞能力专题
2. BLE 与戒指通信专题
3. 推荐模型与证据链专题
4. UI 收敛与桌面机器人专题

本文档只写当前仓库与当前运行链路能够支撑的事实，不把历史材料、提示词模板、研究规划直接写成“已经全面上线”。

## 2. 使用规则

- 先读项目概览与技术文档，再读本文档。
- 本文档比单独的历史专题文档优先级更高。
- 若本文档与代码冲突，以当前运行代码为准。
- 若本文档与历史阶段总结冲突，以当前维护中的工程文档和代码为准。
- 写正式材料时，可直接复用每章末尾的“标准表述”，但不要删除边界说明。

---

## 3. 讯飞能力专题

### 3.1 当前已落地事实

- Android 端已经完成讯飞能力接入层的工程化收口，核心配置与凭据聚合位于：
  - [XfyunConfig.kt](D:/newstart/core-network/src/main/java/com/example/newstart/xfyun/XfyunConfig.kt)
  - [XfyunCredentialBootstrap.kt](D:/newstart/core-network/src/main/java/com/example/newstart/xfyun/XfyunCredentialBootstrap.kt)
- 当前已接入的能力包括：
  - 语音听写 `IAT`
  - 实时语音转写 `RTASR`
  - 录音文件转写 `RAASR`
  - 语音合成 `TTS`
  - OCR 大模型
  - Spark 文本生成
  - AIUI / 数字人相关配置
- 对应客户端实现已经在仓库中存在：
  - [XfyunIatWsClient.kt](D:/newstart/core-network/src/main/java/com/example/newstart/xfyun/speech/XfyunIatWsClient.kt)
  - [XfyunRtasrWsClient.kt](D:/newstart/core-network/src/main/java/com/example/newstart/xfyun/asr/XfyunRtasrWsClient.kt)
  - [XfyunRaasrClient.kt](D:/newstart/core-network/src/main/java/com/example/newstart/xfyun/asr/XfyunRaasrClient.kt)
  - [XfyunTtsWsClient.kt](D:/newstart/core-network/src/main/java/com/example/newstart/xfyun/speech/XfyunTtsWsClient.kt)
  - [XfyunOcrClient.kt](D:/newstart/core-network/src/main/java/com/example/newstart/xfyun/ocr/XfyunOcrClient.kt)
  - [XfyunSparkWsClient.kt](D:/newstart/core-network/src/main/java/com/example/newstart/xfyun/spark/XfyunSparkWsClient.kt)
- 桌面机器人、医生页语音链路和医检报告分析页均已经接入讯飞能力，而不是停留在静态占位。
- 医检报告 OCR 当前主路径已经切到讯飞 OCR，旧 ML Kit 不是主识别链。
- 数字人医生页仍然保留在工程中，控制器与页面入口都真实存在。

### 3.2 当前运行行为

#### 3.2.1 桌面机器人

- 桌面机器人已经从“麦克风短对话入口”收敛为“页面说明与状态播报入口”。
- 当前主要职责是：
  - 页面说明
  - 当前状态提醒
  - 下一步入口引导
  - 文本气泡与 TTS 同步播报
- 页面进入时会优先走本地预热文案，降低等待感。
- 用户点击机器人时，再根据当前页面和状态选择云端生成或本地兜底文案。
- 默认播报音色当前统一为 `x4_lingxiaoyao_em`。

#### 3.2.2 医生页语音

- 医生页保留两类主要语音交互：
  - `开始录音`：一次性语音转写
  - `开始通话`：连续半双工模式
- 为避免 AI 播报回灌为用户输入，当前运行策略是：
  - TTS 播报期间不启用 IAT
  - 播报结束后进入冷却窗口
  - 冷却窗口结束后再恢复监听
- 这条链路强调稳定性与可控性，不追求全双工电话体验。

#### 3.2.3 医检报告 OCR

- 医检报告分析页先进行 OCR，再进行结构化解析与可读摘要生成。
- 云端可用时，报告会继续进入更深层的理解链路。
- 云端不可用或未登录时，Android 端会给出本地可读兜底，不会把 JSON 原样暴露给用户。

#### 3.2.4 数字人

- 数字人医生页仍存在于项目中，依赖 AIUI 与 `avatar_id`。
- 数字人不等于桌面机器人。桌面机器人是页面级导航助手，数字人更接近医生页里的角色化交互能力。

### 3.3 关键代码/文档落点

#### 3.3.1 代码位置

- 讯飞配置与鉴权：
  - [XfyunConfig.kt](D:/newstart/core-network/src/main/java/com/example/newstart/xfyun/XfyunConfig.kt)
  - [XfyunCredentialBootstrap.kt](D:/newstart/core-network/src/main/java/com/example/newstart/xfyun/XfyunCredentialBootstrap.kt)
  - [XfyunAuthSigners.kt](D:/newstart/core-network/src/main/java/com/example/newstart/xfyun/auth/XfyunAuthSigners.kt)
- 桌面机器人：
  - [PageNarrationContext.kt](D:/newstart/app-shell/src/main/java/com/example/newstart/ui/avatar/PageNarrationContext.kt)
  - [DesktopAvatarPromptBuilder.kt](D:/newstart/app-shell/src/main/java/com/example/newstart/ui/avatar/DesktopAvatarPromptBuilder.kt)
  - [DesktopAvatarNarrationService.kt](D:/newstart/app-shell/src/main/java/com/example/newstart/ui/avatar/DesktopAvatarNarrationService.kt)
  - [AvatarSpeechPlaybackController.kt](D:/newstart/app-shell/src/main/java/com/example/newstart/ui/avatar/AvatarSpeechPlaybackController.kt)
- 医生页与数字人：
  - [DoctorChatFragment.kt](D:/newstart/feature-doctor/src/main/java/com/example/newstart/ui/doctor/DoctorChatFragment.kt)
  - [DoctorChatViewModel.kt](D:/newstart/feature-doctor/src/main/java/com/example/newstart/ui/doctor/DoctorChatViewModel.kt)
  - [DoctorLiveAvatarActivity.kt](D:/newstart/feature-doctor/src/main/java/com/example/newstart/ui/doctor/DoctorLiveAvatarActivity.kt)
  - [XfyunVirtualHumanController.kt](D:/newstart/feature-doctor/src/main/java/com/example/newstart/xfyun/virtual/XfyunVirtualHumanController.kt)
- 医检报告 OCR：
  - [MedicalReportAnalyzeFragment.kt](D:/newstart/feature-relax/src/main/java/com/example/newstart/ui/relax/MedicalReportAnalyzeFragment.kt)
  - [MedicalReportAnalyzeViewModel.kt](D:/newstart/feature-relax/src/main/java/com/example/newstart/ui/relax/MedicalReportAnalyzeViewModel.kt)

#### 3.3.2 主事实文档

- [XFYUN_INTEGRATION_MAINTENANCE.md](D:/newstart/docs/20_专题系统说明/XFYUN_INTEGRATION_MAINTENANCE.md)
- [UI_SYSTEM_REFACTOR_BASELINE.md](D:/newstart/docs/20_专题系统说明/UI_SYSTEM_REFACTOR_BASELINE.md)

#### 3.3.3 仅供补充理解、不能直接当成系统事实的文档

- [DESKTOP_AVATAR_SYSTEM_INSTRUCTION.md](D:/newstart/docs/20_专题系统说明/DESKTOP_AVATAR_SYSTEM_INSTRUCTION.md)
- [DOCTOR_LIVE_AVATAR_SYSTEM_INSTRUCTION.md](D:/newstart/docs/20_专题系统说明/DOCTOR_LIVE_AVATAR_SYSTEM_INSTRUCTION.md)

这两份文档主要描述提示词模板和角色行为约束，不等于完整的运行机制本身。

### 3.4 当前边界与不要误写的点

- 不要把桌面机器人写成“自由语音问答入口”。当前它主要是页面说明、状态提醒和下一步引导。
- 不要把数字人页写成整个 Android 端的默认交互入口。数字人页是局部能力，不是全局壳层。
- 不要把提示词模板文档写成“系统已经内置的知识库事实”。模板只是提示词，不是业务数据源。
- 不要写成“全双工连续语音通话已经成熟上线”。当前实际是半双工连续模式。
- 不要写成“讯飞能力不依赖本地配置”。当前仍通过 `local.properties` 和 Gradle 构建注入凭据，不能把凭据写死在代码中。

### 3.5 适合写进正式材料的标准表述

长庚环在 Android 端已经完成讯飞语音、OCR 与文本生成能力的工程化接入。系统并未把大模型直接作为唯一对话入口，而是根据不同场景进行能力分工：桌面机器人负责页面说明与状态播报，医生页负责半双工语音问诊与数字人扩展，医检报告页负责 OCR 识别与报告理解增强。该设计保证了交互体验、风险控制和运行稳定性之间的平衡。

在实现层面，项目已形成统一的讯飞配置聚合、鉴权、WebSocket/HTTP 客户端和 Android 页面接入链路。桌面机器人与医生页均已统一音色与播报策略，并通过冷却窗口和状态控制避免语音回灌问题。对于报告识别场景，系统已将讯飞 OCR 作为主识别能力，并保留本地可读兜底，从而提升了复杂医疗文本的解析能力与可用性。

---

## 4. BLE 与戒指通信专题

### 4.1 当前已落地事实

- 长庚环当前的设备通信主链是自定义 BLE 协议，协议体系以 Hi90B / Clevering 为基础。
- 当前主要 GATT 约定为：
  - 服务 UUID：`0000FFFA-0000-1000-8000-00805F9B34FB`
  - 通知特征：`0000FFFC-0000-1000-8000-00805F9B34FB`
  - 写入特征：`0000FFFB-0000-1000-8000-00805F9B34FB`
- Android 端已经具备：
  - 设备扫描
  - BLE 连接
  - 命令发送
  - 特征通知接收
  - 协议帧解析
  - 工作参数下发
  - 即时任务与周期任务管理
  - 采集数据入库
- 当前能够进入建议链路的设备数据主要包括：
  - 心率
  - 血氧
  - HRV
  - 体温
  - PPG 原始样本
  - 加速度与陀螺仪
  - 由运动传感器估算出的运动强度
- 当前产品策略已经从“PPG 优先”切到“生命体征优先”。

### 4.2 当前运行行为

#### 4.2.1 连接与初始化

- 设备连接后，系统会完成通知使能、时钟同步、电量查询、工作参数写入与回读、历史数据量查询以及数据传输启动。
- 当前推荐初始化流程强调“先稳定链路，再开始连续采集”，而不是一连上设备就请求所有类型数据。

#### 4.2.2 采集策略

- 当前默认周期任务优先关注：
  - 心率与血氧
  - 体温
  - HRV
  - 运动传感器
- 即时任务默认不再以 PPG 作为主入口，运动 Burst 作为额外增强。
- 步数字段虽然在协议层和数据层存在，但当前产品主链并不依赖步数作为首要健康证据。

#### 4.2.3 数据去向

- BLE 采集数据会进入本地数据库与采集服务，随后影响：
  - 晨报恢复分
  - 趋势页
  - 医生页设备快照
  - 症状引导页设备证据
  - 干预画像与每日处方

#### 4.2.4 调试策略

- 当前 Android 端既保留面向用户的设备页，也保留面向联调的高级工具入口。
- 设备页主流程强调“连接状态、同步状态与主动作”，高级排查能力被下沉而不是直接占据首屏。

### 4.3 关键代码/文档落点

#### 4.3.1 代码位置

- BLE 核心：
  - [BleManager.kt](D:/newstart/core-ble/src/main/java/com/example/newstart/bluetooth/BleManager.kt)
  - [BleConnectionManager.kt](D:/newstart/core-ble/src/main/java/com/example/newstart/bluetooth/BleConnectionManager.kt)
  - [CleveringCommandSender.kt](D:/newstart/core-ble/src/main/java/com/example/newstart/bluetooth/CleveringCommandSender.kt)
  - [Hi90BCommandBuilder.kt](D:/newstart/core-ble/src/main/java/com/example/newstart/bluetooth/Hi90BCommandBuilder.kt)
  - [Hi90BFrameParser.kt](D:/newstart/core-ble/src/main/java/com/example/newstart/bluetooth/Hi90BFrameParser.kt)
  - [Hi90BWorkParams.kt](D:/newstart/core-ble/src/main/java/com/example/newstart/bluetooth/Hi90BWorkParams.kt)
- 设备页面：
  - [DeviceFragment.kt](D:/newstart/feature-device/src/main/java/com/example/newstart/ui/device/DeviceFragment.kt)
  - [DeviceViewModel.kt](D:/newstart/feature-device/src/main/java/com/example/newstart/ui/device/DeviceViewModel.kt)
  - [DeviceListAdapter.kt](D:/newstart/feature-device/src/main/java/com/example/newstart/ui/device/DeviceListAdapter.kt)
- 数据采集服务与本地存储：
  - [DataCollectionService.kt](D:/newstart/app-shell/src/main/java/com/example/newstart/service/DataCollectionService.kt)
  - [AppDatabase.kt](D:/newstart/core-db/src/main/java/com/example/newstart/database/AppDatabase.kt)
  - [HealthMetricsEntity.kt](D:/newstart/core-db/src/main/java/com/example/newstart/database/entity/HealthMetricsEntity.kt)
  - [PpgSampleEntity.kt](D:/newstart/core-db/src/main/java/com/example/newstart/database/entity/PpgSampleEntity.kt)

#### 4.3.2 主事实文档

- [戒指通讯协议使用说明与阶段性总结.md](D:/newstart/docs/20_专题系统说明/戒指通讯协议使用说明与阶段性总结.md)

#### 4.3.3 历史层文档

- [Clevering设备蓝牙配置.md](D:/newstart/docs/20_专题系统说明/Clevering设备蓝牙配置.md)
- [蓝牙连接功能使用指南.md](D:/newstart/docs/20_专题系统说明/蓝牙连接功能使用指南.md)

这两份文档应保留，但默认只作为历史材料或补充说明，不再作为当前 BLE 主事实依据。

### 4.4 当前边界与不要误写的点

- 不要把当前协议写成通用标准蓝牙健康协议。当前是项目自定义的 Hi90B / Clevering BLE 协议。
- 不要把步数写成当前主建议链的核心输入。当前更重要的是心率、血氧、HRV、体温和运动传感器。
- 不要写成“PPG 仍然是默认主采集策略”。当前策略已经调整为生命体征优先。
- 不要把历史 BLE 文档与阶段性总结文档并列为同等权重的事实源。
- 不要忽略本地数据库在数据链路中的作用。当前 BLE 数据并不是只做即时显示，而是要进入后续趋势、建议和画像计算。

### 4.5 适合写进正式材料的标准表述

长庚环在设备侧采用自定义 BLE 通信协议与 Android 端进行实时连接与数据同步。项目已经完成服务发现、特征读写、通知接收、协议帧解析、工作参数下发、即时任务控制和数据入库等关键能力，形成了从戒指采集到移动端分析的稳定链路。当前系统以心率、血氧、HRV、体温与运动传感器为主要连续证据源，并将采集策略调整为生命体征优先，以提高日常监测与后续分析的稳定性。

在工程实现上，BLE 协议与命令发送逻辑已从历史单模块结构中抽离到独立基础模块，设备页则承担连接、同步与状态呈现职责。通过本地数据库和采集服务，戒指端数据可进一步进入晨报、趋势分析、症状自查与个体化干预建议链路，从而实现设备感知与软件智能辅助的真正闭环。

---

## 5. 推荐模型与证据链专题

### 5.1 当前已落地事实

#### 5.1.1 当前已经成立的事实

- 长庚环的建议生成不是“纯大模型一句话直出”，而是证据驱动、分层生成的体系。
- 当前能够确认的关键证据来源包括：
  - 戒指与设备数据
  - 睡眠记录
  - 量表与基线评估
  - 医生问诊记录
  - 医检报告与异常指标
  - 干预执行与依从性
  - 症状引导输入
  - 云端上下文与周期总结
- 项目已经明确存在“证据 -> 分析 -> 建议”这一分层思路，而不是直接把所有输入拼接成一个大 prompt。
- 当前文档与代码都强调安全门控、风险等级、解释链和个体化程度的重要性。
- 云端推荐模型相关代码已经独立成专题目录：
  - [scientific-model.ts](D:/newstart/cloud-next/src/lib/recommendation-model/scientific-model.ts)
  - [explanation.ts](D:/newstart/cloud-next/src/lib/recommendation-model/explanation.ts)
  - [srm-v2-config.ts](D:/newstart/cloud-next/src/lib/recommendation-model/srm-v2-config.ts)
  - [admin.ts](D:/newstart/cloud-next/src/lib/recommendation-model/admin.ts)

#### 5.1.2 当前必须单独说明的边界事实

- 文档中出现的 `SRM_V2` 更接近“配置化建议框架”的定义，不等于已经全面上线。
- 当前仓库明确存在：
  - SRM_V2 的研究结论
  - SRM_V2 的数据库配置化设计
  - 云端的推荐模型目录与解释层
- 但是否全面切到线上主链，仍受远端数据库迁移、配置表生效状态和部署状态影响。
- 因此，更适合写成“项目已形成建议生成框架和配置化演进方向”，不适合写成“所有建议都已经由 SRM_V2 线上驱动”。

### 5.2 当前运行行为

#### 5.2.1 建议生成的实际分工

- 本地 Android 端负责采集、预处理、页面组织、局部解释兜底和部分即时建议。
- 云端负责更完整的证据整合、建议生成、周期总结和医生回合决策增强。
- 当前建议链路强调：
  - 结构化输入
  - 风险优先
  - 缺失输入显式处理
  - 文本解释与行动建议分离

#### 5.2.2 证据链当前覆盖的主要场景

- 晨报建议与晨间处方
- 每日干预画像与每日处方
- AI 医生追问与下一步建议
- 症状引导页的风险分层与支持动作
- 医检报告增强理解与异常提示
- 趋势/周期报告的总结与下一阶段重点

#### 5.2.3 当前运行中的解释链

- 系统并不满足于只给出“建议是什么”，还会尝试保留：
  - 风险等级
  - 原因摘要
  - 证据覆盖度
  - 置信度或可信度
  - 缺失输入提示
- 这也是项目后续可以写“可解释建议引擎”的基础。

### 5.3 关键代码/文档落点

#### 5.3.1 主事实文档

- [SCIENTIFIC_RECOMMENDATION_MODEL.md](D:/newstart/docs/20_专题系统说明/SCIENTIFIC_RECOMMENDATION_MODEL.md)
- [RECOMMENDATION_EVIDENCE_MAP.md](D:/newstart/docs/20_专题系统说明/RECOMMENDATION_EVIDENCE_MAP.md)

#### 5.3.2 研究与规划层文档

- [SCIENTIFIC_RECOMMENDATION_MODEL_RESEARCH.md](D:/newstart/docs/20_专题系统说明/SCIENTIFIC_RECOMMENDATION_MODEL_RESEARCH.md)
- [SRM_V2_CONFIGURATION_PLAN.md](D:/newstart/docs/20_专题系统说明/SRM_V2_CONFIGURATION_PLAN.md)

#### 5.3.3 云端代码落点

- [scientific-model.ts](D:/newstart/cloud-next/src/lib/recommendation-model/scientific-model.ts)
- [explanation.ts](D:/newstart/cloud-next/src/lib/recommendation-model/explanation.ts)
- [srm-v2-config.ts](D:/newstart/cloud-next/src/lib/recommendation-model/srm-v2-config.ts)
- [engine.ts](D:/newstart/cloud-next/src/lib/prescription/engine.ts)

#### 5.3.4 Android 与业务侧代码落点

- [DoctorChatViewModel.kt](D:/newstart/feature-doctor/src/main/java/com/example/newstart/ui/doctor/DoctorChatViewModel.kt)
- [RelaxHubViewModel.kt](D:/newstart/feature-relax/src/main/java/com/example/newstart/ui/relax/RelaxHubViewModel.kt)
- [SymptomGuideViewModel.kt](D:/newstart/feature-relax/src/main/java/com/example/newstart/ui/relax/SymptomGuideViewModel.kt)
- [MedicalReportAnalyzeViewModel.kt](D:/newstart/feature-relax/src/main/java/com/example/newstart/ui/relax/MedicalReportAnalyzeViewModel.kt)

### 5.4 当前边界与不要误写的点

- 不要把 [SCIENTIFIC_RECOMMENDATION_MODEL_RESEARCH.md](D:/newstart/docs/20_专题系统说明/SCIENTIFIC_RECOMMENDATION_MODEL_RESEARCH.md) 写成“已上线实现说明”。它是研究结论，不是运行时验收文档。
- 不要把 [SRM_V2_CONFIGURATION_PLAN.md](D:/newstart/docs/20_专题系统说明/SRM_V2_CONFIGURATION_PLAN.md) 写成“线上已经全面切换到 SRM_V2”。该文档明确写了“本轮只定义配置化方案，不切换线上主链”。
- 不要把建议生成写成“纯黑盒大模型自动诊断”。当前更准确的说法是“证据驱动、带安全门控和解释层的建议引擎”。
- 不要把所有推荐逻辑都写成“已经完成学习型优化、bandit 或因果策略学习”。这些内容属于研究与后续规划。
- 不要忽略推荐证据的多源特征。长庚环的建议并不是只来自睡眠或只来自戒指，而是多源证据整合后的结果。

### 5.5 适合写进正式材料的标准表述

长庚环当前采用的是证据驱动、可解释、可演进的建议生成体系，而不是单一黑盒模型直出。系统会综合戒指设备数据、睡眠记录、量表基线、医生问诊、医检报告、症状输入与干预执行历史，在风险优先与安全门控约束下生成建议与解释文本。这种设计兼顾了医学场景中的稳健性、可追踪性与用户可理解性。

从工程演进角度看，项目已经形成科学建议生成模型的框架化设计，包括结构化证据整合、域级假设、安全闸门、解释层和配置化方案。当前可以明确写成“项目已完成推荐模型框架设计并在多场景中落地证据链思路”，但不应夸大为“SRM_V2 已经在所有线上链路全面接管”。更准确的表达是：项目已经完成从规则增强到配置化、可解释建议引擎的演进准备，并具备继续扩展为更高级策略系统的基础。

---

## 6. UI 收敛与桌面机器人专题

### 6.1 当前已落地事实

- Android 端当前已经完成一轮面向交付和评分的 UI 系统收口，不是简单的颜色替换，而是信息架构、页面层级和交互职责的统一。
- 本轮收口的重点是：
  - 用统一设计 token 规范标题、正文、状态标签、卡片层级和按钮语义
  - 让今日、医生、趋势、设备、我的等主页面回到统一骨架
  - 对放松中心、症状引导、医检报告分析等次级页做样式和层级收口
  - 把桌面机器人从“麦克风入口”改成“页面说明与状态播报入口”
- 当前项目没有引入 Compose，也没有通过一次大改重写所有页面，而是在现有 Fragment/XML 体系内完成结构收口。

### 6.2 当前运行行为

#### 6.2.1 页面体系

- 今日页强调总览、风险和主建议，不再让大量噪音信息挤占首屏。
- 医生页强调“对话优先”，顶部状态区和输入区都进行了压缩，辅助区下沉到输入区后方。
- 设备页采用“状态 Hero + 主动作 + 设备列表 + 高级工具折叠”的结构。
- 我的页采用“用户 Hero + 云端状态 + 设置列表 + 快捷入口”的结构。
- 趋势页、放松中心、症状自查与医检分析页也在标题层级、卡片风格和按钮语义上与主页面保持一致。

#### 6.2.2 桌面机器人

- 桌面机器人不再承担自由语音问答入口。
- 当前行为更加接近“桌面导航助手”：
  - 进入页面时给出简短说明
  - 提示当前风险或本页重点
  - 引导下一步动作
  - 支持点击重播或重新生成
- 机器人的透明度、动作语义与播报逻辑已经做过收口，不再是每个页面各自维护一套。

#### 6.2.3 医生页语音交互

- 医生页已经收敛到半双工连续语音通话和一次性录音两种主模式。
- 为保证稳定性，当前通过状态机控制聆听、识别、回复、播报和冷却窗口。
- 统一音色和统一冷却策略是 UI 收口的一部分，不只是语音 SDK 的细节。

### 6.3 关键代码/文档落点

#### 6.3.1 主事实文档

- [UI_SYSTEM_REFACTOR_BASELINE.md](D:/newstart/docs/20_专题系统说明/UI_SYSTEM_REFACTOR_BASELINE.md)
- [TECH_REFACTOR_STATUS.md](D:/newstart/docs/10_项目事实与架构/TECH_REFACTOR_STATUS.md)

#### 6.3.2 代码位置

- Android 宿主与全局 Avatar：
  - [MainActivity.kt](D:/newstart/app-shell/src/main/java/com/example/newstart/MainActivity.kt)
  - [AvatarGuideScriptProvider.kt](D:/newstart/app-shell/src/main/java/com/example/newstart/ui/avatar/AvatarGuideScriptProvider.kt)
  - [DesktopAvatarNarrationService.kt](D:/newstart/app-shell/src/main/java/com/example/newstart/ui/avatar/DesktopAvatarNarrationService.kt)
  - [DesktopAvatarNarrationPolicy.kt](D:/newstart/app-shell/src/main/java/com/example/newstart/ui/avatar/DesktopAvatarNarrationPolicy.kt)
- 医生页：
  - [DoctorChatFragment.kt](D:/newstart/feature-doctor/src/main/java/com/example/newstart/ui/doctor/DoctorChatFragment.kt)
  - [DoctorChatAdapter.kt](D:/newstart/feature-doctor/src/main/java/com/example/newstart/ui/doctor/DoctorChatAdapter.kt)
  - [DoctorChatViewModel.kt](D:/newstart/feature-doctor/src/main/java/com/example/newstart/ui/doctor/DoctorChatViewModel.kt)
- 设备页：
  - [DeviceFragment.kt](D:/newstart/feature-device/src/main/java/com/example/newstart/ui/device/DeviceFragment.kt)
  - [DeviceViewModel.kt](D:/newstart/feature-device/src/main/java/com/example/newstart/ui/device/DeviceViewModel.kt)
- 放松与症状页：
  - [RelaxHubFragment.kt](D:/newstart/feature-relax/src/main/java/com/example/newstart/ui/relax/RelaxHubFragment.kt)
  - [SymptomGuideFragment.kt](D:/newstart/feature-relax/src/main/java/com/example/newstart/ui/relax/SymptomGuideFragment.kt)
  - [MedicalReportAnalyzeFragment.kt](D:/newstart/feature-relax/src/main/java/com/example/newstart/ui/relax/MedicalReportAnalyzeFragment.kt)

### 6.4 当前边界与不要误写的点

- 不要把当前 UI 收口写成“已经完成全面组件化重写”。当前是统一骨架、统一 token、统一职责，不是彻底重建。
- 不要写成“桌面机器人已经恢复自由语音助手能力”。目前它是页面说明入口，不是麦克风问答入口。
- 不要把下一轮 UI 债写成已完成，例如：
  - 医生页辅助区抽屉化
  - 更深层的 section 复用
  - 更彻底的渲染器抽象
- 不要写成“项目已经切到 Compose”。当前仍是 Fragment/XML 体系。
- 不要把视觉统一误写为“只改主题色”。当前真实改动包含页面层级、信息架构、按钮语义和桌面机器人交互职责。

### 6.5 适合写进正式材料的标准表述

长庚环在 Android 端已完成一轮面向交付质量的 UI 系统收口。项目并未为追求形式而推翻原有页面，而是在现有 Fragment/XML 技术栈基础上统一了设计 token、卡片层级、标题层级、按钮语义和页面主次关系，使今日、医生、趋势、设备、我的以及干预相关页面形成一致的视觉和交互骨架。这种做法既降低了大规模重构风险，也显著提升了代码组织和用户体验一致性。

桌面机器人是本轮 UI 收口中的关键调整点。它已从原先偏自由对话的入口，转变为页面说明、状态播报和下一步引导入口；医生页则进一步收敛到“对话优先 + 半双工语音状态机”的模式。通过这种收口，系统在不改变核心功能链路的前提下，获得了更清晰的信息架构和更适合答辩表述的工程边界。

---

## 7. AI 使用本文档时的最终约束

1. 讯飞相关写作必须区分“运行时接入能力”和“提示词模板文档”。
2. BLE 相关写作必须以 [戒指通讯协议使用说明与阶段性总结.md](D:/newstart/docs/20_专题系统说明/戒指通讯协议使用说明与阶段性总结.md) 为主依据。
3. 推荐模型相关写作必须严格区分“当前事实”和“研究/规划”，不能把 `SRM_V2` 研究方案写成全面上线事实。
4. UI 收口相关写作必须强调“行为冻结下的结构统一”，不能夸大为“彻底重写全部页面”。
5. 本文档的角色是专题事实总览，不替代项目概览、技术文档和模块介绍文档；写正式文档时应与那三份基线文档一起使用。

