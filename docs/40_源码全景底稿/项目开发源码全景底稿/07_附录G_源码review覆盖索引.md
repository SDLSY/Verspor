# 标题
附录 G：源码 review 覆盖索引

- 适用任务：证明 review 范围、核对文档事实覆盖、后续维护
- 阅读优先级：中高
- 是否允许对外直接引用：允许，主要用于内部核查

## 1. review 方法说明

本索引的目标不是罗列每个文件的逐行笔记，而是确认：

- 哪些目录属于项目自有源码
- 哪些目录已纳入事实基线
- 哪些目录是 current
- 哪些目录是 legacy
- 哪些目录要显式排除

## 2. 现行 Android 事实层

| 路径 | 类型 | 角色 | 是否进入主文档 |
| --- | --- | --- | --- |
| `app-shell` | current | Android 唯一运行入口、壳层、采集服务、机器人全局层 | 是 |
| `core-common` | current | 导航、主题、公共 UI、资源与部分共享控制器 | 是 |
| `core-model` | current | 领域模型、协议目录、核心数据类 | 是 |
| `core-data` | current | 仓储、编排、同步、bootstrap 与业务服务 | 是 |
| `core-ble` | current | 戒指 BLE、协议与解析 | 是 |
| `core-network` | current | 会话、API、网络模型、部分第三方能力接入 | 是 |
| `core-db` | current | Room、DAO、实体、迁移 | 是 |
| `core-ml` | current | 本地模型、解析器、轻量 AI / 规则回退 | 是 |
| `feature-home` | current | 今日与健康数据页面 | 是 |
| `feature-device` | current | 设备连接、扫描、状态联动 | 是 |
| `feature-doctor` | current | 问诊、评估、问诊单与医生页体验 | 是 |
| `feature-relax` | current | 症状引导、报告、药物 / 饮食、呼吸、Zen、干预、复盘 | 是 |
| `feature-trend` | current | 周 / 月趋势、周期总结 | 是 |
| `feature-profile` | current | 我的、登录、设置四页、隐私与关于 | 是 |

## 3. 云端与后台事实层

| 路径 | 类型 | 角色 | 是否进入主文档 |
| --- | --- | --- | --- |
| `cloud-next/src/app` | current | 页面与 API 路由 | 是 |
| `cloud-next/src/lib` | current | 后台聚合、AI 编排、模型与业务逻辑 | 是 |
| `cloud-next/src/components` | current | 后台壳与 UI 共享组件 | 是 |
| `cloud-next/scripts` | current | 种子脚本、演示脚本、支撑工具 | 是 |
| `cloud-next/supabase` | current | 数据库迁移 | 是 |

## 4. 模型与共享契约事实层

| 路径 | 类型 | 角色 | 是否进入主文档 |
| --- | --- | --- | --- |
| `contracts` | current | 共享 schema / DTO 契约层 | 是 |
| `ml` | current | 训练、评估、导出脚本 | 是 |
| `hf-inference` | current | ONNX 睡眠推理服务 | 是 |
| `tools` | current-support | 必要支撑工具，按需引用 | 条件进入 |

## 5. legacy 与历史参考层

| 路径 | 类型 | 角色 | 是否进入主文档 |
| --- | --- | --- | --- |
| `app` | legacy | 历史单体归档，与现行模块做映射 | 是，但只按映射说明进入 |

## 6. 显式排除层

以下目录不应进入项目事实层：

| 路径 | 原因 |
| --- | --- |
| `cloud-next/node_modules` | 依赖，不是项目自有源码 |
| `cloud-next/.next` | 构建产物 |
| `.gradle` | 构建缓存 |
| 各模块 `build/` | 构建产物 |
| `tmp` / `_tmp` / `.tmp` | 临时目录 |
| `captures` / `debug_export` | 调试与截图产物 |
| 各类 `.db` / 日志文件 | 运行产物，不是实现源码 |

## 7. 后续维护规则

如果未来继续更新这套底稿，应先做这几件事：

1. 先确认新的模块或目录是不是 current 事实层。
2. 如果是 current 层，先登记到本索引。
3. 再补主文档与对应附录。
4. 如果是 legacy、新实验目录或构建产物，必须先标清身份，再决定是否写入。
