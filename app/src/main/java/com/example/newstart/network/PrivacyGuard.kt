package com.example.newstart.network

import com.example.newstart.data.HealthMetrics
import com.example.newstart.data.SleepData
import kotlin.math.roundToInt

object PrivacyGuard {

    private const val USE_EDGE_ADVICE_FIRST = true

    fun shouldUseEdgeAdviceFirst(): Boolean = USE_EDGE_ADVICE_FIRST

    fun minimizedAnalysisRawData(
        metrics: HealthMetrics,
        sleep: SleepData,
    ): Map<String, Any> {
        val hrBucket = bucketInt(metrics.heartRate.current, 5)
        val spo2Bucket = bucketInt(metrics.bloodOxygen.current, 1)
        val tempBucket = bucketFloat(metrics.temperature.current, 0.2f)
        val hrvBucket = bucketInt(metrics.hrv.current, 5)
        val totalSleepBucket = bucketInt(sleep.totalSleepMinutes, 15)
        val deepSleepBucket = bucketInt(sleep.deepSleepMinutes, 15)

        return mapOf(
            "heartRateBin" to hrBucket,
            "bloodOxygenBin" to spo2Bucket,
            "temperatureBin" to tempBucket,
            "hrvBin" to hrvBucket,
            "totalSleepMinutesBin" to totalSleepBucket,
            "deepSleepMinutesBin" to deepSleepBucket,
        )
    }

    fun minimizedAdviceData(sleep: SleepData): Map<String, Any> {
        return mapOf(
            "sleepEfficiencyBin" to bucketFloat(sleep.sleepEfficiency, 5f),
            "totalSleepMinutesBin" to bucketInt(sleep.totalSleepMinutes, 15),
            "awakeCount" to sleep.awakeCount.coerceIn(0, 12),
        )
    }

    private fun bucketInt(value: Int, step: Int): Int {
        if (step <= 0) return value
        return ((value.toFloat() / step).roundToInt() * step)
    }

    private fun bucketFloat(value: Float, step: Float): Float {
        if (step <= 0f) return value
        val bucketed = ((value / step).roundToInt() * step)
        return (bucketed * 10f).roundToInt() / 10f
    }
}
