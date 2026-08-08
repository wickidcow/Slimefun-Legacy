# Slimefun Legacy 4.1.13 — Phase 4.1B-A

## Goal

Automatically support safe guide-to-machine ingredient filling for addon machines that use Slimefun's standard `AContainer` architecture.

## Supported machines

A machine is eligible when all of the following are true:

1. The Slimefun item extends `AContainer`.
2. The displayed guide recipe can be matched to an actual recipe in `AContainer#getMachineRecipes()`.
3. The placed block has the exact same Slimefun item ID as the machine opened in the guide.
4. The machine exposes valid, unique input slots that do not overlap its output slots.
5. The player passes protection, region ownership and inventory-viewer checks.

This allows compatible machines from addons such as InfinityExpansion2, FluffyMachines, LiteXpansion, DynaTech and FoxyMachines to work automatically when they populate the normal `AContainer` recipe list.

Supreme's maintained `GenericMachine` is an important example of the boundary: it extends `AContainer`, but processes its own public `machineRecipes` list instead of the inherited registered recipe list. Its recipes remain browseable, but filling stays disabled until the custom-machine adapter phase can describe Supreme's real processing rules safely.

## Deliberate boundary

Some addons expose extra recipes through public fields or compatibility methods while processing them through custom inventory code. Those recipes remain visible in the recipe browser, but they do not receive a fill button unless the recipe also exists in the container's registered runtime recipe list.

FastMachines and fully custom inventories remain outside this phase. They require the later custom machine adapter API.

## Safety retained from Phase 4.1A

- One-set and maximum-safe-set modes
- Full player and machine inventory simulation
- Rollback after unexpected commit failure
- Exact placed-machine validation
- Protection-plugin checks
- Folia region ownership checks
- Open-inventory and ticker coordination
- Input-slot-only writes
- No output generation, direct processing, energy modification or nearby-storage access
