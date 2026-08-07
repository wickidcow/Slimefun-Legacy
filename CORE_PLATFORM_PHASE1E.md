# Core Platform Phase 1E — Runtime Stability & External Integration Foundation

Phase 1E is the Slimefun Legacy 4.1.23 runtime-hardening line. Its rule is simple: successful normal Slimefun behavior stays on the existing path; only failing optional callbacks are isolated.

## Part 1 — machine failure isolation

- Keeps the existing per-location machine circuit breaker and makes its failure threshold configurable.
- Tracks live failure owner, Slimefun item ID, location, cause, retry state and duplicate-report suppression.
- Catches failures inside deferred synchronized machine callbacks, not only failures thrown by the coordinator.
- Rate-limits repeated `BlockTicker.startNewTick()` lifecycle exceptions.
- Preserves ticker registrations and stored machine data while a failing location is paused.
- Adds `/sf doctor runtime` diagnostics.

## Part 2 — Rebar/Pylon adapter foundation

- Adds a capability-based provider API for inventory, storage, cargo, machine, energy and fluid bridges.
- Adds reflection-only Rebar/Pylon block discovery with no compile-time dependency.
- Classifies loaded external blocks conservatively as inventory/storage, cargo/logistics, machine/processor and fluid endpoints.
- Adds `/sf doctor integrations probe` for targeted block capability inspection.
- Does not enable cross-network cargo transfer or Rebar/Pylon energy exchange.

## Part 3 — guarded recovery and compatibility protection

- Adds circuit-breaker isolation for failing external provider status and block-inspection callbacks.
- Tracks provider, operation, cause, retry state and duplicate-report suppression without affecting normal Slimefun processing.
- Adds `/sf doctor runtime retry`, `/sf doctor runtime retry all`, `/sf doctor integrations retry <id|all>` and `/sf doctor integrations reload`.
- Adds explicit external-adapter failure thresholds/cooldowns in `config.yml`.
- Adds a hash guard for the green Part 2 versions of normal Slimefun Cargo, Energy, NetworkManager, Guide, `SlimefunItem`, `BlockTicker`, `AContainer` and `TickerTask` code. Part 3 fails verification if those normal core paths change.
- Keeps the 991-signature compatibility baseline as a release gate.

## Part 3.1 — `/sf versions` operator clarity

- Replaces raw runtime enum-style labels with plain-language compatibility results.
- Shows `✔ Compatible`, `⚠ Compatible with warnings`, `? Compatibility not verified`, `✕ Incompatible`, or `✕ Disabled` for every detected addon.
- Explains that an undeclared addon is loaded but not runtime-verified instead of presenting the internal `Undeclared` state without context.
- Keeps declaration source and detailed reasons available in hover text.
- Does not change addon loading, compatibility decisions, Cargo, Energy, machine processing, saved data, or any protected API signature.

## Normal Slimefun compatibility guarantee for Part 3

Part 3 does **not** modify Slimefun CargoNet, EnergyNet, NetworkManager, SlimefunGuide, SlimefunItem, BlockTicker, AContainer or the already-green Part 1 TickerTask. Healthy core machines, cargo networks, energy networks and addons continue using their existing execution paths.

The new failure/retry methods on the Phase 1E external integration service are Java default methods, so existing implementations do not need to recompile just to satisfy the new API surface.

## Rebar/Pylon safety boundary

Rebar/Pylon remain optional. Detection never implies interoperability. Slimefun does not automatically inject items into Rebar cargo networks and does not exchange energy with Rebar/Pylon unless a future adapter implements proven-compatible semantics.

## Compatibility

Phase 1E is additive. It does not change item IDs, recipes, storage keys, databases, saved-world formats, normal Cargo/Energy behavior, or compatibility-protected addon API signatures.
