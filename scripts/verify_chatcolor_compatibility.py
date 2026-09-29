#!/usr/bin/env python3
"""Keep Bukkit ChatColor usage limited to documented legacy/API compatibility boundaries."""

from __future__ import annotations

import sys
from pathlib import Path

ALLOWED = {
    "src/main/java/io/github/thebusybiscuit/slimefun4/utils/NumberUtils.java":
        "getTextColorFromPercentage",
    "src/main/java/io/github/thebusybiscuit/slimefun4/core/services/profiler/PerformanceRating.java":
        "getTextColor()",
}

CHAT_UTILS = "src/main/java/io/github/thebusybiscuit/slimefun4/utils/ChatUtils.java"
CHAT_UTILS_SIGNATURE = "crop(@Nonnull org.bukkit.ChatColor color, @Nonnull String string)"
CHAT_UTILS_MODERN = "crop(@Nonnull String color, @Nonnull String string)"


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
    source_root = root / "src" / "main" / "java"
    observed: set[str] = set()
    failures: list[str] = []

    for java_file in source_root.rglob("*.java"):
        text = java_file.read_text(encoding="utf-8")
        rel = java_file.relative_to(root).as_posix()
        if "org.bukkit.ChatColor" not in text:
            continue

        if rel == CHAT_UTILS:
            if (
                text.count("org.bukkit.ChatColor") != 1
                or CHAT_UTILS_SIGNATURE not in text
                or CHAT_UTILS_MODERN not in text
                or "import org.bukkit.ChatColor;" in text
                or '"deprecation"' not in text
            ):
                failures.append("ChatUtils Bukkit ChatColor compatibility overload drifted from its narrow boundary")
            continue

        modern_alternative = ALLOWED.get(rel)
        if modern_alternative is None:
            failures.append(f"Unapproved Bukkit ChatColor usage: {rel}")
            continue

        observed.add(rel)
        if modern_alternative not in text:
            failures.append(
                f"{rel} is missing its modern Adventure alternative: {modern_alternative}"
            )
        if '"deprecation"' not in text:
            failures.append(f"{rel} uses Bukkit ChatColor without an explicit deprecation suppression")

    for rel in sorted(set(ALLOWED) - observed):
        failures.append(f"Compatibility whitelist entry no longer uses Bukkit ChatColor: {rel}")

    if failures:
        print("Bukkit ChatColor compatibility verification: FAIL")
        for failure in sorted(set(failures)):
            print(f"- {failure}")
        return 1

    print("Bukkit ChatColor compatibility verification: PASS")
    print(f"Approved compatibility boundaries: {len(ALLOWED) + 1}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
