# Slimefun Legacy 4.1.25 — Core Platform Phase 1G (Development)

## Core lifecycle and scheduler foundation

- Adds observable startup/shutdown lifecycle state and phases.
- Adds ordered shutdown failure isolation so one cleanup failure cannot prevent unrelated later cleanup work.
- Adds scheduler quiesce/health reporting while retaining all existing scheduler APIs.
- Explicitly shuts down Slimefun-owned executor pools during plugin shutdown.
- Keeps Paper and experimental Folia scheduling behind the existing centralized scheduler abstraction.

## Machine and storage runtime modernization

- Adds stable machine-runtime and read-only storage-runtime facades.
- Adds immutable runtime snapshots for diagnostics and future core modernization.
- Moves Doctor health/recovery reporting toward these facades instead of exposing implementation details.
- Does not change machine processing, storage schemas, saved data, Cargo, or Energy behavior.

## Addon runtime hardening

- Adds non-invasive addon callback health telemetry for failures already contained by Slimefun.
- Tracks guarded compatibility-provider, addon item-load, and third-party integration callback failures.
- Adds `/sf doctor core` for lifecycle, scheduler, machine, storage, and addon callback health.
- Adds callback-health evidence to focused `/sf doctor compatibility <addon>` reports.
- No addon is automatically disabled by this telemetry.

## Compatibility guarantees

- The 991 compatibility-protected 4.1.19 API signatures remain release-gated.
- Phase 1E still hash-protects normal Cargo, Energy, NetworkManager, Guide, `SlimefunItem`, `BlockTicker`, `AContainer`, and `TickerTask` implementations.
- No item IDs, recipes, research IDs, storage keys, database schemas, saved-world formats, or normal gameplay semantics are intentionally changed.
