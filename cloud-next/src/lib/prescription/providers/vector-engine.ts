import { readAiEnv, resolveChatCompletionsUrl, resolveLogicalModelId } from "@/lib/ai/config";
import type { PrescriptionModelProvider } from "@/lib/prescription/providers/base";
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

const REQUEST_TIMEOUT_MS = 15000;

function buildPrompt(input: PrescriptionProviderInput): string {
  const catalogSummary = input.request.catalog
    .map((item) => `${item.protocolCode}: ${item.displayName}（${item.interventionType}） ${item.description}`)
    .join("\n");

  return [
    "你是健康干预处方引擎，只能在给定协议目录中选方案。",
    "禁止输出诊断、药物、检查处方；只允许输出健康管理建议。",
    "riskLevel 必须使用枚举：LOW、MEDIUM、HIGH。",
    "timing 必须使用枚举：MORNING、AFTERNOON、EVENING、BEFORE_SLEEP、FLEXIBLE。",
    "durationSec 必须使用秒数，例如 900 代表 15 分钟。",
    "primaryInterventionType 和 secondaryInterventionType 必须填写 protocolCode，不能填写 ROUTINE、AUDIO、TASK、BREATHING 这类 interventionType。",
    "lifestyleTaskCodes 只能填写 TASK_ 开头的 protocolCode。",
    "targetDomains 只能填写以下键：sleepDisturbance, stressLoad, fatigueLoad, recoveryCapacity, anxietyRisk, depressiveRisk, adherenceReadiness。",
    "evidence 和 contraindications 必须是字符串数组，不允许是单个字符串。",
    "如果存在红旗或高风险，必须在 lifestyleTaskCodes 中加入 TASK_DOCTOR_PRIORITY。",
    "输出必须是严格 JSON，不要 Markdown，不要解释，不要代码块。",
    "字段固定为：primaryGoal,riskLevel,targetDomains,primaryInterventionType,secondaryInterventionType,lifestyleTaskCodes,timing,durationSec,rationale,evidence,contraindications,followupMetric。",
    "参考格式：{\"primaryGoal\":\"降低睡前唤醒\",\"riskLevel\":\"MEDIUM\",\"targetDomains\":[\"sleepDisturbance\",\"stressLoad\"],\"primaryInterventionType\":\"SLEEP_WIND_DOWN_15M\",\"secondaryInterventionType\":\"BODY_SCAN_NSDR_10M\",\"lifestyleTaskCodes\":[\"TASK_SCREEN_CURFEW\",\"TASK_CAFFEINE_CUTOFF\"],\"timing\":\"BEFORE_SLEEP\",\"durationSec\":900,\"rationale\":\"睡眠扰动和压力同时偏高，适合先做睡前减刺激再接身体扫描。\",\"evidence\":[\"近7天平均睡眠不足\",\"PSS-10 偏高\"],\"contraindications\":[\"若出现胸痛或呼吸困难，应优先就医\"],\"followupMetric\":\"入睡时长和睡前紧张度\"}",
    "协议目录如下：",
    catalogSummary,
    "输入上下文如下：",
    JSON.stringify(
      {
        triggerType: input.request.triggerType,
        domainScores: input.request.domainScores,
        evidenceFacts: input.request.evidenceFacts,
        redFlags: input.request.redFlags,
        ragContext: input.ragContext,
      },
      null,
      2
    ),
  ].join("\n\n");
}

export class VectorEnginePrescriptionProvider implements PrescriptionModelProvider {
  readonly providerId = "vector_engine";

  isEnabled(): boolean {
    return Boolean(readAiEnv("VECTOR_ENGINE_API_KEY"));
  }

  async generate(input: PrescriptionProviderInput): Promise<DailyPrescriptionResponse | null> {
    const apiKey = readAiEnv("VECTOR_ENGINE_API_KEY");
    if (!apiKey) {
      return null;
    }

    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);

    try {
      const response = await fetch(
        resolveChatCompletionsUrl("vector_engine"),
        {
          method: "POST",
          headers: {
            Authorization: `Bearer ${apiKey}`,
            "Content-Type": "application/json",
            "HTTP-Referer": process.env.APP_ACCEPTANCE_BASE_URL ?? "https://newstart.local",
            "X-Title": "NewStart Prescription Engine",
          },
          body: JSON.stringify({
            model: resolveLogicalModelId("vector_engine", "text.structured"),
            response_format: { type: "json_object" },
            temperature: 0.2,
            max_tokens: 800,
            messages: [
              {
                role: "system",
                content:
                  "你是严谨的健康干预处方服务。你只能从给定目录中选择协议，并返回严格 JSON。",
              },
              {
                role: "user",
                content: buildPrompt(input),
              },
            ],
          }),
          signal: controller.signal,
        }
      );

      if (!response.ok) {
        const body = await response.text();
        console.warn("[Prescription][VectorEngine] non-200 response", {
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
        console.warn("[Prescription][VectorEngine] empty or non-json content", {
          rawContent: rawContent.slice(0, 800),
          traceId: input.traceId,
        });
        return null;
      }

      const parsed = DailyPrescriptionResponseSchema.safeParse(
        normalizeDecisionPayload(JSON.parse(jsonText), input)
      );
      if (!parsed.success) {
        console.warn("[Prescription][VectorEngine] schema validation failed", {
          issues: parsed.error.issues,
          jsonText: jsonText.slice(0, 800),
          traceId: input.traceId,
        });
        return null;
      }

      return parsed.data;
    } catch (error) {
      console.warn("[Prescription][VectorEngine] request failed", {
        error: error instanceof Error ? error.message : String(error),
        traceId: input.traceId,
      });
      return null;
    } finally {
      clearTimeout(timeout);
    }
  }
}
