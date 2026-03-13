package com.example.newstart.xfyun.asr

import com.example.newstart.xfyun.XfyunConfig
import com.example.newstart.xfyun.auth.TsSignaSigner
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class XfyunRaasrClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(60, TimeUnit.SECONDS)
        .build()
) {

    companion object {
        private const val UPLOAD_URL = "https://raasr.xfyun.cn/v2/api/upload"
        private const val RESULT_URL = "https://raasr.xfyun.cn/v2/api/getResult"
    }

    fun upload(file: File): String {
        val credentials = XfyunConfig.raasrCredentials
        require(credentials.isReady) { "Xfyun RAASR credentials are not configured" }
        val ts = (System.currentTimeMillis() / 1000).toString()
        val signa = TsSignaSigner.signa(credentials.appId, credentials.apiKey, ts)
        val body = file.asRequestBody("application/octet-stream".toMediaType())
        val signedUrl = UPLOAD_URL.toHttpUrl().newBuilder()
            .addQueryParameter("appId", credentials.appId)
            .addQueryParameter("ts", ts)
            .addQueryParameter("signa", signa)
            .addQueryParameter("fileName", file.name)
            .addQueryParameter("fileSize", file.length().toString())
            .addQueryParameter("duration", "60")
            .build()
        val request = Request.Builder()
            .url(signedUrl)
            .post(body)
            .build()
        return client.newCall(request).execute().use { response ->
            val bodyText = response.body?.string().orEmpty()
            require(response.isSuccessful) { "Xfyun RAASR upload failed: ${response.code} $bodyText" }
            val payload = JSONObject(bodyText)
            require(payload.isRaasrSuccess()) { "Xfyun RAASR upload returned business error: $bodyText" }
            val content = payload.opt("content")
            val orderId = when (content) {
                is JSONObject -> content.optString("orderId")
                else -> payload.optString("content")
            }
            require(orderId.isNotBlank()) { "Xfyun RAASR upload returned empty order id: $bodyText" }
            orderId
        }
    }

    fun getResult(orderId: String): String {
        val credentials = XfyunConfig.raasrCredentials
        require(credentials.isReady) { "Xfyun RAASR credentials are not configured" }
        val ts = (System.currentTimeMillis() / 1000).toString()
        val signa = TsSignaSigner.signa(credentials.appId, credentials.apiKey, ts)
        val signedUrl = RESULT_URL.toHttpUrl().newBuilder()
            .addQueryParameter("appId", credentials.appId)
            .addQueryParameter("ts", ts)
            .addQueryParameter("signa", signa)
            .addQueryParameter("orderId", orderId)
            .addQueryParameter("resultType", "transfer")
            .build()
        val request = Request.Builder()
            .url(signedUrl)
            .get()
            .build()
        return client.newCall(request).execute().use { response ->
            val bodyText = response.body?.string().orEmpty()
            require(response.isSuccessful) { "Xfyun RAASR getResult failed: ${response.code} $bodyText" }
            val payload = JSONObject(bodyText)
            require(payload.isRaasrSuccess()) { "Xfyun RAASR getResult returned business error: $bodyText" }
            bodyText
        }
    }
}

private fun JSONObject.isRaasrSuccess(): Boolean {
    val okValue = opt("ok")
    if (okValue != null) {
        return when (okValue) {
            is Number -> okValue.toInt() == 0
            else -> okValue.toString() == "0"
        }
    }
    return optString("code") == "000000"
}
