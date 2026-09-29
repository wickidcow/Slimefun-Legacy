#!/usr/bin/env python3
"""Keep Bukkit ChatColor usage limited to documented legacy/API compatibility boundaries."""

from __future__ import annotations

import sys
from pathlib import Path

ALLOWED = {
    "src/main/java/io/github/thebusybiscuit/slimefun4/utils/NumberUtils.java",
    "src/main/java/me/mrCookieSlime/CSCoreLibPlugin/general/Inventory/ChestMenu.java",
    "src/main/java/io/github/thebusybiscuit/slimefun4/core/services/profiler/PerformanceRating.java",
    "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/elevator/ElevatorPlate.java",
    "src/main/java/io/github/thebusybiscuit/slimefun4/core/services/stability/ItemPresentationDoctor.java",
}


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
    source_root = root / "src" / "main" / "java"
    observed: set[str] = set()

    for java_file in source_root.rglob("*.java"):
        text = java_file.read_text(encoding="utf-8")
        if "import org.bukkit.ChatColor;" in text:
            observed.add(java_file.relative_to(root).as_posix())

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
