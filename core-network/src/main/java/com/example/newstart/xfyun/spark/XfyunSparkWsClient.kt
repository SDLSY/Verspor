package com.example.newstart.xfyun.spark

import com.example.newstart.xfyun.XfyunSparkEndpoint
import com.example.newstart.xfyun.auth.HmacWsUrlSigner
import kotlinx.coroutines.CompletableDeferred
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.SocketException
import java.util.concurrent.TimeUnit

open class XfyunSparkWsClient(
    private val endpoint: XfyunSparkEndpoint,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
) {

    companion object {
        private const val MAX_ATTEMPTS = 2
    }

    open suspend fun chat(messages: List<SparkChatMessage>, temperature: Double = 0.45): String {
        var lastError: Throwable? = null
        repeat(MAX_ATTEMPTS) { attempt ->
            runCatching {
                return chatOnce(messages, temperature)
            }.onFailure { throwable ->
                lastError = throwable
                if (!throwable.isRetriableTransportError() || attempt == MAX_ATTEMPTS - 1) {
                    throw throwable
                }
            }
        }
        throw lastError ?: IllegalStateException("Xfyun Spark request failed without an error")
    }

    private suspend fun chatOnce(messages: List<SparkChatMessage>, temperature: Double): String {
        val credentials = endpoint.credentials
        require(credentials.isReady) { "Xfyun Spark credentials are not configured" }
        require(messages.isNotEmpty()) { "Spark messages must not be empty" }
        val deferred = CompletableDeferred<String>()
        val answer = StringBuilder()
        val request = Request.Builder()
            .url(HmacWsUrlSigner.sign(endpoint.url, credentials.apiKey, credentials.apiSecret))
            .build()
        client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val payload = JSONObject().apply {
                    put(
                        "header",
                        JSONObject().apply {
                            put("app_id", credentials.appId)
                            put("uid", "vespero-avatar")
                        }
                    )
                    put(
                        "parameter",
                        JSONObject().apply {
                            put(
                                "chat",
                                JSONObject().apply {
                                    put("domain", endpoint.domain)
                                    put("temperature", temperature)
                                    put("max_tokens", 2048)
                                }
                            )
                        }
                    )
                    put(
                        "payload",
                        JSONObject().apply {
                            put(
                                "message",
                                JSONObject().apply {
                                    put(
                                        "text",
                                        JSONArray().apply {
                                            messages.forEach { message ->
                                                put(
                                                    JSONObject().apply {
                                                        put("role", message.role)
                                                        put("content", message.content)
                                                    }
                                                )
                                            }
                                        }
                                    )
                                }
                            )
                        }
                    )
                }
                webSocket.send(payload.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching {
                    val json = JSONObject(text)
                    val header = json.optJSONObject("header")
                    val code = header?.optInt("code", -1) ?: -1
                    require(code == 0) { "Xfyun Spark returned error: $text" }
                    val payload = json.optJSONObject("payload")
                    val choices = payload?.optJSONObject("choices")
                    val textArray = choices?.optJSONArray("text")
                    if (textArray != null) {
                        for (index in 0 until textArray.length()) {
                            answer.append(textArray.getJSONObject(index).optString("content"))
                        }
                    }
                    if (choices?.optInt("status", 1) == 2) {
                        deferred.complete(answer.toString().trim())
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
        return deferred.await()
    }

    private fun Throwable.isRetriableTransportError(): Boolean {
        return this is IOException || this is SocketException || cause is SocketException
    }
}

data class SparkChatMessage(
    val role: String,
    val content: String
)

class XfyunSparkLiteWsClient(endpoint: XfyunSparkEndpoint) : XfyunSparkWsClient(endpoint)

class XfyunSparkXWsClient(
    private val sparkXEndpoint: XfyunSparkEndpoint
) : XfyunSparkWsClient(sparkXEndpoint) {

    override suspend fun chat(messages: List<SparkChatMessage>, temperature: Double): String {
        val domainCandidates = listOf(
            sparkXEndpoint.domain,
            "reasoner-x1",
            "spark-x",
            "x1"
        ).map { it.trim() }.filter { it.isNotBlank() }.distinct()
        var lastError: Throwable? = null
        for (domain in domainCandidates) {
            val candidateEndpoint = sparkXEndpoint.copy(domain = domain)
            try {
                return XfyunSparkWsClient(candidateEndpoint).chat(messages, temperature)
            } catch (t: Throwable) {
                lastError = t
            }
        }
        throw lastError ?: IllegalStateException("Xfyun Spark X request failed without error detail")
    }
}
