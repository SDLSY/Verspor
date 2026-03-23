import { NextResponse } from "next/server";
import { writeAuditEvent } from "@/lib/audit";
import { hasAdminAccess } from "@/lib/admin-auth";
import { findConfirmationState, type AuthPayload } from "@/lib/auth-state";
import { readDemoMetadata } from "@/lib/demo/bootstrap";
import { fail, ok } from "@/lib/http";
import { createPublicClient, createServiceClient } from "@/lib/supabase";

const DEFAULT_ANDROID_DEMO_LOGIN_EMAIL =
  process.env.DEMO_ANDROID_LOGIN_EMAIL ?? "demo_baseline_recovery@demo.changgengring.local";

type DemoLoginTarget = {
  email: string;
  role: "demo_user" | "demo_admin" | "";
};

export async function POST() {
  const target = await resolveDemoLoginTarget();
  const email = target?.email ?? "";
  const password = resolveDemoLoginPassword(target?.role ?? "");
  if (!email || !password) {
    return NextResponse.json(fail(503, "demo login unavailable"), { status: 503 });
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
      return NextResponse.json(fail(401, error?.message ?? "demo login failed"), { status: 401 });
    }

    const metadata = data.user.user_metadata as Record<string, unknown> | null;
    const metadataName = metadata?.username;
    const username = typeof metadataName === "string" ? metadataName : email.split("@")[0];
    const demoMetadata = readDemoMetadata(metadata);

    const serviceClient = createServiceClient();
    await writeAuditEvent(serviceClient, {
      userId: data.user.id,
      actor: "api:auth/demo-login",
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
      }),
    );
  } catch (error) {
    const message = error instanceof Error ? error.message : "demo login failed";
    return NextResponse.json(fail(500, message), { status: 500 });
  }
}

async function resolveDemoLoginTarget(): Promise<DemoLoginTarget | null> {
  const preferredEmail = DEFAULT_ANDROID_DEMO_LOGIN_EMAIL.trim().toLowerCase();
  const serviceClient = createServiceClient();
  let page = 1;
  let firstConfirmedDemoUser: DemoLoginTarget | null = null;
  let firstConfirmedAdmin: DemoLoginTarget | null = null;
  let firstConfirmedEmail: DemoLoginTarget | null = null;

  while (page <= 10) {
    const { data, error } = await serviceClient.auth.admin.listUsers({ page, perPage: 200 });
    if (error) {
      return (
        (preferredEmail ? { email: preferredEmail, role: "demo_user" as const } : null) ??
        firstConfirmedDemoUser ??
        firstConfirmedAdmin ??
        firstConfirmedEmail
      );
    }

    for (const candidate of data.users) {
      const email = candidate.email?.trim().toLowerCase();
      if (!email || !candidate.email_confirmed_at) {
        continue;
      }
      const metadata = candidate.user_metadata as Record<string, unknown> | null;
      const demoRole =
        typeof metadata?.demoRole === "string" ? metadata.demoRole.trim().toLowerCase() : "";

      if (email === preferredEmail) {
        return {
          email,
          role: demoRole === "demo_user" || demoRole === "demo_admin" ? demoRole : "demo_user",
        };
      }
      if (!firstConfirmedDemoUser && demoRole === "demo_user") {
        firstConfirmedDemoUser = { email, role: "demo_user" };
      }
      if (!firstConfirmedAdmin && demoRole === "demo_admin") {
        firstConfirmedAdmin = { email, role: "demo_admin" };
      }
      if (!firstConfirmedEmail) {
        firstConfirmedEmail = {
          email,
          role: demoRole === "demo_user" || demoRole === "demo_admin" ? demoRole : "",
        };
      }
    }

    if (!data.nextPage) {
      break;
    }
    page = data.nextPage;
  }

  return (
    firstConfirmedDemoUser ??
    firstConfirmedAdmin ??
    firstConfirmedEmail ??
    (preferredEmail ? { email: preferredEmail, role: "demo_user" as const } : null)
  );
}

function resolveDemoLoginPassword(role: DemoLoginTarget["role"]): string {
  if (role === "demo_admin") {
    return (
      process.env.DEMO_ADMIN_DEFAULT_PASSWORD?.trim() ||
      process.env.DEMO_ACCOUNT_DEFAULT_PASSWORD?.trim() ||
      process.env.DEMO_LOGIN_PASSWORD?.trim() ||
      ""
    );
  }

  return (
    process.env.DEMO_ACCOUNT_DEFAULT_PASSWORD?.trim() ||
    process.env.DEMO_LOGIN_PASSWORD?.trim() ||
    process.env.DEMO_ADMIN_DEFAULT_PASSWORD?.trim() ||
    ""
  );
}
