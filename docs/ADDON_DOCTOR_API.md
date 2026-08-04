# Addon Doctor API

Slimefun Legacy 4.1.17 adds an optional, addon-facing diagnostics service for loaded runtime state.

## Registering a provider

An addon implements `AddonDoctor` and registers it through Bukkit's `ServicesManager`:

```java
AddonDoctor doctor = repair -> new AddonDoctorReport(
    "MyAddon",
    repair,
    scanned,
    issues,
    repaired,
    failures,
    details
);
Bukkit.getServicesManager().register(AddonDoctor.class, doctor, plugin, ServicePriority.Normal);
```

Implementations must run on the server-owned thread, must not force-load chunks, and should only repair state that can be reconstructed safely. Bukkit automatically removes registrations when the providing plugin is disabled; addons may also call `unregisterAll(plugin)` explicitly.

## Commands

- `/sf doctor addons status` lists registered providers.
- `/sf doctor addons scan` performs a dry run.
- `/sf doctor addons repair confirm` runs safe repairs after explicit confirmation.

Provider failures are isolated. One addon throwing an exception does not prevent the remaining addon doctors from completing.

## Cross-core compatibility

This API is optional and unique to Slimefun Legacy. Addons that also target Slimefun United or Slimefun Gugu should isolate registration behind a class-presence check or a reflective bridge. The addon must not directly link this API from classes that load on every supported core.
