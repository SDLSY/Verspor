import type { SupabaseClient, User } from "@supabase/supabase-js";

export type ConfirmationState = "unconfirmed" | "confirmed" | null;
export type AuthState = "SIGNED_IN" | "PENDING_CONFIRMATION";

export type AuthPayload = {
  authState: AuthState;
  email: string;
  token?: string;
  refreshToken?: string;
  userId?: string;
  username?: string;
  canResendConfirmation: boolean;
};

export async function findUserByEmail(
  serviceClient: SupabaseClient,
  email: string
): Promise<User | null> {
  let page = 1;
  while (page <= 10) {
    const { data, error } = await serviceClient.auth.admin.listUsers({ page, perPage: 200 });
    if (error) {
      return null;
    }

    const matchedUser =
      data.users.find((candidate) => candidate.email?.trim().toLowerCase() === email) ?? null;
    if (matchedUser) {
      return matchedUser;
    }

    if (!data.nextPage) {
      return null;
    }
    page = data.nextPage;
  }
  return null;
}

export async function findConfirmationState(
  serviceClient: SupabaseClient,
  email: string
): Promise<ConfirmationState> {
  const matchedUser = await findUserByEmail(serviceClient, email);
  if (!matchedUser) {
    return null;
  }
  return matchedUser.email_confirmed_at ? "confirmed" : "unconfirmed";
}

export function buildAppRedirectUrl(pathname: string, requestUrl?: string): string {
  const rawBaseUrl = process.env.APP_PUBLIC_BASE_URL?.trim();
  const requestBaseUrl = resolveRequestOrigin(requestUrl);
  const baseUrl = rawBaseUrl || requestBaseUrl || "http://localhost:3000";
  return new URL(pathname, ensureTrailingSlash(baseUrl)).toString();
}

function resolveRequestOrigin(requestUrl?: string): string {
  if (!requestUrl) {
    return "";
  }

  try {
    return new URL(requestUrl).origin;
  } catch {
    return "";
  }
}

function ensureTrailingSlash(url: string): string {
  return url.endsWith("/") ? url : `${url}/`;
}
