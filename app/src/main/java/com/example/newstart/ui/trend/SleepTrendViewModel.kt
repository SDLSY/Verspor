package com.example.newstart.ui.trend

import android.app.Application
import android.graphics.Color
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.newstart.database.AppDatabase
import com.example.newstart.database.entity.InterventionExecutionEntity
import com.example.newstart.database.entity.InterventionTaskEntity
import com.example.newstart.intervention.InterventionProtocolCatalog
import com.example.newstart.intervention.PersonalizationLevel
import com.example.newstart.intervention.PersonalizationMissingInput
import com.example.newstart.intervention.PrescriptionItemType
import com.example.newstart.network.models.PeriodSummaryActionItem
import com.example.newstart.network.models.PeriodSummaryData
import com.example.newstart.repository.InterventionProfileRepository
import com.example.newstart.repository.MedicalReportRepository
import com.example.newstart.repository.NetworkRepository
import com.example.newstart.repository.SleepRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import com.example.newstart.R
import com.example.newstart.ui.intervention.InterventionActionUiModel

class SleepTrendViewModel(application: Application) : AndroidViewModel(application) {

    private data class TimedValue(val timestamp: Long, val value: Float)

    private data class MetricFilterConfig(
        val hardMin: Float,
        val hardMax: Float,
        val maxStepDelta: Float,
        val madThreshold: Float = 3.5f,
        val maxPoints: Int = 120
    )

    private val db = AppDatabase.getDatabase(application)
    private val repository = SleepRepository(
        db.sleepDataDao(),
        db.healthMetricsDao(),
        db.recoveryScoreDao(),
        db.ppgSampleDao()
    )
    private val networkRepository = NetworkRepository()
    private val profileRepository = InterventionProfileRepository(application, db)
    private val medicalReportRepository = MedicalReportRepository(db.medicalReportDao(), db.medicalMetricDao())
    private val taskDao = db.interventionTaskDao()
    private val executionDao = db.interventionExecutionDao()

    private var sleepJob: Job? = null
    private var recoveryJob: Job? = null
    private var metricsJob: Job? = null
    private var ppgJob: Job? = null

    private val _sleepDurationData = MutableLiveData<Pair<List<String>, List<Float>>>()
    val sleepDurationData: LiveData<Pair<List<String>, List<Float>>> = _sleepDurationData

    private val _recoveryScoreData = MutableLiveData<Pair<List<String>, List<Float>>>()
    val recoveryScoreData: LiveData<Pair<List<String>, List<Float>>> = _recoveryScoreData

    private val _sleepQualityDistribution = MutableLiveData<Map<String, Float>>()
    val sleepQualityDistribution: LiveData<Map<String, Float>> = _sleepQualityDistribution

    private val _statistics = MutableLiveData<SleepStatistics>()
    val statistics: LiveData<SleepStatistics> = _statistics

    private val _heartRateTrendData = MutableLiveData<Pair<List<String>, List<Float>>>()
    val heartRateTrendData: LiveData<Pair<List<String>, List<Float>>> = _heartRateTrendData

    private val _bloodOxygenTrendData = MutableLiveData<Pair<List<String>, List<Float>>>()
    val bloodOxygenTrendData: LiveData<Pair<List<String>, List<Float>>> = _bloodOxygenTrendData

    private val _temperatureTrendData = MutableLiveData<Pair<List<String>, List<Float>>>()
    val temperatureTrendData: LiveData<Pair<List<String>, List<Float>>> = _temperatureTrendData

    private val _motionTrendData = MutableLiveData<Pair<List<String>, List<Float>>>()
    val motionTrendData: LiveData<Pair<List<String>, List<Float>>> = _motionTrendData

    private val _ppgTrendData = MutableLiveData<Pair<List<String>, List<Float>>>()
    val ppgTrendData: LiveData<Pair<List<String>, List<Float>>> = _ppgTrendData

    private val _periodReport = MutableLiveData<HealthPeriodReportUiModel>()
    val periodReport: LiveData<HealthPeriodReportUiModel> = _periodReport

