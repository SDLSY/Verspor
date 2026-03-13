import type { ActiveModelProfile } from "@/lib/model-registry";
import { runLocalBaselineInference } from "@/lib/local-baseline-inference";
import { runLocalEnsembleInference } from "@/lib/local-ensemble-inference";


type JsonMap = Record<string, unknown>;

export type InferenceWindowRow = {
  window_start: string;
  window_end: string;
  hr_features: JsonMap | null;
  spo2_features: JsonMap | null;
  hrv_features: JsonMap | null;
  temp_features: JsonMap | null;
  motion_features: JsonMap | null;
  ppg_features: JsonMap | null;
  edge_anomaly_signal: number | null;
};

export type StagePrediction = {
  stage5: "WAKE" | "N1" | "N2" | "N3" | "REM";
  confidence: number;
};

export type ModelInferenceOutput = {
  stages: StagePrediction[];
  anomalyScore: number;
  factors: string[];
  insights: string[];
};

function clamp(value: number, low: number, high: number): number {
  return Math.max(low, Math.min(high, value));
}

function toStringArray(value: unknown): string[] {
  if (!Array.isArray(value)) {
    return [];
  }
  return value.filter((item): item is string => typeof item === "string");
}

function normalizeStage(stage: unknown): StagePrediction["stage5"] {
  if (stage === "WAKE" || stage === "N1" || stage === "N2" || stage === "N3" || stage === "REM") {
    return stage;
  }
  if (typeof stage === "string") {
    const upper = stage.toUpperCase();
    if (upper === "AWAKE" || upper === "W") {
      return "WAKE";
    }
    if (upper === "DEEP") {
      return "N3";
    }
    if (upper === "LIGHT") {
      return "N2";
    }
    if (upper === "R") {
      return "REM";
    }
  }
  return "N2";
}

function normalizeConfidence(value: unknown): number {
  const numberValue = Number(value);
  if (!Number.isFinite(numberValue)) {
    return 0.5;
  }
  return clamp(Number(numberValue.toFixed(4)), 0, 1);
}

function normalizeAnomalyScore(value: unknown): number {
  const numberValue = Number(value);
  if (!Number.isFinite(numberValue)) {
    return 25;
  }
  if (numberValue <= 1) {
    return clamp(Math.round(numberValue * 100), 0, 100);
  }
  return clamp(Math.round(numberValue), 0, 100);
}

function alignStages(stages: StagePrediction[], expectedLength: number): StagePrediction[] {
  if (expectedLength <= 0) {
    return [];
  }
  if (stages.length === expectedLength) {
    return stages;
  }
  if (stages.length === 0) {
    return Array.from({ length: expectedLength }).map(() => ({ stage5: "N2", confidence: 0.45 }));
  }
  if (stages.length > expectedLength) {
    return stages.slice(0, expectedLength);
  }

  const last = stages[stages.length - 1];
  const padded = [...stages];
  while (padded.length < expectedLength) {
    padded.push(last);
  }
  return padded;
}

function extractResponsePayload(raw: unknown): JsonMap {
  if (typeof raw !== "object" || raw === null || Array.isArray(raw)) {
    return {};
  }
  const response = raw as JsonMap;
  if (typeof response.data === "object" && response.data !== null && !Array.isArray(response.data)) {
    return response.data as JsonMap;
  }
  return response;
}

function normalizeStages(value: unknown): StagePrediction[] {
  if (!Array.isArray(value)) {
    return [];
  }

  return value.map((item) => {
    if (typeof item === "string") {
      return {
        stage5: normalizeStage(item),
        confidence: 0.6,
      };
    }

    if (typeof item === "object" && item !== null && !Array.isArray(item)) {
      const row = item as JsonMap;
      return {
        stage5: normalizeStage(row.stage5 ?? row.stage ?? row.label),
        confidence: normalizeConfidence(row.confidence ?? row.probability),
      };
    }

    return {
      stage5: "N2",
      confidence: 0.5,
    };
  });
}

function buildInferencePayload(profile: ActiveModelProfile, windows: InferenceWindowRow[]): JsonMap {
  return {
    modelVersion: profile.version,
    featureSchemaVersion: profile.featureSchemaVersion,
    windows,
  };
}

async function callHttpInference(
  profile: ActiveModelProfile,
  windows: InferenceWindowRow[]
): Promise<ModelInferenceOutput> {
  if (!profile.inferenceEndpoint) {
    throw new Error("missing inference endpoint");
  }

  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), profile.inferenceTimeoutMs);
  const token = (process.env.MODEL_INFERENCE_TOKEN ?? "").trim();

  try {
    const response = await fetch(profile.inferenceEndpoint, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        ...(token ? { authorization: `Bearer ${token}` } : {}),
      },
      body: JSON.stringify(buildInferencePayload(profile, windows)),
      signal: controller.signal,
    });

    if (!response.ok) {
      throw new Error(`model inference failed with status ${response.status}`);
    }

    const raw = await response.json().catch(() => ({}));
    const payload = extractResponsePayload(raw);
    const alignedStages = alignStages(normalizeStages(payload.stages), windows.length);
    const anomalyScore = normalizeAnomalyScore(payload.anomalyScore ?? payload.anomaly_score);

    return {
      stages: alignedStages,
      anomalyScore,
      factors: toStringArray(payload.factors ?? payload.primary_factors),
      insights: toStringArray(payload.insights),
    };
  } finally {
    clearTimeout(timeoutId);
  }
}

function isLocalBaselineEndpoint(endpoint: string | null): boolean {
  return typeof endpoint === "string" && endpoint.trim().toLowerCase() === "local://baseline";
}

function isLocalEnsembleEndpoint(endpoint: string | null): boolean {
  return typeof endpoint === "string" && endpoint.trim().toLowerCase() === "local://ensemble";
}

export async function runRealModelInference(
  profile: ActiveModelProfile,
  windows: InferenceWindowRow[]
): Promise<ModelInferenceOutput | null> {
  if (profile.runtimeType !== "http") {
    return null;
  }
  if (isLocalBaselineEndpoint(profile.inferenceEndpoint)) {
    return runLocalBaselineInference(windows);
  }
  if (isLocalEnsembleEndpoint(profile.inferenceEndpoint)) {
    return runLocalEnsembleInference(windows);
  }
  if (!profile.inferenceEndpoint) {
    return null;
  }
  return callHttpInference(profile, windows);
}
