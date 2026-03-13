package com.example.newstart.util

import kotlin.math.abs
import kotlin.math.sqrt

data class PpgPeakMetrics(
    val peakTimestamps: List<Long>,
    val rrIntervalsMs: List<Float>,
    val estimatedHeartRateBpm: Float,
    val rmssdMs: Float,
    val signalQuality: Float
)

object PpgPeakDetector {
    private const val MIN_INTERVAL_MS = 240f
    private const val MAX_INTERVAL_MS = 1800f

    fun analyze(
        samples: List<Pair<Long, Float>>,
        minPeakDistanceMs: Long = 280L
    ): PpgPeakMetrics? {
        if (samples.size < 20) return null

        val sorted = samples.sortedBy { it.first }
        val timestamps = sorted.map { it.first }
        val values = sorted.map { it.second }

        val mean = values.average().toFloat()
        val std = stdDev(values, mean)
        if (std < 1e-3f) return null

        val threshold = mean + std * 0.10f
        val prominenceMin = (std * 0.03f).coerceAtLeast(4f)

        val peakIndexes = mutableListOf<Int>()
        var lastPeakTs: Long? = null

        for (i in 1 until values.lastIndex) {
            val current = values[i]
            if (current <= threshold) continue

            val prev = values[i - 1]
            val next = values[i + 1]
            if (!(current > prev && current >= next)) continue

            val localBase = (prev + next) * 0.5f
            if (current - localBase < prominenceMin) continue

            val ts = timestamps[i]
            if (lastPeakTs != null && ts - lastPeakTs < minPeakDistanceMs) {
                if (peakIndexes.isNotEmpty() && current > values[peakIndexes.last()]) {
                    peakIndexes[peakIndexes.lastIndex] = i
                    lastPeakTs = ts
                }
                continue
            }

            peakIndexes.add(i)
            lastPeakTs = ts
        }

        if (peakIndexes.size < 3) return null

        val peakTimestamps = peakIndexes.map { timestamps[it] }
        val rrIntervalsRaw = peakTimestamps
            .zipWithNext { a, b -> (b - a).toFloat() }
        val rrIntervals = rrIntervalsRaw.filter { it in MIN_INTERVAL_MS..MAX_INTERVAL_MS }
            .ifEmpty { rrIntervalsRaw }

        if (rrIntervals.size < 2) return null

        val meanRr = rrIntervals.average().toFloat()
        val estimatedHeartRate = if (meanRr > 0f) 60_000f / meanRr else 0f
        if (estimatedHeartRate <= 0f) return null

        val rmssd = rmssd(rrIntervals)
        val quality = signalQuality(values, std, rrIntervals, estimatedHeartRate)

        return PpgPeakMetrics(
            peakTimestamps = peakTimestamps,
            rrIntervalsMs = rrIntervals,
            estimatedHeartRateBpm = estimatedHeartRate,
            rmssdMs = rmssd,
            signalQuality = quality
        )
    }

    private fun signalQuality(
        values: List<Float>,
        std: Float,
        rrIntervals: List<Float>,
        estimatedHeartRate: Float
    ): Float {
        val meanAbs = values.map { abs(it) }.average().toFloat().coerceAtLeast(1e-3f)
        val cv = (std / meanAbs).coerceIn(0f, 1f)
        val amplitudeScore = (cv / 0.22f).coerceIn(0f, 1f)

        val rrMean = rrIntervals.average().toFloat().coerceAtLeast(1e-3f)
        val rrStd = stdDev(rrIntervals, rrMean)
        val rrCv = (rrStd / rrMean).coerceIn(0f, 1f)
        val rhythmScore = (1f - rrCv / 0.30f).coerceIn(0f, 1f)

        val hrScore = when {
            estimatedHeartRate in 50f..110f -> 1f
            estimatedHeartRate in 40f..130f -> 0.7f
            else -> 0.35f
        }

        return (amplitudeScore * 0.35f + rhythmScore * 0.45f + hrScore * 0.20f).coerceIn(0f, 1f)
    }

    private fun rmssd(rrIntervals: List<Float>): Float {
        if (rrIntervals.size < 2) return 0f
        val diffSquares = rrIntervals.zipWithNext { a, b ->
            val diff = b - a
            diff * diff
        }
        return sqrt(diffSquares.average().toFloat())
    }

    private fun stdDev(values: List<Float>, mean: Float): Float {
        if (values.isEmpty()) return 0f
        val variance = values
            .map { v -> (v - mean) * (v - mean) }
            .average()
            .toFloat()
        return sqrt(variance)
    }
}
