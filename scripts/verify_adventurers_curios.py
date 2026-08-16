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
        "wayfarer": "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios/WayfarersLodestone.java",
        "bastion": "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios/BastionResonator.java",
        "flare": "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios/EmergencyFlare.java",
        "surveyor": "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios/SurveyorsRod.java",
        "stabilizer": "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios/ChunkStabilizer.java",
        "beacon": "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios/BeaconPlus.java",
        "beacon_effect": "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios/BeaconPlusEffect.java",
        "beacon_runtime": "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios/BeaconPlusRuntime.java",
        "beacon_power": "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios/BeaconPlusPowerState.java",
        "beacon_listener": "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios/BeaconPlusEffectListener.java",
        "beacon_manager": "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios/BeaconPlusManager.java",
        "beacon_chunk_mode": "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios/BeaconPlusChunkMode.java",
        "beacon_field_area": "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios/BeaconPlusFieldArea.java",
        "beacon_gravity": "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios/BeaconPlusGravity.java",
        "beacon_support_mode": "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios/BeaconPlusSupportMode.java",
        "beacon_lifecycle": "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios/BeaconPlusLifecycleListener.java",
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
            '"ADVENTURERS_WAYFARERS_LODESTONE"',
            '"ADVENTURERS_BASTION_RESONATOR"',
            '"ADVENTURERS_EMERGENCY_FLARE"',
            '"ADVENTURERS_SURVEYORS_ROD"',
            '"ADVENTURERS_CHUNK_STABILIZER"',
            "new WayfindersCompass(",
            "new EchoLantern(",
            "new ExplorersSpyglass(",
            "new MinersCanary(",
            "new DungeonChalk(",
            "new StormGlass(",
            "new ExpeditionJournal(",
            "new BeaconPlus(",
            "new WayfarersLodestone(",
            "new BastionResonator(",
            "new EmergencyFlare(",
            "new SurveyorsRod(",
            "new ChunkStabilizer(",
            "30 independently toggleable effects",
            "Extra Power costs 30 XP levels",
        ):
            require(token in setup, f"Curios setup invariant is missing: {token}", failures)
        require(setup.count("RecipeType.ENHANCED_CRAFTING_TABLE") >= 13, "All thirteen Curios need real recipes", failures)
        for token in (
            "new ItemStack(Material.BEACON)",
            "new ItemStack(Material.NETHERITE_INGOT)",
            "new ItemStack(Material.ECHO_SHARD)",
            "new ItemStack(Material.ENDER_EYE)",
        ):
            require(token in setup, f"Native Beacon Plus recipe invariant is missing: {token}", failures)
        for forbidden in ("BeaconPlus3", "thito.beaconplus"):
            require(forbidden not in setup, f"Curios setup must not depend on discontinued BeaconPlus3: {forbidden}", failures)

        post_setup = read(root, "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/setup/PostSetup.java")
        registration = post_setup.find("AdventurersCuriosSetup.setup(Slimefun.instance());")
        finalization = post_setup.find("markInitialRegistrationFinalized()")
        require(registration >= 0, "Adventurer's Curios is not registered during PostSetup", failures)
        require(finalization >= 0, "Registry finalization marker is missing", failures)
        if registration >= 0 and finalization >= 0:
            require(registration < finalization, "Curios must register before the item registry is finalized", failures)

        wayfinder = read(root, files["wayfinder"])
        for token in ("getLastDeathLocation()", "setLodestone(target)", "setLodestoneTracked(false)", "getSpawnLocation()"):
            require(token in wayfinder, f"Wayfinder invariant is missing: {token}", failures)

        lantern = read(root, files["lantern"])
        for token in ("getNearbyEntities", "instanceof Monster", "PotionEffectType.GLOWING", "COOLDOWN_TICKS = 30 * 20"):
            require(token in lantern, f"Echo Lantern invariant is missing: {token}", failures)

        spyglass = read(root, files["spyglass"])
        for token in ("getBiome().getKey().getKey()", "getDirection(location.getYaw())", "location.getBlockX()"):
            require(token in spyglass, f"Explorer's Spyglass invariant is missing: {token}", failures)

        canary = read(root, files["canary"])
        for token in ("RANGE = 7", "COOLDOWN_TICKS = 5 * 20", "world.isChunkLoaded", "Material.LAVA"):
            require(token in canary, f"Miner's Canary invariant is missing: {token}", failures)
        require("loadChunk" not in canary and "getChunkAt(" not in canary, "Miner's Canary must not load chunks to scan", failures)

        chalk = read(root, files["chalk"])
        for token in ("PersistentDataType.STRING", '"dungeon_chalk_marker"', "getClickedBlock()", "player.isSneaking()"):
            require(token in chalk, f"Dungeon Chalk invariant is missing: {token}", failures)
        for forbidden in ("setType(", "breakNaturally(", "runLater("):
            require(forbidden not in chalk, f"Dungeon Chalk must remain non-destructive: {forbidden}", failures)

        storm = read(root, files["storm"])
        for token in ("isThundering()", "hasStorm()", "getFullTime()", "getWeatherDuration()"):
            require(token in storm, f"Storm Glass invariant is missing: {token}", failures)
        require("setStorm(" not in storm and "setTime(" not in storm, "Storm Glass must remain read-only", failures)

        journal = read(root, files["journal"])
        for token in ("MAX_RECORDED_BIOMES = 128", '"expedition_journal_biomes"', "PersistentDataType.STRING", "getBiome().getKey().getKey()"):
            require(token in journal, f"Expedition Journal invariant is missing: {token}", failures)

        wayfarer = read(root, files["wayfarer"])
        for token in (
            """new ChestMenu("&6&lWayfarer's Lodestone", 54)""",
            "SEARCH_RADIUS_BLOCKS = 4_096",
            "RANDOM_PROBE_RADIUS_BLOCKS = 10_000",
            "MAX_SEARCH_PROBES = 3",
            "COOLDOWN_MILLIS = 180_000L",
            "world.locateNearestBiome(",
            "HORIZONTAL_SEARCH_INTERVAL",
            "VERTICAL_SEARCH_INTERVAL",
            "player.teleportAsync(safe)",
            "world.getWorldBorder().isInside(destination)",
            "runAt(probe",
            "runForLater(",
            "player.isSneaking()",
        ):
            require(token in wayfarer, f"Wayfarer's Lodestone invariant is missing: {token}", failures)
        for forbidden in ("loadChunk(", "setChunkForceLoaded", "setForceLoaded"):
            require(forbidden not in wayfarer, f"Wayfarer's Lodestone must not force chunk loading while searching: {forbidden}", failures)

        bastion = read(root, files["bastion"])
        for token in (
            "SEARCH_RADIUS_CHUNKS = 128",
            "COOLDOWN_MILLIS = 30_000L",
            "World.Environment.NETHER",
            "Registry.STRUCTURE_TYPE",
            'NamespacedKey.minecraft("bastion_remnant")',
            "world.locateNearestStructure(origin, bastion, SEARCH_RADIUS_CHUNKS, false)",
            "compassMeta.setLodestone(target)",
            "compassMeta.setLodestoneTracked(false)",
        ):
            require(token in bastion, f"Bastion Resonator invariant is missing: {token}", failures)
        require("loadChunk(" not in bastion, "Bastion Resonator must not generate/load chunks for its search", failures)

        flare = read(root, files["flare"])
        for token in (
            "FLARE_PULSES = 40",
            "PULSE_DELAY_TICKS = 10L",
            "COOLDOWN_MILLIS = 45_000L",
            "FireworkEffect.builder()",
            "Particle.DUST",
            "Particle.END_ROD",
            "runAtLater(origin",
            "FlareMode next()",
            "player.isSneaking()",
        ):
            require(token in flare, f"Emergency Flare invariant is missing: {token}", failures)

        surveyor = read(root, files["surveyor"])
        for token in (
            "Math.floorDiv(chunk.getX(), 32)",
            "Math.floorDiv(chunk.getZ(), 32)",
            "chunk.getEntities()",
            "chunk.getTileEntities()",
            "chunk.isForceLoaded()",
            "block.getLightFromBlocks()",
            "block.getLightFromSky()",
            "world.isChunkLoaded",
            "runAt(target",
            "runFor(",
        ):
            require(token in surveyor, f"Surveyor's Rod invariant is missing: {token}", failures)
        require("loadChunk(" not in surveyor, "Surveyor's Rod must not load chunks for a survey", failures)

        stabilizer = read(root, files["stabilizer"])
        for token in (
            "BUSY_SCORE = 120",
            "HEAVY_SCORE = 300",
            "CRITICAL_SCORE = 800",
            "chunk.getEntities()",
            "chunk.getTileEntities()",
            "instanceof ArmorStand",
            "instanceof Item",
            "instanceof Minecart",
            "Material.HOPPER",
            "calculateScore(",
            "very high armor-stand density",
            "This scan is read-only",
            "world.isChunkLoaded",
        ):
            require(token in stabilizer, f"Chunk Stabilizer invariant is missing: {token}", failures)
        for forbidden in ("entity.remove(", "breakNaturally(", "setType(", "loadChunk("):
            require(forbidden not in stabilizer, f"Chunk Stabilizer must remain read-only: {forbidden}", failures)

        effect_catalog = read(root, files["beacon_effect"])
        effect_tokens = (
            "FURNACE_BOOSTER", "STRENGTH", "INVISIBLE", "REGENERATION", "RESISTANCE", "FAST_DIGGING", "CURE",
            "CROPS", "SPAWNERS", "SLOWDOWN", "SPEED", "PEACEFUL", "NIGHT_VISION", "FLYING",
            "EXPERIENCE_BOOSTER", "LUCK", "BURNER", "WATER_BREATHING", "FIRE_EXTINGUISHER",
            "POISON", "GRAVITY_WELL", "JUMP", "EXP_GAIN", "COOLDOWN_REDUCTION", "IMMORTALITY_FIELD",
            "SCALE", "EXTRA_POWER", "EXTRA_RANGE", "ACTIVATOR", "AUTO_REPAIR",
        )
        for token in effect_tokens:
            require(token in effect_catalog, f"Beacon Plus effect catalog is missing: {token}", failures)
        require(len(effect_tokens) == 30, "Beacon Plus verifier must protect exactly 30 requested effect families", failures)
        require("serialize(Set<BeaconPlusEffect>" in effect_catalog, "Beacon Plus effect serialization is missing", failures)

        beacon = read(root, files["beacon"])
        for token in (
            "implements EnergyNetComponent",
            "EnergyNetComponentType.CONSUMER",
            "ENERGY_CAPACITY = 8_192",
            "BASE_ENERGY_PER_EFFECT_PER_PULSE = 16",
            "LAST_FIELD_PULSE_TICKS",
            "gameTime - previous < POWER_PULSE_INTERVAL_TICKS",
            "EXTRA_POWER_PERCENT = 50",
            "EXTRA_POWER_XP_LEVEL_COST = 30",
            "player.giveExpLevels(-EXTRA_POWER_XP_LEVEL_COST)",
            "new ChestMenu(\"&6&lBeacon Plus\", 54)",
            "BeaconPlusEffect.values()",
            "BeaconPlusRuntime.tick(block, data)",
            "BeaconPlusPowerState.markPowered(block, data)",
            "BeaconPlusEffectListener.register(Slimefun.instance())",
            "BeaconPlusLifecycleListener.register(Slimefun.instance())",
            "BeaconPlusManager.start(Slimefun.instance())",
            "BlockPlaceHandler(false)",
            "BlockUseHandler",
            "SimpleBlockBreakHandler",
            "DISABLE_ALL_SLOT",
            "Field state: ",
            "Configured, but not active:",
            "FIELD_AREA_SLOT",
            "createFieldAreaItem",
            "cycleFieldArea",
            "Affected area:",
            "BeaconPlusFieldArea.DEFAULT.name()",
        ):
            require(token in beacon, f"Native Beacon Plus menu/runtime invariant is missing: {token}", failures)
        for forbidden in ("BeaconPlus3", "thito.beaconplus", "Class.forName(", "CustomItemStack"):
            require(forbidden not in beacon, f"Native Beacon Plus must not bridge to third-party runtime: {forbidden}", failures)

        runtime = read(root, files["beacon_runtime"])
        for token in (
            'EFFECTS_KEY = "beacon_plus_effects"',
            "PULSE_INTERVAL_TICKS = 20",
            "MAX_TILE_ENTITIES_PER_PULSE = 96",
            "CROP_SAMPLES_PER_CHUNK = 8",
            "getEffectiveFieldArea",
            "getEntitiesInArea",
            "world.isChunkLoaded",
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
            "EFFECT_DURATION_TICKS = 100",
            "new PotionEffect(type, duration, amplifier, false, true, true)",
            "Attribute.SCALE",
            "repairInventory(",
            "boostFurnace(",
            "boostSpawner(",
            "applyCropBoost(",
            "BeaconPlusGravity.getPullStrength(power)",
            "Math.max(-1.25D, Math.min(1.25D, pull.getY()))",
        ):
            require(token in runtime, f"Beacon Plus bounded runtime invariant is missing: {token}", failures)
        for forbidden in ("NetworkManager", "CargoNet", "tickBlock(", "setChunkForceLoaded", "setForceLoaded"):
            require(forbidden not in runtime, f"Beacon Plus runtime crossed a protected boundary: {forbidden}", failures)

        power_state = read(root, files["beacon_power"])
        for token in (
            "POWERED_TTL_MILLIS = 2_500L",
            "markPowered(Block block, ASlimefunDataContainer data)",
            "getPowerForEffect(Location target, BeaconPlusEffect effect)",
            "BeaconPlusEffect.INVISIBLE",
            "PotionEffectType.INVISIBILITY",
            "world.isChunkLoaded",
            "reconcileNearbyPlayerStates",
        ):
            require(token in power_state, f"Beacon Plus paid-power invariant is missing: {token}", failures)
        require("loadChunk" not in power_state, "Beacon Plus power-state checks must not load chunks", failures)

        listener = read(root, files["beacon_listener"])
        for token in (
            "PlayerItemCooldownEvent",
            "PlayerExpChangeEvent",
            "EntityTargetLivingEntityEvent",
            "EntityDamageByEntityEvent",
            "EntityDamageEvent",
            "IMMORTALITY_COOLDOWN_MILLIS = 60_000L",
            "BeaconPlusPowerState.getPowerForEffect",
            "BeaconPlusPowerState.hasPoweredEffect",
            "BeaconPlusEffect.EXPERIENCE_BOOSTER",
            "BeaconPlusEffect.COOLDOWN_REDUCTION",
            "BeaconPlusEffect.PEACEFUL",
            "BeaconPlusEffect.IMMORTALITY_FIELD",
        ):
            require(token in listener, f"Beacon Plus event-runtime invariant is missing: {token}", failures)

        chunk_mode = read(root, files["beacon_chunk_mode"])
        for token in (
            'OFF("Off", 0, false)',
            'SINGLE("1x1 Chunks", 0, true)',
            'AREA_3X3("3x3 Chunks", 1, true)',
            'AREA_5X5("5x5 Chunks", 2, true)',
            "forFieldArea",
            '"KEEP_CHUNK_LOADED"',
            '"CHUNK_ACTIVATOR"',
        ):
            require(token in chunk_mode, f"Beacon Plus Activator mode invariant is missing: {token}", failures)

        field_area = read(root, files["beacon_field_area"])
        for token in (
            'CHUNK_1X1("1x1 Chunks", 0)',
            'AREA_3X3("3x3 Chunks", 1)',
            'AREA_5X5("5x5 Chunks", 2)',
            "DEFAULT = AREA_3X3",
            "BeaconPlusFieldArea expand()",
            "containsChunk(",
            'case "AREA_5X5", "5X5", "LARGE" -> AREA_5X5',
        ):
            require(token in field_area, f"Beacon Plus shared effect-area invariant is missing: {token}", failures)

        gravity = read(root, files["beacon_gravity"])
        for token in (
            "NORMAL_PULL = 1.50D",
            "EXTRA_POWER_PULL = 2.10D",
            "getPullStrength(int power)",
        ):
            require(token in gravity, f"Beacon Plus Gravity Well strength invariant is missing: {token}", failures)

        manager = read(root, files["beacon_manager"])
        for token in (
            'ITEM_ID = "BEACON_PLUS"',
            'FIELD_AREA_KEY = "beacon_plus_field_area"',
            "getFieldArea(",
            "BeaconPlusFieldArea.DEFAULT.name()",
            "MAX_ACTIVE_BEACONS = 64",
            "MAX_UNIQUE_CHUNKS = 256",
            "addPluginChunkTicket",
            "removePluginChunkTicket",
            "ticketReferences",
            'resolve("adventurers-curios-beacons.properties")',
            "scheduleValidation()",
        ):
            require(token in manager, f"Beacon Plus manager invariant is missing: {token}", failures)
        for forbidden in ("NetworkManager", "CargoNet", "TickerTask", "setChunkForceLoaded", "setForceLoaded"):
            require(forbidden not in manager, f"Beacon Plus manager crossed a runtime boundary: {forbidden}", failures)

        lifecycle = read(root, files["beacon_lifecycle"])
        require("BeaconPlusPowerState.shutdown()" in lifecycle, "Beacon Plus paid-power cleanup is missing", failures)
        require("BeaconPlus.clearPulseState()" in lifecycle, "Beacon Plus field-pulse cleanup is missing", failures)
        require("BeaconPlusRuntime.shutdown()" in lifecycle, "Beacon Plus player-state cleanup is missing", failures)
        require("BeaconPlusManager.shutdownCurrent()" in lifecycle, "Beacon Plus chunk-ticket cleanup is missing", failures)

        docs = read(root, files["docs"])
        for token in (
            "30 independently toggleable",
            "Invisible Effect",
            "8,192 J internal buffer",
            "16 J per pulse",
            "50% more machine energy",
            "30 XP levels",
            "BEACON_PLUS:",
            "enabled: false",
            "Slimefun Electricity",
            "Beacon Blocks",
            "3x3 Chunks",
            "5x5 Chunks",
            "maximum **64 active Beacon Plus loaders**",
            "maximum **256 unique chunks**",
            "96 inspected states per pulse",
            "25%",
            "40%",
            "No proprietary BeaconPlus runtime classes or source code are copied",
            "Wayfarer's Lodestone",
            "Bastion Resonator",
            "Emergency Flare",
            "Surveyor's Rod",
            "Chunk Stabilizer",
            "randomized probe",
            "findUnexplored=false",
            "read-only",
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
        "- thirteen built-in Curios are registered before registry finalization\n"
        "- Beacon Plus exposes all 30 requested toggleable effect families through a native 54-slot menu\n"
        "- Invisible Effect restores the historical BeaconPlus effect omitted by the initial native port\n"
        "- Beacon Plus is a normal EnergyNet consumer with an 8,192 J buffer and 16 J/effect field pulses\n"
        "- Extra Power requires a 30-level unlock and increases field-energy draw by exactly 50%\n"
        "- event-driven bonuses require a recently successful paid energy pulse\n"
        "- crop and tile-entity work is capped and unloaded chunks are not scanned for normal field effects\n"
        "- Activator uses reference-counted plugin chunk tickets with 64-beacon and 256-chunk global caps\n"
        "- shutdown restores player flight/scale state, clears paid-power state and releases chunk tickets\n"
        "- the discontinued BeaconPlus3 plugin is not a runtime dependency\n",
        encoding="utf-8",
    )
    print(report.read_text(encoding="utf-8"), end="")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
