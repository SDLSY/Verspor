from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any

import numpy as np
from numpy.typing import NDArray
from sklearn.ensemble import RandomForestClassifier

from train_baseline import (
    STAGE_ORDER,
    STAGE_TO_ID,
    build_context_vectors,
    evaluate_model,
    feature_vector,
    load_matrix_from_manifest,
    normalize_stage,
    oversample_minority_classes,
    read_any_table,
    read_jsonl,
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Train semi-supervised sleep-stage model with pseudo-label weighting"
    )
    _ = parser.add_argument("--manifests_dir", type=str, default="./data/manifests")
    _ = parser.add_argument(
        "--train_manifest", type=str, default="train_manifest.jsonl"
    )
    _ = parser.add_argument("--val_manifest", type=str, default="val_manifest.jsonl")
    _ = parser.add_argument("--test_manifest", type=str, default="test_manifest.jsonl")
    _ = parser.add_argument(
        "--pseudo_labels_path",
        type=str,
        default="./artifacts/week2/pseudo_labels/pseudo_labels.jsonl",
    )
    _ = parser.add_argument("--pseudo_weight", type=float, default=0.55)
    _ = parser.add_argument("--pseudo_min_confidence", type=float, default=0.55)
    _ = parser.add_argument(
        "--output_dir", type=str, default="./artifacts/week2/semi_supervised"
    )
    _ = parser.add_argument("--random_seed", type=int, default=42)
    return parser.parse_args()


def load_pseudo_weighted_samples(
    pseudo_labels_path: Path,
    pseudo_weight: float,
    pseudo_min_confidence: float,
) -> tuple[
    NDArray[np.float32],
    NDArray[np.int64],
    NDArray[np.float32],
    dict[str, int],
]:
    records = read_jsonl(pseudo_labels_path)
    grouped: dict[str, list[dict[str, Any]]] = {}
    for item in records:
        path = str(item.get("path", "")).strip()
        if not path:
            continue
        grouped.setdefault(path, []).append(item)

    vectors: list[NDArray[np.float32]] = []
    labels: list[int] = []
    weights: list[float] = []
    stage_counts = {stage: 0 for stage in STAGE_ORDER}

    stage_weight_boost = {
        "WAKE": 1.0,
        "N1": 1.8,
        "N2": 1.0,
        "N3": 1.25,
        "REM": 1.6,
    }

    for path_str, rows in grouped.items():
        path = Path(path_str)
        if not path.exists():
            continue
        epochs = read_any_table(path)
        if not epochs:
            continue
        epoch_vectors = [feature_vector(epoch) for epoch in epochs]
        context_vectors = build_context_vectors(epoch_vectors, context_size=1)

        for row in rows:
            stage = normalize_stage(row.get("sleep_stage_5"))
            if stage is None:
                continue
            index_value = row.get("epoch_index")
            if not isinstance(index_value, (int, np.integer)):
                continue
            epoch_index = int(index_value)
            if epoch_index < 0 or epoch_index >= len(context_vectors):
                continue

            confidence_value = row.get("confidence")
            confidence = 0.0
            if isinstance(confidence_value, (int, float, np.integer, np.floating)):
                confidence = float(confidence_value)
            elif isinstance(confidence_value, str):
                text = confidence_value.strip()
                if text:
                    try:
                        confidence = float(text)
                    except ValueError:
                        confidence = 0.0
            if confidence < pseudo_min_confidence:
                continue

            stage_name = STAGE_ORDER[stage]
            base_weight = max(0.05, min(1.0, confidence * pseudo_weight))
            boosted = max(0.05, min(1.0, base_weight * stage_weight_boost[stage_name]))

            vectors.append(context_vectors[epoch_index])
            labels.append(stage)
            weights.append(float(boosted))
            stage_counts[stage_name] += 1

    if not vectors:
        return (
            np.zeros((0, 51), dtype=np.float32),
            np.zeros((0,), dtype=np.int64),
            np.zeros((0,), dtype=np.float32),
            stage_counts,
        )

    return (
        np.vstack(vectors).astype(np.float32),
        np.asarray(labels, dtype=np.int64),
        np.asarray(weights, dtype=np.float32),
        stage_counts,
    )


