import type { DailyPrescriptionResponse, PrescriptionProviderInput } from "@/lib/prescription/types";

export interface PrescriptionModelProvider {
  readonly providerId: string;
  isEnabled(): boolean;
  generate(input: PrescriptionProviderInput): Promise<DailyPrescriptionResponse | null>;
}

function extractBalancedJsonObject(raw: string): string | null {
  let start = -1;
  let depth = 0;
  let inString = false;
  let escaped = false;

  for (let index = 0; index < raw.length; index += 1) {
    const char = raw[index];

    if (escaped) {
      escaped = false;
      continue;
    }

    if (char === "\\") {
      escaped = true;
      continue;
    }

    if (char === '"') {
      inString = !inString;
      continue;
    }

    if (inString) {
      continue;
    }

    if (char === "{") {
      if (depth === 0) {
        start = index;
      }
      depth += 1;
      continue;
    }

    if (char === "}") {
      if (depth === 0) {
        continue;
      }
      depth -= 1;
      if (depth === 0 && start >= 0) {
        return raw.slice(start, index + 1);
      }
    }
  }

  return null;
}

export function extractJsonObject(raw: string): string | null {
  const trimmed = raw.trim();
  if (!trimmed) {
    return null;
  }

  const fenced = trimmed.match(/```(?:json)?\s*([\s\S]*?)\s*```/i);
  const candidate = (fenced?.[1] ?? trimmed).trim();

  if (candidate.startsWith("{") && candidate.endsWith("}")) {
    return candidate;
  }

  return extractBalancedJsonObject(candidate);
}

export function normalizeMessageContent(value: unknown): string {
  if (typeof value === "string") {
    return value;
  }
  if (Array.isArray(value)) {
    return value
      .map((item) => {
        if (typeof item === "string") {
          return item;
        }
        if (typeof item === "object" && item !== null && "text" in item) {
          return String((item as { text?: unknown }).text ?? "");
        }
        if (typeof item === "object" && item !== null && "content" in item) {
          return normalizeMessageContent((item as { content?: unknown }).content);
        }
        return "";
      })
      .join("\n")
      .trim();
  }
  if (typeof value === "object" && value !== null) {
    if ("text" in value) {
      return String((value as { text?: unknown }).text ?? "").trim();
    }
    if ("content" in value) {
      return normalizeMessageContent((value as { content?: unknown }).content);
    }
  }
  return "";
}

function normalizeScalar(value: unknown): string {
  return typeof value === "string" ? value.trim() : "";
}

function normalizeStringArray(value: unknown): string[] {
  if (Array.isArray(value)) {
    return value
      .map((item) => normalizeScalar(item))
      .filter(Boolean);
  }
  if (typeof value === "string") {
    return value
      .split(/[\n,，;；、]+/)
      .map((item) => item.trim())
      .filter(Boolean);
  }
  return [];
}

function normalizeRiskLevel(value: unknown): DailyPrescriptionResponse["riskLevel"] {
  const normalized = normalizeScalar(value).toUpperCase();
  if (normalized === "LOW" || normalized === "MEDIUM" || normalized === "HIGH") {
    return normalized;
  }
  if (["低", "低风险"].includes(normalized)) {
    return "LOW";
  }
  if (["中", "中等", "中风险"].includes(normalized)) {
    return "MEDIUM";
  }
  return "HIGH";
}

function normalizeTiming(value: unknown): DailyPrescriptionResponse["timing"] {
  const normalized = normalizeScalar(value).toUpperCase();
  if (["MORNING", "AFTERNOON", "EVENING", "BEFORE_SLEEP", "FLEXIBLE"].includes(normalized)) {
    return normalized as DailyPrescriptionResponse["timing"];
  }
  if (["早晨", "清晨", "上午"].includes(normalized)) {
    return "MORNING";
  }
  if (["下午", "午后"].includes(normalized)) {
    return "AFTERNOON";
  }
  if (["傍晚", "晚上", "晚间"].includes(normalized)) {
    return "EVENING";
  }
  if (["睡前", "就寝前", "入睡前", "临睡前"].includes(normalized)) {
    return "BEFORE_SLEEP";
  }
  return "FLEXIBLE";
}

