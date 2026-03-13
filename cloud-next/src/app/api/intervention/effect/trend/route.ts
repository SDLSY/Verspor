import { NextResponse } from "next/server";
import { requireAuth } from "@/lib/auth";
import { writeAuditEvent } from "@/lib/audit";
import { fail, ok } from "@/lib/http";
import { createServiceClient } from "@/lib/supabase";

type ExecutionRow = {
  ended_at: string;
  effect_score: number | null;
  before_stress: number | null;
  after_stress: number | null;
};

type Bucket = {
  date: number;
  effectSum: number;
  stressDropSum: number;
  count: number;
};

export async function GET(req: Request) {
  const auth = await requireAuth(req);
  if (!auth.authenticated) {
    return auth.response;
  }

  const url = new URL(req.url);
  const days = Math.max(1, Math.min(90, Number(url.searchParams.get("days") ?? 7)));
  const startAt = new Date(Date.now() - (days - 1) * 24 * 60 * 60 * 1000).toISOString();

  try {
    const { data, error } = await auth.client
      .from("intervention_executions")
      .select("ended_at,effect_score,before_stress,after_stress")
      .eq("user_id", auth.user.id)
      .gte("ended_at", startAt)
      .order("ended_at", { ascending: true })
      .returns<ExecutionRow[]>();
    if (error) {
      return NextResponse.json(fail(500, error.message), { status: 500 });
    }

    const buckets = new Map<string, Bucket>();
    (data ?? []).forEach((row) => {
      const date = new Date(row.ended_at);
      const dayKey = date.toISOString().slice(0, 10);
      const dayStart = new Date(dayKey).getTime();
      const bucket = buckets.get(dayKey) ?? {
        date: dayStart,
        effectSum: 0,
        stressDropSum: 0,
        count: 0,
      };
      bucket.count += 1;
      bucket.effectSum += Number(row.effect_score ?? 0);
      bucket.stressDropSum += Number(row.before_stress ?? 0) - Number(row.after_stress ?? 0);
      buckets.set(dayKey, bucket);
    });

    const trend = [...buckets.values()]
      .sort((a, b) => a.date - b.date)
      .map((bucket) => ({
        date: bucket.date,
        avgEffectScore: bucket.count > 0 ? bucket.effectSum / bucket.count : 0,
        avgStressDrop: bucket.count > 0 ? bucket.stressDropSum / bucket.count : 0,
        executionCount: bucket.count,
      }));

    const serviceClient = createServiceClient();
    await writeAuditEvent(serviceClient, {
      userId: auth.user.id,
      actor: "api:intervention/effect/trend",
      action: "read",
      resourceType: "intervention_executions",
      resourceId: auth.user.id,
      metadata: { days },
    }).catch(() => null);

    return NextResponse.json(ok("ok", trend));
  } catch (error) {
    const message = error instanceof Error ? error.message : "query trend failed";
    return NextResponse.json(fail(500, message), { status: 500 });
  }
}
