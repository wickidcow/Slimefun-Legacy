#!/usr/bin/env python3
"""Run every Slimefun Legacy source invariant from one command."""

from __future__ import annotations

import subprocess
import sys
from pathlib import Path


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
    scripts = (
        "verify_english.py",
        "verify_chunk_load_threading.py",
        "check_api_annotations.py",
        "verify_part2.py",
        "verify_part3.py",
        "verify_part4.py",
        "verify_folia_phase1.py",
        "verify_enhanced_guide.py",
        "verify_enhanced_guide_phase2.py",
        "verify_enhanced_guide_phase3.py",
        "verify_enhanced_guide_phase3_1.py",
        "verify_gugu_sync.py",
        "verify_paper_purpur_compat.py",
    )

    for script_name in scripts:
        script = root / "scripts" / script_name
        print(f"\n==> {script_name}", flush=True)
        result = subprocess.run([sys.executable, str(script), str(root)], cwd=root, check=False)
        if result.returncode != 0:
            return result.returncode

    print("\nAll Slimefun Legacy verification checks passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
