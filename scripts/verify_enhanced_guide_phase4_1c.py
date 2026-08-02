#!/usr/bin/env python3
"""Verify Phase 4.1B-B custom machine input-fill adapter invariants."""

from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".")
api_root = root / "src/main/java/io/github/thebusybiscuit/slimefun4/api/recipes/machine"
adapter = api_root / "MachineInputFillAdapter.java"
registry = api_root / "MachineInputFillAdapterRegistry.java"
resolved = api_root / "MachineInputFillRecipe.java"
implementation = root / "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/guide/enhanced/LegacyMachineInputFillAdapters.java"
manager = root / "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/guide/enhanced/LegacyMachineInputFillManager.java"
registry_test = root / "src/test/java/io/github/thebusybiscuit/slimefun4/api/recipes/machine/TestMachineInputFillAdapterRegistry.java"
recipe_test = root / "src/test/java/io/github/thebusybiscuit/slimefun4/api/recipes/machine/TestMachineInputFillRecipe.java"
supreme_test = root / "src/test/java/io/github/thebusybiscuit/slimefun4/implementation/guide/enhanced/TestSupremeGenericMachineInputFillAdapter.java"

required = (adapter, registry, resolved, implementation, manager, registry_test, recipe_test, supreme_test)
missing = [str(path.relative_to(root)) for path in required if not path.is_file()]
if missing:
    raise SystemExit("Missing Phase 4.1B-B files: " + ", ".join(missing))

texts = {path: path.read_text(encoding="utf-8") for path in required}
adapter_text = texts[adapter]
registry_text = texts[registry]
resolved_text = texts[resolved]
implementation_text = texts[implementation]
manager_text = texts[manager]
registry_test_text = texts[registry_test]
recipe_test_text = texts[recipe_test]
supreme_test_text = texts[supreme_test]

checks = {
    "Public adapter API": "@SlimefunAPI" in adapter_text and "interface MachineInputFillAdapter" in adapter_text,
    "Namespaced adapter identity": "NamespacedKey getKey()" in adapter_text,
    "Player-facing adapter name": "getDisplayName()" in adapter_text and "Supreme GenericMachine" in implementation_text,
    "Priority ordering": "getPriority()" in adapter_text and "comparingInt(MachineInputFillAdapter::getPriority)" in registry_text,
    "Runtime recipe resolution": "MachineInputFillRecipe resolve(" in adapter_text,
    "Exact target default": "guideMachine.getId().equals(placedMachine.getId())" in adapter_text,
    "Adapter safety hook": "isSafeToFill(" in adapter_text,
    "Registry replacement": "ADAPTERS.put(key, adapter)" in registry_text,
    "Registry removal": "ADAPTERS.remove" in registry_text,
    "Defensive ingredients": "ingredient.clone()" in resolved_text and "getIngredients()" in resolved_text,
    "Invalid ingredients rejected": "must be concrete non-empty items" in resolved_text,
    "Input and protected slots": "getInputSlots()" in resolved_text and "getProtectedSlots()" in resolved_text,
    "Maximum fill limit": "resolveMaximumSets" in resolved_text,
    "Manager uses registry": "findAdapter(plugin, guideMachine, recipe)" in manager_text,
    "Adapter target validation": "adapter.isValidTarget(guideMachine, placedItem)" in manager_text,
    "Adapter safety validation": "adapter.isSafeToFill(player, placedItem, target, menu)" in manager_text,
    "Adapter recipe resolution": "adapter.resolve(placedItem, recipe, selectedAlternatives.clone())" in manager_text,
    "Protected-slot validation": "validProtectedSlots" in manager_text and "slotsAreDisjoint(inputSlots, protectedSlots)" in manager_text,
    "Transaction rollback preserved": "restore(playerInventory, originalPlayer, menu, inputSlots, originalMachine)" in manager_text,
    "Standard adapter retained": "class StandardContainerAdapter" in implementation_text,
    "Supreme adapter registered first": "new SupremeGenericMachineAdapter(plugin)" in implementation_text,
    "Supreme public recipe field": 'getField("machineRecipes")' in implementation_text,
    "Supreme public recipe getters": "getInputNotNull" in implementation_text and "getOutputNotNull" in implementation_text,
    "Supreme status slot protected": "getStatusSlot" in implementation_text and "protectedSlots(machine, outputSlots)" in implementation_text,
    "No private reflective access": "setAccessible(" not in implementation_text and "getDeclaredField(" not in implementation_text,
    "Registry regression tests": "ordersAdaptersByPriorityAndReplacesMatchingKeys" in registry_test_text,
    "Recipe defensive-copy tests": "defensivelyCopiesIngredientsAndSlots" in recipe_test_text,
    "Supreme resolution tests": "resolvesSupremePublicRecipeFieldAndProtectsStatusSlot" in supreme_test_text,
    "Supreme output rejection test": "rejectsDisplayedRecipeThatDoesNotMatchSupremeOutput" in supreme_test_text,
}

failed = [name for name, passed in checks.items() if not passed]
if failed:
    for name in failed:
        print("FAIL:", name)
    raise SystemExit(1)

print("PASS: enhanced guide Phase 4.1B-B custom machine adapter invariants")
