#!/usr/bin/env python3
"""Verify the consolidated Slimefun Legacy project-history layout."""

from __future__ import annotations

import sys
from pathlib import Path

ALLOWED_ROOT_MARKDOWN = {
    "AGENTS.md",
    "CONTRIBUTING.md",
    "EVERYTHING_THAT_CHANGED.md",
    "README.md",
}

REQUIRED_HISTORY_MARKERS = (
    "# Slimefun Legacy 4.1.18",
    "# Slimefun Legacy 4.1.19",
    "# Slimefun Legacy 4.1.20",
    "# Slimefun Legacy 4.1.21",
    "# Slimefun Legacy 4.1.22",
    "# Slimefun Legacy 4.1.23",
    "# Slimefun Legacy 4.1.24",
    "# Slimefun Legacy 4.1.25",
    "Core Platform Phase 1A",
    "Core Platform Phase 1B",
    "Core Platform Phase 1C",
    "Core Platform Phase 1D",
    "Core Platform Phase 1E",
    "Core Platform Phase 1F",
    "Core Platform Phase 1G",
    "Core Platform Phase 1H",
    "Core Platform Phase 1I",
    "Core Platform Phase 1J",
    "Enhanced Guide",
    "Slimefun Legacy Folia Support — Phase 1",
    "Gugu",
)


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
    failures: list[str] = []

    history_path = root / "EVERYTHING_THAT_CHANGED.md"
    if not history_path.is_file():
        failures.append("EVERYTHING_THAT_CHANGED.md is missing")
        history = ""
    else:
        history = history_path.read_text(encoding="utf-8")

    for marker in REQUIRED_HISTORY_MARKERS:
        if marker not in history:
            failures.append(f"Consolidated history is missing marker: {marker}")

    readme_path = root / "README.md"
    readme = readme_path.read_text(encoding="utf-8") if readme_path.is_file() else ""
    if "[Release History](EVERYTHING_THAT_CHANGED.md)" not in readme:
        failures.append("README does not link to EVERYTHING_THAT_CHANGED.md")
    if "docs/history/" in readme:
        failures.append("README still links to the retired docs/history directory")
    if "CHANGELOG.md" in readme:
        failures.append("README still links to the retired root CHANGELOG.md")

    root_markdown = {path.name for path in root.glob("*.md") if path.is_file()}
    unexpected_root_markdown = sorted(root_markdown - ALLOWED_ROOT_MARKDOWN)
    if unexpected_root_markdown:
        failures.append(
            "Unexpected historical Markdown files remain in the repository root: "
            + ", ".join(unexpected_root_markdown)
        )

    scan_roots = [root / "scripts", root / ".github" / "workflows"]
    this_file = Path(__file__).resolve()
    for scan_root in scan_roots:
        if not scan_root.is_dir():
            continue
        for path in scan_root.rglob("*"):
            if not path.is_file() or path.resolve() == this_file:
                continue
            try:
                text = path.read_text(encoding="utf-8")
            except UnicodeDecodeError:
                continue
            if "CHANGELOG.md" in text:
                failures.append(f"Legacy CHANGELOG.md reference remains in {path.relative_to(root)}")
            if "docs/history/" in text:
                failures.append(f"Legacy docs/history reference remains in {path.relative_to(root)}")

    if failures:
        print("Documentation consolidation verification failed:")
        for failure in failures:
            print(f" - {failure}")
        return 1

    print("Documentation consolidation verification passed.")
    history_dir = root / "docs" / "history"
    if history_dir.exists():
        print("Note: docs/history is no longer required and may be deleted after consolidation.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
