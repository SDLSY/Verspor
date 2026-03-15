import type { SupabaseClient } from "@supabase/supabase-js";
import type { DailyPrescriptionRequest, PrescriptionServerContext } from "@/lib/prescription/types";
import { resolvePersonalizationStatus } from "@/lib/personalization/status";

type NightlyReportRow = {
  sleep_record_id: string;
  recovery_score: number | null;
  sleep_quality: string | null;
  insights: unknown;
  created_at: string;
};

type InterventionTaskRow = {
  task_id: string;
  protocol_type: string;
  status: string;
  task_date: string;
};

type InterventionExecutionRow = {
  task_id: string;
  effect_score: number | null;
  before_stress: number | null;
  after_stress: number | null;
  completion_type: string;
  started_at: string;
};

type MedicalReportRow = {
  report_id: string;
  report_date: string;
  risk_level: string;
};

type MedicalMetricRow = {
  metric_code: string;
  metric_name: string;
  is_abnormal: boolean;
};

type AssessmentBaselineSnapshotRow = {
  completed_count: number;
  freshness_until: string;
};

type DoctorInquirySummaryRow = {
  assessed_at: string;
  risk_level: string;
  red_flags_json: unknown;
};

type MedicationAnalysisRow = {
  recognized_name: string;
  risk_level: string;
  risk_flags_json: unknown;
  requires_manual_review: boolean;
  captured_at: string;
};

type FoodAnalysisRow = {
  meal_type: string;
  estimated_calories: number;
  nutrition_risk_level: string;
  nutrition_flags_json: unknown;
  captured_at: string;
};

function toStringArray(value: unknown): string[] {
  if (!Array.isArray(value)) {
    return [];
  }
  return value.filter((item): item is string => typeof item === "string" && item.trim().length > 0);
}

function uniq(values: string[]): string[] {
  return Array.from(new Set(values.map((item) => item.trim()).filter(Boolean)));
}

function safeNumber(value: number | null | undefined): number | null {
  return typeof value === "number" && Number.isFinite(value) ? value : null;
}

function average(values: number[]): number | null {
  if (values.length === 0) {
    return null;
  }
  return values.reduce((sum, value) => sum + value, 0) / values.length;
}

