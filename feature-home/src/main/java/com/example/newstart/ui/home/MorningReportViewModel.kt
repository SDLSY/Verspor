package com.example.newstart.ui.home

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.newstart.core.common.R
import com.example.newstart.data.BloodOxygenData
import com.example.newstart.data.HRVData
import com.example.newstart.data.HeartRateData
import com.example.newstart.data.HealthMetrics
import com.example.newstart.data.RecoveryLevel
import com.example.newstart.data.RecoveryScore
import com.example.newstart.data.SleepData
import com.example.newstart.data.TemperatureData
import com.example.newstart.data.Trend
import com.example.newstart.database.AppDatabase
import com.example.newstart.ml.AnomalyLevel
import com.example.newstart.ml.AnomalyPrimaryFactor
import com.example.newstart.ml.InferenceSource
import com.example.newstart.ml.PredictionResult
import com.example.newstart.ml.SensorDataInput
import com.example.newstart.network.PrivacyGuard
import com.example.newstart.network.models.SleepAnalysisRequest
import com.example.newstart.network.models.SleepDataRequest
import com.example.newstart.network.models.SyncRequest
import com.example.newstart.intervention.InterventionProtocolCatalog
import com.example.newstart.intervention.InterventionProfileViewData
import com.example.newstart.intervention.PersonalizationLevel
import com.example.newstart.intervention.PersonalizationMissingInput
import com.example.newstart.intervention.PrescriptionBundleDetails
import com.example.newstart.intervention.PrescriptionItemDetails
import com.example.newstart.intervention.PrescriptionItemType
import com.example.newstart.intervention.ProfileTriggerType
import com.example.newstart.lifestyle.DailyReadinessContribution
import com.example.newstart.repository.InterventionProfileRepository
import com.example.newstart.repository.InterventionRepository
import com.example.newstart.repository.InterventionExperienceCodec
import com.example.newstart.repository.FoodAnalysisRepository
import com.example.newstart.repository.MedicationAnalysisRepository
import com.example.newstart.repository.NetworkRepository
import com.example.newstart.repository.PrescriptionRepository
import com.example.newstart.repository.RelaxRepository
import com.example.newstart.repository.SleepRepository
import com.example.newstart.service.ai.LocalAnomalyDetectionService
import com.example.newstart.ui.intervention.InterventionActionUiModel
import com.example.newstart.util.HRVAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

class MorningReportViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MorningReportViewModel"
    }

    private val repository: SleepRepository
    private val interventionRepository: InterventionRepository
    private val interventionProfileRepository: InterventionProfileRepository
    private val prescriptionRepository: PrescriptionRepository
    private val relaxRepository: RelaxRepository
    private val medicationAnalysisRepository: MedicationAnalysisRepository
    private val foodAnalysisRepository: FoodAnalysisRepository
    private val networkRepository = NetworkRepository()
    private val anomalyDetector = LocalAnomalyDetectionService(application)
    private val app = getApplication<Application>()

    init {
        val database = AppDatabase.getDatabase(application)
        repository = SleepRepository(
            database.sleepDataDao(),
            database.healthMetricsDao(),
            database.recoveryScoreDao(),
            database.ppgSampleDao()
        )
        interventionRepository = InterventionRepository(
            taskDao = database.interventionTaskDao(),
            executionDao = database.interventionExecutionDao()
        )
        interventionProfileRepository = InterventionProfileRepository(application, database)
        prescriptionRepository = PrescriptionRepository(application, database, interventionProfileRepository)
        relaxRepository = RelaxRepository(
            relaxSessionDao = database.relaxSessionDao(),
            healthMetricsDao = database.healthMetricsDao()
        )
        medicationAnalysisRepository = MedicationAnalysisRepository(application, database)
        foodAnalysisRepository = FoodAnalysisRepository(application, database)
    }

    private val _recoveryScore = MutableLiveData<RecoveryScore>()
    val recoveryScore: LiveData<RecoveryScore> = _recoveryScore

    private val _sleepData = MutableLiveData<SleepData>()
    val sleepData: LiveData<SleepData> = _sleepData

    private val _healthMetrics = MutableLiveData<HealthMetrics>()
    val healthMetrics: LiveData<HealthMetrics> = _healthMetrics

    private val _adviceList = MutableLiveData<List<InterventionActionUiModel>>()
    val adviceList: LiveData<List<InterventionActionUiModel>> = _adviceList

    private val _cloudLoopMessage = MutableLiveData<String>()
    val cloudLoopMessage: LiveData<String> = _cloudLoopMessage

    private val _recoveryContributionSummary = MutableLiveData(
        app.getString(R.string.morning_recovery_contribution_default)
    )
    val recoveryContributionSummary: LiveData<String> = _recoveryContributionSummary

    private val _aiCredibility = MutableLiveData(AiCredibilityState.default())
    val aiCredibility: LiveData<AiCredibilityState> = _aiCredibility

    private val _edgeAdviceStatus = MutableLiveData<String>()
    val edgeAdviceStatus: LiveData<String> = _edgeAdviceStatus

    private val _interventionSummary = MutableLiveData<String>()
    val interventionSummary: LiveData<String> = _interventionSummary

    private val _personalizationState = MutableLiveData(MorningPersonalizationUiState.default(app))
    val personalizationState: LiveData<MorningPersonalizationUiState> = _personalizationState

    private val _recommendationInsight = MutableLiveData(MorningRecommendationInsightUiState.default(app))
    val recommendationInsight: LiveData<MorningRecommendationInsightUiState> = _recommendationInsight

    fun loadMorningReport() {
        viewModelScope.launch {
            val localMetrics = repository.getLatestHealthMetrics().first()
            val resolvedMetrics = localMetrics ?: createFallbackMetrics()
            val localSleep = repository.getLatestSleepData().first()
            val resolvedSleep = localSleep ?: createFallbackSleepData()

            _sleepData.value = resolvedSleep
            _healthMetrics.value = resolvedMetrics

            if (localSleep == null && localMetrics != null) {
                Log.i(TAG, "no sleep record, showing latest realtime metrics with fallback sleep summary")
            }

            val localAnomaly = detectLocalAnomaly(resolvedMetrics)
            val baseRecovery = buildRecoveryFromData(resolvedSleep, resolvedMetrics, null, localAnomaly)
            val readinessContribution = buildDailyReadinessContribution()
            val adjustedRecovery = applyDailyReadinessContribution(baseRecovery, readinessContribution)
            _recoveryScore.value = adjustedRecovery
            _recoveryContributionSummary.value = readinessContribution.summary
            if (localSleep != null) {
                repository.saveRecoveryScore(localSleep.id, adjustedRecovery)
            }

            refreshMorningPrescription(forceGenerate = false)
            refreshInterventionSummary()
        }
    }

    fun regenerateEdgeAdvice() {
        viewModelScope.launch {
            val sleep = _sleepData.value ?: repository.getLatestSleepData().first()
            val metrics = _healthMetrics.value ?: repository.getLatestHealthMetrics().first()
            val recovery = _recoveryScore.value

            if (sleep == null || metrics == null || recovery == null) {
                _cloudLoopMessage.postValue("当前数据不足，请先完成晨报加载。")
                return@launch
            }

            val bundle = refreshMorningPrescription(forceGenerate = true)
            _cloudLoopMessage.postValue(
                if (bundle != null) {
                    app.getString(R.string.morning_prescription_generate_done)
                } else {
                    app.getString(R.string.morning_prescription_generate_failed)
                }
            )
            refreshInterventionSummary()
        }
    }

    fun runCloudMinimalLoop() {
        viewModelScope.launch {
            val sleep = _sleepData.value ?: repository.getLatestSleepData().first() ?: run {
                loadMockData()
                _sleepData.value
            }

            if (sleep == null) {
                _cloudLoopMessage.postValue("暂无可上传的睡眠数据。")
                return@launch
            }

            val metrics = _healthMetrics.value ?: repository.getLatestHealthMetrics().first()
            val safeMetrics = metrics ?: createFallbackMetrics()

            val cloudSession = networkRepository.getCurrentSession() ?: run {
                _cloudLoopMessage.postValue("请先在“我的”页面登录云端账号。")
                return@launch
            }

            val uploadRequest = SleepDataRequest(
                userId = cloudSession.userId,
                sleepRecordId = sleep.id,
                date = sleep.date.time,
                bedTime = sleep.bedTime.time,
                wakeTime = sleep.wakeTime.time,
                totalSleepMinutes = sleep.totalSleepMinutes,
                deepSleepMinutes = sleep.deepSleepMinutes,
                lightSleepMinutes = sleep.lightSleepMinutes,
                remSleepMinutes = sleep.remSleepMinutes
            )

            networkRepository.uploadSleepData(uploadRequest).getOrElse { error ->
                _cloudLoopMessage.postValue("睡眠上传失败：${error.message ?: "未知错误"}")
                return@launch
            }

            val analysisRequest = SleepAnalysisRequest(
                userId = cloudSession.userId,
                sleepRecordId = sleep.id,
                rawData = PrivacyGuard.minimizedAnalysisRawData(safeMetrics, sleep)
            )

            val analysis = networkRepository.analyzeSleep(analysisRequest).getOrElse { error ->
                _cloudLoopMessage.postValue("云端分析失败：${error.message ?: "未知错误"}")
                return@launch
            }

            val edgeAnomaly = detectLocalAnomaly(safeMetrics)
            val cloudRecovery = buildRecoveryFromData(
                sleep = sleep,
                metrics = safeMetrics,
                cloudScoreOverride = analysis.recoveryScore,
                anomalyResult = edgeAnomaly
            )
            val readinessContribution = buildDailyReadinessContribution()
            val adjustedRecovery = applyDailyReadinessContribution(cloudRecovery, readinessContribution)
            _recoveryScore.postValue(adjustedRecovery)
            _recoveryContributionSummary.postValue(readinessContribution.summary)
            repository.saveRecoveryScore(sleep.id, adjustedRecovery)

            refreshMorningPrescription(forceGenerate = true)

            val syncResponse = networkRepository.syncData(
                SyncRequest(
                    userId = cloudSession.userId,
                    lastSyncTime = System.currentTimeMillis(),
                    data = mapOf(
                        "sleepRecordId" to sleep.id,
                        "recoveryScore" to cloudRecovery.score,
                        "source" to "morning_report"
                    )
                )
            ).getOrNull()

            val syncHint = syncResponse?.let { "，同步 ${it.updatedRecords} 条" }.orEmpty()
            _cloudLoopMessage.postValue("云端闭环完成：恢复分 ${cloudRecovery.score}$syncHint")
        }
    }

    fun saveTodayData() {
        viewModelScope.launch {
            loadMockData()
            _sleepData.value?.let { sleep ->
                repository.saveSleepData(sleep)
                _healthMetrics.value?.let { metrics ->
                    repository.saveHealthMetrics(sleep.id, metrics)
                }
                _recoveryScore.value?.let { score ->
                    repository.saveRecoveryScore(sleep.id, score)
                }
            }
        }
    }

    fun refresh() {
        loadMorningReport()
    }

    override fun onCleared() {
        anomalyDetector.close()
        super.onCleared()
    }

    private fun createFallbackMetrics(): HealthMetrics {
        return HealthMetrics(
            heartRate = HeartRateData(58, 60, 52, 68, Trend.STABLE, false),
            bloodOxygen = BloodOxygenData(97, 97, 95, "稳定", false),
            temperature = TemperatureData(36.5f, 36.5f, "正常", false),
            hrv = HRVData(
                current = 60,
                baseline = 60,
                recoveryRate = 0f,
                trend = Trend.STABLE,
                stressLevel = HRVAnalyzer.calculateStressLevel(60, 60)
            )
        )
    }

    private fun buildRecoveryFromData(
        sleep: SleepData,
        metrics: HealthMetrics,
        cloudScoreOverride: Int?,
        anomalyResult: PredictionResult? = null
    ): RecoveryScore {
        val calculated = RecoveryScore.calculate(
            sleepEfficiency = sleep.sleepEfficiency,
            hrvRecoveryRate = metrics.hrv.recoveryRate,
            deepSleepPercentage = sleep.getDeepSleepPercentage(),
            temperatureRhythm = if (metrics.temperature.current in 36.0f..37.4f) 88f else 70f,
            oxygenStability = metrics.bloodOxygen.current.toFloat()
        )

        val baseScore = (cloudScoreOverride ?: calculated.score).coerceIn(0, 100)
        val anomalyPenalty = if (cloudScoreOverride == null && anomalyResult != null) {
            (anomalyResult.anomalyScore * 20f).toInt().coerceIn(0, 20)
        } else {
            0
        }
        val finalScore = (baseScore - anomalyPenalty).coerceIn(0, 100)

        val finalLevel = when {
            finalScore >= 80 -> RecoveryLevel.EXCELLENT
            finalScore >= 60 -> RecoveryLevel.GOOD
            finalScore >= 40 -> RecoveryLevel.FAIR
            else -> RecoveryLevel.POOR
        }

        return calculated.copy(score = finalScore, level = finalLevel)
    }

    private fun detectLocalAnomaly(metrics: HealthMetrics): PredictionResult? {
        return try {
            val hrStd = ((metrics.heartRate.max - metrics.heartRate.min).toFloat() / 4f)
                .coerceAtLeast(1f)
            val spo2Max = maxOf(metrics.bloodOxygen.current, metrics.bloodOxygen.avg).toFloat()
            val input = SensorDataInput(
                heartRate = metrics.heartRate.current.toFloat(),
                heartRateMin = metrics.heartRate.min.toFloat(),
                heartRateMax = metrics.heartRate.max.toFloat(),
                heartRateStd = hrStd,
                spo2 = metrics.bloodOxygen.current.toFloat(),
                spo2Min = metrics.bloodOxygen.min.toFloat(),
                spo2Max = spo2Max,
                hrvSdnn = metrics.hrv.current.toFloat(),
                temp = metrics.temperature.current
            )
            anomalyDetector.predict(input).also {
                _aiCredibility.postValue(buildAiCredibilityState(it, metrics))
                Log.d(TAG, "edge-anomaly score=${it.anomalyScore}, level=${it.level}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "edge-anomaly failed: ${e.message}")
            _aiCredibility.postValue(
                AiCredibilityState(
                    confidencePercent = 0,
                    confidenceLabel = "可信度不足",
                    sourceLabel = "基础评估已更新",
                    summary = "本次未完成有效推理，恢复分仅基于基础规则计算。",
                    primaryFactor = "无法识别主导因子",
                    reason = "当前仅展示基础恢复结果，可稍后再次刷新查看。",
                    inferenceHint = "",
                    indicatorLevel = CredibilityLevel.LOW,
                    riskLevel = AnomalyLevel.ERROR,
                    riskScore = 0f,
                    riskDetected = true
                )
            )
            null
        }
    }

    private fun buildAiCredibilityState(
        result: PredictionResult,
        metrics: HealthMetrics
    ): AiCredibilityState {
        val confidencePercent = (result.confidence * 100f).roundToInt().coerceIn(0, 100)
        val confidenceLabel = when {
            confidencePercent >= 80 -> "高可信"
            confidencePercent >= 60 -> "中等可信"
            else -> "低可信"
        }
        val indicatorLevel = when {
            confidencePercent >= 75 -> CredibilityLevel.HIGH
            confidencePercent >= 55 -> CredibilityLevel.MEDIUM
            else -> CredibilityLevel.LOW
        }

        val sourceLabel = when (result.source) {
            InferenceSource.TFLITE -> "综合评估已更新"
            InferenceSource.RULE_FALLBACK -> "基础评估已更新"
            InferenceSource.ERROR -> "评估结果待复核"
        }

        val summary = when (result.level) {
            AnomalyLevel.CRITICAL -> "检测到明显风险信号，请优先关注异常指标。"
            AnomalyLevel.WARNING -> "检测到中度风险信号，建议今天降低负荷。"
            AnomalyLevel.MILD -> "出现轻度异常信号，建议继续观察。"
            AnomalyLevel.NORMAL -> "暂未检测到显著异常信号。"
            AnomalyLevel.UNKNOWN -> "推理结果存在不确定性，请结合体感评估。"
            AnomalyLevel.ERROR -> "本次推理失败，结果可信度较低。"
        }

        val primaryFactor = when (result.primaryFactor) {
            AnomalyPrimaryFactor.HEART_RATE -> "主导因子：心率波动"
            AnomalyPrimaryFactor.BLOOD_OXYGEN -> "主导因子：血氧波动"
            AnomalyPrimaryFactor.HRV -> "主导因子：HRV 恢复不足"
            AnomalyPrimaryFactor.TEMPERATURE -> "主导因子：体温偏离"
            AnomalyPrimaryFactor.MIXED -> "主导因子：多指标叠加"
            AnomalyPrimaryFactor.NONE -> "主导因子：无明显异常"
        }

        val reason = when (result.primaryFactor) {
            AnomalyPrimaryFactor.HEART_RATE ->
                "当前心率 ${metrics.heartRate.current} bpm，区间 ${metrics.heartRate.min}-${metrics.heartRate.max} bpm。"
            AnomalyPrimaryFactor.BLOOD_OXYGEN ->
                "当前血氧 ${metrics.bloodOxygen.current}%，夜间最低 ${metrics.bloodOxygen.min}%。"
            AnomalyPrimaryFactor.HRV ->
                "当前 HRV ${metrics.hrv.current}，基线 ${metrics.hrv.baseline}，恢复率 ${String.format(Locale.getDefault(), "%.1f", metrics.hrv.recoveryRate)}%。"
            AnomalyPrimaryFactor.TEMPERATURE ->
                "当前体温 ${String.format(Locale.getDefault(), "%.2f", metrics.temperature.current)}℃，状态 ${metrics.temperature.status}。"
            AnomalyPrimaryFactor.MIXED ->
                "血氧、心率与 HRV 同时出现偏离，建议综合评估当天负荷。"
            AnomalyPrimaryFactor.NONE ->
                "关键指标处于相对稳定区间。"
        }

        val inferenceHint = ""
        val riskDetected = result.level == AnomalyLevel.MILD ||
            result.level == AnomalyLevel.WARNING ||
            result.level == AnomalyLevel.CRITICAL

        return AiCredibilityState(
            confidencePercent = confidencePercent,
            confidenceLabel = confidenceLabel,
            sourceLabel = sourceLabel,
            summary = summary,
            primaryFactor = primaryFactor,
            reason = reason,
            inferenceHint = inferenceHint,
            indicatorLevel = indicatorLevel,
            riskLevel = result.level,
            riskScore = result.anomalyScore,
            riskDetected = riskDetected
        )
    }

    private suspend fun loadMockData() {
        val mockSleepData = createFallbackSleepData()
        _sleepData.value = mockSleepData

        val mockHealthMetrics = HealthMetrics(
            heartRate = HeartRateData(
                current = 58,
                avg = 60,
                min = 52,
                max = 68,
                trend = Trend.DOWN,
                isAbnormal = false
            ),
            bloodOxygen = BloodOxygenData(
                current = 97,
                avg = 97,
                min = 95,
                stability = "稳定",
                isAbnormal = false
            ),
            temperature = TemperatureData(
                current = 36.4f,
                avg = 36.5f,
                status = "正常",
                isAbnormal = false
            ),
            hrv = HRVData(
                current = 65,
                baseline = 60,
                recoveryRate = 8.3f,
                trend = Trend.UP,
                stressLevel = HRVAnalyzer.calculateStressLevel(65, 60)
            )
        )
        _healthMetrics.value = mockHealthMetrics

        val mockAnomaly = detectLocalAnomaly(mockHealthMetrics)
        val mockRecoveryScore = buildRecoveryFromData(mockSleepData, mockHealthMetrics, null, mockAnomaly)
        val readinessContribution = buildDailyReadinessContribution()
        _recoveryScore.value = applyDailyReadinessContribution(mockRecoveryScore, readinessContribution)
        _recoveryContributionSummary.value = readinessContribution.summary

        refreshMorningPrescription(forceGenerate = false)
    }

    private fun createFallbackSleepData(): SleepData {
        val calendar = Calendar.getInstance()
        val wakeTime = calendar.time
        calendar.add(Calendar.HOUR_OF_DAY, -8)
        val bedTime = calendar.time

        return SleepData(
            id = UUID.randomUUID().toString(),
            date = Date(),
            bedTime = bedTime,
            wakeTime = wakeTime,
            totalSleepMinutes = 492,
            deepSleepMinutes = 150,
            lightSleepMinutes = 216,
            remSleepMinutes = 126,
            awakeMinutes = 0,
            sleepEfficiency = 85f,
            fallAsleepMinutes = 12,
            awakeCount = 2
        )
    }

    private suspend fun buildDailyReadinessContribution(
        now: Long = System.currentTimeMillis()
    ): DailyReadinessContribution = withContext(Dispatchers.IO) {
        val medication = medicationAnalysisRepository.getLatest()
        val foodRecords = foodAnalysisRepository.getRecentSince(now - 24L * 60L * 60L * 1000L)
        val relaxSessions = relaxRepository.getRecentSessions(16)
            .filter { it.endTime >= now - 24L * 60L * 60L * 1000L }

        var medicationDelta = 0
        val contributionNotes = mutableListOf<String>()
        medication?.let { record ->
            medicationDelta = when {
                record.requiresManualReview -> -8
                record.riskLevel.equals("HIGH", ignoreCase = true) -> -6
                record.riskLevel.equals("MEDIUM", ignoreCase = true) -> -3
                record.confidence >= 0.8f -> 1
                else -> 0
            }
            contributionNotes += if (medicationDelta < 0) {
                "药物识别提示谨慎 ${medicationDelta}"
            } else {
                "药物记录已纳入参考 +$medicationDelta"
            }
        }

        var nutritionDelta = 0
        if (foodRecords.isNotEmpty()) {
            val totalCalories = foodRecords.sumOf { it.estimatedCalories }
            val highRisk = foodRecords.any { it.nutritionRiskLevel.equals("HIGH", ignoreCase = true) }
            val mediumRisk = foodRecords.any { it.nutritionRiskLevel.equals("MEDIUM", ignoreCase = true) }
            nutritionDelta = when {
                highRisk -> -7
                mediumRisk -> -3
                totalCalories in 1100..2200 -> 4
                totalCalories in 1..799 || totalCalories > 2600 -> -5
                else -> 1
            }
            contributionNotes += if (nutritionDelta < 0) {
                "饮食结构与热量拖累恢复 ${nutritionDelta}"
            } else {
                "最近饮食状态支持恢复 +$nutritionDelta"
            }
        }

        var interventionDelta = 0
        if (relaxSessions.isNotEmpty()) {
            val completionScores = relaxSessions.mapNotNull { session ->
                InterventionExperienceCodec.fromJson(session.metadataJson)
                    ?.completionQuality
                    ?.takeIf { it > 0 }
            }
            val avgCompletion = completionScores.average()
            val realtimeImproved = relaxSessions.count { session ->
                val metadata = InterventionExperienceCodec.fromJson(session.metadataJson)
                metadata?.realtimeSignalAvailable == true && (session.postHrv > session.preHrv || session.postHr < session.preHr)
            }
            interventionDelta = when {
                !avgCompletion.isNaN() && avgCompletion >= 78 -> 6
                !avgCompletion.isNaN() && avgCompletion >= 60 -> 4
                realtimeImproved > 0 -> 3
                completionScores.isEmpty() && relaxSessions.size >= 2 -> 2
                !avgCompletion.isNaN() && avgCompletion < 35 -> -3
                else -> 1
            }
            contributionNotes += when {
                interventionDelta > 0 -> app.getString(R.string.morning_intervention_delta_positive, interventionDelta)
                interventionDelta < 0 -> app.getString(R.string.morning_intervention_delta_negative, interventionDelta)
                else -> app.getString(R.string.morning_intervention_delta_neutral)
            }
        }

        val summary = contributionNotes.joinToString("，").ifBlank {
            app.getString(R.string.morning_recovery_contribution_default)
        }
        DailyReadinessContribution(
            medicationDelta = medicationDelta,
            nutritionDelta = nutritionDelta,
            interventionDelta = interventionDelta,
            summary = summary
        )
    }

    private fun applyDailyReadinessContribution(
        baseRecovery: RecoveryScore,
        contribution: DailyReadinessContribution
    ): RecoveryScore {
        val finalScore = (
            baseRecovery.score +
                contribution.medicationDelta +
                contribution.nutritionDelta +
                contribution.interventionDelta
            ).coerceIn(0, 100)

        val finalLevel = when {
            finalScore >= 80 -> RecoveryLevel.EXCELLENT
            finalScore >= 60 -> RecoveryLevel.GOOD
            finalScore >= 40 -> RecoveryLevel.FAIR
            else -> RecoveryLevel.POOR
        }
        return baseRecovery.copy(score = finalScore, level = finalLevel)
    }

    private suspend fun refreshMorningPrescription(forceGenerate: Boolean): PrescriptionBundleDetails? {
        val profile = interventionProfileRepository.getLatestViewData()
        val bundle = if (forceGenerate) {
            prescriptionRepository.generateForTrigger(ProfileTriggerType.DAILY_REFRESH)
        } else {
            prescriptionRepository.getLatestActiveBundle()
                ?: prescriptionRepository.generateForTrigger(ProfileTriggerType.DAILY_REFRESH)
        }

        _adviceList.postValue(bundle?.toMorningActions().orEmpty())
        _personalizationState.postValue(profile.toMorningPersonalizationState(app, bundle != null))
        _edgeAdviceStatus.postValue(
            when {
                bundle == null && profile.personalizationStatus.isPreview -> app.getString(
                    R.string.morning_prescription_status_preview_missing,
                    profile.missingInputSummary
                )
                bundle == null -> app.getString(R.string.morning_prescription_status_empty)
                forceGenerate && profile.personalizationStatus.isPreview -> app.getString(
                    R.string.morning_prescription_status_refreshed_preview,
                    profile.missingInputSummary
                )
                forceGenerate -> app.getString(R.string.morning_prescription_status_refreshed)
                profile.personalizationStatus.isPreview -> app.getString(
                    R.string.morning_prescription_status_ready_preview,
                    profile.missingInputSummary
                )
                else -> app.getString(R.string.morning_prescription_status_ready)
            }
        )
        refreshRecommendationInsight(bundle)
        return bundle
    }

    private fun InterventionProfileViewData.toMorningPersonalizationState(
        app: Application,
        hasBundle: Boolean
    ): MorningPersonalizationUiState {
        val preview = personalizationStatus.isPreview
        val label = when (personalizationStatus.level) {
            PersonalizationLevel.PREVIEW ->
                app.getString(R.string.intervention_personalization_preview_label)

            PersonalizationLevel.FULL ->
                app.getString(R.string.intervention_personalization_full_label)
        }
        val summary = when {
            preview && hasBundle -> app.getString(R.string.morning_personalization_preview_summary)
            preview -> app.getString(R.string.morning_personalization_preview_empty_summary)
            else -> app.getString(R.string.morning_personalization_full_summary)
        }
        val detail = if (preview) {
            app.getString(R.string.morning_personalization_missing_detail, missingInputSummary)
        } else {
            app.getString(R.string.morning_personalization_full_detail)
        }
        return MorningPersonalizationUiState(
            level = personalizationStatus.level,
            label = label,
            summary = summary,
            detail = detail,
            missingInputs = personalizationStatus.missingInputs.ifEmpty {
                inferMissingInputs(missingInputSummary)
            }
        )
    }

    private fun inferMissingInputs(summary: String): List<PersonalizationMissingInput> {
        val inferred = mutableListOf<PersonalizationMissingInput>()
        if (summary.contains("量表")) {
            inferred += PersonalizationMissingInput.BASELINE_ASSESSMENT
        }
        if (summary.contains("问诊")) {
            inferred += PersonalizationMissingInput.DOCTOR_INQUIRY
        }
        if (summary.contains("设备")) {
            inferred += PersonalizationMissingInput.DEVICE_DATA
        }
        return inferred
    }

    private fun PrescriptionBundleDetails.toMorningActions(): List<InterventionActionUiModel> {
        return items.sortedBy { it.sequenceOrder }.map { it.toMorningAction(rationale) }
    }

    private fun PrescriptionItemDetails.toMorningAction(bundleRationale: String): InterventionActionUiModel {
        val definition = InterventionProtocolCatalog.find(protocolCode)
        val prefix = when (itemType) {
            PrescriptionItemType.PRIMARY -> app.getString(R.string.intervention_dashboard_action_primary)
            PrescriptionItemType.SECONDARY -> app.getString(R.string.intervention_dashboard_action_secondary)
            PrescriptionItemType.LIFESTYLE -> app.getString(R.string.intervention_dashboard_action_lifestyle)
        }
        val displayName = definition?.displayName ?: protocolCode
        return InterventionActionUiModel(
            title = app.getString(R.string.intervention_dashboard_action_title, prefix, displayName),
            subtitle = bundleRationale,
            protocolCode = protocolCode,
            durationSec = durationSec,
            assetRef = assetRef,
            itemType = itemType
        )
    }

    private suspend fun refreshInterventionSummary() {
        val range = yesterdayRange()
        val completion = interventionRepository.getCompletionSummary(range.first, range.second).first()
        val execution = interventionRepository.getExecutionSummary(range.first, range.second).first()
        val yesterdaySummary = if (completion.totalCount <= 0) {
            app.getString(R.string.morning_intervention_summary_empty)
        } else {
            app.getString(
                R.string.morning_intervention_summary_format,
                completion.completedCount,
                completion.totalCount,
                execution.avgStressDrop
            )
        }

        val profile = interventionProfileRepository.getLatestViewData()
        val bundle = prescriptionRepository.getLatestActiveBundle()
        val summaryLines = mutableListOf<String>()
        summaryLines += yesterdaySummary

        if (bundle == null) {
            summaryLines += app.getString(R.string.morning_intervention_today_empty)
        } else {
            summaryLines += app.getString(R.string.morning_intervention_today_prefix, bundle.primaryGoal)
            summaryLines += app.getString(R.string.morning_intervention_reason_prefix, bundle.rationale)
            summaryLines += app.getString(
                R.string.morning_intervention_evidence_prefix,
                bundle.evidence.take(3).joinToString("、").ifBlank { "暂无" }
            )
        }

        summaryLines += if (profile.personalizationStatus.isPreview) {
            app.getString(
                R.string.morning_intervention_preview_cta,
                profile.missingInputSummary
            )
        } else {
            app.getString(R.string.morning_intervention_cta)
        }

        _interventionSummary.postValue(summaryLines.joinToString("\n"))
    }

    private suspend fun refreshRecommendationInsight(bundle: PrescriptionBundleDetails?) {
        val cloudSession = networkRepository.getCurrentSession()
        if (cloudSession == null) {
            _recommendationInsight.postValue(MorningRecommendationInsightUiState.fromLocalBundle(app, bundle))
            return
        }

        val explanation = networkRepository
            .getRecommendationExplanations(traceType = "DAILY_PRESCRIPTION", limit = 1)
            .getOrNull()
            ?.items
            ?.firstOrNull()

        val effects = networkRepository
            .getRecommendationEffects(days = 30)
            .getOrNull()

        _recommendationInsight.postValue(
            MorningRecommendationInsightUiState.fromRemote(app, explanation, effects, bundle)
        )
    }

    private fun yesterdayRange(): Pair<Long, Long> {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val todayStart = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_YEAR, -1)
        val yesterdayStart = calendar.timeInMillis
        return yesterdayStart to (todayStart - 1)
    }
}

