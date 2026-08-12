#!/usr/bin/env python3
"""Verify player-activated tool invariants that bypass normal machine transactions."""

from __future__ import annotations

import sys
from pathlib import Path


def read(root: Path, relative: str) -> str:
    path = root / relative
    if not path.is_file():
        raise SystemExit(f"Tool correctness failed: missing file {relative}")
    return path.read_text(encoding="utf-8")


def require(text: str, needle: str, label: str) -> None:
    if needle not in text:
        raise SystemExit(f"Tool correctness failed: missing {label}: {needle}")


def forbid_before(text: str, forbidden: str, boundary: str, label: str) -> None:
    boundary_at = text.find(boundary)
    if boundary_at < 0:
        raise SystemExit(f"Tool correctness failed: missing boundary for {label}: {boundary}")
    forbidden_at = text.find(forbidden)
    if 0 <= forbidden_at < boundary_at:
        raise SystemExit(f"Tool correctness failed: {label} occurs before commit boundary")


def require_after(text: str, needle: str, boundary: str, label: str) -> None:
    boundary_at = text.find(boundary)
    needle_at = text.find(needle, boundary_at + len(boundary)) if boundary_at >= 0 else -1
    if boundary_at < 0 or needle_at < 0:
        raise SystemExit(f"Tool correctness failed: missing post-boundary {label}: {needle}")


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()

    explosive = read(root, "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/tools/ExplosiveTool.java")
    event_boundary = "if (!event.isCancelled()) {"
    custom_remove = "CustomBlock.remove(block.getLocation());"
    custom_loot = "drops.addAll(CustomBlock.byAlreadyPlaced(block).getLoot());"

    require(explosive, "ExplosiveToolBreakBlocksEvent event = new ExplosiveToolBreakBlocksEvent", "explosive-tool cancellable event")
    forbid_before(explosive, custom_remove, event_boundary, "ItemsAdder custom-block removal")
    forbid_before(explosive, custom_loot, event_boundary, "ItemsAdder custom-block loot mutation")
    require_after(explosive, custom_remove, event_boundary, "ItemsAdder custom-block removal")
    require_after(explosive, custom_loot, event_boundary, "ItemsAdder custom-block loot capture")
    require(explosive, "Slimefun.getProtectionManager().hasPermission(p, b.getLocation(), Interaction.BREAK_BLOCK)", "explosive-tool protection check")
    require(explosive, "SlimefunTag.UNBREAKABLE_MATERIALS.isTagged(b.getType())", "explosive-tool unbreakable-material guard")
    require(explosive, "b.getWorld().getWorldBorder().isInside(b.getLocation())", "explosive-tool world-border guard")

    lumber = read(root, "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/tools/LumberAxe.java")
    require(lumber, "!StorageCacheUtils.hasSlimefunBlock(b.getLocation())", "Lumber Axe Slimefun-block exclusion")
    require(lumber, "Interaction.BREAK_BLOCK", "Lumber Axe protection check")
    require(lumber, "private static final int MAX_BROKEN = 100;", "Lumber Axe bounded vein size")

    containment = read(root, "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/tools/PickaxeOfContainment.java")
    require(containment, "if (item instanceof RepairedSpawner)", "repaired-spawner preservation")
    require(containment, "else if (item == null)", "vanilla-spawner conversion")
    require(containment, "return null;", "addon spawner-machine exclusion")
    require(containment, "e.setExpToDrop(0);", "contained-spawner XP suppression")
    require(containment, "e.setDropItems(false);", "contained-spawner vanilla-drop suppression")

    grappling = read(root, "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/tools/GrapplingHook.java")
    require(grappling, "!Slimefun.getGrapplingHookListener().isGrappling(uuid)", "single active Grappling Hook guard")
    require(grappling, "if (p.getInventory().getItemInOffHand().getType() == Material.BOW)", "Grappling Hook offhand bow dupe guard")
    require(grappling, "ItemUtils.consumeItem(item, false);", "Grappling Hook configured consumption")

    print("Player tool correctness verification passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
