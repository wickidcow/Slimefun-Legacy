# Scoped lock lifetime preservation — October 2, 2026

## Scope and source

Continue published 4.1.66 source `22ae22c4e32fd3b086565922f4427206d0f4b065`. RecordKey condition ownership is already merged via PR312/313; do not reapply the older local patch. Preserve the separately active revision107 addon selection in PR314 without changing its branch or source pins.

Only package-private ScopedLock implementation, its tests and this checkpoint change. No item/research IDs, block/storage identities, codecs, database schema, recipe, menu, capacity, machine rate, save cadence, migration default, version or addon selection changes.

## Reproduced defect

The prior implementation registered or obtained a ReentrantLock, then acquired it outside the map. Release unlocked it, inspected isLocked and removed the map entry. A caller can already have obtained that lock while waiting or before acquisition, even when isLocked is false. Cleanup can then discard its registration, allowing a later caller to create a second lock for the same scope. The original and new callers can enter supposedly exclusive critical sections together; their unlocks can also be directed to different entries.

The helper is used by ADataController and BlockDataController. This is a confirmed controlled helper-level concurrency defect, not an assertion that a particular customer's database has already been damaged.

## Correction and unchanged behavior

Count a reservation for each acquisition inside an atomic per-key map computation, before waiting for the actual lock. Release and decrement under the same map coordination, retiring the entry only when there are no held or waiting acquisitions. Recursive acquisitions own separate reservations. A wrong-thread unlock throws before decrementing; an absent unlock remains a no-op.

Actual blocking lock acquisition remains outside the map callback, preventing a waiting caller from holding a map bin hostage or blocking an equal-hash unrelated scope. Default non-fair reentrant locking, uninterruptible acquisition, interrupted-status behavior and explicit scope-key semantics remain intact. hasLock is still a registry snapshot, not an atomic maintenance barrier. Scope keys must remain stable during their acquisition/release lifetime as before.

This is not a database transaction redesign, a fair-queue guarantee, hostile reflection defense, interrupted-JVM recovery, Folia region-ownership certificate or measured TPS improvement. No legacy data rewrite is necessary.

## Permanent regressions and evidence

ScopedLockConcurrencyTest exposes twelve real-thread scenarios from ScopedLockRegression through JUnit. The same test bodies also run directly on Java 21 with the project's real key classes. Latches pause only after the real ConcurrentHashMap operation has returned, so the fixture controls the historical registration/acquisition window rather than changing lock behavior. No handwritten Bukkit, lock or database substitute is used.

Coverage includes reserved entrants, actual split critical-section reproduction, multiple pending callers, cleanup, recursive holds, absent/wrong-owner unlocks, equivalent keys, unrelated equal-hash scopes, interruption, 4,000 completed scopes, and 16,000 nested updates from eight contenders. Two deterministic original-code control cases fail with direct ownership/overlap assertions. Additional contention cases are not used as proof of a required negative-control result.

Local preparation compiled the exact original Git blob `213eef286a97d32a14aecd8c8937146399466428` and the replacement with Java21 and actual available 4.1.65 dependency classes. All twelve corrected scenarios passed ten consecutive runs. The first draft referred to a nonexistent FieldKey.BLOCK; correcting the test fixture to the existing FieldKey.LOCATION fixed that compile setup. This local work does not represent the full current-project Gradle/JUnit/API/server matrix. New-head CI must run independently against the actual 4.1.66 source and record its own test output.

The original-control runner is available as ScopedLockRegression --control when compiled against the original helper; it must fail its two deliberate assertions, not fail due to setup or missing dependencies. Existing controller submission/order, storage and migration tests remain required. The complete canonical addon and full-stack checks are not replaced by these helper regressions.

## Release boundary

No default-branch merge, version bump, release asset replacement or live-server installation is performed by preparing this candidate. Before release, require the complete core tests, API compatibility, supported-platform checks and matching 45-addon validation. Coordinate with PR314 so its independently tested addon updates are retained in the eventual release source.
