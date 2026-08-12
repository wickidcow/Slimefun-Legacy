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
    require(runtime, "static boolean consumeOneEach", "transactional enchantment input helper")
    require_before(
        runtime,
        "for (int slot : slots) {\n            ItemStack item = menu.getItemInSlot(slot);",
        "for (int slot : slots) {\n            menu.consumeItem(slot, 1);",
        "preflight-before-consume transaction",
    )
    require(runtime, "Inputs were left untouched.", "machine failure safety diagnostic")

    enchanter = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/electric/machines/enchanting/AutoEnchanter.java",
    )
    require(enchanter, "AutoEnchantEvent event", "AutoEnchantEvent dispatch")
    require(enchanter, "AsyncAutoEnchanterProcessEvent event", "async process event dispatch")
    require(enchanter, "if (event.isCancelled())", "enchanter cancellation handling")
    require_before(
        enchanter,
        ".fitAll(menu.toInventory(), recipe.getOutput(), InventoryContext.MACHINE_OUTPUT, getOutputSlots())",
        "EnchantmentMachineRuntime.consumeOneEach(menu, getInputSlots())",
        "enchanter output fit before input consumption",
    )
    require(enchanter, "EnchantmentMachineRuntime.one(target)", "single target-item recipe input")
    require(enchanter, "EnchantmentMachineRuntime.one(enchantedBook)", "single enchanted-book recipe input")

    disenchanter = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/electric/machines/enchanting/AutoDisenchanter.java",
    )
    require(disenchanter, "AutoDisenchantEvent event", "AutoDisenchantEvent dispatch")
    require(disenchanter, "if (event.isCancelled())", "disenchanter cancellation handling")
    require(disenchanter, "transferWasComplete", "disenchantment transfer verification")
    require_before(
        disenchanter,
        ".fitAll(menu.toInventory(), recipe.getOutput(), InventoryContext.MACHINE_OUTPUT, getOutputSlots())",
        "EnchantmentMachineRuntime.consumeOneEach(menu, getInputSlots())",
        "disenchanter output fit before input consumption",
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
    require_before(
        container,
        "if (found.containsKey(slot)) {",
        ".isSimilar(inventory.get(slot), input, MatchContext.RECIPE_INPUT, true, true)",
        "distinct input-slot reservation before recipe matching",
    )
    require_before(
        container,
        ".fitAll(\n                                inv.toInventory(),",
        "inv.consumeItem(entry.getKey(), entry.getValue());",
        "generic container output fit before input consumption",
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
        "while (experiencePoints - withdrawn >= EXPERIENCE_PER_FLASK",
        "EXP Collector new-total conversion loop",
    )
    require(exp_collector, "int storedExperience = Math.max(0, Integer.parseInt(value));", "negative EXP repair")
    require_before(
        exp_collector,
        "withdrawn += EXPERIENCE_PER_FLASK;",
        "StorageCacheUtils.setData(location, DATA_KEY, String.valueOf(experiencePoints - withdrawn));",
        "EXP withdrawal before persisted remainder",
    )

    farmer = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/androids/FarmerAndroid.java",
    )
    require(farmer, "menu.fits(drop, getOutputSlots())", "Farmer Android full-output preflight")
    require(farmer, "ItemStack remainder = menu.pushItem(drop, getOutputSlots());", "Farmer Android transactional output push")
    require_before(
        farmer,
        "menu.fits(drop, getOutputSlots())",
        "menu.pushItem(drop, getOutputSlots())",
        "Farmer Android fit-before-push transaction",
    )
    require_before(
        farmer,
        "if (remainder == null)",
        "ageable.setAge(0);",
        "Farmer Android harvest completion before crop reset",
    )

    woodcutter = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/androids/WoodcutterAndroid.java",
    )
    require(woodcutter, "ItemStack remainder = menu.pushItem(drop, getOutputSlots());", "Woodcutter Android overflow capture")
    require(woodcutter, "dropItemNaturally(log.getLocation(), remainder)", "Woodcutter Android overflow preservation")
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
