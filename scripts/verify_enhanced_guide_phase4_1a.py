#!/usr/bin/env python3
"""Verify Phase 4.1A core AContainer machine input filling invariants."""

from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".")
manager = root / "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/guide/enhanced/LegacyMachineInputFillManager.java"
browser = root / "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/guide/enhanced/LegacyMachineRecipeBrowser.java"
providers = root / "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/guide/enhanced/LegacyMachineRecipeProviders.java"
settings = root / "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/guide/enhanced/LegacyGuideSettings.java"
bootstrap = root / "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/guide/enhanced/LegacyGuideBootstrap.java"
config = root / "src/main/resources/enhanced-guide.yml"
test = root / "src/test/java/io/github/thebusybiscuit/slimefun4/implementation/guide/enhanced/TestMachineInputFillPlan.java"

required = (manager, browser, providers, settings, bootstrap, config, test)
missing = [str(path.relative_to(root)) for path in required if not path.is_file()]
if missing:
    raise SystemExit("Missing Phase 4.1A files: " + ", ".join(missing))

manager_text = manager.read_text(encoding="utf-8")
browser_text = browser.read_text(encoding="utf-8")
providers_text = providers.read_text(encoding="utf-8")
settings_text = settings.read_text(encoding="utf-8")
bootstrap_text = bootstrap.read_text(encoding="utf-8")
config_text = config.read_text(encoding="utf-8")
test_text = test.read_text(encoding="utf-8")

checks = {
    "Core AContainer restriction": "machine instanceof AContainer" in manager_text and "machine.getAddon() == plugin" in manager_text,
    "Native provider restriction": "isContainerProvider(provider)" in manager_text and "CONTAINER_PROVIDER_KEY" in providers_text,
    "Exact placed machine validation": "guideMachine.getId().equals(placedItem.getId())" in manager_text,
    "Protection check": "Interaction.INTERACT_BLOCK" in manager_text,
    "Folia region ownership check": "isOwnedByCurrentRegion" in manager_text,
    "Machine ticker coordination": "setInventoryViewed" in manager_text and "isInventoryViewed" in manager_text,
    "Viewer collision protection": "menu.hasViewer()" in manager_text,
    "Transactional player snapshot": "originalPlayer" in manager_text and "setStorageContents" in manager_text,
    "Transactional machine snapshot": "originalMachine" in manager_text and "restore(" in manager_text,
    "Only input slots are written": "container.getInputSlots()" in manager_text and "writeSlots(menu, inputSlots" in manager_text,
    "No output generation": "pushItem" not in manager_text and "getOutputSlots" not in manager_text,
    "No direct operation start": "startOperation" not in manager_text,
    "Duplicate input slot assignment": "assignOccupied" in manager_text and "usedRequirements" in manager_text,
    "Partial stack support": "findTemplate" in manager_text and "removeMatching" in manager_text,
    "Maximum set planning": "planMaximum" in manager_text,
    "Virtual item admission": "canInsertIntoEmptySlot" in manager_text,
    "Recipe input matching": "MatchContext.RECIPE_INPUT" in manager_text,
    "Stack merge matching": "MatchContext.STACK_MERGE" in manager_text,
    "Guide fill button": "Fill Machine Inputs" in manager_text and "inputFill.createButton(recipe)" in browser_text,
    "Shift fill control": "action.isShiftClicked()" in browser_text,
    "Selected alternatives forwarded": "selectedAlternatives.clone()" in browser_text,
    "Settings wired": "hasMachineInputFill" in settings_text and "machine-input-fill" in config_text,
    "Bootstrap wired": "LegacyMachineInputFillManager.initialize(plugin)" in bootstrap_text,
    "Regression tests": "topsUpPartiallyFilledInputSlot" in test_text and "keepsDuplicateIngredientsInSeparateMachineSlots" in test_text and "fillsMaximumCompleteRecipeSets" in test_text,
}

failed = [name for name, passed in checks.items() if not passed]
if failed:
    for name in failed:
        print("FAIL:", name)
    raise SystemExit(1)

print("PASS: enhanced guide Phase 4.1A core machine input filling invariants")
