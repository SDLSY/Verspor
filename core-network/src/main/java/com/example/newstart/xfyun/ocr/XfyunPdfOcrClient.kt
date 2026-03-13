package com.example.newstart.xfyun.ocr

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.example.newstart.xfyun.XfyunConfig
import com.example.newstart.xfyun.auth.TsSignaSigner
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class XfyunPdfOcrClient {

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(90, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val START_URL = "https://iocr.xfyun.cn/ocrzdq/v1/pdfOcr/start"
        private const val STATUS_URL = "https://iocr.xfyun.cn/ocrzdq/v1/pdfOcr/status"
        private const val EXPORT_FORMAT = "markdown"
        private const val POLL_INTERVAL_MS = 5_000L
        private const val MAX_POLLS = 24
    }

    suspend fun recognize(
        contentResolver: ContentResolver,
        uri: Uri,
        exportFormat: String = EXPORT_FORMAT
    ): String {
        val credentials = XfyunConfig.ocrCredentials
        require(credentials.appId.isNotBlank() && credentials.apiSecret.isNotBlank()) {
            "讯飞 PDF OCR 凭据未配置"
        }
        val taskNo = startTask(contentResolver, uri, credentials.appId, credentials.apiSecret, exportFormat)
        return pollTaskUntilReady(taskNo, credentials.appId, credentials.apiSecret)
    }

    private fun startTask(
        contentResolver: ContentResolver,
        uri: Uri,
        appId: String,
        apiSecret: String,
        exportFormat: String
    ): String {
        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IOException("无法读取 PDF 文件")
        val fileName = queryDisplayName(contentResolver, uri).ifBlank { "medical_report.pdf" }
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                fileName,
                bytes.toRequestBody("application/pdf".toMediaType())
            )
            .addFormDataPart("exportFormat", exportFormat)
            .build()
        val request = Request.Builder()
            .url(START_URL)
            .headers(buildHeaders(appId, apiSecret))
            .post(body)
            .build()

        return httpClient.newCall(request).execute().use { response ->
            val bodyText = response.body?.string().orEmpty()
            require(response.isSuccessful) { "讯飞 PDF OCR 启动失败: ${response.code} $bodyText" }
            val payload = JSONObject(bodyText)
            require(payload.optBoolean("flag", false) && payload.optInt("code", -1) == 0) {
                "讯飞 PDF OCR 启动异常: ${payload.optString("desc").ifBlank { bodyText }}"
            }
            payload.optJSONObject("data")?.optString("taskNo").orEmpty().also { taskNo ->
                require(taskNo.isNotBlank()) { "讯飞 PDF OCR 未返回任务号" }
            }
        }
    }

    private suspend fun pollTaskUntilReady(taskNo: String, appId: String, apiSecret: String): String {
        repeat(MAX_POLLS) { index ->
            if (index > 0) {
                delay(POLL_INTERVAL_MS)
            }
            val request = Request.Builder()
                .url(
                    "$STATUS_URL?taskNo=$taskNo"
                )
                .headers(buildHeaders(appId, apiSecret))
                .get()
                .build()
            val result = httpClient.newCall(request).execute().use { response ->
                val bodyText = response.body?.string().orEmpty()
                require(response.isSuccessful) { "讯飞 PDF OCR 状态查询失败: ${response.code} $bodyText" }
                JSONObject(bodyText)
            }
            require(result.optBoolean("flag", false) && result.optInt("code", -1) == 0) {
                "讯飞 PDF OCR 状态异常: ${result.optString("desc")}"
            }
            val data = result.optJSONObject("data") ?: return@repeat
            when (data.optString("status")) {
                "FINISH" -> {
                    val mergedText = downloadFrom(data.optString("downUrl"))
                    if (mergedText.isNotBlank()) {
                        return mergedText
                    }
                    val pageText = downloadPageResults(data.optJSONArray("pageList"))
                    if (pageText.isNotBlank()) {
                        return pageText
                    }
                    throw IOException("讯飞 PDF OCR 已完成，但未返回可用内容")
                }

                "ANY_FAILED" -> {
                    val partialText = downloadPageResults(data.optJSONArray("pageList"))
                    if (partialText.isNotBlank()) {
                        return partialText
                    }
                    throw IOException(data.optString("tip").ifBlank { "讯飞 PDF OCR 部分失败" })
                }

                "FAILED", "STOP" -> {
                    throw IOException(data.optString("tip").ifBlank { "讯飞 PDF OCR 任务失败" })
                }
            }
        }
        throw IOException("讯飞 PDF OCR 处理超时，请稍后重试")
    }

    private fun downloadPageResults(pageList: JSONArray?): String {
        if (pageList == null) return ""
        val results = mutableListOf<String>()
        for (index in 0 until pageList.length()) {
            val page = pageList.optJSONObject(index) ?: continue
            val text = downloadFrom(page.optString("downUrl"))
            if (text.isNotBlank()) {
                results += text
            }
        }
        return results.joinToString("\n\n")
    }

    private fun downloadFrom(url: String): String {
        if (url.isBlank()) return ""
        val request = Request.Builder().url(url).get().build()
        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use ""
            response.body?.string().orEmpty().trim()
        }
    }

    private fun buildHeaders(appId: String, apiSecret: String): okhttp3.Headers {
        val ts = (System.currentTimeMillis() / 1000L).toString()
        val signa = TsSignaSigner.signa(appId, apiSecret, ts)
        return okhttp3.Headers.Builder()
            .add("appId", appId)
            .add("timestamp", ts)
            .add("signature", signa)
            .build()
    }

    private fun queryDisplayName(contentResolver: ContentResolver, uri: Uri): String {
        return contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) cursor.getString(index).orEmpty() else ""
                } else {
                    ""
                }
            }
            .orEmpty()
    }
}
