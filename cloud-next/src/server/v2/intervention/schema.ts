import { z } from "zod";

export const InterventionTaskSchema = z.object({
  taskId: z.string().min(1),
  sourceType: z.enum(["AI_COACH", "MEDICAL_REPORT", "RULE_ENGINE"]),
  bodyZone: z.string().min(1),
  protocolType: z.string().min(1),
  durationSec: z.number().int().positive(),
});

export const InterventionExecutionSchema = z.object({
  executionId: z.string().min(1),
  taskId: z.string().min(1),
  elapsedSec: z.number().int().nonnegative(),
  effectScore: z.number().nullable().optional(),
});
