import {
  buildMetadataObject,
  clampInteger,
  createAdminError,
  listDirectoryUsers,
  toTimestamp,
} from "@/lib/admin-core";
import { createServiceClient } from "@/lib/supabase";

type Row = Record<string, unknown>;

export type AdminModelHealth = {
  status: "ok" | "error" | "fallback" | "internal" | "unknown";
  detail: string | null;
  latencyMs: number | null;
};

export type AdminModelRecord = {
  modelKind: string;
  version: string;
  isActive: boolean;
  artifactPath: string | null;
  featureSchemaVersion: string;
  runtimeType: string;
  inferenceEndpoint: string | null;
  confidenceThreshold: number;
  fallbackEnabled: boolean;
  inferenceTimeoutMs: number;
  updatedAt: number | null;
  health: AdminModelHealth;
};

export type AdminModelsResponse = {
  items: AdminModelRecord[];
};

export type AdminJobsQuery = {
  page?: number;
  pageSize?: number;
  status?: string;
};

export type AdminJobsResponse = {
  counts: Record<string, number>;
  latency: {
    samples: number;
    avgMs: number | null;
    p95Ms: number | null;
  };
  pagination: {
    page: number;
    pageSize: number;
    total: number;
    totalPages: number;
  };
  filters: {
    status: string;
  };
  recentJobs: Array<{
    jobId: string;
    userId: string;
    userEmail: string | null;
    sleepRecordId: string;
    status: string;
    modelVersion: string | null;
    errorMessage: string | null;
    createdAt: number | null;
    finishedAt: number | null;
  }>;
  recentFailed: Array<{
    jobId: string;
    userId: string;
    userEmail: string | null;
    errorMessage: string | null;
    createdAt: number | null;
  }>;
};

export type AdminAuditQuery = {
  page?: number;
  pageSize?: number;
  actor?: string;
  action?: string;
  resourceType?: string;
  startAt?: number;
  endAt?: number;
};

export type AdminAuditResponse = {
  pagination: {
    page: number;
    pageSize: number;
    total: number;
    totalPages: number;
  };
  filters: {
    actor: string;
    action: string;
    resourceType: string;
    startAt: number | null;
    endAt: number | null;
  };
  items: Array<{
    id: string;
    userId: string | null;
    actor: string;
    action: string;
    resourceType: string;
    resourceId: string | null;
    createdAt: number | null;
    metadata: Record<string, unknown>;
  }>;
};

function getString(value: unknown): string {
  return typeof value === "string" ? value : "";
}

function getNumber(value: unknown): number | null {
  const numberValue = Number(value);
  return Number.isFinite(numberValue) ? numberValue : null;
}

async function probeModelHealth(row: Row): Promise<AdminModelHealth> {
  const runtimeType = getString(row.runtime_type).trim().toLowerCase();
  const endpoint = getString(row.inference_endpoint).trim();

  if (runtimeType !== "http") {
    return { status: "fallback", detail: "当前使用本地兜底运行时", latencyMs: null };
  }
  if (!endpoint) {
    return { status: "unknown", detail: "未配置推理地址", latencyMs: null };
  }
  if (!/^https?:\/\//i.test(endpoint)) {
    return { status: "internal", detail: endpoint, latencyMs: null };
  }

  const healthUrl = endpoint.endsWith("/") ? `${endpoint}health` : `${endpoint}/health`;
  const startedAt = Date.now();
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), 2500);
  try {
    const response = await fetch(healthUrl, { method: "GET", cache: "no-store", signal: controller.signal });
    const latencyMs = Date.now() - startedAt;
    if (!response.ok) {
      return { status: "error", detail: `健康检查失败（HTTP ${response.status}）`, latencyMs };
    }
    return { status: "ok", detail: "健康检查正常", latencyMs };
  } catch (error) {
    return {
      status: "error",
      detail: error instanceof Error && error.name === "AbortError" ? "健康检查超时" : "健康检查失败",
      latencyMs: Date.now() - startedAt,
    };
  } finally {
    clearTimeout(timeoutId);
  }
}

