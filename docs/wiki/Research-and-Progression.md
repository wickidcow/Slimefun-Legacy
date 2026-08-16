# 📚 Research & Progression

Research is one of Slimefun's core progression systems. Instead of receiving every recipe immediately, players gradually unlock technologies through the **Slimefun Guide**.

This gives Slimefun a progression curve closer to a modpack than a simple collection of custom recipes.

## How research works

Many Slimefun items are attached to a research entry. When a player reaches that item in the Guide, the Guide can show the research requirement before the recipe becomes available.

Research commonly costs Minecraft experience levels. Server owners can change progression behavior through configuration, permissions or other plugins, so the values shown on one server may differ from another.

> **Use the in-game Guide for the exact research cost on your server.**

## Recommended early-game path

A practical first progression path is:

1. Learn the **Enhanced Crafting Table**.
2. Build the early manual processing multiblocks.
3. Start producing dusts and basic ingots.
4. Learn alloy production and technical components.
5. Unlock your first electric machines.
6. Establish a small generator and energy storage.
7. Upgrade toward automatic resource processing.
8. Add Cargo once you have multiple machines worth connecting.

Trying to unlock everything immediately usually wastes experience and makes the Guide harder to learn.

## Research is per player

Research normally represents a player's own progression rather than a global server unlock. Two players on the same server can therefore have different research progress.

This matters when:

- sharing Slimefun items with new players
- diagnosing why one player sees a recipe while another does not
- recovering player data
- migrating between Slimefun builds

## Knowledge-sharing items

Classic Slimefun includes items centered around transferring or preserving knowledge, such as knowledge tomes/flasks depending on the enabled content. Their exact behavior and availability can be changed by server configuration or addons.

Always check the Guide entry before using a knowledge-transfer item on valuable progress.

## Server-owner advice

For a progression-focused server:

- avoid giving all researches by default unless that is intentional
- keep research costs high enough to create progression but not so high that players avoid experimentation
- test addon research trees after installing or updating addons
- keep backups before changing research or player-data behavior
- use permissions for administrative research tools rather than granting them broadly

## When research looks broken

If a recipe unexpectedly appears locked or unlocked:

1. Confirm the item exists in the current Guide.
2. Check whether the server disabled that item or category.
3. Confirm the player has the expected research.
4. Check whether an addon owns the item.
5. Restart normally if configuration was changed — do not use `/reload`.
6. For persistent problems, gather diagnostics from **[Doctor & Diagnostics](Doctor-and-Diagnostics.md)**.

## Next steps

Once you understand research, continue with **[Multiblocks & Basic Machines](Multiblocks-and-Basic-Machines.md)** and **[Resources, Dusts & Alloys](Resources-Dusts-and-Alloys.md)**.
