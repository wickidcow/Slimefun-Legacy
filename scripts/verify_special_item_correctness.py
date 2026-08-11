#!/usr/bin/env python3
"""Verify special-item runtime invariants that are easy to regress silently."""

from __future__ import annotations

import sys
from pathlib import Path


def read(root: Path, relative: str) -> str:
    path = root / relative
    if not path.is_file():
        raise SystemExit(f"Special-item correctness failed: missing file {relative}")
    return path.read_text(encoding="utf-8")


def require(text: str, needle: str, label: str) -> None:
    if needle not in text:
        raise SystemExit(f"Special-item correctness failed: missing {label}: {needle}")


def forbid(text: str, needle: str, label: str) -> None:
    if needle in text:
        raise SystemExit(f"Special-item correctness failed: forbidden {label}: {needle}")


def require_before(text: str, first: str, second: str, label: str) -> None:
    first_at = text.find(first)
    second_at = text.find(second)
    if first_at < 0 or second_at < 0 or first_at >= second_at:
        raise SystemExit(
            f"Special-item correctness failed: ordering violation for {label}: "
            f"expected {first!r} before {second!r}"
        )


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()

    gps = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/gps/GPSTransmitter.java",
    )
    require(gps, "private UUID parseOwner(@Nullable String value)", "safe GPS owner parser")
    require(gps, "if (value == null || value.isBlank())", "missing GPS owner handling")
    require(gps, "catch (IllegalArgumentException ignored)", "malformed GPS owner handling")
    require(gps, "if (owner == null) {\n                    return;", "invalid GPS owner tick guard")
    require(gps, "public boolean isSynchronized()", "GPS ticker synchronization declaration")
    require(gps, "return true;", "synchronized GPS transmitter ticker")
    forbid(
        gps,
        "UUID owner = UUID.fromString(data.getData(\"owner\"));",
        "raw GPS ticker owner parsing",
    )

    fisherman = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/androids/FishermanAndroid.java",
    )
    require(
        fisherman,
        "ItemStack remainder = menu.pushItem(drop, getOutputSlots());",
        "Fisherman Android overflow capture",
    )
    require(
        fisherman,
        "water.getWorld().dropItemNaturally(water.getLocation(), remainder);",
        "Fisherman Android overflow preservation",
    )
    require_before(
        fisherman,
        "ItemStack remainder = menu.pushItem(drop, getOutputSlots());",
        "water.getWorld().dropItemNaturally(water.getLocation(), remainder);",
        "Fisherman Android push-before-overflow-drop",
    )

    elevator = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/elevator/ElevatorPlate.java",
    )
    require(
        elevator,
        "int pages = Math.max(1, (floors.size() + GUI_SIZE - 1) / GUI_SIZE);",
        "Elevator exact page-count calculation",
    )
    forbid(elevator, "int pages = 1 + (floors.size() / GUI_SIZE);", "blank Elevator page calculation")
    require(
        elevator,
        "player.teleportAsync(destination).whenComplete((teleported, error) -> {",
        "Elevator failed-teleport completion handling",
    )
    require(
        elevator,
        "if (error != null || !Boolean.TRUE.equals(teleported)) {\n                    users.remove(uuid);",
        "Elevator teleport marker cleanup on failure",
    )

    teleporter = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/api/gps/TeleportationManager.java",
    )
    require(
        teleporter,
        "Validate.notNull(destination, \"Destination cannot be null\");",
        "Teleporter destination validation",
    )
    forbid(
        teleporter,
        "Validate.notNull(source, \"Destination cannot be null\");",
        "duplicated Teleporter source validation",
    )

    fluid_pump = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/electric/machines/FluidPump.java",
    )
    require(
        fluid_pump,
        "SlimefunUtils.isItemSimilar(itemInSlot, emptyBottle, true, false)\n                        && (fluid.getType() == Material.WATER || fluid.getType() == Material.BUBBLE_COLUMN)",
        "Fluid Pump water-only bottle input",
    )

    assembler = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/electric/machines/entities/AbstractEntityAssembler.java",
    )
    require(
        assembler,
        "public boolean isSynchronized() {\n                return true;\n            }",
        "synchronized entity assembler ticker",
    )

    smeltery = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/electric/machines/ElectricSmeltery.java",
    )
    require(smeltery, "List<Integer> emptySlots = new LinkedList<>();", "Electric Smeltery empty-slot tracking")
    require(
        smeltery,
        "if (!matchingSlots.isEmpty()) {\n                    Collections.sort(matchingSlots, compareSlots(menu));\n                    return toSlotArray(matchingSlots);\n                }\n\n                return toSlotArray(emptySlots);",
        "Electric Smeltery matching-stack then empty-slot cargo fallback",
    )
    forbid(
        smeltery,
        "else if (fullSlots == slots.size())",
        "Electric Smeltery false-full cargo short circuit",
    )

    research = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/api/researches/Research.java",
    )
    require(
        research,
        "setCurrencyCost(Slimefun.getResearchCfg().getDouble(path + \".currency-cost\"));",
        "decimal Vault research cost loading",
    )
    forbid(
        research,
        "setCurrencyCost(Slimefun.getResearchCfg().getInt(path + \".currency-cost\"));",
        "integer-truncated Vault research cost loading",
    )

    print("Special-item runtime correctness verification passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
