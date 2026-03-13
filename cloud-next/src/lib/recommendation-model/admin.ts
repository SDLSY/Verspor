import { createServiceClient } from "@/lib/supabase";
import {
  normalizeRecommendationModelProfile,
  type LoadedRecommendationModelProfile,
  type RecommendationModelProfileStatus,
} from "@/lib/recommendation-model/srm-v2-config";
import { writeAuditEvent } from "@/lib/audit";

type RecommendationModelProfileRow = {
  id: string;
  model_code: string;
  profile_code: string;
  status: RecommendationModelProfileStatus;
  description: string | null;
  thresholds_json: unknown;
  weights_json: unknown;
  gate_rules_json: unknown;
  mode_priorities_json: unknown;
  confidence_formula_json: unknown;
  created_at: string;
  updated_at: string;
};

export type RecommendationModelProfileAdminInput = {
  modelCode: string;
  profileCode: string;
  status?: RecommendationModelProfileStatus;
  description?: string | null;
  thresholds?: Record<string, unknown>;
  weights?: Record<string, unknown>;
  gateRules?: Record<string, unknown>;
  modePriorities?: Record<string, unknown>;
  confidenceFormula?: Record<string, unknown>;
};

export type RecommendationModelProfileAdminFilters = {
  modelCode?: string;
  profileCode?: string;
  status?: RecommendationModelProfileStatus;
};

export type RecommendationProfileVersionEntry = {
  id: string;
  action: string;
  actor: string;
  createdAt: string;
  note: string | null;
  sourceProfileCode: string | null;
  snapshot: LoadedRecommendationModelProfile | null;
};

type RecommendationProfileVersionOptions = {
  actor?: string;
  action?: string;
  note?: string | null;
  sourceProfileCode?: string | null;
};

type AuditVersionRow = {
  id: string;
  actor: string;
  action: string;
  metadata: unknown;
  created_at: string;
};

function sanitizeCode(value: string, field: string): string {
  const normalized = value.trim();
  if (!/^[A-Za-z0-9_.-]{2,64}$/.test(normalized)) {
    throw new Error(`${field} format invalid`);
  }
  return normalized;
}

function sanitizeStatus(value: RecommendationModelProfileStatus | undefined): RecommendationModelProfileStatus {
  if (value === "draft" || value === "active" || value === "archived") {
    return value;
  }
  return "draft";
}

function buildResourceId(modelCode: string, profileCode: string): string {
  return `${modelCode}:${profileCode}`;
}

function toNumberRecord(value: Record<string, unknown> | undefined): Record<string, number> {
  if (!value) {
    return {};
  }
  const record: Record<string, number> = {};
  Object.entries(value).forEach(([key, raw]) => {
    const numeric = typeof raw === "number" ? raw : Number(raw);
    if (Number.isFinite(numeric)) {
      record[key] = numeric;
    }
  });
  return record;
}

function toScalarRecord(
  value: Record<string, unknown> | undefined
): Record<string, string | number | boolean> {
  if (!value) {
    return {};
  }
  const record: Record<string, string | number | boolean> = {};
  Object.entries(value).forEach(([key, raw]) => {
    if (typeof raw === "string" || typeof raw === "number" || typeof raw === "boolean") {
      record[key] = raw;
    }
  });
  return record;
}

function toStringArrayRecord(value: Record<string, unknown> | undefined): Record<string, string[]> {
  if (!value) {
    return {};
  }
  const record: Record<string, string[]> = {};
  Object.entries(value).forEach(([key, raw]) => {
    if (!Array.isArray(raw)) {
      return;
    }
    const items = raw
      .map((item) => (typeof item === "string" ? item.trim() : ""))
      .filter(Boolean);
    if (items.length > 0) {
      record[key] = Array.from(new Set(items));
    }
  });
  return record;
}

async function archiveOtherActiveProfiles(modelCode: string, profileCode: string): Promise<void> {
  const client = createServiceClient();
  await client
    .from("recommendation_model_profiles")
    .update({
      status: "archived",
      updated_at: new Date().toISOString(),
    })
    .eq("model_code", modelCode)
    .eq("status", "active")
    .neq("profile_code", profileCode);
}

