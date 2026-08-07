# Core Platform Phase 1E — Runtime Stability & External Integration Foundation

Phase 1E begins the 4.1.23 runtime hardening line.

## Runtime failure isolation

- Keeps the existing per-location machine circuit breaker and makes its failure threshold configurable.
- Tracks live failure owner, Slimefun item ID, location, cause, retry state and duplicate-report suppression.
- Catches failures that occur inside deferred synchronized machine callbacks, not only failures thrown by the coordinator.
- Rate-limits repeated `BlockTicker.startNewTick()` lifecycle exceptions.
- Adds `/sf doctor runtime` and expands `/sf doctor status` and `/sf stability status`.
- Preserves ticker registrations and stored machine data while a location is paused.

## External integration foundation

- Adds a capability-based provider API for inventory, storage, cargo, machine, energy and fluid bridges.
- Adds guarded runtime detection for Rebar and Pylon without linking either project into Slimefun core.
- Adds `/sf doctor integrations` and a small `/sf versions` summary.
- Detection never implies compatibility: an adapter must explicitly register the capabilities it actually supports.

Rebar/Pylon are intentionally not directly adapted in this first Phase 1E segment because their public documentation describes Rebar as experimental and warns that versions may be mutually incompatible. Pylon is itself a Rebar addon and its cargo/electric models are separate from Slimefun's. The new provider boundary lets a version-pinned adapter be added without destabilizing core.

## Baseline lifecycle

The release-blocking previous-stable baseline remains 4.1.21 during this development segment because 4.1.22 has not yet been published as a GitHub release. After 4.1.22 is released, Phase 1E should advance `previous_stable` to the full 40-character 4.1.22 release commit before 4.1.23 is finalized.

## Compatibility

This phase is additive. It does not change item IDs, recipes, storage keys, databases, saved-world formats, or existing addon API signatures.
