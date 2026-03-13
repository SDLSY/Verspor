import { NextResponse } from "next/server";
import { fail, makeTraceId, ok } from "@/server/v2/common/types";

export async function POST(req: Request) {
  const traceId = makeTraceId();
  const body = await req.json().catch(() => ({} as Record<string, unknown>));
  const version = String(body.modelVersion ?? "").trim();
  if (!version) {
    return NextResponse.json(fail(traceId, 400, "modelVersion required"), { status: 400 });
  }
  return NextResponse.json(ok(traceId, "activated", { modelVersion: version }));
}
