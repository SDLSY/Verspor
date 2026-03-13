package com.example.newstart.contracts

data class ApiEnvelope<T>(
    val code: Int,
    val message: String,
    val data: T?,
    val traceId: String,
)

data class InterventionTaskDto(
    val taskId: String,
    val sourceType: String,
    val bodyZone: String,
    val protocolType: String,
    val durationSec: Int,
)

data class InterventionExecutionDto(
    val executionId: String,
    val taskId: String,
    val elapsedSec: Int,
    val effectScore: Double?,
)

data class InterventionEffectDto(
    val date: Long,
    val avgEffectScore: Double,
    val executionCount: Int,
)

enum class AiProviderId {
    VECTOR_ENGINE,
    OPENROUTER,
    DEEPSEEK,
}

enum class AiCapability {
    TextReasoning,
    StructuredText,
    Retrieval,
    VisionOCR,
    VisionUnderstanding,
    SpeechASR,
    SpeechTTS,
    ImageGeneration,
    VideoGeneration,
}

enum class AiLogicalModelId {
    TEXT_FAST,
    TEXT_STRUCTURED,
    TEXT_LONG_CONTEXT,
    RETRIEVAL_EMBED,
    RETRIEVAL_RERANK,
    VISION_OCR,
    VISION_REASONING,
    SPEECH_ASR,
    SPEECH_TTS,
    IMAGE_GENERATE,
    VIDEO_GENERATE_ASYNC,
}

enum class AiExecutionMode {
    LOCAL,
    CLOUD,
    FALLBACK,
}

enum class AiJobStatus {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
}
