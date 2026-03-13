package com.example.newstart.ui.relax

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.newstart.core.common.R
import com.example.newstart.database.AppDatabase
import com.example.newstart.database.entity.HealthMetricsEntity
import com.example.newstart.database.entity.InterventionTaskEntity
import com.example.newstart.network.models.InterventionTaskUpsertRequest
import com.example.newstart.repository.InterventionRepository
import com.example.newstart.repository.InterventionTaskSourceType
import com.example.newstart.repository.InterventionTaskStatus
import com.example.newstart.repository.NetworkRepository
import com.example.newstart.repository.RelaxRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale
import java.util.UUID

class SymptomGuideViewModel(application: Application) : AndroidViewModel(application) {

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

    private val _uiState = MutableLiveData(buildInitialState())
    val uiState: LiveData<SymptomGuideUiState> = _uiState

    private val _launchCommand = MutableLiveData<SymptomGuideLaunchCommand?>()
    val launchCommand: LiveData<SymptomGuideLaunchCommand?> = _launchCommand

    private val _toastEvent = MutableLiveData<String?>()
    val toastEvent: LiveData<String?> = _toastEvent

    private var latestMetrics: HealthMetricsEntity? = null

    init {
        observeLatestMetrics()
    }

    fun consumeLaunchCommand() {
        _launchCommand.value = null
    }

    fun consumeToast() {
        _toastEvent.value = null
    }

    fun setSurfaceSide(side: SurfaceSide) {
        updateState { state ->
            if (state.selectedSurfaceSide == side) state else state.copy(selectedSurfaceSide = side)
        }
    }

    fun addQuickSymptom(label: String) {
        val zone = uiState.value
            ?.quickSymptoms
            ?.firstOrNull { it.label == label }
            ?.zone
            ?: inferZoneFromSymptom(label)
        addMarker(
            SelectedBodyMarker(
                id = UUID.randomUUID().toString(),
                zone = zone,
                surfaceSide = SurfaceSide.FRONT,
                symptomLabel = label,
                severity = defaultSeverityFor(label),
                durationLabel = app.getString(R.string.symptom_duration_recent_day)
            )
        )
    }

    fun upsertMarker(
        zone: SymptomBodyZone,
        side: SurfaceSide,
        symptomLabel: String,
        severity: Int,
        durationLabel: String,
        note: String
    ) {
        addMarker(
            SelectedBodyMarker(
                id = UUID.randomUUID().toString(),
                zone = zone,
                surfaceSide = side,
                symptomLabel = symptomLabel,
                severity = severity,
                durationLabel = durationLabel,
                note = note.trim()
            )
        )
    }

    fun removeMarker(markerId: String) {
        updateState { state ->
            val markers = state.selectedMarkers.filterNot { it.id == markerId }
            state.copy(
                selectedMarkers = markers,
                outcome = null,
                canGenerate = markers.isNotEmpty()
            )
        }
    }

    fun setTrigger(trigger: String) {
        updateState { state ->
            state.copy(selectedTrigger = trigger, outcome = null)
        }
    }

    fun toggleAssociatedSymptom(label: String, checked: Boolean) {
        updateState { state ->
            val next = state.selectedAssociatedSymptoms.toMutableList().apply {
                if (checked) {
                    if (!contains(label)) add(label)
                } else {
                    remove(label)
                }
            }
            state.copy(selectedAssociatedSymptoms = next, outcome = null)
        }
    }

    fun setAdditionalNote(note: String) {
        updateState { state ->
            if (state.additionalNote == note) state else state.copy(additionalNote = note, outcome = null)
        }
    }

