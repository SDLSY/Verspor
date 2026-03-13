import type { SupabaseClient } from "@supabase/supabase-js";
import { resolvePersonalizationStatus } from "@/lib/personalization/status";
import type { PrescriptionPersonalizationLevel } from "@/lib/prescription/types";

const DAY_MS = 24 * 60 * 60 * 1000;

type PeriodKind = "weekly" | "monthly";
type RiskLevel = "LOW" | "MEDIUM" | "HIGH";
type TrendDirection = "UP" | "DOWN" | "STABLE";
type ActionItemType = "PRIMARY" | "SECONDARY" | "LIFESTYLE";
type ReportConfidence = "LOW" | "MEDIUM" | "HIGH";

type SleepSessionRow = {
  session_date: string;
  total_sleep_minutes: number | null;
  deep_sleep_minutes: number | null;
  light_sleep_minutes: number | null;
  rem_sleep_minutes: number | null;
};

type NightlyReportRow = {
  created_at: string;
  recovery_score: number | null;
  sleep_quality: string | null;
  insights: unknown;
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
  ended_at: string;
};

type MedicalReportRow = {
  report_id: string;
  report_date: string;
  risk_level: string;
};

type MedicalMetricRow = {
  report_id: string;
  metric_name: string;
  is_abnormal: boolean;
};

type PrescriptionSnapshotRow = {
  personalization_level: PrescriptionPersonalizationLevel | null;
  missing_inputs_json: unknown;
};

type AssessmentBaselineSnapshotRow = {
  completed_count: number;
  freshness_until: string;
};

type DoctorInquirySummaryRow = {
  assessed_at: string;
};

type ActionDefinition = {
  title: string;
  subtitle: string;
  protocolCode: string;
  durationSec: number;
  assetRef: string;
  itemType: ActionItemType;
};

export type PeriodSummaryMetric = {
  label: string;
  value: string;
  comparison: string;
  direction: TrendDirection;
};

export type PeriodSummaryAction = ActionDefinition;

export type PeriodSummaryPayload = {
  period: PeriodKind;
  periodLabel: string;
  title: string;
  sampleSufficient: boolean;
  headline: string;
  riskLevel: RiskLevel;
  riskSummary: string;
  highlights: string[];
  metricChanges: PeriodSummaryMetric[];
  interventionSummary: string;
  nextFocusTitle: string;
  nextFocusDetail: string;
  personalizationLevel: PrescriptionPersonalizationLevel;
  missingInputs: string[];
  reportConfidence: ReportConfidence;
  actions: PeriodSummaryAction[];
};

type GeneratePeriodSummaryInput = {
  client: SupabaseClient;
  userId: string;
  period: PeriodKind;
};

