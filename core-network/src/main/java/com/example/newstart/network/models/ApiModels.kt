package com.example.newstart.network.models

import com.google.gson.annotations.SerializedName

/**
 * 网络请求/响应数据模型
 */

// ========== 基础响应 ==========
data class BaseResponse(
    @SerializedName("code")
    val code: Int,
    @SerializedName("message")
    val message: String,
    @SerializedName("success")
    val success: Boolean
)

// ========== 用户认证 ==========
data class LoginRequest(
    @SerializedName("email")
    val email: String,
    @SerializedName("password")
    val password: String
)

data class RegisterRequest(
    @SerializedName("email")
    val email: String,
    @SerializedName("password")
    val password: String,
    @SerializedName("username")
    val username: String
)

data class AuthResponse(
    @SerializedName("code")
    val code: Int,
    @SerializedName("message")
    val message: String,
    @SerializedName("data")
    val data: AuthData?
)

data class AuthData(
    @SerializedName("authState")
    val authState: String,
    @SerializedName("email")
    val email: String,
    @SerializedName("token")
    val token: String = "",
    @SerializedName("refreshToken")
    val refreshToken: String = "",
    @SerializedName("userId")
    val userId: String = "",
    @SerializedName("username")
    val username: String = "",
    @SerializedName("demoRole")
    val demoRole: String? = null,
    @SerializedName("demoScenario")
    val demoScenario: String? = null,
    @SerializedName("demoSeedVersion")
    val demoSeedVersion: String? = null,
    @SerializedName("displayName")
    val displayName: String? = null,
    @SerializedName("canResendConfirmation")
    val canResendConfirmation: Boolean = false
)

data class EmailActionRequest(
    @SerializedName("email")
    val email: String
)

data class RefreshTokenRequest(
    @SerializedName("refreshToken")
    val refreshToken: String
)

data class ActionResponse(
    @SerializedName("code")
    val code: Int,
    @SerializedName("message")
    val message: String,
    @SerializedName("traceId")
    val traceId: String? = null
)

data class AvatarNarrationRequest(
    @SerializedName("pageKey")
    val pageKey: String,
    @SerializedName("pageTitle")
    val pageTitle: String,
    @SerializedName("pageSubtitle")
    val pageSubtitle: String = "",
    @SerializedName("visibleHighlights")
    val visibleHighlights: List<String> = emptyList(),
    @SerializedName("userStateSummary")
    val userStateSummary: String = "",
    @SerializedName("riskSummary")
    val riskSummary: String = "",
    @SerializedName("actionHint")
    val actionHint: String = "",
    @SerializedName("trigger")
    val trigger: String = "enter"
)

data class AvatarNarrationResponse(
    @SerializedName("code")
    val code: Int,
    @SerializedName("message")
    val message: String,
    @SerializedName("data")
    val data: AvatarNarrationPayload?,
    @SerializedName("traceId")
    val traceId: String? = null
)

data class AvatarNarrationPayload(
    @SerializedName("text")
    val text: String,
    @SerializedName("semanticAction")
    val semanticAction: String = "",
    @SerializedName("source")
    val source: String = "",
    @SerializedName("modelLabel")
    val modelLabel: String = ""
)

// ========== 睡眠数据 ==========
data class SleepDataRequest(
    @SerializedName("userId")
    val userId: String,
    @SerializedName("sleepRecordId")
    val sleepRecordId: String? = null,
    @SerializedName("date")
    val date: Long,
    @SerializedName("bedTime")
    val bedTime: Long,
    @SerializedName("wakeTime")
    val wakeTime: Long,
    @SerializedName("totalSleepMinutes")
    val totalSleepMinutes: Int,
    @SerializedName("deepSleepMinutes")
    val deepSleepMinutes: Int,
    @SerializedName("lightSleepMinutes")
    val lightSleepMinutes: Int,
    @SerializedName("remSleepMinutes")
    val remSleepMinutes: Int
)

data class SleepAnalysisRequest(
    @SerializedName("userId")
    val userId: String,
    @SerializedName("sleepRecordId")
    val sleepRecordId: String,
    @SerializedName("rawData")
    val rawData: Map<String, Any>  // 传感器原始数据
)

data class SleepAnalysisResponse(
    @SerializedName("code")
    val code: Int,
    @SerializedName("message")
    val message: String,
    @SerializedName("data")
    val data: SleepAnalysisData?
)

data class SleepAnalysisData(
    @SerializedName("sleepStages")
    val sleepStages: List<String>,  // ["DEEP", "REM", "LIGHT", ...]
    @SerializedName("recoveryScore")
    val recoveryScore: Int,
    @SerializedName("sleepQuality")
    val sleepQuality: String,
    @SerializedName("insights")
    val insights: List<String>      // 分析洞察
)

// ========== 睡眠历史 ==========
data class SleepHistoryResponse(
    @SerializedName("code")
    val code: Int,
    @SerializedName("message")
    val message: String,
    @SerializedName("data")
    val data: List<SleepHistoryItem>?
)

data class SleepHistoryItem(
    @SerializedName("id")
    val id: String,
    @SerializedName("date")
    val date: Long,
    @SerializedName("duration")
    val duration: Int,
    @SerializedName("quality")
    val quality: String
)

// ========== 恢复指数 ==========
data class RecoveryTrendResponse(
    @SerializedName("code")
    val code: Int,
    @SerializedName("message")
    val message: String,
    @SerializedName("data")
    val data: List<RecoveryTrendItem>?
)

data class RecoveryTrendItem(
    @SerializedName("date")
    val date: Long,
    @SerializedName("score")
    val score: Int
)

// ========== 数据同步 ==========
data class SyncRequest(
    @SerializedName("userId")
    val userId: String,
    @SerializedName("lastSyncTime")
    val lastSyncTime: Long,
    @SerializedName("data")
    val data: Map<String, Any>
)

data class SyncResponse(
    @SerializedName("code")
    val code: Int,
    @SerializedName("message")
    val message: String,
    @SerializedName("data")
    val data: SyncData?
)

data class SyncData(
    @SerializedName("syncTime")
    val syncTime: Long,
    @SerializedName("updatedRecords")
    val updatedRecords: Int
)

