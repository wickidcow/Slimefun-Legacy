#!/usr/bin/env python3
"""Reject legacy Bungee/Spigot rich-message APIs in production Java sources."""

from __future__ import annotations

import sys
from pathlib import Path

FORBIDDEN = (
    "net.md_5.bungee.api.chat",
    ".spigot().sendMessage",
)


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
    source_root = root / "src" / "main" / "java"
    failures: list[str] = []

    for path in source_root.rglob("*.java"):
        source = path.read_text(encoding="utf-8")
        for token in FORBIDDEN:
            if token in source:
                failures.append(
                    f"{path.relative_to(root)} still uses legacy rich-message API: {token}"
                )

    if failures:
        print("Adventure message modernization verification: FAIL")
        for failure in failures:
            print(f"- {failure}")
        return 1

    print("Adventure message modernization verification: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
