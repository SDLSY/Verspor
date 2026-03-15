function normalizeToken(value: string | null | undefined): string {
  return (value ?? "").trim().toUpperCase();
}

export function formatStatusLabel(value: string | null | undefined): string {
  const normalized = normalizeToken(value);
  const labels: Record<string, string> = {
    ALL: "全部",
    QUEUED: "排队中",
    PENDING: "待处理",
    RUNNING: "运行中",
    SUCCEEDED: "成功",
    COMPLETED: "已完成",
    FAILED: "失败",
    PARSED: "已解析",
    ACTIVE: "启用中",
    NORMAL: "正常",
    ABNORMAL: "异常",
  };
  return labels[normalized] ?? (value?.trim() || "-");
}

export function formatRiskLabel(value: string | null | undefined): string {
  const normalized = normalizeToken(value);
  const labels: Record<string, string> = {
    HIGH: "高",
    MEDIUM: "中",
    LOW: "低",
    CRITICAL: "危急",
    MODERATE: "中",
  };
  return labels[normalized] ?? (value?.trim() || "-");
}

export function formatRuntimeLabel(value: string | null | undefined): string {
  const normalized = normalizeToken(value);
  const labels: Record<string, string> = {
    HTTP: "云端推理",
    FALLBACK: "本地兜底",
  };
  return labels[normalized] ?? (value?.trim() || "-");
}

export function formatHealthStatusLabel(value: string | null | undefined): string {
  const normalized = normalizeToken(value);
  const labels: Record<string, string> = {
    OK: "正常",
    ERROR: "异常",
    FALLBACK: "兜底",
    INTERNAL: "内网地址",
    UNKNOWN: "未知",
  };
  return labels[normalized] ?? (value?.trim() || "-");
}

export function formatSleepQualityLabel(value: string | null | undefined): string {
  const normalized = normalizeToken(value);
  const labels: Record<string, string> = {
    GREAT: "优秀",
    GOOD: "良好",
    FAIR: "一般",
    POOR: "较差",
    BAD: "很差",
    UNKNOWN: "未知",
  };
  return labels[normalized] ?? (value?.trim() || "-");
}

export function formatSourceTypeLabel(value: string | null | undefined): string {
  const normalized = normalizeToken(value);
  const labels: Record<string, string> = {
    RULE_ENGINE: "规则引擎",
    ML_ENGINE: "模型引擎",
    MANUAL: "人工录入",
  };
  return labels[normalized] ?? (value?.trim() || "-");
}

export function formatProtocolTypeLabel(value: string | null | undefined): string {
  const normalized = normalizeToken(value);
  const labels: Record<string, string> = {
    LOW_ACTIVITY: "低强度方案",
    HIGH_ACTIVITY: "高强度方案",
    BREATHING: "呼吸训练",
    RELAXATION: "放松方案",
    RECOVERY: "恢复方案",
  };
  return labels[normalized] ?? (value?.trim() || "-");
}

export function formatBodyZoneLabel(value: string | null | undefined): string {
  const normalized = normalizeToken(value);
  const labels: Record<string, string> = {
    HEAD: "头部",
    CHEST: "胸部",
    LIMB: "四肢",
    BACK: "背部",
    ABDOMEN: "腹部",
  };
  return labels[normalized] ?? (value?.trim() || "-");
}

export function formatParseStatusLabel(value: string | null | undefined): string {
  const normalized = normalizeToken(value);
  const labels: Record<string, string> = {
    PARSED: "已解析",
    PENDING: "待处理",
    FAILED: "失败",
  };
  return labels[normalized] ?? (value?.trim() || "-");
}

export function formatReportTypeLabel(value: string | null | undefined): string {
  const normalized = normalizeToken(value);
  const labels: Record<string, string> = {
    LAB_REPORT: "化验报告",
    BLOOD_TEST: "血液报告",
    ECG: "心电图",
    PDF: "PDF 报告",
  };
  return labels[normalized] ?? (value?.trim() || "-");
}

export function formatAuditActionLabel(value: string | null | undefined): string {
  const normalized = normalizeToken(value);
  const labels: Record<string, string> = {
    REGISTER: "注册",
    ACTIVATE: "激活",
    REGISTER_ACTIVATE: "注册并激活",
    UPSERT: "写入或更新",
    READ: "读取",
    COMPLETE: "完成",
    ENQUEUE: "入队",
    INSERT: "写入",
    LOGIN: "登录",
    SYNC: "同步",
    UPDATE: "更新",
    ARCHIVE: "归档",
  };
  return labels[normalized] ?? (value?.trim() || "-");
}

export function formatResourceTypeLabel(value: string | null | undefined): string {
  const normalized = normalizeToken(value).replace(/-/g, "_");
  const labels: Record<string, string> = {
    MODEL_REGISTRY: "模型注册表",
    INTERVENTION_TASKS: "干预任务",
    INTERVENTION_EXECUTIONS: "干预执行",
    AUDIT_EVENTS: "审计事件",
    NIGHTLY_REPORTS: "夜间报告",
    INFERENCE_JOBS: "推理作业",
    SLEEP_SESSIONS: "睡眠记录",
    SLEEP_WINDOWS: "睡眠窗口",
    MEDICAL_REPORTS: "医疗报告",
    MEDICAL_METRICS: "医疗指标",
    MEDICATION_ANALYSIS_RECORDS: "药物识别记录",
    FOOD_ANALYSIS_RECORDS: "饮食分析记录",
    AUTH: "认证",
    PROFILE: "用户资料",
    SYNC: "同步记录",
    RECOMMENDATION_TRACES: "建议轨迹",
    RECOMMENDATION_MODEL_PROFILES: "建议策略配置",
  };
  return labels[normalized] ?? (value?.trim() || "-");
}

export function formatTimelineTypeLabel(value: string): string {
  const normalized = normalizeToken(value).replace(/-/g, "_");
  const labels: Record<string, string> = {
    SLEEP_SESSION: "睡眠记录",
    INFERENCE_JOB: "推理作业",
    NIGHTLY_REPORT: "夜间报告",
    INTERVENTION_TASK: "干预任务",
    INTERVENTION_EXECUTION: "干预执行",
    MEDICAL_REPORT: "医疗报告",
    MEDICATION_ANALYSIS: "药物识别",
    FOOD_ANALYSIS: "饮食分析",
    AUDIT_EVENT: "审计事件",
  };
  return labels[normalized] ?? value;
}

export function formatBooleanLabel(value: boolean, trueLabel = "是", falseLabel = "否"): string {
  return value ? trueLabel : falseLabel;
}
