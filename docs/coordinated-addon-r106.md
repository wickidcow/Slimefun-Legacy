# Coordinated addon revision 106

## Preserved selection and histories

Reconcile PR304, PR305 and PR306, which independently proposed revision105, without losing any selection. Published4.1.64 source is `e47a3035b35dfb93325d22ba03b2a69dd6cd6992`. This branch also retains master `a194ab1d0d5dd1cfc72caada225961ba0852ab87`, including the merged ExtraHeads selection, its guarded publisher, and the separate RecordKey ownership fix. Those existing changes are not reverted or republished as the old core.

| Addon | Selected source | Version |
| --- | --- | --- |
| JustEnoughGuide | `89ce9a8b0e8ef9d1939256f1d80917af63e439e2` | 2.1.70 |
| ExtraHeads | `72e7d9bc3ee2ac01bd75b147df39f018bffe544d` | 1.0.4 |
| FluffyMachines | `394de0dbfe770ae13256aa1cfbe747fc26b55a3a` | 26.2.14 |

The other42 source pins, all45 members, exclusions, saved IDs/formats and migration defaults remain unchanged from revision104. Core production is unchanged relative to the newly merged master. The published4.1.64 core asset/tag must remain unchanged during this addon-only refresh. No project-test ledger counts are invented for metadata reconciliation.

## Completed checks for the initial candidate

Reconciliation run36959278387 verified the exact proposal/file boundaries and all existing source invariants. Initial canonical run36959561527 produced the complete45-addon archive from head4df8de0c/tested merge72f2130c. Its inner ZIP SHA256 was `6d898272606a953efc246fe286c320590c5f352ade54e37936ed08222be82daa`. Independent inspection verified every pin, descriptor/version, manifest/checksum, archive integrity and Java21 base classes.

Published-core run36960073049 used that same initial archive with the exact released4.1.64 core SHA256 `316b308139e90981ed037d82ddc57b62b354d8e0a41804d5ebf3bb2b8f896d80`. Paper1.21.11 stable132,26.2 stable129 and26.3 beta142 passed two boots with all45 actual enabled states/versions, real WorldEdit, guide-renderer class linkage, and29 initial/36 restart crafter assertions. The initial result did not exercise every guide method and is not approval to publish that archive.

## Reproduced bundle-only JEG linkage defect

Additional actual-binary testing in36961863682 found the initial26.3-compiled JEG invokes `TextComponent.Builder.build():Component`, unavailable in the independently resolved1.21.11 API. The exact bundled JAR fails with NoSuchMethodError at ClipboardUtil:81; the published baseline-built standalone2.1.70 succeeds. Both use the same JEG source. This is distinct from the user's earlier missing-renderer incident, which was reported resolved after updating.

Do not publish the initial revision106 ZIP above. Startup and class existence checks are insufficient to prove delayed method linkage.

## Verified packaging correction

Retain the Paper26.3 compile probe, then rebuild only JEG against the supported1.21.11 API using the same exact core and its actual Gradle build. Preserve the helper's explicit core environment, run the project check lifecycle without exclusions, and execute both packaged clipboard overloads with an independent floor dependency graph. The manifest records JEG's actual distributable API. No JEG implementation, ID, version, recipe or guide layout change is introduced.

Eight permanent workflow tests enforce the ordering, isolated JEG condition, same core, non-skipped Gradle check path, independent linkage execution and manifest metadata. The non-shipped Java probe exercises six clipboard scenarios. Both are retained in normal validation; temporary audit workflows are excluded.

Run36963443406 passed the exact proposed workflow step, the existing core invariants, all eight guards and actual clipboard calls on Java21. The old workflow fails seven guard assertions; the original bundled JAR still reproduces NoSuchMethodError while the corrected JAR passes both methods/six scenarios. JEG's Gradle test task is NO-SOURCE; no JUnit suite is claimed. Its real shadow-JAR completeness checks remain active.

Downloaded evidence11208823528 matched SHA256 `e920ec951eb6bdf3cc59e62af2b824bf47da4465ac46f4305c3d8aa97c97b9f3`. All four promoted source blobs and actual logs were inspected. Corrected isolated JAR SHA256 `309cd7086b2ea7f27b5f01ba712eca0439d21a7906378d422aa2359b8a6f8dcd`; its ClipboardUtil class is byte-identical to the published standalone. Four other class files have unchanged disassembled instructions/signatures, not a blanket binary-equivalence guarantee. Early audit attempts failed on test syntax and incorrect direct-build tool/environment setup; these are retained failures, not successful controls. No production check was removed to accommodate them.

## Standalone and native coverage

Run36960983149 downloaded and verified all three real standalone releases, their source-tree correspondence, versions, hashes, archives and Java21 output. The all45 metadata audit36961187046 found raw JAR assets in every repository; differing tag/source histories still require binary review and are not silently treated as outdated or equivalent.

The previously incomplete original crafter1.21.11 control was completed in36959357258 using unchanged baseline/probe binaries and a bounded completion-marker wait. Both phases reproduce the original stale-cache behavior; all45 addons remain required. Updated native coverage remains separately attributed.

## Release gate

The new combined head needs its own complete canonical archive and normal tests. Its exact corrected archive must also pass all45 enabled-state/version checks, actual clipboard invocation and crafter persistence across supported Paper restarts with the unchanged published4.1.64 core. Earlier initial-candidate startup success must not be relabeled as this result.

After validation, merge the combined selection, close incorporated proposals without deleting unique work, and replace only the addon ZIP after backing up current assets/metadata and checking for concurrent changes. Verify uploaded bytes and unchanged core/tag. This document does not announce publication or a core version bump, and does not certify every historical world, machine, future API, crash or Folia interaction.
