type JsonMap = Record<string, unknown>;

export type WindowFeatureRow = {
  windowStart: string;
  windowEnd: string;
  hrFeatures: JsonMap | null;
  spo2Features: JsonMap | null;
  hrvFeatures: JsonMap | null;
  tempFeatures: JsonMap | null;
  motionFeatures: JsonMap | null;
  ppgFeatures: JsonMap | null;
  edgeAnomalySignal: number | null;
  modalityCount: number;
};

function asObject(value: unknown): JsonMap | null {
  if (typeof value !== "object" || value === null || Array.isArray(value)) {
    return null;
  }
  return value as JsonMap;
}

function hasKeys(value: JsonMap): boolean {
  return Object.keys(value).length > 0;
}

function pickObject(input: JsonMap, keys: string[]): JsonMap | null {
  for (const key of keys) {
    const value = asObject(input[key]);
    if (value && hasKeys(value)) {
      return value;
    }
  }
  return null;
}

function pickNumber(input: JsonMap, keys: string[]): number | null {
  for (const key of keys) {
    const raw = input[key];
    const value = Number(raw);
    if (Number.isFinite(value)) {
      return value;
    }
  }
  return null;
}

function pickTime(input: JsonMap, keys: string[], fallbackMs: number): string {
  for (const key of keys) {
    const value = input[key];
    if (typeof value === "string") {
      const parsed = new Date(value);
      if (!Number.isNaN(parsed.getTime())) {
        return parsed.toISOString();
      }
    }
    const numeric = Number(value);
    if (Number.isFinite(numeric) && numeric > 0) {
      return new Date(numeric).toISOString();
    }
  }
  return new Date(fallbackMs).toISOString();
}

function buildHrFeatures(input: JsonMap): JsonMap | null {
  const explicit = pickObject(input, ["hrFeatures", "hr_features"]);
  if (explicit) {
    return explicit;
  }

  const heartRate = pickNumber(input, ["heartRate", "hr", "heart_rate"]);
  if (heartRate === null) {
    return null;
  }

  return {
    heartRate,
    heartRateAvg: pickNumber(input, ["heartRateAvg", "hrAvg", "heart_rate_avg"]),
    heartRateMin: pickNumber(input, ["heartRateMin", "hrMin", "heart_rate_min"]),
    heartRateMax: pickNumber(input, ["heartRateMax", "hrMax", "heart_rate_max"]),
  };
}

function buildSpo2Features(input: JsonMap): JsonMap | null {
  const explicit = pickObject(input, ["spo2Features", "spo2_features"]);
  if (explicit) {
    return explicit;
  }

  const bloodOxygen = pickNumber(input, ["bloodOxygen", "spo2", "blood_oxygen"]);
  if (bloodOxygen === null) {
    return null;
  }

  return {
    bloodOxygen,
    bloodOxygenAvg: pickNumber(input, ["bloodOxygenAvg", "spo2Avg", "blood_oxygen_avg"]),
    bloodOxygenMin: pickNumber(input, ["bloodOxygenMin", "spo2Min", "blood_oxygen_min"]),
  };
}

function buildHrvFeatures(input: JsonMap): JsonMap | null {
  const explicit = pickObject(input, ["hrvFeatures", "hrv_features"]);
  if (explicit) {
    return explicit;
  }

  const hrv = pickNumber(input, ["hrv", "hrvCurrent", "hrv_current"]);
  if (hrv === null) {
    return null;
  }

  return {
    hrv,
    rmssd: pickNumber(input, ["rmssd"]),
    sdnn: pickNumber(input, ["sdnn"]),
    lfHfRatio: pickNumber(input, ["lfHfRatio", "lf_hf_ratio"]),
  };
}

function buildTempFeatures(input: JsonMap): JsonMap | null {
  const explicit = pickObject(input, ["tempFeatures", "temp_features"]);
  if (explicit) {
    return explicit;
  }

  const temperature = pickNumber(input, ["temperature", "temp", "temperatureCurrent", "temperature_current"]);
  if (temperature === null) {
    return null;
  }

  return {
    temperature,
    temperatureAvg: pickNumber(input, ["temperatureAvg", "tempAvg", "temperature_avg"]),
    temperatureTrend: pickNumber(input, ["temperatureTrend", "tempTrend", "temperature_trend"]),
  };
}

function buildMotionFeatures(input: JsonMap): JsonMap | null {
  const explicit = pickObject(input, ["motionFeatures", "motion_features"]);
  if (explicit) {
    return explicit;
  }

  const motion = asObject(input.motion);
  const accelerometer = input.accelerometer;
  const gyroscope = input.gyroscope;
  const motionIntensity = pickNumber(input, ["motionIntensity", "motion_intensity", "accMagnitude", "acc_magnitude"]);

  if (!motion && accelerometer === undefined && gyroscope === undefined && motionIntensity === null) {
    return null;
  }

  return {
    ...(motion ?? {}),
    accelerometer,
    gyroscope,
    motionIntensity,
  };
}

function buildPpgFeatures(input: JsonMap): JsonMap | null {
  const explicit = pickObject(input, ["ppgFeatures", "ppg_features"]);
  if (explicit) {
    return explicit;
  }

  const ppg = input.ppg;
  const ppgValue = pickNumber(input, ["ppgValue", "ppg_value"]);
  if (ppg === undefined && ppgValue === null) {
    return null;
  }

  return {
    ppg,
    ppgValue,
  };
}

export function extractWindowFeatureRow(payload: unknown, fallbackTimestampMs: number): WindowFeatureRow {
  const input = asObject(payload) ?? {};
  const windowStart = pickTime(input, ["windowStart", "window_start", "timestamp", "ts"], fallbackTimestampMs);
  const windowEnd = pickTime(
    input,
    ["windowEnd", "window_end"],
    new Date(windowStart).getTime() + 30_000
  );

  const hrFeatures = buildHrFeatures(input);
  const spo2Features = buildSpo2Features(input);
  const hrvFeatures = buildHrvFeatures(input);
  const tempFeatures = buildTempFeatures(input);
  const motionFeatures = buildMotionFeatures(input);
  const ppgFeatures = buildPpgFeatures(input);
  const edgeAnomalySignal = pickNumber(input, [
    "edgeAnomalySignal",
    "edge_anomaly_signal",
    "anomalyScore",
    "anomaly_score",
  ]);

  const modalityCount = [hrFeatures, spo2Features, hrvFeatures, tempFeatures, motionFeatures, ppgFeatures].filter(
    (value) => value !== null
  ).length;

  return {
    windowStart,
    windowEnd,
    hrFeatures,
    spo2Features,
    hrvFeatures,
    tempFeatures,
    motionFeatures,
    ppgFeatures,
    edgeAnomalySignal,
    modalityCount,
  };
}
