import demoConfig from "@/lib/demo/demo-accounts.config.json";

const DAY_MS = 24 * 60 * 60 * 1000;

export type DemoRole = "demo_user" | "demo_admin";
export type DemoScenario =
  | "demo_baseline_recovery"
  | "demo_report_doctor_loop"
  | "demo_lifestyle_loop"
  | "demo_live_intervention"
  | "demo_high_risk_ops"
  | "demo_admin_console";

type DemoAccountConfig = {
  role: DemoRole;
  scenario: DemoScenario;
  displayName: string;
};

type DemoMetadata = {
  demoRole: DemoRole | "";
  demoScenario: DemoScenario | "";
  demoSeedVersion: string;
  displayName: string;
};

export type DemoBootstrapSnapshot = {
  devices: DemoDevicePayload[];
  sleepRecords: DemoSleepRecordPayload[];
  healthMetrics: DemoHealthMetricsPayload[];
  recoveryScores: DemoRecoveryScorePayload[];
  doctorSessions: DemoDoctorSessionPayload[];
  doctorMessages: DemoDoctorMessagePayload[];
  doctorAssessments: DemoDoctorAssessmentPayload[];
  assessmentSessions: DemoAssessmentSessionPayload[];
  assessmentAnswers: DemoAssessmentAnswerPayload[];
  interventionTasks: DemoInterventionTaskPayload[];
  interventionExecutions: DemoInterventionExecutionPayload[];
  medicalReports: DemoMedicalReportPayload[];
  medicalMetrics: DemoMedicalMetricPayload[];
  relaxSessions: DemoRelaxSessionPayload[];
  interventionProfileSnapshots: DemoInterventionProfileSnapshotPayload[];
  prescriptionBundles: DemoPrescriptionBundlePayload[];
  prescriptionItems: DemoPrescriptionItemPayload[];
  medicationRecords: DemoMedicationRecordPayload[];
  foodRecords: DemoFoodRecordPayload[];
};

export type DemoBootstrapPayload = {
  demoRole: DemoRole;
  demoScenario: DemoScenario;
  demoSeedVersion: string;
  displayName: string;
  snapshot: DemoBootstrapSnapshot;
};

export type DemoDevicePayload = {
  deviceId: string;
  deviceName: string;
  macAddress: string;
  batteryLevel: number;
  firmwareVersion: string;
  connectionState: string;
  lastSyncTime: number;
  isPrimary: boolean;
  createdAt: number;
  updatedAt: number;
};

export type DemoSleepRecordPayload = {
  id: string;
  date: number;
  bedTime: number;
  wakeTime: number;
  totalSleepMinutes: number;
  deepSleepMinutes: number;
  lightSleepMinutes: number;
  remSleepMinutes: number;
  awakeMinutes: number;
  sleepEfficiency: number;
  fallAsleepMinutes: number;
  awakeCount: number;
  createdAt: number;
  updatedAt: number;
};

export type DemoHealthMetricsPayload = {
  id: string;
  sleepRecordId: string;
  timestamp: number;
  heartRateSample: number;
  bloodOxygenSample: number;
  temperatureSample: number;
  stepsSample: number;
  accMagnitudeSample: number;
  heartRateCurrent: number;
  heartRateAvg: number;
  heartRateMin: number;
  heartRateMax: number;
  heartRateTrend: string;
  bloodOxygenCurrent: number;
  bloodOxygenAvg: number;
  bloodOxygenMin: number;
  bloodOxygenStability: string;
  temperatureCurrent: number;
  temperatureAvg: number;
  temperatureStatus: string;
  hrvCurrent: number;
  hrvBaseline: number;
  hrvRecoveryRate: number;
  hrvTrend: string;
};

export type DemoRecoveryScorePayload = {
  id: string;
  sleepRecordId: string;
  date: number;
  score: number;
  sleepEfficiencyScore: number;
  hrvRecoveryScore: number;
  deepSleepScore: number;
  temperatureRhythmScore: number;
  oxygenStabilityScore: number;
  level: string;
  createdAt: number;
};

export type DemoDoctorSessionPayload = {
  id: string;
  createdAt: number;
  updatedAt: number;
  status: string;
  domain: string;
  chiefComplaint: string;
  riskLevel: string;
};

export type DemoDoctorMessagePayload = {
  id: string;
  sessionId: string;
  role: string;
  messageType: string;
  content: string;
  timestamp: number;
  payloadJson: string | null;
  actionProtocolType: string | null;
  actionDurationSec: number | null;
};

export type DemoDoctorAssessmentPayload = {
  id: string;
  sessionId: string;
  createdAt: number;
  suspectedIssuesJson: string;
  symptomFactsJson: string;
  missingInfoJson: string;
  redFlagsJson: string;
  recommendedDepartment: string;
  doctorSummary: string;
  nextStepAdviceJson: string;
  disclaimer: string;
};

export type DemoAssessmentSessionPayload = {
  id: string;
  scaleCode: string;
  startedAt: number;
  completedAt: number | null;
  totalScore: number;
  severityLevel: string;
  freshnessUntil: number;
  source: string;
};

export type DemoAssessmentAnswerPayload = {
  id: string;
  sessionId: string;
  itemCode: string;
  itemOrder: number;
  answerValue: number;
};

export type DemoInterventionTaskPayload = {
  id: string;
  date: number;
  sourceType: string;
  triggerReason: string;
  bodyZone: string;
  protocolType: string;
  durationSec: number;
  plannedAt: number;
  status: string;
  createdAt: number;
  updatedAt: number;
};

export type DemoInterventionExecutionPayload = {
  id: string;
  taskId: string;
  startedAt: number;
  endedAt: number;
  elapsedSec: number;
  beforeStress: number;
  afterStress: number;
  beforeHr: number;
  afterHr: number;
  effectScore: number;
  completionType: string;
  metadataJson: string | null;
};

export type DemoMedicalReportPayload = {
  id: string;
  reportDate: number;
  reportType: string;
  imageUri: string;
  ocrTextDigest: string;
  parseStatus: string;
  riskLevel: string;
  createdAt: number;
};

export type DemoMedicalMetricPayload = {
  id: string;
  reportId: string;
  metricCode: string;
  metricName: string;
  metricValue: number;
  unit: string;
  refLow: number | null;
  refHigh: number | null;
  isAbnormal: boolean;
  confidence: number;
};

export type DemoRelaxSessionPayload = {
  id: string;
  startTime: number;
  endTime: number;
  protocolType: string;
  durationSec: number;
  preStress: number;
  postStress: number;
  preHr: number;
  postHr: number;
  preHrv: number;
  postHrv: number;
  preMotion: number;
  postMotion: number;
  effectScore: number;
  metadataJson: string | null;
};

export type DemoInterventionProfileSnapshotPayload = {
  id: string;
  generatedAt: number;
  triggerType: string;
  domainScoresJson: string;
  evidenceFactsJson: string;
  redFlagsJson: string;
};

export type DemoPrescriptionBundlePayload = {
  id: string;
  createdAt: number;
  triggerType: string;
  profileSnapshotId: string;
  primaryGoal: string;
  riskLevel: string;
  rationale: string;
  evidenceJson: string;
  status: string;
};

