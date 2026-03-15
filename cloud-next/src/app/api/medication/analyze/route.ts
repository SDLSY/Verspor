import { NextResponse } from "next/server";
import { requireAuth } from "@/lib/auth";
import { fail, ok, createTraceId } from "@/lib/http";
import {
  STRUCTURED_VISION_MODEL_ID,
  STRUCTURED_VISION_PROVIDER_ID,
  generateStructuredVision,
} from "@/lib/ai/structured-vision";
import {
  loadLifestyleUserContext,
  normalizeMedicationVisionPayload,
} from "@/lib/lifestyle-analysis";

function buildDataUrl(bytes: Uint8Array, mimeType: string): string {
  return `data:${mimeType};base64,${Buffer.from(bytes).toString("base64")}`;
}

export async function POST(req: Request) {
  const traceId = createTraceId();
  const auth = await requireAuth(req);
  if (!auth.authenticated) {
    return auth.response;
  }

  const formData = await req.formData().catch(() => null);
  const file = formData?.get("file");
  if (!(file instanceof File)) {
    return NextResponse.json(fail(400, "image file is required", traceId), { status: 400 });
  }

  const mimeType = String(formData?.get("mimeType") ?? file.type ?? "image/jpeg").trim() || "image/jpeg";
  const bytes = new Uint8Array(await file.arrayBuffer());
  if (bytes.length === 0) {
    return NextResponse.json(fail(400, "empty image file", traceId), { status: 400 });
  }

  const userContext = await loadLifestyleUserContext(auth.client, auth.user.id);
  const dataUrl = buildDataUrl(bytes, mimeType);

  const systemPrompt = `
你是医疗辅助识别助手。
你只能输出严格 JSON，不要 Markdown，不要解释。
目标：根据用户上传的药物图片，提取药名、剂型、规格、可能成分，并结合用户当前症状与状态给出保守建议。
禁止输出停药、换药、调剂量、替代治疗等具体医疗指令。
如果图片模糊、信息不完整或置信度不足，必须把 requiresManualReview 设为 true。
riskLevel 只能是 LOW、MEDIUM、HIGH。
输出字段固定为：
{
  "recognizedName": "string",
  "dosageForm": "string",
  "specification": "string",
  "activeIngredients": ["string"],
  "matchedSymptoms": ["string"],
  "usageSummary": "string",
  "riskLevel": "LOW|MEDIUM|HIGH",
  "riskFlags": ["string"],
  "evidenceNotes": ["string"],
  "advice": "string",
  "confidence": 0.0,
  "requiresManualReview": false
}
  `.trim();

  const userPrompt = `
请识别这张药物图片。

用户当前症状摘要：
${userContext.symptomSummary}

用户当前状态摘要：
${userContext.currentStatus}

当前红旗提示：
${userContext.redFlags.length > 0 ? userContext.redFlags.join("；") : "暂无明确红旗"}

要求：
1. 先根据图片输出结构化药物信息。
2. 再判断与用户当前症状/状态是否存在需要谨慎的点。
3. advice 只能给保守的健康管理建议，最多建议“咨询医生或药师确认”。
4. evidenceNotes 只写简短依据摘要，不要编造链接。
  `.trim();

  const result = await generateStructuredVision({
    systemPrompt,
    userPrompt,
    imageDataUrl: dataUrl,
    traceId,
  });

  if (!result) {
    return NextResponse.json(fail(503, "medication image analysis unavailable", traceId), {
      status: 503,
    });
  }

  let parsed: unknown;
  try {
    parsed = JSON.parse(result.content);
  } catch {
    return NextResponse.json(fail(502, "invalid structured medication response", traceId), {
      status: 502,
    });
  }

  const payload = normalizeMedicationVisionPayload(parsed);
  return NextResponse.json(
    ok("ok", traceId, {
      ...payload,
      analysisMode: "CLOUD_IMAGE_PARSE",
      metadata: {
        providerId: STRUCTURED_VISION_PROVIDER_ID,
        modelId: STRUCTURED_VISION_MODEL_ID,
        traceId: result.traceId,
        fallbackUsed: false,
      },
    })
  );
}
