#!/usr/bin/env python3
"""Verify the Resonance Beacon effect-area visualizer remains bounded, exact, and display-only."""

from __future__ import annotations

import sys
from pathlib import Path


def read(root: Path, relative: str) -> str:
    path = root / relative
    if not path.is_file():
        raise SystemExit(f"Resonance Beacon area visualizer verification failed: missing {relative}")
    return path.read_text(encoding="utf-8")


def require(text: str, needle: str, label: str) -> None:
    if needle not in text:
        raise SystemExit(f"Resonance Beacon area visualizer verification failed: missing {label}: {needle}")


def forbid(text: str, needle: str, label: str) -> None:
    if needle in text:
        raise SystemExit(f"Resonance Beacon area visualizer verification failed: forbidden {label}: {needle}")


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()

    visualizer = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios/BeaconPlusAreaVisualizer.java",
    )
    for needle, label in (
        ('SHOW_AREA_KEY = "beacon_plus_show_effect_area"', "persistent area-display setting"),
        ("private static final int MENU_SLOT = 45;", "dedicated menu slot"),
        ("private static final int MAX_PARTICLES_PER_VIEWER = 512;", "per-viewer particle cap"),
        ("private static final long RENDER_INTERVAL_TICKS = 40L;", "bounded render cadence"),
        ("BeaconPlusRuntime.getEffectiveRange(block)", "effective runtime range lookup"),
        ("BeaconPlusField.footprint", "shared chunk-aligned field footprint"),
        ("world.isChunkLoaded", "unloaded-chunk guard"),
        ("Slimefun.getSchedulerService().runFor", "player-owned scheduler handoff"),
        ("Particle.END_ROD", "packet-only area particles"),
        ("StorageCacheUtils.setData(location, SHOW_AREA_KEY, Boolean.toString(enabled))", "toggle persistence"),
        ('"Show Effect Area"', "menu control label"),
        ('"Display only • never loads extra chunks"', "non-gameplay safety explanation"),
        ("MAX_PARTICLES_PER_VIEWER", "bounded particle accounting"),
    ):
        require(visualizer, needle, label)

    # The effect-area outline is opt-in and visual only. It must never become a chunk loader or marker-entity system.
    for needle, label in (
        ("loadChunk(", "visualizer chunk loading"),
        ("setForceLoaded", "forced chunk loading"),
        ("addPluginChunkTicket", "plugin chunk tickets"),
        ("spawnEntity", "marker entity spawning"),
        ("ArmorStand", "armor-stand markers"),
    ):
        forbid(visualizer, needle, label)

    field = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios/BeaconPlusField.java",
    )
    for needle, label in (
        ("private static final double CHUNK_SIZE = 16.0D;", "Minecraft chunk width"),
        ("Math.ceil(range / CHUNK_SIZE) - 1", "range-to-chunk radius calculation"),
        ("record ChunkFootprint", "shared chunk footprint record"),
        ("boolean containsChunk", "chunk containment check"),
        ("int widthChunks()", "footprint width reporting"),
    ):
        require(field, needle, label)

    lifecycle = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios/BeaconPlusLifecycleListener.java",
    )
    require(lifecycle, "BeaconPlusAreaVisualizer.register(plugin);", "visualizer lifecycle registration")
    require(lifecycle, "BeaconPlusAreaVisualizer.shutdown();", "visualizer shutdown cleanup")
    require(lifecycle, "BeaconPlusBeam.setVisualsEnabled", "independent powered-beam visual toggle")
    require(lifecycle, "Sneak-right-click", "yellow-beam control guidance")

    beam = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios/BeaconPlusBeam.java",
    )
    for needle, label in (
        ('VISUALS_ENABLED_KEY = "beacon_plus_visuals_enabled"', "powered-beam persistence"),
        ('stored == null || !"false".equalsIgnoreCase(stored)', "default-on yellow beam semantics"),
        ("Particle.DUST", "yellow beam particles"),
        ("Particle.ELECTRIC_SPARK", "yellow beam spark accents"),
        ("world.isChunkLoaded", "legacy-filter cleanup chunk guard"),
    ):
        require(beam, needle, label)
    forbid(beam, "spawnEntity", "yellow beam marker entities")
    forbid(beam, "setForceLoaded", "yellow beam forced chunk loading")

    print("Resonance Beacon area visualizer verification passed.")
    print("- effect-area outline uses the current chunk-aligned runtime footprint")
    print("- display toggle is persistent, opt-in, and independent of gameplay powers")
    print("- rendering is packet-only, scheduler-owned, particle-capped, and never loads chunks")
    print("- powered yellow beam remains a separate default-on visual with its own toggle")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
