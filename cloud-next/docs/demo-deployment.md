# Demo Deployment Checklist

## Goal
Run the demo chain with `cloud-next` on Vercel and the model runtime on Hugging Face Space.

## Environment variables
- `NEXT_PUBLIC_SUPABASE_URL`: Supabase project URL.
- `NEXT_PUBLIC_SUPABASE_ANON_KEY`: public anon key for auth/session flows.
- `SUPABASE_SERVICE_ROLE_KEY`: server-only key for worker writes.
- `INTERNAL_WORKER_TOKEN`: shared secret for internal worker and model management endpoints.
- `CRON_SECRET`: Vercel Cron bearer secret. Set it to a value different from the public auth keys.
- `MODEL_INFERENCE_TOKEN`: bearer token that `cloud-next` sends to the HF model runtime.
- `APP_ACCEPTANCE_BASE_URL`: optional acceptance target URL.
- `APP_ACCEPTANCE_MODEL_HTTP_ENDPOINT`: optional model URL for the acceptance script.
- `APP_ACCEPTANCE_MODEL_VERSION`: optional model version for the acceptance script, default `mmt-v1-http`.

## Deploy order
1. Deploy `hf-inference` to Hugging Face Space and verify `GET /health`.
2. Set `MODEL_INFERENCE_TOKEN` in both Hugging Face Space and Vercel.
3. Deploy `cloud-next` to Vercel with `INTERNAL_WORKER_TOKEN` and `CRON_SECRET`.
4. Run Supabase migrations in order: `0001` -> `0004`.
5. Register and activate the HTTP model profile through `/api/internal/models/register`.

## Vercel Hobby note
- Vercel Hobby only supports one Cron execution per day.
- Keep `/api/sleep/analyze` best-effort worker dispatch as the primary path.
- Use the daily Cron sweep only to clean up any queued jobs that were missed by the immediate kick.
- If you move the project to Pro later, change `vercel.json` back to `*/1 * * * *`.

## Recommended active model
```json
{
  "modelKind": "sleep-multimodal",
  "version": "mmt-v1-http",
  "runtimeType": "http",
  "inferenceEndpoint": "https://<your-space>.hf.space/",
  "confidenceThreshold": 0.6,
  "fallbackEnabled": true,
  "inferenceTimeoutMs": 12000,
  "activate": true
}
```

## Validation
- `npm run build`
- `npm run lint`
- `python cloud-next/scripts/run_end_cloud_acceptance.py`

The acceptance script now validates that the final report uses the configured HTTP model version.
