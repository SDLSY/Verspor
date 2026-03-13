"use server";

import type { Route } from "next";
import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import { upsertAdminInterventionTask } from "@/lib/admin-mutations";

function parseTimestamp(value: FormDataEntryValue | null): number | null {
  if (typeof value !== "string" || !value.trim()) {
    return null;
  }
  const parsed = Date.parse(value);
  return Number.isFinite(parsed) ? parsed : null;
}

function readText(value: FormDataEntryValue | null): string {
  return typeof value === "string" ? value.trim() : "";
}

function readNumber(value: FormDataEntryValue | null): number | undefined {
  if (typeof value !== "string" || !value.trim()) {
    return undefined;
  }
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : undefined;
}

export async function saveInterventionTask(userId: string, formData: FormData) {
  try {
    await upsertAdminInterventionTask(userId, {
      taskId: readText(formData.get("taskId")) || undefined,
      date: parseTimestamp(formData.get("taskDate")),
      sourceType: readText(formData.get("sourceType")) || undefined,
      triggerReason: readText(formData.get("triggerReason")) || undefined,
      bodyZone: readText(formData.get("bodyZone")) || undefined,
      protocolType: readText(formData.get("protocolType")) || undefined,
      durationSec: readNumber(formData.get("durationSec")),
      plannedAt: parseTimestamp(formData.get("plannedAt")),
      status: readText(formData.get("status")) || undefined,
    });
  } catch (error) {
    const message = error instanceof Error ? error.message : "干预任务保存失败";
    redirect(`/patients/${userId}?error=${encodeURIComponent(message)}#interventions` as Route);
  }

  revalidatePath(`/patients/${userId}`);
  redirect(`/patients/${userId}?saved=1#interventions` as Route);
}
