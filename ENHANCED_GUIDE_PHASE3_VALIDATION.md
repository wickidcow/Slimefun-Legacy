# Enhanced Guide Phase 3 validation

## Static checks completed

- Phase 1 menu, search and bookmark implementation remains present.
- Phase 2 shaped dispenser filling and its button protections remain present.
- Grind Stone, Smeltery, Ore Crusher, Compressor and Pressure Chamber are explicitly classified as unordered dispenser recipes.
- Unordered targets are resolved through the registered core `MultiBlockMachine` structure, supported material tags and required dispenser direction.
- Unordered transfers aggregate repeated requirements, reject unrelated dispenser contents and commit only after a complete cloned plan succeeds.
- Ancient Altar recipe validation requires the center catalyst and all eight outer ingredients.
- The altar recipe-to-pedestal slot order and all eight core pedestal offsets are explicitly represented.
- Altar and pedestal identities are verified through Slimefun block storage rather than material alone.
- The altar, all pedestals, region ownership, protection access, overhead clearance and existing displayed items are checked before placement.
- Pedestal entities use the Ancient Pedestal item-spawn reason, armor-stand carrier, no-pickup marker and actual source item metadata.
- Partial altar placement is rolled back and the original player inventory is restored after a failed commit where possible.
- The altar activity lock derives its duration from the configured 36-step ritual animation and altar step delay.
- Button lore supports a configurable abbreviated missing list; right-click produces a complete ingredient report.
- Sub-recipe hints identify craftable Slimefun ingredients without executing recursive crafting.
- Phase 3 defaults parse successfully from `enhanced-guide.yml` and can independently disable unordered machines, altar preparation, catalyst handling and sub-recipe hints.
- Java source syntax parsing, brace checks, ZIP integrity, patch application and exact patched-tree comparison are part of packaging validation.

## Required build and staging checks

A complete Gradle build still must run through the repository's normal GitHub Actions workflow. Test the resulting JAR on a copied Paper server before production. Specifically test every supported multiblock orientation, repeated ingredients, partially filled dispensers, full inventories, custom item metadata, protected claims, Folia region boundaries, all eight altar pedestal positions, occupied pedestals, obstructed pedestals, catalyst hotbar displacement, failed altar activation and non-default altar step delay.

## Full-build limitation in this workspace

The complete repository was not available locally, and a final clone attempt could not resolve `github.com` from the isolated container. Therefore this package does not claim a successful full Gradle compilation. GitHub Actions must still run `spotlessApply`, `spotlessCheck` and `clean build` before the resulting JAR is installed.
