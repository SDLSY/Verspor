package com.example.newstart.repository

import android.content.Context
import com.example.newstart.database.AppDatabase
import com.example.newstart.database.entity.AssessmentAnswerEntity
import com.example.newstart.database.entity.AssessmentSessionEntity
import com.example.newstart.intervention.AssessmentCatalog
import com.example.newstart.intervention.AssessmentScaleDefinition
import com.example.newstart.intervention.AssessmentScaleResult
import com.example.newstart.intervention.AssessmentSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class AssessmentRepository(
    private val context: Context,
    private val db: AppDatabase = AppDatabase.getDatabase(context)
) {
    private val sessionDao = db.assessmentSessionDao()
    private val answerDao = db.assessmentAnswerDao()

    suspend fun getBaselineDefinitions(): List<AssessmentScaleDefinition> = withContext(Dispatchers.IO) {
        AssessmentCatalog.baseline(context)
    }

    suspend fun getDefinition(scaleCode: String): AssessmentScaleDefinition? = withContext(Dispatchers.IO) {
        AssessmentCatalog.find(context, scaleCode)
    }

    suspend fun hasFreshBaseline(now: Long = System.currentTimeMillis()): Boolean = withContext(Dispatchers.IO) {
        val freshCodes = sessionDao.getFreshSessions(now)
            .map { it.scaleCode }
            .toSet()
        AssessmentCatalog.baselineScaleCodes.all { it in freshCodes }
    }

    suspend fun saveCompletedScale(
        scaleCode: String,
        answers: Map<String, Int>,
        source: AssessmentSource
    ): AssessmentScaleResult = withContext(Dispatchers.IO) {
        val definition = AssessmentCatalog.find(context, scaleCode)
            ?: error("Unknown scale code: $scaleCode")
        val now = System.currentTimeMillis()
        val scoredAnswers = definition.questions.map { question ->
            val rawValue = answers[question.code] ?: 0
            val maxValue = question.options.maxOfOrNull { it.value } ?: 0
            if (question.reverseScore) {
                maxValue - rawValue
            } else {
                rawValue
            }
        }
        val totalScore = scoredAnswers.sum()
        val severity = definition.severityBands.firstOrNull { it.matches(totalScore) }?.label ?: "未分层"
        val freshnessUntil = now + TimeUnit.DAYS.toMillis(definition.freshnessDays.toLong())

        val session = AssessmentSessionEntity(
            scaleCode = scaleCode,
            startedAt = now,
            completedAt = now,
            totalScore = totalScore,
            severityLevel = severity,
            freshnessUntil = freshnessUntil,
            source = source.name
        )
        sessionDao.upsert(session)
        answerDao.deleteBySession(session.id)
        answerDao.insertAll(
            definition.questions.mapIndexed { index, question ->
                AssessmentAnswerEntity(
                    sessionId = session.id,
                    itemCode = question.code,
                    itemOrder = index,
                    answerValue = answers[question.code] ?: 0
                )
            }
        )
        session.toScaleResult(definition.title)
    }

    suspend fun getLatestScaleResult(scaleCode: String): AssessmentScaleResult? = withContext(Dispatchers.IO) {
        val definition = AssessmentCatalog.find(context, scaleCode) ?: return@withContext null
        sessionDao.getLatestCompletedByScale(scaleCode)?.toScaleResult(definition.title)
    }

    suspend fun getLatestScaleSession(scaleCode: String): AssessmentSessionEntity? = withContext(Dispatchers.IO) {
        sessionDao.getLatestCompletedByScale(scaleCode)
    }

    suspend fun getAnswersForSession(sessionId: String): List<AssessmentAnswerEntity> = withContext(Dispatchers.IO) {
        answerDao.getBySession(sessionId)
    }

    suspend fun getLatestScaleAnswers(scaleCode: String): Map<String, Int> = withContext(Dispatchers.IO) {
        val session = sessionDao.getLatestCompletedByScale(scaleCode) ?: return@withContext emptyMap()
        answerDao.getBySession(session.id).associate { it.itemCode to it.answerValue }
    }

    suspend fun getLatestScaleResults(
        scaleCodes: Collection<String> = AssessmentCatalog.baselineScaleCodes
    ): List<AssessmentScaleResult> = withContext(Dispatchers.IO) {
        scaleCodes.mapNotNull { code -> getLatestScaleResult(code) }
    }

    private fun AssessmentSessionEntity.toScaleResult(scaleTitle: String): AssessmentScaleResult {
        return AssessmentScaleResult(
            scaleCode = scaleCode,
            scaleTitle = scaleTitle,
            totalScore = totalScore,
            severityLabel = severityLevel,
            completedAt = completedAt ?: startedAt,
            freshnessUntil = freshnessUntil,
            source = source
        )
    }
}
