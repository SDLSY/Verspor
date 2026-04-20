# 全栈技术重构执行状态

更新时间：2026-03-10

## 本批次完成

### 1. Android UI 系统收口
- 主次页面统一到同一套设计 token：标题、正文、状态标签、卡片层级、按钮语义。
- 医生页首屏重新收口到“对话优先”，顶部状态区和底部输入区进一步压缩。
- 医生页辅助区已移动到输入区下方，聊天列表不再被推荐卡和多模态区提前挤压。
- 今日页、趋势页、设备页、我的页已统一到同一视觉骨架和资源文案体系。
- 放松中心、症状引导、医检分析等次级页保留原功能，但样式和层级已开始统一。

### 2. 桌面机器人行为重构
- 桌面机器人取消麦克风短对话入口。
- 现改为“页面说明 + TTS 播报 + 动作映射”。
- 说明文案优先由 Spark 生成，失败时回退本地脚本。
- 点击 Avatar 时只做重播/重生成，不再申请录音权限。

### 3. 医生页语音收口
- 通话模式固定为半双工连续状态机。
- 为避免 AI 播报被重新识别为用户输入：
  - 播报期间不启用 IAT
  - 播报结束后进入冷却窗口再恢复监听
  - 进入通话前会先停止现有 Avatar/TTS 播报
- 默认 TTS 音色统一切到 `x4_lingxiaoyao_em`。

### 4. 项目级乱码清扫
- 已修复主资源文件、主页面 Fragment/XML、讯飞链路和建议解释链路中的真实乱码。
- 当前运行时代码触达路径已回到 UTF-8 基线。
- 对 `app/src/main/res`、`app/src/main/java`、`docs` 的追加磁盘扫描命中数为 `0`。
- 仍保留的乱码主要在自动生成的 `artifacts/`、`captures/`、`build/` 目录，不属于运行时代码。

### 5. 建议模型文档与配置化方案
- 已重写 `SCIENTIFIC_RECOMMENDATION_MODEL.md`，恢复为可读 UTF-8 文档。
- 已新增：
  - `SCIENTIFIC_RECOMMENDATION_MODEL_RESEARCH.md`
  - `SRM_V2_CONFIGURATION_PLAN.md`
- 当前建议模型仍为 `SRM_V1`，本批次只完成研究和配置化设计，不切线上主链。

## 当前门禁验证
- Android：
  - `gradlew.bat :app-shell:assembleDebug`
  - `gradlew.bat :app-shell:testDebugUnitTest`
  - `gradlew.bat :app-shell:lint`
  - `gradlew.bat :app-shell:connectedDebugAndroidTest` 本轮执行时 `adb devices` 为空，未形成有效验证
- Cloud：
  - `npm run lint`
  - `npm run build`
- 数据库：
  - `recommendation_traces` 已在远端 Supabase 建表并完成一次真实写入回查

## 当前边界
- 数字人医生本批次不再验收，以本地已通过为准。
- 桌面机器人不再承担自由语音对话，而是承担页面说明播报。
- SRM_V2 仅有研究和配置表设计，尚未接入线上运行时。
