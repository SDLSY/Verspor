import type { User } from "@supabase/supabase-js";
import {
  AdminPatientRiskLevel,
  clampInteger,
  createAdminError,
  getDirectoryUser,
  groupCount,
  latestBy,
  listDirectoryUsers,
  mapToneFromStatus,
  normalizeDisplayName,
  normalizeRiskLevel,
  requireDirectoryUser,
  toStringArray,
  toTimestamp,
} from "@/lib/admin-core";
import {
  formatAuditActionLabel,
  formatBodyZoneLabel,
  formatProtocolTypeLabel,
  formatReportTypeLabel,
  formatResourceTypeLabel,
  formatRiskLabel,
  formatSleepQualityLabel,
  formatStatusLabel,
} from "@/lib/admin-labels";
import { createServiceClient } from "@/lib/supabase";

type Row = Record<string, unknown>;

export const ADMIN_PAGE_SIZE = 20;

export type AdminPatientsQuery = {
  page?: number;
  pageSize?: number;
  q?: string;
  riskLevel?: string;
  pendingOnly?: boolean;
  failedOnly?: boolean;
  recentSleepOnly?: boolean;
  recentSleepDays?: number;
};

export type AdminPatientListItem = {
  userId: string;
  email: string;
  displayName: string;
  lastActiveAt: number | null;
  latestSleepDate: number | null;
  latestRecoveryScore: number | null;
  latestRiskLevel: AdminPatientRiskLevel;
  pendingInterventionCount: number;
  latestJobStatus: string | null;
  latestAbnormalMetricCount: number;
  latestSleepRecordId: string | null;
  latestReportAt: number | null;
  hasRecentFailedJob: boolean;
};

export type AdminPatientsResponse = {
  summary: {
    highRiskPatients: number;
    pendingInterventions: number;
    staleSleepReports: number;
    failedJobPatients: number;
    totalPatients: number;
  };
  pagination: {
    page: number;
    pageSize: number;
    total: number;
    totalPages: number;
  };
  filters: {
    q: string;
    riskLevel: string;
    pendingOnly: boolean;
    failedOnly: boolean;
    recentSleepOnly: boolean;
    recentSleepDays: number;
  };
  items: AdminPatientListItem[];
};

export type AdminPatientIdentity = {
  userId: string;
  email: string;
  displayName: string;
  createdAt: number | null;
};

export type AdminPatientOverview = {
  identity: AdminPatientIdentity;
  latestSleep: {
    sleepRecordId: string | null;
    sessionDate: number | null;
    recoveryScore: number | null;
    sleepQuality: string | null;
    anomalyScore: number | null;
    modelVersion: string | null;
  };
  latestIntervention: {
    pendingCount: number;
    latestExecutionEndedAt: number | null;
    latestExecutionEffectScore: number | null;
    latestExecutionStressDrop: number | null;
  };
  latestMedical: {
    reportId: string | null;
    reportDate: number | null;
    riskLevel: AdminPatientRiskLevel;
    abnormalMetricCount: number;
  };
  latestJob: {
    jobId: string | null;
    status: string | null;
    modelVersion: string | null;
    createdAt: number | null;
    finishedAt: number | null;
  };
  latestFailedJob: {
    jobId: string;
    createdAt: number | null;
    errorMessage: string | null;
  } | null;
};

export type AdminSleepRecord = {
  sleepRecordId: string;
  sessionDate: number;
  totalSleepMinutes: number | null;
  recoveryScore: number | null;
  sleepQuality: string | null;
  anomalyScore: number | null;
  modelVersion: string | null;
  factors: string[];
  insights: string[];
  sleepStages5: string[];
};

export type AdminPatientSleep = {
  records: AdminSleepRecord[];
  trend: Array<{ date: number; recoveryScore: number }>;
};

export type AdminPatientInterventions = {
  tasks: Array<{
    taskId: string;
    taskDate: number | null;
    sourceType: string;
    triggerReason: string | null;
    bodyZone: string;
    protocolType: string;
    durationSec: number;
    plannedAt: number | null;
    status: string;
    updatedAt: number | null;
  }>;
  executions: Array<{
    executionId: string;
    taskId: string;
    startedAt: number | null;
    endedAt: number | null;
    elapsedSec: number;
    beforeStress: number | null;
    afterStress: number | null;
    beforeHr: number | null;
    afterHr: number | null;
    effectScore: number | null;
    completionType: string;
  }>;
};

