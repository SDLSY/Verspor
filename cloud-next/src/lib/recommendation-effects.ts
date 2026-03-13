import type { SupabaseClient } from "@supabase/supabase-js";

type RecommendationEffectLinkRow = {
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

type RecommendationTraceAttributionRow = {
  trace_id: string;
  created_at: string;
  provider_id: string | null;
  risk_level: string | null;
  metadata_json: {
    modelVersion?: string;
    profileCode?: string;
    modelProfile?: string;
    configSource?: string;
    recommendationMode?: string;
  } | null;
};

const ATTRIBUTION_LOOKBACK_MS = 36 * 60 * 60 * 1000;
const ATTRIBUTION_FUTURE_SKEW_MS = 5 * 60 * 1000;

type InterventionExecutionFallbackRow = {
  execution_id: string;
  ended_at: string;
  elapsed_sec: number;
  effect_score: number | null;
  before_stress: number | null;
  after_stress: number | null;
};

export type RecommendationEffectSummaryInput = {
  userId: string;
  days?: number;
  recommendationMode?: string;
  profileCode?: string;
};

type EffectAggregateBucket = {
  count: number;
  effectSum: number;
  stressSum: number;
  elapsedSum: number;
};

function toNumber(value: number | null | undefined): number {
  return typeof value === "number" && Number.isFinite(value) ? value : 0;
}

function buildEmptySummary(
  input: RecommendationEffectSummaryInput,
  source: "view" | "fallback" | "unavailable"
) {
  return {
    source,
    days: Math.max(1, Math.min(90, input.days ?? 30)),
    totalExecutions: 0,
    attributedExecutions: 0,
    attributionRate: 0,
    avgEffectScore: 0,
    avgStressDrop: 0,
    avgElapsedSec: 0,
    byRecommendationMode: [] as Array<{
      recommendationMode: string;
      executionCount: number;
      avgEffectScore: number;
      avgStressDrop: number;
    }>,
    byModelProfile: [] as Array<{
      profileCode: string;
      configSource: string;
      executionCount: number;
      avgEffectScore: number;
      avgStressDrop: number;
    }>,
    dailyTrend: [] as Array<{
      date: string;
      executionCount: number;
      avgEffectScore: number;
      avgStressDrop: number;
    }>,
  };
}

function finalizeAggregate(bucket: EffectAggregateBucket) {
  return {
    executionCount: bucket.count,
    avgEffectScore: bucket.count > 0 ? bucket.effectSum / bucket.count : 0,
    avgStressDrop: bucket.count > 0 ? bucket.stressSum / bucket.count : 0,
  };
}

function filterEffectRows(
  rows: RecommendationEffectLinkRow[],
  input: RecommendationEffectSummaryInput
): RecommendationEffectLinkRow[] {
  return rows.filter((row) => {
    if (input.recommendationMode) {
      const targetMode = input.recommendationMode.trim();
      if ((row.recommendation_mode ?? "UNSPECIFIED") !== targetMode) {
        return false;
      }
    }
    if (input.profileCode) {
      const targetProfile = input.profileCode.trim();
      if ((row.model_profile ?? "default_adult_cn") !== targetProfile) {
        return false;
      }
    }
    return true;
  });
}

function aggregateEffectRows(
  rows: RecommendationEffectLinkRow[],
  input: RecommendationEffectSummaryInput,
  source: "view" | "fallback" | "unavailable"
) {
  if (rows.length === 0) {
    return buildEmptySummary(input, source);
  }

  const byMode = new Map<string, EffectAggregateBucket>();
  const byProfile = new Map<string, EffectAggregateBucket & { configSource: string }>();
  const byDay = new Map<string, EffectAggregateBucket>();

  let attributedExecutions = 0;
  let effectSum = 0;
  let stressSum = 0;
  let elapsedSum = 0;

  rows.forEach((row) => {
    const effectScore = toNumber(row.effect_score);
    const stressDrop = toNumber(row.stress_drop);
    const dayKey = row.ended_at.slice(0, 10);

    if (row.attributed_trace_id) {
      attributedExecutions += 1;
    }
    effectSum += effectScore;
    stressSum += stressDrop;
    elapsedSum += toNumber(row.elapsed_sec);

    const modeKey = row.recommendation_mode ?? "UNSPECIFIED";
    const profileKey = `${row.model_profile ?? "default_adult_cn"}::${row.config_source ?? "default"}`;

    const modeBucket = byMode.get(modeKey) ?? { count: 0, effectSum: 0, stressSum: 0, elapsedSum: 0 };
    modeBucket.count += 1;
    modeBucket.effectSum += effectScore;
    modeBucket.stressSum += stressDrop;
    modeBucket.elapsedSum += toNumber(row.elapsed_sec);
    byMode.set(modeKey, modeBucket);

    const profileBucket =
      byProfile.get(profileKey) ?? {
        count: 0,
        effectSum: 0,
        stressSum: 0,
        elapsedSum: 0,
        configSource: row.config_source ?? "default",
      };
    profileBucket.count += 1;
    profileBucket.effectSum += effectScore;
    profileBucket.stressSum += stressDrop;
    profileBucket.elapsedSum += toNumber(row.elapsed_sec);
    byProfile.set(profileKey, profileBucket);

    const dayBucket = byDay.get(dayKey) ?? { count: 0, effectSum: 0, stressSum: 0, elapsedSum: 0 };
    dayBucket.count += 1;
    dayBucket.effectSum += effectScore;
    dayBucket.stressSum += stressDrop;
    dayBucket.elapsedSum += toNumber(row.elapsed_sec);
    byDay.set(dayKey, dayBucket);
  });

  return {
    source,
    days: Math.max(1, Math.min(90, input.days ?? 30)),
    totalExecutions: rows.length,
    attributedExecutions,
    attributionRate: rows.length > 0 ? attributedExecutions / rows.length : 0,
    avgEffectScore: rows.length > 0 ? effectSum / rows.length : 0,
    avgStressDrop: rows.length > 0 ? stressSum / rows.length : 0,
    avgElapsedSec: rows.length > 0 ? elapsedSum / rows.length : 0,
    byRecommendationMode: [...byMode.entries()]
      .map(([recommendationMode, bucket]) => ({
        recommendationMode,
        ...finalizeAggregate(bucket),
      }))
      .sort((left, right) => right.executionCount - left.executionCount),
    byModelProfile: [...byProfile.entries()]
      .map(([key, bucket]) => ({
        profileCode: key.split("::")[0],
        configSource: bucket.configSource,
        ...finalizeAggregate(bucket),
      }))
      .sort((left, right) => right.executionCount - left.executionCount),
    dailyTrend: [...byDay.entries()]
      .map(([date, bucket]) => ({
        date,
        ...finalizeAggregate(bucket),
      }))
      .sort((left, right) => left.date.localeCompare(right.date)),
  };
}

async function queryEffectViewRows(
  client: SupabaseClient,
  input: RecommendationEffectSummaryInput
): Promise<RecommendationEffectLinkRow[]> {
  const days = Math.max(1, Math.min(90, input.days ?? 30));
  const startAt = new Date(Date.now() - (days - 1) * 24 * 60 * 60 * 1000).toISOString();

  let query = client
    .from("recommendation_effect_links_v1")
    .select(
      "user_id,execution_id,ended_at,elapsed_sec,effect_score,stress_drop,attributed_trace_id,provider_id,risk_level,model_version,model_profile,config_source,recommendation_mode,attribution_mode"
    )
    .eq("user_id", input.userId)
    .gte("ended_at", startAt)
    .order("ended_at", { ascending: true });

  const { data, error } = await query.returns<RecommendationEffectLinkRow[]>();
  if (error) {
    throw new Error(error.message);
  }

  return data ?? [];
}

async function queryFallbackRows(
  client: SupabaseClient,
  input: RecommendationEffectSummaryInput
): Promise<RecommendationEffectLinkRow[]> {
  const days = Math.max(1, Math.min(90, input.days ?? 30));
  const startAt = new Date(Date.now() - (days - 1) * 24 * 60 * 60 * 1000).toISOString();
  const { data, error } = await client
    .from("intervention_executions")
    .select("execution_id,ended_at,elapsed_sec,effect_score,before_stress,after_stress")
    .eq("user_id", input.userId)
    .gte("ended_at", startAt)
    .order("ended_at", { ascending: true })
    .returns<InterventionExecutionFallbackRow[]>();

  if (error) {
    throw new Error(error.message);
  }

  return (data ?? []).map((row) => ({
    user_id: input.userId,
    execution_id: row.execution_id,
    ended_at: row.ended_at,
    elapsed_sec: row.elapsed_sec,
    effect_score: row.effect_score,
    stress_drop: toNumber(row.before_stress) - toNumber(row.after_stress),
    attributed_trace_id: null,
    provider_id: null,
    risk_level: null,
    model_version: null,
    model_profile: null,
    config_source: null,
    recommendation_mode: null,
    attribution_mode: "FALLBACK_EXECUTION_ONLY",
  }));
}

async function queryAttributionTraces(
  client: SupabaseClient,
  userId: string,
  rows: RecommendationEffectLinkRow[]
): Promise<RecommendationTraceAttributionRow[]> {
  if (rows.length === 0) {
    return [];
  }

  const endedTimes = rows.map((row) => Date.parse(row.ended_at)).filter(Number.isFinite);
  const minEndedAt = new Date(Math.min(...endedTimes) - ATTRIBUTION_LOOKBACK_MS).toISOString();
  const maxEndedAt = new Date(Math.max(...endedTimes) + ATTRIBUTION_FUTURE_SKEW_MS).toISOString();

  const { data, error } = await client
    .from("recommendation_traces")
    .select("trace_id,created_at,provider_id,risk_level,metadata_json")
    .eq("user_id", userId)
    .eq("trace_type", "DAILY_PRESCRIPTION")
    .gte("created_at", minEndedAt)
    .lte("created_at", maxEndedAt)
    .order("created_at", { ascending: true })
    .returns<RecommendationTraceAttributionRow[]>();

  if (error) {
    throw new Error(error.message);
  }

  return data ?? [];
}

function enrichRowsWithAttribution(
  rows: RecommendationEffectLinkRow[],
  traces: RecommendationTraceAttributionRow[]
): RecommendationEffectLinkRow[] {
  if (rows.length === 0 || traces.length === 0) {
    return rows;
  }

  return rows.map((row) => {
    if (row.attributed_trace_id) {
      return row;
    }

    const endedAt = Date.parse(row.ended_at);
    const bestTrace = traces
      .filter((trace) => {
        const createdAt = Date.parse(trace.created_at);
        return createdAt >= endedAt - ATTRIBUTION_LOOKBACK_MS &&
          createdAt <= endedAt + ATTRIBUTION_FUTURE_SKEW_MS;
      })
      .sort((left, right) => Date.parse(right.created_at) - Date.parse(left.created_at))[0];

    if (!bestTrace) {
      return row;
    }

    return {
      ...row,
      attributed_trace_id: bestTrace.trace_id,
      provider_id: bestTrace.provider_id,
      risk_level: bestTrace.risk_level,
      model_version: bestTrace.metadata_json?.modelVersion ?? "SRM_V2",
      model_profile:
        bestTrace.metadata_json?.profileCode ??
        bestTrace.metadata_json?.modelProfile ??
        "default_adult_cn",
      config_source: bestTrace.metadata_json?.configSource ?? "default",
      recommendation_mode: bestTrace.metadata_json?.recommendationMode ?? "FOLLOW_UP",
      attribution_mode: "WINDOW_LAST_DAILY_APP",
    };
  });
}

export async function getRecommendationEffectSummary(
  client: SupabaseClient,
  input: RecommendationEffectSummaryInput
) {
  try {
    const viewRows = await queryEffectViewRows(client, input);
    const traces = await queryAttributionTraces(client, input.userId, viewRows);
    const rows = filterEffectRows(enrichRowsWithAttribution(viewRows, traces), input);
    return aggregateEffectRows(rows, input, "view");
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    if (!/recommendation_effect_links_v1|PGRST205|not exist/i.test(message)) {
      throw error;
    }
    try {
      const rows = await queryFallbackRows(client, input);
      return aggregateEffectRows(rows, input, "fallback");
    } catch (fallbackError) {
      const fallbackMessage =
        fallbackError instanceof Error ? fallbackError.message : String(fallbackError);
      if (/intervention_executions|PGRST205|not exist/i.test(fallbackMessage)) {
        return buildEmptySummary(input, "unavailable");
      }
      throw fallbackError;
    }
  }
}
