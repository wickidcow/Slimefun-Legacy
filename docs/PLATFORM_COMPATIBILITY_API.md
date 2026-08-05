# Platform Compatibility API

Slimefun Legacy exposes a read-only capability service for addons that need to support multiple Paper-family runtimes, Java versions, or Minecraft release lines.

## Access

```java
PlatformCompatibilityService compatibility = Slimefun.getPlatformCompatibilityService();
PlatformProfile profile = compatibility.getProfile();
```

The service is initialized before normal Slimefun item and listener registration. Addons should read it during or after their own enable sequence.

## Prefer capabilities

Use a capability when the decision depends on an available API:

```java
if (compatibility.supports(PlatformCapability.ASYNC_CHUNK_LOADING)) {
    // Use an asynchronous chunk-loading implementation.
}
```

Use the platform family only when behavior genuinely differs by scheduler ownership or support policy:

```java
if (compatibility.isRegionOwnedExecution()) {
    // Use entity-, location-, or region-owned execution.
}
```

Do not use the family as a substitute for a capability check.

## Declarative addon requirements

An addon can evaluate all startup requirements in one operation:

```java
PlatformRequirements requirements = PlatformRequirements.builder()
        .minimumMinecraftVersion(1, 21, 11)
        .minimumJavaVersion(21)
        .requireCapabilities(
                PlatformCapability.PAPER_API,
                PlatformCapability.DATA_COMPONENT_API)
        .acceptFamilies(
                PlatformFamily.PAPER,
                PlatformFamily.PURPUR,
                PlatformFamily.PAPER_DERIVATIVE)
        .build();

PlatformCompatibilityReport report = compatibility.check(requirements);
if (!report.isCompatible()) {
    getLogger().warning(report.describe());
}
```

The report contains every failed requirement. Empty accepted-family sets mean any family is accepted. Empty capability sets mean no optional capability is required.

## Available profile data

- `getSoftwareName()` — the server-reported software name;
- `getServerVersion()` — the full server build string;
- `getRawMinecraftVersion()` — the Minecraft version reported by the server;
- `getMinecraftVersion()` — an optional `MinecraftVersionNumber`;
- `getJavaFeatureVersion()` — the current Java feature version;
- `getFamily()` — Paper, Purpur, Folia, a Paper derivative, or unknown;
- `getSupportLevel()` — supported, experimental, best effort, unsupported, or unknown;
- `getCapabilities()` — an immutable set of detected runtime capabilities;
- `isPaperCompatible()` — whether the Paper API boundary was detected;
- `isRegionOwnedExecution()` — whether Folia ownership semantics are active.

## Capabilities

| Capability | Meaning |
| --- | --- |
| `PAPER_API` | A Paper-family platform/API surface was detected |
| `REGION_SCHEDULER_API` | The server exposes the region scheduler API |
| `GLOBAL_REGION_SCHEDULER_API` | The server exposes the global-region scheduler API |
| `ASYNC_SCHEDULER_API` | The server exposes the Paper async scheduler API |
| `REGION_OWNED_EXECUTION` | Slimefun is running in Folia region-owned mode |
| `ASYNC_CHUNK_LOADING` | An async chunk-loading method is available |
| `ADVENTURE_COMPONENT_MESSAGES` | Adventure components can be delivered directly to command senders |
| `DATA_COMPONENT_API` | Paper's data-component API is present |
| `PLAYER_PICK_BLOCK_EVENT` | Paper's player pick-block event is present |

Detection is conservative. An absent capability means the addon must use a compatible fallback or disable only the affected optional behavior.

## Minecraft and Java versions

`MinecraftVersionNumber` compares actual numeric components rather than enum positions:

```java
boolean supported = compatibility.isMinecraftVersionAtLeast(1, 21, 11);
boolean oldRelease = compatibility.isMinecraftVersionBefore(1, 21, 11);
boolean modernJava = compatibility.isJavaVersionAtLeast(21);
```

The historical `MinecraftVersion` enum remains available for old addons. New addon code should prefer `MinecraftVersionNumber`, declarative requirements, or the compatibility service.

## Deprecation lifecycle

Addon-facing APIs may use both Java's `@Deprecated` and Slimefun Legacy's `@SlimefunDeprecated` annotation. The latter records the deprecation version, replacement guidance, and an optional earliest removal version. An empty removal version means removal is not scheduled.

Compatibility bridges remain available until a separately documented release explicitly schedules removal through the public API compatibility process.

## Compatibility contract

The API is additive and annotated with `@SlimefunAPI`. New service helpers are default interface methods so implementations compiled against 4.1.19 continue to link. Existing capability names and getter signatures must not be removed without the project's public API compatibility process.

## Compatibility signature guard

The repository stores the 4.1.19 compatibility-protected source surface in `compatibility/api-signatures-4.1.19.txt`. The full Legacy verifier runs `scripts/verify_api_compatibility.py`; new APIs are allowed, but changing or removing an existing public or protected declaration fails validation. Intentional future removals must first follow the documented deprecation lifecycle and update policy rather than silently changing the baseline.