export type AdminPatientMedical = {
  reports: Array<{
    reportId: string;
    reportDate: number | null;
    reportType: string;
    parseStatus: string;
    riskLevel: AdminPatientRiskLevel;
  }>;
  latestReport: {
    reportId: string;
    reportDate: number | null;
    reportType: string;
    parseStatus: string;
    riskLevel: AdminPatientRiskLevel;
  } | null;
  latestMetrics: Array<{
    metricCode: string;
    metricName: string;
    metricValue: number;
    unit: string;
    refLow: number | null;
    refHigh: number | null;
    isAbnormal: boolean;
    confidence: number;
  }>;
};

export type AdminTimelineEvent = {
  id: string;
  type:
    | "sleep_session"
    | "inference_job"
    | "nightly_report"
    | "intervention_task"
    | "intervention_execution"
    | "medical_report"
    | "medication_analysis"
    | "food_analysis"
    | "audit_event";
  occurredAt: number;
  title: string;
  description: string;
  tone: "neutral" | "success" | "warning" | "danger" | "info";
};

export type AdminPatientTimeline = {
  events: AdminTimelineEvent[];
};

function getString(value: unknown): string {
  return typeof value === "string" ? value : "";
}

function getNumber(value: unknown): number | null {
  const numberValue = Number(value);
  return Number.isFinite(numberValue) ? numberValue : null;
}

function isMissingRelationMessage(message: string | null | undefined): boolean {
  const normalized = (message ?? "").toLowerCase();
  return (
    normalized.includes("could not find the table") ||
    normalized.includes("schema cache") ||
    normalized.includes("does not exist")
  );
}

type QueryResult<T> = PromiseLike<{
  data: T | null;
  error: { message: string } | null;
}>;

async function resolveOptionalData<T>(query: QueryResult<T>, fallback: T): Promise<T> {
  const { data, error } = await query;
  if (error) {
    if (isMissingRelationMessage(error.message)) {
      return fallback;
    }
    throw createAdminError(error.message);
  }
  return data ?? fallback;
}

function buildIdentity(user: User): AdminPatientIdentity {
  return {
    userId: user.id,
    email: user.email ?? "",
    displayName: normalizeDisplayName(user),
    createdAt: toTimestamp(user.created_at ?? null),
  };
}

async function fetchRows(
  table: string,
  select: string,
  userIds: string[],
  orderBy?: string,
  ascending = false,
  optional = false
): Promise<Row[]> {
  if (userIds.length === 0) {
    return [];
  }
  const client = createServiceClient();
  let query = client.from(table).select(select).in("user_id", userIds).limit(5000);
  if (orderBy) {
    query = query.order(orderBy, { ascending });
  }
  const { data, error } = await query.returns<Row[]>();
  if (error) {
    if (optional && isMissingRelationMessage(error.message)) {
      return [];
    }
    throw createAdminError(error.message);
  }
  return data ?? [];
}