// ========== 个性化建议 ==========
data class AdviceRequest(
    @SerializedName("userId")
    val userId: String,
    @SerializedName("recoveryScore")
    val recoveryScore: Int,
    @SerializedName("sleepData")
    val sleepData: Map<String, Any>
)

data class AdviceResponse(
    @SerializedName("code")
    val code: Int,
    @SerializedName("message")
    val message: String,
    @SerializedName("data")
    val data: List<AdviceItem>?
)

data class AdviceItem(
    @SerializedName("type")
    val type: String,
    @SerializedName("title")
    val title: String,
    @SerializedName("description")
    val description: String
)

// ========== 原始数据上传 ==========
data class RawDataRequest(
    @SerializedName("userId")
    val userId: String,
    @SerializedName("deviceId")
    val deviceId: String,
    @SerializedName("timestamp")
    val timestamp: Long,
    @SerializedName("sensorData")
    val sensorData: Map<String, Any>
)

// ========== 用户信息 ==========
data class UserProfileResponse(
    @SerializedName("code")
    val code: Int,
    @SerializedName("message")
    val message: String,
    @SerializedName("data")
    val data: UserProfile?
)

data class UserProfile(
    @SerializedName("userId")
    val userId: String,
    @SerializedName("username")
    val username: String,
    @SerializedName("email")
    val email: String,
    @SerializedName("age")
    val age: Int?,
    @SerializedName("gender")
    val gender: String?,
    @SerializedName("demoRole")
    val demoRole: String? = null,
    @SerializedName("demoScenario")
    val demoScenario: String? = null,
    @SerializedName("demoSeedVersion")
    val demoSeedVersion: String? = null,
    @SerializedName("displayName")
    val displayName: String? = null
)

data class UserProfileRequest(
    @SerializedName("username")
    val username: String?,
    @SerializedName("age")
    val age: Int?,
    @SerializedName("gender")
    val gender: String?
)

// ========== Medical report + intervention ==========
data class MedicalMetricUpsertRequest(
    @SerializedName("reportId")
    val reportId: String? = null,
    @SerializedName("reportDate")
    val reportDate: Long,
    @SerializedName("reportType")
    val reportType: String,
    @SerializedName("riskLevel")
    val riskLevel: String,
    @SerializedName("metrics")
    val metrics: List<MedicalMetricUpsertItem>
)

data class MedicalMetricUpsertItem(
    @SerializedName("metricCode")
    val metricCode: String,
    @SerializedName("metricName")
    val metricName: String,
    @SerializedName("metricValue")
    val metricValue: Float,
    @SerializedName("unit")
    val unit: String,
    @SerializedName("refLow")
    val refLow: Float?,
    @SerializedName("refHigh")
    val refHigh: Float?,
    @SerializedName("isAbnormal")
    val isAbnormal: Boolean,
    @SerializedName("confidence")
    val confidence: Float
)

data class LatestMedicalReportResponse(
    @SerializedName("code")
    val code: Int,
    @SerializedName("message")
    val message: String,
    @SerializedName("data")
    val data: LatestMedicalReportData?
)

data class LatestMedicalReportData(
    @SerializedName("reportId")
    val reportId: String,
    @SerializedName("reportDate")
    val reportDate: Long,
    @SerializedName("riskLevel")
    val riskLevel: String,
    @SerializedName("abnormalCount")
    val abnormalCount: Int
)

data class InterventionTaskUpsertRequest(
    @SerializedName("taskId")
    val taskId: String,
    @SerializedName("date")
    val date: Long,
    @SerializedName("sourceType")
    val sourceType: String,
    @SerializedName("triggerReason")
    val triggerReason: String,
    @SerializedName("bodyZone")
    val bodyZone: String,
    @SerializedName("protocolType")
    val protocolType: String,
    @SerializedName("durationSec")
    val durationSec: Int,
    @SerializedName("plannedAt")
    val plannedAt: Long,
    @SerializedName("status")
    val status: String
)

data class InterventionExecutionUpsertRequest(
    @SerializedName("executionId")
    val executionId: String,
    @SerializedName("taskId")
    val taskId: String,
    @SerializedName("startedAt")
    val startedAt: Long,
    @SerializedName("endedAt")
    val endedAt: Long,
    @SerializedName("elapsedSec")
    val elapsedSec: Int,
    @SerializedName("beforeStress")
    val beforeStress: Float,
    @SerializedName("afterStress")
    val afterStress: Float,
    @SerializedName("beforeHr")
    val beforeHr: Int,
    @SerializedName("afterHr")
    val afterHr: Int,
    @SerializedName("effectScore")
    val effectScore: Float,
    @SerializedName("completionType")
    val completionType: String,
    @SerializedName("metadataJson")
    val metadataJson: String? = null
)

data class InterventionEffectTrendResponse(
    @SerializedName("code")
    val code: Int,
    @SerializedName("message")
    val message: String,
    @SerializedName("data")
    val data: List<InterventionEffectTrendItem>?
)

data class InterventionEffectTrendItem(
    @SerializedName("date")
    val date: Long,
    @SerializedName("avgEffectScore")
    val avgEffectScore: Float,
    @SerializedName("avgStressDrop")
    val avgStressDrop: Float,
    @SerializedName("executionCount")
    val executionCount: Int
)

data class AssessmentBaselineSummaryUpsertRequest(
    @SerializedName("completedScaleCodes")
    val completedScaleCodes: List<String>,
    @SerializedName("completedCount")
    val completedCount: Int,
    @SerializedName("completedAt")
    val completedAt: Long,
    @SerializedName("freshnessUntil")
    val freshnessUntil: Long,
    @SerializedName("source")
    val source: String = "ANDROID"
)

data class DoctorInquirySummaryUpsertRequest(
    @SerializedName("sessionId")
    val sessionId: String,
    @SerializedName("assessedAt")
    val assessedAt: Long,
    @SerializedName("riskLevel")
    val riskLevel: String,
    @SerializedName("chiefComplaint")
    val chiefComplaint: String,
    @SerializedName("redFlags")
    val redFlags: List<String>,
    @SerializedName("recommendedDepartment")
    val recommendedDepartment: String,
    @SerializedName("doctorSummary")
    val doctorSummary: String
)

