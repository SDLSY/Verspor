from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any

import numpy as np
from numpy.typing import NDArray
from sklearn.ensemble import RandomForestClassifier


STAGE_TO_ID = {"WAKE": 0, "N1": 1, "N2": 2, "N3": 3, "REM": 4}
ID_TO_STAGE = {value: key for key, value in STAGE_TO_ID.items()}
STAGE_ORDER = ["WAKE", "N1", "N2", "N3", "REM"]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Train baseline sleep-stage model from manifests"
    )
    _ = parser.add_argument("--manifests_dir", type=str, default="./data/manifests")
    _ = parser.add_argument(
        "--train_manifest", type=str, default="train_manifest.jsonl"
    )
    _ = parser.add_argument("--val_manifest", type=str, default="val_manifest.jsonl")
    _ = parser.add_argument("--test_manifest", type=str, default="test_manifest.jsonl")
    _ = parser.add_argument(
        "--output_dir", type=str, default="./artifacts/week2/baseline"
    )
    _ = parser.add_argument("--random_seed", type=int, default=42)
    return parser.parse_args()


def read_jsonl(path: Path) -> list[dict[str, Any]]:
    if not path.exists():
        return []
    records: list[dict[str, Any]] = []
    with path.open("r", encoding="utf-8") as handle:
        for line in handle:
            line = line.strip()
            if not line:
                continue
            item = json.loads(line)
            if isinstance(item, dict):
                records.append(item)
    return records


def read_json_payload(path: Path) -> list[dict[str, Any]]:
    if not path.exists():
        return []
    if path.suffix.lower() == ".jsonl":
        return read_jsonl(path)
    with path.open("r", encoding="utf-8") as handle:
        data = json.load(handle)
    if isinstance(data, list):
        return [item for item in data if isinstance(item, dict)]
    if isinstance(data, dict):
        epochs = data.get("epochs")
        if isinstance(epochs, list):
            return [item for item in epochs if isinstance(item, dict)]
        return [data]
    return []


def read_csv_payload(path: Path) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    if not path.exists():
        return rows
    with path.open("r", encoding="utf-8") as handle:
        header_line = handle.readline().strip()
        if not header_line:
            return rows
        headers = [item.strip() for item in header_line.split(",")]
        for line in handle:
            line = line.strip()
            if not line:
                continue
            values = [item.strip() for item in line.split(",")]
            row = {
                headers[i]: values[i] if i < len(values) else ""
                for i in range(len(headers))
            }
            rows.append(row)
    return rows


def read_any_table(path: Path) -> list[dict[str, Any]]:
    suffix = path.suffix.lower()
    if suffix in {".json", ".jsonl"}:
        return read_json_payload(path)
    if suffix == ".csv":
        return read_csv_payload(path)
    return []


def normalize_stage(value: Any) -> int | None:
    if isinstance(value, str):
        upper = value.strip().upper()
        if upper in STAGE_TO_ID:
            return STAGE_TO_ID[upper]
    if isinstance(value, (int, np.integer)):
        stage = int(value)
        if stage in ID_TO_STAGE:
            return stage
    return None


def to_number(value: Any) -> float | None:
    if isinstance(value, (int, float, np.integer, np.floating)):
        number = float(value)
        if np.isfinite(number):
            return number
        return None
    if isinstance(value, str):
        text = value.strip()
        if not text:
            return None
        try:
            number = float(text)
        except ValueError:
            return None
        if np.isfinite(number):
            return number
    return None


def feature_vector(epoch: dict[str, Any]) -> NDArray[np.float32]:
    hr = epoch.get("hrFeatures") or epoch.get("hr_features") or {}
    spo2 = epoch.get("spo2Features") or epoch.get("spo2_features") or {}
    hrv = epoch.get("hrvFeatures") or epoch.get("hrv_features") or {}
    temp = epoch.get("tempFeatures") or epoch.get("temp_features") or {}
    motion = epoch.get("motionFeatures") or epoch.get("motion_features") or {}
    ppg = epoch.get("ppgFeatures") or epoch.get("ppg_features") or {}

    fields = [
        hr.get("heartRate"),
        hr.get("heartRateAvg"),
        hr.get("heartRateMin"),
        hr.get("heartRateMax"),
        spo2.get("bloodOxygen"),
        spo2.get("bloodOxygenAvg"),
        spo2.get("bloodOxygenMin"),
        hrv.get("hrv"),
        hrv.get("rmssd"),
        hrv.get("sdnn"),
        hrv.get("lfHfRatio"),
        temp.get("temperature"),
        temp.get("temperatureAvg"),
        temp.get("temperatureTrend"),
        motion.get("motionIntensity"),
        ppg.get("ppgValue"),
        epoch.get("edgeAnomalySignal") or epoch.get("edge_anomaly_signal"),
    ]

    values = [to_number(item) for item in fields]
    return np.array(
        [item if item is not None else 0.0 for item in values], dtype=np.float32
    )


