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

    multiblock = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/core/multiblocks/MultiBlockMachine.java",
    )
    require(multiblock, "protected final void finishCraftedItemSafely", "safe delayed multiblock completion")
    require(
        multiblock,
        "if (block.getState(false) instanceof Container liveContainer)",
        "live-container delayed completion",
    )
    require(multiblock, "ItemSpawnReason.MULTIBLOCK_MACHINE_OVERFLOW", "delayed output overflow recovery")

    abstract = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/multiblocks/AbstractCraftingTable.java",
    )
    require(abstract, "consumeInputs(@Nonnull Inventory inv, @Nonnull ItemStack[] recipe)", "recipe-aware input helper")
    require(abstract, "recipeCell.getAmount()", "recipe-required amount consumption")
    require(abstract, "createVirtualInventory(", "recipe-aware output simulation")
    require(abstract, "@Nonnull ItemStack[] recipe", "recipe-aware output simulation parameter")
    require(abstract, "finishCraftedItemSafely(output, dispenser);", "shared delayed crafting completion")

    for name in ("EnhancedCraftingTable", "MagicWorkbench", "ArmorForge"):
        source = read(
            root,
            f"src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/multiblocks/{name}.java",
        )
        require(source, "consumeInputs(inv, recipe);", f"{name} recipe-aware consumption")
        require(source, "createVirtualInventory(inv, recipe)", f"{name} recipe-aware virtual inventory")
        require(source, "finishCraftedItem(output, dispenser);", f"{name} live delayed output completion")

    magic = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/multiblocks/MagicWorkbench.java",
    )
    require(magic, "b.getWorld().playEffect", "Magic Workbench machine-world animation")
    reject(magic, "p.getWorld().playEffect", "Magic Workbench player-world animation")

    for name in ("Compressor", "PressureChamber"):
        source = read(
            root,
            f"src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/multiblocks/{name}.java",
        )
        require(source, "ItemStack output = event.getOutput();", f"{name} final event output")
        require(source, "findOutputInventory(output,", f"{name} final output preflight")
        require(source, "finishCraftedItemSafely(output, dispenser);", f"{name} delayed output recovery")

    smeltery = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/multiblocks/AbstractSmeltery.java",
    )
    require(smeltery, "int[] remainingAmounts = new int[contents.length];", "smeltery slot reservation accounting")
    require(
        smeltery,
        "SlimefunUtils.isItemSimilar(stack, expectedInput, true, false)",
        "smeltery amount-independent ingredient matching",
    )
    require(smeltery, "int reserved = Math.min(remainingAmounts[slot], required);", "smeltery quantity reservation")
    require(smeltery, "ItemStack output = event.getOutput();", "smeltery final event output")
    require(smeltery, "findOutputInventory(output, possibleDispenser, inv)", "smeltery final output preflight")
    require(smeltery, "handleCraftedItem(output, dispenser, inv);", "smeltery overflow-safe insertion")
    reject(smeltery, "j == (inv.getContents().length - 1)", "smeltery same-stack repeated ingredient matching")

    for name in ("Juicer", "GrindStone", "OreCrusher"):
        source = read(
            root,
            f"src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/multiblocks/{name}.java",
        )
        require(source, "ItemStack output = event.getOutput();", f"{name} final event output")
        require(source, "removing.setAmount(convert.getAmount());", f"{name} recipe-declared consumption")
        require(source, "handleCraftedItem(output, possibleDispenser, inv);", f"{name} overflow-safe output")
        reject(source, "removing.setAmount(1);", f"{name} hard-coded one-item consumption")

    panning = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/multiblocks/AutomatedPanningMachine.java",
    )
    require(panning, "OutputChest.findOutputChestFor(b.getRelative(BlockFace.DOWN), finalOutput)", "panning final-output chest selection")
    require(panning, "ItemStack remainder = Slimefun.getItemStackService()", "panning insertion remainder handling")

    washer = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/multiblocks/OreWasher.java",
    )
    if washer.count("Bukkit.getPluginManager().callEvent(event);") < 3:
        raise SystemExit("Core-correctness verification failed: Ore Washer must dispatch all three craft events")
    require(washer, "findSafeOutputInventory", "Ore Washer final-output transaction preflight")
    require(washer, "canFitAll", "Ore Washer multi-output fit simulation")
    require(washer, "SlimefunItems.STONE_CHUNK", "Ore Washer secondary Stone Chunk output accounting")
    require(
        washer,
        "SlimefunUtils.isItemSimilar(input, new ItemStack(Material.SAND, 2), false)",
        "Ore Washer two-sand recipe match",
    )
    require(
        washer,
        "findSafeOutputInventory(output, dispBlock, inv, input, 2, output)",
        "Ore Washer two-sand output preflight",
    )
    require(
        washer,
        "completeCraft(p, b, inv, outputInv, input, 2, output);",
        "Ore Washer two-sand consumption",
    )
    require(washer, "removing.setAmount(amount);", "Ore Washer declared input amount consumption")

    altar_listener = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/listeners/AncientAltarListener.java",
    )
    require(
        altar_listener,
        "if (p.getGameMode() != GameMode.CREATIVE) {\n                    consumed.add(catalyst);",
        "Ancient Altar survival-only catalyst ledger",
    )

    altar_task = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/tasks/AncientAltarTask.java",
    )
    require(altar_task, "restoreConsumedItems();", "Ancient Altar abort/cancellation restoration")
    require(altar_task, "if (!running) {\n                return;\n            }", "Ancient Altar immediate abort stop")
    require(altar_task, "items.clear();", "Ancient Altar consumed-resource ledger cleanup")
    require(altar_task, "if (!item.isValid()", "Ancient Altar missing-item abort")

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

    for event_name in ("AutoEnchantEvent", "AutoDisenchantEvent", "AsyncAutoEnchanterProcessEvent"):
        event = read(root, f"src/main/java/io/github/thebusybiscuit/slimefun4/api/events/{event_name}.java")
        require(event, "EventThreading.isCurrentThreadAsynchronous", f"{event_name} thread context")

    android = read(root, "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/androids/ProgrammableAndroid.java")
    require(android, "PlayerSkin.fromBase64(texture)", "Android texture preservation")

    versions = read(root, "src/main/java/io/github/thebusybiscuit/slimefun4/core/commands/subcommands/VersionsCommand.java")
    require(versions, "URI.create", "addon URL validation")

    profile_controller = read(root, "src/main/java/com/xzavier0722/mc/plugin/slimefun4/storage/controller/ProfileDataController.java")
    require(profile_controller, "UUID.randomUUID()", "UUID backpack identity allocation")

    print("Focused core-correctness audit verification passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
