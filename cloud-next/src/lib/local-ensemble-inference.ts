import {
  type LocalInferenceWindowRow,
  type LocalModelOutput,
  runLocalBaselineInference,
} from "@/lib/local-baseline-inference";

export type { LocalInferenceWindowRow, LocalModelOutput };

const STAGES: Array<LocalModelOutput["stages"][number]["stage5"]> = [
  "WAKE",
  "N1",
  "N2",
  "N3",
  "REM",
];

const STAGE_INDEX: Record<LocalModelOutput["stages"][number]["stage5"], number> = {
  WAKE: 0,
  N1: 1,
  N2: 2,
  N3: 3,
  REM: 4,
};

function clamp(value: number, low: number, high: number): number {
  return Math.max(low, Math.min(high, value));
}

function asObject(value: unknown): Record<string, unknown> {
  if (typeof value !== "object" || value === null || Array.isArray(value)) {
    return {};
  }
  return value as Record<string, unknown>;
}

function toNumber(value: unknown): number | null {
  const parsed = Number(value);
  if (!Number.isFinite(parsed)) {
    return null;
  }
  return parsed;
}

function asSignal(value: number | null): number {
  if (typeof value !== "number" || !Number.isFinite(value)) {
    return 0;
  }
  if (value <= 1) {
    return clamp(value * 100, 0, 100);
  }
  return clamp(value, 0, 100);
}

function baselineProbabilities(
  stage: LocalModelOutput["stages"][number]["stage5"],
  confidence: number
): number[] {
  const probs = [0.08, 0.08, 0.08, 0.08, 0.08];
  const idx = STAGE_INDEX[stage];
  const main = clamp(confidence, 0.45, 0.98);
  probs[idx] = main;
  const rest = (1 - main) / 4;
  for (let i = 0; i < probs.length; i += 1) {
    if (i !== idx) {
      probs[i] = rest;
    }
  }
  return probs;
}

function normalizeDistribution(values: number[]): number[] {
  const clipped = values.map((value) => Math.max(0, value));
  const sum = clipped.reduce((acc, current) => acc + current, 0);
  if (sum <= 1e-9) {
    return [0.1, 0.15, 0.35, 0.2, 0.2];
  }
  return clipped.map((value) => value / sum);
}

function transformerHeuristicProbabilities(row: LocalInferenceWindowRow): number[] {
  const hr = asObject(row.hr_features);
  const hrv = asObject(row.hrv_features);
  const motion = asObject(row.motion_features);
  const spo2 = asObject(row.spo2_features);

  const heartRate = toNumber(hr.heartRateAvg ?? hr.heartRate) ?? 55;
  const rmssd = toNumber(hrv.rmssd) ?? 25;
  const lfHf = toNumber(hrv.lfHfRatio) ?? 1;
  const motionIntensity = toNumber(motion.motionIntensity) ?? 0.03;
  const bloodOxygen = toNumber(spo2.bloodOxygenAvg ?? spo2.bloodOxygen) ?? 98.4;
  const edge = asSignal(row.edge_anomaly_signal);

  const wakeScore =
    (motionIntensity > 0.18 ? 2.2 : 0.4) +
    (heartRate > 68 ? 1.3 : 0.2) +
    (edge > 65 ? 1.3 : 0.1);
  const n1Score =
    (edge > 35 && edge <= 70 ? 1.9 : 0.2) +
    (motionIntensity > 0.06 && motionIntensity <= 0.2 ? 0.8 : 0.1) +
    (lfHf > 1.4 ? 0.6 : 0.1);
  const n2Score =
    (motionIntensity <= 0.08 ? 1.7 : 0.5) +
    (heartRate >= 50 && heartRate <= 64 ? 1.0 : 0.3) +
    (edge <= 40 ? 0.9 : 0.2);
  const n3Score =
    (motionIntensity <= 0.03 ? 2.1 : 0.2) +
    (heartRate < 54 ? 1.0 : 0.2) +
    (rmssd < 22 ? 0.8 : 0.2) +
    (edge < 28 ? 0.5 : 0.1);
  const remScore =
    (rmssd >= 28 ? 1.3 : 0.3) +
    (lfHf >= 1.2 ? 0.9 : 0.3) +
    (motionIntensity <= 0.06 ? 0.7 : 0.2) +
    (bloodOxygen < 97.8 ? 0.5 : 0.2) +
    (edge >= 20 && edge <= 55 ? 0.8 : 0.1);

  return normalizeDistribution([wakeScore, n1Score, n2Score, n3Score, remScore]);
}

function readRfWeight(): number {
  const value = Number(process.env.LOCAL_ENSEMBLE_RF_WEIGHT ?? "0.31");
  if (!Number.isFinite(value)) {
    return 0.31;
  }
  return clamp(value, 0, 1);
}

export function runLocalEnsembleInference(windows: LocalInferenceWindowRow[]): LocalModelOutput {
  const baseline = runLocalBaselineInference(windows);
  if (windows.length === 0) {
    return baseline;
  }

  const weightRf = readRfWeight();
  const stages = windows.map((row, index) => {
    const baselineRow = baseline.stages[index] ?? { stage5: "N2" as const, confidence: 0.5 };
    const pRf = baselineProbabilities(baselineRow.stage5, baselineRow.confidence);
    const pTf = transformerHeuristicProbabilities(row);
    const fused = pRf.map((value, i) => value * weightRf + pTf[i] * (1 - weightRf));
    const normalized = normalizeDistribution(fused);
    let best = 0;
    for (let i = 1; i < normalized.length; i += 1) {
      if (normalized[i] > normalized[best]) {
        best = i;
      }
    }
    return {
      stage5: STAGES[best],
      confidence: Number(clamp(normalized[best], 0.45, 0.99).toFixed(4)),
    };
  });

  const edgeMean =
    windows.reduce((acc, row) => acc + asSignal(row.edge_anomaly_signal), 0) / windows.length;
  const wakeLikeRatio = stages.filter((item) => item.stage5 === "WAKE" || item.stage5 === "N1").length / stages.length;
  const anomalyScore = clamp(Math.round(edgeMean * 0.65 + wakeLikeRatio * 35), 0, 100);

  return {
    stages,
    anomalyScore,
    factors:
      anomalyScore >= 50
        ? ["soft_voting_ensemble", "edge_anomaly_signal", "stage_instability"]
        : ["soft_voting_ensemble", "stable_stage_pattern"],
    insights:
      anomalyScore >= 50
        ? ["融合模型识别到较高波动，建议结合多夜数据与恢复状态复核。"]
        : ["融合模型识别结果整体稳定，可持续跟踪周趋势。"],
  };
}
