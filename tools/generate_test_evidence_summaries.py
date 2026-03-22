from __future__ import annotations

import csv
import json
from pathlib import Path


ROOT = Path(r"D:\newstart\test-evidence")
CASES_DIR = ROOT / "cases"
SUMMARY_DIR = ROOT / "summary"


def apath(*parts: str) -> str:
    return str(Path(*parts))


CASES = [
    {
        "id": "TC-ANDROID-BUILD-001",
        "title": "Android 调试构建验证",
        "objective": "验证 :app-shell 调试包在当前源码状态下可以成功编译输出 APK。",
        "prereq": [
            "仓库位于 D:\\newstart",
            "已安装 JDK / Android SDK / Gradle Wrapper 依赖完整",
        ],
        "steps": [
            "在仓库根目录执行 cmd /c gradlew.bat :app-shell:assembleDebug",
            "记录完整 Gradle 输出与耗时",
            "检查调试 APK 是否生成",
        ],
        "result": "构建成功，:app-shell 生成 debug APK，耗时约 80.41 秒。",
        "status": "PASS_WITH_WARNING",
        "defects": [
            "构建过程中出现 Kotlin/Java 弃用与标签作用域警告，但未阻断输出。"
        ],
        "sentence": "Android 主运行模块 :app-shell 在当前代码基线上可稳定完成调试构建，并生成可安装的 debug APK。",
        "evidence": [
            apath(CASES_DIR, "TC-ANDROID-BUILD-001", "gradle_assemble_debug.log"),
            apath(CASES_DIR, "TC-ANDROID-BUILD-001", "timing.txt"),
            apath(CASES_DIR, "TC-ANDROID-BUILD-001", "apk_info.txt"),
            apath(CASES_DIR, "TC-ANDROID-BUILD-001", "evidence_preview.png"),
        ],
        "metrics": {"duration_seconds": 80.41},
    },
    {
        "id": "TC-CLOUD-BUILD-001",
        "title": "cloud-next 构建验证",
        "objective": "验证 cloud-next 云端与后台工程在当前源码状态下可以完成生产构建。",
        "prereq": ["已安装 Node.js / npm", "cloud-next 依赖已安装"],
        "steps": ["进入 cloud-next 目录", "执行 npm run build", "记录构建输出与路由清单"],
        "result": "构建成功，Next.js 构建完成，输出后台页面与 API 路由清单，耗时约 15.63 秒。",
        "status": "PASS",
        "defects": [],
        "sentence": "cloud-next 在当前配置下能够完成生产构建，后台页面与 API 路由可正常产出。",
        "evidence": [
            apath(CASES_DIR, "TC-CLOUD-BUILD-001", "cloud_next_build.log"),
            apath(CASES_DIR, "TC-CLOUD-BUILD-001", "timing.txt"),
            apath(CASES_DIR, "TC-CLOUD-BUILD-001", "evidence_preview.png"),
        ],
        "metrics": {"duration_seconds": 15.63},
    },
    {
        "id": "TC-ANDROID-INSTALL-001",
        "title": "Android 真机安装验证",
        "objective": "验证 :app-shell 调试包可以安装到已连接真机。",
        "prereq": ["真机已通过 adb 连接", "调试 APK 已构建完成"],
        "steps": [
            "执行 cmd /c gradlew.bat :app-shell:installDebug",
            "记录安装输出和耗时",
            "确认目标设备安装成功",
        ],
        "result": "安装成功，调试包已安装到 OPD2405 真机，耗时约 43.92 秒。",
        "status": "PASS",
        "defects": [],
        "sentence": "调试包可以通过标准 Gradle 安装流程稳定部署到真实 Android 设备。",
        "evidence": [
            apath(CASES_DIR, "TC-ANDROID-INSTALL-001", "gradle_install_debug.log"),
            apath(CASES_DIR, "TC-ANDROID-INSTALL-001", "timing.txt"),
            apath(CASES_DIR, "TC-ANDROID-INSTALL-001", "evidence_preview.png"),
        ],
        "metrics": {"duration_seconds": 43.92},
    },
    {
        "id": "TC-ANDROID-LAUNCH-001",
        "title": "Android 真机启动烟雾验证",
        "objective": "验证应用在真机上可冷启动进入 MainActivity，并保留页面和日志证据。",
        "prereq": ["com.example.newstart 已安装到真机", "adb 可访问设备"],
        "steps": [
            "清空 logcat",
            "强制停止应用",
            "执行 adb shell am start -W -n com.example.newstart/.MainActivity",
            "抓取页面截图、窗口树和 logcat 尾部",
        ],
        "result": "应用冷启动成功，MainActivity 正常拉起，TotalTime=2382ms，页面截图与日志均已采集。",
        "status": "PASS_WITH_WARNING",
        "defects": [
            "uiautomator dump 返回 “could not get idle state”，但 XML 文件仍成功导出，不影响页面取证。"
        ],
        "sentence": "应用在真机上可完成冷启动并进入主页面，启动后能够继续请求云端推荐相关接口。",
        "evidence": [
            apath(CASES_DIR, "TC-ANDROID-LAUNCH-001", "am_start_w.txt"),
            apath(CASES_DIR, "TC-ANDROID-LAUNCH-001", "launch_screen.png"),
            apath(CASES_DIR, "TC-ANDROID-LAUNCH-001", "window_dump.xml"),
            apath(CASES_DIR, "TC-ANDROID-LAUNCH-001", "logcat_tail.txt"),
        ],
        "metrics": {"total_time_ms": 2382, "wait_time_ms": 2394},
    },
    {
        "id": "TC-ANDROID-PERF-001",
        "title": "Android 冷启动性能采样",
        "objective": "对应用冷启动性能进行重复采样，形成可复用的平均/最小/最大统计。",
        "prereq": ["应用已安装到真机", "设备保持可交互状态"],
        "steps": [
            "连续 5 次执行 force-stop + am start -W",
            "记录每次 TotalTime / WaitTime",
            "统计平均值、最小值、最大值",
        ],
        "result": "5 次采样完成，TotalTime 平均 1779.40ms，最小 1678ms，最大 2058ms；WaitTime 平均 1785.80ms。",
        "status": "PASS",
        "defects": [],
        "sentence": "应用冷启动在 5 次真机采样中的 TotalTime 平均约 1.78 秒，整体波动控制在约 0.38 秒范围内。",
        "evidence": [
            apath(CASES_DIR, "TC-ANDROID-PERF-001", "cold_start_runs.txt"),
            apath(CASES_DIR, "TC-ANDROID-PERF-001", "metrics_summary.txt"),
            apath(CASES_DIR, "TC-ANDROID-PERF-001", "evidence_preview.png"),
        ],
        "metrics": {
            "runs": 5,
            "total_avg_ms": 1779.40,
            "total_min_ms": 1678,
            "total_max_ms": 2058,
            "wait_avg_ms": 1785.80,
            "wait_min_ms": 1683,
            "wait_max_ms": 2062,
        },
    },
    {
        "id": "TC-CLOUD-ADMIN-LOGIN-001",
        "title": "云端后台注册登录与主页面取证",
        "objective": "验证云端后台可通过新注册账号获得管理员访问能力，并正常访问 dashboard/story/patients/reports。",
        "prereq": [
            "生产站点 https://cloud.changgengring.cyou 可访问",
            "注册接口可用",
        ],
        "steps": [
            "调用 /api/auth/register 创建临时测试账号",
            "访问后台 dashboard",
            "依次抓取 story、patients?demoOnly=1、reports 页面截图与快照",
            "保存网络请求与健康检查响应",
        ],
        "result": "注册接口返回 200，临时账号可直接进入后台；dashboard、story、patients、reports 页面均成功打开并完成截图。",
        "status": "PASS_WITH_WARNING",
        "defects": ["浏览器控制台出现 favicon.ico 404，不影响后台主流程和页面加载。"],
        "sentence": "云端后台当前允许新注册账号直接进入管理员工作台，驾驶舱、闭环故事、患者池和报告页均可正常访问。",
        "evidence": [
            apath(CASES_DIR, "TC-CLOUD-ADMIN-LOGIN-001", "register_request.json"),
            apath(CASES_DIR, "TC-CLOUD-ADMIN-LOGIN-001", "register_response.json"),
            apath(CASES_DIR, "TC-CLOUD-ADMIN-LOGIN-001", "health_response.json"),
            apath(CASES_DIR, "TC-CLOUD-ADMIN-LOGIN-001", "dashboard.png"),
            apath(CASES_DIR, "TC-CLOUD-ADMIN-LOGIN-001", "dashboard_snapshot.md"),
            apath(CASES_DIR, "TC-CLOUD-ADMIN-LOGIN-001", "story.png"),
            apath(CASES_DIR, "TC-CLOUD-ADMIN-LOGIN-001", "story_snapshot.md"),
            apath(CASES_DIR, "TC-CLOUD-ADMIN-LOGIN-001", "patients.png"),
            apath(CASES_DIR, "TC-CLOUD-ADMIN-LOGIN-001", "patients_snapshot.md"),
            apath(CASES_DIR, "TC-CLOUD-ADMIN-LOGIN-001", "reports.png"),
            apath(CASES_DIR, "TC-CLOUD-ADMIN-LOGIN-001", "reports_snapshot.md"),
            apath(CASES_DIR, "TC-CLOUD-ADMIN-LOGIN-001", "network_requests.txt"),
        ],
        "metrics": {"register_status_code": 200, "health_status_code": 200},
    },
    {
        "id": "TC-ANDROID-DB-EXPORT-001",
        "title": "Android 本地数据库导出取证",
        "objective": "验证调试包可通过 run-as 导出本地 Room 数据库文件，形成数据库取证基础。",
        "prereq": ["debuggable 调试包已安装", "adb run-as 可访问应用私有目录"],
        "steps": [
            "使用 adb shell run-as 列出数据库目录",
            "导出 sleep_health_database 主文件及 wal/shm",
            "导出 package dumpsys 以确认 debuggable 状态",
        ],
        "result": "数据库目录和主文件、wal、shm 均成功导出；可确认当前调试包为 debuggable。",
        "status": "PASS_WITH_WARNING",
        "defects": [
            "导出的主数据库文件处于 WAL 模式，直接用本地 sqlite3 打开失败，后续若需表级分析应在一致性快照策略下处理。"
        ],
        "sentence": "调试包支持通过 run-as 导出本地数据库物证，但当前 WAL 模式下需要额外一致性处理才能直接做表级解析。",
        "evidence": [
            apath(CASES_DIR, "TC-ANDROID-DB-EXPORT-001", "databases_ls.txt"),
            apath(CASES_DIR, "TC-ANDROID-DB-EXPORT-001", "sleep_health_database.db"),
            apath(CASES_DIR, "TC-ANDROID-DB-EXPORT-001", "sleep_health_database.db-wal"),
            apath(CASES_DIR, "TC-ANDROID-DB-EXPORT-001", "sleep_health_database.db-shm"),
            apath(CASES_DIR, "TC-ANDROID-DB-EXPORT-001", "package_dump.txt"),
            apath(CASES_DIR, "TC-ANDROID-DB-EXPORT-001", "evidence_preview.png"),
        ],
        "metrics": {"db_main_bytes": 415998, "db_wal_bytes": 563886, "db_shm_bytes": 32847},
    },
    {
        "id": "TC-ANDROID-NAV-001",
        "title": "Android 底部五导航页面可达性烟雾测试",
        "objective": "验证今日、医生、趋势、设备、我的五个一级导航位在真机上可被实际点击并完成页面截图。",
        "prereq": ["应用已安装并可启动", "真机分辨率固定为 2000x2800"],
        "steps": [
            "冷启动应用",
            "基于真机底部导航槽位坐标依次点击 5 个一级导航位",
            "每次点击后等待 3 秒并截图",
        ],
        "result": "五个导航槽位均完成点击与截图取证，底部导航的页面可达性证据已生成。",
        "status": "PASS_WITH_WARNING",
        "defects": [
            "UIAutomator 导出的中文文本存在编码问题，首轮基于文本查找导航标签失败，后改用固定坐标完成烟雾取证。"
        ],
        "sentence": "Android 底部五导航在真机上具备基本可达性，当前页面切换证据可通过逐页截图复核。",
        "evidence": [
            apath(CASES_DIR, "TC-ANDROID-NAV-001", "am_start_w.txt"),
            apath(CASES_DIR, "TC-ANDROID-NAV-001", "am_start_w_recheck.txt"),
            apath(CASES_DIR, "TC-ANDROID-NAV-001", "nav_results.csv"),
            apath(CASES_DIR, "TC-ANDROID-NAV-001", "01_today.png"),
            apath(CASES_DIR, "TC-ANDROID-NAV-001", "02_doctor.png"),
            apath(CASES_DIR, "TC-ANDROID-NAV-001", "03_trend.png"),
            apath(CASES_DIR, "TC-ANDROID-NAV-001", "04_device.png"),
            apath(CASES_DIR, "TC-ANDROID-NAV-001", "05_profile.png"),
            apath(CASES_DIR, "TC-ANDROID-NAV-001", "logcat_tail.txt"),
        ],
        "metrics": {"screenshots": 5},
    },
]


