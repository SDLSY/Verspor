import { z } from "zod";
import { extractJsonObject } from "@/lib/prescription/providers/base";
import { generateStructuredText } from "@/lib/ai/openai-compatible";
import type { AiProviderId } from "@/lib/ai/types";

export const ReportUnderstandingRequestSchema = z.object({
  reportType: z.string().trim().max(64).default("PHOTO"),
  ocrText: z.string().trim().min(1).max(12000),
  ocrMarkdown: z.string().trim().max(40000).optional().default(""),
});

export const ReportMetricSchema = z.object({
  metricCode: z.string().trim().min(1).max(32),
  metricName: z.string().trim().min(1).max(120),
  metricValue: z.number(),
  unit: z.string().trim().max(32).default(""),
  refLow: z.number().nullable().default(null),
  refHigh: z.number().nullable().default(null),
  isAbnormal: z.boolean().default(false),
  confidence: z.number().min(0).max(1).default(0.85),
});

export const ReportUnderstandingSchema = z.object({
  reportType: z.string().trim().default("PHOTO"),
  riskLevel: z.enum(["LOW", "MEDIUM", "HIGH"]).default("LOW"),
  abnormalCount: z.number().int().min(0).default(0),
  readableReport: z.string().trim().default(""),
  metrics: z.array(ReportMetricSchema).max(20).default([]),
  summary: z.string().trim().default(""),
});

export type ReportUnderstandingRequest = z.infer<typeof ReportUnderstandingRequestSchema>;
export type ReportUnderstanding = z.infer<typeof ReportUnderstandingSchema>;

type JsonObject = Record<string, unknown>;

function readNonEmptyEnv(name: string): string | null {
  const value = process.env[name]?.trim();
  return value ? value : null;
}

function resolveReportProviderOrder(): AiProviderId[] {
  const configured = [
    readNonEmptyEnv("REPORT_UNDERSTAND_PROVIDER_PRIMARY") ?? "openrouter",
    readNonEmptyEnv("REPORT_UNDERSTAND_PROVIDER_SECONDARY") ?? "vector_engine",
    readNonEmptyEnv("REPORT_UNDERSTAND_PROVIDER_TERTIARY") ?? "deepseek",
  ];
  const allowed: AiProviderId[] = ["openrouter", "vector_engine", "deepseek"];
  const seen = new Set<AiProviderId>();
  return configured
    .filter((item): item is AiProviderId => allowed.includes(item as AiProviderId))
    .filter((providerId) => {
      if (seen.has(providerId)) {
        return false;
      }
      seen.add(providerId);
      return true;
    });
}

function resolveReportModelOverrides(): Partial<Record<AiProviderId, string>> {
  return {
    openrouter:
      readNonEmptyEnv("REPORT_UNDERSTAND_OPENROUTER_MODEL") ?? "google/gemini-2.5-flash",
    vector_engine:
      readNonEmptyEnv("REPORT_UNDERSTAND_VECTOR_ENGINE_MODEL") ?? "gpt-4.1-mini",
    deepseek:
      readNonEmptyEnv("REPORT_UNDERSTAND_DEEPSEEK_MODEL") ?? "deepseek-v3.2",
  };
}

function toObject(value: unknown): JsonObject {
  if (typeof value !== "object" || value == null || Array.isArray(value)) {
    return {};
  }
  return value as JsonObject;
}

function toStringValue(value: unknown, maxLength: number, fallback = ""): string {
  const normalized =
    typeof value === "string"
      ? value.trim()
      : typeof value === "number" || typeof value === "boolean"
        ? String(value)
        : "";
  return normalized.slice(0, maxLength) || fallback;
}

function toBooleanValue(value: unknown): boolean {
  if (typeof value === "boolean") {
    return value;
  }
  if (typeof value === "string") {
    return value.trim().toLowerCase() === "true";
  }
  return false;
}

function toNullableNumberValue(value: unknown): number | null {
  if (value == null || value === "") {
    return null;
  }
  if (typeof value === "number") {
    return Number.isFinite(value) ? value : null;
  }
  if (typeof value === "string") {
    const matched = value.replace(/,/g, "").match(/-?\d+(?:\.\d+)?/);
    if (!matched) {
      return null;
    }
    const parsed = Number(matched[0]);
    return Number.isFinite(parsed) ? parsed : null;
  }
  return null;
}

