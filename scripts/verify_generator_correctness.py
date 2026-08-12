#!/usr/bin/env python3
"""Verify lossless generator fuel and byproduct transaction invariants."""

from __future__ import annotations

import re
import sys
from pathlib import Path


def read(root: Path, relative: str) -> str:
    path = root / relative
    if not path.is_file():
        raise SystemExit(f"Generator correctness failed: missing file {relative}")
    return path.read_text(encoding="utf-8")


def compact(text: str) -> str:
    return " ".join(text.split())


def require(text: str, needle: str, label: str) -> None:
    if needle not in text:
        raise SystemExit(f"Generator correctness failed: missing {label}: {needle}")


def require_before(text: str, first: str, second: str, label: str) -> None:
    first_at = text.find(first)
    second_at = text.find(second)
    if first_at < 0 or second_at < 0 or first_at >= second_at:
        raise SystemExit(
            f"Generator correctness failed: ordering violation for {label}: "
            f"expected {first!r} before {second!r}"
        )


def method_body(text: str, method_name: str) -> str:
    match = re.search(rf"\b{re.escape(method_name)}\s*\([^)]*\)\s*\{{", text)
    if not match:
        raise SystemExit(f"Generator correctness failed: missing method {method_name}")

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

    raise SystemExit(f"Generator correctness failed: unterminated method {method_name}")


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
    generator = read(
        root,
        "src/main/java/me/mrCookieSlime/Slimefun/Objects/SlimefunItem/abstractItems/AGenerator.java",
    )
    fuel = read(
        root,
        "src/main/java/me/mrCookieSlime/Slimefun/Objects/SlimefunItem/abstractItems/MachineFuel.java",
    )
    operation = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/operations/FuelOperation.java",
    )

    generated = compact(method_body(generator, "getGeneratedOutput"))
    result_helper = compact(method_body(generator, "getFuelResult"))
    recipe = compact(method_body(generator, "findRecipe"))
    fuel_compact = compact(fuel)
    operation_compact = compact(operation)

    # A storage/menu race must pause the generator instead of dereferencing a missing menu.
    require(generated, "if (inv == null) { return 0; }", "missing-menu guard")

    # MachineFuel already models a result and FuelOperation persists it. AGenerator must honor
    # that contract rather than only special-casing buckets at completion time.
    require(fuel_compact, "public ItemStack getOutput() { return output; }", "MachineFuel result API")
    require(operation_compact, "this(recipe.getInput(), recipe.getOutput(), recipe.getTicks())", "FuelOperation result capture")
    require(operation_compact, "public ItemStack getResult() { return result; }", "FuelOperation result accessor")
    require(result_helper, "ItemStack configuredResult = fuel.getOutput()", "configured fuel result")
    require(result_helper, "return isBucket(fuel.getInput()) ? new ItemStack(Material.BUCKET) : null", "bucket fallback result")
    require(generated, "ItemStack result = operation.getResult()", "completion result lookup")

    # Output capacity is a precondition to consuming fuel. Revalidate again at completion and
    # preserve an unexpected push remainder rather than silently deleting or duplicating it.
    require(generated, "ItemStack result = getFuelResult(fuel)", "fuel result preflight")
    require(generated, "if (result != null && !inv.fits(result, getOutputSlots())) { return 0; }", "start output-capacity preflight")
    require_before(
        generated,
        "if (result != null && !inv.fits(result, getOutputSlots())) { return 0; }",
        "inv.consumeItem(entry.getKey(), entry.getValue())",
        "output preflight before fuel consumption",
    )
    require(generated, "if (!inv.fits(result, getOutputSlots())) { return 0; }", "completion output-capacity revalidation")
    require_before(
        generated,
        "if (!inv.fits(result, getOutputSlots())) { return 0; }",
        "inv.pushItem(result.clone(), getOutputSlots())",
        "completion fit check before output commit",
    )
    require(generated, "l.getWorld().dropItemNaturally(l, remainder)", "unexpected remainder preservation")
    require_before(
        generated,
        "inv.pushItem(result.clone(), getOutputSlots())",
        "processor.endOperation(l)",
        "output commit before operation completion",
    )

    # Recipe similarity alone does not prove a full fuel quantity is present. Addon-defined
    # MachineFuel inputs may require more than one item, so reserve the exact configured amount.
    require(recipe, "int requiredAmount = fuel.getInput().getAmount()", "configured fuel quantity")
    require(recipe, "candidate != null && candidate.getAmount() >= requiredAmount && fuel.test(candidate)", "full-stack fuel eligibility")
    require(recipe, "found.put(slot, requiredAmount)", "exact fuel reservation")
    require(generated, "inv.consumeItem(entry.getKey(), entry.getValue())", "reserved fuel consumption")
    require(generated, "new FuelOperation( fuel.getInput(), result == null ? null : result.clone(), fuel.getTicks())", "preflight result persisted into operation")

    print("Generator correctness verification passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