export async function buildPrescriptionServerContext(
  client: SupabaseClient,
  userId: string,
  request: DailyPrescriptionRequest
): Promise<PrescriptionServerContext> {
  const now = Date.now();
  const fourteenDaysAgo = new Date(now - 14 * 24 * 60 * 60 * 1000).toISOString();
  const sevenDaysAgo = new Date(now - 7 * 24 * 60 * 60 * 1000).toISOString();

  const [sleepRes, nightlyRes, taskRes, executionRes, reportRes, baselineRes, inquiryRes, medicationRes, foodRes] = await Promise.all([
    client
      .from("sleep_sessions")
      .select("session_date")
      .eq("user_id", userId)
      .gte("session_date", sevenDaysAgo)
      .order("session_date", { ascending: false })
      .limit(7),
    client
      .from("nightly_reports")
      .select("sleep_record_id,recovery_score,sleep_quality,insights,created_at")
      .eq("user_id", userId)
      .order("created_at", { ascending: false })
      .limit(1)
      .maybeSingle<NightlyReportRow>(),
    client
      .from("intervention_tasks")
      .select("task_id,protocol_type,status,task_date")
      .eq("user_id", userId)
      .gte("task_date", fourteenDaysAgo)
      .order("task_date", { ascending: false })
      .limit(16)
      .returns<InterventionTaskRow[]>(),
    client
      .from("intervention_executions")
      .select("task_id,effect_score,before_stress,after_stress,completion_type,started_at")
      .eq("user_id", userId)
      .gte("started_at", fourteenDaysAgo)
      .order("started_at", { ascending: false })
      .limit(16)
      .returns<InterventionExecutionRow[]>(),
    client
      .from("medical_reports")
      .select("report_id,report_date,risk_level")
      .eq("user_id", userId)
      .order("report_date", { ascending: false })
      .limit(1)
      .maybeSingle<MedicalReportRow>(),
    client
      .from("assessment_baseline_snapshots")
      .select("completed_count,freshness_until")
      .eq("user_id", userId)
      .order("completed_at", { ascending: false })
      .limit(1)
      .maybeSingle<AssessmentBaselineSnapshotRow>(),
    client
      .from("doctor_inquiry_summaries")
      .select("assessed_at,risk_level,red_flags_json")
      .eq("user_id", userId)
      .order("assessed_at", { ascending: false })
      .limit(1)
      .maybeSingle<DoctorInquirySummaryRow>(),
    client
      .from("medication_analysis_records")
      .select("recognized_name,risk_level,risk_flags_json,requires_manual_review,captured_at")
      .eq("user_id", userId)
      .order("captured_at", { ascending: false })
      .limit(1)
      .maybeSingle<MedicationAnalysisRow>(),
    client
      .from("food_analysis_records")
      .select("meal_type,estimated_calories,nutrition_risk_level,nutrition_flags_json,captured_at")
      .eq("user_id", userId)
      .order("captured_at", { ascending: false })
      .limit(3)
      .returns<FoodAnalysisRow[]>(),
  ]);

  const sleepRows = sleepRes.error ? [] : sleepRes.data ?? [];
  const latestNightly = nightlyRes.error ? null : nightlyRes.data;
  const recentTasks = taskRes.error ? [] : taskRes.data ?? [];
  const recentExecutions = executionRes.error ? [] : executionRes.data ?? [];
  const latestReport = reportRes.error ? null : reportRes.data;
  const latestBaseline = baselineRes.error ? null : baselineRes.data;
  const latestInquiry = inquiryRes.error ? null : inquiryRes.data;
  const latestMedication = medicationRes.error ? null : medicationRes.data;
  const recentFood = foodRes.error ? [] : foodRes.data ?? [];

  let abnormalMetrics: MedicalMetricRow[] = [];
  if (latestReport?.report_id) {
    const metricRes = await client
      .from("medical_metrics")
      .select("metric_code,metric_name,is_abnormal")
      .eq("user_id", userId)
      .eq("report_id", latestReport.report_id)
      .eq("is_abnormal", true)
      .limit(8)
      .returns<MedicalMetricRow[]>();
    abnormalMetrics = metricRes.error ? [] : metricRes.data ?? [];
  }

  const serverEvidenceFacts: Record<string, string[]> = {};
  const serverRedFlags: string[] = [];

  if (latestNightly) {
    const sleepFacts = uniq([
      latestNightly.recovery_score == null ? "" : `最近一次夜间恢复分 ${Math.round(latestNightly.recovery_score)}`,
      latestNightly.sleep_quality ? `最近一次睡眠质量 ${latestNightly.sleep_quality}` : "",
      ...toStringArray(latestNightly.insights).slice(0, 3),
    ]);
    if (sleepFacts.length > 0) {
      serverEvidenceFacts.sleep = sleepFacts;
    }
    if ((latestNightly.recovery_score ?? 100) <= 35) {
      serverRedFlags.push("RECOVERY_VERY_LOW");
    }
  }

  const taskProtocolById = new Map(recentTasks.map((item) => [item.task_id, item.protocol_type]));
  const protocolStats = new Map<
    string,
    { taskCount: number; completedCount: number; executionCount: number; effectScores: number[]; stressDrops: number[] }
  >();

  for (const task of recentTasks) {
    const bucket = protocolStats.get(task.protocol_type) ?? {
      taskCount: 0,
      completedCount: 0,
      executionCount: 0,
      effectScores: [],
      stressDrops: [],
    };
    bucket.taskCount += 1;
    if (task.status === "COMPLETED") {
      bucket.completedCount += 1;
    }
    protocolStats.set(task.protocol_type, bucket);
  }

  const stressDeltas = recentExecutions
    .filter((item) => item.before_stress != null && item.after_stress != null)
    .map((item) => Number(item.before_stress ?? 0) - Number(item.after_stress ?? 0));
  const effectScores = recentExecutions
    .map((item) => safeNumber(item.effect_score))
    .filter((item): item is number => item != null);

  for (const execution of recentExecutions) {
    const protocol = taskProtocolById.get(execution.task_id);
    if (!protocol) {
      continue;
    }
    const bucket = protocolStats.get(protocol) ?? {
      taskCount: 0,
      completedCount: 0,
      executionCount: 0,
      effectScores: [],
      stressDrops: [],
    };
    bucket.executionCount += 1;
    const effect = safeNumber(execution.effect_score);
    if (effect != null) {
      bucket.effectScores.push(effect);
    }
    if (execution.before_stress != null && execution.after_stress != null) {
      bucket.stressDrops.push(Number(execution.before_stress) - Number(execution.after_stress));
    }
    protocolStats.set(protocol, bucket);
  }

  const avgStressDrop = average(stressDeltas);
  const avgEffect = average(effectScores);
  if (recentExecutions.length > 0) {
    serverEvidenceFacts.intervention = uniq([
      avgStressDrop == null ? "" : `近 14 天执行后平均压力下降 ${avgStressDrop.toFixed(1)}`,
      avgEffect == null ? "" : `近 14 天平均效果分 ${avgEffect.toFixed(1)}`,
      `近 14 天已记录 ${recentExecutions.length} 次执行回执`,
    ]);
  }

  const latestTaskSummary = recentTasks.slice(0, 4).map((item) => `${item.protocol_type}:${item.status}`);
  const breathingFatigue =
    recentTasks.slice(0, 3).length >= 3 &&
    recentTasks
      .slice(0, 3)
      .every((item) => item.protocol_type.toUpperCase().startsWith("BREATH") && item.status !== "COMPLETED");

  const preferredProtocolCodes: string[] = [];
  const downweightedProtocolCodes: string[] = [];
  for (const [protocolCode, stat] of protocolStats.entries()) {
    const completionRate = stat.taskCount > 0 ? stat.completedCount / stat.taskCount : 0;
    const avgProtocolEffect = average(stat.effectScores);
    if (stat.executionCount >= 2 && (avgProtocolEffect ?? Number.NEGATIVE_INFINITY) >= 15) {
      preferredProtocolCodes.push(protocolCode);
    }
    if (stat.taskCount >= 2 && completionRate < 0.4) {
      downweightedProtocolCodes.push(protocolCode);
    }
  }
  if (breathingFatigue) {
    downweightedProtocolCodes.push(
      ...recentTasks
        .filter((item) => item.protocol_type.toUpperCase().startsWith("BREATH"))
        .map((item) => item.protocol_type)
    );
  }

  if (latestReport) {
    const medicalFacts = uniq([
      `最近一次医检风险等级 ${latestReport.risk_level}`,
      ...abnormalMetrics.map((item) => `${item.metric_name} 异常`),
    ]);
    if (medicalFacts.length > 0) {
      serverEvidenceFacts.medical = medicalFacts;
    }
    if (latestReport.risk_level === "HIGH") {
      serverRedFlags.push("HIGH_MEDICAL_RISK");
    }
  }

  if (latestMedication) {
    const medicationFacts = uniq([
      latestMedication.recognized_name ? `最近药物记录 ${latestMedication.recognized_name}` : "",
      latestMedication.requires_manual_review ? "药物识别结果仍需人工确认" : "",
      ...toStringArray(latestMedication.risk_flags_json),
    ]);
    if (medicationFacts.length > 0) {
      serverEvidenceFacts.medication = medicationFacts;
    }
    if (latestMedication.risk_level === "HIGH" || latestMedication.requires_manual_review) {
      serverRedFlags.push("MEDICATION_REVIEW_REQUIRED");
    }
  }

  if (recentFood.length > 0) {
    const totalCalories = recentFood.reduce((sum, item) => sum + Number(item.estimated_calories ?? 0), 0);
    const nutritionFlags = uniq(recentFood.flatMap((item) => toStringArray(item.nutrition_flags_json)));
    serverEvidenceFacts.nutrition = uniq([
      `最近饮食热量估算 ${Math.round(totalCalories)} kcal`,
      ...nutritionFlags.slice(0, 3),
    ]);
    if (recentFood.some((item) => item.nutrition_risk_level === "HIGH")) {
      serverRedFlags.push("NUTRITION_HIGH_RISK");
    }
  }

  if (latestInquiry?.risk_level === "HIGH") {
    serverRedFlags.push("HIGH_DOCTOR_RISK");
  }
  serverRedFlags.push(...toStringArray(latestInquiry?.red_flags_json));

  if ((request.redFlags.length > 0 || serverRedFlags.length > 0) && !serverRedFlags.includes("DOCTOR_PRIORITY")) {
    serverRedFlags.push("DOCTOR_PRIORITY");
  }

  const personalizationStatus = resolvePersonalizationStatus({
    baseMissingInputs: request.missingInputs,
    hasEnoughDeviceData: sleepRows.length >= 3,
    hasFreshBaseline:
      latestBaseline != null &&
      latestBaseline.completed_count >= 6 &&
      new Date(latestBaseline.freshness_until).getTime() > now,
    hasRecentDoctorInquiry:
      latestInquiry != null &&
      new Date(latestInquiry.assessed_at).getTime() >= now - 30 * 24 * 60 * 60 * 1000,
  });

  return {
    serverEvidenceFacts,
    serverRedFlags: uniq(serverRedFlags),
    latestRecoveryScore: latestNightly?.recovery_score ?? null,
    latestSleepQuality: latestNightly?.sleep_quality ?? null,
    latestMedicalRiskLevel: latestReport?.risk_level ?? null,
    latestMedicalMetricLabels: uniq(abnormalMetrics.map((item) => item.metric_name)),
    latestMedicationSummary:
      latestMedication?.recognized_name?.trim()
        ? `${latestMedication.recognized_name} / ${latestMedication.risk_level}`
        : null,
    recentMedicationRiskFlags: uniq(toStringArray(latestMedication?.risk_flags_json)),
    latestNutritionSummary:
      recentFood.length > 0
        ? `${recentFood[0]?.meal_type ?? "UNSPECIFIED"} / ${Math.round(
            recentFood.reduce((sum, item) => sum + Number(item.estimated_calories ?? 0), 0)
          )} kcal`
        : null,
    nutritionRiskLevel: recentFood[0]?.nutrition_risk_level ?? null,
    recentInterventionSummary: uniq(latestTaskSummary),
    latestSleepSummary: uniq(serverEvidenceFacts.sleep ?? []),
    breathingFatigue,
    preferredProtocolCodes: uniq(preferredProtocolCodes),
    downweightedProtocolCodes: uniq(downweightedProtocolCodes),
    averageEffectScore: avgEffect,
    averageStressDrop: avgStressDrop,
    personalizationLevel: personalizationStatus.personalizationLevel,
    missingInputs: personalizationStatus.missingInputs as PrescriptionServerContext["missingInputs"],
  };
}
