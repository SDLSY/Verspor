import { randomUUID } from "crypto";
import { readAiEnv } from "@/lib/ai/config";
import type { SpeechPromptProfile } from "@/lib/ai/prompts";

const DOUBAO_TTS_PROVIDER_ID = "doubao";
const DOUBAO_TTS_ENDPOINT =
  "https://openspeech.bytedance.com/api/v3/tts/unidirectional";
const DOUBAO_TTS_RESOURCE_ID = "seed-tts-2.0";
const DOUBAO_TTS_MODEL_ID = "seed-tts-2.0-standard";

type DoubaoTtsConfig = {
  endpoint: string;
  appId: string;
  accessKey: string;
  resourceId: string;
  modelId: string;
  defaultSpeaker: string;
  doctorSpeaker: string;
  sleepSpeaker: string;
  calmSpeaker: string;
};

export type SpeechSynthesisResult = {
  audioUrl: string;
  text: string;
  voice: string;
  providerId: string;
  modelId: string;
  traceId: string;
  fallbackUsed: boolean;
  status: "completed";
};

type DoubaoChunk = {
  code?: unknown;
  message?: unknown;
  data?: unknown;
};

function readDoubaoTtsConfig(): DoubaoTtsConfig | null {
  const appId = readAiEnv("DOUBAO_TTS_APP_ID") ?? readAiEnv("DOUBAO_TTS_APP_KEY");
  const accessKey = readAiEnv("DOUBAO_TTS_ACCESS_KEY");
  const defaultSpeaker = readAiEnv("DOUBAO_TTS_DEFAULT_SPEAKER");

  if (!appId || !accessKey || !defaultSpeaker) {
    return null;
  }

  return {
    endpoint: readAiEnv("DOUBAO_TTS_ENDPOINT") ?? DOUBAO_TTS_ENDPOINT,
    appId,
    accessKey,
    resourceId: readAiEnv("DOUBAO_TTS_RESOURCE_ID") ?? DOUBAO_TTS_RESOURCE_ID,
    modelId: readAiEnv("DOUBAO_TTS_MODEL") ?? DOUBAO_TTS_MODEL_ID,
    defaultSpeaker,
    doctorSpeaker: readAiEnv("DOUBAO_TTS_DOCTOR_SPEAKER") ?? "",
    sleepSpeaker: readAiEnv("DOUBAO_TTS_SLEEP_SPEAKER") ?? "",
    calmSpeaker: readAiEnv("DOUBAO_TTS_CALM_SPEAKER") ?? "",
  };
}

function resolveProfileSpeaker(config: DoubaoTtsConfig, profile: SpeechPromptProfile): string {
  switch (profile) {
    case "doctor_summary":
      return config.doctorSpeaker || config.defaultSpeaker;
    case "sleep_coach":
      return config.sleepSpeaker || config.defaultSpeaker;
    case "calm_assistant":
      return config.calmSpeaker || config.defaultSpeaker;
  }
}

function resolveSpeakerId(
  config: DoubaoTtsConfig,
  voice: string,
  profile: SpeechPromptProfile
): string {
  const requestedVoice = voice.trim();
  const normalizedVoice = requestedVoice.toLowerCase();
  const profileSpeaker = resolveProfileSpeaker(config, profile);

  if (
    normalizedVoice === "" ||
    normalizedVoice === "alloy" ||
    normalizedVoice === "x4_yezi" ||
    normalizedVoice === "x4_lingxiaoyao_em"
  ) {
    return profileSpeaker;
  }

  switch (normalizedVoice) {
    case "doctor":
    case "doctor_summary":
      return config.doctorSpeaker || config.defaultSpeaker;
    case "sleep":
    case "sleep_coach":
      return config.sleepSpeaker || config.defaultSpeaker;
    case "calm":
    case "calm_assistant":
      return config.calmSpeaker || config.defaultSpeaker;
    default:
      return requestedVoice || profileSpeaker;
  }
}

