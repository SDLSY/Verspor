package com.example.newstart.ai

data class InterventionPlanInput(
    val bodyZone: String,
    val zoneDetail: String = bodyZone,
    val pickSource: String = "unknown",
    val triggerReason: String,
    val stressIndex: Int,
    val recoveryScore: Int,
    val heartRate: Int,
    val hrv: Int,
    val spo2: Int
)

data class InterventionAction(
    val name: String,
    val detail: String,
    val durationSec: Int
)

data class InterventionPlanResult(
    val title: String,
    val rationale: String,
    val actions: List<InterventionAction>,
    val caution: String,
    val completionRule: String,
    val protocolType: String,
    val fallbackUsed: Boolean
)
