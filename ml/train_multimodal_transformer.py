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


MODALITY_NAMES = ["hr", "spo2", "hrv", "temp", "motion", "ppg", "edge"]
MODALITY_DIMS = {
    "hr": 4,
    "spo2": 3,
    "hrv": 4,
    "temp": 3,
    "motion": 1,
    "ppg": 1,
    "edge": 1,
}


FloatArray = NDArray[np.float32]
IntArray = NDArray[np.int64]
BatchType = tuple[dict[str, torch.Tensor], torch.Tensor]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Train multimodal transformer on manifest epochs"
    )
    _ = parser.add_argument("--manifests_dir", type=str, default="./data/manifests")
    _ = parser.add_argument(
        "--train_manifest", type=str, default="train_manifest.jsonl"
    )
    _ = parser.add_argument("--val_manifest", type=str, default="val_manifest.jsonl")
    _ = parser.add_argument("--test_manifest", type=str, default="test_manifest.jsonl")
    _ = parser.add_argument(
        "--output_dir", type=str, default="./artifacts/week2/transformer"
    )
    _ = parser.add_argument("--epochs", type=int, default=20)
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
        "--loss_type",
        type=str,
        choices=["ce", "focal"],
        default="ce",
    )
    _ = parser.add_argument("--focal_gamma", type=float, default=2.0)
    _ = parser.add_argument(
        "--select_metric",
        type=str,
        choices=["macro_f1", "accuracy"],
        default="macro_f1",
    )
    _ = parser.add_argument("--random_seed", type=int, default=42)
    return parser.parse_args()


def modality_features(epoch: dict[str, Any]) -> dict[str, FloatArray]:
    hr = epoch.get("hrFeatures") or epoch.get("hr_features") or {}
    spo2 = epoch.get("spo2Features") or epoch.get("spo2_features") or {}
    hrv = epoch.get("hrvFeatures") or epoch.get("hrv_features") or {}
    temp = epoch.get("tempFeatures") or epoch.get("temp_features") or {}
    motion = epoch.get("motionFeatures") or epoch.get("motion_features") or {}
    ppg = epoch.get("ppgFeatures") or epoch.get("ppg_features") or {}

    def pick(values: list[Any], size: int) -> FloatArray:
        parsed = [to_number(item) for item in values]
        filled = [(item if item is not None else 0.0) for item in parsed]
        return np.asarray(filled[:size], dtype=np.float32)

    return {
        "hr": pick(
            [
                hr.get("heartRate"),
                hr.get("heartRateAvg"),
                hr.get("heartRateMin"),
                hr.get("heartRateMax"),
            ],
            MODALITY_DIMS["hr"],
        ),
        "spo2": pick(
            [
                spo2.get("bloodOxygen"),
                spo2.get("bloodOxygenAvg"),
                spo2.get("bloodOxygenMin"),
            ],
            MODALITY_DIMS["spo2"],
        ),
        "hrv": pick(
            [
                hrv.get("hrv"),
                hrv.get("rmssd"),
                hrv.get("sdnn"),
                hrv.get("lfHfRatio"),
            ],
            MODALITY_DIMS["hrv"],
        ),
        "temp": pick(
            [
                temp.get("temperature"),
                temp.get("temperatureAvg"),
                temp.get("temperatureTrend"),
            ],
            MODALITY_DIMS["temp"],
        ),
        "motion": pick(
            [motion.get("motionIntensity")],
            MODALITY_DIMS["motion"],
        ),
        "ppg": pick([ppg.get("ppgValue")], MODALITY_DIMS["ppg"]),
        "edge": pick(
            [epoch.get("edgeAnomalySignal") or epoch.get("edge_anomaly_signal")],
            MODALITY_DIMS["edge"],
        ),
    }


def load_split_records(path: Path) -> tuple[dict[str, FloatArray], IntArray]:
    features = {name: [] for name in MODALITY_NAMES}
    labels: list[int] = []

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

    if not labels:
        empty = {
            name: np.zeros((0, MODALITY_DIMS[name]), dtype=np.float32)
            for name in MODALITY_NAMES
        }
        return empty, np.zeros((0,), dtype=np.int64)

    stacked = {
        name: np.vstack(values).astype(np.float32) for name, values in features.items()
    }
    return stacked, np.asarray(labels, dtype=np.int64)


class ManifestDataset(Dataset[BatchType]):
    def __init__(self, x: dict[str, FloatArray], y: IntArray):
        self.x = {name: torch.from_numpy(values) for name, values in x.items()}
        self.y = torch.from_numpy(y)

    def __len__(self) -> int:
        return int(self.y.shape[0])

    def __getitem__(self, index: int) -> tuple[dict[str, torch.Tensor], torch.Tensor]:
        item = {name: values[index] for name, values in self.x.items()}
        return item, self.y[index]


