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

  const mimeType =
    String(formData?.get("mimeType") ?? file.type ?? "image/jpeg").trim() || "image/jpeg";
  const bytes = new Uint8Array(await file.arrayBuffer());
  if (bytes.length === 0) {
    return NextResponse.json(fail(400, "empty image file", traceId), { status: 400 });
  }

  const userContext = await loadLifestyleUserContext(auth.client, auth.user.id);
  const dataUrl = buildDataUrl(bytes, mimeType);

  const systemPrompt = `
You are a medication image analysis assistant.
Return strict JSON only. Do not return Markdown. Do not explain the JSON.
Identify the medication name, dosage form, specification, and likely active ingredients from the image.
Then provide a conservative health-management suggestion based on the user's current symptoms and status.
Never provide instructions to stop medication, switch medication, change dosage, or replace a clinician.
If the image is blurry, incomplete, or low-confidence, set requiresManualReview to true.
All free-text fields must be concise Simplified Chinese.
Output exactly this shape:
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
Please analyze this medication image.

Current symptom summary:
${userContext.symptomSummary}

Current user status:
${userContext.currentStatus}

Current red flags:
${userContext.redFlags.length > 0 ? userContext.redFlags.join("; ") : "None"}

Requirements:
1. Extract the most likely medication identity from the image.
2. Judge whether the current symptoms or status suggest any caution points.
3. advice must stay conservative and may suggest consulting a doctor or pharmacist.
4. evidenceNotes should be short evidence summaries only, with no fabricated links.
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
        modelId: result.modelId,
        traceId: result.traceId,
        fallbackUsed: result.modelId !== STRUCTURED_VISION_MODEL_ID,
      },
    })
  );
}