function toBoundedNumberValue(
  value: unknown,
  min: number,
  max: number,
  fallback: number
): number {
  const parsed = toNullableNumberValue(value);
  if (parsed == null) {
    return fallback;
  }
  return Math.min(max, Math.max(min, parsed));
}

function toRiskLevel(value: unknown, fallback = "LOW"): "LOW" | "MEDIUM" | "HIGH" {
  const normalized = toStringValue(value, 32, fallback).toUpperCase();
  if (normalized === "HIGH" || normalized === "MEDIUM" || normalized === "LOW") {
    return normalized;
  }
  return fallback as "LOW" | "MEDIUM" | "HIGH";
}

function normalizeMetricPayload(value: unknown) {
  const payload = toObject(value);
  const metricCode = toStringValue(payload.metricCode, 32);
  const metricName = toStringValue(payload.metricName, 120, metricCode);
  const metricValue = toNullableNumberValue(payload.metricValue);
  if (!metricCode || !metricName || metricValue == null) {
    return null;
  }

  const refLow = toNullableNumberValue(payload.refLow);
  const refHigh = toNullableNumberValue(payload.refHigh);
  const isAbnormal =
    toBooleanValue(payload.isAbnormal) ||
    (refLow != null && metricValue < refLow) ||
    (refHigh != null && metricValue > refHigh);

  return {
    metricCode,
    metricName,
    metricValue,
    unit: toStringValue(payload.unit, 32),
    refLow,
    refHigh,
    isAbnormal,
    confidence: toBoundedNumberValue(payload.confidence, 0, 1, 0.85),
  };
}

function buildFallbackSummary(
  riskLevel: "LOW" | "MEDIUM" | "HIGH",
  abnormalCount: number
): string {
  if (abnormalCount <= 0) {
    return "已完成报告整理，当前未识别到明确异常项，仍建议结合原始报告继续核对。";
  }
  if (riskLevel === "HIGH") {
    return `已整理出 ${abnormalCount} 项偏离指标，建议尽快结合原始报告进行线下评估。`;
  }
  return `已整理出 ${abnormalCount} 项需要关注的指标，建议结合原始报告继续核对。`;
}

function buildFallbackReadableReport(
  riskLevel: "LOW" | "MEDIUM" | "HIGH",
  abnormalCount: number,
  summary: string,
  metrics: Array<ReturnType<typeof normalizeMetricPayload>>
): string {
  const validMetrics = metrics.filter(
    (metric): metric is NonNullable<ReturnType<typeof normalizeMetricPayload>> => metric != null
  );
  const lines = [
    `报告结论：${summary}`,
    `风险等级：${riskLevel}`,
    `异常项：${abnormalCount} 项`,
  ];
  if (validMetrics.length > 0) {
    lines.push("关键指标：");
    validMetrics.slice(0, 5).forEach((metric, index) => {
      lines.push(
        `${index + 1}. ${metric.metricName} ${metric.metricValue}${metric.unit ? ` ${metric.unit}` : ""}${
          metric.isAbnormal ? "（偏离参考范围）" : ""
        }`
      );
    });
  }
  return lines.join("\n").trim();
}

function normalizeReportUnderstandingPayload(
  input: unknown,
  request: ReportUnderstandingRequest
): ReportUnderstanding {
  const payload = toObject(input);
  const metrics = Array.isArray(payload.metrics)
    ? payload.metrics
        .map((item) => normalizeMetricPayload(item))
        .filter((item): item is NonNullable<ReturnType<typeof normalizeMetricPayload>> => item != null)
        .slice(0, 20)
    : [];
  const inferredAbnormalCount = metrics.filter((metric) => metric.isAbnormal).length;
  const abnormalCount = Math.max(
    0,
    Math.round(toBoundedNumberValue(payload.abnormalCount, 0, 99, inferredAbnormalCount))
  );
  const riskLevel = toRiskLevel(
    payload.riskLevel,
    inferredAbnormalCount >= 3 ? "HIGH" : inferredAbnormalCount >= 1 ? "MEDIUM" : "LOW"
  );
  const summary = toStringValue(payload.summary, 1200, buildFallbackSummary(riskLevel, abnormalCount));
  const readableReport = toStringValue(
    payload.readableReport,
    4000,
    buildFallbackReadableReport(riskLevel, abnormalCount, summary, metrics)
  );

  return {
    reportType: toStringValue(payload.reportType, 64, request.reportType),
    riskLevel,
    abnormalCount,
    readableReport,
    summary,
    metrics,
  };
}

