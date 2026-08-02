#!/usr/bin/env python3
"""Verify Phase 4.1B-A automatic addon AContainer input filling invariants."""

from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".")
manager = root / "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/guide/enhanced/LegacyMachineInputFillManager.java"
browser = root / "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/guide/enhanced/LegacyMachineRecipeBrowser.java"
test = root / "src/test/java/io/github/thebusybiscuit/slimefun4/implementation/guide/enhanced/TestAddonContainerInputFillSupport.java"

required = (manager, browser, test)
missing = [str(path.relative_to(root)) for path in required if not path.is_file()]
if missing:
    raise SystemExit("Missing Phase 4.1B-A files: " + ", ".join(missing))

manager_text = manager.read_text(encoding="utf-8")
browser_text = browser.read_text(encoding="utf-8")
test_text = test.read_text(encoding="utf-8")

checks = {
    "Addon ownership restriction removed": "machine.getAddon() == plugin" not in manager_text,
    "Any verified AContainer supported": "machine instanceof AContainer container" in manager_text,
    "Registered recipe gate": "hasCompatibleRegisteredRecipe" in manager_text,
    "Selected recipe revalidation": "resolveRegisteredRequirements" in manager_text,
    "Actual registered amounts used": "return registeredInputs(registered)" in manager_text,
    "Public-only recipe rejection": "could not be verified against the machine's registered recipes" in manager_text,
    "Input/output overlap guard": "slotsAreDisjoint(inputSlots, container.getOutputSlots())" in manager_text,
    "Exact placed machine validation": "guideMachine.getId().equals(placedItem.getId())" in manager_text,
    "Browser checks each recipe": "inputFill.supports(context.machine(), recipe)" in browser_text,
    "Addon-aware button": "Supports verified core and addon containers" in manager_text,
    "Order-independent matching": "matchChoiceGroup" in manager_text and "usedDisplayed" in manager_text,
    "Duplicate-safe output matching": "matchStack" in manager_text,
    "Addon recipe tests": "acceptsRegisteredAddonContainerRecipeInAnyDisplayOrder" in test_text,
    "Unregistered source tests": "rejectsPublicProviderRecipeThatIsNotRegisteredByContainer" in test_text,
    "Alternative selection tests": "selectedAlternativeMustMatchRegisteredContainerRecipe" in test_text,
    "Duplicate recipe tests": "duplicateIngredientsAndOutputsMustMatchExactly" in test_text,
}

failed = [name for name, passed in checks.items() if not passed]
if failed:
    for name in failed:
        print("FAIL:", name)
    raise SystemExit(1)

print("PASS: enhanced guide Phase 4.1B-A addon AContainer input filling invariants")