export async function listAdminPatients(query: AdminPatientsQuery = {}): Promise<AdminPatientsResponse> {
  const page = clampInteger(query.page, 1, 1, 9999);
  const pageSize = clampInteger(query.pageSize, ADMIN_PAGE_SIZE, 1, 50);
  const recentSleepDays = clampInteger(query.recentSleepDays, 7, 1, 30);
  const q = (query.q ?? "").trim().toLowerCase();
  const riskLevel = (query.riskLevel ?? "ALL").trim().toUpperCase();
  const pendingOnly = Boolean(query.pendingOnly);
  const failedOnly = Boolean(query.failedOnly);
  const recentSleepOnly = Boolean(query.recentSleepOnly);

  const users = await listDirectoryUsers();
  const userIds = users.map((user) => user.id);
  const [sleepSessions, nightlyReports, anomalies, jobs, tasks, medicalReports, medicalMetrics] = await Promise.all([
    fetchRows("sleep_sessions", "user_id,sleep_record_id,session_date,total_sleep_minutes", userIds, "session_date"),
    fetchRows("nightly_reports", "user_id,sleep_record_id,recovery_score,sleep_quality,insights,model_version,created_at,updated_at", userIds, "created_at"),
    fetchRows("anomaly_scores", "user_id,sleep_record_id,score_0_100,primary_factors,model_version,created_at", userIds, "created_at"),
    fetchRows("inference_jobs", "id,user_id,sleep_record_id,status,model_version,error_message,created_at,started_at,finished_at", userIds, "created_at"),
    fetchRows("intervention_tasks", "user_id,task_id,task_date,source_type,trigger_reason,body_zone,protocol_type,duration_sec,planned_at,status,updated_at,created_at", userIds, "planned_at", false, true),
    fetchRows("medical_reports", "user_id,report_id,report_date,report_type,parse_status,risk_level,created_at", userIds, "report_date", false, true),
    fetchRows("medical_metrics", "user_id,report_id,metric_code,metric_name,metric_value,unit,ref_low,ref_high,is_abnormal,confidence", userIds, undefined, false, true),
  ]);

  const latestSleepByUser = latestBy(sleepSessions, (row) => getString(row.user_id), (row) => toTimestamp(getString(row.session_date)));
  const latestNightlyByUser = latestBy(nightlyReports, (row) => getString(row.user_id), (row) => toTimestamp(getString(row.created_at)) ?? toTimestamp(getString(row.updated_at)));
  const latestAnomalyByUser = latestBy(anomalies, (row) => getString(row.user_id), (row) => toTimestamp(getString(row.created_at)));
  const latestJobByUser = latestBy(jobs, (row) => getString(row.user_id), (row) => toTimestamp(getString(row.created_at)));
  const latestMedicalByUser = latestBy(medicalReports, (row) => getString(row.user_id), (row) => toTimestamp(getString(row.report_date)));
  const pendingTaskCountByUser = groupCount(tasks, (row) => getString(row.user_id), (row) => {
    const status = getString(row.status).trim().toUpperCase();
    return status === "PENDING" || status === "RUNNING";
  });
  const abnormalMetricCountByReport = groupCount(medicalMetrics, (row) => `${getString(row.user_id)}:${getString(row.report_id)}`, (row) => Boolean(row.is_abnormal));
  const recentFailedJobUsers = new Set(
    jobs
      .filter((row) => {
        const createdAt = toTimestamp(getString(row.created_at)) ?? 0;
        return getString(row.status).trim().toUpperCase() === "FAILED" && createdAt >= Date.now() - 24 * 60 * 60 * 1000;
      })
      .map((row) => getString(row.user_id))
  );
  const recentSleepThreshold = Date.now() - recentSleepDays * 24 * 60 * 60 * 1000;

  const items = users.map<AdminPatientListItem>((user) => {
    const latestSleep = latestSleepByUser.get(user.id);
    const latestNightly = latestNightlyByUser.get(user.id);
    const latestAnomaly = latestAnomalyByUser.get(user.id);
    const latestJob = latestJobByUser.get(user.id);
    const latestMedical = latestMedicalByUser.get(user.id);
    const latestRecoveryScore =
      getNumber(latestNightly?.recovery_score) ??
      (typeof getNumber(latestAnomaly?.score_0_100) === "number" ? 100 - (getNumber(latestAnomaly?.score_0_100) ?? 0) : null);
    const latestRiskLevel = normalizeRiskLevel(
      getString(latestMedical?.risk_level),
      getNumber(latestAnomaly?.score_0_100),
      latestRecoveryScore
    );
    const latestMedicalKey = latestMedical ? `${getString(latestMedical.user_id)}:${getString(latestMedical.report_id)}` : "";
    const lastActiveAt = Math.max(
      toTimestamp(getString(latestSleep?.session_date)) ?? 0,
      toTimestamp(getString(latestNightly?.created_at)) ?? 0,
      toTimestamp(getString(latestJob?.created_at)) ?? 0,
      toTimestamp(getString(latestMedical?.report_date)) ?? 0
    );

    return {
      userId: user.id,
      email: user.email ?? "",
      displayName: normalizeDisplayName(user),
      lastActiveAt: lastActiveAt > 0 ? lastActiveAt : null,
      latestSleepDate: toTimestamp(getString(latestSleep?.session_date)),
      latestRecoveryScore,
      latestRiskLevel,
      pendingInterventionCount: pendingTaskCountByUser.get(user.id) ?? 0,
      latestJobStatus: getString(latestJob?.status) || null,
      latestAbnormalMetricCount: abnormalMetricCountByReport.get(latestMedicalKey) ?? 0,
      latestSleepRecordId: getString(latestSleep?.sleep_record_id) || null,
      latestReportAt: toTimestamp(getString(latestNightly?.created_at)),
      hasRecentFailedJob: recentFailedJobUsers.has(user.id),
    };
  });

  const filtered = items.filter((item) => {
    if (q) {
      const haystack = `${item.userId} ${item.email} ${item.displayName}`.toLowerCase();
      if (!haystack.includes(q)) {
        return false;
      }
    }
    if (riskLevel !== "ALL" && item.latestRiskLevel !== riskLevel) {
      return false;
    }
    if (pendingOnly && item.pendingInterventionCount <= 0) {
      return false;
    }
    if (failedOnly && !item.hasRecentFailedJob) {
      return false;
    }
    if (recentSleepOnly && (item.latestSleepDate ?? 0) < recentSleepThreshold) {
      return false;
    }
    return true;
  });

  filtered.sort((left, right) => (right.lastActiveAt ?? 0) - (left.lastActiveAt ?? 0));

  const summary = filtered.reduce(
    (acc, item) => {
      if (item.latestRiskLevel === "HIGH") {
        acc.highRiskPatients += 1;
      }
      acc.pendingInterventions += item.pendingInterventionCount;
      if (item.latestSleepDate && (!item.latestReportAt || item.latestReportAt < Date.now() - 24 * 60 * 60 * 1000)) {
        acc.staleSleepReports += 1;
      }
      if (item.hasRecentFailedJob) {
        acc.failedJobPatients += 1;
      }
      acc.totalPatients += 1;
      return acc;
    },
    { highRiskPatients: 0, pendingInterventions: 0, staleSleepReports: 0, failedJobPatients: 0, totalPatients: 0 }
  );

  const total = filtered.length;
  const totalPages = Math.max(1, Math.ceil(total / pageSize));
  const safePage = Math.min(page, totalPages);
  const startIndex = (safePage - 1) * pageSize;

  return {
    summary,
    pagination: { page: safePage, pageSize, total, totalPages },
    filters: { q: query.q ?? "", riskLevel, pendingOnly, failedOnly, recentSleepOnly, recentSleepDays },
    items: filtered.slice(startIndex, startIndex + pageSize),
  };
}