data class AiCredibilityState(
    val confidencePercent: Int,
    val confidenceLabel: String,
    val sourceLabel: String,
    val summary: String,
    val primaryFactor: String,
    val reason: String,
    val inferenceHint: String,
    val indicatorLevel: CredibilityLevel,
    val riskLevel: AnomalyLevel,
    val riskScore: Float,
    val riskDetected: Boolean
) {
    companion object {
        fun default(): AiCredibilityState {
            return AiCredibilityState(
                confidencePercent = 0,
                confidenceLabel = "等待推理",
                sourceLabel = "评估待更新",
                summary = "尚未生成 AI 判断结果。",
                primaryFactor = "主导因子：待生成",
                reason = "加载晨报后将自动完成端侧推理。",
                inferenceHint = "",
                indicatorLevel = CredibilityLevel.MEDIUM,
                riskLevel = AnomalyLevel.UNKNOWN,
                riskScore = 0f,
                riskDetected = false
            )
        }
    }
}

enum class CredibilityLevel {
    HIGH,
    MEDIUM,
    LOW
}

data class MorningPersonalizationUiState(
    val level: PersonalizationLevel,
    val label: String,
    val summary: String,
    val detail: String,
    val missingInputs: List<PersonalizationMissingInput>
) {
    val isPreview: Boolean = level == PersonalizationLevel.PREVIEW

    companion object {
        fun default(app: Application): MorningPersonalizationUiState {
            return MorningPersonalizationUiState(
                level = PersonalizationLevel.PREVIEW,
                label = app.getString(R.string.intervention_personalization_preview_label),
                summary = app.getString(R.string.morning_personalization_preview_empty_summary),
                detail = app.getString(R.string.morning_personalization_missing_detail, app.getString(R.string.intervention_today_baseline)),
                missingInputs = emptyList()
            )
        }
    }
}

