# Architecture Decision Record

## Purpose

Build cloud-side inference for wearable multimodal data with outputs:
- sleep staging (5 classes)
- anomaly score (0-100)

while keeping Android edge TFLite as real-time anomaly signal.

## Decisions

1. Next.js on Vercel acts as API/BFF and orchestration layer, not heavy ML runtime.
2. Supabase Postgres is the system of record for sessions, jobs, results, and reports.
3. Cloud inference runs asynchronously through queued jobs.
4. Cloudflare is the security front door for API traffic.
5. Existing Android `/api/*` contracts remain compatible in phase 1.

## Why Async Inference

- Nightly staging and anomaly analysis can exceed typical serverless request budgets.
- Async keeps ingestion fast and resilient to retries and mobile network instability.
- Result APIs can return cached completed outputs immediately.

## Phase 1 Compatibility Contract

Maintain:
- `POST /api/auth/login`
- `POST /api/sleep/upload`
- `POST /api/sleep/analyze`
- `POST /api/advice/generate`
- `POST /api/sync`

And introduce:
- `POST /api/v1/inference/nightly`
- `GET /api/v1/inference/nightly/:jobId`
- `GET /api/v1/reports/nightly/:sleepRecordId`

## Data Boundaries

- `sleep_sessions`: per-night aggregate metadata
- `sleep_windows`: windowed multimodal features and edge anomaly signal
- `inference_jobs`: queue state and retries
- `sleep_stage_results`: epoch-level 5-class stages and legacy mapping
- `anomaly_scores`: nightly anomaly score + factors
- `nightly_reports`: app-facing report payload

## Security Baseline

- Supabase Auth JWT for identity
- RLS on all business tables with `auth.uid() = user_id`
- service-role key server-only
- Cloudflare WAF + rate limit for auth/upload/analyze endpoints
- no cache for dynamic `/api/*` paths

## Model Versioning

Each job and result must carry:
- `model_version`
- feature schema version (to avoid incompatible replays)

Rollback policy:
- mark previous model version active
- process only new sessions with active version
- optionally requeue impacted sessions for backfill

## Stage Mapping Strategy

Cloud standard classes:
- `WAKE`, `N1`, `N2`, `N3`, `REM`

Legacy app compatibility:
- `WAKE -> AWAKE`
- `N1/N2 -> LIGHT`
- `N3 -> DEEP`
- `REM -> REM`

## Operational SLOs

- upload success rate >= 99%
- inference job success rate >= 98%
- report query p95 < 3s (excluding queue wait)
- unauthorized cross-user read/write should be blocked by RLS
