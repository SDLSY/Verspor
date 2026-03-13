package com.example.newstart.ui.relax

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.newstart.R
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
        val markerLabels = markers.map { it.symptomLabel }
        val maxSeverity = markers.maxOfOrNull { it.severity } ?: 0
        val hasRedFlagLabel = markerLabels.any { label ->
            label.contains("胸痛") ||
                label.contains("呼吸困难") ||
                label.contains("呼吸不畅") ||
                label.contains("意识异常") ||
                label.contains("持续高热")
        }
        val hasChestRisk = markers.any {
            it.zone == SymptomBodyZone.CHEST && (
                it.symptomLabel.contains("胸") || it.symptomLabel.contains("呼吸")
                )
        }
        val hasHeadRisk = markers.any {
            it.zone == SymptomBodyZone.HEAD && (
                it.symptomLabel.contains("意识") || it.symptomLabel.contains("眩晕")
                )
        }
        return when {
            hasRedFlagLabel -> SymptomRiskLevel.HIGH
            hasChestRisk && maxSeverity >= 7 -> SymptomRiskLevel.HIGH
            hasHeadRisk && maxSeverity >= 8 -> SymptomRiskLevel.HIGH
            associatedSymptoms.contains(app.getString(R.string.symptom_assoc_fever)) && maxSeverity >= 7 ->
                SymptomRiskLevel.HIGH
            maxSeverity >= 7 -> SymptomRiskLevel.MEDIUM
            markers.any {
                it.symptomLabel.contains("胸闷") ||
                    it.symptomLabel.contains("腹痛") ||
                    it.symptomLabel.contains("头晕")
            } -> SymptomRiskLevel.MEDIUM
            associatedSymptoms.contains(app.getString(R.string.symptom_assoc_fever)) -> SymptomRiskLevel.MEDIUM
            else -> SymptomRiskLevel.LOW
        }
    }

    private fun primaryZone(markers: List<SelectedBodyMarker>): SymptomBodyZone {
        if (markers.isEmpty()) return SymptomBodyZone.CHEST
        val counts = mutableMapOf<SymptomBodyZone, Int>()
        for (marker in markers) {
            counts[marker.zone] = (counts[marker.zone] ?: 0) + 1
        }
        return counts.maxByOrNull { it.value }?.key ?: SymptomBodyZone.CHEST
    }

    private fun buildSuspectedDirections(
        primaryZone: SymptomBodyZone,
        riskLevel: SymptomRiskLevel,
        state: SymptomGuideUiState
    ): List<SymptomSuspectedDirection> {
        val trigger = state.selectedTrigger
        return when (primaryZone) {
            SymptomBodyZone.HEAD -> listOf(
                SymptomSuspectedDirection("紧张相关头颈不适", "头颈不适与压力、睡眠不足或长时间用眼时常见重叠，需要继续补充诱因。", "依据较强"),
                SymptomSuspectedDirection("上呼吸道或鼻咽刺激", "若同时有咽痛、鼻塞、发热或夜间加重，可继续追问感染或过敏相关线索。", "可继续补充"),
                SymptomSuspectedDirection(
                    if (riskLevel == SymptomRiskLevel.HIGH) "需优先排除神经系统风险" else "需继续排除神经系统问题",
                    "若伴随意识变化、步态异常、持续加重或高强度头痛，应尽快转入线下评估。",
                    "需结合问诊"
                )
            )

            SymptomBodyZone.CHEST -> listOf(
                SymptomSuspectedDirection(
                    if (riskLevel == SymptomRiskLevel.HIGH) "心肺相关风险需优先排除" else "胸廓肌肉紧张或呼吸节律失衡",
                    if (riskLevel == SymptomRiskLevel.HIGH) {
                        "胸部不适合并高强度或红旗症状时，应先排除心肺急性问题。"
                    } else {
                        "压力后、久坐后或呼吸节律紊乱时，胸廓肌肉紧张和自主神经波动较常见。"
                    },
                    "依据较强"
                ),
                SymptomSuspectedDirection("上呼吸道刺激或轻度呼吸道不适", "若有咳嗽、气促、夜间加重或发热，需继续区分气道刺激与感染相关问题。", "可继续补充"),
                SymptomSuspectedDirection(
                    "压力相关自主神经波动",
                    "若本次不适与压力、失眠或情绪波动同步出现，可继续通过 AI 问诊做区分。",
                    if (trigger == app.getString(R.string.symptom_trigger_stress)) "依据较强" else "需结合问诊"
                )
            )

            SymptomBodyZone.ABDOMEN -> listOf(
                SymptomSuspectedDirection("功能性胃肠不适", "腹部不适在进食后、作息紊乱或压力波动时常见，需要结合持续时间和伴随症状判断。", "依据较强"),
                SymptomSuspectedDirection(
                    "饮食或作息刺激",
                    "若与进食、熬夜或刺激性食物相关，可优先记录饮食和排便变化。",
                    if (trigger == app.getString(R.string.symptom_trigger_food)) "依据较强" else "可继续补充"
                ),
                SymptomSuspectedDirection("腹壁或核心紧张", "运动后、久坐后或姿势负荷增加时，腹壁与核心紧张也会引发局部不适。", "需结合问诊")
            )

            SymptomBodyZone.LIMB -> listOf(
                SymptomSuspectedDirection("肌肉过用或恢复不足", "肢体不适在训练后、久走久站后或睡眠恢复不足时较常见。", "依据较强"),
                SymptomSuspectedDirection("关节或软组织轻度劳损", "若活动时明显、休息后缓解，可继续补充受力动作和持续时间。", "可继续补充"),
                SymptomSuspectedDirection(
                    "循环或末梢神经不适",
                    "若伴随麻木、冰凉、进行性无力或明显肿胀，应尽快转入线下评估。",
                    if (riskLevel == SymptomRiskLevel.HIGH) "依据较强" else "需结合问诊"
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
            SymptomBodyZone.HEAD -> SymptomSupportAction(true, app.getString(R.string.symptom_action_support), "BREATH_4_7_8", 240, "头颈不适场景下，先做缓和呼吸以降低唤醒水平。")
            SymptomBodyZone.CHEST -> SymptomSupportAction(true, app.getString(R.string.symptom_action_support), "BREATH_4_6", 300, "胸部不适优先使用慢呼气节律，帮助稳定主观紧张与呼吸节律。")
            SymptomBodyZone.ABDOMEN -> SymptomSupportAction(true, app.getString(R.string.symptom_action_support), "BOX", 240, "腹部不适优先采用低负担节律训练，避免过快呼吸。")
            SymptomBodyZone.LIMB -> SymptomSupportAction(true, app.getString(R.string.symptom_action_support), "BREATH_4_6", 180, "肢体疲劳或酸痛可先配合缓和呼吸，降低整体紧张与恢复负荷。")
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
            SymptomRiskLevel.HIGH -> "当前症状存在红旗提示或高强度不适，建议优先线下就医，不要只依赖自我训练。"
            SymptomRiskLevel.MEDIUM -> "当前更适合在 24 小时内做进一步评估，同时记录变化。当前最高主观强度约 $severity / 10。"
            SymptomRiskLevel.LOW -> "${zoneLabel(primaryZone)}目前更像轻中度不适，可先居家观察并做低负担缓解。"
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
        val associatedPart = if (associatedSymptoms.isEmpty()) "目前没有补充明显伴随表现" else "伴随${associatedSymptoms.joinToString("、")}"
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
            SymptomBodyZone.HEAD -> if (riskLevel == SymptomRiskLevel.HIGH) "生命体征、体温、神经系统查体，必要时头颈部进一步评估" else "血压、睡眠与压力问诊、神经系统基础查体"
            SymptomBodyZone.CHEST -> if (riskLevel == SymptomRiskLevel.HIGH) "生命体征、心电图、血氧与胸部评估" else "血氧、呼吸频率、胸部听诊或进一步问诊"
            SymptomBodyZone.ABDOMEN -> if (riskLevel == SymptomRiskLevel.HIGH) "腹部查体、体温、血常规，必要时腹部影像" else "饮食史、腹部查体、排便与消化症状问诊"
            SymptomBodyZone.LIMB -> if (riskLevel == SymptomRiskLevel.HIGH) "患肢查体、循环/感觉评估，必要时影像" else "关节活动度、训练负荷、恢复与疼痛记录"
        }
    }

    private fun buildNextSteps(primaryZone: SymptomBodyZone, riskLevel: SymptomRiskLevel): List<String> {
        return when (riskLevel) {
            SymptomRiskLevel.HIGH -> listOf(
                "优先线下就医或急诊评估，先不要依赖自我训练。",
                "减少活动并持续观察症状变化，如有报告可立即上传。",
                "进入 AI 问诊页补充主诉、持续时间、诱因和伴随表现。"
            )

            SymptomRiskLevel.MEDIUM -> listOf(
                "建议在 24 小时内完成进一步评估，并记录症状变化。",
                "继续 AI 问诊补充病程、诱因和伴随表现。",
                if (primaryZone == SymptomBodyZone.CHEST) "若出现胸痛、气促加重或意识异常，立即改走线下就医。" else "如症状持续加重、强度升高或出现红旗症状，及时线下就医。"
            )

            SymptomRiskLevel.LOW -> listOf(
                "可先居家观察 24-48 小时，记录诱因和变化。",
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
            label.contains("头") || label.contains("晕") || label.contains("意识") || label.contains("太阳穴") || label.contains("热") || label.contains("发热") ->
                SymptomBodyZone.HEAD
            label.contains("胸") || label.contains("呼吸") || label.contains("出汗") -> SymptomBodyZone.CHEST
            label.contains("腹") || label.contains("胃") || label.contains("恶心") -> SymptomBodyZone.ABDOMEN
            else -> SymptomBodyZone.LIMB
        }
    }

    private fun defaultSeverityFor(label: String): Int {
        return when {
            label.contains("痛") -> 7
            label.contains("困难") || label.contains("气促") -> 8
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
