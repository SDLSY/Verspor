import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import process from "node:process";
import { fileURLToPath } from "node:url";
import { createClient } from "@supabase/supabase-js";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const rootDir = path.resolve(__dirname, "..", "..");
const configPath = path.join(rootDir, "src", "lib", "demo", "demo-accounts.config.json");

loadEnvFiles();

function loadEnvFiles() {
  const candidates = [
    path.join(rootDir, ".env.local"),
    path.join(rootDir, ".env.production-pulled"),
    path.join(rootDir, ".env"),
  ];
  for (const filePath of candidates) {
    if (!fs.existsSync(filePath)) continue;
    const content = fs.readFileSync(filePath, "utf8");
    for (const line of content.split(/\r?\n/)) {
      const match = line.match(/^([^#=\s]+)=(.*)$/);
      if (!match) continue;
      const key = match[1];
      const rawValue = match[2].trim();
      if (process.env[key]?.trim()) continue;
      process.env[key] = rawValue.replace(/^"(.*)"$/, "$1").replace(/^'(.*)'$/, "$1");
    }
  }
}

function requireEnv(name) {
  const value = process.env[name]?.trim();
  if (!value) {
    throw new Error(`missing env: ${name}`);
  }
  return value;
}

function optionalEnv(name, fallback = "") {
  const value = process.env[name]?.trim();
  return value || fallback;
}

function readConfig() {
  return JSON.parse(fs.readFileSync(configPath, "utf8"));
}

function createServiceClient() {
  return createClient(
    requireEnv("NEXT_PUBLIC_SUPABASE_URL"),
    requireEnv("SUPABASE_SERVICE_ROLE_KEY"),
    {
      auth: {
        autoRefreshToken: false,
        persistSession: false,
      },
    }
  );
}

function iso(ms) {
  return new Date(ms).toISOString();
}

function dayStart(now, offsetDays) {
  const date = new Date(now + offsetDays * 24 * 60 * 60 * 1000);
  date.setHours(0, 0, 0, 0);
  return date.getTime();
}

function clamp(value, min, max) {
  return Math.max(min, Math.min(max, value));
}

function sleepQuality(score) {
  if (score >= 85) return "GREAT";
  if (score >= 70) return "GOOD";
  if (score >= 55) return "FAIR";
  return "POOR";
}

function anomalyFactors(anomalyScore) {
  if (anomalyScore >= 65) return ["stress_load", "hrv_drop", "fragmented_sleep"];
  if (anomalyScore >= 45) return ["spo2_variability", "late_meal"];
  if (anomalyScore >= 25) return ["mild_sleep_fragmentation"];
  return ["stable_night"];
}

function effectMetadata(mode, effectScore, recommendationMode = "FULL") {
  return {
    recommendationMode,
    explanation: {
      summary: `本次建议重点围绕${mode}执行与恢复节律展开。`,
      reasons: [`${mode}近期执行效果稳定`, "近 24 小时恢复证据完整"],
      nextStep: "继续保持当前节律，并在次日晨报中对照恢复分变化。",
    },
    modelVersion: "demo-srm-v2",
    profileCode: "default_adult_cn",
    configSource: "database",
    evidenceCoverage: Math.min(0.98, Math.max(0.62, effectScore / 100)),
  };
}

function scientificSignals(label) {
  return {
    scientificModel: {
      decisionSummary: `${label}已经进入可讲解的演示闭环。`,
      recommendationMode: "FULL",
      modelVersion: "demo-srm-v2",
      profileCode: "default_adult_cn",
      configSource: "database",
      safetyGate: "PASS",
      evidenceCoverage: 0.84,
      evidenceLedger: [
        { label: "最近 7 天恢复分趋势可用" },
        { label: "量表与问诊摘要已归档" },
        { label: "干预执行结果已回写" },
      ],
    },
  };
}

function buildSleepRows(userId, scenarioCode, now, options) {
  const rows = {
    sleep_sessions: [],
    nightly_reports: [],
    anomaly_scores: [],
  };
  const days = options.days ?? 14;

  for (let index = days - 1; index >= 0; index -= 1) {
    const offset = -index;
    const date = dayStart(now, offset);
    const totalSleepMinutes = clamp(
      Math.round(options.totalSleepBase + options.totalSleepSlope * (days - index - 1)),
      250,
      540
    );
    const deepSleepMinutes = Math.round(totalSleepMinutes * 0.22);
    const remSleepMinutes = Math.round(totalSleepMinutes * 0.21);
    const lightSleepMinutes = totalSleepMinutes - deepSleepMinutes - remSleepMinutes;
    const awakeMinutes = clamp(options.awakeBase - Math.floor((days - index - 1) / 4), 8, 54);
    const anomalyScore = clamp(
      Math.round(options.anomalyBase + options.anomalySlope * (days - index - 1)),
      8,
      92
    );
    const recoveryScore = clamp(
      Math.round(options.recoveryBase + options.recoverySlope * (days - index - 1)),
      28,
      94
    );
    const sleepRecordId = `${scenarioCode}_sleep_${String(days - index).padStart(2, "0")}`;
    const sessionDate = date + 8 * 60 * 60 * 1000;

    rows.sleep_sessions.push({
      user_id: userId,
      sleep_record_id: sleepRecordId,
      session_date: iso(sessionDate),
      bed_time: iso(date - (7 * 60 + 20) * 60 * 1000),
      wake_time: iso(date - (7 * 60 + 20) * 60 * 1000 + (totalSleepMinutes + awakeMinutes) * 60 * 1000),
      total_sleep_minutes: totalSleepMinutes,
      deep_sleep_minutes: deepSleepMinutes,
      light_sleep_minutes: lightSleepMinutes,
      rem_sleep_minutes: remSleepMinutes,
      source: "demo_seed",
    });

    rows.nightly_reports.push({
      user_id: userId,
      sleep_record_id: sleepRecordId,
      recovery_score: recoveryScore,
      sleep_quality: sleepQuality(recoveryScore),
      insights: [
        `${options.label}近阶段恢复分为 ${recoveryScore} 分。`,
        anomalyScore >= 55 ? "夜间波动较大，建议优先调整节律与干预连续性。" : "夜间状态整体稳定，可维持当前恢复节律。",
      ],
      model_version: "demo-mmt-v1",
      created_at: iso(sessionDate + 20 * 60 * 1000),
      updated_at: iso(sessionDate + 20 * 60 * 1000),
    });

    rows.anomaly_scores.push({
      user_id: userId,
      sleep_record_id: sleepRecordId,
      score_0_100: anomalyScore,
      primary_factors: anomalyFactors(anomalyScore),
      model_version: "demo-mmt-v1",
      created_at: iso(sessionDate + 18 * 60 * 1000),
    });
  }

  return rows;
}

function makeTask(userId, scenarioCode, id, plannedAt, payload) {
  return {
    user_id: userId,
    task_id: `${scenarioCode}_${id}`,
    task_date: iso(dayStart(plannedAt, 0)),
    source_type: payload.sourceType ?? "RULE_ENGINE",
    trigger_reason: payload.triggerReason,
    body_zone: payload.bodyZone,
    protocol_type: payload.protocolType,
    duration_sec: payload.durationSec,
    planned_at: iso(plannedAt),
    status: payload.status,
    created_at: iso(plannedAt - 20 * 60 * 1000),
    updated_at: iso(plannedAt),
  };
}

function makeExecution(userId, scenarioCode, id, taskId, startedAt, payload) {
  return {
    user_id: userId,
    execution_id: `${scenarioCode}_${id}`,
    task_id: taskId,
    started_at: iso(startedAt),
    ended_at: iso(startedAt + payload.elapsedSec * 1000),
    elapsed_sec: payload.elapsedSec,
    before_stress: payload.beforeStress,
    after_stress: payload.afterStress,
    before_hr: payload.beforeHr,
    after_hr: payload.afterHr,
    effect_score: payload.effectScore,
    completion_type: payload.completionType ?? "FULL",
  };
}

function makeTrace(userId, traceType, traceKey, providerId, riskLevel, label, createdAt, overrides = {}) {
  return {
    user_id: userId,
    trace_type: traceType,
    trace_key: traceKey,
    trace_id: `${traceKey}_${crypto.randomUUID()}`,
    provider_id: providerId,
    risk_level: riskLevel,
    personalization_level: overrides.personalizationLevel ?? "FULL",
    missing_inputs_json: overrides.missingInputs ?? [],
    input_materials_json: overrides.inputMaterials ?? {},
    derived_signals_json: overrides.derivedSignals ?? scientificSignals(label),
    output_payload_json: overrides.outputPayload ?? {},
    metadata_json: overrides.metadata ?? effectMetadata(label, overrides.effectScore ?? 76, overrides.recommendationMode),
    is_fallback: overrides.isFallback ?? false,
    source: "DEMO_SEED",
    created_at: iso(createdAt),
  };
}

function makeAuditEvent(userId, actor, action, resourceType, resourceId, createdAt, metadata = {}) {
  return {
    user_id: userId,
    actor,
    action,
    resource_type: resourceType,
    resource_id: resourceId,
    metadata,
    created_at: iso(createdAt),
  };
}

function makeDoctorSummary(userId, scenarioCode, assessedAt, payload) {
  return {
    user_id: userId,
    session_id: `${scenarioCode}_doctor_session`,
    assessed_at: iso(assessedAt),
    risk_level: payload.riskLevel,
    chief_complaint: payload.chiefComplaint,
    red_flags_json: payload.redFlags,
    recommended_department: payload.recommendedDepartment,
    doctor_summary: payload.doctorSummary,
    updated_at: iso(assessedAt),
  };
}

function makeMedicalReport(userId, scenarioCode, reportId, reportDate, payload) {
  return {
    report: {
      user_id: userId,
      report_id: `${scenarioCode}_${reportId}`,
      report_date: iso(reportDate),
      report_type: payload.reportType,
      parse_status: payload.parseStatus ?? "PARSED",
      risk_level: payload.riskLevel,
      created_at: iso(reportDate + 10 * 60 * 1000),
    },
    metrics: payload.metrics.map((metric, index) => ({
      user_id: userId,
      report_id: `${scenarioCode}_${reportId}`,
      metric_code: metric.metricCode,
      metric_name: metric.metricName,
      metric_value: metric.metricValue,
      unit: metric.unit,
      ref_low: metric.refLow ?? null,
      ref_high: metric.refHigh ?? null,
      is_abnormal: Boolean(metric.isAbnormal),
      confidence: metric.confidence ?? 0.92,
    })),
  };
}

function makeMedicationRecord(userId, scenarioCode, capturedAt, payload) {
  return {
    user_id: userId,
    record_id: `${scenarioCode}_medication_01`,
    captured_at: iso(capturedAt),
    image_uri: `demo://medication/${scenarioCode}.jpg`,
    recognized_name: payload.recognizedName,
    dosage_form: payload.dosageForm,
    specification: payload.specification,
    active_ingredients_json: payload.activeIngredients,
    matched_symptoms_json: payload.matchedSymptoms,
    usage_summary: payload.usageSummary,
    risk_level: payload.riskLevel,
    risk_flags_json: payload.riskFlags,
    evidence_notes_json: payload.evidenceNotes,
    advice: payload.advice,
    confidence: payload.confidence ?? 0.84,
    requires_manual_review: Boolean(payload.requiresManualReview),
    analysis_mode: "CLOUD_IMAGE_PARSE",
    provider_id: payload.providerId ?? "vector_engine",
    model_id: payload.modelId ?? "qwen3-vl-235b-a22b-instruct",
    trace_id: `${scenarioCode}_medication_trace`,
    updated_at: iso(capturedAt),
  };
}

function makeFoodRecord(userId, scenarioCode, index, capturedAt, payload) {
  return {
    user_id: userId,
    record_id: `${scenarioCode}_food_${String(index).padStart(2, "0")}`,
    captured_at: iso(capturedAt),
    image_uri: `demo://food/${scenarioCode}/${index}.jpg`,
    meal_type: payload.mealType,
    food_items_json: payload.foodItems,
    estimated_calories: payload.estimatedCalories,
    carbohydrate_grams: payload.carbohydrateGrams,
    protein_grams: payload.proteinGrams,
    fat_grams: payload.fatGrams,
    nutrition_risk_level: payload.nutritionRiskLevel,
    nutrition_flags_json: payload.nutritionFlags,
    daily_contribution: payload.dailyContribution,
    advice: payload.advice,
    confidence: payload.confidence ?? 0.88,
    requires_manual_review: false,
    analysis_mode: "CLOUD_IMAGE_PARSE",
    provider_id: "vector_engine",
    model_id: "qwen3-vl-235b-a22b-instruct",
    trace_id: `${scenarioCode}_food_trace_${index}`,
    updated_at: iso(capturedAt),
  };
}

function makeAssessmentBaseline(userId, assessedAt, codes) {
  return {
    user_id: userId,
    completed_scale_codes_json: codes,
    completed_count: codes.length,
    completed_at: iso(assessedAt),
    freshness_until: iso(assessedAt + 21 * 24 * 60 * 60 * 1000),
    source: "DEMO_SEED",
    updated_at: iso(assessedAt),
  };
}

function makeInferenceJob(userId, scenarioCode, sleepRecordId, createdAt, payload) {
  const jobSuffix = payload.status ?? "queued";
  return {
    id: crypto.randomUUID(),
    user_id: userId,
    sleep_record_id: sleepRecordId,
    status: payload.status,
    idempotency_key: `${scenarioCode}_${sleepRecordId}_${jobSuffix}`,
    model_version: payload.modelVersion ?? "demo-mmt-v1",
    error_message: payload.errorMessage ?? null,
    created_at: iso(createdAt),
    started_at: payload.startedAt ? iso(payload.startedAt) : null,
    finished_at: payload.finishedAt ? iso(payload.finishedAt) : null,
  };
}

function emptySeed() {
  return {
    sleep_sessions: [],
    nightly_reports: [],
    anomaly_scores: [],
    intervention_tasks: [],
    intervention_executions: [],
    medical_reports: [],
    medical_metrics: [],
    doctor_inquiry_summaries: [],
    assessment_baseline_snapshots: [],
    medication_analysis_records: [],
    food_analysis_records: [],
    recommendation_traces: [],
    audit_events: [],
    inference_jobs: [],
  };
}

function buildBaselineRecoverySeed(userId, scenarioCode, now) {
  const base = emptySeed();
  Object.assign(base, buildSleepRows(userId, scenarioCode, now, {
    days: 30,
    totalSleepBase: 420,
    totalSleepSlope: 0.4,
    recoveryBase: 76,
    recoverySlope: 0.2,
    anomalyBase: 24,
    anomalySlope: -0.1,
    awakeBase: 18,
    label: "恢复基线账号",
  }));

  const task = makeTask(userId, scenarioCode, "task_01", now - 18 * 60 * 60 * 1000, {
    triggerReason: "恢复分稳定，维持晚间节律",
    bodyZone: "LIMB",
    protocolType: "RELAXATION",
    durationSec: 900,
    status: "COMPLETED",
  });
  base.intervention_tasks.push(task);
  base.intervention_executions.push(
    makeExecution(userId, scenarioCode, "execution_01", task.task_id, now - 18 * 60 * 60 * 1000, {
      elapsedSec: 900,
      beforeStress: 48,
      afterStress: 33,
      beforeHr: 76,
      afterHr: 68,
      effectScore: 82,
    })
  );
  base.assessment_baseline_snapshots.push(
    makeAssessmentBaseline(userId, now - 3 * 24 * 60 * 60 * 1000, ["ISI", "ESS", "PSS10"])
  );
  base.recommendation_traces.push(
    makeTrace(
      userId,
      "PERIOD_SUMMARY",
      `${scenarioCode}_period_summary`,
      "openrouter",
      "LOW",
      "恢复基线",
      now - 60 * 60 * 1000,
      { recommendationMode: "FULL", effectScore: 82 }
    )
  );
  base.audit_events.push(
    makeAuditEvent(
      userId,
      "demo_seed",
      "UPSERT",
      "sleep_sessions",
      `${scenarioCode}_sleep_30`,
      now - 50 * 60 * 1000,
      { scenario: scenarioCode }
    )
  );
  return base;
}

function buildReportDoctorSeed(userId, scenarioCode, now) {
  const base = emptySeed();
  Object.assign(base, buildSleepRows(userId, scenarioCode, now, {
    days: 21,
    totalSleepBase: 355,
    totalSleepSlope: 0.5,
    recoveryBase: 54,
    recoverySlope: 0.15,
    anomalyBase: 41,
    anomalySlope: -0.05,
    awakeBase: 28,
    label: "报告问诊闭环账号",
  }));

  const report = makeMedicalReport(userId, scenarioCode, "report_01", now - 2 * 24 * 60 * 60 * 1000, {
    reportType: "LAB_REPORT",
    riskLevel: "MEDIUM",
    metrics: [
      { metricCode: "LDL", metricName: "低密度脂蛋白", metricValue: 3.9, unit: "mmol/L", refHigh: 3.4, isAbnormal: true },
      { metricCode: "GLU", metricName: "空腹血糖", metricValue: 6.2, unit: "mmol/L", refLow: 3.9, refHigh: 6.1, isAbnormal: true },
    ],
  });
  base.medical_reports.push(report.report);
  base.medical_metrics.push(...report.metrics);
  base.doctor_inquiry_summaries.push(
    makeDoctorSummary(userId, scenarioCode, now - 20 * 60 * 60 * 1000, {
      riskLevel: "MEDIUM",
      chiefComplaint: "上传体检报告后，希望了解异常指标与最近睡眠差之间的关系。",
      redFlags: [],
      recommendedDepartment: "全科门诊",
      doctorSummary: "报告提示血脂和血糖边缘异常，结合近期恢复分下降，建议先做生活方式调整并继续跟踪问诊和干预执行。",
    })
  );
  base.assessment_baseline_snapshots.push(
    makeAssessmentBaseline(userId, now - 2 * 24 * 60 * 60 * 1000, ["ISI", "ESS", "PSS10", "GAD7"])
  );
  const pendingTask = makeTask(userId, scenarioCode, "task_01", now - 6 * 60 * 60 * 1000, {
    triggerReason: "报告异常进入干预闭环",
    bodyZone: "CHEST",
    protocolType: "BREATHING",
    durationSec: 180,
    status: "PENDING",
  });
  const completedTask = makeTask(userId, scenarioCode, "task_02", now - 26 * 60 * 60 * 1000, {
    triggerReason: "首轮问诊后执行保守放松方案",
    bodyZone: "LIMB",
    protocolType: "RELAXATION",
    durationSec: 600,
    status: "COMPLETED",
  });
  base.intervention_tasks.push(pendingTask, completedTask);
  base.intervention_executions.push(
    makeExecution(userId, scenarioCode, "execution_01", completedTask.task_id, now - 26 * 60 * 60 * 1000, {
      elapsedSec: 600,
      beforeStress: 72,
      afterStress: 49,
      beforeHr: 84,
      afterHr: 73,
      effectScore: 74,
    })
  );
  base.recommendation_traces.push(
    makeTrace(
      userId,
      "DOCTOR_TURN",
      `${scenarioCode}_doctor_turn`,
      "openrouter",
      "MEDIUM",
      "报告问诊闭环",
      now - 5 * 60 * 60 * 1000,
      { recommendationMode: "DOCTOR_LOOP", effectScore: 74 }
    )
  );
  base.audit_events.push(
    makeAuditEvent(
      userId,
      "demo_seed",
      "UPSERT",
      "medical_reports",
      report.report.report_id,
      now - 5 * 60 * 60 * 1000,
      { scenario: scenarioCode, riskLevel: "MEDIUM" }
    )
  );
  return base;
}

function buildLifestyleSeed(userId, scenarioCode, now) {
  const base = emptySeed();
  Object.assign(base, buildSleepRows(userId, scenarioCode, now, {
    days: 14,
    totalSleepBase: 340,
    totalSleepSlope: 0.3,
    recoveryBase: 49,
    recoverySlope: 0.12,
    anomalyBase: 47,
    anomalySlope: 0.04,
    awakeBase: 34,
    label: "药物饮食闭环账号",
  }));

  base.assessment_baseline_snapshots.push(
    makeAssessmentBaseline(userId, now - 3 * 24 * 60 * 60 * 1000, ["PSS10", "ESS", "WHO5"])
  );
  base.medication_analysis_records.push(
    makeMedicationRecord(userId, scenarioCode, now - 28 * 60 * 60 * 1000, {
      recognizedName: "布洛芬缓释胶囊",
      dosageForm: "胶囊",
      specification: "0.3g x 20 粒",
      activeIngredients: ["布洛芬"],
      matchedSymptoms: ["头痛", "肩颈不适"],
      usageSummary: "用于缓解疼痛，但不能替代对恢复分波动根因的判断。",
      riskLevel: "MEDIUM",
      riskFlags: ["若近期胃部不适需谨慎", "若频繁使用建议先咨询医生"],
      evidenceNotes: ["近两天恢复分偏低", "药物与当前症状匹配度一般，需结合主诉判断"],
      advice: "该记录已进入生活方式画像，用于解释恢复分波动与建议优先级。",
      confidence: 0.84,
      requiresManualReview: false,
    })
  );
  base.food_analysis_records.push(
    makeFoodRecord(userId, scenarioCode, 1, dayStart(now, 0) + 8 * 60 * 60 * 1000, {
      mealType: "BREAKFAST",
      foodItems: ["甜面包", "奶茶"],
      estimatedCalories: 620,
      carbohydrateGrams: 88,
      proteinGrams: 11,
      fatGrams: 20,
      nutritionRiskLevel: "HIGH",
      nutritionFlags: ["精制糖偏高", "蛋白质不足"],
      dailyContribution: "抬高上午血糖波动，不利于恢复分稳定。",
      advice: "早餐建议换成高蛋白主食组合，减少甜饮。",
    }),
    makeFoodRecord(userId, scenarioCode, 2, dayStart(now, 0) + 13 * 60 * 60 * 1000, {
      mealType: "LUNCH",
      foodItems: ["米饭", "炸鸡", "可乐"],
      estimatedCalories: 860,
      carbohydrateGrams: 92,
      proteinGrams: 24,
      fatGrams: 34,
      nutritionRiskLevel: "MEDIUM",
      nutritionFlags: ["脂肪偏高", "蔬菜不足"],
      dailyContribution: "午餐负荷偏大，会拖累下午恢复感。",
      advice: "午餐优先减少油炸和含糖饮料，补足蔬菜。",
    }),
    makeFoodRecord(userId, scenarioCode, 3, dayStart(now, -1) + 20 * 60 * 60 * 1000, {
      mealType: "DINNER",
      foodItems: ["火锅", "啤酒"],
      estimatedCalories: 980,
      carbohydrateGrams: 54,
      proteinGrams: 38,
      fatGrams: 48,
      nutritionRiskLevel: "HIGH",
      nutritionFlags: ["晚餐总热量偏高", "夜间刺激负担偏大"],
      dailyContribution: "会放大夜间唤醒和次日疲劳。",
      advice: "睡前 4 小时减少高油高辣和酒精摄入。",
    })
  );
  base.recommendation_traces.push(
    makeTrace(
      userId,
      "DAILY_PRESCRIPTION",
      `${scenarioCode}_lifestyle_daily`,
      "openrouter",
      "MEDIUM",
      "药物饮食闭环",
      now - 45 * 60 * 1000,
      { recommendationMode: "LIFESTYLE", effectScore: 68 }
    )
  );
  base.audit_events.push(
    makeAuditEvent(
      userId,
      "demo_seed",
      "UPSERT",
      "food_analysis_records",
      `${scenarioCode}_food_03`,
      now - 40 * 60 * 1000,
      { scenario: scenarioCode }
    )
  );
  return base;
}

function buildLiveInterventionSeed(userId, scenarioCode, now) {
  const base = emptySeed();
  Object.assign(base, buildSleepRows(userId, scenarioCode, now, {
    days: 10,
    totalSleepBase: 352,
    totalSleepSlope: 0.6,
    recoveryBase: 47,
    recoverySlope: 0.35,
    anomalyBase: 44,
    anomalySlope: -0.08,
    awakeBase: 30,
    label: "现场干预闭环账号",
  }));

  base.assessment_baseline_snapshots.push(
    makeAssessmentBaseline(userId, now - 5 * 24 * 60 * 60 * 1000, ["PSS10", "ESS"])
  );
  const task1 = makeTask(userId, scenarioCode, "task_01", now - 2 * 60 * 60 * 1000, {
    triggerReason: "实时压力偏高，建议呼吸训练",
    bodyZone: "CHEST",
    protocolType: "BREATHING",
    durationSec: 180,
    status: "COMPLETED",
  });
  const task2 = makeTask(userId, scenarioCode, "task_02", now - 70 * 60 * 1000, {
    triggerReason: "睡前思绪活跃，建议 Zen 交互",
    bodyZone: "HEAD",
    protocolType: "RELAXATION",
    durationSec: 300,
    status: "COMPLETED",
  });
  const task3 = makeTask(userId, scenarioCode, "task_03", now - 25 * 60 * 1000, {
    triggerReason: "准备进入睡前音景流程",
    bodyZone: "LIMB",
    protocolType: "RECOVERY",
    durationSec: 900,
    status: "PENDING",
  });
  base.intervention_tasks.push(task1, task2, task3);
  base.intervention_executions.push(
    makeExecution(userId, scenarioCode, "execution_01", task1.task_id, now - 2 * 60 * 60 * 1000, {
      elapsedSec: 180,
      beforeStress: 79,
      afterStress: 47,
      beforeHr: 88,
      afterHr: 72,
      effectScore: 83,
    }),
    makeExecution(userId, scenarioCode, "execution_02", task2.task_id, now - 70 * 60 * 1000, {
      elapsedSec: 300,
      beforeStress: 73,
      afterStress: 45,
      beforeHr: 83,
      afterHr: 69,
      effectScore: 80,
    })
  );
  base.recommendation_traces.push(
    makeTrace(
      userId,
      "DAILY_PRESCRIPTION",
      `${scenarioCode}_live_intervention`,
      "openrouter",
      "LOW",
      "现场干预闭环",
      now - 35 * 60 * 1000,
      { recommendationMode: "RECOVERY", effectScore: 81 }
    )
  );
  base.audit_events.push(
    makeAuditEvent(
      userId,
      "demo_seed",
      "UPSERT",
      "intervention_executions",
      `${scenarioCode}_execution_02`,
      now - 30 * 60 * 1000,
      { scenario: scenarioCode, liveMode: true }
    )
  );
  return base;
}

function buildHighRiskSeed(userId, scenarioCode, now) {
  const base = emptySeed();
  Object.assign(base, buildSleepRows(userId, scenarioCode, now, {
    days: 30,
    totalSleepBase: 292,
    totalSleepSlope: 0.3,
    recoveryBase: 38,
    recoverySlope: 0.05,
    anomalyBase: 68,
    anomalySlope: 0.06,
    awakeBase: 42,
    label: "高风险运营闭环账号",
  }));

  const report = makeMedicalReport(userId, scenarioCode, "report_01", now - 24 * 60 * 60 * 1000, {
    reportType: "PDF",
    riskLevel: "HIGH",
    metrics: [
      { metricCode: "SBP", metricName: "收缩压", metricValue: 152, unit: "mmHg", refLow: 90, refHigh: 140, isAbnormal: true },
      { metricCode: "DBP", metricName: "舒张压", metricValue: 98, unit: "mmHg", refLow: 60, refHigh: 90, isAbnormal: true },
    ],
  });
  base.medical_reports.push(report.report);
  base.medical_metrics.push(...report.metrics);
  base.doctor_inquiry_summaries.push(
    makeDoctorSummary(userId, scenarioCode, now - 18 * 60 * 60 * 1000, {
      riskLevel: "HIGH",
      chiefComplaint: "连续多天恢复分很低，夜间反复醒，白天胸闷焦躁。",
      redFlags: ["胸闷若持续或加重需优先线下就医"],
      recommendedDepartment: "综合门诊",
      doctorSummary: "当前演示账号处于高风险链路，建议优先就医评估，再以保守干预作为辅助。",
    })
  );
  base.assessment_baseline_snapshots.push(
    makeAssessmentBaseline(userId, now - 24 * 60 * 60 * 1000, ["ISI", "ESS", "PSS10", "GAD7", "PHQ9"])
  );
  const pendingTask = makeTask(userId, scenarioCode, "task_01", now - 90 * 60 * 1000, {
    triggerReason: "高风险场景优先安排线下评估",
    bodyZone: "CHEST",
    protocolType: "RECOVERY",
    durationSec: 120,
    status: "PENDING",
  });
  base.intervention_tasks.push(pendingTask);
  base.inference_jobs.push(
    makeInferenceJob(
      userId,
      scenarioCode,
      `${scenarioCode}_sleep_30`,
      now - 3 * 60 * 60 * 1000,
      {
        status: "failed",
        errorMessage: "demo queued worker timeout",
        startedAt: now - 175 * 60 * 1000,
        finishedAt: now - 170 * 60 * 1000,
      }
    ),
    makeInferenceJob(
      userId,
      scenarioCode,
      `${scenarioCode}_sleep_29`,
      now - 90 * 60 * 1000,
      {
        status: "queued",
      }
    )
  );
  base.recommendation_traces.push(
    makeTrace(
      userId,
      "DAILY_PRESCRIPTION",
      `${scenarioCode}_high_risk_daily`,
      "openrouter",
      "HIGH",
      "高风险运营闭环",
      now - 80 * 60 * 1000,
      { recommendationMode: "HIGH_RISK", effectScore: 41 }
    )
  );
  base.audit_events.push(
    makeAuditEvent(
      userId,
      "demo_seed",
      "UPSERT",
      "inference_jobs",
      base.inference_jobs[0].id,
      now - 70 * 60 * 1000,
      { scenario: scenarioCode, status: "failed" }
    )
  );
  return base;
}

function buildScenarioSeed(userId, scenarioCode, now) {
  switch (scenarioCode) {
    case "demo_baseline_recovery":
      return buildBaselineRecoverySeed(userId, scenarioCode, now);
    case "demo_report_doctor_loop":
      return buildReportDoctorSeed(userId, scenarioCode, now);
    case "demo_lifestyle_loop":
      return buildLifestyleSeed(userId, scenarioCode, now);
    case "demo_live_intervention":
      return buildLiveInterventionSeed(userId, scenarioCode, now);
    case "demo_high_risk_ops":
      return buildHighRiskSeed(userId, scenarioCode, now);
    default:
      return emptySeed();
  }
}

function isMissingTableError(error) {
  const message = error?.message?.toLowerCase?.() ?? "";
  return (
    message.includes("does not exist") ||
    message.includes("could not find the table") ||
    message.includes("schema cache")
  );
}

async function clearRows(client, table, userId) {
  const { error } = await client.from(table).delete().eq("user_id", userId);
  if (error && !isMissingTableError(error)) {
    throw new Error(`[${table}] clear failed: ${error.message}`);
  }
}

async function insertRows(client, table, rows) {
  if (!rows.length) {
    return;
  }
  const { error } = await client.from(table).insert(rows);
  if (error) {
    throw new Error(`[${table}] insert failed: ${error.message}`);
  }
}

async function upsertUser(client, existingUsersByEmail, account, seedVersion, domain, defaultPassword, adminPassword) {
  const email = `${account.scenario}@${domain}`;
  const password = account.role === "demo_admin" ? adminPassword : defaultPassword;
  const metadata = {
    demoRole: account.role,
    demoScenario: account.scenario,
    demoSeedVersion: seedVersion,
    displayName: account.displayName,
    username: account.scenario,
    full_name: account.displayName,
  };

  const existing = existingUsersByEmail.get(email);
  if (existing) {
    const { data, error } = await client.auth.admin.updateUserById(existing.id, {
      email,
      password,
      email_confirm: true,
      user_metadata: metadata,
    });
    if (error) {
      throw new Error(`update demo user failed for ${email}: ${error.message}`);
    }
    return { user: data.user, email };
  }

  const { data, error } = await client.auth.admin.createUser({
    email,
    password,
    email_confirm: true,
    user_metadata: metadata,
  });
  if (error || !data.user) {
    throw new Error(`create demo user failed for ${email}: ${error?.message ?? "unknown error"}`);
  }
  existingUsersByEmail.set(email, data.user);
  return { user: data.user, email };
}

async function loadExistingUsers(client) {
  const existing = new Map();
  let page = 1;
  const perPage = 100;

  while (true) {
    const { data, error } = await client.auth.admin.listUsers({ page, perPage });
    if (error) {
      throw new Error(`list users failed: ${error.message}`);
    }
    const users = data.users ?? [];
    if (!users.length) {
      break;
    }
    users.forEach((user) => {
      if (user.email) {
        existing.set(user.email, user);
      }
    });
    if (users.length < perPage) {
      break;
    }
    page += 1;
  }

  return existing;
}

async function seedScenarioData(client, userId, scenarioCode, now) {
  const tables = [
    "sleep_sessions",
    "nightly_reports",
    "anomaly_scores",
    "intervention_tasks",
    "intervention_executions",
    "medical_reports",
    "medical_metrics",
    "doctor_inquiry_summaries",
    "assessment_baseline_snapshots",
    "medication_analysis_records",
    "food_analysis_records",
    "recommendation_traces",
    "audit_events",
    "inference_jobs",
  ];

  for (const table of tables) {
    await clearRows(client, table, userId);
  }

  const seed = buildScenarioSeed(userId, scenarioCode, now);
  for (const table of tables) {
    await insertRows(client, table, seed[table] ?? []);
  }

  return seed;
}

async function main() {
  const client = createServiceClient();
  const config = readConfig();
  const domain = optionalEnv("DEMO_ACCOUNT_EMAIL_DOMAIN", "demo.changgengring.local");
  const defaultPassword = requireEnv("DEMO_ACCOUNT_DEFAULT_PASSWORD");
  const adminPassword = optionalEnv("DEMO_ADMIN_DEFAULT_PASSWORD", defaultPassword);
  const existingUsersByEmail = await loadExistingUsers(client);
  const now = Date.now();
  const summaries = [];

  for (const account of config.accounts) {
    const { user, email } = await upsertUser(
      client,
      existingUsersByEmail,
      account,
      config.seedVersion,
      domain,
      defaultPassword,
      adminPassword
    );

    let seededRows = 0;
    if (account.role === "demo_user") {
      const seed = await seedScenarioData(client, user.id, account.scenario, now);
      seededRows = Object.values(seed).reduce((sum, list) => sum + list.length, 0);
    }

    summaries.push({
      scenario: account.scenario,
      role: account.role,
      email,
      displayName: account.displayName,
      seededRows,
    });
  }

  console.log("\nDemo accounts seeded successfully.\n");
  console.table(summaries);
  console.log(`Seed version: ${config.seedVersion}`);
  console.log(`User password source: DEMO_ACCOUNT_DEFAULT_PASSWORD${adminPassword !== defaultPassword ? " / DEMO_ADMIN_DEFAULT_PASSWORD" : ""}`);
  console.log(`Remember to add demo_admin_console@${domain} into ADMIN_EMAIL_ALLOWLIST for admin console login.\n`);
}

main().catch((error) => {
  console.error("\nDemo seed failed:\n", error);
  process.exitCode = 1;
});
