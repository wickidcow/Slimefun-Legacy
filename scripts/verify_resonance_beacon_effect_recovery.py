#!/usr/bin/env python3
"""Protect Resonance Beacon effect-state recovery and reliable pulse scheduling."""

from __future__ import annotations

import sys
from pathlib import Path


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
    base = root / "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios"
    progression = (base / "BeaconPlusProgression.java").read_text(encoding="utf-8")
    runtime = (base / "BeaconPlusRuntime.java").read_text(encoding="utf-8")

    failures: list[str] = []

    for token in (
        "ensureMinimumTier",
        "if (current >= target)",
        "data.set(path(owner, effect), target)",
    ):
        if token not in progression:
            failures.append(f"Progression recovery invariant missing: {token}")

    for token in (
        "recoverConfiguredTier",
        "BeaconPlusLegacyDataStore.isLegacyImported(location)",
        "!getConfiguredEffects(location).contains(effect)",
        "BeaconPlusProgression.ensureMinimumTier(owner, effect, minimumTier)",
        "case SINGLE -> 1",
        "case AREA_3X3 -> 2",
        "case AREA_5X5 -> 3",
        "BeaconPlusLegacyDataStore.LEGACY_IMPORTED_OWNER",
    ):
        if token not in runtime:
            failures.append(f"Runtime configured-effect recovery invariant missing: {token}")

    if "return unlocked > 0 ? unlocked : recoverConfiguredTier(location, owner, effect);" not in runtime:
        failures.append("Existing non-zero progression must win before configured-effect recovery")

    for token in (
        "LAST_PULSE_GAME_TICKS",
        "if (!shouldPulse(block.getLocation(), gameTime))",
        "gameTime - previous < PULSE_INTERVAL_TICKS",
        "LAST_PULSE_GAME_TICKS.remove(key)",
        "LAST_PULSE_GAME_TICKS.clear()",
    ):
        if token not in runtime:
            failures.append(f"Reliable pulse scheduling invariant missing: {token}")

    for forbidden in (
        "gameTime + block.getX() * 31L + block.getZ() * 17L",
        "Math.floorMod(gameTime + block.getX()",
    ):
        if forbidden in runtime:
            failures.append(f"Coordinate-dependent pulse phase must not return: {forbidden}")

    if failures:
        print("Resonance Beacon effect-state/pulse verification failed:", file=sys.stderr)
        for failure in failures:
            print(f" - {failure}", file=sys.stderr)
        return 1

    print("Resonance Beacon effect-state and pulse verification passed.")
    print("- existing configured native powers can recover a missing Tier I progression entry")
    print("- Activator 3x3/5x5 modes preserve Tier II/III recovery")
    print("- explicit legacy BeaconData tiers remain authoritative")
    print("- unconfigured powers are never unlocked by recovery")
    print("- every beacon uses elapsed game-time pulse throttling independent of coordinates")
    print("- coordinate-dependent modulo phasing cannot silently suppress all effects")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
