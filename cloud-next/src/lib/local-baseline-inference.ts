type JsonMap = Record<string, unknown>;

export type LocalInferenceWindowRow = {
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

export type LocalModelOutput = {
  stages: Array<{
    stage5: "WAKE" | "N1" | "N2" | "N3" | "REM";
    confidence: number;
  }>;
  anomalyScore: number;
  factors: string[];
  insights: string[];
};

const STAGES: Array<"WAKE" | "N1" | "N2" | "N3" | "REM"> = ["WAKE", "N1", "N2", "N3", "REM"];

const BASELINE_CENTROIDS: Record<number, number[]> = {
  0: [55.29790115356445, 55.29790115356445, 51.797916412353516, 58.79788589477539, 98.49281311035156, 98.49281311035156, 97.69226837158203, 35.23829650878906, 25.371593475341797, 30.304956436157227, 1.0357521772384644, 36.399314880371094, 36.399314880371094, 0, 0.0450180321931839, 231.98886108398438, 0.0744776651263237],
  1: [55.13355255126953, 55.13355255126953, 51.633548736572266, 58.63355255126953, 98.49700164794922, 98.49700164794922, 97.69691467285156, 35.10684585571289, 25.27690315246582, 30.191864013671875, 1.0160340070724487, 36.40000915527344, 36.40000915527344, 0, 0.02061016671359539, 111.03389739990234, 0.03338474780321121],
  2: [55.18899154663086, 55.18899154663086, 51.68900680541992, 58.688987731933594, 98.49559783935547, 98.49559783935547, 97.69564056396484, 35.15113830566406, 25.30883026123047, 30.230016708374023, 1.02268385887146, 36.40011215209961, 36.40011215209961, 0, 0.02846112661063671, 153.06166076660156, 0.04723618924617767],
  3: [55.36565017700195, 55.36565017700195, 51.86565017700195, 58.86565399169922, 98.49125671386719, 98.49125671386719, 97.69132995605469, 35.29256057739258, 25.410633087158203, 30.3515625, 1.04385507106781, 36.40004348754883, 36.40004348754883, 0, 0.05800672993063927, 217.79124450683594, 0.0914134606719017],
  4: [55.123897552490234, 55.123897552490234, 51.62388610839844, 58.6239013671875, 98.49710845947266, 98.49710845947266, 97.6971664428711, 35.09913635253906, 25.271379470825195, 30.185293197631836, 1.0148696899414062, 36.399932861328125, 36.399932861328125, 0, 0.018925586715340614, 106.62325286865234, 0.03098185546696186],
};

function clamp(value: number, low: number, high: number): number {
  return Math.max(low, Math.min(high, value));
}

function asObject(value: unknown): JsonMap {
  if (typeof value !== "object" || value === null || Array.isArray(value)) {
    return {};
  }
  return value as JsonMap;
}

function toNumber(value: unknown): number | null {
  const numberValue = Number(value);
  if (!Number.isFinite(numberValue)) {
    return null;
  }
  return numberValue;
}

function normalizedEdgeSignal(value: number | null): number {
  if (typeof value !== "number" || !Number.isFinite(value)) {
    return 0;
  }
  if (value <= 1) {
    return clamp(value * 100, 0, 100);
  }
  return clamp(value, 0, 100);
}

function vectorize(row: LocalInferenceWindowRow): number[] {
  const hr = asObject(row.hr_features);
  const spo2 = asObject(row.spo2_features);
  const hrv = asObject(row.hrv_features);
  const temp = asObject(row.temp_features);
  const motion = asObject(row.motion_features);
  const ppg = asObject(row.ppg_features);

  const values: Array<number | null> = [
    toNumber(hr.heartRate),
    toNumber(hr.heartRateAvg),
    toNumber(hr.heartRateMin),
    toNumber(hr.heartRateMax),
    toNumber(spo2.bloodOxygen),
    toNumber(spo2.bloodOxygenAvg),
    toNumber(spo2.bloodOxygenMin),
    toNumber(hrv.hrv),
    toNumber(hrv.rmssd),
    toNumber(hrv.sdnn),
    toNumber(hrv.lfHfRatio),
    toNumber(temp.temperature),
    toNumber(temp.temperatureAvg),
    toNumber(temp.temperatureTrend),
    toNumber(motion.motionIntensity),
    toNumber(ppg.ppgValue),
    toNumber(row.edge_anomaly_signal),
  ];

  return values.map((item) => (item === null ? 0 : item));
}

function predictStage(feature: number[]): { stage5: LocalModelOutput["stages"][number]["stage5"]; confidence: number } {
  const pairs = Object.entries(BASELINE_CENTROIDS).map(([label, centroid]) => {
    let distance = 0;
    for (let i = 0; i < centroid.length; i += 1) {
      const delta = feature[i] - centroid[i];
      distance += delta * delta;
    }
    return { label: Number(label), distance };
  });

  pairs.sort((a, b) => a.distance - b.distance);
  const best = pairs[0];
  const second = pairs[1] ?? pairs[0];
  const separation = (second.distance - best.distance) / Math.max(second.distance, 1e-6);
  const confidence = clamp(0.5 + separation * 0.5, 0.45, 0.99);

  return {
    stage5: STAGES[clamp(Math.trunc(best.label), 0, STAGES.length - 1)],
    confidence: Number(confidence.toFixed(4)),
  };
}

export function runLocalBaselineInference(windows: LocalInferenceWindowRow[]): LocalModelOutput {
  if (windows.length === 0) {
    return {
      stages: [],
      anomalyScore: 20,
      factors: ["missing_windows"],
      insights: ["缺少窗口特征，无法执行基线模型推理。"],
    };
  }

  const stages = windows.map((row) => predictStage(vectorize(row)));
  const edgeMean = windows.reduce((acc, row) => acc + normalizedEdgeSignal(row.edge_anomaly_signal), 0) / windows.length;
  const wakeRatio = stages.filter((item) => item.stage5 === "WAKE" || item.stage5 === "N1").length / stages.length;
  const anomalyScore = clamp(Math.round(edgeMean * 0.7 + wakeRatio * 30), 0, 100);

  const factors = anomalyScore >= 50 ? ["edge_anomaly_signal", "stage_instability"] : ["stable_stage_pattern"];
  const insights =
    anomalyScore >= 50
      ? ["模型识别到较高夜间波动，建议结合血氧与心率趋势复核。"]
      : ["模型识别结果整体稳定，可继续观察多夜趋势。"];

  return {
    stages,
    anomalyScore,
    factors,
    insights,
  };
}
