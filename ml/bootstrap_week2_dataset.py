from __future__ import annotations

import argparse
import json
from pathlib import Path

import numpy as np


STAGES = ["WAKE", "N1", "N2", "N3", "REM"]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Create bootstrap labeled and unlabeled datasets for Week2"
    )
    _ = parser.add_argument("--output_root", type=str, default="./data")
    _ = parser.add_argument("--labeled_subjects", type=int, default=12)
    _ = parser.add_argument("--unlabeled_subjects", type=int, default=6)
    _ = parser.add_argument("--sessions_per_subject", type=int, default=2)
    _ = parser.add_argument("--epochs_per_session", type=int, default=160)
    _ = parser.add_argument("--seed", type=int, default=42)
    return parser.parse_args()


def clamp(value: float, low: float, high: float) -> float:
    return max(low, min(high, value))


def stage_probs(epoch_index: int, total_epochs: int) -> np.ndarray:
    phase = epoch_index / max(total_epochs, 1)
    if phase < 0.12:
        probs = np.array([0.60, 0.25, 0.10, 0.03, 0.02], dtype=np.float64)
    elif phase < 0.55:
        probs = np.array([0.08, 0.16, 0.48, 0.20, 0.08], dtype=np.float64)
    elif phase < 0.82:
        probs = np.array([0.06, 0.12, 0.40, 0.10, 0.32], dtype=np.float64)
    else:
        probs = np.array([0.34, 0.25, 0.26, 0.05, 0.10], dtype=np.float64)
    return probs / probs.sum()


def sample_stage(rng: np.random.Generator, epoch_index: int, total_epochs: int) -> str:
    probs = stage_probs(epoch_index, total_epochs)
    return str(rng.choice(STAGES, p=probs))


def stage_base(stage: str) -> dict[str, float]:
    if stage == "WAKE":
        return {
            "hr": 72.0,
            "spo2": 97.0,
            "hrv": 42.0,
            "temp": 36.6,
            "motion": 6.0,
            "anomaly": 0.45,
        }
    if stage == "N1":
        return {
            "hr": 66.0,
            "spo2": 96.0,
            "hrv": 48.0,
            "temp": 36.5,
            "motion": 4.5,
            "anomaly": 0.35,
        }
    if stage == "N2":
        return {
            "hr": 61.0,
            "spo2": 97.0,
            "hrv": 56.0,
            "temp": 36.4,
            "motion": 2.8,
            "anomaly": 0.22,
        }
    if stage == "N3":
        return {
            "hr": 55.0,
            "spo2": 97.5,
            "hrv": 66.0,
            "temp": 36.3,
            "motion": 1.7,
            "anomaly": 0.12,
        }
    return {
        "hr": 59.0,
        "spo2": 96.5,
        "hrv": 52.0,
        "temp": 36.45,
        "motion": 2.4,
        "anomaly": 0.18,
    }


