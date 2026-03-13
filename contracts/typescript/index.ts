export type ApiEnvelope<T> = {
  code: number;
  message: string;
  data?: T;
  traceId: string;
};

export type InterventionTask = {
  taskId: string;
  sourceType: "AI_COACH" | "MEDICAL_REPORT" | "RULE_ENGINE";
  bodyZone: string;
  protocolType: string;
  durationSec: number;
};

export type InterventionExecution = {
  executionId: string;
  taskId: string;
  elapsedSec: number;
  effectScore: number | null;
};

export type InterventionEffect = {
  date: number;
  avgEffectScore: number;
  executionCount: number;
};

export type AiProviderId = "vector_engine" | "openrouter" | "deepseek";

export type AiCapability =
  | "TextReasoning"
  | "StructuredText"
  | "Retrieval"
  | "VisionOCR"
  | "VisionUnderstanding"
  | "SpeechASR"
  | "SpeechTTS"
  | "ImageGeneration"
  | "VideoGeneration";

export type AiLogicalModelId =
  | "text.fast"
  | "text.structured"
  | "text.long_context"
  | "retrieval.embed"
  | "retrieval.rerank"
  | "vision.ocr"
  | "vision.reasoning"
  | "speech.asr"
  | "speech.tts"
  | "image.generate"
  | "video.generate.async";

export type AiExecutionMode = "LOCAL" | "CLOUD" | "FALLBACK";

export type AiJobStatus = "queued" | "running" | "succeeded" | "failed";
