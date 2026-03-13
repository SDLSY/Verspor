package com.example.newstart.xfyun.speech

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Base64
import com.example.newstart.xfyun.XfyunConfig
import com.example.newstart.xfyun.auth.HmacWsUrlSigner
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.net.SocketException
import java.util.concurrent.TimeUnit

class XfyunIatWsClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
) {

    companion object {
        private const val IAT_URL = "wss://iat-api.xfyun.cn/v2/iat"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val FRAME_INTERVAL_MS = 40L
        private const val MAX_RECORD_MS = 12000L
        private const val MAX_IDLE_MS = 5000L
        private const val MAX_SILENCE_AFTER_SPEECH_MS = 1100L
        private const val SPEECH_THRESHOLD = 900
        private const val FRAME_BYTES = SAMPLE_RATE * 2 * FRAME_INTERVAL_MS.toInt() / 1000
        private const val MAX_ATTEMPTS = 2
    }

    @Volatile
    private var shouldStopCurrentRecognition = false

    @Volatile
    private var currentAudioRecord: AudioRecord? = null

    @Volatile
    private var currentWebSocket: WebSocket? = null

    fun cancelCurrentRecognition() {
        shouldStopCurrentRecognition = true
        runCatching { currentAudioRecord?.stop() }
        runCatching { currentAudioRecord?.release() }
        currentAudioRecord = null
        currentWebSocket?.cancel()
        currentWebSocket = null
    }

    suspend fun recognizeOnce(hotWords: List<String> = emptyList()): String = withContext(Dispatchers.IO) {
        val credentials = XfyunConfig.iatCredentials
        require(credentials.isReady) { "Xfyun IAT credentials are not configured" }
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        require(minBuffer > 0) { "Unable to initialize AudioRecord for IAT" }
        shouldStopCurrentRecognition = false
        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            maxOf(minBuffer, 4096)
        )
        currentAudioRecord = audioRecord
        recognizeInternal(hotWords) { sendFrame, sendEnd ->
            val audioBuffer = ByteArray(FRAME_BYTES)
            audioRecord.startRecording()
            var speechDetected = false
            var lastSpeechAt = android.os.SystemClock.elapsedRealtime()
            val startedAt = lastSpeechAt
            try {
                while (!shouldStopCurrentRecognition && android.os.SystemClock.elapsedRealtime() - startedAt < MAX_RECORD_MS) {
                    val read = audioRecord.read(audioBuffer, 0, audioBuffer.size)
                    if (read <= 0) continue
                    val frame = audioBuffer.copyOf(read)
                    val amplitude = frame.maxAmplitude()
                    val now = android.os.SystemClock.elapsedRealtime()
                    if (amplitude >= SPEECH_THRESHOLD) {
                        speechDetected = true
                        lastSpeechAt = now
                    }
                    sendFrame(frame)
                    if (!speechDetected && now - startedAt >= MAX_IDLE_MS) {
                        break
                    }
                    if (speechDetected && now - lastSpeechAt >= MAX_SILENCE_AFTER_SPEECH_MS) {
                        break
                    }
                }
            } finally {
                runCatching {
                    audioRecord.stop()
                    audioRecord.release()
                }
                currentAudioRecord = null
                sendEnd()
            }
        }
    }

    suspend fun recognizePcm(pcmAudio: ByteArray, hotWords: List<String> = emptyList()): String = withContext(Dispatchers.IO) {
        require(pcmAudio.isNotEmpty()) { "IAT PCM input must not be empty" }
        shouldStopCurrentRecognition = false
        recognizeInternal(hotWords) { sendFrame, sendEnd ->
            var offset = 0
            while (!shouldStopCurrentRecognition && offset < pcmAudio.size) {
                val endIndex = minOf(offset + FRAME_BYTES, pcmAudio.size)
                sendFrame(pcmAudio.copyOfRange(offset, endIndex))
                offset = endIndex
                if (offset < pcmAudio.size) {
                    Thread.sleep(FRAME_INTERVAL_MS)
                }
            }
            sendEnd()
        }
    }

    private suspend fun recognizeInternal(
        hotWords: List<String>,
        audioProducer: (sendFrame: (ByteArray) -> Unit, sendEnd: () -> Unit) -> Unit
    ): String {
        var lastError: Throwable? = null
        repeat(MAX_ATTEMPTS) { attempt ->
            runCatching {
                return recognizeInternalOnce(hotWords, audioProducer)
            }.onFailure { throwable ->
                lastError = throwable
                if (!throwable.isRetriableTransportError() || attempt == MAX_ATTEMPTS - 1) {
                    throw throwable
                }
            }
        }
        throw lastError ?: IllegalStateException("Xfyun IAT failed without an error")
    }

    private suspend fun recognizeInternalOnce(
        hotWords: List<String>,
        audioProducer: (sendFrame: (ByteArray) -> Unit, sendEnd: () -> Unit) -> Unit
    ): String {
        val credentials = XfyunConfig.iatCredentials
        require(credentials.isReady) { "Xfyun IAT credentials are not configured" }
        val deferred = CompletableDeferred<String>()
        val resultBuilder = StringBuilder()
        val signedUrl = HmacWsUrlSigner.sign(IAT_URL, credentials.apiKey, credentials.apiSecret)
        val request = Request.Builder().url(signedUrl).build()
        lateinit var webSocket: WebSocket
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val business = buildBusinessPayload(hotWords)
                runCatching {
                    var isFirstFrame = true
                    audioProducer(
                        { frame ->
                            webSocket.send(buildAudioFrameJson(credentials.appId, business, frame, if (isFirstFrame) 0 else 1))
                            isFirstFrame = false
                        },
                        {
                            webSocket.send(buildEndFrameJson())
                        }
                    )
                }.onFailure {
                    if (!deferred.isCompleted) {
                        deferred.completeExceptionally(it)
                    }
                    webSocket.cancel()
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching {
                    val json = JSONObject(text)
                    val code = json.optInt("code", -1)
                    require(code == 0) { "Xfyun IAT returned error: $text" }
                    val data = json.optJSONObject("data") ?: return@runCatching
                    val result = data.optJSONObject("result") ?: return@runCatching
                    resultBuilder.append(parseWords(result.optJSONArray("ws")))
                    if (data.optInt("status", 1) == 2) {
                        deferred.complete(resultBuilder.toString().trim())
                        currentWebSocket = null
                        webSocket.close(1000, "done")
                    }
                }.onFailure {
                    if (!deferred.isCompleted) {
                        deferred.completeExceptionally(it)
                    }
                    currentWebSocket = null
                    webSocket.cancel()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                currentWebSocket = null
                if (!deferred.isCompleted) {
                    deferred.completeExceptionally(t)
                }
            }
        })
        currentWebSocket = webSocket
        return deferred.await()
    }

    private fun buildBusinessPayload(hotWords: List<String>): JSONObject {
        return JSONObject().apply {
            put("language", "zh_cn")
            put("domain", "iat")
            put("accent", "mandarin")
            put("vad_eos", 1000)
            put("dwa", "none")
        }
    }

    private fun buildAudioFrameJson(
        appId: String,
        business: JSONObject,
        frame: ByteArray,
        status: Int
    ): String {
        return JSONObject().apply {
            if (status == 0) {
                put(
                    "common",
                    JSONObject().apply {
                        put("app_id", appId)
                    }
                )
                put("business", business)
            }
            put(
                "data",
                JSONObject().apply {
                    put("status", status)
                    put("format", "audio/L16;rate=16000")
                    put("audio", Base64.encodeToString(frame, Base64.NO_WRAP))
                    put("encoding", "raw")
                }
            )
        }.toString()
    }

    private fun buildEndFrameJson(): String {
        return JSONObject().apply {
            put(
                "data",
                JSONObject().apply {
                    put("status", 2)
                    put("format", "audio/L16;rate=16000")
                    put("audio", "")
                    put("encoding", "raw")
                }
            )
        }.toString()
    }

    private fun Throwable.isRetriableTransportError(): Boolean {
        return this is IOException || this is SocketException || cause is SocketException
    }
}

private fun parseWords(wsArray: JSONArray?): String {
    if (wsArray == null) return ""
    val builder = StringBuilder()
    for (i in 0 until wsArray.length()) {
        val ws = wsArray.optJSONObject(i) ?: continue
        val cw = ws.optJSONArray("cw") ?: continue
        if (cw.length() > 0) {
            builder.append(cw.optJSONObject(0)?.optString("w").orEmpty())
        }
    }
    return builder.toString()
}

private fun ByteArray.maxAmplitude(): Int {
    val buffer = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN)
    var max = 0
    while (buffer.remaining() >= 2) {
        val amplitude = kotlin.math.abs(buffer.short.toInt())
        if (amplitude > max) {
            max = amplitude
        }
    }
    return max
}
