import { listDirectoryUsers, normalizeDisplayName, toTimestamp } from "@/lib/admin-core";
import { createServiceClient } from "@/lib/supabase";

type RecommendationTraceMetadata = {
  explanation?: {
    summary?: string;
    reasons?: string[];
    nextStep?: string;
  };
  modelVersion?: string;
  profileCode?: string;
  modelProfile?: string;
  configSource?: string;
  recommendationMode?: string;
};

type RecommendationTraceAdminRow = {
  id: string;
  user_id: string;
  trace_type: string;
  trace_key: string | null;
  trace_id: string | null;
  provider_id: string | null;
  risk_level: string | null;
  personalization_level: string | null;
  is_fallback: boolean;
  metadata_json: unknown;
  derived_signals_json: unknown;
  created_at: string;
};

type RecommendationEffectAdminRow = {
  user_id: string;
  execution_id: string;
  ended_at: string;
  elapsed_sec: number;
  effect_score: number | null;
  stress_drop: number | null;
  attributed_trace_id: string | null;
  provider_id: string | null;
  risk_level: string | null;
  model_version: string | null;
  model_profile: string | null;
  config_source: string | null;
  recommendation_mode: string | null;
  attribution_mode: string;
};

export type RecommendationTraceAdminItem = {
  id: string;
  userId: string;
  email: string;
  displayName: string;
  traceType: string;
  traceKey: string | null;
  traceId: string | null;
  providerId: string | null;
  riskLevel: string | null;
  personalizationLevel: string | null;
  isFallback: boolean;
  createdAt: number | null;
  summary: string;
  reasons: string[];
  nextStep: string | null;
  modelVersion: string | null;
  profileCode: string | null;
  configSource: string | null;
  recommendationMode: string | null;
  safetyGate: string | null;
  evidenceCoverage: number | null;
  evidenceHighlights: string[];
};

export type RecommendationTraceAdminView = {
  summary: {
    totalTraces: number;
    fallbackTraces: number;
    highRiskTraces: number;
    topRecommendationMode: string | null;
    configSources: string[];
  };
  filters: {
    q: string;
    traceType: string;
    recommendationMode: string;
    configSource: string;
    userId: string;
    days: number;
  };
  pagination: {
    page: number;
    pageSize: number;
    total: number;
    totalPages: number;
  };
  items: RecommendationTraceAdminItem[];
};

export type RecommendationEffectAdminView = {
  source: "view" | "unavailable";
  days: number;
  totalExecutions: number;
  attributedExecutions: number;
  attributionRate: number;
  avgEffectScore: number;
  avgStressDrop: number;
  avgElapsedSec: number;
  byRecommendationMode: Array<{
    recommendationMode: string;
    executionCount: number;
    avgEffectScore: number;
    avgStressDrop: number;
  }>;
  byModelProfile: Array<{
    profileCode: string;
    configSource: string;
    executionCount: number;
    avgEffectScore: number;
    avgStressDrop: number;
  }>;
  byUser: Array<{
    userId: string;
    email: string;
    displayName: string;
    executionCount: number;
    avgEffectScore: number;
    avgStressDrop: number;
  }>;
};

export type RecommendationTraceAdminFilters = {
  page?: number;
  pageSize?: number;
  q?: string;
  userId?: string;
  traceType?: string;
  recommendationMode?: string;
  configSource?: string;
  days?: number;
};

export type RecommendationEffectAdminFilters = {
  days?: number;
  profileCode?: string;
  recommendationMode?: string;
  userId?: string;
};

function clampInteger(value: number | undefined, fallback: number, low: number, high: number): number {
  if (!Number.isFinite(value ?? NaN)) {
    return fallback;
  }
  return Math.max(low, Math.min(high, Math.trunc(value as number)));
}

function toObject(value: unknown): Record<string, unknown> {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    return {};
  }
  return value as Record<string, unknown>;
}

function readString(value: unknown): string | null {
  return typeof value === "string" && value.trim().length > 0 ? value.trim() : null;
}

