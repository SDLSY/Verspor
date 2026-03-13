from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any

import numpy as np
import torch
from numpy.typing import NDArray
from sklearn.ensemble import RandomForestClassifier
from torch import nn
from torch.utils.data import DataLoader

from train_baseline import (
    STAGE_ORDER,
    compute_confusion_matrix,
    compute_metrics,
    load_matrix_from_manifest,
    oversample_minority_classes,
    train_model,
)
from train_multimodal_transformer import (
    BatchType,
    FocalCrossEntropyLoss,
    ManifestDataset,
    MultiModalEpochTransformer,
    class_weights_from_labels,
    compute_modality_stats,
    load_split_records,
    normalize_modalities,
    train_epoch,
)

FloatArray = NDArray[np.float32]
IntArray = NDArray[np.int64]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Evaluate RF + Transformer soft-voting"
    )
    _ = parser.add_argument("--manifests_dir", type=str, default="./data/manifests")
    _ = parser.add_argument(
        "--train_manifest", type=str, default="train_manifest.jsonl"
    )
    _ = parser.add_argument("--val_manifest", type=str, default="val_manifest.jsonl")
    _ = parser.add_argument("--test_manifest", type=str, default="test_manifest.jsonl")
    _ = parser.add_argument(
        "--output_dir", type=str, default="./artifacts/week2/ensemble_soft_voting"
    )
    _ = parser.add_argument("--epochs", type=int, default=20)
    _ = parser.add_argument("--batch_size", type=int, default=128)
    _ = parser.add_argument("--learning_rate", type=float, default=5e-4)
    _ = parser.add_argument("--weight_decay", type=float, default=1e-3)
    _ = parser.add_argument("--d_model", type=int, default=64)
    _ = parser.add_argument("--num_heads", type=int, default=4)
    _ = parser.add_argument("--num_layers", type=int, default=2)
    _ = parser.add_argument("--dropout", type=float, default=0.15)
    _ = parser.add_argument("--class_weight_power", type=float, default=0.35)
    _ = parser.add_argument("--class_weight_min", type=float, default=0.6)
    _ = parser.add_argument("--class_weight_max", type=float, default=1.8)
    _ = parser.add_argument("--focal_gamma", type=float, default=1.5)
    _ = parser.add_argument(
        "--transformer_loss_type", choices=["ce", "focal"], default="focal"
    )
    _ = parser.add_argument("--disable_class_weights", action="store_true")
    _ = parser.add_argument(
        "--optimize_metric", choices=["accuracy", "macro_f1"], default="macro_f1"
    )
    _ = parser.add_argument("--random_seed", type=int, default=42)
    return parser.parse_args()


def to_loader(
    x: dict[str, FloatArray], y: IntArray, batch_size: int, shuffle: bool
) -> DataLoader[BatchType]:
    ds = ManifestDataset(x, y)
    return DataLoader(ds, batch_size=batch_size, shuffle=shuffle, num_workers=0)


def predict_transformer_proba(
    model: nn.Module, loader: DataLoader[BatchType], device: torch.device
) -> tuple[IntArray, NDArray[np.float64]]:
    model.eval()
    all_true: list[int] = []
    all_proba: list[NDArray[np.float64]] = []
    with torch.no_grad():
        for batch_x, batch_y in loader:
            inputs = {k: v.to(device) for k, v in batch_x.items()}
            logits = model(inputs)
            proba = torch.softmax(logits, dim=1).cpu().numpy().astype(np.float64)
            all_true.extend(batch_y.numpy().tolist())
            all_proba.append(proba)

    y_true = np.asarray(all_true, dtype=np.int64)
    probs = (
        np.vstack(all_proba)
        if all_proba
        else np.zeros((0, len(STAGE_ORDER)), dtype=np.float64)
    )
    return y_true, probs


def predict_rf_proba(
    model: RandomForestClassifier, x: NDArray[np.float32]
) -> NDArray[np.float64]:
    if len(x) == 0:
        return np.zeros((0, len(STAGE_ORDER)), dtype=np.float64)
    probs = model.predict_proba(x)
    if isinstance(probs, list):
        return np.asarray(probs[0], dtype=np.float64)
    return np.asarray(probs, dtype=np.float64)


