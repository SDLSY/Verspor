from __future__ import annotations

import json
from pathlib import Path
from typing import Any

import matplotlib.pyplot as plt

ROOT = Path(__file__).resolve().parents[1]
OUT_DIR = ROOT / "artifacts" / "week2" / "visualization"
OUT_DIR.mkdir(parents=True, exist_ok=True)

REPORTS = {
    "semi_rf": ROOT
    / "artifacts"
    / "week2"
    / "semi_supervised"
    / "semi_supervised_report.json",
    "transformer_acc": ROOT
    / "artifacts"
    / "week2"
    / "transformer_vis_eval_acc"
    / "transformer_report.json",
    "transformer_focal": ROOT
    / "artifacts"
    / "week2"
    / "transformer_vis_eval_focal"
    / "transformer_report.json",
}

STAGES = ["WAKE", "N1", "N2", "N3", "REM"]


def load_test_metrics(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))["metrics"]["test"]


def main() -> None:
    metrics = {name: load_test_metrics(path) for name, path in REPORTS.items()}

    names = list(metrics.keys())
    accuracies = [metrics[name]["accuracy"] for name in names]
    macro_f1s = [metrics[name]["macro_f1"] for name in names]

    plt.figure(figsize=(8, 4.5))
    plt.bar(names, accuracies, color=["#1f77b4", "#ff7f0e", "#2ca02c"])
    plt.ylim(0.0, 1.0)
    plt.ylabel("Accuracy")
    plt.title("Test Accuracy Comparison")
    for i, value in enumerate(accuracies):
        plt.text(i, value + 0.01, f"{value:.4f}", ha="center", va="bottom", fontsize=9)
    plt.tight_layout()
    plt.savefig(OUT_DIR / "transformer-test-accuracy-comparison.svg", format="svg")
    plt.close()

    plt.figure(figsize=(8, 4.5))
    plt.bar(names, macro_f1s, color=["#1f77b4", "#ff7f0e", "#2ca02c"])
    plt.ylim(0.0, 1.0)
    plt.ylabel("Macro F1")
    plt.title("Test Macro-F1 Comparison")
    for i, value in enumerate(macro_f1s):
        plt.text(i, value + 0.01, f"{value:.4f}", ha="center", va="bottom", fontsize=9)
    plt.tight_layout()
    plt.savefig(OUT_DIR / "transformer-test-macrof1-comparison.svg", format="svg")
    plt.close()

    x = range(len(STAGES))
    width = 0.24
    plt.figure(figsize=(10, 4.8))
    for idx, name in enumerate(names):
        stage_f1 = [metrics[name]["report"][stage]["f1-score"] for stage in STAGES]
        offsets = [pos + (idx - 1) * width for pos in x]
        plt.bar(offsets, stage_f1, width=width, label=name)

    plt.xticks(list(x), STAGES)
    plt.ylim(0.0, 1.0)
    plt.ylabel("F1")
    plt.title("Test F1 by Stage")
    plt.legend()
    plt.tight_layout()
    plt.savefig(OUT_DIR / "transformer-test-f1-by-stage-comparison.svg", format="svg")
    plt.close()

    report_md = OUT_DIR / "transformer-visualization-report.md"
    lines = [
        "# Transformer Test & Visualization Report",
        "",
        "## Models Compared",
        "",
        "- `semi_rf` (reference production model)",
        "- `transformer_acc` (accuracy-priority CE run)",
        "- `transformer_focal` (macro-F1-priority focal run)",
        "",
        "## Test Metrics",
        "",
        "| Model | Accuracy | Macro F1 |",
        "|---|---:|---:|",
    ]
    for name in names:
        lines.append(
            f"| {name} | {metrics[name]['accuracy']:.6f} | {metrics[name]['macro_f1']:.6f} |"
        )

    lines.extend(
        [
            "",
            "## Visualizations",
            "",
            "- `artifacts/week2/visualization/transformer-test-accuracy-comparison.svg`",
            "- `artifacts/week2/visualization/transformer-test-macrof1-comparison.svg`",
            "- `artifacts/week2/visualization/transformer-test-f1-by-stage-comparison.svg`",
        ]
    )
    report_md.write_text("\n".join(lines) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