export type DemoPrescriptionItemPayload = {
  id: string;
  bundleId: string;
  itemType: string;
  protocolCode: string;
  assetRef: string;
  durationSec: number;
  sequenceOrder: number;
  timingSlot: string;
  isRequired: boolean;
  status: string;
};

export type DemoMedicationRecordPayload = {
  id: string;
  capturedAt: number;
  imageUri: string;
  recognizedName: string;
  dosageForm: string;
  specification: string;
  activeIngredientsJson: string;
  matchedSymptomsJson: string;
  usageSummary: string;
  riskLevel: string;
  riskFlagsJson: string;
  evidenceNotesJson: string;
  advice: string;
  confidence: number;
  requiresManualReview: boolean;
  analysisMode: string;
  providerId: string | null;
  modelId: string | null;
  traceId: string | null;
  syncState: string;
  cloudRecordId: string | null;
  syncedAt: number | null;
  createdAt: number;
  updatedAt: number;
};

export type DemoFoodRecordPayload = {
  id: string;
  capturedAt: number;
  imageUri: string;
  mealType: string;
  foodItemsJson: string;
  estimatedCalories: number;
  carbohydrateGrams: number;
  proteinGrams: number;
  fatGrams: number;
  nutritionRiskLevel: string;
  nutritionFlagsJson: string;
  dailyContribution: string;
  advice: string;
  confidence: number;
  requiresManualReview: boolean;
  analysisMode: string;
  providerId: string | null;
  modelId: string | null;
  traceId: string | null;
  syncState: string;
  cloudRecordId: string | null;
  syncedAt: number | null;
  createdAt: number;
  updatedAt: number;
};

const accountConfigs = (demoConfig.accounts as DemoAccountConfig[]).reduce<
  Record<string, DemoAccountConfig>
>((acc, item) => {
  acc[item.scenario] = item;
  return acc;
}, {});

function json(value: unknown): string {
  return JSON.stringify(value);
}

function clamp(value: number, min: number, max: number): number {
  return Math.max(min, Math.min(max, value));
}

function dayStart(now: number, offsetDays: number): number {
  const date = new Date(now + offsetDays * DAY_MS);
  date.setHours(0, 0, 0, 0);
  return date.getTime();
}

function sleepRecordId(prefix: string, offsetDays: number): string {
  return `${prefix}-sleep-${Math.abs(offsetDays)}`;
}

function makeAssessmentSession(
  prefix: string,
  scaleCode: string,
  totalScore: number,
  severityLevel: string,
  completedAt: number
): DemoAssessmentSessionPayload {
  return {
    id: `${prefix}-${scaleCode.toLowerCase()}-session`,
    scaleCode,
    startedAt: completedAt - 8 * 60 * 1000,
    completedAt,
    totalScore,
    severityLevel,
    freshnessUntil: completedAt + 21 * DAY_MS,
    source: "DEMO_BOOTSTRAP",
  };
}

function makeDoctorLoop(
  prefix: string,
  now: number,
  chiefComplaint: string,
  riskLevel: string,
  doctorSummary: string,
  followUpQuestion: string,
  suspectedIssues: Array<{ name: string; rationale: string; confidence: number }>,
  symptomFacts: string[],
  missingInfo: string[],
  redFlags: string[],
  nextSteps: string[],
  recommendedDepartment: string
): {
  session: DemoDoctorSessionPayload;
  messages: DemoDoctorMessagePayload[];
  assessment: DemoDoctorAssessmentPayload;
} {
  const sessionId = `${prefix}-doctor-session`;
  const createdAt = now - 11 * 60 * 60 * 1000;
  return {
    session: {
      id: sessionId,
      createdAt,
      updatedAt: createdAt + 16 * 60 * 1000,
      status: "CLOSED",
      domain: "SLEEP_AND_RECOVERY",
      chiefComplaint,
      riskLevel,
    },
    messages: [
      {
        id: `${prefix}-doctor-message-user-1`,
        sessionId,
        role: "USER",
        messageType: "TEXT",
        content: chiefComplaint,
        timestamp: createdAt + 2 * 60 * 1000,
        payloadJson: null,
        actionProtocolType: null,
        actionDurationSec: null,
      },
      {
        id: `${prefix}-doctor-message-ai-1`,
        sessionId,
        role: "ASSISTANT",
        messageType: "TEXT",
        content: followUpQuestion,
        timestamp: createdAt + 4 * 60 * 1000,
        payloadJson: null,
        actionProtocolType: null,
        actionDurationSec: null,
      },
      {
        id: `${prefix}-doctor-message-user-2`,
        sessionId,
        role: "USER",
        messageType: "TEXT",
        content: symptomFacts.join("；"),
        timestamp: createdAt + 8 * 60 * 1000,
        payloadJson: null,
        actionProtocolType: null,
        actionDurationSec: null,
      },
      {
        id: `${prefix}-doctor-message-ai-2`,
        sessionId,
        role: "ASSISTANT",
        messageType: "ASSESSMENT",
        content: doctorSummary,
        timestamp: createdAt + 16 * 60 * 1000,
        payloadJson: json({
          riskLevel,
          recommendedDepartment,
          nextStepAdvice: nextSteps,
        }),
        actionProtocolType: "BREATH_4_6",
        actionDurationSec: 180,
      },
    ],
    assessment: {
      id: `${prefix}-doctor-assessment`,
      sessionId,
      createdAt: createdAt + 16 * 60 * 1000,
      suspectedIssuesJson: json(suspectedIssues),
      symptomFactsJson: json(symptomFacts),
      missingInfoJson: json(missingInfo),
      redFlagsJson: json(redFlags),
      recommendedDepartment,
      doctorSummary,
      nextStepAdviceJson: json(nextSteps),
      disclaimer: "该问诊单仅用于演示健康辅助闭环，不替代医生面对面诊疗。",
    },
  };
}

