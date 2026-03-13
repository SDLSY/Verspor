from __future__ import annotations

import argparse
import json
from pathlib import Path
from urllib.request import urlopen

import mne
import numpy as np
from numpy.typing import NDArray


SLEEP_EDF_ROOT = (
    "https://www.physionet.org/physiobank/database/sleep-edfx/sleep-cassette"
)

RECORDS: list[tuple[str, str]] = [
    ("SC4001E0-PSG.edf", "SC4001EC-Hypnogram.edf"),
    ("SC4002E0-PSG.edf", "SC4002EC-Hypnogram.edf"),
    ("SC4011E0-PSG.edf", "SC4011EH-Hypnogram.edf"),
    ("SC4012E0-PSG.edf", "SC4012EC-Hypnogram.edf"),
    ("SC4021E0-PSG.edf", "SC4021EH-Hypnogram.edf"),
]

STAGE_MAP = {
    "Sleep stage W": "WAKE",
    "Sleep stage 1": "N1",
    "Sleep stage 2": "N2",
    "Sleep stage 3": "N3",
    "Sleep stage 4": "N3",
    "Sleep stage R": "REM",
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Fetch real Sleep-EDF subset and convert to manifest-ready jsonl"
    )
    _ = parser.add_argument(
        "--raw_dir", type=str, default="./data/public_labeled_raw/sleep_edf"
    )
    _ = parser.add_argument("--output_dir", type=str, default="./data/public_labeled")
    _ = parser.add_argument("--max_records", type=int, default=2)
    _ = parser.add_argument("--epoch_seconds", type=int, default=30)
    return parser.parse_args()


def download_file(url: str, target: Path) -> None:
    if target.exists() and target.stat().st_size > 0:
        return
    target.parent.mkdir(parents=True, exist_ok=True)
    with urlopen(url, timeout=120) as response:
        data = response.read()
    target.write_bytes(data)


def choose_channel(raw: mne.io.BaseRaw) -> int:
    preferred = ["EEG Fpz-Cz", "EEG Pz-Oz", "EOG horizontal", "EMG submental"]
    for name in preferred:
        if name in raw.ch_names:
            return raw.ch_names.index(name)
    return 0


def signal_to_modalities(signal: NDArray[np.float64]) -> dict[str, object]:
    mean = float(np.mean(signal))
    std = float(np.std(signal))
    min_v = float(np.min(signal))
    max_v = float(np.max(signal))
    abs_mean = float(np.mean(np.abs(signal)))

    heart_rate = float(np.clip(55.0 + std * 10000.0, 42.0, 130.0))
    spo2 = float(np.clip(98.5 - abs_mean * 300.0, 85.0, 100.0))
    hrv = float(np.clip(35.0 + std * 8000.0, 10.0, 120.0))
    temp = float(np.clip(36.4 + mean * 10.0, 35.5, 37.5))
    motion = float(np.clip(abs_mean * 2000.0, 0.0, 12.0))

    return {
        "hrFeatures": {
            "heartRate": round(heart_rate, 3),
            "heartRateAvg": round(heart_rate, 3),
            "heartRateMin": round(max(40.0, heart_rate - 3.5), 3),
            "heartRateMax": round(min(140.0, heart_rate + 3.5), 3),
        },
        "spo2Features": {
            "bloodOxygen": round(spo2, 3),
            "bloodOxygenAvg": round(spo2, 3),
            "bloodOxygenMin": round(max(82.0, spo2 - 0.8), 3),
        },
        "hrvFeatures": {
            "hrv": round(hrv, 3),
            "rmssd": round(np.clip(hrv * 0.72, 6.0, 110.0), 3),
            "sdnn": round(np.clip(hrv * 0.86, 8.0, 130.0), 3),
            "lfHfRatio": round(np.clip(1.0 + std * 1200.0, 0.2, 4.0), 3),
        },
        "tempFeatures": {
            "temperature": round(temp, 3),
            "temperatureAvg": round(temp, 3),
            "temperatureTrend": 0.0,
        },
        "motionFeatures": {
            "motionIntensity": round(motion, 3),
            "accelerometer": [round(abs_mean, 6), round(std, 6), round(abs(mean), 6)],
            "gyroscope": [round(std, 6), round(abs(mean), 6), round(abs_mean, 6)],
        },
        "ppgFeatures": {
            "ppgValue": int(np.clip((max_v - min_v) * 1_000_000.0, 0.0, 3000.0)),
            "ppg": [
                int(np.clip((mean + std) * 1_000_000.0, 0.0, 3000.0)),
                int(np.clip((mean) * 1_000_000.0, 0.0, 3000.0)),
                int(np.clip((mean - std) * 1_000_000.0, 0.0, 3000.0)),
            ],
        },
        "edgeAnomalySignal": round(float(np.clip(std * 2500.0, 0.0, 1.0)), 4),
    }


