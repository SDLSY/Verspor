# Implementation Backlog

## Status

- Sprint 1: done
- Sprint 2: done
- Sprint 3: done

## Sprint 1 (Foundation)

1. Initialize Next.js project runtime in `cloud-next`.
2. Wire Supabase client helpers for anon and service-role flows.
3. Implement compatibility endpoints:
   - `/api/auth/login`
   - `/api/auth/register`
   - `/api/sleep/upload`
   - `/api/sleep/analyze`
   - `/api/advice/generate`
   - `/api/sync`
4. Run Supabase migration `0001_core_schema.sql`.
5. Validate RLS policies with user-scoped reads/writes.

## Sprint 2 (Async Inference)

1. Implement job enqueue endpoint `/api/v1/inference/nightly`.
2. Implement job status endpoint `/api/v1/inference/nightly/:jobId`.
3. Implement report endpoint `/api/v1/reports/nightly/:sleepRecordId`.
4. Add worker contract for consuming queued jobs and writing results.
5. Add idempotency key enforcement for upload and analyze requests.

## Sprint 3 (Model and Security Hardening)

1. Add model registry table and activation strategy.
2. Persist model version on all inference outputs.
3. Add Cloudflare WAF and rate-limit policies.
4. Add audit event writes for sensitive read/write actions.
5. Add observability dashboards for job success and API latency.

## Definition of Done

- Android legacy `/api/*` flow remains functional.
- Cloud output includes 5-stage classes and mapped legacy stages.
- RLS prevents cross-user reads.
- Job retries are idempotent.
- Build passes in `cloud-next`.
