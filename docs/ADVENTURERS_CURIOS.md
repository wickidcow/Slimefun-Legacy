# Adventurer's Curios

Adventurer's Curios is a built-in Slimefun Legacy guide category for exploration tools, navigation aids, and unusual field gadgets.

The goal is to add fun utility without creating another large machine progression tree or introducing new plugin dependencies.

## First curios

### Wayfinder's Compass

A reusable field compass that retunes itself when right-clicked.

- Points to the player's last recorded death location.
- If no death location exists, it binds to the current world's spawn.
- Stores the target on the compass itself as an untracked lodestone target.
- Shows the target world and coordinates when retuned.

### Echo Lantern

A short-range spectral detector for dangerous areas.

- Right-click to pulse a 20-block radius around the player.
- Nearby hostile monsters glow for 8 seconds.
- Has a 30-second cooldown.
- Does not load chunks or scan block storage.

### Explorer's Spyglass

A survey tool that keeps the normal spyglass experience while reporting field information.

- Displays current X/Y/Z coordinates.
- Displays the current biome.
- Displays the player's cardinal/intercardinal heading.

## Design boundaries

Curios should remain lightweight and player-focused. New curios should avoid changing saved-world formats, database schemas, Cargo behavior, Energy behavior, or machine transaction semantics.

Where possible, curios should use bounded, local runtime work and existing Bukkit/Paper APIs so the category remains safe for the Paper-first Slimefun Legacy runtime and does not require another addon.

## Future ideas

Possible later additions include:

- Miner's Canary — warns when dangerous lava is close.
- Dungeon Chalk — lightweight exploration markers.
- Traveler's Bedroll — an expedition-oriented sleep utility.
- Pocket Campfire — portable field cooking.
- Emergency Parachute — a repairable last-second fall saver.
- Recall Stone — a carefully constrained long-cooldown return tool.
- Relic Detector — an exploration-oriented detector with tightly bounded checks.

These are ideas, not compatibility promises, and should be added individually with runtime and performance checks.
