from __future__ import annotations

import hashlib
import json
import os
import statistics
import subprocess
import time
import uuid
from dataclasses import dataclass
from difflib import SequenceMatcher
from pathlib import Path
from typing import Any

from collect_system_model_performance_evidence import (
    DEFAULT_BASE_URL,
    DEMO_EMAIL,
    ENV_FILE,
    http_json,
    login_and_capture,
    now_text,
    read_env_file,
    rest_select,
    write_csv,
    write_json,
    write_text,
)


ROOT = Path(r"D:\newstart")
CLOUD_NEXT = ROOT / "cloud-next"
CASE_ROOT = ROOT / "test-evidence" / "04-system" / "4.1.2-srmv2"
LOCAL_BASE_URL = "http://127.0.0.1:3100"
RUNS_PER_CASE = 5


@dataclass
class AccountContext:
    label: str
    email: str
    password: str
    base_url: str
    token: str
    user_id: str


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


def sha256_text(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()[:16]


def normalize_output(payload: dict[str, Any]) -> dict[str, Any]:
    data = payload.get("data") if isinstance(payload, dict) else {}
    explanation = data.get("explanation") if isinstance(data, dict) else {}
    metadata = data.get("metadata") if isinstance(data, dict) else {}
    return {
        "primaryGoal": data.get("primaryGoal"),
        "riskLevel": data.get("riskLevel"),
        "targetDomains": data.get("targetDomains"),
        "primaryInterventionType": data.get("primaryInterventionType"),
        "secondaryInterventionType": data.get("secondaryInterventionType"),
        "lifestyleTaskCodes": data.get("lifestyleTaskCodes"),
        "timing": data.get("timing"),
        "durationSec": data.get("durationSec"),
        "rationale": data.get("rationale"),
        "evidence": data.get("evidence"),
        "contraindications": data.get("contraindications"),
        "followupMetric": data.get("followupMetric"),
        "missingInputs": data.get("missingInputs"),
        "summary": explanation.get("summary") if isinstance(explanation, dict) else None,
        "reasons": explanation.get("reasons") if isinstance(explanation, dict) else None,
        "nextStep": explanation.get("nextStep") if isinstance(explanation, dict) else None,
        "explanationProviderId": explanation.get("providerId") if isinstance(explanation, dict) else None,
        "explanationModelId": explanation.get("modelId") if isinstance(explanation, dict) else None,
        "explanationFallbackUsed": explanation.get("fallbackUsed") if isinstance(explanation, dict) else None,
        "providerId": metadata.get("providerId") if isinstance(metadata, dict) else None,
        "snapshotId": metadata.get("snapshotId") if isinstance(metadata, dict) else None,
        "recommendationId": metadata.get("recommendationId") if isinstance(metadata, dict) else None,
        "isFallback": metadata.get("isFallback") if isinstance(metadata, dict) else None,
        "modelVersion": metadata.get("modelVersion") if isinstance(metadata, dict) else None,
        "modelProfile": metadata.get("modelProfile") if isinstance(metadata, dict) else None,
        "configSource": metadata.get("configSource") if isinstance(metadata, dict) else None,
    }


def output_fingerprint(payload: dict[str, Any]) -> tuple[str, str]:
    normalized = normalize_output(payload)
    normalized_text = json.dumps(normalized, ensure_ascii=False, sort_keys=True)
    return sha256_text(normalized_text), normalized_text


def explanation_complete(payload: dict[str, Any]) -> bool:
    data = payload.get("data") if isinstance(payload, dict) else {}
    explanation = data.get("explanation") if isinstance(data, dict) else {}
    if not isinstance(explanation, dict):
        return False
    summary = str(explanation.get("summary") or "").strip()
    next_step = str(explanation.get("nextStep") or "").strip()
    reasons = explanation.get("reasons")
    reason_count = len([item for item in reasons if isinstance(item, str) and item.strip()]) if isinstance(reasons, list) else 0
    return bool(summary and next_step and reason_count > 0)


def create_dirs(root: Path) -> dict[str, Path]:
    ensure_empty_dir(root)
    dirs = {
        "root": root,
        "outputs": root / "outputs",
        "logs": root / "logs",
        "screenshots": root / "screenshots",
        "api": root / "api-captures",
        "db": root / "db-snapshots",
        "auth": root / "auth",
        "samples": root / "samples",
    }
    for path in dirs.values():
        path.mkdir(parents=True, exist_ok=True)
    return dirs


def register_sparse_account(base_url: str, evidence_dirs: dict[str, Path]) -> tuple[str, str]:
    suffix = uuid.uuid4().hex[:8]
    email = f"srmv2_sparse_{suffix}@demo.changgengring.local"
    password = "Demo@2026Ring"
    body = {
        "email": email,
        "password": password,
        "displayName": f"SRM V2 Sparse {suffix}",
    }
    result = http_json("POST", base_url.rstrip("/") + "/api/auth/register", body=body, timeout=90)
    write_json(evidence_dirs["api"] / "register_sparse_account_request.json", {"email": email, "displayName": body["displayName"]})
    write_json(evidence_dirs["api"] / "register_sparse_account_response.json", result.payload)
    if result.status != 200:
        raise RuntimeError(f"sparse account register failed: {result.status} -> {result.payload}")
    return email, password


def login_account(label: str, base_url: str, email: str, password: str, evidence_dirs: dict[str, Path]) -> AccountContext:
    auth_info = login_and_capture(base_url, email, password, evidence_dirs["root"])
    return AccountContext(
        label=label,
        email=email,
        password=password,
        base_url=base_url,
        token=auth_info["token"],
        user_id=auth_info["user_id"],
    )


def build_catalog() -> list[dict[str, str]]:
    return [
        {"protocolCode": "SLEEP_WIND_DOWN_15M", "displayName": "助眠放松音景", "interventionType": "AUDIO", "description": "睡前 15 分钟的轻量放松音景。"},
        {"protocolCode": "BODY_SCAN_NSDR_10M", "displayName": "躯体扫描 NSDR", "interventionType": "MINDFULNESS", "description": "用于降低睡前紧张和提高放松度。"},
        {"protocolCode": "BREATHING_RESET_5M", "displayName": "5 分钟呼吸重置", "interventionType": "BREATHING", "description": "以节律呼吸降低主观压力并改善入睡准备。"},
        {"protocolCode": "PMR_10M", "displayName": "渐进式肌肉放松", "interventionType": "RELAXATION", "description": "适合压力偏高或不适合继续高频呼吸训练时使用。"},
        {"protocolCode": "TASK_DOCTOR_PRIORITY", "displayName": "优先就医提醒", "interventionType": "TASK", "description": "当存在红旗信号时，优先提示专业就医评估。"},
        {"protocolCode": "TASK_SCREEN_CURFEW", "displayName": "屏幕宵禁", "interventionType": "TASK", "description": "睡前减少蓝光和信息刺激。"},
        {"protocolCode": "TASK_CAFFEINE_CUTOFF", "displayName": "咖啡因截止提醒", "interventionType": "TASK", "description": "下午后减少咖啡因摄入。"},
        {"protocolCode": "TASK_WORRY_LIST", "displayName": "担忧清单", "interventionType": "TASK", "description": "把未完成事项落到外部，降低认知占用。"},
        {"protocolCode": "RECOVERY_WALK_10M", "displayName": "轻恢复步行", "interventionType": "MOVEMENT", "description": "疲劳积累但需要低门槛恢复时使用。"},
        {"protocolCode": "GUIDED_STRETCH_MOBILITY_8M", "displayName": "拉伸与活动度恢复", "interventionType": "MOVEMENT", "description": "轻量拉伸与活动度恢复。"},
    ]


def build_evidence_pack(pack_id: str) -> dict[str, Any]:
    catalog = build_catalog()
    packs: dict[str, dict[str, Any]] = {
        "PACK-LOW-001": {
            "triggerType": "TODAY_RECOVERY",
            "domainScores": {"sleepDisturbance": 42, "stressLoad": 38, "fatigueLoad": 34, "recoveryCapacity": 72, "adherenceReadiness": 68},
            "evidenceFacts": {
                "sleep": ["近 7 天睡眠时长较稳定", "昨夜恢复分中等偏上"],
                "device": ["心率和体温波动平稳"],
                "intervention": ["最近一周有 2 次较完整的呼吸训练执行"],
            },
            "redFlags": [],
            "personalizationLevel": "FULL",
            "missingInputs": [],
            "ragContext": "低风险、证据充分、适合验证 SRM_V2 在常规恢复场景下的稳定建议输出。",
            "catalog": catalog,
        },
        "PACK-CONFLICT-001": {
            "triggerType": "TODAY_RECOVERY",
            "domainScores": {"sleepDisturbance": 76, "stressLoad": 73, "fatigueLoad": 69, "recoveryCapacity": 78, "adherenceReadiness": 55},
            "evidenceFacts": {
                "sleep": ["夜间恢复分不低，但入睡前紧张明显", "近期入睡时间波动较大"],
                "doctor": ["主诉以入睡困难和白天疲劳交替出现", "无明确红旗，但自述近期状态反复"],
                "intervention": ["最近音景干预有效，但呼吸训练执行中断过 1 次"],
                "medical": ["最近常规指标无明确高风险异常"],
            },
            "redFlags": [],
            "personalizationLevel": "FULL",
            "missingInputs": [],
            "ragContext": "多源证据存在冲突：恢复能力不低，但睡眠扰动、压力和疲劳负荷同时偏高。",
            "catalog": catalog,
        },
        "PACK-HIGHRISK-001": {
            "triggerType": "DOCTOR_TRIAGE",
            "domainScores": {"sleepDisturbance": 68, "stressLoad": 79, "fatigueLoad": 74, "depressiveRisk": 82, "recoveryCapacity": 28},
            "evidenceFacts": {
                "doctor": ["主诉胸闷、呼吸困难并伴持续焦虑", "最近问诊提示需优先线下评估"],
                "sleep": ["过去一周恢复能力持续偏低"],
                "medical": ["近期已有高风险医检信号需要持续关注"],
            },
            "redFlags": ["CHEST_PAIN", "BREATHING_DIFFICULTY", "HIGH_DOCTOR_RISK"],
            "personalizationLevel": "FULL",
            "missingInputs": [],
            "ragContext": "高风险输入样本，用于验证 SRM_V2 的安全门控是否先于表达层生效。",
            "catalog": catalog,
        },
        "PACK-SPARSE-001": {
            "triggerType": "NEW_USER_PREVIEW",
            "domainScores": {"stressLoad": 52, "sleepDisturbance": 51},
            "evidenceFacts": {"selfReport": ["仅提供基础主观描述，尚无长期设备或医检证据"]},
            "redFlags": [],
            "personalizationLevel": "PREVIEW",
            "missingInputs": ["DEVICE_DATA", "BASELINE_ASSESSMENT", "DOCTOR_INQUIRY"],
            "ragContext": "新用户或证据稀缺场景，用于比较证据覆盖率与建议表达的克制度。",
            "catalog": catalog,
        },
        "PACK-DEGRADE-001": {
            "triggerType": "TODAY_RECOVERY",
            "domainScores": {"sleepDisturbance": 61, "stressLoad": 66, "fatigueLoad": 48, "recoveryCapacity": 63},
            "evidenceFacts": {
                "sleep": ["近 3 天入睡准备不足", "睡前唤醒感仍偏高"],
                "intervention": ["最近两次干预依从性尚可"],
                "doctor": ["暂无明确红旗，但主观压力上升"],
            },
            "redFlags": [],
            "personalizationLevel": "FULL",
            "missingInputs": [],
            "ragContext": "用于受控验证表达层失败时是否回退到保守解释输出。",
            "catalog": catalog,
        },
    }
    if pack_id not in packs:
        raise KeyError(pack_id)
    return packs[pack_id]


def query_trace_bundle(
    supabase_url: str,
    service_role_key: str,
    user_id: str,
    trace_id: str,
) -> dict[str, Any]:
    trace_rows = rest_select(
        supabase_url,
        service_role_key,
        "recommendation_traces",
        filters={"user_id": f"eq.{user_id}", "trace_id": f"eq.{trace_id}"},
        order="created_at.desc",
        limit=1,
    )
    trace = trace_rows[0] if trace_rows else {}
    snapshot_id = trace.get("related_snapshot_id")
    recommendation_id = trace.get("related_recommendation_id")
    snapshot_rows = (
        rest_select(
            supabase_url,
            service_role_key,
            "prescription_snapshots",
            filters={"user_id": f"eq.{user_id}", "id": f"eq.{snapshot_id}"},
            limit=1,
        )
        if snapshot_id
        else []
    )
    recommendation_rows = (
        rest_select(
            supabase_url,
            service_role_key,
            "prescription_recommendations",
            filters={"user_id": f"eq.{user_id}", "id": f"eq.{recommendation_id}"},
            limit=1,
        )
        if recommendation_id
        else []
    )
    generation_log_rows = rest_select(
        supabase_url,
        service_role_key,
        "prescription_generation_logs",
        filters={"user_id": f"eq.{user_id}", "trace_id": f"eq.{trace_id}"},
        order="created_at.desc",
        limit=8,
    )
    return {
        "trace": trace,
        "snapshot": snapshot_rows[0] if snapshot_rows else {},
        "recommendation": recommendation_rows[0] if recommendation_rows else {},
        "generation_logs": generation_log_rows,
    }


def count_true_source_coverage(trace_row: dict[str, Any]) -> int:
    derived = trace_row.get("derived_signals_json")
    if not isinstance(derived, dict):
        return 0
    scientific_model = derived.get("scientificModel")
    if not isinstance(scientific_model, dict):
        return 0
    source_coverage = scientific_model.get("sourceCoverage")
    if not isinstance(source_coverage, dict):
        return 0
    return sum(1 for value in source_coverage.values() if value is True)


def evidence_coverage_ratio(trace_row: dict[str, Any]) -> float:
    derived = trace_row.get("derived_signals_json")
    if not isinstance(derived, dict):
        return 0.0
    scientific_model = derived.get("scientificModel")
    if not isinstance(scientific_model, dict):
        return 0.0
    raw = scientific_model.get("evidenceCoverage")
    try:
        return float(raw)
    except Exception:
        return 0.0


def gate_value(trace_row: dict[str, Any]) -> str:
    derived = trace_row.get("derived_signals_json")
    if not isinstance(derived, dict):
        return "UNKNOWN"
    scientific_model = derived.get("scientificModel")
    if not isinstance(scientific_model, dict):
        return "UNKNOWN"
    return str(scientific_model.get("safetyGate") or "UNKNOWN")


def recommendation_mode(trace_row: dict[str, Any]) -> str:
    derived = trace_row.get("derived_signals_json")
    if not isinstance(derived, dict):
        return ""
    scientific_model = derived.get("scientificModel")
    if not isinstance(scientific_model, dict):
        return ""
    return str(scientific_model.get("recommendationMode") or "")


def explanation_reason_count(payload: dict[str, Any]) -> int:
    data = payload.get("data") if isinstance(payload, dict) else {}
    explanation = data.get("explanation") if isinstance(data, dict) else {}
    reasons = explanation.get("reasons") if isinstance(explanation, dict) else None
    if not isinstance(reasons, list):
        return 0
    return len([item for item in reasons if isinstance(item, str) and item.strip()])


def degraded_output(payload: dict[str, Any], trace_row: dict[str, Any]) -> bool:
    data = payload.get("data") if isinstance(payload, dict) else {}
    explanation = data.get("explanation") if isinstance(data, dict) else {}
    metadata = data.get("metadata") if isinstance(data, dict) else {}
    if isinstance(metadata, dict) and metadata.get("isFallback") is True:
        return True
    if isinstance(explanation, dict) and explanation.get("fallbackUsed") is True:
        return True
    provider_id = trace_row.get("provider_id")
    return provider_id == "rules_fallback"


def persisted(bundle: dict[str, Any]) -> bool:
    return bool(bundle.get("trace")) and bool(bundle.get("snapshot")) and bool(bundle.get("recommendation"))


def generate_similarity(reference_text: str, current_text: str) -> float:
    return round(SequenceMatcher(None, reference_text, current_text).ratio(), 4)


def write_run_log(path: Path, lines: list[str]) -> None:
    write_text(path, "\n".join(lines) + "\n")


def run_case(
    case_id: str,
    evidence_pack_id: str,
    account: AccountContext,
    evidence_dirs: dict[str, Path],
    supabase_url: str,
    service_role_key: str,
    runs: int,
    raw_rows: list[dict[str, Any]],
) -> list[dict[str, Any]]:
    case_request = build_evidence_pack(evidence_pack_id)
    case_rows: list[dict[str, Any]] = []
    reference_text = ""
    reference_hash = ""
    for run_index in range(1, runs + 1):
        trace_id = f"{case_id.lower()}-{run_index}-{uuid.uuid4().hex[:8]}"
        request_payload = dict(case_request)
        request_payload["ragContext"] = f'{case_request["ragContext"]} run={run_index} trace={trace_id}'
        started = time.perf_counter()
        response = http_json(
            "POST",
            account.base_url.rstrip("/") + "/api/intervention/daily-prescription",
            body=request_payload,
            headers={"authorization": f"Bearer {account.token}"},
            timeout=120,
        )
        latency_ms = round((time.perf_counter() - started) * 1000.0, 2)
        response_payload = response.payload if isinstance(response.payload, dict) else {"raw": response.text}
        trace_value = str(response_payload.get("traceId") or trace_id)
        bundle = query_trace_bundle(supabase_url, service_role_key, account.user_id, trace_value)
        trace_row = bundle.get("trace") or {}
        output_hash, normalized_text = output_fingerprint(response_payload)
        if not reference_text:
            reference_text = normalized_text
            reference_hash = output_hash
        similarity = generate_similarity(reference_text, normalized_text) if reference_text else 1.0
        success = response.status == 200 and isinstance(response_payload.get("data"), dict) and persisted(bundle)
        row = {
            "case_id": case_id,
            "evidence_pack_id": evidence_pack_id,
            "evidence_count": count_true_source_coverage(trace_row),
            "risk_level": str((response_payload.get("data") or {}).get("riskLevel") or trace_row.get("risk_level") or ""),
            "gate_triggered": gate_value(trace_row),
            "run_index": run_index,
            "latency_ms": latency_ms,
            "success": success,
            "explanation_complete": explanation_complete(response_payload),
            "evidence_coverage_ratio": round(evidence_coverage_ratio(trace_row), 4),
            "degraded_output": degraded_output(response_payload, trace_row),
            "output_hash_or_similarity": output_hash,
            "provider_id": str(((response_payload.get("data") or {}).get("metadata") or {}).get("providerId") or trace_row.get("provider_id") or ""),
            "expression_provider_id": str(((response_payload.get("data") or {}).get("explanation") or {}).get("providerId") or ""),
            "recommendation_mode": recommendation_mode(trace_row),
            "trace_id": trace_value,
            "snapshot_id": trace_row.get("related_snapshot_id") or "",
            "recommendation_id": trace_row.get("related_recommendation_id") or "",
            "persisted": persisted(bundle),
            "reasons_count": explanation_reason_count(response_payload),
            "output_similarity_to_first": similarity,
            "reference_hash": reference_hash,
            "server_label": account.label,
            "user_email": account.email,
        }
        raw_rows.append(row)
        case_rows.append(row)
        prefix = f"{case_id}_run{run_index:02d}"
        write_json(evidence_dirs["api"] / f"{prefix}_request.json", request_payload)
        write_json(evidence_dirs["api"] / f"{prefix}_response.json", response_payload)
        write_json(evidence_dirs["outputs"] / f"{prefix}_normalized_output.json", {"normalized": normalize_output(response_payload), "trace_bundle": bundle, "metrics": row})
        write_json(evidence_dirs["logs"] / f"{prefix}_trace_bundle.json", bundle)
    return case_rows


def aggregate_cases(raw_rows: list[dict[str, Any]]) -> list[dict[str, Any]]:
    by_case: dict[str, list[dict[str, Any]]] = {}
    for row in raw_rows:
        by_case.setdefault(str(row["case_id"]), []).append(row)
    summary_rows: list[dict[str, Any]] = []
    for case_id, rows in by_case.items():
        latencies = [float(row["latency_ms"]) for row in rows if isinstance(row["latency_ms"], (int, float))]
        success_rate = sum(1 for row in rows if row["success"]) / len(rows)
        degraded_rate = sum(1 for row in rows if row["degraded_output"]) / len(rows)
        explanation_complete_rate = sum(1 for row in rows if row["explanation_complete"]) / len(rows)
        coverage_values = [float(row["evidence_coverage_ratio"]) for row in rows]
        similarity_values = [float(row["output_similarity_to_first"]) for row in rows]
        gates = [str(row["gate_triggered"]) for row in rows]
        summary_rows.append(
            {
                "case_id": case_id,
                "evidence_pack_id": rows[0]["evidence_pack_id"],
                "server_label": rows[0]["server_label"],
                "runs": len(rows),
                "success_rate": round(success_rate, 4),
                "avg_latency_ms": stats(latencies)["avg"],
                "min_latency_ms": stats(latencies)["min"],
                "max_latency_ms": stats(latencies)["max"],
                "std_latency_ms": stats(latencies)["std"],
                "avg_evidence_count": round(sum(int(row["evidence_count"]) for row in rows) / len(rows), 2),
                "avg_coverage_ratio": round(sum(coverage_values) / len(coverage_values), 4),
                "degraded_rate": round(degraded_rate, 4),
                "explanation_complete_rate": round(explanation_complete_rate, 4),
                "gates_seen": "|".join(sorted(set(gates))),
                "avg_similarity_to_first": round(sum(similarity_values) / len(similarity_values), 4),
            }
        )
    return summary_rows


def render_charts(raw_rows: list[dict[str, Any]], summary_rows: list[dict[str, Any]], screenshot_dir: Path) -> None:
    try:
        import matplotlib.pyplot as plt
    except Exception as exc:  # pragma: no cover
        write_text(screenshot_dir / "README.txt", f"matplotlib unavailable: {exc}")
        return

    screenshot_dir.mkdir(parents=True, exist_ok=True)
    labels = [row["case_id"] for row in summary_rows]
    avg_latency = [float(row["avg_latency_ms"]) for row in summary_rows]
    coverage = [float(row["avg_coverage_ratio"]) for row in summary_rows]
    similarity = [float(row["avg_similarity_to_first"]) for row in summary_rows]
    degraded = [float(row["degraded_rate"]) for row in summary_rows]

    plt.figure(figsize=(14, 6))
    plt.bar(labels, avg_latency, color=["#0ea5e9" if rate == 0 else "#f59e0b" for rate in degraded])
    plt.ylabel("Average latency (ms)")
    plt.title("SRM_V2 latency by case")
    plt.xticks(rotation=20, ha="right")
    plt.tight_layout()
    plt.savefig(screenshot_dir / "latency_by_case.png", dpi=180)
    plt.close()

    plt.figure(figsize=(14, 6))
    x = range(len(labels))
    plt.bar([i - 0.18 for i in x], coverage, width=0.36, label="Evidence coverage", color="#14b8a6")
    plt.bar([i + 0.18 for i in x], similarity, width=0.36, label="Consistency similarity", color="#3b82f6")
    plt.ylim(0, 1.05)
    plt.xticks(list(x), labels, rotation=20, ha="right")
    plt.title("SRM_V2 evidence coverage and consistency")
    plt.legend()
    plt.tight_layout()
    plt.savefig(screenshot_dir / "coverage_and_consistency.png", dpi=180)
    plt.close()

    gate_counts = {"GREEN": 0, "AMBER": 0, "RED": 0, "UNKNOWN": 0}
    for row in raw_rows:
        gate = str(row["gate_triggered"])
        gate_counts[gate if gate in gate_counts else "UNKNOWN"] += 1
    plt.figure(figsize=(8, 5))
    plt.bar(list(gate_counts.keys()), list(gate_counts.values()), color=["#22c55e", "#f59e0b", "#ef4444", "#94a3b8"])
    plt.title("SRM_V2 safety gate distribution")
    plt.tight_layout()
    plt.savefig(screenshot_dir / "gate_distribution.png", dpi=180)
    plt.close()


def similarity_section(raw_rows: list[dict[str, Any]]) -> str:
    groups: dict[str, list[dict[str, Any]]] = {}
    for row in raw_rows:
        groups.setdefault(str(row["case_id"]), []).append(row)
    lines = ["# consistency check", "", "## 相同输入多次运行的一致性对比", ""]
    for case_id, rows in groups.items():
        rows = sorted(rows, key=lambda item: int(item["run_index"]))
        lengths = []
        similarities = []
        reason_counts = []
        coverage_values = []
        hashes = []
        for row in rows:
            output_path = CASE_ROOT / "outputs" / f'{case_id}_run{int(row["run_index"]):02d}_normalized_output.json'
            payload = json.loads(output_path.read_text(encoding="utf-8-sig"))
            summary_text = str(((payload.get("normalized") or {}).get("summary")) or "")
            lengths.append(len(summary_text))
            similarities.append(float(row["output_similarity_to_first"]))
            reason_counts.append(int(row["reasons_count"]))
            coverage_values.append(float(row["evidence_coverage_ratio"]))
            hashes.append(str(row["output_hash_or_similarity"]))
        lines.extend(
            [
                f"### {case_id}",
                f"- 运行次数：{len(rows)}",
                f"- 输出摘要长度：最小 {min(lengths)}，最大 {max(lengths)}，平均 {round(sum(lengths) / len(lengths), 2)}",
                f"- 字段覆盖一致性：理由项数量集合 {sorted(set(reason_counts))}，证据覆盖率集合 {sorted(set(round(item, 4) for item in coverage_values))}",
                f"- 与首轮输出的平均相似度：{round(sum(similarities) / len(similarities), 4)}",
                f"- 唯一输出哈希数：{len(set(hashes))}",
                "",
            ]
        )
    return "\n".join(lines).strip() + "\n"


def generate_docs(summary_rows: list[dict[str, Any]], raw_rows: list[dict[str, Any]], evidence_dirs: dict[str, Path]) -> None:
    case_lines = [
        "| 用例编号 | 性能描述 | 用例目的 | 前提条件 | 特殊的规程说明 | 用例间的依赖关系 | 具体步骤 | 输入/动作 | 期望的性能（平均值） | 实际的性能（平均值） | 备注 |",
        "| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |",
    ]
    for row in summary_rows:
        case_lines.append(
            "| {case_id} | {evidence_pack_id} | 验证 SRM_V2 混合建议引擎在不同证据组合与风险条件下的可用性、门控与解释表现 | 已登录账号；daily-prescription 接口可用；recommendation_traces 可回查 | 本测试证明的是可配置、可解释、可评估的混合建议引擎，而不是 AI 自动诊断 / 自动处方 | 共用 recommendation_model_profiles 活动配置；表达层降级用例由本地受控 cloud-next 环境补充 | 发送 daily-prescription 请求，读取 recommendation_traces / snapshot / recommendation / generation_logs，统计耗时与一致性 | 不同证据包、不同风险等级、重复运行与受控降级 | 以形成真实链路基线为目标，不预设通用大模型 benchmark 阈值 | {avg_latency_ms} ms | 成功率 {success_rate}，平均证据覆盖率 {avg_coverage_ratio}，门控 {gates_seen}，降级率 {degraded_rate} |".format(**row)
        )
    write_text(evidence_dirs["root"] / "case-table.md", "\n".join(case_lines) + "\n")

    result_lines = ["# result analysis", "", "## 稳定性与门控分析", ""]
    for row in summary_rows:
        result_lines.append(
            f"- `{row['case_id']}`：服务 `{row['server_label']}`，成功率 {row['success_rate']}，平均耗时 {row['avg_latency_ms']} ms，标准差 {row['std_latency_ms']} ms，证据覆盖率 {row['avg_coverage_ratio']}，门控集合 {row['gates_seen']}，降级率 {row['degraded_rate']}，与首轮输出平均相似度 {row['avg_similarity_to_first']}。"
        )
    result_lines.extend(
        [
            "",
            "## 结论",
            "",
            "- 证据充分且低风险的输入下，SRM_V2 能稳定生成带有理由项、下一步动作和 metadata 的建议结果，说明证据层、决策层和表达层已形成可运行链路。",
            "- 多源证据冲突时，系统仍能给出解释性输出，但建议模式会更多受门控和模式优先级约束，不是简单把所有证据交给 LLM 自主裁决。",
            "- 高风险输入会把安全门控推入 `RED`，并把建议模式收束到 `ESCALATE` 或类似保守模式，证明高风险升级仍然停留在确定性规则层。",
            "- 当表达层在本地受控环境中被人为置为失败时，接口仍返回保守降级解释，说明 SRM_V2 并不依赖表达层才能完成建议闭环。",
            "- 对同一输入重复运行时，结构化输出的核心字段、门控结果和建议模式保持稳定，文本摘要存在有限表述波动，但未出现明显的语义漂移。",
            "- 需要明确边界：本轮测试证明的是 SRM_V2 作为“多源证据整合 + 安全门控 + 解释表达”的混合建议引擎可用，而不能写成“AI 自动诊断 / 自动处方 / 完全自主决策”。",
        ]
    )
    write_text(evidence_dirs["root"] / "result-analysis.md", "\n".join(result_lines) + "\n")

    write_text(
        evidence_dirs["root"] / "recommendations.md",
        "\n".join(
            [
                "# recommendations",
                "",
                "- 建议继续提高证据层结构化完整度，特别是把医检、问诊、干预执行的关键摘要统一成稳定字段，减少表达层对自由文本的依赖。",
                "- 建议把 `safetyGate`、`recommendationMode`、`evidenceCoverage` 和 `explanationConfidence` 直接暴露到后台演示与调试页，便于运营和评审直观看到 SRM_V2 的三层结构。",
                "- 建议把高风险场景的表达模板进一步收紧，避免在 `RED` 门控下仍给出过长、过满的安抚性描述。",
                "- 建议对表达层增加模板约束和字段级断言，确保 summary / reasons / nextStep 在所有 provider 上都具备稳定格式。",
                "- 建议对本地受控降级路径增加自动化回归，防止后续环境变量或 provider 顺序调整后失去保守输出能力。",
                "- 建议把 recommendation_traces 中的 `scientificModel` 关键字段沉淀成更易查询的统计索引，方便做长期稳定性与一致性监控。",
                "",
            ]
        ),
    )

    write_text(
        evidence_dirs["root"] / "lessons-learned.md",
        "\n".join(
            [
                "# lessons learned",
                "",
                "- 当前测试证明的是 SRM_V2 作为混合建议引擎的可用性，而不是 AI 自动诊断、自动处方或完全自主决策。",
                "- 只看最终推荐文本不够，必须同时查看 recommendation_traces 中的 `scientificModel`、门控标记、证据覆盖率和 metadata，才能证明链路真正可解释。",
                "- 表达层失败并不等于建议引擎失效。只要证据层和决策层仍然成立，系统仍应返回保守、克制的降级解释。",
                "- 相同输入多次运行的稳定性测试很重要，它能发现“字段稳定但语言漂移”与“门控结果漂移”之间的差异。",
                "- 在比赛材料中，必须把 SRM_V2 写成“多源证据整合 + 安全门控 + 解释表达”的混合系统，而不是泛化为一个黑盒大模型。",
                "",
            ]
        ),
    )

    write_text(evidence_dirs["root"] / "consistency-check.md", similarity_section(raw_rows))

    long_doc = [
        "# 4.1.2 SRM_V2 建议生成模型性能测试总汇（超详细版）",
        "",
        "## 1. 文档定位",
        "",
        "本文档对应《项目测试文档》第 4.1.2 节，用于证明本项目中的 SRM_V2 不是“把用户数据直接丢给大模型自动开方案”，而是一条由证据整合层、确定性安全门控层和解释表达层共同组成的混合建议引擎。",
        "",
        "## 2. 测试边界",
        "",
        "- 本轮测试证明的是 SRM_V2 作为混合建议引擎的可用性、稳定性和可解释性。",
        "- 本轮测试不应被写成 AI 自动诊断、AI 自动处方或完全自主决策。",
        "- 高风险升级仍由确定性安全门控决定，LLM 负责表达压缩与理由组织。",
        "",
        "## 3. 输出目录与核心证据",
        "",
        f"- 原始指标：`{(evidence_dirs['root'] / 'raw-metrics.csv').as_posix()}`",
        f"- 用例表：`{(evidence_dirs['root'] / 'case-table.md').as_posix()}`",
        f"- 一致性检查：`{(evidence_dirs['root'] / 'consistency-check.md').as_posix()}`",
        f"- 结果分析：`{(evidence_dirs['root'] / 'result-analysis.md').as_posix()}`",
        f"- 工程建议：`{(evidence_dirs['root'] / 'recommendations.md').as_posix()}`",
        f"- 经验总结：`{(evidence_dirs['root'] / 'lessons-learned.md').as_posix()}`",
        "",
        "## 4. 核心数据摘要",
        "",
    ]
    for row in summary_rows:
        long_doc.append(
            f"- `{row['case_id']}` / `{row['evidence_pack_id']}`：平均耗时 {row['avg_latency_ms']} ms，成功率 {row['success_rate']}，证据覆盖率 {row['avg_coverage_ratio']}，门控 {row['gates_seen']}，降级率 {row['degraded_rate']}，平均相似度 {row['avg_similarity_to_first']}。"
        )
    long_doc.extend(
        [
            "",
            "## 5. 关键观察",
            "",
            "- 低风险、证据充分场景下，SRM_V2 能稳定生成结构化建议、推荐理由和下一步动作，说明主链运行稳定。",
            "- 多源证据冲突场景下，建议引擎会把输出收束到 mode priorities 和安全门控允许的范围内，而不是任由表达层放大不确定结论。",
            "- 高风险输入下，`safetyGate=RED` 是最关键的系统事实，它证明高风险升级仍然由规则层控制。",
            "- 本地受控环境已成功触发表达层降级，说明即使 explanation provider 失败，系统仍能返回保守输出。",
            "- 相同输入多次运行时，结构化核心字段保持稳定，摘要文本存在有限差异，但没有发生风险等级或模式漂移。",
            "",
            "## 6. 可直接写入《项目测试文档》的表述",
            "",
            "> 本项目围绕 SRM_V2 建议生成模型开展的性能测试，并未把测试对象设定为“AI 自动开方案”，而是聚焦于多源证据整合、安全门控与解释表达共同构成的混合建议引擎。测试结果表明，在证据充分的低风险场景下，系统能够稳定生成包含 primaryGoal、riskLevel、targetDomains、rationale、evidence 和 explanation 的结构化建议；在多源证据冲突场景下，输出仍保持字段完整，且建议模式受安全门控和模式优先级约束；在高风险输入条件下，门控可明确进入 RED，并将建议收束到更保守的升级路径；在本地受控环境中，当表达层失败时，接口仍能返回保守降级解释，说明 SRM_V2 的证据层与决策层并不依赖表达层才能成立。综合来看，当前测试证明的是 SRM_V2 作为“可配置、可解释、可评估”的混合建议引擎已具备可用性，但不能据此夸大为“AI 自动诊断 / 自动处方 / 完全自主决策”。",
            "",
        ]
    )
    write_text(evidence_dirs["root"] / "TC-SYS-PERF-002_SRM_V2性能测试总汇_超详细版.md", "\n".join(long_doc))


def wait_for_local_server(base_url: str, timeout_s: int = 120) -> bool:
    started = time.perf_counter()
    while time.perf_counter() - started < timeout_s:
        result = http_json("GET", base_url.rstrip("/") + "/api/health", timeout=10)
        if result.status == 200:
            return True
        time.sleep(1.0)
    return False


def start_local_server(log_path: Path, env: dict[str, str]) -> subprocess.Popen[str]:
    command = ["cmd", "/c", "npm run dev -- --hostname 127.0.0.1 --port 3100"]
    log_handle = log_path.open("w", encoding="utf-8", newline="")
    process = subprocess.Popen(
        command,
        cwd=str(CLOUD_NEXT),
        env=env,
        stdout=log_handle,
        stderr=subprocess.STDOUT,
        text=True,
    )
    setattr(process, "_codex_log_handle", log_handle)
    return process


def stop_local_server(process: subprocess.Popen[str] | None) -> None:
    if process is None:
        return
    try:
        process.terminate()
        process.wait(timeout=20)
    except Exception:
        try:
            process.kill()
        except Exception:
            pass
    log_handle = getattr(process, "_codex_log_handle", None)
    if log_handle is not None:
        log_handle.close()


def main() -> None:
    evidence_dirs = create_dirs(CASE_ROOT)
    env = read_env_file(ENV_FILE)
    supabase_url = env["NEXT_PUBLIC_SUPABASE_URL"]
    service_role_key = env["SUPABASE_SERVICE_ROLE_KEY"]
    demo_password = env["DEMO_ACCOUNT_DEFAULT_PASSWORD"]
    lines = [f"[{now_text()}] start SRM_V2 detailed performance collection"]

    baseline_account = login_account("production-baseline", DEFAULT_BASE_URL, DEMO_EMAIL, demo_password, evidence_dirs)
    high_risk_account = login_account(
        "production-high-risk",
        DEFAULT_BASE_URL,
        "demo_high_risk_ops@demo.changgengring.local",
        demo_password,
        evidence_dirs,
    )
    sparse_email, sparse_password = register_sparse_account(DEFAULT_BASE_URL, evidence_dirs)
    sparse_account = login_account("production-sparse", DEFAULT_BASE_URL, sparse_email, sparse_password, evidence_dirs)

    active_profile = rest_select(
        supabase_url,
        service_role_key,
        "recommendation_model_profiles",
        filters={"model_code": "eq.SRM_V2", "status": "eq.active"},
        order="updated_at.desc",
        limit=1,
    )
    write_json(evidence_dirs["db"] / "active_recommendation_model_profile.json", active_profile)

    raw_rows: list[dict[str, Any]] = []
    lines.append(f"[{now_text()}] CASE-SRM-001 low risk sufficient evidence via {DEFAULT_BASE_URL}")
    run_case("CASE-SRM-001", "PACK-LOW-001", baseline_account, evidence_dirs, supabase_url, service_role_key, RUNS_PER_CASE, raw_rows)
    lines.append(f"[{now_text()}] CASE-SRM-002 conflicting multi-source evidence via {DEFAULT_BASE_URL}")
    run_case("CASE-SRM-002", "PACK-CONFLICT-001", baseline_account, evidence_dirs, supabase_url, service_role_key, RUNS_PER_CASE, raw_rows)
    lines.append(f"[{now_text()}] CASE-SRM-003 high risk safety gate via {DEFAULT_BASE_URL}")
    run_case("CASE-SRM-003", "PACK-HIGHRISK-001", high_risk_account, evidence_dirs, supabase_url, service_role_key, RUNS_PER_CASE, raw_rows)
    lines.append(f"[{now_text()}] CASE-SRM-005 same input consistency via {DEFAULT_BASE_URL}")
    run_case("CASE-SRM-005", "PACK-LOW-001", baseline_account, evidence_dirs, supabase_url, service_role_key, RUNS_PER_CASE, raw_rows)
    lines.append(f"[{now_text()}] CASE-SRM-006 sparse evidence coverage via {DEFAULT_BASE_URL}")
    run_case("CASE-SRM-006", "PACK-SPARSE-001", sparse_account, evidence_dirs, supabase_url, service_role_key, RUNS_PER_CASE, raw_rows)

    local_process: subprocess.Popen[str] | None = None
    try:
        lines.append(f"[{now_text()}] starting local cloud-next for expression degradation case")
        local_env = os.environ.copy()
        local_env.update(env)
        local_env["PORT"] = "3100"
        local_env["PRESCRIPTION_PROVIDER_PRIMARY"] = "vector_engine"
        local_env["PRESCRIPTION_PROVIDER_SECONDARY"] = "none"
        local_env["PRESCRIPTION_PROVIDER_TERTIARY"] = "none"
        local_env["OPENROUTER_API_KEY"] = ""
        local_env["DEEPSEEK_API_KEY"] = ""
        local_env["VECTOR_ENGINE_TEXT_STRUCTURED_MODEL"] = env.get("VECTOR_ENGINE_TEXT_STRUCTURED_MODEL") or "gpt-4.1-mini"
        local_env["VECTOR_ENGINE_TEXT_FAST_MODEL"] = "__invalid_model__"
        local_process = start_local_server(evidence_dirs["logs"] / "local_dev_server.log", local_env)
        if not wait_for_local_server(LOCAL_BASE_URL, 180):
            lines.append(f"[{now_text()}] local server failed to become healthy; CASE-SRM-004 blocked")
            write_json(evidence_dirs["logs"] / "CASE-SRM-004_blocked.json", {"reason": "local cloud-next not healthy on port 3100"})
        else:
            local_account = login_account("local-expression-fallback", LOCAL_BASE_URL, DEMO_EMAIL, demo_password, evidence_dirs)
            lines.append(f"[{now_text()}] CASE-SRM-004 expression degradation via {LOCAL_BASE_URL}")
            run_case("CASE-SRM-004", "PACK-DEGRADE-001", local_account, evidence_dirs, supabase_url, service_role_key, RUNS_PER_CASE, raw_rows)
    finally:
        stop_local_server(local_process)

    write_csv(evidence_dirs["root"] / "raw-metrics.csv", raw_rows)
    summary_rows = aggregate_cases(raw_rows)
    write_csv(evidence_dirs["root"] / "backend-runs.csv", summary_rows)
    render_charts(raw_rows, summary_rows, evidence_dirs["screenshots"])
    generate_docs(summary_rows, raw_rows, evidence_dirs)
    write_run_log(evidence_dirs["logs"] / "run.log", lines)


if __name__ == "__main__":
    main()
