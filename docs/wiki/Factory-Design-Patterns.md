# 🏭 Factory Design Patterns

A good Slimefun factory is not the one with the most blocks. It is the one you can understand when something stops working.

This page collects practical layout patterns for building reliable factories with electric machines, Cargo and energy networks.

## Pattern 1: Buffer every major stage

Instead of connecting every machine directly to the next one, use intermediate storage:

```text
Raw Storage
    ↓
Processor A
    ↓
Buffer Chest
    ↓
Processor B
    ↓
Finished Storage
```

Buffers make it obvious where a production chain is blocked and prevent one slow machine from confusing the entire line.

## Pattern 2: One job per Cargo channel

Use Cargo channels intentionally.

For example:

- Channel A — raw inputs
- Channel B — processed dusts/materials
- Channel C — alloys/components
- Channel D — finished products

The exact color does not matter; consistency does.

Write the scheme down somewhere in the factory so another player can understand it.

## Pattern 3: Separate production cells

Instead of one enormous network covering the whole base, build cells:

- ore-processing cell
- alloy/material cell
- food/agriculture cell
- reactor/nuclear cell
- final assembly cell

Each cell can have its own storage, Cargo and sometimes its own energy distribution.

This reduces troubleshooting complexity and can reduce unnecessary network traversal.

## Pattern 4: Manual fallback bench

Keep the essential multiblocks near your factory even after everything becomes electric.

A small manual workshop is useful when:

- power is down
- a machine is being debugged
- a low-volume recipe is not worth automating
- a new player needs to learn the progression

## Pattern 5: Design output before input

Before feeding a machine automatically, make sure every possible output has somewhere to go.

This is especially important for machines and reactors with:

- multiple outputs
- byproducts
- container returns
- variable recipe results

A blocked output is one of the most common reasons a factory appears stalled.

## Pattern 6: Power headroom

Do not size an energy network so generation exactly equals the theoretical demand of the machines.

Leave headroom for:

- several machines starting together
- temporary generator downtime
- new machines added later
- addon machines with higher demand

Capacitors help absorb short demand spikes.

## Pattern 7: Keep nuclear systems isolated

Reactors and radioactive storage deserve their own area.

Keep nuclear fuel, coolant, byproducts and reactor Cargo separate from beginner storage and general-purpose factory channels.

See **[Radiation & Reactors](Radiation-and-Reactors.md)**.

## Pattern 8: Label everything

On a large server, signs/holograms/named storage can save hours later.

Label:

- network purpose
- Cargo channel scheme
- machine input/output
- fuel storage
- byproduct storage
- addon ownership for unusual machines

The person fixing a factory six months later may not be the person who built it.

## Pattern 9: Scale only after one unit works

Before building ten identical production lines:

1. Build one.
2. Run it manually.
3. Add energy.
4. Add Cargo.
5. Fill inputs and let it cycle repeatedly.
6. Confirm no items accumulate in the wrong place.
7. Profile it if the machine is expensive.
8. Only then copy the design.

Scaling a broken design only makes the failure harder to diagnose.

## Pattern 10: Staging for giant builds

For very large automated systems, especially addon-heavy factories, consider prototyping on a staging copy first.

This is useful when the design includes:

- hundreds of network nodes
- chunk loaders
- cross-region Folia behavior
- complex reactors
- high-speed addon machines
- large shared storage systems

## Debugging a factory by layers

When production stops, work from the machine outward:

```text
Valid recipe?
    ↓
Correct input amount?
    ↓
Output space?
    ↓
Machine has energy?
    ↓
Cargo direction/channel/filter correct?
    ↓
Network/chunk loaded and healthy?
    ↓
Protection allowing access?
    ↓
Addon/runtime compatibility healthy?
```

This is much faster than randomly replacing blocks.

## Related pages

- **[Electric Machines](Electric-Machines.md)**
- **[Energy Networks](Energy-Networks.md)**
- **[Cargo Networks](Cargo-Networks.md)**
- **[Server Performance](Server-Performance.md)**
