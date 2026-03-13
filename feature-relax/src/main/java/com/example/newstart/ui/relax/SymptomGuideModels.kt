package com.example.newstart.ui.relax

const val DOCTOR_PREFILL_MESSAGE_KEY = "doctor_prefill_message"

enum class SurfaceSide {
    FRONT,
    BACK
}

enum class SymptomBodyZone {
    HEAD,
    CHEST,
    ABDOMEN,
    LIMB
}

enum class SymptomRiskLevel {
    HIGH,
    MEDIUM,
    LOW
}

data class SymptomTag(
    val label: String,
    val zone: SymptomBodyZone,
    val isRedFlag: Boolean = false
)

data class SelectedBodyMarker(
    val id: String,
    val zone: SymptomBodyZone,
    val surfaceSide: SurfaceSide,
    val symptomLabel: String,
    val severity: Int,
    val durationLabel: String,
    val note: String = ""
)

data class SymptomSuspectedDirection(
    val title: String,
    val reason: String,
    val confidenceLabel: String
)

data class SymptomSupportAction(
    val enabled: Boolean,
    val label: String,
    val protocolType: String = "",
    val durationSec: Int = 0,
    val reason: String = ""
)

data class SymptomCheckOutcome(
    val riskLevel: SymptomRiskLevel,
    val riskTitle: String,
    val riskSummary: String,
    val suspectedDirections: List<SymptomSuspectedDirection>,
    val evidenceSummary: String,
    val deviceEvidence: String,
    val suggestedDepartment: String,
    val suggestedChecks: String,
    val nextSteps: List<String>,
    val doctorPrefill: String,
    val supportAction: SymptomSupportAction,
    val disclaimer: String
)

data class SymptomGuideUiState(
    val selectedSurfaceSide: SurfaceSide = SurfaceSide.FRONT,
    val redFlagHints: List<String> = emptyList(),
    val quickSymptoms: List<SymptomTag> = emptyList(),
    val triggerOptions: List<String> = emptyList(),
    val associatedSymptomOptions: List<String> = emptyList(),
    val selectedMarkers: List<SelectedBodyMarker> = emptyList(),
    val selectedTrigger: String = "",
    val selectedAssociatedSymptoms: List<String> = emptyList(),
    val additionalNote: String = "",
    val deviceEvidence: String = "",
    val outcome: SymptomCheckOutcome? = null,
    val canGenerate: Boolean = false
)

data class SymptomGuideLaunchCommand(
    val protocolType: String,
    val durationSec: Int,
    val taskId: String
)
