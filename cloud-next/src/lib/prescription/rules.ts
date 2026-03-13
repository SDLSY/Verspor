import type {
  DailyPrescriptionRequest,
  DailyPrescriptionResponse,
  PrescriptionCatalogItem,
  PrescriptionRiskLevel,
  PrescriptionServerContext,
} from "@/lib/prescription/types";

function uniq(values: string[]): string[] {
  return Array.from(new Set(values.map((item) => item.trim()).filter(Boolean)));
}

function flattenEvidence(evidenceFacts: Record<string, string[]>): string[] {
  return Object.values(evidenceFacts).flat();
}

function hasCode(catalog: PrescriptionCatalogItem[], code: string): boolean {
  return catalog.some((item) => item.protocolCode === code);
}

function isTaskCode(code: string): boolean {
  return code.startsWith("TASK_");
}

function isDownweighted(code: string, context: PrescriptionServerContext): boolean {
  return context.downweightedProtocolCodes.includes(code);
}

function pickBestCode(
  catalog: PrescriptionCatalogItem[],
  context: PrescriptionServerContext,
  preferredCodes: string[],
  predicate?: (item: PrescriptionCatalogItem) => boolean
): string | null {
  const candidates = uniq([
    ...context.preferredProtocolCodes,
    ...preferredCodes,
    ...catalog.filter((item) => predicate?.(item) ?? true).map((item) => item.protocolCode),
  ]);
  for (const code of candidates) {
    const item = catalog.find((catalogItem) => catalogItem.protocolCode === code);
    if (!item) {
      continue;
    }
    if (predicate && !predicate(item)) {
      continue;
    }
    if (isDownweighted(code, context)) {
      continue;
    }
    return code;
  }
  for (const code of preferredCodes) {
    const item = catalog.find((catalogItem) => catalogItem.protocolCode === code);
    if (item && (!predicate || predicate(item))) {
      return item.protocolCode;
    }
  }
  const fallback = predicate ? catalog.find(predicate) : catalog[0];
  return fallback?.protocolCode ?? null;
}

function sanitizeLifestyleCodes(catalog: PrescriptionCatalogItem[], codes: string[]): string[] {
  return uniq(codes.filter((code) => isTaskCode(code) && hasCode(catalog, code)));
}

export function mergeRequestWithServerContext(
  request: DailyPrescriptionRequest,
  context: PrescriptionServerContext
): DailyPrescriptionRequest {
  const mergedEvidenceFacts: Record<string, string[]> = { ...request.evidenceFacts };
  Object.entries(context.serverEvidenceFacts).forEach(([key, value]) => {
    mergedEvidenceFacts[key] = uniq([...(mergedEvidenceFacts[key] ?? []), ...value]);
  });

  return {
    ...request,
    evidenceFacts: mergedEvidenceFacts,
    redFlags: uniq([...request.redFlags, ...context.serverRedFlags]),
    personalizationLevel: context.personalizationLevel,
    missingInputs: context.missingInputs,
  };
}

export function validateProviderDecision(
  request: DailyPrescriptionRequest,
  context: PrescriptionServerContext,
  payload: DailyPrescriptionResponse
): { valid: boolean; payload: DailyPrescriptionResponse | null; failureCode: string | null } {
  if (!hasCode(request.catalog, payload.primaryInterventionType)) {
    return { valid: false, payload: null, failureCode: "INVALID_PRIMARY_CODE" };
  }

  if (payload.secondaryInterventionType && !hasCode(request.catalog, payload.secondaryInterventionType)) {
    return { valid: false, payload: null, failureCode: "INVALID_SECONDARY_CODE" };
  }

  if (payload.lifestyleTaskCodes.some((code) => !hasCode(request.catalog, code))) {
    return { valid: false, payload: null, failureCode: "INVALID_LIFESTYLE_CODE" };
  }

  if (isDownweighted(payload.primaryInterventionType, context)) {
    return { valid: false, payload: null, failureCode: "DOWNWEIGHTED_PRIMARY_PROTOCOL" };
  }

  const doctorPriorityCode = pickBestCode(request.catalog, context, ["TASK_DOCTOR_PRIORITY"], (item) =>
    isTaskCode(item.protocolCode)
  );
  const lifestyleTaskCodes = sanitizeLifestyleCodes(request.catalog, [
    ...(doctorPriorityCode && request.redFlags.length > 0 ? [doctorPriorityCode] : []),
    ...payload.lifestyleTaskCodes,
  ]);

  const riskLevel: PrescriptionRiskLevel =
    request.redFlags.length > 0 || context.latestMedicalRiskLevel === "HIGH" ? "HIGH" : payload.riskLevel;

  const secondaryInterventionType =
    payload.secondaryInterventionType && isDownweighted(payload.secondaryInterventionType, context)
      ? ""
      : payload.secondaryInterventionType;

  return {
    valid: true,
    payload: {
      ...payload,
      riskLevel,
      personalizationLevel: context.personalizationLevel,
      missingInputs: context.missingInputs,
      isPreview: context.personalizationLevel === "PREVIEW",
      secondaryInterventionType,
      lifestyleTaskCodes,
      evidence: uniq([...payload.evidence, ...flattenEvidence(request.evidenceFacts)]).slice(0, 8),
      contraindications: uniq(payload.contraindications),
    },
    failureCode: null,
  };
}

