#!/usr/bin/env python3
"""Reject Bukkit metadata API use in Slimefun production sources."""

from __future__ import annotations

import sys
from pathlib import Path

FORBIDDEN = (
    "org.bukkit.metadata.",
    ".setMetadata(",
    ".getMetadata(",
    ".hasMetadata(",
    ".removeMetadata(",
)


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
    source_root = root / "src" / "main" / "java"
    failures: list[str] = []

    for path in source_root.rglob("*.java"):
        text = path.read_text(encoding="utf-8")
        for token in FORBIDDEN:
            if token in text:
                failures.append(
                    f"{path.relative_to(root)} still uses Bukkit metadata API: {token}"
                )

    if failures:
        print("Bukkit metadata modernization verification: FAIL")
        for failure in failures:
            print(f"- {failure}")
        return 1

    print("Bukkit metadata modernization verification: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
