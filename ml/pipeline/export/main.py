from __future__ import annotations

from pathlib import Path
from pipeline.common.io import write_json


def run(output_dir: Path) -> Path:
    tflite = output_dir / "models" / "model.tflite"
    tflite.parent.mkdir(parents=True, exist_ok=True)
    tflite.write_bytes(b"vespero-tflite-placeholder")

    write_json(
        output_dir / "schemas" / "feature_schema.json",
        {
            "feature_schema_version": "v2.0.0",
            "features": [
                "heart_rate",
                "hrv",
                "blood_oxygen",
                "temperature",
                "motion_intensity",
                "ppg_value",
            ],
        },
    )
    return tflite
