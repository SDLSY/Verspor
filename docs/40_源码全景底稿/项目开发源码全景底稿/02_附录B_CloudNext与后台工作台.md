# 标题
附录 B：CloudNext 与后台工作台

- 适用任务：云端架构理解、后台文档写作、端云闭环说明
- 阅读优先级：高
- 是否允许对外直接引用：允许，但不得把演示能力写成外部开放生产能力

## 1. cloud-next 的定位

`cloud-next` 是当前项目的云端主线，同时承担三类职责：

1. 面向 Android 的业务 API
2. 后台管理与运营工作台
3. AI 编排、模型路由、报告 / 推荐 / 问诊 / 语音 / 图片分析等增强能力

技术栈从 `package.json` 可以确认：

- Next.js
- React
- Supabase
- TypeScript
- Zod

## 2. 目录结构的当前语义

### 2.1 `src/app`

承载：

- 页面路由
- 登录与密码重置页面
- dashboard / patients / reports / recommendations / system / story 等后台页面
- `/api/*` 业务 API

### 2.2 `src/lib`

承载核心逻辑：

- `admin-*`：后台聚合与运营逻辑
- `ai/*`：AI provider 配置、提示词、语音、图片理解、报告理解、医生问诊
- `demo/*`：演示账号 bootstrap 逻辑
- `prescription/*`：处方上下文、规则、provider、检索与引擎
- `model-*`：模型注册与推理运行时
- `recommendation-*`：建议效果、解释、追踪
- `supabase/*`：客户端与服务端 Supabase 适配

### 2.3 `scripts/demo-accounts`

当前演示体系的重要入口。它不只是脚本目录，而是：

- 演示账号种子工具
- 场景数据重建工具
- 观演前准备链路的一部分

### 2.4 `supabase`

这里保留数据库迁移，是云端数据结构的事实来源之一。

## 3. 面向 Android 的 API 主线

当前 Android 会用到的关键路由至少包括：

- 认证：
  - `/api/auth/login`
  - `/api/auth/register`
  - `/api/auth/refresh`
- 睡眠与恢复：
  - `/api/sleep/upload`
  - `/api/sleep/analyze`
  - `/api/recovery/trend`
  - `/api/report/period-summary`
- 医检报告：
  - `/api/report/understand`
  - `/api/report/latest`
  - `/api/report/metrics/upsert`
- 医生：
  - `/api/doctor/turn`
  - `/api/doctor/inquiry-summary/upsert`
- 干预：
  - `/api/intervention/daily-prescription`
  - `/api/intervention/task/upsert`
  - `/api/intervention/execution/upsert`
  - `/api/intervention/effect/trend`
- 药物 / 饮食：
  - `/api/medication/analyze`
  - `/api/medication/records/upsert`
  - `/api/food/analyze`
  - `/api/food/records/upsert`
- 机器人与语音：
  - `/api/avatar/narration`
  - `/api/ai/speech/synthesize`
- 演示：
  - `/api/demo/bootstrap`

## 4. 后台当前页面体系

### 4.1 总体形态

后台不是旧式“表格控制台”，当前已经重构为“演示 / 闭环优先”的工作台。核心页面包括：

- `dashboard`
- `story`
- `patients`
- `reports`
- `recommendations`
- `system/jobs`
- `system/models`

### 4.2 Dashboard

当前 dashboard 的重点不是单纯统计卡，而是：

- 5 条 demo 闭环故事卡
- 全局状态条
- 高风险患者、报告、模型 / 作业状态的总览入口

### 4.3 Story

`story/page.tsx` 是演示化后台的重要补充，它把项目的闭环故事抽成可讲解的页面，而不是要求讲解者在多个表格之间自己组织叙事。

### 4.4 Patients

患者页已经从“普通列表”转成：

- demo 患者池 / 重点患者池
- 完整患者池
- 单患者故事化详情页

患者详情页当前叙事顺序明确为：

- 当前判断
- 关键证据
- 报告与问诊
- 建议与干预
- 结果回写
- 时间线

### 4.5 Reports

报告页当前按待处理队列组织，而不是单纯原始记录页。它强调：

- 待解析
- 高风险
- 待问诊
- 已形成建议

### 4.6 Recommendations

建议页已去研发化，重点放在：

- 建议是什么
- 为什么给
- 有没有执行
- 执行效果如何

技术元信息默认弱化或折叠。

### 4.7 System

系统页被明确成“高级运维区”，包括：

- jobs
- models
- audit

这里保留研发和运维能力，但默认不作为首层演示主视图。

## 5. 云端 AI 编排的角色

当前 cloud-next 不是“纯 CRUD 接口层”，而是项目的 AI 编排中心。它承担：

- 提示词构建
- provider 配置
- provider 回退
- 结构化输出解析
- 模型注册表读取
- worker 调度
- 管理端和 Android 端共享的解释与增强逻辑

因此，云端在项目中的角色不是“辅助服务”，而是很多关键业务链路的增强层。

## 6. 演示体系与后台的关系

当前后台已经与演示账号体系联动：

- demo 账号由 seed 创建
- 管理员 demo 账号可直接进入后台
- dashboard / story / patients 等页面优先展示可讲 demo 闭环
- Android 演示数据与后台观演数据可以通过同一套账号体系串起来

这意味着：

- 项目不只是有真实业务代码
- 还已经具备“稳定演示闭环”的工程化准备

## 7. CloudNext 当前必须写清的边界

1. 云端不是单模型系统，而是多 provider、多能力的编排层。
2. 后台不是研发内网工具，而是已被整理成患者 / 报告 / 建议 / 运维的工作台。
3. 管理端很多页面依赖 Supabase 登录态和管理员权限，不是匿名开放系统。
4. 演示账号、seed、bootstrap 是当前云端主线的一部分，不是临时脚本。
5. `cloud-next` 中必须排除 `node_modules`、`.next`、日志和 pulled env 文件，不得把它们写进项目实现层。
