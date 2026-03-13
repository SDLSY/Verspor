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
import com.example.newstart.intervention.PrescriptionItemDetails
import com.example.newstart.intervention.PrescriptionItemType
import com.example.newstart.intervention.PersonalizationLevel
import com.example.newstart.intervention.PersonalizationMissingInput
import com.example.newstart.intervention.ProfileTriggerType
import com.example.newstart.repository.InterventionProfileRepository
import com.example.newstart.repository.PrescriptionRepository
import kotlinx.coroutines.launch

data class InterventionDashboardUiState(
    val isLoading: Boolean = true,
    val baselineCompleted: Boolean = false,
    val isPreview: Boolean = true,
    val personalizationLevel: PersonalizationLevel = PersonalizationLevel.PREVIEW,
    val missingInputs: List<PersonalizationMissingInput> = emptyList(),
    val bundleId: String? = null,
    val todayTitle: String = "",
    val rationale: String = "",
    val goalLine: String = "",
    val riskLine: String = "",
    val evidenceLine: String = "",
    val quickActions: List<InterventionActionUiModel> = emptyList(),
    val baselineSummary: String = "",
    val missingInputSummary: String = "",
    val canGenerate: Boolean = false
)

class InterventionDashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val app = getApplication<Application>()
    private val profileRepository = InterventionProfileRepository(application)
    private val prescriptionRepository = PrescriptionRepository(application)

    private val _uiState = MutableLiveData(InterventionDashboardUiState())
    val uiState: LiveData<InterventionDashboardUiState> = _uiState

    private val _toastEvent = MutableLiveData<String?>()
    val toastEvent: LiveData<String?> = _toastEvent

    init {
        refreshDashboard()
    }

    fun refreshDashboard(forceGenerate: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = _uiState.value?.copy(isLoading = true)
            val profile = profileRepository.getLatestViewData()
            var bundle = prescriptionRepository.getLatestActiveBundle()
            if (bundle == null || forceGenerate) {
                bundle = prescriptionRepository.generateForTrigger(ProfileTriggerType.DAILY_REFRESH) ?: bundle
            }
            _uiState.value = buildState(profile, bundle)
        }
    }

    fun generateTodayPrescription() {
        viewModelScope.launch {
            val profile = profileRepository.getLatestViewData()
            _uiState.value = _uiState.value?.copy(isLoading = true)
            val bundle = prescriptionRepository.generateForTrigger(ProfileTriggerType.DAILY_REFRESH)
            val latestProfile = profileRepository.getLatestViewData()
            _uiState.value = buildState(latestProfile, bundle)
            _toastEvent.value = if (bundle != null) {
                if (profile.personalizationStatus.isPreview) {
                    app.getString(R.string.intervention_dashboard_generated_preview)
                } else {
                    app.getString(R.string.intervention_dashboard_generated)
                }
            } else {
                app.getString(R.string.intervention_dashboard_generate_failed)
            }
        }
    }

    fun consumeToast() {
        _toastEvent.value = null
    }

    private fun buildState(
        profile: InterventionProfileViewData,
        bundle: PrescriptionBundleDetails?
    ): InterventionDashboardUiState {
        val readinessSummary = if (profile.personalizationStatus.isPreview) {
            app.getString(R.string.intervention_dashboard_preview_ready)
        } else {
            app.getString(R.string.intervention_dashboard_full_ready)
        }
        if (bundle == null) {
            return InterventionDashboardUiState(
                isLoading = false,
                baselineCompleted = profile.baselineCompleted,
                isPreview = profile.personalizationStatus.isPreview,
                personalizationLevel = profile.personalizationStatus.level,
                missingInputs = profile.personalizationStatus.missingInputs,
                todayTitle = if (profile.personalizationStatus.isPreview) {
                    app.getString(R.string.intervention_dashboard_title_preview)
                } else {
                    app.getString(R.string.intervention_dashboard_title_ready)
                },
                rationale = if (profile.personalizationStatus.isPreview) {
                    app.getString(
                        R.string.intervention_dashboard_reason_preview,
                        profile.missingInputSummary
                    )
                } else {
                    app.getString(R.string.intervention_dashboard_reason_ready)
                },
                quickActions = emptyList(),
                baselineSummary = readinessSummary,
                missingInputSummary = profile.missingInputSummary,
                canGenerate = true
            )
        }

        val quickActions = bundle.items
            .sortedBy { it.sequenceOrder }
            .take(3)
            .map { item -> item.toAction() }

        return InterventionDashboardUiState(
            isLoading = false,
            baselineCompleted = profile.baselineCompleted,
            isPreview = profile.personalizationStatus.isPreview,
            personalizationLevel = profile.personalizationStatus.level,
            missingInputs = profile.personalizationStatus.missingInputs,
            bundleId = bundle.id,
            todayTitle = if (profile.personalizationStatus.isPreview) {
                app.getString(R.string.intervention_dashboard_title_preview_with_goal, bundle.primaryGoal)
            } else {
                bundle.primaryGoal
            },
            rationale = if (profile.personalizationStatus.isPreview) {
                app.getString(
                    R.string.intervention_dashboard_bundle_preview_reason,
                    profile.missingInputSummary,
                    bundle.rationale
                )
            } else {
                bundle.rationale
            },
            goalLine = app.getString(R.string.intervention_dashboard_goal_prefix, bundle.primaryGoal),
            riskLine = app.getString(R.string.intervention_dashboard_risk_prefix, bundle.riskLevel),
            evidenceLine = app.getString(
                R.string.intervention_dashboard_evidence_prefix,
                bundle.evidence.take(3).joinToString("；").ifBlank {
                    app.getString(R.string.intervention_dashboard_none)
                }
            ),
            quickActions = quickActions,
            baselineSummary = readinessSummary,
            missingInputSummary = profile.missingInputSummary,
            canGenerate = true
        )
    }

    private fun PrescriptionItemDetails.toAction(): InterventionActionUiModel {
        val definition = InterventionProtocolCatalog.find(protocolCode)
        val prefix = when (itemType) {
            PrescriptionItemType.PRIMARY -> app.getString(R.string.intervention_dashboard_action_primary)
            PrescriptionItemType.SECONDARY -> app.getString(R.string.intervention_dashboard_action_secondary)
            PrescriptionItemType.LIFESTYLE -> app.getString(R.string.intervention_dashboard_action_lifestyle)
        }
        val displayName = definition?.displayName ?: protocolCode
        return InterventionActionUiModel(
            title = app.getString(R.string.intervention_dashboard_action_title, prefix, displayName),
            subtitle = definition?.description
                ?: app.getString(R.string.intervention_dashboard_action_subtitle),
            protocolCode = protocolCode,
            durationSec = durationSec,
            assetRef = assetRef,
            itemType = itemType
        )
    }
}

