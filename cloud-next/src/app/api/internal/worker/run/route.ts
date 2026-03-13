import { NextResponse } from "next/server";
import { writeAuditEvent } from "@/lib/audit";
import { fail, ok, parseJsonBody } from "@/lib/http";
import { mapLegacyStage } from "@/lib/inference";
import { requireInternalToken } from "@/lib/internal";
import { type InferenceWindowRow, runRealModelInference } from "@/lib/model-inference";
import { getActiveModelProfile, getModelProfileByVersion } from "@/lib/model-registry";
import { createServiceClient } from "@/lib/supabase";

type WorkerBody = {
  limit?: number;
};

type JobCandidate = {
  id: string;
  user_id: string;
  sleep_record_id: string;
  model_version: string | null;
};

type ClaimedJob = JobCandidate;

type WindowRow = InferenceWindowRow;

type WorkerInferenceResult = {
  stages: Array<{ stage5: string; confidence: number }>;
  anomalyScore: number;
  factors: string[];
  insights: string[];
  usedFallback: boolean;
  fallbackReason: string | null;
};

function clamp(value: number, low: number, high: number): number {
  return Math.max(low, Math.min(high, value));
}

function normalizeEdgeSignal(value: number | null): number | null {
  if (typeof value !== "number" || !Number.isFinite(value)) {
    return null;
  }
  if (value <= 1) {
    return clamp(Math.round(value * 100), 0, 100);
  }
  return clamp(Math.round(value), 0, 100);
}

function inferFallbackFactors(anomalyScore: number): string[] {
  if (anomalyScore >= 50) {
    return ["edge_anomaly_signal", "hrv_drop"];
  }
  if (anomalyScore >= 30) {
    return ["spo2_variability"];
  }
  return ["stable_night"];
}

function buildStages(epochCount: number, anomalyScore: number): Array<{ stage5: string; confidence: number }> {
  const base = ["N2", "N3", "REM", "N2", "N1"];
  const stages: Array<{ stage5: string; confidence: number }> = [];

  for (let i = 0; i < epochCount; i += 1) {
    let stage5 = base[i % base.length] as string;
    if (anomalyScore >= 70 && i % 4 === 0) {
      stage5 = "WAKE";
    }
    if (anomalyScore >= 50 && i % 6 === 5) {
      stage5 = "N1";
    }
    const confidence = clamp(0.9 - anomalyScore / 200 + ((i % 3) - 1) * 0.03, 0.45, 0.95);
    stages.push({ stage5, confidence: Number(confidence.toFixed(4)) });
  }

  return stages;
}

