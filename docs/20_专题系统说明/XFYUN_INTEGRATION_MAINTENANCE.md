# 讯飞能力接入维护文档

## 1. 当前落地范围
- Android 新增 `com.example.newstart.xfyun` 接入层。
- 已接入能力：
  - `IAT`：`XfyunIatWsClient`
  - `RTASR`：`XfyunRtasrWsClient`
  - `RAASR`：`XfyunRaasrClient`
  - `TTS`：`XfyunTtsWsClient`
  - `OCR 大模型`：`XfyunOcrClient`
  - `Spark Lite / Spark X1.5`：`XfyunSparkWsClient`
- 桌面机器人当前主链：`PageNarrationContext + DesktopAvatarPromptBuilder + DesktopAvatarNarrationService`
- 数字人实时问诊页：`DoctorLiveAvatarActivity` + `XfyunVirtualHumanController`
- 医检 OCR 主链已替换为讯飞 OCR，原 ML Kit 不再是主识别路径。

## 2. 当前行为定义

### 2.1 桌面机器人
- 不再做麦克风短对话。
- 当前只负责：
  - 页面说明
  - 当前状态提醒
  - 下一步入口引导
  - TTS 播报与气泡同步显示
- 触发方式：
  - 页面进入自动触发
  - 点击 Avatar 重播或重新生成当前页面说明
- 默认模型：`Spark Lite`
- 复杂上下文、高风险或长文本时可升级 `Spark X1.5`
- 默认音色：`x4_lingxiaoyao_em`

### 2.2 医生页
- `开始录音`：一次性语音转写
- `开始通话`：连续半双工模式
- 医生页 TTS 与桌面机器人统一走 `x4_lingxiaoyao_em`
- 为避免回声回灌：
  - 播报期间绝不启用 IAT
  - 播报结束后进入冷却窗口，再恢复监听

### 2.3 数字人页
- 数字人页继续存在，但不在本批次验收范围内。
- 角色控制依赖：
  - `cbmparams.nlp.prompt`
  - 已绑定的知识库
  - `avatar_id`

## 3. 代码位置
- 构建注入：`D:\newstart\app-shell\build.gradle.kts`
- 配置聚合：`D:\newstart\app\src\main\java\com\example\newstart\xfyun\XfyunConfig.kt`
- 鉴权：`D:\newstart\app\src\main\java\com\example\newstart\xfyun\auth\XfyunAuthSigners.kt`
- 桌面机器人：
  - `D:\newstart\app\src\main\java\com\example\newstart\ui\avatar\PageNarrationContext.kt`
  - `D:\newstart\app\src\main\java\com\example\newstart\ui\avatar\DesktopAvatarPromptBuilder.kt`
  - `D:\newstart\app\src\main\java\com\example\newstart\ui\avatar\DesktopAvatarNarrationPolicy.kt`
  - `D:\newstart\app\src\main\java\com\example\newstart\ui\avatar\DesktopAvatarNarrationService.kt`
- 数字人页：`D:\newstart\app\src\main\java\com\example\newstart\ui\doctor\DoctorLiveAvatarActivity.kt`
- 数字人控制器：`D:\newstart\app\src\main\java\com\example\newstart\xfyun\virtual\XfyunVirtualHumanController.kt`
- OCR 替换点：`D:\newstart\app\src\main\java\com\example\newstart\ui\relax\MedicalReportAnalyzeFragment.kt`

## 4. 本地配置
这些值不要提交到仓库，只放在 `local.properties` 或 Gradle `-P` 参数中。

### 4.1 全局兜底
- `XFYUN_APP_ID`
- `XFYUN_API_KEY`
- `XFYUN_API_SECRET`

### 4.2 服务级覆盖
- `XFYUN_IAT_*`
- `XFYUN_RTASR_*`
- `XFYUN_RAASR_*`
- `XFYUN_TTS_*`
- `XFYUN_OCR_*`
- `XFYUN_SPARK_LITE_*`
- `XFYUN_SPARK_X_*`
- `XFYUN_AIUI_*`
- `XFYUN_VH_AVATAR_ID`
- `XFYUN_AIUI_DOCTOR_PROMPT`（可选）

### 4.3 默认建议值
- `XFYUN_SPARK_LITE_DOMAIN=lite`
- `XFYUN_SPARK_X_DOMAIN=x1`
- `XFYUN_AIUI_SCENE=sos_app`
- `XFYUN_TTS_VOICE_NAME=x4_lingxiaoyao_em`
- `XFYUN_VH_VOICE_NAME=x4_lingxiaoyao_em`

## 5. 鉴权类型
- `IAT / TTS / Spark`：HMAC WebSocket
- `OCR`：HMAC HTTP
- `RTASR / RAASR`：`appid + ts + signa`

## 6. 知识库
- 已有自动化脚本：`D:\newstart\tools\xfyun_km_bootstrap.py`
- 当前知识库已支持建库、上传和绑定
- 仍需要本地保留：
  - `XFYUN_UID`
  - `XFYUN_API_PASSWORD`

## 7. 当前维护重点
1. 桌面机器人只做页面播报，不再回退到麦克风短对话。
2. 所有 TTS 默认音色统一为 `x4_lingxiaoyao_em`。
3. 医生页语音链以半双工连续为准，不做本批次内全双工电话体验。
4. 若要继续升级数字人医生效果，优先调 prompt 和知识库，不先改 UI 壳层。
