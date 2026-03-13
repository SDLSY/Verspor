import type {
  DailyPrescriptionRequest,
  DailyPrescriptionResponse,
  PrescriptionPersonalizationMissingInput,
  PrescriptionServerContext,
} from "@/lib/prescription/types";
import {
  SRM_V2_DEFAULT_PROFILE,
  type LoadedRecommendationModelProfile,
} from "@/lib/recommendation-model/srm-v2-config";

export type ScientificTraceType = "DAILY_PRESCRIPTION" | "PERIOD_SUMMARY" | "DOCTOR_TURN";
export type ScientificSafetyGate = "GREEN" | "AMBER" | "RED";
export type ScientificConfidence = "LOW" | "MEDIUM" | "HIGH";
export type ScientificRecommendationMode =
  | "ESCALATE"
  | "STABILIZE"
  | "SLEEP_PREP"
  | "STRESS_REGULATION"
  | "RECOVERY"
  | "FOLLOW_UP";

export type ScientificEvidenceItem = {
  source:
    | "DEVICE_RING"
    | "SLEEP_SESSION"
    | "BASELINE_ASSESSMENT"
    | "DOCTOR_INQUIRY"
    | "MEDICAL_REPORT"
    | "INTERVENTION_EXECUTION"
    | "CLOUD_CONTEXT";
  domain: string;
  label: string;
  kind: "score" | "flag" | "fact" | "count" | "trend" | "summary";
  value: string | number | boolean;
  weight: number;
  confidence: number;
};

export type ScientificHypothesis = {
  code: string;
  score: number;
  evidenceLabels: string[];
  rationale: string;
};

export type ScientificRecommendationSheet = {
  modelVersion: "SRM_V2";
  profileCode: string;
  configSource: "database" | "default";
  traceType: ScientificTraceType;
  safetyGate: ScientificSafetyGate;
  recommendationMode: ScientificRecommendationMode;
  explanationConfidence: ScientificConfidence;
  evidenceCoverage: number;
  sourceCoverage: Record<string, boolean>;
  evidenceLedger: ScientificEvidenceItem[];
  hypotheses: ScientificHypothesis[];
  decisionSummary: string;
  innovationNotes: string[];
};

type PeriodMetricChange = {
  label: string;
  value: string;
  comparison: string;
  direction: "UP" | "DOWN" | "STABLE";
};

type PeriodSummaryForModel = {
  period: "weekly" | "monthly";
  riskLevel: "LOW" | "MEDIUM" | "HIGH";
  sampleSufficient: boolean;
  personalizationLevel: string;
  missingInputs: string[];
  reportConfidence: "LOW" | "MEDIUM" | "HIGH";
  headline: string;
  highlights: string[];
  metricChanges: PeriodMetricChange[];
  nextFocusTitle: string;
  nextFocusDetail: string;
};

type DoctorTurnForModel = {
  stage: "INTAKE" | "CLARIFYING" | "ASSESSING" | "COMPLETED" | "ESCALATED";
  followUpCount: number;
  conversationBlock: string;
  contextBlock: string;
  ragContext: string;
  chiefComplaint: string;
  symptomFacts: string[];
  missingInfo: string[];
  suspectedIssues: Array<{ name: string; confidence: number }>;
  riskLevel: "LOW" | "MEDIUM" | "HIGH";
  redFlags: string[];
  recommendedDepartment: string;
  nextStepAdvice: string[];
  doctorSummary: string;
  outputStage: "CLARIFYING" | "COMPLETED" | "ESCALATED";
};

function clamp(value: number, min = 0, max = 1): number {
  return Math.min(max, Math.max(min, value));
}

function uniq(values: string[]): string[] {
  return Array.from(new Set(values.map((item) => item.trim()).filter(Boolean)));
}

function toConfidence(score: number): ScientificConfidence {
  if (score >= 0.75) {
    return "HIGH";
  }
  if (score >= 0.4) {
    return "MEDIUM";
  }
  return "LOW";
}

function scoreToWeight(score: number): number {
  return Number(clamp(score / 100, 0.05, 1).toFixed(2));
}

