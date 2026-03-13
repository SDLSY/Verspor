import { createInternalTokenHeaders } from "@/lib/internal";

type DispatchOptions = {
  limit?: number;
  timeoutMs?: number;
};

type DispatchResult = {
  ok: boolean;
  status: number | null;
  reason: string | null;
};

function resolveWorkerUrl(req: Request): string {
  return new URL("/api/internal/worker/run", req.url).toString();
}

export async function dispatchWorkerRun(
  req: Request,
  options: DispatchOptions = {}
): Promise<DispatchResult> {
  const limit = Math.max(1, Math.min(100, Math.trunc(Number(options.limit ?? 1))));
  const timeoutMs = Math.max(200, Math.min(5000, Math.trunc(Number(options.timeoutMs ?? 900))));
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), timeoutMs);

  try {
    const response = await fetch(resolveWorkerUrl(req), {
      method: "POST",
      headers: {
        "content-type": "application/json",
        ...createInternalTokenHeaders(),
      },
      body: JSON.stringify({ limit }),
      cache: "no-store",
      signal: controller.signal,
    });

    if (!response.ok) {
      return {
        ok: false,
        status: response.status,
        reason: `worker responded ${response.status}`,
      };
    }

    return {
      ok: true,
      status: response.status,
      reason: null,
    };
  } catch (error) {
    const reason = error instanceof Error ? error.message : "worker dispatch failed";
    return {
      ok: false,
      status: null,
      reason,
    };
  } finally {
    clearTimeout(timeoutId);
  }
}
