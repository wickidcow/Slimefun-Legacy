# Slimefun Legacy 4.1.23 — Core Platform Phase 1E (Development)

## Runtime stability

- Expanded machine circuit-breaker diagnostics with live owner, location, item, cause and retry information.
- Added configurable consecutive-failure threshold and ticker lifecycle log cooldown.
- Added protection around deferred synchronized machine callbacks.
- Rate-limited repeated BlockTicker lifecycle exceptions.
- Added `/sf doctor runtime`.

## External integration foundation

- Added an additive capability/provider API for external inventories, storage, cargo, machines, energy and fluids.
- Added safe Rebar and Pylon detection with no hard dependency.
- Added `/sf doctor integrations`.
- External systems are detection-only until a compatible provider explicitly registers capabilities.

## Compatibility

- The development compatibility baseline remains 4.1.21 until 4.1.22 is published, then it will advance to the pinned 4.1.22 release commit before finalizing 4.1.23.
- Existing public/protected APIs remain available.
- No item IDs, recipes, storage keys, database schemas, or saved-world formats changed.
- Rebar/Pylon support in this segment is a bridge foundation, not an automatic cross-network conversion layer.