async function persistRecommendationModelProfile(
  client: ReturnType<typeof createServiceClient>,
  input: RecommendationModelProfileAdminInput
) {
  const modelCode = sanitizeCode(input.modelCode, "modelCode");
  const profileCode = sanitizeCode(input.profileCode, "profileCode");
  const status = sanitizeStatus(input.status);

  if (status === "active") {
    await archiveOtherActiveProfiles(modelCode, profileCode);
  }

  const { data, error } = await client
    .from("recommendation_model_profiles")
    .upsert(
      {
        model_code: modelCode,
        profile_code: profileCode,
        status,
        description: input.description?.trim() || null,
        thresholds_json: toNumberRecord(input.thresholds),
        weights_json: toNumberRecord(input.weights),
        gate_rules_json: toScalarRecord(input.gateRules),
        mode_priorities_json: toStringArrayRecord(input.modePriorities),
        confidence_formula_json: toNumberRecord(input.confidenceFormula),
        updated_at: new Date().toISOString(),
      },
      { onConflict: "model_code,profile_code" }
    )
    .select(
      "id,model_code,profile_code,status,description,thresholds_json,weights_json,gate_rules_json,mode_priorities_json,confidence_formula_json,created_at,updated_at"
    )
    .single<RecommendationModelProfileRow>();

  if (error) {
    throw new Error(error.message);
  }

  return normalizeRecommendationModelProfile(data);
}

async function recordRecommendationProfileVersion(
  client: ReturnType<typeof createServiceClient>,
  profile: LoadedRecommendationModelProfile,
  options: RecommendationProfileVersionOptions = {}
): Promise<void> {
  await writeAuditEvent(client, {
    actor: options.actor ?? "ADMIN_CONSOLE",
    action: options.action ?? "recommendation_profile_saved",
    resourceType: "recommendation_profile",
    resourceId: buildResourceId(profile.modelCode, profile.profileCode),
    metadata: {
      modelCode: profile.modelCode,
      profileCode: profile.profileCode,
      status: profile.status,
      description: profile.description,
      note: options.note ?? null,
      sourceProfileCode: options.sourceProfileCode ?? null,
      snapshot: {
        modelCode: profile.modelCode,
        profileCode: profile.profileCode,
        status: profile.status,
        description: profile.description,
        thresholds: profile.thresholds,
        weights: profile.weights,
        gateRules: profile.gateRules,
        modePriorities: profile.modePriorities,
        confidenceFormula: profile.confidenceFormula,
      },
    },
  });
}

function parseVersionEntry(row: AuditVersionRow): RecommendationProfileVersionEntry {
  const metadata =
    row.metadata && typeof row.metadata === "object" && !Array.isArray(row.metadata)
      ? (row.metadata as Record<string, unknown>)
      : {};
  const snapshot =
    metadata.snapshot && typeof metadata.snapshot === "object" && !Array.isArray(metadata.snapshot)
      ? normalizeRecommendationModelProfile({
          id: row.id,
          model_code: typeof metadata.modelCode === "string" ? metadata.modelCode : undefined,
          profile_code: typeof metadata.profileCode === "string" ? metadata.profileCode : undefined,
          status:
            metadata.snapshot &&
            typeof metadata.snapshot === "object" &&
            !Array.isArray(metadata.snapshot) &&
            typeof (metadata.snapshot as Record<string, unknown>).status === "string"
              ? ((metadata.snapshot as Record<string, unknown>).status as RecommendationModelProfileStatus)
              : undefined,
          description:
            metadata.snapshot &&
            typeof metadata.snapshot === "object" &&
            !Array.isArray(metadata.snapshot) &&
            typeof (metadata.snapshot as Record<string, unknown>).description === "string"
              ? ((metadata.snapshot as Record<string, unknown>).description as string)
              : null,
          thresholds_json:
            metadata.snapshot && typeof metadata.snapshot === "object" && !Array.isArray(metadata.snapshot)
              ? (metadata.snapshot as Record<string, unknown>).thresholds
              : undefined,
          weights_json:
            metadata.snapshot && typeof metadata.snapshot === "object" && !Array.isArray(metadata.snapshot)
              ? (metadata.snapshot as Record<string, unknown>).weights
              : undefined,
          gate_rules_json:
            metadata.snapshot && typeof metadata.snapshot === "object" && !Array.isArray(metadata.snapshot)
              ? (metadata.snapshot as Record<string, unknown>).gateRules
              : undefined,
          mode_priorities_json:
            metadata.snapshot && typeof metadata.snapshot === "object" && !Array.isArray(metadata.snapshot)
              ? (metadata.snapshot as Record<string, unknown>).modePriorities
              : undefined,
          confidence_formula_json:
            metadata.snapshot && typeof metadata.snapshot === "object" && !Array.isArray(metadata.snapshot)
              ? (metadata.snapshot as Record<string, unknown>).confidenceFormula
              : undefined,
          created_at: row.created_at,
          updated_at: row.created_at,
        })
      : null;

  return {
    id: row.id,
    action: row.action,
    actor: row.actor,
    createdAt: row.created_at,
    note: typeof metadata.note === "string" ? metadata.note : null,
    sourceProfileCode: typeof metadata.sourceProfileCode === "string" ? metadata.sourceProfileCode : null,
    snapshot,
  };
}

