> 文档状态：内部规划文档
>
> 本文档用于记录阶段性工程化提升计划，不保证与当前代码状态一一对应。
> 若需确认当前真实模块结构、运行入口和迁移状态，请优先参考
> `docs/10_项目事实与架构/MODULE_MAP.md`、`docs/10_项目事实与架构/TECH_REFACTOR_STATUS.md` 和 `docs/10_项目事实与架构/06_技术文档基线_2026-03-05.md`。
# 工程结构与可维护性提升计划

更新时间：2026-03-06

## 目标

在不改变现有页面、交互和业务功能的前提下，把项目从“可演示原型”提升到“可持续开发的工程化项目”。

目标分数：
- 工程结构：`5/10 -> 8/10`
- 可维护性：`4/10 -> 7/10`

约束：
- 不允许页面回退
- 不允许已有核心链路失效
- 每一阶段都必须保持 Android 与 cloud-next 可构建

## 当前问题

### 1. 结构问题
- Android 历史上以 `:app` 单体为主，当前虽然已经引入 `app-shell/core-*`，但 `repository/network/ui` 仍未彻底拆分。
- 业务边界不清晰，UI、数据访问、设备协议、AI 调用还存在跨层耦合。
- Cloud 已有 `/api/v2/*` 骨架，但旧接口和新接口的职责边界还没有完全稳定。

### 2. 可维护性问题
- 关键流程主要靠人工验证，自动化测试覆盖不足。
- 真机回归、蓝牙链路回归、端到端回归缺失。
- 代码中仍有较多超大类、隐式依赖和历史兼容逻辑。
- 目前“能编译”不等于“能安全持续修改”。

## 总体策略

采用“稳态迁移”方案，而不是一次性推翻重写。

核心原则：
- 先固化边界，再迁移实现
- 先补验证，再扩大改动面
- 先拆底层，再拆 UI
- 每一阶段都以“行为等价”为验收标准

## 计划分期

### Phase 1：稳定当前重构基线（1 周）

目标：
- 让当前多模块状态成为稳定起点，而不是临时改造结果。

任务：
- 固化 `app-shell` 作为新启动模块
- 清理 `app-shell` 中遗留的占位壳代码
- 确认 `core-model/core-common/core-db/core-ble` 为真实编译边界
- 更新重构状态文档，保证代码与文档口径一致

验收标准：
- `:core-model :core-common :core-db :core-ble :app-shell` 全部可编译
- `:app-shell:testDebugUnitTest` 通过
- `cloud-next npm run lint && npm run build` 通过

### Phase 2：拆分数据访问层（1-2 周）

目标：
- 把数据访问从 UI 和应用入口中剥离出来。

任务：
- 将 `repository/*` 迁入 `core-data`
- 将 `network/*` 迁入 `core-network`
- 明确 `core-data -> core-db/core-network/core-model`
- 禁止 UI 直接访问 DAO、Retrofit、设备协议
- 整理 `DataManager`，将其职责缩减为协调层或删除

验收标准：
- `ui/*` 中不再直接依赖 `database/*` 和 `network/*`
- `SleepHealthApp` 不再直接初始化多个底层单例
- `repository` 成为唯一数据聚合入口

### Phase 3：拆分 AI/ML 运行时边界（1 周）

目标：
- 把 AI 与模型能力从业务层剥离，避免继续扩散。

任务：
- 将 `ai/*` 的客户端调用迁入 `core-network` 或 `core-ml`
- 将端侧模型装载与推理逻辑集中到 `core-ml`
- 为 `BuildConfig` 相关配置建立单独配置访问层
- 统一 AI 调用失败时的降级路径

验收标准：
- UI/ViewModel 不直接访问 `OpenRouterDoctorApiClient` 或模型装载类
- AI 接口调用具备统一入口和错误处理
- 本地模型、云端模型、规则回退三类路径能清晰区分

### Phase 4：拆分 Feature UI（2-3 周）

目标：
- 让页面真正落入各 feature 模块，而不是继续由 `app-shell` 承担。

