import { NextResponse } from "next/server";
import { writeAuditEvent } from "@/lib/audit";
import { hasAdminAccess } from "@/lib/admin-auth";
import { readDemoMetadata } from "@/lib/demo/bootstrap";
import { fail, ok, parseJsonBody } from "@/lib/http";
import { createPublicClient, createServiceClient } from "@/lib/supabase";
import { findConfirmationState, type AuthPayload } from "@/lib/auth-state";

type LoginBody = {
  email?: string;
  password?: string;
};

export async function POST(req: Request) {
  const body = parseJsonBody<LoginBody>(await req.json().catch(() => ({})));
  const email = (body.email ?? "").trim().toLowerCase();
  const password = body.password ?? "";

  if (!email || !password) {
    return NextResponse.json(fail(400, "email/password required"), { status: 400 });
  }

  try {
    const client = createPublicClient();
    const { data, error } = await client.auth.signInWithPassword({ email, password });
    if (error || !data.session || !data.user) {
      const serviceClient = createServiceClient();
      const confirmationState = await findConfirmationState(serviceClient, email).catch(() => null);
      if (confirmationState === "unconfirmed") {
        const payload: AuthPayload = {
          authState: "PENDING_CONFIRMATION",
          email,
          canResendConfirmation: true,
        };
        return NextResponse.json(ok("email confirmation required", payload), { status: 200 });
      }
      return NextResponse.json(fail(401, error?.message ?? "login failed"), { status: 401 });
    }

    const metadata = data.user.user_metadata as Record<string, unknown> | null;
    const metadataName = metadata?.username;
    const username = typeof metadataName === "string" ? metadataName : email.split("@")[0];
    const demoMetadata = readDemoMetadata(metadata);

    const serviceClient = createServiceClient();
    await writeAuditEvent(serviceClient, {
      userId: data.user.id,
      actor: "api:auth/login",
      action: "login",
      resourceType: "auth",
      resourceId: data.user.id,
    }).catch(() => null);

    return NextResponse.json(
      ok("ok", {
        authState: "SIGNED_IN",
        email,
        token: data.session.access_token,
        refreshToken: data.session.refresh_token ?? "",
        userId: data.user.id,
        username,
        demoRole: demoMetadata.demoRole || null,
        demoScenario: demoMetadata.demoScenario || null,
        demoSeedVersion: demoMetadata.demoSeedVersion || null,
        displayName: demoMetadata.displayName || null,
        adminRole: typeof metadata?.adminRole === "string" ? metadata.adminRole : null,
        adminAccess: hasAdminAccess(data.user),
        canResendConfirmation: false,
      })
    );
  } catch (error) {
    const message = error instanceof Error ? error.message : "login failed";
    return NextResponse.json(fail(500, message), { status: 500 });
  }
}
