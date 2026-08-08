# Slimefun Legacy 4.1.21 — Core Platform Phase 1C

## Addon compatibility infrastructure

- Added addon compatibility declarations through explicit registration, a provider interface, or `slimefun-compatibility.json`.
- Added runtime diagnostics for tested core variants, platform requirements, required dependencies, and optional integrations.
- Added a centralized optional-dependency and guarded-reflection service.
- Added `/sf doctor compatibility` and compatibility status details to `/sf versions`.
- Kept undeclared addons loadable and treated inactive optional integrations as informational.

## Release gates

- Added a machine-readable representative addon matrix and core API registry.
- Added dynamic GitHub Actions matrix generation.
- Extended addon comparison to verify both source compilation and precompiled binary linkage.
- Added missing-class, missing-method, and missing-field detection across compatibility-protected Slimefun namespaces.
- Added permanent Phase 1C verification and synthetic linkage regression tests.

## Compatibility

- Existing addon APIs remain available.
- No item IDs, recipes, storage keys, database schemas, saved-world formats, or gameplay behavior changed.
- Required compatibility targets block candidate-only regressions; independently maintained probes remain advisory.