export async function getAdminPatientOverview(userId: string): Promise<AdminPatientOverview> {
  const user = requireDirectoryUser(await getDirectoryUser(userId), userId);
  const client = createServiceClient();
  const [sleepRes, reportRes, anomalyRes, jobRes] = await Promise.all([
    client.from("sleep_sessions").select("user_id,sleep_record_id,session_date,total_sleep_minutes").eq("user_id", userId).order("session_date", { ascending: false }).limit(1).maybeSingle<Row>(),
    client.from("nightly_reports").select("user_id,sleep_record_id,recovery_score,sleep_quality,insights,model_version,created_at,updated_at").eq("user_id", userId).order("created_at", { ascending: false }).limit(1).maybeSingle<Row>(),
    client.from("anomaly_scores").select("user_id,sleep_record_id,score_0_100,primary_factors,model_version,created_at").eq("user_id", userId).order("created_at", { ascending: false }).limit(1).maybeSingle<Row>(),
    client.from("inference_jobs").select("id,user_id,status,model_version,error_message,created_at,finished_at").eq("user_id", userId).order("created_at", { ascending: false }).limit(20).returns<Row[]>(),
  ]);
  if (sleepRes.error || reportRes.error || anomalyRes.error || jobRes.error) {
    throw createAdminError(
      sleepRes.error?.message ??
        reportRes.error?.message ??
        anomalyRes.error?.message ??
        jobRes.error?.message ??
        "加载患者概览失败"
    );
  }

  const [taskRows, latestExecution, latestMedical] = await Promise.all([
    resolveOptionalData(
      client
        .from("intervention_tasks")
        .select("user_id,task_id,status,planned_at")
        .eq("user_id", userId)
        .order("planned_at", { ascending: false })
        .limit(50)
        .returns<Row[]>(),
      []
    ),
    resolveOptionalData(
      client
        .from("intervention_executions")
        .select("user_id,execution_id,started_at,ended_at,effect_score,before_stress,after_stress")
        .eq("user_id", userId)
        .order("ended_at", { ascending: false })
        .limit(1)
        .maybeSingle<Row>(),
      null
    ),
    resolveOptionalData(
      client
        .from("medical_reports")
        .select("user_id,report_id,report_date,risk_level")
        .eq("user_id", userId)
        .order("report_date", { ascending: false })
        .limit(1)
        .maybeSingle<Row>(),
      null
    ),
  ]);

  const latestSleep = sleepRes.data ?? null;
  const latestReport = reportRes.data ?? null;
  const latestAnomaly = anomalyRes.data ?? null;
  const jobs = jobRes.data ?? [];
  const latestJob = jobs[0] ?? null;
  const latestFailedJob = jobs.find((row) => getString(row.status).trim().toUpperCase() === "FAILED") ?? null;

  let abnormalMetricCount = 0;
  if (latestMedical) {
    const { count, error } = await client
      .from("medical_metrics")
      .select("*", { count: "exact", head: true })
      .eq("user_id", userId)
      .eq("report_id", getString(latestMedical.report_id))
      .eq("is_abnormal", true);
    if (error) {
      if (!isMissingRelationMessage(error.message)) {
        throw createAdminError(error.message);
      }
    } else {
      abnormalMetricCount = count ?? 0;
    }
  }

  const pendingCount = taskRows.filter((row) => {
    const status = getString(row.status).trim().toUpperCase();
    return status === "PENDING" || status === "RUNNING";
  }).length;
  const latestRecoveryScore = getNumber(latestReport?.recovery_score) ?? (typeof getNumber(latestAnomaly?.score_0_100) === "number" ? 100 - (getNumber(latestAnomaly?.score_0_100) ?? 0) : null);
  const latestRiskLevel = normalizeRiskLevel(getString(latestMedical?.risk_level), getNumber(latestAnomaly?.score_0_100), latestRecoveryScore);
  const stressDrop = latestExecution && getNumber(latestExecution.before_stress) != null && getNumber(latestExecution.after_stress) != null ? (getNumber(latestExecution.before_stress) ?? 0) - (getNumber(latestExecution.after_stress) ?? 0) : null;

  return {
    identity: buildIdentity(user),
    latestSleep: {
      sleepRecordId: getString(latestSleep?.sleep_record_id) || getString(latestReport?.sleep_record_id) || getString(latestAnomaly?.sleep_record_id) || null,
      sessionDate: toTimestamp(getString(latestSleep?.session_date)),
      recoveryScore: latestRecoveryScore,
      sleepQuality: getString(latestReport?.sleep_quality) || null,
      anomalyScore: getNumber(latestAnomaly?.score_0_100),
      modelVersion: getString(latestReport?.model_version) || getString(latestAnomaly?.model_version) || null,
    },
    latestIntervention: {
      pendingCount,
      latestExecutionEndedAt: toTimestamp(getString(latestExecution?.ended_at)),
      latestExecutionEffectScore: getNumber(latestExecution?.effect_score),
      latestExecutionStressDrop: stressDrop,
    },
    latestMedical: {
      reportId: getString(latestMedical?.report_id) || null,
      reportDate: toTimestamp(getString(latestMedical?.report_date)),
      riskLevel: latestRiskLevel,
      abnormalMetricCount,
    },
    latestJob: {
      jobId: getString(latestJob?.id) || null,
      status: getString(latestJob?.status) || null,
      modelVersion: getString(latestJob?.model_version) || null,
      createdAt: toTimestamp(getString(latestJob?.created_at)),
      finishedAt: toTimestamp(getString(latestJob?.finished_at)),
    },
    latestFailedJob: latestFailedJob
      ? {
          jobId: getString(latestFailedJob.id),
          createdAt: toTimestamp(getString(latestFailedJob.created_at)),
          errorMessage: getString(latestFailedJob.error_message) || null,
        }
      : null,
  };
}

