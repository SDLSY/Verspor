package com.example.newstart.ui.avatar

import com.example.newstart.xfyun.spark.SparkChatMessage

object DesktopAvatarPromptBuilder {

    fun buildMessages(context: PageNarrationContext): List<SparkChatMessage> {
        val highlights = context.visibleHighlights
            .filter { it.isNotBlank() }
            .distinct()
            .take(6)
            .joinToString(separator = "\n- ", prefix = "- ")
            .ifBlank { "- 暂无额外页面要点" }

        val userState = context.userStateSummary.ifBlank { "暂无额外用户状态" }
        val riskSummary = context.riskSummary.ifBlank { "暂无明确风险提示" }
        val actionHint = context.actionHint.ifBlank { "先解释当前页面，再给出一个自然的下一步动作。" }

        return listOf(
            SparkChatMessage(
                role = "system",
                content = buildString {
                    append("你是新启健康 App 的桌面 3D 导航助手。")
                    append("你的任务是根据当前页面内容，用简体中文说一段短提示，帮助用户理解页面重点和下一步。")
                    append("不要做诊断，不替代医生，不输出夸张结论。")
                    append("输出必须适合语音播报，控制在 2 到 3 句，总长度不超过 110 个汉字。")
                    append("如果页面有风险或异常提示，要先说风险，再说建议动作。")
                    append("不要输出 Markdown、序号、标题或多段长文。")
                }
            ),
            SparkChatMessage(
                role = "user",
                content = buildString {
                    appendLine("请根据下面上下文，生成桌面机器人播报文案。")
                    appendLine("页面键：${context.pageKey}")
                    appendLine("页面标题：${context.pageTitle}")
                    appendLine("页面副标题：${context.pageSubtitle.ifBlank { "无" }}")
                    appendLine("触发方式：${context.trigger}")
                    appendLine("用户状态：$userState")
                    appendLine("风险摘要：$riskSummary")
                    appendLine("建议动作：$actionHint")
                    appendLine("当前可见要点：")
                    appendLine(highlights)
                    append("请直接输出最终播报文案。")
                }
            )
        )
    }
}
