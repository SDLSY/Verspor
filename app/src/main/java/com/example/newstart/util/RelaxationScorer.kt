package com.example.newstart.util

import kotlin.math.abs

data class RelaxVitalSnapshot(
    val heartRate: Int,
    val hrv: Int,
    val spo2: Int,
    val motion: Float
)

data class RelaxEffectResult(
    val preStress: Float,
    val postStress: Float,
    val effectScore: Float
)

object RelaxationScorer {

    fun calculateStressIndex(snapshot: RelaxVitalSnapshot): Int {
        val weighted = ArrayList<Pair<Float, Float>>()

        if (snapshot.heartRate in 25..240) {
            val hrRisk = (abs(snapshot.heartRate - 65f) / 35f * 100f).coerceIn(0f, 100f)
            weighted += hrRisk to 0.35f
        }

        if (snapshot.hrv in 10..220) {
            val hrvRisk = ((55f - snapshot.hrv) / 35f * 100f).coerceIn(0f, 100f)
            weighted += hrvRisk to 0.35f
        }

        if (snapshot.spo2 in 70..100) {
            val spo2Risk = ((97f - snapshot.spo2) / 7f * 100f).coerceIn(0f, 100f)
            weighted += spo2Risk to 0.20f
        }

        if (snapshot.motion > 0f) {
            val motionRisk = (snapshot.motion / 20f * 100f).coerceIn(0f, 100f)
            weighted += motionRisk to 0.10f
        }

        if (weighted.isEmpty()) return 0

        val weightSum = weighted.sumOf { it.second.toDouble() }.toFloat().coerceAtLeast(1e-6f)
        val value = weighted.sumOf { (it.first * it.second).toDouble() }.toFloat() / weightSum
        return value.toInt().coerceIn(0, 100)
    }

    fun calculateEffect(pre: RelaxVitalSnapshot, post: RelaxVitalSnapshot): RelaxEffectResult {
        val preStress = calculateStressIndex(pre).toFloat()
        val postStress = calculateStressIndex(post).toFloat()

        val deltaStress = preStress - postStress
        val deltaHr = (pre.heartRate - post.heartRate).toFloat()
        val deltaHrv = (post.hrv - pre.hrv).toFloat()
        val deltaMotion = pre.motion - post.motion

        val effectScore = (50f +
            0.7f * deltaStress +
            0.8f * deltaHr +
            0.6f * deltaHrv +
            1.2f * deltaMotion
            ).coerceIn(0f, 100f)

        return RelaxEffectResult(
            preStress = preStress,
            postStress = postStress,
            effectScore = effectScore
        )
    }
}