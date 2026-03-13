import { NextResponse } from "next/server";
import { requireAuth } from "@/lib/auth";
import { writeAuditEvent } from "@/lib/audit";
import { enqueueNightlyJob, getLatestAnalysis, resolveIdempotencyKey } from "@/lib/inference";
import { fail, ok, parseJsonBody } from "@/lib/http";
import { getActiveModelVersion } from "@/lib/model-registry";
import { createServiceClient } from "@/lib/supabase";
import { dispatchWorkerRun } from "@/lib/worker-dispatch";
import { extractWindowFeatureRow } from "@/lib/window-features";

type AnalyzeBody = {
  sleepRecordId?: string;
  rawData?: unknown;
  idempotencyKey?: string;
};

export async function POST(req: Request) {
  const auth = await requireAuth(req);
  if (!auth.authenticated) {
    return auth.response;
  }

  const body = parseJsonBody<AnalyzeBody>(await req.json().catch(() => ({})));
  const sleepRecordId = (body.sleepRecordId ?? "").trim();
  const idempotencyHeader = req.headers.get("x-idempotency-key") ?? "";
  const idempotencyKey = resolveIdempotencyKey(
    body.idempotencyKey ?? idempotencyHeader,
    auth.user.id,
    sleepRecordId
  );

  if (!sleepRecordId) {
    return NextResponse.json(fail(400, "sleepRecordId required"), { status: 400 });
  }

  try {
    const serviceClient = createServiceClient();
    const latest = await getLatestAnalysis(auth.client, auth.user.id, sleepRecordId);
    if (latest) {
      await writeAuditEvent(serviceClient, {
        userId: auth.user.id,
        actor: "api:sleep/analyze",
        action: "read",
        resourceType: "nightly_reports",
        resourceId: sleepRecordId,
      });
      return NextResponse.json(
        ok("ok", {
          sleepStages: latest.sleepStages,
          sleepStages5: latest.sleepStages5,
          recoveryScore: latest.recoveryScore,
          sleepQuality: latest.sleepQuality,
          insights: latest.insights,
          anomalyScore: latest.anomalyScore,
          factors: latest.factors,
          modelVersion: latest.modelVersion,
          status: "succeeded",
        })
      );
    }

    const windowRow = extractWindowFeatureRow(body.rawData, Date.now());
    const { error: saveWindowError } = await auth.client.from("sleep_windows").insert({
      user_id: auth.user.id,
      sleep_record_id: sleepRecordId,
      window_start: windowRow.windowStart,
      window_end: windowRow.windowEnd,
      hr_features: windowRow.hrFeatures,
      spo2_features: windowRow.spo2Features,
      hrv_features: windowRow.hrvFeatures,
      temp_features: windowRow.tempFeatures,
      motion_features: windowRow.motionFeatures,
      ppg_features: windowRow.ppgFeatures,
      edge_anomaly_signal: windowRow.edgeAnomalySignal,
    });

    if (saveWindowError) {
      return NextResponse.json(fail(500, saveWindowError.message), { status: 500 });
    }

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
      actor: "api:sleep/analyze",
      action: "enqueue",
      resourceType: "inference_jobs",
      resourceId: job.id,
      metadata: {
        sleepRecordId,
        modelVersion,
        modalityCount: windowRow.modalityCount,
      },
    });

    const dispatchResult = await dispatchWorkerRun(req, {
      limit: 1,
      timeoutMs: 900,
    });
    if (!dispatchResult.ok) {
      console.warn(
        `[sleep/analyze] worker dispatch skipped for ${sleepRecordId}: ${dispatchResult.reason ?? "unknown"}`
      );
    }

    return NextResponse.json(
      ok("accepted", {
        sleepStages: [],
        recoveryScore: 0,
        sleepQuality: "处理中",
        insights: ["analysis job accepted"],
        sleepStages5: [],
        anomalyScore: 0,
        modelVersion: job.model_version ?? "mmt-v1",
        jobId: job.id,
        status: job.status,
      })
    );
  } catch (error) {
    const message = error instanceof Error ? error.message : "analyze failed";
    return NextResponse.json(fail(500, message), { status: 500 });
  }
}
