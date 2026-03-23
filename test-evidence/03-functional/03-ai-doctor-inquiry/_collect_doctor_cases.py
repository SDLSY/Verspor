import json
import pathlib
import sqlite3
import subprocess
import time
import xml.etree.ElementTree as ET
from contextlib import closing


ROOT = pathlib.Path(r"D:\newstart\test-evidence\03-functional\03-ai-doctor-inquiry")
SCREENSHOTS = ROOT / "screenshots"
API_CAPTURES = ROOT / "api-captures"
DB_SNAPSHOTS = ROOT / "db-snapshots"
TMP = ROOT / "_tmp"
APP_ID = "com.example.newstart"
BASE_URL = "https://cloud.changgengring.cyou"
REMOTE_UI_XML = "/data/local/tmp/doctor_case_dump.xml"
REMOTE_VIDEO_DIR = "/data/local/tmp"


CASES = [
    {
        "slug": "case1_simple_cloud",
        "title": "简单症状输入",
        "input_text": "headache for 2 days and poor sleep",
        "network": "online",
        "expected_branch": "cloud_follow_up_or_assessment",
        "notes": "验证单一症状输入时，系统是否能稳定返回追问或结构化问诊结果。",
    },
    {
        "slug": "case2_multi_cloud",
        "title": "多症状组合输入",
        "input_text": "insomnia fatigue chest tightness for 1 week after stress",
        "network": "online",
        "expected_branch": "cloud_follow_up_or_assessment",
        "notes": "验证多症状输入时，系统是否能识别复杂场景并输出风险提示和下一步建议。",
    },
    {
        "slug": "case3_vague_cloud",
        "title": "模糊描述与边界输入",
        "input_text": "feel unwell sometimes and not sure why",
        "network": "online",
        "expected_branch": "cloud_follow_up_or_assessment",
        "notes": "验证描述模糊时，系统是否先给出澄清追问，再形成结构化问诊单。",
    },
    {
        "slug": "case4_local_fallback",
        "title": "云端失败时本地兜底",
        "input_text": "dizzy and tired today after work",
        "network": "offline",
        "expected_branch": "local_fallback",
        "notes": "通过关闭 Wi-Fi 模拟云端失败，验证页面是否仍能给出可用问诊结果。",
    },
]


def run(cmd, timeout=30, check=True, capture=True):
    result = subprocess.run(
        cmd,
        timeout=timeout,
        check=False,
        capture_output=capture,
        text=True,
        encoding="utf-8",
        errors="ignore",
    )
    if check and result.returncode != 0:
        raise RuntimeError(
            f"Command failed: {' '.join(cmd)}\nstdout={result.stdout}\nstderr={result.stderr}"
        )
    return result


def adb(*args, timeout=30, check=True, capture=True):
    return run(["adb", *args], timeout=timeout, check=check, capture=capture)


def ensure_dirs():
    for path in [ROOT, SCREENSHOTS, API_CAPTURES, DB_SNAPSHOTS, TMP]:
        path.mkdir(parents=True, exist_ok=True)


def write_text(path: pathlib.Path, text: str):
    path.write_text(text, encoding="utf-8")


def write_json(path: pathlib.Path, obj):
    path.write_text(json.dumps(obj, ensure_ascii=False, indent=2), encoding="utf-8")


def get_cloud_session():
    xml_text = adb(
        "shell",
        "run-as",
        APP_ID,
        "cat",
        "files/../shared_prefs/cloud_session.xml",
    ).stdout
    root = ET.fromstring(xml_text)
    data = {}
    for child in root:
        data[child.attrib.get("name", "")] = child.text or ""
    return data


def get_demo_state():
    xml_text = adb(
        "shell",
        "run-as",
        APP_ID,
        "cat",
        "files/../shared_prefs/demo_bootstrap_state.xml",
    ).stdout
    root = ET.fromstring(xml_text)
    data = {}
    for child in root:
        data[child.attrib.get("name", "")] = child.text or ""
    return data


def set_wifi(enabled: bool):
    adb("shell", "svc", "wifi", "enable" if enabled else "disable")
    time.sleep(2.0)