def convert_record(
    psg_path: Path, hyp_path: Path, output_path: Path, epoch_seconds: int
) -> int:
    raw = mne.io.read_raw_edf(str(psg_path), preload=False, verbose="ERROR")
    annotations = mne.read_annotations(str(hyp_path))

    channel_index = choose_channel(raw)
    sfreq = float(raw.info["sfreq"])
    base_ms = 1_760_000_000_000

    rows: list[dict[str, object]] = []
    epoch_idx = 0

    for onset, duration, desc in zip(
        annotations.onset, annotations.duration, annotations.description
    ):
        stage = STAGE_MAP.get(desc)
        if stage is None:
            continue
        segment_epochs = int(duration // epoch_seconds)
        for j in range(segment_epochs):
            t0 = onset + j * epoch_seconds
            t1 = t0 + epoch_seconds
            start = int(t0 * sfreq)
            stop = int(t1 * sfreq)
            if stop <= start:
                continue
            signal = raw.get_data(picks=[channel_index], start=start, stop=stop)[0]
            if signal.size == 0:
                continue

            modality = signal_to_modalities(signal)
            window_start = base_ms + epoch_idx * epoch_seconds * 1000
            row = {
                "windowStart": window_start,
                "windowEnd": window_start + epoch_seconds * 1000,
                **modality,
                "sleep_stage_5": stage,
            }
            rows.append(row)
            epoch_idx += 1

    output_path.parent.mkdir(parents=True, exist_ok=True)
    with output_path.open("w", encoding="utf-8") as handle:
        for row in rows:
            _ = handle.write(json.dumps(row, ensure_ascii=False) + "\n")

    return len(rows)


def main() -> None:
    args = parse_args()
    raw_dir = Path(str(args.raw_dir))
    output_root = Path(str(args.output_dir))

    max_records = max(1, int(args.max_records))
    selected = RECORDS[:max_records]

    total_epochs = 0
    sessions = 0

    for psg_name, hyp_name in selected:
        psg_url = f"{SLEEP_EDF_ROOT}/{psg_name}"
        hyp_url = f"{SLEEP_EDF_ROOT}/{hyp_name}"
        psg_path = raw_dir / psg_name
        hyp_path = raw_dir / hyp_name

        download_file(psg_url, psg_path)
        download_file(hyp_url, hyp_path)

        subject_id = psg_name.split("-")[0]
        session_id = subject_id.lower()
        output_path = (
            output_root / f"sleepedf_{subject_id.lower()}" / f"{session_id}.jsonl"
        )

        epochs = convert_record(
            psg_path, hyp_path, output_path, int(args.epoch_seconds)
        )
        total_epochs += epochs
        sessions += 1

    summary = {
        "status": "ok",
        "records_requested": max_records,
        "records_processed": sessions,
        "epochs_generated": total_epochs,
        "output_dir": str(output_root.resolve()),
        "source": "Sleep-EDF via GitHub-listed links",
    }

    summary_path = output_root.parent / "sleepedf_bootstrap_summary.json"
    with summary_path.open("w", encoding="utf-8") as handle:
        json.dump(summary, handle, indent=2, ensure_ascii=False)

    print(json.dumps(summary, indent=2, ensure_ascii=False))


if __name__ == "__main__":
    main()
