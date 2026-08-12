#!/usr/bin/env python3
"""Verify Reactor fuel, byproduct, access-port and coolant transaction invariants."""

from __future__ import annotations

import re
import sys
from pathlib import Path


def read(root: Path, relative: str) -> str:
    path = root / relative
    if not path.is_file():
        raise SystemExit(f"Reactor correctness failed: missing file {relative}")
    return path.read_text(encoding="utf-8")


def compact(text: str) -> str:
    return " ".join(text.split())


def require(text: str, needle: str, label: str) -> None:
    if needle not in text:
        raise SystemExit(f"Reactor correctness failed: missing {label}: {needle}")


def require_absent(text: str, needle: str, label: str) -> None:
    if needle in text:
        raise SystemExit(f"Reactor correctness failed: forbidden {label}: {needle}")


def require_before(text: str, first: str, second: str, label: str) -> None:
    first_at = text.find(first)
    second_at = text.find(second)
    if first_at < 0 or second_at < 0 or first_at >= second_at:
        raise SystemExit(
            f"Reactor correctness failed: ordering violation for {label}: "
            f"expected {first!r} before {second!r}"
        )


def method_body(text: str, method_name: str) -> str:
    match = re.search(rf"\b{re.escape(method_name)}\s*\([^)]*\)\s*\{{", text)
    if not match:
        raise SystemExit(f"Reactor correctness failed: missing method {method_name}")

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

    raise SystemExit(f"Reactor correctness failed: unterminated method {method_name}")


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
    source = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/electric/reactors/Reactor.java",
    )
    source_compact = compact(source)

    generated = compact(method_body(source, "getGeneratedOutput"))
    energy = compact(method_body(source, "generateEnergy"))
    byproduct = compact(method_body(source, "createByproduct"))
    transfer = compact(method_body(source, "transferOutputToAccessPort"))
    burn = compact(method_body(source, "burnNextFuel"))
    capacity = compact(method_body(source, "canStoreByproduct"))
    coolant = compact(method_body(source, "hasEnoughCoolant"))
    restock = compact(method_body(source, "restockFuel"))
    find_fuel = compact(method_body(source, "findFuel"))
    access_port = compact(method_body(source, "getAccessPort"))

    # Storage races must pause a reactor rather than tick a missing menu.
    require(generated, "if (inv == null) { return 0; }", "missing-menu guard")

    # Reactor charge checks must use the long-backed energy API. Persisted values outside the
    # legacy int range are valid data and must be clamped to capacity, not misread as zero.
    require(energy, "long capacity = Math.max(0L, getCapacityLong())", "long reactor capacity")
    require(
        energy,
        "Math.max(0L, Math.min(getChargeLong(l, data), capacity))",
        "long stored-charge clamp",
    )
    require(energy, "long space = capacity - charge", "long available-space calculation")
    require(energy, "return space >= produced ? produced : 0", "long-safe generation decision")
    require_absent(energy, "Integer.parseInt", "legacy int energy parsing")

    # Fuel brought in through the access port is part of the same tick's eligibility check.
    require_before(
        burn,
        "restockFuel(inv, accessPort)",
        "MachineFuel fuel = findFuel(inv, found)",
        "access-port restock before fuel lookup",
    )

    # Addon-defined MachineFuel inputs may require multiple items. Similarity alone is not
    # enough: reserve and consume the exact configured amount.
    require(find_fuel, "int requiredAmount = fuel.getInput().getAmount()", "configured fuel amount")
    require(
        find_fuel,
        "candidate != null && candidate.getAmount() >= requiredAmount && fuel.test(candidate)",
        "full-stack fuel eligibility",
    )
    require(find_fuel, "found.put(slot, requiredAmount)", "exact fuel reservation")

    # A reactor must know that its byproduct has somewhere to go before fuel is consumed.
    require(burn, "fuel == null || !canStoreByproduct(fuel.getOutput(), inv, accessPort)", "byproduct preflight")
    require_before(
        burn,
        "fuel == null || !canStoreByproduct(fuel.getOutput(), inv, accessPort)",
        "inv.consumeItem(entry.getKey(), entry.getValue())",
        "byproduct preflight before fuel consumption",
    )
    require_before(
        burn,
        "inv.consumeItem(entry.getKey(), entry.getValue())",
        "processor.startOperation(l, new FuelOperation(fuel))",
        "fuel commit before operation start",
    )
    require(capacity, "result == null", "null-byproduct allowance")
    require(capacity, "inv.fits(result, getOutputSlots())", "reactor byproduct capacity")
    require(
        capacity,
        "accessPort != null && accessPort.fits(result, ReactorAccessPort.getOutputSlots())",
        "access-port byproduct capacity",
    )

    # Completion must select a full-capacity target before mutating the finished operation.
    require(byproduct, "if (inv.fits(result, getOutputSlots()))", "reactor completion capacity check")
    require(
        byproduct,
        "accessPort != null && accessPort.fits(result, ReactorAccessPort.getOutputSlots())",
        "access-port completion capacity check",
    )
    require(byproduct, "ItemStack remainder = targetMenu.pushItem(result.clone(), targetSlots)", "byproduct commit")
    require(byproduct, "l.getWorld().dropItemNaturally(l, remainder)", "unexpected remainder preservation")
    require_before(
        byproduct,
        "ItemStack remainder = targetMenu.pushItem(result.clone(), targetSlots)",
        "inv.replaceExistingItem(22, new CustomItemStack(Material.BLACK_STAINED_GLASS_PANE, \" \"))",
        "byproduct commit before progress reset",
    )
    require_before(
        byproduct,
        "inv.replaceExistingItem(22, new CustomItemStack(Material.BLACK_STAINED_GLASS_PANE, \" \"))",
        "processor.endOperation(l)",
        "progress reset before operation completion",
    )

    # Cross-menu transfers should never share a mutable ItemStack reference between inventories.
    require(transfer, "accessPort.pushItem(stack.clone(), ReactorAccessPort.getOutputSlots())", "cloned output transfer")
    require(coolant, "menu.pushItem(accessPortItem.clone(), getCoolantSlots())", "cloned coolant transfer")
    require(restock, "menu.pushItem(portItem.clone(), getFuelSlots())", "cloned fuel transfer")

    # A concurrently removed/replaced access port must fail closed without a null sf-id dereference.
    require(
        access_port,
        "SlimefunItems.REACTOR_ACCESS_PORT.getItemId().equals(port.getSfId())",
        "null-safe live access-port identity check",
    )
    require_absent(source_compact, "port.getSfId().equals(", "null-unsafe access-port identity check")

    print("Reactor correctness verification passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
