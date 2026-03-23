"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import type { EmailOtpType } from "@supabase/supabase-js";
import { createClient } from "@/lib/supabase/client";

type PageStatus = "verifying" | "success" | "error";

export default function ConfirmClient({
  nextPath,
  tokenHash,
  type,
}: {
  nextPath?: string;
  tokenHash: string;
  type: string;
}) {
  const [status, setStatus] = useState<PageStatus>("verifying");
  const [message, setMessage] = useState(
    "\u6b63\u5728\u9a8c\u8bc1\u90ae\u7bb1\uff0c\u8bf7\u7a0d\u5019\u3002",
  );
  const supabase = useMemo(() => createClient(), []);

  useEffect(() => {
    let cancelled = false;

    async function verify() {
      if (!tokenHash || !type) {
        setStatus("error");
        setMessage(
          "\u786e\u8ba4\u94fe\u63a5\u4e0d\u5b8c\u6574\uff0c\u8bf7\u91cd\u65b0\u4ece\u90ae\u4ef6\u4e2d\u6253\u5f00\u786e\u8ba4\u94fe\u63a5\u3002",
        );
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
        setMessage(
          error.message ||
            "\u90ae\u7bb1\u786e\u8ba4\u5931\u8d25\uff0c\u8bf7\u91cd\u65b0\u53d1\u9001\u786e\u8ba4\u90ae\u4ef6\u540e\u518d\u8bd5\u3002",
        );
        return;
      }

      setStatus("success");
      setMessage(
        "\u90ae\u7bb1\u5df2\u786e\u8ba4\uff0c\u73b0\u5728\u53ef\u4ee5\u8fd4\u56de App \u6216\u7ba1\u7406\u540e\u53f0\u7ee7\u7eed\u767b\u5f55\u3002",
      );

      if (nextPath) {
        window.setTimeout(() => {
          window.location.assign(nextPath);
        }, 200);
      }
    }

    void verify();
    return () => {
      cancelled = true;
    };
  }, [nextPath, supabase, tokenHash, type]);

  return (
    <main className="admin-standalone-page">
      <section className="admin-login-card">
        <div>
          <p className="admin-kicker">NewStart Cloud</p>
          <h1 className="admin-page-title">\u90ae\u7bb1\u786e\u8ba4</h1>
          <p className="admin-page-subtitle">{message}</p>
        </div>

        {status === "verifying" ? (
          <p className="admin-page-subtitle">
            \u8bf7\u4fdd\u6301\u5f53\u524d\u9875\u9762\u6253\u5f00\uff0c\u7cfb\u7edf\u6b63\u5728\u5b8c\u6210\u786e\u8ba4\u3002
          </p>
        ) : null}

        {status !== "verifying" ? (
          <div className="admin-button-row">
            <Link className="admin-primary-button" href="/login">
              \u524d\u5f80\u767b\u5f55
            </Link>
          </div>
        ) : null}
      </section>
    </main>
  );
}
