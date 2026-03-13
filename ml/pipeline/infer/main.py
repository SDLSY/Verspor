from __future__ import annotations

from pathlib import Path
import json


def run(output_dir: Path) -> Path:
    result = {
        "model_version": "v2.0.0",
        "feature_schema_version": "v2.0.0",
        "sleep_stages_5": ["N2", "N3", "REM"],
        "anomaly_score": 24,
    }
    out = output_dir / "inference" / "latest.json"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return out
