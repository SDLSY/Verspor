# SRM_V2 配置化方案

更新时间：2026-03-10

## 1. 目标
SRM_V2 不改变当前 SRM_V1 的业务语义，而是把以下内容从代码硬编码迁移到数据库配置：
- 阈值
- 权重
- 风险门控
- 建议模式优先级
- 解释置信度参数

本轮只定义配置化方案，不切换线上主链。

## 2. 设计原则
- 线上建议主链继续使用 `SRM_V1`。
- `SRM_V2` 先以只读配置形式存在。
- 配置项必须版本化，支持灰度和回滚。
- 配置结构优先 JSONB，避免为每个参数拆一张窄表。
- 安全门控与建议模式分开存，避免后续调整互相污染。

## 3. 建议的数据库结构

### 3.1 recommendation_model_profiles
建议新增一张配置主表：
- `id uuid`
- `model_code text` 例如 `SRM_V2`
- `profile_code text` 例如 `default_adult_cn`
- `status text` 例如 `draft / active / archived`
- `description text`
- `thresholds_json jsonb`
- `weights_json jsonb`
- `gate_rules_json jsonb`
- `mode_priorities_json jsonb`
- `confidence_formula_json jsonb`
- `created_at timestamptz`
- `updated_at timestamptz`

### 3.2 配置字段建议

#### thresholds_json
```json
{
  "sleepDisturbance": 60,
  "stressLoad": 60,
  "fatigueLoad": 60,
  "recoveryCapacityLow": 40
}
```

#### weights_json
```json
{
  "evidenceCoverage": 0.45,
  "evidenceCount": 0.25,
  "hypothesisCount": 0.30
}
```

#### gate_rules_json
```json
{
  "redFlagGate": "RED",
  "highMedicalRiskGate": "RED",
  "mediumRiskGate": "AMBER"
}
```

#### mode_priorities_json
```json
{
  "RED": ["ESCALATE"],
  "AMBER": ["RECOVERY", "STRESS_REGULATION", "FOLLOW_UP"],
  "GREEN": ["SLEEP_PREP", "RECOVERY", "FOLLOW_UP"]
}
```

#### confidence_formula_json
```json
{
  "coverageWeight": 0.40,
  "missingPenaltyWeight": 0.35,
  "riskSignalWeight": 0.25
}
```

## 4. 运行时架构
建议新增一个只读配置层：
- `RecommendationModelConfigRepository`
- `loadActiveScientificModelProfile(modelCode, profileCode)`
- `getFallbackScientificModelProfile()`

运行顺序：
1. 优先读取活动配置
2. 若配置缺失或结构非法，回退到 SRM_V1 内置常量
3. 将最终生效配置写入 trace metadata，保证可追溯

## 5. 与当前代码的关系
- `cloud-next/src/lib/recommendation-model/scientific-model.ts` 继续作为 SRM_V1 主实现
- 新配置表和读取层先并存，不切流量
- 迁移完成后，再考虑把阈值和权重从硬编码提取出去

## 6. 不做项
本轮明确不做：
- 在线学习
- contextual bandit
- offline RL
- 因果效应估计直接进主链
- 自动从 trace 反推线上策略参数

## 7. 建议的迁移顺序
1. 建表并写入第一版默认配置
2. 增加只读配置加载器
3. 在 trace 中记录“实际生效配置版本”
4. 灰度一小部分链路到 `SRM_V2` 只读参数
5. 再评估是否完全替换 `SRM_V1` 常量
