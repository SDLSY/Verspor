import { NextResponse } from "next/server";
import { SleepUploadSchema } from "@/server/v2/sleep/schema";
import { fail, makeTraceId, ok } from "@/server/v2/common/types";

export async function POST(req: Request) {
  const traceId = makeTraceId();
  const body = await req.json().catch(() => ({}));
  const parsed = SleepUploadSchema.safeParse(body);
  if (!parsed.success) {
    return NextResponse.json(fail(traceId, 400, "invalid payload"), { status: 400 });
  }
  return NextResponse.json(ok(traceId, "uploaded", { sleepRecordId: parsed.data.sleepRecordId }));
}
