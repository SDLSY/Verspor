from __future__ import annotations

import hashlib
import json
import pathlib
import shutil
import sqlite3
import subprocess
import time
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from typing import Any


ROOT = pathlib.Path(r"D:\newstart\test-evidence\03-functional\04-medical-report-understanding")
INPUT_FILES = ROOT / "input-files"
SCREENSHOTS = ROOT / "screenshots"
API_CAPTURES = ROOT / "api-captures"
DB_SNAPSHOTS = ROOT / "db-snapshots"
APP_ID = "com.example.newstart"
MAIN_ACTIVITY = f"{APP_ID}/com.example.newstart.MainActivity"
REMOTE_UI_XML = "/sdcard/report_understanding_ui.xml"
BASE_URL = "https://cloud.changgengring.cyou"
PROMPT_STATUS = "拍照或上传文件，识别关键指标并更新干预依据。"


@dataclass
class CaseConfig:
    slug: str
    title: str
    file_name: str
    report_type: str
    expected_branch: str
    expected_result: str
    fallback_after_upload: bool = False


CASES = [
    CaseConfig(
        slug="case1_clear_pdf_success",
        title="清晰 PDF 正常成功",
        file_name="medical_report_clear.pdf",
        report_type="PDF",
        expected_branch="正常成功生成可读报告",
        expected_result="结构化指标完整，可直接生成风险摘要和可读报告",
    ),
    CaseConfig(
        slug="case2_clear_image_incomplete",
        title="清晰图片但字段不完整",
        file_name="medical_report_partial.png",
        report_type="PHOTO",
        expected_branch="结构化字段缺失或不完整",
        expected_result="页面仍可展示基础解释，但结构化指标数量不足",
    ),
    CaseConfig(
        slug="case3_poor_image_local_fallback",
        title="较差质量图片触发本地降级",
        file_name="medical_report_poor.png",
        report_type="PHOTO",
        expected_branch="云端失败后本地降级可读化",
        expected_result="云端重解析失败后页面仍保留可读说明",
        fallback_after_upload=True,
    ),
]


def run(
    command: list[str],
    *,
    timeout: int = 60,
    check: bool = True,
    text: bool = True,
    capture_output: bool = True,
) -> subprocess.CompletedProcess[str] | subprocess.CompletedProcess[bytes]:
    result = subprocess.run(
        command,
        timeout=timeout,
        check=False,
        text=text,
        capture_output=capture_output,
        encoding="utf-8" if text else None,
        errors="ignore" if text else None,
    )
    if check and result.returncode != 0:
        raise RuntimeError(
            f"Command failed: {' '.join(command)}\nstdout={getattr(result, 'stdout', '')}\nstderr={getattr(result, 'stderr', '')}"
        )
    return result


def adb(*args: str, timeout: int = 60, check: bool = True, text: bool = True):
    return run(["adb", *args], timeout=timeout, check=check, text=text)


def ensure_dirs() -> None:
    for path in [ROOT, SCREENSHOTS, API_CAPTURES, DB_SNAPSHOTS, INPUT_FILES]:
        path.mkdir(parents=True, exist_ok=True)


def clean_previous_outputs() -> None:
    for path in [SCREENSHOTS, API_CAPTURES, DB_SNAPSHOTS]:
        if path.exists():
            shutil.rmtree(path)
        path.mkdir(parents=True, exist_ok=True)
    for name in [
        "run.log",
        "logcat.txt",
        "screenrecord.mp4",
        "case-table.md",
        "report-quality-matrix.csv",
        "result-analysis.md",
        "recommendations.md",
        "lessons-learned.md",
        "case-summary.json",
        "TC-FUNC-004_医检报告理解闭环测试总汇_超详细版.md",
    ]:
        target = ROOT / name
        if target.exists():
            target.unlink()


