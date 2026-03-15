package com.example.newstart.repository

import android.content.Context
import android.util.Log
import com.example.newstart.database.AppDatabase
import com.example.newstart.database.entity.AssessmentAnswerEntity
import com.example.newstart.database.entity.AssessmentSessionEntity
import com.example.newstart.database.entity.DeviceEntity
import com.example.newstart.database.entity.DoctorAssessmentEntity
import com.example.newstart.database.entity.DoctorMessageEntity
import com.example.newstart.database.entity.DoctorSessionEntity
import com.example.newstart.database.entity.FoodAnalysisEntity
import com.example.newstart.database.entity.HealthMetricsEntity
import com.example.newstart.database.entity.InterventionExecutionEntity
import com.example.newstart.database.entity.InterventionProfileSnapshotEntity
import com.example.newstart.database.entity.InterventionTaskEntity
import com.example.newstart.database.entity.MedicalMetricEntity
import com.example.newstart.database.entity.MedicalReportEntity
import com.example.newstart.database.entity.MedicationAnalysisEntity
import com.example.newstart.database.entity.PrescriptionBundleEntity
import com.example.newstart.database.entity.PrescriptionItemEntity
import com.example.newstart.database.entity.RecoveryScoreEntity
import com.example.newstart.database.entity.RelaxSessionEntity
import com.example.newstart.database.entity.SleepDataEntity
import com.example.newstart.network.models.AuthData
import com.example.newstart.network.models.DemoBootstrapData
import com.example.newstart.network.models.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DemoBootstrapCoordinator(
    private val context: Context,
    private val accountRepository: CloudAccountRepository = CloudAccountRepository()
) {

    private val db: AppDatabase by lazy(LazyThreadSafetyMode.NONE) {
        AppDatabase.getDatabase(context)
    }

    companion object {
        private const val TAG = "DemoBootstrap"
        private const val ROLE_DEMO_USER = "demo_user"
        private const val PREFS_NAME = "demo_bootstrap_state"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_SCENARIO = "scenario"
        private const val KEY_VERSION = "version"
    }

    data class DemoBootstrapResult(
        val isDemoAccount: Boolean,
        val applied: Boolean,
        val scenario: String? = null,
        val version: String? = null,
        val message: String = ""
    )

    suspend fun bootstrapForAuth(authData: AuthData): Result<DemoBootstrapResult> {
        return withContext(Dispatchers.IO) {
            val session = accountRepository.getCurrentSession()
                ?: return@withContext Result.success(
                    DemoBootstrapResult(
                        isDemoAccount = false,
                        applied = false,
                        message = "当前没有有效登录会话"
                    )
                )

            if (!authData.demoRole.equals(ROLE_DEMO_USER, ignoreCase = true)) {
                clearPreviousDemoImportIfNeeded()
                return@withContext Result.success(
                    DemoBootstrapResult(
                        isDemoAccount = false,
                        applied = false,
                        message = "当前账号不是演示账号"
                    )
                )
            }

            val scenario = authData.demoScenario.orEmpty().trim()
            val version = authData.demoSeedVersion.orEmpty().trim()
            if (scenario.isBlank() || version.isBlank()) {
                return@withContext Result.failure(Exception("演示账号缺少场景元信息"))
            }

            if (isCurrentImport(session.userId, scenario, version)) {
                return@withContext Result.success(
                    DemoBootstrapResult(
                        isDemoAccount = true,
                        applied = false,
                        scenario = scenario,
                        version = version,
                        message = "演示数据已经是当前版本"
                    )
                )
            }

            val payload = accountRepository.getDemoBootstrap().getOrElse {
                return@withContext Result.failure(it)
            }
            importSnapshot(payload)
            persistState(session.userId, payload.demoScenario, payload.demoSeedVersion)
            Result.success(
                DemoBootstrapResult(
                    isDemoAccount = true,
                    applied = true,
                    scenario = payload.demoScenario,
                    version = payload.demoSeedVersion,
                    message = "已导入演示场景：${payload.displayName}"
                )
            )
        }
    }

    suspend fun bootstrapForCurrentSession(): Result<DemoBootstrapResult> {
        return withContext(Dispatchers.IO) {
            if (accountRepository.getCurrentSession() == null) {
                return@withContext Result.success(
                    DemoBootstrapResult(
                        isDemoAccount = false,
                        applied = false,
                        message = "当前没有有效登录会话"
                    )
                )
            }

            val profile = accountRepository.getUserProfile().getOrElse {
                return@withContext Result.failure(it)
            }
            syncForProfile(profile)
        }
    }

    private suspend fun syncForProfile(profile: UserProfile): Result<DemoBootstrapResult> {
        if (!profile.demoRole.equals(ROLE_DEMO_USER, ignoreCase = true)) {
            clearPreviousDemoImportIfNeeded()
            return Result.success(
                DemoBootstrapResult(
                    isDemoAccount = false,
                    applied = false,
                    message = "当前账号不是演示账号"
                )
            )
        }

        val scenario = profile.demoScenario.orEmpty().trim()
        val version = profile.demoSeedVersion.orEmpty().trim()
        if (scenario.isBlank() || version.isBlank()) {
            return Result.failure(Exception("演示账号缺少场景元信息"))
        }
        if (isCurrentImport(profile.userId, scenario, version)) {
            return Result.success(
                DemoBootstrapResult(
                    isDemoAccount = true,
                    applied = false,
                    scenario = scenario,
                    version = version,
                    message = "演示数据已经是当前版本"
                )
            )
        }

        val payload = accountRepository.getDemoBootstrap().getOrElse {
            return Result.failure(it)
        }
        importSnapshot(payload)
        persistState(profile.userId, payload.demoScenario, payload.demoSeedVersion)
        return Result.success(
            DemoBootstrapResult(
                isDemoAccount = true,
                applied = true,
                scenario = payload.demoScenario,
                version = payload.demoSeedVersion,
                message = "已导入演示场景：${payload.displayName}"
            )
        )
    }

    private suspend fun importSnapshot(data: DemoBootstrapData) {
        Log.i(TAG, "importing demo snapshot scenario=${data.demoScenario} version=${data.demoSeedVersion}")
        db.clearAllTables()
        val snapshot = data.snapshot

        snapshot.devices.forEach { db.deviceDao().insert(it.toEntity()) }
        db.sleepDataDao().insertAll(snapshot.sleepRecords.map { it.toEntity() })
        snapshot.healthMetrics.forEach { db.healthMetricsDao().insert(it.toEntity()) }
        snapshot.recoveryScores.forEach { db.recoveryScoreDao().insert(it.toEntity()) }
        snapshot.assessmentSessions.forEach { db.assessmentSessionDao().upsert(it.toEntity()) }
        if (snapshot.assessmentAnswers.isNotEmpty()) {
            db.assessmentAnswerDao().insertAll(snapshot.assessmentAnswers.map { it.toEntity() })
        }
        snapshot.doctorSessions.forEach { db.doctorSessionDao().insert(it.toEntity()) }
        snapshot.doctorAssessments.forEach { db.doctorAssessmentDao().insert(it.toEntity()) }
        snapshot.doctorMessages.forEach { db.doctorMessageDao().insert(it.toEntity()) }
        snapshot.medicalReports.forEach { db.medicalReportDao().upsert(it.toEntity()) }
        if (snapshot.medicalMetrics.isNotEmpty()) {
            db.medicalMetricDao().upsertAll(snapshot.medicalMetrics.map { it.toEntity() })
        }
        snapshot.interventionProfileSnapshots.forEach {
            db.interventionProfileSnapshotDao().upsert(it.toEntity())
        }
        snapshot.prescriptionBundles.forEach { db.prescriptionBundleDao().upsert(it.toEntity()) }
        if (snapshot.prescriptionItems.isNotEmpty()) {
            db.prescriptionItemDao().upsertAll(snapshot.prescriptionItems.map { it.toEntity() })
        }
        snapshot.interventionTasks.forEach { db.interventionTaskDao().upsert(it.toEntity()) }
        if (snapshot.interventionExecutions.isNotEmpty()) {
            db.interventionExecutionDao().insertAll(snapshot.interventionExecutions.map { it.toEntity() })
        }
        snapshot.relaxSessions.forEach { db.relaxSessionDao().insert(it.toEntity()) }
        snapshot.medicationRecords.forEach { db.medicationAnalysisDao().upsert(it.toEntity()) }
        snapshot.foodRecords.forEach { db.foodAnalysisDao().upsert(it.toEntity()) }
    }

    private fun clearPreviousDemoImportIfNeeded() {
        val prefs = prefs()
        val priorUserId = prefs.getString(KEY_USER_ID, null).orEmpty()
        if (priorUserId.isBlank()) {
            return
        }
        db.clearAllTables()
        prefs.edit().clear().apply()
        Log.i(TAG, "cleared previously imported demo data for user=$priorUserId")
    }

    private fun isCurrentImport(userId: String, scenario: String, version: String): Boolean {
        val prefs = prefs()
        return prefs.getString(KEY_USER_ID, null) == userId &&
            prefs.getString(KEY_SCENARIO, null) == scenario &&
            prefs.getString(KEY_VERSION, null) == version
    }

    private fun persistState(userId: String, scenario: String, version: String) {
        prefs().edit()
            .putString(KEY_USER_ID, userId)
            .putString(KEY_SCENARIO, scenario)
            .putString(KEY_VERSION, version)
            .apply()
    }

    private fun prefs() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}

