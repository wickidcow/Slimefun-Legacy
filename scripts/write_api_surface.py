#!/usr/bin/env python3
"""Write the compatibility-protected public API surface for a Slimefun JAR."""
from __future__ import annotations

import argparse
from pathlib import Path

from check_api_compatibility import public_signatures, write_surface


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("jar", type=Path)
    parser.add_argument("--output", type=Path, default=Path("build/reports/api-surface-candidate.txt"))
    args = parser.parse_args()
    signatures = public_signatures(args.jar)
    write_surface(args.output, signatures, "Candidate API surface")
    print(f"Wrote {len(signatures)} public API signatures to {args.output}.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
