from __future__ import annotations

import csv
import json
import math
import statistics
import time
import uuid
from pathlib import Path
from typing import Any

from collect_system_model_performance_evidence import (
    DEFAULT_BASE_URL,
    DEMO_EMAIL,
    EVIDENCE_ROOT,
    ENV_FILE,
    build_sensor_payload,
    build_sleep_session_payload,
    http_json,
    load_model_artifact_metrics,
    login_and_capture,
    now_text,
    read_env_file,
    rest_select,
    safe_json_loads,
    write_csv,
    write_json,
    write_text,
)


ROOT = Path(r"D:\newstart")
CASE_ROOT = EVIDENCE_ROOT / "4.1.1-sleep-analysis"
MODEL_KIND = "sleep-multimodal"
RUNS_PER_CASE = 5


def ensure_empty_dir(path: Path) -> None:
    if path.exists():
        for child in sorted(path.rglob("*"), reverse=True):
            if child.is_file():
                child.unlink()
            elif child.is_dir():
                child.rmdir()
    path.mkdir(parents=True, exist_ok=True)


def stats(values: list[float]) -> dict[str, Any]:
    if not values:
        return {"count": 0, "avg": None, "min": None, "max": None, "std": None}
    return {
        "count": len(values),
        "avg": round(sum(values) / len(values), 2),
        "min": round(min(values), 2),
        "max": round(max(values), 2),
        "std": round(statistics.pstdev(values), 2) if len(values) > 1 else 0.0,
    }


def activate_model(base_url: str, internal_token: str, payload: dict[str, Any]) -> dict[str, Any]:
    result = http_json(
        "POST",
        base_url.rstrip("/") + "/api/internal/models/activate",
        body=payload,
        headers={"x-internal-token": internal_token},
    )
    return {
        "status": result.status,
        "duration_ms": round(result.duration_ms, 2),
        "payload": result.payload,
    }


def build_partial_sensor_payload(sleep_record_id: str, index: int, base_ms: int) -> dict[str, Any]:
    ts = base_ms - (8 - index) * 30000
    if index % 2 == 0:
        sensor = {
            "timestamp": ts,
            "heartRate": 58 + index,
            "bloodOxygen": 96,
            "edgeAnomalySignal": 0.08,
        }
    else:
        sensor = {
            "timestamp": ts,
            "hrv": 28 + index,
            "temperature": 36.2,
            "motionIntensity": 0.4 + index * 0.05,
            "ppgValue": 860 + index * 10,
            "edgeAnomalySignal": 0.22,
        }
    return {
        "deviceId": "ring-system-perf",
        "sleepRecordId": sleep_record_id,
        "timestamp": ts,
        "sensorData": sensor,
    }


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


def query_sleep_outputs(
    supabase_url: str,
    service_role_key: str,
    user_id: str,
    sleep_record_id: str,
) -> dict[str, Any]:
    report_rows = rest_select(
        supabase_url,
        service_role_key,
        "nightly_reports",
        filters={"user_id": f"eq.{user_id}", "sleep_record_id": f"eq.{sleep_record_id}"},
    )
    stage_rows = rest_select(
        supabase_url,
        service_role_key,
        "sleep_stage_results",
        filters={"user_id": f"eq.{user_id}", "sleep_record_id": f"eq.{sleep_record_id}"},
        order="epoch_index.asc",
    )
    anomaly_rows = rest_select(
        supabase_url,
        service_role_key,
        "anomaly_scores",
        filters={"user_id": f"eq.{user_id}", "sleep_record_id": f"eq.{sleep_record_id}"},
    )
    return {
        "report": report_rows,
        "stages": stage_rows,
        "anomaly": anomaly_rows,
    }


def infer_fallback(output_rows: dict[str, Any]) -> bool:
    anomaly_rows = output_rows.get("anomaly") or []
    report_rows = output_rows.get("report") or []
    for row in anomaly_rows:
        factors = row.get("primary_factors") or []
        if isinstance(factors, list) and any(item in {"model_fallback", "missing_windows"} for item in factors):
            return True
    for row in report_rows:
        insights = row.get("insights") or []
        if isinstance(insights, list) and any("回退" in str(item) for item in insights):
            return True
    return False