export async function getAdminPatientSleep(userId: string): Promise<AdminPatientSleep> {
  requireDirectoryUser(await getDirectoryUser(userId), userId);
  const client = createServiceClient();
  const since = new Date(Date.now() - 30 * 24 * 60 * 60 * 1000).toISOString();
  const { data: sessions, error: sessionError } = await client.from("sleep_sessions").select("user_id,sleep_record_id,session_date,total_sleep_minutes").eq("user_id", userId).gte("session_date", since).order("session_date", { ascending: false }).limit(30).returns<Row[]>();
  if (sessionError) {
    throw createAdminError(sessionError.message);
  }
  const sessionRows = sessions ?? [];
  if (sessionRows.length === 0) {
    return { records: [], trend: [] };
  }
  const recordIds = sessionRows.map((row) => getString(row.sleep_record_id));
  const [reportRes, anomalyRes, stageRes] = await Promise.all([
    client.from("nightly_reports").select("user_id,sleep_record_id,recovery_score,sleep_quality,insights,model_version,created_at,updated_at").eq("user_id", userId).in("sleep_record_id", recordIds).returns<Row[]>(),
    client.from("anomaly_scores").select("user_id,sleep_record_id,score_0_100,primary_factors,model_version,created_at").eq("user_id", userId).in("sleep_record_id", recordIds).returns<Row[]>(),
    client.from("sleep_stage_results").select("user_id,sleep_record_id,epoch_index,stage_5,created_at").eq("user_id", userId).in("sleep_record_id", recordIds).order("epoch_index", { ascending: true }).returns<Row[]>(),
  ]);
  if (reportRes.error || anomalyRes.error || stageRes.error) {
    throw createAdminError(
      reportRes.error?.message ?? anomalyRes.error?.message ?? stageRes.error?.message ?? "加载睡眠详情失败"
    );
  }

  const reportsByRecord = latestBy(reportRes.data ?? [], (row) => getString(row.sleep_record_id), (row) => toTimestamp(getString(row.created_at)));
  const anomaliesByRecord = latestBy(anomalyRes.data ?? [], (row) => getString(row.sleep_record_id), (row) => toTimestamp(getString(row.created_at)));
  const stagesByRecord = new Map<string, string[]>();
  (stageRes.data ?? []).forEach((row) => {
    const key = getString(row.sleep_record_id);
    const values = stagesByRecord.get(key) ?? [];
    values.push(getString(row.stage_5));
    stagesByRecord.set(key, values);
  });

  const records = sessionRows.map<AdminSleepRecord>((session) => {
    const key = getString(session.sleep_record_id);
    const report = reportsByRecord.get(key);
    const anomaly = anomaliesByRecord.get(key);
    const recoveryScore = getNumber(report?.recovery_score) ?? (typeof getNumber(anomaly?.score_0_100) === "number" ? 100 - (getNumber(anomaly?.score_0_100) ?? 0) : null);
    return {
      sleepRecordId: key,
      sessionDate: toTimestamp(getString(session.session_date)) ?? Date.now(),
      totalSleepMinutes: getNumber(session.total_sleep_minutes),
      recoveryScore,
      sleepQuality: getString(report?.sleep_quality) || null,
      anomalyScore: getNumber(anomaly?.score_0_100),
      modelVersion: getString(report?.model_version) || getString(anomaly?.model_version) || null,
      factors: toStringArray(anomaly?.primary_factors),
      insights: toStringArray(report?.insights),
      sleepStages5: stagesByRecord.get(key) ?? [],
    };
  });

  return {
    records,
    trend: records
      .filter((record): record is AdminSleepRecord & { recoveryScore: number } => typeof record.recoveryScore === "number")
      .map((record) => ({ date: record.sessionDate, recoveryScore: record.recoveryScore }))
      .sort((left, right) => left.date - right.date),
  };
}

