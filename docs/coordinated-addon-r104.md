# 4.1.64 release reconciliation with addon revision 104

## Preserved histories and exact scope

The release preparation at 49538c64a978a7e6fe58ff9a2adf42585fbe38b9 is reconciled with actual master 2bcaa53f2f1c7695cdb18cbf003d2516fdab6d79. Both parents are retained. The default-branch comparison since the preparation base identifies only the revision-104 matrix and the two PR299 production/test files. Those exact master blobs are retained without modification. No new storage, ticker, recipe, identity or migration implementation is introduced by this reconciliation.

The full addon matrix is byte-identical to master's revision 104, blob 20ff35f3bb4eb0e6eed87fab79660e0586e8f9db. All 45 members and exclusions remain. SlimeTinker is selected at bdb2ba51ad946de686c50dc1745ba89053b4535d; BetterChests 1.0.3, BuildingStaff 1.0.35, FluffyMachines 26.2.13 and Networks 1.0.47 remain selected. The optional BuildingStaff compile-only provider installer remains in the canonical workflow.

Release version, support/API contracts and previous-stable baseline retain the reviewed 4.1.64 preparation. The gradle comment and release notes now identify revision 104. The core source retains the PR295 cancellation/deletion correction and PR299 section-sign/RGB color compatibility correction.

## Evidence attribution, not recycled green checks

The existing docs/coordinated-addon-r103.md is a historical preparation checkpoint. Its 512 core entries and reproducibility hash describe the earlier preparation, before the current merged color tests; they are not final results for this source. Its 71 companion-addon tests and the 463-entry ledger are retained with their original source/core/run attribution. The ledger's revision 103 label describes its last reconciled evidence set, not the selected bundle revision.

SlimeTinker PR4 separately records its 13 permanent rendering tests per supported API lane from normal run 36947237160, and generated native item validation run 36949120294 with 70 initial and 94 restart assertions per lane. These tests used the unchanged published 4.1.63 binary. They do not imply that every addon suite was rerun with 4.1.64. Its completed source, test records and historical validation branches remain preserved.

The new combined 4.1.64 commit must pass its own complete normal CI, exact-source canonical build and provider-complete full-stack tests. The exact-master reproducible publisher is dispatched only after those release gates pass. The release core and its addon ZIP must identify the same source commit, and the uploaded assets must be downloaded and independently checked.

## Recovery boundary

At reconciliation, published 4.1.63 core asset 603654347 has SHA-256 993b257c54efe19b58cc8f4946d4d39387f7787fe263c9f27123343159344c42. Its current revision-104 addon asset 604494714 has SHA-256 e6ba58c00d843f445d8292435645fc3bda0902cfa38fd986fc2e6a9e3ca87ca8. The tag remains 2703e3a500b426849f3eb7806862bb4343bb9d6a. A new 4.1.64 release must not replace any of them.

No live-server files, world data or automatic migration defaults are changed. Generated fixtures, startup/restart checks and API compatibility do not establish exhaustive customer-world, live-client, cross-fork rollback, crash-atomicity or Folia concurrency safety. Preserve recovery data and test representative production copies before upgrading.
