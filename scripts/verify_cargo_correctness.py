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
    return_to_source = compact(method_body(task, "returnItemToSource"))

    require(route, "CargoUtils.withdraw(network, inventories, inputNode.getBlock(), inputTarget)", "source withdrawal")
    require(route, "catch (Exception | LinkageError ex)", "post-withdraw routing failure guard")
    require(route, "restoreAfterRoutingFailure(inputTarget, previousSlot, stack, ex)", "routing rollback")
    require_before(
        route,
        "CargoUtils.withdraw(network, inventories, inputNode.getBlock(), inputTarget)",
        "restoreAfterRoutingFailure(inputTarget, previousSlot, stack, ex)",
        "withdrawal before rollback guard",
    )

    require(recovery, "ItemStack rest = returnItemToSource(inputTarget, previousSlot, item)", "source restoration attempt")
    require(recovery, "ItemSpawnReason.CARGO_OVERFLOW", "lossless recovery overflow")
    require_absent(recovery, "isItemDeletionEnabled", "item-deletion opt-in during exceptional rollback")

    require(
        return_to_source,
        "CargoUtils.hasInventory(inputTarget) ? inventories.get(inputTarget.getLocation()) : null",
        "live source inventory revalidation",
    )
    require(return_to_source, "return item", "unrecoverable source remainder return")
    require_absent(task_compact, "attachedBlocks.computeIfAbsent", "stale attached-block task cache")

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

    print("Cargo correctness verification passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
