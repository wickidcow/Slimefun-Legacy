#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path
import sys

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()

paths = {
    "guard": ROOT / "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/guide/GuideRuntimeGuard.java",
    "entry": ROOT / "src/main/java/io/github/thebusybiscuit/slimefun4/core/guide/SlimefunGuide.java",
    "history": ROOT / "src/main/java/io/github/thebusybiscuit/slimefun4/core/guide/GuideHistory.java",
    "nested": ROOT / "src/main/java/io/github/thebusybiscuit/slimefun4/api/items/groups/NestedItemGroup.java",
    "classic": ROOT / "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/guide/SurvivalSlimefunGuide.java",
    "enhanced": ROOT / "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/guide/enhanced/EnhancedSurvivalSlimefunGuide.java",
}

errors: list[str] = []


def require(condition: bool, message: str) -> None:
    if not condition:
        errors.append(message)


def read(name: str) -> str:
    path = paths[name]
    require(path.is_file(), f"missing required file: {path.relative_to(ROOT)}")
    return path.read_text(encoding="utf-8") if path.is_file() else ""


guard = read("guard")
entry = read("entry")
history = read("history")
nested = read("nested")
classic = read("classic")
enhanced = read("enhanced")
classic_compact = "".join(classic.split())
enhanced_compact = "".join(enhanced.split())

require("public static <T> T getOrDefault" in guard, "render fallback guard is missing")
require("player.getUniqueId()" in guard and "player.getName()" in guard, "player diagnostics are incomplete")
require("activeChain=" in guard and "depth=" in guard, "guide call-chain diagnostics are incomplete")
require("RuntimeException | LinkageError | StackOverflowError" in guard, "runtime failure boundary is incomplete")
require("WARNING_COOLDOWN_MILLIS" in guard and "SLOW_GUIDE_CALL_NANOS" in guard, "diagnostic throttling is missing")
require("GuideRuntimeGuard.run" in entry, "public SlimefunGuide entry points are not guarded")
require("Slimefun.runSyncFor(player" in entry, "player-owned synchronous guide scheduling is missing")

for operation in (
    "restore history main menu page",
    "restore history item group page",
    "restore history Slimefun item",
    "restore history recipe page",
    "restore history search",
):
    require(operation in history, f"GuideHistory guard is missing: {operation}")
require(history.count("GuideRuntimeGuard.run(") >= 5, "GuideHistory dispatch is not fully guarded")

require("open nested item group page 1" in nested, "nested root opening is not guarded")
require(nested.count("GuideRuntimeGuard.getOrDefault(") >= 2, "nested visibility/icon fallback is missing")
require(nested.count("GuideRuntimeGuard.run(") >= 3, "nested pagination is not fully guarded")
require("brokenCategoryIcon" in nested, "nested broken-category fallback icon is missing")

require("safeItemGroupIcon(profile,group,p)" in classic_compact, "classic category icon fallback is missing")
require("safeItemGroupName(profile,group,p)" in classic_compact, "classic category name fallback is missing")
require("\"open FlexItemGroup\"" in classic, "classic FlexItemGroup guard is missing")
require("SlimefunGuide.openMainMenu(profile,getMode(),next);" in classic_compact, "classic main-menu pagination bypasses the guard")
require("SlimefunGuide.openItemGroup(profile,itemGroup,getMode(),next);" in classic_compact, "classic group pagination bypasses the guard")
require("SlimefunGuide.displayItem(profile,sfitem,true);" in classic_compact, "classic item click bypasses the guard")
require("if(itemGroupinstanceofFlexItemGroupflexItemGroup){flexItemGroup.open(p,profile,getMode());return;}" not in classic_compact,
        "unguarded classic FlexItemGroup block remains")
require("\n                openMainMenu(profile, next);" not in classic, "unguarded classic main-menu call remains")
require("\n                openItemGroup(profile, itemGroup, next);" not in classic, "unguarded classic group-page call remains")
require("displayItem(profile,itemstack,0,true);" not in classic_compact, "unguarded classic recipe item call remains")

require("import io.github.thebusybiscuit.slimefun4.implementation.guide.GuideRuntimeGuard;" in enhanced,
        "enhanced guide guard import is missing")
require("\"open enhanced FlexItemGroup\"" in enhanced, "enhanced FlexItemGroup guard is missing")
require("runEnhancedPage" in enhanced, "enhanced bookmark/search page guard is missing")
require("safeItemGroupIcon(profile,group,player)" in enhanced_compact, "enhanced category icon fallback is missing")
require("SlimefunGuide.openMainMenu(profile,getMode(),safePage-1)" in enhanced_compact,
        "enhanced main-menu pagination bypasses the guard")
require("SlimefunGuide.openItemGroup(profile,itemGroup,getMode(),safePage-1)" in enhanced_compact,
        "enhanced group pagination bypasses the guard")
require("SlimefunGuide.displayItem(profile,item,true);" in enhanced_compact,
        "enhanced item click bypasses the guard")
require("if(itemGroupinstanceofFlexItemGroupflexItemGroup){flexItemGroup.open(player,profile,getMode());return;}" not in enhanced_compact,
        "unguarded enhanced FlexItemGroup block remains")
require("\n                    openMainMenu(profile, history.getMainMenuPage());" not in enhanced,
        "unguarded enhanced back-to-main call remains")

for name, text in (("guard", guard), ("history", history), ("nested", nested), ("classic", classic), ("enhanced", enhanced)):
    require(text.count("{") == text.count("}"), f"unbalanced braces in {name}")

if errors:
    print("Slimefun Legacy 4.1.18 Phase 1B verification failed:", file=sys.stderr)
    for error in errors:
        print(f" - {error}", file=sys.stderr)
    raise SystemExit(1)

print("Slimefun Legacy 4.1.18 Phase 1B verification passed.")