def evaluate_probs(y_true: IntArray, probs: NDArray[np.float64]) -> dict[str, Any]:
    if len(y_true) == 0:
        return {
            "samples": 0,
            "accuracy": None,
            "macro_f1": None,
            "report": {},
            "confusion_matrix": {"labels": STAGE_ORDER, "matrix": []},
        }
    y_pred = np.argmax(probs, axis=1).astype(np.int64)
    metrics = compute_metrics(y_true, y_pred)
    confusion = compute_confusion_matrix(y_true, y_pred)
    return {
        "samples": int(len(y_true)),
        "accuracy": metrics["accuracy"],
        "macro_f1": metrics["macro_f1"],
        "report": metrics["report"],
        "confusion_matrix": confusion,
    }


def check_aligned(y_rf: IntArray, y_tf: IntArray, split: str) -> None:
    if len(y_rf) != len(y_tf) or not np.array_equal(y_rf, y_tf):
        raise RuntimeError(f"{split} labels not aligned between RF and Transformer")


def main() -> None:
    args = parse_args()
    np.random.seed(int(args.random_seed))
    torch.manual_seed(int(args.random_seed))

    manifests_dir = Path(str(args.manifests_dir))
    output_dir = Path(str(args.output_dir))
    output_dir.mkdir(parents=True, exist_ok=True)

    train_manifest = manifests_dir / str(args.train_manifest)
    val_manifest = manifests_dir / str(args.val_manifest)
    test_manifest = manifests_dir / str(args.test_manifest)

    x_rf_train, y_rf_train = load_matrix_from_manifest(train_manifest)
    x_rf_val, y_rf_val = load_matrix_from_manifest(val_manifest)
    x_rf_test, y_rf_test = load_matrix_from_manifest(test_manifest)

    if len(y_rf_train) == 0:
        report = {"status": "no_labeled_samples", "message": "empty train split"}
        (output_dir / "ensemble_report.json").write_text(
            json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8"
        )
        print(json.dumps(report, ensure_ascii=False))
        return

    x_rf_bal, y_rf_bal, rf_balanced_counts = oversample_minority_classes(
        x_rf_train, y_rf_train, seed=int(args.random_seed)
    )
    rf_model = train_model(x_rf_bal, y_rf_bal)

    train_tf_x, train_tf_y = load_split_records(train_manifest)
    val_tf_x, val_tf_y = load_split_records(val_manifest)
    test_tf_x, test_tf_y = load_split_records(test_manifest)

    stats = compute_modality_stats(train_tf_x)
    train_tf_x = normalize_modalities(train_tf_x, stats)
    val_tf_x = normalize_modalities(val_tf_x, stats)
    test_tf_x = normalize_modalities(test_tf_x, stats)

    check_aligned(y_rf_train, train_tf_y, "train")
    check_aligned(y_rf_val, val_tf_y, "val")
    check_aligned(y_rf_test, test_tf_y, "test")

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    tf_model = MultiModalEpochTransformer(
        d_model=int(args.d_model),
        num_heads=int(args.num_heads),
        num_layers=int(args.num_layers),
        dropout=float(args.dropout),
        num_classes=len(STAGE_ORDER),
    ).to(device)

    class_weights: torch.Tensor | None = None
    if not bool(args.disable_class_weights):
        class_weights = class_weights_from_labels(
            train_tf_y,
            power=float(args.class_weight_power),
            min_weight=float(args.class_weight_min),
            max_weight=float(args.class_weight_max),
        ).to(device)

    if str(args.transformer_loss_type) == "focal":
        criterion = FocalCrossEntropyLoss(
            gamma=float(args.focal_gamma), class_weights=class_weights
        )
    elif class_weights is None:
        criterion = nn.CrossEntropyLoss()
    else:
        criterion = nn.CrossEntropyLoss(weight=class_weights)
    optimizer = torch.optim.AdamW(
        tf_model.parameters(),
        lr=float(args.learning_rate),
        weight_decay=float(args.weight_decay),
    )

    train_loader = to_loader(
        train_tf_x,
        train_tf_y,
        batch_size=int(args.batch_size),
        shuffle=True,
    )
    val_loader = to_loader(
        val_tf_x,
        val_tf_y,
        batch_size=int(args.batch_size),
        shuffle=False,
    )
    test_loader = to_loader(
        test_tf_x,
        test_tf_y,
        batch_size=int(args.batch_size),
        shuffle=False,
    )

    best_val = -1.0
    best_state: dict[str, torch.Tensor] | None = None
    history: list[dict[str, float]] = []

    for epoch in range(1, int(args.epochs) + 1):
        train_loss = train_epoch(tf_model, train_loader, criterion, optimizer, device)
        y_val_true, val_tf_probs = predict_transformer_proba(
            tf_model, val_loader, device
        )
        val_tf_metrics = evaluate_probs(y_val_true, val_tf_probs)
        score = float(val_tf_metrics[str(args.optimize_metric)] or 0.0)
        history.append(
            {
                "epoch": float(epoch),
                "train_loss": float(train_loss),
                "val_accuracy": float(val_tf_metrics["accuracy"] or 0.0),
                "val_macro_f1": float(val_tf_metrics["macro_f1"] or 0.0),
            }
        )
        if score >= best_val:
            best_val = score
            best_state = {k: v.cpu() for k, v in tf_model.state_dict().items()}

    if best_state is not None:
        tf_model.load_state_dict(best_state)

    y_train_true, train_tf_probs = predict_transformer_proba(
        tf_model, train_loader, device
    )
    y_val_true, val_tf_probs = predict_transformer_proba(tf_model, val_loader, device)
    y_test_true, test_tf_probs = predict_transformer_proba(
        tf_model, test_loader, device
    )

    train_rf_probs = predict_rf_proba(rf_model, x_rf_train)
    val_rf_probs = predict_rf_proba(rf_model, x_rf_val)
    test_rf_probs = predict_rf_proba(rf_model, x_rf_test)

    candidate_weights = [round(v, 2) for v in np.arange(0.0, 1.01, 0.05)]
    best_weight = 0.5
    best_metric = -1.0
    for weight_rf in candidate_weights:
        fused = weight_rf * val_rf_probs + (1.0 - weight_rf) * val_tf_probs
        metrics = evaluate_probs(y_val_true, fused)
        metric_value = float(metrics[str(args.optimize_metric)] or 0.0)
        if metric_value >= best_metric:
            best_metric = metric_value
            best_weight = float(weight_rf)

    train_fused = best_weight * train_rf_probs + (1.0 - best_weight) * train_tf_probs
    val_fused = best_weight * val_rf_probs + (1.0 - best_weight) * val_tf_probs
    test_fused = best_weight * test_rf_probs + (1.0 - best_weight) * test_tf_probs

    report: dict[str, Any] = {
        "status": "ok",
        "model": {
            "type": "SoftVotingEnsemble",
            "stages": STAGE_ORDER,
            "optimize_metric": str(args.optimize_metric),
            "weight_search": candidate_weights,
            "best_weight_rf": best_weight,
            "best_weight_transformer": float(1.0 - best_weight),
            "best_val_metric": best_metric,
            "rf_training": {
                "class_imbalance": "random_oversampling + class_weight",
                "balanced_train_samples": int(len(x_rf_bal)),
                "balanced_stage_counts": rf_balanced_counts,
            },
            "transformer_training": {
                "loss_type": str(args.transformer_loss_type),
                "focal_gamma": float(args.focal_gamma),
                "disable_class_weights": bool(args.disable_class_weights),
                "d_model": int(args.d_model),
                "num_heads": int(args.num_heads),
                "num_layers": int(args.num_layers),
                "dropout": float(args.dropout),
                "epochs": int(args.epochs),
                "best_val_metric": best_val,
                "device": str(device),
            },
        },
        "metrics": {
            "train": evaluate_probs(y_train_true, train_fused),
            "val": evaluate_probs(y_val_true, val_fused),
            "test": evaluate_probs(y_test_true, test_fused),
            "rf_only_test": evaluate_probs(y_rf_test, test_rf_probs),
            "transformer_only_test": evaluate_probs(y_test_true, test_tf_probs),
        },
        "history": history,
    }

    (output_dir / "ensemble_report.json").write_text(
        json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8"
    )
    (output_dir / "ensemble_history.json").write_text(
        json.dumps(history, indent=2, ensure_ascii=False), encoding="utf-8"
    )

    print("Soft-voting ensemble evaluation completed")
    metrics_obj = report.get("metrics", {})
    test_obj = metrics_obj.get("test", {}) if isinstance(metrics_obj, dict) else {}
    print(json.dumps(test_obj, indent=2, ensure_ascii=False))


if __name__ == "__main__":
    main()
