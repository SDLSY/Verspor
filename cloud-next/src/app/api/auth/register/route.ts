import { NextResponse } from "next/server";
import { writeAuditEvent } from "@/lib/audit";
import { fail, ok, parseJsonBody } from "@/lib/http";
import { createPublicClient, createServiceClient } from "@/lib/supabase";
import { buildAppRedirectUrl, findConfirmationState, type AuthPayload } from "@/lib/auth-state";

type RegisterBody = {
  email?: string;
  password?: string;
  username?: string;
};

export async function POST(req: Request) {
  const body = parseJsonBody<RegisterBody>(await req.json().catch(() => ({})));
  const email = (body.email ?? "").trim().toLowerCase();
  const password = body.password ?? "";
  const username = (body.username ?? "").trim() || email.split("@")[0];

  if (!email || !password) {
    return NextResponse.json(fail(400, "email/password required"), { status: 400 });
  }

  try {
    const client = createPublicClient();
    const emailRedirectTo = buildAppRedirectUrl("/auth/confirm", req.url);
    const signUp = await client.auth.signUp({
      email,
      password,
      options: {
        emailRedirectTo,
        data: {
          username,
        },
      },
    });

    if (signUp.error) {
      const serviceClient = createServiceClient();
      const confirmationState = await findConfirmationState(serviceClient, email).catch(() => null);
      if (confirmationState === "unconfirmed") {
        const payload: AuthPayload = {
          authState: "PENDING_CONFIRMATION",
          email,
          canResendConfirmation: true,
        };
        return NextResponse.json(ok("confirmation pending", payload), { status: 202 });
      }
      return NextResponse.json(fail(400, signUp.error.message), { status: 400 });
    }

    let session = signUp.data.session;
    let userId = signUp.data.user?.id ?? "";
    const identities = signUp.data.user?.identities ?? [];

    if (!session) {
      const serviceClient = createServiceClient();
      const confirmationState = await findConfirmationState(serviceClient, email).catch(() => null);
      if (signUp.data.user?.id && identities.length == 0 && confirmationState === "confirmed") {
        return NextResponse.json(fail(409, "account already exists, please login"), {
          status: 409,
        });
      }
      const payload: AuthPayload = {
        authState: "PENDING_CONFIRMATION",
        email,
        canResendConfirmation: true,
      };
      return NextResponse.json(ok("confirmation pending", payload), {
        status: 202,
      });
    } else if (signUp.data.user) {
      userId = signUp.data.user.id;
    }

    if (!userId) {
      const payload: AuthPayload = {
        authState: "PENDING_CONFIRMATION",
        email,
        canResendConfirmation: true,
      };
      return NextResponse.json(ok("confirmation pending", payload), { status: 202 });
    }

    const serviceClient = createServiceClient();
    await writeAuditEvent(serviceClient, {
      userId,
      actor: "api:auth/register",
      action: "register",
      resourceType: "auth",
      resourceId: userId,
    }).catch(() => null);

    return NextResponse.json(
      ok("ok", {
        authState: "SIGNED_IN",
        email,
        token: session.access_token,
        refreshToken: session.refresh_token ?? "",
        userId,
        username,
        canResendConfirmation: false,
      })
    );
  } catch (error) {
    const message = error instanceof Error ? error.message : "register failed";
    return NextResponse.json(fail(500, message), { status: 500 });
  }
}
