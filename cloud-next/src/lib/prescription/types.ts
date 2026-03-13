import { z } from "zod";

export const TimingSlotSchema = z.enum([
  "MORNING",
  "AFTERNOON",
  "EVENING",
  "BEFORE_SLEEP",
  "FLEXIBLE",
]);

export const RiskLevelSchema = z.enum(["LOW", "MEDIUM", "HIGH"]);
export const PersonalizationLevelSchema = z.enum(["PREVIEW", "FULL"]);
export const PersonalizationMissingInputSchema = z.enum([
  "DEVICE_DATA",
  "BASELINE_ASSESSMENT",
  "DOCTOR_INQUIRY",
]);

export const PrescriptionCatalogItemSchema = z.object({
  protocolCode: z.string().trim().min(1).max(80),
  displayName: z.string().trim().min(1).max(120),
  interventionType: z.string().trim().min(1).max(64),
  description: z.string().trim().min(1).max(500),
});

export const DailyPrescriptionRequestSchema = z.object({
  triggerType: z.string().trim().min(1).max(64),
  domainScores: z.record(z.string(), z.number().int().min(0).max(100)).default({}),
  evidenceFacts: z
    .record(z.string(), z.array(z.string().trim().min(1).max(240)).max(12))
    .default({}),
  redFlags: z.array(z.string().trim().min(1).max(120)).max(12).default([]),
  personalizationLevel: PersonalizationLevelSchema.default("PREVIEW"),
  missingInputs: z.array(PersonalizationMissingInputSchema).max(3).default([]),
  ragContext: z.string().max(8000).default(""),
  catalog: z.array(PrescriptionCatalogItemSchema).min(1),
});

export const DailyPrescriptionResponseSchema = z.object({
  primaryGoal: z.string().trim().min(1).max(120),
  riskLevel: RiskLevelSchema,
  targetDomains: z.array(z.string().trim().min(1).max(64)).max(7).default([]),
  primaryInterventionType: z.string().trim().min(1).max(80),
  secondaryInterventionType: z.string().trim().max(80).default(""),
  lifestyleTaskCodes: z.array(z.string().trim().min(1).max(80)).max(6).default([]),
  timing: TimingSlotSchema.default("FLEXIBLE"),
  durationSec: z.number().int().min(60).max(7200).default(600),
  rationale: z.string().trim().min(1).max(800),
  evidence: z.array(z.string().trim().min(1).max(240)).max(8).default([]),
  contraindications: z.array(z.string().trim().min(1).max(160)).max(6).default([]),
  followupMetric: z.string().trim().min(1).max(120),
  personalizationLevel: PersonalizationLevelSchema.default("PREVIEW"),
  missingInputs: z.array(PersonalizationMissingInputSchema).max(3).default([]),
  isPreview: z.boolean().default(true),
});

export type PrescriptionCatalogItem = z.infer<typeof PrescriptionCatalogItemSchema>;
export type DailyPrescriptionRequest = z.infer<typeof DailyPrescriptionRequestSchema>;
export type DailyPrescriptionResponse = z.infer<typeof DailyPrescriptionResponseSchema>;
export type PrescriptionRiskLevel = z.infer<typeof RiskLevelSchema>;
export type PrescriptionTimingSlot = z.infer<typeof TimingSlotSchema>;
export type PrescriptionPersonalizationLevel = z.infer<typeof PersonalizationLevelSchema>;
export type PrescriptionPersonalizationMissingInput = z.infer<typeof PersonalizationMissingInputSchema>;

export type PrescriptionServerContext = {
  serverEvidenceFacts: Record<string, string[]>;
  serverRedFlags: string[];
  latestRecoveryScore: number | null;
  latestSleepQuality: string | null;
  latestMedicalRiskLevel: string | null;
  latestMedicalMetricLabels: string[];
  recentInterventionSummary: string[];
  latestSleepSummary: string[];
  breathingFatigue: boolean;
  preferredProtocolCodes: string[];
  downweightedProtocolCodes: string[];
  averageEffectScore: number | null;
  averageStressDrop: number | null;
  personalizationLevel: PrescriptionPersonalizationLevel;
  missingInputs: PrescriptionPersonalizationMissingInput[];
};

export type PrescriptionProviderInput = {
  request: DailyPrescriptionRequest;
  ragContext: string;
  traceId: string;
};

export type PrescriptionProviderAttempt = {
  providerId: string;
  success: boolean;
  latencyMs: number;
  failureCode: string | null;
  recommendationId?: string | null;
};
