# Record-key condition view reconciliation — October 2, 2026

The recovered local condition-ownership patch and PR #312 implement the same ownership correction. This follow-up continues the existing PR at a21f77de30831d3ca69ef92b137771e115014376 instead of creating a duplicate. All original 19 condition tests and 13 collection-ownership tests are retained.

## One remaining view difference

The detached sequential view inherited AbstractList.subList, whose modification check observes the wrapper's unused modCount rather than the linked backing list. After an explicit addCondition, a retained sublist could therefore report its stale size instead of detecting the backing-list change as the historical linked-list view did.

Delegate subList creation to the real backing list and wrap that sublist in the same copying view. This five-line production addition preserves the underlying single-threaded invalidation checks, nested subviews, linear iteration and detached Pair exports. It is not a thread-safety guarantee, does not encourage retaining invalid sublists, and does not make concurrent mutation of a RecordKey supported.

## Executed local comparison

The exact PR source was reconstructed and checked against Git blob 22f73e03f5af0baed468aa0da201a41528bb6fff. All three variants were compiled with Java 21 and the actual checksum-verified published 4.1.65 dependencies, without replacement Pair/Bukkit implementations. Only the temporary Pair import was mapped to the release's shaded namespace.

The saved 32-scenario executable suite produced 21 expected assertion failures on the released implementation, one failure on the existing PR (subListStillDetectsStructuralChange), and zero failures after this follow-up. The passing scenarios include 2,000 deterministic ownership cases. This run is not a full Gradle/JUnit or server result.

Eight permanent JUnit tests now cover direct/nested/empty sublist invalidation, already-created iterators, live root views, detached nested/reverse exports, structural immutability and historical bounds/null/duplicate handling. The new combined head must pass its own full project, API, platform, canonical 45-addon and full-stack checks; predecessor success is not substituted.

No public descriptors, equality/hash/text rules, item/research IDs, stored keys/types, SQL schema, recipes, inventory codecs, machine rates or migration defaults change. All 45 revision-106 addon selections and the published 4.1.65 core/bundle remain untouched. The existing SQLite ownership tests and their source/core attribution are retained. Temporary audit helpers and the earlier standalone review ZIP are not production artifacts.
