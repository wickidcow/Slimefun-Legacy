# Adventurer's Curios

Adventurer's Curios is a built-in Slimefun Legacy guide category for exploration tools, navigation aids, field safety, and expedition gadgets. It is controlled by `options.enable-non-original-slimefun-additions`.

## Current Curios

### Wayfinder's Compass
Retunes to the player's last death location and falls back to world spawn.

### Echo Lantern
Reveals nearby hostile monsters with Glowing for eight seconds. It uses a 20-block radius, 30-second cooldown, and does not load chunks.

### Explorer's Spyglass
Reports coordinates, biome and heading.

### Miner's Canary
A carried passive danger alarm.

- Warns when an `Enemy` is targeting or moving toward the carrier within 12 blocks.
- Warns about exposed lava in a bounded local scan.
- Chirps immediately when breaking a block exposes adjacent lava.
- Warns for fire, dangerously low air, and dangerous falls.
- Passive scans are rate-limited and never load chunks.
- Right click performs an immediate manual scan.

Dungeon Chalk was intentionally removed from Adventurer's Curios.

### Storm Glass
Read-only weather, day-phase and moon-phase field instrument.

### Expedition Journal
Bounded player-carried biome log.

### Traveler's Bedroll
Portable personal rest with a five-minute cooldown. It resets phantom rest and restores a little health/food without changing world time or the player's respawn point.

### Emergency Parachute
Event-driven carried fall saver with a 60-second cooldown. It prevents dangerous/lethal fall damage and has no repeating task.

## Resonance Beacon

`BEACON_PLUS` is retained as the internal Slimefun id for migration compatibility, but the player-facing item is **Resonance Beacon**.

### Recipe

Enhanced Crafting Table:

| | | |
|---|---|---|
| Echo Shard | Essence of Afterlife | Echo Shard |
| Magical Glass | Beacon | Magical Glass |
| Blistering Ingot | Synthetic Diamond | Blistering Ingot |

### Progression

The Resonance Beacon has exactly 28 player-facing powers. Every power is independently controlled by the server under `SlimefunLegacyAddition.PoweredBeacon.powers`.

For native Resonance Beacons, unlocks are permanent to the beacon owner and support **Tier I, Tier II and Tier III**. A player can:

- right click a locked power to purchase Tier I and enable it;
- right click an unlocked power to enable/disable it;
- shift-right-click to purchase the next tier.

Costs may use Minecraft experience levels or Vault money. Global and per-power payment modes/costs are configurable. All Resonance Beacon powers ship enabled by default; a server owner can disable any individual power by setting that power's `enabled` value to `false` under `SlimefunLegacyAddition.PoweredBeacon.powers`.

Purchasing Tier III never bypasses the physical beacon. The effective tier is capped by the pyramid below the beacon.

### Optional electric operation

Every Resonance Beacon can optionally operate as a native Slimefun Energy Network consumer. Electric operation is **OFF by default**, including existing and BeaconData-imported beacons, so upgrading does not add an energy requirement to an established beacon.

The owner or an operator can toggle **Electric Operation** from the beacon GUI. When enabled:

- the beacon accepts Slimefun energy through the normal Energy Network and stores it in a 4,096 J buffer by default;
- once per 20-tick runtime pulse it pays a configurable base cost plus a small cost for each active power tier;
- Activator adds a configurable tier surcharge because it can hold 1x1, 3x3, or 5x5 chunks loaded;
- if the buffer cannot pay the current pulse, all powers become dormant without losing selections or purchased tiers;
- Activator chunk tickets are released while unpowered and automatically return when enough energy is available again;
- turning electric operation OFF immediately returns the beacon to normal pyramid/progression-only operation.

The capacity and pulse-cost values are configurable under `SlimefunLegacyAddition.PoweredBeacon.electric-operation`. The energy option does not create a 29th power and never bypasses pyramid tier requirements.

### Pyramid resonance

Default mineral values:

- Iron Block: 1
- Gold Block: 2
- Emerald Block: 3
- Diamond Block: 4
- Netherite Block: 5

Default physical thresholds:

- Tier I: at least a 3x3 / one-layer beacon base and average material power 1
- Tier II: at least a 5x5 / two-layer base and average material power 3
- Tier III: at least a 7x7 / three-layer base and average material power 4

Mixed valid beacon minerals are supported; the average configured material power is used. All mineral values and thresholds are editable in `config.yml`.

### Toggleable powers

