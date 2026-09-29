#!/usr/bin/env python3
"""Reject deprecated Paper MaterialTags usage in production Java sources."""

from __future__ import annotations

import sys
from pathlib import Path

FORBIDDEN = (
    "com.destroystokyo.paper.MaterialTags",
    "MaterialTags.",
)


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
    source_root = root / "src" / "main" / "java"
    failures: list[str] = []

    for java_file in source_root.rglob("*.java"):
        source = java_file.read_text(encoding="utf-8")
        for token in FORBIDDEN:
            if token in source:
                failures.append(
                    f"{java_file.relative_to(root)} still uses deprecated Paper MaterialTags API: {token}"
                )

    if failures:
        print("Paper MaterialTags modernization verification: FAIL")
        for failure in failures:
            print(f"- {failure}")
        return 1

    print("Paper MaterialTags modernization verification: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
