from __future__ import annotations

import argparse
import json
import subprocess
import sys
from pathlib import Path
from typing import Any

import numpy as np


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Run multi-seed soft-voting ensemble stability retest"
    )
    _ = parser.add_argument("--manifests_dir", type=str, default="data/manifests")
    _ = parser.add_argument(
        "--output_dir", type=str, default="artifacts/week2/ensemble_multiseed"
    )
    _ = parser.add_argument("--seeds", type=str, default="7,13,21,42,84")
    _ = parser.add_argument("--epochs", type=int, default=1)
    _ = parser.add_argument("--batch_size", type=int, default=128)
    _ = parser.add_argument("--d_model", type=int, default=64)
    _ = parser.add_argument("--num_heads", type=int, default=4)
    _ = parser.add_argument("--num_layers", type=int, default=2)
    _ = parser.add_argument("--dropout", type=float, default=0.15)
    _ = parser.add_argument("--learning_rate", type=float, default=5e-4)
    _ = parser.add_argument("--weight_decay", type=float, default=1e-3)
    _ = parser.add_argument("--transformer_loss_type", choices=["ce", "focal"], default="focal")
    _ = parser.add_argument("--class_weight_power", type=float, default=0.35)
    _ = parser.add_argument("--class_weight_min", type=float, default=0.6)
    _ = parser.add_argument("--class_weight_max", type=float, default=1.8)
    _ = parser.add_argument("--focal_gamma", type=float, default=1.5)
    _ = parser.add_argument("--optimize_metric", choices=["accuracy", "macro_f1"], default="macro_f1")
    _ = parser.add_argument("--disable_class_weights", action="store_true")
    return parser.parse_args()


def run_one_seed(root: Path, args: argparse.Namespace, seed: int, out_dir: Path) -> dict[str, Any]:
    cmd = [
        sys.executable,
        str(root / "ml" / "evaluate_soft_voting_ensemble.py"),
        "--manifests_dir",
        str(args.manifests_dir),
        "--output_dir",
        str(out_dir),
        "--epochs",
        str(args.epochs),
        "--batch_size",
        str(args.batch_size),
        "--d_model",
        str(args.d_model),
        "--num_heads",
        str(args.num_heads),
        "--num_layers",
        str(args.num_layers),
        "--dropout",
        str(args.dropout),
        "--learning_rate",
        str(args.learning_rate),
        "--weight_decay",
        str(args.weight_decay),
        "--transformer_loss_type",
        str(args.transformer_loss_type),
        "--class_weight_power",
        str(args.class_weight_power),
        "--class_weight_min",
        str(args.class_weight_min),
        "--class_weight_max",
        str(args.class_weight_max),
        "--focal_gamma",
        str(args.focal_gamma),
        "--optimize_metric",
        str(args.optimize_metric),
        "--random_seed",
        str(seed),
    ]
    if bool(args.disable_class_weights):
        cmd.append("--disable_class_weights")

    subprocess.run(cmd, check=True, cwd=str(root))

    report_path = out_dir / "ensemble_report.json"
    report = json.loads(report_path.read_text(encoding="utf-8"))
    test = report["metrics"]["test"]
    return {
        "seed": int(seed),
        "accuracy": float(test["accuracy"]),
        "macro_f1": float(test["macro_f1"]),
        "best_weight_rf": float(report["model"]["best_weight_rf"]),
        "best_weight_transformer": float(report["model"]["best_weight_transformer"]),
        "output_dir": str(out_dir),
    }


def mean_std(values: list[float]) -> dict[str, float]:
    arr = np.asarray(values, dtype=np.float64)
    return {
        "mean": float(arr.mean()) if len(arr) else 0.0,
        "std": float(arr.std(ddof=0)) if len(arr) else 0.0,
        "min": float(arr.min()) if len(arr) else 0.0,
        "max": float(arr.max()) if len(arr) else 0.0,
    }


def main() -> None:
    args = parse_args()
    root = Path(__file__).resolve().parents[1]
    base_out = root / str(args.output_dir)
    base_out.mkdir(parents=True, exist_ok=True)

    seeds = [int(item.strip()) for item in str(args.seeds).split(",") if item.strip()]
    results: list[dict[str, Any]] = []

    for seed in seeds:
        run_dir = base_out / f"seed_{seed}"
        run_dir.mkdir(parents=True, exist_ok=True)
        result = run_one_seed(root, args, seed, run_dir)
        results.append(result)

    accuracies = [item["accuracy"] for item in results]
    macro_f1s = [item["macro_f1"] for item in results]
    rf_weights = [item["best_weight_rf"] for item in results]

    summary = {
        "status": "ok",
        "config": {
            "seeds": seeds,
            "epochs": int(args.epochs),
            "batch_size": int(args.batch_size),
            "transformer_loss_type": str(args.transformer_loss_type),
            "optimize_metric": str(args.optimize_metric),
            "disable_class_weights": bool(args.disable_class_weights),
        },
        "aggregate": {
            "accuracy": mean_std(accuracies),
            "macro_f1": mean_std(macro_f1s),
            "best_weight_rf": mean_std(rf_weights),
        },
        "runs": results,
    }

    (base_out / "multiseed_summary.json").write_text(
        json.dumps(summary, indent=2, ensure_ascii=False), encoding="utf-8"
    )

    print("Multi-seed ensemble retest completed")
    print(json.dumps(summary["aggregate"], indent=2, ensure_ascii=False))


if __name__ == "__main__":
    main()
