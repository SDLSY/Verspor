package com.example.newstart.repository.prescription

import android.util.Log
import com.example.newstart.BuildConfig
import com.example.newstart.network.ApiClient
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class ApiPrescriptionDecisionProvider(
    private val gson: Gson = Gson()
) : PrescriptionDecisionProvider {

    override val providerId: String = "cloud_api_prescription"

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    override suspend fun generate(request: PrescriptionDecisionRequest): PrescriptionDecisionPayload? = withContext(Dispatchers.IO) {
        val endpoint = buildEndpoint() ?: return@withContext null
        val bodyJson = gson.toJson(request)
        val requestBuilder = Request.Builder()
            .url(endpoint)
            .post(bodyJson.toRequestBody(jsonMediaType))
            .addHeader("Content-Type", "application/json")
        ApiClient.getAuthSession()?.token
            ?.takeIf { it.isNotBlank() }
            ?.let { token ->
                requestBuilder.addHeader("Authorization", "Bearer $token")
            }
        val httpRequest = requestBuilder.build()

        runCatching {
            client.newCall(httpRequest).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Log.w(TAG, "Prescription API failed: HTTP_${response.code}")
                    return@use null
                }
                parsePayload(body)
            }
        }.onFailure {
            Log.w(TAG, "Prescription API error: ${it.message}")
        }.getOrNull()
    }

    private fun buildEndpoint(): String? {
        val baseUrl = BuildConfig.API_BASE_URL.trim()
        if (baseUrl.isEmpty()) {
            return null
        }
        return "${baseUrl.trimEnd('/')}/api/intervention/daily-prescription"
    }

    private fun parsePayload(raw: String): PrescriptionDecisionPayload? {
        return runCatching {
            val root = JsonParser.parseString(raw).asJsonObject
            val payloadNode = when {
                looksLikePayload(root) -> root
                root.has("data") && root.get("data").isJsonObject -> root.getAsJsonObject("data")
                else -> null
            } ?: return null
            gson.fromJson(payloadNode, PrescriptionDecisionPayload::class.java)
        }.getOrNull()
    }

    private fun looksLikePayload(node: JsonObject): Boolean {
        return node.has("primaryInterventionType") || node.has("primaryGoal")
    }

    companion object {
        private const val TAG = "ApiPrescriptionProvider"
    }
}
