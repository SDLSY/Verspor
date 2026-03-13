import { NextResponse } from "next/server";
import { requireAuth } from "@/lib/auth";
import { writeAuditEvent } from "@/lib/audit";
import { fail, ok, parseJsonBody } from "@/lib/http";
import { toIsoTime, toInt } from "@/lib/inference";
import { createServiceClient } from "@/lib/supabase";

type ExecutionBody = {
  executionId?: string;
  taskId?: string;
  startedAt?: number;
  endedAt?: number;
  elapsedSec?: number;
  beforeStress?: number;
  afterStress?: number;
  beforeHr?: number;
  afterHr?: number;
  effectScore?: number;
  completionType?: string;
};

function toNumber(value: unknown): number | null {
  const n = Number(value);
  return Number.isFinite(n) ? n : null;
}

export async function POST(req: Request) {
  const auth = await requireAuth(req);
  if (!auth.authenticated) {
    return auth.response;
  }

  const body = parseJsonBody<ExecutionBody>(await req.json().catch(() => ({})));
  const executionId = (body.executionId ?? "").trim();
  const taskId = (body.taskId ?? "").trim();
  if (!executionId || !taskId) {
    return NextResponse.json(fail(400, "executionId and taskId are required"), { status: 400 });
  }

  try {
    const { error } = await auth.client.from("intervention_executions").upsert(
      {
        user_id: auth.user.id,
        execution_id: executionId,
        task_id: taskId,
        started_at: toIsoTime(body.startedAt) ?? new Date().toISOString(),
        ended_at: toIsoTime(body.endedAt) ?? new Date().toISOString(),
        elapsed_sec: toInt(body.elapsedSec) ?? 0,
        before_stress: toNumber(body.beforeStress),
        after_stress: toNumber(body.afterStress),
        before_hr: toInt(body.beforeHr),
        after_hr: toInt(body.afterHr),
        effect_score: toNumber(body.effectScore),
        completion_type: (body.completionType ?? "FULL").trim().toUpperCase(),
      },
      { onConflict: "user_id,execution_id" }
    );
    if (error) {
      return NextResponse.json(fail(500, error.message), { status: 500 });
    }

    const serviceClient = createServiceClient();
    await writeAuditEvent(serviceClient, {
      userId: auth.user.id,
      actor: "api:intervention/execution/upsert",
      action: "upsert",
      resourceType: "intervention_executions",
      resourceId: executionId,
      metadata: { taskId },
    }).catch(() => null);

    return NextResponse.json(ok("ok", { executionId }));
  } catch (error) {
    const message = error instanceof Error ? error.message : "execution upsert failed";
    return NextResponse.json(fail(500, message), { status: 500 });
  }
}
