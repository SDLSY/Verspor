import type { SupabaseClient } from "@supabase/supabase-js";
import type {
  RecommendationExpression,
} from "@/lib/recommendation-model/explanation";
import type {
  ScientificHypothesis,
  ScientificRecommendationSheet,
  ScientificTraceType,
} from "@/lib/recommendation-model/scientific-model";

type RecommendationTraceRow = {
  id: string;
  trace_type: ScientificTraceType;
  trace_key: string | null;
  trace_id: string | null;
  provider_id: string | null;
  risk_level: string | null;
  personalization_level: string | null;
  is_fallback: boolean;
  metadata_json: unknown;
  derived_signals_json: unknown;
  created_at: string;
};

type RecommendationTraceMetadata = {
  explanation?: RecommendationExpression;
  modelVersion?: string;
  profileCode?: string;
  modelProfile?: string;
  configSource?: string;
  recommendationMode?: string;
};

export type RecommendationExplanationPanel = {
  id: string;
  traceId: string | null;
  traceType: ScientificTraceType;
  traceKey: string | null;
  createdAt: string;
  providerId: string | null;
  riskLevel: string | null;
  personalizationLevel: string | null;
  isFallback: boolean;
  summary: string;
  reasons: string[];
  nextStep: string;
  recommendationMode: string | null;
  modelVersion: string | null;
  modelProfile: string | null;
  configSource: string | null;
  safetyGate: string | null;
  evidenceCoverage: number | null;
  evidenceHighlights: string[];
};

type ListRecommendationExplanationPanelsInput = {
  userId: string;
  traceType?: ScientificTraceType;
  traceId?: string;
  limit?: number;
};

function toObject(value: unknown): Record<string, unknown> {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    return {};
  }
  return value as Record<string, unknown>;
}

function readString(value: unknown): string | null {
  return typeof value === "string" && value.trim().length > 0 ? value.trim() : null;
}

function readStringArray(value: unknown, max = 3): string[] {
  if (!Array.isArray(value)) {
    return [];
  }
  return value
    .map((item) => (typeof item === "string" ? item.trim() : ""))
    .filter(Boolean)
    .slice(0, max);
}

function readNumber(value: unknown): number | null {
  if (typeof value === "number" && Number.isFinite(value)) {
    return value;
  }
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}

function asScientificModel(value: unknown): ScientificRecommendationSheet | null {
  const object = toObject(value);
  if (readString(object.modelVersion) !== "SRM_V2") {
    return null;
  }
  return object as unknown as ScientificRecommendationSheet;
}

function extractScientificModel(row: RecommendationTraceRow): ScientificRecommendationSheet | null {
  const derivedSignals = toObject(row.derived_signals_json);
  return asScientificModel(derivedSignals.scientificModel);
}

function extractExplanation(row: RecommendationTraceRow): RecommendationExpression | null {
  const metadata = toObject(row.metadata_json) as RecommendationTraceMetadata;
  const explanation = metadata.explanation;
  if (!explanation || typeof explanation !== "object") {
    return null;
  }
  return explanation;
}

function fallbackReasons(scientificModel: ScientificRecommendationSheet | null): string[] {
  if (!scientificModel) {
    return [];
  }

  const topHypothesis = scientificModel.hypotheses[0] as ScientificHypothesis | undefined;
  const hypothesisReasons = topHypothesis?.evidenceLabels?.slice(0, 3) ?? [];
  if (hypothesisReasons.length > 0) {
    return hypothesisReasons;
  }

  return scientificModel.evidenceLedger.slice(0, 3).map((item) => item.label);
}

function fallbackNextStep(scientificModel: ScientificRecommendationSheet | null): string {
  if (!scientificModel) {
    return "先执行当前建议，再观察变化。";
  }
  return `优先按“${scientificModel.recommendationMode}”方向执行本轮建议。`;
}

function mapExplanationPanel(row: RecommendationTraceRow): RecommendationExplanationPanel {
  const metadata = toObject(row.metadata_json) as RecommendationTraceMetadata;
  const scientificModel = extractScientificModel(row);
  const explanation = extractExplanation(row);

  return {
    id: row.id,
    traceId: row.trace_id,
    traceType: row.trace_type,
    traceKey: row.trace_key,
    createdAt: row.created_at,
    providerId: row.provider_id,
    riskLevel: row.risk_level,
    personalizationLevel: row.personalization_level,
    isFallback: row.is_fallback,
    summary:
      readString(explanation?.summary) ??
      readString(scientificModel?.decisionSummary) ??
      "系统已生成本轮建议解释。",
    reasons:
      readStringArray(explanation?.reasons, 3).length > 0
        ? readStringArray(explanation?.reasons, 3)
        : fallbackReasons(scientificModel),
    nextStep:
      readString(explanation?.nextStep) ?? fallbackNextStep(scientificModel),
    recommendationMode:
      readString(metadata.recommendationMode) ??
      readString(scientificModel?.recommendationMode) ??
      null,
    modelVersion:
      readString(metadata.modelVersion) ??
      readString(scientificModel?.modelVersion) ??
      null,
    modelProfile:
      readString(metadata.profileCode) ??
      readString(metadata.modelProfile) ??
      readString(scientificModel?.profileCode) ??
      null,
    configSource:
      readString(metadata.configSource) ??
      readString(scientificModel?.configSource) ??
      null,
    safetyGate: readString(scientificModel?.safetyGate),
    evidenceCoverage: readNumber(scientificModel?.evidenceCoverage),
    evidenceHighlights:
      scientificModel?.evidenceLedger?.slice(0, 3).map((item) => item.label) ?? [],
  };
}

export async function listRecommendationExplanationPanels(
  client: SupabaseClient,
  input: ListRecommendationExplanationPanelsInput
): Promise<RecommendationExplanationPanel[]> {
  const limit = Math.max(1, Math.min(20, input.limit ?? 6));

  let query = client
    .from("recommendation_traces")
    .select(
      "id,trace_type,trace_key,trace_id,provider_id,risk_level,personalization_level,is_fallback,metadata_json,derived_signals_json,created_at"
    )
    .eq("user_id", input.userId)
    .order("created_at", { ascending: false })
    .limit(limit);

  if (input.traceType) {
    query = query.eq("trace_type", input.traceType);
  }

  if (input.traceId) {
    query = query.eq("trace_id", input.traceId);
  }

  const { data, error } = await query.returns<RecommendationTraceRow[]>();
  if (error) {
    throw new Error(error.message);
  }

  return (data ?? []).map(mapExplanationPanel);
}