    fun generateOutcome() {
        val state = _uiState.value ?: return
        if (state.selectedMarkers.isEmpty()) {
            _toastEvent.value = app.getString(R.string.symptom_toast_need_selection)
            return
        }

        val riskLevel = determineRiskLevel(state.selectedMarkers, state.selectedAssociatedSymptoms)
        val primaryZone = primaryZone(state.selectedMarkers)
        val outcome = SymptomCheckOutcome(
            riskLevel = riskLevel,
            riskTitle = riskTitle(riskLevel),
            riskSummary = riskSummary(primaryZone, riskLevel, state.selectedMarkers),
            suspectedDirections = buildSuspectedDirections(primaryZone, riskLevel, state),
            evidenceSummary = buildEvidenceSummary(
                markers = state.selectedMarkers,
                trigger = state.selectedTrigger,
                associatedSymptoms = state.selectedAssociatedSymptoms,
                note = state.additionalNote
            ),
            deviceEvidence = state.deviceEvidence,
            suggestedDepartment = suggestedDepartment(primaryZone, riskLevel),
            suggestedChecks = suggestedChecks(primaryZone, riskLevel),
            nextSteps = buildNextSteps(primaryZone, riskLevel),
            doctorPrefill = buildDoctorPrefill(
                markers = state.selectedMarkers,
                trigger = state.selectedTrigger,
                associatedSymptoms = state.selectedAssociatedSymptoms,
                note = state.additionalNote,
                riskLevel = riskLevel
            ),
            supportAction = buildSupportAction(primaryZone, riskLevel),
            disclaimer = app.getString(R.string.symptom_disclaimer)
        )
        updateState { current -> current.copy(outcome = outcome) }
    }

    fun startSupportAction() {
        val outcome = _uiState.value?.outcome ?: return
        if (!outcome.supportAction.enabled) {
            _toastEvent.value = app.getString(R.string.symptom_support_blocked_toast)
            return
        }

        viewModelScope.launch {
            val primaryZone = primaryZone(_uiState.value?.selectedMarkers.orEmpty())
            val now = System.currentTimeMillis()
            val task = InterventionTaskEntity(
                id = UUID.randomUUID().toString(),
                date = startOfDay(now),
                sourceType = InterventionTaskSourceType.RULE_ENGINE.name,
                triggerReason = outcome.supportAction.reason,
                bodyZone = primaryZone.name,
                protocolType = outcome.supportAction.protocolType,
                durationSec = outcome.supportAction.durationSec,
                plannedAt = now,
                status = InterventionTaskStatus.PENDING.name,
                createdAt = now,
                updatedAt = now
            )
            interventionRepository.upsertTask(task)
            syncTaskAsync(task)
            _launchCommand.postValue(
                SymptomGuideLaunchCommand(
                    protocolType = task.protocolType,
                    durationSec = task.durationSec,
                    taskId = task.id
                )
            )
        }
    }

    private fun observeLatestMetrics() {
        viewModelScope.launch {
            repository.getLatestMetricsFlow().collectLatest { entity ->
                latestMetrics = entity
                val deviceEvidence = buildDeviceEvidence(entity)
                updateState { state ->
                    state.copy(
                        deviceEvidence = deviceEvidence,
                        outcome = state.outcome?.copy(deviceEvidence = deviceEvidence)
                    )
                }
            }
        }
    }

    private fun addMarker(marker: SelectedBodyMarker) {
        updateState { state ->
            val deduplicated = state.selectedMarkers
                .filterNot {
                    it.zone == marker.zone &&
                        it.surfaceSide == marker.surfaceSide &&
                        it.symptomLabel == marker.symptomLabel
                } + marker
            state.copy(
                selectedMarkers = deduplicated,
                outcome = null,
                canGenerate = deduplicated.isNotEmpty()
            )
        }
    }

