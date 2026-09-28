#!/usr/bin/env python3
"""Prevent deprecated Bukkit plugin-description metadata access from returning."""

from __future__ import annotations

import sys
from pathlib import Path

FORBIDDEN = (
    ".getDescription().getVersion()",
    ".getDescription().getAuthors()",
    ".getDescription().getDepend()",
    ".getDescription().getSoftDepend()",
)


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
    source_root = root / "src" / "main" / "java"
    failures: list[str] = []

    for path in source_root.rglob("*.java"):
        text = path.read_text(encoding="utf-8")
        for token in FORBIDDEN:
            if token in text:
                failures.append(f"{path.relative_to(root)} still uses deprecated plugin metadata access: {token}")

    if failures:
        print("Plugin metadata modernization verification: FAIL")
        for failure in failures:
            print(f"- {failure}")
        return 1

    print("Plugin metadata modernization verification: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
