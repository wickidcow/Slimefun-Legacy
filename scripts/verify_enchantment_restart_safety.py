#!/usr/bin/env python3
"""Verify restart/interruption safety for the core Auto Enchanter and Auto Disenchanter."""

from __future__ import annotations

import sys
from pathlib import Path


def read(root: Path, relative: str) -> str:
    path = root / relative
    if not path.is_file():
        raise SystemExit(f"Enchantment restart-safety verification failed: missing {relative}")
    return path.read_text(encoding="utf-8")


def require(text: str, needle: str, label: str) -> None:
    if needle not in text:
        raise SystemExit(f"Enchantment restart-safety verification failed: missing {label}: {needle}")


def forbid(text: str, needle: str, label: str) -> None:
    if needle in text:
        raise SystemExit(f"Enchantment restart-safety verification failed: forbidden {label}: {needle}")


def require_before(text: str, first: str, second: str, label: str) -> None:
    first_at = text.find(first)
    second_at = text.find(second)
    if first_at < 0 or second_at < 0 or first_at >= second_at:
        raise SystemExit(
            f"Enchantment restart-safety verification failed: ordering violation for {label}: "
            f"expected {first!r} before {second!r}"
        )


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()

    container = read(
        root,
        "src/main/java/me/mrCookieSlime/Slimefun/Objects/SlimefunItem/abstractItems/AContainer.java",
    )
    require(container, "protected boolean canProgressOperation", "operation-progress input hook")
    require(container, "protected boolean commitOperationInputs", "operation-completion input hook")
    require_before(
        container,
        "if (!canProgressOperation(inv, currentOperation))",
        "if (takeCharge(b.getLocation()))",
        "input validation before energy consumption",
    )
    require_before(
        container,
        ".fitAll(inv.toInventory(), results, InventoryContext.MACHINE_OUTPUT, getOutputSlots())",
        "if (!commitOperationInputs(inv, currentOperation))",
        "output capacity preflight before deferred input consumption",
    )
    require_before(
        container,
        "if (!commitOperationInputs(inv, currentOperation))",
        "for (ItemStack output : results)",
        "deferred input consumption before output emission",
    )

    runtime = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/electric/machines/enchanting/EnchantmentMachineRuntime.java",
    )
    require(runtime, "static boolean inputsMatchSnapshots", "non-consuming exact input preflight")
    require(runtime, "matchInputSlots(menu, slots, expectedInputs)", "shared exact-slot matcher")
    require(runtime, "current.isSimilar(expected)", "metadata-sensitive input identity check")
    require(runtime, "menu.consumeItem(slot, 1);", "single-item completion consumption")

    abstract_machine = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/electric/machines/enchanting/AbstractEnchantmentMachine.java",
    )
    require(
        abstract_machine,
        "protected boolean canProgressOperation(BlockMenu menu, CraftingOperation operation)",
        "enchantment-machine progress guard",
    )
    require(
        abstract_machine,
        "EnchantmentMachineRuntime.inputsMatchSnapshots(menu, getInputSlots(), operation.getIngredients())",
        "live input snapshot validation while processing",
    )
    forbid(
        abstract_machine,
        "protected BlockBreakHandler onBlockBreak()",
        "duplicate-producing operation-snapshot block-break refund",
    )
    forbid(abstract_machine, "dropInterruptedInputs", "legacy interruption world-drop refund")

    for relative, expected in (
        (
            "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/electric/machines/enchanting/AutoEnchanter.java",
            "new ItemStack[] {targetSnapshot, bookSnapshot}",
        ),
        (
            "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/electric/machines/enchanting/AutoDisenchanter.java",
            "new ItemStack[] {item, book}",
        ),
    ):
        machine = read(root, relative)
        require(machine, "EnchantmentMachineRuntime.inputsMatchSnapshots(", "non-consuming start preflight")
        require(machine, expected, "exact start snapshots")
        require(
            machine,
            "protected boolean commitOperationInputs(BlockMenu menu, CraftingOperation operation)",
            "completion-time input commit",
        )
        require(
            machine,
            "EnchantmentMachineRuntime.consumeOneEachIfUnchanged(\n                menu, getInputSlots(), operation.getIngredients())",
            "exact completion-time consumption",
        )
        require_before(
            machine,
            "EnchantmentMachineRuntime.inputsMatchSnapshots(",
            "EnchantmentMachineRuntime.consumeOneEachIfUnchanged(",
            "start preflight before completion consumption",
        )

    storage = read(
        root,
        "src/main/java/com/xzavier0722/mc/plugin/slimefun4/storage/controller/BlockDataController.java",
    )
    require_before(
        storage,
        "saveAllBlockInventories();",
        "super.shutdown();",
        "machine inventories saved before controller shutdown",
    )
    require_before(
        storage,
        "executeAllDelayedTasks();",
        "super.shutdown();",
        "delayed inventory writes flushed before controller shutdown",
    )

    controller = read(
        root,
        "src/main/java/com/xzavier0722/mc/plugin/slimefun4/storage/controller/ADataController.java",
    )
    require(controller, "while (pendingTask > 0", "database shutdown write drain")
    require(controller, "lastShutdownClean = scheduledWriteTasks.isEmpty();", "clean write-drain verification")

    print("Enchantment restart/interruption safety verification passed.")
    print("- processing inputs remain in persisted machine slots until completion")
    print("- missing/changed inputs pause before energy consumption")
    print("- exact inputs commit only after output capacity is available")
    print("- block break relies on real inventory items instead of snapshot refunds")
    print("- normal shutdown saves inventories and drains delayed database writes")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
