# Core Platform Phase 1G — Lifecycle, Runtime and Addon Hardening

Phase 1G modernizes Slimefun Legacy internals while deliberately preserving normal Slimefun gameplay and addon-facing behavior. The work is split into three logical parts but ships together in 4.1.25.

## Part 1 — Core lifecycle and scheduler foundation

- Adds a read-only `CoreLifecycleService` with startup/shutdown state and phase snapshots.
- Makes startup phases observable without reordering the existing initialization sequence.
- Makes shutdown cleanup ordered and failure-isolated: one failing cleanup step is logged but does not prevent later independent cleanup steps.
- Extends `SlimefunScheduler` with additive default methods for quiescing and health snapshots, preserving third-party scheduler implementation compatibility.
- `PaperScheduler` stops accepting new work before task cancellation and reports tracked task count plus region-owned execution mode.
- `ThreadService` now has an explicit shutdown lifecycle and correctly uses the configured fixed-delay period.

## Part 2 — Machine and storage runtime facades

- Adds `MachineRuntimeService` and immutable `MachineRuntimeSnapshot` as stable access points for ticker health and recovery.
- Adds read-only `StorageRuntimeService` and `StorageRuntimeSnapshot` for database/cache health.
- Moves Doctor status/recovery commands onto those facades where possible.
- Does not alter machine recipes, ticker semantics, Cargo, Energy, block storage keys, database schemas, or saved-world formats.

## Part 3 — Addon runtime compatibility and release hardening

- Adds `AddonRuntimeHealthService` to record failures that are already caught at guarded addon callback boundaries.
- Records compatibility-provider failures, addon item-load failures, and third-party integration callback failures for diagnostics.
- Does not automatically disable an addon or change the success path because of telemetry.
- Adds `/sf doctor core` and enriches `/sf doctor compatibility <addon>` with guarded callback health evidence.
- Keeps the 4.1.19 protected API baseline and the Phase 1E normal-core hash guard as release-blocking checks.

## Compatibility boundary

Phase 1G is infrastructure modernization. It does not change normal Slimefun Cargo or Energy semantics, machine processing, recipes, item IDs, research IDs, storage formats, database schemas, or saved-world formats. New addon-facing services are additive. Existing scheduler methods remain intact, and newly added scheduler lifecycle methods have compatibility-preserving defaults.
