# Slimefun Legacy 4.1.64 maintenance checkpoint

## Current source, not a repeated merge

Base this release on actual master `56491fb98dd13113b3372fe964821ce591a0d28d`. The other active workstream already reconciled and merged core PR297/298 and published the refreshed revision103 addon ZIP on4.1.63. Their complete histories, documentation, all45 members, every source selection and the BuildingStaff optional-provider compile step are retained unchanged here. Do not recreate those merges or replace that published ZIP again.

The previously merged core PR295 storage fix is retained for delivery in4.1.64: cancelling a pending block removal must persist queued deletions as well as writes. The private in-memory deletion marker introduces no new stored format. It does not recover deletions lost before this fix or redesign cross-thread removal.

This release preparation changes no production Java, item/research IDs, typed persisted keys, storage identities, machine rates, recipes or migration defaults. It synchronizes the seven release/support/README/baseline metadata files, updates three addon test-ledger rows from actual new reports, and adds release notes. The previous stable regression baseline advances to exact published4.1.63 source `2703e3a500b426849f3eb7806862bb4343bb9d6a`. The historical4.1.15 advisory baseline remains intact.

## Independent companion-addon validation

[Run36944318814 attempt2](https://github.com/wickidcow/Slimefun-Legacy/actions/runs/36944318814) checked the original two parent selections and rebuilt the same three companion sources now merged in master against checksum-verified published4.1.63. Full Maven clean-verify reports contain BetterChests30, BuildingStaff21 and FluffyMachines20 tests:71 passed, zero failures/errors/skips. No project test was skipped. The provided core dependency was the only compile/test redirection; BuildingStaff used its existing optional-provider API installer. All final addon JARs passed Java21 checks.

Downloaded artifact11200903882 matched SHA256 `6b49cd172f16d4c669b3cd5f97c5616a626c19106c3736e26a9c542b5393302f`; actual XML and staged blobs were independently inspected. These results update only those three rows. Networks retains its98-test evidence. The consolidated ledger now records463 project-test entries across18 addons, with27 lacking unit-test report entries but potentially having separate runtime/static checks. This is not one fresh all45 test run. Five pre-existing selected/tested-source discrepancies remain disclosed rather than relabeled as newly tested.

The first attempt detected missing BetterChests1.0.3 publication before testing. Its reviewed main was explicitly dispatched through its unchanged normal build/runtime/publisher workflow. [Run36944637735](https://github.com/wickidcow/SF_BetterChests/actions/runs/36944637735) passed and published raw asset604408805, SHA256 `24f0c5e3dc6cb5b3c7797f77e85ad908430f5a9840a3e6071e3dabe1a2ef1aa8`. A test-only dispatch guard initially selected an audit-branch run sharing the same SHA; it was corrected to require the actual main run, not bypassed.

The successful reconciliation subsequently downloaded the published4.1.63 core and all four changed addon releases and checked publisher hashes, sizes, ZIP integrity, plugin versions and absence of test/probe libraries. Their exact receipts are retained in published-release-checks.json. Rebuilt Maven test JAR hashes are not relabeled as the separately published binaries.

## Versioned core preparation evidence

[Run36945701820](https://github.com/wickidcow/Slimefun-Legacy/actions/runs/36945701820) applied the same release metadata and reviewed addon selection to production sourcecf09e5bc, which has identical production Java to current master56491fb9. All source invariants passed; the full uncached Gradle build produced512 reported test entries, zero failures/errors and one existing historical-database-fixture skip. Explicit production deprecation/removal report was0/0, not a claim of no unchecked/test/runtime notes.

Two clean builds produced byte-identical JARs with SHA256 `c394433378a2cb80e3cb8505235cd8e8757a50052e0c9131b582960b7ba73de3`. Artifact inspection passed version4.1.64, source identity, previous-stable4.1.63, Java21 and packaging checks. Downloaded evidence11201114828 matched SHA256 `4b0b219de1dd70a84ad6d723fce78a7e9016c7ed0d16995c5103b8be84bc6acb`; all122 XML reports, both artifact reports, seven promoted metadata blobs and the actual normalization diff were inspected.

The initial version-only harness failed existing README/support-version guards. Those fields were synchronized instead of removing the guards. Another attempt omitted the normal build's existing Spotless preprocessing and failed formatting. The successful run used the same Spotless step as build-ci.yml, retained its normalization diff and did not promote unrelated formatted Java files. Every full-build/Spotless/test assertion remained active. Pre-promotion artifacts are not stable releases or proof of the final committed source.

## Publication boundary

The final committed4.1.64 head requires its own normal core, API/baseline, platform, canonical45-addon and provider-complete full-stack checks. Exact-master publication then runs the existing reproducible-release workflow and independently verifies actual release assets. Preserve the existing4.1.63 core/tag and its already-refreshed addon ZIP. A version label or old green workflow is not a new-source release certificate.

Item/data compatibility remains a mandatory conservative boundary. Neither generated fixtures, successful starts nor the ledger prove every populated customer world, live-client interaction, external plugin interleaving, power-loss scenario, cross-fork rollback or Folia region behavior. Optional repairs remain opt-in. Temporary preparation/dispatch workflows are excluded from this release source.
