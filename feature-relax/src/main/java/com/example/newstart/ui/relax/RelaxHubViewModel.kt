package com.example.newstart.ui.relax

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.newstart.ai.InterventionAction
import com.example.newstart.ai.InterventionPlanInput
import com.example.newstart.ai.InterventionPlanResult
import com.example.newstart.core.common.R
import com.example.newstart.database.AppDatabase
import com.example.newstart.database.dao.RelaxDailySummary
import com.example.newstart.database.entity.HealthMetricsEntity
import com.example.newstart.database.entity.InterventionTaskEntity
import com.example.newstart.network.models.InterventionTaskUpsertRequest
import com.example.newstart.repository.InterventionRepository
import com.example.newstart.repository.InterventionTaskSourceType
import com.example.newstart.repository.InterventionTaskStatus
import com.example.newstart.repository.NetworkRepository
import com.example.newstart.repository.RelaxRepository
import com.example.newstart.service.ai.LocalInterventionPlanningService
import com.example.newstart.util.PerformanceTelemetry
import com.example.newstart.util.RelaxVitalSnapshot
import com.example.newstart.util.RelaxationScorer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class RelaxHubViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "RelaxHubViewModel"
        private const val LLM_ENHANCE_TIMEOUT_MS = 3_000L
        private val LLM_TIMEOUT_EXECUTOR = Executors.newCachedThreadPool()
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

    private val _uiState = MutableLiveData(
        RelaxHubUiState(
            summary = app.getString(R.string.relax_summary_waiting),
            recommendation = app.getString(R.string.relax_recommendation_waiting),
            heartRateText = app.getString(R.string.relax_metric_hr_placeholder),
            hrvText = app.getString(R.string.relax_metric_hrv_placeholder),
            spo2Text = app.getString(R.string.relax_metric_spo2_placeholder),
            motionText = app.getString(R.string.relax_metric_motion_placeholder),
            updatedAtText = app.getString(R.string.relax_updated_placeholder)
        )
    )
    val uiState: LiveData<RelaxHubUiState> = _uiState

    private val _launchCommand = MutableLiveData<InterventionLaunchCommand?>()
    val launchCommand: LiveData<InterventionLaunchCommand?> = _launchCommand

    private val _toastEvent = MutableLiveData<String?>()
    val toastEvent: LiveData<String?> = _toastEvent

    private var latestMetrics: HealthMetricsEntity? = null
    private var latestSummary: RelaxDailySummary = RelaxDailySummary()
    private var enhanceJob: Job? = null
    private var activePlanToken: Long = 0L
    private var activeDraftTaskId: String? = null

    init {
        observeLatestMetrics()
        observeTodaySummary()
    }

    fun consumeLaunchCommand() {
        _launchCommand.value = null
    }

    fun consumeToast() {
        _toastEvent.value = null
    }

    fun createQuickTask(protocolType: String, durationSec: Int, reason: String) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val task = InterventionTaskEntity(
                date = startOfDay(now),
                sourceType = InterventionTaskSourceType.RULE_ENGINE.name,
                triggerReason = reason,
                bodyZone = HumanBody3DView.BodyZone.CHEST.name,
                protocolType = protocolType,
                durationSec = durationSec,
                plannedAt = now,
                status = InterventionTaskStatus.PENDING.name,
                createdAt = now,
                updatedAt = now
            )
            interventionRepository.upsertTask(task)
            syncTaskAsync(task)
            _launchCommand.postValue(
                InterventionLaunchCommand(
                    protocolType = protocolType,
                    durationSec = durationSec,
                    taskId = task.id
                )
            )
        }
    }

    fun executeCurrentPlan() {
        viewModelScope.launch {
            val state = _uiState.value ?: return@launch
            val now = System.currentTimeMillis()
            val task = InterventionTaskEntity(
                id = activeDraftTaskId ?: UUID.randomUUID().toString(),
                date = startOfDay(now),
                sourceType = if (state.isAiEnhanced) {
                    InterventionTaskSourceType.LLM_ENHANCED.name
                } else {
                    InterventionTaskSourceType.RULE_IMMEDIATE.name
                },
                triggerReason = state.lastPlanReason.ifBlank { "Plan execute" },
                bodyZone = state.selectedBodyZone.ifBlank { HumanBody3DView.BodyZone.CHEST.name },
                protocolType = state.lastPlanProtocolType,
                durationSec = state.lastPlanDurationSec,
                plannedAt = now,
                status = InterventionTaskStatus.PENDING.name,
                createdAt = now,
                updatedAt = now
            )
            interventionRepository.upsertTask(task)
            activeDraftTaskId = task.id
            syncTaskAsync(task)
            _launchCommand.postValue(
                InterventionLaunchCommand(
                    protocolType = task.protocolType,
                    durationSec = task.durationSec,
                    taskId = task.id
                )
            )
        }
    }

    fun onBodyZoneSelected(
        zone: HumanBody3DView.BodyZone,
        source: HumanBody3DView.ZonePickSource
    ) {
        activePlanToken += 1L
        val token = activePlanToken
        enhanceJob?.cancel()

        viewModelScope.launch {
            Log.d(TAG, "onBodyZoneSelected start: zone=$zone source=$source")

            val metrics = latestMetrics ?: repository.getLatestMetricsOnce()
            val snapshot = metrics.toSnapshot()
            val stressIndex = if (snapshot.hasValidSignals()) {
                RelaxationScorer.calculateStressIndex(snapshot)
            } else {
                50
            }

            val ruleStart = PerformanceTelemetry.nowElapsedMs()
            val rulePlan = buildRulePlan(zone = zone, stressIndex = stressIndex)
            PerformanceTelemetry.recordDuration(
                metric = "plan_rule_latency_ms",
                startElapsedMs = ruleStart,
                attributes = mapOf(
                    "zone" to zone.name,
                    "source" to source.name.lowercase(Locale.US)
                )
            )

            val now = System.currentTimeMillis()
            val ruleDuration = rulePlan.actions.sumOf { it.durationSec }.coerceIn(60, 600)
            val draftTask = InterventionTaskEntity(
                id = activeDraftTaskId ?: UUID.randomUUID().toString(),
                date = startOfDay(now),
                sourceType = InterventionTaskSourceType.RULE_IMMEDIATE.name,
                triggerReason = rulePlan.rationale,
                bodyZone = zone.name,
                protocolType = rulePlan.protocolType,
                durationSec = ruleDuration,
                plannedAt = now,
                status = InterventionTaskStatus.PENDING.name,
                createdAt = now,
                updatedAt = now
            )
            interventionRepository.upsertTask(draftTask)
            activeDraftTaskId = draftTask.id
            syncTaskAsync(draftTask)

            val currentState = _uiState.value ?: RelaxHubUiState()
            _uiState.postValue(
                currentState.copy(
                    selectedBodyZone = zone.name,
                    selectedZoneDisplayName = zoneDisplayName(zone, source),
                    lastPlanTitle = rulePlan.title,
                    lastPlanReason = rulePlan.rationale,
                    lastPlanRule = rulePlan.completionRule,
                    lastPlanFallback = false,
                    lastPlanProtocolType = rulePlan.protocolType,
                    lastPlanDurationSec = ruleDuration,
                    planGenerationState = PlanGenerationState.ENHANCING,
                    isAiEnhanced = false
                )
            )

            val enhanceInput = InterventionPlanInput(
                bodyZone = zone.name,
                zoneDetail = zoneDisplayName(zone, source),
                pickSource = source.name.lowercase(Locale.US),
                triggerReason = "3D hotspot tap: ${zone.name}",
                stressIndex = stressIndex,
                recoveryScore = 100 - stressIndex,
                heartRate = snapshot.heartRate,
                hrv = snapshot.hrv,
                spo2 = snapshot.spo2
            )

            enhanceJob = viewModelScope.launch {
                try {
                    val llmStart = PerformanceTelemetry.nowElapsedMs()
                    val llmPlan = runLlmEnhancementWithTimeout(enhanceInput, LLM_ENHANCE_TIMEOUT_MS)
                    if (token != activePlanToken) return@launch

                    if (llmPlan == null) {
                        PerformanceTelemetry.record(
                            metric = "plan_llm_timeout_count",
                            value = 1.0,
                            unit = "count",
                            attributes = mapOf("zone" to zone.name)
                        )
                        settleEnhanceNoUpdate()
                        return@launch
                    }

                    PerformanceTelemetry.recordDuration(
                        metric = "plan_llm_enhance_latency_ms",
                        startElapsedMs = llmStart,
                        attributes = mapOf(
                            "zone" to zone.name,
                            "fallback" to llmPlan.fallbackUsed.toString()
                        )
                    )

                    if (llmPlan.fallbackUsed) {
                        settleEnhanceNoUpdate()
                        return@launch
                    }

                    val llmDuration = llmPlan.actions.sumOf { it.durationSec }.coerceIn(60, 600)
                    val upgradedTask = draftTask.copy(
                        sourceType = InterventionTaskSourceType.LLM_ENHANCED.name,
                        triggerReason = llmPlan.rationale,
                        protocolType = llmPlan.protocolType,
                        durationSec = llmDuration,
                        updatedAt = System.currentTimeMillis()
                    )
                    interventionRepository.upsertTask(upgradedTask)
                    syncTaskAsync(upgradedTask)

                    val state = _uiState.value ?: RelaxHubUiState()
                    _uiState.postValue(
                        state.copy(
                            lastPlanTitle = llmPlan.title,
                            lastPlanReason = llmPlan.rationale,
                            lastPlanRule = llmPlan.completionRule,
                            lastPlanFallback = false,
                            lastPlanProtocolType = llmPlan.protocolType,
                            lastPlanDurationSec = llmDuration,
                            planGenerationState = PlanGenerationState.ENHANCED,
                            isAiEnhanced = true
                        )
                    )
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    Log.e(TAG, "onBodyZoneSelected enhance failed: zone=$zone", t)
                    if (token == activePlanToken) {
                        val state = _uiState.value ?: RelaxHubUiState()
                        _uiState.postValue(
                            state.copy(
                                planGenerationState = PlanGenerationState.FAILED,
                                isAiEnhanced = false
                            )
                        )
                    }
                }
            }
        }
    }

    private suspend fun runLlmEnhancementWithTimeout(
        input: InterventionPlanInput,
        timeoutMs: Long
    ): InterventionPlanResult? {
        return withContext(Dispatchers.IO) {
            val future = LLM_TIMEOUT_EXECUTOR.submit<InterventionPlanResult> {
                LocalInterventionPlanningService.generateInterventionPlan(input)
            }
            try {
                future.get(timeoutMs, TimeUnit.MILLISECONDS)
            } catch (_: TimeoutException) {
                future.cancel(true)
                null
            } catch (_: ExecutionException) {
                null
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                null
            }
        }
    }

    private fun settleEnhanceNoUpdate() {
        val state = _uiState.value ?: RelaxHubUiState()
        _uiState.postValue(
            state.copy(
                planGenerationState = PlanGenerationState.RULE_READY,
                isAiEnhanced = false
            )
        )
    }

    private fun buildRulePlan(
        zone: HumanBody3DView.BodyZone,
        stressIndex: Int
    ): InterventionPlanResult {
        val highStress = stressIndex >= 70
        return when (zone) {
            HumanBody3DView.BodyZone.HEAD,
            HumanBody3DView.BodyZone.NECK -> {
                InterventionPlanResult(
                    title = "头颈减压呼吸方案",
                    rationale = "头颈区域紧张提示认知负荷偏高，优先降低刺激并放慢呼吸节律。",
                    actions = listOf(
                        InterventionAction("降低屏幕刺激", "降低屏幕亮度并减少视觉刺激 10 分钟。", 600),
                        InterventionAction(
                            "4-6 呼吸",
                            "吸气 4 秒、呼气 6 秒，保持稳定节律。",
                            if (highStress) 300 else 180
                        )
                    ),
                    caution = "若出现头晕或胸闷不适，请暂停训练。",
                    completionRule = "完成两项动作并记录训练前后的压力指数。",
                    protocolType = "BREATH_4_6",
                    fallbackUsed = false
                )
            }

            HumanBody3DView.BodyZone.CHEST,
            HumanBody3DView.BodyZone.UPPER_BACK -> {
                InterventionPlanResult(
                    title = "胸背节律干预方案",
                    rationale = "胸背压力信号提示交感负荷偏高，先进行节律呼吸。",
                    actions = listOf(
                        InterventionAction(
                            "4-7-8 呼吸",
                            "吸气 4 秒、屏息 7 秒、呼气 8 秒，重置节律。",
                            if (highStress) 300 else 180
                        ),
                        InterventionAction("低强度步行", "慢走以稳定呼吸节律。", 300)
                    ),
                    caution = "若出现明显胸痛，请及时就医。",
                    completionRule = "完成呼吸训练并执行一项低负荷运动。",
                    protocolType = "BREATH_4_7_8",
                    fallbackUsed = false
                )
            }

            HumanBody3DView.BodyZone.ABDOMEN,
            HumanBody3DView.BodyZone.LOWER_BACK -> {
                InterventionPlanResult(
                    title = "核心放松干预方案",
                    rationale = "核心区域紧张提示副交感恢复不足，建议使用方块呼吸与放松动作。",
                    actions = listOf(
                        InterventionAction("方块呼吸", "吸气、停顿、呼气、停顿各 4 秒。", 240),
                        InterventionAction("仰卧放松", "平躺并进行腹式放松呼吸。", 180)
                    ),
                    caution = "若持续腹痛或恶心，请停止训练。",
                    completionRule = "保持节律稳定，中断时间不超过 15 秒。",
                    protocolType = "BOX",
                    fallbackUsed = false
                )
            }

            else -> {
                InterventionPlanResult(
                    title = "四肢恢复干预方案",
                    rationale = "四肢压力提示外周负荷累积，建议活动度训练配合控速呼吸。",
                    actions = listOf(
                        InterventionAction("动态拉伸", "进行肩部与髋部的动态拉伸。", 180),
                        InterventionAction("4-6 呼吸", "保持稳定节律并降低压力反应。", 180)
                    ),
                    caution = "若出现头晕或疼痛，请降低强度。",
                    completionRule = "一次连续完成拉伸与呼吸训练。",
                    protocolType = "BREATH_4_6",
                    fallbackUsed = false
                )
            }
        }
    }

    private fun zoneDisplayName(
        zone: HumanBody3DView.BodyZone,
        source: HumanBody3DView.ZonePickSource? = null
    ): String {
        if (source == HumanBody3DView.ZonePickSource.BUTTON && zone == HumanBody3DView.BodyZone.LEFT_LEG) {
            return "四肢"
        }
        return when (zone) {
            HumanBody3DView.BodyZone.HEAD -> "头部"
            HumanBody3DView.BodyZone.NECK -> "颈部"
            HumanBody3DView.BodyZone.CHEST -> "胸部"
            HumanBody3DView.BodyZone.UPPER_BACK -> "上背"
            HumanBody3DView.BodyZone.ABDOMEN -> "腹部"
            HumanBody3DView.BodyZone.LOWER_BACK -> "下背"
            HumanBody3DView.BodyZone.LEFT_ARM -> "左臂"
            HumanBody3DView.BodyZone.RIGHT_ARM -> "右臂"
            HumanBody3DView.BodyZone.LEFT_LEG -> "左腿"
            HumanBody3DView.BodyZone.RIGHT_LEG -> "右腿"
        }
    }

    private fun observeLatestMetrics() {
        viewModelScope.launch {
            repository.getLatestMetricsFlow().collectLatest { entity ->
                latestMetrics = entity
                rebuildUiState()
            }
        }
    }

    private fun observeTodaySummary() {
        val (startTime, endTime) = todayRange()
        viewModelScope.launch {
            repository.getTodaySummary(startTime, endTime).collectLatest { summary ->
                latestSummary = summary
                rebuildUiState()
            }
        }
    }

    private fun rebuildUiState() {
        _uiState.postValue(buildUiState(latestMetrics, latestSummary))
    }

    private fun buildUiState(
        entity: HealthMetricsEntity?,
        summary: RelaxDailySummary
    ): RelaxHubUiState {
        val previousState = _uiState.value ?: RelaxHubUiState()
        if (entity == null) {
            return RelaxHubUiState(
                summary = app.getString(R.string.relax_summary_waiting),
                recommendation = app.getString(R.string.relax_recommendation_waiting),
                heartRateText = app.getString(R.string.relax_metric_hr_placeholder),
                hrvText = app.getString(R.string.relax_metric_hrv_placeholder),
                spo2Text = app.getString(R.string.relax_metric_spo2_placeholder),
                motionText = app.getString(R.string.relax_metric_motion_placeholder),
                updatedAtText = app.getString(R.string.relax_updated_placeholder),
                todaySessions = summary.sessionCount,
                todayMinutes = summary.totalMinutes,
                selectedBodyZone = previousState.selectedBodyZone,
                selectedZoneDisplayName = previousState.selectedZoneDisplayName,
                lastPlanTitle = previousState.lastPlanTitle,
                lastPlanReason = previousState.lastPlanReason,
                lastPlanRule = previousState.lastPlanRule,
                lastPlanFallback = previousState.lastPlanFallback,
                lastPlanProtocolType = previousState.lastPlanProtocolType,
                lastPlanDurationSec = previousState.lastPlanDurationSec,
                planGenerationState = previousState.planGenerationState,
                isAiEnhanced = previousState.isAiEnhanced
            )
        }

        val snapshot = entity.toSnapshot()
        val hasValidSignals = snapshot.hasValidSignals()
        val stressIndex = if (hasValidSignals) RelaxationScorer.calculateStressIndex(snapshot) else 0
        val level = if (!hasValidSignals) {
            RelaxStressLevel.UNKNOWN
        } else {
            when {
                stressIndex >= 70 -> RelaxStressLevel.HIGH
                stressIndex >= 40 -> RelaxStressLevel.MEDIUM
                else -> RelaxStressLevel.LOW
            }
        }

        val summaryText = when (level) {
            RelaxStressLevel.HIGH -> app.getString(R.string.relax_summary_high)
            RelaxStressLevel.MEDIUM -> app.getString(R.string.relax_summary_medium)
            RelaxStressLevel.LOW -> app.getString(R.string.relax_summary_low)
            RelaxStressLevel.UNKNOWN -> app.getString(R.string.relax_summary_waiting)
        }

        val recommendation = when {
            level == RelaxStressLevel.HIGH -> app.getString(R.string.relax_recommendation_high)
            level == RelaxStressLevel.MEDIUM && entity.accMagnitudeSample >= 10f ->
                app.getString(R.string.relax_recommendation_medium_motion)

            level == RelaxStressLevel.MEDIUM -> app.getString(R.string.relax_recommendation_medium)
            level == RelaxStressLevel.LOW -> app.getString(R.string.relax_recommendation_low)
            else -> app.getString(R.string.relax_recommendation_waiting)
        }

        val heartRateText = if (entity.heartRateSample > 0) {
            app.getString(R.string.relax_metric_hr_value, entity.heartRateSample)
        } else {
            app.getString(R.string.relax_metric_hr_placeholder)
        }
        val hrvText = if (entity.hrvCurrent > 0) {
            app.getString(R.string.relax_metric_hrv_value, entity.hrvCurrent)
        } else {
            app.getString(R.string.relax_metric_hrv_placeholder)
        }
        val spo2Text = if (entity.bloodOxygenSample > 0) {
            app.getString(R.string.relax_metric_spo2_value, entity.bloodOxygenSample)
        } else {
            app.getString(R.string.relax_metric_spo2_placeholder)
        }
        val motionText = if (entity.accMagnitudeSample > 0f) {
            app.getString(R.string.relax_metric_motion_value, entity.accMagnitudeSample.coerceAtLeast(0f))
        } else {
            app.getString(R.string.relax_metric_motion_placeholder)
        }
        val timeText = app.getString(
            R.string.relax_updated_value,
            SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(entity.timestamp)
        )

        return RelaxHubUiState(
            stressIndex = stressIndex,
            level = level,
            summary = summaryText,
            recommendation = recommendation,
            heartRateText = heartRateText,
            hrvText = hrvText,
            spo2Text = spo2Text,
            motionText = motionText,
            updatedAtText = timeText,
            todaySessions = summary.sessionCount,
            todayMinutes = summary.totalMinutes,
            selectedBodyZone = previousState.selectedBodyZone,
            selectedZoneDisplayName = previousState.selectedZoneDisplayName,
            lastPlanTitle = previousState.lastPlanTitle,
            lastPlanReason = previousState.lastPlanReason,
            lastPlanRule = previousState.lastPlanRule,
            lastPlanFallback = previousState.lastPlanFallback,
            lastPlanProtocolType = previousState.lastPlanProtocolType,
            lastPlanDurationSec = previousState.lastPlanDurationSec,
            planGenerationState = previousState.planGenerationState,
            isAiEnhanced = previousState.isAiEnhanced
        )
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

    private fun RelaxVitalSnapshot.hasValidSignals(): Boolean {
        return (heartRate in 25..240) ||
            (hrv in 10..220) ||
            (spo2 in 70..100) ||
            (motion > 0f)
    }

    private fun todayRange(): Pair<Long, Long> {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val start = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        val end = calendar.timeInMillis - 1
        return start to end
    }

    private fun startOfDay(timestamp: Long): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }

    private fun syncTaskAsync(task: InterventionTaskEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { syncTaskIfAvailable(task) }
                .onFailure { Log.w(TAG, "syncTaskIfAvailable failed", it) }
        }
    }

    private suspend fun syncTaskIfAvailable(task: InterventionTaskEntity) {
        if (networkRepository.getCurrentSession() == null) return
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
                status = task.status
            )
        )
    }
}

