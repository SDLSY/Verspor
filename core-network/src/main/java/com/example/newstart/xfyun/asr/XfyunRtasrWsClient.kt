package com.example.newstart.xfyun.asr

import android.util.Log
import com.example.newstart.xfyun.XfyunConfig
import com.example.newstart.xfyun.auth.TsSignaSigner
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString.Companion.toByteString
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.SocketException
import java.util.Collections
import java.util.TreeMap
import java.util.concurrent.TimeUnit

class XfyunRtasrWsClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
) {

    companion object {
        private const val TAG = "XfyunRtasrWsClient"
        private const val BASE_URL = "wss://rtasr.xfyun.cn/v1/ws"
        private const val FRAME_INTERVAL_MS = 40L
        private const val FRAME_BYTES = 1280
        private const val FINAL_RESULT_TIMEOUT_MS = 12_000L
        private const val MAX_ATTEMPTS = 2
    }

    fun open(onText: (String) -> Unit, onError: (Throwable) -> Unit): WebSocket {
        val request = Request.Builder().url(buildSignedUrl()).build()
        return client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "RTASR raw message: $text")
                runCatching {
                    val data = JSONObject(text)
                    if (data.optString("action") == "result") {
                        onText(data.optString("data"))
                    }
                }.onFailure(onError)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "RTASR websocket open() failure code=${response?.code}", t)
                onError(t)
            }
        })
    }

    suspend fun transcribePcm(pcmAudio: ByteArray, pd: String? = null): String = withContext(Dispatchers.IO) {
        require(XfyunConfig.rtasrCredentials.isReady) { "Xfyun RTASR credentials are not configured" }
        require(pcmAudio.isNotEmpty()) { "RTASR PCM input must not be empty" }

        var lastError: Throwable? = null
        repeat(MAX_ATTEMPTS) { attempt ->
            runCatching {
                return@withContext transcribePcmOnce(pcmAudio, pd)
            }.onFailure { throwable ->
                lastError = throwable
                if (!throwable.isRetriableTransportError() || attempt == MAX_ATTEMPTS - 1) {
                    throw throwable
                }
            }
        }
        throw lastError ?: IllegalStateException("Xfyun RTASR failed without an error")
    }

    private suspend fun transcribePcmOnce(pcmAudio: ByteArray, pd: String? = null): String {
        require(XfyunConfig.rtasrCredentials.isReady) { "Xfyun RTASR credentials are not configured" }
        require(pcmAudio.isNotEmpty()) { "RTASR PCM input must not be empty" }

        val deferred = CompletableDeferred<String>()
        val segments = Collections.synchronizedSortedMap(TreeMap<Int, String>())
        val request = Request.Builder().url(buildSignedUrl(pd)).build()

        fun joinedSegments(): String = synchronized(segments) {
            segments.values.joinToString(separator = "").trim()
        }

        client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "RTASR websocket opened")
                Thread {
                    runCatching {
                        var offset = 0
                        var frameCount = 0
                        while (offset < pcmAudio.size) {
                            val endIndex = minOf(offset + FRAME_BYTES, pcmAudio.size)
                            val chunk = pcmAudio.copyOfRange(offset, endIndex)
                            webSocket.send(chunk.toByteString())
                            offset = endIndex
                            frameCount++
                            if (offset < pcmAudio.size) {
                                Thread.sleep(FRAME_INTERVAL_MS)
                            }
                        }
                        Log.d(TAG, "RTASR audio upload finished, frames=$frameCount bytes=${pcmAudio.size}")
                        webSocket.send("{\"end\": true}".toByteArray().toByteString())
                        Log.d(TAG, "RTASR end marker sent")

                        Thread {
                            Thread.sleep(FINAL_RESULT_TIMEOUT_MS)
                            if (deferred.isCompleted) return@Thread
                            val partial = joinedSegments()
                            if (partial.isNotBlank()) {
                                Log.w(TAG, "RTASR timed out waiting for close; returning partial result=$partial")
                                deferred.complete(partial)
                                webSocket.cancel()
                            }
                        }.start()
                    }.onFailure {
                        Log.e(TAG, "RTASR upload failed", it)
                        if (!deferred.isCompleted) {
                            deferred.completeExceptionally(it)
                        }
                        webSocket.cancel()
                    }
                }.start()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "RTASR message: $text")
                runCatching {
                    val root = JSONObject(text)
                    when (root.optString("action")) {
                        "error" -> throw IllegalStateException("Xfyun RTASR returned error: $text")
                        "result" -> {
                            val resultData = root.optString("data")
                            val (segmentId, segmentText) = parseSegment(resultData)
                            if (segmentText.isNotBlank()) {
                                segments[segmentId] = segmentText
                            }
                            if (root.optBoolean("isEnd", false)) {
                                if (!deferred.isCompleted) {
                                    deferred.complete(joinedSegments())
                                }
                                webSocket.close(1000, "result-complete")
                            }
                        }
                    }
                }.onFailure {
                    Log.e(TAG, "RTASR parse failed", it)
                    if (!deferred.isCompleted) {
                        deferred.completeExceptionally(it)
                    }
                    webSocket.cancel()
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "RTASR websocket closed code=$code reason=$reason segments=${segments.size}")
                if (!deferred.isCompleted) {
                    deferred.complete(joinedSegments())
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "RTASR websocket failure code=${response?.code}", t)
                if (!deferred.isCompleted) {
                    deferred.completeExceptionally(t)
                }
            }
        })
        return deferred.await()
    }

    private fun buildSignedUrl(pd: String? = null): String {
        val credentials = XfyunConfig.rtasrCredentials
        require(credentials.isReady) { "Xfyun RTASR credentials are not configured" }
        val ts = (System.currentTimeMillis() / 1000).toString()
        val signa = TsSignaSigner.signa(credentials.appId, credentials.apiKey, ts)
        val builder = BASE_URL
            .replaceFirst("wss://", "https://")
            .replaceFirst("ws://", "http://")
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("appid", credentials.appId)
            .addQueryParameter("ts", ts)
            .addQueryParameter("signa", signa)
        pd?.takeIf { it.isNotBlank() }?.let { builder.addQueryParameter("pd", it) }
        return builder.build().toString().replaceFirst("https://", "wss://")
    }

    private fun Throwable.isRetriableTransportError(): Boolean {
        return this is IOException || this is SocketException || cause is SocketException
    }
}

private fun parseSegment(raw: String): Pair<Int, String> {
    if (raw.isBlank()) return -1 to ""
    val root = JSONObject(raw)
    val segmentId = root.optInt("seg_id", -1)
    val text = StringBuilder()
    val rtArray = root.optJSONObject("cn")
        ?.optJSONObject("st")
        ?.optJSONArray("rt")
        ?: JSONArray()
    for (rtIndex in 0 until rtArray.length()) {
        val wsArray = rtArray.optJSONObject(rtIndex)?.optJSONArray("ws") ?: continue
        for (wsIndex in 0 until wsArray.length()) {
            val cwArray = wsArray.optJSONObject(wsIndex)?.optJSONArray("cw") ?: continue
            if (cwArray.length() > 0) {
                text.append(cwArray.optJSONObject(0)?.optString("w").orEmpty())
            }
        }
    }
    return segmentId to text.toString().trim()
}
