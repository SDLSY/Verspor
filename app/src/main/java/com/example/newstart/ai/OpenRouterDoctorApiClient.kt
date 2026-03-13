package com.example.newstart.ai

import android.util.Log
import com.example.newstart.BuildConfig
import com.google.gson.JsonParser
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object OpenRouterDoctorApiClient {

    data class OpenRouterReplyResult(
        val text: String?,
        val error: String?
    )

    private const val TAG = "OpenRouterDoctorApiClient"
    private const val ENDPOINT = "https://openrouter.ai/api/v1/chat/completions"
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    fun hasApiKey(): Boolean = BuildConfig.OPENROUTER_API_KEY.trim().isNotEmpty()

    fun generateDoctorReply(
        question: String,
        contextBlock: String,
        ragContext: String
    ): OpenRouterReplyResult {
        val systemPrompt = """
            You are a digital doctor assistant for sleep, stress, recovery, and exercise pacing.
            Always respond in Simplified Chinese.
            Keep answer concise and actionable:
            1) one-line conclusion
            2) up to 3 concrete actions
            3) one short safety note
            Do not provide definitive diagnosis.
        """.trimIndent()

        val userPrompt = """
            User question:
            $question

            Health context:
            $contextBlock

            Knowledge snippets:
            $ragContext
        """.trimIndent()

        return generateCompletion(
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
            temperature = 0.4,
            maxTokens = 512
        )
    }

    fun generateStructuredDoctorTurn(
        conversationBlock: String,
        contextBlock: String,
        ragContext: String,
        stage: String,
        followUpCount: Int
    ): OpenRouterReplyResult {
        val systemPrompt = """
            你是一名面向睡眠、压力、疲劳、呼吸和运动恢复场景的 AI 问诊引擎。
            你的职责是做初筛、风险分层和就医建议，不做明确诊断，也不提供治疗处方。
            必须使用简体中文，并且只输出严格 JSON，不要输出 Markdown，不要输出额外解释。

            规则如下：
            1. 结合患者自述、健康上下文和知识片段，决定继续追问还是直接生成结构化问诊单。
            2. 每次最多只追问 1 个高价值问题；如果信息已经足够，请直接完成问诊单。
            3. 如果出现胸痛、晕厥、严重呼吸困难、持续高热、意识异常、急性神经系统症状等红旗，必须停止追问并进入 ESCALATED。
            4. suspectedIssues 只允许输出“疑似问题排序”，不得使用明确诊断语气。
            5. confidence 必须是 0-100 的整数。
            6. riskLevel 只允许 LOW、MEDIUM、HIGH。
            7. stage 只允许 CLARIFYING、COMPLETED、ESCALATED。
            8. 如果 stage 为 CLARIFYING，则 followUpQuestion 必填；否则 followUpQuestion 置空。

            输出 JSON 字段固定为：
            {
              "chiefComplaint": "string",
              "symptomFacts": ["string"],
              "missingInfo": ["string"],
              "suspectedIssues": [{"name":"string","rationale":"string","confidence":80}],
              "riskLevel": "LOW|MEDIUM|HIGH",
              "redFlags": ["string"],
              "recommendedDepartment": "string",
              "nextStepAdvice": ["string"],
              "doctorSummary": "string",
              "disclaimer": "string",
              "followUpQuestion": "string",
              "stage": "CLARIFYING|COMPLETED|ESCALATED"
            }
        """.trimIndent()

        val userPrompt = """
            当前阶段：$stage
            已追问轮数：$followUpCount / 4

            患者对话记录：
            $conversationBlock

            健康上下文：
            $contextBlock

            参考知识：
            $ragContext
        """.trimIndent()

        return generateCompletion(
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
            temperature = 0.2,
            maxTokens = 1200
        )
    }

    fun generateCompletion(
        systemPrompt: String,
        userPrompt: String,
        temperature: Double,
        maxTokens: Int
    ): OpenRouterReplyResult {
        val apiKey = BuildConfig.OPENROUTER_API_KEY.trim()
        if (apiKey.isEmpty()) {
            return OpenRouterReplyResult(text = null, error = "API_KEY_MISSING")
        }

        val configuredModel = BuildConfig.OPENROUTER_MODEL.trim().ifEmpty { "google/gemini-2.5-flash" }
        val modelCandidates = listOf(
            configuredModel,
            "google/gemini-2.5-flash",
            "openai/gpt-4.1-mini",
            "deepseek/deepseek-v3.2"
        ).distinct()

        var lastError = "UNKNOWN"
        for (model in modelCandidates) {
            val payload = """
                {
                  "model": ${toJsonString(model)},
                  "messages": [
                    {"role": "system", "content": ${toJsonString(systemPrompt)}},
                    {"role": "user", "content": ${toJsonString(userPrompt)}}
                  ],
                  "temperature": $temperature,
                  "max_tokens": $maxTokens
                }
            """.trimIndent()

            val request = Request.Builder()
                .url(ENDPOINT)
                .post(payload.toRequestBody(jsonMediaType))
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .addHeader("HTTP-Referer", "https://newstart.local")
                .addHeader("X-Title", "NewStart Doctor")
                .build()

            val result = runCatching {
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        "HTTP_${response.code}:${parseErrorMessage(body)}"
                    } else {
                        extractChoiceText(body) ?: "EMPTY_RESPONSE"
                    }
                }
            }.getOrElse {
                "NETWORK_ERROR:${it.message ?: it.javaClass.simpleName}"
            }

            if (!result.startsWith("HTTP_") &&
                !result.startsWith("NETWORK_ERROR:") &&
                result != "EMPTY_RESPONSE"
            ) {
                Log.i(TAG, "OpenRouter model success: $model")
                return OpenRouterReplyResult(text = result, error = null)
            }

            lastError = result
            Log.w(TAG, "OpenRouter model failed: $model, error=$lastError")
        }

        return OpenRouterReplyResult(text = null, error = lastError)
    }

    private fun extractChoiceText(raw: String): String? {
        return runCatching {
            val root = JsonParser.parseString(raw).asJsonObject
            val choices = root.getAsJsonArray("choices") ?: return null
            if (choices.size() == 0) return null
            val message = choices[0].asJsonObject.getAsJsonObject("message") ?: return null
            val content = message.get("content")?.asString.orEmpty().trim()
            content.ifBlank { null }
        }.getOrNull()
    }

    private fun parseErrorMessage(raw: String): String {
        return runCatching {
            val root = JsonParser.parseString(raw).asJsonObject
            root.getAsJsonObject("error")?.get("message")?.asString
                ?: root.get("message")?.asString
                ?: raw.take(200)
        }.getOrDefault(raw.take(200))
    }

    private fun toJsonString(value: String): String {
        val escaped = value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "")
        return "\"$escaped\""
    }
}
