# Core Platform Phase 1E — Runtime Stability & External Integration Foundation

Phase 1E is the Slimefun Legacy 4.1.23 runtime hardening and external-compatibility line.

## Part 1 — runtime failure isolation

- Keeps the existing per-location machine circuit breaker and makes its failure threshold configurable.
- Tracks live failure owner, Slimefun item ID, location, cause, retry state and duplicate-report suppression.
- Catches failures that occur inside deferred synchronized machine callbacks, not only failures thrown by the coordinator.
- Rate-limits repeated `BlockTicker.startNewTick()` lifecycle exceptions.
- Adds `/sf doctor runtime` and expands `/sf doctor status` and `/sf stability status`.
- Preserves ticker registrations and stored machine data while a location is paused.

## Part 1 — external integration provider API

- Adds a capability-based provider API for inventory, storage, cargo, machine, energy and fluid bridges.
- Adds guarded runtime detection for Rebar and Pylon without linking either project into Slimefun core.
- Adds `/sf doctor integrations` and a small `/sf versions` summary.
- Detection never implies compatibility: a provider must explicitly expose the capabilities it supports.

## Part 2 — Rebar/Pylon block capability adapters

- Adds built-in reflection-only Rebar and Pylon providers when compatible Rebar runtime classes are present.
- Resolves loaded Rebar blocks through Rebar's runtime block-storage surface without a compile-time Rebar dependency.
- Classifies blocks through documented Rebar marker interfaces for virtual/vanilla inventory, logistics/cargo, processors and fluids.
- Adds an additive block-inspection API so other integrations can expose per-block capability snapshots.
- Adds `/sf doctor integrations probe`; a player can look at a block and see the mapped provider, implementation type, optional content key and capabilities.
- Explicit addon-provided integrations still override Legacy's conservative built-in provider for the same integration ID.
- Reflection failures, missing marker interfaces or a changed Rebar resolver degrade to diagnostics instead of preventing Slimefun startup.

### Part 2 safety boundary

Part 2 is discovery and endpoint mapping, not cross-network item transfer. It does **not** inject Slimefun Cargo into Rebar cargo graphs, mutate Rebar virtual inventories, or claim that Rebar/Pylon machines are Slimefun machines.

Energy exchange remains disabled. Rebar's electricity system is flow/network based and does not have the same semantics as Slimefun's joule buffers, so a conversion bridge requires a separate contract rather than an unsafe unit-only adapter.

This conservative boundary is intentional because Rebar and Pylon are still documented as experimental and their APIs can change between releases.

## Baseline lifecycle

The release-blocking previous-stable baseline remains 4.1.21 during this development segment until a published 4.1.22 GitHub release commit is pinned. Before 4.1.23 is finalized, `previous_stable` should advance to the full 40-character 4.1.22 release commit.

## Compatibility

Phase 1E remains additive. It does not change item IDs, recipes, storage keys, databases, saved-world formats, or remove existing addon API signatures. Rebar and Pylon remain optional runtime integrations.
