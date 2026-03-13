import { NextResponse } from "next/server";
import { LoginSchema } from "@/server/v2/auth/schema";
import { fail, makeTraceId, ok } from "@/server/v2/common/types";

export async function POST(req: Request) {
  const traceId = makeTraceId();
  const body = await req.json().catch(() => ({}));
  const parsed = LoginSchema.safeParse(body);
  if (!parsed.success) {
    return NextResponse.json(fail(traceId, 400, "invalid payload"), { status: 400 });
  }
  return NextResponse.json(ok(traceId, "ok", { userId: "u_v2_demo", token: "v2-token" }));
}
