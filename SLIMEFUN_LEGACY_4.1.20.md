# Slimefun Legacy 4.1.20 — Core Platform Phase 1B

This release continues the compatibility-first core modernization started in 4.1.19. It does not remove or rename existing addon APIs and does not change Slimefun items, recipes, storage, databases, or gameplay.

## Addon-facing compatibility requirements

Addons can now describe what they need and let Slimefun evaluate the running server:

```java
PlatformRequirements requirements = PlatformRequirements.builder()
        .minimumMinecraftVersion(1, 21, 11)
        .minimumJavaVersion(21)
        .requireCapability(PlatformCapability.PAPER_API)
        .acceptFamilies(PlatformFamily.PAPER, PlatformFamily.PURPUR)
        .build();

PlatformCompatibilityReport report =
        Slimefun.getPlatformCompatibilityService().check(requirements);

if (!report.isCompatible()) {
    getLogger().warning(report.describe());
}
```

The compatibility service also provides additive default helpers for Minecraft version comparisons, Java version checks, Paper compatibility, platform family checks, and Folia region-owned execution. Default methods preserve binary compatibility with any third-party implementation compiled against 4.1.19.

## Centralized runtime detection

Paper, Purpur, Folia, scheduler, Adventure, data-component, async chunk-loading, and player pick-block event probes now pass through one internal detector. Startup validation, scheduler routing, error reports, guide diagnostics, event threading, and optional Paper behavior consume that shared result instead of repeating PaperLib, server-name, or implementation-class checks.

The scheduler no longer freezes Folia detection in a static constant. The normal Slimefun instance injects the initialized platform service, while the original `PaperScheduler(Plugin)` constructor remains available as a compatibility bridge.

## API deprecation lifecycle

The new `@SlimefunDeprecated` annotation records:

- the first Legacy version that deprecated an API;
- the recommended replacement;
- an optional earliest removal version.

An empty removal version means removal is not scheduled. Java's normal `@Deprecated` annotation remains required. `FoliaSupport` is the first documented bridge: it remains callable, but new code should use the platform compatibility service or scheduler service.

## Compatibility guarantees

- Existing addon APIs remain present.
- `FoliaSupport.isFolia()` remains present.
- `PaperScheduler(Plugin)` remains present.
- The historical `MinecraftVersion` enum remains present.
- Existing scheduler interfaces and task handles remain present.
- No data format or gameplay behavior changes are included.

## Verification

Phase 1B adds permanent static checks that prevent new direct PaperLib checks, direct Paper/Folia implementation probes outside the detector, and region-scheduler calls outside the scheduler implementation. A checked-in 4.1.19 signature baseline verifies that all 991 compatibility-protected public and protected declarations remain present while additive APIs are allowed. Unit tests cover declarative requirements, complete incompatibility reporting, immutable results, and compatibility-service default methods.
