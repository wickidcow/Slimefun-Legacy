#!/usr/bin/env python3
"""Verify Slimefun Legacy 4.1.15 FastMachines input-fill invariants."""

from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".")
implementation = root / "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/guide/enhanced/LegacyMachineInputFillAdapters.java"
test = root / "src/test/java/io/github/thebusybiscuit/slimefun4/implementation/guide/enhanced/TestFastMachinesInputFillAdapter.java"
changelog = root / "CHANGELOG.md"
guide = root / "ENHANCED_GUIDE.md"
properties = root / "gradle.properties"

required = (implementation, test, changelog, guide, properties)
missing = [str(path.relative_to(root)) for path in required if not path.is_file()]
if missing:
    raise SystemExit("Missing FastMachines input-fill files: " + ", ".join(missing))

implementation_text = implementation.read_text(encoding="utf-8")
test_text = test.read_text(encoding="utf-8")
changelog_text = changelog.read_text(encoding="utf-8")
guide_text = guide.read_text(encoding="utf-8")
properties_text = properties.read_text(encoding="utf-8")

checks = {
    "FastMachines adapter registered": "new FastMachinesInputFillAdapter(plugin)" in implementation_text,
    "FastMachines adapter priority": "return 1100;" in implementation_text,
    "Package-gated support": 'PACKAGE_PREFIX = "net.guizhanss.fastmachines."' in implementation_text,
    "Public machine recipe getter": 'findMethod(type, "getRecipes")' in implementation_text,
    "Public input-slot getter": 'findMethod(type, "getInputSlots")' in implementation_text,
    "Public recipe input getter": 'findMethod(type, "getInputs")' in implementation_text,
    "Public recipe output getter": 'findMethod(type, "getOutputs")' in implementation_text,
    "Public choice getter": 'findMethod(type, "getChoices")' in implementation_text,
    "Public wrapper getter": 'findMethod(type, "getBaseItem")' in implementation_text,
    "Verified 54-slot inventory": "INVENTORY_SIZE = 54" in implementation_text,
    "Verified ingredient range": "FIRST_INPUT_SLOT = 0" in implementation_text
    and "LAST_INPUT_SLOT = 35" in implementation_text,
    "Protected GUI range": "FIRST_PROTECTED_SLOT = 36" in implementation_text
    and "LAST_PROTECTED_SLOT = 53" in implementation_text,
    "Fail-closed safety hook": "isExpectedInputLayout(readInputSlots(machine))" in implementation_text,
    "Alternative revalidation": "resolveRequirements(" in implementation_text
    and "anyChoiceMatches(authoritativeChoices, selected, matcher)" in implementation_text,
    "Output revalidation": "matchesStacks(recipe.outputs(), displayedOutputs, outputMatcher)" in implementation_text,
    "No private reflection": "setAccessible(" not in implementation_text
    and "getDeclaredMethod(" not in implementation_text
    and "getDeclaredField(" not in implementation_text,
    "Choice regression test": "resolvesSelectedFastMachinesChoiceAndProtectsControlSlots" in test_text,
    "Output rejection test": "rejectsRecipeWhenFastMachinesOutputDoesNotMatch" in test_text,
    "Amount rejection test": "rejectsDisplayedAlternativeWithWrongRequiredAmount" in test_text,
    "Unsafe layout rejection test": "rejectsUnexpectedFastMachinesInputLayout" in test_text,
    "Release changelog": "# Slimefun Legacy 4.1.15 — FastMachines Input Filling" in changelog_text,
    "Release version": "projectVersion=4.1.15" in properties_text,
    "Guide documentation": "FastMachines filling is limited to verified ingredient slots 0–35" in guide_text,
}

failed = [name for name, passed in checks.items() if not passed]
if failed:
    for name in failed:
        print("FAIL:", name)
    raise SystemExit(1)

print("PASS: Slimefun Legacy 4.1.15 FastMachines input-fill invariants")
