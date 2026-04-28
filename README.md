# 长庚环 VesperO

长庚环是一套围绕智能戒指构建的端云协同健康辅助作品。它把 Android App、云端服务、睡眠分期模型、AI 问诊与干预反馈连接在一起，面向睡眠恢复、日常健康管理和居家康复辅助场景，提供从数据采集到建议执行的完整体验。

项目的核心想法很直接：让智能戒指持续记录睡眠、心率、血氧、体温、运动和 PPG 等信号，由手机端完成交互、展示和本地分析，再由云端补充模型推理、报告理解、问诊增强和管理后台能力。用户看到的是一个健康助手，开发侧则由多模块 Android、Next.js 云服务、Supabase 数据底座和模型服务共同支撑。

## 项目亮点

- 智能戒指接入：支持 BLE 扫描、连接、工作参数读取、实时波形和后台采集。
- 睡眠与恢复分析：展示睡眠摘要、恢复分、生理指标、异常波动和趋势复盘。
- AI 医生问诊：支持文本输入、语音输入、风险提示、问诊摘要和结构化结果保存。
- 医检报告理解：支持 OCR 后的报告整理、指标解释和风险提示。
- 药物与饮食分析：面向药品图片和饮食图片提供辅助识别、风险提示和记录回写。
- 个性化干预：包含症状自查、呼吸训练、干预任务、执行记录和复盘反馈。
- 桌面机器人讲解：Android 端提供页面讲解、点击讲解和语音播报体验。
- 云端管理后台：提供患者概览、睡眠报告、模型任务、审计日志和系统管理页面。

## 系统组成

| 模块 | 说明 |
| --- | --- |
| `app-shell/` | Android 应用入口，负责打包、全局导航、桌面机器人叠层和应用级配置 |
| `core-common/` | 公共资源、导航、主题、日志和通用 UI 支撑 |
| `core-model/` | 睡眠、恢复、设备、问诊、报告、干预等领域模型 |
| `core-data/` | Repository、云端同步、画像、处方、演示数据协调 |
| `core-ble/` | BLE 连接、Hi90B 协议、命令构帧和采样解析 |
| `core-network/` | Retrofit 接口、API DTO、隐私裁剪、讯飞与云端能力接入 |
| `core-db/` | Room 数据库、实体、DAO 和迁移 |
| `core-ml/` | 本地异常检测、PPG/HRV/体温分析、报告解析和边缘推理辅助 |
| `feature-home/` | 今日状态、晨报、恢复分和建议展示 |
| `feature-device/` | 设备扫描、连接、实时数据和采集控制 |
| `feature-doctor/` | 医生问诊、语音交互、风险评估和问诊记录 |
| `feature-relax/` | 症状自查、报告理解、药物饮食分析、干预、呼吸和复盘 |
| `feature-trend/` | 睡眠趋势、恢复趋势和周期报告 |
| `feature-profile/` | 我的页面、账户、个人资料、隐私和通知 |
| `cloud-next/` | Next.js 云端 API、Web 展示页、管理后台、AI provider 编排 |
| `contracts/` | Android 与云端共享的数据契约 |
| `ml/` | 模型训练、导出、推理实验和边缘演示脚本 |
| `hf-inference/` | 睡眠分期 ONNX 推理服务，可部署到 Hugging Face Space |
| `tools/` | 图表生成、证据整理、模型资产处理和演示辅助脚本 |

## 功能体验

### Android App

App 以五个一级页面组织主要体验：

- 今日：睡眠摘要、恢复分、生理指标、异常波动和建议。
- 医生：文本/语音问诊、红旗风险提示、结构化建议和历史会话。
- 趋势：睡眠、恢复、生理指标和周期报告。
- 设备：智能戒指扫描、连接、断开、参数读取和采集服务控制。
- 我的：登录、个人资料、通知、隐私、关于和云端账户入口。

此外，App 还提供症状自查、医检报告分析、药物分析、饮食分析、呼吸训练、干预执行、放松复盘和 3D 人体交互等扩展页面。

### Web 与云服务

Web 端访问地址：

```text
https://cloud.changgengring.cyou/
```

云端工程位于 `cloud-next/`，基于 Next.js App Router 和 Route Handlers 构建，承担以下职责：

- 用户注册、登录、刷新和密码重置。
- 睡眠数据上传、分析任务入队和报告查询。
- 医生问诊、报告理解、建议生成和干预回写。
- 多模态 AI 能力，包括语音转写、语音合成、图片生成和视频任务。
- 管理后台页面，包括患者概览、任务队列、模型配置和审计日志。

### 睡眠分期模型服务

模型服务页面：

```text
https://huggingface.co/spaces/1new/sleep-transformer-v2
```

健康检查：

```text
https://1new-sleep-transformer-v2.hf.space/health
```

推理接口：

```text
POST https://1new-sleep-transformer-v2.hf.space/
```