function missingInputToSourceFlag(
  missingInputs: PrescriptionPersonalizationMissingInput[] | string[]
): Record<string, boolean> {
  const missing = new Set(missingInputs);
  return {
    deviceData: !missing.has("DEVICE_DATA"),
    baselineAssessment: !missing.has("BASELINE_ASSESSMENT"),
    doctorInquiry: !missing.has("DOCTOR_INQUIRY"),
  };
}

function resolveProfile(
  profile?: LoadedRecommendationModelProfile
): LoadedRecommendationModelProfile {
  return profile ?? { ...SRM_V2_DEFAULT_PROFILE };
}

function resolveSafetyGate(
  candidates: Array<{ active: boolean; ruleKey: string; fallback: ScientificSafetyGate }>,
  profile: LoadedRecommendationModelProfile,
  defaultGate: ScientificSafetyGate = "GREEN"
): ScientificSafetyGate {
  for (const candidate of candidates) {
    if (!candidate.active) {
      continue;
    }
    const configured = profile.gateRules[candidate.ruleKey];
    if (configured === "GREEN" || configured === "AMBER" || configured === "RED") {
      return configured;
    }
    return candidate.fallback;
  }
  return defaultGate;
}

function resolveRecommendationMode(
  gate: ScientificSafetyGate,
  hypotheses: ScientificHypothesis[],
  fallback: ScientificRecommendationMode,
  profile: LoadedRecommendationModelProfile
): ScientificRecommendationMode {
  const orderedHypotheses = [...hypotheses].sort((left, right) => right.score - left.score);
  const allowed = profile.modePriorities[gate] ?? [];
  for (const mode of allowed) {
    if (orderedHypotheses.some((hypothesis) => hypothesis.code === mode)) {
      return mode as ScientificRecommendationMode;
    }
  }
  const first = orderedHypotheses[0]?.code;
  if (
    first === "ESCALATE" ||
    first === "STABILIZE" ||
    first === "SLEEP_PREP" ||
    first === "STRESS_REGULATION" ||
    first === "RECOVERY" ||
    first === "FOLLOW_UP"
  ) {
    return first;
  }
  return fallback;
}

function buildCoverageConfidence(
  profile: LoadedRecommendationModelProfile,
  input: { evidenceCoverage: number; evidenceCount: number; hypothesisCount: number }
): ScientificConfidence {
  const evidenceCoverageWeight = profile.weights.evidenceCoverage ?? 0.45;
  const evidenceCountWeight = profile.weights.evidenceCount ?? 0.25;
  const hypothesisCountWeight = profile.weights.hypothesisCount ?? 0.3;

  const score =
    input.evidenceCoverage * evidenceCoverageWeight +
    clamp((input.evidenceCount >= 4 ? 1 : input.evidenceCount / 4) * evidenceCountWeight) +
    clamp((input.hypothesisCount >= 2 ? 1 : input.hypothesisCount / 2) * hypothesisCountWeight);

  return toConfidence(score);
}

function buildDoctorConfidence(
  profile: LoadedRecommendationModelProfile,
  input: { evidenceCoverage: number; missingInfoCount: number; hasRedFlags: boolean }
): ScientificConfidence {
  const weights = profile.confidenceFormula;
  const coverageWeight = weights.coverageWeight ?? 0.4;
  const missingPenaltyWeight = weights.missingPenaltyWeight ?? 0.35;
  const riskSignalWeight = weights.riskSignalWeight ?? 0.25;

  const missingPenalty = clamp(1 - input.missingInfoCount / 8);
  const riskSignal = input.hasRedFlags ? 1 : 0.6;
  const score =
    input.evidenceCoverage * coverageWeight +
    missingPenalty * missingPenaltyWeight +
    riskSignal * riskSignalWeight;

  return toConfidence(score);
}

