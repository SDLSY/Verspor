import { z } from "zod";
import { createTraceId } from "@/lib/http";
import { generateStructuredText } from "@/lib/ai/openai-compatible";
import {
  generateDoubaoSpeechSynthesis,
  type SpeechSynthesisResult,
} from "@/lib/ai/doubao-tts";
import {
  buildImageGenerationPrompt,
  buildSpeechRewritePrompt,
  buildSpeechTranscriptionPrompt,
  buildVideoGenerationPrompt,
  type ImagePromptProfile,
  type SpeechPromptProfile,
  type VideoPromptProfile,
} from "@/lib/ai/prompts";
import {
  getAiProviderOrder,
  getProviderApiKey,
  isProviderEnabled,
  resolveLogicalModelId,
} from "@/lib/ai/config";
import type { AiLogicalModelId, AiProviderId } from "@/lib/ai/types";

const VECTOR_AUDIO_TRANSCRIPTION_URL = "https://api.vectorengine.ai/v1/audio/transcriptions";
const VECTOR_AUDIO_SPEECH_URL = "https://api.vectorengine.ai/v1/audio/speech";
const VECTOR_IMAGE_GENERATION_URL = "https://api.vectorengine.ai/v1/images/generations";
const VECTOR_VIDEO_GENERATION_URL = "https://api.vectorengine.ai/v1/video/generations";

export const SpeechTranscriptionRequestSchema = z.object({
  audioUrl: z.string().trim().url(),
  mimeType: z.string().trim().max(64).default("audio/mpeg"),
  hint: z.string().trim().max(500).default(""),
});

export const SpeechSynthesisRequestSchema = z.object({
  text: z.string().trim().min(1).max(4000),
  voice: z.string().trim().max(64).default("alloy"),
  profile: z.enum(["doctor_summary", "sleep_coach", "calm_assistant"]).default("calm_assistant"),
});

export const ImageGenerationRequestSchema = z.object({
  prompt: z.string().trim().min(1).max(4000),
  size: z.string().trim().max(32).default("1024x1024"),
  profile: z
    .enum(["medical_wellness_product", "medical_education_illustration", "clinical_ui_cover"])
    .default("medical_wellness_product"),
});

export const VideoGenerationRequestSchema = z.object({
  prompt: z.string().trim().min(1).max(4000),
  durationSec: z.number().int().min(1).max(60).default(10),
  profile: z.enum(["sleep_guidance", "recovery_demo", "calm_product_story"]).default("sleep_guidance"),
});

type ProviderSelection = {
  providerId: AiProviderId;
  modelId: string;
  traceId: string;
};

function pickProvider(logicalModelId: AiLogicalModelId, traceId?: string): ProviderSelection | null {
  const order = getAiProviderOrder();
  for (const providerId of order) {
    if (!isProviderEnabled(providerId)) {
      continue;
    }
    const modelId = resolveLogicalModelId(providerId, logicalModelId);
    if (!modelId) {
      continue;
    }
    return {
      providerId,
      modelId,
      traceId: traceId ?? createTraceId(),
    };
  }
  return null;
}

function buildJsonHeaders(providerId: AiProviderId, apiKey: string): Record<string, string> {
  const headers: Record<string, string> = {
    Authorization: `Bearer ${apiKey}`,
    "Content-Type": "application/json",
  };
  if (providerId === "vector_engine") {
    headers["HTTP-Referer"] = process.env.APP_ACCEPTANCE_BASE_URL ?? "https://newstart.local";
    headers["X-Title"] = "NewStart AI Runtime";
  }
  return headers;
}

function toDataUrl(contentType: string, bytes: ArrayBuffer): string {
  const base64 = Buffer.from(bytes).toString("base64");
  return `data:${contentType};base64,${base64}`;
}

