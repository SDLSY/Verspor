from __future__ import annotations

import json
import shutil
import sqlite3
import subprocess
import time
from pathlib import Path
from typing import Any
import xml.etree.ElementTree as ET

CASE_DIR = Path(r"D:\newstart\test-evidence\03-functional\02-today-recovery-physiology")
SCREENSHOT_DIR = CASE_DIR / "screenshots"
DB_DIR = CASE_DIR / "db-snapshots"
API_DIR = CASE_DIR / "api-captures"
TMP_DIR = CASE_DIR / "_tmp"

PACKAGE = "com.example.newstart"
ACTIVITY = "com.example.newstart/.MainActivity"
DB_NAME = "sleep_health_database"

HOME_TAB = (557, 1811)


def ensure_dirs() -> None:
    for path in [CASE_DIR, SCREENSHOT_DIR, DB_DIR, API_DIR, TMP_DIR]:
        path.mkdir(parents=True, exist_ok=True)


def now() -> str:
    return time.strftime("%Y-%m-%d %H:%M:%S")


def log(message: str) -> None:
    with (CASE_DIR / "run.log").open("a", encoding="utf-8") as fh:
        fh.write(f"[{now()}] {message}\n")


def run(args: list[str], *, check: bool = True, text: bool = True, capture: bool = True) -> subprocess.CompletedProcess:
    log("RUN " + " ".join(args))
    return subprocess.run(
        args,
        check=check,
        text=text,
        capture_output=capture,
        encoding="utf-8" if text else None,
        errors="ignore" if text else None,
    )


def run_to_file(args: list[str], target: Path) -> None:
    log("RUN_TO_FILE " + " ".join(args) + f" -> {target}")
    with target.open("wb") as fh:
        proc = subprocess.run(args, stdout=fh, stderr=subprocess.PIPE)
    if proc.returncode != 0:
        raise RuntimeError(f"command failed: {' '.join(args)} stderr={proc.stderr.decode('utf-8', errors='ignore')}")


def adb(*args: str, check: bool = True, text: bool = True) -> subprocess.CompletedProcess:
    return run(["adb", *args], check=check, text=text, capture=True)


def adb_exec_out_to_file(args: list[str], target: Path) -> None:
    run_to_file(["adb", "exec-out", *args], target)


def save_text(path: Path, text: str) -> None:
    path.write_text(text, encoding="utf-8")


def backup_live_db(prefix: str) -> tuple[Path, Path]:
    raw_dir = DB_DIR / prefix
    raw_dir.mkdir(parents=True, exist_ok=True)
    for suffix in ["", "-wal", "-shm"]:
        target = raw_dir / f"{DB_NAME}{suffix}"
        adb_exec_out_to_file(["run-as", PACKAGE, "cat", f"databases/{DB_NAME}{suffix}"], target)
    query_db = DB_DIR / f"{prefix}_query.db"
    merge_wal_snapshot(raw_dir / DB_NAME, raw_dir / f"{DB_NAME}-wal", raw_dir / f"{DB_NAME}-shm", query_db)
    return raw_dir / DB_NAME, query_db


def merge_wal_snapshot(main_db: Path, wal: Path, shm: Path, output_db: Path) -> None:
    work_dir = TMP_DIR / f"merge_{output_db.stem}"
    if work_dir.exists():
        shutil.rmtree(work_dir)
    work_dir.mkdir(parents=True)
    work_main = work_dir / DB_NAME
    shutil.copy2(main_db, work_main)
    if wal.exists():
        shutil.copy2(wal, work_dir / f"{DB_NAME}-wal")
    if shm.exists():
        shutil.copy2(shm, work_dir / f"{DB_NAME}-shm")
    src = sqlite3.connect(work_main)
    dst = sqlite3.connect(output_db)
    with dst:
        src.backup(dst)
    src.close()
    dst.close()


def make_variant(source_db: Path, variant_db: Path, variant: str) -> None:
    shutil.copy2(source_db, variant_db)
    conn = sqlite3.connect(variant_db)
    cur = conn.cursor()
    if variant == "complete":
        pass
    elif variant == "physiology_without_sleep":
        cur.execute("DELETE FROM sleep_records")
        cur.execute("DELETE FROM recovery_scores")
    elif variant == "fallback_no_latest":
        cur.execute("DELETE FROM sleep_records")
        cur.execute("DELETE FROM recovery_scores")
        cur.execute("DELETE FROM health_metrics")
        cur.execute("DELETE FROM ppg_samples")
    else:
        raise ValueError(f"unknown variant {variant}")
    conn.commit()
    conn.close()


