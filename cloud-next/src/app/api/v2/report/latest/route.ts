import { NextResponse } from "next/server";
import { makeTraceId, ok } from "@/server/v2/common/types";

export async function GET() {
  const traceId = makeTraceId();
  return NextResponse.json(
    ok(traceId, "ok", {
      reportId: "v2-report-latest",
      riskLevel: "MEDIUM",
      abnormalCount: 0,
    })
  );
}
