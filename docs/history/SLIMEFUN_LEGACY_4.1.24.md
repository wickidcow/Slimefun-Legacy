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

## Phase 1F Part 2 — compatibility evidence

- Adds `/sf doctor compatibility` evidence reporting with registry tier, declaration source, runtime load state, active
  machine-failure state, and safe compatibility-layer linkage signals.
- Adds a `Recognized` tier for addon families Legacy can identify but does not currently CI monitor.
- Adds recognition aliases for Better Farming, DankTech2, Cultivation, Electric Spawners, ExtraTools,
  GeneticChickengineering, HotbarPets, Magic 8 Ball, MobCapturer, SFMobDrops, SlimefunAdvancements, SlimeGlue,
  SimpleMaterialGenerators, and SoulJars.
- Recognition does not mark an addon compatible. Undeclared addons remain undeclared at the public API layer.

## Phase 1F Part 2.1 — compact version report

- `/sf versions` now keeps each addon to a compact `Name version — Status` line.
- Status output uses short words only: `Compatible`, `Known`, `Recognized`, `Warning`, `Unknown`, `Incompatible`, or `Disabled`.
- Hovering the status shows the full compatibility evidence and reason.
- Long custom build/version labels are shortened only on-screen; hovering the version shows the full exact build string.
