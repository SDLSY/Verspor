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
    val gender: String?
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
    val completionType: String
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
