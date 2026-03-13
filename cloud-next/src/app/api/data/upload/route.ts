import { NextResponse } from "next/server";
import { requireAuth } from "@/lib/auth";
import { writeAuditEvent } from "@/lib/audit";
import { toInt } from "@/lib/inference";
import { fail, ok, parseJsonBody } from "@/lib/http";
import { createServiceClient } from "@/lib/supabase";
import { extractWindowFeatureRow } from "@/lib/window-features";

type RawUploadBody = {
  deviceId?: string;
  sleepRecordId?: string;
  timestamp?: number;
  sensorData?: unknown;
};

export async function POST(req: Request) {
  const auth = await requireAuth(req);
  if (!auth.authenticated) {
    return auth.response;
  }

  const body = parseJsonBody<RawUploadBody>(await req.json().catch(() => ({})));
  const deviceId = (body.deviceId ?? "").trim();
  const timestamp = toInt(body.timestamp) ?? Date.now();
  const windowRow = extractWindowFeatureRow(body.sensorData, timestamp);

  if (!deviceId) {
    return NextResponse.json(fail(400, "deviceId required"), { status: 400 });
  }

  const day = new Date(timestamp).toISOString().slice(0, 10).replaceAll("-", "");
  const sleepRecordId = (body.sleepRecordId ?? "").trim() || `raw_${day}`;

  try {
    const [deviceRes, windowRes] = await Promise.all([
      auth.client.from("devices").upsert(
        {
          user_id: auth.user.id,
          device_id: deviceId,
          device_name: "ring",
        },
        { onConflict: "user_id,device_id" }
      ),
      auth.client.from("sleep_windows").insert({
        user_id: auth.user.id,
        sleep_record_id: sleepRecordId,
        window_start: windowRow.windowStart,
        window_end: windowRow.windowEnd,
        hr_features: windowRow.hrFeatures,
        spo2_features: windowRow.spo2Features,
        hrv_features: windowRow.hrvFeatures,
        temp_features: windowRow.tempFeatures,
        motion_features: windowRow.motionFeatures,
        ppg_features: windowRow.ppgFeatures,
        edge_anomaly_signal: windowRow.edgeAnomalySignal,
      }),
    ]);

    if (deviceRes.error) {
      return NextResponse.json(fail(500, deviceRes.error.message), { status: 500 });
    }
    if (windowRes.error) {
      return NextResponse.json(fail(500, windowRes.error.message), { status: 500 });
    }

    const serviceClient = createServiceClient();
    await writeAuditEvent(serviceClient, {
      userId: auth.user.id,
      actor: "api:data/upload",
      action: "insert",
      resourceType: "sleep_windows",
      resourceId: sleepRecordId,
      metadata: { deviceId, modalityCount: windowRow.modalityCount },
    });

    return NextResponse.json(ok("raw data uploaded", undefined, true));
  } catch (error) {
    const message = error instanceof Error ? error.message : "raw upload failed";
    return NextResponse.json(fail(500, message), { status: 500 });
  }
}
