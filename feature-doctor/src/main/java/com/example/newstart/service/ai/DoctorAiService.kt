package com.example.newstart.service.ai

import android.app.Application
import com.example.newstart.core.common.R
import com.example.newstart.network.models.DoctorTurnRequest
import com.example.newstart.repository.NetworkRepository
import com.example.newstart.ui.doctor.DoctorAssessmentPayload
import com.example.newstart.ui.doctor.DoctorFollowUpPayload
import com.example.newstart.ui.doctor.DoctorInferenceSource
import com.example.newstart.ui.doctor.DoctorInquiryStage
import com.example.newstart.ui.doctor.DoctorSuspectedIssue
import com.example.newstart.ui.doctor.DoctorTurnDecision

class DoctorAiService(
    private val application: Application,
    private val networkRepository: NetworkRepository
) {

    suspend fun generateCloudTurn(
        conversationBlock: String,
        contextBlock: String,
        ragContext: String,
        stage: DoctorInquiryStage,
        followUpCount: Int,
        defaultRiskLevel: String
    ): DoctorTurnDecision? {
        if (networkRepository.getCurrentSession() == null) {
            return null
        }

        val response = networkRepository.generateDoctorTurn(
            DoctorTurnRequest(
                conversationBlock = conversationBlock,
                contextBlock = contextBlock,
                ragContext = ragContext,
                stage = stage.name,
                followUpCount = followUpCount
            )
        ).getOrNull() ?: return null

        val nextStage = when {
            response.redFlags.isNotEmpty() -> DoctorInquiryStage.ESCALATED
            response.stage.equals(DoctorInquiryStage.ESCALATED.name, ignoreCase = true) -> DoctorInquiryStage.ESCALATED
            response.followUpQuestion.isNotBlank() -> DoctorInquiryStage.CLARIFYING
            else -> DoctorInquiryStage.COMPLETED
        }

        return if (nextStage == DoctorInquiryStage.CLARIFYING) {
            DoctorTurnDecision(
                nextStage = nextStage,
                source = DoctorInferenceSource.CLOUD_ENHANCED,
                followUp = DoctorFollowUpPayload(
                    question = response.followUpQuestion,
                    missingInfo = response.missingInfo,
                    stage = nextStage
                )
            )
        } else {
            DoctorTurnDecision(
                nextStage = nextStage,
                source = DoctorInferenceSource.CLOUD_ENHANCED,
                assessment = DoctorAssessmentPayload(
                    chiefComplaint = response.chiefComplaint.ifBlank {
                        application.getString(R.string.doctor_history_default_title)
                    },
                    symptomFacts = response.symptomFacts,
                    missingInfo = response.missingInfo,
                    suspectedIssues = response.suspectedIssues.map {
                        DoctorSuspectedIssue(
                            name = it.name,
                            rationale = it.rationale,
                            confidence = it.confidence
                        )
                    },
                    riskLevel = response.riskLevel.ifBlank { defaultRiskLevel },
                    redFlags = response.redFlags,
                    recommendedDepartment = response.recommendedDepartment.ifBlank { "全科" },
                    nextStepAdvice = response.nextStepAdvice.ifEmpty {
                        listOf("建议结合线下面诊进一步确认。")
                    },
                    doctorSummary = response.doctorSummary,
                    disclaimer = response.disclaimer
                )
            )
        }
    }
}
