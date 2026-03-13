import { NextResponse } from "next/server";
import { requireAuth } from "@/lib/auth";
import { writeAuditEvent } from "@/lib/audit";
import { ok, parseJsonBody } from "@/lib/http";
import { createServiceClient } from "@/lib/supabase";

type SyncBody = {
  data?: Record<string, unknown>;
};

export async function POST(req: Request) {
  const auth = await requireAuth(req);
  if (!auth.authenticated) {
    return auth.response;
  }

  const body = parseJsonBody<SyncBody>(await req.json().catch(() => ({})));
  const updatedRecords = body.data ? Object.keys(body.data).length : 0;

  const serviceClient = createServiceClient();
  await writeAuditEvent(serviceClient, {
    userId: auth.user.id,
    actor: "api:sync",
    action: "sync",
    resourceType: "sync",
    resourceId: auth.user.id,
    metadata: { updatedRecords },
  }).catch(() => null);

  return NextResponse.json(
    ok("ok", {
      syncTime: Date.now(),
      updatedRecords,
    })
  );
}
