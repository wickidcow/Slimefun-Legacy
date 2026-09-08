# Addon Doctor API

Slimefun Legacy provides optional addon-facing diagnostics services for loaded runtime state. The original `AddonDoctor` service remains available for general addon diagnostics and safe repair. Slimefun Legacy 4.1.47 also adds `LegacyItemMigrationProvider` for guarded addon-owned legacy item-ID migrations.

## Registering an AddonDoctor provider

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

## General Doctor commands

- `/sf doctor addons status` lists registered providers.
- `/sf doctor addons scan` performs a dry run.
- `/sf doctor addons repair confirm` runs safe repairs after explicit confirmation.

Provider failures are isolated. One addon throwing an exception does not prevent the remaining addon doctors from completing.

## Legacy item migration providers

A migration-aware addon may implement `LegacyItemMigrationProvider` and register it with Bukkit's `ServicesManager`. The provider owns the persistence-specific migration logic; Slimefun core only validates and delegates the operation.

```java
LegacyItemMigrationProvider migrations = new LegacyItemMigrationProvider() {
    @Override
    public String getMigrationName() {
        return "MyAddon legacy IDs";
    }

    @Override
    public Map<String, String> getLegacyItemMappings() {
        return Map.of("OLD_ITEM_ID", "CURRENT_ITEM_ID");
    }

    @Override
    public AddonDoctorReport runMigration(boolean repair) {
        return scanAndOptionallyMigrateOwnedState(repair);
    }
};

Bukkit.getServicesManager().register(
    LegacyItemMigrationProvider.class,
    migrations,
    plugin,
    ServicePriority.Normal
);
```

The mappings returned by `getLegacyItemMappings()` must agree with the mappings the addon publishes through `SlimefunRegistry#registerLegacySlimefunItemId`. Slimefun Legacy validates that agreement before a repair pass is allowed.

Migration providers must not force-load chunks solely to perform a migration. A repair pass should only mutate state in the provider's supported loaded scope. The provider is responsible for understanding its own item metadata, block records, menus and persistent storage; Slimefun core does not infer or rewrite addon-specific persistence formats.

## Migration commands and safety gate

- `/sf doctor migrations status` summarizes registered legacy mappings and providers.
- `/sf doctor migrations list [page]` lists declared legacy-to-current ID replacements.
- `/sf doctor migrations unknown` correlates sampled unknown IDs from Item Doctor with declared mappings.
- `/sf doctor migrations plan` produces a generic read-only migration plan from the latest Item Doctor evidence.
- `/sf doctor migrations providers` lists enabled migration providers and validation state.
- `/sf doctor migrations scan <plugin>` runs a provider read-only scan and, when clean, creates a short-lived execution fingerprint.
- `/sf doctor migrations execute <plugin> <fingerprint>` delegates the repair pass only when the prepared fingerprint is still valid.

Prepared provider plans expire after 10 minutes. Missing targets, unsafe or conflicting mappings, provider scan failures, expired plans and incorrect fingerprints fail closed. A new clean scan is required before execution can proceed again. Operators should make an offline backup before executing an addon-owned migration.

## Cross-core compatibility

These APIs are optional and unique to Slimefun Legacy. Addons that also target Original Slimefun, Slimefun United or Slimefun Gugu should isolate registration behind a class-presence check or a reflective bridge. The addon must not directly link Legacy-only diagnostics APIs from classes that load on every supported core.
