#!/usr/bin/env python3
"""Verify that every 4.1.19 compatibility-protected source signature still exists."""

from __future__ import annotations

import re
import sys
from pathlib import Path

PROTECTED_PREFIXES = (
    "io/github/thebusybiscuit/slimefun4/api",
    "io/github/thebusybiscuit/slimefun4/core/attributes",
    "io/github/thebusybiscuit/slimefun4/core/services/scheduling",
    "me/mrCookieSlime/Slimefun/Objects/handlers",
    "me/mrCookieSlime/Slimefun/api",
)
TOKEN = re.compile(
    r"\.\.\.|::|->|>>>=|>>>|>>|<<|>=|<=|==|!=|&&|\|\||\+\+|--|"
    r"\+=|-=|\*=|/=|%=|&=|\|=|\^=|[A-Za-z_$][\w$]*|\d+(?:\.\d+)?|[^\s]"
)
IDENTIFIER = re.compile(r"^[A-Za-z_$]")


def strip_comments_and_literals(text: str) -> str:
    """Replace comments and literal contents while preserving Java structure and line breaks."""
    output: list[str] = []
    index = 0
    state = "code"

    while index < len(text):
        current = text[index]
        following = text[index + 1] if index + 1 < len(text) else ""

        if state == "code":
            if current == "/" and following == "/":
                state = "line-comment"
                output.extend("  ")
                index += 2
                continue
            if current == "/" and following == "*":
                state = "block-comment"
                output.extend("  ")
                index += 2
                continue
            if current == '"':
                state = "string"
                output.append(" ")
                index += 1
                continue
            if current == "'":
                state = "character"
                output.append(" ")
                index += 1
                continue

            output.append(current)
            index += 1
            continue

        if state == "line-comment":
            if current == "\n":
                state = "code"
                output.append("\n")
            else:
                output.append(" ")
            index += 1
            continue

        if state == "block-comment":
            if current == "*" and following == "/":
                state = "code"
                output.extend("  ")
                index += 2
            else:
                output.append("\n" if current == "\n" else " ")
                index += 1
            continue

        quote = '"' if state == "string" else "'"
        if current == "\\":
            output.extend("  ")
            index += 2
        elif current == quote:
            state = "code"
            output.append(" ")
            index += 1
        else:
            output.append("\n" if current == "\n" else " ")
            index += 1

    return "".join(output)


def normalize_signature(tokens: list[str]) -> str:
    """Remove annotations and normalize whitespace for stable source-signature matching."""
    normalized: list[str] = []
    index = 0

    while index < len(tokens):
        if tokens[index] != "@":
            normalized.append(tokens[index])
            index += 1
            continue

        index += 1
        if index < len(tokens) and IDENTIFIER.match(tokens[index]):
            index += 1
            while (
                index + 1 < len(tokens)
                and tokens[index] == "."
                and IDENTIFIER.match(tokens[index + 1])
            ):
                index += 2

        if index < len(tokens) and tokens[index] == "(":
            annotation_depth = 1
            index += 1
            while index < len(tokens) and annotation_depth:
                if tokens[index] == "(":
                    annotation_depth += 1
                elif tokens[index] == ")":
                    annotation_depth -= 1
                index += 1

    return " ".join(normalized)


def extract_file_signatures(path: Path) -> list[str]:
    tokens = TOKEN.findall(strip_comments_and_literals(path.read_text(encoding="utf-8")))
    signatures: list[str] = []
    brace_depth = 0
    index = 0

    while index < len(tokens):
        token = tokens[index]
        if token == "{":
            brace_depth += 1
            index += 1
            continue
        if token == "}":
            brace_depth = max(0, brace_depth - 1)
            index += 1
            continue

        if token not in ("public", "protected") or brace_depth not in (0, 1):
            index += 1
            continue

        start = index
        cursor = index
        parenthesis_depth = 0
        bracket_depth = 0
        delimiter: str | None = None

        while cursor < len(tokens):
            candidate = tokens[cursor]
            if candidate == "(":
                parenthesis_depth += 1
            elif candidate == ")":
                parenthesis_depth = max(0, parenthesis_depth - 1)
            elif candidate == "[":
                bracket_depth += 1
            elif candidate == "]":
                bracket_depth = max(0, bracket_depth - 1)
            elif parenthesis_depth == 0 and bracket_depth == 0 and candidate in ("{", ";"):
                delimiter = candidate
                break
            cursor += 1

        if delimiter is None:
            break

        declaration = tokens[start:cursor]
        if "=" in declaration:
            declaration = declaration[: declaration.index("=")]

        signature = normalize_signature(declaration)
        if signature and not signature.endswith(") ->"):
            signatures.append(signature)

        if delimiter == "{":
            brace_depth += 1
        index = cursor + 1

    return signatures


def extract_signatures(root: Path) -> set[str]:
    source_root = root / "src/main/java"
    signatures: set[str] = set()

    for prefix in PROTECTED_PREFIXES:
        package_root = source_root / prefix
        if not package_root.is_dir():
            continue

        for source in sorted(package_root.rglob("*.java")):
            relative = source.relative_to(source_root)
            for signature in extract_file_signatures(source):
                signatures.add(f"{relative}|{signature}")

    return signatures


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
    baseline_path = root / "compatibility/api-signatures-4.1.19.txt"
    if not baseline_path.is_file():
        print(f"Missing API compatibility baseline: {baseline_path.relative_to(root)}", file=sys.stderr)
        return 1

    expected = {
        line.strip()
        for line in baseline_path.read_text(encoding="utf-8").splitlines()
        if line.strip() and not line.lstrip().startswith("#")
    }
    current = extract_signatures(root)
    missing = sorted(expected - current)

    report = root / "build/reports/api-compatibility-4.1.19.txt"
    report.parent.mkdir(parents=True, exist_ok=True)
    if missing:
        message = (
            "Slimefun Legacy 4.1.19 API compatibility failures:\n"
            + "\n".join(f"- Removed or changed: {signature}" for signature in missing)
            + "\n"
        )
        report.write_text(message, encoding="utf-8")
        print(message, file=sys.stderr, end="")
        return 1

    message = (
        "Slimefun Legacy 4.1.19 compatibility-protected API baseline\n"
        f"Protected signatures: {len(expected)}\n"
        f"Current signatures: {len(current)}\n"
        "Removed or changed signatures: 0\n"
        "Result: PASS\n"
    )
    report.write_text(message, encoding="utf-8")
    print(f"API compatibility baseline passed: all {len(expected)} protected 4.1.19 signatures remain.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
