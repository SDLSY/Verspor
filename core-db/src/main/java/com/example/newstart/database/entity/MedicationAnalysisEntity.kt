package com.example.newstart.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "medication_analysis_records",
    indices = [
        Index(value = ["capturedAt"]),
        Index(value = ["riskLevel"]),
        Index(value = ["syncState"]),
        Index(value = ["requiresManualReview"])
    ]
)
data class MedicationAnalysisEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val capturedAt: Long,
    val imageUri: String,
    val recognizedName: String,
    val dosageForm: String,
    val specification: String,
    val activeIngredientsJson: String,
    val matchedSymptomsJson: String,
    val usageSummary: String,
    val riskLevel: String,
    val riskFlagsJson: String,
    val evidenceNotesJson: String,
    val advice: String,
    val confidence: Float,
    val requiresManualReview: Boolean,
    val analysisMode: String,
    val providerId: String? = null,
    val modelId: String? = null,
    val traceId: String? = null,
    val syncState: String,
    val cloudRecordId: String? = null,
    val syncedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
