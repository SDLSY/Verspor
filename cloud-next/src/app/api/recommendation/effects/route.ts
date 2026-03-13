import { NextResponse } from "next/server";
import { requireAuth } from "@/lib/auth";
import { fail, ok } from "@/lib/http";
import { getRecommendationEffectSummary } from "@/lib/recommendation-effects";
import { createServiceClient } from "@/lib/supabase";

function toNumber(value: string | null): number | undefined {
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
    const data = await getRecommendationEffectSummary(createServiceClient(), {
      userId: auth.user.id,
      days: toNumber(searchParams.get("days")),
      recommendationMode: searchParams.get("recommendationMode")?.trim() || undefined,
      profileCode: searchParams.get("profileCode")?.trim() || undefined,
    });

    return NextResponse.json(ok("ok", data));
  } catch (error) {
    const message = error instanceof Error ? error.message : "query effect summary failed";
    return NextResponse.json(fail(500, message), { status: 500 });
  }
}