function readNumber(value: unknown): number | null {
  if (typeof value === "number" && Number.isFinite(value)) {
    return value;
  }
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}

function readStringArray(value: unknown, max = 3): string[] {
  if (!Array.isArray(value)) {
    return [];
  }
  return value
    .map((item) => (typeof item === "string" ? item.trim() : ""))
    .filter(Boolean)
    .slice(0, max);
}

function buildTraceItem(row: RecommendationTraceAdminRow, userInfo: { email: string; displayName: string }): RecommendationTraceAdminItem {
  const metadata = toObject(row.metadata_json) as RecommendationTraceMetadata;
  const derivedSignals = toObject(row.derived_signals_json);
  const scientificModel = toObject(derivedSignals.scientificModel);
  const explanation = metadata.explanation ?? {};
  const evidenceLedger = Array.isArray(scientificModel.evidenceLedger)
    ? scientificModel.evidenceLedger
        .slice(0, 3)
        .map((item) => readString(toObject(item).label) ?? "")
        .filter(Boolean)
    : [];

  return {
    id: row.id,
    userId: row.user_id,
    email: userInfo.email,
    displayName: userInfo.displayName,
    traceType: row.trace_type,
    traceKey: row.trace_key,
    traceId: row.trace_id,
    providerId: row.provider_id,
    riskLevel: row.risk_level,
    personalizationLevel: row.personalization_level,
    isFallback: row.is_fallback,
    createdAt: toTimestamp(row.created_at),
    summary:
      readString(explanation.summary) ??
      readString(scientificModel.decisionSummary) ??
      "系统已完成本次建议决策。",
    reasons:
      readStringArray(explanation.reasons, 3).length > 0
        ? readStringArray(explanation.reasons, 3)
        : evidenceLedger,
    nextStep: readString(explanation.nextStep),
    modelVersion: readString(metadata.modelVersion) ?? readString(scientificModel.modelVersion),
    profileCode:
      readString(metadata.profileCode) ??
      readString(metadata.modelProfile) ??
      readString(scientificModel.profileCode),
    configSource:
      readString(metadata.configSource) ?? readString(scientificModel.configSource),
    recommendationMode:
      readString(metadata.recommendationMode) ?? readString(scientificModel.recommendationMode),
    safetyGate: readString(scientificModel.safetyGate),
    evidenceCoverage: readNumber(scientificModel.evidenceCoverage),
    evidenceHighlights: evidenceLedger,
  };
}

function matchesQuery(item: RecommendationTraceAdminItem, q: string): boolean {
  if (!q) {
    return true;
  }
  const keyword = q.trim().toLowerCase();
  return [item.displayName, item.email, item.userId, item.traceId ?? "", item.providerId ?? "", item.summary].some((value) =>
    value.toLowerCase().includes(keyword)
  );
}

