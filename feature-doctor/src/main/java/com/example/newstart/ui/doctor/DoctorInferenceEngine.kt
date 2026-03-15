package com.example.newstart.ui.doctor

import com.google.gson.JsonParser

private const val MAX_FOLLOW_UPS = 4

data class DoctorInferenceInput(
    val latestUserMessage: String,
    val conversation: List<DoctorChatMessage>,
    val stage: DoctorInquiryStage,
    val followUpCount: Int,
    val snapshot: DoctorMetricSnapshot,
    val riskSummary: DoctorRiskSummary,
    val contextBlock: String,
    val ragContext: String
)

enum class DoctorInferenceSource {
    CLOUD_ENHANCED,
    LOCAL_RULE
}

data class DoctorTurnDecision(
    val nextStage: DoctorInquiryStage,
    val source: DoctorInferenceSource,
    val followUp: DoctorFollowUpPayload? = null,
    val assessment: DoctorAssessmentPayload? = null
)

object DoctorInferenceEngine {

    fun generateTurn(input: DoctorInferenceInput): DoctorTurnDecision {
        val conversationText = buildConversationBlock(
            if (input.stage == DoctorInquiryStage.ASSESSING) {
                input.conversation
            } else {
                input.conversation + DoctorChatMessage.user(input.latestUserMessage)
            }
        )
        val redFlags = DoctorDecisionEngine.detectRedFlags(conversationText)
        if (redFlags.isNotEmpty()) {
            return DoctorTurnDecision(
                nextStage = DoctorInquiryStage.ESCALATED,
                source = DoctorInferenceSource.LOCAL_RULE,
                assessment = DoctorDecisionEngine.buildFallbackAssessment(
                    historyText = conversationText,
                    snapshot = input.snapshot,
                    riskSummary = input.riskSummary,
                    redFlags = redFlags
                )
            )
        }

        if (input.stage == DoctorInquiryStage.ASSESSING) {
            return DoctorTurnDecision(
                nextStage = DoctorInquiryStage.COMPLETED,
                source = DoctorInferenceSource.LOCAL_RULE,
                assessment = DoctorDecisionEngine.buildFallbackAssessment(
                    historyText = conversationText,
                    snapshot = input.snapshot,
                    riskSummary = input.riskSummary
                )
            )
        }

        val localFollowUp = DoctorDecisionEngine.buildFollowUpPayload(
            historyText = conversationText,
            followUpCount = input.followUpCount
        )
        val shouldContinue = input.followUpCount < 2 ||
            (input.followUpCount < MAX_FOLLOW_UPS && localFollowUp.missingInfo.isNotEmpty())

        return if (shouldContinue) {
            DoctorTurnDecision(
                nextStage = DoctorInquiryStage.CLARIFYING,
                source = DoctorInferenceSource.LOCAL_RULE,
                followUp = localFollowUp
            )
        } else {
            DoctorTurnDecision(
                nextStage = DoctorInquiryStage.COMPLETED,
                source = DoctorInferenceSource.LOCAL_RULE,
                assessment = DoctorDecisionEngine.buildFallbackAssessment(
                    historyText = conversationText,
                    snapshot = input.snapshot,
                    riskSummary = input.riskSummary
                )
            )
        }
    }

    internal fun buildConversationBlock(messages: List<DoctorChatMessage>): String {
        return messages.joinToString(separator = "\n") { message ->
            val role = when (message.role) {
                DoctorRole.USER -> "患者"
                DoctorRole.ASSISTANT -> "助手"
            }
            "$role：${message.content}"
        }
    }