任务：
- 迁移 `ui/home -> feature-home`
- 迁移 `ui/device -> feature-device`
- 迁移 `ui/doctor -> feature-doctor`
- 迁移 `ui/relax -> feature-relax`
- 迁移 `ui/trend -> feature-trend`
- 迁移 `ui/profile -> feature-profile`
- `app-shell` 只保留：
  - `MainActivity`
  - 导航
  - 全局应用初始化

验收标准：
- 各 feature 模块都能独立参与编译
- `app-shell` 不再直接承载具体页面实现
- 功能入口、导航图、资源引用保持与现有行为一致

### Phase 5：补全测试体系（2 周）

目标：
- 把“能跑”升级为“能验证”。

任务：
- 为 `core-model` 和 `core-common` 补纯 JVM 单测
- 为 `core-db` 补 Repository/DAO 组合测试
- 为 `core-network` 补接口契约测试
- 为 `feature-doctor`、`feature-device` 补关键 ViewModel 单测
- 建立三条关键 E2E 用例：
  - 蓝牙连接与数据接收
  - 睡眠数据上传与分析
  - 医生建议与干预执行

验收标准：
- 核心模块具备基础单测
- 关键链路具备最少一条自动化回归
- 新增改动不再完全依赖手工点击验证

### Phase 6：清理技术债与发布门禁（1 周）

目标：
- 让项目进入可持续迭代状态。

任务：
- 处理超大类，设定单文件大小阈值
- 增加静态检查清单
- 固化 PR 合并前检查项
- 为构建、测试、云端 API、ML smoke 建立统一门禁
- 制定“禁止新增耦合”的规则

验收标准：
- 新增代码必须进入既定模块
- 不再允许 UI 层直接穿透到底层实现
- 发布前检查项可自动执行

## 优先级排序

必须先做：
1. Phase 1
2. Phase 2
3. Phase 4

必须并行推进：
1. Phase 3
2. Phase 5

最后收口：
1. Phase 6

## 技术规则

### Android 侧规则
- `app-shell` 只负责启动、导航、全局装配
- `feature-*` 只负责页面与 ViewModel
- `core-data` 是数据聚合入口
- `core-db` 不允许被 UI 直接访问
- `core-network` 不允许被页面直接访问
- `core-ble` 不允许被页面直接访问
- `core-model` 只放纯模型，不放 Android 依赖

### Cloud 侧规则
- 旧 `/api/*` 继续服务现有客户端
- 新功能统一落入 `/api/v2/*`
- 服务端领域按 `auth/sleep/intervention/report/model/job` 维持
- 返回结构继续统一为 `code/message/data/traceId`

### 可维护性规则
- 单文件超过 `500` 行就要拆分
- ViewModel 只做编排，不写协议解析和数据库细节
- 新功能没有测试，不允许视为完成
- 新接口没有契约说明，不允许直接并入主干

## 量化指标

### 结构指标
- `app-shell` 代码量下降到只保留壳层
- `ui/*` 全部归属 `feature-*`
- `repository/network/database/bluetooth` 全部归属 `core-*`
- UI 到 DAO/Retrofit/BLE 的直接引用数量降为 `0`

### 可维护性指标
- 关键链路具备自动化回归
- 核心模块具备单测
- 每次改动都能通过固定构建门禁
- 真机回归清单固定化

## 建议执行顺序

如果按我来继续推进，顺序会是：
1. 先完成 `core-data/core-network` 迁移
2. 再拆 `feature-doctor` 和 `feature-device`
3. 再拆剩余页面模块
4. 最后补测试和发布门禁

原因：
- `doctor/device` 两块最复杂，先拆掉，项目后续维护难度会明显下降。
- `home/profile/trend` 相对简单，后拆风险更低。

## 结论

这个项目不需要推倒重来。

正确做法是：
- 保住现有页面和功能
- 把底层能力和页面实现逐步迁到明确边界里
- 用测试和门禁把“能演示”变成“能维护”

做到这一步后，项目的工程结构和可维护性分数可以明显提升，而且不会破坏现在已经能用的产品形态。