export function buildDailyPrescriptionScientificModel(input: {
  request: DailyPrescriptionRequest;
  mergedRequest: DailyPrescriptionRequest;
  serverContext: PrescriptionServerContext;
  payload: DailyPrescriptionResponse;
  profile?: LoadedRecommendationModelProfile;
}): ScientificRecommendationSheet {
  const profile = resolveProfile(input.profile);
  const sourceCoverage = {
    ...missingInputToSourceFlag(input.payload.missingInputs),
    medicalReport:
      input.serverContext.latestMedicalRiskLevel != null ||
      input.serverContext.latestMedicalMetricLabels.length > 0,
    interventionExecution:
      input.serverContext.averageEffectScore != null ||
      input.serverContext.averageStressDrop != null ||
      input.serverContext.recentInterventionSummary.length > 0,
    sleepContext: input.serverContext.latestSleepSummary.length > 0,
  };

  const coverageValues = Object.values(sourceCoverage);
  const evidenceCoverage = Number(
    clamp(coverageValues.filter(Boolean).length / coverageValues.length).toFixed(2)
  );

  const evidenceLedger: ScientificEvidenceItem[] = [];
  Object.entries(input.mergedRequest.domainScores)
    .sort(([, left], [, right]) => right - left)
    .slice(0, 4)
    .forEach(([domain, score]) => {
      evidenceLedger.push({
        source: "CLOUD_CONTEXT",
        domain,
        label: `${domain} 得分 ${score}`,
        kind: "score",
        value: score,
        weight: scoreToWeight(score),
        confidence: coverageValues.filter(Boolean).length / coverageValues.length,
      });
    });

  uniq(input.mergedRequest.redFlags).slice(0, 4).forEach((flag) => {
    evidenceLedger.push({
      source: "DOCTOR_INQUIRY",
      domain: "risk",
      label: flag,
      kind: "flag",
      value: true,
      weight: 1,
      confidence: 1,
    });
  });

  if (input.serverContext.latestMedicalRiskLevel) {
    evidenceLedger.push({
      source: "MEDICAL_REPORT",
      domain: "medicalRisk",
      label: `最近医检风险 ${input.serverContext.latestMedicalRiskLevel}`,
      kind: "summary",
      value: input.serverContext.latestMedicalRiskLevel,
      weight: input.serverContext.latestMedicalRiskLevel === "HIGH" ? 1 : 0.65,
      confidence: 0.85,
    });
  }

  if (input.serverContext.latestRecoveryScore != null) {
    evidenceLedger.push({
      source: "DEVICE_RING",
      domain: "recoveryCapacity",
      label: `最近恢复分 ${Math.round(input.serverContext.latestRecoveryScore)}`,
      kind: "score",
      value: Math.round(input.serverContext.latestRecoveryScore),
      weight: scoreToWeight(100 - input.serverContext.latestRecoveryScore),
      confidence: 0.8,
    });
  }

  if (input.serverContext.averageEffectScore != null) {
    evidenceLedger.push({
      source: "INTERVENTION_EXECUTION",
      domain: "adherenceReadiness",
      label: `平均执行效果 ${Math.round(input.serverContext.averageEffectScore)}`,
      kind: "score",
      value: Math.round(input.serverContext.averageEffectScore),
      weight: scoreToWeight(input.serverContext.averageEffectScore),
      confidence: 0.75,
    });
  }

  const hypotheses: ScientificHypothesis[] = [];
  const sleepDisturbance = input.mergedRequest.domainScores.sleepDisturbance ?? 0;
  const stressLoad = input.mergedRequest.domainScores.stressLoad ?? 0;
  const fatigueLoad = input.mergedRequest.domainScores.fatigueLoad ?? 0;
  const recoveryCapacity = input.mergedRequest.domainScores.recoveryCapacity ?? 50;

  if (sleepDisturbance >= (profile.thresholds.sleepDisturbance ?? 60)) {
    hypotheses.push({
      code: "SLEEP_PREP",
      score: scoreToWeight(sleepDisturbance),
      evidenceLabels: uniq([
        `sleepDisturbance=${sleepDisturbance}`,
        ...(input.mergedRequest.evidenceFacts.sleepDisturbance ?? []).slice(0, 2),
      ]),
      rationale: "睡眠扰动域得分偏高，建议优先降低睡前唤醒负荷并收口晚间刺激。",
    });
  }
  if (stressLoad >= (profile.thresholds.stressLoad ?? 60)) {
    hypotheses.push({
      code: "STRESS_REGULATION",
      score: scoreToWeight(stressLoad),
      evidenceLabels: uniq([
        `stressLoad=${stressLoad}`,
        ...(input.mergedRequest.evidenceFacts.stressLoad ?? []).slice(0, 2),
      ]),
      rationale: "压力负荷域偏高时，先做降压和自主神经稳定，比直接提高训练强度更稳妥。",
    });
  }
  if (
    fatigueLoad >= (profile.thresholds.fatigueLoad ?? 60) ||
    recoveryCapacity <= (profile.thresholds.recoveryCapacityLow ?? 40)
  ) {
    hypotheses.push({
      code: "RECOVERY",
      score: scoreToWeight(Math.max(fatigueLoad, 100 - recoveryCapacity)),
      evidenceLabels: uniq([
        fatigueLoad > 0 ? `fatigueLoad=${fatigueLoad}` : "",
        `recoveryCapacity=${recoveryCapacity}`,
      ]),
      rationale: "疲劳负荷偏高或恢复能力偏低时，应优先恢复而不是追加负荷。",
    });
  }
  if (
    input.mergedRequest.redFlags.length > 0 ||
    input.serverContext.latestMedicalRiskLevel === "HIGH"
  ) {
    hypotheses.push({
      code: "ESCALATE",
      score: 1,
      evidenceLabels: uniq([
        ...input.mergedRequest.redFlags.slice(0, 3),
        input.serverContext.latestMedicalRiskLevel
          ? `medicalRisk=${input.serverContext.latestMedicalRiskLevel}`
          : "",
      ]),
      rationale: "红旗或高风险医检证据出现时，建议把专业评估前置，干预仅保留辅助性质。",
    });
  }

  const safetyGate = resolveSafetyGate(
    [
      {
        active: input.mergedRequest.redFlags.length > 0,
        ruleKey: "redFlagGate",
        fallback: "RED",
      },
      {
        active: input.serverContext.latestMedicalRiskLevel === "HIGH",
        ruleKey: "highMedicalRiskGate",
        fallback: "RED",
      },
      {
        active: input.payload.riskLevel === "MEDIUM",
        ruleKey: "mediumRiskGate",
        fallback: "AMBER",
      },
    ],
    profile,
    "GREEN"
  );

  const recommendationMode = resolveRecommendationMode(
    safetyGate,
    hypotheses,
    "SLEEP_PREP",
    profile
  );

  return {
    modelVersion: "SRM_V2",
    profileCode: profile.profileCode,
    configSource: profile.source,
    traceType: "DAILY_PRESCRIPTION",
    safetyGate,
    recommendationMode,
    explanationConfidence: buildCoverageConfidence(profile, {
      evidenceCoverage,
      evidenceCount: evidenceLedger.length,
      hypothesisCount: hypotheses.length,
    }),
    evidenceCoverage,
    sourceCoverage,
    evidenceLedger,
    hypotheses,
    decisionSummary: `系统先基于多源证据形成健康假设，再经过安全闸门和策略优先级选择，最终把处方收口到“${input.payload.primaryGoal}”。`,
    innovationNotes: [
      `当前建议由配置化策略档案 ${profile.profileCode} 驱动，不再把阈值和模式优先级硬编码在主链里。`,
      "多源证据不是直接拼 prompt，而是先经过域级假设层，再进入建议表达层。",
      "红旗、医检高风险和个体化输入完整度共同影响安全闸门与解释置信度。",
    ],
  };
}