function makeSleepSeries(
  prefix: string,
  now: number,
  days: number,
  seed: {
    totalSleepBase: number;
    totalSleepSlope: number;
    recoveryBase: number;
    recoverySlope: number;
    hrBase: number;
    hrvBase: number;
    oxygenBase: number;
    temperatureBase: number;
    awakeBase: number;
  }
): {
  sleepRecords: DemoSleepRecordPayload[];
  healthMetrics: DemoHealthMetricsPayload[];
  recoveryScores: DemoRecoveryScorePayload[];
} {
  const sleepRecords: DemoSleepRecordPayload[] = [];
  const healthMetrics: DemoHealthMetricsPayload[] = [];
  const recoveryScores: DemoRecoveryScorePayload[] = [];

  for (let index = days - 1; index >= 0; index -= 1) {
    const offset = -index;
    const date = dayStart(now, offset);
    const totalSleepMinutes = clamp(
      Math.round(seed.totalSleepBase + seed.totalSleepSlope * (days - index - 1)),
      240,
      560
    );
    const deepSleepMinutes = Math.round(totalSleepMinutes * 0.22);
    const remSleepMinutes = Math.round(totalSleepMinutes * 0.21);
    const awakeMinutes = clamp(seed.awakeBase - Math.floor((days - index - 1) / 4), 8, 48);
    const lightSleepMinutes = totalSleepMinutes - deepSleepMinutes - remSleepMinutes;
    const bedTime = date - (7 * 60 + 20) * 60 * 1000;
    const wakeTime = bedTime + (totalSleepMinutes + awakeMinutes) * 60 * 1000;
    const sleepEfficiency = clamp(
      Math.round((totalSleepMinutes / (totalSleepMinutes + awakeMinutes)) * 1000) / 10,
      68,
      97
    );
    const fallAsleepMinutes = clamp(18 - Math.floor((days - index - 1) / 5), 8, 28);
    const awakeCount = clamp(3 - Math.floor((days - index - 1) / 9), 0, 5);
    const score = clamp(
      Math.round(seed.recoveryBase + seed.recoverySlope * (days - index - 1)),
      35,
      92
    );
    const heartRateAvg = clamp(seed.hrBase - Math.floor((days - index - 1) / 6), 54, 96);
    const heartRateCurrent = clamp(heartRateAvg + 2, 54, 100);
    const bloodOxygenAvg = clamp(seed.oxygenBase + Math.floor((days - index - 1) / 10), 92, 99);
    const hrvCurrent = clamp(seed.hrvBase + Math.floor((days - index - 1) * 0.8), 18, 62);
    const hrvBaseline = clamp(seed.hrvBase + 4, 22, 64);
    const metricTimestamp = date + 8 * 60 * 60 * 1000;
    const sleepId = sleepRecordId(prefix, offset);

    sleepRecords.push({
      id: sleepId,
      date,
      bedTime,
      wakeTime,
      totalSleepMinutes,
      deepSleepMinutes,
      lightSleepMinutes,
      remSleepMinutes,
      awakeMinutes,
      sleepEfficiency,
      fallAsleepMinutes,
      awakeCount,
      createdAt: metricTimestamp,
      updatedAt: metricTimestamp,
    });

    healthMetrics.push({
      id: `${sleepId}-metrics`,
      sleepRecordId: sleepId,
      timestamp: metricTimestamp,
      heartRateSample: heartRateCurrent,
      bloodOxygenSample: bloodOxygenAvg,
      temperatureSample: seed.temperatureBase,
      stepsSample: 4200 + (days - index - 1) * 180,
      accMagnitudeSample: 1.6 - (days - index - 1) * 0.01,
      heartRateCurrent,
      heartRateAvg,
      heartRateMin: clamp(heartRateAvg - 7, 42, 88),
      heartRateMax: clamp(heartRateAvg + 12, 64, 120),
      heartRateTrend: heartRateAvg >= seed.hrBase ? "STABLE" : "DOWN",
      bloodOxygenCurrent: bloodOxygenAvg,
      bloodOxygenAvg,
      bloodOxygenMin: clamp(bloodOxygenAvg - 2, 88, 97),
      bloodOxygenStability: bloodOxygenAvg >= 96 ? "STABLE" : "WATCH",
      temperatureCurrent: seed.temperatureBase,
      temperatureAvg: seed.temperatureBase,
      temperatureStatus: "STABLE",
      hrvCurrent,
      hrvBaseline,
      hrvRecoveryRate: Math.round((hrvCurrent / Math.max(hrvBaseline, 1)) * 100) / 100,
      hrvTrend: hrvCurrent >= hrvBaseline ? "UP" : "STABLE",
    });

    recoveryScores.push({
      id: `${sleepId}-recovery`,
      sleepRecordId: sleepId,
      date,
      score,
      sleepEfficiencyScore: sleepEfficiency,
      hrvRecoveryScore: clamp(score + 4, 0, 100),
      deepSleepScore: clamp(Math.round((deepSleepMinutes / Math.max(totalSleepMinutes, 1)) * 100), 0, 100),
      temperatureRhythmScore: clamp(score - 2, 0, 100),
      oxygenStabilityScore: clamp(bloodOxygenAvg, 0, 100),
      level: score >= 80 ? "GOOD" : score >= 60 ? "FAIR" : "POOR",
      createdAt: metricTimestamp,
    });
  }

  return { sleepRecords, healthMetrics, recoveryScores };
}

function makeProfileSnapshot(
  prefix: string,
  now: number,
  domainScores: Record<string, number>,
  evidenceFacts: Record<string, string[]>,
  redFlags: string[]
): DemoInterventionProfileSnapshotPayload {
  return {
    id: `${prefix}-profile-snapshot`,
    generatedAt: now - 2 * 60 * 60 * 1000,
    triggerType: "DAILY_REFRESH",
    domainScoresJson: json(domainScores),
    evidenceFactsJson: json(evidenceFacts),
    redFlagsJson: json(redFlags),
  };
}

function makePrescriptionBundle(
  prefix: string,
  now: number,
  profileSnapshotId: string,
  primaryGoal: string,
  riskLevel: string,
  rationale: string,
  evidence: string[],
  items: Array<{
    protocolCode: string;
    assetRef: string;
    durationSec: number;
    sequenceOrder: number;
    timingSlot: string;
    itemType?: string;
    isRequired?: boolean;
  }>
): {
  bundle: DemoPrescriptionBundlePayload;
  items: DemoPrescriptionItemPayload[];
} {
  const bundleId = `${prefix}-prescription-bundle`;
  return {
    bundle: {
      id: bundleId,
      createdAt: now - 90 * 60 * 1000,
      triggerType: "DAILY_REFRESH",
      profileSnapshotId,
      primaryGoal,
      riskLevel,
      rationale,
      evidenceJson: json(evidence),
      status: "ACTIVE",
    },
    items: items.map((item) => ({
      id: `${bundleId}-item-${item.sequenceOrder}`,
      bundleId,
      itemType: item.itemType ?? "INTERVENTION",
      protocolCode: item.protocolCode,
      assetRef: item.assetRef,
      durationSec: item.durationSec,
      sequenceOrder: item.sequenceOrder,
      timingSlot: item.timingSlot,
      isRequired: item.isRequired ?? true,
      status: "PENDING",
    })),
  };
}

function makeTask(
  prefix: string,
  index: number,
  timestamp: number,
  protocolType: string,
  status: string,
  triggerReason: string,
  bodyZone: string,
  durationSec: number
): DemoInterventionTaskPayload {
  return {
    id: `${prefix}-task-${index}`,
    date: dayStart(timestamp, 0),
    sourceType: "RECOMMENDATION",
    triggerReason,
    bodyZone,
    protocolType,
    durationSec,
    plannedAt: timestamp,
    status,
    createdAt: timestamp - 20 * 60 * 1000,
    updatedAt: timestamp,
  };
}

function makeExecution(
  taskId: string,
  prefix: string,
  index: number,
  startedAt: number,
  durationSec: number,
  beforeStress: number,
  afterStress: number,
  beforeHr: number,
  afterHr: number,
  effectScore: number,
  modality: string
): DemoInterventionExecutionPayload {
  return {
    id: `${prefix}-execution-${index}`,
    taskId,
    startedAt,
    endedAt: startedAt + durationSec * 1000,
    elapsedSec: durationSec,
    beforeStress,
    afterStress,
    beforeHr,
    afterHr,
    effectScore,
    completionType: "FULL",
    metadataJson: json({
      modality,
      sessionVariant: modality,
      hapticEnabled: modality === "BREATH_VISUAL" || modality === "HAPTIC",
      realtimeSignalAvailable: true,
      avgRelaxSignal: Math.round(effectScore),
      completionQuality: Math.round(effectScore),
    }),
  };
}

