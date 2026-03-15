import { NextResponse } from "next/server";
import { createTraceId, fail, ok, parseJsonBody } from "@/lib/http";
import {
  SpeechSynthesisRequestSchema,
  generateSpeechSynthesis,
} from "@/lib/ai/multimodal";

export async function POST(req: Request) {
  const traceId = createTraceId();

  let json: unknown;
  try {
    json = await req.json();
  } catch {
    return NextResponse.json(fail(400, "invalid json payload", traceId), { status: 400 });
  }

  const parsed = SpeechSynthesisRequestSchema.safeParse(parseJsonBody(json));
  if (!parsed.success) {
    return NextResponse.json(fail(400, "invalid speech synthesis payload", traceId), {
      status: 400,
    });
  }

  const result = await generateSpeechSynthesis(
    parsed.data.text,
    parsed.data.voice,
    parsed.data.profile,
    traceId
  ).catch(
    (error) => {
      const message = error instanceof Error ? error.message : "speech synthesis failed";
      return NextResponse.json(fail(503, message, traceId), { status: 503 });
    }
  );

  if (result instanceof NextResponse) {
    return result;
  }

  if (!result) {
    return NextResponse.json(fail(503, "speech synthesis unavailable", traceId), {
      status: 503,
    });
  }

  return NextResponse.json(ok("ok", traceId, result));
}
