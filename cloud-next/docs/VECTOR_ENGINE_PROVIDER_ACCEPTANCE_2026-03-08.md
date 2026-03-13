# Vector Engine Provider Acceptance

更新时间：2026-03-08

本文件记录对 Vector Engine 的真实 provider 验收结果。目标是确认：

- speech / image / video 实际可用的 endpoint
- 最小可用模型
- 成功返回格式
- 常见失败返回格式

说明：
- 验收通过服务器侧真实 HTTP 调用完成
- 不记录 API key
- 只记录最小必要的请求形状与返回形状

## 1. 模型发现

通过 `GET /v1/models` 已确认以下模型存在：

- 语音转文字：`whisper-1`
- 文本转语音：`tts-1`
- 图片生成：`flux-pro`、`gpt-image-1`、`gpt-image-1-mini`
- 视频生成：`aigc-video-kling`、`sora-2`、`veo3.1-fast-components`

## 2. Speech ASR

### 实测 endpoint

- `POST https://api.vectorengine.ai/v1/audio/transcriptions`

### 实测模型

- `whisper-1`

### 请求形态

- `multipart/form-data`
- 字段：
  - `file`
  - `model`
  - `language`

### 成功返回格式

```json
{
  "text": "你好,我昨晚睡眠不好,今天有些頭暈和疲勞。"
}
```

结论：
- 该接口可正常工作
- 返回为单层 JSON，对路由封装友好

## 3. Speech TTS

### 实测 endpoint

- `POST https://api.vectorengine.ai/v1/audio/speech`

### 实测模型

- `tts-1`

### 请求形态

```json
{
  "model": "tts-1",
  "input": "Hello from VesperO cloud speech synthesis acceptance.",
  "voice": "alloy",
  "format": "mp3"
}
```

### 成功返回格式

- HTTP `200`
- `Content-Type: audio/mpeg`
- 返回体为二进制 MP3

说明：
- 该接口不是 JSON
- 云端路由需要把二进制转成可传输形式（当前实现为 data URL）

## 4. Image Generation

### 实测 endpoint

- `POST https://api.vectorengine.ai/v1/images/generations`

### 实测模型

- 成功：`flux-pro`
- 失败：`flux.1-dev`（当前分组无可用渠道）

### 请求形态

```json
{
  "model": "flux-pro",
  "prompt": "A flat minimal illustration of a sleep ring on a bedside table, white background.",
  "size": "1024x1024"
}
```

### 成功返回格式

```json
{
  "data": [
    {
      "url": "https://..."
    }
  ],
  "created": 1772969054
}
```

### 失败返回格式

```json
{
  "error": {
    "message": "当前分组 default 下对于模型 flux.1-dev 无可用渠道",
    "message_zh": "当前分组 default 下对于模型 flux.1-dev 无可用渠道",
    "type": "new_api_error"
  }
}
```

结论：
- 图片生成接口可用
- `flux-pro` 当前可用
- 返回格式与 OpenAI 风格相近

## 5. Video Generation

### 实测 endpoint

- `POST https://api.vectorengine.ai/v1/video/generations`

### 实测模型

- `sora-2`
- `veo3.1-fast-components`
- `aigc-video-kling`

### 请求形态

```json
{
  "model": "aigc-video-kling",
  "prompt": "A calm night sky over a quiet city, 5-second clip.",
  "duration": 5
}
```

### 当前实测返回格式（上游高负载）

```json
{
  "code": 500,
  "message": "当前分组上游负载已饱和，请稍后再试",
  "request_id": "",
  "data": {
    "task_id": "",
    "task_status": "error",
    "created_at": 0,
    "updated_at": 0
  }
}
```

结论：
- 视频使用的实际 endpoint 已确认是 `/v1/video/generations`
- 当前实测未拿到成功排队响应，但已经确认返回结构为“任务型异步结构”，而不是同步视频内容
- 因此云端路由应按异步 job 模式封装

## 6. 当前代码配置

当前 `.env.local` 默认建议值：

- `VECTOR_ENGINE_SPEECH_ASR_MODEL=whisper-1`
- `VECTOR_ENGINE_SPEECH_TTS_MODEL=tts-1`
- `VECTOR_ENGINE_IMAGE_GENERATION_MODEL=flux-pro`
- `VECTOR_ENGINE_VIDEO_GENERATION_MODEL=aigc-video-kling`

## 7. 当前结论

- `speech transcription`：已真实打通
- `speech synthesis`：已真实打通
- `image generation`：已真实打通
- `video generation`：端点与异步返回结构已确认，当前受上游负载影响，尚未拿到成功排队样本
