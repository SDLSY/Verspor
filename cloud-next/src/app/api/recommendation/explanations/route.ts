import { NextResponse } from "next/server";
import { requireAuth } from "@/lib/auth";
import { fail, ok } from "@/lib/http";
import { listRecommendationExplanationPanels } from "@/lib/recommendation-explanations";
import { createServiceClient } from "@/lib/supabase";
import type { ScientificTraceType } from "@/lib/recommendation-model/scientific-model";

function toTraceType(value: string | null): ScientificTraceType | undefined {
  if (
    value === "DAILY_PRESCRIPTION" ||
    value === "PERIOD_SUMMARY" ||
    value === "DOCTOR_TURN"
  ) {
    return value;
  }
  return undefined;
}

function toLimit(value: string | null): number | undefined {
  if (!value) {
    return undefined;
  }
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : undefined;
}

export async function GET(req: Request) {
  const auth = await requireAuth(req);
  if (!auth.authenticated) {
    return auth.response;
  }

  try {
    const searchParams = new URL(req.url).searchParams;
    const items = await listRecommendationExplanationPanels(createServiceClient(), {
      userId: auth.user.id,
      traceType: toTraceType(searchParams.get("traceType")),
      traceId: searchParams.get("traceId")?.trim() || undefined,
      limit: toLimit(searchParams.get("limit")),
    });

    return NextResponse.json(
      ok("ok", {
        items,
        total: items.length,
      })
    );
  } catch (error) {
    const message = error instanceof Error ? error.message : "query explanations failed";
    return NextResponse.json(fail(500, message), { status: 500 });
  }
}