    private fun buildInitialState(): SymptomGuideUiState {
        return SymptomGuideUiState(
            redFlagHints = listOf(
                app.getString(R.string.symptom_red_flag_chest_pain),
                app.getString(R.string.symptom_red_flag_breath),
                app.getString(R.string.symptom_red_flag_consciousness),
                app.getString(R.string.symptom_red_flag_fever)
            ),
            quickSymptoms = listOf(
                SymptomTag(app.getString(R.string.symptom_quick_headache), SymptomBodyZone.HEAD),
                SymptomTag(app.getString(R.string.symptom_quick_chest_tightness), SymptomBodyZone.CHEST),
                SymptomTag(app.getString(R.string.symptom_quick_temple_pain), SymptomBodyZone.HEAD),
                SymptomTag(app.getString(R.string.symptom_quick_abdominal_pain), SymptomBodyZone.ABDOMEN),
                SymptomTag(app.getString(R.string.symptom_quick_wrist_pain), SymptomBodyZone.LIMB),
                SymptomTag(app.getString(R.string.symptom_quick_knee_pain), SymptomBodyZone.LIMB),
                SymptomTag(app.getString(R.string.symptom_quick_sweating), SymptomBodyZone.CHEST)
            ),
            triggerOptions = listOf(
                app.getString(R.string.symptom_trigger_stress),
                app.getString(R.string.symptom_trigger_exercise),
                app.getString(R.string.symptom_trigger_food),
                app.getString(R.string.symptom_trigger_night),
                app.getString(R.string.symptom_trigger_unknown)
            ),
            associatedSymptomOptions = listOf(
                app.getString(R.string.symptom_assoc_fatigue),
                app.getString(R.string.symptom_assoc_dizziness),
                app.getString(R.string.symptom_assoc_nausea),
                app.getString(R.string.symptom_assoc_cough),
                app.getString(R.string.symptom_assoc_insomnia),
                app.getString(R.string.symptom_assoc_fever)
            ),
            deviceEvidence = buildDeviceEvidence(latestMetrics)
        )
    }

    private fun determineRiskLevel(
        markers: List<SelectedBodyMarker>,
        associatedSymptoms: List<String>
    ): SymptomRiskLevel {
        val labels = markers.map { it.symptomLabel }
        val maxSeverity = markers.maxOfOrNull { it.severity } ?: 0
        val hasRedFlagLabel = labels.any { label ->
            label.contains(app.getString(R.string.symptom_red_flag_chest_pain)) ||
                label.contains(app.getString(R.string.symptom_red_flag_breath)) ||
                label.contains(app.getString(R.string.symptom_red_flag_consciousness)) ||
                label.contains(app.getString(R.string.symptom_red_flag_fever))
        }
        val hasChestRisk = markers.any {
            it.zone == SymptomBodyZone.CHEST &&
                (it.symptomLabel.contains(app.getString(R.string.symptom_quick_chest_tightness)) ||
                    it.symptomLabel.contains(app.getString(R.string.symptom_red_flag_breath)) ||
                    it.symptomLabel.contains("胸痛"))
        }
        val hasHeadRisk = markers.any {
            it.zone == SymptomBodyZone.HEAD &&
                (it.symptomLabel.contains(app.getString(R.string.symptom_red_flag_consciousness)) ||
                    it.symptomLabel.contains(app.getString(R.string.symptom_quick_dizziness)) ||
                    it.symptomLabel.contains(app.getString(R.string.symptom_quick_headache)))
        }
        return when {
            hasRedFlagLabel -> SymptomRiskLevel.HIGH
            hasChestRisk && maxSeverity >= 7 -> SymptomRiskLevel.HIGH
            hasHeadRisk && maxSeverity >= 8 -> SymptomRiskLevel.HIGH
            associatedSymptoms.contains(app.getString(R.string.symptom_assoc_fever)) && maxSeverity >= 7 ->
                SymptomRiskLevel.HIGH
            maxSeverity >= 7 -> SymptomRiskLevel.MEDIUM
            labels.any {
                it.contains(app.getString(R.string.symptom_quick_chest_tightness)) ||
                    it.contains(app.getString(R.string.symptom_quick_abdominal_pain)) ||
                    it.contains(app.getString(R.string.symptom_quick_dizziness))
            } -> SymptomRiskLevel.MEDIUM
            associatedSymptoms.contains(app.getString(R.string.symptom_assoc_fever)) -> SymptomRiskLevel.MEDIUM
            else -> SymptomRiskLevel.LOW
        }
    }