export function buildPeriodSummaryScientificModel(input: {
  payload: PeriodSummaryForModel;
  profile?: LoadedRecommendationModelProfile;
}): ScientificRecommendationSheet {
  const profile = resolveProfile(input.profile);
  const payload = input.payload;
  const sourceCoverage = {
    sleepSamples: payload.sampleSufficient,
    personalization: payload.missingInputs.length === 0,
    trendMetrics: payload.metricChanges.length > 0,
    highlights: payload.highlights.length > 0,
  };
  const evidenceCoverage = Number(
    clamp(Object.values(sourceCoverage).filter(Boolean).length / Object.keys(sourceCoverage).length).toFixed(2)
  );

  const evidenceLedger: ScientificEvidenceItem[] = payload.metricChanges.slice(0, 4).map((metric) => ({
    source: "CLOUD_CONTEXT",
    domain: metric.label,
    label: `${metric.label}: ${metric.value} (${metric.comparison})`,
    kind: "trend",
    value: metric.direction,
    weight: metric.direction === "STABLE" ? 0.45 : 0.75,
    confidence: payload.sampleSufficient ? 0.85 : 0.55,
  }));

  payload.highlights.slice(0, 3).forEach((highlight) => {
    evidenceLedger.push({
      source: "CLOUD_CONTEXT",
      domain: "highlight",
      label: highlight,
      kind: "summary",
      value: highlight,
      weight: 0.55,
      confidence: 0.7,
    });
  });

  const focusMode: ScientificRecommendationMode =
    payload.riskLevel === "HIGH"
      ? "ESCALATE"
      : payload.nextFocusTitle.includes("恢复")
        ? "RECOVERY"
        : payload.nextFocusTitle.includes("睡")
          ? "SLEEP_PREP"
          : "FOLLOW_UP";

  const hypotheses: ScientificHypothesis[] = [
    {
      code: focusMode,
      score: payload.sampleSufficient ? 0.8 : 0.45,
      evidenceLabels: uniq([payload.headline, ...payload.highlights.slice(0, 2)]),
      rationale: payload.nextFocusDetail,
    },
  ];

  const safetyGate = resolveSafetyGate(
    [
      {
        active: payload.riskLevel === "HIGH",
        ruleKey: "highPeriodRiskGate",
        fallback: "RED",
      },
      {
        active: payload.riskLevel === "MEDIUM",
        ruleKey: "mediumPeriodRiskGate",
        fallback: "AMBER",
      },
    ],
    profile,
    "GREEN"
  );

  return {
    modelVersion: "SRM_V2",
    profileCode: profile.profileCode,
    configSource: profile.source,
    traceType: "PERIOD_SUMMARY",
    safetyGate,
    recommendationMode: resolveRecommendationMode(safetyGate, hypotheses, focusMode, profile),
    explanationConfidence:
      payload.reportConfidence === "HIGH"
        ? "HIGH"
        : payload.reportConfidence === "MEDIUM"
          ? "MEDIUM"
          : "LOW",
    evidenceCoverage,
    sourceCoverage,
    evidenceLedger,
    hypotheses,
    decisionSummary: "周期建议不是单次快照，而是由趋势变化、执行依从性和风险信号共同决定。",
    innovationNotes: [
      `周期建议已接入配置化策略档案 ${profile.profileCode}，支持按不同人群切换策略阈值。`,
      "周期报告用趋势证据替代单点分数，避免建议被单日异常牵着走。",
      "报告置信度和输入缺失被显式建模，避免把低样本报告伪装成高确定性结论。",
    ],
  };
}