private fun com.example.newstart.network.models.DemoDeviceRecord.toEntity() = DeviceEntity(
    deviceId = deviceId,
    deviceName = deviceName,
    macAddress = macAddress,
    batteryLevel = batteryLevel,
    firmwareVersion = firmwareVersion,
    connectionState = connectionState,
    lastSyncTime = lastSyncTime,
    isPrimary = isPrimary,
    createdAt = createdAt,
    updatedAt = updatedAt
)

private fun com.example.newstart.network.models.DemoSleepRecord.toEntity() = SleepDataEntity(
    id = id,
    date = date,
    bedTime = bedTime,
    wakeTime = wakeTime,
    totalSleepMinutes = totalSleepMinutes,
    deepSleepMinutes = deepSleepMinutes,
    lightSleepMinutes = lightSleepMinutes,
    remSleepMinutes = remSleepMinutes,
    awakeMinutes = awakeMinutes,
    sleepEfficiency = sleepEfficiency,
    fallAsleepMinutes = fallAsleepMinutes,
    awakeCount = awakeCount,
    createdAt = createdAt,
    updatedAt = updatedAt
)

private fun com.example.newstart.network.models.DemoHealthMetricsRecord.toEntity() = HealthMetricsEntity(
    id = id,
    sleepRecordId = sleepRecordId,
    timestamp = timestamp,
    heartRateSample = heartRateSample,
    bloodOxygenSample = bloodOxygenSample,
    temperatureSample = temperatureSample,
    stepsSample = stepsSample,
    accMagnitudeSample = accMagnitudeSample,
    heartRateCurrent = heartRateCurrent,
    heartRateAvg = heartRateAvg,
    heartRateMin = heartRateMin,
    heartRateMax = heartRateMax,
    heartRateTrend = heartRateTrend,
    bloodOxygenCurrent = bloodOxygenCurrent,
    bloodOxygenAvg = bloodOxygenAvg,
    bloodOxygenMin = bloodOxygenMin,
    bloodOxygenStability = bloodOxygenStability,
    temperatureCurrent = temperatureCurrent,
    temperatureAvg = temperatureAvg,
    temperatureStatus = temperatureStatus,
    hrvCurrent = hrvCurrent,
    hrvBaseline = hrvBaseline,
    hrvRecoveryRate = hrvRecoveryRate,
    hrvTrend = hrvTrend
)

