#!/usr/bin/env python3
"""Verify passive armor protection against stale cached equipment."""

from __future__ import annotations

import sys
from pathlib import Path


def read(root: Path, relative: str) -> str:
    path = root / relative
    if not path.is_file():
        raise SystemExit(f"Armor correctness failed: missing file {relative}")
    return path.read_text(encoding="utf-8")


def require(text: str, needle: str, label: str) -> None:
    if needle not in text:
        raise SystemExit(f"Armor correctness failed: missing {label}: {needle}")


def forbid(text: str, needle: str, label: str) -> None:
    if needle in text:
        raise SystemExit(f"Armor correctness failed: forbidden {label}: {needle}")


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()

    util = read(root, "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/armor/ArmorProtectionUtils.java")
    require(util, "player.getInventory().getArmorContents()", "current-equipment armor scan")
    require(util, "SlimefunItem.getByItem(stack)", "current Slimefun armor resolution")
    require(util, "item instanceof ProtectiveArmor protectiveArmor", "protective armor attribute check")
    require(util, "if (!protectiveArmor.isFullSetRequired())", "single-piece protection support")
    require(util, "setId == null || setId.equals(armorSetId)", "same-set full armor validation")
    require(util, "return armorCount == 4;", "four-piece full-set requirement")

    radiation = read(root, "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/tasks/armor/RadiationTask.java")
    require(
        radiation,
        "ArmorProtectionUtils.hasFullProtectionAgainst(p, ProtectionType.RADIATION)",
        "fresh radiation armor check",
    )
    forbid(radiation, "profile.hasFullProtectionAgainst(ProtectionType.RADIATION)", "cached radiation armor check")

    bees = read(root, "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/listeners/entity/BeeListener.java")
    require(
        bees,
        "ArmorProtectionUtils.hasFullProtectionAgainst(p, ProtectionType.BEES)",
        "fresh bee armor check",
    )
    forbid(bees, "profile.hasFullProtectionAgainst(ProtectionType.BEES)", "cached bee armor check")

    elytra = read(root, "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/listeners/ElytraImpactListener.java")
    require(elytra, "ItemStack helmetStack = p.getInventory().getHelmet();", "current Elytra Cap helmet lookup")
    require(elytra, "SlimefunItem helmetItem = SlimefunItem.getByItem(helmetStack);", "current Elytra Cap item resolution")
    require(
        elytra,
        "ArmorProtectionUtils.hasFullProtectionAgainst(p, ProtectionType.FLYING_INTO_WALL)",
        "fresh Elytra impact armor check",
    )
    require(elytra, "damageableItem.damageItem(p, helmetStack);", "damage current protected helmet")
    forbid(elytra, "profile.getArmor()[3].getItem()", "cached Elytra Cap helmet lookup")

    config = read(root, "src/main/resources/config.yml")
    require(config, "armor-update-interval: 10", "documented periodic armor cache interval")

    print("Armor correctness verification passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
