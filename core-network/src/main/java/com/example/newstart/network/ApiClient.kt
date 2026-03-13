package com.example.newstart.network

import android.content.Context
import com.example.newstart.core.network.BuildConfig
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * API瀹㈡埛绔?
 * 閰嶇疆Retrofit鍜孫kHttp
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
     * 璁剧疆璁よ瘉Token
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
     * 娓呴櫎Token
     */
    fun clearAuthToken() {
        authToken = null
        CloudSessionStore.clear()
    }
    
    /**
     * 鏃ュ織鎷︽埅鍣?
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
     * 璁よ瘉鎷︽埅鍣?
     */
    private val authInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        val requestBuilder = originalRequest.newBuilder()
        
        // 娣诲姞Token
        authToken?.let {
            requestBuilder.addHeader("Authorization", "Bearer $it")
        }
        
        // 娣诲姞閫氱敤Header
        requestBuilder.addHeader("Content-Type", "application/json")
        requestBuilder.addHeader("Accept", "application/json")
        
        chain.proceed(requestBuilder.build())
    }
    
    /**
     * OkHttp瀹㈡埛绔?
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
     * Retrofit瀹炰緥
     */
    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    
    /**
     * API鏈嶅姟
     */
    val apiService: ApiService = retrofit.create(ApiService::class.java)
}

