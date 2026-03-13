import { NextResponse } from "next/server";
import { fail, ok, parseJsonBody } from "@/lib/http";
import {
  type LocalInferenceWindowRow,
  runLocalEnsembleInference,
} from "@/lib/local-ensemble-inference";

type InferenceBody = {
  windows?: LocalInferenceWindowRow[];
};

function readModelToken(): string {
  const value = (process.env.MODEL_INFERENCE_TOKEN ?? "").trim();
  if (value) {
    return value;
  }
  return (process.env.INTERNAL_WORKER_TOKEN ?? "").trim();
}

function requireModelToken(req: Request): NextResponse | null {
  const expected = readModelToken();
  if (!expected) {
    return NextResponse.json(fail(500, "missing model inference token"), { status: 500 });
  }

  const authHeader = req.headers.get("authorization") ?? "";
  if (authHeader.toLowerCase().startsWith("bearer ")) {
    const token = authHeader.slice(7).trim();
    if (token === expected) {
      return null;
    }
  }

  return NextResponse.json(fail(401, "unauthorized model token"), { status: 401 });
}

export async function POST(req: Request) {
  const unauthorized = requireModelToken(req);
  if (unauthorized) {
    return unauthorized;
  }

  const body = parseJsonBody<InferenceBody>(await req.json().catch(() => ({})));
  const windows = Array.isArray(body.windows) ? body.windows : [];
  if (windows.length === 0) {
    return NextResponse.json(fail(400, "windows required"), { status: 400 });
  }

  const result = runLocalEnsembleInference(windows);
  return NextResponse.json(ok("ok", result));
}
