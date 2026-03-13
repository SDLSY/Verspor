package com.example.newstart.network

import android.content.Context
import com.example.newstart.BuildConfig
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * API客户端
 * 配置Retrofit和OkHttp
 */
object ApiClient {
    
    private val baseUrl: String by lazy {
        val raw = BuildConfig.API_BASE_URL.trim()
        if (raw.endsWith("/")) raw else "$raw/"
    }
    
    private var authToken: String? = null

    fun init(context: Context) {
        CloudSessionStore.init(context)
        authToken = CloudSessionStore.get()?.token
    }
    
    /**
     * 设置认证Token
     */
    fun setAuthToken(token: String) {
        authToken = token
    }

    fun setAuthSession(session: CloudSession) {
        authToken = session.token
        CloudSessionStore.save(session)
    }

    fun getAuthSession(): CloudSession? {
        return CloudSessionStore.get()
    }
    
    /**
     * 清除Token
     */
    fun clearAuthToken() {
        authToken = null
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
        
        // 添加Token
        authToken?.let {
            requestBuilder.addHeader("Authorization", "Bearer $it")
        }
        
        // 添加通用Header
        requestBuilder.addHeader("Content-Type", "application/json")
        requestBuilder.addHeader("Accept", "application/json")
        
        chain.proceed(requestBuilder.build())
    }
    
    /**
     * OkHttp客户端
     */
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .addInterceptor(authInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    
    /**
     * Retrofit实例
     */
    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    
    /**
     * API服务
     */
    val apiService: ApiService = retrofit.create(ApiService::class.java)
}
