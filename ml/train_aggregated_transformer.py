"""Train AggregatedSleepTransformer with multi-task loss (stage + anomaly)."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any

import numpy as np
from numpy.typing import NDArray
import torch
from torch import nn
import torch.nn.functional as F
from torch.utils.data import DataLoader, Dataset

from train_baseline import (
    ID_TO_STAGE,
    STAGE_ORDER,
    STAGE_TO_ID,
    compute_confusion_matrix,
    compute_metrics,
    normalize_stage,
    read_any_table,
    read_jsonl,
    to_number,
)
from train_multimodal_transformer import (
    MODALITY_DIMS,
    MODALITY_NAMES,
    FocalCrossEntropyLoss,
    class_weights_from_labels,
    compute_modality_stats,
    load_split_records,
    modality_features,
    normalize_modalities,
)
from AggregatedTransformer import AggregatedSleepTransformer

FloatArray = NDArray[np.float32]
IntArray = NDArray[np.int64]
BatchType = tuple[dict[str, torch.Tensor], torch.Tensor, torch.Tensor]

ANOMALY_THRESHOLD = 0.5


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Train aggregated sleep transformer with anomaly head"
    )
    _ = parser.add_argument("--manifests_dir", type=str, default="./data/manifests")
    _ = parser.add_argument(
        "--train_manifest", type=str, default="train_manifest.jsonl"
    )
    _ = parser.add_argument("--val_manifest", type=str, default="val_manifest.jsonl")
    _ = parser.add_argument("--test_manifest", type=str, default="test_manifest.jsonl")
    _ = parser.add_argument(
        "--output_dir", type=str, default="./artifacts/week2/aggregated_transformer"
    )
    _ = parser.add_argument("--epochs", type=int, default=30)
    _ = parser.add_argument("--batch_size", type=int, default=128)
    _ = parser.add_argument("--learning_rate", type=float, default=5e-4)
    _ = parser.add_argument("--weight_decay", type=float, default=1e-3)
    _ = parser.add_argument("--d_model", type=int, default=64)
    _ = parser.add_argument("--num_heads", type=int, default=4)
    _ = parser.add_argument("--num_layers", type=int, default=2)
    _ = parser.add_argument("--dropout", type=float, default=0.15)
    _ = parser.add_argument("--class_weight_power", type=float, default=0.5)
    _ = parser.add_argument("--class_weight_min", type=float, default=0.4)
    _ = parser.add_argument("--class_weight_max", type=float, default=2.5)
    _ = parser.add_argument("--disable_class_weights", action="store_true")
    _ = parser.add_argument(
        "--loss_type", type=str, choices=["ce", "focal"], default="ce"
    )
    _ = parser.add_argument("--focal_gamma", type=float, default=2.0)
    _ = parser.add_argument("--anomaly_weight", type=float, default=0.3)
    _ = parser.add_argument(
        "--select_metric",
        type=str,
        choices=["macro_f1", "accuracy"],
        default="macro_f1",
    )
    _ = parser.add_argument("--random_seed", type=int, default=42)
    return parser.parse_args()


def load_split_with_anomaly(
    path: Path,
) -> tuple[dict[str, FloatArray], IntArray, FloatArray]:
    """Load records and derive anomaly labels from edge_anomaly_signal."""
    features: dict[str, list[FloatArray]] = {name: [] for name in MODALITY_NAMES}
    labels: list[int] = []
    anomaly_labels: list[float] = []

    for record in read_jsonl(path):
        item_path = Path(str(record.get("path", "")))
        if not item_path.exists():
            continue
        epochs = read_any_table(item_path)
        for epoch in epochs:
            stage = normalize_stage(
                epoch.get("sleep_stage_5")
                or epoch.get("sleepStage5")
                or epoch.get("label")
            )
            if stage is None:
                continue
            modality = modality_features(epoch)
            for name in MODALITY_NAMES:
                features[name].append(modality[name])
            labels.append(stage)

            edge_val = to_number(
                epoch.get("edgeAnomalySignal")
                or epoch.get("edge_anomaly_signal")
            )
            if edge_val is not None:
                normalized = edge_val / 100.0 if edge_val > 1.0 else edge_val
                anomaly_labels.append(1.0 if normalized > ANOMALY_THRESHOLD else 0.0)
            else:
                anomaly_labels.append(0.0)

    if not labels:
        empty = {
            name: np.zeros((0, MODALITY_DIMS[name]), dtype=np.float32)
            for name in MODALITY_NAMES
        }
        return (
            empty,
            np.zeros((0,), dtype=np.int64),
            np.zeros((0,), dtype=np.float32),
        )

    stacked = {
        name: np.vstack(values).astype(np.float32)
        for name, values in features.items()
    }
    return (
        stacked,
        np.asarray(labels, dtype=np.int64),
        np.asarray(anomaly_labels, dtype=np.float32),
    )


class AnomalyDataset(Dataset[BatchType]):
    def __init__(
        self,
        x: dict[str, FloatArray],
        y: IntArray,
        anomaly: FloatArray,
    ):
        self.x = {name: torch.from_numpy(values) for name, values in x.items()}
        self.y = torch.from_numpy(y)
        self.anomaly = torch.from_numpy(anomaly)

    def __len__(self) -> int:
        return int(self.y.shape[0])

    def __getitem__(
        self, index: int
    ) -> tuple[dict[str, torch.Tensor], torch.Tensor, torch.Tensor]:
        item = {name: values[index] for name, values in self.x.items()}
        return item, self.y[index], self.anomaly[index]


def train_epoch(
    model: AggregatedSleepTransformer,
    loader: DataLoader[BatchType],
    stage_criterion: nn.Module,
    anomaly_weight: float,
    optimizer: torch.optim.Optimizer,
    device: torch.device,
) -> float:
    model.train()
    losses: list[float] = []
    for batch_x, batch_y, batch_anomaly in loader:
        inputs = {k: v.to(device) for k, v in batch_x.items()}
        labels = batch_y.to(device)
        anomaly_target = batch_anomaly.to(device).unsqueeze(1)

        stage_logits, anomaly_pred = model(inputs)
        stage_loss = stage_criterion(stage_logits, labels)
        anomaly_loss = F.binary_cross_entropy(anomaly_pred, anomaly_target)
        loss = stage_loss + anomaly_weight * anomaly_loss

        optimizer.zero_grad(set_to_none=True)
        loss.backward()
        nn.utils.clip_grad_norm_(model.parameters(), max_norm=1.0)
        optimizer.step()
        losses.append(float(loss.item()))
    return float(np.mean(losses)) if losses else 0.0


def evaluate(
    model: AggregatedSleepTransformer,
    loader: DataLoader[BatchType],
    device: torch.device,
) -> dict[str, Any]:
    model.eval()
    all_true: list[int] = []
    all_pred: list[int] = []
    anomaly_preds: list[float] = []
    anomaly_targets: list[float] = []
    with torch.no_grad():
        for batch_x, batch_y, batch_anomaly in loader:
            inputs = {k: v.to(device) for k, v in batch_x.items()}
            labels = batch_y.to(device)
            stage_logits, anomaly_pred = model(inputs)
            pred = torch.argmax(stage_logits, dim=1)
            all_true.extend(labels.cpu().numpy().tolist())
            all_pred.extend(pred.cpu().numpy().tolist())
            anomaly_preds.extend(anomaly_pred.squeeze(1).cpu().numpy().tolist())
            anomaly_targets.extend(batch_anomaly.numpy().tolist())

    y_true = np.asarray(all_true, dtype=np.int64)
    y_pred = np.asarray(all_pred, dtype=np.int64)
    if len(y_true) == 0:
        return {
            "samples": 0,
            "accuracy": None,
            "macro_f1": None,
            "report": {},
            "confusion_matrix": {"labels": STAGE_ORDER, "matrix": []},
            "anomaly_auc": None,
        }
    metrics = compute_metrics(y_true, y_pred)
    confusion = compute_confusion_matrix(y_true, y_pred)
    return {
        "samples": int(len(y_true)),
        "accuracy": metrics["accuracy"],
        "macro_f1": metrics["macro_f1"],
        "report": metrics["report"],
        "confusion_matrix": confusion,
    }


def main() -> None:
    args = parse_args()
    torch.manual_seed(int(args.random_seed))
    np.random.seed(int(args.random_seed))

    manifests_dir = Path(str(args.manifests_dir))
    output_dir = Path(str(args.output_dir))
    output_dir.mkdir(parents=True, exist_ok=True)

    train_x, train_y, train_anomaly = load_split_with_anomaly(
        manifests_dir / str(args.train_manifest)
    )
    val_x, val_y, val_anomaly = load_split_with_anomaly(
        manifests_dir / str(args.val_manifest)
    )
    test_x, test_y, test_anomaly = load_split_with_anomaly(
        manifests_dir / str(args.test_manifest)
    )

    if len(train_y) == 0:
        report = {"status": "no_labeled_samples", "message": "empty train split"}
        (output_dir / "aggregated_report.json").write_text(
            json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8"
        )
        print(json.dumps(report, ensure_ascii=False))
        return

    stats = compute_modality_stats(train_x)
    train_x_norm = normalize_modalities(train_x, stats)
    val_x_norm = normalize_modalities(val_x, stats)
    test_x_norm = normalize_modalities(test_x, stats)

    # Save normalization stats for ONNX export
    norm_stats = {}
    for name in MODALITY_NAMES:
        mean, std = stats[name]
        norm_stats[name] = {
            "mean": mean.tolist(),
            "std": std.tolist(),
        }
    (output_dir / "normalization_stats.json").write_text(
        json.dumps(norm_stats, ensure_ascii=False, indent=2), encoding="utf-8"
    )

    train_ds = AnomalyDataset(train_x_norm, train_y, train_anomaly)
    val_ds = AnomalyDataset(val_x_norm, val_y, val_anomaly)
    test_ds = AnomalyDataset(test_x_norm, test_y, test_anomaly)

    train_loader = DataLoader(
        train_ds, batch_size=int(args.batch_size), shuffle=True, num_workers=0
    )
    val_loader = DataLoader(
        val_ds, batch_size=int(args.batch_size), shuffle=False, num_workers=0
    )
    test_loader = DataLoader(
        test_ds, batch_size=int(args.batch_size), shuffle=False, num_workers=0
    )

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    model = AggregatedSleepTransformer(
        d_model=int(args.d_model),
        num_heads=int(args.num_heads),
        num_layers=int(args.num_layers),
        dropout=float(args.dropout),
        num_classes=len(STAGE_ORDER),
    ).to(device)

    class_weights: torch.Tensor | None = None
    if not bool(args.disable_class_weights):
        class_weights = class_weights_from_labels(
            train_y,
            power=float(args.class_weight_power),
            min_weight=float(args.class_weight_min),
            max_weight=float(args.class_weight_max),
        ).to(device)

    if str(args.loss_type) == "focal":
        stage_criterion: nn.Module = FocalCrossEntropyLoss(
            gamma=float(args.focal_gamma),
            class_weights=class_weights,
        )
    elif class_weights is None:
        stage_criterion = nn.CrossEntropyLoss()
    else:
        stage_criterion = nn.CrossEntropyLoss(weight=class_weights)

    optimizer = torch.optim.AdamW(
        model.parameters(),
        lr=float(args.learning_rate),
        weight_decay=float(args.weight_decay),
    )

    history: list[dict[str, float]] = []
    best_state = None
    best_val_score = -1.0

    for epoch in range(1, int(args.epochs) + 1):
        train_loss = train_epoch(
            model,
            train_loader,
            stage_criterion,
            float(args.anomaly_weight),
            optimizer,
            device,
        )
        val_metrics = evaluate(model, val_loader, device)
        val_macro_f1 = float(val_metrics["macro_f1"] or 0.0)
        val_accuracy = float(val_metrics["accuracy"] or 0.0)
        current_score = (
            val_accuracy if str(args.select_metric) == "accuracy" else val_macro_f1
        )
        history.append(
            {
                "epoch": float(epoch),
                "train_loss": float(train_loss),
                "val_macro_f1": float(val_macro_f1),
                "val_accuracy": float(val_accuracy),
            }
        )
        print(
            f"Epoch {epoch:3d} | loss={train_loss:.4f} "
            f"| val_f1={val_macro_f1:.4f} val_acc={val_accuracy:.4f}"
        )
        if current_score >= best_val_score:
            best_val_score = current_score
            best_state = {k: v.cpu() for k, v in model.state_dict().items()}

    if best_state is not None:
        model.load_state_dict(best_state)
        torch.save(best_state, output_dir / "best_model.pth")

    train_metrics = evaluate(model, train_loader, device)
    val_metrics = evaluate(model, val_loader, device)
    test_metrics = evaluate(model, test_loader, device)

    report = {
        "status": "ok",
        "model": {
            "type": "AggregatedSleepTransformer",
            "d_model": int(args.d_model),
            "num_heads": int(args.num_heads),
            "num_layers": int(args.num_layers),
            "dropout": float(args.dropout),
            "anomaly_weight": float(args.anomaly_weight),
            "loss_type": str(args.loss_type),
            "features": MODALITY_DIMS,
            "stages": STAGE_ORDER,
            "epochs": int(args.epochs),
            "device": str(device),
            "best_val_score": best_val_score,
        },
        "metrics": {
            "train": train_metrics,
            "val": val_metrics,
            "test": test_metrics,
        },
        "history": history,
    }

    (output_dir / "aggregated_report.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    print("\nTraining completed")
    print(json.dumps(report["metrics"], indent=2, ensure_ascii=False))


if __name__ == "__main__":
    main()
