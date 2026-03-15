import type { SupabaseClient } from "@supabase/supabase-js";

type JsonObject = Record<string, unknown>;

type DoctorInquiryRow = {
  chief_complaint: string;
  doctor_summary: string;
  red_flags_json: unknown;
};

type NightlyReportRow = {
  recovery_score: number | null;
  sleep_quality: string | null;
};

type MedicalReportRow = {
  risk_level: string;
  report_type: string;
};

export type LifestyleUserContext = {
  symptomSummary: string;
  currentStatus: string;
  redFlags: string[];
};

export type MedicationVisionPayload = {
  recognizedName: string;
  dosageForm: string;
  specification: string;
  activeIngredients: string[];
  matchedSymptoms: string[];
  usageSummary: string;
  riskLevel: string;
  riskFlags: string[];
  evidenceNotes: string[];
  advice: string;
  confidence: number;
  requiresManualReview: boolean;
};

export type FoodVisionPayload = {
  mealType: string;
  foodItems: string[];
  estimatedCalories: number;
  carbohydrateGrams: number;
  proteinGrams: number;
  fatGrams: number;
  nutritionRiskLevel: string;
  nutritionFlags: string[];
  dailyContribution: string;
  advice: string;
  confidence: number;
  requiresManualReview: boolean;
};

function toObject(value: unknown): JsonObject {
  if (typeof value !== "object" || value == null || Array.isArray(value)) {
    return {};
  }
  return value as JsonObject;
}

function toStringValue(value: unknown, maxLength: number, fallback = ""): string {
  const normalized = typeof value === "string" ? value.trim() : "";
  return normalized.slice(0, maxLength) || fallback;
}

function toStringArray(value: unknown, limit: number, maxItemLength: number): string[] {
  if (!Array.isArray(value)) {
    return [];
  }
  const items = value
    .map((item) => (typeof item === "string" ? item.trim() : ""))
    .filter(Boolean)
    .slice(0, limit)
    .map((item) => item.slice(0, maxItemLength));
  return Array.from(new Set(items));
}

function toBooleanValue(value: unknown): boolean {
  if (typeof value === "boolean") {
    return value;
  }
  if (typeof value === "string") {
    return value.trim().toLowerCase() === "true";
  }
  return false;
}

function toNumberValue(value: unknown, min: number, max: number, fallback = 0): number {
  const numberValue = Number(value);
  if (!Number.isFinite(numberValue)) {
    return fallback;
  }
  return Math.min(max, Math.max(min, numberValue));
}

function toRiskLevel(value: unknown, fallback = "LOW"): string {
  const normalized = toStringValue(value, 32, fallback).toUpperCase();
  if (normalized === "HIGH" || normalized === "MEDIUM" || normalized === "LOW") {
    return normalized;
  }
  return fallback;
}

function joinCompact(values: string[]): string {
  return values.filter(Boolean).join("，").trim();
}

export function normalizeMedicationVisionPayload(input: unknown): MedicationVisionPayload {
  const payload = toObject(input);
  const confidence = toNumberValue(payload.confidence, 0, 1, 0.45);
  const recognizedName = toStringValue(payload.recognizedName, 120);
  return {
    recognizedName,
    dosageForm: toStringValue(payload.dosageForm, 80),
    specification: toStringValue(payload.specification, 120),
    activeIngredients: toStringArray(payload.activeIngredients, 8, 80),
    matchedSymptoms: toStringArray(payload.matchedSymptoms, 8, 80),
    usageSummary: toStringValue(payload.usageSummary, 400),
    riskLevel: toRiskLevel(payload.riskLevel),
    riskFlags: toStringArray(payload.riskFlags, 8, 120),
    evidenceNotes: toStringArray(payload.evidenceNotes, 8, 160),
    advice: toStringValue(payload.advice, 500),
    confidence,
    requiresManualReview:
      toBooleanValue(payload.requiresManualReview) || confidence < 0.65 || recognizedName.length === 0,
  };
}

export function normalizeFoodVisionPayload(input: unknown): FoodVisionPayload {
  const payload = toObject(input);
  const confidence = toNumberValue(payload.confidence, 0, 1, 0.45);
  return {
    mealType: toStringValue(payload.mealType, 32, "UNSPECIFIED").toUpperCase(),
    foodItems: toStringArray(payload.foodItems, 10, 80),
    estimatedCalories: Math.round(toNumberValue(payload.estimatedCalories, 0, 4000, 0)),
    carbohydrateGrams: toNumberValue(payload.carbohydrateGrams, 0, 500, 0),
    proteinGrams: toNumberValue(payload.proteinGrams, 0, 300, 0),
    fatGrams: toNumberValue(payload.fatGrams, 0, 250, 0),
    nutritionRiskLevel: toRiskLevel(payload.nutritionRiskLevel),
    nutritionFlags: toStringArray(payload.nutritionFlags, 8, 120),
    dailyContribution: toStringValue(payload.dailyContribution, 500),
    advice: toStringValue(payload.advice, 500),
    confidence,
    requiresManualReview: toBooleanValue(payload.requiresManualReview) || confidence < 0.65,
  };
}

export async function loadLifestyleUserContext(
  client: SupabaseClient,
  userId: string
): Promise<LifestyleUserContext> {
  const [doctorRes, nightlyRes, medicalRes] = await Promise.all([
    client
      .from("doctor_inquiry_summaries")
      .select("chief_complaint,doctor_summary,red_flags_json")
      .eq("user_id", userId)
      .order("assessed_at", { ascending: false })
      .limit(1)
      .maybeSingle<DoctorInquiryRow>(),
    client
      .from("nightly_reports")
      .select("recovery_score,sleep_quality")
      .eq("user_id", userId)
      .order("created_at", { ascending: false })
      .limit(1)
      .maybeSingle<NightlyReportRow>(),
    client
      .from("medical_reports")
      .select("risk_level,report_type")
      .eq("user_id", userId)
      .order("report_date", { ascending: false })
      .limit(1)
      .maybeSingle<MedicalReportRow>(),
  ]);

  const doctor = doctorRes.error ? null : doctorRes.data;
  const nightly = nightlyRes.error ? null : nightlyRes.data;
  const medical = medicalRes.error ? null : medicalRes.data;

  return {
    symptomSummary:
      doctor?.doctor_summary?.trim() ||
      doctor?.chief_complaint?.trim() ||
      "暂无结构化症状摘要",
    currentStatus:
      joinCompact([
        nightly?.recovery_score != null ? `最近恢复分 ${Math.round(nightly.recovery_score)}` : "",
        nightly?.sleep_quality ? `睡眠质量 ${nightly.sleep_quality}` : "",
        medical?.risk_level ? `最近医检风险 ${medical.risk_level}` : "",
        medical?.report_type ? `最近报告类型 ${medical.report_type}` : "",
      ]) || "暂无最新恢复状态摘要",
    redFlags: toStringArray(doctor?.red_flags_json, 8, 120),
  };
}
