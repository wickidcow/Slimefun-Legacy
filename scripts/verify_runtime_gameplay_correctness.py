#!/usr/bin/env python3
"""Verify the first Slimefun Legacy runtime/gameplay-correctness invariants.

This phase deliberately targets player-visible machine behavior that a clean server boot
cannot prove: transactional input handling, cancellation safety, output-fit ordering,
processing lifecycle, and energy-gated progress.
"""

from __future__ import annotations

import sys
from pathlib import Path


def read(root: Path, relative: str) -> str:
    path = root / relative
    if not path.is_file():
        raise SystemExit(f"Runtime/gameplay correctness failed: missing file {relative}")
    return path.read_text(encoding="utf-8")


def require(text: str, needle: str, label: str) -> None:
    if needle not in text:
        raise SystemExit(f"Runtime/gameplay correctness failed: missing {label}: {needle}")


def require_before(text: str, first: str, second: str, label: str) -> None:
    first_at = text.find(first)
    second_at = text.find(second)
    if first_at < 0 or second_at < 0 or first_at >= second_at:
        raise SystemExit(
            f"Runtime/gameplay correctness failed: ordering violation for {label}: "
            f"expected {first!r} before {second!r}"
        )


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()

    runtime = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/electric/machines/enchanting/EnchantmentMachineRuntime.java",
    )
    require(runtime, "Math.max(1,", "non-zero enchantment processing duration")
    require(
        runtime,
        "static boolean consumeOneEachIfUnchanged",
        "snapshot-bound transactional enchantment input helper",
    )
    require(runtime, "current.isSimilar(expected)", "exact enchantment input snapshot revalidation")
    require_before(
        runtime,
        "ItemStack current = menu.getItemInSlot(slots[index]);",
        "for (int slot : slots) {\n            menu.consumeItem(slot, 1);",
        "snapshot preflight before enchantment input consumption",
    )
    require(runtime, "Inputs were left untouched.", "machine failure safety diagnostic")

    enchanter = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/electric/machines/enchanting/AutoEnchanter.java",
    )
    require(enchanter, "AutoEnchantEvent event", "AutoEnchantEvent dispatch")
    require(enchanter, "AsyncAutoEnchanterProcessEvent event", "async process event dispatch")
    require(enchanter, "if (event.isCancelled())", "enchanter cancellation handling")
    require(enchanter, "ItemStack targetSnapshot", "Auto Enchanter target snapshot")
    require(enchanter, "ItemStack bookSnapshot", "Auto Enchanter book snapshot")
    require_before(
        enchanter,
        ".fitAll(menu.toInventory(), recipe.getOutput(), InventoryContext.MACHINE_OUTPUT, getOutputSlots())",
        "EnchantmentMachineRuntime.consumeOneEachIfUnchanged(",
        "enchanter output fit before snapshot-bound input consumption",
    )
    require(
        enchanter,
        "new ItemStack[] {targetSnapshot, bookSnapshot}",
        "Auto Enchanter exact input snapshots supplied to transaction helper",
    )
    require(enchanter, "EnchantmentMachineRuntime.one(targetSnapshot)", "single target-item recipe input")
    require(enchanter, "EnchantmentMachineRuntime.one(bookSnapshot)", "single enchanted-book recipe input")

    disenchanter = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/electric/machines/enchanting/AutoDisenchanter.java",
    )
    require(disenchanter, "AutoDisenchantEvent event", "AutoDisenchantEvent dispatch")
    require(disenchanter, "if (event.isCancelled())", "disenchanter cancellation handling")
    require(disenchanter, "transferWasComplete", "disenchantment transfer verification")
    require(disenchanter, "ItemStack itemSnapshot", "Auto Disenchanter item snapshot")
    require(disenchanter, "ItemStack bookSnapshot", "Auto Disenchanter book snapshot")
    require_before(
        disenchanter,
        ".fitAll(menu.toInventory(), recipe.getOutput(), InventoryContext.MACHINE_OUTPUT, getOutputSlots())",
        "EnchantmentMachineRuntime.consumeOneEachIfUnchanged(",
        "disenchanter output fit before snapshot-bound input consumption",
    )
    require(
        disenchanter,
        "new ItemStack[] {item, book}",
        "Auto Disenchanter exact input snapshots supplied to transaction helper",
    )
    require(disenchanter, "EnchantmentMachineRuntime.one(book)", "single book recipe input")
    require(disenchanter, "EnchantmentMachineRuntime.one(item)", "single enchanted-item recipe input")

    book_binder = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/electric/machines/enchanting/BookBinder.java",
    )
    require(
        book_binder,
        "for (Map.Entry<Enchantment, Integer> entry : ech2.entrySet()) {\n            boolean hasConflicts = false;",
        "per-enchantment Book Binder conflict state",
    )
    require(
        book_binder,
        "hasConflicts = true;\n                    break;",
        "Book Binder conflict short-circuit",
    )
    require_before(
        book_binder,
        ".fitAll(\n                                    menu.toInventory(),",
        "menu.consumeItem(inputSlot);",
        "Book Binder output fit before input consumption",
    )

    container = read(
        root,
        "src/main/java/me/mrCookieSlime/Slimefun/Objects/SlimefunItem/abstractItems/AContainer.java",
    )
    require(container, "if (inv == null) {\n            return;\n        }", "generic container missing-menu guard")
    require(container, "CraftingOperation currentOperation = processor.getOperation(b);", "machine operation retrieval")
    require(
        container,
        "recipe.setTicks(Math.max(1, recipe.getTicks() / getSpeed()));",
        "non-zero generic container recipe duration",
    )
    require_before(
        container,
        "if (!currentOperation.isFinished()) {",
        "if (takeCharge(b.getLocation()))",
        "finished check before machine energy charge",
    )
    require(container, "if (takeCharge(b.getLocation()))", "energy-gated machine progress")
    require(container, "currentOperation.addProgress(1);", "machine operation progress")
    require(container, "ItemStack[] results = currentOperation.getResults();", "completed machine result snapshot")
    require(
        container,
        ".fitAll(inv.toInventory(), results, InventoryContext.MACHINE_OUTPUT, getOutputSlots())",
        "completed machine output backpressure",
    )
    require(container, "for (ItemStack output : results)", "machine output completion")
    require(container, "ItemStack remainder = inv.pushItem(output.clone(), getOutputSlots());", "machine output remainder capture")
    require(container, "Slimefun.runSyncAt(", "region-safe machine overflow preservation")
    require(container, "dropItemNaturally(overflowLocation, overflow)", "machine overflow drop preservation")
    require(container, "processor.endOperation(b);", "machine operation completion cleanup")
    require_before(
        container,
        ".fitAll(inv.toInventory(), results, InventoryContext.MACHINE_OUTPUT, getOutputSlots())",
        "for (ItemStack output : results)",
        "completion fit before machine output commit",
    )
    require_before(
        container,
        "for (ItemStack output : results)",
        "inv.replaceExistingItem(22, new CustomItemStack(Material.BLACK_STAINED_GLASS_PANE, \" \"));",
        "machine outputs before progress reset",
    )
    require_before(
        container,
        "inv.replaceExistingItem(22, new CustomItemStack(Material.BLACK_STAINED_GLASS_PANE, \" \"));",
        "processor.endOperation(b);\n            return;",
        "machine progress reset before completion cleanup",
    )
    # AContainer keeps the historical one-recipe-input-per-physical-slot rule, but the
    # allocation-light hot path now tracks reservations in fixed arrays rather than HashMaps.
    require_before(
        container,
        "if (usedSlots[i]) {",
        ".isSimilar(candidate, input, MatchContext.RECIPE_INPUT, true, true)",
        "distinct input-slot reservation before recipe matching",
    )
    require_before(
        container,
        "if (candidate == null || candidate.getType() != input.getType()) {",
        ".isSimilar(candidate, input, MatchContext.RECIPE_INPUT, true, true)",
        "cheap material prefilter before recipe similarity",
    )
    require_before(
        container,
        ".isSimilar(candidate, input, MatchContext.RECIPE_INPUT, true, true)",
        "usedSlots[i] = true;",
        "recipe match before reserving physical input slot",
    )
    require_before(
        container,
        ".fitAll(\n                                inv.toInventory(),",
        "inv.consumeItem(inputSlots[i], consumeAmounts[i]);",
        "generic container output fit before input consumption",
    )

    fluid_pump = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/electric/machines/FluidPump.java",
    )
    require(fluid_pump, "BlockData originalFluid = source.getBlockData().clone();", "FluidPump source snapshot")
    require(fluid_pump, "source.setType(Material.AIR, false);", "FluidPump source consumption before output")
    require(fluid_pump, "ItemStack remainder = menu.pushItem(output, getOutputSlots());", "FluidPump output remainder capture")
    require(fluid_pump, "source.setBlockData(originalFluid, false);", "FluidPump source rollback")
    require(fluid_pump, "menu.replaceExistingItem(inputSlot, originalInput);", "FluidPump input rollback")
    require(fluid_pump, "addCharge(machine.getLocation(), ENERGY_CONSUMPTION);", "FluidPump energy rollback")
    require_before(
        fluid_pump,
        "source.setType(Material.AIR, false);",
        "ItemStack remainder = menu.pushItem(output, getOutputSlots());",
        "FluidPump consumes fluid source before bucket output commit",
    )

    tree_accelerator = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/electric/machines/accelerators/TreeGrowthAccelerator.java",
    )
    require(tree_accelerator, "if (!sapling.applyBoneMeal(BlockFace.UP))", "tree bonemeal success check")
    require_before(
        tree_accelerator,
        "if (!sapling.applyBoneMeal(BlockFace.UP))",
        "removeCharge(machine.getLocation(), ENERGY_CONSUMPTION);",
        "tree growth success before energy consumption",
    )
    require_before(
        tree_accelerator,
        "if (!sapling.applyBoneMeal(BlockFace.UP))",
        "inv.consumeItem(slot);",
        "tree growth success before fertilizer consumption",
    )

    exp_collector = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/electric/machines/entities/ExpCollector.java",
    )
    require(exp_collector, "private static final int EXPERIENCE_PER_FLASK = 10;", "EXP flask conversion unit")
    require(
        exp_collector,
        "StorageCacheUtils.setData(location, DATA_KEY, String.valueOf(remainingExperience));",
        "EXP Collector accounts collected experience before output",
    )
    require(
        exp_collector,
        "int nextBalance = remainingExperience - EXPERIENCE_PER_FLASK;",
        "EXP Collector per-flask source debit",
    )
    require(
        exp_collector,
        "ItemStack[] outputSnapshot = snapshotSlots(menu, outputSlots);",
        "EXP Collector output rollback snapshot",
    )
    require(exp_collector, "int storedExperience = Math.max(0, Integer.parseInt(value));", "negative EXP repair")
    require_before(
        exp_collector,
        "StorageCacheUtils.setData(location, DATA_KEY, String.valueOf(nextBalance));",
        "menu.pushItem(SlimefunItems.FILLED_FLASK_OF_KNOWLEDGE.clone(), outputSlots);",
        "EXP balance debit before flask output",
    )
    require(
        exp_collector,
        "restoreSlots(menu, outputSlots, outputSnapshot);",
        "EXP Collector output rollback",
    )

    farmer = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/androids/FarmerAndroid.java",
    )
    require(farmer, "menu.fits(drop, getOutputSlots())", "Farmer Android full-output preflight")
    require(farmer, "BlockData originalCrop = data.clone();", "Farmer Android crop snapshot")
    require(farmer, "ItemStack[] originalOutputs = snapshotSlots(menu, outputSlots);", "Farmer Android output snapshot")
    require(farmer, "ItemStack remainder = menu.pushItem(drop, outputSlots);", "Farmer Android transactional output push")
    require(farmer, "restoreSlots(menu, outputSlots, originalOutputs);", "Farmer Android output rollback")
    require(farmer, "block.setBlockData(originalCrop);", "Farmer Android crop rollback")
    require_before(
        farmer,
        "menu.fits(drop, getOutputSlots())",
        "ageable.setAge(0);",
        "Farmer Android fit preflight before crop mutation",
    )
    require_before(
        farmer,
        "ageable.setAge(0);",
        "ItemStack remainder = menu.pushItem(drop, outputSlots);",
        "Farmer Android crop consumption before harvest output",
    )

    miner = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/androids/MinerAndroid.java",
    )
    require(miner, "InfiniteBlockGenerator generator = null;", "Miner Android explicit generator transaction branch")
    require(
        miner,
        "if (generator == null) {\n                block.setType(Material.AIR);",
        "Miner Android ordinary-source consumption",
    )
    require_before(
        miner,
        "block.setType(Material.AIR);",
        "for (ItemStack drop : drops)",
        "Miner Android ordinary block consumption before drop commit",
    )
    require(
        miner,
        "if (generator != null) {",
        "Miner Android renewable generator exemption",
    )

    woodcutter = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/androids/WoodcutterAndroid.java",
    )
    require(woodcutter, "ItemStack remainder = menu.pushItem(drop, getOutputSlots());", "Woodcutter Android overflow capture")
    require(woodcutter, "dropItemNaturally(log.getLocation(), remainder)", "Woodcutter Android overflow preservation")
    require(
        woodcutter,
        "if (saplingType == null || soilRequirement == null)",
        "Woodcutter Android unknown-log fail-safe",
    )
    require(
        woodcutter,
        "block.setType(Material.AIR);\n            return;",
        "Woodcutter Android consumes unmapped future logs",
    )
    require_before(
        woodcutter,
        "if (log.getY() == android.getRelative(face).getY())",
        "ItemStack remainder = menu.pushItem(drop, getOutputSlots());",
        "Woodcutter Android world mutation before harvested output",
    )
    require_before(
        woodcutter,
        "ItemStack remainder = menu.pushItem(drop, getOutputSlots());",
        "dropItemNaturally(log.getLocation(), remainder)",
        "Woodcutter Android push-before-overflow-drop",
    )

    reactor = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/electric/reactors/Reactor.java",
    )
    require(
        reactor,
        "for (MachineFuel fuel : fuelTypes) {\n            int requiredAmount = fuel.getInput().getAmount();\n\n            for (int slot : getFuelSlots()) {",
        "reactor full-quantity fuel lookup restricted to fuel slots",
    )
    require(reactor, "for (int slot : getCoolantSlots())", "reactor coolant slot separation")

    research = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/api/researches/Research.java",
    )
    require(
        research,
        "public void setCost(int cost) {\n        if (cost < 0)",
        "deprecated research cost validates incoming value",
    )
    require(
        research,
        "public void setLevelCost(int levelCost) {\n        if (levelCost < 0)",
        "research level cost validates incoming value",
    )

    backpack_listener = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/listeners/BackpackListener.java",
    )
    require(
        backpack_listener,
        "isBackpackItem(e.getMainHandItem()) || isBackpackItem(e.getOffHandItem())",
        "backpack hand-swap guard checks both participating hands",
    )
    require(
        backpack_listener,
        "isBackpackItem(e.getCurrentItem()) || isBackpackItem(e.getCursor())",
        "backpack player-inventory click guards current item and cursor",
    )
    require(
        backpack_listener,
        "if (e.getClick() == ClickType.NUMBER_KEY)",
        "backpack player-inventory number-key guard",
    )
    require(
        backpack_listener,
        "if (isBackpackItem(hotbarItem))",
        "backpack hotbar source validation",
    )
    require(
        backpack_listener,
        "e.getClick() == ClickType.SWAP_OFFHAND",
        "backpack player-inventory offhand-swap guard",
    )
    require(
        backpack_listener,
        "isBackpackItem(e.getWhoClicked().getInventory().getItemInOffHand())",
        "backpack offhand source validation",
    )

    cooler_listener = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/listeners/CoolerListener.java",
    )
    require(cooler_listener, "ItemStack currentCooler = findCurrentCooler(p, coolerItem);", "Cooler live-item revalidation")
    require(cooler_listener, "currentCooler == null || !cooler.canUse(p, false)", "Cooler post-load permission revalidation")
    require_before(
        cooler_listener,
        "ItemStack currentCooler = findCurrentCooler(p, coolerItem);",
        "PlayerBackpack.migrateLegacyItem(currentCooler, backpack);",
        "Cooler live identity before legacy migration",
    )
    require(
        cooler_listener,
        "SlimefunItem.getByItem(stack) instanceof Juice",
        "Cooler only consumes registered Juice items",
    )
    require(
        cooler_listener,
        "item.setAmount(item.getAmount() - 1);",
        "Cooler consumes one item from malformed stacked Juice",
    )

    regulator = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/electric/EnergyRegulator.java",
    )
    require(regulator, "public boolean isSynchronized()", "Energy Regulator ticker declaration")
    require(regulator, "return true;", "synchronized Energy Regulator ticker")

    print("Runtime/gameplay correctness phase 1 verification passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
