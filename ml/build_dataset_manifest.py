from __future__ import annotations

import argparse
import json
import random
from collections.abc import Iterable
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import cast


SUPPORTED_SUFFIXES = {".json", ".jsonl", ".csv", ".parquet", ".npz", ".npy"}


@dataclass
class SessionRecord:
    source: str
    subject_id: str
    session_id: str
    path: str
    label_available: bool
    feature_schema_version: str = "v1"
    label_schema_version: str = "aasm5-v1"


def infer_subject_id(file_path: Path) -> str:
    if file_path.parent.name and file_path.parent.name != file_path.anchor:
        return file_path.parent.name
    return file_path.stem.split("_")[0]


def infer_session_id(file_path: Path) -> str:
    return file_path.stem


def discover_records(
    root_dir: Path, source: str, label_available: bool
) -> list[SessionRecord]:
    if not root_dir.exists():
        return []

    records: list[SessionRecord] = []
    for file_path in root_dir.rglob("*"):
        if (
            not file_path.is_file()
            or file_path.suffix.lower() not in SUPPORTED_SUFFIXES
        ):
            continue

        subject_id = infer_subject_id(file_path)
        session_id = infer_session_id(file_path)
        records.append(
            SessionRecord(
                source=source,
                subject_id=subject_id,
                session_id=session_id,
                path=str(file_path.resolve()),
                label_available=label_available,
            )
        )

    return records


def group_by_subject(
    records: Iterable[SessionRecord],
) -> dict[str, list[SessionRecord]]:
    grouped: dict[str, list[SessionRecord]] = {}
    for record in records:
        if record.subject_id not in grouped:
            grouped[record.subject_id] = []
        grouped[record.subject_id].append(record)
    return grouped


def split_subjects(subject_ids: list[str], seed: int) -> dict[str, list[str]]:
    rng = random.Random(seed)
    shuffled = list(subject_ids)
    rng.shuffle(shuffled)

    total = len(shuffled)
    if total == 0:
        return {"train": [], "val": [], "test": []}

    if total == 1:
        return {"train": shuffled, "val": [], "test": []}

    if total == 2:
        return {"train": [shuffled[0]], "val": [], "test": [shuffled[1]]}

    val_count = max(1, int(total * 0.15))
    test_count = max(1, int(total * 0.15))
    train_count = total - val_count - test_count

    if train_count < 1:
        train_count = 1
        remaining = total - train_count
        val_count = max(1, remaining // 2)
        test_count = remaining - val_count

    train_end = train_count
    val_end = train_end + val_count

    return {
        "train": shuffled[:train_end],
        "val": shuffled[train_end:val_end],
        "test": shuffled[val_end:],
    }


def flatten_subject_split(
    grouped: dict[str, list[SessionRecord]], split_subjects_map: dict[str, list[str]]
) -> dict[str, list[SessionRecord]]:
    output: dict[str, list[SessionRecord]] = {"train": [], "val": [], "test": []}
    for split, subjects in split_subjects_map.items():
        for subject_id in subjects:
            output[split].extend(grouped.get(subject_id, []))
    return output


def write_jsonl(path: Path, records: list[SessionRecord]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as handle:
        for record in records:
            _ = handle.write(json.dumps(asdict(record), ensure_ascii=False) + "\n")


def build_summary(
    public_records: list[SessionRecord],
    ring_records: list[SessionRecord],
    split_records: dict[str, list[SessionRecord]],
) -> dict[str, object]:
    labeled_subjects = sorted({record.subject_id for record in public_records})
    unlabeled_subjects = sorted({record.subject_id for record in ring_records})

    return {
        "protocol": {
            "epoch_seconds": 30,
            "stages": ["WAKE", "N1", "N2", "N3", "REM"],
            "split_strategy": "subject-wise",
        },
        "counts": {
            "labeled_sessions": len(public_records),
            "unlabeled_sessions": len(ring_records),
            "train_sessions": len(split_records["train"]),
            "val_sessions": len(split_records["val"]),
            "test_sessions": len(split_records["test"]),
            "labeled_subjects": len(labeled_subjects),
            "unlabeled_subjects": len(unlabeled_subjects),
        },
        "subjects": {
            "labeled": labeled_subjects,
            "unlabeled": unlabeled_subjects,
        },
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Build dataset manifests for sleep staging"
    )
    _ = parser.add_argument(
        "--public_dir",
        type=str,
        default="./data/public_labeled",
        help="Labeled data root",
    )
    _ = parser.add_argument(
        "--ring_dir",
        type=str,
        default="./data/ring_unlabeled",
        help="Unlabeled ring data root",
    )
    _ = parser.add_argument(
        "--output_dir",
        type=str,
        default="./data/manifests",
        help="Manifest output directory",
    )
    _ = parser.add_argument(
        "--seed", type=int, default=42, help="Random seed for subject split"
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()

    public_dir = Path(cast(str, args.public_dir))
    ring_dir = Path(cast(str, args.ring_dir))
    output_dir = Path(cast(str, args.output_dir))

    public_records = discover_records(
        public_dir, source="public_labeled", label_available=True
    )
    ring_records = discover_records(
        ring_dir, source="ring_unlabeled", label_available=False
    )

    grouped = group_by_subject(public_records)
    subject_ids = sorted(grouped.keys())
    split_map: dict[str, list[str]] = (
        split_subjects(subject_ids, cast(int, args.seed))
        if subject_ids
        else {"train": [], "val": [], "test": []}
    )
    split_records = flatten_subject_split(grouped, split_map)

    write_jsonl(output_dir / "train_manifest.jsonl", split_records["train"])
    write_jsonl(output_dir / "val_manifest.jsonl", split_records["val"])
    write_jsonl(output_dir / "test_manifest.jsonl", split_records["test"])
    write_jsonl(output_dir / "unlabeled_manifest.jsonl", ring_records)

    summary = build_summary(public_records, ring_records, split_records)
    with (output_dir / "summary.json").open("w", encoding="utf-8") as handle:
        json.dump(summary, handle, indent=2, ensure_ascii=False)

    print("Manifest build complete")
    print(json.dumps(summary["counts"], indent=2, ensure_ascii=False))


if __name__ == "__main__":
    main()
