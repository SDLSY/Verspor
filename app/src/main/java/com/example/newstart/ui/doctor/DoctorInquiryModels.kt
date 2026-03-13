package com.example.newstart.ui.doctor

enum class DoctorInquiryStage {
    INTAKE,
    CLARIFYING,
    ASSESSING,
    COMPLETED,
    ESCALATED
}

enum class DoctorMessageType {
    TEXT,
    FOLLOW_UP,
    ASSESSMENT
}

data class DoctorSuspectedIssue(
    val name: String,
    val rationale: String,
    val confidence: Int
)

data class DoctorFollowUpPayload(
    val question: String,
    val missingInfo: List<String> = emptyList(),
    val stage: DoctorInquiryStage = DoctorInquiryStage.CLARIFYING
)

data class DoctorAssessmentPayload(
    val chiefComplaint: String,
    val symptomFacts: List<String>,
    val missingInfo: List<String>,
    val suspectedIssues: List<DoctorSuspectedIssue>,
    val riskLevel: String,
    val redFlags: List<String>,
    val recommendedDepartment: String,
    val nextStepAdvice: List<String>,
    val doctorSummary: String,
    val disclaimer: String
)

data class DoctorHistorySummary(
    val sessionId: String,
    val title: String,
    val subtitle: String,
    val updatedAt: Long,
    val riskLevel: String
)
