#!/usr/bin/env python3
"""Static source checks for Slimefun Legacy's native enhanced guide."""

from __future__ import annotations

import re
import sys
from pathlib import Path

import yaml

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()

JAVA_ROOT = ROOT / "src/main/java/io/github/thebusybiscuit/slimefun4"
GUIDE = JAVA_ROOT / "implementation/guide"
ENHANCED = GUIDE / "enhanced"
LANGUAGE_OPTION = JAVA_ROOT / "core/guide/options/PlayerLanguageOption.java"
REQUIRED_FILES = [
    JAVA_ROOT / "core/SlimefunRegistry.java",
    GUIDE / "GuideSearchIndex.java",
    GUIDE / "IndexedSurvivalSlimefunGuide.java",
    LANGUAGE_OPTION,
    ENHANCED / "LegacyGuideBootstrap.java",
    ENHANCED / "LegacyGuideSettings.java",
    ENHANCED / "LegacyGuideBookmarks.java",
    ENHANCED / "EnhancedSurvivalSlimefunGuide.java",
    ENHANCED / "IndexedEnhancedSurvivalSlimefunGuide.java",
    ENHANCED / "RecipeUsageIndexedEnhancedSurvivalSlimefunGuide.java",
    ENHANCED / "LegacyRecipeUsageBrowser.java",
    ENHANCED / "EnhancedCheatSheetSlimefunGuide.java",
    ROOT / "src/main/resources/enhanced-guide.yml",
    ROOT / "EVERYTHING_THAT_CHANGED.md",
]


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def read(path: Path) -> str:
    require(path.is_file(), f"Missing required file: {path.relative_to(ROOT)}")
    return path.read_text(encoding="utf-8")


def validate_layout(config: dict, name: str, marker: str) -> None:
    layout = config["format"][name]
    require(isinstance(layout, list) and len(layout) == 6, f"format.{name} must contain six rows")
    require(all(isinstance(row, str) and len(row) == 9 for row in layout), f"format.{name} rows must be nine characters")
    require(any(marker in row for row in layout), f"format.{name} must contain '{marker}' slots")
    require(any("P" in row for row in layout), f"format.{name} must contain a previous-page slot")
    require(any("N" in row for row in layout), f"format.{name} must contain a next-page slot")


