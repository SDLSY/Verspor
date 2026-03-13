"use server";

import type { Route } from "next";
import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import {
  listRecommendationModelProfiles,
  rollbackRecommendationModelProfileVersion,
  upsertRecommendationModelProfile,
} from "@/lib/recommendation-model/admin";

function readText(value: FormDataEntryValue | null): string {
  return typeof value === "string" ? value.trim() : "";
}

function readJsonRecord(value: FormDataEntryValue | null, field: string): Record<string, unknown> {
  const raw = typeof value === "string" ? value.trim() : "";
  if (!raw) {
    return {};
  }
  try {
    const parsed = JSON.parse(raw);
    if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
      throw new Error(`${field} 必须是 JSON 对象`);
    }
    return parsed as Record<string, unknown>;
  } catch (error) {
    if (error instanceof Error) {
      throw error;
    }
    throw new Error(`${field} 解析失败`);
  }
}

export async function saveRecommendationProfileAction(formData: FormData) {
  try {
    await upsertRecommendationModelProfile({
      modelCode: readText(formData.get("modelCode")) || "SRM_V2",
      profileCode: readText(formData.get("profileCode")),
      status:
        (readText(formData.get("status")) as "draft" | "active" | "archived") || "draft",
      description: readText(formData.get("description")) || null,
      thresholds: readJsonRecord(formData.get("thresholdsJson"), "thresholdsJson"),
      weights: readJsonRecord(formData.get("weightsJson"), "weightsJson"),
      gateRules: readJsonRecord(formData.get("gateRulesJson"), "gateRulesJson"),
      modePriorities: readJsonRecord(formData.get("modePrioritiesJson"), "modePrioritiesJson"),
      confidenceFormula: readJsonRecord(
        formData.get("confidenceFormulaJson"),
        "confidenceFormulaJson"
      ),
    }, {
      action: "recommendation_profile_saved",
    });
  } catch (error) {
    const message = error instanceof Error ? error.message : "保存策略配置失败";
    redirect(`/recommendations/profiles?error=${encodeURIComponent(message)}` as Route);
  }

  revalidatePath("/recommendations/profiles");
  redirect("/recommendations/profiles?saved=1" as Route);
}

export async function cloneRecommendationProfileAction(formData: FormData) {
  try {
    const modelCode = readText(formData.get("modelCode")) || "SRM_V2";
    const sourceProfileCode = readText(formData.get("sourceProfileCode"));
    const newProfileCode = readText(formData.get("newProfileCode"));
    const profiles = await listRecommendationModelProfiles({
      modelCode,
      profileCode: sourceProfileCode,
    });
    const source = profiles[0];
    if (!source) {
      throw new Error("找不到要复制的 profile");
    }

    await upsertRecommendationModelProfile({
      modelCode,
      profileCode: newProfileCode,
      status: "draft",
      description: source.description ? `${source.description}（复制）` : "复制出的草稿配置",
      thresholds: source.thresholds,
      weights: source.weights,
      gateRules: source.gateRules,
      modePriorities: source.modePriorities,
      confidenceFormula: source.confidenceFormula,
    }, {
      action: "recommendation_profile_cloned",
      note: `由 ${source.profileCode} 复制`,
      sourceProfileCode,
    });
  } catch (error) {
    const message = error instanceof Error ? error.message : "复制策略配置失败";
    redirect(`/recommendations/profiles?error=${encodeURIComponent(message)}` as Route);
  }

  revalidatePath("/recommendations/profiles");
  redirect("/recommendations/profiles?cloned=1" as Route);
}

export async function rollbackRecommendationProfileVersionAction(formData: FormData) {
  try {
    const versionId = readText(formData.get("versionId"));
    if (!versionId) {
      throw new Error("缺少版本标识");
    }
    const rolledBack = await rollbackRecommendationModelProfileVersion(versionId);
    revalidatePath("/recommendations/profiles");
    redirect(
      `/recommendations/profiles?rolledBack=1&profileCode=${encodeURIComponent(rolledBack.profileCode)}` as Route
    );
  } catch (error) {
    const message = error instanceof Error ? error.message : "回滚版本失败";
    redirect(`/recommendations/profiles?error=${encodeURIComponent(message)}` as Route);
  }
}
