package com.example.newstart.xfyun.speech

import android.util.Base64
import com.example.newstart.xfyun.XfyunConfig
import com.example.newstart.xfyun.auth.HmacWsUrlSigner
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.net.SocketException
import java.util.concurrent.TimeUnit

class XfyunTtsWsClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
) {

    companion object {
        private const val TTS_URL = "wss://tts-api.xfyun.cn/v2/tts"
        private const val REQUEST_TIMEOUT_MS = 45_000L
        private const val MAX_ATTEMPTS = 2
    }

    suspend fun synthesize(text: String, voiceName: String = XfyunConfig.defaultVoiceName): String {
        val audioBytes = synthesizeBytes(
            text = text,
            voiceName = voiceName,
            audioEncoding = "lame",
            streamMp3 = true
        )
        return "data:audio/mpeg;base64," + Base64.encodeToString(audioBytes, Base64.NO_WRAP)
    }

    suspend fun synthesizePcm(text: String, voiceName: String = XfyunConfig.defaultVoiceName): ByteArray {
        return synthesizeBytes(
            text = text,
            voiceName = voiceName,
            audioEncoding = "raw",
            streamMp3 = false
        )
    }

    private suspend fun synthesizeBytes(
        text: String,
        voiceName: String,
        audioEncoding: String,
        streamMp3: Boolean
    ): ByteArray {
        var lastError: Throwable? = null
        repeat(MAX_ATTEMPTS) { attempt ->
            runCatching {
                return synthesizeBytesOnce(text, voiceName, audioEncoding, streamMp3)
            }.onFailure { throwable ->
                lastError = throwable
                if (!throwable.isRetriableTransportError() || attempt == MAX_ATTEMPTS - 1) {
                    throw throwable
                }
            }
        }
        throw lastError ?: IllegalStateException("Xfyun TTS failed without an error")
    }

    private suspend fun synthesizeBytesOnce(
        text: String,
        voiceName: String,
        audioEncoding: String,
        streamMp3: Boolean
    ): ByteArray {
        val credentials = XfyunConfig.ttsCredentials
        require(credentials.isReady) { "Xfyun TTS credentials are not configured" }
        require(text.isNotBlank()) { "TTS text must not be blank" }

        val deferred = CompletableDeferred<ByteArray>()
        val audioStream = ByteArrayOutputStream()
        val request = Request.Builder()
            .url(HmacWsUrlSigner.sign(TTS_URL, credentials.apiKey, credentials.apiSecret))
            .build()
        lateinit var webSocket: WebSocket
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val payload = JSONObject().apply {
                    put(
                        "common",
                        JSONObject().apply {
                            put("app_id", credentials.appId)
                        }
                    )
                    put(
                        "business",
                        JSONObject().apply {
                            put("aue", audioEncoding)
                            if (streamMp3) {
                                put("sfl", 1)
                            }
                            put("auf", "audio/L16;rate=16000")
                            put("vcn", voiceName)
                            put("tte", "utf8")
                            put("speed", 55)
                            put("pitch", 50)
                            put("volume", 60)
                        }
                    )
                    put(
                        "data",
                        JSONObject().apply {
                            put(
                                "text",
                                Base64.encodeToString(
                                    text.toByteArray(StandardCharsets.UTF_8),
                                    Base64.NO_WRAP
                                )
                            )
                            put("status", 2)
                        }
                    )
                }
                webSocket.send(payload.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching {
                    val json = JSONObject(text)
                    val code = json.optInt("code", -1)
                    require(code == 0) { "Xfyun TTS returned error: $text" }
                    val data = json.optJSONObject("data") ?: return@runCatching
                    val audio = data.optString("audio")
                    if (audio.isNotBlank()) {
                        audioStream.write(Base64.decode(audio, Base64.DEFAULT))
                    }
                    if (data.optInt("status", 1) == 2) {
                        deferred.complete(audioStream.toByteArray())
                        webSocket.close(1000, "done")
                    }
                }.onFailure {
                    if (!deferred.isCompleted) {
                        deferred.completeExceptionally(it)
                    }
                    webSocket.cancel()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!deferred.isCompleted) {
                    deferred.completeExceptionally(t)
                }
            }
        })
        return withTimeout(REQUEST_TIMEOUT_MS) {
            deferred.await()
        }
    }

    private fun Throwable.isRetriableTransportError(): Boolean {
        return this is IOException || this is SocketException || cause is SocketException
    }
}
