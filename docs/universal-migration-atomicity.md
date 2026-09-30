# Atomic universal migration: implementation and validation

## Scope and compatibility

This core-only follow-up to `0b923551ac2ea01ca6acea1f6f224008d0bf91ab` replaces the existing block-to-universal migration's separately queued writes and source deletion with one confirmed database transaction. It does not introduce a new item format or new migration policy. Minecraft 1.21.11+ remains the runtime floor; older plugin-created items and saved data remain compatibility requirements. Paper/Purpur remain primary, with separate Leaf/Folia validation.

Item/research IDs, typed PDC values, recipes, item codec output, table schemas, migration defaults, resource-pack mappings and addon implementations are unchanged. Original stored item payloads and custom fields are copied through SQL rather than reconstructed from new item templates. A valid historical universal location representation is retained exactly; the established reserved location field is added only when absent.

## Transaction boundary

The migration takes an immutable preflight snapshot of the source record, raw custom fields and raw inventory values. On one connection it verifies serializable transaction support and the source snapshot, refuses an occupied destination, inserts the destination record and copies the existing values, verifies the complete destination, removes exactly the source rows, verifies the result and commits. Copy and deletion are one atomic commit, not two separately durable operations. The existing SQLite, PostgreSQL and transactional MySQL/MariaDB storage families are recognized; MySQL/MariaDB require all six involved tables to be InnoDB. Unverified engines and caller-owned transactions are refused before mutation.

Normal rollback retains the source. A rollback failure never enables auto-commit on the unresolved transaction. A lost commit acknowledgement is treated as ambiguous rather than falsely successful or definitely undone. Retrying retains the same destination UUID and validates the exact committed destination instead of allocating another copy. Only a confirmed pre-commit rollback permits a fresh source snapshot. Bounded retries cover confirmed serializable conflicts, not uncertain commits.

The controller submits this operation through the existing tracked writer queue with a distinct coordination key; ordinary record writes cannot compact it away. It waits for confirmed execution before activating the destination menu/ticker and retiring the source cache. No additional queued source deletion follows the transaction. Pending source metadata writes are refused or allowed to drain before migration. A post-commit activation failure retains persisted data and the pending identity for recovery; an already-loaded matching destination keeps its live menu rather than being replaced with a second instance.

The additive adapter method has a default refusal, preserving old adapter linkage without an unsafe best-effort migration fallback. Existing unrelated save paths keep their established behavior.

## Native validation completed before promotion

[Run 36710833152](https://github.com/wickidcow/Slimefun-Legacy/actions/runs/36710833152) completed successfully with the real Java 25/Gradle project dependencies, native Xerial SQLite, the actual SQLite adapter/controller queue, and disposable MySQL 8.4 and PostgreSQL 16 services. Downloaded archive digests, JUnit XML and formatted source blob hashes were independently checked.

| Check | Result |
| --- | --- |
| Full project build and source invariants | Passed; all 991 protected API signatures retained. |
| Core JUnit report | 443 entries: 442 reported passed, 1 pre-existing fixture skip, 0 failures/errors. |
| Actual new SQLite/plan cases | 19 passed: commit/reopen, seven rollback points, lost acknowledgement, conflict retry, collision refusal, concurrency and abrupt JVM exits. |
| Actual new controller cases | 10 passed using the production SQLite adapter, real writer execution, activation/recovery and queue identity checks. |
| MySQL case | Passed all seven rollback boundaries, commit with a lost acknowledgement and exact replay. |
| PostgreSQL case | Passed the same seven rollback boundaries, commit with a lost acknowledgement and exact replay. |
| Explicit javac deprecation/removal report | 0 / 0; approved production suppressions were not expanded. |
| Candidate packaging and Java 21 bytecode checks | Passed in isolated validation. |

The core SQL suite reports one additional successful empty dynamic-factory entry because no external database URL is configured in that job. It is explicitly not counted among the 19 actual SQLite/plan cases or claimed as database-engine validation. The separate database job must execute the two named MYSQL and POSTGRES cases with no skips. The one normal-suite skip remains `DatabasePatchV3RealDatabaseTest#migratesAndDeserializesEveryInventoryItem`, requiring a separate historical fixture; it is not a skipped migration case in this batch.

The initial real build also passed, but its evidence counter expected 19 XML entries and encountered the empty factory report. The counter was corrected without removing an assertion or changing production code, and the external driver cases were extended from one rollback point to all seven. Both jobs then passed independently.

Evidence: core artifact `11094092399`, SHA-256 `679c3cd7ec8b6603e639b99af70ec83b2623cbf6481b3acc62aff64623b6eb67`; database artifact `11094396889`, SHA-256 `635a8cd58a7e0d21d5dbf3b05396b8015ca29b3d47a74336d54aa2009ce8d356`.

Only the twelve validated Java source/test files, this updated document and the permanent database workflow are promoted. Temporary validation scripts, compressed patch parts and runner-only workflows are excluded. The resulting PR commit must independently pass the normal platform/API/addon matrix and the new database workflow before being described as a validated candidate. No merge or official release is implied by the isolated run.

## Permanent regression coverage

Native SQLite and controller regressions run in the ordinary Gradle suite. `Universal Migration Database Compatibility` runs actual disposable MySQL/PostgreSQL checks for storage/build changes on PRs to master, master pushes, or explicit workflow dispatch. It uses read-only repository permissions, test-only credentials and a test-only MySQL driver; it does not change shipped dependencies, production data or release publishing.

## Limits and next release evidence

Abrupt-process tests halt a separate JVM just before and just after SQLite commit and reopen the database, yielding the complete old or new state. They do not simulate physical power loss, disk corruption, MySQL/PostgreSQL process death, every replication configuration or arbitrary external database writes. Durability depends on the database's supported transaction and persistence configuration.

The controller tests use the existing isolated Slimefun test constructor rather than enabling a complete production server. Normal platform boots, real historical-world upgrades, addon gameplay/performance and exact Gugu/United round trips remain separate checks. Shared native format with the inspected Gugu source is not a full-world compatibility guarantee; direct rollback to the inspected United reader is still not certified. No serializer downgrade, cross-fork conversion or automatic world rewrite is introduced by this batch.