data class MedicationRecordUpsertRequest(
    @SerializedName("recordId")
    val recordId: String,
    @SerializedName("capturedAt")
    val capturedAt: Long,
    @SerializedName("imageUri")
    val imageUri: String,
    @SerializedName("recognizedName")
    val recognizedName: String,
    @SerializedName("dosageForm")
    val dosageForm: String,
    @SerializedName("specification")
    val specification: String,
    @SerializedName("activeIngredients")
    val activeIngredients: List<String>,
    @SerializedName("matchedSymptoms")
    val matchedSymptoms: List<String>,
    @SerializedName("usageSummary")
    val usageSummary: String,
    @SerializedName("riskLevel")
    val riskLevel: String,
    @SerializedName("riskFlags")
    val riskFlags: List<String>,
    @SerializedName("evidenceNotes")
    val evidenceNotes: List<String>,
    @SerializedName("advice")
    val advice: String,
    @SerializedName("confidence")
    val confidence: Float,
    @SerializedName("requiresManualReview")
    val requiresManualReview: Boolean,
    @SerializedName("analysisMode")
    val analysisMode: String,
    @SerializedName("providerId")
    val providerId: String? = null,
    @SerializedName("modelId")
    val modelId: String? = null,
    @SerializedName("traceId")
    val traceId: String? = null
)

data class FoodRecordUpsertRequest(
    @SerializedName("recordId")
    val recordId: String,
    @SerializedName("capturedAt")
    val capturedAt: Long,
    @SerializedName("imageUri")
    val imageUri: String,
    @SerializedName("mealType")
    val mealType: String,
    @SerializedName("foodItems")
    val foodItems: List<String>,
    @SerializedName("estimatedCalories")
    val estimatedCalories: Int,
    @SerializedName("carbohydrateGrams")
    val carbohydrateGrams: Float,
    @SerializedName("proteinGrams")
    val proteinGrams: Float,
    @SerializedName("fatGrams")
    val fatGrams: Float,
    @SerializedName("nutritionRiskLevel")
    val nutritionRiskLevel: String,
    @SerializedName("nutritionFlags")
    val nutritionFlags: List<String>,
    @SerializedName("dailyContribution")
    val dailyContribution: String,
    @SerializedName("advice")
    val advice: String,
    @SerializedName("confidence")
    val confidence: Float,
    @SerializedName("requiresManualReview")
    val requiresManualReview: Boolean,
    @SerializedName("analysisMode")
    val analysisMode: String,
    @SerializedName("providerId")
    val providerId: String? = null,
    @SerializedName("modelId")
    val modelId: String? = null,
    @SerializedName("traceId")
    val traceId: String? = null
)

// ========== Period summary ==========
data class PeriodSummaryResponse(
    @SerializedName("code")
    val code: Int,
    @SerializedName("message")
    val message: String,
    @SerializedName("data")
    val data: PeriodSummaryData?
)

data class PeriodSummaryData(
    @SerializedName("period")
    val period: String,
    @SerializedName("periodLabel")
    val periodLabel: String,
    @SerializedName("title")
    val title: String,
    @SerializedName("sampleSufficient")
    val sampleSufficient: Boolean,
    @SerializedName("headline")
    val headline: String,
    @SerializedName("riskLevel")
    val riskLevel: String,
    @SerializedName("riskSummary")
    val riskSummary: String,
    @SerializedName("highlights")
    val highlights: List<String>,
    @SerializedName("metricChanges")
    val metricChanges: List<PeriodSummaryMetricItem>,
    @SerializedName("interventionSummary")
    val interventionSummary: String,
    @SerializedName("nextFocusTitle")
    val nextFocusTitle: String,
    @SerializedName("nextFocusDetail")
    val nextFocusDetail: String,
    @SerializedName("personalizationLevel")
    val personalizationLevel: String = "PREVIEW",
    @SerializedName("missingInputs")
    val missingInputs: List<String> = emptyList(),
    @SerializedName("reportConfidence")
    val reportConfidence: String = "LOW",
    @SerializedName("actions")
    val actions: List<PeriodSummaryActionItem>
)

data class PeriodSummaryMetricItem(
    @SerializedName("label")
    val label: String,
    @SerializedName("value")
    val value: String,
    @SerializedName("comparison")
    val comparison: String,
    @SerializedName("direction")
    val direction: String
)

data class PeriodSummaryActionItem(
    @SerializedName("title")
    val title: String,
    @SerializedName("subtitle")
    val subtitle: String,
    @SerializedName("protocolCode")
    val protocolCode: String,
    @SerializedName("durationSec")
    val durationSec: Int,
    @SerializedName("assetRef")
    val assetRef: String,
    @SerializedName("itemType")
    val itemType: String
)

// ========== AI runtime ==========
data class DoctorTurnRequest(
    @SerializedName("conversationBlock")
    val conversationBlock: String,
    @SerializedName("contextBlock")
    val contextBlock: String,
    @SerializedName("ragContext")
    val ragContext: String,
    @SerializedName("stage")
    val stage: String,
    @SerializedName("followUpCount")
    val followUpCount: Int
)

data class DoctorTurnResponse(
    @SerializedName("code")
    val code: Int,
    @SerializedName("message")
    val message: String,
    @SerializedName("data")
    val data: DoctorTurnData?
)

data class DoctorTurnData(
    @SerializedName("chiefComplaint")
    val chiefComplaint: String,
    @SerializedName("symptomFacts")
    val symptomFacts: List<String>,
    @SerializedName("missingInfo")
    val missingInfo: List<String>,
    @SerializedName("suspectedIssues")
    val suspectedIssues: List<DoctorSuspectedIssueItem>,
    @SerializedName("riskLevel")
    val riskLevel: String,
    @SerializedName("redFlags")
    val redFlags: List<String>,
    @SerializedName("recommendedDepartment")
    val recommendedDepartment: String,
    @SerializedName("nextStepAdvice")
    val nextStepAdvice: List<String>,
    @SerializedName("doctorSummary")
    val doctorSummary: String,
    @SerializedName("disclaimer")
    val disclaimer: String,
    @SerializedName("followUpQuestion")
    val followUpQuestion: String,
    @SerializedName("stage")
    val stage: String,
    @SerializedName("explanation")
    val explanation: RecommendationExplanationData? = null,
    @SerializedName("metadata")
    val metadata: AiMetadata?
)