function makeRelaxSession(
  prefix: string,
  index: number,
  startedAt: number,
  durationSec: number,
  protocolType: string,
  effectScore: number,
  modality: string
): DemoRelaxSessionPayload {
  return {
    id: `${prefix}-relax-${index}`,
    startTime: startedAt,
    endTime: startedAt + durationSec * 1000,
    protocolType,
    durationSec,
    preStress: 72 - index * 4,
    postStress: 44 - index * 2,
    preHr: 84 - index,
    postHr: 68 - index,
    preHrv: 26 + index,
    postHrv: 34 + index,
    preMotion: 1.8 - index * 0.1,
    postMotion: 0.7 - index * 0.05,
    effectScore,
    metadataJson: json({
      modality,
      sessionVariant: protocolType,
      hapticEnabled: protocolType.startsWith("BREATH"),
      realtimeSignalAvailable: true,
      avgRelaxSignal: Math.round(effectScore),
      manualAdjustCount: modality === "SOUNDSCAPE" ? 3 : 1,
      completionQuality: Math.round(effectScore),
    }),
  };
}

function makeMedicationRecord(
  prefix: string,
  now: number,
  overrides: Partial<DemoMedicationRecordPayload>
): DemoMedicationRecordPayload {
  const capturedAt = now - 6 * 60 * 60 * 1000;
  return {
    id: `${prefix}-medication-record`,
    capturedAt,
    imageUri: `demo://images/${prefix}/medication.jpg`,
    recognizedName: "褪黑素缓释片",
    dosageForm: "片剂",
    specification: "2mg x 20片",
    activeIngredientsJson: json(["褪黑素"]),
    matchedSymptomsJson: json(["入睡困难", "睡前思绪活跃"]),
    usageSummary: "用于睡前辅助节律调整，不替代失眠诊疗。",
    riskLevel: "LOW",
    riskFlagsJson: json(["连续使用前建议先确认作息和既往用药情况"]),
    evidenceNotesJson: json(["最近 3 天睡前仍存在高唤醒，建议先配合作息干预"]),
    advice: "建议结合固定睡前流程使用，如需长期服用请先咨询医生或药师。",
    confidence: 0.91,
    requiresManualReview: false,
    analysisMode: "CLOUD_IMAGE_PARSE",
    providerId: "vector_engine",
    modelId: "qwen3-vl-235b-a22b-instruct",
    traceId: `${prefix}-medication-trace`,
    syncState: "SYNCED",
    cloudRecordId: `${prefix}-medication-cloud`,
    syncedAt: capturedAt + 2 * 60 * 1000,
    createdAt: capturedAt,
    updatedAt: capturedAt + 2 * 60 * 1000,
    ...overrides,
  };
}

function makeFoodRecord(
  prefix: string,
  key: string,
  capturedAt: number,
  mealType: string,
  foodItems: string[],
  estimatedCalories: number,
  nutritionRiskLevel: string,
  nutritionFlags: string[],
  dailyContribution: string,
  advice: string,
  overrides: Partial<DemoFoodRecordPayload> = {}
): DemoFoodRecordPayload {
  return {
    id: `${prefix}-food-${key}`,
    capturedAt,
    imageUri: `demo://images/${prefix}/food-${key}.jpg`,
    mealType,
    foodItemsJson: json(foodItems),
    estimatedCalories,
    carbohydrateGrams: Math.round((estimatedCalories * 0.45) / 4 * 10) / 10,
    proteinGrams: Math.round((estimatedCalories * 0.2) / 4 * 10) / 10,
    fatGrams: Math.round((estimatedCalories * 0.35) / 9 * 10) / 10,
    nutritionRiskLevel,
    nutritionFlagsJson: json(nutritionFlags),
    dailyContribution,
    advice,
    confidence: 0.88,
    requiresManualReview: false,
    analysisMode: "CLOUD_IMAGE_PARSE",
    providerId: "vector_engine",
    modelId: "qwen3-vl-235b-a22b-instruct",
    traceId: `${prefix}-food-${key}-trace`,
    syncState: "SYNCED",
    cloudRecordId: `${prefix}-food-${key}-cloud`,
    syncedAt: capturedAt + 60 * 1000,
    createdAt: capturedAt,
    updatedAt: capturedAt + 60 * 1000,
    ...overrides,
  };
}

