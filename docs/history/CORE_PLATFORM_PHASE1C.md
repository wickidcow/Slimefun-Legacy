# Core Platform Phase 1C

Phase 1C turns the Phase 1A/1B platform foundation into addon-facing update infrastructure while retaining the complete existing addon API.

## Runtime compatibility layer

- Additive compatibility declarations through Java registration, a provider interface, or an embedded JSON manifest.
- Runtime evaluation of tested Slimefun cores, platform requirements, required plugins, and optional integrations.
- Central optional-dependency discovery and guarded reflection.
- `/sf doctor compatibility`, startup summaries, and addon status in `/sf versions`.
- Undeclared legacy addons remain loadable. Diagnostics do not automatically disable plugins.

## Release compatibility gates

- Machine-readable core API registry and addon matrix.
- Dynamically generated GitHub Actions matrix rather than duplicated workflow YAML entries.
- Baseline/candidate source-build comparison.
- Precompiled-addon JVM linkage analysis for missing Slimefun classes, methods, and fields.
- Required and advisory tiers with explicit release behavior.
- Synthetic linkage-checker verification and permanent Phase 1C static invariants.

## Compatibility promise

Phase 1C is additive. It does not rename or remove existing APIs, item IDs, recipes, storage keys, database structures, saved-world formats, or scheduler bridges. The 4.1.19 public/protected API baseline remains enforced.
