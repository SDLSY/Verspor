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

  const mimeType =
    String(formData?.get("mimeType") ?? file.type ?? "image/jpeg").trim() || "image/jpeg";
  const bytes = new Uint8Array(await file.arrayBuffer());
  if (bytes.length === 0) {
    return NextResponse.json(fail(400, "empty image file", traceId), { status: 400 });
  }

  const userContext = await loadLifestyleUserContext(auth.client, auth.user.id);
  const dataUrl = buildDataUrl(bytes, mimeType);

  const systemPrompt = `
You are a nutrition image analysis assistant.
Return strict JSON only. Do not return Markdown. Do not explain the JSON.
Identify the main food items, likely meal type, estimated calories, and macronutrients from the image.
Then provide a conservative suggestion based on the user's current recovery status.
If the meal is ambiguous or portion size is uncertain, use conservative estimates and set requiresManualReview to true.
All free-text fields must be concise Simplified Chinese.
Output exactly this shape:
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
Please analyze this food image.

Current symptom summary:
${userContext.symptomSummary}

Current user status:
${userContext.currentStatus}

Current red flags:
${userContext.redFlags.length > 0 ? userContext.redFlags.join("; ") : "None"}

Requirements:
1. Identify the main food items and the likely meal type first.
2. Estimate calories, carbohydrate, protein, and fat conservatively.
3. nutritionFlags should describe risks such as high sugar, high fat, high salt, low protein, or low energy.
4. advice must stay within conservative dietary suggestions and must not make disease-treatment claims.
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
        modelId: result.modelId,
        traceId: result.traceId,
        fallbackUsed: result.modelId !== STRUCTURED_VISION_MODEL_ID,
      },
    })
  );
}
