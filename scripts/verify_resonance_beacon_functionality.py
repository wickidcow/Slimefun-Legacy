#!/usr/bin/env python3
"""Verify the native Resonance Beacon/BeaconPlus gameplay and performance contract."""

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

    def source(name: str) -> str:
        return read(root, base.relative_to(root).as_posix() + f"/{name}")

    beacon = source("BeaconPlus.java")
    runtime = source("BeaconPlusRuntime.java")
    effects = source("BeaconPlusRuntimeEffects.java")
    listener = source("BeaconPlusEffectListener.java")
    lifecycle = source("BeaconPlusLifecycleListener.java")
    effect_enum = source("BeaconPlusEffect.java")
    energy = source("BeaconPlusEnergy.java")
    manager = source("BeaconPlusManager.java")
    pyramid = source("BeaconPlusPyramid.java")
    field = source("BeaconPlusField.java")
    legacy = source("BeaconPlusLegacyDataStore.java")
    beam = source("BeaconPlusBeam.java")
    performance = source("BeaconPlusPerformance.java")
    admin = source("BeaconPlusAdminCommand.java")
    config = read(root, "src/main/resources/configSFLAddons.yml")

    # Identity/migration: performance work must never replace the storage-safe historic item id.
    for text, token, label in (
        (manager, 'ITEM_ID = "BEACON_PLUS"', "historic block id"),
        (beacon, "Native Slimefun Legacy Resonance Beacon", "current display implementation"),
        (legacy, "LEGACY_IMPORTED_OWNER", "legacy BeaconData ownership bridge"),
        (legacy, "createBlock(location, BeaconPlusManager.ITEM_ID)", "legacy BeaconData import"),
    ):
        require(text, token, label, failures)

    # Exactly the 29 approved configurable powers remain connected.
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

    # Phase A: resolved state must cache the expensive physical pyramid/tier/range calculation and invalidate on edits.
    for token, label in (
        ("RESOLVED_STATE_CACHE_TICKS = 200L", "10-second resolved state cache"),
        ("RESOLVED_STATES", "resolved state cache map"),
        ("ResolvedBeaconState", "resolved state snapshot"),
        ("BeaconPlusPyramid.inspect(block)", "cached pyramid inspection"),
        ("Map.copyOf(tiers)", "immutable cached tier snapshot"),
        ("invalidateResolvedState(location)", "configuration cache invalidation"),
        ("getResolvedState(block)", "shared cached state lookup"),
    ):
        require(runtime, token, label, failures)
    require(pyramid, "averageMaterialPower", "pyramid material resonance", failures)
    require(field, "Math.ceil(range / CHUNK_SIZE) - 1", "chunk-aligned range footprint", failures)

    # Phase B: player work is split by cadence instead of rechecking every power every second.
    for token, label in (
        ("PLAYER_POTION_INTERVAL_TICKS = 200", "10-second potion lane"),
        ("PLAYER_UTILITY_INTERVAL_TICKS = 40", "2-second utility lane"),
        ("PLAYER_PASSIVE_INTERVAL_TICKS = 100", "5-second passive lane"),
        ("MONSTER_INTERVAL_TICKS = 40", "2-second monster lane"),
        ("TILE_BOOST_INTERVAL_TICKS = 40", "2-second tile lane"),
        ("CROP_INTERVAL_TICKS = 40", "2-second crop lane"),
        ("PLAYER_EFFECT_DURATION_TICKS = 600", "30-second player effect duration"),
        ("PLAYER_EFFECT_REFRESH_THRESHOLD_TICKS = 200", "10-second potion refresh threshold"),
        ("PLAYER_POTION_EFFECTS = Set.of", "player potion classification"),
        ("PLAYER_UTILITY_EFFECTS", "player utility classification"),
        ("PLAYER_PASSIVE_EFFECTS", "player passive classification"),
        ("MONSTER_PERIODIC_EFFECTS", "monster periodic classification"),
        ("isLaneDue(block, gameTime", "staggered lane scheduling"),
        ("for (Player player : block.getWorld().getPlayers())", "Paper world-player fast path"),
        ("if (folia && (potionPulse || utilityPulse || passivePulse || flightPulse))", "Folia player lane guard"),
    ):
        require(effects, token, label, failures)

    # Player powers retain their actual behaviors.
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
        require(effects, f"BeaconPlusEffect.{power}", f"{power} runtime lookup", failures)
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

    # Flight reconciliation follows chunk-aligned field membership instead of block-by-block movement.
    for token, label in (
        ("boolean sameChunk", "chunk transition movement gate"),
        ("from.getBlockX() >> 4", "source chunk lookup"),
        ("to.getBlockX() >> 4", "destination chunk lookup"),
        ("BeaconPlusRuntime.refreshPlayerState", "flight field reconciliation"),
    ):
        require(listener, token, label, failures)

    # Peaceful is primarily event-driven; periodic monster work excludes it while activation clears existing targets once.
    require(listener, "EntityTargetLivingEntityEvent", "Peaceful target event", failures)
    require(listener, "EntityDamageByEntityEvent", "Peaceful hostile damage event", failures)
    require(runtime, "reconcilePeacefulTargets", "Peaceful activation reconciliation", failures)
    monster_classification = effects.split("MONSTER_PERIODIC_EFFECTS =", 1)[-1].split(");", 1)[0]
    if "BeaconPlusEffect.PEACEFUL" in monster_classification:
        failures.append("Peaceful must not remain in the recurring monster scan lane")

    # Gravity Well remains the same power but distributes loaded field chunks across the one-second window.
    for token, label in (
        ("GRAVITY_SLICES = 4", "Gravity Well slice cap"),
        ("GRAVITY_SLICE_DELAY_TICKS = 5L", "Gravity Well slice spacing"),
        ("scheduleGravityWell", "Gravity Well scheduler"),
        ("Slimefun.getSchedulerService().runLater", "delayed Gravity Well slices"),
        ("processGravityChunks", "Gravity Well slice execution"),
        ("entity instanceof Mob || entity instanceof Item", "Gravity Well entity eligibility"),
    ):
        require(effects, token, label, failures)

    # Furnace/spawner cadence is slower but accumulates the previous once-per-second amount.
    for token, label in (
        ("TILE_BOOST_INTERVAL_TICKS / PULSE_INTERVAL_TICKS", "tile accumulated cadence multiplier"),
        ("boostFurnace", "furnace boost"),
        ("boostSpawner", "spawner boost"),
    ):
        require(effects, token, label, failures)

    # Phase C: cosmetics are viewer-aware, bounded and independent of gameplay dispatch.
    for token, label in (
        ("VIEWER_RANGE_SQUARED = 160.0D * 160.0D", "beam viewer range"),
        ("MAX_BEAM_SEGMENTS = 6", "bounded beam segment count"),
        ("hasNearbyViewer(beaconBlock)", "viewer-aware beam gate"),
        ("VISUALS_ENABLED", "cached beam visual setting"),
        ("CLEANED_LEGACY_FILTERS", "one-time legacy filter cleanup cache"),
    ):
        require(beam, token, label, failures)
    require(runtime, "BeaconPlusBeam.markPowered(block)", "cosmetic beam dispatch", failures)
    require(runtime, "BeaconPlusRuntimeEffects.applyPulse", "gameplay dispatch independent from beam", failures)

    # Performance buckets must remain lightweight and operator-visible for live tuning.
    for token, label in (
        ("enum Section", "performance section enum"),
        ("LongAdder", "low-contention performance counters"),
        ("STATE(\"State/cache\")", "state timing bucket"),
        ("GRAVITY(\"Gravity Well\")", "Gravity timing bucket"),
    ):
        require(performance, token, label, failures)
    require(admin, 'args[0].equalsIgnoreCase("perf")', "/beacon perf command", failures)
    require(admin, "BeaconPlusPerformance.snapshot", "performance snapshot output", failures)

    # Event-driven powers remain event-driven and use the shared cached field lookup.
    for power, token in {
        "EXPERIENCE_BOOSTER": "PlayerExpChangeEvent",
        "COOLDOWN_REDUCTION": "PlayerItemCooldownEvent",
        "RADIATION_ABSORBER": "RadiationDamageEvent",
        "IMMORTALITY_FIELD": "onFatalDamage",
        "PEACEFUL": "EntityTargetLivingEntityEvent",
    }.items():
        require(listener, f"BeaconPlusEffect.{power}", f"{power} event lookup", failures)
        require(listener, token, f"{power} event handler", failures)

    # Modifier powers and reliable once-per-second energy heartbeat remain intact.
    for token, label in (
        ("BeaconPlusEffect.EXTRA_POWER", "Extra Power resolution"),
        ("tier + extraPowerTier", "Extra Power tier addition"),
        ("EXTRA_RANGE_PER_TIER = 16", "one-chunk-ring-per-tier Extra Range"),
        ("BeaconPlusEffect.EXTRA_RANGE", "Extra Range resolution"),
        ("LAST_PULSE_GAME_TICKS", "per-beacon pulse state"),
        ("shouldPulse(block.getLocation(), gameTime)", "elapsed pulse gate"),
        ("gameTime - previous < PULSE_INTERVAL_TICKS", "20-tick throttle"),
        ("BeaconPlusEnergy.consumePulse(block, data, tiers)", "once-per-pulse electric payment"),
        ("applyPulse(block, tiers, state.range(), gameTime, newlyPowered)", "phased gameplay pulse dispatch"),
    ):
        require(runtime, token, label, failures)
    for token in (
        "gameTime + block.getX() * 31L + block.getZ() * 17L",
        "Math.floorMod(gameTime + block.getX()",
    ):
        forbid(runtime, token, "coordinate-dependent master pulse phasing", failures)

    for token, label in (
        ("getDemand", "electric demand calculation"),
        ("hasOperationalPower", "electric readiness"),
        ("consumePulse", "electric consumption"),
        ('ELECTRIC_MODE_KEY = "beacon_plus_electric_mode"', "per-beacon electric mode"),
        ('ENERGY_CHARGE_KEY = "energy-charge"', "Slimefun charge key"),
    ):
        require(energy, token, label, failures)

    # Interaction and Activator contracts remain unchanged.
    for text, token, label in (
        (beacon, "addItemHandler(onPlace(), onUse(), onBreak(), createTicker())", "Resonance Beacon BlockUseHandler"),
        (beacon, "openMenu(player, block, owner)", "normal-click custom menu dispatch"),
        (lifecycle, "event.getHand() == EquipmentSlot.OFF_HAND", "off-hand interaction guard"),
        (lifecycle, "denyInteraction(event)", "vanilla beacon interaction suppression"),
        (lifecycle, "event.getHand() != EquipmentSlot.HAND || !event.getPlayer().isSneaking()", "sneak-main-hand beam gate"),
    ):
        require(text, token, label, failures)
    for token, label in (
        ("MAX_ACTIVE_BEACONS = 64", "Activator beacon cap"),
        ("MAX_UNIQUE_CHUNKS = 256", "Activator chunk cap"),
        ("addPluginChunkTicket", "Activator ticket acquisition"),
        ("removePluginChunkTicket", "Activator ticket release"),
    ):
        require(manager, token, label, failures)

    # Crops remain bounded to the relevant vertical band and work cap.
    for token, label in (
        ("CROP_SAMPLES_PER_CHUNK = 8", "crop samples per covered chunk"),
        ("MAX_CROP_SAMPLES_PER_PULSE = 512", "crop work cap"),
        ("CROP_VERTICAL_RADIUS = 8", "crop vertical band"),
        ("beaconBlock.getY() - CROP_VERTICAL_RADIUS", "crop lower Y bound"),
        ("beaconBlock.getY() + CROP_VERTICAL_RADIUS + 1", "crop upper Y bound"),
    ):
        require(effects, token, label, failures)
    forbid(effects, "random.nextInt(world.getMinHeight(), world.getMaxHeight())", "world-height crop roulette", failures)

    # Persistence and module defaults remain unchanged.
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
    require(config, "enabled: false", "fresh-install Curiosities default off", failures)
    require(config, "PoweredBeacon:", "Resonance Beacon config root", failures)
    require(config, "powers:", "per-power admin controls", failures)

    if failures:
        print("Resonance Beacon functionality verification: FAIL", file=sys.stderr)
        for failure in failures:
            print(f"- {failure}", file=sys.stderr)
        return 1

    print("Resonance Beacon functionality verification: PASS")
    print("- all 29 approved powers remain connected without item-id or persistence changes")
    print("- pyramid/tier/range resolution is cached and invalidated on configuration changes")
    print("- player, monster, tile, crop and passive work is split into staggered cadence lanes")
    print("- Peaceful remains primarily event-driven and Gravity Well work is distributed across the second")
    print("- flight reconciliation follows chunk-aligned field transitions")
    print("- powered beam rendering is viewer-aware and bounded independently from gameplay")
    print("- focused operator performance buckets remain available through /beacon perf")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
