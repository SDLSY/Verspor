import { writeAuditEvent } from "@/lib/audit";
import { createInternalTokenHeaders } from "@/lib/internal";
import { randomId } from "@/lib/http";
import { toIsoTime, toInt } from "@/lib/inference";
import { createAdminError, getDirectoryUser, requireDirectoryUser, toTimestamp } from "@/lib/admin-core";
import { createServiceClient } from "@/lib/supabase";

type Row = Record<string, unknown>;

export type AdminTaskInput = {
  taskId?: string;
  date?: number | null;
  sourceType?: string;
  triggerReason?: string;
  bodyZone?: string;
  protocolType?: string;
  durationSec?: number;
  plannedAt?: number | null;
  status?: string;
  actorEmail?: string | null;
};

export type AdminModelMutationInput = {
  modelKind?: string;
  version: string;
  artifactPath?: string;
  featureSchemaVersion?: string;
  runtimeType?: "fallback" | "http";
  inferenceEndpoint?: string | null;
  confidenceThreshold?: number;
  fallbackEnabled?: boolean;
  inferenceTimeoutMs?: number;
  activate?: boolean;
  actorEmail?: string | null;
};

function getString(value: unknown): string {
  return typeof value === "string" ? value : "";
}

function getNumber(value: unknown): number | null {
  const numberValue = Number(value);
  return Number.isFinite(numberValue) ? numberValue : null;
}

function normalizeThreshold(value: unknown): number {
  const numberValue = Number(value);
  if (!Number.isFinite(numberValue)) {
    return 0.6;
  }
  return Math.max(0, Math.min(1, numberValue));
}

function normalizeTimeout(value: unknown): number {
  const numberValue = Number(value);
  if (!Number.isFinite(numberValue)) {
    return 12000;
  }
  return Math.max(1000, Math.min(120000, Math.trunc(numberValue)));
}

function mapModelRow(row: Row) {
  return {
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
  };
}

export async function registerAdminModel(input: AdminModelMutationInput) {
  const modelKind = (input.modelKind ?? "sleep-multimodal").trim();
  const version = input.version.trim();
  if (!modelKind || !version) {
    throw createAdminError("模型类型和版本号不能为空");
  }

  const client = createServiceClient();
  const payload = {
    model_kind: modelKind,
    version,
    artifact_path: (input.artifactPath ?? "").trim() || null,
    feature_schema_version: (input.featureSchemaVersion ?? "v1").trim() || "v1",
    runtime_type: input.runtimeType === "http" ? "http" : "fallback",
    inference_endpoint: input.inferenceEndpoint === null ? null : typeof input.inferenceEndpoint === "string" ? input.inferenceEndpoint.trim() || null : null,
    confidence_threshold: normalizeThreshold(input.confidenceThreshold),
    fallback_enabled: typeof input.fallbackEnabled === "boolean" ? input.fallbackEnabled : true,
    inference_timeout_ms: normalizeTimeout(input.inferenceTimeoutMs),
    updated_at: new Date().toISOString(),
  };

  const upsertRes = await client
    .from("model_registry")
    .upsert(payload, { onConflict: "model_kind,version" })
    .select("model_kind,version,is_active,artifact_path,feature_schema_version,runtime_type,inference_endpoint,confidence_threshold,fallback_enabled,inference_timeout_ms,updated_at")
    .single<Row>();

  if (upsertRes.error || !upsertRes.data) {
    throw createAdminError(upsertRes.error?.message ?? "模型注册失败");
  }

  await writeAuditEvent(client, {
    userId: null,
    actor: input.actorEmail ? `admin:${input.actorEmail}` : "admin:model/register",
    action: "register",
    resourceType: "model_registry",
    resourceId: `${modelKind}:${version}`,
  }).catch(() => null);

  if (input.activate) {
    return activateAdminModel(input);
  }

  return mapModelRow(upsertRes.data);
}