def main() -> None:
    args = parse_args()
    manifests_dir = Path(str(args.manifests_dir))
    output_dir = Path(str(args.output_dir))
    output_dir.mkdir(parents=True, exist_ok=True)

    train_manifest = manifests_dir / str(args.train_manifest)
    val_manifest = manifests_dir / str(args.val_manifest)
    test_manifest = manifests_dir / str(args.test_manifest)
    pseudo_labels_path = Path(str(args.pseudo_labels_path))

    x_train, y_train = load_matrix_from_manifest(train_manifest)
    x_val, y_val = load_matrix_from_manifest(val_manifest)
    x_test, y_test = load_matrix_from_manifest(test_manifest)

    if len(x_train) == 0:
        report = {
            "status": "no_labeled_samples",
            "message": "train manifest has no labeled epochs",
        }
        (output_dir / "semi_supervised_report.json").write_text(
            json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8"
        )
        print(json.dumps(report, ensure_ascii=False))
        return

    x_train_balanced, y_train_balanced, balanced_counts = oversample_minority_classes(
        x_train, y_train, seed=int(args.random_seed)
    )
    labeled_weights = np.ones((len(x_train_balanced),), dtype=np.float32)

    x_pseudo, y_pseudo, w_pseudo, pseudo_stage_counts = load_pseudo_weighted_samples(
        pseudo_labels_path=pseudo_labels_path,
        pseudo_weight=float(args.pseudo_weight),
        pseudo_min_confidence=float(args.pseudo_min_confidence),
    )

    if len(x_pseudo) > 0:
        x_combined = np.vstack([x_train_balanced, x_pseudo]).astype(np.float32)
        y_combined = np.concatenate([y_train_balanced, y_pseudo]).astype(np.int64)
        sample_weights = np.concatenate([labeled_weights, w_pseudo]).astype(np.float32)
    else:
        x_combined = x_train_balanced
        y_combined = y_train_balanced
        sample_weights = labeled_weights

    model = RandomForestClassifier(
        n_estimators=700,
        random_state=int(args.random_seed),
        class_weight="balanced_subsample",
        n_jobs=-1,
    )
    model.fit(x_combined, y_combined, sample_weight=sample_weights)

    train_metrics = evaluate_model(model, x_train, y_train)
    val_metrics = evaluate_model(model, x_val, y_val)
    test_metrics = evaluate_model(model, x_test, y_test)

    report = {
        "status": "ok",
        "model": {
            "type": "RandomForestClassifier-SemiSupervised",
            "features": int(x_train.shape[1]) if len(x_train) > 0 else 51,
            "stages": STAGE_ORDER,
            "training": {
                "class_imbalance": "random_oversampling + class_weight",
                "pseudo_label_weighting": True,
                "balanced_train_samples": int(len(x_train_balanced)),
                "balanced_stage_counts": balanced_counts,
                "pseudo_samples": int(len(x_pseudo)),
                "pseudo_stage_counts": pseudo_stage_counts,
                "pseudo_weight": float(args.pseudo_weight),
                "pseudo_min_confidence": float(args.pseudo_min_confidence),
            },
        },
        "metrics": {
            "train": train_metrics,
            "val": val_metrics,
            "test": test_metrics,
        },
    }

    model_meta = {
        "model_type": "RandomForestClassifier-SemiSupervised",
        "n_estimators": 700,
        "random_state": int(args.random_seed),
        "class_weight": "balanced_subsample",
        "features": int(x_train.shape[1]) if len(x_train) > 0 else 51,
        "pseudo_samples": int(len(x_pseudo)),
    }
    (output_dir / "semi_supervised_model.json").write_text(
        json.dumps(model_meta, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    (output_dir / "semi_supervised_report.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8"
    )

    print("Semi-supervised training completed")
    print(json.dumps(report["metrics"], indent=2, ensure_ascii=False))


if __name__ == "__main__":
    main()