    fun loadTrendData(timeRange: TimeRange) {
        val days = when (timeRange) {
            TimeRange.LAST_7_DAYS -> 7
            TimeRange.LAST_30_DAYS -> 30
        }
        val endTime = System.currentTimeMillis()
        val startTime = endTime - days.toLong() * 24L * 60L * 60L * 1000L

        sleepJob?.cancel()
        recoveryJob?.cancel()
        metricsJob?.cancel()
        ppgJob?.cancel()

        sleepJob = viewModelScope.launch {
            repository.getLastNDaysSleep(days).collect { sleepList ->
                if (sleepList.isNotEmpty()) {
                    loadRealTrendData(sleepList)
                } else {
                    _sleepDurationData.postValue(Pair(emptyList(), emptyList()))
                    _sleepQualityDistribution.postValue(emptyMap())
                }
            }
        }

        recoveryJob = viewModelScope.launch {
            repository.getLastNDaysRecoveryScore(days).collect { scoreList ->
                val labels = _sleepDurationData.value?.first ?: emptyList()
                val scores = stabilizeSeries(scoreList.map { it.score.toFloat().coerceIn(0f, 100f) })
                _recoveryScoreData.postValue(Pair(labels, scores))
            }
        }

        metricsJob = viewModelScope.launch(Dispatchers.Default) {
            val timeFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
            repository.getHealthMetricsSamplesByTimeRange(startTime, endTime).collectLatest { list ->
                if (list.isEmpty()) {
                    _heartRateTrendData.postValue(Pair(emptyList(), emptyList()))
                    _bloodOxygenTrendData.postValue(Pair(emptyList(), emptyList()))
                    _temperatureTrendData.postValue(Pair(emptyList(), emptyList()))
                    _motionTrendData.postValue(Pair(emptyList(), emptyList()))
                    return@collectLatest
                }

                val points = list.take(260).reversed()

                _heartRateTrendData.postValue(
                    buildFilteredSeries(
                        samples = points.mapNotNull {
                            val value = it.heartRateSample.toFloat()
                            if (value > 0f) TimedValue(it.timestamp, value) else null
                        },
                        config = MetricFilterConfig(40f, 210f, 25f),
                        timeFormat = timeFormat
                    )
                )

                _bloodOxygenTrendData.postValue(
                    buildFilteredSeries(
                        samples = points.mapNotNull {
                            val value = it.bloodOxygenSample.toFloat()
                            if (value > 0f) TimedValue(it.timestamp, value) else null
                        },
                        config = MetricFilterConfig(80f, 100f, 4f),
                        timeFormat = timeFormat
                    )
                )

                _temperatureTrendData.postValue(
                    buildFilteredSeries(
                        samples = points.mapNotNull {
                            val value = it.temperatureSample
                            if (value > 0f) TimedValue(it.timestamp, value) else null
                        },
                        config = MetricFilterConfig(30f, 42f, 0.8f),
                        timeFormat = timeFormat
                    )
                )

                _motionTrendData.postValue(
                    buildFilteredSeries(
                        samples = points.mapNotNull {
                            val value = normalizeMotionIntensity(it.accMagnitudeSample)
                            if (value > 0f) TimedValue(it.timestamp, value) else null
                        },
                        config = MetricFilterConfig(0f, 20f, 3.5f),
                        timeFormat = timeFormat
                    )
                )
            }
        }

        ppgJob = viewModelScope.launch(Dispatchers.Default) {
            val timeFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
            repository.getPpgSamplesByTimeRange(startTime, endTime).collectLatest { list ->
                if (list.isEmpty()) {
                    _ppgTrendData.postValue(Pair(emptyList(), emptyList()))
                    return@collectLatest
                }
                val points = list.take(500).reversed().filter { it.ppgValue > 0f }
                _ppgTrendData.postValue(
                    Pair(
                        points.map { timeFormat.format(Date(it.timestamp)) },
                        points.map { it.ppgValue }
                    )
                )
            }
        }

        viewModelScope.launch {
            _statistics.postValue(repository.getSleepStatistics(days))
        }

        viewModelScope.launch {
            val localReport = withContext(Dispatchers.IO) {
                buildLocalFallbackReport(timeRange)
            }
            _periodReport.postValue(localReport)

            val remotePeriod = if (timeRange == TimeRange.LAST_7_DAYS) "weekly" else "monthly"
            val remoteReport = withContext(Dispatchers.IO) {
                profileRepository.syncPersonalizationSupportIfPossible()
                networkRepository.getPeriodSummary(remotePeriod)
                    .getOrNull()
                    ?.let { mapRemoteReport(timeRange, it) }
            }
            if (remoteReport != null) {
                _periodReport.postValue(remoteReport)
            }
        }
    }

