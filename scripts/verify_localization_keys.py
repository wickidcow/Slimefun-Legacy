#!/usr/bin/env python3
"""Verify literal Slimefun localization keys exist in the bundled English resources.

Only complete string-literal keys passed directly to Slimefun's localization service
are checked. Runtime-built keys (for example ``"languages." + id``) are deliberately
ignored so the verifier catches real missing resources without guessing dynamic paths.
"""
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

YAML_KEY = re.compile(r"^(\s*)(?:['\"]?)([A-Za-z0-9_.-]+)(?:['\"]?):(?:\s|$)")
CALL_START = re.compile(
    r"Slimefun\.getLocalization\(\)\s*\.\s*(getMessage|getMessages|sendMessage)\s*\(",
    re.MULTILINE,
)
STRING_LITERAL = re.compile(r'^\s*"((?:\\.|[^"\\])*)"\s*$', re.DOTALL)


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


def split_call_arguments(text: str, start: int) -> list[str] | None:
    """Split arguments for the call whose opening parenthesis ends at ``start``."""
    args: list[str] = []
    arg_start = start
    depth = 0
    in_string = False
    escaped = False

    for index in range(start, len(text)):
        char = text[index]

        if in_string:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == '"':
                in_string = False
            continue

        if char == '"':
            in_string = True
        elif char in "([{":
            depth += 1
        elif char in ")]}":
            if char == ")" and depth == 0:
                args.append(text[arg_start:index])
                return args
            depth = max(0, depth - 1)
        elif char == "," and depth == 0:
            args.append(text[arg_start:index])
            arg_start = index + 1

    return None


def string_literal(argument: str) -> str | None:
    match = STRING_LITERAL.match(argument)
    if not match:
        return None
    return bytes(match.group(1), "utf-8").decode("unicode_escape")


def literal_localization_references(root: Path) -> list[tuple[Path, int, str]]:
    references: list[tuple[Path, int, str]] = []
    java_root = root / "src/main/java"

    for path in java_root.rglob("*.java"):
        text = path.read_text(encoding="utf-8")
        for match in CALL_START.finditer(text):
            method = match.group(1)
            args = split_call_arguments(text, match.end())
            if not args:
                continue

            key: str | None = None
            if method == "sendMessage":
                if len(args) > 1:
                    key = string_literal(args[1])
            else:
                key = string_literal(args[0])
                if key is None and len(args) > 1:
                    key = string_literal(args[1])

            if not key:
                # Empty strings and runtime-built expressions are not localization keys.
                continue

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

    references = literal_localization_references(root)
    problems = verify(root)
    if problems:
        print("English localization key verification failed:", file=sys.stderr)
        for problem in problems:
            print(f" - {problem}", file=sys.stderr)
        return 1

    print(f"English localization key verification passed: {len(references)} literal core references resolved.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
