import { NextResponse } from "next/server";
import { requireAuth } from "@/lib/auth";
import { writeAuditEvent } from "@/lib/audit";
import { fail, ok } from "@/lib/http";
import { createServiceClient } from "@/lib/supabase";

type TrendRow = {
  created_at: string;
  recovery_score: number | null;
};

export async function GET(req: Request) {
  const auth = await requireAuth(req);
  if (!auth.authenticated) {
    return auth.response;
  }

  const url = new URL(req.url);
  const days = Math.max(1, Math.min(90, Number(url.searchParams.get("days") ?? 7)));

  try {
    const { data, error } = await auth.client
      .from("nightly_reports")
      .select("created_at,recovery_score")
      .eq("user_id", auth.user.id)
      .order("created_at", { ascending: false })
      .limit(days)
      .returns<TrendRow[]>();

    if (error) {
      return NextResponse.json(fail(500, error.message), { status: 500 });
    }

    const trend = (data ?? []).map((row) => ({
      date: new Date(row.created_at).getTime(),
      score: row.recovery_score ?? 0,
    }));

    const serviceClient = createServiceClient();
    await writeAuditEvent(serviceClient, {
      userId: auth.user.id,
      actor: "api:recovery/trend",
      action: "read",
      resourceType: "nightly_reports",
      resourceId: auth.user.id,
    }).catch(() => null);

    return NextResponse.json(ok("ok", trend));
  } catch (error) {
    const message = error instanceof Error ? error.message : "trend query failed";
    return NextResponse.json(fail(500, message), { status: 500 });
  }
}
