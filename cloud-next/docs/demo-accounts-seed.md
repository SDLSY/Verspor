# Demo Accounts Seed 说明

## 目的
本说明对应 `cloud-next/scripts/demo-accounts/seed.mjs`，用于在独立演示环境中快速创建演示账号并灌入后台可见数据。

该脚本解决两件事：
- 创建或更新 5 个演示用户账号和 1 个后台管理员账号。
- 按场景重建后台会实际读取的业务表，保证 `dashboard`、`patients`、`reports`、`recommendations`、`system/jobs` 有可讲内容。

## 账号来源
场景定义文件：
- `src/lib/demo/demo-accounts.config.json`

当前固定场景：
- `demo_baseline_recovery`
- `demo_report_doctor_loop`
- `demo_lifestyle_loop`
- `demo_live_intervention`
- `demo_high_risk_ops`
- `demo_admin_console`

## 环境变量
必需：
- `NEXT_PUBLIC_SUPABASE_URL`
- `SUPABASE_SERVICE_ROLE_KEY`
- `DEMO_ACCOUNT_DEFAULT_PASSWORD`

可选：
- `DEMO_ADMIN_DEFAULT_PASSWORD`
- `DEMO_ACCOUNT_EMAIL_DOMAIN`

说明：
- 管理员密码默认沿用 `DEMO_ACCOUNT_DEFAULT_PASSWORD`。
- 邮箱模板固定为 `${scenario}@${DEMO_ACCOUNT_EMAIL_DOMAIN}`。

## 执行方式
在 `cloud-next` 目录执行：

```bash
npm run seed:demo-accounts
```

脚本会输出：
- 场景 code
- 角色
- 邮箱
- 显示名
- 插入的业务行数

## 账号 metadata 约定
每个账号都会写入：
- `demoRole`
- `demoScenario`
- `demoSeedVersion`
- `displayName`
- `username`
- `full_name`

其中：
- `demo_user` 用于 Android 和后台联合演示。
- `demo_admin` 只用于后台登录。

## 重建策略
脚本是可重复执行的：
- 先创建或更新账号。
- 再按 `user_id` 删除演示相关业务表中的旧数据。
- 最后插入新的种子数据。

当前重建的表：
- `sleep_sessions`
- `nightly_reports`
- `anomaly_scores`
- `intervention_tasks`
- `intervention_executions`
- `medical_reports`
- `medical_metrics`
- `doctor_inquiry_summaries`
- `assessment_baseline_snapshots`
- `medication_analysis_records`
- `food_analysis_records`
- `recommendation_traces`
- `audit_events`
- `inference_jobs`

## 场景覆盖
### demo_baseline_recovery
- 30 天睡眠、夜间报告、异常分。
- 少量已完成干预执行。
- 低风险建议轨迹。

### demo_report_doctor_loop
- 1 份异常医检报告和 2 条异常指标。
- 1 条问诊摘要。
- 1 个待执行任务和 1 条已执行记录。

### demo_lifestyle_loop
- 1 条药物记录。
- 3 条餐次饮食记录。
- 1 条与生活方式有关的建议轨迹。

### demo_live_intervention
- 近期睡眠与恢复基线。
- 3 个干预任务，2 条已完成执行。
- 用于现场继续追加执行记录的低风险基线。

### demo_high_risk_ops
- 持续低恢复分。
- 高风险报告与异常指标。
- 失败作业 + 排队作业。
- 待处理任务与高风险建议轨迹。

## 与 Android demo bootstrap 的关系
这份脚本只负责云端和后台可见数据。  
Android 端主页面仍然先读本地 Room，因此另外还有一条 `/api/demo/bootstrap` 本地快照链路：

- Android 登录后识别 demo 账号。
- 拉取 `/api/demo/bootstrap`。
- 清空旧演示本地库并导入对应场景快照。

两者关系：
- `seed.mjs` 负责后台闭环。
- `/api/demo/bootstrap` 负责 Android 首屏可见内容。

## 管理员账号注意事项
`demo_admin_console` 账号创建后，还需要把对应邮箱加入：

- `ADMIN_EMAIL_ALLOWLIST`

否则后台会被未授权拦截。

## 风险说明
- 该脚本假定当前环境是独立演示环境。
- 不建议在生产环境直接执行。
- 如果演示环境已存在其他真实数据，请先确认是否允许清理相同 `user_id` 下的数据。
