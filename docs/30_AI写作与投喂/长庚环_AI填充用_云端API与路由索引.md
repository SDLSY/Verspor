# 长庚环 AI 填充用：云端 API 与路由索引

## 1. 文档定位

本文档用于给后续 AI、写作助手、代码理解系统提供一份面向 `cloud-next/` 的事实索引。目标不是伪造完整 OpenAPI，也不是穷举每个字段，而是让外部模型能准确回答以下问题：

- 当前云端在系统里承担什么角色。
- `cloud-next` 里到底有哪些主要路由。
- 哪些接口面向 Android 主 App，哪些接口属于后台、内部或实验路径。
- 认证、睡眠、报告、医生、干预、机器人等能力各自落在什么接口组中。
- 哪些接口已经在当前运行链路中使用，哪些仍偏内部或演进中。

本文档优先描述仓库中能证实的事实，不把规划接口、假想字段、未来演进当作当前上线能力。

## 2. 当前云端角色与边界

### 2.1 当前云端的核心角色

当前项目的云端位于 `cloud-next/`，技术栈为 Next.js App Router + Supabase。它在系统中的职责主要包括：

- 统一认证与账号管理。
- 作为 Android 端的主要业务 API 层。
- 承接 AI 增强能力，如医生问诊增强、医检报告理解、机器人叙事生成。
- 承接睡眠、恢复、报告、评估、干预等结构化数据的云端落点。
- 提供一部分后台、管理和内部 worker 路由。

### 2.2 当前云端的非职责边界

以下内容不应被写成“完全由云端负责”：

- 戒指 BLE 通信不在 `cloud-next` 完成，而是在 Android 端完成。
- 本地数据库写入不在 `cloud-next` 内完成，而是在 Android 本地完成。
- 并非所有 AI 功能都强制依赖云端，项目中存在明显的本地兜底与本地草案生成能力。
- 云端不是单独的“患者前台站点”，它同时包含 Android API、Web 登录页、后台页和内部路由。

## 3. API 分组总览

根据 `cloud-next/src/app/api` 当前目录结构，可以把当前路由大致分成以下几组：

1. 认证与账号组
2. 睡眠、恢复与同步组
3. 干预、评估与推荐组
4. 医生、报告与智能分析组
5. AI 语音、图像、视频与机器人叙事组
6. 数据上传与多模态输入组
7. 内部管理、后台与系统控制组
8. 版本兼容与实验路径

从 Android 主 App 的当前调用角度看，最重要的是以下几类：

- `/api/auth/*`
- `/api/user/profile`
- `/api/sleep/*`
- `/api/recovery/trend`
- `/api/sync`
- `/api/report/*`
- `/api/doctor/*`
- `/api/intervention/*`
- `/api/assessment/*`
- `/api/avatar/narration`
- `/api/ai/*`

## 4. 认证与用户资料路由

### 4.1 当前已落地事实

当前项目的认证主链路已经收敛到 Supabase Auth，云端对外暴露的主要认证接口包括：

- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/resend-confirmation`
- `POST /api/auth/password-reset`
- `GET /api/user/profile`
- `PUT /api/user/profile`

注册和重置密码所用的邮件回跳地址，不是写死在 Android 中，而是由云端根据 `APP_PUBLIC_BASE_URL` 拼装。

### 4.2 当前运行行为

- 注册时，如果 Supabase 开启邮箱确认，接口返回的是“待确认邮箱”状态，而不是直接当成失败。
- 登录时，如果账号存在但未完成邮箱确认，接口会返回待确认状态。
- 忘记密码邮件会把用户引导到 `/reset-password`。
- 邮箱确认邮件会把用户引导到 `/auth/confirm`。
- Android 侧“我的”页、登录页、资料页主要消费这组接口。

### 4.3 关键代码与文档落点

- `cloud-next/src/app/api/auth/register/route.ts`
- `cloud-next/src/app/api/auth/login/route.ts`
- `cloud-next/src/app/api/auth/resend-confirmation/route.ts`
- `cloud-next/src/app/api/auth/password-reset/route.ts`
- `cloud-next/src/app/api/user/profile/route.ts`
- `cloud-next/src/lib/auth-state.ts`
- `cloud-next/src/lib/supabase/middleware.ts`

### 4.4 当前边界与不要误写的点

- 不要把当前系统写成“支持手机号登录、第三方登录、企业 SSO”，仓库事实并不支持这种表述。
- 不要把“待确认邮箱”描述成系统 bug；当前实现已经把它作为显式状态处理。
- 不要把资料页写成“支持头像上传”，当前头像更接近字母头像和本地资料展示，不是完整媒体管理系统。

### 4.5 适合写进正式材料的标准表述

当前长庚环云端采用 Supabase 作为认证底座，在 `cloud-next` 中统一封装注册、登录、邮箱确认、密码重置与资料读写接口，为 Android 单入口 App 提供一致的账号服务。

## 5. 睡眠、恢复与数据同步路由

### 5.1 当前已落地事实

当前与睡眠、恢复、同步直接相关的主要路由包括：

- `POST /api/sleep/upload`
- `POST /api/sleep/analyze`
- `GET /api/sleep/history`
- `GET /api/recovery/trend`
- `POST /api/sync`
- `POST /api/data/upload`

其中 Android 端还存在本地数据库与本地分析，因此云端不是唯一数据处理层，但它是跨端同步、历史汇总、趋势生成的重要一层。

### 5.2 当前运行行为

- 戒指侧原始数据先在 Android 本地采集、落库，再按业务场景向云端上传。
- 今日页和趋势页展示依赖本地与云端聚合结果。
- `recovery/trend` 更偏中高层趋势聚合，不是底层 BLE 通信接口。
- `sync` 与 `data/upload` 负责把本地结构化结果或原始数据片段上送到云端。

### 5.3 关键代码与文档落点

- `cloud-next/src/app/api/sleep/upload/route.ts`
- `cloud-next/src/app/api/sleep/analyze/route.ts`
- `cloud-next/src/app/api/sleep/history/route.ts`
- `cloud-next/src/app/api/recovery/trend/route.ts`
- `cloud-next/src/app/api/sync/route.ts`
- `cloud-next/src/app/api/data/upload/route.ts`
- Android 调用口：`core-network/src/main/java/com/example/newstart/network/ApiService.kt`

### 5.4 当前边界与不要误写的点

- 不要把 `/api/sleep/analyze` 写成整个项目唯一的睡眠算法入口，端侧本地也有分析链路。
- 不要把 `sync` 写成单纯“云备份”，它更接近业务同步入口。
- 不要把睡眠趋势与报告分析混为一谈，它们属于不同的数据链和不同的页面语义。

### 5.5 适合写进正式材料的标准表述

云端为睡眠、恢复与同步能力提供统一 API 层，重点负责结构化数据接收、历史记录查询、趋势聚合和跨端一致性支撑，而底层采集与即时落库仍在 Android 侧完成。

## 6. 干预、评估与推荐路由

### 6.1 当前已落地事实

当前与干预、评估、推荐直接相关的云端路由包括：

- `POST /api/advice/generate`
- `POST /api/intervention/task/upsert`
- `POST /api/intervention/execution/upsert`
- `GET /api/intervention/effect-trend`
- `POST /api/assessment/baseline-summary/upsert`

这些路由用于承接问诊、评估、干预执行与结果回写，不等同于整个推荐算法都只跑在云端。

### 6.2 当前运行行为

- Android 端会先基于本地状态组织任务，再向云端写入干预任务、执行记录和效果摘要。
- `advice/generate` 更偏建议生成或推荐入口。
- `assessment/baseline-summary/upsert` 用于写入基线评估摘要。
- 干预后的执行记录和效果趋势，最终可被趋势页、推荐链路或复盘链路消费。

### 6.3 关键代码与文档落点

- `cloud-next/src/app/api/advice/generate/route.ts`
- `cloud-next/src/app/api/intervention/task/upsert/route.ts`
- `cloud-next/src/app/api/intervention/execution/upsert/route.ts`
- `cloud-next/src/app/api/intervention/effect-trend/route.ts`
- `cloud-next/src/app/api/assessment/baseline-summary/upsert/route.ts`
- 相关 Android 调用口：`ApiService.kt`

### 6.4 当前边界与不要误写的点

- 不要写成“所有干预处方完全由云端实时计算完成”，当前系统有本地状态机、模板和兜底逻辑。
- 不要把“推荐模型研究文档”里的全部能力默认写成当前接口都已上线。
- 不要把评估、建议、干预执行、效果回写写成一个不可拆分的单点接口，它们在当前云端是分路由组织的。

### 6.5 适合写进正式材料的标准表述

长庚环云端将评估摘要、建议生成、干预任务、执行记录和效果回写拆分为独立接口，便于 Android 端在统一用户流程下实现“建议生成—任务执行—效果复盘”的闭环。

## 7. 医生、报告与智能分析路由

### 7.1 当前已落地事实

当前医生问诊、医检报告和结构化理解相关的主要路由包括：

- `POST /api/doctor/turn`
- `POST /api/doctor/inquiry-summary/upsert`
- `POST /api/report/metrics/upsert`
- `GET /api/report/latest`
- `GET /api/report/period-summary`
- `POST /api/report/understand`

其中 `doctor/turn` 与 `report/understand` 都是当前云端 AI 增强的重要入口。

### 7.2 当前运行行为

- 症状自查或医生页产生的上下文可进入 `doctor/turn`，由云端生成下一轮追问、建议或解释。
- 报告 OCR 原文和结构化指标可进入 `report/understand`，生成更适合用户阅读的解释结果。
- 报告结构化指标还会通过 `metrics/upsert` 等接口沉淀为可查询记录。

### 7.3 关键代码与文档落点

- `cloud-next/src/app/api/doctor/turn/route.ts`
- `cloud-next/src/app/api/doctor/inquiry-summary/upsert/route.ts`
- `cloud-next/src/app/api/report/metrics/upsert/route.ts`
- `cloud-next/src/app/api/report/latest/route.ts`
- `cloud-next/src/app/api/report/period-summary/route.ts`
- `cloud-next/src/app/api/report/understand/route.ts`

### 7.4 当前边界与不要误写的点

- 不要把 `doctor/turn` 写成“医生视频问诊平台”，它更准确的定位是医生问诊增强或多轮问答后端。
- 不要把 `report/understand` 写成正式医学诊断引擎，它更适合表述为医检报告理解与可读化增强。
- 不要把“可读报告”理解成完全脱离 OCR 原文的独立真相层，它仍依赖 OCR 文本、结构化指标和规则/模型解释。

### 7.5 适合写进正式材料的标准表述

云端通过医生问诊增强接口和医检报告理解接口，把 Android 端采集到的主诉、症状、自查结果与报告文本转换为更适合用户理解和后续干预决策的结构化输出。

## 8. AI 语音、图像、视频与机器人叙事路由

### 8.1 当前已落地事实

当前云端还承接了一批 AI 多模态路由，主要包括：

- `POST /api/ai/speech/transcribe`
- `POST /api/ai/speech/transcribe-multipart`
- `POST /api/ai/speech/synthesize`
- `POST /api/ai/image/generate`
- `GET /api/ai/image/jobs/[jobId]`
- `POST /api/ai/video/generate`
- `GET /api/ai/video/jobs/[jobId]`
- `POST /api/avatar/narration`

其中：

- `ai/speech/*` 对应语音转写和语音合成。
- `ai/image/*`、`ai/video/*` 是生成式媒体接口。
- `avatar/narration` 是桌面机器人与页面叙事文案接口。

### 8.2 当前运行行为

- 桌面机器人在页面进入时优先使用本地预热文案，在点击触发时可走云端 `avatar/narration` 动态生成。
- 医生页和其他语音能力会调用 `ai/speech/*`。
- 图像与视频接口更偏 AI 能力拓展层，不是当前 Android 主闭环中最核心的常态接口。

### 8.3 关键代码与文档落点

- `cloud-next/src/app/api/ai/speech/transcribe/route.ts`
- `cloud-next/src/app/api/ai/speech/transcribe-multipart/route.ts`
- `cloud-next/src/app/api/ai/speech/synthesize/route.ts`
- `cloud-next/src/app/api/ai/image/generate/route.ts`
- `cloud-next/src/app/api/ai/video/generate/route.ts`
- `cloud-next/src/app/api/avatar/narration/route.ts`

### 8.4 当前边界与不要误写的点

- 不要把 `avatar/narration` 写成一个开放式闲聊大模型接口，它是页面说明与导航叙事接口。
- 不要把多媒体生成能力写成项目核心主流程，当前它们更像扩展能力。
- 不要把讯飞端侧集成和云端 AI 多模态路由混成一个系统；两者是协作关系，不是完全同一层。

### 8.5 适合写进正式材料的标准表述

云端提供语音转写、语音合成、图像/视频生成与机器人叙事接口，作为 Android 端本地能力之外的增强层，用于提升问诊交互、页面引导和多模态扩展能力。

## 9. 内部管理与运行时控制路由

### 9.1 当前已落地事实

`cloud-next` 中还存在一些更偏内部管理或系统控制的路由，例如：

- `api/internal/*`
- `api/admin/*`
- `api/model/*`
- `api/cron/*`
- `api/cache/*`
- `api/origin-auth/*`

这些路径说明当前云端不仅是 App API，还承担内部 worker、模型服务调度、后台校验和系统控制职责。

### 9.2 当前边界与不要误写的点

- 不要把这些内部路由默认写成 Android 主 App 常规调用面。
- 不要把后台和内部接口暴露为“所有用户可直接访问的能力”。
- 不要把这部分写成完全稳定公开 API；它们更偏平台内部支撑层。

## 10. 版本兼容与实验性接口

当前目录中还能看到 `api/v1/*`、`api/v2/*`、`api/prescription/*`、`api/conversation/*` 等路径。这说明云端存在一定的版本兼容、实验路径或能力演进痕迹。对外写作时应使用更稳妥的表述：

- 当前系统已经形成多组业务 API。
- 一部分接口承担稳定业务，一部分仍处于能力演进与兼容收口阶段。
- 不要仅凭 `v1`、`v2` 目录名就断言整个系统已经完成正式版本治理。

## 11. 当前边界与不要误写的点

1. 不要把当前云端写成“纯后端 API 服务”，因为它同时承载登录页、确认页、重置密码页和后台页。
2. 不要把所有 AI 能力写成“完全云端化”，当前系统明显存在端侧兜底和端云协同。
3. 不要把所有路由都写成 Android 当前主流程必经路由，许多接口是内部、后台或实验性质。
4. 不要伪造完整 OpenAPI 字段，尤其不要在不知道字段的情况下编出请求体或响应体。
5. 不要把研究文档中的推荐模型设想默认映射为当前所有 API 都已上线。

## 12. 适合写进正式材料的统一标准表述

长庚环云端基于 `cloud-next` 构建，采用 Next.js App Router 与 Supabase 组合，向 Android 单入口 App 提供认证、资料、睡眠与恢复、医检报告理解、医生问诊增强、干预回写、机器人叙事等多组 API，同时保留后台管理、内部 worker 与模型控制能力。其总体定位不是单一功能接口集合，而是端云协同健康辅助系统中的统一云端服务层。
