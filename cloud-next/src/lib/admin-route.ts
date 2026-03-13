import { NextResponse } from "next/server";
import type { User } from "@supabase/supabase-js";
import { requireAdminRoute } from "@/lib/admin-auth";
import { fail, ok } from "@/lib/http";

function mapErrorStatus(message: string): number {
  return /not found/i.test(message) ? 404 : 500;
}

export async function withAdminRoute<T>(
  handler: (user: User) => Promise<T>
): Promise<NextResponse> {
  const access = await requireAdminRoute();
  if (!access.ok) {
    return access.response;
  }

  try {
    const data = await handler(access.user);
    return NextResponse.json(ok("ok", data));
  } catch (error) {
    const message = error instanceof Error ? error.message : "internal admin error";
    return NextResponse.json(fail(mapErrorStatus(message), message), {
      status: mapErrorStatus(message),
    });
  }
}

export function adminBadRequest(message: string): NextResponse {
  return NextResponse.json(fail(400, message), { status: 400 });
}