data class DoctorSuspectedIssueItem(
    @SerializedName("name")
    val name: String,
    @SerializedName("rationale")
    val rationale: String,
    @SerializedName("confidence")
    val confidence: Int
)

data class ReportUnderstandingRequest(
    @SerializedName("reportType")
    val reportType: String,
    @SerializedName("ocrText")
    val ocrText: String,
    @SerializedName("ocrMarkdown")
    val ocrMarkdown: String? = null
)

data class ReportUnderstandingResponse(
    @SerializedName("code")
    val code: Int,
    @SerializedName("message")
    val message: String,
    @SerializedName("data")
    val data: ReportUnderstandingData?
)

data class ReportUnderstandingData(
    @SerializedName("reportType")
    val reportType: String,
    @SerializedName("riskLevel")
    val riskLevel: String,
    @SerializedName("abnormalCount")
    val abnormalCount: Int,
    @SerializedName("readableReport")
    val readableReport: String? = null,
    @SerializedName("summary")
    val summary: String,
    @SerializedName("metrics")
    val metrics: List<MedicalMetricUpsertItem>,
    @SerializedName("metadata")
    val metadata: AiMetadata?
)

data class MedicationAnalyzeResponse(
    @SerializedName("code")
    val code: Int,
    @SerializedName("message")
    val message: String,
    @SerializedName("data")
    val data: MedicationAnalyzeData?
)

data class MedicationAnalyzeData(
    @SerializedName("recognizedName")
    val recognizedName: String,
    @SerializedName("dosageForm")
    val dosageForm: String,
    @SerializedName("specification")
    val specification: String,
    @SerializedName("activeIngredients")
    val activeIngredients: List<String>,
    @SerializedName("matchedSymptoms")
    val matchedSymptoms: List<String>,
    @SerializedName("usageSummary")
    val usageSummary: String,
    @SerializedName("riskLevel")
    val riskLevel: String,
    @SerializedName("riskFlags")
    val riskFlags: List<String>,
    @SerializedName("evidenceNotes")
    val evidenceNotes: List<String>,
    @SerializedName("advice")
    val advice: String,
    @SerializedName("confidence")
    val confidence: Float,
    @SerializedName("requiresManualReview")
    val requiresManualReview: Boolean,
    @SerializedName("analysisMode")
    val analysisMode: String,
    @SerializedName("metadata")
    val metadata: AiMetadata?
)

data class FoodAnalyzeResponse(
    @SerializedName("code")
    val code: Int,
    @SerializedName("message")
    val message: String,
    @SerializedName("data")
    val data: FoodAnalyzeData?
)

data class FoodAnalyzeData(
    @SerializedName("mealType")
    val mealType: String,
    @SerializedName("foodItems")
    val foodItems: List<String>,
    @SerializedName("estimatedCalories")
    val estimatedCalories: Int,
    @SerializedName("carbohydrateGrams")
    val carbohydrateGrams: Float,
    @SerializedName("proteinGrams")
    val proteinGrams: Float,
    @SerializedName("fatGrams")
    val fatGrams: Float,
    @SerializedName("nutritionRiskLevel")
    val nutritionRiskLevel: String,
    @SerializedName("nutritionFlags")
    val nutritionFlags: List<String>,
    @SerializedName("dailyContribution")
    val dailyContribution: String,
    @SerializedName("advice")
    val advice: String,
    @SerializedName("confidence")
    val confidence: Float,
    @SerializedName("requiresManualReview")
    val requiresManualReview: Boolean,
    @SerializedName("analysisMode")
    val analysisMode: String,
    @SerializedName("metadata")
    val metadata: AiMetadata?
)

data class SpeechTranscriptionRequest(
    @SerializedName("audioUrl")
    val audioUrl: String,
    @SerializedName("mimeType")
    val mimeType: String = "audio/mpeg",
    @SerializedName("hint")
    val hint: String = ""
)

data class SpeechTranscriptionResponse(
    @SerializedName("code")
    val code: Int,
    @SerializedName("message")
    val message: String,
    @SerializedName("data")
    val data: SpeechTranscriptionData?
)

data class SpeechTranscriptionData(
    @SerializedName("transcript")
    val transcript: String,
    @SerializedName("hint")
    val hint: String,
    @SerializedName("audioUrl")
    val audioUrl: String,
    @SerializedName("providerId")
    val providerId: String,
    @SerializedName("modelId")
    val modelId: String,
    @SerializedName("traceId")
    val traceId: String,
    @SerializedName("fallbackUsed")
    val fallbackUsed: Boolean,
    @SerializedName("status")
    val status: String
)

data class SpeechSynthesisRequest(
    @SerializedName("text")
    val text: String,
    @SerializedName("voice")
    val voice: String = "alloy",
    @SerializedName("profile")
    val profile: String = "calm_assistant"
)

data class SpeechSynthesisResponse(
    @SerializedName("code")
    val code: Int,
    @SerializedName("message")
    val message: String,
    @SerializedName("data")
    val data: SpeechSynthesisData?
)

data class SpeechSynthesisData(
    @SerializedName("audioUrl")
    val audioUrl: String,
    @SerializedName("text")
    val text: String,
    @SerializedName("voice")
    val voice: String,
    @SerializedName("providerId")
    val providerId: String,
    @SerializedName("modelId")
    val modelId: String,
    @SerializedName("traceId")
    val traceId: String,
    @SerializedName("fallbackUsed")
    val fallbackUsed: Boolean,
    @SerializedName("status")
    val status: String
)

data class ImageGenerationRequest(
    @SerializedName("prompt")
    val prompt: String,
    @SerializedName("size")
    val size: String = "1024x1024",
    @SerializedName("profile")
    val profile: String = "medical_wellness_product"
)

data class ImageGenerationResponse(
    @SerializedName("code")
    val code: Int,
    @SerializedName("message")
    val message: String,
    @SerializedName("data")
    val data: ImageGenerationData?
)

