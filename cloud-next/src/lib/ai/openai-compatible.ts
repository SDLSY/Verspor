import { createTraceId } from "@/lib/http";
import {
  getAiProviderOrder,
  getProviderApiKey,
  isProviderEnabled,
  resolveChatCompletionsUrl,
  resolveLogicalModelId,
} from "@/lib/ai/config";
import type { AiCapability, AiLogicalModelId, AiProviderId } from "@/lib/ai/types";

type ChatMessage = {
  role: "system" | "user" | "assistant";
  content: string;
};

export type StructuredTextGenerationInput = {
  capability: AiCapability;
  logicalModelId: AiLogicalModelId;
  messages: ChatMessage[];
  providerOrder?: AiProviderId[];
  modelOverrideByProvider?: Partial<Record<AiProviderId, string>>;
  temperature?: number;
  maxTokens?: number;
  responseFormat?: "json_object";
  timeoutMs?: number;
  traceId?: string;
};

export type StructuredTextGenerationResult = {
  providerId: AiProviderId;
  logicalModelId: AiLogicalModelId;
  modelId: string;
  content: string;
  traceId: string;
  fallbackUsed: boolean;
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

function buildHeaders(providerId: AiProviderId, apiKey: string): Record<string, string> {
  const headers: Record<string, string> = {
    Authorization: `Bearer ${apiKey}`,
    "Content-Type": "application/json",
  };

  if (providerId === "vector_engine" || providerId === "openrouter") {
    headers["HTTP-Referer"] = process.env.APP_ACCEPTANCE_BASE_URL ?? "https://newstart.local";
    headers["X-Title"] = "NewStart AI Runtime";
  }

  return headers;
}

export async function generateStructuredText(
  input: StructuredTextGenerationInput
): Promise<StructuredTextGenerationResult | null> {
  const traceId = input.traceId ?? createTraceId();
  const providerOrder = (input.providerOrder?.length ? input.providerOrder : getAiProviderOrder())
    .filter((providerId, index, list) => list.indexOf(providerId) === index);

  for (const providerId of providerOrder) {
    if (!isProviderEnabled(providerId)) {
      continue;
    }
    const apiKey = getProviderApiKey(providerId);
    if (!apiKey) {
      continue;
    }
    const modelId =
      input.modelOverrideByProvider?.[providerId] ??
      resolveLogicalModelId(providerId, input.logicalModelId);
    if (!modelId) {
      continue;
    }

    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), input.timeoutMs ?? 15000);

    try {
      const response = await fetch(resolveChatCompletionsUrl(providerId), {
        method: "POST",
        headers: buildHeaders(providerId, apiKey),
        body: JSON.stringify({
          model: modelId,
          messages: input.messages,
          temperature: input.temperature ?? 0.2,
          max_tokens: input.maxTokens ?? 1200,
          ...(input.responseFormat === "json_object"
            ? { response_format: { type: "json_object" } }
            : {}),
        }),
        signal: controller.signal,
      });

      if (!response.ok) {
        const body = await response.text();
        console.warn("[AI] provider non-200", {
          providerId,
          capability: input.capability,
          status: response.status,
          body: body.slice(0, 600),
          traceId,
        });
        continue;
      }

      const payload = (await response.json()) as ChatCompletionResponse;
      const content = normalizeMessageContent(payload.choices?.[0]?.message?.content);
      if (!content) {
        console.warn("[AI] provider empty content", {
          providerId,
          capability: input.capability,
          traceId,
        });
        continue;
      }

      return {
        providerId,
        logicalModelId: input.logicalModelId,
        modelId,
        content,
        traceId,
        fallbackUsed: providerId !== providerOrder[0],
      };
    } catch (error) {
      console.warn("[AI] provider request failed", {
        providerId,
        capability: input.capability,
        error: error instanceof Error ? error.message : String(error),
        traceId,
      });
    } finally {
      clearTimeout(timeout);
    }
  }

  return null;
}