function buildBaselineRecoveryScenario(now: number): DemoBootstrapSnapshot {
  const prefix = "demo-baseline";
  const series = makeSleepSeries(prefix, now, 30, {
    totalSleepBase: 410,
    totalSleepSlope: 2.4,
    recoveryBase: 61,
    recoverySlope: 0.7,
    hrBase: 72,
    hrvBase: 31,
    oxygenBase: 96,
    temperatureBase: 36.5,
    awakeBase: 24,
  });
  const profile = makeProfileSnapshot(
    prefix,
    now,
    {
      sleepDisturbance: 32,
      stressLoad: 28,
      fatigueLoad: 34,
      recoveryCapacity: 76,
      anxietyRisk: 24,
      depressiveRisk: 18,
      adherenceReadiness: 78,
      medicationRisk: 20,
      nutritionRisk: 26,
    },
    {
      sleepDisturbance: ["近 7 天平均睡眠约 7.3 小时", "本周夜间觉醒次数较上周下降"],
      recoveryCapacity: ["HRV 已回到个人基线附近", "本周平均恢复分维持在 70 分以上"],
      adherenceReadiness: ["最近 5 次干预任务完成 4 次", "更容易完成短时呼吸和轻音景"],
    },
    []
  );
  const prescription = makePrescriptionBundle(
    prefix,
    now,
    profile.id,
    "保持稳定节律并巩固恢复分",
    "LOW",
    "当前恢复基础较稳，重点是维持规律而不是频繁切换方案。",
    ["恢复分连续 7 天稳定在中高区间", "睡眠效率维持在 90% 左右"],
    [
      { protocolCode: "SOUNDSCAPE_SLEEP_AUDIO_15M", assetRef: "audio://soundscape/15m", durationSec: 900, sequenceOrder: 1, timingSlot: "BEFORE_SLEEP" },
      { protocolCode: "BREATH_4_6", assetRef: "breathing://4-6", durationSec: 180, sequenceOrder: 2, timingSlot: "EVENING" },
      { protocolCode: "TASK_DAYLIGHT_WALK", assetRef: "task://daylight-walk", durationSec: 600, sequenceOrder: 3, timingSlot: "MORNING", itemType: "TASK", isRequired: false },
    ]
  );
  const task1 = makeTask(prefix, 1, now - 18 * 60 * 60 * 1000, "BREATH_4_6", "COMPLETED", "晨报恢复分维持稳定", "CHEST", 180);
  const task2 = makeTask(prefix, 2, now - 4 * 60 * 60 * 1000, "SOUNDSCAPE_SLEEP_AUDIO_15M", "PENDING", "保持睡前流程稳定", "WHOLE_BODY", 900);
  const task3 = makeTask(prefix, 3, now - 2 * DAY_MS, "TASK_DAYLIGHT_WALK", "COMPLETED", "白天维持轻活动", "LEGS", 600);
  const doctor = makeDoctorLoop(
    prefix,
    now,
    "最近睡眠整体稳定，偶尔晚睡后第二天略疲劳。",
    "LOW",
    "当前没有新的高风险提示，建议继续维持规律作息并减少睡前刺激。",
    "最近一周有没有连续两天以上明显失眠或早醒？",
    [{ name: "睡前节律轻度波动", rationale: "偶有晚睡但恢复分整体稳定", confidence: 63 }],
    ["近 7 天睡眠节律整体稳定", "偶有晚睡后次日轻度疲劳"],
    ["最近是否饮用含咖啡因饮料"],
    [],
    ["继续保持固定睡前流程", "如果连续 3 天恢复分下滑，再重新评估"],
    "睡眠医学科"
  );
  const lowRiskReportId = `${prefix}-report`;
  return {
    devices: [
      {
        deviceId: `${prefix}-device`,
        deviceName: "长庚环 演示戒指",
        macAddress: "AA:BB:CC:00:11:22",
        batteryLevel: 83,
        firmwareVersion: "1.3.2-demo",
        connectionState: "DISCONNECTED",
        lastSyncTime: now - 2 * 60 * 60 * 1000,
        isPrimary: true,
        createdAt: now - 10 * DAY_MS,
        updatedAt: now - 2 * 60 * 60 * 1000,
      },
    ],
    sleepRecords: series.sleepRecords,
    healthMetrics: series.healthMetrics,
    recoveryScores: series.recoveryScores,
    doctorSessions: [doctor.session],
    doctorMessages: doctor.messages,
    doctorAssessments: [doctor.assessment],
    assessmentSessions: [
      makeAssessmentSession(prefix, "ISI", 8, "轻度", now - 4 * DAY_MS),
      makeAssessmentSession(prefix, "ESS", 6, "正常", now - 4 * DAY_MS),
      makeAssessmentSession(prefix, "PSS10", 13, "轻度压力", now - 4 * DAY_MS),
      makeAssessmentSession(prefix, "GAD7", 4, "轻度", now - 4 * DAY_MS),
      makeAssessmentSession(prefix, "PHQ9", 3, "轻度", now - 4 * DAY_MS),
      makeAssessmentSession(prefix, "WHO5", 18, "良好", now - 4 * DAY_MS),
    ],
    assessmentAnswers: [],
    interventionTasks: [task1, task2, task3],
    interventionExecutions: [
      makeExecution(task1.id, prefix, 1, task1.plannedAt, 180, 68, 42, 80, 68, 79, "BREATH_VISUAL"),
      makeExecution(task3.id, prefix, 2, task3.plannedAt, 600, 54, 40, 78, 71, 73, "TASK"),
    ],
    medicalReports: [
      {
        id: lowRiskReportId,
        reportDate: now - 12 * DAY_MS,
        reportType: "体检报告",
        imageUri: `demo://reports/${prefix}.pdf`,
        ocrTextDigest: "年度体检主要指标稳定，无需额外警报。",
        parseStatus: "PARSED",
        riskLevel: "LOW",
        createdAt: now - 12 * DAY_MS,
      },
    ],
    medicalMetrics: [
      {
        id: `${lowRiskReportId}-metric-1`,
        reportId: lowRiskReportId,
        metricCode: "GLU",
        metricName: "空腹血糖",
        metricValue: 5.1,
        unit: "mmol/L",
        refLow: 3.9,
        refHigh: 6.1,
        isAbnormal: false,
        confidence: 0.92,
      },
    ],
    relaxSessions: [
      makeRelaxSession(prefix, 1, now - 18 * 60 * 60 * 1000, 180, "BREATH_4_6", 78, "BREATH_VISUAL"),
      makeRelaxSession(prefix, 2, now - 2 * DAY_MS, 900, "SOUNDSCAPE_SLEEP_AUDIO_15M", 74, "SOUNDSCAPE"),
    ],
    interventionProfileSnapshots: [profile],
    prescriptionBundles: [prescription.bundle],
    prescriptionItems: prescription.items,
    medicationRecords: [],
    foodRecords: [],
  };
}

function buildReportDoctorScenario(now: number): DemoBootstrapSnapshot {
  const prefix = "demo-report-doctor";
  const series = makeSleepSeries(prefix, now, 21, {
    totalSleepBase: 365,
    totalSleepSlope: 0.8,
    recoveryBase: 54,
    recoverySlope: 0.4,
    hrBase: 78,
    hrvBase: 26,
    oxygenBase: 95,
    temperatureBase: 36.6,
    awakeBase: 32,
  });
  const doctor = makeDoctorLoop(
    prefix,
    now,
    "最近两周凌晨易醒，白天疲劳，体检报告里也有异常指标。",
    "MEDIUM",
    "当前主要问题是睡眠维持困难叠加体检异常，建议先完成结构化问诊并把睡前减刺激和呼吸干预执行起来。",
    "最近凌晨易醒通常发生在几点？醒后多久能再次入睡？",
    [
      { name: "睡眠维持困难", rationale: "凌晨易醒、白天疲劳已持续两周", confidence: 82 },
      { name: "高唤醒伴恢复下降", rationale: "恢复分下降与问诊主诉一致", confidence: 76 },
    ],
    ["过去两周凌晨 3-4 点易醒", "醒后需要 30 分钟以上才能再次入睡", "白天精神下降且注意力变差"],
    ["是否已有固定用药", "近期是否明显增加咖啡因摄入"],
    ["若出现胸闷气短或持续加重，需优先线下就医"],
    ["先完成问诊单并标记高风险症状", "今晚优先执行睡前减刺激和 4-6 呼吸", "一周后复盘恢复分与夜间觉醒变化"],
    "睡眠医学科"
  );
  const reportId = `${prefix}-report`;
  const profile = makeProfileSnapshot(
    prefix,
    now,
    {
      sleepDisturbance: 72,
      stressLoad: 58,
      fatigueLoad: 67,
      recoveryCapacity: 44,
      anxietyRisk: 46,
      depressiveRisk: 24,
      adherenceReadiness: 61,
      medicationRisk: 22,
      nutritionRisk: 28,
    },
    {
      sleepDisturbance: ["最近两周凌晨易醒", "近 7 天平均睡眠不足 6.5 小时", "睡眠效率下降到 83% 左右"],
      fatigueLoad: ["晨起恢复分低于 60 分", "白天主观疲劳明显"],
      recoveryCapacity: ["HRV 低于个体基线", "夜间心率高于近期稳定水平"],
    },
    ["若体检异常与症状加重同步，优先线下进一步评估"]
  );
  const prescription = makePrescriptionBundle(
    prefix,
    now,
    profile.id,
    "优先稳定睡眠维持并完成报告-问诊-干预闭环",
    "MEDIUM",
    "报告异常与问诊主诉都指向恢复下降，当前建议先以保守减刺激和节律干预为主。",
    ["异常指标已进入可读报告", "问诊单已识别到睡眠维持困难和疲劳"],
    [
      { protocolCode: "SLEEP_WIND_DOWN_15M", assetRef: "session://wind-down/15m", durationSec: 900, sequenceOrder: 1, timingSlot: "BEFORE_SLEEP" },
      { protocolCode: "BREATH_4_6", assetRef: "breathing://4-6", durationSec: 180, sequenceOrder: 2, timingSlot: "BEFORE_SLEEP" },
      { protocolCode: "TASK_DOCTOR_PRIORITY", assetRef: "task://doctor-priority", durationSec: 120, sequenceOrder: 3, timingSlot: "FLEXIBLE", itemType: "TASK" },
    ]
  );
  const pendingTask = makeTask(prefix, 1, now - 3 * 60 * 60 * 1000, "SLEEP_WIND_DOWN_15M", "PENDING", "医检报告与问诊提示睡眠维持困难", "WHOLE_BODY", 900);
  const completedTask = makeTask(prefix, 2, now - DAY_MS, "BREATH_4_6", "COMPLETED", "睡前高唤醒", "CHEST", 180);
  return {
    devices: [],
    sleepRecords: series.sleepRecords,
    healthMetrics: series.healthMetrics,
    recoveryScores: series.recoveryScores,
    doctorSessions: [doctor.session],
    doctorMessages: doctor.messages,
    doctorAssessments: [doctor.assessment],
    assessmentSessions: [
      makeAssessmentSession(prefix, "ISI", 16, "中度", now - 2 * DAY_MS),
      makeAssessmentSession(prefix, "ESS", 11, "偏高", now - 2 * DAY_MS),
      makeAssessmentSession(prefix, "PSS10", 22, "中度压力", now - 2 * DAY_MS),
      makeAssessmentSession(prefix, "GAD7", 8, "轻中度", now - 2 * DAY_MS),
    ],
    assessmentAnswers: [],
    interventionTasks: [pendingTask, completedTask],
    interventionExecutions: [
      makeExecution(completedTask.id, prefix, 1, completedTask.plannedAt, 180, 77, 51, 88, 74, 72, "BREATH_VISUAL"),
    ],
    medicalReports: [
      {
        id: reportId,
        reportDate: now - 2 * DAY_MS,
        reportType: "血液检查",
        imageUri: `demo://reports/${prefix}.pdf`,
        ocrTextDigest: "血脂和空腹血糖超出参考范围，建议结合睡眠与生活方式综合评估。",
        parseStatus: "PARSED",
        riskLevel: "MEDIUM",
        createdAt: now - 2 * DAY_MS,
      },
    ],
    medicalMetrics: [
      {
        id: `${reportId}-metric-1`,
        reportId,
        metricCode: "LDL",
        metricName: "低密度脂蛋白",
        metricValue: 3.9,
        unit: "mmol/L",
        refLow: null,
        refHigh: 3.4,
        isAbnormal: true,
        confidence: 0.95,
      },
      {
        id: `${reportId}-metric-2`,
        reportId,
        metricCode: "GLU",
        metricName: "空腹血糖",
        metricValue: 6.2,
        unit: "mmol/L",
        refLow: 3.9,
        refHigh: 6.1,
        isAbnormal: true,
        confidence: 0.91,
      },
    ],
    relaxSessions: [
      makeRelaxSession(prefix, 1, now - DAY_MS, 180, "BREATH_4_6", 72, "BREATH_VISUAL"),
    ],
    interventionProfileSnapshots: [profile],
    prescriptionBundles: [prescription.bundle],
    prescriptionItems: prescription.items,
    medicationRecords: [],
    foodRecords: [],
  };
}

