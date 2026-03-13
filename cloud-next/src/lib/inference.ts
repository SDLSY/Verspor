import type { SupabaseClient } from "@supabase/supabase-js";

type JsonMap = Record<string, unknown>;

export type AnalysisData = {
  sleepStages: string[];
  sleepStages5: string[];
  recoveryScore: number;
  sleepQuality: string;
  insights: string[];
  anomalyScore: number;
  factors: string[];
  modelVersion: string;
};

type ReportRow = {
  recovery_score: number | null;
  sleep_quality: string | null;
  insights: unknown;
  model_version: string | null;
};

type StageRow = {
  stage_5: string;
  stage_legacy: string;
  model_version: string;
};

type AnomalyRow = {
  score_0_100: number;
  primary_factors: unknown;
  model_version: string;
};

type JobRow = {
  id: string;
  status: string;
  model_version: string | null;
  finished_at: string | null;
  sleep_record_id: string;
};

function toStringArray(value: unknown): string[] {
  if (!Array.isArray(value)) {
    return [];
  }
  return value.filter((item): item is string => typeof item === "string");
}

export function mapLegacyStage(stage5: string): string {
  if (stage5 === "WAKE") {
    return "AWAKE";
  }
  if (stage5 === "N3") {
    return "DEEP";
  }
  if (stage5 === "REM") {
    return "REM";
  }
  return "LIGHT";
}

function normalizeSleepQuality(score: number): string {
  if (score >= 85) {
    return "优秀";
  }
  if (score >= 70) {
    return "良好";
  }
  if (score >= 55) {
    return "一般";
  }
  return "较差";
}

function normalizeInsights(rawInsights: unknown, anomalyScore: number): string[] {
  const insights = toStringArray(rawInsights);
  if (insights.length > 0) {
    return insights;
  }
  if (anomalyScore >= 60) {
    return ["夜间异常波动较多，建议优先恢复与补觉。"];
  }
  if (anomalyScore >= 30) {
    return ["存在轻中度波动，建议降低训练强度并提前入睡。"];
  }
  return ["整体状态稳定，可维持常规作息。"];
}

export function resolveIdempotencyKey(
  explicitKey: string,
  userId: string,
  sleepRecordId: string
): string {
  if (explicitKey.trim()) {
    return explicitKey.trim();
  }
  return `${userId}:${sleepRecordId}`;
}

export async function enqueueNightlyJob(
  client: SupabaseClient,
  userId: string,
  sleepRecordId: string,
  idempotencyKey: string,
  modelVersion = "mmt-v1"
): Promise<JobRow> {
  const { data, error } = await client
    .from("inference_jobs")
    .upsert(
      {
        user_id: userId,
        sleep_record_id: sleepRecordId,
        status: "queued",
        idempotency_key: idempotencyKey,
        model_version: modelVersion,
      },
      { onConflict: "user_id,idempotency_key" }
    )
    .select("id,status,model_version,finished_at,sleep_record_id")
    .single<JobRow>();

  if (error || !data) {
    throw new Error(error?.message ?? "failed to enqueue inference job");
  }

  return data;
}

export async function getLatestAnalysis(
  client: SupabaseClient,
  userId: string,
  sleepRecordId: string
): Promise<AnalysisData | null> {
  const [reportRes, stageRes, anomalyRes] = await Promise.all([
    client
      .from("nightly_reports")
      .select("recovery_score,sleep_quality,insights,model_version")
      .eq("user_id", userId)
      .eq("sleep_record_id", sleepRecordId)
      .maybeSingle<ReportRow>(),
    client
      .from("sleep_stage_results")
      .select("stage_5,stage_legacy,model_version")
      .eq("user_id", userId)
      .eq("sleep_record_id", sleepRecordId)
      .order("epoch_index", { ascending: true })
      .returns<StageRow[]>(),
    client
      .from("anomaly_scores")
      .select("score_0_100,primary_factors,model_version")
      .eq("user_id", userId)
      .eq("sleep_record_id", sleepRecordId)
      .order("created_at", { ascending: false })
      .limit(1)
      .maybeSingle<AnomalyRow>(),
  ]);

  if (reportRes.error) {
    throw new Error(reportRes.error.message);
  }
  if (stageRes.error) {
    throw new Error(stageRes.error.message);
  }
  if (anomalyRes.error) {
    throw new Error(anomalyRes.error.message);
  }

  const report = reportRes.data;
  const stages = stageRes.data ?? [];
  const anomaly = anomalyRes.data;

  if (!report && stages.length === 0 && !anomaly) {
    return null;
  }

  const sleepStages5 = stages.map((row) => row.stage_5);
  const sleepStages =
    stages.length > 0
      ? stages.map((row) => row.stage_legacy || mapLegacyStage(row.stage_5))
      : sleepStages5.map((stage) => mapLegacyStage(stage));
  const anomalyScore = anomaly?.score_0_100 ?? 20;
  const recoveryScore = report?.recovery_score ?? Math.max(0, 100 - anomalyScore);
  const sleepQuality = report?.sleep_quality ?? normalizeSleepQuality(recoveryScore);
  const modelVersion =
    report?.model_version ?? anomaly?.model_version ?? stages[0]?.model_version ?? "mmt-v1";
  const factors = toStringArray(anomaly?.primary_factors);
  const insights = normalizeInsights(report?.insights, anomalyScore);

  return {
    sleepStages,
    sleepStages5,
    recoveryScore,
    sleepQuality,
    insights,
    anomalyScore,
    factors,
    modelVersion,
  };
}

export function toIsoTime(value: unknown): string | null {
  const numberValue = Number(value);
  if (!Number.isFinite(numberValue) || numberValue <= 0) {
    return null;
  }
  return new Date(numberValue).toISOString();
}

export function toInt(value: unknown): number | null {
  const numberValue = Number(value);
  if (!Number.isFinite(numberValue)) {
    return null;
  }
  return Math.trunc(numberValue);
}

export function toObject(value: unknown): JsonMap {
  if (typeof value !== "object" || value === null || Array.isArray(value)) {
    return {};
  }
  return value as JsonMap;
}
