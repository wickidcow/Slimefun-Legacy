#!/usr/bin/env python3
from pathlib import Path
import sys
import yaml

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
failures = []

def require(value, message):
    if not value:
        failures.append(message)

def read(path):
    return (root / path).read_text(encoding="utf-8")

manager_path = "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/guide/enhanced/LegacyRecipeFillManager.java"
manager = read(manager_path)
settings = read("src/main/java/io/github/thebusybiscuit/slimefun4/implementation/guide/enhanced/LegacyGuideSettings.java")
bootstrap = read("src/main/java/io/github/thebusybiscuit/slimefun4/implementation/guide/enhanced/LegacyGuideBootstrap.java")
guide = read("src/main/java/io/github/thebusybiscuit/slimefun4/implementation/guide/enhanced/EnhancedSurvivalSlimefunGuide.java")
config = yaml.safe_load(read("src/main/resources/enhanced-guide.yml"))

require("LegacyRecipeFillManager.initialize(plugin)" in bootstrap, "recipe fill manager is not initialized")
require("decorateRecipePage(player, item)" in guide, "recipe page is not decorated")
require("Interaction.INTERACT_BLOCK" in manager, "protection check is missing")
require("isOwnedByCurrentRegion" in manager, "Folia ownership check is missing")
require("RecipeType.ENHANCED_CRAFTING_TABLE" in manager, "Enhanced Crafting Table support is missing")
require("RecipeType.MAGIC_WORKBENCH" in manager, "Magic Workbench support is missing")
require("RecipeType.ARMOR_FORGE" in manager, "Armor Forge support is missing")
require("setStorageContents" in manager and "setContents" in manager, "transaction commit writes are missing")
require("cloneContents" in manager, "inventory snapshot cloning is missing")
require("restoreInventories" in manager, "inventory rollback is missing")
require("InventoryAction.COLLECT_TO_CURSOR" in manager, "double-click button protection is missing")
require("InventoryAction.MOVE_TO_OTHER_INVENTORY" in manager, "shift-transfer button protection is missing")
require("InventoryDragEvent" in manager, "drag button protection is missing")
require("PlayerQuitEvent" in manager, "disconnect cleanup is missing")
require("isFacing(dispenser, BlockFace.UP)" in manager or "directional.getFacing() == BlockFace.UP" in manager, "Armor Forge dispenser orientation check is missing")
require("matchesRecipeIngredient" in manager, "machine-specific recipe matching is missing")
require("SlimefunBackpack" in manager, "backpack recipe compatibility is missing")
require("getBoolean(\"features.recipe-fill.enabled\", true)" in settings, "recipe fill default is missing")
require(config["features"]["recipe-fill"]["enabled"] is True, "recipe fill is not enabled in defaults")
require(2 <= config["features"]["recipe-fill"]["target-range"] <= 12, "invalid target range")
require(1 <= config["features"]["recipe-fill"]["maximum-sets"] <= 64, "invalid maximum sets")

# lightweight source integrity checks
for path in [manager_path,
             "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/guide/enhanced/LegacyGuideSettings.java",
             "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/guide/enhanced/LegacyGuideBootstrap.java",
             "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/guide/enhanced/EnhancedSurvivalSlimefunGuide.java"]:
    text = read(path)
    require(text.count("{") == text.count("}"), f"unbalanced braces: {path}")

if failures:
    for failure in failures:
        print("FAIL:", failure)
    raise SystemExit(1)
print("PASS: enhanced guide Phase 2 static invariants")
