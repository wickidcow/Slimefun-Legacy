# Addon cleanup checkpoint — October 1, 2026

## Result and publication boundary

The interrupted cleanup's changes are intact. Twenty-three addon pull requests across twenty repositories were merged; all twenty-three were verified against the retrieved PR history. The canonical addon source matrix is revision 101 at source `a11e736c64407f089f9b976dc7270e3febbcf85c`.

The revised `SF_Addons_1.21.11-26.3.zip` was built, downloaded, independently checksum/structure/bytecode-checked, and matched byte-for-byte to the archive used for three successful two-boot full-stack tests. It contains all 45 maintained addons.

**Publication is separate: the GitHub v4.1.63 release still contains revision 100. Revision 101 has not replaced that release asset. No production server files were installed or changed.** The revision-101 artifact was tested with the exact a11e736 core source, not every previously published core binary. Existing release assets retain their original source identity.

- ZIP SHA-256: `fbdd1c0efd9a79841cf3b49538bd60290fab699c7848c4777354ce2b2f119598`
- [Canonical bundle run 36929724892](https://github.com/wickidcow/Slimefun-Legacy/actions/runs/36929724892), artifact `11195204155`.
- [Exact-bundle full-stack run 36929725023](https://github.com/wickidcow/Slimefun-Legacy/actions/runs/36929725023).
- [Read-only branch inventory run 36930848096](https://github.com/wickidcow/Slimefun-Legacy/actions/runs/36930848096), artifact `11195712901`.

## Five changed selections from revision 100

| Repository | Included JAR | Selected source |
| --- | --- | --- |
| SF_HotbarPets | SF_HotbarPets1.0.2.jar | `20b83bf0fa39522647c92be38abbd24688cb5b1b` |
| SF_MobDrops | SF_MobDrops1.0.4.jar | `8943594f006a443ce8a77113f76709c871d03697` |
| SF_RykenSlimeCustomizer | SF_RykenSlimeCustomizer3.1.11.jar | `9fc2dc5f9cf3bf650602b70095da44a57f42172b` |
| SF_SlimeGlue | SF_SlimeGlue1.0.1.jar | `32db74f9b50317aab96163642ad4b5ed2d62471f` |
| SF_SMG | SF_SMG1.0.4.jar | `eecf6c5a0a09c9391fd654c95f80b02e2123c571` |

These contain the API/presentation/metadata cleanup and Ryken's refreshed registration-reporting change. Ryken distinguishes conditionally skipped definitions from real failures and avoids empty late-init logging while retaining the maintained guide build source. Its refreshed PR build 36929498355 passed before merge.

The other preservation fixes were already selected in revision 100; merging them consolidated the maintained default branches without changing their selected source trees. The existing source-test ledger contains individually attributed earlier evidence, not a fresh run of every test against every revised pin. This checkpoint separately records revision 101's actual evidence.

## Verified package and runtime results

The 45 source pins, exact JAR set, plugin descriptors, individual manifest hashes and SHA256SUMS entries match. Outer and inner ZIP integrity and duplicate-entry checks passed. All 12,319 class files applicable to the Java 21 floor are within major version 65 and are not preview classes; higher multi-release overlays were handled separately. JUnit/Mockito/MockBukkit packages were checked for accidental inclusion.

Downloaded runtime artifacts were SHA-256-checked against GitHub's digests. Artifact 11195927483 records matching-push-artifact provenance from a11e736; its inner ZIP has the exact delivered ZIP hash above. All 45 addons were required to enable on each boot, with zero dependency-gated addons and a real WorldEdit provider. All six normalized logs contained zero ERROR/SEVERE lines and no inspected linkage/configuration-load failure signatures. The second boots confirmed clean previous shutdowns.

| Runtime | Actual build/channel | Required addons | Result |
| --- | --- | ---: | --- |
| Paper 1.21.11 | 132 / STABLE | 45 | Two boots passed |
| Paper 26.2 | 129 / STABLE | 45 | Two boots passed |
| Paper 26.3 | 142 / BETA | 45 | Two boots passed |

Runtime artifacts and verified digests:

- 1.21.11, `11195628813`: `cda81e96dc0530555805154ca345024573db3d58bf14db97a7f37bd97b3bccfd`
- 26.2, `11196545201`: `eba48ef20a58dbb526e5e620f04925f185bd69db4d3e7dbfe68023c79f64c771`
- 26.3, `11195179946`: `f4404f1382b9ebcc8ea99a733cd731466fc4d273720f29ea97a2d130e556c7bb`

These are generated-server startup/restart and package checks, not exhaustive populated-machine, captured historical-world, crash-atomicity, cross-fork rollback, future-API or Folia region-concurrency certification. The tested 26.3 build was beta, not stable.

## Verified merged addon PRs

| Repository | Merged PRs |
| --- | --- |
| SF_CultivationLegacy | #3 |
| SF_CrystamaeHistoria | #4 |
| SF_DankTech2 | #4 |
| SF_FinalTECH | #52 |
| SF_Galactifun | #10 |
| SF_InfinityExpansion2 | #23 |
| SF_LiteXpansion | #5 |
| SF_JustEnoughGuide | #15 |
| SF_FNAmplifications | #5, #6 |
| SF_MagicExpansion | #8, #9 |
| SF_NetworksExp | #49 |
| SF_RykenSlimeCustomizer | #8, #6 |
| SF_SlimeEasy | #6 |
| SF_SlimefunAdvancements | #10 |
| SF_SlimeHUD | #6 |
| SF_Supreme | #19 |
| SF_HotbarPets | #1 |
| SF_SlimeGlue | #2 |
| SF_SMG | #1 |
| SF_MobDrops | #3 |

## Corrected closures: preserve unfinished work

Three drafts were incorrectly closed earlier simply because they were old or partially superseded. They were reopened as drafts with corrective review notes. Being outdated is not evidence that unique work is complete.

- **SF_BetterChests #8:** main implements only part of the draft. The broader machine storage/ticker/menu/serialization changes still need selective review and tests. Preserve main's read-only Doctor boundary and exact legacy Cargo AIR mutation; do not silently discard writes when modern block data is absent.
- **SF_BuildingStaff #1:** optional Rebar/Pylon integration is not present in inspected master. Refresh selectively without raising the Java 21 / Minecraft 1.21.11 floor; verify protection, provider identity, rollback and exact-item consumption.
- **SF_FluffyMachines #5:** hover/item-frame and buffer-registration work remains. Refresh against current 26.2.12 storage code instead of merging obsolete version/build assumptions. Verify first-item identity, both buffers, cancellation/protection and restart.
- **SF_ExtraTools #1:** remains open because the repository is archived/read-only. GitHub rejected the earlier write with HTTP 403. It was not unarchived and remains excluded from the bundle.

Core PRs #291, #292 and #293 remain closed as superseded/integrated. Other concurrently active core work was not blindly merged.

The inventory also found unpromoted validation branches, including SlimeTinkerIE2 `work/native-item-presentation-validation` at `572ab998df93e063a72c4f3ce9c55019c1d1493e`. That workflow applies source-hash-checked presentation changes during validation. It is not proof those changes are already promoted or ready for this ZIP; its branch was preserved.

## Read-only inventory: all scoped repositories

The scan completed all 50 repositories: 45 bundle members and five related/integrated/excluded repositories. It examined 420 non-default branches with zero repository errors. All 45 bundle pins had the same tree as their repository's default branch at observation.

| Classification | Branches |
| --- | ---: |
| Same commit as default | 1 |
| Ancestor of default | 109 |
| Same tree, different history | 10 |
| Exact tip associated with a merged PR | 209 |
| Distinct tip with no PR found | 39 |
| Distinct tip after a closed PR | 48 |
| Open PR, preserved | 4 |

There are 329 branches with integration/equal-tree evidence and 91 retained for closer review. Neither set is an automatic deletion list: merged work may later have been reverted; divergent work may have been selectively reimplemented; protection and ongoing work may matter. **No branch refs were deleted by this continuation.**

The sequential audit began October 1, 2026 at 21:46:19 UTC; this is not an atomic cross-repository snapshot. Its source is committed on `audit/addon-branch-inventory-20261001` at `4e2670cf4ea8ce53697dddc0cd2babc1782806c9`. Eight conservative classification tests passed locally and in Actions. The full artifact contains per-repository defaults/pins, per-branch exact heads, ahead/behind counts, tree identities, PR associations and preservation reasons. It is ancestry/tree/PR analysis, not semantic review of every historical patch.

The downloaded audit artifact SHA-256 is `72625625ae272ce35de85b4fcafa5ab6aaf0bbcc52922093c630e1f47d5e3e8a`; its Actions retention expires October 31, 2026. The full JSON and Markdown report were also provided with the conversation artifacts.

## Installation boundary

Test intended replacements on a disposable copy first. Stop cleanly, back up worlds, Slimefun's database and addon data, then replace rather than duplicate plugin JARs. Do not install unwanted addons merely because they appear in the complete ZIP. Preserve registrations and migration settings. This audit enables no automatic migration and makes no production data changes.