data class ImageGenerationData(
    @SerializedName("imageUrl")
    val imageUrl: String,
    @SerializedName("prompt")
    val prompt: String,
    @SerializedName("size")
    val size: String,
    @SerializedName("providerId")
    val providerId: String,
    @SerializedName("modelId")
    val modelId: String,
    @SerializedName("traceId")
    val traceId: String,
    @SerializedName("fallbackUsed")
    val fallbackUsed: Boolean,
    @SerializedName("status")
    val status: String
)

data class VideoGenerationRequest(
    @SerializedName("prompt")
    val prompt: String,
    @SerializedName("durationSec")
    val durationSec: Int = 10,
    @SerializedName("profile")
    val profile: String = "sleep_guidance"
)

data class VideoGenerationResponse(
    @SerializedName("code")
    val code: Int,
    @SerializedName("message")
    val message: String,
    @SerializedName("data")
    val data: VideoGenerationData?
)

data class VideoGenerationData(
    @SerializedName("jobId")
    val jobId: String,
    @SerializedName("status")
    val status: String,
    @SerializedName("prompt")
    val prompt: String,
    @SerializedName("durationSec")
    val durationSec: Int,
    @SerializedName("providerId")
    val providerId: String,
    @SerializedName("modelId")
    val modelId: String,
    @SerializedName("traceId")
    val traceId: String,
    @SerializedName("fallbackUsed")
    val fallbackUsed: Boolean
)

data class VideoJobResponse(
    @SerializedName("code")
    val code: Int,
    @SerializedName("message")
    val message: String,
    @SerializedName("data")
    val data: VideoJobData?
)

data class VideoJobData(
    @SerializedName("jobId")
    val jobId: String,
    @SerializedName("status")
    val status: String,
    @SerializedName("videoUrl")
    val videoUrl: String
)

data class AiMetadata(
    @SerializedName("providerId")
    val providerId: String? = null,
    @SerializedName("modelId")
    val modelId: String? = null,
    @SerializedName("traceId")
    val traceId: String? = null,
    @SerializedName("fallbackUsed")
    val fallbackUsed: Boolean = false,
    @SerializedName("modelProfile")
    val modelProfile: String? = null,
    @SerializedName("profileCode")
    val profileCode: String? = null,
    @SerializedName("configSource")
    val configSource: String? = null,
    @SerializedName("recommendationMode")
    val recommendationMode: String? = null,
    @SerializedName("modelVersion")
    val modelVersion: String? = null
)

data class RecommendationExplanationData(
    @SerializedName("summary")
    val summary: String = "",
    @SerializedName("reasons")
    val reasons: List<String> = emptyList(),
    @SerializedName("nextStep")
    val nextStep: String = ""
)

data class RecommendationExplanationsResponse(
    @SerializedName("code")
    val code: Int,
    @SerializedName("message")
    val message: String,
    @SerializedName("data")
    val data: RecommendationExplanationsData?
)

data class RecommendationExplanationsData(
    @SerializedName("items")
    val items: List<RecommendationExplanationItem> = emptyList(),
    @SerializedName("total")
    val total: Int = 0
)

data class RecommendationExplanationItem(
    @SerializedName("id")
    val id: String,
    @SerializedName("traceId")
    val traceId: String? = null,
    @SerializedName("traceType")
    val traceType: String,
    @SerializedName("traceKey")
    val traceKey: String? = null,
    @SerializedName("createdAt")
    val createdAt: String,
    @SerializedName("providerId")
    val providerId: String? = null,
    @SerializedName("riskLevel")
    val riskLevel: String? = null,
    @SerializedName("personalizationLevel")
    val personalizationLevel: String? = null,
    @SerializedName("isFallback")
    val isFallback: Boolean = false,
    @SerializedName("summary")
    val summary: String = "",
    @SerializedName("reasons")
    val reasons: List<String> = emptyList(),
    @SerializedName("nextStep")
    val nextStep: String = "",
    @SerializedName("recommendationMode")
    val recommendationMode: String? = null,
    @SerializedName("modelVersion")
    val modelVersion: String? = null,
    @SerializedName("modelProfile")
    val modelProfile: String? = null,
    @SerializedName("configSource")
    val configSource: String? = null,
    @SerializedName("safetyGate")
    val safetyGate: String? = null,
    @SerializedName("evidenceCoverage")
    val evidenceCoverage: Float? = null,
    @SerializedName("evidenceHighlights")
    val evidenceHighlights: List<String> = emptyList()
)

data class RecommendationEffectsResponse(
    @SerializedName("code")
    val code: Int,
    @SerializedName("message")
    val message: String,
    @SerializedName("data")
    val data: RecommendationEffectSummaryData?
)

data class RecommendationEffectSummaryData(
    @SerializedName("source")
    val source: String = "unavailable",
    @SerializedName("days")
    val days: Int = 30,
    @SerializedName("totalExecutions")
    val totalExecutions: Int = 0,
    @SerializedName("attributedExecutions")
    val attributedExecutions: Int = 0,
    @SerializedName("attributionRate")
    val attributionRate: Float = 0f,
    @SerializedName("avgEffectScore")
    val avgEffectScore: Float = 0f,
    @SerializedName("avgStressDrop")
    val avgStressDrop: Float = 0f,
    @SerializedName("avgElapsedSec")
    val avgElapsedSec: Float = 0f,
    @SerializedName("byRecommendationMode")
    val byRecommendationMode: List<RecommendationModeEffectItem> = emptyList(),
    @SerializedName("byModelProfile")
    val byModelProfile: List<ModelProfileEffectItem> = emptyList(),
    @SerializedName("dailyTrend")
    val dailyTrend: List<RecommendationDailyTrendItem> = emptyList()
)

data class RecommendationModeEffectItem(
    @SerializedName("recommendationMode")
    val recommendationMode: String,
    @SerializedName("executionCount")
    val executionCount: Int,
    @SerializedName("avgEffectScore")
    val avgEffectScore: Float = 0f,
    @SerializedName("avgStressDrop")
    val avgStressDrop: Float = 0f
)

