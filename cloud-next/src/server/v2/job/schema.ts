import { z } from "zod";

export const NightlyJobSchema = z.object({
  userId: z.string().min(1),
  sleepRecordId: z.string().min(1),
  idempotencyKey: z.string().min(1),
});