private fun com.example.newstart.network.models.DemoRecoveryScoreRecord.toEntity() = RecoveryScoreEntity(
    id = id,
    sleepRecordId = sleepRecordId,
    date = date,
    score = score,
    sleepEfficiencyScore = sleepEfficiencyScore,
    hrvRecoveryScore = hrvRecoveryScore,
    deepSleepScore = deepSleepScore,
    temperatureRhythmScore = temperatureRhythmScore,
    oxygenStabilityScore = oxygenStabilityScore,
    level = level,
    createdAt = createdAt
)

private fun com.example.newstart.network.models.DemoDoctorSessionRecord.toEntity() = DoctorSessionEntity(
    id = id,
    createdAt = createdAt,
    updatedAt = updatedAt,
    status = status,
    domain = domain,
    chiefComplaint = chiefComplaint,
    riskLevel = riskLevel
)

private fun com.example.newstart.network.models.DemoDoctorMessageRecord.toEntity() = DoctorMessageEntity(
    id = id,
    sessionId = sessionId,
    role = role,
    messageType = messageType,
    content = content,
    timestamp = timestamp,
    payloadJson = payloadJson,
    actionProtocolType = actionProtocolType,
    actionDurationSec = actionDurationSec
)

private fun com.example.newstart.network.models.DemoDoctorAssessmentRecord.toEntity() =
    DoctorAssessmentEntity(
        id = id,
        sessionId = sessionId,
        createdAt = createdAt,
        suspectedIssuesJson = suspectedIssuesJson,
        symptomFactsJson = symptomFactsJson,
        missingInfoJson = missingInfoJson,
        redFlagsJson = redFlagsJson,
        recommendedDepartment = recommendedDepartment,
        doctorSummary = doctorSummary,
        nextStepAdviceJson = nextStepAdviceJson,
        disclaimer = disclaimer
    )

