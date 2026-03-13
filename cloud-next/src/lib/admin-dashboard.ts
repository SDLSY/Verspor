import { createServiceClient } from "@/lib/supabase";
import { getAdminJobs, getAdminModels } from "@/lib/admin-system";
import { listAdminPatients } from "@/lib/admin-patients";
import { listRecommendationModelProfiles } from "@/lib/recommendation-model/admin";

type Row = Record<string, unknown>;

function getString(value: unknown): string {
  return typeof value === "string" ? value : "";
}

function getNumber(value: unknown): number | null {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}

export type DashboardSummaryView = {
  patientSummary: {
    totalPatients: number;
    highRiskPatients: number;
    pendingInterventions: number;
    staleSleepReports: number;
    failedJobPatients: number;
    items: Awaited<ReturnType<typeof listAdminPatients>>["items"];
  };
  recommendationSummary: {
    recentTraceCount: number;
    fallbackTraceCount: number;
    activeProfileCode: string;
    configSource: string;
    topRecommendationMode: string | null;
    avgEffectScore: number | null;
    attributionRate: number | null;
  };
  reportSummary: {
    totalReports: number;
    pendingReports: number;
    highRiskReports: number;
    abnormalMetrics: number;
  };
  systemSummary: {
    activeModelVersion: string | null;
    registeredModels: number;
    failedJobs: number;
    runningJobs: number;
    queueJobs: number;
    latestFailedJobs: Awaited<ReturnType<typeof getAdminJobs>>["recentFailed"];
  };
};

export async function getAdminDashboardSummary(): Promise<DashboardSummaryView> {
  const client = createServiceClient();
  const [patients, jobs, models, profiles, recentTracesRes, effectRes, reportRes, metricsRes] =
    await Promise.all([
      listAdminPatients({ page: 1, pageSize: 6, recentSleepDays: 7 }),
      getAdminJobs({ page: 1, pageSize: 6, status: "ALL" }),
      getAdminModels(),
      listRecommendationModelProfiles({ modelCode: "SRM_V2" }).catch(() => []),
      client
        .from("recommendation_traces")
        .select("recommendation_mode:metadata_json->>recommendationMode,is_fallback", { count: "exact" })
        .order("created_at", { ascending: false })
        .limit(120)
        .returns<Row[]>(),
      client
        .from("recommendation_effect_summary_v1")
        .select("execution_count,avg_effect_score,attribution_mode")
        .returns<Row[]>(),
      client
        .from("medical_reports")
        .select("parse_status,risk_level", { count: "exact" })
        .order("created_at", { ascending: false })
        .limit(5000)
        .returns<Row[]>(),
      client
        .from("medical_metrics")
        .select("is_abnormal")
        .eq("is_abnormal", true)
        .limit(5000)
        .returns<Row[]>(),
    ]);

  const recentTraces = recentTracesRes.data ?? [];
  const traceCounts = recentTraces.reduce<Record<string, number>>((acc, row) => {
    const mode = getString(row.recommendation_mode) || "UNSPECIFIED";
    acc[mode] = (acc[mode] ?? 0) + 1;
    return acc;
  }, {});
  const topRecommendationMode = Object.entries(traceCounts).sort((a, b) => b[1] - a[1])[0]?.[0] ?? null;
  const fallbackTraceCount = recentTraces.filter((row) => Boolean(row.is_fallback)).length;

  const activeProfile = profiles.find((item) => item.status === "active") ?? null;
  const effectRows = effectRes.data ?? [];
  const attributedRows = effectRows.filter(
    (row) => getString(row.attribution_mode) === "WINDOW_LAST_DAILY"
  );
  const executionTotal = effectRows.reduce((sum, row) => sum + (getNumber(row.execution_count) ?? 0), 0);
  const attributedTotal = attributedRows.reduce((sum, row) => sum + (getNumber(row.execution_count) ?? 0), 0);
  const weightedEffectSum = effectRows.reduce(
    (sum, row) => sum + (getNumber(row.avg_effect_score) ?? 0) * (getNumber(row.execution_count) ?? 0),
    0
  );

  const reports = reportRes.data ?? [];
  const pendingReports = reports.filter((row) => getString(row.parse_status).toUpperCase() === "PENDING").length;
  const highRiskReports = reports.filter((row) => {
    const risk = getString(row.risk_level).toUpperCase();
    return risk === "HIGH" || risk === "CRITICAL";
  }).length;

  const activeModel = models.items.find((item) => item.isActive) ?? null;

  return {
    patientSummary: {
      ...patients.summary,
      items: patients.items,
    },
    recommendationSummary: {
      recentTraceCount: recentTracesRes.count ?? recentTraces.length,
      fallbackTraceCount,
      activeProfileCode: activeProfile?.profileCode ?? "default_adult_cn",
      configSource: activeProfile?.source ?? "database",
      topRecommendationMode,
      avgEffectScore: executionTotal > 0 ? weightedEffectSum / executionTotal : null,
      attributionRate: executionTotal > 0 ? attributedTotal / executionTotal : null,
    },
    reportSummary: {
      totalReports: reportRes.count ?? reports.length,
      pendingReports,
      highRiskReports,
      abnormalMetrics: metricsRes.data?.length ?? 0,
    },
    systemSummary: {
      activeModelVersion: activeModel?.version ?? null,
      registeredModels: models.items.length,
      failedJobs: jobs.counts.failed ?? 0,
      runningJobs: jobs.counts.running ?? 0,
      queueJobs: jobs.counts.queued ?? 0,
      latestFailedJobs: jobs.recentFailed,
    },
  };
}
