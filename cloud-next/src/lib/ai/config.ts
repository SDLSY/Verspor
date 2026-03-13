import type { AiLogicalModelId, AiProviderId } from "@/lib/ai/types";

const DEFAULT_CHAT_COMPLETIONS_URL: Record<AiProviderId, string> = {
  vector_engine: "https://api.vectorengine.ai/v1/chat/completions",
  openrouter: "https://openrouter.ai/api/v1/chat/completions",
  deepseek: "https://api.deepseek.com/chat/completions",
};

export function readAiEnv(name: string): string | null {
  const value = process.env[name]?.trim();
  return value ? value : null;
}

export function isProviderEnabled(providerId: AiProviderId): boolean {
  switch (providerId) {
    case "vector_engine":
      return Boolean(readAiEnv("VECTOR_ENGINE_API_KEY"));
    case "openrouter":
      return Boolean(readAiEnv("OPENROUTER_API_KEY"));
    case "deepseek":
      return Boolean(readAiEnv("DEEPSEEK_API_KEY"));
  }
}

export function getProviderApiKey(providerId: AiProviderId): string | null {
  switch (providerId) {
    case "vector_engine":
      return readAiEnv("VECTOR_ENGINE_API_KEY");
    case "openrouter":
      return readAiEnv("OPENROUTER_API_KEY");
    case "deepseek":
      return readAiEnv("DEEPSEEK_API_KEY");
  }
}

export function getAiProviderOrder(): AiProviderId[] {
  const order = [
    (readAiEnv("PRESCRIPTION_PROVIDER_PRIMARY") ?? "openrouter").toLowerCase(),
    (readAiEnv("PRESCRIPTION_PROVIDER_SECONDARY") ?? "vector_engine").toLowerCase(),
    (readAiEnv("PRESCRIPTION_PROVIDER_TERTIARY") ?? "deepseek").toLowerCase(),
  ];

  const allowed: AiProviderId[] = ["vector_engine", "openrouter", "deepseek"];
  const seen = new Set<AiProviderId>();

  return order
    .filter((item): item is AiProviderId => allowed.includes(item as AiProviderId))
    .filter((providerId) => {
      if (seen.has(providerId)) {
        return false;
      }
      seen.add(providerId);
      return true;
    });
}

export function resolveChatCompletionsUrl(providerId: AiProviderId): string {
  switch (providerId) {
    case "vector_engine":
      return readAiEnv("VECTOR_ENGINE_CHAT_COMPLETIONS_URL") ?? DEFAULT_CHAT_COMPLETIONS_URL.vector_engine;
    case "openrouter":
      return readAiEnv("OPENROUTER_CHAT_COMPLETIONS_URL") ?? DEFAULT_CHAT_COMPLETIONS_URL.openrouter;
    case "deepseek":
      return readAiEnv("DEEPSEEK_CHAT_COMPLETIONS_URL") ?? DEFAULT_CHAT_COMPLETIONS_URL.deepseek;
  }
}

export function resolveLogicalModelId(
  providerId: AiProviderId,
  logicalModelId: AiLogicalModelId
): string | null {
  const providerScoped = (() => {
    switch (providerId) {
      case "vector_engine":
        switch (logicalModelId) {
          case "text.fast":
            return readAiEnv("VECTOR_ENGINE_TEXT_FAST_MODEL");
          case "text.structured":
            return readAiEnv("VECTOR_ENGINE_TEXT_STRUCTURED_MODEL");
          case "text.long_context":
            return readAiEnv("VECTOR_ENGINE_TEXT_LONG_CONTEXT_MODEL");
          case "retrieval.embed":
            return readAiEnv("VECTOR_ENGINE_RETRIEVAL_EMBED_MODEL");
          case "retrieval.rerank":
            return readAiEnv("VECTOR_ENGINE_RETRIEVAL_RERANK_MODEL");
          case "vision.ocr":
            return readAiEnv("VECTOR_ENGINE_VISION_OCR_MODEL");
          case "vision.reasoning":
            return readAiEnv("VECTOR_ENGINE_VISION_REASONING_MODEL");
          case "speech.asr":
            return readAiEnv("VECTOR_ENGINE_SPEECH_ASR_MODEL");
          case "speech.tts":
            return readAiEnv("VECTOR_ENGINE_SPEECH_TTS_MODEL");
          case "image.generate":
            return readAiEnv("VECTOR_ENGINE_IMAGE_GENERATION_MODEL");
          case "video.generate.async":
            return readAiEnv("VECTOR_ENGINE_VIDEO_GENERATION_MODEL");
        }
      case "openrouter":
        return logicalModelId === "text.fast" ||
          logicalModelId === "text.structured" ||
          logicalModelId === "text.long_context"
          ? readAiEnv("OPENROUTER_MODEL")
          : null;
      case "deepseek":
        return logicalModelId === "text.fast" ||
          logicalModelId === "text.structured" ||
          logicalModelId === "text.long_context"
          ? readAiEnv("DEEPSEEK_MODEL")
          : null;
    }
  })();

  if (providerScoped) {
    return providerScoped;
  }

  if (providerId === "openrouter") {
    if (
      logicalModelId === "text.fast" ||
      logicalModelId === "text.structured" ||
      logicalModelId === "text.long_context"
    ) {
      return "google/gemini-2.5-flash";
    }
  }

  if (providerId === "vector_engine") {
    if (
      logicalModelId === "text.fast" ||
      logicalModelId === "text.structured" ||
      logicalModelId === "text.long_context"
    ) {
      return "gpt-4.1-mini";
    }
  }

  if (providerId === "deepseek") {
    if (
      logicalModelId === "text.fast" ||
      logicalModelId === "text.structured" ||
      logicalModelId === "text.long_context"
    ) {
      return "deepseek-v3.2";
    }
  }

  return null;
}
