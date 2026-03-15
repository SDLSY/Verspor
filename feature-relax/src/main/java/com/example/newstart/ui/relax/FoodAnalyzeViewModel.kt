package com.example.newstart.ui.relax

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.newstart.core.common.R
import com.example.newstart.database.AppDatabase
import com.example.newstart.intervention.ProfileTriggerType
import com.example.newstart.lifestyle.FoodAnalysisRecord
import com.example.newstart.network.models.AiMetadata
import com.example.newstart.network.models.FoodAnalyzeData
import com.example.newstart.network.models.FoodRecordUpsertRequest
import com.example.newstart.repository.FoodAnalysisRepository
import com.example.newstart.repository.InterventionProfileRepository
import com.example.newstart.repository.NetworkRepository
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class FoodAnalyzeViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application
    private val db = AppDatabase.getDatabase(application)
    private val repository = FoodAnalysisRepository(application, db)
    private val networkRepository = NetworkRepository()
    private val profileRepository = InterventionProfileRepository(application, db)

    private val _uiState = MutableLiveData(
        FoodAnalyzeUiState(
            statusText = app.getString(R.string.food_analyze_subtitle),
            advice = app.getString(R.string.food_analyze_advice_placeholder)
        )
    )
    val uiState: LiveData<FoodAnalyzeUiState> = _uiState

    private val _toastEvent = MutableLiveData<String?>()
    val toastEvent: LiveData<String?> = _toastEvent

    private var draftFilePath: String = ""
    private var draftImageUri: String = ""
    private var draftMimeType: String = "image/jpeg"
    private var draftCapturedAt: Long = System.currentTimeMillis()
    private var draftMetadata: AiMetadata? = null
    private var draftRequiresManualReview: Boolean = false

    fun consumeToast() {
        _toastEvent.value = null
    }

    fun prepareImage(filePath: String, imageUri: String, mimeType: String) {
        draftFilePath = filePath
        draftImageUri = imageUri
        draftMimeType = mimeType
        draftCapturedAt = System.currentTimeMillis()
        draftMetadata = null
        draftRequiresManualReview = false

        val loggedIn = networkRepository.getCurrentSession() != null
        _uiState.value = (_uiState.value ?: FoodAnalyzeUiState()).copy(
            previewImageUri = imageUri,
            hasSelectedImage = true,
            canRunCloudAnalyze = loggedIn,
            statusText = if (loggedIn) {
                app.getString(R.string.food_analyze_ready_for_cloud)
            } else {
                app.getString(R.string.food_analyze_manual_status)
            }
        )

        if (loggedIn) {
            analyzeSelectedImage()
        }
    }

    fun analyzeSelectedImage() {
        if (draftFilePath.isBlank()) {
            _toastEvent.value = app.getString(R.string.food_analyze_pick_image_first)
            return
        }
        if (networkRepository.getCurrentSession() == null) {
            _uiState.value = (_uiState.value ?: FoodAnalyzeUiState()).copy(
                canRunCloudAnalyze = false,
                statusText = app.getString(R.string.food_analyze_manual_status)
            )
            _toastEvent.value = app.getString(R.string.food_analyze_login_required)
            return
        }

        viewModelScope.launch {
            _uiState.value = (_uiState.value ?: FoodAnalyzeUiState()).copy(
                isAnalyzing = true,
                statusText = app.getString(R.string.food_analyze_running)
            )
            val result = networkRepository.analyzeFoodImage(
                file = File(draftFilePath),
                mimeType = draftMimeType
            )
            result.onSuccess { payload ->
                applyCloudPayload(payload)
            }.onFailure { error ->
                val loginExpired = networkRepository.getCurrentSession() == null
                _uiState.postValue(
                    (_uiState.value ?: FoodAnalyzeUiState()).copy(
                        isAnalyzing = false,
                        canRunCloudAnalyze = !loginExpired,
                        statusText = if (loginExpired) {
                            app.getString(R.string.food_analyze_login_expired)
                        } else {
                            app.getString(R.string.food_analyze_failed, error.message ?: "")
                        }
                    )
                )
            }
        }
    }

    fun saveRecord(
        mealType: String,
        foodItemsText: String,
        estimatedCaloriesText: String,
        carbohydrateText: String,
        proteinText: String,
        fatText: String,
        nutritionRiskLevel: String,
        nutritionFlagsText: String,
        dailyContribution: String,
        advice: String
    ) {
        if (draftImageUri.isBlank()) {
            _toastEvent.value = app.getString(R.string.food_analyze_pick_image_first)
            return
        }
        if (foodItemsText.isBlank()) {
            _toastEvent.value = app.getString(R.string.food_analyze_items_required)
            return
        }

        viewModelScope.launch {
            val hasCloudSession = networkRepository.getCurrentSession() != null
            val initialRecord = FoodAnalysisRecord(
                id = UUID.randomUUID().toString(),
                capturedAt = draftCapturedAt,
                imageUri = draftImageUri,
                mealType = mealType.trim().uppercase().ifBlank { "UNSPECIFIED" },
                foodItems = splitDraftValues(foodItemsText),
                estimatedCalories = estimatedCaloriesText.toIntOrNull() ?: 0,
                carbohydrateGrams = carbohydrateText.toFloatOrNull() ?: 0f,
                proteinGrams = proteinText.toFloatOrNull() ?: 0f,
                fatGrams = fatText.toFloatOrNull() ?: 0f,
                nutritionRiskLevel = normalizeRiskLevel(nutritionRiskLevel),
                nutritionFlags = splitDraftValues(nutritionFlagsText),
                dailyContribution = dailyContribution.trim(),
                advice = advice.trim(),
                confidence = _uiState.value?.confidence ?: 0f,
                requiresManualReview = draftRequiresManualReview,
                analysisMode = if (draftMetadata != null) "CLOUD_IMAGE_PARSE" else "MANUAL",
                providerId = draftMetadata?.providerId,
                modelId = draftMetadata?.modelId,
                traceId = draftMetadata?.traceId,
                syncState = if (hasCloudSession) "PENDING" else "LOCAL_ONLY"
            )
            repository.save(initialRecord)

            val finalRecord = if (hasCloudSession) {
                val syncOk = networkRepository.upsertFoodRecord(
                    FoodRecordUpsertRequest(
                        recordId = initialRecord.id,
                        capturedAt = initialRecord.capturedAt,
                        imageUri = initialRecord.imageUri,
                        mealType = initialRecord.mealType,
                        foodItems = initialRecord.foodItems,
                        estimatedCalories = initialRecord.estimatedCalories,
                        carbohydrateGrams = initialRecord.carbohydrateGrams,
                        proteinGrams = initialRecord.proteinGrams,
                        fatGrams = initialRecord.fatGrams,
                        nutritionRiskLevel = initialRecord.nutritionRiskLevel,
                        nutritionFlags = initialRecord.nutritionFlags,
                        dailyContribution = initialRecord.dailyContribution,
                        advice = initialRecord.advice,
                        confidence = initialRecord.confidence,
                        requiresManualReview = initialRecord.requiresManualReview,
                        analysisMode = initialRecord.analysisMode,
                        providerId = initialRecord.providerId,
                        modelId = initialRecord.modelId,
                        traceId = initialRecord.traceId
                    )
                ).isSuccess
                initialRecord.copy(
                    syncState = if (syncOk) "SYNCED" else "PENDING",
                    cloudRecordId = if (syncOk) initialRecord.id else null,
                    syncedAt = if (syncOk) System.currentTimeMillis() else null
                )
            } else {
                initialRecord
            }

            if (finalRecord != initialRecord) {
                repository.save(finalRecord)
            }
            profileRepository.refreshSnapshot(ProfileTriggerType.DAILY_REFRESH)
            _uiState.postValue(
                (_uiState.value ?: FoodAnalyzeUiState()).copy(
                    isAnalyzing = false,
                    statusText = app.getString(R.string.food_analyze_saved)
                )
            )
            _toastEvent.postValue(app.getString(R.string.food_analyze_saved))
        }
    }

    private fun applyCloudPayload(payload: FoodAnalyzeData) {
        draftMetadata = payload.metadata
        draftRequiresManualReview = payload.requiresManualReview
        _uiState.postValue(
            FoodAnalyzeUiState(
                previewImageUri = draftImageUri,
                hasSelectedImage = true,
                canRunCloudAnalyze = true,
                isAnalyzing = false,
                statusText = if (payload.requiresManualReview) {
                    app.getString(R.string.food_analyze_manual_review_required)
                } else {
                    app.getString(R.string.food_analyze_cloud_done)
                },
                mealType = payload.mealType,
                foodItemsText = payload.foodItems.joinToString("、"),
                estimatedCaloriesText = payload.estimatedCalories.toString(),
                carbohydrateText = payload.carbohydrateGrams.toInt().toString(),
                proteinText = payload.proteinGrams.toInt().toString(),
                fatText = payload.fatGrams.toInt().toString(),
                nutritionRiskLevel = payload.nutritionRiskLevel,
                nutritionFlagsText = payload.nutritionFlags.joinToString("、"),
                dailyContribution = payload.dailyContribution,
                advice = payload.advice.ifBlank {
                    app.getString(R.string.food_analyze_advice_placeholder)
                },
                confidence = payload.confidence,
                requiresManualReview = payload.requiresManualReview
            )
        )
    }

    private fun splitDraftValues(raw: String): List<String> {
        return raw.split(',', '，', '\n', ';', '；')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun normalizeRiskLevel(raw: String): String {
        return when (raw.trim().uppercase()) {
            "HIGH" -> "HIGH"
            "MEDIUM" -> "MEDIUM"
            else -> "LOW"
        }
    }
}

data class FoodAnalyzeUiState(
    val previewImageUri: String = "",
    val hasSelectedImage: Boolean = false,
    val canRunCloudAnalyze: Boolean = false,
    val isAnalyzing: Boolean = false,
    val statusText: String = "",
    val mealType: String = "UNSPECIFIED",
    val foodItemsText: String = "",
    val estimatedCaloriesText: String = "",
    val carbohydrateText: String = "",
    val proteinText: String = "",
    val fatText: String = "",
    val nutritionRiskLevel: String = "LOW",
    val nutritionFlagsText: String = "",
    val dailyContribution: String = "",
    val advice: String = "",
    val confidence: Float = 0f,
    val requiresManualReview: Boolean = false
)