function buildRequestHeaders(config: DoubaoTtsConfig): Record<string, string> {
  return {
    "Content-Type": "application/json",
    "X-Api-App-Id": config.appId,
    "X-Api-Access-Key": config.accessKey,
    "X-Api-Resource-Id": config.resourceId,
    "X-Api-Request-Id": randomUUID(),
    "X-Control-Require-Usage-Tokens-Return": "*",
  };
}

function extractJsonObjects(raw: string): DoubaoChunk[] {
  const chunks: DoubaoChunk[] = [];
  let start = -1;
  let depth = 0;
  let inString = false;
  let escaping = false;

  for (let index = 0; index < raw.length; index += 1) {
    const char = raw[index];

    if (inString) {
      if (escaping) {
        escaping = false;
        continue;
      }
      if (char === "\\") {
        escaping = true;
        continue;
      }
      if (char === "\"") {
        inString = false;
      }
      continue;
    }

    if (char === "\"") {
      inString = true;
      continue;
    }
    if (char === "{") {
      if (depth === 0) {
        start = index;
      }
      depth += 1;
      continue;
    }
    if (char === "}") {
      depth -= 1;
      if (depth === 0 && start >= 0) {
        const candidate = raw.slice(start, index + 1);
        try {
          chunks.push(JSON.parse(candidate) as DoubaoChunk);
        } catch {
          // Ignore malformed fragments and continue scanning the stream.
        }
        start = -1;
      }
    }
  }

  return chunks;
}

function buildErrorMessage(status: number, rawBody: string): string {
  const chunk = extractJsonObjects(rawBody).find((item) => typeof item.message === "string");
  const message =
    typeof chunk?.message === "string" && chunk.message.trim().length > 0
      ? chunk.message.trim()
      : rawBody.trim().slice(0, 240);
  return message ? `doubao tts failed: ${message}` : `doubao tts failed: HTTP_${status}`;
}

function decodeAudioFromStream(rawBody: string): Buffer {
  const audioChunks: Buffer[] = [];
  let lastMessage = "";

  for (const chunk of extractJsonObjects(rawBody)) {
    const code = typeof chunk.code === "number" ? chunk.code : 0;
    const message = typeof chunk.message === "string" ? chunk.message.trim() : "";
    if (message) {
      lastMessage = message;
    }
    if (code !== 0 && code !== 20000000) {
      throw new Error(message || `doubao tts failed: CODE_${code}`);
    }
    if (typeof chunk.data === "string" && chunk.data.trim().length > 0) {
      audioChunks.push(Buffer.from(chunk.data.trim(), "base64"));
    }
  }

  if (audioChunks.length === 0) {
    throw new Error(lastMessage || "doubao tts returned no audio");
  }

  return Buffer.concat(audioChunks);
}

function toDataUrl(contentType: string, bytes: Buffer): string {
  return `data:${contentType};base64,${bytes.toString("base64")}`;
}

export async function generateDoubaoSpeechSynthesis(
  text: string,
  voice: string,
  profile: SpeechPromptProfile,
  traceId: string
): Promise<SpeechSynthesisResult | null> {
  const config = readDoubaoTtsConfig();
  if (!config) {
    return null;
  }

  const speaker = resolveSpeakerId(config, voice, profile);
  const response = await fetch(config.endpoint, {
    method: "POST",
    headers: buildRequestHeaders(config),
    body: JSON.stringify({
      user: {
        uid: `newstart-${traceId}`,
      },
      namespace: "BidirectionalTTS",
      req_params: {
        text,
        model: config.modelId,
        speaker,
        audio_params: {
          format: "mp3",
          sample_rate: 24000,
          bit_rate: 64000,
          speech_rate: 0,
          loudness_rate: 0,
        },
        additions: JSON.stringify({
          disable_markdown_filter: true,
        }),
      },
    }),
  });

  const rawBody = await response.text();
  if (!response.ok) {
    throw new Error(buildErrorMessage(response.status, rawBody));
  }

  const audioBytes = decodeAudioFromStream(rawBody);

  return {
    audioUrl: toDataUrl("audio/mpeg", audioBytes),
    text,
    voice,
    providerId: DOUBAO_TTS_PROVIDER_ID,
    modelId: config.modelId,
    traceId,
    fallbackUsed: false,
    status: "completed",
  };
}
