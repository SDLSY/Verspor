package com.example.newstart.ui.doctor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DoctorDecisionEngineTest {

    @Test
    fun evaluateRisk_returnsHigh_whenRecoveryAndSleepArePoor() {
        val snapshot = DoctorMetricSnapshot(
            recoveryScore = 45,
            sleepMinutes = 320,
            sleepEfficiency = 78f,
            awakeCount = 4,
            heartRate = 98,
            spo2Min = 91,
            hrvCurrent = 28,
            hrvBaseline = 44
        )

        val summary = DoctorDecisionEngine.evaluateRisk(snapshot)
        assertEquals(DoctorRiskLevel.HIGH, summary.level)
        assertTrue(summary.score >= 65)
    }

    @Test
    fun buildRecommendations_returnsTwoActionableProtocols() {
        val snapshot = DoctorMetricSnapshot(
            recoveryScore = 72,
            sleepMinutes = 430,
            sleepEfficiency = 88f,
            awakeCount = 1,
            heartRate = 78,
            spo2Min = 96,
            hrvCurrent = 40,
            hrvBaseline = 42
        )
        val risk = DoctorDecisionEngine.evaluateRisk(snapshot)
        val recommendations = DoctorDecisionEngine.buildRecommendations(risk, snapshot)

        assertTrue(recommendations.size >= 2)
        assertTrue(recommendations.first().durationSec > 0)
        assertTrue(recommendations.first().protocolType.isNotBlank())
    }

    @Test
    fun detectActionFromReply_parsesProtocolAndChineseDuration() {
        val reply = "建议先做 4-7-8 呼吸 3分钟，结束后再复测主观压力。"

        val action = DoctorDecisionEngine.detectActionFromReply(reply)
        assertNotNull(action)
        assertEquals("BREATH_4_7_8", action?.protocolType)
        assertEquals(180, action?.durationSec)
    }

    @Test
    fun detectRedFlags_returnsUrgentTips_whenChestPainPresent() {
        val redFlags = DoctorDecisionEngine.detectRedFlags("我昨晚胸痛，还出现过喘不上气。")
        assertFalse(redFlags.isEmpty())
        assertTrue(redFlags.any { it.contains("红旗", ignoreCase = false) || it.contains("胸痛") })
    }

    @Test
    fun buildFallbackAssessment_returnsSuspectedIssues() {
        val snapshot = DoctorMetricSnapshot(
            recoveryScore = 51,
            sleepMinutes = 350,
            sleepEfficiency = 81f,
            awakeCount = 3,
            heartRate = 92,
            spo2Min = 92,
            hrvCurrent = 25,
            hrvBaseline = 39
        )
        val assessment = DoctorDecisionEngine.buildFallbackAssessment(
            historyText = "最近总是失眠，压力也很大，夜里会打鼾，白天犯困。",
            snapshot = snapshot,
            riskSummary = DoctorDecisionEngine.evaluateRisk(snapshot)
        )

        assertTrue(assessment.suspectedIssues.isNotEmpty())
        assertTrue(assessment.recommendedDepartment.isNotBlank())
        assertTrue(assessment.doctorSummary.isNotBlank())
    }
}
