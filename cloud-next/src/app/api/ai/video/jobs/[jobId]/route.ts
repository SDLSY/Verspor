import { NextResponse } from "next/server";
import { requireAuth } from "@/lib/auth";
import { createTraceId, fail, ok } from "@/lib/http";
import { normalizeVideoJobResponse } from "@/lib/ai/multimodal";

type Params = {
  params: Promise<{ jobId: string }>;
};

export async function GET(req: Request, { params }: Params) {
  const traceId = createTraceId();
  const auth = await requireAuth(req);
  if (!auth.authenticated) {
    return auth.response;
  }

  const { jobId } = await params;
  if (!jobId.trim()) {
    return NextResponse.json(fail(400, "jobId is required", traceId), { status: 400 });
  }

  return NextResponse.json(ok("ok", traceId, normalizeVideoJobResponse(jobId, traceId)));
}
