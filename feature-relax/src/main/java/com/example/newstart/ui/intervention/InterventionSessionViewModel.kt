package com.example.newstart.ui.intervention

import android.app.Application
import androidx.annotation.RawRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.newstart.core.common.R
import com.example.newstart.database.AppDatabase
import com.example.newstart.database.entity.HealthMetricsEntity
import com.example.newstart.database.entity.InterventionExecutionEntity
import com.example.newstart.database.entity.InterventionTaskEntity
import com.example.newstart.database.entity.RelaxSessionEntity
import com.example.newstart.intervention.HapticPatternMode
import com.example.newstart.intervention.InterventionExperienceMetadata
import com.example.newstart.intervention.InterventionExperienceModality
import com.example.newstart.intervention.InterventionProtocolCatalog
import com.example.newstart.intervention.InterventionSessionContent
import com.example.newstart.intervention.InterventionSessionContentCatalog
import com.example.newstart.intervention.PersonalizationLevel
import com.example.newstart.intervention.PersonalizationMissingInput
import com.example.newstart.intervention.ProfileTriggerType
import com.example.newstart.intervention.RelaxRealtimeFeedback
import com.example.newstart.network.models.InterventionExecutionUpsertRequest
import com.example.newstart.network.models.InterventionTaskUpsertRequest
import com.example.newstart.repository.InterventionExperienceCodec
import com.example.newstart.repository.InterventionProfileRepository
import com.example.newstart.repository.InterventionRepository
import com.example.newstart.repository.InterventionTaskSourceType
import com.example.newstart.repository.InterventionTaskStatus
import com.example.newstart.repository.NetworkRepository
import com.example.newstart.repository.RelaxRepository
import com.example.newstart.ui.relax.RelaxSignalTracker
import com.example.newstart.util.RelaxVitalSnapshot
import com.example.newstart.util.RelaxationScorer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class InterventionSessionUiState(
    val protocolCode: String = "",
    val title: String = "",
    val subtitle: String = "",
    val rationale: String = "",
    val personalizationLevel: PersonalizationLevel = PersonalizationLevel.PREVIEW,
    val missingInputs: List<PersonalizationMissingInput> = emptyList(),
    val personalizationLabel: String = "",
    val personalizationSummary: String = "",
    val missingInputSummary: String = "",
    val itemTypeLabel: String = "",
    val timingLabel: String = "",
    val modeLabel: String = "",
    val durationLabel: String = "",
    val stepsText: String = "",
    val storyboardText: String = "",
    val completionRule: String = "",
    val showAudioCard: Boolean = false,
    @RawRes val audioResId: Int = 0,
    val audioTitle: String = "",
    val audioSubtitle: String = "",
    val audioSourceText: String = "",
    val methodSourceText: String = "",
    val showBreathingCoachEntry: Boolean = false,
    val breathingCoachProtocolType: String = "",
    val isSoundscapeSession: Boolean = false,
    val adaptiveSoundscapeEnabled: Boolean = false,
    val soundscapeFeedback: RelaxRealtimeFeedback = RelaxRealtimeFeedback(),
    val soundscapeHint: String = "",
    val durationSec: Int = 0,
    val remainingSec: Int = 0,
    val beforeStress: Int = 40,
    val afterStress: Int = 25,
    val isRunning: Boolean = false,
    val hasStarted: Boolean = false,
    val canComplete: Boolean = false,
    val isCompleted: Boolean = false
)

sealed class InterventionSessionEvent {
    data class Toast(val message: String) : InterventionSessionEvent()
    data object Completed : InterventionSessionEvent()
}

class InterventionSessionViewModel(application: Application) : AndroidViewModel(application) {

    private val app = getApplication<Application>()
    private val db = AppDatabase.getDatabase(application)
    private val interventionRepository = InterventionRepository(
        db.interventionTaskDao(),
        db.interventionExecutionDao()
    )
    private val relaxRepository = RelaxRepository(
        db.relaxSessionDao(),
        db.healthMetricsDao()
    )
    private val profileRepository = InterventionProfileRepository(application, db)
    private val networkRepository = NetworkRepository()
    private val signalTracker = RelaxSignalTracker()

