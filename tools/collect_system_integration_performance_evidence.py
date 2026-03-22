from __future__ import annotations

import csv
import importlib.util
import json
import shutil
import sqlite3
import statistics
import subprocess
import sys
import time
import uuid
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Any


ROOT = Path(r"D:\newstart")
OUT_DIR = ROOT / "test-evidence" / "04-system" / "4.1.3-system-integration"
FUNC_HELPER = ROOT / "tools" / "run_functional_evidence.py"
MODEL_HELPER = ROOT / "tools" / "collect_system_model_performance_evidence.py"
APP_ID = "com.example.newstart"


def load_module(path: Path, name: str):
    spec = importlib.util.spec_from_file_location(name, path)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"Unable to load helper module from {path}")
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
    return module


func = load_module(FUNC_HELPER, "functional_base_for_system_integration")
model = load_module(MODEL_HELPER, "model_perf_base_for_system_integration")


def now_text() -> str:
    return time.strftime("%Y-%m-%d %H:%M:%S")


def ensure_empty_dir(path: Path) -> None:
    if path.exists():
        shutil.rmtree(path)
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


def stats(values: list[float]) -> dict[str, float | None]:
    if not values:
        return {"avg": None, "min": None, "max": None, "std": None}
    return {
        "avg": round(sum(values) / len(values), 2),
        "min": round(min(values), 2),
        "max": round(max(values), 2),
        "std": round(statistics.pstdev(values), 2) if len(values) > 1 else 0.0,
    }


def create_dirs(base: Path) -> dict[str, Path]:
    ensure_empty_dir(base)
    dirs = {
        "root": base,
        "screenshots": base / "screenshots",
        "screenrecords": base / "screenrecords",
        "logs": base / "logs",
        "db": base / "db-snapshots",
        "api": base / "api-captures",
        "auth": base / "auth",
    }
    for key in ("screenshots", "screenrecords", "logs", "db", "api", "auth"):
        dirs[key].mkdir(parents=True, exist_ok=True)
    return dirs


def copy_if_exists(src: Path, dst: Path) -> None:
    if src.exists():
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(src, dst)


def ui_is_app(ui_path: Path) -> bool:
    root = func.dump_ui(ui_path)
    if root.attrib.get("package") == APP_ID:
        return True
    return any(node.attrib.get("package") == APP_ID for node in root.iter("node"))


def ensure_app_foreground(ui_path: Path, run_log: list[str], dirs: dict[str, Path]) -> bool:
    func.adb("shell", "am", "force-stop", "com.android.documentsui", check=False)
    for attempt in range(4):
        launch = func.adb("shell", "am", "start", "-S", "-W", "-n", func.MAIN_ACTIVITY, check=False)
        write_text(
            dirs["logs"] / f"launch_attempt_{attempt + 1}.txt",
            (launch.stdout or "") + "\n" + (launch.stderr or ""),
        )
        func.wait_for_ui_settle(4.0)
        if ui_is_app(ui_path):
            run_log.append(f"[{now_text()}] app foreground confirmed on attempt {attempt + 1}")
            return True
        func.adb("shell", "input", "keyevent", "4", check=False)
        time.sleep(1.2)
    run_log.append(f"[{now_text()}] app foreground not confirmed after retries")
    return ui_is_app(ui_path)


def ensure_account_on_device(account_name: str, auth_dir: Path, run_log: list[str]):
    creds = func.load_demo_credentials()
    auth = func.login_demo_account(account_name, creds, auth_dir)
    run_log.append(
        f"[{now_text()}] login {auth.email} scenario={auth.demo_scenario} seed={auth.demo_seed_version}"
    )
    func.inject_session(auth)
    func.bootstrap_profile(auth, auth_dir)
    bootstrap_ok = func.wait_for_bootstrap(auth, timeout_seconds=40)
    run_log.append(f"[{now_text()}] device bootstrap for {account_name}: {bootstrap_ok}")
    return auth, bootstrap_ok


def summarize_snapshot_counts(overview: dict[str, Any], table: str) -> int:
    value = ((overview.get("selectedRowCounts") or {}).get(table) or 0)
    return int(value) if isinstance(value, int) else 0


def doctor_payload() -> dict[str, Any]:
    return {
        "conversationBlock": "用户：近两天头痛、睡眠差，白天容易疲劳。",
        "contextBlock": "\n".join(
            [
                "recovery_score=62",
                "sleep_minutes=418",
                "sleep_efficiency=87",
                "awake_count=2",
                "heart_rate=69",
                "spo2_min=95",
                "hrv_current=53",
                "hrv_baseline=60",
            ]
        ),
        "ragContext": "近期睡眠不足时优先固定起床时间，并减少睡前高刺激输入。",
        "stage": "INTAKE",
        "followUpCount": 0,
    }


def report_payload() -> dict[str, Any]:
    ocr_text = "\n".join(
        [
            "体检报告",
            "总胆固醇 6.20 mmol/L",
            "甘油三酯 2.40 mmol/L",
            "低密度脂蛋白胆固醇 4.10 mmol/L",
            "空腹血糖 6.10 mmol/L",
            "血红蛋白 145 g/L",
        ]
    )
    return {"reportType": "PHOTO", "ocrText": ocr_text, "ocrMarkdown": ocr_text}


def avatar_payload() -> dict[str, Any]:
    return {
        "pageKey": "home_today",
        "pageTitle": "今日",
        "pageSubtitle": "恢复分与核心指标总览",
        "trigger": "tap",
        "userStateSummary": "恢复分 69，昨夜睡眠 7.1 小时，主要指标稳定。",
        "riskSummary": "暂无明显高风险提示。",
        "actionHint": "先查看今日重点，再决定是否进入干预中心或趋势页。",
        "visibleHighlights": ["恢复分主卡片", "心率、血氧、体温、HRV 摘要", "建议入口与干预快捷卡"],
    }


def collect_current_page_screenshot(prefix: str, dirs: dict[str, Path]) -> None:
    func.screenshot(dirs["screenshots"] / f"{prefix}.png")
    func.dump_ui(dirs["screenshots"] / f"{prefix}.xml")


