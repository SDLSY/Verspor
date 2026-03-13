import { NextResponse } from "next/server";
import { requireAuth } from "@/lib/auth";
import { writeAuditEvent } from "@/lib/audit";
import { getLatestAnalysis } from "@/lib/inference";
import { fail, ok } from "@/lib/http";
import { createServiceClient } from "@/lib/supabase";

export async function GET(
  req: Request,
  { params }: { params: Promise<{ sleepRecordId: string }> }
) {
  const auth = await requireAuth(req);
  if (!auth.authenticated) {
    return auth.response;
  }

  const { sleepRecordId } = await params;

  try {
    const analysis = await getLatestAnalysis(auth.client, auth.user.id, sleepRecordId);
    if (!analysis) {
      return NextResponse.json(fail(404, "report not ready"), { status: 404 });
    }

    const serviceClient = createServiceClient();
    await writeAuditEvent(serviceClient, {
      userId: auth.user.id,
      actor: "api:v1/reports/nightly",
      action: "read",
      resourceType: "nightly_reports",
      resourceId: sleepRecordId,
      metadata: {
        modelVersion: analysis.modelVersion,
      },
    });

    return NextResponse.json(
      ok("ok", {
        sleepRecordId,
        sleepStages5: analysis.sleepStages5,
        sleepStages: analysis.sleepStages,
        anomalyScore: analysis.anomalyScore,
        recoveryScore: analysis.recoveryScore,
        factors: analysis.factors,
        insights: analysis.insights,
        modelVersion: analysis.modelVersion,
      })
    );
  } catch (error) {
    const message = error instanceof Error ? error.message : "query failed";
    return NextResponse.json(fail(500, message), { status: 500 });
  }
}
