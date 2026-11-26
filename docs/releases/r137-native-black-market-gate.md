# r137 native Black Market receipt/restart and release-byte gates

This work is scoped to MagicExpansion PR #18 and Slimefun Legacy PR #386. No live server data, core Java, other addon source pin, published asset, item ID or persistent format is changed by these integration fixtures.

## Completed logic gate

At integration head `c98fd8ed9e574f2a27d83a048bfcae4d5861bb33`, MagicExpansion source `3a8818528cf32701d19c99fc33fa7f7ef2545be4` passed the coordinated bundle workflow `38663002047` and full-stack workflow `38663002198`. All four native Black Market lanes executed successfully, not skipped: Paper 1.21.11 build 132, Paper 26.2 build 134, Paper 26.3 build 190 and experimental Purpur 26.3 build 2646. Each lane first completed the standard two-boot 45-addon smoke, then four fresh JVM phases: seed, verify, missing, restored. The native Networks/Fluffy conservation step also executed successfully on Paper 26.3.

The four downloaded runtime archives were independently SHA-256 verified against GitHub artifact metadata. Their console and phase-evidence hashes matched their result JSON. All 16 Black Market processes returned PASS with exit code zero. The seed/verify/missing/restored phases reported 87/114/32/153 assertions respectively on each lane. All lanes used the same candidate core and bundle. This was a development-core gate, not an assertion that its core JAR equaled published 4.1.74.

## Distinct version and unchanged Java logic

After that coordinated pass, MagicExpansion was assigned version 1.1.13 at source `13924991a1960daa40fe221cab5a4eef95d46ecb`. Only the POM version, workflow output identity and new release notes changed. Source CI `38664857521` passed every compile/build step; its release step was skipped. The downloaded reports confirm 146 executed tests across 14 suites, including 40 Black Market tests, with no failures, errors or skips.

The raw baseline-built candidate JAR has SHA-256 `674aeac4dbf5ce09969131dbfdbadd1e037eebfed74588f98f135902ab81adde`. Its 474 compiled class entries are byte-identical to the prior independently downloaded 3a88185 candidate. The plugin descriptor now identifies 1.1.13 and the non-shipping probe is absent.

The integration matrix and native probe explicitly target this new head and version. All 44 other addon entries remain unchanged.

## Final exact-byte gate

The [archived r137 release-candidate workflow](https://github.com/wickidcow/Slimefun-Legacy/blob/497c7659d244bba203a90e030e9054990924590f/.github/workflows/r137-release-candidate.yml) was read-only and had no release-writing step. It was retired from active Actions after v4.1.82. Its historical verification contract required:

1. Validate the still-published 4.1.74/r136 release ID, source, asset IDs, names, sizes and SHA-256 digests before and after preparation.
2. Validate the pinned 1.1.13 source workflow, raw JAR artifact and actual 146-test report artifact. An expired artifact, changed head or failed/skipped test refuses preparation.
3. Replace only the MagicExpansion JAR using the existing targeted-refresh validator. All other 44 published JAR bytes and non-target archive members must remain identical. Build the candidate ZIP twice and compare bytes.
4. Use the exact published 4.1.74 core SHA-256 `72a524d7c0d6f75a9decbf06679515887273ae53673d3f5ee38f355bef952c96`, rather than the development core, on all four native lanes.
5. Re-run the two-boot smoke, four-JVM Black Market phases and native Paper 26.3 Networks/Fluffy check against the frozen candidate. Match each report to the exact core, bundle and MagicExpansion hashes.
6. Recheck the published release lease and emit `release-ready.json` only after every required job and native phase succeeds. The evidence records `NOT_PUBLISHED`; this workflow cannot deploy or publish.

The 12 offline final-release admission contracts and 18 native-fixture admission contracts passed locally. They test simulated metadata/files, not native transactions. Final exact-byte runtime success must be read from the actual workflow evidence, not inferred from the earlier logic gate.

## Safety and limits

The non-shipping Java probe uses the installed addon's actual FileStore and native Bukkit serialization, not a duplicated implementation. It requires a nonce authorization file/JVM property, loopback offline generated world, console command, server thread and no players. It touches only its own synthetic ledger. All 45 source/JAR/hash identities, core/server hashes across boots, fresh results, phase/PID/nonce, console PASS and exit code zero are checked.

Synthetic records cover purchase/reveal receipts, ten offers with varied costs/rewards, amounts, names, lore and PDC strings/integers/longs/bytes. Tests cover externally rewritten cycles, missing markers with receipts, failed initialization invalidation, clean restart, legitimate rotation and stale refresh refusal. Synthetic marker restoration is not a production recovery procedure.

These checks do not prove client-click behavior, crash/power-loss atomicity, every historical world, or the cause of Albion's original external `cycle.yml` mismatch. No automatic repair, receipt deletion, forced unlock, offer reroll or purchase-limit reset is introduced. Publish only the exact subsequently validated artifact bytes; a rebuilt or different JAR needs renewed verification.
