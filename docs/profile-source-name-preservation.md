# Preserve supplied profile names at initial creation

## Finding and scope

This correction continues coordinated addon PR #292 from `38e708c234e48b51f56940ae4c47bc0c73877d73`. It changes one production method, adds eight regression tests and records the evidence. Slimefun stays at 4.1.62; no item/research IDs, UUIDs, keys, schema, stored inventory format, recipe, machine rate, resource-pack mapping or migration default is changed.

The actual SlimeEasy floor-runtime evidence from run `36789633986` showed a machine player named `SE_Butcher`, UUID `5ab9c5fd-28e7-368d-b034-231adfa1d37d`. A separate UUID-only Bukkit offline lookup had no name. `ProfileDataController.createProfile(OfflinePlayer)` constructed its initial record through `PlayerProfile.getOwner()`, losing the name already supplied by the addon.

The SQLite parent table requires a non-null name. Its INSERT OR IGNORE skipped that parent row, and subsequent research-child inserts failed foreign-key constraints. The inspected failed runtime database had no parent/research rows for that identity. Changing item IDs or disabling database constraints would not repair this boundary.

## Correction

Build the existing record as before, then retain `p.getName()` when the caller supplied a non-null name, before submitting the initial parent write. Keep the existing owner-lookup behavior when the caller supplied no name; do not invent a username. Existing cached profiles still return without an additional write. Event registration, UUID ownership, backpack counts, research rules, queue scope/force flag and write scheduling remain unchanged.

This is not a global rename, automatic repair of existing records, recovery of corrupt inventories or a modification to the fake player's permissions. It prevents an avoidable loss of known caller information during new-profile creation. The old production blob was `032df51e806d0945225840590fc78dff8a6f7c81`; the corrected blob is `3f471ce1f03789dda1051aeefb245b0f7da4d4bc`.

## Actual regression evidence

[Validation run 36793063530](https://github.com/wickidcow/Slimefun-Legacy/actions/runs/36793063530) ran the actual controller before applying the correction. Its negative control failed with the intended assertion, expected `SE_Butcher` but observed `Player0`. MockBukkit supplies a generated name for an unknown UUID; the real server's corresponding lookup was null. Compilation/setup failures were not accepted as this negative control.

The corrected full project was then built uncached with Java 25 and its normal Java 21 output target. All source invariants, formatting, full Gradle build, explicit production deprecation/removal gate, class target checks and packaged source-identity checks passed.

Downloaded evidence artifact `11132487236` matched SHA-256 `8c05ce575884c4c8c3fc3807fe6fc2dfe7a3e21b6e9683aeca6d71099bb42f90`. Inspection of its 120 JUnit XML reports found 493 reported entries, zero failures/errors and one existing historical-database-fixture skip. All eight new profile tests passed without skips. An empty external-database test factory in the primary suite is not evidence of external-database execution. Explicit production javac deprecation/removal counts were 0/0; unrelated unchecked/test-library notes are not included in that claim.

The tests exercise the actual controller/profile classes with a recording submission boundary. They protect exact supplied names, stable UUIDs, independent owners with equal names, unchanged cached-profile behavior, the existing null-name fallback, initial counts and scope flags. A real SQLite fixture writes the captured parent/research records, closes and reopens the database, and checks their identities and values. A separate SQL negative control proves that ignoring a null-name parent cannot support a research child. These fixture tests are not a substitute for the controller's full asynchronous runtime path.

The final test source blob is `8f04798773db110aebc7a5f11ef64130498d70c2`. Both source blobs were independently checked against the downloaded files before promotion. Candidate JAR artifact `11132327701` has archive SHA-256 `8ce73aec7b0f82bba24d5f3b771e2cedfc3dddedf0c63c7c954a21f99c338745`; the enclosed core JAR hash recorded by the build is `488ea7e0f48453694f8a4e29f9ca148fac1b85973d52b5d273a06fd5f17e7d9e`.

The validation source commit was `6aefb735434fa7b73fc348356ae66de7160f69a6` plus the two explicitly hash-verified source files. It is not represented as an unmodified release commit. Temporary validation workflows and source-transport files are not included in this production promotion.

Earlier harness attempts exposed missing test dependencies, a nondeterministic unknown-owner mock, formatting-sensitive source-check ordering and missing build provenance. Those setup issues were corrected without weakening the production, assertion, source or packaging gates. The final test uses a JDK proxy fixture and adds no dependency.

## Remaining coordinated validation

The paired SlimeEasy native-floor run tests one unchanged Java 21 addon JAR on Paper 1.21.11/Java 21 and Paper 26.2/26.3 on Java 25. It must inspect the actual runtime profile database after shutdown, compare the exact enabled research-ID set and machine UUID/name, and retain native click/damage/banner and permission-file assertions. Until that run passes, this document does not certify the SlimeEasy floor correction.

Normal PR validation must also pass for the promoted source. Preserve the complete 45-addon set and reconcile concurrent addon fixes before publishing the final bundle. A current source compile or these eight tests do not certify historical-world upgrades, all-addon gameplay/performance, full GuGu interoperability or United rollback. United-to-Legacy upgrades remain relevant; United reverse conversion is not a release gate. No master merge, stable release or production-world modification is performed by this checkpoint.