    private suspend fun buildLocalFallbackReport(timeRange: TimeRange): HealthPeriodReportUiModel {
        val days = if (timeRange == TimeRange.LAST_7_DAYS) 7 else 30
        val periodLabel = if (timeRange == TimeRange.LAST_7_DAYS) "本周" else "本月"
        val minSamples = if (timeRange == TimeRange.LAST_7_DAYS) 3 else 10
        val now = System.currentTimeMillis()
        val startTime = now - days.toLong() * 24L * 60L * 60L * 1000L

        val sleepHistory = repository.getLastNDaysSleep(days * 2).first()
        val recoveryHistory = repository.getLastNDaysRecoveryScore(days * 2).first()
        val currentSleep = sleepHistory.take(days)
        val previousSleep = sleepHistory.drop(days).take(days)
        val currentRecovery = recoveryHistory.take(days)
        val previousRecovery = recoveryHistory.drop(days).take(days)
        val executionSummary = executionDao.getExecutionSummary(startTime, now).first()
        val completionSummary = taskDao.getCompletionSummary(startTime, now).first()
        val viewData = profileRepository.getLatestViewData()
        val snapshot = viewData.snapshot
        val latestReport = medicalReportRepository.getLatestReport()
        val latestMetrics = latestReport?.let { medicalReportRepository.getMetricsByReport(it.id) }.orEmpty()
        val abnormalMetrics = latestMetrics.filter { it.isAbnormal }
        val recentTasks = taskDao.getRecent(24).filter { it.date in startTime..now }
        val recentExecutions = executionDao.getRecent(24).filter { it.endedAt in startTime..now }

        val avgSleepHours = currentSleep.map { it.totalSleepMinutes / 60f }.takeIf { it.isNotEmpty() }?.average()?.toFloat()
        val prevSleepHours = previousSleep.map { it.totalSleepMinutes / 60f }.takeIf { it.isNotEmpty() }?.average()?.toFloat()
        val avgRecovery = currentRecovery.map { it.score.toFloat() }.takeIf { it.isNotEmpty() }?.average()?.toFloat()
        val prevRecovery = previousRecovery.map { it.score.toFloat() }.takeIf { it.isNotEmpty() }?.average()?.toFloat()
        val adherenceRate = if (completionSummary.totalCount > 0) {
            completionSummary.completedCount.toFloat() / completionSummary.totalCount.toFloat()
        } else {
            null
        }

        val sleepScore = snapshot?.domainScores?.get("sleepDisturbance") ?: when {
            (avgSleepHours ?: 8f) < 5.5f -> 82
            (avgSleepHours ?: 8f) < 6.5f -> 68
            else -> 38
        }
        val fatigueScore = snapshot?.domainScores?.get("fatigueLoad") ?: when {
            (avgRecovery ?: 100f) < 35f -> 76
            (avgRecovery ?: 100f) < 50f -> 62
            else -> 35
        }
        val recoveryScore = snapshot?.domainScores?.get("recoveryCapacity") ?: when {
            (avgRecovery ?: 100f) < 35f -> 28
            (avgRecovery ?: 100f) < 50f -> 44
            else -> 68
        }
        val riskLevel = when {
            snapshot?.redFlags?.isNotEmpty() == true || latestReport?.riskLevel == "HIGH" -> "HIGH"
            sleepScore >= 65 || fatigueScore >= 65 || (abnormalMetrics.isNotEmpty()) -> "MEDIUM"
            else -> "LOW"
        }
        val sampleSufficient = currentSleep.size >= minSamples
        val reportConfidence = resolveReportConfidence(viewData.personalizationStatus.level, sampleSufficient)

        val headline = when {
            !sampleSufficient -> "${periodLabel}样本还不够完整，先把设备记录和干预执行稳定下来。"
            riskLevel == "HIGH" -> "${periodLabel}出现了高风险变化，放松建议只能作为辅助，优先级需要让位给进一步评估。"
            sleepScore >= 65 && recoveryScore < 50 -> "${periodLabel}睡眠仍不稳定，恢复能力也偏弱，后续重点需要先把睡前流程和低门槛恢复做实。"
            sleepScore >= 65 -> "${periodLabel}最主要的问题仍然是睡眠扰动偏高，下一阶段需要继续压低睡前唤醒。"
            fatigueScore >= 65 || recoveryScore < 50 -> "${periodLabel}恢复能力偏低，建议减少硬扛，优先做恢复型干预。"
            else -> "${periodLabel}整体状态相对稳定，下一阶段重点是维持节律并提高执行连续性。"
        }

        val riskSummary = when (riskLevel) {
            "HIGH" -> "本周期存在红旗或高风险医检信号，建议优先联系医生，并将放松训练作为辅助。"
            "MEDIUM" -> "本周期仍有中等风险变化，建议继续观察睡眠、恢复和执行依从性。"
            else -> "本周期未见新的高风险变化，重点转向维持节律与巩固有效干预。"
        }

        val highlights = buildList {
            avgSleepHours?.let { add("${periodLabel}平均睡眠 ${String.format(Locale.getDefault(), "%.1f", it)} 小时") }
            avgRecovery?.let { add("${periodLabel}平均恢复 ${it.toInt()} 分") }
            if (completionSummary.totalCount > 0) {
                add("${periodLabel}已完成 ${completionSummary.completedCount}/${completionSummary.totalCount} 个干预任务")
            }
            if (abnormalMetrics.isNotEmpty()) {
                add("${periodLabel}医检关注项：${abnormalMetrics.take(3).joinToString("、") { it.metricName }}")
            }
            snapshot?.redFlags?.takeIf { it.isNotEmpty() }?.let {
                add("画像提示需要优先关注高风险变化")
            }
        }.ifEmpty {
            listOf("暂无足够样本，请继续记录睡眠、恢复与干预执行")
        }

        val metricLines = buildList {
            add(metricLine("平均睡眠时长", avgSleepHours?.let { String.format(Locale.getDefault(), "%.1f 小时", it) } ?: "--", compareDelta(avgSleepHours, prevSleepHours, "小时")))
            add(metricLine("平均恢复分", avgRecovery?.toInt()?.toString()?.plus(" 分") ?: "--", compareDelta(avgRecovery, prevRecovery, "分")))
            add(metricLine("干预执行次数", "${executionSummary.count} 次", "本地模式下仅统计当前周期"))
            add(metricLine("平均干预效果", if (executionSummary.count > 0) "${executionSummary.avgEffectScore.toInt()} 分" else "--", "本地模式下仅统计当前周期"))
        }

        val bestProtocolCode = findBestProtocol(recentTasks, recentExecutions)
        val nextFocus = resolveLocalNextFocus(
            riskLevel = riskLevel,
            sleepScore = sleepScore,
            fatigueScore = fatigueScore,
            recoveryScore = recoveryScore,
            adherenceRate = adherenceRate,
            bestProtocolCode = bestProtocolCode
        )

        return HealthPeriodReportUiModel(
            period = timeRange,
            title = "${periodLabel}健康报告",
            headline = headline,
            sourceLabel = buildReportSourceLabel(periodLabel, false, viewData.personalizationStatus.level),
            sampleHint = buildReportSampleHint(
                sampleSufficient = sampleSufficient,
                missingInputs = viewData.personalizationStatus.missingInputs,
                confidence = reportConfidence
            ),
            riskLabel = riskLabelText(riskLevel),
            riskSummary = riskSummary,
            highlightsText = highlights.joinToString("\n") { "• $it" },
            metricChangesText = metricLines.joinToString("\n"),
            interventionSummary = buildString {
                append("${periodLabel}共记录 ${executionSummary.count} 次干预执行")
                if (completionSummary.totalCount > 0) {
                    append("，任务完成率 ${Math.round((completionSummary.completedCount * 100f) / completionSummary.totalCount)}%")
                }
                if (executionSummary.count > 0) {
                    append("，平均效果分 ${executionSummary.avgEffectScore.toInt()} 分")
                }
            },
            nextFocusTitle = nextFocus.first,
            nextFocusDetail = nextFocus.second,
            primaryAction = nextFocus.third,
            secondaryAction = nextFocus.fourth,
            personalizationLevel = viewData.personalizationStatus.level,
            missingInputs = viewData.personalizationStatus.missingInputs,
            reportConfidence = reportConfidence
        )
    }

