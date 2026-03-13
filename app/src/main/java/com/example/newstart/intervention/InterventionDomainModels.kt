package com.example.newstart.intervention

enum class AssessmentSource {
    BASELINE,
    WEEKLY_TRACK,
    MONTHLY_REVIEW,
    EVENT_TRIGGER
}

enum class ProfileTriggerType {
    DAILY_REFRESH,
    DOCTOR_ASSESSMENT,
    MEDICAL_REPORT,
    SCALE_COMPLETION,
    EXECUTION_FEEDBACK,
    ZONE_TRIGGER
}

enum class PrescriptionItemType {
    PRIMARY,
    SECONDARY,
    LIFESTYLE
}

enum class PrescriptionTimingSlot {
    MORNING,
    AFTERNOON,
    EVENING,
    BEFORE_SLEEP,
    FLEXIBLE
}

enum class PersonalizationLevel {
    PREVIEW,
    FULL
}

enum class PersonalizationMissingInput {
    DEVICE_DATA,
    BASELINE_ASSESSMENT,
    DOCTOR_INQUIRY
}

data class PersonalizationStatus(
    val level: PersonalizationLevel,
    val missingInputs: List<PersonalizationMissingInput>
) {
    val isPreview: Boolean = level == PersonalizationLevel.PREVIEW
}

data class AssessmentScaleResult(
    val scaleCode: String,
    val scaleTitle: String,
    val totalScore: Int,
    val severityLabel: String,
    val completedAt: Long,
    val freshnessUntil: Long,
    val source: String
)

data class InterventionProfileSnapshot(
    val id: String,
    val generatedAt: Long,
    val triggerType: ProfileTriggerType,
    val domainScores: Map<String, Int>,
    val evidenceFacts: Map<String, List<String>>,
    val redFlags: List<String>
)

data class InterventionProfileViewData(
    val snapshot: InterventionProfileSnapshot?,
    val scaleResults: List<AssessmentScaleResult>,
    val latestDoctorSummary: String,
    val latestMedicalSummary: String,
    val adherenceHint: String,
    val baselineCompleted: Boolean,
    val personalizationStatus: PersonalizationStatus,
    val personalizationSummary: String,
    val missingInputSummary: String
)

data class PrescriptionItemDetails(
    val id: String,
    val itemType: PrescriptionItemType,
    val protocolCode: String,
    val assetRef: String,
    val durationSec: Int,
    val sequenceOrder: Int,
    val timingSlot: PrescriptionTimingSlot,
    val isRequired: Boolean,
    val status: String
)

data class PrescriptionBundleDetails(
    val id: String,
    val createdAt: Long,
    val triggerType: String,
    val primaryGoal: String,
    val riskLevel: String,
    val rationale: String,
    val evidence: List<String>,
    val items: List<PrescriptionItemDetails>
)
