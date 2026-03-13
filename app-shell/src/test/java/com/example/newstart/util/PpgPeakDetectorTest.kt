package com.example.newstart.util

import com.example.newstart.data.SensorData
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class PpgPeakDetectorTest {

    @Test
    fun analyze_detects_reasonable_bpm_from_synthetic_ppg() {
        val startTs = 1_700_000_000_000L
        val samplePeriodMs = 100L // 10Hz

        val samples = buildList {
            for (i in 0 until 300) {
                val tMs = i * samplePeriodMs
                val slot = i % 10
                val waveform = when (slot) {
                    4 -> 1500f
                    3, 5 -> 1120f
                    else -> 900f
                }
                add((startTs + tMs) to waveform.toFloat())
            }
        }

        val metrics = PpgPeakDetector.analyze(samples)
        assertNotNull(metrics)
        metrics!!
        assertTrue(metrics.estimatedHeartRateBpm in 60f..90f)
        assertTrue(metrics.rmssdMs >= 0f)
        assertTrue(metrics.signalQuality > 0.2f)
    }

    @Test
    fun estimator_returns_non_zero_when_raw_hrv_missing_but_ppg_present() {
        val estimator = HrvFallbackEstimator()
        val startTs = 1_700_000_000_000L
        val samplePeriodMs = 40L // 25Hz
        val periodMs = 60_000.0 / 70.0

        var resolved = 0
        for (i in 0 until 500) {
            val tMs = i * samplePeriodMs
            val phase = (tMs / periodMs) * 2.0 * PI
            val ppg = (900.0 + 240.0 * sin(phase) + 35.0 * sin(phase * 1.9)).toFloat()
            resolved = estimator.resolve(
                SensorData(
                    timestamp = startTs + tMs,
                    heartRate = 70,
                    bloodOxygen = 0,
                    temperature = 0f,
                    accelerometer = Triple(0f, 0f, 0f),
                    gyroscope = Triple(0f, 0f, 0f),
                    ppgValue = ppg,
                    hrv = 0,
                    steps = 0
                )
            )
        }

        assertTrue(resolved > 0)
    }
}
