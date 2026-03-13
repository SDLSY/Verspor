import { NextResponse } from "next/server";
import { makeTraceId, ok } from "@/server/v2/common/types";

export async function GET(_: Request, ctx: { params: Promise<{ jobId: string }> }) {
  const traceId = makeTraceId();
  const params = await ctx.params;
  return NextResponse.json(
    ok(traceId, "ok", {
      jobId: params.jobId,
      status: "succeeded",
      modelVersion: "v2-baseline",
      featureSchemaVersion: "v2.0.0",
    })
  );
}
