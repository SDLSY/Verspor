package com.example.newstart.ui.relax

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.newstart.core.common.R
import com.example.newstart.database.AppDatabase
import com.example.newstart.database.entity.HealthMetricsEntity
import com.example.newstart.database.entity.InterventionExecutionEntity
import com.example.newstart.database.entity.RelaxSessionEntity
import com.example.newstart.intervention.HapticPatternMode
import com.example.newstart.intervention.InterventionExperienceMetadata
import com.example.newstart.intervention.InterventionExperienceModality
import com.example.newstart.intervention.RelaxRealtimeFeedback
import com.example.newstart.network.models.InterventionExecutionUpsertRequest
import com.example.newstart.network.models.InterventionTaskUpsertRequest
import com.example.newstart.repository.InterventionExperienceCodec
import com.example.newstart.repository.InterventionRepository
import com.example.newstart.repository.InterventionTaskStatus
import com.example.newstart.repository.NetworkRepository
import com.example.newstart.repository.RelaxRepository
import com.example.newstart.util.RelaxVitalSnapshot
import com.example.newstart.util.RelaxationScorer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class BreathingCoachViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val MIN_SAVED_DURATION_SEC = 30
    }

    private val app = application
    private val db = AppDatabase.getDatabase(application)
    private val repository = RelaxRepository(
        relaxSessionDao = db.relaxSessionDao(),
        healthMetricsDao = db.healthMetricsDao()
    )
    private val interventionRepository = InterventionRepository(
        taskDao = db.interventionTaskDao(),
        executionDao = db.interventionExecutionDao()
    )
    private val networkRepository = NetworkRepository()
    private val signalTracker = RelaxSignalTracker()

    private val _uiState = MutableLiveData(BreathingCoachUiState())
    val uiState: LiveData<BreathingCoachUiState> = _uiState

    private var initialized = false
    private var tickerJob: Job? = null
    private var feedbackJob: Job? = null
    private var selectedProtocol = BreathingProtocol.BREATH_4_6
    private var selectedDurationSec = 60
    private var elapsedSec = 0
    private var sessionStartTime = 0L
    private var preSnapshot = RelaxVitalSnapshot(heartRate = 0, hrv = 0, spo2 = 0, motion = 0f)
    private var boundTaskId: String? = null
    private val sessionSignals = mutableListOf<Float>()
    private val sessionHrvs = mutableListOf<Int>()
    private var sessionPeakHr = 0
    private var sessionRealtimeAvailable = false
    private var sessionFallbackMode: String? = null
    private var sessionHapticsEnabled = false
    private var sessionHapticMode = HapticPatternMode.BREATH

    fun initialize(protocolTypeArg: String?, durationSecArg: Int?) {
        if (initialized) return
        initialized = true

        selectedProtocol = BreathingProtocol.fromWireValue(protocolTypeArg)
        selectedDurationSec = normalizeDuration(durationSecArg ?: 60)
        boundTaskId = null

        _uiState.value = _uiState.value?.copy(
            selectedProtocol = selectedProtocol,
            selectedDurationSec = selectedDurationSec,
            totalRemainingSec = selectedDurationSec
        )
        observeRealtimeFeedback()
    }

    fun initialize(protocolTypeArg: String?, durationSecArg: Int?, taskIdArg: String?) {
        initialize(protocolTypeArg, durationSecArg)
        boundTaskId = taskIdArg?.takeIf { it.isNotBlank() }
    }

    fun selectProtocol(protocol: BreathingProtocol) {
        if (_uiState.value?.isRunning == true) return
        selectedProtocol = protocol
        _uiState.value = _uiState.value?.copy(selectedProtocol = protocol)
    }

    fun selectDuration(durationSec: Int) {
        if (_uiState.value?.isRunning == true) return
        selectedDurationSec = normalizeDuration(durationSec)
        _uiState.value = _uiState.value?.copy(
            selectedDurationSec = selectedDurationSec,
            totalRemainingSec = selectedDurationSec
        )
    }

    fun startSession(
        hapticsEnabled: Boolean = false,
        hapticMode: HapticPatternMode = HapticPatternMode.BREATH
    ) {
        if (_uiState.value?.isRunning == true) return

        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            preSnapshot = repository.getLatestMetricsOnce().toSnapshot()
            sessionStartTime = System.currentTimeMillis()
            elapsedSec = 0
            sessionSignals.clear()
            sessionHrvs.clear()
            sessionPeakHr = 0
            sessionRealtimeAvailable = false
            sessionFallbackMode = null
            sessionHapticsEnabled = hapticsEnabled
            sessionHapticMode = hapticMode
            boundTaskId?.let { taskId ->
                interventionRepository.updateTaskStatus(taskId, InterventionTaskStatus.RUNNING)
            }

            pushRunningUi()

            while (elapsedSec < selectedDurationSec) {
                delay(1000L)
                elapsedSec += 1
                pushRunningUi()
            }

            finalizeSession(completed = true)
        }
    }

    fun stopSessionEarly() {
        if (_uiState.value?.isRunning != true) return

        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            finalizeSession(completed = false)
        }
    }

    fun reset() {
        if (_uiState.value?.isRunning == true) return
        elapsedSec = 0
        sessionStartTime = 0L
        _uiState.value = _uiState.value?.copy(
            selectedProtocol = selectedProtocol,
            selectedDurationSec = selectedDurationSec,
            isRunning = false,
            phase = BreathingPhase.PREPARE,
            phaseRemainingSec = 0,
            phaseProgress = 0,
            totalRemainingSec = selectedDurationSec,
            result = null
        )
    }

    private fun pushRunningUi() {
        val phaseState = calculatePhaseState(selectedProtocol, elapsedSec)
        val feedback = signalTracker.currentFeedback()
        appendSessionFeedback(feedback)
        _uiState.postValue(
            (_uiState.value ?: BreathingCoachUiState()).copy(
                selectedProtocol = selectedProtocol,
                selectedDurationSec = selectedDurationSec,
                isRunning = true,
                phase = phaseState.phase,
                phaseRemainingSec = phaseState.remainingSec,
                phaseProgress = phaseState.progress,
                totalRemainingSec = (selectedDurationSec - elapsedSec).coerceAtLeast(0),
                feedback = feedback,
                feedbackHeadline = feedbackHeadline(feedback),
                feedbackDetail = feedbackDetail(feedback),
                result = null
            )
        )
    }

    private suspend fun finalizeSession(completed: Boolean) {
        val wasRunning = _uiState.value?.isRunning == true
        if (!wasRunning) return

        val now = System.currentTimeMillis()
        val actualElapsedSec = elapsedSec.coerceAtLeast(1)
        val postSnapshot = repository.getLatestMetricsOnce().toSnapshot()
        val effect = RelaxationScorer.calculateEffect(preSnapshot, postSnapshot)
        val shouldSave = completed || actualElapsedSec >= MIN_SAVED_DURATION_SEC
        val feedback = signalTracker.currentFeedback(now)
        appendSessionFeedback(feedback)
        val metadata = InterventionExperienceMetadata(
            modality = InterventionExperienceModality.BREATH_VISUAL,
            sessionVariant = selectedProtocol.wireValue,
            hapticEnabled = sessionHapticsEnabled,
            preferredHapticMode = sessionHapticMode,
            realtimeSignalAvailable = sessionRealtimeAvailable,
            avgRelaxSignal = sessionSignals.takeIf { it.isNotEmpty() }?.average()?.toFloat(),
            peakHeartRate = sessionPeakHr.takeIf { it > 0 },
            averageHrv = sessionHrvs.takeIf { it.isNotEmpty() }?.average()?.toInt(),
            completionQuality = buildCompletionQuality(actualElapsedSec, completed),
            fallbackMode = sessionFallbackMode
        )
        val metadataJson = InterventionExperienceCodec.toJson(metadata)

        if (shouldSave) {
            repository.saveSession(
                RelaxSessionEntity(
                    startTime = if (sessionStartTime > 0L) sessionStartTime else now - actualElapsedSec * 1000L,
                    endTime = now,
                    protocolType = selectedProtocol.wireValue,
                    durationSec = actualElapsedSec,
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
            boundTaskId?.let { taskId ->
                val executionEntity = InterventionExecutionEntity(
                    taskId = taskId,
                    startedAt = if (sessionStartTime > 0L) sessionStartTime else now - actualElapsedSec * 1000L,
                    endedAt = now,
                    elapsedSec = actualElapsedSec,
                    beforeStress = effect.preStress,
                    afterStress = effect.postStress,
                    beforeHr = preSnapshot.heartRate,
                    afterHr = postSnapshot.heartRate,
                    effectScore = effect.effectScore,
                    completionType = if (completed) "FULL" else "PARTIAL",
                    metadataJson = metadataJson
                )
                interventionRepository.insertExecution(executionEntity)
                interventionRepository.updateTaskStatus(
                    taskId,
                    if (completed) InterventionTaskStatus.COMPLETED else InterventionTaskStatus.SKIPPED
                )
                syncExecutionToCloud(
                    taskId = taskId,
                    execution = executionEntity,
                    status = if (completed) InterventionTaskStatus.COMPLETED else InterventionTaskStatus.SKIPPED
                )
            }
        } else {
            boundTaskId?.let { taskId ->
                interventionRepository.updateTaskStatus(taskId, InterventionTaskStatus.FAILED)
                syncTaskStatusToCloud(taskId, InterventionTaskStatus.FAILED)
            }
        }

        _uiState.postValue(
            (_uiState.value ?: BreathingCoachUiState()).copy(
                selectedProtocol = selectedProtocol,
                selectedDurationSec = selectedDurationSec,
                isRunning = false,
                phase = BreathingPhase.PREPARE,
                phaseRemainingSec = 0,
                phaseProgress = 0,
                totalRemainingSec = (selectedDurationSec - elapsedSec).coerceAtLeast(0),
                feedback = feedback,
                feedbackHeadline = feedbackHeadline(feedback),
                feedbackDetail = feedbackDetail(feedback),
                result = BreathingSessionResult(
                    completed = completed,
                    saved = shouldSave,
                    elapsedSec = actualElapsedSec,
                    preStress = effect.preStress.roundToInt().coerceIn(0, 100),
                    postStress = effect.postStress.roundToInt().coerceIn(0, 100),
                    effectScore = effect.effectScore.roundToInt().coerceIn(0, 100)
                )
            )
        )

        tickerJob = null
    }

    private fun normalizeDuration(durationSec: Int): Int {
        return when {
            durationSec <= 120 -> 60
            durationSec <= 240 -> 180
            else -> 300
        }
    }

    private fun calculatePhaseState(protocol: BreathingProtocol, elapsedSec: Int): PhaseState {
        val phases = protocol.phases
        if (phases.isEmpty()) {
            return PhaseState(BreathingPhase.PREPARE, 0, 0)
        }

        val cycleSec = phases.sumOf { it.durationSec }.coerceAtLeast(1)
        var secInCycle = elapsedSec % cycleSec

        for (phase in phases) {
            if (secInCycle < phase.durationSec) {
                val remaining = (phase.durationSec - secInCycle).coerceAtLeast(1)
                val progress = (((secInCycle + 1f) / phase.durationSec.toFloat()) * 100f)
                    .roundToInt()
                    .coerceIn(0, 100)
                return PhaseState(
                    phase = phase.phase,
                    remainingSec = remaining,
                    progress = progress
                )
            }
            secInCycle -= phase.durationSec
        }

        val fallback = phases.last()
        return PhaseState(fallback.phase, 1, 100)
    }

    private fun observeRealtimeFeedback() {
        if (feedbackJob != null) return
        feedbackJob = viewModelScope.launch {
            repository.getLatestMetricsFlow().collect { metrics ->
                val feedback = signalTracker.onSample(metrics)
                if (_uiState.value?.isRunning == true) {
                    appendSessionFeedback(feedback)
                }
                _uiState.postValue(
                    (_uiState.value ?: BreathingCoachUiState()).copy(
                        feedback = feedback,
                        feedbackHeadline = feedbackHeadline(feedback),
                        feedbackDetail = feedbackDetail(feedback)
                    )
                )
            }
        }
    }

    private fun appendSessionFeedback(feedback: RelaxRealtimeFeedback) {
        if (feedback.hasRealtimeData) {
            sessionRealtimeAvailable = true
            sessionSignals += feedback.relaxSignal
            if (feedback.hrv > 0) {
                sessionHrvs += feedback.hrv
            }
            sessionPeakHr = maxOf(sessionPeakHr, feedback.heartRate)
        } else if (sessionFallbackMode == null) {
            sessionFallbackMode = "BREATH_ONLY"
        }
    }

    private fun feedbackHeadline(feedback: RelaxRealtimeFeedback): String {
        return when {
            !feedback.hasRealtimeData -> app.getString(R.string.relax_realtime_feedback_fallback)
            feedback.relaxSignal >= 0.72f -> app.getString(R.string.relax_realtime_feedback_calm)
            feedback.relaxSignal >= 0.48f -> app.getString(R.string.relax_realtime_feedback_steady)
            else -> app.getString(R.string.relax_realtime_feedback_active)
        }
    }

    private fun feedbackDetail(feedback: RelaxRealtimeFeedback): String {
        return app.getString(
            R.string.relax_feedback_signal_format,
            (feedback.relaxSignal * 100f).toInt().coerceIn(0, 100),
            feedback.heartRate.coerceAtLeast(0),
            feedback.hrv.coerceAtLeast(0)
        )
    }

    private fun buildCompletionQuality(actualElapsedSec: Int, completed: Boolean): Int {
        val durationRatio = (actualElapsedSec.toFloat() / selectedDurationSec.toFloat()).coerceIn(0f, 1f)
        val signalBonus = sessionSignals.takeIf { it.isNotEmpty() }?.average()?.times(25f)?.toInt() ?: 0
        val base = (durationRatio * 70f).toInt() + signalBonus + if (completed) 10 else 0
        return base.coerceIn(0, 100)
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

    private suspend fun syncExecutionToCloud(
        taskId: String,
        execution: InterventionExecutionEntity,
        status: InterventionTaskStatus
    ) {
        if (networkRepository.getCurrentSession() == null) {
            return
        }
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
                status = status.name
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

    private suspend fun syncTaskStatusToCloud(taskId: String, status: InterventionTaskStatus) {
        if (networkRepository.getCurrentSession() == null) {
            return
        }
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
                status = status.name
            )
        )
    }

    override fun onCleared() {
        tickerJob?.cancel()
        feedbackJob?.cancel()
        super.onCleared()
    }
}

data class BreathingCoachUiState(
    val selectedProtocol: BreathingProtocol = BreathingProtocol.BREATH_4_6,
    val selectedDurationSec: Int = 60,
    val isRunning: Boolean = false,
    val phase: BreathingPhase = BreathingPhase.PREPARE,
    val phaseRemainingSec: Int = 0,
    val phaseProgress: Int = 0,
    val totalRemainingSec: Int = 60,
    val feedback: RelaxRealtimeFeedback = RelaxRealtimeFeedback(),
    val feedbackHeadline: String = "",
    val feedbackDetail: String = "",
    val result: BreathingSessionResult? = null
)

data class BreathingSessionResult(
    val completed: Boolean,
    val saved: Boolean,
    val elapsedSec: Int,
    val preStress: Int,
    val postStress: Int,
    val effectScore: Int
)

enum class BreathingPhase {
    PREPARE,
    INHALE,
    HOLD,
    EXHALE
}

enum class BreathingProtocol(
    val wireValue: String,
    val phases: List<PhaseSegment>
) {
    BREATH_4_6(
        wireValue = "BREATH_4_6",
        phases = listOf(
            PhaseSegment(BreathingPhase.INHALE, 4),
            PhaseSegment(BreathingPhase.EXHALE, 6)
        )
    ),
    BREATH_4_7_8(
        wireValue = "BREATH_4_7_8",
        phases = listOf(
            PhaseSegment(BreathingPhase.INHALE, 4),
            PhaseSegment(BreathingPhase.HOLD, 7),
            PhaseSegment(BreathingPhase.EXHALE, 8)
        )
    ),
    BOX(
        wireValue = "BOX",
        phases = listOf(
            PhaseSegment(BreathingPhase.INHALE, 4),
            PhaseSegment(BreathingPhase.HOLD, 4),
            PhaseSegment(BreathingPhase.EXHALE, 4),
            PhaseSegment(BreathingPhase.HOLD, 4)
        )
    );

    companion object {
        fun fromWireValue(value: String?): BreathingProtocol {
            val normalized = when (value?.uppercase()) {
                "BREATH_BOX" -> "BOX"
                else -> value
            }
            return entries.firstOrNull { it.wireValue.equals(normalized, ignoreCase = true) }
                ?: BREATH_4_6
        }
    }
}

data class PhaseSegment(
    val phase: BreathingPhase,
    val durationSec: Int
)

private data class PhaseState(
    val phase: BreathingPhase,
    val remainingSec: Int,
    val progress: Int
)
