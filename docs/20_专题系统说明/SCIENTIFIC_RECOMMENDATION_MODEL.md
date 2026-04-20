# 科学建议生成模型（SRM_V2）

更新时间：2026-03-10

## 1. 模型定位
SRM_V2 不是端到端黑盒模型，也不是单一 prompt 生成器。
它的定位是：

- 多源证据整合层
- 域级假设层
- 安全闸门层
- 配置化策略层
- LLM 解释与表达层
- 可解释追踪层

当前主目标不是“自动学策略”，而是把建议生成从纯经验逻辑提升为：
- 可解释
- 可追踪
- 可审计
- 可配置
- 可逐步演进

## 2. 证据如何整合
当前后端先整合结构化证据，再进入建议生成，不是把所有信息直接拼成一段 prompt。

主要证据来源：
- 设备/戒指：恢复分、生命体征、趋势信号
- 睡眠会话：时长、效率、睡眠分期、夜醒等
- 量表基线：失眠、嗜睡、压力、焦虑、抑郁、幸福感
- 医生问诊：主诉、缺失信息、红旗、疑似问题、阶段判断
- 医检报告：OCR 文本、异常指标、风险等级
- 干预执行：执行效果、压力下降、最近执行摘要
- 云端上下文：最近夜间报告、补充证据、推荐偏好和降权信息

统一抽象为：
- `ScientificEvidenceItem`
- `ScientificHypothesis`
- `ScientificRecommendationSheet`

## 3. 核心推断机制
### 3.1 域级假设
当前不是直接从输入跳到建议，而是先形成域级假设，例如：
- `SLEEP_PREP`
- `STRESS_REGULATION`
- `RECOVERY`
- `ESCALATE`
- `FOLLOW_UP`
- `STABILIZE`

每个假设都显式记录：
- `score`
- `evidenceLabels`
- `rationale`

### 3.2 安全闸门
当前输出先经过安全闸门，再决定建议模式。

安全闸门：
- `GREEN`
- `AMBER`
- `RED`

高风险医检和红旗线索优先级高于自由生成文案。当前这部分是明确的工程规则，而不是让模型自行判断是否升级。

### 3.3 配置化策略选择
当前运行时会优先从 `recommendation_model_profiles` 读取活动策略档案。
若数据库尚未执行 `0008_recommendation_model_profiles.sql`，则自动回退到内置默认档案 `default_adult_cn`。

当前配置化范围包括：
- `thresholds`
- `weights`
- `gateRules`
- `modePriorities`
- `confidenceFormula`

这意味着建议引擎已经从“代码里写死全部阈值”升级为：
- 数据库配置优先
- 代码内置默认档案兜底
- 同时保留稳定回退能力

## 4. 可解释输出与表达层
每次核心建议生成后，后端都会形成 `ScientificRecommendationSheet`，至少包含：
- `modelVersion`
- `profileCode`
- `configSource`
- `traceType`
- `safetyGate`
- `recommendationMode`
- `explanationConfidence`
- `evidenceCoverage`
- `sourceCoverage`
- `evidenceLedger`
- `hypotheses`
- `decisionSummary`
- `innovationNotes`

结构化决策形成后，会进入独立的 LLM 解释层：
- 先输出结构化 `ScientificRecommendationSheet`
- 再调用轻量文本模型生成 `summary / reasons / nextStep`
- 若模型不可用，自动回退到确定性解释文案

这不是给用户看的原始 prompt，而是后端可直接持久化、前端可直接消费的解释卡。

## 5. 当前接入位置
- 每日处方：`cloud-next/src/lib/prescription/engine.ts`
- 周期总结：`cloud-next/src/app/api/report/period-summary/route.ts`
- 医生问诊：`cloud-next/src/app/api/doctor/turn/route.ts`
- 建议追踪：`cloud-next/src/lib/recommendation-tracking.ts`
- 追踪表：`cloud-next/supabase/migrations/0007_recommendation_tracking.sql`
- 策略配置表：`cloud-next/supabase/migrations/0008_recommendation_model_profiles.sql`
- 配置加载器：`cloud-next/src/lib/recommendation-model/srm-v2-config.ts`
- LLM 解释层：`cloud-next/src/lib/recommendation-model/explanation.ts`

## 6. 当前优势与边界
### 优势
- 不是纯大模型直出
- 不是不可解释的黑盒规则堆砌
- 可以落库追踪“为什么给了这条建议”
- 可以通过数据库调整阈值、门控和优先级
- 可以直接支持答辩中的“证据驱动 + 配置化策略 + 可追踪闭环”叙述

### 边界
- 当前仍是配置化策略系统，不是学习型策略系统
- 还没有接 bandit / uplift / 因果评估
- `recommendation_model_profiles` 是否真正生效，取决于远端数据库是否执行 `0008` migration

SRM_V2 的准确定位应当是：
**配置化、证据驱动、可解释、可追踪的建议决策与表达框架。**
