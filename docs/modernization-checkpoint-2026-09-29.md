# Legacy-first modernization checkpoint — September 29, 2026

## Owner-confirmed implementation order

Finish and validate Slimefun Legacy core first, then Networks, InfinityExpansion2, Supreme, FinalTECH and the remaining maintained addons in dependency-aware batches. FinalTECH is reauthorized in this workstream; the earlier hold no longer applies. Addons remain compatibility consumers during core testing, not an excuse to switch implementation focus before the core checkpoint is sound. Rebar and Pylon remain outside this modernization scope.

The contract remains Minecraft 1.21.11+, prioritizing Paper and Purpur and validating Leaf/Folia appropriately. Preserve item/research IDs, recipes, outputs, production rates, energy costs, persistent keys, namespaces, inventories, machine/backpack identities, transfer ordering and saved data. Maintain the legacy public bridges where data or addon linkage depends on them. Raw plugin JARs and an exact-source tested addon bundle remain the delivery target.

## Core correction and real build evidence

PR #289 initially stopped before compilation at the supported-platform-floor verifier. The remaining issue was not only an unused import: `SlimefunItemSetup` still used `VersionedPotionEffectType` five times. The compatibility class maps each used field directly to the identically named native `PotionEffectType`.

The correction removes that import and replaces one `HASTE`, three `JUMP_BOOST` and one `STRENGTH` alias references. Existing effects, durations, amplifiers, item IDs and recipe ingredients are unchanged. The compatibility class itself is retained for older addon binaries.

Exact inspected source: `b44106d1b78297e66843660023930be796752c46`.

- Original item-setup blob: `3bfa9dd6f2e9ba053e7af6c1c3a328740f3f8ea3`.
- Corrected item-setup blob: `8cdf7701762a126aa7bf6c4a68358c7be3a96bdd`.
- Alias-definition blob checked before substitution: `91d0835760f766deacc7402861e61b4523f0885d`.

[Isolated validation run 36647049278](https://github.com/wickidcow/Slimefun-Legacy/actions/runs/36647049278) checked out the exact PR source, applied the hash-guarded source correction and ran the real repository build on Java 25. Results from its logs and JUnit XML summary:

| Check | Observed result |
| --- | --- |
| `scripts/verify_legacy.py` | All registered core invariants passed, including the 991-signature API baseline. |
| Gradle formatting and build | Successful; production and test sources compiled and the test task ran. |
| JUnit total | 332 discovered: 331 passed, 1 skipped, 0 failures, 0 errors. |
| `BlockDataConfigWrapperTest` | All 15 tests passed using the real project dependencies, without the earlier local dependency stubs. |
| Explicit javac compatibility warnings | 0 deprecation and 0 removal warnings; the existing 20 approved compatibility suppressions were not expanded. |
| Skipped test | `DatabasePatchV3RealDatabaseTest#migratesAndDeserializesEveryInventoryItem`; an actual database fixture was not supplied. |

This run does not prove all Minecraft/platform runtime combinations or cross-fork world round trips. Test-only deprecated-API notes and a Byte Buddy/Unsafe runtime warning were also present; the zero-warning statement above is specifically the explicit production javac deprecation/removal report, not a claim of no warnings anywhere.

## Make validation evidence fail closed

The primary build previously opened `build/reports/deprecation-compile.log` through `tee` while running `clean build`. Cleaning can unlink that output file. The summarizer then treated a missing file as empty input and could report zero warnings without evidence.

The consolidated correction runs `clean` before opening the log and makes missing, unreadable and blank input fail. A `--require-successful-build` flag additionally rejects incomplete or failed Gradle logs in the primary build. Invalid input replaces any stale clean report. Eighteen local Python regression cases passed, including an actual open-file/unlink reproduction and preservation of warning enforcement after a successful build.

The exact consolidated PR commit must still run its normal CI; the isolated result above tested the production correction before these validation-only changes were committed. The new validation tests are already included through the existing `test_summarize_deprecations.py` entry in the full invariant runner.

## Pinned cross-fork source audit

The following is source evidence, not a successful server-switch test:

| Implementation | Pinned source | Item payload observation |
| --- | --- | --- |
| Legacy | `b44106d1b78297e66843660023930be796752c46` | `ItemStackDataCodec` writes `SF2` plus Paper-native bytes and reads that format plus historical Base64 Bukkit object streams. |
| Gugu | `SlimefunGuguProject/Slimefun4@f1722396066859c075dadafd2e908a7ff96af9a8` | Its codec uses the same `SF2` header and native payload layout. Its String API also recognizes Base64-encoded current data. Table/schema, metadata and full-world behavior still require testing. |
| United | `Slimefun-United/Slimefun-United@6bfe999ea203269a94795624bd4cb736d47479c8` | Its `DataUtils` writes Base64 Bukkit object streams and its shown reader expects that stream format. It does not decode Legacy's `SF2` envelope through that reader. |

Sources: [Gugu codec](https://github.com/SlimefunGuguProject/Slimefun4/blob/f1722396066859c075dadafd2e908a7ff96af9a8/src/main/java/com/xzavier0722/mc/plugin/slimefun4/storage/util/ItemStackDataCodec.java), [Gugu String API](https://github.com/SlimefunGuguProject/Slimefun4/blob/f1722396066859c075dadafd2e908a7ff96af9a8/src/main/java/com/xzavier0722/mc/plugin/slimefun4/storage/util/DataUtils.java), [United reader](https://github.com/Slimefun-United/Slimefun-United/blob/6bfe999ea203269a94795624bd4cb736d47479c8/src/main/java/com/xzavier0722/mc/plugin/slimefun4/storage/util/DataUtils.java).

Do not change the current serializer back globally or rewrite live worlds to claim interchange. United needs a separately verified conversion/export or compatible reader, with a disposable copy and exact item/metadata/content comparisons. Test a plugin switch on the same Minecraft version separately from any Minecraft-version change. Shared items require matching registrations/addons on the destination; Legacy-only features cannot be assumed to exist there.

A further core audit point is `DataUtils#serializeItemStackBytes`: non-empty serialization failures can currently return the same empty payload used for empty slots. `RecordSet` and maintenance writers call it. Trace each commit/retry path and add failure-injection persistence tests before changing this historical API behavior. No serializer, database patch, storage schema or migration defaults are modified by this checkpoint.
