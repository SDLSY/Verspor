import { NextResponse } from "next/server";
import { buildAppRedirectUrl, findConfirmationState } from "@/lib/auth-state";
import { fail, ok, parseJsonBody } from "@/lib/http";
import { createPublicClient, createServiceClient } from "@/lib/supabase";

type ResendConfirmationBody = {
  email?: string;
};

export async function POST(req: Request) {
  const body = parseJsonBody<ResendConfirmationBody>(await req.json().catch(() => ({})));
  const email = (body.email ?? "").trim().toLowerCase();
  if (!email) {
    return NextResponse.json(fail(400, "email required"), { status: 400 });
  }

  try {
    const serviceClient = createServiceClient();
    const confirmationState = await findConfirmationState(serviceClient, email).catch(() => null);
    if (confirmationState === "confirmed") {
      return NextResponse.json(ok("email already confirmed"));
    }

    const client = createPublicClient();
    const { error } = await client.auth.resend({
      type: "signup",
      email,
      options: {
        emailRedirectTo: buildAppRedirectUrl("/auth/confirm", req.url),
      },
    });

    if (error && confirmationState === "unconfirmed") {
      return NextResponse.json(fail(500, error.message), { status: 500 });
    }

    return NextResponse.json(ok("confirmation email sent"));
  } catch (error) {
    const message = error instanceof Error ? error.message : "resend confirmation failed";
    return NextResponse.json(fail(500, message), { status: 500 });
  }
}
