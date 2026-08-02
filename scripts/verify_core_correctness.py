#!/usr/bin/env python3
"""Verify the focused Slimefun Legacy core-correctness audit invariants."""

from __future__ import annotations

import sys
from pathlib import Path


def require(text: str, needle: str, label: str) -> None:
    if needle not in text:
        raise SystemExit(f"Core-correctness verification failed: missing {label}: {needle}")


def reject(text: str, needle: str, label: str) -> None:
    if needle in text:
        raise SystemExit(f"Core-correctness verification failed: found obsolete {label}: {needle}")


def read(root: Path, relative: str) -> str:
    path = root / relative
    if not path.is_file():
        raise SystemExit(f"Core-correctness verification failed: missing file {relative}")
    return path.read_text(encoding="utf-8")


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()

    abstract = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/multiblocks/AbstractCraftingTable.java",
    )
    require(abstract, "consumeInputs(@Nonnull Inventory inv, @Nonnull ItemStack[] recipe)", "recipe-aware input helper")
    require(abstract, "recipeCell.getAmount()", "recipe-required amount consumption")
    require(abstract, "createVirtualInventory(", "recipe-aware output simulation")
    require(abstract, "@Nonnull ItemStack[] recipe", "recipe-aware output simulation parameter")

    for name in ("EnhancedCraftingTable", "MagicWorkbench", "ArmorForge"):
        source = read(
            root,
            f"src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/multiblocks/{name}.java",
        )
        require(source, "consumeInputs(inv, recipe);", f"{name} recipe-aware consumption")
        require(source, "createVirtualInventory(inv, recipe)", f"{name} recipe-aware virtual inventory")

    listener = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/listeners/MultiBlockListener.java",
    )
    require(listener, "multiblocks.descendingIterator()", "all-match multiblock dispatch")
    reject(listener, "MultiBlock mb = multiblocks.getLast();", "single-last multiblock dispatch")

    regulator = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/electric/EnergyRegulator.java",
    )
    require(regulator, "public boolean isSynchronized()", "Energy Regulator ticker declaration")
    require(regulator, "return true;", "synchronized Energy Regulator ticker")

    multi_tool = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/electric/gadgets/MultiTool.java",
    )
    require(multi_tool, "PersistentDataType.STRING", "string Multi Tool mode storage")
    require(multi_tool, "PersistentDataType.INTEGER", "legacy integer Multi Tool migration")
    require(multi_tool, "pdc.remove(multiToolMode);", "PDC type cleanup before migration")
    reject(multi_tool, "pdc.set(multiToolMode, PersistentDataType.INTEGER", "new integer Multi Tool writes")

    backpack = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/listeners/BackpackListener.java",
    )
    require(backpack, "Could not resolve backpack identity", "missing backpack diagnostic")
    require(backpack, "Refused to open invalid backpack", "invalid backpack diagnostic")

    # Important fixes reviewed during this audit and already present in Legacy.
    for event_name in ("AutoEnchantEvent", "AutoDisenchantEvent", "AsyncAutoEnchanterProcessEvent"):
        event = read(
            root,
            f"src/main/java/io/github/thebusybiscuit/slimefun4/api/events/{event_name}.java",
        )
        require(event, "EventThreading.isCurrentThreadAsynchronous", f"{event_name} thread context")

    android = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/androids/ProgrammableAndroid.java",
    )
    require(android, "PlayerSkin.fromBase64(texture)", "Android texture preservation")

    versions = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/core/commands/subcommands/VersionsCommand.java",
    )
    require(versions, "URI.create", "addon URL validation")

    profile_controller = read(
        root,
        "src/main/java/com/xzavier0722/mc/plugin/slimefun4/storage/controller/ProfileDataController.java",
    )
    require(profile_controller, "UUID.randomUUID()", "UUID backpack identity allocation")

    print("Focused core-correctness audit verification passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
