# Failure-safe inventory write staging

## Scope

This is the next core-only batch after PR #289 head `22aec969b3e5b8634737152babd5e8242dec6c6c`. It does not modify any addon repository, item registration, recipe, research, namespace, codec format, schema, resource-pack mapping or migration default. Minecraft 1.21.11+ and Paper/Purpur priority remain unchanged; the age of saved items is not a server-version support floor.

## Failure mechanism and correction

The historical `DataUtils.serializeItemStackBytes` API can log an encoding failure and return empty bytes. `RecordSet.put(FieldKey, ItemStack)` previously accepted that return as ordinary item data. Thus a failed non-empty item could replace the last successfully stored value with the same empty representation used by an empty inventory slot.

An additive `serializeItemStackBytesForStorage` method performs the same encoding and MySQL size check without discarding an exception. The old String and byte-array helpers remain callable and retain their tolerant failure/known-empty-race behavior for addon compatibility. The shared encoder avoids global configuration lookups for payloads below the existing MySQL limit; it does not change the limit or bypass option.

`RecordSet` and administrative stored-item replacement creation use the strict path. Read-only records reject changes before invoking an addon-supplied serializer. The block/universal async save entry points now return failed futures when snapshotting or staging fails rather than throwing synchronously. Backpacks already had a staging failure boundary; it now receives genuine errors instead of an apparently successful empty encoding.

All three full-inventory staging paths treat actual null, AIR or zero-count input as a deletion. A non-empty snapshot that throws—even with Paper's empty-item error message—is a failed save, not authority to delete a stored row. Compatibility-only delayed slot writers also contain runtime/linkage serialization errors without scheduling an empty write.

## Invariants

Staging completes before any part of that inventory's new batch is submitted. A bad later slot prevents that new batch's earlier slots and deletions from being submitted; it does not cancel a previously queued successful stage. Failed staging leaves the last acknowledged snapshot/dirty state alone. A later successful retry can acknowledge the new snapshot. Existing per-inventory save ordering and partial-write reconciliation remain intact.

This preserves the last successfully saved state; it does not claim that an unsaved live state survives a process crash, or that failing hardware/database transactions become infallible. Callers must continue to respect failed futures and existing access/reservation rules.

## Tests and verified evidence

`InventorySerializationFailureTest` has seven focused tests and four parameterized scenarios run for block, universal and backpack inventories (19 test executions total). It uses the real item codec and real controller staging/acknowledgement. A controlled completion adapter writes to disposable SQLite files so failure order and acknowledgement timing can be asserted deterministically. It does not run the normal background executor/production SQL adapter or a live Folia scheduler.

Coverage includes exact successful output-format equality, real empty states, a non-empty Paper-empty exception, ordinary native exceptions, missing-class errors, cloning failure, read-only protection, administrative replacement failure, no partial submission on staging failure, preservation/retry/reopen, full-batch empty deletes, partial database failures and 54-slot reconciliation, and two overlapping snapshots completing in order.

The negative control restores the old `RecordSet` writer while retaining the rest of the candidate and runs the non-empty/Paper-empty regression. That is a test of the old write boundary, not a claim that the entire historical core was booted.

[Validation run 36652818546](https://github.com/wickidcow/Slimefun-Legacy/actions/runs/36652818546) completed successfully using Java 25 and real project dependencies. Its downloaded JUnit XML and source blobs were independently checked:

| Check | Observed result |
| --- | --- |
| Original RecordSet negative control | Expected failure reproduced: the non-empty serialization failure was not rejected. |
| Corrected full Gradle build | Passed. |
| New save-safety suite | 19 passed, 0 failures/errors/skips. |
| Full JUnit suite | 365 discovered: 364 passed, 1 skipped, 0 failures/errors. |
| Explicit production javac report | 0 deprecation, 0 removal warnings. |
| Full source invariant suite | Passed, including all 991 protected API signatures. |

The pre-existing skipped external-database-fixture test remains separate from the passing disposable-SQLite scenarios. Compatibility suppressions were not expanded. Test-source deprecated-API notes and test-library runtime notices are not counted as explicit production javac warnings.

Evidence artifact: `11071770595`; SHA-256: `87ed75528ff0457a9d5956e1dcc934daa7df465e0966ad6d7bec654ab1c3cd26`.

Only the six formatted source files from that successful run are promoted into the main PR, plus this document. Their Git blob hashes were verified and their Java tokens compared with the reviewed changes. Temporary patch scripts and validation workflows are excluded. The normal PR workflows must separately validate the resulting commit.

The first run found one invalid test fixture: Paper rejects construction of a CAVE_AIR ItemStack before serialization is reached. The corrected fixture uses constructible null/AIR/zero-count inventory states. Production behavior and failure assertions were not relaxed.

## Remaining read-side audit

No failed-load isolation is claimed by this write-side batch. `getBackpackInv`, `loadBlockData`, `loadUniversalData` and `migrateUniversalData` still need separate correction/tests: their current catch paths can represent an unreadable record as null and request later reconciliation. The universal migration path also creates the destination before finishing source-item decoding.

The next read-side batch must distinguish unreadable stored items from genuinely empty slots, retain original bytes, and avoid exposing a partially decoded writable inventory or acknowledging/ticking it as fully loaded. Audit normal unload, explicit removal, absent addon registration, malformed slot numbers, migration retries and recovery ownership before changing those paths. Real old-world upgrades and cross-fork transitions remain separate validation requirements.
