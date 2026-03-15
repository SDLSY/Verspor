package com.example.newstart.ui.relax

import com.example.newstart.database.entity.HealthMetricsEntity
import com.example.newstart.intervention.RelaxRealtimeFeedback
import com.example.newstart.intervention.RelaxSignalState
import kotlin.math.roundToInt

class RelaxSignalTracker(
    private val smoothingWindowMs: Long = 5_000L,
    private val staleThresholdMs: Long = 15_000L
) {

    private data class Sample(
        val timestamp: Long,
        val heartRate: Int,
        val hrv: Int,
        val motion: Float
    )

    private val samples = ArrayDeque<Sample>()
    private var lastFeedback = RelaxRealtimeFeedback()

    fun onSample(metrics: HealthMetricsEntity?, now: Long = System.currentTimeMillis()): RelaxRealtimeFeedback {
        if (metrics == null) {
            lastFeedback = lastFeedback.toFallback(now)
            return lastFeedback
        }

        val sample = Sample(
            timestamp = metrics.timestamp,
            heartRate = metrics.heartRateSample.takeIf { it > 0 } ?: metrics.heartRateAvg,
            hrv = metrics.hrvCurrent.coerceAtLeast(0),
            motion = metrics.accMagnitudeSample.coerceAtLeast(0f)
        )
        samples += sample
        trimWindow(now)
        lastFeedback = buildFeedback(now)
        return lastFeedback
    }

    fun currentFeedback(now: Long = System.currentTimeMillis()): RelaxRealtimeFeedback {
        trimWindow(now)
        lastFeedback = buildFeedback(now)
        return lastFeedback
    }

    fun reset() {
        samples.clear()
        lastFeedback = RelaxRealtimeFeedback()
    }

    fun averageRelaxSignal(): Float? {
        val liveSamples = samples.takeIf { it.isNotEmpty() } ?: return null
        return liveSamples.map { sample -> scoreSample(sample) }.average().toFloat()
    }

    fun averageHrv(): Int? {
        val liveSamples = samples.takeIf { it.isNotEmpty() } ?: return null
        return liveSamples.map { it.hrv }.average().roundToInt()
    }

    fun peakHeartRate(): Int? = samples.maxOfOrNull { it.heartRate }

    private fun trimWindow(now: Long) {
        while (samples.isNotEmpty() && now - samples.first().timestamp > smoothingWindowMs) {
            samples.removeFirst()
        }
    }

    private fun buildFeedback(now: Long): RelaxRealtimeFeedback {
        if (samples.isEmpty()) {
            return lastFeedback.toFallback(now)
        }
        val latest = samples.last()
        val live = now - latest.timestamp <= staleThresholdMs
        if (!live) {
            return lastFeedback.toFallback(now)
        }

        val avgHeartRate = samples.map { it.heartRate }.average().toFloat()
        val avgHrv = samples.map { it.hrv }.average().toFloat()
        val avgMotion = samples.map { it.motion }.average().toFloat()
        val relaxSignal = scoreSample(
            Sample(
                timestamp = latest.timestamp,
                heartRate = avgHeartRate.roundToInt(),
                hrv = avgHrv.roundToInt(),
                motion = avgMotion
            )
        )
        val signalState = when {
            relaxSignal >= 0.72f -> RelaxSignalState.CALM
            relaxSignal >= 0.48f -> RelaxSignalState.STEADY
            else -> RelaxSignalState.ACTIVE
        }
        return RelaxRealtimeFeedback(
            signalState = signalState,
            relaxSignal = relaxSignal,
            heartRate = avgHeartRate.roundToInt(),
            hrv = avgHrv.roundToInt(),
            motion = avgMotion,
            updatedAt = latest.timestamp,
            hasRealtimeData = true,
            summary = when (signalState) {
                RelaxSignalState.CALM -> "状态正在放松"
                RelaxSignalState.STEADY -> "状态相对平稳"
                RelaxSignalState.ACTIVE -> "当前唤醒仍偏高"
                RelaxSignalState.FALLBACK -> "已切回节律模式"
            }
        )
    }

    private fun scoreSample(sample: Sample): Float {
        val heartRateScore = (1f - ((sample.heartRate - 58f) / 42f)).coerceIn(0f, 1f)
        val hrvScore = ((sample.hrv - 16f) / 42f).coerceIn(0f, 1f)
        val motionScore = (1f - (sample.motion / 3.8f)).coerceIn(0f, 1f)
        return (heartRateScore * 0.45f + hrvScore * 0.35f + motionScore * 0.20f).coerceIn(0f, 1f)
    }

    private fun RelaxRealtimeFeedback.toFallback(now: Long): RelaxRealtimeFeedback {
        return copy(
            signalState = RelaxSignalState.FALLBACK,
            relaxSignal = if (relaxSignal > 0f) relaxSignal else 0.5f,
            updatedAt = now,
            hasRealtimeData = false,
            summary = "已切回纯呼吸节律模式"
        )
    }
}
