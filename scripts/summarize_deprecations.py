#!/usr/bin/env python3
"""Create a human-readable javac warning report without treating absent evidence as clean."""
from __future__ import annotations

import argparse
import re
from collections import Counter
from pathlib import Path

WARNING = re.compile(r"^(.*?\.java):(\d+): warning: \[(deprecation|removal)\] (.*)$")
BUILD_SUCCESS = re.compile(r"^BUILD SUCCESSFUL(?:\s|$)", re.MULTILINE)
BUILD_FAILURE = re.compile(r"^BUILD FAILED(?:\s|$)", re.MULTILINE)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("log", type=Path)
    parser.add_argument("--output", type=Path, default=Path("build/reports/deprecations.md"))
    parser.add_argument(
        "--fail-on-warnings",
        action="store_true",
        help="Return a non-zero exit status when explicit deprecation/removal warnings are present.",
    )
    parser.add_argument(
        "--require-successful-build",
        action="store_true",
        help="Require a completed successful Gradle build in addition to a readable, non-empty log.",
    )
    args = parser.parse_args()

    text = ""
    evidence_error = None
    try:
        text = args.log.read_text(encoding="utf-8", errors="replace")
        if not text.strip():
            evidence_error = "The compiler log is empty; compatibility warnings cannot be evaluated."
    except OSError as error:
        evidence_error = f"The compiler log could not be read ({type(error).__name__}); compatibility warnings cannot be evaluated."

    if evidence_error is None and args.require_successful_build:
        if BUILD_FAILURE.search(text):
            evidence_error = "The compiler log records a failed Gradle build."
        elif not BUILD_SUCCESS.search(text):
            evidence_error = "The compiler log does not record a completed successful Gradle build."

    # Replace any old report rather than leaving a stale zero-warning report behind.
    if evidence_error is not None:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(
            "# Slimefun Legacy deprecation/removal report\n\n"
            "**Validation unavailable or unsuccessful.**\n\n"
            f"{evidence_error}\n\n"
            "No zero-warning or successful-build claim can be made from this input.\n",
            encoding="utf-8",
        )
        print(f"Compatibility warning evidence: FAIL — {evidence_error}")
        if args.fail_on_warnings:
            print("Compatibility warning gate: FAIL")
        return 2

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
        lines.append("No explicit deprecation or removal warnings were found in the supplied log.")
    lines.extend(
        [
            "",
            "> Public compatibility bridges may remain deprecated intentionally. Warning counts alone do not prove runtime, persistence or cross-fork compatibility.",
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
