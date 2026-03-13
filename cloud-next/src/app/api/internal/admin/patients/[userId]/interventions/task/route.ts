import { NextResponse } from "next/server";
import { upsertAdminInterventionTask } from "@/lib/admin-mutations";
import { requireAdminRoute } from "@/lib/admin-auth";
import { fail, ok, parseJsonBody } from "@/lib/http";

type RouteContext = {
  params: Promise<{ userId: string }>;
};

type Body = {
  taskId?: string;
  date?: number | null;
  sourceType?: string;
  triggerReason?: string;
  bodyZone?: string;
  protocolType?: string;
  durationSec?: number;
  plannedAt?: number | null;
  status?: string;
};

export async function POST(req: Request, context: RouteContext) {
  const access = await requireAdminRoute();
  if (!access.ok) {
    return access.response;
  }

  const { userId } = await context.params;
  const body = parseJsonBody<Body>(await req.json().catch(() => ({})));

  try {
    const data = await upsertAdminInterventionTask(userId, {
      ...body,
      actorEmail: access.user.email,
    });
    return NextResponse.json(ok("ok", data));
  } catch (error) {
    const message = error instanceof Error ? error.message : "task save failed";
    const status = /not found/i.test(message) ? 404 : 500;
    return NextResponse.json(fail(status, message), { status });
  }
}
