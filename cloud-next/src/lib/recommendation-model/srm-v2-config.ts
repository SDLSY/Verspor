import type { SupabaseClient } from "@supabase/supabase-js";

export type RecommendationModelProfileStatus = "draft" | "active" | "archived";
export type RecommendationModelProfileSource = "database" | "default";

export type RecommendationModelProfile = {
  id: string;
  modelCode: string;
  profileCode: string;
  status: RecommendationModelProfileStatus;
  description: string | null;
  thresholds: Record<string, number>;
  weights: Record<string, number>;
  gateRules: Record<string, string | number | boolean>;
  modePriorities: Record<string, string[]>;
  confidenceFormula: Record<string, number>;
  createdAt: string;
  updatedAt: string;
};

export type LoadedRecommendationModelProfile = RecommendationModelProfile & {
  source: RecommendationModelProfileSource;
};

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

export const SRM_V2_DEFAULT_PROFILE_CODE = "default_adult_cn";
export const SRM_V2_MODEL_CODE = "SRM_V2";

const DEFAULT_TIMESTAMP = new Date(0).toISOString();

export const SRM_V2_DEFAULT_PROFILE: LoadedRecommendationModelProfile = {
  id: "default-srm-v2-profile",
  modelCode: SRM_V2_MODEL_CODE,
  profileCode: SRM_V2_DEFAULT_PROFILE_CODE,
  status: "active",
  description: "SRM_V2 默认成人中文配置，作为数据库缺省或回退配置。",
  thresholds: {
    sleepDisturbance: 60,
    stressLoad: 60,
    fatigueLoad: 60,
    recoveryCapacityLow: 40,
    followUpMissingInfo: 2,
    doctorEvidenceLow: 0.45,
  },
  weights: {
    evidenceCoverage: 0.45,
    evidenceCount: 0.25,
    hypothesisCount: 0.3,
  },
  gateRules: {
    redFlagGate: "RED",
    highMedicalRiskGate: "RED",
    highPeriodRiskGate: "RED",
    escalatedStageGate: "RED",
    mediumRiskGate: "AMBER",
    mediumPeriodRiskGate: "AMBER",
  },
  modePriorities: {
    RED: ["ESCALATE"],
    AMBER: ["RECOVERY", "STRESS_REGULATION", "FOLLOW_UP", "SLEEP_PREP", "STABILIZE"],
    GREEN: ["SLEEP_PREP", "RECOVERY", "FOLLOW_UP", "STABILIZE", "STRESS_REGULATION"],
  },
  confidenceFormula: {
    coverageWeight: 0.4,
    missingPenaltyWeight: 0.35,
    riskSignalWeight: 0.25,
  },
  createdAt: DEFAULT_TIMESTAMP,
  updatedAt: DEFAULT_TIMESTAMP,
  source: "default",
};

function asNumberRecord(value: unknown, fallback: Record<string, number>): Record<string, number> {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    return { ...fallback };
  }

  const record = { ...fallback };
  Object.entries(value as Record<string, unknown>).forEach(([key, raw]) => {
    const numeric = typeof raw === "number" ? raw : Number(raw);
    if (Number.isFinite(numeric)) {
      record[key] = numeric;
    }
  });
  return record;
}

function asStringArrayRecord(
  value: unknown,
  fallback: Record<string, string[]>
): Record<string, string[]> {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    return Object.fromEntries(
      Object.entries(fallback).map(([key, items]) => [key, [...items]])
    );
  }

  const record: Record<string, string[]> = Object.fromEntries(
    Object.entries(fallback).map(([key, items]) => [key, [...items]])
  );

  Object.entries(value as Record<string, unknown>).forEach(([key, raw]) => {
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

function asScalarRecord(
  value: unknown,
  fallback: Record<string, string | number | boolean>
): Record<string, string | number | boolean> {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    return { ...fallback };
  }

  const record = { ...fallback };
  Object.entries(value as Record<string, unknown>).forEach(([key, raw]) => {
    if (typeof raw === "string" || typeof raw === "number" || typeof raw === "boolean") {
      record[key] = raw;
    }
  });
  return record;
}

export function normalizeRecommendationModelProfile(
  row?: Partial<RecommendationModelProfileRow> | null
): LoadedRecommendationModelProfile {
  if (!row) {
    return { ...SRM_V2_DEFAULT_PROFILE };
  }

  return {
    id: row.id?.trim() || SRM_V2_DEFAULT_PROFILE.id,
    modelCode: row.model_code?.trim() || SRM_V2_DEFAULT_PROFILE.modelCode,
    profileCode: row.profile_code?.trim() || SRM_V2_DEFAULT_PROFILE.profileCode,
    status: row.status ?? SRM_V2_DEFAULT_PROFILE.status,
    description: row.description ?? SRM_V2_DEFAULT_PROFILE.description,
    thresholds: asNumberRecord(row.thresholds_json, SRM_V2_DEFAULT_PROFILE.thresholds),
    weights: asNumberRecord(row.weights_json, SRM_V2_DEFAULT_PROFILE.weights),
    gateRules: asScalarRecord(row.gate_rules_json, SRM_V2_DEFAULT_PROFILE.gateRules),
    modePriorities: asStringArrayRecord(
      row.mode_priorities_json,
      SRM_V2_DEFAULT_PROFILE.modePriorities
    ),
    confidenceFormula: asNumberRecord(
      row.confidence_formula_json,
      SRM_V2_DEFAULT_PROFILE.confidenceFormula
    ),
    createdAt: row.created_at?.trim() || SRM_V2_DEFAULT_PROFILE.createdAt,
    updatedAt: row.updated_at?.trim() || SRM_V2_DEFAULT_PROFILE.updatedAt,
    source: "database",
  };
}

export async function loadRecommendationModelProfile(
  client: SupabaseClient,
  input?: { modelCode?: string; profileCode?: string }
): Promise<LoadedRecommendationModelProfile> {
  const modelCode = input?.modelCode ?? SRM_V2_MODEL_CODE;
  const profileCode = input?.profileCode?.trim() || null;

  try {
    let query = client
      .from("recommendation_model_profiles")
      .select(
        "id,model_code,profile_code,status,description,thresholds_json,weights_json,gate_rules_json,mode_priorities_json,confidence_formula_json,created_at,updated_at"
      )
      .eq("model_code", modelCode)
      .eq("status", "active")
      .order("updated_at", { ascending: false })
      .limit(1);

    if (profileCode) {
      query = query.eq("profile_code", profileCode);
    }

    const { data, error } = await query.maybeSingle<RecommendationModelProfileRow>();
    if (error) {
      console.warn("[SRM_V2] load profile failed, fallback to default", {
        modelCode,
        profileCode,
        message: error.message,
      });
      return { ...SRM_V2_DEFAULT_PROFILE };
    }

    if (!data) {
      return { ...SRM_V2_DEFAULT_PROFILE };
    }

    return normalizeRecommendationModelProfile(data);
  } catch (error) {
    console.warn("[SRM_V2] unexpected load profile failure, fallback to default", {
      modelCode,
      profileCode,
      message: error instanceof Error ? error.message : String(error),
    });
    return { ...SRM_V2_DEFAULT_PROFILE };
  }
}