该服务使用 ONNX Runtime 运行 `sleep-transformer-v2.onnx`，输入为按睡眠窗口聚合后的 17 维生理特征，输出 WAKE、N1、N2、N3、REM 五类睡眠阶段、置信度和异常分数。若服务端开启了 `API_TOKEN`，调用时需要携带：

```text
Authorization: Bearer <评审或部署环境提供的 Token>
```

## 作品安装说明

### Android 安装包

GitHub 仓库提供 Android 安装包：

```text
releases/android/app-release.apk
```

该 APK 通过 Git LFS 存储。如果直接在 GitHub 页面下载，请打开文件后点击 Download；如果通过 Git 克隆仓库，请先安装 Git LFS 并执行：

```powershell
git lfs pull
```

安装步骤：

1. 将 `app-release.apk` 复制到 Android 手机。
2. 在系统设置中允许“安装未知来源应用”。
3. 点击 APK 完成安装。
4. 打开应用后，可进入设备页连接智能戒指；没有实物设备时，可使用演示数据体验主要页面。

也可以从源码构建 Debug 安装包：

```powershell
.\gradlew.bat :app-shell:assembleDebug
```

Debug APK 生成后通常位于：

```text
app-shell/build/outputs/apk/debug/app-shell-debug.apk
```

安装到已连接的 Android 设备：

```powershell
.\gradlew.bat :app-shell:installDebug
```

Release 包构建命令：

```powershell
.\gradlew.bat :app-shell:assembleRelease
```

Release 构建需要根目录提供 `keystore.properties` 和对应签名文件。没有签名配置时，请使用 Debug 包进行本地体验。

### Web 端访问

浏览器打开：

```text
https://cloud.changgengring.cyou/
```

部分后台页面需要登录账号和云端环境配置。公开展示页可直接访问。

### 模型服务访问

浏览器打开健康检查地址：

```text
https://1new-sleep-transformer-v2.hf.space/health
```

如果返回类似内容，说明模型服务已加载：

```json
{"status":"ok","model_loaded":true}
```

## 开发运行

### Android

常用命令：

```powershell
.\gradlew.bat :app-shell:assembleDebug
.\gradlew.bat :app-shell:installDebug
.\gradlew.bat :app-shell:testDebugUnitTest
.\gradlew.bat :app-shell:lint
```

核心模块单测：

```powershell
.\gradlew.bat :core-ble:testDebugUnitTest
.\gradlew.bat :core-network:testDebugUnitTest
.\gradlew.bat :core-db:testDebugUnitTest
.\gradlew.bat :core-data:testDebugUnitTest
.\gradlew.bat :core-ml:testDebugUnitTest
```

Android 端常用配置来自 `local.properties`、Gradle properties 和构建字段。涉及云端 API、OpenRouter、讯飞能力和签名文件的配置不应提交到公开仓库。

### Cloud

```powershell
cd cloud-next
npm install
npm run dev
npm run lint
npm run build
```

生产部署使用 Vercel。项目已包含 `vercel.json`，并配置了内部 worker 的定时任务。

### ONNX 推理服务

```powershell
cd hf-inference
uvicorn app:app --host 0.0.0.0 --port 7860
```

本地健康检查：

```text
http://127.0.0.1:7860/health
```

Docker 部署可直接使用 `hf-inference/Dockerfile`。

## 环境配置

### Android

常见配置项包括：

- API 基地址：`DEBUG_API_BASE_URL`、`RELEASE_API_BASE_URL`
- OpenRouter 模型配置
- 讯飞 IAT、RTASR、RAASR、TTS、OCR、Spark、AIUI 和虚拟人相关密钥
- Release 签名配置：`keystore.properties`

### Cloud

请基于 `cloud-next/.env.example` 准备环境变量，常见分组如下：

- Supabase：`NEXT_PUBLIC_SUPABASE_URL`、`NEXT_PUBLIC_SUPABASE_ANON_KEY`、`SUPABASE_SERVICE_ROLE_KEY`
- 内部任务：`INTERNAL_WORKER_TOKEN`、`CRON_SECRET`
- AI provider：`OPENROUTER_*`、`VECTOR_ENGINE_*`、`DEEPSEEK_*`、`DOUBAO_TTS_*`
- 模型服务：`MODEL_INFERENCE_TOKEN`

## 目录说明

```text
app-shell/        Android 应用宿主
core-*/           Android 公共能力与数据层
feature-*/        Android 业务页面
cloud-next/       云端 API、Web 页面和管理后台
contracts/        跨端数据契约
ml/               训练、导出和实验脚本
hf-inference/     ONNX 睡眠分期服务
tools/            工程辅助脚本
app/              早期 Android 代码归档
```

## 使用提示

长庚环提供的是健康辅助和信息整理能力，不替代医生诊断，也不自动开具医疗处方。涉及胸痛、呼吸困难、持续高热、严重过敏、意识异常等紧急症状时，应优先线下就医。

仓库中包含 Android、云端、模型和工具脚本。首次阅读时建议先运行 Android 或打开 Web 展示页，再按需要进入具体模块源码。
