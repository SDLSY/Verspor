# cloud-next 文档索引

本目录记录 `cloud-next/` 的活文档。它解决两个问题：

- 云端现在到底有哪些真实接口和部署约束
- 哪些文档是架构事实，哪些只是上线/演示辅助材料

在开始修改云端代码前，建议先阅读本文件，再进入具体文档。

## 建议阅读顺序

1. `cloud-next/README.md`
2. `cloud-next/docs/architecture.md`
3. `cloud-next/docs/api-v2-contract.md`
4. `cloud-next/docs/api-contract.md`
5. `cloud-next/docs/VECTOR_ENGINE_PROVIDER_ACCEPTANCE_2026-03-08.md`
6. `cloud-next/docs/implementation-backlog.md`

## 文档说明

| 文档 | 用途 | 何时阅读 |
| --- | --- | --- |
| `architecture.md` | 云端整体架构、运行形态、边界 | 理解服务职责、准备改架构时 |
| `api-v2-contract.md` | 新主线 `/api/v2/*` 契约说明 | 对齐 Android / Cloud v2 接口时 |
| `api-contract.md` | 兼容中的旧 API 契约 | 处理旧客户端或回归兼容时 |
| `VECTOR_ENGINE_PROVIDER_ACCEPTANCE_2026-03-08.md` | Vector Engine speech/image/video 实测结果与实际返回格式 | 打通多模态 provider 或排查返回格式时 |
| `MULTIMODAL_PROMPT_PROFILES.md` | speech/image/video 的 prompt profile 与使用规则 | 调整多模态生成质量时 |
| `demo-deployment.md` | 演示/本地部署步骤 | 准备部署演示环境时 |
| `implementation-backlog.md` | 云端待办与迁移缺口 | 排期和拆任务时 |
| `cloudflare-security-checklist.md` | Cloudflare 部署安全检查项 | 上线前或安全复核时 |
| `cloudflare-waf-ratelimit-rules.md` | WAF / 限流规则参考 | 配置网关安全策略时 |
| `observability-dashboard.sql` | 观测面板初始化 SQL | 初始化监控查询或排查运行状态时 |

## 使用约定

- 云端总入口仍以 `cloud-next/README.md` 为准。
- 当旧 `/api/*` 与新 `/api/v2/*` 描述冲突时，先核对代码，再更新 `api-v2-contract.md` 和 `api-contract.md`。
- 本目录只记录云端事实，不重复 Android 或 `contracts/` 的细节。
