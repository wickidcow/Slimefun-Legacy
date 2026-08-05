# Slimefun Legacy Core Platform Phase 1A

Core Platform Phase 1A returns development to the core compatibility and maintenance architecture. It creates a stable boundary for future Minecraft, Paper, Purpur, Folia, addon, and upstream-fork work without changing Slimefun item IDs, recipes, stored data, database schemas, or normal gameplay.

## Goals

- Make future Minecraft and server-platform updates require changes in one compatibility layer instead of scattered version checks.
- Give addons a supported way to ask what the current runtime can do.
- Track useful changes from Original Slimefun, Gugu, Slimefun5, Slimefun United, and Slimefun4Core without automatically importing unsafe code.
- Preserve the established Slimefun 4 addon API and production data.
- Keep Paper primary, Purpur supported, conventional Paper derivatives best-effort, and Folia experimental.

## Capability-based platform API

The new `PlatformCompatibilityService` is available through:

```java
PlatformCompatibilityService compatibility = Slimefun.getPlatformCompatibilityService();
```

Addons can check concrete runtime capabilities:

```java
if (compatibility.supports(PlatformCapability.DATA_COMPONENT_API)) {
    // Use the modern Paper data-component path.
} else {
    // Retain the compatible legacy path.
}
```

This is safer than checking `Bukkit.getName()`, comparing implementation package names, or assuming that every server with the same Minecraft version exposes the same APIs.

The immutable `PlatformProfile` reports:

- detected platform family;
- support level;
- raw and parsed Minecraft version;
- Java feature version;
- detected scheduler, Adventure, chunk-loading, and data-component capabilities.

`/sf versions` now includes this profile and capability inventory so addon bug reports can include the exact compatibility environment.

## Version parsing

`MinecraftVersionNumber` is a new semantic numeric version type. It parses normal release and pre-release identifiers such as `1.21.11`, `26.1`, and `1.21.2-pre2` without depending on enum ordering. Snapshot names are not guessed.

The historical `MinecraftVersion` enum remains intact for addon binary compatibility. Core startup maps the centrally parsed numeric version back to that enum only where the legacy API still requires it.

## Multi-fork upstream intake

`compatibility/upstream-sources.json` records every reviewed source and its allowed role:

| Source | Role | Intake policy |
| --- | --- | --- |
| Original Slimefun 4 | Historical API and data baseline | Compatibility reference |
| Slimefun Gugu | Maintained code upstream | Existing guarded merge workflow |
| Slimefun5 | Modern compatibility reference | Selective ports only |
| Slimefun United | Feature and architecture reference | Selective ports only |
| Slimefun4Core | Experimental design reference | Design review until independently proven |

`scripts/check_upstream_candidates.py` validates this registry and can query each configured GitHub branch. The weekly **Upstream Candidate Radar** workflow publishes an advisory report. It never merges, downloads, or replaces source files.

Candidate ideas are recorded in `compatibility/core-feature-backlog.json`. A candidate cannot become active merely by editing the manifest; it still requires implementation, compatibility review, tests, and release documentation.

## Feature candidates retained for later phases

Phase 1A records, but does not yet activate:

- a formal API deprecation and migration lifecycle;
- capability-routed scheduler and event bridges;
- optional core module dependencies and feature toggles;
- unified localization keys with guaranteed English fallback;
- deeper storage consistency and migration audits;
- optional staff multitool behavior from Slimefun United.

Core safety work comes before gameplay ports. This prevents feature imports from making later Paper, Purpur, Folia, database, or addon updates harder.

## Compatibility guarantees

This phase intentionally makes no changes to:

- Slimefun item IDs;
- research IDs;
- recipe definitions;
- block-storage keys;
- backpack or player-profile formats;
- database schema;
- Cargo or energy behavior;
- normal guide organization or controls.

Existing public APIs are retained. The new platform API is additive.

## Validation

Run the complete source verification:

```bash
python3 scripts/verify_legacy.py .
```

Run the Phase 1A verifier directly:

```bash
python3 scripts/verify_core_platform_phase1a.py .
```

Validate the upstream registry without network access:

```bash
python3 scripts/check_upstream_candidates.py --offline
```
