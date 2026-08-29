#!/usr/bin/env python3
"""Guard the Resonance Beacon beam renderer against invisible remote particle work."""

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
    beam_path = "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios/BeaconPlusBeam.java"
    failures: list[str] = []

    try:
        beam = read(root, beam_path)

        for token, label in (
            ("private static final int MAX_BEAM_SEGMENTS = 64", "bounded full-height beam segments"),
            ("private static final double VISUAL_VIEW_RANGE = 36.0D * 16.0D", "generous remote-view range"),
            ("VISUAL_VIEW_RANGE_SQUARED = VISUAL_VIEW_RANGE * VISUAL_VIEW_RANGE", "squared viewer range"),
            ("!Slimefun.getSchedulerService().isFolia() && !hasNearbyViewer(beaconBlock)", "Paper-family viewer gate with Folia exclusion"),
            ("for (Player player : world.getPlayers())", "world-player viewer scan"),
            ("dx * dx + dz * dz <= VISUAL_VIEW_RANGE_SQUARED", "horizontal distance-squared viewer check"),
            ("private static final Set<BeamKey> LEGACY_FILTER_CHECKED", "one-way legacy filter cleanup cache"),
        ):
            require(token in beam, f"missing {label}: {token}", failures)

        gate = beam.find("!Slimefun.getSchedulerService().isFolia() && !hasNearbyViewer(beaconBlock)")
        render = beam.find("renderPoweredVisual(beaconBlock);")
        require(gate >= 0 and render >= 0 and gate < render,
                "remote-viewer gate must run before powered beam particle rendering", failures)

        require(
            "getNearbyEntities" not in beam,
            "beam viewer detection must not replace the cheap player-list check with an entity scan",
            failures,
        )
        require(
            "chunk.getEntities" not in beam and "getEntities()" not in beam,
            "beam viewer detection must not enumerate chunk entities",
            failures,
        )
        require(
            "isVisualsEnabled(location)" in beam,
            "live visual-toggle storage check must remain active",
            failures,
        )
        require(
            beam.find("isVisualsEnabled(location)") < gate,
            "disabled visuals must return before viewer/particle work",
            failures,
        )
    except Exception as error:
        failures.append(f"verification could not inspect Resonance Beacon beam source: {error}")

    if failures:
        print("Resonance Beacon beam performance verification: FAIL", file=sys.stderr)
        for failure in failures:
            print(f"- {failure}", file=sys.stderr)
        return 1

    print("Resonance Beacon beam performance verification: PASS")
    print("- Paper-family remote beacons skip invisible particle dispatch when no viewer is within 36 chunks")
    print("- visible beam density remains bounded at the existing 64-segment ceiling")
    print("- Folia keeps the ownership-safe preexisting render path")
    print("- one-way legacy glass cleanup no longer rereads storage every powered pulse")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
