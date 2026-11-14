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
    "src/main/java/io/github/thebusybiscuit/slimefun4/utils/compatibility/VersionedItemFlag.java": 1,
    "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/Slimefun.java": 1,
    # Paper 26.3 can mark distance-despawn configuration deprecated even though
    # Entity#setPersistent controls world saving, not this behavior. Keep two
    # single-call, private compatibility shims until the API has an equivalent.
    "src/main/java/io/github/thebusybiscuit/slimefun4/core/services/holograms/HologramsService.java": 1,
    "src/main/java/io/github/thebusybiscuit/slimefun4/utils/ArmorStandUtils.java": 1,
    # Existing Networks/InfinityLib folders still implement ItemGroup.isHidden(Player).
    "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/guide/SurvivalSlimefunGuide.java": 1,
}

PATTERN = re.compile(r'@SuppressWarnings\s*\((?:\{[^)]*\}|[^)]*)\)')

# Approved ONLY for these exact single-call despan-policy shims. The whitelist
# must not become permission to hide unrelated deprecations in either class.
DESPAWN_SHIMS = {
    "src/main/java/io/github/thebusybiscuit/slimefun4/core/services/holograms/HologramsService.java": "preventHologramDespawn",
    "src/main/java/io/github/thebusybiscuit/slimefun4/utils/ArmorStandUtils.java": "preventArmorStandDespawn",
}


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

    guide_path = source_root / "io/github/thebusybiscuit/slimefun4/implementation/guide/SurvivalSlimefunGuide.java"
    guide = guide_path.read_text(encoding="utf-8")
    if not re.search(
        r'@SuppressWarnings\("deprecation"\)\s*//[^\n]*\n\s*'
        r'static boolean isVisibleInMainMenu\(ItemGroup group, Player player\)\s*\{\s*'
        r'return group\.isVisible\(player\) && !group\.isHidden\(player\);\s*\}',
        guide,
    ):
        failures.append("Guide hiding compatibility suppression must stay on the single legacy visibility check")

    for rel, method in DESPAWN_SHIMS.items():
        source = (root / rel).read_text(encoding="utf-8")
        exact = (
            r'@SuppressWarnings\("deprecation"\)\s+'
            + rf'private static void {method}\(ArmorStand armorStand\)\s*\{{\s*'
            + r'armorStand\.setRemoveWhenFarAway\(false\);\s*\}'
        )
        if not re.search(exact, source):
            failures.append(
                f"{rel} deprecated distance-despawn shim no longer matches its one approved call"
            )

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