export function buildRuleFallback(
  request: DailyPrescriptionRequest,
  context: PrescriptionServerContext
): DailyPrescriptionResponse {
  const sleep = request.domainScores.sleepDisturbance ?? 0;
  const stress = request.domainScores.stressLoad ?? 0;
  const fatigue = request.domainScores.fatigueLoad ?? 0;
  const recovery = request.domainScores.recoveryCapacity ?? 50;
  const anxiety = request.domainScores.anxietyRisk ?? 0;
  const depression = request.domainScores.depressiveRisk ?? 0;

  const evidence = uniq(flattenEvidence(request.evidenceFacts)).slice(0, 6);
  const doctorPriorityCode = pickBestCode(request.catalog, context, ["TASK_DOCTOR_PRIORITY"], (item) =>
    isTaskCode(item.protocolCode)
  );

  const makeResponse = (
    payload: Omit<
      DailyPrescriptionResponse,
      | "evidence"
      | "contraindications"
      | "lifestyleTaskCodes"
      | "personalizationLevel"
      | "missingInputs"
      | "isPreview"
    > & {
      lifestyleTaskCodes: string[];
    }
  ): DailyPrescriptionResponse => ({
    ...payload,
    personalizationLevel: context.personalizationLevel,
    missingInputs: context.missingInputs,
    isPreview: context.personalizationLevel === "PREVIEW",
    lifestyleTaskCodes: sanitizeLifestyleCodes(request.catalog, payload.lifestyleTaskCodes),
    evidence,
    contraindications:
      request.redFlags.length > 0
        ? ["如果出现红旗症状或症状持续加重，应优先线下评估，不应仅依赖放松训练。"]
        : [],
  });

  const sleepPrimary = pickBestCode(
    request.catalog,
    context,
    ["SLEEP_WIND_DOWN_15M"],
    (item) => item.protocolCode.includes("SLEEP_WIND_DOWN")
  );
  const bodyScanSecondary = pickBestCode(
    request.catalog,
    context,
    ["BODY_SCAN_NSDR_10M"],
    (item) => item.protocolCode.includes("BODY_SCAN")
  );
  const recoveryWalkPrimary = pickBestCode(
    request.catalog,
    context,
    ["RECOVERY_WALK_10M"],
    (item) => item.protocolCode.includes("RECOVERY_WALK")
  );
  const stretchSecondary = pickBestCode(
    request.catalog,
    context,
    ["GUIDED_STRETCH_MOBILITY_8M"],
    (item) => item.protocolCode.includes("STRETCH") || item.protocolCode.includes("MOBILITY")
  );
  const breathingPrimary = pickBestCode(
    request.catalog,
    context,
    ["BREATH_4_6_5M"],
    (item) => item.protocolCode.startsWith("BREATH")
  );
  const pmrPrimary = pickBestCode(
    request.catalog,
    context,
    ["PMR_10M"],
    (item) => item.protocolCode.includes("PMR")
  );
  const soundscapePrimary = pickBestCode(
    request.catalog,
    context,
    ["SOUNDSCAPE_SLEEP_AUDIO_15M"],
    (item) => item.protocolCode.includes("SOUNDSCAPE")
  );
  const cognitiveSecondary = pickBestCode(
    request.catalog,
    context,
    ["COGNITIVE_OFFLOAD_8M"],
    (item) => item.protocolCode.includes("COGNITIVE")
  );

  if (request.redFlags.length > 0 || depression >= 75 || context.latestMedicalRiskLevel === "HIGH") {
    return makeResponse({
      primaryGoal: "先稳定状态，并把医生评估前置",
      riskLevel: "HIGH",
      targetDomains: ["stressLoad", "depressiveRisk"],
      primaryInterventionType: bodyScanSecondary ?? request.catalog[0].protocolCode,
      secondaryInterventionType: cognitiveSecondary ?? "",
      lifestyleTaskCodes: uniq([doctorPriorityCode ?? "", "TASK_WORRY_LIST"]),
      timing: "FLEXIBLE",
      durationSec: 600,
      rationale:
        "当前画像存在高风险或红旗信号，放松训练只能作为辅助，必须把医生优先提醒放在最前面。",
      followupMetric: "红旗症状是否缓解，以及是否已经完成医生评估",
    });
  }

  if (sleep >= 65 && stress >= 60) {
    return makeResponse({
      primaryGoal: "降低睡前唤醒，改善入睡准备度",
      riskLevel: "MEDIUM",
      targetDomains: ["sleepDisturbance", "stressLoad"],
      primaryInterventionType: sleepPrimary ?? request.catalog[0].protocolCode,
      secondaryInterventionType: bodyScanSecondary ?? "",
      lifestyleTaskCodes: uniq(["TASK_SCREEN_CURFEW", "TASK_CAFFEINE_CUTOFF"]),
      timing: "BEFORE_SLEEP",
      durationSec: 900,
      rationale:
        "睡眠扰动和压力负荷同时偏高时，优先安排睡前减刺激流程，再用身体扫描帮助收尾。",
      followupMetric: "入睡时长和睡前紧张度",
    });
  }

  if (fatigue >= 65 || recovery <= 40) {
    const primary = recoveryWalkPrimary ?? stretchSecondary ?? request.catalog[0].protocolCode;
    const secondary = primary === recoveryWalkPrimary ? stretchSecondary ?? "" : recoveryWalkPrimary ?? "";
    return makeResponse({
      primaryGoal: "先做低门槛恢复，减轻疲劳积累",
      riskLevel: recovery <= 30 ? "HIGH" : "MEDIUM",
      targetDomains: ["fatigueLoad", "recoveryCapacity"],
      primaryInterventionType: primary,
      secondaryInterventionType: secondary,
      lifestyleTaskCodes: uniq(["TASK_DAYLIGHT_WALK"]),
      timing: "AFTERNOON",
      durationSec: 600,
      rationale:
        "疲劳负荷高或恢复能力偏低时，优先安排轻活动恢复和拉伸，而不是继续叠加高认知负荷的练习。",
      followupMetric: "下午精力评分与次日恢复分",
    });
  }

  if (stress >= 60 || anxiety >= 60) {
    const primary = context.breathingFatigue
      ? pmrPrimary ?? bodyScanSecondary ?? request.catalog[0].protocolCode
      : breathingPrimary ?? pmrPrimary ?? request.catalog[0].protocolCode;
    return makeResponse({
      primaryGoal: "降低压力唤醒并提升可执行性",
      riskLevel: "MEDIUM",
      targetDomains: ["stressLoad", "anxietyRisk"],
      primaryInterventionType: primary,
      secondaryInterventionType: bodyScanSecondary ?? "",
      lifestyleTaskCodes: uniq(["TASK_WORRY_LIST"]),
      timing: "EVENING",
      durationSec: 600,
      rationale:
        "压力或焦虑风险偏高时，先给短时且清晰的减压方案；如果近期对呼吸训练依从性差，就切换到渐进式肌肉放松。",
      followupMetric: "执行前后主观压力评分",
    });
  }

  const primary =
    soundscapePrimary ?? sleepPrimary ?? request.catalog[0].protocolCode;

  return makeResponse({
    primaryGoal: "维持稳定恢复节律",
    riskLevel: "LOW",
    targetDomains: ["recoveryCapacity"],
    primaryInterventionType: primary,
    secondaryInterventionType: cognitiveSecondary ?? "",
    lifestyleTaskCodes: uniq(["TASK_DAYLIGHT_WALK"]),
    timing: "BEFORE_SLEEP",
    durationSec: 900,
    rationale:
      "当前风险没有集中落在单一高危域，先维持稳定恢复节律，再通过轻量思绪卸载增强执行感。",
    followupMetric: "次日主观恢复感",
  });
}
