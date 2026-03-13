package com.example.newstart.core.model

data class InterventionTask(
    val taskId: String,
    val sourceType: String,
    val bodyZone: String,
    val protocolType: String,
    val durationSec: Int,
)

data class InterventionExecution(
    val executionId: String,
    val taskId: String,
    val elapsedSec: Int,
    val effectScore: Double?,
)

data class InterventionEffect(
    val date: Long,
    val avgEffectScore: Double,
    val executionCount: Int,
)
