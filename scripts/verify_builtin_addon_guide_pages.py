#!/usr/bin/env python3
"""Verify built-in ExtraGear and ExtraTools retain standalone-style guide pages."""

from __future__ import annotations

import sys
from pathlib import Path


def read(root: Path, relative: str) -> str:
    path = root / relative
    if not path.is_file():
        raise AssertionError(f"Missing required file: {relative}")
    return path.read_text(encoding="utf-8")


def require(source: str, needle: str, message: str) -> None:
    if needle not in source:
        raise AssertionError(message)


def forbid(source: str, needle: str, message: str) -> None:
    if needle in source:
        raise AssertionError(message)


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()

    try:
        gear = read(
            root,
            "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/setup/ExtraGearSetup.java",
        )
        tools_setup = read(
            root,
            "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/setup/ExtraToolsSetup.java",
        )
        tools_items = read(
            root,
            "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/extratools/ExtraToolsItems.java",
        )

        require(
            gear,
            'NamespacedKey.fromString("extragear:items")',
            "ExtraGear must keep its original extragear:items guide page key",
        )
        require(
            gear,
            "createItemGroupIcon(), 1",
            "ExtraGear guide page must keep the original tier-1 placement",
        )
        require(
            gear,
            "ITEM_GROUP.register(plugin);",
            "ExtraGear guide page must be explicitly registered before its items",
        )
        require(
            gear,
            "ItemGroup weapons = ITEM_GROUP;",
            "ExtraGear swords must use the dedicated ExtraGear guide page",
        )
        require(
            gear,
            "ItemGroup armor = ITEM_GROUP;",
            "ExtraGear armor must use the dedicated ExtraGear guide page",
        )
        forbid(
            gear,
            "findCoreGroup(plugin",
            "ExtraGear must not route its items into core Weapons or Armor guide pages",
        )

        require(
            tools_items,
            'NamespacedKey.fromString("extratools:extra_tools")',
            "ExtraTools must keep its original extratools:extra_tools guide page key",
        )
        require(
            tools_items,
            "Material.DIAMOND_AXE",
            "ExtraTools guide page must keep its original diamond-axe icon",
        )
        require(
            tools_items,
            'ChatColor.DARK_RED + "Extra Tools"',
            "ExtraTools guide page must keep its original Extra Tools label",
        )
        require(
            tools_items,
            "createItemGroupIcon(), 1",
            "ExtraTools guide page must stay at tier 1 alongside ExtraGear",
        )
        require(
            tools_setup,
            "ExtraToolsItems.ITEM_GROUP.register(plugin);",
            "ExtraTools guide page must be explicitly registered before its items",
        )
        require(
            tools_setup,
            "Skipping built-in ExtraTools item because its id is already registered",
            "ExtraTools must skip individual id collisions instead of dropping the whole guide page",
        )
        forbid(
            tools_setup,
            "Built-in ExtraTools was not registered because these item ids already exist",
            "ExtraTools must not disable the whole module because one legacy item id collides",
        )

    except AssertionError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1

    print("Built-in addon guide pages verified: ExtraGear and ExtraTools remain dedicated tier-1 pages.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
