import { NextResponse } from "next/server";
import { requireAuth } from "@/lib/auth";
import { writeAuditEvent } from "@/lib/audit";
import { fail, ok, parseJsonBody } from "@/lib/http";
import { createServiceClient } from "@/lib/supabase";

type ProfileBody = {
  username?: string;
  age?: number;
  gender?: string;
};

function buildProfile(
  userId: string,
  email: string,
  metadata: Record<string, unknown> | null
): {
  userId: string;
  username: string;
  email: string;
  age: number | null;
  gender: string | null;
} {
  const usernameRaw = metadata?.username;
  const ageRaw = metadata?.age;
  const genderRaw = metadata?.gender;
  return {
    userId,
    username: typeof usernameRaw === "string" ? usernameRaw : email.split("@")[0],
    email,
    age: typeof ageRaw === "number" ? ageRaw : null,
    gender: typeof genderRaw === "string" ? genderRaw : null,
  };
}

export async function GET(req: Request) {
  const auth = await requireAuth(req);
  if (!auth.authenticated) {
    return auth.response;
  }

  const metadata = auth.user.user_metadata as Record<string, unknown> | null;
  const serviceClient = createServiceClient();
  await writeAuditEvent(serviceClient, {
    userId: auth.user.id,
    actor: "api:user/profile",
    action: "read",
    resourceType: "profile",
    resourceId: auth.user.id,
  }).catch(() => null);
  return NextResponse.json(ok("ok", buildProfile(auth.user.id, auth.user.email ?? "", metadata)));
}

export async function PUT(req: Request) {
  const auth = await requireAuth(req);
  if (!auth.authenticated) {
    return auth.response;
  }

  const body = parseJsonBody<ProfileBody>(await req.json().catch(() => ({})));
  const payload: Record<string, unknown> = {};

  if (typeof body.username === "string") {
    payload.username = body.username;
  }
  if (typeof body.age === "number") {
    payload.age = body.age;
  }
  if (typeof body.gender === "string") {
    payload.gender = body.gender;
  }

  if (Object.keys(payload).length === 0) {
    const metadata = auth.user.user_metadata as Record<string, unknown> | null;
    return NextResponse.json(ok("ok", buildProfile(auth.user.id, auth.user.email ?? "", metadata)));
  }

  try {
    const { data, error } = await auth.client.auth.updateUser({
      data: payload,
    });

    if (error) {
      return NextResponse.json(fail(500, error.message), { status: 500 });
    }

    const metadata = data.user?.user_metadata as Record<string, unknown> | null;
    const serviceClient = createServiceClient();
    await writeAuditEvent(serviceClient, {
      userId: auth.user.id,
      actor: "api:user/profile",
      action: "update",
      resourceType: "profile",
      resourceId: auth.user.id,
    }).catch(() => null);
    return NextResponse.json(ok("ok", buildProfile(auth.user.id, auth.user.email ?? "", metadata)));
  } catch (error) {
    const message = error instanceof Error ? error.message : "profile update failed";
    return NextResponse.json(fail(500, message), { status: 500 });
  }
}