    private fun primaryZone(markers: List<SelectedBodyMarker>): SymptomBodyZone {
        if (markers.isEmpty()) return SymptomBodyZone.CHEST
        val counts = mutableMapOf<SymptomBodyZone, Int>()
        markers.forEach { marker ->
            counts[marker.zone] = (counts[marker.zone] ?: 0) + 1
        }
        return counts.maxByOrNull { it.value }?.key ?: SymptomBodyZone.CHEST
    }

    private fun buildSuspectedDirections(
        primaryZone: SymptomBodyZone,
        riskLevel: SymptomRiskLevel,
        state: SymptomGuideUiState
    ): List<SymptomSuspectedDirection> {
        val stressTrigger = state.selectedTrigger == app.getString(R.string.symptom_trigger_stress)
        val foodTrigger = state.selectedTrigger == app.getString(R.string.symptom_trigger_food)

        return when (primaryZone) {
            SymptomBodyZone.HEAD -> listOf(
                SymptomSuspectedDirection(
                    title = "紧张性头痛或睡眠不足",
                    reason = "头部不适常和压力、睡眠不足、长时间盯屏或节律紊乱叠加出现，可继续补充起始时间和加重场景。",
                    confidenceLabel = if (stressTrigger) "依据较强" else "可继续补充"
                ),
                SymptomSuspectedDirection(
                    title = "上呼吸道或鼻窦刺激",
                    reason = "若伴随鼻塞、咽部不适、发热或晨起更明显，需要进一步区分感冒、过敏或鼻窦相关问题。",
                    confidenceLabel = "可继续补充"
                ),
                SymptomSuspectedDirection(
                    title = if (riskLevel == SymptomRiskLevel.HIGH) "需优先排除神经系统风险" else "需继续排除神经系统问题",
                    reason = "若同时出现意识改变、持续加重、明显眩晕、肢体无力或剧烈头痛，建议尽快转为线下评估。",
                    confidenceLabel = "需结合问诊"
                )
            )

            SymptomBodyZone.CHEST -> listOf(
                SymptomSuspectedDirection(
                    title = if (riskLevel == SymptomRiskLevel.HIGH) "需优先排除心肺急性风险" else "胸壁紧张或呼吸节律紊乱",
                    reason = if (riskLevel == SymptomRiskLevel.HIGH) {
                        "胸部不适达到较高强度、伴呼吸困难或红旗症状时，应优先排除心肺急性问题。"
                    } else {
                        "压力、久坐、过度警觉或呼吸节律失衡时，胸壁肌肉紧张和主观胸闷较常见。"
                    },
                    confidenceLabel = "依据较强"
                ),
                SymptomSuspectedDirection(
                    title = "上呼吸道刺激或轻度气道不适",
                    reason = "若伴随咳嗽、发热、夜间加重或活动后气促，需要继续补充呼吸道相关症状。",
                    confidenceLabel = "可继续补充"
                ),
                SymptomSuspectedDirection(
                    title = "压力相关自主神经波动",
                    reason = "如果本次不适和压力、睡眠差或情绪波动同步出现，可继续通过 AI 问诊区分身心因素与器质性问题。",
                    confidenceLabel = if (stressTrigger) "依据较强" else "需结合问诊"
                )
            )

            SymptomBodyZone.ABDOMEN -> listOf(
                SymptomSuspectedDirection(
                    title = "功能性胃肠不适",
                    reason = "腹部不适在作息紊乱、压力波动或饮食节律不稳定时较常见，需要结合持续时间和排便情况判断。",
                    confidenceLabel = "依据较强"
                ),
                SymptomSuspectedDirection(
                    title = "饮食相关刺激",
                    reason = "若和进食、熬夜、辛辣或酒精相关，建议优先记录最近饮食和症状发生顺序。",
                    confidenceLabel = if (foodTrigger) "依据较强" else "可继续补充"
                ),
                SymptomSuspectedDirection(
                    title = "腹壁或核心紧张",
                    reason = "运动后、久坐后或姿势负荷增加时，腹壁与核心紧张也会带来局部不适。",
                    confidenceLabel = "需结合问诊"
                )
            )

            SymptomBodyZone.LIMB -> listOf(
                SymptomSuspectedDirection(
                    title = "肌肉劳损或恢复不足",
                    reason = "四肢酸痛在训练后、久站久走后或恢复睡眠不足时较常见，优先评估最近负荷变化。",
                    confidenceLabel = "依据较强"
                ),
                SymptomSuspectedDirection(
                    title = "关节或软组织轻度劳损",
                    reason = "若活动时更明显、休息后缓解，可继续补充受力动作、扭转、负重或重复使用情况。",
                    confidenceLabel = "可继续补充"
                ),
                SymptomSuspectedDirection(
                    title = "需排除循环或末梢神经问题",
                    reason = "若伴随麻木、明显无力、发冷、肿胀或持续恶化，建议尽快转入线下评估。",
                    confidenceLabel = if (riskLevel == SymptomRiskLevel.HIGH) "依据较强" else "需结合问诊"
                )
            )
        }
    }

