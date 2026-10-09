# Addon bundle r129 candidate: DynaTech 1.1.04

This candidate advances the addon release matrix from revision 128 to 129 and pins
DynaTech 1.1.04. Initial preparation used Slimefun Legacy master commit
`9e80ba889cbddd45fc6456f6199a6607252503d3`, which contains the r128 FluffyMachines
update. The current integration base is
`5c5e092577ae716dd38ae0e0d09b6b09b9ea4707`, including
[PR #357](https://github.com/wickidcow/Slimefun-Legacy/pull/357)'s read-only
historical Doctor hints. That master update is incorporated without rewriting
the candidate's initial history. Public Slimefun Legacy 4.1.71 and its r127 addon
bundle remain unchanged. This document records candidate preparation; r129 is
not a published replacement for those release assets.

The initial core PR head `449c0bee322fba004b1bcb8e858142c48de09936` and its GitHub
merge commit `786b68f8c8a8f0007247eb259beae259b3af66ce` preceded the master update.
Their core, bundle and full-stack CI results, including bundle run 37867207502,
are superseded historical evidence and provide no acceptance for the refreshed
combined source. The unchanged DynaTech publication and fixture evidence below
retains its separate attribution to the addon and pinned public core.

## Source and artifact identity

| Field | Value |
| --- | --- |
| Addon | `wickidcow/SF_DynaTech` |
| Previous bundle source | `8dbc4a4967b943cfddd1d0677713a932a8ce45ba` |
| New bundle source | `f4289dbbd61fcc93ae8c6bcfde5b9e3892d371bc` |
| Reviewed and merged source tree | `c3f6e9f0f50fa65a60acddd42c05891beefacfd1` |
| Merged change | [DynaTech PR #9](https://github.com/wickidcow/SF_DynaTech/pull/9) |
| Published addon release | [v1.1.04](https://github.com/wickidcow/SF_DynaTech/releases/tag/v1.1.04) |
| Release ID / asset ID | `407378149` / `623416565` |
| Public JAR | `SF_DynaTech1.1.04.jar` |
| Public JAR size | 702,372 bytes |
| Public JAR SHA-256 | `975536a3716e1c7157dd609b287817489b926e2a3341d9a6da255db7c16e422e` |
| Publication run | [37864770475, attempt 1](https://github.com/wickidcow/SF_DynaTech/actions/runs/37864770475) |

PR #9 was merged through the normal repository workflow. The v1.1.04 tag resolves
to the new bundle source above, and the successful merged-head CI run published a
new addon release. The downloaded public asset passed ZIP CRC validation and
matches the reviewed PR and branch-push JAR byte for byte. The older v1.1.03
release asset remains unchanged.

The bundle retains all 45 members, its existing filename, artifact naming rules,
compatibility policy and exclusions. The other 44 source pins are unchanged,
including FluffyMachines at `bea7728a3b6ac607c8edcad5c9d42edb71b777b1`.

## Scoped behavior and compatibility boundaries

DynaTech's wireless transfer change debits only the amount actually accepted by
the destination insertion. It performs inventory mutation on the server thread,
rechecks the current unlocked input and output menus, and marks both inventories
dirty after a successful transfer. It retains the ordinary cost of 8 energy at
each endpoint, whole-stack admission and existing transfer order.

The change preserves item and research IDs, typed PDC, recipes, inventory slots,
machine identities and saved link strings. It does not change a Slimefun Legacy
storage format or core implementation. The
[existing-item compatibility contract](../old-item-compatibility-contract.md)
and [storage audit](../modernization-storage-audit.md) remain release requirements.

## Evidence completed before r129 integration

[PR run 37863157682](https://github.com/wickidcow/SF_DynaTech/actions/runs/37863157682)
and [branch-push run 37863153100](https://github.com/wickidcow/SF_DynaTech/actions/runs/37863153100)
both passed on attempt 1. Their reviewed source tree is the merged tree recorded
above. Each ran three API verification builds, followed by packaging against the
1.21.11 API, and the DynaTech contract checks. The API selections were
`1.21.11-R0.1-SNAPSHOT`, `26.2.build.112-stable` and
`26.3-rc-3.build.1-alpha`. The emitted JAR retains the Java 21 bytecode floor;
native servers ran on JDK 25.

Both CI runs also passed the following real Paper fixture matrix. Each lane
completed 25 transfer cases and four separate-process restart cases, with 148
transfer assertions and 48 restart assertions. These are repeated runs of the
same case set, not additional distinct regression cases.

| Paper runtime | Exact build | PR run | Branch-push run |
| --- | --- | --- | --- |
| 1.21.11 | 132, stable | 25 transfers + 4 restarts passed | 25 transfers + 4 restarts passed |
| 26.2 | 132, stable | 25 transfers + 4 restarts passed | 25 transfers + 4 restarts passed |
| 26.3 | 159, beta | 25 transfers + 4 restarts passed | 25 transfers + 4 restarts passed |

The native lanes use the unchanged public `Slimefun-Legacy4.1.71.jar`, SHA-256
`916b055a6917c87405078948fd00cdbb2394078248d1c9bfbbfa9c4b37106e6a`.
Their acceptance gates include retained installed binary identities, complete
metadata readbacks at all eight restart endpoints, ordinary queue quiescence,
observed quiet windows, strict plugin-log checks and normal process exits.
The independent evidence record is `dynatech-ci/pr9-readback/ci-independent-audit.json`;
the run links above retain the CI reports and artifacts.

The merged-head publication run 37864770475 also passed the same three native
lanes on attempt 1. Independent re-evaluation confirmed 75 transfer executions,
12 restart executions, 588 case assertions, 78 actual database metadata
observations and nine normal boot/stop cycles, with no strict plugin failures.
Its raw public artifact digest agrees with the downloaded release JAR above.
The retained record is `dynatech-ci/merged-f4289db/publication-independent-audit.json`,
SHA-256 `35ab24a65974a932e50a63220ad8ae41a8c4f88e969c8f56d8d712d58cb4f617`.

The final unchanged helper also reproduced the DynaTech 1.1.03 baseline taken
from the published Slimefun Legacy 4.1.71/r127 addon bundle, SHA-256
`b7e3e447f58fdcad60c03ea78b6538c14548d638e1aeca5dfdf37cd4091c4a8c`.
This identifies the bundled JAR used by the fixture separately from the older
standalone v1.1.03 release asset. On Paper 1.21.11 build 132, exactly 11 negative
transfer cases and the two intended persistence restart cases failed, while all
14 ordinary transfer controls and both untouched restart controls passed. The
complete, nonfatal reports satisfied the baseline contract. Its retained evidence is
`dynatech-native-baseline-1.21.11-readback-b/evidence/manifest.json`, SHA-256
`c4cb04b1586c10b4359c6074a865bd855ebc5a4c1e59bd59e9a342202bfa106d`.

Earlier failed local native attempts and unexplained post-exit file/SQLite WAL
observations remain recorded in `dynatech-audit/native-shutdown-followup.md`.
A later successful bounded diagnostic showed that a main database file alone
can differ from the logical database including its WAL. It did not establish
the cause of the earlier failures or the actor behind later sidecar reappearance.
Those observations remain unresolved; passing acceptance runs do not close them.

## r129 validation and release gates

The following local checks were rerun on the combined source using current
integration base `5c5e092577ae716dd38ae0e0d09b6b09b9ea4707`, the checked-in Gradle
9.4.1 wrapper, the existing JDK 25 toolchain and the normal credential-free proxy.
Initial preparation logs remain in `r129-core-local`; refreshed logs, XML reports
and audits are retained separately in `r129-core-local/refreshed-5c5e092`.

| Local check | Actual result |
| --- | --- |
| Matrix/source scope audit and `git diff --check` | Passed; only the matrix and this note differ from the current integration base. |
| `python3 scripts/verify_legacy.py .` with JDK 25 on `PATH` | Passed all repository invariant checks. |
| `./gradlew --no-daemon --console=plain test` | Passed: 705 tests total, 704 passed, one skipped, zero failures/errors; 141 XML reports. |
| `./gradlew --no-daemon --console=plain build` | Failed at `spotlessJavaCheck`; test outputs were current, and `sourcesJar` and `shadowJar` completed. |
| `./gradlew --no-daemon --console=plain spotlessCheck` | Failed on the same 283 inherited Java formatting violations. |

The skipped test is the existing
`DatabasePatchV3RealDatabaseTest.migratesAndDeserializesEveryInventoryItem()`;
its setup requires the optional `slimefun.realDatabase` system property. Refreshed
production compilation and the test task both executed; the test command passed
in 15.1 seconds. The invariant verifier passed in 23.2 seconds. Build and explicit
formatting checks failed in 9.2 and 6.5 seconds, respectively. No refreshed command
hit its bound: tests/invariants allowed 90 seconds each, and build/formatting
allowed 60 seconds each.

The refreshed comparison independently found 283 Spotless violations; every
affected source file matches its Git blob at the current `5c5e092` base. Java,
runtime scripts and Gradle configuration are unchanged relative to that base,
and PR #357's incoming catalog, verifier and API documentation match master
exactly. The refreshed evidence directory contains `formatting-violations.json`,
`formatting-violations.txt` and `formatting-violations.diff`, with every violation
path, proposed formatting difference and base/source hash, alongside the full
command logs and `junit-summary.json`. The existing CI build workflow runs
`spotlessApply` before building; success through that workflow is distinct from
a passing `spotlessCheck` on an untouched source checkout. The local formatting
gate remains failed and must be disclosed in the integration PR.

The following gates remain pending for the exact r129 integration commit:

- Core build, tests, formatting, API/bytecode guards and the required repository CI checks.
- Construction of the exact 45-member r129 bundle from the pinned sources,
  including artifact names, source revisions and JAR/ZIP hashes.
- The required addon compatibility/compile and runtime workflows, plus full-stack
  checks using the r129 core and bundle artifacts on the supported version matrix.
- Review of the resulting CI evidence and public-asset provenance before any new
  core/bundle release is published through the normal release workflow.

Track evolving CI run IDs, conclusions and artifact audits in the integration PR
body against the exact tested commit. Keep this source note as the preparation
record so later CI completion does not repeatedly change the commit under test.

The DynaTech fixture evidence covers disposable Paper inventories and normal
restart persistence with the pinned public core and default SQLite configuration.
It does not certify Folia region safety, crash atomicity, connected-player
interaction, every storage backend, real player-world upgrades or cross-fork
round trips. The earlier r127/r128 results and addon-only CI do not substitute
for the pending r129 core, bundle and full-stack gates.
