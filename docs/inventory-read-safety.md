# Preserving inventories after incomplete reads

## Scope

Core-only continuation of the failure-safe writer checkpoint `5b6bf35f75ae35cf7cbac58e8b90f8e57eb7acf5`. This batch changes no item IDs, research IDs, PDC keys/types, recipes, output formats, schema, migration defaults, resource-pack mapping or addon code. Historical readers remain available. Minecraft 1.21.11+ remains the server floor, not an age cutoff for saved items.

## Failure and correction

The old backpack/block/universal load paths caught item decoding errors, placed null in the affected slot, and marked the inventory for a later full reconciliation. A later save could therefore delete the original record. Ignoring malformed or out-of-range slot numbers carried a similar risk.

`StoredInventoryReader` now stages the complete inventory locally. It validates slot numbers/ranges/uniqueness and stored representations before publishing anything. Historical explicit empty fields remain valid. A non-empty payload that fails to produce a usable item is refused rather than treated as empty. Valid records are read through the existing codec and are not rewritten during the read.

Backpack, block and universal load attempts retain a per-owner incomplete-read guard until a complete retry succeeds. The core inventory save entry points and queued submission boundaries refuse writes while that guard is present. Failed block/universal loads remain not-loaded, do not expose a newly constructed writable menu, and do not activate a ticker. Universal instances are removed from the loaded map only when the map still contains that same instance.

All item decoding precedes application of KV data and construction of menus. The historical loaded flag is temporarily available during preset construction because existing presets may read their own KV state; inventory writes remain guarded until construction and snapshotting finish. This is not a claim of transactional publication of every addon callback or arbitrary KV operation.

Universal migration now preflights all source inventory records and decodes KV values before allocating a destination. Invalid source data or an unavailable required preset throws without creating a destination or deleting the source. The list-based asynchronous block-load callback now runs inside the scheduled load task, after every load completes; it does not announce success ahead of the reads.

## Tests

The two added suites contain 22 executions. The 12 reader cases cover the supported text/binary item representations, exact item amount/metadata, unchanged input bytes, historical explicit empties, malformed/missing/duplicate/out-of-range slots, unsupported types, unusable decoded results and linkage failures.

The 10 controller cases exercise real controller read and save-gating logic with SQLite-backed read records and intercepted write submissions. They cover failed block/universal/backpack reads, preservation after database close/reopen, blocked save attempts, complete recovery after explicit fixture repair, missing-preset recovery, malformed slots, migration refusal before destination creation/source removal, and asynchronous callback ordering.

The fixture invokes Slimefun's retained explicit test constructor, uses real registries, and does not enable production services. It does not subclass the final core, strip its final modifier, mock the storage decoder, or claim to boot a live Minecraft server. The test-only compatibility constructor is deliberately retained outside production modernization.

## Verified pre-promotion evidence

[Run 36657130800](https://github.com/wickidcow/Slimefun-Legacy/actions/runs/36657130800) passed the full source invariant suite, formatting, compilation and Gradle tests on Java 25. The downloaded evidence was independently checked:

| Check | Observed result |
| --- | --- |
| Full JUnit suite | 387 discovered: 386 passed, 1 skipped, 0 failures/errors. |
| StoredInventoryReaderTest | 12 passed, no failures/errors/skips. |
| InventoryReadFailureTest | 10 passed, no failures/errors/skips. |
| Negative control | Restoring the old ProfileDataController produced exactly the expected one backpack failure among three cases: the corrupt read did not throw. |
| Explicit production javac warnings | 0 deprecation, 0 removal. |
| Source invariants | All passed, including all 991 protected API signatures. |

Evidence artifact `11072218126`, SHA-256 `e76aa52c162f630d8bdedcd33362dfe666cd2a2c8943ff457e1fdfd3415be262`. The eight validated file hashes were checked; production Java tokens match the reviewed patch, and both verifier files match exactly. Focused storage/API/suppression checks also passed locally on the formatted files. Existing production compatibility suppressions remain at 20. The full suite's one skip is the pre-existing external-database-fixture test; the new SQLite-backed cases did run. Test-library Unsafe runtime notices are separate from the production compiler warning count.

The initial validation runs caught an invalid test subclass and MockBukkit's similar final-class proxy restriction. Only test construction was corrected, using the existing supported test constructor; the core remains final, and no failed test was removed or disabled. Normal PR CI must independently validate the promoted commit. Temporary validation workflows and patch payloads are not promoted.

## Limits and remaining work

These guards protect the audited core load/save paths after an incomplete read. They are not a universal intercept of explicit removal, replacement, move, direct SQL or administrative deletion APIs. Those lifecycle transitions need a separate audit, including recovery-marker handling when a corrupt machine is deliberately replaced. A server restart re-reads and revalidates the stored records; the guard itself is in memory.

Migration preflight is not durable migration commit safety. After a valid source passes preflight, destination writes, source deletion, interruption and retry still need transaction/acknowledgement tests. The scope also does not certify live Folia region ownership, arbitrary concurrent external calls, every historic server's items, or a full Gugu/United world round trip. Real backups and exact-version upgrade testing remain release requirements.
