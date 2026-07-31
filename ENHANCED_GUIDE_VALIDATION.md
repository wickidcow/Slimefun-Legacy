# Slimefun Legacy Native Enhanced Guide — Phase 1 Validation

## Scope

This package adds a native JEG-style guide layer to the current Slimefun Legacy source without embedding the JustEnoughGuide plugin or replacing private fields through reflection.

## Completed checks

- Parsed `enhanced-guide.yml` and verified every menu layout is exactly six rows by nine slots.
- Verified the main, group, search and bookmark layouts contain their required content and pagination markers.
- Verified enhanced survival and cheat implementations are registered through a single native bootstrap.
- Verified the unchanged classic survival and cheat guides remain available when `enabled: false`.
- Verified persistent bookmarks store Slimefun item IDs rather than serialized item stacks.
- Verified paged search includes item names, IDs, addons, categories, recipe types and lore.
- Verified search filters for `id:`, `addon:`, `group:` and `recipe:`.
- Verified research unlocking, item permissions, guide history, cheat permission checks and inherited recipe rendering remain connected.
- Verified no reflection, private-field replacement, updater, Pinyin search implementation or Chinese player-facing Java text was added.
- Compiled the new sources against focused Java 21 API stubs matching the Slimefun Legacy guide, Bukkit, ChestMenu and Dough method signatures used by this patch.
- Ran a raw `javac` parser pass over every Java file; all reported errors were expected missing-class errors from the isolated source-only workspace, with zero syntax-error patterns.
- Ran the package's static verifier successfully.

## Full build limitation

The complete Slimefun Legacy Gradle project and dependency cache are not mounted in this isolated workspace, and outbound dependency downloads are unavailable. A complete `spotlessApply` and `clean build` must therefore run in GitHub Actions after these files are dropped into the user's current Folia Phase 2 repository.

## Required staging checks

1. Run the normal GitHub Actions formatter and build workflows.
2. Start a copied Paper server without the JustEnoughGuide JAR.
3. Test the survival guide and cheat guide.
4. Test categories from core Slimefun and several addons.
5. Test locked research, missing permissions and research purchases.
6. Test searches by name, ID, addon, group and recipe type across multiple pages.
7. Add and remove bookmarks, restart the server and confirm they persist.
8. Test normal and shift-click cheat item behavior with the appropriate permission.
9. Re-enable the classic guide in `enhanced-guide.yml` and confirm the fallback works after restart.
