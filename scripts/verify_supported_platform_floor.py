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
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/electric/machines/ElectricIngotPulverizer.java": (
            "MinecraftVersion",
            "new ItemStack(Material.COPPER_INGOT)",
        ),
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/blocks/Crucible.java": (
            "MinecraftVersion",
            "new ItemStack(Material.COBBLED_DEEPSLATE, 12)",
        ),
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/multiblocks/GrindStone.java": (
            "MinecraftVersion",
            "new ItemStack(Material.AMETHYST_BLOCK)",
        ),
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/electric/machines/ElectrifiedCrucible.java": (
            "MinecraftVersion",
            "new ItemStack(Material.COBBLED_DEEPSLATE, 12)",
        ),
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/electric/generators/BioGenerator.java": (
            "MinecraftVersion",
            "new ItemStack(Material.GLOW_BERRIES)",
        ),
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/electric/machines/ElectricPress.java": (
            "MinecraftVersion",
            "new ItemStack(Material.AMETHYST_SHARD, 4)",
        ),
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/weapons/SwordOfBeheading.java": (
            "MinecraftVersion",
            "new ItemStack(Material.PIGLIN_HEAD)",
        ),
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/electric/machines/entities/ProduceCollector.java": (
            "MinecraftVersion",
            "n instanceof Cow || n instanceof Goat",
        ),
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/listeners/TalismanListener.java": (
            "MinecraftVersion",
            "entity instanceof Allay",
        ),
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/multiblocks/miner/IndustrialMiner.java": (
            "MinecraftVersion",
            "SlimefunTag.DEEPSLATE_ORES.isTagged(type)",
        ),
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/electric/machines/accelerators/TreeGrowthAccelerator.java": (
            "MinecraftVersion",
            "return applyBoneMeal(machine, sapling, inv);",
        ),
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/multiblocks/OreCrusher.java": (
            "MinecraftVersion",
            "new ItemStack(Material.RAW_COPPER)",
        ),
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/listeners/ExplosionsListener.java": (
            "MinecraftVersion",
            "e.getExplosionResult() == ExplosionResult.TRIGGER_BLOCK",
        ),
    }
    for relative, (forbidden, required) in modern_paths.items():
        text = (root / relative).read_text(encoding="utf-8")
        if forbidden in text:
            failures.append(f"{relative} still contains unsupported-version gate {forbidden}")
        if required not in text:
            failures.append(f"{relative} is missing its 1.21.11+ direct code path: {required}")

    woodcutter = (root / "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/androids/WoodcutterAndroid.java").read_text(encoding="utf-8")
    if "MinecraftVersion" in woodcutter or "SlimefunExtended.isAtLeast" in woodcutter:
        failures.append("WoodcutterAndroid still contains obsolete tree-version gates")
    for required in ("MANGROVE_PROPAGULE", "CHERRY_SAPLING", "PALE_OAK_SAPLING"):
        if required not in woodcutter:
            failures.append(f"WoodcutterAndroid is missing supported tree mapping: {required}")

    ore_dictionary = (root / "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/multiblocks/miner/OreDictionary.java").read_text(encoding="utf-8")
    if "MinecraftVersion" in ore_dictionary or "forVersion(" in ore_dictionary:
        failures.append("OreDictionary still contains an obsolete version factory")

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
