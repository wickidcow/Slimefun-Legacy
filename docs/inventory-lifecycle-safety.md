# Incomplete-inventory protection across lifecycle operations

## Scope

This core-only batch extends the incomplete-read protection introduced at `5fc2270ffab18b6cff208afad36f162d79f2bbce`. The preceding patch prevented an incomplete inventory from being published or saved; this patch prevents known recovery state from being bypassed by audited removal, replacement, movement and bulk-removal entry points. Minecraft 1.21.11+ remains the runtime floor, not an age cutoff on player items. Paper/Purpur remain primary, with Leaf/Folia validation.

No item/research IDs, typed PDC values, recipes, codec writes, database schemas, migration defaults, resource-pack maps or addon implementations change. Existing items are not replaced with new templates. The new recovery index is in memory only, not a new persistent schema.

## Defect and correction

The previous `SlimefunChunkData.removeBlockData` changed its cache first and then requested persistent record deletion. Rejecting only the low-level delete therefore left an incorrectly detached cache even when the original database record was retained. The controller and direct chunk API now check recovery state before changing caches, pending-removal flags, menus, ticker state or submitting the audited record mutations. Creating a replacement or moving onto a protected position is rejected as well. A guarded member prevents the audited whole-chunk or whole-world deletion from starting; unrelated chunks/worlds are not blocked.

Universal inventories have UUID identities but may represent a physical block. A synchronized reverse index retains positions known from cached state or the two already-supported persisted location representations. Evicting a failed universal load does not remove its location protection. Several failed UUIDs at the same position are tracked independently, so recovering one does not release another. A complete successful load clears only its own entries after successful activation. There is no public force-clear method or automatic clearing on attempted removal.

The index does not load or resolve worlds/chunks and does not rewrite persisted location strings. The healthy player-event check returns early without constructing location keys when there is no recovery state. Player break/place listeners cancel known protected positions before drops or metadata mutation. The sensitive-block cleanup branch also refuses a known protected position.

## Healthy behavior and migration preflight

Ordinary removal, cache tombstones, recreation and movement retain their historical behavior. Tests exercise unchanged IDs and item amounts after explicit fixture repair and complete reload. The existing internal universal migration may remove its source only after the already-established source preflight; private helper methods avoid temporarily clearing the public source guard merely to invoke a public mutation method. This preserves source-preflight control flow; it does NOT certify durable migration completion or introduce a public force-delete API.

## Actual pre-promotion validation

[Validation run 36660609203](https://github.com/wickidcow/Slimefun-Legacy/actions/runs/36660609203) completed successfully using Java 25 and the real repository dependencies. Downloaded JUnit XML, the artifact digest and all seven formatted source blob hashes were checked independently. The Java token streams match the reviewed patch after excluding comments/import formatting.

| Check | Observed result |
| --- | --- |
| Full Gradle build | Passed. |
| Full JUnit suite | 413 discovered: 412 passed, 1 skipped, 0 failures/errors. |
| New controller/cache/event cases | 21 passed, no skips. |
| New recovery-location cases | 5 passed, no skips. |
| Complete source invariants | Passed, including all 991 protected API signatures. |
| Explicit javac compatibility report | 0 deprecation / 0 removal warnings; production suppression allowlist unchanged at 20. |

The one skipped test is the pre-existing external-database-fixture test, not a new lifecycle test. Test-library/runtime notices remain distinct from the explicit compiler report.

The lifecycle tests use real controller/cache/event methods, SQLite-backed fixture reads, intercepted mutation submissions and the existing Slimefun test constructor. The location-index tests use no server. Coverage includes failed reads, cache preservation, all audited location mutations, move source/destination protection, bulk/queued deletion, unrelated scopes, normal tombstones and move behavior, recovery/recreation, UUID cache eviction, historical locations, overlapping owners, in-progress loads, event cancellation and private migration preflight without releasing the public guard.

The negative control in [run 36659862162](https://github.com/wickidcow/Slimefun-Legacy/actions/runs/36659862162) restored only the old direct chunk-removal implementation. It reproduced exactly one expected cache-identity assertion failure after deletion had been refused. A compiler or setup failure was not accepted as this result.

Successful evidence artifact: `11074127294`; SHA-256 `35e04bdf60e4a7d7ef60baf74d8a8e6408eaea0c30ee5521950f8457f835c49d`. Negative-control evidence artifact: `11073378854`; SHA-256 `3556d78c5ab2d98096edd97c54a433ecb7036e0c094f5ba92d0bfccfea228d9e`.

Only the seven validated source/test files plus this document are promoted. Temporary workflows and compressed patch data are excluded. The resulting commit must separately pass its normal PR platform/API/addon workflows before being considered a validated candidate. No merge or release is implied by this isolated result.

## Fixture corrections

The first run passed 25 of 26 new cases; listener registration stopped because the test plugin was disabled. The corrected fixture temporarily sets only its test enable flag to register the real listener, restores the flag immediately and unregisters the listener before direct handler calls. It does not start production services or relax event assertions. A subsequent strict warning gate identified the intentional deprecated-for-removal move-alias call in a compatibility test. Its method-scoped test annotation now documents both deprecation and removal coverage; the historical alias remains exercised, and removing it would still fail compilation. No production suppression was added. A YAML indentation correction was confined to the temporary workflow. The production patch remained unchanged through these fixture corrections.

## Remaining boundaries

These tests are not concurrent Folia execution, production SQL-adapter tests, full historical worlds or disk-crash fault injection. Rejected known-guarded mutations retain their original persisted records and known cache identities; they do not reconstruct corrupt data or guarantee the survival of unsaved live state.

The guard protects known incomplete/in-progress loads, not all previously undiscovered corruption before every destructive event. An invalid universal location remains protected by UUID but cannot safely be assigned an invented physical location. Arbitrary direct SQL, external plugin world changes, admin force operations and all possible concurrent check/mutation interleavings are not certified by this scoped patch.

Durable universal migration remains a separate release-blocking audit: destination record, custom data and inventory must be confirmed persisted before source deletion, with interruption/retry behavior tested. The private preflight route here retains existing migration timing; it is not represented as crash-safe conversion. Exact Gugu/United round trips, real old-world fixtures and the final same-source addon bundle remain separate release requirements.
