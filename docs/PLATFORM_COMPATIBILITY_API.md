# Platform Compatibility API

Slimefun Legacy exposes a read-only capability service for addons that need to support multiple Paper-family runtimes or Minecraft release lines.

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
if (profile.getFamily() == PlatformFamily.FOLIA) {
    // Use entity-, location-, or region-owned execution.
}
```

Do not use the family as a substitute for a capability check.

## Available profile data

- `getSoftwareName()` — the server-reported software name;
- `getServerVersion()` — the full server build string;
- `getRawMinecraftVersion()` — the Minecraft version reported by the server;
- `getMinecraftVersion()` — an optional `MinecraftVersionNumber`;
- `getJavaFeatureVersion()` — the current Java feature version;
- `getFamily()` — Paper, Purpur, Folia, a Paper derivative, or unknown;
- `getSupportLevel()` — supported, experimental, best effort, unsupported, or unknown;
- `getCapabilities()` — an immutable set of detected runtime capabilities.

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

Detection is conservative. An absent capability means the addon must use a compatible fallback or disable only the affected optional behavior.

## Minecraft versions

`MinecraftVersionNumber` compares actual numeric components rather than enum positions:

```java
boolean supported = compatibility.isMinecraftVersionAtLeast(1, 21, 11);
```

The historical `MinecraftVersion` enum remains available for old addons. New addon code should prefer `MinecraftVersionNumber` or the service method.

## Compatibility contract

The API is additive and annotated with `@SlimefunAPI`. New capabilities may be added. Existing capability names and getter signatures should not be removed without the project's public API compatibility process.
