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

### Wayfarer's Lodestone

A reusable biome-travel curio with a locked 54-slot destination menu.

- Presents dimension-appropriate biome choices in the Overworld, Nether, and End.
- Each search starts from a **randomized probe** up to 10,000 blocks from the user's origin, then uses Paper's biome locator with a bounded 4,096-block search radius.
- The search uses at most three probes and does not deliberately brute-force/generate hundreds of chunks.
- After a biome is located, the destination is checked again for solid, non-liquid, non-hazardous standing space and the world border before `teleportAsync` is attempted.
- Successful travel has a three-minute per-player cooldown; failed searches do not consume the cooldown.
- Sneak-right-click cancels an active search.

### Bastion Resonator

A Nether-only structure compass.

- Resolves the modern `minecraft:bastion_remnant` structure type from the server registry.
- Searches up to 128 chunks and calls the structure locator with **findUnexplored=false**, so the item does not intentionally generate unexplored terrain just to find a Bastion.
- Tunes its Recovery Compass lodestone target to the located Bastion and reports direction and approximate distance.
- Uses a 30-second per-player cooldown to prevent structure-locator spam.

### Emergency Flare

A reusable visual expedition signal.

- Sneak-right-click cycles **Help**, **Rally Point**, and **Danger** modes.
- Right-click launches a colored large firework and a matching particle column.
- The particle marker pulses for about 20 seconds and the item has a 45-second cooldown.
- In normal dimensions the flare is moved above local terrain when practical; in the Nether it stays near the user's vertical level rather than being placed above the bedrock roof.

### Surveyor's Rod

A deeper diagnostic companion to the Explorer's Spyglass.

- Right-click reports world coordinates, biome, chunk coordinates, region-file coordinates, surface height, entity count, block-entity count, and force-loaded state.
- Sneak-right-clicking a block reports block type, biome, block light, sky light, and total light.
- It refuses to load an unloaded chunk merely to inspect it.
- Region/chunk work is scheduled at the target location and the immutable report is returned to the player on the player's scheduler.

### Chunk Stabilizer

A read-only stability scanner intended to expose problematic chunks before they become severe FPS/TPS problems.

- Scores total entities, armor stands, dropped items, minecarts, projectiles, XP orbs, block entities, and hoppers.
- Reports **STABLE**, **BUSY**, **HEAVY**, or **CRITICAL** and highlights extreme armor-stand/item/entity concentrations.
- Sneak-right-click includes extra entity-class counts.
- It never removes entities, breaks blocks, changes block types, or loads an unloaded chunk to perform a scan.

## Beacon Plus

`BEACON_PLUS` is a native Slimefun Legacy block. It does not require the discontinued BeaconPlus3 plugin and does not import, bundle, or execute BeaconPlus3 classes.

Right-clicking a placed Beacon Plus opens a 54-slot owner-controlled menu with **30 independently toggleable effects**. Server operators may also configure it. Every effect defaults to **OFF**.

The menu has two independent controls in addition to the 30 effect toggles. **Power Source** chooses either **Slimefun Electricity** or **Beacon Blocks**. Electricity mode works without a vanilla pyramid and pays the configured field cost once per second. Beacon Blocks mode uses normal vanilla beacon pyramid/sky activation as its power requirement and consumes no Slimefun Energy. **Effect Area** chooses the chunk-aligned area used by every enabled effect: **1x1 Chunks**, **3x3 Chunks**, or **5x5 Chunks**. New and migrated beacons default to **3x3 Chunks**. Player potion effects are applied as normal, non-ambient effects with visible HUD icons and particles. **Extra Range** expands the selected Effect Area by one tier, capped at 5x5. **Extra Power** increases supported effect strength by one tier and increases electricity-mode field draw by 50%.

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
21. **Gravity Well** — strongly pulls Bukkit mobs, including Endermen and passive/hostile AI mobs, plus loose item entities toward the beacon once per second. Players and armor stands are excluded. Normal pull strength is 1.50 versus the prior 0.30 setting, exactly 5× the previous normal pull; Extra Power raises it to 2.10.
22. **Jump** — gives Jump Boost to players inside the field.
23. **Exp Gain** — grants a small passive XP pulse while players remain inside the field.
24. **Cooldown Reduction** — shortens newly applied item cooldowns while the player is in range.
25. **Immortality Field** — gives a chance to cancel otherwise fatal damage. Normal power is 25%; Extra Power is 40%; successful saves have a 60-second per-player cooldown.
26. **Scale** — makes players 25% larger using Slimefun's own namespaced Scale attribute modifier, removed when they leave the field.
27. **Extra Power** — raises supported potion/booster strength by one tier and strengthens several utility effects. The first activation on each Beacon Plus costs 30 XP levels for non-operators.
28. **Extra Range** — expands the selected Effect Area by one tier: 1x1 becomes 3x3, 3x3 becomes 5x5, and 5x5 remains capped at 5x5.
29. **Activator** — when enabled, keeps the current effective Effect Area loaded using bounded Paper plugin chunk tickets.
30. **Auto Repair** — slowly repairs damaged tools, weapons, and armor carried by players in the field.

