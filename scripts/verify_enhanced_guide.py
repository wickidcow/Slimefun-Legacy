#!/usr/bin/env python3
"""Static source checks for Slimefun Legacy's native enhanced guide."""

from __future__ import annotations

import re
import sys
from pathlib import Path

import yaml

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()

JAVA_ROOT = ROOT / "src/main/java/io/github/thebusybiscuit/slimefun4"
ENHANCED = JAVA_ROOT / "implementation/guide/enhanced"
REQUIRED_FILES = [
    JAVA_ROOT / "core/SlimefunRegistry.java",
    ENHANCED / "LegacyGuideBootstrap.java",
    ENHANCED / "LegacyGuideSettings.java",
    ENHANCED / "LegacyGuideBookmarks.java",
    ENHANCED / "EnhancedSurvivalSlimefunGuide.java",
    ENHANCED / "EnhancedCheatSheetSlimefunGuide.java",
    JAVA_ROOT / "implementation/guide/CheatAddonItemGroup.java",
    ROOT / "src/main/resources/enhanced-guide.yml",
    ROOT / "ENHANCED_GUIDE.md",
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

    registry = sources[JAVA_ROOT / "core/SlimefunRegistry.java"]
    bootstrap = sources[ENHANCED / "LegacyGuideBootstrap.java"]
    guide = sources[ENHANCED / "EnhancedSurvivalSlimefunGuide.java"]
    cheat_guide = sources[ENHANCED / "EnhancedCheatSheetSlimefunGuide.java"]
    bookmarks = sources[ENHANCED / "LegacyGuideBookmarks.java"]
    cheat_addons = sources[JAVA_ROOT / "implementation/guide/CheatAddonItemGroup.java"]

    require("LegacyGuideBootstrap.register(plugin, guides);" in registry, "Registry does not use the native guide bootstrap")
    require("new EnhancedSurvivalSlimefunGuide()" in bootstrap, "Enhanced survival guide is not registered")
    require("new EnhancedCheatSheetSlimefunGuide()" in bootstrap, "Enhanced cheat guide is not registered")
    require("new SurvivalSlimefunGuide()" in bootstrap, "Classic survival fallback is missing")
    require("new CheatSheetSlimefunGuide()" in bootstrap, "Classic cheat fallback is missing")
    require('getPlugin("JustEnoughGuide")' in bootstrap, "JEG coexistence warning is missing")

    for marker in ["id:", "addon:", "group:", "recipe:"]:
        require(marker in guide, f"Smart-search filter missing: {marker}")
    require("openSearchPage" in guide and "pageCount" in guide, "Paged search implementation is missing")
    require("action.isRightClicked()" in guide, "Right-click bookmark control is missing")
    require("research.unlockFromGuide" in guide, "Research unlock behavior is missing")
    require('hasPermission("slimefun.cheat.items")' in guide, "Cheat-item permission guard is missing")
    require("displayItem(profile, item, true)" in guide, "Classic recipe rendering bridge is missing")
    require("CheatAddonItemGroup.createAddonFolders" in cheat_guide,
            "Enhanced cheat guide does not use plugin-based addon folders")
    require("group instanceof SubItemGroup" in cheat_addons,
            "Cheat addon folders do not suppress flattened child categories")
    require("group instanceof NestedItemGroup" in cheat_addons and "hasVisibleSubGroups(player)" in cheat_addons,
            "Cheat addon folders do not preserve nested addon categories")
    require("group.isVisible(player)" in cheat_addons,
            "Cheat addon folders do not hide empty or disabled normal categories")
    require("guide-bookmarks.yml" in bookmarks and "itemId" in bookmarks, "Persistent item-ID bookmarks are missing")

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
