package com.example.newstart.xfyun.ocr

import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.util.Base64
import com.example.newstart.xfyun.XfyunConfig
import com.example.newstart.xfyun.auth.HmacHttpSigner
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InterruptedIOException
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

class XfyunOcrClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(90, TimeUnit.SECONDS)
        .build()
) {

    companion object {
        private const val OCR_URL = "https://cbm01.cn-huabei-1.xf-yun.com/v1/private/se75ocrbm"
    }

    fun recognize(bitmap: Bitmap, encoding: String = "jpg"): String {
        val credentials = XfyunConfig.ocrCredentials
        require(credentials.isReady) { "讯飞 OCR 凭据未配置" }
        val base64Image = bitmap.toBase64(encoding)
        val bodyJson = JSONObject().apply {
            put(
                "header",
                JSONObject().apply {
                    put("app_id", credentials.appId)
                    put("status", 0)
                }
            )
            put(
                "parameter",
                JSONObject().apply {
                    put(
                        "ocr",
                        JSONObject().apply {
                            put("result_option", "normal")
                            put("result_format", "json")
                            put("output_type", "one_shot")
                            put("exif_option", "0")
                            put("json_element_option", "")
                            put("markdown_element_option", "watermark=0,page_header=0,page_footer=0,page_number=0,graph=0")
                            put("sed_element_option", "watermark=0,page_header=0,page_footer=0,page_number=0,graph=0")
                            put("alpha_option", "0")
                            put("rotation_min_angle", 5)
                            put(
                                "result",
                                JSONObject().apply {
                                    put("encoding", "utf8")
                                    put("compress", "raw")
                                    put("format", "plain")
                                }
                            )
                        }
                    )
                }
            )
            put(
                "payload",
                JSONObject().apply {
                    put(
                        "image",
                        JSONObject().apply {
                            put("encoding", encoding)
                            put("image", base64Image)
                            put("status", 2)
                            put("seq", 0)
                        }
                    )
                }
            )
        }
        val request = Request.Builder()
            .url(HmacHttpSigner.buildSignedUrl("POST", OCR_URL, credentials.apiKey, credentials.apiSecret))
            .post(bodyJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        repeat(2) { attempt ->
            try {
                return httpClient.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    require(response.isSuccessful) { "讯飞 OCR 请求失败: ${response.code} $body" }
                    val payload = JSONObject(body)
                    val code = payload.optJSONObject("header")?.optInt("code", -1) ?: -1
                    require(code == 0) { "讯飞 OCR 返回异常: $body" }
                    val textBase64 = payload.optJSONObject("payload")
                        ?.optJSONObject("result")
                        ?.optString("text")
                        .orEmpty()
                    require(textBase64.isNotBlank()) { "讯飞 OCR 未返回可识别文本" }
                    String(Base64.decode(textBase64, Base64.DEFAULT), StandardCharsets.UTF_8)
                }
            } catch (timeout: InterruptedIOException) {
                if (attempt == 1) throw timeout
            }
        }
        error("OCR 请求连续超时")
    }
}

private fun Bitmap.toBase64(encoding: String): String {
    val format = when (encoding.lowercase()) {
        "png" -> CompressFormat.PNG
        else -> CompressFormat.JPEG
    }
    val quality = if (format == CompressFormat.PNG) 100 else 92
    val stream = ByteArrayOutputStream()
    compress(format, quality, stream)
    return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
}