const ACTIONS: Record<string, ActionDefinition> = {
  SLEEP_WIND_DOWN_15M: {
    title: "睡前减刺激流程",
    subtitle: "先把晚间刺激降下来，再进入深度放松。",
    protocolCode: "SLEEP_WIND_DOWN_15M",
    durationSec: 900,
    assetRef: "session://wind-down/15m",
    itemType: "PRIMARY",
  },
  BODY_SCAN_NSDR_10M: {
    title: "身体扫描 NSDR",
    subtitle: "适合睡前高唤醒或白天压力堆积后的快速降负荷。",
    protocolCode: "BODY_SCAN_NSDR_10M",
    durationSec: 600,
    assetRef: "session://body-scan/10m",
    itemType: "SECONDARY",
  },
  GUIDED_STRETCH_MOBILITY_8M: {
    title: "引导拉伸与活动恢复",
    subtitle: "用低门槛活动恢复替代继续硬扛疲劳。",
    protocolCode: "GUIDED_STRETCH_MOBILITY_8M",
    durationSec: 480,
    assetRef: "session://stretch/8m",
    itemType: "SECONDARY",
  },
  RECOVERY_WALK_10M: {
    title: "恢复步行",
    subtitle: "白天完成一轮轻步行，帮助恢复和节律稳定。",
    protocolCode: "RECOVERY_WALK_10M",
    durationSec: 600,
    assetRef: "session://walk/10m",
    itemType: "PRIMARY",
  },
  SOUNDSCAPE_SLEEP_AUDIO_15M: {
    title: "助眠音景",
    subtitle: "适合作为低门槛的睡前入口，先把执行做起来。",
    protocolCode: "SOUNDSCAPE_SLEEP_AUDIO_15M",
    durationSec: 900,
    assetRef: "audio://soundscape/15m",
    itemType: "PRIMARY",
  },
  TASK_SCREEN_CURFEW: {
    title: "睡前 60 分钟停用强蓝光屏幕",
    subtitle: "把晚间刺激先降下来，睡前建议更容易生效。",
    protocolCode: "TASK_SCREEN_CURFEW",
    durationSec: 900,
    assetRef: "task://screen-curfew",
    itemType: "LIFESTYLE",
  },
  TASK_CAFFEINE_CUTOFF: {
    title: "下午 2 点后不摄入咖啡因",
    subtitle: "减少晚间残余兴奋，避免继续拉高入睡门槛。",
    protocolCode: "TASK_CAFFEINE_CUTOFF",
    durationSec: 60,
    assetRef: "task://caffeine-cutoff",
    itemType: "LIFESTYLE",
  },
  TASK_DAYLIGHT_WALK: {
    title: "白天户外轻步行",
    subtitle: "用日光和轻活动帮助节律稳定。",
    protocolCode: "TASK_DAYLIGHT_WALK",
    durationSec: 600,
    assetRef: "task://daylight-walk",
    itemType: "LIFESTYLE",
  },
  TASK_WORRY_LIST: {
    title: "担忧清单",
    subtitle: "把脑内循环的担忧写出来，降低睡前反刍。",
    protocolCode: "TASK_WORRY_LIST",
    durationSec: 300,
    assetRef: "task://worry-list",
    itemType: "LIFESTYLE",
  },
  TASK_DOCTOR_PRIORITY: {
    title: "优先联系医生",
    subtitle: "本周期出现高风险变化，建议先做专业评估。",
    protocolCode: "TASK_DOCTOR_PRIORITY",
    durationSec: 120,
    assetRef: "screen://doctor",
    itemType: "LIFESTYLE",
  },
};

function uniq(values: string[]): string[] {
  return Array.from(new Set(values.map((item) => item.trim()).filter(Boolean)));
}

function toStringArray(value: unknown): string[] {
  if (!Array.isArray(value)) {
    return [];
  }
  return value.filter((item): item is string => typeof item === "string" && item.trim().length > 0);
}

function avg(values: number[]): number | null {
  if (values.length === 0) {
    return null;
  }
  return values.reduce((sum, value) => sum + value, 0) / values.length;
}

function safeNumber(value: number | null | undefined): number | null {
  return typeof value === "number" && Number.isFinite(value) ? value : null;
}

function partitionByPeriod<T>(
  rows: T[],
  getTime: (row: T) => number,
  currentStart: number,
  previousStart: number
): { current: T[]; previous: T[] } {
  const current: T[] = [];
  const previous: T[] = [];
  rows.forEach((row) => {
    const time = getTime(row);
    if (time >= currentStart) {
      current.push(row);
    } else if (time >= previousStart) {
      previous.push(row);
    }
  });
  return { current, previous };
}

function formatHours(hours: number | null): string {
  if (hours == null) return "--";
  return `${hours.toFixed(1)} 小时`;
}

function formatScore(score: number | null): string {
  if (score == null) return "--";
  return `${Math.round(score)} 分`;
}

function formatCount(count: number): string {
  return `${count} 次`;
}