class MultiModalEpochTransformer(nn.Module):
    def __init__(
        self,
        d_model: int,
        num_heads: int,
        num_layers: int,
        dropout: float,
        num_classes: int,
    ):
        super().__init__()
        self.projectors = nn.ModuleDict(
            {
                name: nn.Sequential(
                    nn.Linear(MODALITY_DIMS[name], d_model),
                    nn.LayerNorm(d_model),
                    nn.GELU(),
                )
                for name in MODALITY_NAMES
            }
        )
        self.cls_token = nn.Parameter(torch.zeros(1, 1, d_model))
        self.pos_embedding = nn.Parameter(
            torch.zeros(1, len(MODALITY_NAMES) + 1, d_model)
        )
        encoder_layer = nn.TransformerEncoderLayer(
            d_model=d_model,
            nhead=num_heads,
            dim_feedforward=d_model * 4,
            dropout=dropout,
            activation="gelu",
            batch_first=True,
        )
        self.encoder = nn.TransformerEncoder(encoder_layer, num_layers=num_layers)
        self.classifier = nn.Sequential(
            nn.LayerNorm(d_model),
            nn.Dropout(dropout),
            nn.Linear(d_model, num_classes),
        )

    def forward(self, batch: dict[str, torch.Tensor]) -> torch.Tensor:
        tokens = [self.projectors[name](batch[name]) for name in MODALITY_NAMES]
        x = torch.stack(tokens, dim=1)
        bsz = x.shape[0]
        cls = self.cls_token.expand(bsz, -1, -1)
        x = torch.cat([cls, x], dim=1)
        x = x + self.pos_embedding[:, : x.shape[1], :]
        encoded = self.encoder(x)
        return self.classifier(encoded[:, 0, :])


class FocalCrossEntropyLoss(nn.Module):
    def __init__(self, gamma: float, class_weights: torch.Tensor | None = None):
        super().__init__()
        self.gamma = gamma
        self.class_weights = class_weights

    def forward(self, logits: torch.Tensor, labels: torch.Tensor) -> torch.Tensor:
        ce = F.cross_entropy(
            logits,
            labels,
            weight=self.class_weights,
            reduction="none",
        )
        pt = torch.exp(-ce)
        loss = torch.pow(1.0 - pt, self.gamma) * ce
        return loss.mean()


def class_weights_from_labels(
    labels: IntArray,
    power: float,
    min_weight: float,
    max_weight: float,
) -> torch.Tensor:
    counts = np.bincount(labels, minlength=len(STAGE_ORDER)).astype(np.float32)
    counts = np.maximum(counts, 1.0)
    inv = np.power(counts.sum() / counts, power)
    normalized = inv / inv.mean()
    normalized = np.clip(normalized, min_weight, max_weight)
    return torch.tensor(normalized, dtype=torch.float32)


def compute_modality_stats(
    x: dict[str, FloatArray],
) -> dict[str, tuple[FloatArray, FloatArray]]:
    stats: dict[str, tuple[FloatArray, FloatArray]] = {}
    for name, values in x.items():
        if values.shape[0] == 0:
            dim = values.shape[1]
            stats[name] = (
                np.zeros((dim,), dtype=np.float32),
                np.ones((dim,), dtype=np.float32),
            )
            continue
        mean = values.mean(axis=0).astype(np.float32)
        std = values.std(axis=0).astype(np.float32)
        std = np.maximum(std, 1e-6)
        stats[name] = (mean, std)
    return stats


def normalize_modalities(
    x: dict[str, FloatArray],
    stats: dict[str, tuple[FloatArray, FloatArray]],
) -> dict[str, FloatArray]:
    normalized: dict[str, FloatArray] = {}
    for name, values in x.items():
        mean, std = stats[name]
        normalized[name] = ((values - mean) / std).astype(np.float32)
    return normalized


