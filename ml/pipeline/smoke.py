from __future__ import annotations

import subprocess
import sys
from pathlib import Path


def run() -> int:
    root = Path(__file__).resolve().parents[1]
    cmd = [sys.executable, str(root / "pipeline" / "cli.py"), "--stage", "all", "--output", str(root / "artifacts")]
    return subprocess.call(cmd, cwd=str(root))


if __name__ == "__main__":
    raise SystemExit(run())
