import type { User } from "@supabase/supabase-js";

export type AdminDemoScenarioCode =
  | "demo_baseline_recovery"
  | "demo_report_doctor_loop"
  | "demo_lifestyle_loop"
  | "demo_live_intervention"
  | "demo_high_risk_ops";

export type AdminScenarioDefinition = {
  code: AdminDemoScenarioCode;
  title: string;
  shortTitle: string;
  label: string;
  summary: string;
  storyStage: string;
  actionSummary: string;
  jumpLabel: string;
  buildPath: (userId?: string | null) => string;
};

export type AdminScenarioInfo = {
  isDemoUser: boolean;
  scenarioCode: AdminDemoScenarioCode | null;
  scenarioLabel: string | null;
  storyTitle: string | null;
  storyStage: string | null;
  actionSummary: string | null;
  summary: string | null;
  jumpLabel: string | null;
  recommendedPath: string;
};

export const adminDemoScenarios: AdminScenarioDefinition[] = [
  {
    code: "demo_baseline_recovery",
    title: "恢复基线闭环",
    shortTitle: "恢复基线",
    label: "恢复基线闭环",
    summary: "展示正常监测、恢复分生成、近 7 天与近 30 天趋势如何连成一条主线。",
    storyStage: "从监测到趋势",
    actionSummary: "先讲今日恢复分，再切趋势周报和月报。",
    jumpLabel: "打开恢复链路",
    buildPath: (userId) => (userId ? `/patients/${userId}#overview` : "/patients?demoOnly=1&scenarioCode=demo_baseline_recovery"),
  },
  {
    code: "demo_report_doctor_loop",
    title: "报告问诊闭环",
    shortTitle: "报告问诊",
    label: "报告问诊闭环",
    summary: "展示异常报告如何进入可读化、问诊摘要，再形成干预入口。",
    storyStage: "从报告到问诊",
    actionSummary: "先看报告，再看问诊，再落到干预。",
    jumpLabel: "打开报告链路",
    buildPath: (userId) => (userId ? `/patients/${userId}#reports` : "/reports?parseStatus=PARSED"),
  },
  {
    code: "demo_lifestyle_loop",
    title: "药食画像闭环",
    shortTitle: "药食画像",
    label: "药食画像闭环",
    summary: "展示药物识别、饮食分析如何进入画像、恢复分解释与建议闭环。",
    storyStage: "从生活方式到画像",
    actionSummary: "先看生活方式记录，再讲建议和恢复分解释。",
    jumpLabel: "打开药食链路",
    buildPath: (userId) => (userId ? `/patients/${userId}#timeline` : "/recommendations?days=30"),
  },
  {
    code: "demo_live_intervention",
    title: "实时干预闭环",
    shortTitle: "实时干预",
    label: "实时干预闭环",
    summary: "展示实时数据、呼吸训练、Zen 轻交互、音景执行和复盘回写。",
    storyStage: "从实时反馈到复盘",
    actionSummary: "先看当前状态，再讲执行记录和回写结果。",
    jumpLabel: "打开干预链路",
    buildPath: (userId) => (userId ? `/patients/${userId}#interventions` : "/patients?demoOnly=1&scenarioCode=demo_live_intervention"),
  },
  {
    code: "demo_high_risk_ops",
    title: "高风险运维闭环",
    shortTitle: "高风险运维",
    label: "高风险运维闭环",
    summary: "展示高风险患者、待处理报告、失败作业和人工处理动作如何联动。",
    storyStage: "从风险到处理",
    actionSummary: "先看风险和报告，再讲作业和处理动作。",
    jumpLabel: "打开高风险链路",
    buildPath: (userId) => (userId ? `/patients/${userId}#timeline` : "/system/jobs?status=FAILED"),
  },
];

const scenarioByCode = new Map(adminDemoScenarios.map((item) => [item.code, item]));

function readString(value: unknown): string | null {
  return typeof value === "string" && value.trim().length > 0 ? value.trim() : null;
}

function readMetadata(value: unknown): Record<string, unknown> {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    return {};
  }
  return value as Record<string, unknown>;
}

function inferScenarioCode(email: string | null, metadata: Record<string, unknown>): AdminDemoScenarioCode | null {
  const fromMetadata = readString(metadata.demoScenario);
  if (fromMetadata && scenarioByCode.has(fromMetadata as AdminDemoScenarioCode)) {
    return fromMetadata as AdminDemoScenarioCode;
  }
  const localPart = readString(email)?.split("@")[0] ?? null;
  if (localPart && scenarioByCode.has(localPart as AdminDemoScenarioCode)) {
    return localPart as AdminDemoScenarioCode;
  }
  return null;
}

export function getAdminScenarioInfo(user: Pick<User, "email" | "user_metadata"> | { email?: string | null; user_metadata?: unknown }): AdminScenarioInfo {
  const email = readString(user.email ?? null);
  const metadata = readMetadata("user_metadata" in user ? user.user_metadata : {});
  const scenarioCode = inferScenarioCode(email, metadata);
  const scenario = scenarioCode ? scenarioByCode.get(scenarioCode) ?? null : null;

  return {
    isDemoUser: Boolean(scenario),
    scenarioCode,
    scenarioLabel: scenario?.label ?? null,
    storyTitle: scenario?.title ?? null,
    storyStage: scenario?.storyStage ?? null,
    actionSummary: scenario?.actionSummary ?? null,
    summary: scenario?.summary ?? null,
    jumpLabel: scenario?.jumpLabel ?? null,
    recommendedPath: scenario?.buildPath(null) ?? "/patients",
  };
}

export function getScenarioDefinition(code: AdminDemoScenarioCode | null | undefined): AdminScenarioDefinition | null {
  if (!code) {
    return null;
  }
  return scenarioByCode.get(code) ?? null;
}

export function buildScenarioPath(code: AdminDemoScenarioCode | null | undefined, userId?: string | null): string {
  const scenario = getScenarioDefinition(code);
  return scenario?.buildPath(userId) ?? "/patients";
}

export function buildScenarioEvidenceCount(item: {
  latestSleepDate: number | null;
  latestReportAt: number | null;
  latestAbnormalMetricCount: number;
  pendingInterventionCount: number;
  latestJobStatus: string | null;
}): number {
  let count = 0;
  if (item.latestSleepDate) count += 1;
  if (item.latestReportAt) count += 1;
  if (item.latestAbnormalMetricCount > 0) count += 1;
  if (item.pendingInterventionCount > 0) count += 1;
  if (item.latestJobStatus) count += 1;
  return count;
}