function buildLifestyleScenario(now: number): DemoBootstrapSnapshot {
  const prefix = "demo-lifestyle";
  const series = makeSleepSeries(prefix, now, 21, {
    totalSleepBase: 345,
    totalSleepSlope: 1.1,
    recoveryBase: 48,
    recoverySlope: 0.3,
    hrBase: 82,
    hrvBase: 23,
    oxygenBase: 95,
    temperatureBase: 36.6,
    awakeBase: 34,
  });
  const medication = makeMedicationRecord(prefix, now, {
    recognizedName: "布洛芬缓释胶囊",
    dosageForm: "胶囊",
    specification: "0.3g x 20粒",
    matchedSymptomsJson: json(["头痛", "肩颈不适"]),
    usageSummary: "用于缓解疼痛，但不能替代对睡眠和恢复下降原因的判断。",
    riskLevel: "MEDIUM",
    riskFlagsJson: json(["近期胃部不适时需谨慎", "若频繁使用建议先咨询医生"]),
    evidenceNotesJson: json(["连续两天恢复分偏低", "药物与症状的匹配度一般，需结合主诉判断"]),
    advice: "仅把本条记录作为画像参考，若头痛反复或需长期服用，请咨询医生或药师。",
    confidence: 0.82,
  });
  const breakfastAt = dayStart(now, 0) + 8 * 60 * 60 * 1000;
  const lunchAt = dayStart(now, 0) + 13 * 60 * 60 * 1000;
  const dinnerAt = dayStart(now, -1) + 20 * 60 * 60 * 1000;
  const foodRecords = [
    makeFoodRecord(prefix, "breakfast", breakfastAt, "BREAKFAST", ["甜面包", "奶茶"], 620, "HIGH", ["早餐精制糖偏高", "蛋白质明显不足"], "拉高上午血糖波动，不利于恢复分稳定。", "建议把早餐替换为高蛋白主食组合，减少甜饮。"),
    makeFoodRecord(prefix, "lunch", lunchAt, "LUNCH", ["米饭", "炸鸡", "可乐"], 860, "MEDIUM", ["午餐脂肪偏高", "蔬菜摄入不足"], "对当日恢复分形成持续负荷。", "午餐优先减少油炸和含糖饮料，补足蔬菜。"),
    makeFoodRecord(prefix, "dinner", dinnerAt, "DINNER", ["火锅", "啤酒"], 980, "HIGH", ["晚餐总热量偏高", "夜间刺激负担偏大"], "会推高夜间唤醒和次日疲劳。", "睡前 4 小时减少高油高辣和酒精摄入。"),
  ];
  const profile = makeProfileSnapshot(
    prefix,
    now,
    {
      sleepDisturbance: 58,
      stressLoad: 44,
      fatigueLoad: 63,
      recoveryCapacity: 42,
      anxietyRisk: 31,
      depressiveRisk: 19,
      adherenceReadiness: 57,
      medicationRisk: 61,
      nutritionRisk: 78,
    },
    {
      nutritionRisk: ["最近 24 小时总热量明显偏高", "早餐和晚餐都出现高风险营养标记"],
      medicationRisk: ["最近一次药物识别为止痛药", "仍需结合症状评估是否频繁使用"],
      recoveryCapacity: ["恢复分连续 3 天低于 55 分"],
    },
    ["饮食模式可能放大夜间恢复压力"]
  );
  const prescription = makePrescriptionBundle(
    prefix,
    now,
    profile.id,
    "降低药食因素对恢复分的持续干扰",
    "MEDIUM",
    "当前恢复波动与药物使用和近 24 小时饮食结构都有关，优先做可执行的生活方式调整。",
    ["饮食分析提示总热量和糖脂负担偏高", "药物识别记录已进入画像参考"],
    [
      { protocolCode: "TASK_CAFFEINE_CUTOFF", assetRef: "task://caffeine-cutoff", durationSec: 60, sequenceOrder: 1, timingSlot: "AFTERNOON", itemType: "TASK" },
      { protocolCode: "SOUNDSCAPE_SLEEP_AUDIO_15M", assetRef: "audio://soundscape/15m", durationSec: 900, sequenceOrder: 2, timingSlot: "BEFORE_SLEEP" },
      { protocolCode: "COGNITIVE_OFFLOAD_5M", assetRef: "session://cognitive-offload/5m", durationSec: 300, sequenceOrder: 3, timingSlot: "EVENING" },
    ]
  );
  return {
    devices: [],
    sleepRecords: series.sleepRecords,
    healthMetrics: series.healthMetrics,
    recoveryScores: series.recoveryScores,
    doctorSessions: [],
    doctorMessages: [],
    doctorAssessments: [],
    assessmentSessions: [
      makeAssessmentSession(prefix, "PSS10", 18, "中度压力", now - 3 * DAY_MS),
      makeAssessmentSession(prefix, "ESS", 9, "轻度", now - 3 * DAY_MS),
      makeAssessmentSession(prefix, "WHO5", 12, "一般", now - 3 * DAY_MS),
    ],
    assessmentAnswers: [],
    interventionTasks: [
      makeTask(prefix, 1, now - 90 * 60 * 1000, "COGNITIVE_OFFLOAD_5M", "PENDING", "药食画像提示恢复波动", "HEAD", 300),
    ],
    interventionExecutions: [],
    medicalReports: [],
    medicalMetrics: [],
    relaxSessions: [
      makeRelaxSession(prefix, 1, now - 2 * DAY_MS, 300, "COGNITIVE_OFFLOAD_5M", 67, "ZEN"),
    ],
    interventionProfileSnapshots: [profile],
    prescriptionBundles: [prescription.bundle],
    prescriptionItems: prescription.items,
    medicationRecords: [medication],
    foodRecords,
  };
}

