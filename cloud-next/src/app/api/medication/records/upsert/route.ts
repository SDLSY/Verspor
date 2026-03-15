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
  recognizedName?: string;
  dosageForm?: string;
  specification?: string;
  activeIngredients?: string[];
  matchedSymptoms?: string[];
  usageSummary?: string;
  riskLevel?: string;
  riskFlags?: string[];
  evidenceNotes?: string[];
  advice?: string;
  confidence?: number;
  requiresManualReview?: boolean;
  analysisMode?: string;
  providerId?: string;
  modelId?: string;
  traceId?: string;
};

function sanitizeStrings(value: unknown, limit = 8, maxLength = 120): string[] {
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

  const riskLevel = (body.riskLevel ?? "LOW").trim().toUpperCase();
  const activeIngredients = sanitizeStrings(body.activeIngredients, 8, 80);
  const matchedSymptoms = sanitizeStrings(body.matchedSymptoms, 8, 80);
  const riskFlags = sanitizeStrings(body.riskFlags, 8, 120);
  const evidenceNotes = sanitizeStrings(body.evidenceNotes, 8, 160);

  try {
    const { error } = await auth.client.from("medication_analysis_records").upsert(
      {
        user_id: auth.user.id,
        record_id: recordId,
        captured_at: toIsoTime(body.capturedAt) ?? new Date().toISOString(),
        image_uri: (body.imageUri ?? "").trim(),
        recognized_name: (body.recognizedName ?? "").trim(),
        dosage_form: (body.dosageForm ?? "").trim(),
        specification: (body.specification ?? "").trim(),
        active_ingredients_json: activeIngredients,
        matched_symptoms_json: matchedSymptoms,
        usage_summary: (body.usageSummary ?? "").trim(),
        risk_level: riskLevel,
        risk_flags_json: riskFlags,
        evidence_notes_json: evidenceNotes,
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
      actor: "api:medication/records/upsert",
      action: "upsert",
      resourceType: "medication_analysis_records",
      resourceId: recordId,
      metadata: {
        riskLevel,
        requiresManualReview: Boolean(body.requiresManualReview),
      },
    }).catch(() => null);

    return NextResponse.json(ok("ok", { recordId }));
  } catch (error) {
    const message = error instanceof Error ? error.message : "medication record upsert failed";
    return NextResponse.json(fail(500, message), { status: 500 });
  }
}