data class ModelProfileEffectItem(
    @SerializedName("profileCode")
    val profileCode: String,
    @SerializedName("configSource")
    val configSource: String,
    @SerializedName("executionCount")
    val executionCount: Int,
    @SerializedName("avgEffectScore")
    val avgEffectScore: Float = 0f,
    @SerializedName("avgStressDrop")
    val avgStressDrop: Float = 0f
)

data class RecommendationDailyTrendItem(
    @SerializedName("date")
    val date: String,
    @SerializedName("executionCount")
    val executionCount: Int,
    @SerializedName("avgEffectScore")
    val avgEffectScore: Float = 0f,
    @SerializedName("avgStressDrop")
    val avgStressDrop: Float = 0f
)

// ========== Demo bootstrap ==========
data class DemoBootstrapResponse(
    @SerializedName("code")
    val code: Int,
    @SerializedName("message")
    val message: String,
    @SerializedName("data")
    val data: DemoBootstrapData?
)

data class DemoBootstrapData(
    @SerializedName("demoRole")
    val demoRole: String,
    @SerializedName("demoScenario")
    val demoScenario: String,
    @SerializedName("demoSeedVersion")
    val demoSeedVersion: String,
    @SerializedName("displayName")
    val displayName: String,
    @SerializedName("snapshot")
    val snapshot: DemoBootstrapSnapshot
)

data class DemoBootstrapSnapshot(
    @SerializedName("devices")
    val devices: List<DemoDeviceRecord> = emptyList(),
    @SerializedName("sleepRecords")
    val sleepRecords: List<DemoSleepRecord> = emptyList(),
    @SerializedName("healthMetrics")
    val healthMetrics: List<DemoHealthMetricsRecord> = emptyList(),
    @SerializedName("recoveryScores")
    val recoveryScores: List<DemoRecoveryScoreRecord> = emptyList(),
    @SerializedName("doctorSessions")
    val doctorSessions: List<DemoDoctorSessionRecord> = emptyList(),
    @SerializedName("doctorMessages")
    val doctorMessages: List<DemoDoctorMessageRecord> = emptyList(),
    @SerializedName("doctorAssessments")
    val doctorAssessments: List<DemoDoctorAssessmentRecord> = emptyList(),
    @SerializedName("assessmentSessions")
    val assessmentSessions: List<DemoAssessmentSessionRecord> = emptyList(),
    @SerializedName("assessmentAnswers")
    val assessmentAnswers: List<DemoAssessmentAnswerRecord> = emptyList(),
    @SerializedName("interventionTasks")
    val interventionTasks: List<DemoInterventionTaskRecord> = emptyList(),
    @SerializedName("interventionExecutions")
    val interventionExecutions: List<DemoInterventionExecutionRecord> = emptyList(),
    @SerializedName("medicalReports")
    val medicalReports: List<DemoMedicalReportRecord> = emptyList(),
    @SerializedName("medicalMetrics")
    val medicalMetrics: List<DemoMedicalMetricRecord> = emptyList(),
    @SerializedName("relaxSessions")
    val relaxSessions: List<DemoRelaxSessionRecord> = emptyList(),
    @SerializedName("interventionProfileSnapshots")
    val interventionProfileSnapshots: List<DemoInterventionProfileSnapshotRecord> = emptyList(),
    @SerializedName("prescriptionBundles")
    val prescriptionBundles: List<DemoPrescriptionBundleRecord> = emptyList(),
    @SerializedName("prescriptionItems")
    val prescriptionItems: List<DemoPrescriptionItemRecord> = emptyList(),
    @SerializedName("medicationRecords")
    val medicationRecords: List<DemoMedicationRecord> = emptyList(),
    @SerializedName("foodRecords")
    val foodRecords: List<DemoFoodRecord> = emptyList()
)

data class DemoDeviceRecord(
    @SerializedName("deviceId") val deviceId: String,
    @SerializedName("deviceName") val deviceName: String,
    @SerializedName("macAddress") val macAddress: String,
    @SerializedName("batteryLevel") val batteryLevel: Int,
    @SerializedName("firmwareVersion") val firmwareVersion: String,
    @SerializedName("connectionState") val connectionState: String,
    @SerializedName("lastSyncTime") val lastSyncTime: Long,
    @SerializedName("isPrimary") val isPrimary: Boolean,
    @SerializedName("createdAt") val createdAt: Long,
    @SerializedName("updatedAt") val updatedAt: Long
)

data class DemoSleepRecord(
    @SerializedName("id") val id: String,
    @SerializedName("date") val date: Long,
    @SerializedName("bedTime") val bedTime: Long,
    @SerializedName("wakeTime") val wakeTime: Long,
    @SerializedName("totalSleepMinutes") val totalSleepMinutes: Int,
    @SerializedName("deepSleepMinutes") val deepSleepMinutes: Int,
    @SerializedName("lightSleepMinutes") val lightSleepMinutes: Int,
    @SerializedName("remSleepMinutes") val remSleepMinutes: Int,
    @SerializedName("awakeMinutes") val awakeMinutes: Int,
    @SerializedName("sleepEfficiency") val sleepEfficiency: Float,
    @SerializedName("fallAsleepMinutes") val fallAsleepMinutes: Int,
    @SerializedName("awakeCount") val awakeCount: Int,
    @SerializedName("createdAt") val createdAt: Long,
    @SerializedName("updatedAt") val updatedAt: Long
)