function computeLatencyStats(rows: Row[]): { samples: number; avgMs: number | null; p95Ms: number | null } {
  const durations = rows
    .map((row) => {
      const startedAt = toTimestamp(getString(row.started_at));
      const finishedAt = toTimestamp(getString(row.finished_at));
      if (startedAt == null || finishedAt == null) {
        return NaN;
      }
      return finishedAt - startedAt;
    })
    .filter((value) => Number.isFinite(value) && value >= 0)
    .sort((left, right) => left - right);

  if (durations.length === 0) {
    return { samples: 0, avgMs: null, p95Ms: null };
  }

  const p95Index = Math.min(durations.length - 1, Math.floor(durations.length * 0.95));
  return {
    samples: durations.length,
    avgMs: Math.round(durations.reduce((acc, value) => acc + value, 0) / durations.length),
    p95Ms: Math.round(durations[p95Index]),
  };
}

export async function getAdminModels(): Promise<AdminModelsResponse> {
  const client = createServiceClient();
  const { data, error } = await client
    .from("model_registry")
    .select("model_kind,version,is_active,artifact_path,feature_schema_version,runtime_type,inference_endpoint,confidence_threshold,fallback_enabled,inference_timeout_ms,updated_at")
    .order("updated_at", { ascending: false })
    .returns<Row[]>();
  if (error) {
    throw createAdminError(error.message);
  }
  const rows = data ?? [];
  const healthList = await Promise.all(rows.map((row) => probeModelHealth(row)));
  return {
    items: rows.map((row, index) => ({
      modelKind: getString(row.model_kind),
      version: getString(row.version),
      isActive: Boolean(row.is_active),
      artifactPath: getString(row.artifact_path) || null,
      featureSchemaVersion: getString(row.feature_schema_version),
      runtimeType: getString(row.runtime_type),
      inferenceEndpoint: getString(row.inference_endpoint) || null,
      confidenceThreshold: getNumber(row.confidence_threshold) ?? 0.6,
      fallbackEnabled: Boolean(row.fallback_enabled),
      inferenceTimeoutMs: getNumber(row.inference_timeout_ms) ?? 12000,
      updatedAt: toTimestamp(getString(row.updated_at)),
      health: healthList[index],
    })),
  };
}

