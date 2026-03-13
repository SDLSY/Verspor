import { NextResponse } from "next/server";
import { z } from "zod";
import { generateStructuredText } from "@/lib/ai/openai-compatible";
import { fail, ok, parseJsonBody, createTraceId } from "@/lib/http";
import { extractJsonObject } from "@/lib/prescription/providers/base";

const AvatarNarrationRequestSchema = z.object({
  pageKey: z.string().trim().min(1).max(48).default("unknown"),
  pageTitle: z.string().trim().min(1).max(80),
  pageSubtitle: z.string().trim().max(120).default(""),
  visibleHighlights: z.array(z.string().trim().min(1).max(80)).max(8).default([]),
  userStateSummary: z.string().trim().max(240).default(""),
  riskSummary: z.string().trim().max(160).default(""),
  actionHint: z.string().trim().max(200).default(""),
  trigger: z.enum(["enter", "tap", "button", "replay"]).default("enter"),
});

const AvatarNarrationPayloadSchema = z.object({
  text: z.string().trim().min(1).max(140),
  semanticAction: z.enum(["wave", "point", "alert", "encourage", "listen"]).default("wave"),
});

type AvatarNarrationRequest = z.infer<typeof AvatarNarrationRequestSchema>;
type AvatarNarrationPayload = z.infer<typeof AvatarNarrationPayloadSchema>;

type GeneratedNarration = AvatarNarrationPayload & {
  providerId: string;
  modelId: string;
};

export async function POST(req: Request) {
  const traceId = createTraceId();

  let json: unknown;
  try {
    json = await req.json();
  } catch {
    return NextResponse.json(fail(400, "invalid json payload", traceId), { status: 400 });
  }

  const parsed = AvatarNarrationRequestSchema.safeParse(parseJsonBody(json));
  if (!parsed.success) {
    return NextResponse.json(fail(400, "invalid avatar narration payload", traceId), {
      status: 400,
    });
  }

  const fallback = buildFallbackNarration(parsed.data);
  const generated = await generateAvatarNarration(parsed.data, traceId);
  const payload = generated ?? fallback;

  return NextResponse.json(
    ok("ok", traceId, {
      text: payload.text,
      semanticAction: payload.semanticAction,
      source: generated ? "cloud_ai" : "cloud_fallback",
      modelLabel: generated ? formatModelLabel(generated.providerId, generated.modelId) : "Cloud fallback",
    })
  );
}

async function generateAvatarNarration(
  input: AvatarNarrationRequest,
  traceId: string
): Promise<GeneratedNarration | null> {
  const highlights = input.visibleHighlights.length > 0
    ? input.visibleHighlights.map((item) => `- ${item}`).join("\n")
    : "- 暂无额外页面要点";

  const result = await generateStructuredText({
    capability: "StructuredText",
    logicalModelId: "text.structured",
    responseFormat: "json_object",
    temperature: 0.2,
    maxTokens: 220,
    traceId,
    messages: [
      {
        role: "system",
        content: [
          "你是长庚环 App 的桌面导航机器人文案引擎。",
          "你的任务是基于当前页面上下文，生成适合语音播报和气泡展示的短提示。",
          "请使用简体中文，2 到 3 句，总长度不超过 110 个汉字。",
          "如果存在风险或异常提示，要先提醒风险，再说明下一步。",
          "如果风险摘要包含“暂无”“未见”“无明显”等否定词，不要把它写成风险。",
          "你服务的是健康管理 App，不是运维平台，不要提事件列表、工单、告警面板或后台。",
          "只能使用页面标题、可见要点、用户状态、风险摘要和建议动作，不要引入未提供的对象。",
          "不要做诊断，不要夸大结论，不要输出 Markdown。",
          "必须输出严格 JSON：{\"text\":\"...\",\"semanticAction\":\"wave|point|alert|encourage|listen\"}。",
        ].join(" "),
      },
      {
        role: "user",
        content: [
          `页面键：${input.pageKey}`,
          `页面标题：${input.pageTitle}`,
          `页面副标题：${input.pageSubtitle || "无"}`,
          `触发方式：${input.trigger}`,
          `用户状态：${input.userStateSummary || "暂无额外状态"}`,
          `风险摘要：${input.riskSummary || "暂无明确风险提示"}`,
          `建议动作：${input.actionHint || "解释页面重点并提示下一步"}`,
          "可见要点：",
          highlights,
        ].join("\n"),
      },
    ],
  });

  if (!result) {
    return null;
  }

  try {
    const jsonText = extractJsonObject(result.content) ?? result.content;
    const parsed = AvatarNarrationPayloadSchema.safeParse(JSON.parse(jsonText));
    if (!parsed.success) {
      return null;
    }
    return {
      text: sanitizeNarration(parsed.data.text),
      semanticAction: parsed.data.semanticAction,
      providerId: result.providerId,
      modelId: result.modelId,
    };
  } catch {
    return null;
  }
}