function buildLiveInterventionScenario(now: number): DemoBootstrapSnapshot {
  const prefix = "demo-live-intervention";
  const series = makeSleepSeries(prefix, now, 14, {
    totalSleepBase: 352,
    totalSleepSlope: 1.3,
    recoveryBase: 46,
    recoverySlope: 0.5,
    hrBase: 80,
    hrvBase: 24,
    oxygenBase: 95,
    temperatureBase: 36.5,
    awakeBase: 30,
  });
  const profile = makeProfileSnapshot(
    prefix,
    now,
    {
      sleepDisturbance: 61,
      stressLoad: 52,
      fatigueLoad: 56,
      recoveryCapacity: 47,
      anxietyRisk: 34,
      depressiveRisk: 18,
      adherenceReadiness: 72,
      medicationRisk: 18,
      nutritionRisk: 24,
    },
    {
      adherenceReadiness: ["最近 3 天已连续完成呼吸、Zen 和音景干预", "更适合从低门槛干预先开始"],
      recoveryCapacity: ["呼吸训练后心率和压力均有回落"],
    },
    []
  );
  const prescription = makePrescriptionBundle(
    prefix,
    now,
    profile.id,
    "通过实时反馈把恢复训练做成可复盘的闭环",
    "LOW",
    "当前重点不是静态看报告，而是演示设备数据如何进入执行体验和复盘。",
    ["最近 3 次干预已产生可量化效果分", "可继续从视觉呼吸、Zen 和音景切换体验"],
    [
      { protocolCode: "BREATH_4_6", assetRef: "breathing://4-6", durationSec: 180, sequenceOrder: 1, timingSlot: "EVENING" },
      { protocolCode: "ZEN_WAVE_GARDEN_5M", assetRef: "interactive://zen-wave", durationSec: 300, sequenceOrder: 2, timingSlot: "EVENING" },
      { protocolCode: "SOUNDSCAPE_SLEEP_AUDIO_15M", assetRef: "audio://soundscape/15m", durationSec: 900, sequenceOrder: 3, timingSlot: "BEFORE_SLEEP" },
    ]
  );
  const task1 = makeTask(prefix, 1, now - 2 * 60 * 60 * 1000, "BREATH_4_6", "COMPLETED", "实时压力偏高", "CHEST", 180);
  const task2 = makeTask(prefix, 2, now - 70 * 60 * 1000, "ZEN_WAVE_GARDEN_5M", "COMPLETED", "睡前思绪活跃", "HEAD", 300);
  const task3 = makeTask(prefix, 3, now - 25 * 60 * 1000, "SOUNDSCAPE_SLEEP_AUDIO_15M", "PENDING", "准备进入睡前流程", "WHOLE_BODY", 900);
  return {
    devices: [
      {
        deviceId: `${prefix}-device`,
        deviceName: "长庚环 现场演示戒指",
        macAddress: "AA:BB:CC:11:22:33",
        batteryLevel: 91,
        firmwareVersion: "1.4.0-demo",
        connectionState: "CONNECTED",
        lastSyncTime: now - 3 * 60 * 1000,
        isPrimary: true,
        createdAt: now - 7 * DAY_MS,
        updatedAt: now - 3 * 60 * 1000,
      },
    ],
    sleepRecords: series.sleepRecords,
    healthMetrics: series.healthMetrics,
    recoveryScores: series.recoveryScores,
    doctorSessions: [],
    doctorMessages: [],
    doctorAssessments: [],
    assessmentSessions: [
      makeAssessmentSession(prefix, "PSS10", 17, "中度压力", now - 5 * DAY_MS),
      makeAssessmentSession(prefix, "ESS", 8, "正常上沿", now - 5 * DAY_MS),
    ],
    assessmentAnswers: [],
    interventionTasks: [task1, task2, task3],
    interventionExecutions: [
      makeExecution(task1.id, prefix, 1, task1.plannedAt, 180, 81, 49, 89, 72, 83, "BREATH_VISUAL"),
      makeExecution(task2.id, prefix, 2, task2.plannedAt, 300, 74, 46, 84, 69, 80, "ZEN"),
    ],
    medicalReports: [],
    medicalMetrics: [],
    relaxSessions: [
      makeRelaxSession(prefix, 1, task1.plannedAt, 180, "BREATH_4_6", 83, "BREATH_VISUAL"),
      makeRelaxSession(prefix, 2, task2.plannedAt, 300, "ZEN_WAVE_GARDEN_5M", 80, "ZEN"),
      makeRelaxSession(prefix, 3, now - DAY_MS, 900, "SOUNDSCAPE_SLEEP_AUDIO_15M", 76, "SOUNDSCAPE"),
    ],
    interventionProfileSnapshots: [profile],
    prescriptionBundles: [prescription.bundle],
    prescriptionItems: prescription.items,
    medicationRecords: [],
    foodRecords: [],
  };
}

