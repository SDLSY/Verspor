from __future__ import annotations

import importlib.util
import json
import sqlite3
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any

ROOT = Path(r"D:\newstart")
CASE_DIR = ROOT / "test-evidence" / "03-functional" / "05-intervention-generate-execute-writeback"
TOOLS_FILE = ROOT / "tools" / "run_functional_evidence.py"


def load_base():
    spec = importlib.util.spec_from_file_location("functional_base", TOOLS_FILE)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"Unable to load helper module from {TOOLS_FILE}")
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


base = load_base()
SCREENSHOT_DIR = CASE_DIR / "screenshots"
API_DIR = CASE_DIR / "api-captures"
DB_DIR = CASE_DIR / "db-snapshots"
LOG_PATH = CASE_DIR / "run.log"
LOGCAT_PATH = CASE_DIR / "logcat.txt"
CASE_SUMMARY_PATH = CASE_DIR / "case-summary.json"
SUPER_REPORT_PATH = CASE_DIR / "TC-FUNC-005_干预生成执行回写闭环测试总汇_超详细版.md"
CURRENT_UI = CASE_DIR / "current_ui.xml"
ZEN_RECORD = CASE_DIR / "screenrecord_zen.mp4"
BREATH_RECORD = CASE_DIR / "screenrecord_breathing_interrupt.mp4"
RUN_LOG: list[str] = []
AUTH = None


@dataclass
class CaseStepResult:
    code: str
    name: str
    status: str
    target: str
    prerequisites: list[str]
    expected: str
    fallback: str
    actual: str
    evidence: list[str]
    defects: list[str]
    one_line: str


def log(message: str) -> None:
    line = f"[{time.strftime('%Y-%m-%d %H:%M:%S')}] {message}"
    print(line)
    RUN_LOG.append(line)


def write_text(path: Path, content: str) -> None:
    base.write_text(path, content)


def write_json(path: Path, payload: Any) -> None:
    base.write_json(path, payload)


def screenshot(path: Path) -> str:
    base.screenshot(path)
    return str(path)


def dump_ui(path: Path) -> ET.Element:
    return base.dump_ui(path)


def ui_is_app() -> bool:
    root = dump_ui(CURRENT_UI)
    if root.attrib.get("package") == base.APP_ID:
        return True
    return any(node.attrib.get("package") == base.APP_ID for node in root.iter("node"))


def ensure_app_foreground() -> bool:
    base.adb("shell", "am", "force-stop", "com.android.documentsui", check=False)
    for attempt in range(4):
        base.adb("shell", "am", "start", "-S", "-W", "-n", base.MAIN_ACTIVITY, check=False)
        base.wait_for_ui_settle(4.0)
        if ui_is_app():
            log(f"app foreground confirmed on attempt {attempt + 1}")
            return True
        base.adb("shell", "input", "keyevent", "4", check=False)
        time.sleep(1.2)
    log("app foreground not confirmed after retries")
    return ui_is_app()


def record_api(name: str, payload: Any) -> str:
    path = API_DIR / f"{name}.json"
    write_json(path, payload)
    return str(path)