function qualityByRecovery(score: number): string {
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

function insightByAnomaly(anomalyScore: number): string[] {
  if (anomalyScore >= 70) {
    return ["夜间异常波动较高，建议优先恢复和提前入睡。"];
  }
  if (anomalyScore >= 40) {
    return ["存在轻中度波动，建议降低训练强度并关注血氧变化。"];
  }
  return ["夜间状态稳定，可维持常规训练和作息。"];
}

function applyConfidenceGate(
  stages: Array<{ stage5: string; confidence: number }>,
  threshold: number
): { gatedStages: Array<{ stage5: string; confidence: number }>; lowConfidenceCount: number } {
  const normalizedThreshold = clamp(threshold, 0, 1);
  let lowConfidenceCount = 0;

  const gatedStages = stages.map((stage) => {
    if (stage.confidence < normalizedThreshold) {
      lowConfidenceCount += 1;
      return {
        stage5: "N1",
        confidence: stage.confidence,
      };
    }
    return stage;
  });

  return { gatedStages, lowConfidenceCount };
}

function runFallbackInference(windows: WindowRow[]): WorkerInferenceResult {
  const normalizedSignals = windows
    .map((row) => normalizeEdgeSignal(row.edge_anomaly_signal))
    .filter((value): value is number => typeof value === "number");

  const anomalyScore =
    normalizedSignals.length > 0
      ? Math.round(normalizedSignals.reduce((acc, current) => acc + current, 0) / normalizedSignals.length)
      : 25;

  const epochCount = clamp(windows.length, 5, 60);
  const stages = buildStages(epochCount, anomalyScore);

  return {
    stages,
    anomalyScore,
    factors: inferFallbackFactors(anomalyScore),
    insights: insightByAnomaly(anomalyScore),
    usedFallback: true,
    fallbackReason: "fallback_runtime",
  };
}

async function claimJobs(limit: number): Promise<JobCandidate[]> {
  const client = createServiceClient();
  const { data, error } = await client
    .from("inference_jobs")
    .select("id,user_id,sleep_record_id,model_version")
    .eq("status", "queued")
    .order("created_at", { ascending: true })
    .limit(limit)
    .returns<JobCandidate[]>();

  if (error || !data) {
    return [];
  }

  return data;
}

async function runWorker(limit: number) {
  const serviceClient = createServiceClient();
  const activeModelProfile = await getActiveModelProfile(serviceClient);
  const candidates = await claimJobs(limit);

  let processed = 0;
  let succeeded = 0;
  let failed = 0;
  const jobIds: string[] = [];

  for (const candidate of candidates) {
    const claim = await serviceClient
      .from("inference_jobs")
      .update({
        status: "running",
        started_at: new Date().toISOString(),
        error_message: null,
      })
      .eq("id", candidate.id)
      .eq("status", "queued")
      .select("id,user_id,sleep_record_id,model_version")
      .maybeSingle<ClaimedJob>();

    if (claim.error || !claim.data) {
      continue;
    }

    const claimed = claim.data;

    processed += 1;
    jobIds.push(claimed.id);

    try {
      const requestedVersion = claimed.model_version?.trim() ?? "";
      const profileFromVersion = requestedVersion
        ? await getModelProfileByVersion(serviceClient, requestedVersion)
        : null;
      const modelProfile = profileFromVersion ?? activeModelProfile;
      const modelVersion = modelProfile.version;

      const windowsRes = await serviceClient
        .from("sleep_windows")
        .select(
          "window_start,window_end,hr_features,spo2_features,hrv_features,temp_features,motion_features,ppg_features,edge_anomaly_signal"
        )
        .eq("user_id", claimed.user_id)
        .eq("sleep_record_id", claimed.sleep_record_id)
        .order("window_start", { ascending: true })
        .limit(120)
        .returns<WindowRow[]>();

      if (windowsRes.error) {
        throw new Error(windowsRes.error.message);
      }

      const windows = windowsRes.data ?? [];

      let inference: WorkerInferenceResult;
      if (windows.length === 0) {
        inference = runFallbackInference(windows);
        inference.fallbackReason = "missing_windows";
      } else {
        try {
          const modelOutput = await runRealModelInference(modelProfile, windows);
          if (!modelOutput) {
            inference = runFallbackInference(windows);
          } else {
            inference = {
              stages: modelOutput.stages,
              anomalyScore: modelOutput.anomalyScore,
              factors: modelOutput.factors,
              insights: modelOutput.insights,
              usedFallback: false,
              fallbackReason: null,
            };
          }
        } catch (error) {
          if (!modelProfile.fallbackEnabled) {
            throw error;
          }
          const message = error instanceof Error ? error.message : "model_inference_failed";
          inference = runFallbackInference(windows);
          inference.fallbackReason = message;
        }
      }

      const { gatedStages, lowConfidenceCount } = applyConfidenceGate(
        inference.stages,
        modelProfile.confidenceThreshold
      );

      const anomalyScore = clamp(Math.round(inference.anomalyScore), 0, 100);
      const recoveryScore = clamp(100 - anomalyScore, 0, 100);
      const sleepQuality = qualityByRecovery(recoveryScore);
      const factors = [...inference.factors];
      if (inference.usedFallback && !factors.includes("model_fallback")) {
        factors.push("model_fallback");
      }
      if (lowConfidenceCount > 0 && !factors.includes("low_confidence_gate")) {
        factors.push("low_confidence_gate");
      }

      const insights =
        inference.insights.length > 0
          ? [...inference.insights]
          : insightByAnomaly(anomalyScore);

      if (inference.usedFallback) {
        insights.push("云端模型不可用，当前结果由回退策略生成。");
      }
      if (lowConfidenceCount > 0) {
        insights.push(`置信度门控已触发 ${lowConfidenceCount} 个片段。`);
      }

      const stageRows = gatedStages.map((row, index) => ({
        user_id: claimed.user_id,
        sleep_record_id: claimed.sleep_record_id,
        epoch_index: index,
        stage_5: row.stage5,
        stage_legacy: mapLegacyStage(row.stage5),
        confidence: row.confidence,
        model_version: modelVersion,
      }));

      const stageSave = await serviceClient
        .from("sleep_stage_results")
        .upsert(stageRows, { onConflict: "user_id,sleep_record_id,epoch_index" });
      if (stageSave.error) {
        throw new Error(stageSave.error.message);
      }

      const anomalySave = await serviceClient.from("anomaly_scores").upsert(
        {
          user_id: claimed.user_id,
          sleep_record_id: claimed.sleep_record_id,
          score_0_100: anomalyScore,
          primary_factors: factors,
          model_version: modelVersion,
        },
        { onConflict: "user_id,sleep_record_id,model_version" }
      );
      if (anomalySave.error) {
        throw new Error(anomalySave.error.message);
      }

      const reportSave = await serviceClient.from("nightly_reports").upsert(
        {
          user_id: claimed.user_id,
          sleep_record_id: claimed.sleep_record_id,
          recovery_score: recoveryScore,
          sleep_quality: sleepQuality,
          insights,
          model_version: modelVersion,
          updated_at: new Date().toISOString(),
        },
        { onConflict: "user_id,sleep_record_id" }
      );
      if (reportSave.error) {
        throw new Error(reportSave.error.message);
      }

      const completeJob = await serviceClient
        .from("inference_jobs")
        .update({
          status: "succeeded",
          model_version: modelVersion,
          finished_at: new Date().toISOString(),
          error_message: null,
        })
        .eq("id", claimed.id);
      if (completeJob.error) {
        throw new Error(completeJob.error.message);
      }

      await writeAuditEvent(serviceClient, {
        userId: claimed.user_id,
        actor: "internal:worker/run",
        action: "complete",
        resourceType: "inference_jobs",
        resourceId: claimed.id,
        metadata: {
          sleepRecordId: claimed.sleep_record_id,
          modelVersion,
          featureSchemaVersion: modelProfile.featureSchemaVersion,
          runtimeType: modelProfile.runtimeType,
          confidenceThreshold: modelProfile.confidenceThreshold,
          usedFallback: inference.usedFallback,
          fallbackReason: inference.fallbackReason,
          lowConfidenceCount,
          anomalyScore,
        },
      });

      succeeded += 1;
    } catch (error) {
      failed += 1;
      const message = error instanceof Error ? error.message : "worker failed";
      await serviceClient
        .from("inference_jobs")
        .update({
          status: "failed",
          finished_at: new Date().toISOString(),
          error_message: message,
        })
        .eq("id", claimed.id);
    }
  }

  return NextResponse.json(
    ok("ok", {
      requestedLimit: limit,
      selected: candidates.length,
      processed,
      succeeded,
      failed,
      jobIds,
    })
  );
}

export async function POST(req: Request) {
  const unauthorized = requireInternalToken(req);
  if (unauthorized) {
    return unauthorized;
  }

  const body = parseJsonBody<WorkerBody>(await req.json().catch(() => ({})));
  const limit = clamp(Number(body.limit ?? 10), 1, 100);
  return runWorker(limit);
}

export async function GET(req: Request) {
  const unauthorized = requireInternalToken(req);
  if (unauthorized) {
    return unauthorized;
  }

  const url = new URL(req.url);
  const limit = clamp(Number(url.searchParams.get("limit") ?? 20), 1, 100);
  return runWorker(limit);
}
