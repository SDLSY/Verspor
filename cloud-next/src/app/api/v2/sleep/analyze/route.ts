import { NextResponse } from "next/server";
import { SleepAnalyzeSchema } from "@/server/v2/sleep/schema";
import { fail, makeTraceId, ok } from "@/server/v2/common/types";

export async function POST(req: Request) {
  const traceId = makeTraceId();
  const body = await req.json().catch(() => ({}));
  const parsed = SleepAnalyzeSchema.safeParse(body);
  if (!parsed.success) {
    return NextResponse.json(fail(traceId, 400, "invalid payload"), { status: 400 });
  }

  return NextResponse.json(
    ok(traceId, "ok", {
      sleepRecordId: parsed.data.sleepRecordId,
      sleepStages5: ["N2", "N3", "REM"],
      anomalyScore: 24,
      modelVersion: "v2-baseline",
      featureSchemaVersion: "v2.0.0",
    })
  );
}
