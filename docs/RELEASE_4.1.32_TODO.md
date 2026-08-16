# Slimefun Legacy 4.1.32 — Stability & Compatibility

This release is intentionally focused on stability, compatibility, regression fixes, and release hardening after 4.1.31.

## Scope rules

- Do not change existing Slimefun item IDs.
- Do not change existing recipes or research IDs unless fixing a confirmed broken recipe.
- Do not change storage keys, databases, or saved-world formats.
- Avoid unrelated gameplay expansion in this release.
- Keep Paper 26.2 as the primary runtime target while retaining the established Java 21 bytecode floor.

## To do

- [x] Enhanced Guide: show real enchantments when hovering enchanted Slimefun/addon items in category, search, and bookmark views.
  - Root cause: `EnhancedSurvivalSlimefunGuide.decorateItem(...)` added `ItemFlag.HIDE_ENCHANTS` to the display copy.
  - Fix: retain `HIDE_ATTRIBUTES` and `HIDE_ADDITIONAL_TOOLTIP`, but stop hiding enchantments.
  - Primary reported case: ExtraGear equipment showed its full enchantments on the recipe page but not in the Enhanced Guide item list.
- [x] Add an English localization-key release guard for literal core localization references.
  - Dynamic/runtime-generated localization prefixes are deliberately ignored to avoid false positives.
  - The guard is part of `scripts/verify_legacy.py` so future missing core English strings fail CI.
- [x] Restore the missing static English localization contract discovered by the new guard.
  - Includes backpack anti-tamper/owner feedback, Auto Crafter feedback, Auto Enchanter limits, Table Saw protection, Android access limits, migration/clear-data/admin feedback, and timings status.
- [x] Fix `/sf banitem` using the wrong root path for `messages.invalid-item-in-hand`.
- [x] Harden Auto Enchanter and Auto Disenchanter input transactions against input-swap/metadata races.
  - Outputs are calculated from one-item snapshots.
  - Inputs are consumed only if the current input pair still matches those exact snapshots.
  - AdvancedEnchantments disenchanting now receives a cloned input rather than the live inventory stack.
- [x] Beacon Plus: add a default-on Area Preview toggle to the existing configuration menu.
  - Uses the unused menu slot 51 and a lever icon with clear up/down ON/OFF state text and lever-click feedback.
  - Preview follows the exact effective 1x1 / 3x3 / 5x5 chunk field, including Extra Range expansion.
  - Only the outer boundary is rendered with sparse particles; no armor stands or marker entities are created.
  - Unloaded chunks are skipped instead of being force-loaded, and Folia perimeter work is handed to the relevant region scheduler.
- [ ] Validate Beacon Plus Area Preview live for 1x1, 3x3, 5x5, and Extra Range expansion.
- [ ] Validate Area Preview OFF persistence and confirm existing Beacon Plus blocks default to ON when no setting is stored.
- [ ] Validate Auto Enchanter I/II and Auto Disenchanter I/II end-to-end: input → operation → power → output.
- [ ] Validate machine interruption behavior: GUI close, cargo/hopper changes, chunk unload/reload, restart, and output-full conditions.
- [ ] Run Enhanced Guide verification suite and confirm no category/search/bookmark regression.
- [ ] Validate ExtraGear enchanted swords and armor in the Enhanced Guide and recipe view.
- [ ] Run the full Slimefun Legacy verification suite.
- [ ] Verify Java 21 bytecode output.
- [ ] Run Paper 26.2 runtime smoke validation.
- [ ] Run runtime gameplay correctness checks.
- [ ] Harden addon compatibility CI against Maven baseline-cache/repository corruption false failures.
- [ ] Validate the release artifact and reproducible clean build.
- [ ] Review addon compatibility regressions discovered since 4.1.31 and include only confirmed low-risk fixes.
- [ ] Prepare 4.1.32 release notes and final raw JAR after all required checks are green.

## Release title

**Slimefun Legacy 4.1.32 — Stability & Compatibility**
