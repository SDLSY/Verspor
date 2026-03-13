import { z } from "zod";
import { extractJsonObject } from "@/lib/prescription/providers/base";
import { generateStructuredText } from "@/lib/ai/openai-compatible";

export const DoctorTurnRequestSchema = z.object({
  conversationBlock: z.string().trim().min(1).max(12000),
  contextBlock: z.string().trim().max(8000).default(""),
  ragContext: z.string().trim().max(8000).default(""),
  stage: z.enum(["INTAKE", "CLARIFYING", "ASSESSING", "COMPLETED", "ESCALATED"]).default("INTAKE"),
  followUpCount: z.number().int().min(0).max(8).default(0),
});

export const DoctorTurnSchema = z.object({
  chiefComplaint: z.string().trim().default(""),
  symptomFacts: z.array(z.string().trim().min(1).max(240)).max(12).default([]),
  missingInfo: z.array(z.string().trim().min(1).max(120)).max(8).default([]),
  suspectedIssues: z.array(
    z.object({
      name: z.string().trim().min(1).max(120),
      rationale: z.string().trim().min(1).max(240),
      confidence: z.number().int().min(0).max(100),
    })
  ).max(6).default([]),
  riskLevel: z.enum(["LOW", "MEDIUM", "HIGH"]).default("MEDIUM"),
  redFlags: z.array(z.string().trim().min(1).max(160)).max(8).default([]),
  recommendedDepartment: z.string().trim().default(""),
  nextStepAdvice: z.array(z.string().trim().min(1).max(240)).max(8).default([]),
  doctorSummary: z.string().trim().default(""),
  disclaimer: z.string().trim().default("本结果仅用于健康初筛与问诊整理，不能替代医生面诊、检查和正式诊断。"),
  followUpQuestion: z.string().trim().default(""),
  stage: z.enum(["CLARIFYING", "COMPLETED", "ESCALATED"]).default("COMPLETED"),
});

export type DoctorTurnRequest = z.infer<typeof DoctorTurnRequestSchema>;
export type DoctorTurn = z.infer<typeof DoctorTurnSchema>;

export async function generateDoctorTurn(
  input: DoctorTurnRequest,
  traceId: string
): Promise<{ payload: DoctorTurn; providerId: string; fallbackUsed: boolean } | null> {
  const systemPrompt = `
你是一名面向睡眠、压力、疲劳、呼吸和运动恢复场景的 AI 问诊引擎。
你的职责是做初筛、风险分层和就医建议，不做明确诊断，也不提供治疗处方。
必须使用简体中文，并且只输出严格 JSON，不要输出 Markdown，不要输出额外解释。

规则如下：
1. 结合患者自述、健康上下文和知识片段，决定继续追问还是直接生成结构化问诊单。
2. 每次最多只追问 1 个高价值问题；如果信息已经足够，请直接完成问诊单。
3. 如果出现胸痛、晕厥、严重呼吸困难、持续高热、意识异常、急性神经系统症状等红旗，必须停止追问并进入 ESCALATED。
4. suspectedIssues 只允许输出“疑似问题排序”，不得使用明确诊断语气。
5. confidence 必须是 0-100 的整数。
6. riskLevel 只允许 LOW、MEDIUM、HIGH。
7. stage 只允许 CLARIFYING、COMPLETED、ESCALATED。
8. 如果 stage 为 CLARIFYING，则 followUpQuestion 必填；否则 followUpQuestion 置空。
`.trim();

  const userPrompt = `
当前阶段：${input.stage}
已追问轮数：${input.followUpCount} / 4

患者对话记录：
${input.conversationBlock}

健康上下文：
${input.contextBlock}

参考知识：
${input.ragContext}
`.trim();

  const result = await generateStructuredText({
    capability: "StructuredText",
    logicalModelId: "text.structured",
    responseFormat: "json_object",
    maxTokens: 1200,
    temperature: 0.2,
    traceId,
    messages: [
      { role: "system", content: systemPrompt },
      { role: "user", content: userPrompt },
    ],
  });

  if (!result) {
    return null;
  }

  const jsonText = extractJsonObject(result.content) ?? result.content;
  const parsed = DoctorTurnSchema.safeParse(JSON.parse(jsonText));
  if (!parsed.success) {
    console.warn("[AI][DoctorTurn] schema validation failed", {
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
