#!/usr/bin/env python3
"""Verify the Phase 4 universal machine recipe provider API and browser wiring."""

from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".")
api_root = root / "src/main/java/io/github/thebusybiscuit/slimefun4/api/recipes/machine"
provider_impl = root / "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/guide/enhanced/LegacyMachineRecipeProviders.java"
browser = root / "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/guide/enhanced/LegacyMachineRecipeBrowser.java"
config = root / "src/main/resources/enhanced-guide.yml"

api_files = (
    "MachineRecipeDisplay.java",
    "MachineRecipeDisplayItem.java",
    "MachineRecipeIngredient.java",
    "MachineRecipeLayout.java",
    "MachineRecipeProvider.java",
    "MachineRecipeProviderRegistry.java",
)
required = [api_root / name for name in api_files] + [provider_impl, browser, config]
missing = [str(path.relative_to(root)) for path in required if not path.is_file()]
if missing:
    raise SystemExit("Missing Phase 4 files: " + ", ".join(missing))

api_text = "\n".join((api_root / name).read_text(encoding="utf-8") for name in api_files)
provider_text = provider_impl.read_text(encoding="utf-8")
browser_text = browser.read_text(encoding="utf-8")
config_text = config.read_text(encoding="utf-8")

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
    "Public getter compatibility provider": "class PublicMethodProvider" in provider_text and 'getMethod("getMachineRecipes")' in provider_text,
    "RecipeDisplayItem fallback": "class RecipeDisplayProvider" in provider_text,
    "FastMachines provider retained": "class FastMachinesProvider" in provider_text and 'getMethod("getRecipes")' in provider_text,
    "FastMachines alternatives retained": 'getMethod("getChoices")' in provider_text and 'getMethod("getBaseItem")' in provider_text,
    "World filtering retained": 'findMethod(type, "isDisabledIn", World.class)' in provider_text,
    "Browser uses provider registry": "MachineRecipeProviderRegistry.getProviders()" in browser_text,
    "Browser no longer hard-codes FastMachines": "FAST_MACHINES_PACKAGE" not in browser_text,
    "Universal button wording": "View everything this machine can process." in browser_text,
    "Structured metadata display": "Processing ticks:" in browser_text and "Energy use:" in browser_text,
    "No private field reflection": "getDeclaredField" not in provider_text and "setAccessible" not in provider_text,
    "Configuration remains enabled": "machine-recipes:" in config_text and "enabled: true" in config_text,
}

failed = [name for name, passed in checks.items() if not passed]
if failed:
    for name in failed:
        print("FAIL:", name)
    raise SystemExit(1)

print("PASS: enhanced guide Phase 4 universal machine recipe provider invariants")
