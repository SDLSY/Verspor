import { NextResponse } from "next/server";
import { requireAuth } from "@/lib/auth";
import { writeAuditEvent } from "@/lib/audit";
import { fail, ok } from "@/lib/http";
import { createServiceClient } from "@/lib/supabase";

type HistoryRow = {
  sleep_record_id: string;
  session_date: string;
  total_sleep_minutes: number | null;
};

function qualityByDuration(duration: number): string {
  if (duration >= 450) {
    return "优";
  }
  if (duration >= 390) {
    return "良";
  }
  if (duration >= 330) {
    return "一般";
  }
  return "较差";
}

export async function GET(req: Request) {
  const auth = await requireAuth(req);
  if (!auth.authenticated) {
    return auth.response;
  }

  const url = new URL(req.url);
  const startDate = Number(url.searchParams.get("startDate") ?? 0);
  const endDate = Number(url.searchParams.get("endDate") ?? Date.now());

  try {
    const { data, error } = await auth.client
      .from("sleep_sessions")
      .select("sleep_record_id,session_date,total_sleep_minutes")
      .eq("user_id", auth.user.id)
      .gte("session_date", new Date(startDate > 0 ? startDate : Date.now() - 30 * 86400000).toISOString())
      .lte("session_date", new Date(endDate).toISOString())
      .order("session_date", { ascending: false })
      .returns<HistoryRow[]>();

    if (error) {
      return NextResponse.json(fail(500, error.message), { status: 500 });
    }

    const history = (data ?? []).map((row) => {
      const duration = row.total_sleep_minutes ?? 0;
      return {
        id: row.sleep_record_id,
        date: new Date(row.session_date).getTime(),
        duration,
        quality: qualityByDuration(duration),
      };
    });

    const serviceClient = createServiceClient();
    await writeAuditEvent(serviceClient, {
      userId: auth.user.id,
      actor: "api:sleep/history",
      action: "read",
      resourceType: "sleep_sessions",
      resourceId: auth.user.id,
    }).catch(() => null);

    return NextResponse.json(ok("ok", history));
  } catch (error) {
    const message = error instanceof Error ? error.message : "history query failed";
    return NextResponse.json(fail(500, message), { status: 500 });
  }
}
