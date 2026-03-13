import { NextResponse } from "next/server";
import { writeAuditEvent } from "@/lib/audit";
import { fail, ok, parseJsonBody } from "@/lib/http";
import { requireInternalToken } from "@/lib/internal";
import { createServiceClient } from "@/lib/supabase";

type ActivateBody = {
  modelKind?: string;
  version?: string;
  runtimeType?: "fallback" | "http";
  inferenceEndpoint?: string | null;
  confidenceThreshold?: number;
  fallbackEnabled?: boolean;
  inferenceTimeoutMs?: number;
};

function normalizeThreshold(value: unknown): number | null {
  if (typeof value !== "number" || !Number.isFinite(value)) {
    return null;
  }
  return Math.max(0, Math.min(1, value));
}

function normalizeTimeout(value: unknown): number | null {
  if (typeof value !== "number" || !Number.isFinite(value)) {
    return null;
  }
  return Math.max(1000, Math.min(120000, Math.trunc(value)));
}

export async function POST(req: Request) {
  const unauthorized = requireInternalToken(req);
  if (unauthorized) {
    return unauthorized;
  }

  const body = parseJsonBody<ActivateBody>(await req.json().catch(() => ({})));
  const modelKind = (body.modelKind ?? "sleep-multimodal").trim();
  const version = (body.version ?? "").trim();
  const runtimeType = body.runtimeType === "http" ? "http" : body.runtimeType === "fallback" ? "fallback" : null;
  const inferenceEndpoint =
    body.inferenceEndpoint === null
      ? null
      : typeof body.inferenceEndpoint === "string"
      ? body.inferenceEndpoint.trim() || null
      : null;
  const confidenceThreshold = normalizeThreshold(body.confidenceThreshold);
  const fallbackEnabled = typeof body.fallbackEnabled === "boolean" ? body.fallbackEnabled : null;
  const inferenceTimeoutMs = normalizeTimeout(body.inferenceTimeoutMs);

  if (!modelKind || !version) {
    return NextResponse.json(fail(400, "modelKind and version required"), { status: 400 });
  }

  const client = createServiceClient();

  const target = await client
    .from("model_registry")
    .select("id")
    .eq("model_kind", modelKind)
    .eq("version", version)
    .maybeSingle<{ id: string }>();

  if (target.error) {
    return NextResponse.json(fail(500, target.error.message), { status: 500 });
  }
  if (!target.data) {
    return NextResponse.json(fail(404, "model version not found"), { status: 404 });
  }

  const reset = await client
    .from("model_registry")
    .update({ is_active: false, updated_at: new Date().toISOString() })
    .eq("model_kind", modelKind);

  if (reset.error) {
    return NextResponse.json(fail(500, reset.error.message), { status: 500 });
  }

  const activate = await client
    .from("model_registry")
    .update({
      is_active: true,
      updated_at: new Date().toISOString(),
      ...(runtimeType ? { runtime_type: runtimeType } : {}),
      ...(typeof body.inferenceEndpoint !== "undefined" ? { inference_endpoint: inferenceEndpoint } : {}),
      ...(confidenceThreshold !== null ? { confidence_threshold: confidenceThreshold } : {}),
      ...(fallbackEnabled !== null ? { fallback_enabled: fallbackEnabled } : {}),
      ...(inferenceTimeoutMs !== null ? { inference_timeout_ms: inferenceTimeoutMs } : {}),
    })
    .eq("id", target.data.id)
    .select(
      "model_kind,version,is_active,runtime_type,inference_endpoint,confidence_threshold,fallback_enabled,inference_timeout_ms"
    )
    .single<{
      model_kind: string;
      version: string;
      is_active: boolean;
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
      actor: "internal:models/activate",
      action: "activate",
      resourceType: "model_registry",
      resourceId: `${modelKind}:${version}`,
      metadata: {
        runtimeType: activate.data.runtime_type,
        confidenceThreshold: activate.data.confidence_threshold,
        fallbackEnabled: activate.data.fallback_enabled,
      },
    }).catch(() => null);

  return NextResponse.json(ok("ok", activate.data));
}