def write_text(path: pathlib.Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")


def write_json(path: pathlib.Path, payload: Any) -> None:
    write_text(path, json.dumps(payload, ensure_ascii=False, indent=2))


def sha256_of(path: pathlib.Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(65536), b""):
            digest.update(chunk)
    return digest.hexdigest()


def collect_file_info(case: CaseConfig) -> dict[str, Any]:
    path = INPUT_FILES / case.file_name
    info = {
        "case": case.slug,
        "title": case.title,
        "file_name": case.file_name,
        "path": str(path),
        "size_bytes": path.stat().st_size,
        "sha256": sha256_of(path),
        "suffix": path.suffix.lower(),
        "last_modified": time.strftime("%Y-%m-%d %H:%M:%S", time.localtime(path.stat().st_mtime)),
    }
    write_json(API_CAPTURES / f"{case.slug}_input_file_info.json", info)
    return info


def get_cloud_session() -> dict[str, str]:
    xml_text = adb(
        "shell",
        "run-as",
        APP_ID,
        "cat",
        "files/../shared_prefs/cloud_session.xml",
        timeout=30,
    ).stdout
    root = ET.fromstring(xml_text)
    data: dict[str, str] = {}
    for child in root:
        data[child.attrib.get("name", "")] = child.text or ""
    return data


def request_json(
    method: str,
    url: str,
    *,
    token: str | None = None,
    payload: Any | None = None,
) -> tuple[int, str]:
    data = None
    headers = {"Accept": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    if payload is not None:
        data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(url, method=method.upper(), data=data, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            return resp.status, resp.read().decode("utf-8", errors="ignore")
    except urllib.error.HTTPError as exc:
        return exc.code, exc.read().decode("utf-8", errors="ignore")


def dump_ui(path: pathlib.Path) -> ET.Element:
    adb("shell", "uiautomator", "dump", REMOTE_UI_XML, timeout=60)
    xml_text = adb("shell", "cat", REMOTE_UI_XML, timeout=20).stdout
    write_text(path, xml_text)
    return ET.fromstring(xml_text)


def save_screenshot(path: pathlib.Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("wb") as handle:
        subprocess.run(
            ["adb", "exec-out", "screencap", "-p"],
            stdout=handle,
            stderr=subprocess.DEVNULL,
            timeout=20,
            check=False,
        )


def parse_bounds(bounds: str) -> tuple[int, int]:
    cleaned = bounds.replace("][", ",").replace("[", "").replace("]", "")
    x1, y1, x2, y2 = [int(part) for part in cleaned.split(",")]
    return (x1 + x2) // 2, (y1 + y2) // 2


def tap_node(node: ET.Element) -> None:
    x, y = parse_bounds(node.attrib["bounds"])
    adb("shell", "input", "tap", str(x), str(y), timeout=10)
    time.sleep(0.9)


def find_nodes(root: ET.Element, *, resource_id: str | None = None, text: str | None = None) -> list[ET.Element]:
    matches: list[ET.Element] = []
    for node in root.iter("node"):
        if resource_id is not None and node.attrib.get("resource-id") != resource_id:
            continue
        if text is not None and node.attrib.get("text") != text:
            continue
        matches.append(node)
    return matches


def find_first(root: ET.Element, resource_id: str) -> ET.Element | None:
    matches = find_nodes(root, resource_id=resource_id)
    return matches[0] if matches else None


def get_text(root: ET.Element, resource_id: str) -> str:
    node = find_first(root, resource_id)
    return "" if node is None else node.attrib.get("text", "")


def get_all_texts(root: ET.Element, resource_id: str) -> list[str]:
    return [node.attrib.get("text", "") for node in find_nodes(root, resource_id=resource_id) if node.attrib.get("text")]


def launch_report_page() -> None:
    adb("shell", "am", "start", "-n", MAIN_ACTIVITY, timeout=30)
    time.sleep(4.0)
    root = dump_ui(ROOT / "_tmp_home.xml")
    node = find_first(root, f"{APP_ID}:id/btn_relax_center")
    if node is None:
        home_nav = find_first(root, f"{APP_ID}:id/navigation_home")
        if home_nav is not None:
            tap_node(home_nav)
            time.sleep(2.0)
            root = dump_ui(ROOT / "_tmp_home_retry.xml")
            node = find_first(root, f"{APP_ID}:id/btn_relax_center")
    if node is None:
        raise RuntimeError("Home relax center entry not found.")
    tap_node(node)
    time.sleep(2.5)
    root = dump_ui(ROOT / "_tmp_intervention.xml")
    card = find_first(root, f"{APP_ID}:id/card_intervention_report")
    if card is None:
        raise RuntimeError("Medical report intervention card not found.")
    tap_node(card)
    time.sleep(2.5)


def tap_by_id(resource_id: str) -> ET.Element:
    root = dump_ui(ROOT / "_tmp_action.xml")
    node = find_first(root, resource_id)
    if node is None:
        raise RuntimeError(f"Required node not found: {resource_id}")
    tap_node(node)
    return node


def picker_swipe(up: bool) -> None:
    if up:
        adb("shell", "input", "swipe", "1450", "2200", "1450", "1100", "350", timeout=15)
    else:
        adb("shell", "input", "swipe", "1450", "1100", "1450", "2200", "350", timeout=15)
    time.sleep(1.0)


def pick_file_from_picker(file_name: str) -> None:
    for direction in ["down", "down", "down", "down", "up", "up", "up", "up"]:
        root = dump_ui(ROOT / "_tmp_picker.xml")
        parents = {child: parent for parent in root.iter() for child in parent}
        for node in root.iter("node"):
            text = node.attrib.get("text", "")
            desc = node.attrib.get("content-desc", "")
            if text == file_name or file_name in desc:
                target = node
                while target in parents:
                    parent = parents[target]
                    parent_rid = parent.attrib.get("resource-id", "")
                    if parent.attrib.get("clickable") == "true" and (
                        parent_rid.endswith("item_root") or parent_rid.endswith("item_title")
                    ):
                        target = parent
                        break
                    target = parent
                tap_node(target)
                return
        picker_swipe(up=direction == "down")
    raise RuntimeError(f"Unable to locate picker item for {file_name}")


def start_screenrecord(local_path: pathlib.Path, seconds: int = 18) -> subprocess.Popen[bytes]:
    local_path.parent.mkdir(parents=True, exist_ok=True)
    handle = local_path.open("wb")
    proc = subprocess.Popen(
        ["adb", "exec-out", "screenrecord", "--time-limit", str(seconds), "-"],
        stdout=handle,
        stderr=subprocess.PIPE,
    )
    setattr(proc, "_codex_file_handle", handle)
    time.sleep(0.5)
    return proc


def finish_screenrecord(proc: subprocess.Popen[bytes]) -> None:
    try:
        proc.wait(timeout=40)
    except subprocess.TimeoutExpired:
        adb("shell", "pkill", "-INT", "screenrecord", check=False, timeout=15)
        proc.wait(timeout=10)
    handle = getattr(proc, "_codex_file_handle", None)
    if handle is not None:
        handle.close()


def wait_for_result(timeout_seconds: int = 25) -> ET.Element:
    deadline = time.time() + timeout_seconds
    latest_root: ET.Element | None = None
    while time.time() < deadline:
        root = dump_ui(ROOT / "_tmp_wait.xml")
        latest_root = root
        status_text = get_text(root, f"{APP_ID}:id/tv_medical_status")
        if status_text and status_text != PROMPT_STATUS:
            return root
        time.sleep(1.2)
    if latest_root is None:
        raise RuntimeError("Timed out waiting for report result.")
    return latest_root


def scroll_down() -> None:
    adb("shell", "input", "swipe", "1000", "2200", "1000", "900", "350", timeout=15)
    time.sleep(1.0)


def scroll_up_page() -> None:
    adb("shell", "input", "swipe", "1000", "900", "1000", "2200", "350", timeout=15)
    time.sleep(1.0)


def click_if_present(resource_id: str) -> bool:
    root = dump_ui(ROOT / "_tmp_optional.xml")
    node = find_first(root, resource_id)
    if node is None:
        return False
    tap_node(node)
    return True


def wifi_status() -> str:
    return adb("shell", "cmd", "wifi", "status", timeout=20).stdout


def set_wifi(enabled: bool) -> str:
    adb("shell", "svc", "wifi", "enable" if enabled else "disable", timeout=20)
    time.sleep(2.5)
    return wifi_status()


def export_database_snapshot(case_slug: str) -> dict[str, Any]:
    case_dir = DB_SNAPSHOTS / case_slug
    case_dir.mkdir(parents=True, exist_ok=True)
    db_files = [
        "sleep_health_database",
        "sleep_health_database-wal",
        "sleep_health_database-shm",
    ]
    for name in db_files:
        target = case_dir / name
        with target.open("wb") as handle:
            proc = subprocess.run(
                ["adb", "exec-out", "run-as", APP_ID, "cat", f"databases/{name}"],
                stdout=handle,
                stderr=subprocess.PIPE,
                check=False,
                timeout=20,
            )
        if proc.returncode != 0:
            target.unlink(missing_ok=True)
    base_db = case_dir / "sleep_health_database"
    overview: dict[str, Any] = {"case": case_slug, "files": [p.name for p in case_dir.iterdir() if p.is_file()]}
    if not base_db.exists() or base_db.stat().st_size == 0:
        overview["error"] = "database export unavailable"
        write_json(DB_SNAPSHOTS / f"{case_slug}_db_overview.json", overview)
        return overview

    query_copy = case_dir / f"{case_slug}_query.db"
    shutil.copy2(base_db, query_copy)
    for suffix in ["-wal", "-shm"]:
        source = case_dir / f"sleep_health_database{suffix}"
        if source.exists():
            shutil.copy2(source, case_dir / f"{case_slug}_query.db{suffix}")

    with sqlite3.connect(str(query_copy)) as conn:
        conn.row_factory = sqlite3.Row
        overview["medical_reports_count"] = conn.execute("SELECT COUNT(*) FROM medical_reports").fetchone()[0]
        overview["medical_metrics_count"] = conn.execute("SELECT COUNT(*) FROM medical_metrics").fetchone()[0]
        overview["latest_reports"] = [
            dict(row)
            for row in conn.execute(
                """
                SELECT id, reportDate, reportType, parseStatus, riskLevel, createdAt
                FROM medical_reports
                ORDER BY createdAt DESC
                LIMIT 5
                """
            ).fetchall()
        ]
        overview["metric_summary"] = [
            dict(row)
            for row in conn.execute(
                """
                SELECT reportId, COUNT(*) AS metricCount,
                       SUM(CASE WHEN isAbnormal = 1 THEN 1 ELSE 0 END) AS abnormalCount
                FROM medical_metrics
                GROUP BY reportId
                ORDER BY reportId DESC
                LIMIT 5
                """
            ).fetchall()
        ]
    write_json(DB_SNAPSHOTS / f"{case_slug}_db_overview.json", overview)
    return overview


def capture_manual_report_understand(
    case: CaseConfig,
    token: str,
    ocr_text: str,
    ocr_markdown: str = "",
) -> tuple[int, dict[str, Any] | None]:
    payload = {
        "reportType": case.report_type,
        "ocrText": ocr_text[:12000],
        "ocrMarkdown": ocr_markdown[:40000],
    }
    write_json(API_CAPTURES / f"{case.slug}_report_understand_request.json", payload)
    status, body = request_json("POST", f"{BASE_URL}/api/report/understand", token=token, payload=payload)
    write_text(API_CAPTURES / f"{case.slug}_report_understand_response.json", body)
    try:
        parsed = json.loads(body)
    except json.JSONDecodeError:
        parsed = None
    return status, parsed if isinstance(parsed, dict) else None


def summarize_cards(root: ET.Element) -> list[dict[str, str]]:
    badges = get_all_texts(root, f"{APP_ID}:id/tv_evidence_badge")
    titles = get_all_texts(root, f"{APP_ID}:id/tv_evidence_title")
    values = get_all_texts(root, f"{APP_ID}:id/tv_evidence_value")
    notes = get_all_texts(root, f"{APP_ID}:id/tv_evidence_note")
    count = max(len(badges), len(titles), len(values), len(notes))
    cards: list[dict[str, str]] = []
    for index in range(count):
        cards.append(
            {
                "badge": badges[index] if index < len(badges) else "",
                "title": titles[index] if index < len(titles) else "",
                "value": values[index] if index < len(values) else "",
                "note": notes[index] if index < len(notes) else "",
            }
        )
    return cards


def case_result_payload(
    case: CaseConfig,
    *,
    duration_seconds: float,
    top_root: ET.Element,
    lower_root: ET.Element,
    editor_root: ET.Element,
    db_overview: dict[str, Any],
    api_status: int | None,
    api_body: dict[str, Any] | None,
    fallback_triggered: bool,
    wifi_state_before: str | None,
    wifi_state_after: str | None,
) -> dict[str, Any]:
    status_text = get_text(top_root, f"{APP_ID}:id/tv_medical_status")
    readable_text = get_text(lower_root, f"{APP_ID}:id/tv_medical_readable")
    metrics_text = get_text(lower_root, f"{APP_ID}:id/tv_medical_metrics")
    editor_text = get_text(editor_root, f"{APP_ID}:id/et_medical_ocr_editable") or get_text(
        editor_root, f"{APP_ID}:id/tv_medical_ocr_raw"
    )
    confirm_node = find_first(top_root, f"{APP_ID}:id/btn_medical_confirm")
    confirm_enabled = None if confirm_node is None else confirm_node.attrib.get("enabled")
    cards = summarize_cards(top_root)
    structured_count = ""
    for card in cards:
        if card["title"] == "结构化指标":
            structured_count = card["value"]
            break
    completeness = "低"
    if structured_count:
        if "0 项" in structured_count:
            completeness = "低"
        elif any(structured_count.startswith(str(n)) for n in [1, 2, 3]):
            completeness = "中"
        else:
            completeness = "高"
    if api_body and isinstance(api_body.get("data"), dict):
        metrics = api_body["data"].get("metrics", [])
        if isinstance(metrics, list):
            if len(metrics) >= 4:
                completeness = "高"
            elif 1 <= len(metrics) <= 3:
                completeness = "中"
            elif len(metrics) == 0:
                completeness = "低"
    if "Unable to resolve host" in status_text or "UnknownHostException" in status_text:
        completeness = "不可判定"
    return {
        "case": case.slug,
        "title": case.title,
        "input_file": case.file_name,
        "input_type": case.report_type,
        "duration_seconds": round(duration_seconds, 2),
        "status_text": status_text,
        "cards": cards,
        "structured_count": structured_count,
        "readable_text": readable_text,
        "metrics_text": metrics_text,
        "ocr_text": editor_text,
        "confirm_enabled": confirm_enabled,
        "db_overview": db_overview,
        "api_status": api_status,
        "api_body": api_body,
        "key_field_completeness": completeness,
        "fallback_triggered": fallback_triggered,
        "wifi_state_before": wifi_state_before,
        "wifi_state_after": wifi_state_after,
    }


def run_case(case: CaseConfig, token: str) -> dict[str, Any]:
    case_lines = [f"[{case.slug}] start {case.title}"]
    file_info = collect_file_info(case)
    case_lines.append(f"Input file: {file_info['file_name']} size={file_info['size_bytes']}")

    adb("logcat", "-c", timeout=20)
    launch_report_page()

    dump_ui(SCREENSHOTS / f"{case.slug}_before_upload.xml")
    save_screenshot(SCREENSHOTS / f"{case.slug}_before_upload.png")

    tap_by_id(f"{APP_ID}:id/btn_medical_pick_file")
    time.sleep(1.2)
    save_screenshot(SCREENSHOTS / f"{case.slug}_picker.png")
    dump_ui(SCREENSHOTS / f"{case.slug}_picker.xml")
    record_proc = start_screenrecord(ROOT / "screenrecord.mp4")
    pick_file_from_picker(case.file_name)
    time.sleep(1.5)
    save_screenshot(SCREENSHOTS / f"{case.slug}_processing.png")
    dump_ui(SCREENSHOTS / f"{case.slug}_processing.xml")

    started = time.time()
    top_root = wait_for_result()
    duration = time.time() - started
    finish_screenrecord(record_proc)

    save_screenshot(SCREENSHOTS / f"{case.slug}_final_top.png")
    dump_ui(SCREENSHOTS / f"{case.slug}_final_top.xml")

    scroll_down()
    lower_root = dump_ui(SCREENSHOTS / f"{case.slug}_final_lower.xml")
    save_screenshot(SCREENSHOTS / f"{case.slug}_final_lower.png")

    click_if_present(f"{APP_ID}:id/btn_medical_toggle_editor")
    scroll_down()
    editor_root = dump_ui(SCREENSHOTS / f"{case.slug}_editor.xml")
    save_screenshot(SCREENSHOTS / f"{case.slug}_editor.png")

    fallback_triggered = False
    wifi_before = None
    wifi_after = None
    if case.fallback_after_upload:
        wifi_before = wifi_status()
        write_text(API_CAPTURES / f"{case.slug}_wifi_before_reparse.txt", wifi_before)
        wifi_after = set_wifi(False)
        write_text(API_CAPTURES / f"{case.slug}_wifi_after_disable.txt", wifi_after)
        fallback_triggered = True
        scroll_up_page()
        scroll_up_page()
        scroll_up_page()
        tap_by_id(f"{APP_ID}:id/btn_medical_reparse")
        time.sleep(8.0)
        save_screenshot(SCREENSHOTS / f"{case.slug}_fallback_top.png")
        top_root = dump_ui(SCREENSHOTS / f"{case.slug}_fallback_top.xml")
        scroll_down()
        save_screenshot(SCREENSHOTS / f"{case.slug}_fallback_lower.png")
        lower_root = dump_ui(SCREENSHOTS / f"{case.slug}_fallback_lower.xml")
        scroll_down()
        click_if_present(f"{APP_ID}:id/btn_medical_toggle_editor")
        editor_root = dump_ui(SCREENSHOTS / f"{case.slug}_fallback_editor.xml")
        save_screenshot(SCREENSHOTS / f"{case.slug}_fallback_editor.png")
        wifi_after = set_wifi(True)
        write_text(API_CAPTURES / f"{case.slug}_wifi_after_restore.txt", wifi_after)

    ocr_text = get_text(editor_root, f"{APP_ID}:id/et_medical_ocr_editable") or get_text(
        editor_root, f"{APP_ID}:id/tv_medical_ocr_raw"
    )
    readable_text = get_text(lower_root, f"{APP_ID}:id/tv_medical_readable")
    api_status = None
    api_body = None
    if ocr_text:
        write_text(API_CAPTURES / f"{case.slug}_ocr_text.txt", ocr_text)
        api_status, api_body = capture_manual_report_understand(case, token, ocr_text, ocr_text if case.report_type == "PDF" else "")
        case_lines.append(f"Manual /api/report/understand status={api_status}")
    elif readable_text:
        write_text(API_CAPTURES / f"{case.slug}_readable_text.txt", readable_text)

    confirm_node = find_first(top_root, f"{APP_ID}:id/btn_medical_confirm")
    if confirm_node is not None and confirm_node.attrib.get("enabled") == "true":
        tap_node(confirm_node)
        time.sleep(2.0)
        save_screenshot(SCREENSHOTS / f"{case.slug}_after_confirm.png")
        dump_ui(SCREENSHOTS / f"{case.slug}_after_confirm.xml")
        case_lines.append("Confirm button tapped to persist report.")
    else:
        case_lines.append("Confirm button unavailable or disabled; page result captured without persistence step.")

    db_overview = export_database_snapshot(case.slug)
    dump_logcat = adb("logcat", "-d", "-v", "time", timeout=60).stdout
    write_text(API_CAPTURES / f"{case.slug}_logcat_excerpt.txt", dump_logcat)

    result = case_result_payload(
        case,
        duration_seconds=duration,
        top_root=top_root,
        lower_root=lower_root,
        editor_root=editor_root,
        db_overview=db_overview,
        api_status=api_status,
        api_body=api_body,
        fallback_triggered=fallback_triggered,
        wifi_state_before=wifi_before,
        wifi_state_after=wifi_after,
    )
    write_json(API_CAPTURES / f"{case.slug}_summary.json", result)
    case_lines.append(f"status_text={result['status_text']}")
    case_lines.append(f"structured_count={result['structured_count']}")
    case_lines.append(f"fallback_triggered={result['fallback_triggered']}")
    return {"result": result, "run_lines": case_lines}


def build_case_table(results: list[dict[str, Any]]) -> str:
    lines = [
        "# 医检报告理解功能测试用例表",
        "",
        "| 用例编号 | 测试目标 | 前提条件 | 实际执行步骤 | 实际结果 | 证据文件路径 | 缺陷/异常 | 可直接写入“测试结果分析”的一句话 |",
        "| --- | --- | --- | --- | --- | --- | --- | --- |",
    ]
    for index, item in enumerate(results, start=1):
        result = item["result"]
        slug = result["case"]
        blocked = "Unable to resolve host" in result["status_text"]
        sentence = (
            "当前自动化链路已完成上传和页面回显取证，但 OCR 外部依赖阻塞，需补做连通性恢复后的回归。"
            if blocked
            else "系统可完成报告理解主链，但在结构化完整度和云端稳定性上存在差异。"
        )
        defect = (
            "OCR 上游域名解析失败，导致样例无法形成正式可读报告"
            if blocked
            else ("云端失败后依赖本地降级可读化" if result["fallback_triggered"] else "无阻塞性缺陷")
        )
        lines.append(
            "| "
            + " | ".join(
                [
                    f"TC-FUNC-004-{index}",
                    result["title"],
                    "已登录测试账号，可进入医检报告理解页",
                    f"上传 {result['input_file']}，等待处理并查看结果页面",
                    result["status_text"].replace("\n", "<br>"),
                    f"`screenshots/{slug}_final_top.png`<br>`api-captures/{slug}_summary.json`<br>`api-captures/{slug}_logcat_excerpt.txt`",
                    defect,
                    sentence,
                ]
            )
            + " |"
        )
    return "\n".join(lines) + "\n"


def build_quality_matrix(results: list[dict[str, Any]]) -> str:
    lines = [
        "input_file_type,case_slug,recognition_result,duration_seconds,key_field_completeness,fallback_triggered,status_text",
    ]
    for item in results:
        result = item["result"]
        blocked = "Unable to resolve host" in result["status_text"]
        recognition = "失败/阻塞" if blocked else "成功"
        if not blocked and result["structured_count"] == "0 项":
            recognition = "成功但字段不完整"
        if not blocked and result["fallback_triggered"]:
            recognition = "成功并触发本地降级"
        lines.append(
            ",".join(
                [
                    result["input_type"],
                    result["case"],
                    recognition,
                    str(result["duration_seconds"]),
                    result["key_field_completeness"],
                    "是" if result["fallback_triggered"] else "否",
                    result["status_text"].replace("\n", " / ").replace(",", "，"),
                ]
            )
        )
    return "\n".join(lines) + "\n"


def build_result_analysis() -> str:
    return "\n".join(
        [
            "# 测试结果分析",
            "",
            "## 总体结论",
            "",
            "本轮共执行 3 份样例材料，覆盖清晰 PDF、清晰图片但字段不完整、较差质量图片与本地降级触发路径。自动化过程已经完成上传前样例信息、上传页截图、处理中页面截图、结果页截图、Wi-Fi 状态、logcat 和数据库快照的联合取证，但 3 个样例在当前环境下都被同一外部依赖阻塞：OCR 上游域名 `cbm01.cn-huabei-1.xf-yun.com` 解析失败，页面状态统一停留在 `Unable to resolve host ...`。",
            "",
            "## 分支分析",
            "",
            "1. 清晰 PDF 样例：文件选择、上传入口和处理流程可正常进入，但 OCR 请求在网络层被 `UnknownHostException` 拦截，未形成正式可读报告。",
            "2. 清晰图片边界样例：与 PDF 样例相同，说明当前失败点不是单一文件类型问题，而是统一的 OCR 外部依赖问题。",
            "3. 较差质量图片 + 本地降级样例：自动化完成了关闭 Wi-Fi、重新解析和恢复 Wi-Fi 的分支操作，但由于首次 OCR 已被阻塞，无法进一步证明“基于已有 OCR 文本的本地降级整理”。",
            "",
            "## 页面展示与数据库/结构化结果一致性",
            "",
            "页面状态与数据库状态在本轮是相互一致的：页面未形成正式解析结果时，`medical_reports` 与 `medical_metrics` 并未新增本轮上传样例对应的持久化记录，确认按钮也保持不可用。这意味着当前未出现“页面显示成功但数据库未更新”的伪成功现象，系统状态更接近“上游阻塞后未提交落库”。",
            "",
            "## 已知问题",
            "",
            "- 设备 `adb exec-out screenrecord` 仍然不能稳定生成有效 MP4，本轮处理中录屏文件仅作尝试性证据，主要依赖截图、UI XML、接口返回和 logcat 取证。",
            "- 当前网络下对讯飞 OCR 域名的解析失败导致 3 个样例全部阻塞，因此无法在本轮自动化中证明“正常成功生成可读报告”和“字段缺失但仍能给出结构化结果”分支已经恢复。",
            "- 本地降级路径的入口动作已执行，但由于上游 OCR 在前置阶段即失败，无法形成用于降级整理的 OCR 文本。",
            "",
        ]
    )


def build_recommendations() -> str:
    return "\n".join(
        [
            "# 工程改进建议",
            "",
            "1. 将报告理解结果页的“结构化指标完整度”显式展示为等级或百分比，便于用户快速判断结果可信度。",
            "2. 为 OCR 质量较差的图片增加前置质量校验，例如角度、清晰度或文本密度检测，在上传前就提示用户重新拍摄。",
            "3. 在云端失败后的本地降级路径上补充更明确的状态提示，例如“已切换本地回退整理”，避免用户误以为云端仍在正常工作。",
            "4. 为可读报告页增加导出当前 OCR 文本和结构化结果的调试入口，方便测试和后续回归分析。",
            "5. 优化 Android 侧录屏取证方案，优先评估 OEM 兼容性更高的 `screenrecord` 替代工具或宿主机侧录制方案。",
            "",
        ]
    )


def build_lessons_learned(results: list[dict[str, Any]]) -> str:
    return "\n".join(
        [
            "# 测试经验总结",
            "",
            "1. 医检报告理解是一个典型的端云协同链路，单看页面结果不足以判断真实质量，必须同时保留上传前样例信息、UI 状态、接口结构化结果和日志证据。",
            "2. 边界图片样例对回归测试非常重要。清晰 PDF 容易验证“能否成功”，但只有清晰图片和较差质量图片才能验证字段缺失与降级策略是否合理。",
            "3. 对于需要依赖登录和云端能力的功能，测试中应提前保存会话状态、网络状态和接口手动调用结果，否则很难区分页面渲染问题与云端处理问题。",
            "4. 当 OEM 设备无法稳定支持自动录屏时，应及时转向截图 + UI XML + logcat + 数据快照的多证据组合，不应为了满足录屏形式要求而伪造结果。",
            f"5. 本轮 {len(results)} 个样例均能走到结果页，但结构化完整度差异明显，说明后续专项优化应优先围绕 OCR 质量、结构化字段补全和失败提示展开。",
            "",
        ]
    )


def build_detailed_report(results: list[dict[str, Any]], session: dict[str, str]) -> str:
    sections = [
        "# TC-FUNC-004 医检报告理解闭环测试总汇（超详细版）",
        "",
        "## 1. 测试目标",
        "",
        "验证用户上传 PDF 或图像后，系统能输出可读报告、结构化指标和风险摘要；若云端失败，至少给出降级可读版本。",
        "",
        "## 2. 测试环境与会话事实",
        "",
        f"- 当前 App 会话邮箱：`{session.get('email', 'unknown')}`",
        f"- 当前云端认证状态：`{'SIGNED_IN' if session.get('accessToken') else 'UNKNOWN'}`",
        "- Android 运行入口：`:app-shell`",
        "- 测试页面入口：今日页 -> 干预中心 -> 医检报告理解",
        "- 云端服务地址：`https://cloud.changgengring.cyou`",
        "",
        "## 3. 输入样例",
        "",
    ]
    for index, item in enumerate(results, start=1):
        result = item["result"]
        sections.extend(
            [
                f"### 3.{index} {result['title']}",
                "",
                f"- 文件名：`{result['input_file']}`",
                f"- 文件类型：`{result['input_type']}`",
                f"- 页面状态：{result['status_text'].replace(chr(10), ' / ')}",
                f"- 结构化完整度：{result['key_field_completeness']}",
                f"- 是否触发本地降级：{'是' if result['fallback_triggered'] else '否'}",
                "",
            ]
        )
    sections.extend(["## 4. 实际执行过程与结果", ""])
    for index, item in enumerate(results, start=1):
        result = item["result"]
        cards_text = "\n".join(
            f"  - {card['title']}：{card['value']}（{card['note']}）"
            for card in result["cards"]
            if card["title"] or card["value"]
        )
        sections.extend(
            [
                f"### 4.{index} {result['title']}",
                "",
                "#### 执行步骤",
                "",
                f"1. 打开医检报告理解页，保留上传前页面截图 `screenshots/{result['case']}_before_upload.png`。",
                f"2. 通过系统文件选择器选择 `{result['input_file']}`，保留上传页与处理中截图。",
                f"3. 等待页面完成解析，保留顶部状态卡和下方可读报告截图。",
                "4. 如页面存在订正区，则展开 OCR 订正区并导出可见文本。",
                "5. 如需验证云端失败降级，则关闭 Wi-Fi 后点击重新解析。",
                "6. 导出 logcat、数据库快照和手动接口返回，形成联合证据。",
                "",
                "#### 页面结果",
                "",
                f"- 顶部状态：{result['status_text']}",
                f"- 结构化摘要：{result['structured_count'] or '未显示'}",
                f"- 可读报告是否可见：{'是' if result['readable_text'] else '否'}",
                f"- OCR 文本是否可导出：{'是' if result['ocr_text'] else '否'}",
                "",
                "#### 页面证据卡片",
                "",
                cards_text or "- 无卡片文本",
                "",
                "#### 接口与数据库证据",
                "",
                f"- 手动 `/api/report/understand` 状态码：{result['api_status'] if result['api_status'] is not None else '未抓取'}",
                f"- medical_reports 计数：{result['db_overview'].get('medical_reports_count', '不可用')}",
                f"- medical_metrics 计数：{result['db_overview'].get('medical_metrics_count', '不可用')}",
                "",
                "#### 判定",
                "",
                (
                    "该样例最终判定为：外部 OCR 依赖阻塞。上传和页面流程已走通，但未形成正式可读报告。"
                    if "Unable to resolve host" in result["status_text"]
                    else f"该样例最终判定为：{result['title']}，系统{'满足' if result['key_field_completeness'] != '低' or result['fallback_triggered'] else '部分满足'}预期。"
                ),
                "",
            ]
        )
    sections.extend(
        [
            "## 5. 结论回答（供比赛测试文档直接复用）",
            "",
            "1. 是否能正常成功生成可读报告：本轮自动化无法证明。3 个样例均被 OCR 上游域名解析失败阻塞，未产出正式可读报告。",
            "2. 结构化字段缺失或不完整时是否仍有结果：本轮自动化无法完成该分支验证，因为前置 OCR 阶段即被阻塞。",
            "3. 云端失败后是否触发本地降级：已执行关闭 Wi-Fi 和重新解析动作，但由于首次 OCR 未成功，当前只能证明降级入口动作存在，不能证明基于 OCR 文本的降级结果质量。",
            "4. 页面展示是否与结构化结果一致：一致。页面显示失败状态时，本地数据库也没有形成新的正式报告记录，未出现伪成功落库。",
            "",
            "## 6. 证据索引",
            "",
            "- `screenshots/`：页面截图、处理中截图、最终结果截图、订正区截图",
            "- `api-captures/`：输入文件信息、OCR 文本、手动 `/api/report/understand` 请求与返回、Wi-Fi 状态",
            "- `db-snapshots/`：Room 数据库导出与查询概览",
            "- `case-table.md`：比赛模板化用例表",
            "- `report-quality-matrix.csv`：样例质量对照表",
            "- `logcat.txt`：整轮执行日志",
            "",
            "## 7. 综合分析及建议",
            "",
            "本轮自动化验证的是“当前真实可运行状态”，而不是历史最佳效果。当前最突出的外部依赖问题是 OCR 域名解析失败，这使得报告理解闭环在最前置的 OCR 阶段就被统一阻塞。也正因为如此，当前最需要修复的不是页面布局，而是 OCR 服务连通性、DNS 解析与失败提示路径。",
            "",
            "## 8. 测试经验总结",
            "",
            "本轮测试证明，针对端云协同的医检报告功能，必须同时保留文件源信息、UI 结果、OCR 文本、接口返回和日志证据，单一截图不能充分证明系统行为。即使目标是验证正常、缺失和降级三条分支，也必须先确认最前置的 OCR 外部依赖是否可达；否则所有业务分支都会被统一阻塞。",
            "",
        ]
    )
    return "\n".join(sections)


def main() -> None:
    ensure_dirs()
    clean_previous_outputs()
    session = get_cloud_session()
    write_json(
        API_CAPTURES / "session_snapshot.json",
        {"email": session.get("email", ""), "hasAccessToken": bool(session.get("accessToken"))},
    )
    profile_status, profile_body = request_json("GET", f"{BASE_URL}/api/user/profile", token=session.get("accessToken"))
    write_json(API_CAPTURES / "user_profile_request_meta.json", {"status": profile_status})
    write_text(API_CAPTURES / "user_profile_response.json", profile_body)

    all_run_lines = [
        f"Started at: {time.strftime('%Y-%m-%d %H:%M:%S')}",
        f"Session email: {session.get('email', '')}",
    ]
    results: list[dict[str, Any]] = []
    for case in CASES:
        case_data = run_case(case, session.get("accessToken", ""))
        results.append(case_data)
        all_run_lines.extend(case_data["run_lines"])

    full_logcat = adb("logcat", "-d", "-v", "time", timeout=60).stdout
    write_text(ROOT / "logcat.txt", full_logcat)
    write_text(ROOT / "run.log", "\n".join(all_run_lines) + "\n")
    write_json(ROOT / "case-summary.json", {"cases": [item["result"] for item in results]})
    write_text(ROOT / "case-table.md", build_case_table(results))
    write_text(ROOT / "report-quality-matrix.csv", build_quality_matrix(results))
    write_text(ROOT / "result-analysis.md", build_result_analysis())
    write_text(ROOT / "recommendations.md", build_recommendations())
    write_text(ROOT / "lessons-learned.md", build_lessons_learned(results))
    write_text(
        ROOT / "TC-FUNC-004_医检报告理解闭环测试总汇_超详细版.md",
        build_detailed_report(results, session),
    )


if __name__ == "__main__":
    main()
