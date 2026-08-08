#!/usr/bin/env python3
"""Static invariants for Slimefun Legacy Native Enhanced Guide Phase 3."""

from pathlib import Path
import sys
import yaml

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
FAILURES: list[str] = []


def require(value: bool, message: str) -> None:
    if not value:
        FAILURES.append(message)


def read(relative: str) -> str:
    path = ROOT / relative
    require(path.is_file(), f"missing required file: {relative}")
    return path.read_text(encoding="utf-8") if path.is_file() else ""


manager_path = "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/guide/enhanced/LegacyRecipeFillManager.java"
settings_path = "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/guide/enhanced/LegacyGuideSettings.java"
bootstrap_path = "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/guide/enhanced/LegacyGuideBootstrap.java"
config_path = "src/main/resources/enhanced-guide.yml"

manager = read(manager_path)
settings = read(settings_path)
bootstrap = read(bootstrap_path)
config = yaml.safe_load(read(config_path))

# Phase 3 recipe classifications.
for recipe_type in (
    "GRIND_STONE",
    "SMELTERY",
    "ORE_CRUSHER",
    "COMPRESSOR",
    "PRESSURE_CHAMBER",
):
    require(f"RecipeType.{recipe_type}" in manager, f"missing unordered support: {recipe_type}")
require("RecipeType.ANCIENT_ALTAR" in manager, "Ancient Altar classification is missing")
require("UNORDERED_DISPENSER" in manager, "unordered recipe kind is missing")
require("ANCIENT_ALTAR" in manager, "altar recipe kind is missing")

# Registered multiblock matching and target validation.
require("recipeType.getMachine()" in manager, "registered recipe machine lookup is missing")
require("instanceof MultiBlockMachine" in manager, "core multiblock machine validation is missing")
require("machine.getMultiBlock()" in manager, "registered multiblock structure lookup is missing")
require("MultiBlock.getSupportedTags()" in manager, "supported material tag matching is missing")
require("RecipeType.PRESSURE_CHAMBER.equals(recipeType)" in manager, "pressure chamber direction branch is missing")
require("BlockFace.DOWN" in manager and "BlockFace.UP" in manager, "required dispenser direction checks are missing")
require("Clear unrelated items from the machine dispenser first" in manager, "unordered foreign-item rejection is missing")
require("aggregateRequirements" in manager, "unordered repeated-requirement aggregation is missing")

# Ancient Altar structure, order and item presentation.
require("ALTAR_RECIPE_SLOTS = {0, 1, 2, 5, 8, 7, 6, 3}" in manager, "altar slot order changed")
for offset in (
    "{2, 0, -2}",
    "{3, 0, 0}",
    "{2, 0, 2}",
    "{0, 0, 3}",
    "{-2, 0, 2}",
    "{-3, 0, 0}",
    "{-2, 0, -2}",
    "{0, 0, -3}",
):
    require(offset in manager, f"missing altar pedestal offset: {offset}")
require("StorageCacheUtils.isBlock" in manager, "Slimefun block identity checks are missing")
require("getAncientAltarPedestals" in manager, "altar pedestal validation is missing")
require("getPlacedItem(pedestal).isPresent()" in manager, "occupied-pedestal check is missing")
require("pedestal.getRelative(BlockFace.UP).getType().isAir()" in manager, "pedestal obstruction check is missing")
require("ItemSpawnReason.ANCIENT_PEDESTAL_PLACE_ITEM" in manager, "altar item spawn reason is missing")
require("AncientPedestal.ITEM_PREFIX" in manager, "altar display identity is missing")
require("markAsNoPickup" in manager, "altar no-pickup marker is missing")
require("armorStand.addPassenger(entity)" in manager, "altar armor-stand carrier is missing")
require("rollbackAltarPlacement" in manager, "partial altar rollback is missing")
require("setStorageContents(cloneContents(originalPlayer))" in manager, "altar inventory restoration is missing")
require("prepareCatalystInHand" in manager, "optional catalyst preparation is missing")

# Altar activity lock follows configurable ritual length.
require("PlayerRightClickEvent" in manager, "altar activation observation is missing")
require("36L * ancientAltar.getStepDelay()" in manager, "dynamic altar ritual lock calculation is missing")
require("observedAltarLocks" in manager, "altar activity lock storage is missing")

# Protection, Folia and transactional behavior.
require("Interaction.INTERACT_BLOCK" in manager, "protection check is missing")
require("isOwnedByCurrentRegion" in manager, "Folia ownership check is missing")
require("cloneContents" in manager, "inventory cloning is missing")
require("restoreInventories" in manager, "dispenser transaction rollback is missing")
require("ItemStack[] trial = cloneContents(contents)" in manager, "all-or-nothing shaped extraction trial is missing")
require("contents[slot] = cloneItem(trial[slot])" in manager, "shaped extraction commit is missing")

# Ingredient reporting and non-execution boundary.
require("sendIngredientReport" in manager, "full ingredient report is missing")
require("event.isRightClick()" in manager, "right-click report control is missing")
require("getRecipeFillMaximumMissingLines" in manager, "configurable missing lore limit is missing")
require("Sub-recipe available:" in manager, "sub-recipe ID report is missing")
require("Items are moved, never crafted." in manager, "non-crafting boundary text is missing")
require("getNearby" not in manager, "nearby-storage scanning was introduced unexpectedly")

# Settings and defaults.
expected_setting_fragments = (
    'getBoolean("features.recipe-fill.unordered-machines", true)',
    'getBoolean("features.recipe-fill.ancient-altar.enabled", true)',
    'getBoolean("features.recipe-fill.ancient-altar.prepare-catalyst-in-hand", true)',
    'getBoolean("features.recipe-fill.missing-report.show-sub-recipe-hints", true)',
    'getInt("features.recipe-fill.ancient-altar.activation-lock-seconds", 15)',
    'getInt("features.recipe-fill.missing-report.maximum-lore-lines", 4)',
)
for fragment in expected_setting_fragments:
    require(fragment in settings, f"missing settings default: {fragment}")
require("unordered machine fill" in bootstrap.lower(), "Phase 3 bootstrap summary is missing unordered support")
require("ancient altar" in bootstrap.lower(), "Phase 3 bootstrap summary is missing altar support")

recipe_fill = config["features"]["recipe-fill"]
require(recipe_fill["unordered-machines"] is True, "unordered machines are not enabled by default")
require(recipe_fill["ancient-altar"]["enabled"] is True, "altar preparation is not enabled by default")
require(
    recipe_fill["ancient-altar"]["prepare-catalyst-in-hand"] is True,
    "catalyst preparation is not enabled by default",
)
require(5 <= recipe_fill["ancient-altar"]["activation-lock-seconds"] <= 60, "invalid altar lock default")
require(1 <= recipe_fill["missing-report"]["maximum-lore-lines"] <= 8, "invalid missing lore limit")
require(recipe_fill["missing-report"]["show-sub-recipe-hints"] is True, "sub-recipe hints are not enabled")

# Lightweight source integrity checks.
for path in (manager_path, settings_path, bootstrap_path):
    text = read(path)
    require(text.count("{") == text.count("}"), f"unbalanced braces: {path}")

require((ROOT / "docs/history/ENHANCED_GUIDE.md").is_file(), "missing consolidated Enhanced Guide documentation")

if FAILURES:
    for failure in FAILURES:
        print("FAIL:", failure)
    raise SystemExit(1)

print("PASS: enhanced guide Phase 3 static invariants")