### Energy and Extra Power balance

Beacon Plus is an `EnergyNetComponentType.CONSUMER` with an **8,192 J internal buffer**.

- Field work runs on a staggered one-second pulse.
- In **Slimefun Electricity** mode, each enabled field effect costs **16 J per pulse** before Extra Power. Extra Power itself and Activator are excluded from the base effect count.
- In **Beacon Blocks** mode, a valid vanilla beacon pyramid/sky activation powers the field and no Slimefun Energy is consumed.
- When **Extra Power** is enabled in electricity mode, the aggregate field-energy cost is multiplied by **1.50**, so the stronger mode uses exactly **50% more machine energy**.
- The first time Extra Power is activated on a particular Beacon Plus, a non-operator owner pays **30 XP levels**. That unlock is stored on the placed Beacon Plus, so turning Extra Power off and back on does not charge again.
- Operators can configure and unlock Extra Power without paying the XP cost.
- In electricity mode, if the buffer cannot pay a field pulse, periodic field work is skipped and event-driven Beacon Plus bonuses stop treating that beacon as powered until a later pulse succeeds. Beacon Blocks mode instead depends on the vanilla pyramid/sky power condition.
- Activator chunk loading remains a separately bounded safety feature. It does not add to the normal field-energy pulse calculation.

### Effect Area, Activator, and safety

**Effect Area** is controlled from the same menu and applies to every enabled Beacon Plus effect. The choices are:

- **1x1 Chunks**
- **3x3 Chunks** — default
- **5x5 Chunks**

Every individual effect remains independently toggleable. The Status item and each effect button display the current affected area. **Extra Range** expands the selected area one tier, capped at 5x5.

**Activator** remains a separate ON/OFF effect. When Activator is enabled, it keeps the current effective Effect Area loaded. Changing Effect Area or Extra Range while Activator is enabled updates the chunk-loader coverage as long as the global safety cap is not exceeded.

The loader is deliberately hard-bounded:

- maximum **64 active Beacon Plus loaders** server-wide
- maximum **256 unique chunks** held by Beacon Plus at once
- overlapping beacons reference-count the same ticket rather than fighting over chunk ownership
- tickets are released when a beacon is broken or Slimefun disables
- Activator locations/load states persist in `plugins/Slimefun/adventurers-curios-beacons.properties`; the selected Effect Area is stored on the placed Beacon Plus
- restored records are validated after startup and stale entries are removed

Activator changes chunk residency only. It does not directly call Slimefun machine, Cargo, Energy, Networks, or addon tick methods. Loaded systems continue through their normal runtimes.

Historical public loader mode names `KEEP_CHUNK_LOADED` and `CHUNK_ACTIVATOR` remain accepted as migration aliases for 1x1 loader coverage. Existing Beacon Plus blocks without a stored Effect Area migrate safely to the 3x3 default.

### Performance model

Beacon Plus intentionally avoids one scheduler per effect or one scheduler per beacon.

- One normal Slimefun `BlockTicker` handles periodic Beacon Plus work and energy payment.
- One listener handles XP changes, cooldowns, peaceful targeting/damage, immortality, and movement cleanup.
- Periodic pulses keep their initial location-based staggering, then use elapsed-tick tracking so a missed exact modulo tick cannot skip a whole one-second field pulse.
- Gravity Well therefore receives one pull on the first eligible ticker at least 20 game ticks after its previous pull instead of requiring one exact server tick.
- Tile-entity work is capped at 96 inspected states per pulse.
- Crop growth uses bounded random samples rather than scanning every block in the field.
- Event-driven effects consult a short-lived powered-state cache populated only after the Beacon Plus completes a successful pulse using its selected valid power source.
- Flight and Scale are reconciled on movement, configuration changes, power loss, break, and shutdown so persistent player state is cleaned up.
- No normal field effect loads chunks just to find targets; Activator is the only feature allowed to hold chunks loaded.
- On Folia, work that would cross region boundaries is reduced to same-region/same-chunk behavior rather than performing unsafe cross-region access.

### Server-owner / admin disable notes

There are three levels of control:

1. **Diagnose or disable one placed Beacon Plus:** open its menu and read **Beacon Plus Status**. It reports the selected Power Source, selected/effective Effect Area, Activator state, `ACTIVE`/`NOT POWERED`, and whether electricity or the Beacon Blocks pyramid condition is blocking the field. The owner or an operator can click **Disable All Effects**. This also turns its Activator off.
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
