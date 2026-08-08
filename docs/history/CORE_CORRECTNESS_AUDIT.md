# Slimefun Legacy Core-Correctness Audit

This maintenance pass reviews active fixes from Slimefun 5, Slimefun United, Gugu and Folia-oriented work against the current Slimefun Legacy source. Paper and Purpur remain the primary runtime targets. Fixes are ported only when Legacy is missing the behavior and the change can preserve addon compatibility.

## Reviewed upstream fixes

- Slimefun 5 `e443570`: recipe-required multiblock consumption.
- Slimefun 5 `b63c725`: dispatch to all matching multiblocks.
- Slimefun 5 `fc79efb`: thread-correct Auto Enchanter/Disenchanter events.
- Slimefun 5 `be7316e`: backpack identity collisions and silent open failures.
- Slimefun 5 `0d7f954`: hidden-addon visibility recovery.
- Slimefun United `bd4b36a`: Energy Regulator hologram thread safety and bug-tracker URL validation.
- Slimefun United `5ca54ab`: string-based Multi Tool mode storage.
- Slimefun United `b8789d5`: Programmable Android texture preservation.

## Ported in this audit

### Recipe-amount-correct multiblocks

The Enhanced Crafting Table, Magic Workbench and Armor Forge now consume the amount declared in each matched recipe cell instead of always subtracting one. Their virtual output-capacity simulation uses the same recipe amounts, including consumable-container handling.

### All matching multiblock handlers

A click is now dispatched to every matching multiblock in reverse registration order. This prevents one overlapping structure definition from silently swallowing a click intended for another machine. Each machine still validates its own inventory and recipe before acting.

### Energy Regulator thread ownership

The Energy Regulator ticker is now synchronized. On Paper and Purpur this routes hologram/network mutation through the server-owned path; on Folia the existing scheduler abstraction keeps it on the owning region.

### Multi Tool mode migration

Multi Tool mode is now stored as the selected Slimefun item ID rather than a numeric list position. Existing integer-mode tools are migrated on first use. This keeps the selected mode stable if configuration reorders or inserts modes.

### Backpack resolution diagnostics

Backpack allocation already uses UUID identities and a persisted monotonic display counter, so the collision bug from older map-size allocation does not apply. This audit adds warnings when a current or legacy backpack identity resolves to missing or invalid storage instead of failing silently.

## Confirmed already present

- Auto Enchanter and Auto Disenchanter events derive their asynchronous flag from the current Paper/Purpur thread or Folia-owned region.
- `/sf versions` validates addon bug-tracker URLs and has a plain-text delivery fallback.
- Programmable Android movement preserves the stored base64 head texture.
- Backpack items use UUID storage identities, persisted numbering, duplicate-open reservations and viewer checks.
- The Vanilla Auto-Crafter protects modern gamerule reads.
- Profiler reports reject stale cycle completion.
- Cargo, energy and storage code already use Legacy's scheduler and transaction safeguards.

## Reviewed but not ported

### Addon-visibility reset

Slimefun 5's fix resets a per-player hidden-addon set. Legacy does not implement that data model; its guide reads registered item groups directly and catches addon visibility failures. Porting the setting-specific reset would add an unused subsystem.

### False multiblock “Assembled” message

The reviewed Slimefun 5 fix targets a structure-placement notification path that does not exist in this Legacy source. There is no equivalent message to correct.

### Anvil custom-name preservation

Legacy intentionally blocks Slimefun items from anvils. Slimefun 5's rename-preservation marker belongs to its translated-item/anvil architecture and would require changing established gameplay rather than correcting a current Legacy bug.

### Slimefun 5 package compatibility shim

Legacy retains the original `io.github.thebusybiscuit.slimefun4` API packages, so addons do not need a shim for a package relocation that never occurred here.

### Alternate SQL backends and redstone multiblock autocrafting

These are major features, not focused compatibility fixes. They remain separate future projects because the current storage and crafting systems are stable.

### Runtime lore migration audit

Slimefun 5's audit is tied to its translation-block migration. Legacy is English-first and uses a different presentation-repair system, so a direct port would report many intentional strings. A Legacy-specific addon lore diagnostic can be designed separately without affecting this core-correctness release.

## Validation

Run:

```bash
python3 scripts/verify_legacy.py .
./gradlew spotlessApply
./gradlew clean build
```

The audit verifier checks both newly ported fixes and the important protections already present in Legacy.
