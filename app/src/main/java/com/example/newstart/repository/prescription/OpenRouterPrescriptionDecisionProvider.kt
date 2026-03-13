package com.example.newstart.repository.prescription

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.example.newstart.service.ai.StructuredTextCloudService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OpenRouterPrescriptionDecisionProvider(
    private val gson: Gson = Gson()
) : PrescriptionDecisionProvider {

    override val providerId: String = "openrouter_prescription"

    override suspend fun generate(request: PrescriptionDecisionRequest): PrescriptionDecisionPayload? = withContext(Dispatchers.IO) {
        if (!StructuredTextCloudService.isAvailable()) {
            return@withContext null
        }

        val catalogLines = request.catalog.joinToString("\n") {
            "- ${it.protocolCode}: ${it.displayName} / ${it.interventionType} / ${it.description}"
        }
        val evidenceLines = request.evidenceFacts.entries.joinToString("\n") { (key, value) ->
            "$key: ${value.joinToString("；")}"
        }
        val systemPrompt = """
            你是健康干预处方引擎。
            你只能从给定协议代码中选择主干预、辅助干预和生活任务，不能创造新的协议代码。
            你只能做健康管理与恢复建议，不给出明确诊断。
            如果存在红旗，请把医生优先和风险提示放在 rationale 中，并把 TASK_DOCTOR_PRIORITY 放入生活任务。
            必须输出严格 JSON，不要 Markdown，不要解释。

            输出字段固定为：
            {
              "primaryGoal":"string",
              "riskLevel":"LOW|MEDIUM|HIGH",
              "targetDomains":["string"],
              "primaryInterventionType":"protocolCode",
              "secondaryInterventionType":"protocolCode",
              "lifestyleTaskCodes":["protocolCode"],
              "timing":"MORNING|AFTERNOON|EVENING|BEFORE_SLEEP|FLEXIBLE",
              "durationSec":600,
              "rationale":"string",
              "evidence":["string"],
              "contraindications":["string"],
              "followupMetric":"string"
            }
        """.trimIndent()
        val userPrompt = """
            当前触发类型：
            ${request.triggerType}

            当前画像域分数：
            ${gson.toJson(request.domainScores)}

            当前证据：
            $evidenceLines

            红旗：
            ${if (request.redFlags.isEmpty()) "无" else request.redFlags.joinToString("；")}

            可选协议目录：
            $catalogLines

            检索到的处方知识：
            ${request.ragContext}
        """.trimIndent()

        val reply = StructuredTextCloudService.generateCompletion(
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
            temperature = 0.2,
            maxTokens = 900
        )
        val text = reply.text ?: return@withContext null
        runCatching {
            val json = JsonParser.parseString(text).asJsonObject
            gson.fromJson(json, PrescriptionDecisionPayload::class.java)
        }.onFailure {
            Log.w(TAG, "OpenRouter prescription parse failed: ${it.message}")
        }.getOrNull()
    }

    companion object {
        private const val TAG = "OpenRouterPrescription"
    }
}