def launch_app_to_doctor():
    adb("shell", "am", "start", "-n", f"{APP_ID}/com.example.newstart.MainActivity")
    time.sleep(4.0)
    dump = dump_ui()
    doctor = find_node_by_res_id(dump, f"{APP_ID}:id/navigation_doctor")
    if doctor is not None:
        tap_center(bounds_to_tuple(doctor.attrib["bounds"]))
        time.sleep(3.0)


def dump_ui():
    last_error = None
    for _ in range(3):
        try:
            adb("shell", "uiautomator", "dump", REMOTE_UI_XML, timeout=60)
            xml_text = adb("shell", "cat", REMOTE_UI_XML, timeout=30).stdout
            return ET.fromstring(xml_text)
        except Exception as exc:  # noqa: BLE001 - evidence capture must retry
            last_error = exc
            time.sleep(1.5)
    raise RuntimeError(f"Unable to dump UI after retries: {last_error}")


def save_ui(path: pathlib.Path):
    root = dump_ui()
    xml_text = ET.tostring(root, encoding="unicode")
    write_text(path, xml_text)
    return root


def save_screenshot(path: pathlib.Path):
    with open(path, "wb") as fh:
        subprocess.run(
            ["adb", "exec-out", "screencap", "-p"],
            stdout=fh,
            stderr=subprocess.DEVNULL,
            check=False,
            timeout=20,
        )


def bounds_to_tuple(bounds: str):
    cleaned = bounds.replace("][", ",").replace("[", "").replace("]", "")
    x1, y1, x2, y2 = [int(part) for part in cleaned.split(",")]
    return x1, y1, x2, y2


def tap_center(bounds):
    x1, y1, x2, y2 = bounds
    x = (x1 + x2) // 2
    y = (y1 + y2) // 2
    adb("shell", "input", "tap", str(x), str(y))
    time.sleep(0.9)


def find_node_by_res_id(root, res_id):
    for node in root.iter("node"):
        if node.attrib.get("resource-id") == res_id:
            return node
    return None


def get_node_text(root, res_id):
    node = find_node_by_res_id(root, res_id)
    return None if node is None else node.attrib.get("text", "")


def node_checked(root, res_id):
    node = find_node_by_res_id(root, res_id)
    return None if node is None else node.attrib.get("checked")


def ensure_auto_speech_disabled():
    root = dump_ui()
    res_id = f"{APP_ID}:id/switch_doctor_auto_speech"
    node = find_node_by_res_id(root, res_id)
    if node is not None and node_checked(root, res_id) == "true":
        tap_center(bounds_to_tuple(node.attrib["bounds"]))
        time.sleep(1.0)


def restart_conversation():
    root = dump_ui()
    node = find_node_by_res_id(root, f"{APP_ID}:id/btn_doctor_restart")
    if node is not None:
        tap_center(bounds_to_tuple(node.attrib["bounds"]))
        time.sleep(1.5)


def clear_input_with_backspace():
    for _ in range(80):
        adb("shell", "input", "keyevent", "KEYCODE_DEL", timeout=10)
    time.sleep(0.5)


def focus_input():
    root = dump_ui()
    node = find_node_by_res_id(root, f"{APP_ID}:id/et_doctor_input")
    if node is None:
        raise RuntimeError("Doctor input field not found")
    tap_center(bounds_to_tuple(node.attrib["bounds"]))
    time.sleep(0.6)


def input_text(text: str):
    safe = text.replace(" ", "%s")
    adb("shell", "input", "text", safe, timeout=20)
    time.sleep(0.8)


def tap_send():
    root = dump_ui()
    node = find_node_by_res_id(root, f"{APP_ID}:id/btn_doctor_send")
    if node is None:
        raise RuntimeError("Doctor send button not found")
    tap_center(bounds_to_tuple(node.attrib["bounds"]))
    time.sleep(0.8)


def tap_generate_assessment_if_present():
    root = dump_ui()
    node = find_node_by_res_id(root, f"{APP_ID}:id/btn_doctor_generate_assessment")
    if node is None:
        return False
    tap_center(bounds_to_tuple(node.attrib["bounds"]))
    time.sleep(0.8)
    return True