def write_case_summary(case: dict) -> None:
    case_dir = CASES_DIR / case["id"]
    lines = [
        f"# {case['id']}",
        "",
        f"- 用例编号：`{case['id']}`",
        f"- 测试目标：{case['objective']}",
        f"- 前提条件：{'；'.join(case['prereq'])}",
        "- 实际执行步骤：",
    ]
    for index, step in enumerate(case["steps"], start=1):
        lines.append(f"  {index}. {step}")
    lines.extend(
        [
            f"- 实际结果：{case['result']}",
            "- 证据文件路径：",
        ]
    )
    for evidence in case["evidence"]:
        lines.append(f"  - `{evidence}`")
    defects = "；".join(case["defects"]) if case["defects"] else "未发现阻断性缺陷。"
    lines.extend(
        [
            f"- 缺陷/异常：{defects}",
            f"- 可直接写入“测试结果分析”的一句话：{case['sentence']}",
            "",
        ]
    )
    (case_dir / "summary.md").write_text("\n".join(lines), encoding="utf-8-sig")
    result_json = {
        "case_id": case["id"],
        "title": case["title"],
        "status": case["status"],
        "objective": case["objective"],
        "result": case["result"],
        "metrics": case["metrics"],
        "defects": case["defects"],
        "report_sentence": case["sentence"],
        "evidence": case["evidence"],
    }
    (case_dir / "result.json").write_text(
        json.dumps(result_json, ensure_ascii=False, indent=2), encoding="utf-8"
    )


