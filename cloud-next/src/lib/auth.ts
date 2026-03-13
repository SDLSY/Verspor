import { NextResponse } from "next/server";
import type { SupabaseClient, User } from "@supabase/supabase-js";
import { fail } from "@/lib/http";
import { createUserClient } from "@/lib/supabase";

type AuthSuccess = {
  authenticated: true;
  token: string;
  user: User;
  client: SupabaseClient;
};

type AuthFailure = {
  authenticated: false;
  response: NextResponse;
};

export type AuthContext = AuthSuccess | AuthFailure;

function getBearerToken(req: Request): string {
  const header = req.headers.get("authorization") ?? "";
  if (!header.toLowerCase().startsWith("bearer ")) {
    return "";
  }
  return header.slice(7).trim();
}

export async function requireAuth(req: Request): Promise<AuthContext> {
  const token = getBearerToken(req);
  if (!token) {
    return {
      authenticated: false,
      response: NextResponse.json(fail(401, "missing bearer token"), { status: 401 }),
    };
  }

  const client = createUserClient(token);
  const { data, error } = await client.auth.getUser();
  if (error || !data.user) {
    return {
      authenticated: false,
      response: NextResponse.json(fail(401, "invalid token"), { status: 401 }),
    };
  }

  return {
    authenticated: true,
    token,
    user: data.user,
    client,
  };
}
