# AI Runtime Architecture

更新时间：2026-03-08

## 目标

把当前分散在 Android 旧业务层、`cloud-next` 和 `ml/` 脚本中的 AI 能力，收口成一套可维护的端云混合架构。

核心原则：
- 端侧只保留实时、离线、隐私关键能力
- 云侧承担重生成和多模态能力
- UI 不直接访问具体模型客户端
- 模型切换只改 provider 配置和 API key，不改业务代码

## 当前真实能力分布

### 端侧实时层
- `SleepAnomalyDetector`
  - TFLite 优先
  - 规则回退
- 本地 OCR
  - 当前以 ML Kit / 本地解析链为主
- 本地干预草案
  - `EdgeLlmOnDeviceModel.generateInterventionPlan()`
- 本地轻量检索
  - 当前以关键词 RAG 为主

### 云侧生成层
- 当前已落地在 `cloud-next` 的处方 provider 链
- provider 优先级：
  - `vector_engine`
  - `openrouter`
  - `deepseek`
- 已统一环境变量入口和逻辑模型档位

### 策略编排层
- Android 端通过 `app/service/ai/*` 封装 AI 能力
- 云端通过 `cloud-next/src/lib/ai/*` 和 `prescription/providers/*` 收口 provider 能力
- `contracts/` 维护通用 AI 类型

## 三层架构

### 1. 端侧实时层

只保留以下能力：
- 晨报 / 异常检测
- 本地 OCR 兜底
- 本地干预草案兜底
- 本地规则回退
- 本地轻量检索

目标：
- 低延迟
- 离线可用
- 失败可回退
- 不阻塞首屏

### 2. 云侧生成层

负责：
- 医生问诊结构化回合
- 每日处方生成
- 医检图片理解 / OCR 增强解析
- 语音 ASR / TTS
- 图片生成
- 视频生成与理解

供应商策略：
- 主供应商：Vector Engine
- 备用：OpenRouter
- 第三优先级：DeepSeek
- Gemini 不再作为当前架构组成部分

### 3. 策略编排层

所有 AI 能力通过统一抽象访问，不让 UI 直接接触模型客户端。

当前约束：
- `ui/*` 不直接依赖 `OpenRouterDoctorApiClient`
- `ui/*` 不直接依赖 `SleepAnomalyDetector`
- `ui/*` 不直接依赖 `EdgeLlmOnDeviceModel`
- `ui/*` 不直接依赖 `MedicalReportParser`

## 能力矩阵

| 能力 | 当前主路径 | 当前兜底 | 备注 |
| --- | --- | --- | --- |
| `TextReasoning` | Cloud | Local rule | 医生问诊、处方、解释类文本 |
| `StructuredText` | Cloud | Local rule | 结构化问诊、结构化处方 |
| `Retrieval` | Local keyword / Cloud planned | Local keyword | 后续可接 embeddings + rerank |
| `VisionOCR` | Local OCR | Local parser | 当前医检主链仍以本地兜底 |
| `VisionUnderstanding` | Cloud planned | Local parser | 用于医检增强理解 |
| `SpeechASR` | Cloud planned | 无 | 当前尚未接入 |
| `SpeechTTS` | Cloud planned | 静态音频素材 | 当前音频播放仍为静态资源 |
| `ImageGeneration` | Cloud planned | 无 | 不进入同步主链 |
| `VideoGeneration` | Cloud async planned | 无 | 只走异步任务，不阻塞页面 |

## 逻辑模型档位

已统一逻辑模型 ID：

- `text.fast`
- `text.structured`
- `text.long_context`
- `retrieval.embed`
- `retrieval.rerank`
- `vision.ocr`
- `vision.reasoning`
- `speech.asr`
- `speech.tts`
- `image.generate`
- `video.generate.async`

规则：
- 业务代码只依赖逻辑模型档位
- provider-specific model id 通过环境变量或服务端配置映射
- 当前文本结构化档位默认使用 `qwen/qwen-2.5-7b-instruct`

## 当前代码落点

### Android
- `app/service/ai/StructuredTextCloudService.kt`
- `app/service/ai/LocalAnomalyDetectionService.kt`
- `app/service/ai/LocalInterventionPlanningService.kt`
- `app/service/ai/MedicalReportAiService.kt`
- `app/service/ai/LocalDoctorRetrievalService.kt`

### Cloud
- `cloud-next/src/lib/ai/types.ts`
- `cloud-next/src/lib/ai/config.ts`
- `cloud-next/src/lib/ai/openai-compatible.ts`
- `cloud-next/src/lib/ai/doctor-turn.ts`
- `cloud-next/src/lib/ai/report-understanding.ts`
- `cloud-next/src/lib/ai/multimodal.ts`
- `cloud-next/src/lib/prescription/providers/vector-engine.ts`
- `cloud-next/src/lib/prescription/providers/openrouter.ts`
- `cloud-next/src/lib/prescription/providers/deepseek.ts`

### Contracts
- `contracts/typescript/index.ts`
- `contracts/kotlin/.../InterventionDtos.kt`

## 当前已落地 AI 路由

- `POST /api/doctor/turn`
- `POST /api/report/understand`
- `POST /api/ai/speech/transcribe`
- `POST /api/ai/speech/synthesize`
- `POST /api/ai/image/generate`
- `POST /api/ai/video/generate`
- `GET /api/ai/video/jobs/:jobId`

说明：
- 医生问诊与医检增强已经进入统一云端 AI 入口
- 语音、图像、视频已经具备统一路由和 provider 选择能力
- 视频仍按异步任务处理，不进入同步业务主链

## 当前已落地交互入口

- 医生页：
  - 语音问诊控制条（录音 -> 转写 -> 填入输入框 -> 发送）
  - 自动播报开关
  - image/video 多模态助手扩展区
- 全局 Avatar：
  - 医生页回复可通过 TTS 交给全局 Avatar 播放
  - speaking / idle / emphasis 已与真实音频播放状态联动

## 当前不做的事

- 不让视频能力进入同步业务主链
- 不在本轮把所有 Android AI 实现物理迁入 `core-ml`
- 不修改历史 docx 和比赛材料正文

## 后续迁移方向

1. 把 `service/ai/*` 中稳定的服务接口下沉到 `core-data/core-network/core-ml`
2. 把医生问诊、医检增强、语音能力统一接入云端通用 AI registry
3. 为检索、语音、图像、视频补 contracts 和任务 DTO
4. 将 `feature-*` 继续从 UI 页面侧完成物理拆分