def build_context_vectors(
    vectors: list[NDArray[np.float32]], context_size: int = 1
) -> list[NDArray[np.float32]]:
    if not vectors:
        return []
    width = int(vectors[0].shape[0])
    padded = (
        [np.zeros((width,), dtype=np.float32)] * context_size
        + vectors
        + [np.zeros((width,), dtype=np.float32)] * context_size
    )
    output: list[NDArray[np.float32]] = []
    window = context_size * 2 + 1
    for idx in range(context_size, context_size + len(vectors)):
        parts = padded[idx - context_size : idx + context_size + 1]
        output.append(np.concatenate(parts, axis=0))
    assert all(item.shape[0] == width * window for item in output)
    return output


def load_matrix_from_manifest(
    path: Path,
) -> tuple[NDArray[np.float32], NDArray[np.int64]]:
    vectors: list[NDArray[np.float32]] = []
    labels: list[int] = []

    for record in read_jsonl(path):
        item_path = Path(str(record.get("path", "")))
        if not item_path.exists():
            continue
        epochs = read_any_table(item_path)
        session_vectors: list[NDArray[np.float32]] = []
        session_labels: list[int] = []
        for epoch in epochs:
            stage = normalize_stage(
                epoch.get("sleep_stage_5")
                or epoch.get("sleepStage5")
                or epoch.get("label")
            )
            if stage is None:
                continue
            session_vectors.append(feature_vector(epoch))
            session_labels.append(stage)

        if session_vectors:
            vectors.extend(build_context_vectors(session_vectors, context_size=1))
            labels.extend(session_labels)

    if not vectors:
        return np.zeros((0, 51), dtype=np.float32), np.zeros((0,), dtype=np.int64)

    return np.vstack(vectors), np.array(labels, dtype=np.int64)


def train_model(x: NDArray[np.float32], y: NDArray[np.int64]) -> RandomForestClassifier:
    model = RandomForestClassifier(
        n_estimators=500,
        random_state=42,
        class_weight="balanced_subsample",
        n_jobs=-1,
    )
    model.fit(x, y)
    return model


def predict_model(
    model: RandomForestClassifier, x: NDArray[np.float32]
) -> NDArray[np.int64]:
    if len(x) == 0:
        return np.zeros((0,), dtype=np.int64)
    predicted = model.predict(x)
    return np.asarray(predicted, dtype=np.int64)


def compute_metrics(
    y_true: NDArray[np.int64], y_pred: NDArray[np.int64]
) -> dict[str, Any]:
    if len(y_true) == 0:
        return {"accuracy": None, "macro_f1": None, "report": {}}

    accuracy = float((y_true == y_pred).sum() / len(y_true))
    report: dict[str, Any] = {}
    f1_values: list[float] = []

    for stage_name, stage_id in STAGE_TO_ID.items():
        tp = int(((y_true == stage_id) & (y_pred == stage_id)).sum())
        fp = int(((y_true != stage_id) & (y_pred == stage_id)).sum())
        fn = int(((y_true == stage_id) & (y_pred != stage_id)).sum())

        precision = tp / (tp + fp) if tp + fp > 0 else 0.0
        recall = tp / (tp + fn) if tp + fn > 0 else 0.0
        f1 = (
            2 * precision * recall / (precision + recall)
            if precision + recall > 0
            else 0.0
        )
        support = int((y_true == stage_id).sum())

        report[stage_name] = {
            "precision": precision,
            "recall": recall,
            "f1-score": f1,
            "support": support,
        }
        f1_values.append(f1)

    macro_f1 = float(sum(f1_values) / len(f1_values))
    return {
        "accuracy": accuracy,
        "macro_f1": macro_f1,
        "report": report,
    }


def compute_confusion_matrix(
    y_true: NDArray[np.int64], y_pred: NDArray[np.int64]
) -> dict[str, Any]:
    label_ids = [STAGE_TO_ID[stage] for stage in STAGE_ORDER]
    matrix = np.zeros((len(label_ids), len(label_ids)), dtype=np.int64)
    for true_label, pred_label in zip(y_true.tolist(), y_pred.tolist()):
        if true_label not in label_ids or pred_label not in label_ids:
            continue
        i = label_ids.index(int(true_label))
        j = label_ids.index(int(pred_label))
        matrix[i, j] += 1
    return {
        "labels": STAGE_ORDER,
        "matrix": matrix.tolist(),
    }


