package com.example.newstart.util

import com.example.newstart.data.SensorData
import java.util.ArrayDeque
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Fallback HRV estimator used when the device does not provide valid raw HRV.
 * It fuses heart-rate-series statistics and PPG peak metrics with smoothing.
 */
class HrvFallbackEstimator(
    private val windowMs: Long = 120_000L,
    private val minHrSamples: Int = 4
) {

    companion object {
        private const val MIN_HRV_MS = 18
        private const val MAX_HRV_MS = 180
    }

    private data class TimedValue(val timestamp: Long, val value: Float)

    private val hrSamples = ArrayDeque<TimedValue>()
    private val ppgSamples = ArrayDeque<TimedValue>()

    private var lastResolvedHrv: Int = 0
    private var lastPpgPeakMetrics: PpgPeakMetrics? = null

    fun resolve(sample: SensorData): Int {
        val now = if (sample.timestamp > 0L) sample.timestamp else System.currentTimeMillis()

        if (sample.heartRate > 0) {
            hrSamples.addLast(TimedValue(now, sample.heartRate.toFloat()))
        }
        if (sample.ppgValue > 0f) {
            ppgSamples.addLast(TimedValue(now, sample.ppgValue))
        }

        trimExpired(hrSamples, now)
        trimExpired(ppgSamples, now)

        if (sample.hrv > 0) {
            lastResolvedHrv = sample.hrv.coerceIn(MIN_HRV_MS, MAX_HRV_MS)
            return lastResolvedHrv
        }

        val hrSeriesEstimate = estimateFromHeartRateSeries(sample.heartRate)
        val ppgPeakEstimate = estimateFromPpgPeaks()
        val singleHrEstimate = if (sample.heartRate > 0) estimateFromSingleHeartRate(sample.heartRate) else null
        val ppgCompensation = estimatePpgCompensation()

        val rawEstimate = when {
            hrSeriesEstimate != null && ppgPeakEstimate != null ->
                hrSeriesEstimate * 0.55f + ppgPeakEstimate * 0.45f + ppgCompensation * 0.35f
            hrSeriesEstimate != null -> hrSeriesEstimate + ppgCompensation
            ppgPeakEstimate != null -> ppgPeakEstimate + ppgCompensation * 0.2f
            singleHrEstimate != null -> singleHrEstimate + ppgCompensation * 0.5f
            lastResolvedHrv > 0 -> lastResolvedHrv.toFloat()
            else -> 0f
        }

        if (rawEstimate <= 0f) return 0

        val clamped = rawEstimate.roundToInt().coerceIn(MIN_HRV_MS, MAX_HRV_MS)
        val smoothed = if (lastResolvedHrv > 0) {
            (lastResolvedHrv * 0.65f + clamped * 0.35f).roundToInt()
        } else {
            clamped
        }

        lastResolvedHrv = smoothed.coerceIn(MIN_HRV_MS, MAX_HRV_MS)
        return lastResolvedHrv
    }

    private fun estimateFromHeartRateSeries(currentHeartRate: Int): Float? {
        if (hrSamples.size < minHrSamples) return null

        val rrList = hrSamples.map { timed ->
            60_000f / timed.value.coerceAtLeast(30f)
        }
        if (rrList.size < minHrSamples) return null

        val mean = rrList.average().toFloat()
        val sdnn = stdDev(rrList, mean)
        val diffSquares = rrList.zipWithNext { a, b -> (b - a) * (b - a) }
        val rmssd = if (diffSquares.isNotEmpty()) {
            sqrt(diffSquares.average().toFloat())
        } else {
            sdnn
        }

        var estimate = sdnn * 0.6f + rmssd * 0.4f

        if (currentHeartRate > 0) {
            estimate += when {
                currentHeartRate >= 95 -> -10f
                currentHeartRate >= 80 -> -5f
                currentHeartRate <= 55 -> 5f
                else -> 0f
            }
        }

        return estimate.coerceAtLeast(0f)
    }

    private fun estimateFromPpgPeaks(): Float? {
        if (ppgSamples.size < 20) return null

        val metrics = PpgPeakDetector.analyze(ppgSamples.map { it.timestamp to it.value }) ?: return null
        lastPpgPeakMetrics = metrics

        if (metrics.signalQuality < 0.25f) return null

        val ppgRmssd = metrics.rmssdMs.coerceIn(MIN_HRV_MS.toFloat(), MAX_HRV_MS.toFloat())
        val hrAdjustment = when {
            metrics.estimatedHeartRateBpm >= 95f -> -8f
            metrics.estimatedHeartRateBpm >= 80f -> -4f
            metrics.estimatedHeartRateBpm <= 55f -> 4f
            else -> 0f
        }
        val qualityBoost = metrics.signalQuality * 8f
        return (ppgRmssd + hrAdjustment + qualityBoost).coerceAtLeast(0f)
    }

    private fun estimateFromSingleHeartRate(heartRate: Int): Float {
        val baseline = 120f - heartRate * 0.9f
        return baseline.coerceIn(20f, 80f)
    }

    private fun estimatePpgCompensation(): Float {
        if (ppgSamples.size < 8) return 0f

        val values = ppgSamples.map { it.value }
        val meanAbs = values.map { abs(it) }.average().toFloat()
        if (meanAbs <= 1e-3f) return 0f

        val mean = values.average().toFloat()
        val std = stdDev(values, mean)
        val cv = (std / meanAbs).coerceIn(0f, 1f)

        return (cv * 12f).coerceIn(0f, 12f)
    }

    private fun stdDev(values: List<Float>, mean: Float): Float {
        if (values.isEmpty()) return 0f
        val variance = values
            .map { v -> (v - mean) * (v - mean) }
            .average()
            .toFloat()
        return sqrt(variance)
    }

    private fun trimExpired(queue: ArrayDeque<TimedValue>, now: Long) {
        while (queue.isNotEmpty() && now - queue.first().timestamp > windowMs) {
            queue.removeFirst()
        }
    }

    fun getLastPpgPeakMetrics(): PpgPeakMetrics? = lastPpgPeakMetrics
}

