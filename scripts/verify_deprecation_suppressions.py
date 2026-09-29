#!/usr/bin/env python3
"""Ensure deprecation suppressions stay limited to documented compatibility shims."""

from __future__ import annotations

import re
import sys
from pathlib import Path

ALLOWED = {
    "src/main/java/me/mrCookieSlime/Slimefun/api/BlockInfoConfig.java": 1,
    "src/main/java/me/mrCookieSlime/Slimefun/api/BlockStorage.java": 1,
    "src/main/java/com/xzavier0722/mc/plugin/slimefun4/storage/controller/BlockDataConfigWrapper.java": 1,
    "src/main/java/me/mrCookieSlime/Slimefun/Objects/handlers/BlockTicker.java": 1,
    "src/main/java/com/xzavier0722/mc/plugin/slimefun4/storage/util/LegacyItemMetaDeserializer.java": 3,
    "src/main/java/com/xzavier0722/mc/plugin/slimefun4/storage/util/ItemStackDataCodec.java": 1,
    "src/main/java/io/github/thebusybiscuit/slimefun4/core/attributes/EnergyNetProvider.java": 1,
    "src/main/java/io/github/thebusybiscuit/slimefun4/core/attributes/EnergyNetComponent.java": 1,
    "src/main/java/io/github/thebusybiscuit/slimefun4/core/services/profiler/PerformanceRating.java": 1,
    "src/main/java/io/github/thebusybiscuit/slimefun4/utils/NumberUtils.java": 1,
    "src/main/java/io/github/thebusybiscuit/slimefun4/utils/ChatUtils.java": 1,
    "src/main/java/io/github/thebusybiscuit/slimefun4/api/items/SlimefunItemStack.java": 1,
    "src/main/java/io/github/thebusybiscuit/slimefun4/utils/itemstack/ItemStackWrapper.java": 1,
    "src/main/java/io/github/thebusybiscuit/slimefun4/core/services/stability/LegacyItemSchemaMigrationExecutor.java": 1,
    "src/main/java/io/github/thebusybiscuit/slimefun4/utils/compatibility/LegacyBukkitCompatibility.java": 2,
    "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/Slimefun.java": 1,
}

PATTERN = re.compile(r'@SuppressWarnings\s*\((?:\{[^)]*\}|[^)]*)\)')


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
    source_root = root / "src" / "main" / "java"
    observed: dict[str, int] = {}
    failures: list[str] = []

    for path in source_root.rglob("*.java"):
        text = path.read_text(encoding="utf-8")
        count = 0
        for match in PATTERN.finditer(text):
            annotation = match.group(0)
            if '"deprecation"' in annotation or '"removal"' in annotation:
                count += 1
        if count:
            rel = path.relative_to(root).as_posix()
            observed[rel] = count
            allowed = ALLOWED.get(rel)
            if allowed is None:
                failures.append(f"{rel} has {count} unapproved deprecation/removal suppression(s)")
            elif allowed != count:
                failures.append(f"{rel} has {count} suppression(s); compatibility whitelist expects {allowed}")

    for rel, expected in ALLOWED.items():
        actual = observed.get(rel, 0)
        if actual != expected:
            failures.append(f"{rel} compatibility suppression count changed: expected {expected}, found {actual}")

    if failures:
        print("Deprecation suppression verification: FAIL")
        for failure in sorted(set(failures)):
            print(f"- {failure}")
        return 1

    print("Deprecation suppression verification: PASS")
    print(f"Approved compatibility suppressions: {sum(ALLOWED.values())}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
