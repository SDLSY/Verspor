from __future__ import annotations

from pathlib import Path
from datetime import datetime, UTC
from pipeline.common.io import write_json


def run(output_dir: Path) -> Path:
    model_dir = output_dir / "models"
    model_dir.mkdir(parents=True, exist_ok=True)
    model_file = model_dir / "model.bin"
    model_file.write_bytes(b"vespero-model-placeholder")

    write_json(
        output_dir / "cards" / "model_card.json",
        {
            "model_version": "v2.0.0",
            "feature_schema_version": "v2.0.0",
            "created_at": datetime.now(UTC).isoformat(),
            "runtime": "python-baseline",
        },
    )
    return model_file
