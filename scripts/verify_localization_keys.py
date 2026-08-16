#!/usr/bin/env python3
"""Verify literal Slimefun localization keys exist in the bundled English resources.

This intentionally checks only literal keys passed directly to Slimefun's localization
service. Dynamic addon- or item-derived keys are ignored so the verifier remains a
low-noise release guard rather than guessing at runtime-generated paths.
"""
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

YAML_KEY = re.compile(r"^(\s*)([A-Za-z0-9_.-]+):(?:\s|$)")
LOCALIZATION_CALL = re.compile(
    r"Slimefun\.getLocalization\(\)\.(?:getMessage|getMessages|sendMessage)"
    r"\s*\(\s*[^,\n]+,\s*\"([^\"\\]*(?:\\.[^\"\\]*)*)\"",
    re.MULTILINE,
)


def yaml_paths(path: Path) -> set[str]:
    """Return dotted configuration paths declared by a simple Bukkit-style YAML file."""
    paths: set[str] = set()
    stack: list[tuple[int, str]] = []

    for raw_line in path.read_text(encoding="utf-8").splitlines():
        if not raw_line.strip() or raw_line.lstrip().startswith(("#", "-")):
            continue

        match = YAML_KEY.match(raw_line)
        if not match:
            continue

        indent = len(match.group(1).replace("\t", "    "))
        key = match.group(2)
        while stack and stack[-1][0] >= indent:
            stack.pop()

        parts = [entry[1] for entry in stack]
        parts.append(key)
        paths.add(".".join(parts))
        stack.append((indent, key))

    return paths


def english_keys(root: Path) -> set[str]:
    language_dir = root / "src/main/resources/languages/en"
    keys: set[str] = set()
    for path in sorted(language_dir.glob("*.yml")):
        keys.update(yaml_paths(path))
    return keys


def literal_localization_references(root: Path) -> list[tuple[Path, int, str]]:
    references: list[tuple[Path, int, str]] = []
    java_root = root / "src/main/java"
    for path in java_root.rglob("*.java"):
        text = path.read_text(encoding="utf-8")
        for match in LOCALIZATION_CALL.finditer(text):
            key = bytes(match.group(1), "utf-8").decode("unicode_escape")
            line = text.count("\n", 0, match.start()) + 1
            references.append((path, line, key))
    return references


def verify(root: Path) -> list[str]:
    keys = english_keys(root)
    problems: list[str] = []
    for path, line, key in literal_localization_references(root):
        if key not in keys:
            problems.append(f"{path.relative_to(root)}:{line} references missing English localization key `{key}`")
    return problems


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("root", nargs="?", default=".")
    args = parser.parse_args()
    root = Path(args.root).resolve()

    problems = verify(root)
    if problems:
        print("English localization key verification failed:", file=sys.stderr)
        for problem in problems:
            print(f" - {problem}", file=sys.stderr)
        return 1

    references = literal_localization_references(root)
    print(f"English localization key verification passed: {len(references)} literal core references resolved.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