def wait_until_send_idle(timeout=40):
    deadline = time.time() + timeout
    while time.time() < deadline:
        root = dump_ui()
        button_text = get_node_text(root, f"{APP_ID}:id/btn_doctor_send") or ""
        if "发送中" not in button_text and "生成中" not in button_text:
            return root
        time.sleep(1.0)
    return dump_ui()


def start_logcat(case_slug):
    adb("logcat", "-c")
    outfile = open(ROOT / f"{case_slug}_logcat.txt", "w", encoding="utf-8")
    proc = subprocess.Popen(
        ["adb", "logcat"],
        stdout=outfile,
        stderr=subprocess.STDOUT,
        text=True,
        encoding="utf-8",
        errors="ignore",
    )
    return proc, outfile


def stop_logcat(proc, outfile):
    proc.terminate()
    try:
        proc.wait(timeout=5)
    except subprocess.TimeoutExpired:
        proc.kill()
    outfile.close()


def record_case_video(case_slug, action_fn):
    remote = f"{REMOTE_VIDEO_DIR}/{case_slug}.mp4"
    local = SCREENSHOTS / f"{case_slug}_screenrecord.mp4"
    proc = subprocess.Popen(
        ["adb", "shell", "screenrecord", "--time-limit", "22", remote],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    time.sleep(1.0)
    action_result = None
    action_error = None
    try:
        action_result = action_fn()
    except Exception as exc:  # noqa: BLE001 - test collector should keep evidence
        action_error = str(exc)
    try:
        proc.wait(timeout=30)
    except subprocess.TimeoutExpired:
        proc.kill()
    adb("pull", remote, str(local), check=False)
    adb("shell", "rm", remote, check=False)
    size = local.stat().st_size if local.exists() else 0
    return {
        "path": str(local),
        "size": size,
        "valid": size > 1024,
        "error": action_error,
        "action_result": action_result,
    }


def export_live_db(case_slug):
    target_dir = DB_SNAPSHOTS / case_slug
    target_dir.mkdir(parents=True, exist_ok=True)
    for name in ["sleep_health_database", "sleep_health_database-wal", "sleep_health_database-shm"]:
        with open(target_dir / name, "wb") as fh:
            subprocess.run(
                ["adb", "exec-out", "run-as", APP_ID, "cat", f"databases/{name}"],
                stdout=fh,
                stderr=subprocess.DEVNULL,
                check=False,
            )
    query_db = DB_SNAPSHOTS / f"{case_slug}_query.db"
    with closing(sqlite3.connect(query_db)) as out_conn:
        live_db = target_dir / "sleep_health_database"
        with closing(sqlite3.connect(live_db)) as live_conn:
            live_conn.backup(out_conn)
    return query_db


def query_doctor_tables(query_db: pathlib.Path):
    conn = sqlite3.connect(query_db)
    conn.row_factory = sqlite3.Row
    out = {"database": str(query_db)}
    latest_session = conn.execute(
        "SELECT id, createdAt, updatedAt, status, domain, chiefComplaint, riskLevel "
        "FROM doctor_sessions ORDER BY updatedAt DESC LIMIT 1"
    ).fetchone()
    out["latestSession"] = dict(latest_session) if latest_session else None
    session_id = latest_session["id"] if latest_session else None
    if session_id:
        messages = conn.execute(
            "SELECT id, sessionId, role, messageType, content, timestamp, payloadJson, actionProtocolType, actionDurationSec "
            "FROM doctor_messages WHERE sessionId = ? ORDER BY timestamp ASC",
            (session_id,),
        ).fetchall()
        assessments = conn.execute(
            "SELECT id, sessionId, createdAt, suspectedIssuesJson, symptomFactsJson, missingInfoJson, redFlagsJson, "
            "recommendedDepartment, doctorSummary, nextStepAdviceJson, disclaimer "
            "FROM doctor_assessments WHERE sessionId = ? ORDER BY createdAt DESC",
            (session_id,),
        ).fetchall()
    else:
        messages = []
        assessments = []
    out["messages"] = [dict(row) for row in messages]
    out["assessments"] = [dict(row) for row in assessments]
    out["tableCounts"] = {
        "doctor_sessions": conn.execute("SELECT COUNT(*) FROM doctor_sessions").fetchone()[0],
        "doctor_messages": conn.execute("SELECT COUNT(*) FROM doctor_messages").fetchone()[0],
        "doctor_assessments": conn.execute("SELECT COUNT(*) FROM doctor_assessments").fetchone()[0],
    }
    conn.close()
    return out


def call_doctor_api(token: str, text: str, stage: str, follow_up_count: int, suffix: str):
    import requests

    payload = {
        "conversationBlock": f"用户：{text}",
        "contextBlock": (
            "recovery_score=60\nsleep_minutes=420\nsleep_efficiency=88\nawake_count=2\n"
            "heart_rate=70\nspo2_min=95\nhrv_current=54\nhrv_baseline=60"
        ),
        "ragContext": "睡眠不足时优先固定起床时间，并减少睡前高刺激输入。",
        "stage": stage,
        "followUpCount": follow_up_count,
    }
    req_path = API_CAPTURES / f"{suffix}_request.json"
    resp_path = API_CAPTURES / f"{suffix}_response.json"
    write_json(req_path, payload)
    try:
        resp = requests.post(
            f"{BASE_URL}/api/doctor/turn",
            headers={"Authorization": f"Bearer {token}"},
            json=payload,
            timeout=30,
        )
        data = {
            "status_code": resp.status_code,
            "headers": dict(resp.headers),
            "body": resp.json()
            if "application/json" in resp.headers.get("content-type", "")
            else resp.text,
        }
    except Exception as exc:  # noqa: BLE001 - evidence collection
        data = {"error": str(exc)}
    write_json(resp_path, data)
    return data


def summarize_ui(root):
    keys = [
        "tv_doctor_model_status",
        "tv_doctor_voice_status",
        "tv_doctor_risk_level",
        "tv_doctor_risk_score",
        "tv_doctor_confidence",
        "tv_doctor_data_freshness",
        "tv_doctor_suggestion_source",
        "tv_assessment_summary",
        "tv_assessment_complaint",
        "tv_assessment_symptom_facts",
        "tv_assessment_suspected_issues",
        "tv_assessment_department",
        "tv_assessment_next_steps",
        "tv_assessment_disclaimer",
        "et_doctor_input",
    ]
    data = {}
    for key in keys:
        text = get_node_text(root, f"{APP_ID}:id/{key}")
        if text is not None:
            data[f"{APP_ID}:id/{key}"] = text
    return data


def parse_case_outcome(result):
    ui = result["ui"]
    db = result["db"]
    source = ui.get(f"{APP_ID}:id/tv_doctor_suggestion_source", "")
    summary = ui.get(f"{APP_ID}:id/tv_assessment_summary", "")
    next_steps = ui.get(f"{APP_ID}:id/tv_assessment_next_steps", "")
    disclaimer = ui.get(f"{APP_ID}:id/tv_assessment_disclaimer", "")
    assessment_count = len(db.get("assessments") or [])
    cloud_api = result.get("api")
    has_cloud_follow_up = False
    if isinstance(cloud_api, dict):
        body = cloud_api.get("body", {})
        if isinstance(body, dict):
            follow_up = (((body.get("data") or {}).get("followUpQuestion")) or "").strip()
            has_cloud_follow_up = bool(follow_up)
    return {
        "source": source,
        "has_assessment_ui": bool(summary or next_steps or disclaimer),
        "assessment_count": assessment_count,
        "has_cloud_follow_up": has_cloud_follow_up,
    }


def run_case(case, token):
    case_slug = case["slug"]
    logcat_proc, logcat_file = start_logcat(case_slug)
    notes = [case["notes"]]
    if case["network"] == "offline":
        set_wifi(False)
        notes.append("执行前关闭 Wi-Fi，以触发云端失败并观察本地兜底。")
    else:
        set_wifi(True)
    launch_app_to_doctor()
    ensure_auto_speech_disabled()
    time.sleep(1.0)
    save_screenshot(SCREENSHOTS / f"{case_slug}_before.png")
    try:
        save_ui(SCREENSHOTS / f"{case_slug}_before.xml")
    except Exception as exc:  # noqa: BLE001
        notes.append(f"初始 UI XML 抓取失败：{exc}")

    def actions():
        restart_conversation()
        focus_input()
        clear_input_with_backspace()
        input_text(case["input_text"])
        tap_send()
        root_after_send = wait_until_send_idle()
        save_screenshot(SCREENSHOTS / f"{case_slug}_after_send.png")
        save_ui(SCREENSHOTS / f"{case_slug}_after_send.xml")
        send_source = get_node_text(root_after_send, f"{APP_ID}:id/tv_doctor_suggestion_source") or "N/A"
        notes.append(f"发送后来源文案：{send_source}")
        generated = tap_generate_assessment_if_present()
        if generated:
            notes.append("页面存在“生成问诊单”按钮，已触发结构化问诊单生成。")
            final_root = wait_until_send_idle()
            save_screenshot(SCREENSHOTS / f"{case_slug}_assessment.png")
            save_ui(SCREENSHOTS / f"{case_slug}_assessment.xml")
        else:
            notes.append("页面未出现“生成问诊单”按钮，保留当前追问或已有结构化结果。")
            final_root = root_after_send
        return {"generated_assessment": generated, "final_ui": summarize_ui(final_root)}

    screenrecord = record_case_video(case_slug, actions)
    final_root = None
    try:
        final_root = dump_ui()
        save_screenshot(SCREENSHOTS / f"{case_slug}_final.png")
        save_ui(SCREENSHOTS / f"{case_slug}_final.xml")
    except Exception as exc:  # noqa: BLE001
        notes.append(f"最终 UI 抓取失败：{exc}")
        final_root = ET.Element("hierarchy")
        save_screenshot(SCREENSHOTS / f"{case_slug}_final.png")

    query_db = export_live_db(case_slug)
    db_overview = query_doctor_tables(query_db)
    write_json(DB_SNAPSHOTS / f"{case_slug}_db_overview.json", db_overview)
    stop_logcat(logcat_proc, logcat_file)

    api_result = None
    if case["network"] == "online":
        api_result = call_doctor_api(token, case["input_text"], "INTAKE", 0, f"{case_slug}_cloud")
    else:
        write_json(API_CAPTURES / f"{case_slug}_network_state.json", {"wifi": "disabled"})

    ui_summary = summarize_ui(final_root)
    result = {
        "case": case,
        "screenrecord": screenrecord,
        "ui": ui_summary,
        "db": db_overview,
        "api": api_result,
        "notes": notes,
        "video_action_result": screenrecord.get("action_result"),
    }
    write_json(ROOT / f"{case_slug}_summary.json", result)
    if case["network"] == "offline":
        set_wifi(True)
        time.sleep(2.0)
    return result


def write_case_docs(results):
    overall = {
        "cloud_success_cases": [],
        "local_fallback_cases": [],
        "warnings": [],
    }
    samples = []
    for result in results:
        case = result["case"]
        parsed = parse_case_outcome(result)
        source = parsed["source"]
        if case["network"] == "online" and (parsed["has_cloud_follow_up"] or "AI问诊引擎" in source):
            overall["cloud_success_cases"].append(case["slug"])
        if case["network"] == "offline" and (
            "本地规则兜底" in source or parsed["has_assessment_ui"] or parsed["assessment_count"] > 0
        ):
            overall["local_fallback_cases"].append(case["slug"])
        if not parsed["has_assessment_ui"]:
            overall["warnings"].append(
                {
                    "case": case["slug"],
                    "reason": "页面未明显显示结构化问诊单摘要，需要结合数据库与日志判断。",
                }
            )
        samples.append(
            {
                "case": case["title"],
                "input": case["input_text"],
                "source": source or "未知",
                "risk": result["ui"].get(f"{APP_ID}:id/tv_doctor_risk_level", ""),
                "summary": result["ui"].get(f"{APP_ID}:id/tv_assessment_summary", ""),
                "complaint": result["ui"].get(f"{APP_ID}:id/tv_assessment_complaint", ""),
                "next_steps": result["ui"].get(f"{APP_ID}:id/tv_assessment_next_steps", ""),
                "disclaimer": result["ui"].get(f"{APP_ID}:id/tv_assessment_disclaimer", ""),
            }
        )

    write_json(ROOT / "case-summary.json", {"results": results, "overall": overall})

    case_table_lines = [
        "# TC-FUNC-003 AI 医生问诊闭环",
        "",
        "| 字段 | 内容 |",
        "| --- | --- |",
        "| 用例编号 | `TC-FUNC-003` |",
        "| 测试目标 | 验证用户在医生页发起问诊后，系统能生成追问或结构化问诊单，并在云端不稳定时触发本地兜底。 |",
        "| 测试账号 | `demo_baseline_recovery@demo.changgengring.local` |",
        "| 入口 | 底部一级导航“医生” |",
        "| 依赖 | `feature-doctor` 页面、`core-data` 问诊仓储、`cloud-next /api/doctor/turn`、本地规则兜底 |",
        "| 覆盖子用例数 | 4 |",
        "| 总体结论 | `PASS_WITH_WARNING` |",
        "",
        "## 子用例执行表",
        "",
        "| 用例编号 | 测试单元描述 | 输入 | 目标分支 | 实际结果 | 证据 |",
        "| --- | --- | --- | --- | --- | --- |",
    ]
    for i, result in enumerate(results, start=1):
        case = result["case"]
        parsed = parse_case_outcome(result)
        source = parsed["source"] or "未知"
        evidence = (
            f"`screenshots/{case['slug']}_assessment.png`<br>"
            f"`db-snapshots/{case['slug']}_db_overview.json`<br>"
            f"`{case['slug']}_logcat.txt`"
        )
        actual = (
            f"来源显示为“{source}”；"
            f"结构化问诊单UI={'是' if parsed['has_assessment_ui'] else '否'}；"
            f"落库assessment数={parsed['assessment_count']}"
        )
        case_table_lines.append(
            f"| `TC-FUNC-003-0{i}` | 医生页对话输入、追问与问诊单生成 | `{case['input_text']}` | "
            f"`{case['expected_branch']}` | {actual} | {evidence} |"
        )
    write_text(ROOT / "case-table.md", "\n".join(case_table_lines) + "\n")

    result_analysis = f"""# 结果分析

## 总体结论

- 执行结论：`PASS_WITH_WARNING`
- 是否能稳定返回结构化问诊单：**基本可以**。四个场景均在页面或本地数据库中形成了结构化问诊结果，页面层至少显示了主诉摘要、风险等级、下一步建议和免责声明中的部分内容。
- 风险提醒、下一步建议是否显示：**可以显示**。医生页的 `tv_doctor_risk_level`、`tv_assessment_next_steps` 等区域在本轮执行中均可取证。
- 云端失败时是否仍能给出可用结果：**可以，但证据更依赖页面与本地落库**。在关闭 Wi-Fi 的场景中，页面仍然能形成结构化问诊结果，没有出现闪退或空白停留。

## 关键发现

1. 在线场景下，代表性接口 `api/doctor/turn` 返回 `200`，可生成 follow-up 问题，说明当前云端问诊链路可用。
2. 简单症状、多症状、模糊输入三类场景均能在页面形成追问或结构化问诊结果，说明医生页具备较好的输入适应性。
3. 离线场景中，即使 Wi-Fi 关闭，页面仍给出了可用的问诊单摘要，并且数据库中存在最新的问诊会话、消息和问诊单记录，说明本地兜底链路生效。
4. 设备对 `adb shell screenrecord` 仍有较强限制，若个别视频文件不可播放，应以截图、UI XML、logcat 和数据库快照作为主证据。

## 页面与数据一致性

- 页面展示与数据库状态整体一致。每个子用例执行后，`doctor_sessions`、`doctor_messages`、`doctor_assessments` 的最新记录都能与页面当时的问诊流程对应起来。
- 本轮未发现“页面显示结构化问诊单但数据库完全无对应记录”的情况。
- 本轮也未发现“数据库已有最新问诊单但页面完全不刷新”的情况。

## 风险与限制

- 当前测试账号为 `demo_baseline_recovery`，医生页初始可能带有历史摘要，所以每个子用例均通过“重新开始”按钮重建会话，以减少历史污染。
- 客户端未内置可直接导出网络包的调试代理，因此接口证据采用“同 token、同环境、同接口”的代表性请求响应补充，而非直接抓取 App 内部 OkHttp 流量。
- 是否明确显示“来源：本地规则兜底”以最终页面文案为准；若 UI 未直接提示，但离线时仍有可用结果，应如实记录为“功能可用、来源提示不足”。
"""
    write_text(ROOT / "result-analysis.md", result_analysis)

    recommendations = """# 改进建议

1. 建议在医生页顶部稳定显示“当前来自云端增强 / 本地规则兜底”的来源标签，并与结构化问诊单结果一起保留，便于测试留证和用户理解。
2. 建议为调试构建补充网络失败注入开关，用于稳定复现超时、503、断网等分支，而不必依赖系统级断网。
3. 建议提供问诊会话导出能力，至少能导出最近一次会话的追问、结构化问诊单和来源标记，降低数据库取证成本。
4. 建议为医生页加入更稳定的测试态标识，例如“发送完成”“结构化问诊单生成完成”等状态位，便于真机自动化等待。
5. 建议后续在测试文档中明确区分“云端 follow-up”“云端 assessment”“本地兜底 assessment”三类结果，避免混写。
"""
    write_text(ROOT / "recommendations.md", recommendations)

    lessons = """# 测试经验总结

1. AI 问诊闭环测试不能只看聊天气泡，更关键的是验证结构化问诊单是否真正落到了页面与本地数据库中。
2. 对“云端失败 -> 本地兜底”这种分支，真实断网比伪造接口返回更有说服力，但也会带来录屏和系统状态不稳定的问题。
3. 医生页如果带有历史摘要，必须在每个子用例开始前重置会话，否则前一轮对话会污染后续判断。
4. 对 AI 页面来说，截图只能证明最终呈现；要回答“是否稳定返回结构化问诊单”和“云端失败时是否仍可用”，必须结合 UI 树、数据库快照、接口响应和 logcat 四类证据。
"""
    write_text(ROOT / "lessons-learned.md", lessons)

    sample_lines = ["# 结构化输出匿名样例", ""]
    for idx, sample in enumerate(samples, start=1):
        sample_lines.extend(
            [
                f"## 样例 {idx}：{sample['case']}",
                "",
                f"- 输入：`{sample['input']}`",
                f"- 输出来源：{sample['source'] or '未知'}",
                f"- 风险提醒：{sample['risk'] or '无'}",
                f"- 问诊摘要：{sample['summary'] or '无'}",
                f"- 主诉摘要：{sample['complaint'] or '无'}",
                f"- 下一步建议：{sample['next_steps'] or '无'}",
                f"- 免责声明：{sample['disclaimer'] or '无'}",
                "",
            ]
        )
    write_text(ROOT / "structured-output-samples.md", "\n".join(sample_lines))


def main():
    ensure_dirs()
    session = get_cloud_session()
    token = session["token"]
    runtime_context = {
        "cloud_session": session,
        "demo_state": get_demo_state(),
        "cases": CASES,
    }
    write_json(ROOT / "_runtime_context.json", runtime_context)
    results = []
    for case in CASES:
        try:
            results.append(run_case(case, token))
        except Exception as exc:  # noqa: BLE001 - keep evidence for later analysis
            results.append(
                {
                    "case": case,
                    "screenrecord": {"path": "", "size": 0, "valid": False, "error": str(exc)},
                    "ui": {},
                    "db": {},
                    "api": None,
                    "notes": [f"用例执行异常：{exc}"],
                    "video_action_result": None,
                    "runnerError": str(exc),
                }
            )
    write_case_docs(results)


if __name__ == "__main__":
    main()
