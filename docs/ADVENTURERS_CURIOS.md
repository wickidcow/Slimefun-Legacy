# Adventurer's Curios

Adventurer's Curios is a built-in Slimefun Legacy guide category for exploration tools, navigation aids, field safety, and unusual expedition gadgets.

The goal is to add fun utility without creating another large machine progression tree or introducing new plugin dependencies.

## Curios

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

### Miner's Canary

A bounded hand-held lava detector.

- Scans only on deliberate use and has a five-second cooldown.
- Never loads an unloaded chunk just to search it.
- Reports the amount of exposed lava found and approximate nearest distance.
- Folia limits the scan to the player's owning chunk to avoid cross-region block access.

### Dungeon Chalk

A reusable personal breadcrumb that does not modify the world.

- Right-click a block to record one marker on the chalk item itself.
- Right-click air to recall the stored world, coordinates and approximate distance.
- Sneak-right-click to erase the breadcrumb.
- Does not place temporary blocks, bypass protection plugins, or require cleanup tasks.

### Storm Glass

A read-only field instrument.

- Reports clear/rain/thunder state.
- Reports the current day phase and moon phase.
- Reports the remaining duration of the current weather cycle.
- Never changes time or weather.

### Expedition Journal

A player-carried biome log.

- Records the current biome only when the player deliberately makes an entry.
- Stores discoveries on that individual journal item using persistent item data.
- Sneak-right-click shows recent discoveries.
- Caps stored biome entries so item metadata cannot grow without bound.

### Beacon Plus

Beacon Plus is a placed expedition-support block with two independent controls.

**Player support** — right-click to cycle:

- Off
- Speed
- Haste
- Resistance
- Regeneration
- Night Vision

**Chunk loading** — sneak-right-click to cycle:

- Off
- This Chunk
- 3x3 Area

Beacon Plus uses Paper/Folia plugin chunk tickets rather than force-loaded chunks or fake players. Slimefun Legacy's normal ticker already processes registered machines whenever their chunk is loaded, so Beacon Plus does not emulate, duplicate, or directly invoke machine ticks. Networks and compatible addons likewise continue through their own normal runtimes while their required chunks remain loaded.

Safety limits are intentionally hard-bounded:

- maximum 64 active chunk-loading Beacon Plus blocks per server
- maximum 256 unique chunks held by Beacon Plus at once
- overlapping beacon areas use reference-counted tickets so one beacon cannot unload a chunk still required by another
- tickets are released when the beacon is broken or when Slimefun disables
- active beacon locations and selected modes are persisted in `plugins/Slimefun/adventurers-curios-beacons.properties` so legitimate loaders can be restored after restart
- stale saved entries are validated after startup and removed if no `BEACON_PLUS` Slimefun block remains there

Beacon Plus configuration is owner-controlled. Server operators may also change a beacon's modes.

#### Beacon Plus compatibility and migration identifiers

The native Slimefun Legacy item ID is:

- `BEACON_PLUS`

The chunk-mode parser also recognizes the public historical BeaconPlus names:

- `KEEP_CHUNK_LOADED`
- `CHUNK_ACTIVATOR`

Those names are treated as migration aliases for the single-chunk loader mode. This is an independent Slimefun Legacy implementation; no proprietary BeaconPlus source is copied.

A third-party BeaconPlus item that was never a Slimefun-tagged item cannot be safely converted merely from its display name. Exact automatic migration of those physical items/blocks should only be added if a real legacy item or storage sample is available, so unrelated beacons cannot be misidentified.

## Runtime design boundaries

Curios should remain lightweight and player-focused. New curios should avoid changing existing saved-world formats, database schemas, Cargo behavior, Energy behavior, or machine transaction semantics.

Beacon Plus is the deliberate exception to the usual "no chunk loading" rule, but it changes chunk residency only. It does not introduce a second machine scheduler, alter energy costs, bypass machine safety/backpressure, or directly operate Networks/Cargo.

Where possible, Curios use bounded, local runtime work and existing Bukkit/Paper APIs so the category remains safe for the Paper-first Slimefun Legacy runtime and does not require another addon.

## Future ideas

Possible later additions include:

- Traveler's Bedroll — an expedition-oriented sleep utility.
- Pocket Campfire — portable field cooking.
- Emergency Parachute — a repairable last-second fall saver.
- Recall Stone — a carefully constrained long-cooldown return tool.
- Relic Detector — an exploration-oriented detector with tightly bounded checks.

These are ideas, not compatibility promises, and should be added individually with runtime and performance checks.
