package com.example.newstart.network

import com.example.newstart.data.BloodOxygenData
import com.example.newstart.data.HeartRateData
import com.example.newstart.data.HRVData
import com.example.newstart.data.HealthMetrics
import com.example.newstart.data.SleepData
import com.example.newstart.data.StressLevel
import com.example.newstart.data.TemperatureData
import com.example.newstart.data.Trend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class PrivacyGuardTest {

    @Test
    fun minimizedAnalysisRawData_returns_bucketed_values_for_normal_input() {
        val result = PrivacyGuard.minimizedAnalysisRawData(
            metrics = metrics(heartRate = 63, spo2 = 97, temperature = 36.36f, hrv = 58),
            sleep = sleep(totalSleepMinutes = 437, deepSleepMinutes = 102)
        )

        assertEquals(65, result["heartRateBin"])
        assertEquals(97, result["bloodOxygenBin"])
        assertEquals(36.4f, result["temperatureBin"])
        assertEquals(60, result["hrvBin"])
        assertEquals(435, result["totalSleepMinutesBin"])
        assertEquals(105, result["deepSleepMinutesBin"])
    }

    @Test
    fun minimizedAdviceData_buckets_efficiency_and_clamps_awake_count() {
        val result = PrivacyGuard.minimizedAdviceData(
            sleep = sleep(
                totalSleepMinutes = 421,
                deepSleepMinutes = 90,
                sleepEfficiency = 92.4f,
                awakeCount = 19
            )
        )

        assertEquals(90.0f, result["sleepEfficiencyBin"])
        assertEquals(420, result["totalSleepMinutesBin"])
        assertEquals(12, result["awakeCount"])
    }

    @Test
    fun shouldUseEdgeAdviceFirst_is_enabled() {
        assertTrue(PrivacyGuard.shouldUseEdgeAdviceFirst())
    }

    private fun metrics(
        heartRate: Int,
        spo2: Int,
        temperature: Float,
        hrv: Int
    ): HealthMetrics {
        return HealthMetrics(
            heartRate = HeartRateData(heartRate, heartRate, heartRate - 2, heartRate + 3, Trend.STABLE),
            bloodOxygen = BloodOxygenData(spo2, spo2, spo2 - 1, "stable"),
            temperature = TemperatureData(temperature, temperature, "normal"),
            hrv = HRVData(hrv, 60, 85f, Trend.STABLE, StressLevel.MODERATE)
        )
    }

    private fun sleep(
        totalSleepMinutes: Int,
        deepSleepMinutes: Int,
        sleepEfficiency: Float = 88.0f,
        awakeCount: Int = 2
    ): SleepData {
        val now = Date(0L)
        return SleepData(
            id = "sleep-1",
            date = now,
            bedTime = now,
            wakeTime = Date(totalSleepMinutes * 60L * 1000L),
            totalSleepMinutes = totalSleepMinutes,
            deepSleepMinutes = deepSleepMinutes,
            lightSleepMinutes = 180,
            remSleepMinutes = 90,
            awakeMinutes = 15,
            sleepEfficiency = sleepEfficiency,
            fallAsleepMinutes = 10,
            awakeCount = awakeCount
        )
    }
}
