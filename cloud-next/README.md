# cloud-next

`cloud-next` 是长庚环项目的云端 API 网关、AI 能力编排层与管理后台。  
它不是独立产品，而是和 Android `:app-shell`、Supabase、模型运行时、演示账号体系一起组成端云协同主链。

## 1. 当前定位

当前这套云端主要承担四类职责：

- 对 Android 端暴露统一的 `/api/*` 路由
- 负责睡眠分析、建议生成、报告理解、语音播报、图片分析等云端能力编排
- 负责后台工作台，包括 `dashboard`、`patients`、`reports`、`recommendations`、`system`
- 负责演示账号、bootstrap、本地灌库配套接口，以及后台测试/运维辅助能力

## 2. 运行形态

- Web 框架：Next.js Route Handlers + App Router
- 数据底座：Supabase（Postgres / Auth / RLS）
- 部署方式：Vercel
- 外部模型运行时：HTTP 推理端点、本地 fallback provider、统一 AI provider 配置

## 3. 当前主链说明

### 3.1 睡眠分析链路

1. Android 上传睡眠记录与采样数据
2. `POST /api/sleep/analyze` 创建分析任务
3. internal worker 消费任务并调用当前激活的模型配置
4. 结果写入 `sleep_stage_results`、`anomaly_scores`、`nightly_reports`
5. 页面和后台再消费这些结果

这条链路证明的是“端云协同睡眠分析”，不是“Android 本地完整五阶段睡眠分期模型稳定部署”。

### 3.2 建议生成链路

1. 证据层整合睡眠、问诊、量表、医检、干预执行等结构化输入
2. 决策层通过规则、安全门控与模型配置做约束
3. 表达层负责推荐理由、执行说明和页面讲解文案
4. 结果写入 recommendation / prescription / trace 类记录

这条链路对应的是混合建议引擎，不应写成“AI 自动诊断”或“完全自主开方案”。

### 3.3 演示与后台链路

1. 演示账号登录
2. `/api/demo/bootstrap` 返回本地快照
3. Android 导入本地事实层
4. 后台通过患者工作台、报告页、推荐页与系统页完成闭环展示

## 4. 当前目录结构

- `src/app/`
  - App Router 页面与 API 路由
- `src/lib/`
  - AI provider、鉴权、Supabase、处方/推荐、后台聚合逻辑
- `scripts/`
  - 演示账号、测试、运维辅助脚本
- `supabase/`
  - migration 与数据库结构演进
- `docs/`
  - 云端接口、部署、架构与 provider 说明
- `models/`
  - 云端模型或运行时相关资源

说明：

- `output/playwright/` 这类浏览器导出截图属于生成物，不再作为仓库内容保留
- `public/` 下的静态示意图也已从本目录移除；当前 `cloud-next` 不依赖这些仓库内静态图片运行

## 5. 关键接口分组

### 5.1 用户与端侧主链

- `POST /api/auth/login`
- `POST /api/auth/register`
- `GET /api/user/profile`
- `POST /api/sleep/upload`
- `POST /api/sleep/analyze`
- `GET /api/report/latest`
- `POST /api/report/understand`
- `POST /api/intervention/daily-prescription`
- `POST /api/intervention/execution/upsert`

### 5.2 AI 能力接口

- `POST /api/doctor/turn`
- `POST /api/report/understand`
- `POST /api/ai/speech/transcribe`
- `POST /api/ai/speech/synthesize`
- `POST /api/medication/analyze`
- `POST /api/food/analyze`
- `POST /api/avatar/narration`

### 5.3 后台与运维接口

- `POST /api/internal/worker/run`
- `GET /api/internal/worker/run`
- `POST /api/internal/models/register`
- `POST /api/internal/models/activate`
- `GET /api/internal/metrics/jobs`
- `GET /api/internal/admin/*`

## 6. 环境变量分组

### 6.1 基础运行

- `NEXT_PUBLIC_SUPABASE_URL`
- `NEXT_PUBLIC_SUPABASE_ANON_KEY`
- `SUPABASE_SERVICE_ROLE_KEY`
- `INTERNAL_WORKER_TOKEN`
- `MODEL_INFERENCE_TOKEN`

### 6.2 调度与安全

- `CRON_SECRET`
- `CLOUDFLARE_ORIGIN_SECRET`

### 6.3 建议生成与 AI provider

- `PRESCRIPTION_PROVIDER_PRIMARY`
- `PRESCRIPTION_PROVIDER_SECONDARY`
- `PRESCRIPTION_PROVIDER_TERTIARY`

支持的 provider id：

- `vector_engine`
- `openrouter`
- `deepseek`

### 6.4 Vector Engine

- `VECTOR_ENGINE_API_KEY`
- `VECTOR_ENGINE_STRUCTURED_VISION_API_KEY`
- `VECTOR_ENGINE_STRUCTURED_VISION_CHAT_COMPLETIONS_URL`
- `VECTOR_ENGINE_CHAT_COMPLETIONS_URL`
- `VECTOR_ENGINE_TEXT_FAST_MODEL`
- `VECTOR_ENGINE_TEXT_STRUCTURED_MODEL`
- `VECTOR_ENGINE_TEXT_LONG_CONTEXT_MODEL`
- `VECTOR_ENGINE_RETRIEVAL_EMBED_MODEL`
- `VECTOR_ENGINE_RETRIEVAL_RERANK_MODEL`
- `VECTOR_ENGINE_VISION_OCR_MODEL`
- `VECTOR_ENGINE_VISION_REASONING_MODEL`
- `VECTOR_ENGINE_SPEECH_ASR_MODEL`
- `VECTOR_ENGINE_SPEECH_TTS_MODEL`
- `VECTOR_ENGINE_IMAGE_GENERATION_MODEL`
- `VECTOR_ENGINE_VIDEO_GENERATION_MODEL`

### 6.5 OpenRouter

- `OPENROUTER_API_KEY`
- `OPENROUTER_MODEL`

### 6.6 演示环境

- `DEMO_ACCOUNT_DEFAULT_PASSWORD`
- `DEMO_ADMIN_DEFAULT_PASSWORD`
- `DEMO_ACCOUNT_EMAIL_DOMAIN`
- `ADMIN_EMAIL_ALLOWLIST`

## 7. 本地校验

```bash
npm install
npm run build
npm run lint
```

## 8. 相关文档

- `docs/README.md`
- `docs/api-contract.md`
- `docs/api-v2-contract.md`
- `docs/architecture.md`
- `docs/demo-deployment.md`
- `docs/demo-accounts-seed.md`
- `docs/MULTIMODAL_PROMPT_PROFILES.md`
- `docs/VECTOR_ENGINE_PROVIDER_ACCEPTANCE_2026-03-08.md`

## 9. 维护边界

- 不要把本地 `.env`、`.env.pulled`、`.env.local.bak*` 之类环境快照提交进仓库
- 不要把 `output/playwright/` 一类截图生成物作为源码的一部分
- 不要把当前云端能力写成“单一模型”或“完全自主决策系统”
- 睡眠分析、SRM_V2、TTS、图片分析都应按“可配置、可回退、可追踪”的方式理解
