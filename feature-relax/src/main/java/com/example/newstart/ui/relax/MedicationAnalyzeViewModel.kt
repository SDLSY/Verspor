package com.example.newstart.ui.relax

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.newstart.core.common.R
import com.example.newstart.database.AppDatabase
import com.example.newstart.lifestyle.MedicationAnalysisRecord
import com.example.newstart.network.models.AiMetadata
import com.example.newstart.network.models.MedicationAnalyzeData
import com.example.newstart.network.models.MedicationRecordUpsertRequest
import com.example.newstart.intervention.ProfileTriggerType
import com.example.newstart.repository.InterventionProfileRepository
import com.example.newstart.repository.MedicationAnalysisRepository
import com.example.newstart.repository.NetworkRepository
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class MedicationAnalyzeViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application
    private val db = AppDatabase.getDatabase(application)
    private val repository = MedicationAnalysisRepository(application, db)
    private val networkRepository = NetworkRepository()
    private val profileRepository = InterventionProfileRepository(application, db)

    private val _uiState = MutableLiveData(
        MedicationAnalyzeUiState(
            statusText = app.getString(R.string.medication_analyze_subtitle),
            advice = app.getString(R.string.medication_analyze_advice_placeholder)
        )
    )
    val uiState: LiveData<MedicationAnalyzeUiState> = _uiState

    private val _toastEvent = MutableLiveData<String?>()
    val toastEvent: LiveData<String?> = _toastEvent

    private var draftFilePath: String = ""
    private var draftImageUri: String = ""
    private var draftMimeType: String = "image/jpeg"
    private var draftCapturedAt: Long = System.currentTimeMillis()
    private var draftMetadata: AiMetadata? = null
    private var draftEvidenceNotes: List<String> = emptyList()
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
        draftEvidenceNotes = emptyList()
        draftRequiresManualReview = false

        val loggedIn = networkRepository.getCurrentSession() != null
        _uiState.value = (_uiState.value ?: MedicationAnalyzeUiState()).copy(
            previewImageUri = imageUri,
            hasSelectedImage = true,
            canRunCloudAnalyze = loggedIn,
            statusText = if (loggedIn) {
                app.getString(R.string.medication_analyze_ready_for_cloud)
            } else {
                app.getString(R.string.medication_analyze_manual_status)
            }
        )

        if (loggedIn) {
            analyzeSelectedImage()
        }
    }

    fun analyzeSelectedImage() {
        if (draftFilePath.isBlank()) {
            _toastEvent.value = app.getString(R.string.medication_analyze_pick_image_first)
            return
        }
        if (networkRepository.getCurrentSession() == null) {
            _uiState.value = (_uiState.value ?: MedicationAnalyzeUiState()).copy(
                canRunCloudAnalyze = false,
                statusText = app.getString(R.string.medication_analyze_manual_status)
            )
            _toastEvent.value = app.getString(R.string.medication_analyze_login_required)
            return
        }

        viewModelScope.launch {
            _uiState.value = (_uiState.value ?: MedicationAnalyzeUiState()).copy(
                isAnalyzing = true,
                statusText = app.getString(R.string.medication_analyze_running)
            )
            val result = networkRepository.analyzeMedicationImage(
                file = File(draftFilePath),
                mimeType = draftMimeType
            )
            result.onSuccess { payload ->
                applyCloudPayload(payload)
            }.onFailure { error ->
                val loginExpired = networkRepository.getCurrentSession() == null
                _uiState.postValue(
                    (_uiState.value ?: MedicationAnalyzeUiState()).copy(
                        isAnalyzing = false,
                        canRunCloudAnalyze = !loginExpired,
                        statusText = if (loginExpired) {
                            app.getString(R.string.medication_analyze_login_expired)
                        } else {
                            app.getString(R.string.medication_analyze_failed, error.message ?: "")
                        }
                    )
                )
            }
        }
    }

    fun saveRecord(
        recognizedName: String,
        dosageForm: String,
        specification: String,
        activeIngredientsText: String,
        matchedSymptomsText: String,
        usageSummary: String,
        riskLevel: String,
        riskFlagsText: String,
        advice: String
    ) {
        if (draftImageUri.isBlank()) {
            _toastEvent.value = app.getString(R.string.medication_analyze_pick_image_first)
            return
        }
        if (recognizedName.isBlank()) {
            _toastEvent.value = app.getString(R.string.medication_analyze_name_required)
            return
        }

        viewModelScope.launch {
            val hasCloudSession = networkRepository.getCurrentSession() != null
            val initialRecord = MedicationAnalysisRecord(
                id = UUID.randomUUID().toString(),
                capturedAt = draftCapturedAt,
                imageUri = draftImageUri,
                recognizedName = recognizedName.trim(),
                dosageForm = dosageForm.trim(),
                specification = specification.trim(),
                activeIngredients = splitDraftValues(activeIngredientsText),
                matchedSymptoms = splitDraftValues(matchedSymptomsText),
                usageSummary = usageSummary.trim(),
                riskLevel = normalizeRiskLevel(riskLevel),
                riskFlags = splitDraftValues(riskFlagsText),
                evidenceNotes = draftEvidenceNotes,
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
                val syncOk = networkRepository.upsertMedicationRecord(
                    MedicationRecordUpsertRequest(
                        recordId = initialRecord.id,
                        capturedAt = initialRecord.capturedAt,
                        imageUri = initialRecord.imageUri,
                        recognizedName = initialRecord.recognizedName,
                        dosageForm = initialRecord.dosageForm,
                        specification = initialRecord.specification,
                        activeIngredients = initialRecord.activeIngredients,
                        matchedSymptoms = initialRecord.matchedSymptoms,
                        usageSummary = initialRecord.usageSummary,
                        riskLevel = initialRecord.riskLevel,
                        riskFlags = initialRecord.riskFlags,
                        evidenceNotes = initialRecord.evidenceNotes,
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
                (_uiState.value ?: MedicationAnalyzeUiState()).copy(
                    isAnalyzing = false,
                    statusText = app.getString(R.string.medication_analyze_saved)
                )
            )
            _toastEvent.postValue(app.getString(R.string.medication_analyze_saved))
        }
    }

    private fun applyCloudPayload(payload: MedicationAnalyzeData) {
        draftMetadata = payload.metadata
        draftEvidenceNotes = payload.evidenceNotes
        draftRequiresManualReview = payload.requiresManualReview
        _uiState.postValue(
            MedicationAnalyzeUiState(
                previewImageUri = draftImageUri,
                hasSelectedImage = true,
                canRunCloudAnalyze = true,
                isAnalyzing = false,
                statusText = if (payload.requiresManualReview) {
                    app.getString(R.string.medication_analyze_manual_review_required)
                } else {
                    app.getString(R.string.medication_analyze_cloud_done)
                },
                recognizedName = payload.recognizedName,
                dosageForm = payload.dosageForm,
                specification = payload.specification,
                activeIngredientsText = payload.activeIngredients.joinToString("、"),
                matchedSymptomsText = payload.matchedSymptoms.joinToString("、"),
                usageSummary = payload.usageSummary,
                riskLevel = payload.riskLevel,
                riskFlagsText = payload.riskFlags.joinToString("、"),
                advice = payload.advice.ifBlank {
                    app.getString(R.string.medication_analyze_advice_placeholder)
                },
                evidenceText = payload.evidenceNotes.joinToString("\n"),
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

data class MedicationAnalyzeUiState(
    val previewImageUri: String = "",
    val hasSelectedImage: Boolean = false,
    val canRunCloudAnalyze: Boolean = false,
    val isAnalyzing: Boolean = false,
    val statusText: String = "",
    val recognizedName: String = "",
    val dosageForm: String = "",
    val specification: String = "",
    val activeIngredientsText: String = "",
    val matchedSymptomsText: String = "",
    val usageSummary: String = "",
    val riskLevel: String = "LOW",
    val riskFlagsText: String = "",
    val advice: String = "",
    val evidenceText: String = "",
    val confidence: Float = 0f,
    val requiresManualReview: Boolean = false
)
