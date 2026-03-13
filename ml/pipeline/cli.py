from __future__ import annotations

import argparse
import sys
from pathlib import Path

CURRENT_DIR = Path(__file__).resolve().parent
PROJECT_ROOT = CURRENT_DIR.parent
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from pipeline.train.main import run as run_train
from pipeline.export.main import run as run_export
from pipeline.infer.main import run as run_infer


def main() -> None:
    parser = argparse.ArgumentParser(description="VesperO ML pipeline")
    parser.add_argument("--stage", choices=["train", "export", "infer", "all"], default="all")
    parser.add_argument("--output", default="artifacts")
    args = parser.parse_args()

    output_dir = Path(args.output)

    if args.stage in {"train", "all"}:
        run_train(output_dir)
    if args.stage in {"export", "all"}:
        run_export(output_dir)
    if args.stage in {"infer", "all"}:
        run_infer(output_dir)


if __name__ == "__main__":
    main()
