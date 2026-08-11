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

    container = read(
        root,
        "src/main/java/me/mrCookieSlime/Slimefun/Objects/SlimefunItem/abstractItems/AContainer.java",
    )
    require(container, "CraftingOperation currentOperation = processor.getOperation(b);", "machine operation retrieval")
    require(container, "if (takeCharge(b.getLocation()))", "energy-gated machine progress")
    require(container, "currentOperation.addProgress(1);", "machine operation progress")
    require(container, "for (ItemStack output : currentOperation.getResults())", "machine output completion")
    require(container, "processor.endOperation(b);", "machine operation completion cleanup")
    require_before(
        container,
        ".fitAll(\n                                inv.toInventory(),",
        "inv.consumeItem(entry.getKey(), entry.getValue());",
        "generic container output fit before input consumption",
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