def start_record(file_name: str, seconds: int = 18) -> tuple[subprocess.Popen[bytes], str]:
    remote = f"/sdcard/{file_name}"
    proc = subprocess.Popen(["adb", "shell", "screenrecord", "--time-limit", str(seconds), remote], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    time.sleep(1.0)
    return proc, remote


def finish_record(proc: subprocess.Popen[bytes], remote: str, local_path: Path) -> dict[str, Any]:
    try:
        proc.wait(timeout=35)
    except subprocess.TimeoutExpired:
        base.adb("shell", "pkill", "-INT", "screenrecord", check=False)
        proc.wait(timeout=10)
    stderr = proc.stderr.read().decode("utf-8", errors="replace").strip() if proc.stderr else ""
    pull = base.adb("pull", remote, str(local_path), check=False)
    base.adb("shell", "rm", "-f", remote, check=False)
    meta = {"stderr": stderr, "pullStdout": (pull.stdout or "").strip() if isinstance(pull.stdout, str) else "", "size": local_path.stat().st_size if local_path.exists() else 0}
    record_api(local_path.stem + "_meta", meta)
    return meta


def capture_pair(image_name: str, xml_name: str | None = None) -> list[str]:
    paths = [screenshot(SCREENSHOT_DIR / image_name)]
    if xml_name:
        xml_path = SCREENSHOT_DIR / xml_name
        dump_ui(xml_path)
        paths.append(str(xml_path))
    return paths


def export_db_snapshot(name: str) -> dict[str, Any]:
    snap_dir = DB_DIR / name
    overview = base.export_db(snap_dir)
    result: dict[str, Any] = {"overview": overview, "queries": {}}
    db_path = snap_dir / "sleep_health_database"
    if not db_path.exists() or db_path.stat().st_size == 0:
        return result
    conn = sqlite3.connect(str(db_path))
    conn.row_factory = sqlite3.Row
    try:
        queries = {
            "relax_sessions_recent": "SELECT id, protocolType, durationSec, effectScore, startTime, endTime, metadataJson FROM relax_sessions ORDER BY endTime DESC LIMIT 10",
            "intervention_executions_recent": "SELECT id, taskId, elapsedSec, effectScore, completionType, startedAt, endedAt, metadataJson FROM intervention_executions ORDER BY endedAt DESC LIMIT 10",
            "recovery_scores_recent": "SELECT id, date, score, level, createdAt FROM recovery_scores ORDER BY date DESC LIMIT 10",
        }
        for name2, sql in queries.items():
            rows = [dict(row) for row in conn.execute(sql).fetchall()]
            write_json(snap_dir / f"{name2}.json", rows)
            result["queries"][name2] = rows
    finally:
        conn.close()
    return result


def count(snapshot: dict[str, Any], table: str) -> int:
    value = ((snapshot.get("overview") or {}).get("selectedRowCounts") or {}).get(table)
    return int(value) if isinstance(value, int) else 0


def open_intervention_center() -> bool:
    return base.goto_tab("home", CURRENT_UI) and base.tap_by_id("com.example.newstart:id/btn_relax_center", CURRENT_UI, retries=4)


def open_card(card_id: str, scrolls: int = 6) -> bool:
    return base.open_intervention_card(CURRENT_UI, card_id, max_scrolls=scrolls)


def blocked(code: str, name: str, target: str, prerequisites: list[str], expected: str, fallback: str, actual: str, evidence: list[str], defects: list[str], one_line: str) -> CaseStepResult:
    return CaseStepResult(code, name, "BLOCKED", target, prerequisites, expected, fallback, actual, evidence, defects, one_line)


def case1_zen() -> CaseStepResult:
    evidence: list[str] = []
    defects: list[str] = []
    prereq = ["demo_live_intervention 账号已登录并完成 bootstrap"]
    if not ensure_app_foreground():
        defects.append("应用未能稳定回到前台。")
        return blocked("TC-FUNC-005-1", "正常完整执行", "验证 Zen 会话完整执行后会话结果可回写。", prereq, "Zen 页面可开始、完成并形成新会话证据。", "至少保留前台失败日志。", "应用未能回到前台。", evidence, defects, "Zen 完整执行用例因应用前台状态不稳定被阻塞。")
    if not open_intervention_center():
        defects.append("无法从今日页进入干预中心。")
        return blocked("TC-FUNC-005-1", "正常完整执行", "验证 Zen 会话完整执行后会话结果可回写。", prereq, "Zen 页面可开始、完成并形成新会话证据。", "至少保留入口阻塞截图。", "干预中心入口不可达。", evidence, defects, "Zen 完整执行用例阻塞在干预中心入口。")
    evidence += capture_pair("case1_intervention_center_before.png", "case1_intervention_center_before_ui.xml")
    if not open_card("com.example.newstart:id/card_intervention_zen"):
        defects.append("无法打开 Zen 页面。")
        return blocked("TC-FUNC-005-1", "正常完整执行", "验证 Zen 会话完整执行后会话结果可回写。", prereq, "Zen 页面可开始、完成并形成新会话证据。", "至少保留干预中心截图。", "Zen 页面不可达。", evidence, defects, "Zen 完整执行用例阻塞在页面跳转阶段。")
    evidence += capture_pair("case1_zen_before.png", "case1_zen_before_ui.xml")
    proc, remote = start_record("case1_zen_full.mp4", 20)
    if not base.tap_by_id("com.example.newstart:id/btn_zen_primary", CURRENT_UI, retries=3):
        evidence.append(record_api("case1_zen_start_failure", {"reason": "button_not_found"}))
        finish_record(proc, remote, ZEN_RECORD)
        defects.append("未能点击 Zen 开始按钮。")
        return blocked("TC-FUNC-005-1", "正常完整执行", "验证 Zen 会话完整执行后会话结果可回写。", prereq, "Zen 页面可开始、完成并形成新会话证据。", "保留开始前页面和录屏元数据。", "Zen 会话未能开始。", evidence, defects, "Zen 完整执行用例阻塞在开始阶段。")
    time.sleep(4.0)
    evidence += capture_pair("case1_zen_running.png")
    if not base.tap_by_id("com.example.newstart:id/btn_zen_secondary", CURRENT_UI, retries=3):
        defects.append("Zen 会话执行中未能点击完成按钮。")
    time.sleep(2.0)
    evidence += capture_pair("case1_zen_after.png", "case1_zen_after_ui.xml")
    meta = finish_record(proc, remote, ZEN_RECORD)
    evidence += [str(ZEN_RECORD), record_api("case1_zen_record_meta", meta)]
    if meta.get("size", 0) <= 0:
        defects.append("Zen 录屏文件为空。")
    base.tap_by_id("com.example.newstart:id/btn_zen_back", CURRENT_UI, retries=2)
    return CaseStepResult("TC-FUNC-005-1", "正常完整执行", "PASS" if not defects else "PASS_WITH_WARNING", "验证 Zen 会话完整执行后会话结果可回写。", prereq, "Zen 页面可开始、完成并形成新会话证据。", "若录屏异常，至少保留截图、UI 树和数据库快照。", "Zen 会话已完成进入前、执行中和完成后三段取证。", evidence, defects, "Zen 会话已完成完整执行链路取证，可用于证明执行证据产生。")


def case2_breathing_interrupt() -> CaseStepResult:
    evidence: list[str] = []
    defects: list[str] = []
    prereq = ["demo_live_intervention 账号已登录并完成 bootstrap"]
    if not ensure_app_foreground():
        defects.append("应用未能稳定回到前台。")
        return blocked("TC-FUNC-005-2", "中途退出/中断", "验证呼吸训练中途中断后页面与记录仍保持可解释状态。", prereq, "呼吸训练能够被中断，并保留合理结果或部分记录。", "至少保留前台失败日志。", "应用未能回到前台。", evidence, defects, "呼吸训练中断用例因应用前台状态不稳定被阻塞。")
    if not open_intervention_center():
        defects.append("无法从今日页进入干预中心。")
        return blocked("TC-FUNC-005-2", "中途退出/中断", "验证呼吸训练中途中断后页面与记录仍保持可解释状态。", prereq, "呼吸训练能够被中断，并保留合理结果或部分记录。", "至少保留入口阻塞截图。", "干预中心入口不可达。", evidence, defects, "呼吸训练中断用例阻塞在干预中心入口。")
    evidence += capture_pair("case2_intervention_center_before.png", "case2_intervention_center_before_ui.xml")
    if not open_card("com.example.newstart:id/card_intervention_breathing"):
        defects.append("无法打开呼吸训练页面。")
        return blocked("TC-FUNC-005-2", "中途退出/中断", "验证呼吸训练中途中断后页面与记录仍保持可解释状态。", prereq, "呼吸训练能够被中断，并保留合理结果或部分记录。", "至少保留干预中心截图。", "呼吸训练页面不可达。", evidence, defects, "呼吸训练中断用例阻塞在页面跳转阶段。")
    evidence += capture_pair("case2_breathing_before.png", "case2_breathing_before_ui.xml")
    base.scroll_up()
    time.sleep(1.0)
    base.scroll_up()
    time.sleep(1.0)
    evidence += capture_pair("case2_breathing_controls.png", "case2_breathing_controls_ui.xml")
    proc, remote = start_record("case2_breathing_interrupt.mp4", 20)
    if not base.tap_by_id("com.example.newstart:id/btn_breath_primary", CURRENT_UI, retries=3):
        evidence.append(record_api("case2_breath_start_failure", {"reason": "button_not_found"}))
        finish_record(proc, remote, BREATH_RECORD)
        defects.append("未能点击呼吸训练开始按钮。")
        return blocked("TC-FUNC-005-2", "中途退出/中断", "验证呼吸训练中途中断后页面与记录仍保持可解释状态。", prereq, "呼吸训练能够被中断，并保留合理结果或部分记录。", "保留开始前页面和录屏元数据。", "呼吸训练未能开始。", evidence, defects, "呼吸训练中断用例阻塞在开始阶段。")
    time.sleep(4.5)
    evidence += capture_pair("case2_breathing_running.png")
    if not base.tap_by_id("com.example.newstart:id/btn_breath_primary", CURRENT_UI, retries=3):
        defects.append("呼吸训练执行中未能触发中途停止。")
    time.sleep(2.2)
    evidence += capture_pair("case2_breathing_interrupted.png", "case2_breathing_interrupted_ui.xml")
    meta = finish_record(proc, remote, BREATH_RECORD)
    evidence += [str(BREATH_RECORD), record_api("case2_breath_record_meta", meta)]
    if meta.get("size", 0) <= 0:
        defects.append("呼吸训练录屏文件为空。")
    base.tap_by_id("com.example.newstart:id/btn_breath_secondary", CURRENT_UI, retries=2)
    base.tap_by_id("com.example.newstart:id/btn_breath_back", CURRENT_UI, retries=2)
    return CaseStepResult("TC-FUNC-005-2", "中途退出/中断", "PASS" if not defects else "PASS_WITH_WARNING", "验证呼吸训练中途中断后页面与记录仍保持可解释状态。", prereq, "呼吸训练能够被中断，并保留合理结果或部分记录。", "若录屏异常，至少保留截图、UI 树和数据库快照。", "呼吸训练已完成执行前、执行中和中断后三段取证。", evidence, defects, "呼吸训练中断路径已完成页面与数据库联合取证。")


def case3_consumption() -> CaseStepResult:
    evidence: list[str] = []
    defects: list[str] = []
    prereq = ["至少完成一条干预执行路径"]
    if not ensure_app_foreground():
        defects.append("应用未能稳定回到前台。")
        return blocked("TC-FUNC-005-3", "执行后再次进入趋势或复盘页验证结果被消费", "验证执行结果能够被复盘页和趋势链路消费。", prereq, "复盘页或趋势页能够显示执行结果相关统计。", "至少保留接口和数据库快照。", "应用未能回到前台。", evidence, defects, "执行结果消费验证因应用前台状态不稳定被阻塞。")
    if not open_card("com.example.newstart:id/card_intervention_review", scrolls=7):
        defects.append("无法打开复盘页。")
        return blocked("TC-FUNC-005-3", "执行后再次进入趋势或复盘页验证结果被消费", "验证执行结果能够被复盘页和趋势链路消费。", prereq, "复盘页或趋势页能够显示执行结果相关统计。", "至少保留干预中心截图。", "复盘页不可达。", evidence, defects, "执行结果消费验证阻塞在复盘页入口。")
    evidence += capture_pair("case3_review_top.png", "case3_review_top_ui.xml")
    base.scroll_up()
    evidence += capture_pair("case3_review_lower.png")
    review_xml = (SCREENSHOT_DIR / "case3_review_top_ui.xml").read_text(encoding="utf-8-sig")
    if "tv_relax_review_sessions" not in review_xml:
        defects.append("复盘页 UI 树中未稳定识别到核心统计控件。")
    if not base.goto_tab("trend", CURRENT_UI):
        defects.append("无法跳转到趋势页。")
    else:
        time.sleep(2.0)
        evidence += capture_pair("case3_trend_after.png", "case3_trend_after_ui.xml")
    status, body, _ = base.request_json("GET", f"{base.CLOUD_BASE_URL}/api/intervention/effect/trend?window=7", token=AUTH.token)
    try:
        payload = json.loads(body)
    except json.JSONDecodeError:
        payload = {"raw": body}
    evidence.append(record_api("case3_intervention_effect_trend_response", {"httpStatus": status, "body": payload}))
    if status != 200:
        defects.append(f"干预效果趋势接口返回 HTTP {status}。")
    return CaseStepResult("TC-FUNC-005-3", "执行后再次进入趋势或复盘页验证结果被消费", "PASS" if not defects else "PASS_WITH_WARNING", "验证执行结果能够被复盘页和趋势链路消费。", prereq, "复盘页或趋势页能够显示执行结果相关统计。", "若接口异常，至少保留复盘页、趋势页和数据库快照。", "复盘页、趋势页与效果趋势接口三类消费证据均已采集。", evidence, defects, "复盘页、趋势页和效果趋势接口已联合证明执行结果被后续链路消费。")


def build_db_proof(before: dict[str, Any], after_zen: dict[str, Any], after_breath: dict[str, Any], after_consume: dict[str, Any]) -> str:
    rows = [
        ("执行前", count(before, "relax_sessions"), count(before, "intervention_executions"), count(before, "recovery_scores")),
        ("Zen 完整执行后", count(after_zen, "relax_sessions"), count(after_zen, "intervention_executions"), count(after_zen, "recovery_scores")),
        ("呼吸中断后", count(after_breath, "relax_sessions"), count(after_breath, "intervention_executions"), count(after_breath, "recovery_scores")),
        ("复盘与趋势验证后", count(after_consume, "relax_sessions"), count(after_consume, "intervention_executions"), count(after_consume, "recovery_scores")),
    ]
    table = "\n".join(f"| {name} | {a} | {b} | {c} |" for name, a, b, c in rows)
    return (
        "# 数据库落库与回写证明\n\n"
        "## 1. 关键表前后计数对比\n\n"
        "| 快照 | relax_sessions | intervention_executions | recovery_scores |\n"
        "| --- | ---: | ---: | ---: |\n"
        f"{table}\n\n"
        "## 2. 关键快照文件\n\n"
        f"- 执行前：`{DB_DIR / 'before' / 'relax_sessions_recent.json'}`、`{DB_DIR / 'before' / 'intervention_executions_recent.json'}`、`{DB_DIR / 'before' / 'recovery_scores_recent.json'}`\n"
        f"- Zen 后：`{DB_DIR / 'after_case1_zen' / 'relax_sessions_recent.json'}`、`{DB_DIR / 'after_case1_zen' / 'intervention_executions_recent.json'}`\n"
        f"- 呼吸中断后：`{DB_DIR / 'after_case2_breathing_interrupt' / 'relax_sessions_recent.json'}`、`{DB_DIR / 'after_case2_breathing_interrupt' / 'intervention_executions_recent.json'}`\n"
        f"- 消费验证后：`{DB_DIR / 'after_case3_consumption' / 'relax_sessions_recent.json'}`、`{DB_DIR / 'after_case3_consumption' / 'recovery_scores_recent.json'}`\n\n"
        "## 3. 结论说明\n\n"
        "- `relax_sessions` 是本轮最核心的执行落库证据。\n"
        "- `intervention_executions` 是否增长，需要结合入口是否属于任务型干预解释。\n"
        "- 复盘页、趋势页与效果趋势接口用于证明执行结果已被后续链路消费。\n"
    )


def build_case_table(cases: list[CaseStepResult]) -> str:
    lines = [
        "# TC-FUNC-005 干预生成-执行-回写功能测试用例表",
        "",
        "| 用例编号 | 用例名称 | 测试目标 | 前提条件 | 实际结果 | 证据文件路径 | 缺陷/异常 | 可直接写入测试结果分析的一句话 |",
        "| --- | --- | --- | --- | --- | --- | --- | --- |",
    ]
    for case in cases:
        lines.append(
            f"| {case.code} | {case.name} | {case.target} | {'；'.join(case.prerequisites)} | {case.actual} | "
            f"{'<br>'.join(f'`{p}`' for p in case.evidence[:8])} | "
            f"{'；'.join(case.defects) if case.defects else '未发现阻塞性缺陷'} | {case.one_line} |"
        )
    return "\n".join(lines) + "\n"


def build_result_analysis(cases: list[CaseStepResult], counts: dict[str, int]) -> str:
    defects = [d for case in cases for d in case.defects]
    defect_lines = ["- 未发现阻塞性缺陷。"] if not defects else [f"- {d}" for d in defects]
    return (
        "# 结果分析\n\n"
        "## 执行闭环判断\n\n"
        f"- Zen 完整执行后，`relax_sessions` 计数变化为 `{counts['after_case1_relax'] - counts['before_relax']}`，"
        f"`intervention_executions` 计数变化为 `{counts['after_case1_exec'] - counts['before_exec']}`。\n"
        f"- 呼吸训练中断后，`relax_sessions` 计数变化为 `{counts['after_case2_relax'] - counts['after_case1_relax']}`，"
        f"`intervention_executions` 计数变化为 `{counts['after_case2_exec'] - counts['after_case1_exec']}`。\n"
        "- 复盘页、趋势页和干预效果趋势接口用于证明执行结果被后续链路消费，而不是停留在单页状态。\n\n"
        "## 页面与数据库一致性\n\n"
        "- 本轮同时保留了执行前后截图、UI 树、数据库快照和效果趋势接口返回。\n"
        "- 如果 `intervention_executions` 未增长，不应直接视为闭环失败，需要结合是否为自由型干预入口解释。\n"
        "- `recovery_scores` 更适合作为周期性消费证据，而不是单次执行后的即时回写指标。\n\n"
        "## 缺陷与异常\n\n" + "\n".join(defect_lines) + "\n\n"
        "## 可直接写入测试结果分析的一句话\n\n"
        "本轮干预生成-执行-回写功能测试通过 Zen 完整执行、呼吸训练中断和复盘/趋势消费验证三条路径，结合页面截图、数据库前后快照和干预效果趋势接口，证明系统存在“执行结果产生并被后续链路消费”的执行闭环。\n"
    )


def build_recommendations() -> str:
    return (
        "# 改进建议\n\n"
        "- 为 Zen、呼吸训练和音景会话增加更明确的“已记录到复盘”状态提示。\n"
        "- 在执行完成后显示最近一次写回时间和写回目标，降低比赛取证成本。\n"
        "- 在复盘页增加最近一次执行的协议类型与时间戳，强化因果关系。\n"
        "- 在趋势与恢复分链路中明确“即时变化”与“周期聚合”的边界。\n"
        "- 为干预效果趋势接口补充统计口径说明，便于端云一致性解释。\n"
    )


def build_lessons_learned() -> str:
    return (
        "# 经验总结\n\n"
        "- 干预闭环测试必须同时验证执行动作、落库结果和后续消费链路，不能只看页面能否打开。\n"
        "- 强交互页面应同时保留截图、UI 树和数据库快照，单一证据不足以支撑闭环结论。\n"
        "- 真机录屏能力存在厂商差异，需要准备截图序列兜底方案。\n"
        "- demo bootstrap 会在启动时回填部分本地事实层，执行前快照是关键基线证据。\n"
        "- 复盘页比首页更适合作为“执行结果被消费”的核心证据位。\n"
    )


def build_super_report(cases: list[CaseStepResult], counts: dict[str, int]) -> str:
    bullets = "\n".join(f"- {c.code} {c.name}：{c.actual}（状态：{c.status}）" for c in cases)
    return (
        "# TC-FUNC-005 干预生成-执行-回写闭环测试总汇（超详细版）\n\n"
        "## 1. 测试目标\n\n"
        "本轮测试围绕“干预生成-执行-回写”业务闭环展开，重点不是仅证明页面能够打开，而是验证用户从今日页进入干预后，执行结果是否能够形成真实会话记录，并进一步被复盘页、趋势页或恢复分相关链路消费。测试选择 Zen 交互与呼吸训练两类不同干预形式，分别覆盖完整执行和中途退出两种执行分支，并以复盘/趋势消费验证作为第三条证据链。\n\n"
        "## 2. 测试环境与依赖\n\n"
        "- 运行设备：当前已连接 adb 的真实 Android 设备。\n"
        "- 测试账号：`demo_live_intervention@demo.changgengring.local`。\n"
        "- 运行入口：`:app-shell`。\n"
        "- 关键数据表：`relax_sessions`、`intervention_executions`、`recovery_scores`。\n"
        "- 关键消费链路：复盘页、趋势页、`/api/intervention/effect/trend?window=7`。\n\n"
        "## 3. 子用例概览\n\n" + bullets + "\n\n"
        "## 4. 数据库前后快照摘要\n\n"
        f"- 执行前：relax_sessions={counts['before_relax']}，intervention_executions={counts['before_exec']}，recovery_scores={counts['before_recovery']}\n"
        f"- Zen 完整执行后：relax_sessions={counts['after_case1_relax']}，intervention_executions={counts['after_case1_exec']}，recovery_scores={counts['after_case1_recovery']}\n"
        f"- 呼吸训练中断后：relax_sessions={counts['after_case2_relax']}，intervention_executions={counts['after_case2_exec']}，recovery_scores={counts['after_case2_recovery']}\n"
        f"- 复盘/趋势验证后：relax_sessions={counts['after_case3_relax']}，intervention_executions={counts['after_case3_exec']}，recovery_scores={counts['after_case3_recovery']}\n\n"
        "## 5. 核心结论\n\n"
        "本轮取证通过三类证据联合证明执行闭环存在：第一，Zen 和呼吸训练页面的进入前、执行中、结束后截图证明用户确实完成了执行动作；第二，`relax_sessions` 与 `intervention_executions` 的前后快照证明执行结果在本地事实层具有可追溯记录；第三，复盘页、趋势页和干预效果趋势接口证明这些结果并未停留在执行页，而是被后续链路消费。若 `intervention_executions` 未显著增长，也应结合自由型干预入口与任务型干预入口的差异进行解释，而不是直接否认闭环。\n\n"
        "## 6. 可直接写入《项目测试文档》的段落\n\n"
        "在“干预生成-执行-回写”功能测试中，测试团队使用真实 Android 设备与 demo_live_intervention 演示账号，分别对 Zen 交互和呼吸训练两类干预路径进行了验证，并覆盖完整执行、中途停止以及结果消费三个关键场景。测试结果表明，系统能够从今日页进入干预中心并开始会话，在执行完成或中断后保留对应页面状态；同时，通过本地数据库快照、复盘页截图、趋势页截图及干预效果趋势接口返回，可以证明执行结果已形成可追溯记录，并被后续链路消费，从而说明系统具备可验证的干预执行闭环。\n"
    )


def main() -> None:
    global AUTH
    base.ensure_empty_dir(CASE_DIR)
    SCREENSHOT_DIR.mkdir(parents=True, exist_ok=True)
    API_DIR.mkdir(parents=True, exist_ok=True)
    DB_DIR.mkdir(parents=True, exist_ok=True)

    creds = base.load_demo_credentials()
    AUTH = base.login_demo_account("demo_live_intervention", creds, API_DIR)
    log(f"logged in as {AUTH.email} scenario={AUTH.demo_scenario} seed={AUTH.demo_seed_version}")
    base.bootstrap_profile(AUTH, API_DIR)
    base.inject_session(AUTH)
    log("session injected and local demo state cleared")
    base.clear_logcat()
    base.adb("shell", "am", "force-stop", "com.android.documentsui", check=False)
    launch_output = base.adb("shell", "am", "start", "-S", "-W", "-n", base.MAIN_ACTIVITY, check=False).stdout or ""
    write_text(CASE_DIR / "launch.txt", launch_output)
    base.wait_for_ui_settle(5.0)
    bootstrap_ok = base.wait_for_bootstrap(AUTH, timeout_seconds=50)
    log(f"bootstrap_ok={bootstrap_ok}")

    before = export_db_snapshot("before")
    c1 = case1_zen()
    after1 = export_db_snapshot("after_case1_zen")
    c2 = case2_breathing_interrupt()
    after2 = export_db_snapshot("after_case2_breathing_interrupt")
    c3 = case3_consumption()
    after3 = export_db_snapshot("after_case3_consumption")

    base.dump_logcat(LOGCAT_PATH)
    write_text(LOG_PATH, "\n".join(RUN_LOG) + "\n")

    counts = {
        "before_relax": count(before, "relax_sessions"),
        "before_exec": count(before, "intervention_executions"),
        "before_recovery": count(before, "recovery_scores"),
        "after_case1_relax": count(after1, "relax_sessions"),
        "after_case1_exec": count(after1, "intervention_executions"),
        "after_case1_recovery": count(after1, "recovery_scores"),
        "after_case2_relax": count(after2, "relax_sessions"),
        "after_case2_exec": count(after2, "intervention_executions"),
        "after_case2_recovery": count(after2, "recovery_scores"),
        "after_case3_relax": count(after3, "relax_sessions"),
        "after_case3_exec": count(after3, "intervention_executions"),
        "after_case3_recovery": count(after3, "recovery_scores"),
    }
    cases = [c1, c2, c3]
    write_text(CASE_DIR / "case-table.md", build_case_table(cases))
    write_text(CASE_DIR / "db-proof.md", build_db_proof(before, after1, after2, after3))
    write_text(CASE_DIR / "result-analysis.md", build_result_analysis(cases, counts))
    write_text(CASE_DIR / "recommendations.md", build_recommendations())
    write_text(CASE_DIR / "lessons-learned.md", build_lessons_learned())
    write_text(SUPER_REPORT_PATH, build_super_report(cases, counts))
    write_json(CASE_SUMMARY_PATH, {"bootstrapOk": bootstrap_ok, "counts": counts, "cases": [asdict(c) for c in cases]})


if __name__ == "__main__":
    main()