    private fun mapRemoteReport(timeRange: TimeRange, data: PeriodSummaryData): HealthPeriodReportUiModel {
        val personalizationLevel = parsePersonalizationLevel(data.personalizationLevel)
        val missingInputs = parseMissingInputs(data.missingInputs)
        val reportConfidence = parseReportConfidence(data.reportConfidence)
        return HealthPeriodReportUiModel(
            period = timeRange,
            title = data.title,
            headline = data.headline,
            sourceLabel = buildReportSourceLabel(data.periodLabel, true, personalizationLevel),
            sampleHint = buildReportSampleHint(
                sampleSufficient = data.sampleSufficient,
                missingInputs = missingInputs,
                confidence = reportConfidence
            ),
            riskLabel = riskLabelText(data.riskLevel),
            riskSummary = data.riskSummary,
            highlightsText = data.highlights
                .ifEmpty { listOf("暂无足够数据，请继续记录睡眠、恢复与干预执行") }
                .joinToString("\n") { "• $it" },
            metricChangesText = data.metricChanges
                .joinToString("\n") { metricLine(it.label, it.value, it.comparison) },
            interventionSummary = data.interventionSummary,
            nextFocusTitle = data.nextFocusTitle,
            nextFocusDetail = data.nextFocusDetail,
            primaryAction = data.actions.getOrNull(0)?.toUiAction(),
            secondaryAction = data.actions.getOrNull(1)?.toUiAction(),
            personalizationLevel = personalizationLevel,
            missingInputs = missingInputs,
            reportConfidence = reportConfidence
        )
    }

