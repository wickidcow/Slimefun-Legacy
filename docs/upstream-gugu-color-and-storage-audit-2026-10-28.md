# Slimefun Legacy 26.3-era color/deprecation and GuGu intake audit (2026-10-28)

## Verified upstream comparison

- Legacy source checkpoint: `4db3e6feedd2022300c0ddfacc71a0113c3282aa` (published v4.1.78 unchanged).
- Gugu master checkpoint: `c111c3573de16d8027b7c87f85d220a93564c073`. Gugu remains the **sole** active upstream intake source, not United/Original.
- GuGu commit `2f2a286a9fab2bb7297f2af321630aa3aa429564` indexes `BlockDataController` universal block locations by `BlockPosition -> UUID` instead of scanning a map. The Legacy location lookup still scans loaded universal records and can benefit, but the Legacy controller also protects incomplete inventories, recovery locations and pending migrations. Do **not** copy the index unguarded: only index successfully hydrated records, update on moves/creation, remove on pending deletion/load failure/shutdown, and test old-world data, conflicting UUIDs and failed inventory hydration. This is a **separate storage-risk task**, not part of this patch.
- GuGu commit `8c866e393839c48786a020e5f783983dcc603351` reworks ticker freeze/stepping with new executor threads. Legacy uses a distinct region scheduler and profiler contract; bulk cherry-picking GuGu executor changes is unsafe for Folia. Evaluate cancellation, shutdown and owner-thread invariants before porting.
- GuGu profile fixes `a43ea68c9c55ff13b087aff24da75e448bcbfeae` and `60dc04f0f72cbad72b17dac34379dd52660579be` alter timings; Legacy already distinguishes total sampled ticker callback work, average, P95 and max from whole-server MSPT. Reconcile measurements before merging implementations.

## Deprecation/color policy

- Bukkit `ChatColor` is deprecated by modern Paper. Legacy already provides `ChatUtils.crop(String, String)` as the preferred path and `NumberUtils.getTextColorFromPercentage(float)` returning Adventure `NamedTextColor`. Keep the existing `ChatUtils.crop(ChatColor, String)` and `NumberUtils.getColorFromPercentage(float)` **binary-compatible** for older addon binaries; GuGu still uses old Bukkit colors in ChatUtils.
- Existing guards `scripts/verify_chatcolor_compatibility.py`, `scripts/verify_deprecation_suppressions.py` and `scripts/verify_no_deprecated_itemmeta_setters.py` prevent unreviewed deprecated paths. New tests preserve RGB/section/ampersand translations and old/new crop signatures. Never bulk-rewrite persistent lore, menus, saved item text or legacy addons merely to silence compiler warnings.
- Legacy's shared mutable `DecimalFormat` can race across Folia player regions in both compact energy displays **and** `roundDecimalNumber` profiler output. Use one synchronized formatting boundary that retains the exact `#.##` ROOT-locale pattern, all existing outputs and public method descriptors; no long-lived ThreadLocal references to plugin classes.

## Runtime gates and support claims

- `1.21.11` is the minimum supported Minecraft runtime and Java 21 bytecode floor; retain real Paper/Purpur two-boot and old-world compatibility checks.
- `26.1.2` is the last Paper 26.1 variant; add advisory exact `26.1.2.build.74-stable` source compilation on Java 25. **Not promoted to supported** without old-world/WorldKey migration and two-boot validation. Paper 26.1 changed dimension storage; do not infer safe rollback or world downgrade.
- `26.2` stays supported with native Paper/Purpur and advisory Folia/Leaf gates.
- `26.3+` remains the primary API and production focus. Compile on JDK 25, emit Java 21 bytecode, and preserve independent release, API, addon, full-stack and region-scheduler evidence. No upgrade without version-specific validation.

This patch does not change Slimefun IDs, world/backpack storage, machine recipes or payout semantics. No releases, addon bundles, external Pylon/Rebar or Geyser/Bedrock features are touched.
