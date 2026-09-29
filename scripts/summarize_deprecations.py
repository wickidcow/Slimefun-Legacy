#!/usr/bin/env python3
"""Create a stable, human-readable report from javac deprecation/removal warnings."""
from __future__ import annotations

import argparse
import re
from collections import Counter
from pathlib import Path

WARNING = re.compile(r"^(.*?\.java):(\d+): warning: \[(deprecation|removal)\] (.*)$")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("log", type=Path)
    parser.add_argument("--output", type=Path, default=Path("build/reports/deprecations.md"))
    parser.add_argument(
        "--fail-on-warnings",
        action="store_true",
        help="Return a non-zero exit status when explicit deprecation/removal warnings are present.",
    )
    args = parser.parse_args()

    text = args.log.read_text(encoding="utf-8", errors="replace") if args.log.exists() else ""
    warnings: list[tuple[str, int, str, str]] = []
    for line in text.splitlines():
        match = WARNING.match(line.strip())
        if match:
            warnings.append((match.group(1), int(match.group(2)), match.group(3), match.group(4)))

    per_file = Counter(path for path, _, _, _ in warnings)
    per_category = Counter(category for _, _, category, _ in warnings)
    lines = [
        "# Slimefun Legacy deprecation/removal report",
        "",
        f"Detected **{len(warnings)}** explicit javac compatibility warning(s).",
        f"- Deprecation: **{per_category.get('deprecation', 0)}**",
        f"- Removal: **{per_category.get('removal', 0)}**",
        "",
    ]
    if warnings:
        lines.extend(["## Warnings by source file", ""])
        lines.extend(f"- `{path}`: {count}" for path, count in sorted(per_file.items()))
        lines.extend(["", "## Detailed warnings", ""])
        lines.extend(
            f"- `{path}:{line}` — **{category}** — {message}"
            for path, line, category, message in warnings
        )
    else:
        lines.append("No explicit deprecation or removal warnings were emitted.")
    lines.extend(
        [
            "",
            "> This report is informational. Public compatibility bridges may remain deprecated intentionally; new internal use should be reduced over time.",
        ]
    )
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(
        "Compatibility warning report written with "
        f"{len(warnings)} warning(s): "
        f"{per_category.get('deprecation', 0)} deprecation, "
        f"{per_category.get('removal', 0)} removal."
    )
    if args.fail_on_warnings and warnings:
        print("Compatibility warning gate: FAIL")
        return 1

    if args.fail_on_warnings:
        print("Compatibility warning gate: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