export function buildDoctorTurnScientificModel(input: {
  payload: DoctorTurnForModel;
  profile?: LoadedRecommendationModelProfile;
}): ScientificRecommendationSheet {
  const profile = resolveProfile(input.profile);
  const payload = input.payload;
  const sourceCoverage = {
    conversation: payload.conversationBlock.trim().length > 0,
    context: payload.contextBlock.trim().length > 0,
    rag: payload.ragContext.trim().length > 0,
    symptomFacts: payload.symptomFacts.length > 0,
  };
  const evidenceCoverage = Number(
    clamp(Object.values(sourceCoverage).filter(Boolean).length / Object.keys(sourceCoverage).length).toFixed(2)
  );

  const evidenceLedger: ScientificEvidenceItem[] = [
    {
      source: "DOCTOR_INQUIRY",
      domain: "chiefComplaint",
      label: payload.chiefComplaint || "未明确主诉",
      kind: "summary",
      value: payload.chiefComplaint || "",
      weight: payload.chiefComplaint ? 0.8 : 0.2,
      confidence: payload.chiefComplaint ? 0.85 : 0.3,
    },
    {
      source: "DOCTOR_INQUIRY",
      domain: "missingInfo",
      label: `缺失关键信息 ${payload.missingInfo.length} 项`,
      kind: "count",
      value: payload.missingInfo.length,
      weight: clamp(1 - payload.missingInfo.length / 8, 0.15, 1),
      confidence: 0.85,
    },
  ];

  payload.redFlags.slice(0, 3).forEach((flag) => {
    evidenceLedger.push({
      source: "DOCTOR_INQUIRY",
      domain: "risk",
      label: flag,
      kind: "flag",
      value: true,
      weight: 1,
      confidence: 1,
    });
  });

  payload.suspectedIssues.slice(0, 3).forEach((issue) => {
    evidenceLedger.push({
      source: "DOCTOR_INQUIRY",
      domain: "suspectedIssue",
      label: `${issue.name} (${issue.confidence})`,
      kind: "summary",
      value: issue.confidence,
      weight: scoreToWeight(issue.confidence),
      confidence: 0.75,
    });
  });

  const hypotheses: ScientificHypothesis[] = [
    {
      code:
        payload.outputStage === "ESCALATED"
          ? "ESCALATE"
          : payload.outputStage === "CLARIFYING"
            ? "FOLLOW_UP"
            : "STABILIZE",
      score:
        payload.outputStage === "ESCALATED"
          ? 1
          : payload.outputStage === "CLARIFYING"
            ? 0.65
            : 0.8,
      evidenceLabels: uniq([...payload.symptomFacts.slice(0, 2), ...payload.redFlags.slice(0, 2)]),
      rationale:
        payload.outputStage === "CLARIFYING"
          ? "当前仍以补齐高价值病史信息为主。"
          : payload.outputStage === "ESCALATED"
            ? "红旗或高风险提示已达到升级处理条件。"
            : "现有信息已足够给出结构化初筛总结。",
    },
  ];

  const safetyGate = resolveSafetyGate(
    [
      {
        active: payload.outputStage === "ESCALATED",
        ruleKey: "escalatedStageGate",
        fallback: "RED",
      },
      {
        active: payload.redFlags.length > 0,
        ruleKey: "redFlagGate",
        fallback: "RED",
      },
      {
        active: payload.riskLevel === "MEDIUM",
        ruleKey: "mediumRiskGate",
        fallback: "AMBER",
      },
    ],
    profile,
    "GREEN"
  );

  return {
    modelVersion: "SRM_V2",
    profileCode: profile.profileCode,
    configSource: profile.source,
    traceType: "DOCTOR_TURN",
    safetyGate,
    recommendationMode: resolveRecommendationMode(
      safetyGate,
      hypotheses,
      payload.outputStage === "CLARIFYING" ? "FOLLOW_UP" : "STABILIZE",
      profile
    ),
    explanationConfidence: buildDoctorConfidence(profile, {
      evidenceCoverage,
      missingInfoCount: payload.missingInfo.length,
      hasRedFlags: payload.redFlags.length > 0,
    }),
    evidenceCoverage,
    sourceCoverage,
    evidenceLedger,
    hypotheses,
    decisionSummary:
      payload.outputStage === "CLARIFYING"
        ? "本轮优先补齐最高价值的缺失病史信息，再继续问诊。"
        : payload.outputStage === "ESCALATED"
          ? "本轮已进入风险升级处理，建议把线下就医前置。"
          : "本轮信息已足够生成结构化问诊总结与下一步建议。",
    innovationNotes: [
      `医生问诊解释层已接入配置化策略档案 ${profile.profileCode}，可以按不同人群切换追问和风控阈值。`,
      "医生问诊不直接输出自由文本，而是先形成阶段性判断和风险分层。",
      "缺失信息量和红旗数量直接影响解释置信度，避免问诊看起来过度确定。",
    ],
  };
}
