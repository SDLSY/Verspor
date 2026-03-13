import { NextResponse } from "next/server";
import { requireAuth } from "@/lib/auth";
import { writeAuditEvent } from "@/lib/audit";
import { fail, ok, parseJsonBody } from "@/lib/http";
import { toIsoTime, toInt } from "@/lib/inference";
import { createServiceClient } from "@/lib/supabase";

type TaskBody = {
  taskId?: string;
  date?: number;
  sourceType?: string;
  triggerReason?: string;
  bodyZone?: string;
  protocolType?: string;
  durationSec?: number;
  plannedAt?: number;
  status?: string;
};

export async function POST(req: Request) {
  const auth = await requireAuth(req);
  if (!auth.authenticated) {
    return auth.response;
  }

  const body = parseJsonBody<TaskBody>(await req.json().catch(() => ({})));
  const taskId = (body.taskId ?? "").trim();
  if (!taskId) {
    return NextResponse.json(fail(400, "taskId is required"), { status: 400 });
  }

  try {
    const { error } = await auth.client.from("intervention_tasks").upsert(
      {
        user_id: auth.user.id,
        task_id: taskId,
        task_date: toIsoTime(body.date) ?? new Date().toISOString(),
        source_type: (body.sourceType ?? "RULE_ENGINE").trim().toUpperCase(),
        trigger_reason: body.triggerReason ?? "",
        body_zone: (body.bodyZone ?? "LIMB").trim().toUpperCase(),
        protocol_type: (body.protocolType ?? "LOW_ACTIVITY").trim().toUpperCase(),
        duration_sec: toInt(body.durationSec) ?? 60,
        planned_at: toIsoTime(body.plannedAt) ?? new Date().toISOString(),
        status: (body.status ?? "PENDING").trim().toUpperCase(),
        updated_at: new Date().toISOString(),
      },
      { onConflict: "user_id,task_id" }
    );
    if (error) {
      return NextResponse.json(fail(500, error.message), { status: 500 });
    }

    const serviceClient = createServiceClient();
    await writeAuditEvent(serviceClient, {
      userId: auth.user.id,
      actor: "api:intervention/task/upsert",
      action: "upsert",
      resourceType: "intervention_tasks",
      resourceId: taskId,
      metadata: { status: body.status ?? "PENDING" },
    }).catch(() => null);

    return NextResponse.json(ok("ok", { taskId }));
  } catch (error) {
    const message = error instanceof Error ? error.message : "task upsert failed";
    return NextResponse.json(fail(500, message), { status: 500 });
  }
}
