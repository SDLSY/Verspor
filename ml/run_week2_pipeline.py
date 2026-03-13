from __future__ import annotations

import argparse
import json
import subprocess
from pathlib import Path


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run Week2 ML pipeline end-to-end")
    _ = parser.add_argument("--public_dir", type=str, default="./data/public_labeled")
    _ = parser.add_argument("--ring_dir", type=str, default="./data/ring_unlabeled")
    _ = parser.add_argument("--manifests_dir", type=str, default="./data/manifests")
    _ = parser.add_argument(
        "--baseline_output", type=str, default="./artifacts/week2/baseline"
    )
    _ = parser.add_argument(
        "--pseudo_output", type=str, default="./artifacts/week2/pseudo_labels"
    )
    _ = parser.add_argument("--confidence_threshold", type=float, default=0.8)
    _ = parser.add_argument("--seed", type=int, default=42)
    return parser.parse_args()


def run_step(command: list[str], cwd: Path) -> None:
    print("$", " ".join(command))
    result = subprocess.run(command, cwd=str(cwd), check=False)
    if result.returncode != 0:
        raise RuntimeError(f"command failed ({result.returncode}): {' '.join(command)}")


def load_json(path: Path) -> dict[str, object]:
    if not path.exists():
        return {}
    with path.open("r", encoding="utf-8") as handle:
        data = json.load(handle)
    if isinstance(data, dict):
        return data
    return {}


def derive_class_thresholds(
    baseline_report: dict[str, object], base_threshold: float
) -> dict[str, float]:
    metrics = baseline_report.get("metrics")
    if not isinstance(metrics, dict):
        return {}
    test_metrics = metrics.get("test")
    if not isinstance(test_metrics, dict):
        return {}
    report = test_metrics.get("report")
    if not isinstance(report, dict):
        return {}

    result: dict[str, float] = {}
    for stage in ["N1", "REM", "N3", "N2", "WAKE"]:
        details = report.get(stage)
        if not isinstance(details, dict):
            continue
        recall_value = details.get("recall")
        if not isinstance(recall_value, (int, float)):
            continue
        recall = float(recall_value)

        threshold = float(base_threshold)
        if stage in {"N1", "REM"}:
            threshold -= 0.12
        if recall < 0.2:
            threshold -= 0.12
        elif recall < 0.35:
            threshold -= 0.08
        result[stage] = round(max(0.5, min(0.95, threshold)), 3)

    return result


def main() -> None:
    args = parse_args()
    root = Path(__file__).resolve().parent.parent

    manifests_dir = Path(args.manifests_dir)
    baseline_output = Path(args.baseline_output)
    pseudo_output = Path(args.pseudo_output)

    run_step(
        [
            "python",
            "ml/build_dataset_manifest.py",
            "--public_dir",
            str(args.public_dir),
            "--ring_dir",
            str(args.ring_dir),
            "--output_dir",
            str(manifests_dir),
            "--seed",
            str(args.seed),
        ],
        root,
    )

    baseline_report = load_json(root / baseline_output / "baseline_report.json")
    class_thresholds = derive_class_thresholds(
        baseline_report=baseline_report,
        base_threshold=float(args.confidence_threshold),
    )

    threshold_path = root / pseudo_output / "dynamic_class_thresholds.json"
    threshold_path.parent.mkdir(parents=True, exist_ok=True)
    with threshold_path.open("w", encoding="utf-8") as handle:
        json.dump(class_thresholds, handle, indent=2, ensure_ascii=False)

    run_step(
        [
            "python",
            "ml/train_baseline.py",
            "--manifests_dir",
            str(manifests_dir),
            "--output_dir",
            str(baseline_output),
            "--random_seed",
            str(args.seed),
        ],
        root,
    )

    run_step(
        [
            "python",
            "ml/generate_pseudo_labels.py",
            "--manifests_dir",
            str(manifests_dir),
            "--output_dir",
            str(pseudo_output),
            "--confidence_threshold",
            str(args.confidence_threshold),
            "--class_thresholds_json",
            str(threshold_path),
            "--random_seed",
            str(args.seed),
        ],
        root,
    )

    summary = {
        "manifest_summary": load_json(root / manifests_dir / "summary.json"),
        "baseline_report": baseline_report,
        "dynamic_thresholds": class_thresholds,
        "pseudo_report": load_json(root / pseudo_output / "pseudo_label_report.json"),
    }

    output_path = root / "artifacts" / "week2" / "pipeline_run_summary.json"
    output_path.parent.mkdir(parents=True, exist_ok=True)
    with output_path.open("w", encoding="utf-8") as handle:
        json.dump(summary, handle, indent=2, ensure_ascii=False)

    print("Week2 pipeline completed")
    print(f"summary: {output_path}")


if __name__ == "__main__":
    main()
