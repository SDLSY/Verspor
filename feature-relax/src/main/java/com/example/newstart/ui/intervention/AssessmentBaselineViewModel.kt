package com.example.newstart.ui.intervention

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.newstart.core.common.R
import com.example.newstart.intervention.AssessmentCatalog
import com.example.newstart.intervention.AssessmentOptionDefinition
import com.example.newstart.intervention.AssessmentScaleDefinition
import com.example.newstart.intervention.AssessmentSource
import com.example.newstart.intervention.ProfileTriggerType
import com.example.newstart.repository.AssessmentRepository
import com.example.newstart.repository.NetworkRepository
import com.example.newstart.repository.PrescriptionRepository
import com.example.newstart.network.models.AssessmentBaselineSummaryUpsertRequest
import kotlinx.coroutines.launch

data class AssessmentBaselineUiState(
    val isLoading: Boolean = true,
    val progressText: String = "",
    val scaleTitle: String = "",
    val scaleDescription: String = "",
    val questionProgressText: String = "",
    val questionPrompt: String = "",
    val options: List<AssessmentOptionDefinition> = emptyList(),
    val selectedValue: Int? = null,
    val canGoBack: Boolean = false,
    val isLastStep: Boolean = false
)

sealed class AssessmentBaselineEvent {
    data class Toast(val message: String) : AssessmentBaselineEvent()
    data object Completed : AssessmentBaselineEvent()
}

class AssessmentBaselineViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "AssessmentBaselineVM"
    }

    private val assessmentRepository = AssessmentRepository(application)
    private val prescriptionRepository = PrescriptionRepository(application)
    private val networkRepository = NetworkRepository()
    private val app = getApplication<Application>()

    private val _uiState = MutableLiveData(AssessmentBaselineUiState())
    val uiState: LiveData<AssessmentBaselineUiState> = _uiState

    private val _event = MutableLiveData<AssessmentBaselineEvent?>()
    val event: LiveData<AssessmentBaselineEvent?> = _event

    private var scales: List<AssessmentScaleDefinition> = emptyList()
    private val answersByScale = linkedMapOf<String, MutableMap<String, Int>>()
    private var currentScaleIndex = 0
    private var currentQuestionIndex = 0

    init {
        load()
    }

    fun selectAnswer(value: Int) {
        val scale = scales.getOrNull(currentScaleIndex) ?: return
        val question = scale.questions.getOrNull(currentQuestionIndex) ?: return
        val answers = answersByScale.getOrPut(scale.code) { linkedMapOf() }
        answers[question.code] = value
        publish()
    }

    fun previous() {
        if (scales.isEmpty()) return
        when {
            currentQuestionIndex > 0 -> currentQuestionIndex -= 1
            currentScaleIndex > 0 -> {
                currentScaleIndex -= 1
                currentQuestionIndex = scales[currentScaleIndex].questions.lastIndex
            }
            else -> return
        }
        publish()
    }

    fun next() {
        val scale = scales.getOrNull(currentScaleIndex) ?: return
        val question = scale.questions.getOrNull(currentQuestionIndex) ?: return
        val answers = answersByScale.getOrPut(scale.code) { linkedMapOf() }
        if (!answers.containsKey(question.code)) {
            _event.value = AssessmentBaselineEvent.Toast(
                app.getString(R.string.assessment_baseline_missing)
            )
            return
        }
        if (currentQuestionIndex < scale.questions.lastIndex) {
            currentQuestionIndex += 1
            publish()
            return
        }
        if (currentScaleIndex < scales.lastIndex) {
            currentScaleIndex += 1
            currentQuestionIndex = 0
            publish()
            return
        }
        finishAllScales()
    }

    fun consumeEvent() {
        _event.value = null
    }

    private fun load() {
        viewModelScope.launch {
            scales = assessmentRepository.getBaselineDefinitions()
            scales.forEach { definition ->
                answersByScale.putIfAbsent(definition.code, linkedMapOf())
            }
            publish()
        }
    }

    private fun finishAllScales() {
        viewModelScope.launch {
            _uiState.value = _uiState.value?.copy(isLoading = true)
            scales.forEach { scale ->
                val answers = answersByScale[scale.code].orEmpty()
                assessmentRepository.saveCompletedScale(
                    scaleCode = scale.code,
                    answers = answers,
                    source = AssessmentSource.BASELINE
                )
            }
            syncBaselineSummaryIfPossible()
            prescriptionRepository.generateForTrigger(ProfileTriggerType.SCALE_COMPLETION)
            _event.value = AssessmentBaselineEvent.Toast(
                app.getString(R.string.assessment_baseline_done)
            )
            _event.value = AssessmentBaselineEvent.Completed
        }
    }

    private fun publish() {
        val scale = scales.getOrNull(currentScaleIndex)
        val question = scale?.questions?.getOrNull(currentQuestionIndex)
        if (scale == null || question == null) {
            _uiState.value = AssessmentBaselineUiState(isLoading = true)
            return
        }
        val selectedValue = answersByScale[scale.code]?.get(question.code)
        _uiState.value = AssessmentBaselineUiState(
            isLoading = false,
            progressText = app.getString(
                R.string.assessment_baseline_progress,
                currentScaleIndex + 1,
                scales.size
            ),
            scaleTitle = scale.title,
            scaleDescription = scale.description,
            questionProgressText = "棰樼洰 ${currentQuestionIndex + 1} / ${scale.questions.size}",
            questionPrompt = question.prompt,
            options = question.options,
            selectedValue = selectedValue,
            canGoBack = currentScaleIndex > 0 || currentQuestionIndex > 0,
            isLastStep = currentScaleIndex == scales.lastIndex &&
                currentQuestionIndex == scale.questions.lastIndex
        )
    }

    private suspend fun syncBaselineSummaryIfPossible() {
        val session = networkRepository.getCurrentSession() ?: return
        val baselineCodes = AssessmentCatalog.baselineScaleCodes
        val completedSessions = baselineCodes.mapNotNull { code ->
            assessmentRepository.getLatestScaleSession(code)
        }
        if (completedSessions.isEmpty()) {
            return
        }
        val completedCount = completedSessions.size
        val completedAt = completedSessions.mapNotNull { it.completedAt }.maxOrNull()
            ?: completedSessions.maxOfOrNull { it.startedAt }
            ?: System.currentTimeMillis()
        val freshnessUntil = completedSessions.minOfOrNull { it.freshnessUntil }
            ?: System.currentTimeMillis()
        val request = AssessmentBaselineSummaryUpsertRequest(
            completedScaleCodes = completedSessions.map { it.scaleCode }.distinct(),
            completedCount = completedCount,
            completedAt = completedAt,
            freshnessUntil = freshnessUntil
        )
        networkRepository.upsertAssessmentBaselineSummary(request).onFailure {
            Log.w(TAG, "syncBaselineSummaryIfPossible failed for ${session.userId}: ${it.message}")
        }
    }
}

