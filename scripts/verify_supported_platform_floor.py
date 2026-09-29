#!/usr/bin/env python3
"""Enforce the Minecraft/Paper 1.21.11+ platform floor on modernized compatibility surfaces."""

from __future__ import annotations

import sys
from pathlib import Path


MODERN_ALIAS_FILES = (
    "src/main/java/io/github/thebusybiscuit/slimefun4/utils/compatibility/VersionedItemFlag.java",
    "src/main/java/io/github/thebusybiscuit/slimefun4/utils/compatibility/VersionedParticle.java",
    "src/main/java/io/github/thebusybiscuit/slimefun4/utils/compatibility/VersionedEntityType.java",
    "src/main/java/io/github/thebusybiscuit/slimefun4/utils/compatibility/VersionedPotionType.java",
    "src/main/java/io/github/thebusybiscuit/slimefun4/utils/compatibility/VersionedEnchantment.java",
    "src/main/java/io/github/thebusybiscuit/slimefun4/utils/compatibility/VersionedPotionEffectType.java",
)


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
    failures: list[str] = []

    for relative in MODERN_ALIAS_FILES:
        text = (root / relative).read_text(encoding="utf-8")
        if "MinecraftVersion" in text or "Slimefun.getMinecraftVersion()" in text:
            failures.append(f"{relative} still contains a pre-floor version gate")
        if "java.lang.reflect" in text or "getDeclaredField(" in text:
            failures.append(f"{relative} still contains pre-1.21.11 reflection fallback logic")

    events = (root / "src/main/java/city/norain/slimefun4/compatibillty/VersionedEvent.java").read_text(encoding="utf-8")
    for forbidden in (
        "MinecraftVersion",
        "java.lang.reflect",
        "@SneakyThrows",
        "BLOCK_EXPLODE_EVENT_CONSTRUCTOR",
        "GET_TOP_INVENTORY",
        "GET_CLICKED_INVENTORY",
    ):
        if forbidden in events:
            failures.append(f"VersionedEvent still contains obsolete compatibility token: {forbidden}")
    if "ExplosionResult.DESTROY" not in events:
        failures.append("VersionedEvent must use the modern BlockExplodeEvent constructor")
    if "event.getView().getTopInventory()" not in events or "event.getClickedInventory()" not in events:
        failures.append("VersionedEvent must use direct 1.21.11+ inventory APIs")

    fireworks = (root / "src/main/java/io/github/thebusybiscuit/slimefun4/utils/FireworkUtils.java").read_text(encoding="utf-8")
    if "EntityType.FIREWORK_ROCKET" not in fireworks:
        failures.append("FireworkUtils must use the modern FIREWORK_ROCKET entity type")
    if "MinecraftVersion" in fireworks or 'EntityType.valueOf("FIREWORK")' in fireworks:
        failures.append("FireworkUtils still contains a pre-1.20.5 entity fallback")

    extended = (root / "src/main/java/city/norain/slimefun4/SlimefunExtended.java").read_text(encoding="utf-8")
    if "VersionedEvent.init()" in extended:
        failures.append("SlimefunExtended still initializes removed pre-1.21 event reflection state")

    if failures:
        print("Supported platform floor verification: FAIL")
        for failure in failures:
            print(f"- {failure}")
        return 1

    print("Supported platform floor verification: PASS")
    print("Modernized compatibility surfaces require Minecraft/Paper 1.21.11+ APIs.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