export async function listAdminRecommendationTraces(
  filters: RecommendationTraceAdminFilters = {}
): Promise<RecommendationTraceAdminView> {
  const client = createServiceClient();
  const page = clampInteger(filters.page, 1, 1, 9999);
  const pageSize = clampInteger(filters.pageSize, 20, 1, 50);
  const days = clampInteger(filters.days, 14, 1, 90);
  const startAt = new Date(Date.now() - days * 24 * 60 * 60 * 1000).toISOString();

  let query = client
    .from("recommendation_traces")
    .select("id,user_id,trace_type,trace_key,trace_id,provider_id,risk_level,personalization_level,is_fallback,metadata_json,derived_signals_json,created_at")
    .gte("created_at", startAt)
    .order("created_at", { ascending: false })
    .limit(500);

  if (filters.userId?.trim()) {
    query = query.eq("user_id", filters.userId.trim());
  }
  if (filters.traceType?.trim()) {
    query = query.eq("trace_type", filters.traceType.trim());
  }

  const [{ data, error }, users] = await Promise.all([query.returns<RecommendationTraceAdminRow[]>(), listDirectoryUsers(600)]);
  if (error) {
    throw new Error(error.message);
  }

  const directory = new Map(users.map((user) => [user.id, { email: user.email ?? "", displayName: normalizeDisplayName(user) }]));

  const items = (data ?? []).map((row) =>
    buildTraceItem(row, directory.get(row.user_id) ?? { email: "", displayName: row.user_id.slice(0, 8) })
  );

  const filtered = items.filter((item) => {
    if (filters.recommendationMode?.trim() && (item.recommendationMode ?? "") !== filters.recommendationMode.trim()) {
      return false;
    }
    if (filters.configSource?.trim() && (item.configSource ?? "") !== filters.configSource.trim()) {
      return false;
    }
    return matchesQuery(item, filters.q ?? "");
  });

  const total = filtered.length;
  const totalPages = Math.max(1, Math.ceil(total / pageSize));
  const pageItems = filtered.slice((page - 1) * pageSize, page * pageSize);
  const modeCounts = filtered.reduce<Record<string, number>>((acc, item) => {
    const key = item.recommendationMode ?? "UNSPECIFIED";
    acc[key] = (acc[key] ?? 0) + 1;
    return acc;
  }, {});

  return {
    summary: {
      totalTraces: total,
      fallbackTraces: filtered.filter((item) => item.isFallback).length,
      highRiskTraces: filtered.filter((item) => {
        const risk = (item.riskLevel ?? "").toUpperCase();
        return risk === "HIGH" || risk === "CRITICAL";
      }).length,
      topRecommendationMode: Object.entries(modeCounts).sort((left, right) => right[1] - left[1])[0]?.[0] ?? null,
      configSources: Array.from(new Set(filtered.map((item) => item.configSource ?? "default"))).sort(),
    },
    filters: {
      q: filters.q?.trim() ?? "",
      traceType: filters.traceType?.trim() ?? "",
      recommendationMode: filters.recommendationMode?.trim() ?? "",
      configSource: filters.configSource?.trim() ?? "",
      userId: filters.userId?.trim() ?? "",
      days,
    },
    pagination: { page, pageSize, total, totalPages },
    items: pageItems,
  };
}

function aggregateBucket(target: { count: number; effect: number; stress: number; elapsed: number }, row: RecommendationEffectAdminRow) {
  target.count += 1;
  target.effect += typeof row.effect_score === "number" ? row.effect_score : 0;
  target.stress += typeof row.stress_drop === "number" ? row.stress_drop : 0;
  target.elapsed += typeof row.elapsed_sec === "number" ? row.elapsed_sec : 0;
}

