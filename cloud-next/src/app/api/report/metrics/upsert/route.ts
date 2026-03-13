import { NextResponse } from "next/server";
import { requireAuth } from "@/lib/auth";
import { writeAuditEvent } from "@/lib/audit";
import { fail, ok, parseJsonBody, randomId } from "@/lib/http";
import { toIsoTime } from "@/lib/inference";
import { createServiceClient } from "@/lib/supabase";

type MetricItem = {
  metricCode?: string;
  metricName?: string;
  metricValue?: number;
  unit?: string;
  refLow?: number | null;
  refHigh?: number | null;
  isAbnormal?: boolean;
  confidence?: number;
};

type UpsertBody = {
  reportId?: string;
  reportDate?: number;
  reportType?: string;
  riskLevel?: string;
  metrics?: MetricItem[];
};

function toNumber(input: unknown, fallback = 0): number {
  const value = Number(input);
  return Number.isFinite(value) ? value : fallback;
}

export async function POST(req: Request) {
  const auth = await requireAuth(req);
  if (!auth.authenticated) {
    return auth.response;
  }

  const body = parseJsonBody<UpsertBody>(await req.json().catch(() => ({})));
  const reportId = (body.reportId ?? "").trim() || randomId("report");
  const reportDate = toIsoTime(body.reportDate) ?? new Date().toISOString();
  const reportType = (body.reportType ?? "PHOTO").trim();
  const riskLevel = (body.riskLevel ?? "LOW").trim().toUpperCase();
  const metrics = Array.isArray(body.metrics) ? body.metrics : [];

  try {
    const { error: reportError } = await auth.client.from("medical_reports").upsert(
      {
        user_id: auth.user.id,
        report_id: reportId,
        report_date: reportDate,
        report_type: reportType,
        parse_status: "PARSED",
        risk_level: riskLevel,
      },
      { onConflict: "user_id,report_id" }
    );
    if (reportError) {
      return NextResponse.json(fail(500, reportError.message), { status: 500 });
    }

    const { error: deleteError } = await auth.client
      .from("medical_metrics")
      .delete()
      .eq("user_id", auth.user.id)
      .eq("report_id", reportId);
    if (deleteError) {
      return NextResponse.json(fail(500, deleteError.message), { status: 500 });
    }

    if (metrics.length > 0) {
      const payload = metrics.map((metric) => ({
        user_id: auth.user.id,
        report_id: reportId,
        metric_code: (metric.metricCode ?? "UNKNOWN").trim(),
        metric_name: (metric.metricName ?? "Unknown metric").trim(),
        metric_value: toNumber(metric.metricValue),
        unit: (metric.unit ?? "").trim(),
        ref_low: metric.refLow == null ? null : toNumber(metric.refLow),
        ref_high: metric.refHigh == null ? null : toNumber(metric.refHigh),
        is_abnormal: Boolean(metric.isAbnormal),
        confidence: toNumber(metric.confidence, 0.8),
      }));

      const { error: metricError } = await auth.client.from("medical_metrics").insert(payload);
      if (metricError) {
        return NextResponse.json(fail(500, metricError.message), { status: 500 });
      }
    }

    const serviceClient = createServiceClient();
    await writeAuditEvent(serviceClient, {
      userId: auth.user.id,
      actor: "api:report/metrics/upsert",
      action: "upsert",
      resourceType: "medical_reports",
      resourceId: reportId,
      metadata: { metricCount: metrics.length, riskLevel },
    }).catch(() => null);

    return NextResponse.json(ok("ok", { reportId, metricCount: metrics.length }));
  } catch (error) {
    const message = error instanceof Error ? error.message : "report upsert failed";
    return NextResponse.json(fail(500, message), { status: 500 });
  }
}
