#!/usr/bin/env python3
"""Verify entity-interaction and small seasonal/misc item transaction invariants."""

from __future__ import annotations

import sys
from pathlib import Path


def read(root: Path, relative: str) -> str:
    path = root / relative
    if not path.is_file():
        raise SystemExit(f"Interaction/misc correctness failed: missing file {relative}")
    return path.read_text(encoding="utf-8")


def require(text: str, needle: str, label: str) -> None:
    if needle not in text:
        raise SystemExit(f"Interaction/misc correctness failed: missing {label}: {needle}")


def require_before(text: str, first: str, second: str, label: str) -> None:
    first_at = text.find(first)
    second_at = text.find(second)
    if first_at < 0 or second_at < 0 or first_at >= second_at:
        raise SystemExit(
            f"Interaction/misc correctness failed: ordering violation for {label}: expected {first!r} before {second!r}"
        )


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()

    entity_listener = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/listeners/entity/EntityInteractionListener.java",
    )
    require(
        entity_listener,
        "@EventHandler(ignoreCancelled = true)\n    public void onInteractEntity(PlayerInteractEntityEvent e)",
        "cancelled entity-interaction filter",
    )
    require(entity_listener, "if (sfItem.canUse(e.getPlayer(), true))", "entity item canUse guard")

    goo = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/misc/StrangeNetherGoo.java",
    )
    require(goo, "Interaction.INTERACT_ENTITY", "Strange Nether Goo protection type")
    require(
        goo,
        ".hasPermission(e.getPlayer(), sheep.getLocation(), Interaction.INTERACT_ENTITY)",
        "Strange Nether Goo entity-protection check",
    )
    require_before(
        goo,
        ".hasPermission(e.getPlayer(), sheep.getLocation(), Interaction.INTERACT_ENTITY)",
        "ItemUtils.consumeItem(item, false);",
        "Strange Nether Goo protection-before-consumption ordering",
    )

    christmas = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/seasonal/ChristmasPresent.java",
    )
    require(christmas, "if (gifts.length == 0)", "Christmas Present empty gift-pool guard")
    require_before(
        christmas,
        "if (gifts.length == 0)",
        "ItemUtils.consumeItem(e.getItem(), false);",
        "Christmas Present gift validation before consumption",
    )

    easter = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/seasonal/EasterEgg.java",
    )
    require(easter, "if (gifts.length == 0)", "Easter Egg empty gift-pool guard")
    require_before(
        easter,
        "if (gifts.length == 0)",
        "ItemUtils.consumeItem(e.getItem(), false);",
        "Easter Egg gift validation before consumption",
    )

    print("Interaction/misc correctness verification passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
