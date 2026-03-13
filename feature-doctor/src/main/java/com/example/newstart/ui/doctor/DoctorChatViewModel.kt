package com.example.newstart.ui.doctor

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.newstart.core.common.R
import com.example.newstart.database.AppDatabase
import com.example.newstart.database.entity.DoctorAssessmentEntity
import com.example.newstart.database.entity.DoctorMessageEntity
import com.example.newstart.database.entity.DoctorSessionEntity
import com.example.newstart.database.entity.InterventionTaskEntity
import com.example.newstart.repository.DoctorConversationRepository
import com.example.newstart.repository.InterventionRepository
import com.example.newstart.repository.InterventionTaskSourceType
import com.example.newstart.repository.InterventionTaskStatus
import com.example.newstart.repository.NetworkRepository
import com.example.newstart.repository.SleepRepository
import com.example.newstart.network.models.DoctorInquirySummaryUpsertRequest
import com.example.newstart.service.ai.DoctorAiService
import com.example.newstart.service.ai.RetrievalService
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class DoctorRecommendation(
    val protocolType: String,
    val durationSec: Int,
    val reason: String,
    val expectedEffect: String,
    val confidence: Int
)

data class DoctorRecommendationExplanationUiState(
    val visible: Boolean = false,
    val summary: String = "",
    val reasons: List<String> = emptyList(),
    val nextStep: String = "",
    val metaLabel: String = ""
)

data class DoctorUiState(
    val riskLevelText: String = "--",
    val riskScore: Int = 0,
    val dataFreshnessText: String = "--",
    val suggestionSourceText: String = "--",
    val confidenceText: String = "--",
    val modelStatusText: String = "--",
    val recommendedInterventions: List<DoctorRecommendation> = emptyList(),
    val messages: List<DoctorChatMessage> = emptyList(),
    val isSending: Boolean = false,
    val currentStage: DoctorInquiryStage = DoctorInquiryStage.INTAKE,
    val currentSessionId: String = "",
    val historySummaries: List<DoctorHistorySummary> = emptyList(),
    val canGenerateAssessment: Boolean = false,
    val recommendationExplanation: DoctorRecommendationExplanationUiState = DoctorRecommendationExplanationUiState()
)

sealed class DoctorEvent {
    data class NavigateToBreathing(
        val protocolType: String,
        val durationSec: Int,
        val taskId: String
    ) : DoctorEvent()

    data class Toast(val message: String) : DoctorEvent()
}

private data class SnapshotBundle(
    val snapshot: DoctorMetricSnapshot,
    val freshnessText: String
)

private data class DoctorTurnUiResult(
    val turn: DoctorTurnDecision,
    val riskSummary: DoctorRiskSummary,
    val freshnessText: String,
    val recommendations: List<DoctorRecommendation>
)

class DoctorChatViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "DoctorChatViewModel"
    }

    private val app = application
    private val gson = Gson()
    private val db = AppDatabase.getDatabase(application)

    private val sleepRepository = SleepRepository(
        db.sleepDataDao(),
        db.healthMetricsDao(),
        db.recoveryScoreDao(),
        db.ppgSampleDao()
    )

    private val interventionRepository = InterventionRepository(
        taskDao = db.interventionTaskDao(),
        executionDao = db.interventionExecutionDao()
    )

    private val doctorRepository = DoctorConversationRepository(
        sessionDao = db.doctorSessionDao(),
        assessmentDao = db.doctorAssessmentDao(),
        messageDao = db.doctorMessageDao()
    )
    private val networkRepository = NetworkRepository()
    private val doctorAiService = DoctorAiService(application, networkRepository)

    private val healthMetricsDao = db.healthMetricsDao()

    private val _uiState = MutableLiveData(DoctorUiState())
    val uiState: LiveData<DoctorUiState> = _uiState

    private val _event = MutableLiveData<DoctorEvent?>()
    val event: LiveData<DoctorEvent?> = _event

    private var activeSessionId: String = ""

    init {
        viewModelScope.launch {
            bootstrap()
        }
    }

    fun sendMessage(raw: String) {
        val text = raw.trim()
        val state = _uiState.value ?: DoctorUiState()
        if (text.isBlank() || state.isSending) return

        val pendingId = "pending_${System.currentTimeMillis()}"
        val userMessage = DoctorChatMessage.user(text)
        val thinkingMessage = DoctorChatMessage.assistant(
            content = app.getString(R.string.doctor_thinking),
            isPending = true
        ).copy(id = pendingId)

        updateState {
            it.copy(
                messages = it.messages + userMessage + thinkingMessage,
                isSending = true
            )
        }

        viewModelScope.launch {
            try {
                val session = ensureActiveSession()
                if (session.chiefComplaint.isBlank()) {
                    doctorRepository.upsertSession(
                        session.copy(
                            chiefComplaint = DoctorDecisionEngine.inferChiefComplaint(text),
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
                persistMessage(session.id, userMessage)
                val result = withContext(Dispatchers.IO) {
                    buildDoctorTurn(
                        session = session,
                        latestUserMessage = text,
                        conversation = state.messages
                    )
                }
                val assistantMessage = when {
                    result.turn.followUp != null -> DoctorChatMessage.followUp(result.turn.followUp)
                    result.turn.assessment != null -> DoctorChatMessage.assessment(result.turn.assessment)
                    else -> DoctorChatMessage.assistant(app.getString(R.string.doctor_error_retry))
                }
                persistTurnResult(session, assistantMessage, result)
                updateState {
                    it.copy(
                        messages = it.messages.removePending(pendingId) + assistantMessage,
                        isSending = false,
                        suggestionSourceText = suggestionSourceText(result.turn.source),
                        confidenceText = app.getString(R.string.doctor_confidence_format, result.riskSummary.confidence),
                        riskLevelText = mapRiskLevelText(result.riskSummary.level),
                        riskScore = result.riskSummary.score,
                        dataFreshnessText = result.freshnessText,
                        recommendedInterventions = result.recommendations,
                        modelStatusText = modelStatusText(result.turn.source),
                        currentStage = result.turn.nextStage,
                        canGenerateAssessment = canGenerateAssessment(
                            result.turn.nextStage,
                            it.messages + assistantMessage
                        )
                    )
                }
                refreshRecommendationExplanation()
                refreshHistory()
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                val fallbackMessage = DoctorChatMessage.assistant(
                    content = app.getString(R.string.doctor_error_retry)
                )
                val sessionId = activeSessionId
                if (sessionId.isNotBlank()) {
                    persistMessage(sessionId, fallbackMessage)
                }
                updateState {
                    it.copy(
                        messages = it.messages.removePending(pendingId) + fallbackMessage,
                        isSending = false,
                        suggestionSourceText = app.getString(R.string.doctor_source_local),
                        modelStatusText = app.getString(R.string.doctor_model_status_local_active),
                        recommendationExplanation = DoctorRecommendationExplanationUiState()
                    )
                }
                _event.value = DoctorEvent.Toast(app.getString(R.string.doctor_error_toast))
            }
        }
    }

    fun onRecommendationStart(index: Int) {
        val recommendation = _uiState.value
            ?.recommendedInterventions
            ?.getOrNull(index)
            ?: return
        launchIntervention(recommendation)
    }

    fun onMessageAction(action: DoctorMessageAction) {
        val state = _uiState.value ?: return
        val recommendation = state.recommendedInterventions.firstOrNull {
            it.protocolType == action.protocolType && it.durationSec == action.durationSec
        } ?: DoctorRecommendation(
            protocolType = action.protocolType,
            durationSec = action.durationSec,
            reason = app.getString(R.string.doctor_reason_action_from_reply),
            expectedEffect = app.getString(R.string.doctor_effect_generic),
            confidence = extractConfidenceNumber(state.confidenceText)
        )
        launchIntervention(recommendation)
    }

    fun onQuickQuestionAsked(question: String) {
        sendMessage(question)
    }

    fun generateAssessmentNow() {
        val state = _uiState.value ?: return
        if (state.isSending || activeSessionId.isBlank()) return
        val pendingId = "pending_${System.currentTimeMillis()}"
        val thinkingMessage = DoctorChatMessage.assistant(
            content = app.getString(R.string.doctor_generating_assessment),
            isPending = true
        ).copy(id = pendingId)
        updateState {
            it.copy(messages = it.messages + thinkingMessage, isSending = true)
        }
        viewModelScope.launch {
            try {
                val session = ensureActiveSession()
                val result = withContext(Dispatchers.IO) {
                    buildDoctorTurn(
                        session = session.copy(status = DoctorInquiryStage.ASSESSING.name),
                        latestUserMessage = app.getString(R.string.doctor_force_assessment_prompt),
                        conversation = state.messages
                    )
                }
                val assistantMessage = result.turn.assessment?.let { DoctorChatMessage.assessment(it) }
                    ?: DoctorChatMessage.assistant(app.getString(R.string.doctor_error_retry))
                persistTurnResult(session, assistantMessage, result)
                updateState {
                    it.copy(
                        messages = it.messages.removePending(pendingId) + assistantMessage,
                        isSending = false,
                        suggestionSourceText = suggestionSourceText(result.turn.source),
                        modelStatusText = modelStatusText(result.turn.source),
                        currentStage = result.turn.nextStage,
                        canGenerateAssessment = false
                    )
                }
                refreshRecommendationExplanation()
                refreshHistory()
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                updateState {
                    it.copy(
                        messages = it.messages.removePending(pendingId),
                        isSending = false
                    )
                }
                _event.value = DoctorEvent.Toast(app.getString(R.string.doctor_error_toast))
            }
        }
    }

    fun restartConversation() {
        if ((_uiState.value?.isSending == true)) return
        viewModelScope.launch {
            val session = withContext(Dispatchers.IO) { createNewSession() }
            val intro = buildIntroMessage()
            persistMessage(session.id, intro)
            activeSessionId = session.id
            val snapshotBundle = withContext(Dispatchers.IO) { loadSnapshotBundle() }
            val riskSummary = DoctorDecisionEngine.evaluateRisk(snapshotBundle.snapshot)
            val recommendations = DoctorDecisionEngine
                .buildRecommendations(riskSummary, snapshotBundle.snapshot)
                .take(2)
                .map { mapRecommendation(it) }
            updateState {
                it.copy(
                    messages = listOf(intro),
                    currentStage = DoctorInquiryStage.INTAKE,
                    currentSessionId = session.id,
                    canGenerateAssessment = false,
                    suggestionSourceText = app.getString(R.string.doctor_source_engine),
                    modelStatusText = defaultModelStatusText(),
                    recommendedInterventions = recommendations,
                    riskLevelText = mapRiskLevelText(riskSummary.level),
                    riskScore = riskSummary.score,
                    confidenceText = app.getString(R.string.doctor_confidence_format, riskSummary.confidence),
                    dataFreshnessText = snapshotBundle.freshnessText
                )
            }
            refreshHistory()
        }
    }

    fun loadSession(sessionId: String) {
        if (sessionId.isBlank()) return
        viewModelScope.launch {
            val session = withContext(Dispatchers.IO) { doctorRepository.getSession(sessionId) } ?: return@launch
            val messages = withContext(Dispatchers.IO) {
                val loadedMessages = loadMessagesForSession(session.id)
                backfillAssessmentIfNeeded(session.id, loadedMessages)
                loadedMessages
            }
            activeSessionId = session.id
            updateState {
                it.copy(
                    currentSessionId = session.id,
                    currentStage = parseStage(session.status),
                    messages = messages,
                    canGenerateAssessment = canGenerateAssessment(parseStage(session.status), messages)
                )
            }
            refreshRecommendationExplanation()
        }
    }

    fun consumeEvent() {
        _event.value = null
    }

    private suspend fun bootstrap() {
        refreshHistory()
        val snapshotBundle = withContext(Dispatchers.IO) { loadSnapshotBundle() }
        val riskSummary = DoctorDecisionEngine.evaluateRisk(snapshotBundle.snapshot)
        val recommendations = DoctorDecisionEngine
            .buildRecommendations(riskSummary, snapshotBundle.snapshot)
            .take(2)
            .map { mapRecommendation(it) }

        val latestSession = withContext(Dispatchers.IO) { doctorRepository.getLatestSession() }
        val session = if (latestSession == null || parseStage(latestSession.status).let { it == DoctorInquiryStage.COMPLETED || it == DoctorInquiryStage.ESCALATED }) {
            withContext(Dispatchers.IO) { createNewSession() }
        } else {
            latestSession
        }
        activeSessionId = session.id
        var messages = withContext(Dispatchers.IO) {
            val loadedMessages = loadMessagesForSession(session.id)
            backfillAssessmentIfNeeded(session.id, loadedMessages)
            loadedMessages
        }
        if (messages.isEmpty()) {
            val intro = buildIntroMessage()
            persistMessage(session.id, intro)
            messages = listOf(intro)
        }

        updateState {
            it.copy(
                messages = messages,
                riskLevelText = mapRiskLevelText(riskSummary.level),
                riskScore = riskSummary.score,
                dataFreshnessText = snapshotBundle.freshnessText,
                suggestionSourceText = app.getString(R.string.doctor_source_engine),
                confidenceText = app.getString(R.string.doctor_confidence_format, riskSummary.confidence),
                modelStatusText = defaultModelStatusText(),
                recommendedInterventions = recommendations,
                currentStage = parseStage(session.status),
                currentSessionId = session.id,
                canGenerateAssessment = canGenerateAssessment(parseStage(session.status), messages)
            )
        }
        refreshRecommendationExplanation()
    }

    private suspend fun buildDoctorTurn(
        session: DoctorSessionEntity,
        latestUserMessage: String,
        conversation: List<DoctorChatMessage>
    ): DoctorTurnUiResult {
        val snapshotBundle = loadSnapshotBundle()
        val riskSummary = DoctorDecisionEngine.evaluateRisk(snapshotBundle.snapshot)
        val recommendations = DoctorDecisionEngine
            .buildRecommendations(riskSummary, snapshotBundle.snapshot)
            .take(2)
            .map { mapRecommendation(it) }

        val ragContext = buildRagContext(latestUserMessage)
        val contextBlock = buildContextBlock(snapshotBundle.snapshot)
        val followUpCount = conversation.count { it.messageType == DoctorMessageType.FOLLOW_UP }

        val stage = parseStage(session.status)
        val conversationBlock = DoctorInferenceEngine.buildConversationBlock(
            conversation + DoctorChatMessage.user(latestUserMessage)
        )
        val turn = doctorAiService.generateCloudTurn(
            conversationBlock = conversationBlock,
            contextBlock = contextBlock,
            ragContext = ragContext,
            stage = stage,
            followUpCount = followUpCount,
            defaultRiskLevel = riskSummary.level.name
        ) ?: DoctorInferenceEngine.generateTurn(
            DoctorInferenceInput(
                latestUserMessage = latestUserMessage,
                conversation = conversation,
                stage = stage,
                followUpCount = followUpCount,
                snapshot = snapshotBundle.snapshot,
                riskSummary = riskSummary,
                contextBlock = contextBlock,
                ragContext = ragContext
            )
        )
        return DoctorTurnUiResult(
            turn = turn,
            riskSummary = riskSummary,
            freshnessText = snapshotBundle.freshnessText,
            recommendations = recommendations
        )
    }

    private suspend fun persistTurnResult(
        session: DoctorSessionEntity,
        assistantMessage: DoctorChatMessage,
        result: DoctorTurnUiResult
    ) {
        withContext(Dispatchers.IO) {
            persistMessage(session.id, assistantMessage)
            val assessment = assistantMessage.assessmentPayload ?: result.turn.assessment
            if (assessment != null) {
                upsertAssessmentSnapshot(session.id, assessment)
            }
            val updatedSession = session.copy(
                updatedAt = System.currentTimeMillis(),
                status = result.turn.nextStage.name,
                chiefComplaint = assessment?.chiefComplaint?.takeIf { it.isNotBlank() }
                    ?: session.chiefComplaint.ifBlank { assistantMessage.content.take(48) },
                riskLevel = result.turn.assessment?.riskLevel ?: result.riskSummary.level.name
            )
            doctorRepository.upsertSession(updatedSession)
            if (assessment != null) {
                syncDoctorInquirySummaryIfPossible(updatedSession, assessment)
            }
        }
    }

    private suspend fun backfillAssessmentIfNeeded(
        sessionId: String,
        messages: List<DoctorChatMessage>
    ) {
        if (doctorRepository.getLatestAssessment(sessionId) != null) return
        val payload = messages
            .lastOrNull { it.messageType == DoctorMessageType.ASSESSMENT }
            ?.assessmentPayload
            ?: return
        upsertAssessmentSnapshot(sessionId, payload)
    }

    private suspend fun upsertAssessmentSnapshot(
        sessionId: String,
        assessment: DoctorAssessmentPayload
    ) {
        doctorRepository.saveAssessment(
            DoctorAssessmentEntity(
                sessionId = sessionId,
                suspectedIssuesJson = gson.toJson(assessment.suspectedIssues),
                symptomFactsJson = gson.toJson(assessment.symptomFacts),
                missingInfoJson = gson.toJson(assessment.missingInfo),
                redFlagsJson = gson.toJson(assessment.redFlags),
                recommendedDepartment = assessment.recommendedDepartment,
                doctorSummary = assessment.doctorSummary,
                nextStepAdviceJson = gson.toJson(assessment.nextStepAdvice),
                disclaimer = assessment.disclaimer
            )
        )
    }

    private suspend fun syncDoctorInquirySummaryIfPossible(
        session: DoctorSessionEntity,
        assessment: DoctorAssessmentPayload
    ) {
        val cloudSession = networkRepository.getCurrentSession() ?: return
        val request = DoctorInquirySummaryUpsertRequest(
            sessionId = session.id,
            assessedAt = System.currentTimeMillis(),
            riskLevel = assessment.riskLevel.ifBlank { session.riskLevel },
            chiefComplaint = assessment.chiefComplaint.ifBlank { session.chiefComplaint },
            redFlags = assessment.redFlags,
            recommendedDepartment = assessment.recommendedDepartment,
            doctorSummary = assessment.doctorSummary
        )
        networkRepository.upsertDoctorInquirySummary(request).onFailure {
            Log.w(TAG, "syncDoctorInquirySummaryIfPossible failed for ${cloudSession.userId}: ${it.message}")
        }
    }

    private suspend fun loadSnapshotBundle(): SnapshotBundle {
        val now = System.currentTimeMillis()
        val metricsEntity = healthMetricsDao.getLatestOnce()
        val sleep = sleepRepository.getLatestSleepData().first()
        val recovery = sleepRepository.getLatestRecoveryScore().first()

        val snapshot = DoctorMetricSnapshot(
            recoveryScore = recovery?.score ?: 0,
            sleepMinutes = sleep?.totalSleepMinutes ?: 0,
            sleepEfficiency = sleep?.sleepEfficiency ?: 0f,
            awakeCount = sleep?.awakeCount ?: 0,
            heartRate = metricsEntity?.heartRateSample ?: 0,
            spo2Min = metricsEntity?.bloodOxygenMin ?: 0,
            hrvCurrent = metricsEntity?.hrvCurrent ?: 0,
            hrvBaseline = metricsEntity?.hrvBaseline ?: 0
        )

        val freshnessText = if (metricsEntity == null) {
            app.getString(R.string.doctor_freshness_unknown)
        } else {
            val minutes = ((now - metricsEntity.timestamp) / 60_000L).coerceAtLeast(0)
            app.getString(R.string.doctor_freshness_format, minutes)
        }
        return SnapshotBundle(snapshot = snapshot, freshnessText = freshnessText)
    }

    private fun buildContextBlock(snapshot: DoctorMetricSnapshot): String {
        return """
            recovery_score=${snapshot.recoveryScore}
            sleep_minutes=${snapshot.sleepMinutes}
            sleep_efficiency=${snapshot.sleepEfficiency}
            awake_count=${snapshot.awakeCount}
            heart_rate=${snapshot.heartRate}
            spo2_min=${snapshot.spo2Min}
            hrv_current=${snapshot.hrvCurrent}
            hrv_baseline=${snapshot.hrvBaseline}
        """.trimIndent()
    }

    private fun buildRagContext(question: String): String {
        return RetrievalService.buildDoctorRagContext(app, question)
    }

    private fun mapRecommendation(draft: DoctorRecommendationDraft): DoctorRecommendation {
        val reason = when (draft.template) {
            DoctorRecommendationTemplate.STABILIZE -> app.getString(R.string.doctor_reason_stabilize)
            DoctorRecommendationTemplate.BALANCE -> app.getString(R.string.doctor_reason_balance)
            DoctorRecommendationTemplate.SLEEP_PREP -> app.getString(R.string.doctor_reason_sleep_prep)
        }
        val effect = when (draft.template) {
            DoctorRecommendationTemplate.STABILIZE -> app.getString(R.string.doctor_effect_stabilize)
            DoctorRecommendationTemplate.BALANCE -> app.getString(R.string.doctor_effect_balance)
            DoctorRecommendationTemplate.SLEEP_PREP -> app.getString(R.string.doctor_effect_sleep_prep)
        }
        return DoctorRecommendation(
            protocolType = draft.protocolType,
            durationSec = draft.durationSec,
            reason = reason,
            expectedEffect = effect,
            confidence = draft.confidence
        )
    }

    private fun launchIntervention(recommendation: DoctorRecommendation) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val task = InterventionTaskEntity(
                date = startOfDay(now),
                sourceType = InterventionTaskSourceType.AI_COACH.name,
                triggerReason = recommendation.reason,
                bodyZone = "CHEST",
                protocolType = recommendation.protocolType,
                durationSec = recommendation.durationSec,
                plannedAt = now,
                status = InterventionTaskStatus.PENDING.name,
                createdAt = now,
                updatedAt = now
            )
            withContext(Dispatchers.IO) {
                interventionRepository.upsertTask(task)
            }
            _event.value = DoctorEvent.NavigateToBreathing(
                protocolType = recommendation.protocolType,
                durationSec = recommendation.durationSec,
                taskId = task.id
            )
        }
    }

    private suspend fun loadMessagesForSession(sessionId: String): List<DoctorChatMessage> {
        return doctorRepository.getMessages(sessionId, 80).map { entity ->
            val messageType = entity.messageType.toDoctorMessageType()
            val actionProtocolType = entity.actionProtocolType
            val actionDurationSec = entity.actionDurationSec
            DoctorChatMessage(
                id = entity.id,
                role = if (entity.role == DoctorRole.USER.name) DoctorRole.USER else DoctorRole.ASSISTANT,
                content = entity.content,
                timestamp = entity.timestamp,
                messageType = messageType,
                followUpPayload = if (messageType == DoctorMessageType.FOLLOW_UP && !entity.payloadJson.isNullOrBlank()) {
                    runCatching { gson.fromJson(entity.payloadJson, DoctorFollowUpPayload::class.java) }.getOrNull()
                } else {
                    null
                },
                assessmentPayload = if (messageType == DoctorMessageType.ASSESSMENT && !entity.payloadJson.isNullOrBlank()) {
                    runCatching { gson.fromJson(entity.payloadJson, DoctorAssessmentPayload::class.java) }.getOrNull()
                } else {
                    null
                },
                action = if (actionProtocolType.isNullOrBlank() || actionDurationSec == null) {
                    null
                } else {
                    DoctorMessageAction(
                        protocolType = actionProtocolType,
                        durationSec = actionDurationSec
                    )
                }
            )
        }
    }

    private suspend fun persistMessage(sessionId: String, message: DoctorChatMessage) {
        doctorRepository.saveMessage(
            DoctorMessageEntity(
                id = message.id,
                sessionId = sessionId,
                role = message.role.name,
                messageType = message.messageType.name,
                content = message.content,
                timestamp = message.timestamp,
                payloadJson = serializePayload(message),
                actionProtocolType = message.action?.protocolType,
                actionDurationSec = message.action?.durationSec
            )
        )
    }

    private suspend fun ensureActiveSession(): DoctorSessionEntity {
        if (activeSessionId.isNotBlank()) {
            doctorRepository.getSession(activeSessionId)?.let { return it }
        }
        return createNewSession().also { activeSessionId = it.id }
    }

    private suspend fun createNewSession(): DoctorSessionEntity {
        val session = DoctorSessionEntity(
            status = DoctorInquiryStage.INTAKE.name,
            domain = "SLEEP_RECOVERY",
            chiefComplaint = "",
            riskLevel = DoctorRiskLevel.LOW.name
        )
        doctorRepository.upsertSession(session)
        return session
    }

    private suspend fun refreshHistory() {
        val sessions = withContext(Dispatchers.IO) { doctorRepository.getRecentSessions(10) }
        updateState { state ->
            state.copy(
                historySummaries = sessions.map { session ->
                    DoctorHistorySummary(
                        sessionId = session.id,
                        title = session.chiefComplaint.ifBlank { app.getString(R.string.doctor_history_default_title) },
                        subtitle = formatHistorySubtitle(session),
                        updatedAt = session.updatedAt,
                        riskLevel = session.riskLevel
                    )
                }
            )
        }
    }

    private fun buildIntroMessage(): DoctorChatMessage {
        return DoctorChatMessage.followUp(
            DoctorFollowUpPayload(
                question = app.getString(R.string.doctor_intro_question),
                missingInfo = listOf(
                    app.getString(R.string.doctor_missing_chief_complaint),
                    app.getString(R.string.doctor_missing_duration),
                    app.getString(R.string.doctor_missing_trigger),
                    app.getString(R.string.doctor_missing_associated_symptoms)
                ),
                stage = DoctorInquiryStage.INTAKE
            )
        )
    }

    private fun formatHistorySubtitle(session: DoctorSessionEntity): String {
        val formatter = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)
        return app.getString(
            R.string.doctor_history_subtitle_format,
            formatter.format(session.updatedAt),
            mapRiskLevelText(parseRiskLevel(session.riskLevel))
        )
    }

    private fun parseRiskLevel(value: String): DoctorRiskLevel {
        return runCatching { DoctorRiskLevel.valueOf(value) }.getOrDefault(DoctorRiskLevel.LOW)
    }

    private fun parseStage(value: String): DoctorInquiryStage {
        return runCatching { DoctorInquiryStage.valueOf(value) }.getOrDefault(DoctorInquiryStage.INTAKE)
    }

    private fun serializePayload(message: DoctorChatMessage): String? {
        return when (message.messageType) {
            DoctorMessageType.TEXT -> null
            DoctorMessageType.FOLLOW_UP -> message.followUpPayload?.let(gson::toJson)
            DoctorMessageType.ASSESSMENT -> message.assessmentPayload?.let(gson::toJson)
        }
    }

    private fun suggestionSourceText(source: DoctorInferenceSource): String {
        return when (source) {
            DoctorInferenceSource.CLOUD_ENHANCED -> app.getString(R.string.doctor_source_engine)
            DoctorInferenceSource.LOCAL_RULE -> app.getString(R.string.doctor_source_local)
        }
    }

    private fun defaultModelStatusText(): String {
        return if (networkRepository.getCurrentSession() != null) {
            app.getString(R.string.doctor_model_status_cloud_ready)
        } else {
            app.getString(R.string.doctor_model_status_local_ready)
        }
    }

    private fun modelStatusText(source: DoctorInferenceSource): String {
        return when (source) {
            DoctorInferenceSource.CLOUD_ENHANCED -> app.getString(R.string.doctor_model_status_cloud_ready)
            DoctorInferenceSource.LOCAL_RULE -> app.getString(R.string.doctor_model_status_local_active)
        }
    }

    private fun startOfDay(timeMillis: Long): Long {
        val calendar = Calendar.getInstance().apply { timeInMillis = timeMillis }
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private fun mapRiskLevelText(level: DoctorRiskLevel): String {
        return when (level) {
            DoctorRiskLevel.HIGH -> app.getString(R.string.doctor_risk_high)
            DoctorRiskLevel.MEDIUM -> app.getString(R.string.doctor_risk_medium)
            DoctorRiskLevel.LOW -> app.getString(R.string.doctor_risk_low)
        }
    }

    private fun extractConfidenceNumber(text: String): Int {
        return "\\d+".toRegex().find(text)?.value?.toIntOrNull()?.coerceIn(0, 100) ?: 70
    }

    private fun canGenerateAssessment(stage: DoctorInquiryStage, messages: List<DoctorChatMessage>): Boolean {
        val userCount = messages.count { it.role == DoctorRole.USER }
        return stage != DoctorInquiryStage.COMPLETED &&
            stage != DoctorInquiryStage.ESCALATED &&
            userCount > 0
    }

    private fun updateState(transform: (DoctorUiState) -> DoctorUiState) {
        val current = _uiState.value ?: DoctorUiState()
        _uiState.value = transform(current)
    }

    private fun refreshRecommendationExplanation() {
        if (networkRepository.getCurrentSession() == null) {
            updateState { it.copy(recommendationExplanation = DoctorRecommendationExplanationUiState()) }
            return
        }
        viewModelScope.launch {
            val explanationState = withContext(Dispatchers.IO) {
                networkRepository
                    .getRecommendationExplanations(traceType = "DOCTOR_TURN", limit = 1)
                    .getOrNull()
                    ?.items
                    ?.firstOrNull()
                    ?.toDoctorExplanationUiState()
                    ?: DoctorRecommendationExplanationUiState()
            }
            updateState { it.copy(recommendationExplanation = explanationState) }
        }
    }
}

private fun List<DoctorChatMessage>.removePending(pendingId: String): List<DoctorChatMessage> {
    return filterNot { it.id == pendingId }
}

private fun String.toDoctorMessageType(): DoctorMessageType {
    return runCatching { DoctorMessageType.valueOf(this) }.getOrDefault(DoctorMessageType.TEXT)
}

private fun com.example.newstart.network.models.RecommendationExplanationItem.toDoctorExplanationUiState():
    DoctorRecommendationExplanationUiState {
    val metaParts = listOfNotNull(
        modelProfile?.takeIf { it.isNotBlank() },
        configSource?.takeIf { it.isNotBlank() },
        recommendationMode?.takeIf { it.isNotBlank() }
    )
    return DoctorRecommendationExplanationUiState(
        visible = summary.isNotBlank() || reasons.isNotEmpty() || nextStep.isNotBlank(),
        summary = summary,
        reasons = reasons.take(3),
        nextStep = nextStep,
        metaLabel = metaParts.joinToString(" 路 ")
    )
}

