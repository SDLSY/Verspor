# Multimodal Prompt Profiles

更新时间：2026-03-08

## 目标

为 `speech / image / video` 建立稳定可维护的 prompt 模板层，避免继续直接把裸 prompt 透传给 provider。

## Speech

### ASR

当前通过 `buildSpeechTranscriptionPrompt(hint)` 注入医疗/健康场景提示。

目标：
- 提升中文健康场景识别准确率
- 优先保留症状、时长、频率、时间段、诱因、缓解因素、部位、严重程度
- 强化对常见术语的识别：
  - 血氧
  - 心率
  - HRV
  - 失眠
  - 胸闷
  - 头晕
  - 疲劳
  - 焦虑
  - 呼吸困难

### TTS

当前先走文本改写，再走语音合成。

可用 profile：
- `doctor_summary`
- `sleep_coach`
- `calm_assistant`

规则：
- 医疗说明改成适合听的短句
- 不输出 Markdown
- 不读链接
- 不使用诊断口吻

## Image

可用 profile：
- `medical_wellness_product`
- `medical_education_illustration`
- `clinical_ui_cover`

当前默认：
- `medical_wellness_product`

设计原则：
- 高信任感
- 健康科技气质
- 低刺激
- 禁止 gore / surgery / needle / panic visuals

## Video

可用 profile：
- `sleep_guidance`
- `recovery_demo`
- `calm_product_story`

当前默认：
- `sleep_guidance`

设计原则：
- 稳定镜头
- 低刺激运动
- 清晰动作目标
- 不使用夸张广告语境
- 不允许惊悚、血腥、急救恐慌场面

## 代码位置

- `cloud-next/src/lib/ai/prompts.ts`
- `cloud-next/src/lib/ai/multimodal.ts`

## Android 调用入口

- `SpeechService.kt`
- `MediaGenerationService.kt`

当前这两个服务都已经支持把 `profile` 作为参数传给云端路由。

## 页面接入建议（当前已落地）

- 医生页使用：
  - `speech.tts`
    - `followUp` 默认 `calm_assistant`
    - `assessment` 默认 `doctor_summary`
  - `image.generate`
    - 默认 `medical_wellness_product`
  - `video.generate`
    - 默认 `sleep_guidance`
