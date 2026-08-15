# Adventurer's Curios

Adventurer's Curios is a built-in Slimefun Legacy guide category for exploration tools, navigation aids, field safety, and unusual expedition gadgets.

The category is intentionally lightweight and Paper-first. Ordinary Curios do not change existing Cargo, database, storage-schema, or machine transaction semantics. Beacon Plus intentionally participates in Slimefun's existing EnergyNet as a normal consumer so its stronger field effects have an operating cost.

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

Right-clicking a placed Beacon Plus opens a 54-slot owner-controlled menu with **30 independently toggleable effects**. Server operators may also configure it. Every effect defaults to **OFF**.

Normal field effects require the Beacon Plus block to sit on a valid vanilla beacon pyramid and have Slimefun Energy available. Its base field range is the range reported by the vanilla/Paper beacon. **Extra Range** adds 20 blocks. **Extra Power** increases supported effect strength by one tier and is treated as a +50% energy overclock.

### Toggleable effects

1. **Furnace Booster** — advances nearby active furnace cooking progress in bounded pulses.
2. **Strength Effect** — gives Strength to players inside the field.
3. **Invisible Effect** — gives Invisibility to players while they remain inside the powered field. This restores the historical BeaconPlus3 effect that was missing from the initial native port.
4. **Regeneration Effect** — gives Regeneration to players inside the field.
5. **Resistance Effect** — gives Resistance to players inside the field.
6. **Fast Digging** — gives Haste to players inside the field.
7. **Cure** — removes harmful potion effects from players inside the field.
8. **Crops** — samples nearby loaded crop blocks and advances age without scanning every block in the radius.
9. **Spawners** — reduces the current delay of nearby loaded creature spawners, with a safe minimum delay.
10. **Slowdown** — gives Slowness to hostile monsters inside the field.
11. **Speed** — gives Speed to players inside the field.
12. **Peaceful** — prevents hostile monsters in the field from targeting or directly damaging players.
13. **Nightvision** — gives Night Vision to players inside the field.
14. **Flying** — grants survival flight while the player remains inside the field, then restores the player's prior flight permission on exit or shutdown.
15. **Experience Booster** — multiplies positive experience gains; normal power doubles XP and Extra Power triples it.
16. **Luck** — gives Luck to players inside the field.
17. **Burner** — ignites nearby undead monsters even when ordinary sunlight would not.
18. **Water Breathing** — gives Water Breathing to players inside the field.
19. **Fire Extinguisher** — extinguishes players inside the field.
20. **Poison** — poisons hostile monsters inside the field.
21. **Gravity Well** — gently pulls hostile mobs and loose item entities toward the beacon.
22. **Jump** — gives Jump Boost to players inside the field.
23. **Exp Gain** — grants a small passive XP pulse while players remain inside the field.
24. **Cooldown Reduction** — shortens newly applied item cooldowns while the player is in range.
25. **Immortality Field** — gives a chance to cancel otherwise fatal damage. Normal power is 25%; Extra Power is 40%; successful saves have a 60-second per-player cooldown.
26. **Scale** — makes players 25% larger using Slimefun's own namespaced Scale attribute modifier, removed when they leave the field.
27. **Extra Power** — raises supported potion/booster strength by one tier and strengthens several utility effects. The first activation on each Beacon Plus costs 30 XP levels for non-operators.
28. **Extra Range** — adds 20 blocks to the active beacon field range.
29. **Activator** — keeps selected chunks loaded using bounded Paper plugin chunk tickets.
30. **Auto Repair** — slowly repairs damaged tools, weapons, and armor carried by players in the field.

### Energy and Extra Power balance

Beacon Plus is an `EnergyNetComponentType.CONSUMER` with an **8,192 J internal buffer**.

- Field work is paid once per staggered one-second pulse.
- Each enabled field effect costs **16 J per pulse** before Extra Power. Extra Power itself and Activator are excluded from the base effect count.
- When **Extra Power** is enabled, the aggregate field-energy cost is multiplied by **1.50**, so the stronger mode uses exactly **50% more machine energy**.
- The first time Extra Power is activated on a particular Beacon Plus, a non-operator owner pays **30 XP levels**. That unlock is stored on the placed Beacon Plus, so turning Extra Power off and back on does not charge again.
- Operators can configure and unlock Extra Power without paying the XP cost.
- If the buffer cannot pay a field pulse, periodic field work is skipped and event-driven Beacon Plus bonuses stop treating that beacon as powered until a later pulse succeeds.
- Activator chunk loading remains a separately bounded safety feature. It does not add to the normal field-energy pulse calculation.

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

- One normal Slimefun `BlockTicker` handles periodic Beacon Plus work and energy payment.
- One listener handles XP changes, cooldowns, peaceful targeting/damage, immortality, and movement cleanup.
- Periodic pulses are staggered by beacon location rather than all firing on the same server tick.
- Tile-entity work is capped at 96 inspected states per pulse.
- Crop growth uses bounded random samples rather than scanning every block in the field.
- Event-driven effects consult a short-lived powered-state cache populated only after the Beacon Plus successfully pays its energy pulse.
- Flight and Scale are reconciled on movement, configuration changes, power loss, break, and shutdown so persistent player state is cleaned up.
- No normal field effect loads chunks just to find targets; Activator is the only feature allowed to hold chunks loaded.
- On Folia, work that would cross region boundaries is reduced to same-region/same-chunk behavior rather than performing unsafe cross-region access.

### Server-owner / admin disable notes

There are three levels of control:

1. **Disable one placed Beacon Plus:** the owner or an operator can open its menu and click **Disable All Effects**. This also turns its Activator off.
2. **Disable Beacon Plus globally:** stop the server, open `plugins/Slimefun/Items.yml`, and set:

   ```yaml
   BEACON_PLUS:
     enabled: false
   ```

   Start the server again after saving. Slimefun registers configurable items using the `<ITEM_ID>.enabled` setting, so `BEACON_PLUS.enabled` is the global switch.
3. **Disable it only in selected worlds:** use the normal Slimefun per-world item settings for `BEACON_PLUS`. Global disable still takes priority.

If a server owner wants Beacon Plus available but does not want Extra Power used, leave Extra Power **OFF** in each Beacon Plus menu. Extra Power defaults to OFF and does not consume its 30-level unlock cost until someone deliberately activates it.

### Ownership and clean-room implementation

The placed block stores an owner UUID. Only its owner or a server operator can change settings.

This is an independent Slimefun Legacy implementation. The discontinued BeaconPlus3 plugin was used only as a behavioral/configuration reference for the feature list the server owner wanted to preserve. No proprietary BeaconPlus runtime classes or source code are copied into Slimefun Legacy.

## Runtime design boundaries

New Curios should prefer bounded local work, existing Bukkit/Paper APIs, and deliberate player interaction. Beacon Plus is the deliberate exception that may hold chunks loaded and consume Slimefun Energy, but its loader is explicitly capped and its energy use goes through the existing EnergyNet consumer contract.

## Future ideas

Possible later additions include:

- Traveler's Bedroll
- Pocket Campfire
- Emergency Parachute
- Recall Stone
- Relic Detector

These are ideas rather than compatibility promises and should be added individually with runtime and performance checks.