function formatDelta(current: number | null, previous: number | null, unit: string): { text: string; direction: TrendDirection } {
  if (current == null || previous == null) {
    return { text: "较上周期样本不足", direction: "STABLE" };
  }
  const delta = current - previous;
  if (Math.abs(delta) < 0.01) {
    return { text: "较上周期持平", direction: "STABLE" };
  }
  const prefix = delta > 0 ? "+" : "";
  return {
    text: `较上周期 ${prefix}${delta.toFixed(unit === "次" ? 0 : 1)} ${unit}`,
    direction: delta > 0 ? "UP" : "DOWN",
  };
}

function formatQualityTrend(delta: number | null): string {
  if (delta == null || Math.abs(delta) < 0.01) return "总体稳定";
  return delta > 0 ? "较上周期改善" : "较上周期回落";
}

function buildMetricChanges(input: {
  avgSleepHours: number | null;
  prevSleepHours: number | null;
  avgRecovery: number | null;
  prevRecovery: number | null;
  executionCount: number;
  prevExecutionCount: number;
  avgEffectScore: number | null;
  prevAvgEffectScore: number | null;
}): PeriodSummaryMetric[] {
  const sleepDelta = formatDelta(input.avgSleepHours, input.prevSleepHours, "小时");
  const recoveryDelta = formatDelta(input.avgRecovery, input.prevRecovery, "分");
  const executionDelta = formatDelta(input.executionCount, input.prevExecutionCount, "次");
  const effectDelta = formatDelta(input.avgEffectScore, input.prevAvgEffectScore, "分");

  return [
    {
      label: "平均睡眠时长",
      value: formatHours(input.avgSleepHours),
      comparison: sleepDelta.text,
      direction: sleepDelta.direction,
    },
    {
      label: "平均恢复分",
      value: formatScore(input.avgRecovery),
      comparison: recoveryDelta.text,
      direction: recoveryDelta.direction,
    },
    {
      label: "干预执行次数",
      value: formatCount(input.executionCount),
      comparison: executionDelta.text,
      direction: executionDelta.direction,
    },
    {
      label: "平均干预效果",
      value: formatScore(input.avgEffectScore),
      comparison: effectDelta.text,
      direction: effectDelta.direction,
    },
  ];
}

function resolveRiskLevel(input: {
  latestMedicalRisk: string | null;
  abnormalMetricCount: number;
  avgSleepHours: number | null;
  avgRecovery: number | null;
  adherenceRate: number | null;
}): RiskLevel {
  if (
    input.latestMedicalRisk === "HIGH" ||
    input.abnormalMetricCount >= 3 ||
    (input.avgSleepHours != null && input.avgSleepHours < 5) ||
    (input.avgRecovery != null && input.avgRecovery < 35)
  ) {
    return "HIGH";
  }
  if (
    input.latestMedicalRisk === "MEDIUM" ||
    (input.avgSleepHours != null && input.avgSleepHours < 6.5) ||
    (input.avgRecovery != null && input.avgRecovery < 55) ||
    (input.adherenceRate != null && input.adherenceRate < 0.45)
  ) {
    return "MEDIUM";
  }
  return "LOW";
}

function pickBestProtocol(
  taskProtocolById: Map<string, string>,
  executions: InterventionExecutionRow[]
): string | null {
  const buckets = new Map<string, { effectSum: number; count: number }>();
  executions.forEach((execution) => {
    const protocol = taskProtocolById.get(execution.task_id);
    if (!protocol) return;
    const effect = safeNumber(execution.effect_score) ?? 0;
    const bucket = buckets.get(protocol) ?? { effectSum: 0, count: 0 };
    bucket.effectSum += effect;
    bucket.count += 1;
    buckets.set(protocol, bucket);
  });

  let bestCode: string | null = null;
  let bestAvg = Number.NEGATIVE_INFINITY;
  for (const [code, bucket] of buckets.entries()) {
    if (bucket.count <= 0) continue;
    const avgEffect = bucket.effectSum / bucket.count;
    if (avgEffect > bestAvg) {
      bestAvg = avgEffect;
      bestCode = code;
    }
  }
  return bestCode;
}

