#!/usr/bin/env python3
"""Keep Bukkit ChatColor usage limited to documented legacy/API compatibility boundaries."""

from __future__ import annotations

import sys
from pathlib import Path

ALLOWED = {
    "src/main/java/io/github/thebusybiscuit/slimefun4/utils/NumberUtils.java",
    "src/main/java/io/github/thebusybiscuit/slimefun4/core/services/profiler/PerformanceRating.java",
}


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
    source_root = root / "src" / "main" / "java"
    observed: set[str] = set()

    chat_utils_rel = "src/main/java/io/github/thebusybiscuit/slimefun4/utils/ChatUtils.java"
    chat_utils_signature = "crop(@Nonnull org.bukkit.ChatColor color, @Nonnull String string)"

    for java_file in source_root.rglob("*.java"):
        text = java_file.read_text(encoding="utf-8")
        rel = java_file.relative_to(root).as_posix()
        if "org.bukkit.ChatColor" not in text:
            continue

        if rel == chat_utils_rel:
            # Preserve one source/binary compatibility overload for addons while keeping
            # all ChatUtils implementation logic off Bukkit ChatColor.
            if (
                text.count("org.bukkit.ChatColor") == 1
                and chat_utils_signature in text
                and "import org.bukkit.ChatColor;" not in text
            ):
                continue

        observed.add(rel)

    unexpected = sorted(observed - ALLOWED)
    missing = sorted(ALLOWED - observed)

    if unexpected or missing:
        print("Bukkit ChatColor compatibility verification: FAIL")
        for rel in unexpected:
            print(f"- Unapproved Bukkit ChatColor usage: {rel}")
        for rel in missing:
            print(f"- Compatibility whitelist entry no longer uses Bukkit ChatColor: {rel}")
        return 1

    print("Bukkit ChatColor compatibility verification: PASS")
    print(f"Approved compatibility boundaries: {len(ALLOWED)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
