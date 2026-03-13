# API v2 Contract (Refactor Baseline)

Base envelope for every v2 endpoint:

```json
{
  "code": 0,
  "message": "ok",
  "data": {},
  "traceId": "trace_xxx"
}
```

## Auth
- `POST /api/v2/auth/login`

## Sleep
- `POST /api/v2/sleep/upload`
- `POST /api/v2/sleep/analyze`

## Intervention
- `POST /api/v2/intervention/task`
- `POST /api/v2/intervention/execution`
- `GET /api/v2/intervention/effect?days=7`

## Report
- `GET /api/v2/report/latest`

## Model
- `POST /api/v2/model/activate`

## Job
- `POST /api/v2/job/nightly`
- `GET /api/v2/job/nightly/{jobId}`

## Legacy policy
- Legacy `/api/*` and `/api/v1/*` remain online during the Android/client cutover.
- `src/proxy.ts` now only handles shared session middleware so the old clients and `/api/v2/*` can run in parallel.
- Internal APIs under `/api/internal/*` remain available for worker/runtime flow.
