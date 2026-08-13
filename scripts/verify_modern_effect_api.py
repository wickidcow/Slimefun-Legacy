#!/usr/bin/env python3
"""Prevent removed/deprecated Bukkit Effect constants from returning to production source."""

from __future__ import annotations

import sys
from pathlib import Path


FORBIDDEN = (
    "Effect.STEP_SOUND",
    "Effect.SMOKE",
)


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
    source_root = root / "src/main/java"

    if not source_root.is_dir():
        raise SystemExit("Modern effect API verification failed: missing src/main/java")

    failures: list[str] = []
    for path in sorted(source_root.rglob("*.java")):
        text = path.read_text(encoding="utf-8")
        for line_number, line in enumerate(text.splitlines(), start=1):
            for forbidden in FORBIDDEN:
                if forbidden in line:
                    relative = path.relative_to(root)
                    failures.append(f"{relative}:{line_number}: forbidden {forbidden}")

    if failures:
        raise SystemExit(
            "Modern effect API verification failed:\n" + "\n".join(f"- {failure}" for failure in failures)
        )

    print("Modern effect API verification passed: no STEP_SOUND or SMOKE Effect constants remain.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
