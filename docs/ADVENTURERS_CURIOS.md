# Adventurer's Curios

Adventurer's Curios is a built-in Slimefun Legacy guide category for exploration tools, navigation aids, field safety, and unusual expedition gadgets.

The goal is to add fun utility without creating another large machine progression tree or changing existing Cargo, Energy, storage, or machine transaction semantics. Optional integrations should stay optional and must not become hard startup dependencies.

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

Beacon Plus is an optional bridge between Adventurer's Curios and the standalone **BeaconPlus3** plugin.

Slimefun Legacy does **not** reimplement BeaconPlus3's beacon runtime. Instead:

1. The Enhanced Crafting Table crafts a Slimefun `BEACON_PLUS` commissioning item.
2. Right-clicking that item checks whether the `BeaconPlus3` plugin is installed and enabled.
3. Slimefun reads BeaconPlus3's configured `Permissions.Craft` value and honors it using BeaconPlus3's own permission helper.
4. Slimefun calls BeaconPlus3's public `BeaconAPI#createBeaconEmptyItem(Player)` hook through an optional reflection boundary.
5. The Curio is replaced with the genuine BeaconPlus3 beacon item created by BeaconPlus3 itself.
6. From that point onward, BeaconPlus3 exclusively owns placement, GUI, effects, upgrades, access lists, serialization, persistence, chunk behavior, and beacon runtime logic.

If BeaconPlus3 is missing, disabled, its craft permission is denied, or its creation API cannot be reached, the Curio is **not consumed**. Slimefun Legacy therefore has no hard BeaconPlus3 dependency and remains usable on servers without the plugin.

The Curios recipe mirrors the familiar BeaconPlus3 beacon pattern, but uses the Enhanced Crafting Table:

```text
Glass       Lever        Glass
Clock       End Crystal  Redstone Block
Obsidian    Anvil        Obsidian
```

The resulting commissioned beacon always uses the installed BeaconPlus3 build's own current configuration. Slimefun does not duplicate or cache BeaconPlus3 settings such as base power, pyramid power sources, range calculation, effects, economy costs, permissions, access-list behavior, GUI configuration, storage, or Albion-specific runtime fixes.

This integration intentionally uses no direct `thito.beaconplus` imports or bundled BeaconPlus classes. The standalone plugin remains independently upgradeable, and Slimefun only depends on the public creation/config hooks at the moment a player commissions the Curio.

## Runtime design boundaries

Curios should remain lightweight and player-focused. New curios should avoid changing existing saved-world formats, database schemas, Cargo behavior, Energy behavior, or machine transaction semantics.

Where possible, Curios use bounded, local runtime work and existing Bukkit/Paper APIs so the category remains safe for the Paper-first Slimefun Legacy runtime.

Optional third-party bridges should follow the Beacon Plus model:

- no hard startup dependency
- no copied third-party runtime implementation
- no bundled third-party classes
- target-plugin permissions are honored rather than bypassed
- failure leaves the player's Curio intact
- ownership is handed to the target plugin once it creates its native item

## Future ideas

Possible later additions include:

- Traveler's Bedroll — an expedition-oriented sleep utility.
- Pocket Campfire — portable field cooking.
- Emergency Parachute — a repairable last-second fall saver.
- Recall Stone — a carefully constrained long-cooldown return tool.
- Relic Detector — an exploration-oriented detector with tightly bounded checks.

These are ideas, not compatibility promises, and should be added individually with runtime and performance checks.