function buildHighRiskScenario(now: number): DemoBootstrapSnapshot {
  const prefix = "demo-high-risk";
  const series = makeSleepSeries(prefix, now, 30, {
    totalSleepBase: 298,
    totalSleepSlope: 0.6,
    recoveryBase: 36,
    recoverySlope: 0.2,
    hrBase: 88,
    hrvBase: 20,
    oxygenBase: 93,
    temperatureBase: 36.8,
    awakeBase: 42,
  });
  const doctor = makeDoctorLoop(
    prefix,
    now,
    "连续多天恢复分很低，夜间反复醒，白天胸闷焦躁。",
    "HIGH",
    "当前已经进入高风险演示场景，建议优先就医评估，再把干预建议作为辅助。",
    "胸闷和焦躁是否伴随明显心悸、呼吸困难或持续加重？",
    [
      { name: "高唤醒伴恢复持续下降", rationale: "近 7 天恢复分持续在低位", confidence: 88 },
      { name: "需要优先线下评估的风险场景", rationale: "主诉已包含胸闷和明显日间功能受损", confidence: 84 },
    ],
    ["连续 5 天恢复分低于 45 分", "夜间反复醒且白天胸闷焦躁", "最近一周干预执行中断较多"],
    ["是否已有心血管或焦虑相关既往史"],
    ["胸闷若持续或加重需立刻线下就医"],
    ["优先联系医生或心理专业人员", "在等待评估期间只保留保守呼吸与减刺激干预"],
    "综合门诊"
  );
  const reportId = `${prefix}-report`;
  const profile = makeProfileSnapshot(
    prefix,
    now,
    {
      sleepDisturbance: 86,
      stressLoad: 79,
      fatigueLoad: 83,
      recoveryCapacity: 24,
      anxietyRisk: 69,
      depressiveRisk: 42,
      adherenceReadiness: 39,
      medicationRisk: 33,
      nutritionRisk: 46,
    },
    {
      sleepDisturbance: ["近 7 天平均睡眠不足 5.5 小时", "夜间觉醒次数和醒后清醒时间都偏高"],
      stressLoad: ["HRV 持续低于基线", "静息心率偏高且胸闷焦躁主诉明显"],
      adherenceReadiness: ["最近 4 次干预中断 3 次", "高风险场景下需优先就医，不宜只靠自助干预"],
    },
    ["胸闷或症状加重时需优先线下就医", "当前仅保留保守干预，不应延误评估"]
  );
  const prescription = makePrescriptionBundle(
    prefix,
    now,
    profile.id,
    "高风险场景下先把就医动作前置，再保守维持节律",
    "HIGH",
    "恢复、问诊和医疗报告都提示高风险，不应把干预建议当成替代诊疗。",
    ["恢复分持续低位", "高风险报告未完全关闭", "存在失败作业与待处理任务"],
    [
      { protocolCode: "TASK_DOCTOR_PRIORITY", assetRef: "task://doctor-priority", durationSec: 120, sequenceOrder: 1, timingSlot: "FLEXIBLE", itemType: "TASK" },
      { protocolCode: "BREATH_4_6", assetRef: "breathing://4-6", durationSec: 180, sequenceOrder: 2, timingSlot: "EVENING", isRequired: false },
    ]
  );
  const pendingTask = makeTask(prefix, 1, now - 90 * 60 * 1000, "TASK_DOCTOR_PRIORITY", "PENDING", "高风险场景优先就医", "WHOLE_BODY", 120);
  return {
    devices: [],
    sleepRecords: series.sleepRecords,
    healthMetrics: series.healthMetrics,
    recoveryScores: series.recoveryScores,
    doctorSessions: [doctor.session],
    doctorMessages: doctor.messages,
    doctorAssessments: [doctor.assessment],
    assessmentSessions: [
      makeAssessmentSession(prefix, "ISI", 22, "重度", now - DAY_MS),
      makeAssessmentSession(prefix, "ESS", 13, "明显偏高", now - DAY_MS),
      makeAssessmentSession(prefix, "PSS10", 28, "高压力", now - DAY_MS),
      makeAssessmentSession(prefix, "GAD7", 13, "中重度", now - DAY_MS),
      makeAssessmentSession(prefix, "PHQ9", 10, "中度", now - DAY_MS),
    ],
    assessmentAnswers: [],
    interventionTasks: [pendingTask],
    interventionExecutions: [],
    medicalReports: [
      {
        id: reportId,
        reportDate: now - DAY_MS,
        reportType: "心电/血压相关报告",
        imageUri: `demo://reports/${prefix}.pdf`,
        ocrTextDigest: "血压与相关指标存在高风险提示，需要进一步评估。",
        parseStatus: "PARSED",
        riskLevel: "HIGH",
        createdAt: now - DAY_MS,
      },
    ],
    medicalMetrics: [
      {
        id: `${reportId}-metric-1`,
        reportId,
        metricCode: "SBP",
        metricName: "收缩压",
        metricValue: 152,
        unit: "mmHg",
        refLow: 90,
        refHigh: 140,
        isAbnormal: true,
        confidence: 0.96,
      },
      {
        id: `${reportId}-metric-2`,
        reportId,
        metricCode: "DBP",
        metricName: "舒张压",
        metricValue: 98,
        unit: "mmHg",
        refLow: 60,
        refHigh: 90,
        isAbnormal: true,
        confidence: 0.96,
      },
    ],
    relaxSessions: [],
    interventionProfileSnapshots: [profile],
    prescriptionBundles: [prescription.bundle],
    prescriptionItems: prescription.items,
    medicationRecords: [],
    foodRecords: [],
  };
}

function emptySnapshot(): DemoBootstrapSnapshot {
  return {
    devices: [],
    sleepRecords: [],
    healthMetrics: [],
    recoveryScores: [],
    doctorSessions: [],
    doctorMessages: [],
    doctorAssessments: [],
    assessmentSessions: [],
    assessmentAnswers: [],
    interventionTasks: [],
    interventionExecutions: [],
    medicalReports: [],
    medicalMetrics: [],
    relaxSessions: [],
    interventionProfileSnapshots: [],
    prescriptionBundles: [],
    prescriptionItems: [],
    medicationRecords: [],
    foodRecords: [],
  };
}

export function getDemoSeedVersion(): string {
  return demoConfig.seedVersion;
}

export function getDemoAccountConfig(scenario: string): DemoAccountConfig | null {
  return accountConfigs[scenario] ?? null;
}

export function readDemoMetadata(metadata: Record<string, unknown> | null | undefined): DemoMetadata {
  const demoRole = metadata?.demoRole;
  const demoScenario = metadata?.demoScenario;
  const demoSeedVersion = metadata?.demoSeedVersion;
  const displayName = metadata?.displayName;
  return {
    demoRole: demoRole === "demo_user" || demoRole === "demo_admin" ? demoRole : "",
    demoScenario:
      typeof demoScenario === "string" && demoScenario in accountConfigs
        ? (demoScenario as DemoScenario)
        : "",
    demoSeedVersion:
      typeof demoSeedVersion === "string" && demoSeedVersion.trim()
        ? demoSeedVersion.trim()
        : getDemoSeedVersion(),
    displayName: typeof displayName === "string" ? displayName.trim() : "",
  };
}

export function buildDemoBootstrapPayload(input: {
  userId: string;
  scenario: DemoScenario;
  displayName?: string;
  now?: number;
}): DemoBootstrapPayload | null {
  const config = getDemoAccountConfig(input.scenario);
  if (!config || config.role !== "demo_user") {
    return null;
  }
  const now = input.now ?? Date.now();
  const snapshot = (() => {
    switch (input.scenario) {
      case "demo_baseline_recovery":
        return buildBaselineRecoveryScenario(now);
      case "demo_report_doctor_loop":
        return buildReportDoctorScenario(now);
      case "demo_lifestyle_loop":
        return buildLifestyleScenario(now);
      case "demo_live_intervention":
        return buildLiveInterventionScenario(now);
      case "demo_high_risk_ops":
        return buildHighRiskScenario(now);
      default:
        return emptySnapshot();
    }
  })();
  return {
    demoRole: "demo_user",
    demoScenario: input.scenario,
    demoSeedVersion: getDemoSeedVersion(),
    displayName: input.displayName?.trim() || config.displayName,
    snapshot,
  };
}
