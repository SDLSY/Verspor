import { NextResponse } from "next/server";
import { NightlyJobSchema } from "@/server/v2/job/schema";
import { fail, makeTraceId, ok } from "@/server/v2/common/types";

export async function POST(req: Request) {
  const traceId = makeTraceId();
  const body = await req.json().catch(() => ({}));
  const parsed = NightlyJobSchema.safeParse(body);
  if (!parsed.success) {
    return NextResponse.json(fail(traceId, 400, "invalid payload"), { status: 400 });
  }
  return NextResponse.json(ok(traceId, "accepted", { jobId: `job_${Date.now()}`, status: "queued" }));
}