function buildUserPrompt(input: ReportUnderstandingRequest): string {
  const markdownBlock = input.ocrMarkdown.trim()
    ? `\nOCR markdown:\n${input.ocrMarkdown}\n`
    : "";

  return `
Report type: ${input.reportType}

OCR text:
${input.ocrText}
${markdownBlock}

Return strict JSON only with this shape:
{
  "reportType":"string",
  "riskLevel":"LOW|MEDIUM|HIGH",
  "abnormalCount":0,
  "readableReport":"string",
  "summary":"string",
  "metrics":[
    {
      "metricCode":"string",
      "metricName":"string",
      "metricValue":0,
      "unit":"string",
      "refLow":null,
      "refHigh":null,
      "isAbnormal":false,
      "confidence":0.85
    }
  ]
}
Numeric fields must be JSON numbers, not quoted strings.
String fields must never be null.
All narrative text must be concise Simplified Chinese.
  `.trim();
}

export async function generateReportUnderstanding(
  input: ReportUnderstandingRequest,
  traceId: string
): Promise<{ payload: ReportUnderstanding; providerId: string; fallbackUsed: boolean } | null> {
  const providerOrder = resolveReportProviderOrder();
  const modelOverrideByProvider = resolveReportModelOverrides();
  const systemPrompt = `
You are a medical report understanding assistant.
You convert OCR text from lab reports or physical exam reports into a user-readable structured summary.
Your job:
1. Extract identifiable metrics, reference ranges, and abnormal markers.
2. Produce a concise risk level and summary.
3. Produce a readable Simplified Chinese report that the user can understand directly.

Constraints:
- Do not give a definitive diagnosis.
- Do not prescribe drugs or treatment plans.
- Only use information supported by the OCR text or OCR markdown.
- readableReport must be concise Simplified Chinese and suitable for direct display.
- If no stable metrics can be extracted, still explain that the report needs clearer OCR or manual review.
- riskLevel must be LOW, MEDIUM, or HIGH.
- confidence values must be decimals between 0 and 1.
- Output must be strict JSON only.
  `.trim();

  for (const providerId of providerOrder) {
    const result = await generateStructuredText({
      capability: "StructuredText",
      logicalModelId: "text.structured",
      providerOrder: [providerId],
      modelOverrideByProvider,
      responseFormat: "json_object",
      maxTokens: 2200,
      temperature: 0.1,
      traceId,
      messages: [
        { role: "system", content: systemPrompt },
        { role: "user", content: buildUserPrompt(input) },
      ],
    });

    if (!result) {
      continue;
    }

    let parsedJson: unknown;
    try {
      const jsonText = extractJsonObject(result.content) ?? result.content;
      parsedJson = JSON.parse(jsonText);
    } catch (error) {
      console.warn("[AI][ReportUnderstanding] invalid json", {
        traceId,
        providerId: result.providerId,
        error: error instanceof Error ? error.message : String(error),
      });
      continue;
    }

    const normalized = normalizeReportUnderstandingPayload(parsedJson, input);
    const parsed = ReportUnderstandingSchema.safeParse(normalized);
    if (!parsed.success) {
      console.warn("[AI][ReportUnderstanding] schema validation failed", {
        issues: parsed.error.issues,
        traceId,
        providerId: result.providerId,
      });
      continue;
    }

    return {
      payload: parsed.data,
      providerId: result.providerId,
      fallbackUsed: providerId !== providerOrder[0],
    };
  }

  return null;
}
