from __future__ import annotations

import json
import random
import ssl
import time
from pathlib import Path
from urllib.error import HTTPError
from urllib.error import URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen


def read_env_local(path: Path) -> dict[str, str]:
    output: dict[str, str] = {}
    if not path.exists():
        return output
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        output[key.strip()] = value.strip()
    return output


def http_json(
    method: str,
    url: str,
    body: object | None = None,
    headers: dict[str, str] | None = None,
) -> tuple[int, dict[str, object]]:
    req_headers = {"content-type": "application/json"}
    if headers:
        req_headers.update(headers)
    data = None if body is None else json.dumps(body).encode("utf-8")
    req = Request(url, data=data, headers=req_headers, method=method)
    last_error: Exception | None = None
    for attempt in range(1, 4):
        try:
            with urlopen(req, timeout=60) as resp:
                text = resp.read().decode("utf-8")
                payload = json.loads(text) if text else {}
                if isinstance(payload, dict):
                    return resp.status, payload
                return resp.status, {}
        except HTTPError as exc:
            text = exc.read().decode("utf-8", errors="ignore")
            try:
                payload = json.loads(text)
            except Exception:
                payload = {"raw": text}
            raise RuntimeError(f"{method} {url} -> {exc.code}: {payload}") from exc
        except (URLError, ssl.SSLError) as exc:
            last_error = exc
            if attempt >= 3:
                break
            time.sleep(1.2 * attempt)
    raise RuntimeError(f"{method} {url} network error: {last_error}")


def supabase_admin_create_user(
    sb_url: str, service_role: str, email: str, password: str
) -> dict[str, object]:
    _, payload = http_json(
        "POST",
        sb_url.rstrip("/") + "/auth/v1/admin/users",
        body={
            "email": email,
            "password": password,
            "email_confirm": True,
            "user_metadata": {"username": email.split("@")[0]},
        },
        headers={
            "apikey": service_role,
            "authorization": f"Bearer {service_role}",
        },
    )
    return payload


def supabase_rest_count(
    sb_url: str, service_role: str, table: str, filters: dict[str, str]
) -> int:
    query = dict(filters)
    query["select"] = "id"
    url = sb_url.rstrip("/") + f"/rest/v1/{table}?" + urlencode(query)
    req = Request(
        url,
        headers={
            "apikey": service_role,
            "authorization": f"Bearer {service_role}",
            "prefer": "count=exact",
        },
        method="GET",
    )
    with urlopen(req, timeout=60) as resp:
        _ = resp.read().decode("utf-8", errors="ignore")
        content_range = resp.headers.get("content-range", "")
        if "/" in content_range:
            return int(content_range.split("/")[-1])
    return -1


