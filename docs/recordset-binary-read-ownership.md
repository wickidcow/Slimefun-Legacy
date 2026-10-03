# RecordSet binary read ownership

## Baseline and defect

Continue master `5cb1e47c35a2b85ccc342dbf4121fac4bf9a1969`, after the published 4.1.66 RecordKey correction and the complete 45-addon revision-107 selection. Do not reapply the old offline RecordKey patch.

`RecordSet.put(FieldKey, byte[])` already owns its input, but `getValue` and `getAllValues` exposed the owned byte arrays. Calling `readonly()` prevented explicit puts, not changes through those arrays. An external mutation could change serialized inventory bytes before a queued SQL writer consumed the supposedly read-only record, or alter a record returned by the SQL reader.

## Narrow correction

`getValue` now returns a detached binary copy. `getAllValues` retains a structurally unmodifiable live map while lazily exporting each binary buffer into that view's own storage. Supported puts are still visible. Editing an exported buffer may change that exported view, never the record or another newly requested view.

A view retains each exported array's identity until the source is replaced; repeated gets, entries, containsValue, equals and hashCode therefore follow normal array-valued Map contracts rather than generating a fresh identity on every iteration. Key traversal does not clone item buffers and text remains immutable/shared. Only actual binary exports are copied, at most once per source buffer per view. Lazy initialization is synchronized within that view, not globally. This does not support concurrent record puts or simultaneous edits to a caller-owned exported array.

The serialization writer and readers, byte representation, String/Base64 compatibility API, null/default/scalar handling and public method descriptors are unchanged. No IDs, field names/types, SQL tables, inventory capacities, item templates, recipes, machine rates, save cadence or migration defaults change. `RecordSet` is the only changed production source file. There is no automatic world rewrite and this patch cannot reconstruct data corrupted before installation.

## Added validation

The proposed permanent suite includes 51 cases:

- 32 export-path cases: 16 direct/map/entry/value/array/stream/callback paths on writable and frozen records.
- Seven ownership/live-view/scalar/structural guards, including 1,000 deterministic byte-array cases.
- Four array-valued Map contract cases protecting stable identities, equality, hashing and replacement.
- Six JDBC SQLite tests across block, backpack and universal inventory scopes. These invoke the actual SqliteAdapter setData/getData and SqlUtils reader, overriding only connection/profiler transport for a temporary local JDBC database, then close/reopen the store.
- Two real-codec MockBukkit cases retaining original item IDs, name/lore, amount, FLOAT charge, large LONG count and opaque byte-array metadata after an attempted exported-buffer overwrite.

These tests are not claimed as passing merely because the files exist. Record actual normal CI, original-code controls and supported-platform/addon evidence on the PR. Mock and generated JDBC fixtures are not captured customer worlds, arbitrary concurrency, crash-durability, MySQL/PostgreSQL deployment or Folia-region certification.

No release version, addon pin or published asset is changed in this patch. Full build/formatting/API and same-source addon/runtime validation remain required before integration; release publication remains a separate exact-source operation.
