import { NextResponse } from "next/server";
import { requireAuth } from "@/lib/auth";
import { writeAuditEvent } from "@/lib/audit";
import { enqueueNightlyJob, resolveIdempotencyKey } from "@/lib/inference";
import { fail, ok, parseJsonBody } from "@/lib/http";
import { getActiveModelVersion } from "@/lib/model-registry";
import { createServiceClient } from "@/lib/supabase";
import { dispatchWorkerRun } from "@/lib/worker-dispatch";

type NightlyRequest = {
  sleepRecordId?: string;
  idempotencyKey?: string;
};

export async function POST(req: Request) {
  const auth = await requireAuth(req);
  if (!auth.authenticated) {
    return auth.response;
  }

  const body = parseJsonBody<NightlyRequest>(await req.json().catch(() => ({})));
  const sleepRecordId = (body.sleepRecordId ?? "").trim();
  const idempotencyKey = resolveIdempotencyKey(
    body.idempotencyKey ?? req.headers.get("x-idempotency-key") ?? "",
    auth.user.id,
    sleepRecordId
  );

  if (!sleepRecordId) {
    return NextResponse.json(fail(400, "sleepRecordId required"), { status: 400 });
  }

  try {
    const serviceClient = createServiceClient();
    const modelVersion = await getActiveModelVersion(serviceClient);
    const job = await enqueueNightlyJob(
      auth.client,
      auth.user.id,
      sleepRecordId,
      idempotencyKey,
      modelVersion
    );

    await writeAuditEvent(serviceClient, {
      userId: auth.user.id,
      actor: "api:v1/inference/nightly",
      action: "enqueue",
      resourceType: "inference_jobs",
      resourceId: job.id,
      metadata: {
        sleepRecordId,
        modelVersion,
      },
    });

    const dispatchResult = await dispatchWorkerRun(req, {
      limit: 1,
      timeoutMs: 900,
    });
    if (!dispatchResult.ok) {
      console.warn(
        `[v1/inference/nightly] worker dispatch skipped for ${sleepRecordId}: ${dispatchResult.reason ?? "unknown"}`
      );
    }

    return NextResponse.json(
      ok("accepted", {
        jobId: job.id,
        status: job.status,
      })
    );
  } catch (error) {
    const message = error instanceof Error ? error.message : "enqueue failed";
    return NextResponse.json(fail(500, message), { status: 500 });
  }
}
