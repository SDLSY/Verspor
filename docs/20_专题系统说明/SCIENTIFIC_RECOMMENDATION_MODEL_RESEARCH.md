# 建议生成模型前沿研究结论

更新时间：2026-03-10

## 1. 研究目标
本轮研究只回答一个问题：

**对 VesperO 这类健康干预产品，当前更前沿但仍可落地的建议生成架构应该长什么样。**

结论先说：
- 不适合直接把建议生成改成纯黑盒 LLM。
- 也不适合在现阶段直接上 contextual bandit、offline RL 或因果策略学习作为主链。
- 最适合当前项目的是：
  - 结构化证据整合
  - 检索增强
  - 域级假设
  - 安全门控
  - 生成式说明与行动文案
  - 效果追踪与离线评估预留位

## 2. 查到的前沿共识

### 2.1 LLM 不应直接替代推荐系统主干
多篇综述都指出，LLM 在推荐场景里更适合承担：
- 语义理解
- 长文本建模
- 解释生成
- 交互式追问
- 检索增强整合

而不是完全替代检索、排序、规则门控和安全边界。

参考：
- [A Survey on Large Language Models for Recommendation](https://arxiv.org/abs/2305.19860)
- [Large Language Models for Generative Recommendation: A Survey and Visionary Discussions](https://aclanthology.org/2024.lrec-main.886/)

### 2.2 前沿系统是“多阶段”而不是“一次生成”
较成熟的推荐系统架构仍然遵循多阶段模式：
- 候选证据检索
- 结构化特征整合
- 策略/门控
- 生成式解释或最终文案

对 VesperO 来说，推荐的不是商品，而是干预动作、睡眠建议、风险升级与问诊下一步。这类场景对安全闸门要求更高，因此更不适合端到端纯生成。

### 2.3 可解释推荐正在从“解释后生成”转向“解释即中间状态”
Explainable Recommendation 的更可靠方向，不是事后补一句理由，而是在生成前先形成：
- 证据账本
- 域级假设
- 风险等级
- 缺失输入

然后再输出建议。

参考：
- [On explaining recommendations with Large Language Models: a review](https://www.frontiersin.org/journals/big-data/articles/10.3389/fdata.2024.1505284/full)

### 2.4 离线评估比在线探索更适合当前阶段
前沿论文说明，推荐系统确实在往 off-policy evaluation、learning-to-rank 和 bandit 方向演进，但这些方法依赖：
- 稳定日志
- 清晰反馈定义
- 明确曝光策略
- 足够样本量

当前项目虽然已经开始记录 `recommendation_traces`，但还没形成稳定的“曝光 -> 执行 -> 效果”长期日志。因此不应把 bandit/RL 作为本阶段主链。

参考：
- [Top-K Off-Policy Correction for a REINFORCE Recommender System](https://arxiv.org/abs/1812.02353)
- [Pessimistic Off-Policy Optimization for Learning to Rank](https://arxiv.org/abs/2206.02593)
- [Off-Policy Evaluation in Embedded Spaces](https://arxiv.org/abs/2203.02807)

## 3. 对当前项目的直接启发

### 3.1 保留证据层，不做“纯 prompt 合成”
当前项目已经有：
- 戒指数据
- 睡眠记录
- 量表
- 医生问诊
- 医检 OCR
- 干预执行

这些都应先结构化整合，再给生成层使用。

### 3.2 LLM 主要承担三类任务
推荐在本项目里让 LLM 主要承担：
- 证据压缩与摘要
- 推荐理由和执行说明生成
- 页面播报文案与角色化解释

不建议让 LLM 直接决定：
- 红旗是否升级
- 是否允许继续训练
- 是否直接替代医生建议

### 3.3 安全门控要继续放在确定性层
对健康干预项目来说，安全门控必须独立于生成模型：
- 红旗优先
- 高风险医检优先
- 缺失输入显式降置信度
- 低覆盖证据不允许假装“高度确定”

### 3.4 当前最合适的路线是 SRM_V2
目标不是追求最前沿论文里的“最强策略学习”，而是构建一条稳妥演进路线：
- `SRM_V1`：代码内阈值 + 规则增强解释
- `SRM_V2`：数据库可配置阈值/权重/门控/优先级
- `SRM_V3`：加入离线评估、效果回写分析和策略比较
- 更后期才考虑：bandit / uplift / causal policy learning

## 4. 本轮结论
本项目下一步最有价值的创新点不是“换一个更大的模型”，而是：

**把当前建议生成系统演进成可配置、可解释、可评估的混合建议引擎。**

这条路线既符合当前工程成熟度，也能保留足够的技术含量和后续研究空间。
