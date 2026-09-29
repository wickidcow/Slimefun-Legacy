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

    modern_paths = {
        "src/main/java/io/github/thebusybiscuit/slimefun4/utils/SlimefunUtils.java": ("MINECRAFT_1_16", "return inventory.isEmpty();"),
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/listeners/crafting/SmithingTableListener.java": (
            "MINECRAFT_1_20",
            "return 2;",
        ),
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/listeners/BlockListener.java": (
            "MINECRAFT_1_19",
            "return blockData.isSupported(block);",
        ),
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/weapons/IcyBow.java": (
            "MINECRAFT_1_17",
            "player.setFreezeTicks(60);",
        ),
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/electric/machines/AutoDrier.java": (
            "MINECRAFT_1_19",
            "new ItemStack(Material.MUD)",
        ),
    }
    for relative, (forbidden, required) in modern_paths.items():
        text = (root / relative).read_text(encoding="utf-8")
        if forbidden in text:
            failures.append(f"{relative} still contains unsupported-version gate {forbidden}")
        if required not in text:
            failures.append(f"{relative} is missing its 1.21.11+ direct code path: {required}")

    resource_maps = {
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/resources/OilResource.java": "/biome-maps/oil_v1.18.json",
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/resources/SaltResource.java": "/biome-maps/salt_v1.18.json",
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/resources/UraniumResource.java": "/biome-maps/uranium_v1.18.json",
    }
    for relative, required_map in resource_maps.items():
        text = (root / relative).read_text(encoding="utf-8")
        if "MinecraftVersion" in text:
            failures.append(f"{relative} still contains an obsolete biome-version branch")
        if required_map not in text:
            failures.append(f"{relative} is missing the supported biome map {required_map}")

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