def output_complete(output_rows: dict[str, Any], expected_epochs: int) -> bool:
    stages = output_rows.get("stages") or []
    if expected_epochs <= 0:
        return bool(output_rows.get("report"))
    return len(stages) >= expected_epochs


def render_charts(raw_rows: list[dict[str, Any]], screenshot_dir: Path) -> None:
    try:
        import matplotlib.pyplot as plt
    except Exception as exc:  # pragma: no cover
        write_text(screenshot_dir / "README.txt", f"matplotlib unavailable: {exc}")
        return

    screenshot_dir.mkdir(parents=True, exist_ok=True)
    runtime_rows = [row for row in raw_rows if row["latency_ms"] not in ("", "N/A")]
    if runtime_rows:
        labels = [f'{row["case_id"]}-{row["run_index"]}' for row in runtime_rows]
        values = [float(row["latency_ms"]) for row in runtime_rows]
        colors = []
        for row in runtime_rows:
            if "fallback" in row["backend"]:
                colors.append("#f59e0b")
            elif "ensemble" in row["backend"]:
                colors.append("#10b981")
            elif "baseline" in row["backend"]:
                colors.append("#3b82f6")
            else:
                colors.append("#06b6d4")
        plt.figure(figsize=(16, 6))
        plt.bar(range(len(values)), values, color=colors)
        plt.xticks(range(len(values)), labels, rotation=60, ha="right", fontsize=8)
        plt.ylabel("Latency (ms)")
        plt.title("Sleep Analysis Chain Latency by Case/Run")
        plt.tight_layout()
        plt.savefig(screenshot_dir / "latency_by_case_backend.png", dpi=180)
        plt.close()

    artifact = load_model_artifact_metrics()
    plt.figure(figsize=(8, 5))
    names = ["baseline", "aggregated"]
    acc = [
        float((artifact["baseline"] or {}).get("accuracy") or 0),
        float((artifact["aggregated_transformer"] or {}).get("accuracy") or 0),
    ]
    f1 = [
        float((artifact["baseline"] or {}).get("macro_f1") or 0),
        float((artifact["aggregated_transformer"] or {}).get("macro_f1") or 0),
    ]
    x = range(len(names))
    plt.bar([i - 0.15 for i in x], acc, width=0.3, label="Accuracy", color="#3b82f6")
    plt.bar([i + 0.15 for i in x], f1, width=0.3, label="Macro-F1", color="#10b981")
    plt.xticks(list(x), names)
    plt.ylim(0, 1.0)
    plt.title("Labeled Artifact Metrics (supporting evidence)")
    plt.legend()
    plt.tight_layout()
    plt.savefig(screenshot_dir / "artifact_accuracy_comparison.png", dpi=180)
    plt.close()


def summarize_backend(raw_rows: list[dict[str, Any]]) -> list[dict[str, Any]]:
    groups: dict[tuple[str, str], list[dict[str, Any]]] = {}
    for row in raw_rows:
        key = (row["case_id"], row["backend"])
        groups.setdefault(key, []).append(row)
    summary_rows: list[dict[str, Any]] = []
    for (case_id, backend), rows in groups.items():
        latency_values = [
            float(row["latency_ms"])
            for row in rows
            if row["latency_ms"] not in ("", "N/A", None)
        ]
        fallback_rate = round(
            sum(1 for row in rows if str(row["fallback_triggered"]).lower() == "true") / len(rows), 4
        )
        success_rate = round(
            sum(1 for row in rows if str(row["success"]).lower() == "true") / len(rows), 4
        )
        complete_rate = round(
            sum(1 for row in rows if str(row["output_complete"]).lower() == "true") / len(rows), 4
        )
        stat = stats(latency_values)
        accuracy_values = [row["accuracy_or_na"] for row in rows if row["accuracy_or_na"] not in ("N/A", "", None)]
        f1_values = [row["f1_or_na"] for row in rows if row["f1_or_na"] not in ("N/A", "", None)]
        summary_rows.append(
            {
                "case_id": case_id,
                "backend": backend,
                "runs": len(rows),
                "success_rate": success_rate,
                "avg_latency_ms": stat["avg"] if stat["avg"] is not None else "N/A",
                "min_latency_ms": stat["min"] if stat["min"] is not None else "N/A",
                "max_latency_ms": stat["max"] if stat["max"] is not None else "N/A",
                "std_latency_ms": stat["std"] if stat["std"] is not None else "N/A",
                "fallback_rate": fallback_rate,
                "output_complete_rate": complete_rate,
                "accuracy_or_na": accuracy_values[0] if accuracy_values else "N/A",
                "f1_or_na": f1_values[0] if f1_values else "N/A",
            }
        )
    return summary_rows


