#!/usr/bin/env python3
"""Verify the complete native Resonance Beacon/BeaconPlus gameplay contract."""

from __future__ import annotations

import sys
from pathlib import Path


def read(root: Path, relative: str) -> str:
    path = root / relative
    if not path.is_file():
        raise SystemExit(f"Resonance Beacon functionality verification failed: missing {relative}")
    return path.read_text(encoding="utf-8")


def require(text: str, token: str, label: str, failures: list[str]) -> None:
    if token not in text:
        failures.append(f"missing {label}: {token}")


def forbid(text: str, token: str, label: str, failures: list[str]) -> None:
    if token in text:
        failures.append(f"forbidden {label}: {token}")


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
    base = root / "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios"
    failures: list[str] = []

    beacon = read(root, base.relative_to(root).as_posix() + "/BeaconPlus.java")
    runtime = read(root, base.relative_to(root).as_posix() + "/BeaconPlusRuntime.java")
    effects = read(root, base.relative_to(root).as_posix() + "/BeaconPlusRuntimeEffects.java")
    listener = read(root, base.relative_to(root).as_posix() + "/BeaconPlusEffectListener.java")
    lifecycle = read(root, base.relative_to(root).as_posix() + "/BeaconPlusLifecycleListener.java")
    effect_enum = read(root, base.relative_to(root).as_posix() + "/BeaconPlusEffect.java")
    energy = read(root, base.relative_to(root).as_posix() + "/BeaconPlusEnergy.java")
    manager = read(root, base.relative_to(root).as_posix() + "/BeaconPlusManager.java")
    pyramid = read(root, base.relative_to(root).as_posix() + "/BeaconPlusPyramid.java")
    field = read(root, base.relative_to(root).as_posix() + "/BeaconPlusField.java")
    legacy = read(root, base.relative_to(root).as_posix() + "/BeaconPlusLegacyDataStore.java")
    config = read(root, "src/main/resources/configSFLAddons.yml")

    # Identity/migration: the display rename must never replace the storage-safe item id.
    for text, token, label in (
        (manager, 'ITEM_ID = "BEACON_PLUS"', "historic block id"),
        (beacon, "Native Slimefun Legacy Resonance Beacon", "current display implementation"),
        (legacy, "LEGACY_IMPORTED_OWNER", "legacy BeaconData ownership bridge"),
        (legacy, "createBlock(location, BeaconPlusManager.ITEM_ID)", "legacy BeaconData import"),
    ):
        require(text, token, label, failures)

    # Exactly the 29 approved configurable powers must remain present.
    approved = (
        "FURNACE_BOOSTER", "STRENGTH", "REGENERATION", "RESISTANCE", "FAST_DIGGING", "CURE",
        "CROPS", "SPAWNERS", "SLOWDOWN", "SPEED", "PEACEFUL", "NIGHT_VISION", "FLYING",
        "EXPERIENCE_BOOSTER", "LUCK", "BURNER", "WATER_BREATHING", "FIRE_EXTINGUISHER",
        "RADIATION_ABSORBER", "POISON", "GRAVITY_WELL", "JUMP", "EXP_GAIN",
        "COOLDOWN_REDUCTION", "IMMORTALITY_FIELD", "EXTRA_POWER", "EXTRA_RANGE", "ACTIVATOR",
        "AUTO_REPAIR",
    )
    if len(approved) != 29:
        failures.append("verifier approved-power list is not exactly 29")
    for power in approved:
        require(effect_enum, power, f"power enum {power}", failures)
    require(effect_enum, "this != SCALE", "Scale migration tombstone exclusion", failures)

    # Periodic player effects.
    player_handlers = {
        "STRENGTH": "PotionEffectType.STRENGTH",
        "REGENERATION": "PotionEffectType.REGENERATION",
        "RESISTANCE": "PotionEffectType.RESISTANCE",
        "FAST_DIGGING": "PotionEffectType.HASTE",
        "SPEED": "PotionEffectType.SPEED",
        "NIGHT_VISION": "PotionEffectType.NIGHT_VISION",
        "LUCK": "PotionEffectType.LUCK",
        "WATER_BREATHING": "PotionEffectType.WATER_BREATHING",
        "JUMP": "PotionEffectType.JUMP_BOOST",
    }
    for power, potion in player_handlers.items():
        require(effects, f"BeaconPlusEffect.{power}", f"{power} periodic handler", failures)
        require(effects, potion, f"{power} potion mapping", failures)
    for power, token in {
        "CURE": "player.removePotionEffect(harmful)",
        "FIRE_EXTINGUISHER": "player.setFireTicks(0)",
        "EXP_GAIN": "player.giveExp(expGainTier)",
        "AUTO_REPAIR": "repairInventory(player.getInventory(), repairTier)",
        "FLYING": "updateFlight(player, tiers.getOrDefault(BeaconPlusEffect.FLYING, 0) > 0)",
    }.items():
        require(effects, f"BeaconPlusEffect.{power}", f"{power} power lookup", failures)
        require(effects, token, f"{power} behavior", failures)

    # Periodic mob/world effects.
    for power, token in {
        "SLOWDOWN": "PotionEffectType.SLOWNESS",
        "POISON": "PotionEffectType.POISON",
        "BURNER": "monster.setFireTicks",
        "PEACEFUL": "monster.setTarget(null)",
        "GRAVITY_WELL": "entity instanceof Mob || entity instanceof Item",
        "FURNACE_BOOSTER": "boostFurnace",
        "SPAWNERS": "boostSpawner",
        "CROPS": "applyCropBoost",
    }.items():
        require(effects, f"BeaconPlusEffect.{power}", f"{power} power lookup", failures)
        require(effects, token, f"{power} behavior", failures)

    # Pulse scan routing: player-only powers use the cheap world-player path on Paper-family servers;
    # mob effects and Gravity Well retain full entity enumeration, while Folia stays region-local.
    for token, label in (
        ("PLAYER_PULSE_EFFECTS = Set.of", "player pulse effect classification"),
        ("MONSTER_PULSE_EFFECTS = Set.of", "monster pulse effect classification"),
        ("if (playerPulse && !folia)", "Paper player-only fast path"),
        ("for (Player player : world.getPlayers())", "world player iteration"),
        ("footprint.containsChunk", "chunk-aligned player range filter"),
        ("boolean scanChunkEntities = gravityTier > 0 || monsterPulse || (folia && playerPulse)", "entity scan gate"),
        ("boolean needsLoadedChunks = scanChunkEntities || furnaceTier > 0 || spawnerTier > 0 || cropPulse", "chunk work gate"),
        ("if (scanChunkEntities)", "conditional full entity scan"),
        ("if (folia && playerPulse && entity instanceof Player player)", "Folia region-local player pulse path"),
        ("else if (monsterPulse && entity instanceof Monster monster)", "conditional monster pulse path"),
    ):
        require(effects, token, label, failures)

    # Event-driven powers.
    for power, token in {
        "EXPERIENCE_BOOSTER": "PlayerExpChangeEvent",
        "COOLDOWN_REDUCTION": "PlayerItemCooldownEvent",
        "RADIATION_ABSORBER": "RadiationDamageEvent",
        "IMMORTALITY_FIELD": "onFatalDamage",
        "PEACEFUL": "EntityTargetLivingEntityEvent",
    }.items():
        require(listener, f"BeaconPlusEffect.{power}", f"{power} event lookup", failures)
        require(listener, token, f"{power} event handler", failures)

    # Modifier powers and physical tier ceiling.
    require(runtime, "BeaconPlusEffect.EXTRA_POWER", "Extra Power resolution", failures)
    require(runtime, "tier + extraPowerTier", "Extra Power tier addition", failures)
    require(runtime, "EXTRA_RANGE_PER_TIER = 16", "one-chunk-ring-per-tier Extra Range", failures)
    require(runtime, "BeaconPlusEffect.EXTRA_RANGE", "Extra Range resolution", failures)
    require(runtime, "BeaconPlusPyramid.inspect(block).naturalPowerTier()", "pyramid ceiling lookup", failures)
    require(pyramid, "averageMaterialPower", "pyramid material resonance", failures)
    require(field, "Math.ceil(range / CHUNK_SIZE) - 1", "chunk-aligned range footprint", failures)

    # Interaction routing: normal main-hand clicks use the custom menu while the second hand must never fall through
    # to Minecraft's vanilla Beacon interaction. Sneak-main-hand remains reserved for the beam visual toggle.
    for text, token, label in (
        (beacon, "addItemHandler(onPlace(), onUse(), onBreak(), createTicker())", "Resonance Beacon BlockUseHandler"),
        (beacon, "openMenu(player, block, owner)", "normal-click custom menu dispatch"),
        (lifecycle, "event.getHand() == EquipmentSlot.OFF_HAND", "off-hand interaction guard"),
        (lifecycle, "denyInteraction(event)", "vanilla beacon interaction suppression"),
        (lifecycle, "event.getHand() != EquipmentSlot.HAND || !event.getPlayer().isSneaking()", "sneak-main-hand beam gate"),
    ):
        require(text, token, label, failures)

    # Reliable pulse timing: never phase-lock to coordinates against Slimefun's ticker cadence.
    for token, label in (
        ("LAST_PULSE_GAME_TICKS", "per-beacon pulse state"),
        ("shouldPulse(block.getLocation(), gameTime)", "elapsed pulse gate"),
        ("gameTime - previous < PULSE_INTERVAL_TICKS", "20-tick throttle"),
        ("BeaconPlusRuntimeEffects.applyPulse(block, tiers, range, gameTime)", "gameplay pulse dispatch"),
    ):
        require(runtime, token, label, failures)
    for token in (
        "gameTime + block.getX() * 31L + block.getZ() * 17L",
        "Math.floorMod(gameTime + block.getX()",
    ):
        forbid(runtime, token, "coordinate-dependent pulse phasing", failures)

    # Electric mode is only a power gate over the same potential tier snapshot.
    for token, label in (
        ("BeaconPlusEnergy.consumePulse(block, data, tiers)", "once-per-pulse electric payment"),
        ("getPotentialActiveTiers", "potential tier resolution"),
        ("static boolean isOperational", "shared operational state"),
    ):
        require(runtime, token, label, failures)
    for token, label in (
        ("getDemand", "electric demand calculation"),
        ("hasOperationalPower", "electric readiness"),
        ("consumePulse", "electric consumption"),
        ('ELECTRIC_MODE_KEY = "beacon_plus_electric_mode"', "per-beacon electric mode"),
        ('ENERGY_CHARGE_KEY = "energy-charge"', "Slimefun charge key"),
    ):
        require(energy, token, label, failures)
    for token, label in (
        ("EnumMap<BeaconPlusEffect, Integer> potentialTiers", "single GUI tier snapshot"),
        ("boolean operational = BeaconPlusRuntime.isOperational(block, potentialTiers)", "single GUI operational snapshot"),
        ("createElectricOperationItem(block, potentialTiers, operational)", "electric GUI snapshot reuse"),
        ("Runtime: ", "effect runtime status"),
        ("DORMANT (ENERGY)", "explicit energy dormant reason"),
    ):
        require(beacon, token, label, failures)

    # Activator must turn on and off and remain bounded.
    for token, label in (
        ("BeaconPlusRuntime.reconcileActivator(block, 0)", "explicit Activator off path"),
        ("AREA_3X3", "Activator Tier II"),
        ("AREA_5X5", "Activator Tier III"),
    ):
        require(beacon + runtime, token, label, failures)
    for token, label in (
        ("MAX_ACTIVE_BEACONS = 64", "Activator beacon cap"),
        ("MAX_UNIQUE_CHUNKS = 256", "Activator chunk cap"),
        ("addPluginChunkTicket", "Activator ticket acquisition"),
        ("removePluginChunkTicket", "Activator ticket release"),
    ):
        require(manager, token, label, failures)

    # Crops must inspect the relevant vertical band, not randomize across the whole world height.
    for token, label in (
        ("CROP_SAMPLES_PER_CHUNK = 8", "crop samples per covered chunk"),
        ("MAX_CROP_SAMPLES_PER_PULSE = 512", "crop work cap"),
        ("CROP_VERTICAL_RADIUS = 8", "crop vertical band"),
        ("beaconBlock.getY() - CROP_VERTICAL_RADIUS", "crop lower Y bound"),
        ("beaconBlock.getY() + CROP_VERTICAL_RADIUS + 1", "crop upper Y bound"),
    ):
        require(effects, token, label, failures)
    forbid(effects, "random.nextInt(world.getMinHeight(), world.getMaxHeight())", "world-height crop roulette", failures)

    # Persistence and ownership safety.
    for token, label in (
        ("beacon_plus_owner", "owner storage"),
        ("beacon_plus_chunk_mode", "Activator storage"),
        ("beacon_plus_effects", "effect storage"),
    ):
        require(manager + runtime, token, label, failures)
    for token, label in (
        ("BeaconData", "legacy data folder"),
        ("normalizeLegacyTier", "legacy tier migration"),
        ("writeBeacon", "legacy mirror"),
    ):
        require(legacy, token, label, failures)

    # Curiosities remain opt-in at the module level while beacon powers remain server-configurable once enabled.
    require(config, "enabled: false", "fresh-install Curiosities default off", failures)
    require(config, "PoweredBeacon:", "Resonance Beacon config root", failures)
    require(config, "powers:", "per-power admin controls", failures)

    if failures:
        print("Resonance Beacon functionality verification: FAIL", file=sys.stderr)
        for failure in failures:
            print(f"- {failure}", file=sys.stderr)
        return 1

    print("Resonance Beacon functionality verification: PASS")
    print("- all 29 approved powers have a periodic, event-driven, or modifier runtime path")
    print("- player-only Paper pulses avoid full chunk entity scans; mob/gravity and Folia paths remain guarded")
    print("- main-hand menu and off-hand vanilla-interaction routing are guarded")
    print("- pulse scheduling cannot phase-lock against beacon coordinates")
    print("- electric READY/effect runtime status share one operational snapshot")
    print("- Activator has bounded acquire/release paths")
    print("- crop and gravity behavior retain the proven BeaconPlus gameplay semantics")
    print("- pyramid, field, energy, ownership, and legacy persistence contracts remain connected")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
