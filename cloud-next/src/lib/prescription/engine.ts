import type { SupabaseClient } from "@supabase/supabase-js";
import { writeAuditEvent } from "@/lib/audit";
import { buildPrescriptionServerContext } from "@/lib/prescription/context";
import { createPrescriptionProviderChain } from "@/lib/prescription/providers/factory";
import { buildPrescriptionRagContext } from "@/lib/prescription/retrieval";
import { buildDailyPrescriptionScientificModel } from "@/lib/recommendation-model/scientific-model";
import { generateRecommendationExpression } from "@/lib/recommendation-model/explanation";
import { loadRecommendationModelProfile } from "@/lib/recommendation-model/srm-v2-config";
import { writeRecommendationTrace } from "@/lib/recommendation-tracking";
import {
  buildRuleFallback,
  mergeRequestWithServerContext,
  validateProviderDecision,
} from "@/lib/prescription/rules";
import type {
  DailyPrescriptionRequest,
  DailyPrescriptionResponse,
  PrescriptionServerContext,
} from "@/lib/prescription/types";

export type GenerateDailyPrescriptionInput = {
  authClient: SupabaseClient;
  serviceClient: SupabaseClient;
  userId: string;
  request: DailyPrescriptionRequest;
  traceId: string;
};

export type GenerateDailyPrescriptionResult = {
  payload: DailyPrescriptionResponse;
  providerId: string;
  snapshotId: string | null;
  recommendationId: string | null;
  isFallback: boolean;
  scientificModel: ReturnType<typeof buildDailyPrescriptionScientificModel>;
  explanation: Awaited<ReturnType<typeof generateRecommendationExpression>>;
};

async function insertSnapshot(
  client: SupabaseClient,
  userId: string,
  traceId: string,
  request: DailyPrescriptionRequest
): Promise<string | null> {
  const { data, error } = await client
    .from("prescription_snapshots")
    .insert({
      user_id: userId,
      trigger_type: request.triggerType,
      domain_scores_json: request.domainScores,
      evidence_facts_json: request.evidenceFacts,
      red_flags_json: request.redFlags,
      personalization_level: request.personalizationLevel,
      missing_inputs_json: request.missingInputs,
      trace_id: traceId,
    })
    .select("id")
    .single();

  if (error) {
    return null;
  }

  return (data as { id: string }).id;
}

async function insertRecommendation(
  client: SupabaseClient,
  userId: string,
  snapshotId: string | null,
  providerId: string,
  payload: DailyPrescriptionResponse,
  isFallback: boolean
): Promise<string | null> {
  const { data, error } = await client
    .from("prescription_recommendations")
    .insert({
      user_id: userId,
      snapshot_id: snapshotId,
      provider_id: providerId,
      primary_goal: payload.primaryGoal,
      risk_level: payload.riskLevel,
      target_domains_json: payload.targetDomains,
      primary_intervention_type: payload.primaryInterventionType,
      secondary_intervention_type: payload.secondaryInterventionType,
      lifestyle_task_codes_json: payload.lifestyleTaskCodes,
      timing_slot: payload.timing,
      duration_sec: payload.durationSec,
      rationale: payload.rationale,
      evidence_json: payload.evidence,
      contraindications_json: payload.contraindications,
      followup_metric: payload.followupMetric,
      is_fallback: isFallback,
    })
    .select("id")
    .single();

  if (error) {
    return null;
  }

  return (data as { id: string }).id;
}

async function insertGenerationLog(
  client: SupabaseClient,
  input: {
    userId: string;
    snapshotId: string | null;
    recommendationId?: string | null;
    providerId: string;
    success: boolean;
    latencyMs: number;
    failureCode: string | null;
    traceId: string;
  }
): Promise<void> {
  await client.from("prescription_generation_logs").insert({
    user_id: input.userId,
    snapshot_id: input.snapshotId,
    recommendation_id: input.recommendationId ?? null,
    provider_id: input.providerId,
    success: input.success,
    latency_ms: input.latencyMs,
    failure_code: input.failureCode,
    trace_id: input.traceId,
  });
}

