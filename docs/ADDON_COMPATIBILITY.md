# Addon compatibility declarations

Slimefun Legacy 4.1.21 adds an optional compatibility declaration system. Existing addons do not need to change: an addon with no declaration remains loadable and is reported as **Undeclared**, not disabled.

Declarations can be supplied in three ways, in priority order:

1. Register an `AddonCompatibilityDeclaration` with `Slimefun.getAddonCompatibilityService()`.
2. Implement `AddonCompatibilityProvider` in the addon plugin class.
3. Place `slimefun-compatibility.json` at the root of the addon JAR.

## Embedded manifest

```json
{
  "schema": 1,
  "tested_core_variants": ["legacy", "gugu", "united"],
  "minimum_minecraft": "1.21.11",
  "minimum_java": 21,
  "required_capabilities": ["PAPER_API", "ASYNC_CHUNK_LOADING"],
  "accepted_platform_families": ["PAPER", "PURPUR", "PAPER_DERIVATIVE"],
  "required_plugins": ["InfinityExpansion2"],
  "optional_plugins": ["Networks"],
  "notes": "The Networks bridge is optional."
}
```

The JSON schema is available at `docs/addon-compatibility-manifest.schema.json`.

Missing required capabilities or plugins produce an **Incompatible** diagnostic. An untested core produces a **Warning**. Missing optional plugins and declaration notes are informational and do not downgrade an otherwise compatible addon.

## Java registration

```java
PlatformRequirements requirements = PlatformRequirements.builder()
    .minimumJavaVersion(21)
    .minimumMinecraftVersion(1, 21, 11)
    .requireCapability(PlatformCapability.PAPER_API)
    .build();

AddonCompatibilityDeclaration declaration = AddonCompatibilityDeclaration.builder()
    .testCores(SlimefunCoreVariant.LEGACY, SlimefunCoreVariant.GUGU)
    .platformRequirements(requirements)
    .optionalPlugin("Networks")
    .build();

Slimefun.getAddonCompatibilityService().register(this, declaration);
```

Registration is additive. Existing `SlimefunAddon`, scheduler, machine, item, storage, and historical compatibility APIs remain available.

## Optional integrations

Use `Slimefun.getOptionalDependencyService()` for centralized plugin discovery, class lookup, public-method lookup, and guarded reflective invocation. The service catches linkage failures at the integration boundary and returns a `CompatibilityInvocation` instead of requiring each addon to implement its own reflection wrapper.

## Diagnostics

- `/sf versions` appends the compatibility status to installed Slimefun addons.
- `/sf doctor compatibility` prints the full runtime compatibility summary and per-addon messages.
- Startup prints an aggregate result and logs incompatible declarations without automatically disabling addons.

## CI matrix

`compatibility/addon-compatibility-matrix.json` is the single source of truth for source-build probes. Required entries block releases on a candidate-only regression. Advisory entries remain visible but do not block a release when an independent repository is unavailable or has changed its own build.

The comparison harness now performs both:

- a clean source build against the known-good 4.1.15 baseline and the candidate core; and
- binary-linkage analysis of the baseline-compiled addon against the candidate JAR.

The linkage pass detects missing referenced Slimefun classes, methods, and fields, including inherited members across the compatibility-protected namespaces.