data class DemoHealthMetricsRecord(
    @SerializedName("id") val id: String,
    @SerializedName("sleepRecordId") val sleepRecordId: String,
    @SerializedName("timestamp") val timestamp: Long,
    @SerializedName("heartRateSample") val heartRateSample: Int,
    @SerializedName("bloodOxygenSample") val bloodOxygenSample: Int,
    @SerializedName("temperatureSample") val temperatureSample: Float,
    @SerializedName("stepsSample") val stepsSample: Int,
    @SerializedName("accMagnitudeSample") val accMagnitudeSample: Float,
    @SerializedName("heartRateCurrent") val heartRateCurrent: Int,
    @SerializedName("heartRateAvg") val heartRateAvg: Int,
    @SerializedName("heartRateMin") val heartRateMin: Int,
    @SerializedName("heartRateMax") val heartRateMax: Int,
    @SerializedName("heartRateTrend") val heartRateTrend: String,
    @SerializedName("bloodOxygenCurrent") val bloodOxygenCurrent: Int,
    @SerializedName("bloodOxygenAvg") val bloodOxygenAvg: Int,
    @SerializedName("bloodOxygenMin") val bloodOxygenMin: Int,
    @SerializedName("bloodOxygenStability") val bloodOxygenStability: String,
    @SerializedName("temperatureCurrent") val temperatureCurrent: Float,
    @SerializedName("temperatureAvg") val temperatureAvg: Float,
    @SerializedName("temperatureStatus") val temperatureStatus: String,
    @SerializedName("hrvCurrent") val hrvCurrent: Int,
    @SerializedName("hrvBaseline") val hrvBaseline: Int,
    @SerializedName("hrvRecoveryRate") val hrvRecoveryRate: Float,
    @SerializedName("hrvTrend") val hrvTrend: String
)

data class DemoRecoveryScoreRecord(
    @SerializedName("id") val id: String,
    @SerializedName("sleepRecordId") val sleepRecordId: String,
    @SerializedName("date") val date: Long,
    @SerializedName("score") val score: Int,
    @SerializedName("sleepEfficiencyScore") val sleepEfficiencyScore: Float,
    @SerializedName("hrvRecoveryScore") val hrvRecoveryScore: Float,
    @SerializedName("deepSleepScore") val deepSleepScore: Float,
    @SerializedName("temperatureRhythmScore") val temperatureRhythmScore: Float,
    @SerializedName("oxygenStabilityScore") val oxygenStabilityScore: Float,
    @SerializedName("level") val level: String,
    @SerializedName("createdAt") val createdAt: Long
)

data class DemoDoctorSessionRecord(
    @SerializedName("id") val id: String,
    @SerializedName("createdAt") val createdAt: Long,
    @SerializedName("updatedAt") val updatedAt: Long,
    @SerializedName("status") val status: String,
    @SerializedName("domain") val domain: String,
    @SerializedName("chiefComplaint") val chiefComplaint: String,
    @SerializedName("riskLevel") val riskLevel: String
)

data class DemoDoctorMessageRecord(
    @SerializedName("id") val id: String,
    @SerializedName("sessionId") val sessionId: String,
    @SerializedName("role") val role: String,
    @SerializedName("messageType") val messageType: String,
    @SerializedName("content") val content: String,
    @SerializedName("timestamp") val timestamp: Long,
    @SerializedName("payloadJson") val payloadJson: String? = null,
    @SerializedName("actionProtocolType") val actionProtocolType: String? = null,
    @SerializedName("actionDurationSec") val actionDurationSec: Int? = null
)

data class DemoDoctorAssessmentRecord(
    @SerializedName("id") val id: String,
    @SerializedName("sessionId") val sessionId: String,
    @SerializedName("createdAt") val createdAt: Long,
    @SerializedName("suspectedIssuesJson") val suspectedIssuesJson: String,
    @SerializedName("symptomFactsJson") val symptomFactsJson: String,
    @SerializedName("missingInfoJson") val missingInfoJson: String,
    @SerializedName("redFlagsJson") val redFlagsJson: String,
    @SerializedName("recommendedDepartment") val recommendedDepartment: String,
    @SerializedName("doctorSummary") val doctorSummary: String,
    @SerializedName("nextStepAdviceJson") val nextStepAdviceJson: String,
    @SerializedName("disclaimer") val disclaimer: String
)

data class DemoAssessmentSessionRecord(
    @SerializedName("id") val id: String,
    @SerializedName("scaleCode") val scaleCode: String,
    @SerializedName("startedAt") val startedAt: Long,
    @SerializedName("completedAt") val completedAt: Long?,
    @SerializedName("totalScore") val totalScore: Int,
    @SerializedName("severityLevel") val severityLevel: String,
    @SerializedName("freshnessUntil") val freshnessUntil: Long,
    @SerializedName("source") val source: String
)

data class DemoAssessmentAnswerRecord(
    @SerializedName("id") val id: String,
    @SerializedName("sessionId") val sessionId: String,
    @SerializedName("itemCode") val itemCode: String,
    @SerializedName("itemOrder") val itemOrder: Int,
    @SerializedName("answerValue") val answerValue: Int
)

data class DemoInterventionTaskRecord(
    @SerializedName("id") val id: String,
    @SerializedName("date") val date: Long,
    @SerializedName("sourceType") val sourceType: String,
    @SerializedName("triggerReason") val triggerReason: String,
    @SerializedName("bodyZone") val bodyZone: String,
    @SerializedName("protocolType") val protocolType: String,
    @SerializedName("durationSec") val durationSec: Int,
    @SerializedName("plannedAt") val plannedAt: Long,
    @SerializedName("status") val status: String,
    @SerializedName("createdAt") val createdAt: Long,
    @SerializedName("updatedAt") val updatedAt: Long
)

data class DemoInterventionExecutionRecord(
    @SerializedName("id") val id: String,
    @SerializedName("taskId") val taskId: String,
    @SerializedName("startedAt") val startedAt: Long,
    @SerializedName("endedAt") val endedAt: Long,
    @SerializedName("elapsedSec") val elapsedSec: Int,
    @SerializedName("beforeStress") val beforeStress: Float,
    @SerializedName("afterStress") val afterStress: Float,
    @SerializedName("beforeHr") val beforeHr: Int,
    @SerializedName("afterHr") val afterHr: Int,
    @SerializedName("effectScore") val effectScore: Float,
    @SerializedName("completionType") val completionType: String,
    @SerializedName("metadataJson") val metadataJson: String? = null
)

data class DemoMedicalReportRecord(
    @SerializedName("id") val id: String,
    @SerializedName("reportDate") val reportDate: Long,
    @SerializedName("reportType") val reportType: String,
    @SerializedName("imageUri") val imageUri: String,
    @SerializedName("ocrTextDigest") val ocrTextDigest: String,
    @SerializedName("parseStatus") val parseStatus: String,
    @SerializedName("riskLevel") val riskLevel: String,
    @SerializedName("createdAt") val createdAt: Long
)

