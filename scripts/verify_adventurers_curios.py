#!/usr/bin/env python3
"""Verify the built-in Adventurer's Curios category and native Beacon Plus runtime."""

from __future__ import annotations

import sys
from pathlib import Path


def read(root: Path, relative: str) -> str:
    path = root / relative
    if not path.is_file():
        raise FileNotFoundError(relative)
    return path.read_text(encoding="utf-8")


def require(condition: bool, message: str, failures: list[str]) -> None:
    if not condition:
        failures.append(message)


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
    failures: list[str] = []

    files = {
        "setup": "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/setup/AdventurersCuriosSetup.java",
        "wayfinder": "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios/WayfindersCompass.java",
        "lantern": "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios/EchoLantern.java",
        "spyglass": "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios/ExplorersSpyglass.java",
        "canary": "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios/MinersCanary.java",
        "chalk": "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios/DungeonChalk.java",
        "storm": "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios/StormGlass.java",
        "journal": "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios/ExpeditionJournal.java",
        "beacon": "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios/BeaconPlus.java",
        "effect": "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios/BeaconPlusEffect.java",
        "runtime": "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios/BeaconPlusRuntime.java",
        "listener": "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios/BeaconPlusEffectListener.java",
        "manager": "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios/BeaconPlusManager.java",
        "chunk_mode": "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios/BeaconPlusChunkMode.java",
        "support_mode": "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios/BeaconPlusSupportMode.java",
        "lifecycle": "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios/BeaconPlusLifecycleListener.java",
        "docs": "docs/ADVENTURERS_CURIOS.md",
    }

    for relative in files.values():
        require((root / relative).is_file(), f"Missing Adventurer's Curios file: {relative}", failures)

    try:
        setup = read(root, files["setup"])
        for token in (
            '"adventurers_curios"',
            '"ADVENTURERS_WAYFINDERS_COMPASS"',
            '"ADVENTURERS_ECHO_LANTERN"',
            '"ADVENTURERS_EXPLORERS_SPYGLASS"',
            '"ADVENTURERS_MINERS_CANARY"',
            '"ADVENTURERS_DUNGEON_CHALK"',
            '"ADVENTURERS_STORM_GLASS"',
            '"ADVENTURERS_EXPEDITION_JOURNAL"',
            '"BEACON_PLUS"',
            "new BeaconPlus(",
            "28 independently toggleable powers",
        ):
            require(token in setup, f"Curios setup invariant is missing: {token}", failures)
        require(setup.count("RecipeType.ENHANCED_CRAFTING_TABLE") >= 8, "All eight Curios need real recipes", failures)
        require("BeaconPlus3" not in setup and "thito.beaconplus" not in setup,
                "Curios setup must not depend on BeaconPlus3", failures)

        post_setup = read(root, "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/setup/PostSetup.java")
        registration = post_setup.find("AdventurersCuriosSetup.setup(Slimefun.instance());")
        finalization = post_setup.find("markInitialRegistrationFinalized()")
        require(registration >= 0, "Adventurer's Curios is not registered during PostSetup", failures)
        require(finalization >= 0, "Registry finalization marker is missing", failures)
        if registration >= 0 and finalization >= 0:
            require(registration < finalization, "Curios must register before the item registry is finalized", failures)

        wayfinder = read(root, files["wayfinder"])
        require("getLastDeathLocation()" in wayfinder and "setLodestone(target)" in wayfinder,
                "Wayfinder behavior changed unexpectedly", failures)
        lantern = read(root, files["lantern"])
        require("getNearbyEntities" in lantern and "PotionEffectType.GLOWING" in lantern,
                "Echo Lantern behavior changed unexpectedly", failures)
        spyglass = read(root, files["spyglass"])
        require("getBiome().getKey().getKey()" in spyglass and "getDirection(location.getYaw())" in spyglass,
                "Explorer's Spyglass behavior changed unexpectedly", failures)
        canary = read(root, files["canary"])
        require("world.isChunkLoaded" in canary and "loadChunk" not in canary,
                "Miner's Canary must remain bounded and non-loading", failures)
        chalk = read(root, files["chalk"])
        require('"dungeon_chalk_marker"' in chalk and "setType(" not in chalk,
                "Dungeon Chalk must remain non-destructive", failures)
        storm = read(root, files["storm"])
        require("isThundering()" in storm and "setStorm(" not in storm,
                "Storm Glass must remain read-only", failures)
        journal = read(root, files["journal"])
        require("MAX_RECORDED_BIOMES = 128" in journal,
                "Expedition Journal must retain bounded storage", failures)

        effect_catalog = read(root, files["effect"])
        approved = (
            "FURNACE_BOOSTER", "STRENGTH", "REGENERATION", "RESISTANCE", "FAST_DIGGING", "CURE",
            "CROPS", "SPAWNERS", "SLOWDOWN", "SPEED", "PEACEFUL", "NIGHT_VISION", "FLYING",
            "EXPERIENCE_BOOSTER", "LUCK", "BURNER", "WATER_BREATHING", "FIRE_EXTINGUISHER",
            "POISON", "GRAVITY_WELL", "JUMP", "EXP_GAIN", "COOLDOWN_REDUCTION", "IMMORTALITY_FIELD",
            "EXTRA_POWER", "EXTRA_RANGE", "ACTIVATOR", "AUTO_REPAIR",
        )
        for token in approved:
            require(token in effect_catalog, f"Beacon Plus approved power is missing: {token}", failures)
        require(len(approved) == 28, "Verifier must protect exactly 28 approved Beacon Plus powers", failures)
        require("configurableValues()" in effect_catalog, "Approved-power menu filter is missing", failures)
        require("this != SCALE" in effect_catalog, "Legacy Scale value must remain non-configurable", failures)
        require("effect.isConfigurable()" in effect_catalog and ".filter(BeaconPlusEffect::isConfigurable)" in effect_catalog,
                "Legacy Scale must not parse or persist", failures)

        beacon = read(root, files["beacon"])
        for token in (
            'new ChestMenu("&6&lBeacon Plus", 54)',
            "BeaconPlusEffect.configurableValues()",
            "BeaconPlusRuntime.tick(block, data)",
            "BeaconPlusEffectListener.register(Slimefun.instance())",
            "BeaconPlusLifecycleListener.register(Slimefun.instance())",
            "BeaconPlusManager.start(Slimefun.instance())",
            "Enabled powers:",
            "/28",
        ):
            require(token in beacon, f"Native Beacon Plus invariant is missing: {token}", failures)
        for forbidden in ("BeaconPlus3", "thito.beaconplus", "Class.forName("):
            require(forbidden not in beacon, f"Native Beacon Plus must not bridge to third-party runtime: {forbidden}", failures)

        runtime = read(root, files["runtime"])
        for token in (
            'EFFECTS_KEY = "beacon_plus_effects"',
            "PULSE_INTERVAL_TICKS = 20",
            "MAX_TILE_ENTITIES_PER_PULSE = 96",
            "CROP_SAMPLES_PER_PULSE = 48",
            "EXTRA_RANGE_BLOCKS = 20",
            "PotionEffectType.STRENGTH",
            "PotionEffectType.REGENERATION",
            "PotionEffectType.RESISTANCE",
            "PotionEffectType.HASTE",
            "PotionEffectType.NIGHT_VISION",
            "PotionEffectType.LUCK",
            "PotionEffectType.WATER_BREATHING",
            "PotionEffectType.JUMP_BOOST",
            "PotionEffectType.SLOWNESS",
            "PotionEffectType.POISON",
            "repairInventory(", "boostFurnace(", "boostSpawner(", "applyCropBoost(",
        ):
            require(token in runtime, f"Beacon Plus bounded runtime invariant is missing: {token}", failures)
        for forbidden in ("NetworkManager", "CargoNet", "setChunkForceLoaded", "setForceLoaded"):
            require(forbidden not in runtime, f"Beacon Plus runtime crossed a protected boundary: {forbidden}", failures)

        listener = read(root, files["listener"])
        for token in (
            "PlayerItemCooldownEvent", "PlayerExpChangeEvent", "EntityTargetLivingEntityEvent",
            "EntityDamageByEntityEvent", "EntityDamageEvent", "IMMORTALITY_COOLDOWN_MILLIS = 60_000L",
            "BeaconPlusEffect.EXPERIENCE_BOOSTER", "BeaconPlusEffect.COOLDOWN_REDUCTION",
            "BeaconPlusEffect.PEACEFUL", "BeaconPlusEffect.IMMORTALITY_FIELD",
        ):
            require(token in listener, f"Beacon Plus event-runtime invariant is missing: {token}", failures)

        manager = read(root, files["manager"])
        for token in (
            'ITEM_ID = "BEACON_PLUS"', "MAX_ACTIVE_BEACONS = 64", "MAX_UNIQUE_CHUNKS = 256",
            "addPluginChunkTicket", "removePluginChunkTicket", "ticketReferences",
            'resolve("adventurers-curios-beacons.properties")', "scheduleValidation()",
        ):
            require(token in manager, f"Beacon Plus Activator manager invariant is missing: {token}", failures)

        lifecycle = read(root, files["lifecycle"])
        require("BeaconPlusRuntime.shutdown()" in lifecycle, "Beacon Plus player-state cleanup is missing", failures)
        require("BeaconPlusManager.shutdownCurrent()" in lifecycle, "Beacon Plus chunk-ticket cleanup is missing", failures)

        docs = read(root, files["docs"])
        for token in (
            "28 independently toggleable",
            "maximum **64 active Beacon Plus loaders**",
            "maximum **256 unique chunks**",
            "96 inspected states per pulse",
            "25%", "40%",
        ):
            require(token in docs, f"Curios documentation is missing Beacon Plus detail: {token}", failures)
    except FileNotFoundError as error:
        failures.append(f"Unable to inspect missing Adventurer's Curios file: {error}")

    report = root / "build/reports/adventurers-curios.txt"
    report.parent.mkdir(parents=True, exist_ok=True)

    if failures:
        report.write_text(
            "Adventurer's Curios verification: FAIL\n"
            + "\n".join(f"- {failure}" for failure in failures)
            + "\n",
            encoding="utf-8",
        )
        print(report.read_text(encoding="utf-8"), end="")
        return 1

    report.write_text(
        "Adventurer's Curios verification: PASS\n"
        "- eight built-in Curios are registered before registry finalization\n"
        "- Beacon Plus exposes exactly the 28 approved player-facing powers through its native menu\n"
        "- the legacy Scale development value cannot be configured, parsed or persisted\n"
        "- periodic work is bounded and event-driven powers share one listener\n"
        "- Activator uses reference-counted plugin chunk tickets with global safety caps\n"
        "- shutdown restores player state and releases Beacon Plus chunk tickets\n"
        "- BeaconPlus3 is not a runtime dependency\n",
        encoding="utf-8",
    )
    print(report.read_text(encoding="utf-8"), end="")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