    private fun buildReportSourceLabel(
        periodLabel: String,
        fromCloud: Boolean,
        personalizationLevel: PersonalizationLevel
    ): String {
        val source = if (fromCloud) {
            getApplication<Application>().getString(R.string.trend_period_source_cloud, periodLabel)
        } else {
            getApplication<Application>().getString(R.string.trend_period_source_local, periodLabel)
        }
        val levelText = if (personalizationLevel == PersonalizationLevel.FULL) {
            getApplication<Application>().getString(R.string.intervention_personalization_full_label)
        } else {
            getApplication<Application>().getString(R.string.intervention_personalization_preview_label)
        }
        return "$source · $levelText"
    }

    private fun buildReportSampleHint(
        sampleSufficient: Boolean,
        missingInputs: List<PersonalizationMissingInput>,
        confidence: ReportConfidenceLevel
    ): String {
        val baseHint = if (sampleSufficient) {
            getApplication<Application>().getString(R.string.trend_period_sample_sufficient)
        } else {
            getApplication<Application>().getString(R.string.trend_period_sample_insufficient)
        }
        val confidenceText = when (confidence) {
            ReportConfidenceLevel.HIGH -> getApplication<Application>().getString(R.string.trend_period_confidence_high)
            ReportConfidenceLevel.MEDIUM -> getApplication<Application>().getString(R.string.trend_period_confidence_medium)
            ReportConfidenceLevel.LOW -> getApplication<Application>().getString(R.string.trend_period_confidence_low)
        }
        val missingText = if (missingInputs.isEmpty()) {
            ""
        } else {
            " · 仍缺：${missingInputs.joinToString("、") { missingInputLabel(it) }}"
        }
        return baseHint + " · " + confidenceText + missingText
    }

