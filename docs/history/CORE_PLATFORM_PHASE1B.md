# Core Platform Phase 1B

## Goal

Make the 4.1.19 capability foundation the single compatibility boundary used by Slimefun core and future addons, while retaining every existing public entry point required by current addons.

## Delivered

### Declarative addon requirements

`PlatformRequirements` and `PlatformCompatibilityReport` let an addon describe minimum Minecraft and Java versions, required runtime capabilities, and accepted platform families. The platform service returns every unmet requirement rather than failing on the first one.

### Additive service helpers

All new `PlatformCompatibilityService` methods are Java default methods. A third-party service implementation compiled against 4.1.19 continues to link because no new abstract interface method was introduced.

### Central runtime detector

`RuntimePlatformDetector` owns implementation-class, method, and Paper-family probes. Core code no longer repeats PaperLib, Folia class, or Bukkit server-version checks.

### Scheduler routing

`PaperScheduler` consumes the platform service in normal Slimefun startup. The original constructor remains, with detector fallback, so direct legacy construction remains source and binary compatible.

### Deprecation lifecycle

`@SlimefunDeprecated` supplements Java `@Deprecated` with a version, replacement, and optional earliest removal version. A deprecated API is not scheduled for removal unless `removalVersion` is explicitly populated.

### API signature baseline

`compatibility/api-signatures-4.1.19.txt` records all 991 public and protected declarations in compatibility-protected packages. `verify_api_compatibility.py` allows additive APIs but fails when an existing declaration is removed, moved, renamed, or changed.

## Retained bridges

- `FoliaSupport` and `FoliaSupport.isFolia()`
- `PaperScheduler(Plugin)`
- all existing `SlimefunScheduler` methods
- the historical `MinecraftVersion` enum
- existing `Slimefun.getPlatformCompatibilityService()` and `getSchedulerService()` accessors

## Non-goals

- no module toggles;
- no item, recipe, or guide redesign;
- no storage or database migration;
- no automatic upstream code merge;
- no claim that an addon is Folia-safe merely because Slimefun core is running on Folia.

## Next phase

Phase 1C should inventory version-gated gameplay code and replace only genuine API-availability decisions with named capabilities. Historical gameplay differences should remain explicit Minecraft-version behavior rather than being incorrectly generalized as platform capabilities.