def evaluate_transformer(
    model: nn.Module,
    loader: DataLoader[BatchType],
    device: torch.device,
) -> dict[str, Any]:
    model.eval()
    all_true: list[int] = []
    all_pred: list[int] = []
    with torch.no_grad():
        for batch_x, batch_y in loader:
            inputs = {k: v.to(device) for k, v in batch_x.items()}
            labels = batch_y.to(device)
            logits = model(inputs)
            pred = torch.argmax(logits, dim=1)
            all_true.extend(labels.cpu().numpy().tolist())
            all_pred.extend(pred.cpu().numpy().tolist())

    y_true = np.asarray(all_true, dtype=np.int64)
    y_pred = np.asarray(all_pred, dtype=np.int64)
    if len(y_true) == 0:
        return {
            "samples": 0,
            "accuracy": None,
            "macro_f1": None,
            "report": {},
            "confusion_matrix": {"labels": STAGE_ORDER, "matrix": []},
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


def train_epoch(
    model: nn.Module,
    loader: DataLoader[BatchType],
    criterion: nn.Module,
    optimizer: torch.optim.Optimizer,
    device: torch.device,
) -> float:
    model.train()
    losses: list[float] = []
    for batch_x, batch_y in loader:
        inputs = {k: v.to(device) for k, v in batch_x.items()}
        labels = batch_y.to(device)
        logits = model(inputs)
        loss = criterion(logits, labels)
        optimizer.zero_grad(set_to_none=True)
        loss.backward()
        nn.utils.clip_grad_norm_(model.parameters(), max_norm=1.0)
        optimizer.step()
        losses.append(float(loss.item()))
    return float(np.mean(losses)) if losses else 0.0


def main() -> None:
    args = parse_args()
    torch.manual_seed(int(args.random_seed))
    np.random.seed(int(args.random_seed))

    manifests_dir = Path(str(args.manifests_dir))
    output_dir = Path(str(args.output_dir))
    output_dir.mkdir(parents=True, exist_ok=True)

    train_x, train_y = load_split_records(manifests_dir / str(args.train_manifest))
    val_x, val_y = load_split_records(manifests_dir / str(args.val_manifest))
    test_x, test_y = load_split_records(manifests_dir / str(args.test_manifest))

    if len(train_y) == 0:
        report = {"status": "no_labeled_samples", "message": "empty train split"}
        (output_dir / "transformer_report.json").write_text(
            json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8"
        )
        print(json.dumps(report, ensure_ascii=False))
        return

    stats = compute_modality_stats(train_x)
    train_x = normalize_modalities(train_x, stats)
    val_x = normalize_modalities(val_x, stats)
    test_x = normalize_modalities(test_x, stats)

    train_ds = ManifestDataset(train_x, train_y)
    val_ds = ManifestDataset(val_x, val_y)
    test_ds = ManifestDataset(test_x, test_y)

    train_loader = DataLoader(
        train_ds,
        batch_size=int(args.batch_size),
        shuffle=True,
        num_workers=0,
    )
    val_loader = DataLoader(
        val_ds,
        batch_size=int(args.batch_size),
        shuffle=False,
        num_workers=0,
    )
    test_loader = DataLoader(
        test_ds,
        batch_size=int(args.batch_size),
        shuffle=False,
        num_workers=0,
    )

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    model = MultiModalEpochTransformer(
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
        criterion = FocalCrossEntropyLoss(
            gamma=float(args.focal_gamma),
            class_weights=class_weights,
        )
    elif class_weights is None:
        criterion = nn.CrossEntropyLoss()
    else:
        criterion = nn.CrossEntropyLoss(weight=class_weights)
    optimizer = torch.optim.AdamW(
        model.parameters(),
        lr=float(args.learning_rate),
        weight_decay=float(args.weight_decay),
    )

    history: list[dict[str, float]] = []
    best_state = None
    best_val_score = -1.0

    for epoch in range(1, int(args.epochs) + 1):
        train_loss = train_epoch(model, train_loader, criterion, optimizer, device)
        val_metrics = evaluate_transformer(model, val_loader, device)
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
        if current_score >= best_val_score:
            best_val_score = current_score
            best_state = {k: v.cpu() for k, v in model.state_dict().items()}

    if best_state is not None:
        model.load_state_dict(best_state)

    train_metrics = evaluate_transformer(model, train_loader, device)
    val_metrics = evaluate_transformer(model, val_loader, device)
    test_metrics = evaluate_transformer(model, test_loader, device)

    report = {
        "status": "ok",
        "model": {
            "type": "MultiModalEpochTransformer",
            "d_model": int(args.d_model),
            "num_heads": int(args.num_heads),
            "num_layers": int(args.num_layers),
            "dropout": float(args.dropout),
            "class_weight_power": float(args.class_weight_power),
            "class_weight_min": float(args.class_weight_min),
            "class_weight_max": float(args.class_weight_max),
            "disable_class_weights": bool(args.disable_class_weights),
            "loss_type": str(args.loss_type),
            "focal_gamma": float(args.focal_gamma),
            "select_metric": str(args.select_metric),
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

    (output_dir / "transformer_report.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    (output_dir / "transformer_history.json").write_text(
        json.dumps(history, ensure_ascii=False, indent=2), encoding="utf-8"
    )

    print("Transformer training completed")
    print(json.dumps(report["metrics"], indent=2, ensure_ascii=False))


if __name__ == "__main__":
    main()
