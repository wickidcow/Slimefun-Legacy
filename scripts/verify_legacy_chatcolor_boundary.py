#!/usr/bin/env python3
"""Keep Bukkit ChatColor usage limited to documented Legacy compatibility bridges."""

from __future__ import annotations

import sys
from pathlib import Path

ALLOWED = {
    "src/main/java/io/github/thebusybiscuit/slimefun4/core/services/profiler/PerformanceRating.java":
        "getTextColor()",
    "src/main/java/io/github/thebusybiscuit/slimefun4/utils/NumberUtils.java":
        "getTextColorFromPercentage",
    "src/main/java/io/github/thebusybiscuit/slimefun4/utils/ChatUtils.java":
        "crop(@Nonnull String color",
}

TOKEN = "org.bukkit.ChatColor"


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
    source_root = root / "src" / "main" / "java"
    failures: list[str] = []
    observed: set[str] = set()

    for path in source_root.rglob("*.java"):
        source = path.read_text(encoding="utf-8")
        if TOKEN not in source:
            continue

        relative = path.relative_to(root).as_posix()
        observed.add(relative)

        modern_boundary = ALLOWED.get(relative)
        if modern_boundary is None:
            failures.append(f"{relative} reintroduced Bukkit ChatColor outside an approved compatibility bridge")
            continue

        if modern_boundary not in source:
            failures.append(
                f"{relative} compatibility bridge is missing its modern Adventure alternative: {modern_boundary}"
            )

        if '"deprecation"' not in source:
            failures.append(f"{relative} uses Bukkit ChatColor without an explicit deprecation suppression")

    for relative in ALLOWED:
        if relative not in observed:
            failures.append(
                f"{relative} no longer uses Bukkit ChatColor; remove it from the compatibility boundary whitelist"
            )

    if failures:
        print("Legacy ChatColor boundary verification: FAIL")
        for failure in sorted(set(failures)):
            print(f"- {failure}")
        return 1

    print("Legacy ChatColor boundary verification: PASS")
    print(f"Approved Bukkit ChatColor compatibility bridges: {len(observed)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
