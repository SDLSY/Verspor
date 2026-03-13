import { NextResponse } from "next/server";
import { requireAuth } from "@/lib/auth";
import { fail, ok, parseJsonBody, createTraceId } from "@/lib/http";
import { generateDailyPrescription } from "@/lib/prescription/engine";
import { DailyPrescriptionRequestSchema } from "@/lib/prescription/types";
import { createServiceClient } from "@/lib/supabase";

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

  const parsed = DailyPrescriptionRequestSchema.safeParse(parseJsonBody(json));
  if (!parsed.success) {
    return NextResponse.json(fail(400, "invalid daily prescription payload", traceId), {
      status: 400,
    });
  }

  try {
    const result = await generateDailyPrescription({
      authClient: auth.client,
      serviceClient: createServiceClient(),
      userId: auth.user.id,
      request: parsed.data,
      traceId,
    });

    return NextResponse.json(
      ok("ok", traceId, {
        ...result.payload,
        explanation: result.explanation,
        metadata: {
          providerId: result.providerId,
          snapshotId: result.snapshotId,
          recommendationId: result.recommendationId,
          isFallback: result.isFallback,
          modelVersion: result.scientificModel.modelVersion,
          modelProfile: result.scientificModel.profileCode,
          configSource: result.scientificModel.configSource,
        },
      })
    );
  } catch {
    return NextResponse.json(
      fail(500, "failed to generate daily prescription", traceId),
      { status: 500 }
    );
  }
}