    private fun buildSupportAction(
        primaryZone: SymptomBodyZone,
        riskLevel: SymptomRiskLevel
    ): SymptomSupportAction {
        if (riskLevel == SymptomRiskLevel.HIGH) {
            return SymptomSupportAction(
                enabled = false,
                label = app.getString(R.string.symptom_action_support_blocked),
                reason = app.getString(R.string.symptom_support_high_risk)
            )
        }

        return when (primaryZone) {
            SymptomBodyZone.HEAD -> SymptomSupportAction(
                enabled = true,
                label = app.getString(R.string.symptom_action_support),
                protocolType = "BREATH_4_7_8",
                durationSec = 240,
                reason = "头部不适场景下，先用低负担呼吸节律降低紧张与唤醒水平。"
            )
            SymptomBodyZone.CHEST -> SymptomSupportAction(
                enabled = true,
                label = app.getString(R.string.symptom_action_support),
                protocolType = "BREATH_4_6",
                durationSec = 300,
                reason = "胸部不适优先使用慢呼气节律，帮助稳定主观紧张和呼吸节奏。"
            )
            SymptomBodyZone.ABDOMEN -> SymptomSupportAction(
                enabled = true,
                label = app.getString(R.string.symptom_action_support),
                protocolType = "BOX",
                durationSec = 240,
                reason = "腹部不适优先选低负担节律训练，避免过快或过深呼吸造成刺激。"
            )
            SymptomBodyZone.LIMB -> SymptomSupportAction(
                enabled = true,
                label = app.getString(R.string.symptom_action_support),
                protocolType = "BREATH_4_6",
                durationSec = 180,
                reason = "四肢酸痛或疲劳可先配合缓和呼吸，降低整体紧张和恢复负荷。"
            )
        }
    }

    private fun riskTitle(riskLevel: SymptomRiskLevel): String {
        return when (riskLevel) {
            SymptomRiskLevel.HIGH -> app.getString(R.string.symptom_risk_high)
            SymptomRiskLevel.MEDIUM -> app.getString(R.string.symptom_risk_medium)
            SymptomRiskLevel.LOW -> app.getString(R.string.symptom_risk_low)
        }
    }

    private fun riskSummary(
        primaryZone: SymptomBodyZone,
        riskLevel: SymptomRiskLevel,
        markers: List<SelectedBodyMarker>
    ): String {
        val severity = markers.maxOfOrNull { it.severity } ?: 0
        return when (riskLevel) {
            SymptomRiskLevel.HIGH ->
                "当前症状存在红旗提示或高强度不适，建议优先线下就医，不要只依赖自我训练。"
            SymptomRiskLevel.MEDIUM ->
                "当前更适合在 24 小时内完成进一步评估，同时记录变化。当前最高主观强度约为 $severity / 10。"
            SymptomRiskLevel.LOW ->
                "${zoneLabel(primaryZone)}目前更像轻中度不适，可先居家观察并做低负担缓解。"
        }
    }

