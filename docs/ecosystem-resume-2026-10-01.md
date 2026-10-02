# Ecosystem continuation: Networks 1.0.47 and bundle revision 102

## Recovered baseline

This continues actual core master `cf09e5bc04cb912604e65bfeacce9dabeda5014a`, not the earlier unreleased revision99 branch. The cancelled-removal storage correction from core PR295 is already merged through `dbffc153f7b83e4fae1fbf086ea1922940d7a307`. The separately published stable core remains4.1.63 at `2703e3a5`; do not describe later master changes as already present in that stable JAR.

Keep Minecraft1.21.11/Java21 as the runtime floor and Paper26.3 as primary. Old item formats and identities remain relevant regardless of their age. Preserve GuGu compatibility and forward upgrades from United; reverse United conversion is not a release gate. Rebar/Pylon experiments remain outside the canonical bundle.

## Repository-by-repository state recovery

Read-only run36937621805 inspected all45 repositories selected by manifest101: default heads, all branch pages, open PRs/issues, current-head workflow results and latest release asset metadata. All45 reads succeeded. This was a sequential metadata snapshot, not a fresh full-code or gameplay audit of every repository.

At that snapshot there were no open non-PR issues in these45 repositories. Open PRs were Networks53 (the new fix), FluffyMachines5 (unmerged barrel hover/frame/registration work), and BuildingStaff1 (optional Rebar/Pylon support). The latter two were retained for separate review; neither was merged or deleted merely because of age. A cancelled FluffyMachines run coexisted with a successful current-head run, so cancellation alone is not an unresolved failure.

Networks had70 recorded branches:17 tips were ancestors of the inspected master and53 were not. The returned branches were protected. Ancestor status alone is not proof that a branch is obsolete; unique and protected history was not deleted. Core PR295 was not recreated. The selected JEG source `a53d11e7` and its current master `fd47e5cf` compare with no file changes, so its already-incorporated guide work was not duplicated.

Snapshot artifact11199115316 SHA256: `fa874ffa00f651202c48eb31450aeaa3013ef3fb6d53dbb6a6bdac08e129260f`. Downloaded JSON, archive integrity and Networks ancestry records were inspected. This snapshot covers the canonical45, not an assertion that excluded/archived addons or separately maintained integrations have been fully audited.

## Networks correction and safe integration

Networks PR53 contains the grid implementation `cf25f72d` and the synchronized1.0.47 release metadata `a2e10dc9`. It was merged as `ba79e6e36bd4798d37f7b8988efb414e7f041927` after normal final-head workflow36938589126 passed. The tested head and merged commit share the exact tree `4a9062f3fd95f892ade078b68cebde2df45b7e4f`.

The limited forward sort loop skipped its last supported mode. Filter/sort changes also retained a derived entry cache until the next tick. Corrected setters invalidate only on real selection changes; unchanged choices and paging retain the cache. Backward wrapping reuses an enum array. No storage inventory is modified, and item/research IDs, typed keys, amounts, recipes, machine rates, transfer priorities and quantum category ordering remain unchanged. No measured TPS or arbitrary thread-safety claim is made.

Independent original-code controls failed10 assertions among17 new tests without test errors. The corrected project passed98 tests with zero failures/errors/skips against the checksum-verified published Legacy4.1.63 API. The normal workflow also retained its Legacy/Gugu/United compile/test lanes. These checks are not saved-world rollback certification.

The first isolated version attempt stopped before Gradle because old changelog/version guards disagreed. All release identities were updated together, not bypassed. Versioned validation36938244757 passed all98 tests and Java21/universal-JAR checks; its five release-file blobs and16 XML reports were independently inspected. The1.0.47 version prevents the new behavior from overwriting the existing1.0.46 asset. Normal CI now retains XML reports.

## Exact bundle selection and evidence

Integration run36939326787 verified the successful normal Networks run, merged/head tree equality, actual98-test XML, JAR checksums and Java21 classes, then passed all retained core source invariants. Artifact11199147046 SHA256: `abaadca65139e3a9d5273daf50f3eef8eeb4a71e49cd83fb4f3c7e54455b2583`. Downloaded reports and both metadata blobs were inspected.

Tested Networks JAR SHA256: `b9d63570c30f4d0195393b567bddcf48973e1c103302526ecebbb3f67106e9ed`. Actual core API input SHA256: `17914e0f208a68d9b41b84733936d702742df291b41cff35d706e7bf52f31d7c`; its standard commit field identifies corecf09e5bc. This compile-only dependency artifact has an unknown dedicated source field/epoch time and is not the canonical release core. Do not confuse that API artifact with the published4.1.63 JAR or the corrected canonical core publication workflow.

Revision102 retains every addon and exclusion. Only Networks advances from5bc81686 to the merged1.0.47 sourceba79e6e3; all other44 entries remain identical. The historical ledger total becomes402 project-test entries across16 addons;29 have no unit-test report entries, without excluding separate runtime/static coverage. Only Networks receives new test evidence. This is not one new all45 full-project test run.

Five pre-existing selected/tested-source differences remain explicit rather than being relabeled as newly tested:

| Repository | Selected source | Earlier ledger source |
| --- | --- | --- |
| SF_HotbarPets |20b83bf0fa39522647c92be38abbd24688cb5b1b|1434e5f6dccb2b15812626d17435e74982bf7229|
| SF_MobDrops |8943594f006a443ce8a77113f76709c871d03697|999a9e88d3770b092f1f898c409d220e4615bfcd|
| SF_RykenSlimeCustomizer |9fc2dc5f9cf3bf650602b70095da44a57f42172b|6b165f9efafad8e016e80cb3b146127c6314d6c6|
| SF_SlimeGlue |32db74f9b50317aab96163642ad4b5ed2d62471f|b15da0fa38befcccf58271177c7477dc664c09f7|
| SF_SMG |eecf6c5a0a09c9391fd654c95f80b02e2123c571|b0579092ed81b643c778357cb7890a69643907e2|

These differences identify attribution work to check, not proven plugin bugs or grounds to remove an addon. Their existing source selections and recorded test results are preserved.

## Publication boundary and next work

Networks' master publisher independently rechecks its final merged source before producing the standalone raw1.0.47 JAR. Verify that release asset separately; the selection test above did not publish it. This core branch needs its own canonical45-addon build and complete-stack validation. Existing release assets are unchanged by a source-manifest update; publication belongs to the exact-source reproducible-release workflow.

No core production code, IDs, storage format, migration defaults or core version change is made here. Temporary inventory/source-staging workflows are excluded. Next substantive review includes the retained FluffyMachines barrel work, the five evidence-source differences, and machine/ticker behavior not covered by the scoped grid correction. Do not declare every SF_ plugin exhaustively modernized based on a metadata snapshot or startup test.