data class MorningRecommendationInsightUiState(
    val summary: String,
    val reasons: List<String>,
    val metaLabel: String,
    val effectHeadline: String,
    val effectDetail: String
) {
    companion object {
        fun default(app: Application): MorningRecommendationInsightUiState {
            return MorningRecommendationInsightUiState(
                summary = app.getString(R.string.morning_recommendation_explanation_loading),
                reasons = emptyList(),
                metaLabel = app.getString(R.string.morning_recommendation_explanation_meta_default),
                effectHeadline = app.getString(R.string.morning_recommendation_effects_title),
                effectDetail = app.getString(R.string.morning_recommendation_effects_empty)
            )
        }

        fun fromLocalBundle(
            app: Application,
            bundle: PrescriptionBundleDetails?
        ): MorningRecommendationInsightUiState {
            val reasons = bundle?.evidence?.take(2).orEmpty()
            return MorningRecommendationInsightUiState(
                summary = bundle?.rationale?.takeIf { it.isNotBlank() }
                    ?: app.getString(R.string.morning_recommendation_explanation_login_required),
                reasons = reasons,
                metaLabel = if (bundle != null || reasons.isNotEmpty()) {
                    "基于当前设备与填写信息整理"
                } else {
                    "基于今日状态与近期记录整理"
                },
                effectHeadline = app.getString(R.string.morning_recommendation_effects_title),
                effectDetail = app.getString(R.string.morning_recommendation_effects_login_required)
            )
        }

        fun fromRemote(
            app: Application,
            explanation: com.example.newstart.network.models.RecommendationExplanationItem?,
            effects: com.example.newstart.network.models.RecommendationEffectSummaryData?,
            bundle: PrescriptionBundleDetails?
        ): MorningRecommendationInsightUiState {
            val summary = explanation?.summary?.takeIf { it.isNotBlank() }
                ?: bundle?.rationale?.takeIf { it.isNotBlank() }
                ?: app.getString(R.string.morning_recommendation_explanation_empty)
            val reasons = when {
                !explanation?.reasons.isNullOrEmpty() -> explanation!!.reasons.take(2)
                !bundle?.evidence.isNullOrEmpty() -> bundle!!.evidence.take(2)
                else -> emptyList()
            }
            val metaLabel = if (explanation != null || bundle != null) {
                "基于今日状态与近期记录整理"
            } else {
                app.getString(R.string.morning_recommendation_explanation_loading)
            }
            val effectDetail = if (effects == null || effects.totalExecutions <= 0) {
                app.getString(R.string.morning_recommendation_effects_empty)
            } else {
                app.getString(
                    R.string.morning_recommendation_effects_detail,
                    effects.attributedExecutions,
                    effects.totalExecutions,
                    effects.avgEffectScore,
                    effects.avgStressDrop
                )
            }
            return MorningRecommendationInsightUiState(
                summary = summary,
                reasons = reasons,
                metaLabel = metaLabel,
                effectHeadline = app.getString(R.string.morning_recommendation_effects_title),
                effectDetail = effectDetail
            )
        }
    }
}

