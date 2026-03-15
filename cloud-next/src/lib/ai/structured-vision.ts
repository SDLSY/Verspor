import { createTraceId } from "@/lib/http";
import { getProviderApiKey, resolveChatCompletionsUrl } from "@/lib/ai/config";

export const STRUCTURED_VISION_PROVIDER_ID = "vector_engine" as const;
export const STRUCTURED_VISION_MODEL_ID = "gemini-3.1-flash-lite-preview";

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

export async function generateStructuredVision(
  input: StructuredVisionGenerationInput
): Promise<StructuredVisionGenerationResult | null> {
  const traceId = input.traceId ?? createTraceId();
  const apiKey = getProviderApiKey(STRUCTURED_VISION_PROVIDER_ID);
  if (!apiKey) {
    console.warn("[AI] structured vision missing vector engine api key", { traceId });
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

  try {
    const response = await fetch(resolveChatCompletionsUrl(STRUCTURED_VISION_PROVIDER_ID), {
      method: "POST",
      headers: buildHeaders(apiKey),
      body: JSON.stringify({
        model: STRUCTURED_VISION_MODEL_ID,
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
        status: response.status,
        body: body.slice(0, 600),
        traceId,
      });
      return null;
    }

    const payload = (await response.json()) as ChatCompletionResponse;
    const content = normalizeMessageContent(payload.choices?.[0]?.message?.content);
    if (!content) {
      console.warn("[AI] structured vision empty content", { traceId });
      return null;
    }

    return {
      providerId: STRUCTURED_VISION_PROVIDER_ID,
      modelId: STRUCTURED_VISION_MODEL_ID,
      content,
      traceId,
    };
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
