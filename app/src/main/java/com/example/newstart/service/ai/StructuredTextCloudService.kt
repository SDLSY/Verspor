package com.example.newstart.service.ai

import com.example.newstart.ai.OpenRouterDoctorApiClient

data class StructuredTextResult(
    val text: String?,
    val error: String?
)

object StructuredTextCloudService {

    fun isAvailable(): Boolean = OpenRouterDoctorApiClient.hasApiKey()

    fun generateStructuredDoctorTurn(
        conversationBlock: String,
        contextBlock: String,
        ragContext: String,
        stage: String,
        followUpCount: Int
    ): StructuredTextResult {
        val result = OpenRouterDoctorApiClient.generateStructuredDoctorTurn(
            conversationBlock = conversationBlock,
            contextBlock = contextBlock,
            ragContext = ragContext,
            stage = stage,
            followUpCount = followUpCount
        )
        return StructuredTextResult(text = result.text, error = result.error)
    }

    fun generateCompletion(
        systemPrompt: String,
        userPrompt: String,
        temperature: Double,
        maxTokens: Int
    ): StructuredTextResult {
        val result = OpenRouterDoctorApiClient.generateCompletion(
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
            temperature = temperature,
            maxTokens = maxTokens
        )
        return StructuredTextResult(text = result.text, error = result.error)
    }
}