export async function getAdminPatientInterventions(userId: string): Promise<AdminPatientInterventions> {
  requireDirectoryUser(await getDirectoryUser(userId), userId);
  const client = createServiceClient();
  const [taskRows, executionRows] = await Promise.all([
    resolveOptionalData(
      client
        .from("intervention_tasks")
        .select("task_id,task_date,source_type,trigger_reason,body_zone,protocol_type,duration_sec,planned_at,status,updated_at")
        .eq("user_id", userId)
        .order("planned_at", { ascending: false })
        .limit(100)
        .returns<Row[]>(),
      []
    ),
    resolveOptionalData(
      client
        .from("intervention_executions")
        .select("execution_id,task_id,started_at,ended_at,elapsed_sec,before_stress,after_stress,before_hr,after_hr,effect_score,completion_type")
        .eq("user_id", userId)
        .order("started_at", { ascending: false })
        .limit(100)
        .returns<Row[]>(),
      []
    ),
  ]);
  return {
    tasks: taskRows.map((row) => ({
      taskId: getString(row.task_id),
      taskDate: toTimestamp(getString(row.task_date)),
      sourceType: getString(row.source_type),
      triggerReason: getString(row.trigger_reason) || null,
      bodyZone: getString(row.body_zone),
      protocolType: getString(row.protocol_type),
      durationSec: getNumber(row.duration_sec) ?? 0,
      plannedAt: toTimestamp(getString(row.planned_at)),
      status: getString(row.status),
      updatedAt: toTimestamp(getString(row.updated_at)),
    })),
    executions: executionRows.map((row) => ({
      executionId: getString(row.execution_id),
      taskId: getString(row.task_id),
      startedAt: toTimestamp(getString(row.started_at)),
      endedAt: toTimestamp(getString(row.ended_at)),
      elapsedSec: getNumber(row.elapsed_sec) ?? 0,
      beforeStress: getNumber(row.before_stress),
      afterStress: getNumber(row.after_stress),
      beforeHr: getNumber(row.before_hr),
      afterHr: getNumber(row.after_hr),
      effectScore: getNumber(row.effect_score),
      completionType: getString(row.completion_type),
    })),
  };
}

export async function getAdminPatientMedical(userId: string): Promise<AdminPatientMedical> {
  requireDirectoryUser(await getDirectoryUser(userId), userId);
  const client = createServiceClient();
  const reportRows = await resolveOptionalData(
    client
      .from("medical_reports")
      .select("report_id,report_date,report_type,parse_status,risk_level")
      .eq("user_id", userId)
      .order("report_date", { ascending: false })
      .limit(30)
      .returns<Row[]>(),
    []
  );
  const latestReport = reportRows[0] ?? null;
  let latestMetrics: Row[] = [];
  if (latestReport) {
    latestMetrics = await resolveOptionalData(
      client
        .from("medical_metrics")
        .select("metric_code,metric_name,metric_value,unit,ref_low,ref_high,is_abnormal,confidence")
        .eq("user_id", userId)
        .eq("report_id", getString(latestReport.report_id))
        .order("is_abnormal", { ascending: false })
        .order("metric_name", { ascending: true })
        .returns<Row[]>(),
      []
    );
  }
  return {
    reports: reportRows.map((row) => ({
      reportId: getString(row.report_id),
      reportDate: toTimestamp(getString(row.report_date)),
      reportType: getString(row.report_type),
      parseStatus: getString(row.parse_status),
      riskLevel: normalizeRiskLevel(getString(row.risk_level), null, null),
    })),
    latestReport: latestReport
      ? {
          reportId: getString(latestReport.report_id),
          reportDate: toTimestamp(getString(latestReport.report_date)),
          reportType: getString(latestReport.report_type),
          parseStatus: getString(latestReport.parse_status),
          riskLevel: normalizeRiskLevel(getString(latestReport.risk_level), null, null),
        }
      : null,
    latestMetrics: latestMetrics.map((row) => ({
      metricCode: getString(row.metric_code),
      metricName: getString(row.metric_name),
      metricValue: getNumber(row.metric_value) ?? 0,
      unit: getString(row.unit),
      refLow: getNumber(row.ref_low),
      refHigh: getNumber(row.ref_high),
      isAbnormal: Boolean(row.is_abnormal),
      confidence: getNumber(row.confidence) ?? 0,
    })),
  };
}

