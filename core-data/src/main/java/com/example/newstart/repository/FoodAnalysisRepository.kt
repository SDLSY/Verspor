package com.example.newstart.repository

import android.content.Context
import com.example.newstart.database.AppDatabase
import com.example.newstart.database.dao.FoodAnalysisDao
import com.example.newstart.database.entity.FoodAnalysisEntity
import com.example.newstart.lifestyle.FoodAnalysisRecord
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FoodAnalysisRepository(
    context: Context,
    db: AppDatabase = AppDatabase.getDatabase(context)
) {

    private val gson = Gson()
    private val dao: FoodAnalysisDao = db.foodAnalysisDao()

    fun getLatestFlow(): Flow<FoodAnalysisRecord?> = dao.getLatestFlow().map { entity ->
        entity?.toModel()
    }

    suspend fun getLatest(): FoodAnalysisRecord? = dao.getLatest()?.toModel()

    suspend fun getRecentSince(since: Long): List<FoodAnalysisRecord> {
        return dao.getRecentSince(since).map { it.toModel() }
    }

    suspend fun save(record: FoodAnalysisRecord) {
        dao.upsert(record.toEntity())
    }

    private fun FoodAnalysisEntity.toModel(): FoodAnalysisRecord {
        return FoodAnalysisRecord(
            id = id,
            capturedAt = capturedAt,
            imageUri = imageUri,
            mealType = mealType,
            foodItems = parseStringList(foodItemsJson),
            estimatedCalories = estimatedCalories,
            carbohydrateGrams = carbohydrateGrams,
            proteinGrams = proteinGrams,
            fatGrams = fatGrams,
            nutritionRiskLevel = nutritionRiskLevel,
            nutritionFlags = parseStringList(nutritionFlagsJson),
            dailyContribution = dailyContribution,
            advice = advice,
            confidence = confidence,
            requiresManualReview = requiresManualReview,
            analysisMode = analysisMode,
            providerId = providerId,
            modelId = modelId,
            traceId = traceId,
            syncState = syncState,
            cloudRecordId = cloudRecordId,
            syncedAt = syncedAt
        )
    }

    private fun FoodAnalysisRecord.toEntity(): FoodAnalysisEntity {
        return FoodAnalysisEntity(
            id = id,
            capturedAt = capturedAt,
            imageUri = imageUri,
            mealType = mealType,
            foodItemsJson = gson.toJson(foodItems),
            estimatedCalories = estimatedCalories,
            carbohydrateGrams = carbohydrateGrams,
            proteinGrams = proteinGrams,
            fatGrams = fatGrams,
            nutritionRiskLevel = nutritionRiskLevel,
            nutritionFlagsJson = gson.toJson(nutritionFlags),
            dailyContribution = dailyContribution,
            advice = advice,
            confidence = confidence,
            requiresManualReview = requiresManualReview,
            analysisMode = analysisMode,
            providerId = providerId,
            modelId = modelId,
            traceId = traceId,
            syncState = syncState,
            cloudRecordId = cloudRecordId,
            syncedAt = syncedAt
        )
    }

    private fun parseStringList(rawJson: String): List<String> {
        if (rawJson.isBlank()) return emptyList()
        val listType = object : TypeToken<List<String>>() {}.type
        return gson.fromJson<List<String>>(rawJson, listType).orEmpty()
    }
}
