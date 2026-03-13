import { NextResponse } from "next/server";
import { requireAuth } from "@/lib/auth";
import { createTraceId, fail, ok, parseJsonBody } from "@/lib/http";
import { buildDoctorTurnScientificModel } from "@/lib/recommendation-model/scientific-model";
import { generateRecommendationExpression } from "@/lib/recommendation-model/explanation";
import { loadRecommendationModelProfile } from "@/lib/recommendation-model/srm-v2-config";
import { writeRecommendationTrace } from "@/lib/recommendation-tracking";
import { createServiceClient } from "@/lib/supabase";
import { DoctorTurnRequestSchema, generateDoctorTurn } from "@/lib/ai/doctor-turn";

export async function POST(req: Request) {
  const traceId = createTraceId();
  const auth = await requireAuth(req);
  if (!auth.authenticated) {
    return auth.response;
  }

  let json: unknown;
  try {
    json = await req.json();
  } catch {
    return NextResponse.json(fail(400, "invalid json payload", traceId), { status: 400 });
  }

  const parsed = DoctorTurnRequestSchema.safeParse(parseJsonBody(json));
  if (!parsed.success) {
    return NextResponse.json(fail(400, "invalid doctor turn payload", traceId), { status: 400 });
  }

  const result = await generateDoctorTurn(parsed.data, traceId);
  if (!result) {
    return NextResponse.json(fail(503, "doctor turn generation unavailable", traceId), {
      status: 503,
    });
  }

  const serviceClient = createServiceClient();
  const modelProfile = await loadRecommendationModelProfile(serviceClient);
  const scientificModel = buildDoctorTurnScientificModel({
    payload: {
      stage: parsed.data.stage,
      followUpCount: parsed.data.followUpCount,
      conversationBlock: parsed.data.conversationBlock,
      contextBlock: parsed.data.contextBlock,
      ragContext: parsed.data.ragContext,
      chiefComplaint: result.payload.chiefComplaint,
      symptomFacts: result.payload.symptomFacts,
      missingInfo: result.payload.missingInfo,
      suspectedIssues: result.payload.suspectedIssues,
      riskLevel: result.payload.riskLevel,
      redFlags: result.payload.redFlags,
      recommendedDepartment: result.payload.recommendedDepartment,
      nextStepAdvice: result.payload.nextStepAdvice,
      doctorSummary: result.payload.doctorSummary,
      outputStage: result.payload.stage,
    },
    profile: modelProfile,
  });
  const explanation = await generateRecommendationExpression({
    traceType: "DOCTOR_TURN",
    scientificModel,
    outputPayload: result.payload as Record<string, unknown>,
    traceId,
  });

  await writeRecommendationTrace(serviceClient, {
    userId: auth.user.id,
    traceType: "DOCTOR_TURN",
    traceKey: parsed.data.stage,
    traceId,
    providerId: result.providerId,
    riskLevel: result.payload.riskLevel,
    inputMaterials: {
      stage: parsed.data.stage,
      followUpCount: parsed.data.followUpCount,
      conversationBlock: parsed.data.conversationBlock.slice(0, 4000),
      contextBlock: parsed.data.contextBlock.slice(0, 2000),
      ragContext: parsed.data.ragContext.slice(0, 2000),
    },
    derivedSignals: {
      scientificModel,
      missingInfoCount: result.payload.missingInfo.length,
      redFlagCount: result.payload.redFlags.length,
      suspectedIssueCount: result.payload.suspectedIssues.length,
    },
    outputPayload: result.payload,
    metadata: {
      modelVersion: scientificModel.modelVersion,
      profileCode: scientificModel.profileCode,
      configSource: scientificModel.configSource,
      recommendationMode: scientificModel.recommendationMode,
      recommendedDepartment: result.payload.recommendedDepartment,
      explanation,
    },
    isFallback: result.fallbackUsed,
  }).catch(() => null);

  return NextResponse.json(
    ok("ok", traceId, {
      ...result.payload,
      explanation,
      metadata: {
        providerId: result.providerId,
        fallbackUsed: result.fallbackUsed,
        modelVersion: scientificModel.modelVersion,
        modelProfile: scientificModel.profileCode,
        configSource: scientificModel.configSource,
      },
    })
  );
}
