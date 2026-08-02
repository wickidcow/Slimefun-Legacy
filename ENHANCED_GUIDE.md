# Slimefun Legacy Native Enhanced Guide — Phases 1–4.1B-A

This package replaces the default survival and cheat guide registrations with native enhanced implementations designed to feel familiar to JustEnoughGuide users while remaining inside Slimefun Legacy's normal guide API.

## Phase 1 — Guide experience

- JEG-style six-row category and item layouts.
- Configurable character-based menu formats.
- Paged smart search instead of the classic 35-result cap.
- Search by item name, Slimefun ID, addon, category, recipe type and lore.
- Search filters: `id:`, `addon:`, `group:` and `recipe:`.
- Persistent per-player bookmarks stored by Slimefun item ID.
- Item lore showing category, addon and Slimefun ID.
- Existing research, permissions, history, cheat-item and native recipe rendering behavior retained.

## Phase 2 — Shaped recipe preparation

- Transactional guide-to-dispenser filling for Enhanced Crafting Table, Magic Workbench and Armor Forge.
- Left-click prepares one recipe; shift-left-click prepares the maximum complete sets that fit.
- Machine-specific ingredient matching, backpack compatibility, protection checks and Folia ownership checks.
- No direct crafting and no nearby-storage access.

## Phase 3 — Advanced preparation and reports

- Unordered filling for Grind Stone, Smeltery, Ore Crusher, Compressor and Pressure Chamber.
- Ancient Altar pedestal preparation with optional safe catalyst selection.
- Full right-click ingredient reports and abbreviated missing-item button lore.
- Slimefun sub-recipe hints without recursive execution.
- Transactional planning, rollback and conservative altar ritual locking.

## Phase 4 — Universal machine recipe browsing

- Addon-facing `MachineRecipeProvider` registry and normalized recipe model.
- Direct integration through `MachineRecipeDisplayItem`.
- Automatic structured browsing for core and addon `AContainer` machines.
- Compatibility discovery for common public recipe methods and fields, including `getMachineRecipes()`, `getRecipeProcess()`, `getRecipes()`, `machineRecipes` and `recipes`.
- Supreme-style recipe objects are normalized through public input/output arrays, numbered getters, processing-time getters and chance metadata.
- Existing `RecipeDisplayItem` recipes receive the same paged browser.
- FastMachines support moved into the provider system while retaining alternatives and world filters.
- Optional processing-time, energy-use, layout and source metadata in recipe details.
- Defensive `ItemStack` copying so guide rendering cannot mutate addon recipes.

See [`docs/MACHINE_RECIPE_PROVIDER_API.md`](docs/MACHINE_RECIPE_PROVIDER_API.md) for addon integration examples.

## Phase 4.1A — Core GUI machine input filling

- Adds a **Fill Machine Inputs** button to verified `AContainer` recipes.
- Left-click transfers one complete recipe set; shift-left-click transfers the maximum safe number of complete sets.
- Requires the player to aim at the exact placed machine shown in the guide.
- Writes only the machine's declared input slots and never touches outputs, controls or upgrades.
- Coordinates with the machine ticker during the transaction and refuses machines that are open or already being viewed.
- Uses the same recipe-input and stack-merge matching services as the machine runtime, including virtual-item handling.
- Simulates the complete player/machine transfer before committing and restores both inventories after an unexpected failure.
- Preserves protection-plugin checks and Folia region ownership.
- Does not start operations, consume energy, generate outputs or access nearby storage.

## Phase 4.1B-A — Automatic addon `AContainer` filling

- Extends **Fill Machine Inputs** to addon machines that inherit the standard Slimefun `AContainer` implementation.
- Removes the previous Slimefun-core addon ownership restriction.
- Matches each displayed recipe back to the machine's actual registered `MachineRecipe` list before showing the fill button.
- Keeps reflected or guide-only addon recipes view-only when they cannot be verified against the runtime container recipes.
- Revalidates the player's selected ingredient alternatives when the transfer begins.
- Uses the amounts from the registered runtime recipe rather than trusting reflected guide metadata.
- Supports reordered inputs and duplicate ingredients through unique, order-independent recipe matching.
- Rejects addon machines whose declared input slots overlap their output slots.
- Retains all Phase 4.1A transaction, rollback, protection, ticker and Folia safety checks.


## Technical design

- Native guide registration; no reflection or private-field replacement.
- No updater, Pinyin index, Chinese alias layer or separate scheduler.
- Classic guide fallback through `plugins/Slimefun/enhanced-guide.yml`.
- Existing server configuration remains compatible through code defaults for added settings.

## Current boundary

The guide still does not automatically craft items, withdraw from nearby storage or recursively craft missing sub-components. Phase 4.1B-A fills verified core and addon `AContainer` input slots. Machines with custom inventory systems, addon-specific GUI adapters, nearby-storage access and higher-risk automation remain future phases.

## Installation

1. Remove the JustEnoughGuide JAR before testing this native implementation.
2. Back up the Slimefun Legacy repository.
3. Extract the appropriate changed-files ZIP into the repository root and replace matching files.
4. Commit the files and run the normal GitHub Actions build.
5. Fully stop the test server, replace the Slimefun Legacy JAR and start it normally.
6. Test on a copied Paper server before production use.

To restore the classic guide, set `enabled: false` in `plugins/Slimefun/enhanced-guide.yml` and fully restart the server.
## Phase 4 compatibility correction

- Supreme and other addons that keep their real processing recipes outside `AContainer.getMachineRecipes()` now receive the Machine Recipes browser.
- Public collection methods, iterable/array sources, map values and public recipe-list fields are supported without private reflection.
- Standard `AContainer` recipes and addon-owned public recipe sources are merged when a machine uses both systems.
- Every recognized public source is inspected, so an empty compatibility getter cannot hide a populated recipe field.
- Numbered input/output getters remain available when an aggregate getter exists but returns no usable items.
- Recipe objects may expose aggregate inputs/outputs or numbered getters such as `getInput1()` and `getInput2()`.
- Existing FastMachines world filtering and alternative-choice handling remain isolated to its dedicated provider.

## Cheat guide category grouping

The enhanced and classic `/sf cheat` menus now reuse the normal guide's exact top-level categories, icons, ordering, nested groups and addon visibility rules. This preserves addon-designed navigation instead of replacing every plugin with a generic chest folder. Item clicks still use cheat mode, so normal clicks grant one item and shift-clicks grant a full stack where supported.

