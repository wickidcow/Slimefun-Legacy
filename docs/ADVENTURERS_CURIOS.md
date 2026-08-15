# Adventurer's Curios

Adventurer's Curios is a built-in Slimefun Legacy guide category for exploration tools, navigation aids, field safety, and unusual expedition gadgets.

The category is intentionally lightweight and Paper-first. Curios should not change existing Cargo, Energy, database, storage-schema, or machine transaction semantics.

The category is controlled by `options.enable-non-original-slimefun-additions`. Slimefun Legacy ships this option as `true`; setting it to `false` disables Adventurer's Curios and future Legacy-only gameplay additions on the next restart.

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

A carried, bounded early-warning curio for mining and exploration.

- Chirps automatically when a nearby hostile mob targets the carrier or is moving toward them.
- Chirps for nearby **exposed lava**, rather than sealed lava completely enclosed by blocks.
- Also warns for a few immediate player dangers such as being on fire, dangerously low air, or a dangerous fall.
- Passive checks occur only for players actually carrying the Canary.
- Movement-based hazard scans are throttled to once every two seconds per carrier.
- Repeated warning chirps are rate-limited to once every four seconds per carrier.
- Right-click performs an immediate manual danger scan and reports either the nearest detected danger or an all-clear.
- The lava scan is bounded to six blocks horizontally and five blocks vertically.
- Hostile approach detection is bounded to 12 blocks.
- Never loads a chunk to perform a scan.
- On Folia, entity and block inspection is restricted to safe local chunk behavior.

Dungeon Chalk has been removed from Adventurer's Curios and is no longer registered or built.

### Storm Glass

A read-only field instrument reporting weather, day phase, moon phase, and remaining weather duration.

### Expedition Journal

A player-carried biome log with a bounded number of persistent discoveries.

### Traveler's Bedroll

`ADVENTURERS_TRAVELERS_BEDROLL` is a portable personal rest tool intended for long expeditions without replacing Minecraft's real bed/spawn mechanics.

- Right-click at night in the Overworld to rest.
- Rest is refused when hostile monsters are within eight blocks.
- Resets the player's phantom-rest timer.
- Restores two hearts, four food points, and a small amount of saturation.
- Has a five-minute cooldown.
- Does **not** place a temporary bed, change world time, skip the night, teleport the player, or change the player's respawn point.
- Runs only when deliberately used; there is no repeating bedroll task.

### Emergency Parachute

`ADVENTURERS_EMERGENCY_PARACHUTE` is a reusable carried safety curio.

- Automatically activates when a fall would deal at least three hearts of damage, or when a smaller fall would otherwise be lethal.
- Prevents that fall's damage and resets fall distance.
- Can activate while carried in the player's normal inventory or off-hand; it does not need to be equipped as armor.
- Ignores small non-lethal falls so the safety cooldown is not wasted.
- Has a 60-second cooldown after a successful deployment.
- Uses an event listener only; it has no repeating scheduler and performs no chunk scans.

## Beacon Plus

`BEACON_PLUS` is a native Slimefun Legacy block in Adventurer's Curios. It does not require the discontinued BeaconPlus3 plugin and does not import, bundle, or execute BeaconPlus3 classes.

### Recipe

Beacon Plus uses this exact Enhanced Crafting Table recipe:

| | | |
|---|---|---|
| Echo Shard | Essence of Afterlife | Echo Shard |
| Magical Glass | Beacon | Magical Glass |
| Blistering Ingot | Synthetic Diamond | Blistering Ingot |

The recipe uses the real Slimefun `ESSENCE_OF_AFTERLIFE`, `MAGICAL_GLASS`, completed `BLISTERING_INGOT_3`, and `SYNTHETIC_DIAMOND` item stacks.

Right-clicking a placed Beacon Plus opens a 54-slot owner-controlled menu. Server operators may also configure it. The menu exposes exactly **28 independently toggleable powers**, matching the Albion keep list. Every power defaults to **OFF**.

Normal field powers require the Beacon Plus block to sit on a valid vanilla beacon pyramid. Its base field range is the range reported by the vanilla/Paper beacon. **Extra Range** adds 20 blocks. **Extra Power** increases supported effect strength by one tier.

### Toggleable powers

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
20. **Gravity Well** — pulls supported nearby non-player entities toward the beacon.
21. **Jump** — gives Jump Boost to players inside the field.
22. **Exp Gain** — grants a small passive XP pulse while players remain inside the field.
23. **Cooldown Reduction** — shortens newly applied item cooldowns while the player is in range.
24. **Immortality Field** — gives a chance to cancel otherwise fatal damage. Normal power is 25%; Extra Power is 40%; successful saves have a 60-second per-player cooldown.
25. **Extra Power** — raises supported potion/booster strength by one tier and strengthens several utility powers.
26. **Extra Range** — adds 20 blocks to the active beacon field range.
27. **Activator** — keeps selected chunks loaded using bounded Paper plugin chunk tickets.
28. **Auto Repair** — slowly repairs damaged tools, weapons, and armor carried by players in the field.

The brief development-only **Scale** experiment is not an available power. Its enum value remains only as a migration tombstone so an old stored `scale` value is ignored and removed rather than becoming active.

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

Beacon Plus intentionally avoids one scheduler per power or one scheduler per beacon.

- One normal Slimefun `BlockTicker` handles periodic Beacon Plus work.
- One listener handles XP changes, cooldowns, peaceful targeting/damage, immortality, and movement cleanup.
- Periodic pulses are staggered by beacon location rather than all firing on the same server tick.
- Tile-entity work is capped at 96 inspected states per pulse.
- Crop growth uses bounded random samples rather than scanning every block in the field.
- No field power loads chunks just to find targets; Activator is the only power allowed to hold chunks loaded.
- On Folia, work that would cross region boundaries is reduced to same-region/same-chunk behavior rather than performing unsafe cross-region access.

### Ownership and implementation boundary

The placed block stores an owner UUID. Only its owner or a server operator can change settings.

This is a native Slimefun Legacy implementation built around the requested behavior list and configuration semantics. BeaconPlus3 is not loaded as a dependency and its runtime classes are not bundled into Slimefun Legacy.

## Runtime design boundaries

New Curios should prefer bounded local work, existing Bukkit/Paper APIs, and deliberate player interaction. Beacon Plus is the deliberate exception that may hold chunks loaded, but its loader is explicitly capped and isolated from Slimefun machine execution.

## Future ideas

Possible later additions include:

- Pocket Campfire
- Relic Detector

A Recall Stone is intentionally not planned because existing RTP/teleport tooling already fills that role. These ideas are not compatibility promises and should be added individually with runtime and performance checks.
