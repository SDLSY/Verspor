# 文本编码清扫基线

更新时间：2026-03-10

## 1. 本轮目标
- 只修复磁盘中的真实乱码，不处理 PowerShell 或终端显示层的假乱码。
- 优先清理：
  - `app/src/main/res/values*`
  - 主页面 `Fragment/XML`
  - 医疗/OCR/讯飞/建议解释链路
  - 活文档 `docs/*.md`

## 2. 已修复的关键文件
- `app/src/main/res/values/strings_ui_refresh.xml`
- `app/src/main/res/values/strings_xfyun.xml`
- `app/src/main/res/values/strings_doctor.xml`
- `app/src/main/java/com/example/newstart/ui/home/MorningReportFragment.kt`
- `app/src/main/java/com/example/newstart/ui/trend/SleepTrendFragment.kt`
- `app/src/main/java/com/example/newstart/ui/profile/ProfileFragment.kt`
- `app/src/main/java/com/example/newstart/bluetooth/BleManager.kt`
- `cloud-next/src/lib/recommendation-model/scientific-model.ts`
- `docs/20_专题系统说明/SCIENTIFIC_RECOMMENDATION_MODEL.md`

## 3. 当前检查结果
- `app/src/main/res`：主资源 XML 已按 UTF-8 重新校验。
- `app/src/main/java`：本轮触达的页面与语音/Avatar 主链无真实乱码。
- `docs`：活文档已统一回到 UTF-8 基线。
- 2026-03-10 本轮追加扫描结果：
  - `app/src/main/res`
  - `app/src/main/java`
  - `docs`
  共计真实乱码命中 `0`；此前多数“乱码”来自 PowerShell 终端显示编码，不是磁盘文件损坏。

## 4. 仍保留但不在本批次处理的内容
- `artifacts/` 下的构建与报告产物
- `captures/` 下的 UI dump / 抓取文件
- `app-shell/build/` 下自动生成的 merged resources / lint 报告

这些文件即使出现乱码，也不属于运行时代码事实源，不纳入本批次清扫范围。

## 5. 维护要求
- 所有中文用户可见文本必须优先落资源文件。
- 代码内中文仅允许出现在：
  - 测试断言
  - fallback 常量
  - 文档性日志
- 新增 Markdown 文档统一使用 UTF-8。
- 若后续再出现乱码，优先检查：
  - 文件保存编码
  - 复制源是否为 GBK/ANSI 文本
  - IDE 自动转码行为

