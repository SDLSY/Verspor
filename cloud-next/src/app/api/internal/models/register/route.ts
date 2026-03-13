import { NextResponse } from "next/server";
import { writeAuditEvent } from "@/lib/audit";
import { fail, ok, parseJsonBody } from "@/lib/http";
import { requireInternalToken } from "@/lib/internal";
import { createServiceClient } from "@/lib/supabase";

type RegisterBody = {
  modelKind?: string;
  version?: string;
  artifactPath?: string;
  featureSchemaVersion?: string;
  runtimeType?: "fallback" | "http";
  inferenceEndpoint?: string | null;
  confidenceThreshold?: number;
  fallbackEnabled?: boolean;
  inferenceTimeoutMs?: number;
  activate?: boolean;
};

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

export async function POST(req: Request) {
  const unauthorized = requireInternalToken(req);
  if (unauthorized) {
    return unauthorized;
  }

  const body = parseJsonBody<RegisterBody>(await req.json().catch(() => ({})));
  const modelKind = (body.modelKind ?? "sleep-multimodal").trim();
  const version = (body.version ?? "").trim();

  if (!modelKind || !version) {
    return NextResponse.json(fail(400, "modelKind and version required"), { status: 400 });
  }

  const payload = {
    model_kind: modelKind,
    version,
    artifact_path: (body.artifactPath ?? "").trim() || null,
    feature_schema_version: (body.featureSchemaVersion ?? "v1").trim() || "v1",
    runtime_type: body.runtimeType === "http" ? "http" : "fallback",
    inference_endpoint:
      body.inferenceEndpoint === null
        ? null
        : typeof body.inferenceEndpoint === "string"
        ? body.inferenceEndpoint.trim() || null
        : null,
    confidence_threshold: normalizeThreshold(body.confidenceThreshold),
    fallback_enabled: typeof body.fallbackEnabled === "boolean" ? body.fallbackEnabled : true,
    inference_timeout_ms: normalizeTimeout(body.inferenceTimeoutMs),
    updated_at: new Date().toISOString(),
  };

  const client = createServiceClient();

  const upsertRes = await client
    .from("model_registry")
    .upsert(payload, { onConflict: "model_kind,version" })
    .select(
      "model_kind,version,is_active,artifact_path,feature_schema_version,runtime_type,inference_endpoint,confidence_threshold,fallback_enabled,inference_timeout_ms"
    )
    .single<{
      model_kind: string;
      version: string;
      is_active: boolean;
      artifact_path: string | null;
      feature_schema_version: string;
      runtime_type: string;
      inference_endpoint: string | null;
      confidence_threshold: number;
      fallback_enabled: boolean;
      inference_timeout_ms: number;
    }>();

  if (upsertRes.error || !upsertRes.data) {
    return NextResponse.json(fail(500, upsertRes.error?.message ?? "register failed"), { status: 500 });
  }

  if (body.activate) {
    const reset = await client
      .from("model_registry")
      .update({ is_active: false, updated_at: new Date().toISOString() })
      .eq("model_kind", modelKind)
      .neq("version", version);

    if (reset.error) {
      return NextResponse.json(fail(500, reset.error.message), { status: 500 });
    }

    const activate = await client
      .from("model_registry")
      .update({ is_active: true, updated_at: new Date().toISOString() })
      .eq("model_kind", modelKind)
      .eq("version", version)
      .select(
        "model_kind,version,is_active,artifact_path,feature_schema_version,runtime_type,inference_endpoint,confidence_threshold,fallback_enabled,inference_timeout_ms"
      )
      .single<{
        model_kind: string;
        version: string;
        is_active: boolean;
        artifact_path: string | null;
        feature_schema_version: string;
        runtime_type: string;
        inference_endpoint: string | null;
        confidence_threshold: number;
        fallback_enabled: boolean;
        inference_timeout_ms: number;
      }>();

    if (activate.error || !activate.data) {
      return NextResponse.json(fail(500, activate.error?.message ?? "activation failed"), { status: 500 });
    }

    await writeAuditEvent(client, {
      userId: null,
      actor: "internal:models/register",
      action: "register_activate",
      resourceType: "model_registry",
      resourceId: `${modelKind}:${version}`,
    }).catch(() => null);

    return NextResponse.json(ok("ok", activate.data));
  }

  await writeAuditEvent(client, {
    userId: null,
    actor: "internal:models/register",
    action: "register",
    resourceType: "model_registry",
    resourceId: `${modelKind}:${version}`,
  }).catch(() => null);

  return NextResponse.json(ok("ok", upsertRes.data));
}
