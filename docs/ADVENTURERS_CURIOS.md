# Adventurer's Curios

Adventurer's Curios is a built-in Slimefun Legacy guide category for exploration tools, navigation aids, field safety, and unusual expedition gadgets.

The category is intentionally lightweight and Paper-first. Curios should not change existing Cargo, Energy, database, storage-schema, or machine transaction semantics.

## Curios

### Wayfinder's Compass

A reusable field compass that retunes itself when right-clicked.

- Points to the player's last recorded death location.
- Falls back to the current world's spawn when no death is recorded.
- Stores its target on the compass as an untracked lodestone target.

### Echo Lantern

A short-range spectral detector.

- Reveals nearby hostile monsters with Glowing for eight seconds.
- Uses a 20-block radius and a 30-second cooldown.
- Does not load chunks.

### Explorer's Spyglass

A survey tool that reports coordinates, biome, and heading while retaining normal spyglass use.

### Miner's Canary

A bounded lava detector for mining.

- Runs only when deliberately used.
- Never loads a chunk to perform its scan.
- Folia limits cross-region block inspection.

### Dungeon Chalk

A personal breadcrumb stored on the item rather than placed in the world.

- Right-click a block to mark it.
- Right-click air to recall the marker.
- Sneak-right-click to erase it.

### Storm Glass

A read-only field instrument reporting weather, day phase, moon phase, and remaining weather duration.

### Expedition Journal

A player-carried biome log with a bounded number of persistent discoveries.

## Beacon Plus

`BEACON_PLUS` is a native Slimefun Legacy block. It does not require the discontinued BeaconPlus3 plugin and does not import, bundle, or execute BeaconPlus3 classes.

Right-clicking a placed Beacon Plus opens a 54-slot owner-controlled menu. Server operators may also configure it. Every effect defaults to **OFF**.

Normal field effects require the Beacon Plus block to sit on a valid vanilla beacon pyramid. Its base field range is the range reported by the vanilla/Paper beacon. **Extra Range** adds 20 blocks. **Extra Power** increases supported effect strength by one tier.

### Toggleable effects

1. **Furnace Booster** — advances nearby active furnace cooking progress in bounded pulses.
2. **Strength Effect** — gives Strength to players inside the field.
3. **Regeneration Effect** — gives Regeneration to players inside the field.
4. **Resistance Effect** — gives Resistance to players inside the field.
5. **Fast Digging** — gives Haste to players inside the field.
6. **Cure** — removes harmful potion effects from players inside the field.
7. **Crops** — samples nearby loaded crop blocks and advances age without scanning every block in the radius.
8. **Spawners** — reduces the current delay of nearby loaded creature spawners, with a safe minimum delay.
9. **Slowdown** — gives Slowness to hostile monsters inside the field.
10. **Speed** — gives Speed to players inside the field.
11. **Peaceful** — prevents hostile monsters in the field from targeting or directly damaging players.
12. **Nightvision** — gives Night Vision to players inside the field.
13. **Flying** — grants survival flight while the player remains inside the field, then restores the player's prior flight permission on exit or shutdown.
14. **Experience Booster** — multiplies positive experience gains; normal power doubles XP and Extra Power triples it.
15. **Luck** — gives Luck to players inside the field.
16. **Burner** — ignites nearby undead monsters even when ordinary sunlight would not.
17. **Water Breathing** — gives Water Breathing to players inside the field.
18. **Fire Extinguisher** — extinguishes players inside the field.
19. **Poison** — poisons hostile monsters inside the field.
20. **Gravity Well** — gently pulls hostile mobs and loose item entities toward the beacon.
21. **Jump** — gives Jump Boost to players inside the field.
22. **Exp Gain** — grants a small passive XP pulse while players remain inside the field.
23. **Cooldown Reduction** — shortens newly applied item cooldowns while the player is in range.
24. **Immortality Field** — gives a chance to cancel otherwise fatal damage. Normal power is 25%; Extra Power is 40%; successful saves have a 60-second per-player cooldown.
25. **Scale** — makes players 25% larger using Slimefun's own namespaced Scale attribute modifier, removed when they leave the field.
26. **Extra Power** — raises supported potion/booster strength by one tier and strengthens several utility effects.
27. **Extra Range** — adds 20 blocks to the active beacon field range.
28. **Activator** — keeps selected chunks loaded using bounded Paper plugin chunk tickets.
29. **Auto Repair** — slowly repairs damaged tools, weapons, and armor carried by players in the field.

### Activator modes and safety

Activator is controlled from the same menu. Coverage can be:

- **Off**
- **This Chunk**
- **3x3 Area**

The loader is deliberately hard-bounded:

- maximum **64 active Beacon Plus loaders** server-wide
- maximum **256 unique chunks** held by Beacon Plus at once
- overlapping beacons reference-count the same ticket rather than fighting over chunk ownership
- tickets are released when a beacon is broken or Slimefun disables
- Activator locations and coverage modes persist in `plugins/Slimefun/adventurers-curios-beacons.properties`
- restored records are validated after startup and stale entries are removed

Activator changes chunk residency only. It does not directly call Slimefun machine, Cargo, Energy, Networks, or addon tick methods. Loaded systems continue through their normal runtimes.

Historical public mode names `KEEP_CHUNK_LOADED` and `CHUNK_ACTIVATOR` are accepted as migration aliases for **This Chunk**.

### Performance model

Beacon Plus intentionally avoids one scheduler per effect or one scheduler per beacon.

- One normal Slimefun `BlockTicker` handles periodic Beacon Plus work.
- One listener handles XP changes, cooldowns, peaceful targeting/damage, immortality, and movement cleanup.
- Periodic pulses are staggered by beacon location rather than all firing on the same server tick.
- Tile-entity work is capped at 96 inspected states per pulse.
- Crop growth uses bounded random samples rather than scanning every block in the field.
- No effect loads chunks just to find targets; Activator is the only feature allowed to hold chunks loaded.
- On Folia, work that would cross region boundaries is reduced to same-region/same-chunk behavior rather than performing unsafe cross-region access.

### Ownership and clean-room implementation

The placed block stores an owner UUID. Only its owner or a server operator can change settings.

This is an independent Slimefun Legacy implementation. The discontinued BeaconPlus3 plugin was used only as a behavioral/configuration reference for the feature list the server owner wanted to preserve. No proprietary BeaconPlus runtime classes or source code are copied into Slimefun Legacy.

## Runtime design boundaries

New Curios should prefer bounded local work, existing Bukkit/Paper APIs, and deliberate player interaction. Beacon Plus is the deliberate exception that may hold chunks loaded, but its loader is explicitly capped and isolated from Slimefun machine execution.

## Future ideas

Possible later additions include:

- Traveler's Bedroll
- Pocket Campfire
- Emergency Parachute
- Recall Stone
- Relic Detector

These are ideas rather than compatibility promises and should be added individually with runtime and performance checks.
