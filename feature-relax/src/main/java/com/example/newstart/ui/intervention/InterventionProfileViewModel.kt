package com.example.newstart.ui.intervention

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.newstart.core.common.R
import com.example.newstart.intervention.InterventionProfileViewData
import com.example.newstart.intervention.InterventionProtocolCatalog
import com.example.newstart.intervention.PrescriptionBundleDetails
import com.example.newstart.intervention.PrescriptionItemType
import com.example.newstart.intervention.ProfileTriggerType
import com.example.newstart.repository.InterventionProfileRepository
import com.example.newstart.repository.PrescriptionRepository
import kotlinx.coroutines.launch

data class InterventionProfileUiState(
    val isLoading: Boolean = true,
    val baselineCompleted: Boolean = false,
    val isPreview: Boolean = true,
    val baselineText: String = "",
    val missingInputText: String = "",
    val scoreLines: List<String> = emptyList(),
    val scaleLines: List<String> = emptyList(),
    val doctorSummary: String = "",
    val medicalSummary: String = "",
    val adherenceHint: String = "",
    val evidenceLines: List<String> = emptyList(),
    val bundleTitle: String = "",
    val bundleSummary: String = "",
    val primaryAction: InterventionActionUiModel? = null,
    val canGenerate: Boolean = false,
    val missingInputs: List<String> = emptyList()
)

class InterventionProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val app = getApplication<Application>()
    private val profileRepository = InterventionProfileRepository(application)
    private val prescriptionRepository = PrescriptionRepository(application)

    private val _uiState = MutableLiveData(InterventionProfileUiState())
    val uiState: LiveData<InterventionProfileUiState> = _uiState

    private val _toastEvent = MutableLiveData<String?>()
    val toastEvent: LiveData<String?> = _toastEvent

    init {
        refreshProfile()
    }

    fun refreshProfile() {
        viewModelScope.launch {
            _uiState.value = _uiState.value?.copy(isLoading = true)
            val viewData = profileRepository.getLatestViewData()
            val bundle = prescriptionRepository.getLatestActiveBundle()
            _uiState.value = buildState(viewData, bundle)
        }
    }

    fun generateTodayBundle() {
        viewModelScope.launch {
            val viewData = profileRepository.getLatestViewData()
            _uiState.value = _uiState.value?.copy(isLoading = true)
            val bundle = prescriptionRepository.generateForTrigger(ProfileTriggerType.DAILY_REFRESH)
            _uiState.value = buildState(profileRepository.getLatestViewData(), bundle)
            _toastEvent.value = if (bundle != null) {
                if (viewData.personalizationStatus.isPreview) {
                    app.getString(R.string.intervention_profile_generate_preview)
                } else {
                    app.getString(R.string.intervention_profile_generate_done)
                }
            } else {
                app.getString(R.string.intervention_profile_generate_failed)
            }
        }
    }

    fun consumeToast() {
        _toastEvent.value = null
    }

    private fun buildState(
        viewData: InterventionProfileViewData,
        bundle: PrescriptionBundleDetails?
    ): InterventionProfileUiState {
        val snapshot = viewData.snapshot
        val scoreLines = snapshot?.domainScores?.map { (key, value) ->
            app.getString(R.string.intervention_profile_score_prefix, domainDisplayName(key), value)
        }.orEmpty()
        val scaleLines = viewData.scaleResults.map {
            "${it.scaleTitle}：${it.totalScore} 分，${it.severityLabel}"
        }
        val evidenceLines = snapshot?.evidenceFacts
            ?.flatMap { (key, values) -> values.map { "${domainDisplayName(key)} · $it" } }
            ?.distinct()
            .orEmpty()

        val primaryAction = bundle?.items
            ?.sortedBy { it.sequenceOrder }
            ?.firstOrNull()
            ?.let { item ->
                val definition = InterventionProtocolCatalog.find(item.protocolCode)
                val prefix = when (item.itemType) {
                    PrescriptionItemType.PRIMARY -> app.getString(R.string.intervention_dashboard_action_primary)
                    PrescriptionItemType.SECONDARY -> app.getString(R.string.intervention_dashboard_action_secondary)
                    PrescriptionItemType.LIFESTYLE -> app.getString(R.string.intervention_dashboard_action_lifestyle)
                }
                InterventionActionUiModel(
                    title = app.getString(
                        R.string.intervention_dashboard_action_title,
                        prefix,
                        definition?.displayName ?: item.protocolCode
                    ),
                    subtitle = definition?.description
                        ?: app.getString(R.string.intervention_profile_primary_subtitle),
                    protocolCode = item.protocolCode,
                    durationSec = item.durationSec,
                    assetRef = item.assetRef,
                    itemType = item.itemType
                )
            }

        return InterventionProfileUiState(
            isLoading = false,
            baselineCompleted = viewData.baselineCompleted,
            isPreview = viewData.personalizationStatus.isPreview,
            baselineText = if (viewData.personalizationStatus.isPreview) {
                app.getString(R.string.intervention_profile_preview_title)
            } else {
                app.getString(R.string.intervention_profile_full_title)
            },
            missingInputText = viewData.missingInputSummary,
            scoreLines = if (scoreLines.isEmpty()) {
                listOf(app.getString(R.string.intervention_profile_scores_empty))
            } else {
                scoreLines
            },
            scaleLines = if (scaleLines.isEmpty()) {
                listOf(app.getString(R.string.intervention_profile_scales_empty))
            } else {
                scaleLines
            },
            doctorSummary = viewData.latestDoctorSummary,
            medicalSummary = viewData.latestMedicalSummary,
            adherenceHint = viewData.adherenceHint,
            evidenceLines = if (evidenceLines.isEmpty()) {
                listOf(app.getString(R.string.intervention_profile_evidence_empty))
            } else {
                evidenceLines
            },
            bundleTitle = bundle?.primaryGoal ?: if (viewData.personalizationStatus.isPreview) {
                app.getString(R.string.intervention_profile_bundle_preview)
            } else {
                app.getString(R.string.intervention_profile_bundle_empty)
            },
            bundleSummary = bundle?.rationale ?: if (viewData.personalizationStatus.isPreview) {
                app.getString(R.string.intervention_profile_bundle_intro_preview)
            } else {
                app.getString(R.string.intervention_profile_bundle_intro)
            },
            primaryAction = primaryAction,
            canGenerate = true,
            missingInputs = viewData.personalizationStatus.missingInputs.map { it.name }
        )
    }

    private fun domainDisplayName(key: String): String {
        return when (key) {
            "sleepDisturbance" -> app.getString(R.string.intervention_profile_domain_sleep)
            "stressLoad" -> app.getString(R.string.intervention_profile_domain_stress)
            "fatigueLoad" -> app.getString(R.string.intervention_profile_domain_fatigue)
            "recoveryCapacity" -> app.getString(R.string.intervention_profile_domain_recovery)
            "anxietyRisk" -> app.getString(R.string.intervention_profile_domain_anxiety)
            "depressiveRisk" -> app.getString(R.string.intervention_profile_domain_depressive)
            "adherenceReadiness" -> app.getString(R.string.intervention_profile_domain_adherence)
            else -> key
        }
    }
}

