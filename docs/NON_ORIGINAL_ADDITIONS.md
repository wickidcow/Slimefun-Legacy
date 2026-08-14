# Non-original Slimefun additions

Slimefun Legacy keeps fork-specific gameplay additions opt-in.

By default, `options.enable-non-original-slimefun-additions` is `false` in `config.yml`.

- `false`: only the original/core Slimefun content set registers. Adventurer's Curios and future Legacy-only gameplay additions stay disabled.
- `true`: enables Adventurer's Curios and is the master opt-in for future Slimefun Legacy-only gameplay additions.

Changing this setting requires a server restart because Slimefun items and item groups are registered during startup.
