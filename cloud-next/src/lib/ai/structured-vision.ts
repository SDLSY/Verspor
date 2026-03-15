import { createTraceId } from "@/lib/http";
import {
  getProviderApiKey,
  readAiEnv,
  resolveChatCompletionsUrl,
  resolveLogicalModelId,
} from "@/lib/ai/config";

export const STRUCTURED_VISION_PROVIDER_ID = "vector_engine" as const;
export const STRUCTURED_VISION_MODEL_ID = "qwen3-vl-235b-a22b-instruct";
const STRUCTURED_VISION_API_KEY_ENV_NAMES = [
  "VECTOR_ENGINE_STRUCTURED_VISION_API_KEY",
  "VECTOR_ENGINE_GEMINI_API_KEY",
] as const;

type VisionMessage = {
  role: "system" | "user";
  content:
    | string
    | Array<
        | { type: "text"; text: string }
        | { type: "image_url"; image_url: { url: string } }
      >;
};

export type StructuredVisionGenerationInput = {
  systemPrompt: string;
  userPrompt: string;
  imageDataUrl: string;
  maxTokens?: number;
  temperature?: number;
  traceId?: string;
};

export type StructuredVisionGenerationResult = {
  providerId: typeof STRUCTURED_VISION_PROVIDER_ID;
  modelId: string;
  content: string;
  traceId: string;
};

type ChatCompletionResponse = {
  choices?: Array<{ message?: { content?: unknown } }>;
};

function normalizeMessageContent(value: unknown): string {
  if (typeof value === "string") {
    return value.trim();
  }
  if (Array.isArray(value)) {
    return value
      .map((item) => {
        if (typeof item === "string") {
          return item;
        }
        if (typeof item === "object" && item !== null && "text" in item) {
          return String((item as { text?: unknown }).text ?? "");
        }
        return "";
      })
      .join("\n")
      .trim();
  }
  return "";
}

function buildHeaders(apiKey: string): Record<string, string> {
  return {
    Authorization: `Bearer ${apiKey}`,
    "Content-Type": "application/json",
    "HTTP-Referer": process.env.APP_ACCEPTANCE_BASE_URL ?? "https://newstart.local",
    "X-Title": "NewStart Vision Runtime",
  };
}

function resolveStructuredVisionPreferredApiKey(): string | null {
  for (const envName of STRUCTURED_VISION_API_KEY_ENV_NAMES) {
    const value = readAiEnv(envName);
    if (value) {
      return value;
    }
  }
  return null;
}

function resolveStructuredVisionChatCompletionsUrl(): string {
  return (
    readAiEnv("VECTOR_ENGINE_STRUCTURED_VISION_CHAT_COMPLETIONS_URL") ??
    resolveChatCompletionsUrl(STRUCTURED_VISION_PROVIDER_ID)
  );
}

function resolveStructuredVisionApiKeyForModel(modelId: string): string | null {
  const preferredApiKey = resolveStructuredVisionPreferredApiKey();
  const sharedApiKey = getProviderApiKey(STRUCTURED_VISION_PROVIDER_ID);
  if (modelId === STRUCTURED_VISION_MODEL_ID) {
    return preferredApiKey ?? sharedApiKey;
  }
  return sharedApiKey ?? preferredApiKey;
}

function resolveStructuredVisionModelCandidates(): string[] {
  return Array.from(
    new Set(
      [
        STRUCTURED_VISION_MODEL_ID,
        resolveLogicalModelId(STRUCTURED_VISION_PROVIDER_ID, "vision.reasoning"),
        resolveLogicalModelId(STRUCTURED_VISION_PROVIDER_ID, "text.structured"),
        resolveLogicalModelId(STRUCTURED_VISION_PROVIDER_ID, "text.fast"),
      ].filter((value): value is string => typeof value === "string" && value.trim().length > 0)
    )
  );
}

export async function generateStructuredVision(
  input: StructuredVisionGenerationInput
): Promise<StructuredVisionGenerationResult | null> {
  const traceId = input.traceId ?? createTraceId();
  const sharedApiKey = getProviderApiKey(STRUCTURED_VISION_PROVIDER_ID);
  const preferredApiKey = resolveStructuredVisionPreferredApiKey();
  if (!sharedApiKey && !preferredApiKey) {
    console.warn("[AI] structured vision missing vector engine api key", {
      traceId,
      acceptedEnvNames: ["VECTOR_ENGINE_API_KEY", ...STRUCTURED_VISION_API_KEY_ENV_NAMES],
    });
    return null;
  }

  const messages: VisionMessage[] = [
    {
      role: "system",
      content: input.systemPrompt,
    },
    {
      role: "user",
      content: [
        { type: "text", text: input.userPrompt },
        { type: "image_url", image_url: { url: input.imageDataUrl } },
      ],
    },
  ];

  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 20_000);
  const modelCandidates = resolveStructuredVisionModelCandidates();

  try {
    for (const modelId of modelCandidates) {
      const apiKey = resolveStructuredVisionApiKeyForModel(modelId);
      if (!apiKey) {
        continue;
      }
      const response = await fetch(resolveStructuredVisionChatCompletionsUrl(), {
        method: "POST",
        headers: buildHeaders(apiKey),
        body: JSON.stringify({
          model: modelId,
          messages,
          temperature: input.temperature ?? 0.1,
          max_tokens: input.maxTokens ?? 1200,
          response_format: { type: "json_object" },
        }),
        signal: controller.signal,
      });

      if (!response.ok) {
        const body = await response.text();
        console.warn("[AI] structured vision non-200", {
          modelId,
          status: response.status,
          body: body.slice(0, 600),
          traceId,
        });
        if (/No available channels? for model/i.test(body)) {
          continue;
        }
        continue;
      }

      const payload = (await response.json()) as ChatCompletionResponse;
      const content = normalizeMessageContent(payload.choices?.[0]?.message?.content);
      if (!content) {
        console.warn("[AI] structured vision empty content", { modelId, traceId });
        continue;
      }

      return {
        providerId: STRUCTURED_VISION_PROVIDER_ID,
        modelId,
        content,
        traceId,
      };
    }
    return null;
  } catch (error) {
    console.warn("[AI] structured vision request failed", {
      traceId,
      error: error instanceof Error ? error.message : String(error),
    });
    return null;
  } finally {
    clearTimeout(timeout);
  }
}