def make_epoch(
    rng: np.random.Generator,
    start_ms: int,
    epoch_index: int,
    total_epochs: int,
    with_label: bool,
) -> dict[str, object]:
    stage = sample_stage(rng, epoch_index, total_epochs)
    base = stage_base(stage)

    hr = clamp(base["hr"] + rng.normal(0.0, 3.0), 42.0, 140.0)
    spo2 = clamp(base["spo2"] + rng.normal(0.0, 1.0), 85.0, 100.0)
    hrv = clamp(base["hrv"] + rng.normal(0.0, 6.0), 10.0, 120.0)
    temp = clamp(base["temp"] + rng.normal(0.0, 0.08), 35.0, 38.5)
    motion = clamp(base["motion"] + rng.normal(0.0, 1.2), 0.0, 16.0)
    anomaly = clamp(base["anomaly"] + rng.normal(0.0, 0.07), 0.0, 1.0)

    ppg_value = int(clamp(1100 + (hr - 50) * 8 + rng.normal(0.0, 40.0), 700.0, 1800.0))
    window_start = start_ms + epoch_index * 30_000
    window_end = window_start + 30_000

    epoch: dict[str, object] = {
        "windowStart": window_start,
        "windowEnd": window_end,
        "hrFeatures": {
            "heartRate": round(hr, 2),
            "heartRateAvg": round(clamp(hr + rng.normal(0.0, 1.4), 42.0, 140.0), 2),
            "heartRateMin": round(
                clamp(hr - abs(rng.normal(2.0, 1.5)), 40.0, 138.0), 2
            ),
            "heartRateMax": round(
                clamp(hr + abs(rng.normal(3.0, 1.8)), 45.0, 145.0), 2
            ),
        },
        "spo2Features": {
            "bloodOxygen": round(spo2, 2),
            "bloodOxygenAvg": round(clamp(spo2 + rng.normal(0.0, 0.4), 85.0, 100.0), 2),
            "bloodOxygenMin": round(
                clamp(spo2 - abs(rng.normal(0.8, 0.6)), 82.0, 100.0), 2
            ),
        },
        "hrvFeatures": {
            "hrv": round(hrv, 2),
            "rmssd": round(clamp(hrv * 0.72 + rng.normal(0.0, 3.0), 6.0, 110.0), 2),
            "sdnn": round(clamp(hrv * 0.86 + rng.normal(0.0, 3.5), 8.0, 130.0), 2),
            "lfHfRatio": round(clamp(1.2 + rng.normal(0.0, 0.35), 0.2, 4.0), 3),
        },
        "tempFeatures": {
            "temperature": round(temp, 3),
            "temperatureAvg": round(clamp(temp + rng.normal(0.0, 0.03), 35.0, 38.5), 3),
            "temperatureTrend": round(rng.normal(0.0, 0.01), 4),
        },
        "motionFeatures": {
            "motionIntensity": round(motion, 3),
            "accelerometer": [round(float(rng.normal(0.0, 0.2)), 4) for _ in range(3)],
            "gyroscope": [round(float(rng.normal(0.0, 0.1)), 4) for _ in range(3)],
        },
        "ppgFeatures": {
            "ppgValue": ppg_value,
            "ppg": [
                int(clamp(ppg_value + rng.normal(0.0, 15.0), 700.0, 1800.0)),
                int(clamp(ppg_value + rng.normal(0.0, 15.0), 700.0, 1800.0)),
                int(clamp(ppg_value + rng.normal(0.0, 15.0), 700.0, 1800.0)),
            ],
        },
        "edgeAnomalySignal": round(anomaly, 4),
    }

    if with_label:
        epoch["sleep_stage_5"] = stage

    return epoch


def write_jsonl(path: Path, rows: list[dict[str, object]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as handle:
        for row in rows:
            _ = handle.write(json.dumps(row, ensure_ascii=False) + "\n")


def build_dataset(
    root: Path,
    prefix: str,
    subjects: int,
    sessions_per_subject: int,
    epochs_per_session: int,
    seed: int,
    with_label: bool,
) -> int:
    rng = np.random.default_rng(seed)
    total_sessions = 0
    for subject_index in range(subjects):
        subject_id = f"{prefix}_sub_{subject_index + 1:03d}"
        subject_dir = root / subject_id
        for session_index in range(sessions_per_subject):
            session_id = f"{subject_id}_sess_{session_index + 1:02d}"
            start_ms = int(
                1_760_000_000_000 + subject_index * 86_400_000 + session_index * 30_000
            )
            rows = [
                make_epoch(rng, start_ms, epoch_idx, epochs_per_session, with_label)
                for epoch_idx in range(epochs_per_session)
            ]
            write_jsonl(subject_dir / f"{session_id}.jsonl", rows)
            total_sessions += 1
    return total_sessions


def main() -> None:
    args = parse_args()
    output_root = Path(str(args.output_root))
    labeled_dir = output_root / "public_labeled"
    unlabeled_dir = output_root / "ring_unlabeled"

    labeled_sessions = build_dataset(
        labeled_dir,
        prefix="public",
        subjects=int(args.labeled_subjects),
        sessions_per_subject=int(args.sessions_per_subject),
        epochs_per_session=int(args.epochs_per_session),
        seed=int(args.seed),
        with_label=True,
    )

    unlabeled_sessions = build_dataset(
        unlabeled_dir,
        prefix="ring",
        subjects=int(args.unlabeled_subjects),
        sessions_per_subject=int(args.sessions_per_subject),
        epochs_per_session=int(args.epochs_per_session),
        seed=int(args.seed) + 997,
        with_label=False,
    )

    summary = {
        "status": "ok",
        "public_labeled_sessions": labeled_sessions,
        "ring_unlabeled_sessions": unlabeled_sessions,
        "epoch_seconds": 30,
        "stages": STAGES,
    }

    summary_path = output_root / "bootstrap_summary.json"
    with summary_path.open("w", encoding="utf-8") as handle:
        json.dump(summary, handle, indent=2, ensure_ascii=False)

    print(json.dumps(summary, indent=2, ensure_ascii=False))


if __name__ == "__main__":
    main()
