package com.example.newstart.repository

import com.example.newstart.demo.DemoConfig
import com.example.newstart.network.ApiClient
import com.example.newstart.network.CloudSession
import com.example.newstart.network.models.ActionResponse
import com.example.newstart.network.models.AuthData
import com.example.newstart.network.models.AuthResponse
import com.example.newstart.network.models.DemoBootstrapData
import com.example.newstart.network.models.EmailActionRequest
import com.example.newstart.network.models.LoginRequest
import com.example.newstart.network.models.RegisterRequest
import com.example.newstart.network.models.UserProfile
import com.example.newstart.network.models.UserProfileRequest
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.io.IOException

class CloudAccountRepository {

    private val apiService = ApiClient.apiService

    suspend fun login(email: String, password: String): Result<AuthData> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.login(LoginRequest(email, password))
                handleAuthResponse(response, "登录失败")
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
                    if (firstError is CancellationException) {
                        throw firstError
                    }
                    if (isTransientNetworkError(firstError)) {
                        delay(800)
                        return@withContext runCatching {
                            apiService.register(RegisterRequest(email, password, username))
                        }.fold(
                            onSuccess = { handleAuthResponse(it, "注册失败") },
                            onFailure = { retryError ->
                                if (retryError is CancellationException) {
                                    throw retryError
                                }
                                Result.failure(Exception(mapExceptionMessage(retryError)))
                            }
                        )
                    }
                    return@withContext Result.failure(Exception(mapExceptionMessage(firstError)))
                }

                handleAuthResponse(response, "注册失败")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(Exception(mapExceptionMessage(e)))
            }
        }
    }

    suspend fun resendConfirmation(email: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.resendConfirmation(EmailActionRequest(email))
                handleActionResponse(response, "重新发送确认邮件失败")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(Exception(mapExceptionMessage(e)))
            }
        }
    }

    suspend fun requestPasswordReset(email: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.requestPasswordReset(EmailActionRequest(email))
                handleActionResponse(response, "发送重置密码邮件失败")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(Exception(mapExceptionMessage(e)))
            }
        }
    }

    suspend fun loginWithDemoAccount(): Result<AuthData> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.loginWithDemoAccount()
                handleAuthResponse(response, "演示账号登录失败")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(Exception(mapExceptionMessage(e)))
            }
        }
    }

    suspend fun getUserProfile(): Result<UserProfile> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.getUserProfile()
                if (response.isSuccessful && response.body()?.data != null) {
                    val profile = response.body()!!.data!!
                    syncSessionProfile(profile)
                    Result.success(profile)
                } else {
                    Result.failure(
                        Exception(
                            extractAuthenticatedErrorMessage(
                                response,
                                response.body()?.message ?: "获取个人资料失败"
                            )
                        )
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(Exception(mapExceptionMessage(e)))
            }
        }
    }

    suspend fun updateUserProfile(request: UserProfileRequest): Result<UserProfile> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.updateUserProfile(request)
                if (response.isSuccessful && response.body()?.data != null) {
                    val profile = response.body()!!.data!!
                    syncSessionProfile(profile)
                    Result.success(profile)
                } else {
                    Result.failure(
                        Exception(
                            extractAuthenticatedErrorMessage(
                                response,
                                response.body()?.message ?: "保存个人资料失败"
                            )
                        )
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(Exception(mapExceptionMessage(e)))
            }
        }
    }

    suspend fun getDemoBootstrap(): Result<DemoBootstrapData> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.getDemoBootstrap()
                if (response.isSuccessful && response.body()?.data != null) {
                    Result.success(response.body()!!.data!!)
                } else {
                    Result.failure(
                        Exception(
                            extractAuthenticatedErrorMessage(
                                response,
                                response.body()?.message ?: "获取演示数据失败"
                            )
                        )
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(Exception(mapExceptionMessage(e)))
            }
        }
    }

    fun getCurrentSession(): CloudSession? {
        return ApiClient.getAuthSession()
    }

    fun logout() {
        DemoConfig.isDemoMode = false
        ApiClient.clearAuthToken()
    }

    private fun handleAuthResponse(response: Response<AuthResponse>, fallback: String): Result<AuthData> {
        val authData = response.body()?.data
        return if (response.isSuccessful && authData != null) {
            completeSessionUpdate(authData)
            Result.success(authData)
        } else {
            Result.failure(Exception(extractErrorMessage(response, response.body()?.message ?: fallback)))
        }
    }

    private fun handleActionResponse(response: Response<ActionResponse>, fallback: String): Result<String> {
        return if (response.isSuccessful && response.body()?.code == 0) {
            Result.success(response.body()?.message ?: "ok")
        } else {
            Result.failure(Exception(extractErrorMessage(response, response.body()?.message ?: fallback)))
        }
    }

    private fun completeSessionUpdate(authData: AuthData) {
        if (authData.authState != "SIGNED_IN") {
            return
        }
        val token = safeAuthValue(authData.token)
        val refreshToken = safeAuthValue(authData.refreshToken)
        val userId = safeAuthValue(authData.userId)
        val email = safeAuthValue(authData.email)
        val username = safeAuthValue(authData.username).ifBlank {
            email.substringBefore("@", userId)
        }

        if (token.isBlank() || userId.isBlank()) {
            return
        }
        ApiClient.setAuthSession(
            CloudSession(
                token = token,
                refreshToken = refreshToken,
                userId = userId,
                username = username,
                email = email
            )
        )
    }

    private fun syncSessionProfile(profile: UserProfile) {
        val currentSession = getCurrentSession() ?: return
        if (currentSession.userId != profile.userId) {
            return
        }
        ApiClient.setAuthSession(
            currentSession.copy(
                username = profile.username,
                email = profile.email
            )
        )
    }

    private fun <T> extractAuthenticatedErrorMessage(response: Response<T>, fallback: String): String {
        val message = extractErrorMessage(response, fallback)
        if (response.code() == 401) {
            ApiClient.clearAuthToken()
            return "登录已过期，请重新登录"
        }
        return message
    }

    private fun isTransientNetworkError(error: Throwable): Boolean {
        val message = error.message.orEmpty().lowercase()
        return error is IOException ||
            message.contains("connection closed") ||
            message.contains("unexpected end") ||
            message.contains("eof") ||
            message.contains("timeout") ||
            message.contains("ssl")
    }

    private fun mapExceptionMessage(error: Throwable): String {
        val message = error.message.orEmpty().trim()
        val lower = message.lowercase()
        return when {
            lower.contains("rate limit") -> "请求过于频繁，请稍后再试"
            lower.contains("connection closed") ||
                lower.contains("unexpected end") ||
                lower.contains("eof") -> "网络连接中断，请检查网络后重试"
            lower.contains("timeout") -> "请求超时，请稍后再试"
            message.isNotBlank() -> message
            else -> "网络请求失败"
        }
    }

    private fun <T> extractErrorMessage(response: Response<T>, fallback: String): String {
        val bodyMessage = runCatching {
            val raw = response.errorBody()?.string().orEmpty()
            if (raw.isBlank()) {
                return@runCatching ""
            }
            JsonParser.parseString(raw).asJsonObject.get("message")?.asString.orEmpty()
        }.getOrDefault("")
        return bodyMessage.ifBlank { fallback }
    }

    private fun safeAuthValue(value: String?): String {
        return value?.trim().orEmpty()
    }
}
