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

function buildUserPrompt(input: ReportUnderstandingRequest): string {
  const markdownBlock = input.ocrMarkdown.trim()
    ? `\nOCR markdown：\n${input.ocrMarkdown}\n`
    : "";

  return `
报告类型：${input.reportType}

OCR 文本：
${input.ocrText}
${markdownBlock}

请输出固定 JSON：{
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
`.trim();
}

export async function generateReportUnderstanding(
  input: ReportUnderstandingRequest,
  traceId: string
): Promise<{ payload: ReportUnderstanding; providerId: string; fallbackUsed: boolean } | null> {
  const systemPrompt = `
你是医疗报告理解助手，负责把 OCR 提取出的体检或检验文本整理成用户能直接看懂的结构化结果。
你的职责有三项：
1. 提取可识别的指标、参考范围和异常标记。
2. 给出风险分层和一句话摘要。
3. 把 OCR 文本或 OCR markdown 整理成适合人阅读的报告说明，便于用户核对，而不是直接复述原始 OCR 文本。

约束：
- 不输出明确诊断，不给药物和治疗方案。
- 只基于 OCR 内容中能支持的内容回答，不编造缺失指标。
- readableReport 必须是简体中文、多行文本，可直接展示给用户。
- readableReport 建议按以下结构组织：
  报告结论：
  关键指标：
  需要关注：
- 如果没有识别出有效指标，也要给出简洁说明，提示用户重新拍摄或补充更清晰的报告。
- riskLevel 只能是 LOW、MEDIUM、HIGH。
- confidence 必须是 0 到 1 之间的小数。
- 输出必须是严格 JSON。`.trim();

  const result = await generateStructuredText({
    capability: "StructuredText",
    logicalModelId: "text.structured",
    providerOrder: resolveReportProviderOrder(),
    modelOverrideByProvider: resolveReportModelOverrides(),
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
    return null;
  }

  const jsonText = extractJsonObject(result.content) ?? result.content;
  const parsed = ReportUnderstandingSchema.safeParse(JSON.parse(jsonText));
  if (!parsed.success) {
    console.warn("[AI][ReportUnderstanding] schema validation failed", {
      issues: parsed.error.issues,
      traceId,
      providerId: result.providerId,
    });
    return null;
  }

  return {
    payload: parsed.data,
    providerId: result.providerId,
    fallbackUsed: result.fallbackUsed,
  };
}