async function insertIntegratedTrace(
  client: SupabaseClient,
  input: {
    userId: string;
    traceId: string;
    request: DailyPrescriptionRequest;
    mergedRequest: DailyPrescriptionRequest;
    serverContext: PrescriptionServerContext;
    payload: DailyPrescriptionResponse;
    providerId: string;
    snapshotId: string | null;
    recommendationId: string | null;
    isFallback: boolean;
    scientificModel: ReturnType<typeof buildDailyPrescriptionScientificModel>;
    explanation: Awaited<ReturnType<typeof generateRecommendationExpression>>;
  }
): Promise<void> {
  await writeRecommendationTrace(client, {
    userId: input.userId,
    traceType: "DAILY_PRESCRIPTION",
    traceKey: input.mergedRequest.triggerType,
    traceId: input.traceId,
    providerId: input.providerId,
    relatedSnapshotId: input.snapshotId,
    relatedRecommendationId: input.recommendationId,
    riskLevel: input.payload.riskLevel,
    personalizationLevel: input.payload.personalizationLevel,
    missingInputs: input.payload.missingInputs,
    inputMaterials: {
      triggerType: input.request.triggerType,
      requestDomainScores: input.request.domainScores,
      requestEvidenceFacts: input.request.evidenceFacts,
      requestRedFlags: input.request.redFlags,
      requestPersonalizationLevel: input.request.personalizationLevel,
      requestMissingInputs: input.request.missingInputs,
      catalogProtocolCodes: input.request.catalog.map((item) => item.protocolCode),
    },
    derivedSignals: {
      mergedDomainScores: input.mergedRequest.domainScores,
      mergedEvidenceFacts: input.mergedRequest.evidenceFacts,
      mergedRedFlags: input.mergedRequest.redFlags,
      mergedPersonalizationLevel: input.mergedRequest.personalizationLevel,
      mergedMissingInputs: input.mergedRequest.missingInputs,
      serverContext: input.serverContext,
      scientificModel: input.scientificModel,
    },
    outputPayload: input.payload,
    metadata: {
      snapshotId: input.snapshotId,
      recommendationId: input.recommendationId,
      modelVersion: input.scientificModel.modelVersion,
      profileCode: input.scientificModel.profileCode,
      configSource: input.scientificModel.configSource,
      recommendationMode: input.scientificModel.recommendationMode,
      explanation: input.explanation,
    },
    isFallback: input.isFallback,
  });
}

