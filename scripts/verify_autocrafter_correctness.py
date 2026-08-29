#!/usr/bin/env python3
"""Verify auto-crafter reservation, output, persistence, and energy transaction invariants."""

from __future__ import annotations

import sys
from pathlib import Path


def read(root: Path, relative: str) -> str:
    path = root / relative
    if not path.is_file():
        raise SystemExit(f"Auto-crafter correctness failed: missing file {relative}")
    return path.read_text(encoding="utf-8")


def require(text: str, needle: str, label: str) -> None:
    if needle not in text:
        raise SystemExit(f"Auto-crafter correctness failed: missing {label}: {needle}")


def forbid(text: str, needle: str, label: str) -> None:
    if needle in text:
        raise SystemExit(f"Auto-crafter correctness failed: forbidden {label}: {needle}")


def require_before(text: str, first: str, second: str, label: str) -> None:
    first_at = text.find(first)
    second_at = text.find(second)
    if first_at < 0 or second_at < 0 or first_at >= second_at:
        raise SystemExit(
            f"Auto-crafter correctness failed: ordering violation for {label}: expected {first!r} before {second!r}"
        )


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()

    crafter = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/autocrafters/AbstractAutoCrafter.java",
    )
    require(crafter, "public boolean isSynchronized()", "synchronized auto-crafter ticker declaration")
    require(crafter, "return true;", "synchronized auto-crafter ticker")
    require(
        crafter,
        "if (recipe == null || !recipe.isEnabled() || getCharge(b.getLocation(), data) < getEnergyConsumption())",
        "recipe, enabled-state and energy preflight",
    )
    require(crafter, "if (craft(interactor, recipe))", "craft success gate before energy charge")
    require_before(
        crafter,
        "if (craft(interactor, recipe))",
        "removeCharge(b.getLocation(), getEnergyConsumption());",
        "auto-crafter craft-before-energy ordering",
    )
    require(
        crafter,
        "int amount = itemQuantities.getOrDefault(slot, item.getAmount());",
        "per-slot remaining quantity tracking",
    )
    require(
        crafter,
        "if (amount > 0 && matches(item, predicate))",
        "positive remaining quantity reservation guard",
    )
    require(crafter, "itemQuantities.put(slot, amount - 1);", "one-unit ingredient reservation")
    require_before(
        crafter,
        "if (inv.canOutput(recipe.getResult()))",
        "if (!inv.matchRecipe(this, recipe.getIngredients(), itemQuantities))",
        "result preflight before recipe reservation",
    )
    require(crafter, "boolean success = inv.addItem(recipe.getResult().clone());", "result commit status")
    require(
        crafter,
        "if (success) {\n                // Fixes #2926 - Push leftover items to the inventory.",
        "leftovers only after result commit",
    )
    require(
        crafter,
        "return shapelessRecipe.getChoiceList().size();",
        "generic shapeless RecipeChoice ingredient counting",
    )
    require(
        crafter,
        "RecipeChoice choice = shapedRecipe.getChoiceMap().get(each);",
        "generic shaped RecipeChoice lookup",
    )
    require(
        crafter,
        "ItemStack itemInChoice = choice.getItemStack();",
        "Paper RecipeChoice representative-stack ingredient counting",
    )
    forbid(
        crafter,
        "RecipeChoice.MaterialChoice materialChoice = (RecipeChoice.MaterialChoice)",
        "MaterialChoice-only shaped recipe cast",
    )

    chest = read(
        root,
        "src/main/java/com/xzavier0722/mc/plugin/slimefun4/autocrafter/ChestInventoryParser.java",
    )
    require(chest, "ItemStack[] contents = inv.getContents();", "single vanilla inventory snapshot")
    require(
        chest,
        "byte[] matchModes = AutoCrafterInventoryMatcher.createMatchModeCache(crafter, contents.length);",
        "lazy per-slot match-mode cache creation",
    )
    require(
        chest,
        "AutoCrafterInventoryMatcher.matchesAny(crafter, contents, itemQuantities, predicate, matchModes)",
        "snapshot and match-mode reuse for every ingredient predicate",
    )
    require_before(
        chest,
        "ItemStack[] contents = inv.getContents();",
        "for (Predicate<ItemStack> predicate : recipe)",
        "snapshot before recipe predicate loop",
    )
    require_before(
        chest,
        "byte[] matchModes = AutoCrafterInventoryMatcher.createMatchModeCache(crafter, contents.length);",
        "for (Predicate<ItemStack> predicate : recipe)",
        "match-mode cache before recipe predicate loop",
    )
    require(
        chest,
        "ItemStack remainder = Slimefun.getItemStackService().addItem(inv, item, InventoryContext.MACHINE_OUTPUT);",
        "chest exact insertion remainder capture",
    )
    require(chest, "if (remainder == null || remainder.getAmount() <= 0)", "chest complete insertion success")
    require(chest, "location.getWorld().dropItemNaturally(location, remainder);", "chest rejected remainder preservation")
    require_before(
        chest,
        "ItemStack remainder = Slimefun.getItemStackService().addItem",
        "dropItemNaturally(location, remainder)",
        "chest insertion-before-overflow preservation",
    )

    matcher = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/autocrafters/AutoCrafterInventoryMatcher.java",
    )
    require(
        matcher,
        "crafter.getClass() == EnhancedAutoCrafter.class",
        "fast path restricted to exact core Enhanced Auto Crafter",
    )
    require(
        matcher,
        "Slimefun.getItemStackService().isVirtualItem(item)",
        "lazy virtual-item resolution",
    )
    require(
        matcher,
        "matches = mode == MATCH_MODE_DIRECT ? predicate.test(item) : crafter.matches(item, predicate);",
        "direct normal-stack predicate with virtual fallback",
    )
    require(
        matcher,
        "int amount = itemQuantities.getOrDefault(slot, item.getAmount());",
        "snapshot matcher per-slot remaining quantity tracking",
    )
    require(matcher, "itemQuantities.put(slot, amount - 1);", "snapshot matcher one-unit reservation")
    require(
        matcher,
        "return matchesAny(crafter, contents, itemQuantities, predicate, null);",
        "legacy matcher path preserves crafter matching semantics",
    )

    smart_port = read(
        root,
        "src/main/java/com/xzavier0722/mc/plugin/slimefun4/autocrafter/CrafterSmartPortParser.java",
    )
    require(smart_port, "private final Inventory inventory;", "cached Smart Port inventory view")
    require(smart_port, "this.inventory = inv.toInventory();", "single Smart Port inventory-view capture")
    require(
        smart_port,
        ".fits(inventory, item, InventoryContext.MACHINE_OUTPUT, CrafterSmartPort.OUTPUT_SLOTS)",
        "Smart Port output fit uses cached inventory view",
    )
    require(smart_port, "ItemStack[] contents = inventory.getContents();", "single Smart Port contents snapshot")
    require(
        smart_port,
        "AutoCrafterInventoryMatcher.matchesAny(crafter, contents, itemQuantities, predicate)",
        "Smart Port snapshot reuse for every ingredient predicate",
    )
    require_before(
        smart_port,
        "ItemStack[] contents = inventory.getContents();",
        "for (Predicate<ItemStack> predicate : recipe)",
        "Smart Port snapshot before recipe predicate loop",
    )
    forbid(
        smart_port,
        "crafter.matchesAny(inv.toInventory(), itemQuantities, predicate)",
        "per-ingredient Smart Port inventory conversion",
    )
    require(
        smart_port,
        "ItemStack remainder = inv.pushItem(item, CrafterSmartPort.OUTPUT_SLOTS);",
        "smart-port exact insertion remainder capture",
    )
    require(
        smart_port,
        "inv.getBlock().getWorld().dropItemNaturally(inv.getLocation(), remainder);",
        "smart-port rejected remainder preservation",
    )

    manager = read(
        root,
        "src/main/java/com/xzavier0722/mc/plugin/slimefun4/autocrafter/CrafterInteractorManager.java",
    )
    require(manager, "var blockData = StorageCacheUtils.getBlock(b.getLocation());", "direct interactor block-data lookup")
    require(manager, "CrafterInteractorHandler handler = handlers.get(blockData.getSfId());", "direct interactor handler lookup")
    require(
        manager,
        "return handler == null ? null : handler.getInteractor(blockData.getBlockMenu());",
        "interactor null-safe construction",
    )
    forbid(manager, "if (hasInterator(b))", "duplicate has-interactor lookup inside getInteractor")

    contract = read(
        root,
        "src/main/java/com/xzavier0722/mc/plugin/slimefun4/autocrafter/CrafterInteractable.java",
    )
    require(contract, "Implementations must therefore be lossless", "addon interactor lossless commit contract")
    require(
        contract,
        "Implementations must not partially insert an item and\n     * then return {@code false}.",
        "addon interactor no-partial-failure contract",
    )

    vanilla = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/autocrafters/VanillaAutoCrafter.java",
    )
    require(vanilla, "String[] values = CommonPatterns.COLON.split(value, 2);", "bounded vanilla recipe-key split")
    require(
        vanilla,
        "if (values.length != 2 || values[0].isBlank() || values[1].isBlank())",
        "malformed vanilla recipe-key guard",
    )
    require(vanilla, "catch (IllegalArgumentException ignored)", "invalid vanilla namespace/key recovery")
    require(vanilla, "if (recipe != null)", "unsupported vanilla recipe wrapper guard")
    forbid(vanilla, "String[] values = CommonPatterns.COLON.split(value);", "unbounded vanilla recipe-key split")

    vanilla_recipe = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/autocrafters/VanillaRecipe.java",
    )
    require(
        vanilla_recipe,
        "items[4] = choice.getItemStack();",
        "single generic RecipeChoice preview representative",
    )
    require(
        vanilla_recipe,
        "items[i] = choice.getItemStack();",
        "multi-slot generic RecipeChoice preview representative",
    )
    require(
        vanilla_recipe,
        "choice instanceof MaterialChoice materialChoice && materialChoice.getChoices().size() > 1",
        "MaterialChoice cycling remains optional preview enhancement",
    )
    forbid(
        vanilla_recipe,
        "choices.length == 1 && choices[0] instanceof MaterialChoice",
        "MaterialChoice-only single recipe preview",
    )

    slimefun = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/autocrafters/SlimefunAutoCrafter.java",
    )
    require(slimefun, "if (value == null || value.isBlank())", "missing Slimefun recipe-id guard")
    require(slimefun, "if (recipe != null)", "stale Slimefun recipe-type guard")
    forbid(
        slimefun,
        "AbstractRecipe recipe = AbstractRecipe.of(item, targetRecipeType);\n                recipe.setEnabled(enabled);",
        "unchecked Slimefun recipe wrapper",
    )

    print("Auto-crafter correctness verification passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
