package com.example.newstart.ui.doctor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DoctorInferenceEngineTest {

    @Test
    fun parseStructuredTurn_parsesAssessmentJson() {
        val parsed = DoctorInferenceEngine.parseStructuredTurn(
            """
            {
              "chiefComplaint": "反复失眠并伴随白天疲劳",
              "symptomFacts": ["近两周入睡困难", "白天明显犯困"],
              "missingInfo": ["具体持续时间"],
              "suspectedIssues": [
                {"name": "压力相关失眠", "rationale": "症状与精神紧张和入睡困难相关", "confidence": 84}
              ],
              "riskLevel": "MEDIUM",
              "redFlags": [],
              "recommendedDepartment": "心身医学科",
              "nextStepAdvice": ["补充持续时间", "记录最近三天睡眠时长"],
              "doctorSummary": "当前更像压力相关失眠，建议继续线下确认。",
              "disclaimer": "仅用于初筛",
              "followUpQuestion": "",
              "stage": "COMPLETED"
            }
            """.trimIndent()
        )

        assertNotNull(parsed)
        assertEquals("反复失眠并伴随白天疲劳", parsed?.chiefComplaint)
        assertEquals(1, parsed?.suspectedIssues?.size)
        assertEquals("MEDIUM", parsed?.riskLevel)
    }

    @Test
    fun generateTurn_escalatesImmediately_whenRedFlagPresent() {
        val snapshot = DoctorMetricSnapshot(
            recoveryScore = 60,
            sleepMinutes = 420,
            sleepEfficiency = 86f,
            awakeCount = 1,
            heartRate = 80,
            spo2Min = 97,
            hrvCurrent = 35,
            hrvBaseline = 36
        )
        val risk = DoctorDecisionEngine.evaluateRisk(snapshot)
        val decision = DoctorInferenceEngine.generateTurn(
            DoctorInferenceInput(
                latestUserMessage = "我这两天胸痛，而且喘不上气。",
                conversation = emptyList(),
                stage = DoctorInquiryStage.INTAKE,
                followUpCount = 0,
                snapshot = snapshot,
                riskSummary = risk,
                contextBlock = "recovery_score=60",
                ragContext = ""
            )
        )

        assertEquals(DoctorInquiryStage.ESCALATED, decision.nextStage)
        assertTrue(decision.assessment?.redFlags?.isNotEmpty() == true)
    }
}