    private fun parsePersonalizationLevel(raw: String): PersonalizationLevel {
        return runCatching { PersonalizationLevel.valueOf(raw) }
            .getOrDefault(PersonalizationLevel.PREVIEW)
    }

    private fun parseMissingInputs(raw: List<String>): List<PersonalizationMissingInput> {
        return raw.mapNotNull { value ->
            runCatching { PersonalizationMissingInput.valueOf(value) }.getOrNull()
        }
    }

    private fun parseReportConfidence(raw: String): ReportConfidenceLevel {
        return runCatching { ReportConfidenceLevel.valueOf(raw) }
            .getOrDefault(ReportConfidenceLevel.LOW)
    }

    private fun resolveReportConfidence(
        personalizationLevel: PersonalizationLevel,
        sampleSufficient: Boolean
    ): ReportConfidenceLevel {
        return when {
            !sampleSufficient -> ReportConfidenceLevel.LOW
            personalizationLevel == PersonalizationLevel.FULL -> ReportConfidenceLevel.HIGH
            else -> ReportConfidenceLevel.MEDIUM
        }
    }

    private fun missingInputLabel(input: PersonalizationMissingInput): String {
        return when (input) {
            PersonalizationMissingInput.DEVICE_DATA -> "设备睡眠数据"
            PersonalizationMissingInput.BASELINE_ASSESSMENT -> "基线量表"
            PersonalizationMissingInput.DOCTOR_INQUIRY -> "结构化问诊"
        }
    }

    private fun resolveLocalNextFocus(
        riskLevel: String,
        sleepScore: Int,
        fatigueScore: Int,
        recoveryScore: Int,
        adherenceRate: Float?,
        bestProtocolCode: String?
    ): Quadruple<String, String, InterventionActionUiModel?, InterventionActionUiModel?> {
        return when {
            riskLevel == "HIGH" -> Quadruple(
                "下阶段先做风险分流",
                "优先联系医生或进一步复查，再把低负荷放松作为辅助，不要只靠自助训练硬扛。",
                InterventionActionUiModel(
                    title = "优先联系医生",
                    subtitle = "本周期出现高风险变化，先完成专业评估。",
                    protocolCode = "TASK_DOCTOR_PRIORITY",
                    durationSec = 120,
                    assetRef = "screen://doctor",
                    itemType = PrescriptionItemType.LIFESTYLE
                ),
                actionFromCatalog("BODY_SCAN_NSDR_10M", PrescriptionItemType.SECONDARY)
            )
            sleepScore >= 65 -> Quadruple(
                "下阶段重点先稳定睡前流程",
                "建议继续执行睡前减刺激和身体扫描，减少晚间刺激输入，再观察入睡与次日恢复变化。",
                actionFromCatalog("SLEEP_WIND_DOWN_15M", PrescriptionItemType.PRIMARY),
                actionFromCatalog("BODY_SCAN_NSDR_10M", PrescriptionItemType.SECONDARY)
            )
            fatigueScore >= 65 || recoveryScore < 50 -> Quadruple(
                "下阶段重点做恢复型干预",
                "优先用恢复步行和引导拉伸替代继续硬扛疲劳，把恢复能力先托住。",
                actionFromCatalog("RECOVERY_WALK_10M", PrescriptionItemType.PRIMARY),
                actionFromCatalog("GUIDED_STRETCH_MOBILITY_8M", PrescriptionItemType.SECONDARY)
            )
            adherenceRate != null && adherenceRate < 0.45f -> Quadruple(
                "下阶段先提高可执行性",
                "先用更低门槛的助眠音景和一个生活任务，把执行连续性做起来。",
                actionFromCatalog("SOUNDSCAPE_SLEEP_AUDIO_15M", PrescriptionItemType.PRIMARY),
                actionFromCatalog("TASK_SCREEN_CURFEW", PrescriptionItemType.LIFESTYLE)
            )
            !bestProtocolCode.isNullOrBlank() -> Quadruple(
                "下阶段延续本周期最有效的干预",
                "当前已经出现效果更好的协议，建议继续保持，并配一个轻量生活任务巩固结果。",
                actionFromCatalog(bestProtocolCode, PrescriptionItemType.PRIMARY),
                actionFromCatalog("TASK_DAYLIGHT_WALK", PrescriptionItemType.LIFESTYLE)
            )
            else -> Quadruple(
                "下阶段继续保持稳定节律",
                "当前更适合维持低负担、可长期坚持的节律型干预，避免频繁切换方案。",
                actionFromCatalog("SOUNDSCAPE_SLEEP_AUDIO_15M", PrescriptionItemType.PRIMARY),
                actionFromCatalog("TASK_DAYLIGHT_WALK", PrescriptionItemType.LIFESTYLE)
            )
        }
    }

