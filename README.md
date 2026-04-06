# 长庚环 / VesperO

长庚环是一个以智能戒指为感知入口、以 Android 应用为交互中心、以云端 AI 与后台工作台为增强能力的端云协同健康辅助系统。项目主线不是“展示更多监测图表”，而是把睡眠、恢复、生理指标、问诊、医检、药食分析与干预执行收敛成一条可验证的业务闭环：

`感知 -> 分析 -> 解释 -> 建议 -> 执行 -> 回写 -> 趋势 / 复盘`

## 当前项目状态

- Android 当前唯一运行入口是 `:app-shell`，不再通过 `sourceSets` 编译 `app/`。
- Android 已按 `core-* + feature-*` 组织现行代码，`app/` 仅作为 legacy 归档保留。
- 云端主线位于 `cloud-next/`，承载 API、管理员后台、演示账号 bootstrap、模型编排与数据写回。
- 睡眠分析、建议生成、桌面机器人、豆包 TTS、药物/饮食分析、医生问诊、干预执行与回写链路均已接入当前主线。
- 仓库中同时保留了训练、导出和部署相关的模型脚本，但不能把“支持能力”误写成“Android 端默认全量运行”。

## 核心能力

### 1. 睡眠分析链路

- 使用 `WAKE / N1 / N2 / N3 / REM` 五阶段标签体系。
- 训练与研究层保留 baseline、聚合特征 Transformer、导出与推理脚本。
- 当前系统证明的是“端云协同睡眠分析链路”可用；Android 端本地长期在线的是轻量异常检测与兜底能力，而不是完整五阶段分期主模型。

### 2. SRM_V2 混合建议引擎

- 证据层整合戒指数据、睡眠记录、量表、AI 问诊、医检 OCR、干预执行等结构化输入。
- 决策层通过确定性安全门控控制高风险升级，不让 LLM 直接承担高风险判定。
- 表达层负责证据压缩、推荐理由、执行说明与页面播报文案生成。

### 3. 业务闭环

- 戒指连接与采集 -> 今日页恢复分与生理数据 -> 趋势观察
- 医检报告上传 -> 可读化理解 -> 医生问诊 -> 干预中心
- 药物 / 饮食图片分析 -> 画像 / 解释 -> 建议上下文
- 干预生成 -> 执行 -> 回写 -> 复盘 / 趋势
- 桌面机器人 -> 页面讲解文案 -> 云端 TTS 播报

### 4. 演示与后台

- `cloud-next` 提供管理员后台，当前按“总览驾驶舱 / 闭环故事 / 患者工作台 / 报告与问诊 / 建议与效果 / 系统运维”组织。
- 仓库内已补充 demo 账号 seed、云端 bootstrap 与 Android 本地灌库链路，便于比赛演示与回归测试。

## 仓库结构

| 路径 | 作用 | 备注 |
| --- | --- | --- |
| `app-shell/` | Android 当前运行入口 | 当前唯一 APK 宿主 |
| `core-common/` | 公共能力、资源、日志、工具 | 现行模块 |
| `core-model/` | 核心领域模型与协议对象 | 现行模块 |
| `core-data/` | Repository、业务编排、同步与 demo bootstrap | 现行模块 |
| `core-ble/` | 戒指通信、协议与 demo 设备配置 | 现行模块 |
| `core-network/` | Retrofit / API / 网络协议层 | 现行模块 |
| `core-db/` | Room、DAO、实体与迁移 | 现行模块 |
| `core-ml/` | 端侧 AI / 规则能力与解析工具 | 现行模块 |
| `feature-home/` | 今日页、恢复分、晨报 | 现行模块 |
| `feature-device/` | 设备页、扫描、连接、采集状态 | 现行模块 |
| `feature-doctor/` | AI 医生、问诊、结构化结果 | 现行模块 |
| `feature-relax/` | 医检报告、药食分析、干预中心、Zen、呼吸训练 | 现行模块 |
| `feature-trend/` | 趋势、周 / 月报、复盘 | 现行模块 |
| `feature-profile/` | 我的、账号、设置、云端登录 | 现行模块 |
| `cloud-next/` | Next.js API、后台工作台、Supabase 集成、模型编排 | 云端主线 |
| `contracts/` | 共享 schema / DTO / 契约 | 端云对齐 |
| `ml/` | 训练、导出、推理、模型实验脚本 | 研究与部署支撑 |
| `tools/` | 自动化测试、证据生成、原型图 / 流程图脚本 | 项目工具层 |
| `app/` | 历史 Android 业务代码 | legacy 归档，不参与当前 APK 构建 |
| `docs/` | 项目文档、源码对齐资料、AI 投喂资料 | 文档入口 |

## 快速开始

### Android

```powershell
.\gradlew.bat :app-shell:assembleDebug
.\gradlew.bat :app-shell:installDebug
.\gradlew.bat :app-shell:testDebugUnitTest
.\gradlew.bat :app-shell:lint
```

### Cloud

```powershell
cd cloud-next
npm install
npm run dev
npm run build
npm run lint
```

### 核心模块单元测试

```powershell
.\gradlew.bat :core-ble:testDebugUnitTest
.\gradlew.bat :core-network:testDebugUnitTest
.\gradlew.bat :core-db:testDebugUnitTest
.\gradlew.bat :core-data:testDebugUnitTest
.\gradlew.bat :core-ml:testDebugUnitTest
```

## 测试与取证

- `tools/` 下已经补充了一批自动化脚本，用于：
  - 单元测试证据整理
  - 功能闭环取证
  - 第 4 章系统 / 模型性能测试数据采集
  - 原型图与流程图生成
- `test-evidence/` 为本地产生的测试证据目录，默认作为交付 / 取证产物使用，不作为仓库核心源码事实层。

## 文档入口

建议优先从以下入口理解项目，而不是从历史比赛材料或零散截图开始：

1. `README.md`
2. `cloud-next/README.md`
3. `app-shell/README.md`
4. `docs/` 下的源码对齐资料与项目总览

说明：

- `docs/` 是当前文档入口；历史提交模板、材料目录、截图与本地证据不作为源码事实入口。
- 如果需要比赛材料、开发文档或测试文档，建议基于 `docs/` 与 `test-evidence/` 的最新结果单独导出。

## 维护约定

- 根 README 只保留项目定位、主能力、仓库结构、运行方式和文档入口。
- 详细设计、ER 图、原型图、测试反填材料和 AI 投喂资料进入 `docs/` 或 `test-evidence/`。
- 当前源码事实优先于历史材料；不要把 legacy `app/`、本地生成文件或截图误写成当前主链。
- 不要把睡眠分析链路夸大为“Android 本地完整五阶段分期稳定部署”。
- 不要把 SRM_V2 写成“AI 自动诊断 / 自动处方 / 完全自主决策”。

## 相关说明

- 云端说明：`cloud-next/README.md`
- Android 宿主说明：`app-shell/README.md`
- 契约说明：`contracts/README.md`
- 文档维护说明：`docs/DOC_MAINTENANCE.md`
