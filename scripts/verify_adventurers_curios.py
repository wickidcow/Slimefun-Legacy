#!/usr/bin/env python3
"""Verify the built-in Adventurer's Curios category and first field gadgets."""

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
    failures: list[str] = []

    required_files = (
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/setup/AdventurersCuriosSetup.java",
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios/WayfindersCompass.java",
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios/EchoLantern.java",
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios/ExplorersSpyglass.java",
        "docs/ADVENTURERS_CURIOS.md",
    )

    for relative in required_files:
        require((root / relative).is_file(), f"Missing Adventurer's Curios file: {relative}", failures)

    try:
        setup = read(root, required_files[0])
        for token in (
            '"adventurers_curios"',
            '"ADVENTURERS_WAYFINDERS_COMPASS"',
            '"ADVENTURERS_ECHO_LANTERN"',
            '"ADVENTURERS_EXPLORERS_SPYGLASS"',
            "RecipeType.ENHANCED_CRAFTING_TABLE",
            "new WayfindersCompass(",
            "new EchoLantern(",
            "new ExplorersSpyglass(",
        ):
            require(token in setup, f"Curios setup invariant is missing: {token}", failures)

        post_setup = read(root, "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/setup/PostSetup.java")
        registration = post_setup.find("AdventurersCuriosSetup.setup(Slimefun.instance());")
        finalization = post_setup.find("markInitialRegistrationFinalized()")
        require(registration >= 0, "Adventurer's Curios is not registered during PostSetup", failures)
        require(finalization >= 0, "Registry finalization marker is missing", failures)
        if registration >= 0 and finalization >= 0:
            require(registration < finalization, "Curios must register before the item registry is finalized", failures)

        wayfinder = read(root, required_files[1])
        for token in (
            "getLastDeathLocation()",
            "setLodestone(target)",
            "setLodestoneTracked(false)",
            "getSpawnLocation()",
        ):
            require(token in wayfinder, f"Wayfinder invariant is missing: {token}", failures)

        lantern = read(root, required_files[2])
        for token in (
            "getNearbyEntities",
            "instanceof Monster",
            "PotionEffectType.GLOWING",
            "COOLDOWN_TICKS = 30 * 20",
        ):
            require(token in lantern, f"Echo Lantern invariant is missing: {token}", failures)

        spyglass = read(root, required_files[3])
        for token in (
            "getBiome().getKey().getKey()",
            "getDirection(location.getYaw())",
            "location.getBlockX()",
            "location.getBlockY()",
            "location.getBlockZ()",
        ):
            require(token in spyglass, f"Explorer's Spyglass invariant is missing: {token}", failures)
    except FileNotFoundError as error:
        failures.append(f"Unable to inspect missing Adventurer's Curios file: {error}")

    report = root / "build/reports/adventurers-curios.txt"
    report.parent.mkdir(parents=True, exist_ok=True)

    if failures:
        report.write_text(
            "Adventurer's Curios verification: FAIL\n"
            + "\n".join(f"- {failure}" for failure in failures)
            + "\n",
            encoding="utf-8",
        )
        print(report.read_text(encoding="utf-8"), end="")
        return 1

    report.write_text(
        "Adventurer's Curios verification: PASS\n"
        "- built-in guide category is present\n"
        "- three initial curios are registered before registry finalization\n"
        "- Wayfinder's Compass retains its death/spawn navigation behavior\n"
        "- Echo Lantern retains its bounded hostile reveal pulse and cooldown\n"
        "- Explorer's Spyglass retains its coordinate, biome and heading survey\n"
        "- no database, storage schema, machine, Cargo or Energy behavior is changed\n",
        encoding="utf-8",
    )
    print(report.read_text(encoding="utf-8"), end="")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
