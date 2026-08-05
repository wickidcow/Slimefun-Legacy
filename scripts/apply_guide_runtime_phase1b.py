#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path
import sys

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
CLASSIC = ROOT / "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/guide/SurvivalSlimefunGuide.java"
ENHANCED = ROOT / "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/guide/enhanced/EnhancedSurvivalSlimefunGuide.java"


class TransformError(RuntimeError):
    pass


def replace_required(text: str, old: str, new: str, label: str, count: int | None = None) -> str:
    found = text.count(old)
    if found == 0:
        if new in text:
            return text
        raise TransformError(f"Could not find expected {label}; the guide source may have changed upstream")
    if count is not None and found != count:
        raise TransformError(f"Expected {count} occurrence(s) of {label}, found {found}")
    return text.replace(old, new)


def add_before(text: str, marker: str, addition: str, label: str) -> str:
    if addition.strip() in text:
        return text
    position = text.find(marker)
    if position < 0:
        raise TransformError(f"Could not find insertion point for {label}")
    return text[:position] + addition + text[position:]


def transform_classic(text: str) -> str:
    marker = "// Slimefun Legacy 4.1.18 Phase 1B internal guide guards."
    if marker in text:
        return text
    text = replace_required(
        text,
        """            if (next != page && next > 0) {\n                openMainMenu(profile, next);\n            }""",
        """            if (next != page && next > 0) {\n                SlimefunGuide.openMainMenu(profile, getMode(), next);\n            }""",
        "classic previous main-menu navigation",
        1,
    )
    text = replace_required(
        text,
        """            if (next != page && next <= pages) {\n                openMainMenu(profile, next);\n            }""",
        """            if (next != page && next <= pages) {\n                SlimefunGuide.openMainMenu(profile, getMode(), next);\n            }""",
        "classic next main-menu navigation",
        1,
    )
    text = replace_required(
        text,
        "menu.addItem(index, group.getItem(p));",
        "menu.addItem(index, safeItemGroupIcon(profile, group, p));",
        "classic group icon rendering",
        1,
    )
    text = replace_required(
        text,
        "openItemGroup(profile, group, 1);",
        "SlimefunGuide.openItemGroup(profile, group, getMode(), 1);",
        "classic item-group click navigation",
        1,
    )
    text = replace_required(
        text,
        "lore.add(parent.getItem(p).getItemMeta().getDisplayName());",
        "lore.add(safeItemGroupName(profile, parent, p));",
        "classic locked parent name rendering",
        1,
    )
    text = replace_required(
        text,
        "+ group.getItem(p).getItemMeta().getDisplayName(),",
        "+ safeItemGroupName(profile, group, p),",
        "classic locked group name rendering",
        1,
    )
    text = replace_required(
        text,
        """        if (itemGroup instanceof FlexItemGroup flexItemGroup) {\n            flexItemGroup.open(p, profile, getMode());\n            return;\n        }""",
        """        if (itemGroup instanceof FlexItemGroup flexItemGroup) {\n            GuideRuntimeGuard.run(\n                    profile,\n                    getMode(),\n                    \"open FlexItemGroup\",\n                    itemGroup,\n                    () -> flexItemGroup.open(p, profile, getMode()));\n            return;\n        }""",
        "classic FlexItemGroup dispatch",
        1,
    )
    text = replace_required(
        text,
        "openItemGroup(profile, itemGroup, next);",
        "SlimefunGuide.openItemGroup(profile, itemGroup, getMode(), next);",
        "classic item-group pagination",
        2,
    )
    text = replace_required(
        text,
        "displayItem(profile, sfitem, true);",
        "SlimefunGuide.displayItem(profile, sfitem, true);",
        "classic Slimefun item click",
        1,
    )
    text = replace_required(
        text,
        "displayItem(profile, slimefunItem, true);",
        "SlimefunGuide.displayItem(profile, slimefunItem, true);",
        "classic search item click",
        1,
    )
    text = replace_required(
        text,
        "displayItem(profile, itemstack, 0, true);",
        "SlimefunGuide.displayItem(profile, itemstack, true);",
        "classic recipe ingredient click",
        2,
    )
    text = replace_required(
        text,
        "openMainMenu(profile, profile.getGuideHistory().getMainMenuPage());",
        "SlimefunGuide.openMainMenu(profile, getMode(), profile.getGuideHistory().getMainMenuPage());",
        "classic back-to-main navigation",
        2,
    )

    helpers = """
    @ParametersAreNonnullByDefault
    protected final ItemStack safeItemGroupIcon(PlayerProfile profile, ItemGroup group, Player player) {
        return GuideRuntimeGuard.getOrDefault(
                profile,
                getMode(),
                "render item group icon",
                group,
                new CustomItemStack(
                        Material.BARRIER,
                        "&4Broken guide category",
                        "",
                        "&7This addon category could not be rendered.",
                        "&7Check the server console for details."),
                () -> group.getItem(player));
    }

    @ParametersAreNonnullByDefault
    protected final String safeItemGroupName(PlayerProfile profile, ItemGroup group, Player player) {
        ItemStack icon = safeItemGroupIcon(profile, group, player);
        return icon.hasItemMeta() && icon.getItemMeta().hasDisplayName()
                ? icon.getItemMeta().getDisplayName()
                : ChatColor.RED + "Broken guide category";
    }

"""
    text = add_before(
        text,
        "    @ParametersAreNonnullByDefault\n    public void createHeader",
        helpers,
        "classic safe category render helpers",
    )
    text = text.replace(
        "public class SurvivalSlimefunGuide implements SlimefunGuideImplementation {",
        "public class SurvivalSlimefunGuide implements SlimefunGuideImplementation {\n\n    " + marker,
        1,
    )
    return text


