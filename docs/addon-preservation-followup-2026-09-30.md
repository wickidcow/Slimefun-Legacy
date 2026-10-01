# Addon preservation follow-through — September 30, 2026 (Eastern)

## Exact scope and candidate source selection

Continue revision96/core `7c8320828f7d0f98e80246f2766e7814a8a15eca` without replacing concurrent core/component work. Revision97 changes exactly three of the 45 pinned addon sources. All other 42 source rows and every inclusion/exclusion remain unchanged. No core production Java, release version, storage format, item/research ID or automatic migration setting changes here.

| Addon | Selected source | Owner PR | New tests | Full project test entries |
| --- | --- | --- | --- | --- |
| Galactifun | `82bdf5254b42a14f38c98590a8b6ebf708c5be22` | [10](https://github.com/wickidcow/SF_Galactifun/pull/10) | 13 | 25 |
| SlimeHUD | `351e69c4e9889ac386a3db099c62d66d2b6caff3` | [6](https://github.com/wickidcow/SF_SlimeHUD/pull/6) | 20 | 20 |
| Slimefun Advancements | `279446eaac5bb7bbb2599a10b5637fa8c87b36f0` | [10](https://github.com/wickidcow/SF_SlimefunAdvancements/pull/10) | 14 | 14 |

The 47 new permanent tests and all59 entries in these three full-project reports passed, with zero failures/errors/skips. Their normal repository PR workflows also passed independently. The tested same-source core was freshly built in run36809109648; its SHA256 is `90ae41d7dc78a92d53803802c81877bbac01f3ac4f7be8b72005597d3ade0853`.

## What was corrected

**Galactifun:** the writable Stargate registry no longer treats malformed/unreadable YAML as an empty address directory. It validates before world/gameplay initialization, explicitly stops after a failed load, stages complete writes and retains pending changes after a failed replacement. An identical registration can retry a pending save. Gate addresses, gfsgAddress, world names, full signed coordinates, unknown keys and ordinary teleport validation remain unchanged.

**SlimeHUD:** unreadable player.yml cannot become an empty saveable store. Startup stops before HUD tasks; complete writes are staged, pending mutations survive a failed write, and unchanged healthy saves no longer rewrite the file. Player UUID paths, existing preference/default semantics, commands and HUD handlers remain unchanged. Unknown keys, file symlinks and POSIX permissions are preserved by the tested boundary.

**Slimefun Advancements:** removing a definition no longer silently deletes its saved advancement or criterion progress. A detached original JSON document retains unresolved identities and opaque fields; current known counters/completion values remain authoritative. Restored definitions recover usable old progress. Explicit null values and large unknown numbers remain present. Failed primary/backup parsing no longer mixes partially loaded records. Known progression/reward/revocation logic, UUID filenames, checked IO failures and existing backup conventions remain unchanged.

The filesystem changes do not promise safety against every power loss, disk failure, hard-link topology or concurrent external writer. The advancement correction does not certify the separate default-config and unrecoverable-corruption policies.

## Actual generated old-addon runtime fixtures

[Run36809109648](https://github.com/wickidcow/Slimefun-Legacy/actions/runs/36809109648) passed 48 actual server cycles for Galactifun and SlimeHUD. Each addon used its real original writer, followed by candidate upgrade and restart. Unchanged operations and shutdown retained valid files byte-for-byte. A blocked write retained original bytes and pending state, then retried correctly and survived another process restart. Original-addon malformed-file controls reproduced data loss; candidate controls refused initialization and retained the exact malformed bytes on both attempts.

[Run36810744968](https://github.com/wickidcow/Slimefun-Legacy/actions/runs/36810744968) passed 18 actual advancement server cycles. The real original addon wrote two UUID-specific profiles. The candidate ran with one advancement and one criterion absent, saved active changes, restarted, restored definitions and restarted again. Exact old counts74/23/37 and separate completion flags recovered through the real PlayerProgress API. Partial progress resumed7→8. The original producer granted exactly one counted fixture reward; no upgrade/load/restoration/completed retry duplicated it. The original-addon control reproduced both unresolved records being dropped.

All three addons used the same unchanged JAR bytes across their three server lanes:

| Runtime | Observed server | Result |
| --- | --- | --- |
| Java21 | Paper1.21.11 build132 STABLE | All22 cycles passed |
| Java25 | Paper26.2 build129 STABLE | All22 cycles passed |
| Java25 | Paper26.3 build140 BETA | All22 cycles passed |

The 66 cycles are generated old-addon fixtures, not captured customer worlds or a full machine-throughput certificate. Advancement future-extension fields were explicitly added to the actual old-writer fixture and are not misrepresented as original writer output. Offline fixture client packets/chat announcements were disabled; real counter/completion/reward logic was exercised.

## Independent evidence checks

All source blobs, actual XML, archive digests, saved-file comparisons and runtime logs were inspected. Test dependencies were absent from the distributable plugin JARs, and Java21 bytecode checks passed.

| Full build evidence | Artifact | SHA256 |
| --- | --- | --- |
| Galactifun |11139406150|`58170ad2d826a78eace026d9c8893444a6e5559a5918294240fa3817d25e527c`|
| SlimeHUD |11138454797|`5ca83d5cdba649af265893299e6be27fcf471e162f2bd61109c0b825139259a1`|
| Advancements |11138763684|`144e1d9ecc8cd90ae083c5a4bd03f289d09fc0540f75d4d12e734ab529603c4d`|

The nine runtime artifact IDs/digests and exact JAR hashes are retained in each updated ledger row and owner PR. [Consolidation36811326212](https://github.com/wickidcow/Slimefun-Legacy/actions/runs/36811326212) rechecked completed run identities, all actual XML totals, all nine runtime archives, all66 successful cycle records, source-lock/ledger equality and every retained core source invariant before staging only the two reviewed JSON blobs. Its artifact11139299759 SHA256 is `9bae326806e1d14992804a53ee5c7ba886f5e7b20e2fe9204becdbf735a29d53`.

The source-only review snapshot collected all45 addons and core tooling in run36807829511, artifact11138018705, SHA256 `b39fc433d01519053c958cf4ec7cd4e77bcb5d82a6f8cb9836af76115b756e6e`. This is a review snapshot, not proof that every line or deprecated API has been audited.

## Ledger and remaining release gates

The consolidated ledger now records 45 successful exact-source full-build outcomes and **274 reported test entries across14 addons**. **31 addons still report no automated test entries**. These counts consolidate prior evidence with the three new exact-core builds; they are not one new all45 full-suite run. Original core/run evidence remains attached to unchanged entries.

Require the new exact candidate's complete45-addon bundle and matching provider-complete runtime checks before release. Retain the separate real previous-release item-upgrade evidence and reconcile later concurrent addon changes. Broader machine/load/concurrency tests, remaining deprecated APIs, portable-shop persistence/payment review and captured historical-world fixtures remain follow-up work, not silently waived guarantees.

Temporary source-export, staging and runtime-harness workflows are excluded from this production integration. Slimefun remains4.1.62; addon versions remain development candidates pending coordinated version/release work. No master/main merge, stable release, production installation or live data conversion was performed by this checkpoint.
