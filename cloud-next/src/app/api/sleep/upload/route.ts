import { NextResponse } from "next/server";
import { requireAuth } from "@/lib/auth";
import { writeAuditEvent } from "@/lib/audit";
import { toInt, toIsoTime } from "@/lib/inference";
import { fail, ok, parseJsonBody } from "@/lib/http";
import { createServiceClient } from "@/lib/supabase";

type UploadBody = {
  sleepRecordId?: string;
  idempotencyKey?: string;
  date?: number;
  bedTime?: number;
  wakeTime?: number;
  totalSleepMinutes?: number;
  deepSleepMinutes?: number;
  lightSleepMinutes?: number;
  remSleepMinutes?: number;
};

function normalizedIdempotencyKey(req: Request, body: UploadBody, userId: string): string {
  const headerKey = req.headers.get("x-idempotency-key") ?? "";
  const explicit = (body.idempotencyKey ?? headerKey).trim();
  if (explicit) {
    return explicit;
  }
  const dateSeed = Number(body.date ?? Date.now());
  return `${userId}:${dateSeed}`;
}

function buildSleepRecordId(explicit: string, idempotencyKey: string): string {
  const value = explicit.trim();
  if (value) {
    return value;
  }
  const compact = idempotencyKey.replace(/[^a-zA-Z0-9]/g, "").slice(0, 24);
  return `sleep_${compact || Date.now().toString()}`;
}

export async function POST(req: Request) {
  const auth = await requireAuth(req);
  if (!auth.authenticated) {
    return auth.response;
  }

  const body = parseJsonBody<UploadBody>(await req.json().catch(() => ({})));
  const idempotencyKey = normalizedIdempotencyKey(req, body, auth.user.id);
  const sleepRecordId = buildSleepRecordId(body.sleepRecordId ?? "", idempotencyKey);

  const sessionDate = toIsoTime(body.date) ?? new Date().toISOString();
  const bedTime = toIsoTime(body.bedTime);
  const wakeTime = toIsoTime(body.wakeTime);

  try {
    const { error } = await auth.client.from("sleep_sessions").upsert(
      {
        user_id: auth.user.id,
        sleep_record_id: sleepRecordId,
        session_date: sessionDate,
        bed_time: bedTime,
        wake_time: wakeTime,
        total_sleep_minutes: toInt(body.totalSleepMinutes),
        deep_sleep_minutes: toInt(body.deepSleepMinutes),
        light_sleep_minutes: toInt(body.lightSleepMinutes),
        rem_sleep_minutes: toInt(body.remSleepMinutes),
        source: "mobile_upload",
      },
      { onConflict: "user_id,sleep_record_id" }
    );

    if (error) {
      return NextResponse.json(fail(500, error.message), { status: 500 });
    }

    const serviceClient = createServiceClient();
    await writeAuditEvent(serviceClient, {
      userId: auth.user.id,
      actor: "api:sleep/upload",
      action: "upsert",
      resourceType: "sleep_sessions",
      resourceId: sleepRecordId,
      metadata: {
        idempotencyKey,
      },
    });

    return NextResponse.json(ok(`uploaded ${sleepRecordId}`, undefined, true));
  } catch (error) {
    const message = error instanceof Error ? error.message : "upload failed";
    return NextResponse.json(fail(500, message), { status: 500 });
  }
}