export async function getAdminJobs(query: AdminJobsQuery = {}): Promise<AdminJobsResponse> {
  const page = clampInteger(query.page, 1, 1, 9999);
  const pageSize = clampInteger(query.pageSize, 20, 1, 50);
  const status = (query.status ?? "ALL").trim().toUpperCase();
  const client = createServiceClient();

  const [countRes, latencyRes, recentFailedRes, totalRes, recentJobRes, users] = await Promise.all([
    client.from("inference_jobs").select("id,status").limit(5000).returns<Row[]>(),
    client.from("inference_jobs").select("id,started_at,finished_at").not("finished_at", "is", null).not("started_at", "is", null).limit(500).returns<Row[]>(),
    client.from("inference_jobs").select("id,user_id,error_message,created_at").eq("status", "failed").order("created_at", { ascending: false }).limit(10).returns<Row[]>(),
    (() => {
      let queryBuilder = client.from("inference_jobs").select("*", { count: "exact", head: true });
      if (status !== "ALL") {
        queryBuilder = queryBuilder.eq("status", status.toLowerCase());
      }
      return queryBuilder;
    })(),
    (() => {
      let queryBuilder = client.from("inference_jobs").select("id,user_id,sleep_record_id,status,model_version,error_message,created_at,finished_at").order("created_at", { ascending: false }).range((page - 1) * pageSize, (page - 1) * pageSize + pageSize - 1);
      if (status !== "ALL") {
        queryBuilder = queryBuilder.eq("status", status.toLowerCase());
      }
      return queryBuilder.returns<Row[]>();
    })(),
    listDirectoryUsers(),
  ]);

  if (countRes.error || latencyRes.error || recentFailedRes.error || totalRes.error || recentJobRes.error) {
    throw createAdminError(
      countRes.error?.message ??
        latencyRes.error?.message ??
        recentFailedRes.error?.message ??
        totalRes.error?.message ??
        recentJobRes.error?.message ??
        "加载作业数据失败"
    );
  }

  const counts = (countRes.data ?? []).reduce<Record<string, number>>((acc, row) => {
    const normalized = getString(row.status).trim().toLowerCase();
    acc[normalized] = (acc[normalized] ?? 0) + 1;
    return acc;
  }, {});
  const directoryById = new Map(users.map((user) => [user.id, user.email ?? null]));
  const total = totalRes.count ?? 0;
  const totalPages = Math.max(1, Math.ceil(total / pageSize));

  return {
    counts,
    latency: computeLatencyStats(latencyRes.data ?? []),
    pagination: { page, pageSize, total, totalPages },
    filters: { status },
    recentJobs: (recentJobRes.data ?? []).map((row) => ({
      jobId: getString(row.id),
      userId: getString(row.user_id),
      userEmail: directoryById.get(getString(row.user_id)) ?? null,
      sleepRecordId: getString(row.sleep_record_id),
      status: getString(row.status),
      modelVersion: getString(row.model_version) || null,
      errorMessage: getString(row.error_message) || null,
      createdAt: toTimestamp(getString(row.created_at)),
      finishedAt: toTimestamp(getString(row.finished_at)),
    })),
    recentFailed: (recentFailedRes.data ?? []).map((row) => ({
      jobId: getString(row.id),
      userId: getString(row.user_id),
      userEmail: directoryById.get(getString(row.user_id)) ?? null,
      errorMessage: getString(row.error_message) || null,
      createdAt: toTimestamp(getString(row.created_at)),
    })),
  };
}

export async function getAdminAudit(query: AdminAuditQuery = {}): Promise<AdminAuditResponse> {
  const page = clampInteger(query.page, 1, 1, 9999);
  const pageSize = clampInteger(query.pageSize, 50, 1, 100);
  const actor = (query.actor ?? "").trim();
  const action = (query.action ?? "").trim();
  const resourceType = (query.resourceType ?? "").trim();
  const startAt = query.startAt ?? null;
  const endAt = query.endAt ?? null;
  const client = createServiceClient();

  let baseQuery = client.from("audit_events").select("id,user_id,actor,action,resource_type,resource_id,metadata,created_at", { count: "exact" }).order("created_at", { ascending: false }).range((page - 1) * pageSize, (page - 1) * pageSize + pageSize - 1);
  if (actor) baseQuery = baseQuery.ilike("actor", `%${actor}%`);
  if (action) baseQuery = baseQuery.ilike("action", `%${action}%`);
  if (resourceType) baseQuery = baseQuery.ilike("resource_type", `%${resourceType}%`);
  if (startAt) baseQuery = baseQuery.gte("created_at", new Date(startAt).toISOString());
  if (endAt) baseQuery = baseQuery.lte("created_at", new Date(endAt).toISOString());

  const { data, error, count } = await baseQuery.returns<Row[]>();
  if (error) {
    throw createAdminError(error.message);
  }

  const total = count ?? 0;
  const totalPages = Math.max(1, Math.ceil(total / pageSize));
  return {
    pagination: { page, pageSize, total, totalPages },
    filters: { actor, action, resourceType, startAt, endAt },
    items: (data ?? []).map((row) => ({
      id: getString(row.id),
      userId: getString(row.user_id) || null,
      actor: getString(row.actor),
      action: getString(row.action),
      resourceType: getString(row.resource_type),
      resourceId: getString(row.resource_id) || null,
      createdAt: toTimestamp(getString(row.created_at)),
      metadata: buildMetadataObject(row.metadata),
    })),
  };
}
