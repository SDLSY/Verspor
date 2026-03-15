package com.example.newstart.lifestyle

data class MedicationAnalysisRecord(
    val id: String,
    val capturedAt: Long,
    val imageUri: String,
    val recognizedName: String,
    val dosageForm: String,
    val specification: String,
    val activeIngredients: List<String>,
    val matchedSymptoms: List<String>,
    val usageSummary: String,
    val riskLevel: String,
    val riskFlags: List<String>,
    val evidenceNotes: List<String>,
    val advice: String,
    val confidence: Float,
    val requiresManualReview: Boolean,
    val analysisMode: String,
    val providerId: String? = null,
    val modelId: String? = null,
    val traceId: String? = null,
    val syncState: String,
    val cloudRecordId: String? = null,
    val syncedAt: Long? = null
)

data class FoodAnalysisRecord(
    val id: String,
    val capturedAt: Long,
    val imageUri: String,
    val mealType: String,
    val foodItems: List<String>,
    val estimatedCalories: Int,
    val carbohydrateGrams: Float,
    val proteinGrams: Float,
    val fatGrams: Float,
    val nutritionRiskLevel: String,
    val nutritionFlags: List<String>,
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
    val syncedAt: Long? = null
)

data class DailyReadinessContribution(
    val medicationDelta: Int,
    val nutritionDelta: Int,
    val interventionDelta: Int,
    val summary: String
)
