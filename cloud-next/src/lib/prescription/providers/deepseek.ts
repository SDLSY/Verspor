import type { PrescriptionModelProvider } from "@/lib/prescription/providers/base";
import { readAiEnv, resolveChatCompletionsUrl, resolveLogicalModelId } from "@/lib/ai/config";
import {
  extractJsonObject,
  normalizeDecisionPayload,
  normalizeMessageContent,
} from "@/lib/prescription/providers/base";
import {
  DailyPrescriptionResponseSchema,
  type DailyPrescriptionResponse,
  type PrescriptionProviderInput,
} from "@/lib/prescription/types";

const REQUEST_TIMEOUT_MS = 12000;

function buildPrompt(input: PrescriptionProviderInput): string {
  return [
    "你是健康干预处方引擎，只能在闭合协议目录中选择干预。",
    "禁止输出诊断结论，禁止输出目录外协议码。",
    "riskLevel 必须使用 LOW、MEDIUM、HIGH。",
    "timing 必须使用 MORNING、AFTERNOON、EVENING、BEFORE_SLEEP、FLEXIBLE。",
    "durationSec 必须是秒数。",
    "primaryInterventionType 和 secondaryInterventionType 必须填写 protocolCode。",
    "lifestyleTaskCodes 只能填写 TASK_ 开头的 protocolCode。",
    "evidence 和 contraindications 必须是字符串数组。",
    "输出必须是严格 JSON。",
    JSON.stringify(
      {
        triggerType: input.request.triggerType,
        domainScores: input.request.domainScores,
        evidenceFacts: input.request.evidenceFacts,
        redFlags: input.request.redFlags,
        ragContext: input.ragContext,
        catalog: input.request.catalog,
      },
      null,
      2
    ),
  ].join("\n\n");
}

export class DeepSeekPrescriptionProvider implements PrescriptionModelProvider {
  readonly providerId = "deepseek";

  isEnabled(): boolean {
    return Boolean(readAiEnv("DEEPSEEK_API_KEY"));
  }

  async generate(input: PrescriptionProviderInput): Promise<DailyPrescriptionResponse | null> {
    const apiKey = readAiEnv("DEEPSEEK_API_KEY");
    if (!apiKey) {
      return null;
    }

    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);

    try {
      const response = await fetch(resolveChatCompletionsUrl("deepseek"), {
        method: "POST",
        headers: {
          Authorization: `Bearer ${apiKey}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          model: resolveLogicalModelId("deepseek", "text.structured"),
          temperature: 0.2,
          response_format: { type: "json_object" },
          messages: [
            {
              role: "system",
              content: "你是严谨的健康干预处方服务，必须返回严格 JSON。",
            },
            {
              role: "user",
              content: buildPrompt(input),
            },
          ],
        }),
        signal: controller.signal,
      });

      if (!response.ok) {
        const body = await response.text();
        console.warn("[Prescription][DeepSeek] non-200 response", {
          status: response.status,
          body: body.slice(0, 800),
          traceId: input.traceId,
        });
        return null;
      }

      const payload = (await response.json()) as {
        choices?: Array<{ message?: { content?: unknown } }>;
      };
      const rawContent = normalizeMessageContent(payload.choices?.[0]?.message?.content);
      const jsonText = extractJsonObject(rawContent);
      if (!jsonText) {
        console.warn("[Prescription][DeepSeek] empty or non-json content", {
          rawContent: rawContent.slice(0, 800),
          traceId: input.traceId,
        });
        return null;
      }

      const parsed = DailyPrescriptionResponseSchema.safeParse(
        normalizeDecisionPayload(JSON.parse(jsonText), input)
      );
      if (!parsed.success) {
        console.warn("[Prescription][DeepSeek] schema validation failed", {
          issues: parsed.error.issues,
          jsonText: jsonText.slice(0, 800),
          traceId: input.traceId,
        });
        return null;
      }

      return parsed.data;
    } catch (error) {
      console.warn("[Prescription][DeepSeek] request failed", {
        error: error instanceof Error ? error.message : String(error),
        traceId: input.traceId,
      });
      return null;
    } finally {
      clearTimeout(timeout);
    }
  }
}