    private val _uiState = MutableLiveData(InterventionSessionUiState())
    val uiState: LiveData<InterventionSessionUiState> = _uiState

    private val _event = MutableLiveData<InterventionSessionEvent?>()
    val event: LiveData<InterventionSessionEvent?> = _event

    private var tickerJob: Job? = null
    private var feedbackJob: Job? = null
    private var startedAt: Long? = null
    private var taskId: String? = null
    private var protocolCode: String = ""
    private var durationSec: Int = 0
    private var preSnapshot = RelaxVitalSnapshot(heartRate = 0, hrv = 0, spo2 = 0, motion = 0f)
    private val sessionSignals = mutableListOf<Float>()
    private val sessionHrvs = mutableListOf<Int>()
    private var sessionPeakHr = 0
    private var sessionRealtimeAvailable = false

    fun initialize(
        protocolCode: String?,
        title: String?,
        itemType: String?,
        durationSec: Int,
        rationale: String?
    ) {
        val safeCode = protocolCode.orEmpty().ifBlank { "RECOVERY_WALK_10M" }
        val definition = InterventionProtocolCatalog.find(safeCode)
        val content = InterventionSessionContentCatalog.find(safeCode)
        val normalizedTitle = definition?.displayName
            ?: title?.trim().orEmpty().ifBlank {
                app.getString(R.string.intervention_session_title_fallback)
            }
        val isSoundscapeSession = safeCode == "SOUNDSCAPE_SLEEP_AUDIO_15M"
        this.protocolCode = safeCode
        this.durationSec = durationSec.takeIf { it > 0 } ?: (definition?.defaultDurationSec ?: 300)
        _uiState.value = InterventionSessionUiState(
            protocolCode = safeCode,
            title = normalizedTitle,
            subtitle = definition?.description
                ?: app.getString(R.string.intervention_session_default_desc),
            rationale = rationale.orEmpty(),
            personalizationLabel = app.getString(R.string.intervention_personalization_preview_label),
            personalizationSummary = app.getString(
                R.string.intervention_session_personalization_preview_desc,
                app.getString(R.string.intervention_session_personalization_missing_default)
            ),
            itemTypeLabel = resolveItemTypeLabel(itemType),
            timingLabel = resolveTimingLabel(definition?.defaultTimingSlot),
            modeLabel = resolveModeLabel(definition, content, isSoundscapeSession),
            durationLabel = app.getString(
                R.string.intervention_session_duration_format,
                formatDurationLabel(this.durationSec)
            ),
            stepsText = definition?.steps
                ?.mapIndexed { index, step -> "${index + 1}. $step" }
                ?.joinToString("\n")
                .orEmpty(),
            storyboardText = content?.storyboardSteps
                ?.mapIndexed { index, step -> "${index + 1}. $step" }
                ?.joinToString("\n")
                .orEmpty(),
            completionRule = buildCompletionRule(definition, content, isSoundscapeSession),
            showAudioCard = content != null && !isSoundscapeSession,
            audioResId = content?.audioResId ?: 0,
            audioTitle = content?.audioTitle.orEmpty(),
            audioSubtitle = content?.audioSubtitle.orEmpty(),
            audioSourceText = content?.let {
                app.getString(
                    R.string.intervention_session_audio_source_format,
                    it.audioSourceName,
                    it.audioLicense
                )
            }.orEmpty(),
            methodSourceText = content?.let {
                app.getString(
                    R.string.intervention_session_method_source_format,
                    it.methodSourceName
                )
            }.orEmpty(),
            showBreathingCoachEntry = definition?.supportsBreathingCoach == true,
            breathingCoachProtocolType = toBreathingCoachProtocol(safeCode),
            isSoundscapeSession = isSoundscapeSession,
            soundscapeHint = if (isSoundscapeSession) {
                app.getString(R.string.soundscape_hint_default)
            } else {
                ""
            },
            durationSec = this.durationSec,
            remainingSec = this.durationSec
        )
        observeRealtimeFeedback()
        viewModelScope.launch {
            val profile = profileRepository.getLatestViewData()
            val status = profile.personalizationStatus
            _uiState.value = _uiState.value?.copy(
                personalizationLevel = status.level,
                missingInputs = status.missingInputs,
                personalizationLabel = if (status.isPreview) {
                    app.getString(R.string.intervention_personalization_preview_label)
                } else {
                    app.getString(R.string.intervention_personalization_full_label)
                },
                personalizationSummary = if (status.isPreview) {
                    app.getString(
                        R.string.intervention_session_personalization_preview_desc,
                        profile.missingInputSummary
                    )
                } else {
                    app.getString(R.string.intervention_session_personalization_full_desc)
                },
                missingInputSummary = if (status.isPreview) profile.missingInputSummary else ""
            )
        }
    }

