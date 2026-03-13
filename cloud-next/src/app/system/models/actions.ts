"use server";

import type { Route } from "next";
import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import { activateAdminModel, registerAdminModel } from "@/lib/admin-mutations";

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

function readBoolean(value: FormDataEntryValue | null): boolean {
  return value === "on" || value === "true" || value === "1";
}

export async function registerModelAction(formData: FormData) {
  try {
    await registerAdminModel({
      modelKind: readText(formData.get("modelKind")) || undefined,
      version: readText(formData.get("version")),
      artifactPath: readText(formData.get("artifactPath")) || undefined,
      featureSchemaVersion: readText(formData.get("featureSchemaVersion")) || undefined,
      runtimeType: readText(formData.get("runtimeType")) === "http" ? "http" : "fallback",
      inferenceEndpoint: readText(formData.get("inferenceEndpoint")) || null,
      confidenceThreshold: readNumber(formData.get("confidenceThreshold")),
      fallbackEnabled: readBoolean(formData.get("fallbackEnabled")),
      inferenceTimeoutMs: readNumber(formData.get("inferenceTimeoutMs")),
      activate: readBoolean(formData.get("activate")),
    });
  } catch (error) {
    const message = error instanceof Error ? error.message : "模型保存失败";
    redirect(`/system/models?error=${encodeURIComponent(message)}` as Route);
  }

  revalidatePath("/system/models");
  redirect("/system/models?saved=1" as Route);
}

export async function activateModelAction(formData: FormData) {
  try {
    await activateAdminModel({
      modelKind: readText(formData.get("modelKind")) || undefined,
      version: readText(formData.get("version")),
    });
  } catch (error) {
    const message = error instanceof Error ? error.message : "模型激活失败";
    redirect(`/system/models?error=${encodeURIComponent(message)}` as Route);
  }

  revalidatePath("/system/models");
  redirect("/system/models?activated=1" as Route);
}