export async function activateAdminModel(input: AdminModelMutationInput) {
  const modelKind = (input.modelKind ?? "sleep-multimodal").trim();
  const version = input.version.trim();
  if (!modelKind || !version) {
    throw createAdminError("模型类型和版本号不能为空");
  }

  const client = createServiceClient();
  const target = await client.from("model_registry").select("id").eq("model_kind", modelKind).eq("version", version).maybeSingle<{ id: string }>();
  if (target.error) {
    throw createAdminError(target.error.message);
  }
  if (!target.data) {
    throw createAdminError("未找到指定模型版本");
  }

  const reset = await client.from("model_registry").update({ is_active: false, updated_at: new Date().toISOString() }).eq("model_kind", modelKind);
  if (reset.error) {
    throw createAdminError(reset.error.message);
  }

  const updatePayload = {
    is_active: true,
    updated_at: new Date().toISOString(),
    ...(input.runtimeType ? { runtime_type: input.runtimeType } : {}),
    ...(typeof input.inferenceEndpoint !== "undefined" ? { inference_endpoint: input.inferenceEndpoint === null ? null : typeof input.inferenceEndpoint === "string" ? input.inferenceEndpoint.trim() || null : null } : {}),
    ...(typeof input.confidenceThreshold !== "undefined" ? { confidence_threshold: normalizeThreshold(input.confidenceThreshold) } : {}),
    ...(typeof input.fallbackEnabled === "boolean" ? { fallback_enabled: input.fallbackEnabled } : {}),
    ...(typeof input.inferenceTimeoutMs !== "undefined" ? { inference_timeout_ms: normalizeTimeout(input.inferenceTimeoutMs) } : {}),
  };

  const activate = await client
    .from("model_registry")
    .update(updatePayload)
    .eq("id", target.data.id)
    .select("model_kind,version,is_active,artifact_path,feature_schema_version,runtime_type,inference_endpoint,confidence_threshold,fallback_enabled,inference_timeout_ms,updated_at")
    .single<Row>();

  if (activate.error || !activate.data) {
    throw createAdminError(activate.error?.message ?? "模型激活失败");
  }

  await writeAuditEvent(client, {
    userId: null,
    actor: input.actorEmail ? `admin:${input.actorEmail}` : "admin:model/activate",
    action: "activate",
    resourceType: "model_registry",
    resourceId: `${modelKind}:${version}`,
    metadata: {
      runtimeType: getString(activate.data.runtime_type),
      confidenceThreshold: getNumber(activate.data.confidence_threshold),
      fallbackEnabled: Boolean(activate.data.fallback_enabled),
    },
  }).catch(() => null);

  return mapModelRow(activate.data);
}

export async function upsertAdminInterventionTask(userId: string, input: AdminTaskInput): Promise<{ taskId: string }> {
  requireDirectoryUser(await getDirectoryUser(userId), userId);
  const client = createServiceClient();
  const taskId = (input.taskId ?? "").trim() || randomId("task");

  const { error } = await client.from("intervention_tasks").upsert(
    {
      user_id: userId,
      task_id: taskId,
      task_date: toIsoTime(input.date) ?? new Date().toISOString(),
      source_type: (input.sourceType ?? "RULE_ENGINE").trim().toUpperCase(),
      trigger_reason: input.triggerReason ?? "",
      body_zone: (input.bodyZone ?? "LIMB").trim().toUpperCase(),
      protocol_type: (input.protocolType ?? "LOW_ACTIVITY").trim().toUpperCase(),
      duration_sec: toInt(input.durationSec) ?? 60,
      planned_at: toIsoTime(input.plannedAt) ?? new Date().toISOString(),
      status: (input.status ?? "PENDING").trim().toUpperCase(),
      updated_at: new Date().toISOString(),
    },
    { onConflict: "user_id,task_id" }
  );

  if (error) {
    throw createAdminError(error.message);
  }

  await writeAuditEvent(client, {
    userId,
    actor: input.actorEmail ? `admin:${input.actorEmail}` : "admin:intervention/task/upsert",
    action: "upsert",
    resourceType: "intervention_tasks",
    resourceId: taskId,
    metadata: { status: (input.status ?? "PENDING").trim().toUpperCase() },
  }).catch(() => null);

  return { taskId };
}

export async function triggerAdminWorker(origin: string, limit = 20): Promise<Record<string, unknown>> {
  const response = await fetch(new URL("/api/internal/worker/run", origin), {
    method: "POST",
    headers: {
      "content-type": "application/json",
      ...createInternalTokenHeaders(),
    },
    body: JSON.stringify({ limit: Math.max(1, Math.min(100, Math.trunc(Number(limit) || 20))) }),
    cache: "no-store",
  });

  const payload = (await response.json().catch(() => null)) as { code?: number; message?: string; data?: Record<string, unknown> } | null;
  if (!response.ok || payload?.code !== 0) {
    throw createAdminError(payload?.message ?? `手动作业触发失败（HTTP ${response.status}）`);
  }
  return payload?.data ?? {};
}