def main() -> int:
    sources = {path: read(path) for path in REQUIRED_FILES}
    combined_java = "\n".join(text for path, text in sources.items() if path.suffix == ".java")

    config = yaml.safe_load(sources[ROOT / "src/main/resources/enhanced-guide.yml"])
    require(isinstance(config, dict), "enhanced-guide.yml must contain a YAML mapping")
    validate_layout(config, "main", "G")
    validate_layout(config, "group", "i")
    validate_layout(config, "search", "i")
    validate_layout(config, "bookmarks", "i")

    recipe_usages = config["features"]["recipe-usages"]
    require(recipe_usages.get("enabled") is True, "Recipe-usage browser must be enabled by default")
    require(recipe_usages.get("index-items-per-tick") == 6, "Recipe-usage item batch default changed")
    require(recipe_usages.get("index-budget-micros") == 1500, "Recipe-usage time budget default changed")

    registry = sources[JAVA_ROOT / "core/SlimefunRegistry.java"]
    bootstrap = sources[ENHANCED / "LegacyGuideBootstrap.java"]
    search_index = sources[GUIDE / "GuideSearchIndex.java"]
    indexed_classic = sources[GUIDE / "IndexedSurvivalSlimefunGuide.java"]
    language_option = sources[LANGUAGE_OPTION]
    guide = sources[ENHANCED / "EnhancedSurvivalSlimefunGuide.java"]
    indexed_guide = sources[ENHANCED / "IndexedEnhancedSurvivalSlimefunGuide.java"]
    usage_guide = sources[ENHANCED / "RecipeUsageIndexedEnhancedSurvivalSlimefunGuide.java"]
    usage_browser = sources[ENHANCED / "LegacyRecipeUsageBrowser.java"]
    cheat_guide = sources[ENHANCED / "EnhancedCheatSheetSlimefunGuide.java"]
    bookmarks = sources[ENHANCED / "LegacyGuideBookmarks.java"]

    require("LegacyGuideBootstrap.register(plugin, guides);" in registry, "Registry does not use the native guide bootstrap")
    require("new RecipeUsageIndexedEnhancedSurvivalSlimefunGuide()" in bootstrap,
            "4.2 indexed enhanced survival guide is not registered")
    require("extends IndexedEnhancedSurvivalSlimefunGuide" in usage_guide,
            "4.2 guide wrapper no longer preserves indexed enhanced search")
    require("LegacyRecipeUsageBrowser.initialize(plugin);" in bootstrap,
            "4.2 recipe-usage browser is not initialized")
    require("new EnhancedCheatSheetSlimefunGuide()" in bootstrap, "Enhanced cheat guide is not registered")
    require("new IndexedSurvivalSlimefunGuide()" in bootstrap, "Indexed classic survival fallback is missing")
    require("new CheatSheetSlimefunGuide()" in bootstrap, "Classic cheat fallback is missing")
    require('getPlugin("JustEnoughGuide")' in bootstrap, "JEG coexistence warning is missing")

    require("searchByName" in indexed_classic, "Classic guide does not use the shared search index")
    require("searchSmart" in indexed_guide, "Enhanced guide does not use the shared search index")
    require("sortedByName" in search_index, "Shared search index does not keep a pre-sorted view")
    require("entriesByItem" in search_index, "Shared search index does not cache item metadata")

    for marker in ["id:", "addon:", "group:", "recipe:"]:
        require(marker in search_index, f"Smart-search filter missing: {marker}")
    require("openIndexedSearchPage" in indexed_guide and "pageCount" in indexed_guide,
            "Paged indexed search implementation is missing")
    require(
        "action.isShiftClicked() && LegacyGuideSettings.get().hasBookmarks()" in guide
        and "action.isShiftClicked() && LegacyGuideSettings.get().hasBookmarks()" in indexed_guide,
        "Shift-click bookmark control is missing",
    )
    require(
        "openItem(profile, pl, item, action.isRightClicked())" in guide
        and "openIndexedItem(profile, clickedPlayer, item, action.isRightClicked())" in indexed_guide,
        "Right-click full-stack cheat control is missing",
    )
    require("research.unlockFromGuide" in guide, "Research unlock behavior is missing")
    require('hasPermission("slimefun.cheat.items")' in indexed_guide, "Cheat-item permission guard is missing")
    require("displayItem(profile, item, true)" in guide, "Classic recipe rendering bridge is missing")
    require("getVisibleItemGroups(player, profile, SlimefunGuideMode.SURVIVAL_MODE)" in cheat_guide,
            "Enhanced cheat guide does not mirror the normal guide category hierarchy")
    require("CheatAddonItemGroup.createAddonFolders" not in cheat_guide,
            "Enhanced cheat guide still uses generated generic addon folders")

    settings_controls = guide.split("findSlots(format, 'T')) {", 1)[1].split(
        "findSlots(format, 'S')) {", 1
    )[0]
    require("SlimefunGuideSettings.openSettings" in settings_controls,
            "Enhanced guide Settings & Info control is missing")
    require("if (isSurvivalMode())" not in settings_controls,
            "Enhanced guide Settings & Info must remain available in cheat mode")
    require("Language language = Slimefun.getLocalization().getLanguage(p);" in language_option,
            "Language option no longer renders from the active/default language")
    display_language = language_option.split("public Optional<ItemStack> getDisplayItem", 1)[1].split(
        "public void onClick", 1
    )[0]
    require("return Optional.empty()" not in display_language,
            "English-only mode must not hide the language option")
    require("if (Slimefun.getLocalization().isEnabled())" in language_option,
            "Language selector must only enumerate additional languages when translations are enabled")

    require("guide-bookmarks.yml" in bookmarks and "itemId" in bookmarks, "Persistent item-ID bookmarks are missing")
    require(
        "private final Map<UUID, LinkedHashSet<String>> bookmarks = new HashMap<>();" in bookmarks,
        "Per-player in-memory bookmark cache is missing",
    )
    require(
        "bookmarks.computeIfAbsent(" in bookmarks,
        "Bookmark membership checks no longer reuse the in-memory cache",
    )
    require(
        "implements Listener" in bookmarks
        and "registerEvents(this, plugin)" in bookmarks
        and "bookmarks.remove(event.getPlayer().getUniqueId())" in bookmarks,
        "Bookmark cache eviction on player quit is missing",
    )

    require("runLater(() -> runBuildBatch(state), 1L)" in usage_browser,
            "Recipe-usage indexing no longer yields between scheduled batches")
    require("processed < state.maxItemsPerTick" in usage_browser,
            "Recipe-usage item batch cap is missing")
    require("System.nanoTime() - batchStarted >= state.batchBudgetNanos" in usage_browser,
            "Recipe-usage elapsed-time budget is missing")
    require("builds.putIfAbsent(worldId, created)" in usage_browser,
            "Recipe-usage browser no longer coalesces one build per world")
    require("getOrBuildIndex(" not in usage_browser,
            "Synchronous reverse-index builder returned")

    forbidden = ["getDeclaredField", "setAccessible(", "java.lang.reflect", "pinyin", "auto-update"]
    for token in forbidden:
        require(token not in combined_java.lower() if token == "pinyin" else token not in combined_java,
                f"Forbidden implementation dependency found: {token}")

    require(not re.search(r"[\u3400-\u4dbf\u4e00-\u9fff\uf900-\ufaff]", combined_java),
            "Chinese characters found in player-facing Java sources")

    print("Native enhanced guide static verification passed.")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, KeyError, TypeError, yaml.YAMLError) as error:
        print(f"Native enhanced guide verification failed: {error}", file=sys.stderr)
        raise SystemExit(1)