    private fun findBestProtocol(
        tasks: List<InterventionTaskEntity>,
        executions: List<InterventionExecutionEntity>
    ): String? {
        if (tasks.isEmpty() || executions.isEmpty()) return null
        val taskMap = tasks.associateBy { it.id }
        val buckets = linkedMapOf<String, MutableList<Float>>()
        executions.forEach { execution ->
            val protocol = taskMap[execution.taskId]?.protocolType ?: return@forEach
            val list = buckets.getOrPut(protocol) { mutableListOf() }
            list += execution.effectScore
        }
        return buckets.maxByOrNull { (_, values) -> values.average() }?.key
    }

    private fun actionFromCatalog(protocolCode: String, itemType: PrescriptionItemType): InterventionActionUiModel? {
        val definition = InterventionProtocolCatalog.find(protocolCode) ?: return null
        return InterventionActionUiModel(
            title = definition.displayName,
            subtitle = definition.description,
            protocolCode = definition.protocolCode,
            durationSec = definition.defaultDurationSec,
            assetRef = definition.assetRef,
            itemType = itemType
        )
    }

    private fun PeriodSummaryActionItem.toUiAction(): InterventionActionUiModel {
        val parsedItemType = runCatching { PrescriptionItemType.valueOf(itemType) }
            .getOrDefault(PrescriptionItemType.LIFESTYLE)
        return InterventionActionUiModel(
            title = title,
            subtitle = subtitle,
            protocolCode = protocolCode,
            durationSec = durationSec,
            assetRef = assetRef,
            itemType = parsedItemType
        )
    }

    private fun metricLine(label: String, value: String, comparison: String): String {
        return "• $label：$value · $comparison"
    }

    private fun compareDelta(current: Float?, previous: Float?, unit: String): String {
        if (current == null || previous == null) {
            return "较上周期样本不足"
        }
        val delta = current - previous
        if (abs(delta) < 0.05f) {
            return "较上周期持平"
        }
        val prefix = if (delta > 0f) "+" else ""
        return "较上周期 $prefix${String.format(Locale.getDefault(), "%.1f", delta)} $unit"
    }

    private fun riskLabelText(riskLevel: String): String {
        return when (riskLevel.uppercase(Locale.ROOT)) {
            "HIGH" -> "高风险"
            "MEDIUM" -> "中风险"
            else -> "低风险"
        }
    }

    private fun loadRealTrendData(sleepList: List<com.example.newstart.data.SleepData>) {
        val dateFormat = SimpleDateFormat("MM/dd", Locale.getDefault())
        val labels = sleepList.map { dateFormat.format(it.date) }
        val durations = sleepList.map { it.totalSleepMinutes / 60f }
        _sleepDurationData.postValue(Pair(labels, durations))

        val totalMinutes = sleepList.sumOf { it.totalSleepMinutes }
        if (totalMinutes > 0) {
            val deepSum = sleepList.sumOf { it.deepSleepMinutes }
            val remSum = sleepList.sumOf { it.remSleepMinutes }
            val lightSum = sleepList.sumOf { it.lightSleepMinutes }
            val awakeSum = sleepList.sumOf { it.awakeMinutes }
            _sleepQualityDistribution.postValue(
                mapOf(
                    "深睡" to (deepSum.toFloat() / totalMinutes * 100f),
                    "REM" to (remSum.toFloat() / totalMinutes * 100f),
                    "浅睡" to (lightSum.toFloat() / totalMinutes * 100f),
                    "清醒" to (awakeSum.toFloat() / totalMinutes * 100f)
                )
            )
        } else {
            _sleepQualityDistribution.postValue(emptyMap())
        }
    }

