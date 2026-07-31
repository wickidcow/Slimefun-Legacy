# Slimefun Legacy Native Enhanced Guide — Phases 1–3

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

## Technical design

- Native guide registration; no reflection or private-field replacement.
- No updater, Pinyin index, Chinese alias layer or separate scheduler.
- Classic guide fallback through `plugins/Slimefun/enhanced-guide.yml`.
- Existing server configuration remains compatible through code defaults for added settings.

## Current boundary

The guide still does not automatically craft items, withdraw from nearby storage or recursively craft missing sub-components. GUI-based addon machine adapters and those higher-risk automation layers remain future phases.

## Installation

1. Remove the JustEnoughGuide JAR before testing this native implementation.
2. Back up the Slimefun Legacy repository.
3. Extract the appropriate changed-files ZIP into the repository root and replace matching files.
4. Commit the files and run the normal GitHub Actions build.
5. Fully stop the test server, replace the Slimefun Legacy JAR and start it normally.
6. Test on a copied Paper server before production use.

To restore the classic guide, set `enabled: false` in `plugins/Slimefun/enhanced-guide.yml` and fully restart the server.
