#!/usr/bin/env python3
"""Verify the Phase 4 universal machine recipe provider API and browser wiring."""

from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".")
api_root = root / "src/main/java/io/github/thebusybiscuit/slimefun4/api/recipes/machine"
provider_impl = root / "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/guide/enhanced/LegacyMachineRecipeProviders.java"
browser = root / "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/guide/enhanced/LegacyMachineRecipeBrowser.java"
enhanced_cheat = root / "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/guide/enhanced/EnhancedCheatSheetSlimefunGuide.java"
classic_cheat = root / "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/guide/CheatSheetSlimefunGuide.java"
nested_group = root / "src/main/java/io/github/thebusybiscuit/slimefun4/api/items/groups/NestedItemGroup.java"
config = root / "src/main/resources/enhanced-guide.yml"
provider_test = root / "src/test/java/io/github/thebusybiscuit/slimefun4/implementation/guide/enhanced/TestPublicMachineRecipeProvider.java"

api_files = (
    "MachineRecipeDisplay.java",
    "MachineRecipeDisplayItem.java",
    "MachineRecipeIngredient.java",
    "MachineRecipeLayout.java",
    "MachineRecipeProvider.java",
    "MachineRecipeProviderRegistry.java",
)
required = [api_root / name for name in api_files] + [provider_impl, browser, enhanced_cheat, classic_cheat, nested_group, config, provider_test]
missing = [str(path.relative_to(root)) for path in required if not path.is_file()]
if missing:
    raise SystemExit("Missing Phase 4 files: " + ", ".join(missing))

api_text = "\n".join((api_root / name).read_text(encoding="utf-8") for name in api_files)
provider_text = provider_impl.read_text(encoding="utf-8")
browser_text = browser.read_text(encoding="utf-8")
config_text = config.read_text(encoding="utf-8")
cheat_text = enhanced_cheat.read_text(encoding="utf-8") + classic_cheat.read_text(encoding="utf-8")
nested_text = nested_group.read_text(encoding="utf-8")
test_text = provider_test.read_text(encoding="utf-8")

checks = {
    "Addon-facing API annotations": api_text.count("@SlimefunAPI") >= 6,
    "Normalized recipe model": "class MachineRecipeDisplay" in api_text,
    "Alternative ingredient model": "class MachineRecipeIngredient" in api_text,
    "Provider interface": "interface MachineRecipeProvider" in api_text,
    "Provider registry": "class MachineRecipeProviderRegistry" in api_text,
    "Direct item integration": "interface MachineRecipeDisplayItem" in api_text,
    "Priority ordering": "getPriority" in api_text and ".reversed()" in api_text,
    "Direct provider": "class DirectProvider" in provider_text,
    "Legacy AContainer provider": "class ContainerProvider" in provider_text and "getMachineRecipes()" in provider_text,
    "Public member compatibility provider": "class PublicMethodProvider" in provider_text and "MACHINE_METHOD_NAMES" in provider_text and "MACHINE_FIELD_NAMES" in provider_text and "findMachineSources" in provider_text,
    "Supreme method conventions": "getRecipeProcess" in provider_text and "getTimeProcess" in provider_text,
    "Supreme field conventions": "machineRecipes" in provider_text and 'type.getField(name)' in provider_text,
    "Numbered recipe getters": "findNumberedMethods" in provider_text and 'findMethod(type, "getChance")' in provider_text,
    "Multiple public sources are merged": "IdentityHashMap" in provider_text and "for (RecipeSourceAccessor source : sources)" in provider_text,
    "Standard and addon-owned sources are merged": "Merge the standard list first" in provider_text and "container.getMachineRecipes()" in provider_text,
    "Public provider precedes plain container provider": "return 850;" in provider_text,
    "Aggregate getter keeps numbered fallback": "if (!aggregateItems.isEmpty())" in provider_text,
    "Iterable and array recipe sources": "collectObjects" in provider_text and "Array.getLength" in provider_text,
    "RecipeDisplayItem fallback": "class RecipeDisplayProvider" in provider_text,
    "FastMachines provider retained": "class FastMachinesProvider" in provider_text and 'getMethod("getRecipes")' in provider_text,
    "FastMachines alternatives retained": 'getMethod("getChoices")' in provider_text and 'getMethod("getBaseItem")' in provider_text,
    "World filtering retained": 'findMethod(type, "isDisabledIn", World.class)' in provider_text,
    "Browser uses provider registry": "MachineRecipeProviderRegistry.getProviders()" in browser_text,
    "Browser no longer hard-codes FastMachines": "FAST_MACHINES_PACKAGE" not in browser_text,
    "Universal button wording": "View everything this machine can process." in browser_text,
    "Structured metadata display": "Processing ticks:" in browser_text and "Energy use:" in browser_text,
    "No private field reflection": ".getDeclaredField(" not in provider_text and ".getDeclaredMethod(" not in provider_text and ".setAccessible(" not in provider_text,
    "Cheat guide mirrors normal category hierarchy": cheat_text.count("SlimefunGuideMode.SURVIVAL_MODE") >= 2,
    "Generated generic addon folders are disabled": "CheatAddonItemGroup.createAddonFolders" not in cheat_text,
    "Nested groups expose safe visibility check": "boolean hasVisibleSubGroups" in nested_text and "isVisibleInNested" in nested_text,
    "Supreme regression coverage": "SupremeStyleMachine" in test_text and "machineRecipes" in test_text,
    "Numbered getter regression coverage": "NumberedRecipeMachine" in test_text and "getInput2" in test_text,
    "Map recipe regression coverage": "MapRecipeMachine" in test_text and "recipeMap" in test_text,
    "Configuration remains enabled": "machine-recipes:" in config_text and "enabled: true" in config_text,
}

failed = [name for name, passed in checks.items() if not passed]
if failed:
    for name in failed:
        print("FAIL:", name)
    raise SystemExit(1)

print("PASS: enhanced guide Phase 4 universal machine recipe provider invariants")
