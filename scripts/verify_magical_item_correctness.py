#!/usr/bin/env python3
"""Verify player-facing magical item transaction and corruption-safety invariants."""

from __future__ import annotations

import sys
from pathlib import Path


def read(root: Path, relative: str) -> str:
    path = root / relative
    if not path.is_file():
        raise SystemExit(f"Magical-item correctness failed: missing file {relative}")
    return path.read_text(encoding="utf-8")


def require(text: str, needle: str, label: str) -> None:
    if needle not in text:
        raise SystemExit(f"Magical-item correctness failed: missing {label}: {needle}")


def forbid(text: str, needle: str, label: str) -> None:
    if needle in text:
        raise SystemExit(f"Magical-item correctness failed: forbidden {label}: {needle}")


def require_before(text: str, first: str, second: str, label: str) -> None:
    first_at = text.find(first)
    second_at = text.find(second)
    if first_at < 0 or second_at < 0 or first_at >= second_at:
        raise SystemExit(
            f"Magical-item correctness failed: ordering violation for {label}: expected {first!r} before {second!r}"
        )


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()

    tome = read(root, "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/magical/KnowledgeTome.java")
    require(tome, "if (lore == null || lore.size() < 2 || lore.get(1) == null)", "Knowledge Tome lore corruption guard")
    require(tome, "if (serializedOwner == null || serializedOwner.isBlank())", "Knowledge Tome blank owner guard")
    require(tome, "catch (IllegalArgumentException ignored)", "Knowledge Tome malformed UUID guard")
    require(tome, "ItemStack singleTome = item.clone();", "Knowledge Tome one-item transaction snapshot")
    require(tome, "singleTome.setAmount(1);", "Knowledge Tome exact one-item consumption")
    require(
        tome,
        "&& !p.getInventory().removeItem(singleTome).isEmpty())",
        "Knowledge Tome commit-before-research transfer",
    )
    require_before(
        tome,
        "&& !p.getInventory().removeItem(singleTome).isEmpty())",
        "for (Research research : owner.getResearches())",
        "Knowledge Tome consumption before research grant",
    )
    forbid(tome, "ItemUtils.consumeItem(item, false);", "Knowledge Tome eager consumption")

    flask = read(root, "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/magical/KnowledgeFlask.java")
    require(flask, "p.setLevel(p.getLevel() - 1);", "Knowledge Flask level payment")
    require(flask, "if (!p.getInventory().addItem(item).isEmpty())", "Knowledge Flask inventory overflow handling")
    require(flask, "p.getWorld().dropItemNaturally(p.getLocation(), item);", "Knowledge Flask overflow preservation")
    require_before(flask, "p.setLevel(p.getLevel() - 1);", "ItemUtils.consumeItem(e.getItem(), false);", "Knowledge Flask successful transaction ordering")

    bonemeal = read(root, "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/magical/InfernalBonemeal.java")
    require(bonemeal, "if (ageable.getAge() < ageable.getMaximumAge())", "Infernal Bonemeal maturity guard")
    require_before(bonemeal, "b.setBlockData(ageable);", "ItemUtils.consumeItem(e.getItem(), false);", "Infernal Bonemeal grow-before-consume ordering")

    pills = read(root, "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/magical/MagicalZombiePills.java")
    require(pills, "Interaction.INTERACT_ENTITY", "Magical Zombie Pills entity-protection check")
    require(pills, "if (entity instanceof ZombieVillager zombieVillager)", "Magical Zombie Pills villager conversion")
    require(pills, "else if (entity instanceof PigZombie pigZombie)", "Magical Zombie Pills piglin conversion")

    print("Magical-item correctness verification passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
