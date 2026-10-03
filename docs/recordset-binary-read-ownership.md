# RecordSet binary read ownership

## Baseline and defect

Continue master `5cb1e47c35a2b85ccc342dbf4121fac4bf9a1969`, which already contains the scoped-lock 4.1.67 release preparation, the published 4.1.66 RecordKey correction and all 45 revision-107 addon selections. This patch does not bump a version or alter the separately prepared release source. Do not reapply the old offline RecordKey patch.

`RecordSet.put(FieldKey, byte[])` already owns its input, but `getValue` and `getAllValues` exposed the owned byte arrays. Calling `readonly()` prevented explicit puts, not changes through those arrays. An external mutation could change serialized inventory bytes before a queued SQL writer consumed the supposedly read-only record, or alter a record returned by the SQL reader.

## Narrow correction

`getValue` now returns a detached binary copy. `getAllValues` retains a structurally unmodifiable live map while lazily exporting each binary buffer into that view's own storage. Supported puts are still visible. Editing an exported buffer may change that exported view, never the record or another newly requested view.

A view retains each exported array's identity until the source is replaced; repeated gets, entries, containsValue, equals and hashCode therefore follow normal array-valued Map contracts rather than generating a fresh identity on every iteration. Distinct views own distinct arrays, so reference-based equality between two independently requested binary exports is not promised. Key traversal does not clone item buffers and text remains immutable/shared. Only actual binary exports are copied, at most once per source buffer per view. Lazy initialization is synchronized within that view, not globally. This does not support concurrent record puts or simultaneous edits to a caller-owned exported array.

The serialization writer and readers, byte representation, String/Base64 compatibility API, null/default/scalar handling and public method descriptors are unchanged. No IDs, field names/types, SQL tables, inventory capacities, item templates, recipes, machine rates, save cadence or migration defaults change. `RecordSet` is the only changed production source file. There is no automatic world rewrite and this patch cannot reconstruct data corrupted before installation.

## Permanent validation

The suite includes 51 new cases:

- 32 export-path cases: 16 direct/map/entry/value/array/stream/callback paths on writable and frozen records.
- Seven ownership/live-view/scalar/structural guards, including 1,000 deterministic byte-array cases.
- Four array-valued Map contract cases protecting stable identities, equality, hashing and replacement.
- Six JDBC SQLite tests across block, backpack and universal inventory scopes. These invoke the actual SqliteAdapter setData/getData and SqlUtils reader, overriding only connection/profiler transport for a temporary local JDBC database, then close/reopen the store.
- Two actual-codec MockBukkit cases retaining original item IDs, name/lore, amount, FLOAT charge, large LONG count and opaque byte-array metadata after an attempted exported-buffer overwrite.

The older StoredInventoryReader invalid-slot tests now compare a detached pre-read byte snapshot with the exact post-refusal contents rather than requiring a shared mutable array reference. Every malformed/duplicate-slot refusal assertion remains. This strengthens content-preservation evidence instead of requiring the unsafe identity being removed.

## Actual focused results

Run `37087979463` tested source `762a6f8e7596a6920a08bb01fd6b35d61d11ecca` after the normal source-invariants-before-Spotless ordering. All 51 new cases passed. Restoring only the original RecordSet implementation produced 46 assertion failures among the same 51 cases, zero errors/skips, including all six reopened SQLite cases. The corrected source resolves those failures. It also produced a Java 21 candidate JAR for separately tracked native item tests.

Evidence artifact `11261153301` matched SHA-256 `e7cbf183bbdd251ffae84a1404c0261cd4f7acb7b62e3c540e866649bbd6852c`. Actual XML, formatted source and Git blob identities were downloaded and independently inspected. Only the five scoped formatted Java files were promoted; unrelated formatter changes and temporary audit workflows/probes are excluded.

The first attempt failed before tests because the JDBC fixture incorrectly narrowed SqliteAdapter's public executeSql method to protected. Changing that test-only override to public fixed compilation. The next full run executed all 51 new cases successfully but exposed six old array-reference assertions in StoredInventoryReaderTest; these were corrected to exact byte comparisons as described above. Neither failed attempt is relabeled as a successful full build.

Native Paper results, full final-head Gradle/API checks and the complete same-source addon/runtime gates remain separately attributable in the PR checkpoint. Generated JDBC/MockBukkit/native fixtures are not captured customer worlds, arbitrary concurrency, crash-durability, MySQL/PostgreSQL deployment or Folia-region certification. No measured TPS improvement is claimed.

No release version, addon pin or published asset is changed in this patch. Full build/formatting/API and same-source addon/runtime validation remain required before integration; publication remains a separate exact-source operation.
