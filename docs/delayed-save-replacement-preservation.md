# Delayed-save replacement preservation

## Current-state continuation

This change is based on master `d1ac11a1c6891ac6768dff935bcc8de7d149b7a5`, after the separately prepared 4.1.64 release metadata was merged. It retains all 45 revision-104 addon source selections, version/support contracts, storage cancellation replay and legacy color corrections. No release tag, published JAR/ZIP or production server is changed.

The production files inspected before this patch have the same Git blobs on the earlier implementation baseline `2bcaa53f2f1c7695cdb18cbf003d2516fdab6d79` and this new release base. The intervening changes are release metadata, documentation, the ledger and full-stack trigger tests, not the delayed-save implementation.

## Reproduced queue race

The delayed-saving looper iterates a queue snapshot. A task can finish submitting its old value while a newer change for the same LinkedKey installs a replacement task. The old key-only completion callback then removes the replacement from the live queue. The replacement value or deletion is left in memory without its corresponding deferred database submission.

The correction supplies both the key and exact completed task to the core callback and calls the existing ConcurrentHashMap's conditional `remove(key, completedTask)`. A replaced entry remains queued. A completed current entry is removed. The snapshot and execution scheduling are otherwise unchanged.

The historical public `(int, Supplier, Consumer)` constructor remains available with its existing behavior. A named `withTaskCompletion` factory avoids introducing an ambiguous public overload for existing source expressions such as `tasks::remove`. The core uses the new factory; existing external callers are not silently rewritten.

## Preserved contracts

No item/research IDs, persistent keys or types, record layout, serialized item format, block/backpack identities, inventories, recipes, capacities, transfer order, machine rates, scheduler periods or migration defaults change. Save coalescing before execution, failure retry, force-save timing and repeated-success suppression remain in place. The change introduces no disk markers and does not convert saved data.

A successful delayed runnable still means work was submitted normally, not that an independently scheduled database operation has already been acknowledged. This patch does not change shutdown acknowledgement or provide a cross-resource transaction. It cannot reconstruct writes already lost before installing the fix. No measured TPS gain is claimed.

## Actual project verification

[Validation run 36952373189](https://github.com/wickidcow/Slimefun-Legacy/actions/runs/36952373189) completed the full source-invariant suite, normal Spotless preprocessing and an uncached Gradle clean build. All 15 new tests passed. The 124 downloaded XML reports contain 529 reported entries, zero failures/errors and one existing historical-database-fixture skip. Empty external-database factory entries are not additional executed database coverage.

The negative control restores only the original key-only removal in the real controller factory, retaining the same test environment and other implementation. It produces four assertion failures among 15 tests with no test errors/skips: due completion, forced completion, queued deletion and a chain of replacement writes. The corrected code passes all cases.

Permanent tests cover actual controller enqueue/coalescing/retry paths, both looper timing paths, exact callback identity, independent locations, a deterministic two-thread interleaving, stale snapshots, null/empty snapshots, old constructor compatibility, and 1,000 successive replacements without queue growth or data loss. Recorded runnable effects in these tests are not an external database fixture.

Evidence artifact 11204053439 matched SHA-256 `33bd4215796e2d8b3dbaba258aa0221c5bef4c58c7f3e3b649ee57634bb7a4b9`. Its actual XML, negative-control output, source diff and all three promoted source blob hashes were independently inspected. The selected controller diff contains only the factory wiring/helper. Unrelated formatter normalization and temporary audit workflows are not promoted.

The first audit attempt successfully reproduced the four failures but ran formatting-sensitive textual invariants after Spotless. Matching the normal CI ordering (source guards first, formatting second) corrected that harness without removing a test or guard. Production deprecation/removal, Java 21 bytecode and artifact metadata checks passed in the successful run; test/runtime warnings are a separate category.

## Remaining evidence and integration

Real Paper/database restart validation is tracked independently in run 36952667574. Its planned fixture uses actual controller writes and separate server processes with a deliberately controlled completion interleaving; it is not a connected-player or uncontrolled-concurrency test. Do not describe those results as passed until the run and actual observations are inspected.

The clean promoted head must pass its own normal API, platform, complete-addon and full-stack checks against the current 4.1.64 metadata. Do not reuse the earlier audit build as proof of the new commit or insert this patch into an already validated release without a new exact-source build. The separately active release workflow remains untouched.