    fun updateBeforeStress(value: Int) {
        _uiState.value = _uiState.value?.copy(beforeStress = value)
    }

    fun updateAfterStress(value: Int) {
        _uiState.value = _uiState.value?.copy(afterStress = value)
    }

    fun updateAdaptiveSoundscapeEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value?.copy(adaptiveSoundscapeEnabled = enabled)
    }

    fun startSession() {
        val state = _uiState.value ?: return
        if (state.isRunning || state.isCompleted) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            startedAt = now
            preSnapshot = relaxRepository.getLatestMetricsOnce().toSnapshot()
            signalTracker.reset()
            sessionSignals.clear()
            sessionHrvs.clear()
            sessionPeakHr = 0
            sessionRealtimeAvailable = false
            val task = InterventionTaskEntity(
                date = now,
                sourceType = InterventionTaskSourceType.AI_COACH.name,
                triggerReason = app.getString(R.string.intervention_session_trigger_reason),
                bodyZone = "PROFILE",
                protocolType = protocolCode,
                durationSec = durationSec,
                plannedAt = now,
                status = InterventionTaskStatus.RUNNING.name
            )
            taskId = task.id
            interventionRepository.upsertTask(task)
            _uiState.value = state.copy(isRunning = true, hasStarted = true, canComplete = false)
            startTicker()
        }
    }

    fun completeSession(metadata: InterventionExperienceMetadata? = null) {
        val state = _uiState.value ?: return
        val sessionStartedAt = startedAt
        val currentTaskId = taskId
        if (sessionStartedAt == null || currentTaskId == null) {
            _event.value = InterventionSessionEvent.Toast(
                app.getString(R.string.intervention_session_start_first)
            )
            return
        }
        viewModelScope.launch {
            tickerJob?.cancel()
            val endedAt = System.currentTimeMillis()
            val postSnapshot = relaxRepository.getLatestMetricsOnce().toSnapshot()
            val effect = RelaxationScorer.calculateEffect(preSnapshot, postSnapshot)
            val elapsedSec = ((endedAt - sessionStartedAt) / 1000L).toInt().coerceAtLeast(1)
            val completionType = if (elapsedSec >= (durationSec * 0.85f).roundToInt()) "FULL" else "PARTIAL"
            val defaultMetadata = buildDefaultMetadata(state, elapsedSec)
            val resolvedMetadata = metadata?.mergeWith(defaultMetadata) ?: defaultMetadata
            val metadataJson = InterventionExperienceCodec.toJson(resolvedMetadata)
            interventionRepository.updateTaskStatus(currentTaskId, InterventionTaskStatus.COMPLETED)
            val execution = InterventionExecutionEntity(
                taskId = currentTaskId,
                startedAt = sessionStartedAt,
                endedAt = endedAt,
                elapsedSec = elapsedSec,
                beforeStress = state.beforeStress.toFloat(),
                afterStress = state.afterStress.toFloat(),
                beforeHr = preSnapshot.heartRate,
                afterHr = postSnapshot.heartRate,
                effectScore = effect.effectScore,
                completionType = completionType,
                metadataJson = metadataJson
            )
            interventionRepository.insertExecution(execution)
            relaxRepository.saveSession(
                RelaxSessionEntity(
                    startTime = sessionStartedAt,
                    endTime = endedAt,
                    protocolType = protocolCode,
                    durationSec = elapsedSec,
                    preStress = effect.preStress,
                    postStress = effect.postStress,
                    preHr = preSnapshot.heartRate,
                    postHr = postSnapshot.heartRate,
                    preHrv = preSnapshot.hrv,
                    postHrv = postSnapshot.hrv,
                    preMotion = preSnapshot.motion,
                    postMotion = postSnapshot.motion,
                    effectScore = effect.effectScore,
                    metadataJson = metadataJson
                )
            )
            syncExecutionToCloud(currentTaskId, execution)
            profileRepository.refreshSnapshot(ProfileTriggerType.EXECUTION_FEEDBACK)
            _uiState.value = state.copy(
                isRunning = false,
                canComplete = false,
                isCompleted = true,
                remainingSec = 0
            )
            _event.value = InterventionSessionEvent.Toast(
                app.getString(R.string.intervention_session_saved)
            )
            _event.value = InterventionSessionEvent.Completed
        }
    }

    fun consumeEvent() {
        _event.value = null
    }

    override fun onCleared() {
        tickerJob?.cancel()
        feedbackJob?.cancel()
        super.onCleared()
    }

    private fun observeRealtimeFeedback() {
        if (feedbackJob != null) return
        feedbackJob = viewModelScope.launch {
            relaxRepository.getLatestMetricsFlow().collect { metrics ->
                val feedback = signalTracker.onSample(metrics)
                if (_uiState.value?.isRunning == true) {
                    appendSessionFeedback(feedback)
                }
                _uiState.postValue(
                    (_uiState.value ?: InterventionSessionUiState()).copy(
                        soundscapeFeedback = feedback,
                        soundscapeHint = buildSoundscapeHint(feedback)
                    )
                )
            }
        }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (true) {
                delay(1000L)
                val current = _uiState.value ?: break
                val next = (current.remainingSec - 1).coerceAtLeast(0)
                val finished = next == 0
                _uiState.value = current.copy(
                    remainingSec = next,
                    isRunning = !finished,
                    canComplete = finished || current.hasStarted
                )
                if (finished) {
                    _event.value = InterventionSessionEvent.Toast(
                        app.getString(R.string.intervention_session_finish_prompt)
                    )
                    break
                }
            }
        }
    }

    private fun appendSessionFeedback(feedback: RelaxRealtimeFeedback) {
        if (!feedback.hasRealtimeData) return
        sessionRealtimeAvailable = true
        sessionSignals += feedback.relaxSignal
        if (feedback.hrv > 0) {
            sessionHrvs += feedback.hrv
        }
        sessionPeakHr = maxOf(sessionPeakHr, feedback.heartRate)
    }

    private fun buildSoundscapeHint(feedback: RelaxRealtimeFeedback): String {
        return when {
            !feedback.hasRealtimeData -> app.getString(R.string.soundscape_hint_default)
            feedback.motion > 1.0f -> app.getString(R.string.soundscape_hint_high_motion)
            feedback.relaxSignal >= 0.72f -> app.getString(R.string.soundscape_hint_calm)
            else -> app.getString(R.string.soundscape_hint_default)
        }
    }

    private fun buildDefaultMetadata(
        state: InterventionSessionUiState,
        elapsedSec: Int
    ): InterventionExperienceMetadata {
        val durationRatio = (elapsedSec.toFloat() / durationSec.toFloat()).coerceIn(0f, 1f)
        val completionQuality = (
            durationRatio * 72f +
                (sessionSignals.takeIf { it.isNotEmpty() }?.average()?.times(18f)?.toFloat() ?: 0f)
            ).roundToInt().coerceIn(0, 100)

        val modality = when {
            state.isSoundscapeSession -> InterventionExperienceModality.SOUNDSCAPE
            state.showAudioCard -> InterventionExperienceModality.AUDIO
            protocolCode.startsWith("TASK_") -> InterventionExperienceModality.TASK
            else -> InterventionExperienceModality.GUIDED
        }
        return InterventionExperienceMetadata(
            modality = modality,
            sessionVariant = protocolCode,
            hapticEnabled = false,
            preferredHapticMode = HapticPatternMode.BREATH,
            realtimeSignalAvailable = sessionRealtimeAvailable,
            avgRelaxSignal = sessionSignals.takeIf { it.isNotEmpty() }?.average()?.toFloat(),
            peakHeartRate = sessionPeakHr.takeIf { it > 0 },
            averageHrv = sessionHrvs.takeIf { it.isNotEmpty() }?.average()?.toInt(),
            completionQuality = completionQuality
        )
    }

    private suspend fun syncExecutionToCloud(taskId: String, execution: InterventionExecutionEntity) {
        if (networkRepository.getCurrentSession() == null) return
        val task = interventionRepository.getTask(taskId) ?: return
        networkRepository.upsertInterventionTask(
            InterventionTaskUpsertRequest(
                taskId = task.id,
                date = task.date,
                sourceType = task.sourceType,
                triggerReason = task.triggerReason,
                bodyZone = task.bodyZone,
                protocolType = task.protocolType,
                durationSec = task.durationSec,
                plannedAt = task.plannedAt,
                status = InterventionTaskStatus.COMPLETED.name
            )
        )
        networkRepository.upsertInterventionExecution(
            InterventionExecutionUpsertRequest(
                executionId = execution.id,
                taskId = execution.taskId,
                startedAt = execution.startedAt,
                endedAt = execution.endedAt,
                elapsedSec = execution.elapsedSec,
                beforeStress = execution.beforeStress,
                afterStress = execution.afterStress,
                beforeHr = execution.beforeHr,
                afterHr = execution.afterHr,
                effectScore = execution.effectScore,
                completionType = execution.completionType,
                metadataJson = execution.metadataJson
            )
        )
    }

    private fun resolveItemTypeLabel(rawItemType: String?): String {
        return when (rawItemType.orEmpty().uppercase()) {
            "PRIMARY" -> app.getString(R.string.intervention_session_type_primary)
            "SECONDARY" -> app.getString(R.string.intervention_session_type_secondary)
            "LIFESTYLE" -> app.getString(R.string.intervention_session_type_lifestyle)
            else -> app.getString(R.string.intervention_session_type_generic)
        }
    }

    private fun resolveTimingLabel(
        timingSlot: com.example.newstart.intervention.PrescriptionTimingSlot?
    ): String {
        val label = when (timingSlot) {
            com.example.newstart.intervention.PrescriptionTimingSlot.MORNING ->
                app.getString(R.string.intervention_session_timing_morning)
            com.example.newstart.intervention.PrescriptionTimingSlot.AFTERNOON ->
                app.getString(R.string.intervention_session_timing_afternoon)
            com.example.newstart.intervention.PrescriptionTimingSlot.EVENING ->
                app.getString(R.string.intervention_session_timing_evening)
            com.example.newstart.intervention.PrescriptionTimingSlot.BEFORE_SLEEP ->
                app.getString(R.string.intervention_session_timing_before_sleep)
            com.example.newstart.intervention.PrescriptionTimingSlot.FLEXIBLE, null ->
                app.getString(R.string.intervention_session_timing_flexible)
        }
        return app.getString(R.string.intervention_session_timing_format, label)
    }

    private fun resolveModeLabel(
        definition: com.example.newstart.intervention.InterventionProtocolDefinition?,
        content: InterventionSessionContent?,
        isSoundscapeSession: Boolean
    ): String {
        val mode = when {
            isSoundscapeSession ->
                app.getString(R.string.soundscape_title)
            definition?.supportsBreathingCoach == true ->
                app.getString(R.string.intervention_session_mode_breathing)
            content != null ->
                app.getString(R.string.intervention_session_mode_story_audio)
            definition?.assetRef?.startsWith("audio://") == true ->
                app.getString(R.string.intervention_session_mode_audio)
            definition?.assetRef?.startsWith("task://") == true ->
                app.getString(R.string.intervention_session_mode_task)
            else ->
                app.getString(R.string.intervention_session_mode_guided)
        }
        return app.getString(R.string.intervention_session_mode_format, mode)
    }

    private fun buildCompletionRule(
        definition: com.example.newstart.intervention.InterventionProtocolDefinition?,
        content: InterventionSessionContent?,
        isSoundscapeSession: Boolean
    ): String {
        val baseRule = when {
            isSoundscapeSession ->
                "至少保持当前音景一轮完整体验，并在结束前确认主观压力变化。"
            content != null ->
                app.getString(R.string.intervention_session_rule_story_audio)
            definition?.assetRef?.startsWith("task://") == true ->
                app.getString(R.string.intervention_session_rule_task)
            definition?.assetRef?.startsWith("audio://") == true ->
                app.getString(R.string.intervention_session_rule_audio)
            definition?.supportsBreathingCoach == true ->
                app.getString(R.string.intervention_session_rule_breathing)
            else ->
                app.getString(R.string.intervention_session_rule_guided)
        }
        return app.getString(R.string.intervention_session_completion_format, baseRule)
    }

    private fun formatDurationLabel(totalSec: Int): String {
        val minutes = totalSec / 60
        return if (minutes > 0) {
            app.getString(R.string.intervention_session_duration_minutes, minutes)
        } else {
            app.getString(R.string.intervention_session_duration_seconds, totalSec)
        }
    }

    private fun toBreathingCoachProtocol(protocolCode: String): String {
        return when (protocolCode) {
            "BREATH_BOX" -> "BOX"
            else -> protocolCode
        }
    }

    private fun HealthMetricsEntity?.toSnapshot(): RelaxVitalSnapshot {
        if (this == null) {
            return RelaxVitalSnapshot(heartRate = 0, hrv = 0, spo2 = 0, motion = 0f)
        }
        return RelaxVitalSnapshot(
            heartRate = heartRateSample,
            hrv = hrvCurrent,
            spo2 = bloodOxygenSample,
            motion = accMagnitudeSample
        )
    }

    private fun InterventionExperienceMetadata.mergeWith(
        fallback: InterventionExperienceMetadata
    ): InterventionExperienceMetadata {
        return copy(
            sessionVariant = sessionVariant.ifBlank { fallback.sessionVariant },
            hapticEnabled = hapticEnabled || fallback.hapticEnabled,
            preferredHapticMode = preferredHapticMode ?: fallback.preferredHapticMode,
            realtimeSignalAvailable = realtimeSignalAvailable || fallback.realtimeSignalAvailable,
            avgRelaxSignal = avgRelaxSignal ?: fallback.avgRelaxSignal,
            peakHeartRate = peakHeartRate ?: fallback.peakHeartRate,
            averageHrv = averageHrv ?: fallback.averageHrv,
            soundscapeMix = if (soundscapeMix.isEmpty()) fallback.soundscapeMix else soundscapeMix,
            manualAdjustCount = maxOf(manualAdjustCount, fallback.manualAdjustCount),
            completionQuality = if (completionQuality > 0) completionQuality else fallback.completionQuality,
            fallbackMode = fallbackMode ?: fallback.fallbackMode,
            interactionTouches = maxOf(interactionTouches, fallback.interactionTouches)
        )
    }
}
