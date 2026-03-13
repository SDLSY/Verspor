"use server";

import type { Route } from "next";
import { headers } from "next/headers";
import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import { triggerAdminWorker } from "@/lib/admin-mutations";

function readLimit(formData: FormData): number {
  const raw = formData.get("limit");
  if (typeof raw !== "string" || !raw.trim()) {
    return 20;
  }
  const parsed = Number(raw);
  return Number.isFinite(parsed) ? parsed : 20;
}

async function resolveOrigin(): Promise<string> {
  const headerList = await headers();
  const host = headerList.get("x-forwarded-host") ?? headerList.get("host");
  const protocol = headerList.get("x-forwarded-proto") ?? "http";
  if (!host) {
    throw new Error("缺少主机头信息");
  }
  return `${protocol}://${host}`;
}

export async function triggerWorkerAction(formData: FormData) {
  try {
    const origin = await resolveOrigin();
    const result = await triggerAdminWorker(origin, readLimit(formData));
    const processed = typeof result.processed === "number" ? result.processed : 0;
    revalidatePath("/system/jobs");
    redirect(`/system/jobs?ran=${processed}` as Route);
  } catch (error) {
    const message = error instanceof Error ? error.message : "手动作业触发失败";
    redirect(`/system/jobs?error=${encodeURIComponent(message)}` as Route);
  }
}
