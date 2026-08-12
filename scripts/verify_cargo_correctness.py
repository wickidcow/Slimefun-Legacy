#!/usr/bin/env python3
"""Verify Cargo transfer conservation and chunk-access invariants."""

from __future__ import annotations

import re
import sys
from pathlib import Path


def read(root: Path, relative: str) -> str:
    path = root / relative
    if not path.is_file():
        raise SystemExit(f"Cargo correctness failed: missing file {relative}")
    return path.read_text(encoding="utf-8")


def compact(text: str) -> str:
    return " ".join(text.split())


def require(text: str, needle: str, label: str) -> None:
    if needle not in text:
        raise SystemExit(f"Cargo correctness failed: missing {label}: {needle}")


def require_absent(text: str, needle: str, label: str) -> None:
    if needle in text:
        raise SystemExit(f"Cargo correctness failed: forbidden {label}: {needle}")


def require_before(text: str, first: str, second: str, label: str) -> None:
    first_at = text.find(first)
    second_at = text.find(second)
    if first_at < 0 or second_at < 0 or first_at >= second_at:
        raise SystemExit(
            f"Cargo correctness failed: ordering violation for {label}: "
            f"expected {first!r} before {second!r}"
        )


def method_body(text: str, method_name: str) -> str:
    match = re.search(rf"\b{re.escape(method_name)}\s*\([^)]*\)\s*\{{", text)
    if not match:
        raise SystemExit(f"Cargo correctness failed: missing method {method_name}")

    start = match.end() - 1
    depth = 0
    for index in range(start, len(text)):
        char = text[index]
        if char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                return text[start + 1 : index]

    raise SystemExit(f"Cargo correctness failed: unterminated method {method_name}")


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()

    task = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/core/networks/cargo/CargoNetworkTask.java",
    )
    task_compact = compact(task)
    route = compact(method_body(task, "routeItems"))
    recovery = compact(method_body(task, "restoreAfterRoutingFailure"))
    restore_original = compact(method_body(task, "restoreOriginalSlot"))
    return_to_source = compact(method_body(task, "returnItemToSource"))
    live_source = compact(method_body(task, "getLiveSourceInventory"))

    require(route, "CargoUtils.withdraw(network, inventories, inputNode.getBlock(), inputTarget)", "source withdrawal")
    require(route, "catch (Exception | LinkageError ex)", "post-withdraw routing failure guard")
    require(route, "restoreAfterRoutingFailure(inputTarget, previousSlot, stack, ex)", "routing rollback")
    require_before(
        route,
        "CargoUtils.withdraw(network, inventories, inputNode.getBlock(), inputTarget)",
        "restoreAfterRoutingFailure(inputTarget, previousSlot, stack, ex)",
        "withdrawal before rollback guard",
    )

    require(recovery, "restoreOriginalSlot(inputTarget, previousSlot, item)", "exact source-slot restoration")
    require(recovery, "ItemSpawnReason.CARGO_OVERFLOW", "lossless recovery overflow")
    require(recovery, "catch (Exception | LinkageError recoveryFailure)", "rollback restoration failure guard")
    require(recovery, "catch (Exception | LinkageError overflowFailure)", "rollback overflow failure guard")
    require_absent(recovery, "isItemDeletionEnabled", "item-deletion opt-in during exceptional rollback")
    require_absent(recovery, "returnItemToSource", "normal multi-slot insertion during exceptional rollback")

    require(restore_original, "DirtyChestMenu menu = CargoUtils.getChestMenu(inputTarget)", "custom-menu rollback priority")
    require(restore_original, "menu.getItemInSlot(previousSlot) == null", "custom-menu original-slot check")
    require(restore_original, "menu.replaceExistingItem(previousSlot, item)", "custom-menu exact-slot restore")
    require(restore_original, "Inventory inv = getLiveSourceInventory(inputTarget)", "live vanilla source restore")
    require(restore_original, "inv.getItem(previousSlot) == null", "vanilla original-slot check")
    require(restore_original, "inv.setItem(previousSlot, item)", "vanilla exact-slot restore")
    require_before(
        restore_original,
        "DirtyChestMenu menu = CargoUtils.getChestMenu(inputTarget)",
        "Inventory inv = getLiveSourceInventory(inputTarget)",
        "Slimefun menu before underlying vanilla inventory",
    )

    require(return_to_source, "DirtyChestMenu menu = CargoUtils.getChestMenu(inputTarget)", "custom-menu remainder handling")
    require(return_to_source, "Inventory inv = getLiveSourceInventory(inputTarget)", "live source inventory revalidation")
    require_before(
        return_to_source,
        "DirtyChestMenu menu = CargoUtils.getChestMenu(inputTarget)",
        "Inventory inv = getLiveSourceInventory(inputTarget)",
        "custom menu before vanilla remainder path",
    )
    require(return_to_source, "return item", "unrecoverable source remainder return")

    require(live_source, ".isChunkLoaded(", "source chunk guard before inventory refresh")
    require(live_source, "inputTarget.getState(false)", "live block-state refresh")
    require(live_source, "state instanceof InventoryHolder holder", "live inventory-holder validation")
    require(live_source, "inventories.put(location, inventory)", "validated inventory cache refresh")
    require_absent(task_compact, "attachedBlocks.computeIfAbsent", "stale attached-block task cache")
    require_absent(task_compact, "inventories.get(inputTarget.getLocation())", "stale source inventory lookup")

    network = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/core/networks/cargo/AbstractItemNetwork.java",
    )
    attached = compact(method_body(network, "getAttachedBlock"))
    require(attached, "!isLocationAccessible(l) || !isChunkLoaded(l)", "source node chunk guard")
    require(attached, "!isLocationAccessible(targetLocation) || !isChunkLoaded(targetLocation)", "attached target chunk guard")
    require_before(attached, "!isChunkLoaded(targetLocation)", "targetLocation.getBlock()", "target chunk check before block access")

    utils = compact(
        read(
            root,
            "src/main/java/io/github/thebusybiscuit/slimefun4/core/networks/cargo/CargoUtils.java",
        )
    )
    require(utils, "stack.setAmount(stack.getAmount() - maxStackSize)", "empty-slot partial-stack remainder")
    require(utils, "stack.setAmount(amount - maxStackSize)", "merge partial-stack remainder")
    require(utils, "getSlotsAccessedByItemTransport(menu, ItemTransportFlow.WITHDRAW, null)", "withdraw slot contract")
    require(utils, "getSlotsAccessedByItemTransport(menu, ItemTransportFlow.INSERT, wrapper)", "insert slot contract")
    require(utils, "new CargoWithdrawEvent(node, target", "withdraw event")
    require(utils, "new CargoInsertEvent(node, target", "insert event")
    require(utils, "if (event.isCancelled()) { return null; }", "cancelled custom-menu withdrawal guard")
    require(utils, "if (event.isCancelled()) { return stack; }", "cancelled custom-menu insertion guard")
    require_before(
        utils,
        "Bukkit.getPluginManager().callEvent(event); if (event.isCancelled()) { return stack; }",
        "getSlotsAccessedByItemTransport(menu, ItemTransportFlow.INSERT, wrapper)",
        "insert cancellation before custom-menu mutation",
    )

    print("Cargo correctness verification passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
