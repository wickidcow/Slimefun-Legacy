# Paper/Purpur Compatibility Maintenance — Round 2

This maintenance pass modernizes Slimefun Legacy's own Paper, Bukkit and WorldEdit calls while preserving the JVM signatures older addons were compiled against.

## Modernized internals

- Replaced direct construction of Paper's internal `EntityDamageByEntityEvent` with supported `DamageSource` and `LivingEntity.damage(...)` calls.
- Preserved direct and causing entities for Seismic Axe, Stomper Boots and Explosive Bow damage.
- Preserved cancellation-aware custom knockback for Seismic Axe and Explosive Bow.
- Updated WorldEdit block-vector coordinate access from deprecated `getBlockX/Y/Z()` methods to `getX/Y/Z()`.
- Added missing `@Deprecated` annotations to legacy SQL schema constants and translated their documentation to English.
- Enabled Java 25 native access for Gradle SQLite storage tests.

## Addon compatibility retained

The following legacy signatures remain available and are covered by regression tests:

- `BlockTicker.tick(Block, SlimefunItem, Config)`
- `EnergyNetComponent.getCharge(Location, Config)`
- `EnergyNetProvider.getGeneratedOutput(Location, Config)`
- `EnergyNetProvider.willExplode(Location, Config)`
- `BlockStorage.getLocationInfo(Location)` returning the legacy `Config` view

The old CS-CoreLib `Config` class remains deprecated, but it is no longer marked for removal in Slimefun Legacy. New internal code continues to use `ASlimefunDataContainer`, `SlimefunBlockData`, binary inventory storage and the modern scheduler abstractions.

## Validation

Run:

```bash
python3 scripts/verify_legacy.py .
./gradlew spotlessApply clean build --no-daemon
```

The new `verify_compatibility_round2.py` check rejects removal of compatibility bridges and rejects reintroduction of internal Paper damage-event constructors or deprecated WorldEdit vector accessors.
