#!/usr/bin/env python3
"""Verify food consumption and Cooler transaction invariants."""

from __future__ import annotations

import sys
from pathlib import Path


def read(root: Path, relative: str) -> str:
    path = root / relative
    if not path.is_file():
        raise SystemExit(f"Food correctness failed: missing file {relative}")
    return path.read_text(encoding="utf-8")


def require(text: str, needle: str, label: str) -> None:
    if needle not in text:
        raise SystemExit(f"Food correctness failed: missing {label}: {needle}")


def require_before(text: str, first: str, second: str, label: str) -> None:
    first_at = text.find(first)
    second_at = text.find(second)
    if first_at < 0 or second_at < 0 or first_at >= second_at:
        raise SystemExit(
            f"Food correctness failed: ordering violation for {label}: expected {first!r} before {second!r}"
        )


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()

    consume_listener = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/listeners/SlimefunItemConsumeListener.java",
    )
    require(
        consume_listener,
        "@EventHandler(ignoreCancelled = true)\n    public void onConsume(PlayerItemConsumeEvent e)",
        "cancelled consume-event filter",
    )
    require(
        consume_listener,
        "if (sfItem.canUse(p, true))",
        "consumed Slimefun item canUse guard",
    )

    cooler = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/listeners/CoolerListener.java",
    )
    if cooler.count("@EventHandler(ignoreCancelled = true)") < 2:
        raise SystemExit("Food correctness failed: Cooler hunger and starvation handlers must ignore cancellation")
    require(cooler, "List<ItemStack> coolers = new ArrayList<>();", "Cooler candidate snapshot")
    require(cooler, "tryConsumeFromCoolers(p, coolers, 0);", "Cooler sequential consumption start")
    require(
        cooler,
        "PlayerBackpack.getAsync(coolerItem).whenComplete((backpack, error) -> Slimefun.runSyncFor(p, () -> {",
        "Cooler async load returned to player-owned thread",
    )
    require(cooler, "ItemStack currentCooler = findCurrentCooler(p, coolerItem);", "Cooler live-item revalidation")
    require(
        cooler,
        "if (currentCooler == null || !cooler.canUse(p, false))",
        "Cooler live-item and permission guard",
    )
    require(cooler, "PlayerBackpack.migrateLegacyItem(currentCooler, backpack);", "Cooler legacy identity migration")
    require(
        cooler,
        "if (!consumeJuice(p, currentCooler, backpack)) {\n                tryConsumeFromCoolers(p, coolers, index + 1);",
        "Cooler fallthrough only after failed consumption",
    )
    require_before(
        cooler,
        "ItemStack currentCooler = findCurrentCooler(p, coolerItem);",
        "PlayerBackpack.migrateLegacyItem(currentCooler, backpack);",
        "Cooler live-item validation before identity migration",
    )
    require_before(
        cooler,
        "if (!event.isCancelled())",
        "p.addPotionEffect(effect);",
        "Cooler activation-event cancellation before effects",
    )
    require_before(
        cooler,
        "inv.setItem(slot, null);",
        "saveBackpackInventory(backpack);",
        "Cooler remove-before-persist ordering",
    )

    juice = read(root, "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/food/Juice.java")
    require(juice, "ItemConsumptionHandler getItemHandler()", "Juice consumption handler")
    require(juice, "removeGlassBottle(p, item);", "Juice vanilla bottle cleanup")

    diet_cookie = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/food/DietCookie.java",
    )
    require(diet_cookie, "p.addPotionEffect", "Diet Cookie consume effect")

    print("Food correctness verification passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