enum class RelaxStressLevel {
    LOW,
    MEDIUM,
    HIGH,
    UNKNOWN
}

enum class PlanGenerationState {
    IDLE,
    RULE_READY,
    ENHANCING,
    ENHANCED,
    FAILED
}

data class InterventionLaunchCommand(
    val protocolType: String,
    val durationSec: Int,
    val taskId: String
)

data class RelaxHubUiState(
    val stressIndex: Int = 0,
    val level: RelaxStressLevel = RelaxStressLevel.UNKNOWN,
    val summary: String = "",
    val recommendation: String = "",
    val heartRateText: String = "",
    val hrvText: String = "",
    val spo2Text: String = "",
    val motionText: String = "",
    val updatedAtText: String = "",
    val todaySessions: Int = 0,
    val todayMinutes: Int = 0,
    val selectedBodyZone: String = "",
    val selectedZoneDisplayName: String = "",
    val lastPlanTitle: String = "",
    val lastPlanReason: String = "",
    val lastPlanRule: String = "",
    val lastPlanFallback: Boolean = false,
    val lastPlanProtocolType: String = "BREATH_4_6",
    val lastPlanDurationSec: Int = 60,
    val planGenerationState: PlanGenerationState = PlanGenerationState.IDLE,
    val isAiEnhanced: Boolean = false
)