export async function generateSpeechTranscription(
  audioUrl: string,
  mimeType: string,
  hint: string,
  traceId?: string
) {
  const sourceResponse = await fetch(audioUrl);
  if (!sourceResponse.ok) {
    throw new Error(`failed to fetch audio source: HTTP_${sourceResponse.status}`);
  }
  const audioBytes = await sourceResponse.arrayBuffer();
  return generateSpeechTranscriptionFromBytes(audioBytes, mimeType, hint, traceId);
}

export async function generateSpeechTranscriptionFromBytes(
  audioBytes: ArrayBuffer,
  mimeType: string,
  hint: string,
  traceId?: string
) {
  const selected = pickProvider("speech.asr", traceId);
  if (!selected) return null;
  const apiKey = getProviderApiKey(selected.providerId);
  if (!apiKey) return null;

  if (selected.providerId !== "vector_engine") {
    return null;
  }

  const formData = new FormData();
  formData.append("file", new Blob([audioBytes], { type: mimeType }), "speech-input.wav");
  formData.append("model", selected.modelId);
  formData.append("language", "zh");
  formData.append("prompt", buildSpeechTranscriptionPrompt(hint));

  const response = await fetch(VECTOR_AUDIO_TRANSCRIPTION_URL, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${apiKey}`,
    },
    body: formData,
  });

  const json = (await response.json()) as { text?: string; error?: { message?: string } };
  if (!response.ok) {
    throw new Error(json.error?.message ?? "speech transcription failed");
  }

  return {
    transcript: json.text ?? "",
    hint,
    audioUrl: "",
    providerId: selected.providerId,
    modelId: selected.modelId,
    traceId: selected.traceId,
    fallbackUsed: selected.providerId !== getAiProviderOrder()[0],
    status: "completed",
  };
}

async function rewriteSpeechText(
  text: string,
  profile: SpeechPromptProfile,
  traceId: string
): Promise<string> {
  const prompt = buildSpeechRewritePrompt(text, profile);
  const rewritten = await generateStructuredText({
    capability: "TextReasoning",
    logicalModelId: "text.fast",
    maxTokens: 800,
    temperature: 0.2,
    traceId,
    messages: [
      { role: "system", content: prompt.systemPrompt },
      { role: "user", content: prompt.userPrompt },
    ],
  }).catch(() => null);

  return rewritten?.content?.trim() || text;
}

export async function generateSpeechSynthesis(
  text: string,
  voice: string,
  profile: SpeechPromptProfile,
  traceId?: string
) {
  const resolvedTraceId = traceId ?? createTraceId();
  const narrationText = await rewriteSpeechText(text, profile, resolvedTraceId);

  const doubaoResult = await generateDoubaoSpeechSynthesis(
    narrationText,
    voice,
    profile,
    resolvedTraceId
  );
  if (doubaoResult) {
    return doubaoResult;
  }

  return generateVectorSpeechSynthesis(narrationText, voice, resolvedTraceId);
}

async function generateVectorSpeechSynthesis(
  narrationText: string,
  voice: string,
  traceId: string
): Promise<SpeechSynthesisResult | null> {
  const selected = pickProvider("speech.tts", traceId);
  if (!selected) return null;
  const apiKey = getProviderApiKey(selected.providerId);
  if (!apiKey) return null;

  if (selected.providerId !== "vector_engine") {
    return null;
  }

  const response = await fetch(VECTOR_AUDIO_SPEECH_URL, {
    method: "POST",
    headers: buildJsonHeaders(selected.providerId, apiKey),
    body: JSON.stringify({
      model: selected.modelId,
      input: narrationText,
      voice: resolveVectorSpeechVoice(voice),
      format: "mp3",
    }),
  });

  if (!response.ok) {
    const json = (await response.json().catch(() => ({}))) as { error?: { message?: string } };
    throw new Error(json.error?.message ?? "speech synthesis failed");
  }

  const contentType = response.headers.get("content-type") ?? "audio/mpeg";
  const bytes = await response.arrayBuffer();

  return {
    audioUrl: toDataUrl(contentType, bytes),
    text: narrationText,
    voice: resolveVectorSpeechVoice(voice),
    providerId: selected.providerId,
    modelId: selected.modelId,
    traceId: selected.traceId,
    fallbackUsed: true,
    status: "completed",
  };
}

function resolveVectorSpeechVoice(voice: string): string {
  return whenLegacyVoice(voice) ? "alloy" : voice.trim().toLowerCase() || "alloy";
}

function whenLegacyVoice(voice: string): boolean {
  return ["", "x4_yezi", "x4_lingxiaoyao_em"].includes(voice.trim().toLowerCase());
}

export async function generateImage(
  prompt: string,
  size: string,
  profile: ImagePromptProfile,
  traceId?: string
) {
  const selected = pickProvider("image.generate", traceId);
  if (!selected) return null;
  const apiKey = getProviderApiKey(selected.providerId);
  if (!apiKey) return null;

  if (selected.providerId !== "vector_engine") {
    return null;
  }

  const response = await fetch(VECTOR_IMAGE_GENERATION_URL, {
    method: "POST",
    headers: buildJsonHeaders(selected.providerId, apiKey),
    body: JSON.stringify({
      model: selected.modelId,
      prompt: buildImageGenerationPrompt(prompt, profile),
      size,
    }),
  });

  const json = (await response.json()) as {
    data?: Array<{ url?: string; b64_json?: string }>;
    error?: { message?: string };
  };
  if (!response.ok) {
    throw new Error(json.error?.message ?? "image generation failed");
  }

  const first = json.data?.[0];
  const imageUrl = first?.url ?? (first?.b64_json ? `data:image/png;base64,${first.b64_json}` : "");

  return {
    imageUrl,
    prompt: buildImageGenerationPrompt(prompt, profile),
    size,
    providerId: selected.providerId,
    modelId: selected.modelId,
    traceId: selected.traceId,
    fallbackUsed: selected.providerId !== getAiProviderOrder()[0],
    status: response.ok ? "completed" : "accepted",
  };
}

export async function generateVideo(
  prompt: string,
  durationSec: number,
  profile: VideoPromptProfile,
  traceId?: string
) {
  const selected = pickProvider("video.generate.async", traceId);
  if (!selected) return null;
  const apiKey = getProviderApiKey(selected.providerId);
  if (!apiKey) return null;

  if (selected.providerId !== "vector_engine") {
    return null;
  }

  const response = await fetch(VECTOR_VIDEO_GENERATION_URL, {
    method: "POST",
    headers: buildJsonHeaders(selected.providerId, apiKey),
    body: JSON.stringify({
      model: selected.modelId,
      prompt: buildVideoGenerationPrompt(prompt, profile, durationSec),
      duration: durationSec,
    }),
  });

  const json = (await response.json()) as {
    code?: number | string;
    message?: string;
    request_id?: string;
    data?: {
      task_id?: string;
      task_status?: string;
      created_at?: number;
      updated_at?: number;
    };
  };
  if (!response.ok) {
    throw new Error(json.message ?? "video generation failed");
  }

  return {
    jobId: json.data?.task_id ?? "",
    status: json.data?.task_status ?? "queued",
    prompt: buildVideoGenerationPrompt(prompt, profile, durationSec),
    durationSec,
    providerId: selected.providerId,
    modelId: selected.modelId,
    traceId: selected.traceId,
    fallbackUsed: selected.providerId !== getAiProviderOrder()[0],
    requestId: json.request_id ?? "",
    createdAt: json.data?.created_at ?? 0,
    updatedAt: json.data?.updated_at ?? 0,
  };
}

export function normalizeVideoJobResponse(jobId: string, traceId?: string) {
  return {
    jobId,
    status: "queued",
    videoUrl: "",
    traceId: traceId ?? createTraceId(),
  };
}
