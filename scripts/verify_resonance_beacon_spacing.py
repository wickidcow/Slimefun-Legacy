#!/usr/bin/env python3
"""Verify Resonance Beacon placement-spacing invariants."""

from __future__ import annotations

import re
import sys
from pathlib import Path


def read(root: Path, relative: str) -> str:
    path = root / relative
    if not path.is_file():
        raise SystemExit(f"Resonance Beacon spacing verification failed: missing {relative}")
    return path.read_text(encoding="utf-8")


def compact(text: str) -> str:
    return " ".join(text.split())


def method_body(text: str, method_name: str) -> str:
    match = re.search(rf"\b{re.escape(method_name)}\s*\([^)]*\)\s*\{{", text)
    if not match:
        raise SystemExit(f"Resonance Beacon spacing verification failed: missing method {method_name}")

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

    raise SystemExit(f"Resonance Beacon spacing verification failed: unterminated method {method_name}")


def require(text: str, token: str, label: str) -> None:
    if token not in text:
        raise SystemExit(f"Resonance Beacon spacing verification failed: missing {label}: {token}")


def forbid(text: str, token: str, label: str) -> None:
    if token in text:
        raise SystemExit(f"Resonance Beacon spacing verification failed: forbidden {label}: {token}")


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
    base = "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios/"
    manager = read(root, base + "BeaconPlusManager.java")
    lifecycle = read(root, base + "BeaconPlusLifecycleListener.java")

    spacing = compact(method_body(manager, "isBeaconWithinChunkRadius"))
    placement = compact(method_body(lifecycle, "onBeaconPlace"))
    activation = compact(method_body(manager, "canActivate"))

    # Placement reserves a square three-chunk radius around every registered Resonance Beacon.
    require(lifecycle, "MINIMUM_BEACON_CHUNK_SPACING = 3", "three-chunk placement radius")
    require(placement, "SlimefunItem.getByItem(event.getItemInHand())", "Resonance Beacon item resolution")
    require(placement, "BeaconPlusManager.ITEM_ID.equals(item.getId())", "Resonance Beacon placement filter")
    require(
        placement,
        "manager.isBeaconWithinChunkRadius( event.getBlockPlaced().getLocation(), MINIMUM_BEACON_CHUNK_SPACING)",
        "manager spacing lookup",
    )
    require(placement, "event.setCancelled(true)", "placement cancellation")
    require(placement, "too close to another Resonance Beacon", "player-facing proximity error")
    require(placement, "more than 3 chunks apart", "player-facing spacing requirement")

    # The manager checks all registered beacons in the same world, regardless of Activator state.
    require(spacing, "UUID worldId = location.getWorld().getUID()", "same-world placement scope")
    require(spacing, "for (BeaconRecord record : records.values())", "registered-beacon scan")
    require(spacing, "other.worldId().equals(worldId)", "cross-world exclusion")
    require(spacing, "Math.abs(otherChunkX - chunkX) <= radius", "X chunk-radius bound")
    require(spacing, "Math.abs(otherChunkZ - chunkZ) <= radius", "Z chunk-radius bound")
    forbid(spacing, "chunkMode()", "Activator-dependent placement spacing")
    forbid(spacing, "ticketReferences", "chunk-ticket-dependent placement spacing")

    # User-requested policy is placement-only. Activator enable/upgrade keeps the existing safety-cap behavior.
    forbid(manager, "isChunkCoveredByActiveBeacon", "old Activator-coverage placement rule")
    forbid(manager, "introducesCoverageOverlap", "Activator overlap restriction")
    forbid(activation, "isBeaconWithinChunkRadius", "placement spacing applied to Activator changes")

    print("Resonance Beacon spacing verification passed.")
    print("- placement is blocked within three chunks of any registered Resonance Beacon in the same world")
    print("- the player receives a clear too-close error")
    print("- Activator enable/upgrade behavior remains governed only by its existing safety caps")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
