#!/usr/bin/env python3
"""Keep modern Legacy Curios code on Adventure instead of Bukkit ChatColor."""

from __future__ import annotations

import sys
from pathlib import Path

FILES = (
    "StormGlass.java",
    "EchoLocator.java",
    "EchoLantern.java",
    "MinersCanary.java",
    "SurveyorsRod.java",
    "GeigerCounter.java",
    "RescueWhistle.java",
    "EmergencyFlare.java",
    "FieldRepairKit.java",
    "ContainmentTrap.java",
    "SalvagersMagnet.java",
    "ChunkStabilizer.java",
    "BastionResonator.java",
    "ExpeditionJournal.java",
    "WayfindersCompass.java",
    "ExplorersSpyglass.java",
    "WayfarersLodestone.java",
    "EmergencyParachute.java",
    "BeaconPlus.java",
    "BeaconPlusAreaVisualizer.java",
    "BeaconPlusLifecycleListener.java",
    "BeaconPlusAdminCommand.java",
)

STARTER_FILES = ("UniversalLeash.java",)

FORBIDDEN = ("org.bukkit.ChatColor", "ChatColor.")


def check(path: Path, root: Path, failures: list[str]) -> None:
    text = path.read_text(encoding="utf-8")
    for token in FORBIDDEN:
        if token in text:
            failures.append(f"{path.relative_to(root)} still uses {token}")


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
    curios = root / "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios"
    failures: list[str] = []

    for name in FILES:
        check(curios / name, root, failures)
    for name in STARTER_FILES:
        check(curios / "starter" / name, root, failures)

    if failures:
        print("Curios Adventure formatting verification: FAIL")
        for failure in failures:
            print(f"- {failure}")
        return 1

    print("Curios Adventure formatting verification: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
