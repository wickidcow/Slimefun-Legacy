#!/usr/bin/env python3
"""Verify Beacon Plus Area Preview remains lightweight, exact, and default-on."""

from __future__ import annotations

import sys
from pathlib import Path


def read(root: Path, relative: str) -> str:
    path = root / relative
    if not path.is_file():
        raise SystemExit(f"Beacon Plus Area Preview verification failed: missing {relative}")
    return path.read_text(encoding="utf-8")


def require(text: str, needle: str, label: str) -> None:
    if needle not in text:
        raise SystemExit(f"Beacon Plus Area Preview verification failed: missing {label}: {needle}")


def forbid(text: str, needle: str, label: str) -> None:
    if needle in text:
        raise SystemExit(f"Beacon Plus Area Preview verification failed: forbidden {label}: {needle}")


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()

    preview = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios/BeaconPlusAreaPreview.java",
    )
    require(preview, 'ENABLED_KEY = "beacon_plus_area_preview_enabled"', "persistent preview setting")
    require(preview, 'stored == null || !"false".equalsIgnoreCase(stored)', "default-on preview semantics")
    require(preview, "BeaconPlusRuntime.getEffectiveFieldArea", "effective field-area lookup")
    require(preview, "area.getRadius()", "chunk-radius boundary calculation")
    require(preview, "world.isChunkLoaded", "unloaded-chunk guard")
    require(preview, "Slimefun.runSyncAt", "Folia region handoff")
    require(preview, "Particle.DUST", "lightweight boundary particles")
    require(preview, "Particle.ELECTRIC_SPARK", "visible corner markers")
    forbid(preview, "loadChunk(", "preview chunk loading")
    forbid(preview, "setForceLoaded", "preview forced chunk loading")
    forbid(preview, "spawnEntity", "preview marker entities")
    forbid(preview, "ArmorStand", "preview armor stands")

    field_area = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios/BeaconPlusFieldArea.java",
    )
    require(field_area, 'ONE_BY_ONE(1, "1x1 Chunk")', "1x1 field tier")
    require(field_area, 'THREE_BY_THREE(3, "3x3 Chunks")', "3x3 field tier")
    require(field_area, 'FIVE_BY_FIVE(5, "5x5 Chunks")', "5x5 field tier")

    beam = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios/BeaconPlusBeam.java",
    )
    require(beam, "BeaconPlusAreaPreview.render(beaconBlock);", "powered preview refresh")
    require(beam, "BeaconPlusAreaPreview.render(location);", "idle/unpowered preview refresh")

    listener = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios/BeaconPlusLifecycleListener.java",
    )
    require(listener, "private static final int AREA_PREVIEW_SLOT = 51;", "dedicated menu slot")
    require(listener, "new ItemStack(Material.LEVER)", "lever menu icon")
    require(listener, '"↑ Area Preview: ON"', "lever-up ON visual label")
    require(listener, '"↓ Area Preview: OFF"', "lever-down OFF visual label")
    require(listener, "BeaconPlusAreaPreview.setEnabled(location, enabled);", "preview toggle persistence")
    require(listener, "Sound.BLOCK_LEVER_CLICK", "lever toggle feedback")
    require(listener, "BeaconPlusRuntime.getEffectiveFieldArea", "menu displays effective area")
    require(listener, "Visual only; effect range is unchanged.", "non-gameplay toggle explanation")

    print("Beacon Plus Area Preview verification passed.")
    print("- preview defaults ON unless explicitly disabled")
    print("- exact effective 1x1/3x3/5x5 field boundary is used")
    print("- sparse particles are used without marker entities or chunk loading")
    print("- menu lever has explicit up/down ON/OFF state and persistence")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
