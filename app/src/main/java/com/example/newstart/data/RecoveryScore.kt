package com.example.newstart.data

/**
 * 恢复指数模型
 */
data class RecoveryScore(
    val score: Int,                    // 总分 (0-100)
    val sleepEfficiencyScore: Float,   // 睡眠效率得分
    val hrvRecoveryScore: Float,       // HRV 恢复得分
    val deepSleepScore: Float,         // 深睡得分
    val temperatureRhythmScore: Float, // 体温节律得分
    val oxygenStabilityScore: Float,   // 血氧稳定性得分
    val level: RecoveryLevel           // 恢复等级
) {
    companion object {
        private const val WEIGHT_SLEEP_EFFICIENCY = 0.35f
        private const val WEIGHT_HRV_RECOVERY = 0.25f
        private const val WEIGHT_DEEP_SLEEP = 0.20f
        private const val WEIGHT_TEMPERATURE = 0.15f
        private const val WEIGHT_OXYGEN = 0.05f

        fun calculate(
            sleepEfficiency: Float,
            hrvRecoveryRate: Float,
            deepSleepPercentage: Float,
            temperatureRhythm: Float,
            oxygenStability: Float
        ): RecoveryScore {
            val score = (
                sleepEfficiency * WEIGHT_SLEEP_EFFICIENCY +
                    hrvRecoveryRate * WEIGHT_HRV_RECOVERY +
                    deepSleepPercentage * WEIGHT_DEEP_SLEEP +
                    temperatureRhythm * WEIGHT_TEMPERATURE +
                    oxygenStability * WEIGHT_OXYGEN
                ).toInt().coerceIn(0, 100)

            val level = when {
                score >= 80 -> RecoveryLevel.EXCELLENT
                score >= 60 -> RecoveryLevel.GOOD
                score >= 40 -> RecoveryLevel.FAIR
                else -> RecoveryLevel.POOR
            }

            return RecoveryScore(
                score = score,
                sleepEfficiencyScore = sleepEfficiency,
                hrvRecoveryScore = hrvRecoveryRate,
                deepSleepScore = deepSleepPercentage,
                temperatureRhythmScore = temperatureRhythm,
                oxygenStabilityScore = oxygenStability,
                level = level
            )
        }
    }

    fun getStatusDescription(): String = level.getDescription()

    fun getStatusEmoji(): String = level.getEmoji()

    fun getStatusColor(): Int = level.getColor()
}

/**
 * 恢复等级枚举
 */
enum class RecoveryLevel {
    EXCELLENT, // 80-100
    GOOD,      // 60-79
    FAIR,      // 40-59
    POOR;      // 0-39

    fun getDescription(): String {
        return when (this) {
            EXCELLENT -> "状态极佳，适合挑战任务"
            GOOD -> "状态良好，保持节奏"
            FAIR -> "需要关注，今天悠着点"
            POOR -> "恢复不足，优先睡眠"
        }
    }

    fun getEmoji(): String {
        return when (this) {
            EXCELLENT -> "优秀"
            GOOD -> "良好"
            FAIR -> "关注"
            POOR -> "恢复中"
        }
    }

    fun getColor(): Int {
        return when (this) {
            EXCELLENT -> 0xFFFF9800.toInt()
            GOOD -> 0xFF4CAF50.toInt()
            FAIR -> 0xFFFFC107.toInt()
            POOR -> 0xFFF44336.toInt()
        }
    }
}

