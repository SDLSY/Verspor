export const AI_PROVIDER_IDS = ["vector_engine", "openrouter", "deepseek"] as const;
export type AiProviderId = (typeof AI_PROVIDER_IDS)[number];

export const AI_CAPABILITIES = [
  "TextReasoning",
  "StructuredText",
  "Retrieval",
  "VisionOCR",
  "VisionUnderstanding",
  "SpeechASR",
  "SpeechTTS",
  "ImageGeneration",
  "VideoGeneration",
] as const;
export type AiCapability = (typeof AI_CAPABILITIES)[number];

export const AI_LOGICAL_MODEL_IDS = [
  "text.fast",
  "text.structured",
  "text.long_context",
  "retrieval.embed",
  "retrieval.rerank",
  "vision.ocr",
  "vision.reasoning",
  "speech.asr",
  "speech.tts",
  "image.generate",
  "video.generate.async",
] as const;
export type AiLogicalModelId = (typeof AI_LOGICAL_MODEL_IDS)[number];

export const AI_EXECUTION_MODES = ["LOCAL", "CLOUD", "FALLBACK"] as const;
export type AiExecutionMode = (typeof AI_EXECUTION_MODES)[number];

export const AI_JOB_STATUSES = ["queued", "running", "succeeded", "failed"] as const;
export type AiJobStatus = (typeof AI_JOB_STATUSES)[number];