def transform_enhanced(text: str) -> str:
    marker = "// Slimefun Legacy 4.1.18 Phase 1B internal guide guards."
    if marker in text:
        return text
    import_line = "import io.github.thebusybiscuit.slimefun4.implementation.guide.GuideRuntimeGuard;\n"
    if import_line not in text:
        import_marker = "import io.github.thebusybiscuit.slimefun4.implementation.guide.SurvivalSlimefunGuide;\n"
        if import_marker not in text:
            raise TransformError("Could not find enhanced guide import insertion point")
        text = text.replace(import_marker, import_line + import_marker, 1)

    text = replace_required(
        text,
        """        addCommonControls(menu, profile, format, safePage, pages, () -> openMainMenu(profile, safePage - 1),\n                () -> openMainMenu(profile, safePage + 1));""",
        """        addCommonControls(\n                menu,\n                profile,\n                format,\n                safePage,\n                pages,\n                () -> SlimefunGuide.openMainMenu(profile, getMode(), safePage - 1),\n                () -> SlimefunGuide.openMainMenu(profile, getMode(), safePage + 1));""",
        "enhanced main-menu pagination",
        1,
    )
    text = replace_required(
        text,
        """        if (itemGroup instanceof FlexItemGroup flexItemGroup) {\n            flexItemGroup.open(player, profile, getMode());\n            return;\n        }""",
        """        if (itemGroup instanceof FlexItemGroup flexItemGroup) {\n            GuideRuntimeGuard.run(\n                    profile,\n                    getMode(),\n                    \"open enhanced FlexItemGroup\",\n                    itemGroup,\n                    () -> flexItemGroup.open(player, profile, getMode()));\n            return;\n        }""",
        "enhanced FlexItemGroup dispatch",
        1,
    )
    text = replace_required(
        text,
        """        addCommonControls(menu, profile, format, safePage, pages,\n                () -> openItemGroup(profile, itemGroup, safePage - 1),\n                () -> openItemGroup(profile, itemGroup, safePage + 1));""",
        """        addCommonControls(\n                menu,\n                profile,\n                format,\n                safePage,\n                pages,\n                () -> SlimefunGuide.openItemGroup(profile, itemGroup, getMode(), safePage - 1),\n                () -> SlimefunGuide.openItemGroup(profile, itemGroup, getMode(), safePage + 1));""",
        "enhanced item-group pagination",
        1,
    )
    text = replace_required(
        text,
        "openSearchPage(profile, input, 1, addToHistory);",
        "runEnhancedPage(profile, \"open enhanced search page 1\", () -> openSearchPage(profile, input, 1, addToHistory));",
        "enhanced search entry",
        1,
    )
    text = replace_required(
        text,
        """        addCommonControls(menu, profile, format, safePage, pages, () -> openBookmarks(profile, safePage - 1),\n                () -> openBookmarks(profile, safePage + 1));""",
        """        addCommonControls(\n                menu,\n                profile,\n                format,\n                safePage,\n                pages,\n                () -> runEnhancedPage(\n                        profile, \"open enhanced bookmarks page \" + (safePage - 1), () -> openBookmarks(profile, safePage - 1)),\n                () -> runEnhancedPage(\n                        profile, \"open enhanced bookmarks page \" + (safePage + 1), () -> openBookmarks(profile, safePage + 1)));""",
        "enhanced bookmark pagination",
        1,
    )
    text = replace_required(
        text,
        "openBookmarks(profile, safePage);",
        "runEnhancedPage(profile, \"refresh enhanced bookmarks page \" + safePage, () -> openBookmarks(profile, safePage));",
        "enhanced bookmark refresh",
        1,
    )
    text = replace_required(
        text,
        """        addCommonControls(menu, profile, format, safePage, pages,\n                () -> openSearchPage(profile, input, safePage - 1, false),\n                () -> openSearchPage(profile, input, safePage + 1, false));""",
        """        addCommonControls(\n                menu,\n                profile,\n                format,\n                safePage,\n                pages,\n                () -> runEnhancedPage(\n                        profile,\n                        \"open enhanced search page \" + (safePage - 1),\n                        () -> openSearchPage(profile, input, safePage - 1, false)),\n                () -> runEnhancedPage(\n                        profile,\n                        \"open enhanced search page \" + (safePage + 1),\n                        () -> openSearchPage(profile, input, safePage + 1, false)));""",
        "enhanced search pagination",
        1,
    )
    text = replace_required(
        text,
        "openSearchPage(profile, input, safePage, false);",
        "runEnhancedPage(profile, \"refresh enhanced search page \" + safePage, () -> openSearchPage(profile, input, safePage, false));",
        "enhanced search refresh",
        1,
    )
    text = replace_required(
        text,
        "menu.addItem(slot, group.getItem(player));",
        "menu.addItem(slot, safeItemGroupIcon(profile, group, player));",
        "enhanced group icon rendering",
        1,
    )
    text = replace_required(
        text,
        "openItemGroup(profile, group, 1);",
        "SlimefunGuide.openItemGroup(profile, group, getMode(), 1);",
        "enhanced item-group click navigation",
        1,
    )
    text = replace_required(
        text,
        "lore.add(parent.getItem(player).getItemMeta().getDisplayName());",
        "lore.add(safeItemGroupName(profile, parent, player));",
        "enhanced locked parent name rendering",
        1,
    )
    text = replace_required(
        text,
        "+ group.getItem(player).getItemMeta().getDisplayName(),",
        "+ safeItemGroupName(profile, group, player),",
        "enhanced locked group name rendering",
        1,
    )
    text = replace_required(
        text,
        "openItemGroup(profile, itemGroup, page);",
        "SlimefunGuide.openItemGroup(profile, itemGroup, getMode(), page);",
        "enhanced bookmarked item-group refresh",
        1,
    )
    text = replace_required(
        text,
        "displayItem(profile, item, true);",
        "SlimefunGuide.displayItem(profile, item, true);",
        "enhanced Slimefun item display",
        1,
    )
    text = replace_required(
        text,
        "openBookmarks(profile, 1);",
        "runEnhancedPage(profile, \"open enhanced bookmarks page 1\", () -> openBookmarks(profile, 1));",
        "enhanced bookmarks entry",
        1,
    )
    text = replace_required(
        text,
        "openMainMenu(profile, history.getMainMenuPage());",
        "SlimefunGuide.openMainMenu(profile, getMode(), history.getMainMenuPage());",
        "enhanced back-to-main navigation",
        2,
    )

    helper = """
    private void runEnhancedPage(PlayerProfile profile, String operation, Runnable action) {
        GuideRuntimeGuard.run(profile, getMode(), operation, null, action);
    }

"""
    text = add_before(
        text,
        "    private void requestSearch(Player player, PlayerProfile profile)",
        helper,
        "enhanced private page guard helper",
    )
    text = text.replace(
        "public class EnhancedSurvivalSlimefunGuide extends SurvivalSlimefunGuide {",
        "public class EnhancedSurvivalSlimefunGuide extends SurvivalSlimefunGuide {\n\n    " + marker,
        1,
    )
    return text


def update(path: Path, transformer) -> bool:
    if not path.is_file():
        raise TransformError(f"Missing required source file: {path.relative_to(ROOT)}")
    original = path.read_text(encoding="utf-8")
    transformed = transformer(original)
    if transformed == original:
        print(f"Already applied: {path.relative_to(ROOT)}")
        return False
    path.write_text(transformed, encoding="utf-8")
    print(f"Updated: {path.relative_to(ROOT)}")
    return True


def main() -> int:
    try:
        changed = update(CLASSIC, transform_classic)
        changed = update(ENHANCED, transform_enhanced) or changed
    except TransformError as error:
        print(f"Phase 1B source update failed: {error}", file=sys.stderr)
        return 1

    print("Phase 1B source update complete." if changed else "Phase 1B was already applied.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
