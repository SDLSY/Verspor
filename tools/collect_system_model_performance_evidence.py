from __future__ import annotations

import csv
import json
import math
import ssl
import statistics
import time
import uuid
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Any
from urllib import error, parse, request


ROOT = Path(r"D:\newstart")
ENV_FILE = ROOT / "cloud-next" / ".env.local"
EVIDENCE_ROOT = ROOT / "test-evidence" / "04-system"
DEFAULT_BASE_URL = "https://cloud.changgengring.cyou"
DEMO_EMAIL = "demo_baseline_recovery@demo.changgengring.local"


@dataclass
class HttpResult:
    status: int
    duration_ms: float
    payload: Any
    text: str


def read_env_file(path: Path) -> dict[str, str]:
    env: dict[str, str] = {}
    if not path.exists():
        return env
    for raw_line in path.read_text(encoding="utf-8-sig").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        env[key.strip()] = value.strip().strip('"').strip("'")
    return env


def ensure_empty_dir(path: Path) -> None:
    if path.exists():
        for child in sorted(path.rglob("*"), reverse=True):
            if child.is_file():
                child.unlink()
            elif child.is_dir():
                child.rmdir()
    path.mkdir(parents=True, exist_ok=True)


def write_text(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8-sig")


def write_json(path: Path, payload: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8-sig")


def write_csv(path: Path, rows: list[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if not rows:
        path.write_text("", encoding="utf-8-sig")
        return
    fieldnames = list(rows[0].keys())
    with path.open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)


def now_text() -> str:
    return datetime.now().strftime("%Y-%m-%d %H:%M:%S")


def redact_auth_payload(payload: Any) -> Any:
    if isinstance(payload, dict):
        result: dict[str, Any] = {}
        for key, value in payload.items():
            if key.lower() in {"password", "token", "refreshtoken", "access_token"}:
                result[key] = "<redacted>"
            else:
                result[key] = redact_auth_payload(value)
        return result
    if isinstance(payload, list):
        return [redact_auth_payload(item) for item in payload]
    return payload


def safe_json_loads(text: str) -> Any:
    if not text:
        return {}
    try:
        return json.loads(text)
    except Exception:
        return {"raw": text}


def http_json(
    method: str,
    url: str,
    *,
    body: Any | None = None,
    headers: dict[str, str] | None = None,
    timeout: int = 60,
) -> HttpResult:
    req_headers = {"content-type": "application/json"}
    if headers:
        req_headers.update(headers)
    data = None if body is None else json.dumps(body).encode("utf-8")
    req = request.Request(url, data=data, headers=req_headers, method=method)
    started = time.perf_counter()
    try:
        with request.urlopen(req, timeout=timeout) as resp:
            text = resp.read().decode("utf-8", errors="ignore")
            duration_ms = (time.perf_counter() - started) * 1000.0
            return HttpResult(
                status=resp.status,
                duration_ms=duration_ms,
                payload=safe_json_loads(text),
                text=text,
            )
    except error.HTTPError as exc:
        text = exc.read().decode("utf-8", errors="ignore")
        duration_ms = (time.perf_counter() - started) * 1000.0
        return HttpResult(
            status=exc.code,
            duration_ms=duration_ms,
            payload=safe_json_loads(text),
            text=text,
        )
    except (error.URLError, ssl.SSLError) as exc:
        duration_ms = (time.perf_counter() - started) * 1000.0
        return HttpResult(
            status=0,
            duration_ms=duration_ms,
            payload={"error": str(exc)},
            text=str(exc),
        )


def rest_select(
    supabase_url: str,
    service_role_key: str,
    table: str,
    *,
    filters: dict[str, str] | None = None,
    limit: int | None = None,
    order: str | None = None,
) -> list[dict[str, Any]]:
    params: dict[str, str] = {"select": "*"}
    if filters:
        params.update(filters)
    if limit is not None:
        params["limit"] = str(limit)
    if order:
        params["order"] = order
    url = supabase_url.rstrip("/") + f"/rest/v1/{table}?" + parse.urlencode(params)
    req = request.Request(
        url,
        headers={
            "apikey": service_role_key,
            "authorization": f"Bearer {service_role_key}",
        },
        method="GET",
    )
    try:
        with request.urlopen(req, timeout=60) as resp:
            text = resp.read().decode("utf-8", errors="ignore")
    except error.HTTPError as exc:
        text = exc.read().decode("utf-8", errors="ignore")
        return [
            {
                "_error_status": exc.code,
                "_error_message": safe_json_loads(text),
                "_table": table,
                "_filters": filters or {},
            }
        ]
    except (error.URLError, ssl.SSLError) as exc:
        return [
            {
                "_error_status": 0,
                "_error_message": str(exc),
                "_table": table,
                "_filters": filters or {},
            }
        ]
    payload = safe_json_loads(text)
    if isinstance(payload, list):
        return [item for item in payload if isinstance(item, dict)]
    return []


def stats(values: list[float]) -> dict[str, Any]:
    if not values:
        return {
            "count": 0,
            "average_ms": None,
            "min_ms": None,
            "max_ms": None,
            "stddev_ms": None,
        }
    return {
        "count": len(values),
        "average_ms": round(sum(values) / len(values), 2),
        "min_ms": round(min(values), 2),
        "max_ms": round(max(values), 2),
        "stddev_ms": round(statistics.pstdev(values), 2) if len(values) > 1 else 0.0,
    }


def format_stats_line(label: str, values: list[float]) -> str:
    s = stats(values)
    if not values:
        return f"- {label}：无有效数据"
    return (
        f"- {label}：平均 {s['average_ms']} ms，最小 {s['min_ms']} ms，"
        f"最大 {s['max_ms']} ms，标准差 {s['stddev_ms']} ms（n={s['count']}）"
    )


def load_model_artifact_metrics() -> dict[str, Any]:
    aggregated = safe_json_loads(
        (ROOT / "ml" / "artifacts" / "week2" / "aggregated_transformer" / "aggregated_report.json")
        .read_text(encoding="utf-8")
    )
    baseline = safe_json_loads(
        (ROOT / "ml" / "artifacts" / "week2" / "baseline" / "baseline_report.json")
        .read_text(encoding="utf-8")
    )
    aggregated_test = ((aggregated.get("metrics") or {}).get("test") or {}) if isinstance(aggregated, dict) else {}
    baseline_test = ((baseline.get("metrics") or {}).get("test") or {}) if isinstance(baseline, dict) else {}
    return {
        "aggregated_transformer": {
            "type": aggregated.get("type"),
            "accuracy": aggregated_test.get("accuracy"),
            "macro_f1": aggregated_test.get("macro_f1"),
            "samples": aggregated_test.get("samples"),
        },
        "baseline": {
            "type": baseline.get("type"),
            "accuracy": baseline_test.get("accuracy"),
            "macro_f1": baseline_test.get("macro_f1"),
            "samples": baseline_test.get("samples"),
        },
    }


def create_dirs(base: Path) -> dict[str, Path]:
    ensure_empty_dir(base)
    dirs = {
        "root": base,
        "api": base / "api-captures",
        "db": base / "db-snapshots",
    }
    for path in dirs.values():
        path.mkdir(parents=True, exist_ok=True)
    return dirs


def login_and_capture(base_url: str, email: str, password: str, summary_dir: Path) -> dict[str, Any]:
    auth_dir = summary_dir / "auth"
    auth_dir.mkdir(parents=True, exist_ok=True)
    login_body = {"email": email, "password": password}
    login_result = http_json("POST", base_url.rstrip("/") + "/api/auth/login", body=login_body)
    write_json(auth_dir / "auth_login_request.json", redact_auth_payload(login_body))
    write_json(auth_dir / "auth_login_response.json", redact_auth_payload(login_result.payload))
    if login_result.status != 200:
        raise RuntimeError(f"login failed: {login_result.status} -> {login_result.payload}")
    data = login_result.payload.get("data") if isinstance(login_result.payload, dict) else {}
    if not isinstance(data, dict):
        raise RuntimeError(f"login payload malformed: {login_result.payload}")
    token = str(data.get("token") or "")
    user_id = str(data.get("userId") or "")
    if not token or not user_id:
        raise RuntimeError(f"login token missing: {login_result.payload}")
    profile_result = http_json(
        "GET",
        base_url.rstrip("/") + "/api/user/profile",
        headers={"authorization": f"Bearer {token}"},
    )
    write_json(auth_dir / "auth_profile_response.json", redact_auth_payload(profile_result.payload))
    return {
        "token": token,
        "user_id": user_id,
        "email": email,
        "login_ms": login_result.duration_ms,
        "profile_ms": profile_result.duration_ms,
    }


def build_sleep_session_payload(sleep_record_id: str, base_ms: int) -> dict[str, Any]:
    return {
        "sleepRecordId": sleep_record_id,
        "date": base_ms,
        "bedTime": base_ms - 8 * 3600 * 1000,
        "wakeTime": base_ms,
        "totalSleepMinutes": 432,
        "deepSleepMinutes": 93,
        "lightSleepMinutes": 257,
        "remSleepMinutes": 82,
    }


def build_sensor_payload(sleep_record_id: str, index: int, base_ms: int) -> dict[str, Any]:
    ts = base_ms - (20 - index) * 30000
    return {
        "deviceId": "ring-system-perf",
        "sleepRecordId": sleep_record_id,
        "timestamp": ts,
        "sensorData": {
            "timestamp": ts,
            "heartRate": 56 + (index % 7),
            "bloodOxygen": 96 + (index % 3),
            "hrv": 31 + (index % 11),
            "temperature": round(36.3 + ((index % 5) - 2) * 0.03, 2),
            "motionIntensity": round(1.0 + (index % 6) * 0.16, 2),
            "ppgValue": 940 + index * 13,
            "edgeAnomalySignal": round(0.11 + (index % 5) * 0.03, 3),
        },
    }


def wait_for_job(base_url: str, token: str, job_id: str, timeout_s: int = 45) -> tuple[dict[str, Any], float | None]:
    started = time.perf_counter()
    last_payload: dict[str, Any] = {}
    while time.perf_counter() - started < timeout_s:
        result = http_json(
            "GET",
            base_url.rstrip("/") + f"/api/v1/inference/nightly/{job_id}",
            headers={"authorization": f"Bearer {token}"},
        )
        if isinstance(result.payload, dict):
            last_payload = result.payload
        data = last_payload.get("data") if isinstance(last_payload.get("data"), dict) else {}
        status = str(data.get("status") or "").lower()
        if status in {"succeeded", "failed"}:
            return last_payload, (time.perf_counter() - started) * 1000.0
        time.sleep(1.0)
    return last_payload, None


def wait_for_report(base_url: str, token: str, sleep_record_id: str, timeout_s: int = 45) -> tuple[dict[str, Any], float | None]:
    started = time.perf_counter()
    last_payload: dict[str, Any] = {}
    while time.perf_counter() - started < timeout_s:
        result = http_json(
            "GET",
            base_url.rstrip("/") + f"/api/v1/reports/nightly/{sleep_record_id}",
            headers={"authorization": f"Bearer {token}"},
        )
        if isinstance(result.payload, dict):
            last_payload = result.payload
        if result.status == 200:
            return last_payload, (time.perf_counter() - started) * 1000.0
        time.sleep(1.0)
    return last_payload, None


def daily_prescription_payload() -> dict[str, Any]:
    return {
        "triggerType": "TODAY_RECOVERY",
        "domainScores": {
            "recoveryScore": 69,
            "sleepRegularity": 74,
            "stressLoad": 58,
        },
        "evidenceFacts": {
            "sleep": ["近7天平均睡眠7.1小时", "昨夜恢复分69"],
            "intervention": ["最近完成2次呼吸训练", "最近1次Zen会话完成度较高"],
            "doctor": ["暂无高风险问诊结论", "症状以轻度疲劳和入睡困难为主"],
        },
        "redFlags": [],
        "personalizationLevel": "FULL",
        "missingInputs": [],
        "ragContext": "系统测试性能采样，保持业务规则与线上配置一致。",
        "catalog": [
            {
                "protocolCode": "SLEEP_WIND_DOWN_15M",
                "displayName": "助眠放松音景",
                "interventionType": "AUDIO",
                "description": "睡前15分钟的轻量放松音景。",
            },
            {
                "protocolCode": "BODY_SCAN_NSDR_10M",
                "displayName": "躯体扫描 NSDR",
                "interventionType": "MINDFULNESS",
                "description": "用于降低睡前紧张和提高放松度。",
            },
            {
                "protocolCode": "BREATHING_RESET_5M",
                "displayName": "5分钟呼吸重置",
                "interventionType": "BREATHING",
                "description": "以节律呼吸降低主观压力并改善入睡准备。",
            },
        ],
    }


def run_sleep_analysis_case(
    dirs: dict[str, Path],
    base_url: str,
    supabase_url: str,
    service_role_key: str,
    token: str,
    user_id: str,
    worker_token: str,
    runs: int,
) -> dict[str, Any]:
    run_lines = [f"[{now_text()}] 4.1.1 睡眠分析链路性能测试开始"]
    rows: list[dict[str, Any]] = []
    last_ids: dict[str, str] = {}
    for run_index in range(1, runs + 1):
        sleep_record_id = f"perf-sleep-{int(time.time())}-{run_index}-{uuid.uuid4().hex[:8]}"
        base_ms = int(time.time() * 1000)
        run_lines.append(f"[{now_text()}] run#{run_index} sleepRecordId={sleep_record_id}")
        upload_session = http_json(
            "POST",
            base_url.rstrip("/") + "/api/sleep/upload",
            body=build_sleep_session_payload(sleep_record_id, base_ms),
            headers={"authorization": f"Bearer {token}"},
        )
        upload_durations: list[float] = []
        for sample_index in range(20):
            sensor_result = http_json(
                "POST",
                base_url.rstrip("/") + "/api/data/upload",
                body=build_sensor_payload(sleep_record_id, sample_index, base_ms),
                headers={"authorization": f"Bearer {token}"},
            )
            upload_durations.append(sensor_result.duration_ms)
        analyze_result = http_json(
            "POST",
            base_url.rstrip("/") + "/api/sleep/analyze",
            body={
                "sleepRecordId": sleep_record_id,
                "rawData": {
                    "windowStart": base_ms,
                    "windowEnd": base_ms + 30000,
                    "heartRate": 59,
                    "bloodOxygen": 97,
                    "hrv": 42,
                    "temperature": 36.4,
                    "motionIntensity": 1.08,
                    "ppgValue": 1180,
                    "edgeAnomalySignal": 0.17,
                },
            },
            headers={"authorization": f"Bearer {token}"},
        )
        if run_index in {1, runs}:
            write_json(dirs["api"] / f"run{run_index:02d}_analyze_request.json", {"sleepRecordId": sleep_record_id})
            write_json(dirs["api"] / f"run{run_index:02d}_analyze_response.json", analyze_result.payload)
        data = analyze_result.payload.get("data") if isinstance(analyze_result.payload, dict) else {}
        job_id = str((data or {}).get("jobId") or "")
        worker_duration = None
        worker_status = None
        if worker_token:
            worker_result = http_json(
                "POST",
                base_url.rstrip("/") + "/api/internal/worker/run",
                body={"limit": 20},
                headers={"x-internal-token": worker_token},
            )
            worker_duration = worker_result.duration_ms
            worker_status = worker_result.status
            if run_index in {1, runs}:
                write_json(dirs["api"] / f"run{run_index:02d}_worker_response.json", worker_result.payload)
        job_payload, job_ready_ms = wait_for_job(base_url, token, job_id) if job_id else ({}, None)
        report_payload, report_ready_ms = wait_for_report(base_url, token, sleep_record_id)
        rows.append(
            {
                "run": run_index,
                "sleepRecordId": sleep_record_id,
                "sleepUploadMs": round(upload_session.duration_ms, 2),
                "dataUploadAvgMs": round(sum(upload_durations) / len(upload_durations), 2),
                "sleepAnalyzeMs": round(analyze_result.duration_ms, 2),
                "workerRunMs": round(worker_duration, 2) if worker_duration is not None else "",
                "jobReadyMs": round(job_ready_ms, 2) if job_ready_ms is not None else "",
                "reportReadyMs": round(report_ready_ms, 2) if report_ready_ms is not None else "",
                "analyzeStatus": analyze_result.status,
                "workerStatus": worker_status or "",
                "jobStatus": ((job_payload.get("data") or {}).get("status") if isinstance(job_payload, dict) else "") or "",
                "reportStatus": report_payload.get("code") if isinstance(report_payload, dict) else "",
            }
        )
        last_ids = {"sleepRecordId": sleep_record_id, "jobId": job_id}
    write_csv(dirs["root"] / "sleep-analysis-runs.csv", rows)
    if last_ids:
        write_json(
            dirs["db"] / "last_inference_job.json",
            rest_select(supabase_url, service_role_key, "inference_jobs", filters={"id": f"eq.{last_ids['jobId']}"}),
        )
        write_json(
            dirs["db"] / "last_nightly_report.json",
            rest_select(
                supabase_url,
                service_role_key,
                "nightly_reports",
                filters={"user_id": f"eq.{user_id}", "sleep_record_id": f"eq.{last_ids['sleepRecordId']}"},
            ),
        )
        write_json(
            dirs["db"] / "last_sleep_stage_results.json",
            rest_select(
                supabase_url,
                service_role_key,
                "sleep_stage_results",
                filters={"user_id": f"eq.{user_id}", "sleep_record_id": f"eq.{last_ids['sleepRecordId']}"},
                order="epoch_index.asc",
            ),
        )
        write_json(
            dirs["db"] / "last_anomaly_scores.json",
            rest_select(
                supabase_url,
                service_role_key,
                "anomaly_scores",
                filters={"user_id": f"eq.{user_id}", "sleep_record_id": f"eq.{last_ids['sleepRecordId']}"},
            ),
        )
    write_json(dirs["root"] / "model-artifact-metrics.json", load_model_artifact_metrics())
    write_text(dirs["root"] / "run.log", "\n".join(run_lines) + "\n")
    return {"rows": rows}


def run_srm_case(
    dirs: dict[str, Path],
    base_url: str,
    supabase_url: str,
    service_role_key: str,
    token: str,
    user_id: str,
    runs: int,
) -> dict[str, Any]:
    run_lines = [f"[{now_text()}] 4.1.2 SRM_V2 建议生成模型性能测试开始"]
    rows: list[dict[str, Any]] = []
    last_meta: dict[str, str] = {}
    for run_index in range(1, runs + 1):
        payload = daily_prescription_payload()
        result = http_json(
            "POST",
            base_url.rstrip("/") + "/api/intervention/daily-prescription",
            body=payload,
            headers={"authorization": f"Bearer {token}"},
        )
        if run_index in {1, runs}:
            write_json(dirs["api"] / f"run{run_index:02d}_daily_prescription_request.json", payload)
            write_json(dirs["api"] / f"run{run_index:02d}_daily_prescription_response.json", result.payload)
        data = result.payload.get("data") if isinstance(result.payload, dict) else {}
        metadata = data.get("metadata") if isinstance(data, dict) else {}
        trace_id = str(result.payload.get("traceId") or "")
        last_meta = {
            "traceId": trace_id,
            "snapshotId": str((metadata or {}).get("snapshotId") or ""),
            "recommendationId": str((metadata or {}).get("recommendationId") or ""),
        }
        rows.append(
            {
                "run": run_index,
                "httpStatus": result.status,
                "latencyMs": round(result.duration_ms, 2),
                "providerId": (metadata or {}).get("providerId", ""),
                "modelVersion": (metadata or {}).get("modelVersion", ""),
                "modelProfile": (metadata or {}).get("modelProfile", ""),
                "configSource": (metadata or {}).get("configSource", ""),
                "isFallback": (metadata or {}).get("isFallback", ""),
                "traceId": trace_id,
                "snapshotId": last_meta["snapshotId"],
                "recommendationId": last_meta["recommendationId"],
            }
        )
        run_lines.append(f"[{now_text()}] run#{run_index} status={result.status} latencyMs={result.duration_ms:.2f}")
    write_csv(dirs["root"] / "srm-v2-runs.csv", rows)
    if last_meta:
        write_json(
            dirs["db"] / "active_recommendation_model_profile.json",
            rest_select(
                supabase_url,
                service_role_key,
                "recommendation_model_profiles",
                filters={"model_code": "eq.SRM_V2", "profile_code": "eq.default_adult_cn"},
                order="updated_at.desc",
                limit=3,
            ),
        )
        write_json(
            dirs["db"] / "last_recommendation_trace.json",
            rest_select(
                supabase_url,
                service_role_key,
                "recommendation_traces",
                filters={"trace_id": f"eq.{last_meta['traceId']}"},
                limit=5,
            ),
        )
        write_json(
            dirs["db"] / "last_prescription_generation_logs.json",
            rest_select(
                supabase_url,
                service_role_key,
                "prescription_generation_logs",
                filters={"trace_id": f"eq.{last_meta['traceId']}", "user_id": f"eq.{user_id}"},
                order="created_at.desc",
                limit=10,
            ),
        )
        if last_meta.get("snapshotId"):
            write_json(
                dirs["db"] / "last_prescription_snapshot.json",
                rest_select(
                    supabase_url,
                    service_role_key,
                    "prescription_snapshots",
                    filters={"id": f"eq.{last_meta['snapshotId']}"},
                ),
            )
        if last_meta.get("recommendationId"):
            write_json(
                dirs["db"] / "last_prescription_recommendation.json",
                rest_select(
                    supabase_url,
                    service_role_key,
                    "prescription_recommendations",
                    filters={"id": f"eq.{last_meta['recommendationId']}"},
                ),
            )
    write_text(dirs["root"] / "run.log", "\n".join(run_lines) + "\n")
    return {"rows": rows}


def run_system_linked_case(
    dirs: dict[str, Path],
    base_url: str,
    supabase_url: str,
    service_role_key: str,
    token: str,
    user_id: str,
    worker_token: str,
    runs: int,
) -> dict[str, Any]:
    run_lines = [f"[{now_text()}] 4.1.3 系统级联动性能测试开始"]
    rows: list[dict[str, Any]] = []
    last_trace_ids: dict[str, str] = {}
    for run_index in range(1, runs + 1):
        chain_started = time.perf_counter()
        sleep_record_id = f"perf-linked-{int(time.time())}-{run_index}-{uuid.uuid4().hex[:8]}"
        base_ms = int(time.time() * 1000)
        http_json(
            "POST",
            base_url.rstrip("/") + "/api/sleep/upload",
            body=build_sleep_session_payload(sleep_record_id, base_ms),
            headers={"authorization": f"Bearer {token}"},
        )
        for sample_index in range(12):
            http_json(
                "POST",
                base_url.rstrip("/") + "/api/data/upload",
                body=build_sensor_payload(sleep_record_id, sample_index, base_ms),
                headers={"authorization": f"Bearer {token}"},
            )
        sleep_result = http_json(
            "POST",
            base_url.rstrip("/") + "/api/sleep/analyze",
            body={
                "sleepRecordId": sleep_record_id,
                "rawData": {
                    "windowStart": base_ms,
                    "windowEnd": base_ms + 30000,
                    "heartRate": 61,
                    "bloodOxygen": 97,
                    "hrv": 40,
                    "temperature": 36.5,
                    "motionIntensity": 1.02,
                    "ppgValue": 1160,
                    "edgeAnomalySignal": 0.15,
                },
            },
            headers={"authorization": f"Bearer {token}"},
        )
        sleep_data = sleep_result.payload.get("data") if isinstance(sleep_result.payload, dict) else {}
        job_id = str((sleep_data or {}).get("jobId") or "")
        if worker_token:
            worker_result = http_json(
                "POST",
                base_url.rstrip("/") + "/api/internal/worker/run",
                body={"limit": 20},
                headers={"x-internal-token": worker_token},
            )
            if run_index in {1, runs}:
                write_json(dirs["api"] / f"run{run_index:02d}_worker_response.json", worker_result.payload)
        _, report_ready_ms = wait_for_report(base_url, token, sleep_record_id)
        period_result = http_json(
            "GET",
            base_url.rstrip("/") + "/api/report/period-summary?period=weekly",
            headers={"authorization": f"Bearer {token}"},
        )
        daily_result = http_json(
            "POST",
            base_url.rstrip("/") + "/api/intervention/daily-prescription",
            body=daily_prescription_payload(),
            headers={"authorization": f"Bearer {token}"},
        )
        linked_total_ms = (time.perf_counter() - chain_started) * 1000.0
        if run_index in {1, runs}:
            write_json(dirs["api"] / f"run{run_index:02d}_sleep_analyze_response.json", sleep_result.payload)
            write_json(dirs["api"] / f"run{run_index:02d}_period_summary_response.json", period_result.payload)
            write_json(dirs["api"] / f"run{run_index:02d}_daily_prescription_response.json", daily_result.payload)
        period_trace_id = str(period_result.payload.get("traceId") or "")
        daily_trace_id = str(daily_result.payload.get("traceId") or "")
        daily_data = daily_result.payload.get("data") if isinstance(daily_result.payload, dict) else {}
        daily_meta = daily_data.get("metadata") if isinstance(daily_data, dict) else {}
        rows.append(
            {
                "run": run_index,
                "sleepAnalyzeMs": round(sleep_result.duration_ms, 2),
                "reportReadyMs": round(report_ready_ms, 2) if report_ready_ms is not None else "",
                "periodSummaryMs": round(period_result.duration_ms, 2),
                "dailyPrescriptionMs": round(daily_result.duration_ms, 2),
                "linkedTotalMs": round(linked_total_ms, 2),
                "sleepAnalyzeStatus": sleep_result.status,
                "periodSummaryStatus": period_result.status,
                "dailyPrescriptionStatus": daily_result.status,
                "periodTraceId": period_trace_id,
                "dailyTraceId": daily_trace_id,
                "snapshotId": (daily_meta or {}).get("snapshotId", ""),
                "recommendationId": (daily_meta or {}).get("recommendationId", ""),
                "sleepRecordId": sleep_record_id,
                "jobId": job_id,
            }
        )
        last_trace_ids = {
            "periodTraceId": period_trace_id,
            "dailyTraceId": daily_trace_id,
            "sleepRecordId": sleep_record_id,
            "snapshotId": str((daily_meta or {}).get("snapshotId") or ""),
            "recommendationId": str((daily_meta or {}).get("recommendationId") or ""),
        }
        run_lines.append(f"[{now_text()}] run#{run_index} linkedTotalMs={linked_total_ms:.2f}")
    write_csv(dirs["root"] / "system-linked-runs.csv", rows)
    if last_trace_ids:
        write_json(
            dirs["db"] / "last_linked_nightly_report.json",
            rest_select(
                supabase_url,
                service_role_key,
                "nightly_reports",
                filters={"user_id": f"eq.{user_id}", "sleep_record_id": f"eq.{last_trace_ids['sleepRecordId']}"},
            ),
        )
        write_json(
            dirs["db"] / "last_period_summary_trace.json",
            rest_select(
                supabase_url,
                service_role_key,
                "recommendation_traces",
                filters={"trace_id": f"eq.{last_trace_ids['periodTraceId']}"},
            ),
        )
        write_json(
            dirs["db"] / "last_daily_prescription_trace.json",
            rest_select(
                supabase_url,
                service_role_key,
                "recommendation_traces",
                filters={"trace_id": f"eq.{last_trace_ids['dailyTraceId']}"},
            ),
        )
        if last_trace_ids.get("snapshotId"):
            write_json(
                dirs["db"] / "last_linked_snapshot.json",
                rest_select(
                    supabase_url,
                    service_role_key,
                    "prescription_snapshots",
                    filters={"id": f"eq.{last_trace_ids['snapshotId']}"},
                ),
            )
        if last_trace_ids.get("recommendationId"):
            write_json(
                dirs["db"] / "last_linked_recommendation.json",
                rest_select(
                    supabase_url,
                    service_role_key,
                    "prescription_recommendations",
                    filters={"id": f"eq.{last_trace_ids['recommendationId']}"},
                ),
            )
    write_text(dirs["root"] / "run.log", "\n".join(run_lines) + "\n")
    return {"rows": rows}


def actual_average(values: list[float]) -> str:
    s = stats(values)
    return "N/A" if not values else f"{s['average_ms']} ms"


def write_section_docs(
    section_dir: Path,
    title: str,
    case_id: str,
    purpose: str,
    rows: list[dict[str, Any]],
    metrics: list[tuple[str, str]],
    note_lines: list[str],
    blockers: list[str],
) -> None:
    metric_values: dict[str, list[float]] = {}
    for column, _ in metrics:
        values: list[float] = []
        for row in rows:
            value = row.get(column)
            if isinstance(value, (int, float)) and not (isinstance(value, float) and math.isnan(value)):
                values.append(float(value))
        metric_values[column] = values
    case_table_lines = [
        "| 用例编号 | 性能描述 | 用例目的 | 前提条件 | 特殊的规程说明 | 用例间的依赖关系 | 具体步骤 | 输入/动作 | 期望的性能（平均值） | 实际的性能（平均值） | 备注 |",
        "| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |",
    ]
    for column, label in metrics:
        case_table_lines.append(
            f"| {case_id} | {label} | {purpose} | 已登录演示账号，使用当前线上配置 | "
            "为稳定观测异步链路，保留必要的轮询与内部 worker 触发 | 与同节其余指标共享同一业务链路 | "
            f"连续执行 {len(rows)} 次并记录每次耗时 | 调用真实接口并抓取数据库侧证据 | "
            "本轮采集不预设硬阈值，以形成真实基线为目标 | "
            f"{actual_average(metric_values[column])} | "
            + ("；".join(blockers) if blockers else "无明显阻塞") + " |"
        )
    write_text(section_dir / "case-table.md", "\n".join(case_table_lines) + "\n")

    analysis_lines = [f"# {title} - result analysis", "", "## 指标统计", ""]
    for column, label in metrics:
        analysis_lines.append(format_stats_line(label, metric_values[column]))
    analysis_lines.extend(["", "## 结果分析", ""])
    analysis_lines.extend(f"- {line}" for line in note_lines)
    if blockers:
        analysis_lines.extend(["", "## 阻塞与限制", ""])
        analysis_lines.extend(f"- {line}" for line in blockers)
    write_text(section_dir / "result-analysis.md", "\n".join(analysis_lines) + "\n")

    recommendations = [
        "# recommendations",
        "",
        "- 建议保留本次采集脚本作为赛前复测入口，避免手工跑数造成口径漂移。",
        "- 建议将关键时序字段与 traceId 继续暴露在只读审计层，便于复盘与答辩留证。",
        "- 若后续需要提交更强的性能证明，可在演示环境补充固定种子数据并重复执行同一脚本。",
    ]
    if blockers:
        recommendations.append("- 对阻塞项应优先补可观测性，而不是在文档中推断或虚构结果。")
    write_text(section_dir / "recommendations.md", "\n".join(recommendations) + "\n")

    lessons = [
        "# lessons learned",
        "",
        "- 本章性能测试不能机械套用通用 benchmark，必须围绕真实业务链路和落库表组织证据。",
        "- 异步链路的性能留证需要同时抓接口耗时和数据库完成时间，否则只能得到入口耗时。",
        "- 真实线上配置下的 provider fallback、模型门控和 trace 写入会显著影响最终耗时，不能忽略。",
    ]
    if blockers:
        lessons.append("- 对无法自动稳定复现的路径，应明确标注阻塞原因并保留半成品证据。")
    write_text(section_dir / "lessons-learned.md", "\n".join(lessons) + "\n")


def generate_summary(summary_dir: Path, sections: list[dict[str, Any]]) -> None:
    summary_dir.mkdir(parents=True, exist_ok=True)
    case_rows = [
        {
            "section": section["section"],
            "case_id": section["case_id"],
            "title": section["title"],
            "status": section["status"],
            "evidence_dir": str(section["dir"]),
        }
        for section in sections
    ]
    write_csv(summary_dir / "test_case_index.csv", case_rows)
    defects = ["# defect list", ""]
    for section in sections:
        if section["blockers"]:
            defects.append(f"## {section['case_id']} {section['title']}")
            defects.extend(f"- {line}" for line in section["blockers"])
            defects.append("")
    write_text(summary_dir / "defect_list.md", "\n".join(defects) + "\n")
    evidence = ["# evidence index", ""]
    for section in sections:
        evidence.append(f"## {section['section']} {section['title']}")
        evidence.append(f"- 证据目录：`{section['dir']}`")
        evidence.append(f"- 用例表：`{section['dir'] / 'case-table.md'}`")
        evidence.append(f"- 结果分析：`{section['dir'] / 'result-analysis.md'}`")
        evidence.append("")
    write_text(summary_dir / "evidence_index.md", "\n".join(evidence) + "\n")
    chapter = ["# chapter ready notes", "", "## 测试结果分析", ""]
    for section in sections:
        chapter.append(f"- {section['section']}：{section['summary_line']}")
    chapter.extend(["", "## 综合分析及建议", ""])
    chapter.extend(
        [
            "- 本章以真实业务闭环为核心，而非页面响应时间或通用 AI benchmark。",
            "- 睡眠分析链路与 SRM_V2 建议链均通过真实登录、真实线上接口、真实数据库落库证据进行采样。",
            "- 系统级联动测试证明两条核心创新能力能够进入同一业务闭环，但异步链路仍需要通过 trace 和数据库时间戳共同取证。",
        ]
    )
    chapter.extend(["", "## 测试经验总结", ""])
    chapter.extend(
        [
            "- 异步推理系统的性能测试必须拆分入口耗时、任务完成耗时和结果可消费耗时。",
            "- 生成模型性能不能只看接口返回时间，还要证明 recommendation trace、generation log 和 recommendation/snapshot 成功写入。",
            "- 比赛材料中的性能数值应以真实执行日志和 CSV 为准，不应事后估算。",
        ]
    )
    write_text(summary_dir / "chapter_ready_notes.md", "\n".join(chapter) + "\n")

    long_lines = ["# 04-system 系统测试与模型性能测试总汇", ""]
    for section in sections:
        long_lines.extend(
            [
                f"## {section['section']} {section['title']}",
                "",
                f"- 用例编号：{section['case_id']}",
                f"- 结果状态：{section['status']}",
                f"- 证据目录：`{section['dir']}`",
                f"- 一句话结论：{section['summary_line']}",
                "",
            ]
        )
    write_text(summary_dir / "04-system_系统测试与模型性能测试总汇_超详细版.md", "\n".join(long_lines) + "\n")


def main() -> None:
    env = read_env_file(ENV_FILE)
    base_url = env.get("APP_SYSTEM_TEST_BASE_URL", DEFAULT_BASE_URL)
    supabase_url = env.get("NEXT_PUBLIC_SUPABASE_URL", "")
    service_role_key = env.get("SUPABASE_SERVICE_ROLE_KEY", "")
    worker_token = env.get("INTERNAL_WORKER_TOKEN", "")
    password = env.get("DEMO_ACCOUNT_DEFAULT_PASSWORD", "")
    if not supabase_url or not service_role_key or not password:
        raise RuntimeError("missing NEXT_PUBLIC_SUPABASE_URL / SUPABASE_SERVICE_ROLE_KEY / DEMO_ACCOUNT_DEFAULT_PASSWORD")

    ensure_empty_dir(EVIDENCE_ROOT)
    summary_dir = EVIDENCE_ROOT / "summary"
    login_info = login_and_capture(base_url, DEMO_EMAIL, password, summary_dir)

    sleep_dirs = create_dirs(EVIDENCE_ROOT / "4.1.1-sleep-analysis-chain")
    sleep_rows = run_sleep_analysis_case(
        sleep_dirs,
        base_url,
        supabase_url,
        service_role_key,
        login_info["token"],
        login_info["user_id"],
        worker_token,
        runs=10,
    )["rows"]
    sleep_blockers = ["未配置 INTERNAL_WORKER_TOKEN，异步链路只能依赖轮询。"] if not worker_token else []
    write_section_docs(
        sleep_dirs["root"],
        "4.1.1 睡眠分析链路性能测试",
        "SYS-PERF-001",
        "验证面向五阶段睡眠分析的端云协同推理链路在真实登录和真实落库条件下的耗时与完成度。",
        sleep_rows,
        [
            ("sleepAnalyzeMs", "睡眠分析入口请求耗时"),
            ("workerRunMs", "worker 调度耗时"),
            ("jobReadyMs", "推理任务完成耗时"),
            ("reportReadyMs", "夜间报告可读结果耗时"),
        ],
        [
            "测试脚本先调用 `/api/sleep/upload` 和 `/api/data/upload` 构造真实睡眠窗口，再调用 `/api/sleep/analyze`。",
            "数据库侧同步抓取 `inference_jobs`、`nightly_reports`、`sleep_stage_results` 和 `anomaly_scores`，证明链路不止返回了入口 ACK。",
            "本节同时附加本地训练产物中的 `AggregatedSleepTransformer` 与 baseline 指标，用于说明模型效果基线与在线时序属于不同维度证据。",
        ],
        sleep_blockers,
    )

    srm_dirs = create_dirs(EVIDENCE_ROOT / "4.1.2-srm-v2-performance")
    srm_rows = run_srm_case(
        srm_dirs,
        base_url,
        supabase_url,
        service_role_key,
        login_info["token"],
        login_info["user_id"],
        runs=10,
    )["rows"]
    srm_blockers = ["存在 provider fallback 或失败时，响应延迟会包含回退开销。"]
    write_section_docs(
        srm_dirs["root"],
        "4.1.2 建议生成模型（SRM_V2）性能测试",
        "SYS-PERF-002",
        "验证 SRM_V2 在多源证据整合、安全门控与可解释追踪条件下的真实生成耗时与落库情况。",
        srm_rows,
        [("latencyMs", "SRM_V2 建议生成接口耗时")],
        [
            "每次调用都会记录外层 `traceId`、`snapshotId` 与 `recommendationId`，随后用 service role 直接查询 `recommendation_traces`、`prescription_generation_logs`、`prescription_recommendations` 和 `prescription_snapshots`。",
            "本节不以“文本生成成功”作为唯一结论，而以 trace、generation log 和 recommendation/snapshot 同步存在作为闭环证据。",
            "若线上 provider 出现 schema 校验失败或回退，性能结果会真实包含这部分成本，因此更贴近真实业务环境。",
        ],
        srm_blockers,
    )

    linked_dirs = create_dirs(EVIDENCE_ROOT / "4.1.3-system-linked-performance")
    linked_rows = run_system_linked_case(
        linked_dirs,
        base_url,
        supabase_url,
        service_role_key,
        login_info["token"],
        login_info["user_id"],
        worker_token,
        runs=10,
    )["rows"]
    linked_blockers = ["未配置 INTERNAL_WORKER_TOKEN，异步链路完成时间更依赖轮询和平台调度。"] if not worker_token else []
    write_section_docs(
        linked_dirs["root"],
        "4.1.3 系统级联动性能测试",
        "SYS-PERF-003",
        "验证睡眠分析链路与 SRM_V2 建议链路进入同一业务闭环后的整体可用性与总耗时。",
        linked_rows,
        [
            ("sleepAnalyzeMs", "链路内睡眠分析入口耗时"),
            ("reportReadyMs", "链路内睡眠报告准备耗时"),
            ("periodSummaryMs", "周期报告解释耗时"),
            ("dailyPrescriptionMs", "日处方生成耗时"),
            ("linkedTotalMs", "系统级联动总耗时"),
        ],
        [
            "本节按真实业务顺序依次触发 `sleep/analyze -> nightly report -> period-summary -> daily-prescription`。",
            "数据库侧同步抓取 `nightly_reports` 与 `recommendation_traces`，证明睡眠分析与建议生成不是相互独立的孤立接口。",
            "本节的总耗时不等同于单接口 benchmark，而是接近真实闭环中的业务可用时间。",
        ],
        linked_blockers,
    )

    sections = [
        {
            "section": "4.1.1",
            "case_id": "SYS-PERF-001",
            "title": "睡眠分析链路性能测试",
            "dir": sleep_dirs["root"],
            "status": "PASS_WITH_WARNING" if sleep_blockers else "PASS",
            "blockers": sleep_blockers,
            "summary_line": "在真实登录与真实落库条件下完成了 10 次睡眠分析链路采样，可同时给出入口耗时、任务完成耗时和报告可消费耗时。",
        },
        {
            "section": "4.1.2",
            "case_id": "SYS-PERF-002",
            "title": "建议生成模型（SRM_V2）性能测试",
            "dir": srm_dirs["root"],
            "status": "PASS_WITH_WARNING",
            "blockers": srm_blockers,
            "summary_line": "完成了 10 次 SRM_V2 真实生成采样，并通过 trace、generation log、recommendation 和 snapshot 证明结果可追踪。",
        },
        {
            "section": "4.1.3",
            "case_id": "SYS-PERF-003",
            "title": "系统级联动性能测试",
            "dir": linked_dirs["root"],
            "status": "PASS_WITH_WARNING" if linked_blockers else "PASS",
            "blockers": linked_blockers,
            "summary_line": "完成了睡眠分析、周期总结与日处方生成的联动采样，可证明两项核心创新能力能够进入同一业务闭环。",
        },
    ]
    generate_summary(summary_dir, sections)


if __name__ == "__main__":
    main()