def write_case_index() -> None:
    with (SUMMARY_DIR / "test_case_index.csv").open(
        "w", newline="", encoding="utf-8-sig"
    ) as file:
        writer = csv.writer(file)
        writer.writerow(["case_id", "title", "status", "objective", "summary_path"])
        for case in CASES:
            writer.writerow(
                [
                    case["id"],
                    case["title"],
                    case["status"],
                    case["objective"],
                    str(CASES_DIR / case["id"] / "summary.md"),
                ]
            )


def write_defects() -> None:
    lines = ["# 缺陷与异常清单", ""]
    found = False
    for case in CASES:
        if not case["defects"]:
            continue
        found = True
        lines.append(f"## {case['id']} {case['title']}")
        for defect in case["defects"]:
            lines.append(f"- {defect}")
        lines.append("")
    if not found:
        lines.append("- 本轮自动化与取证过程中未发现需要单独登记的缺陷。")
    (SUMMARY_DIR / "defect_list.md").write_text(
        "\n".join(lines), encoding="utf-8-sig"
    )


def write_evidence_index() -> None:
    lines = ["# 证据索引", ""]
    for case in CASES:
        lines.append(f"## {case['id']} {case['title']}")
        lines.append(f"- 状态：`{case['status']}`")
        for evidence in case["evidence"]:
            lines.append(f"- `{evidence}`")
        lines.append("")
    (SUMMARY_DIR / "evidence_index.md").write_text(
        "\n".join(lines), encoding="utf-8-sig"
    )


