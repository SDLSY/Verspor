import { NextResponse } from "next/server";
import { fail, ok } from "@/lib/http";
import { requireInternalToken } from "@/lib/internal";
import { createServiceClient } from "@/lib/supabase";

type CountRow = {
  status: string;
};

type DurationRow = {
  started_at: string | null;
  finished_at: string | null;
};

export async function GET(req: Request) {
  const unauthorized = requireInternalToken(req);
  if (unauthorized) {
    return unauthorized;
  }

  const client = createServiceClient();

  const [countRes, durationRes] = await Promise.all([
    client
      .from("inference_jobs")
      .select("status")
      .limit(5000)
      .returns<CountRow[]>(),
    client
      .from("inference_jobs")
      .select("started_at,finished_at")
      .not("finished_at", "is", null)
      .not("started_at", "is", null)
      .limit(500)
      .returns<DurationRow[]>(),
  ]);

  if (countRes.error) {
    return NextResponse.json(fail(500, countRes.error.message), { status: 500 });
  }
  if (durationRes.error) {
    return NextResponse.json(fail(500, durationRes.error.message), { status: 500 });
  }

  const counts = (countRes.data ?? []).reduce<Record<string, number>>((acc, row) => {
    acc[row.status] = (acc[row.status] ?? 0) + 1;
    return acc;
  }, {});

  const durations = (durationRes.data ?? [])
    .map((row) => {
      if (!row.started_at || !row.finished_at) {
        return NaN;
      }
      const diff = new Date(row.finished_at).getTime() - new Date(row.started_at).getTime();
      return diff;
    })
    .filter((value) => Number.isFinite(value) && value >= 0)
    .sort((a, b) => a - b);

  const p95Index = durations.length > 0 ? Math.floor(durations.length * 0.95) : -1;
  const p95Ms = p95Index >= 0 ? Math.round(durations[p95Index]) : null;
  const avgMs =
    durations.length > 0
      ? Math.round(durations.reduce((acc, value) => acc + value, 0) / durations.length)
      : null;

  return NextResponse.json(
    ok("ok", {
      counts,
      latency: {
        samples: durations.length,
        avgMs,
        p95Ms,
      },
      generatedAt: Date.now(),
    })
  );
}