def oversample_minority_classes(
    x: NDArray[np.float32], y: NDArray[np.int64], seed: int
) -> tuple[NDArray[np.float32], NDArray[np.int64], dict[str, int]]:
    if len(x) == 0:
        return x, y, {}

    rng = np.random.default_rng(seed)
    class_ids = sorted(set(int(v) for v in y.tolist()))
    class_indices = {cid: np.where(y == cid)[0] for cid in class_ids}
    max_count = max(len(indexes) for indexes in class_indices.values())
    target_count = max(1, int(max_count * 0.65))

    resampled_indexes: list[int] = []
    counts: dict[str, int] = {}
    for cid in class_ids:
        indexes = class_indices[cid]
        if len(indexes) == 0:
            continue
        if len(indexes) < target_count:
            sampled = rng.choice(indexes, size=target_count, replace=True)
        else:
            sampled = indexes
        resampled_indexes.extend(int(v) for v in sampled.tolist())
        counts[ID_TO_STAGE[cid]] = int(len(sampled))

    rng.shuffle(resampled_indexes)
    picked = np.asarray(resampled_indexes, dtype=np.int64)
    return x[picked], y[picked], counts


def evaluate_model(
    model: RandomForestClassifier,
    x: NDArray[np.float32],
    y: NDArray[np.int64],
) -> dict[str, Any]:
    if len(x) == 0:
        return {
            "samples": 0,
            "accuracy": None,
            "macro_f1": None,
            "report": {},
        }

    y_pred = predict_model(model, x)
    metrics = compute_metrics(y, y_pred)
    confusion = compute_confusion_matrix(y, y_pred)
    return {
        "samples": int(len(x)),
        "accuracy": metrics["accuracy"],
        "macro_f1": metrics["macro_f1"],
        "report": metrics["report"],
        "confusion_matrix": confusion,
    }


def main() -> None:
    args = parse_args()

    manifests_dir = Path(str(args.manifests_dir))
    output_dir = Path(str(args.output_dir))
    output_dir.mkdir(parents=True, exist_ok=True)

    train_manifest = manifests_dir / str(args.train_manifest)
    val_manifest = manifests_dir / str(args.val_manifest)
    test_manifest = manifests_dir / str(args.test_manifest)

    x_train, y_train = load_matrix_from_manifest(train_manifest)
    x_val, y_val = load_matrix_from_manifest(val_manifest)
    x_test, y_test = load_matrix_from_manifest(test_manifest)

    if len(x_train) == 0:
        report = {
            "status": "no_labeled_samples",
            "message": "train manifest has no labeled epochs; add public labeled dataset first",
            "counts": {
                "train": int(len(x_train)),
                "val": int(len(x_val)),
                "test": int(len(x_test)),
            },
        }
        with (output_dir / "baseline_report.json").open(
            "w", encoding="utf-8"
        ) as handle:
            json.dump(report, handle, indent=2, ensure_ascii=False)
        print(json.dumps(report, indent=2, ensure_ascii=False))
        return

    x_train_balanced, y_train_balanced, balanced_counts = oversample_minority_classes(
        x_train, y_train, seed=int(args.random_seed)
    )

    model = train_model(x_train_balanced, y_train_balanced)
    train_metrics = evaluate_model(model, x_train, y_train)
    val_metrics = evaluate_model(model, x_val, y_val)
    test_metrics = evaluate_model(model, x_test, y_test)

    report = {
        "status": "ok",
        "model": {
            "type": "RandomForestClassifier",
            "features": int(x_train.shape[1]) if len(x_train) > 0 else 51,
            "stages": STAGE_ORDER,
            "training": {
                "class_imbalance": "random_oversampling + class_weight",
                "balanced_train_samples": int(len(x_train_balanced)),
                "balanced_stage_counts": balanced_counts,
            },
        },
        "metrics": {
            "train": train_metrics,
            "val": val_metrics,
            "test": test_metrics,
        },
    }

    centroid_payload: dict[str, Any] = {
        "model_type": "RandomForestClassifier",
        "n_estimators": 500,
        "random_state": 42,
        "class_weight": "balanced_subsample",
        "features": int(x_train.shape[1]) if len(x_train) > 0 else 51,
    }
    with (output_dir / "baseline_model.json").open("w", encoding="utf-8") as handle:
        json.dump(centroid_payload, handle, ensure_ascii=False)

    with (output_dir / "baseline_report.json").open("w", encoding="utf-8") as handle:
        json.dump(report, handle, indent=2, ensure_ascii=False)

    print("Baseline training completed")
    print(json.dumps(report["metrics"], indent=2, ensure_ascii=False))


if __name__ == "__main__":
    main()
