import { NextResponse } from "next/server";
import { requireAuth } from "@/lib/auth";
import { createTraceId, fail, ok, parseJsonBody } from "@/lib/http";
import {
  SpeechTranscriptionRequestSchema,
  generateSpeechTranscriptionFromBytes,
  generateSpeechTranscription,
} from "@/lib/ai/multimodal";

export async function POST(req: Request) {
  const traceId = createTraceId();
  const auth = await requireAuth(req);
  if (!auth.authenticated) {
    return auth.response;
  }

  const contentType = req.headers.get("content-type") ?? "";
  if (contentType.startsWith("multipart/form-data")) {
    const formData = await req.formData().catch(() => null);
    if (!formData) {
      return NextResponse.json(fail(400, "invalid multipart payload", traceId), { status: 400 });
    }
    const file = formData.get("file");
    const mimeType = (formData.get("mimeType")?.toString() ?? "audio/mp4").trim();
    const hint = (formData.get("hint")?.toString() ?? "").trim();
    if (!(file instanceof File)) {
      return NextResponse.json(fail(400, "file is required", traceId), { status: 400 });
    }
    const bytes = await file.arrayBuffer();
    const result = await generateSpeechTranscriptionFromBytes(bytes, mimeType, hint, traceId).catch(
      (error) => {
        const message = error instanceof Error ? error.message : "speech transcription failed";
        return NextResponse.json(fail(503, message, traceId), { status: 503 });
      }
    );

    if (result instanceof NextResponse) {
      return result;
    }

    if (!result) {
      return NextResponse.json(fail(503, "speech transcription unavailable", traceId), {
        status: 503,
      });
    }

    return NextResponse.json(ok("ok", traceId, result));
  }

  let json: unknown;
  try {
    json = await req.json();
  } catch {
    return NextResponse.json(fail(400, "invalid json payload", traceId), { status: 400 });
  }

  const parsed = SpeechTranscriptionRequestSchema.safeParse(parseJsonBody(json));
  if (!parsed.success) {
    return NextResponse.json(fail(400, "invalid speech transcription payload", traceId), {
      status: 400,
    });
  }

  const result = await generateSpeechTranscription(
    parsed.data.audioUrl,
    parsed.data.mimeType,
    parsed.data.hint,
    traceId
  ).catch((error) => {
    const message = error instanceof Error ? error.message : "speech transcription failed";
    return NextResponse.json(fail(503, message, traceId), { status: 503 });
  });

  if (result instanceof NextResponse) {
    return result;
  }

  if (!result) {
    return NextResponse.json(fail(503, "speech transcription unavailable", traceId), {
      status: 503,
    });
  }

  return NextResponse.json(ok("ok", traceId, result));
}