private fun com.example.newstart.network.models.DemoAssessmentSessionRecord.toEntity() =
    AssessmentSessionEntity(
        id = id,
        scaleCode = scaleCode,
        startedAt = startedAt,
        completedAt = completedAt,
        totalScore = totalScore,
        severityLevel = severityLevel,
        freshnessUntil = freshnessUntil,
        source = source
    )

private fun com.example.newstart.network.models.DemoAssessmentAnswerRecord.toEntity() =
    AssessmentAnswerEntity(
        id = id,
        sessionId = sessionId,
        itemCode = itemCode,
        itemOrder = itemOrder,
        answerValue = answerValue
    )

private fun com.example.newstart.network.models.DemoInterventionTaskRecord.toEntity() =
    InterventionTaskEntity(
        id = id,
        date = date,
        sourceType = sourceType,
        triggerReason = triggerReason,
        bodyZone = bodyZone,
        protocolType = protocolType,
        durationSec = durationSec,
        plannedAt = plannedAt,
        status = status,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

private fun com.example.newstart.network.models.DemoInterventionExecutionRecord.toEntity() =
    InterventionExecutionEntity(
        id = id,
        taskId = taskId,
        startedAt = startedAt,
        endedAt = endedAt,
        elapsedSec = elapsedSec,
        beforeStress = beforeStress,
        afterStress = afterStress,
        beforeHr = beforeHr,
        afterHr = afterHr,
        effectScore = effectScore,
        completionType = completionType,
        metadataJson = metadataJson
    )

private fun com.example.newstart.network.models.DemoMedicalReportRecord.toEntity() = MedicalReportEntity(
    id = id,
    reportDate = reportDate,
    reportType = reportType,
    imageUri = imageUri,
    ocrTextDigest = ocrTextDigest,
    parseStatus = parseStatus,
    riskLevel = riskLevel,
    createdAt = createdAt
)

private fun com.example.newstart.network.models.DemoMedicalMetricRecord.toEntity() = MedicalMetricEntity(
    id = id,
    reportId = reportId,
    metricCode = metricCode,
    metricName = metricName,
    metricValue = metricValue,
    unit = unit,
    refLow = refLow,
    refHigh = refHigh,
    isAbnormal = isAbnormal,
    confidence = confidence
)

private fun com.example.newstart.network.models.DemoRelaxSessionRecord.toEntity() = RelaxSessionEntity(
    id = id,
    startTime = startTime,
    endTime = endTime,
    protocolType = protocolType,
    durationSec = durationSec,
    preStress = preStress,
    postStress = postStress,
    preHr = preHr,
    postHr = postHr,
    preHrv = preHrv,
    postHrv = postHrv,
    preMotion = preMotion,
    postMotion = postMotion,
    effectScore = effectScore,
    metadataJson = metadataJson
)

private fun com.example.newstart.network.models.DemoInterventionProfileSnapshotRecord.toEntity() =
    InterventionProfileSnapshotEntity(
        id = id,
        generatedAt = generatedAt,
        triggerType = triggerType,
        domainScoresJson = domainScoresJson,
        evidenceFactsJson = evidenceFactsJson,
        redFlagsJson = redFlagsJson
    )

private fun com.example.newstart.network.models.DemoPrescriptionBundleRecord.toEntity() =
    PrescriptionBundleEntity(
        id = id,
        createdAt = createdAt,
        triggerType = triggerType,
        profileSnapshotId = profileSnapshotId,
        primaryGoal = primaryGoal,
        riskLevel = riskLevel,
        rationale = rationale,
        evidenceJson = evidenceJson,
        status = status
    )

private fun com.example.newstart.network.models.DemoPrescriptionItemRecord.toEntity() =
    PrescriptionItemEntity(
        id = id,
        bundleId = bundleId,
        itemType = itemType,
        protocolCode = protocolCode,
        assetRef = assetRef,
        durationSec = durationSec,
        sequenceOrder = sequenceOrder,
        timingSlot = timingSlot,
        isRequired = isRequired,
        status = status
    )

private fun com.example.newstart.network.models.DemoMedicationRecord.toEntity() = MedicationAnalysisEntity(
    id = id,
    capturedAt = capturedAt,
    imageUri = imageUri,
    recognizedName = recognizedName,
    dosageForm = dosageForm,
    specification = specification,
    activeIngredientsJson = activeIngredientsJson,
    matchedSymptomsJson = matchedSymptomsJson,
    usageSummary = usageSummary,
    riskLevel = riskLevel,
    riskFlagsJson = riskFlagsJson,
    evidenceNotesJson = evidenceNotesJson,
    advice = advice,
    confidence = confidence,
    requiresManualReview = requiresManualReview,
    analysisMode = analysisMode,
    providerId = providerId,
    modelId = modelId,
    traceId = traceId,
    syncState = syncState,
    cloudRecordId = cloudRecordId,
    syncedAt = syncedAt,
    createdAt = createdAt,
    updatedAt = updatedAt
)

private fun com.example.newstart.network.models.DemoFoodRecord.toEntity() = FoodAnalysisEntity(
    id = id,
    capturedAt = capturedAt,
    imageUri = imageUri,
    mealType = mealType,
    foodItemsJson = foodItemsJson,
    estimatedCalories = estimatedCalories,
    carbohydrateGrams = carbohydrateGrams,
    proteinGrams = proteinGrams,
    fatGrams = fatGrams,
    nutritionRiskLevel = nutritionRiskLevel,
    nutritionFlagsJson = nutritionFlagsJson,
    dailyContribution = dailyContribution,
    advice = advice,
    confidence = confidence,
    requiresManualReview = requiresManualReview,
    analysisMode = analysisMode,
    providerId = providerId,
    modelId = modelId,
    traceId = traceId,
    syncState = syncState,
    cloudRecordId = cloudRecordId,
    syncedAt = syncedAt,
    createdAt = createdAt,
    updatedAt = updatedAt
)