export async function getAdminRecommendationEffects(
  filters: RecommendationEffectAdminFilters = {}
): Promise<RecommendationEffectAdminView> {
  const client = createServiceClient();
  const days = clampInteger(filters.days, 30, 1, 90);
  const startAt = new Date(Date.now() - (days - 1) * 24 * 60 * 60 * 1000).toISOString();

  let query = client
    .from("recommendation_effect_links_v1")
    .select("user_id,execution_id,ended_at,elapsed_sec,effect_score,stress_drop,attributed_trace_id,provider_id,risk_level,model_version,model_profile,config_source,recommendation_mode,attribution_mode")
    .gte("ended_at", startAt)
    .order("ended_at", { ascending: false });

  if (filters.userId?.trim()) {
    query = query.eq("user_id", filters.userId.trim());
  }

  const [{ data, error }, users] = await Promise.all([query.returns<RecommendationEffectAdminRow[]>(), listDirectoryUsers(600)]);
  if (error) {
    return {
      source: "unavailable",
      days,
      totalExecutions: 0,
      attributedExecutions: 0,
      attributionRate: 0,
      avgEffectScore: 0,
      avgStressDrop: 0,
      avgElapsedSec: 0,
      byRecommendationMode: [],
      byModelProfile: [],
      byUser: [],
    };
  }

  const filtered = (data ?? []).filter((row) => {
    if (filters.profileCode?.trim() && (row.model_profile ?? "") !== filters.profileCode.trim()) {
      return false;
    }
    if (filters.recommendationMode?.trim() && (row.recommendation_mode ?? "") !== filters.recommendationMode.trim()) {
      return false;
    }
    return true;
  });

  const directory = new Map(users.map((user) => [user.id, { email: user.email ?? "", displayName: normalizeDisplayName(user) }]));
  if (filtered.length === 0) {
    return {
      source: "view",
      days,
      totalExecutions: 0,
      attributedExecutions: 0,
      attributionRate: 0,
      avgEffectScore: 0,
      avgStressDrop: 0,
      avgElapsedSec: 0,
      byRecommendationMode: [],
      byModelProfile: [],
      byUser: [],
    };
  }

  const byMode = new Map<string, { count: number; effect: number; stress: number; elapsed: number }>();
  const byProfile = new Map<string, { count: number; effect: number; stress: number; elapsed: number; configSource: string }>();
  const byUser = new Map<string, { count: number; effect: number; stress: number; elapsed: number }>();

  let attributedExecutions = 0;
  let effectSum = 0;
  let stressSum = 0;
  let elapsedSum = 0;

  filtered.forEach((row) => {
    if (row.attributed_trace_id) {
      attributedExecutions += 1;
    }
    effectSum += typeof row.effect_score === "number" ? row.effect_score : 0;
    stressSum += typeof row.stress_drop === "number" ? row.stress_drop : 0;
    elapsedSum += typeof row.elapsed_sec === "number" ? row.elapsed_sec : 0;

    const modeBucket = byMode.get(row.recommendation_mode ?? "UNSPECIFIED") ?? { count: 0, effect: 0, stress: 0, elapsed: 0 };
    aggregateBucket(modeBucket, row);
    byMode.set(row.recommendation_mode ?? "UNSPECIFIED", modeBucket);

    const profileKey = `${row.model_profile ?? "default_adult_cn"}::${row.config_source ?? "default"}`;
    const profileBucket = byProfile.get(profileKey) ?? {
      count: 0,
      effect: 0,
      stress: 0,
      elapsed: 0,
      configSource: row.config_source ?? "default",
    };
    aggregateBucket(profileBucket, row);
    byProfile.set(profileKey, profileBucket);

    const userBucket = byUser.get(row.user_id) ?? { count: 0, effect: 0, stress: 0, elapsed: 0 };
    aggregateBucket(userBucket, row);
    byUser.set(row.user_id, userBucket);
  });

  return {
    source: "view",
    days,
    totalExecutions: filtered.length,
    attributedExecutions,
    attributionRate: filtered.length > 0 ? attributedExecutions / filtered.length : 0,
    avgEffectScore: filtered.length > 0 ? effectSum / filtered.length : 0,
    avgStressDrop: filtered.length > 0 ? stressSum / filtered.length : 0,
    avgElapsedSec: filtered.length > 0 ? elapsedSum / filtered.length : 0,
    byRecommendationMode: [...byMode.entries()].map(([recommendationMode, bucket]) => ({
      recommendationMode,
      executionCount: bucket.count,
      avgEffectScore: bucket.count > 0 ? bucket.effect / bucket.count : 0,
      avgStressDrop: bucket.count > 0 ? bucket.stress / bucket.count : 0,
    })).sort((left, right) => right.executionCount - left.executionCount),
    byModelProfile: [...byProfile.entries()].map(([key, bucket]) => ({
      profileCode: key.split("::")[0],
      configSource: bucket.configSource,
      executionCount: bucket.count,
      avgEffectScore: bucket.count > 0 ? bucket.effect / bucket.count : 0,
      avgStressDrop: bucket.count > 0 ? bucket.stress / bucket.count : 0,
    })).sort((left, right) => right.executionCount - left.executionCount),
    byUser: [...byUser.entries()].map(([userId, bucket]) => ({
      userId,
      email: directory.get(userId)?.email ?? "",
      displayName: directory.get(userId)?.displayName ?? userId.slice(0, 8),
      executionCount: bucket.count,
      avgEffectScore: bucket.count > 0 ? bucket.effect / bucket.count : 0,
      avgStressDrop: bucket.count > 0 ? bucket.stress / bucket.count : 0,
    })).sort((left, right) => right.executionCount - left.executionCount).slice(0, 12),
  };
}
