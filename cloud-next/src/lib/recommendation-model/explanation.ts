import { extractJsonObject } from "@/lib/prescription/providers/base";
import { generateStructuredText } from "@/lib/ai/openai-compatible";
import type { ScientificRecommendationSheet, ScientificTraceType } from "@/lib/recommendation-model/scientific-model";

export type RecommendationExpression = {
  summary: string;
  reasons: string[];
  nextStep: string;
  providerId: string | null;
  modelId: string | null;
  fallbackUsed: boolean;
};

type RecommendationExpressionInput = {
  traceType: ScientificTraceType;
  scientificModel: ScientificRecommendationSheet;
  outputPayload: Record<string, unknown>;
  traceId: string;
};

function truncateText(value: string, maxLength: number): string {
  if (value.length <= maxLength) {
    return value;
  }
  return `${value.slice(0, maxLength - 1)}…`;
}

function stringifyPayload(outputPayload: Record<string, unknown>): string {
  return JSON.stringify(outputPayload, null, 2).slice(0, 6000);
}

function buildFallbackExpression(input: RecommendationExpressionInput): RecommendationExpression {
  const topHypothesis = input.scientificModel.hypotheses[0];
  const topEvidence = input.scientificModel.evidenceLedger.slice(0, 3).map((item) => item.label);
  const reasons =
    topHypothesis?.evidenceLabels.slice(0, 2).length
      ? topHypothesis.evidenceLabels.slice(0, 2)
      : topEvidence;

  const nextStep = (() => {
    const payload = input.outputPayload;
    const nextStepAdvice = payload.nextStepAdvice;
    if (Array.isArray(nextStepAdvice)) {
      const first = nextStepAdvice.find((item) => typeof item === "string" && item.trim().length > 0);
      if (typeof first === "string") {
        return truncateText(first.trim(), 80);
      }
    }
    if (typeof payload.primaryGoal === "string" && payload.primaryGoal.trim().length > 0) {
      return truncateText(`优先围绕“${payload.primaryGoal.trim()}”执行本轮建议。`, 80);
    }
    if (typeof payload.nextFocusDetail === "string" && payload.nextFocusDetail.trim().length > 0) {
      return truncateText(payload.nextFocusDetail.trim(), 80);
    }
    return "先按当前建议执行一轮，再观察效果与风险变化。";
  })();

  return {
    summary: truncateText(input.scientificModel.decisionSummary, 120),
    reasons: reasons.slice(0, 3),
    nextStep,
    providerId: null,
    modelId: null,
    fallbackUsed: true,
  };
}

export async function generateRecommendationExpression(
  input: RecommendationExpressionInput
): Promise<RecommendationExpression> {
  const fallback = buildFallbackExpression(input);
  const result = await generateStructuredText({
    capability: "TextReasoning",
    logicalModelId: "text.fast",
    responseFormat: "json_object",
    maxTokens: 420,
    temperature: 0.15,
    timeoutMs: 12000,
    traceId: input.traceId,
    messages: [
      {
        role: "system",
        content:
          "你是 VesperO 的建议解释层。你的任务是把结构化建议决策翻译成短、准、克制的简体中文说明。" +
          "你不能做诊断，不能夸大结论，不能脱离已给证据扩展。" +
          "请输出严格 JSON，字段固定为 summary、reasons、nextStep。" +
          "summary 不超过 120 字，reasons 最多 3 条，nextStep 不超过 80 字。",
      },
      {
        role: "user",
        content: [
          `traceType: ${input.traceType}`,
          "scientificModel:",
          JSON.stringify(input.scientificModel, null, 2),
          "outputPayload:",
          stringifyPayload(input.outputPayload),
        ].join("\n"),
      },
    ],
  });

  if (!result) {
    return fallback;
  }

  try {
    const jsonText = extractJsonObject(result.content) ?? result.content;
    const parsed = JSON.parse(jsonText) as {
      summary?: unknown;
      reasons?: unknown;
      nextStep?: unknown;
    };

    const summary = typeof parsed.summary === "string" ? truncateText(parsed.summary.trim(), 120) : "";
    const reasons = Array.isArray(parsed.reasons)
      ? parsed.reasons
          .map((item) => (typeof item === "string" ? item.trim() : ""))
          .filter(Boolean)
          .slice(0, 3)
      : [];
    const nextStep = typeof parsed.nextStep === "string" ? truncateText(parsed.nextStep.trim(), 80) : "";

    if (!summary || reasons.length === 0 || !nextStep) {
      return fallback;
    }

    return {
      summary,
      reasons,
      nextStep,
      providerId: result.providerId,
      modelId: result.modelId,
      fallbackUsed: result.fallbackUsed,
    };
  } catch {
    return fallback;
  }
}
