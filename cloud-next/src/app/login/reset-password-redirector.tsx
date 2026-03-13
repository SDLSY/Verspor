"use client";

import { useEffect } from "react";

function shouldRedirectToResetPassword(search: string, hash: string): boolean {
  const searchParams = new URLSearchParams(search);
  const searchType = searchParams.get("type") ?? "";
  const searchCode = searchParams.get("code") ?? "";
  const searchTokenHash = searchParams.get("token_hash") ?? "";
  if (searchType === "recovery" && (searchCode || searchTokenHash)) {
    return true;
  }

  const normalizedHash = hash.startsWith("#") ? hash.slice(1) : hash;
  if (!normalizedHash) {
    return false;
  }

  const hashParams = new URLSearchParams(normalizedHash);
  const hashType = hashParams.get("type") ?? "";
  const accessToken = hashParams.get("access_token") ?? "";
  const refreshToken = hashParams.get("refresh_token") ?? "";
  return hashType === "recovery" && !!accessToken && !!refreshToken;
}

export default function ResetPasswordRedirector() {
  useEffect(() => {
    if (typeof window === "undefined") {
      return;
    }

    const { search, hash } = window.location;
    if (!shouldRedirectToResetPassword(search, hash)) {
      return;
    }

    window.location.replace(`/reset-password${search}${hash}`);
  }, []);

  return null;
}
