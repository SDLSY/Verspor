import { adminBadRequest, withAdminRoute } from "@/lib/admin-route";
import {
  listRecommendationModelProfiles,
  upsertRecommendationModelProfile,
} from "@/lib/recommendation-model/admin";
import { parseJsonBody } from "@/lib/http";
import type { RecommendationModelProfileStatus } from "@/lib/recommendation-model/srm-v2-config";

function toStatus(value: string | null): RecommendationModelProfileStatus | undefined {
  if (value === "draft" || value === "active" || value === "archived") {
    return value;
  }
  return undefined;
}

export async function GET(req: Request) {
  const searchParams = new URL(req.url).searchParams;
  return withAdminRoute(async () =>
    listRecommendationModelProfiles({
      modelCode: searchParams.get("modelCode")?.trim() || undefined,
      profileCode: searchParams.get("profileCode")?.trim() || undefined,
      status: toStatus(searchParams.get("status")),
    })
  );
}

export async function POST(req: Request) {
  let body: unknown;
  try {
    body = await req.json();
  } catch {
    return adminBadRequest("invalid json payload");
  }

  const parsed = parseJsonBody<{
    modelCode?: string;
    profileCode?: string;
    status?: RecommendationModelProfileStatus;
    description?: string | null;
    thresholds?: Record<string, unknown>;
    weights?: Record<string, unknown>;
    gateRules?: Record<string, unknown>;
    modePriorities?: Record<string, unknown>;
    confidenceFormula?: Record<string, unknown>;
  }>(body);

  if (!parsed.modelCode?.trim() || !parsed.profileCode?.trim()) {
    return adminBadRequest("modelCode and profileCode are required");
  }

  const modelCode = parsed.modelCode.trim();
  const profileCode = parsed.profileCode.trim();

  return withAdminRoute(async () =>
    upsertRecommendationModelProfile({
      modelCode,
      profileCode,
      status: parsed.status,
      description: parsed.description,
      thresholds: parsed.thresholds,
      weights: parsed.weights,
      gateRules: parsed.gateRules,
      modePriorities: parsed.modePriorities,
      confidenceFormula: parsed.confidenceFormula,
    })
  );
}
