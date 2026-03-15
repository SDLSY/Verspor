package com.example.newstart.repository

import android.content.Context
import com.example.newstart.database.AppDatabase
import com.example.newstart.database.dao.MedicationAnalysisDao
import com.example.newstart.database.entity.MedicationAnalysisEntity
import com.example.newstart.lifestyle.MedicationAnalysisRecord
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MedicationAnalysisRepository(
    context: Context,
    db: AppDatabase = AppDatabase.getDatabase(context)
) {

    private val gson = Gson()
    private val dao: MedicationAnalysisDao = db.medicationAnalysisDao()

    fun getLatestFlow(): Flow<MedicationAnalysisRecord?> = dao.getLatestFlow().map { entity ->
        entity?.toModel()
    }

    suspend fun getLatest(): MedicationAnalysisRecord? = dao.getLatest()?.toModel()

    suspend fun getRecentSince(since: Long): List<MedicationAnalysisRecord> {
        return dao.getRecentSince(since).map { it.toModel() }
    }

    suspend fun save(record: MedicationAnalysisRecord) {
        dao.upsert(record.toEntity())
    }

    private fun MedicationAnalysisEntity.toModel(): MedicationAnalysisRecord {
        return MedicationAnalysisRecord(
            id = id,
            capturedAt = capturedAt,
            imageUri = imageUri,
            recognizedName = recognizedName,
            dosageForm = dosageForm,
            specification = specification,
            activeIngredients = parseStringList(activeIngredientsJson),
            matchedSymptoms = parseStringList(matchedSymptomsJson),
            usageSummary = usageSummary,
            riskLevel = riskLevel,
            riskFlags = parseStringList(riskFlagsJson),
            evidenceNotes = parseStringList(evidenceNotesJson),
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

    private fun MedicationAnalysisRecord.toEntity(): MedicationAnalysisEntity {
        return MedicationAnalysisEntity(
            id = id,
            capturedAt = capturedAt,
            imageUri = imageUri,
            recognizedName = recognizedName,
            dosageForm = dosageForm,
            specification = specification,
            activeIngredientsJson = gson.toJson(activeIngredients),
            matchedSymptomsJson = gson.toJson(matchedSymptoms),
            usageSummary = usageSummary,
            riskLevel = riskLevel,
            riskFlagsJson = gson.toJson(riskFlags),
            evidenceNotesJson = gson.toJson(evidenceNotes),
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
