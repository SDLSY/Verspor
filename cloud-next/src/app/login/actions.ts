"use server";

import type { Route } from "next";
import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import { buildAppRedirectUrl } from "@/lib/auth-state";
import { createServiceClient } from "@/lib/supabase";
import { createClient } from "@/lib/supabase/server";

const DEFAULT_DEMO_LOGIN_EMAIL =
  process.env.DEMO_LOGIN_EMAIL ?? "demo_admin_console@demo.changgengring.local";

function readRequiredText(formData: FormData, key: string): string {
  const value = formData.get(key);
  return typeof value === "string" ? value.trim() : "";
}

function redirectWithMessage(message: string): never {
  redirect((`/login?message=${encodeURIComponent(message)}`) as Route);
}

export async function login(formData: FormData) {
  const supabase = await createClient();
  const email = readRequiredText(formData, "email").toLowerCase();
  const password = readRequiredText(formData, "password");

  const { error } = await supabase.auth.signInWithPassword({ email, password });
  if (error) {
    const confirmationState = await findConfirmationState(email).catch(() => null);
    if (confirmationState === "unconfirmed") {
      redirectWithMessage("该账户尚未完成邮箱确认，请先前往邮箱确认后再登录。");
    }
    redirectWithMessage("邮箱或密码错误，请重新检查后再试。");
  }

  revalidatePath("/", "layout");
  redirect("/dashboard" as Route);
}

export async function signup(formData: FormData) {
  const supabase = await createClient();
  const email = readRequiredText(formData, "email").toLowerCase();
  const password = readRequiredText(formData, "password");

  const { data, error } = await supabase.auth.signUp({
    email,
    password,
    options: {
      emailRedirectTo: buildAppRedirectUrl("/auth/confirm"),
    },
  });
  if (error) {
    redirectWithMessage("创建账户失败，请检查邮箱是否已存在。");
  }

  if (!data.session) {
    redirectWithMessage("账户已创建，请先前往邮箱完成确认，再返回登录。");
  }

  revalidatePath("/", "layout");
  redirect("/dashboard" as Route);
}

export async function loginWithDemoAccount() {
  const email = await resolveDemoLoginEmail();
  if (!email) {
    redirectWithMessage("当前环境中没有可用于直达登录的已确认账号。");
  }

  const serviceClient = createServiceClient();
  const { data, error } = await serviceClient.auth.admin.generateLink({
    type: "magiclink",
    email,
    options: {
      redirectTo: buildAppRedirectUrl("/login"),
    },
  });
  const tokenHash = data.properties?.hashed_token;
  const verificationType = data.properties?.verification_type;
  if (error || !tokenHash || !verificationType) {
    redirectWithMessage("演示账号直达登录失败，请检查当前环境中的账号配置。");
  }

  redirect(
    (`/auth/confirm?token_hash=${encodeURIComponent(tokenHash)}&type=${encodeURIComponent(
      verificationType,
    )}&next=${encodeURIComponent("/dashboard")}`) as Route,
  );
}

export async function logout() {
  const supabase = await createClient();
  await supabase.auth.signOut();
  revalidatePath("/", "layout");
  redirect("/login" as Route);
}

type ConfirmationState = "unconfirmed" | "confirmed" | null;

export async function resolveDemoLoginEmail(): Promise<string | null> {
  const preferredEmail = DEFAULT_DEMO_LOGIN_EMAIL.trim().toLowerCase();
  const serviceClient = createServiceClient();
  let page = 1;
  let firstConfirmedDemoEmail: string | null = null;
  let firstConfirmedEmail: string | null = null;

  while (page <= 10) {
    const { data, error } = await serviceClient.auth.admin.listUsers({ page, perPage: 200 });
    if (error) {
      return preferredEmail || firstConfirmedDemoEmail || firstConfirmedEmail;
    }

    for (const candidate of data.users) {
      const email = candidate.email?.trim().toLowerCase();
      if (!email || !candidate.email_confirmed_at) {
        continue;
      }
      if (email === preferredEmail) {
        return email;
      }
      if (!firstConfirmedDemoEmail && email.endsWith("@demo.changgengring.local")) {
        firstConfirmedDemoEmail = email;
      }
      if (!firstConfirmedEmail) {
        firstConfirmedEmail = email;
      }
    }

    if (!data.nextPage) {
      break;
    }
    page = data.nextPage;
  }

  return firstConfirmedDemoEmail ?? firstConfirmedEmail ?? preferredEmail ?? null;
}

async function findConfirmationState(email: string): Promise<ConfirmationState> {
  const serviceClient = createServiceClient();
  let page = 1;
  while (page <= 10) {
    const { data, error } = await serviceClient.auth.admin.listUsers({ page, perPage: 200 });
    if (error) {
      return null;
    }

    const matchedUser = data.users.find(
      (candidate) => candidate.email?.trim().toLowerCase() === email
    );
    if (matchedUser) {
      return matchedUser.email_confirmed_at ? "confirmed" : "unconfirmed";
    }

    if (!data.nextPage) {
      return null;
    }
    page = data.nextPage;
  }
  return null;
}
