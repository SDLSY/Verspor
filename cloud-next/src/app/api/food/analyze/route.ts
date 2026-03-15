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
  normalizeFoodVisionPayload,
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
你是营养分析助手。
你只能输出严格 JSON，不要 Markdown，不要解释。
目标：根据食物图片估算食物项、餐次、热量与三大营养素，并结合用户当前恢复状态给出保守建议。
不要编造精确克重；如果无法确认，使用保守估计并把 requiresManualReview 设为 true。
nutritionRiskLevel 只能是 LOW、MEDIUM、HIGH。
输出字段固定为：
{
  "mealType": "BREAKFAST|LUNCH|DINNER|SNACK|UNSPECIFIED",
  "foodItems": ["string"],
  "estimatedCalories": 0,
  "carbohydrateGrams": 0,
  "proteinGrams": 0,
  "fatGrams": 0,
  "nutritionRiskLevel": "LOW|MEDIUM|HIGH",
  "nutritionFlags": ["string"],
  "dailyContribution": "string",
  "advice": "string",
  "confidence": 0.0,
  "requiresManualReview": false
}
  `.trim();

  const userPrompt = `
请分析这张食物图片。

用户当前症状摘要：
${userContext.symptomSummary}

用户当前状态摘要：
${userContext.currentStatus}

当前红旗提示：
${userContext.redFlags.length > 0 ? userContext.redFlags.join("；") : "暂无明确红旗"}

要求：
1. 先识别主要食物项和餐次。
2. 再估算总热量以及碳水、蛋白、脂肪。
3. nutritionFlags 用于描述高油、高糖、高盐、蛋白偏低、热量偏低等风险。
4. advice 只给保守的饮食调整建议，不要给疾病治疗结论。
  `.trim();

  const result = await generateStructuredVision({
    systemPrompt,
    userPrompt,
    imageDataUrl: dataUrl,
    traceId,
  });

  if (!result) {
    return NextResponse.json(fail(503, "food image analysis unavailable", traceId), {
      status: 503,
    });
  }

  let parsed: unknown;
  try {
    parsed = JSON.parse(result.content);
  } catch {
    return NextResponse.json(fail(502, "invalid structured food response", traceId), {
      status: 502,
    });
  }

  const payload = normalizeFoodVisionPayload(parsed);
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