function normalizeDurationSec(value: unknown): number {
  const raw =
    typeof value === "number"
      ? value
      : Number.parseFloat(normalizeScalar(value).replace(/[^\d.]/g, ""));
  if (!Number.isFinite(raw) || raw <= 0) {
    return 600;
  }
  if (raw < 60) {
    return Math.round(raw * 60);
  }
  return Math.max(60, Math.min(7200, Math.round(raw)));
}

function normalizeTargetDomains(
  value: unknown,
  input: PrescriptionProviderInput
): DailyPrescriptionResponse["targetDomains"] {
  const aliasMap: Record<string, string> = {
    sleepdisturbance: "sleepDisturbance",
    睡眠: "sleepDisturbance",
    睡眠扰动: "sleepDisturbance",
    失眠: "sleepDisturbance",
    stressload: "stressLoad",
    压力: "stressLoad",
    紧张: "stressLoad",
    fatigueload: "fatigueLoad",
    疲劳: "fatigueLoad",
    recoverycapacity: "recoveryCapacity",
    恢复: "recoveryCapacity",
    恢复能力: "recoveryCapacity",
    anxietyrisk: "anxietyRisk",
    焦虑: "anxietyRisk",
    depressiverisk: "depressiveRisk",
    抑郁: "depressiveRisk",
    adherencereadiness: "adherenceReadiness",
    依从性: "adherenceReadiness",
  };

  const domains = normalizeStringArray(value)
    .map((item) => aliasMap[item.replace(/[\s_-]/g, "").toLowerCase()] ?? item)
    .filter((item) =>
      [
        "sleepDisturbance",
        "stressLoad",
        "fatigueLoad",
        "recoveryCapacity",
        "anxietyRisk",
        "depressiveRisk",
        "adherenceReadiness",
      ].includes(item)
    );

  if (domains.length > 0) {
    return Array.from(new Set(domains)).slice(0, 7);
  }

  return Object.entries(input.request.domainScores)
    .sort((left, right) => right[1] - left[1])
    .slice(0, 2)
    .map(([key]) => key) as DailyPrescriptionResponse["targetDomains"];
}

export function normalizeDecisionPayload(
  value: unknown,
  input: PrescriptionProviderInput
): Record<string, unknown> | null {
  if (typeof value !== "object" || value === null) {
    return null;
  }

  const raw = value as Record<string, unknown>;
  const catalogCodes = new Set(input.request.catalog.map((item) => item.protocolCode));
  const requestedCodes = [
    normalizeScalar(raw.primaryInterventionType),
    normalizeScalar(raw.secondaryInterventionType),
    ...normalizeStringArray(raw.lifestyleTaskCodes),
  ].filter(Boolean);

  const validCodes = requestedCodes.filter((code) => catalogCodes.has(code));
  const validProtocolCodes = validCodes.filter((code) => !code.startsWith("TASK_"));
  const validTaskCodes = validCodes.filter((code) => code.startsWith("TASK_"));

  const primaryRaw = normalizeScalar(raw.primaryInterventionType);
  const secondaryRaw = normalizeScalar(raw.secondaryInterventionType);
  const primaryInterventionType =
    (catalogCodes.has(primaryRaw) && !primaryRaw.startsWith("TASK_") ? primaryRaw : validProtocolCodes[0]) ?? "";
  const secondaryInterventionType =
    (catalogCodes.has(secondaryRaw) &&
    !secondaryRaw.startsWith("TASK_") &&
    secondaryRaw !== primaryInterventionType
      ? secondaryRaw
      : validProtocolCodes.find((code) => code !== primaryInterventionType)) ?? "";

  return {
    primaryGoal: normalizeScalar(raw.primaryGoal) || "维持稳定恢复节律",
    riskLevel: normalizeRiskLevel(raw.riskLevel),
    targetDomains: normalizeTargetDomains(raw.targetDomains, input),
    primaryInterventionType,
    secondaryInterventionType,
    lifestyleTaskCodes: Array.from(new Set(validTaskCodes)).slice(0, 6),
    timing: normalizeTiming(raw.timing),
    durationSec: normalizeDurationSec(raw.durationSec),
    rationale: normalizeScalar(raw.rationale) || normalizeScalar(raw.primaryGoal) || "根据当前画像生成干预处方。",
    evidence: normalizeStringArray(raw.evidence).slice(0, 8),
    contraindications: normalizeStringArray(raw.contraindications).slice(0, 6),
    followupMetric: normalizeScalar(raw.followupMetric) || "次日主观恢复感",
  };
}
