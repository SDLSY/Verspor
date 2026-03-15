package com.example.newstart.ui.relax

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.newstart.core.common.R
import com.example.newstart.database.AppDatabase
import com.example.newstart.database.entity.InterventionExecutionEntity
import com.example.newstart.database.entity.InterventionTaskEntity
import com.example.newstart.database.entity.RelaxSessionEntity
import com.example.newstart.intervention.HapticPatternMode
import com.example.newstart.intervention.InterventionExperienceMetadata
import com.example.newstart.intervention.InterventionExperienceModality
import com.example.newstart.intervention.ProfileTriggerType
import com.example.newstart.intervention.ZenInteractionMode
import com.example.newstart.network.models.InterventionExecutionUpsertRequest
import com.example.newstart.network.models.InterventionTaskUpsertRequest
import com.example.newstart.repository.InterventionExperienceCodec
import com.example.newstart.repository.InterventionProfileRepository
import com.example.newstart.repository.InterventionRepository
import com.example.newstart.repository.InterventionTaskSourceType
import com.example.newstart.repository.InterventionTaskStatus
import com.example.newstart.repository.NetworkRepository
import com.example.newstart.repository.RelaxRepository
import com.example.newstart.util.RelaxVitalSnapshot
import com.example.newstart.util.RelaxationScorer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class ZenInteractionUiState(
    val protocolCode: String = "ZEN_WAVE_GARDEN_5M",
    val title: String = "",
    val subtitle: String = "",
    val rationale: String = "",
    val mode: ZenInteractionMode = ZenInteractionMode.WAVE_GARDEN,
    val remainingSec: Int = 300,
    val isRunning: Boolean = false,
    val hasStarted: Boolean = false,
    val isCompleted: Boolean = false,
    val interactionProgress: Int = 0,
    val touchCount: Int = 0
)

sealed class ZenInteractionEvent {
    data class Toast(val message: String) : ZenInteractionEvent()
    data object Completed : ZenInteractionEvent()
}

class ZenInteractionViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application
    private val db = AppDatabase.getDatabase(application)
    private val relaxRepository = RelaxRepository(db.relaxSessionDao(), db.healthMetricsDao())
    private val interventionRepository = InterventionRepository(db.interventionTaskDao(), db.interventionExecutionDao())
    private val profileRepository = InterventionProfileRepository(application, db)
    private val networkRepository = NetworkRepository()

    private val _uiState = MutableLiveData(ZenInteractionUiState())
    val uiState: LiveData<ZenInteractionUiState> = _uiState

    private val _event = MutableLiveData<ZenInteractionEvent?>()
    val event: LiveData<ZenInteractionEvent?> = _event

    private var tickerJob: Job? = null
    private var startedAt: Long = 0L
    private var taskId: String? = null
    private var preSnapshot = RelaxVitalSnapshot(heartRate = 0, hrv = 0, spo2 = 0, motion = 0f)

    fun initialize(protocolCode: String?, title: String?, durationSec: Int, rationale: String?) {
        val safeProtocol = protocolCode.orEmpty().ifBlank { "ZEN_WAVE_GARDEN_5M" }
        val safeMode = ZenInteractionMode.fromProtocol(safeProtocol)
        _uiState.value = ZenInteractionUiState(
            protocolCode = safeProtocol,
            title = title.orEmpty().ifBlank {
                if (safeMode == ZenInteractionMode.MIST_ERASE) {
                    app.getString(R.string.zen_interaction_mode_mist)
                } else {
                    app.getString(R.string.zen_interaction_mode_wave)
                }
            },
            subtitle = if (safeMode == ZenInteractionMode.MIST_ERASE) {
                app.getString(R.string.zen_interaction_hint_mist)
            } else {
                app.getString(R.string.zen_interaction_hint_wave)
            },
            rationale = rationale.orEmpty(),
            mode = safeMode,
            remainingSec = durationSec.coerceAtLeast(60)
        )
    }

    fun startSession() {
        val state = _uiState.value ?: return
        if (state.isRunning || state.isCompleted) return
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            val now = System.currentTimeMillis()
            preSnapshot = relaxRepository.getLatestMetricsOnce().toSnapshot()
            startedAt = now
            val task = InterventionTaskEntity(
                date = now,
                sourceType = InterventionTaskSourceType.AI_COACH.name,
                triggerReason = "zen_interaction",
                bodyZone = "PROFILE",
                protocolType = state.protocolCode,
                durationSec = state.remainingSec,
                plannedAt = now,
                status = InterventionTaskStatus.RUNNING.name
            )
            taskId = task.id
            interventionRepository.upsertTask(task)
            _uiState.postValue(state.copy(isRunning = true, hasStarted = true))
            while ((_uiState.value?.remainingSec ?: 0) > 0) {
                delay(1000L)
                val current = _uiState.value ?: break
                _uiState.postValue(
                    current.copy(
                        remainingSec = (current.remainingSec - 1).coerceAtLeast(0),
                        isRunning = current.remainingSec > 1
                    )
                )
            }
        }
    }

    fun updateInteraction(progress: Float, touches: Int) {
        val state = _uiState.value ?: return
        _uiState.value = state.copy(
            interactionProgress = (progress * 100f).toInt().coerceIn(0, 100),
            touchCount = touches
        )
    }

    fun completeSession() {
        val state = _uiState.value ?: return
        val safeTaskId = taskId ?: return
        viewModelScope.launch {
            tickerJob?.cancel()
            val endedAt = System.currentTimeMillis()
            val postSnapshot = relaxRepository.getLatestMetricsOnce().toSnapshot()
            val effect = RelaxationScorer.calculateEffect(preSnapshot, postSnapshot)
            val metadata = InterventionExperienceMetadata(
                modality = InterventionExperienceModality.ZEN,
                sessionVariant = state.mode.name,
                hapticEnabled = false,
                preferredHapticMode = HapticPatternMode.BREATH,
                realtimeSignalAvailable = false,
                avgRelaxSignal = null,
                peakHeartRate = maxOf(preSnapshot.heartRate, postSnapshot.heartRate),
                averageHrv = listOf(preSnapshot.hrv, postSnapshot.hrv)
                    .filter { it > 0 }
                    .average()
                    .takeIf { !it.isNaN() }
                    ?.toInt(),
                completionQuality = ((state.interactionProgress * 0.7f) + (state.touchCount.coerceAtMost(40) * 0.75f))
                    .toInt()
                    .coerceIn(0, 100),
                interactionTouches = state.touchCount
            )
            val metadataJson = InterventionExperienceCodec.toJson(metadata)
            interventionRepository.updateTaskStatus(safeTaskId, InterventionTaskStatus.COMPLETED)
            val execution = InterventionExecutionEntity(
                taskId = safeTaskId,
                startedAt = startedAt,
                endedAt = endedAt,
                elapsedSec = ((endedAt - startedAt) / 1000L).toInt().coerceAtLeast(1),
                beforeStress = effect.preStress,
                afterStress = effect.postStress,
                beforeHr = preSnapshot.heartRate,
                afterHr = postSnapshot.heartRate,
                effectScore = effect.effectScore,
                completionType = "FULL",
                metadataJson = metadataJson
            )
            interventionRepository.insertExecution(execution)
            relaxRepository.saveSession(
                RelaxSessionEntity(
                    startTime = startedAt,
                    endTime = endedAt,
                    protocolType = state.protocolCode,
                    durationSec = ((endedAt - startedAt) / 1000L).toInt().coerceAtLeast(1),
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
            syncExecutionToCloud(safeTaskId, execution)
            profileRepository.refreshSnapshot(ProfileTriggerType.EXECUTION_FEEDBACK)
            _uiState.postValue(state.copy(isRunning = false, isCompleted = true, remainingSec = 0))
            _event.postValue(ZenInteractionEvent.Toast(app.getString(R.string.zen_interaction_saved)))
            _event.postValue(ZenInteractionEvent.Completed)
        }
    }

    fun consumeEvent() {
        _event.value = null
    }

    override fun onCleared() {
        tickerJob?.cancel()
        super.onCleared()
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

    private fun com.example.newstart.database.entity.HealthMetricsEntity?.toSnapshot(): RelaxVitalSnapshot {
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
}
