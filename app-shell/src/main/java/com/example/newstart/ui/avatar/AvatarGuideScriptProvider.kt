package com.example.newstart.ui.avatar

import com.example.newstart.core.common.R as CommonR

data class AvatarGuideScript(
    val enterMessage: String,
    val tapReplies: List<String>
)

object AvatarGuideScriptProvider {

    private val defaultScript = AvatarGuideScript(
        enterMessage = "我先帮你看这一页的重点，需要时再点我补充下一步。",
        tapReplies = listOf(
            "我可以把这一页先做什么、后做什么，说得更直接一点。",
            "如果云端回复稍慢，我会先用本地提示帮你不断线。"
        )
    )

    fun scriptFor(destinationId: Int): AvatarGuideScript {
        return when (destinationId) {
            CommonR.id.navigation_home -> AvatarGuideScript(
                enterMessage = "先看今日恢复分和风险提示，再决定今天先做哪项干预。",
                tapReplies = listOf(
                    "如果恢复分偏低，先降负荷，再看干预建议会更稳。",
                    "想直接进入主流程，可以从这里转去干预中心。"
                )
            )

            CommonR.id.navigation_doctor -> AvatarGuideScript(
                enterMessage = "先把主诉、持续时间和伴随症状说清楚，再看系统怎么继续追问。",
                tapReplies = listOf(
                    "连续语音适合边说边补充，录音更适合一次性描述清楚。",
                    "如果已经拿到建议，记得把动作放到辅助区，不要挡住主对话。"
                )
            )

            CommonR.id.navigation_trend -> AvatarGuideScript(
                enterMessage = "这里主要看变化，先看周报结论，再看最近的上升或下滑。",
                tapReplies = listOf(
                    "如果趋势连续下滑，优先回干预中心调整动作。",
                    "趋势页看的是变化解释，不需要把每张图都逐一看完。"
                )
            )

            CommonR.id.navigation_device -> AvatarGuideScript(
                enterMessage = "先确认连接、电量和同步状态，异常时再用高级工具排查。",
                tapReplies = listOf(
                    "如果同步异常，先断开重连，再看后台采集状态。",
                    "高级工具更适合排查问题，不是日常主入口。"
                )
            )

            CommonR.id.navigation_profile -> AvatarGuideScript(
                enterMessage = "这里主要处理账户、通知和隐私设置，先确认云端状态。",
                tapReplies = listOf(
                    "如果还没登录，建议先完成登录再同步更多数据。",
                    "需要回主流程时，可以从这里快速返回核心页面。"
                )
            )

            CommonR.id.navigation_intervention_center -> AvatarGuideScript(
                enterMessage = "这里把症状自查、医检解析和执行建议串成一条主线。",
                tapReplies = listOf(
                    "先从最贴近当前问题的入口开始，不需要把每个模块都点一遍。",
                    "这里适合决定今天做什么，不替代医生问诊。"
                )
            )

            CommonR.id.navigation_relax_hub -> AvatarGuideScript(
                enterMessage = "这里是症状自查，先点部位或选症状，再生成辅助判断。",
                tapReplies = listOf(
                    "如果不确定从哪开始，先选最明显不适的部位或症状。",
                    "先完成定位和补充信息，再看风险分级会更准。"
                )
            )

            CommonR.id.navigation_relax_center_legacy -> AvatarGuideScript(
                enterMessage = "这里是康复辅助场景，先选身体区域，再看对应动作和建议。",
                tapReplies = listOf(
                    "如果不确定从哪开始，先选最明显不适的部位。",
                    "动作做完记得反馈体感，这会影响后续建议。"
                )
            )

            CommonR.id.navigation_breathing_coach -> AvatarGuideScript(
                enterMessage = "先选训练节律，再跟着一轮呼吸，不必一开始就做满时长。",
                tapReplies = listOf(
                    "时间紧张时先做短版，比临时放弃更有效。",
                    "训练结束后记一下体感，后续建议会更准。"
                )
            )

            CommonR.id.navigation_medical_report_analyze -> AvatarGuideScript(
                enterMessage = "先确认识别结果，再看可读摘要和异常指标，别只盯原始 OCR。",
                tapReplies = listOf(
                    "如果 OCR 不完整，可以先展开编辑区手动修正。",
                    "可读报告是辅助理解，最终还是以原报告和医生解释为准。"
                )
            )

            CommonR.id.navigation_relax_review -> AvatarGuideScript(
                enterMessage = "这里看执行后的完成率、体感和效果变化，帮助你决定下一步。",
                tapReplies = listOf(
                    "如果效果一般，优先回到干预中心换一个更轻量的动作。",
                    "复盘不是打分，而是帮助你第二天做更稳的选择。"
                )
            )

            CommonR.id.navigation_assessment_baseline -> AvatarGuideScript(
                enterMessage = "先完成基线评估，再生成更适合你的干预建议。",
                tapReplies = listOf(
                    "基线越完整，后续推荐和问诊就越稳。",
                    "如果时间有限，先完成关键量表，再补充次要项。"
                )
            )

            CommonR.id.navigation_intervention_profile -> AvatarGuideScript(
                enterMessage = "先看清方案目标和结构，再决定是否开始执行。",
                tapReplies = listOf(
                    "先理解这轮方案针对什么，再开始执行会更稳。",
                    "如果目标和体感不匹配，可以先回上一层重新选择。"
                )
            )

            CommonR.id.navigation_intervention_session -> AvatarGuideScript(
                enterMessage = "先跟随当前步骤执行，再反馈体感变化和完成情况。",
                tapReplies = listOf(
                    "执行时先按当前节奏完成一轮，再判断要不要调整。",
                    "如果出现明显不适，先停止并回到上一页查看建议。"
                )
            )

            else -> defaultScript
        }
    }
}
