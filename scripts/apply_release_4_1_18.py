#!/usr/bin/env python3
"""Apply the generated portions of the Slimefun Legacy 4.1.18 release."""

from __future__ import annotations

import sys
from pathlib import Path

MARKER = "Slimefun Legacy 4.1.18 Item Doctor failure isolation."
RELEASE_HEADING = "# Slimefun Legacy 4.1.18 — Guide & Runtime Stability"


def replace_required(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise RuntimeError(f"Could not find {label}; the source layout changed")
    return text.replace(old, new, 1)


def update_item_doctor(root: Path) -> None:
    path = root / "src/main/java/io/github/thebusybiscuit/slimefun4/core/services/stability/ItemPresentationDoctor.java"
    text = path.read_text(encoding="utf-8")
    if MARKER in text:
        return

    old = """        report.stackScanned();
        boolean changed = inspectSlimefunPresentation(item, repair, report);
        if (depth < MAX_CONTAINER_DEPTH) {
            try {
                changed |= inspectNestedItems(item, repair, report, depth + 1);
            } catch (RuntimeException ex) {
                report.failure();
                Slimefun.logger().log(Level.WARNING, "Item doctor could not inspect a nested container.", ex);
            }
        }
        return changed;"""
    new = """        // Slimefun Legacy 4.1.18 Item Doctor failure isolation.
        report.stackScanned();
        boolean changed = false;
        try {
            changed = inspectSlimefunPresentation(item, repair, report);
        } catch (RuntimeException | LinkageError ex) {
            report.failure();
            Slimefun.logger().log(
                    Level.WARNING,
                    "Item doctor skipped a failing stack [" + describeStack(item) + "]. The scan will continue.",
                    ex);
        }
        if (depth < MAX_CONTAINER_DEPTH) {
            try {
                changed |= inspectNestedItems(item, repair, report, depth + 1);
            } catch (RuntimeException | LinkageError ex) {
                report.failure();
                Slimefun.logger().log(
                        Level.WARNING,
                        "Item doctor could not inspect a nested container [" + describeStack(item)
                                + "]. The scan will continue.",
                        ex);
            }
        }
        return changed;"""
    text = replace_required(text, old, new, "Item Doctor stack inspection body")

    insertion = """    private static String describeStack(ItemStack item) {
        String itemId = "<none>";
        try {
            itemId = Slimefun.getItemDataService().getItemData(item).orElse("<none>");
        } catch (RuntimeException | LinkageError ignored) {
            itemId = "<unreadable>";
        }
        return "type=" + item.getType() + ", slimefunId=" + itemId;
    }
"""
    anchor = "    private boolean inspectSlimefunPresentation(\n"
    if anchor not in text:
        raise RuntimeError("Could not find Item Doctor helper insertion point")
    text = text.replace(anchor, insertion + anchor, 1)

    text = text.replace(
        "            } catch (RuntimeException ex) {\n                report.failure();\n                report.unresolvedTemplateFound(itemId);",
        "            } catch (RuntimeException | LinkageError ex) {\n                report.failure();\n                report.unresolvedTemplateFound(itemId);",
        1,
    )
    text = text.replace(
        "        } catch (RuntimeException ex) {\n            report.failure();\n            try {",
        "        } catch (RuntimeException | LinkageError ex) {\n            report.failure();\n            try {",
        1,
    )
    text = text.replace(
        "            } catch (RuntimeException rollbackError) {",
        "            } catch (RuntimeException | LinkageError rollbackError) {",
        1,
    )

    # The 4.1.17 branch already contains this STAFF_ELEMENTAL_STORM-safe fallback.
    required_fallback = """                    if (usesLeft == null) {
                        usesLeft = limitedUseItem.getMaxUseCount();
                    }"""
    if required_fallback not in text:
        legacy = """                var storedUses = limitedUseItem.getStoredUses(item);
                usesLeft = storedUses.isPresent()
                        ? storedUses.getAsInt()
                        : ItemDoctorText.findLegacyUsesLeft(lore);"""
        replacement = """                var storedUses = limitedUseItem.getStoredUses(item);
                if (storedUses.isPresent()) {
                    usesLeft = storedUses.getAsInt();
                } else {
                    usesLeft = ItemDoctorText.findLegacyUsesLeft(lore);
                    if (usesLeft == null) {
                        usesLeft = limitedUseItem.getMaxUseCount();
                    }
                }"""
        text = replace_required(text, legacy, replacement, "limited-use fallback")

    path.write_text(text, encoding="utf-8", newline="\n")


def update_changelog(root: Path) -> None:
    path = root / "EVERYTHING_THAT_CHANGED.md"
    text = path.read_text(encoding="utf-8")
    if RELEASE_HEADING in text:
        return
    section = """# Slimefun Legacy 4.1.18 — Guide & Runtime Stability
- Added guarded classic, enhanced, nested, search, bookmark, history, and addon `FlexItemGroup` guide dispatch.
- Blocks recursive or broken addon guide menus without allowing one category to freeze or disconnect a player.
- Adds slow-menu timing, addon/category ownership, player context, suppressed-warning counts, and periodic guide runtime summaries.
- Hardened the Item Doctor so one malformed stack or addon linkage failure is counted, logged, skipped, and cannot terminate the full scan.
- Preserves limited-use state by falling back to the registered maximum when an old stack has neither stored-use data nor readable legacy lore.
- Hardened Auto Enchanter and Auto Disenchanter operations with transactional input consumption, output-capacity checks, minimum processing times, and visible blocked-state diagnostics.
- Preserves the optional AdvancedEnchantments bridge for applying and extracting supported custom enchantments without a compile-time dependency.
- Cancelled or failed enchantment events now leave machine inputs untouched instead of moving or consuming items.
- Added static release verification and a final Java 25 / Java 21-bytecode release workflow.
- No item IDs, database schemas, saved block formats, or backpack formats were changed.
"""
    path.write_text(section + text, encoding="utf-8", newline="\n")


def update_readme(root: Path) -> None:
    path = root / "README.md"
    text = path.read_text(encoding="utf-8")
    text = text.replace("Slimefun Legacy 4.1.17 is tested primarily", "Slimefun Legacy 4.1.18 is tested primarily")
    if "Guide runtime isolation with slow-menu and addon ownership diagnostics" not in text:
        anchor = "- Native Enhanced Guide with smart search, bookmarks, safe recipe preparation, and universal machine recipe browsing\n"
        addition = anchor + "- Guide runtime isolation with slow-menu and addon ownership diagnostics\n"
        if anchor in text:
            text = text.replace(anchor, addition, 1)
    path.write_text(text, encoding="utf-8", newline="\n")


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
    update_item_doctor(root)
    update_changelog(root)
    update_readme(root)
    print("Slimefun Legacy 4.1.18 generated release changes applied.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
