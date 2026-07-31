# Slimefun Legacy Native Enhanced Guide — Phase 1

This patch replaces the default survival and cheat guide registrations with native enhanced implementations. It is designed to feel familiar to JustEnoughGuide users while remaining part of Slimefun Legacy's normal guide API.

## Included

- JEG-style six-row category and item layouts.
- Configurable character-based menu formats.
- Paged smart search instead of the classic 35-result cap.
- Search by item name, Slimefun ID, addon, category, recipe type and lore.
- Search filters: `id:`, `addon:`, `group:` and `recipe:`.
- Persistent per-player bookmarks stored by Slimefun item ID.
- Right-click bookmark controls in item groups and search results.
- Item lore showing category, addon and Slimefun ID.
- Existing Slimefun research, permission, history, cheat-item and recipe rendering behavior retained.
- Classic guide fallback through `plugins/Slimefun/enhanced-guide.yml`.
- No reflection, private-field replacement, updater, Pinyin index or separate scheduler.

## Intentionally deferred

Recipe autofill, nearby-container scanning and recursive sub-recipe crafting are not included in Phase 1. These features modify inventories and machines and need a dedicated transactional implementation for Paper/Folia safety rather than a direct copy of JEG's addon logic.

## Installation

1. Remove the JustEnoughGuide JAR before testing this native implementation.
2. Back up the Slimefun Legacy repository.
3. Extract the changed-files ZIP into the repository root and replace matching files.
4. Commit the files and run the normal GitHub Actions build.
5. Fully stop the test server, replace the Slimefun Legacy JAR and start it normally.
6. Test survival guide, cheat guide, search, bookmarks, research unlocking and addon categories.

To restore the classic guide, set `enabled: false` in `plugins/Slimefun/enhanced-guide.yml` and fully restart the server.
