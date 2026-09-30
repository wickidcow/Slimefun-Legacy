#!/usr/bin/env python3
"""Reject configuration-load failures in a completed, normalized server log.

This is a supplementary smoke gate, not a substitute for plugin-enable,
clean-shutdown, inventory-preservation, or gameplay checks.
"""
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

FAILURES = (
    re.compile(r"\bInvalidConfigurationException\b"),
    re.compile(r"\bsnakeyaml\.(?:parser\.ParserException|scanner\.ScannerException|constructor\.ConstructorException|error\.YAMLException)\b"),
    re.compile(r"\b(?:cannot|could not|unable to|failed to)\s+(?:load|parse|read)\b[^\r\n]*\.ya?ml\b", re.IGNORECASE),
    re.compile(r"\berror\s+(?:loading|parsing|reading)\b[^\r\n]*\.ya?ml\b", re.IGNORECASE),
)


def configuration_failures(text: str) -> list[tuple[int, str]]:
    """Return each failed line once, in its original order, without changing it."""
    return [
        (number, line)
        for number, line in enumerate(text.splitlines(), start=1)
        if any(pattern.search(line) for pattern in FAILURES)
    ]


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("log", type=Path, help="Completed normalized server console log")
    args = parser.parse_args(argv)
    try:
        text = args.log.read_text(encoding="utf-8")
    except (OSError, UnicodeError) as exc:
        print(f"Runtime configuration validation failed: cannot read {args.log}: {exc}", file=sys.stderr)
        return 1
    if not text.strip():
        print(f"Runtime configuration validation failed: empty log {args.log}", file=sys.stderr)
        return 1
    failures = configuration_failures(text)
    if failures:
        print(f"Runtime configuration validation failed: {len(failures)} matching lines in {args.log}", file=sys.stderr)
        for number, line in failures[:40]:
            print(f"{number}: {line}", file=sys.stderr)
        if len(failures) > 40:
            print("Further matches omitted; inspect the retained complete log.", file=sys.stderr)
        return 1
    print(f"Runtime configuration validation passed: {args.log}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
