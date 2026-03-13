package com.example.newstart.repository.prescription

import com.google.gson.annotations.SerializedName

data class PrescriptionProtocolDescriptor(
    val protocolCode: String,
    val displayName: String,
    val interventionType: String,
    val description: String
)

data class PrescriptionDecisionRequest(
    val triggerType: String,
    val domainScores: Map<String, Int>,
    val evidenceFacts: Map<String, List<String>>,
    val redFlags: List<String>,
    val personalizationLevel: String,
    val missingInputs: List<String>,
    val ragContext: String,
    val catalog: List<PrescriptionProtocolDescriptor>
)

data class PrescriptionDecisionPayload(
    @SerializedName("primaryGoal")
    val primaryGoal: String = "",
    @SerializedName("riskLevel")
    val riskLevel: String = "MEDIUM",
    @SerializedName("targetDomains")
    val targetDomains: List<String> = emptyList(),
    @SerializedName("primaryInterventionType")
    val primaryInterventionType: String = "",
    @SerializedName("secondaryInterventionType")
    val secondaryInterventionType: String = "",
    @SerializedName("lifestyleTaskCodes")
    val lifestyleTaskCodes: List<String> = emptyList(),
    @SerializedName("timing")
    val timing: String = "FLEXIBLE",
    @SerializedName("durationSec")
    val durationSec: Int = 0,
    @SerializedName("rationale")
    val rationale: String = "",
    @SerializedName("evidence")
    val evidence: List<String> = emptyList(),
    @SerializedName("contraindications")
    val contraindications: List<String> = emptyList(),
    @SerializedName("followupMetric")
    val followupMetric: String = "",
    @SerializedName("personalizationLevel")
    val personalizationLevel: String = "PREVIEW",
    @SerializedName("missingInputs")
    val missingInputs: List<String> = emptyList(),
    @SerializedName("isPreview")
    val isPreview: Boolean = true
)