function buildHeadline(input: {
  periodLabel: string;
  sampleSufficient: boolean;
  avgSleepHours: number | null;
  prevSleepHours: number | null;
  avgRecovery: number | null;
  prevRecovery: number | null;
  adherenceRate: number | null;
  latestMedicalRisk: string | null;
}): string {
  if (!input.sampleSufficient) {
    return `${input.periodLabel}样本还不够完整，建议继续佩戴设备并坚持记录干预执行。`;
  }

  const sleepDelta = input.avgSleepHours != null && input.prevSleepHours != null
    ? input.avgSleepHours - input.prevSleepHours
    : null;
  const recoveryDelta = input.avgRecovery != null && input.prevRecovery != null
    ? input.avgRecovery - input.prevRecovery
    : null;

  if (input.latestMedicalRisk === "HIGH") {
    return `${input.periodLabel}出现了较高风险的医检信号，放松建议只能作为辅助，优先级需要让位给进一步评估。`;
  }
  if ((input.avgSleepHours ?? 99) < 6.5 && (recoveryDelta ?? 0) > 2) {
    return `${input.periodLabel}睡眠仍然偏短，但恢复能力在回升，说明当前干预开始起作用。`;
  }
  if ((input.avgSleepHours ?? 99) < 6.5) {
    return `${input.periodLabel}最主要的问题仍然是睡眠时长不足，后续重点需要继续压低睡前唤醒。`;
  }
  if ((input.avgRecovery ?? 100) < 50) {
    return `${input.periodLabel}恢复能力仍然偏低，下一阶段要优先做低门槛恢复而不是继续硬扛疲劳。`;
  }
  if ((input.adherenceRate ?? 1) < 0.45) {
    return `${input.periodLabel}执行次数还不够，真正限制效果的不是方案数量，而是执行稳定性。`;
  }
  if ((sleepDelta ?? 0) > 0.2 || (recoveryDelta ?? 0) > 2) {
    return `${input.periodLabel}整体状态在稳步改善，可以继续沿用当前有效的干预组合。`;
  }
  return `${input.periodLabel}整体较为稳定，下一阶段重点是保持节律并逐步提高干预依从性。`;
}

function buildRiskSummary(input: {
  riskLevel: RiskLevel;
  latestMedicalRisk: string | null;
  abnormalMetricCount: number;
  avgSleepHours: number | null;
  avgRecovery: number | null;
  highRiskSignals: string[];
}): string {
  if (input.riskLevel === "HIGH") {
    return uniq([
      input.latestMedicalRisk === "HIGH" ? "本周期出现高风险医检信号。" : "",
      input.abnormalMetricCount > 0 ? `本周期累计发现 ${input.abnormalMetricCount} 项异常指标。` : "",
      (input.avgSleepHours ?? 99) < 5 ? "平均睡眠时长明显不足。" : "",
      (input.avgRecovery ?? 100) < 35 ? "恢复分持续偏低。" : "",
      ...input.highRiskSignals,
    ]).join(" ");
  }
  if (input.riskLevel === "MEDIUM") {
    return uniq([
      (input.avgSleepHours ?? 99) < 6.5 ? "睡眠扰动仍在中等风险区间。" : "",
      (input.avgRecovery ?? 100) < 55 ? "恢复能力仍有改善空间。" : "",
      input.latestMedicalRisk === "MEDIUM" ? "最近医检提示需要持续关注。" : "",
    ]).join(" ");
  }
  return "当前没有新的高风险信号，重点转向维持节律与巩固有效干预。";
}

