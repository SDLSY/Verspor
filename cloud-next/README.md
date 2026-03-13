# cloud-next

Next.js cloud gateway for the demo edge-cloud sleep analysis chain.

## Runtime shape
- Public API and orchestration: Next.js Route Handlers
- System of record: Supabase (Postgres/Auth/RLS)
- Public deployment: Vercel
- External model runtime: Hugging Face Space or any HTTP inference endpoint

## Active demo flow
1. Android keeps calling legacy `/api/*` endpoints.
2. `POST /api/sleep/analyze` stores the latest window, enqueues a nightly job, then best-effort kicks the internal worker.
3. `POST /api/internal/worker/run` or Vercel Cron `GET /api/internal/worker/run` consumes queued jobs.
4. The worker calls the active model profile from `model_registry`.
5. Results are written into `sleep_stage_results`, `anomaly_scores`, and `nightly_reports`.

## Internal control surface
- `POST /api/internal/worker/run`
- `GET /api/internal/worker/run`
- `POST /api/internal/models/register`
- `POST /api/internal/models/activate`
- `GET /api/internal/metrics/jobs`

## Required environment variables
- `NEXT_PUBLIC_SUPABASE_URL`
- `NEXT_PUBLIC_SUPABASE_ANON_KEY`
- `SUPABASE_SERVICE_ROLE_KEY`
- `INTERNAL_WORKER_TOKEN`
- `MODEL_INFERENCE_TOKEN`

## Recommended demo environment variables
- `CRON_SECRET`
- `APP_ACCEPTANCE_BASE_URL`
- `APP_ACCEPTANCE_MODEL_HTTP_ENDPOINT`
- `APP_ACCEPTANCE_MODEL_VERSION`

## AI provider variables

Prescription generation now supports provider priority and Vector Engine as the primary provider.

Priority:
- `PRESCRIPTION_PROVIDER_PRIMARY`
- `PRESCRIPTION_PROVIDER_SECONDARY`
- `PRESCRIPTION_PROVIDER_TERTIARY`

Supported provider ids:
- `vector_engine`
- `openrouter`
- `deepseek`

Vector Engine:
- `VECTOR_ENGINE_API_KEY`
- `VECTOR_ENGINE_CHAT_COMPLETIONS_URL` (optional)
- `VECTOR_ENGINE_TEXT_FAST_MODEL`
- `VECTOR_ENGINE_TEXT_STRUCTURED_MODEL`
- `VECTOR_ENGINE_TEXT_LONG_CONTEXT_MODEL`
- `VECTOR_ENGINE_RETRIEVAL_EMBED_MODEL`
- `VECTOR_ENGINE_RETRIEVAL_RERANK_MODEL`
- `VECTOR_ENGINE_VISION_OCR_MODEL`
- `VECTOR_ENGINE_VISION_REASONING_MODEL`
- `VECTOR_ENGINE_SPEECH_ASR_MODEL`
- `VECTOR_ENGINE_SPEECH_TTS_MODEL`
- `VECTOR_ENGINE_IMAGE_GENERATION_MODEL`
- `VECTOR_ENGINE_VIDEO_GENERATION_MODEL`

OpenRouter fallback:
- `OPENROUTER_API_KEY`
- `OPENROUTER_MODEL`

## AI routes

Current AI-facing routes:

- `POST /api/doctor/turn`
- `POST /api/report/understand`
- `POST /api/ai/speech/transcribe`
- `POST /api/ai/speech/synthesize`
- `POST /api/ai/image/generate`
- `POST /api/ai/video/generate`
- `GET /api/ai/video/jobs/:jobId`

Current rollout rule:

- doctor turn and report understanding are product-facing AI routes
- speech/image/video are available through the unified registry, but still treated as non-critical capability surfaces

## Model runtime rules
- `runtime_type=http` calls the configured external model endpoint.
- `fallback_enabled=true` keeps the demo resilient when the external model is slow or unavailable.
- `confidence_threshold=0.60` gates low-confidence epochs before result write-back.

## Local validation
```bash
npm install
npm run build
npm run lint
```

## Docs
- `docs/README.md`
- `docs/api-contract.md`
- `docs/architecture.md`
- `docs/demo-deployment.md`
- `docs/implementation-backlog.md`
