import { NextResponse } from "next/server";
import { buildAppRedirectUrl } from "@/lib/auth-state";
import { fail, ok, parseJsonBody } from "@/lib/http";
import { createPublicClient } from "@/lib/supabase";

type PasswordResetBody = {
  email?: string;
};

export async function POST(req: Request) {
  const body = parseJsonBody<PasswordResetBody>(await req.json().catch(() => ({})));
  const email = (body.email ?? "").trim().toLowerCase();
  if (!email) {
    return NextResponse.json(fail(400, "email required"), { status: 400 });
  }

  try {
    const client = createPublicClient();
    const { error } = await client.auth.resetPasswordForEmail(email, {
      redirectTo: buildAppRedirectUrl("/reset-password", req.url),
    });

    if (error) {
      return NextResponse.json(fail(500, error.message), { status: 500 });
    }

    return NextResponse.json(ok("password reset email sent"));
  } catch (error) {
    const message = error instanceof Error ? error.message : "password reset failed";
    return NextResponse.json(fail(500, message), { status: 500 });
  }
}