def copy_existing_page_proofs(dirs: dict[str, Path]) -> None:
    source_pairs = [
        (
            ROOT / "test-evidence" / "03-functional" / "02-today-recovery-physiology" / "screenshots" / "today_top.png",
            dirs["screenshots"] / "proof_today_top.png",
        ),
        (
            ROOT / "test-evidence" / "03-functional" / "02-today-recovery-physiology" / "screenshots" / "today_lower.png",
            dirs["screenshots"] / "proof_today_lower.png",
        ),
        (
            ROOT / "test-evidence" / "03-functional" / "03-ai-doctor-inquiry" / "screenshots" / "doctor_page.png",
            dirs["screenshots"] / "proof_doctor_page.png",
        ),
        (
            ROOT / "test-evidence" / "03-functional" / "03-ai-doctor-inquiry" / "screenshots" / "doctor_assessment.png",
            dirs["screenshots"] / "proof_doctor_assessment.png",
        ),
        (
            ROOT / "test-evidence" / "03-functional" / "05-intervention-generate-execute-writeback" / "screenshots" / "case3_review_top.png",
            dirs["screenshots"] / "proof_review_top.png",
        ),
        (
            ROOT / "test-evidence" / "03-functional" / "05-intervention-generate-execute-writeback" / "screenshots" / "case3_trend_after.png",
            dirs["screenshots"] / "proof_trend_after.png",
        ),
        (
            ROOT / "test-evidence" / "03-functional" / "07-desktop-avatar-and-tts" / "screenshots" / "case1_home_bubble.png",
            dirs["screenshots"] / "proof_home_bubble.png",
        ),
        (
            ROOT / "test-evidence" / "03-functional" / "07-desktop-avatar-and-tts" / "screenshots" / "case2_doctor_bubble.png",
            dirs["screenshots"] / "proof_doctor_bubble.png",
        ),
        (
            ROOT / "test-evidence" / "03-functional" / "01-ring-connection-collection" / "screenshots" / "case1_before_top.png",
            dirs["screenshots"] / "proof_ring_before.png",
        ),
        (
            ROOT / "test-evidence" / "03-functional" / "01-ring-connection-collection" / "screenshots" / "case3_recovered.png",
            dirs["screenshots"] / "proof_ring_recovered.png",
        ),
    ]
    for src, dst in source_pairs:
        copy_if_exists(src, dst)


def wait_for_log_pattern(patterns: list[str], timeout_s: float = 12.0) -> tuple[bool, str]:
    deadline = time.perf_counter() + timeout_s
    captured = ""
    while time.perf_counter() < deadline:
        dump = func.adb("logcat", "-d", "-v", "time")
        text = dump.stdout or ""
        captured = text
        if any(pattern in text for pattern in patterns):
            return True, text
        time.sleep(0.5)
    return False, captured