def main() -> None:
    repo_root = Path(__file__).resolve().parents[2]
    env = read_env_local(repo_root / "cloud-next" / ".env.local")

    app_base = env.get("APP_ACCEPTANCE_BASE_URL", "https://cloud-next-psi.vercel.app")
    model_http_endpoint = env.get(
        "APP_ACCEPTANCE_MODEL_HTTP_ENDPOINT",
        app_base.rstrip("/") + "/api/internal/model-runtime/baseline",
    )
    model_version = env.get("APP_ACCEPTANCE_MODEL_VERSION", "mmt-v1-http")
    sb_url = env.get("NEXT_PUBLIC_SUPABASE_URL", "")
    service_role = env.get("SUPABASE_SERVICE_ROLE_KEY", "")
    internal_token = env.get("INTERNAL_WORKER_TOKEN", "")

    if not sb_url or not service_role or not internal_token:
        raise RuntimeError("missing required env in cloud-next/.env.local")

    _, health = http_json("GET", app_base.rstrip("/") + "/api/health")

    now = int(time.time())
    email = f"accept{now}{random.randint(1000, 9999)}@mail.com"
    password = "Test1234!@#abcd"
    admin_user = supabase_admin_create_user(sb_url, service_role, email, password)

    _, login = http_json(
        "POST",
        app_base.rstrip("/") + "/api/auth/login",
        body={"email": email, "password": password},
    )
    login_data = login.get("data") if isinstance(login.get("data"), dict) else {}
    token = str(login_data.get("token", ""))
    user_id = str(login_data.get("userId", ""))
    if not token or not user_id:
        raise RuntimeError(f"login failed: {login}")

    auth_headers = {"authorization": f"Bearer {token}"}
    sleep_record_id = f"acceptsleep{now}"
    base_ts = now * 1000

    _ = http_json(
        "POST",
        app_base.rstrip("/") + "/api/sleep/upload",
        body={
            "sleepRecordId": sleep_record_id,
            "date": base_ts,
            "bedTime": base_ts - 8 * 3600 * 1000,
            "wakeTime": base_ts,
            "totalSleepMinutes": 436,
            "deepSleepMinutes": 94,
            "lightSleepMinutes": 259,
            "remSleepMinutes": 83,
        },
        headers=auth_headers,
    )

    for index in range(20):
        ts = base_ts - (20 - index) * 30000
        _ = http_json(
            "POST",
            app_base.rstrip("/") + "/api/data/upload",
            body={
                "deviceId": "ring-acceptance",
                "sleepRecordId": sleep_record_id,
                "timestamp": ts,
                "sensorData": {
                    "timestamp": ts,
                    "heartRate": 57 + (index % 7),
                    "bloodOxygen": 96 + (index % 3),
                    "hrv": 34 + (index % 10),
                    "temperature": 36.3 + ((index % 5) - 2) * 0.03,
                    "motionIntensity": 1.0 + (index % 6) * 0.18,
                    "ppgValue": 920 + index * 12,
                    "edgeAnomalySignal": round(0.10 + (index % 5) * 0.03, 3),
                },
            },
            headers=auth_headers,
        )

    _ = http_json(
        "POST",
        app_base.rstrip("/") + "/api/internal/models/register",
        body={
            "modelKind": "sleep-multimodal",
            "version": model_version,
            "runtimeType": "http",
            "inferenceEndpoint": model_http_endpoint,
            "confidenceThreshold": 0.6,
            "fallbackEnabled": True,
            "inferenceTimeoutMs": 12000,
            "activate": True,
        },
        headers={"x-internal-token": internal_token},
    )

    _, enqueued = http_json(
        "POST",
        app_base.rstrip("/") + "/api/sleep/analyze",
        body={
            "sleepRecordId": sleep_record_id,
            "rawData": {
                "windowStart": base_ts,
                "windowEnd": base_ts + 30000,
                "heartRate": 59,
                "bloodOxygen": 97,
                "hrv": 42,
                "temperature": 36.4,
                "motionIntensity": 1.1,
                "ppgValue": 1180,
                "edgeAnomalySignal": 0.17,
            },
        },
        headers=auth_headers,
    )
    job_id = str((enqueued.get("data") or {}).get("jobId", ""))

    _, worker_result = http_json(
        "POST",
        app_base.rstrip("/") + "/api/internal/worker/run",
        body={"limit": 20},
        headers={"x-internal-token": internal_token},
    )

    job_status = "unknown"
    for _ in range(12):
        _, job_payload = http_json(
            "GET",
            app_base.rstrip("/") + f"/api/v1/inference/nightly/{job_id}",
            headers=auth_headers,
        )
        job_data = (
            job_payload.get("data") if isinstance(job_payload.get("data"), dict) else {}
        )
        job_status = str(job_data.get("status", "")).lower()
        if job_status in {"succeeded", "failed"}:
            break
        time.sleep(1.0)

    _, report_payload = http_json(
        "GET",
        app_base.rstrip("/") + f"/api/v1/reports/nightly/{sleep_record_id}",
        headers=auth_headers,
    )
    report_data = (
        report_payload.get("data")
        if isinstance(report_payload.get("data"), dict)
        else {}
    )

    windows_count = supabase_rest_count(
        sb_url,
        service_role,
        "sleep_windows",
        {"user_id": f"eq.{user_id}", "sleep_record_id": f"eq.{sleep_record_id}"},
    )
    stages_count = supabase_rest_count(
        sb_url,
        service_role,
        "sleep_stage_results",
        {"user_id": f"eq.{user_id}", "sleep_record_id": f"eq.{sleep_record_id}"},
    )

    result = {
        "healthStatus": health.get("status"),
        "createdUserId": admin_user.get("id"),
        "userId": user_id,
        "sleepRecordId": sleep_record_id,
        "modelHttpEndpoint": model_http_endpoint,
        "modelVersionExpected": model_version,
        "windowsInSupabase": windows_count,
        "jobId": job_id,
        "jobStatus": job_status,
        "workerProcessed": (worker_result.get("data") or {}).get("processed"),
        "workerSucceeded": (worker_result.get("data") or {}).get("succeeded"),
        "stageRowsInSupabase": stages_count,
        "reportModelVersion": report_data.get("modelVersion"),
        "reportAnomalyScore": report_data.get("anomalyScore"),
        "reportStageCount": len(report_data.get("sleepStages5") or []),
    }

    if result["jobStatus"] != "succeeded":
        raise RuntimeError(f"job did not succeed: {result}")
    if result["reportModelVersion"] != model_version:
        raise RuntimeError(f"unexpected model version: {result}")
    if int(result["reportStageCount"]) <= 0:
        raise RuntimeError(f"empty report stages: {result}")

    out_path = repo_root / "artifacts" / "acceptance" / "end_cloud_chain.json"
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(
        json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    print(json.dumps(result, ensure_ascii=False))
    print(f"result_path={out_path}")


if __name__ == "__main__":
    main()
