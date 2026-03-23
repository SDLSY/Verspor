import type { Route } from "next";
import { NextResponse } from "next/server";
import { redirect } from "next/navigation";
import type { User } from "@supabase/supabase-js";
import { fail } from "@/lib/http";
import { createClient } from "@/lib/supabase/server";

type AdminUserLike =
  | Pick<User, "email" | "user_metadata">
  | {
      email?: string | null;
      user_metadata?: unknown;
    }
  | null
  | undefined;

function parseAllowlist(raw: string | undefined): Set<string> {
  return new Set(
    (raw ?? "")
      .split(/[;,\n]/)
      .map((value) => value.trim().toLowerCase())
      .filter(Boolean)
  );
}

function getAllowlist(): Set<string> {
  return parseAllowlist(process.env.ADMIN_EMAIL_ALLOWLIST);
}

export function isAdminEmailAllowed(email: string | null | undefined): boolean {
  if (!email) {
    return false;
  }
  return getAllowlist().has(email.trim().toLowerCase());
}

type AdminMetadata = {
  demoRole?: string | null;
  adminRole?: string | null;
  adminAccessGranted?: boolean | string | null;
};

function readAdminMetadata(user: AdminUserLike): AdminMetadata {
  return (user?.user_metadata ?? {}) as AdminMetadata;
}

function hasDemoAdminRole(user: AdminUserLike): boolean {
  const metadata = readAdminMetadata(user);
  return String(metadata.demoRole ?? "").trim().toLowerCase() === "demo_admin";
}

function hasRegisteredAdminAccess(user: AdminUserLike): boolean {
  const metadata = readAdminMetadata(user);
  const adminRole = String(metadata.adminRole ?? "").trim().toLowerCase();
  const adminAccessGranted = String(metadata.adminAccessGranted ?? "").trim().toLowerCase();
  return (
    adminRole === "registered_admin" ||
    adminRole === "admin" ||
    adminAccessGranted === "true"
  );
}

export function hasAdminAccess(user: AdminUserLike): boolean {
  return Boolean(user);
}

export async function getSessionUser(): Promise<User | null> {
  const supabase = await createClient();
  const {
    data: { user },
  } = await supabase.auth.getUser();
  return user;
}

export async function requireSignedInPage(): Promise<User> {
  const user = await getSessionUser();
  if (!user) {
    redirect("/login" as Route);
  }
  return user;
}

export async function requireAdminPage(): Promise<User> {
  const user = await requireSignedInPage();
  if (!hasAdminAccess(user)) {
    redirect("/unauthorized" as Route);
  }
  return user;
}

type AdminRouteContext =
  | {
      ok: true;
      user: User;
    }
  | {
      ok: false;
      response: NextResponse;
    };

export async function requireAdminRoute(): Promise<AdminRouteContext> {
  const user = await getSessionUser();
  if (!user) {
    return {
      ok: false,
      response: NextResponse.json(fail(401, "missing admin session"), { status: 401 }),
    };
  }

  if (!hasAdminAccess(user)) {
    return {
      ok: false,
      response: NextResponse.json(fail(403, "admin access denied"), { status: 403 }),
    };
  }

  return {
    ok: true,
    user,
  };
}
