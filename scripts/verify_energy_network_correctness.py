#!/usr/bin/env python3
"""Verify EnergyNet live-component, charge-bound and unloaded-chunk invariants."""

from __future__ import annotations

import re
import sys
from pathlib import Path


def read(root: Path, relative: str) -> str:
    path = root / relative
    if not path.is_file():
        raise SystemExit(f"Energy network correctness failed: missing file {relative}")
    return path.read_text(encoding="utf-8")


def compact(text: str) -> str:
    return " ".join(text.split())


def require(text: str, needle: str, label: str) -> None:
    if needle not in text:
        raise SystemExit(f"Energy network correctness failed: missing {label}: {needle}")


def require_absent(text: str, needle: str, label: str) -> None:
    if needle in text:
        raise SystemExit(f"Energy network correctness failed: forbidden {label}: {needle}")


def require_before(text: str, first: str, second: str, label: str) -> None:
    first_at = text.find(first)
    second_at = text.find(second)
    if first_at < 0 or second_at < 0 or first_at >= second_at:
        raise SystemExit(
            f"Energy network correctness failed: ordering violation for {label}: "
            f"expected {first!r} before {second!r}"
        )


def method_body(text: str, method_name: str) -> str:
    match = re.search(rf"\b{re.escape(method_name)}\s*\([^)]*\)\s*\{{", text)
    if not match:
        raise SystemExit(f"Energy network correctness failed: missing method {method_name}")

    start = match.end() - 1
    depth = 0
    for index in range(start, len(text)):
        char = text[index]
        if char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                return text[start + 1 : index]

    raise SystemExit(f"Energy network correctness failed: unterminated method {method_name}")


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
    source = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/core/networks/energy/EnergyNet.java",
    )
    source_compact = compact(source)

    tick = compact(method_body(source, "tick"))
    generators = compact(method_body(source, "tickAllGenerators"))
    capacitors = compact(method_body(source, "tickAllCapacitors"))
    storage = compact(method_body(source, "storeRemainingEnergy"))
    accessible = compact(method_body(source, "isEnergyLocationAccessible"))
    safe_capacity = compact(method_body(source, "getSafeCapacity"))
    safe_charge = compact(method_body(source, "getSafeCharge"))
    set_safe_charge = compact(method_body(source, "setSafeCharge"))
    resolve_component = compact(method_body(source, "resolveLiveComponent"))
    resolve_generator = compact(method_body(source, "resolveLiveGenerator"))

    # Preserve the established network transaction order: collect supply, satisfy consumers,
    # then place the leftover back into network storage. Generator profiler accounting may be
    # carried alongside the supply result, but it must not alter that transaction order.
    generator_phase = "GeneratorTickResult generatorResult = tickAllGenerators()"
    consumer_phase = "for (Map.Entry<Location, EnergyNetComponent> entry : consumers.entrySet())"
    storage_phase = "storeRemainingEnergy(remainingEnergy)"
    require(tick, generator_phase, "generator supply phase")
    require(tick, "tickAllCapacitors()", "capacitor supply phase")
    require(tick, "NumberUtils.flowSafeAddition(generatorResult.supply(), capacitorsSupply)", "combined network supply")
    require(tick, consumer_phase, "consumer phase")
    require(tick, storage_phase, "leftover storage phase")
    require_before(tick, generator_phase, consumer_phase, "supply before consumers")
    require_before(tick, consumer_phase, storage_phase, "consumers before leftover storage")
    require(tick, "profilerTimestamp += generatorResult.profiledNanos()", "generator profiler subtraction accounting")

    # Paper accessibility is not a chunk-loaded guarantee. Energy state must never force or
    # touch an unloaded component chunk.
    require(accessible, "isLocationAccessible(location)", "Folia ownership guard")
    require(accessible, ".isChunkLoaded(", "loaded-chunk guard")
    require(source_compact, "if (!isEnergyLocationAccessible(loc))", "energy-node accessibility checks")

    # Cached node identities can become stale between topology updates. Every active type must
    # re-resolve against the current Slimefun id before state is read or written.
    require(tick, "resolveLiveComponent( loc, entry.getValue(), data.getSfId(), EnergyNetComponentType.CONSUMER, consumers)", "consumer identity refresh")
    require(capacitors, "resolveLiveComponent( loc, entry.getValue(), data.getSfId(), EnergyNetComponentType.CAPACITOR, capacitors)", "capacitor identity refresh")
    require(storage, "EnergyNetComponentType.CAPACITOR, capacitors", "capacitor identity refresh before storage")
    require(generators, "resolveLiveGenerator(loc, provider, data.getSfId())", "generator identity refresh")
    require(storage, "resolveLiveGenerator(loc, entry.getValue(), data.getSfId())", "generator identity refresh before storage")
    require(resolve_component, "cache.remove(loc, cached)", "stale component cache eviction")
    require(resolve_generator, "generators.remove(loc, cached)", "stale generator cache eviction")

    # Data that has not finished loading must be requested and skipped rather than treated as
    # zero and overwritten.
    require(tick, "if (!data.isDataLoaded()) { StorageCacheUtils.requestLoad(data); continue; }", "consumer load guard")
    require(capacitors, "if (!data.isDataLoaded()) { StorageCacheUtils.requestLoad(data); continue; }", "capacitor load guard")
    require(generators, "if (!data.isDataLoaded()) { StorageCacheUtils.requestLoad(data); continue; }", "generator load guard")
    require(storage, "StorageCacheUtils.requestLoad(data)", "storage load guard")

    # Network-visible energy must always remain in legal bounds even if old persisted data or
    # an addon implementation returns an invalid number.
    require(safe_capacity, "Math.max(0L, component.getCapacityLong())", "non-negative capacity bound")
    require(safe_charge, "NumberUtils.clamp(0L, component.getChargeLong(loc, data), capacity)", "charge 0..capacity bound")
    require(set_safe_charge, "NumberUtils.clamp(0L, charge, capacity)", "write 0..capacity bound")
    require(set_safe_charge, "component.setCharge(loc, safeCharge, data)", "loaded-container energy write")
    require(generators, "Math.max(0L, provider.getGeneratedOutputLong(loc, data))", "non-negative generator output")
    require(generators, "getSafeCharge(provider, loc, data, capacity)", "bounded generator stored charge")
    require(capacitors, "getSafeCharge(component, loc, data, capacity)", "bounded capacitor supply")
    require(tick, "getSafeCharge(component, loc, data, capacity)", "bounded consumer charge")

    # Direct location-only charge reads/writes can race storage reloads and bypass the already
    # validated data container. Keep EnergyNet on the loaded-container path.
    require_absent(source_compact, ".getChargeLong(loc);", "location-only charge read")
    require_absent(source_compact, ".setCharge(loc, capacity);", "location-only capacity write")
    require_absent(source_compact, ".setCharge(loc, remainingEnergy);", "location-only remainder write")

    # Stable vanilla player-head power states are a presentation mirror, not energy truth. Avoid
    # re-reading Bukkit block data every energy tick. Periodic self-heal checks are distributed
    # across a stable per-location phase so many networks do not all revalidate in the same tick.
    bridge = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/core/networks/energy/VanillaPowerStateBridge.java",
    )
    bridge_compact = compact(bridge)
    bridge_sync = compact(method_body(bridge, "sync"))
    bridge_cache = compact(method_body(bridge, "cache"))

    require(bridge_compact, "REVALIDATE_INTERVAL_TICKS = 20L", "20-tick power-state revalidation")
    require(bridge_compact, "MAX_CACHED_LOCATIONS = 32_768", "bounded power-state cache")
    require(bridge_compact, "Map<Location, CachedState> LAST_APPLIED_STATE = new ConcurrentHashMap<>()", "thread-safe power-state cache")
    require(bridge_sync, "CachedState cached = LAST_APPLIED_STATE.get(location)", "cached desired-state lookup")
    require(bridge_sync, "cached.powered == powered", "unchanged-state fast path")
    require(bridge_sync, "gameTime < cached.nextValidationTick", "staggered periodic self-heal gate")
    require_before(bridge_sync, "CachedState cached = LAST_APPLIED_STATE.get(location)", "Block block = location.getBlock()", "cache check before Bukkit block access")
    require(bridge_sync, "powerable.isPowered() != powered", "write only on actual vanilla-state change")
    require(bridge_sync, "cache(location, powered, gameTime)", "successful validation refresh")
    require(bridge_cache, "phase = Math.floorMod(location.hashCode(), REVALIDATE_INTERVAL_TICKS)", "stable per-location validation phase")
    require(bridge_cache, "nextValidationTick = gameTime + 1L", "future validation scheduling")
    require(bridge_cache, "Math.floorMod(phase - Math.floorMod(nextValidationTick, REVALIDATE_INTERVAL_TICKS), REVALIDATE_INTERVAL_TICKS)", "phase-aligned validation offset")
    require(bridge_cache, "LAST_APPLIED_STATE.size() >= MAX_CACHED_LOCATIONS", "cache size cap check")
    require(bridge_cache, "LAST_APPLIED_STATE.clear()", "cache overflow recovery")
    require(bridge_cache, "location.clone()", "stable cache key")

    print("Energy network correctness verification passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
