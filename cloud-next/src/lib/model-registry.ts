import type { SupabaseClient } from "@supabase/supabase-js";

type ModelRow = {
  version: string;
  feature_schema_version: string;
  runtime_type: string;
  inference_endpoint: string | null;
  confidence_threshold: number;
  fallback_enabled: boolean;
  inference_timeout_ms: number;
};

export type ActiveModelProfile = {
  version: string;
  featureSchemaVersion: string;
  runtimeType: "fallback" | "http";
  inferenceEndpoint: string | null;
  confidenceThreshold: number;
  fallbackEnabled: boolean;
  inferenceTimeoutMs: number;
};

const DEFAULT_PROFILE: ActiveModelProfile = {
  version: "mmt-v1",
  featureSchemaVersion: "v1",
  runtimeType: "fallback",
  inferenceEndpoint: null,
  confidenceThreshold: 0.6,
  fallbackEnabled: true,
  inferenceTimeoutMs: 12000,
};

function toProfile(row: ModelRow): ActiveModelProfile {
  return {
    version: row.version || DEFAULT_PROFILE.version,
    featureSchemaVersion: row.feature_schema_version || DEFAULT_PROFILE.featureSchemaVersion,
    runtimeType: sanitizeRuntimeType(row.runtime_type),
    inferenceEndpoint: row.inference_endpoint,
    confidenceThreshold: sanitizeThreshold(Number(row.confidence_threshold)),
    fallbackEnabled: Boolean(row.fallback_enabled),
    inferenceTimeoutMs: sanitizeTimeout(Number(row.inference_timeout_ms)),
  };
}

function sanitizeRuntimeType(value: string): "fallback" | "http" {
  return value === "http" ? "http" : "fallback";
}

function sanitizeThreshold(value: number): number {
  if (!Number.isFinite(value)) {
    return DEFAULT_PROFILE.confidenceThreshold;
  }
  return Math.max(0, Math.min(1, value));
}

function sanitizeTimeout(value: number): number {
  if (!Number.isFinite(value)) {
    return DEFAULT_PROFILE.inferenceTimeoutMs;
  }
  return Math.max(1000, Math.min(120000, Math.trunc(value)));
}

export async function getActiveModelProfile(
  client: SupabaseClient,
  modelKind = "sleep-multimodal"
): Promise<ActiveModelProfile> {
  const { data, error } = await client
    .from("model_registry")
    .select(
      "version,feature_schema_version,runtime_type,inference_endpoint,confidence_threshold,fallback_enabled,inference_timeout_ms"
    )
    .eq("model_kind", modelKind)
    .eq("is_active", true)
    .order("updated_at", { ascending: false })
    .limit(1)
    .maybeSingle<ModelRow>();

  if (error || !data) {
    return DEFAULT_PROFILE;
  }

  return toProfile(data);
}

export async function getModelProfileByVersion(
  client: SupabaseClient,
  version: string,
  modelKind = "sleep-multimodal"
): Promise<ActiveModelProfile | null> {
  const normalized = version.trim();
  if (!normalized) {
    return null;
  }

  const { data, error } = await client
    .from("model_registry")
    .select(
      "version,feature_schema_version,runtime_type,inference_endpoint,confidence_threshold,fallback_enabled,inference_timeout_ms"
    )
    .eq("model_kind", modelKind)
    .eq("version", normalized)
    .maybeSingle<ModelRow>();

  if (error || !data) {
    return null;
  }

  return toProfile(data);
}

export async function getActiveModelVersion(
  client: SupabaseClient,
  modelKind = "sleep-multimodal"
): Promise<string> {
  const profile = await getActiveModelProfile(client, modelKind);
  return profile.version;
}