    internal fun parseStructuredTurn(raw: String): ParsedDoctorTurn? {
        val json = extractFirstJsonObject(raw) ?: raw
        return runCatching {
            val root = JsonParser.parseString(json).asJsonObject
            val followUpQuestion = root.get("followUpQuestion")?.asString?.trim().orEmpty()
            val riskLevel = root.get("riskLevel")?.asString?.trim().orEmpty().ifBlank { "MEDIUM" }
            val recommendedDepartment = root.get("recommendedDepartment")?.asString?.trim().orEmpty()
            val doctorSummary = root.get("doctorSummary")?.asString?.trim().orEmpty()
            val disclaimer = root.get("disclaimer")?.asString?.trim().orEmpty().ifBlank {
                "本结果仅用于健康初筛与问诊整理，不能替代医生面诊、检查和正式诊断。"
            }
            val stage = root.get("stage")?.asString?.trim().orEmpty()
            val chiefComplaint = root.get("chiefComplaint")?.asString?.trim().orEmpty()
            val symptomFacts = root.getAsJsonArray("symptomFacts")
                ?.mapNotNull { it.asString?.trim()?.takeIf(String::isNotBlank) }
                .orEmpty()
            val missingInfo = root.getAsJsonArray("missingInfo")
                ?.mapNotNull { it.asString?.trim()?.takeIf(String::isNotBlank) }
                .orEmpty()
            val redFlags = root.getAsJsonArray("redFlags")
                ?.mapNotNull { it.asString?.trim()?.takeIf(String::isNotBlank) }
                .orEmpty()
            val nextStepAdvice = root.getAsJsonArray("nextStepAdvice")
                ?.mapNotNull { it.asString?.trim()?.takeIf(String::isNotBlank) }
                .orEmpty()
            val suspectedIssues = root.getAsJsonArray("suspectedIssues")
                ?.mapNotNull { item ->
                    runCatching {
                        val obj = item.asJsonObject
                        val name = obj.get("name")?.asString?.trim().orEmpty()
                        val rationale = obj.get("rationale")?.asString?.trim().orEmpty()
                        val confidence = obj.get("confidence")?.asInt ?: 70
                        if (name.isBlank() || rationale.isBlank()) {
                            null
                        } else {
                            DoctorSuspectedIssue(
                                name = name,
                                rationale = rationale,
                                confidence = confidence.coerceIn(0, 100)
                            )
                        }
                    }.getOrNull()
                }
                .orEmpty()

            val hasFollowUp = followUpQuestion.isNotBlank()
            val hasAssessment = chiefComplaint.isNotBlank() &&
                doctorSummary.isNotBlank() &&
                recommendedDepartment.isNotBlank() &&
                suspectedIssues.isNotEmpty()
            if (!hasFollowUp && !hasAssessment && redFlags.isEmpty()) {
                null
            } else {
                ParsedDoctorTurn(
                    chiefComplaint = chiefComplaint,
                    symptomFacts = symptomFacts,
                    missingInfo = missingInfo,
                    suspectedIssues = suspectedIssues,
                    riskLevel = riskLevel,
                    redFlags = redFlags,
                    recommendedDepartment = recommendedDepartment,
                    nextStepAdvice = nextStepAdvice,
                    doctorSummary = doctorSummary,
                    disclaimer = disclaimer,
                    followUpQuestion = followUpQuestion,
                    stage = stage
                )
            }
        }.getOrNull()
    }

    private fun extractFirstJsonObject(raw: String): String? {
        val start = raw.indexOf('{')
        if (start < 0) return null

        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until raw.length) {
            val ch = raw[i]
            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (ch == '\\') {
                    escaped = true
                } else if (ch == '"') {
                    inString = false
                }
                continue
            }

            when (ch) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        return raw.substring(start, i + 1)
                    }
                }
            }
        }
        return null
    }
}

internal data class ParsedDoctorTurn(
    val chiefComplaint: String,
    val symptomFacts: List<String>,
    val missingInfo: List<String>,
    val suspectedIssues: List<DoctorSuspectedIssue>,
    val riskLevel: String,
    val redFlags: List<String>,
    val recommendedDepartment: String,
    val nextStepAdvice: List<String>,
    val doctorSummary: String,
    val disclaimer: String,
    val followUpQuestion: String,
    val stage: String
) {
    fun toPayload(defaultRiskLevel: String): DoctorAssessmentPayload {
        return DoctorAssessmentPayload(
            chiefComplaint = chiefComplaint.ifBlank { "未提取到明确主诉" },
            symptomFacts = symptomFacts,
            missingInfo = missingInfo,
            suspectedIssues = suspectedIssues,
            riskLevel = riskLevel.ifBlank { defaultRiskLevel },
            redFlags = redFlags,
            recommendedDepartment = recommendedDepartment.ifBlank { "全科" },
            nextStepAdvice = nextStepAdvice.ifEmpty { listOf("建议结合线下面诊进一步确认。") },
            doctorSummary = doctorSummary,
            disclaimer = disclaimer
        )
    }
}
