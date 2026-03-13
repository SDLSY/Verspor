import { NextResponse } from "next/server";
import { requireAuth } from "@/lib/auth";
import { writeAuditEvent } from "@/lib/audit";
import { fail, ok, parseJsonBody } from "@/lib/http";
import { toIsoTime, toInt } from "@/lib/inference";
import { createServiceClient } from "@/lib/supabase";

type BaselineSummaryBody = {
  completedScaleCodes?: string[];
  completedCount?: number;
  completedAt?: number;
  freshnessUntil?: number;
  source?: string;
};

function sanitizeScaleCodes(value: string[] | undefined): string[] {
  if (!Array.isArray(value)) {
    return [];
  }
  return Array.from(
    new Set(
      value
        .map((item) => (typeof item === "string" ? item.trim().toUpperCase() : ""))
        .filter(Boolean)
        .slice(0, 12)
    )
  );
}

export async function POST(req: Request) {
  const auth = await requireAuth(req);
  if (!auth.authenticated) {
    return auth.response;
  }

  const body = parseJsonBody<BaselineSummaryBody>(await req.json().catch(() => ({})));
  const completedScaleCodes = sanitizeScaleCodes(body.completedScaleCodes);
  const completedCount = toInt(body.completedCount) ?? completedScaleCodes.length;
  const freshnessUntil = toIsoTime(body.freshnessUntil);
  if (!freshnessUntil) {
    return NextResponse.json(fail(400, "freshnessUntil is required"), { status: 400 });
  }

  try {
    const { error } = await auth.client.from("assessment_baseline_snapshots").upsert(
      {
        user_id: auth.user.id,
        completed_scale_codes_json: completedScaleCodes,
        completed_count: completedCount,
        completed_at: toIsoTime(body.completedAt) ?? new Date().toISOString(),
        freshness_until: freshnessUntil,
        source: (body.source ?? "ANDROID").trim().toUpperCase(),
        updated_at: new Date().toISOString(),
      },
      { onConflict: "user_id" }
    );

    if (error) {
      return NextResponse.json(fail(500, error.message), { status: 500 });
    }

    const serviceClient = createServiceClient();
    await writeAuditEvent(serviceClient, {
      userId: auth.user.id,
      actor: "api:assessment/baseline-summary/upsert",
      action: "upsert",
      resourceType: "assessment_baseline_snapshots",
      resourceId: auth.user.id,
      metadata: {
        completedCount,
        completedScaleCodes,
      },
    }).catch(() => null);

    return NextResponse.json(ok("ok", { completedCount, completedScaleCodes }));
  } catch (error) {
    const message = error instanceof Error ? error.message : "baseline summary upsert failed";
    return NextResponse.json(fail(500, message), { status: 500 });
  }
}
