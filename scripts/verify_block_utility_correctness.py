#!/usr/bin/env python3
"""Verify block utility ownership, persistence, and output safety invariants."""

from __future__ import annotations

import sys
from pathlib import Path


def read(root: Path, relative: str) -> str:
    path = root / relative
    if not path.is_file():
        raise SystemExit(f"Block-utility correctness failed: missing file {relative}")
    return path.read_text(encoding="utf-8")


def require(text: str, needle: str, label: str) -> None:
    if needle not in text:
        raise SystemExit(f"Block-utility correctness failed: missing {label}: {needle}")


def forbid(text: str, needle: str, label: str) -> None:
    if needle in text:
        raise SystemExit(f"Block-utility correctness failed: forbidden {label}: {needle}")


def require_before(text: str, first: str, second: str, label: str) -> None:
    first_at = text.find(first)
    second_at = text.find(second)
    if first_at < 0 or second_at < 0 or first_at >= second_at:
        raise SystemExit(
            f"Block-utility correctness failed: ordering violation for {label}: expected {first!r} before {second!r}"
        )


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()

    placer = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/blocks/BlockPlacer.java",
    )
    require(placer, "if (owner == null)", "Block Placer legacy ownerless fallback")
    require(placer, "if (owner.isBlank())", "Block Placer blank-owner fail-closed guard")
    require(placer, "catch (IllegalArgumentException ignored)", "Block Placer malformed-owner recovery")
    require(
        placer,
        "hasPermission(player, target, Interaction.PLACE_BLOCK)",
        "Block Placer protection-manager check",
    )
    require(
        placer,
        "if (inv.removeItem(removedItem).isEmpty())",
        "Block Placer item removal commit check",
    )
    require_before(
        placer,
        "if (inv.removeItem(removedItem).isEmpty())",
        "runnable.run();",
        "Block Placer consume-before-placement commit",
    )

    hologram = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/blocks/HologramProjector.java",
    )
    require(hologram, "private static final double DEFAULT_OFFSET = 0.5D;", "Hologram default offset")
    require(hologram, "private static double readOffset(@Nonnull Block projector)", "Hologram safe offset reader")
    require(hologram, "if (Double.isFinite(offset))", "Hologram non-finite offset rejection")
    require(hologram, "catch (NumberFormatException ignored)", "Hologram malformed offset recovery")
    require(
        hologram,
        "StorageCacheUtils.setData(projector.getLocation(), OFFSET_PARAMETER, String.valueOf(DEFAULT_OFFSET));",
        "Hologram invalid offset repair",
    )
    forbid(
        hologram,
        "Double.parseDouble(blockData.getData(OFFSET_PARAMETER))",
        "raw Hologram block-data offset parsing",
    )

    output_chest = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/blocks/OutputChest.java",
    )
    require(output_chest, "!slimefunItem.isDisabledIn(b.getWorld())", "enabled Output Chest requirement")
    require(
        output_chest,
        "Slimefun.getItemStackService().fits(inv, item, InventoryContext.OUTPUT_CHEST)",
        "Output Chest final item fit preflight",
    )

    furnace = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/blocks/EnhancedFurnace.java",
    )
    require(furnace, "public boolean isSynchronized()", "Enhanced Furnace synchronized ticker")
    require(furnace, "furnace.getCookTime() > 0", "Enhanced Furnace active-cook guard")

    spawner = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/blocks/AbstractMonsterSpawner.java",
    )
    require(spawner, "catch (IllegalArgumentException ignored)", "Spawner malformed legacy entity-type recovery")
    require(spawner, "if (type != null && type.isSpawnable()", "Spawner spawnable BlockState guard")

    print("Block-utility correctness verification passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