def install_db(host_db: Path) -> None:
    adb("shell", "am", "force-stop", PACKAGE)
    remote = "/data/local/tmp/today_case.db"
    adb("push", str(host_db), remote)
    adb("shell", "run-as", PACKAGE, "rm", "-f", f"databases/{DB_NAME}", f"databases/{DB_NAME}-wal", f"databases/{DB_NAME}-shm")
    adb("shell", "run-as", PACKAGE, "cp", remote, f"databases/{DB_NAME}")
    adb("shell", "rm", "-f", remote)


def backup_prefs() -> tuple[str, str]:
    session = adb("shell", "run-as", PACKAGE, "cat", "shared_prefs/cloud_session.xml").stdout
    demo = adb("shell", "run-as", PACKAGE, "cat", "shared_prefs/demo_bootstrap_state.xml").stdout
    save_text(TMP_DIR / "cloud_session.xml.bak", session)
    save_text(TMP_DIR / "demo_bootstrap_state.xml.bak", demo)
    return session, demo


def set_demo_bootstrap_state_v2() -> None:
    xml = """<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n    <string name=\"user_id\">93923586-ffaf-4609-a15b-89bae57964b8</string>\n    <string name=\"scenario\">demo_baseline_recovery</string>\n    <string name=\"version\">2026-04-demo-v2</string>\n</map>\n"""
    local = TMP_DIR / "demo_bootstrap_state_v2.xml"
    save_text(local, xml)
    remote = "/data/local/tmp/demo_bootstrap_state_v2.xml"
    adb("push", str(local), remote)
    adb("shell", "run-as", PACKAGE, "cp", remote, "shared_prefs/demo_bootstrap_state.xml")
    adb("shell", "rm", "-f", remote)


def restore_prefs() -> None:
    for name in ["cloud_session.xml", "demo_bootstrap_state.xml"]:
        src = TMP_DIR / f"{name}.bak"
        if src.exists():
            remote = f"/data/local/tmp/{name}.restore"
            adb("push", str(src), remote)
            adb("shell", "run-as", PACKAGE, "cp", remote, f"shared_prefs/{name}")
            adb("shell", "rm", "-f", remote)


def capture_screenshot(path: Path) -> None:
    adb_exec_out_to_file(["screencap", "-p"], path)


def dump_ui(path: Path) -> None:
    remote = "/sdcard/uidump-today-case.xml"
    adb("shell", "uiautomator", "dump", remote)
    adb("pull", remote, str(path))


def open_today_page() -> None:
    adb("shell", "am", "start", "-W", "-n", ACTIVITY)
    time.sleep(2)
    adb("shell", "input", "tap", str(HOME_TAB[0]), str(HOME_TAB[1]))
    time.sleep(4)


def scroll_to_top() -> None:
    for _ in range(2):
        adb("shell", "input", "swipe", "1400", "700", "1400", "1700", "300")
        time.sleep(1)


def scroll_to_lower() -> None:
    adb("shell", "input", "swipe", "1400", "1500", "1400", "500", "500")
    time.sleep(2)


def collect_texts(xml_path: Path) -> dict[str, str]:
    root = ET.parse(xml_path).getroot()
    mapping: dict[str, str] = {}
    for node in root.iter("node"):
        rid = node.attrib.get("resource-id", "")
        text = node.attrib.get("text", "").strip()
        if rid and text:
            mapping[rid] = text
    return mapping


def export_case_db(case_name: str) -> tuple[Path, dict[str, Any]]:
    _, query_db = backup_live_db(case_name)
    overview = describe_db(query_db)
    save_text(DB_DIR / f"{case_name}_db_overview.json", json.dumps(overview, ensure_ascii=False, indent=2))
    return query_db, overview


