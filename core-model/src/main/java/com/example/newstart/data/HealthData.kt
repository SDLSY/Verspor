package com.example.newstart.data

/**
 * HRV（心率变异性）数据模型
 */
data class HRVData(
    val current: Int,               // 当前 HRV (ms)
    val baseline: Int,              // 基线水平 (ms)
    val recoveryRate: Float,        // 恢复率 (0-100%)
    val trend: Trend,               // 趋势
    val stressLevel: StressLevel    // 压力等级
)

/**
 * 压力等级枚举
 */
enum class StressLevel {
    LOW,        // 放松 (HRV >= 90% baseline)
    MODERATE,   // 正常 (HRV 70-90% baseline)
    HIGH,       // 偏高 (HRV 50-70% baseline)
    VERY_HIGH;  // 很高 (HRV < 50% baseline)
    
    fun getDisplayName(): String {
        return when (this) {
            LOW -> "放松"
            MODERATE -> "正常"
            HIGH -> "偏高"
            VERY_HIGH -> "很高"
        }
    }
    
    fun getEmoji(): String {
        return when (this) {
            LOW -> "😌"
            MODERATE -> "😊"
            HIGH -> "😰"
            VERY_HIGH -> "😫"
        }
    }
}

/**
 * 运动/活动数据模型
 */
data class ActivityData(
    val activityLevel: ActivityLevel,   // 当前活动强度
    val totalMinutes: Int,              // 总活动时长(分钟)
    val lightMinutes: Int,              // 轻度活动
    val moderateMinutes: Int,           // 中度活动
    val vigorousMinutes: Int,           // 剧烈活动
    val caloriesBurned: Int             // 消耗卡路里
)

/**
 * 活动强度枚举
 */
enum class ActivityLevel {
    STILL,      // 静止 (magnitude < 0.1g)
    LIGHT,      // 轻度 (0.1-0.5g)
    MODERATE,   // 中度 (0.5-1.5g)
    VIGOROUS;   // 剧烈 (> 1.5g)
    
    fun getDisplayName(): String {
        return when (this) {
            STILL -> "静止"
            LIGHT -> "轻度活动"
            MODERATE -> "中度活动"
            VIGOROUS -> "剧烈活动"
        }
    }
    
    fun getIcon(): String {
        return when (this) {
            STILL -> "🛌"
            LIGHT -> "🚶"
            MODERATE -> "🏃"
            VIGOROUS -> "🏃‍♂️💨"
        }
    }
}

/**
 * 步数统计数据
 */
data class StepsData(
    val currentSteps: Int,          // 当前步数
    val targetSteps: Int,           // 目标步数
    val weeklyAverage: Int,         // 本周平均
    val streakDays: Int,            // 连续达标天数
    val caloriesBurned: Int         // 消耗卡路里 (估算)
) {
    fun getProgress(): Float {
        return (currentSteps.toFloat() / targetSteps * 100).coerceIn(0f, 100f)
    }
    
    fun isTargetReached(): Boolean {
        return currentSteps >= targetSteps
    }
}
