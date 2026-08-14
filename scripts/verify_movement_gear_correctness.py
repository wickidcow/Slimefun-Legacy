#!/usr/bin/env python3
"""Verify movement-gear lifecycle, charge and stale-equipment invariants."""

from __future__ import annotations

import sys
from pathlib import Path


def read(root: Path, relative: str) -> str:
    path = root / relative
    if not path.is_file():
        raise SystemExit(f"Movement-gear correctness failed: missing file {relative}")
    return path.read_text(encoding="utf-8")


def require(text: str, needle: str, label: str) -> None:
    if needle not in text:
        raise SystemExit(f"Movement-gear correctness failed: missing {label}: {needle}")


def require_before(text: str, first: str, second: str, label: str) -> None:
    first_at = text.find(first)
    second_at = text.find(second)
    if first_at < 0 or second_at < 0 or first_at >= second_at:
        raise SystemExit(
            f"Movement-gear correctness failed: ordering violation for {label}: expected {first!r} before {second!r}"
        )


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()

    base = read(root, "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/tasks/player/AbstractPlayerTask.java")
    require(base, "!p.isOnline() || !p.isValid() || p.isDead() || !p.isSneaking()", "player-task lifecycle guard")
    require(base, "cancel();\n            return false;", "player-task invalid-state cancellation")

    jetpack = read(root, "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/tasks/player/JetpackTask.java")
    require(jetpack, "SlimefunItem.getByItem(chestplate) != jetpack", "Jetpack exact equipped-item identity guard")
    require(jetpack, "cancel();\n            return;", "Jetpack stale-equipment cancellation")
    require_before(jetpack, "SlimefunItem.getByItem(chestplate) != jetpack", "jetpack.removeItemCharge(chestplate, COST)", "Jetpack identity-before-charge ordering")
    require_before(jetpack, "jetpack.removeItemCharge(chestplate, COST)", "p.setVelocity(vector);", "Jetpack charge-before-thrust ordering")

    boots = read(root, "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/tasks/player/JetBootsTask.java")
    require(boots, "SlimefunItem.getByItem(equippedBoots) != boots", "Jet Boots exact equipped-item identity guard")
    require(boots, "cancel();\n            return;", "Jet Boots stale-equipment cancellation")
    require_before(boots, "SlimefunItem.getByItem(equippedBoots) != boots", "boots.removeItemCharge(equippedBoots, COST)", "Jet Boots identity-before-charge ordering")
    require_before(boots, "boots.removeItemCharge(equippedBoots, COST)", "p.setVelocity(vector);", "Jet Boots charge-before-thrust ordering")

    bee = read(root, "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/tasks/player/BeeWingsTask.java")
    require(bee, "wings = equipped instanceof BeeWings beeWings ? beeWings : null;", "Bee Wings task-start item capture")
    require(bee, "SlimefunItem.getByItem(chestplate) != wings", "Bee Wings exact equipped-item identity guard")
    require_before(bee, "SlimefunItem.getByItem(chestplate) != wings", "return true;", "Bee Wings identity validation before task continuation")

    parachute = read(root, "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/tasks/player/ParachuteTask.java")
    require(parachute, "parachute = equipped instanceof Parachute equippedParachute ? equippedParachute : null;", "Parachute task-start item capture")
    require(parachute, "SlimefunItem.getByItem(chestplate) != parachute", "Parachute exact equipped-item identity guard")
    require_before(parachute, "SlimefunItem.getByItem(chestplate) != parachute", "p.setVelocity(vector);", "Parachute identity-before-effect ordering")

    gadgets = read(root, "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/listeners/GadgetsListener.java")
    require(gadgets, "if (chestplate == null || !chestplate.canUse(p, true))", "gadget chestplate permission gate")
    require(gadgets, "if (boots instanceof JetBoots jetBoots && boots.canUse(p, true))", "Jet Boots permission gate")

    boots_listener = read(root, "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/listeners/SlimefunBootsListener.java")
    require(boots_listener, "SlimefunItem boots = SlimefunItem.getByItem(p.getInventory().getBoots());", "event-time boots resolution")
    require(boots_listener, "if (!boots.canUse(p, true))", "fall-protection permission gate")
    require(boots_listener, "if (boots instanceof FarmerShoes && boots.canUse(p, true))", "Farmer Shoes permission gate")

    print("Movement-gear correctness verification passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