def write_chapter_notes() -> None:
    lines = [
        "# 可直接用于测试文档的整理笔记",
        "",
        "## 测试结果分析",
        "",
    ]
    for case in CASES:
        lines.append(f"- {case['sentence']}")
    lines.extend(
        [
            "",
            "## 综合分析及建议",
            "",
            "- 当前 Android 主模块 :app-shell 与 cloud-next 均可完成构建，说明端云两侧主工程已具备较稳定的持续交付基础。",
            "- 真机安装、启动、底部导航切换和后台主页面访问均已形成截图和日志证据，能够支撑比赛文档中的可用性与完整流程说明。",
            "- 建议后续补一轮“演示账号登录 Android -> 自动 bootstrap -> 医检报告/问诊/干预闭环”的专项取证，以进一步增强业务闭环证据。",
            "- 本地数据库已可通过 run-as 导出，但 WAL 模式下一致性快照仍需完善，后续如需表级统计建议加入导出前 checkpoint 或应用停写策略。",
            "- 后台控制台存在 favicon 404 与页面辅助取证中的少量非阻断性异常，不影响主流程，但可在正式答辩前做体验级清理。",
            "",
            "## 测试经验总结",
            "",
            "- 对 Android 构建、安装和冷启动性能，应优先保留 Gradle 日志、adb 启动时间和真机截图三类证据，便于后续交叉佐证。",
            "- 对后台页面，建议同时保留注册/登录接口返回、页面截图和 DOM 快照，避免只有图片没有可检索的文本证据。",
            "- 对数据库取证，单独导出主数据库文件并不足以支撑 SQLite 解析，启用 WAL 的应用必须同步导出 wal/shm 或采用一致性快照方案。",
            "- 在中文 UI 自动化中，UIAutomator 文本导出可能受编码影响；出现该类问题时，应及时切换为坐标点击或更稳定的页面结构定位方案。",
        ]
    )
    (SUMMARY_DIR / "chapter_ready_notes.md").write_text(
        "\n".join(lines), encoding="utf-8-sig"
    )


def main() -> None:
    SUMMARY_DIR.mkdir(parents=True, exist_ok=True)
    for case in CASES:
        write_case_summary(case)
    write_case_index()
    write_defects()
    write_evidence_index()
    write_chapter_notes()
    print("summaries-generated")


if __name__ == "__main__":
    main()
