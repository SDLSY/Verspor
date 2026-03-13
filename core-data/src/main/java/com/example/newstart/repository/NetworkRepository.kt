package com.example.newstart.repository

import com.example.newstart.network.ApiClient
import com.example.newstart.network.CloudSession
import com.example.newstart.network.models.*
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.io.IOException
import java.io.File
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 网络数据仓库
 * 负责与云端服务通信
 */
class NetworkRepository {
    
    private val apiService = ApiClient.apiService
    
    /**
     * 用户登录
     */
    suspend fun login(email: String, password: String): Result<AuthData> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.login(LoginRequest(email, password))
                if (response.isSuccessful && response.body()?.data != null) {
                    val loginData = response.body()!!.data!!
                    if (loginData.authState != "SIGNED_IN") {
                        return@withContext Result.success(loginData)
                    }
                    ApiClient.setAuthSession(
                        CloudSession(
                            token = loginData.token,
                            userId = loginData.userId,
                            username = loginData.username,
                            email = email
                        )
                    )
                    Result.success(loginData)
                } else {
                    Result.failure(Exception(extractErrorMessage(response, response.body()?.message ?: "登录失败")))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(Exception(mapExceptionMessage(e)))
            }
        }
    }

    suspend fun register(email: String, password: String, username: String): Result<AuthData> {
        return withContext(Dispatchers.IO) {
            try {
                val response = runCatching {
                    apiService.register(RegisterRequest(email, password, username))
                }.getOrElse { firstError ->
                    if (firstError is CancellationException) throw firstError
                    if (isTransientNetworkError(firstError)) {
                        delay(800)
                        return@withContext runCatching {
                            apiService.register(RegisterRequest(email, password, username))
                        }.fold(
                            onSuccess = { handleRegisterResponse(it, email, password) },
                            onFailure = { retryError ->
                                if (retryError is CancellationException) throw retryError
                                Result.failure(Exception(mapExceptionMessage(retryError)))
                            }
                        )
                    }
                    return@withContext Result.failure(Exception(mapExceptionMessage(firstError)))
                }

                handleRegisterResponse(response, email, password)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(Exception(mapExceptionMessage(e)))
            }
        }
    }

    private suspend fun handleRegisterResponse(
        response: Response<AuthResponse>,
        email: String,
        password: String
    ): Result<AuthData> {
        return if (response.isSuccessful && response.body()?.data != null) {
            val loginData = response.body()!!.data!!
            if (loginData.authState != "SIGNED_IN") {
                Result.success(loginData)
            } else {
            ApiClient.setAuthSession(
                CloudSession(
                    token = loginData.token,
                    userId = loginData.userId,
                    username = loginData.username,
                    email = email
                )
            )
            Result.success(loginData)
            }
        } else if (response.isSuccessful) {
            val loginResult = login(email, password)
            loginResult.fold(
                onSuccess = { Result.success(it) },
                onFailure = {
                    val registerMsg = response.body()?.message ?: "注册成功，但自动登录失败"
                    Result.failure(Exception("$registerMsg；${it.message ?: "请手动登录"}"))
                }
            )
        } else {
            Result.failure(Exception(extractErrorMessage(response, response.body()?.message ?: "注册失败")))
        }
    }

    private fun isTransientNetworkError(error: Throwable): Boolean {
        val msg = (error.message ?: "").lowercase()
        return error is IOException ||
            msg.contains("connection closed") ||
            msg.contains("unexpected end") ||
            msg.contains("eof") ||
            msg.contains("timeout") ||
            msg.contains("ssl")
    }

    private fun mapExceptionMessage(error: Throwable): String {
        val msg = (error.message ?: "").trim()
        val lower = msg.lowercase()
        return when {
            lower.contains("rate limit") -> "请求过于频繁，请等待约1分钟后重试"
            lower.contains("connection closed") || lower.contains("unexpected end") || lower.contains("eof") ->
                "网络连接被中断，请检查网络后重试（避免连续点击，以免触发限流）"
            lower.contains("timeout") -> "请求超时，请稍后重试"
            msg.isNotBlank() -> msg
            else -> "网络请求失败"
        }
    }

    fun getCurrentSession(): CloudSession? {
        return ApiClient.getAuthSession()
    }

    fun logout() {
        ApiClient.clearAuthToken()
    }
    
    /**
     * 上传睡眠数据
     */
    suspend fun uploadSleepData(request: SleepDataRequest): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.uploadSleepData(request)
                if (response.isSuccessful && response.body()?.success == true) {
                    Result.success(true)
                } else {
                    Result.failure(Exception(response.body()?.message ?: "上传失败"))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    /**
     * 请求睡眠分析
     */
    suspend fun analyzeSleep(request: SleepAnalysisRequest): Result<SleepAnalysisData> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.analyzeSleep(request)
                if (response.isSuccessful && response.body()?.data != null) {
                    Result.success(response.body()!!.data!!)
                } else {
                    Result.failure(Exception(response.body()?.message ?: "分析失败"))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    /**
     * 获取睡眠历史
     */
    suspend fun getSleepHistory(startDate: Long, endDate: Long): Result<List<SleepHistoryItem>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.getSleepHistory(startDate, endDate)
                if (response.isSuccessful && response.body()?.data != null) {
                    Result.success(response.body()!!.data!!)
                } else {
                    Result.failure(Exception(response.body()?.message ?: "获取失败"))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    /**
     * 同步数据到云端
     */
    suspend fun syncData(request: SyncRequest): Result<SyncData> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.syncData(request)
                if (response.isSuccessful && response.body()?.data != null) {
                    Result.success(response.body()!!.data!!)
                } else {
                    Result.failure(Exception(response.body()?.message ?: "同步失败"))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    /**
     * 生成个性化建议
     */
    suspend fun generateAdvice(request: AdviceRequest): Result<List<AdviceItem>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.generateAdvice(request)
                if (response.isSuccessful && response.body()?.data != null) {
                    Result.success(response.body()!!.data!!)
                } else {
                    Result.failure(Exception(response.body()?.message ?: "生成失败"))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun upsertReportMetrics(request: MedicalMetricUpsertRequest): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.upsertReportMetrics(request)
                if (response.isSuccessful && response.body()?.code == 0) {
                    Result.success(true)
                } else {
                    Result.failure(Exception(response.body()?.message ?: "upsert report metrics failed"))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getLatestReport(): Result<LatestMedicalReportData?> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.getLatestReport()
                if (response.isSuccessful) {
                    Result.success(response.body()?.data)
                } else {
                    Result.failure(Exception(response.body()?.message ?: "fetch latest report failed"))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun upsertInterventionTask(request: InterventionTaskUpsertRequest): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.upsertInterventionTask(request)
                if (response.isSuccessful && response.body()?.code == 0) {
                    Result.success(true)
                } else {
                    Result.failure(Exception(response.body()?.message ?: "upsert intervention task failed"))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun upsertInterventionExecution(request: InterventionExecutionUpsertRequest): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.upsertInterventionExecution(request)
                if (response.isSuccessful && response.body()?.code == 0) {
                    Result.success(true)
                } else {
                    Result.failure(Exception(response.body()?.message ?: "upsert intervention execution failed"))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun upsertAssessmentBaselineSummary(
        request: AssessmentBaselineSummaryUpsertRequest
    ): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.upsertAssessmentBaselineSummary(request)
                if (response.isSuccessful && response.body()?.code == 0) {
                    Result.success(true)
                } else {
                    Result.failure(
                        Exception(response.body()?.message ?: "upsert assessment baseline summary failed")
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun upsertDoctorInquirySummary(
        request: DoctorInquirySummaryUpsertRequest
    ): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.upsertDoctorInquirySummary(request)
                if (response.isSuccessful && response.body()?.code == 0) {
                    Result.success(true)
                } else {
                    Result.failure(
                        Exception(response.body()?.message ?: "upsert doctor inquiry summary failed")
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getInterventionEffectTrend(days: Int): Result<List<InterventionEffectTrendItem>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.getInterventionEffectTrend(days)
                if (response.isSuccessful && response.body()?.data != null) {
                    Result.success(response.body()!!.data!!)
                } else {
                    Result.failure(Exception(response.body()?.message ?: "fetch intervention trend failed"))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getPeriodSummary(period: String): Result<PeriodSummaryData?> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.getPeriodSummary(period)
                if (response.isSuccessful) {
                    Result.success(response.body()?.data)
                } else {
                    Result.failure(Exception(response.body()?.message ?: "fetch period summary failed"))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun generateDoctorTurn(request: DoctorTurnRequest): Result<DoctorTurnData> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.generateDoctorTurn(request)
                if (response.isSuccessful && response.body()?.data != null) {
                    Result.success(response.body()!!.data!!)
                } else {
                    Result.failure(Exception(response.body()?.message ?: "generate doctor turn failed"))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun understandMedicalReport(request: ReportUnderstandingRequest): Result<ReportUnderstandingData> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.understandMedicalReport(request)
                if (response.isSuccessful && response.body()?.data != null) {
                    Result.success(response.body()!!.data!!)
                } else {
                    Result.failure(Exception(response.body()?.message ?: "understand medical report failed"))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun transcribeSpeech(request: SpeechTranscriptionRequest): Result<SpeechTranscriptionData> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.transcribeSpeech(request)
                if (response.isSuccessful && response.body()?.data != null) {
                    Result.success(response.body()!!.data!!)
                } else {
                    Result.failure(Exception(response.body()?.message ?: "transcribe speech failed"))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun transcribeSpeechFile(
        file: File,
        mimeType: String = "audio/mp4",
        hint: String = ""
    ): Result<SpeechTranscriptionData> {
        return withContext(Dispatchers.IO) {
            try {
                val mediaType = mimeType.toMediaType()
                val filePart = MultipartBody.Part.createFormData(
                    "file",
                    file.name,
                    file.asRequestBody(mediaType)
                )
                val mimeBody = mimeType.toRequestBody("text/plain".toMediaType())
                val hintBody = hint.toRequestBody("text/plain".toMediaType())
                val response = apiService.transcribeSpeechUpload(filePart, mimeBody, hintBody)
                if (response.isSuccessful && response.body()?.data != null) {
                    Result.success(response.body()!!.data!!)
                } else {
                    Result.failure(Exception(response.body()?.message ?: "transcribe speech file failed"))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun synthesizeSpeech(request: SpeechSynthesisRequest): Result<SpeechSynthesisData> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.synthesizeSpeech(request)
                if (response.isSuccessful && response.body()?.data != null) {
                    Result.success(response.body()!!.data!!)
                } else {
                    Result.failure(Exception(response.body()?.message ?: "synthesize speech failed"))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun generateImage(request: ImageGenerationRequest): Result<ImageGenerationData> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.generateImage(request)
                if (response.isSuccessful && response.body()?.data != null) {
                    Result.success(response.body()!!.data!!)
                } else {
                    Result.failure(Exception(response.body()?.message ?: "generate image failed"))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun generateVideo(request: VideoGenerationRequest): Result<VideoGenerationData> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.generateVideo(request)
                if (response.isSuccessful && response.body()?.data != null) {
                    Result.success(response.body()!!.data!!)
                } else {
                    Result.failure(Exception(response.body()?.message ?: "generate video failed"))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getVideoJob(jobId: String): Result<VideoJobData> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.getVideoJob(jobId)
                if (response.isSuccessful && response.body()?.data != null) {
                    Result.success(response.body()!!.data!!)
                } else {
                    Result.failure(Exception(response.body()?.message ?: "get video job failed"))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getRecommendationExplanations(
        traceType: String? = null,
        traceId: String? = null,
        limit: Int? = null
    ): Result<RecommendationExplanationsData> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.getRecommendationExplanations(
                    traceType = traceType,
                    traceId = traceId,
                    limit = limit
                )
                if (response.isSuccessful && response.body()?.data != null) {
                    Result.success(response.body()!!.data!!)
                } else {
                    Result.failure(
                        Exception(response.body()?.message ?: "fetch recommendation explanations failed")
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getRecommendationEffects(
        days: Int? = null,
        recommendationMode: String? = null,
        profileCode: String? = null
    ): Result<RecommendationEffectSummaryData> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.getRecommendationEffects(
                    days = days,
                    recommendationMode = recommendationMode,
                    profileCode = profileCode
                )
                if (response.isSuccessful && response.body()?.data != null) {
                    Result.success(response.body()!!.data!!)
                } else {
                    Result.failure(
                        Exception(response.body()?.message ?: "fetch recommendation effects failed")
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private fun <T> extractErrorMessage(response: Response<T>, fallback: String): String {
        val bodyMsg = runCatching {
            val raw = response.errorBody()?.string().orEmpty()
            if (raw.isBlank()) return@runCatching ""
            val root = JsonParser.parseString(raw).asJsonObject
            root.get("message")?.asString.orEmpty()
        }.getOrDefault("")

        return bodyMsg.ifBlank { fallback }
    }
}
