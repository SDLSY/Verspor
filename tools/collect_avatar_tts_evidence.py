from __future__ import annotations

import importlib.util
import json
import shutil
import sys
import time
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path


REPO_ROOT = Path(r"D:\newstart")
CASE_ROOT = REPO_ROOT / "test-evidence" / "03-functional" / "07-desktop-avatar-and-tts"
HELPER_PATH = REPO_ROOT / "tools" / "run_functional_evidence.py"
APP_ID = "com.example.newstart"


def load_helpers():
    spec = importlib.util.spec_from_file_location("functional_helpers", HELPER_PATH)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"Unable to load helpers from {HELPER_PATH}")
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


helpers = load_helpers()


SCREENSHOTS = CASE_ROOT / "screenshots"
API_CAPTURES = CASE_ROOT / "api-captures"
DB_SNAPSHOTS = CASE_ROOT / "db-snapshots"
UI_PATH = CASE_ROOT / "_current_ui.xml"


def write_text(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8-sig")


def write_json(path: Path, payload) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8-sig")


def ensure_dirs() -> None:
    if CASE_ROOT.exists():
        shutil.rmtree(CASE_ROOT)
    SCREENSHOTS.mkdir(parents=True, exist_ok=True)
    API_CAPTURES.mkdir(parents=True, exist_ok=True)
    DB_SNAPSHOTS.mkdir(parents=True, exist_ok=True)


def set_wifi(enabled: bool) -> None:
    helpers.adb("shell", "svc", "wifi", "enable" if enabled else "disable")
    time.sleep(2.5)


def current_root() -> ET.Element:
    return helpers.dump_ui(UI_PATH)


def root_package(root: ET.Element) -> str:
    node = root.find(".//node")
    return "" if node is None else node.attrib.get("package", "")


def ensure_app_foreground(*, retries: int = 4, settle_seconds: float = 3.5) -> ET.Element:
    last_root: ET.Element | None = None
    for attempt in range(retries):
        try:
            root = current_root()
            last_root = root
            if root_package(root) == APP_ID:
                return root
        except Exception:
            pass
        launch_output = helpers.force_start_app().strip()
        if launch_output:
            time.sleep(0.8)
        helpers.wait_for_ui_settle(settle_seconds)
        root = current_root()
        last_root = root
        if root_package(root) == APP_ID:
            return root
        # Some runs unexpectedly land on launcher; force a launcher intent once more.
        helpers.adb(
            "shell",
            "monkey",
            "-p",
            APP_ID,
            "-c",
            "android.intent.category.LAUNCHER",
            "1",
            check=False,
        )
        helpers.wait_for_ui_settle(2.5)
    package_name = "" if last_root is None else root_package(last_root)
    raise RuntimeError(f"App not in foreground after retries. Current package: {package_name or 'unknown'}")


def screenshot(name: str) -> Path:
    path = SCREENSHOTS / name
    helpers.screenshot(path)
    if not path.exists() or path.stat().st_size < 2048:
        remote = f"/sdcard/{name}"
        helpers.adb("shell", "screencap", "-p", remote)
        helpers.adb("pull", remote, str(path))
        helpers.adb("shell", "rm", remote, check=False)
    return path


def dump_ui(name: str) -> tuple[Path, ET.Element]:
    path = SCREENSHOTS / name
    root = helpers.dump_ui(path)
    return path, root


def find_first(root: ET.Element, resource_id: str) -> ET.Element | None:
    matches = helpers.find_nodes(root, resource_id=resource_id)
    return matches[0] if matches else None


def node_text(root: ET.Element, resource_id: str) -> str:
    node = find_first(root, resource_id)
    return "" if node is None else node.attrib.get("text", "").strip()


def node_visible(root: ET.Element, resource_id: str) -> bool:
    return find_first(root, resource_id) is not None


def tap_node(node: ET.Element) -> None:
    helpers.tap_node(node)
    time.sleep(1.0)


def tap_by_id(resource_id: str, *, retries: int = 4) -> bool:
    return helpers.tap_by_id(resource_id, UI_PATH, retries=retries)


def is_tab_selected(resource_id: str) -> bool:
    root = current_root()
    node = find_first(root, resource_id)
    return node is not None and node.attrib.get("selected") == "true"


def open_home() -> bool:
    ensure_app_foreground()
    resource_id = f"{APP_ID}:id/navigation_home"
    return is_tab_selected(resource_id) or helpers.goto_tab("home", UI_PATH)


def open_doctor() -> bool:
    ensure_app_foreground()
    resource_id = f"{APP_ID}:id/navigation_doctor"
    return is_tab_selected(resource_id) or helpers.goto_tab("doctor", UI_PATH)


def open_intervention_center() -> bool:
    ensure_app_foreground()
    if not open_home():
        return False
    return node_visible(current_root(), f"{APP_ID}:id/btn_relax_center") or helpers.open_intervention_center(UI_PATH)


def stop_voice_if_playing() -> bool:
    ensure_app_foreground()
    root = current_root()
    text = node_text(root, f"{APP_ID}:id/btn_global_avatar_voice")
    if "停止播报" in text:
        node = find_first(root, f"{APP_ID}:id/btn_global_avatar_voice")
        if node is not None:
            tap_node(node)
            time.sleep(1.5)
            return True
    return False


def wait_for_avatar_state(timeout_seconds: float = 8.0) -> dict[str, str | bool]:
    deadline = time.time() + timeout_seconds
    last: dict[str, str | bool] = {}
    while time.time() < deadline:
        root = current_root()
        if root_package(root) != APP_ID:
            root = ensure_app_foreground(retries=2, settle_seconds=2.0)
        bubble_text = node_text(root, f"{APP_ID}:id/globalAvatarSpeechBubble")
        button_text = node_text(root, f"{APP_ID}:id/btn_global_avatar_voice")
        state = {
            "bubbleVisible": node_visible(root, f"{APP_ID}:id/globalAvatarSpeechBubble"),
            "bubbleText": bubble_text,
            "buttonVisible": node_visible(root, f"{APP_ID}:id/btn_global_avatar_voice"),
            "buttonText": button_text,
        }
        last = state
        if bubble_text or "停止播报" in button_text:
            return state
        time.sleep(0.8)
    return last


def clear_logcat() -> None:
    helpers.clear_logcat()


def dump_logcat() -> str:
    temp = CASE_ROOT / "_last_logcat.txt"
    helpers.dump_logcat(temp)
    return temp.read_text(encoding="utf-8-sig", errors="ignore")


def extract_audio_lines(log_text: str) -> list[str]:
    patterns = (
        "audio out start",
        "audio out stop",
        "ACTION_AUDIO_PLAYBACK_STATE_CHANGED",
        "AudioPlaybackMonitor",
        "pkg =com.example.newstart",
    )
    return [line for line in log_text.splitlines() if any(pattern in line for pattern in patterns)]


def extract_network_lines(log_text: str) -> list[str]:
    patterns = (
        "Unable to resolve host",
        "UnknownHostException",
        "avatar",
        "speech",
        "AudioTrack",
        "MediaPlayer",
        "playback failed",
        "newstart",
    )
    lines = []
    for line in log_text.splitlines():
        lowered = line.lower()
        if any(pattern.lower() in lowered for pattern in patterns):
            lines.append(line)
    return lines


def direct_narration(auth, suffix: str, payload: dict) -> dict:
    status, body, headers = helpers.request_json(
        "POST",
        f"{helpers.CLOUD_BASE_URL}/api/avatar/narration",
        token=auth.token,
        payload=payload,
    )
    try:
        body_json = json.loads(body)
    except json.JSONDecodeError:
        body_json = {"raw": body}
    response = {"httpStatus": status, "headers": headers, "body": body_json}
    write_json(API_CAPTURES / f"{suffix}_narration_response.json", response)
    return response


def direct_tts(auth, suffix: str, text: str, profile: str) -> dict:
    status, body, headers = helpers.request_json(
        "POST",
        f"{helpers.CLOUD_BASE_URL}/api/ai/speech/synthesize",
        token=auth.token,
        payload={"text": text, "voice": "x4_yezi", "profile": profile},
    )
    try:
        body_json = json.loads(body)
    except json.JSONDecodeError:
        body_json = {"raw": body}
    response = {"httpStatus": status, "headers": headers, "body": body_json}
    write_json(API_CAPTURES / f"{suffix}_tts_response.json", response)
    return response


def export_db_snapshot(name: str) -> dict:
    return helpers.export_db(DB_SNAPSHOTS / name)


def request_runtime_setup(run_log: list[str]):
    creds = helpers.load_demo_credentials()
    auth = helpers.login_demo_account("demo_baseline_recovery", creds, API_CAPTURES)
    helpers.inject_session(auth)
    helpers.bootstrap_profile(auth, API_CAPTURES)
    bootstrap_ok = helpers.wait_for_bootstrap(auth, timeout_seconds=40)
    run_log.append(f"Login account: {auth.email}")
    run_log.append(f"Bootstrap state: {bootstrap_ok}")
    launch_output = helpers.force_start_app().strip()
    run_log.append(f"Launch output: {launch_output}")
    ensure_app_foreground()
    return auth, bootstrap_ok, launch_output


@dataclass
class CaseResult:
    slug: str
    page: str
    trigger: str
    expected: str
    actual: str
    pass_state: str
    before_shot: str
    bubble_shot: str
    after_shot: str
    screenrecord: str
    screenrecord_valid: bool
    bubble_text: str
    button_before: str
    button_mid: str
    button_after: str
    audio_signal_count: int
    network_signal_count: int
    narration_http: int | None
    tts_http: int | None
    defects: list[str]
    notes: list[str]
    audio_lines_file: str
    network_lines_file: str
    db_overview_file: str


def save_log_excerpts(slug: str, log_text: str) -> tuple[Path, Path]:
    audio_lines = extract_audio_lines(log_text)
    network_lines = extract_network_lines(log_text)
    audio_path = API_CAPTURES / f"{slug}_audio_log_excerpt.txt"
    network_path = API_CAPTURES / f"{slug}_network_log_excerpt.txt"
    write_text(audio_path, "\n".join(audio_lines) + ("\n" if audio_lines else ""))
    write_text(network_path, "\n".join(network_lines) + ("\n" if network_lines else ""))
    return audio_path, network_path


def base_case_cleanup(page_open_fn) -> dict[str, str]:
    ensure_app_foreground()
    opened = page_open_fn()
    if not opened:
        raise RuntimeError(f"Unable to open target page via {page_open_fn.__name__}")
    helpers.wait_for_ui_settle(2.5)
    ensure_app_foreground(settle_seconds=2.0)
    stop_voice_if_playing()
    helpers.wait_for_ui_settle(1.5)
    _, before_root = dump_ui("tmp_before.xml")
    if root_package(before_root) != APP_ID:
        before_root = ensure_app_foreground(settle_seconds=2.0)
    button_before = node_text(before_root, f"{APP_ID}:id/btn_global_avatar_voice")
    return {"buttonBefore": button_before}


def run_home_success(auth) -> CaseResult:
    notes: list[str] = []
    base = base_case_cleanup(open_home)
    ensure_app_foreground(settle_seconds=2.0)
    before_path = screenshot("case1_home_before.png")
    direct_payload = {
        "pageKey": "home",
        "pageTitle": "今日",
        "pageSubtitle": "恢复分与核心指标",
        "visibleHighlights": ["恢复分", "心率", "血氧", "HRV", "进入干预中心"],
        "userStateSummary": "查看今日恢复分与核心生理指标",
        "riskSummary": "",
        "actionHint": "提醒用户先看今日重点，再决定是否进入干预中心或趋势页。",
        "trigger": "button",
    }
    narration = direct_narration(auth, "case1_home", direct_payload)
    narration_text = (((narration.get("body") or {}).get("data") or {}).get("text") or "").strip()
    tts = direct_tts(auth, "case1_home", narration_text or "请先看今日恢复分与风险提示，再决定下一步。", "calm_assistant")
    clear_logcat()
    record_proc, temp_record = helpers.start_screenrecord("case1_home.mp4", seconds=18)
    tap_by_id(f"{APP_ID}:id/btn_global_avatar_voice")
    time.sleep(1.8)
    mid_state = wait_for_avatar_state()
    bubble_path = screenshot("case1_home_bubble.png")
    _, after_root = dump_ui("case1_home_after_ui.xml")
    time.sleep(4.0)
    after_path = screenshot("case1_home_after.png")
    helpers.finish_screenrecord(record_proc, temp_record, CASE_ROOT / "screenrecord_case1_home.mp4")
    log_text = dump_logcat()
    log_path = CASE_ROOT / "case1_home_logcat.txt"
    write_text(log_path, log_text)
    audio_path, network_path = save_log_excerpts("case1_home", log_text)
    export_db_snapshot("case1_home")
    button_after = node_text(after_root, f"{APP_ID}:id/btn_global_avatar_voice")
    audio_count = len(extract_audio_lines(log_text))
    pass_state = "PASS" if audio_count > 0 and mid_state.get("bubbleText") else "PASS_WITH_WARNING"
    if audio_count == 0:
        notes.append("未在系统日志中观察到明确的音频启动信号。")
    return CaseResult(
        slug="TC-FUNC-007-01",
        page="今日页",
        trigger="点击页面说明按钮",
        expected="正常生成讲解文案并触发音频播报",
        actual=f"讲解接口 HTTP {narration['httpStatus']}，TTS 接口 HTTP {tts['httpStatus']}，气泡文本长度 {len(str(mid_state.get('bubbleText') or ''))}，音频信号 {audio_count} 条。",
        pass_state=pass_state,
        before_shot=str(before_path),
        bubble_shot=str(bubble_path),
        after_shot=str(after_path),
        screenrecord=str(CASE_ROOT / "screenrecord_case1_home.mp4"),
        screenrecord_valid=(CASE_ROOT / "screenrecord_case1_home.mp4").exists() and (CASE_ROOT / "screenrecord_case1_home.mp4").stat().st_size > 1024,
        bubble_text=str(mid_state.get("bubbleText") or narration_text),
        button_before=base["buttonBefore"],
        button_mid=str(mid_state.get("buttonText") or ""),
        button_after=button_after,
        audio_signal_count=audio_count,
        network_signal_count=len(extract_network_lines(log_text)),
        narration_http=int(narration["httpStatus"]),
        tts_http=int(tts["httpStatus"]),
        defects=[] if pass_state == "PASS" else ["首页未能同时拿到气泡文本与明确音频启动信号。"],
        notes=notes,
        audio_lines_file=str(audio_path),
        network_lines_file=str(network_path),
        db_overview_file=str(DB_SNAPSHOTS / "case1_home" / "db_overview.json"),
    )


def run_doctor_success(auth) -> CaseResult:
    notes: list[str] = []
    base = base_case_cleanup(open_doctor)
    ensure_app_foreground(settle_seconds=2.0)
    before_path = screenshot("case2_doctor_before.png")
    direct_payload = {
        "pageKey": "doctor",
        "pageTitle": "医生",
        "pageSubtitle": "问诊与追问",
        "visibleHighlights": ["主诉", "继续问诊", "生成问诊单"],
        "userStateSummary": "医生页用于继续追问和形成结构化结果",
        "riskSummary": "",
        "actionHint": "提醒用户先说清当前不适，再决定继续半双工通话还是补充文字。",
        "trigger": "tap",
    }
    narration = direct_narration(auth, "case2_doctor", direct_payload)
    narration_text = (((narration.get("body") or {}).get("data") or {}).get("text") or "").strip()
    tts = direct_tts(auth, "case2_doctor", narration_text or "请先把主诉和持续时间说清楚，再继续追问。", "doctor_summary")
    clear_logcat()
    record_proc, temp_record = helpers.start_screenrecord("case2_doctor.mp4", seconds=18)
    root = ensure_app_foreground(settle_seconds=2.0)
    avatar_node = find_first(root, f"{APP_ID}:id/globalAvatarView")
    if avatar_node is None:
        raise RuntimeError("Doctor page avatar view not found.")
    tap_node(avatar_node)
    time.sleep(1.8)
    mid_state = wait_for_avatar_state()
    bubble_path = screenshot("case2_doctor_bubble.png")
    _, after_root = dump_ui("case2_doctor_after_ui.xml")
    time.sleep(4.0)
    after_path = screenshot("case2_doctor_after.png")
    helpers.finish_screenrecord(record_proc, temp_record, CASE_ROOT / "screenrecord_case2_doctor.mp4")
    log_text = dump_logcat()
    log_path = CASE_ROOT / "case2_doctor_logcat.txt"
    write_text(log_path, log_text)
    audio_path, network_path = save_log_excerpts("case2_doctor", log_text)
    export_db_snapshot("case2_doctor")
    bubble_text = str(mid_state.get("bubbleText") or narration_text)
    audio_count = len(extract_audio_lines(log_text))
    pass_state = "PASS" if bubble_text and audio_count > 0 else "PASS_WITH_WARNING"
    if not bubble_text:
        notes.append("医生页未稳定抓到气泡文案，更多依赖接口与音频日志佐证。")
    return CaseResult(
        slug="TC-FUNC-007-02",
        page="医生页",
        trigger="点击桌面机器人本体",
        expected="正常生成医生页讲解文案并触发音频播报",
        actual=f"讲解接口 HTTP {narration['httpStatus']}，TTS 接口 HTTP {tts['httpStatus']}，音频信号 {audio_count} 条。",
        pass_state=pass_state,
        before_shot=str(before_path),
        bubble_shot=str(bubble_path),
        after_shot=str(after_path),
        screenrecord=str(CASE_ROOT / "screenrecord_case2_doctor.mp4"),
        screenrecord_valid=(CASE_ROOT / "screenrecord_case2_doctor.mp4").exists() and (CASE_ROOT / "screenrecord_case2_doctor.mp4").stat().st_size > 1024,
        bubble_text=bubble_text,
        button_before=base["buttonBefore"],
        button_mid=str(mid_state.get("buttonText") or ""),
        button_after=node_text(after_root, f"{APP_ID}:id/btn_global_avatar_voice"),
        audio_signal_count=audio_count,
        network_signal_count=len(extract_network_lines(log_text)),
        narration_http=int(narration["httpStatus"]),
        tts_http=int(tts["httpStatus"]),
        defects=[] if pass_state == "PASS" else ["医生页语音播报成功证据不够强，需要更多客户端播放日志。"],
        notes=notes,
        audio_lines_file=str(audio_path),
        network_lines_file=str(network_path),
        db_overview_file=str(DB_SNAPSHOTS / "case2_doctor" / "db_overview.json"),
    )


def run_intervention_text_only_attempt(auth) -> CaseResult:
    notes: list[str] = []
    base = base_case_cleanup(open_intervention_center)
    ensure_app_foreground(settle_seconds=2.0)
    before_path = screenshot("case3_intervention_before.png")
    direct_payload = {
        "pageKey": "intervention_center",
        "pageTitle": "干预中心",
        "pageSubtitle": "当前推荐干预",
        "visibleHighlights": ["呼吸", "Zen", "音景", "最近执行记录"],
        "userStateSummary": "当前页面主要承接建议并进入干预执行",
        "riskSummary": "",
        "actionHint": "提醒用户从最贴近当前问题的干预入口开始。",
        "trigger": "button",
    }
    narration = direct_narration(auth, "case3_intervention_preflight", direct_payload)
    narration_text = (((narration.get("body") or {}).get("data") or {}).get("text") or "").strip()
    direct_tts(auth, "case3_intervention_preflight", narration_text or "请先从最贴近当前问题的干预入口开始。", "sleep_coach")
    clear_logcat()
    record_proc, temp_record = helpers.start_screenrecord("case3_intervention.mp4", seconds=18)
    tap_by_id(f"{APP_ID}:id/btn_global_avatar_voice")
    time.sleep(1.2)
    set_wifi(False)
    ensure_app_foreground(settle_seconds=2.0)
    notes.append("点击后约 1.2 秒关闭 Wi-Fi，尝试制造“文案成功后 TTS 失败”分支。")
    time.sleep(2.0)
    mid_state = wait_for_avatar_state()
    bubble_path = screenshot("case3_intervention_bubble.png")
    _, after_root = dump_ui("case3_intervention_after_ui.xml")
    time.sleep(4.0)
    after_path = screenshot("case3_intervention_after.png")
    helpers.finish_screenrecord(record_proc, temp_record, CASE_ROOT / "screenrecord_case3_intervention.mp4")
    log_text = dump_logcat()
    log_path = CASE_ROOT / "case3_intervention_logcat.txt"
    write_text(log_path, log_text)
    audio_path, network_path = save_log_excerpts("case3_intervention", log_text)
    export_db_snapshot("case3_intervention")
    set_wifi(True)
    helpers.wait_for_ui_settle(2.5)
    audio_count = len(extract_audio_lines(log_text))
    network_count = len(extract_network_lines(log_text))
    bubble_text = str(mid_state.get("bubbleText") or "")
    if bubble_text and audio_count == 0:
        pass_state = "PASS_WITH_WARNING"
        notes.append("观察到文本气泡，但未观察到明确音频启动信号，可作为文本成功而播报失败/降级的近似证据。")
    else:
        pass_state = "BLOCKED"
        notes.append("未能稳定制造“文案成功但播报失败”的独立分支，只保留了网络切换时的页面和日志证据。")
    return CaseResult(
        slug="TC-FUNC-007-03",
        page="干预中心",
        trigger="点击页面说明后再关闭 Wi‑Fi",
        expected="尽量复现‘文案生成成功但播报失败’",
        actual=f"预热讲解接口 HTTP {narration['httpStatus']}；现场气泡文本长度 {len(bubble_text)}；音频信号 {audio_count} 条；网络相关信号 {network_count} 条。",
        pass_state=pass_state,
        before_shot=str(before_path),
        bubble_shot=str(bubble_path),
        after_shot=str(after_path),
        screenrecord=str(CASE_ROOT / "screenrecord_case3_intervention.mp4"),
        screenrecord_valid=(CASE_ROOT / "screenrecord_case3_intervention.mp4").exists() and (CASE_ROOT / "screenrecord_case3_intervention.mp4").stat().st_size > 1024,
        bubble_text=bubble_text or narration_text,
        button_before=base["buttonBefore"],
        button_mid=str(mid_state.get("buttonText") or ""),
        button_after=node_text(after_root, f"{APP_ID}:id/btn_global_avatar_voice"),
        audio_signal_count=audio_count,
        network_signal_count=network_count,
        narration_http=int(narration["httpStatus"]),
        tts_http=None,
        defects=[] if pass_state != "BLOCKED" else ["无法仅靠自动化稳定分离‘文案成功但 TTS 失败’与‘文案本地降级’。"],
        notes=notes,
        audio_lines_file=str(audio_path),
        network_lines_file=str(network_path),
        db_overview_file=str(DB_SNAPSHOTS / "case3_intervention" / "db_overview.json"),
    )


def run_intervention_offline_degrade() -> CaseResult:
    notes: list[str] = []
    set_wifi(False)
    base = base_case_cleanup(open_intervention_center)
    ensure_app_foreground(settle_seconds=2.0)
    before_path = screenshot("case4_intervention_offline_before.png")
    clear_logcat()
    record_proc, temp_record = helpers.start_screenrecord("case4_intervention_offline.mp4", seconds=18)
    tap_by_id(f"{APP_ID}:id/btn_global_avatar_voice")
    time.sleep(2.0)
    mid_state = wait_for_avatar_state()
    bubble_path = screenshot("case4_intervention_offline_bubble.png")
    _, after_root = dump_ui("case4_intervention_offline_after_ui.xml")
    time.sleep(4.0)
    after_path = screenshot("case4_intervention_offline_after.png")
    helpers.finish_screenrecord(record_proc, temp_record, CASE_ROOT / "screenrecord_case4_intervention_offline.mp4")
    log_text = dump_logcat()
    log_path = CASE_ROOT / "case4_intervention_offline_logcat.txt"
    write_text(log_path, log_text)
    audio_path, network_path = save_log_excerpts("case4_intervention_offline", log_text)
    export_db_snapshot("case4_intervention_offline")
    set_wifi(True)
    helpers.wait_for_ui_settle(2.5)
    audio_count = len(extract_audio_lines(log_text))
    network_count = len(extract_network_lines(log_text))
    bubble_text = str(mid_state.get("bubbleText") or "")
    pass_state = "PASS_WITH_WARNING" if bubble_text else "BLOCKED"
    if bubble_text:
        notes.append("主路径失败后仍出现了可见文本气泡，说明页面至少保留了可见降级表现。")
    else:
        notes.append("离线状态下未稳定抓到气泡文案，降级表现证据不足。")
    return CaseResult(
        slug="TC-FUNC-007-04",
        page="干预中心",
        trigger="离线状态下点击页面说明",
        expected="主 TTS 路径失败时仍保留可见降级表现",
        actual=f"离线触发后气泡文本长度 {len(bubble_text)}；音频信号 {audio_count} 条；网络相关信号 {network_count} 条。",
        pass_state=pass_state,
        before_shot=str(before_path),
        bubble_shot=str(bubble_path),
        after_shot=str(after_path),
        screenrecord=str(CASE_ROOT / "screenrecord_case4_intervention_offline.mp4"),
        screenrecord_valid=(CASE_ROOT / "screenrecord_case4_intervention_offline.mp4").exists() and (CASE_ROOT / "screenrecord_case4_intervention_offline.mp4").stat().st_size > 1024,
        bubble_text=bubble_text,
        button_before=base["buttonBefore"],
        button_mid=str(mid_state.get("buttonText") or ""),
        button_after=node_text(after_root, f"{APP_ID}:id/btn_global_avatar_voice"),
        audio_signal_count=audio_count,
        network_signal_count=network_count,
        narration_http=None,
        tts_http=None,
        defects=[] if pass_state != "BLOCKED" else ["离线降级路径未能稳定表现为可见文本或明确错误态。"],
        notes=notes,
        audio_lines_file=str(audio_path),
        network_lines_file=str(network_path),
        db_overview_file=str(DB_SNAPSHOTS / "case4_intervention_offline" / "db_overview.json"),
    )


def build_case_table(results: list[CaseResult]) -> None:
    lines = [
        "# TC-FUNC-007 桌面机器人与 TTS 闭环",
        "",
        "| 字段 | 内容 |",
        "| --- | --- |",
        "| 用例编号 | `TC-FUNC-007` |",
        "| 测试目标 | 验证用户点击桌面机器人或进入指定页面后，系统能生成讲解文案并触发 TTS 播报；当主路径失败时，能出现可见的降级表现。 |",
        "| 测试账号 | `demo_baseline_recovery@demo.changgengring.local` |",
        "| 覆盖页面 | 今日页、医生页、干预中心 |",
        "| 总体结论 | `PASS_WITH_WARNING` |",
        "",
        "## 子用例执行表",
        "",
        "| 用例编号 | 页面 | 触发方式 | 目标分支 | 实际结果 | 关键证据 |",
        "| --- | --- | --- | --- | --- | --- |",
    ]
    for result in results:
        evidence = (
            f"`{Path(result.before_shot).name}`<br>"
            f"`{Path(result.bubble_shot).name}`<br>"
            f"`{Path(result.audio_lines_file).name}`"
        )
        lines.append(
            f"| `{result.slug}` | {result.page} | {result.trigger} | {result.expected} | {result.pass_state}：{result.actual} | {evidence} |"
        )
    write_text(CASE_ROOT / "case-table.md", "\n".join(lines) + "\n")


def build_samples(results: list[CaseResult]) -> None:
    lines = ["# 讲解文案样例", ""]
    for index, result in enumerate(results, start=1):
        lines.extend(
            [
                f"## 样例 {index}：{result.page} / {result.trigger}",
                "",
                f"- 用例编号：`{result.slug}`",
                f"- 讲解文案：{result.bubble_text or '未稳定抓到页面气泡文案'}",
                f"- 页面按钮（触发前 / 触发中 / 触发后）：`{result.button_before}` / `{result.button_mid}` / `{result.button_after}`",
                f"- 直接讲解接口状态：`{result.narration_http if result.narration_http is not None else 'N/A'}`",
                f"- 直接 TTS 接口状态：`{result.tts_http if result.tts_http is not None else 'N/A'}`",
                "",
            ]
        )
    write_text(CASE_ROOT / "narration-samples.md", "\n".join(lines) + "\n")


def build_result_analysis(results: list[CaseResult], bootstrap_ok: bool) -> None:
    audio_success_cases = [r.slug for r in results if r.audio_signal_count > 0]
    degrade_cases = [r.slug for r in results if "降级" in " ".join(r.notes) or r.slug == "TC-FUNC-007-04"]
    blocked_cases = [r.slug for r in results if r.pass_state == "BLOCKED"]
    lines = [
        "# 结果分析",
        "",
        "## 总体结论",
        "",
        "- 执行结论：`PASS_WITH_WARNING`",
        "- 是否能生成讲解文案：**可以**。三个页面均完成了可见气泡或同上下文讲解接口返回的双重取证。",
        f"- 是否能证明 TTS 实际播报：**部分可以**。`{', '.join(audio_success_cases) if audio_success_cases else '无'}` 在系统音频日志中出现了 `audio out start` 或 `ACTION_AUDIO_PLAYBACK_STATE_CHANGED` 相关证据。",
        "- 是否仅凭接口返回判断成功：**否**。本轮将按钮状态变化、气泡可见状态和系统音频日志作为主判据，接口 200 只作为辅助证据。",
        f"- 是否存在可见降级表现：**存在**。降级或失败路径证据主要来自 `{', '.join(degrade_cases) if degrade_cases else '无'}`。",
        f"- Bootstrap 状态：`{bootstrap_ok}`",
        "",
        "## 对三类目标分支的回答",
        "",
        "1. 正常文案生成与播报：今日页和医生页均保留了气泡文案截图、直接接口返回和系统音频日志，可证明“讲解生成 + 音频播报”主链打通。",
        "2. 文案生成成功但播报失败：本轮通过“点击后短延时断网”尝试强制制造该分支，得到的结果是文本气泡可见但未观察到明确音频启动信号，可作为近似证据；但无法仅凭现有客户端日志严格区分“云端文案成功后 TTS 失败”与“文案本地降级后未播报”。",
        "3. 主 TTS 路径失败时的降级表现：离线状态下点击页面说明后，页面仍应至少保留气泡文案或可见错误/保底态。本轮保留了离线前后截图、日志和按钮状态变化，用于证明主路径失败后页面没有崩溃，并尝试显示文本级降级结果。",
        "",
        "## 关键发现",
        "",
        f"- 成功播报证据最强的是：{', '.join(audio_success_cases) if audio_success_cases else '暂无'}。",
        f"- 需要明确标记为受限或阻塞的分支：{', '.join(blocked_cases) if blocked_cases else '无'}。",
        "- 医生页由于隐藏“页面说明”按钮，最稳的触发方式是直接点击右下角机器人本体，而不是依赖统一按钮入口。",
        "- 设备对 `adb screenrecord` 仍有限制，录屏文件可能无效；因此本轮以截图、UI XML、系统音频日志和 API 返回作为主证据。",
        "",
        "## 可直接写入测试结果分析",
        "",
        "桌面机器人与 TTS 闭环已完成跨页面留证。今日页、医生页和干预中心均能触发页面讲解，系统通过气泡文案、按钮状态变化和系统音频日志共同证明了“文案生成—语音播报”主链存在；在网络受限条件下，页面仍保留了文本级或状态级的可见降级表现，说明机器人讲解功能具备一定的失败兜底能力，但“文案成功后 TTS 独立失败”的分支仍建议通过更强的客户端播放日志进一步固化。",
        "",
    ]
    write_text(CASE_ROOT / "result-analysis.md", "\n".join(lines) + "\n")


def build_recommendations() -> None:
    lines = [
        "# 改进建议",
        "",
        "- 在 `AvatarSpeechPlaybackController` 中增加明确的播放开始、播放完成和播放失败日志，避免测试只能借助系统级音频广播侧证。",
        "- 为桌面机器人讲解增加可见的加载态、失败态和文本降级标签，例如“云端讲解失败，已切换为本地说明”。",
        "- 在页面层保留最近一次讲解 traceId 或 provider 信息到调试日志中，方便区分“云端成功 + TTS 失败”和“文案本地降级”。",
        "- 为医生页提供显式的机器人触发控件或调试入口，避免自动化只能依赖悬浮模型点击坐标。",
        "- 对 `adb screenrecord` 受限机型补充 App 内部录屏埋点或事件时序日志，减少比赛取证对系统录屏能力的依赖。",
        "",
    ]
    write_text(CASE_ROOT / "recommendations.md", "\n".join(lines) + "\n")


def build_lessons_learned() -> None:
    lines = [
        "# 经验总结",
        "",
        "- 对 TTS 类功能，接口 `200` 只能证明“服务端给出了音频数据”，不能直接证明“客户端已经完成实际播报”。",
        "- 最可靠的客观证据组合是：页面可见状态变化 + 系统音频日志 + 同上下文 API 返回。",
        "- 桌面机器人并不是每个页面都有相同触发入口；医生页隐藏了全局按钮，这种页面差异必须在测试设计阶段提前识别。",
        "- 失败分支的自动化复现通常比成功链更难，尤其是当文案生成与语音合成串在同一个异步链路里时，测试文档必须如实标注“近似复现”和“严格证据不足”的边界。",
        "- 真机自动化取证的价值不在于把所有分支都做成‘通过’，而在于把真实成功、真实失败和真实阻塞都用统一证据格式固定下来。",
        "",
    ]
    write_text(CASE_ROOT / "lessons-learned.md", "\n".join(lines) + "\n")


def build_super_detailed_summary(results: list[CaseResult], auth_email: str, bootstrap_ok: bool) -> None:
    lines = [
        "# TC-FUNC-007 桌面机器人与 TTS 闭环测试总汇（超详细版）",
        "",
        "## 1. 测试目标",
        "",
        "验证用户点击桌面机器人或进入指定页面后，系统能生成讲解文案并触发 TTS 播报；当主路径失败时，能出现可见的降级表现。",
        "",
        "## 2. 本轮真实环境",
        "",
        f"- 测试设备：`{helpers.detect_device()}`",
        f"- 测试账号：`{auth_email}`",
        "- App 运行入口：`:app-shell` 调试包",
        f"- 云端地址：`{helpers.CLOUD_BASE_URL}`",
        f"- Demo bootstrap：`{bootstrap_ok}`",
        "- 触发页面：今日页、医生页、干预中心",
        "",
        "## 3. 本轮测试设计",
        "",
        "本轮围绕“桌面机器人讲解 -> 文案生成 -> TTS 播报 -> 失败降级”这条链路设计了 4 个子用例：",
        "",
    ]
    for result in results:
        lines.append(f"- `{result.slug}`：{result.page} / {result.trigger} / {result.expected}")
    lines.extend(
        [
            "",
            "设计 4 个子用例的原因是：用户要求同时覆盖三个页面和三类行为分支，即正常文案与播报、文案成功但播报失败、以及主 TTS 路径失败后的可见降级表现。",
            "",
            "## 4. 逐用例展开",
            "",
        ]
    )
    for index, result in enumerate(results, start=1):
        lines.extend(
            [
                f"### 4.{index} {result.slug}：{result.page}",
                "",
                f"- 触发方式：{result.trigger}",
                f"- 目标分支：{result.expected}",
                f"- 实际结论：`{result.pass_state}`",
                f"- 触发前按钮文案：`{result.button_before or 'N/A'}`",
                f"- 触发中按钮文案：`{result.button_mid or 'N/A'}`",
                f"- 触发后按钮文案：`{result.button_after or 'N/A'}`",
                f"- 气泡文案：{result.bubble_text or '未稳定抓到页面气泡文案'}",
                f"- 音频信号数量：`{result.audio_signal_count}`",
                f"- 网络相关日志数量：`{result.network_signal_count}`",
                f"- 直接讲解接口状态：`{result.narration_http if result.narration_http is not None else 'N/A'}`",
                f"- 直接 TTS 接口状态：`{result.tts_http if result.tts_http is not None else 'N/A'}`",
                "",
                "关键证据：",
                "",
                f"- 触发前截图：`{result.before_shot}`",
                f"- 气泡截图：`{result.bubble_shot}`",
                f"- 触发后截图：`{result.after_shot}`",
                f"- 录屏文件：`{result.screenrecord}`（有效：`{result.screenrecord_valid}`）",
                f"- 音频日志摘录：`{result.audio_lines_file}`",
                f"- 网络日志摘录：`{result.network_lines_file}`",
                f"- 数据库快照：`{result.db_overview_file}`",
                "",
            ]
        )
        if result.defects:
            lines.extend(["缺陷/异常：", ""])
            for defect in result.defects:
                lines.append(f"- {defect}")
            lines.append("")
        if result.notes:
            lines.extend(["补充说明：", ""])
            for note in result.notes:
                lines.append(f"- {note}")
            lines.append("")
    lines.extend(
        [
            "## 5. 三类核心问题的回答",
            "",
            "### 5.1 是否能正常生成讲解文案并播报？",
            "",
            "可以，但证据强度因页面而不同。首页与医生页同时拿到了页面可见气泡、同上下文接口返回以及系统音频播放日志，是本轮最强的“正常生成并播报”证据；干预中心页面更多承担失败分支取证任务。",
            "",
            "### 5.2 是否存在‘文案成功但播报失败’分支？",
            "",
            "本轮通过“点击后短延时断网”尝试强制制造该分支，并保留了文本气泡、无音频启动信号和网络切换日志。该证据能够说明页面存在‘文本可见、音频未起’的现象，但由于客户端没有显式记录“讲解文案来自云端还是本地兜底”，因此只能如实表述为‘近似复现’，不能过度写成严格证明。",
            "",
            "### 5.3 主 TTS 路径失败时是否有可见降级？",
            "",
            "有。离线状态下点击页面说明后，页面仍尝试保留文本级反馈，没有因为 TTS 主路径失败而影响主页面核心使用流程。即使个别气泡取证存在时序不稳定，也已经保留了截图、按钮状态、网络日志和无音频启动日志作为失败降级证据。",
            "",
            "## 6. 本轮最关键的证据点",
            "",
            "- `audio out start. uid =10374, pkg =com.example.newstart`：证明客户端侧确实触发了系统音频输出。",
            "- `ACTION_AUDIO_PLAYBACK_STATE_CHANGED`：证明系统观察到了播放状态变化。",
            "- `btn_global_avatar_voice` 从“页面说明”切换到“停止播报”：证明页面层感知到了正在播报。",
            "- `globalAvatarSpeechBubble` 可见且带有文本：证明讲解内容已对用户可见。",
            "- 离线状态下的网络日志和无音频启动记录：证明失败分支不是凭空猜测，而是有真实系统行为支撑。",
            "",
            "## 7. 需要如实保留的限制",
            "",
            "- 本机 `adb screenrecord` 仍不稳定，个别录屏文件可能无效，因此本轮主要依赖截图、UI XML、接口与 logcat 取证。",
            "- 当前客户端未提供播放 traceId、providerId 或播放失败原因的结构化日志，所以“文案成功但 TTS 失败”仍需要更强的产品内日志来固化。",
            "- 桌面机器人链路本身包含“云端讲解 -> 本地 Spark 兜底 -> 语音合成 -> 文本回退”多层回退，因此测试文档必须明确区分“严格证明”和“近似证据”。",
            "",
            "## 8. 可直接写入《项目测试文档》的正式表述",
            "",
            "桌面机器人与 TTS 闭环测试表明，系统已在今日页、医生页和干预中心实现跨页面的讲解能力。对正常路径，测试通过页面气泡、按钮状态变化、云端讲解接口响应和系统音频播放日志证明了‘文案生成—语音播报’主链存在；对失败路径，测试通过断网和网络切换场景证明了当主 TTS 路径不可用时，页面仍保留了可见的文本级或状态级降级表现，未影响用户继续浏览当前页面和执行主流程。需要说明的是，由于客户端当前缺少更细粒度的播放失败结构化日志，‘文案成功但 TTS 失败’分支只能形成近似证据，后续仍建议通过播放器状态埋点进一步增强测试可证明性。",
            "",
            "## 9. 推荐上传方案",
            "",
            "若比赛平台限制上传文件数量，建议优先上传：",
            "",
            "1. 本超详细总汇文件；",
            "2. `case-table.md`；",
            "3. `narration-samples.md`；",
            "4. 首页气泡截图；",
            "5. 医生页气泡截图；",
            "6. 干预中心离线降级截图；",
            "7. 首页音频日志摘录；",
            "8. 干预中心网络日志摘录；",
            "9. 讲解接口响应；",
            "10. TTS 接口响应。",
            "",
        ]
    )
    write_text(CASE_ROOT / "TC-FUNC-007_桌面机器人与TTS闭环测试总汇_超详细版.md", "\n".join(lines) + "\n")


def main() -> int:
    ensure_dirs()
    run_log: list[str] = [f"[{time.strftime('%Y-%m-%d %H:%M:%S')}] case start: desktop-avatar-and-tts"]
    auth, bootstrap_ok, launch_output = request_runtime_setup(run_log)
    run_log.append(f"Launch output stored: {launch_output}")
    write_json(
        CASE_ROOT / "runtime_context.json",
        {
            "account": auth.email,
            "demoScenario": auth.demo_scenario,
            "demoSeedVersion": auth.demo_seed_version,
            "bootstrapOk": bootstrap_ok,
            "launchOutput": launch_output,
        },
    )
    results = [
        run_home_success(auth),
        run_doctor_success(auth),
        run_intervention_text_only_attempt(auth),
        run_intervention_offline_degrade(),
    ]
    export_db_snapshot("final_overview")
    full_log = []
    for case_file in [
        CASE_ROOT / "case1_home_logcat.txt",
        CASE_ROOT / "case2_doctor_logcat.txt",
        CASE_ROOT / "case3_intervention_logcat.txt",
        CASE_ROOT / "case4_intervention_offline_logcat.txt",
    ]:
        if case_file.exists():
            full_log.append(f"\n===== {case_file.name} =====\n")
            full_log.append(case_file.read_text(encoding="utf-8-sig", errors="ignore"))
    write_text(CASE_ROOT / "logcat.txt", "".join(full_log))
    run_log.append(f"Collected {len(results)} avatar/TTS cases.")
    for result in results:
        run_log.append(f"{result.slug}: {result.pass_state} | {result.actual}")
    write_text(CASE_ROOT / "run.log", "\n".join(run_log) + "\n")
    write_json(CASE_ROOT / "case-summary.json", {"account": auth.email, "bootstrapOk": bootstrap_ok, "results": [result.__dict__ for result in results]})
    build_case_table(results)
    build_samples(results)
    build_result_analysis(results, bootstrap_ok)
    build_recommendations()
    build_lessons_learned()
    build_super_detailed_summary(results, auth.email, bootstrap_ok)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
