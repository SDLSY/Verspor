import protocols from "@/server/prescription-knowledge/protocols.json";
import redFlags from "@/server/prescription-knowledge/red-flags.json";
import reportMappings from "@/server/prescription-knowledge/report-mappings.json";
import type { DailyPrescriptionRequest, PrescriptionServerContext } from "@/lib/prescription/types";

type ProtocolKnowledgeItem = {
  id: string;
  title: string;
  tags: string[];
  summary: string;
  contraindications: string[];
  priorityProtocols: string[];
  evidenceTemplate: string;
};

type RedFlagKnowledgeItem = {
  id: string;
  tags: string[];
  summary: string;
  requiredTasks: string[];
  contraindications: string[];
};

type ReportMappingKnowledgeItem = {
  metricCode: string;
  tags: string[];
  summary: string;
  preferredProtocols: string[];
  avoidProtocols: string[];
};

function normalizeTag(value: string): string {
  return value.trim().toLowerCase();
}

function buildRequestTags(request: DailyPrescriptionRequest, context: PrescriptionServerContext): Set<string> {
  const tags = new Set<string>();
  tags.add(normalizeTag(request.triggerType));

  Object.entries(request.domainScores).forEach(([key, value]) => {
    const normalized = normalizeTag(key);
    if (value >= 55) {
      tags.add(normalized);
    }
    if (value >= 70) {
      tags.add(`${normalized}_high`);
    }
  });

  request.redFlags.forEach((flag) => tags.add(normalizeTag(flag)));
  context.serverRedFlags.forEach((flag) => tags.add(normalizeTag(flag)));
  context.latestMedicalMetricLabels.forEach((metric) => tags.add(normalizeTag(metric)));

  if ((request.domainScores.recoveryCapacity ?? 100) <= 40 || (context.latestRecoveryScore ?? 100) <= 40) {
    tags.add("recovery");
  }
  if ((request.domainScores.sleepDisturbance ?? 0) >= 65) {
    tags.add("sleep");
    tags.add("isi_high");
  }
  if ((request.domainScores.stressLoad ?? 0) >= 65) {
    tags.add("stress");
  }
  if ((request.domainScores.anxietyRisk ?? 0) >= 65) {
    tags.add("anxiety");
  }
  if (context.breathingFatigue) {
    tags.add("low_adherence");
  }

  return tags;
}

function scoreTags(itemTags: string[], tags: Set<string>): number {
  return itemTags.reduce((score, tag) => score + (tags.has(normalizeTag(tag)) ? 1 : 0), 0);
}

function selectProtocolKnowledge(tags: Set<string>, limit: number): ProtocolKnowledgeItem[] {
  return protocols
    .map((item) => ({ item, score: scoreTags(item.tags, tags) }))
    .filter(({ score }) => score > 0)
    .sort((left, right) => right.score - left.score)
    .slice(0, limit)
    .map(({ item }) => item);
}

function selectRedFlagKnowledge(tags: Set<string>, limit: number): RedFlagKnowledgeItem[] {
  return redFlags
    .map((item) => ({ item, score: scoreTags(item.tags, tags) }))
    .filter(({ score }) => score > 0)
    .sort((left, right) => right.score - left.score)
    .slice(0, limit)
    .map(({ item }) => item);
}

function selectReportKnowledge(tags: Set<string>, limit: number): ReportMappingKnowledgeItem[] {
  return reportMappings
    .map((item) => ({ item, score: scoreTags(item.tags, tags) }))
    .filter(({ score }) => score > 0)
    .sort((left, right) => right.score - left.score)
    .slice(0, limit)
    .map(({ item }) => item);
}

function protocolSection(items: ProtocolKnowledgeItem[]): string {
  if (items.length === 0) {
    return "";
  }
  return [
    "协议知识",
    ...items.map((item) => {
      const lines = [
        `- ${item.title}: ${item.summary}`,
        `  推荐协议: ${item.priorityProtocols.join("、")}`,
        `  禁忌摘要: ${item.contraindications.join("；")}`,
        `  证据模板: ${item.evidenceTemplate}`,
      ];
      return lines.join("\n");
    }),
  ].join("\n");
}

function redFlagSection(items: RedFlagKnowledgeItem[]): string {
  if (items.length === 0) {
    return "";
  }
  return [
    "风险边界",
    ...items.map((item) => {
      const lines = [
        `- ${item.id}: ${item.summary}`,
        `  强制任务: ${item.requiredTasks.join("、")}`,
        `  禁忌摘要: ${item.contraindications.join("；")}`,
      ];
      return lines.join("\n");
    }),
  ].join("\n");
}

function reportSection(items: ReportMappingKnowledgeItem[]): string {
  if (items.length === 0) {
    return "";
  }
  return [
    "医检映射",
    ...items.map((item) => {
      const lines = [
        `- ${item.metricCode}: ${item.summary}`,
        `  优先协议: ${item.preferredProtocols.join("、")}`,
        `  避免协议: ${item.avoidProtocols.length > 0 ? item.avoidProtocols.join("、") : "暂无"}`,
      ];
      return lines.join("\n");
    }),
  ].join("\n");
}

export function buildPrescriptionRagContext(
  request: DailyPrescriptionRequest,
  context: PrescriptionServerContext
): string {
  const tags = buildRequestTags(request, context);
  const protocolItems = selectProtocolKnowledge(tags, 4);
  const flagItems = selectRedFlagKnowledge(tags, 3);
  const reportItems = selectReportKnowledge(tags, 3);

  const segments = [
    request.ragContext.trim(),
    protocolSection(protocolItems),
    redFlagSection(flagItems),
    reportSection(reportItems),
  ].filter((segment) => segment.length > 0);

  return segments.join("\n\n");
}
