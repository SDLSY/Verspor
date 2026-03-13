import { NextResponse } from "next/server";
import { requireAuth } from "@/lib/auth";
import { writeAuditEvent } from "@/lib/audit";
import { fail, ok } from "@/lib/http";
import { createServiceClient } from "@/lib/supabase";

type LatestReportRow = {
  report_id: string;
  report_date: string;
  risk_level: string;
};

type AbnormalCountRow = {
  count: number;
};

export async function GET(req: Request) {
  const auth = await requireAuth(req);
  if (!auth.authenticated) {
    return auth.response;
  }

  try {
    const { data, error } = await auth.client
      .from("medical_reports")
      .select("report_id,report_date,risk_level")
      .eq("user_id", auth.user.id)
      .order("report_date", { ascending: false })
      .limit(1)
      .returns<LatestReportRow[]>();
    if (error) {
      return NextResponse.json(fail(500, error.message), { status: 500 });
    }

    const latest = data?.[0];
    if (!latest) {
      return NextResponse.json(ok("ok", null));
    }

    const { count, error: countError } = await auth.client
      .from("medical_metrics")
      .select("*", { count: "exact", head: true })
      .eq("user_id", auth.user.id)
      .eq("report_id", latest.report_id)
      .eq("is_abnormal", true)
      .returns<AbnormalCountRow[]>();
    if (countError) {
      return NextResponse.json(fail(500, countError.message), { status: 500 });
    }

    const serviceClient = createServiceClient();
    await writeAuditEvent(serviceClient, {
      userId: auth.user.id,
      actor: "api:report/latest",
      action: "read",
      resourceType: "medical_reports",
      resourceId: latest.report_id,
    }).catch(() => null);

    return NextResponse.json(
      ok("ok", {
        reportId: latest.report_id,
        reportDate: new Date(latest.report_date).getTime(),
        riskLevel: latest.risk_level,
        abnormalCount: count ?? 0,
      })
    );
  } catch (error) {
    const message = error instanceof Error ? error.message : "query latest report failed";
    return NextResponse.json(fail(500, message), { status: 500 });
  }
}