function buildFallbackNarration(input: AvatarNarrationRequest): AvatarNarrationPayload {
  const primaryHighlight = input.visibleHighlights.find((item) => item.length <= 24) ?? "";
  const trimmedRisk = sanitizeNarration(input.riskSummary);
  const actionSentence = summarizeActionHint(input.actionHint, input.pageKey);
  const text = (() => {
    if (hasActionableRisk(trimmedRisk)) {
      return `这里先关注${trimmedRisk}。建议你先完成当前页面最关键的一步。`;
    }
    if (primaryHighlight.length > 0) {
      return `这里是${input.pageTitle}，当前重点是${primaryHighlight}。${actionSentence}`;
    }
    if (input.pageSubtitle.length > 0) {
      return `这里是${input.pageTitle}。${sanitizeNarration(input.pageSubtitle)}。${actionSentence}`;
    }
    return `这里是${input.pageTitle}。${actionSentence}`;
  })();

  return {
    text: sanitizeNarration(text),
    semanticAction: inferSemanticAction(input, text),
  };
}

function summarizeActionHint(actionHint: string, pageKey: string): string {
  const cleanHint = sanitizeNarration(actionHint);
  if (cleanHint.length > 0) {
    const normalizedHint = cleanHint.replace(/^提醒用户/, "").replace(/。/g, "").trim();
    return normalizedHint.startsWith("先")
      ? `建议${normalizedHint}。`
      : `建议先${normalizedHint}。`;
  }

  switch (pageKey) {
    case "home":
      return "建议先看今日重点，再决定下一步。";
    case "doctor":
      return "建议先把当前不适说清楚。";
    case "trend":
      return "建议先看趋势结论，再决定是否干预。";
    case "device":
      return "建议先确认连接和同步状态。";
    case "profile":
      return "建议先处理账户和设置。";
    case "intervention_center":
      return "建议先从最贴近当前问题的干预入口开始。";
    case "relax_center":
      return "建议先选中不适部位，再查看动作和建议。";
    case "symptom_guide":
      return "建议先完成症状定位和补充信息。";
    case "breathing_coach":
      return "建议先跟随当前节奏稳定呼吸。";
    case "medical_report":
      return "建议先看可读摘要，再校对 OCR 文本。";
    case "assessment_baseline":
      return "建议先完成关键评估，再生成方案。";
    case "intervention_profile":
      return "建议先看清方案结构，再决定是否开始。";
    case "intervention_session":
      return "建议先跟随当前步骤执行，再反馈体感变化。";
    default:
      return "建议先完成当前页面最关键的一步。";
  }
}

function inferSemanticAction(input: AvatarNarrationRequest, text: string): AvatarNarrationPayload["semanticAction"] {
  const lowered = text.toLowerCase();
  if (hasActionableRisk(input.riskSummary) || text.includes("风险") || text.includes("异常") || lowered.includes("alert")) {
    return "alert";
  }
  if (["tap", "button", "replay"].includes(input.trigger)) {
    return "listen";
  }
  if (text.includes("点击") || text.includes("进入") || text.includes("打开")) {
    return "point";
  }
  if (text.includes("建议") || text.includes("继续") || text.includes("先")) {
    return "encourage";
  }
  return "wave";
}

function sanitizeNarration(text: string): string {
  return text
    .replace(/```/g, "")
    .replace(/[#*]/g, "")
    .replace(/\s+/g, " ")
    .replace(/[“”‘’"'`]/g, "")
    .trim()
    .slice(0, 140);
}

function hasActionableRisk(value: string): boolean {
  const normalized = sanitizeNarration(value);
  if (!normalized) {
    return false;
  }
  const nonRiskMarkers = ["暂无", "未见", "无明显", "未提示", "稳定", "正常"];
  if (nonRiskMarkers.some((marker) => normalized.includes(marker))) {
    return false;
  }
  return ["风险", "预警", "异常", "红旗", "高危", "警示"].some((marker) => normalized.includes(marker));
}

function formatModelLabel(providerId: string, modelId: string): string {
  const providerLabel = (() => {
    switch (providerId) {
      case "openrouter":
        return "OpenRouter";
      case "vector_engine":
        return "Vector Engine";
      case "deepseek":
        return "DeepSeek";
      default:
        return providerId;
    }
  })();
  return `${providerLabel} · ${modelId}`;
}
