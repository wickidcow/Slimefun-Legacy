# Slimefun Legacy 4.1.23 — Core Platform Phase 1E (Development)

## Part 1 — runtime stability

- Expanded machine circuit-breaker diagnostics with live owner, location, item, cause and retry information.
- Added configurable consecutive-failure threshold and ticker lifecycle log cooldown.
- Added protection around deferred synchronized machine callbacks.
- Rate-limited repeated BlockTicker lifecycle exceptions.
- Added `/sf doctor runtime`.

## Part 1 — external integration foundation

- Added an additive capability/provider API for external inventories, storage, cargo, machines, energy and fluids.
- Added safe Rebar and Pylon detection with no hard dependency.
- Added `/sf doctor integrations`.

## Part 2 — Rebar/Pylon adapters

- Added reflection-only built-in Rebar and Pylon block capability adapters.
- Added safe loaded-block resolution through Rebar's block-storage API surface.
- Added per-block inventory/storage, cargo/logistics, machine/processor and fluid capability mapping using Rebar marker interfaces.
- Added an additive `ExternalBlockIntegration` API and provider/service block inspection hooks.
- Added `/sf doctor integrations probe` for targeted block diagnostics.
- Explicit third-party providers can override the built-in adapter for the same integration ID.
- Incompatible or changed Rebar APIs fail closed: the adapter disables its probe instead of breaking Slimefun.

## Deliberate limits

- No automatic Slimefun Cargo ↔ Rebar Cargo item transfer is enabled yet.
- No direct mutation of Rebar virtual inventories is performed.
- No Rebar/Pylon electricity conversion is enabled; the two energy models require a separate safe interoperability contract.
- Rebar and Pylon remain optional and are never hard-linked into Slimefun core.

## Compatibility

- The development compatibility baseline remains 4.1.21 until a published 4.1.22 release commit is pinned, then it will advance before finalizing 4.1.23.
- Existing public/protected APIs remain available; Part 2 only adds APIs.
- No item IDs, recipes, storage keys, database schemas, or saved-world formats changed.
