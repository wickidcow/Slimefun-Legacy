#!/usr/bin/env python3
"""Verify melee and ranged weapon event, damage, and projectile invariants."""

from __future__ import annotations

import sys
from pathlib import Path


def read(root: Path, relative: str) -> str:
    path = root / relative
    if not path.is_file():
        raise SystemExit(f"Weapon correctness failed: missing file {relative}")
    return path.read_text(encoding="utf-8")


def require(text: str, needle: str, label: str) -> None:
    if needle not in text:
        raise SystemExit(f"Weapon correctness failed: missing {label}: {needle}")


def forbid(text: str, needle: str, label: str) -> None:
    if needle in text:
        raise SystemExit(f"Weapon correctness failed: forbidden {label}: {needle}")


def require_before(text: str, first: str, second: str, label: str) -> None:
    first_at = text.find(first)
    second_at = text.find(second)
    if first_at < 0 or second_at < 0 or first_at >= second_at:
        raise SystemExit(
            f"Weapon correctness failed: ordering violation for {label}: expected {first!r} before {second!r}"
        )


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()

    hit_listener = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/listeners/SlimefunItemHitListener.java",
    )
    require(
        hit_listener,
        "@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)",
        "melee cancelled-hit filter",
    )
    require(
        hit_listener,
        "if (sfItem != null && sfItem.canUse(p, true))",
        "melee weapon canUse guard",
    )

    bow_listener = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/listeners/SlimefunBowListener.java",
    )
    require(bow_listener, "@EventHandler(ignoreCancelled = true)\n    public void onBowUse", "cancelled bow-shot filter")
    require(
        bow_listener,
        "if (bow instanceof SlimefunBow slimefunBow && slimefunBow.canUse(player, true))",
        "bow canUse guard",
    )
    require(
        bow_listener,
        "if (!e.isCancelled() && bow != null)",
        "cancelled projectile-damage filter",
    )
    require(
        bow_listener,
        "Slimefun.getSchedulerService()",
        "projectile-hit cleanup scheduler",
    )

    explosive = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/weapons/ExplosiveBow.java",
    )
    require(explosive, "int radius = range.getValue();", "Explosive Bow stable radius snapshot")
    require(
        explosive,
        "if (damage <= 0 || entity.getUniqueId().equals(target.getUniqueId()))",
        "Explosive Bow non-positive/direct-target AoE guard",
    )
    require_before(
        explosive,
        "if (damage <= 0 || entity.getUniqueId().equals(target.getUniqueId()))",
        "boolean damaged = DamageUtils.damage",
        "Explosive Bow validate-before-damage ordering",
    )
    require_before(
        explosive,
        "boolean damaged = DamageUtils.damage",
        "if (damaged)",
        "Explosive Bow damage-before-knockback ordering",
    )

    icy = read(root, "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/weapons/IcyBow.java")
    require(
        icy,
        "if (player.isBlocking() && e.getFinalDamage() <= 0)",
        "Icy Bow blocked-hit effect guard",
    )

    vampire = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/weapons/VampireBlade.java",
    )
    require(vampire, "if (e.getFinalDamage() <= 0)", "Vampire Blade zero-damage guard")
    require_before(
        vampire,
        "if (e.getFinalDamage() <= 0)",
        "ThreadLocalRandom.current().nextInt(100) < getChance()",
        "Vampire Blade damage-before-heal-roll ordering",
    )

    seismic = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/weapons/SeismicAxe.java",
    )
    require(seismic, "boolean damaged = DamageUtils.damage", "Seismic Axe attributed damage result")
    require_before(
        seismic,
        "boolean damaged = DamageUtils.damage",
        "if (damaged)",
        "Seismic Axe damage-before-knockback ordering",
    )

    beheading = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/weapons/SwordOfBeheading.java",
    )
    require(
        beheading,
        "random.nextInt(100) < chanceZombie.getValue()",
        "Sword of Beheading exact percentage comparison",
    )
    forbid(
        beheading,
        "random.nextInt(100) <= chanceZombie.getValue()",
        "Sword of Beheading off-by-one percentage comparison",
    )

    print("Weapon correctness verification passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
