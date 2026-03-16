import { listDirectoryUsers, normalizeDisplayName, toTimestamp } from "@/lib/admin-core";
import { getAdminScenarioInfo } from "@/lib/admin-story";
import { createServiceClient } from "@/lib/supabase";

type Row = Record<string, unknown>;

export type AdminReportsQuery = {
  page?: number;
  pageSize?: number;
  q?: string;
  riskLevel?: string;
  parseStatus?: string;
};

export type AdminReportsView = {
  summary: {
    totalReports: number;
    pendingReports: number;
    highRiskReports: number;
    abnormalMetrics: number;
  };
  filters: {
    q: string;
    riskLevel: string;
    parseStatus: string;
  };
  pagination: {
    page: number;
    pageSize: number;
    total: number;
    totalPages: number;
  };
  items: Array<{
    reportId: string;
    userId: string;
    email: string;
    displayName: string;
    scenarioCode: string | null;
    scenarioLabel: string | null;
    reportDate: number | null;
    reportType: string;
    parseStatus: string;
    riskLevel: string | null;
    abnormalMetricCount: number;
    latestDoctorSummary: string | null;
    latestRecommendedDepartment: string | null;
  }>;
};

function clampInteger(value: number | undefined, fallback: number, low: number, high: number): number {
  if (!Number.isFinite(value ?? NaN)) {
    return fallback;
  }
  return Math.max(low, Math.min(high, Math.trunc(value as number)));
}

function getString(value: unknown): string {
  return typeof value === "string" ? value : "";
}

function latestBy<T extends Row>(
  rows: T[],
  keyResolver: (row: T) => string,
  timestampResolver: (row: T) => number | null
): Map<string, T> {
  const map = new Map<string, T>();
  rows.forEach((row) => {
    const key = keyResolver(row);
    const current = map.get(key);
    const nextTime = timestampResolver(row) ?? 0;
    const currentTime = current ? timestampResolver(current) ?? 0 : 0;
    if (!current || nextTime >= currentTime) {
      map.set(key, row);
    }
  });
  return map;
}

function groupCount(rows: Row[], keyResolver: (row: Row) => string): Map<string, number> {
  const map = new Map<string, number>();
  rows.forEach((row) => {
    const key = keyResolver(row);
    map.set(key, (map.get(key) ?? 0) + 1);
  });
  return map;
}

export async function listAdminReports(query: AdminReportsQuery = {}): Promise<AdminReportsView> {
  const client = createServiceClient();
  const page = clampInteger(query.page, 1, 1, 9999);
  const pageSize = clampInteger(query.pageSize, 20, 1, 50);
  const q = (query.q ?? "").trim().toLowerCase();
  const riskLevel = (query.riskLevel ?? "ALL").trim().toUpperCase();
  const parseStatus = (query.parseStatus ?? "ALL").trim().toUpperCase();

  const [users, reportRes, metricsRes, doctorRes] = await Promise.all([
    listDirectoryUsers(600),
    client
      .from("medical_reports")
      .select("user_id,report_id,report_date,report_type,parse_status,risk_level,created_at")
      .order("report_date", { ascending: false })
      .limit(2000)
      .returns<Row[]>(),
    client
      .from("medical_metrics")
      .select("user_id,report_id,is_abnormal")
      .eq("is_abnormal", true)
      .limit(5000)
      .returns<Row[]>(),
    client
      .from("doctor_inquiry_summaries")
      .select("user_id,doctor_summary,recommended_department,assessed_at")
      .order("assessed_at", { ascending: false })
      .limit(1000)
      .returns<Row[]>(),
  ]);

  if (reportRes.error || metricsRes.error || doctorRes.error) {
    throw new Error(
      reportRes.error?.message ??
        metricsRes.error?.message ??
        doctorRes.error?.message ??
        "加载报告工作台失败"
    );
  }

  const directory = new Map(
    users.map((user) => [user.id, { email: user.email ?? "", displayName: normalizeDisplayName(user) }])
  );
  const abnormalCounts = groupCount(metricsRes.data ?? [], (row) => `${getString(row.user_id)}:${getString(row.report_id)}`);
  const latestDoctorByUser = latestBy(
    doctorRes.data ?? [],
    (row) => getString(row.user_id),
    (row) => toTimestamp(getString(row.assessed_at))
  );

  const items = (reportRes.data ?? [])
    .map((row) => {
      const userId = getString(row.user_id);
      const userInfo = directory.get(userId) ?? { email: "", displayName: userId.slice(0, 8) };
      const doctorSummary = latestDoctorByUser.get(userId);
      const scenarioInfo = getAdminScenarioInfo({ email: userInfo.email, user_metadata: null });
      return {
        reportId: getString(row.report_id),
        userId,
        email: userInfo.email,
        displayName: userInfo.displayName,
        scenarioCode: scenarioInfo.scenarioCode,
        scenarioLabel: scenarioInfo.scenarioLabel,
        reportDate: toTimestamp(getString(row.report_date)),
        reportType: getString(row.report_type),
        parseStatus: getString(row.parse_status),
        riskLevel: getString(row.risk_level) || null,
        abnormalMetricCount: abnormalCounts.get(`${userId}:${getString(row.report_id)}`) ?? 0,
        latestDoctorSummary: getString(doctorSummary?.doctor_summary) || null,
        latestRecommendedDepartment: getString(doctorSummary?.recommended_department) || null,
      };
    })
    .filter((item) => {
      if (riskLevel !== "ALL" && (item.riskLevel ?? "").toUpperCase() !== riskLevel) {
        return false;
      }
      if (parseStatus !== "ALL" && item.parseStatus.toUpperCase() !== parseStatus) {
        return false;
      }
      if (!q) {
        return true;
      }
      return [
        item.reportId,
        item.userId,
        item.email,
        item.displayName,
        item.latestDoctorSummary ?? "",
        item.scenarioLabel ?? "",
      ]
        .join(" ")
        .toLowerCase()
        .includes(q);
    });

  const total = items.length;
  const totalPages = Math.max(1, Math.ceil(total / pageSize));
  const start = (page - 1) * pageSize;

  return {
    summary: {
      totalReports: total,
      pendingReports: items.filter((item) => item.parseStatus.toUpperCase() === "PENDING").length,
      highRiskReports: items.filter((item) => {
        const risk = (item.riskLevel ?? "").toUpperCase();
        return risk === "HIGH" || risk === "CRITICAL";
      }).length,
      abnormalMetrics: items.reduce((sum, item) => sum + item.abnormalMetricCount, 0),
    },
    filters: {
      q: query.q?.trim() ?? "",
      riskLevel,
      parseStatus,
    },
    pagination: {
      page,
      pageSize,
      total,
      totalPages,
    },
    items: items.slice(start, start + pageSize),
  };
}
