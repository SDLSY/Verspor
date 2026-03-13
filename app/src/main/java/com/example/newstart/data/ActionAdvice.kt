package com.example.newstart.data

/**
 * 行动建议模型。
 */
data class ActionAdvice(
    val type: AdviceType,
    val title: String,
    val description: String,
    val actionable: Boolean = true
)

/**
 * 建议类型。
 */
enum class AdviceType {
    EXERCISE,
    SLEEP,
    STRESS,
    FOCUS,
    NUTRITION;

    fun getIcon(): String {
        return when (this) {
            EXERCISE -> "运动"
            SLEEP -> "睡眠"
            STRESS -> "减压"
            FOCUS -> "专注"
            NUTRITION -> "营养"
        }
    }
}

/**
 * 建议生成器。
 */
object AdviceGenerator {

    fun generateAdvice(recoveryScore: RecoveryScore, sleepData: SleepData): List<ActionAdvice> {
        val adviceList = mutableListOf<ActionAdvice>()

        adviceList.add(generateExerciseAdvice(recoveryScore))

        if (recoveryScore.score < 60 || sleepData.totalSleepMinutes < 420) {
            adviceList.add(generateSleepAdvice(sleepData))
        }

        if (recoveryScore.hrvRecoveryScore < 0) {
            adviceList.add(generateStressAdvice())
        }

        if (recoveryScore.score >= 70) {
            adviceList.add(generateFocusAdvice())
        }

        return adviceList
    }

    private fun generateExerciseAdvice(recoveryScore: RecoveryScore): ActionAdvice {
        return when (recoveryScore.level) {
            RecoveryLevel.EXCELLENT -> ActionAdvice(
                type = AdviceType.EXERCISE,
                title = "高强度训练",
                description = "今天状态优秀，可进行 HIIT 或力量训练（强度 8-9/10）。"
            )
            RecoveryLevel.GOOD -> ActionAdvice(
                type = AdviceType.EXERCISE,
                title = "中等强度运动",
                description = "适合有氧运动，如慢跑 30-45 分钟（强度 6-7/10）。"
            )
            RecoveryLevel.FAIR -> ActionAdvice(
                type = AdviceType.EXERCISE,
                title = "轻度运动",
                description = "建议轻度活动，如散步或瑜伽（强度 4-5/10）。"
            )
            RecoveryLevel.POOR -> ActionAdvice(
                type = AdviceType.EXERCISE,
                title = "休息优先",
                description = "今天避免剧烈运动，可做轻度拉伸。"
            )
        }
    }

    private fun generateSleepAdvice(sleepData: SleepData): ActionAdvice {
        val sleepDebt = (480 - sleepData.totalSleepMinutes).coerceAtLeast(0)
        val advanceMinutes = (sleepDebt * 0.5).toInt().coerceIn(30, 90)

        return ActionAdvice(
            type = AdviceType.SLEEP,
            title = "作息调整",
            description = "建议今晚提前 ${advanceMinutes} 分钟入睡，补足睡眠债务。"
        )
    }

    private fun generateStressAdvice(): ActionAdvice {
        return ActionAdvice(
            type = AdviceType.STRESS,
            title = "压力管理",
            description = "检测到压力水平偏高，建议进行 5 分钟呼吸放松或 10 分钟冥想。"
        )
    }

    private fun generateFocusAdvice(): ActionAdvice {
        return ActionAdvice(
            type = AdviceType.FOCUS,
            title = "最佳专注时段",
            description = "推荐时段：9:00-11:30，15:00-17:30。"
        )
    }
}
