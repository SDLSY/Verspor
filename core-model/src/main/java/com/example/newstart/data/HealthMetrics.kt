package com.example.newstart.data

/**
 * 健康指标数据
 */
data class HealthMetrics(
    val heartRate: HeartRateData,
    val bloodOxygen: BloodOxygenData,
    val temperature: TemperatureData,
    val hrv: HRVData
)

/**
 * 心率数据
 */
data class HeartRateData(
    val current: Int,               // 当前心率 (bpm)
    val avg: Int,                   // 平均心率
    val min: Int,                   // 最低心率
    val max: Int,                   // 最高心率
    val trend: Trend,               // 趋势
    val isAbnormal: Boolean = false
) {
    fun getChangeFromBaseline(baseline: Int): Int = current - baseline
}

/**
 * 血氧数据
 */
data class BloodOxygenData(
    val current: Int,               // 当前血氧 (%)
    val avg: Int,                   // 平均血氧
    val min: Int,                   // 最低血氧
    val stability: String,          // 稳定性描述
    val isAbnormal: Boolean = false
) {
    fun isLow(): Boolean = current < 90
}

/**
 * 体温数据
 */
data class TemperatureData(
    val current: Float,             // 当前体温 (℃)
    val avg: Float,                 // 平均体温
    val status: String,             // 状态描述
    val isAbnormal: Boolean = false
) {
    fun isInNormalRange(): Boolean = current in 36.0..37.5
}

/**
 * 趋势枚举
 */
enum class Trend {
    UP,
    DOWN,
    STABLE;

    fun getSymbol(): String {
        return when (this) {
            UP -> "↑"
            DOWN -> "↓"
            STABLE -> "→"
        }
    }
}

