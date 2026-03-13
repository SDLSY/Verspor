import { NextResponse } from "next/server";
import { requireAuth } from "@/lib/auth";
import { writeAuditEvent } from "@/lib/audit";
import { fail, ok } from "@/lib/http";
import { createServiceClient } from "@/lib/supabase";

export async function GET(
  req: Request,
  { params }: { params: Promise<{ jobId: string }> }
) {
  const auth = await requireAuth(req);
  if (!auth.authenticated) {
    return auth.response;
  }

  const { jobId } = await params;

  try {
    const { data, error } = await auth.client
      .from("inference_jobs")
      .select("id,status,model_version,finished_at")
      .eq("user_id", auth.user.id)
      .eq("id", jobId)
      .maybeSingle<{
        id: string;
        status: string;
        model_version: string | null;
        finished_at: string | null;
      }>();

    if (error) {
      return NextResponse.json(fail(500, error.message), { status: 500 });
    }

    if (!data) {
      return NextResponse.json(fail(404, "job not found"), { status: 404 });
    }

    const serviceClient = createServiceClient();
    await writeAuditEvent(serviceClient, {
      userId: auth.user.id,
      actor: "api:v1/inference/nightly/[jobId]",
      action: "read",
      resourceType: "inference_jobs",
      resourceId: data.id,
    }).catch(() => null);

    return NextResponse.json(
      ok("ok", {
        jobId: data.id,
        status: data.status,
        modelVersion: data.model_version ?? "mmt-v1",
        finishedAt: data.finished_at,
      })
    );
  } catch (error) {
    const message = error instanceof Error ? error.message : "query failed";
    return NextResponse.json(fail(500, message), { status: 500 });
  }
}