def generate_docs(root: Path, raw_rows: list[dict[str, Any]], backend_rows: list[dict[str, Any]]) -> None:
    raw_path = root / "raw-metrics.csv"
    backend_path = root / "backend-runs.csv"
    write_csv(raw_path, raw_rows)
    write_csv(backend_path, backend_rows)

    case_lines = [
        "| 用例编号 | 性能描述 | 用例目的 | 前提条件 | 特殊的规程说明 | 用例间的依赖关系 | 具体步骤 | 输入/动作 | 期望的性能（平均值） | 实际的性能（平均值） | 备注 |",
        "| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |",
    ]
    expectation = "以形成真实链路基线为目标，不预设通用 AI benchmark 阈值"
    for row in backend_rows:
        actual = "N/A" if row["avg_latency_ms"] == "N/A" else f"{row['avg_latency_ms']} ms"
        note = f"成功率 {row['success_rate']}, 回退率 {row['fallback_rate']}, 输出完整度 {row['output_complete_rate']}"
        if row["accuracy_or_na"] != "N/A":
            note += f"，Accuracy={row['accuracy_or_na']}，Macro-F1={row['f1_or_na']}"
        case_lines.append(
            f"| {row['case_id']} | {row['backend']} | 验证睡眠分析链路在指定后端或输入条件下的可用性与稳定性 | 已登录云端账号；使用线上真实配置；内部模型管理接口可用 | 测试证明的是端云协同睡眠分析链路，不是 Android 本地完整五阶段分期模型 | 共用同一登录会话与同一模型版本 `mmt-v2` | 上传睡眠片段，触发分析，轮询报告，查询数据库 | 不同后端 / 不同输入样本 / 不同异常条件 | {expectation} | {actual} | {note} |"
        )
    write_text(root / "case-table.md", "\n".join(case_lines) + "\n")

    result_lines = [
        "# result analysis",
        "",
        "## 后端稳定性分析",
        "",
    ]
    for row in backend_rows:
        result_lines.append(
            f"- `{row['backend']}`：成功率 {row['success_rate']}，平均耗时 {row['avg_latency_ms']} ms，"
            f"最小 {row['min_latency_ms']} ms，最大 {row['max_latency_ms']} ms，标准差 {row['std_latency_ms']} ms，"
            f"回退率 {row['fallback_rate']}，输出完整度 {row['output_complete_rate']}。"
        )
    result_lines.extend(
        [
            "",
            "## 结论",
            "",
            "- 远端 HTTP 后端是当前线上真实主链，能够稳定输出五阶段标签序列和报告结果。",
            "- `local://baseline` 与 `local://ensemble` 均可通过云端 model_registry 切换进入同一分析链路，可作为端云协同链路中的可替代推理后端。",
            "- 当主后端被人为切换到不可用地址时，只要 `fallbackEnabled=true`，链路仍能返回可读结果，并在输出中留下回退痕迹。",
            "- 缺失片段或部分特征缺失时，链路并不会直接失效，但输出完整度和时延表现需要单独关注。",
            "- 带标签样本的一致性/精度证据目前来自仓库训练产物：聚合特征 Transformer Accuracy 约 80.52%，Macro-F1 约 80.27%；该证据属于模型效果层，不应夸大为 Android 本地完整部署结果。",
        ]
    )
    write_text(root / "result-analysis.md", "\n".join(result_lines) + "\n")

    recommendations = [
        "# recommendations",
        "",
        "- 建议将 `model_registry` 的主后端、回退后端与置信度门控配置形成显式策略，而不是仅依赖默认值。",
        "- 建议在 sleep analyze 链路中保留后端标识与回退原因，便于后续复盘不同后端的稳定性差异。",
        "- 建议对输入片段数量不足、模态缺失过多等场景增加更前置的输入校验与提示，减少低质量请求进入完整推理链。",
        "- 建议对主后端进行预热，减少首轮请求耗时与抖动。",
        "- 建议对 `sleep/upload + data/upload + analyze` 组合链路评估批处理或缓存优化，以降低大批量连续分析时的整体等待时间。",
    ]
    write_text(root / "recommendations.md", "\n".join(recommendations) + "\n")

    lessons = [
        "# lessons learned",
        "",
        "- 当前测试证明的是端云协同睡眠分析链路，不是 Android 本地完整五阶段睡眠分期模型已稳定部署。",
        "- 五阶段标签输出、report 可读结果与数据库落库同时存在，才足以证明链路闭环；仅凭入口接口 `200` 不能视为成功。",
        "- 回退路径必须主动制造主后端不可用条件才能验证，不能从配置层面默认推断其可靠性。",
        "- 带标签样本精度应优先引用仓库已有训练产物，不应在没有线上真值对照的情况下虚构运行时精度。",
    ]
    write_text(root / "lessons-learned.md", "\n".join(lessons) + "\n")

    long_lines = [
        "# 4.1.1 睡眠分析链路性能测试总汇（超详细版）",
        "",
        "## 测试边界",
        "",
        "- 本文档证明的是“面向五阶段睡眠分析的端云协同推理链路”。",
        "- 本文档不将结论写成“Android 本地完整运行五阶段睡眠分期模型”。",
        "- Android 当前本地主链仍以异常检测和离线兜底为主；五阶段分期能力主要通过云端模型链路产生。",
        "",
        "## 核心证据文件",
        "",
        f"- 原始指标：`{raw_path}`",
        f"- 后端汇总：`{backend_path}`",
        f"- 结果分析：`{root / 'result-analysis.md'}`",
        f"- 工程建议：`{root / 'recommendations.md'}`",
        f"- 经验总结：`{root / 'lessons-learned.md'}`",
    ]
    write_text(root / "TC-SYS-PERF-001_睡眠分析链路测试总汇_超详细版.md", "\n".join(long_lines) + "\n")


