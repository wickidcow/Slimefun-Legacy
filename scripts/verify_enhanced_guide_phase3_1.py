#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".")
browser = root / "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/guide/enhanced/LegacyMachineRecipeBrowser.java"
guide = root / "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/guide/enhanced/EnhancedSurvivalSlimefunGuide.java"
bootstrap = root / "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/guide/enhanced/LegacyGuideBootstrap.java"
settings = root / "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/guide/enhanced/LegacyGuideSettings.java"
config = root / "src/main/resources/enhanced-guide.yml"

required = [browser, guide, bootstrap, settings, config]
missing = [str(path.relative_to(root)) for path in required if not path.is_file()]
if missing:
    raise SystemExit("Missing Phase 3.1 files: " + ", ".join(missing))

browser_text = browser.read_text(encoding="utf-8")
guide_text = guide.read_text(encoding="utf-8")
bootstrap_text = bootstrap.read_text(encoding="utf-8")
settings_text = settings.read_text(encoding="utf-8")
config_text = config.read_text(encoding="utf-8")

checks = {
    "FastMachines package adapter": 'FAST_MACHINES_PACKAGE = "net.guizhanss.fastmachines."' in browser_text,
    "Public recipe getter": 'getMethod("getRecipes")' in browser_text,
    "Public recipe input getter": 'getMethod("getInputs")' in browser_text,
    "Public recipe output getter": 'getMethod("getOutputs")' in browser_text,
    "World recipe filtering": 'getMethod("isDisabledIn", World.class)' in browser_text,
    "Alternative ingredient support": 'getMethod("getChoices")' in browser_text,
    "Exact base item support": 'getMethod("getBaseItem")' in browser_text,
    "Protected PDC button": 'enhanced_guide_machine_recipes' in browser_text,
    "Paged recipe list": 'LIST_SLOTS' in browser_text and 'openRecipeList' in browser_text,
    "Detailed recipe view": 'openRecipeDetail' in browser_text,
    "Guide decoration": 'LegacyMachineRecipeBrowser.get().decorateMachinePage' in guide_text,
    "Bootstrap initialization": 'LegacyMachineRecipeBrowser.initialize(plugin)' in bootstrap_text,
    "Settings default enabled": 'features.machine-recipes.enabled", true' in settings_text,
    "YAML option": 'machine-recipes:' in config_text and 'enabled: true' in config_text,
    "No private field reflection": 'getDeclaredField' not in browser_text and 'setAccessible' not in browser_text,
}

failed = [name for name, passed in checks.items() if not passed]
if failed:
    for name in failed:
        print("FAIL:", name)
    raise SystemExit(1)

print("PASS: enhanced guide Phase 3.1 FastMachines recipe-browser invariants")