    private fun buildEvidenceSummary(
        markers: List<SelectedBodyMarker>,
        trigger: String,
        associatedSymptoms: List<String>,
        note: String
    ): String {
        val markerSummary = markers.joinToString("；") { marker ->
            "${surfaceLabel(marker.surfaceSide)}${zoneLabel(marker.zone)}·${marker.symptomLabel}（${marker.severity}/10，${marker.durationLabel}）"
        }
        val parts = mutableListOf("部位与症状：$markerSummary")
        if (trigger.isNotBlank()) parts += "诱因：$trigger"
        if (associatedSymptoms.isNotEmpty()) parts += "伴随表现：${associatedSymptoms.joinToString("、")}"
        if (note.isNotBlank()) parts += "补充说明：$note"
        return parts.joinToString("\n")
    }

    private fun buildDoctorPrefill(
        markers: List<SelectedBodyMarker>,
        trigger: String,
        associatedSymptoms: List<String>,
        note: String,
        riskLevel: SymptomRiskLevel
    ): String {
        val mainComplaint = markers.joinToString("；") { marker ->
            "${surfaceLabel(marker.surfaceSide)}${zoneLabel(marker.zone)}出现${marker.symptomLabel}，强度${marker.severity}/10，持续${marker.durationLabel}"
        }
        val triggerPart = if (trigger.isBlank()) "诱因暂不明确" else "可能诱因是$trigger"
        val associatedPart = if (associatedSymptoms.isEmpty()) {
            "目前没有补充明显伴随表现"
        } else {
            "伴随${associatedSymptoms.joinToString("、")}"
        }
        val notePart = if (note.isBlank()) "" else "。补充说明：$note"
        return "主诉摘要：$mainComplaint。$triggerPart。$associatedPart。当前风险等级为${riskTitle(riskLevel)}$notePart。请继续追问并生成结构化问诊单。"
    }

    private fun suggestedDepartment(primaryZone: SymptomBodyZone, riskLevel: SymptomRiskLevel): String {
        return when (primaryZone) {
            SymptomBodyZone.HEAD -> if (riskLevel == SymptomRiskLevel.HIGH) "急诊 / 神经内科" else "全科 / 神经内科"
            SymptomBodyZone.CHEST -> if (riskLevel == SymptomRiskLevel.HIGH) "急诊 / 心内科 / 呼吸科" else "全科 / 呼吸科"
            SymptomBodyZone.ABDOMEN -> if (riskLevel == SymptomRiskLevel.HIGH) "急诊 / 消化内科" else "全科 / 消化内科"
            SymptomBodyZone.LIMB -> if (riskLevel == SymptomRiskLevel.HIGH) "急诊 / 骨科" else "全科 / 康复医学科"
        }
    }

    private fun suggestedChecks(primaryZone: SymptomBodyZone, riskLevel: SymptomRiskLevel): String {
        return when (primaryZone) {
            SymptomBodyZone.HEAD -> if (riskLevel == SymptomRiskLevel.HIGH) {
                "生命体征、体温、神经系统查体，必要时做进一步头颅评估。"
            } else {
                "血压、睡眠与压力问诊，以及基础神经系统查体。"
            }
            SymptomBodyZone.CHEST -> if (riskLevel == SymptomRiskLevel.HIGH) {
                "生命体征、心电图、血氧和胸部评估。"
            } else {
                "血氧、呼吸频率、胸部听诊和进一步问诊。"
            }
            SymptomBodyZone.ABDOMEN -> if (riskLevel == SymptomRiskLevel.HIGH) {
                "腹部查体、体温、血常规，必要时腹部影像。"
            } else {
                "饮食史、腹部查体、排便变化和消化症状问诊。"
            }
            SymptomBodyZone.LIMB -> if (riskLevel == SymptomRiskLevel.HIGH) {
                "患肢查体、循环/感觉评估，必要时影像检查。"
            } else {
                "关节活动度、训练负荷、恢复情况和疼痛记录。"
            }
        }
    }

