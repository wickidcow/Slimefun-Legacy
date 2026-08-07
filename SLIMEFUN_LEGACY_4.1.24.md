# Slimefun Legacy 4.1.24 — Core Platform Phase 1F (Development)

## Better addon compatibility reporting

- `/sf versions` now distinguishes declared compatibility, known CI-monitored addon families, compatibility warnings, unknown addon compatibility, disabled addons, and declared incompatibilities.
- Common addon runtime names such as Networks, InfinityExpansion2, MagicExpansion, Supreme, DynaTech, FluffyMachines, FoxyMachines, FlowerPower, IDreamOfEasy, Gastronomicon, Bump, SlimeCustomizer, EMCTech, BetterChests, NetworksExpansion, FastMachines, and SlimeTinker are recognized through the same addon families monitored by Legacy CI.
- CI-monitored addons are not falsely promoted to exact-build compatibility. The report explicitly explains that the installed JAR may differ from the build tested by CI.
- Unknown addons are described as Slimefun addons with unknown compatibility rather than as unrecognized/broken.
- Addon lines are sorted alphabetically for easier server audits.

## Compatibility

- Existing 991 compatibility-protected API signatures remain unchanged.
- Existing Phase 1E runtime protection and Rebar/Pylon discovery remain intact.
- No item IDs, recipes, research IDs, storage keys, database schemas, saved-world formats, Cargo behavior, Energy behavior, machine behavior, or guide behavior are changed by this update.
