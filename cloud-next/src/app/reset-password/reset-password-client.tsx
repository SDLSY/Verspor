"use client";

import Link from "next/link";
import { FormEvent, useEffect, useMemo, useState } from "react";
import { createClient } from "@supabase/supabase-js";

type VerifyState = "verifying" | "ready" | "success" | "error";

function createBrowserClient() {
  return createClient(
    process.env.NEXT_PUBLIC_SUPABASE_URL ?? "",
    process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY ?? ""
  );
}

function readHashRecoverySession() {
  if (typeof window === "undefined") {
    return null;
  }

  const hash = window.location.hash.startsWith("#")
    ? window.location.hash.slice(1)
    : window.location.hash;
  if (!hash) {
    return null;
  }

  const params = new URLSearchParams(hash);
  const accessToken = params.get("access_token") ?? "";
  const refreshToken = params.get("refresh_token") ?? "";
  const recoveryType = params.get("type") ?? "";
  if (!accessToken || !refreshToken || recoveryType !== "recovery") {
    return null;
  }

  return { accessToken, refreshToken };
}

export default function ResetPasswordClient({
  code,
  tokenHash,
  type,
}: {
  code: string;
  tokenHash: string;
  type: string;
}) {
  const [status, setStatus] = useState<VerifyState>("verifying");
  const [message, setMessage] = useState("正在验证重置链接，请稍候。");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const supabase = useMemo(() => createBrowserClient(), []);

  useEffect(() => {
    let cancelled = false;

    async function markReadyOrError(errorMessage?: string) {
      if (cancelled) {
        return;
      }

      if (errorMessage) {
        setStatus("error");
        setMessage(errorMessage);
        return;
      }

      setStatus("ready");
      setMessage("验证成功，请设置新的登录密码。");
    }

    async function verify() {
      if (code) {
        const { error } = await supabase.auth.exchangeCodeForSession(code);
        await markReadyOrError(error?.message || undefined);
        return;
      }

      const hashSession = readHashRecoverySession();
      if (hashSession) {
        const { error } = await supabase.auth.setSession({
          access_token: hashSession.accessToken,
          refresh_token: hashSession.refreshToken,
        });
        await markReadyOrError(error?.message || undefined);
        return;
      }

      if (tokenHash && type === "recovery") {
        const { error } = await supabase.auth.verifyOtp({
          token_hash: tokenHash,
          type: "recovery",
        });
        await markReadyOrError(error?.message || undefined);
        return;
      }

      await markReadyOrError("重置链接无效或已过期，请重新申请密码重置邮件。");
    }

    void verify();
    return () => {
      cancelled = true;
    };
  }, [code, supabase, tokenHash, type]);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (password.length < 6) {
      setMessage("密码至少 6 位。");
      return;
    }
    if (password !== confirmPassword) {
      setMessage("两次输入的密码不一致。");
      return;
    }

    setStatus("verifying");
    setMessage("正在保存新密码，请稍候。");
    const { error } = await supabase.auth.updateUser({ password });
    if (error) {
      setStatus("ready");
      setMessage(error.message || "密码更新失败，请重新尝试。");
      return;
    }

    setStatus("success");
    setMessage("密码已更新，请返回登录页并使用新密码登录。");
  }

  return (
    <main className="admin-standalone-page">
      <section className="admin-login-card">
        <div>
          <p className="admin-kicker">长庚环 Cloud</p>
          <h1 className="admin-page-title">重置密码</h1>
          <p className="admin-page-subtitle">{message}</p>
        </div>

        {status === "ready" ? (
          <form className="admin-form-stack" onSubmit={submit}>
            <label className="admin-field">
              <span>新密码</span>
              <input
                className="admin-input"
                type="password"
                autoComplete="new-password"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                required
              />
            </label>

            <label className="admin-field">
              <span>确认新密码</span>
              <input
                className="admin-input"
                type="password"
                autoComplete="new-password"
                value={confirmPassword}
                onChange={(event) => setConfirmPassword(event.target.value)}
                required
              />
            </label>

            <button className="admin-primary-button" type="submit">
              保存新密码
            </button>
          </form>
        ) : null}

        {status === "success" || status === "error" ? (
          <div className="admin-button-row">
            <Link className="admin-secondary-button" href="/login">
              返回登录
            </Link>
          </div>
        ) : null}
      </section>
    </main>
  );
}
