# Record-key condition ownership — October 2, 2026

Continue released 4.1.65 at 82427167200e1b349b53c8c81f2373a5b36e340f. This patch is distinct from the already-merged empty-collection, inventory-snapshot and delayed-completion fixes.

## Defect and correction

RecordKey copied a supplied condition list but retained its mutable Pair entries. Its unmodifiable getter also exposed those same entries. A caller changing either Pair value could alter SQL conditions without invalidating the already-cached key text/hash. A queued or cached request could therefore refer to a different record than its identity described. This is a demonstrated aliasing condition, not evidence that all server owners have experienced it.

Copy each condition Pair at construction and detach every Pair returned by the public view. Keep the historical live, structurally unmodifiable list: supported addCondition calls remain visible in a previously obtained view. Keep entry order, duplicates, exact opaque strings, existing null handling, Boolean conversion, equals/hash/text rules and public method descriptors. No item or research IDs, stored field names/types, SQL schema, inventory codecs, recipes, machine rates, save cadence or migration defaults change.

The view is created once per key. It delegates sequential iteration to the existing linked list instead of repeatedly performing indexed traversal. Private identity/comparison code uses the owned conditions directly; Pair copies occur only on input/public export. No measured server-TPS gain is claimed.

## Validation

The new 19-test permanent suite covers mutable constructor inputs, all public export paths, live-view additions, structural immutability, bidirectional traversal, ordering/duplicates, null/opaque values, cached identity, map lookup and 1,000 shared-input keys. Three generated SQLite tests use the production SQL condition renderer for read/update/delete, close the connection and inspect persisted rows after reopening. These are not full controller, Minecraft restart, captured-world, MySQL/PostgreSQL or uncontrolled-concurrency tests.

Before commit, an independent Java21 harness compiled the exact original source (Git blob c2f32181a4367a8eba2cfeaac8eb98b742f4558b) and the correction with actual local plugin dependency classes, without handwritten Bukkit/Pair substitutes. Both constructor and getter aliasing reproduced on the original; neither reproduced on the correction, and live-view additions remained visible. That local check did not run JUnit or a database and is not substituted for the normal project build.

Require the new head's complete Gradle tests, source/API guards, Java21 packaging and supported-platform/addon checks. Record actual new-head outcomes in the PR rather than reusing release or predecessor green checks. The published 4.1.65 JAR/ZIP and all 45 revision-106 addon selections remain untouched by this patch. No automatic data rewrite or recovery of previously misdirected operations is introduced.
