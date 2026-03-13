package com.example.newstart.repository

import android.content.Context
import android.util.Log
import com.example.newstart.database.AppDatabase
import com.example.newstart.database.entity.DoctorAssessmentEntity
import com.example.newstart.database.entity.HealthMetricsEntity
import com.example.newstart.database.entity.InterventionProfileSnapshotEntity
import com.example.newstart.database.entity.InterventionTaskEntity
import com.example.newstart.database.entity.MedicalMetricEntity
import com.example.newstart.intervention.AssessmentCatalog
import com.example.newstart.intervention.InterventionProfileSnapshot
import com.example.newstart.intervention.InterventionProfileViewData
import com.example.newstart.intervention.PersonalizationLevel
import com.example.newstart.intervention.PersonalizationMissingInput
import com.example.newstart.intervention.PersonalizationStatus
import com.example.newstart.intervention.ProfileTriggerType
import com.example.newstart.network.models.AssessmentBaselineSummaryUpsertRequest
import com.example.newstart.network.models.DoctorInquirySummaryUpsertRequest
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class InterventionProfileRepository(
    private val context: Context,
    private val db: AppDatabase = AppDatabase.getDatabase(context)
) {

    private val gson = Gson()
    private val snapshotDao = db.interventionProfileSnapshotDao()
    private val sleepDao = db.sleepDataDao()
    private val recoveryScoreDao = db.recoveryScoreDao()
    private val healthMetricsDao = db.healthMetricsDao()
    private val taskDao = db.interventionTaskDao()
    private val executionDao = db.interventionExecutionDao()
    private val assessmentRepository = AssessmentRepository(context, db)
    private val medicalReportRepository = MedicalReportRepository(db.medicalReportDao(), db.medicalMetricDao())
    private val networkRepository = NetworkRepository()
    private val doctorRepository = DoctorConversationRepository(
        db.doctorSessionDao(),
        db.doctorAssessmentDao(),
        db.doctorMessageDao()
    )

    companion object {
        private const val DAY_MS = 24L * 60L * 60L * 1000L
        private const val TAG = "InterventionProfileRepo"
    }

    suspend fun hasFreshBaseline(now: Long = System.currentTimeMillis()): Boolean {
        return assessmentRepository.hasFreshBaseline(now)
    }

    suspend fun getPersonalizationStatus(
        now: Long = System.currentTimeMillis()
    ): PersonalizationStatus = withContext(Dispatchers.IO) {
        buildPersonalizationStatus(now)
    }

    suspend fun syncPersonalizationSupportIfPossible(
        now: Long = System.currentTimeMillis()
    ) = withContext(Dispatchers.IO) {
        val session = networkRepository.getCurrentSession() ?: return@withContext
        syncBaselineSummary(now, session.userId)
        syncDoctorInquirySummary(now, session.userId)
    }

    suspend fun refreshSnapshot(
        triggerType: ProfileTriggerType,
        now: Long = System.currentTimeMillis()
    ): InterventionProfileSnapshot = withContext(Dispatchers.IO) {
        val snapshot = buildSnapshot(triggerType, now)
        snapshotDao.upsert(snapshot.toEntity())
        snapshot
    }

    suspend fun getLatestSnapshot(): InterventionProfileSnapshot? = withContext(Dispatchers.IO) {
        snapshotDao.getLatest()?.toModel()
    }

    suspend fun getLatestViewData(
        now: Long = System.currentTimeMillis()
    ): InterventionProfileViewData = withContext(Dispatchers.IO) {
        val baselineCompleted = assessmentRepository.hasFreshBaseline(now)
        val personalizationStatus = buildPersonalizationStatus(now)
        val scaleResults = assessmentRepository.getLatestScaleResults()
        var latestSnapshot = snapshotDao.getLatest()?.toModel()
        if (latestSnapshot == null) {
            latestSnapshot = buildSnapshot(ProfileTriggerType.DAILY_REFRESH, now)
            snapshotDao.upsert(latestSnapshot.toEntity())
        }

        val latestSession = doctorRepository.getLatestSession()
        val latestAssessment = latestSession?.let { doctorRepository.getLatestAssessment(it.id) }
        val latestDoctorSummary = latestAssessment?.doctorSummary
            ?.takeIf { it.isNotBlank() }
            ?: latestSession?.chiefComplaint
                ?.takeIf { it.isNotBlank() }
            ?: "暂无问诊摘要"

        val latestMedicalSummary = buildMedicalSummary()
        val adherenceHint = buildAdherenceHint(taskDao.getRecent(10))

        InterventionProfileViewData(
            snapshot = latestSnapshot,
            scaleResults = scaleResults,
            latestDoctorSummary = latestDoctorSummary,
            latestMedicalSummary = latestMedicalSummary,
            adherenceHint = adherenceHint,
            baselineCompleted = baselineCompleted,
            personalizationStatus = personalizationStatus,
            personalizationSummary = buildPersonalizationSummary(personalizationStatus),
            missingInputSummary = buildMissingInputSummary(personalizationStatus)
        )
    }

    private suspend fun buildPersonalizationStatus(now: Long): PersonalizationStatus {
        val missingInputs = mutableListOf<PersonalizationMissingInput>()
        if (!hasRecentDeviceSamples(now)) {
            missingInputs += PersonalizationMissingInput.DEVICE_DATA
        }
        if (!assessmentRepository.hasFreshBaseline(now)) {
            missingInputs += PersonalizationMissingInput.BASELINE_ASSESSMENT
        }
        if (!hasRecentDoctorInquiry(now)) {
            missingInputs += PersonalizationMissingInput.DOCTOR_INQUIRY
        }
        val level = if (missingInputs.isEmpty()) {
            PersonalizationLevel.FULL
        } else {
            PersonalizationLevel.PREVIEW
        }
        return PersonalizationStatus(level = level, missingInputs = missingInputs)
    }

    private suspend fun hasRecentDeviceSamples(now: Long): Boolean {
        val sevenDaysAgo = now - 7 * DAY_MS
        return sleepDao.getByDateRange(sevenDaysAgo, now).first().size >= 3
    }

    private suspend fun hasRecentDoctorInquiry(now: Long): Boolean {
        val latestSession = doctorRepository.getLatestSession() ?: return false
        val latestAssessment = doctorRepository.getLatestAssessment(latestSession.id) ?: return false
        return latestAssessment.createdAt >= now - 30 * DAY_MS
    }

    private suspend fun syncBaselineSummary(now: Long, userId: String) {
        val baselineCodes = AssessmentCatalog.baselineScaleCodes
        val completedSessions = baselineCodes.mapNotNull { code ->
            assessmentRepository.getLatestScaleSession(code)
        }
        if (completedSessions.isEmpty()) {
            return
        }
        val completedAt = completedSessions.mapNotNull { it.completedAt }.maxOrNull()
            ?: completedSessions.maxOfOrNull { it.startedAt }
            ?: now
        val freshnessUntil = completedSessions.minOfOrNull { it.freshnessUntil } ?: now
        val request = AssessmentBaselineSummaryUpsertRequest(
            completedScaleCodes = completedSessions.map { it.scaleCode }.distinct(),
            completedCount = completedSessions.size,
            completedAt = completedAt,
            freshnessUntil = freshnessUntil
        )
        networkRepository.upsertAssessmentBaselineSummary(request).onFailure {
            Log.w(TAG, "syncBaselineSummary failed for $userId: ${it.message}")
        }
    }

    private suspend fun syncDoctorInquirySummary(now: Long, userId: String) {
        val latestSession = doctorRepository.getLatestSession() ?: return
        val latestAssessment = doctorRepository.getLatestAssessment(latestSession.id) ?: return
        val request = DoctorInquirySummaryUpsertRequest(
            sessionId = latestSession.id,
            assessedAt = latestAssessment.createdAt.takeIf { it > 0L } ?: now,
            riskLevel = latestSession.riskLevel,
            chiefComplaint = latestSession.chiefComplaint,
            redFlags = parseStringList(latestAssessment.redFlagsJson),
            recommendedDepartment = latestAssessment.recommendedDepartment,
            doctorSummary = latestAssessment.doctorSummary
        )
        networkRepository.upsertDoctorInquirySummary(request).onFailure {
            Log.w(TAG, "syncDoctorInquirySummary failed for $userId: ${it.message}")
        }
    }

    private fun buildPersonalizationSummary(status: PersonalizationStatus): String {
        return if (status.isPreview) {
            "当前为半个体化建议，系统只基于部分证据生成今日方案。"
        } else {
            "完整个体化处方已解锁，今日建议会联动设备、量表和问诊证据。"
        }
    }

    private fun buildMissingInputSummary(status: PersonalizationStatus): String {
        if (status.missingInputs.isEmpty()) {
            return "当前设备数据、基线量表和结构化问诊都已齐全。"
        }
        val labels = status.missingInputs.map { input ->
            when (input) {
                PersonalizationMissingInput.DEVICE_DATA -> "设备睡眠数据"
                PersonalizationMissingInput.BASELINE_ASSESSMENT -> "基线量表"
                PersonalizationMissingInput.DOCTOR_INQUIRY -> "结构化问诊"
            }
        }
        return "仍缺少：${labels.joinToString("、")}"
    }

    private suspend fun buildSnapshot(
        triggerType: ProfileTriggerType,
        now: Long
    ): InterventionProfileSnapshot {
        val scaleResults = assessmentRepository.getLatestScaleResults()
        val scaleMap = scaleResults.associateBy { it.scaleCode }
        val latestDoctorAssessment = doctorRepository.getLatestSession()
            ?.let { doctorRepository.getLatestAssessment(it.id) }
        val latestMedicalReport = medicalReportRepository.getLatestReport()
        val latestMedicalMetrics = latestMedicalReport?.let {
            medicalReportRepository.getMetricsByReport(it.id)
        }.orEmpty()
        val latestHealthMetrics = healthMetricsDao.getLatestOnce()
        val recentTasks = taskDao.getRecent(10)
        val recentExecutions = executionDao.getRecent(10)

        val evidence = linkedMapOf<String, MutableList<String>>(
            "sleepDisturbance" to mutableListOf(),
            "stressLoad" to mutableListOf(),
            "fatigueLoad" to mutableListOf(),
            "recoveryCapacity" to mutableListOf(),
            "anxietyRisk" to mutableListOf(),
            "depressiveRisk" to mutableListOf(),
            "adherenceReadiness" to mutableListOf()
        )

        val averageSleep1 = sleepDao.getAverageSleepDuration(1)
        val averageSleep7 = sleepDao.getAverageSleepDuration(7)
        val sleepEfficiency7 = sleepDao.getAverageSleepEfficiency(7)
        val deepSleepPercent7 = sleepDao.getAverageDeepSleepPercentage(7)
        val recoveryAverage7 = recoveryScoreDao.getAverageScore(7)

        val sleepDisturbance = buildSleepScore(
            averageSleep1 = averageSleep1,
            averageSleep7 = averageSleep7,
            sleepEfficiency7 = sleepEfficiency7,
            deepSleepPercent7 = deepSleepPercent7,
            isi = scaleMap["ISI"]?.totalScore,
            ess = scaleMap["ESS"]?.totalScore,
            evidence = evidence.getValue("sleepDisturbance"),
            latestDoctorAssessment = latestDoctorAssessment
        )

        val stressLoad = buildStressScore(
            pss10 = scaleMap["PSS10"]?.totalScore,
            gad7 = scaleMap["GAD7"]?.totalScore,
            latestHealthMetrics = latestHealthMetrics,
            evidence = evidence.getValue("stressLoad"),
            latestDoctorAssessment = latestDoctorAssessment
        )

        val fatigueLoad = buildFatigueScore(
            ess = scaleMap["ESS"]?.totalScore,
            recoveryAverage7 = recoveryAverage7,
            averageSleep7 = averageSleep7,
            latestHealthMetrics = latestHealthMetrics,
            evidence = evidence.getValue("fatigueLoad")
        )

        val recoveryCapacity = buildRecoveryScore(
            recoveryAverage7 = recoveryAverage7,
            averageSleep7 = averageSleep7,
            latestHealthMetrics = latestHealthMetrics,
            evidence = evidence.getValue("recoveryCapacity")
        )

        val anxietyRisk = buildAnxietyScore(
            gad7 = scaleMap["GAD7"]?.totalScore,
            pss10 = scaleMap["PSS10"]?.totalScore,
            evidence = evidence.getValue("anxietyRisk"),
            latestDoctorAssessment = latestDoctorAssessment
        )

        val depressiveRisk = buildDepressiveScore(
            phq9 = scaleMap["PHQ9"]?.totalScore,
            who5 = scaleMap["WHO5"]?.totalScore,
            evidence = evidence.getValue("depressiveRisk")
        )

        val adherenceReadiness = buildAdherenceReadiness(
            recentTasks = recentTasks,
            recentExecutionsCount = recentExecutions.size,
            evidence = evidence.getValue("adherenceReadiness")
        )

        val redFlags = collectRedFlags(
            latestDoctorAssessment = latestDoctorAssessment,
            phq9Answers = assessmentRepository.getLatestScaleAnswers("PHQ9")
        )

        if (latestMedicalMetrics.isNotEmpty()) {
            appendMedicalEvidence(evidence, latestMedicalMetrics)
        }

        val domainScores = linkedMapOf(
            "sleepDisturbance" to sleepDisturbance,
            "stressLoad" to stressLoad,
            "fatigueLoad" to fatigueLoad,
            "recoveryCapacity" to recoveryCapacity,
            "anxietyRisk" to anxietyRisk,
            "depressiveRisk" to depressiveRisk,
            "adherenceReadiness" to adherenceReadiness
        )

        return InterventionProfileSnapshot(
            id = java.util.UUID.randomUUID().toString(),
            generatedAt = now,
            triggerType = triggerType,
            domainScores = domainScores,
            evidenceFacts = evidence.mapValues { (_, value) -> value.distinct() },
            redFlags = redFlags.distinct()
        )
    }

    private fun buildSleepScore(
        averageSleep1: Float?,
        averageSleep7: Float?,
        sleepEfficiency7: Float?,
        deepSleepPercent7: Float?,
        isi: Int?,
        ess: Int?,
        evidence: MutableList<String>,
        latestDoctorAssessment: DoctorAssessmentEntity?
    ): Int {
        var score = 20
        averageSleep1?.let {
            when {
                it < 360f -> score += 18
                it < 420f -> score += 10
            }
            evidence += "近 1 天平均睡眠 ${formatMinutes(it)}"
        }
        averageSleep7?.let {
            when {
                it < 360f -> score += 22
                it < 420f -> score += 12
            }
            evidence += "近 7 天平均睡眠 ${formatMinutes(it)}"
        }
        sleepEfficiency7?.let {
            when {
                it < 80f -> score += 18
                it < 88f -> score += 10
            }
            evidence += "近 7 天平均睡眠效率 ${it.roundToInt()}%"
        }
        deepSleepPercent7?.let {
            if (it < 15f) score += 8
            evidence += "近 7 天深睡占比 ${it.roundToInt()}%"
        }
        isi?.let {
            score += (it * 2.5f).roundToInt().coerceAtMost(40)
            evidence += "ISI ${it} 分"
        }
        ess?.let {
            if (it >= 11) {
                score += 6
                evidence += "ESS ${it} 分，提示日间嗜睡"
            }
        }
        val symptomFacts = parseStringList(latestDoctorAssessment?.symptomFactsJson)
        if (symptomFacts.any { it.contains("失眠") || it.contains("早醒") || it.contains("睡眠浅") }) {
            score += 8
            evidence += "问诊记录提示存在睡眠困扰"
        }
        return clamp(score)
    }

    private fun buildStressScore(
        pss10: Int?,
        gad7: Int?,
        latestHealthMetrics: HealthMetricsEntity?,
        evidence: MutableList<String>,
        latestDoctorAssessment: DoctorAssessmentEntity?
    ): Int {
        var score = 18
        pss10?.let {
            score += (it * 2.2f).roundToInt()
            evidence += "PSS-10 ${it} 分"
        }
        gad7?.let {
            score += (it * 1.8f).roundToInt()
            evidence += "GAD-7 ${it} 分"
        }
        latestHealthMetrics?.let {
            if (it.hrvCurrent < it.hrvBaseline) {
                score += 8
                evidence += "当前 HRV 低于基线"
            }
            if (it.heartRateAvg >= 85) {
                score += 6
                evidence += "近期平均心率偏高"
            }
        }
        val symptomFacts = parseStringList(latestDoctorAssessment?.symptomFactsJson)
        if (symptomFacts.any { it.contains("压力") || it.contains("紧张") || it.contains("焦虑") }) {
            score += 8
            evidence += "问诊记录提示主观压力较高"
        }
        return clamp(score)
    }

    private fun buildFatigueScore(
        ess: Int?,
        recoveryAverage7: Float?,
        averageSleep7: Float?,
        latestHealthMetrics: HealthMetricsEntity?,
        evidence: MutableList<String>
    ): Int {
        var score = 16
        ess?.let {
            score += (it * 3.4f).roundToInt()
            evidence += "ESS ${it} 分"
        }
        recoveryAverage7?.let {
            if (it < 60f) {
                score += 16
            }
            evidence += "近 7 天恢复均分 ${it.roundToInt()}"
        }
        averageSleep7?.let {
            if (it < 420f) {
                score += 12
                evidence += "近 7 天睡眠不足 ${formatMinutes(it)}"
            }
        }
        latestHealthMetrics?.let {
            if (it.hrvCurrent < (it.hrvBaseline * 0.85f).roundToInt()) {
                score += 8
                evidence += "HRV 恢复不足"
            }
        }
        return clamp(score)
    }

    private fun buildRecoveryScore(
        recoveryAverage7: Float?,
        averageSleep7: Float?,
        latestHealthMetrics: HealthMetricsEntity?,
        evidence: MutableList<String>
    ): Int {
        var score = 55
        recoveryAverage7?.let {
            score = it.roundToInt()
            evidence += "近 7 天恢复均分 ${it.roundToInt()}"
        }
        averageSleep7?.let {
            if (it < 360f) {
                score -= 18
            } else if (it < 420f) {
                score -= 10
            }
            evidence += "睡眠时长会直接影响恢复能力"
        }
        latestHealthMetrics?.let {
            if (it.hrvCurrent >= it.hrvBaseline) {
                score += 8
                evidence += "当前 HRV 已回到基线附近"
            } else {
                score -= 8
                evidence += "当前 HRV 仍低于基线"
            }
        }
        return clamp(score)
    }

    private fun buildAnxietyScore(
        gad7: Int?,
        pss10: Int?,
        evidence: MutableList<String>,
        latestDoctorAssessment: DoctorAssessmentEntity?
    ): Int {
        var score = 12
        gad7?.let {
            score += (it * 3.4f).roundToInt()
            evidence += "GAD-7 ${it} 分"
        }
        pss10?.let {
            if (it >= 27) {
                score += 10
                evidence += "PSS-10 提示高压力"
            }
        }
        val symptomFacts = parseStringList(latestDoctorAssessment?.symptomFactsJson)
        if (symptomFacts.any { it.contains("焦虑") || it.contains("坐立不安") }) {
            score += 10
            evidence += "问诊记录提示焦虑症状"
        }
        return clamp(score)
    }

    private fun buildDepressiveScore(
        phq9: Int?,
        who5: Int?,
        evidence: MutableList<String>
    ): Int {
        var score = 10
        phq9?.let {
            score += (it * 3.1f).roundToInt()
            evidence += "PHQ-9 ${it} 分"
        }
        who5?.let {
            val penalty = ((25 - it).coerceAtLeast(0) * 2.4f).roundToInt()
            score += penalty
            evidence += "WHO-5 ${it} 分"
        }
        return clamp(score)
    }

    private fun buildAdherenceReadiness(
        recentTasks: List<InterventionTaskEntity>,
        recentExecutionsCount: Int,
        evidence: MutableList<String>
    ): Int {
        if (recentTasks.isEmpty()) {
            evidence += "尚未形成稳定干预历史"
            return 55
        }
        val completedCount = recentTasks.count { it.status == "COMPLETED" }
        val completionRate = completedCount.toFloat() / recentTasks.size.toFloat()
        val readiness = (completionRate * 100f).roundToInt().coerceIn(20, 95)
        evidence += "近 ${recentTasks.size} 次任务完成率 ${(completionRate * 100).roundToInt()}%"
        if (recentExecutionsCount > 0) {
            evidence += "已有 ${recentExecutionsCount} 次完成回执"
        }
        return readiness
    }

    private fun collectRedFlags(
        latestDoctorAssessment: DoctorAssessmentEntity?,
        phq9Answers: Map<String, Int>
    ): List<String> {
        val flags = mutableListOf<String>()
        flags += parseStringList(latestDoctorAssessment?.redFlagsJson)
        val phq9Item9 = phq9Answers["PHQ9_9"] ?: 0
        if (phq9Item9 > 0) {
            flags += "PHQ-9 第 9 题阳性，建议医生优先评估"
        }
        return flags.distinct()
    }

    private fun appendMedicalEvidence(
        evidence: MutableMap<String, MutableList<String>>,
        metrics: List<MedicalMetricEntity>
    ) {
        val abnormal = metrics.filter { it.isAbnormal }
        if (abnormal.isEmpty()) {
            return
        }
        abnormal.take(4).forEach { metric ->
            val fact = "${metric.metricName} ${metric.metricValue.formatCompact()}${metric.unit} 异常"
            when (metric.metricCode) {
                "BP", "SBP", "DBP" -> {
                    evidence.getValue("stressLoad") += fact
                    evidence.getValue("fatigueLoad") += fact
                }
                "GLU", "HBA1C" -> {
                    evidence.getValue("fatigueLoad") += fact
                }
                "TC", "LDL", "TG", "UA" -> {
                    evidence.getValue("recoveryCapacity") += fact
                }
            }
        }
    }

    private suspend fun buildMedicalSummary(): String {
        val latestMedicalReport = medicalReportRepository.getLatestReport() ?: return "暂无医检摘要"
        val abnormalMetrics = medicalReportRepository.getMetricsByReport(latestMedicalReport.id)
            .filter { it.isAbnormal }
            .map { "${it.metricName}${it.metricValue.formatCompact()}${it.unit}" }
        return if (abnormalMetrics.isEmpty()) {
            "最近一次医检未发现重点异常"
        } else {
            "最近一次医检提示 ${abnormalMetrics.take(3).joinToString("、")}" 
        }
    }

    private fun buildAdherenceHint(recentTasks: List<InterventionTaskEntity>): String {
        if (recentTasks.isEmpty()) {
            return "还没有稳定的执行历史，建议先从短时干预开始。"
        }
        val completedCount = recentTasks.count { it.status == "COMPLETED" }
        val completionRate = (completedCount.toFloat() / recentTasks.size.toFloat() * 100f).roundToInt()
        return if (completionRate >= 70) {
            "近 ${recentTasks.size} 次干预完成率 ${completionRate}%，可以安排更完整的处方包。"
        } else {
            "近 ${recentTasks.size} 次干预完成率 ${completionRate}%，优先推荐更短、更容易开始的方案。"
        }
    }

    private fun InterventionProfileSnapshot.toEntity(): InterventionProfileSnapshotEntity {
        return InterventionProfileSnapshotEntity(
            id = id,
            generatedAt = generatedAt,
            triggerType = triggerType.name,
            domainScoresJson = gson.toJson(domainScores),
            evidenceFactsJson = gson.toJson(evidenceFacts),
            redFlagsJson = gson.toJson(redFlags)
        )
    }

    private fun InterventionProfileSnapshotEntity.toModel(): InterventionProfileSnapshot {
        val domainType = object : TypeToken<Map<String, Int>>() {}.type
        val evidenceType = object : TypeToken<Map<String, List<String>>>() {}.type
        val listType = object : TypeToken<List<String>>() {}.type
        return InterventionProfileSnapshot(
            id = id,
            generatedAt = generatedAt,
            triggerType = runCatching { ProfileTriggerType.valueOf(triggerType) }
                .getOrDefault(ProfileTriggerType.DAILY_REFRESH),
            domainScores = gson.fromJson(domainScoresJson, domainType) ?: emptyMap(),
            evidenceFacts = gson.fromJson(evidenceFactsJson, evidenceType) ?: emptyMap(),
            redFlags = gson.fromJson(redFlagsJson, listType) ?: emptyList()
        )
    }

    private fun parseStringList(rawJson: String?): List<String> {
        if (rawJson.isNullOrBlank()) return emptyList()
        return runCatching {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson<List<String>>(rawJson, type).orEmpty()
        }.getOrElse {
            Log.w(TAG, "parseStringList failed: ${it.message}")
            emptyList()
        }
    }

    private fun formatMinutes(minutes: Float): String {
        val total = minutes.roundToInt()
        val hours = total / 60
        val mins = total % 60
        return "${hours}小时${mins}分钟"
    }

    private fun clamp(value: Int): Int = value.coerceIn(0, 100)

    private fun Float.formatCompact(): String {
        val rounded = (this * 10).roundToInt() / 10f
        return if (rounded % 1f == 0f) rounded.toInt().toString() else rounded.toString()
    }
}
