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

    gps = read(root, "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/gps/GPSTransmitter.java")
    require(gps, "private UUID parseOwner(@Nullable String value)", "safe GPS owner parser")
    require(gps, "if (value == null || value.isBlank())", "missing GPS owner handling")
    require(gps, "catch (IllegalArgumentException ignored)", "malformed GPS owner handling")
    require(gps, "if (owner == null) {\n                    return;", "invalid GPS owner tick guard")
    require(gps, "public boolean isSynchronized()", "GPS ticker synchronization declaration")
    require(gps, "return true;", "synchronized GPS transmitter ticker")
    forbid(gps, "UUID owner = UUID.fromString(data.getData(\"owner\"));", "raw GPS ticker owner parsing")

    fisherman = read(root, "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/androids/FishermanAndroid.java")
    require(fisherman, "ItemStack remainder = menu.pushItem(drop, getOutputSlots());", "Fisherman Android overflow capture")
    require(fisherman, "water.getWorld().dropItemNaturally(water.getLocation(), remainder);", "Fisherman Android overflow preservation")
    require_before(
        fisherman,
        "ItemStack remainder = menu.pushItem(drop, getOutputSlots());",
        "water.getWorld().dropItemNaturally(water.getLocation(), remainder);",
        "Fisherman Android push-before-overflow-drop",
    )

    elevator = read(root, "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/elevator/ElevatorPlate.java")
    require(elevator, "int pages = Math.max(1, (floors.size() + GUI_SIZE - 1) / GUI_SIZE);", "Elevator exact page-count calculation")
    forbid(elevator, "int pages = 1 + (floors.size() / GUI_SIZE);", "blank Elevator page calculation")
    require(elevator, "player.teleportAsync(destination).whenComplete((teleported, error) -> {", "Elevator failed-teleport completion handling")
    require(elevator, "if (error != null || !Boolean.TRUE.equals(teleported)) {\n                    users.remove(uuid);", "Elevator teleport marker cleanup on failure")

    teleporter = read(root, "src/main/java/io/github/thebusybiscuit/slimefun4/api/gps/TeleportationManager.java")
    require(teleporter, "Validate.notNull(destination, \"Destination cannot be null\");", "Teleporter destination validation")
    forbid(teleporter, "Validate.notNull(source, \"Destination cannot be null\");", "duplicated Teleporter source validation")

    teleporter_listener = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/listeners/TeleporterListener.java",
    )
    require(
        teleporter_listener,
        "private @Nullable UUID parseOwner(@Nullable String value)",
        "safe teleporter owner parser",
    )
    require(
        teleporter_listener,
        "if (value == null || value.isBlank())",
        "missing teleporter owner handling",
    )
    require(
        teleporter_listener,
        "catch (IllegalArgumentException ignored)",
        "malformed teleporter owner handling",
    )
    require(
        teleporter_listener,
        "UUID owner = parseOwner(ownerUid);",
        "teleporter owner validation before GUI open",
    )
    require(
        teleporter_listener,
        "teleport(result.getData(\"owner\"), p, block);",
        "teleporter loaded-result owner read",
    )
    forbid(
        teleporter_listener,
        "openTeleporterGUI(p, UUID.fromString(ownerUid), b)",
        "raw teleporter owner parsing",
    )

    fluid_pump = read(root, "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/electric/machines/FluidPump.java")
    require(
        fluid_pump,
        "SlimefunUtils.isItemSimilar(itemInSlot, emptyBottle, true, false)\n                        && (fluid.getType() == Material.WATER || fluid.getType() == Material.BUBBLE_COLUMN)",
        "Fluid Pump water-only bottle input",
    )

    assembler = read(root, "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/electric/machines/entities/AbstractEntityAssembler.java")
    require(assembler, "public boolean isSynchronized() {\n                return true;\n            }", "synchronized entity assembler ticker")
    require(assembler, "if (!\"true\".equals(data.getData(KEY_ENABLED)))", "entity assembler fail-closed enabled state")
    require(assembler, "private double readOffset(@Nullable String value)", "entity assembler safe offset parser")
    require(assembler, "Double.isFinite(offset) ? offset : DEFAULT_OFFSET", "entity assembler non-finite offset rejection")
    require(assembler, "catch (NumberFormatException ignored)", "entity assembler malformed offset handling")
    forbid(assembler, "double offset = Double.parseDouble(data.getData(KEY_OFFSET));", "raw entity assembler tick offset parsing")

    geo_miner = read(root, "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/geo/GEOMiner.java")
    require(geo_miner, "public boolean isSynchronized() {\n                return true;\n            }", "synchronized GEO Miner ticker")
    require(geo_miner, "if (!inv.fits(result, OUTPUT_SLOTS)) {\n                    return;", "GEO Miner finished-output backpressure")
    require(geo_miner, "if (inv == null) {\n            return;", "GEO Miner missing-menu guard")

    trash = read(root, "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/cargo/TrashCan.java")
    require(trash, "public boolean isSynchronized() {\n                return true;\n            }", "synchronized Trash Can ticker")
    require(trash, "if (menu == null) {\n                    return;", "Trash Can missing-menu guard")

    reactor = read(root, "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/electric/reactors/Reactor.java")
    require(reactor, "transferOutputToAccessPort(inv, accessPort);", "reactor output draining before byproduct")
    require(reactor, "else if (accessPort != null && accessPort.fits(result, ReactorAccessPort.getOutputSlots()))", "reactor direct access-port byproduct fallback")
    require(
        reactor,
        "// Keep the finished operation and its progress display intact until a full\n                // byproduct can be committed to either the reactor or its live access port.",
        "reactor byproduct and progress-display backpressure",
    )
    require(reactor, "catch (NumberFormatException ignored)", "reactor malformed stored-energy recovery")
    forbid(reactor, "inv.pushItem(result.clone(), getOutputSlots());\n        }\n\n        if (accessPort != null)", "reactor unchecked byproduct insertion")

    smeltery = read(root, "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/electric/machines/ElectricSmeltery.java")
    require(smeltery, "List<Integer> emptySlots = new LinkedList<>();", "Electric Smeltery empty-slot tracking")
    require(
        smeltery,
        "if (!matchingSlots.isEmpty()) {\n                    Collections.sort(matchingSlots, compareSlots(menu));\n                    return toSlotArray(matchingSlots);\n                }\n\n                return toSlotArray(emptySlots);",
        "Electric Smeltery matching-stack then empty-slot cargo fallback",
    )
    forbid(smeltery, "else if (fullSlots == slots.size())", "Electric Smeltery false-full cargo short circuit")

    oil_pump = read(root, "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/geo/OilPump.java")
    require(oil_pump, "ItemStack remaining = inv.pushItem(input.clone(), getOutputSlots());", "Oil Pump depletion remainder capture")
    require(
        oil_pump,
        "if (after == 0) {\n                            inv.replaceExistingItem(slot, null);\n                        } else if (after < before) {\n                            input.setAmount(after);\n                            inv.replaceExistingItem(slot, input);",
        "Oil Pump depletion input preservation",
    )

    composter = read(root, "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/blocks/Composter.java")
    require(composter, "ItemStack remainder =", "Composter delayed output remainder capture")
    require(
        composter,
        "Slimefun.getItemStackService().addItem(outputChest.get(), output, InventoryContext.OUTPUT_CHEST);",
        "Composter delayed output insertion",
    )
    require(composter, "InventoryContext.OUTPUT_CHEST", "Composter output-chest insertion context")
    require(composter, "dropItemNaturally(b.getRelative(BlockFace.UP).getLocation(), remainder)", "Composter overflow preservation")

    crucible = read(root, "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/blocks/Crucible.java")
    require(crucible, "if (!canAcceptLiquid(block, water))", "Crucible obstruction preflight")
    require(crucible, "return water && block.getBlockData() instanceof Waterlogged;", "Crucible waterlogging support")
    require(
        crucible,
        "if (water && block.getWorld().getEnvironment() == Environment.NETHER && !allowWaterInNether.getValue()) {\n            return true;",
        "Crucible preserved Nether consumption semantics",
    )
    require_before(crucible, "if (!canAcceptLiquid(block, water))", "if (craft(p, input))", "Crucible preflight-before-consumption")

    research = read(root, "src/main/java/io/github/thebusybiscuit/slimefun4/api/researches/Research.java")
    require(research, "setCurrencyCost(Slimefun.getResearchCfg().getDouble(path + \".currency-cost\"));", "decimal Vault research cost loading")
    forbid(research, "setCurrencyCost(Slimefun.getResearchCfg().getInt(path + \".currency-cost\"));", "integer-truncated Vault research cost loading")

    print("Special-item runtime correctness verification passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
