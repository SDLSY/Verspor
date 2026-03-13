import { NextResponse } from "next/server";
import { makeTraceId, ok } from "@/server/v2/common/types";

export async function GET(req: Request) {
  const traceId = makeTraceId();
  const url = new URL(req.url);
  const days = Number(url.searchParams.get("days") ?? 7);
  return NextResponse.json(
    ok(traceId, "ok", [
      {
        date: Date.now(),
        avgEffectScore: 80,
        executionCount: Math.max(1, days > 14 ? 5 : 3),
      },
    ])
  );
}
