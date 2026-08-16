# ⚙️ Server Configuration

Slimefun Legacy ships with production-oriented defaults, but server owners can tune progression, Guide behavior, machine timing, networks, radiation, backups and Legacy safety features in `plugins/Slimefun/config.yml`.

> [!WARNING]
> Back up the current configuration before editing it, and **restart the server normally** after changes. Do not use `/reload`.

## English-first defaults

Slimefun Legacy is maintained as an English-first fork. The default configuration uses:

```yaml
options:
  auto-update: false
  language: en
  enable-translations: false
```

The built-in upstream auto-updater is intentionally disabled so it cannot replace the Legacy build with a different upstream core.

## Guide settings

Current defaults include:

```yaml
guide:
  show-vanilla-recipes: true
  receive-on-first-join: true
  show-hidden-item-groups-in-search: false
```

These control whether vanilla recipes appear in the Slimefun Guide, whether new players receive a Guide and whether hidden groups are included in search.

## Research settings

Research progression can be adjusted under `researches:`.

Important options include:

```yaml
researches:
  free-in-creative-mode: true
  enable-fireworks: true
  use-money-unlock: false
  currency-cost-convert-rate: 25.0
  disable-learning-animation: false
```

If `use-money-unlock` is enabled, the server's economy/Vault setup becomes part of the research system. Test that path before enabling it for production players.

## Machine ticker settings

Slimefun's regular machine work is controlled under `URID:`:

```yaml
URID:
  info-delay: 3000
  custom-ticker-delay: 10
  enable-tickers: true
```

Increasing ticker delay can reduce how often machine work is processed, but it also slows visible machine behavior. Do not change this as a first response to lag; profile first.

See **[Server Performance](Server-Performance.md)**.

## Cargo and energy network settings

Current network defaults include:

```yaml
networks:
  max-size: 200
  cargo-ticker-delay: 0
  enable-visualizer: true
  delete-excess-items: false
```

### `max-size`

Limits network traversal. Smaller values can constrain very large Cargo/energy installations but may also break networks players already built.

### `cargo-ticker-delay`

Adds delay between Cargo processing cycles. Raising this can lower Cargo work at the cost of slower item movement.

### `delete-excess-items`

The default is conservative: excess items are **not** silently deleted. Be very cautious about changing any setting that intentionally voids items.

## Radiation settings

Current defaults include radiation support and a grace period:

```yaml
options:
  enable-radiation: true
  radiation-update-interval: 1
  radiation-grace-period: 15
  burn-players-when-radioactive: true
```

Changing radiation behavior alters late-game balance and safety. Document changes for players if your server deviates from the normal Slimefun experience.

## Backups and autosave

Important safety-oriented defaults include:

```yaml
options:
  auto-save-delay-in-minutes: 10
  backup-data: true
```

These settings do not replace full server backups. Before upgrading Slimefun core or major addons, still back up worlds, Slimefun data, player data, databases and addon data.

## GPS limits

Current defaults include:

```yaml
options:
  max-gps-waypoints: 21
```

Changing this can affect how many locations players are allowed to maintain in GPS systems.

## Backpack behavior

Current Legacy configuration includes:

```yaml
backpack:
  allow-open-when-owner-offline: true
```

This setting affects administrative/shared ownership scenarios and should be considered alongside your permission and protection model.

## Stability safeguards

Slimefun Legacy adds a `stability:` section for production-server safeguards, including machine circuit breakers, external integration isolation and the Storage/Item Doctor.

Examples include thresholds and cooldowns for repeatedly failing machine callbacks. These systems are designed to isolate pathological failures rather than change normal healthy machine behavior.

Use **[Doctor & Diagnostics](Doctor-and-Diagnostics.md)** before changing safety thresholds just to hide an error.

## Storage & Item Doctor configuration

The `stability.item-doctor` section controls conservative item recovery behavior. The defaults are designed to repair visible presentation data while preserving IDs and functional state.

Do not use Doctor configuration as a generic item editor. Its purpose is recovery of recognized Slimefun items.

## Recommended change process

For production servers:

1. Save a copy of the current config.
2. Change one related group of settings at a time.
3. Restart normally.
4. Review console startup.
5. Test the affected feature as a normal player.
6. Compare performance/behavior to the previous setting.
7. Roll back the config change if the result is worse.

## Related pages

- **[Installation & Upgrades](Installation-and-Upgrades.md)**
- **[Server Owner Guide](Server-Owner-Guide.md)**
- **[Server Performance](Server-Performance.md)**
- **[Doctor & Diagnostics](Doctor-and-Diagnostics.md)**