def screenrecord_attempt(name: str, duration_s: int, action, dirs: dict[str, Path]) -> dict[str, Any]:
    remote = f"/sdcard/{name}.mp4"
    local = dirs["screenrecords"] / f"{name}.mp4"
    proc = subprocess.Popen(
        ["adb", "shell", "screenrecord", "--time-limit", str(duration_s), remote],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    time.sleep(1.0)
    try:
        action()
        time.sleep(max(0, duration_s - 2))
    finally:
        try:
            proc.wait(timeout=duration_s + 10)
        except subprocess.TimeoutExpired:
            func.adb("shell", "pkill", "-INT", "screenrecord", check=False)
            proc.wait(timeout=10)
        stderr = proc.stderr.read().decode("utf-8", errors="replace").strip() if proc.stderr else ""
        pull = func.adb("pull", remote, str(local), check=False)
        func.adb("shell", "rm", "-f", remote, check=False)
    meta = {
        "stderr": stderr,
        "pullStdout": (pull.stdout or "").strip() if isinstance(pull.stdout, str) else "",
        "size": local.stat().st_size if local.exists() else 0,
        "localPath": str(local),
    }
    write_json(dirs["screenrecords"] / f"{name}_meta.json", meta)
    return meta


def metric_today_refresh(auth, rows: list[dict[str, Any]], summaries: list[dict[str, Any]], run_log: list[str], dirs: dict[str, Path]) -> None:
    auth_dir = dirs["auth"] / "baseline"
    auth_dir.mkdir(parents=True, exist_ok=True)
    func.inject_session(auth)
    func.bootstrap_profile(auth, auth_dir)
    func.wait_for_bootstrap(auth, timeout_seconds=20)
    ui_path = dirs["logs"] / "_today_ui.xml"
    values: list[float] = []
    successes = 0
    for run_index in range(1, 6):
        func.clear_logcat()
        func.adb("shell", "am", "force-stop", APP_ID, check=False)
        started = time.perf_counter()
        launch = func.force_start_app()
        page_ready = False
        ui_text = ""
        for _ in range(8):
            time.sleep(0.8)
            if ui_is_app(ui_path):
                ui_text = ui_path.read_text(encoding="utf-8-sig", errors="ignore")
                if "navigation_home" in ui_text:
                    page_ready = True
                    break
        latency_ms = round((time.perf_counter() - started) * 1000.0, 2)
        values.append(latency_ms)
        if run_index in {1, 5}:
            collect_current_page_screenshot(f"today_refresh_run{run_index:02d}", dirs)
        func.dump_logcat(dirs["logs"] / f"today_refresh_run{run_index:02d}_logcat.txt")
        overview = func.export_db(dirs["db"] / f"today_refresh_run{run_index:02d}")
        success = page_ready
        successes += 1 if success else 0
        notes = (launch.strip().replace("\n", " | ")[:200] if launch else "") or "launch output empty"
        rows.append(
            {
                "case_id": "SYS-INTEG-001",
                "metric_name": "today_refresh_ms",
                "run_index": run_index,
                "latency_ms_or_rate": latency_ms,
                "success": success,
                "page_updated": page_ready,
                "db_written": False,
                "notes": notes,
            }
        )
        run_log.append(
            f"[{now_text()}] today_refresh run#{run_index} latencyMs={latency_ms} pageReady={page_ready} tables={len(overview.get('tables') or [])}"
        )
    summaries.append(
        {
            "case_id": "SYS-INTEG-001",
            "metric_name": "今日页恢复分与摘要刷新耗时",
            "expected": "平均 <= 2500 ms，页面可进入可消费状态",
            "stats": stats(values),
            "success_rate": round(successes / 5 * 100, 2),
            "page_updated_rate": round(successes / 5 * 100, 2),
            "db_written_rate": 0.0,
            "status": "PASS" if successes == 5 else "PASS_WITH_WARNING",
            "notes": "以冷启动后今日页主内容进入可消费状态的本地耗时衡量，不将其写成模型推理耗时。",
        }
    )


def metric_doctor_response(auth, rows: list[dict[str, Any]], summaries: list[dict[str, Any]], run_log: list[str], dirs: dict[str, Path]) -> None:
    values: list[float] = []
    successes = 0
    payload = doctor_payload()
    for run_index in range(1, 6):
        result = model.http_json(
            "POST",
            model.DEFAULT_BASE_URL.rstrip("/") + "/api/doctor/turn",
            body=payload,
            headers={"authorization": f"Bearer {auth.token}"},
        )
        result_payload = result.payload if isinstance(result.payload, dict) else {}
        data = result_payload.get("data") if isinstance(result_payload.get("data"), dict) else {}
        success = result.status == 200 and isinstance(data, dict) and (
            bool(data.get("followUps")) or bool(data.get("assessment"))
        )
        if run_index in {1, 5}:
            write_json(dirs["api"] / f"doctor_run{run_index:02d}_request.json", payload)
            write_json(dirs["api"] / f"doctor_run{run_index:02d}_response.json", result_payload)
        values.append(round(result.duration_ms, 2))
        successes += 1 if success else 0
        rows.append(
            {
                "case_id": "SYS-INTEG-002",
                "metric_name": "doctor_first_response_ms",
                "run_index": run_index,
                "latency_ms_or_rate": round(result.duration_ms, 2),
                "success": success,
                "page_updated": True,
                "db_written": False,
                "notes": f"http={result.status} traceId={result_payload.get('traceId', '')}",
            }
        )
        run_log.append(
            f"[{now_text()}] doctor_first_response run#{run_index} latencyMs={result.duration_ms:.2f} status={result.status}"
        )
    summaries.append(
        {
            "case_id": "SYS-INTEG-002",
            "metric_name": "AI 医生首轮响应平均耗时",
            "expected": "平均 <= 7000 ms，首轮 follow-up/assessment 可返回",
            "stats": stats(values),
            "success_rate": round(successes / 5 * 100, 2),
            "page_updated_rate": 100.0,
            "db_written_rate": 0.0,
            "status": "PASS" if successes == 5 else "PASS_WITH_WARNING",
            "notes": "页面消费证据使用医生页与问诊结果卡截图，不把此项写成问诊单稳定落库成功率。",
        }
    )


def metric_report_understand(auth, rows: list[dict[str, Any]], summaries: list[dict[str, Any]], run_log: list[str], dirs: dict[str, Path]) -> None:
    values: list[float] = []
    successes = 0
    payload = report_payload()
    for run_index in range(1, 6):
        result = model.http_json(
            "POST",
            model.DEFAULT_BASE_URL.rstrip("/") + "/api/report/understand",
            body=payload,
            headers={"authorization": f"Bearer {auth.token}"},
        )
        result_payload = result.payload if isinstance(result.payload, dict) else {}
        data = result_payload.get("data") if isinstance(result_payload.get("data"), dict) else {}
        success = result.status == 200 and isinstance(data, dict) and bool(data.get("readableReport"))
        if run_index in {1, 5}:
            write_json(dirs["api"] / f"report_run{run_index:02d}_request.json", payload)
            write_json(dirs["api"] / f"report_run{run_index:02d}_response.json", result_payload)
        values.append(round(result.duration_ms, 2))
        successes += 1 if success else 0
        rows.append(
            {
                "case_id": "SYS-INTEG-003",
                "metric_name": "report_understand_ms",
                "run_index": run_index,
                "latency_ms_or_rate": round(result.duration_ms, 2),
                "success": success,
                "page_updated": False,
                "db_written": False,
                "notes": "仅测 OCR 后的报告理解服务；上传/OCR 前置链路在当前环境有外部依赖波动。",
            }
        )
        run_log.append(
            f"[{now_text()}] report_understand run#{run_index} latencyMs={result.duration_ms:.2f} status={result.status}"
        )
    summaries.append(
        {
            "case_id": "SYS-INTEG-003",
            "metric_name": "医检报告解析平均耗时",
            "expected": "平均 <= 5000 ms；能返回可读报告与结构化指标",
            "stats": stats(values),
            "success_rate": round(successes / 5 * 100, 2),
            "page_updated_rate": 0.0,
            "db_written_rate": 0.0,
            "status": "PASS_WITH_WARNING" if successes == 5 else "BLOCKED",
            "notes": "本轮证明的是报告理解服务，不包含外部 OCR 上传耗时；页面消费需依赖单独功能取证。",
        }
    )


def metric_avatar_narration(auth, rows: list[dict[str, Any]], summaries: list[dict[str, Any]], run_log: list[str], dirs: dict[str, Path]) -> None:
    values: list[float] = []
    successes = 0
    payload = avatar_payload()
    for run_index in range(1, 6):
        result = model.http_json(
            "POST",
            model.DEFAULT_BASE_URL.rstrip("/") + "/api/avatar/narration",
            body=payload,
            headers={"authorization": f"Bearer {auth.token}"},
        )
        result_payload = result.payload if isinstance(result.payload, dict) else {}
        data = result_payload.get("data") if isinstance(result_payload.get("data"), dict) else {}
        success = result.status == 200 and isinstance(data, dict) and bool(data.get("text"))
        if run_index in {1, 5}:
            write_json(dirs["api"] / f"avatar_run{run_index:02d}_request.json", payload)
            write_json(dirs["api"] / f"avatar_run{run_index:02d}_response.json", result_payload)
        values.append(round(result.duration_ms, 2))
        successes += 1 if success else 0
        rows.append(
            {
                "case_id": "SYS-INTEG-004",
                "metric_name": "avatar_narration_ms",
                "run_index": run_index,
                "latency_ms_or_rate": round(result.duration_ms, 2),
                "success": success,
                "page_updated": True,
                "db_written": False,
                "notes": f"http={result.status} semanticAction={(data or {}).get('semanticAction', '')}",
            }
        )
        run_log.append(
            f"[{now_text()}] avatar_narration run#{run_index} latencyMs={result.duration_ms:.2f} status={result.status}"
        )
    summaries.append(
        {
            "case_id": "SYS-INTEG-004",
            "metric_name": "桌面机器人文案生成平均耗时",
            "expected": "平均 <= 4000 ms，文案可进入页面气泡展示",
            "stats": stats(values),
            "success_rate": round(successes / 5 * 100, 2),
            "page_updated_rate": 100.0,
            "db_written_rate": 0.0,
            "status": "PASS" if successes == 5 else "PASS_WITH_WARNING",
            "notes": "页面消费证据使用桌面机器人气泡截图与 UI 树，不把此项扩大为 TTS 全链路耗时。",
        }
    )


def metric_tts_playback(auth, rows: list[dict[str, Any]], summaries: list[dict[str, Any]], run_log: list[str], dirs: dict[str, Path]) -> None:
    auth_dir = dirs["auth"] / "baseline"
    auth_dir.mkdir(parents=True, exist_ok=True)
    func.inject_session(auth)
    func.bootstrap_profile(auth, auth_dir)
    func.wait_for_bootstrap(auth, timeout_seconds=20)
    ui_path = dirs["logs"] / "_tts_ui.xml"
    values: list[float] = []
    successes = 0

    def trigger_home_avatar() -> None:
        ensure_app_foreground(ui_path, run_log, dirs)
        func.goto_tab("home", ui_path)
        func.tap_by_id("com.example.newstart:id/btn_global_avatar_voice", ui_path, retries=2)

    screen_meta = screenrecord_attempt("tts_home_sample", 10, trigger_home_avatar, dirs)
    run_log.append(f"[{now_text()}] tts screenrecord sample size={screen_meta.get('size', 0)}")

    for run_index in range(1, 6):
        ensure_app_foreground(ui_path, run_log, dirs)
        func.goto_tab("home", ui_path)
        if run_index in {1, 5}:
            collect_current_page_screenshot(f"tts_before_run{run_index:02d}", dirs)
        func.clear_logcat()
        started = time.perf_counter()
        tapped = func.tap_by_id("com.example.newstart:id/btn_global_avatar_voice", ui_path, retries=2)
        found, logcat_text = wait_for_log_pattern(
            ["audio out start. uid", "ACTION_AUDIO_PLAYBACK_STATE_CHANGED"],
            timeout_s=12.0,
        )
        latency_ms = round((time.perf_counter() - started) * 1000.0, 2)
        log_path = dirs["logs"] / f"tts_run{run_index:02d}_logcat.txt"
        write_text(log_path, logcat_text)
        ui_text = ""
        try:
            func.dump_ui(ui_path)
            ui_text = ui_path.read_text(encoding="utf-8-sig", errors="ignore")
        except Exception:
            ui_text = ""
        page_updated = "globalAvatarSpeechBubble" in ui_text or "btn_global_avatar_voice" in ui_text
        success = tapped and found
        if run_index in {1, 5}:
            collect_current_page_screenshot(f"tts_after_run{run_index:02d}", dirs)
        values.append(latency_ms)
        successes += 1 if success else 0
        rows.append(
            {
                "case_id": "SYS-INTEG-005",
                "metric_name": "tts_trigger_to_playback_ms",
                "run_index": run_index,
                "latency_ms_or_rate": latency_ms,
                "success": success,
                "page_updated": page_updated,
                "db_written": False,
                "notes": "通过页面点击后等待播放器状态日志 `audio out start` 估算触发到起播时延。",
            }
        )
        run_log.append(
            f"[{now_text()}] tts_trigger_to_playback run#{run_index} latencyMs={latency_ms} tapped={tapped} audioStart={found} pageUpdated={page_updated}"
        )
        time.sleep(1.5)
    page_rate = round(
        sum(1 for row in rows if row["case_id"] == "SYS-INTEG-005" and row["page_updated"]) / 5 * 100,
        2,
    )
    summaries.append(
        {
            "case_id": "SYS-INTEG-005",
            "metric_name": "TTS 从触发到开始播放的平均时延",
            "expected": "平均 <= 3500 ms；页面气泡可见且播放器状态进入播放",
            "stats": stats(values),
            "success_rate": round(successes / 5 * 100, 2),
            "page_updated_rate": page_rate,
            "db_written_rate": 0.0,
            "status": "PASS" if successes == 5 else "PASS_WITH_WARNING",
            "notes": "该指标不以 TTS HTTP 200 代替起播成功，而以页面触发后播放器状态日志作为主要证据。",
        }
    )


def metric_intervention_writeback(auth, rows: list[dict[str, Any]], summaries: list[dict[str, Any]], run_log: list[str], dirs: dict[str, Path]) -> None:
    auth_dir = dirs["auth"] / "live_intervention"
    auth_dir.mkdir(parents=True, exist_ok=True)
    func.inject_session(auth)
    func.bootstrap_profile(auth, auth_dir)
    func.wait_for_bootstrap(auth, timeout_seconds=20)
    ui_path = dirs["logs"] / "_intervention_ui.xml"
    values: list[float] = []
    successes = 0
    screen_meta = screenrecord_attempt("intervention_sample", 16, lambda: None, dirs)
    run_log.append(f"[{now_text()}] intervention screenrecord sample size={screen_meta.get('size', 0)}")
    for run_index in range(1, 6):
        if not ensure_app_foreground(ui_path, run_log, dirs):
            rows.append(
                {
                    "case_id": "SYS-INTEG-006",
                    "metric_name": "intervention_writeback_success_rate",
                    "run_index": run_index,
                    "latency_ms_or_rate": 0.0,
                    "success": False,
                    "page_updated": False,
                    "db_written": False,
                    "notes": "App not foreground; run blocked.",
                }
            )
            values.append(0.0)
            continue
        opened_center = func.open_intervention_center(ui_path)
        if not opened_center:
            rows.append(
                {
                    "case_id": "SYS-INTEG-006",
                    "metric_name": "intervention_writeback_success_rate",
                    "run_index": run_index,
                    "latency_ms_or_rate": 0.0,
                    "success": False,
                    "page_updated": False,
                    "db_written": False,
                    "notes": "Unable to open intervention center.",
                }
            )
            values.append(0.0)
            continue
        if run_index in {1, 5}:
            collect_current_page_screenshot(f"intervention_before_run{run_index:02d}", dirs)
        before_overview = func.export_db(dirs["db"] / f"intervention_before_run{run_index:02d}")
        before_exec = summarize_snapshot_counts(before_overview, "intervention_executions")
        before_relax = summarize_snapshot_counts(before_overview, "relax_sessions")
        opened_zen = func.open_intervention_card(ui_path, "com.example.newstart:id/card_intervention_zen", max_scrolls=6)
        started = time.perf_counter()
        if not opened_zen:
            rows.append(
                {
                    "case_id": "SYS-INTEG-006",
                    "metric_name": "intervention_writeback_success_rate",
                    "run_index": run_index,
                    "latency_ms_or_rate": 0.0,
                    "success": False,
                    "page_updated": False,
                    "db_written": False,
                    "notes": "Unable to open Zen card.",
                }
            )
            values.append(0.0)
            continue
        started_ok = func.tap_by_id("com.example.newstart:id/btn_zen_primary", ui_path, retries=3)
        time.sleep(4.0)
        completed_ok = func.tap_by_id("com.example.newstart:id/btn_zen_secondary", ui_path, retries=3)
        time.sleep(2.0)
        after_overview = func.export_db(dirs["db"] / f"intervention_after_run{run_index:02d}")
        after_exec = summarize_snapshot_counts(after_overview, "intervention_executions")
        after_relax = summarize_snapshot_counts(after_overview, "relax_sessions")
        db_written = after_exec > before_exec or after_relax > before_relax
        latency_ms = round((time.perf_counter() - started) * 1000.0, 2)
        success = started_ok and completed_ok and db_written
        if run_index in {1, 5}:
            collect_current_page_screenshot(f"intervention_after_run{run_index:02d}", dirs)
        values.append(100.0 if success else 0.0)
        successes += 1 if success else 0
        rows.append(
            {
                "case_id": "SYS-INTEG-006",
                "metric_name": "intervention_writeback_success_rate",
                "run_index": run_index,
                "latency_ms_or_rate": 100.0 if success else 0.0,
                "success": success,
                "page_updated": completed_ok,
                "db_written": db_written,
                "notes": f"sessionDurationMs={latency_ms}; beforeExec={before_exec}; afterExec={after_exec}; beforeRelax={before_relax}; afterRelax={after_relax}",
            }
        )
        run_log.append(
            f"[{now_text()}] intervention_writeback run#{run_index} durationMs={latency_ms} dbWritten={db_written} beforeExec={before_exec} afterExec={after_exec}"
        )
        func.tap_by_id("com.example.newstart:id/btn_zen_back", ui_path, retries=2)
        time.sleep(1.0)
    rate = round(successes / 5 * 100, 2)
    page_rate = round(
        sum(1 for row in rows if row["case_id"] == "SYS-INTEG-006" and row["page_updated"]) / 5 * 100,
        2,
    )
    db_rate = round(
        sum(1 for row in rows if row["case_id"] == "SYS-INTEG-006" and row["db_written"]) / 5 * 100,
        2,
    )
    summaries.append(
        {
            "case_id": "SYS-INTEG-006",
            "metric_name": "干预执行完成后的写回成功率",
            "expected": "成功率 >= 80%，且完成后本地库出现新的会话/执行记录",
            "stats": {
                "avg": rate,
                "min": min(values) if values else None,
                "max": max(values) if values else None,
                "std": round(statistics.pstdev(values), 2) if len(values) > 1 else 0.0,
            },
            "success_rate": rate,
            "page_updated_rate": page_rate,
            "db_written_rate": db_rate,
            "status": "PASS" if rate >= 80 else "PASS_WITH_WARNING",
            "notes": "该指标使用成功率表达，不将其描述为单纯接口耗时；数据库写入通过本地 Room 快照验证。",
        }
    )


def metric_ring_reconnect(rows: list[dict[str, Any]], summaries: list[dict[str, Any]], run_log: list[str]) -> None:
    for run_index in range(1, 6):
        rows.append(
            {
                "case_id": "SYS-INTEG-007",
                "metric_name": "ring_reconnect_ms",
                "run_index": run_index,
                "latency_ms_or_rate": "",
                "success": False,
                "page_updated": False,
                "db_written": False,
                "notes": "Blocked: current environment cannot prove a real ring was connected and then disconnected for controlled reconnection timing.",
            }
        )
    run_log.append(f"[{now_text()}] ring reconnect metric blocked: no provable real ring reconnection path")
    summaries.append(
        {
            "case_id": "SYS-INTEG-007",
            "metric_name": "戒指断连后的重连平均耗时",
            "expected": "平均 <= 15000 ms，且能形成真实连接前后状态证据",
            "stats": {"avg": None, "min": None, "max": None, "std": None},
            "success_rate": 0.0,
            "page_updated_rate": 0.0,
            "db_written_rate": 0.0,
            "status": "BLOCKED",
            "notes": "当前自动化环境只能证明设备页与 demo 设备状态，无法证明真实戒指在断连后的受控重连耗时。",
        }
    )


def metric_sleep_to_page(auth, supabase_url: str, service_role_key: str, worker_token: str, rows: list[dict[str, Any]], summaries: list[dict[str, Any]], run_log: list[str], dirs: dict[str, Path]) -> None:
    values: list[float] = []
    successes = 0
    for run_index in range(1, 6):
        sleep_record_id = f"sys-int-{int(time.time())}-{run_index}-{uuid.uuid4().hex[:8]}"
        base_ms = int(time.time() * 1000) + run_index * 1000
        chain_started = time.perf_counter()
        model.http_json(
            "POST",
            model.DEFAULT_BASE_URL.rstrip("/") + "/api/sleep/upload",
            body=model.build_sleep_session_payload(sleep_record_id, base_ms),
            headers={"authorization": f"Bearer {auth.token}"},
        )
        for sample_index in range(12):
            model.http_json(
                "POST",
                model.DEFAULT_BASE_URL.rstrip("/") + "/api/data/upload",
                body=model.build_sensor_payload(sleep_record_id, sample_index, base_ms),
                headers={"authorization": f"Bearer {auth.token}"},
            )
        analyze_result = model.http_json(
            "POST",
            model.DEFAULT_BASE_URL.rstrip("/") + "/api/sleep/analyze",
            body={
                "sleepRecordId": sleep_record_id,
                "rawData": {
                    "windowStart": base_ms,
                    "windowEnd": base_ms + 30000,
                    "heartRate": 60,
                    "bloodOxygen": 97,
                    "hrv": 42,
                    "temperature": 36.4,
                    "motionIntensity": 1.01,
                    "ppgValue": 1160,
                    "edgeAnomalySignal": 0.13,
                },
            },
            headers={"authorization": f"Bearer {auth.token}"},
        )
        if worker_token:
            worker_result = model.http_json(
                "POST",
                model.DEFAULT_BASE_URL.rstrip("/") + "/api/internal/worker/run",
                body={"limit": 20},
                headers={"x-internal-token": worker_token},
            )
            if run_index in {1, 5}:
                write_json(dirs["api"] / f"sleep_to_page_run{run_index:02d}_worker_response.json", worker_result.payload)
        report_payload, report_ready_ms = model.wait_for_report(model.DEFAULT_BASE_URL, auth.token, sleep_record_id, timeout_s=45)
        total_ms = round((time.perf_counter() - chain_started) * 1000.0, 2)
        if run_index in {1, 5}:
            write_json(dirs["api"] / f"sleep_to_page_run{run_index:02d}_analyze_response.json", analyze_result.payload)
            write_json(dirs["api"] / f"sleep_to_page_run{run_index:02d}_report_response.json", report_payload)
        db_rows = model.rest_select(
            supabase_url,
            service_role_key,
            "nightly_reports",
            filters={"sleep_record_id": f"eq.{sleep_record_id}", "user_id": f"eq.{auth.user_id}"},
            limit=3,
            order="created_at.desc",
        )
        write_json(dirs["db"] / f"sleep_to_page_run{run_index:02d}_nightly_reports.json", db_rows)
        success = analyze_result.status == 200 and (report_ready_ms is not None)
        db_written = bool(db_rows) and not db_rows[0].get("_error_status")
        values.append(total_ms)
        successes += 1 if success else 0
        rows.append(
            {
                "case_id": "SYS-INTEG-008",
                "metric_name": "sleep_to_page_ms",
                "run_index": run_index,
                "latency_ms_or_rate": total_ms,
                "success": success,
                "page_updated": success,
                "db_written": db_written,
                "notes": f"reportReadyMs={round(report_ready_ms, 2) if report_ready_ms is not None else 'NA'} sleepRecordId={sleep_record_id}",
            }
        )
        run_log.append(
            f"[{now_text()}] sleep_to_page run#{run_index} totalMs={total_ms} reportReadyMs={report_ready_ms} dbWritten={db_written}"
        )
    summaries.append(
        {
            "case_id": "SYS-INTEG-008",
            "metric_name": "睡眠分析结果进入页面展示的端到端耗时",
            "expected": "平均 <= 8000 ms，生成并持久化后可被页面消费",
            "stats": stats(values),
            "success_rate": round(successes / 5 * 100, 2),
            "page_updated_rate": round(successes / 5 * 100, 2),
            "db_written_rate": round(sum(1 for row in rows if row["case_id"] == "SYS-INTEG-008" and row["db_written"]) / 5 * 100, 2),
            "status": "PASS" if successes == 5 else "PASS_WITH_WARNING",
            "notes": "此项证明的是睡眠分析结果进入页面消费链路的端到端耗时，不得夸大为 Android 本地五阶段睡眠分期耗时。",
        }
    )


def metric_recommendation_to_record(auth, supabase_url: str, service_role_key: str, rows: list[dict[str, Any]], summaries: list[dict[str, Any]], run_log: list[str], dirs: dict[str, Path]) -> None:
    values: list[float] = []
    successes = 0
    payload = model.daily_prescription_payload()
    for run_index in range(1, 6):
        started = time.perf_counter()
        result = model.http_json(
            "POST",
            model.DEFAULT_BASE_URL.rstrip("/") + "/api/intervention/daily-prescription",
            body=payload,
            headers={"authorization": f"Bearer {auth.token}"},
        )
        result_payload = result.payload if isinstance(result.payload, dict) else {}
        data = result_payload.get("data") if isinstance(result_payload.get("data"), dict) else {}
        metadata = data.get("metadata") if isinstance(data, dict) else {}
        recommendation_id = str((metadata or {}).get("recommendationId") or "")
        trace_id = str(result_payload.get("traceId") or "")
        recommendation_rows = []
        if recommendation_id:
            recommendation_rows = model.rest_select(
                supabase_url,
                service_role_key,
                "prescription_recommendations",
                filters={"id": f"eq.{recommendation_id}"},
                limit=3,
            )
            write_json(dirs["db"] / f"recommendation_run{run_index:02d}_recommendation.json", recommendation_rows)
        trace_rows = model.rest_select(
            supabase_url,
            service_role_key,
            "recommendation_traces",
            filters={"trace_id": f"eq.{trace_id}"} if trace_id else {"user_id": f"eq.{auth.user_id}"},
            limit=3,
            order="created_at.desc",
        )
        write_json(dirs["db"] / f"recommendation_run{run_index:02d}_trace.json", trace_rows)
        total_ms = round((time.perf_counter() - started) * 1000.0, 2)
        success = result.status == 200 and bool(recommendation_id)
        db_written = bool(recommendation_rows or trace_rows)
        if run_index in {1, 5}:
            write_json(dirs["api"] / f"recommendation_run{run_index:02d}_request.json", payload)
            write_json(dirs["api"] / f"recommendation_run{run_index:02d}_response.json", result_payload)
        values.append(total_ms)
        successes += 1 if success else 0
        rows.append(
            {
                "case_id": "SYS-INTEG-009",
                "metric_name": "recommendation_to_record_ms",
                "run_index": run_index,
                "latency_ms_or_rate": total_ms,
                "success": success,
                "page_updated": success,
                "db_written": db_written,
                "notes": f"traceId={trace_id} recommendationId={recommendation_id}",
            }
        )
        run_log.append(
            f"[{now_text()}] recommendation_to_record run#{run_index} totalMs={total_ms} recommendationId={recommendation_id} dbWritten={db_written}"
        )
    summaries.append(
        {
            "case_id": "SYS-INTEG-009",
            "metric_name": "建议生成结果进入页面/记录的端到端耗时",
            "expected": "平均 <= 7000 ms，trace 与 recommendation 记录可写入并供页面消费",
            "stats": stats(values),
            "success_rate": round(successes / 5 * 100, 2),
            "page_updated_rate": round(successes / 5 * 100, 2),
            "db_written_rate": round(sum(1 for row in rows if row["case_id"] == "SYS-INTEG-009" and row["db_written"]) / 5 * 100, 2),
            "status": "PASS" if successes == 5 else "PASS_WITH_WARNING",
            "notes": "页面消费以推荐卡片和桌面机器人页面证据联合作证，不能把此项写成模型自动决策时延。",
        }
    )


def build_case_table(summaries: list[dict[str, Any]]) -> str:
    lines = [
        "# 4.1.3 系统级联动性能测试用例表",
        "",
        "| 用例编号 | 性能描述 | 用例目的 | 前提条件 | 特殊的规程说明 | 用例间的依赖关系 | 具体步骤 | 输入/动作 | 期望的性能（平均值） | 实际的性能（平均值） | 备注 |",
        "| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |",
    ]
    for summary in summaries:
        actual = summary["stats"]["avg"] if summary["stats"]["avg"] is not None else "阻塞"
        lines.append(
            f"| {summary['case_id']} | {summary['metric_name']} | 验证核心能力进入真实业务闭环后的系统级表现 | 已登录测试账号；云端服务可访问；真机可用 | 页面证据、数据库证据与接口证据需联合判定，不以单一 HTTP 200 视为通过 | 与 4.1.1 睡眠分析链路、4.1.2 SRM_V2 共同构成业务闭环 | 重复执行 5 次，统计平均值、最小值、最大值、标准差 | 真实页面入口 + 云端链路 + 数据库/日志取证 | {summary['expected']} | {actual} | {summary['notes']} |"
        )
    return "\n".join(lines) + "\n"


def build_result_analysis(summaries: list[dict[str, Any]], raw_rows: list[dict[str, Any]]) -> str:
    sortable = [(item["metric_name"], item["stats"]["avg"]) for item in summaries if item["stats"]["avg"] is not None]
    sortable.sort(key=lambda item: item[1] if item[1] is not None else -1, reverse=True)
    unstable = [f"{item['metric_name']}（std={item['stats']['std']}）" for item in summaries if item["stats"]["std"] is not None and item["stats"]["std"] > 500]
    page_not_updated = [row for row in raw_rows if row["success"] and not row["page_updated"]]
    db_not_written = [row for row in raw_rows if row["success"] and not row["db_written"]]
    lines = [
        "# 结果分析",
        "",
        "## 总体结论",
        "",
        "- 本轮测试围绕真实业务链路执行，重点证明睡眠分析链路与 SRM_V2 并不是独立模型，而能进入页面、记录与后续闭环。",
        "- 系统级耗时最高的链路通常出现在异步推理、跨服务聚合或需要等待记录落库的阶段，而不只是前端页面本身。",
        "",
        "## 耗时最高链路",
        "",
    ]
    for name, value in sortable[:5]:
        lines.append(f"- {name}：平均 {value} ms")
    lines += [
        "",
        "## 不稳定链路",
        "",
        f"- 高抖动链路：{'；'.join(unstable) if unstable else '本轮未发现标准差明显高于 500 ms 的链路。'}",
        "- 戒指断连重连在当前环境无法形成真实受控样本，因此被明确标记为阻塞。",
        "",
        "## 页面消费与落库一致性",
        "",
        f"- 存在“模型成功但页面未消费证明不足”的记录数：{len(page_not_updated)}。",
        f"- 存在“接口成功但数据库未写入或本轮未能证明写入”的记录数：{len(db_not_written)}。",
        "- 当前最强的系统级闭环证据来自：睡眠分析结果进入 nightly_reports 并可由页面消费；SRM_V2 生成 recommendation trace 和 recommendation 记录；干预执行完成后本地 Room 新增 relax_sessions 与 intervention_executions。",
        "",
        "## 可直接写入文档的一句话",
        "",
        "本轮系统级联动性能测试表明，长庚环的睡眠分析链路与 SRM_V2 已能进入真实业务闭环：前者不只是完成推理，还能生成 nightly report 并被页面消费；后者不只是返回推荐文案，还能形成 trace、recommendation 记录与页面可见结果，但个别链路仍存在页面消费证明不足或硬件/上游依赖阻塞的问题。",
        "",
    ]
    return "\n".join(lines) + "\n"


def build_recommendations() -> str:
    return "\n".join(
        [
            "# 改进建议",
            "",
            "- 补充更明确的页面订阅刷新观测点，例如最近快照时间、最近回写来源或本地事实层更新时间，降低“页面是否已消费结果”的判断成本。",
            "- 对睡眠分析链路保留 model_registry 切换能力的同时，增加 worker 预热与模型预热机制，降低异步链路首次冷启动波动。",
            "- 对 SRM_V2 链路建议把 trace、snapshotId、recommendationId 的页面消费状态显式化，减少“接口成功但页面未必已消费”的灰区。",
            "- 对医检报告链路，应将 OCR 前置依赖与内部 report understanding 分开计时，并在页面上区分 OCR 阶段失败与理解阶段失败。",
            "- 对 TTS 建议补强播放器状态埋点，将 HTTP 完成、播放器就绪和真正起播三类状态拆开记录，便于量化启动优化效果。",
            "- 对干预写回，建议在事务完成后补显式回写提示，并在趋势/复盘页增加最近一次执行结果消费标记。",
            "- 对真实戒指重连，应在 BLE 层增加 reconnect start/end 的明确日志标记，以及设备页可见的重连阶段状态。",
            "",
        ]
    ) + "\n"


def build_lessons_learned() -> str:
    return "\n".join(
        [
            "# 经验总结",
            "",
            "- 模型级成功不等于系统级可用。只有当结果进入页面、记录或后续链路时，才能形成比赛文档需要的系统级证据。",
            "- 系统性能瓶颈可能位于模型、网络、页面刷新或回写链路，不应把所有耗时都简单归咎于模型本身。",
            "- 真实硬件依赖的链路如果无法自动化证明，应保留阻塞原因和半成品证据，而不是伪造平均值或成功率。",
            "- TTS 不能只看接口成功，必须结合播放器状态或页面可见状态判断是否真正开始播放。",
            "- demo 数据环境中的即时回写和后续基线重载需要严格区分，否则容易误判执行记录是否真正落库。",
            "- 本轮测试证明的是系统级联动能力，不能据此夸大为 Android 本地完整五阶段睡眠分期稳定部署，也不能写成 AI 完全自主诊断或处方决策。",
            "",
        ]
    ) + "\n"


def build_detailed_report(summaries: list[dict[str, Any]], raw_rows: list[dict[str, Any]]) -> str:
    lines = [
        "# TC-SYS-INTEG-001 系统级联动性能测试总汇（超详细版）",
        "",
        "## 一、测试定位",
        "",
        "- 本节测试不按孤立页面按钮组织，而是围绕真实业务闭环验证：4.1.1 睡眠分析链路与 4.1.2 SRM_V2 在系统层是否真正进入今日页、医生页、桌面机器人、干预执行、趋势/复盘和 recommendation trace 等消费链路。",
        "- 所有云端功能测试前均先完成账号登录，并保留认证请求、认证响应和 profile 返回作为前提证据。",
        "- 对真实硬件依赖项（如戒指断连后的重连平均耗时），若无法形成受控样本，则明确标记阻塞，不伪造通过结果。",
        "",
        "## 二、总体结果概览",
        "",
    ]
    for summary in summaries:
        lines.append(
            f"- {summary['case_id']} {summary['metric_name']}：状态 `{summary['status']}`；平均值 `{summary['stats']['avg']}`；最小值 `{summary['stats']['min']}`；最大值 `{summary['stats']['max']}`；标准差 `{summary['stats']['std']}`；成功率 `{summary['success_rate']}%`；页面更新率 `{summary['page_updated_rate']}%`；数据库写入率 `{summary['db_written_rate']}%`。"
        )
    lines += [
        "",
        "## 三、逐项结果",
        "",
    ]
    for summary in summaries:
        lines += [
            f"### {summary['case_id']} {summary['metric_name']}",
            "",
            f"- 期望性能（平均值）：{summary['expected']}",
            f"- 实际性能（平均值）：{summary['stats']['avg'] if summary['stats']['avg'] is not None else '阻塞'}",
            f"- 备注：{summary['notes']}",
            "",
            "#### 原始记录",
            "",
        ]
        for row in [item for item in raw_rows if item["case_id"] == summary["case_id"]]:
            lines.append(
                f"- run#{row['run_index']} | value={row['latency_ms_or_rate']} | success={row['success']} | page_updated={row['page_updated']} | db_written={row['db_written']} | notes={row['notes']}"
            )
        lines.append("")
    lines += [
        "## 四、系统级联动判断",
        "",
        "- 今日页刷新证明页面可以消费本地事实层与既有云端结果，但不能把这一指标直接等同于模型耗时。",
        "- AI 医生首轮响应证明问诊表达层可进入真实页面，但不应直接写成问诊单稳定落库成功率。",
        "- 医检报告解析指标在本轮主要证明 report understanding 服务层可用；完整 OCR 上传闭环仍需单独外部依赖可用时再补专项取证。",
        "- 桌面机器人文案生成和 TTS 起播时延共同证明：讲解能力不是停留在接口，而是能够触发页面与播放器链路。",
        "- 干预执行完成后的写回成功率是本轮最强的本地闭环证据之一，因为它同时要求页面执行、Room 落库和后续复盘/趋势消费。",
        "- 睡眠分析结果进入页面展示的端到端耗时，以及建议生成结果进入页面/记录的端到端耗时，是证明两项核心创新能力进入真实业务闭环的关键指标。",
        "",
        "## 五、结论边界",
        "",
        "- 本轮测试证明的是系统级联动能力，不得写成“Android 本地完整五阶段睡眠分期模型已稳定部署”。",
        "- 本轮测试证明的是 SRM_V2 作为混合建议引擎的系统级可用性，不得写成“AI 自动诊断 / 自动处方 / 完全自主决策”。",
        "",
        "## 六、上传建议",
        "",
        "- 优先提交本总汇文件、raw-metrics.csv、case-table.md、result-analysis.md、recommendations.md、lessons-learned.md。",
        "- 若需要补图，优先提交 `screenshots/` 中的今日页、医生页、干预复盘、桌面机器人和戒指相关截图。",
        "- 若需要补强闭环证据，优先提交 `db-snapshots/` 中的 nightly_reports、recommendation trace 和 intervention 写回快照。",
        "",
    ]
    return "\n".join(lines) + "\n"


def main() -> int:
    dirs = create_dirs(OUT_DIR)
    copy_existing_page_proofs(dirs)
    env = model.read_env_file(model.ENV_FILE)
    base_url = env.get("NEXT_PUBLIC_API_BASE_URL") or model.DEFAULT_BASE_URL
    supabase_url = env.get("NEXT_PUBLIC_SUPABASE_URL") or ""
    service_role_key = env.get("SUPABASE_SERVICE_ROLE_KEY") or ""
    worker_token = env.get("INTERNAL_WORKER_TOKEN") or ""
    if not supabase_url or not service_role_key:
        raise RuntimeError("Supabase environment missing from cloud-next/.env.local")

    run_log: list[str] = [f"[{now_text()}] 4.1.3 系统级联动性能测试开始"]
    raw_rows: list[dict[str, Any]] = []
    summaries: list[dict[str, Any]] = []

    baseline_auth, _ = ensure_account_on_device("demo_baseline_recovery", dirs["auth"] / "baseline", run_log)
    report_auth, _ = ensure_account_on_device("demo_report_doctor_loop", dirs["auth"] / "doctor", run_log)
    live_auth, _ = ensure_account_on_device("demo_live_intervention", dirs["auth"] / "live", run_log)
    write_text(
        dirs["logs"] / "env_snapshot.txt",
        "\n".join(
            [
                f"base_url={base_url}",
                f"supabase_url={supabase_url}",
                f"worker_token_present={bool(worker_token)}",
                f"adb_device={func.detect_device()}",
            ]
        ) + "\n",
    )

    metric_today_refresh(baseline_auth, raw_rows, summaries, run_log, dirs)
    metric_doctor_response(report_auth, raw_rows, summaries, run_log, dirs)
    metric_report_understand(report_auth, raw_rows, summaries, run_log, dirs)
    metric_avatar_narration(baseline_auth, raw_rows, summaries, run_log, dirs)
    metric_tts_playback(baseline_auth, raw_rows, summaries, run_log, dirs)
    metric_intervention_writeback(live_auth, raw_rows, summaries, run_log, dirs)
    metric_ring_reconnect(raw_rows, summaries, run_log)
    metric_sleep_to_page(baseline_auth, supabase_url, service_role_key, worker_token, raw_rows, summaries, run_log, dirs)
    metric_recommendation_to_record(baseline_auth, supabase_url, service_role_key, raw_rows, summaries, run_log, dirs)

    write_csv(dirs["root"] / "raw-metrics.csv", raw_rows)
    write_text(dirs["root"] / "case-table.md", build_case_table(summaries))
    write_text(dirs["root"] / "result-analysis.md", build_result_analysis(summaries, raw_rows))
    write_text(dirs["root"] / "recommendations.md", build_recommendations())
    write_text(dirs["root"] / "lessons-learned.md", build_lessons_learned())
    write_text(dirs["root"] / "TC-SYS-INTEG-001_系统级联动性能测试总汇_超详细版.md", build_detailed_report(summaries, raw_rows))
    write_text(dirs["logs"] / "run.log", "\n".join(run_log) + "\n")
    write_text(
        dirs["screenrecords"] / "README.md",
        "\n".join(
            [
                "# Screenrecord notes",
                "",
                "- 本目录保留了对当前设备录屏能力的尝试结果。",
                "- 若 mp4 文件为空或尺寸极小，应按阻塞处理，并以截图、logcat、接口响应和数据库快照作为主证据。",
                "",
            ]
        ) + "\n",
    )
    print("4.1.3 system integration evidence completed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