export async function generateDailyPrescription(
  input: GenerateDailyPrescriptionInput
): Promise<GenerateDailyPrescriptionResult> {
  const modelProfile = await loadRecommendationModelProfile(input.serviceClient);
  const serverContext = await buildPrescriptionServerContext(
    input.serviceClient,
    input.userId,
    input.request
  );
  const mergedRequest = mergeRequestWithServerContext(input.request, serverContext);
  const ragContext = buildPrescriptionRagContext(mergedRequest, serverContext);
  const snapshotId = await insertSnapshot(input.serviceClient, input.userId, input.traceId, mergedRequest);
  const providers = createPrescriptionProviderChain();

  for (const provider of providers) {
    const startedAt = Date.now();
    const rawDecision = await provider.generate({
      request: mergedRequest,
      ragContext,
      traceId: input.traceId,
    });
    const latencyMs = Date.now() - startedAt;

    if (!rawDecision) {
      await insertGenerationLog(input.serviceClient, {
        userId: input.userId,
        snapshotId,
        providerId: provider.providerId,
        success: false,
        latencyMs,
        failureCode: "EMPTY_PROVIDER_RESULT",
        traceId: input.traceId,
      });
      continue;
    }

    const validated = validateProviderDecision(mergedRequest, serverContext, rawDecision);
    if (!validated.valid || !validated.payload) {
      await insertGenerationLog(input.serviceClient, {
        userId: input.userId,
        snapshotId,
        providerId: provider.providerId,
        success: false,
        latencyMs,
        failureCode: validated.failureCode,
        traceId: input.traceId,
      });
      continue;
    }

    const recommendationId = await insertRecommendation(
      input.serviceClient,
      input.userId,
      snapshotId,
      provider.providerId,
      validated.payload,
      false
    );

    await insertGenerationLog(input.serviceClient, {
      userId: input.userId,
      snapshotId,
      recommendationId,
      providerId: provider.providerId,
      success: true,
      latencyMs,
      failureCode: null,
      traceId: input.traceId,
    });

    await writeAuditEvent(input.serviceClient, {
      userId: input.userId,
      actor: "prescription_engine",
      action: "generated_daily_prescription",
      resourceType: "prescription_recommendation",
      resourceId: recommendationId ?? undefined,
      metadata: {
        providerId: provider.providerId,
        snapshotId,
        isFallback: false,
        traceId: input.traceId,
      },
    });

    const scientificModel = buildDailyPrescriptionScientificModel({
      request: input.request,
      mergedRequest,
      serverContext,
      payload: validated.payload,
      profile: modelProfile,
    });
    const explanation = await generateRecommendationExpression({
      traceType: "DAILY_PRESCRIPTION",
      scientificModel,
      outputPayload: validated.payload,
      traceId: input.traceId,
    });

    await insertIntegratedTrace(input.serviceClient, {
      userId: input.userId,
      traceId: input.traceId,
      request: input.request,
      mergedRequest,
      serverContext,
      payload: validated.payload,
      providerId: provider.providerId,
      snapshotId,
      recommendationId,
      isFallback: false,
      scientificModel,
      explanation,
    });

    return {
      payload: validated.payload,
      providerId: provider.providerId,
      snapshotId,
      recommendationId,
      isFallback: false,
      scientificModel,
      explanation,
    };
  }

  const fallbackPayload = buildRuleFallback(mergedRequest, serverContext);
  const fallbackProviderId = "rules_fallback";
  const recommendationId = await insertRecommendation(
    input.serviceClient,
    input.userId,
    snapshotId,
    fallbackProviderId,
    fallbackPayload,
    true
  );

  await insertGenerationLog(input.serviceClient, {
    userId: input.userId,
    snapshotId,
    recommendationId,
    providerId: fallbackProviderId,
    success: true,
    latencyMs: 0,
    failureCode: null,
    traceId: input.traceId,
  });

  await writeAuditEvent(input.serviceClient, {
    userId: input.userId,
    actor: "prescription_engine",
    action: "generated_daily_prescription",
    resourceType: "prescription_recommendation",
    resourceId: recommendationId ?? undefined,
    metadata: {
      providerId: fallbackProviderId,
      snapshotId,
      isFallback: true,
      traceId: input.traceId,
    },
  });

  const scientificModel = buildDailyPrescriptionScientificModel({
    request: input.request,
    mergedRequest,
    serverContext,
    payload: fallbackPayload,
    profile: modelProfile,
  });
  const explanation = await generateRecommendationExpression({
    traceType: "DAILY_PRESCRIPTION",
    scientificModel,
    outputPayload: fallbackPayload,
    traceId: input.traceId,
  });

  await insertIntegratedTrace(input.serviceClient, {
    userId: input.userId,
    traceId: input.traceId,
    request: input.request,
    mergedRequest,
    serverContext,
    payload: fallbackPayload,
    providerId: fallbackProviderId,
    snapshotId,
    recommendationId,
    isFallback: true,
    scientificModel,
    explanation,
  });

  return {
    payload: fallbackPayload,
    providerId: fallbackProviderId,
    snapshotId,
    recommendationId,
    isFallback: true,
    scientificModel,
    explanation,
  };
}
