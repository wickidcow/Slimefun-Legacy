# ⚙️ Electric Machines

Electric machines are where Slimefun progression starts to feel like a real factory. They replace repeated manual processing with powered blocks that can run recipes automatically and, in many cases, integrate with Cargo.

## Before building electric machines

Make sure you understand three systems first:

1. **Energy generation** — something must produce power.
2. **Energy storage and distribution** — the network must be able to deliver enough power when machines need it.
3. **Input/output flow** — the machine still needs valid ingredients and room for products.

If any one of those is missing, a machine can look completely built while doing nothing.

See **[Energy Networks](Energy-Networks.md)** before creating a large electric workshop.

## Common electric-machine families

Classic Slimefun includes many powered machines. Exact availability depends on the current build and server configuration, but familiar families include:

| Family | Examples / role |
| --- | --- |
| **Ore processing** | Electric Gold Pan, Electric Dust Washer, Electric Ore Grinder |
| **Metal processing** | Electric Ingot Factory, Electric Ingot Pulverizer, Electric Smeltery |
| **Pressure / material processing** | Electric Press, Electrified Crucible, Heated Pressure Chamber, Carbon Press |
| **Enchanting** | Auto Enchanter, Auto Disenchanter, Auto Anvil, Book Binder |
| **Food / farming** | Food Fabricator, Food Composter, Crop/Tree/Animal growth systems |
| **Utility** | Charging Bench, Freezer, Fluid Pump, EXP Collector |
| **Entity automation** | Iron Golem / Wither assembly systems where enabled |

The in-game Guide is authoritative for recipe, tier, power demand and whether a machine is enabled.

## Machine tiers

Some Slimefun machines have multiple tiers. Higher tiers commonly trade more expensive crafting requirements for faster processing, larger buffers or other improvements.

When planning upgrades, ask:

- Is my bottleneck machine speed or ingredient supply?
- Can the energy network sustain the higher tier?
- Can Cargo feed and empty the machine fast enough?
- Is the upgrade worth the resource cost for the amount I actually process?

A faster machine does not help if it spends most of its time waiting for input.

## Machine buffers

Many machines can internally hold some energy. Do not confuse a machine's own buffer with a network capacitor.

The network still needs enough generation and storage to keep several consumers running together.

## Recipe troubleshooting

When an electric machine will not begin a recipe, check in this order:

1. Is the recipe actually valid for this machine and tier?
2. Are the exact required amounts present?
3. Is there room for every output/byproduct?
4. Does the machine have access to enough energy?
5. Is Cargo moving an ingredient out again before the machine can reserve it?
6. Does an addon own or modify this machine?

Slimefun Legacy includes focused runtime-correctness testing for shared processors and important machine families, but an addon can still implement its own inventory or recipe behavior.

## Auto Enchanter and Auto Disenchanter

Enchanting machines are especially sensitive to exact item state and recipe lifecycle. If one accepts an item but appears to do nothing, verify energy, valid inputs and output space before assuming the machine is frozen.

If behavior changed after an update, collect `/sf versions`, Doctor diagnostics and the first relevant exception before modifying stored items.

## Cargo automation

Once one machine works correctly by hand, automate it with Cargo.

A reliable factory pattern is:

**buffer chest → machine input → machine output → buffer chest**

Prove each step before chaining many machines together. This makes it much easier to identify whether a failure is in the recipe, energy network or Cargo routing.

See **[Cargo Networks](Cargo-Networks.md)**.

## Addon machines

Addon electric machines may use the classic Slimefun machine APIs or their own custom inventory/processor logic.

Slimefun Legacy exposes machine recipe-provider and input-fill adapter APIs to make Enhanced Guide and safe automation integration easier for compatible addons.

For an addon machine that behaves differently from core machines, check **[Addon Ecosystem](Addon-Ecosystem.md)** before treating it as a core bug.

## Related pages

- **[Energy Networks](Energy-Networks.md)**
- **[Cargo Networks](Cargo-Networks.md)**
- **[Resources, Dusts & Alloys](Resources-Dusts-and-Alloys.md)**
- **[Server Performance](Server-Performance.md)**
