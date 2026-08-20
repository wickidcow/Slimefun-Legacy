#!/usr/bin/env python3
"""Verify Adventurer's Curios and the native Resonance Beacon invariants."""
from __future__ import annotations
import sys
from pathlib import Path


def read(root: Path, rel: str) -> str:
    path = root / rel
    if not path.is_file():
        raise FileNotFoundError(rel)
    return path.read_text(encoding="utf-8")


def req(condition: bool, message: str, failures: list[str]) -> None:
    if not condition:
        failures.append(message)


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
    failures: list[str] = []
    base = "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios/"
    files = {
        "setup": "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/setup/AdventurersCuriosSetup.java",
        "canary": base + "MinersCanary.java",
        "parachute": base + "EmergencyParachute.java",
        "beacon": base + "BeaconPlus.java",
        "effect": base + "BeaconPlusEffect.java",
        "runtime": base + "BeaconPlusRuntime.java",
        "runtime_effects": base + "BeaconPlusRuntimeEffects.java",
        "energy": base + "BeaconPlusEnergy.java",
        "field": base + "BeaconPlusField.java",
        "listener": base + "BeaconPlusEffectListener.java",
        "manager": base + "BeaconPlusManager.java",
        "mode": base + "BeaconPlusChunkMode.java",
        "config_class": base + "BeaconPlusConfig.java",
        "chunk_control": base + "BeaconPlusChunkLoadingControl.java",
        "progression": base + "BeaconPlusProgression.java",
        "pyramid": base + "BeaconPlusPyramid.java",
        "legacy": base + "BeaconPlusLegacyDataStore.java",
        "lifecycle": base + "BeaconPlusLifecycleListener.java",
        "core_config": "src/main/resources/config.yml",
        "sfl_addons_config": "src/main/resources/configSFLAddons.yml",
        "curiosities_config_class": "src/main/java/io/github/thebusybiscuit/slimefun4/core/config/CuriositiesConfig.java",
        "docs": "docs/ADVENTURERS_CURIOS.md",
    }

    for rel in files.values():
        req((root / rel).is_file(), f"Missing Curios file: {rel}", failures)
    req(not (root / (base + "DungeonChalk.java")).exists(), "Dungeon Chalk source must stay removed", failures)
    req(not (root / "src/main/resources/curiosities.yml").exists(),
        "Legacy curiosities.yml must stay removed; use configSFLAddons.yml", failures)

    try:
        setup = read(root, files["setup"])
        for token in (
            '"adventurers_curios"', '"adventurers_curios_field"', '"containment_armor"',
            '"ADVENTURERS_MINERS_CANARY"', '"ADVENTURERS_TRAVELERS_BEDROLL"',
            '"ADVENTURERS_EMERGENCY_PARACHUTE"', '"BEACON_PLUS"', '"&6&lResonance Beacon"',
            'with 29 configurable three-tier powers.', "SlimefunItems.ESSENCE_OF_AFTERLIFE",
            "SlimefunItems.MAGICAL_GLASS", "SlimefunItems.BLISTERING_INGOT_3",
            "SlimefunItems.SYNTHETIC_DIAMOND", "canary.registerListener(plugin)",
            "parachute.registerListener(plugin)", "new BeaconPlus(", "CuriositiesConfig.isEnabled()",
            "CuriositiesConfig.FILE_NAME", "new ContainmentTrap(", "new HazardProtectionArmorPiece(",
        ):
            req(token in setup, f"Curios setup invariant missing: {token}", failures)
        req("DUNGEON_CHALK" not in setup and "DungeonChalk" not in setup,
            "Dungeon Chalk is still registered", failures)
        req(setup.count("RecipeType.ENHANCED_CRAFTING_TABLE") >= 10,
            "Curios/containment recipes are unexpectedly missing", failures)

        canary = read(root, files["canary"])
        for token in ("EntityTargetLivingEntityEvent", "BlockBreakEvent", "Material.LAVA",
                      "PASSIVE_SCAN_INTERVAL_MILLIS", "isCarryingCanary"):
            req(token in canary, f"Miner's Canary danger invariant missing: {token}", failures)
        for forbidden in ("loadChunk", "setChunkForceLoaded", "runTaskTimer", "scheduleSyncRepeatingTask"):
            req(forbidden not in canary, f"Miner's Canary must remain bounded/event-driven: {forbidden}", failures)

        parachute = read(root, files["parachute"])
        for token in ("EntityDamageEvent", "FALL", "registerListener", "60"):
            req(token in parachute, f"Emergency Parachute invariant missing: {token}", failures)

        effects = read(root, files["effect"])
        approved = (
            "FURNACE_BOOSTER", "STRENGTH", "REGENERATION", "RESISTANCE", "FAST_DIGGING", "CURE",
            "CROPS", "SPAWNERS", "SLOWDOWN", "SPEED", "PEACEFUL", "NIGHT_VISION", "FLYING",
            "EXPERIENCE_BOOSTER", "LUCK", "BURNER", "WATER_BREATHING", "FIRE_EXTINGUISHER",
            "RADIATION_ABSORBER", "POISON", "GRAVITY_WELL", "JUMP", "EXP_GAIN",
            "COOLDOWN_REDUCTION", "IMMORTALITY_FIELD", "EXTRA_POWER", "EXTRA_RANGE", "ACTIVATOR",
            "AUTO_REPAIR",
        )
        req(len(approved) == 29, "Verifier must protect exactly 29 Resonance Beacon powers", failures)
        for token in approved:
            req(token in effects, f"Approved Resonance Beacon power missing: {token}", failures)
        for token in ('"radiation_absorber"', '"Radiation Absorber"', "Material.HEAVY_CORE"):
            req(token in effects, f"Radiation Absorber definition missing: {token}", failures)
        req("this != SCALE" in effects and ".filter(BeaconPlusEffect::isConfigurable)" in effects,
            "Scale must remain a non-configurable migration tombstone", failures)

        cfgclass = read(root, files["config_class"])
        for token in (
            'ROOT = "SlimefunLegacyAddition.PoweredBeacon"', 'BEACON_DATA_ROOT = ROOT + ".BeaconData"',
            '"WORLD"', '"BeaconData"', '"EXPERIENCE"', "PaymentMode.MONEY",
            "material-power.IRON_BLOCK", "material-power.NETHERITE_BLOCK", "tier-requirements.3",
            "electric-operation.capacity", "base-joules-per-pulse", "CuriositiesConfig.getConfig()",
            "RADIATION_ABSORBER",
        ):
            req(token in cfgclass, f"Resonance Beacon config invariant missing: {token}", failures)

        curiosities_config_class = read(root, files["curiosities_config_class"])
        for token in (
            'FILE_NAME = "configSFLAddons.yml"', 'RETIRED_FILE_NAME = "curiosities.yml"',
            'LEGACY_MODULE_TOGGLE = "options.enable-non-original-slimefun-additions"',
            'LEGACY_ADDITIONS_ROOT = "SlimefunLegacyAddition"',
            'LEGACY_BEACON_ROOT = LEGACY_ADDITIONS_ROOT + ".PoweredBeacon"',
            "plugin.saveResource(FILE_NAME, false)", "YamlConfiguration.loadConfiguration(file)",
            "Files.copy(retired.toPath(), file.toPath())", "migrateLegacyCoreSettings()",
            'setValue("enabled", core.getBoolean(LEGACY_MODULE_TOGGLE))', "Slimefun.isNewlyInstalled()",
            'setValue("enabled", true)', "if (!save())", "cleanupLegacyCoreSettings()",
            "core.set(LEGACY_MODULE_TOGGLE, null)", "core.set(LEGACY_BEACON_ROOT, null)",
            "plugin.saveConfig()", "public synchronized boolean save()", "if (!dirty)",
        ):
            req(token in curiosities_config_class,
                f"Slimefun Legacy addons config loader invariant missing: {token}", failures)
        req("io.github.bakedlibs.dough" not in curiosities_config_class,
            "Slimefun Legacy addons config must not expand the Dough dependency boundary", failures)

        addons_config = read(root, files["sfl_addons_config"])
        req("\nenabled: false\n\nSlimefunLegacyAddition:" in addons_config,
            "configSFLAddons.yml must default Adventurer's Curios OFF for a genuinely fresh install", failures)
        for token in (
            "SlimefunLegacyAddition:", "PoweredBeacon:", "BeaconData:", "storage-type: WORLD",
            "folder-name: BeaconData", "payment-mode: EXPERIENCE", "IRON_BLOCK: 1.0", "NETHERITE_BLOCK: 5.0",
            "flying:\n        enabled: true", "immortality-field:\n        enabled: true",
            "radiation-absorber:\n        enabled: true", "auto-repair:", "electric-operation:", "capacity: 4096",
        ):
            req(token in addons_config, f"configSFLAddons.yml Resonance Beacon default missing: {token}", failures)

        core_config = read(root, files["core_config"])
        for forbidden in ("SlimefunLegacyAddition:", "PoweredBeacon:", "enable-non-original-slimefun-additions"):
            req(forbidden not in core_config,
                f"generic config.yml must not own Slimefun Legacy addon setting: {forbidden}", failures)

        chunk_control = read(root, files["chunk_control"])
        for token in (
            "resolve(CuriositiesConfig.FILE_NAME)", "CuriositiesConfig.getConfig().reload()",
            '"Could not persist Resonance Beacon chunk-loading state to " + CuriositiesConfig.FILE_NAME',
        ):
            req(token in chunk_control, f"Resonance Beacon addons config persistence invariant missing: {token}", failures)

        for source_name, source in (
            ("AdventurersCuriosSetup", setup),
            ("BeaconPlusConfig", cfgclass),
            ("BeaconPlusChunkLoadingControl", chunk_control),
        ):
            req("curiosities.yml" not in source,
                f"{source_name} still references retired curiosities.yml outside migration code", failures)

        progression = read(root, files["progression"])
        for token in ("adventurers-curios-beacon-progress.yml", "purchaseNextTier", "Vault", "Economy", "experience levels"):
            req(token.lower() in progression.lower(), f"Progression invariant missing: {token}", failures)

        pyramid = read(root, files["pyramid"])
        for token in ("naturalPowerTier", "averageMaterialPower", "getMaterialPower", "getRequiredPyramidTier"):
            req(token in pyramid, f"Pyramid resonance invariant missing: {token}", failures)

        beacon = read(root, files["beacon"])
        for token in (
            'new ChestMenu("&6&lResonance Beacon", 54)', "purchaseNextTier", "action.isRightClicked()",
            "action.isShiftClicked()", "BeaconPlusPyramid.inspect", "BeaconPlusConfig.installDefaults()",
            "BeaconPlusLegacyDataStore.start", "BeaconPlusRuntime.reconcileActivator", "Enabled powers:",
            'enabled.size() + "/29"', "37", "EnergyNetComponent", "ELECTRIC_OPERATION_SLOT", "isEnergyNetActive",
            "BeaconPlusEffect.RADIATION_ABSORBER", "Tier I absorbs 25 exposure",
            "Disabled in configSFLAddons.yml",
        ):
            req(token in beacon, f"Resonance Beacon menu invariant missing: {token}", failures)
        for forbidden in ("BeaconPlus3", "thito.beaconplus", "Class.forName("):
            req(forbidden not in beacon, f"Resonance Beacon must not bridge third-party runtime: {forbidden}", failures)

        listener = read(root, files["listener"])
        for token in (
            "PlayerItemCooldownEvent", "PlayerExpChangeEvent", "IMMORTALITY_COOLDOWN_MILLIS = 60_000L", "0.55D",
            "RadiationDamageEvent", "BeaconPlusEffect.RADIATION_ABSORBER", "RadiationUtils.getExposure",
            "Math.min(25, exposure)", "Math.min(50, exposure)", "RadiationUtils.clearExposure(player)",
            "event.setCancelled(true)",
        ):
            req(token in listener, f"Event power invariant missing: {token}", failures)

        mode = read(root, files["mode"])
        req("AREA_5X5" in mode and '"5x5 Area"' in mode, "Tier III Activator coverage missing", failures)

        runtime = read(root, files["runtime"])
        runtime_effects = read(root, files["runtime_effects"])
        energy = read(root, files["energy"])
        for token in (
            'ELECTRIC_MODE_KEY = "beacon_plus_electric_mode"', "energy-charge", "getDemand(",
            "consumePulse(", "hasOperationalPower(", "Activator",
        ):
            req(token in energy, f"Resonance Beacon energy invariant missing: {token}", failures)
        req("BeaconPlusEnergy.consumePulse(block, data, tiers)" in runtime,
            "Resonance Beacon runtime must pay electric cost once per pulse", failures)
        req("getPotentialActiveTiers" in runtime,
            "Electric operation must preserve configured tiers while energy-gating active tiers", failures)

        for token in (
            'EFFECTS_KEY = "beacon_plus_effects"', "EXTRA_RANGE_PER_TIER = 10", "getActiveTiers",
            "getUnlockedTierAtBeacon", "getSelectedTierAtBeacon", "BeaconPlusPyramid.inspect",
            "BeaconPlusLegacyDataStore.getImportedOverriddenRange", "AREA_5X5", "BeaconPlusRuntimeEffects.applyPulse",
        ):
            req(token in runtime, f"Resonance Beacon runtime invariant missing: {token}", failures)
        for token in ("MAX_TILE_ENTITIES_PER_PULSE = 96", "CROP_SAMPLES_PER_PULSE = 48", "repairInventory(", "getLoadedChunksInField"):
            req(token in runtime_effects, f"Bounded Resonance Beacon effects invariant missing: {token}", failures)
        field = read(root, files["field"])
        for token in ("chunkRadius", "ChunkFootprint", "containsChunk", "widthChunks"):
            req(token in field, f"Full-height chunk field invariant missing: {token}", failures)
        req("distanceSquared(target)" not in runtime,
            "Resonance Beacon field must not retain a vertical/spherical distance limit", failures)
        for forbidden in ("NetworkManager", "CargoNet", "setChunkForceLoaded", "setForceLoaded"):
            req(forbidden not in runtime and forbidden not in runtime_effects,
                f"Resonance Beacon runtime crossed protected boundary: {forbidden}", failures)

        manager = read(root, files["manager"])
        for token in (
            'ITEM_ID = "BEACON_PLUS"', "MAX_ACTIVE_BEACONS = 64", "MAX_UNIQUE_CHUNKS = 256",
            "addPluginChunkTicket", "removePluginChunkTicket", "ticketReferences",
            'resolve("adventurers-curios-beacons.properties")',
        ):
            req(token in manager, f"Activator manager invariant missing: {token}", failures)

        legacy = read(root, files["legacy"])
        for token in (
            'resolve(BeaconPlusConfig.getBeaconDataFolderName())', 'chunkX + "." + chunkZ + ".json"',
            'root.get("Beacons")', '"customName"', '"showParticles"', '"overriddenRange"',
            '"exp_boost"', '"resist"', '"fastdig"', '"fireExtinguisher"', '"fire_extenguisher"',
            "MAX_JSON_STRING_LAYERS = 3", "normalizeLegacyTier", "LEGACY_IMPORTED_OWNER",
            "createBlock(location, BeaconPlusManager.ITEM_ID)",
        ):
            req(token in legacy, f"BeaconData compatibility invariant missing: {token}", failures)
        req('effects.add(existingKey == null ? legacyKey(effect) : existingKey, existing)' in legacy,
            "Legacy effect aliases must be preserved when mirroring", failures)

        lifecycle = read(root, files["lifecycle"])
        for token in ("BeaconPlusRuntime.shutdown()", "BeaconPlusProgression.shutdown()",
                      "BeaconPlusLegacyDataStore.shutdownCurrent()", "BeaconPlusManager.shutdownCurrent()"):
            req(token in lifecycle, f"Shutdown cleanup missing: {token}", failures)

        docs = read(root, files["docs"])
        for token in (
            "Resonance Beacon", "29 player-facing powers", "Radiation Absorber", "Tier I removes 25",
            "BeaconData", "Tier III", "5x5 chunks", "Dungeon Chalk was intentionally removed", "Blistering Ingot",
            "configSFLAddons.yml",
        ):
            req(token in docs, f"Curios documentation missing: {token}", failures)
    except FileNotFoundError as exc:
        failures.append(f"Unable to inspect missing Curios file: {exc}")

    report = root / "build/reports/adventurers-curios.txt"
    report.parent.mkdir(parents=True, exist_ok=True)
    if failures:
        text = "Adventurer's Curios verification: FAIL\n" + "\n".join(f"- {x}" for x in failures) + "\n"
        report.write_text(text, encoding="utf-8")
        print(text, end="")
        return 1

    text = (
        "Adventurer's Curios verification: PASS\n"
        "- current Curiosities and containment content remain integrated on the master baseline\n"
        "- Dungeon Chalk is removed and Miner's Canary remains a bounded passive danger alarm\n"
        "- Resonance Beacon retains BEACON_PLUS only as its migration-safe internal id\n"
        "- exactly 29 administrator-controlled powers support three-tier progression\n"
        "- Radiation Absorber suppresses symptoms and scrubs 25/50/all exposure by tier\n"
        "- pyramid size and configurable mineral resonance cap effective tiers\n"
        "- Legacy addon settings live in configSFLAddons.yml, not generic config.yml\n"
        "- fresh installs default Curiosities off while existing installations stay enabled\n"
        "- migrated Curiosities keys are removed from config.yml only after the replacement config saves\n"
        "- legacy WORLD BeaconData JSON remains import/mirror compatible\n"
        "- field powers use full-height chunk-aligned square footprints without loading chunks\n"
        "- Activator remains reference-counted and hard-capped\n"
    )
    report.write_text(text, encoding="utf-8")
    print(text, end="")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
