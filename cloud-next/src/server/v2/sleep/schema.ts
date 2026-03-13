import { z } from "zod";

export const SleepUploadSchema = z.object({
  userId: z.string().min(1),
  sleepRecordId: z.string().min(1),
  date: z.number().int(),
  totalSleepMinutes: z.number().int().nonnegative(),
});

export const SleepAnalyzeSchema = z.object({
  userId: z.string().min(1),
  sleepRecordId: z.string().min(1),
});
