import { NextResponse } from "next/server";
import { requireAuth } from "@/lib/auth";
import { writeAuditEvent } from "@/lib/audit";
import { createTraceId, fail, ok } from "@/lib/http";
import { buildPeriodSummaryScientificModel } from "@/lib/recommendation-model/scientific-model";
import { generateRecommendationExpression } from "@/lib/recommendation-model/explanation";
import { loadRecommendationModelProfile } from "@/lib/recommendation-model/srm-v2-config";
import { writeRecommendationTrace } from "@/lib/recommendation-tracking";
import { generatePeriodSummary } from "@/lib/report/period-summary";
import { createServiceClient } from "@/lib/supabase";

export async function GET(req: Request) {
  const traceId = createTraceId();
  const auth = await requireAuth(req);
  if (!auth.authenticated) {
    return auth.response;
  }

  const url = new URL(req.url);
  const periodParam = (url.searchParams.get("period") ?? "weekly").toLowerCase();
  if (periodParam !== "weekly" && periodParam !== "monthly") {
    return NextResponse.json(fail(400, "invalid period, expected weekly or monthly", traceId), {
      status: 400,
    });
  }

  try {
    const serviceClient = createServiceClient();
    const payload = await generatePeriodSummary({
      client: auth.client,
      userId: auth.user.id,
      period: periodParam,
    });

    const modelProfile = await loadRecommendationModelProfile(serviceClient);
    const scientificModel = buildPeriodSummaryScientificModel({
      payload,
      profile: modelProfile,
    });
    const explanation = await generateRecommendationExpression({
      traceType: "PERIOD_SUMMARY",
      scientificModel,
      outputPayload: payload as Record<string, unknown>,
      traceId,
    });

    await writeRecommendationTrace(serviceClient, {
      userId: auth.user.id,
      traceType: "PERIOD_SUMMARY",
      traceKey: periodParam,
      traceId,
      riskLevel: payload.riskLevel,
      personalizationLevel: payload.personalizationLevel,
      missingInputs: payload.missingInputs,
      inputMaterials: {
        period: payload.period,
        sampleSufficient: payload.sampleSufficient,
        reportConfidence: payload.reportConfidence,
        metricChanges: payload.metricChanges,
        highlights: payload.highlights,
      },
      derivedSignals: {
        scientificModel,
      },
      outputPayload: payload,
      metadata: {
        modelVersion: scientificModel.modelVersion,
        profileCode: scientificModel.profileCode,
        configSource: scientificModel.configSource,
        recommendationMode: scientificModel.recommendationMode,
        explanation,
      },
    }).catch(() => null);

    await writeAuditEvent(serviceClient, {
      userId: auth.user.id,
      actor: "api:report/period-summary",
      action: "read",
      resourceType: "health_period_report",
      resourceId: periodParam,
      metadata: {
        period: periodParam,
        riskLevel: payload.riskLevel,
        sampleSufficient: payload.sampleSufficient,
        traceId,
      },
    }).catch(() => null);

    return NextResponse.json(
      ok("ok", traceId, {
        ...payload,
        explanation,
        metadata: {
          modelVersion: scientificModel.modelVersion,
          modelProfile: scientificModel.profileCode,
          configSource: scientificModel.configSource,
        },
      })
    );
  } catch (error) {
    const message = error instanceof Error ? error.message : "generate period summary failed";
    return NextResponse.json(fail(500, message, traceId), { status: 500 });
  }
}
