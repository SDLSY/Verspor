"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { createClient, type EmailOtpType } from "@supabase/supabase-js";

type PageStatus = "verifying" | "success" | "error";

function createBrowserClient() {
  return createClient(
    process.env.NEXT_PUBLIC_SUPABASE_URL ?? "",
    process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY ?? ""
  );
}

export default function ConfirmClient({
  tokenHash,
  type,
}: {
  tokenHash: string;
  type: string;
}) {
  const [status, setStatus] = useState<PageStatus>("verifying");
  const [message, setMessage] = useState("正在验证邮箱，请稍候。");
  const supabase = useMemo(() => createBrowserClient(), []);

  useEffect(() => {
    let cancelled = false;

    async function verify() {
      if (!tokenHash || !type) {
        setStatus("error");
        setMessage("确认链接不完整，请重新从邮件中打开确认链接。");
        return;
      }

      const { error } = await supabase.auth.verifyOtp({
        token_hash: tokenHash,
        type: type as EmailOtpType,
      });

      if (cancelled) {
        return;
      }

      if (error) {
        setStatus("error");
        setMessage(error.message || "邮箱确认失败，请重新发送确认邮件后再试。");
        return;
      }

      setStatus("success");
      setMessage("邮箱已确认，现在可以返回 App 或管理后台继续登录。");
    }

    void verify();
    return () => {
      cancelled = true;
    };
  }, [supabase, tokenHash, type]);

  return (
    <main className="admin-standalone-page">
      <section className="admin-login-card">
        <div>
          <p className="admin-kicker">NewStart Cloud</p>
          <h1 className="admin-page-title">邮箱确认</h1>
          <p className="admin-page-subtitle">{message}</p>
        </div>

        {status === "verifying" ? (
          <p className="admin-page-subtitle">请保持当前页面打开，系统正在完成确认。</p>
        ) : null}

        {status !== "verifying" ? (
          <div className="admin-button-row">
            <Link className="admin-primary-button" href="/login">
              前往登录
            </Link>
          </div>
        ) : null}
      </section>
    </main>
  );
}
