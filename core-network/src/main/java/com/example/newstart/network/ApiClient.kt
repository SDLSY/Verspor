package com.example.newstart.network

import android.content.Context
import com.example.newstart.core.network.BuildConfig
import com.example.newstart.network.models.RefreshTokenRequest
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * API 客户端
 * 配置 Retrofit 和 OkHttp
 */
object ApiClient {

    private const val MAX_AUTH_RETRIES = 2

    private val baseUrl: String by lazy {
        val raw = BuildConfig.API_BASE_URL.trim()
        if (raw.endsWith("/")) raw else "$raw/"
    }

    @Volatile
    private var authSession: CloudSession? = null

    fun init(context: Context) {
        CloudSessionStore.init(context)
        authSession = CloudSessionStore.get()
    }

    /**
     * 设置认证 Token
     */
    fun setAuthToken(token: String) {
        val current = getAuthSession() ?: return
        setAuthSession(current.copy(token = token))
    }

    fun setAuthSession(session: CloudSession) {
        authSession = session
        CloudSessionStore.save(session)
    }

    fun getAuthSession(): CloudSession? {
        val cached = authSession
        if (cached != null) {
            return cached
        }
        return CloudSessionStore.get()?.also { authSession = it }
    }

    /**
     * 清除 Token
     */
    fun clearAuthToken() {
        authSession = null
        CloudSessionStore.clear()
    }

    /**
     * 日志拦截器
     */
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BASIC
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
        redactHeader("Authorization")
    }
    
    /**
     * 认证拦截器
     */
    private val authInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        val requestBuilder = originalRequest.newBuilder()

        // 添加 Token
        getAuthSession()?.token?.takeIf { it.isNotBlank() }?.let {
            requestBuilder.header("Authorization", buildBearerToken(it))
        }

        // 添加通用 Header
        if (originalRequest.header("Content-Type").isNullOrBlank()) {
            requestBuilder.addHeader("Content-Type", "application/json")
        }
        if (originalRequest.header("Accept").isNullOrBlank()) {
            requestBuilder.addHeader("Accept", "application/json")
        }

        chain.proceed(requestBuilder.build())
    }

    private val authAuthenticator = Authenticator { _, response ->
        if (response.code != 401) {
            return@Authenticator null
        }
        if (responseCount(response) >= MAX_AUTH_RETRIES) {
            return@Authenticator null
        }
        val request = response.request
        if (isAuthPath(request.url.encodedPath)) {
            return@Authenticator null
        }

        synchronized(this) {
            val currentSession = getAuthSession() ?: return@Authenticator null
            val latestRequest = reuseLatestTokenIfNeeded(request, currentSession)
            if (latestRequest != null) {
                return@Authenticator latestRequest
            }

            val refreshToken = currentSession.refreshToken.trim()
            if (refreshToken.isBlank()) {
                clearAuthToken()
                return@Authenticator null
            }

            val refreshedSession = refreshSessionBlocking(refreshToken) ?: run {
                clearAuthToken()
                return@Authenticator null
            }
            setAuthSession(refreshedSession)
            request.newBuilder()
                .header("Authorization", buildBearerToken(refreshedSession.token))
                .build()
        }
    }

    private fun refreshSessionBlocking(refreshToken: String): CloudSession? {
        val response = runCatching {
            runBlocking {
                refreshApiService.refreshAuth(RefreshTokenRequest(refreshToken))
            }
        }.getOrNull() ?: return null

        val authData = response.body()?.data ?: return null
        if (!response.isSuccessful || authData.authState != "SIGNED_IN") {
            return null
        }
        if (authData.token.isBlank() || authData.userId.isBlank()) {
            return null
        }

        val username = authData.username.ifBlank {
            authData.email.substringBefore("@", authData.userId)
        }
        return CloudSession(
            token = authData.token,
            refreshToken = authData.refreshToken.ifBlank { refreshToken },
            userId = authData.userId,
            username = username,
            email = authData.email
        )
    }

    private fun reuseLatestTokenIfNeeded(request: Request, currentSession: CloudSession): Request? {
        val latestHeader = buildBearerToken(currentSession.token)
        val requestHeader = request.header("Authorization").orEmpty()
        if (requestHeader.isBlank() || requestHeader == latestHeader) {
            return null
        }
        return request.newBuilder()
            .header("Authorization", latestHeader)
            .build()
    }

    private fun responseCount(response: Response): Int {
        var result = 1
        var prior = response.priorResponse
        while (prior != null) {
            result += 1
            prior = prior.priorResponse
        }
        return result
    }

    private fun isAuthPath(path: String): Boolean {
        return path.startsWith("/api/auth/")
    }

    private fun buildBearerToken(token: String): String {
        return "Bearer $token"
    }

    /**
     * OkHttp 客户端
     */
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .addInterceptor(authInterceptor)
        .authenticator(authAuthenticator)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val refreshOkHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /**
     * Retrofit 实例
     */
    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val refreshRetrofit: Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(refreshOkHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    /**
     * API 服务
     */
    val apiService: ApiService = retrofit.create(ApiService::class.java)

    private val refreshApiService: ApiService = refreshRetrofit.create(ApiService::class.java)
}

