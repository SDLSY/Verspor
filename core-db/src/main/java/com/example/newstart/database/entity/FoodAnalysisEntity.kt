package com.example.newstart.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "food_analysis_records",
    indices = [
        Index(value = ["capturedAt"]),
        Index(value = ["nutritionRiskLevel"]),
        Index(value = ["syncState"]),
        Index(value = ["requiresManualReview"])
    ]
)
data class FoodAnalysisEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val capturedAt: Long,
    val imageUri: String,
    val mealType: String,
    val foodItemsJson: String,
    val estimatedCalories: Int,
    val carbohydrateGrams: Float,
    val proteinGrams: Float,
    val fatGrams: Float,
    val nutritionRiskLevel: String,
    val nutritionFlagsJson: String,
    val dailyContribution: String,
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
