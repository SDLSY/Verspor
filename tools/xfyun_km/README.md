# 讯飞知识库自动化

这个目录放两类内容：

- `knowledge/`：准备上传到讯飞超拟人交互知识库的 `.txt` 素材
- `../xfyun_km_bootstrap.py`：调用官方 `Interact_KM` 接口，完成建库、上传、绑定

## 前置条件
需要在仓库根目录 `local.properties` 中至少放入以下字段之一：

- `XFYUN_RAG_UID` 或 `XFYUN_UID`
- `XFYUN_RAG_API_PASSWORD` 或 `XFYUN_API_PASSWORD`
- `XFYUN_AIUI_APP_ID` 或 `XFYUN_APP_ID`
- `XFYUN_AIUI_SCENE`

其中：

- `uid` 与 `APIPassword` 来自讯飞知识库管理接口文档要求
- `appId + sceneName` 是要绑定知识库的数字人应用

## 一键执行

```powershell
py -3 .\tools\xfyun_km_bootstrap.py bootstrap
```

只做本地校验：

```powershell
py -3 .\tools\xfyun_km_bootstrap.py bootstrap --dry-run
```

## 默认行为

- 远端知识库名：`VesperO数字人医生知识库`
- 素材目录：`tools/xfyun_km/knowledge`
- 绑定阈值：`0.35`
- 本地结果输出：`tmp/xfyun_km_state.json`

## 官方接口对应关系

- 建库：`POST /aiuiKnowledge/rag/api/repo/create`
- 上传文档：`POST /aiuiKnowledge/rag/api/doc/saveRepoDoc`
- 绑定应用：`POST /aiuiKnowledge/rag/api/app/saveRepoConfig`

鉴权：

- Header：`Authorization: Bearer <APIPassword>`

## 素材约束

官方文档当前明确支持：

- `txt`
- `pdf`
- `doc`

首版素材全部使用 `.txt`，避免额外格式转换。
