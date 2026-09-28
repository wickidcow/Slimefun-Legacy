#!/usr/bin/env python3
"""Prevent deprecated ItemMeta string setters from returning to modernized presentation paths."""

from __future__ import annotations

import sys
from pathlib import Path

FILES = (
    "src/main/java/com/xzavier0722/mc/plugin/slimefun4/autocrafter/CrafterSmartPort.java",
    "src/main/java/io/github/thebusybiscuit/slimefun4/api/gps/GPSNetwork.java",
    "src/main/java/io/github/thebusybiscuit/slimefun4/api/items/ItemGroup.java",
    "src/main/java/io/github/thebusybiscuit/slimefun4/core/commands/subcommands/BackpackCommand.java",
    "src/main/java/io/github/thebusybiscuit/slimefun4/core/guide/options/ContributorsMenu.java",
    "src/main/java/io/github/thebusybiscuit/slimefun4/core/guide/options/DoctorGuideMenu.java",
    "src/main/java/io/github/thebusybiscuit/slimefun4/core/guide/options/DoctorOperationsCenterMenu.java",
    "src/main/java/io/github/thebusybiscuit/slimefun4/core/guide/options/GuideModeOption.java",
    "src/main/java/io/github/thebusybiscuit/slimefun4/core/guide/options/ResourcePackGuideMenu.java",
    "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/guide/enhanced/IndexedEnhancedSurvivalSlimefunGuide.java",
    "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/guide/enhanced/LegacyRecipeUsageBrowser.java",
    "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios/BeaconPlus.java",
    "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios/BeaconPlusAreaVisualizer.java",
    "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/electric/EnergyRegulator.java",
    "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/electric/machines/enchanting/EnchantmentMachineRuntime.java",
    "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/extratools/CobblestoneGenerator.java",
    "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/extratools/ExtraToolsItems.java",
    "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/magic8ball/Magic8BallItems.java",
    "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/setup/AdventurersCuriosSetup.java",
    "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/setup/AdventurersToolsSetup.java",
    "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/setup/ExtraGearSetup.java",
    "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/setup/IrradiatedArsenalSetup.java",
    "src/main/java/io/github/thebusybiscuit/slimefun4/utils/ChestMenuUtils.java",
    "src/main/java/io/github/thebusybiscuit/slimefun4/utils/FireworkUtils.java",
    "src/main/java/io/github/thebusybiscuit/slimefun4/utils/itemstack/ColoredFireworkStar.java",
    "src/main/java/io/github/thebusybiscuit/slimefun4/utils/itemstack/SlimefunGuideItem.java",
)

FORBIDDEN = (".setDisplayName(", ".setLore(")


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
    failures: list[str] = []

    for relative in FILES:
        path = root / relative
        if not path.is_file():
            failures.append(f"Missing modernization target: {relative}")
            continue

        source = path.read_text(encoding="utf-8")
        for token in FORBIDDEN:
            if token in source:
                failures.append(f"{relative} reintroduced deprecated ItemMeta string setter: {token}")

    if failures:
        print("Presentation API modernization verification: FAIL")
        for failure in failures:
            print(f"- {failure}")
        return 1

    print("Presentation API modernization verification: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
