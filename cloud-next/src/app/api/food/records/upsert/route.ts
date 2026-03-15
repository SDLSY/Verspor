import { NextResponse } from "next/server";
import { requireAuth } from "@/lib/auth";
import { writeAuditEvent } from "@/lib/audit";
import { fail, ok, parseJsonBody } from "@/lib/http";
import { toIsoTime } from "@/lib/inference";
import { createServiceClient } from "@/lib/supabase";

type UpsertBody = {
  recordId?: string;
  capturedAt?: number;
  imageUri?: string;
  mealType?: string;
  foodItems?: string[];
  estimatedCalories?: number;
  carbohydrateGrams?: number;
  proteinGrams?: number;
  fatGrams?: number;
  nutritionRiskLevel?: string;
  nutritionFlags?: string[];
  dailyContribution?: string;
  advice?: string;
  confidence?: number;
  requiresManualReview?: boolean;
  analysisMode?: string;
  providerId?: string;
  modelId?: string;
  traceId?: string;
};

function sanitizeStrings(value: unknown, limit = 10, maxLength = 120): string[] {
  if (!Array.isArray(value)) {
    return [];
  }
  return Array.from(
    new Set(
      value
        .map((item) => (typeof item === "string" ? item.trim() : ""))
        .filter(Boolean)
        .slice(0, limit)
        .map((item) => item.slice(0, maxLength))
    )
  );
}

export async function POST(req: Request) {
  const auth = await requireAuth(req);
  if (!auth.authenticated) {
    return auth.response;
  }

  const body = parseJsonBody<UpsertBody>(await req.json().catch(() => ({})));
  const recordId = (body.recordId ?? "").trim();
  if (!recordId) {
    return NextResponse.json(fail(400, "recordId is required"), { status: 400 });
  }

  const nutritionRiskLevel = (body.nutritionRiskLevel ?? "LOW").trim().toUpperCase();
  const foodItems = sanitizeStrings(body.foodItems, 10, 80);
  const nutritionFlags = sanitizeStrings(body.nutritionFlags, 8, 120);

  try {
    const { error } = await auth.client.from("food_analysis_records").upsert(
      {
        user_id: auth.user.id,
        record_id: recordId,
        captured_at: toIsoTime(body.capturedAt) ?? new Date().toISOString(),
        image_uri: (body.imageUri ?? "").trim(),
        meal_type: (body.mealType ?? "UNSPECIFIED").trim().toUpperCase(),
        food_items_json: foodItems,
        estimated_calories: Math.round(Number(body.estimatedCalories ?? 0)),
        carbohydrate_grams: Number(body.carbohydrateGrams ?? 0),
        protein_grams: Number(body.proteinGrams ?? 0),
        fat_grams: Number(body.fatGrams ?? 0),
        nutrition_risk_level: nutritionRiskLevel,
        nutrition_flags_json: nutritionFlags,
        daily_contribution: (body.dailyContribution ?? "").trim(),
        advice: (body.advice ?? "").trim(),
        confidence: Number(body.confidence ?? 0),
        requires_manual_review: Boolean(body.requiresManualReview),
        analysis_mode: (body.analysisMode ?? "MANUAL").trim().toUpperCase(),
        provider_id: (body.providerId ?? "").trim() || null,
        model_id: (body.modelId ?? "").trim() || null,
        trace_id: (body.traceId ?? "").trim() || null,
        updated_at: new Date().toISOString(),
      },
      { onConflict: "user_id,record_id" }
    );

    if (error) {
      return NextResponse.json(fail(500, error.message), { status: 500 });
    }

    const serviceClient = createServiceClient();
    await writeAuditEvent(serviceClient, {
      userId: auth.user.id,
      actor: "api:food/records/upsert",
      action: "upsert",
      resourceType: "food_analysis_records",
      resourceId: recordId,
      metadata: {
        nutritionRiskLevel,
        estimatedCalories: Math.round(Number(body.estimatedCalories ?? 0)),
      },
    }).catch(() => null);

    return NextResponse.json(ok("ok", { recordId }));
  } catch (error) {
    const message = error instanceof Error ? error.message : "food record upsert failed";
    return NextResponse.json(fail(500, message), { status: 500 });
  }
}