function buildNextFocus(input: {
  riskLevel: RiskLevel;
  avgSleepHours: number | null;
  avgRecovery: number | null;
  adherenceRate: number | null;
  bestProtocolCode: string | null;
  avgStressDrop: number | null;
}): { title: string; detail: string; actions: PeriodSummaryAction[] } {
  if (input.riskLevel === "HIGH") {
    return {
      title: "下阶段先做风险分流",
      detail: "先完成医生评估或复查，再把低负荷放松作为辅助，而不是继续堆叠自助训练。",
      actions: [ACTIONS.TASK_DOCTOR_PRIORITY, ACTIONS.BODY_SCAN_NSDR_10M],
    };
  }
  if ((input.avgSleepHours ?? 99) < 6.5) {
    return {
      title: "下阶段重点先稳定睡前流程",
      detail: "优先继续执行睡前减刺激和身体扫描，减少晚间刺激输入，再观察入睡与次日恢复变化。",
      actions: [ACTIONS.SLEEP_WIND_DOWN_15M, ACTIONS.BODY_SCAN_NSDR_10M],
    };
  }
  if ((input.avgRecovery ?? 100) < 50) {
    return {
      title: "下阶段重点做恢复型干预",
      detail: "优先用恢复步行和引导拉伸替代高门槛训练，把恢复能力先托住。",
      actions: [ACTIONS.RECOVERY_WALK_10M, ACTIONS.GUIDED_STRETCH_MOBILITY_8M],
    };
  }
  if ((input.adherenceRate ?? 1) < 0.45) {
    return {
      title: "下阶段先提高可执行性",
      detail: "先用低门槛的助眠音景和一个生活任务把执行稳定下来，再逐步加回复杂干预。",
      actions: [ACTIONS.SOUNDSCAPE_SLEEP_AUDIO_15M, ACTIONS.TASK_SCREEN_CURFEW],
    };
  }
  if (input.bestProtocolCode && ACTIONS[input.bestProtocolCode]) {
    return {
      title: "下阶段延续本周期最有效的干预",
      detail: "当前最有效的协议已经出现，建议继续保持，并同步加入一个轻量生活任务巩固效果。",
      actions: [ACTIONS[input.bestProtocolCode], ACTIONS.TASK_DAYLIGHT_WALK],
    };
  }
  if ((input.avgStressDrop ?? 0) > 5) {
    return {
      title: "下阶段维持减压收益",
      detail: "本周期执行后压力下降较明显，建议保持睡前或晚间的稳定放松入口。",
      actions: [ACTIONS.BODY_SCAN_NSDR_10M, ACTIONS.TASK_WORRY_LIST],
    };
  }
  return {
    title: "下阶段继续保持稳定节律",
    detail: "当前更适合维持低负担、可长期坚持的节律型干预，避免频繁切换方案。",
    actions: [ACTIONS.SOUNDSCAPE_SLEEP_AUDIO_15M, ACTIONS.TASK_DAYLIGHT_WALK],
  };
}

function resolveReportConfidence(
  sampleSufficient: boolean,
  personalizationLevel: PrescriptionPersonalizationLevel
): ReportConfidence {
  if (!sampleSufficient) {
    return "LOW";
  }
  return personalizationLevel === "FULL" ? "HIGH" : "MEDIUM";
}

