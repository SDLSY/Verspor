package com.example.newstart.data

import java.util.Date

/**
 * 睡眠数据模型
 */
data class SleepData(
    val id: String,
    val date: Date,
    val bedTime: Date,              // 入睡时间
    val wakeTime: Date,             // 起床时间
    val totalSleepMinutes: Int,     // 总睡眠时长（分钟）
    val deepSleepMinutes: Int,      // 深睡时长
    val lightSleepMinutes: Int,     // 浅睡时长
    val remSleepMinutes: Int,       // REM 时长
    val awakeMinutes: Int,          // 清醒时长
    val sleepEfficiency: Float,     // 睡眠效率 (0-100)
    val fallAsleepMinutes: Int,     // 入睡耗时
    val awakeCount: Int,            // 夜间清醒次数
    val sleepStages: List<SleepStageSegment> = emptyList() // 分期详情
) {
    fun getTotalInBedMinutes(): Int {
        return ((wakeTime.time - bedTime.time) / (1000 * 60)).toInt()
    }

    fun getDeepSleepPercentage(): Float {
        return if (totalSleepMinutes > 0) {
            deepSleepMinutes.toFloat() / totalSleepMinutes * 100f
        } else {
            0f
        }
    }

    fun getRemSleepPercentage(): Float {
        return if (totalSleepMinutes > 0) {
            remSleepMinutes.toFloat() / totalSleepMinutes * 100f
        } else {
            0f
        }
    }

    fun getFormattedDuration(): String {
        val hours = totalSleepMinutes / 60
        val minutes = totalSleepMinutes % 60
        return "${hours}小时${minutes}分钟"
    }
}

/**
 * 睡眠分期片段
 */
data class SleepStageSegment(
    val stage: SleepStage,
    val startTime: Date,
    val endTime: Date,
    val durationMinutes: Int
)