    private fun buildFilteredSeries(
        samples: List<TimedValue>,
        config: MetricFilterConfig,
        timeFormat: SimpleDateFormat
    ): Pair<List<String>, List<Float>> {
        if (samples.isEmpty()) return Pair(emptyList(), emptyList())

        val bounded = samples.filter {
            it.value in config.hardMin..config.hardMax && it.value.isFinite()
        }
        if (bounded.isEmpty()) return Pair(emptyList(), emptyList())

        val madFiltered = filterByMad(bounded, config.madThreshold)
        val jumpFiltered = filterByStepJump(madFiltered, config.maxStepDelta)
        val finalSeries = downsample(jumpFiltered.ifEmpty { madFiltered }, config.maxPoints)
        val labels = finalSeries.map { timeFormat.format(Date(it.timestamp)) }
        val values = stabilizeSeries(finalSeries.map { it.value })
        return Pair(labels, values)
    }

    private fun filterByMad(samples: List<TimedValue>, threshold: Float): List<TimedValue> {
        if (samples.size < 5) return samples
        val values = samples.map { it.value }
        val median = median(values)
        val deviations = values.map { abs(it - median) }
        val mad = median(deviations)
        if (mad <= 1e-6f) return samples
        return samples.filter { abs(it.value - median) / mad <= threshold }
    }

    private fun filterByStepJump(samples: List<TimedValue>, maxStepDelta: Float): List<TimedValue> {
        if (samples.size < 3) return samples
        val kept = ArrayList<TimedValue>(samples.size)
        var droppedCount = 0
        for (sample in samples) {
            if (kept.isEmpty()) {
                kept += sample
                continue
            }
            val baseline = kept.takeLast(5).map { it.value }.average().toFloat()
            if (abs(sample.value - baseline) <= maxStepDelta) {
                kept += sample
            } else {
                droppedCount++
            }
        }
        if (droppedCount > 0) {
            Log.d("SleepTrendViewModel", "[FILTER] step-jump dropped=$droppedCount kept=${kept.size}")
        }
        return if (kept.size >= 2) kept else samples
    }

    private fun downsample(samples: List<TimedValue>, maxPoints: Int): List<TimedValue> {
        if (samples.size <= maxPoints) return samples
        val step = samples.size.toFloat() / maxPoints.toFloat()
        val result = ArrayList<TimedValue>(maxPoints)
        var cursor = 0f
        while (cursor < samples.size) {
            result += samples[cursor.toInt()]
            cursor += step
        }
        if (result.isNotEmpty()) {
            result[result.lastIndex] = samples.last()
        }
        return result
    }

    private fun stabilizeSeries(values: List<Float>): List<Float> {
        if (values.size < 3) return values
        val smoothed = MutableList(values.size) { 0f }
        for (index in values.indices) {
            val from = (index - 1).coerceAtLeast(0)
            val to = (index + 1).coerceAtMost(values.lastIndex)
            val window = values.subList(from, to + 1)
            smoothed[index] = window.average().toFloat()
        }
        return smoothed
    }

    private fun normalizeMotionIntensity(raw: Float): Float {
        if (!raw.isFinite() || raw <= 0f) return 0f
        return when {
            raw <= 20f -> raw
            raw <= 40f -> (raw * 0.6f).coerceIn(0f, 20f)
            else -> 20f
        }
    }

    private fun median(values: List<Float>): Float {
        if (values.isEmpty()) return 0f
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            (sorted[mid - 1] + sorted[mid]) / 2f
        } else {
            sorted[mid]
        }
    }
}

data class SleepStatistics(
    val avgDuration: String,
    val avgDeepSleepPercentage: String,
    val avgEfficiency: String,
    val bestDay: String
)

enum class TimeRange {
    LAST_7_DAYS,
    LAST_30_DAYS
}

private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)
