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
- [ ] Run Enhanced Guide verification suite and confirm no category/search/bookmark regression.
- [ ] Validate ExtraGear enchanted swords and armor in the Enhanced Guide and recipe view.
- [ ] Run the full Slimefun Legacy verification suite.
- [ ] Verify Java 21 bytecode output.
- [ ] Run Paper 26.2 runtime smoke validation.
- [ ] Run runtime gameplay correctness checks.
- [ ] Validate the release artifact and reproducible clean build.
- [ ] Review addon compatibility regressions discovered since 4.1.31 and include only confirmed low-risk fixes.
- [ ] Prepare 4.1.32 release notes and final raw JAR after all required checks are green.

## Release title

**Slimefun Legacy 4.1.32 — Stability & Compatibility**