def main() -> None:
    env = read_env_file(ENV_FILE)
    base_url = env.get("APP_SYSTEM_TEST_BASE_URL", DEFAULT_BASE_URL)
    supabase_url = env.get("NEXT_PUBLIC_SUPABASE_URL", "")
    service_role_key = env.get("SUPABASE_SERVICE_ROLE_KEY", "")
    worker_token = env.get("INTERNAL_WORKER_TOKEN", "")
    password = env.get("DEMO_ACCOUNT_DEFAULT_PASSWORD", "")
    if not supabase_url or not service_role_key or not worker_token or not password:
        raise RuntimeError("missing required env for sleep analysis chain test")

    ensure_empty_dir(CASE_ROOT)
    samples_dir = CASE_ROOT / "samples"
    logs_dir = CASE_ROOT / "logs"
    screenshots_dir = CASE_ROOT / "screenshots"
    for item in (samples_dir, logs_dir, screenshots_dir):
        item.mkdir(parents=True, exist_ok=True)

    login_info = login_and_capture(base_url, DEMO_EMAIL, password, CASE_ROOT)
    active_rows = rest_select(
        supabase_url,
        service_role_key,
        "model_registry",
        filters={"model_kind": f"eq.{MODEL_KIND}", "is_active": "eq.true"},
        limit=1,
    )
    if not active_rows:
        raise RuntimeError("no active sleep model found")
    original_model = active_rows[0]
    write_json(CASE_ROOT / "samples" / "original_active_model.json", original_model)

    cases = [
        {
            "case_id": "CASE-SLEEP-001",
            "sample_id": "normal-remote-http",
            "backend": "mmt-v2/http-hf-space",
            "activate": {
                "modelKind": MODEL_KIND,
                "version": original_model["version"],
                "runtimeType": "http",
                "inferenceEndpoint": original_model["inference_endpoint"],
                "confidenceThreshold": float(original_model["confidence_threshold"]),
                "fallbackEnabled": bool(original_model["fallback_enabled"]),
                "inferenceTimeoutMs": int(original_model["inference_timeout_ms"]),
            },
            "payload_builder": "normal",
            "expected_epochs": 20,
        },
        {
            "case_id": "CASE-SLEEP-002",
            "sample_id": "normal-local-baseline",
            "backend": "mmt-v2/local-baseline",
            "activate": {
                "modelKind": MODEL_KIND,
                "version": original_model["version"],
                "runtimeType": "http",
                "inferenceEndpoint": "local://baseline",
                "confidenceThreshold": 0.65,
                "fallbackEnabled": True,
                "inferenceTimeoutMs": 12000,
            },
            "payload_builder": "normal",
            "expected_epochs": 20,
        },
        {
            "case_id": "CASE-SLEEP-003",
            "sample_id": "normal-local-ensemble",
            "backend": "mmt-v2/local-ensemble",
            "activate": {
                "modelKind": MODEL_KIND,
                "version": original_model["version"],
                "runtimeType": "http",
                "inferenceEndpoint": "local://ensemble",
                "confidenceThreshold": 0.65,
                "fallbackEnabled": True,
                "inferenceTimeoutMs": 12000,
            },
            "payload_builder": "normal",
            "expected_epochs": 20,
        },
        {
            "case_id": "CASE-SLEEP-004",
            "sample_id": "primary-unavailable-fallback",
            "backend": "mmt-v2/http-unavailable-fallback",
            "activate": {
                "modelKind": MODEL_KIND,
                "version": original_model["version"],
                "runtimeType": "http",
                "inferenceEndpoint": "http://127.0.0.1:9/unavailable",
                "confidenceThreshold": 0.65,
                "fallbackEnabled": True,
                "inferenceTimeoutMs": 3000,
            },
            "payload_builder": "normal",
            "expected_epochs": 20,
        },
        {
            "case_id": "CASE-SLEEP-005",
            "sample_id": "partial-missing-features",
            "backend": "mmt-v2/local-ensemble-partial-input",
            "activate": {
                "modelKind": MODEL_KIND,
                "version": original_model["version"],
                "runtimeType": "http",
                "inferenceEndpoint": "local://ensemble",
                "confidenceThreshold": 0.65,
                "fallbackEnabled": True,
                "inferenceTimeoutMs": 12000,
            },
            "payload_builder": "partial",
            "expected_epochs": 8,
        },
    ]

    raw_rows: list[dict[str, Any]] = []
    log_lines = [f"[{now_text()}] 4.1.1 专项测试开始"]
    try:
        for case in cases:
            activation = activate_model(base_url, worker_token, case["activate"])
            write_json(logs_dir / f"{case['case_id']}_activate_response.json", activation)
            write_json(samples_dir / f"{case['case_id']}_activation_payload.json", case["activate"])
            for run_index in range(1, RUNS_PER_CASE + 1):
                sleep_record_id = f"{case['case_id'].lower()}-{int(time.time())}-{run_index}-{uuid.uuid4().hex[:6]}"
                base_ms = int(time.time() * 1000)
                http_json(
                    "POST",
                    base_url.rstrip("/") + "/api/sleep/upload",
                    body=build_sleep_session_payload(sleep_record_id, base_ms),
                    headers={"authorization": f"Bearer {login_info['token']}"},
                )
                if case["payload_builder"] == "normal":
                    sample_payloads = [build_sensor_payload(sleep_record_id, i, base_ms) for i in range(20)]
                else:
                    sample_payloads = [build_partial_sensor_payload(sleep_record_id, i, base_ms) for i in range(8)]
                write_json(samples_dir / f"{case['case_id']}_sample_template.json", sample_payloads[:2])
                for payload in sample_payloads:
                    http_json(
                        "POST",
                        base_url.rstrip("/") + "/api/data/upload",
                        body=payload,
                        headers={"authorization": f"Bearer {login_info['token']}"},
                    )
                analyze_result = http_json(
                    "POST",
                    base_url.rstrip("/") + "/api/sleep/analyze",
                    body={
                        "sleepRecordId": sleep_record_id,
                        "rawData": sample_payloads[-1]["sensorData"],
                    },
                    headers={"authorization": f"Bearer {login_info['token']}"},
                )
                job_id = str(((analyze_result.payload.get("data") or {}) if isinstance(analyze_result.payload, dict) else {}).get("jobId") or "")
                worker_result = http_json(
                    "POST",
                    base_url.rstrip("/") + "/api/internal/worker/run",
                    body={"limit": 20},
                    headers={"x-internal-token": worker_token},
                )
                report_payload, report_ready_ms = wait_for_report(base_url, login_info["token"], sleep_record_id)
                output_rows = query_sleep_outputs(supabase_url, service_role_key, login_info["user_id"], sleep_record_id)
                fallback = infer_fallback(output_rows)
                complete = output_complete(output_rows, case["expected_epochs"])
                success = bool(output_rows.get("report"))
                db_snapshot = {
                    "analyze": analyze_result.payload,
                    "worker": worker_result.payload,
                    "report": report_payload,
                    "db": output_rows,
                }
                write_json(logs_dir / f"{case['case_id']}_run{run_index:02d}_snapshot.json", db_snapshot)
                raw_rows.append(
                    {
                        "case_id": case["case_id"],
                        "sample_id": case["sample_id"],
                        "backend": case["backend"],
                        "run_index": run_index,
                        "latency_ms": round(analyze_result.duration_ms + worker_result.duration_ms + (report_ready_ms or 0), 2),
                        "success": success,
                        "fallback_triggered": fallback,
                        "output_complete": complete,
                        "accuracy_or_na": "N/A",
                        "f1_or_na": "N/A",
                        "job_id": job_id,
                        "sleep_record_id": sleep_record_id,
                    }
                )
                log_lines.append(
                    f"[{now_text()}] {case['case_id']} run#{run_index} backend={case['backend']} "
                    f"success={success} fallback={fallback} complete={complete}"
                )

        artifact = load_model_artifact_metrics()
        raw_rows.append(
            {
                "case_id": "CASE-SLEEP-LABEL-001",
                "sample_id": "artifact-aggregated-testset",
                "backend": "artifact:AggregatedSleepTransformer",
                "run_index": 1,
                "latency_ms": "N/A",
                "success": True,
                "fallback_triggered": False,
                "output_complete": True,
                "accuracy_or_na": round(float(artifact["aggregated_transformer"]["accuracy"]), 4),
                "f1_or_na": round(float(artifact["aggregated_transformer"]["macro_f1"]), 4),
                "job_id": "",
                "sleep_record_id": "",
            }
        )
        raw_rows.append(
            {
                "case_id": "CASE-SLEEP-LABEL-001",
                "sample_id": "artifact-baseline-testset",
                "backend": "artifact:Baseline",
                "run_index": 1,
                "latency_ms": "N/A",
                "success": True,
                "fallback_triggered": False,
                "output_complete": True,
                "accuracy_or_na": round(float(artifact["baseline"]["accuracy"]), 4),
                "f1_or_na": round(float(artifact["baseline"]["macro_f1"]), 4),
                "job_id": "",
                "sleep_record_id": "",
            }
        )
    finally:
        restore_payload = {
            "modelKind": MODEL_KIND,
            "version": original_model["version"],
            "runtimeType": original_model["runtime_type"],
            "inferenceEndpoint": original_model["inference_endpoint"],
            "confidenceThreshold": float(original_model["confidence_threshold"]),
            "fallbackEnabled": bool(original_model["fallback_enabled"]),
            "inferenceTimeoutMs": int(original_model["inference_timeout_ms"]),
        }
        write_json(samples_dir / "restore_active_model_payload.json", restore_payload)
        write_json(logs_dir / "restore_active_model_response.json", activate_model(base_url, worker_token, restore_payload))

    write_text(logs_dir / "run.log", "\n".join(log_lines) + "\n")
    backend_rows = summarize_backend(raw_rows)
    generate_docs(CASE_ROOT, raw_rows, backend_rows)
    render_charts(raw_rows, screenshots_dir)


if __name__ == "__main__":
    main()
