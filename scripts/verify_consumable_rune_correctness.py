#!/usr/bin/env python3
"""Verify medical consumables and delayed rune transactions."""

from __future__ import annotations

import sys
from pathlib import Path


def read(root: Path, relative: str) -> str:
    path = root / relative
    if not path.is_file():
        raise SystemExit(f"Consumable/rune correctness failed: missing file {relative}")
    return path.read_text(encoding="utf-8")


def require(text: str, needle: str, label: str) -> None:
    if needle not in text:
        raise SystemExit(f"Consumable/rune correctness failed: missing {label}: {needle}")


def forbid(text: str, needle: str, label: str) -> None:
    if needle in text:
        raise SystemExit(f"Consumable/rune correctness failed: forbidden {label}: {needle}")


def require_before(text: str, first: str, second: str, label: str) -> None:
    first_at = text.find(first)
    second_at = text.find(second)
    if first_at < 0 or second_at < 0 or first_at >= second_at:
        raise SystemExit(
            f"Consumable/rune correctness failed: ordering violation for {label}: expected {first!r} before {second!r}"
        )


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()

    splint = read(root, "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/medical/Splint.java")
    require(
        splint,
        "if (p.getHealth() >= p.getAttribute(AttributeX.MAX_HEALTH).getValue())",
        "Splint full-health guard",
    )
    forbid(splint, "p.getFireTicks() <= 0", "Splint fire-based eligibility")
    require_before(
        splint,
        "if (p.getHealth() >= p.getAttribute(AttributeX.MAX_HEALTH).getValue())",
        "ItemUtils.consumeItem(e.getItem(), false);",
        "Splint need-before-consume ordering",
    )

    enchantment = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/magical/runes/EnchantmentRune.java",
    )
    require(enchantment, "ItemStack liveTarget = item.getItemStack();", "Enchantment Rune live-target refresh")
    require(enchantment, "ItemStack enchanted = liveTarget.clone();", "Enchantment Rune output preparation clone")
    require(enchantment, "enchanted.addEnchantment(enchantment, level);", "Enchantment Rune pre-commit enchantment")
    require_before(enchantment, "enchanted.addEnchantment(enchantment, level);", "item.remove();", "Enchantment Rune prepare-before-remove ordering")
    require_before(enchantment, "item.remove();", "consumeOneRune(rune);", "Enchantment Rune target-before-rune commit ordering")
    require(enchantment, "remaining.setAmount(liveRune.getAmount() - 1);", "Enchantment Rune exact one-rune decrement")
    forbid(enchantment, "l.getWorld().dropItemNaturally(l, runeStack);", "Enchantment Rune duplicate refund path")

    soulbound = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/magical/runes/SoulboundRune.java",
    )
    require(soulbound, "ItemStack liveTarget = item.getItemStack();", "Soulbound Rune live-target refresh")
    require(soulbound, "ItemStack soulbound = liveTarget.clone();", "Soulbound Rune output preparation clone")
    require(soulbound, "SlimefunUtils.setSoulbound(soulbound, true);", "Soulbound Rune pre-commit binding")
    require_before(soulbound, "SlimefunUtils.setSoulbound(soulbound, true);", "item.remove();", "Soulbound Rune prepare-before-remove ordering")
    require_before(soulbound, "item.remove();", "consumeOneRune(rune);", "Soulbound Rune target-before-rune commit ordering")
    require(soulbound, "remaining.setAmount(liveRune.getAmount() - 1);", "Soulbound Rune exact one-rune decrement")
    forbid(soulbound, "item.remove();\n                                rune.remove();", "Soulbound Rune whole-stack consumption")

    villager = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/magical/runes/VillagerRune.java",
    )
    require(villager, "Interaction.INTERACT_ENTITY", "Villager Rune protection check")
    require(villager, "villager.getProfession() == Profession.NONE || villager.getProfession() == Profession.NITWIT", "Villager Rune invalid-target guard")

    print("Consumable/rune correctness verification passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