    private fun buildNextSteps(primaryZone: SymptomBodyZone, riskLevel: SymptomRiskLevel): List<String> {
        return when (riskLevel) {
            SymptomRiskLevel.HIGH -> listOf(
                "优先线下就医或急诊评估，先不要只依赖自我训练。",
                "减少活动并持续观察症状变化，如有报告可立即上传。",
                "进入 AI 问诊页补充主诉、持续时间、诱因和伴随表现。"
            )
            SymptomRiskLevel.MEDIUM -> listOf(
                "建议在 24 小时内完成进一步评估，并记录症状变化。",
                "继续 AI 问诊，补充病程、诱因和伴随表现。",
                if (primaryZone == SymptomBodyZone.CHEST) {
                    "若出现胸痛、气促加重或意识异常，立即改走线下就医。"
                } else {
                    "如症状持续加重、范围扩大或出现红旗症状，及时线下就医。"
                }
            )
            SymptomRiskLevel.LOW -> listOf(
                "可先居家观察 24 至 48 小时，记录诱因和变化。",
                "优先选择低负担放松或康复动作，避免高刺激训练。",
                "如症状未缓解、范围扩大或出现红旗症状，及时转入线下评估。"
            )
        }
    }

    private fun buildDeviceEvidence(entity: HealthMetricsEntity?): String {
        if (entity == null) {
            return app.getString(R.string.symptom_device_evidence_missing)
        }
        val segments = mutableListOf<String>()
        if (entity.heartRateSample > 0) segments += "心率 ${entity.heartRateSample} bpm"
        if (entity.hrvCurrent > 0) segments += "HRV ${entity.hrvCurrent} ms"
        if (entity.bloodOxygenSample > 0) segments += "血氧 ${entity.bloodOxygenSample}%"
        if (entity.accMagnitudeSample > 0f) {
            segments += "活动 ${String.format(Locale.getDefault(), "%.1f", entity.accMagnitudeSample)}"
        }
        return if (segments.isEmpty()) {
            app.getString(R.string.symptom_device_evidence_missing)
        } else {
            app.getString(R.string.symptom_device_evidence_format, segments.joinToString(" / "))
        }
    }

    private fun inferZoneFromSymptom(label: String): SymptomBodyZone {
        return when {
            label.contains("头") || label.contains("太阳穴") || label.contains("意识") || label.contains("发热") ->
                SymptomBodyZone.HEAD
            label.contains("胸") || label.contains("呼吸") || label.contains("出汗") ->
                SymptomBodyZone.CHEST
            label.contains("腹") || label.contains("胃") || label.contains("恶心") ->
                SymptomBodyZone.ABDOMEN
            else -> SymptomBodyZone.LIMB
        }
    }

    private fun defaultSeverityFor(label: String): Int {
        return when {
            label.contains("困难") || label.contains("意识") -> 8
            label.contains("痛") || label.contains("闷") -> 7
            else -> 5
        }
    }

    private fun zoneLabel(zone: SymptomBodyZone): String {
        return when (zone) {
            SymptomBodyZone.HEAD -> app.getString(R.string.symptom_zone_head)
            SymptomBodyZone.CHEST -> app.getString(R.string.symptom_zone_chest)
            SymptomBodyZone.ABDOMEN -> app.getString(R.string.symptom_zone_abdomen)
            SymptomBodyZone.LIMB -> app.getString(R.string.symptom_zone_limb)
        }
    }

    private fun surfaceLabel(side: SurfaceSide): String {
        return when (side) {
            SurfaceSide.FRONT -> app.getString(R.string.symptom_surface_front_short)
            SurfaceSide.BACK -> app.getString(R.string.symptom_surface_back_short)
        }
    }

    private fun syncTaskAsync(task: InterventionTaskEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { syncTaskIfAvailable(task) }
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

    private fun startOfDay(timestamp: Long): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private inline fun updateState(transform: (SymptomGuideUiState) -> SymptomGuideUiState) {
        val current = _uiState.value ?: buildInitialState()
        _uiState.value = transform(current)
    }
}