1. Furnace Booster
2. Strength Effect
3. Regeneration Effect
4. Resistance Effect
5. Fast Digging
6. Cure
7. Crops
8. Spawners
9. Slowdown
10. Speed
11. Peaceful
12. Nightvision
13. Flying
14. Experience Booster
15. Luck
16. Burner
17. Water Breathing
18. Fire Extinguisher
19. Poison
20. Gravity Well
21. Jump
22. Exp Gain
23. Cooldown Reduction
24. Immortality Field
25. Extra Power
26. Extra Range
27. Activator
28. Auto Repair

The old Scale experiment remains only as a disabled migration tombstone and is not a configurable power.

### Tier behavior

Potion powers use Tier I/II/III as effect amplifiers 0/1/2. Other powers scale within bounded limits. Examples include stronger furnace/spawner/crop boosts, more passive XP, faster repair, stronger Gravity Well pull, and stronger Burner duration.

Experience Booster multiplies positive XP by 2x/3x/4x. Cooldown Reduction uses 40%/60%/75% reduction. Immortality Field uses 25%/40%/55% save chance with a 60-second successful-save cooldown.

Extra Range adds 10 blocks per tier. Extra Power can raise supported field powers further, still capped at Tier III.

Field powers use a **chunk-aligned square footprint** derived from the beacon's effective range. Every covered chunk is affected from the world's minimum build height through its maximum build height, so there is no vertical distance limit. Normal field powers still never load chunks on their own. The **Show Effect Area** control renders the exact covered chunk grid as a flat square at each viewer's current Y level.

Activator coverage is derived from its effective tier:

- Tier I: this chunk
- Tier II: 3x3 chunks
- Tier III: 5x5 chunks

Activator retains hard server safety caps of 64 active Resonance Beacon loaders and 256 unique ticketed chunks. Overlapping loaders are reference-counted.

## BeaconData compatibility

The old BeaconPlus **WORLD** storage layout is supported directly. By default Slimefun creates a `BeaconData` folder inside every world folder:

`<world>/BeaconData/<chunkX>.<chunkZ>.json`

The JSON layout remains the legacy shape:

```json
{"Beacons":[{"x":0,"y":64,"z":0,"customName":"Beacon","showParticles":true,"effects":{}}]}
```

The compatibility reader supports the old fields `x`, `y`, `z`, `customName`, `showParticles`, optional `overriddenRange`, and per-effect `level`, `selected`, and optional `modes`.

Legacy aliases such as `exp_boost`, `resist`, `fastdig`, `nightvision`, `immortality`, `fireExtinguisher`, and `fire_extenguisher` are recognized. Unknown/unapproved legacy effects (for example Glow, Invisible and Scale) are preserved in the compatibility JSON but ignored by the native Resonance Beacon runtime.

Legacy levels are mapped proportionally into three native tiers, so an old effect that was fully upgraded remains a Tier III unlock even if that old effect used a maximum level above three. The raw old `level` value is preserved when mirroring the JSON. Powers whose old maximum was already three or lower retain their old numeric tier directly.

Existing legacy records contain no owner UUID. Imported beacons are therefore operator-managed and their old unlock/selected levels are grandfathered rather than charging a new purchase cost. Native owner/progression metadata remains in Slimefun storage and is not injected into the compatibility JSON schema.

The reader also recovers the old double-encoded JSON failure mode and normalizes it on the next save.

### BeaconData config

`SlimefunLegacyAddition.PoweredBeacon.BeaconData` controls:

- `enabled`
- `storage-type` (WORLD is the compatibility mode)
- `folder-name` (default `BeaconData`)
- `import-existing`
- `mirror-native-beacons`
- `bootstrap-legacy-activators`
- `honor-overridden-range`

This allows an existing BeaconPlus `BeaconData` directory to be copied directly into the appropriate world folder.

## Runtime boundaries

- One normal Slimefun `BlockTicker` handles periodic Resonance Beacon work.
- Event-driven XP, cooldown, Peaceful, Immortality and player-state work share one listener.
- Tile-entity inspection is bounded to 96 states per pulse.
- Crop growth uses bounded random samples.
- Normal field powers cover full-height chunk columns but never load chunks.
- Activator is the only Resonance Beacon system allowed to hold chunks loaded.
- Folia cross-region work is reduced to safe local behavior.
- Shutdown restores temporary player state, saves progression, and releases chunk tickets.
- No BeaconPlus3 runtime classes are loaded or bundled.
