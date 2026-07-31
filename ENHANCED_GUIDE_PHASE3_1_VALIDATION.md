# Enhanced Guide Phase 3.1 Validation

## Result

Phase 3.1 passed the available static, configuration, source-shape, patch, and focused compilation checks.

## Checks passed

- Phase 1 native enhanced-guide static verification.
- Phase 2 recipe-fill static verification.
- Phase 3 unordered-machine and Ancient Altar static verification.
- Phase 3.1 FastMachines machine-recipe-browser static verification.
- `enhanced-guide.yml` YAML parsing.
- Machine recipe browsing defaults to enabled.
- FastMachines detection is isolated to the optional `net.guizhanss.fastmachines` package.
- The adapter uses the public `getRecipes()` method.
- Recipe parsing uses public `getInputs()`, `getOutputs()`, and `isDisabledIn(World)` methods.
- Alternative ingredients use public `getChoices()` data.
- Exact ingredient identities use public `getBaseItem()` data.
- No private-field reflection is present.
- No `setAccessible(true)` calls are present.
- The guide button is tagged using a Slimefun-owned persistent-data key.
- Inventory drag and unsafe collection interactions involving the protected button are blocked.
- Browser sessions are removed when the original guide page closes or the player disconnects.
- Recipe lists are filtered for the player's current world.
- Focused Java 21 API-shape compilation passed using strict stubs for the Bukkit, Slimefun, Dough, and ChestMenu methods used by the new browser.
- Incremental patch whitespace validation passed.
- Incremental ZIP overlay validation passed.
- Combined ZIP integrity validation passed.

## Compatibility basis

The current Albion-maintained FastMachines source exposes:

- A public `recipes` property on `BaseFastMachine`, compiled to `getRecipes()`.
- Public recipe `inputs` and `outputs` properties.
- Public `isDisabledIn(World)` filtering.
- Public recipe-choice maps containing exact item wrappers and required amounts.
- Public item-wrapper `baseItem` identities.

The Slimefun Legacy browser accesses those public methods without adding FastMachines as a hard compile-time dependency.

## Build status

A complete Gradle build could not be run in the packaging container because direct GitHub dependency/repository access was unavailable. The previous Phase 3 tree was successfully compiled by the user's GitHub Actions workflow. Phase 3.1 adds one isolated Java class and small registration/configuration calls, and the new class passed focused Java 21 API-shape compilation.

Run the repository's normal GitHub Actions workflow after installing the incremental files. The workflow should run `spotlessApply`, `spotlessCheck`, `clean build`, and tests before uploading the resulting Slimefun artifact.
