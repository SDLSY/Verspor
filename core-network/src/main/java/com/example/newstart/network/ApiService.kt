package com.example.newstart.network

import com.example.newstart.network.models.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

/**
 * 云端API服务接口
 */
interface ApiService {
    
    /**
     * 用户认证
     */
    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>
    
    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>

    @POST("api/auth/refresh")
    suspend fun refreshAuth(@Body request: RefreshTokenRequest): Response<AuthResponse>

    @POST("api/auth/resend-confirmation")
    suspend fun resendConfirmation(@Body request: EmailActionRequest): Response<ActionResponse>

    @POST("api/auth/password-reset")
    suspend fun requestPasswordReset(@Body request: EmailActionRequest): Response<ActionResponse>

    @POST("api/auth/demo-login")
    suspend fun loginWithDemoAccount(): Response<AuthResponse>

    @POST("api/avatar/narration")
    suspend fun generateAvatarNarration(
        @Body request: AvatarNarrationRequest
    ): Response<AvatarNarrationResponse>
    
    /**
     * 睡眠数据上传
     */
    @POST("api/sleep/upload")
    suspend fun uploadSleepData(@Body request: SleepDataRequest): Response<BaseResponse>
    
    /**
     * 睡眠分析请求
     */
    @POST("api/sleep/analyze")
    suspend fun analyzeSleep(@Body request: SleepAnalysisRequest): Response<SleepAnalysisResponse>
    
    /**
     * 获取睡眠历史
     */
    @GET("api/sleep/history")
    suspend fun getSleepHistory(
        @Query("startDate") startDate: Long,
        @Query("endDate") endDate: Long
    ): Response<SleepHistoryResponse>
    
    /**
     * 获取恢复指数趋势
     */
    @GET("api/recovery/trend")
    suspend fun getRecoveryTrend(
        @Query("days") days: Int
    ): Response<RecoveryTrendResponse>
    
    /**
     * 同步数据
     */
    @POST("api/sync")
    suspend fun syncData(@Body request: SyncRequest): Response<SyncResponse>
    
    /**
     * 获取个性化建议
     */
    @POST("api/advice/generate")
    suspend fun generateAdvice(@Body request: AdviceRequest): Response<AdviceResponse>
    
    /**
     * 上传传感器原始数据（用于模型训练）
     */
    @POST("api/data/upload")
    suspend fun uploadRawData(@Body request: RawDataRequest): Response<BaseResponse>
    
    /**
     * 获取用户信息
     */
    @GET("api/user/profile")
    suspend fun getUserProfile(): Response<UserProfileResponse>

    @GET("api/demo/bootstrap")
    suspend fun getDemoBootstrap(): Response<DemoBootstrapResponse>
    
    /**
     * 更新用户信息
     */
    @PUT("api/user/profile")
    suspend fun updateUserProfile(@Body request: UserProfileRequest): Response<UserProfileResponse>

    @POST("api/report/metrics/upsert")
    suspend fun upsertReportMetrics(@Body request: MedicalMetricUpsertRequest): Response<BaseResponse>

    @GET("api/report/latest")
    suspend fun getLatestReport(): Response<LatestMedicalReportResponse>

    @POST("api/intervention/task/upsert")
    suspend fun upsertInterventionTask(@Body request: InterventionTaskUpsertRequest): Response<BaseResponse>

    @POST("api/intervention/execution/upsert")
    suspend fun upsertInterventionExecution(@Body request: InterventionExecutionUpsertRequest): Response<BaseResponse>

    @POST("api/assessment/baseline-summary/upsert")
    suspend fun upsertAssessmentBaselineSummary(@Body request: AssessmentBaselineSummaryUpsertRequest): Response<BaseResponse>

    @POST("api/doctor/inquiry-summary/upsert")
    suspend fun upsertDoctorInquirySummary(@Body request: DoctorInquirySummaryUpsertRequest): Response<BaseResponse>

    @POST("api/medication/records/upsert")
    suspend fun upsertMedicationRecord(@Body request: MedicationRecordUpsertRequest): Response<BaseResponse>

    @POST("api/food/records/upsert")
    suspend fun upsertFoodRecord(@Body request: FoodRecordUpsertRequest): Response<BaseResponse>

    @GET("api/intervention/effect/trend")
    suspend fun getInterventionEffectTrend(
        @Query("days") days: Int
    ): Response<InterventionEffectTrendResponse>

    @GET("api/report/period-summary")
    suspend fun getPeriodSummary(
        @Query("period") period: String
    ): Response<PeriodSummaryResponse>

    @POST("api/doctor/turn")
    suspend fun generateDoctorTurn(@Body request: DoctorTurnRequest): Response<DoctorTurnResponse>

    @POST("api/report/understand")
    suspend fun understandMedicalReport(@Body request: ReportUnderstandingRequest): Response<ReportUnderstandingResponse>

    @Multipart
    @POST("api/medication/analyze")
    suspend fun analyzeMedicationImage(
        @Part file: MultipartBody.Part,
        @Part("mimeType") mimeType: RequestBody
    ): Response<MedicationAnalyzeResponse>

    @Multipart
    @POST("api/food/analyze")
    suspend fun analyzeFoodImage(
        @Part file: MultipartBody.Part,
        @Part("mimeType") mimeType: RequestBody
    ): Response<FoodAnalyzeResponse>

    @POST("api/ai/speech/transcribe")
    suspend fun transcribeSpeech(@Body request: SpeechTranscriptionRequest): Response<SpeechTranscriptionResponse>

    @Multipart
    @POST("api/ai/speech/transcribe")
    suspend fun transcribeSpeechUpload(
        @Part file: MultipartBody.Part,
        @Part("mimeType") mimeType: RequestBody,
        @Part("hint") hint: RequestBody
    ): Response<SpeechTranscriptionResponse>

    @POST("api/ai/speech/synthesize")
    suspend fun synthesizeSpeech(@Body request: SpeechSynthesisRequest): Response<SpeechSynthesisResponse>

    @POST("api/ai/image/generate")
    suspend fun generateImage(@Body request: ImageGenerationRequest): Response<ImageGenerationResponse>

    @POST("api/ai/video/generate")
    suspend fun generateVideo(@Body request: VideoGenerationRequest): Response<VideoGenerationResponse>

    @GET("api/ai/video/jobs/{jobId}")
    suspend fun getVideoJob(@Path("jobId") jobId: String): Response<VideoJobResponse>

    @GET("api/recommendation/explanations")
    suspend fun getRecommendationExplanations(
        @Query("traceType") traceType: String? = null,
        @Query("traceId") traceId: String? = null,
        @Query("limit") limit: Int? = null
    ): Response<RecommendationExplanationsResponse>

    @GET("api/recommendation/effects")
    suspend fun getRecommendationEffects(
        @Query("days") days: Int? = null,
        @Query("recommendationMode") recommendationMode: String? = null,
        @Query("profileCode") profileCode: String? = null
    ): Response<RecommendationEffectsResponse>
}