export async function generatePeriodSummary(
  input: GeneratePeriodSummaryInput
): Promise<PeriodSummaryPayload> {
  const days = input.period === "weekly" ? 7 : 30;
  const periodLabel = input.period === "weekly" ? "本周" : "本月";
  const minSamples = input.period === "weekly" ? 3 : 10;
  const now = Date.now();
  const currentStart = now - days * DAY_MS;
  const previousStart = now - days * 2 * DAY_MS;
  const previousStartIso = new Date(previousStart).toISOString();
  const sevenDaysAgo = now - 7 * DAY_MS;

  const [sleepRes, nightlyRes, taskRes, executionRes, medicalRes, snapshotRes, baselineRes, inquiryRes] = await Promise.all([
    input.client
      .from("sleep_sessions")
      .select("session_date,total_sleep_minutes,deep_sleep_minutes,light_sleep_minutes,rem_sleep_minutes")
      .eq("user_id", input.userId)
      .gte("session_date", previousStartIso)
      .order("session_date", { ascending: true })
      .returns<SleepSessionRow[]>(),
    input.client
      .from("nightly_reports")
      .select("created_at,recovery_score,sleep_quality,insights")
      .eq("user_id", input.userId)
      .gte("created_at", previousStartIso)
      .order("created_at", { ascending: true })
      .returns<NightlyReportRow[]>(),
    input.client
      .from("intervention_tasks")
      .select("task_id,protocol_type,status,task_date")
      .eq("user_id", input.userId)
      .gte("task_date", previousStartIso)
      .order("task_date", { ascending: true })
      .returns<InterventionTaskRow[]>(),
    input.client
      .from("intervention_executions")
      .select("task_id,effect_score,before_stress,after_stress,completion_type,ended_at")
      .eq("user_id", input.userId)
      .gte("ended_at", previousStartIso)
      .order("ended_at", { ascending: true })
      .returns<InterventionExecutionRow[]>(),
    input.client
      .from("medical_reports")
      .select("report_id,report_date,risk_level")
      .eq("user_id", input.userId)
      .gte("report_date", previousStartIso)
      .order("report_date", { ascending: true })
      .returns<MedicalReportRow[]>(),
    input.client
      .from("prescription_snapshots")
      .select("personalization_level,missing_inputs_json")
      .eq("user_id", input.userId)
      .order("snapshot_date", { ascending: false })
      .limit(1)
      .maybeSingle<PrescriptionSnapshotRow>(),
    input.client
      .from("assessment_baseline_snapshots")
      .select("completed_count,freshness_until")
      .eq("user_id", input.userId)
      .order("completed_at", { ascending: false })
      .limit(1)
      .maybeSingle<AssessmentBaselineSnapshotRow>(),
    input.client
      .from("doctor_inquiry_summaries")
      .select("assessed_at")
      .eq("user_id", input.userId)
      .order("assessed_at", { ascending: false })
      .limit(1)
      .maybeSingle<DoctorInquirySummaryRow>(),
  ]);

  if (sleepRes.error) throw new Error(sleepRes.error.message);
  if (nightlyRes.error) throw new Error(nightlyRes.error.message);
  if (taskRes.error) throw new Error(taskRes.error.message);
  if (executionRes.error) throw new Error(executionRes.error.message);
  if (medicalRes.error) throw new Error(medicalRes.error.message);
  if (snapshotRes.error) throw new Error(snapshotRes.error.message);
  if (baselineRes.error) throw new Error(baselineRes.error.message);
  if (inquiryRes.error) throw new Error(inquiryRes.error.message);

  const sleepRows = sleepRes.data ?? [];
  const nightlyRows = nightlyRes.data ?? [];
  const taskRows = taskRes.data ?? [];
  const executionRows = executionRes.data ?? [];
  const medicalRows = medicalRes.data ?? [];
  const latestSnapshot = snapshotRes.data;
  const latestBaseline = baselineRes.data;
  const latestInquiry = inquiryRes.data;

  const sleepPartition = partitionByPeriod(sleepRows, (row) => new Date(row.session_date).getTime(), currentStart, previousStart);
  const nightlyPartition = partitionByPeriod(nightlyRows, (row) => new Date(row.created_at).getTime(), currentStart, previousStart);
  const taskPartition = partitionByPeriod(taskRows, (row) => new Date(row.task_date).getTime(), currentStart, previousStart);
  const executionPartition = partitionByPeriod(executionRows, (row) => new Date(row.ended_at).getTime(), currentStart, previousStart);
  const medicalPartition = partitionByPeriod(medicalRows, (row) => new Date(row.report_date).getTime(), currentStart, previousStart);

  const currentReportIds = medicalPartition.current.map((row) => row.report_id);
  let abnormalMetrics: MedicalMetricRow[] = [];
  if (currentReportIds.length > 0) {
    const metricsRes = await input.client
      .from("medical_metrics")
      .select("report_id,metric_name,is_abnormal")
      .eq("user_id", input.userId)
      .in("report_id", currentReportIds)
      .eq("is_abnormal", true)
      .returns<MedicalMetricRow[]>();
    if (metricsRes.error) throw new Error(metricsRes.error.message);
    abnormalMetrics = metricsRes.data ?? [];
  }

  const currentSleepHours = avg(
    sleepPartition.current
      .map((row) => safeNumber(row.total_sleep_minutes))
      .filter((value): value is number => value != null)
      .map((value) => value / 60)
  );
  const previousSleepHours = avg(
    sleepPartition.previous
      .map((row) => safeNumber(row.total_sleep_minutes))
      .filter((value): value is number => value != null)
      .map((value) => value / 60)
  );
  const currentRecovery = avg(
    nightlyPartition.current.map((row) => safeNumber(row.recovery_score)).filter((value): value is number => value != null)
  );
  const previousRecovery = avg(
    nightlyPartition.previous.map((row) => safeNumber(row.recovery_score)).filter((value): value is number => value != null)
  );
  const currentEffect = avg(
    executionPartition.current.map((row) => safeNumber(row.effect_score)).filter((value): value is number => value != null)
  );
  const previousEffect = avg(
    executionPartition.previous.map((row) => safeNumber(row.effect_score)).filter((value): value is number => value != null)
  );
  const currentStressDrop = avg(
    executionPartition.current
      .map((row) => {
        const before = safeNumber(row.before_stress);
        const after = safeNumber(row.after_stress);
        return before != null && after != null ? before - after : null;
      })
      .filter((value): value is number => value != null)
  );

  const currentTaskCount = taskPartition.current.length;
  const currentCompletedTaskCount = taskPartition.current.filter((row) => row.status === "COMPLETED").length;
  const adherenceRate = currentTaskCount > 0 ? currentCompletedTaskCount / currentTaskCount : null;

  const currentExecutionCount = executionPartition.current.length;
  const previousExecutionCount = executionPartition.previous.length;
  const latestMedicalRisk = medicalPartition.current.length > 0
    ? medicalPartition.current[medicalPartition.current.length - 1].risk_level
    : null;

  const taskProtocolById = new Map(taskRows.map((row) => [row.task_id, row.protocol_type]));
  const bestProtocolCode = pickBestProtocol(taskProtocolById, executionPartition.current);
  const abnormalMetricNames = uniq(abnormalMetrics.map((row) => row.metric_name));
  const latestInsights = uniq(
    nightlyPartition.current
      .flatMap((row) => toStringArray(row.insights))
      .slice(-6)
  ).slice(0, 3);

  const riskLevel = resolveRiskLevel({
    latestMedicalRisk,
    abnormalMetricCount: abnormalMetrics.length,
    avgSleepHours: currentSleepHours,
    avgRecovery: currentRecovery,
    adherenceRate,
  });

  const sampleSufficient = sleepPartition.current.length >= minSamples;
  const recentSevenDaySleepSamples = sleepRows.filter(
    (row) => new Date(row.session_date).getTime() >= sevenDaysAgo
  ).length;
  const snapshotMissingInputs = toStringArray(latestSnapshot?.missing_inputs_json);
  const personalizationStatus = resolvePersonalizationStatus({
    baseMissingInputs:
      latestSnapshot?.personalization_level != null || snapshotMissingInputs.length > 0
        ? snapshotMissingInputs
        : ["DEVICE_DATA", "BASELINE_ASSESSMENT", "DOCTOR_INQUIRY"],
    hasEnoughDeviceData: recentSevenDaySleepSamples >= 3,
    hasFreshBaseline:
      latestBaseline != null &&
      latestBaseline.completed_count >= 6 &&
      new Date(latestBaseline.freshness_until).getTime() > now,
    hasRecentDoctorInquiry:
      latestInquiry != null &&
      new Date(latestInquiry.assessed_at).getTime() >= now - 30 * DAY_MS,
  });
  const personalizationLevel = personalizationStatus.personalizationLevel;
  const missingInputs = personalizationStatus.missingInputs;
  const reportConfidence = resolveReportConfidence(sampleSufficient, personalizationLevel);
  const headline = buildHeadline({
    periodLabel,
    sampleSufficient,
    avgSleepHours: currentSleepHours,
    prevSleepHours: previousSleepHours,
    avgRecovery: currentRecovery,
    prevRecovery: previousRecovery,
    adherenceRate,
    latestMedicalRisk,
  });

  const riskSignals = abnormalMetricNames.slice(0, 3).map((name) => `${name} 需要持续关注。`);
  const riskSummary = buildRiskSummary({
    riskLevel,
    latestMedicalRisk,
    abnormalMetricCount: abnormalMetrics.length,
    avgSleepHours: currentSleepHours,
    avgRecovery: currentRecovery,
    highRiskSignals: riskSignals,
  });

  const metricChanges = buildMetricChanges({
    avgSleepHours: currentSleepHours,
    prevSleepHours: previousSleepHours,
    avgRecovery: currentRecovery,
    prevRecovery: previousRecovery,
    executionCount: currentExecutionCount,
    prevExecutionCount: previousExecutionCount,
    avgEffectScore: currentEffect,
    prevAvgEffectScore: previousEffect,
  });

  const qualityLabels = uniq(nightlyPartition.current.map((row) => row.sleep_quality ?? "")).slice(0, 2);
  const interventionSummary = uniq([
    currentExecutionCount > 0 ? `${periodLabel}共完成 ${currentExecutionCount} 次干预` : `${periodLabel}还没有形成有效的执行样本`,
    adherenceRate == null ? "" : `任务完成率 ${Math.round(adherenceRate * 100)}%`,
    currentEffect == null ? "" : `平均效果分 ${Math.round(currentEffect)} 分`,
    currentStressDrop == null ? "" : `执行后平均压力下降 ${currentStressDrop.toFixed(1)} 分`,
    bestProtocolCode && ACTIONS[bestProtocolCode] ? `${ACTIONS[bestProtocolCode].title} 的效果最突出` : "",
  ]).join("，");

  const nextFocus = buildNextFocus({
    riskLevel,
    avgSleepHours: currentSleepHours,
    avgRecovery: currentRecovery,
    adherenceRate,
    bestProtocolCode,
    avgStressDrop: currentStressDrop,
  });

  const highlights = uniq([
    currentSleepHours == null ? "" : `${periodLabel}平均睡眠 ${formatHours(currentSleepHours)}，${formatQualityTrend(currentSleepHours != null && previousSleepHours != null ? currentSleepHours - previousSleepHours : null)}`,
    currentRecovery == null ? "" : `${periodLabel}平均恢复 ${formatScore(currentRecovery)}，${formatQualityTrend(currentRecovery != null && previousRecovery != null ? currentRecovery - previousRecovery : null)}`,
    qualityLabels.length > 0 ? `${periodLabel}睡眠质量标签：${qualityLabels.join("、")}` : "",
    latestInsights.length > 0 ? latestInsights[0] : "",
    abnormalMetricNames.length > 0 ? `${periodLabel}医检关注项：${abnormalMetricNames.slice(0, 3).join("、")}` : "",
  ]).slice(0, 5);

  return {
    period: input.period,
    periodLabel,
    title: `${periodLabel}健康报告`,
    sampleSufficient,
    headline,
    riskLevel,
    riskSummary,
    highlights,
    metricChanges,
    interventionSummary,
    nextFocusTitle: nextFocus.title,
    nextFocusDetail: nextFocus.detail,
    personalizationLevel,
    missingInputs,
    reportConfidence,
    actions: nextFocus.actions,
  };
}
