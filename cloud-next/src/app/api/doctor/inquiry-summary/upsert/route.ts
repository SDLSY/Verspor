import { NextResponse } from "next/server";
import { requireAuth } from "@/lib/auth";
import { writeAuditEvent } from "@/lib/audit";
import { fail, ok, parseJsonBody } from "@/lib/http";
import { toIsoTime } from "@/lib/inference";
import { createServiceClient } from "@/lib/supabase";

type DoctorInquirySummaryBody = {
  sessionId?: string;
  assessedAt?: number;
  riskLevel?: string;
  chiefComplaint?: string;
  redFlags?: string[];
  recommendedDepartment?: string;
  doctorSummary?: string;
};

function sanitizeStrings(value: string[] | undefined): string[] {
  if (!Array.isArray(value)) {
    return [];
  }
  return Array.from(
    new Set(
      value
        .map((item) => (typeof item === "string" ? item.trim() : ""))
        .filter(Boolean)
        .slice(0, 12)
    )
  );
}

export async function POST(req: Request) {
  const auth = await requireAuth(req);
  if (!auth.authenticated) {
    return auth.response;
  }

  const body = parseJsonBody<DoctorInquirySummaryBody>(await req.json().catch(() => ({})));
  const sessionId = (body.sessionId ?? "").trim();
  if (!sessionId) {
    return NextResponse.json(fail(400, "sessionId is required"), { status: 400 });
  }

  const riskLevel = (body.riskLevel ?? "LOW").trim().toUpperCase();
  const redFlags = sanitizeStrings(body.redFlags);

  try {
    const { error } = await auth.client.from("doctor_inquiry_summaries").upsert(
      {
        user_id: auth.user.id,
        session_id: sessionId,
        assessed_at: toIsoTime(body.assessedAt) ?? new Date().toISOString(),
        risk_level: riskLevel,
        chief_complaint: (body.chiefComplaint ?? "").trim(),
        red_flags_json: redFlags,
        recommended_department: (body.recommendedDepartment ?? "").trim(),
        doctor_summary: (body.doctorSummary ?? "").trim(),
        updated_at: new Date().toISOString(),
      },
      { onConflict: "user_id,session_id" }
    );

    if (error) {
      return NextResponse.json(fail(500, error.message), { status: 500 });
    }

    const serviceClient = createServiceClient();
    await writeAuditEvent(serviceClient, {
      userId: auth.user.id,
      actor: "api:doctor/inquiry-summary/upsert",
      action: "upsert",
      resourceType: "doctor_inquiry_summaries",
      resourceId: sessionId,
      metadata: {
        riskLevel,
        redFlags,
      },
    }).catch(() => null);

    return NextResponse.json(ok("ok", { sessionId, riskLevel }));
  } catch (error) {
    const message = error instanceof Error ? error.message : "doctor inquiry summary upsert failed";
    return NextResponse.json(fail(500, message), { status: 500 });
  }
}
