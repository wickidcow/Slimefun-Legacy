#!/usr/bin/env python3
"""Create a stable, human-readable report from a javac -Xlint:deprecation build log."""
from __future__ import annotations

import argparse
import re
from collections import Counter
from pathlib import Path

WARNING = re.compile(r"^(.*?\.java):(\d+): warning: \[deprecation\] (.*)$")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("log", type=Path)
    parser.add_argument("--output", type=Path, default=Path("build/reports/deprecations.md"))
    args = parser.parse_args()

    text = args.log.read_text(encoding="utf-8", errors="replace") if args.log.exists() else ""
    warnings: list[tuple[str, int, str]] = []
    for line in text.splitlines():
        match = WARNING.match(line.strip())
        if match:
            warnings.append((match.group(1), int(match.group(2)), match.group(3)))

    per_file = Counter(path for path, _, _ in warnings)
    lines = [
        "# Slimefun Legacy deprecation report",
        "",
        f"Detected **{len(warnings)}** explicit `javac -Xlint:deprecation` warning(s).",
        "",
    ]
    if warnings:
        lines.extend(["## Warnings by source file", ""])
        lines.extend(f"- `{path}`: {count}" for path, count in sorted(per_file.items()))
        lines.extend(["", "## Detailed warnings", ""])
        lines.extend(f"- `{path}:{line}` — {message}" for path, line, message in warnings)
    else:
        lines.append("No explicit deprecation warnings were emitted.")
    lines.extend(
        [
            "",
            "> This report is informational in 4.1.16. Public compatibility bridges may remain deprecated intentionally; new internal use should be reduced over time.",
        ]
    )
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"Deprecation report written with {len(warnings)} warning(s).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
