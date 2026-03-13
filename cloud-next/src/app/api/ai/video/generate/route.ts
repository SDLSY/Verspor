import { NextResponse } from "next/server";
import { requireAuth } from "@/lib/auth";
import { createTraceId, fail, ok, parseJsonBody } from "@/lib/http";
import {
  VideoGenerationRequestSchema,
  generateVideo,
} from "@/lib/ai/multimodal";

export async function POST(req: Request) {
  const traceId = createTraceId();
  const auth = await requireAuth(req);
  if (!auth.authenticated) {
    return auth.response;
  }

  let json: unknown;
  try {
    json = await req.json();
  } catch {
    return NextResponse.json(fail(400, "invalid json payload", traceId), { status: 400 });
  }

  const parsed = VideoGenerationRequestSchema.safeParse(parseJsonBody(json));
  if (!parsed.success) {
    return NextResponse.json(fail(400, "invalid video generation payload", traceId), {
      status: 400,
    });
  }

  const result = await generateVideo(
    parsed.data.prompt,
    parsed.data.durationSec,
    parsed.data.profile,
    traceId
  ).catch(
    (error) => {
      const message = error instanceof Error ? error.message : "video generation failed";
      return NextResponse.json(fail(503, message, traceId), { status: 503 });
    }
  );

  if (result instanceof NextResponse) {
    return result;
  }

  if (!result) {
    return NextResponse.json(fail(503, "video generation unavailable", traceId), {
      status: 503,
    });
  }

  return NextResponse.json(ok("accepted", traceId, result));
}
