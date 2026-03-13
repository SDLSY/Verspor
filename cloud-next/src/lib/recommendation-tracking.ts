import type { SupabaseClient } from "@supabase/supabase-js";

export type RecommendationTraceType =
  | "DAILY_PRESCRIPTION"
  | "PERIOD_SUMMARY"
  | "DOCTOR_TURN";

type RecommendationTraceInput = {
  userId: string;
  traceType: RecommendationTraceType;
  traceKey?: string | null;
  traceId?: string | null;
  providerId?: string | null;
  relatedSnapshotId?: string | null;
  relatedRecommendationId?: string | null;
  riskLevel?: string | null;
  personalizationLevel?: string | null;
  missingInputs?: string[];
  inputMaterials?: Record<string, unknown>;
  derivedSignals?: Record<string, unknown>;
  outputPayload?: Record<string, unknown>;
  metadata?: Record<string, unknown>;
  isFallback?: boolean;
  source?: string;
};

function sanitizeStringArray(value: string[] | undefined): string[] {
  if (!Array.isArray(value)) {
    return [];
  }
  return Array.from(
    new Set(
      value
        .map((item) => item.trim())
        .filter(Boolean)
        .slice(0, 16)
    )
  );
}

export async function writeRecommendationTrace(
  client: SupabaseClient,
  input: RecommendationTraceInput
): Promise<string | null> {
  const { data, error } = await client
    .from("recommendation_traces")
    .insert({
      user_id: input.userId,
      trace_type: input.traceType,
      trace_key: input.traceKey ?? null,
      trace_id: input.traceId ?? null,
      provider_id: input.providerId ?? null,
      related_snapshot_id: input.relatedSnapshotId ?? null,
      related_recommendation_id: input.relatedRecommendationId ?? null,
      risk_level: input.riskLevel ?? null,
      personalization_level: input.personalizationLevel ?? null,
      missing_inputs_json: sanitizeStringArray(input.missingInputs),
      input_materials_json: input.inputMaterials ?? {},
      derived_signals_json: input.derivedSignals ?? {},
      output_payload_json: input.outputPayload ?? {},
      metadata_json: input.metadata ?? {},
      is_fallback: input.isFallback ?? false,
      source: input.source ?? "CLOUD_NEXT",
    })
    .select("id")
    .single();

  if (error) {
    console.warn("[tracking] recommendation trace insert failed", {
      traceType: input.traceType,
      traceId: input.traceId,
      message: error.message,
    });
    return null;
  }

  return (data as { id: string }).id;
}
