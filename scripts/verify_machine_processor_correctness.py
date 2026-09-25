#!/usr/bin/env python3
"""Verify shared MachineProcessor lifecycle and progress-bar invariants."""

from __future__ import annotations

import re
import sys
from pathlib import Path


def read(root: Path, relative: str) -> str:
    path = root / relative
    if not path.is_file():
        raise SystemExit(f"Machine processor correctness failed: missing file {relative}")
    return path.read_text(encoding="utf-8")


def compact(text: str) -> str:
    return " ".join(text.split())


def require(text: str, needle: str, label: str) -> None:
    if needle not in text:
        raise SystemExit(f"Machine processor correctness failed: missing {label}: {needle}")


def require_absent(text: str, needle: str, label: str) -> None:
    if needle in text:
        raise SystemExit(f"Machine processor correctness failed: forbidden {label}: {needle}")


def method_body(text: str, method_name: str) -> str:
    match = re.search(rf"\b{re.escape(method_name)}\s*\([^)]*\)\s*\{{", text)
    if not match:
        raise SystemExit(f"Machine processor correctness failed: missing method {method_name}")

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

    raise SystemExit(f"Machine processor correctness failed: unterminated method {method_name}")


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
    source = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/core/machines/MachineProcessor.java",
    )
    operation_source = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/core/machines/MachineOperation.java",
    )
    crafting_operation_source = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/operations/CraftingOperation.java",
    )
    fuel_operation_source = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/operations/FuelOperation.java",
    )
    menu_utils = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/utils/ChestMenuUtils.java",
    )
    container_source = read(
        root,
        "src/main/java/me/mrCookieSlime/Slimefun/Objects/SlimefunItem/abstractItems/AContainer.java",
    )
    electric_smeltery_source = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/electric/machines/ElectricSmeltery.java",
    )

    source_compact = compact(source)
    progress = compact(method_body(source, "updateProgressBar"))
    operation = compact(operation_source)
    crafting_operation = compact(crafting_operation_source)
    fuel_operation = compact(fuel_operation_source)
    menu_progress = compact(method_body(menu_utils, "updateProgressbar"))
    progress_text = compact(method_body(menu_utils, "getProgressBar"))
    durability = compact(method_body(menu_utils, "getDurability"))
    container_recipe_scan = compact(method_body(container_source, "findNextRecipe"))
    electric_smeltery = compact(electric_smeltery_source)

    require(operation, "return getRemainingTicks() <= 0", "finished-operation contract")
    require(progress, "int remainingTicks = operation.getRemainingTicks()", "remaining-tick lookup")
    require(progress, "int totalTicks = operation.getTotalTicks()", "total-tick lookup")
    require(
        progress,
        "if (remainingTicks > 0 && totalTicks > 0)",
        "unfinished positive-duration progress guard",
    )
    require_absent(
        progress,
        "remainingTicks > 0 || totalTicks > 0",
        "finished-operation progress update",
    )
    require(progress, "ChestMenuUtils.updateProgressbar", "progress-bar update")

    # Running operations must own their ItemStack state. Recipes are public addon-facing
    # objects and may be reused or mutated after an operation starts; sharing those exact
    # ItemStack references would let an in-flight machine change underneath itself.
    require(crafting_operation, "this.ingredients = snapshot(ingredients)", "crafting ingredient snapshot")
    require(crafting_operation, "this.results = snapshot(results)", "crafting result snapshot")
    require(
        crafting_operation,
        "snapshot[i] = stacks[i] == null ? null : stacks[i].clone()",
        "deep crafting ItemStack snapshot",
    )
    require_absent(crafting_operation, "this.ingredients = ingredients", "shared crafting ingredient array")
    require_absent(crafting_operation, "this.results = results", "shared crafting result array")
    require(fuel_operation, "this.ingredient = ingredient.clone()", "fuel ingredient snapshot")
    require(
        fuel_operation,
        "this.result = result == null ? null : result.clone()",
        "fuel byproduct snapshot",
    )
    require_absent(fuel_operation, "this.ingredient = ingredient;", "shared fuel ingredient stack")
    require_absent(fuel_operation, "this.result = result;", "shared fuel result stack")

    # MachineProcessor exposes three endOperation overloads. Verify the concrete BlockPosition
    # implementation across the source instead of accidentally inspecting a delegating wrapper.
    require(source_compact, "T operation = machines.remove(pos)", "atomic operation removal before lifecycle callback")
    require(source_compact, "if (operation.isFinished())", "finished-operation event gate")
    require(
        source_compact,
        "new AsyncMachineOperationFinishEvent(pos, this, operation)",
        "finish event dispatch",
    )
    require(source_compact, "operation.onCancel(pos)", "premature-operation cancellation callback")

    # Progress helpers must remain safe when called outside MachineProcessor too. Clamp timing
    # inputs and use ratio math instead of integer division so long operations still animate.
    require(menu_progress, "inv.getViewers().isEmpty() || time <= 0", "invalid-duration/viewer guard")
    require(menu_progress, "int safeTimeLeft = Math.max(0, Math.min(timeLeft, time))", "remaining-time clamp")
    require(menu_progress, "getDurability(item, safeTimeLeft, time)", "bounded durability input")
    require(menu_progress, "getProgressBar(safeTimeLeft, time)", "bounded text progress input")
    require(progress_text, "int safeTotal = Math.max(1, total)", "positive text-progress total")
    require(progress_text, "int safeTime = Math.max(0, Math.min(time, safeTotal))", "text-progress remaining clamp")
    require(durability, "if (maxDurability <= 0 || max <= 0)", "non-damageable/invalid-duration durability guard")
    require(durability, "int safeTimeLeft = Math.max(0, Math.min(timeLeft, max))", "durability remaining clamp")
    require(
        durability,
        "Math.round((maxDurability * (double) safeTimeLeft) / max)",
        "precise durability ratio",
    )
    require_absent(durability, "getMaxDurability() / max", "integer-truncated durability ratio")

    # High-speed AContainer machines can run this path thousands of times per profile window.
    # Preserve the historical one-input-per-slot behavior while keeping recipe scans allocation-light.
    require(container_recipe_scan, "int[] inputSlots = getInputSlots()", "single input-slot snapshot")
    require(container_recipe_scan, "ItemStack[] inventory = new ItemStack[inputSlots.length]", "array inventory snapshot")
    require(container_recipe_scan, "boolean[] usedSlots = new boolean[inputSlots.length]", "slot-use tracking")
    require(container_recipe_scan, "int[] consumeAmounts = new int[inputSlots.length]", "consume amount tracking")
    require(container_recipe_scan, "candidate.getType() != input.getType()", "cheap material prefilter")
    require(container_recipe_scan, "if (usedSlots[i])", "one recipe input per physical slot")
    require_absent(container_recipe_scan, "new HashMap", "per-tick recipe-scan HashMap allocation")
    require_absent(container_recipe_scan, "getInputSlots())", "repeated virtual input-slot lookup inside scan loops")

    # Electric Smeltery is a common item-aware cargo target (including IE2 Void Smeltery).
    # Keep its six-slot preference semantics without LinkedList/Integer/Comparator churn.
    require(electric_smeltery, "int[] matchingSlots = new int[INPUT_SLOTS.length]", "primitive matching-slot buffer")
    require(electric_smeltery, "int[] emptySlots = new int[INPUT_SLOTS.length]", "primitive empty-slot buffer")
    require(electric_smeltery, "matchingAmounts[insertAt - 1] > stack.getAmount()", "ascending partial-stack routing")
    require(electric_smeltery, "Arrays.copyOf(matchingSlots, matchingCount)", "bounded matching-slot result")
    require_absent(electric_smeltery, "new LinkedList", "cargo-slot LinkedList allocation")
    require_absent(electric_smeltery, "Collections.sort", "boxed cargo-slot sorting")
    require_absent(electric_smeltery, "Comparator<Integer>", "boxed cargo-slot comparator")

    # Paper has marked these legacy Effect constants for removal. Keep production source on
    # Particle/Sound APIs so a future Paper update cannot turn today's warnings into failures.
    # Report every remaining occurrence at once so CI does not hide later violations behind
    # the first file it happens to encounter.
    production_source = root / "src/main/java"
    deprecated_effects: list[str] = []
    for java_file in sorted(production_source.rglob("*.java")):
        java_source = java_file.read_text(encoding="utf-8")
        relative = java_file.relative_to(root)
        for legacy_effect in ("Effect.STEP_SOUND", "Effect.SMOKE"):
            if legacy_effect in java_source:
                deprecated_effects.append(f"{relative}: {legacy_effect}")

    if deprecated_effects:
        details = "\n - ".join(deprecated_effects)
        raise SystemExit(f"Machine processor correctness failed: forbidden deprecated effects:\n - {details}")

    print("Machine processor correctness verification passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