export async function getAdminPatientTimeline(userId: string, filters: { types?: string[] } = {}): Promise<AdminPatientTimeline> {
  requireDirectoryUser(await getDirectoryUser(userId), userId);
  const client = createServiceClient();
  const [sleepRes, jobRes, reportRes] = await Promise.all([
    client.from("sleep_sessions").select("sleep_record_id,session_date,total_sleep_minutes").eq("user_id", userId).order("session_date", { ascending: false }).limit(20).returns<Row[]>(),
    client.from("inference_jobs").select("id,sleep_record_id,status,error_message,created_at").eq("user_id", userId).order("created_at", { ascending: false }).limit(20).returns<Row[]>(),
    client.from("nightly_reports").select("sleep_record_id,recovery_score,sleep_quality,created_at").eq("user_id", userId).order("created_at", { ascending: false }).limit(20).returns<Row[]>(),
  ]);
  if (sleepRes.error || jobRes.error || reportRes.error) {
    throw createAdminError(
      sleepRes.error?.message ?? jobRes.error?.message ?? reportRes.error?.message ?? "加载患者时间线失败"
    );
  }

  const [taskRows, executionRows, medicalRows, medicationRows, foodRows, auditRows] = await Promise.all([
    resolveOptionalData(
      client
        .from("intervention_tasks")
        .select("task_id,protocol_type,body_zone,duration_sec,status,planned_at")
        .eq("user_id", userId)
        .order("planned_at", { ascending: false })
        .limit(20)
        .returns<Row[]>(),
      []
    ),
    resolveOptionalData(
      client
        .from("intervention_executions")
        .select("execution_id,task_id,effect_score,started_at,ended_at")
        .eq("user_id", userId)
        .order("started_at", { ascending: false })
        .limit(20)
        .returns<Row[]>(),
      []
    ),
    resolveOptionalData(
      client
        .from("medical_reports")
        .select("report_id,report_type,risk_level,report_date")
        .eq("user_id", userId)
        .order("report_date", { ascending: false })
        .limit(20)
        .returns<Row[]>(),
      []
    ),
    resolveOptionalData(
      client
        .from("medication_analysis_records")
        .select("record_id,recognized_name,risk_level,requires_manual_review,captured_at")
        .eq("user_id", userId)
        .order("captured_at", { ascending: false })
        .limit(20)
        .returns<Row[]>(),
      []
    ),
    resolveOptionalData(
      client
        .from("food_analysis_records")
        .select("record_id,meal_type,estimated_calories,nutrition_risk_level,captured_at")
        .eq("user_id", userId)
        .order("captured_at", { ascending: false })
        .limit(20)
        .returns<Row[]>(),
      []
    ),
    resolveOptionalData(
      client
        .from("audit_events")
        .select("id,actor,action,resource_type,resource_id,created_at")
        .eq("user_id", userId)
        .order("created_at", { ascending: false })
        .limit(40)
        .returns<Row[]>(),
      []
    ),
  ]);

  const events: AdminTimelineEvent[] = [];
  (sleepRes.data ?? []).forEach((row) => {
    const occurredAt = toTimestamp(getString(row.session_date));
    if (!occurredAt) return;
    events.push({
      id: `sleep:${getString(row.sleep_record_id)}`,
      type: "sleep_session",
      occurredAt,
      title: "已上传睡眠会话",
      description: `记录 ${getString(row.sleep_record_id)}，总睡眠 ${getNumber(row.total_sleep_minutes) ?? 0} 分钟。`,
      tone: "info",
    });
  });
  (jobRes.data ?? []).forEach((row) => {
    const occurredAt = toTimestamp(getString(row.created_at));
    if (!occurredAt) return;
    events.push({
      id: `job:${getString(row.id)}`,
      type: "inference_job",
      occurredAt,
      title: `推理作业${formatStatusLabel(getString(row.status))}`,
      description: getString(row.error_message) || `睡眠记录 ${getString(row.sleep_record_id)} 的推理任务。`,
      tone: mapToneFromStatus(getString(row.status)),
    });
  });
  (reportRes.data ?? []).forEach((row) => {
    const occurredAt = toTimestamp(getString(row.created_at));
    if (!occurredAt) return;
    events.push({
      id: `nightly:${getString(row.sleep_record_id)}`,
      type: "nightly_report",
      occurredAt,
      title: "已生成夜间报告",
      description: `恢复分 ${getNumber(row.recovery_score) ?? 0}，睡眠质量 ${formatSleepQualityLabel(getString(row.sleep_quality) || "UNKNOWN")}。`,
      tone: "success",
    });
  });
  taskRows.forEach((row) => {
    const occurredAt = toTimestamp(getString(row.planned_at));
    if (!occurredAt) return;
    events.push({
      id: `task:${getString(row.task_id)}`,
      type: "intervention_task",
      occurredAt,
      title: `干预任务${formatStatusLabel(getString(row.status))}`,
      description: `${formatProtocolTypeLabel(getString(row.protocol_type))} / ${formatBodyZoneLabel(getString(row.body_zone))} / ${getNumber(row.duration_sec) ?? 0} 秒`,
      tone: mapToneFromStatus(getString(row.status)),
    });
  });
  executionRows.forEach((row) => {
    const occurredAt = toTimestamp(getString(row.ended_at)) ?? toTimestamp(getString(row.started_at));
    if (!occurredAt) return;
    events.push({
      id: `execution:${getString(row.execution_id)}`,
      type: "intervention_execution",
      occurredAt,
      title: "已完成干预执行",
      description: `任务 ${getString(row.task_id)}，效果分 ${getNumber(row.effect_score) ?? 0}。`,
      tone: "success",
    });
  });
  medicalRows.forEach((row) => {
    const occurredAt = toTimestamp(getString(row.report_date));
    if (!occurredAt) return;
    events.push({
      id: `medical:${getString(row.report_id)}`,
      type: "medical_report",
      occurredAt,
      title: "已导入医疗报告",
      description: `${formatReportTypeLabel(getString(row.report_type))} / ${formatRiskLabel(getString(row.risk_level))}`,
      tone: getString(row.risk_level).trim().toUpperCase() === "HIGH" ? "warning" : "info",
    });
  });
  medicationRows.forEach((row) => {
    const occurredAt = toTimestamp(getString(row.captured_at));
    if (!occurredAt) return;
    const requiresManualReview = Boolean(row.requires_manual_review);
    const riskLevel = getString(row.risk_level).trim().toUpperCase();
    events.push({
      id: `medication:${getString(row.record_id)}`,
      type: "medication_analysis",
      occurredAt,
      title: "已完成药物识别",
      description: `${getString(row.recognized_name) || "待确认药物"} / ${requiresManualReview ? "需人工确认" : formatRiskLabel(riskLevel)}`,
      tone: requiresManualReview || riskLevel === "HIGH" ? "warning" : "info",
    });
  });
  foodRows.forEach((row) => {
    const occurredAt = toTimestamp(getString(row.captured_at));
    if (!occurredAt) return;
    const riskLevel = getString(row.nutrition_risk_level).trim().toUpperCase();
    events.push({
      id: `food:${getString(row.record_id)}`,
      type: "food_analysis",
      occurredAt,
      title: "已完成饮食分析",
      description: `${getString(row.meal_type) || "UNSPECIFIED"} / ${getNumber(row.estimated_calories) ?? 0} kcal / ${formatRiskLabel(riskLevel)}`,
      tone: riskLevel === "HIGH" ? "warning" : "success",
    });
  });
  auditRows.forEach((row) => {
    const occurredAt = toTimestamp(getString(row.created_at));
    if (!occurredAt) return;
    events.push({
      id: `audit:${getString(row.id)}`,
      type: "audit_event",
      occurredAt,
      title: `${getString(row.actor)} ${formatAuditActionLabel(getString(row.action))}`,
      description: `${formatResourceTypeLabel(getString(row.resource_type))}${getString(row.resource_id) ? ` / ${getString(row.resource_id)}` : ""}`,
      tone: "neutral",
    });
  });

  const typeFilter = new Set((filters.types ?? []).map((value) => value.trim()).filter(Boolean));
  return {
    events: events.filter((event) => typeFilter.size === 0 || typeFilter.has(event.type)).sort((left, right) => right.occurredAt - left.occurredAt),
  };
}