data class DemoMedicalMetricRecord(
    @SerializedName("id") val id: String,
    @SerializedName("reportId") val reportId: String,
    @SerializedName("metricCode") val metricCode: String,
    @SerializedName("metricName") val metricName: String,
    @SerializedName("metricValue") val metricValue: Float,
    @SerializedName("unit") val unit: String,
    @SerializedName("refLow") val refLow: Float?,
    @SerializedName("refHigh") val refHigh: Float?,
    @SerializedName("isAbnormal") val isAbnormal: Boolean,
    @SerializedName("confidence") val confidence: Float
)

data class DemoRelaxSessionRecord(
    @SerializedName("id") val id: String,
    @SerializedName("startTime") val startTime: Long,
    @SerializedName("endTime") val endTime: Long,
    @SerializedName("protocolType") val protocolType: String,
    @SerializedName("durationSec") val durationSec: Int,
    @SerializedName("preStress") val preStress: Float,
    @SerializedName("postStress") val postStress: Float,
    @SerializedName("preHr") val preHr: Int,
    @SerializedName("postHr") val postHr: Int,
    @SerializedName("preHrv") val preHrv: Int,
    @SerializedName("postHrv") val postHrv: Int,
    @SerializedName("preMotion") val preMotion: Float,
    @SerializedName("postMotion") val postMotion: Float,
    @SerializedName("effectScore") val effectScore: Float,
    @SerializedName("metadataJson") val metadataJson: String? = null
)

data class DemoInterventionProfileSnapshotRecord(
    @SerializedName("id") val id: String,
    @SerializedName("generatedAt") val generatedAt: Long,
    @SerializedName("triggerType") val triggerType: String,
    @SerializedName("domainScoresJson") val domainScoresJson: String,
    @SerializedName("evidenceFactsJson") val evidenceFactsJson: String,
    @SerializedName("redFlagsJson") val redFlagsJson: String
)

data class DemoPrescriptionBundleRecord(
    @SerializedName("id") val id: String,
    @SerializedName("createdAt") val createdAt: Long,
    @SerializedName("triggerType") val triggerType: String,
    @SerializedName("profileSnapshotId") val profileSnapshotId: String,
    @SerializedName("primaryGoal") val primaryGoal: String,
    @SerializedName("riskLevel") val riskLevel: String,
    @SerializedName("rationale") val rationale: String,
    @SerializedName("evidenceJson") val evidenceJson: String,
    @SerializedName("status") val status: String
)

data class DemoPrescriptionItemRecord(
    @SerializedName("id") val id: String,
    @SerializedName("bundleId") val bundleId: String,
    @SerializedName("itemType") val itemType: String,
    @SerializedName("protocolCode") val protocolCode: String,
    @SerializedName("assetRef") val assetRef: String,
    @SerializedName("durationSec") val durationSec: Int,
    @SerializedName("sequenceOrder") val sequenceOrder: Int,
    @SerializedName("timingSlot") val timingSlot: String,
    @SerializedName("isRequired") val isRequired: Boolean,
    @SerializedName("status") val status: String
)

data class DemoMedicationRecord(
    @SerializedName("id") val id: String,
    @SerializedName("capturedAt") val capturedAt: Long,
    @SerializedName("imageUri") val imageUri: String,
    @SerializedName("recognizedName") val recognizedName: String,
    @SerializedName("dosageForm") val dosageForm: String,
    @SerializedName("specification") val specification: String,
    @SerializedName("activeIngredientsJson") val activeIngredientsJson: String,
    @SerializedName("matchedSymptomsJson") val matchedSymptomsJson: String,
    @SerializedName("usageSummary") val usageSummary: String,
    @SerializedName("riskLevel") val riskLevel: String,
    @SerializedName("riskFlagsJson") val riskFlagsJson: String,
    @SerializedName("evidenceNotesJson") val evidenceNotesJson: String,
    @SerializedName("advice") val advice: String,
    @SerializedName("confidence") val confidence: Float,
    @SerializedName("requiresManualReview") val requiresManualReview: Boolean,
    @SerializedName("analysisMode") val analysisMode: String,
    @SerializedName("providerId") val providerId: String? = null,
    @SerializedName("modelId") val modelId: String? = null,
    @SerializedName("traceId") val traceId: String? = null,
    @SerializedName("syncState") val syncState: String,
    @SerializedName("cloudRecordId") val cloudRecordId: String? = null,
    @SerializedName("syncedAt") val syncedAt: Long? = null,
    @SerializedName("createdAt") val createdAt: Long,
    @SerializedName("updatedAt") val updatedAt: Long
)

data class DemoFoodRecord(
    @SerializedName("id") val id: String,
    @SerializedName("capturedAt") val capturedAt: Long,
    @SerializedName("imageUri") val imageUri: String,
    @SerializedName("mealType") val mealType: String,
    @SerializedName("foodItemsJson") val foodItemsJson: String,
    @SerializedName("estimatedCalories") val estimatedCalories: Int,
    @SerializedName("carbohydrateGrams") val carbohydrateGrams: Float,
    @SerializedName("proteinGrams") val proteinGrams: Float,
    @SerializedName("fatGrams") val fatGrams: Float,
    @SerializedName("nutritionRiskLevel") val nutritionRiskLevel: String,
    @SerializedName("nutritionFlagsJson") val nutritionFlagsJson: String,
    @SerializedName("dailyContribution") val dailyContribution: String,
    @SerializedName("advice") val advice: String,
    @SerializedName("confidence") val confidence: Float,
    @SerializedName("requiresManualReview") val requiresManualReview: Boolean,
    @SerializedName("analysisMode") val analysisMode: String,
    @SerializedName("providerId") val providerId: String? = null,
    @SerializedName("modelId") val modelId: String? = null,
    @SerializedName("traceId") val traceId: String? = null,
    @SerializedName("syncState") val syncState: String,
    @SerializedName("cloudRecordId") val cloudRecordId: String? = null,
    @SerializedName("syncedAt") val syncedAt: Long? = null,
    @SerializedName("createdAt") val createdAt: Long,
    @SerializedName("updatedAt") val updatedAt: Long
)