export async function listRecommendationModelProfiles(
  filters: RecommendationModelProfileAdminFilters = {}
) {
  const client = createServiceClient();
  let query = client
    .from("recommendation_model_profiles")
    .select(
      "id,model_code,profile_code,status,description,thresholds_json,weights_json,gate_rules_json,mode_priorities_json,confidence_formula_json,created_at,updated_at"
    )
    .order("model_code", { ascending: true })
    .order("updated_at", { ascending: false });

  if (filters.modelCode) {
    query = query.eq("model_code", filters.modelCode.trim());
  }
  if (filters.profileCode) {
    query = query.eq("profile_code", filters.profileCode.trim());
  }
  if (filters.status) {
    query = query.eq("status", filters.status);
  }

  const { data, error } = await query.returns<RecommendationModelProfileRow[]>();
  if (error) {
    throw new Error(error.message);
  }

  return (data ?? []).map((row) => normalizeRecommendationModelProfile(row));
}

export async function upsertRecommendationModelProfile(
  input: RecommendationModelProfileAdminInput,
  options: RecommendationProfileVersionOptions = {}
) {
  const client = createServiceClient();
  const profile = await persistRecommendationModelProfile(client, input);
  await recordRecommendationProfileVersion(client, profile, options);
  return profile;
}

export async function listRecommendationProfileVersions(
  modelCode: string,
  profileCode: string,
  limit = 8
) {
  const client = createServiceClient();
  const { data, error } = await client
    .from("audit_events")
    .select("id,actor,action,metadata,created_at")
    .eq("resource_type", "recommendation_profile")
    .eq("resource_id", buildResourceId(modelCode, profileCode))
    .order("created_at", { ascending: false })
    .limit(limit)
    .returns<AuditVersionRow[]>();

  if (error) {
    throw new Error(error.message);
  }

  return (data ?? []).map(parseVersionEntry);
}

export async function rollbackRecommendationModelProfileVersion(versionId: string) {
  const client = createServiceClient();
  const { data, error } = await client
    .from("audit_events")
    .select("id,actor,action,metadata,created_at")
    .eq("id", versionId)
    .eq("resource_type", "recommendation_profile")
    .maybeSingle<AuditVersionRow>();

  if (error) {
    throw new Error(error.message);
  }
  if (!data) {
    throw new Error("找不到要回滚的版本");
  }

  const version = parseVersionEntry(data);
  if (!version.snapshot) {
    throw new Error("该版本不包含可回滚的快照");
  }

  return upsertRecommendationModelProfile(
    {
      modelCode: version.snapshot.modelCode,
      profileCode: version.snapshot.profileCode,
      status: version.snapshot.status,
      description: version.snapshot.description,
      thresholds: version.snapshot.thresholds,
      weights: version.snapshot.weights,
      gateRules: version.snapshot.gateRules,
      modePriorities: version.snapshot.modePriorities,
      confidenceFormula: version.snapshot.confidenceFormula,
    },
    {
      action: "recommendation_profile_rolled_back",
      note: `回滚到版本 ${version.id}`,
      sourceProfileCode: version.snapshot.profileCode,
    }
  );
}