def describe_db(db_path: Path) -> dict[str, Any]:
    conn = sqlite3.connect(db_path)
    conn.row_factory = sqlite3.Row
    cur = conn.cursor()
    result: dict[str, Any] = {"database": str(db_path), "tables": [], "queries": {}}
    result["tables"] = [row[0] for row in cur.execute("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name")]
    queries = {
        "sleep_records": "SELECT id, date, totalSleepMinutes, sleepEfficiency, awakeCount FROM sleep_records ORDER BY date DESC LIMIT 5",
        "health_metrics": "SELECT id, sleepRecordId, timestamp, heartRateCurrent, bloodOxygenCurrent, temperatureCurrent, hrvCurrent FROM health_metrics ORDER BY timestamp DESC LIMIT 5",
        "recovery_scores": "SELECT id, sleepRecordId, date, score, level FROM recovery_scores ORDER BY date DESC LIMIT 5",
    }
    for name, sql in queries.items():
        rows = cur.execute(sql).fetchall()
        result["queries"][name] = {
            "count": cur.execute(f"SELECT COUNT(*) FROM {name}").fetchone()[0],
            "rows": [dict(row) for row in rows],
        }
    conn.close()
    return result


def attempt_screenrecord(target: Path) -> str:
    proc = subprocess.run(
        ["adb", "exec-out", "screenrecord", "--time-limit", "8", "-"],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        timeout=20,
    )
    target.write_bytes(proc.stdout)
    if len(proc.stdout) < 1024:
        return f"blocked_or_invalid:{len(proc.stdout)}"
    return f"ok:{len(proc.stdout)}"


def collect_case(case_name: str, variant_db: Path) -> dict[str, Any]:
    adb("logcat", "-c")
    install_db(variant_db)
    open_today_page()
    scroll_to_top()

    top_png = SCREENSHOT_DIR / f"{case_name}_top.png"
    top_xml = SCREENSHOT_DIR / f"{case_name}_top.xml"
    capture_screenshot(top_png)
    dump_ui(top_xml)

    scroll_to_lower()
    lower_png = SCREENSHOT_DIR / f"{case_name}_lower.png"
    lower_xml = SCREENSHOT_DIR / f"{case_name}_lower.xml"
    capture_screenshot(lower_png)
    dump_ui(lower_xml)

    query_db, overview = export_case_db(case_name)
    logcat_path = CASE_DIR / f"{case_name}_logcat.txt"
    save_text(logcat_path, adb("logcat", "-d", "-v", "time").stdout)

    top_texts = collect_texts(top_xml)
    lower_texts = collect_texts(lower_xml)
    return {
        "case": case_name,
        "top_texts": top_texts,
        "lower_texts": lower_texts,
        "db_overview": overview,
        "query_db": str(query_db),
        "logcat": str(logcat_path),
    }


def capture_api_files() -> None:
    session = adb("shell", "run-as", PACKAGE, "cat", "shared_prefs/cloud_session.xml").stdout
    save_text(API_DIR / "cloud_session.xml", session)


def main() -> None:
    ensure_dirs()
    (CASE_DIR / "run.log").write_text("", encoding="utf-8")
    log("case start: 02-today-recovery-physiology")
    backup_prefs()
    set_demo_bootstrap_state_v2()
    capture_api_files()

    original_main, original_query = backup_live_db("original")
    variant_complete = TMP_DIR / "variant_complete.db"
    variant_no_sleep = TMP_DIR / "variant_no_sleep.db"
    variant_fallback = TMP_DIR / "variant_fallback.db"

    make_variant(original_query, variant_complete, "complete")
    make_variant(original_query, variant_no_sleep, "physiology_without_sleep")
    make_variant(original_query, variant_fallback, "fallback_no_latest")

    screenrecord_status = attempt_screenrecord(CASE_DIR / "screenrecord.mp4")
    log(f"screenrecord_status={screenrecord_status}")

    results = {
        "complete": collect_case("case1_complete", variant_complete),
        "physiology_without_sleep": collect_case("case2_physiology_without_sleep", variant_no_sleep),
        "fallback_no_latest": collect_case("case3_fallback_no_latest", variant_fallback),
    }

    # Restore original database and prefs.
    install_db(original_query)
    restore_prefs()

    save_text(CASE_DIR / "current_ui.xml", json.dumps(results, ensure_ascii=False, indent=2))
    save_text(CASE_DIR / "logcat.txt", "\n\n".join(
        [
            f"===== {name} =====\n{Path(data['logcat']).read_text(encoding='utf-8', errors='ignore')}"
            for name, data in results.items()
        ]
    ))
    save_text(CASE_DIR / "case-summary.json", json.dumps({"screenrecord": screenrecord_status, "results": results}, ensure_ascii=False, indent=2))
    log("SUCCESS")


if __name__ == "__main__":
    main()
