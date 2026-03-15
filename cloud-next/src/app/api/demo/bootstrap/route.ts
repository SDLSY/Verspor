import { NextResponse } from "next/server";
import { requireAuth } from "@/lib/auth";
import { fail, ok } from "@/lib/http";
import { buildDemoBootstrapPayload, readDemoMetadata } from "@/lib/demo/bootstrap";

export async function GET(req: Request) {
  const auth = await requireAuth(req);
  if (!auth.authenticated) {
    return auth.response;
  }

  const metadata = readDemoMetadata(auth.user.user_metadata as Record<string, unknown> | null);
  if (metadata.demoRole !== "demo_user" || !metadata.demoScenario) {
    return NextResponse.json(fail(403, "demo bootstrap unavailable for current account"), {
      status: 403,
    });
  }

  const payload = buildDemoBootstrapPayload({
    userId: auth.user.id,
    scenario: metadata.demoScenario,
    displayName: metadata.displayName,
  });

  if (!payload) {
    return NextResponse.json(fail(404, "demo scenario not found"), { status: 404 });
  }

  return NextResponse.json(ok("ok", payload));
}
