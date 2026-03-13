import { NextResponse } from "next/server";
import { requireAuth } from "@/lib/auth";
import { createTraceId, fail, ok, parseJsonBody } from "@/lib/http";
import {
  ReportUnderstandingRequestSchema,
  generateReportUnderstanding,
} from "@/lib/ai/report-understanding";

export async function POST(req: Request) {
  const traceId = createTraceId();
  const auth = await requireAuth(req);
  if (!auth.authenticated) {
    return auth.response;
  }

  let json: unknown;
  try {
    json = await req.json();
  } catch {
    return NextResponse.json(fail(400, "invalid json payload", traceId), { status: 400 });
  }

  const parsed = ReportUnderstandingRequestSchema.safeParse(parseJsonBody(json));
  if (!parsed.success) {
    return NextResponse.json(fail(400, "invalid report understanding payload", traceId), {
      status: 400,
    });
  }

  const result = await generateReportUnderstanding(parsed.data, traceId);
  if (!result) {
    return NextResponse.json(fail(503, "report understanding unavailable", traceId), {
      status: 503,
    });
  }

  return NextResponse.json(
    ok("ok", traceId, {
      ...result.payload,
      metadata: {
        providerId: result.providerId,
        fallbackUsed: result.fallbackUsed,
      },
    })
  );
}
