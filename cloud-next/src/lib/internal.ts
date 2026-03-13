import { NextResponse } from "next/server";
import { fail } from "@/lib/http";

function readEnvToken(name: string): string {
  return (process.env[name] ?? "").trim();
}

export function readInternalWorkerToken(): string {
  const value = readEnvToken("INTERNAL_WORKER_TOKEN");
  if (!value) {
    throw new Error("missing env: INTERNAL_WORKER_TOKEN");
  }
  return value;
}

function readAllowedInternalTokens(): string[] {
  const required = readInternalWorkerToken();
  const cronSecret = readEnvToken("CRON_SECRET");
  return cronSecret && cronSecret !== required ? [required, cronSecret] : [required];
}

export function createInternalTokenHeaders(): HeadersInit {
  return {
    "x-internal-token": readInternalWorkerToken(),
  };
}

export function requireInternalToken(req: Request): NextResponse | null {
  const expectedTokens = readAllowedInternalTokens();
  const header = req.headers.get("x-internal-token") ?? "";
  if (header && expectedTokens.includes(header)) {
    return null;
  }

  const authHeader = req.headers.get("authorization") ?? "";
  if (authHeader.toLowerCase().startsWith("bearer ")) {
    const bearer = authHeader.slice(7).trim();
    if (expectedTokens.includes(bearer)) {
      return null;
    }
  }

  return NextResponse.json(fail(401, "unauthorized internal token"), { status: 401 });
}
